# fitter — presupuesto de tiempo y fitter nativo (tres rojos)

Fecha: 2026-10-01. Solo edición; no se ejecutó Gradle, adb ni tests. Los símbolos nuevos se verificaron con Grep y el diff se revisó contra `snapshot-pre-wave1`
(que no incluye los cambios de wizgen/wizvm/prog: mis hunks son los listados abajo). CRLF de `SimpleCyclePersonalizer.kt` conservado (1861/1861 líneas), el resto LF.

## Resumen del diagnóstico

| Rojo | Veredicto | Causa raíz |
|---|---|---|
| `NativeProfileRecipeAndFitterTest.cardio_defaults_use_session_time_boundaries_44_and_45` | DEFECTO DE PRODUCCIÓN (oráculo correcto) | El fitter tenía dos palancas que r2 no contiene: un «paso 4b» que acortaba el cardio y un ajuste de descansos. El «§12.3.4b» que citaba el código no existe en el plan (§12.3 tiene pasos 1–5). A 45 min el cardio por defecto es 15 y el día mide 46 > 45; el 4b lo bajaba a 10 y entregaba programa. |
| `PlanGenerationCoverageT006Test.Q3_fitter_viability_neighbors_and_cardio_default_boundaries` | MISMA RAÍZ que (1) (confirmado) | `default cardio a 45 min expected:<15> but was:<10>`: el 4b dejaba un 10 donde el default de SESSION_TIME es 15. El oráculo de Q3 ya acepta el resultado conforme al plan (rechazo temporal con `defaultCardio=15m`). |
| `SetupExecutableAvailabilityMatrixTest.T019_A_negativo_…_20min_4_filas` | MIXTO: defecto de producción (3 d) + defecto de ARNÉS (5 y 6 d) + oráculo débil (suficiencia) | Ver abajo. La premisa «3 de 4 filas fallan porque el requiredMinutes no es 21» solo es cierta para d3. |

## (1) y (2) — cardio 44/45

Aritmética (estimador real): Atleta corporal 1 día principiante (X1_NP = `P(S); B,F; S,Fv; puente,H; U,H` + cardio). Con cardio 10 mide 2460 s = 41 min (el test lo aserta a 44).
Cada escalón de cardio añade 5 / 10 / 20 min: 15 → 2760 s = 46,0; 20 → 3060 s = 51,0; 30 → 3660 s = 61,0. A SESSION_TIME = 45 el default es 15 (§11.4: «44 min selecciona default10 y
45 min default15; después se valida la duración total; no recalcular el default usando tiempo restante») → 46 > 45. En ese día no hay palanca legítima: sin I/C, H ya en 2 series en principiante,
Fv sin segunda exposición, un solo día (no hay a dónde mover) → §12.3 paso 5: `TIME_BUDGET` con el mínimo real (46), «no devolver éxito parcial».

Lo que hacía el producto (descripción del programa entregado, en el XML de g04): «Cardio Día 1: 15→10 min por presupuesto (§12.3.4b)».
- Paso 4b (l. ~1056 antes del cambio): `OFFERED_MINUTES.filter { it < actual }.maxOrNull() ?: (actual − 1).coerceAtLeast(5)` → con actual = 10 daba 9…5 min (contra «cardio ≥ 10 min por bloque» y «no inventar 12/14») y recortaba también los minutos explícitos del usuario (contra §11.4 «se valida completo; no se recorta silenciosamente» y §12.4 «sin reducirlos»).
- Ajuste de descansos (l. ~1088): −15 s a todo slot con descanso > 75 s si el exceso era ≤ 3 min; a 45 min (exceso 1) solo lo frenaba el suelo H9 del T1 (180 s) por casualidad; en planes sin T1/T2 (Músculo) podía entregar descansos de 105 s. Contra §12.2: «las recetas mínimas no se hacen caber bajando descansos».

Cambio (`SimpleCyclePersonalizer.personalizeNative`): se eliminaron ambos bloques (no se sustituyen) y se dejó un comentario con las citas de r2. Resultado esperado: 44 → programa con cardio 10 y `maxSessionMinutes = 41`; 45 → `null`, `TIME_BUDGET`, `maxSessionMinutes = 46` (46 − 41 = 5 exactamente lo que aserta el test).
Q3: 30 → rechazo temporal (mín. 41, `defaultCardio=10m`), 45 → rechazo temporal (mín. 46, `defaultCardio=15m`), 59 → Ready con cardio 15 (día 46), 60 → Ready con cardio 20 (día 51); el mapa esperado `{30:10, 45:15, 59:15, 60:20}` se cumple sin tocar Q3.

Tests que lo confirman: los dos rojos tal cual están + nuevo `NativeProfileRecipeAndFitterTest.athlete_cardio_minutes_are_validated_whole_and_never_trimmed_to_fit` (10/15/20/30 min explícitos → mínimos 41/46/51/61; un minuto menos → `TIME_BUDGET` con ese mínimo, nunca un cardio más corto).

## (3) — grupo A de la matriz (A-dN-m20 y A-dN-m21)

Datos del XML g04 (`T019_A_negativo…`): d1 pasa (21). d3: «mínimo real es de 25 min» + `diagnóstico de composición: W5 …`. d5 y d6: el plan propio YA informa «el mínimo real es de 21 min»
(`witnessRequiredMinutes=21`), pero fallan por «un rechazo interno no puede pasar como incompatibilidad de usuario»: `template:body-20-5@MATERIALIZATION` /
`template:body-16-4@MATERIALIZATION: IllegalStateException: No hay ExerciseCompositionMetadataProvider`.

### d3 — defecto de producción del fitter (mínimo informado incorrecto: 25 en vez de 21)

Reconstrucción por aritmética (no ejecutada; coincide con las cifras observadas):
- BFA sin ajustar: 180 + 3·300 (H) + 120 (superman, I) + 108 (core, C) + 3·90 (aproximaciones S, B y superman: SM es familia bisagra) = 1578 s → 27 (las sesiones de 27 de `A-d1-m30`).
- BFB sin ajustar: 180 + 900 + 120 (G) + 108 + 2·90 = 1488 s → 25.
- Con I y C retirados ambos son 3 H + 2 aproximaciones = 1260 s = 21,0 (confirmado en 1 día: `A-d1-m21` mide 21; en 5/6 días las sesiones medidas son 20,15,21,15,21 y 15,20,15,21,15,21 = BL, BU, BL_MRV).
- El fitter se atascaba en 25 por dos causas encadenadas:
  (a) `isFittable` comparaba por posición de día el MÁXIMO de todas las semanas (`minutesByDay`). En Músculo 3 días las semanas pares usan `[dayPlans[1], [0], [1]]` (`assembleNativeRecipe`), así que una posición la ocupan dos planes de día y reducir solo uno no baja el máximo mientras el otro empata → retirada rechazada por «sin progreso».
  (b) W5 («Accesorios cambian dentro del bloque»: el conjunto de accesorios de la semana debe ser el mismo en todas las semanas) rechaza retirar el superman de la primera BFA mientras la tercera lo conserva (la tercera no aparece en semanas pares).
  El estado final atascado es BFB sin ajustar (25) y BFA sin core pero con superman (1470 s → 25): «mínimo real 25».
- El 25 es incorrecto: r2 §12.3 paso 2 permite una receta de 21 min y §12.3 paso 5 exige «minutos mínimos calculados». Y `A-d3-m21` «pasaba» solo porque otro candidato histórico (`native:full-body`, visto en el MATRIX_ROW) cabía; el plan propio no era viable a 21.

Cambio: (1) `isFittable` compara cada sesión materializada (`allSessionMinutes`) en vez del máximo por posición (equivalente en calendarios sin alternancia, porque en ellos la posición de día = un único plan de día y la descarga deriva monótonamente del plan); (2) helper `alternatesEvenWeeks(kind, dayCount)` (Músculo 3 días; lo comparte `assembleNativeRecipe`) y, solo en ese calendario, el paso 2 se repite mientras acepte retiradas (el accesorio sale primero de la copia que W5 permite y después de la otra). Traza esperada a 20/21 min: pasada 1 → G de BFB, SM de la tercera BFA, C de la primera y de la tercera BFA; pasada 2 → SM de la primera BFA y C de BFB; final 3 H por día = 21 min (a 20 el paso 3/4 no tienen palanca y se informa `TIME_BUDGET` con 21).

### d5 / d6 — defecto del ARNÉS (no del mínimo)

Las plantillas del wizard (`CatalogSource.TEMPLATE` → `ProgramTemplateEngine.applyTemplate` → `PlanMaterializer.materialize` con `metadata = CompositionMetadataHolder.resolve()`) leen los metadatos del holder global que en producción rellena `ExerciseDatabase.initializeExerciseDatabase`. La matriz no inicializa `ExerciseDatabase` (y por diseño no llama a `CatalogCompositionTestSupport.install()`), así que cada plantilla publicada con 5 o 6 días se rechazaba con ISE → INTERNAL_MATERIALIZATION y la invariante «un rechazo interno no puede pasar como incompatibilidad de usuario» fallaba. Con 1 y 3 días no se publica ninguna plantilla (por eso d1 pasaba).
Cambio (solo test): `withProductionCompositionMetadata(enabled = row.expectedNegativeReason != null)` instala `CatalogCompositionMetadataProvider.fromCatalog(approvedCatalog)` (el mismo proveedor que producción, del asset ya verificado por identidad) y restaura el valor anterior; solo en filas negativas, porque `T001_03` compara el conjunto de rechazos con los ID publicados y no se quiso alterar el resto sin poder ejecutarlas. La aserción no se tocó.
Hallazgo de producción para otro dueño (SetupWizardViewModel, rama TEMPLATE): pasar metadatos explícitos en vez de depender del holder global.

### Oráculo (independiente, por fila)

`independentBodyweightMuscleFloorMinutes(days)` calcula el piso desde el calendario/arquetipos de r2 §11.3 (con BL_MRV por DEV-r2-01 en 5/6 días) y las constantes del estimador, aplicando el paso 2 de §12.3 con el suelo diario (≥ 2 configuraciones, ≥ 4 series).
Por día: BFA = BFB = BL_MRV = 1260 s (21); BL = 1170 s (19,5 → 20); BU = 888 s (14,8 → 15; no puede perder SM ni C: quedaría con 3 series). Pisos de fila: d1 = d3 = d5 = d6 = 21. Esas cifras por día son las que mide el motor en `A-d5-m21` y `A-d6-m21`, lo que valida el modelo.
Las filas de suficiencia (`rowsASufficiencyAtFloor`, id desde el piso de su fila) exigen ahora `requiredWitnessPlanId = native:muscle-foundation-v2` (el testigo no puede ser otro candidato viable) y que la sesión más larga mida EXACTAMENTE el piso. 12 positivos a {30,60,100} intactos.

Tests que lo confirman: `T019_A_negativo_…_20min_4_filas` (TIME_BUDGET tipado, etapa DURATION, `requiredMinutes == piso de la fila`, sin rechazos internos), `T019_A_suficiencia_…_21min_4_filas` (plan propio viable y de 21 min exactos en las 4 filas) y el nuevo `NativeProfileRecipeAndFitterTest.muscle_bodyweight_three_day_alternation_reports_and_reaches_its_real_floor`.

## Archivos tocados (todos míos)

- `android-native/app/src/main/java/com/example/kpkn/domain/training/SimpleCyclePersonalizer.kt` (CRLF): `isFittable`, repetición del paso 2, eliminación del 4b y del ajuste de descansos, helper `alternatesEvenWeeks`.
- `android-native/app/src/main/java/com/example/kpkn/data/protocols/definitions/NativeProfileSpec.kt`: solo KDoc de `NativeCardioDefaults`.
- `android-native/app/src/test/java/com/example/kpkn/domain/training/NativeProfileRecipeAndFitterTest.kt`: dos tests nuevos.
- `android-native/app/src/test/java/com/example/kpkn/screens/onboarding/SetupExecutableAvailabilityMatrixTest.kt`: oráculo por fila, `requiredWitnessPlanId`, holder en filas negativas, KDoc.
- `docs/WIZARD_PLAN_DEVIATIONS.md`: DEC-w1-01…08 (cardio/descansos, Músculo 3 d, matriz A, y las decisiones de wizvm y prog).
- Sin cambios: `SessionDurationEstimator.kt` (correcto), `PlanGenerationCoverageT006Test.kt` (su oráculo ya era el del plan).

## Riesgos / a mirar primero si algo sale rojo

1. Nada se ejecutó. El primer sospechoso del arreglo de d3 es que la secuencia de dos pasadas no converja a 3 H por día (W5 distinto del reconstruido); síntoma: `T019_A_suficiencia` d3 con «$plan debe ser viable» o `muscle_bodyweight_three_day_alternation_…` con `maxSessionMinutes` ≠ 21.
2. Filas que solo cabían gracias al 4b o al recorte de descansos pasan a `TIME_BUDGET` con mínimo ≤ presupuesto + 3 (Q2 las acepta; `Q2_required_positives` no debería depender de ellas: los testigos con cardio tras resistencia son 1d/60 y 2d/60 y miden 47–54 min por aritmética). Si algún positivo cae, es un hallazgo real, no un oráculo flojo.
3. d5/d6 negativas: tras instalar el holder las plantillas deberían rechazarse con MATERIAL tipado («Esta receta necesita material que no has declarado: …»); si aparece otro INTERNAL (otra dependencia global de la ruta de plantillas), la causa es del VM/plantillas, no del mínimo.
4. El mensaje de `TIME_BUDGET` conserva el sufijo «diagnóstico de composición: …» del último intento de composición fallido del fitter (p. ej. W5 de una retirada probada y descartada), aunque el mínimo informado sea correcto; es cosmético y preexistente. No se tocó.

## Verificación sugerida (de la más rápida a la más pesada)

```
./gradlew testBaseDebugUnitTest --tests '*.NativeProfileRecipeAndFitterTest.cardio_defaults_use_session_time_boundaries_44_and_45' --tests '*.NativeProfileRecipeAndFitterTest.athlete_cardio_minutes_are_validated_whole_and_never_trimmed_to_fit' --tests '*.NativeProfileRecipeAndFitterTest.muscle_bodyweight_three_day_alternation_reports_and_reaches_its_real_floor'
./gradlew testBaseDebugUnitTest --tests '*.NativeProfileRecipeAndFitterTest'
./gradlew testBaseDebugUnitTest --tests '*.SetupExecutableAvailabilityMatrixTest'
./gradlew testBaseDebugUnitTest --tests '*.PlanGenerationCoverageT006Test.Q3_fitter_viability_neighbors_and_cardio_default_boundaries' --tests '*.PlanGenerationCoverageT006Test.Q2_required_positives'
./gradlew testBaseDebugUnitTest --tests '*.PlanGenerationCoverageT006Test.Q2_real_generation_coverage_2304_inputs'   # timeout >= 3600 s, heap 4g
```
