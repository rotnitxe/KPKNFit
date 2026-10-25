#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
codescan.py — "desensamblado ligero" del segmento de instrucciones del snapshot AOT, sin capstone.

Para cada rango de código de la InstructionsTable (payload_i .. payload_{i+1}) reconoce:
  * cargas del object pool (PP): ARM64 `ldr Xt,[x27,#imm]` y `add Xd,x27,#hi,lsl 12 ; ldr Xt,[Xd,#lo]`;
    ARM32 `ldr Rt,[r5,#imm12]` y `add Rd,r5,#rot ; ldr Rt,[Rd,#imm12]`  -> índice de entrada del pool
  * inmediatos materializados (movz/movk/movn, mov/movw/movt) y comparaciones con inmediato (cmp/cmn)
  * llamadas directas (bl / b fuera de rango) y llamadas por tabla de despacho (x21 / r7 + offset)
Registra también qué rango contiene cada instrucción. Es heurístico: no sigue el flujo de control.

Uso: python -I codescan.py <libapp.so> <dir_salida>
Salida: ranges.pickle (lista de dicts) y ranges_summary.json
"""
import sys, os, json, pickle, struct, bisect, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dartsnap
import dump_model


def sext(v, bits):
    if v & (1 << (bits - 1)):
        v -= (1 << bits)
    return v


def vfp_expand_imm64(imm8):
    """VFPExpandImm (ARM/ARM64) para doubles: a=signo, b, cdefgh."""
    a = (imm8 >> 7) & 1; b = (imm8 >> 6) & 1
    exp = ((1 - b) << 10) | (b * 0xFF << 2) | ((imm8 >> 4) & 3)
    frac = (imm8 & 0xF) << 48
    bits = (a << 63) | (exp << 52) | frac
    return struct.unpack("<d", struct.pack("<Q", bits))[0]


def scan_arm64(text, base, starts):
    """starts: lista ordenada de inicios (vaddr) de rangos. Devuelve lista de dicts por rango."""
    n = len(starts)
    ranges = [dict(start=starts[i], end=(starts[i + 1] if i + 1 < n else base + len(text)), pool=[], imm=[], cmp=[], calls=[], dispatch=[], branches=[], fimm=[]) for i in range(n)]
    ri = 0
    r = ranges[0]
    nxt = r["end"]
    pp_add = {}   # Rd -> hi offset (add from x27)
    dt_add = {}   # Rd -> hi offset (add from x21)
    movs = {}     # Rd -> (value, last_pc)
    NOISE_RN = {26, 27, 21, 28, 29, 31, 22}   # THR, PP, DISPATCH, HEAP_BITS, FP, SP, NULL
    pc = base
    for (ins,) in struct.iter_unpack("<I", text):
        while pc >= nxt and ri + 1 < n:
            ri += 1; r = ranges[ri]; nxt = r["end"]
            pp_add.clear(); dt_add.clear(); movs.clear()
        op = ins >> 24
        # LDR (unsigned offset) 64-bit GPR (0xF94...) o FP double (0xFD4...)
        if (ins & 0xFFC00000) == 0xF9400000 or (ins & 0xFFC00000) == 0xFD400000:
            rn = (ins >> 5) & 31; rt = ins & 31
            imm = ((ins >> 10) & 0xFFF) * 8
            is_fp = (ins & 0xFFC00000) == 0xFD400000
            if rn == 27:
                idx = (imm - 16) // 8
                if idx >= 0:
                    r["pool"].append(idx)
            elif rn in pp_add:
                off = pp_add[rn] + imm
                idx = (off - 16) // 8
                if idx >= 0:
                    r["pool"].append(idx)
            elif rn == 21 and not is_fp:
                r["dispatch"].append(imm // 8)
            elif rn in dt_add and not is_fp:
                r["dispatch"].append((dt_add[rn] + imm) // 8)
            if not is_fp:
                pp_add.pop(rt, None); dt_add.pop(rt, None); movs.pop(rt, None)
        # FMOV (scalar, immediate) double: 0x1E601000 | imm8<<13 | Rd
        elif (ins & 0xFFE03FE0) == 0x1E601000:
            r["fimm"].append(vfp_expand_imm64((ins >> 13) & 0xFF))
        # ADD (immediate) 64-bit
        elif (ins & 0xFF000000) == 0x91000000:
            sh = (ins >> 22) & 1
            imm = ((ins >> 10) & 0xFFF) << (12 if sh else 0)
            rn = (ins >> 5) & 31; rd = ins & 31
            if rn == 27:
                pp_add[rd] = imm
            elif rn == 21:
                dt_add[rd] = imm
            else:
                pp_add.pop(rd, None); dt_add.pop(rd, None)
            if rd != 31 and rn not in NOISE_RN:
                r["imm"].append(imm)
            movs.pop(rd, None)
        # SUB (immediate) 64/32 with Rd != 31 -> immediate operand
        elif (ins & 0x7F000000) == 0x51000000 and (ins & 31) != 31 and ((ins >> 5) & 31) not in NOISE_RN:
            sh = (ins >> 22) & 1
            imm = ((ins >> 10) & 0xFFF) << (12 if sh else 0)
            r["imm"].append(-imm)
        # ADD (immediate) 32-bit
        elif (ins & 0xFF000000) == 0x11000000:
            if (ins & 31) != 31 and ((ins >> 5) & 31) not in NOISE_RN:
                sh = (ins >> 22) & 1
                r["imm"].append(((ins >> 10) & 0xFFF) << (12 if sh else 0))
        # SUBS imm with Rd=31 -> CMP ; ADDS imm Rd=31 -> CMN
        elif (ins & 0x7F00001F) == 0x7100001F or (ins & 0x7F00001F) == 0x3100001F:
            sh = (ins >> 22) & 1
            imm = ((ins >> 10) & 0xFFF) << (12 if sh else 0)
            if (ins & 0x7F00001F) == 0x3100001F:
                imm = -imm
            r["cmp"].append(imm)
        # MOVZ / MOVN / MOVK
        elif (ins & 0x7F800000) in (0x52800000, 0x12800000, 0x72800000):
            is64 = bool(ins & 0x80000000)
            hw = (ins >> 21) & 3
            imm16 = (ins >> 5) & 0xFFFF
            rd = ins & 31
            kind = ins & 0x7F800000
            if kind == 0x52800000:    # MOVZ
                movs[rd] = imm16 << (16 * hw)
            elif kind == 0x12800000:  # MOVN
                v = ~(imm16 << (16 * hw))
                movs[rd] = v & (0xFFFFFFFFFFFFFFFF if is64 else 0xFFFFFFFF)
            else:                     # MOVK
                v = movs.get(rd, 0)
                v = (v & ~(0xFFFF << (16 * hw))) | (imm16 << (16 * hw))
                movs[rd] = v
            r["imm"].append(movs[rd])
        # BL
        elif (ins & 0xFC000000) == 0x94000000:
            tgt = pc + sext(ins & 0x3FFFFFF, 26) * 4
            r["calls"].append(tgt)
        # B (unconditional) fuera del rango -> salto de cola
        elif (ins & 0xFC000000) == 0x14000000:
            tgt = pc + sext(ins & 0x3FFFFFF, 26) * 4
            if not (r["start"] <= tgt < r["end"]):
                r["branches"].append(tgt)
        pc += 4
    return ranges


def ror32(v, n):
    n &= 31
    return ((v >> n) | (v << (32 - n))) & 0xFFFFFFFF


def arm_rot_imm(ins):
    return ror32(ins & 0xFF, 2 * ((ins >> 8) & 0xF))


def scan_arm32(text, base, starts):
    n = len(starts)
    ranges = [dict(start=starts[i], end=(starts[i + 1] if i + 1 < n else base + len(text)), pool=[], imm=[], cmp=[], calls=[], dispatch=[], branches=[], fimm=[]) for i in range(n)]
    ri = 0
    r = ranges[0]
    nxt = r["end"]
    pp_add = {}; dt_add = {}; movs = {}
    pc = base
    for (ins,) in struct.iter_unpack("<I", text):
        while pc >= nxt and ri + 1 < n:
            ri += 1; r = ranges[ri]; nxt = r["end"]
            pp_add.clear(); dt_add.clear(); movs.clear()
        if (ins & 0xF0000000) == 0xE0000000:  # cond = AL
            top = ins & 0x0FF00000
            if (ins & 0x0FB00F00) == 0x0D100B00:   # VLDR Dd,[Rn,#+/-imm8*4]
                rn = (ins >> 16) & 15; imm = (ins & 0xFF) * 4
                if not (ins & 0x00800000): imm = -imm
                if rn == 5:
                    idx = (imm + 1 - 8) // 4
                    if idx >= 0: r["pool"].append(idx)
                elif rn in pp_add:
                    idx = (pp_add[rn] + imm + 1 - 8) // 4
                    if idx >= 0: r["pool"].append(idx)
            elif (ins & 0x0FB00FF0) == 0x0EB00B00:  # VMOV.F64 Dd,#imm
                r["fimm"].append(vfp_expand_imm64(((ins >> 12) & 0xF0) | (ins & 0xF)))
            elif top == 0x05900000:   # LDR Rt,[Rn,#+imm12]
                rn = (ins >> 16) & 15; rt = (ins >> 12) & 15; imm = ins & 0xFFF
                if rn == 5:
                    idx = (imm + 1 - 8) // 4
                    if idx >= 0: r["pool"].append(idx)
                elif rn in pp_add:
                    idx = (pp_add[rn] + imm + 1 - 8) // 4
                    if idx >= 0: r["pool"].append(idx)
                elif rn == 7:
                    r["dispatch"].append(imm // 4)
                elif rn in dt_add:
                    r["dispatch"].append((dt_add[rn] + imm) // 4)
                pp_add.pop(rt, None); dt_add.pop(rt, None); movs.pop(rt, None)
            elif top == 0x02800000:  # ADD Rd,Rn,#rot
                rn = (ins >> 16) & 15; rd = (ins >> 12) & 15; imm = arm_rot_imm(ins)
                if rn == 5: pp_add[rd] = imm
                elif rn == 7: dt_add[rd] = imm
                else:
                    pp_add.pop(rd, None); dt_add.pop(rd, None)
                    r["imm"].append(imm)
                movs.pop(rd, None)
            elif top == 0x02400000:  # SUB Rd,Rn,#rot
                r["imm"].append(-arm_rot_imm(ins))
            elif top == 0x03A00000:  # MOV Rd,#rot
                rd = (ins >> 12) & 15; movs[rd] = arm_rot_imm(ins); r["imm"].append(movs[rd])
            elif top == 0x03E00000:  # MVN
                rd = (ins >> 12) & 15; movs[rd] = (~arm_rot_imm(ins)) & 0xFFFFFFFF; r["imm"].append(movs[rd])
            elif top == 0x03000000:  # MOVW
                rd = (ins >> 12) & 15; v = ((ins >> 4) & 0xF000) | (ins & 0xFFF); movs[rd] = v; r["imm"].append(v)
            elif top == 0x03400000:  # MOVT
                rd = (ins >> 12) & 15; v = ((ins >> 4) & 0xF000) | (ins & 0xFFF)
                movs[rd] = (movs.get(rd, 0) & 0xFFFF) | (v << 16); r["imm"].append(movs[rd])
            elif top == 0x03500000:  # CMP Rn,#rot
                r["cmp"].append(arm_rot_imm(ins))
            elif top == 0x03700000:  # CMN
                r["cmp"].append(-arm_rot_imm(ins))
            elif (ins & 0x0F000000) == 0x0B000000:  # BL
                r["calls"].append(pc + 8 + sext(ins & 0xFFFFFF, 24) * 4)
            elif (ins & 0x0F000000) == 0x0A000000:  # B
                tgt = pc + 8 + sext(ins & 0xFFFFFF, 24) * 4
                if not (r["start"] <= tgt < r["end"]):
                    r["branches"].append(tgt)
        elif (ins & 0xFE000000) == 0xFA000000:  # BLX imm (cond=1111)
            h = (ins >> 24) & 1
            r["calls"].append(pc + 8 + sext(ins & 0xFFFFFF, 24) * 4 + h * 2)
        pc += 4
    return ranges


def read_dispatch_table(d, m):
    """Deserializer::ReadDispatchTable: entradas -> dirección de entrada (0 = null entry)."""
    s = d.s
    if not hasattr(m, "roots") or "dispatch_pos" not in m.roots:
        return []
    s.p = int(m.roots["dispatch_pos"], 16)
    length = s.read_unsigned()
    if length == 0:
        return []
    first_code_id = s.read_unsigned()
    kRecent = 64      # kDispatchTableRecentCount = 1 << 6 (app_snapshot.cc:9124)
    kMaxRepeat = 63   # kDispatchTableMaxRepeat; kDispatchTableIndexBase = 64
    table = [0] * length
    recent = [0] * kRecent
    recent_index = 0
    repeat = 0
    value = 0
    few = d.it_first_entry_with_code
    mono_off, poly_off = d.entry_offsets()
    i = 0
    while i < length:
        if repeat > 0:
            table[i] = value; repeat -= 1; i += 1; continue
        enc = s.read_varint(d.word, True)
        if enc == 0:
            value = 0
        elif enc < 0:
            value = recent[~enc]
        elif enc <= kMaxRepeat:
            repeat = enc - 1
            table[i] = value; i += 1
            continue
        else:
            code_index = enc - (kMaxRepeat + 1)
            ci = code_index - 1
            if ci < few:
                value = d.text_vaddr + d.it_entries[ci][0]
            else:
                code = d.refs[d.code_start_index + (ci - few)]
                st, en = d.code_payload(code)
                value = st + (poly_off if code.get("has_monomorphic_entry") else 0)
            recent[recent_index] = value
            recent_index = (recent_index + 1) & (kRecent - 1)
        table[i] = value
        i += 1
    return table


def build(path, out):
    d = dartsnap.load(path)
    m = dump_model.Model(d)
    m.read_roots()
    os.makedirs(out, exist_ok=True)
    text = d.data[d.text_off:d.text_off + d.text_size]
    starts = [d.text_vaddr + e[0] for e in d.it_entries]
    if d.elf.e_machine == 0xB7:
        ranges = scan_arm64(text, d.text_vaddr, starts)
    else:
        ranges = scan_arm32(text, d.text_vaddr, starts)
    # nombres de rangos
    idx_by_start = {r["start"]: i for i, r in enumerate(ranges)}
    def range_index(addr):
        i = bisect.bisect_right(starts, addr) - 1
        return i if i >= 0 else None
    names = collections.defaultdict(list)   # range index -> [(name, evidencia)]
    for fr, (st, en) in m.func_code_range.items():
        i = range_index(st)
        if i is None: continue
        f = d.refs[fr]
        kind = dartsnap.decode_function_kind_tag(f["kind_tag"])[0]
        names[i].append((m.func_qualname(fr), "Function:" + kind, fr))
    # Code objects con owner Class (allocation stubs) o Type (TTS)
    for cr in m.code_refs:
        code = d.refs[cr]
        if code.get("deferred"): continue
        st, en = d.code_payload(code)
        i = range_index(st)
        o = d.refs[code["owner"]] if code["owner"] else None
        if isinstance(o, dict) and o.get("c") == dartsnap.CID["Class"]:
            names[i].append(("<alloc stub %s>" % m.s(o["name"]), "Code.owner:Class", cr))
        elif isinstance(o, dict) and o.get("c") in (dartsnap.CID["Type"], dartsnap.CID["RecordType"], dartsnap.CID["FunctionType"]):
            names[i].append(("<type testing stub %s>" % m.type_str(code["owner"]), "Code.owner:Type", cr))
        elif o is None:
            names[i].append(("<stub/null owner>", "Code.owner:null", cr))
    # nombres derivados: tear-offs y forwarders llaman al método real
    derived = collections.defaultdict(list)
    for i, lst in list(names.items()):
        for (nm, ev, fr) in lst:
            if ev in ("Function:ImplicitClosureFunction", "Function:DynamicInvocationForwarder", "Function:MethodExtractor"):
                r = ranges[i]
                tg = [t for t in r["calls"] + r["branches"]]
                for t in tg:
                    j = range_index(t)
                    if j is None or j == i or names.get(j):
                        continue
                    base = nm
                    if ev == "Function:DynamicInvocationForwarder":
                        base = nm.replace(".dyn:", ".")
                    derived[j].append((base, "derived:" + ev.split(":")[1], fr))
    for j, lst in derived.items():
        # elegir el nombre más frecuente
        c = collections.Counter(n for n, e, f in lst)
        nm = c.most_common(1)[0][0]
        names[j].append((nm, lst[0][1], lst[0][2]))
    dispatch = read_dispatch_table(d, m)
    summary = dict(num_ranges=len(ranges), named=len(names), derived=len(derived), dispatch_len=len(dispatch),
                   pool_refs=sum(len(r["pool"]) for r in ranges), calls=sum(len(r["calls"]) for r in ranges))
    json.dump(summary, open(os.path.join(out, "ranges_summary.json"), "w"), indent=1)
    pickle.dump(dict(ranges=ranges, names=dict(names), dispatch=dispatch, starts=starts),
                open(os.path.join(out, "ranges.pickle"), "wb"))
    print(json.dumps(summary))
    return d, m, ranges, names, dispatch


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2])
