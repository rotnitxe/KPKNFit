# F4 — Micronutrientes de punta a punta (contrato)

Objetivo: que cada alimento registrado desde una ficha con datos lleve su vector completo de nutrientes
(vitaminas, minerales, azúcares, tipos de grasa, colesterol, colina, omega-3, cafeína, alcohol, agua)
y que la hoja de nutrientes del día lo muestre con cobertura honesta. **Sin cambio de esquema Room**
(D2) y **sin recalcular el historial** (D7). Fuente de los datos: el paquete F1a (`F1A_PAQUETE.md`).

## 1. Decisiones

| # | Decisión |
|---|---|
| F4-1 | «Sin dato» ≠ 0 en todo el camino nuevo: un nutriente ausente es una clave que no está; un 0 declarado es una clave con 0. |
| F4-2 | La instantánea va en el JSON de `LoggedFood` (campos nuevos con valor por defecto, como `isQuickEntry`): `catalogRecordId: String?`, `nutrientsPer100g: Map<String, Double>?` (claves = `NutrientId.key`, unidades de la tabla de abajo) y `catalogGeneration: String?`. Se guarda **por 100 g** de la ficha: la cantidad comida se obtiene con `amount` (g), así una edición de gramos nunca deja el vector desfasado. |
| F4-3 | El enlace alimento → ficha del paquete lo decide el dominio al construir el registro (`scaleFoodByPortion` pone `catalogRecordId`); el vector lo pone la capa de datos al guardar (`NutritionRepository`), leyendo el paquete. Si el paquete no está publicado, se guarda sin vector y no pasa nada más. |
| F4-4 | Registros viejos y entradas rápidas: sin vector → la cobertura usa los campos de siempre (fibra, sodio, potasio, lista `micronutrients`) con la regla vieja; nunca se inventan ceros. |
| F4-5 | `NutrientId` (dominio, sin Android) replica 1:1 la tabla `NUTRIENTS` de `scripts/nutrition_v2/catalog/nutrients.py` (id numérico estable, clave, unidad). |
| F4-6 | Metas: DRI donde existe (RDA/AI y UL); límites de guía solo donde son de ingesta total (sodio ≤ 2.300 mg; grasa saturada < 10 % de la energía; cafeína ≤ 400 mg); sin referencia para colesterol, azúcares totales, trans, EPA+DHA y alcohol (se muestra el total y la cobertura). |
| F4-7 | Embarazo y lactancia (19–50 años) usan sus filas DRI en vez de quedarse sin referencia. |

## 2. Nutrientes (`NutrientId`)

| id | clave | unidad | | id | clave | unidad |
|---|---|---|---|---|---|---|
| 1 | energy | kcal | | 20 | selenium | µg |
| 2 | protein | g | | 21 | iodine | µg |
| 3 | fat | g | | 22 | vitamin_a_rae | µg |
| 4 | carbs | g | | 23 | vitamin_c | mg |
| 5 | fiber | g | | 24 | vitamin_d | µg |
| 6 | sugars | g | | 25 | vitamin_e | mg |
| 7 | added_sugars | g | | 26 | vitamin_k | µg |
| 8 | sat_fat | g | | 27 | thiamin | mg |
| 9 | mono_fat | g | | 28 | riboflavin | mg |
| 10 | poly_fat | g | | 29 | niacin | mg |
| 11 | trans_fat | g | | 30 | vitamin_b6 | mg |
| 12 | cholesterol | mg | | 31 | folate_dfe | µg |
| 13 | sodium | mg | | 32 | vitamin_b12 | µg |
| 14 | potassium | mg | | 33 | choline | mg |
| 15 | calcium | mg | | 34 | epa_dha | g |
| 16 | iron | mg | | 35 | ala | g |
| 17 | magnesium | mg | | 36 | caffeine | mg |
| 18 | phosphorus | mg | | 37 | alcohol | g |
| 19 | zinc | mg | | 38 | water | g |

## 3. Enlace alimento → ficha (`CatalogRecordLink.recordIdFor(food)`)

1. `food.id` empieza por `usda_` o `off_` → ese mismo id (así los importa `FoodImporter`).
2. Alimento estático con `sourceRecordId` numérico y `source` que contiene «USDA» → `usda_<sourceRecordId>`.
3. `sourceRecordId` que es un código de barras (8–14 dígitos) con `source` OFF → `off_<código>`.
4. Ids compuestos («171265+171890»), recetas, entradas rápidas y estimaciones → `null`.

## 4. Paquetes de trabajo

| Paquete | Qué | Carril |
|---|---|---|
| F4-A1 | `NutrientId`, campos nuevos de `LoggedFood`, `LoggedFood.nutrientAmount(id)`, `CatalogRecordLink`, `scaleFoodByPortion` pone `catalogRecordId`, puerto de dominio `NutritionCatalogPort` | JVM |
| F4-A2 | `NutrientKey` ampliado (azúcares, grasas por tipo, colesterol, fósforo, colina, EPA+DHA, ALA, cafeína, alcohol), `NutrientCoverage` lee primero la instantánea (ausente ≠ 0), `DriTargets` (embarazo/lactancia, filas nuevas) | JVM |
| F4-B | Lector del paquete (`OPEN_READONLY`), publicación (asset `.gz` → `filesDir`, sha256 + `quick_check`, puntero en `nutrition_catalog_publication`, borra la generación anterior) y enriquecimiento al guardar en `NutritionRepository` | Android (Robolectric) |
| F4-C | Hoja de nutrientes por grupos (macros detallados, minerales, vitaminas, otros) con cobertura | Android (compilar) + teléfono en F5 |

## 5. Aceptación

- Tests de contrato de cada paquete; examen DEV sin regresión en M3; M8 (núcleo fibra/azúcar/sodio/
  potasio) sube en las menciones que enlazan a una ficha del paquete.
- En el teléfono (F5): registrar 10 alimentos comunes y ver la hoja con vitaminas y minerales; un
  registro viejo sigue mostrando «sin dato».
