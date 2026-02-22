#!/usr/bin/env python3
"""
Script para filtrar la base Open Food Facts (~9GB) y generar un JSON ligero
para uso offline (máx. 80MB).

Filtros estrictos: macros completos, opcionalmente sin marcas, salida reducida.
Procesa JSONL en streaming (no carga todo en memoria).

Uso:
  python scripts/build_openfoodfacts_offline_db.py <ruta_archivo.jsonl>
  python scripts/build_openfoodfacts_offline_db.py <ruta_carpeta> [opciones]

Opciones:
  --output data/openFoodFactsOffline.json   Salida
  --max-size-mb 80                         Límite de tamaño (parar al alcanzar)
  --max-products 60000                     Límite de productos
  --exclude-branded                        Excluir productos con marcas
  --slim                                  Solo guardar campos esenciales (recomendado)
"""

import json
import sys
from pathlib import Path

MAX_SIZE_MB = 80
MAX_PRODUCTS = 60_000

# Campos esenciales para foodItemFromOFF (reduce ~70% tamaño)
SLIM_KEYS = frozenset({
    "code", "product_name", "product_name_es", "brands",
    "nutriments", "image_small_url", "image_front_small_url",
})


def slim_product(p: dict) -> dict:
    """Solo campos necesarios para la app."""
    out = {}
    for k in SLIM_KEYS:
        if k in p and p[k] is not None:
            out[k] = p[k]
    # Nutriments: solo los que usa foodItemFromOFF
    nut = p.get("nutriments") or {}
    keep = {
        "energy-kcal_100g", "energy-kcal", "energy_100g",
        "proteins_100g", "proteins", "carbohydrates_100g", "carbohydrates",
        "fat_100g", "fat", "saturated-fat_100g", "monounsaturated-fat_100g",
        "polyunsaturated-fat_100g", "trans-fat_100g",
    }
    out["nutriments"] = {k: v for k, v in nut.items() if k in keep}
    return out


def has_valid_nutriments_strict(p: dict) -> bool:
    """
    Reglas estrictas: debe tener TODOS los macros principales.
    - product_name no vacío, longitud razonable
    - energy (kcal) Y protein Y carbs Y fat
    """
    name = (p.get("product_name") or "").strip()
    if not name or len(name) > 120:
        return False
    nut = p.get("nutriments") or {}
    has_energy = (
        nut.get("energy-kcal_100g") is not None
        or nut.get("energy-kcal") is not None
        or (nut.get("energy_100g") is not None and nut.get("energy_100g") > 0)
    )
    has_protein = nut.get("proteins_100g") is not None or nut.get("proteins") is not None
    has_carbs = nut.get("carbohydrates_100g") is not None or nut.get("carbohydrates") is not None
    has_fat = nut.get("fat_100g") is not None or nut.get("fat") is not None
    if not (has_energy and has_protein and has_carbs and has_fat):
        return False
    # Valores numéricos razonables
    try:
        kcal = nut.get("energy-kcal_100g") or nut.get("energy-kcal")
        if kcal is None and nut.get("energy_100g") is not None:
            kcal = nut["energy_100g"] / 4.184
        if kcal is not None and (kcal < 0 or kcal > 1000):
            return False
    except (TypeError, ZeroDivisionError):
        return False
    return True


def is_generic(p: dict) -> bool:
    """Sin marca o marca genérica."""
    brands = (p.get("brands") or "").strip().lower()
    if not brands:
        return True
    generic = {"sin marca", "generic", "genérico", "no brand", "unbranded", "marca blanca"}
    return brands in generic or any(g in brands for g in generic)


def load_products_json(path: Path) -> list:
    """Carga JSON completo (solo para archivos pequeños)."""
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        data = json.load(f)
    if isinstance(data, list):
        return data
    if isinstance(data, dict) and "products" in data:
        return data["products"] or []
    if isinstance(data, dict) and "results" in data:
        return data["results"] or []
    return []


def stream_jsonl_filtered(
    paths: list[Path],
    *,
    exclude_branded: bool,
    slim: bool,
    max_products: int,
    max_size_bytes: int,
) -> tuple[list[dict], int, int]:
    """
    Procesa JSONL en streaming. Retorna (productos, total_leídos, total_filtrados).
    """
    products = []
    total_read = 0
    total_filtered = 0
    size_so_far = 0

    for path in paths:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                total_read += 1
                if total_read % 500_000 == 0:
                    print(f"    Procesados {total_read:,} líneas, {len(products):,} válidos...")
                try:
                    p = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if exclude_branded and not is_generic(p):
                    continue
                if not has_valid_nutriments_strict(p):
                    continue
                total_filtered += 1
                out = slim_product(p) if slim else p
                products.append(out)
                size_so_far += len(json.dumps(out, ensure_ascii=False, separators=(",", ":")))
                if len(products) >= max_products or size_so_far >= max_size_bytes:
                    return products, total_read, total_filtered
    return products, total_read, total_filtered


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        print("\nEjemplo: python scripts/build_openfoodfacts_offline_db.py C:/ruta/products.jsonl --slim --exclude-branded")
        sys.exit(1)

    base = Path(args[0])
    output_path = Path("data/openFoodFactsOffline.json")
    max_size_mb = MAX_SIZE_MB
    max_products = MAX_PRODUCTS
    exclude_branded = False
    slim = False

    i = 1
    while i < len(args):
        if args[i] == "--output" and i + 1 < len(args):
            output_path = Path(args[i + 1])
            i += 2
            continue
        if args[i] == "--max-size-mb" and i + 1 < len(args):
            max_size_mb = int(args[i + 1])
            i += 2
            continue
        if args[i] == "--max-products" and i + 1 < len(args):
            max_products = int(args[i + 1])
            i += 2
            continue
        if args[i] == "--exclude-branded":
            exclude_branded = True
            i += 1
            continue
        if args[i] == "--slim":
            slim = True
            i += 1
            continue
        i += 1

    if not base.exists():
        print(f"Error: {base} no existe.")
        sys.exit(1)

    max_size_bytes = max_size_mb * 1024 * 1024

    # Recoger archivos
    if base.is_file():
        paths = [base] if base.suffix.lower() in (".json", ".jsonl") else []
    else:
        paths = sorted(base.glob("*.jsonl")) + sorted(base.glob("*.json"))

    if not paths:
        print(f"Error: no se encontraron archivos .json o .jsonl en {base}")
        sys.exit(1)

    # Para bases grandes (~9GB): usar JSONL en streaming (no carga todo en memoria)
    jsonl_paths = [p for p in paths if p.suffix.lower() == ".jsonl"]
    total_size_mb = sum(p.stat().st_size for p in paths) / (1024 * 1024)

    if jsonl_paths:
        # Para bases grandes, slim por defecto (reduce ~70% tamaño)
        if total_size_mb > 100 and not slim:
            slim = True
            print("  (Modo slim activado por defecto para bases grandes)")
        print(f"Procesando {len(jsonl_paths)} archivo(s) JSONL en streaming...")
        print(f"  Filtros: macros completos, max {max_products} productos, max {max_size_mb} MB")
        if exclude_branded:
            print("  Excluyendo productos con marcas")
        if slim:
            print("  Modo slim: solo campos esenciales")
        products, total_read, total_ok = stream_jsonl_filtered(
            jsonl_paths,
            exclude_branded=exclude_branded,
            slim=slim,
            max_products=max_products,
            max_size_bytes=max_size_bytes,
        )
        print(f"  Líneas leídas: {total_read:,} | Válidos: {total_ok:,} | Guardados: {len(products):,}")
    else:
        # Solo JSON (sin JSONL): cargar en memoria. Para bases >500MB puede fallar.
        if total_size_mb > 500:
            print("Error: archivos JSON muy grandes. Para bases de varios GB usa formato JSONL.")
            print("  Descarga: https://world.openfoodfacts.org/data")
            sys.exit(1)
        print("Cargando JSON...")
        all_products = []
        for p in paths:
            print(f"  {p.name}...")
            if p.suffix.lower() == ".jsonl":
                with open(p, "r", encoding="utf-8", errors="replace") as f:
                    for line in f:
                        line = line.strip()
                        if line:
                            try:
                                all_products.append(json.loads(line))
                            except json.JSONDecodeError:
                                pass
            else:
                all_products.extend(load_products_json(p))
        print(f"  Total cargados: {len(all_products):,}")
        print("Filtrando (reglas estrictas: energía, proteína, carbohidratos y grasas)...")
        filtered = [
            p for p in all_products
            if (not exclude_branded or is_generic(p)) and has_valid_nutriments_strict(p)
        ]
        print(f"  Válidos: {len(filtered):,}")
        products = []
        size_so_far = 0
        for p in filtered:
            if len(products) >= max_products or size_so_far >= max_size_bytes:
                break
            out = slim_product(p) if slim else p
            products.append(out)
            size_so_far += len(json.dumps(out, ensure_ascii=False, separators=(",", ":")))

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(products, f, ensure_ascii=False, separators=(",", ":"))

    size_mb = output_path.stat().st_size / (1024 * 1024)
    print(f"\nListo: {output_path}")
    print(f"  Productos: {len(products):,} | Tamaño: {size_mb:.2f} MB")
    if size_mb > MAX_SIZE_MB:
        print(f"  Advertencia: supera el límite recomendado de {MAX_SIZE_MB} MB.")


if __name__ == "__main__":
    main()
