# Script de construcción de base USDA offline

## Requisitos

- Python 3.7+
- Archivos CSV del [Full Download USDA](https://fdc.nal.usda.gov/download-datasets) descomprimidos

## Uso

```powershell
# Desde la raíz del proyecto:
python scripts/build_usda_offline_db.py "C:\ruta\a\carpeta\usda"

# Con salida personalizada:
python scripts/build_usda_offline_db.py "C:\ruta\a\carpeta\usda" --output data/usdaFoodsOffline.json
```

## Archivos necesarios

En la carpeta USDA deben estar:

- `food.csv`
- `food_nutrient.csv`
- `nutrient.csv`

## Resultado

Genera `data/usdaFoodsOffline.json` con alimentos genéricos (excluye branded), compatible con `foodItemFromUSDA()` en la app.
