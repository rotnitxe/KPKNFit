---
flags: [room]
---

# F1 — Persistencia robusta de la sesión en vivo

Depende de F0 (slot + decode + remap). Cierra: **PERS-03, PERS-04, PERS-05/TMR-12, PERS-06, PERS-07, PERS-08/TMR-05, PERS-09, PERS-10, PERS-11, PERS-12** (PERS-13/14/15/16 P2-P3 menores incluidos si caben sin inflar).

## Objetivo

El borrador sobrevive a background, process death y fallos de Room. El finish no anuncia éxito sin write. El GPS no queda huérfano. El modal de avance de volumen no depende de un ongoing ya borrado.

## Rutas

| Ítem | Archivos |
|------|----------|
| Campos draft | `data/models/WorkoutLog.kt` (`OngoingWorkoutState`), `screens/workout/WorkoutPersistenceController.kt` (`buildOngoingUpdate`), `screens/workout/WorkoutSessionHydrator.kt`, `screens/workout/WorkoutUiModels.kt` |
| Cola persist | `WorkoutPersistenceController.kt`, `ProgramRepository.kt` (`updateOngoingWorkoutAndFlush`, `writeOngoingLocked`) |
| Lifecycle | `screens/workout/WorkoutScreen.kt` (`DisposableEffect` ~356), `MainActivity.kt` (`onStop` ~274), `WorkoutViewModel.flushOngoingForBackground` |
| GPS | `WorkoutViewModel.onCleared` ~6046, `WorkoutSessionOverlaysHost.kt` abandon/pause, `services/cardio/CardioGpsForegroundService.kt` |
| Finish | `WorkoutFinishController.kt` (~414-531), `WorkoutScreen.kt` `onConfirm` finish, `ActiveWorkoutHolder` |
| Hydrator sesión huérfana | `WorkoutSessionHydrator.kt:82-118` |

Sin bump de `KpknDatabase` version: campos nuevos **con default** en `OngoingWorkoutState` (`ignoreUnknownKeys` + defaults).

## Impacto

### PERS-03 — Campos que faltan en el snapshot

Añadir a `OngoingWorkoutState` (todos con default):

- `postExerciseFeedbackByExerciseId`
- `planDeviations`
- `showFinishSheet` + `finishResumeSnapshot` (`@Serializable` en `FinishResumeSnapshot` / `WorkoutEditingState` si aún no lo son; extraer DTOs serializables si hay tipos Compose)
- `godModeUndoStack` (serializar `GodModeUndoSnapshot`; `Session` ya es serializable)
- `pendingVolumeAdvances` + `showVolumeAdvanceModal` + `volumeAdvanceHandled` (también PERS-07)
- `archivedCompletedExercises` placeholder vacío; F4 lo llena (D3). Incluir el campo ya en F1 para no reabrir JSON.

`buildOngoingUpdate` y hydrator: copy ida y vuelta. Tras process death con finish abierto, reabrir sheet. Tras feedback post-ejercicio, molestias presentes.

`GodModeUndoSnapshot` / `FinishResumeSnapshot` hoy no están `@Serializable`. Añadir anotación o un DTO `Ongoing*` paralelo. No persistir `CoachMessage` ni caches de performance (se regeneran).

### PERS-04 — Debounce sin `apply` stale

Hoy `persist(immediate=false)` captura `apply = buildOngoingUpdate(state)` y lo lanza a 350 ms bajo `NonCancellable` en el repo.

Contrato nuevo:

1. Debounce **no** captura el lambda sobre un state viejo. El job, al despertar, llama `buildOngoingUpdate(getState())` fresco.
2. Escrituras serializadas en una cola FIFO del controller (un solo `Mutex`/Channel). `persistAndAwait` encola y espera su propio write, no cancela un write ya dentro de Room.
3. Quitar `NonCancellable` del debounce. Dejarlo solo en `finalizeWorkout` / `clearOngoing`.
4. `persist(immediate=true)` no usa `runBlocking` en Main (PERS-12): `scope.launch(IO)` + si el caller necesita await, `persistAndAwait` desde coroutine (`recordSet` ya lo hace).

### PERS-05 / TMR-12 — Flush al background

`flushOngoingForBackground()` existe y **no tiene callers**.

1. `WorkoutScreen` `LifecycleEventObserver`: `ON_PAUSE` y `ON_STOP` → `viewModel.flushOngoingForBackground()` **además** de voz.
2. `MainActivity.onStop`: además de `flushPendingWrites()`, `ActiveWorkoutHolder.get()?.flushOngoingForBackground()` (añadir el método al holder o al VM expuesto).
3. Tick de movilidad: `persist(immediate=false)` o persistir `endsAtMs` una vez (F5 cambia el timer; en F1 al menos no `runBlocking` cada segundo).

### PERS-06 — Snapshot si el template desapareció

`loadSession()` hace `foundSession ?: return false` **antes** de usar `OngoingWorkoutState.session`.

Si `foundSession == null` pero `ongoingWorkout` tiene `session.id == sessionId` (o al menos `programId` match): hidratar **desde el snapshot**. UI banner: “Esta sesión ya no está en el programa; puedes terminar o abandonar lo registrado.” No mezclar ids con un template nuevo.

Si el slot es de **otra** sesión, F0 conflicto aplica.

### PERS-07 — Volume-advance durable

Hoy el log se escribe y se borra ongoing; luego el modal vive solo en RAM (`WorkoutFinishController.kt:479-501`).

Opciones (elegir **A**):

**A (canon):** no borrar ongoing hasta cerrar el modal. Tras `finalizeWorkout` exitoso, marcar en el snapshot `logAlreadyWrittenId` + `pendingVolumeAdvances`. `isComplete` sigue false. Kill → hydrator reabre el modal, no duplica el log (`finalizeWorkout` ya es idempotente por operation id si existe; si no, guard `savedLogId`). Al confirmar/omitir avances: entonces `clearOngoing` + `isComplete` + `onComplete`.

**B:** tabla Room aparte. Más schema. No en esta ola.

`ActiveWorkoutHolder.clear()` se mueve al cierre real del modal, no al defer.

### PERS-08 / TMR-05 — Stop GPS

En `onCleared`, `cancelWorkout`, `abandonWorkoutWithoutSaving`, “Pausar y salir”, “Abandonar”, `finishUpToCurrentPoint`:

```kotlin
CardioGpsTracker.stop()
CardioGpsForegroundService.stop(appContext)
```

Hoy solo `recordCardioSetUsingGps` (~4550). “Pausar y salir”: persistir snapshot cardio **luego** stop.

### PERS-09 — `onFailure` en UI

El `onConfirm` de `WorkoutFinishHost` / `WorkoutScreen` debe pasar `onFailure` → snackbar/toast con `finishWarning`. No silenciar. El draft permanece (ya ocurre si el write falla).

### PERS-10 / PERS-12 — Main thread y silencios

- Prohibido `runBlocking` en `persist()` desde el hilo Main. `immediate` = `scope.launch(IO)` fire-and-forget **o** `persistAndAwait` desde el caller ya suspend.
- `runCatching` vacío: log `KpknDiagnosticLogger` namespace `workout` + opcional `workoutToastNotice` si `immediate` y falló.
- `writeOngoingLocked` no-op si `_ongoingWorkout == null`: tras volume-advance (A) el ongoing sigue existiendo. Si alguien llama persist con ongoing null, log; no tragar.

### PERS-11 — Cursor al hidratar

Si `finishResumeSnapshot` / `showFinishSheet` persistidos → reabrir finish. Si no, `firstIncompleteStep` **incluyendo** omitted/skipped (alinear con navigator; hoy el probe los ignora). Si no hay incompletos y no hay finish persistido → `openFinishSheet()`.

### Menores si el diff lo permite

- PERS-14: permitir finish de sesión con solo warmup/mobility como log no-AUGE o mensaje explícito “no hay series de trabajo” + abandono. No inventar drain.
- PERS-16: unificar abandon overlay → `abandonWorkoutWithoutSaving` (telemetría).
- PERS-13: no tocar wall-clock en F1.

## Pruebas

| Test | Qué |
|------|-----|
| `OngoingWorkoutStateRoundTripTest` | encode/decode con feedback, finish snapshot, volume-advance |
| `WorkoutPersistenceDebounceTest` | dos `persist(false)` rápidos; el write usa el state **último**; un `persistAndAwait` no queda pisado |
| `WorkoutSessionHydratorMissingTemplateTest` | template null + snapshot presente → UI hidratada |
| `WorkoutFinishVolumeAdvanceDurableTest` | tras finalize con surplus, ongoing sigue; segundo finalize no duplica log |
| `ProgramRepositoryFinalizeWorkoutTest` | sigue verde (atómico log+delete **después** del modal, ajustar aserción si el delete se retrasa) |

Cuidado: si A cambia el momento del `clearOngoing`, actualizar `ProgramRepositoryFinalizeWorkoutTest` para el nuevo orden (log escrito; ongoing sigue hasta ack de volume o hasta finish sin surplus).

```bash
./gradlew --no-daemon --console=plain testBaseDebugUnitTest --tests '*Ongoing*' --tests '*WorkoutFinish*' --tests '*Hydrator*' --tests '*Persistence*'
```

## Riesgos

- Serializar `FinishResumeSnapshot.editingState` / undo: si un tipo no es `@Serializable`, extraer DTO; no meter Bitmap/Uri crudos.
- Retrasar `clearOngoing` (PERS-07 A): Home floating card sigue visible durante el modal. Correcto. Guard F0: no empezar otra sesión sin diálogo.
- `flush` en ON_PAUSE + debounce: el join del job no debe ANR; `withTimeout(1500)` como MainActivity.

## Fuera de alcance

Fórmulas AUGE, remap (F0), voz pause (F6), tipo FGS (F5).
