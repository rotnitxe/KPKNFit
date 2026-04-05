# Plan Maestro: ProgramEditor + SessionEditor + WorkoutSession
## Migración PWA → Kotlin/Compose

**Objetivo:** Implementar las 3 pantallas faltantes para conectar el flujo completo de entrenamiento en la app Kotlin. Sin AUGE/AI (va después). Sin nutrición.

**Flujo completo objetivo:**
```
ProgramsScreen → [crear nuevo] → ProgramCreatorWizard → ProgramDetailScreen
ProgramsScreen → [editar existente] → ProgramEditorScreen → ProgramDetailScreen
ProgramDetailScreen → [editar sesión] → SessionEditorScreen → back
ProgramDetailScreen → [iniciar entrenamiento] → WorkoutScreen → back
```

---

## BASE YA EXISTENTE (NO recrear)

### Modelos de datos ✅
- `data/models/Program.kt` — Program, Macrocycle, Block, Mesocycle, ProgramWeek, Loop, ProgramEvent
- `data/models/Session.kt` — Session, SessionPart, Exercise, ExerciseSet
- `data/models/WorkoutLog.kt` — WorkoutLog, CompletedExercise, CompletedSet, OngoingWorkoutState
- `data/models/ExerciseMuscleInfo.kt` — ExerciseMuscleInfo, InvolvedMuscle, MuscleRole
- `data/splits/SplitTemplates.kt` — SplitTemplate, SPLIT_TEMPLATES (28 templates completos)

### Repository ✅
- `data/repository/ProgramRepository.kt`:
  - `addProgram()`, `updateProgram()`, `deleteProgram()`, `getProgramById()`
  - `startWorkout()`, `updateOngoingWorkout()`, `clearOngoingWorkout()`, `ongoingWorkout: StateFlow`
  - `addWorkoutLog()`, `getLogsForSession()`
  - `history: StateFlow<List<WorkoutLog>>`

### Navegación ✅
- `navigation/Navigation.kt` — KpknRoute.ProgramEditor, KpknRoute.SessionEditor, KpknRoute.Workout ya definidos
- `MainActivity.kt` — rutas registradas con GenericScreen placeholder, reemplazar

### Domain ✅
- `domain/training/VolumeCalculator.kt` — `calculateUnifiedMuscleVolume(sessions, exerciseList)`

---

## ARCHIVOS A CREAR

### Área 1: ProgramEditor (Wizard + Advanced)
```
screens/programeditor/
  ProgramEditorScreen.kt          ← orchestrador: wizard vs editor avanzado
  ProgramEditorViewModel.kt       ← estado central
  ProgramCreatorWizard.kt         ← wizard 4 pasos para programas nuevos
  components/
    EditorTopBar.kt               ← barra superior: nombre + guardar + menú
    EditorSideSheet.kt            ← navegación entre secciones
    DetailsSection.kt             ← metadata del programa
    StructureSection.kt           ← editor inline de macro/bloque/meso/semanas
    GoalsSection.kt               ← metas 1RM por ejercicio
    EventsSection.kt              ← eventos de calendario
    VolumeHeatmapSection.kt       ← heatmap de volumen muscular (solo lectura)
    ExportSection.kt              ← exportar/duplicar programa
    SplitSelectorSheet.kt         ← selector de split (bottom sheet 2 pasos)
```

### Área 2: SessionEditor
```
screens/sessioneditor/
  SessionEditorScreen.kt          ← pantalla principal
  SessionEditorViewModel.kt       ← estado
  components/
    SessionHeaderCard.kt          ← nombre y descripción editable
    PartSectionCard.kt            ← sección/parte con nombre, color, ejercicios
    ExerciseRowCard.kt            ← ejercicio colapsado con sets expandibles
    SetRowEditor.kt               ← fila de un set: reps, peso, RPE, descanso
    ExercisePickerSheet.kt        ← buscador de ejercicios (bottom sheet)
```

### Área 3: WorkoutSession
```
screens/workout/
  WorkoutScreen.kt                ← pantalla de entrenamiento en vivo
  WorkoutViewModel.kt             ← máquina de estado del workout
  components/
    ExerciseCarouselBar.kt        ← chips horizontales con ejercicios
    SetInputCard.kt               ← card central: peso + reps + RPE input
    RestTimerCard.kt              ← countdown visual del descanso
    GhostPerformance.kt           ← hint de rendimiento previo (último log)
    FinishWorkoutSheet.kt         ← resumen final + confirmar guardar
```

---

## DETALLE DE IMPLEMENTACIÓN

---

### `ProgramEditorViewModel.kt`

```kotlin
package com.example.kpkn.screens.programeditor

// Estado UI
data class ProgramEditorUiState(
    val programDraft: Program? = null,
    val activeSection: EditorSection = EditorSection.DETAILS,
    val hasUnsavedChanges: Boolean = false,
    val isSideSheetOpen: Boolean = false,
    val isSplitChangerOpen: Boolean = false,
    val isWizardMode: Boolean = false,
    val wizardStep: WizardStep = WizardStep.NAME,
)

enum class EditorSection { DETAILS, STRUCTURE, GOALS, EVENTS, VOLUME, EXPORT }
enum class WizardStep { NAME, MODE, SPLIT, STRUCTURE, DONE }

class ProgramEditorViewModel(private val programId: String) : ViewModel() {
    // StateFlow central
    private val _uiState = MutableStateFlow(ProgramEditorUiState())
    val uiState: StateFlow<ProgramEditorUiState> = _uiState

    // init: si programId == "new" → wizard mode con draft vacío
    //       si programId es UUID → cargar programa existente del repository

    // Funciones:
    // loadOrCreate(programId)  — carga o crea draft
    // updateField(field, value) — mutación inmutable del draft (usando copy())
    // updateProgram(program) — reemplaza el draft completo
    // saveProgram() — repository.addProgram() o updateProgram() + hasUnsavedChanges=false
    // duplicateProgram() — copia con nuevo UUID, addProgram()
    // deleteProgram() — repository.deleteProgram(), navegar back
    // setActiveSection(section) — cambia panel
    // toggleSideSheet() — abre/cierra panel lateral
    // setSplitChangerOpen(open)
    // nextWizardStep() / prevWizardStep()
    // applyWizardSplit(splitTemplate, startDay) — aplica split al draft
    // addBlock(macroIdx) — push nuevo Block con Mesocycle vacío
    // addWeek(macroIdx, blockIdx, mesoIdx) — push nueva ProgramWeek
    // updateBlockName(macroIdx, blockIdx, name)
    // updateMesoGoal(macroIdx, blockIdx, mesoIdx, goal)
    // updateMesoName(macroIdx, blockIdx, mesoIdx, name)
    // deleteBlock(macroIdx, blockIdx)
    // addGoal(exerciseId, exerciseName, target) — push a exerciseGoals map
    // removeGoal(exerciseId)
    // addEvent(event) / removeEvent(id) — actualiza program.events
    // computeVolumeHeatmap(exerciseList) → List<WeekVolumeData> usando VolumeCalculator
}
```

---

### `ProgramEditorScreen.kt`

```
Estructura:
- val program = uiState.programDraft ?: return loading
- if (uiState.isWizardMode) → ProgramCreatorWizard(...)
- else → AdvancedEditor(...)

AdvancedEditor:
  Scaffold(
    topBar = EditorTopBar(name, onSave, onCancel, onDuplicate, onDelete, onToggleSideSheet)
    drawerContent = EditorSideSheet(activeSection, onNavigate)  // ModalNavigationDrawer
  ) {
    AnimatedContent(activeSection) { section ->
      when(section) {
        DETAILS → DetailsSection(program, onUpdateField, onOpenSplitChanger)
        STRUCTURE → StructureSection(program, onUpdateProgram)
        GOALS → GoalsSection(program, exerciseList, onUpdateProgram)
        EVENTS → EventsSection(program, onUpdateProgram)
        VOLUME → VolumeHeatmapSection(program, exerciseList)
        EXPORT → ExportSection(program, onDuplicate)
      }
    }
  }
  if (isSplitChangerOpen) SplitSelectorSheet(...)
```

---

### `ProgramCreatorWizard.kt`

```
Wizard de 4 pasos:

Paso 1 — NAME:
  - Campo de texto grande para nombre del programa
  - Descripción opcional (textarea)
  - Botón "Continuar" habilitado si name.isNotBlank()

Paso 2 — MODE:
  - 3 tarjetas seleccionables:
    · HYPERTROPHY: emoji 💪, descripción
    · POWERLIFTING: emoji 🏋️, descripción
    · POWERBUILDING: emoji ⚡, descripción
  - Botón "Continuar"

Paso 3 — SPLIT:
  - SplitSelectorSheet inline (gallery step únicamente, sin scope)
  - Seleccionar split → avanza automáticamente al paso 4
  - Muestra SPLIT_TEMPLATES filtrados por modo (si powerlifting → mostrar tag POWERLIFTING primero)

Paso 4 — STRUCTURE (solo para mode SIMPLE):
  - Slider: "¿Cuántas semanas?" (4-24 semanas)
  - Crea automáticamente: 1 Macrocycle → 1 Block → 1 Mesocycle → N weeks vacías
  - Para COMPLEX → saltar directamente (usuario configura en StructureSection)
  - Botón "Crear programa"

onDone: viewModel.saveProgram() → onNavigateToProgramDetail(newProgramId)
```

---

### `DetailsSection.kt`

```
Composable sin estado propio (controlled):

Secciones:
1. Nombre: OutlinedTextField, uppercase, maxLines=1
2. Descripción: OutlinedTextField, maxLines=3
3. Modo: ExposedDropdownMenuBox con opciones Hipertrofia/Powerlifting/Powerbuilding
4. Día de inicio: ExposedDropdownMenuBox, Lunes–Domingo (0=Dom, 1=Lun... 6=Sáb)
5. Split actual: Card de preview mostrando:
   - SPLIT_TEMPLATES.find { it.id == program.selectedSplitId }
   - Barras de patrón (color por trabajo, gris por descanso)
   - Botón "Cambiar Split" → onOpenSplitChanger()
```

---

### `StructureSection.kt`

**Nota:** Reutilizar/adaptar el `MacrocycleEditor.kt` ya existente en programdetail/components/.
Diferencias desde el StructureDrawer del PWA:
- Editar nombre de bloque inline (BasicTextField)
- Selector de objetivo (MesocycleGoal) por mesociclo
- Botones: + Agregar Semana, + Agregar Bloque
- Eliminar bloque con confirmación (AlertDialog)
- Barra de progreso por mesociclo (semanas con sesiones vs total)

```
Column {
  program.macrocycles.forEachIndexed { macroIdx, macro ->
    MacrocycleSection(macro) {
      macro.blocks.forEachIndexed { blockIdx, block ->
        BlockCard(
          block = block,
          onNameChange = { vm.updateBlockName(macroIdx, blockIdx, it) },
          onDelete = { vm.deleteBlock(macroIdx, blockIdx) },
        ) {
          block.mesocycles.forEachIndexed { mesoIdx, meso ->
            MesocycleRow(
              meso = meso,
              onNameChange = { vm.updateMesoName(macroIdx, blockIdx, mesoIdx, it) },
              onGoalChange = { vm.updateMesoGoal(macroIdx, blockIdx, mesoIdx, it) },
              onAddWeek = { vm.addWeek(macroIdx, blockIdx, mesoIdx) },
            )
          }
        }
        Button(onClick = { vm.addBlock(macroIdx) }) { Text("+ Bloque") }
      }
    }
  }
}
```

---

### `GoalsSection.kt`

```
Estado local:
  - showPicker: Boolean
  - searchQuery: String

UI:
  if (showPicker) {
    OutlinedTextField(searchQuery, ...) // buscar ejercicio
    LazyColumn {
      exerciseList.filter { it.name.contains(searchQuery, ignoreCase=true) }
        .take(12)
        .forEach { ex ->
          ListItem(text=ex.name, onClick = { vm.addGoal(ex.id, ex.name, 100.0) })
        }
    }
  }

  // Goals list
  program.exerciseGoals.forEach { (exerciseId, target) ->
    Card {
      Row {
        Text(exerciseName)
        OutlinedTextField(
          value = target.toString(),
          onValueChange = { vm.updateGoalTarget(exerciseId, it.toDoubleOrNull() ?: 0.0) },
          label = "KG",
          keyboardType = KeyboardType.Decimal
        )
        IconButton(onClick = { vm.removeGoal(exerciseId) }) {
          Icon(Icons.Default.Delete, ...)
        }
      }
    }
  }
```

---

### `EventsSection.kt`

```
Estado local:
  - showForm: Boolean
  - editingEventId: String?
  - formTitle: String
  - formWeek: Int (calculatedWeek)
  - isRepeat: Boolean
  - formRepeatEvery: Int

UI:
  // Botón "Nuevo Evento"
  Button(onClick = { showForm = true; editingEventId = null })

  // Lista de eventos
  program.events.forEach { event ->
    Card {
      Row {
        Icon(calendar)
        Text(event.title)
        Text("Semana ${event.calculatedWeek}")
        IconButton(delete)
        IconButton(edit → openForm(event))
      }
    }
  }

  // Dialog para crear/editar
  if (showForm) {
    AlertDialog(
      title = "Nuevo Evento",
      content = {
        OutlinedTextField(formTitle, ...)
        OutlinedTextField(formWeek, keyboardType=Number)
      },
      onConfirm = { vm.addOrUpdateEvent(ProgramEvent(UUID, formTitle, "custom", ..., calculatedWeek=formWeek)) }
    )
  }
```

---

### `VolumeHeatmapSection.kt`

```
Solo lectura. Calcula volumen usando VolumeCalculator.

val exerciseList = remember { ExerciseDatabase.exercises } // necesita acceso al DB
val heatmapData = remember(program) {
    buildList {
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEachIndexed { i, week ->
                        add(WeekVolumeEntry(
                            label = "${meso.name} S${i+1}",
                            volumes = calculateUnifiedMuscleVolume(week.sessions, exerciseList)
                        ))
                    }
                }
            }
        }
    }
}

UI: ScrollableHeatmapTable(rows = TOP_MUSCLES, columns = weeks, values = heatmapData)
Cada celda: Box con background = lerp(surface, primary, intensity) donde intensity = sets/maxSets
```

---

### `SplitSelectorSheet.kt`

```
2 pasos (step = "gallery" | "configure"):

Paso Gallery:
  - SearchBar
  - LazyRow de FilterChips para SplitTag
  - LazyColumn de SplitCards:
      Card(onClick = { selectedSplit = it; step = "configure" }) {
        Text(split.name)
        Text(split.description)
        Row { split.pattern.forEach { day -> PatternBar(day) } }
      }

Paso Configure:
  - Preview del split seleccionado
  - "¿Desde qué día empieza la semana?" → DropdownMenu (Lun-Dom)
  - Scope (solo si isEditorMode): Radio: Semana / Bloque / Programa
  - "Preservar ejercicios existentes": Switch (solo si isEditorMode)
  - Button("Aplicar") → onApply(split, scope, preserveExercises, startDay)
  - TextButton("Atrás") → step = "gallery"
```

---

### `SessionEditorViewModel.kt`

```kotlin
data class SessionEditorUiState(
    val session: Session? = null,
    val programId: String = "",
    val weekId: String? = null,
    val macroIndex: Int = 0,
    val mesoIndex: Int = 0,
    val isPickerOpen: Boolean = false,
    val searchQuery: String = "",
    val hasUnsavedChanges: Boolean = false,
)

class SessionEditorViewModel(private val programId: String, private val sessionId: String) : ViewModel() {
    // init: repository.getProgramById(programId)
    //       encontrar sesión por sessionId en toda la estructura
    //       guardar weekId, macroIndex, mesoIndex para poder hacer update

    // Funciones:
    // updateSessionField(field, value) — nombre, descripción
    // addPart() — nueva SessionPart con UUID y color rotativo
    // updatePartName(partIdx, name)
    // updatePartColor(partIdx, color)
    // removePart(partIdx)
    // addExerciseToPart(partIdx, exercise) — desde picker
    // updateExerciseField(partIdx, exerciseIdx, field, value)
    // removeExercise(partIdx, exerciseIdx)
    // addSet(partIdx, exerciseIdx) — copia el último set
    // removeSet(partIdx, exerciseIdx, setIdx)
    // updateSet(partIdx, exerciseIdx, setIdx, field, value) — reps, weight, RPE, rest
    // reorderExercises(partIdx, fromIdx, toIdx)
    // setPickerOpen(open) / setSearchQuery(query)
    // saveSession() — actualizar el programa en repository (findAndReplace session)
}
```

---

### `SessionEditorScreen.kt`

```
Scaffold(
  topBar = TopAppBar(
    title = { Text(session.name) },
    navigationIcon = { BackButton },
    actions = { SaveButton(onClick = { vm.saveSession(); onBack() }) }
  )
) {
  Column(verticalScroll) {
    // Header
    SessionHeaderCard(session, onUpdateName, onUpdateDescription)

    Spacer(8.dp)

    // Parts
    session.parts.forEachIndexed { partIdx, part ->
      PartSectionCard(
        part = part,
        onNameChange = { vm.updatePartName(partIdx, it) },
        onColorChange = { vm.updatePartColor(partIdx, it) },
        onRemovePart = { vm.removePart(partIdx) },
      ) {
        part.exercises.forEachIndexed { exIdx, exercise ->
          ExerciseRowCard(
            exercise = exercise,
            onUpdate = { field, value -> vm.updateExerciseField(partIdx, exIdx, field, value) },
            onRemove = { vm.removeExercise(partIdx, exIdx) },
            onAddSet = { vm.addSet(partIdx, exIdx) },
            onRemoveSet = { setIdx -> vm.removeSet(partIdx, exIdx, setIdx) },
            onUpdateSet = { setIdx, field, value -> vm.updateSet(partIdx, exIdx, setIdx, field, value) },
          )
        }

        OutlinedButton(onClick = { vm.setPickerOpen(true) }) {
          Icon(Icons.Default.Add); Text("Agregar ejercicio")
        }
      }
    }

    Button(onClick = { vm.addPart() }) { Text("+ Nueva Parte") }

    Spacer(120.dp)
  }

  if (uiState.isPickerOpen) ExercisePickerSheet(...)
}
```

---

### `ExerciseRowCard.kt`

```
Estado local: isExpanded (Boolean)

Card(animateContentSize) {
  // Header (siempre visible)
  Row(onClick = { isExpanded = !isExpanded }) {
    Text(exercise.name, Bold)
    Text("${exercise.sets.size} series", color=secondary)
    Text("~${exercise.sets.size * exercise.restTime / 60}min", color=secondary)
    Icon(if(isExpanded) KeyboardArrowUp else KeyboardArrowDown)
    IconButton(delete)
  }

  // Expandido: tabla de sets
  if (isExpanded) {
    // Header row
    Row { Text("Set"); Text("Reps"); Text("Peso"); Text("RPE"); Text("Desc") }

    exercise.sets.forEachIndexed { setIdx, set ->
      SetRowEditor(
        set = set,
        index = setIdx,
        onUpdate = { field, value -> onUpdateSet(setIdx, field, value) },
        onRemove = { onRemoveSet(setIdx) },
      )
    }

    TextButton(onClick = onAddSet) { Icon(Add); Text("Set") }
  }
}
```

---

### `SetRowEditor.kt`

```
Row(verticalAlignment = CenterVertically) {
  Text("${index+1}", size=10)

  // Reps
  OutlinedTextField(
    value = set.targetReps?.toString() ?: "",
    onValueChange = { onUpdate("targetReps", it.toIntOrNull()) },
    label = "Reps",
    keyboardType = Number,
    modifier = Modifier.width(56.dp)
  )

  // Peso (para future: solo si trainingMode == REPS o LOAD)
  OutlinedTextField(
    value = set.weight?.toString() ?: "",
    onValueChange = { onUpdate("weight", it.toDoubleOrNull()) },
    label = "kg",
    keyboardType = Decimal,
    modifier = Modifier.width(64.dp)
  )

  // RPE
  OutlinedTextField(
    value = set.targetRPE?.toString() ?: "",
    onValueChange = { onUpdate("targetRPE", it.toDoubleOrNull()) },
    label = "RPE",
    keyboardType = Decimal,
    modifier = Modifier.width(56.dp)
  )

  // Tiempo descanso (en exercise level, pero mostrar aquí como referencia)
  // No editable por set, el restTime es del exercise

  IconButton(onClick = onRemove) { Icon(Delete, size=16.dp) }
}
```

---

### `ExercisePickerSheet.kt`

```
ModalBottomSheet(onDismissRequest = onDismiss) {
  Column {
    // Search
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchChange,
      leadingIcon = { Icon(Search) },
      label = "Buscar ejercicio",
      modifier = Modifier.fillMaxWidth().padding(16.dp)
    )

    // Lista filtrada
    LazyColumn {
      items(
        exerciseList.filter { it.name.contains(searchQuery, ignoreCase=true) }.take(30)
      ) { ex ->
        ListItem(
          headlineContent = { Text(ex.name) },
          supportingContent = {
            val primary = ex.involvedMuscles.firstOrNull { it.role == MuscleRole.PRIMARY }?.muscle
            if (primary != null) Text(primary, fontSize=10.sp)
          },
          trailingContent = {
            IconButton(onClick = {
              onSelectExercise(Exercise(
                id = UUID.randomUUID().toString(),
                name = ex.name,
                exerciseDbId = ex.id,
                sets = listOf(ExerciseSet(id=UUID.randomUUID().toString(), targetReps=8)),
                restTime = 90,
              ))
            }) { Icon(Icons.Default.Add) }
          }
        )
        HorizontalDivider()
      }
    }
  }
}
```

---

### `WorkoutViewModel.kt`

```kotlin
data class WorkoutUiState(
    val session: Session? = null,
    val programId: String = "",
    val weekId: String? = null,
    val macroIndex: Int = 0,
    val mesoIndex: Int = 0,
    val currentExerciseIdx: Int = 0,
    val currentSetIdx: Int = 0,
    val completedSets: Map<String, CompletedSet> = emptyMap(), // key = "${exerciseId}_${setIdx}"
    val restTimerSeconds: Int = 0,
    val isRestTimerRunning: Boolean = false,
    val showFinishSheet: Boolean = false,
    val startTimeMs: Long = 0L,
    val isComplete: Boolean = false,
)

class WorkoutViewModel(val programId: String, val sessionId: String) : ViewModel() {
    // init:
    //   1. Cargar session del programa
    //   2. Buscar logs anteriores de esta sesión → repository.getLogsForSession(sessionId)
    //   3. repository.startWorkout(OngoingWorkoutState(...))
    //   4. Iniciar el timer de duración

    // Propiedades computadas:
    // currentExercise: Exercise? = session?.exercises?.getOrNull(currentExerciseIdx)
    // currentSet: ExerciseSet? = currentExercise?.sets?.getOrNull(currentSetIdx)
    // lastLogForSession: WorkoutLog? (último log para mostrar ghostPerformance)

    // Funciones:
    // logSet(weight, reps, rpe) — actualizar completedSets map
    // nextSet() — currentSetIdx++ o nextExercise si era el último set
    // nextExercise() — currentExerciseIdx++
    // selectExercise(idx) — saltar a ejercicio
    // startRestTimer(seconds) — kickoff countdown en viewModelScope
    // pauseRestTimer() / stopRestTimer()
    // onRestTimerTick() — decrementar restTimerSeconds
    // showFinish() — showFinishSheet = true
    // finishWorkout(notes, fatigueLevel) — construir WorkoutLog, repository.addWorkoutLog(), clearOngoingWorkout()

    // computeBrzycki1RM(weight, reps): Double = weight * (36.0 / (37.0 - reps))
    // getGhostForSet(exerciseId, setIdx): CompletedSet?
    //   → lastLogForSession?.completedExercises
    //      ?.find { it.exerciseId == exerciseId }
    //      ?.sets?.getOrNull(setIdx)
}
```

---

### `WorkoutScreen.kt`

```
Scaffold(
  topBar = TopAppBar(
    title = { Text(session.name) },
    navigationIcon = { /* pause / back con confirmación */ },
    actions = {
      TextButton(onClick = { vm.showFinish() }) { Text("Terminar") }
    }
  )
) { padding ->
  Column(Modifier.padding(padding)) {
    // 1. Carousel de ejercicios
    ExerciseCarouselBar(
      exercises = session.exercises,
      currentIdx = uiState.currentExerciseIdx,
      completedSets = uiState.completedSets,
      onSelect = { vm.selectExercise(it) }
    )

    Spacer(8.dp)

    // 2. Card central del ejercicio actual
    SetInputCard(
      exercise = currentExercise,
      setIndex = uiState.currentSetIdx,
      onLogSet = { weight, reps, rpe ->
        vm.logSet(weight, reps, rpe)
        vm.startRestTimer(currentExercise.restTime)
      },
      ghostSet = vm.getGhostForSet(currentExercise.id, uiState.currentSetIdx),
    )

    Spacer(8.dp)

    // 3. Rest timer (visible cuando está corriendo)
    if (uiState.isRestTimerRunning) {
      RestTimerCard(
        remainingSeconds = uiState.restTimerSeconds,
        totalSeconds = currentExercise.restTime,
        onSkip = { vm.stopRestTimer(); vm.nextSet() },
        onAddTime = { vm.addRestTime(30) },
      )
    } else {
      // Botón siguiente set
      Button(
        onClick = { vm.nextSet() },
        modifier = Modifier.fillMaxWidth().padding(horizontal=16.dp)
      ) {
        val label = if (isLastSet) "Siguiente ejercicio" else "Set completado"
        Text(label)
      }
    }
  }

  if (uiState.showFinishSheet) {
    FinishWorkoutSheet(
      session = session,
      completedSets = uiState.completedSets,
      durationMinutes = ...,
      onConfirm = { notes, fatigue -> vm.finishWorkout(notes, fatigue); onBack() },
      onDismiss = { vm.hideFinish() }
    )
  }
}
```

---

### `ExerciseCarouselBar.kt`

```
LazyRow(horizontalArrangement = spacedBy(8.dp), contentPadding = PaddingValues(horizontal=16.dp)) {
  session.exercises.forEachIndexed { idx, exercise ->
    val isCompleted = completedSets.keys.any { it.startsWith("${exercise.id}_") &&
                        completedSets.keys.count { it.startsWith("${exercise.id}_") } >= exercise.sets.size }
    val isCurrent = idx == currentIdx

    FilterChip(
      selected = isCurrent,
      onClick = { onSelect(idx) },
      label = { Text(exercise.name, maxLines=1, overflow=Ellipsis, fontSize=11.sp) },
      leadingIcon = if (isCompleted) { { Icon(Check, size=14.dp) } } else null,
      colors = if (isCompleted) FilterChipDefaults.elevatedFilterChipColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
      ) else FilterChipDefaults.elevatedFilterChipColors(),
    )
  }
}
```

---

### `SetInputCard.kt`

```
Card(shape=RoundedCornerShape(24.dp), modifier=Modifier.fillMaxWidth().padding(16.dp)) {
  Column(Modifier.padding(20.dp)) {
    // Header del ejercicio
    Text(exercise.name, fontSize=20.sp, fontWeight=Black)
    Text("Serie ${setIndex+1} / ${exercise.sets.size}", color=secondary)

    // Target del set
    val target = exercise.sets.getOrNull(setIndex)
    if (target != null) {
      Row {
        if (target.targetReps != null) Text("Objetivo: ${target.targetReps} reps")
        if (target.targetRPE != null) Text("@ RPE ${target.targetRPE}")
      }
    }

    // Ghost (performance previa)
    if (ghostSet != null) {
      Card(colors=surfaceVariant) {
        Text("Última vez: ${ghostSet.weight}kg × ${ghostSet.reps}reps", fontSize=12.sp)
      }
    }

    Spacer(16.dp)

    // Inputs
    var weight by remember { mutableStateOf(ghostSet?.weight?.toString() ?: "") }
    var reps by remember { mutableStateOf(ghostSet?.reps?.toString() ?: target?.targetReps?.toString() ?: "") }
    var rpe by remember { mutableStateOf("") }

    Row(horizontalArrangement=spacedBy(12.dp)) {
      OutlinedTextField(weight, { weight = it }, label="Peso (kg)", keyboardType=Decimal, modifier=Modifier.weight(1f))
      OutlinedTextField(reps, { reps = it }, label="Reps", keyboardType=Number, modifier=Modifier.weight(1f))
      OutlinedTextField(rpe, { rpe = it }, label="RPE", keyboardType=Decimal, modifier=Modifier.weight(0.8f))
    }

    Spacer(16.dp)

    Button(
      onClick = { onLogSet(weight.toDoubleOrNull() ?: 0.0, reps.toIntOrNull() ?: 0, rpe.toDoubleOrNull()) },
      modifier = Modifier.fillMaxWidth(),
    ) { Text("Registrar Set", fontWeight=Bold) }
  }
}
```

---

### `RestTimerCard.kt`

```
Card(modifier=fillMaxWidth().padding(horizontal=16.dp)) {
  Column(horizontalAlignment=CenterHorizontally, modifier=Modifier.padding(16.dp)) {
    // Circular progress indicator
    Box(contentAlignment=Center) {
      CircularProgressIndicator(
        progress = { remainingSeconds.toFloat() / totalSeconds.toFloat() },
        strokeWidth = 8.dp,
        modifier = Modifier.size(80.dp)
      )
      Text("${remainingSeconds}s", fontSize=20.sp, fontWeight=Black)
    }

    Spacer(8.dp)

    Row(horizontalArrangement=spacedBy(8.dp)) {
      OutlinedButton(onClick = onSkip) { Text("Saltar") }
      TextButton(onClick = { onAddTime(30) }) { Text("+30s") }
    }
  }
}

// Timer logic en ViewModel usando viewModelScope.launch + delay(1000)
```

---

### `FinishWorkoutSheet.kt`

```
ModalBottomSheet(onDismissRequest = onDismiss) {
  Column(Modifier.padding(24.dp)) {
    Text("Sesión Completada", fontSize=20.sp, fontWeight=Black)
    Spacer(8.dp)

    // Resumen de sets completados
    val totalSets = completedSets.size
    val totalVolume = completedSets.values.sumOf { it.weight * it.reps }
    Text("$totalSets series · ${"%.0f".format(totalVolume)} kg de volumen · ${durationMinutes}min")

    Spacer(16.dp)

    // Fatiga 1-5 selector
    Text("¿Cómo te sentiste?")
    Row(horizontalArrangement=spacedBy(8.dp)) {
      (1..5).forEach { level ->
        val emoji = when(level) { 1->"😄" 2->"🙂" 3->"😐" 4->"😓" else->"😵" }
        FilterChip(selected=fatigue==level, onClick={ fatigue=level }, label={ Text(emoji) })
      }
    }

    Spacer(8.dp)

    // Notas
    OutlinedTextField(notes, { notes=it }, label="Notas (opcional)", maxLines=3, modifier=fillMaxWidth())

    Spacer(16.dp)

    Button(onClick={ onConfirm(notes, fatigue) }, modifier=fillMaxWidth()) {
      Text("Guardar y Terminar")
    }
    TextButton(onClick=onDismiss, modifier=fillMaxWidth()) { Text("Continuar entrenando") }
  }
}
```

---

## WIRING en `MainActivity.kt`

Reemplazar los 3 `GenericScreen` placeholders:

```kotlin
// SessionEditor
composable(KpknRoute.SessionEditor.route) { backStack ->
    val programId = backStack.arguments?.getString(KpknRoute.SessionEditor.ARG_PROGRAM_ID) ?: ""
    val sessionId = backStack.arguments?.getString(KpknRoute.SessionEditor.ARG_SESSION_ID) ?: ""
    SessionEditorScreen(
        programId = programId,
        sessionId = sessionId,
        onBack = { navController.popBackStack() },
    )
}

// Workout
composable(KpknRoute.Workout.route) { backStack ->
    val programId = backStack.arguments?.getString(KpknRoute.Workout.ARG_PROGRAM_ID) ?: ""
    val sessionId = backStack.arguments?.getString(KpknRoute.Workout.ARG_SESSION_ID) ?: ""
    WorkoutScreen(
        programId = programId,
        sessionId = sessionId,
        onBack = { navController.popBackStack() },
    )
}

// ProgramEditor
composable(KpknRoute.ProgramEditor.route) { backStack ->
    val programId = backStack.arguments?.getString(KpknRoute.ProgramEditor.ARG_PROGRAM_ID) ?: ""
    ProgramEditorScreen(
        programId = programId,
        onBack = { navController.popBackStack() },
        onDone = { id -> navController.navigate(KpknRoute.ProgramDetail.create(id)) {
            popUpTo(KpknRoute.Training.route)
        }},
    )
}
```

También conectar "Crear programa" en ProgramsScreen:
```kotlin
onCreateProgram = {
    navController.navigate(KpknRoute.ProgramEditor.create("new"))
}
```

---

## ORDEN DE IMPLEMENTACIÓN RECOMENDADO

1. **SessionEditorViewModel + SessionEditorScreen** (más simple, datos ya conocidos)
   - SessionHeaderCard.kt
   - ExercisePickerSheet.kt
   - ExerciseRowCard.kt + SetRowEditor.kt
   - PartSectionCard.kt

2. **WorkoutViewModel + WorkoutScreen** (estado más complejo pero aislado)
   - ExerciseCarouselBar.kt
   - SetInputCard.kt
   - RestTimerCard.kt
   - FinishWorkoutSheet.kt

3. **ProgramEditorViewModel + ProgramCreatorWizard** (wizard primero)
   - WizardStep composables
   - SplitSelectorSheet.kt

4. **ProgramEditorScreen con Advanced sections**
   - DetailsSection.kt
   - StructureSection.kt (adaptar MacrocycleEditor existente)
   - GoalsSection.kt
   - EventsSection.kt
   - VolumeHeatmapSection.kt
   - ExportSection.kt

5. **Wiring en MainActivity.kt**

---

## DECISIONES DE DISEÑO

| PWA | Kotlin |
|-----|--------|
| `JSON.parse(JSON.stringify(x))` | `x.copy()` (data class deep copy si es plano, rebuild si anidado) |
| `window.confirm()` | `AlertDialog` |
| BottomSheet (framer-motion) | `ModalBottomSheet` (@ExperimentalMaterial3Api) |
| Sidebar drawer (CSS) | `ModalNavigationDrawer` |
| drag-and-drop (react-beautiful-dnd) | Omitir en v1, agregar manualmente con detectDragGestures en v2 |
| localStorage draft | Solo en memoria (DataStore en Phase 4) |
| `crypto.randomUUID()` | `UUID.randomUUID().toString()` |
| SPLIT_TEMPLATES | Ya existe en `data/splits/SplitTemplates.kt` |
| ExerciseDatabase | Ya existe en algún form — verificar `data/exercises/` |
| `addToast()` | `SnackbarHostState.showKpknSnackbar()` (ya existe) |
| Pattern bars (split preview) | `Box` con `background(color)` y `width(fraction)` |

---

## SCOPE EXCLUIDO (va después)
- AUGE dashboard en SessionEditor
- Fatigue metrics en WorkoutSession
- AugeFAB y AI suggestions
- Drag-to-reorder de ejercicios
- Warmup drawer detallado
- PostExercise drawer
- Export a JSON (ExportSection)
- GhostPerformance visual avanzado (solo básico)
