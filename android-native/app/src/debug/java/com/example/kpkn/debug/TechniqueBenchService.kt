package com.example.kpkn.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** SOLO .dbg: banco de archivos en primer plano, sin abrir ventanas ni despertar la pantalla. */
class TechniqueBenchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var layout: BenchLayout? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (packageName != "com.example.kpkn.dbg") {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isCompleted == false) {
            runCatching { layout?.let { AnalyzeFileBench.writeBusy(it) } }
                .onFailure { Log.e("KpknTechniqueBench", "No se pudo publicar ocupado", it) }
            return START_NOT_STICKY
        }
        try {
            createChannel()
            val initialNotification = notification(0, 0)
            when {
                Build.VERSION.SDK_INT >= 35 -> startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
                Build.VERSION.SDK_INT >= 29 -> startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else -> startForeground(NOTIFICATION_ID, initialNotification)
            }
            val resolved = BenchLayout.resolve(this)
            layout = resolved
            val request = requireNotNull(intent) { "Falta el intent del banco" }
            require(request.getStringExtra("mode") == "analyze_file") { "mode debe ser analyze_file" }
            // La respuesta vieja se retira antes de devolver el arranque al conductor.
            java.io.File(resolved.outDir, "analyze.json").delete()
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KPKN:TechniqueBench")
                .apply { acquire() }
            job = scope.launch {
                try {
                    AnalyzeFileBench.fromIntent(applicationContext, resolved, request, clearPreviousSummary = false) { index, total ->
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(index, total))
                    }.run()
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Throwable) {
                    writeError(error)
                } finally {
                    withContext(NonCancellable + Dispatchers.Main.immediate) {
                        releaseWakeLock()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        } catch (error: Throwable) {
            writeError(error)
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Banco de técnica", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(index: Int, total: Int): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(if (total > 0) "Banco de técnica · vídeo $index de $total" else "Banco de técnica · preparando vídeos")
        .setOngoing(true)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setLocalOnly(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setProgress(total, index, total == 0)
        .build()

    private fun writeError(error: Throwable) {
        Log.e("KpknTechniqueBench", "Servicio del banco", error)
        layout?.let { resolved ->
            runCatching {
                AnalyzeFileBench.writeReport(resolved.outDir, "analyze.json", JSONObject()
                    .put("modo", "analyze_file").put("ok", false)
                    .put("error", "${error.javaClass.simpleName}: ${error.message}"))
            }.onFailure { Log.e("KpknTechniqueBench", "No se pudo escribir analyze.json", it) }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // Android 15 limita mediaProcessing a seis horas por día; la cancelación publica el parcial.
    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.cancel(CancellationException("Tiempo de servicio en primer plano agotado"))
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "technique_bench"
        const val NOTIFICATION_ID = 4111
    }
}
