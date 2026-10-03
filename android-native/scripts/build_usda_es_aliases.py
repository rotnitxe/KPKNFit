#!/usr/bin/env python3
"""Genera la tabla de nombres y alias en español de los alimentos USDA (WP-S9).

Salida: android-native/app/src/main/assets/food_data/usda_es_aliases.csv, con una fila por alimento
`foundation_food` de food.csv y las columnas

    fdc_id,en_description,es_name,aliases

`fdc_id` y `en_description` salen de food.csv (ordenadas por fdc_id, con los espacios duros y repetidos normalizados).
`es_name` y `aliases` (sinónimos separados por `|`) se curan a mano y SOBREVIVEN a la regeneración: el script lee la
tabla existente y conserva lo que ya está escrito. Los alimentos nuevos entran con `es_name` vacío (en la app se
quedan con su nombre en inglés hasta que se curen) y los que ya no son foundation_food salen de la tabla.

Convenciones de la curación (las verifica UsdaAliasTableTest):
  * nombres cortos en español de Chile que conservan lo crudo/cocido y el nivel de grasa de la descripción en inglés;
  * ningún es_name se repite (se comparan normalizados: sin tildes, en minúsculas y sin signos);
  * un alias pertenece a UNA sola fila, no repite ningún nombre ni la descripción en inglés y son 4 como máximo;
  * food.csv publica varias veces el mismo alimento (2019-04 y 2019-12, lotes de 2020 a 2022): el de mayor fdc_id
    lleva el nombre limpio y los demás terminan en " (FDC <id>)" y no llevan alias.

Uso (solo biblioteca estándar):

    python android-native/scripts/build_usda_es_aliases.py            # reescribe la tabla
    python android-native/scripts/build_usda_es_aliases.py --check    # sale con 1 si la tabla no es lo que se regeneraría
    python android-native/scripts/build_usda_es_aliases.py --missing  # lista las filas sin es_name

La app lee la tabla en FoodImporter.parseUsdaAliases; los tests están en UsdaAliasTableTest.
"""
import argparse
import csv
import io
import sys
from pathlib import Path

FOOD_DATA = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "food_data"
HEADER = ["fdc_id", "en_description", "es_name", "aliases"]
FOUNDATION = "foundation_food"


def clean_text(value):
    """Colapsa espacios (incluidos los duros U+00A0, que str.split() trata como blancos) y recorta."""
    return " ".join(value.split())


def read_foundation_foods(food_csv):
    """fdc_id -> descripción en inglés de los foundation_food de food.csv."""
    foods = {}
    with open(food_csv, encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            if row["data_type"] == FOUNDATION:
                foods[int(row["fdc_id"])] = clean_text(row["description"])
    return foods


def read_curation(table_csv):
    """fdc_id -> (es_name, aliases) de la tabla existente; vacío si todavía no existe."""
    curation = {}
    if not table_csv.exists():
        return curation
    with open(table_csv, encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            curation[int(row["fdc_id"])] = (clean_text(row.get("es_name") or ""), (row.get("aliases") or "").strip())
    return curation


def build_rows(foods, curation):
    """Filas de la tabla: ordenadas por fdc_id, con la curación existente intacta."""
    rows = []
    for fdc_id in sorted(foods):
        es_name, aliases = curation.get(fdc_id, ("", ""))
        rows.append([str(fdc_id), foods[fdc_id], es_name, aliases])
    return rows


def render(rows):
    """Texto CSV (UTF-8, saltos LF, comillas solo cuando hacen falta)."""
    buffer = io.StringIO(newline="")
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(HEADER)
    writer.writerows(rows)
    return buffer.getvalue()


def main(argv=None):
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--food", type=Path, default=FOOD_DATA / "food.csv", help="food.csv de USDA")
    parser.add_argument("--out", type=Path, default=FOOD_DATA / "usda_es_aliases.csv", help="tabla a generar")
    parser.add_argument("--check", action="store_true", help="no escribe: falla si la tabla difiere de lo regenerado")
    parser.add_argument("--missing", action="store_true", help="no escribe: lista las filas sin es_name")
    args = parser.parse_args(argv)

    foods = read_foundation_foods(args.food)
    curation = read_curation(args.out)
    orphans = sorted(set(curation) - set(foods))
    rows = build_rows(foods, curation)

    if args.missing:
        missing = [row for row in rows if not row[2]]
        for row in missing:
            print(row[0], row[1])
        print(f"{len(missing)} de {len(rows)} filas sin es_name", file=sys.stderr)
        return 0

    text = render(rows)
    if args.check:
        current = args.out.read_bytes().decode("utf-8").replace("\r\n", "\n") if args.out.exists() else ""
        if current != text:
            print(f"{args.out} no coincide con lo que se regeneraría; ejecuta el script sin --check", file=sys.stderr)
            return 1
        print(f"ok: {len(rows)} filas")
        return 0

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="") as handle:
        handle.write(text)
    named = sum(1 for row in rows if row[2])
    print(f"{args.out}: {len(rows)} filas, {named} con es_name, {len(orphans)} huérfanas descartadas")
    return 0


if __name__ == "__main__":
    sys.exit(main())
