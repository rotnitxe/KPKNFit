# Examen de nutrición v2 — contrato

Contrato de F0 (ver `README.md`). Define qué se mide, en qué formato y con qué reglas, para que
**cualquier** sistema de registro por descripción (master, el trabajo de Codex, v2) se compare con
el mismo examen y la misma vara. Nada de este documento depende del catálogo de ningún sistema.

## 1. Carpetas

```
C:\kw\nutri-examen\
  spec\matriz.json            matriz de estratificación (sección 3)
  raw\<modelo>\<celda>.jsonl  salida cruda de la generación
  cases\all.jsonl             TODAS las descripciones (DEV + TEST), sin oro — lo que leen los sistemas
  cases\dev.jsonl             solo DEV, sin oro
  gold\dev.jsonl              oro de DEV (visible)
  labels\<modelo>\*.jsonl     etiquetas de cada etiquetador
  sealed\gold-test.jsonl      oro de TEST — SELLADO (ver sección 7)
  predictions\<sistema>-<fecha>.jsonl
  reports\<fecha>-<split>.md / .json
  cache\                      índices reconstruibles (refindex)
```

Las descripciones no son secretas (los sistemas las leen todas); **el oro de TEST sí**.

## 1 bis. Módulos

- **Módulo A** (E0001–E1000): 16 fenómenos, 6 regiones; DEV 400 / TEST 600.
- **Módulo B** (E1001–E1350, pedido del usuario del 27-oct): estilos variados y más idiomas, en seis
  grupos (`group`): B1 inglés coloquial (60), B2 inglés formal o extenso y spanglish (50), B3 faltas en
  los nombres de alimentos (70), B4 sin puntuación (60), B5 formal y bien escrito (50), B6 texto
  extenso con ruido (60); español de Chile y de unos 20 países más, inglés de EE. UU. y Reino Unido.
  DEV 140 / TEST 210. Total del examen: **1.350 casos (DEV 540 / TEST 810)**.
- Los casos llevan además `lang` (`es` | `en` | `mix`) y `group` (null en el módulo A). F17 = texto
  extenso o con ruido; F18 = inglés o mezcla de idiomas. El informe se desglosa también por grupo.

## 2. Casos (`cases/*.jsonl`) — una línea por descripción

```json
{"id": "E0412", "split": "test", "text": "un vaso de leche en polvo y 2 marraquetas con palta",
 "mealType": "BREAKFAST", "region": "CL", "phenomena": ["F07", "F09"], "style": "telefono",
 "origin": "mimo-v2.6"}
```

- `id`: `E` + 4 dígitos, estable. `split`: `dev` | `test`.
- `mealType`: `BREAKFAST` | `LUNCH` | `DINNER` | `SNACK` (los valores de `MealType` de la app).
- `phenomena`: códigos de la sección 3 (solo para informes; los sistemas no los leen).
- `origin`: modelo que la escribió o `usuario`.

## 3. Estratificación

16 fenómenos × región (CL 70 %, resto AR/MX/PE/CO/ES 30 %) × estilo (`telefono`, `dictado`,
`apurado` con faltas, `detallado`). Objetivo: ≥ 50 descripciones por fenómeno; una descripción puede
cubrir varios. Total 1.000 → DEV 400 / TEST 600, estratificado por fenómeno principal.

| Código | Fenómeno |
|---|---|
| F01 | Alimento suelto sin cantidad |
| F02 | Masa o volumen explícito (g, kg, ml, l, cc, oz) |
| F03 | Conteo y tamaño (2 huevos, una manzana grande) |
| F04 | Medida casera (taza, cucharada, vaso, plato, puñado) |
| F05 | Envase y fracción (media lata, medio yogur de 125 g) |
| F06 | Cocción / estado (crudo, cocido, frito, plancha, horno) |
| F07 | Presentación / forma (polvo, preparado, en conserva, light, sin azúcar) |
| F08 | Plato regional (pastel de choclo, completo, cazuela, porotos con riendas) |
| F09 | Composición «X con Y» |
| F10 | Exclusión («sin pan») frente a atributo («sin azúcar», «sin lactosa») |
| F11 | Marca o producto envasado |
| F12 | Corrección o referencia («eran 150, no 200», «una con palta y otra con queso») |
| F13 | Faltas de tipeo o dictado (sin tildes, sin puntuación, números hablados) |
| F14 | Regionalismo (porotos/frijoles, choclo/elote, frutilla/fresa) |
| F15 | Bebida o alcohol (schop, piscola, copa de vino) |
| F16 | Suplemento (scoop de whey, 5 g de creatina) |

## 4. Oro (`gold/dev.jsonl`, `sealed/gold-test.jsonl`)

Ejemplo con valores ilustrativos (los reales salen de las fichas):

```json
{"id": "E0412",
 "mentions": [
  {"mid": "m1", "span": "un vaso de leche en polvo", "canonicalEs": "leche en polvo entera",
   "aliasesEs": ["leche en polvo"], "category": "LACTEOS", "state": "NA", "form": "RECONSTITUTED",
   "reference": {"source": "USDA_FNDDS", "code": "11211100", "name": "Milk, dry, reconstituted, whole"},
   "per100g": {"kcal": 64.0, "protein": 3.3, "fat": 3.4, "carbs": 4.9},
   "grams": {"target": 200.0, "tolerance": "HOUSEHOLD"},
   "eaten": {"kcal": 128.0, "protein": 6.6, "fat": 6.8, "carbs": 9.8},
   "equivalents": [{"source": "USDA_SR_LEGACY", "code": "171283", "grams": 26.0,
                    "per100g": {"kcal": 496.0, "protein": 26.3, "fat": 26.7, "carbs": 38.4},
                    "note": "polvo seco equivalente a 200 ml preparados"}],
   "ambiguity": [], "granularity": "FOOD", "components": []},
  {"mid": "m2", "span": "2 marraquetas con palta", "canonicalEs": "pan con palta",
   "granularity": "DISH", "allowSplit": true,
   "components": [
     {"canonicalEs": "marraqueta", "grams": {"target": 200.0, "tolerance": "COUNT"}, "eaten": {"kcal": 552.0, "protein": 17.0, "fat": 2.4, "carbs": 114.0}},
     {"canonicalEs": "palta", "grams": {"target": 80.0, "tolerance": "SUBJECTIVE"}, "eaten": {"kcal": 128.0, "protein": 1.6, "fat": 11.7, "carbs": 6.8}}
   ],
   "eaten": {"kcal": 680.0, "protein": 18.6, "fat": 14.1, "carbs": 120.8}, "ambiguity": []}
 ],
 "excluded": [],
 "mealEaten": {"kcal": 808.0}}
```

Reglas:
- **`eaten` es la verdad que se puntúa**: nutrientes consumidos en la mención (gramos × ficha). Así,
  «200 ml de leche reconstituida» y «26 g de leche en polvo» son la misma respuesta correcta.
- `reference` es una ficha pública (FNDDS, SR Legacy, Foundation o CIQUAL) elegida con
  `scripts/nutrition_v2/exam/refindex.py`; `per100g` se copia de ella (no se inventa).
- `grams.tolerance` ∈ `EXPLICIT` | `CONTAINER` | `COUNT` | `HOUSEHOLD` | `SUBJECTIVE` (sección 5).
- `state`: `NA` | `RAW` | `COOKED` | `FRIED` | `GRILLED` | `BAKED` | `STEAMED`.
  `form`: `NA` | `POWDER_DRY` | `RECONSTITUTED` | `CONCENTRATE` | `LIQUID` | `DRIED` |
  `CANNED_DRAINED` | `CANNED_UNDRAINED` | `LIGHT` | `SUGAR_FREE`.
- `ambiguity`: dimensiones donde una pregunta está **justificada** porque las alternativas
  razonables difieren ≥ 50 kcal o ≥ 5 g de proteína o grasa (umbrales del contrato v2):
  `IDENTITY` | `STATE` | `FORM` | `AMOUNT`. Vacía si no hace falta preguntar.
- `granularity`: `FOOD` o `DISH`. Un `DISH` con `allowSplit: true` acepta como correcto el plato en
  una mención o sus `components` en varias (sección 6).
- `excluded`: lo que la persona dijo que NO comió («sin pan»): si un sistema lo registra como comido,
  es una mención extra.
- `optional: true` en condimentos menores (sal, pimienta, un chorrito de limón): no penaliza si falta.

## 5. Tolerancias

| Clase | Gramos | Nutrientes consumidos (kcal y cada macro) |
|---|---|---|
| EXPLICIT (masa o volumen dicho) | ±2 % | ±10 % (piso ±15 kcal, ±2 g) |
| CONTAINER (envase de contenido conocido) | ±10 % | ±15 % (piso ±20 kcal, ±2 g) |
| COUNT (unidades) | ±20 % | ±25 % (piso ±25 kcal, ±3 g) |
| HOUSEHOLD (taza, cucharada, vaso) | ±20 % | ±25 % (piso ±25 kcal, ±3 g) |
| SUBJECTIVE (un plato, un poco, sin cantidad) | ±35 % | ±40 % (piso ±40 kcal, ±4 g) |

Comida completa (M7): ±20 % de kcal.

## 6. Predicciones (`predictions/<sistema>-<fecha>.jsonl`) — lo que entrega cada sistema

```json
{"id": "E0412", "system": "master@c4491bc83", "latencyMs": 14.2, "error": null,
 "mentions": [
  {"text": "leche en polvo", "foodName": "Leche en polvo entera", "foodId": "usda_171283",
   "source": "USDA", "heuristic": false, "excluded": false,
   "grams": 26.0, "eaten": {"kcal": 129.0, "protein": 6.8, "fat": 6.9, "carbs": 9.9},
   "per100g": {"kcal": 496.0, "protein": 26.3, "fat": 26.7, "carbs": 38.4},
   "state": "UNKNOWN", "autoAccepted": false, "questions": ["FORM"],
   "nutrientsPresent": ["fiber", "sugar", "sodium", "potassium"]}]}
```

- Una línea por caso de `cases/all.jsonl`, en cualquier orden. Si el sistema falla o excede 5 s,
  `error` lleva el motivo y `mentions` va vacío (cuenta como fallo, nunca se omite).
- `heuristic`: verdadero si la ficha no es real (estimador, último recurso, marcador sin resolver).
- `autoAccepted`: verdadero si el sistema guardaría sin preguntar (sin pregunta material pendiente).
- `questions`: dimensiones preguntadas (`IDENTITY` | `STATE` | `FORM` | `AMOUNT` | `OIL` | `CUT`).
- Menciones con `excluded: true` no cuentan como comidas.

## 7. Puntuación (`scripts/nutrition_v2/exam/score.py`)

**Emparejamiento** por caso: similitud de tokens normalizados (sin tildes, singular) entre
`text`+`foodName` de la predicción y `span`+`canonicalEs`+`aliasesEs` del oro; asignación codiciosa
por similitud descendente con umbral 0,34. Un `DISH` con `allowSplit` se empareja primero como un todo
y, si no, con el conjunto de predicciones que caen en sus `components` (se suman sus `eaten`).
Predicciones sin pareja = extras; oro sin pareja (no opcional) = omisiones.

| Métrica | Definición |
|---|---|
| **M3 Mención correcta** (principal) | emparejada ∧ kcal y los tres macros consumidos dentro de tolerancia ∧ no heurística |
| M1 Identidad | emparejada ∧ `per100g` de la predicción dentro de ±12 % kcal / ±20 % macros (piso 2 g) de la referencia o de un `equivalents` |
| M2 Gramos | emparejada ∧ gramos en tolerancia (solo si la forma coincide) |
| M4 **Error silencioso** | `autoAccepted` ∧ (sin pareja ∨ kcal fuera de ±50 % ∨ heurística) — tasa sobre menciones autoaceptadas |
| M5 Cobertura | emparejada ∧ no heurística |
| M6 Preguntas | precisión y recobrado de `questions` frente a `ambiguity`; preguntas por comida |
| M7 Comida | kcal de la comida dentro de ±20 % |
| M8 Micronutrientes | % de menciones con el núcleo completo (`F4`); informativo hasta F4 |
| Extras | menciones predichas sin pareja por comida |
| Latencia | p50 / p95 de `latencyMs` |

**Comparación A/B pareada**: bootstrap de 2.000 remuestreos por caso (semilla fija 20261027), IC 95 %
de la diferencia B − A en cada métrica, global y por fenómeno.

**Regla de aceptación** (cambiar el predeterminado): M3 mejora con IC inferior > 0 ∧ M4 no empeora
(IC superior de B − A ≤ 0,5 pp) ∧ ningún fenómeno con ≥ 30 menciones cae más de 3 pp en M3.

## 8. Sellado de TEST

- `sealed/gold-test.jsonl` vive solo en `C:\kw\nutri-examen\sealed\`; nunca entra al repo ni a un
  encargo de agente. Los agentes que escriben código no reciben su ruta.
- `score.py --split test` imprime solo agregados globales y por fenómeno; nunca casos sueltos.
- TEST se corre solo en las compuertas de fase (línea base, veredicto Codex y fin de cada fase), y
  cada corrida queda anotada en `README.md` (fecha, sistema, commit, resultado).
- Un caso de TEST que haya que mirar por un error del oro se mueve a DEV y se repone con uno nuevo.

## 9. Generación y etiquetado

- **Quién:** subagentes de Claude Code Sonnet (decisión D8). Los que generan y etiquetan no reciben
  rutas de código ni del catálogo de la app; solo este contrato, la matriz y `refindex.py`.
- **Generadores:** varios agentes, cada uno con fenómenos y personas distintas (estudiante en Santiago,
  madre en Concepción, oficinista en Ciudad de México, deportista en Madrid…), región y estilo; lotes
  de 25 por celda.
- **Curación:** normalizar, quitar duplicados (Jaccard de trigramas ≥ 0,8), descartar textos que no
  sean comida o que superen 8 alimentos, asignar `id`, partir DEV/TEST estratificado.
- **Etiquetado:** en dos pasadas por lote de 25: (A) menciones con `span`, `canonicalEs`, consulta en
  inglés, estado, forma, cantidad declarada y gramos razonados; (B) elige la ficha entre los candidatos
  de `refindex.py` y ajusta gramos con las porciones de esa ficha. `per100g` y `eaten` los calcula el
  script, no el agente. DEV: un etiquetador. TEST: dos etiquetadores ciegos con encuadres distintos
  (uno parte de la ficha, otro de la porción).
- **Acuerdo:** dos etiquetas concuerdan en una mención si sus `eaten.kcal` difieren ≤ 15 % y sus
  macros caen en la tolerancia de la clase. Acuerdo → oro. Desacuerdo → adjudicación por un tercer
  agente, que elige una, corrige o descarta el caso.
- **Revisión del usuario (opcional):** muestra de 40 casos de DEV en una página para marcar ✓/✗.
