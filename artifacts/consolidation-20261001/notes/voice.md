# voice: edicion y deshacer por voz, Room primero

Estado: implementado por edicion, SIN compilar ni ejecutar (regla de la ola: una sola ranura de Gradle). Cada simbolo usado fue verificado con Grep.

## Que cambio

Nuevos (main, `screens/workout/`):
- `WorkoutVoiceSetMutationController.kt`: `WorkoutVoiceMutationResult` (sealed, PUBLICO: Committed(setKey, set?), NoRecentSet, NoChange, Busy, Closing, Stale, SessionChanged, PersistenceFailed(cause), `isCommitted`) y el controlador `internal` que corre bajo la `recordingGate` compartida (misma que setRecorder, preparationCommitter, cardio; finish/discard esperan `awaitIdle`).
- `WorkoutVoiceSetMutations.kt`: transformaciones puras (`resolveVoiceEditTarget`, `applyVoiceEditPatch`, `applyVoiceUndo`). La MISMA funcion se aplica al estado capturado (candidato de Room) y al estado vivo (publicacion), asi Room y UI no divergen.
- `WorkoutVoiceMutationFeedback.kt`: tabla pura resultado -> frase, `isRetryable`, `needsVisibleNotice`, `committedEditSpeech`.

Modificados:
- `services/workout/WorkoutVoiceController.kt` (CRLF preservado): `pendingUndo` pasa a `AtomicReference`; `consumePendingUndo()` y `clearPendingUndo()` ELIMINADOS (sin llamadores); nuevos `peekPendingUndo(nowMs)`, `clearPendingUndoIf(expected)` (CAS por igualdad estructural), `extendPendingUndoIf(expected, extraMs, nowMs)` y `internal armPendingUndo(...)` (extraido de `onVoiceSetPersisted`, sin cambio de comportamiento; sirve a tests).
- `screens/workout/WorkoutVoiceCommandHandler.kt`: puertos `undoVoiceRecordedSet` / `patchLastCompletedSet` ahora `suspend` y devuelven `WorkoutVoiceMutationResult`; ramas `UndoLastSet`/`EditLastSet` delegan en `handleVoiceUndoLastSet()` / `handleVoiceEditLastSet()`.
- `screens/workout/WorkoutViewModel.kt`: 3 hunks anclados por texto (campo `voiceSetMutations` antes de `private lateinit var stepNavigator`; los dos `override` del puerto; borrado de `undoVoiceRecordedSet`/`patchLastCompletedSet` del VM). No se copio nada del staging. Diff verificado contra el snapshot pre-wave1: solo esos 3 hunks.
- Tests existentes adaptados: `WorkoutVoiceCommandHandlerWarmupTest` (el Proxy responde `NoRecentSet` a los dos puertos en vez de `null`) y `WorkoutVoiceCancellationRegressionTest` (idem + contadores + 2 tests nuevos).

## Comportamiento (requisitos)

1. Nada se publica ni se habla antes de que Room confirme: el handler solo hace `peek`; el controlador llama `persistAndAwait(candidato, publicar)`; token CAS, `abortRestTimer`, sugerencias y voz ocurren despues del commit.
2. Fallo de Room: estado de UI, descanso y token intactos; mensaje hablado distinto ("No pude deshacer la serie. Tus datos se conservan; repite deshacer." / "No pude guardar el cambio...") y aviso visual (`workoutToastNotice`) para `PersistenceFailed` y `Stale`. En fallos reintentables (`PersistenceFailed`, `Busy`, `Stale`) el handler extiende el token (+6 s) para que el reintento sea posible.
3. Reintento sin duplicar: el candidato siempre sale del estado de UI, que solo cambia tras commit (un delta tras fallo se aplica una sola vez). Tras `Committed`, un segundo undo es `NoRecentSet`.
4. Concurrencia: misma compuerta, espera acotada de 1500 ms (una correccion dicha justo despues de "registra..." edita la serie recien registrada); `Busy` si no se libera; `Closing` si finish/cancel/complete/startup/conflicto; capturas dentro de la compuerta; `Skipped` de Room se reintenta UNA vez con recaptura antes de devolver `Stale`.
5. `UiPublicationFailed`: se republica (patron del recorder); si falla otra vez se reconcilia con Room (`loadSession()`) FUERA de la compuerta (evita deadlocks). Sin esto `pendingUiCommit` dejaba todas las persistencias siguientes en `Skipped`.
6. Undo: `restTimer.abortHard()` (via `abortRestTimerHard`) en vez de `stopRestTimer()` (que abre hojas de feedback/cierre). Limpieza completa del estado derivado que hoy limpia `revertExecutionError`: sheets de cierre/feedback, `pendingPostExerciseIdx`, `activeStepKey`, `setJustLoggedKey`/`editingState` si apuntaban a la clave, `setAdvancedFeedback`, `planDeviations` (conservadas si el lado contrario de la serie sigue), ultimo resultado/imbalance, energia en vivo recalculada (una sola vez, se reutiliza en la publicacion).
7. Patch: sincroniza `recordedPayloadV3` (`externalLoad`/`assistedLoad`, `completedReps`/`durationSeconds`, `actualIntensityMode/Value`), respeta carga normalizada (LOAD/LASTRE/ASSISTED; BODYWEIGHT ignora el peso), series TIME (`timeSeconds`), RPE y RIR excluyentes, %RM ignorado (-> `NoChange`), `side` solo elige el hermano unilateral existente (si no existe -> `NoRecentSet`; ignorado en bilaterales), objetivo = `setJustLoggedKey` si existe, si no la ultima serie. Delta sobre la carga tecleada, con clamp a 0 y redondeo a 3 decimales.

## Decisiones (las del informe sec. 7)

- `homologatedResultV3`/`setOutcomeV2` tras un patch: se sincronizan SOLO los campos ligados a carga/volumen que leen los motores de fatiga (`augeEquivalentLoad`, `augeEquivalentReps`); los estadisticos (eRM, percentiles, PR) quedan como se grabaron. Documentado en el KDoc de `applyVoiceEditPatch`.
- Limpieza ampliada del undo: incluida (misma regla que `revertExecutionError`).
- Undo de cardio: sin cambios (borra la serie, no restaura `cardioTimerState` ni GPS). Fuera de alcance.
- "deshacer" sin token ahora HABLA "No hay una serie reciente para deshacer." (antes silencio).
- Espera de compuerta: acotada (1500 ms), no rechazo inmediato.
- NO se anadio `token: Long` a `VoiceUndoPayload` (`services/workout/WorkoutVoiceTypes.kt` no esta en mi tabla de propiedad). Dos tokens con la misma clave y el mismo milisegundo serian iguales, pero entonces apuntan a la misma serie, asi que el CAS es equivalente en la practica.
- El token se valida contra el instante de la peticion (`requestedAtMs`), no tras la espera de compuerta: una espera de hasta 1.5 s no invalida un "deshacer" dicho dentro de la ventana de 5 s.

## Riesgos

- Sin compilar/ejecutar: todo el paquete de tests es nuevo y no se ha corrido. Los tests Room usan latches (`CompletableDeferred`) y `delay` reales bajo `runBlocking`; si hubiera flakiness, ajustar `gateWaitMs`/`delay` en `WorkoutVoiceSetMutationRoomTest`.
- `Skipped` por `pendingUiCommit` sin resolver sigue siendo `Stale` (se reintenta una vez; no se reconcilia). La responsabilidad de reconciliar queda en quien produjo `UiPublicationFailed`.
- `programId` del estado se valida contra el del VM (`SessionChanged` si difieren): el VM inicializa `WorkoutUiState(programId = programId)` y los copy lo conservan.
- Comandos de voz duplicados aguas arriba aplican un delta dos veces (residual conocido, igual que antes).
- `revertExecutionError` (VM) sigue con el patron antiguo (stopRestTimer + persist fire-and-forget): candidato a otra fase.
- Instrumentados (`WorkoutLifecycleDurabilityInstrumentedTest`, informe 5.6) NO anadidos: androidTest queda fuera de mi tabla de propiedad.

## Verificacion sugerida (de rapido a pesado, desde `android-native/`)

1. `./gradlew testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.WorkoutVoiceSetMutationsTest" --tests "com.example.kpkn.screens.workout.WorkoutVoiceMutationFeedbackTest"`
2. `./gradlew testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.WorkoutVoiceUndoTokenTest"`
3. `./gradlew testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.WorkoutVoiceCommandHandlerMutationTest" --tests "com.example.kpkn.screens.workout.WorkoutVoiceCommandHandlerWarmupTest" --tests "com.example.kpkn.screens.workout.WorkoutVoiceCancellationRegressionTest" --tests "com.example.kpkn.screens.workout.WorkoutVoiceCommandHandlerLabelTest"`
4. `./gradlew testBaseDebugUnitTest --tests "com.example.kpkn.screens.workout.WorkoutVoiceSetMutationRoomTest"`
5. Vecinos: `--tests "com.example.kpkn.screens.workout.WorkoutSetRecorderDurableRoomTest" --tests "com.example.kpkn.screens.workout.WorkoutPreparationCommitterRoomTest" --tests "com.example.kpkn.screens.workout.WorkoutRecordingGate*" --tests "com.example.kpkn.services.workout.*"`
6. `./gradlew assembleBaseDebug` (y `compileHealthDebugKotlin` si se quiere cubrir el flavor health).
