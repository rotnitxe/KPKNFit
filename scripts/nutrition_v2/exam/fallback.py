"""Combina dos archivos de predicciones como lo hace la app con el interruptor v2 encendido (F5-b): si v2 falla o no
resuelve ninguna mención de un caso, ese caso usa la predicción de v1. La latencia del caso que cae a v1 es la suma de
las dos, porque en la app corren una detrás de otra.

  python -I scripts/nutrition_v2/exam/fallback.py --primary <v2.jsonl> --fallback <master.jsonl> \
    --system "v2app@..." --out C:/kw/nutri-examen/predictions/<archivo>.jsonl
"""
import argparse
import json
import sys


def read_jsonl(path):
    with open(path, encoding="utf-8") as fh:
        return [json.loads(line) for line in fh if line.strip()]


def resolved(mention):
    """Igual que ResolvedTagAdapterV2: ficha, gramos y al menos un valor por 100 g."""
    return bool(mention.get("foodId")) and mention.get("grams") is not None and bool(
        {k: v for k, v in (mention.get("per100g") or {}).items() if v is not None})


def combine(primary, fallback, system):
    """Devuelve (predicciones, cuantos casos cayeron a v1). Un caso sin pareja en fallback se queda con v2."""
    by_id = {p["id"]: p for p in fallback}
    out, fell = [], 0
    for p in primary:
        use_v2 = not p.get("error") and any(resolved(m) for m in p.get("mentions") or [])
        alt = by_id.get(p["id"])
        if use_v2 or alt is None:
            row = dict(p)
        else:
            fell += 1
            row = dict(alt)
            row["latencyMs"] = (p.get("latencyMs") or 0.0) + (alt.get("latencyMs") or 0.0)
        row["system"] = system
        out.append(row)
    return out, fell


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--primary", required=True)
    ap.add_argument("--fallback", required=True)
    ap.add_argument("--system", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args(argv)
    rows, fell = combine(read_jsonl(args.primary), read_jsonl(args.fallback), args.system)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        for r in rows:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    print("%d casos, %d usan v1" % (len(rows), fell))
    return 0


if __name__ == "__main__":
    sys.exit(main())
