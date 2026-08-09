package com.example.kpkn.services.diagnostics

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.kpkn.R

/** User-visible completion/failure surface for asynchronous diagnostic work. */
object KpknDiagnosticNotificationManager {
    const val CHANNEL_ID = "kpkn_diagnostics"
    private const val SUCCESS_ID = 71_001
    private const val FAILURE_ID = 71_002

    fun ensureChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Diagnósticos KPKN",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Resultados y fallos del análisis de reportes"
            setShowBadge(true)
        }
        context.applicationContext.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun reportCompleted(context: Context, reportId: String, summary: String?) {
        notify(
            context = context,
            id = SUCCESS_ID,
            title = "Reporte analizado",
            body = summary?.takeIf(String::isNotBlank) ?: "El análisis de $reportId ya está disponible en Diagnósticos.",
        )
    }

    fun reportFailed(context: Context, reportId: String, retryable: Boolean, code: String) {
        notify(
            context = context,
            id = FAILURE_ID,
            title = "Análisis de reporte pendiente",
            body = if (retryable) {
                "No se pudo analizar $reportId. Podés reintentarlo desde Diagnósticos."
            } else {
                "El análisis de $reportId falló ($code). Revisá la clave o la conexión en Diagnósticos."
            },
        )
    }

    private fun notify(context: Context, id: Int, title: String, body: String) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                !NotificationManagerCompat.from(appContext).areNotificationsEnabled()
            ) return
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            NotificationManagerCompat.from(appContext).notify(id, notification)
        }
    }
}
