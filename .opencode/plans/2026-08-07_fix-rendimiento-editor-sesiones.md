# Plan — Corregir rendimiento editor de sesiones (auditoría 1)

**Fecha:** 2026-08-07
**Autor:** orquestador (muse-spark-1.2)
**Estado:** `pending_approval` (no editar código de producto hasta aprobación explícita)
**Auditoría fuente:** `docs/audits/2026-08-editor-sesiones/rendimiento.md` (362 líneas, solo lectura, 2026-08, alcance `screens/sessioneditor/`, Kotlin 2.2.10)
**Solicitud:** solucionar TODO lo cubierto por la auditoría 1, paso a paso

---

## 1. Resumen ejecutivo

El "pesado" no es un punto único sino un bucle por tecla/tick y un fan-out por frame:

```
tecla/tick → updateSet→updateExercise→updateSession (copy estructural + lastModifiedAtMs + equals profundo)
→ _uiState nuevo (monolito ~60 campos) → recomposición de TODA la pantalla
→ remember(session) invalidado → buildSessionListItems + orderedMembers por superset
→ (300ms) recálculo AUGE de TODA la semana (6-7 sesiones × sets) + assistant completo
→ _uiState.update → 2ª recomposición masiva
+ drag: offset leído en raíz y pasado a TODOS los items → LazyColumn recompone a 60fps + projectedShiftFor O(n) por item = O(n²)
```

Verificación con subagentes (investigador + android-compose + auge-engine) **confirma al 95-100%** todas las evidencias archivo:línea; única divergencia: líneas ±1-5 por headers. `weeklyMetricsCache` 100% muerto, `exerciseIndex` reconstruye catálogo por acceso, strong skipping activo pero roto por `uiState` inestable.

El plan corrige sin tocar fórmulas AUGE ni `domain/` puro:

1. **Commit on-finish + no timestamp sin cambio + key estructural** (elimina 70-80% recomposiciones al teclear)
2. **Offset solo en layer + shiftMap memoizado** (drag de 60fps recomponiendo todo → 0 recomposiciones por frame)
3. **Flows divididos + sacar searchQuery/snackbar de uiState** (aisla hero, lista, sheets)
4. **Cablear weeklyMetricsCache + O(1) catalogIndex + assistant bajo demanda** (7× menos CPU en Default)
5. **Pulido M/B** (contentType, groupedParts, anchorNames, batch feedback)

No requiere migración Room (salvo query batch opcional), ni voz, ni backend inmediato. Paridad iOS/backend solo documentativa para C4.

---

## 2. Contexto y reproducción

- **Repro fiable tecleo:** abrir editor con sesión de 8 ejercicios sueltos + 1 superset de 3, editar `EditorMiniField` peso/reps (5 caracteres) o arrastrar slider %RM 1s. Actual: cada tecla dispara O(n) copy + equals + rebuild lista + recomposición total + debounce AUGE semanal. Perfetto con `runtime-tracing` muestra P95 >16ms y N≈8-12 items recomponiendo por edición.
- **Repro drag:** 10+ ejercicios, arrastrar uno entre partes. Actual: `LazyColumn` entera recompone por frame, `projectedShiftFor` O(n) × visibles = O(n²). Frame >16ms, drag trabado.
- **Repro picker:** abrir picker, teclear "press" (3-5 teclas). Actual: `searchQuery` en `uiState` recompone fondo + `exerciseCatalogSnapshot().toList()` por tecla + recarga asset V2 al abrir.
- **Repro AUGE:** editar cualquier set, esperar 300ms. Actual: `computeSessionAugeComputation` ×7 sesiones en `Dispatchers.Default` compitiendo con Main + 2ª recomposición.

---

## 3. Hallazgos verificados (subagentes, archivo:línea actual)

> Rutas relativas a `android-native/app/src/main/java/com/example/kpkn/` salvo indicación. `grep` global confirma caché muerto y aliases vacíos. `docs/ARCHITECTURE.md` dice Room v19 → **contradice código** `KpknDatabase.kt` v20 (autoridad).

| ID | Severidad | Evidencia actual verificada | Estado |
|---|---|---|---|
| **C1** offset drag en raíz | CRÍTICO | `SessionEditorScreen.kt:216` `draggingPartOffsetY`, `221` `draggingExerciseOffset`, `219` `draggingExerciseId` leídos en Screen; `511` `draggingExerciseOffset=...` + `516` `draggingPartOffsetY` pasados a cada `SessionEditorListItem` en `items()`; `SessionEditorDragController.kt:229` `+=delta` por pointer; `SessionEditorScrollRenderer.kt:77-82` params → `LooseExerciseItem:374` / `PartExerciseItem:451` / `LooseSuperset:528` / `PartSuperset:655` → `ExerciseEditorCard:108` `dragOffset`; `SessionEditorScreen.kt:312-346` `projectedShiftFor` con `firstOrNull/indexOfFirst` O(n); `ScrollRenderer:134,180,230,281` `animateFloatAsState(projectedShiftFor())` en composición | CONFIRMADO |
| **C2** uiState monolítico | CRÍTICO | `SessionEditorScreen.kt:145` `collectAsStateWithLifecycle()` único; `SessionEditorModels.kt:149-239` ~40 campos; `507` `uiState=uiState` a cada item (`ScrollRenderer:73` solo usa `collapsedPartIds:109` y `competitionMovementIds:157,205,254,298`); `732` `SessionEditorSheets(uiState)` + `700` `AssistantGlassOverlay(uiState)`; `ViewModel.kt:517-531` crea uiState nuevo por edición | CONFIRMADO |
| **C3** commit por tecla/tick | CRÍTICO | `SessionEditorFormFields.kt:72-77` `onValueChange{localValue=it; onCommit(it)}`; `InlineSetRow.kt:578-580` `Slider onValueChange{onUpdate{copy(targetPercentageRM)}}`; `SetEditorCards.kt:108-112` + `552-558` idem; cadena `ViewModelStructure.kt:450` → `383` → `ViewModel.kt:517` `transformExercises:149-167` O(n) + `522` `copy(lastModifiedAtMs=now)` + `529` `hasUnsavedChanges=updated!=original` + `532` `scheduleAugeRecalc/AutoSave`; `Screen:273` `remember(session,collapsedIds){buildSessionListItems}` invalidado por timestamp | CONFIRMADO |
| **C4** AUGE semanal + caché muerto | CRÍTICO | `ViewModel.kt:542-552` `delay(300) withContext(Default){recalcAndPushAuge}`; `564-650` cadena `TrainingEnergyEngine:574 → getLogsForProgram:576 → buildAugeSummary:579-590 → SessionAssistantEngine.evaluate:598-624 → calculateSessionTimeBreakdown:626-632 → _uiState.update:634-649`; `AugeComputation.kt:74-92` `weeklyMetrics = weeklySessions.map{computeSessionAugeComputation}` O(sesiones×sets); `ViewModel.kt:151-157` `weeklyMetricsCache` grep 0 lecturas/escrituras → MUERTO; `659-672` `updateSessionTextField` también llama `scheduleAugeRecalc` aunque nombre no afecta AUGE | CONFIRMADO |
| **A1** exerciseIndex reconstruye | ALTO | `ViewModel.kt:121-128` `get(){exerciseCatalogSnapshot().associateBy{id}+catalogSearchRedirects().map...}` → `ExerciseDatabase.kt:93` `toList()` + `99` `emptyMap()` + `96` `catalogExerciseIndex()=byIdCache` O(1) no usado; accesos `582,608` 2× por AUGE + `AugeActions.kt:292,372` por ejercicio en bucle | CONFIRMADO |
| **A2** picker recompone | ALTO | `ViewModelStructure.kt:108` `setSearchQuery=updateUi{copy(searchQuery)}` → C2; `Sheets.kt:449` `catalog=exerciseCatalogSnapshot()` por recomposición host (`344` recibe uiState completo); `ExercisePickerSheet.kt:114` `remember{ApprovedAssetRepositoryV2}` + `115` `LaunchedEffect{load()}` recarga asset por apertura; `118-133` `return` → rama legacy `134-687` muerta pero param sí se evalúa | CONFIRMADO |
| **A3** projectedShiftFor O(n) | ALTO | Duplica C1: `ScrollRenderer:134/180/230/281` + `Screen:566` `::projectedShiftFor` | CONFIRMADO |
| **A4** remember(session) por timestamp | ALTO | `ViewModel:522` + `Screen:273`, `Analytics.kt:712` `remember(muscleName,session)`, `AssistantSheet:285` `remember(session)` | CONFIRMADO |
| **A5** exactMatch flatMap | ALTO | `ExercisePickerV2Catalog.kt:398-403` `remember(query,catalog,custom){catalog.families.flatMap{definitions}}` vs `279` `definitionsById` cacheado | CONFIRMADO |
| **M1** groupedParts | MEDIO | `Screen:299` `filterNot{isUncategorizedPart}` sin remember, pasado a `506` items + `dragController.updatePartDrag` | CONFIRMADO |
| **M2** resolveRelationshipAnchorName | MEDIO | `UiUtils.kt:142-153` `allExercises().firstOrNull{resolvedCanonicalId}` O(n) alloc; llamado `ScrollRenderer:424,500,573` por tarjeta | CONFIRMADO |
| **M3** allExercises raíz | MEDIO | `Screen:651` `isNotEmpty()`, `672` `firstOrNull` + `Session.kt:61` `exercises+parts.flatMap` lista nueva | CONFIRMADO |
| **M4** supersets re-resueltos | MEDIO | `ScrollRenderer:225,275` `allSupersetGroups().firstOrNull` + `226-228` `mapNotNull{firstOrNull}` O(m·n); `Session.kt:37-58` legacy recomputa `allExercises` varias veces | CONFIRMADO |
| **M5** applyScrollDelta por frame | MEDIO | `DragController:107-131` `mapValues+clear/putAll` en 4 `mutableStateMapOf` por delta; `Screen:374-417` `while(isActive){...delay(16)}` aun con delta==0 | CONFIRMADO |
| **M6** updateExerciseDrag filtra | MEDIO | `DragController:226` `session.parts.filterNot` por evento pointer | CONFIRMADO |
| **M7** sheets/FAB gordos | MEDIO | `Screen:732` `Sheets(uiState,allTemplates)` `699` `AssistantGlassOverlay(uiState,templates)` `640` `HeroGlassFab(summary=augeSummary)` | CONFIRMADO |
| **M8** LaunchedEffect(session,collapsed) prune | MEDIO | `Screen:209` `pruneBounds` O(n) por identidad nueva | CONFIRMADO |
| **M9** loadHistory N+1 | MEDIO | `ViewModel:506-512` `logs.mapNotNull{getFeedbackForLog(log.id)}` N queries | CONFIRMADO |
| **M10** predictedWeights | MEDIO | `ExerciseEditorCard.kt:172-181` `remember(trainingMode,reference1RM,sets)` recalcula todos `calculateSuggestedLoad/EstimatedMetric` por cualquier cambio sets | CONFIRMADO (acotado a ejercicio editado) |
| **B1** contentType | BAJO | `Screen:502` `items(key=stableKey)` sin `contentType` (9 tipos `SessionListItems:19-66`) | CONFIRMADO |
| **B2** snackbar en uiState | BAJO | `Screen:188-193` `LaunchedEffect(snackbarMessage){show+clear}` doble update | CONFIRMADO |
| **B3** pendingAutoExpand relanzado | BAJO | `Screen:291` `LaunchedEffect(pendingId,scrollableListItems)` se relanza por A4 | CONFIRMADO |
| **B4** autosave upsert programa | BAJO | `ViewModel:135-143` `delay(2000) Dispatchers.IO persistRecoverableSession` → `241-253` `upsertSessionInProgram→updateProgram` reescribe programa entero (bien en IO, mal churn) | CONFIRMADO |

Preguntas resueltas: strong skipping **activo** (Kotlin 2.2.10, `org.jetbrains.kotlin.plugin.compose` sin `composeCompiler {}` → ON por defecto), pero `SessionEditorUiState`/`Session` inestables (`List` sin `@Stable`) + `copy(lastModifiedAtMs)` garantizan `equals=false` → no skip.

---

## 4. Diseño propuesto

### 4.1 Objetivos
- P95 frame <16ms en: (a) teclear 5 chars, (b) mover slider 1s, (c) drag entre partes con 10+ ejercicios, (d) abrir picker y teclear "press".
- ≤1 recomposición de items visibles por edición de set (el resto skipea).
- Drag 0 recomposiciones por frame (solo layout/draw del item arrastrado).
- AUGE 7× menos CPU en `Dispatchers.Default` (solo sesión editada + cache), sin cambiar fórmulas.

### 4.2 No objetivos
- No reescribir `domain/auge`, `domain/training`, `domain/workout`, `domain/biomechanics` (puros).
- No migrar Room v20 (salvo query batch opcional M9, sin bump versión).
- No tocar `services/workout/` (voz), `navigation/Navigation.kt`, `backend/` ni `ios-native/` (solo documentar paridad C4).
- No cambiar manual DI ni añadir librerías.

### 4.3 Estrategia por fases (orden impacto/esfuerzo de la auditoría, con dependencias)

```
Fase 0 Quick-wins (1h) ──> Fase 1 C3+A4 (3-4h) ──> Fase 2 C1+A3+M5+M6 (3-5h) ──> Fase 4 C4+A1 (3-4h) ──> Fase 3 C2+A2+M7+B2 (4-6h) ──> Fase 5 pulido M9/M10/B4 (2-3h)
  M1,A1(*),A5,B1      commit on-finish + key    offset layer + shiftMap     weeklyMetricsCache +     flows divididos +   batch feedback,
                     estructural (ROI máx)      (drag fluido)               index O(1) + on-demand  picker singleton     predicted cache
(*) A1 parcial en 0: getter O(1); hoisting completo en Fase 4 con C4
```

Dependencias críticas: M1 antes que M6 y C1; A1 antes/durante C4; C3+A4 antes que M8/B3/M3; C2 antes que A2/M7; C1+A3+M5 atómico.

---

## 5. Cambios detallados por archivo

### Fase 0 — Quick-wins (sin riesgo, 1h)

**A. `SessionEditorViewModel.kt:121-128` — A1 parcial**
- Eliminar getter `exerciseIndex` que reconstruye mapa. Reemplazar por `private val exerciseIndex: Map<String, ExerciseMuscleInfo> get() = catalogExerciseIndex()` (import `com.example.kpkn.data.exercises.catalogExerciseIndex`) O(1) referencia a `exerciseDatabaseByIdCache`. Eliminar rama `catalogSearchRedirects()` (siempre `emptyMap()` en `ExerciseDatabase.kt:99`). Si se teme mutación overlay custom durante recalc, capturar `val index = catalogExerciseIndex()` al inicio de `recalcAndPushAuge` y pasarlo a `buildAugeSummary`/`SessionAssistantEngine`.

**B. `SessionEditorScreen.kt:299` — M1**
- `val groupedParts = remember(session.parts) { session.parts.filterNot { it.isUncategorizedPart() } }` — instancia estable, quita alloc por recomposición y alimenta `SessionEditorListItem` y `dragController.updatePartDrag(groupedParts)`.

**C. `ExercisePickerV2Catalog.kt:398-404` — A5**
- `val allDefinitions = remember(catalog) { catalog.families.flatMap { it.definitions } }` (o usar `definitionsById:279` ya memoizado) y `remember(query, allDefinitions, customExercises)` en lugar de `remember(query, catalog, customExercises)` → elimina flatMap por tecla.

**D. `SessionEditorScreen.kt:502` — B1**
- `items(scrollableListItems, key = { it.stableKey }, contentType = { it::class })` o `contentType = { it.contentTypeEnum }`. Pool recicla solo mismo tipo (Hero/PartHeader/Exercise/Superset). Referencia `ExercisePickerV2Catalog.kt:530,548` que ya lo hace.

### Fase 1 — C3 + A4 + M8 + M3 + B3 (ROI máximo, 3-4h)

**E. `SessionEditorFormFields.kt:72-77` — C3 commit on-finish**
- `EditorMiniField`: mantener `localValue` pero commit en `onDone` / `onFocusChanged(false)` o `snapshotFlow { localValue }.debounce(400)` en lugar de `onValueChange { onCommit(it) }`. No en cada `onValueChange`. Añadir `KeyboardActions(onDone = { onCommit(localValue); focusManager.clearFocus() })`.

**F. `InlineSetRow.kt:578-580` y `SetEditorCards.kt:109-112,552-558` — C3 slider**
- `Slider(value = sliderPercent.toFloat(), onValueChange = { localPercent = it }, onValueChangeFinished = { onUpdate { copy(targetPercentageRM = localPercent.toDouble(), intensityMode = IntensityMode.LOAD) } })`. Estado local mientras se arrastra, commit único al soltar. Igual para `AccentSetValueField:621`.

**G. `SessionEditorViewModel.kt:517-534` — C3 no timestamp sin cambio + dirty flag**
- ```kotlin
- fun updateSession(transform: (Session) -> Session) {
-   val current = _uiState.value.session ?: return
-   val transformed = transform(current)
-   if (transformed == current) return // sin cambio estructural, no tocar lastModifiedAtMs ni emitir
-   val updated = transformed.copy(lastModifiedAtMs = System.currentTimeMillis())
-   _uiState.update { it.copy(session = updated, hasUnsavedChanges = true) } // flag dirty vs equals profundo
-   scheduleAugeRecalc(); scheduleAutoSave()
- }
- ```
- Sustituir `hasUnsavedChanges = updated != originalSession` (equals profundo O(n) por evento) por `var dirty = false` que se pone a true solo si `transformed != current`. Comparar `originalSession` solo en `save`/`hasUnsavedChanges` expuesto.

**H. `SessionEditorScreen.kt:273,291,651,672` + `SessionEditorAnalytics.kt:712` + `AssistantSheet.kt:285` — A4+M8+M3+B3 key estructural**
- No usar `remember(session)` donde `session` cambia por `lastModifiedAtMs`. Cambiar a `remember(session.parts, session.exercises, session.supersetGroups, uiState.collapsedPartIds)` o `remember(session.contentVersion)` donde `contentVersion = session.parts.hashCode() xor session.exercises.hashCode() xor session.supersetGroups.hashCode()` (o `contentHash` puro). Con ello `buildSessionListItems:273` no se reconstruye por timestamp, `LaunchedEffect(pendingAutoExpandExerciseId, scrollableListItems):291` pasa a `LaunchedEffect(pendingAutoExpandExerciseId)` + lectura fría de `scrollableListItems` dentro, `allExercises:651,672` pasa a `val allExercises = remember(session.exercises, session.parts) { session.allExercises() }`.

**I. `SessionEditorViewModel.kt:658-677` — C4 textField no AUGE**
- `updateSessionTextField` (nombre/descripción) no debe llamar `scheduleAugeRecalc()` — solo `scheduleAutoSave()`. Verificado que `computeSessionAugeComputation` no lee `Session.name/description`.

### Fase 2 — C1 + A3 + M5 + M6 (drag fluido, 3-5h, localizado, sin VM)

**J. `SessionEditorScreen.kt:216,221,511,516,385-389,683` — C1 offset solo en layer**
- Dejar de leer `draggingExerciseOffset` en raíz para fan-out. Pasar a items solo `draggingExerciseId: String?` y flags `isDragging: Boolean`, `isDropTarget: Boolean`. El offset se lee **solo dentro de `graphicsLayer` del ítem arrastrado**: `ExerciseEditorCard.kt:222-227` → `translationY = if (isDragging) dragController.draggingExerciseOffset.y else 0f` (lectura de estado en fase layer → 0 recomposición). `DragLiftPreview` (`SessionEditorVisuals.kt:155-205`) cambia param `offset: Offset` por lectura `dragController.draggingExerciseOffset` dentro de `Modifier.offset { IntOffset(...) }` lambda.

**K. `SessionEditorScrollRenderer.kt:77-82,134,180,230,281,566` + `SessionEditorScreen.kt:312-346` — A3 shiftMap memoizado**
- Precalcular una vez por cambio de target: `val shiftByExerciseId = remember(draggingExerciseId, exerciseDropTargetKey, exerciseDropTargetPartId, exerciseDropTargetIndex) { buildShiftMap(session, draggingId, targetKey, targetPart, targetIndex) }` donde `buildShiftMap` itera `session` una vez y asigna `-1,0,+1` desplazamientos. En cada item `val shift = shiftByExerciseId[exerciseId] ?: 0f` y `animateFloatAsState(targetValue = shift * (itemHeight+8))`. Elimina O(n) `projectedShiftFor` por composición → lookup O(1). Calcular `itemHeight` desde `SessionEditorDragController` o constante.

**L. `SessionEditorDragController.kt:107-131,226,351` — M5+M6**
- `applyScrollDelta`: mutar solo entradas afectadas in-place (`for (e in partBounds.entries) e.setValue(e.value.copy(top+=delta))`) en lugar de `mapValues` + `clear/putAll` (evita 4 Snapshot writes por frame). Early return `if (delta==0f) return`.
- `updateExerciseDrag(delta, session, groupedParts)`: recibir `groupedParts` memoizado (M1) en lugar de `session.parts.filterNot` por frame (`226`).
- `beginExerciseDrag`/`updatePartDrag`/`endPartDrag` mantener frozen pero compensar `applyScrollDelta(-delta)` ya existente; verificar `registerExerciseBoundsDuringDrag` sigue registrando items que entran por auto-scroll.

**M. `SessionEditorScreen.kt:374-417` — M5 loop**
- `LaunchedEffect(draggingExerciseId, draggingPartId, lazyColumnWindowBounds)` con `while(isActive){ delay(16) }` → cachear último `pointerY` y salir temprano si `delta==0f` (no recalcular thresholds si dedo no se mueve).

### Fase 4 — C4 + A1 completo + M2 + M4 (3-4h, toca ViewModel + AUGE wiring, sin fórmula)

**N. `SessionEditorViewModel.kt:151-157,564-650` — C4 cablear weeklyMetricsCache**
- Reemplazar `data class CachedWeeklyMetrics(val sessionIds: Set<String>, val metrics: List<SessionAugeComputation>)` por:
  ```kotlin
  private data class CachedWeeklyMetrics(
    val programId: String, val mesoIndex: Int, val settingsHash: Int, val catalogVersion: Int,
    val perSession: Map<String, Pair<Int, SessionAugeComputation>> // id -> (contentHash, computation)
  )
  @Volatile private var weeklyMetricsCache: CachedWeeklyMetrics? = null
  fun Session.contentHashForAuge(): Int // helper puro en domain/sessioneditor/SessionHash.kt, hash solo de fields que afectan AUGE: exercises{dbId, configId, trainingMode, reference1RM, restTime, supersetId, sets{reps/RPE/RIR/%RM/weight/isFailure/intensityMode/dropSets}} + parts + warmup; EXCLUIR name/description/lastModifiedAtMs/dayOfWeek
  ```
- Algoritmo en `recalcAndPushAuge`: merge `draftAwareWeekSessions:568-572` ya existe → snapshot `catalogVersion = catalogExerciseIndex().size`, `settingsHash = settings.calorieGoal.hash + athleteType.hash` → si cache miss (programId/meso/settings/catalog cambian) invalidate all; for each `s in weeklySessions` reuse `perSession[s.id]` si `hash==cachedHash` else `computeSessionAugeComputation(s, catalogExerciseIndex(), settings, programLogs, ...)` solo para esa; `currentMetrics = perSession[session.id]!!`; `weeklyMetrics = draftAwareWeekSessions.map{ perSession[it.id]!! }`; upsert cache. **7× menos CPU**: 224 sets → 32 sets por tecla.
- No cachear EMA `calculateMesocycleStressEMA` si logs cambian entre recalc (barato, O(logs) 10-50) o incluir `logsHash = logs.size + lastDate`.
- `TrainingEnergyEngine.estimatePlannedSession` y `getLogsForProgram` leídos cada debounce aunque no cambien: considerar cache `settings.value` y `logsHash` para no releer si no cambió.

**O. `SessionEditorViewModelAugeActions.kt:288-305,348-378` — A1 hoisting**
- Izar `val index = catalogExerciseIndex()` fuera de bucles `reduceSetsForMuscle` y `exerciseMatchesPrimaryMuscle`/`orderedExerciseIdsForAlert`. Eliminar invocaciones `effectiveMuscles(exercise, exerciseIndex)` por ejercicio que reconstruían mapa.

**P. `SessionEditorUiUtils.kt:142-153` + `SessionEditorScrollRenderer.kt:424,500,573` — M2**
- `val anchorNames = remember(session.allExercises()) { session.allExercises().associate { it.resolvedCanonicalExerciseId().lowercase().trim() to it.name } }` en raíz y pasar `anchorName: String?` a `ExerciseEditorCard` → lookup O(1) en lugar de `firstOrNull` O(n) por tarjeta.

**Q. `SessionEditorScrollRenderer.kt:225,275` + `SessionListItems.kt:79-117` — M4**
- En `buildSessionListItems` ya se resuelven grupos; pasar `supersetGroup: SupersetGroup?` (o `Map<groupId, SupersetGroup>` memoizado `remember(session.supersetGroups)`) a items en lugar de `session.allSupersetGroups().firstOrNull` + `memberIds.mapNotNull{firstOrNull}` O(m·n) por recomposición.

**R. Assistant bajo demanda (TimeCoach pattern)**
- Split `recalcAndPushAuge`: nivel 1 (rings/volumen/energía/tiempo) cada 300ms sin `SessionAssistantEngine.evaluate`; nivel 2 assistant solo si `sheet==AUGE` inmediato, else debounce 2500ms o `refreshAssistantImmediate()` al abrir `openSheet(SessionEditorSheet.AUGE)` (igual que `refreshTimeCoachSuggestions:765-786` al abrir TIEMPO). Nuevo `assistantJob: Job?` cancelable. Mostrar `isLoadingAssistant` si sheet abierta rápido tras editar.

### Fase 3 — C2 + A2 + M7 + B2 (estructural, 4-6h, mayor riesgo, medir aislado)

**S. `SessionEditorViewModel.kt:167-176` + `SessionEditorModels.kt:149-239` — C2 flows divididos**
- Exponer `val sessionFlow: StateFlow<Session?> = _uiState.map{it.session}.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)` y análogos `collapsedPartIdsFlow`, `augeSummaryFlow`, `sheetFlow`, `competitionMovementIdsFlow`. Alternativa incremental: mantener `_uiState` pero crear `derivedStateOf` en Screen y pasar solo proyecciones. No pasar `uiState` entero a `SessionEditorListItem` (`73`) → firma `(collapsedPartIds: Set<String>, competitionMovementIds: Set<String>)`; `SessionEditorSheets` y `AssistantGlassOverlay` suscritos solo a `sheetFlow`/`augeSummaryFlow`/`templatesFlow`.

**T. `SessionEditorViewModelStructure.kt:108` + `SessionEditorModels.kt:173` + `SessionEditorSheets.kt:344,449,644` + `ExercisePickerSheet.kt:114-133` — A2+M7**
- Sacar `searchQuery` de `UiState` → `MutableStateFlow<String>` separado o `rememberSaveable` local del sheet. Eliminar rama legacy muerta `ExercisePickerSheet:126+` tras `return` (y el param `catalog` si queda sin uso). `ApprovedAssetExerciseCatalogRepositoryV2` singleton en `Application` o `remember` a nivel `MainActivity` (prefetch paso 9 ya existe en `CatalogV2ProcessCache.kt`) para no `load()` asset por apertura.

**U. `SessionEditorScreen.kt:188-193` — B2 snackbar canal**
- `Channel<EditorEvent>` / `SharedFlow<String>(replay=0)` para mensajes one-shot; `LaunchedEffect(events) { showKpknSnackbar }` sin `clearSnackbarMessage()` ni `updateUi`. Saca 2 `updateUi` por snackbar de C2.

### Fase 5 — Pulido M9/M10/B4 (2-3h, aislados)

**V. `SessionEditorViewModel.kt:506-514` + `data/db/AugeDao.kt` + `AugeRepository.kt` — M9 N+1**
- Nuevo `getFeedbackForLogs(ids: List<String>): Map<String, PostSessionFeedbackEntity>` con `SELECT * FROM PostSessionFeedbackEntity WHERE logId IN (:ids)` o JOIN `WorkoutLogEntity`. Reemplaza `logs.mapNotNull{getFeedbackForLog(log.id)}` 1 query por log.

**W. `ExerciseEditorCard.kt:172-181` — M10**
- Cache por `set.id + hashCamposRelevantes` en lugar de `remember(trainingMode, reference1RM, sets)` que invalida todos los sets por cualquier cambio. Diff previo: recalcular solo sets cuyo `targetReps/RPE/RIR/%RM/weight` cambió.

**X. `SessionEditorScreen.kt:502` + `SessionEditorAnalytics.kt:712` + `SessionEditorViewModel.kt:241-253` — B4+M8 hygiene**
- `groupedParts` etc ya en Fase 0; `allExercises` remember en raíz; `LaunchedEffect(pendingAutoExpandExerciseId)` solo por id; persist solo sesión/diff vs `upsertSessionInProgram` programa entero cada 2s (coalescing si no hay ventana de guardado global — dejar para follow-up si churn es visible en trace).

---

## 6. Impacto por plataforma y banderas

| Plataforma | Impacto | Detalle |
|---|---|---|
| **Android** | **Sí — directo** | 100% del plan en `screens/sessioneditor/**`, `screens/sessioneditor/components/**`, `data/exercises/ExerciseDatabase.kt`, `data/db/` (M9 query), `domain/sessionassistant/` (helper hash puro). No toca `domain/auge/` fórmulas. |
| **iOS** | **No / Documentativo** | `ios-native/` editor es placeholder (`SessionEditorViewModel.swift` 4 archivos grep). `AugeFatigueEngine.swift:842` ya paridad 1:1 tanques/drain/EMA; `ExerciseDatabase.swift:90 catalogExerciseIndex()` O(1) ya usado. Cuando se materialice editor iOS, **replicar patrón** cache `perSession:[String:(hash,Computation)]` + `contentHash` + `catalogExerciseIndex()` + assistant on-demand. Añadir fila en `docs/paridad/auge-matrix.md` futuro. |
| **Backend** | **Nulo** | `backend/engines/fatigue_engine.py:321` es port AUGE v2 (no v3 `calculateAdjustedPredictedDrain` + dampen 0.72/0.0022), sin concepto semanal ni recalc 300ms. No tocar; registrar divergencia v2/v3 en `BACKEND_EVALUATION.md` si se añade. |

**Banderas:**

| Bandera | Afectada | Valor | Notas |
|---|---|---|---|
| **Room** | **Sí (M9 opcional)** | Sin bump versión | Solo nuevo query `WHERE logId IN (:ids)` o JOIN; no migración destructiva; `KpknDatabase.kt` v20 autoridad (docs dicen v19 desactualizado). Si no se hace M9, plan no toca Room. |
| **AUGE** | **Sí (Fase 4)** | Wiring, no fórmula | Cache `weeklyMetricsCache` + `contentHash` + `catalogExerciseIndex` + assistant on-demand; no cambia constantes AUGE; requiere paridad doc iOS/backend. |
| **Voz** | **No** | — | No se toca `services/workout/` (Vosk/TTS/AIDL). |

---

## 7. Pruebas a ejecutar

### 7.1 Unit (JVM, `android-native/`, `testBaseDebugUnitTest`)

- **Fase 1 (C3):** `SessionEditorRulesEngineTest` existente + nuevo `SessionEditorUpdateSessionTest`: `applyDefaults_rewrites_sets` solo en commit, `updateSession_noop_when_transform_returns_same_instance` (assert `lastModifiedAtMs` no cambia), `hasUnsavedChanges_flag_vs_deepEquals`.
- **Fase 2 (C1/A3):** nuevo `SessionEditorDragControllerTest`: `shiftMap_precomputes_O1_lookup`, `dragOffset_not_propagated_to_non_dragged_items`, `applyScrollDelta_inPlace_mutation`.
- **Fase 4 (C4/A1):** nuevo `SessionEditorAugeComputationTest`: `weeklyMetricsCache_reuses_unchanged_sessions`, `contentHash_ignores_name_description`, `catalogExerciseIndex_O1_no_alloc`, `updateSessionTextField_does_not_trigger_AUGE`, `assistant_not_evaluated_until_sheet_open`.
- **Fase 5 (M9):** `AugeRepositoryTest` batch feedback `getFeedbackForLogs` vs N+1.
- **Existentes a reverificar:** `SupersetRulesTest`, `WorkoutStepRulesTest`, `TimeCoachEngineTest` (generate ok, `apply` sin test — dejar para plan tiempo), `AugeFatigueEngineTest`, `SessionDrainBoundsTest`, `OvertrainingDetectorTest`.

Comandos:
```bash
gradlew.bat testBaseDebugUnitTest --tests "*SessionEditor*"
gradlew.bat testBaseDebugUnitTest --tests "*Auge*"
gradlew.bat testBaseDebugUnitTest --tests "*SessionDrainBoundsTest*"
gradlew.bat test  # completo solo si tiempo
```

### 7.2 Compose / Instrumented (si hay `androidTest`, si no QA manual instrumentada)

- `createComposeRule` con sesión 12×5 + superset: `performTextInput` 5 chars → assert recomposiciones de `SessionEditorListItem` ≤1 (via `Modifier.reobserve` o `composeTestRule` + `printToLog`).
- Drag con `performTouchInput { longPress(dragHandle); moveBy(0,800) }` → `draggingExerciseOffset` solo en layer, `shiftById` lookup, `listState.firstVisibleItemIndex` avanza si auto-scroll (si se mantiene).
- Picker: `performTextInput("press")` → solo sheet recompone, no Screen.

### 7.3 Manual QA + Perfetto

- **Teclear:** 5 chars en `EditorMiniField` → P95 <16ms, `buildSessionListItems` no se ejecuta por cada tecla (ver `runtime-tracing`).
- **Slider:** arrastrar %RM 1s → 1 commit al soltar, no decenas.
- **Drag:** 10+ ejercicios entre partes → 0 recomposiciones por frame (capturar Perfetto `androidx.compose.runtime:runtime-tracing`), `projectedShiftFor` no aparece en composición.
- **AUGE:** teclear → trace `Dispatchers.Default` muestra 1× `computeSessionAugeComputation` (sesión editada) vs 7× antes; `catalogExerciseIndex` 0 allocs.
- **Picker:** typing → fondo no recompone, asset no recarga.

### 7.4 Build

- `gradlew.bat compileBaseDebugKotlin` (targeted, `compileDebugKotlin` ambiguo, base/health flavors)
- `gradlew.bat assembleBaseDebug --offline` tras cada fase
- `gradlew.bat testBaseDebugUnitTest` focalizado antes de `assembleDebug`

---

## 8. Documentación a actualizar

- `docs/ANDROID_UI_SCREENS_MAP.md`: sección SessionEditor → recomposición, `uiState` dividido, commit on-finish, drag offset en layer, `contentType`, `weeklyMetricsCache`.
- `docs/ARCHITECTURE.md` / `docs/ANDROID_ARCHITECTURE_MAP.md`: aclarar que drag state en `screens/sessioneditor/`, AUGE wiring cache no fórmula, Room v20 autoridad (corregir v19).
- `docs/audits/2026-08-editor-sesiones/rendimiento.md` §C4+A1 → marcar mitigado con links a `weeklyMetricsCache` y `catalogExerciseIndex`; añadir medición P95 antes/después.
- `.opencode/kpkn-map.md`: regenerar vía `/map` si se añaden helpers (`domain/sessioneditor/SessionHash.kt`) o test.
- `.opencode/memory/MEMORY.md`: anotar decisión "cache wiring sin optimización numérica, assistant bajo demanda, searchQuery fuera de uiState".
- `docs/paridad/auge-matrix.md` (si existe) o `docs/IOS_PARITY.md`: fila `SessionEditor weeklyMetrics cache (contentHash) — Android OK, iOS placeholder, backend N/A`.

> Código y esquema Room v20 son autoridad si docs dicen v19.

---

## 9. Riesgos y mitigaciones

| Riesgo | Prob | Impacto | Mitigación |
|---|---|---|---|
| Hash colisión / falso reuse si `contentHash` excluye campo que afecta drain (supersetId, reference1RM, restTime, catalogConfigurationId) | Media | Drain stale | Incluir exactamente inputs de `computeSessionAugeComputation`: `exerciseDbId, catalogConfigurationId, trainingMode, reference1RM, restTime, supersetId, sets{reps/RPE/RIR/%RM/weight/isFailure/intensityMode/dropSets}, warmup, supersetGroups`. Tests golden Session. |
| Stale catalog tras overlay custom (usuario crea ejercicio custom durante edición) | Media | Músculos obsoletos | `catalogVersion` (size + hash keys) en cache key; `loadCustomExercisesAsync` invalida. `exerciseDatabaseByIdCache` es `@Volatile` — capturar snapshot al inicio de `recalcAndPushAuge`. |
| Settings/logs no invalidados → drain con sesgo viejo | Media | Métrica desfasada | `settingsHash` + no cachear EMA agregada o `logsHash = logs.size + lastDate`; recalcular EMA barato O(logs). |
| Memoria `perSession` retiene 7× `SessionAugeComputation` (~50KB c/u → 350KB) | Baja | — | Aceptable; limpiar al cambiar `programId/sessionId`; no `WeakReference`. |
| Assistant on-demand deja sheet vacía si se abre rápido tras editar | Baja | UX | `openSheet(AUGE)` dispara `refreshAssistantImmediate()` sin debounce; mostrar `isLoadingAssistant` en ghost cards. |
| Thread safety `weeklyMetricsCache` `@Volatile` accedido desde `Default` e `IO` | Baja | Race | Single writer `recalcAndPushAuge`; `@Volatile` + cancelar `augeJob` antes de escribir; `refreshDerivedStateImmediate:554` también escribe — coordinar. |
| Flows divididos rompen UDF / navegación | Media | Regresión | Exponer derivados con `distinctUntilChanged().stateIn`; colectar por subárbol; test flows + navegación `session-editor/{programId}/{sessionId}`. |
| Commit on-finish pierde ediciones si usuario sale sin blur | Baja | Data loss | `debounce 400ms` fallback + `onDispose { if(dirty) onCommit(localValue) }` + `saveDraftForExit` en IO ya existente `ViewModel:271`. |
| Drag offset solo en layer rompe preview anclado con `imePadding` | Baja | Visual glitch | `DragLiftPreview` lee offset dentro de `Modifier.offset{}` lambda, preview fuera de `hazeSource` ya correcto (`Screen:379` envuelve Scaffold). |
| `contentType` mal tipado mezcla pools | Baja | Más composiciones | Usar `it::class` o enum estable; testar scroll con 12 tipos. |
| Autosave cada 2s aún reescribe programa entero (B4) | Baja | I/O churn | Bien en `Dispatchers.IO` (`135-144`); dejar coalescing/diff para follow-up si trace muestra churn. No bloquea P95. |

---

## 10. Criterios de aceptación

- [ ] Teclear 5 chars en `EditorMiniField` → 1 `updateSession` + 1 `buildSessionListItems` (no por tecla), P95 <16ms, `Perfetto` muestra ≤1 recomposición de items visibles.
- [ ] Slider %RM → commit solo en `onValueChangeFinished`, 0 ciclos intermedios, `lastModifiedAtMs` no cambia si `transformed==current`.
- [ ] `remember(session.parts, exercises, supersetGroups)` no se invalida por `lastModifiedAtMs`; `updateSessionTextField` no dispara `scheduleAugeRecalc`.
- [ ] Drag con 10+ ejercicios → 0 recomposiciones de `LazyColumn` por frame, `shiftByExerciseId` lookup O(1), `applyScrollDelta` sin `clear/putAll`, `groupedParts` memoizado.
- [ ] `ExerciseEditorCard` drag lee `dragController.draggingExerciseOffset` dentro de `graphicsLayer`, `DragLiftPreview` offset en lambda.
- [ ] AUGE: teclear → 1× `computeSessionAugeComputation` (sesión editada) en `Default`, `weeklyMetricsCache` hit para resto, `catalogExerciseIndex()` 0 allocs, `assistant` 0 invocaciones hasta abrir sheet AUGE.
- [ ] Picker typing → solo sheet interior recompone, `catalog=exerciseCatalogSnapshot()` no se evalúa por tecla, asset V2 no recarga por apertura, `exactMatch` no hace `flatMap` por tecla.
- [ ] `items(key=stableKey, contentType=...)` presente, `groupedParts`/`anchorNames`/`allExercises` memoizados, `supersetGroup` pasado ya resuelto.
- [ ] `compileBaseDebugKotlin` + `assembleBaseDebug` verdes; `testBaseDebugUnitTest` con nuevos tests pasa; `Perfetto` runtime-tracing confirma.
- [ ] Docs `ANDROID_UI_SCREENS_MAP.md`, `kpkn-map.md`, `MEMORY.md` actualizados; `Room v20` autoridad documentada.

---

## 11. Plan de entrega (requiere aprobación)

1. **Aprobación explícita** de este plan (pipeline `request_approval` → `construction`). No editar código hasta `pipeline.start`.
2. `constructor_kpkn` ejecuta por fases en rama corta, commits atómicos por archivo:
   - Fase 0: `SessionEditorViewModel.kt` (exerciseIndex O(1)), `SessionEditorScreen.kt` (groupedParts), `ExercisePickerV2Catalog.kt` (allDefinitions), `SessionEditorScreen.kt` (contentType)
   - Fase 1: `SessionEditorFormFields.kt`, `InlineSetRow.kt`, `SetEditorCards.kt`, `SessionEditorViewModel.kt` (updateSession guard), `SessionEditorScreen.kt` + `SessionEditorAugeComputation` (keys estructurales), `SessionEditorViewModel.kt` (textField)
   - Fase 2: `SessionEditorScreen.kt`, `SessionEditorScrollRenderer.kt`, `ExerciseEditorCard.kt`, `SessionEditorVisuals.kt`, `SessionEditorDragController.kt`
   - Fase 4: `SessionEditorViewModel.kt` + `SessionEditorAugeComputation.kt` + `domain/sessioneditor/SessionHash.kt` + `SessionEditorViewModelAugeActions.kt` + `SessionEditorUiUtils.kt`
   - Fase 3: `SessionEditorViewModel.kt`, `SessionEditorModels.kt`, `SessionEditorScreen.kt`, `SessionEditorScrollRenderer.kt`, `SessionEditorSheets.kt`, `ExercisePickerSheet.kt`
   - Fase 5: `data/db/AugeDao.kt`, `ExerciseEditorCard.kt`, etc.
3. Añadir tests `SessionEditorUpdateSessionTest.kt`, `SessionEditorAugeComputationTest.kt`, `SessionEditorDragControllerTest.kt`.
4. `gradlew.bat compileBaseDebugKotlin` → `gradlew.bat testBaseDebugUnitTest --tests "*SessionEditor*" --tests "*Auge*"` → `gradlew.bat assembleBaseDebug --offline`.
5. Auditor revisa diff vs plan; `pipeline submit_audit` → `auditing`.
6. Si auditor pide Fase 2 VM (mover drag state a `ViewModel` con `asStateFlow()`), abrir follow-up.

---

## 12. Alternativas descartadas

- **Debounce 300ms más largo para AUGE en lugar de cache:** sigue O(semana) y no evita `catalogIndex` allocs; cache es 7× win sin cambiar debounce.
- **Migrar todo a `boundsInParent` puro:** tocaría todos `onGloballyPositioned`; compensación scroll ya en `SessionEditorDragController` (fix 2026-08-06) y es menos churn.
- **Column + verticalScroll sin LazyColumn (desactivar virtualización):** con 30+ ejercicios costo composición alto, rompe `rememberLazyListState` hero y `hazeSource`.
- **Strong skipping off / @Stable forzado en UiState:** `UiState` contiene `Session` con `List` → unstable inevitable; dividir flows es solución idiomática, no anotar.

---

## 13. Referencias exactas (para auditor)

- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorScreen.kt:145,188-193,209,216,221,273,291,299,312-346,374-417,440-448,502,507,511,516,566,651,672,700,732`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorDragController.kt:16-42,68-93,107-131,155-229,279-319`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorScrollRenderer.kt:73,77-82,108,134,180,225,230,245,256,275,281,391,424,500,573,773-781`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModel.kt:121-128,151-157,167-176,215-254,506-514,517-534,542-552,564-650,658-677`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModelStructure.kt:108,383,450`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorAugeComputation.kt:74-95,295-504`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModelAugeActions.kt:288-305,348-378`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorFormFields.kt:72-77`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/InlineSetRow.kt:113-121,328-344,578-580,599-624`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/SetEditorCards.kt:83,107-112,552-558`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/ExerciseEditorCard.kt:98-110,147-181,220-227,253`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/sheets/ExercisePickerSheet.kt:114-133`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/sheets/SessionEditorSheets.kt:344,367,449,644`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/sheets/AssistantSheet.kt:285,291`
- `android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeFatigueEngine.kt:276` + `SessionEditorAugeComputation.kt:331-367` (drain scaling)
- `android-native/app/src/main/java/com/example/kpkn/data/exercises/ExerciseDatabase.kt:93,96,99`
- `android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt:37-61`
- `android-native/app/schemas/` + `data/db/KpknDatabase.kt` v20 autoridad

---

> **Siguiente paso:** aprobar este plan para pasar a `construction`. No se editará código de producto hasta `pipeline.start` + `request_approval` confirmados. Código y esquema Room v20 son autoridad si docs dicen v19.
