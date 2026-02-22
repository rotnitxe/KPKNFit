# Script de construcción de base Open Food Facts offline

Para bases de ~9GB, el script usa **filtros estrictos** y procesamiento en **streaming** (no carga todo en memoria).

## Requisitos

- Python 3.7+
- Base Open Food Facts en **JSONL** (una línea = un producto). Para bases grandes, JSONL es obligatorio.

## Formato de datos

- **JSONL** (recomendado para 9GB): descarga desde https://world.openfoodfacts.org/data  
  Ejemplo de nombres: `products.jsonl`, `en.openfoodfacts.org.products.jsonl`
- **JSON**: solo para archivos pequeños (<500 MB)

## Uso básico

```powershell
# Desde la raíz del proyecto:
python scripts/build_openfoodfacts_offline_db.py "C:\ruta\a\products.jsonl"
```

## Opciones recomendadas para bases grandes

```powershell
python scripts/build_openfoodfacts_offline_db.py "C:\ruta\products.jsonl" --slim --exclude-branded
```

| Opción | Descripción |
|--------|-------------|
| `--slim` | Guarda solo campos esenciales (reduce ~70% el tamaño) |
| `--exclude-branded` | Excluye productos con marcas (solo genéricos) |
| `--max-products 60000` | Máximo de productos (por defecto 60.000) |
| `--max-size-mb 80` | Parar al alcanzar 80 MB |
| `--output data/openFoodFactsOffline.json` | Ruta de salida |

## Filtros aplicados

1. **Macros completos**: debe tener energía (kcal), proteína, carbohidratos y grasas
2. **Nombre válido**: entre 1 y 120 caracteres
3. **Valores razonables**: calorías entre 0 y 1000 kcal/100g
4. **Opcional** (`--exclude-branded`): sin marca o marca genérica

## Resultado

Genera `data/openFoodFactsOffline.json` compatible con la app. Objetivo: **< 80 MB**.

## Ejemplo completo

```powershell
cd C:\Users\valen\Downloads\kpkn-fit-(beta-test)

python scripts/build_openfoodfacts_offline_db.py "C:\Users\valen\Downloads\openfoodfacts" --slim --exclude-branded --max-products 50000
```

Si tus archivos están en `C:\Users\valen\Downloads\openfoodfacts\products.jsonl`, el script los procesará en streaming.
