# Auditoría: precisión nutricional V2 (2026-08)

Plan: `.opencode/plans/2026-08-16_nutrition_precision_v2.md` (flags: nutrition, room).

## Fase 0 — Reproducción del defecto crítico (2026-08-16)

Tests: `app/src/test/java/com/example/kpkn/domain/nutrition/CookingStateRegressionTest.kt`
(7 tests, pipeline completo parser → TagResolver con catálogo estático, sin Room).

Resultado con código previo a la Fase 1 (`testBaseDebugUnitTest --tests *.CookingStateRegressionTest`):
**4 failed / 3 passed** — la compuerta exige fallo por la razón esperada antes de modificar producción.

| Caso | Resultado | Evidencia |
|---|---|---|
| `200 g pechuga cocida` | FALLO (esperado) | esperaba `gen004`, obtuvo `gen003` → ficha cruda elegida; con el flujo actual escala 200/0,75 × 31 × 0,95 ≈ 78,5 g proteína (91 g en plancha/horno) |
| `200 g pechuga cocinada` | FALLO (esperado) | esperaba `gen004`, obtuvo `gen003` → el parser no reconoce `cocinada` como método |
| `200 g pechuga cruda` | FALLO (esperado) | esperaba 45,0 g, obtuvo 62,0 g → la ficha «cruda» tiene densidad cocida |
| ficha cruda densidad cruda | FALLO (esperado) | `ficha cruda con densidad cocida: 31.0 g/100 g` |
| `200 g plancha` / `200 g horno` | pass | la ruta de variante preparada ya funciona para esos métodos |
| contexto post-entreno | pass | EXPLICIT_MASS ya bloquea el boost hoy; protege la eliminación de `proteinBoost` de la Fase 1 |

Causa raíz (tres defectos encadenados):

1. `FoodParser.extractCookingMethod` extrae «cocida» como método y la elimina del tag antes
   de resolver; la penalización de estado (−0,35) en `SmartFoodResolver.computeScore` nunca
   separa gen003 (cruda) de gen004 (cocida) y el desempate por `foodId` elige la cruda.
2. `CookingStateResolver.methodSearchSuffixes(COCIDO)` solo prueba el sufijo masculino
   «cocido» → `findPreparedVariant` nunca encuentra «Pechuga de Pollo (cocida)».
3. `MacroCalculator.scaleFoodByPortion` divide 200/0,75 sobre macros crudos (31 g/100 g,
   densidad cocida mal rotulada) y encima aplica factor de cocción ×0,95 (×1,10 en
   plancha/horno) → 78,5–90,9 g.

Referencia del asset (food_nutrient.csv): FDC 331960 cocida braised = 166 kcal / 32,1 P /
3,24 G por 100 g → 200 g = 64,2 g proteína. FDC 2646170 cruda = ~106 kcal / 22,5 P / 1,93 G.

## Fase 1 — Corrección de estado y cálculo (2026-08-16)

Cambios de producto (BUILD SUCCESSFUL tras el bloque):

1. `FoodDatabase.kt`: fichas gen003 (cruda → FDC 2646170: 106 kcal/22,5 P/1,9 G,
   yield 0,75) y gen004 (cocida → FDC 331960: 166 kcal/32,1 P/3,2 G) alineadas al asset.
2. `FoodParser.kt`: `cocinado/cocinada/…` se reconocen como COCIDO (antes quedaban en el
   tag y rompían identidad y estado).
3. `FoodIdentity.kt`: RAW/COOKED patterns reconocen inglés (raw/dried; cooked/boiled/
   braised/baked/fried/grilled/roasted/steamed/smoked) — las filas USDA ya no llegan
   UNKNOWN al ranking; STATE_SUFFIX incorpora «cocinada».
4. `CookingStateResolver.kt`: sufijos de fila preparada con género y plural (cocida/
   cocido/cocidas/cocidos/cocinada/…); `findPreparedVariant` prefiere la clave de nombre
   exacto «tag (sufijo)» antes que alias; `findDryOrCookedVariant` con variantes
   femeninas; nuevo `stateForMethod(method) → FoodState`.
5. `SmartFoodResolver.kt`: `stateHint` estructurado (query → attemptResolve →
   scoreAndRank → computeScore) para que la penalización de estado (−0,35) actúe aunque
   el parser haya extraído la palabra del tag.
6. `MacroCalculator.scaleFoodByPortion`: `proteinBoost` eliminado (el contexto jamás
   muta densidad por 100 g); los factores de cocción no se aplican sobre fichas ya
   cocidas/preparadas (fin de la doble concentración); el rendimiento solo convierte
   entre bases distintas.
7. `TagResolution.kt`: pasa `stateHint` al resolver; prefiere variante cruda cuando el
   usuario declara crudo sobre una fila cocida; `scalingForIntent` reducido a porción;
   `requiresCandidateReview` no se marca cuando la variante preparada coincide con el
   método explícito.
8. `ContextDetector`: helper `adjustProtein` eliminado (no reintroducir equivalentes).
9. Drawer/Repository: sin boost de proteína; el tag de búsqueda manual explícita queda
   `AUTO`/`CURATED_LOCAL` (autoridad de selección manual, no fallback silencioso).

Resultado: `CookingStateRegressionTest` 7/7 verde. **200 g pechuga cocida = 64,2 g
proteína (332 kcal), fila gen004, base cocida, sin yield ni factor adicional.** No
existe ruta válida a 78–91 g: cruda→45,0 g (22,5/100 g), plancha→62,0 g (variante
preparada), horno→62,0 g. Contexto post-entreno no altera proteína por 100 g.
Suite nutricional completa: 26 suites / 324 tests / 0 fallos (línea base 24/313 superada).

## Fase 2 — Procedencia del catálogo, Room v23 e importación atómica (2026-08-16)

Nota: la versión v21 fue tomada por trabajo concurrente (body observations/goals) y
v22 por la procedencia del catálogo/calibración; los snapshots diarios se añadieron
como **v22→v23**, sin reescritura destructiva.

Cambios de producto (BUILD SUCCESSFUL tras el bloque):

1. `Entities.kt`: `GlobalFoodEntity` + `sourceRecordId`, `foodState`, `nutritionBasis`,
   `datasetVersion`, `category`, `portionGrams`, `portionUnit`, `qualityFlagsJson`;
   `LearnedResolutionEntity` + `weightBasis`, `portionMinGrams/MaxGrams`, `preparation`,
   `oilProfile`, `confidence`, `lastConfirmedAt`; nueva `NutritionCalibrationProfileEntity`
   (singleton versionado, esquema del JSON migrable).
2. `KpknDatabase.kt`: v23, cadena no destructiva `20→21→22→23` (columnas con
   defaults, observaciones/metas/calibración y snapshots diarios), registrada en
   `addMigrations`. Esquemas exportados: `app/schemas/.../21.json`, `22.json` y
   `23.json`.
3. `FoodImporter.kt`: importación atómica (`db.withTransaction` — un fallo a mitad
   deja intacta la versión anterior; FTS por triggers dentro de la misma transacción;
   meta/version/checksum solo al finalizar); procedencia poblada por fila (USDA:
   fdcId/estado/base/versión/categoría; OFF: barcode); validación física (rechaza
   negativos, no finitos y macros >100 g/100 g); flags de calidad ENERGY_MISMATCH /
   LOW_QUALITY / INCOMPLETE (marcan, no rechazan — diferencias por fibra/polioles/
   alcohol no se castigan); porción doméstica autoritativa desde `food_portion.csv`
   (primera porción declarada; sin esto todo caía al «100 g» genérico);
   `DATA_VERSION` 7→8 para reimportar con procedencia.
4. `Daos.kt`: get/upsert del perfil de calibración. `build.gradle.kts`+`libs.versions.toml`:
   `room-testing` y esquemas como assets de androidTest.

Tests: `FoodCatalogProvenanceTest` (JVM, 10 tests: validación física, flags, JSON
estable, estado desde descripciones USDA en inglés) verde. `NutritionMigrationTest`
(androidTest: siembra v20 con planes/logs/estado activo/custom foods/global foods/
resoluciones aprendidas → valida cadena 20→21→22→23 con preservación de datos y
defaults de procedencia) compila; **ejecución pendiente de emulador**.

Suite nutricional propia (excluida la clase en construcción de la sesión concurrente):
**BUILD SUCCESSFUL, 0 fallos**.

El generador de assets v2 (`scripts/generate_food_catalog_v2.py`) ya está disponible
para producir esos CSV gzip y su manifiesto de forma atómica; la generación/importación
del asset grande y su medición de memoria siguen pendientes de una ejecución explícita.

## Entrega Android — wizard, cuerpo y calibración

- `NutritionEnergyEngine` usa EER 2023 para adultos elegibles y persiste dirección,
  objetivo tipado y snapshot de cálculo en `NutritionPlan`.
- `BodyProgressRepository` materializa observaciones y metas en Room, importa el JSON
  legacy tras verificar IDs y mantiene una fuente única para la pantalla Cuerpo.
- La calibración exige 14 días, 7 pesajes y 10 jornadas completas; cada revisión queda
  limitada a ±150 kcal y se guarda en el perfil singleton v23.
- La telemetría local expone `docs/contracts/nutrition_interpretation_v1.schema.json`;
  no contiene texto crudo ni credenciales.

Verificación realizada el 2026-08-16:

```text
:app:compileBaseDebugKotlin — BUILD SUCCESSFUL
:app:compileHealthDebugKotlin — BUILD SUCCESSFUL
:app:assembleBaseDebug — BUILD SUCCESSFUL
:app:testBaseDebugUnitTest --tests 'domain.nutrition.*' --tests 'domain.body.*' --tests 'data.food.*' --tests 'navigation.*' — BUILD SUCCESSFUL (351 tests, 0 fallos)
:app:compileBaseDebugAndroidTestKotlin — BUILD SUCCESSFUL
```

La ejecución sin filtros de `testBaseDebugUnitTest` se dejó sin concluir después
de aproximadamente 15 minutos sin producir nuevos XML ni progreso observable; no
se registró un fallo de aserciones. La evidencia de tests que se toma como válida
es la suite filtrada y los bloques dirigidos anteriores.

La instalación de `BaseDebug` en `emulator-5554` quedó comprobada tras los últimos
cambios: Cuerpo abre sin plan, permite registrar una primera medición y el wizard
canónico abre a pantalla completa sin bottom bar; el logcat final no mostró una
excepción fatal. Health Connect físico, la ejecución de `NutritionMigrationTest` y
las pruebas de TalkBack/fuente 200% siguen siendo validaciones separadas.

## Fase 7 (parcial) — Contratos de paridad (2026-08-16)

Creados `docs/contracts/nutrition_interpretation_v1.schema.json` (estados, bases,
fórmulas, precedencia de fuentes/candidatos, reglas de bloqueo de Guardado, rangos y
banderas de calidad) y `docs/contracts/nutrition_interpretation_v1_golden.json`
(8 casos: pollo cocida/cocinada/cruda/plancha, invarianza de contexto, conversión
yield solo sin ficha, ranking de estado USDA en inglés, porciones absolutas
idempotentes). Ambos validados como JSON. iOS/backend no se modifican.

## Estado de la entrega (2026-08-16)

Probado (verde):
- Fase 0 completa (reproducción documentada arriba).
- Fase 1 completa (compuerta 64,2 g satisfecha; suite nutricional propia 0 fallos).
- Fase 2 código completo (Room v23 + procedencia + importación atómica + porciones
  autoritativas + validaciones/flags; suite propia 0 fallos; esquema 22.json exportado).
- Contratos JSON v1 (Fase 7 parcial).

Sin prueba aún (requiere ejecución adicional/autorización):
- `NutritionMigrationTest` (androidTest compila; ejecución pendiente de dispositivo).
- QA de emulador de casos alimentarios completos (200 g pechuga cocida, porción de
  arroz, papas fritas, pollo empanizado al horno, air fryer sin aceite, migración de
  perfil real).
- Importación real del catálogo con procedencia en primer arranque (DATA_VERSION 8).
- Generación de assets v2 comprimidos y manifiesto (Fase 2 pendiente).

## Fases 3–6 implementadas en el bloque Android actual

- `FoodInterpretationV2` expone identidad/familia/candidato, fuente y versión,
  base RAW/COOKED/AS_SOLD/PREPARED/SERVING, gramos observados/base/rango,
  preparación/aceite, macros centrales y min–max, confianzas, transformaciones,
  evidencia persistible y preguntas materiales. Las respuestas `Unsure` conservan
  el rango y no actualizan aprendizaje.
- Food Logger muestra las preguntas encima del resumen, opciones absolutas
  `Pequeña · N g`/`Habitual · N g`/`Grande · N g`, gramos manuales, `No estoy
  seguro; usar estimación`, bloqueo de Guardar y chips de fuente/estado. El ancla
  `baseAmountGrams` evita deriva al alternar tamaños.
- `NutritionCalibrationWizardEngine` y la ruta `nutrition/calibration` guardan
  seis pasos reanudables, omisión, reset y hábitos maduros después de tres
  confirmaciones dentro de ±15%; `NutritionCalibrationProfile` es versionado y
  conserva identidad/estado/porción/aceite por separado.
- `NutritionHistory` mantiene huecos, cobertura `N de M`, snapshots históricos y
  bandas de macros inciertos. La tarjeta de energía mantiene el gasto de entrenamiento
  separado de la meta de ingesta.
- El export/import JSON usa `schemaVersion=2`, hace upsert transaccional sin
  `clearAllTables()`, conserva observaciones/schedule, alimentos personalizados,
  aprendizaje confirmado, calibración y metadatos del catálogo. El reset espera a
  que terminen las escrituras Room para evitar que reaparezca la última medición.
- Cuerpo dibuja puntos de mediana diaria y una tendencia EWMA de vida media de 7 días;
  permite editar entradas manuales, borrar con undo y mantiene Health Connect separado:
  permisos de lectura corporal y escritura opcional, con deduplicación por identificador
  externo.
- `scripts/generate_food_catalog_v2.py` genera de forma atómica los CSV gzip V2 y
  el manifiesto con checksums, licencias y filas aceptadas/rechazadas; no modifica
  los datasets grandes manualmente.

Estas capas tienen pruebas JVM dirigidas (351/351 verdes) y compilación Base/HealthDebug
verde. Sigue sin
ejecutarse en este bloque la migración instrumentada en un emulador, la importación
real del asset grande y la inspección visual/TalkBack; no se afirma aceptación física.
