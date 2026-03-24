# Plan Maestro de Migración: Home, ProgramDetail, ProgramsView

## Dependency Tree

### Home.tsx
```
Home.tsx
├── home/SessionTodayCard (TodaySessionItem, pager con cover)
├── home/HomeCardsSection (macros, biometry, 1RM carousel)
│   ├── home/BabushkaRings (SVG concentric ring chart)
│   └── utils/calculations (FFMI, IPF GL, Brzycki 1RM)
├── home/AugeTelemetryPanel (battery system: muscular/cns/spinal)
│   ├── services/computeWorkerService (calculateGlobalBatteriesAsync)
│   ├── services/auge (getPerMuscleBatteries, ACCORDION_MUSCLES)
│   └── home/BatteryShareCard
├── icons (IntertwinedRingsIcon, SingleRingIcon, Plus, Bell, Settings, Sun, Moon)
├── CaupolicanIcon
├── ui/Button
└── contexts/AppContext
```

### ProgramDetail.tsx
```
ProgramDetail.tsx
├── program-detail/CompactHeroBanner (gradient hero + theme picker)
├── program-detail/IntegratedTabs (main tab + sub-tab animated)
├── program-detail/BlockRoadmap (horizontal block/week selector)
├── program-detail/DayView (7-day grid, session cards, drag-reorder)
├── program-detail/WeekView (session list + adherence)
├── program-detail/SplitView + SplitAdvancedEditor
├── program-detail/LoopsView (loop templates + sequencer)
│   └── services/loopEngine (projectLoops, detectLoopCollisions)
├── program-detail/ProtocolsView (GZCL, 531, Juggernaut, Westside)
├── program-detail/MacrocycleEditorIntegrated (block/meso/week CRUD)
├── program-detail/VolumeView + AnalyticsDashboard (~10 nested widgets)
├── program-detail/ProgressView (star exercise 1RM trends)
├── program-detail/HistoryView (per-exercise history search)
├── services/volumeCalculator (calculateUnifiedMuscleVolume)
└── services/augeAdaptiveService (getCachedAdaptiveData)
```

### ProgramsView.tsx
```
ProgramsView.tsx
├── SwipeToDeleteCard (inline — touch swipe gesture)
├── icons (Play, ChevronRight, Plus, Dumbbell, Activity, Calendar, Layers)
└── contexts/AppContext (activeProgramState, handleDeleteProgram)
```

---

## Data Models (Kotlin data classes from types.ts)

| Model | Complejidad |
|-------|-------------|
| `Program` | XL — id, name, macrocycles, loops, loopState, events, goals, structure |
| `Macrocycle` | S — id, name, blocks |
| `Block` | S — id, name, mesocycles |
| `Mesocycle` | S — id, name, goal, weeks |
| `ProgramWeek` | M — id, sessions, variant, events, isLoopWeek |
| `Session` | L — id, name, exercises, parts, dayOfWeek, sessionB/C/D |
| `SessionPart` | S — id, name, exercises, color |
| `Exercise` | L — id, name, exerciseDbId, sets, warmupSets, restTime, supersetId |
| `ExerciseSet` | M — reps, weight, rpe, rir, type |
| `WorkoutLog` | L — id, programId, sessionId, date, duration, completedExercises, discomforts |
| `CompletedExercise` | M — exerciseId, exerciseName, sets |
| `CompletedSet` | S — weight, side, spinalScore |
| `Loop` | S — id, title, type, repeatEveryXLoops |
| `Settings` | XL — username, theme, weightUnit, nutrition goals, algorithm settings |
| `ActiveProgramState` | M — programId, status, currentMacro/Block/Meso/WeekId |
| `TodaySessionItem` | M — session, program, location, isCompleted, dayOfWeek, log |
| `AugeAdaptiveCache` | M — opaque cache for adaptive engine |

---

## ViewModels

| ViewModel | Reemplaza | Complejidad |
|-----------|-----------|-------------|
| `HomeViewModel` | useHomeViewModel() + AppContext fields | L |
| `AugeTelemetryViewModel` | AugeTelemetryPanel state + computeWorkerService | XL |
| `HomeCardsViewModel` | HomeCardsSection calculations | L |
| `ProgramDetailViewModel` | ProgramDetail inline state + derived calcs | XL |
| `ProgramsViewModel` | ProgramsView + AppContext | M |
| `SharedProgramRepository` | AppContext (programs/history/settings) | L |

---

## Component → Composable Mapping

### Home (~13 archivos)

| PWA | Kotlin | Path | Size |
|-----|--------|------|------|
| Home.tsx | HomeScreen() | screens/home/HomeScreen.kt | L |
| useHomeViewModel | HomeViewModel | screens/home/HomeViewModel.kt | L |
| SessionTodayCard | SessionTodayCard() | screens/home/components/SessionTodayCard.kt | M |
| HomeCardsSection | HomeCardsSection() | screens/home/components/HomeCardsSection.kt | XL |
| BabushkaRings | BabushkaRings() | screens/home/components/BabushkaRings.kt | M |
| AugeTelemetryPanel | AugeTelemetryPanel() | screens/home/components/AugeTelemetryPanel.kt | XL |
| AugeTelemetry VM | AugeTelemetryViewModel | screens/home/AugeTelemetryViewModel.kt | XL |
| CalibrationModal | CalibrationDialog() | screens/home/components/CalibrationDialog.kt | M |
| HomeCards VM | HomeCardsViewModel | screens/home/HomeCardsViewModel.kt | L |

### ProgramDetail (~18 archivos)

| PWA | Kotlin | Path | Size |
|-----|--------|------|------|
| ProgramDetail.tsx | ProgramDetailScreen() | screens/programdetail/ProgramDetailScreen.kt | XL |
| PD state/logic | ProgramDetailViewModel | screens/programdetail/ProgramDetailViewModel.kt | XL |
| CompactHeroBanner | CompactHeroBanner() | screens/programdetail/components/CompactHeroBanner.kt | L |
| IntegratedTabs | IntegratedTabs() | screens/programdetail/components/IntegratedTabs.kt | M |
| BlockRoadmap | BlockRoadmap() | screens/programdetail/components/BlockRoadmap.kt | L |
| DayView | DayView() | screens/programdetail/components/DayView.kt | XL |
| WeekView | WeekView() | screens/programdetail/components/WeekView.kt | M |
| SplitView | SplitView() | screens/programdetail/components/SplitView.kt | L |
| LoopsView | LoopsView() | screens/programdetail/components/LoopsView.kt | L |
| ProtocolsView | ProtocolsView() | screens/programdetail/components/ProtocolsView.kt | L |
| MacrocycleEditor | MacrocycleEditor() | screens/programdetail/components/MacrocycleEditor.kt | XL |
| VolumeView | VolumeView() | screens/programdetail/components/VolumeView.kt | XL |
| ProgressView | ProgressView() | screens/programdetail/components/ProgressView.kt | L |
| HistoryView | HistoryView() | screens/programdetail/components/HistoryView.kt | L |
| WelcomeTour | WelcomeTourDialog() | screens/programdetail/components/WelcomeTourDialog.kt | S |

### ProgramsView (~3 archivos)

| PWA | Kotlin | Path | Size |
|-----|--------|------|------|
| ProgramsView.tsx | ProgramsScreen() | screens/programs/ProgramsScreen.kt | M |
| PV state | ProgramsViewModel | screens/programs/ProgramsViewModel.kt | M |
| SwipeToDeleteCard | SwipeToDeleteCard() | components/SwipeToDeleteCard.kt | M |

---

## Shared Components

| Composable | Usado por | Path |
|-----------|----------|------|
| SectionHeader() | Home, otros | components/SectionHeader.kt |
| CaupolicanIcon() | Home (header, empty, programs) | components/icons/CaupolicanIcon.kt |
| Icons.kt | Todas las pantallas | components/icons/Icons.kt |
| SwipeToDeleteCard() | ProgramsView | components/SwipeToDeleteCard.kt |
| AugeRings() | Home (extraer de MainActivity) | components/AugeRings.kt |

---

## Domain / Service Layer

| Kotlin | PWA Source | Size |
|--------|-----------|------|
| domain/calculations/Calculations.kt | utils/calculations.ts (Brzycki, FFMI, IPF GL) | L |
| domain/calculations/VolumeCalculator.kt | services/volumeCalculator.ts | XL |
| domain/auge/AugeEngine.kt | services/auge.ts (batteries, ACCORDION_MUSCLES) | XL |
| domain/auge/AugeAdaptiveService.kt | services/augeAdaptiveService.ts | L |
| domain/loops/LoopEngine.kt | services/loopEngine.ts | L |
| domain/ProgramHelpers.kt | utils/programHelpers.ts | S |

---

## Navigation Graph

```kotlin
sealed class KpknRoute(val route: String) {
    object Home : KpknRoute("home")
    object Training : KpknRoute("training")     // ProgramsScreen
    object Nutrition : KpknRoute("nutrition")
    object WikiLab : KpknRoute("wikilab")
    object ProgramDetail : KpknRoute("program/{programId}")
    object Settings : KpknRoute("settings")
}
```

Flow: Home → program card → ProgramDetail(id) | Training tab → ProgramsScreen → ProgramDetail(id)

---

## Dependencias nuevas (build.gradle.kts)

```kotlin
implementation("androidx.navigation:navigation-compose:2.8.x")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.x")
implementation("androidx.datastore:datastore-preferences:1.1.x")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.x")
implementation("io.coil-kt:coil-compose:2.7.x")
```

---

## Orden de Implementación (4 fases)

### Fase 0: Foundation (~7 tareas)
1. Data models (`data/models/`)
2. Icon composables (`components/icons/Icons.kt`)
3. Shared components (SectionHeader, CaupolicanIcon, SwipeToDeleteCard)
4. Domain stubs (Calculations.kt, ProgramHelpers.kt)
5. Navigation Compose setup (migrar de AppDestinations a NavHost)
6. Agregar dependencias a build.gradle.kts
7. SharedProgramRepository (reemplaza AppContext)

### Fase 1: ProgramsView (más simple)
8. ProgramsViewModel
9. ProgramsScreen
10. SwipeToDeleteCard (gestures)

### Fase 2: Home (moderado, algo ya existe)
11. Refactorizar MainActivity.kt — extraer composables, setup NavHost
12. HomeViewModel
13. HomeScreen (refactor HomeWithProgramScreen existente)
14. SessionTodayCard
15. BabushkaRings (Canvas)
16. HomeCardsViewModel + HomeCardsSection
17. AugeTelemetryViewModel + AugeTelemetryPanel
18. CalibrationDialog (refactor CalibrationOverlay)
19. Calculations.kt — implementación completa

### Fase 3: ProgramDetail (más complejo)
20. VolumeCalculator.kt
21. AugeEngine.kt
22. LoopEngine.kt
23. ProgramDetailViewModel
24. CompactHeroBanner
25. IntegratedTabs
26. BlockRoadmap
27. DayView
28. WeekView
29. SplitView
30. MacrocycleEditor
31. LoopsView
32. ProtocolsView
33. ProgressView
34. HistoryView
35. VolumeView (stub — AnalyticsDashboard es enorme, implementar widgets incrementalmente)
36. WelcomeTourDialog
37. ProgramDetailScreen

### Fase 4: Polish
38. Deep-link navigation
39. Dark mode across all screens
40. Empty states
41. Error handling / loading states

---

## Totales

| Tamaño | Cantidad |
|--------|----------|
| S (< 100 líneas) | 12 |
| M (100–300 líneas) | 14 |
| L (300–600 líneas) | 10 |
| XL (600+ líneas) | 8 |
| **Total archivos Kotlin** | **~44** |

---

## Riesgos

| Riesgo | Severidad | Mitigación |
|--------|-----------|-----------|
| AnalyticsDashboard (10+ widgets anidados) | ALTA | Stub VolumeView; implementar widgets uno por uno |
| AugeTelemetryPanel (battery calcs, compute worker) | ALTA | Port math a coroutines (Dispatchers.Default) |
| HomeCardsSection (FFMI, IPF, 1RM calculations) | MEDIA | Portar math como funciones puras Kotlin |
| DayView drag-to-reorder sessions | MEDIA | Usar Modifier.draggable o diferir |
| LoopEngine (projections, collision detection) | MEDIA | Pure Kotlin, unit testeable |

---

## Decisiones Arquitectónicas

1. **State**: ViewModel + StateFlow por pantalla. SharedProgramRepository para estado compartido
2. **Sin Hilt**: DI manual con ViewModelFactory. Agregar Hilt después
3. **Sin Room**: DataStore para settings, listas in-memory para programas/history. Room después
4. **Animaciones**: AnimatedVisibility/AnimatedContent reemplazan Framer Motion
5. **Swipe**: Modifier.swipeable() o AnchoredDraggable
6. **Carousels**: LazyRow
7. **Canvas**: BabushkaRings y AugeRings nativos en Compose Canvas
8. **VolumeView/AnalyticsDashboard**: Stub inicial, widgets incrementales
