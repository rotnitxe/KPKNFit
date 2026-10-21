# Sistema C — Entrenamiento (modelos `models/workout`, servicios `services/workout`, paneles de grupos musculares)

**Fuente principal:** `wk_libapp.so` (MacroFactor Workouts 1.4.1, armeabi-v7a, sin punteros comprimidos). Digests: `re/digests/C_wk.txt` (709 clases), `C_wk_skel.txt`, `C_wk_fns.txt`, `C2_wk.json/.txt` (paneles de grupos musculares). El binario de Nutrition (`C_nu`, 526 clases) contiene las mismas librerías `models/workout` y `services/workout`; no se detectaron diferencias de taxonomía.
**Convenciones:** offsets = direcciones virtuales en `wk_libapp.so` (texto del snapshot desde `0x620000`). «palabras» = ranuras de 4 B (arm32) incl. cabecera. Las cadenas hexadecimales de 32 caracteres asociadas a cada valor de enum son identificadores estables (OBSERVADO que existen; INFERIDO que son las claves usadas en la base de datos de ejercicios en JSON).

---

## C.0 Taxonomía (enumeraciones, OBSERVADO)

| Enum | Nº | Valores |
|---|---|---|
| `MuscleGroup` | 22 | chest, quads, upperBack, glutes, lats, hamstrings, biceps, triceps, frontDelts, sideDelts, lowerBack, abs, calves, upperTraps, rearDelts, forearms, obliques, abductors, adductors, neck, tibs, serratus |
| `MuscleGroupCategory` | 2 | upper, lower |
| `RegionTrained` | 4 | upperBody, lowerBody, core, fullBody |
| `MovementPattern` | 44 | horizontalPush, horizontalPull, verticalPush, verticalPull, hipHinge, squat, loadedCarry, coreStabilization, chestAccessory, lateralDeltAccessory, bicepsAccessory, tricepsAccessory, upperTrapAccessory, lungeOrSplitSquatOrStepUpOrSingleLegSquat, quadAccessory, hamstringAccessory, hipAbductorAccessory, calfAccessory, forearmAccessories, shoulderRotationAccessories, olympicLiftVariation, grip, rearDeltAccessory, latAccessory, neckAccessory, throwPattern, frontDeltAccessory, upperBackAccessory, lowerBackAccessory, abAccessory, obliqueAccessory, ankleAccessory, hipAdductorAccessory, gluteMaxAccessory, serratusAccessory, hipFlexorIsolation, tibialisAnteriorAccessory, cardio, other, neckExtensorAccessory, neckFlexorAccessory, anteriorForearmAccessory, posteriorForearmAccessory, miscForearm |
| `JointAction` | 148 | desde elbowFlexion (0) hasta inspiration (147): acciones de codo, antebrazo, hombro, muñeca, dedos, cintura escapular, cadera, rodilla, tobillo, tronco, cuello, mandíbula/hioides, respiración (`ribDepression`, `inspiration`), `gripping`, `na` |
| `ExerciseType` | 3 | singleJoint, multiJoint, core |
| `ExerciseDescriptor` | 9 | core, strength, isolationAccessory, primaryCompound, hypertrophy, neither, bodyweightHomeEquipment, both, secondaryCompound |
| `ExerciseCategory` | 5 | upperCompound, lowerCompound, upperIsolation, lowerIsolation, core |
| `ExerciseMetric` | 11 | reps, repsPerSide, weight, weightPerSide, weightPerSidePersistent, assistanceWeight, duration, durationPerSide, distanceShort, distanceShortPerSide, distanceLong |
| `ExerciseMetricType` | 5 | weight, duration, distanceShort, distanceLong, reps |
| `EquipmentCategory` | 13 | freeWeights, loadableBars, fixedWeightBars, bandsAndRopes, bodyWeights, benchesAndRacks, accessoriesAndFunctional, loadableAccessories, cableMachines, plateLoadedMachines, pinLoadedMachines, cardio, other |
| `LateralityType` / `LateralityGroupType` | 3 / 4 | bilateral, unilateral, asymmetrical / + unilateralAndBilateral |
| `ExerciseSetType` | 5 | standard, warmUp, drop, myo, failure |
| `ExerciseTargetLogField` | 5 | minReps, maxReps, rir, duration, distance |
| `RirLevel` (DTO Swift) | 7 | zero…five, infinity |
| `WarmUpAutomation` | 3 | smart, scheme, none |
| `InitialLogFill` | 2 | smartProgression, previous |
| `FocusMetric` | 2 | previous, auto |
| `PreviousReference` | 2 | anyWorkout, withinProgram |
| `WorkoutDeload` | 2 | firstCycle, lastCycle |
| `CycleType` | 2 | weeks, cycles |
| `ExerciseViewMode` | 13 | oneRepMax "1rm", threeRepMax "3rm", tenRepMax "10rm", totalVolume "tv", bestSetVolume "bsv", heaviestWeight "hw", totalReps "tr", bestSetReps "bsr", totalDuration "tdu", bestSetDuration "bsdu", totalDistance "tdi", bestSetDistance "bsdi", totalSets "ts" |
| `WorkoutRecordType` | 3 | volume, reps, oneRepMax |
| `MuscleGroupViewMode` (package:dashboard) | 2 | sets "s", volume "v" |
| `ProgramSplit` | 13 | fullBody (días: null), upperLower (2), pushPull (2), upperLowerFull (3), pushPullLegs (3), upperLowerUpperLower (4), pushPullHingeSquat (4), pushPullLegsAccessories (4), upperLowerUpperLowerUpper (5), pushPullHingeSquatAccessorries (5), pushPullLegsUpperLower (5), pushPullHingeSquatUpperLower (6), pushPullLegsPushPullLegs (6) |
| `SplitExerciseType` | 34 | allExercises, allUpper, allLower, primary/secondary UpperBodyCompounds, upperBodyAccessories, primary/secondary LowerBodyCompounds, lowerBodyAccessories, allAccessories, primary/secondary × {HorizontalPush, HorizontalPull, VerticalPush, VerticalPull, Squat, Hinge}, primary/secondary Push/Pull Compounds, push/pull/squat/hinge Compounds y Accessories |
| `CompoundMovementPattern` | 6 | horizontalPush, verticalPush, horizontalPull, verticalPull, squat, hinge |
| `ProgramGoal` | 3 | hypertrophy, strength, both |
| `ProgramPeriodization` | 4 | static, linear, reverseLinear, undulating |
| `ExerciseTemplateType` | 3 | primary, secondary, accessory |
| `ExerciseAssessment` | 7 | pullUps5, pullUps10, dips10, pushUps15, benchPress10, inclinePress10, overheadPress10 |
| `GymTemplate` | 7 | everything, commercial, warehouse, local, garage, home, blankSlate |
| `MicroAdjustmentType` | 3 | dial, selectorizedPins, externalPlates |
| `ResistanceLabel` | 8 | extraLight, light, medium, mediumHeavy, heavy, extraHeavy, superHeavy, heaviest |
| `PlateCalculatorLaterality` | 2 | perSide, singleSide |
| `PlanDeltaOrderSource` | 2 | source, update |
| `WorkoutBlockGroupType` | 3 | single, superset, circuit |
| `NameLocale` | 3 | en, ja, generic |

## C.1 Modelos principales (OBSERVADO: clases *freezed* con `toJson`, `copyWith`, `_$identity`)

- **Ejercicio y bases:** `Exercise` (campos supervivientes: `_englishSearchKeywords`, `_japanSearchKeywords` y tuplas de candidatos de búsqueda; getter `source`), `ExerciseDatabase` (`exerciseById: Map<String, Exercise>`, `allowedMetricCombinations: List<List<ExerciseMetric>>`, operador `[]`), `EquipmentDatabase` (`equipmentIdToResistanceGroups: Map<String, List<ResistanceGroup>>`), `Equipment` (getter `weightKg`), `ResistanceGroup` → `LoadedResistanceGroup` / `SingleResistanceGroup`, `EquipmentGroup`, `exerciseSynonyms: Map<String, String>` (estático), `LocalizedName`.
- **Historial:** `Workout` (`activeExerciseDetailIds: Set<String>`, `activeSetsByExerciseId: Map<String, List<(Weight?, WorkoutSet)>>`, `exerciseDetailIds`), `WorkoutBlock`, `WorkoutExercise` (`OrderedSetIndex` → `RegularOrderedSetIndex` / `WarmUpOrderedSetIndex`), `WorkoutSet`, `WorkoutLog` (claves JSON `fullReps`, `partialReps`, `weight`, `rir`, `durationSeconds`, `distance`, `isSkipped`, `restTimer` — con `Duration` por defecto de 120 s), `WorkoutCompletion` (`Completed`/`Skipped`), `WorkoutCycleCompletion`, `WorkoutSource` (`Program`/`CustomWorkout`), `WorkoutRecord`.
- **Sesión en vivo:** `WorkoutSession` (punteros de navegación `NoPointer`, `ExercisePointer`, `SetPointer`, `SegmentPointer`, `LogPointer`, `FieldPointer`), `WorkoutSessionBlock/Exercise/Set/Log`, `WorkoutTimeEvent {pause, play}` + `WorkoutTimeEventSegment`, `RestTimer`, `AutoTarget` (sellada; `_AutoTarget` 6 palabras), `FixableAutoTarget` (6), `AutoTargetsEvent`, `CalculatedWarmUpSource` (sellada: `SmartWarmUpSource`, `SchemeWarmUpSource`), `LogTarget`. Claves JSON de `WorkoutSessionExercise`: `id`, `exerciseId`, `sets`, `note`, `autoTargetsEvents`, `warmUpSources`, `warmUpScheme`, `selectedWarmUpAutomation` (mapa enum→"smart"/…), `originalTarget`, `overrideRestTimers` (C_wk.txt l.7231).
- **Contextos de sesión:** `WorkoutSessionContext(Base)`; `WorkoutSessionExerciseContext` (`fixableAutoTargets: List<FixableAutoTarget?>`, `previousFirstLog: (Weight?, WorkoutLog)?`, `smartWarmUpTargets: List<(double, AutoTarget)>`, `hasWorkedOutPrimaryAgonists: bool`, `hasDoneSameExercise: bool`, método `getClosestWarmUpWeight`); `WorkoutSessionSetContext` (`previousSet`); `WorkoutSessionLogContext` (`defaultLogValue`, `previousLog`, `logTargetFromSetTarget`, `logTargetFromSetValue`).
- **Programa:** `WorkoutProgram` (campos de `copyWith`: `id`, `name`, `updatedAt`, `color`, `icon`, `days`, `experience`, `numCycles`, `deload`, `isPeriodized`, `programExerciseIdToNote`, `workoutCycleCompletions`, `runIndefinitely`), `WorkoutProgramDay/Block/Exercise`, `Periodization` (sellada: `SimplePeriodization(value, deload)` JSON 'simple'; `PeriodizedPeriodization(values, deload)` JSON 'periodized'), `CycleTarget`, `ExerciseTarget` → `ExerciseTargetSet(setType, log, segments)` → `ExerciseTargetLog`, `WorkoutPlan/Block/Exercise`, `CustomWorkout`, `WorkoutSection(Index)`, `Note`.
- **Configuración:** `UserExerciseConfig`, `WarmUpScheme` / `WarmUpSchemeSet`, `LiveActivityConfig` (`RestCompleteLiveActivityBehavior {none, autoAdvance, nudge}`), `Gym` (`weightUnitOrDefault`), `EquipmentWeightConfig` (sellada: `Base…`, `Ranges…`, `Weights…`), `EquipmentWeightRange`, `EquipmentMicroAdjustmentConfig`, `EquipmentWeight`, `MeasuredWeight` (operadores `+ − * /`), `Laterality<T>` (`SingleLaterality`, `LeftRightLaterality`, `LateralityIndex {single, left, right}`).
- **Diff de plan:** `PlanDiff` (`ExerciseAnchor`, `ExerciseMove`, getter `source`), `PlanDelta` (`PlanDeltaAddedExercise`, `…RemovedExercise`, `…ModifiedExercise`, `isNotEmpty`).
- **UI:** `ExerciseUiState` (*freezed*, `toJson`) + `ExerciseUiStateController` por `exerciseId` (familia Riverpod); `ExerciseViewModeData` (`Sets`, `Distance`, `Duration`, `Reps`, `Volume`).
- **Servicios:** `StrengthService` (const) con `XRmCurve` (8 palabras), `SimpleSet` (6), `SimpleWorkout` (2), `SimpleTarget` (10); `WorkoutProgramService` (`exerciseDatabase`, `equipmentDatabase`) con `TimeCalcAllocation` (10) e `IsolationAccessoriesAssignment`; `WorkoutService`; `WorkoutDatabase`; `WorkoutImportService`; `WorkoutProgramImportService`; `PlateLoadWeightCache`; `RestTimerNotificationService` (`_singlePulseVibrationPattern: Int64List`); canales Flutter↔Swift.

## C.2 Afirmaciones por tema

### C.2.1 Series y volumen por grupo muscular

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| El panel de grupos musculares tiene dos modos: series y volumen. | OBSERVADO | Enum `MuscleGroupViewMode {sets "s", volume "v"}` (`package:dashboard/src/model/sections/muscle_group_view_mode.dart`); vista `MuscleGroupDataView` (`ui/dashboard/experiences/workouts/muscle_group_data_view.dart`). |
| Las series de cada ejercicio cuentan 1,0 para un grupo muscular primario y 0,5 para uno secundario. | INFERIDO (fuerte) | Closure `(WorkoutExercise) -> double` @0xb7a810 / @0xb7b4a4 (324 B) llama a @0xb78440 (168 B) que contiene los inmediatos FP 0.5 y 1 y `ListBase.contains` (comprobación de pertenencia a una lista de grupos). La misma rutina @0xb78440 se usa al calcular volumen. |
| El volumen se calcula como par (resistencia, peso corporal) y el peso corporal entra escalado por ejercicio. | OBSERVADO (tipos y cadenas) / INFERIDO (escalado) | Tuplas `(Weight, Weight)` en todas las closures de volumen; rutina @0xc8a3bc con la cadena 'scaledBodyweight' y `Weight.*`; etiquetas 'Resistance' / 'Bodyweight' / 'Total' en el gráfico apilado (`StackedColumnSeries`). |
| Los datos se agrupan por fecha (cabeceras por mes, filas por entrenamiento) y se muestran promedio y total («Average/Total Sets», «Average/Total Volume»). | OBSERVADO | `DateFormat('yMMMM')` @0xb6473c, `DateFormat('MMMEd')` @0xb649cc, tiles `DashboardDataGraphTile` con esas cadenas (en/ja/de). La ventana temporal concreta la controla `DataExperienceGraphSettings` (clave de preferencia 'muscle-group:' + 'chartSettings'); el valor por defecto es DESCONOCIDO. |
| Sólo cuentan las series «activas» del entrenamiento. | INFERIDO | `Workout.activeSetsByExerciseId` / `activeExerciseDetailIds` (campos con inicializador) usados por la vista; qué excluye (calentamiento, saltadas) no se ha verificado. |
| Existen además vistas de «niveles musculares» y una sección de grupos musculares en el panel de la app de entrenamiento. | OBSERVADO (nombres) | `ui/workout/muscle_levels_view.dart`, `sbs_train_app/dashboard/definitions/train_muscle_groups_section_definition.dart`, `widgets/muscle_group_tiles_section.dart`, `colored_muscle_svg.dart`. Contenido: DESCONOCIDO. |

### C.2.2 Próximo objetivo (Smart Progression / auto-targets)

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| El motor de progresión es `StrengthService` y trabaja con curvas x-RM (`XRmCurve`) construidas a partir de series simplificadas (`SimpleSet`, `SimpleWorkout`) y objetivos (`SimpleTarget`). | OBSERVADO (clases, firmas) | Funciones con nombre en `strength_service.dart` (ver abajo). |
| Función de «reps fatigantes esperadas» a partir de reps y RIR, con coeficiente −0,327. | OBSERVADO (firma y constantes) / DESCONOCIDO (forma funcional) | `getExpectedFatiguingReps({double reps, int rir}) -> double` @0xc811c0 (960 B): pool 0.0 y −0.327, multiplicación `_Double.*`. También inlined en `getProgressionSet` (@0xc7fcac, 3976 B). |
| Fatiga acumulada por sesión. | OBSERVADO (firma) | `getCumulativeFatigue(double) -> double` @0xc80fe8 (472 B). |
| La curva x-RM de un ejercicio combina información con pesos 0,8/0,2 y distingue tren superior. | OBSERVADO (constantes) / INFERIDO (semántica) | `getSetAutoProgressionCurve() -> XRmCurve?` @0xc80c34 → cuerpo @0xb6b3d8 (1160 B) con `RegionTrained.upperBody`, 0.8, 0.2, `Weight./`, `Weight.*`. |
| Actualización de la curva entrenamiento a entrenamiento con factor 0,2. | INFERIDO | Closure de *fold* `((List<XRmCurve?>, XRmCurve?, XRmCurve?), SimpleWorkout)` @0xb684b0 → @0xb685b8 (2312 B) con 0.2 y 1.0. |
| Ponderación/penalización de series históricas con ±25 %. | INFERIDO | Closures @0xb69c90 (−0.25, 0.75, 0.25, 1.25) y @0xb69ed4 (−0.25, 0.25, −∞) sobre tuplas `(XRmCurve, num?, Weight, Weight, num?, SimpleSet)`. |
| El objetivo se calcula generando candidatos (reps, peso) con un paso de incremento y eligiendo por un *ranking* de distancia. | INFERIDO | `getAutoTargetTotalRepsAndWeight({bool checkRepRange, String? groupId}) -> (int, Weight)?` @0xc7eb60 (2844 B) usa un `Range` con parámetros `inclusiveMax`, `step` y `Range._decimalPlaces` (@0xc06910) y la función `getRanking(int, Weight) -> double` @0xc7fa78 (usa `Weight.abs`). `getRepsWeight(int) -> Weight` @0xc7fc24 devuelve el peso para n reps. |
| El objetivo puede «fijarse» a los incrementos de un grupo de resistencia concreto del gimnasio. | OBSERVADO (firma) | `getAutoTargetProgressionSet((int, Weight), {List<String> fixResistanceGroupIds, bool isFix})` @0xc7e898. Devuelve `(double, double, double, double, XRmCurve, (…), FixableAutoTarget?)`. |
| Los objetivos auto de una sesión se construyen por serie a partir del primer registro previo y de la serie objetivo; las series a fallo se tratan aparte. | INFERIDO | `WorkoutSessionExerciseContext.init:fixableAutoTargets` @0xc7d69c (1196 B) usa `ExerciseMetric.assistanceWeight`, `SimpleSet`/`SimpleTarget`; closure `(WorkoutSessionSet) -> SimpleTarget?` @0xc84bac referencia `ExerciseSetType.failure`. |
| Preferencias del usuario: relleno inicial (`smartProgression` / `previous`), métrica de foco (`previous` / `auto`, clave de preferencias 'focus_metric'), referencia de «anterior» (`anyWorkout` / `withinProgram`). | OBSERVADO | Enums y `SmartProgressionSettingsView` (C2_wk). |
| En iOS/watch, el motor nativo recibe `AutofillProgression`, `LogAutoFill` y una estimación de dirección (`VerticalDirectionEstimate {unknown, same, above, below, aboveOrBelow}`). | OBSERVADO (DTOs) / INFERIDO (uso) | `services/workout/dtos/flutter_workout_context_dto.dart`, `swift_dtos.dart`. |

### C.2.3 Deload y periodización

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| Un programa tiene `numCycles` (en semanas o ciclos), puede ser periodizado y puede tener descarga en el primer o en el último ciclo. | OBSERVADO | Campos de `WorkoutProgram.copyWith`; `CycleType`; `WorkoutDeload {firstCycle, lastCycle}`; `Periodization` con campo `deload`. |
| Para cada ejercicio del día se deriva un `CycleTarget` de descarga a partir del objetivo normal, con parámetros `deload` y `minCycleIndex`. | OBSERVADO (nombres de argumentos) / INFERIDO (derivación) | Closure `(int, WorkoutProgramDayExercise) -> WorkoutProgramDayExercise` @0xd31480 (736 B) con descriptor de argumentos `deload`, `minCycleIndex`; rutina @0xc91fd4 (732 B) con las mismas cadenas; closures `(WorkoutDeload) -> CycleTarget` @0xd31760 → @0xc15cf8 (588 B) y en `WorkoutProgramDay`. |
| Factor de reducción de la descarga (series, reps o RIR). | DESCONOCIDO | El cuerpo @0xc15cf8 no contiene constantes FP visibles; sin desensamblado completo no se puede afirmar. |
| Progresión del objetivo por ciclo (periodización) como lista de `CycleTarget` por ciclo. | OBSERVADO | `PeriodizedPeriodization(values, deload)` vs `SimplePeriodization(value, deload)`. |

### C.2.4 Calentamiento

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| Tres modos: inteligente, por esquema o ninguno. | OBSERVADO | `WarmUpAutomation {smart, scheme, none}`; fuentes `SmartWarmUpSource` / `SchemeWarmUpSource`; `WarmUpScheme` / `WarmUpSchemeSet` serializables. |
| El calentamiento inteligente usa escalones al 40 %, 60 %, 80 % y 95 % del peso de trabajo, recortados según la intensidad relativa (`oneRepMaxRatio`). | OBSERVADO (lista const) / INFERIDO (recorte) | `getSmartWarmUpRatios({double oneRepMaxRatio}) -> List<double>` @0xc8c454 (664 B): pool con la lista `[0.4, 0.6, 0.8, 0.95]` y comparaciones con 0 y 1. |
| Cada escalón genera un objetivo `(ratio, AutoTarget)`. | OBSERVADO (tipo) | `smartWarmUpTargets: List<(double, AutoTarget)>`; closure `(double, Weight) -> (double, AutoTarget)?` @0xc8c030 (inmediatos 1 y 3). |
| Se reduce u omite el calentamiento si ya se trabajó el agonista primario o el mismo ejercicio en la sesión. | INFERIDO | Campos `hasWorkedOutPrimaryAgonists`, `hasDoneSameExercise` en el contexto del ejercicio. |
| El peso de cada escalón se redondea al peso realizable con el equipamiento del gimnasio (incluye discos *bumper*). | INFERIDO | `getClosestWarmUpWeight`; closures con `EquipmentWeight`, cadena 'shouldUseBumperPlates' (@0xc84b0c), `PlateLoadWeightCache`. |
| En la estimación de duración del programa, el calentamiento cuenta 120 s por ejercicio salvo con `WarmUpAutomation.none`. | OBSERVADO (constantes) / INFERIDO (significado) | Rutina @0xd19408: `WarmUpAutomation.none` → `Duration(0)`, si no `Duration` de 120 000 000 µs. |

### C.2.5 Creación automática de programas (`WorkoutProgramService`)

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| Entradas: *split* (13 opciones con días/semana), objetivo (hipertrofia/fuerza/ambos), periodización (4), plantillas de ejercicio primario/secundario/accesorio, equipamiento del gimnasio, configuración del usuario y autoevaluaciones. | OBSERVADO (enums y dependencias) | Enums de `program_creation/*`; campos `exerciseDatabase`, `equipmentDatabase`; closures de `getBestMatch` que leen `equipmentDatabase` (@0xd211e8) y `_UserExerciseConfig` (@0xc7ca5c). |
| Algunos ejercicios sólo se eligen si el usuario supera una autoevaluación (5/10 dominadas, 10 fondos, …). | OBSERVADO (enum e ids) / INFERIDO (regla) | Closure `(Exercise) -> bool` @0xd21b94 (2576 B) con ~40 ids de ejercicio y `ExerciseAssessment.pullUps5/pullUps10/dips10`. |
| Para objetivo fuerza/ambos se prefiere `primaryCompound`; para accesorios se usan `isolationAccessory`. | OBSERVADO (pool) / INFERIDO (regla) | Closures de `getBestMatch` @0xd21808 (ProgramGoal.both/strength + primaryCompound), @0xd21ac0/@0xd21b0c (isolationAccessory). |
| El reparto de ejercicios entre días usa `fits`, `score`, `roundRobin`, `spillover` y `distributeTemplates`. | OBSERVADO (nombres y firmas) / INFERIDO (semántica) | `::.fits((MuscleGroup?, Exercise, int, ExerciseTemplateType), SplitDay) -> bool` @0xd17be0; `::.score(List<…>) -> (int, int)` @0xd17c54 (penaliza combinaciones; closure @0xd17e30 referencia hamstrings/quads/lats); `::.roundRobin(Iterable<List<…>>)` @0xd1c744; `WorkoutProgramService.spillover()` @0xd1cb14 (1656 B) con 1.25 y comparación con 100 (INFERIDO: tolerancia del 125 % de tiempo y tope de 100 iteraciones); `distributeTemplates(ExerciseTemplateType)` @0xd1d65c. |
| Los accesorios de aislamiento se asignan por grupo muscular y categoría (superior/inferior) con presupuesto de tiempo. | OBSERVADO (firmas) | `getIsolationTemplates({TimeCalcAllocation, MuscleGroupCategory})`, `getIsolationMuscleGroups`, `getAccessoryFor` (closures con forearms/neck/lats/hamstrings/quads). |
| Tiempo estimado por ejercicio según categoría: 90 s o 180 s (compuestos vs aislamiento/core) más 120 s de descanso. | OBSERVADO (constantes) / INFERIDO (asignación exacta) | Rutina @0xd19364 (164 B) con `ExerciseCategory` × `Duration` 90 000 000 / 180 000 000 µs; rutina @0xc8efe4 con 120 000 000 µs. |
| Cada ejercicio recibe de 1 a 6 series y rangos `minFullReps`/`maxFullReps`/`rir` según objetivo y tipo de plantilla; con periodización estática se genera `SimplePeriodization`, si no `PeriodizedPeriodization`. | OBSERVADO (cadenas, enums) / INFERIDO (reglas numéricas) | Closure constructor de día @0xd1a368 (4556 B): pool 'numSets', 'Expected between 1 and 6 sets', 'simple', 'periodized', `ProgramPeriodization.static`, `ProgramGoal.*`, `ExerciseTemplateType.*`, `MovementPattern.horizontalPull/verticalPull`; `::.target(int) -> CycleTarget` @0xd1bbd8 con `ExerciseTargetLog` (`maxFullReps`, `minFullReps`, `rir`) y `ExerciseSetType.standard`. Los valores concretos de reps/RIR por objetivo no se ven. |

### C.2.6 Calculadora de discos y equipamiento

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| La disposición de discos se resuelve con una búsqueda en profundidad que minimiza la diferencia absoluta con el objetivo. | OBSERVADO (nombres) / INFERIDO (criterio) | `BarPlateArrangement.dfs(int, Weight, int, Weight, int)` @0xc88fb0 (1820 B); `considerCandidate(Weight, Weight, int)` @0xc89d18 usa `Weight.abs`; clases `_BestCandidate`, `_PlateSearchInputs`; centinela 1 073 741 824. |
| Se distingue carga por lado o por un solo lado. | OBSERVADO | `PlateCalculatorLaterality {perSide, singleSide}`. |
| Hay una caché de pesos alcanzables por grupo de resistencia. | OBSERVADO (clase) / INFERIDO (contenido) | `PlateLoadWeightCache` (closure @0xc8281c, 2716 B, con dos ids de equipamiento const y `addWeights`; closure `(Weight) -> int` @0xc83bcc con FP 30). |
| Configuración de máquinas: rangos de peso, lista de pesos, micro-ajustes (dial, pines, discos externos) y etiquetas de resistencia para bandas. | OBSERVADO | `EquipmentWeightConfig` (sellada), `EquipmentWeightRange`, `EquipmentMicroAdjustmentConfig`, `MicroAdjustmentType`, `ResistanceLabel`. |

### C.2.7 Otros

| Afirmación | Nivel | Cómo se obtuvo |
|---|---|---|
| Descanso por defecto 120 s por registro; indicadores de último calentamiento y descanso entre lados. | OBSERVADO | `WorkoutLog` JSON (`restTimer` → `Duration` 120 000 000 µs); cadenas 'isLastWarmUpSet', 'isRestBetweenSplit' con `ExerciseSetType.warmUp/drop/myo` (@l.5714). |
| El cambio hecho durante la sesión se puede volcar al programa mediante un diff de plan. | INFERIDO | `PlanDiff`/`PlanDelta` con añadidos, eliminados, modificados y origen del orden (`source`/`update`). |
| La app de Workouts tiene un motor de sesión nativo en Swift (watch / Live Activities) con DTOs espejo. | OBSERVADO (clases) / INFERIDO (uso) | `FlutterSwiftChannel`, `FlutterWorkoutChannel` (`getActiveWorkoutStateAndContext`, `startNextWorkout`, `deleteSavedWorkout`), `FlutterActivityKitChannel`, DTOs `MFWorkout`, `Plan`, `Block`, `Segment`, `SmartWarmup`, `SetSpecialization {drop, failure, myo, warmup, warmupAuto}`, `HeartRateZones` (`Algorithm {manual, karvonen}`), `Exit` (por tiempo o por FC), `SensorHistory`. |
| Importador de programas de hojas de cálculo con parsers específicos. | OBSERVADO | `ProgramImportParser` y variantes (`Default`, 6 de Jeff Nippard, 2 «Sho»/EVLT japonesas), listas const de nombres aprobados (6), correspondencias de ejercicios (42) y de RIR (259). No se reproducen. |
| Búsqueda de ejercicios por palabras clave en inglés y japonés con sinónimos. | OBSERVADO | Campos de `Exercise`, `exerciseSynonyms`, `NameLocale`. |

## C.3 Lo que NO se pudo determinar

- Forma funcional de la curva x-RM, de las reps fatigantes (−0,327) y del *ranking* de candidatos.
- Magnitud de la descarga.
- Valores de reps/RIR/series por objetivo en la creación de programas.
- Ventana temporal por defecto del panel de grupos musculares.
- Campos de `ExerciseUiState` y de la mayoría de modelos (ranuras sin nombre).
