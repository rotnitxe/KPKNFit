package com.example.kpkn.screens.sessioneditor

import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

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
        val payload = JsonObject(
            linkedMapOf(
                "sessionId" to JsonPrimitive(SESSION_ID),
                "hypothesisId" to JsonPrimitive(hypothesisId),
                "location" to JsonPrimitive(location),
                "message" to JsonPrimitive(message),
                "timestamp" to JsonPrimitive(System.currentTimeMillis()),
                "runId" to JsonPrimitive(runId),
                "eventId" to JsonPrimitive(UUID.randomUUID().toString()),
                "data" to JsonObject(data.mapValues { (_, value) -> value.toJsonElement() }),
            ),
        ).toString()

        runCatching { Log.i(TAG, payload) }

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

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Iterable<*> -> JsonArray(map { value -> value.toJsonElement() })
        is Array<*> -> JsonArray(map { value -> value.toJsonElement() })
        else -> JsonPrimitive(toString())
    }
}
