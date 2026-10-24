#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
disasm.py — desensamblado anotado (capstone) de rangos de código del snapshot AOT, usando el modelo
(`out_xx/ranges.pickle`, pool, nombres de función) de report.py.

Anotaciones: cargas del object pool (PP = x27 / r5) con el valor de la entrada; llamadas por tabla de
despacho (x21 / r7); llamadas directas con nombre de función o de stub (VM_STUB_CODE_LIST); campos
(offsets etiquetados); inmediatos (con valor smi); dobles (fmov / vmov / pool).

Uso:
  python disasm.py <libapp.so> <dir_modelo> <dir_salida> <addr_hex> [<addr_hex>...]
     addr_hex = inicio de un rango (o cualquier dirección dentro del rango; se desensambla el rango completo)
     addr_hex puede ser "a:b" para un intervalo explícito
  python disasm.py <libapp.so> <dir_modelo> --refs <texto>      # rangos cuyo pool referencia un objeto con ese texto
  python disasm.py <libapp.so> <dir_modelo> --double <valor>    # rangos que cargan ese double (pool o fmov)
"""
import sys, os, re, struct, bisect, json, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from capstone import Cs, CS_ARCH_ARM64, CS_ARCH_ARM, CS_MODE_ARM
from capstone import arm64 as A64, arm as A32
import dartsnap
import report


def parse_stub_list(path):
    txt = open(path, encoding="utf-8", errors="replace").read()
    def block(name):
        m = re.search(r"#define %s\(V\)(.*?)\n\n" % name, txt, re.S)
        return re.findall(r"V\((\w+)\)", m.group(1)) if m else []
    tts = block("VM_TYPE_TESTING_STUB_CODE_LIST")
    probe = block("PROBE_POINT_STUBS_LIST")
    m = re.search(r"#define VM_STUB_CODE_LIST\(V\)(.*?)\n\n", txt, re.S)
    out = []
    for line in m.group(1).splitlines():
        line = line.strip()
        if line.startswith("V("):
            out.append(re.match(r"V\((\w+)\)", line).group(1))
        elif line.startswith("PROBE_POINT_STUBS_LIST"):
            out.extend(probe)
        elif line.startswith("VM_TYPE_TESTING_STUB_CODE_LIST"):
            out.extend(tts)
    return out


class Disasm(report.Reporter):
    def __init__(self, so_path, model_dir):
        super().__init__(so_path, model_dir)
        d = self.d
        self.arch64 = d.elf.e_machine == 0xB7
        self.text = d.data[d.text_off:d.text_off + d.text_size]
        self.base = d.text_vaddr
        self.md = Cs(CS_ARCH_ARM64, CS_MODE_ARM) if self.arch64 else Cs(CS_ARCH_ARM, CS_MODE_ARM)
        self.md.detail = True
        # DispatchTable::OriginElement: DISPATCH_TABLE_REG apunta a array[origin]; índice real = origin + cid + selector
        # (dispatch_table.cc: ARM64 -> 4096, ARM -> 1023, X64 -> 512)
        self.dt_origin = 4096 if self.arch64 else 1023
        self.stub_names = {}
        try:
            self.read_stubs()
        except Exception as e:
            print("stubs: no se pudieron nombrar:", e)

    # --- stubs ---------------------------------------------------------------
    def read_stubs(self):
        here = os.path.dirname(os.path.abspath(__file__))
        lst = parse_stub_list(os.path.join(here, "..", "ref_src", "3.13.3", "stub_code_list.h"))
        d = self.d; s = d.s
        s.p = d.roots_pos
        n = self.m.roots["n_prefix_refs"]
        prefix = [s.read_ref_id() for _ in range(n)]
        # localizar la serie contigua de 'len(lst)' objetos Code dentro del prefijo
        iscode = [isinstance(d.refs[r], dict) and d.refs[r].get("c") == dartsnap.CID["Code"] for r in prefix]
        best = None
        i = 0
        while i < n:
            if iscode[i]:
                j = i
                while j < n and iscode[j]:
                    j += 1
                if best is None or (j - i) > (best[1] - best[0]):
                    best = (i, j)
                i = j
            else:
                i += 1
        if best is None:
            return
        i0, i1 = best
        run = prefix[i0:i1]
        if len(run) != len(lst):
            print("aviso: serie de stubs de longitud %d (esperaba %d)" % (len(run), len(lst)))
        for k, r in enumerate(run[:len(lst)]):
            code = d.refs[r]
            if code.get("deferred"):
                continue
            st, en = d.code_payload(code)
            ri = self.range_index(st)
            self.stub_names[ri] = lst[k]
        self.stub_run = (i0, i1)

    # --- utilidades ----------------------------------------------------------
    def target_name(self, addr):
        j = self.range_index(addr)
        if j is None:
            return "?"
        r = self.ranges[j]
        if j in self.stub_names:
            nm = "<stub %s>" % self.stub_names[j]
        else:
            nm = self.range_name(j) or "<sin nombre>"
        off = addr - r["start"]
        return "%s@%s%s" % (nm, hex(r["start"]), ("+%d" % off) if off else "")

    def pool_entry(self, idx):
        if idx < 0 or idx >= len(self.pool):
            return None
        return self.pool[idx]

    def pool_double(self, idx):
        e = self.pool_entry(idx)
        if e is None or e[0] != "imm":
            return None
        if self.arch64:
            raw = e[1] & 0xFFFFFFFFFFFFFFFF
        else:
            e2 = self.pool_entry(idx + 1)
            if e2 is None or e2[0] != "imm":
                return None
            raw = (e[1] & 0xFFFFFFFF) | ((e2[1] & 0xFFFFFFFF) << 32)
        return struct.unpack("<d", struct.pack("<Q", raw))[0]

    def pool_repr_short(self, idx, maxlen=110):
        e = self.pool_entry(idx)
        if e is None:
            return "<pool %d fuera de rango>" % idx
        t, v, bits = e
        if t == "imm":
            dv = self.pool_double(idx)
            s = "imm %d" % v
            if dv is not None and dv == dv and abs(dv) < 1e300:
                s += " (double %r)" % dv
            return s
        if t == "obj":
            r = self.m.value_repr(v)
            s = json.dumps(r, ensure_ascii=False, default=str) if not isinstance(r, str) else r
            if len(s) > maxlen:
                s = s[:maxlen] + "…"
            return s
        return "<%s>" % t

    @staticmethod
    def smi(v):
        if v % 2 == 0:
            return " (smi %d)" % (v // 2)
        return ""

    # --- desensamblado --------------------------------------------------------
    def disasm(self, start, end=None):
        if end is None:
            i = self.range_index(start)
            r = self.ranges[i]
            start, end = r["start"], r["end"]
        code = self.text[start - self.base:end - self.base]
        insns = list(self.md.disasm(code, start))
        # etiquetas
        targets = set()
        for ins in insns:
            if ins.mnemonic.startswith("b") or ins.mnemonic in ("cbz", "cbnz", "tbz", "tbnz"):
                for op in ins.operands:
                    if (self.arch64 and op.type == A64.ARM64_OP_IMM) or (not self.arch64 and op.type == A32.ARM_OP_IMM):
                        t = op.imm
                        if start <= t < end:
                            targets.add(t)
        lines = []
        hdr = "; %s  [%s-%s]  %d B" % (self.target_name(start), hex(start), hex(end), end - start)
        lines.append(hdr)
        pp_add = {}; dt_add = {}; movs = {}
        for ins in insns:
            if ins.address in targets:
                lines.append("L_%x:" % ins.address)
            ann = self.annotate(ins, start, end, pp_add, dt_add, movs)
            txt = "  %08x  %-8s %s" % (ins.address, ins.mnemonic, ins.op_str)
            if ann:
                txt = "%-56s ; %s" % (txt, ann)
            lines.append(txt)
        return lines

    def reg(self, ins, regid):
        return ins.reg_name(regid)

    def annotate(self, ins, start, end, pp_add, dt_add, movs):
        notes = []
        mn = ins.mnemonic
        ops = ins.operands
        A = A64 if self.arch64 else A32
        OP_MEM = A64.ARM64_OP_MEM if self.arch64 else A32.ARM_OP_MEM
        OP_IMM = A64.ARM64_OP_IMM if self.arch64 else A32.ARM_OP_IMM
        OP_REG = A64.ARM64_OP_REG if self.arch64 else A32.ARM_OP_REG
        OP_FP = A64.ARM64_OP_FP if self.arch64 else A32.ARM_OP_FP
        PP = "x27" if self.arch64 else "r5"
        DT = "x21" if self.arch64 else "r7"
        THR = "x26" if self.arch64 else "r10"
        FP = "x29" if self.arch64 else "fp"
        dest = None
        if ops and ops[0].type == OP_REG and not (mn.startswith("st") or mn.startswith("cmp") or mn.startswith("cmn") or mn.startswith("tst") or mn.startswith("b") or mn.startswith("cb") or mn.startswith("tb") or mn.startswith("ccm") or mn.startswith("vst") or mn.startswith("push") or mn.startswith("vcmp")):
            dest = self.reg(ins, ops[0].reg)
        # memoria
        for op in ops:
            if op.type == OP_MEM:
                basereg = self.reg(ins, op.mem.base) if op.mem.base else None
                disp = op.mem.disp
                if basereg is None:
                    continue
                if basereg == PP or basereg in pp_add:
                    off = disp + (pp_add.get(basereg, 0) if basereg != PP else 0)
                    if self.arch64:
                        idx = (off - 16) // 8
                    else:
                        idx = (off + 1 - 8) // 4
                    isfp = mn.startswith("v") or (ops[0].type == OP_REG and self.reg(ins, ops[0].reg)[0] in "dsq" and self.arch64)
                    if isfp:
                        dv = self.pool_double(idx)
                        notes.append("pool[%d] = double %r" % (idx, dv) if dv is not None else "pool[%d] = %s" % (idx, self.pool_repr_short(idx)))
                    else:
                        notes.append("pool[%d] = %s" % (idx, self.pool_repr_short(idx)))
                elif basereg == DT or basereg in dt_add:
                    idxreg = self.reg(ins, op.mem.index) if op.mem.index else None
                    if idxreg is not None:
                        sb = movs.get(idxreg)
                        if sb is not None and sb >= (1 << 31):
                            sb -= (1 << 32)
                        notes.append("dispatch[cid + sel %s]" % (sb if sb is not None else "?"))
                    else:
                        off = disp + (dt_add.get(basereg, 0) if basereg != DT else 0)
                        sel = off // (8 if self.arch64 else 4) + self.dt_origin
                        tg = self.dispatch[sel] if 0 <= sel < len(self.dispatch) else 0
                        notes.append("dispatch[%d] -> %s" % (sel, self.target_name(tg) if tg else "null"))
                elif basereg in movs and basereg in ("lr", "x30", "ip", "r12") and mn.startswith("ld"):
                    # ldr lr, [lr, #off] tras add lr, r7, cid, lsl #2 (+ add lr, lr, #hi): selector = (acum + off) / 4
                    sel = (movs[basereg] + disp) // (8 if self.arch64 else 4)
                    notes.append("dispatch[cid + sel %d]" % sel)
                elif basereg == THR:
                    notes.append("THR+%d" % disp)
                elif basereg not in ("sp", FP, "x28", "x22", "pc"):
                    if disp % 4 == 3 or disp % 4 == 7 or (disp % 2 == 1):
                        w = (disp + 1) // 4
                        notes.append("fld w%d (byte %d)" % (w, disp + 1))
        # add desde PP / DT con registro (movw/movt + add rd, r5, rX)
        if mn == "add" and len(ops) >= 3 and ops[0].type == OP_REG and ops[1].type == OP_REG and ops[2].type == OP_REG:
            rn = self.reg(ins, ops[1].reg); rd = self.reg(ins, ops[0].reg); rm = self.reg(ins, ops[2].reg)
            shifted = getattr(ops[2], "shift", None) is not None and ops[2].shift.value
            if rn == DT and shifted:
                # add lr, r7, cid, lsl #2  -> índice relativo al cid; los add posteriores acumulan el selector
                movs[rd] = 0; dest = None
                notes.append("dispatch base (cid)")
            elif rn == PP and rm in movs:
                pp_add[rd] = movs[rm]; dest = None
            elif rn == DT and rm in movs:
                dt_add[rd] = movs[rm]; dest = None
            elif rm == PP and rn in movs:
                pp_add[rd] = movs[rn]; dest = None
            elif rm in movs:      # add rd, cid, #selector  (selector materializado en rm)
                movs[rd] = movs[rm]; dest = None
            elif rn in movs:
                movs[rd] = movs[rn]; dest = None
        # add desde PP / DT
        if mn == "add" and len(ops) >= 3 and ops[0].type == OP_REG and ops[1].type == OP_REG and ops[2].type == OP_IMM:
            rn = self.reg(ins, ops[1].reg); rd = self.reg(ins, ops[0].reg)
            imm = ops[2].imm
            if self.arch64 and getattr(ops[2], "shift", None) is not None and ops[2].shift.value:
                imm <<= ops[2].shift.value
            if rn == PP:
                pp_add[rd] = imm; dest = None
            elif rn == DT:
                dt_add[rd] = imm; dest = None
            elif rn not in (THR, "sp", FP, "x28", "x22"):
                notes.append("imm %d%s" % (imm, self.smi(imm)))
                if rd == rn and rd in movs and rd in ("lr", "x30", "ip", "r12", "x16", "x17"):
                    movs[rd] = movs[rd] + imm; dest = None   # add lr, lr, #sel_hi (acumula)
                elif rd in ("lr", "x30", "ip", "r12", "x16", "x17"):
                    movs[rd] = imm; dest = None   # posible selector de dispatch (add lr, cid, #sel)
        elif mn in ("sub", "subs", "adds", "cmp", "cmn", "and", "orr", "eor", "lsl", "lsr", "asr", "tst", "ands", "mul"):
            for op in ops[1:]:
                if op.type == OP_IMM:
                    v = op.imm
                    notes.append("imm %d%s" % (v, self.smi(v)) if v >= 0 else "imm %d" % v)
        elif mn in ("movz", "movn", "movk", "mov", "movw", "movt", "mvn"):
            if ops and ops[0].type == OP_REG and len(ops) > 1 and ops[1].type == OP_IMM:
                rd = self.reg(ins, ops[0].reg)
                v = ops[1].imm
                if self.arch64:
                    sh = (ops[1].shift.value or 0) if getattr(ops[1], "shift", None) is not None else 0
                    if mn == "movz":
                        movs[rd] = v << sh
                    elif mn == "movn":
                        movs[rd] = (~(v << sh)) & 0xFFFFFFFFFFFFFFFF
                    elif mn == "movk":
                        movs[rd] = (movs.get(rd, 0) & ~(0xFFFF << sh)) | (v << sh)
                    else:
                        movs[rd] = v & 0xFFFFFFFFFFFFFFFF
                else:
                    if mn == "movt":
                        movs[rd] = (movs.get(rd, 0) & 0xFFFF) | ((v & 0xFFFF) << 16)
                    elif mn == "mvn":
                        movs[rd] = (~v) & 0xFFFFFFFF
                    else:
                        movs[rd] = v & 0xFFFFFFFF
                val = movs[rd]
                sval = val - (1 << 64) if (self.arch64 and val >= (1 << 63)) else (val - (1 << 32) if (not self.arch64 and val >= (1 << 31)) else val)
                notes.append("= %d (0x%x)%s" % (sval, val, self.smi(sval) if sval >= 0 else ""))
        elif mn in ("fmov", "vmov", "vmov.f64", "vmov.f32"):
            for op in ops:
                if op.type == OP_FP:
                    notes.append("double %r" % op.fp)
        # llamadas / saltos
        if mn in ("bl", "blx", "b") or mn.startswith("b."):
            for op in ops:
                if op.type == OP_IMM:
                    t = op.imm
                    if start <= t < end:
                        notes.append("-> L_%x" % t)
                    else:
                        notes.append("-> " + self.target_name(t))
        elif mn in ("cbz", "cbnz", "tbz", "tbnz"):
            for op in ops:
                if op.type == OP_IMM and start <= op.imm < end:
                    notes.append("-> L_%x" % op.imm)
        elif mn in ("blr", "br") and ops and ops[0].type == OP_REG:
            r = self.reg(ins, ops[0].reg)
            if r in movs:
                notes.append("-> " + self.target_name(movs[r]))
        # limpiar seguimiento
        if dest:
            pp_add.pop(dest, None); dt_add.pop(dest, None)
            if mn not in ("movz", "movn", "movk", "mov", "movw", "movt", "mvn"):
                movs.pop(dest, None)
        if self.arch64 and ins.address + 0 == 0:
            pass
        return "; ".join(notes)

    # --- búsquedas --------------------------------------------------------------
    def find_refs(self, query, maxn=200):
        q = query.lower()
        hits = {}
        for idx, e in enumerate(self.pool):
            if e[0] != "obj":
                continue
            r = self.m.value_repr(e[1])
            s = json.dumps(r, ensure_ascii=False, default=str) if not isinstance(r, str) else r
            if q in s.lower():
                hits[idx] = s[:120]
        out = []
        for i, r in enumerate(self.ranges):
            common = set(r["pool"]) & set(hits)
            if common:
                out.append((hex(r["start"]), r["end"] - r["start"], self.range_name(i) or "<sin nombre>", [hits[c] for c in sorted(common)][:4]))
        return out[:maxn]

    def callers(self, addr):
        j = self.range_index(addr)
        out = []
        for i, r in enumerate(self.ranges):
            for t in r["calls"] + r["branches"]:
                if self.range_index(t) == j:
                    out.append((hex(r["start"]), r["end"] - r["start"], self.range_name(i) or "<sin nombre>"))
                    break
        return out

    def dispatch_targets(self, selbase, cids):
        out = []
        for cid in cids:
            i = selbase + cid + self.dt_origin
            tg = self.dispatch[i] if 0 <= i < len(self.dispatch) else 0
            out.append((cid, self.m.class_name(cid), self.target_name(tg) if tg else "null"))
        return out

    def fn_refs(self, addr):
        """Rangos cuyo pool referencia un objeto Function (o Closure) cuyo código empieza en addr."""
        hits = {}
        for idx, e in enumerate(self.pool):
            if e[0] != "obj":
                continue
            o = self.d.refs[e[1]]
            if not isinstance(o, dict):
                continue
            fr = None
            if o.get("c") == dartsnap.CID["Function"]:
                fr = e[1]
            elif o.get("c") == dartsnap.CID["Closure"]:
                fr = o.get("function")
            if fr and fr in self.m.func_code_range and self.m.func_code_range[fr][0] == addr:
                hits[idx] = self.m.func_qualname(fr)
        out = []
        for i, r in enumerate(self.ranges):
            common = set(r["pool"]) & set(hits)
            if common:
                out.append((hex(r["start"]), r["end"] - r["start"], self.range_name(i) or "<sin nombre>"))
        return out

    def find_double(self, val, tol=1e-9, maxn=200):
        hits = set()
        for idx, e in enumerate(self.pool):
            if e[0] != "imm":
                continue
            dv = self.pool_double(idx)
            if dv is not None and abs(dv - val) <= tol * max(1.0, abs(val)):
                hits.add(idx)
        out = []
        for i, r in enumerate(self.ranges):
            if set(r["pool"]) & hits or any(abs(f - val) <= tol * max(1.0, abs(val)) for f in r.get("fimm", [])):
                out.append((hex(r["start"]), r["end"] - r["start"], self.range_name(i) or "<sin nombre>"))
        return out[:maxn]


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    so, model_dir = sys.argv[1:3]
    dz = Disasm(so, model_dir)
    args = sys.argv[3:]
    if args and args[0] == "--refs":
        for h in dz.find_refs(" ".join(args[1:])):
            print(h)
        return
    if args and args[0] == "--double":
        for h in dz.find_double(float(args[1])):
            print(h)
        return
    if args and args[0] == "--pool":
        for a in args[1:]:
            idx = int(a)
            print(idx, dz.pool_repr_short(idx, 400))
        return
    if args and args[0] == "--dispatch":
        sel = int(args[1]); cids = [int(c) for c in args[2].split(",")]
        for h in dz.dispatch_targets(sel, cids):
            print(h)
        return
    if args and args[0] == "--fnrefs":
        for h in dz.fn_refs(int(args[1], 16)):
            print(h)
        return
    if args and args[0] == "--range":
        for a in args[1:]:
            i = dz.range_index(int(a, 16))
            print(json.dumps(dz.describe_range(i), ensure_ascii=False, default=str))
        return
    if args and args[0] == "--callers":
        for a in args[1:]:
            print("== callers of", a)
            for h in dz.callers(int(a, 16)):
                print(h)
        return
    if args and args[0] == "--callers-name":
        q = " ".join(args[1:]).lower()
        for i, r in enumerate(dz.ranges):
            nm = dz.range_name(i)
            if nm and q in nm.lower():
                print("== callers of", hex(r["start"]), nm)
                for h in dz.callers(r["start"]):
                    print(h)
        return
    if args and args[0] == "--vtable":
        # selectores (relativos al origen) para los que TODAS las clases dadas tienen entrada no nula
        cids = [int(c) for c in args[1].split(",")]
        n = len(dz.dispatch)
        found = 0
        for sel in range(-dz.dt_origin, n - dz.dt_origin - max(cids)):
            tg = [dz.dispatch[sel + dz.dt_origin + c] if 0 <= sel + dz.dt_origin + c < n else 0 for c in cids]
            if all(tg) and len(set(tg)) == len(tg):
                # filtro anti-alineación: funciones no contiguas (separadas > 256 B) y de tamaño > 120 B
                rs = [dz.range_index(t) for t in tg]
                sizes = [dz.ranges[r]["end"] - dz.ranges[r]["start"] for r in rs]
                srt = sorted(tg)
                if min(sizes) < 120 or any(srt[i + 1] - srt[i] < 256 for i in range(len(srt) - 1)):
                    continue
                print("sel", sel, [(c, dz.target_name(t)) for c, t in zip(cids, tg)])
                found += 1
                if found > 60:
                    break
        return
    if args and args[0] == "--poolfn":
        for a in args[1:]:
            e = dz.pool_entry(int(a))
            if e and e[0] == "obj":
                o = dz.d.refs[e[1]]
                fr = e[1] if o.get("c") == dartsnap.CID["Function"] else o.get("function")
                rng = dz.m.func_code_range.get(fr)
                print(a, dz.m.func_qualname(fr), [hex(x) for x in rng] if rng else None)
        return
    if args and args[0] == "--name":
        q = " ".join(args[1:]).lower()
        for i, r in enumerate(dz.ranges):
            nm = dz.range_name(i)
            if nm and q in nm.lower():
                print(hex(r["start"]), r["end"] - r["start"], nm)
        return
    if args and args[0] == "--layout":
        cid = int(args[1])
        lay = dz.m.instance_layout(cid)
        print(dz.m.class_name(cid), {k: lay[k] for k in sorted(lay)})
        return
    out_dir = args[0]
    os.makedirs(out_dir, exist_ok=True)
    for a in args[1:]:
        if ":" in a:
            s, e = a.split(":")
            lines = dz.disasm(int(s, 16), int(e, 16))
            name = "dis_%s_%s.txt" % (s, e)
        else:
            lines = dz.disasm(int(a, 16))
            name = "dis_%s.txt" % a
        p = os.path.join(out_dir, name)
        open(p, "w", encoding="utf-8").write("\n".join(lines) + "\n")
        print(p, len(lines), "líneas")


if __name__ == "__main__":
    main()
