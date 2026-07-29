package com.example.kpkn.screens.sessioneditor

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Debug-mode NDJSON logger for session-editor audit (session 9ba5f2).
 * Host unit tests write to the workspace log; Android uses Logcat + HTTP ingest.
 */
internal object SessionEditorDebugLog {
    private const val SESSION_ID = "9ba5f2"
    private const val TAG = "AGENT_9ba5f2"
    private const val INGEST =
        "http://127.0.0.1:7803/ingest/3bdafb84-916f-463c-b94a-538b38a08483"
    private const val INGEST_EMU =
        "http://10.0.2.2:7803/ingest/3bdafb84-916f-463c-b94a-538b38a08483"

    /** Absolute workspace log (host JVM / unit tests). */
    private val hostLogPaths = listOf(
        File("C:/Users/valen/Documents/KPKNFit/debug-9ba5f2.log"),
        File("../debug-9ba5f2.log"),
        File("debug-9ba5f2.log"),
    )

    private val deviceLogPaths = listOf(
        File("/sdcard/Download/debug-9ba5f2.log"),
        File("/storage/emulated/0/Download/debug-9ba5f2.log"),
    )

    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "audit1",
    ) {
        val payload = JSONObject()
            .put("sessionId", SESSION_ID)
            .put("hypothesisId", hypothesisId)
            .put("location", location)
            .put("message", message)
            .put("timestamp", System.currentTimeMillis())
            .put("runId", runId)
            .put("data", JSONObject(data.mapValues { (_, v) -> v ?: JSONObject.NULL }))
            .toString()

        Log.i(TAG, payload)

        for (file in hostLogPaths + deviceLogPaths) {
            try {
                file.parentFile?.mkdirs()
                file.appendText(payload + "\n")
            } catch (_: Exception) {
            }
        }

        Thread {
            for (url in listOf(INGEST_EMU, INGEST)) {
                try {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("X-Debug-Session-Id", SESSION_ID)
                        doOutput = true
                        connectTimeout = 800
                        readTimeout = 800
                    }
                    conn.outputStream.use { it.write(payload.toByteArray()) }
                    conn.responseCode
                    conn.disconnect()
                    break
                } catch (_: Exception) {
                }
            }
        }.start()
    }
}
