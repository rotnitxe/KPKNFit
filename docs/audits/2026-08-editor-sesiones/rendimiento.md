# Auditoría de Rendimiento — Editor de Sesiones (KPKNFit)

**Fecha:** 2026-08 · **Alcance:** `screens/sessioneditor/` (Android, Jetpack Compose, Kotlin 2.2.10)
**Motivo:** reporte de usuario: "el editor se siente pesado".
**Método:** solo lectura y análisis de código; ningún archivo de código fue modificado.

---

## Veredicto ejecutivo

El "pesado" no procede de un único punto, sino de un bucle que se dispara **en cada tecla / tick de slider**:

```
EditorMiniField / Slider (commit por evento)
  → updateSet → updateExercise → updateSession
  → session.copy estructural + lastModifiedAtMs = now + equals profundo
  → _uiState.update (uiState monolítico nuevo)
  → recomposición de TODA la pantalla
  → remember(session) invalidado → buildSessionListItems de toda la lista
  → (300 ms después) recálculo AUGE de TODA la semana
  → _uiState.update → otra recomposición masiva
```

En **drag & drop**, el offset por-frame se lee en la raíz y se pasa por parámetro a todos los items → la `LazyColumn` entera se recompone a ~60 fps, y cada item evalúa `projectedShiftFor` (O(n)) por composición → ~O(n²) por frame.

---

## CRÍTICOS

### C1. El offset de drag se lee en la raíz y se pasa por parámetro a TODOS los items → recomposición masiva por frame

**Evidencia:**

- `SessionEditorScreen.kt:221` — `var draggingExerciseOffset by dragController::draggingExerciseOffset` (también `:216` `draggingPartOffsetY`, `:219` `draggingExerciseId`): estados delegados leídos en el ámbito de la pantalla.
- `SessionEditorScreen.kt:511` — `draggingExerciseOffset = draggingExerciseOffset` y `:516` — `draggingPartOffsetY = draggingPartOffsetY` pasados a cada `SessionEditorListItem` dentro de `items(...)` de la LazyColumn.
- `SessionEditorDragController.kt:~229` — `draggingExerciseOffset += delta` dentro de `updateExerciseDrag(delta, session)`: se actualiza en cada evento pointer (múltiples veces por frame).
- `SessionEditorScrollRenderer.kt:76` — el parámetro `draggingExerciseOffset: Offset` llega a `SessionEditorListItem` y de ahí a `LooseExerciseItem` (:367), `PartExerciseItem` (:443), `LooseSupersetItem`/`PartSupersetItem` → y a `ExerciseEditorCard` como `dragOffset` (`SessionEditorScrollRenderer.kt:396-397` y `:476-477`).
- Cada item evalúa en composición `projectedShiftFor(...)` dentro de `animateFloatAsState(targetValue = ...)`: `SessionEditorScrollRenderer.kt:134, 180, 230, 281`; la función (`SessionEditorScreen.kt:307-346`) hace `firstOrNull`/`indexOfFirst` sobre listas de la sesión → O(n) por item.

**Impacto:** cada frame de drag recompone la pantalla completa y **todos** los items visibles (no solo fase layout/draw). Al evaluar `projectedShiftFor` en composición, el coste es ~O(n²) en composiciones por frame. Es la causa directa del drag "pesado/trabado".

**Fix:**

1. Pasar a los items solo `draggingExerciseId: String?` y los flags ya calculados (`isDragging`, `isDropTarget`). El offset debe leerse **solo dentro del `graphicsLayer { }` del ítem arrastrado** (`ExerciseEditorCard.kt:~221-227` ya tiene ese bloque): `translationY = if (isDragging) dragController.draggingExerciseOffset.y else 0f` — lectura de estado en fase de layer → cero recomposición.
2. `DragLiftPreview` (`SessionEditorVisuals.kt:155-205`) ya usa `.offset { }` lambda: cambiar el parámetro `offset` por lectura de `dragController.draggingExerciseOffset` **dentro** del lambda `offset { IntOffset(...) }`.
3. Precalcular el mapa de desplazamientos una vez por cambio de target: `val shiftByExerciseId = remember(draggingExerciseId, exerciseDropTargetKey, exerciseDropTargetIndex) { buildShiftMap(...) }` y hacer lookup O(1) por item en lugar de `projectedShiftFor` por composición.

### C2. `uiState` monolítico colectado en la raíz y pasado entero a cada item de la LazyColumn

**Evidencia:**

- `SessionEditorScreen.kt:145` — `val uiState by viewModel.uiState.collectAsStateWithLifecycle()`: un único StateFlow.
- `SessionEditorModels.kt:149-219` — `SessionEditorUiState` tiene ~60 campos (session, sheets, AUGE, roadmap, logs, templates, snackbar, etc.).
- `SessionEditorScreen.kt:507` — `uiState = uiState` se pasa a `SessionEditorListItem` por cada item de la LazyColumn; su firma lo recibe completo (`SessionEditorScrollRenderer.kt:73`). En el renderer los únicos usos reales son `uiState.collapsedPartIds` (:108) y `uiState.competitionMovementIds` (:161, :203, :257, :298).
- El mismo patrón en `SessionEditorSheets(uiState = uiState)` (`SessionEditorScreen.kt:732`) y `AssistantGlassOverlay(uiState = uiState)` (:699).
- `updateSession` (`SessionEditorViewModel.kt:517-532`) crea un `uiState` nuevo en CADA edición (data class con equals distinto).

**Impacto:** **cualquier** `updateUi { }` — edición de set, tecla en el nombre, `snackbarMessage`, `searchQuery`, `togglePartCollapsed`, el push de `augeSummary` tras cada recálculo — crea un `uiState` nuevo → recomposición de la raíz completa y de **todos** los items visibles, incluidos `SessionHero` + `SessionContextNavigator` (componente de ~45 KB, `SessionEditorScreen.kt:440-500`). Los items no pueden saltarse la recomposición porque reciben un parámetro siempre distinto (el compilador en modo strong skipping compara por equals y el objeto cambia).

**Fix:**

1. No pasar `uiState` a los items: pasar solo lo que usan (`collapsedPartIds: Set<String>`, `competitionMovementIds: Set<String>`).
2. Exponer en el ViewModel flows divididos con `distinctUntilChanged()`: `session`, `collapsedPartIds`, `sheet`, `augeSummary`, `competitionMovementIds`, etc., y colectar cada uno únicamente en el subárbol que lo consume (hero, sheet host, FAB AUGE, navigator). Un snackbar o un push de AUGE dejará de recomponer la lista.
3. El snackbar es canal de eventos (ver B3), no estado: así ni siquiera entra en `uiState`.


### C3. Commit por tecla / por tick de slider → copia estructural de toda la sesión + pipeline completo por evento

**Evidencia:**

- `SessionEditorFormFields.kt:74-77` — `EditorMiniField`: `onValueChange = { localValue = it; onCommit(it) }` → commit (y por tanto update del modelo) **por tecla** en todos los campos de texto del editor.
- `InlineSetRow.kt:578-580` — `Slider(value = sliderPercent.toFloat(), onValueChange = { onUpdate { current -> current.copy(targetPercentageRM = it.toDouble(), intensityMode = IntensityMode.LOAD) } })` → `updateSet` en **cada tick del slider**.
- `SetEditorCards.kt:109-111` (texto commit por tecla) y `:554-556` (slider con commit por tick).
- Cadena de actualización: `updateSet` (`SessionEditorViewModelStructure.kt:450`) → `updateExercise` (:383) → `updateSession` (`SessionEditorViewModel.kt:517-532`):
  - `transformExercises` (`SessionEditorSessionHelpers.kt:143-163`) mapea **todas** las parts y todos los exercises (O(n) de copia estructural aunque cambie un solo campo de un solo set).
  - `updated.copy(lastModifiedAtMs = System.currentTimeMillis())` (:522) → la sesión SIEMPRE tiene nueva identidad.
  - `hasUnsavedChanges = updated != state.originalSession` (:527) → equals profundo de toda la sesión por evento.
  - Llama siempre `scheduleAugeRecalc()` (:531) y `scheduleAutoSave()` (:532).
- `SessionEditorScreen.kt:273` — `remember(session, uiState.collapsedPartIds) { buildSessionListItems(...) }`: como `session` cambia de identidad por `lastModifiedAtMs` en cada evento, se reconstruye **toda** la lista de items por tecla/tick (incluye `SupersetRules.orderedMembers` por superset, `SessionListItems.kt:100-140`, que a su vez llama `session.allSupersetGroups()` + `session.allExercises().filter ...` por grupo — `SupersetRules.kt:195-203`).

**Impacto:** por cada tecla o cada tick del slider: copia estructural O(n) + equals profundo O(n) + rebuild completo de la lista de items + recomposición de toda la pantalla (por C2) + reseteo de los debounces de AUGE y autosave. Un gesto de slider de 1 segundo genera decenas de ciclos completos. Es la causa principal del "pesado" al editar campos.

**Fix:**

1. `EditorMiniField`: commit en `onDone` / pérdida de foco, o `snapshotFlow { localValue }.debounce(400ms)`; no en cada `onValueChange`.
2. Sliders: estado local mientras se arrastra y commit en `onValueChangeFinished` (Compose M3 lo expone tanto en `Slider` como en `SliderState`).
3. En `updateSession`: si `transformed == current`, NO tocar `lastModifiedAtMs` ni emitir; sustituir el equals profundo de `hasUnsavedChanges` por un flag `dirty` que se pone a true solo cuando la transformación cambió algo.
4. Key estructural en vez de instancia: `remember(session.parts, session.exercises, session.supersetGroups, uiState.collapsedPartIds)` (o un `contentVersion` explícito) para que el timestamp no invalide el rebuild de items.


### C4. Recálculo AUGE de TODA la semana tras cada pausa de 300 ms + caché semanal declarado pero muerto

**Evidencia:**

- `SessionEditorViewModel.kt:542-552` — `scheduleAugeRecalc()`: cancela y relanza un job con `delay(300)` que llama `recalcAndPushAuge` en `Dispatchers.Default`.
- `SessionEditorViewModel.kt:564-650` — `recalcAndPushAuge` ejecuta en cadena: `TrainingEnergyEngine.estimatePlannedSession` (:574), `repository.getLogsForProgram(state.programId)` (:577), `buildAugeSummary(...)` (:579-590), `SessionAssistantEngine.evaluate(...)` completo con plantillas (:595-619), `calculateSessionTimeBreakdown(...)` (:620-626), y termina con `_uiState.update { ... augeSummary/assistantReport/ghostExerciseCards ... }` (:630-648).
- `SessionEditorAugeComputation.kt:74-95` — `buildAugeSummary` llama `computeSessionAugeComputation` para la sesión actual Y para **cada una** de las sesiones de la semana: `val weeklyMetrics = weeklySessions.map { computeSessionAugeComputation(session = it, ...) }`. Cada llamada recorre todos los ejercicios/sets de esa sesión (volúmenes por músculo, drain proyectado, insights por ejercicio).
- `SessionEditorViewModel.kt:151-157` — existe `private var weeklyMetricsCache: CachedWeeklyMetrics? = null` pensado para evitar exactamente este recálculo... pero **nunca se lee ni se escribe en ningún otro punto del código**: es caché muerto. Confirmado por búsqueda global (solo aparece la declaración).
- `SessionEditorViewModel.kt:659-676` — `updateSessionTextField` (nombre/descripción de la sesión) también llama `scheduleAugeRecalc()` aunque el nombre no afecta a ningún cálculo AUGE.
- `SessionEditorAugeActions`/helpers resuelven músculos por ejercicio con `exerciseIndex` dentro del bucle (ver A1), multiplicando el coste dentro de `SessionAssistantEngine.evaluate`.

**Impacto:** tras cada pausa de 300 ms al teclear se reejecuta todo el motor AUGE sobre 6-7 sesiones (volúmenes por músculo, drains, per-exercise insights) más el evaluador completo del asistente de sesión. Aunque corre en `Dispatchers.Default` (bien ubicado), la CPU compite con el main thread y el `_uiState.update` final provoca otra recomposición masiva (C2) justo cuando el usuario cree haber terminado de teclear. Es el "pesado" que aparece con retardo tras cada edición.

**Fix:**

1. Cablear `weeklyMetricsCache`: cachear `computeSessionAugeComputation` por sesión (key = `session.id + hashDeContenido`) y recomputar solo la sesión editada; el resto de métricas semanales se reutilizan.
2. `SessionAssistantEngine.evaluate` → ejecutarlo solo al abrir la sheet AUGE o con debounce largo (2-3 s). Ya existe el patrón "TimeCoach bajo demanda" (`SessionEditorViewModel.kt:626` comentario) — aplicarlo también al assistant.
3. NO llamar `scheduleAugeRecalc()` desde `updateSessionTextField` (nombre/descripción no afectan ni a drain ni a volúmenes).


---

## ALTOS

### A1. `exerciseIndex` reconstruye el índice del catálogo en cada acceso

**Evidencia:**

- `SessionEditorViewModel.kt:121-128` — getter:
  ```kotlin
  internal val exerciseIndex: Map<String, ExerciseMuscleInfo>
      get() {
          val base = exerciseCatalogSnapshot().associateBy { it.id.lowercase() }
          val aliasEntries = catalogSearchRedirects().mapNotNull { ... }.toMap()
          return base + aliasEntries
      }
  ```
  `exerciseCatalogSnapshot()` es `exerciseDatabaseCache.toList()` (`data/exercises/ExerciseDatabase.kt:93`) → lista nueva del catálogo completo + mapa nuevo en **cada** acceso.
- Se accede 2× por recálculo AUGE (`SessionEditorViewModel.kt:583` y `:607`).
- Se accede **por ejercicio** dentro de bucles: `SessionEditorViewModelAugeActions.kt:292-294` (`ExerciseMuscleResolver.effectiveMuscles(exercise, exerciseIndex)` dentro del lambda `updateExercise` que se ejecuta por ejercicio) y `:372-373` (mismo patrón en `exerciseMatchesPrimaryMuscle`).
- `catalogSearchRedirects()` devuelve `emptyMap()` (`ExerciseDatabase.kt:99`) → la mitad del trabajo del getter es siempre inútil.
- Existe `catalogExerciseIndex()` (`ExerciseDatabase.kt:96`) que devuelve `exerciseDatabaseByIdCache` en O(1) y está precacheado globalmente — NO se usa aquí.

**Impacto:** mapa de miles de entradas reconstruido O(catálogo) por acceso → O(catálogo × ejercicios) en acciones AUGE y 2× por recálculo. Presión de GC innecesaria en hilos compartidos.

**Fix:** usar `catalogExerciseIndex()` directamente, o cachear el índice en una `val` perezosa del ViewModel (`by lazy`), eliminando la rama de alias (ya es vacía). En los bucles de `SessionEditorViewModelAugeActions.kt`, izar `val index = exerciseIndex` antes del bucle.

### A2. La búsqueda del picker recompone toda la pantalla y recopia el catálogo por tecla

**Evidencia:**

- `SessionEditorViewModelStructure.kt:108` — `setSearchQuery(query) = updateUi { it.copy(searchQuery = query) }` → C2: recomposición de la raíz por tecla mientras se escribe en el buscador.
- `SessionEditorSheets.kt:449` y `:644` — `catalog = exerciseCatalogSnapshot()` se evalúa **en cada recomposición del host de sheets** (que recibe `uiState` completo, `SessionEditorSheets.kt:344` → recompone por cualquier cambio de `uiState`) → `toList()` del catálogo completo por tecla.
- `ExercisePickerSheet.kt:119-124` — el composable sólo renderiza `ExercisePickerV2Catalog(...)` y hace `return` inmediatamente después: todo el bloque legacy posterior (`fullCatalog = remember(catalog, customExercises) {...}`, filtros, chips, lista) es **código muerto** cuyo parámetro `catalog` sí se evalúa en la llamada.
- `ExercisePickerSheet.kt:117-119` — `remember(v2Context) { ApprovedAssetExerciseCatalogRepositoryV2(v2Context) }` + `LaunchedEffect(v2Repository) { v2Repository.load() }`: el repo se recrea y el asset V2 se recarga de disco **en cada apertura del sheet** (Compose recrea el sheet al abrirlo).

**Impacto:** teclear en la búsqueda del catálogo = recomposición de toda la pantalla de fondo (C2) + copia del catálogo por tecla + posible recarga de asset al abrir. El picker es de las interacciones más frecuentes del editor.

**Fix:**

1. Sacar `searchQuery` de `SessionEditorUiState` (flow separado, o estado local del sheet con `rememberSaveable`).
2. Eliminar la rama legacy muerta de `ExercisePickerSheet.kt:126+` tras el `return` (y el parámetro `catalog` si queda sin uso); donde aún se necesite el catálogo, pasar `remember { exerciseCatalogSnapshot() }` o el índice cacheado.
3. `ApprovedAssetExerciseCatalogRepositoryV2`: singleton en contenedor de aplicación (o `remember` a nivel de Activity) para no recargar el asset por apertura.


### A3. `projectedShiftFor` O(n) invocado por item en composición durante el drag

**Evidencia:**

- `SessionEditorScrollRenderer.kt:134-141, 180-187, 230-237, 281-288` — cuatro ramas (`LooseExercise`, `PartExercise`, `LooseSuperset`, `PartSuperset`) evalúan `targetValue = if (draggingExerciseId != null) projectedShiftFor(...)` como argumento de `animateFloatAsState` en fase de composición.
- `SessionEditorScreen.kt:307-346` — implementación de `projectedShiftFor`: `session.allExercises()`/`parts.firstOrNull`, `targetList.indexOfFirst`, `sourceList.indexOfFirst` → O(n) por llamada.
- `SessionEditorScreen.kt:566` — `projectedShiftFor = ::projectedShiftFor` se pasa a TODOS los items.

**Impacto:** combinado con C1, se re-evalúa en cada item visible en cada frame de drag → ~O(n²) en composición por frame.

**Fix:** el del C1 punto 3: mapa de desplazamientos precalculado con `remember(draggingExerciseId, exerciseDropTargetKey, exerciseDropTargetPartId, exerciseDropTargetIndex)` (recalcular una sola vez por cambio de target, que sucede unas pocas veces por drag, no por frame) y lookup O(1) por item.

### A4. `remember(session)` invalidado por `lastModifiedAtMs` en cada edición

**Evidencia:**

- `SessionEditorViewModel.kt:520-526` — `updated = transformed.copy(lastModifiedAtMs = System.currentTimeMillis())` en cada `updateSession` con cambio.
- Consumidores que se invalidan: `SessionEditorScreen.kt:273` (`buildSessionListItems`), `SessionEditorAnalytics.kt:712` (`remember(muscleName, session, ...)` del breakdown), `AssistantSheet.kt:285` (`volumeRows = remember(session)`), `SessionEditorScreen.kt:295` relanzado indirectamente vía `scrollableListItems`.

**Impacto:** cada edición de un set re-crea la sesión con timestamp nuevo → todos los `remember(session)` del árbol se recomputan aunque la estructura visible no haya cambiado.

**Fix:** el de C3 puntos 3-4: no estampar timestamp si no hubo cambio, y usar keys estructurales (contenido) en lugar de la instancia. Secundariamente, separar `lastModifiedAtMs` del equals para las keys de composición (p.ej. un `contentVersion` o comparador de contenido).

### A5. `ExercisePickerV2Catalog — exactMatch` re-evalúa flatMap del catálogo por tecla

**Evidencia:** `ExercisePickerV2Catalog.kt:398-404` —
```kotlin
val exactMatch = remember(query, catalog, customExercises) {
    ExerciseMatchLexicon.hasExactMatch(
        query = query,
        definitions = catalog.families.flatMap { it.definitions },   // flatMap completo
        customExercises = customExercises,
    )
}
```
`remember(query, ...)` se invalida en cada tecla → `catalog.families.flatMap { it.definitions }` (todo el catálogo) + matching por tecla.

**Fix:** precalcular `val allDefinitions = remember(catalog) { catalog.families.flatMap { it.definitions } }` (ya existe `definitionsById`, línea 279, que contiene los mismos datos) y cambiar la key a `remember(query, definitionsById, customExercises)`.


---

## MEDIOS

### M1. `groupedParts` recomputado en cada recomposición (sin remember)

**Evidencia:** `SessionEditorScreen.kt:299` — `val groupedParts = session.parts.filterNot { it.isUncategorizedPart() }` ejecutado en el cuerpo del composable. Se pasa a `SessionEditorListItem` (:506), a `onDragEnd` de parts y a `dragController.updatePartDrag(deltaY, groupedParts)` / `endPartDrag(groupedParts)`.

**Impacto:** asignación nueva (instancia distinta) por recomposición; llega como parámetro a los items (les quita elegibilidad para skip en algunos casos) y al drag handler.

**Fix:** `val groupedParts = remember(session.parts) { session.parts.filterNot { it.isUncategorizedPart() } }`.

### M2. `resolveRelationshipAnchorName` O(n) con string ops por tarjeta por recomposición

**Evidencia:** `SessionEditorUiUtils.kt:144-155` —
```kotlin
internal fun resolveRelationshipAnchorName(session, exercise): String? {
    val anchorId = exercise.relativeToCanonicalExerciseId ?: return null
    return session.allExercises()
        .firstOrNull { it.id != exercise.id && it.resolvedCanonicalExerciseId() == anchorId }
        ?.name ?: anchorId
}
```
Llamado por cada `ExerciseEditorCard`: `SessionEditorScrollRenderer.kt:423` (Loose), `:502` (Part), `:578` (Superset miembro). `allExercises()` es `exercises + parts.flatMap { it.exercises }` (`Session.kt:61`) → lista nueva por llamada; `resolvedCanonicalExerciseId()` hace lowercase/trim por candidato.

**Impacto:** O(n) con allocs por tarjeta por recomposición → O(n²) en sesiones grandes tras cada edición (C2 recompone todas las tarjetas).

**Fix:** una sola vez por cambio estructural: `val anchorNames = remember(session.allExercises()) { session.allExercises().associate { it.resolvedCanonicalExerciseId() to it.name } }`; lookup O(1) por tarjeta.

### M3. `allExercises()` en raíz / por recomposición

**Evidencia:** `SessionEditorScreen.kt:651` (`session.allExercises().isNotEmpty()` para showTimeFab), `:672` (`previewExercise = draggingExerciseId?.let { ... session.allExercises().firstOrNull { it.id == activeId } }`), y en `SessionEditorAnalytics.kt` helpers. También `Session.kt:61` confirma que crea lista nueva.

**Fix:** `val allExercises = remember(session.exercises, session.parts) { session.allExercises() }` en la raíz y pasarlo a quien lo necesite.

### M4. Supersets re-resueltos por item

**Evidencia:** `SessionEditorScrollRenderer.kt:224` y `:265` — `session.allSupersetGroups().firstOrNull { it.id == listItem.groupId }` por item superset; `Session.kt:37-42` — si `supersetGroups` está vacío, `legacySupersetGroups()` llama `allExercises()` varias veces por grupo. Además `memberIds.mapNotNull { id -> ...exercises.firstOrNull { it.id == id } }` en `:226-228` y `:267-269` → O(m·n) por recomposición.

**Fix:** en buildSessionListItems ya se resuelven los grupos; pasar `supersetGroup` (o el mapa id→group precalculado con `remember`) a los items en lugar de rebuscarlos.


### M5. Auto-scroll: reconstrucción de mapas de bounds por frame + loop a 16 ms

**Evidencia:**

- `SessionEditorDragController.kt:applyScrollDelta` (~:110-142) — en cada scroll durante drag ejecuta `frozenExerciseBounds.mapValues`, `frozenPartContentBounds.mapValues`, y reconstruye hasta 4 mapas vivos (`partBounds`, `exerciseBounds`, `partContentBounds`, `looseContentBounds`) con `clear()` + `putAll(...)`. Son mapas de Compose Snapshot (`mutableStateMapOf`) → cada mutación ejecuta snapshot writes.
- `SessionEditorScreen.kt:371-417` — `LaunchedEffect(draggingExerciseId, draggingPartId, lazyColumnWindowBounds)` con `while (isActive) { ... delay(16) }`: aunque el dedo esté lejos de los bordes, el loop sigue vivo durante todo el drag calculando pointerY y thresholds.

**Impacto:** durante auto-scroll prolongado, rebuildea los mapas por frame → churn de GC + snapshot apply. El loop despierta siempre (aunque es razonable, se puede optimizar el caso `delta == 0f`).

**Fix:** mutar solo entradas afectadas (o iterar in-place); salir temprano del loop cuando `delta == 0f` (no recalcular pointer cada 16 ms si el dedo no se mueve — cachear último delta≠0); considerar `set` directo en lugar de `clear/putAll`.

### M6. `updateExerciseDrag` filtra parts por frame

**Evidencia:** `SessionEditorDragController.kt:225` — `session.parts.filterNot { it.isUncategorizedPart() }` al inicio de `updateExerciseDrag` (cada evento pointer).

**Fix:** pasar el groupedParts precalculado (mismo M1) como argumento, en lugar de recomputarlo por frame.

### M7. Sheets y FABs reciben objetos gordos

**Evidencia:** `SessionEditorScreen.kt:732` — `SessionEditorSheets(uiState = uiState, ...)`; `:699` — `AssistantGlassOverlay(uiState = uiState, templates = allTemplates, ...)`; `:640` — `HeroGlassFab(summary = uiState.augeSummary, ...)`. Además `allTemplates` se colecta en raíz (:146).

**Impacto:** cualquier cambio de `uiState` (C2) recompone los hosts de sheets y FABs aunque la mayoría del contenido esté oculto.

**Fix:** pasar solo las proyecciones necesarias (sheet actual, augeSummary, templates, etc.) o subscribir cada subárbol a su propio flow con `distinctUntilChanged`.

### M8. `LaunchedEffect(session, uiState.collapsedPartIds)` se relanza por tecla

**Evidencia:** `SessionEditorScreen.kt:209` — key `session` (identidad nueva por edición, A4) → `dragController.pruneBounds(...)` (O(n) sobre ejercicios) en cada edición de cualquier set.

**Fix:** misma solución de key estructural que C3-4 / M3: keyed por el set de ids de ejercicios/parts realmente presentes, no por la instancia.


### M9. `loadHistory` con N+1 consultas a feedback

**Evidencia:** `SessionEditorViewModel.kt:506-516` —
```kotlin
val logs = repository.getLogsForSession(currentSession.id).sortedByDescending { it.date }
val feedbackByLogId = logs.mapNotNull { log ->
    augeRepository.getFeedbackForLog(log.id)?.let { log.id to it }
}.toMap()
```

**Impacto:** una consulta Room por log; con historiales grandes es una cadena de N queries (I/O real).

**Fix:** API batch `getFeedbackForLogs(ids: List<String>)` o JOIN en Room.

### M10. `predictedWeights`/`predictedMetrics` recomputan todos los sets del ejercicio por cada edición

**Evidencia:** `ExerciseEditorCard.kt:169-178` —
```kotlin
val predictedWeights = remember(exercise.trainingMode, exercise.reference1RM, exercise.prFor1RM, exercise.sets) {
    exercise.sets.associate { set -> set.id to calculateSuggestedLoad(exercise, set) }
}
val predictedMetrics = remember(exercise.trainingMode, exercise.sets) {
    exercise.sets.associate { set -> set.id to calculateEstimatedMetric(exercise, set) }
}
```
Cualquier cambio en `exercise.sets` (cualquier set, cualquier campo) invalida y recalcula para TODOS los sets del ejercicio (`calculateSuggestedLoad`/`calculateEstimatedMetric` no son triviales: miran capacity, percent, hybrid 1RM...).

**Impacto:** acotado al ejercicio editado (los demás ejercicios mantienen identidad), pero añade trabajo por tecla en cada edición.

**Fix:** cachear por `set.id + hashDeCampos relevantes`, o asociar solo sets cuyo input cambió (diff previo) en lugar de re-ejecutar todos.

---

## BAJOS

### B1. Falta `contentType` en items de la LazyColumn principal

**Evidencia:** `SessionEditorScreen.kt:502` — `items(scrollableListItems, key = { it.stableKey })` sin `contentType`. `SessionListItems.kt` define tipos heterogéneos (Hero, CompetitionEditor, PartHeader, PartExercise, PartSuperset, LooseExercise, LooseSuperset, PartAddExercise, AddActions).

**Impacto:** el pool de reuse mezcla composiciones de tipos distintos al hacer scroll (más composiciones nuevas de las necesarias).

**Fix:** añadir `contentType = { it::class }` (o un enum en `SessionListItem`); `ExercisePickerV2Catalog.kt` ya lo hace (:530, :548) y puede servir de referencia.

### B2. Snackbar como estado dentro de `uiState` (doble update + recomposiciones)

**Evidencia:** `SessionEditorScreen.kt:187-193` — `LaunchedEffect(uiState.snackbarMessage)` muestra y luego llama `viewModel.clearSnackbarMessage()` → dos `updateUi` encadenados. Múltiples puntos en el VM copian `snackbarMessage` dentro del estado monolítico (C2).

**Fix:** canal de eventos tipo `Channel<EditorEvent>`/`SharedFlow(replay=0)` para mensajes one-shot; fuera de `uiState`.

### B3. `LaunchedEffect(pendingAutoExpandExerciseId, scrollableListItems)` relanzado por rebuild

**Evidencia:** `SessionEditorScreen.kt:291` — con `scrollableListItems` nuevo en cada edición (A4), el efecto se relanza y repite `lazyColumnIndexForExercise` (lineal) innecesariamente.

**Fix:** key solo `pendingAutoExpandExerciseId` + computed estable (o mover el cálculo al interior del effect con la lista actual leída en frío).

### B4. Autosave: Room upsert del programa completo cada 2 s tras edición

**Evidencia:** `SessionEditorViewModel.kt:241-254` (`persistRecoverableSession` → `repository.upsertSessionInProgram` → `ProgramRepository.kt:129-145` `updateProgram(updated)`), disparado por `scheduleAutoSave` (:135-144, debounce 2000ms, `Dispatchers.IO` ✓).

**Impacto:** fuera del main thread (bien), pero reescribe la entidad de programa completa (con todas las semanas) frecuentemente; churn de I/O/serializer.

**Fix:** persistir solo la sesión modificada (entidad de sesión o diff), o añadir coalescing cuando el programa no ha entrado en ventana de guardado global.


---

## Lo que ya está bien (no tocar)

- **Keys estables en LazyColumn**: `SessionEditorScreen.kt:502` con `stableKey` por tipo (`SessionListItems.kt:19-66`) y `SessionEditorScrollRenderer.kt:391, 468` con `key("part|exercise.id")`. Evita recreaciones de estado al reordenar.
- **Catálogo cacheado**: `catalogExerciseIndex()` O(1) (`ExerciseDatabase.kt:96`); el shell del editor ya lo usa bien (`SessionEditorScreen.kt:199`).
- **Búsqueda picker**: debounce 150 ms + `Dispatchers.Default` + `remember` de resultados + keys + contentType (`ExercisePickerV2Catalog.kt:300-332, 530, 548...`).
- **Motor AUGE bien ubicado**: corre en `Dispatchers.Default` con debounce (`SessionEditorViewModel.kt:542-552`); el problema es el alcance y la caché muerta, no el hilo.
- **Autosave/IO correctamente deferidos**: `scheduleAutoSave` debounce 2 s en `Dispatchers.IO` (`SessionEditorViewModel.kt:135-144`); `saveDraftForExit` en IO (:271-277).
- **`graphicsLayer` para shifts de drag**: la intención es correcta en `SessionEditorScrollRenderer.kt:153-155, 198-200` y `ExerciseEditorCard.kt:220-227`; lo que falla es la fuente de estado (C1/A3).
- **Strong skipping del compilador** (Kotlin 2.2.10 + compose compiler): las lambdas inline con capturas estables están memoizadas por defecto → las lambdas de los items NO son el cuello; lo son los valores cambiantes (`uiState`, `session`, offsets).
- **`onGloballyPositioned` solo en bounds de drag**: no se usa para disparar recomposiciones de contenido (bien delimitado).
- **Weak reads bien hechas**: `ExerciseEditorCard.kt:147-166` (LaunchedEffect al cambiar ids/RM en lugar de observers por frame), `InlineSetRow.kt:113-121` (`sliderPercent` con remember multi-key), drag controller `beginExerciseDrag`/`updateExerciseDrag`/`endExerciseDrag` desacoplados de Compose state donde se puede.

## Plan de ataque sugerido (impacto/esfuerzo)

1. **C3 + A4 — commit on-finish en fields/sliders + no estampar timestamp sin cambio + key estructural** (elimina el lag al teclear; ~2-4 h). Mayor ROI inmediato: es el caso de uso más frecuente.
2. **C1 + A3 — offset solo en layer del ítem arrastrado + mapa de shifts precalculado** (drag fluido; ~3-5 h). Cambio localizado: no afecta al VM.
3. **C2 — flows divididos + items sin `uiState`** (reduce drásticamente recomposiciones en todo el editor; ~4-6 h). Es el refactor estructural; hacer después de 1-2 para medir mejora aislada.
4. **C4 + A1 — cablear `weeklyMetricsCache` + `catalogExerciseIndex()` + assistant bajo demanda** (quita trabajo de fondo inútil; ~3-4 h). Bajo riesgo.
5. **A2 — search fuera de uiState + legacy muerto + repo singleton** (picker fluido; ~2 h).
6. M1-M10 por presupuesto restante; B1-B4 como higiene.

## Verificación propuesta tras los cambios

- Macrobenchmark/FrameTimingMetric en: (a) teclear 5 caracteres en un `EditorMiniField`, (b) mover el slider de %RM, (c) drag de un ejercicio entre partes con 10+ ejercicios, (d) abrir picker y teclear "press".
- Objetivo: P95 de frame < 16 ms en (a)-(c); recomposición de items visibles ≤ 1 por edición de set (instrumentando `Modifier.reobserve` o logs de layout inspector).
- Perfetto trace con `androidx.compose.runtime:runtime-tracing` para confirmar que edits ya no re-entran al `SessionEditorListItem` del resto de ejercicios.

