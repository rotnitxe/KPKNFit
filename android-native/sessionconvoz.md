# Sesión de Entrenamiento por Voz — Documentación actual

> **Estado:** post-Porcupine (2026). Sin Picovoice, sin wake word, sin API keys.
> Documento hermano más corto: [`Informe-Voz-Sesion.md`](Informe-Voz-Sesion.md).

---

## 1. Contexto

Control por voz continuo en la sesión live de KPKN Fit (Android/Kotlin). El usuario activa el mic con el miniFAB, dicta series (peso × reps, RPE/RIR, lado unilateral), confirma con “sí/no”, y puede seguir dictando con la app en background gracias a un FGS tipo `microphone`.

**Stack:** `SpeechRecognizer` nativo + `TextToSpeech` + `AudioManager` (ducking). Cero dependencias externas de voz.

---

## 2. Arquitectura

```
WorkoutCommandDock (miniFAB mic)
  → WorkoutViewModel.toggleVoiceSession / enableVoice
    → WorkoutVoiceCommandHandler
      → WorkoutVoiceController
          ├─ WorkoutContinuousVoiceEngine  (ciclos SpeechRecognizer)
          ├─ WorkoutTtsManager             (TTS es-CL / es-ES)
          ├─ SystemAudioHelper             (ducking)
          └─ WorkoutVoiceCommandParser     (NLP ES)
      → WorkoutVoiceForegroundService      (FGS microphone mientras enabled)
```

### State machine

```
DISABLED ──[miniFAB ON]──▶ LISTENING ──[frase]──▶ PROCESSING ──[parse]──▶ …
                              ▲                                            │
                              │                                     RegisterSet → TTS_SPEAKING
                              │                                            │
                              │                                     CONFIRM_WAIT (sí/no)
                              │                                     timeout 5s = CANCELA
                              │                                            │
                              └──────────── TTS_SPEAKING ←─────────────────┘
                                              (serie registrada / cancelada)

Errores del engine → ERROR_RECOVERY → retry acotado → LISTENING
```

**Importante:** `ttsManager.onReady` **no** puede forzar `DISABLED` si la sesión ya está activa (`sessionWanted` + `WorkoutVoiceSessionGate`). Eso evitaba el bug “modo voz sordo”.

---

## 3. Archivos clave (rutas bajo `app/src/main/java/com/example/kpkn/`)

| Archivo | Rol |
|---------|-----|
| `services/workout/WorkoutVoiceController.kt` | Orquestador + `sessionWanted` |
| `services/workout/WorkoutVoiceSessionGate.kt` | Reglas puras de transición (testables) |
| `services/workout/WorkoutContinuousVoiceEngine.kt` | Mic continuo, partials, prefer offline |
| `services/workout/WorkoutVoiceCommandParser.kt` | Comandos ES |
| `services/workout/WorkoutVoiceSessionState.kt` | Stages + `VoiceSessionCommand` |
| `services/workout/WorkoutTtsManager.kt` | TTS |
| `services/workout/WorkoutVoiceForegroundService.kt` | FGS `microphone` + notificación |
| `services/workout/WorkoutVoicePermissionHelper.kt` | Capacidad mic / recognizer / TTS |
| `screens/workout/WorkoutVoiceCommandHandler.kt` | Toggle, enable, comandos → VM ports |
| `screens/workout/components/WorkoutCommandDock.kt` | MiniFAB 48dp + FAB check + chip de estado |
| `screens/workout/WorkoutScreen.kt` | Permiso `RECORD_AUDIO` + Lifecycle pause/resume |

### Eliminado (no volver a documentar como vigente)

- Porcupine / `PorcupineWakeWordEngine` / `SpeechWakeWordEngine` / `WorkoutWakeWordEngine`
- `WorkoutVoiceUi.kt` (`WorkoutVoiceFab` / `WorkoutVoiceStatusBar` huérfanos)
- Auto-confirmación por timeout (ahora el timeout **cancela**)

---

## 4. UI

- **MiniFAB mic (48dp)** arriba-izquierda del **FAB guardar (56dp)** en `WorkoutCommandDock`.
- Chip de estado visible mientras `voiceSessionEnabled` (escuchando, confirmación, error, etc.).
- `contentDescription`: “Activar/Desactivar control por voz”.

---

## 5. Comandos soportados

| Comando | Ejemplo | Acción |
|---------|---------|--------|
| Registrar serie | “80 por 8 RPE 7”, “ochenta kilos por ocho derecha” | Parse → TTS → confirmar sí/no |
| Confirmar / Cancelar | “sí”, “dale” / “no”, “cancelar” | Registra o descarta |
| Serie extra | “añade una serie”, “serie extra” | Añade set live → “¿solo sesión o para siempre?” |
| Persistencia AddSet | “solo esta sesión” / “para siempre” | `SESSION_ONLY` o `PERMANENT` |
| Saltar set / ejercicio | “saltar serie”, “saltar” | Omite |
| Anterior | “anterior”, “volver” | Retrocede |
| Peso sugerido | “cuánto peso”, “carga” | TTS |
| Descanso | “cuánto falta”, “timer” | TTS |
| Qué / próximo ejercicio | “qué toca”, “qué sigue” | TTS |
| Apagar | “apagar voz”, “silencio” | Desactiva + para FGS |
| Finalizar / cancelar sesión | “finalizar entrenamiento”, “cancelar sesión” | Fin o descarte |

Unilateral y supersets: el provider anuncia lado / ronda; el parser acepta lado en el dictado de serie.

---

## 6. Hands-free (background)

1. Permisos: `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`.
2. Al `enableVoice()` → `WorkoutVoiceForegroundService.start()` (notificación “Control por voz activo”).
3. Al `disableVoice()` / `onCleared` → stop FGS.
4. Lifecycle en `WorkoutScreen`: `ON_PAUSE` / `ON_RESUME` **no apagan** la sesión; en resume se reintenta si quedó en `ERROR_RECOVERY`.

---

## 7. Cómo probar

1. Compilar e instalar flavor `base` debug.
2. Iniciar sesión de entrenamiento.
3. Conceder micrófono (y notificaciones en Android 13+) al tocar el miniFAB.
4. Verificar chip “Escuchando…” **y** TTS “Voz activada. Puedes dictar series y comandos.”
5. Dictar: “ochenta por ocho” → TTS pide confirmación → “sí” (timeout de confirmación: **8s**, cancela si silencio).
6. (Opcional) Salir a home con voz ON → dictar otra serie → volver y verificar registro.
7. Dictar “añade una serie” → prompt por voz (sin diálogo táctil duplicado) → “solo esta sesión” o “para siempre”.

---

## 8. Tests unitarios relevantes

- `WorkoutVoiceSessionGateTest` — race TTS / enable / accept result
- `WorkoutVoiceAddSetParserTest` — AddSet + persistencia
- `WorkoutVoiceConfirmWaitParserTest` — sí/no / corrección
- `WorkoutVoiceUtteranceGuardTest` — gate TTS + timeout
- `WorkoutVoiceInputTest` — parse peso/reps/RPE/lado

```bash
cd android-native
./gradlew :app:testBaseDebugUnitTest --tests "com.example.kpkn.services.workout.WorkoutVoice*"
```

---

## 9. Limitaciones conocidas

| Severidad | Problema | Notas |
|-----------|----------|-------|
| Media | Ruido de gimnasio degrada ASR | Prefer offline + reintentos; puede requerir repetir |
| Media | OEMs pueden limitar mic en background pese al FGS | Validar en Android 14+ del dispositivo real |
| Baja | Sin wake word | Activación solo por miniFAB (o “apagar voz” por voz) |
| Baja | Dialog táctil de persistencia puede coexistir con prompt de voz al añadir serie | Ambos resuelven el mismo `PendingStructuralChange` |
| Baja | NLP / TTS solo español | Inglés no soportado |

---

## 10. Checklist de verificación (actual)

- [x] Sin dependencia Porcupine / Picovoice
- [x] MiniFAB en `WorkoutCommandDock` (no FAB flotante separado)
- [x] `sessionWanted` + gate anti-race TTS→DISABLED
- [x] Timeout CONFIRM_WAIT cancela (no auto-confirma)
- [x] FGS `microphone` + permiso en manifest
- [x] Lifecycle no desactiva voz al background
- [x] Comandos AddSet sesión/permanente
- [x] `WorkoutVoicePermissionHelper` usado en `enableVoice()`
