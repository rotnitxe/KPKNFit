# E3b · RINGS del alta: ruta nueva, datos del borrador y paso a la evidencia v2 (puntos 20–21; sin UI)

Worktree indicado en tu encargo, rama `v3/e3b` (sale de `feat/wizard-v3` con C1 y E3a integrados). Lee antes
`docs/wizard-v3/BRIEF_COMUN.md`, del plan «Punto 20», «Punto 21» y «§3‑bis C», y el brief `E3a.md` (la evidencia v2 ya existe:
`InitialRecoveryEvidenceFactory.fromInputsV2`, `RingsLiftPattern`, `RingsMuscleGroup`). Las pantallas son de E3c: aquí solo datos, ruta,
reductores, validación, resúmenes, mapeo y setters.

**API real que dejó E3a** (integrado): `InitialRecoveryEvidenceFactory.fromInputsV2(capturedAtMs, zone: ZoneId, trainedDays: Set<Int>,
intensity, lifts: Set<RingsLiftPattern>, groupSets: Map<RingsMuscleGroup, Int>, sensations = InitialRecoverySensations(), sourceId =
"initial-recovery")`; enums `RingsLiftPattern { SQUAT, DEADLIFT, HINGE_ROW, BENCH, OVERHEAD, PULL_UP, LUNGE, CARDIO_LONG, INTERVALS }` y
`RingsMuscleGroup(label) { CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE }` en `data/models/InitialRecoveryEvidence.kt`. Sin días entrenados, las
sensaciones declaradas se aplican como penalización por canal (no las ocultes en «No entrené»: siguen preguntándose).

## Contexto (verificado)
- Ruta de RINGS: `domain/onboarding/SetupStepGraph.kt:422-439` (hoy `RINGS_RECENT` → [`SESSIONS`, `RECENCY`, `ACTIVITY`, `INTENSITY`, `AXIAL`]
  → tres pantallas de sensaciones → `DISCOMFORT` → `RESULT` → hito).
- Respuestas en `SetupRingsAnswers` (`screens/onboarding/SetupWizardModels.kt:316-338`); la UI de hoy escribe con `vm.updateStep` + `setStepChoice`
  (`SetupRingsSteps.kt:96-340`).
- Mapeo al guardar y a la vista previa: `SetupWizardViewModel.ringsMapping` (~3585-3613) → `SetupRingsMapper.map`
  (`domain/onboarding/SetupRingsMapper.kt:113-216`).

## 1. Ruta nueva (los ids retirados siguen en el enum y en `blocks`, como los demás pasos retirados)
`RINGS_INTRO` → `RINGS_SESSION_DAYS` → [solo si hubo días: `RINGS_MAIN_LIFTS` → [solo si hubo un día en las últimas 72 h:
`RINGS_GROUP_SETS`] → `RINGS_INTENSITY`] → `RINGS_FEELINGS` → `RINGS_DISCOMFORT` → `RINGS_RESULT` → `MILESTONE_RINGS`.
- Salen de la ruta: `RINGS_RECENT`, `RINGS_SESSIONS`, `RINGS_RECENCY`, `RINGS_ACTIVITY`, `RINGS_AXIAL` y las tres de sensaciones
  (`RINGS_MUSCLE_FEELING`, `RINGS_ENERGY_FEELING`, `RINGS_STRUCTURE_FEELING`), sustituidas por `RINGS_FEELINGS` (las tres en una pantalla).
- `SetupStepContext` gana lo que la ruta necesita (`ringsTrainedDays: Boolean`, `ringsTrainedLast72h: Boolean`), derivado en `stepContext()`.
- No subas `REVISION`: C1 ya la subió a 5 sin ninguna versión publicada entre medias. Un borrador a medias con respuestas viejas de RINGS
  conserva esas respuestas y su ley v1; los pasos nuevos sin respuesta se visitan.

## 2. Datos (en `SetupRingsAnswers`, todo con valor por defecto)
- `trainedDayOffsets: Set<Int> = emptySet()` (0 = hoy … 6) y `sessionDaysState` (`NOT_ANSWERED`, `DECLARED`, `NONE` «No entrené», `UNKNOWN`
  «No lo recuerdo»).
- `mainLifts: Set<RingsLiftPattern> = emptySet()` y `mainLiftsState` (`NOT_ANSWERED`, `DECLARED`, `NONE` «Ninguno de estos», `UNKNOWN`).
- `groupSets: Map<RingsMuscleGroup, Int> = emptyMap()` (0–20) y `groupSetsUnknown: Set<RingsMuscleGroup> = emptySet()`.
- Los campos de hoy se DERIVAN de los nuevos al escribir (para que lo que lee el resto de la app siga coherente): `recentTraining`,
  `recentTrainingState`, `sessionsLastSevenDays` (= número de días), `lastSessionRecencyDays` (= el menor desplazamiento),
  `activityType` (solo cardio → CARDIO; cardio y fuerza → MIXED; si no, STRENGTH) y `axialExposure` (SQUAT o DEADLIFT → DECLARED).

## 3. Reductores (`SetupStepAnswers.kt`) y setters (al FINAL de `SetupWizardViewModel.kt`, vía `mutateDraft`)
- `withTrainedDayToggled(offset)`, `withNoTraining()`, `withTrainingUnknown()` (los dos últimos exclusivos: vacían los días; tocar un día los
  apaga). Cambiar los días deja `capturedAtMs = null`.
- `withMainLiftToggled(pattern)`, `withNoMainLifts()`, `withMainLiftsUnknown()` (exclusivos igual).
- `withGroupSets(group, sets: Int?)` (null = «No lo sé»: entra en `groupSetsUnknown`).
- `withFeelings(muscular, energy, structure)` por campo (cada uno puede quedar sin responder).

## 4. Mapeo a la evidencia v2 (`ringsMapping` y `SetupRingsMapper`)
- Con `sessionDaysState == DECLARED`: `fromInputsV2(capturedAt, zona del dispositivo, trainedDayOffsets, intensidad, mainLifts, groupSets
  (sin los desconocidos), sensaciones, ...)`. Con `NONE`: la evidencia «no entrené» de v2. Con `UNKNOWN`: como hoy con historial desconocido
  (no se fabrica evidencia; las sensaciones van como calibración parcial).
- Sin respuestas nuevas (borrador viejo): exactamente el mapeo de hoy (v1).
- La zona horaria entra como parámetro (el ViewModel pasa la del sistema); el núcleo sigue puro.

## 5. Definiciones, textos, validación y resúmenes
| Paso | Título (≤ 44) | Subtítulo (≤ 100) |
|---|---|---|
| `RINGS_INTRO` | «Tus RINGS, tu recuperación de un vistazo» | «Mira cómo funcionan antes de calcular los tuyos.» |
| `RINGS_SESSION_DAYS` | «¿Qué días entrenaste?» | «Marca los días de la última semana en los que entrenaste.» |
| `RINGS_MAIN_LIFTS` | «¿Qué ejercicios principales hiciste?» | «Así sabemos cuánto cargaron tus músculos y tu columna.» |
| `RINGS_GROUP_SETS` | «¿Cuántas series hiciste por grupo?» | «Desde <día>. Si no lo sabes, déjalo en «No lo sé».» (el día lo pone la UI) |
| `RINGS_FEELINGS` | «¿Cómo te sientes hoy?» | «Tu sensación también cuenta para calcular tus RINGS.» |
- `RINGS_INTRO` se confirma sin dato (es una explicación); `RINGS_SESSION_DAYS` pide días, «No entrené» o «No lo recuerdo»;
  `RINGS_MAIN_LIFTS` pide al menos una opción; `RINGS_GROUP_SETS` y `RINGS_FEELINGS` son omitibles.
- Resúmenes: «3 días · último ayer», «No entrené», «No lo recuerdo»; «Sentadilla, Peso muerto»; «Piernas 14 · Espalda 10»; «Músculos 3 ·
  Energía 4 · Columna —».
- La huella (`SetupInputFootprint`) y la clave de la vista previa de RINGS incluyen todos los campos nuevos.

## Archivos permitidos
`domain/onboarding/{SetupStepGraph, SetupStepDefinitions, SetupRingsMapper}.kt`, `screens/onboarding/{SetupWizardModels, SetupStepAnswers,
SetupStepSummaries}.kt`, `SetupWizardViewModel.kt` (solo `ringsMapping` y los setters nuevos al final) y pruebas. `SetupRingsSteps.kt` solo
para que compile la ruta nueva (ramas vacías `-> Unit` para los ids nuevos: la UI es de E3c).

## Pruebas (JVM)
- `SetupStepGraphTest`: la ruta de RINGS con días en las últimas 72 h, con días más antiguos, con «No entrené» y con «No lo recuerdo».
- Nueva `SetupRingsAnswersV2Test`: exclusividad de «No entrené»/«No lo recuerdo» y de «Ninguno de estos»; los campos derivados; un borrador viejo
  se mapea igual que hoy; un borrador nuevo produce evidencia con `estimatorVersion = 2`; la huella cambia con cada campo nuevo.
- `SetupStepDefinitionsTest`, `SetupStepSummariesTest`, `*SetupStepCopyRules*`, `*SetupRingsMapper*`.
- Filtro: esas clases + `*SetupRings*`. Antes, `:app:compileDebugKotlin`.
