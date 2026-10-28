"""Canonical USDA adapters. Absence and every source portion remain explicit."""
import csv
import io
import json
import re
from pathlib import Path
import zipfile
from build_nutrition_catalog_pack import digest, number, normalize

# Nutrient IDs and priority are per-source facts, not food-name heuristics.
TARGETS = {
    1008: ("energy", 1, "kcal", 1000), 2047: ("energy", 2, "kcal", 1000), 2048: ("energy", 3, "kcal", 1000),
    1003: ("protein", 1, "g", 100), 1004: ("fat", 2, "g", 100), 1085: ("fat", 1, "g", 100),
    1005: ("carbs", 1, "g", 100), 1079: ("fiber", 1, "g", 100),
    2000: ("sugar", 2, "g", 100), 1063: ("sugar", 1, "g", 100),
    1093: ("sodium", 1, "mg", 100000), 1092: ("potassium", 1, "mg", 100000),
    1051: ("water", 1, "g", 100), 1057: ("caffeine", 1, "mg", 100000),
    1018: ("alcohol", 1, "g", 100),
}
REQUIRED = ("energy", "protein", "carbs", "fat")
UNITS = {target[0]: target[2] for target in TARGETS.values()}


def nutrient_values(rows):
    values, priorities = {}, {}
    for nutrient_id, raw in rows:
        target = TARGETS.get(int(nutrient_id))
        if target is None:
            continue
        key, priority, unit, maximum = target
        value = number(raw, maximum)
        if value is None or priority <= priorities.get(key, -1):
            continue
        priorities[key] = priority
        values[key] = {"value": value, "kind": "DECLARED", "unit": unit}
        if int(nutrient_id) in (2047, 2048):
            values[key].update(kind="DERIVED", method="USDA_ATWATER_GENERAL" if int(nutrient_id) == 2047 else "USDA_ATWATER_SPECIFIC")
    # Absence is never inferred to mean zero, even for oils or salt.
    for key, unit in UNITS.items():
        values.setdefault(key, {"value": None, "kind": "MISSING", "unit": unit})
    return values


def portion(row, record_id):
    grams = number(row.get("gramWeight"), 100000)
    if grams is None or grams <= 0:
        return None
    unit = row.get("measureUnit") or {}
    label = row.get("portionDescription") or row.get("modifier") or unit.get("name") or ""
    unit_name = unit.get("name")
    amount = number(row.get("amount", row.get("value")), 100000)
    return {"id": str(row.get("id", "")), "label": str(label), "unit": unit_name,
            "amount": amount, "grams": grams, "milliliters": None,
            "sourceRecordId": str(record_id)}


def aliases_from_csv(path):
    if path is None:
        return {}
    with Path(path).open(encoding="utf-8-sig", newline="") as stream:
        return {str(row["fdc_id"]): [x.strip() for x in [row.get("es_name", "")] + row.get("aliases", "").split("|") if x.strip()]
                for row in csv.DictReader(stream)}


def canonical(food, source, release, aliases):
    record_id = str(food["fdcId"])
    english = food["description"].strip()
    spanish = aliases.get(record_id, [])
    n = normalize(english)
    words = set(re.findall(r"[a-z]+", n))
    # Form and preparation are separate. A powder is not automatically a cooked food.
    physical_form = "POWDER" if words & {"powder", "powdered"} else "UNKNOWN"
    preparation = "RAW" if "raw" in words else "AS_SOLD"
    if words & {"prepared", "reconstituted"}:
        preparation = "PREPARED"
    elif words & {"cooked", "boiled", "roasted", "fried", "baked"}:
        preparation = "COOKED"
    nutrients = nutrient_values((row["nutrient"]["id"], row.get("amount")) for row in food.get("foodNutrients", []))
    category = food.get("foodCategory") or food.get("wweiaFoodCategory") or {}
    category_name = category.get("description") or category.get("wweiaFoodCategoryDescription")
    portions = [p for row in food.get("foodPortions", []) if (p := portion(row, record_id)) is not None]
    return {"id": "usda_" + record_id, "source": source, "sourceRecordId": record_id,
            "sourceRelease": release, "name": spanish[0] if spanish else english, "brand": None,
            "aliases": sorted(set([english] + spanish)), "spanishName": bool(spanish),
            "categories": [category_name] if category_name else [], "regions": ["US"], "languages": ["en"] + (["es"] if spanish else []),
            "denominator": {"amount": 100, "unit": "g", "preparation": preparation},
            "physicalForm": physical_form, "reconstitution": None, "portions": portions,
            "carbohydrateConvention": "TOTAL_BY_DIFFERENCE",
            "nutrients": nutrients, "calculable": all(nutrients[k]["value"] is not None for k in REQUIRED)}


def read_json_archive(path, source, release, aliases):
    with zipfile.ZipFile(path) as archive:
        names = [name for name in archive.namelist() if name.endswith(".json")]
        if len(names) != 1:
            raise ValueError("Expected exactly one USDA JSON document")
        with archive.open(names[0]) as stream:
            document = json.load(stream)
        for food in next(iter(document.values())):
            if isinstance(food, dict) and food.get("description"):
                yield canonical(food, source, release, aliases)
            else:
                yield None  # The builder reports invalid source records explicitly.


def read_sr_archive(path, release, aliases):
    with zipfile.ZipFile(path) as archive:
        def rows(filename):
            name = next(name for name in archive.namelist() if name.endswith("/" + filename))
            with archive.open(name) as stream, io.TextIOWrapper(stream, encoding="utf-8-sig", newline="") as text:
                yield from csv.DictReader(text)
        nutrients, portions = {}, {}
        units = {row["id"]: row["name"] for row in rows("measure_unit.csv")}
        categories = {row["id"]: row["description"] for row in rows("food_category.csv")}
        for row in rows("food_nutrient.csv"):
            if int(row["nutrient_id"]) in TARGETS:
                nutrients.setdefault(row["fdc_id"], []).append({"nutrient": {"id": int(row["nutrient_id"])}, "amount": row["amount"]})
        for row in rows("food_portion.csv"):
            portions.setdefault(row["fdc_id"], []).append({"id": row["id"], "amount": row["amount"],
                "gramWeight": row["gram_weight"], "portionDescription": row["portion_description"],
                "modifier": row["modifier"], "measureUnit": {"name": units.get(row["measure_unit_id"])}})
        for row in rows("food.csv"):
            if row["data_type"] != "sr_legacy_food":
                continue
            food = {"fdcId": row["fdc_id"], "description": row["description"],
                    "foodNutrients": nutrients.get(row["fdc_id"], []), "foodPortions": portions.get(row["fdc_id"], []),
                    "foodCategory": {"description": categories.get(row["food_category_id"])}}
            yield canonical(food, "USDA_SR_LEGACY", release, aliases)


def locked_sources(directory, lock_path):
    lock = json.loads(Path(lock_path).read_text(encoding="utf-8"))
    for source in sorted(lock):
        metadata = lock[source]
        filename = Path(metadata["file"])
        if filename.name != str(filename):
            raise ValueError("Source filenames must be local archive basenames")
        if digest(Path(directory) / filename) != metadata["sha256"]:
            raise ValueError(source + ": archive hash differs from the source lock")
    return lock


def records(directory, lock, alias_path=None):
    aliases = aliases_from_csv(alias_path)
    for source in sorted(lock):
        info = lock[source]
        if source == "USDA_SR_LEGACY":
            yield from read_sr_archive(Path(directory) / info["file"], info["release"], aliases)
        elif source in ("USDA_FOUNDATION", "USDA_FNDDS"):
            yield from read_json_archive(Path(directory) / info["file"], source, info["release"], aliases)
        else:
            raise ValueError("Unsupported locked source: " + source)
