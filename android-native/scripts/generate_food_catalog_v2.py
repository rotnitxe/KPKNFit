#!/usr/bin/env python3
"""Build the provenance-preserving KPKN food catalog assets.

The script intentionally accepts small, normalized CSV extracts instead of
editing the large source datasets in place.  It emits deterministic gzip CSVs
and a manifest with input/output checksums, accepted/rejected rows and source
metadata.  It uses only the Python standard library so it can run in the
offline Android repository tooling.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
import math
import os
import re
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Iterator


SCHEMA_VERSION = 2
CATALOG_COLUMNS = [
    "source", "sourceRecordId", "name", "brand", "normalizedName", "category",
    "nutritionBasis", "foodState", "servingSize", "servingUnit", "calories",
    "protein", "carbs", "fats", "portionGrams", "portionUnit", "qualityFlags", "aliases",
]
PORTION_COLUMNS = ["source", "sourceRecordId", "foodRecordId", "label", "grams", "unit", "basis"]


def normalize(value: str) -> str:
    value = (value or "").strip().lower()
    value = re.sub(r"[^\w\s]+", " ", value, flags=re.UNICODE)
    return re.sub(r"\s+", " ", value).strip()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def open_text(path: Path):
    if path.suffix.lower() == ".gz":
        return gzip.open(path, "rt", encoding="utf-8-sig", newline="")
    return path.open("r", encoding="utf-8-sig", newline="")


def read_rows(path: Path | None) -> Iterator[dict[str, str]]:
    if path is None:
        return iter(())
    with open_text(path) as handle:
        sample = handle.read(4096)
        handle.seek(0)
        try:
            dialect = csv.Sniffer().sniff(sample, delimiters=",\t;")
        except csv.Error:
            dialect = csv.excel
        yield from csv.DictReader(handle, dialect=dialect)


def first(row: dict[str, str], *names: str) -> str:
    lowered = {str(key).strip().lower(): (value or "") for key, value in row.items()}
    for name in names:
        value = lowered.get(name.lower())
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def number(value: str, default: float | None = None) -> float | None:
    if value is None or not str(value).strip():
        return default
    try:
        parsed = float(str(value).replace(",", ".").strip())
    except ValueError:
        return default
    return parsed if math.isfinite(parsed) else default


def record_id(row: dict[str, str]) -> str:
    return first(row, "sourceRecordId", "fdc_id", "fdcid", "id", "code", "barcode", "product_code")


def food_state(name: str, basis: str = "") -> str:
    text = normalize(f"{name} {basis}")
    if re.search(r"\b(raw|crude|crudo|cruda|dry|dried|seco|seca)\b", text):
        return "RAW"
    if re.search(r"\b(cooked|cocido|cocida|boiled|braised|baked|fried|grilled|roasted|steamed|smoked)\b", text):
        return "COOKED"
    return "UNKNOWN"


def macro_value(row: dict[str, str], *names: str) -> float | None:
    return number(first(row, *names))


def validate_food(food: dict[str, str]) -> str | None:
    for field in ("calories", "protein", "carbs", "fats"):
        value = number(food.get(field, ""))
        if value is None:
            return f"missing_{field}"
        if value < 0:
            return f"negative_{field}"
        if field != "calories" and value > 100:
            return f"macro_over_100_{field}"
    grams = number(food.get("portionGrams", ""))
    if grams is not None and grams <= 0:
        return "invalid_portion"
    if not food.get("name", "").strip() or not food.get("sourceRecordId", "").strip():
        return "missing_identity"
    return None


def usda_rows(food_path: Path | None, nutrient_path: Path | None, portion_path: Path | None, version: str):
    nutrients: dict[str, dict[str, float]] = {}
    for row in read_rows(nutrient_path):
        key = record_id(row)
        if not key:
            continue
        name = normalize(first(row, "nutrient_name", "nutrient", "nutrientLabel", "name"))
        value = number(first(row, "amount", "value", "nutrient_amount"))
        if value is None:
            continue
        bucket = nutrients.setdefault(key, {})
        if "energy" in name or "calorie" in name or "kcal" in name:
            bucket["calories"] = value
        elif "protein" in name:
            bucket["protein"] = value
        elif "carbohydrate" in name or name in {"carbs", "carbohydrates"}:
            bucket["carbs"] = value
        elif "fat" in name and "saturated" not in name:
            bucket["fats"] = value

    portions: dict[str, tuple[float, str]] = {}
    for row in read_rows(portion_path):
        key = record_id(row)
        grams = number(first(row, "gram_weight", "gramWeight", "grams", "portionGrams", "weight"))
        if key and grams is not None and grams > 0 and key not in portions:
            portions[key] = (grams, first(row, "modifier", "portion_description", "unit") or "serving")

    for row in read_rows(food_path):
        key = record_id(row)
        if not key:
            continue
        name = first(row, "description", "name", "food_description", "product_name")
        nutrient = nutrients.get(key, {})
        merged = {
            "source": "USDA",
            "sourceRecordId": key,
            "name": name,
            "brand": first(row, "brand_name", "brand", "brand_owner"),
            "category": first(row, "food_category", "category", "category_name"),
            "nutritionBasis": "PER_100G_AS_SOLD",
            "foodState": food_state(name, first(row, "data_type", "basis")),
            "datasetVersion": version,
            "servingSize": "100",
            "servingUnit": "g",
            "calories": str(macro_value(row, "calories", "energy_kcal", "energy") or nutrient.get("calories", "")),
            "protein": str(macro_value(row, "protein", "protein_g") or nutrient.get("protein", "")),
            "carbs": str(macro_value(row, "carbs", "carbohydrate", "carbohydrates") or nutrient.get("carbs", "")),
            "fats": str(macro_value(row, "fats", "fat", "total_lipid") or nutrient.get("fats", "")),
            "portionGrams": str(portions.get(key, ("", ""))[0]),
            "portionUnit": portions.get(key, ("", ""))[1],
            "qualityFlags": "",
            "aliases": "",
        }
        yield merged


def off_rows(path: Path | None, version: str):
    for row in read_rows(path):
        key = record_id(row)
        name = first(row, "product_name", "product_name_en", "name")
        merged = {
            "source": "OFF",
            "sourceRecordId": key,
            "name": name,
            "brand": first(row, "brands", "brand"),
            "category": first(row, "categories", "category"),
            "nutritionBasis": "PER_100G_AS_SOLD",
            "foodState": food_state(name),
            "datasetVersion": version,
            "servingSize": "100",
            "servingUnit": "g",
            "calories": first(row, "energy-kcal_100g", "energy_kcal_100g", "calories_100g"),
            "protein": first(row, "proteins_100g", "protein_100g", "protein"),
            "carbs": first(row, "carbohydrates_100g", "carbs_100g", "carbohydrates"),
            "fats": first(row, "fat_100g", "fats_100g", "fat"),
            "portionGrams": "",
            "portionUnit": "",
            "qualityFlags": "",
            "aliases": first(row, "generic_name", "categories"),
        }
        yield merged


def write_gzip_csv(path: Path, columns: list[str], rows: Iterable[dict[str, str]]) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    os.close(fd)
    temporary_path = Path(temporary)
    count = 0
    try:
        with temporary_path.open("wb") as raw:
            with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0) as compressed:
                text = compressed if False else None  # keep the writer explicit for Python 3.10
                import io
                wrapper = io.TextIOWrapper(compressed, encoding="utf-8", newline="")
                writer = csv.DictWriter(wrapper, fieldnames=columns, extrasaction="ignore", lineterminator="\n")
                writer.writeheader()
                for row in rows:
                    writer.writerow({column: row.get(column, "") for column in columns})
                    count += 1
                wrapper.flush()
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)
    return count


def write_json_atomic(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    os.close(fd)
    temporary_path = Path(temporary)
    try:
        temporary_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


def generate(args: argparse.Namespace) -> dict:
    output = Path(args.output_dir)
    output.mkdir(parents=True, exist_ok=True)
    version = args.dataset_version
    accepted: list[dict[str, str]] = []
    rejected: dict[str, int] = {}
    seen: set[tuple[str, str]] = set()
    for row in (*usda_rows(args.usda_food, args.usda_nutrient, args.usda_portions, version), *off_rows(args.off, version)):
        row["normalizedName"] = normalize(row.get("name", ""))
        issue = validate_food(row)
        key = (row.get("source", ""), row.get("sourceRecordId", ""))
        if issue:
            rejected[issue] = rejected.get(issue, 0) + 1
        elif key in seen:
            rejected["duplicate_source_record"] = rejected.get("duplicate_source_record", 0) + 1
        else:
            seen.add(key)
            accepted.append(row)

    accepted.sort(key=lambda row: (row.get("source", ""), row.get("sourceRecordId", "")))
    catalog_path = output / "food_catalog_v2.csv.gz"
    catalog_rows = write_gzip_csv(catalog_path, CATALOG_COLUMNS, accepted)
    portion_rows = [
        {
            "source": row.get("source", ""),
            "sourceRecordId": row.get("sourceRecordId", ""),
            "foodRecordId": f"{row.get('source', '').lower()}:{row.get('sourceRecordId', '')}",
            "label": row.get("portionUnit", ""),
            "grams": row.get("portionGrams", ""),
            "unit": "g",
            "basis": row.get("nutritionBasis", ""),
        }
        for row in accepted if number(row.get("portionGrams", ""))
    ]
    portions_path = output / "food_portions_v2.csv.gz"
    portions_written = write_gzip_csv(portions_path, PORTION_COLUMNS, portion_rows)
    generated_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "datasetVersion": version,
        "release": args.release,
        "generatedAt": generated_at,
        "generator": "generate_food_catalog_v2.py",
        "inputs": [
            {"path": str(path), "sha256": sha256(path)}
            for path in (args.usda_food, args.usda_nutrient, args.usda_portions, args.off)
            if path is not None and path.exists()
        ],
        "outputs": {
            "food_catalog_v2.csv.gz": {"sha256": sha256(catalog_path), "rows": catalog_rows},
            "food_portions_v2.csv.gz": {"sha256": sha256(portions_path), "rows": portions_written},
        },
        "acceptedRows": len(accepted),
        "rejectedRows": sum(rejected.values()),
        "rejectionReasons": rejected,
        "licenses": args.license,
    }
    write_json_atomic(output / "food_catalog_v2_manifest.json", manifest)
    return manifest


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--output-dir", type=Path, required=True)
    result.add_argument("--dataset-version", default="v2-local")
    result.add_argument("--release", default="unreleased")
    result.add_argument("--license", action="append", default=[])
    result.add_argument("--usda-food", type=Path)
    result.add_argument("--usda-nutrient", type=Path)
    result.add_argument("--usda-portions", type=Path)
    result.add_argument("--off", type=Path)
    return result


if __name__ == "__main__":
    arguments = parser().parse_args()
    print(json.dumps(generate(arguments), ensure_ascii=False, indent=2, sort_keys=True))
