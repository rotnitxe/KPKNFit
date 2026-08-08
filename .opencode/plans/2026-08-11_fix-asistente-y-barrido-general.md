# Plan — Asistente y barrido general (auditoría 5)

**Fecha:** 2026-08-07  
**Autor:** orquestador (muse-spark-1.2)  
**Estado:** `pending_approval` (no editar código de producto hasta aprobación explícita)  
**Auditoría fuente:** `docs/audits/2026-08-editor-sesiones/asistente-y-bugs-generales.md` (270 líneas, 2026-08, alcance `screens/sessioneditor/*` + `domain/sessionassistant/*` + `services/workout/*` + `screens/workout/*` + `domain/templates/*`)  
**Solicitud:** solucionar lo cubierto por auditoría 5 paso a paso, última del bloque editor-sesiones

---

## 1. Resumen ejecutivo

Auditoría dual con 14 hallazgos verificados por 3 investigadores paralelos (Room v20 autoridad `KpknDatabase.kt:20` vs doc v19 obsoleto):

- **Frente A — Asistente:** ruta viva **sí muta modelo** (`AssistantSuggestionsTab` → `applyAssistantSuggestion` → `updateSession` → autosave/AUGE, `AugeActions.kt:41-74,183-346`), pero el engine fue recortado intencionalmente (`SessionAssistantEngine.kt:57-72` solo `ajustes` + `timeAjustes`, test `EngineTest:19` lo certifica) dejando ~470 líneas muertas (`detectarRiesgos` 359-654, `generarAjustes` 658-716, `generarOportunidades` 778-810, `generarTarjetasFantasma` 814-883, `buscarPlantillasCompatibles` 893-941), handlers huérfanos en 3 capas (`AssistantSheet:147-159` descarta `@Suppress`, `Screen:706-723/810-827` snackbars muertos, `SessionEditorSheets:367-369` params nunca usados + `return` early), supersets solapados `A4b` (`Engine:100-112` ex₀-ex₁ y ex₁-ex₂ en misma sugerencia + `AugeActions:133-139` chain sin revalidar), pacing dedupe `A5` (`PacingController:260` raw `size` vs `201` dedupe), y **CRÍTICO A1** `SessionEditorDebugLog.kt:21-86` logger con red `127.0.0.1:7803` + disco `/sdcard/Download/debug-9ba5f2.log` + host path `C:/Users/valen/...` sin `BuildConfig.DEBUG`, disparado en `AssistantSheet:291-305` + `HistorySheet:70-84` + `RulesSheet:706-783` + 4 tests, con 3 logs commiteados (`android-native/app/debug-9ba5f2.log` etc., `git ls-files --cached`).

- **Frente B — Barrido:** **B1 ALTO** variantes B/C/D rotas end-to-end: `SessionEditorScreen:147` siempre `uiState.session` (A), `activeVariantSession:233-238` sin consumidor salvo `commit` no-op (`Variants:141-159` `sessionB = sessionB`), `switchVariant:133` solo flag, `createVariant:87-91` clona `base.copy(id=UUID)` sin regenerar `part/exercise/set/warmup/superset` ids (vs clonador `CloneHelpers:97-108` sí regenera) → ids duplicados A↔B en mismo `Session` persistido. **B2 ALTO** `applyTemplateInternal:96-120` doble apply: `launch { apply }` sin `return` + fallback síncrono ejecuta ambos (IDs distintos por `SessionTemplateEngine:55-79` cloneUUID), doble autosave/AUGE. **B4 MEDIO** `Navigation:48-63,78-84` `persistRecoverableSession` ( `encodeToString` ) en hilo UI (vs `scheduleAutoSave:128 IO`). **B5 MEDIO** código muerto `Navigation:425-455` `appendDraftSnapshot/buildDraftSnapshot` 0 callers + dedupe `==` con `lastModifiedAtMs` + `CoverClone:75-100` wrappers deprecated. **B6 MEDIO** `CloneHelpers:150-164` `REPLACE` `identity.copy(... payload)` conserva `sessionB/C/D` viejas desincronizadas. **B7 MEDIO** `Navigation:496-524` `applySessionToMesocycle` `cloneForWeek = draft.copy(id=UUID)` solo sesión id, inner ids duplicados entre semanas + matching `dayOfWeek+isMainSession`. **B3 N/A** filtro warmup `>1` ya no existe en `HEAD` (613 líneas, 0 hits). **B8 BAJO** `Variants:5-17` `setTargetDuration` `updateUi` bypass `lastModifiedAtMs` + `dismissedTimeCoachIds` reset, `Structure:110-124` `toggleSelection` autosave innecesario.

---

## 2. Contexto y reproducción

- **A1 repro:** abrir Asistente → click header "Volumen de entreno" `AssistantSheet:319` → `SessionEditorDebugLog.log(H-A, post-fix)` → `File("/sdcard/Download/debug-9ba5f2.log").appendText` + `Thread { POST http://10.0.2.2:7803/ingest/... }` aunque `BuildConfig.DEBUG==false`.
- **A3 repro:** abrir `Sheet.AUGE` con `sessionDrain` alto → `AssistantSheet` recibe `onApplyAugeCorrection` y `onAddGhostExercise` pero `147-159` los suprime → botones nunca renderizados → snackbars `Screen:706/712` jamás visibles.
- **A4b repro:** sesión 3 ejercicios sueltos A,B,C + `targetDurationMinutes` bajo → `buildTimeSuggestions` genera `time_overage` con `details = [A-B, B-C]` ambos `defaultAccepted=false` → marcar ambos → `applyAssistantSuggestion` chain: `createSuperset(A-B)` ok, `createSuperset(B-C)` sobre `next` donde B ya agrupado → grupo incoherente.
- **A5 repro:** unilateral paired 3 sets (6 lados L/R) → `evaluatePace:201` `distinct` 3 sets → `progress 0.5`, `adjustRestTimeForPace:260` `size=6` → `progress 1.0` → `needsHurry` falso tarde.
- **B1 repro:** crear variante B (`Variant.B,Rápida`) → `Variants:87` `copy(id=UUID, name)` sin new exercise ids → abrir B vía `SessionContextNavigator:351` → `Screen:147` ve A → editar peso en B → `updateSession` muta A → B congelada.
- **B2 repro:** sesión vacía → seleccionar plantilla → `selectTemplate→applyTemplateInternal(REPLACE)` → cae en `launch` + fallback → logcat 2× `applyTemplate` + 2× `scheduleAutoSave` (2000ms) + `weeklyMetricsCache` invalidado 2×.
- **B6 repro:** día con variante B (Rápida) → clonar otra sesión encima con `REPLACE` → A reemplazado pero `sessionB` sigue con ejercicios viejos de destino.
- **B7 repro:** aplicar sesión a mesociclo (propagar) → semanas destino comparten `Exercise.id` con origen → `draggingExerciseId` colisiona al editar en otra semana.

---

## 3. Hallazgos verificados (archivo:línea actual, `android-native/app/src/main/java/com/example/kpkn/`)

| # | Sev | Hallazgo | Evidencia |
|---|---|---|---|
| **A1** | CRÍTICO | Debug logger red+disco en prod | `SessionEditorDebugLog.kt:18-36,60-86` `INGEST`/`INGEST_EMU`/`hostLogPaths` `C:/Users/valen...`/`deviceLogPaths` + 6 call-sites `AssistantSheet:291-305` H-A, `HistorySheet:70-84` H-C, `RulesSheet:706-720,727-741,770-783` H-B/E + `SessionEditorAuditDebugTest:24,60,106,138` + 3 logs trackeados `debug-9ba5f2.log` |
| **A2** | ALTO | `evaluate()` recortado, 470 líneas muertas | `SessionAssistantEngine.kt:57-72` fijos `OPTIMAL/0/empty` solo `ajustes+timeAjustes` vivos `718-776`/`83-155` + muertas 359-654 riesgos, 658-716 `generarAjustes`, 778-810 `generarOportunidades`, 814-883 `generarTarjetasFantasma`, 885 `estimarImpacto`, 893-941 `buscarPlantillasCompatibles` + test `EngineTest:19` intencional + iOS parity `Engine.swift:43,608` |
| **A3** | ALTO | Handlers 3 capas muertos | `AssistantSheet.kt:147-159` `@Suppress UNUSED_EXPRESSION` descarta 2 callbacks; `Screen.kt:700-729` `AssistantGlassOverlay` + `810-827` `SessionEditorSheets` duplican snackbar `onApplyAugeCorrection/onAddGhostExercise` nunca invocados; `SessionEditorSheets.kt:263,367-369,413-418` params muertos + `early return` si `Sheet.AUGE` |
| **A4a** | BAJO | `when type else Unit` silencioso | `SessionEditorViewModelAugeActions.kt:143-181` `APPLY_TEMPLATE/ADD_GHOST/KEEP/BLOCK_ADD` `41-53` hoy inalcanzables (solo emitidos por muertos A2) |
| **A4b** | MEDIO | Supersets solapados sin revalidar | `SessionAssistantEngine.kt:100-112` `MAX_SUPERSET_SUGGESTIONS=2` loop `i in 0 until size-1` genera A-B y B-C solapados en misma sugerencia; `AugeActions.kt:133-139` chain `details.forEach { next=applyAssistantDetail(next,it) }` sin check `supersetGroupRef==null` |
| **A5** | MEDIO/BAJO | Pacing dedupe unilateral inconsistente | `WorkoutPacingController.kt:201-206` `uniqueCompletedSets distinct` vs `252-263` `completedSets.size` raw + `adjustRestTimeForPace:260` sin dedupe, `WorkflowPacingNotificationManager` ok |
| **B1** | ALTO | Variantes B/C/D rotas + ids duplicados | `SessionEditorScreen.kt:147` siempre `session` A; `Models.kt:233-238` `activeVariantSession` muerto; `Variants.kt:133-135` `switchVariant` solo flag; `141-159` `commit` B→B no-op; `76-108` `createVariant` solo `session.id=UUID` sin `cloneExerciseForTransfer:92-108` vs `CloneHelpers` sí; `Variants.kt:5-17` `setTargetDuration` bypass `lastModifiedAtMs` |
| **B2** | ALTO | Doble `applyTemplateInternal` | `ViewModelTemplates.kt:96-120` `launch { apply } return@launch` + fallback `109-119` sin `return` → siempre 2× apply, 2× `updateSession`/`hasUnsavedChanges`/`scheduleAutoSave`/`scheduleAugeRecalc`; `SessionTemplateEngine:55-79` nuevo UUID cada vez |
| **B3** | — | Warmup `>1` | **N/A en HEAD**: `ClonerSaveWarmupSheets.kt:428` no existe (0 hits `exercisesWithWarmup`), `WarmupSheet:508-610` soporta 1 set vía `ifEmpty` + `onSave→updateWarmupSets:592` |
| **B4** | MEDIO | `persistRecoverableSession` en UI | `Navigation.kt:48-63` `requestSessionSwitch`, `78-84` `createSessionForDay`, `123-129` `createCompetitionSessionForDay` llaman `persistRecoverableSession` (`ViewModel:215-249` `encodeToString`+`putString`) en main; `scheduleAutoSave:128` sí `Dispatchers.IO` |
| **B5** | MEDIO | Código muerto + dedupe roto | `Navigation.kt:425-432` `appendDraftSnapshot` + `434-455` `buildDraftSnapshot` 0 callers (vs `TrainedSessionVersionStore:39-70` correcto `sessionForVersioning:92-106` que zero `lastModifiedAtMs`); `CoverClone:75-94` `@Deprecated exportToSession/importLegacy` 0 callers; `Templates:74-76,99-101` `size>12 Log.w` sin efecto |
| **B6** | MEDIO | REPLACE conserva B/C/D viejas | `CloneHelpers.kt:136-164` `preserveIdentityFrom.copy(exercises=payload.looseExercises, parts=...)` mantiene `sessionB/C/D`, `trainingBackup`, `isMeetDay/isCompetition*` del destino desincronizadas |
| **B7** | MEDIO | Propagación mesociclo solo id sesión | `Navigation.kt:496-524` `cloneForWeek = if(week==state.week) draft else draft.copy(id=UUID)` solo sesión id, `Part/Exercise/Set/Warmup/SupersetGroup` ids duplicados entre semanas + matching `dayOfWeek+isMainSession:509` frágil |
| **B8** | BAJO | Estado menor | `Variants:5-17` `setTargetDuration` `updateUi` sin `lastModifiedAtMs`/`dayOfWeek` + `dismissedTimeCoachIds=emptySet` re-sirve; `Structure:110-124` `toggleSelection` `scheduleAutoSave` innecesario; `Variants:76-108` `create/deleteVariant` `updateUi` sin timestamp |

Severidad audit confirmada salvo B3 (histórico, hoy N/A).

---

## 4. Diseño propuesto

### 4.1 Objetivos
- Eliminar fuga debug prod (A1) y binario de 470 líneas muertas/params muertos (A2/A3) sin romper `EEvalu...` vivo (`ajustes`+`time`).
- Supersets sugeridos non-overlapping + revalidación (A4b) y pacing dedupe unilateral (A5).
- Variantes B/C/D o se arreglan (ids regenerados + `activeVariantSession` wiring + `updateActiveVariantSession`) o se ocultan (A/B1 blocker).
- Template apply single-shot (B2), persist navegacional en `IO` (B4), limpiar código muerto (B5), REPLACE sin B/C/D viejas (B6), deep-clone mesociclo (B7), alinear `setTargetDuration`/selection (B8).
- No tocar `domain/auge` fórmulas ni `navigation/Navigation.kt` ruta.

### 4.2 No objetivos
- No re-implementar reporte completo `riesgos/veredicto/score/ghost/plantillas` (A2 reconectar = feature nueva, fuera de fix bug).
- No introducir `android.*` en `domain/`; keep `domain/sessionassistant` puro.
- No bump Room v20 (variantes son JSON en `Session`).

### 4.3 Estrategia por fases

```
F0 CRÍTICO A1 (15m) → F1 ALTO B1+B2+A2/A3 (2-3h) → F2 MEDIO A4b+A5+B4+B6+B7+B5 (2h) → F3 BAJO B8 (30m) → F4 tests/docs (30m)
   borrar logger+logs+call-sites          variantes+template+limpieza dead handlers      superset overlap+pacing+IO+clone fixes          estado menor
```

F0 independiente y debe ir primero (seguridad). F1 bloquea F2 para variantes (B6/B7 dependen de regen).

---

## 5. Cambios detallados por archivo

### F0 — CRÍTICO A1 debug logger (15m, sin migración)

**A. Borrar `SessionEditorDebugLog.kt` + logs trackeados**
- `git rm android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorDebugLog.kt` (97 líneas)
- `git rm --cached debug-9ba5f2.log android-native/debug-9ba5f2.log android-native/app/debug-9ba5f2.log` + `rm` working tree si existen (`android-native/build-*.log` son artefactos build, ya gitignore? verificar)
- `.gitignore` añadir `debug-9ba5f2.log` + `**/debug-9ba5f2.log`
- Verificar que `SessionEditorAuditDebugTest.kt` era solo probe para este logger: **borrar archivo completo** `app/src/test/java/com/example/kpkn/screens/sessioneditor/SessionEditorAuditDebugTest.kt` (4 tests H-B/C/D/E) o al menos sus 4 `SessionEditorDebugLog.log` calls si el test cubre otra cosa.

**B. Gut call-sites (`#region agent log`)**
- `AssistantSheet.kt:291-305` borrar bloque `toggleVolumeExpanded` log (8 líneas `#region`/`#endregion` + `SessionEditorDebugLog.log` call)
- `HistorySheet.kt:70-84` borrar `H-C` log en `forEachIndexed` filas historial
- `RulesSheet.kt:706-720,727-741,770-783` borrar 3 bloques `H-E/H-B` surrounding `applyTemplate` y `onApply`
- Buscar global `SessionEditorDebugLog` → 0 hits tras F0.

### F1 — ALTO B1+B2+A2/A3 (2-3h)

**C. `SessionEditorViewModelVariants.kt:76-108,133-159,5-17` — B1 variantes**
- **Regenerar ids en `createVariant`** (usar clonador existente `SessionTemplateEngine.cloneSessionContent` o `SessionEditorCloneHelpers.cloneExerciseForTransfer` logic):
  ```kotlin
  val baseCopy = SessionTemplateEngine.cloneSessionContent(base).copy(id = UUID.randomUUID().toString(), name = variantName, sessionB=null, sessionC=null, sessionD=null)
  // cloneSessionContent ya regenera part.id, exercise.id (via supersetIdMap), exercise.sets.id, warmupSets.id, supersetGroups remapeados
  // alternativa: extraer deepCloneForVariant(base):Session que reutilice cloneExerciseForTransfer
  ```
- **Wiring `activeVariantSession`**: en `SessionEditorScreen.kt:147` cambiar `val session = uiState.activeVariantSession ?: uiState.session` y propagar a `buildSessionListItems`, `groupedParts`, `allExercisesForUi`, `TimeCoach` etc. O exponer `val currentSessionForUi` en ViewModel.
- **Nuevo `updateActiveVariantSession(transform)`** que delega según `activeVariant`:
  ```kotlin
  fun SessionEditorViewModel.updateActiveVariantSession(transform: (Session)->Session) = when(currentUiState.activeVariant){
    WeekVariant.A -> updateSession(transform)
    WeekVariant.B -> updateUi { s -> s.copy(session = s.session!!.copy(sessionB = transform(s.session!!.sessionB!!)), hasUnsavedChanges=true).also{ scheduleAutoSave(); scheduleAugeRecalc() }}
    // C/D idem
  }
  ```
  Migrar `setTargetDuration:5-17`, `setPartTargetDuration:19-26`, `setExerciseTargetDuration:28-41`, `distributeTargetDurationAcrossParts:44-66` a usarlo (hoy `distribute` ya `updateCurrentSession` → ok, pero `setTargetDuration` es `updateUi` bypass).
  También `createVariant/deleteVariant` → `updateSession` vs `updateUi` con `lastModifiedAtMs = System.currentTimeMillis()` + `dayOfWeek` sync si se mantiene `updateUi`.

- **Opción ocultar rápido (si no se quiere arreglar):** `SessionContextNavigator.kt:610-655` `availableVariants = listOf(A)` + no renderizar menú crear/derivar; dejar `createVariant` deprecated. Preferir fix completo.

**D. `SessionEditorViewModelTemplates.kt:96-120` — B2 doble apply**
```kotlin
internal fun applyTemplateInternal(template, mode){
  val session = currentUiState.session ?: return
  if(template.session.allExercises().size>12) Log.w(...)
  viewModelScope.launch {
    val prepared = withContext(Default){ template }
    val result = SessionTemplateEngine.applyTemplate(prepared, session, mode)
    updateSession { result }
    updateUi { it.copy(sheet=NONE, templateApplyDecision=null, templateSearchQuery="") }
  }
  return // bloquea fallback síncrono en producción
  // fallback eliminado; tests deben usar runTest/TestScope con viewModelScope
}
```
- Eliminar `109-119` fallback. Si se mantiene para unit test sin scope, envolver en `if (!isUnitTestEnvironment)` o simplemente borrar (tests ya usan `TestScope`).

**E. `domain/sessionassistant/SessionAssistantEngine.kt:57-72,359-941` — A2 muertas (decisión producto: DELETE)**
- **Mantener** `SessionAssistantReport` tal cual (5 campos fijos `OPTIMAL/0/empty` ya validados por `EngineTest:19`), pero **borrar** implementaciones muertas `detectarRiesgos` 359-654, `generarAjustes` 658-716, `generarOportunidades` 778-810, `generarTarjetasFantasma` 814-883, `estimarImpacto` 885-889, `buscarPlantillasCompatibles` 893-941, `clasificarVeredicto/calcularScore/construirResumen` etc., dejando solo `generarAjustesPorRings:718-776` + `buildTimeSuggestions:83-155` + helpers vivos (`calcularDrenajeEstimado`, `calcularVolumenPorMusculo`).
- Alternativa no-op: dejar código pero `@Suppress("unused")` + comentario `// legacy recortado, ver EngineTest:19`. Preferir borrar (~470 líneas) para binario limpio. iOS `Engine.swift` parity idem si se borra Android.

**F. `AssistantSheet.kt:147-159` + `SessionEditorScreen:700-827` + `SessionEditorSheets:263,367-418` — A3 handlers muertos**
- `AssistantSheet.kt:147` quitar params `onApplyAugeCorrection: (String)->Unit` y `onAddGhostExercise` + `155-159` `@Suppress` block (2 líneas)
- `AssistantGlassOverlay:113-121` quitar esos 2 params y reenvíos `132-133`
- `SessionEditorScreen.kt:706-717` borrar `onApplyAugeCorrection/onAddGhostExercise` lambdas con snackbars muertos (12 líneas) — dejar solo `onApplyAssistantSuggestion`
- `SessionEditorSheets.kt:263` `import AssistantSheet` muerto + `367-369` 3 params + `418 early return` comentario ajustado (sigue `if(sheet==AUGE) return` porque se renderiza como overlay)
- `SessionEditorViewModelAugeActions.kt:91-115` `addGhostExercise` queda huérfano → `@Deprecated` o borrar si A2 delete.

### F2 — MEDIO A4b+A5+B4+B6+B7+B5 (2h)

**G. `SessionAssistantEngine.kt:100-112` — A4b superset solapado**
- **Opción preferida non-overlapping:** `for (i in 0 until minOf(exercises.size-1, MAX_SUPERSET_SUGGESTIONS) step 2)` → ex₀-ex₁, ex₂-ex₃ sin solape.
- **+ revalidación secuencial** `SessionEditorViewModelAugeActions.kt:133-139`:
  ```kotlin
  details.forEach { detail ->
    when(val action = detail.action){
      is AssistantDetailAction.ConvertToSuperset -> {
        val tgt = next.allExercises().find{it.id==action.exerciseId} ?: continue
        if(tgt.supersetGroupRefOrLegacyId()!=null) continue
        val idx = next.allExercises().indexOfFirst{it.id==action.exerciseId}
        val nxt = next.allExercises().getOrNull(idx+1) ?: continue
        if(nxt.supersetGroupRefOrLegacyId()!=null) continue
        next = applyAssistantDetail(next, detail)
      }
      else -> next = applyAssistantDetail(next, detail)
    }
  }
  ```

**H. `screens/workout/WorkoutPacingController.kt:252-268` — A5 dedupe**
```kotlin
// antes:
val completedSets = state.completedSets.size
// después (reusa lógica evaluatePace:201-204):
val completedSets = state.completedSets.keys.map{ key -> key.split("_").let{ p -> if(p.size>=2) "${p[0]}_${p[1]}" else key }}.distinct().size
// o extraer fun dedupCompletedSets(keys:Set<String>):Int
```

**I. `SessionEditorViewModelNavigation.kt:48-63,78-84,123-129` — B4 IO**
- Hacer `suspend fun persistRecoverableSession()` + `withContext(Dispatchers.IO)` o envolver calls en `viewModelScope.launch(Dispatchers.IO){ val ok = persistRecoverableSession(state); withContext(Main){ ... } }`
- `SessionEditorViewModel.kt:215-249` ya `persistDraft` hace `encodeToString` síncrono — mover `encodeToString` dentro de `withContext(IO)`:
  ```kotlin
  suspend fun persistRecoverableSession(...) = withContext(Dispatchers.IO){ val ok = persistDraft(state); if(ok && weekId.isNotBlank()) repository.upsertSessionInProgram(...) }
  ```

**J. `SessionEditorCloneHelpers.kt:150-164` — B6 REPLACE sin B/C/D**
```kotlin
identity.copy(
  name=..., dayOfWeek=..., exercises=payload.looseExercises, parts=payload.parts,
  supersetGroups=..., warmup=..., isMainSession=true,
  sessionB=null, sessionC=null, sessionD=null, // limpiar variantes viejas
  trainingBackup=null, isMeetDay=false, isCompetitionSession=false, competitionDetails=null,
)
```

**K. `SessionEditorViewModelNavigation.kt:496-524` — B7 deep clone mesociclo**
- Extraer `deepCloneSessionForWeek(session: Session, newId:String): Session` usando `SessionTemplateEngine.cloneSessionContent` (regenera part/exercise/set/warmup/superset ids + remapeo) o reutilizar `cloneExerciseForTransfer` loop.
- `507` `val cloneForWeek = if(week.id==state.weekId) draft else deepCloneSessionForWeek(draft, UUID.randomUUID().toString())`

**L. `SessionEditorViewModelNavigation.kt:425-455` + `CoverClone.kt:75-100` + `Templates:74-101` — B5 dead code**
- Borrar `appendDraftSnapshot/buildDraftSnapshot` 30 líneas + `exportToSession/importFromSourceSessionLegacy` + `if(size>12) Log.w` sin límite (o reemplazar por `if(size>24) return false` si se quiere límite real).

### F3 — BAJO B8 (30m)

**M. `SessionEditorViewModelVariants.kt:5-17` — setTargetDuration**
```kotlin
fun setTargetDuration(minutes: Int?){
  updateCurrentSession { it.copy(targetDurationMinutes=minutes) } // usa updateSession → lastModifiedAtMs + dayOfWeek sync
  updateUi { it.copy(dismissedTimeCoachIds=emptySet()) } // documentar: re-sirve sugerencias descartadas al cambiar budget
  scheduleAugeRecalc() // updateSession ya lo hace si se usa updateCurrentSession, evitar duplicado
}
```

**N. `SessionEditorViewModelStructure.kt:110-124` — toggleSelection**
- Quitar `scheduleAutoSave()` tras `updateUi { selectedExercisesIds }` (selección es UI, no contenido sesión) o usar `persistDraft()` ligero como `patchRuleDefaults` (`Structure:622`).

**O. `SessionContextNavigator.kt:610-655` + `SessionEditorScreen:488-495` — variantes menú**
- Si F1 variante fix, ajustar `computeAvailableVariants` a reflejar `activeVariantSession` content; si oculto, `availableVariants=listOf(A)`.

---

## 6. Impacto por plataforma y banderas

| Plataforma | Impacto | Detalle |
|---|---|---|
| **Android** | **Sí — directo** | `screens/sessioneditor/*` (todos F0-F3), `domain/sessionassistant/*` (F1/F2), `screens/workout/WorkoutPacingController.kt` (F2), `domain/templates/SessionTemplateEngine.kt` (F1 clone), `data/models/Session.kt` no toca Room. |
| **iOS** | **Parcial** | `ios-native/Domain/Sessionassistant/SessionAssistantEngine.swift:43,608,654,721` mismo recorte muerto (parity con Android F1E), `WorkoutPacingController.swift` mismo dedupe F2H, `Session.swift` variantes B/C/D wiring igual roto si comparten lógica |
| **Backend** | **No** | `backend/engines/` no usa variantes ni DebugLog; no ajuste |

**Banderas:**

| Bandera | Afectada | Valor |
|---|---|---|
| **Room** | **No** | `ProgramEntity.data` JSON `ignoreUnknownKeys`; variantes son `Session` anidada, no tabla; sin migración |
| **AUGE** | **No** | `AugeFatigueEngine` no tocado (solo pacing dedupe) |
| **Voz** | **No** | `voice-engine` no afectado; `WorkoutSetRecorder:519` ya usa `restBetweenSides` correctamente tras auditoría 4 |

---

## 7. Pruebas a ejecutar

### 7.1 Unit (JVM, `android-native/`, `testBaseDebugUnitTest`)

- **A1:** `rm SessionEditorDebugLog.kt` → `grep -r SessionEditorDebugLog main/` → 0 hits; `git ls-files | grep debug-9ba5f2` → 0; `testBaseDebugUnitTest` sin `SessionEditorAuditDebugTest`.
- **A3:** `AssistantSheet` params removidos → `compileBaseDebugKotlin` sin `UNUSED_EXPRESSION` warnings; `SessionEditorScreenTest` overlay sin snackbar muerto.
- **A4b:** `SessionAssistantEngineTest` nuevo `buildTimeSuggestions_nonOverlapping` — 3 ejercicios → sugerencias ex₀-ex₁ solo, no ex₁-ex₂ solapada; `applyAssistantSuggestion_overlapping_skipped` — second `ConvertToSuperset` skipped si `target` ya agrupado.
- **A5:** `WorkoutPacingControllerTest.dedupAdjustRestTimeForPace` — `completedSets = [ex_0_0_L, ex_0_0_R, ex_1_0_L]` → dedup 2, `progress 0.66` no 1.0.
- **B1:** `SessionEditorViewModelVariantsTest.createVariant_regeneratesIds` — `base.exercises.first().id != variantB.exercises.first().id` + `activeVariantSession` wiring → `Screen session == B`.
- **B2:** `SessionEditorViewModelTemplatesTest.applyTemplateInternal_singleShot` — mock `SessionTemplateEngine` count 1, `verify(exactly=1) updateSession`.
- **B4:** `ViewModelNavigation` IO — no hay `StrictMode` violation (detekt `Dispatchers` check).
- **Existentes:** `SessionAssistantEngineTest:19` sigue verde (`OPTIMAL/0/empty`); `WorkoutSetRecorderTest` no afectado.

Comandos:
```bash
gradlew.bat testBaseDebugUnitTest --tests "*SessionAssistantEngineTest*"
gradlew.bat testBaseDebugUnitTest --tests "*WorkoutPacingControllerTest*"
gradlew.bat testBaseDebugUnitTest --tests "*SessionEditorViewModelVariantsTest*"
gradlew.bat testBaseDebugUnitTest --tests "*SessionEditorViewModelTemplatesTest*"
gradlew.bat testBaseDebugUnitTest --tests "*SessionEditorCloneHelpersTest*"
```

### 7.2 Compose / Instrumented
- Abrir Asistente → click volumen → no crea archivo en Download ni hilo (F0).
- Variantes: crear B → editar B peso 80kg → volver a A (peso origen) → B mantiene 80kg, A no cambia.

### 7.3 Build
- `gradlew.bat compileBaseDebugKotlin --offline`
- `gradlew.bat assembleBaseDebug --offline` QA final

---

## 8. Documentación a actualizar

- `docs/audits/2026-08-editor-sesiones/asistente-y-bugs-generales.md` §1.3 y §2.2 priorizaciones → marcar A1, B2, B1, A3 mitigados con links a commits.
- `docs/ARCHITECTURE.md` 28 → `KpknDatabase v20` (no v19) + `docs/ANDROID_ARCHITECTURE_MAP.md` sección `SessionEditor` variantes & DebugLog eliminado.
- `docs/ANDROID_UI_SCREENS_MAP.md`: SessionEditor — variantes wiring `activeVariantSession` y Asistente tabs (Métricas/Sugerencias/Plantillas).
- `app/schemas/` no cambia (JSON).
- `.opencode/kpkn-map.md`: regenerar si se borra `SessionEditorDebugLog.kt`.
- `.opencode/memory/MEMORY.md`: anotar A1 borrado, B1 fix, B2 single-shot.

> Código y esquema Room v20 son autoridad si docs dicen v19.

---

## 9. Riesgos y mitigaciones

| Riesgo | Prob | Impacto | Mitigación |
|---|---|---|---|
| Borrar `SessionEditorDebugLog.kt` rompe `SessionEditorAuditDebugTest` que asume logger existe | Media | Test rojo | Borrar test entero (probe) o mantener logger con `if (!BuildConfig.DEBUG) return` si se quiere preservar |
| Borrar 470 líneas `SessionAssistantEngine` rompe iOS parity (swift mismo muerto) | Media | Paridad | Aplicar mismo `delete` en `ios-native/.../SessionAssistantEngine.swift` 43,608... |
| Variantes fix con `cloneSessionContent` cambia `supersetGroups` ids → `weeklyMetricsCache` miss | Baja | Perf | Cache key incluye `contentHashForAuge` (excluye ids?) No, ids en hash → miss 1 vez tras crear variante, aceptable |
| `applyTemplateInternal` quitar fallback síncrono rompe tests unit que no usan `TestScope` | Media | Test rojo | Migrar tests a `runTest` + `StandardTestDispatcher`; o `return` tras `launch` bloquea fallback en prod pero tests sin scope fallan → agregar `if (isTest) sync` guard |
| Deep-clone mesociclo regenera ids → `WorkoutLog` historic refs a `exerciseId` ya no matchean para `resolveCanonicalExerciseId` | Baja | History | `resolveCanonicalExerciseId` usa `catalogConfigurationId/exerciseDbId/name` fallback, no solo `id` → ok |
| Dedupe `adjustRestTimeForPace` cambia `progress` → descanso `60s` se activa antes, acorta 30s | Baja | UX | Validar con `WorkoutPacingControllerTest` que nuevo progress es esperado (unilateral paired 3 sets, 4 lados completados → 66% vs 133% before) |
| `persistRecoverableSession` a `suspend` rompe callers `Navigation:48,78` que esperan boolean | Baja | Build | Hacer overload `suspend fun persist...IO():Boolean` + wrapper no-suspend `launch(IO)` para navegación |

---

## 10. Criterios de aceptación

- [ ] `git ls-files | grep debug-9ba5f2` → 0 + `grep -r SessionEditorDebugLog` en `main/` → 0 + click volumen no escribe `/sdcard/Download`
- [ ] `SessionAssistantEngine.evaluate()` sin cambios funcionales para ruta viva (test `EngineTest:19` verde) + `AssistantSheet` sin params muertos `onApplyAugeCorrection/onAddGhostExercise` + `SessionEditorScreens` sin import `AssistantSheet` muerto
- [ ] `buildTimeSuggestions` con 4 ejercicios → 2 supersets non-overlapping (ex₀-ex₁, ex₂-ex₃) y chain con `alreadyGrouped` skip
- [ ] `WorkoutPacingController.adjustRestTimeForPace` dedupe `L+R` como 1 serie (unilateral 3 sets, 2 L+R completados → `progress 0.33`)
- [ ] `createVariant(B)` regenera `exercise.id`/`set.id` distintos de A + `Screen` renderiza `activeVariantSession` (editar B no toca A, `commitActiveVariantChanges` ya no no-op)
- [ ] `applyTemplateInternal` 1× `SessionTemplateEngine.applyTemplate` por call (mock count 1)
- [ ] `applySessionToMesocycle` deep-clone regenera inner ids (week ≠ origin → `exercise.id` distinto)
- [ ] `compileBaseDebugKotlin --offline` + `testBaseDebugUnitTest` con nuevos tests pasa

---

## 11. Plan de entrega (requiere aprobación)

1. **Aprobación explícita** de este plan (`pending_approval` → `construction`). No editar código hasta `pipeline.start`.
2. `constructor_kpkn` ejecuta por fases en rama corta, commits atómicos:
   - F0: `SessionEditorDebugLog.kt` + 3 logs + `AssistantSheet/HistorySheet/RulesSheet` gut + `.gitignore`
   - F1: `SessionEditorViewModelVariants.kt` + `SessionEditorScreen.kt` + `SessionEditorViewModelTemplates.kt` + `SessionAssistantEngine.kt` + `AssistantSheet/Screen/Sheets` dead handlers
   - F2: `SessionAssistantEngine.kt` (overlap step 2) + `SessionEditorViewModelAugeActions.kt` revalidación + `WorkoutPacingController.kt` dedupe + `SessionEditorCloneHelpers.kt`/`ViewModelNavigation.kt` deep-clone + B5 dead code
   - F3: `SessionEditorViewModelVariants.kt`/`ViewModelStructure.kt` B8 aligns
3. `gradlew.bat compileBaseDebugKotlin --offline` → `testBaseDebugUnitTest` → `assembleBaseDebug`
4. Auditor revisa diff vs plan; `pipeline submit_audit` → `auditing`

---

## 12. Alternativas descartadas

- **Guardar `SessionEditorDebugLog` con `BuildConfig.DEBUG` guard:** deja deuda (hilos, `/sdcard/Download`, host path `C:/Users/valen...`), mejor borrar; si se necesita debug local, usar `Logcat` + `adb` sin disco/red.
- **Reconectar `SessionAssistantEngine` completo (`detectarRiesgos` etc.):** es feature nueva (~500 líneas + UI Riesgos/Oportunidades/Ghost/Plantillas + handlers) fuera de bugfix scope; documentar como roadmap si PO lo pide.
- **Variantes solo ocultar UI (`availableVariants=listOf(A)`):** fix mínimo 2 líneas que evita ilusión, pero si producto quiere B/C/D (week variant) hay que arreglar de verdad; plan propone fix completo, ocultar como fallback si no hay tiempo.
- **Dejar `applyTemplateInternal` fallback síncrono para tests:** duplica lógica prod; tests deben migrar a `runTest`; `return` tras `launch` es 1 línea y deja fallback solo para test con flag.
- **No dedup en `adjustRestTimeForPace` (unilateral ya cubierto por otro agente):** pero inconsistencia local `evaluatePace` vs `adjust` es bug aislado, fix 1 línea sin riesgo.
- **Borrar `SessionB/C/D` en REPLACE siempre:** si fuente tiene variantes, perdería contenido; alternativa: copiar `source.sessionB/C/D` al destino si `source` es plantilla con variantes, pero audit pide limpiar viejas — plan limpia destino.

---

## 13. Referencias exactas (para auditor)

- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorDebugLog.kt:18-36,60-86`
- `android-native/app/src/test/java/com/example/kpkn/screens/sessioneditor/SessionEditorAuditDebugTest.kt:24,60,106,138`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/sheets/AssistantSheet.kt:147-159,291-305,163-164,200-203,706-783`
- `android-native/app/src/main/java/com/example/kpkn/domain/sessionassistant/SessionAssistantEngine.kt:57-72,83-155,359-941,718-776`
- `android-native/app/src/main/java/com/example/kpkn/domain/sessionassistant/SessionAssistantModels.kt:41-53,134-149`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModelAugeActions.kt:91-159,183-210,265-286`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorScreen.kt:147,488-495,700-729,810-827,668,647`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/sheets/SessionEditorSheets.kt:263,367-418`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModelVariants.kt:5-17,44-66,76-108,111-159`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorModels.kt:233-238`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorCloneHelpers.kt:92-108,136-164,60-83,96-108,231-296`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModelTemplates.kt:96-120,74-101`
- `android-native/app/src/main/java/com/example/kpkn/domain/templates/SessionTemplateEngine.kt:55-138`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModelNavigation.kt:48-63,78-84,304-312,425-455,496-524`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/sheets/ClonerSaveWarmupSheets.kt:508-613,428`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutPacingController.kt:142-268,47-61`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutSetRecorder.kt:518-550`
- `android-native/app/src/main/java/com/example/kpkn/domain/calculations/Calculations.kt:498-620` (ref F2 time)
- `android-native/app/src/test/java/com/example/kpkn/domain/sessionassistant/SessionAssistantEngineTest.kt:19-29`

> **Siguiente paso:** aprobar este plan para pasar a `construction`. No se editará código de producto hasta `pipeline.start` + `request_approval` confirmados. Código y esquema Room v20 son autoridad si docs dicen v19.
