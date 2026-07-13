#!/usr/bin/env python3
"""
Script para filtrar la base USDA FoodData Central y generar un JSON de alimentos
genéricos para uso offline (máx. 80MB).

Excluye: branded_food
Incluye: foundation_food, sr_legacy_food, survey_fndds_food

Uso:
  python scripts/build_usda_offline_db.py <ruta_carpeta_usda> [--output data/usdaFoodsOffline.json]
"""

import csv
import json
import os
import sys
from collections import defaultdict
from pathlib import Path

# Nutrientes esenciales: nutrient_nbr en nutrient.csv
# Macros: 208=Energy kcal, 203=Protein, 205=Carbohydrate, 204=Total lipid
# Micronutrientes: 303=Iron, 301=Calcium, 307=Sodium, 320=Vitamin A, 401=Vitamin C,
# 328=Vitamin D, 418=Vitamin B12, 304=Magnesium, 309=Zinc, 306=Potassium
NUTRIENT_NBRS = {
    208, 203, 204, 205,  # macros
    303, 301, 307, 320, 401, 328, 418, 304, 309, 306,  # micronutrientes
}
MAX_SIZE_MB = 80
PRIORITY_ORDER = ["foundation_food", "sr_legacy_food", "survey_fndds_food"]


def load_nutrient_mapping(nutrient_path: Path) -> dict[int, dict]:
    """Carga nutrient.csv y retorna {nutrient_id: {name, unit}} para NUTRIENT_NBRS."""
    mapping = {}
    if not nutrient_path.exists():
        print(f"Advertencia: {nutrient_path} no existe. Usando IDs directos.")
        return {
            1008: {"name": "Energy", "unit": "kcal"},
            1003: {"name": "Protein", "unit": "g"},
            1005: {"name": "Carbohydrate, by difference", "unit": "g"},
            1004: {"name": "Total lipid (fat)", "unit": "g"},
        }
    with open(nutrient_path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            nbr = row.get("nutrient_nbr", row.get("number", ""))
            try:
                nbr_int = int(nbr)
            except (ValueError, TypeError):
                continue
            if nbr_int in NUTRIENT_NBRS:
                nid = row.get("id", row.get("nutrient_id", ""))
                try:
                    nid_int = int(nid)
                except (ValueError, TypeError):
                    continue
                mapping[nid_int] = {
                    "name": row.get("name", ""),
                    "unit": row.get("unit_name", row.get("unitName", "g")),
                }
    return mapping


def load_valid_fdc_ids(food_path: Path) -> tuple[dict[str, str], set[str]]:
    """
    Lee food.csv por streaming, filtra data_type != branded_food.
    Retorna (dict fdc_id -> description, set de fdc_ids válidos).
    """
    foods = {}
    valid_ids = set()
    if not food_path.exists():
        print(f"Error: {food_path} no existe.")
        return foods, valid_ids
    with open(food_path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        headers = reader.fieldnames or []
        data_type_col = "data_type" if "data_type" in headers else None
        if not data_type_col:
            for h in headers:
                if "data_type" in h.lower() or "datatype" in h.lower():
                    data_type_col = h
                    break
        for row in reader:
            dt = str(row.get(data_type_col or "data_type", "")).strip()
            if dt and "branded" in dt.lower():
                continue
            fdc_id = row.get("fdc_id", row.get("id", ""))
            desc = row.get("description", row.get("food_description", ""))
            if fdc_id and desc:
                foods[fdc_id] = desc
                valid_ids.add(fdc_id)
    return foods, valid_ids


def load_food_nutrients(
    food_nutrient_path: Path,
    valid_fdc_ids: set[str],
    nutrient_ids: set[int],
) -> dict[str, dict[str, float]]:
    """
    Lee food_nutrient.csv por streaming.
    Retorna {fdc_id: {nutrient_name: amount}} solo para nutrientes esenciales.
    """
    nutrients_by_food = defaultdict(lambda: {})
    if not food_nutrient_path.exists():
        print(f"Error: {food_nutrient_path} no existe.")
        return dict(nutrients_by_food)
    with open(food_nutrient_path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            fdc_id = row.get("fdc_id", "")
            if fdc_id not in valid_fdc_ids:
                continue
            nid = row.get("nutrient_id", row.get("id", ""))
            try:
                nid_int = int(nid)
            except (ValueError, TypeError):
                continue
            if nid_int not in nutrient_ids:
                continue
            amount = row.get("amount", row.get("median", row.get("value", "0")))
            try:
                amount_val = float(amount)
            except (ValueError, TypeError):
                continue
            nutrients_by_food[fdc_id][nid_int] = amount_val
    return dict(nutrients_by_food)


def build_nutrient_id_set(nutrient_mapping: dict) -> set[int]:
    """Convierte mapping a set de IDs para filtrar food_nutrient."""
    return set(nutrient_mapping.keys()) if nutrient_mapping else set()


def build_output(
    foods: dict[str, str],
    nutrients_by_food: dict[str, dict[int, float]],
    nutrient_mapping: dict[int, dict],
    nutrient_ids: set[int],
) -> list[dict]:
    """Construye array de objetos compatibles con foodItemFromUSDA()."""
    result = []
    for fdc_id, description in foods.items():
        nut_vals = nutrients_by_food.get(fdc_id, {})
        if not nut_vals:
            continue
        food_nutrients = []
        for nid, amount in nut_vals.items():
            info = nutrient_mapping.get(nid, {"name": f"Nutrient_{nid}", "unit": "g"})
            food_nutrients.append({
                "nutrient": {"name": info["name"], "unitName": info["unit"]},
                "amount": amount,
                "value": amount,
            })
        result.append({
            "fdcId": int(fdc_id) if fdc_id.isdigit() else fdc_id,
            "id": fdc_id,
            "description": description,
            "foodNutrients": food_nutrients,
        })
    return result


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        print("\nEjemplo: python scripts/build_usda_offline_db.py C:/ruta/a/carpeta/usda")
        sys.exit(1)
    base = Path(sys.argv[1])
    output_path = Path("data/usdaFoodsOffline.json")
    for i, arg in enumerate(sys.argv[2:], 2):
        if arg == "--output" and i + 1 < len(sys.argv):
            output_path = Path(sys.argv[i + 1])
            break

    food_csv = base / "food.csv"
    food_nutrient_csv = base / "food_nutrient.csv"
    nutrient_csv = base / "nutrient.csv"

    print("Cargando nutrient.csv...")
    nutrient_mapping = load_nutrient_mapping(nutrient_csv)
    nutrient_ids = build_nutrient_id_set(nutrient_mapping)
    if not nutrient_ids:
        nutrient_ids = {1008, 1003, 1005, 1004}
        nutrient_mapping = {
            1008: {"name": "Energy", "unit": "kcal"},
            1003: {"name": "Protein", "unit": "g"},
            1005: {"name": "Carbohydrate, by difference", "unit": "g"},
            1004: {"name": "Total lipid (fat)", "unit": "g"},
        }
    print(f"  Nutrientes objetivo: {nutrient_ids}")

    print("Cargando food.csv (excluyendo branded)...")
    foods, valid_ids = load_valid_fdc_ids(food_csv)
    print(f"  Alimentos genéricos: {len(foods)}")

    print("Cargando food_nutrient.csv...")
    nutrients_by_food = load_food_nutrients(food_nutrient_csv, valid_ids, nutrient_ids)
    print(f"  Alimentos con nutrientes: {len([f for f in foods if f in nutrients_by_food and nutrients_by_food[f]])}")

    print("Construyendo JSON...")
    output_data = build_output(foods, nutrients_by_food, nutrient_mapping, nutrient_ids)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output_data, f, ensure_ascii=False, separators=(",", ":"))

    size_mb = output_path.stat().st_size / (1024 * 1024)
    print(f"Listo: {output_path} ({len(output_data)} alimentos, {size_mb:.2f} MB)")
    if size_mb > MAX_SIZE_MB:
        print(f"  Advertencia: supera el límite de {MAX_SIZE_MB} MB.")


if __name__ == "__main__":
    main()
