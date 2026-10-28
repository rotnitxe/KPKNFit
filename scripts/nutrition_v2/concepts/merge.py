"""Fusion: python merge.py --in-dir stage3 --out concepts.jsonl --ambiguous ambiguous.jsonl --report report.json"""
import argparse
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import schema  # noqa: E402


def merge(paths):
    """Devuelve (conceptos_ok_ordenados, ambiguos, informe)."""
    accepted, rejected = {}, []
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
            if not errs and cid in accepted:
                errs = ["id repetido (ya en %s)" % accepted[cid][0]]
            if errs:
                rejected.append({"where": loc, "id": cid, "errors": errs})
                continue
            clean = dict(obj)
            for key in ("variants", "components"):
                if clean.get(key):
                    clean[key] = [{k: v for k, v in it.items() if k != "candidates"} for it in clean[key]]
            accepted[cid] = (loc, clean)
    concepts = [accepted[i][1] for i in sorted(accepted)]
    collisions = schema.find_collisions(concepts)
    ambiguous = [{"alias": a, "concepts": ids} for a, ids in sorted(collisions.items())]
    by_cat, alias_counts = {}, {"es": 0, "en": 0, "typos": 0}
    for c in concepts:
        by_cat[c["category"]] = by_cat.get(c["category"], 0) + 1
        for _, kind in schema.alias_entries(c):
            alias_counts[kind] += 1
    report = {
        "read": read, "accepted": len(concepts), "rejected": rejected,
        "byCategory": dict(sorted(by_cat.items())), "aliasesByType": alias_counts,
        "ambiguousAliases": len(ambiguous),
    }
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
    args = ap.parse_args(argv)
    paths = sorted(glob.glob(os.path.join(args.in_dir, "*.jsonl")))
    concepts, ambiguous, report = merge(paths)
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
