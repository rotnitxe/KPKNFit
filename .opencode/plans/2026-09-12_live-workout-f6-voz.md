---
flags: [voice]
---

# F6 — Voz en la sesión en vivo

Depende de D4. No reabrir guards 2026-08-07 (doble save, TTS «guardado» post-write, sesión vacía). Cierra: **VOZ-01, VOZ-02, VOZ-03, VOZ-04, VOZ-05, VOZ-06, VOZ-07, VOZ-08, VOZ-09, VOZ-10, VOZ-11, VOZ-12** (VOZ-13/14/15 P3).

## Objetivo

Permiso y mic se comportan; AUTO no escribe en otro ejercicio; Vosk fallido no entra en bucle; «terminar» no es un cierre accidental; libras y gramática de nombres cumplen lo que el parser promete.

## Rutas

| Ítem | Archivos |
|------|----------|
| Permiso | `WorkoutVoiceCommandHandler.kt:379-392`, `WorkoutContinuousVoiceEngine.kt:591-618`, `WorkoutScreen.kt:225-234` |
| Pause/resume D4 | `WorkoutVoiceCommandHandler.kt:445-460`, `WorkoutViewModel.kt:1877-1878`, `WorkoutScreen.kt:360` |
| AUTO target | `WorkoutVoiceController.kt:2808-2818, 3149`, `WorkoutVoiceCommandHandler.kt:1339-1508`, `WorkoutSetRecorder` |
| Vosk | `WorkoutVoskModelStore.kt`, `WorkoutContinuousVoiceEngine.kt:692-706, 948-951` |
| Cierre | `WorkoutVoiceCommandParser.kt:77-80,533`, `WorkoutVoiceCommandHandler.kt:538,755-763` |
| TTS | `WorkoutTtsManager.kt`, `WorkoutVoiceController.speakFeedbackUpdated` `:615` |
| Parser lb | `WorkoutVoiceInput.kt:704` WEIGHT_KEYWORDS |
| Gramática | `WorkoutVoiceController.kt:3544-3568`, `toVoiceCommandContext()`, `WorkoutVoiceGrammarBuilder.kt` |
| Canal actor | `WorkoutContinuousVoiceEngine.kt:70-73`, `WorkoutVoiceForegroundService.kt:112-116` |
| AIDL | `WorkoutRemoteVoiceEngineClient.kt:192-213` unlinkToDeath |
| Relator | `WorkoutLiveRelatorCatalog.kt` `.first()` residuales |

## Impacto

### VOZ-01 — Permiso a mitad de sesión

Tras fallo de `AudioRecord` / `SecurityException`: reconsultar `RECORD_AUDIO`. Si falta: `disableVoice()`, stage `ERROR_RECOVERY`, TTS/UI “Concede el micrófono”, **sin** `scheduleAfterFailure`. Launcher `!micOk`: mismo mensaje (no no-op).

### VOZ-02 — D4 pause/resume

- `onVoiceHostPaused`: **sigue no-op de captura** (manos libres). Corregir el KDoc del VM (`Stops continuous listening...` es falso).
- `onVoiceHostResumed`: si `voiceSessionEnabled`:
  1. Revalidar permiso (VOZ-01).
  2. Si controller disabled → `enableVoice()`.
  3. Si stage `ERROR_RECOVERY` **o** `MIC_BUSY` → `enable()`/`resumeListening()`.
- No llamar `requestStopCapture` en pause.

### VOZ-03 — Target al parsear

En AUTO **no** `clearPendingConfirmation()` antes de persistir. Capturar `(exerciseId, setIdx, side)` en la interpretación. `handleVoiceRegisterSet` usa `confirmationTarget` también en AUTO (hoy solo ASK). `recordSetV2(..., expectedExerciseId = target)`.

Si el ejercicio ya no existe: VOZ-11 TTS “ese ejercicio ya no está”, cancelar confirmación, no `return` silencioso.

### VOZ-04 — ModelFailed

Tras `EngineCommand.ModelFailed`: un intento nativo si `isOnDeviceRecognitionAvailable`; si no, TTS “modelo de voz no disponible” y apagar. No 5× `resumeListening` del mismo árbol roto.

### VOZ-05 — Vocabulario de cierre

- `FINISH_SESSION_KEYWORDS` **no** incluye «terminar»/«finalizar» sueltos (chocan con cardio/aproximación).
- Frases de cierre real: «sesión terminada», «guardar sesión», «guardar entrenamiento».
- `handleFinishRequest` con `pending.isEmpty()`: puede abrir sheet (UI) **o** si `showFinishSheet` ya true → `finalizeVoiceSession()`. Documentar flujo en dos pasos en el speak: “Abro el resumen. Di sesión terminada para guardar.”
- No llamar `finalizeVoiceSession` desde «terminar» a mitad.

### VOZ-06 — Denegación inicial

`WorkoutScreen` launcher `else`: toast/dock + `bluetoothAdvisory` si aplica. Diálogo de modo (F7) tiene Cancelar; si cancela, no `enableVoice`.

### VOZ-07 — Verbosidad

Acks de comando: `CRITICAL` (se oyen en SILENT solo si son errores). Status cardio «Cardio en curso…»: `speakAnnouncement` (respeta SILENT). Dejar `speakFeedbackUpdated` para errores.

### VOZ-08 — TTS init

- Segundo `setLanguage` (es-ES) comprobar resultado; si ambos fallan → `onError`, no `isInitialized=true`.
- Cola de utterances hasta `onReady` (no disparar `onComplete` vacío).
- Duck null: log; TTS igual (gym con música).

### VOZ-09 — Libras

`WEIGHT_KEYWORDS` + `libra(s)` / `pounds` / `lbs`. Si settings `LBS`, el número dictado se convierte a kg canónico (`n / 2.2046`). Si settings KG y dice libras, convertir igual. Ambigüedad sin unidad: kg (producto).

### VOZ-10 — Gramática de la sesión

`toVoiceCommandContext()` incluye nombres/alias de `sessionExercisesProvider` + `voiceExerciseAliases`, tope de tamaño (p. ej. 24). «Ir a press banca» deja de ser sordo.

### VOZ-12 — Canal lifecycle

`Channel` DROP_OLDEST 32 se queda para RMS/grammar. **Stop/Pause/Enable** por canal no-drop o `trySend` + retry. `pauseAndAwait` del FGS: no `runBlocking` 1.5 s en el Stub; `withTimeout` en IO.

### P3 si cabe

- `unlinkToDeath` simétrico antes de `unbindService`.
- Relator helpers: `firstOrNull().orEmpty()`.
- Diagnóstico: no reintroducir SAF en vivo.

## Pruebas

| Test | Qué |
|------|-----|
| `VoiceHostResumeTest` | MIC_BUSY + onResume → enable; pause no disable |
| `VoiceAutoConfirmTargetTest` | AUTO persiste el id dictado aunque `currentExerciseIdx` cambie |
| `VoiceFinishKeywordsTest` | «terminar» ≠ FinishSession; «sesión terminada» sí con sheet |
| `VoiceWeightPoundsTest` | 180 libras → ~81.6 kg |
| `VoiceModelFailedPolicyTest` | ModelFailed no agenda 5 resumes (fake engine) |
| Existentes | ConfirmDedupe, HandsFree, parsers, `WorkoutVoiceInputTest` |

No romper `WorkoutVoiceCommandHandler` save guards (test de labels no cubre save; añadir aserción `isFinishingWorkout` doble).

```bash
./gradlew --no-daemon --console=plain testBaseDebugUnitTest --tests 'com.example.kpkn.services.workout.*' --tests '*WorkoutVoice*'
```

## Riesgos

- Tocar `FINISH_SESSION_KEYWORDS` puede dejar de reconocer frases que la gente ya usa. Speak de confirmación debe enseñar «sesión terminada».
- Gramática con todos los nombres: Vosk limitado; el tope 24 + alias es obligatorio.
- AUTO target: no reintroducir doble persist (gate `recordingGate` + `confirmedOrCancelled`).

## Fuera de alcance

iOS, nutrición por voz, assets Vosk, instrumented FGS en dispositivo, eco TTS×hipótesis (SOSPECHA 2026-08).
