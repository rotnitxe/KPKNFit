"""Etapa 2: candidatos por query. python candidates.py --in stage1.jsonl --out stage2.jsonl [-k 6]"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "exam"))


def candidate_row(ref):
    p = ref.per100g
    return {
        "ref": ref.id, "source": ref.source, "name": ref.name,
        "kcal": p.get("kcal"), "protein": p.get("protein"), "fat": p.get("fat"), "carbs": p.get("carbs"),
        "portions": ["%s = %g" % (label, grams) for label, grams in ref.portions[:4]],
    }


def search_candidates(index, query, k):
    return [candidate_row(ref) for _, ref in index.search(query, k=k)]


def process(index, in_path, out_path, k=6):
    n = q = 0
    with open(in_path, encoding="utf-8") as src, open(out_path, "w", encoding="utf-8", newline="\n") as dst:
        for line in src:
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            for key in ("variants", "components"):
                for item in obj.get(key) or []:
                    if isinstance(item, dict) and item.get("query"):
                        item["candidates"] = search_candidates(index, item["query"], k)
                        q += 1
            dst.write(json.dumps(obj, ensure_ascii=False) + "\n")
            n += 1
    return n, q


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="in_path", required=True)
    ap.add_argument("--out", dest="out_path", required=True)
    ap.add_argument("-k", type=int, default=6)
    args = ap.parse_args(argv)
    import refindex
    index = refindex.load_index()
    n, q = process(index, args.in_path, args.out_path, args.k)
    print("%d conceptos, %d consultas -> %s" % (n, q, args.out_path))
    return 0


if __name__ == "__main__":
    for s in (sys.stdout, sys.stderr):
        if hasattr(s, "reconfigure"):
            s.reconfigure(encoding="utf-8")
    sys.exit(main())
