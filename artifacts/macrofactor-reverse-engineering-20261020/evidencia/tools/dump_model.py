#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
dump_model.py — Post-proceso del snapshot parseado por dartsnap.py.
Construye un modelo legible (clases, campos, funciones, librerías, pool, enums,
instancias constantes, valores estáticos iniciales) y lo escribe en JSON.

Uso: python -I dump_model.py <libapp.so> <dir_salida> [--filter=package:macrofactor/]

Notas sobre lo que el precompilador (gen_snapshot, modo product + dwarf_stack_traces) deja en el
snapshot (OBSERVADO en estos binarios):
  * Class.functions / Class.fields vienen vacíos en casi todas las clases (tree shaker); los
    miembros se reconstruyen a partir de los objetos Function/Field supervivientes por su 'owner'.
  * Sólo sobreviven las Function que necesita el runtime (closures, tear-offs, forwarders dyn:,
    inicializadores de campos, etc.). Los métodos "normales" son código sin objeto Code/Function
    (descartados): no tienen nombre en el binario.
"""
import sys, os, json, collections, struct
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dartsnap
from dartsnap import CID, CID_NAMES, Obj, decode_function_kind_tag, decode_field_kind_bits, decode_class_state_bits


class Model:
    def __init__(self, d):
        self.d = d
        R = d.refs
        self.R = R
        self.class_by_cid = {}
        self.class_refs = []
        self.func_refs = []; self.field_refs = []; self.lib_refs = []; self.script_refs = []; self.pool_refs = []
        self.code_refs = []
        for cl in d.clusters:
            k = cl["kind"]
            rng = range(cl["start"], cl["stop"])
            if k == "Class":
                for i in rng:
                    self.class_by_cid[R[i]["id"]] = i
                    self.class_refs.append(i)
            elif k == "Function":
                self.func_refs.extend(rng)
            elif k == "Field":
                self.field_refs.extend(rng)
            elif k == "Library":
                self.lib_refs.extend(rng)
            elif k == "Script":
                self.script_refs.extend(rng)
            elif k == "ObjectPool":
                self.pool_refs.extend(rng)
            elif k == "Code":
                self.code_refs.extend(rng)
        self.instances_by_cid = collections.defaultdict(list)
        for cl in d.clusters:
            if cl["kind"] == "Instance":
                self.instances_by_cid[cl["cid"]].extend(range(cl["start"], cl["stop"]))
        self.cid_name_cache = {}
        self._layout_cache = {}
        # miembros por clase (ref de Class -> listas)
        self.funcs_by_class = collections.defaultdict(list)
        self.fields_by_class = collections.defaultdict(list)
        for fr in self.func_refs:
            c = self.owner_class_ref(R[fr]["owner"])
            self.funcs_by_class[c].append(fr)
        for fr in self.field_refs:
            c = self.owner_class_ref(R[fr]["owner"])
            self.fields_by_class[c].append(fr)
        # mapa code ref -> function ref (para nombres de rangos de código)
        self.func_by_code = {}
        self.func_code_range = {}
        for fr in self.func_refs:
            ck, a, b = d.function_code(R[fr])
            if ck == "code":
                self.func_by_code[a] = fr
                code = R[a]
                if not code.get("deferred"):
                    self.func_code_range[fr] = d.code_payload(code)
            elif ck == "discarded":
                self.func_code_range[fr] = (a, b)

    # ------------------------------------------------------------------ helpers
    def s(self, ref):
        o = self.R[ref] if ref else None
        return o if isinstance(o, str) else None

    def obj(self, ref):
        return self.R[ref] if ref else None

    def class_name(self, cid):
        if cid in self.cid_name_cache:
            return self.cid_name_cache[cid]
        cref = self.class_by_cid.get(cid)
        if cref is None:
            n = "<cid %d %s>" % (cid, CID_NAMES[cid] if cid < len(CID_NAMES) else "?")
        else:
            n = self.s(self.R[cref]["name"]) or "<cid %d>" % cid
        self.cid_name_cache[cid] = n
        return n

    def class_lib(self, cref):
        cls = self.R[cref]
        return self.library_url(cls["library"]) or ""

    @staticmethod
    def nullable_suffix(flags):
        # Nullability: kNullable = 0, kNonNullable = 1 (object.h)
        return "" if (flags & 1) else "?"

    def type_str(self, ref, depth=0):
        o = self.obj(ref)
        if o is None:
            return "null"
        if not isinstance(o, dict):
            return repr(o)
        c = o.get("c")
        if c == CID["Type"]:
            flags = o["flags"]
            tcid = (flags >> 3) & 0xfffff
            name = self.class_name(tcid)
            args = self.obj(o["arguments"])
            if isinstance(args, dict) and args.get("c") == CID["TypeArguments"] and args["types"] and depth < 4:
                # los TypeArguments de un Type llevan el vector aplanado (incluye los de superclases);
                # se muestran los últimos num_type_arguments de la clase
                cref = self.class_by_cid.get(tcid)
                nta = self.R[cref]["num_type_arguments"] if cref else len(args["types"])
                ts = args["types"][-nta:] if nta > 0 else []
                if ts:
                    name += "<" + ", ".join(self.type_str(t, depth + 1) for t in ts) + ">"
            return name + self.nullable_suffix(flags)
        if c == CID["TypeParameter"]:
            return "T%d.%d" % (o["base"], o["index"]) + self.nullable_suffix(o["flags"])
        if c == CID["FunctionType"]:
            return self.function_type_str(o, depth) + self.nullable_suffix(o["flags"])
        if c == CID["RecordType"]:
            ft = self.obj(o["field_types"])
            parts = [self.type_str(t, depth + 1) for t in ft["e"]] if isinstance(ft, dict) else []
            return "(" + ", ".join(parts) + ")" + self.nullable_suffix(o["flags"])
        return "<%s>" % (CID_NAMES[c] if c is not None and 0 <= c < len(CID_NAMES) else c)

    def function_type_str(self, ft, depth=0):
        if depth > 3:
            return "Function"
        pc = ft["packed_parameter_counts"]
        has_named = (pc >> 1) & 1
        num_fixed = (pc >> 2) & 0x3fff
        num_opt = (pc >> 16) & 0x3fff
        ptypes = self.obj(ft["parameter_types"])
        names = self.obj(ft["named_parameter_names"])
        types = [self.type_str(t, depth + 1) for t in ptypes["e"]] if isinstance(ptypes, dict) else []
        fixed = types[:num_fixed]
        opt = types[num_fixed:num_fixed + num_opt]
        sig = ", ".join(fixed)
        if num_opt:
            if has_named:
                nms = [self.s(n) or "?" for n in names["e"]] if isinstance(names, dict) else []
                sig += (", " if sig else "") + "{" + ", ".join("%s %s" % (t, n) for t, n in zip(opt, nms + ["?"] * len(opt))) + "}"
            else:
                sig += (", " if sig else "") + "[" + ", ".join(opt) + "]"
        res = self.type_str(ft["result_type"], depth + 1)
        return "(%s) -> %s" % (sig, res)

    def library_url(self, lref):
        o = self.obj(lref)
        return self.s(o["url"]) if isinstance(o, dict) and "url" in o else None

    def owner_class_ref(self, oref):
        o = self.obj(oref)
        if isinstance(o, dict):
            if o.get("c") == CID["Class"]:
                return oref
            if o.get("c") == CID["PatchClass"]:
                return o["wrapped_class"]
        return None

    def func_name(self, fref):
        f = self.R[fref]
        return self.s(f["name"]) or "?"

    def func_qualname(self, fref):
        f = self.R[fref]
        c = self.owner_class_ref(f["owner"])
        cn = self.s(self.R[c]["name"]) if c else "?"
        return "%s.%s" % (cn, self.func_name(fref))

    def func_summary(self, fref):
        f = self.R[fref]
        kind, rec, mod, flags = decode_function_kind_tag(f["kind_tag"])
        sig = self.obj(f["signature"])
        d = collections.OrderedDict(ref=fref, name=self.s(f["name"]), kind=kind, flags=flags)
        if mod:
            d["async_modifier"] = {1: "async", 2: "sync*", 3: "async*"}.get(mod, mod)
        if rec:
            d["recognized"] = rec
        if isinstance(sig, dict) and sig.get("c") == CID["FunctionType"]:
            d["signature"] = self.function_type_str(sig)
        ck, a, b = self.d.function_code(f)
        if ck == "code":
            code = self.R[a]
            if not code.get("deferred"):
                st, en = self.d.code_payload(code)
                d["code"] = [hex(st), hex(en)]
            else:
                d["code"] = "deferred"
            d["has_code_object"] = True
        elif ck == "discarded":
            d["code"] = [hex(a), hex(b)]
            d["has_code_object"] = False
        else:
            d["code"] = None
        data = self.obj(f["data"])
        if kind in ("ClosureFunction", "ImplicitClosureFunction") and isinstance(data, dict) and data.get("c") == CID["ClosureData"]:
            pf = data["parent_function"]
            if pf and isinstance(self.R[pf], dict) and self.R[pf].get("c") == CID["Function"]:
                d["parent_function"] = self.func_qualname(pf)
        return d

    def field_summary(self, fref):
        f = self.R[fref]
        flags = decode_field_kind_bits(f["kind_bits"])
        d = collections.OrderedDict(ref=fref, name=self.s(f["name"]), type=self.type_str(f["type"]), flags=flags)
        v = self.obj(f["host_offset_or_field_id"])
        if "Static" in flags:
            d["field_id"] = v
            if hasattr(self, "static_values") and isinstance(v, int) and v in self.static_values:
                d["initial_value"] = self.value_repr(self.static_values[v])
        else:
            d["offset_words"] = v
        init = f.get("initializer_function")
        if init and isinstance(self.R[init], dict) and self.R[init].get("c") == CID["Function"]:
            d["initializer_code"] = self.func_summary(init).get("code")
        return d

    def value_repr(self, ref, depth=0):
        o = self.obj(ref)
        if o is None:
            return None
        if isinstance(o, (bool, int, float, str)):
            return o
        if not isinstance(o, dict):
            return repr(o)
        c = o.get("c")
        if c == -1:
            return "<%s>" % o["base"]
        if c in (CID["Array"], CID["ImmutableArray"]):
            if depth > 3:
                return "<Array len %d>" % len(o["e"])
            return [self.value_repr(e, depth + 1) for e in o["e"][:64]] + (["...+%d" % (len(o["e"]) - 64)] if len(o["e"]) > 64 else [])
        if c == CID["GrowableObjectArray"]:
            return {"<growable>": self.value_repr(o["data"], depth + 1), "len": self.value_repr(o["length"])}
        if c == CID["Closure"]:
            fn = o["function"]
            return "<closure %s>" % (self.func_qualname(fn) if fn and isinstance(self.R[fn], dict) else "?")
        if c in (CID["Type"], CID["FunctionType"], CID["TypeParameter"], CID["RecordType"]):
            return "<type %s>" % self.type_str(ref)
        if c == CID["TypeArguments"]:
            return "<typeargs %s>" % ", ".join(self.type_str(t) for t in o["types"][:6])
        if c is not None and c >= dartsnap.kNumPredefinedCids:
            return self.instance_repr(ref, depth)
        if c == CID["ConstMap"]:
            data = self.obj(o["data"])
            if isinstance(data, dict) and depth < 3:
                used = self.obj(o["used_data"]) or 0
                pairs = data["e"][:min(used, 200)]
                return {"<map>": [[self.value_repr(pairs[i], depth + 1), self.value_repr(pairs[i + 1], depth + 1)] for i in range(0, len(pairs) - 1, 2)]}
            return "<ConstMap>"
        if c == CID["ConstSet"]:
            data = self.obj(o["data"])
            if isinstance(data, dict) and depth < 3:
                used = self.obj(o["used_data"]) or 0
                return {"<set>": [self.value_repr(e, depth + 1) for e in data["e"][:min(used, 200)]]}
            return "<ConstSet>"
        if c == CID["Record"]:
            return {"<record>": [self.value_repr(f, depth + 1) for f in o["fields"]]}
        if c == CID["Function"]:
            return "<function %s>" % self.func_qualname(ref)
        if c == CID["Field"]:
            return "<field %s>" % self.s(o["name"])
        if c == CID["Class"]:
            return "<class %s>" % self.s(o["name"])
        if c == CID["Code"]:
            return "<code>"
        if c == CID["Library"]:
            return "<library %s>" % self.s(o["url"])
        if c is not None and dartsnap.is_typed_data(c):
            return "<%s len %d>" % (CID_NAMES[c], o.get("len", -1))
        return "<%s>" % (CID_NAMES[c] if c is not None and 0 <= c < len(CID_NAMES) else c)

    def instance_layout(self, cid):
        """slot(word) -> (nombre, unboxed, tipo) usando los Field supervivientes de la clase y superclases."""
        if cid in self._layout_cache:
            return self._layout_cache[cid]
        names = {}
        c = cid
        hops = 0
        while c and hops < 40:
            cref = self.class_by_cid.get(c)
            if cref is None:
                break
            for fr in self.fields_by_class.get(cref, []):
                f = self.R[fr]
                flags = decode_field_kind_bits(f["kind_bits"])
                if "Static" in flags:
                    continue
                w = self.obj(f["host_offset_or_field_id"])
                if isinstance(w, int):
                    names.setdefault(w, (self.s(f["name"]), "Unboxed" in flags, self.type_str(f["type"])))
            cls = self.R[cref]
            st = self.obj(cls["super_type"])
            if isinstance(st, dict) and st.get("c") == CID["Type"]:
                c = (st["flags"] >> 3) & 0xfffff
            else:
                break
            hops += 1
        self._layout_cache[cid] = names
        return names

    def instance_repr(self, ref, depth=0):
        o = self.R[ref]
        cid = o["c"]
        name = self.class_name(cid)
        layout = self.instance_layout(cid)
        hdrw = self.d.word // self.d.cword
        out = collections.OrderedDict()
        out["<class>"] = name
        if depth > 3:
            return out
        fields = o.get("f", [])
        i = 0
        while i < len(fields):
            w = hdrw + i
            fv = fields[i]
            nm, unboxed, ty = layout.get(w, ("@%d" % w, False, "?"))
            if isinstance(fv, tuple):  # unboxed (8 bytes = 2 slots comprimidos de 32 bits)
                lo = fv[1] & 0xffffffff
                if i + 1 < len(fields) and isinstance(fields[i + 1], tuple) and self.d.cword == 4:
                    hi = fields[i + 1][1] & 0xffffffff
                    raw = lo | (hi << 32)
                    i += 2
                else:
                    raw = fv[1]
                    i += 1
                if "double" in ty or ty == "?":
                    try:
                        dv = struct.unpack("<d", struct.pack("<Q", raw & 0xffffffffffffffff))[0]
                    except Exception:
                        dv = None
                    if ty == "?":
                        out[nm] = {"raw": raw, "as_double": dv}
                    else:
                        out[nm] = dv
                else:
                    out[nm] = raw - (1 << 64) if raw >= (1 << 63) else raw
                continue
            out[nm] = self.value_repr(fv, depth + 1)
            i += 1
        return out

    def enum_values(self, cid):
        vals = [self.instance_repr(iref) for iref in self.instances_by_cid.get(cid, [])]
        def key(v):
            x = v.get("index", 0)
            return x if isinstance(x, int) else 0
        vals.sort(key=key)
        return vals

    def derived_member_names(self, cref):
        """Nombres de miembros deducidos de funciones satélite supervivientes."""
        methods = set(); getters = set(); setters = set(); inits = set()
        for fr in self.funcs_by_class.get(cref, []):
            f = self.R[fr]
            kind = decode_function_kind_tag(f["kind_tag"])[0]
            n = self.s(f["name"]) or ""
            if kind == "DynamicInvocationForwarder" and n.startswith("dyn:"):
                n2 = n[4:]
                if n2.startswith("get:"):
                    getters.add(n2[4:])
                elif n2.startswith("set:"):
                    setters.add(n2[4:])
                else:
                    methods.add(n2)
            elif kind == "ImplicitClosureFunction":
                methods.add(n)
            elif kind == "FieldInitializer" and n.startswith("init:"):
                inits.add(n[5:])
            elif kind in ("ImplicitGetter", "GetterFunction") and n.startswith("get:"):
                getters.add(n[4:])
            elif kind in ("ImplicitSetter", "SetterFunction") and n.startswith("set:"):
                setters.add(n[4:])
            elif kind == "MethodExtractor" and n.startswith("get:"):
                methods.add(n[4:])
            elif kind in ("RegularFunction", "Constructor"):
                methods.add(n)
        return dict(methods=sorted(methods), getters=sorted(getters), setters=sorted(setters), field_inits=sorted(inits))

    def class_summary(self, cref, with_members=True):
        cls = self.R[cref]
        d = collections.OrderedDict()
        d["ref"] = cref
        d["cid"] = cls["id"]
        d["name"] = self.s(cls["name"])
        d["library"] = self.library_url(cls["library"])
        sc = self.obj(cls["script"])
        d["script"] = self.s(sc["url"]) if isinstance(sc, dict) and "url" in sc else None
        st = self.obj(cls["super_type"])
        d["super"] = self.type_str(cls["super_type"]) if isinstance(st, dict) else None
        ifs = self.obj(cls["interfaces"])
        if isinstance(ifs, dict) and ifs.get("e"):
            d["interfaces"] = [self.type_str(t) for t in ifs["e"]]
        d["flags"] = decode_class_state_bits(cls["state_bits"])
        tp = self.obj(cls["type_parameters"])
        if isinstance(tp, dict) and tp.get("c") == CID["TypeParameters"]:
            nm = self.obj(tp["names"])
            if isinstance(nm, dict):
                d["type_parameters"] = [self.s(n) for n in nm["e"]]
        d["instance_size_words"] = cls["instance_size_words"]
        d["num_type_arguments"] = cls["num_type_arguments"]
        if with_members:
            d["fields"] = [self.field_summary(fr) for fr in self.fields_by_class.get(cref, [])]
            d["functions"] = [self.func_summary(fr) for fr in self.funcs_by_class.get(cref, [])]
            d["derived_names"] = self.derived_member_names(cref)
            if d["flags"].get("Enum"):
                d["enum_values"] = self.enum_values(cls["id"])
            else:
                insts = self.instances_by_cid.get(cls["id"], [])
                if insts:
                    d["const_instances_count"] = len(insts)
                    d["const_instances"] = [self.instance_repr(i) for i in insts[:60]]
        return d

    def read_roots(self):
        """ProgramDeserializationRoots::ReadRoots: secuencia de refs (Roots::raw_ 302, Roots::internal_
        63+557+256+kNumStubEntries, Roots::api_ 6, tabla de clases predefinidas 161, ObjectStore 171)
        seguida de la tabla inicial de campos estáticos (n = ReadUnsigned; n refs) y la compartida.
        Para no depender del número exacto de stubs (lista con macros anidadas y condicionales), se
        localiza la tabla buscando el punto donde ReadUnsigned() == (máximo field_id estático + 1)."""
        d = self.d
        s = d.s
        max_id = -1
        for fr in self.field_refs:
            f = self.R[fr]
            if f["kind_bits"] & 2:  # StaticBit
                v = self.obj(f["host_offset_or_field_id"])
                if isinstance(v, int) and v > max_id:
                    max_id = v
        expected = max_id + 1
        s.p = d.roots_pos
        prefix = []
        found = None
        limit = d.clustered_end
        while s.p < limit and len(prefix) < 5000:
            save = s.p
            try:
                n = s.read_unsigned()
            except IndexError:
                break
            if n == expected:
                ok = True
                p2 = s.p
                vals = []
                try:
                    for i in range(n):
                        r = s.read_ref_id()
                        if not (0 < r <= d.num_objects):
                            ok = False; break
                        vals.append(r)
                    if ok:
                        n_shared = s.read_unsigned()
                        if n_shared > 100000:
                            ok = False
                except IndexError:
                    ok = False
                if ok:
                    found = (save, len(prefix), vals, n_shared)
                    break
                s.p = p2
            s.p = save
            prefix.append(s.read_ref_id())
        if found is None:
            self.static_values = {}
            self.roots = dict(error="tabla de campos estáticos no localizada", expected=expected)
            return self.roots
        save, nprefix, vals, n_shared = found
        self.static_values = dict(enumerate(vals))
        # releer para dejar el cursor justo después de la tabla compartida (inicio de la dispatch table)
        s.p = save
        s.read_unsigned()
        for i in range(len(vals)):
            s.read_ref_id()
        s.read_unsigned()
        shared = [s.read_ref_id() for _ in range(n_shared)]
        self.shared_static_values = shared
        n_raw = 302; n_api = 6; n_ct = 161; n_os = 171
        n_handles = nprefix - n_raw - n_api - n_ct - n_os
        self.roots = dict(static_table_pos=hex(save), n_prefix_refs=nprefix, n_static=len(vals), n_shared=n_shared,
                          implied_num_stub_entries=n_handles - 63 - 557 - 256, dispatch_pos=hex(s.p))
        return self.roots


def build(path, out, flt=None):
    d = dartsnap.load(path)
    m = Model(d)
    roots = m.read_roots()
    os.makedirs(out, exist_ok=True)
    json.dump(roots, open(os.path.join(out, "roots_info.json"), "w", encoding="utf-8"), indent=1)
    libs = []
    for lref in m.lib_refs:
        L = m.R[lref]
        libs.append(dict(ref=lref, url=m.s(L["url"]), name=m.s(L["name"]), index=L["index"]))
    json.dump(libs, open(os.path.join(out, "libraries.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    classes = []
    for cref in m.class_refs:
        url = m.class_lib(cref)
        if flt and not url.startswith(flt):
            continue
        classes.append(m.class_summary(cref))
    json.dump(classes, open(os.path.join(out, "classes.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    # funciones con nombre (todas) y su rango de código: para atribuir constantes/llamadas
    funcs = []
    for fr in m.func_refs:
        fs = m.func_summary(fr)
        c = m.owner_class_ref(m.R[fr]["owner"])
        fs["class"] = m.s(m.R[c]["name"]) if c else None
        fs["library"] = m.class_lib(c) if c else None
        funcs.append(fs)
    json.dump(funcs, open(os.path.join(out, "functions.json"), "w", encoding="utf-8"), indent=0, ensure_ascii=False)
    # pool
    pools = []
    for pref in m.pool_refs:
        P = m.R[pref]
        ents = []
        for i, (t, v, bits) in enumerate(P["entries"]):
            if t == "obj":
                ents.append([i, "obj", m.value_repr(v)])
            elif t == "imm":
                ents.append([i, "imm", v])
            else:
                ents.append([i, t, None])
        pools.append(dict(ref=pref, length=P["len"], entries=ents))
    json.dump(pools, open(os.path.join(out, "pool.json"), "w", encoding="utf-8"), indent=0, ensure_ascii=False)
    # valores estáticos iniciales con nombre de campo
    statics = []
    for fr in m.field_refs:
        f = m.R[fr]
        if f["kind_bits"] & 2:
            c = m.owner_class_ref(f["owner"])
            fid = m.obj(f["host_offset_or_field_id"])
            v = m.static_values.get(fid) if isinstance(fid, int) else None
            statics.append(dict(ref=fr, field_id=fid, name=m.s(f["name"]), type=m.type_str(f["type"]),
                                cls=m.s(m.R[c]["name"]) if c else None, library=m.class_lib(c) if c else None,
                                flags=decode_field_kind_bits(f["kind_bits"]), value=m.value_repr(v) if v else None))
    json.dump(statics, open(os.path.join(out, "static_fields.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    return d, m


if __name__ == "__main__":
    path, out = sys.argv[1], sys.argv[2]
    flt = None
    for a in sys.argv[3:]:
        if a.startswith("--filter="):
            flt = a.split("=", 1)[1]
    build(path, out, flt)
