#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
report.py — Digest por sistema: clases, enums, instancias constantes, campos, funciones con nombre y,
para cada rango de código asociado, constantes del pool, inmediatos, comparaciones y llamadas.

Uso: python -I report.py <libapp.so> <dir_modelo(out_xx)> <salida.txt> <prefijo_lib1> [<prefijo_lib2> ...]
Opciones: --hops=N (profundidad de llamadas a rangos sin nombre, por defecto 1) --maxfn=N
"""
import sys, os, json, pickle, bisect, collections, struct
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dartsnap
import dump_model
from dartsnap import CID, CID_NAMES


class Reporter:
    def __init__(self, so_path, model_dir):
        self.d = dartsnap.load(so_path)
        self.m = dump_model.Model(self.d)
        self.m.read_roots()
        pk = pickle.load(open(os.path.join(model_dir, "ranges.pickle"), "rb"))
        self.ranges = pk["ranges"]; self.names = pk["names"]; self.dispatch = pk["dispatch"]; self.starts = pk["starts"]
        self.pool = self.d.refs[self.m.pool_refs[0]]["entries"] if self.m.pool_refs else []
        # el pool global suele ser el más grande
        if len(self.m.pool_refs) > 1:
            self.pool = max((self.d.refs[p]["entries"] for p in self.m.pool_refs), key=len)
        self.range_index_cache = {}

    def range_index(self, addr):
        i = bisect.bisect_right(self.starts, addr) - 1
        return i if i >= 0 else None

    def range_name(self, i):
        lst = self.names.get(i)
        if not lst:
            return None
        # preferir nombres de Function sobre stubs; si varias funciones comparten el código (dedup), indicarlo
        lst2 = sorted(lst, key=lambda t: (0 if t[1].startswith("Function") else 1 if t[1].startswith("derived") else 2))
        fnames = sorted(set(t[0] for t in lst2 if t[1].startswith("Function")))
        if len(fnames) > 1:
            return "%s (+%d compartidas, p.ej. %s)" % (fnames[0], len(fnames) - 1, fnames[1])
        return lst2[0][0] + ("" if lst2[0][1].startswith("Function") else " [%s]" % lst2[0][1])

    def pool_entry_repr(self, idx):
        if idx >= len(self.pool):
            return "<pool idx %d fuera de rango>" % idx
        t, v, bits = self.pool[idx]
        if t == "imm":
            r = {"imm": v}
            try:
                dv = struct.unpack("<d", struct.pack("<q", v))[0] if self.d.bits == 64 else None
                if dv is not None and dv == dv and abs(dv) < 1e300 and (abs(dv) > 1e-300 or dv == 0):
                    r["as_double"] = dv
            except Exception:
                pass
            return r
        if t == "obj":
            return self.m.value_repr(v)
        return "<%s>" % t

    def describe_range(self, i, with_calls=True):
        r = self.ranges[i]
        out = collections.OrderedDict()
        out["range"] = [hex(r["start"]), hex(r["end"])]
        out["size"] = r["end"] - r["start"]
        nm = self.range_name(i)
        if nm:
            out["name"] = nm
        pool_vals = []
        seen = set()
        for idx in r["pool"]:
            if idx in seen:
                continue
            seen.add(idx)
            pr = self.pool_entry_repr(idx)
            if isinstance(pr, str) and pr.startswith("<") and ("stub" in pr or pr in ("<Code>", "<null>")):
                continue
            pool_vals.append([idx, pr])
        out["pool"] = pool_vals
        if r.get("fimm"):
            out["fimm"] = sorted(set(r["fimm"]))[:40]
        if r["imm"]:
            out["imm"] = sorted(set(r["imm"]))[:60]
        if r["cmp"]:
            out["cmp"] = sorted(set(r["cmp"]))[:60]
        if with_calls:
            calls = []
            for t in r["calls"] + r["branches"]:
                j = self.range_index(t)
                if j is None:
                    continue
                calls.append((j, self.range_name(j) or "<sin nombre>", hex(self.ranges[j]["start"])))
            # deduplicar manteniendo orden
            seen2 = set(); cl = []
            for c in calls:
                if c[0] in seen2: continue
                seen2.add(c[0]); cl.append(c)
            out["calls"] = [[c[1], c[2]] for c in cl]
            dcalls = []
            for off in r["dispatch"]:
                if 0 <= off < len(self.dispatch):
                    tgt = self.dispatch[off]
                    if tgt:
                        j = self.range_index(tgt)
                        dcalls.append([self.range_name(j) or "<sin nombre>", hex(self.ranges[j]["start"]), off])
            if dcalls:
                out["dispatch_calls"] = dcalls[:80]
        return out

    def digest(self, prefixes, hops=1, maxfn=400):
        m = self.m; d = self.d
        res = collections.OrderedDict()
        classes = []
        for cref in m.class_refs:
            url = m.class_lib(cref)
            if any(url.startswith(p) for p in prefixes):
                classes.append(cref)
        res["prefixes"] = prefixes
        res["num_classes"] = len(classes)
        res["classes"] = []
        visited_ranges = set()
        for cref in classes:
            cs = m.class_summary(cref)
            entry = collections.OrderedDict((k, cs[k]) for k in ("cid", "name", "library", "super", "interfaces", "flags", "type_parameters", "instance_size_words", "fields", "derived_names", "enum_values", "const_instances", "const_instances_count") if k in cs)
            fns = []
            for fs in cs["functions"][:maxfn]:
                fr = fs["ref"]
                rng = m.func_code_range.get(fr)
                fe = collections.OrderedDict((k, fs[k]) for k in ("name", "kind", "signature", "flags", "parent_function") if k in fs)
                if rng:
                    i = self.range_index(rng[0])
                    fe["code"] = self.describe_range(i)
                    visited_ranges.add(i)
                    # un salto a rangos sin nombre
                    if hops >= 1:
                        sub = []
                        for t in self.ranges[i]["calls"] + self.ranges[i]["branches"]:
                            j = self.range_index(t)
                            if j is None or j in visited_ranges:
                                continue
                            if self.range_name(j):
                                continue
                            visited_ranges.add(j)
                            sub.append(self.describe_range(j))
                        for off in self.ranges[i]["dispatch"][:40]:
                            if 0 <= off < len(self.dispatch) and self.dispatch[off]:
                                j = self.range_index(self.dispatch[off])
                                if j is None or j in visited_ranges or self.range_name(j):
                                    continue
                                visited_ranges.add(j)
                                sub.append(self.describe_range(j))
                        if sub:
                            fe["callee_ranges"] = sub
                fns.append(fe)
            entry["functions"] = fns
            res["classes"].append(entry)
        return res


def main():
    so, model_dir, out = sys.argv[1:4]
    prefixes = [a for a in sys.argv[4:] if not a.startswith("--")]
    hops = 1; maxfn = 400
    for a in sys.argv[4:]:
        if a.startswith("--hops="): hops = int(a.split("=")[1])
        if a.startswith("--maxfn="): maxfn = int(a.split("=")[1])
    rp = Reporter(so, model_dir)
    res = rp.digest(prefixes, hops, maxfn)
    json.dump(res, open(out, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
    print("clases:", res["num_classes"], "->", out)


if __name__ == "__main__":
    main()
