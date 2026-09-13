---
flags: [room]
---

# F0 — Hotfix P0 de la sesión en vivo

Dependencias: D1–D6 bloqueadas en [2026-09-12_live-workout-session-fixes.md](2026-09-12_live-workout-session-fixes.md). No empezar F1 hasta que estos tests pasen.

Cierra: **PERS-01, PERS-02, EST-01, EST-02/NAV-01/VOL-04, UI-01**.

## Objetivo

1. No se puede pisar un borrador `ongoing_workout` de otra sesión sin confirmación.
2. Un JSON ilegible no se borra en silencio ni se sustituye por una sesión vacía.
3. Quitar/reordenar series o ejercicios remapea o poda `completedSets` y mapas hermanos por `ExerciseSet.id`.
4. Abrir Setup de un ejercicio sin series no crashea.

## Rutas

| Área | Archivos |
|------|----------|
| Slot único | `data/repository/ProgramRepository.kt` (`startWorkout`, `ongoingWorkout`), `screens/workout/WorkoutSessionHydrator.kt`, `MainActivity.kt` (`onStartWorkout` ~1044 y ~1291), `screens/home/HomeScreen.kt`, `screens/home/HomeSessionSection.kt`, `screens/programdetail/**` |
| JSON | `data/db/Entities.kt` (`dbJson`, `OngoingWorkoutEntity.toOngoingWorkoutState`) |
| Claves | `screens/workout/WorkoutSessionContracts.kt` (nuevos helpers junto a `parseCompletedSetKey` / `workoutSetKey`), `screens/workout/WorkoutStructuralPersistenceController.kt` (`applySessionMutation`), `screens/workout/WorkoutViewModel.kt` (`removeSetFromExercise`, `removeExerciseFromSession`) |
| Crash Setup | `screens/workout/WorkoutExerciseSheets.kt:728` (`ExerciseSetupSheetContent`) |
| Tests | nuevos bajo `screens/workout/` y `data/repository/` + `data/db/` |

Sin migración Room (`version` intacta). Sin voz, sin fórmulas AUGE.

## Impacto

### PERS-01 — Conflicto de slot

`ongoing_workout` es `rowId = 1`. `startWorkout` hace upsert ciego.

1. Añadir en `ProgramRepository`:

```kotlin
sealed class StartWorkoutResult {
    data object Started : StartWorkoutResult()
    data class Conflict(val existing: OngoingWorkoutState) : StartWorkoutResult()
}

fun startWorkout(state: OngoingWorkoutState, replaceExisting: Boolean = false): StartWorkoutResult
```

- Si `_ongoingWorkout == null` o misma identidad `(programId, session.id)` → upsert, `Started`.
- Si identidad distinta y `replaceExisting == false` → **no escribir**, devolver `Conflict`.
- Si `replaceExisting` → upsert como hoy.
- El hydrator de una sesión **distinta** no llama `startWorkout` a ciegas. Si hay conflicto, el VM publica `pendingOngoingConflict: OngoingWorkoutState?` y la UI muestra diálogo: **Reanudar la sesión en curso** / **Descartar y empezar esta**.
- Callers de navegación (`MainActivity.onStartWorkout` Home y Program Detail, play del carrusel que no es `isOngoing`):
  - Si `ongoingWorkout != null` y `(programId, session.id)` distintos → no navegar aún; diálogo a nivel Home/ProgramDetail **o** navegar igual y que el hydrator bloquee en overlay (preferir overlay en `WorkoutScreen` para deep links).
  - Deep link / floating card a la sesión **en curso** → reanudar, sin diálogo.
- `onResumeWorkout` no cambia.

Copy del diálogo (es): “Ya hay un entrenamiento en curso: {nombre}. Si empiezas este, se perderá el anterior.”

### PERS-02 — Decode que no mata el draft

Hoy (`Entities.kt:16,101-103`):

```kotlin
internal val dbJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun OngoingWorkoutEntity.toOngoingWorkoutState(): OngoingWorkoutState? = runCatching {
    dbJson.decodeFromString<OngoingWorkoutState>(data ?: "{}")
}.getOrNull()
```

1. `dbJson`: añadir `coerceInputValues = true` (enums desconocidos → default). **No** cambia el encode de otras entidades que usan el mismo `dbJson`; es compatible hacia adelante.
2. Reemplazar `getOrNull()` por un resultado explícito:

```kotlin
sealed class OngoingDecode {
    data class Ok(val state: OngoingWorkoutState) : OngoingDecode()
    data object Empty : OngoingDecode()
    data class Corrupt(val raw: String, val cause: String) : OngoingDecode()
}
```

- `data.isNullOrBlank()` o `"{}"` → `Empty`.
- decode OK → `Ok`.
- excepción → `Corrupt`; **no** `clearOngoingWorkout`.
3. Al hidratar el repo (init Room): si `Corrupt`, `_ongoingWorkout` se queda `null` **pero** se expone `ongoingWorkoutCorrupt: Boolean` / el raw se deja en la fila.
4. `WorkoutSessionHydrator`: si la fila es corrupt y el usuario abre esa (u otra) sesión → **no** `startWorkout`. UI: “No se pudo leer el entrenamiento en curso. Puedes reintentar o descartarlo.” Descartar = `clearOngoingWorkout` explícito.
5. Test de round-trip con campo extra (ignoreUnknown) y con enum inventado en un campo con default (coerce). Test de payload sin default imposible: marcar `Corrupt`, no overwrite.

### EST-01 / EST-02 — Remapeo central por `set.id`

`applySessionMutation` (`WorkoutStructuralPersistenceController.kt:497-572`) **no** toca `completedSets`. `removeSetFromExercise` recorta la lista (`WorkoutStructuralEditor.kt:190-197`) y las claves `exerciseId_idx` se desalinean.

Añadir en `WorkoutSessionContracts.kt` funciones puras:

```kotlin
fun remapIndexKeyedMap(
    previous: Session,
    next: Session,
    keys: Map<String, T>,
): Map<String, T>

fun remapIndexKeyedSet(previous: Session, next: Session, keys: Set<String>): Set<String>
```

Algoritmo (para cada clave parseable):

1. `parseCompletedSetKey(key)` → `(exerciseId, setIdx, side)`.
2. Ejercicio ausente en `next` → **drop** (EST-01).
3. `setId = previous.exercise(exerciseId).sets[setIdx].id`; si no hay → drop.
4. `newIdx = next.exercise(exerciseId).sets.indexOfFirst { it.id == setId }`; si `< 0` → drop (serie borrada).
5. Reescribir `workoutSetKey(exerciseId, newIdx, side)`.

Aplicar el mismo remap a: `completedSets`, `omittedSetKeys`, `setDrafts`, `manualLoadOverrides`, `persistedLoadModeBySet`, `setAdvancedFeedback`.

Además en `applySessionMutation`:

- Podar `skippedExerciseIds`, `warmupCompletedExerciseIds`, `mobilityCompletedExerciseIds`, `postExerciseFeedbackByExerciseId` de ids que ya no están en `next.allExercises()`.
- Recalcular `activeStepKey`: si el step ya no existe en `workoutStepPositions`, usar `firstIncompleteStep`.
- **No** borrar drafts/overrides del `preferredExerciseId` entero (NAV-02 se cierra aquí: solo remap). Si el preferred es un vecino por borrado, no limpiar sus drafts.
- `currentExerciseIdx` / `currentSetIdx` ya se coercen; tras remap, alinearlos al `activeStepKey`.

`removeExerciseFromSession` deja de usar el vecino como `preferredExerciseId` para wipe. Preferir el ejercicio **actual** si sigue vivo; si se borró el actual, `firstIncomplete` / vecino solo para el cursor.

No cambiar aún replace/Ultra Fast (F4, D3). El prune genérico sí quitará claves de ejercicios eliminados, que es lo que hace falta en F0.

### UI-01 — Setup sin series

`WorkoutExerciseSheets.kt:728`:

```kotlin
currentSet = currentSet ?: exercise.sets.first(),
```

Cambiar a `currentSet ?: exercise.sets.firstOrNull() ?: return` (o rama “sin series” vacía). El host ya pasa `firstOrNull()` (`WorkoutStructureSheetsHost.kt:1226`).

## Pruebas

Nuevos (obligatorios para cerrar F0):

| Test | Aserción |
|------|----------|
| `StartWorkoutConflictTest` | `startWorkout` de otra sesión sin `replaceExisting` no pisa memoria ni Room (si el test usa fake; si toca Room, patrón `ProgramRepositoryFinalizeWorkoutTest`) |
| `OngoingWorkoutDecodeTest` | JSON con clave extra → Ok; enum desconocido con default → Ok vía coerce; JSON roto → Corrupt y la fila no se limpia |
| `RemapCompletedSetKeysTest` | borrar serie índice 1 de 3 hechas: claves quedan `_0` y `_1` con los `CompletedSet` de las series 0 y 2 (ids); no queda huérfana `_2` |
| mismo | borrar ejercicio: ninguna clave `exId_*`; drafts del vecino intactos |
| mismo | omit keys y drafts se desplazan igual |
| `WorkoutExerciseSetupEmptySetsTest` (JVM del contenido si se extrae el `first()` a helper, o test del helper) | `sets` vacío no lanza |

Regresión: `ParseCompletedSetKeyTest`, `ProgramRepositoryFinalizeWorkoutTest`, `WorkoutStructuralEditorTest`, `WorkoutVolumeDeltaTest`.

```bash
./gradlew --no-daemon --console=plain --warning-mode=summary testBaseDebugUnitTest --tests '*.StartWorkoutConflictTest' --tests '*.OngoingWorkoutDecodeTest' --tests '*.RemapCompletedSetKeysTest' --tests '*.ParseCompletedSetKeyTest' --tests '*.ProgramRepositoryFinalizeWorkoutTest'
```

## Riesgos

- `coerceInputValues` en `dbJson` global: un enum mal escrito se vuelve default en **todas** las entidades. Aceptable; peor es perder el draft. No añadir `isLenient`.
- Remap por `set.id`: si algún mutador clona series con UUID nuevo (Ultra Fast), F0 **poda** esas hechas. F4 debe snapshottear antes de densificar. No “arreglar” Ultra Fast aquí con heurística por índice (reintroduce EST-02).
- Diálogo PERS-01: deep link a otra sesión debe ver el mismo conflicto (por eso el guard va en `startWorkout` + hydrator, no solo en el botón de Home).
- Tests Room reales son lentos; preferir fakes del mutex/`upsert` si el repo no es testeable sin DB. Si `ProgramRepository` exige Room, seguir el patrón de `ProgramRepositoryFinalizeWorkoutTest`.

## Fuera de alcance F0

PERS-03..16, flush ON_PAUSE, GPS, builders AUGE, pager, voz, God Mode undo, persistencia al plan.
