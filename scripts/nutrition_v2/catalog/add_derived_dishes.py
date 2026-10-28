#!/usr/bin/env python3
"""Agrega al paquete de catalogo las fichas de platos `kpkn_*` del vocabulario v2 con sus nutrientes derivados.

Copia el paquete de entrada (que nunca se modifica), inserta una ficha `record` por cada id `kpkn_*` de la
exportacion del vocabulario y su vector `per100g` en `nutrient_value` con calidad Q_DERIVED. Sin porciones,
productos ni alias. Determinista: mismas entradas, mismo sha256.

    python -I scripts/nutrition_v2/catalog/add_derived_dishes.py --pack v0.sqlite --export concepts_export.json --out v1.sqlite
"""
import argparse
import hashlib
import json
import math
import shutil
import sqlite3
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

import nutrients as N  # noqa: E402

PREFIX = "kpkn_"


class DerivedError(Exception):
    pass


def file_sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def build_rows(export):
    categories = {c["id"]: c.get("category") for c in export.get("concepts", [])}
    records, values, skipped = [], [], Counter()
    for rid, card in export["records"].items():
        if not rid.startswith(PREFIX):
            continue
        name = card["name"]
        density = card.get("densityGPerMl")
        records.append((rid, "KPKN_DERIVED", rid[len(PREFIX):], name, name, None, None, None,
                        categories.get(rid[len(PREFIX):]), "PREPARED", "NA", 100.0, "g", density))
        for key, raw in sorted(card.get("per100g", {}).items()):
            nut = N.BY_KEY.get(key)
            if nut is None:
                skipped["clave_sin_id"] += 1
                continue
            if raw is None:
                skipped["nulo"] += 1
                continue
            amount = float(raw)
            if not math.isfinite(amount):
                skipped["no_finito"] += 1
            elif not (0 <= amount <= nut.max_value):
                skipped["fuera_de_rango"] += 1
            else:
                values.append((rid, nut.id, round(amount, 6), N.Q_DERIVED))
    return records, values, dict(skipped)


def run(pack, export_path, out):
    pack, out, export_path = Path(pack), Path(out), Path(export_path)
    export = json.loads(export_path.read_text(encoding="utf-8"))
    records, values, skipped = build_rows(export)
    if not records:
        raise DerivedError("la exportacion no trae ids kpkn_")
    out.parent.mkdir(parents=True, exist_ok=True)
    if out.exists():
        out.unlink()
    shutil.copyfile(pack, out)
    con = sqlite3.connect(str(out))
    try:
        ids = [r[0] for r in records]
        existing = [i for i in ids if con.execute("SELECT 1 FROM record WHERE id=?", (i,)).fetchone()]
        if existing:
            raise DerivedError("ya existen en el paquete %d ids kpkn_ (p. ej. %s)" % (len(existing), existing[0]))
        old_gen = con.execute("SELECT value FROM metadata WHERE key='generation'").fetchone()[0]
        con.executemany("INSERT INTO record VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", records)
        con.executemany("INSERT INTO nutrient_value VALUES (?,?,?,?)", values)
        rows_hash = hashlib.sha256(json.dumps({"record": records, "nutrient_value": values}, sort_keys=True,
                                              ensure_ascii=False, separators=(",", ":")).encode("utf-8")).hexdigest()
        new_gen = hashlib.sha256(("derived:" + old_gen + ":" + rows_hash).encode("utf-8")).hexdigest()[:16]
        con.executemany("INSERT OR REPLACE INTO metadata VALUES (?,?)", [
            ("generation", new_gen), ("derivedDishes", str(len(records))),
            ("derivedFrom", export.get("conceptsSha256", ""))])
        con.commit()
        check = con.execute("PRAGMA integrity_check").fetchone()[0]
        if check != "ok":
            raise DerivedError("integrity_check: " + check)
    except Exception:
        con.close()
        out.unlink(missing_ok=True)
        raise
    con.close()
    digest = file_sha256(out)
    out.with_name(out.name + ".sha256").write_text("%s  %s\n" % (digest, out.name), encoding="utf-8")
    return {"dishes": len(records), "values": len(values), "skipped": skipped, "oldGeneration": old_gen,
            "newGeneration": new_gen, "bytes": out.stat().st_size, "sha256": digest}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", required=True)
    ap.add_argument("--export", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args(argv)
    try:
        summary = run(args.pack, args.export, args.out)
    except DerivedError as e:
        print("error: %s" % e, file=sys.stderr)
        return 1
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
