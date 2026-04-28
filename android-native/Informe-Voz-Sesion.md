# Informe Voz Sesión — Implementación Final

## Arquitectura

La sesión de voz usa **SpeechRecognizer nativo de Android** en ciclos continuos. Sin Picovoice, sin dependencias externas, sin API keys.

### Flujo

```
DISABLED ──[FAB]──▶ LISTENING ──[detecta frase]──▶ PROCESSING ──[parse NLP]──▶ TTS_SPEAKING
                       ▲                                │                        │
                       │                          [RegisterSet]           [TTS dice resumen]
                       │                                │                        │
                       │                                ▼                        ▼
                       │                         CONFIRM_WAIT ◀─────────────────┘
                       │                            │         │
                       │                     [confirmar]  [5s timeout]
                       │                            │         │
                       │                            ▼         ▼
                       └──────────────────── TTS_SPEAKING ────┘
                                               (serie registrada)
```

### Micrófono

Un solo capturador a la vez:
- **LISTENING**: `WorkoutContinuousVoiceEngine` tiene el micrófono
- **PROCESSING / TTS_SPEAKING / CONFIRM_WAIT**: `SpeechRecognizer` pausado, ducking activo
- **CONFIRM_WAIT**: se reanuda `SpeechRecognizer` para escuchar confirmar/cancelar
- Al apagar voz / destruir ViewModel: micrófono liberado

### Ducking

- Se activa `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` con `USAGE_VOICE_COMMUNICATION` al entrar a PROCESSING
- Se libera al volver a LISTENING
- TTS usa `USAGE_ASSISTANT` que hace ducking automático adicional

---

## Archivos

### Creados (1)

| Archivo | Propósito |
|---------|-----------|
| `services/workout/WorkoutContinuousVoiceEngine.kt` | Ciclos continuos de SpeechRecognizer con es-CL, partial results, prefer offline, auto-restart |

### Modificados (4)

| Archivo | Cambios |
|---------|---------|
| `services/workout/WorkoutVoiceSessionState.kt` | `VoicePipelineStage`: eliminado `WAKE_WORD` y `COMMAND`, agregado `LISTENING`. Eliminado `wakeWordEngineActive`, `wakeWordEngineType`, `ttsMessage` |
| `services/workout/WorkoutVoiceController.kt` | Reescrito completamente. Usa `WorkoutContinuousVoiceEngine`. Sin wake word. Flujo: LISTENING→PROCESSING→TTS→CONFIRM_WAIT→LISTENING. Confirmación con 5s timeout + escucha de voz |
| `screens/workout/WorkoutVoiceUi.kt` | Actualizado para nuevos stages. FAB verde cuando LISTENING, texto "Escuchando comandos..." |
| `screens/workout/WorkoutViewModel.kt` | Eliminados callbacks `onWakeWordDetected` y `onListeningStarted`. Agregado `onError` callback |

### Eliminados (3)

| Archivo | Razón |
|---------|-------|
| `PorcupineWakeWordEngine.kt` | Dependía de Picovoice |
| `SpeechWakeWordEngine.kt` | Era fallback de Porcupine, reemplazado por engine continuo |
| `WorkoutWakeWordEngine.kt` | Interfaz + factory ya no necesaria |

---

## Comandos soportados

| Categoría | Ejemplos | Acción |
|-----------|----------|--------|
| Registrar serie | "ochenta kilos por ocho", "80 por 8 RPE 7" | Parsea → TTS confirma → espera confirmación |
| Confirmar | "sí", "confirmar", "dale" | Registra serie, auto-avanza |
| Cancelar | "no", "cancelar", "corregir" | Descarta, vuelve a escuchar |
| Saltar | "saltar", "siguiente" | Omite ejercicio |
| Anterior | "anterior", "volver" | Retrocede ejercicio |
| Peso sugerido | "cuánto peso", "carga" | TTS anuncia peso |
| Descanso | "cuánto falta", "timer" | TTS anuncia tiempo |
| Qué ejercicio | "qué toca" | TTS anuncia ejercicio actual |
| Apagar | "apagar voz", "silencio" | Desactiva voz, libera micrófono |

---

## Auto-confirmación

- Si el parse tiene `weightKg != null` Y `metricValue != null` → auto-confirma a los 5s
- Si no tiene datos válidos → auto-cancela a los 5s
- Variable `confirmedOrCancelled` previene doble registro entre timeout y confirmación manual

---

## Dependencias

- **Cero dependencias externas** para voz. Solo Android SDK:
  - `android.speech.SpeechRecognizer`
  - `android.speech.tts.TextToSpeech`
  - `android.media.AudioManager`
- **Sin API keys**, sin tokens, sin consolas externas
- **Permiso**: `RECORD_AUDIO` (ya existente en manifest)

---

## Resultado compilación

```
BUILD SUCCESSFUL in 34s
8 actionable tasks: 2 executed, 6 up-to-date
```

Cero errores. Solo warnings pre-existentes (deprecaciones de `Locale`).

---

## Limitaciones

1. **Ruido de gimnasio**: SpeechRecognizer en es-CL puede fallar con música alta + pesas. Se mitigó con `EXTRA_PREFER_OFFLINE` y reintentos automáticos.
2. **Latencia**: Ciclos de reconocimiento toman 1-3s. El usuario debe hablar claramente y esperar.
3. **Batería**: SpeechRecognizer continuo consume más que hotword dedicado. Aceptable para sesiones de 60-90 min.
4. **Sin wake word**: Para activar/desactivar se requiere tocar el FAB. No hay activación por voz.
5. **Offline**: `EXTRA_PREFER_OFFLINE` es preferencia, no garantía. Algunos dispositivos ignoran y usan red.
