# Auditoría — REGLAS (con foco en REGLAS DE TIEMPO)
## Editor de sesiones → Modelo → Sesión en vivo

- **Repo:** `KPKNFit/android-native`
- **Alcance:** `screens/sessioneditor/*`, `data/models/*`, `screens/workout/*`, `domain/sessionassistant/*`, `domain/calculations/*`, tests relacionados
- **Tipo:** auditoría de lógica de negocio (solo lectura; ningún archivo de código fue modificado)

## Síntoma reportado

> "las reglas están funcionando en los ejercicios, no están aplicando los valores"

Las reglas existen/persisten en los ejercicios, pero sus valores NO se aplican en la sesión en vivo ni en las previews.

## Resumen ejecutivo

El sistema tiene DOS capas de "reglas":

- **(a) Defaults de reglas del editor** — viven solo en el estado de UI del editor (`SessionEditorUiState.ruleDefaults` y `partRuleDefaults`), con respaldo en un draft local en SharedPreferences (`PersistedSessionEditorDraft`).
- **(b) Valores materializados en el modelo** — `Exercise.restTime`, `Exercise.restBetweenSidesSeconds`, `SupersetGroup.restBetweenExercises/restAfterSuperset/roundRest*`, `Session/SessionPart/Exercise.targetDurationMinutes`, campos de `ExerciseSet`.

La sesión en vivo y TODAS las previews leen exclusivamente (b). El puente (a)→(b) es **manual** (botón "Aplicar" de la sheet o el flag `applyToNewItems` al crear elementos), silencioso y frágil: editar una regla no toca el modelo, no marca cambios, no dispara autosave, ni siquiera persiste en el draft si no ocurre otra edición.

Además, existen familias completas de reglas que se persisten y **nadie consume en ninguna capa** (límites `SessionEditorRuleLimits`, `Exercise.timeStrategy`, `ExerciseSet.restBetweenSides`, `WarmupSetDefinition.restBetween` en vivo).

El síntoma queda confirmado y explicado: la infraestructura viva para aplicar descansos materializados existe y funciona (`WorkoutSetRecorder.kt:438-464`); el fallo sistémico está en el editor.

---

## 1. Tabla maestra de reglas definibles en el editor

Leyenda de estados:
- **APLICADA** — el valor persiste en el modelo y un consumidor en vivo lo usa.
- **CONDICIONADA** — solo llega al modelo/vivo mediante un gesto manual (botón "Aplicar" o flag `applyToNewItems` al crear elementos nuevos).
- **PARCIAL** — consumida en vivo solo de forma blanda (guía visual/sonora), sin efecto duro.
- **IGNORADA** — persiste en el modelo/draft, pero ningún consumidor la lee.

| # | Regla (UI, tab REGLAS/TIEMPO) | Dónde persiste (modelo) | Consumidor en vivo (archivo:función) | Estado |
|---|---|---|---|---|
| 1 | Series (`setCount`) | `ExerciseSet` × N (creadas/rewritas) | `WorkoutStepRules.buildSteps` / `WorkoutV2Body` | APLICADA (vía Aplicar) |
| 2 | Reps (`reps`) | `ExerciseSet.targetReps` (`data/models/Session.kt:318`) | recorder/UI de serie | APLICADA (vía Aplicar) |
| 3 | Intensidad (`rpe` + `intensityType` RPE/RIR/FALLO) | `ExerciseSet.targetRPE/targetRIR/intensityMode/isFailure` (`Session.kt:320-322,330`) | autoregulación, UI | APLICADA (vía Aplicar). Clamp RIR inconsistente 0‑5 vs 0‑6 (ver D5) |
| 4 | Descanso Normal (`normalRestSeconds`, `SessionEditorModels.kt:16`) | `Exercise.restTime` (`Session.kt:224`; escritor `SessionEditorRulesEngine.kt:83`) | `WorkoutSetRecorder.kt:438` → `baseRest` → `WorkoutRestTimerOrchestrator.start` | CONDICIONADA |
| 5 | Descanso entre lados (`betweenSidesRestSeconds`, default 0 en `SessionEditorModels.kt:17`) | `Exercise.restBetweenSidesSeconds` (`Session.kt:252`; escritor `RulesEngine:84`, `takeIf { it>0 }` → puede quedar `null`) | `WorkoutSetRecorder.kt:452` + clasificador `WorkoutStepRules.kt:248` | CONDICIONADA + el default 0 la borra |
| 6 | Descanso entre ejercicios de superset (`supersetBetweenRestSeconds`, default 60) | `SupersetGroup.restBetweenExercises` (`Session.kt:68`; escritor `RulesEngine:96`) | `WorkoutSetRecorder.kt:453-456` (prioridad: round map > grupo > legacy > baseRest) | CONDICIONADA; tiene prioridad absoluta sobre `restTime` del ejercicio |
| 7 | Descanso de ronda superset (`supersetRoundRestSeconds`, default 120) | `SupersetGroup.restAfterSuperset` (`Session.kt:69`; escritor `RulesEngine:97`) | `WorkoutSetRecorder.kt:457-460` | CONDICIONADA |
| 8 | Descansos por ronda (manager de supersets) | `SupersetGroup.roundRestBetweenExercises / roundRestAfterSuperset` (`Session.kt:72-73`) | `WorkoutSetRecorder.kt:453,457` | APLICADA en vivo; la preview la IGNORA (`Calculations.kt:600-601` usa solo valores planos) |
| 9 | Flag "Aplicar a nuevos elementos" (`applyToNewItems`) | NO persiste en `Session`; solo draft local (`PersistedSessionEditorDraft`, `SessionEditorViewModel.kt:75,225`) | gatea `withSessionEditorDefaults` (`SessionEditorSessionHelpers.kt:75`), `addSet` (`SessionEditorViewModelStructure.kt:398-400`), `openSupersetCreator` (`SessionEditorViewModelSupersets.kt:118-121`) | CONDICIONADA; efímera por sesión de edición |
| 10 | Overrides Compuestos/Aislamiento (rest/reps/rpe/tipo) | mismo destino que #1‑#7 vía `SessionTemplateQualityRules.isCompound/isIsolation` | iguales que #1‑#7 | CONDICIONADA |
| 11 | Plantillas de reglas (guardar/aplicar) | SharedPreferences `session_editor_rule_templates` (`RuleTemplateStore.kt:101-102`); NO en `Session` | solo precargan defaults (editan la capa a) | no tocan ejercicios por sí solas |
| 12 | Límite global de tiempo (`targetDurationMinutes`) | `Session.targetDurationMinutes` (`Session.kt:36`; escritor `SessionEditorViewModelVariants.kt:5-17`) | `WorkoutPacingController` (timer `:193-194`, alertas `:211-249`, `adjustRestTimeForPace :252-268`); `WorkoutSessionHydrator.kt:274-275,374-379`; `WorkoutV2Body.kt:167-169` | APLICADA (efecto suave: solo recorta descanso si vas tarde) |

| 13 | Presupuesto por grupo/categoría | `SessionPart.targetDurationMinutes` (`Session.kt:117`; escritor `Variants.kt:20-26`) | `WorkoutV2Body.kt:591-613` (barra de progreso) + `WorkoutPacingController.checkLocalBudgetGuide :71-88` | PARCIAL — solo guía visual/sonora |
| 14 | Presupuesto por ejercicio | `Exercise.targetDurationMinutes` (`Session.kt:265`; escritor `Variants.kt:29-41`) | `WorkoutV2Body.kt:590-602` (idem) | PARCIAL |
| 15 | "Repartir global en grupos" | reparte #12 en #13 por peso de series (`Variants.kt:44-66`) | como #13 | PARCIAL |
| 16 | Sugerencias del Time Coach (tab TIEMPO) | `Exercise.restTime` (`TimeCoachEngine.kt:139-145`), creación de supersets, técnicas de densidad, borrado de series/ejercicios | como #4‑#7 tras escribir el modelo | APLICADA (escribe el modelo directo vía `applyTimeCoachSuggestion`, `SessionEditorViewModel.kt:807-836`) |
| 17 | Límites de reglas (`SessionEditorRuleLimits`: maxRPE, maxExercisesPerMuscle, maxVolumePerMuscleSession/Weekly, maxSamePatternPerSession, rigidLimits — `SessionEditorModels.kt:43-50`) | draft local (`SessionEditorViewModel.kt:77,227`); restaurado `SessionEditorViewModelNavigation.kt:309` | NADIE: callbacks suprimidos (`RulesSheet.kt:192-197` `@Suppress`), validador no‑op (`SessionEditorRulesEngine.kt:176-188`) | IGNORADA (subsistema zombie) |
| 18 | Ajuste global de intensidad (`applyGlobalIntensityAdjustment`) | reescribe sets (`RulesEngine:139-174`) | función real pero callback suprimido en la sheet (`RulesSheet.kt:197`) | SIN UI (código muerto) |
| 19 | `Exercise.timeStrategy` (COUNTDOWN/CHRONOMETER/FREE) | campo serializado `Session.kt:264` + Room (`KpknDatabase.kt:417-419`) | NADIE en todo `main/` (0 productores, 0 consumidores; búsqueda global: solo modelo + comentario DB) | IGNORADA (campo muerto) |
| 20 | `ExerciseSet.restBetweenSides` (descanso entre lados por serie) | `Session.kt:362` | vivo solo lee el nivel ejercicio (`WorkoutSetRecorder.kt:452`) | IGNORADA en vivo |
| 21 | Descanso por ejercicio (card del editor) | `Exercise.restTime` + `restBetweenSidesSeconds` (`ExerciseEditorCard.kt:144,183,411-419`) | `WorkoutSetRecorder.kt:438,452` | APLICADA (path fiable hoy) |
| 22 | `ExerciseSet.targetDuration` (series por tiempo, TrainingMode.TIME) | `Session.kt:319` | timer de serie `WorkoutSetRecorder.kt:250` (`timerTargetSeconds`); badge/stepper `WorkoutContextComponents.kt:1359-1365`; preview `Calculations.kt:558` | APLICADA |
| 23 | Descanso de warmup (`WarmupSetDefinition.restBetween`) | `Session.kt:375` | preview SÍ (`Calculations.kt:537`); el timer en vivo usa `baseRest` para `RestTimerKind.WARMUP` (`WorkoutSetRecorder.kt:461-463`) | IGNORADA en vivo |


---

## 2. REGLAS DE TIEMPO en detalle — traza editor → modelo → vivo

### 2.1 Cadena que SÍ funciona (pero condicionada)

EDITOR (tab REGLAS, `RulesSheet.kt`):
- Edición de descansos: diálogos `KpknNativeTimePickerDialog` (`RulesSheet.kt:226-271`) → `onRuleDefaultsChange` / `onPatchRuleDefaults`.
- Wiring: `SessionEditorScreen.kt:835` → `viewModel.updateRuleDefaults` / `:899` → `patchRuleDefaults`.
- Ambos entran en `SessionEditorViewModelStructure.kt:607-668` → **solo `updateUi`** (estado en memoria). Sin `updateSession`, sin `scheduleAutoSave`, sin `persistDraft`.
- Botón "Aplicar" (`RulesSheet.kt:766-786`) → `SessionEditorScreen.kt:766-767 onApplyRules → viewModel.applyRuleDefaultsToSession(partId)` → `Structure.kt:594-605` → `updateSession` → `SessionEditorRulesEngine.applyDefaults` (`RulesEngine:23-109`), que escribe:
  - Sets: `targetReps/targetRPE/targetRIR/intensityMode/isFailure/targetPercentageRM` (`RulesEngine:69-80`), luego `normalizeSet` con peso auto-sugerido (`:244-274`).
  - `Exercise.restTime = effectiveRest` (`:83`).
  - `Exercise.restBetweenSidesSeconds = safeSideRest.takeIf { it > 0 }` (`:84`).
  - `SupersetGroup.restBetweenExercises / restAfterSuperset` (`:93-99`).
  - `updateSession` dispara `scheduleAutoSave` (`SessionEditorViewModel.kt:517-534`) → `persistRecoverableSession` (`:241-254`) → Room (`repository.upsertSessionInProgram`) + draft SharedPreferences.
- Flag `applyToNewItems`: solo gatea (sin Aplicar) `withSessionEditorDefaults` al añadir ejercicios (`SessionEditorSessionHelpers.kt:71-122`), `addSet` (`Structure.kt:393-401`) y defaults al crear supersets (`SessionEditorViewModelSupersets.kt:118-121`).

TAB TIEMPO (misma sheet, `rulesSheetInitialTab`, `SessionEditorModels.kt:194-195`):
- Presupuesto global: chips 30/45/60/90 + "Estimado" + "Sin límite" (`RulesSheet.kt:948-981`) → `setTargetDuration` → `Variants.kt:5-17` escribe `Session.targetDurationMinutes`.
- Presupuestos por grupo/ejercicio (`RulesSheet.kt:1040-1070+`) → `setPartTargetDuration` / `setExerciseTargetDuration` (`Variants.kt:20-41`).
- "Repartir global en grupos" (`RulesSheet.kt:1032-1038`) → `distributeTargetDurationAcrossParts` (`Variants.kt:44-66`).
- Sugerencias del Time Coach (`RulesSheet.kt:854-927`) → `applyTimeCoachSuggestion` (`SessionEditorViewModel.kt:807-836`) → `TimeCoachEngine.apply` (`TimeCoachEngine.kt:138-161`), que escribe el modelo directo (p.ej. `exercise.restTime` para `ReduceRests`, `:139-145`) vía `updateSession`.

VIVO (al registrar una serie, `WorkoutSetRecorder.kt:426-552`):
- `438`  → `val baseRest = exercise.restTime?.takeIf { it > 0 } ?: repository.settings.value.restTimerDefaultSeconds`
- `439-444` → resolución de grupo superset y detección de misma ronda.
- `445-450` → `restKind` ∈ { BETWEEN_SIDES, SUPERSET_INTRA, SUPERSET_ROUND, STANDARD }.
- `451-464` → `plannedRestForKind`:
  - BETWEEN_SIDES → `exercise.restBetweenSidesSeconds ?: 0`
  - SUPERSET_INTRA → `roundRestBetweenExercises[setIdx] ?: group.restBetweenExercises ?: exercise.supersetRestBetween ?: baseRest`
  - SUPERSET_ROUND → `roundRestAfterSuperset[setIdx] ?: group.restAfterSuperset ?: exercise.supersetRestAfter ?: baseRest`
  - WARMUP / STANDARD → `baseRest`
- `474-480` → multiplicador de densidad AUGE usa `plannedRestForKind`.
- `507-517` → descanso adaptativo `WorkoutAdaptiveRest.compute(baseRest, contexto)` (factores acumulativos 0.75–2.10 por técnica/drain/RPE/progreso/tipo/ROM, clamp final 45–360 s, `WorkoutAdaptiveRest.kt:17-20,46-57`).
- `527-528` → `ports.adjustRestTimeForPace(...)` → `WorkoutPacingController.kt:252-268`: si `targetDurationMinutes` existe y vas tarde (`remainingMin <= 15 && progress < 0.50`), recorta a `60.coerceAtLeast(baseSeconds - 30)`.
- `543-551` → `ports.startRestTimer(seconds = effectivePlanned, ...)` → `WorkoutViewModel.kt:2347-2354` → `WorkoutRestTimerOrchestrator.start` (el orquestador NO decide valores: ejecuta los segundos recibidos; `WorkoutRestTimerOrchestrator.kt:36-83`).
- Clasificación previa del paso: `WorkoutStepRules.kt:206-255` asigna `restAfterKind`; `:248` marca BETWEEN_SIDES solo si `exercise.restBetweenSidesSeconds ?: 0 > 0`.

HIDRATACIÓN / RESUME (`WorkoutSessionHydrator.kt`):
- `274-275` → restaura `customTargetDurationMinutes` / `targetDurationMinutes` desde el estado en curso o la sesión.
- `374-379` → si hay objetivo, arranca `startSessionTimer(remainingSeconds)`.
- No transforma descansos: conserva `restTime` tal cual viene del modelo; `restModalState` se restaura desde el ongoing state.
- Normalización previa: `WorkoutViewModel.kt:1109-1114` → `SupersetRules.normalizeSession` preserva `restBetweenExercises/restAfterSuperset/roundRest*` del grupo existente (`SupersetRules.kt:33-46`); fallback legacy 60/120 solo si no existen (`:36-41`).


### 2.2 Dónde se rompe la percepción del usuario (traza del fallo)

1. **Los valores de la tab REGLAS no están sembrados desde el modelo.** `SessionEditorUiState.ruleDefaults` arranca con defaults duros (`3×10, RPE 8, 90s, lados 0 — SessionEditorModels.kt:12-40`) salvo que exista draft local restaurado (`SessionEditorViewModelNavigation.kt:307-309`: `ruleDefaults = persistedDraft?.ruleDefaults ?: it.ruleDefaults`). El usuario ve "90s" en Reglas aunque sus ejercicios tengan 75s o 120s por card (escritos por `ExerciseEditorCard.kt:411-419`) → "las reglas no están aplicando los valores".
2. **Editar valores en la sheet NO escribe nada ni persiste.** `updateRuleDefaults` (`Structure.kt:623-668`) y `patchRuleDefaults` (`:607-621`) → solo `updateUi` (`SessionEditorViewModel.kt:174-176`, que no marca `hasUnsavedChanges` ni llama a `scheduleAutoSave`). El draft solo se escribe desde `persistRecoverableSession` (`:241-254`) / `persistDraft` (`:215-239`), alcanzables únicamente vía `updateSession`/autosave o guardado explícito. Si el usuario edita los números y sale sin pulsar "Aplicar", los cambios se pierden incluso del draft.
3. **Vivo y previews leen el modelo, nunca `ruleDefaults`.** Preview: `Calculations.kt:592` (`exercise.restTime ?: 90`), `SessionEditorAugeComputation.kt:457,567`. Vivo: `WorkoutSetRecorder.kt:438`. Mover la regla no mueve ni el "Estimado" del tab TIEMPO (`RulesSheet.kt:795-851`) ni el timer hasta pulsar "Aplicar".
4. **`WorkoutSetRecorder` es el ÚNICO resolvedor de segundos en vivo** (`:438-464`). No existe ninguna regla de tiempo por ejercicio (duración objetivo dura, tempo, time cap) que viva fuera de los 4 `RestTimerKind` (`WorkoutSessionContracts.kt:9`). Los budgets por ejercicio/grupo solo guían (`WorkoutV2Body.kt:589-650`); `Exercise.timeStrategy` ni siquiera existe en vivo.
5. **En supersets, el `restTime` del ejercicio queda totalmente ignorado** (grupo manda, `SetRecorder:445-464`). El editor lo advierte en texto pequeño (`ExerciseEditorCard.kt:601-607`), pero para el usuario es "mi valor no se aplica". Idéntica omisión en la preview (`Calculations.kt:604`).
6. **Respuesta a la pregunta concreta:** si existe regla de tiempo por ejercicio (duración de serie, time cap, descanso custom, tempo): `WorkoutViewModel`/hydrator/orquestador NO consultan ninguna regla por sí mismos; el único punto de decisión es `WorkoutSetRecorder.kt:438-464`, que usa exactamente `exercise.restTime` (materializado) o el default global de Settings. Duración de serie (`targetDuration`) sí llega al timer de serie (`SetRecorder:250`); budget min por ejercicio/grupo solo a la barra/guía (`WorkoutV2Body.kt:590-617`); `timeStrategy` y tempo no llegan a nada.

---

## 3. ¿SessionEditorRulesEngine tiene efectos reales?

Archivo: `screens/sessioneditor/SessionEditorRulesEngine.kt` (276 líneas).

- **`applyDefaults` (`:23-109`) — efectos REALES y destructivos.** Reescribe el array completo de sets por ejercicio (creando/borrando series hasta `setCount`), normaliza intensidad (`normalizeSet :244-274`, con peso auto-sugerido vía `calculateSuggestedLoad :272-273`), escribe `restTime` (`:83`), `restBetweenSidesSeconds` (`:84`) y descansos de grupos superset (`:93-99`). Pisa valores personalizados por ejercicio y borra el descanso-entre-lados cuando la regla está en 0. Ignora por completo el flag `applyToNewItems` (eso solo gatea la vía "nuevos elementos").
- **`applyGlobalIntensityAdjustment` (`:139-174`) — real pero huérfano.** Reescribe `intensityMode/targetRPE/targetRIR/isFailure/targetPercentageRM` por músculo o global (`adjustExerciseIntensity :190-235`), pero su callback está suprimido en la sheet (`RulesSheet.kt:197`), sin UI activa.
- **`normalizeRuleLimits` / `normalizeAdvancedRuleLimits` (`:111-137`) — normalizadores puros.** Producen `SessionEditorRuleLimits` que nadie consume.
- **`validateBeforeSave` (`:176-188`) — no-op by design.** Solo bloquea nombre en blanco; comentario interno `:185-186`: "Editor rules are defaults-only. Legacy limit fields may exist in old drafts, but they intentionally do not block saves or emit warnings from this sheet." Toda la familia de límites está funcionalmente muerta.
- Import muerto: `suggestRestSeconds` (`:11`) no se usa en el archivo.


---

## 4. Desconexiones de defaults / unidades

| # | Desconexión | Evidencia | Sev |
|---|---|---|---|
| D1 | Fallback de descanso distinto por capa: previews usan **90 s hardcodeado**; vivo usa `settings.restTimerDefaultSeconds` (configurable, default 90) | `Calculations.kt:592`; `SessionEditorAugeComputation.kt:457,567`; vs `WorkoutSetRecorder.kt:438`; `Settings.kt:19-20` | MEDIO |
| D2 | La preview **ignora** `restBetweenSidesSeconds` (sesiones unilaterales infraestimadas), ignora los descansos por ronda (`roundRest*`), y cuenta descanso tras la última serie de cada ejercicio | `Calculations.kt:595-607` (sin between-sides; `restSec += exerciseRestSec * sets.size :606`); `:600-601` (solo valores planos del grupo) | MEDIO |
| D3 | La preview **ignora** el descanso adaptativo y el ajuste por ritmo → el "Estimado" del tab TIEMPO diverge de la sesión real (adaptativo puede llegar a ×2.10, cap 360 s) | `WorkoutAdaptiveRest.kt:17-20,46-57`; `WorkoutPacingController.kt:263-266` | MEDIO |
| D4 | `ruleDefaults` no se seed-ea desde la sesión ni desde Settings: defaults duros `3×10 RPE8 90s lados 0 / entre-ej 60 / ronda 120` | `SessionEditorModels.kt:12-21`; restauración solo desde draft `Navigation:307-309` | ALTO |
| D5 | Clamp RIR incoherente entre rutas: 0‑5 al aplicar defaults vs 0‑6 en ajuste global de intensidad | `RulesEngine:74` y `SessionEditorSessionHelpers.kt:111` vs `RulesEngine:213` | BAJO |
| D6 | `isolationRpe` admite 0.0 mientras compuesto/global exigen mínimo 1.0 (preset de fábrica lo usa con RIR, "Entreno equilibrado") | `RulesEngine:61` vs `:31,60`; `RuleTemplateStore.kt:186-187` | BAJO |
| D7 | Picker de descanso reutiliza el campo "hora" como minuto (techo implícito 23:59 de descanso) | `RulesSheet.kt:262-266` | BAJO |
| D8 | `applyDefaults` con scope de parte reescribe los descansos de grupos superset que comparten ejercicios con otra parte (condición `none { it in scopedExerciseIds }`) | `RulesEngine:88-99` | MEDIO |
| D9 | Unidades: coherentes en estas rutas (todos los descansos en segundos `Int`; budgets en minutos; `endsAtMs` en ms solo interno del timer). Sin bug ms↔s ni min↔s detectado | — | OK |

---

## 5. Cobertura de tests

**Cubierto:**
- `SessionEditorRulesEngineTest` (`app/src/test/.../screens/sessioneditor/`): `applyDefaults_rewrites_sets_with_target_defaults` (`:39-67`, solo aserta sets: count/reps/RPE/mode); `applyGlobalIntensityAdjustment_scopes_to_selected_muscles` (`:69-98+`); `validateBeforeSave` no bloquea límites legacy (`:138-205`); builder de bloques superset (`:207-225`).
- `SessionEditorRulesEngineValidationTest`: solo `validateBeforeSave` (nombre en blanco bloquea / sin warnings legacy).
- `WorkoutStepRulesTest`: orden mobility→warmup→working (`:16-33`); expansión unilateral y clasificación de `RestTimerKind` — BETWEEN_SIDES si `restBetweenSidesSeconds>0` (`:35-50`), STANDARD si 0 (`:52-66`); sets unilaterales extra por lado (`:68-…`); supersets (orden de rondas, mobility agrupada, `:140-211`).
- `SupersetRulesTest`: create/normalize/dissolve (incl. `dissolve_copiesRoundRestAsIndividualRest`, `normalizeSession_promotesLegacySupersetIdsToCanonicalGroups`, cap de 4 miembros).
- `TimeCoachEngineTest` (9 tests): **solo `generate_*`** (sugerencias de descanso sobre límite, dedupe de densidad, priorización aislamiento, reemplazos curados, redundancia de patrón). Ningún test de `TimeCoachEngine.apply` (el que escribe `restTime`).
- `WorkoutRestAlertRulesTest` (`services/workout/`): prealerta 3 s antes, beeps, vibración, ventanas de fallo de audio. Nada de valores de descanso.
- `WorkoutSessionRulesTest` (23 tests): edición en vivo (estado, scope de persistencia, normalización de modos), continuidad superset, handoff de feedback, claves de set, pulse tokens, voz. Nada de descansos/ritmo.

**NO cubierto:**
1. **La cadena completa regla → `Exercise.restTime` → segundos del timer**: `WorkoutSetRecorder.kt:438-464` (baseRest, `plannedRestForKind`, prioridad round-map > grupo > legacy > ejercicio) no tiene ningún test. Tampoco el arranque `:543-551`.
2. `SessionEditorRulesEngine.applyDefaults` sobre `restTime`, `restBetweenSidesSeconds` y `SupersetGroup.*`: los tests solo asertan targets de sets, **nunca los campos de tiempo** ni el scope por parte.
3. `WorkoutAdaptiveRest.compute` (clamps/factores), `WorkoutPacingController.adjustRestTimeForPace`, hidratación de `customTargetDurationMinutes` y arranque del session-timer: 0 tests.
4. Que editar `ruleDefaults` sin pulsar "Aplicar" deje el modelo intacto (el comportamiento raíz del bug reportado) no está fijado por ningún test.
5. El contrato D1 (preview usa 90 fijo vs vivo usa Settings) no está testeado.


---

## 6. Lista priorizada de hallazgos

| # | Severidad | Hallazgo | Evidencia |
|---|---|---|---|
| 1 | CRÍTICO | Los edits de la tab REGLAS (incl. descansos) viven solo en `SessionEditorUiState`: `updateUi` sin persistencia ni efecto; se pierden al salir y nunca llegan a vivo/previews hasta pulsar "Aplicar" | `SessionEditorViewModelStructure.kt:607-668`; `SessionEditorViewModel.kt:174-176,215-254` |
| 2 | CRÍTICO | `ruleDefaults` con defaults duros (90s/3×10/RPE8/lados 0) no sembrados desde la sesión; pulsar "Aplicar" sobrescribe los descansos personalizados por ejercicio y borra el descanso-entre-lados cuando la regla está en 0 | `SessionEditorModels.kt:12-21`; `SessionEditorRulesEngine.kt:64-98`; `SessionEditorViewModelNavigation.kt:307-309` |
| 3 | ALTO | `Exercise.timeStrategy` (COUNTDOWN/CHRONOMETER/FREE): campo persistido en Room con cero productores y cero consumidores en Android | `Session.kt:264,289`; `KpknDatabase.kt:417-419`; búsqueda global `timeStrategy` en `main/` |
| 4 | ALTO | Subsistema `SessionEditorRuleLimits` completo: persiste en draft, se restaura, pero UI suprimida y validador no-op → reglas guardadas que nadie aplica | `RulesSheet.kt:192-197`; `RulesEngine:176-188`; `SessionEditorViewModel.kt:77,227`; `Navigation:309` |
| 5 | ALTO | En supersets, `Exercise.restTime` queda ignorado en vivo y preview (el grupo manda); si el grupo se creó con `applyToNewItems=false` hereda 60/120 por defecto | `WorkoutSetRecorder.kt:445-464`; `SessionEditorViewModelSupersets.kt:118-121`; `Calculations.kt:595-607`; aviso UI `ExerciseEditorCard.kt:601-607` |
| 6 | MEDIO | Descansos por ronda (`roundRest*`) y warmup `restBetween`: aplicados a medias — rondas NO entran en preview; warmup `restBetween` no alimenta el timer en vivo (WARMUP usa `baseRest`) | `Calculations.kt:537,600-601`; `WorkoutSetRecorder.kt:461-463` |
| 7 | MEDIO | Budgets de tiempo por ejercicio/grupo: solo barra de progreso + aviso sonoro al 75/90/100%; no recortan ni cortan; el usuario puede esperar límite duro | `WorkoutV2Body.kt:589-650`; `WorkoutPacingController.kt:71-88` |
| 8 | MEDIO | `adjustRestTimeForPace` solo mira el objetivo de sesión y solo acorta cuando vas muy tarde (≤15 min, <50% progreso): los presupuestos locales no ajustan descanso | `WorkoutPacingController.kt:252-268` |
| 9 | MEDIO | Baselines desalineados: preview 90 s fijos + sin between-sides + sin adaptativo vs vivo con Settings + adaptativo (hasta ×2.10, cap 360 s) + pace-adjust | `Calculations.kt:592`; `SessionEditorAugeComputation.kt:457,567`; `WorkoutAdaptiveRest.kt:17-20`; `Settings.kt:19` |
| 10 | MEDIO | `applyDefaults` con scope de parte reescribe grupos superset que se extienden a otras partes | `SessionEditorRulesEngine.kt:88-99` |
| 11 | BAJO | Campo zombie `ExerciseSet.restBetweenSides` (nivel serie) ignorado en vivo (solo se lee el nivel ejercicio) | `Session.kt:362` vs `WorkoutSetRecorder.kt:452` |
| 12 | BAJO | Clamps RIR incoherentes 0‑5 vs 0‑6; `isolationRpe` admite 0.0 | `RulesEngine:74` vs `:213`; `:61`; `SessionEditorSessionHelpers.kt:111` |
| 13 | BAJO | Import muerto `suggestRestSeconds` en el engine; picker minuto-como-hora con techo 23:59 | `RulesEngine:11`; `RulesSheet.kt:262-266` |
| 14 | INFO | Cadena regla→modelo→timer sin ningún test end-to-end; el test más cercano (`WorkoutStepRulesTest`) cubre solo el TIPO de timer, no los segundos | ver §5 |


## 7. Conclusión

La infraestructura viva para aplicar descansos materializados existe y es correcta (`WorkoutSetRecorder.kt:438-464`, con prioridad sensata round-map > grupo > legacy > ejercicio > settings, más capas adaptativa y de ritmo). El fallo sistémico está en el editor:

1. Las "reglas" son **defaults de escritorio** que exigen un gesto manual (botón "Aplicar") para materializarse en el modelo; la edición es invisible para persistencia, previews y sesión en vivo.
2. Los defaults **no se siembran** desde la sesión real, así que la sheet siempre muestra valores genéricos desconectados de los ejercicios — y "Aplicar" destruye personalizaciones.
3. Hay subfamilias enteras de reglas **persistidas sin consumidor**: límites (`SessionEditorRuleLimits`), `timeStrategy`, `restBetweenSides` por serie, warmup `restBetween` (en vivo), descansos por ronda (en preview).

Exactamente el reporte del usuario: "las reglas están funcionando en los ejercicios, no están aplicando los valores".

## Anexo — mapa de archivos clave

- Editor: `screens/sessioneditor/SessionEditorRulesEngine.kt`, `SessionEditorModels.kt` (`SessionEditorRuleDefaults :12-40`, `SessionEditorRuleLimits :43-50`, UiState `:149+`), `SessionEditorContracts.kt`, `RuleTemplateStore.kt`, `SessionEditorViewModel.kt` (draft `PersistedSessionEditorDraft :66-80`, `persistDraft :215-239`, autosave `:135-144`), `SessionEditorViewModelStructure.kt` (`applyRuleDefaultsToSession :594-605`, `updateRuleDefaults :623-668`), `SessionEditorViewModelVariants.kt` (targets de tiempo), `SessionEditorSessionHelpers.kt` (`withSessionEditorDefaults :71-122`), `components/sheets/RulesSheet.kt`, `components/ExerciseEditorCard.kt:144-183,405-420`.
- Modelo: `data/models/Session.kt` (`Session :6-38`, `SupersetGroup :65-75`, `Exercise :212-272`, `ExerciseSet :316-364`), `data/models/Settings.kt:19-20`.
- Dominio: `domain/calculations/Calculations.kt:431-456,498-620`, `domain/sessionassistant/TimeCoachEngine.kt:23-62,103-161`, `domain/workout/SupersetRules.kt:13-78`.
- Vivo: `screens/workout/WorkoutSetRecorder.kt:426-614`, `WorkoutRestTimerOrchestrator.kt`, `WorkoutAdaptiveRest.kt`, `WorkoutPacingController.kt`, `WorkoutSessionHydrator.kt`, `WorkoutStepRules.kt`, `WorkoutEditingRules.kt`, `WorkoutV2Body.kt:167-169,580-650`, `WorkoutSessionContracts.kt:9`.
- Tests: `app/src/test/.../screens/sessioneditor/SessionEditorRulesEngineTest.kt`, `SessionEditorRulesEngineValidationTest.kt`, `screens/workout/WorkoutStepRulesTest.kt`, `WorkoutSessionRulesTest.kt`, `services/workout/WorkoutRestAlertRulesTest.kt`, `domain/sessionassistant/TimeCoachEngineTest.kt`, `domain/workout/SupersetRulesTest.kt`.

