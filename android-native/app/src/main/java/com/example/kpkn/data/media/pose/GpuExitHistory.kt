package com.example.kpkn.data.media.pose

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/** Cómo terminó el proceso que dejó un intento con GPU pendiente; `null` si no hay historial fiable. */
fun interface GpuExitHistory {
    fun causeOf(pid: Int): GpuExitCause?
}

enum class GpuExitCause { NATIVE_CRASH, JAVA_CRASH, ANR, OTHER }

/** Solo consulta las salidas del proceso actual dentro del paquete, sin confundir otros procesos de la app. */
internal class AndroidGpuExitHistory(private val context: Context) : GpuExitHistory {
    override fun causeOf(pid: Int): GpuExitCause? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
            val processName = Application.getProcessName() ?: return null
            val exit = manager.getHistoricalProcessExitReasons(context.packageName, 0, 16)
                .firstOrNull { it.pid == pid && it.processName == processName } ?: return null
            when (exit.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> GpuExitCause.NATIVE_CRASH
                ApplicationExitInfo.REASON_CRASH -> GpuExitCause.JAVA_CRASH
                ApplicationExitInfo.REASON_ANR -> GpuExitCause.ANR
                ApplicationExitInfo.REASON_UNKNOWN -> null
                else -> GpuExitCause.OTHER
            }
        } catch (_: Exception) {
            null
        }
    }
}
