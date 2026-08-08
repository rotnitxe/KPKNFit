# Auditoría — SERIES UNILATERALES: Editor de sesiones vs Sesión en vivo

Rutas relativas a `android-native/app/src/main/java/com/example/kpkn/`.
Solo lectura/análisis; no se ha editado código. Severidades: CRÍTICO / ALTO / MEDIO / BAJO.

## Modelo de datos (fuente de verdad comparada)

- Por ejercicio: `isUnilateral`, `unilateralMode`, `unilateralSideOrder`, `unilateralIntensityMode`, `restBetweenSidesSeconds` — `data/models/Session.kt:248-252`; enums en `Session.kt:292-294`.
  - `UnilateralMode { BILATERAL, UNILATERAL_PAIRED, UNILATERAL_DIFFERENTIAL }` — `Session.kt:292`. **`UNILATERAL_DIFFERENTIAL` nunca se asigna en producción**: el único toggle usa `UNILATERAL_PAIRED` (`screens/sessioneditor/SessionEditorControls.kt:853`).
- Por set: `leftTarget/rightTarget: UnilateralTarget?`, `restBetweenSides: Int?`, `plannedIntensityTechniques` — `Session.kt:360-363`. `UnilateralTarget` (weight, targetReps, targetDuration, targetValue, targetRPE, targetRIR, intensityMode) — `Session.kt:305-313`.
- Extensión canónica: `Exercise.isEffectivelyUnilateral() = unilateralMode != BILATERAL || isUnilateral` — `Session.kt:441-442`.
- En vivo: `CompletedSet.side: String? ("left"/"right")` — `data/models/WorkoutLog.kt:136`. `CompletedExercise` **no** persiste `unilateralMode` ni `restBetweenSidesSeconds` (`WorkoutLog.kt:90-113`): el registro histórico pierde la configuración; solo es inferible por `side` en los sets.
- `WarmupSetDefinition` no tiene campos unilaterales (`Session.kt:370-376`).

## 1) ¿Cómo persiste el editor la condición unilateral? ¿El vivo la interpreta igual?

### Editor (persistencia)

- Toggle: `toggledBilateralUnilateral()` — `screens/sessioneditor/SessionEditorControls.kt:848-900`.
  - ON: `isUnilateral = true`, `unilateralMode = UNILATERAL_PAIRED`, `restBetweenSidesSeconds = restBetweenSidesSeconds ?: 15` (default suave, línea 855) y copia los targets del set a `leftTarget` + `rightTarget` (856-870).
  - OFF: adopta `leftTarget ?: rightTarget` como target a nivel set y borra lados + `restBetweenSidesSeconds` + `restBetweenSides` (872-899).
- Selector: `UnilateralModeSelector` — `SessionEditorControls.kt:300-347`. **Solo mira `unilateralMode`** (línea 306), no `isEffectivelyUnilateral()`.
- Orden/intensidad de lados: `components/ExerciseEditorCard.kt:549-583` (escribe `unilateralSideOrder` LEFT_RIGHT/RIGHT_LEFT, 549-562; `unilateralIntensityMode` SHARED/INDEPENDENT, 564-583).
- Rest entre lados: `ExerciseEditorCard.kt:405-420` (`CompactRestBundleButton`, onConfirm escribe `restBetweenSidesSeconds = side?.takeIf { it > 0 }`, 411-417).
- Por set/lado (doble tarjeta + ghosts): `SessionEditorControls.kt:462-575`; ghost añade `leftTarget`/`rightTarget` copiando el set (553-568); `UnilateralAddGhostCard` en 546-571 y 632 (superserie: `components/SupersetRoundsAndConfig.kt:632`).
- `addSet` con fusión por lado: `SessionEditorViewModelStructure.kt:393-439`:
  - Si lado "left" existe un right-only set, se fusiona en el último (413-419); análogo para "right" (420-427).
  - Sin lado en ejercicio unilateral: `nextSet.copy(leftTarget = null, rightTarget = null)` (432-433) → ambos lados en vivo.
  - `defaultSideTarget()` copia weight/reps/duration/targetValue/RPE/RIR/intensityMode del set (402-410).
- Edición inline + espejo SHARED: `components/InlineSetRow.kt:131-171` (`updateUniSet` espeja a ambos lados si SHARED, 165-168; solo activo si INDEPENDENT, 170-171). Intensidad escribe a la vez set-level y side (`InlineSetRow.kt:493-501`).
- Chips de técnicas: `components/InlineSetRowTechniqueChips.kt:67-156` (escribe `plannedIntensityTechniques` + flags legacy `isDropSet`/`isRestPause`).
- `normalizeExercise()` preserva side targets explícitamente: `SessionEditorSessionHelpers.kt:343-354`; `normalizeSet` (439-472) ignora el interior de los side targets (no normaliza `leftTarget.targetReps` etc.).

### Vivo (interpretación)

Usa la misma extensión/campos:
- Pasos: `screens/workout/WorkoutStepRules.kt:214` (`!isEffectivelyUnilateral()` → paso único), 228-236 (sides por set).
- Registro: `screens/workout/WorkoutSetRecorder.kt:129-131,154-155`.
- Navegación: `screens/workout/WorkoutStepNavigator.kt:228,240-248`.
- UI: `screens/workout/WorkoutV2Body.kt:395` y helpers `expectedSidesForSet`/`completionKeysForSet` (1193-1223); `screens/workout/WorkoutAndroidTestSupport.kt:409`.
- Edición en vivo: `screens/workout/WorkoutEditingRules.kt:34`.
- Estructural en vivo reutiliza el carousel del editor: `screens/workout/WorkoutStructureSheetsHost.kt:695-810` (`toggledBilateralUnilateral` 715, rest entre lados 737-751, addSet por lado 761-794).

**Paridad de campo correcta**, con dos advertencias:
- **Duplicación de la lógica** (BAJO): `isEffectivelyUnilateral` reimplementada de forma privada en `screens/workout/components/WorkoutRoadmapBar.kt:310-311` y regla `isSetDone` duplicada en `WorkoutEditingRules.kt:53-57` vs `WorkoutViewModel.kt:1120-1124`. Misma semántica hoy; riesgo de deriva.
- **Desincronía visual legacy** (BAJO): con datos viejos `isUnilateral = true` + `unilateralMode = BILATERAL`, el chip del editor muestra "Bilateral" (`SessionEditorControls.kt:306`) aunque el ejercicio se comporta como unilateral en editor y vivo.

## 2) ¿La secuencia en vivo (izq/der, descanso entre lados) es coherente con lo configurado?

- Generación de pasos por set: `WorkoutStepRules.kt:206-255`. Un paso por lado respetando `unilateralSideOrder` (234-235) y la detección de sets de un solo lado: `hasLeftOnly`/`hasRightOnly` (229-233). Claves `workingStepKey(ex, idx, side)` → `ex_idx_L/_R` (65-74.).
- Superseries: mismos pasos L/R dentro de cada ronda (`WorkoutStepRules.kt:119-134`); `restAfterKind` del primer lado se sobreescribe a `BETWEEN_SIDES` (248-249).
- Descanso entre lados: SOLO tras el primer lado y SOLO si `(exercise.restBetweenSidesSeconds ?: 0) > 0` (`WorkoutStepRules.kt:248-252`). El recorder confirma con `unilateralPendingOtherSide` (comprueba clave de la contraparte) y arranca rest `BETWEEN_SIDES` (`WorkoutSetRecorder.kt:430-435,445-452`) con la misma fuente de segundos. **Ambas vías usan exclusivamente el campo a nivel EJERCICIO.**
- Tras registrar L no hay navegación automática al paso R: el recorder no llama `nextSet` cuando falta el otro lado (`WorkoutSetRecorder.kt:430-435`) y el timer arranca con `advanceOnFinish = false` (`WorkoutSetRecorder.kt:543-551`; handler natural en `WorkoutRestTimerOrchestrator.kt:236-260`). El usuario desliza/toca el paso R (pager → `selectWorkoutStep`, `WorkoutV2Body.kt:448-480`). El overlay de descanso conoce el tipo `BETWEEN_SIDES` (`screens/workout/WorkoutRestOverlay.kt:234,356`).
- Páginas del pager una por lado según `expectedSidesForSet` (`WorkoutV2Body.kt:380-420`), tarjeta bloqueada al lado (`sideLocked = isUnilateral && cardSide != null`, 941-942).

Hallazgos de coherencia:
- **ALTO — Completar/saltar sets de un solo lado rompe la secuencia.** `skipSet` usa solo `unilateralSideOrder` (ignora `hasLeftOnly/hasRightOnly`) y marca como skipped la PRIMERA contraparte incompleta (`WorkoutStepNavigator.kt:240-266`); `stillPendingSide` exige ambos lados (286-289). En un set left-only: 1er tap skip→ L skipped, no avanza; 2º tap → marca R (inexistente en los pasos) como skipped — set fantasma que además entra al log (`WorkoutFinishController.kt:87-92`). Igual en `skipCurrentSupersetRound`: marca SIEMPRE left+right (`WorkoutStepNavigator.kt:186-204`).
- **MEDIO — `ExerciseSet.restBetweenSides` (por set) está muerto.** `Session.kt:362`: ningún consumidor en `app/src/main`; el editor solo lo escribe a `null` (`SessionEditorControls.kt:888,894`). Cualquier "descanso entre lados por set" configurado jamás se ejecuta; efectivo únicamente `restBetweenSidesSeconds` (ejercicio).
- **BAJO — Sin auto-avance L→R tras el descanso entre lados** (ver notas de navegación arriba). UX manual.

## 3) ¿El registro (peso/reps por lado) funciona en vivo tal como el editor lo configura?

Lo esencial funciona:
- Defaults por lado desde `leftTarget/rightTarget`: `components/SetExecutionCard.kt:684-696` (peso por lado), 688-696 (métrica por lado), 705-711 (targetDuration por lado), 715-721 y 955-956 (RPE/RIR por lado).
- Claves de registro `${exerciseId}_${setIdx}_L/_R`: `WorkoutSetRecorder.kt:607-611`; `side` persistido en `RecordedSetPayload` (228) y en el `CompletedSet` (`WorkoutSetRecorder.kt:344`; `WorkoutLog.kt:136`).
- Drafts por lado con fallback: `WorkoutViewModel.kt:1146-1206` (`workoutSetKey(ex, idx, side)`, `WorkoutSessionContracts.kt:49-53`); escritura desde la tarjeta `WorkoutV2Body.kt:937-940`.
- Override manual de carga por lado: `WorkoutSetRecorder.kt:420-421` + `WorkoutLoadSuggestionController.kt:658-663`.
- Edición posterior por lado: `WorkoutEditingRules.kt:37-49` (elige side y clave).
- Hydratación de estado con claves por lado: `WorkoutSessionHydrator.kt:122,242` (tal cual) y `resolveResumePosition`.
- Aviso de desbalance por par: `WorkoutViewModel.kt:1385-1429` (RPE/RIR/reps/carga), disparado solo en unilaterales (`WorkoutSetRecorder.kt:397-401`, `WorkoutStepNavigator.kt:268-272`).

Huecos:
- **MEDIO — Sugerencia de carga sin side / sin side targets.** La tarjeta pide la sugerencia sin lado (`WorkoutV2Body.kt:908-912`, tercer argumento = tag, `side` queda por defecto null); `plannedWorkingWeightForSet` ignora `leftTarget/rightTarget` (`WorkoutLoadSuggestionController.kt:687-696`). El chip/autorregulación no respeta pesos configurados por lado (aunque el prefill de la tarjeta sí, `SetExecutionCard.kt:684-687`). La voz sí pasa `side = pendingSide` (`WorkoutViewModel.kt:670-673`).
- **MEDIO — Deuda/desviaciones calculadas sin side targets.** `inferPlannedTarget` (`WorkoutViewModel.kt:987-992`) e `inferPlannedIntensity` (994-999) leen solo campos a nivel set; la deuda por lado frente a targets por lado no se calcula (`WorkoutSetRecorder.kt:171-176`).
- **BAJO — Fallback peligroso del side al grabar.** `initialSide = side ?: expectedSide ?: "left"` si el ejercicio es unilateral (`WorkoutSetRecorder.kt:129`): una vía sin side (p. ej. integraciones) escribe en `_L` aunque el set sea right-only, dejando el paso `_R` pendiente para siempre. Mitigado porque pager y voz sí pasan side (`SetExecutionCard.kt:2538,2563,2584`; `WorkoutViewModel.kt:651-657,670`).
- **BAJO — `addSet` en vivo no copia `targetValue` al `UnilateralTarget` base** (`WorkoutStructureSheetsHost.kt:763-770` vs editor `SessionEditorViewModelStructure.kt:406`): los sets añadidos por lado en vivo pierden `plannedTargetV2` en el nuevo lado.

## 4) ¿Warmups unilaterales y técnicas avanzadas (drop, myo-reps) combinadas con unilateral?

### Warmups — paridad (ambos carecen de lado)

- Modelo sin campos: `Session.kt:370-376` (`WarmupSetDefinition`).
- Editor sin UI de lado en warmup: búsqueda sin coincidencias en `screens/sessioneditor/components/sheets/ClonerSaveWarmupSheets.kt` (`WarmupSheet` recibe solo `List<WarmupSetDefinition>`, invocación en `components/sheets/SessionEditorSheets.kt:591`).
- Vivo sin pasos por lado: `WorkoutStepRules.kt:188-204` (warmupStep sin side), una sola página de warmup por ejercicio (`WorkoutV2Body.kt:388-391`), claves de completado sin side (`WorkoutViewModel.kt:2130-2151`, `WorkoutStepNavigator.kt:639-640`).
- **MEDIO — Ancla de peso del warmup ignora side targets.** `getWarmupWorkingWeightAnchor` usa `side = null` y `plannedWorkingWeightForSet(exercise, 0)` (`WorkoutLoadSuggestionController.kt:398-410,687-696`): si el peso de trabajo solo existe por lado (set.weight = null), el "Peso sugerido" del warmup puede quedar vacío (`WorkoutV2Body.kt:832-876`). Conclusión: soporte igual (ausente) en ambos lados; no es divergencia, es gap de producto.

### Técnicas avanzadas

- Editor: DROP_SET / REST_PAUSE vía `plannedIntensityTechniques` + flags `isDropSet`/`isRestPause` (`components/InlineSetRowTechniqueChips.kt:67-156`, params 171-253). **Myo-reps NO existe** en Android: `TechniqueType = { DROP_SET, REST_PAUSE, PARTIALS, ISO_HOLD, NEGATIVES, CLUSTER_SET }` (`Session.kt:295`).
- Vivo: el flujo guiado lee correctamente lo planeado (`components/SetExecutionCard.kt:830-868`) y aplica técnicas al grabar (`WorkoutSetRecorder.kt:217-223,2295-2304`).
- **ALTO — Drops/rest-pause del lado L se aplican a R (contaminación cruzada).** Estado `dropSetEnabled`/`dropSets`/`restPause*` keyed solo por `(exercise.id, setIndex)` — NO por lado (`SetExecutionCard.kt:794-824`); tras `commitCapturedRecord` el flujo RETIENE los valores (`dropSetEnabled = true`, `dropSets = dropOverride`, 2338-2345) y cambia automáticamente de lado (2357-2359; también 2565-2566, 2584-2585). Al reportar R sin limpiar, `advanced.dropSets`/`restPauses` vuelven a aplicar los valores de L (2295-2304). Data integrity en unilateral + técnicas.
- **MEDIO — Timer por voz (series por tiempo) ignora lado y duración por lado.** `startTimedSet()` usa draft `side = null` (`WorkoutVoiceCommandHandler.kt:618,642-643`) y objetivo a nivel set (`set.targetDuration ?: plannedTargetV2`, 619-621), nunca `leftTarget/rightTarget.targetDuration`; la lectura del card usa el draft del pendingSide (`WorkoutViewModel.kt:670`), por lo que el draft null-side puede no mostrarse en la tarjeta L o R esperada.

## 5) ¿Volumen/AUGE considera unilateral igual en editor (preview) y vivo (WorkoutLog)?

- **Drain AUGE por set: paridad aproximada.** Vivo escala cada lado ×0.5 (`domain/auge/AugeFatigueEngine.kt:276-277`: "each side is half a logical set so L+R ≈ one bilateral set"); preview editorial cuenta 1 por set planeado (`screens/sessioneditor/SessionEditorAugeComputation.kt:331-367`, `validAugeSets` = `filterNot { isIneffective }` en 687; duplicado en `domain/sessionassistant/SessionAssistantEngine.kt:194-250,1028-1036`). L+R ≈ 1 set en ambos mundos. **Excepción (BAJO)**: sets de un solo lado — 0.5 en vivo vs 1.0 en preview.
- **ALTO — Volumen / acumulados ≈ 2× en vivo vs preview.** El `WorkoutLog` guarda L y R como sets separados (`screens/workout/WorkoutFinishController.kt:87-92`) y `totalVolume` suma crudo `peso × repEquivalent` de ambos (149-151). El preview cuenta 1 por set planeado (`domain/training/VolumeCalculator.kt:208-219` `countEffectiveSets`; `SessionEditorAnalytics.kt:466-496`). Analytics de programa sin awareness de lado en logs ni en plan (`domain/training/ProgramAnalyticsEngine.kt:634-637` planned, 639-643 logs; `unilateralExerciseRatio` solo cuenta ejercicios, 245-246). Consecuencia: histórico, volumen semanal por músculo y recomendaciones derivadas ven ~2× lo que el preview prometía para ejercicios unilaterales.
- **MEDIO — Progreso de sesión inflado.** `completedSets.size` (cuenta lados) sobre `allExercises.sumOf { it.sets.size }` (no cuenta lados) — `WorkoutSetRecorder.kt:481-495`: en sesiones con unilaterales el progreso llega antes al 100 %. El drain acumulado por set comparte ese contador (482-489).
- **MEDIO — Duración estimada subestimada.** `TimeCoachEngine.effectiveSetCount` = 1 por set (`domain/sessionassistant/TimeCoachEngine.kt:849-852`) y los previews del editor usan los mismos conteos: un ejercicio unilateral tarda ~2× (2 lados + descanso entre lados) pero se estima como 1 set.
- **MEDIO — El log histórico no conserva la configuración unilateral.** `CompletedExercise` sin `unilateralMode`/`restBetweenSidesSeconds` (`data/models/WorkoutLog.kt:90-113`); solo `CompletedSet.side` (136). La analítica no puede reconstruir intención (diferenciar pares L/R completos de sets de un solo lado).
- **BAJO — Copy del aviso de desbalance.** `"...Considera trabajo unilateral."` en ejercicios que YA son unilaterales (`WorkoutViewModel.kt:1429`).
- **BAJO — `SHARED` vs `INDEPENDENT` sin efecto vivo.** Solo espejo del editor (`InlineSetRow.kt:160-171`); el `computeImbalanceNotice` se calcula igual en ambos modos (`WorkoutViewModel.kt:1385-1429`).

## 6) Bugs concretos y lista priorizada

1. **ALTO — Drops/rest-pause del lado L se aplican a R (contaminación cruzada).** `components/SetExecutionCard.kt:794-824` (estado keyed solo por ejercicio/set), 2295-2304 (aplicación en el reporte), 2338-2359,2565-2566,2584-2585 (retención + auto-cambio de lado).
2. **ALTO — Editar el descanso de un ejercicio unilateral dentro de superserie borra `restBetweenSidesSeconds`.** `ExerciseEditorCard.kt:407-417`: en superserie `sideSeconds = null` (409) → `onConfirm` escribe `restBetweenSidesSeconds = side?.takeIf { it > 0 }` = null (416). En vivo desaparece el `BETWEEN_SIDES` (`WorkoutStepRules.kt:248-252`, `WorkoutSetRecorder.kt:446-452`).
3. **ALTO — Volumen/AUGE acumulado ≈ 2× vs preview para unilaterales.** Log: L+R separados y sumados (`WorkoutFinishController.kt:87-92,149-151`; `ProgramAnalyticsEngine.kt:634-643`); preview: 1/set (`SessionEditorAugeComputation.kt:331-354,687`; `VolumeCalculator.kt:208-219`). Solo el drain por set se mitiga con ×0.5/lado (`AugeFatigueEngine.kt:276-277`).
4. **ALTO — skipSet/skipRound rompen sets de un solo lado y crean sets fantasma.** `WorkoutStepNavigator.kt:240-266` (ignora `hasLeftOnly/hasRightOnly`), 286-289 (exige ambos lados), 186-204 (ronda marca siempre ambos lados); el fantasma entra al log (`WorkoutFinishController.kt:87-92`).
5. **MEDIO — Sets de un solo lado nunca se consideran "hechos" / no editables.** `isSetDone` exige L y R (`WorkoutViewModel.kt:1120-1124`; copia en `WorkoutEditingRules.kt:53-57` con early-return null en 35). Consecuencias: sin edición del set (`WorkoutEditingRules.kt:26-51`), voz los lista como pendientes (`WorkoutVoiceCommandHandler.kt:593-610`), sugerencias recalculadas para el lado ya completado y `finishUpToCurrentPoint` marca el ejercicio como omitido (`WorkoutViewModel.kt:2266-2285`).
6. **MEDIO — Toggle unilateral a mitad de sesión abandona progreso previo (claves sin migrar).** Claves viejas `${ex}_${idx}` no cuentan para pasos con side (`WorkoutStepNavigator.kt:689-695`) y las claves `_L/_R` no cuentan si el ejercicio pasa a bilateral; hidratación pasa el mapa tal cual (`WorkoutSessionHydrator.kt:122,242`).
7. **MEDIO — Campo `ExerciseSet.restBetweenSides` (por set) muerto.** `Session.kt:362`; ningún consumidor en vivo; el editor solo lo escribe a `null` (`SessionEditorControls.kt:888,894`).
8. **MEDIO — Sugerencia de carga y deuda ignoran targets por lado.** `WorkoutLoadSuggestionController.kt:687-696`; llamada sin side desde `WorkoutV2Body.kt:908-912` (voz sí: `WorkoutViewModel.kt:670-673`); `inferPlannedTarget`/`inferPlannedIntensity` (`WorkoutViewModel.kt:987-999`).
9. **MEDIO — Progreso de sesión inflado con unilaterales.** `WorkoutSetRecorder.kt:481-495`.
10. **MEDIO — Warmup sin peso sugerido cuando el peso solo existe por lado; sin soporte de lado en warmup (ambos lados del sistema).** `WorkoutLoadSuggestionController.kt:398-410`; `WorkoutStepRules.kt:188-204`; modelo `Session.kt:370-376`.
11. **MEDIO — Timer por voz (series por tiempo) ignora lado y duración por lado.** `WorkoutVoiceCommandHandler.kt:612-652`.
12. **BAJO — Duplicación de `isEffectivelyUnilateral` / `isSetDone`.** `WorkoutRoadmapBar.kt:310-311`, `WorkoutEditingRules.kt:53-57` vs canónica `Session.kt:441-442` / `WorkoutViewModel.kt:1120-1124`.
13. **BAJO — Código muerto unilateral en editor.** `components/SetEditorUnilateralAndSuperset.kt:20-282` (3 composables sin llamadas: `UnilateralIntensityModeSelector`, `SideTargetRow`, `CompactSupersetSetRow`) y path completo de `components/SetEditorCards.kt` (`SetSideMode` 31, `RemoveSide` 69, `ToggleLink` 71 — sin consumidores fuera del archivo).
14. **BAJO — Selector unilateral del editor solo lee `unilateralMode`.** `SessionEditorControls.kt:306` (desincronía visual con `isUnilateral` legacy).
15. **BAJO — `UNILATERAL_DIFFERENTIAL` inalcanzable** (`Session.kt:292`); `SHARED`/`INDEPENDENT` sin diferencia funcional en vivo.
16. **BAJO — `addSet` en vivo no copia `targetValue`.** `WorkoutStructureSheetsHost.kt:763-770` vs editor (`SessionEditorViewModelStructure.kt:406`).
17. **BAJO — Mensaje de desbalance incoherente.** `WorkoutViewModel.kt:1429`.
18. **BAJO — Sin auto-avance al otro lado tras el descanso entre lados.** `WorkoutSetRecorder.kt:543-551`; `WorkoutRestTimerOrchestrator.kt:236-260`.

## Veredicto

El modelo de persistencia es único y el vivo lo interpreta con los mismos campos (`isEffectivelyUnilateral`, side targets, orden de lados): la base está bien alineada. Las desconexiones graves son: (a) técnicas avanzadas que contaminan el otro lado; (b) borrado accidental del descanso entre lados al editar descansos en superserie; (c) familia de bugs de sets de un solo lado (skip/edición/finalización exigen siempre L+R y pueden crear sets fantasma); y (d) contabilidad de volumen/AUGE acumulado (≈2×), progreso de sesión y duración frente a lo que el preview editorial promete — aunque el drain por set está deliberadamente corregido con ×0.5 por lado. Auditoría solo de lectura; no se ha editado código de producción.






