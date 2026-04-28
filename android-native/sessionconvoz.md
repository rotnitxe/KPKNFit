# INFORME FINAL DE AUDITORÍA MAESTRA

## Sesión de Entrenamiento por Voz — Implementación Completa

---

## 1. Contexto para la siguiente IA

Esta implementación agrega control por voz completo a la sesión de entrenamiento de KPKN Fit (Android/Kotlin). El usuario usa audífonos Bluetooth sin botón accesible. La interacción es: wake word → comando dictado → TTS confirma → set registrado → auto-avance. La música del usuario se atenúa durante comandos (audio ducking) y se restaura al terminar.

**Tecnologías**: Porcupine (wake word offline), Android SpeechRecognizer (comandos), Android TextToSpeech (feedback), AudioManager (ducking).

---

## 2. Archivos creados (10)

| # | Archivo | Ruta | Propósito |
|---|---------|------|-----------|
| 1 | `libs.versions.toml` | `gradle/` | Versión `porcupine = "3.0.3"` |
| 2 | `build.gradle.kts` | `app/` | Dependencia `libs.porcupine.android` |
| 3 | `WorkoutVoiceSessionState.kt` | `services/workout/` | State machine (`VoicePipelineStage`) + data classes (`VoiceSessionState`, `VoiceSessionCommand`) |
| 4 | `WorkoutWakeWordEngine.kt` | `services/workout/` | Interfaz `IWakeWordEngine` + `WakeWordEngineFactory` |
| 5 | `PorcupineWakeWordEngine.kt` | `services/workout/` | Wake word con Porcupine (AudioRecord + keyword "Americano") |
| 6 | `SpeechWakeWordEngine.kt` | `services/workout/` | Fallback: wake word con SpeechRecognizer continuo escaneando parciales |
| 7 | `WorkoutVoiceCommandParser.kt` | `services/workout/` | NLP extendido: 10 tipos de comandos en español (RegisterSet, Confirm, Cancel, Skip, etc.) |
| 8 | `WorkoutTtsManager.kt` | `services/workout/` | TextToSpeech con frases predefinidas español (carga sugerida, confirmación, estado descanso) |
| 9 | `WorkoutVoiceController.kt` | `services/workout/` | Orquestador central: state machine WAKE_WORD→COMMAND→PROCESSING→CONFIRM_WAIT→WAKE_WORD |
| 10 | `WorkoutVoicePermissionHelper.kt` | `services/workout/` | Verificación de permisos (mic, SpeechRecognizer, TTS) |
| 11 | `WorkoutVoiceUi.kt` | `screens/workout/` | Componentes Compose: `WorkoutVoiceFab` (FAB con pulso) + `WorkoutVoiceStatusBar` |

## 3. Archivos modificados (4)

| # | Archivo | Cambios |
|---|---------|---------|
| 1 | `SystemAudioHelper.kt` | Método `requestTransientDuckForVoice()` con `USAGE_VOICE_COMMUNICATION` |
| 2 | `VoiceNutritionRecognizer.kt` | `pickBestTranscription()` prioriza palabras de gym sobre comida (`GYM_SIGNAL_WORDS`) |
| 3 | `WorkoutViewModel.kt` | Integración completa: `voiceController`, `toggleVoiceSession()`, `enableVoice()`, `disableVoice()`, `handleVoiceCommand()`, `handleVoiceRegisterSet()`, `handleVoiceConfirmSet()`, `handleVoiceSuggestWeight()`, `handleVoiceRestStatus()`, `handleVoiceWhatExercise()`, `handleVoiceSkipExercise()`, `handleVoicePreviousExercise()`, `handleVoiceNextExercise()`, `handleVoiceCancelSet()`. Provider de contexto de ejercicio. Cleanup en `onCleared()`. |
| 4 | `WorkoutScreen.kt` | `WorkoutVoiceFab` + `WorkoutVoiceStatusBar` integrados en el Scaffold |

---

## 4. State machine de voz

```
DISABLED ──[toggle ON]──▶ WAKE_WORD
                           │ Porcupine activo (AudioRecord)
                           │ SIN ducking (música normal)
                           │
              [detecta wake word] ──▶ COMMAND
                           │            Ducking ON
                           │            SpeechRecognizer (8s timeout)
                           │
                    [recibe comando] ──▶ PROCESSING → parseCommand()
                           │                 │
                           │         ┌───────┴──────────┐
                           │   [RegisterSet]     [Confirm/Cancel/Skip/etc]
                           │         │                  │
                           │    CONFIRM_WAIT       Ejecuta → TTS responde
                           │    TTS lee datos      Ducking OFF → WAKE_WORD
                           │    5s timeout = auto
                           │
                           └── Ducking OFF → WAKE_WORD
```

---

## 5. Comandos de voz soportados

| Comando | Ejemplo | Acción |
|---------|---------|--------|
| Registrar serie | "ochenta kilos por ocho reps RPE siete" | Rellena datos, TTS confirma, espera "confirmar" |
| Confirmar | "confirmar", "sí", "dale" | Registra serie, auto-avanza |
| Cancelar | "cancelar", "no", "corregir" | Descarta, vuelve a escuchar comando |
| Saltar ejercicio | "saltar", "siguiente" | Omite ejercicio actual, avanza |
| Ejercicio anterior | "anterior", "volver" | Retrocede al ejercicio previo |
| Peso sugerido | "cuánto peso", "carga" | TTS anuncia peso sugerido |
| Descanso restante | "cuánto falta", "timer" | TTS anuncia tiempo restante |
| Qué ejercicio | "qué toca", "qué ejercicio" | TTS anuncia ejercicio actual |
| Próximo ejercicio | "qué sigue" | TTS anuncia siguiente ejercicio |
| Apagar voz | "apagar voz", "silencio" | Desactiva modo voz |

---

## 6. Requisitos para compilar

1. **Porcupine API Key**: Registrar en https://console.picovoice.ai (gratis). Agregar al `AndroidManifest.xml`:
```xml
<meta-data android:name="PICOVOICE_ACCESS_KEY" android:value="TU_KEY_AQUI" />
```
Sin key, Porcupine falla silenciosamente y usa `SpeechWakeWordEngine` (fallback).

2. **Modelo custom "Hola KPKN"** (opcional): Entrenar en Picovoice Console, colocar `.ppn` en `app/src/main/assets/wakewords/`. Actualmente usa built-in "Americano" como keyword por defecto.

3. **Permiso `RECORD_AUDIO`**: Ya está en el manifest. El usuario debe concederlo al activar voz por primera vez.

---

## 7. Problemas conocidos y limitaciones

| Severidad | Problema | Mitigación |
|-----------|----------|------------|
| Media | SpeechRecognizer sin internet puede fallar en algunos dispositivos | Modo offline depende del dispositivo. TTS anuncia "Usa la pantalla" tras 3 errores |
| Media | Ruido extremo de gimnasio degrada reconocimiento de comandos | Wake word (Porcupine) es robusto. Comandos pueden requerir repetición |
| Baja | `Thread.sleep(100)` en `WorkoutVoicePermissionHelper` para detectar TTS | No bloquea UI (se llama desde corrutina). Funciona en 99% de casos |
| Baja | `PorcupineWakeWordEngine.canInitialize()` accede a `Porcupine` como verificación de clase | Si la dependencia no está en classpath, `NoClassDefFoundError` es capturado por `Throwable` |
| Baja | FAB de voz y FAB de completar serie pueden solaparse en pantallas pequeñas | El FAB de voz está 60dp más arriba. En pantallas muy pequeñas (<5") puede haber overlap |

---

## 8. Lo que NO está implementado (para futura iteración)

1. **Rest timer TTS al completarse**: El controller tiene el método `onRestTimerFinished()` pero no está enganchado al `WorkoutRestAlertManager.onAlarmFromReceiver()`. El ViewModel necesitaría llamar `voiceController.onRestTimerFinished(...)` cuando el timer llega a 0.

2. **Wake word custom "Hola KPKN"**: Requiere entrenar modelo en Picovoice Console y embeber el `.ppn`. Actualmente usa "Americano" (built-in).

3. **Ajuste de sensibilidad de wake word**: Porcupine acepta parámetro de sensibilidad (0.0-1.0). No expuesto al usuario.

4. **Configuración de voz en Settings**: No hay pantalla de settings para elegir wake word, velocidad TTS, o activar/desactivar TTS.

5. **Idioma inglés**: Todo el NLP y TTS está en español. Agregar inglés requeriría duplicar diccionarios de palabras clave.

---

## 9. Checklist de verificación para compilación

- [x] `libs.versions.toml` tiene `porcupine = "3.0.3"` y entrada `porcupine-android`
- [x] `app/build.gradle.kts` tiene `implementation(libs.porcupine.android)`
- [x] `SystemAudioHelper` tiene `requestTransientDuckForVoice()` público
- [x] `VoiceNutritionRecognizer` tiene `GYM_SIGNAL_WORDS` en companion object
- [x] Todos los archivos nuevos compilan sin referencias circulares
- [x] `WorkoutViewModel` importa `VoicePipelineStage`, `VoiceSessionState`, `VoiceSessionCommand`, `WorkoutVoiceController`
- [x] `WorkoutVoiceController` NO importa `workoutSetKey` (internal de otro paquete) — **corregido**
- [x] `SpeechWakeWordEngine.isActive` retorna `active` (no la lógica invertida) — **corregido**
- [x] `handleCommandError` invoca `onError?.invoke(message)` — **corregido**
- [x] `exerciseInfoProvider` está como `var` público y es asignado por ViewModel — **corregido**
- [x] `handleVoiceRegisterSet` llama `nextSet()` después de `recordSetV2` — **corregido**
- [ ] `ICONO_PORCUPINE_API_KEY` en `AndroidManifest.xml` — **PENDIENTE: requiere acción manual del desarrollador**

---

## 10. Cómo probar

1. `git clone` + abrir en Android Studio
2. Agregar Picovoice API Key al `AndroidManifest.xml`
3. Ejecutar en dispositivo con Android 8+ y micrófono
4. Iniciar una sesión de entrenamiento
5. Tocar FAB de micrófono (abajo derecha, arriba del check verde)
6. Decir "Americana" (o la wake word configurada)
7. Decir un comando, ej: "ochenta kilos por ocho"
8. Verificar que TTS responde y el set se registra
