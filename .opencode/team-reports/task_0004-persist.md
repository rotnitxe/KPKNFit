# task_0004 — Diagnóstico completo: cadena de persistencia sesión de voz → WorkoutLog → AUGE → rings musculares

**Autor:** persist-dev · **Fecha:** 2026-08-07 · **Fase:** SOLO DIAGNÓSTICO (cero ediciones de código)
**Repo:** `C:\Users\valen\Documents\KPKNFit` · **Código:** `android-native/app/src/main/java/com/example/kpkn/`
**Evidencia forense:** `.opencode/tmp-voice/` (JSONL de voz + recovery logs)

---

## 0. Resumen ejecutivo

1. **Sets de voz = mismo pipeline que UI manual.** Voz y UI convergen en `WorkoutViewModel.recordSetV2` → `WorkoutSetRecorder.record` → `persistOngoingStateAndAwait()` (snapshot durable en Room por serie confirmada).
2. **El save del WorkoutLog tiene UN SOLO gatillo: la sheet Compose** `FinishWorkoutSheet` (botón manual o `LaunchedEffect` disparado por flag de voz). Único caller de `finishWorkout` en todo el repo: `WorkoutScreen.kt:935`.
3. **Ningún servicio foreground puede persistir** (`WorkoutVoiceForegroundService`, `WorkoutRestForegroundService`): cero referencias a `WorkoutLog`/`finalize`/`save`.
4. **La sesión intensa de hoy SÍ se persistió** (evidencia forense: `workout_completed` 17:27:11.671Z + `export_started` 17:27:17.311Z, eventos POST-finalize). **Los rings no drenaron porque el log entró "flaco": solo 9 sets efectivos**; el propio TTS anunció «Tu RING muscular quedó en 99 por ciento».
5. **Trampas estructurales P0 que sí cortan la inyección en otros escenarios:** (a) el TTS instruye «di sesión terminada» pero el parser final NO acepta esa frase como save → silencio total y sesión en limbo; (b) el TTS «Entrenamiento guardado con éxito» suena ANTES del write real; (c) el save por voz depende de un `LaunchedEffect` de Compose (requiere proceso vivo + UI compuesta); (d) no existe auto-finalize de sesiones huérfanas tras kill `LOW_MEMORY`/`JAVA_CRASH`.

---

## 1. Mapa de la cadena end-to-end (referencias verificadas)

**A. Confirmación de series (voz y UI convergen):**
- UI manual: `screens/workout/WorkoutV2Body.kt:955,990` → `viewModel.recordSetV2(...)`
- Voz: `services/workout/WorkoutVoiceController.kt:1562-1568` (routing por estado de UI) → parser (`WorkoutVoiceCommandParser.kt`) → `VoiceSessionCommand.RegisterSet` → `screens/workout/WorkoutVoiceCommandHandler.kt:903 handleVoiceRegisterSet` → `:969 persistVoiceSet(...)` → **`:1019 ports.recordSetV2(...)`**
- Convergencia: port declarado en `WorkoutVoiceCommandHandler.kt:76` → override en `screens/workout/WorkoutViewModel.kt:384-405` → **`WorkoutViewModel.kt:1053-1087 recordSetV2`** → `screens/workout/WorkoutSetRecorder.kt:105 record(...)`
- Escritura de estado: `WorkoutSetRecorder.kt:384-405` (`completedSets + (key to completedSet)`, claves `${exerciseId}_${setIdx}[_L|_R]`, `WorkoutViewModel.kt:1126-1130`) → **`:424 ports.persistOngoingStateAndAwait()`** → snapshot durable inmediato a Room `ongoing_workout`. **Cada serie confirmada sobrevive un kill de proceso.**

**B. Fin de sesión (voz y UI):**
- Voz «terminar»: `WorkoutVoiceCommandParser.kt:400-401` (`FINISH_SESSION_KEYWORDS`, defs `:69-72`) → `VoiceSessionCommand.FinishSession` → `WorkoutVoiceCommandHandler.kt:477` → `:582 handleFinishRequest()` → sin pendientes: `:586 ports.finishUpToCurrentPoint()`; con pendientes: `:587-590` solo habla aviso + evento `finish_pending_guard` (**NO abre cierre**, exige luego «dejar hasta acá» → `LeaveUpToHere` `:478-484` o `ConfirmFinishWithPending` `:486-489`).
- `WorkoutViewModel.kt:2266 finishUpToCurrentPoint()` → `:2298 openFinishSheet()` (`:1666-1685`: setea `showFinishSheet=true`, cierra sheets restantes, `persistOngoingState()`).

**C. Sheet → confirm → save:**
- Sheet compuesta: `WorkoutScreen.kt:869 if (uiState.showFinishSheet) {` → `:906 FinishWorkoutSheet(...)`; pasa `voiceFinalConfirmTriggered = uiState.voiceFinalConfirmTriggered` (`:921`) y `onConfirm` (`:924-935`).
- Confirm por voz: con la sheet abierta, TODO transcript se rutea a `parseFinalFeedbackCommand` (`WorkoutVoiceController.kt:1562-1569`; también `WorkoutVoiceIntentMatcher.kt:25-27`) → `VoiceSessionCommand.LogFinalFeedback` → `WorkoutVoiceCommandHandler.kt:495` → `:828 handleVoiceLogFinalFeedback(...)` → si `isSaveAction`: **`:831-838` setea `voiceFinalConfirmTriggered=true` + `voiceController.speakSessionSaved()`** (NO escribe nada en DB).
- El flag lo consume Compose: `WorkoutFinishHost.kt:854-858 LaunchedEffect(voiceFinalConfirmTriggered) → executeConfirm()` → `:824-851 onConfirm(notes, fatigue, SessionClosingFeedback, share)` → **`WorkoutScreen.kt:935 viewModel.finishWorkout(...)` (ÚNICO caller en todo el repo; grep `finishWorkout(` = 2 resultados: caller + def `WorkoutViewModel.kt:2730`)**.

**D. Save real (interior de finish):**
- `WorkoutViewModel.kt:2730-2751 finishWorkout` → `:2738 finishController.finish(...)` → `screens/workout/WorkoutFinishController.kt:61 finish(...)`:
  - `:69` guard `isFinishingWorkout || isComplete`; `:70` guard `session == null` — **sin guard de sesión vacía**.
  - `:78-124` construye `completedExercises` desde `state.completedSets` (bilateral + `_L`/`_R`, `:87-92`); **`:124 .filter { it.sets.isNotEmpty() }`** descarta ejercicios con cero sets.
  - `:126-147` `omittedExercises` (skipped sin sets); `:149-151` `totalVolume`; `:153-158` `logId = programId|sessionId|weekId|startTimeMs`.
  - `:160 scope.launch` (viewModelScope, **cancellable**) → `:162-206` `stressScore` en `Dispatchers.Default` (lee `AugeRepository.getAdaptiveCache()` `:163-165` + `AugeFatigueEngine.calculateCompletedSessionDrain` `:166-171`, ajustes de closingFeedback, penalidad técnica `:196-199`).
  - `:208-221` `muscleGroups`; `:223-227` `TrainingEnergyEngine.estimateCompletedSession`; `:228` `actualDate = LocalDate.now()`; `:229-231` `scheduledDate/scheduleDeltaDays` (puede ser null).
  - `:233-290` construye `WorkoutLog` (`date = Instant.now().toString()` `:238`, `durationMinutes >= 1` `:73`, `postExerciseReports`, `energySummary`, etc.).
  - **`:292 repository.finalizeWorkout(log)`** → `data/repository/ProgramRepository.kt:286-392`: `withContext(Dispatchers.IO + NonCancellable)` `:287` + `ongoingWorkoutMutex` `:288`; **idempotente por `log.id`** (`:289-301` retry path); insert en transacción Room `:371-380` (`workoutLogDao().insert` + `clearOngoingWorkout` + progress); **`_history.value = historyForProgress` `:382`** (esto dispara el recompute AUGE, §4.1).
  - `:319-328` `performanceRangeStore.persistFinishedSessionPerformance` en launch separado con try/catch que **traga errores** (`:326-328 printStackTrace`); `:302 updatePredictionBias`; `:306-317` agenda `PendingQuestionnaire` +24h; `:333-350` volume-advance puede diferir `onComplete`; `:351 prepareVoiceDiagnosticExport()` (POST-finalize); `:352-359` `isComplete=true`; `:360 ActiveWorkoutHolder.clear()`.

---

## 2. Pregunta (1): ¿el modo voz confirma sets sobre el mismo pipeline que la UI manual o usa camino propio?

**Sí, mismo pipeline.** No existe camino propio de persistencia de series para voz: ambos llaman `WorkoutViewModel.recordSetV2` (§1.A) y escriben el mismo `state.completedSets`, con persistencia durable por serie (`WorkoutSetRecorder.kt:424`).

**Diferencias propias del camino de voz (ambas REDUCEN lo que termina en el log):**

1. **Validación estricta y efímera:** `persistVoiceSet` exige `value > 0` y `weight > 0` salvo `LoadModeV2.BODYWEIGHT` (`WorkoutVoiceCommandHandler.kt:990-994`); si falta, rechaza con `set_persistence_rejected` + TTS de error (`:995-1007`) y no deja draft reintentable → una respuesta de carga perdida (issue 1: el sistema ignora la primera respuesta a «cuánta carga usaste») implica una serie que nunca entra al pipeline.
2. **Comandos no parseados mueren antes:** solo llegan a `recordSetV2` los transcripts que el parser acepta como `RegisterSet`; los demás quedan en `unknown_command_logged`.
3. **Evidencia forense** (`kpkn-voice-20260807-153838-cac8314a.jsonl`): `set_persistence_started` ×9 / `set_persistence_succeeded` ×9 / `set_persistence_rejected` ×0, pero `unknown_command_logged` ×11 y `guided_clarification_asked` ×5 (resueltas ×4) → el trabajo hablado real fue mayor que el registrado: **9 sets efectivos en ~2h45 de sesión** (15:42:03Z → 17:02:33Z). El otro log grande de la misma sesión (`51555bda.jsonl`, 571KB, proceso de captura de audio) tiene **cero** eventos `set_persistence_*` y cero finish/save en 1306 líneas.

---

## 3. Pregunta (2): ¿quién llama al save del WorkoutLog al terminar y el servicio de voz/foreground puede terminar sin guardar?

### 3.1 Quién llama al save

- **ÚNICO punto de entrada:** `WorkoutScreen.kt:935 viewModel.finishWorkout(...)`, dentro de `FinishWorkoutSheet` → requiere **WorkoutScreen compuesta + `showFinishSheet=true` + confirm** (botón `WorkoutFinishHost.kt:909-910` o `LaunchedEffect` de voz `:854-858`). Camino completo del voice-finish en §1.B/C.
- **`onCleared` del ViewModel NO guarda:** `WorkoutViewModel.kt:3147-3159` solo `persistence.flushForBackground()` (snapshot ongoing) + shutdown voz/timers. Nunca llama `finalizeWorkout`.
- `VoiceSessionCommand.CancelSession` → `WorkoutVoiceCommandHandler.kt:490-493 ports.cancelWorkout()` → `WorkoutViewModel.kt:2500-2509`: **borra el ongoing y NO guarda nada** (`clearOngoingWorkoutAndFlush` + reset de estado).
- `abandonWorkoutWithoutSaving` (`WorkoutViewModel.kt:2512-2521`): idem, clear sin save.
- No existe auto-finalize en Application, boot receiver, WorkManager ni startup: nada convierte un ongoing huérfano en log.

### 3.2 Servicios foreground: no persisten, pueden morir sin guardar

- `services/workout/WorkoutVoiceForegroundService.kt`: grep de `WorkoutLog|finalize|save|persist` → **cero coincidencias de persistencia**. `onTaskRemoved` → `stopCaptureAndSelf()` (`:398-401`); `onDestroy` (`:403-416`) solo frena engine, suelta wakelock y foreground; `stopCaptureAndSelf` (`:305-318`) completa prompts y `stopSelf()`. Wakelock con timeout `MAX_WAKE_LOCK_MS` (`:370-377`). Si `startForeground` falla, **mata el propio proceso** (`:358-367`) — otro kill posible mid-sesión.
- `services/workout/WorkoutRestForegroundService.kt`: grep idéntico → cero lógica de save (solo timer/alarmas).
- `services/workout/ActiveWorkoutHolder.kt:6-26`: WeakReference al ViewModel; solo delega `TimerAction` (`:23-25`); **no gatilla finish ni save**. Se limpia en finish (`WorkoutFinishController.kt:360`) y en `onCleared`.

### 3.3 Puntos de corte concretos donde la sesión de voz termina SIN guardar

1. **🔴 (P0) Trampa de keyword en el cierre.** El TTS instruye: «Para finalizar, di sesión terminada.» (`WorkoutViewModel.kt:1718`). Pero con la sheet abierta todo va a `parseFinalFeedbackCommand`, cuyos `saveKeywords` son `{"guardar y terminar","guardar entrenamiento","guardar sesion","terminar entrenamiento","finalizar entrenamiento","finalizar sesion"}` (`WorkoutVoiceCommandParser.kt:801`). **«sesión terminada» (normalizada «sesion terminada») NO contiene ninguna** → `isSaveAction=false`; y como no trae notas/neural/spinal/molestia, `updates` queda vacío → **silencio total: no habla, no avisa, no guarda** (`WorkoutVoiceCommandHandler.kt:878-889` solo actúa `if (updates.isNotEmpty())`). El usuario puede repetir la frase instruida indefinidamente sin efecto; la sesión queda en limbo con la sheet abierta hasta que cierre la app o el SO mate el proceso. (`FINISH_SESSION_KEYWORDS` sí incluye «terminar sesion», `:69-72`, pero ese branch solo corre con la sheet CERRADA, `:400-402`; «finalizar sesion» está en ambos sets y sí funciona.)
2. **🔴 (P0) TTS «guardado» prematuro.** `speakSessionSaved()` dice «Entrenamiento guardado con éxito. ¡Felicitaciones por completar tu sesión!» (`services/workout/WorkoutTtsManager.kt:255-256`) cuando lo único que ocurrió es el seteo del flag (`WorkoutVoiceCommandHandler.kt:831-838`). El write real ocurre después: LaunchedEffect → onConfirm → finishWorkout → `scope.launch` → cálculo pesado de stress (`WorkoutFinishController.kt:162-206`) → recién `:292 finalizeWorkout`. Todo ese tramo corre en `viewModelScope` **cancellable**; si el usuario cierra la app al oír «guardado» (lo natural) o hay kill LMK, la ventana TTS→insert queda abortada y el `NonCancellable` de `finalizeWorkout` (`ProgramRepository.kt:287`) nunca se alcanza.

3. **🔴 (P0) Save acoplado a Compose.** La rama de voz no llama `finishWorkout` directamente: depende de que `FinishWorkoutSheet` esté compuesta y su `LaunchedEffect` dispare (`WorkoutFinishHost.kt:854`). Si el proceso muere en background no hay callbacks aplazados: `onCleared` no finaliza (§3.1).
4. **Guard de pendientes por voz.** Con ejercicios incompletos, `handleFinishRequest` NO abre el cierre; requiere la frase exacta «dejar hasta acá» (`WorkoutVoiceCommandHandler.kt:587-590,478-489`). Cualquier otra cosa = callejón sin salida hablada.
5. **Sesión vacía se guarda igual.** Sin guard de vacío (guards `:69-70`), un finish con cero sets persistidos escribe un log con `completedExercises=[]` que drena 0 (`WorkoutFinishController.kt:124`) y “tapa” el problema.

---

## 4. Pregunta (3): ¿qué lee HomeViewModel/AugeFatigueEngine para drenar los rings y por qué la sesión intensa de hoy no los drenó?

### 4.1 Fuente de los rings del Home

- UI: `screens/home/HomeRingsSection.kt:89-95` → `rememberAugeViewModel()` + `augeSnapshot.ringScore(RecoveryChannelId.MUSCULAR/SYSTEM/STRUCTURE) / 100f` y `perMuscle` (`:90,244`).
- `HomeViewModel` NO computa rings: solo expone `feedbacks` (`HomeViewModel.kt:54,88-94` ← `AugeRepository.getPostSessionFeedbacks()`) para `overtrainedMuscles` (`:99-109`).
- Motor: `screens/auge/AugeViewModel.kt:144-147` — `programRepo.history.combine(settings) → recompute(history, settings)`; timer de 5 min para decay de recuperación (`:153-158`).
- `recompute` (`AugeViewModel.kt:169-269`) lee wellbeing (`:171-183`), feedbacks (`:184`), sleep 7d (`:185`), nutrición (`:186`), adaptiveCache (`:187-189`) y computa:
  - `AugeRecoveryEngine.getPerMuscleBatteries(history,...)` `:195-204`
  - `AugeTtcEngine.calculateArticularBatteries(history,...)` `:205`
  - `AugeRecoveryEngine.calculateGlobalBatteries(history,...)` `:206-217`
  - dashboard/readiness/pending `:218-227`; fatiga acumulada 14d con `AugeFatigueEngine.calculateCompletedSessionStress` por log `:229-241`.

- **Fuente de verdad = `ProgramRepository.history` (WorkoutLogs).** Se siembra desde Room al boot (`ProgramRepository.kt:697 _history.value = logs`) y se actualiza in-place en `finalizeWorkout` (`:382`) / `addWorkoutLog` (`:273-277`). Si el log existe, el ring se mueve solo; si no existe, ningún otro canal lo inyecta: **AUGE no recibe “events” de la sesión — deriva todo del historial.**

### 4.2 Condiciones exactas para que una sesión drene rings

1. **El `WorkoutLog` debe existir en `history`** (persistió vía `finalizeWorkout`/`addWorkoutLog`).
2. **Fecha dentro de ventana:** `getPerMuscleBatteries` filtra `logDateMs(log) >= now-30d` (`AugeRecoveryEngine.kt:1076`); estrés agudo ~10 días (`:462-467, 690-692`); capacidad de trabajo usa 28–35 días con fade (`:344-348, 356-358`). `logDateMs` parsea OffsetDateTime/Instant/LocalDate con fallback (`domain/auge/AugeUtils.kt:50-62`); el log guarda `date = Instant.now().toString()` → parse OK. **No hay requisito de «jour» del programa ni de `scheduledDate`** (en la sesión forense fue `null`, anunciado como «No existe una próxima sesión programada fiable.»).
3. **Sets efectivos por ejercicio** (`AugeFatigueEngine.kt:225-232 isSetEffective`): `!skipped && !isWarmup && (reps>0 || timeSeconds>0 || weight>0)` y **RPE efectivo ≥ 6.0**. El RPE default es 7.0 cuando el set no trae intensidad explícita (`:200-209`), así que sets de voz sin RPE cuentan. **No se requiere carga real > 0** (alcanza `reps>0`, p.ej. peso corporal), pero la MAGNITUD del drenaje escala con peso/RPE/técnicas (`calculateRpeMultiplier :234-243`, `calculateSetBatteryDrain` usado en `AugeRecoveryEngine.kt:372-384`).
4. **Músculos resolubles:** `resolveDbInfo` + `involvedMusclesFor` por ejercicio (`AugeRecoveryEngine.kt:360-368`); el log guarda `effectiveMuscles` (`WorkoutFinishController.kt:106`) con fallback a catálogo.

5. **NO se requiere:** `PostSessionFeedback` (solo AJUSTA baterías vía `feedbacks`, `AugeViewModel.kt:184,202,213`; `auge_feedback` la escribe el cuestionario +24h, no el finish), ni completitud de la sesión, ni `sessionIntensity` (label cosmético del sheet/TTS: `SessionIntensityEngine.kt:35-89`; con default RPE 7.0 el label sale «Intensa» aun con datos pobres — filter de sets `it.weight > 0 && it.reps > 0` `:51`), ni `WorkoutContextPerformanceEntity`.

### 4.3 Por qué la sesión intensa de hoy no drenó (veredicto con evidencia)

**La inyección NO se cortó en finalize: se cortó aguas arriba, en la captura.** Forense (`kpkn-voice-20260807-153838-cac8314a.jsonl`):
- El finish SÍ ocurrió: línea 647 `workout_completed` 17:27:11.671Z (emitido en `WorkoutViewModel.kt:1340 prepareVoiceDiagnosticExport`, invocado desde `WorkoutFinishController.kt:351`, **después** de `finalizeWorkout` `:292`) y línea 648 `export_started` 17:27:17.311Z (cierre del logger, `WorkoutVoiceDiagnosticLogger.kt:136`). Por tanto `workoutLogDao().insert` corrió → el log existe → `_history` se actualizó → `recompute` disparó.
- Pero el preview post-sesión (mismo cómputo que el recompute real: `AugeViewModel.computePostSessionPreview :381-436` → `AugeRecoveryEngine.previewPostSessionBatteries(baseHistory=history, previewLog)`) anunció TRES veces: **«Tu sesión fue intensa. No registraste molestias. Tu RING muscular quedó en 99 por ciento. El músculo con menor batería es Pectorales. Tu RING de energía quedó en 86 por ciento y tu columna en 95/100…»** (líneas 639, 640, 646).
- Causa de magnitud: el `WorkoutLog` quedó con **solo 9 sets** (15:42→17:02Z) porque gran parte del trabajo hablado no entró (§2: 11 `unknown_command_logged`, 5 clarificaciones, y la pérdida de respuestas de carga del issue 1). Con pocos sets y pocos músculos involucrados, el drenaje por músculo (Pectorales el menor) se diluye en el promedio global muscular → 99%. Energía 86 / columna 95 son coherentes con un log chico. Los ejercicios con cero sets se filtran en `:124` y no aportan nada; sin guard de vacío (§3.3.5).

---

## 5. Pregunta (4): LOW_MEMORY 14:48:28Z — ¿la sesión de voz pudo perderse por kill sin persistir?

**Recovery logs verificados** (`.opencode/tmp-voice/kpkn-voice-recovery-*.jsonl`):
- `1786038912337`: `application_exit JAVA_CRASH` 2026-08-06T17:55:12.337Z (reasonCode 4, rss 188MB).
- `1786038947261`: JAVA_CRASH ×2 (2026-08-06T17:55:43.905Z y 17:55:47.261Z).
- `1786061145859`: `LOW_MEMORY` 2026-08-07T00:05:45.859Z, `importance=400`.
- `1786114108436`: **`LOW_MEMORY` 2026-08-07T14:48:28.436Z, `importance=400`, rss=72096KiB**.

**Cronología UTC del 07-08:** kill LOW_MEMORY 14:48:28Z → pruebas de voz 15:19:49Z → **sesión real inicia 15:38:38Z** → sets 15:42→17:02Z → finalize 17:27:11Z. La sesión forenseable ocurrió DESPUÉS del kill y sobrevivió (finalizó en proceso vivo).

**Pero el patrón confirma el riesgo estructural:** `importance=400` = proceso en background matado por LMK **sin callbacks** (no `onDestroy` ordenado; el `onCleared` del VM — que de todos modos no guarda, §3.1 — ni corre). Si una sesión queda abierta (sheet de finish sin confirmar, o sin siquiera finish) y el SO mata el proceso — muy probable con Vosk+TTS+Compose en RAM — entonces: las series confirmadas sobreviven en `ongoing_workout` (gracias a persistencia por serie, `WorkoutSetRecorder.kt:424`), y al reabrir se puede reanudar (`WorkoutSessionHydrator.kt:108 resumedState = repository.ongoingWorkout.value`; Home expone `ongoingWorkout` `HomeViewModel.kt:60`), **pero nada finaliza automáticamente esa sesión**: sin re-apertura manual + confirm, jamás llega a `history` → rings en 0 drenaje para ese trabajo. Combinado con §3.3.1/§3.3.2 (usuario que cree que ya guardó porque el TTS se lo dijo, o que jamás logra la keyword correcta), el escenario «entrené y desapareció» es completamente alcanzable.

### 5.1 Entidades secundarias (ítem 5 de la task)

- `WorkoutContextPerformanceEntity` (tabla `workout_context_performance`, `data/db/Entities.kt:102-105`): la escribe `PerformanceRangeStore.persistFinishedSessionPerformance` post-finalize (`WorkoutFinishController.kt:319-328`) en launch separado, dentro de try/catch que **solo hace `error.printStackTrace()` (`:326-328`)**: pérdida totalmente silenciosa si falla.
- `PostSessionFeedbackEntity` (tabla `auge_feedback`, `Entities.kt:153-156`): **NO se escribe en finish**. Solo vía `AugeViewModel.savePostSessionFeedback` (`AugeViewModel.kt:344`), desde el cuestionario `PendingQuestionnaire` agendado +24h en `WorkoutFinishController.kt:306-317` y respondido en `HomeRingsSection.kt:281-285`. Los rings NO dependen de ella.
- `AugeAdaptiveCacheEntity`: leída en finish para stress (`WorkoutFinishController.kt:163-165`); escrita solo si el usuario editó baterías manualmente (`AugeViewModel.learnFromManualAdjustment :519` / `applyManualBatteries :443`, invocado en `WorkoutScreen.kt:940-960` onComplete cuando hubo edición de rings en la sheet).

---

## 6. Propuestas de fix (diagnóstico → acción; nada editado aún)

### P0 — cortan inyección o mienten estado

1. **Alinear keyword de cierre con el prompt TTS.** Agregar `«sesion terminada»`, `«terminar sesion»`, `«guardar»`, `«listo»`, `«confirmar»` a `saveKeywords` en `WorkoutVoiceCommandParser.kt:801` (o unificar con `FINISH_SESSION_KEYWORDS :69-72`); alternativa mínima: cambiar el prompt en `WorkoutViewModel.kt:1718` a «di guardar y terminar». Añadir fallback hablado: si `LogFinalFeedback` llega con todo null y `isSaveAction=false`, responder «Decí guardar y terminar para cerrar la sesión» en vez de silencio (`WorkoutVoiceCommandHandler.kt:878`).

2. **Desacoplar el save de Compose.** En `handleVoiceLogFinalFeedback(isSaveAction=true)` (`WorkoutVoiceCommandHandler.kt:830-838`) invocar directamente un port `finalizeVoiceSession()` → `viewModel.finishWorkout(...)` con los `voiceFinal*` recogidos (o defaults del preview), en vez de solo setear `voiceFinalConfirmTriggered` y depender de `WorkoutFinishHost.kt:854 LaunchedEffect`. El confirm visual queda como fallback. Esto elimina la ventana TTS→Compose→kill y libera al usuario de tocar la pantalla.
3. **TTS post-write.** Mover `speakSessionSaved()` al `onComplete` real de finish (post-`finalizeWorkout`), para no anunciar «guardado» antes del insert; opcionalmente persistir primero el log con feedback vacío (finalize es idempotente por `log.id`, `ProgramRepository.kt:289-301`) y re-escribir con el feedback después.

### P1 — pérdida silenciosa / robustez

4. **Auto-finalize defensivo de sesiones huérfanas.** Al boot (o al entrar a Home): si `ongoingWorkout` tiene `completedSets` no vacíos y antigüedad > N horas, ofrecer «Recuperar sesión» ejecutando `finalizeWorkout` con el mismo `logId` derivable del snapshot (`WorkoutFinishController.kt:153-158`) o un diálogo reanudar/descartar explícito — hoy depende 100% de que el usuario vuelva a entrar al workout.
5. **Guard de sesión vacía.** Tras el filter de `WorkoutFinishController.kt:124`, si `completedExercises.isEmpty()` no guardar log hueco: avisar por voz/UI («no registré series; ¿guardar igual?»). Un log vacío drena 0 y tapa el problema.
6. **Robustecer escrituras secundarias.** Reemplazar el `printStackTrace` de `WorkoutFinishController.kt:326-328` por log estructurado + retry (o WorkManager) para `WorkoutContextPerformanceEntity`.

### P2 — visibilidad / prevención

7. **Conteo real en el cierre.** Incluir en el resumen hablado/sheet el total persistido («registré 9 series en 4 ejercicios») para que el usuario detecte sub-registro antes de confirmar — mitiga el impacto del issue 1 sobre AUGE.
8. **Reducir huella de memoria en sesión de voz** (Vosk+TTS+Compose) para bajar la probabilidad de `LOW_MEMORY`: p.ej. liberar el modelo Vosk al abrir la finish sheet, o `onTrimMemory` → flush + degradación. La persistencia por serie ya existe; lo que falta es proteger el tramo final.

---

## 7. Apéndice forense — conteo de eventos por archivo JSONL

Conteo (excl. `voice_capture_health`) de `.opencode/tmp-voice/`, parseado con Python:

- `kpkn-voice-20260807-151949-07709bc8.jsonl` (4.6KB): enable/disable voz, diálogo de modo; sin comandos.
- `kpkn-voice-20260807-151949-b52a46d1.jsonl` (8.6KB): `voice_phase`×13, `voice_stop_requested`×2, `diagnostic_closed`×1. Sin sets.
- **`kpkn-voice-20260807-153838-51555bda.jsonl` (571KB):** 1306 líneas — 1145 `voice_capture_health`, 74 `voice_phase`, 51 `audio_route_request`, 7 `vosk_empty_final_partial_used`, 2 `voice_stop_requested`, 1 `diagnostic_closed`. **CERO `set_persistence_*`, CERO finish/save.** Es el log del proceso de captura (audio), no del controlador.
- **`kpkn-voice-20260807-153838-cac8314a.jsonl` (162KB, LA sesión):** 648 líneas — `pipeline_stage_changed`×193, `voice_phase`×232, `asr_final`×34, `command_parsed`×26, `unknown_command_logged`×11, `guided_clarification_asked`×5 / `_resolved`×4, `voice_interpretation_features`×9, **`set_persistence_started`×9, `set_persistence_succeeded`×9, rechazados 0**, `feedback_prompt_shown`×7, `confirmation_rearm_requested`×9, `voice_low_signal_alert`×1, `transcript_corrected`×1, **`session_summary_announced`×3 (líneas 639/640/646), `workout_completed`×1 (647), `export_started`×1 (648)**.
- `kpkn-voice-20260807-171512-31f993c7.jsonl` (81KB): 23 `voice_phase`, rutas de audio; sin comandos (reapertura posterior).

**Cola del archivo cac8314a (timestamps Z):** TTS summary 17:26:40.417 / 17:26:46.252 / 17:27:10.398 → `workout_completed` 17:27:11.671 → `export_started` 17:27:17.311 (`diagnostic_closed` ausente en este archivo — el cierre fue por export/auto-save). Los 3 anuncios repetidos son re-disparos del `LaunchedEffect` del sheet (`WorkoutFinishHost.kt:189-211`, keys incluyen `derivedMuscularFinal`/`spinalFinal`, que varió 95→100 entre anuncios) — consistente con sheet re-compuesta/re-abierta antes del confirm final.

### Cadena de comandos del cierre (referencia rápida)

«terminar sesión» → `FinishSession` (`WorkoutVoiceCommandParser.kt:400`) → `handleFinishRequest` (`WorkoutVoiceCommandHandler.kt:582`) → `finishUpToCurrentPoint` (`WorkoutViewModel.kt:2266`) → `openFinishSheet` (`:2298` / `:1666`) → [sheet compuesta; la voz rutea todo a `parseFinalFeedbackCommand`, `WorkoutVoiceController.kt:1562`] → frase de save → `LogFinalFeedback(isSaveAction=true)` → flag `voiceFinalConfirmTriggered` (`WorkoutVoiceCommandHandler.kt:834`) → `LaunchedEffect` (`WorkoutFinishHost.kt:854`) → `onConfirm` → `finishWorkout` (`WorkoutScreen.kt:935`) → `WorkoutFinishController.finish` (`WorkoutViewModel.kt:2738`) → `finalizeWorkout` (`WorkoutFinishController.kt:292` → `ProgramRepository.kt:286`) → Room insert + `_history` update (`ProgramRepository.kt:372,382`) → `AugeViewModel.recompute` (`AugeViewModel.kt:169`) → rings (`HomeRingsSection.kt:93-95`).

**Veredicto final:** la sesión de hoy se guardó; los rings no drenaron porque solo 9 series efectivas entraron al log (captura de voz con pérdidas), no por una falla de finalize. Las fallas estructurales P0 (keyword de cierre que no parsea, TTS «guardado» anterior al write, save acoplado a Compose, sin auto-finalize tras kill LOW_MEMORY/JAVA_CRASH) hacen que otras sesiones de voz sí puedan terminar sin persistir nada.

---

**Fin del informe — task_0004 (persist-dev).**














