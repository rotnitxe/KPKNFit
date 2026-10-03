# Nota del paquete `repo` (ProgramRepository y detalle de programa)

Edición solo con `Edit`/`Write` de archivos nuevos; sin Gradle, adb ni tests. Los diffs se revisaron contra
`snapshot-pre-wave1`. Los archivos con finales de línea mixtos (ProgramDetailViewModel, ProgramDetailScreen,
DayView, ProgramDetailViewModelTest) quedaron en CRLF puro porque la herramienta Edit normaliza el archivo
entero: el diff crudo muestra esas líneas como tocadas, `git` las normaliza con `autocrlf`.

## ProgramRepository.kt (único dueño)

| Ítem | Qué cambió | Por qué |
|---|---|---|
| 1 / F2 | `finalizeWorkout`: `val nativeInstances` sube antes y `cycleNumber = if (isComplex && !nativeInstances) 1 else log -> run -> cursor -> 1`. | Los planes nativos COMPLEX ya no guardan el ciclo 2 como ciclo 1 (rojo T006:1140). El COMPLEX lineal sin instancias sigue forzando 1. |
| 1 / F-02 | `executedTrainingEvidence`: para `requiresNativeWeekInstances()` solo cuenta logs del `runState.cycleNumber` (un log sin ciclo sigue contando; la sesión en curso siempre protege su id). Resto de programas: igual que antes (AUGE §14.5 intacto). | Las sesiones se reutilizan con los mismos ids en cada ciclo; sin el filtro todas parecían «entrenadas» en el ciclo 2. |
| 2 / F3 | `clearOngoingWorkout()` delega en `launchClearOngoingWorkout()` (internal, devuelve `Job?`): captura todo salvo `CancellationException` y registra con `Log.w`; también limpia una fila corrupta cuando no hay estado en memoria. `clearOngoingWorkoutAndFlush` NO cambia (sigue lanzando: borrado antes de lo terminal, error conserva la sesión, retry posible). | El scope no tiene `CoroutineExceptionHandler`: el `check` fire-and-forget tumbaba el proceso desde `archiveProgram`. |
| 3 / F4 + P6 | `resolveNativeProgressionProposalNow` ya no sostiene `ongoingWorkoutMutex` durante `mutateProgramNow`: instantánea (ids de logs + sesión en curso) bajo el mutex, lo suelta, revalida dentro del transform (aborta con `null` y reintenta, hasta 5 veces con 25 ms, si el mutex está tomado —finalización/inicio/escritura en vuelo— o la instantánea cambió). KDoc con el orden global `ongoing -> programRmw -> Coordinator -> programWrite -> active`. `replaceProgramSafely` NO se tocó (Coordinator -> ongoing queda como excepción documentada: ningún camino sostiene ongoing mientras pide Coordinator). | Elimina el ciclo ongoing<->Coordinator. La comprobación `isLocked` evita que la reserva de la propuesta le quite su avance a una finalización concurrente (misma guarda de versión que F5). |
| 4 / F5 | `flushPendingWrites`: reservas de versión + snapshots + escritura de ongoing dentro de `ongoingWorkoutMutex.withLock`; las escrituras de programas/activo/logs siguen bajo `programWriteMutex` con las guardas de versión. | Una finalización en vuelo ya no puede perder programa avanzado ni cursor por una reserva del flush. |
| 4 / F6 | Reconciliación del cursor nativo movida a `normalizeProgramWithCompetitions` (`reconcileNativeCursorOfActiveProgram`): el programa que se reserva/persiste/publica ya viene reconciliado (idempotente). `repairActiveStateIfNeeded` conserva un camino residual para publicaciones sin normalizar (recuperación del editor, `reconcileTemporalState`) con CAS (`current == program`), versión reservada y persistencia por el lane normal; una copia obsoleta aborta sin pisar nada. | Antes se publicaba `_programs` sin reservar versión ni persistir, desde una copia capturada. |
| 4 / F7 | `loadFromDb` persiste `normalized` (la misma copia que publica la caché, con `repairNativeRunWeekCursor`) reservando versión (`persistProgramIfNewest`). | Antes persistía `migrated` sin reparar en cada arranque y Room nunca convergía. |
| 4 / F11 | `reserveProgramWrite` toma `programMutationLock` (reentrante) y nueva API `internal mutateProgramNowReportingVersion(programId, onVersionReserved, transform)`; `mutateProgramNow` delega en ella. `nextProgramWriteVersionForEditorRecovery()` queda exacta (toda reserva pasa por el monitor) y su KDoc remite a la API nueva. | La predicción `get()+1` fallaba con reservas hechas fuera del monitor (flush, finalización, reemplazo). El editor sigue compilando sin cambios. |
| 5 / P9 | `finalizeWorkout` solo llama a `exerciseCatalogSnapshot()` si `sourceRecipe.nativeProgression != null`; `nextProgram` pasa por `normalizedIdentityFields()`. | No pagar la instantánea del catálogo en programas de autor/legacy; identidades normalizadas antes de Room. |
| T006 | Nuevo `internal ProgramRepository.initForTestsWithDatabaseFile(context, databaseName)` (Room en archivo con nombre propio; el repositorio es dueño de la BD y `closeInstance()` la cierra). | Permite una BD aislada por test en T006 sin tocar `KpknDatabase`. |

Falsos positivos / no aplicado:
* Q5-F5(f) `rematerializePending` sin `weekOccurrence`: NO se aplicó. `PlanMaterializer.materialize` materializa todas las semanas con ocurrencia 1; pasar la ocurrencia real a la reconstrucción cambiaría los ids estables derivados y dependería solo de la adopción por weekday. Reconstruir con 1 reproduce exactamente los ids materializados.
* F9/F10/F12/F13 del informe de merge no son de este paquete.

## PlanMaterializer.kt (solo mi zona)

* `removeManualSessionOverride(program, sessionId, scope: ManualOverrideScope? = null)`: `null` = todos los alcances (llamadores actuales, mismo comportamiento); un alcance concreto deja intactos los demás.
* Nuevo `restoreSessionFromRecipe(program, sessionId, executedSessionIds, scope = null, ...)` + `SessionRestoreResult` / `SessionRestoreRejection`. Reconstruye UNA sesión en SU ocurrencia (siblings = preservados), levanta todas las marcas de esa sesión solo durante la reconstrucción y repone las de los alcances no elegidos. Rechaza (sin éxito falso, edición marcada) si: no hay marca, no hay receta, la sesión no existe, está entrenada/en curso, la semana no está en la receta, o la sesión NO tiene contraparte inequívoca (quedó después de los días de receta en `mergePreservedSessions`, o el `recipeDayId` reconstruido no coincide con el del override/ejercicios previos).
* Alcance elegido por defecto: `SESSION` si existe, si no `TEMPLATE_FUTURE_OCCURRENCES`.

## Detalle de programa

* `ProgramDetailViewModel`: `restoreManualSessionFromPlan` usa `restoreSessionFromRecipe` (mensajes por rechazo; un `false` sin rechazo se reporta como conflicto de escritura, ya no con el texto de «sin edición pendiente»); nuevo `restoreBlockedSessionIds: StateFlow<Set<String>>` (= `executedTrainingEvidence(program).sessionIds`, historial + en curso, recalculado con programa/historial/sesión en curso).
* `DayView`: parámetro `restoreBlockedSessionIds`; el botón exige `isManuallyCustomized && program.sourceRecipe != null && id !in blocked` (el badge «Sesión personalizada» no depende de eso). `ProgramDetailScreen` (TrainingPanel) recoge el flujo y lo pasa.

## Tests

* `AprendeCatalogAuditTest`: ya no exige `version = 27`: exige versión Room >= 27 y que ninguna migración desde `MIGRATION_26_27` toque `muscle_groups/joints/tendons/movement_patterns/kinetic_chains`.
* Nuevo `ProgramRepositoryConsolidationTest` (12 tests: F2, evidencia por ciclo x2, F3 x2, F4 x2 con deadline de hilo daemon, F5, F6, F7, F11).
* `ProgramDetailViewModelTest`: 9 tests nuevos (`restoreManualSessionFromPlan_*`, `removeManualSessionOverride_scope_*`, `restoreBlockedSessionIds_*`).
* androidTest nuevo `ManualSessionRestoreUiTest` (5 tests, modelo `OptionalSessionCalendarSemanticsUiTest`).
* `T006PersistenceAndUseIntegrationTest`: BD de archivo propia por test (`@Rule TestName` + UUID, `deleteDatabase` y cierre de `CompetitionRepository`/`NutritionRepository` en `tearDown`); test 5 crea los overrides con `SessionEditorViewModel.saveSession` (edición con cambio de descanso, no solo nombre), restaura con `ProgramDetailViewModel` tras reabrir y compara contenido completo contra la receta efectiva aceptada + identidad persistida; oráculo de reconstrucción de W2 con igualdad completa (+ ids, ocurrencias únicas, cardio, SPEED); rollback con `ProgramRepository` real (caché intacta tras el fallo, publicada tras el reintento); test 1 renombrado a `fourNativeProfilesIncludingDumbbellAndBodyweightEquipmentCommitRoundTripAndUse` (no es adaptación de planes de autor); nuevo `phatHeavyWorkingSetDefinesTheSpeedLoadOfTheSameConfigurationAfterRoomReopen`.

## Riesgos

* T006: el guardado con el editor real depende de sus validaciones (`SessionEditorRulesEngine`, gate del catálogo v2) y la igualdad completa de W2 / contenido restaurado es un oráculo estricto: un rojo ahí puede ser defecto real, no ruido. No se ejecutó nada.
* SPEED (T006 nuevo): `PlanLoadResolver` NO tiene consumidor de producción (ni el pool `exerciseLoadReferences` se consulta para SPEED porque cada set declara su `reference`): el test verifica el resolutor con la serie registrada y persistida, no un flujo de producto que hoy no existe. Pendiente de producto (P11/Q5).
* `resolveNativeProgressionProposalNow` puede devolver `false` (nada aplicado, reintentable) si la sesión/historial no se estabilizan en 5 intentos; el VM ya muestra «No se pudo aplicar...».
* El filtro por ciclo de `executedTrainingEvidence` cambia el bloqueo del botón de restaurar y la protección de reconstrucciones en el ciclo >= 2 de planes nativos (intencionado).
