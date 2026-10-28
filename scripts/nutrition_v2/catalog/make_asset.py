"""Empaqueta el paquete de catálogo completo como asset de la app (F5, decisiones N1 y F4-10).

Escribe en --out-dir `nutrition_catalog.sqlite.gz` (gzip determinista, mtime=0) y `nutrition_catalog.json`
(manifiesto de F4 §3a: generación, sha256 y bytes del archivo SIN comprimir, formatVersion, fichas calculables),
los dos nombres que lee `NutritionCatalogPublisher`. La generación es la del paquete (`metadata.generation`).

  python -I scripts/nutrition_v2/catalog/make_asset.py --src C:/kw/nutri-build/nutrition_catalog_v0.sqlite \
    --out-dir android-native/app/src/main/assets/food_data
"""
import argparse
import gzip
import hashlib
import json
import os
import sqlite3
import sys


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out-dir", required=True)
    args = ap.parse_args(argv)
    db = sqlite3.connect("file:" + args.src.replace("\\", "/") + "?mode=ro", uri=True)
    try:
        meta = dict(db.execute("SELECT key, value FROM metadata").fetchall())
        records = db.execute("SELECT COUNT(DISTINCT record_id) FROM nutrient_value WHERE nutrient_id = 1").fetchone()[0]
    finally:
        db.close()
    if not meta.get("generation") or not meta.get("formatVersion"):
        print("el paquete no trae generation/formatVersion en metadata", file=sys.stderr)
        return 1
    with open(args.src, "rb") as fh:
        raw = fh.read()
    os.makedirs(args.out_dir, exist_ok=True)
    with open(os.path.join(args.out_dir, "nutrition_catalog.sqlite.gz"), "wb") as fh:
        with gzip.GzipFile(filename="", mode="wb", fileobj=fh, mtime=0, compresslevel=9) as gz:
            gz.write(raw)
    manifest = {
        "generation": meta["generation"],
        "sha256": hashlib.sha256(raw).hexdigest(),
        "bytes": len(raw),
        "formatVersion": int(meta["formatVersion"]),
        "calculableIdentities": records,
    }
    with open(os.path.join(args.out_dir, "nutrition_catalog.json"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump(manifest, fh, indent=2)
        fh.write("\n")
    print(json.dumps(manifest))
    return 0


if __name__ == "__main__":
    sys.exit(main())
