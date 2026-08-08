# Plan — Corregir reglas y reglas de tiempo (auditoría 4)

**Fecha:** 2026-08-07  
**Autor:** orquestador (muse-spark-1.2)  
**Estado:** `pending_approval` (no editar código de producto hasta aprobación explícita)  
**Auditoría fuente:** `docs/audits/2026-08-editor-sesiones/reglas-y-tiempo.md` (209 líneas, solo lectura, 2026-08, alcance `screens/sessioneditor/*` + `data/models/*` + `screens/workout/*` + `domain/sessionassistant/*` + `domain/calculations/*`)  
**Solicitud:** solucionar paso a paso lo cubierto por la auditoría 4 sin romper `persistDraft`/`autosave`/`RTM`.

---

## 1. Resumen ejecutivo

El sistema tiene dos capas de reglas ya confirmadas por 3 investigadores paralelos:

- **(a) Defaults UI** `SessionEditorUiState.ruleDefaults/partRuleDefaults` (`SessionEditorModels.kt:12-40`) — viven solo en memoria + draft local `PersistedSessionEditorDraft` (`SessionEditorViewModel.kt:66-80`, `PersistedSessionEditorDraft.ruleDefaults`) en `SharedPreferences` `SESSION_EDITOR_DRAFT_PREFS`.
- **(b) Valores materializados** `Exercise.restTime/restBetweenSidesSeconds`, `SupersetGroup.restBetweenExercises/restAfterSuperset/roundRest*`, `ExerciseSet.*`, `Session/SessionPart/Exercise.targetDurationMinutes` (`Session.kt:36,65-75,117,212-272,316-364`).

**Puente (a)→(b) manual, frágil:** editar REGLAS solo `updateUi` (`SessionEditorViewModelStructure.kt:607-668`) sin `persistDraft`, sin `hasUnsavedChanges`, sin `scheduleAutoSave` → se pierde al salir sin pulsar “Aplicar” (`RulesSheet.kt:766-786` → `Structure.kt:594-605` → `RulesEngine.applyDefaults:23-109`). Además `ruleDefaults` nace con defaults duros `3×10 RPE8 90s/0/60/120` (`Models.kt:12-21`) nunca sembrados desde la sesión real (`ViewModel.kt:407` `?: SessionEditorRuleDefaults()`), así que “Aplicar” pisa personalizaciones y borra `restBetweenSides` cuando está en 0 (`RulesEngine:92 takeIf>0`).

Vivo y previews leen solo (b). El resolvedor en vivo es único y correcto (`WorkoutSetRecorder.kt:438-464` prioridad `roundRest > grupo > legacy > baseRest` = `Settings.restTimerDefaultSeconds`), pero previews usan `90` hardcodeado (`Calculations.kt:592`, `AugeComputation.kt:645`) e ignoran `betweenSides/roundRest/adaptive/pace`.

Subfamilias **IGNORADA/PARCIAL** confirmadas: `Exercise.timeStrategy` 0 consumidores en `main/` (`Session.kt:264`, `KpknDatabase.kt:418` comentario), `SessionEditorRuleLimits` suprimido `RulesSheet.kt:192-197`, `ExerciseSet.restBetweenSides` solo escribe `null` (`SessionEditorControls.kt:888`), `WarmupSetDefinition.restBetween` preview sí (`Calculations.kt:537`) vivo no (`SetRecorder.kt:461`), budgets part/exercise solo guía (`WorkoutV2Body.kt:589-650`, `PacingController.kt:71-88`).

---

## 2. Contexto y reproducción

- **C1 repro:** abrir editor → REGLAS → cambiar “Descanso Normal” 90→120 (picker `RulesSheet.kt:226-271` → `onRuleDefaultsChange` → `Structure.kt:623`) → cerrar sheet sin “Aplicar” → `onPause` (`SessionEditorScreen.kt:??` `saveDraftForExit`) persiste solo `session` tal cual (`persistRecoverableSession:251`) + `ruleDefaults` del `_uiState` **previo** al edit si `persistDraft` no se llamó → al reabrir `loadSessionInternal:407` restaura `SessionEditorRuleDefaults()` 90s, 120 perdido.
- **C2/D4 repro:** sesión con 3 ejercicios `restTime` 75s/90s/120s (editados por `ExerciseEditorCard.kt:411-419`) → abrir REGLAS → UI muestra 90s (hardcode) → pulsar “Aplicar” → `RulesEngine:91` pisa todos a 90s, borra `restBetweenSidesSeconds=30` si regla 0 (`:92`).
- **D8 repro:** sesión con 2 partes; `SupersetGroup` `[A(parte1), B(parte2)]` (inter-parte permitido `Session.allSupersetGroups()`) → aplicar reglas solo a `parte1` → `RulesEngine:102` `none{it in scopedIds}` false (A en parte) → reescribe `restBetweenExercises/restAfterSuperset` afectando a B.
- **D1 repro:** Settings `restTimerDefaultSeconds=45` → vivo usa 45 (`SetRecorder:438`), preview muestra 90 → estimado `62 min` vs real `55 min`.
- **D2 repro:** unilateral `restBetweenSidesSeconds=30` 6 sets → vivo 6×30=180s extra (`SetRecorder:452`), preview 0 → subestima 3 min.
- **#6 warmup repro:** ejercicio con `warmupSets=[restBetween=90]` y `exercise.restTime=60` → vivo timer `60` (`SetRecorder:461`), preview `90` (`Calculations:537`) → diverge.

---

## 3. Hallazgos verificados (archivo:línea actual, `android-native/app/src/main/java/com/example/kpkn/`)

> Room v20 autoridad (`KpknDatabase.kt:20` `version=20`) vs `docs/ARCHITECTURE.md:29` v19 desactualizado. Líneas de auditoría desplazadas por refactors (ej `AugeComputation:457` hoy es `orderedSuggestions`).

| # | Sev audit | Hallazgo | Evidencia confirmada |
|---|---|---|---|
| **C1** | CRÍTICO | Edits REGLAS solo `updateUi`, se pierden | `Structure.kt:607-621` `patchRuleDefaults` solo `updateUi`, `623-668` `updateRuleDefaults` solo `updateUi`; `ViewModel.kt:184-186` `updateUi` puro `_uiState.update`; contraste `applyRuleDefaultsToSession:594-605` sí `updateSession` (`ViewModel.kt:527-541` `hasUnsavedChanges+scheduleAugeRecalc+scheduleAutoSave`). Wiring `Screen.kt:835,899`, `RulesSheet:766-786` |
| **C2/D4** | CRÍTICO/ALTO | Defaults duros 90s no sembrados, Aplicar pisa | `Models.kt:12-21` `normalRest=90/betweenSides=0/60/120`; `ViewModel.kt:407-409` `resolvedRuleDefaults = persistedDraft?.ruleDefaults ?: SessionEditorRuleDefaults()` sin inferencia desde `draft` (`draft.allExercises().map{restTime}`); `Navigation.kt:307-309` igual |
| **D5** | BAJO | Clamp RIR 0-5 vs 0-6 | **REFUTADO — ya fix** `RulesEngine:67-68` `coerceIn(0.0,6.0)`, `:82` `coerceIn(0,6)`, `:221` `0,6`, `SessionHelpers:102,116` `0,6` + comentario `FIX 59-61`. Auditoría desactualizada. Solo añadir test regresión |
| **D8** | MEDIO | Scope parte reescribe superset cross-part | `RulesEngine:96-106` `scopedExerciseIds` por parte, `102 if(none) group else copy(safeBetween/safeRound)` → basta 1 miembro en parte para reescribir todo |
| **D1** | MEDIO | Preview 90 vs vivo Settings | Vivo `SetRecorder:438` `?: settings.restTimerDefaultSeconds` (`Settings.kt:19`); preview `Calculations:592` `?:90`, `AugeComputation:645` `listOf(90)`, `581` `?:90` |
| **D2** | MEDIO | Preview ignora betweenSides/roundRest/warmup | `Calculations:591-607` no `restBetweenSides`; `600-601` solo `group.restBetweenExercises/restAfterSuperset` planos no `roundRest*` (`SupersetRules:72-73`); `674 estimateSessionDurationMinutes` ignora warmup/mobilty |
| **D3** | MEDIO | Preview sin adaptive/pace | Vivo `AdaptiveRest:17-18` 45-360 + `46-57` factores 0.75-2.10 + `PacingController:252-268` `remainingMin<=15 && progress<0.5 → base-30`; preview `calculateSessionTimeBreakdown` / `AugeComputation:581` fijos |
| **#6** | MEDIO | roundRest/warmup medio aplicado | Vivo `SetRecorder:453,457` sí roundRest por `targetSetIdx`, pero `474-480` `densityMult` usa `restAfterSuperset` plano no `roundRest`; `461` `WARMUP->baseRest` ignora `warmupSets[i].restBetween`; preview `Calculations:537` sí `?:45` |
| **#3/19** | ALTO | `timeStrategy` muerto | `Session.kt:264` `TimeStrategy?` + `KpknDatabase:418` comentario; `grep timeStrategy main/` solo modelo, 0 productores/consumidores; Room JSON blob `ProgramEntity.data` (`Entities.kt:34`), migración no-op `417-424` |
| **#4/17** | ALTO | `RuleLimits` zombie | `Models.kt:43-50`, `ViewModel:76,227,409,persist:237`, `RulesEngine:111-195` normalizadores + `validateBeforeSave` no-op, `RulesSheet:192-197` `@Suppress UNUSED_PARAMETER` |
| **#11/20** | BAJO | `ExerciseSet.restBetweenSides` muerto | `Session.kt:362` `Int?`, vivo lee `exercise.restBetweenSidesSeconds` (`SetRecorder:452`, `StepRules:248`), grep set-level 0 lecturas |
| **#23** | MEDIO | Budget part/exercise solo guía | `Session.kt:36,117,265`, `Variants.kt:5-66` escritores, vivo solo `Session.targetDurationMinutes` (`Hydrator:274`, `Pacing:252`), `V2Body:590-613` barra, `Pacing:71-88` `75/90/100%` TTS |

**No hallado:** D5 inconsistencia (ya 0-6), ms↔s bug (D9 OK).

---

## 4. Diseño propuesto

### 4.1 Objetivos
- Editar REGLAS no se pierde (draft persistido) sin ensuciar `session.lastModifiedAtMs` ni disparar AUGE en cada keystroke.
- `ruleDefaults` inicial coherente con la sesión real (semilla desde modelo) si no hay draft previo; no pisar personalizaciones; no borrar `restBetweenSides` cuando 0.
- Superset scope por parte no contamina grupos cross-part.
- Previews alineados a vivo dentro de dominio puro (sin `android.*`): `Settings` por inyección, `betweenSides/roundRest/warmup` y rango adaptive documentado.
- Campos zombie deprecar con `@Deprecated` y limpiar `@Suppress`, sin bump Room salvo que se borre clave JSON.

### 4.2 No objetivos
- No enforcement duro de budgets part/exercise (son guías por diseño `PacingController:71-105`).
- No reescribir `VolumeCalculator` fuera de `betweenSides/roundRest`; no tocar `navigation/Navigation.kt`.
- No borrar `timeStrategy`/`RuleLimits` del enum/JSON (mantener `ignoreUnknownKeys` para viejos programas).
- No introducir `android.*` en `domain/` (keep pure Kotlin).

### 4.3 Estrategia por fases (orden prerequisitos)

```
F0 Persistencia draft (C1) — 1h → F1 Semilla + scope + betweenSides (C2/D4/D8) — 2h → F2 Previews + warmup vivo (#6/D1-D3) — 2-3h → F3 Zombie deprecation (#3/#4/#11) — 1h → F4 Docs + tests — 1h
   solo updateUi sin perder               defaults vivos, no pisar       alinear estimado vs real              limpiar @Suppress, @Deprecated
```
F0 desbloquea UX sin tocar modelo; F1 requiere F0 (semilla lee `persistedDraft`); F2 independiente de F1 salvo `betweenSides`; F3 solo deprecation.

---

## 5. Cambios detallados por archivo

### F0 — Persistencia draft sin tocar sesión (C1, 1h, sin migración)

**A. `screens/sessioneditor/SessionEditorViewModelStructure.kt:607-668,684-713` — C1**
- Extraer helper `private fun persistRuleDraft() { viewModelScope.launch(Dispatchers.IO) { persistDraft(_uiState.value) } }` o `updateUi+ persistDraft immediate` (solo `SharedPreferences` `draftPrefs.edit().putString`, sin `repository.upsertSessionInProgram` para no marcar `lastModifiedAtMs`).
- Tras `updateUi` en `patchRuleDefaults` (`:611`), `updateRuleDefaults` (`:635` y `662-665`), `updateRuleLimits` (`:685`), `updateAdvancedRuleLimits` (`:702`) añadir:
  ```kotlin
  // no hasUnsavedChanges sobre session, pero sí sobre draft
  _uiState.update { it.copy(hasUnsavedChanges = true) } // opcional: vs originalSession ? draft != persisted ?
  viewModelScope.launch(Dispatchers.IO) { persistDraft(_uiState.value) }
  // scheduleAutoSave() opcional debounce 2s ya existente, pero persistDraft directo evita pérdida en onPause
  ```
- Alternativa sin `hasUnsavedChanges`: solo `persistDraft` inmediato; `saveDraftForExit:281` ya `persistRecoverableSession` pero necesita `ruleDefaults` ya en `_uiState`. No llamar `updateSession` ni `scheduleAugeRecalc`.
- También en `patchRuleDefaults` compuesto `compoundReps/rest/rpe/intensity` (`Structure.kt` vía `patchRuleDefaults` lambdas `RulesSheet.kt:300-500`) cubierto por mismo helper.

**B. `screens/sessioneditor/components/sheets/RulesSheet.kt:192-197` — no tocar en F0** (solo F3).

### F1 — Semilla + No-pisar + Scope (C2/D4/D8 + betweenSides, 2h)

**C. `screens/sessioneditor/SessionEditorViewModel.kt:367-410` `loadSessionInternal` — C2/D4 seed**
```kotlin
val resolvedRuleDefaults: SessionEditorRuleDefaults = persistedDraft?.ruleDefaults ?: run {
    // inferir desde draft (sesión más reciente) si existe
    val exercises = draft.allExercises()
    if (exercises.isEmpty()) SessionEditorRuleDefaults() else {
        val medianRest = exercises.mapNotNull { it.restTime }.sorted().let { if (it.isEmpty()) 90 else it[it.size/2] }
        val medianSide = exercises.mapNotNull { it.restBetweenSidesSeconds }.sorted().let { it.getOrNull(it.size/2) ?: 0 }
        val avgSets = exercises.map { it.sets.size }.average().roundToInt().coerceIn(1,6)
        val avgReps = exercises.flatMap { it.sets }.mapNotNull { it.targetReps }.average().roundToInt().coerceIn(1,30)
        val avgRpe = exercises.flatMap { it.sets }.mapNotNull { it.targetRPE }.average().takeIf { it.isFinite() } ?: 8.0
        SessionEditorRuleDefaults(
            setCount = avgSets, reps = avgReps, rpe = avgRpe.coerceIn(1.0,10.0),
            normalRestSeconds = medianRest.coerceIn(0,600),
            betweenSidesRestSeconds = medianSide.coerceIn(0,300),
            supersetBetweenRestSeconds = draft.allSupersetGroups().map { it.restBetweenExercises }.average().takeIf { it.isFinite() }?.roundToInt() ?: 60,
            supersetRoundRestSeconds = draft.allSupersetGroups().map { it.restAfterSuperset }.average().takeIf { it.isFinite() }?.roundToInt() ?: 120,
        )
    }
}
```
- Mantener `persistedDraft` autoridad si existe; no escribir en draft hasta primer edit F0. Fallback `SessionEditorRuleDefaults()` si sesión vacía. No tocar `Navigation.kt:309` (switch ya respeta draft).
- Helper `medianOrDefault` puro en `domain/sessionassistant` para test.

**D. `screens/sessioneditor/SessionEditorRulesEngine.kt:32-34,83-84,91-106` — no borrar betweenSides + scope**
```kotlin
// 92: antes takeIf>0 borraba unilateral con 0 (regla genérica)
restBetweenSidesSeconds = if (safeSideRest > 0) safeSideRest else this.restBetweenSidesSeconds // preservar existente si 0
// o alternativa: safeSideRest.takeIf{it>0} ?: this.restBetweenSidesSeconds (mantener 30 si regla 0)
// para set objetivo: si ejercicio ya unilateral con 30 y regla 0, no pisar
```
- Scope fix `96-106`:
```kotlin
val updatedGroups = session.allSupersetGroups().map { group ->
    // solo si TODO el grupo contenido en scope (no cruza partes)
    if (group.exerciseOrder.all { it in scopedExerciseIds }) group.copy(...)
    else if (group.exerciseOrder.none { it in scopedExerciseIds }) group
    else group // cruza partes → no tocar (o log)
}
```
- Mantener `RIR 0-6` (ya ok) — añadir comentario `RIR 0-6 parity with applyGlobalIntensityAdjustment:221`.

**E. `screens/sessioneditor/SessionEditorSessionHelpers.kt:71-122` `withSessionEditorDefaults` — revisión**
- Ya `takeIf>0` para `restBetweenSides` no borra si no unilateral; verificar que `applyToNewItems` gate sigue.

### F2 — Previews alineados + warmup vivo (#6/D1-D3, 2-3h, flag AUGE)

**F. `domain/calculations/Calculations.kt:498-620,537` — D1/D2**
- Añadir param `restTimerDefaultSeconds: Int = 90` a `calculateSessionTimeBreakdown` y `estimateSessionDurationMinutes` (mantener default 90 para tests JVM sin Settings):
  ```kotlin
  fun calculateSessionTimeBreakdown(exercises: List<Exercise>, supersetGroups: List<SupersetGroup>, sessionWarmup: List<WarmupSet>, restTimerDefaultSeconds: Int = 90): SessionTimeBreakdown
  // 592: exercise.restTime ?: restTimerDefaultSeconds
  // 537: warmupSets ya ?:45 correcto
  // entre lados: if (exercise.isEffectivelyUnilateral() && (exercise.restBetweenSidesSeconds ?:0) > 0) warmupSec? no, restSec += exercise.restBetweenSidesSeconds!! * (sets.size) // 1 por set paired
  // roundRest: for roundIdx in 0 until rounds) restSec += group.roundRestBetweenExercises?.get(roundIdx) ?: group.restBetweenExercises
  ```
- En `SessionEditorAugeComputation.kt:581,645,674` y `TimeCoachEngine.kt:186` pasar `settings.restTimerDefaultSeconds` (`repository.settings.value.restTimerDefaultSeconds` ya disponible en `SessionEditorViewModel:584` y `SessionAssistantEngine` input).
- Actualizar `SessionEditorViewModel:574` `averageRest` y `TimeCoachEngine.apply` respects.

**G. `screens/sessioneditor/SessionEditorAugeComputation.kt:331-367,581,645` — D2/D3**
- Pasar `restTimerDefaultSeconds` y sumar `restBetweenSidesSeconds` y `roundRest*` idem `Calculations`.
- Documentar que `densityMultiplier` usa `restAfterSuperset` plano → pasar `roundRestAfterSuperset?.get(idx)` si disponible (ver #6).

**H. `screens/workout/WorkoutSetRecorder.kt:452,461,474-480` — #6 warmup + density**
```kotlin
RestTimerKind.WARMUP -> {
    // 461: warmup: intent-distinct por setIdx
    val warmupRest = exercise.warmupSets.getOrNull(targetSetIdx)?.restBetween ?: baseRest
    warmupRest
}
// 474: densityMult para superset round: supersetGroup?.roundRestAfterSuperset?.get(targetSetIdx) ?: supersetGroup?.restAfterSuperset
```

**I. `domain/sessionassistant/TimeCoachEngine.kt:139-145` — no cambio** (respeta superset), pero `generate` ahora con `calculateSessionTimeBreakdown(restTimerDefaultSeconds=...)` correcto.

### F3 — Zombie deprecation (#3/#4/#11, 1h)

**J. `data/models/Session.kt:264,289,362,375` — deprecation sin migración**
```kotlin
@Deprecated("Sin consumidor en vivo. Usa TrainingMode.TIME + ExerciseSet.targetDuration.", level = DeprecationLevel.WARNING)
val timeStrategy: TimeStrategy? = null
@Deprecated("Usa Exercise.restBetweenSidesSeconds", level = DeprecationLevel.WARNING)
val restBetweenSides: Int? = null // en ExerciseSet:362
// WarmupSetDefinition.restBetween: mantener (preview sí), añadir KDoc: "Preview usa, vivo usa baseRest hasta F2-H"
```

**K. `screens/sessioneditor/SessionEditorModels.kt:43-50` + `SessionEditorViewModel.kt:76,227,409` + `components/sheets/RulesSheet.kt:192-197`**
- `@Deprecated("Subsistema legacy: limits guardados en draft local, validateBeforeSave no-op. No bloquean guardado.", level=WARNING)` sobre `SessionEditorRuleLimits`.
- Limpiar `@Suppress("UNUSED_PARAMETER")` y documentar `// legacy no-op via RulesEngine.validateBeforeSave:184`.
- Mantener `PersistedSessionEditorDraft.ruleLimits` decode (`Json ignoreUnknownKeys`) para compat programas viejos.

**L. `screens/sessioneditor/components/sheets/RulesSheet.kt:262-266` picker techo 23:59** — BAJO: añadir `coerceIn(0,600)` comentario o `MAX_REST=600` clamp ya en `RulesEngine:32`.

### F4 — Tests + docs (1h)

**M. Tests unit `app/src/test`**
- `SessionEditorRulesEngineTest` add: `applyDefaults_doesNotEraseBetweenSidesWhenZero`, `applyDefaults_scopePart_doesNotRewriteCrossPartSuperset`, `applyDefaults_RIR6Preserved`.
- Nuevo `SessionEditorRuleDefaultsSeedTest` — `seed_from_session_medianRest_setsReps`.
- Nuevo `PersistedDraftRulesTest` — `patchRuleDefaults_persistsToDraftWithoutSessionWrite`.
- `CalculationsTest` — `calculateSessionTimeBreakdown_usesSettingsDefault_andBetweenSidesAndRoundRest`.
- `WorkoutSetRecorderTest` — `warmup_restBetween_used_notBaseRest`.

**N. Docs:** `reglas-y-tiempo.md` hallazgos 1,2,4,8,10,13 marcados mitigados; `ANDROID_ARCHITECTURE_MAP.md` sección `SessionEditor` persist draft + seed.

---

## 6. Impacto por plataforma y banderas

| Plataforma | Impacto | Detalle |
|---|---|---|
| **Android** | **Sí — directo** | `screens/sessioneditor/*` (F0-F3), `data/models/Session.kt` (deprecate), `domain/calculations/*` (F2), `screens/workout/WorkoutSetRecorder.kt` (F2 warmup), `domain/sessionassistant/TimeCoachEngine.kt` (F2). No `navigation/Navigation.kt`. |
| **iOS** | **Parcial** | `ios-native/... Calculations.swift` mismo 90 hardcode (`estimateSessionDurationMinutes`) y `Warmup` warmupRest; replicar `restTimerDefaultSeconds` + `betweenSides/roundRest`. `timeStrategy` Swift `Session.swift:668` igual muerto → deprecate. |
| **Backend** | **No** | `backend/engines/` no lee `timeStrategy` ni `restBetweenSides` por set; no ajuste. |

**Banderas:**

| Bandera | Afectada | Valor |
|---|---|---|
| **Room** | **No (F0-F2)** | `ProgramEntity.data` JSON `ignoreUnknownKeys`; fields dentro blob. Deprecate sin bump. Solo si se BORRA `timeStrategy` del enum → bump `version 21` + migración no-op `KpknDatabase.kt:424` (decode limpia). |
| **AUGE** | **No** | Preview drain no toca `AugeFatigueEngine` en este plan (ya `sideScale`). `TimeCoachEngine` solo preview. |
| **Voz** | **No** | `voice-engine` no afectado (solo `RestTimerKind.WARMUP`). |

---

## 7. Pruebas a ejecutar

### 7.1 Unit (JVM, `android-native/`, `testBaseDebugUnitTest`)

- **F0:** `PersistedDraftRulesTest.patchRuleDefaults_persists` — `updateRuleDefaults(normalRest=120)` tras `loadSessionInternal` sin `updateSession` → `draftPrefs.getString(key)` contiene `normalRest=120` y `hasUnsavedChanges=true`.
- **F1:** `SessionEditorRulesEngineTest.applyDefaults_doesNotEraseBetweenSidesWhenZero` — ejercicio `restBetweenSides=30` + defaults `betweenSides=0` → `restBetweenSides=30` (no null).
- **F1:** `SessionEditorRulesEngineTest.applyDefaults_scopePart_doesNotRewriteCrossPartSuperset` — grupo `[A(parte1),B(parte2)]` `restBetween=30` → `applyDefaults(partId=parte1, safeBetween=60)` → `B` sigue 30.
- **F1:** `SessionEditorRuleDefaultsSeedTest.seed_from_session` — sesión con rests `75,90,120` → `ruleDefaults.normalRestSeconds=90` mediana.
- **F1 D5 regresión:** `SessionEditorRulesEngineTest.RIR6` ya existente verde.
- **F2:** `CalculationsTest` — `restTimerDefaultSeconds=45` vs 90, `betweenSides 30×3 sets = +90s`, `roundRest` por ronda 60 vs 90.
- **F2:** `WorkoutSetRecorderTest.warmupRestBetween` — `warmupSets[0].restBetween=90` `baseRest=60` → `plannedRest=90`.

Comandos:
```bash
gradlew.bat testBaseDebugUnitTest --tests "*SessionEditorRulesEngineTest*"
gradlew.bat testBaseDebugUnitTest --tests "*PersistedDraftRulesTest*"
gradlew.bat testBaseDebugUnitTest --tests "*CalculationsTest*"
gradlew.bat testBaseDebugUnitTest --tests "*WorkoutSetRecorderTest*warmup*"
gradlew.bat testBaseDebugUnitTest --tests "*SessionEditorRuleDefaultsSeedTest*"
```

### 7.2 Instrumented
- Abrir REGLAS → cambiar rest → cerrar sin Aplicar → reabrir → valor persiste (F0).
- Sesión con unilateral betweenSides 30 → preview muestra +30s por set (F2 D2).

### 7.3 Build
- `gradlew.bat compileBaseDebugKotlin --offline` (wiring puro, sin `android.*` en `domain/`).
- `gradlew.bat assembleBaseDebug --offline` QA final.
- `gradlew.bat testBaseDebugUnitTest` completo si tiempo.

---

## 8. Documentación a actualizar

- `docs/audits/2026-08-editor-sesiones/reglas-y-tiempo.md` §6 hallazgos 1,2,8,10 → mitigados con links a `ViewModelStructure:607` + `ViewModel:407` + `RulesEngine:92,102`.
- `docs/ARCHITECTURE.md` / `docs/ANDROID_ARCHITECTURE_MAP.md`: aclarar dos capas reglas (draft vs materializado), seed, scope superset.
- `docs/ANDROID_UI_SCREENS_MAP.md`: REGLAS sheet — persist draft + Aplicar vs semilla.
- `app/schemas/` no cambia (JSON blob).
- `.opencode/kpkn-map.md`: regenerar si se añade `SessionEditorRuleDefaultsSeedTest`.
- `.opencode/memory/MEMORY.md`: anotar decisión `timeStrategy` deprecate, `RuleLimits` legacy.

> Código y esquema Room v20 son autoridad si docs dicen v19.

---

## 9. Riesgos y mitigaciones

| Riesgo | Prob | Impacto | Mitigación |
|---|---|---|---|
| F0 `persistDraft` en cada keystroke inunda `SharedPreferences` IO | Alta | Jank | Debounce 300ms o `launch(IO)` inmediato pero `apply()` async es barato; no `upsertSessionInProgram` en F0; medir `StrictMode`. |
| `hasUnsavedChanges=true` por solo REGLAS confunde “cambios sin guardar” aunque session == original | Media | UX ruido | Calcular `hasUnsavedChanges = (session != original) \|\| (ruleDefaults != persistedDraft?.ruleDefaults)` o solo draft dirty flag separado; Fase 0 puede no setearlo y solo persistDraft. |
| Semilla mediana con sesión vacía o 1 ejercicio → default 90 vs custom Settings | Baja | Preview | Fallback `SessionEditorRuleDefaults()` + `Settings.restTimerDefaultSeconds` como mediana si lista vacía. |
| F1 `safeSideRest>0` preservar vs usuario quiere borrar descanso entre lados (poner 0) | Media | No puede borrar | Añadir UI “Sin descanso entre lados” explícito: `0` + toggle `hasBetweenSides`; preservación solo si `exercise.isEffectivelyUnilateral()` y regla 0 → mantener; si quiere borrar, usar editor card `null`. Documentar. |
| D8 fix `all` vs `none` cambia comportamiento histórico para superset intra-parte → tests fallan | Baja | Regress | Añadir test cross-part y intra-parte ambos; `all` preserva intra-parte, bloquea cross-part. |
| F2 `restTimerDefaultSeconds` param default 90 rompe tests JVM que no pasan Settings | Baja | Build | Default 90 mantiene verde; nuevos tests pasan 45. |
| Vivo warmup `restBetween` null → fallback `baseRest` correcto, pero `warmupSets` null list → NPE | Baja | Crash | `getOrNull` + `?: baseRest`. |
| Deprecate `timeStrategy` con `WARNING` rompe `allWarningsAsErrors` | Baja | Build | Usar `DeprecationLevel.WARNING` no `ERROR`; `serialName` permanece. |

---

## 10. Criterios de aceptación

- [ ] Editar REGLAS (normalRest, betweenSides, superset) sin “Aplicar” → cerrar editor → reabrir → valores persisten (draft `SharedPreferences`).
- [ ] `loadSessionInternal` sin draft → `ruleDefaults.normalRest` = mediana `exercise.restTime` de la sesión (no 90 fijo).
- [ ] `applyDefaults` con `betweenSides=0` sobre ejercicio `restBetweenSides=30` → sigue `30` (no null).
- [ ] `applyDefaults` scope parte no reescribe `SupersetGroup` que cruza a otra parte.
- [ ] `Calculations.calculateSessionTimeBreakdown(restTimerDefaultSeconds=45)` usa 45 no 90; suma `betweenSides` y `roundRest` por ronda.
- [ ] `WorkoutSetRecorder` `WARMUP` usa `warmupSets[setIdx].restBetween` si no null, no `baseRest`.
- [ ] `Exercise.timeStrategy` y `ExerciseSet.restBetweenSides` anotados `@Deprecated` y `RulesSheet` `@Suppress` limpio con comentario legacy.
- [ ] `compileBaseDebugKotlin --offline` + `testBaseDebugUnitTest` (nuevos tests) verdes.

---

## 11. Plan de entrega (requiere aprobación)

1. **Aprobación explícita** de este plan (pipeline `request_approval` → `construction`). No editar código hasta `pipeline.start`.
2. `constructor_kpkn` ejecuta por fases en rama corta, commits atómicos:
   - F0: `SessionEditorViewModelStructure.kt` persist helper + `SessionEditorViewModel.kt` `persistDraft` wiring
   - F1: `SessionEditorViewModel.kt` seed + `SessionEditorRulesEngine.kt` `92,102-106` + `SessionEditorSessionHelpers.kt`
   - F2: `domain/calculations/Calculations.kt` + `SessionEditorAugeComputation.kt` + `WorkoutSetRecorder.kt:461` + `domain/sessionassistant/TimeCoachEngine.kt`
   - F3: `data/models/Session.kt` deprecate + `SessionEditorModels.kt` + `RulesSheet.kt` `@Suppress`
   - F4: tests + docs
3. `gradlew.bat compileBaseDebugKotlin --offline` → `testBaseDebugUnitTest --tests "*SessionEditorRules*"` → `assembleBaseDebug`.
4. Auditor revisa diff vs plan; `pipeline submit_audit` → `auditing`.

---

## 12. Alternativas descartadas

- **Persistir REGLAS vía `updateSession` directo:** marcaría `lastModifiedAtMs` y `scheduleAugeRecalc` + `upsertSessionInProgram` (Room) en cada keystroke → ruido + AUGE recalc innecesario. Mejor solo `persistDraft` (SharedPreferences) en F0.
- **Seed desde `Settings.restTimerDefaultSeconds` solo:** no refleja personalización por ejercicio; mediana de sesión es más fiel que global.
- **Borrar `timeStrategy` del modelo + bump Room v21:** rompería decode de programas históricos con `timeStrategy=FREE` (`ignoreUnknownKeys` lo tolera pero pierde datos); deprecate es reversible.
- **Cablear `RuleLimits` (`validateBeforeSave` real + sliders):** requiere spec producto de límites rígidos/flexibles; hoy `@Suppress` es intencional `no-op` — dejar zombie documentado.
- **Alinear preview adaptive/pace exactamente a vivo:** preview es dominio puro sin `Settings`/`progress`; simular `WorkoutAdaptiveRest.compute` promedio por sesión añade complejidad; mejor documentar rango “estimado sin adaptive” o añadir nota UI “estimado base”.
- **Eliminar `ExerciseSet.restBetweenSides` del JSON:** mismo caso que `timeStrategy` — deprecate sin borrar mantiene compat `Json encodeDefaults`.

---

## 13. Referencias exactas (para auditor)

- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorModels.kt:12-21,43-50,149-195`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorRulesEngine.kt:23-109,111-195,244-274`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModelStructure.kt:594-605,607-668,684-713,118-121`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModel.kt:66-80,128-137,174-186,215-264,355-410,527-541,807-836`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModelVariants.kt:5-66`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/sheets/RulesSheet.kt:172-271,766-786,192-197,948-1038`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModelNavigation.kt:307-309`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorScreen.kt:766-767,835,899`
- `android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt:36,65-75,117,224,252,264-265,289,316-364,375`
- `android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt:19-20`
- `android-native/app/src/main/java/com/example/kpkn/data/db/KpknDatabase.kt:20,417-424`
- `android-native/app/src/main/java/com/example/kpkn/domain/calculations/Calculations.kt:498-620,537,592,600-606`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorAugeComputation.kt:574-674`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutSetRecorder.kt:438-464,461,474-480,507-528,543-551`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutStepRules.kt:206-253,248`
- `android-native/app/src/main/java/com/example/kpkn/domain/workout/SupersetRules.kt:13-113`
- `android-native/app/src/main/java/com/example/kpkn/domain/sessionassistant/TimeCoachEngine.kt:139-161,418-479`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutPacingController.kt:71-105,252-268`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutSessionHydrator.kt:274-275,374-379`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutV2Body.kt:589-650`
- `android-native/app/src/test/.../screens/sessioneditor/SessionEditorRulesEngineTest.kt:39-205`

> **Siguiente paso:** aprobar este plan para pasar a `construction`. No se editará código de producto hasta `pipeline.start` + `request_approval` confirmados. Código y esquema Room v20 son autoridad si docs dicen v19.
