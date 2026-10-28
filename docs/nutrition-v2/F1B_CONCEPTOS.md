# F1b — Conceptos en español («IA en la fábrica»)

Objetivo: el vocabulario que limita hoy la comprensión. Un **concepto** es lo que una persona nombra al
comer («arroz», «marraqueta», «leche en polvo», «pastel de choclo»), con sus alias en español
(es-CL primero y sinónimos de AR/MX/PE/CO/ES), en inglés y con faltas típicas; su estado y forma por
defecto; sus porciones caseras; y las **fichas** del paquete F1a que dan sus nutrientes por variante
(«arroz blanco cocido» y «crudo» son dos fichas del mismo concepto). El parser v2 (F2) compila su
léxico desde aquí y el resolutor v2 elige la ficha por concepto + estado + forma.

Los modelos se usan solo **al construir** (D1). Los agentes que generan conceptos no ven el examen
ni el código de la app; solo esta guía y el buscador de fichas `scripts/nutrition_v2/exam/refindex.py`.

## 1. Esquema (`concepts.jsonl`, un concepto por línea)

```json
{"id": "arroz_blanco", "name": "Arroz blanco", "kind": "FOOD", "category": "PASTAS_ARROZ",
 "aliasesEs": ["arroz", "arroz blanco", "arroz graneado", "arroz cocido"],
 "aliasesEn": ["white rice", "rice", "steamed rice"],
 "typos": ["aroz", "arros", "arros blanco"],
 "defaultState": "COOKED", "defaultForm": "NA",
 "variants": [
   {"state": "COOKED", "form": "NA", "query": "rice white cooked no added fat", "ref": "usda_…"},
   {"state": "RAW", "form": "NA", "query": "rice white long grain raw", "ref": "usda_…"}],
 "countable": false, "unit": null,
 "portions": [{"label": "taza", "grams": 160, "state": "COOKED"},
              {"label": "cucharada", "grams": 12, "state": "COOKED"},
              {"label": "plato", "grams": 250, "state": "COOKED"}],
 "defaultGrams": 180, "densityGPerMl": null, "reconstitution": null, "components": null,
 "notes": ""}
```

| Campo | Regla |
|---|---|
| `id` | `snake_case` ASCII, único. |
| `name` | Nombre es-CL para mostrar. |
| `kind` | `FOOD` \| `DISH` \| `DRINK` \| `SUPPLEMENT` \| `CONDIMENT`. |
| `category` | Las 20 del examen: ACEITES_GRASAS, ALCOHOL, AZUCARES_DULCES, BEBIDAS, CARNES, CEREALES_PANES, FRUTAS, FRUTOS_SECOS, HUEVOS, LACTEOS, LEGUMBRES, OTROS, PASTAS_ARROZ, PESCADOS_MARISCOS, PLATOS, SALSAS_CONDIMENTOS, SNACKS, SUPLEMENTOS, TUBERCULOS, VERDURAS. |
| `aliasesEs` | Cómo se escribe en un registro real: nombre corto, plural si es habitual, regionalismos (porotos/frijoles/caraotas, palta/aguacate, choclo/elote/mazorca, frutilla/fresa, zapallo/calabaza/ahuyama…). Sin tildes obligatorias (se normaliza). |
| `aliasesEn` | Nombres en inglés que usaría alguien que escribe en inglés. |
| `typos` | Faltas plausibles al teclear o dictar: s/z/c, b/v, ll/y, h muda, ñ→n, letras dobladas o comidas. Nunca una palabra que sea otro alimento. |
| `defaultState` / `defaultForm` | Lo que la gente quiere decir con el nombre solo en una comida (el arroz se come cocido; la avena del desayuno se pesa cruda). Valores de `EXAMEN.md` §4. |
| `variants` | Una por estado/forma relevante; `query` en inglés para el buscador; `ref` lo pone la etapa 3. |
| `countable` / `unit` | Si se cuenta («2 huevos»): `{"names": ["unidad"], "grams": {"S": 44, "M": 50, "L": 56}}` en gramos comestibles. |
| `portions` | Medidas caseras en gramos del estado indicado (taza, cucharada, cucharadita, vaso, plato, rebanada, lámina, puñado, scoop, lata, porción…). |
| `defaultGrams` | Porción típica si no se dice cantidad. |
| `densityGPerMl` | Solo líquidos y polvos que se miden por volumen. |
| `reconstitution` | Polvos y concentrados: `{"gramsPer100ml": 13, "medium": "AGUA"}`; una medida de bebida (vaso, taza, ml) es preparado; masa, cucharada o scoop es polvo seco. |
| `components` | Platos sin ficha propia: `[{"query": "corn sweet cooked", "grams": 150, "note": "pino de choclo"}]` por porción típica; la etapa 3 pone `ref` y la construcción calcula el vector por 100 g (fuente KPKN, calidad «derivado»). |

## 2. Etapas

| Etapa | Quién | Salida (`C:\kw\nutri-build\concepts\`) |
|---|---|---|
| 1. Inventario | agentes Sonnet por lote de categoría (§3) | `stage1\<lote>.jsonl` sin `ref` |
| 2. Candidatos | script: `refindex.py batch` con cada `query` | `stage2\<lote>.jsonl` (6 candidatos con kcal, proteína, grasa y carbohidratos por 100 g) |
| 3. Enlace | agentes Sonnet eligen la ficha por variante o marcan «ninguna sirve» | `stage3\<lote>.jsonl` |
| 4. Validación | script | `report.json`: esquema, ids, colisiones de alias, fichas existentes, rangos por categoría, porciones contra FNDDS (aviso si difieren > 25 %), Atwater de los platos |
| 5. Revisión cruzada | un agente distinto revisa una muestra del 10 % por lote | `review\<lote>.jsonl`; un lote con > 5 % de errores graves se rehace |

Las colisiones de alias entre conceptos no se esconden: van a `ambiguous.jsonl` y el parser las trata
como identidad ambigua (pregunta si la diferencia es material).

## 3. Lotes y metas

| Lote | Categorías | Meta |
|---|---|---|
| C01 | FRUTAS | 110 |
| C02 | VERDURAS | 140 |
| C03 | TUBERCULOS, LEGUMBRES | 70 |
| C04 | CEREALES_PANES | 120 |
| C05 | PASTAS_ARROZ, HUEVOS, ACEITES_GRASAS | 115 |
| C06 | LACTEOS | 110 |
| C07 | CARNES: vacuno, cerdo, cordero, vísceras | 90 |
| C08 | CARNES: aves, embutidos y fiambres | 80 |
| C09 | PESCADOS_MARISCOS | 90 |
| C10 | FRUTOS_SECOS, SNACKS | 110 |
| C11 | AZUCARES_DULCES | 120 |
| C12 | BEBIDAS | 110 |
| C13 | ALCOHOL, SUPLEMENTOS, OTROS | 120 |
| C14 | SALSAS_CONDIMENTOS | 70 |
| C15 | PLATOS chilenos | 120 |
| C16 | PLATOS latinoamericanos | 100 |
| C17 | PLATOS internacionales y comida rápida | 80 |

Meta total ≈ 1.950 conceptos y ≥ 15.000 alias (es + en + faltas). La cola larga (lo que no tenga
concepto) se busca por nombre de ficha en el paquete y por marca/código de barras en OFF.

## 4. Construcción

`build_pack.py` v1 agrega al paquete las tablas `concept`, `concept_alias` (con `alias_norm` y FTS),
`concept_variant`, `concept_portion` y las fichas derivadas `kpkn_<concepto>` de los platos por
componentes; `record.name_es` de cada ficha enlazada pasa a ser el nombre del concepto + estado/forma.
Para el carril JVM se exporta lo mismo a `concepts_export.json` (conceptos + vector por 100 g y
porciones de las fichas enlazadas), así el parser y el resolutor v2 se prueban sin Android.
