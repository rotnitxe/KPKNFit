# F1a — Paquete de catálogo v0 (contrato)

Paquete SQLite de **solo lectura**, construido fuera de la app por scripts reproducibles, que la app
copia desde assets y consulta sin Room. Primera entrega: fichas con **vector completo de nutrientes**,
porciones y productos; el vocabulario en español grande llega en F1b. Ver `README.md` (D1, D2, D7).

## 1. Fuentes y alcance

| Fuente | Entra | Identificador estable |
|---|---|---|
| USDA FNDDS 2021–2023 | 5.431 calculables | `usda_<fdcId>` |
| USDA SR Legacy | 7.793 | `usda_<fdcId>` |
| USDA Foundation | 311 calculables | `usda_<fdcId>` (los 436 de master tienen el mismo fdcId) |
| CIQUAL 2025 | 3.006 calculables | `ciqual_<alim_code>` |
| OFF Chile (asset actual `food_data/off_chile.csv`) | los que pasan los filtros de hoy | `off_<código de barras>` |
| Curado KPKN (catálogo estático) | no entra en v0: sigue en Kotlin y enlaza por `sourceRecordId` | — |

Lectura de fuentes: `scripts/nutrition_v2/sources/` (USDA/CIQUAL) ampliados en un módulo propio para el
vector completo; OFF Chile con los mismos índices de columna que `FoodImporter.kt` y los demás
nutrientes por columna verificada (ver §3). USDA Branded queda fuera (D3).

## 2. Esquema

```sql
CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL);
-- formatVersion=1, generation=<sha256 corto del contenido>, builtAt, sources (JSON con hashes)

CREATE TABLE record (
  id TEXT PRIMARY KEY,            -- usda_171283, ciqual_19024, off_7802800..., gen016
  source TEXT NOT NULL,           -- USDA_FNDDS | USDA_SR_LEGACY | USDA_FOUNDATION | CIQUAL | OFF_CL | KPKN
  source_record_id TEXT NOT NULL,
  name TEXT NOT NULL,             -- nombre para mostrar (es si existe, si no el original)
  name_es TEXT, name_en TEXT, name_fr TEXT,
  brand TEXT,
  category TEXT,
  preparation TEXT NOT NULL,      -- RAW | COOKED | PREPARED | AS_SOLD | UNKNOWN
  form TEXT NOT NULL,             -- NA | POWDER | LIQUID | ... (EXAMEN.md §4)
  basis_amount REAL NOT NULL,     -- 100
  basis_unit TEXT NOT NULL,       -- g | ml
  density_g_per_ml REAL           -- si la fuente permite saberla; si no NULL
) WITHOUT ROWID;

CREATE TABLE nutrient_value (
  record_id TEXT NOT NULL, nutrient_id INTEGER NOT NULL,
  amount REAL NOT NULL,           -- por basis_amount basis_unit, en la unidad de NUTRIENTS
  quality INTEGER NOT NULL,       -- 0 medido/declarado, 1 calculado por la fuente, 2 derivado por nosotros, 3 traza/bajo límite
  PRIMARY KEY (record_id, nutrient_id)
) WITHOUT ROWID;
-- Ausente = no hay fila. Cero declarado = fila con amount 0. Nunca se inventa un 0.

CREATE TABLE portion (
  record_id TEXT NOT NULL, seq INTEGER NOT NULL,
  label TEXT NOT NULL,            -- tal como viene de la fuente
  label_es TEXT,                  -- F1b
  grams REAL NOT NULL, ml REAL,
  PRIMARY KEY (record_id, seq)
) WITHOUT ROWID;

CREATE TABLE product (gtin TEXT PRIMARY KEY, record_id TEXT NOT NULL) WITHOUT ROWID;

CREATE TABLE alias (record_id TEXT NOT NULL, alias TEXT NOT NULL, alias_norm TEXT NOT NULL,
                    lang TEXT NOT NULL, kind TEXT NOT NULL);
-- kind: NAME | SOURCE_ALIAS | KPKN | CURATED (F1b agrega LLM_REVIEWED)
-- alias_norm: minúsculas, sin tildes, solo [a-z0-9 ]; la app normaliza la consulta igual.
CREATE VIRTUAL TABLE alias_fts USING fts4(alias_norm, record_id);
-- tokenizador simple a propósito: remove_diacritics=2 no existe en el SQLite de Android 7 (minSdk 24).

```

No hace falta tabla de equivalencias en v0: `global_foods` ya usa `usda_<fdcId>` y `off_<código>`
(`FoodImporter.kt:489,902`) y unas 70 filas del catálogo estático declaran su `sourceRecordId` de
USDA SR Legacy (p. ej. `gen016` Leche Entera → 171265). La app enlaza por `source` +
`sourceRecordId` en tiempo de ejecución.

## 3. Nutrientes (`NUTRIENTS`, ids estables)

| id | clave | unidad | USDA (nutrient.id, prioridad) | CIQUAL (const_code) | OFF (columna `_100g`) |
|---|---|---|---|---|---|
| 1 | energy | kcal | 1008, si falta 2047, luego 2048 (calidad 1) | 328 | energy-kcal |
| 2 | protein | g | 1003 | 25000 | proteins |
| 3 | fat | g | 1004, luego 1085 | 40000 | fat |
| 4 | carbs | g | 1005, luego 1050 | 31000 | carbohydrates |
| 5 | fiber | g | 1079 | 34100 | fiber |
| 6 | sugars | g | 2000, luego 1063 | 32000 | sugars |
| 7 | added_sugars | g | 1235 | — | added-sugars |
| 8 | sat_fat | g | 1258 | 40302 | saturated-fat |
| 9 | mono_fat | g | 1292 | 40303 | monounsaturated-fat |
| 10 | poly_fat | g | 1293 | 40304 | polyunsaturated-fat |
| 11 | trans_fat | g | 1257 | — | trans-fat |
| 12 | cholesterol | mg | 1253 | 75100 | cholesterol (g → mg) |
| 13 | sodium | mg | 1093 | 10110 | sodium (g → mg) |
| 14 | potassium | mg | 1092 | 10190 | potassium (g → mg) |
| 15 | calcium | mg | 1087 | 10200 | calcium (g → mg) |
| 16 | iron | mg | 1089 | 10260 | iron (g → mg) |
| 17 | magnesium | mg | 1090 | 10120 | magnesium (g → mg) |
| 18 | phosphorus | mg | 1091 | 10150 | phosphorus (g → mg) |
| 19 | zinc | mg | 1095 | 10300 | zinc (g → mg) |
| 20 | selenium | µg | 1103 | 10340 | selenium (g → µg) |
| 21 | iodine | µg | 1100 | 10530 | iodine (g → µg) |
| 22 | vitamin_a_rae | µg | 1106 | retinol 51200 + β-caroteno 51330 / 12 (calidad 2) | vitamin-a (g → µg) |
| 23 | vitamin_c | mg | 1162 | 55100 | vitamin-c (g → mg) |
| 24 | vitamin_d | µg | 1114 | 52100 | vitamin-d (g → µg) |
| 25 | vitamin_e | mg | 1109 | 53100, luego 71010 | vitamin-e (g → mg) |
| 26 | vitamin_k | µg | 1185 | 54101 (+ 54104 si existe) | vitamin-k (g → µg) |
| 27 | thiamin | mg | 1165 | 56100 | vitamin-b1 (g → mg) |
| 28 | riboflavin | mg | 1166 | 56200 | vitamin-b2 (g → mg) |
| 29 | niacin | mg | 1167 | 56310 | vitamin-pp (g → mg) |
| 30 | vitamin_b6 | mg | 1175 | 56500 | vitamin-b6 (g → mg) |
| 31 | folate_dfe | µg | 1190, luego 1177 (calidad 1) | 56702, luego 56700 | folates (g → µg) |
| 32 | vitamin_b12 | µg | 1178 | 56600 | vitamin-b12 (g → µg) |
| 33 | choline | mg | 1180 | — | — |
| 34 | epa_dha | g | 1278 + 1272 (calidad 2 si suma) | 42053 + 42263 | — |
| 35 | ala | g | 1404, luego 1270 | 41833 | alpha-linolenic-acid |
| 36 | caffeine | mg | 1057 | — | caffeine (g → mg) |
| 37 | alcohol | g | 1018 | 60000 | alcohol |
| 38 | water | g | 1051 | 400 | — |

Reglas: el mapeo USDA se hace por id **y** se comprueba el nombre/unidad que trae la fuente (si no
coincide, error de construcción). Valores CIQUAL «traza» o «< límite» → calidad 3 con el valor
declarado (o 0 si solo dice «traza»). OFF: solo columnas cuyo significado se verifique contra los
índices conocidos de `FoodImporter.kt` (89 kcal, 92 grasa, 129 carbohidratos, 130 azúcares, 146 fibra,
150 proteína, 156 sodio en g) con chequeos de coherencia (saturadas ≤ grasa, sal ≈ 2,5 × sodio); una
columna no verificada no entra.

## 4. App (Android)

- Asset `food_data/nutrition_catalog_v0.sqlite` (+ `.sha256`). Al arrancar, en segundo plano: si la
  generación activa (`nutrition_catalog_publication`, ya en Room 36) no es la del asset, copiar a
  `filesDir/nutrition_catalog/<generation>.sqlite`, verificar sha256 e integridad, publicar el
  puntero y borrar la generación anterior. Lectores con `OPEN_READONLY`.
- Mientras no esté publicado, la app funciona igual que hoy.
- Puerto de dominio (sin Android): `NutritionCatalogPort` con `record(id)`, `nutrients(id)`,
  `portions(id)`, `byGtin(gtin)`, `search(query, limit)`, y `recordFor(source, sourceRecordId)` para
  enlazar lo que la app ya resuelve (USDA Foundation/SR de `global_foods` y del catálogo estático, OFF).

## 5. Presupuesto y validación

- Tamaño esperado: ~25 MB sin comprimir; se mide y se anota en el README.
- Validadores del constructor: esquema, ids únicos, unidades, rangos físicos (macros ≤ 100 g/100 g,
  energía ≤ 900 kcal/100 g, sodio ≤ 40.000 mg/100 g), coherencia Atwater (aviso si |kcal − (4P + 4C +
  9G + 7A)| > 20 % y > 30 kcal), cobertura por nutriente y fuente (informe JSON).
- Determinismo: dos construcciones seguidas producen el mismo sha256.
