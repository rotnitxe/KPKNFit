#!/usr/bin/env python3
"""
Enriquece productos con caloría 0 en openFoodFactsOffline.json
usando valores genéricos por categoría (calorías, macros, micronutrientes).

Uso:
  python scripts/enrich_off_zero_calories.py [--input data/openFoodFactsOffline.json] [--output data/openFoodFactsOffline.json]
"""

import json
import sys
from pathlib import Path

# Permitir import desde scripts/ al ejecutar desde raíz del proyecto
_scripts_dir = Path(__file__).resolve().parent
if str(_scripts_dir) not in sys.path:
    sys.path.insert(0, str(_scripts_dir))
from off_generic_nutrition import infer_category, get_generic_nutriments


def get_kcal(nut: dict) -> float | None:
    """Obtiene calorías por 100g desde nutriments OFF."""
    kcal = nut.get("energy-kcal_100g") or nut.get("energy-kcal")
    if kcal is not None:
        try:
            return float(kcal)
        except (TypeError, ValueError):
            pass
    energy = nut.get("energy_100g")
    if energy is not None:
        try:
            return float(energy) / 4.184
        except (TypeError, ValueError, ZeroDivisionError):
            pass
    return None


def needs_enrichment(nut: dict) -> bool:
    """True si el producto tiene calorías 0 o inválidas."""
    kcal = get_kcal(nut)
    return kcal is None or kcal <= 0


def merge_nutriments(existing: dict, generic: dict) -> dict:
    """Rellena solo campos vacíos o 0 con valores genéricos."""
    out = dict(existing)
    for k, v in generic.items():
        if k not in out or out[k] is None:
            out[k] = v
        else:
            try:
                if float(out[k]) <= 0 and v > 0:
                    out[k] = v
            except (TypeError, ValueError):
                out[k] = v
    return out


def main():
    input_path = Path("data/openFoodFactsOffline.json")
    output_path = Path("data/openFoodFactsOffline.json")
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        if args[i] == "--input" and i + 1 < len(args):
            input_path = Path(args[i + 1])
            i += 2
            continue
        if args[i] == "--output" and i + 1 < len(args):
            output_path = Path(args[i + 1])
            i += 2
            continue
        i += 1

    if not input_path.exists():
        print(f"Error: {input_path} no existe.")
        sys.exit(1)

    print(f"Cargando {input_path}...")
    with open(input_path, "r", encoding="utf-8", errors="replace") as f:
        data = json.load(f)

    products = data if isinstance(data, list) else (data.get("products") or [])
    print(f"  Total productos: {len(products)}")

    enriched = 0
    for p in products:
        nut = p.get("nutriments") or {}
        if not needs_enrichment(nut):
            continue
        name = p.get("product_name") or p.get("product_name_es") or ""
        category = infer_category(name)
        generic = get_generic_nutriments(category)
        p["nutriments"] = merge_nutriments(nut, generic)
        enriched += 1
        if enriched % 500 == 0 and enriched > 0:
            print(f"  Enriquecidos: {enriched}...")

    print(f"  Productos enriquecidos: {enriched}")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(products, f, ensure_ascii=False, separators=(",", ":"))

    size_mb = output_path.stat().st_size / (1024 * 1024)
    print(f"Guardado: {output_path} ({size_mb:.2f} MB)")


if __name__ == "__main__":
    main()
