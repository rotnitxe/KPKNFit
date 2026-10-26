package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.TrainingObservationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class TrainingObservationRepository(context: Context) {
    private val appContext = context.applicationContext
    // Snapshot restore/reconnect can replace Room while this repository remains alive.
    private val dao get() = KpknDatabase.getInstance(appContext).trainingObservationDao()
    suspend fun recordContext(sourceId: String, sessionId: String, capturedAtMs: Long,
        discomfortIds: List<String>, note: String?) = withContext(Dispatchers.IO) {
        if (discomfortIds.isEmpty() && note.isNullOrBlank()) return@withContext
        val payload = buildJsonObject {
            put("sessionId", sessionId); put("capturedAtMs", capturedAtMs)
            put("discomfortIds", JsonArray(discomfortIds.map(::JsonPrimitive)))
            note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
        }
        dao.preserve(TrainingObservationEntity(sourceId, "session_context", "", payload.toString(), "explicit_self_report;optional;not_validated_scale"))
    }
    /**
     * Estado de llegada del día (`wellbeing`). Idempotente por fecha: se conserva la primera
     * observación original y nunca se reescribe desde otra pantalla.
     */
    suspend fun recordWellbeing(log: com.example.kpkn.data.models.DailyWellbeingLog) = withContext(Dispatchers.IO) {
        dao.preserve(
            TrainingObservationEntity(
                "wellbeing:${log.date}",
                "wellbeing",
                log.date,
                com.example.kpkn.data.db.dbJson.encodeToString(
                    com.example.kpkn.data.models.DailyWellbeingLog.serializer(), log,
                ),
                "explicit_self_report;optional;not_validated_scale",
            ),
        )
        Unit
    }

    suspend fun getWellbeingLogs(): List<com.example.kpkn.data.models.DailyWellbeingLog> = withContext(Dispatchers.IO) {
        dao.all().filter { it.kind == "wellbeing" }.mapNotNull { observation ->
            runCatching {
                com.example.kpkn.data.db.dbJson.decodeFromString(
                    com.example.kpkn.data.models.DailyWellbeingLog.serializer(), observation.payload,
                )
            }.getOrNull()
        }
    }

    suspend fun getPostSessionFeedbacks(): List<com.example.kpkn.data.models.PostSessionFeedback> = withContext(Dispatchers.IO) {
        dao.all().filter { it.kind == "feedback" }.map { com.example.kpkn.data.db.dbJson.decodeFromString<com.example.kpkn.data.models.PostSessionFeedback>(it.payload) }
    }
    suspend fun getFeedbackForLog(logId: String): com.example.kpkn.data.models.PostSessionFeedback? =
        getPostSessionFeedbacks().firstOrNull { it.logId == logId }

    companion object {
        @Volatile private var INSTANCE: TrainingObservationRepository? = null
        fun getInstance(context: Context): TrainingObservationRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: TrainingObservationRepository(context).also { INSTANCE = it }
        }
    }

}
