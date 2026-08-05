# Informe Voz Sesión — Estado actual del sistema de voz en vivo

> Documento vivo de la implementación actual. Reemplaza el informe anterior basado
> en `SpeechRecognizer` nativo, que quedó obsoleto tras la migración a Vosk.

## Arquitectura

La voz continua corre sobre **Vosk (modelo `vosk-model-small-es-0.42`, ~57,5 MB de
assets) en un proceso separado `:voice`**, aislado del proceso principal mediante
AIDL. El proceso principal nunca carga `libvosk` ni abre `AudioRecord`.

- **Motor**: `WorkoutContinuousVoiceEngine` (en `:voice`): un único actor posee
  `AudioRecord` 16 kHz + `Recognizer` Vosk con **gramáticas restringidas por stage**
  (las frases que el recognizer puede emitir), caché de recognizer = 1, recuperación
  con backoff (300/1 s/2 s → slow-probe 15 s), fallback nativo **one-shot on-device**.
- **IPC**: `WorkoutVoiceForegroundService` (tipo `microphone`, notificación
  silenciosa) + `WorkoutRemoteVoiceEngineClient` con generaciones monotónicas,
  `DeathRecipient` y heartbeat.
- **Supervisión**: `WorkoutVoiceSessionGate` (reglas puras de stage), watchdog de
  captura, y el **"fénix"** de auto-recuperación (`VoiceSessionRecoveryPolicy`).

### Modos de captura (producto)

| Modo | Micrófono | Música del usuario |
|------|-----------|--------------------|
| **Manos Libres** | Audífonos Bluetooth (ruta de comunicación, SCO) | Degrada como llamada |
| **Música** | Micrófono del teléfono (aunque esté bloqueado) | Alta calidad intacta (A2DP) |

No existe modo "pulsar para hablar": la voz está **siempre escuchando** mientras la
sesión está activa, y **el modelo nunca se descarga** durante la sesión.

### Flujo

```
DISABLED ──[activar]──▶ LISTENING ──[final ASR]──▶ PROCESSING ──[comando]──▶ TTS_SPEAKING
                          ▲                            │                        │
                          │                     [RegisterSet]            [TTS confirma]
                          │                            │                        │
                          │                            ▼                        ▼
                          │                      CONFIRM_WAIT ◀─────────────────┘
                          │                          │    │
                          │                  [sí/no]  │  [12s timeout → repregunta → cancela]
                          │                          │    │
                          │                          ▼    ▼
                          └─────────────── LISTENING (serie registrada)
```

Stages: `DISABLED, LISTENING, PROCESSING, CONFIRM_WAIT, TTS_SPEAKING, MIC_BUSY,
RECONNECTING, RECOVERING, ERROR_RECOVERY, FAILED`.

## El "fénix" (auto-recuperación)

Si el proceso `:voice` muere (LMK) o **se cuelga** (sin callbacks durante 12 s en
`LISTENING`):

1. `WorkoutRemoteVoiceEngineClient.handleBinderDeath()` marca `FAILED` (no-terminal):
   la sesión **sigue solicitada** por el usuario.
2. `WorkoutVoiceController.startPhoenixRecovery()` entra en stage `RECOVERING`
   (chip "Reconectando voz...", **sin avisos hablados** en éxito).
3. Reintentos con backoff 0/1/2/5/10/30 s (máx 6 intentos). Cada intento:
   `markRecoveryTriggered()` → `recover()` (foreground garantizado + rebind +
   `sendStart()` con la gramática/stage/modo que el cliente conserva).
4. Éxito → se re-pregunta **una vez** si se interrumpió una confirmación o
   clarificación: *"Perdón, se cortó un segundo. ¿Me repetías?"*. **Nunca
   auto-confirma.** Si se interrumpió en `CONFIRM_WAIT`, se restaura `CONFIRM_WAIT`
   (navegación, reemplazo, tag, fin o persistencia de serie extra).
5. Give-up (agotados los intentos) → mensaje "La voz no pudo recuperarse sola",
   el usuario reconecta tocando el micrófono.

## Comandos soportados (resumen)

Registrar/editar series (peso×reps, decimales de gimnasio, RPE/RIR/%RM, al fallo,
dropset, rest-pause, ROM, lado, "solo la barra", mancuernas, asistidas, tags),
confirmación sí/no, navegación (siguiente/anterior/saltar/ir a), descanso (estado,
omitir, adaptativo, ajustar tiempo), serie extra (con persistencia), carga sugerida,
reemplazo de ejercicio, superscripts, consultas ("cuánto drenaje", "qué serie voy",
"qué lado falta"), feedback de calidad/molestias, reporte de equipo y apagado.

## Calidad y robustez (2026-08)

- Confianza real: el engine lee el **word-confidence** del JSON de Vosk
  (`confidenceKnown=true`) → el gateo de auto-confirmación ya no opera a ciegas.
- Correcciones de mishearing por pares/frases; números imposibles de RIR ("doce",
  "ocho") se corrigen a "dos" tras "rir".
- Aviso único por sesión si se captura voz con nivel bajo.
- Anti-eco: pause del decoder antes de TTS, guard post-TTS 600 ms, sin promoción de
  parciales ±1 s tras TTS.

## Dependencias

- `com.alphacephei:vosk-android` (reconocimiento offline local) + `jna`.
- SDK Android: `AudioRecord`, `TextToSpeech`, `AudioManager`, AIDL.
- Sin API keys ni servicios en la nube para la sesión en vivo.
- Permisos: `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`, e implícitos de BT.

## Limitaciones conocidas

1. Ruido muy alto de gimnasio degrada el ASR (se mitiga con gramáticas restringidas
   y fallback one-shot on-device).
2. Memoria del proceso `:voice`: el modelo queda cargado toda la sesión (decisión de
   producto: disponibilidad > ahorro). El primer plano del FGS lo protege del LMK.
3. Batería: AudioRecord + decodificación continua es lo costoso esperado en sesiones
   largas (90+ min con una sola carga).
4. Sin wake word: se activa tocando el FAB (decisión de producto).
