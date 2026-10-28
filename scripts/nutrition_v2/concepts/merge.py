"""Fusion: python merge.py --in-dir stage3 --out concepts.jsonl --ambiguous ambiguous.jsonl --report report.json"""
import argparse
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import schema  # noqa: E402


def _variant_matches(v, spec):
    state = spec.get("state")
    return (v.get("state") or "NA") == (state or "NA") and (v.get("form") or "NA") == (spec.get("form") or "NA")


def _fold_aliases(dest, gone):
    """Copia a dest los alias de gone que aun no tiene; devuelve cuantos copio."""
    total = 0
    for key, _ in schema.ALIAS_FIELDS:
        have = {schema.normalize(a) for a in dest.get(key) or [] if isinstance(a, str)}
        moved = []
        for a in gone.get(key) or []:
            n = schema.normalize(a) if isinstance(a, str) else ""
            if n and n not in have:
                have.add(n)
                moved.append(a)
        if moved:
            dest[key] = list(dest.get(key) or []) + moved
            total += len(moved)
    return total


def apply_curation(raw, curation):
    """raw: {id: (loc, concepto)}; aplica la curacion en sitio. Devuelve el informe 'curation'."""
    rep = {"dropped": 0, "aliasesMoved": 0, "variantsDropped": 0, "aliasesDropped": 0, "warnings": []}
    curation = curation or {}
    for cid, target in (curation.get("drop") or {}).items():
        if cid not in raw:
            rep["warnings"].append("drop: id inexistente %s" % cid)
            continue
        _, gone = raw.pop(cid)
        rep["dropped"] += 1
        if target is None:
            continue
        if target not in raw:
            rep["warnings"].append("drop %s: destino inexistente %s (alias no traspasados)" % (cid, target))
            continue
        rep["aliasesMoved"] += _fold_aliases(raw[target][1], gone)
    for spec in curation.get("dropVariants") or []:
        cid = spec.get("id")
        if cid not in raw:
            rep["warnings"].append("dropVariants: id inexistente %s" % cid)
            continue
        c = raw[cid][1]
        vs = c.get("variants") or []
        kept = [v for v in vs if not (isinstance(v, dict) and _variant_matches(v, spec))]
        if len(kept) == len(vs):
            rep["warnings"].append("dropVariants: %s sin variante %s/%s" % (cid, spec.get("state"), spec.get("form")))
        else:
            rep["variantsDropped"] += len(vs) - len(kept)
            c["variants"] = kept
    for cid, aliases in (curation.get("dropAliases") or {}).items():
        if cid not in raw:
            rep["warnings"].append("dropAliases: id inexistente %s" % cid)
            continue
        c = raw[cid][1]
        drop = {schema.normalize(a) for a in aliases}
        for key, _ in schema.ALIAS_FIELDS:
            vals = c.get(key)
            if isinstance(vals, list):
                kept = [a for a in vals if not (isinstance(a, str) and schema.normalize(a) in drop)]
                rep["aliasesDropped"] += len(vals) - len(kept)
                c[key] = kept
    return rep


def merge(paths, curation=None):
    """Devuelve (conceptos_ok_ordenados, ambiguos, informe).

    Un id repetido en otro lote es el mismo concepto: se queda el primero (o el del archivo que diga
    curation["keepFrom"][id]) y los demas le ceden sus alias."""
    accepted, rejected, folded, extra = {}, [], [], {}
    read = 0
    for path in paths:
        for ln, obj, err in schema.read_jsonl(path):
            loc = "%s:%d" % (os.path.basename(path), ln)
            read += 1
            if err:
                rejected.append({"where": loc, "id": None, "errors": [err]})
                continue
            errs = schema.validate_concept(obj, 3)
            cid = obj.get("id") if isinstance(obj, dict) else None
            if errs:
                rejected.append({"where": loc, "id": cid, "errors": errs})
                continue
            clean = dict(obj)
            for key in ("variants", "components"):
                if clean.get(key):
                    clean[key] = [{k: v for k, v in it.items() if k != "candidates"} for it in clean[key]]
            if cid in accepted:
                extra.setdefault(cid, []).append((loc, clean))
                continue
            accepted[cid] = (loc, clean)
    keep_from = (curation or {}).get("keepFrom") or {}
    for cid, dups in sorted(extra.items()):
        group = [accepted[cid]] + dups
        want = keep_from.get(cid)
        winner = next((g for g in group if want and g[0].split(":")[0] == want), group[0])
        for loc, other in group:
            if loc != winner[0]:
                folded.append({"where": loc, "id": cid, "into": winner[0],
                               "aliasesMoved": _fold_aliases(winner[1], other)})
        accepted[cid] = winner
    cur_rep = apply_curation(accepted, curation) if curation else None
    # revalida tras la curacion (p. ej. aliasesEs vacio o variante por defecto quitada)
    for cid in sorted(accepted):
        errs = schema.validate_concept(accepted[cid][1], 3)
        if errs:
            rejected.append({"where": accepted[cid][0], "id": cid, "errors": errs})
            del accepted[cid]
    concepts = [accepted[i][1] for i in sorted(accepted)]
    collisions = schema.find_collisions(concepts)
    ambiguous = [{"alias": a, "concepts": ids} for a, ids in sorted(collisions.items())]
    by_cat, alias_counts = {}, {"es": 0, "en": 0, "typos": 0}
    for c in concepts:
        by_cat[c["category"]] = by_cat.get(c["category"], 0) + 1
        for _, kind in schema.alias_entries(c):
            alias_counts[kind] += 1
    report = {
        "read": read, "accepted": len(concepts), "rejected": rejected, "folded": folded,
        "byCategory": dict(sorted(by_cat.items())), "aliasesByType": alias_counts,
        "ambiguousAliases": len(ambiguous),
    }
    if cur_rep is not None:
        report["curation"] = cur_rep
    return concepts, ambiguous, report


def write_jsonl(path, rows):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--in-dir", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--ambiguous", required=True)
    ap.add_argument("--report", required=True)
    ap.add_argument("--curation", default=None)
    args = ap.parse_args(argv)
    paths = sorted(glob.glob(os.path.join(args.in_dir, "*.jsonl")))
    curation = None
    if args.curation:
        with open(args.curation, encoding="utf-8") as f:
            curation = json.load(f)
    concepts, ambiguous, report = merge(paths, curation)
    write_jsonl(args.out, concepts)
    write_jsonl(args.ambiguous, ambiguous)
    with open(args.report, "w", encoding="utf-8", newline="\n") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print("%d leidos, %d aceptados, %d rechazados, %d alias ambiguos" % (
        report["read"], report["accepted"], len(report["rejected"]), len(ambiguous)))
    return 0


if __name__ == "__main__":
    for s in (sys.stdout, sys.stderr):
        if hasattr(s, "reconfigure"):
            s.reconfigure(encoding="utf-8")
    sys.exit(main())
