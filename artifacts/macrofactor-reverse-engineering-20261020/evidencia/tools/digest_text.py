#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""digest_text.py — vista compacta en texto de un digest JSON (report.py).
Uso: python -I digest_text.py <digest.json> [--lib=substr] [--class=substr] [--full]"""
import sys, json

def short(v, n=90):
    s = json.dumps(v, ensure_ascii=False) if not isinstance(v, str) else v
    return s if len(s) <= n else s[:n] + "…"

def fmt_pool(pool, full):
    out = []
    for idx, pr in pool:
        if isinstance(pr, dict) and "imm" in pr:
            if "as_double" in pr and pr["as_double"] is not None and abs(pr["as_double"]) < 1e15:
                out.append("imm %s (%.6g)" % (pr["imm"], pr["as_double"]))
            else:
                out.append("imm %s" % pr["imm"])
        elif isinstance(pr, (int, float)):
            out.append(repr(pr))
        elif isinstance(pr, str):
            if pr.startswith("<type ") or pr.startswith("<closure") or pr.startswith("<function") or pr.startswith("<field") or pr.startswith("<class"):
                if full: out.append(pr)
            elif pr.startswith("<"):
                if full: out.append(pr)
            else:
                out.append("%r" % pr)
        elif isinstance(pr, dict):
            out.append(short(pr, 160 if full else 100))
        elif isinstance(pr, list):
            out.append(short(pr, 160 if full else 100))
        else:
            out.append(short(pr))
    return out

def render_range(c, indent, full):
    lines = []
    lines.append(indent + "code %s-%s (%d B)%s" % (c["range"][0], c["range"][1], c["size"], (" name=" + c["name"]) if c.get("name") else ""))
    pv = fmt_pool(c.get("pool", []), full)
    if pv:
        lines.append(indent + "  pool: " + "; ".join(pv)[:1500])
    if c.get("fimm"):
        lines.append(indent + "  fimm: " + ", ".join("%g" % x for x in c["fimm"][:40]))
    if c.get("imm"):
        lines.append(indent + "  imm: " + ", ".join(("%d(smi %d)" % (x, x // 2) if x % 2 == 0 and x != 0 else str(x)) for x in c["imm"][:40]))
    if c.get("cmp"):
        lines.append(indent + "  cmp: " + ", ".join(("%d(smi %d)" % (x, x // 2) if x % 2 == 0 and x != 0 else str(x)) for x in c["cmp"][:40]))
    if c.get("calls"):
        lines.append(indent + "  calls: " + "; ".join("%s@%s" % (n, a) for n, a in c["calls"][:25]))
    if c.get("dispatch_calls"):
        lines.append(indent + "  dispatch: " + "; ".join("%s@%s" % (n, a) for n, a, o in c["dispatch_calls"][:25]))
    return lines

def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    path = sys.argv[1]
    libf = None; clsf = None; full = False
    for a in sys.argv[2:]:
        if a.startswith("--lib="): libf = a.split("=", 1)[1]
        if a.startswith("--class="): clsf = a.split("=", 1)[1]
        if a == "--full": full = True
    dg = json.load(open(path, encoding="utf-8"))
    for c in dg["classes"]:
        if libf and libf not in (c.get("library") or ""): continue
        if clsf and clsf not in (c.get("name") or ""): continue
        print("=" * 100)
        print("CLASS %s  [cid %d]  lib=%s" % (c["name"], c["cid"], c["library"]))
        print("  super=%s  interfaces=%s  flags=%s  tparams=%s  size_words=%s" % (c.get("super"), c.get("interfaces"), c.get("flags"), c.get("type_parameters"), c.get("instance_size_words")))
        if c.get("fields"):
            for f in c["fields"]:
                print("  FIELD %s : %s  %s  %s" % (f["name"], f["type"], f["flags"], "id=%s" % f.get("field_id") if "field_id" in f else "off=%s" % f.get("offset_words")))
        dn = c.get("derived_names") or {}
        if any(dn.values()):
            print("  DERIVED methods=%s getters=%s setters=%s inits=%s" % (dn.get("methods"), dn.get("getters"), dn.get("setters"), dn.get("field_inits")))
        if c.get("enum_values"):
            for v in c["enum_values"]:
                print("  ENUM " + short(v, 300))
        if c.get("const_instances"):
            print("  CONST INSTANCES (%s):" % c.get("const_instances_count"))
            for v in c["const_instances"][:40]:
                print("    " + short(v, 400 if full else 250))
        for f in c.get("functions", []):
            print("  FN %s  kind=%s  sig=%s  flags=%s%s" % (f["name"], f["kind"], f.get("signature"), f.get("flags"), (" parent=" + f["parent_function"]) if f.get("parent_function") else ""))
            if f.get("code"):
                for l in render_range(f["code"], "     ", full): print(l)
            for sub in f.get("callee_ranges", []):
                for l in render_range(sub, "        -> ", full): print(l)

if __name__ == "__main__":
    main()
