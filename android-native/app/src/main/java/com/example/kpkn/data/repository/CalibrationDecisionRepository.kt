package com.example.kpkn.data.repository

import com.example.kpkn.data.db.CalibrationDecisionDao
import com.example.kpkn.data.db.CalibrationDecisionEntity
import com.example.kpkn.data.models.CalibrationDecisionRecord
import com.example.kpkn.domain.calibration.AcceptedCalibrationReference
import com.example.kpkn.domain.calibration.CalibrationPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** IO only; the owner wraps snapshot writes with ongoing_workout in one Room transaction. */
class CalibrationDecisionRepository(private val dao: CalibrationDecisionDao) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        /** Identidad estable de una ejecución en curso; la usan la sesión, la rehidratación y el descarte. */
        fun executionId(programId: String, sessionId: String, startTimeMs: Long): String =
            "$programId::$sessionId::$startTimeMs"
    }

    /** Descarta las decisiones de una ejecución cancelada (su identidad estable, no la de un registro final). */
    suspend fun discardExecution(programId: String, sessionId: String, startTimeMs: Long) = withContext(Dispatchers.IO) {
        dao.deleteForSession(executionId(programId, sessionId, startTimeMs))
    }

    suspend fun saveSnapshot(records: List<CalibrationDecisionRecord>) = withContext(Dispatchers.IO) {
        if (records.isNotEmpty()) dao.upsert(records.map { r ->
            val d = r.decision
            CalibrationDecisionEntity(r.id, r.sourceSetId, r.sessionLogId, r.programId,
                d.context.configurationId, d.context.key, d.capacityKg, d.referenceKg, d.referenceSource.name,
                d.ratio, d.factor, d.scope.name, d.eligibility.name, json.encodeToString(d.affectedSets),
                "[]", r.status.name, r.createdAt, r.resolvedAt, d.estimatorVersion, json.encodeToString(r))
        })
    }

    suspend fun forSession(sessionLogId: String): List<CalibrationDecisionRecord> = withContext(Dispatchers.IO) {
        dao.forSession(sessionLogId).map { json.decodeFromString<CalibrationDecisionRecord>(it.data) }
    }

    suspend fun all(): List<CalibrationDecisionRecord> = withContext(Dispatchers.IO) {
        dao.getAll().map { json.decodeFromString<CalibrationDecisionRecord>(it.data) }
    }

    suspend fun accepted(now: Long, excludingSourceSetId: String? = null,
        excludingSessionLogId: String? = null): List<AcceptedCalibrationReference> = withContext(Dispatchers.IO) {
        val since = (now - CalibrationPolicy.defaults.referenceValidityDays * 86_400_000L).coerceAtLeast(0L)
        dao.accepted(since, now).filter { it.sourceSetId != excludingSourceSetId && it.sessionLogId != excludingSessionLogId }
            .map { json.decodeFromString<CalibrationDecisionRecord>(it.data) }
            .mapNotNull { r -> r.decision.capacityKg?.let {
                AcceptedCalibrationReference(r.decision.context, it, r.resolvedAt ?: r.createdAt, r.decision.estimatorVersion)
            } }
    }
}
