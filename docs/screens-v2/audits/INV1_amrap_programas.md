# INV1 · AMRAP, test de 1RM y Training Max en el catálogo (inventario de código, 2026-10-20)

Lectura de código en `C:\kw\u11` (sin compilar ni ejecutar). Prefijo `K` = `android-native\app\src\main\java\com\example\kpkn\`. Alias:

| Alias | Ruta | Alias | Ruta |
|---|---|---|---|
| PPC | K`data\programs\PersonalizedPlanCatalog.kt` | PAE | K`domain\training\ProgramAutoregulationEngine.kt` |
| PT | K`data\programs\ProgramTemplates.kt` | APE | K`domain\training\AuthoredProgressionEngine.kt` |
| KAPR | K`data\programs\KpknAdvancedProgramRecipes.kt` | PPE | K`domain\training\ProgramProgressEngine.kt` |
| PET | K`data\programs\PlanEditorialTable.kt` | BTE | K`domain\training\BlockTransitionEngine.kt` |
| PL | K`data\protocols\ProtocolLibrary.kt` | BPE | K`domain\training\BlockProgressionEngine.kt` |
| TPR | K`data\protocols\TrainingPlanRecipe.kt` | PM | K`domain\training\PlanMaterializer.kt` |
| RB | K`data\protocols\RecipeBuilders.kt` | TMR / TMM | K`domain\training\TrainingMaxResolver.kt` / `TrainingMaxMerge.kt` |
| DA | K`data\protocols\DayArchetypes.kt` | RCP | K`domain\training\RecipeContractPolicy.kt` |
| TW | K`data\protocols\definitions\TexasWendlerProtocols.kt` | NWPR | K`domain\training\NativeWorkoutProgressionRuntime.kt` |
| MNG | K`data\protocols\definitions\MadcowNsunsGzcl.kt` | CT | K`domain\training\CompositionTaxonomy.kt` |
| JSS | K`data\protocols\definitions\JuggernautSheikoSmolov.kt` | ESO | K`domain\training\ExerciseSwapOptions.kt` |
| CPL | K`data\protocols\definitions\ClassicPlProtocols.kt` | TO | K`domain\training\TrainingOptions.kt` |
| BNP | K`data\protocols\definitions\BodybuildingNativeProtocols.kt` | IMP | K`domain\training\importer\ProgramImportParser.kt` |
| SBD4 | K`data\protocols\definitions\KpknNativeSbd4.kt` | PRG | K`data\repository\ProgramRepository.kt` |
| APP | K`data\protocols\definitions\AuthoredPhulPhat.kt` | PGM / PSM | K`data\models\Program.kt` / `ProgramScheduleModels.kt` |
| CIDS | K`data\protocols\CatalogIds.kt` | PLP | K`data\models\PowerliftingProfile.kt` |
| SCP | K`domain\training\SimpleCyclePersonalizer.kt` | SES / WL | K`data\models\Session.kt` / `WorkoutLog.kt` |
| PO | K`domain\relator\PlanObserver.kt` | EMI | K`data\models\ExerciseMuscleInfo.kt` |
| PDS / PDSum | K`screens\programdetail\ProgramDetailScreen.kt` / `PlanDetailsSummary.kt` | WSR | K`screens\workout\WorkoutSetRecorder.kt` |
| WFM | K`screens\workout\WorkoutFeedbackModels.kt` | SEC | K`screens\workout\components\SetExecutionCard.kt` |
| WV2 / WUM / WVM | K`screens\workout\WorkoutV2Body.kt` / `WorkoutUiModels.kt` / `WorkoutViewModel.kt` | WLSC | K`screens\workout\WorkoutLoadSuggestionController.kt` |
| LSE | K`domain\workout\LoadSuggestionEngine.kt` | WPHE | K`domain\workout\WorkoutPerformanceHomologationEngine.kt` |
| WCE | K`domain\workout\WarmupCalibrationEngine.kt` | CALC | K`domain\calculations\Calculations.kt` |
| ISR | K`screens\sessioneditor\components\InlineSetRow.kt` | SEVS / SESH / SERE / SEVN | K`screens\sessioneditor\SessionEditorViewModelSets.kt` / `SessionEditorSessionHelpers.kt` / `SessionEditorRulesEngine.kt` / `SessionEditorViewModelNavigation.kt` |
| EED | K`screens\sessioneditor\components\ExerciseEditorDialogs.kt` | ERM | K`domain\exercises\catalogv2\ExerciseCatalogV2RichMetadata.kt` |
| ELA | K`data\exercises\catalogv2\ExerciseCatalogV2LegacyAdapter.kt` | ASE / AO | K`domain\exercises\catalogv2\AprendeSimilarityEngine.kt` / `AprendeOntology.kt` |
| EID / EMR | K`domain\exercises\ExerciseIdentity.kt` / `ExerciseMuscleResolver.kt` | CAT | `android-native\app\src\main\assets\exercise_catalog_v2.json` |

## Resumen

- Una serie AMRAP o «+» se marca solo con `SetRecipe.amrap` (`TPR:135`), que llega a `ExerciseSet.isAmrap` con `IntensityMode.AMRAP`.
- **No hay series calibradoras.** `ExerciseSet.isCalibrator` (`SES:615`) no lo pone a `true` ningún código; solo lo lee una etiqueta del editor (`ISR:619, 821, 830, 1166-1176`). `Exercise.isCalibratorAmrap` (`SES:319`) es un campo muerto.
- El TM solo existe para SQUAT, BENCH, DEADLIFT y OVERHEAD, dentro de `PowerliftingProfile` (`PLP:18-35`). No hay TM ni calibración por ejercicio, patrón o músculo.
- El resultado de un AMRAP solo **sube** el TM en nSuns (`AmrapDrivenTm`) y en SBS RTF (`RepTargetDrivenTm`). En el resto solo baja un 2,5 %, congela la subida o no hace nada.

## 1. Programas

**Cómo se arma el catálogo.**
- `PPC:537-584` reúne 8 nativos históricos, 4 perfiles propios, 10 generados con 20 variantes, las plantillas (`PT:35-255`), los protocolos visibles y PHUL/PHAT autorados.
- Los protocolos visibles están en `PL:357-388` y `PL:397-405`.
- El índice legado (`PL:101-355`: `531-base`, `juggernaut-base`, `nsuns-531`, `sbs-hybrid`, etc.) está oculto (`PL:78-80`).

**Marcador AMRAP.** Además del campo directo, lo ponen:
- `percentSets(amrapLast)` y `repeatPercentSets(amrapLast)` (`RB:32-63`);
- `DayArchetypes.plX(t1Amrap)` (`DA:19/31, 56/67, 86/98`).

`ProtocolFidelitySpec.requiresAmrap` (`TPR:408`) no tiene consumidor.

**«Solo baja / congela»** significa: un AMRAP corto baja el TM un −2,5 % (`PAE:1096`) y además congela la subida de ciclo o bloque de ese levantamiento (`PPE:1266-1286`, `APE:169-174`). Un AMRAP bueno nunca sube el TM.

| id | Nombre | AMRAP / «+» | Test de 1RM | Regla del TM | Efecto del AMRAP |
|---|---|---|---|---|---|
| `protocol:texas-method-3d` / `-4d` | Texas Method | No | Top set 1×5 con `isTopSet` (`TW:82-83, 282`) | TM 0,87; `TopSetPr(1,25/2,5)` (`TW:102, 308`); `WEEKLY_REVIEW` | No aplica; el top set mueve el TM (`PAE:1129-1139`) |
| `protocol:wendler-531-bbb` / `-fsl` | 5/3/1 BBB / FSL | Última serie del T1, semanas 1-3 (`TW:150-154`) | No | TM 0,90; `CycleIncrement(2,5/5, CYCLE)` (`TW:240`); `AMRAP_TM` (`TW:243`) | Solo baja / congela |
| `protocol:madcow-5x5` | Madcow | No (triple del viernes con `isTopSet`, `MNG:69-71`) | No | TM 0,87; `CycleIncrement` | No aplica; el top set no se lee |
| `protocol:nsuns-531-lp-4d` | nSuns LP | 1+ al 95 % en SQ, BP del día 2 y DL (`MNG:132-135`); BP del día 1 y OHP sin AMRAP | No | TM 0,90; `AmrapDrivenTm()` (`MNG:182`); `AMRAP_TM` | **Sube**: tabla en kg si la serie va al ≥ 85 % del TM (`PAE:1110-1120`); corto, −2,5 % |
| `protocol:gzclp` | GZCLP | Última serie del T1 5×3+ (`MNG:254-261`) | No | TM 0,90; `CycleIncrement`; `AMRAP_TM` | Solo baja / congela |
| `protocol:gzcl-jt-2` | J&T 2.0 | RM semanal del T1 (`MNG:356-358`) | 1×1 al 100 % en semanas 6 y 12 | `RepMaxAutoregulated` (`MNG:372`); `AMRAP_TM` | Solo baja; `RepMax` nunca se dispara |
| `protocol:gzcl-rippler` | Rippler | Última serie del T1, semanas 1-10 (`MNG:408-411`) | Semanas 11-12 | `None` | No (H-05, `PAE:1061-1063`) |
| `protocol:juggernaut-2` | Juggernaut 2.0 | Última serie de la 3.ª semana de cada ola, 10+/8+/5+/3+ (`JSS:111, 120`) | No | TM 0,90; `CycleIncrement(2,5/5, BLOCK)` (`JSS:161`); `AMRAP_TM` | Solo baja / congela. **No aplica «reps sobre el estándar»**, aunque la descripción lo promete (`JSS:180`) |
| `protocol:sheiko-29-32`, `smolov`, `smolov-jr`, `candito-6`, `coan-phillipi-dl`, `korte-3x3`, `westside-conjugate`, `calgary-16`, `tsa-9`, `kpkn-native-sbd-4` | — | No | Varios (singles o test) | `None`, `WeeklyKg` o `WeeklyPercent` | — |
| `protocol:cube-method` | Cube | 1 AMRAP en el día de reps de DL, semanas 1-9 (`CPL:113-118`) | Semana 10 | TM 0,95; `None` | No |
| `protocol:lilliebridge` | Lilliebridge | Banca pesada en semanas pares (`CPL:200`) | Singles | `None` | No |
| `protocol:kpkn-sbs-rtf` | SBS RTF | `t1Amrap` en SQ, BP pesada y DL; sin AMRAP en la descarga de la semana 4 (`BNP:373-382`) | No | TM 0,90; `RepTargetDrivenTm()` (`BNP:388`); `AMRAP_TM` | **Sube**: +0,5 %/rep extra; −1 %/rep si faltan 2 o más (`PAE:1089-1102`) |
| `protocol:kpkn-rts-style` | RTS-style | No (top set por RPE, `BNP:300-302`) | No | `None`; `RPE_CAP` | — |
| `template:power-12-3` / `16-4` / `20-5` | PL 12/16/20 semanas | `t1Amrap` en semanas sueltas (`KAPR:75, 108, 129, 163, 188, 195`); bandera muerta cuando `peakDay` reemplaza el T1 | `testDay` en las semanas 16 y 20 | `None` | No |
| `native:*`, `generated:*`, `template:simple-*`, PHUL/PHAT, `body-*`, `powerbuild-*` | — | No | No | `None` o progresión nativa | — |

**Test de 1RM.** El flujo es:
1. Al cerrar un bloque PEAK o REALIZATION, o el TAPER o DELOAD que le sigue, sin competición por delante, `BTE:150-179` propone el test.
2. Eso abre `CONFIRM_1RM_TEST` (`PPE:980-998`).
3. El atleta escribe a mano el 1RM de S, B y D (`PPE:390-394`).
4. `TMM:25-58` fusiona los valores.

Las series «Test» de la sesión no se leen.

## 2. Progresión de recetas

- **Receta.** `TrainingPlanRecipe` tiene `trainingMaxPercent`, `progression`, `autoregulationHooks` y `nativeProgression` (`TPR:452-476`). Bases de carga: `LoadBasis` (`TPR:24-31`); referencias: `PlanLoadReference` (`TPR:35-87`).
- **Reglas de progresión.** `ProgressionRule` (`TPR:331-393`) admite:
  - `None`;
  - `CycleIncrement`;
  - `AmrapDrivenTm` (tabla en kg: 0-1 → 0; 2-3 → 2,5; 4-5 → 5; 6+ → 7,5);
  - `WeeklyPercent` y `WeeklyKg`;
  - `RepTargetDrivenTm` (0,5 / 1,0 / 2);
  - `RepMaxAutoregulated`;
  - `TopSetPr` (1,25 / 2,5).
- **Ganchos de autorregulación.** `AMRAP_TM`, `RPE_CAP` y `WEEKLY_REVIEW` (`TPR:396`). Solo `AMRAP_TM` tiene consumidor.
- **TM.** Se guarda en `PLP:18-35`. `TMR:17-33` lo resuelve así: TM guardado, y si no hay, 1RM × porcentaje de la receta. `PM:1054-1062` toma el TM de cada slot por `slot.lift.liftSlot`.
- **Modos OFF / PROPOSE / AUTO.**
  - `PGM:216`; por defecto OFF en `Program` (`PGM:65`) y PROPOSE en `TrainingOptions` (`TO:59`).
  - La evaluación está en `PAE:148-176` y la aplicación en `PAE:178-257`.
  - PROPOSE crea `PendingProgramAction(CONFIRM_AUTOREGULATION)`.
- **Dos vías de progresión.**
  1. El método del autor (`CycleIncrement`, `WeeklyKg`) siempre se aplica e ignora el modo (`APE:37-53, 99-108, 236-318`; `PPE:1081-1092, 1261-1286`; `PM:1325-1342`).
  2. La de rendimiento (`ADJUST_TM`: `AmrapDrivenTm`, `RepTargetDrivenTm`, `TopSetPr`, `RepMaxAutoregulated`, `PROMOTE_TM`) respeta el modo (`PAE:1009-1042, 964-976`).
- **Lectura del AMRAP.**
  1. En vivo, `WSR:199-203` con `resolveAmrapActive` (`WFM:54-61`), y se guarda en `CompletedSet` (`WSR:441-477`, `WL:162-164`).
  2. Señales semanales: `PRG:1107-1143` → `PAE.collectAmrapHits` (`PAE:553-586`), que empareja también por posición (`PAE:749-774`).
  3. Levantamiento del hit: `RecipeLiftResolver` (`PAE:1266-1289`), con respaldo por texto (`PAE:1244-1254`).
  4. Cambio de TM: `amrapMovesTm` (`PAE:1061-1063`), `amrapTmChange` (`PAE:1085-1107`), y tabla solo con la serie al ≥ 85 % del TM (`PAE:100, 1110-1120`).
  5. Al cerrar ciclo o bloque: `shortAmrapLifts` (`PAE:677-687`).
- **Mutaciones.** `applyMutations` (`PAE:812-921`): primero `applyTmKgDelta` / `applyTmDelta` (`PAE:1194-1225`), después `PM.rematerializeWeek` (`PAE:872-909`).
- **BTE / BPE.** `BTE:108-222` contiene la puerta de 1RM y la descarga, que pone `isAmrap = false` e `isCalibrator = false` (`BTE:305-306`). `BPE:66-119` no usa TM ni AMRAP.
- **Textos al usuario.** `PO:97-136` y `PDS:1133-1155`.

## 3. Calibrador: campos y consumidores

| Consumidor | ¿Lee `isCalibrator`? | ¿Lee AMRAP? |
|---|---|---|
| `InlineSetRow` | Sí, como etiqueta | Sí (`ISR:183`) |
| `PM` (pendientes manuales) | Lo excluye (`PM:1145`) | Lo excluye |
| `NWPR` (doble progresión) | Lo excluye (`NWPR:1007, 1318, 1381, 1534, 1560`) | Lo excluye |
| `PDSum` | Lo excluye (`PDSum:260`) | — |
| `WorkoutSetRecorder` | No | Sí; además genera `amrapCalibrationMessage` (`WSR:983-999`) |
| `SetExecutionCard` | No | Sí (`SEC:932-934`, hoja `SEC:135-152`). Recibe el mensaje (`SEC:852`, `WV2:2194`, `WUM:267`) pero **no lo pinta** |
| `WPHE` | No | Solo `amrapOverride`: +2,5 % al e1RM con más de 3 reps (`WPHE:550`, `CALC:60-71`) |
| `WLSC.computeSessionImprovementAdjustment` | No | No (`WLSC:681-715`) |
| `PAE` | No | Sí (`PAE:569`) |

## 4. Datos para relacionar ejercicios

**Catálogo `CAT`** (`v2-approved-2026-09-29-a`): 96 familias, 228 definiciones, 553 configuraciones, 63 `movementPatternId` y 48 `replacementGroup`.
- Familia: `taxonomy` = `[región, patrón, subpatrón]`.
- `profile`: `movementPatternId`, `primaryMuscles`, `secondaryMuscles` y `stabilizerMuscles` (solo ids, **sin pesos**), `jointInvolvement`, `replacementGroup`, `replacementPriority`, `fatigueTier`, `performanceProfileId`, `axialLoadFactor`, `efc`, `cnc` y `richMetadata`.
- `richMetadata`: `identity.familyId`, `programming.role` y `replacement.preservesIntent` (`"<patrón>:<músculo primario>"`).

**Kotlin.**
- `ERM:14-108` y `ELA:40-111` (no guarda `familyId`; `volumeContribution = null` en `ELA:52`).
- Pesos de respaldo por rol (`EMI:223-247`): PRIMARY 1,0; SECONDARY 0,5; STABILIZER 0,4; NEUTRALIZER 0,1. Hay modificadores (`EMI:197-204`).
- `EMR:11-74` resuelve los músculos efectivos.
- `ASE:10-131` define bandas: EQUIVALENT (mismo `replacementGroup` o `preservesIntent`), PATTERN_VARIANT y ANATOMICAL_TRANSFER, con puntos 12/8/5/6. Solo lo usa `ESO:48-107`.
- `CT:3-163`: 19 familias de patrón y su mapa; `ExerciseCompositionMetadata` en `CT:25-45`.
- `AO:66-130`.
- `EID:80-132`. `Exercise.exerciseFamilyId` es el id de la **definición** (`PM:1207-1213`), no `identity.familyId`.
- **Transferencias existentes.**
  - Por receta, `SlotRecipe.lift.liftSlot`: T2 de GZCLP (`MNG:281-286`), inclinada de Madcow → BENCH (`MNG:63`), Juggernaut OHP → BENCH (`JSS:132`).
  - Por texto, `PAE:787-798` y `PAE:1232-1254`.

**Ids de los básicos** (`CIDS`):

| Ejercicios | Patrón | `replacementGroup` |
|---|---|---|
| Sentadillas | `knee_dominant` | `quad_squat` |
| Sentadilla al cajón | `knee_hip_dominant` | — |
| Banca e inclinada | `horizontal_push` | `chest_press_flat` |
| Peso muerto convencional y sumo | `deadlift` | `ham_deadlift` |
| Rumano | `romanian_deadlift` | `ham_rdl` |
| Press militar | `vertical_push` | `shoulder_press_standing` |

## 5. Carga inteligente en vivo

1. Entrada: `WLSC.getWeightSuggestionWithAutoRegulation` (`WLSC:141-185`), llamada desde `WV2:1921, 2021` y `WVM:640, 731, 904, 1012, 3983`. No se usa con progresión nativa (`LSE:17-25`).
2. Sugerencia contextual primero (`WLSC:218-241`): `buildLoadSuggestionForSet` (`WLSC:506-609`).
   - Base, en este orden: override; primer peso real de la sesión; plan (`CALC:259-308`; con `PERCENT_TM` usa `reference1RM` como TM, `CALC:253-257`); historial (`WLSC:611-653`).
   - `improvementFactor = computeSessionImprovementAdjustment` (`WLSC:681-715`), entre 0,92 y 1,05, y **1,0 si la serie tiene carga planificada** (`WLSC:539-545, 655-658`).
   - `fatigueFactor` 1,0 / 0,8 / 0,6 / 0,5 (`LSE:38-44`).
   - Núcleo: `LSE.computeContextualWorkingLoad` (`LSE:214-274`).
3. Si la contextual es nula, ruta alternativa (`WVM:7240-7330`, `LSE:108-201`). Aplica sesión 1,0-1,08, readiness por sistema y músculo (`WLSC:360-370`, `WFM:441-476`), autorregulación por serie (`WLSC:381-388`), `rmsAdjustedFactor` y sueño, con tope del +12 % (`WLSC:418-426`).
4. Postproceso: suelo por etiqueta (`WLSC:187-209`) y `WarmupCalibrationEngine` (`WLSC:174-184`, `WCE:41-80`).
5. «Carga inteligente» del editor (`EED:110-180`) solo edita `reference1RM` y `prFor1RM`.

**Puntos de enganche para un factor de calibración.**
- Tras registrar el AMRAP: `WSR:983-999`.
- Postproceso común de las dos rutas en vivo: `WLSC:171-184`.
- Ruta contextual: `WLSC:541-546` y `LSE:214-274`.
- Precedente de factor por músculo: `WLSC:360-370`.
- Propuestas de TM: `PAE:1009-1042` y `PAE:812-921`.
- Señal semanal: `PRG:1107-1143`.
- Relaciones entre ejercicios: `ASE`, `CT.familyOf`, `preservesIntent`, `replacementGroup` y `primaryMuscles`.

## 6. Huecos y contradicciones

1. El calibrador no tiene escritor ni lector en vivo; `isCalibratorAmrap` está muerto.
2. `amrapCalibrationMessage` compara toneladas con un e1RM y no se pinta.
3. El AMRAP casi nunca sube el TM; en un AMRAP corto de 5/3/1, GZCLP, Juggernaut y J&T se aplican a la vez el −2,5 % y la congelación. La descripción de Juggernaut promete más de lo que hace.
4. La tabla en kg de nSuns no distingue tren superior e inferior. El OHP de nSuns nunca progresa.
5. `RepMaxAutoregulated` de J&T nunca se dispara (`PAE:652-654`).
6. Las banderas AMRAP de `power-16-4` (semana 11) y `power-20-5` (semana 16) están muertas.
7. El test de 1RM siempre exige S, B y D: Smolov, con solo sentadilla, los pedirá. No hay campo de OHP (`PSM:128-135`). Las series «Test» no se leen.
8. `PROMOTE_TM` compara el e1RM con TM × 1,05 y se dispara en cuanto el e1RM supera el 94,5 % del 1RM. El levantamiento se deduce por texto.
9. `reference1RM` guarda el TM en series `PERCENT_TM` (`PM:1059-1061, 1220-1225`) y se usa a veces como 1RM y a veces como TM (`WLSC:691-699`, `CALC:253-257`). El diálogo del editor lo sobrescribe (`EED:156, 171`).
10. Al guardar, el editor puede borrar los AMRAP de las recetas: `PM:1216` (RM) → `SEVN:385-386` → `SESH:450-458, 474-479, 598-604`, y `SERE:384-391`. Pendiente de verificar con pruebas.
11. El importador reconoce «5+» pero solo lo guarda como nota (`IMP:1236-1237, 557-558, 590, 1372`).
12. `RPE_CAP`, `WEEKLY_REVIEW` y `requiresAmrap` no tienen consumidor.
13. Hay dos tuberías de carga en vivo: readiness, sueño y autorregulación por serie solo actúan si la contextual es nula. Además, por lectura de código, la contextual pondría las series a partir de la segunda al 80 % de la primera (pendiente de verificar).
14. El catálogo no trae pesos musculares. Incoherencias: la inclinada está en `chest_press_flat`; sentadilla y peso muerto son `accessory_compound`; `knee_hip_dominant` se clasifica distinto en `CT:54` y en `AO:101`; hay dos nociones de «familia».
15. No hay memoria de rendimiento por patrón ni por músculo: todo es por ejercicio (`WSR:280-287`, `WPHE:232-233`).
