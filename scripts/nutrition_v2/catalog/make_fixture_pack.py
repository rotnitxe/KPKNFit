"""Recorta el paquete de catálogo v0 a unas pocas fichas para los tests de Android (F4-B).

Copia el esquema exacto del original (sqlite_master, incluida la tabla virtual alias_fts) y solo las filas de los
ids pedidos en record, nutrient_value, portion, product, alias y alias_fts. `metadata.generation` pasa a ser
'fixture-' + los 12 primeros hex del sha256 de las filas copiadas. Escribe en --out-dir:
  fixture.sqlite, fixture.sqlite.gz (gzip determinista, mtime=0) y fixture.json (manifiesto de F4 §3a).

Comando exacto usado para generar los archivos de test:
  python -I scripts/nutrition_v2/catalog/make_fixture_pack.py --src C:/kw/nutri-build/nutrition_catalog_v0.sqlite \
    --out-dir android-native/app/src/test/resources/nutrition_catalog \
    --ids usda_171265,usda_2706489,usda_2705411,usda_1999630,usda_1999631,usda_1104812,ciqual_12060,ciqual_10023,ciqual_12001,off_0011210115644,off_0021000026326,off_7802080001129
"""
import argparse
import gzip
import hashlib
import json
import os
import sqlite3
import sys

TABLES = ["metadata", "record", "nutrient_value", "portion", "product", "alias"]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--ids", required=True)
    args = ap.parse_args()
    ids = [i for i in args.ids.split(",") if i]
    os.makedirs(args.out_dir, exist_ok=True)
    out = os.path.join(args.out_dir, "fixture.sqlite")
    for suffix in ("", "-journal", "-wal", "-shm"):
        if os.path.exists(out + suffix):
            os.remove(out + suffix)

    src = sqlite3.connect("file:" + args.src.replace("\\", "/") + "?mode=ro", uri=True)
    schema = {name: sql for name, sql in src.execute(
        "SELECT name, sql FROM sqlite_master WHERE type IN ('table')"
    ) if sql and not name.startswith("alias_fts_")}
    src.close()

    db = sqlite3.connect(out)
    db.execute("PRAGMA journal_mode=DELETE")
    for name in TABLES + ["alias_fts"]:
        db.execute(schema[name])
    db.execute("ATTACH DATABASE ? AS src", (args.src,))
    marks = ",".join("?" * len(ids))
    db.execute(f"INSERT INTO record SELECT * FROM src.record WHERE id IN ({marks}) ORDER BY id", ids)
    found = {r[0] for r in db.execute("SELECT id FROM record")}
    missing = [i for i in ids if i not in found]
    if missing:
        print("ids que no existen en el paquete:", missing, file=sys.stderr)
        return 1
    db.execute(f"INSERT INTO nutrient_value SELECT * FROM src.nutrient_value WHERE record_id IN ({marks}) ORDER BY record_id, nutrient_id", ids)
    db.execute(f"INSERT INTO portion SELECT * FROM src.portion WHERE record_id IN ({marks}) ORDER BY record_id, seq", ids)
    db.execute(f"INSERT INTO product SELECT * FROM src.product WHERE record_id IN ({marks}) ORDER BY gtin", ids)
    db.execute(f"INSERT INTO alias SELECT * FROM src.alias WHERE record_id IN ({marks}) ORDER BY rowid", ids)
    db.execute(f"INSERT INTO alias_fts(alias_norm, record_id) SELECT alias_norm, record_id FROM src.alias_fts WHERE record_id IN ({marks}) ORDER BY docid", ids)
    db.execute("INSERT INTO metadata SELECT * FROM src.metadata WHERE key <> 'generation'")

    digest = hashlib.sha256()
    for table, order in [("record", "id"), ("nutrient_value", "record_id, nutrient_id"), ("portion", "record_id, seq"),
                         ("product", "gtin"), ("alias", "record_id, alias, kind, lang")]:
        for row in db.execute(f"SELECT * FROM {table} ORDER BY {order}"):
            digest.update(repr(row).encode("utf-8"))
    generation = "fixture-" + digest.hexdigest()[:12]
    db.execute("INSERT INTO metadata(key, value) VALUES('generation', ?)", (generation,))
    format_version = int(db.execute("SELECT value FROM metadata WHERE key='formatVersion'").fetchone()[0])
    records = db.execute("SELECT COUNT(*) FROM record").fetchone()[0]
    db.commit()
    db.execute("DETACH DATABASE src")
    db.execute("VACUUM")
    db.close()

    raw = open(out, "rb").read()
    with open(out + ".gz", "wb") as fh:
        with gzip.GzipFile(filename="", mode="wb", fileobj=fh, mtime=0, compresslevel=9) as gz:
            gz.write(raw)
    manifest = {
        "generation": generation,
        "sha256": hashlib.sha256(raw).hexdigest(),
        "bytes": len(raw),
        "formatVersion": format_version,
        "calculableIdentities": records,
    }
    with open(os.path.join(args.out_dir, "fixture.json"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump(manifest, fh, indent=2)
        fh.write("\n")
    print(json.dumps(manifest))
    return 0


if __name__ == "__main__":
    sys.exit(main())
