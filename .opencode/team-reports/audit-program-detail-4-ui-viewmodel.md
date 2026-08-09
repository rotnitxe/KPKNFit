# Auditoría 4/5 — UI Program Detail y ViewModel

> **Pista:** `ProgramDetailViewModel.kt` 75KB, `ProgramDetailScreen.kt` 64KB, `MacrocycleEditor.kt` 131KB + 10 componentes (BlockRoadmap 32KB, DayView 42KB, SplitView 65KB, VolumeView 65KB, ProgressView 41KB…) · **MVVM + StateFlow**

## Resumen ejecutivo

La UI del detalle **funciona y expone estado reactivo real** (`ProgramDetailViewModel` con `combine(programs, feedbacks)` → `volumeRecommendations` escaladas, `roadmapBlocks`, `simpleRoadmapLoopMarkers`, `totalWeeks`, `isActiveProgram` etc.). Pero **`MacrocycleEditor.kt` es god-file (131KB)** que concentra roadmap, key-dates, plantillas/protocolos, calendario y diálogos `Block/Meso/Week/KeyDate`. `SplitView`/`VolumeView`/`DayView` son pesados pero no rotos; el cuello es la **edición que copia todo `Program` + `normalizedTemporalStructure()`** en cada micro-cambio.

`ProtocolsView` no existe como pantalla — vive como sheet del editor (comentario fantasma en `ProgramProtocolEngine.kt:25-27`).

## Tabla de hallazgos

| ID | Sev | Título | Archivo:línea |
|---|---|---|---|
| U-01 | P2 | God-file `MacrocycleEditor.kt` 131KB: roadmap + key-dates + plantillas/protocolos + calendario + dialogs | `MacrocycleEditor.kt:1-160,850-1300` |
| U-02 | P1 | Semana competición solo muestra nota `macro_editor_competition_week_note`, sin CTA “Crear sesión” | `strings.xml:431` `MacrocycleEditor.kt:110-119` `DayView.kt` |
| U-03 | P2 | Cada edición hace `current.copy(macrocycles=...).normalizedTemporalStructure()` + `repository.updateProgram` (re-serializa todo) | `ProgramDetailViewModel.kt:762-812,814-850` + `ProgramRepository.kt:64-69` |
| U-04 | P2 | `ProgramDetailViewModel` 1.800 líneas: `init { combine(activeProgramState,program,roadmapBlocks) }` + `combine(activeProgramState,program,currentWeeks)` con `collect {}` sin `distinctUntilChanged` en el `combine` interior | `ProgramDetailViewModel.kt:378-413` |
| U-05 | P2 | `SplitView` page size 5 + `remember(program.id, program.macrocycles)` — recomputa todo si cambia cualquier meso | `SplitView.kt:80-112` |
| U-06 | P2 | `VolumeView` helper `canonicalMuscleCatalog` hardcodeado 18 músculos vs `VolumeCalculator.standardVolumeMuscles` (17) — divergencia posible | `VolumeView.kt:102-120` `VolumeCalculator.kt:46-64` |
| U-07 | MEJORA | `DayView`/`VolumeView`/`ProgressView` sin `key` estable en listas de sesiones/músculos | `DayView.kt` `VolumeView.kt` |

## Hallazgos detallados

### U-01 — God-file (P2, deuda mantenibilidad)
131KB, ~2.500 líneas, importa `ProgramKeyDateEngine`, `ProgramProtocolEngine`, `ProgramCalendarEngine`, `PROTOCOL_LIBRARY`, `PROGRAM_TEMPLATES`, `SPLIT_TEMPLATES`, `HazeState`, dialogs bloque/meso/semana/key-date y sheets roadmap/loops/library. `SimpleCalendarizationSheet:850-1300` sola es ~450 líneas.

**Dirección:** descomponer en `MacrocycleRoadmapSection`, `KeyDatesSection`, `CalendarSection`, `LibrarySection` + dialogs en `screens/programdetail/components/editor/`. Hoist estado al VM.

### U-03 — Re-serialización total (P2)
`updateWeekTrainingDayDate`, `updateBlockMetadata`, `deleteWeekFromRoadmap` etc. hacen `current.copy(macrocycles = current.macrocycles.map { macro.copy(blocks=...) })` + `.normalizedTemporalStructure()` + `repository.updateProgram` que serializa `Program` completo a `ProgramEntity.data TEXT`. Correcto funcionalmente, caro si `Program` 20 sem×6 ses×8 ej (~200KB JSON).

**Dirección:** medir con `ProgramJsonSizeTest` (ver Plan 03 T4); si >300KB, patch por `weekId`.

### U-06 — Catálogo canónico duplicado (P2)
`VolumeView.canonicalMuscleCatalog` 18 entries vs `VolumeCalculator.standardVolumeMuscles` 17 — divergencia silenciosa en qué músculos se muestran.

**Dirección:** `VolumeView` debe leer `VolumeCalculator.standardVolumeMuscles` directamente.

## Funcionalidades huérfanas (UI sin backend real)

- **Crear sesión competición desde semana:** prometida `macro_editor_competition_week_note` (U-02), backlog real — creación solo vía `CompetitionConfigSheet` en editor de sesión.
- **Publicación programa:** `tags/author/isPublic` (`Program.kt:22-23`) sin UI de publicación — no prometida, no es bug.

## Cobertura tests y gaps

Existente: `ProgramDetailViewModelTest` (existe, no leído exhaustivo en esta pasada). Gaps: `MacrocycleEditor` sin UI tests, `SplitView` paginación + multi-select, `VolumeView` canonical divergence.

## Preguntas abiertas

1. ¿Presupuesto de líneas objetivo para `MacrocycleEditor` tras descomposición (<400)?
2. ¿Patch por `weekId` o normalización Room si JSON >500KB?
