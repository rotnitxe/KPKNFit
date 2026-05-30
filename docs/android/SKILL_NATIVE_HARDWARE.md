# Skill: Integración de Servicios Nativos y Hardware (Hardware Specialist)

Esta guía documenta los estándares de integración y control de APIs nativas de bajo nivel de Android (háptica, Text-To-Speech, reconocimiento de voz y alarmas del sistema) en KPKN Fit.

---

## 🔊 1. Asistente de Voz y Reconocimiento Offline (Speech APIs)
KPKN Fit integra dictado inteligente para registrar series en vivo ("Serie uno, 100 kilos, 8 repeticiones"). 

### Reglas Críticas:
1. **Verificación de Permisos**: Antes de invocar el reconocimiento de voz o audio, se debe verificar y solicitar dinámicamente el permiso de grabación `Manifest.permission.RECORD_AUDIO`.
2. **Ciclo de Vida TTS**: El motor de `TextToSpeech` debe instanciarse en el contexto de aplicación o servicio, y destruirse estrictamente llamando a `shutdown()` para evitar fugas del canal de sonido del sistema operativo.
3. **Respeto al Foco de Audio (Audio Focus)**: Durante la reproducción de voz del coach o lectura de descansos, se debe bajar el volumen de la música de fondo que el usuario esté escuchando (ducking).

### Implementación Segura de Text-To-Speech:
```kotlin
class KpknVoiceAssistant(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "ES"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
            }
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kpkn_speech_id")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
```

---

## 📳 2. Vibración Háptica Dinámica
Para notificar al usuario el final de descansos de forma sutil sin necesidad de que mire la pantalla, se implementa háptica nativa.

```kotlin
class KpknHapticController(private val context: Context) {
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun triggerSuccessTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Un pulso rápido y suave para éxito
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    fun triggerDoubleAlert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Patrón de doble pulso para alertas críticas
            val timings = longArrayOf(0, 150, 100, 150)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 150, 100, 150), -1)
        }
    }
}
```

---

## ⏰ 3. Gestor de Descanso en Background (AlarmManager & Notifications)
El temporizador de descanso entre series (`WorkoutRestAlertManager`) debe poder correr aun con el teléfono bloqueado. Se utiliza `AlarmManager` para disparar alarmas exactas que despierten al procesador.

### Registro de Alarma Exacta de Descanso:
```kotlin
class WorkoutRestAlertManager(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleRestEndAlarm(restSeconds: Int) {
        val intent = Intent(context, RestEndReceiver::class.java).apply {
            action = "ACTION_REST_TIMER_FINISHED"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (restSeconds * 1000)

        // Registrar alarma exacta que despierte el dispositivo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}

// Receptor que capta la alarma del sistema y despliega notificación push
class RestEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ACTION_REST_TIMER_FINISHED") {
            // Disparar Notificación y Háptica
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val notification = NotificationCompat.Builder(context, "kpkn_channel_rest")
                .setSmallIcon(R.drawable.kpknicon)
                .setContentTitle("¡Descanso Terminado!")
                .setContentText("Es hora de realizar tu siguiente serie de entrenamiento.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(42, notification)
        }
    }
}
```
