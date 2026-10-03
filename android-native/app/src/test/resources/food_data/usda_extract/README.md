# Extracto de USDA Foundation para las pruebas

Filas REALES de `app/src/main/assets/food_data`, copiadas tal cual (sin tocar valores), que `FoodImporterParseTest` y el corpus de
búsqueda (`SearchGoldenCorpusTest`, vía `UsdaSearchFixture`) pasan por `FoodImporter.parseUsda`, la misma función que usa la importación.
Son los CSV con el formato original (`food.csv`, `food_nutrient.csv`, `food_portion.csv`, `measure_unit.csv`, `food_category.csv`,
`usda_es_aliases.csv`) y solo las filas de los alimentos de abajo.

Para ampliarlo: copiar de los CSV de `assets/food_data` la línea de `food.csv` del `fdc_id`, sus filas de `food_nutrient.csv` (solo los
ids de nutriente que mapea el importador: 1003, 1004, 1005, 1008, 1018, 1050, 1051, 1057, 1063, 1079, 1085, 1092, 1093, 2000, 2047 y 2048),
sus filas de `food_portion.csv` si importan y su fila de `usda_es_aliases.csv`; después actualizar las listas de ids de
`FoodImporterParseTest` y `SearchGoldenCorpusTest`.

## Alimentos que entran al catálogo (en el orden de `food.csv`)

| fdc_id | Alimento | Qué comprueba |
|---|---|---|
| 746782 | Milk, whole, 3.25% milkfat | energía 2048 > 2047 > 1008 y azúcar solo en el 1063; porción de una taza |
| 748967 | Eggs, Grade A, Large, egg whole | porción "egg, whole without shell" |
| 331960 | Chicken breast, cooked, braised | pechuga cocida |
| 330458 | Oil, coconut | energía publicada (833 kcal, sin bandera); porción "1 tablespoon, liquid oil" |
| 321358 | Hummus, commercial | "2 tablespoon = 33,9 g" es 16,95 g por cucharada |
| 2346409 | Strawberries, raw | sin fila de alias ni porción: conserva el nombre en inglés |
| 2727569 | Chicken, breast, meat and skin, raw | energía publicada y carbohidrato por diferencia de -0,43 g: se recorta a 0 (`CARB_CLAMPED`) |
| 748608, 748278, 1750351, 1750349 | aceites de oliva extra virgen, canola, oliva extra ligero y girasol | solo "Total fat (NLEA)" (1085): energía Atwater 843,3, 850,5, 836,4 y 838,7 kcal (`ENERGY_ATWATER`) |
| 789828, 790508 | mantequilla sin sal y con sal | grasa 1004 (la salada trae también un 1085 de 65 g, que no gana) y agua: Atwater 733,5 y 739,8 kcal |

## Alimentos Foundation que se quedan fuera

| fdc_id | Alimento | Por qué |
|---|---|---|
| 321505 | Salt, table, iodized | sin macros: 0 kcal |
| 335912 | Beans, Dry, Black (0% moisture) | proteína, grasa y fibra, sin carbohidrato: 4P + 9F daría 111 kcal y el poroto seco tiene ~340 |
| 2747675 | Watermelon, seedless, flesh only, raw | agua, proteína y azúcares, sin carbohidrato: daría 3,5 kcal y la sandía tiene ~30 |

## No son Foundation (el filtro de `food.csv` los salta aunque `food_nutrient.csv` tenga nutrientes mapeados)

319877 (`sub_sample_food`), 335241 (`agricultural_acquisition`), 319874 (`sample_food`) y 319875 (`market_acquisition`).
