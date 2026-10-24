package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.TrainingObservationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class TrainingObservationRepository(context: Context) {
    private val dao = KpknDatabase.getInstance(context.applicationContext).trainingObservationDao()
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
}
