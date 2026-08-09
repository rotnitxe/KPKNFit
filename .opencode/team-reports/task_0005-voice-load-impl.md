# Implementación task_0005 — Respuesta a «¿Qué carga usaste?» ignorada la 1ª vez

Ejecuta el plan de `task_0002-voice-load.md` (F1 mínimo, F2, F3). Validación: ver abajo.

## Archivos editados

### 1. `services/workout/WorkoutContinuousVoiceEngine.kt` — F1 (raíz, camino A)

- **Guardia anti-eco acortada con clarificación pendiente**: en `EngineCommand.Resume` (:902-912) la guardia ahora es `postTtsGuardMs(pendingClarificationActive)` en vez del fijo `POST_TTS_GUARD_MS` (600 ms). El flag ya lo conoce el engine por `UpdateGrammar` (`:209` escribe el `@Volatile` sincrónicamente y `:926` en el actor — llega antes que el `Resume`, mismo canal FIFO).
- Nuevas constantes/visibilidad: `POST_TTS_GUARD_MS` ahora `internal` (:1534); nueva `POST_TTS_CLARIFICATION_GUARD_MS = 150L` (:1536).
- Nuevo helper top-level `postTtsGuardMs(pendingClarificationActive)` (:1577) — testeable.
- Efecto combinado: el discard de PCM (600→150 ms) y la supresión `vosk_empty_final_partial_suppressed` comparten `postTtsGuardUntilMs`, así que ambos se relajan durante clarificaciones.

### 2. `services/workout/WorkoutVoiceController.kt` — F1 controller + F2 + F3

- **F1 (partial-fallback)**: `schedulePartialFinalFallback` (:1346+) — dentro de la ventana post-TTS ahora se registra `lastPostTtsWindowPartialAtMs` y la supresión (`partial_fallback_suppressed_post_tts`) solo aplica si `_state.value.pendingAction == null`; con clarificación viva el partial se permite (la guardia engine-side ya se acortó).
- **F2 (gracia stale)**: `handleFinalResult` — `isStaleConfirmGraceEligible` reemplazada por `staleFinalGraceDecision(stage, pendingAction, transcript, plausibleClarificationReply)` (enum `StaleFinalGraceDecision` ACCEPT_AS_CONFIRM / ACCEPT_AS_CLARIFICATION / DROP, :3739-3767). `ACCEPT_AS_CLARIFICATION` = LISTENING + pendingAction MissingSlot/ConfirmPlannedValue/ConfirmSuggestedLoad + respuesta plausible (número o sí/no) → se reprocesa como final normal hacia `processCommand` (log `stale_final_grace_accepted` con `mode=clarification`); NO se ruta a `handleConfirmInput`. CONFIRM_WAIT+sí/no sigue yendo a `handleConfirmInput` y tiene precedencia.
- **F2 (anuncios)**: `resumeListening` (:3457-3463) — `announceSuggestedLoadForCurrentSetOnce()` y `announcePendingUnilateralSideOnce()` solo si `pendingAction == null`; las llaves "...Once" no se marcan, así que se reagendan solas al resolverse la clarificación.
- **F3 (misses perdonados)**: nuevo `registerClarificationCaptureGrace(transcript)` (:1552) — no consume `clarificationMisses` si el miss es artefacto de captura (`isClarificationMissCaptureArtifact`: ruido, o habla capturada hace ≤ `CLARIFICATION_MISS_POST_TTS_GRACE_MS = 1_200L` en la ventana post-TTS), con tope anti-bucle `MAX_CLARIFICATION_CAPTURE_GRACE = 2` (`clarificationCaptureGraceUsed`). Re-pregunta la MISMA pregunta en las 4 ramas con miss: `ReaskIntensity` (solo si valor == null), `MissingSlot` (mismo slot), `ConfirmPlannedValue` (re-pregunta lo planificado, sin transicionar), `ConfirmSuggestedLoad` (re-pregunta la sugerencia, sin transicionar). Log `guided_clarification_miss_graced`.
- **F3 (acumulador limpio)**: `voskAccumulator.reset()` + `clarificationCaptureGraceUsed = 0` al crear las 4 clarificaciones (`ConfirmPlannedValue`, `MissingSlot` VALUE, `ConfirmSuggestedLoad`, `MissingSlot` WEIGHT) y reset de cortesías también en `ReaskIntensity`.
- Helper puro testeable `isClarificationMissCaptureArtifact(noise, lastPostTtsWindowPartialAtMs, nowMs, graceWindowMs)` (:3774).

### 3. Tests (`app/src/test/java/com/example/kpkn/services/workout/`)

- `WorkoutVoiceControllerConfirmGraceTest.kt` — reescrito a la nueva API enum: gracia sí/no en CONFIRM_WAIT, drop de no-confirm en CONFIRM_WAIT, **aceptación como clarificación** con las 3 pendingActions + respuesta plausible, drop si no plausible / sin pendingAction / stage PROCESSING / pendingAction de otro tipo (LoadMode).
- `WorkoutVoiceCaptureArtifactTest.kt` — NUEVO (4 tests): guardia post-TTS acortada con clarificación (`postTtsGuardMs`), y `isClarificationMissCaptureArtifact` (ruido / dentro de ventana / fuera de ventana / sin evidencia).

## Validación

- `gradlew.bat compileBaseDebugKotlin` → **BUILD SUCCESSFUL in 3m 18s** (única advertencia en el archivo, :2009 `ExerciseReplacement` duplicado en `when`, preexistente).
- `gradlew.bat testBaseDebugUnitTest --tests "*Voice*"` → **BUILD SUCCESSFUL in 41s**: 26 suites de voz, **165 tests, 0 fallos, 0 errores**; las 2 suites tocadas/nuevas en verde (ConfirmGrace 6/6, CaptureArtifact 4/4).

## Notas / follow-up sugerido

- Observabilidad en logs JSONL: esperar menos `stale_final_discarded` con transcript legítimo, nuevos `stale_final_grace_accepted` (mode=clarification) y `guided_clarification_miss_graced`; `partial_fallback_suppressed_post_tts` ya no debe aparecer durante clarificaciones.
- La variante completa de F1 (flag `antiEchoGuard` en `EngineCommand.Resume` propagado por el puerto) quedó descartada a favor de la mínima recomendada.
