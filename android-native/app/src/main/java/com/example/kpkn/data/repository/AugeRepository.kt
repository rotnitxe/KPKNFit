package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/**
 * AugeRepository — Persistent storage para datos del sistema AUGE.
 * Migrado de SharedPreferences a Room para consistencia con ProgramRepository.
 *
 * Date policy: calendar keys use LocalDate in the device zone; anchors/schedules use epoch ms.
 */
class AugeRepository private constructor(context: Context) {

    private val writeOwner = com.example.kpkn.data.backup.DataSessionWriter("auge-repository")
    private val dao = KpknDatabase.getInstance(context).augeDao()
    @Volatile private var muscularResetDiagnosticEmitted = false

    // ─── DailyWellbeingLog ────────────────────────────────────────────────────

    suspend fun getWellbeingLogs(): List<DailyWellbeingLog> = writeOwner.write {
        dao.getAllWellbeing().mapNotNull { it.toWellbeingLog() }
    }

    /**
     * Upserts wellbeing for a calendar date, reusing the existing row id when present
     * so the unique(date) constraint is respected.
     */
    suspend fun saveWellbeingLog(log: DailyWellbeingLog) = writeOwner.write {
        val existing = dao.getWellbeingForDate(log.date)?.toWellbeingLog()
        val toSave = if (existing != null && existing.id != log.id) {
            log.copy(id = existing.id)
        } else {
            log
        }
        dao.upsertWellbeing(toSave.toEntity())
    }

    suspend fun getTodayWellbeing(): DailyWellbeingLog? = writeOwner.write {
        dao.getWellbeingForDate(LocalDate.now().toString())?.toWellbeingLog()
    }

    suspend fun getActiveWellbeingWithManualOverrides(): DailyWellbeingLog? = writeOwner.write {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val now = System.currentTimeMillis()
        val eighteenHoursAgo = now - 18L * 3_600_000L
        // Only today, or yesterday if the manual anchor is still within ~one night (18h)
        dao.getWellbeingInRange(today.minusDays(1).toString(), todayStr)
            .mapNotNull { it.toWellbeingLog() }
            .firstOrNull { w ->
                val hasManual = w.manualNeuralBattery != null ||
                    w.manualMuscularBattery != null ||
                    w.manualSpinalBattery != null ||
                    w.manualMuscleBatteries.isNotEmpty() ||
                    w.manualMuscleOverridesV2.isNotEmpty()
                if (!hasManual) return@firstOrNull false
                if (w.date == todayStr) true
                else (w.manualBatteryAnchorMs ?: 0L) >= eighteenHoursAgo
            }
    }

    /** Clears all manual battery overrides on today's wellbeing (or a no-op if none). */
    suspend fun clearManualBatteryOverrides() = writeOwner.write {
        val today = getTodayWellbeing() ?: return@write
        saveWellbeingLog(
            today.copy(
                manualNeuralBattery = null,
                manualSpinalBattery = null,
                manualMuscularBattery = null,
                manualMuscleBatteries = emptyMap(),
                manualBatteryAnchorMs = null,
                manualMuscleOverridesV2 = emptyMap(),
            ),
        )
    }

    // ─── SleepLog (engine projection — prefer saveSleepLogExtended) ───────────

    suspend fun getLastNSleepLogs(n: Int): List<SleepLog> = writeOwner.write {
        val limit = n.coerceAtLeast(1)
        dao.getLastNSleepLogs(limit).mapNotNull { it.toSleepLog() }
    }

    // ─── SleepLogExtended (canonical rich sleep source) ───────────────────────

    suspend fun saveSleepLogExtended(log: SleepLogExtended) = writeOwner.write {
        dao.upsertSleepLogExtendedAtomic(
            extended = log.toExtendedEntity(),
            basic = log.toSleepLog().toEntity(),
        )
    }

    suspend fun getLastNSleepLogsExtended(n: Int): List<SleepLogExtended> = writeOwner.write {
        val limit = n.coerceAtLeast(1)
        dao.getLastNSleepLogsExtended(limit).mapNotNull { it.toSleepLogExtended() }
    }

    suspend fun getAllSleepLogsExtended(): List<SleepLogExtended> = writeOwner.write {
        dao.getAllSleepLogsExtended().mapNotNull { it.toSleepLogExtended() }
    }

    suspend fun deleteSleepLogExtended(id: String) = writeOwner.write {
        dao.deleteSleepLogBoth(id)
    }

    // ─── PostSessionFeedback ──────────────────────────────────────────────────

    suspend fun getPostSessionFeedbacks(): List<PostSessionFeedback> = writeOwner.write {
        dao.getAllFeedback().mapNotNull { it.toFeedback() }
    }

    suspend fun savePostSessionFeedback(fb: PostSessionFeedback) = writeOwner.write {
        dao.upsertFeedback(fb.toEntity())
    }

    suspend fun getFeedbackForLog(logId: String): PostSessionFeedback? = writeOwner.write {
        dao.getFeedbackForLog(logId)?.toFeedback()
    }

    // ─── Adaptive Cache ─────────────────────────────────────────────────────

    suspend fun getAdaptiveCache(): AugeAdaptiveCache = writeOwner.write {
        val entity = dao.getAdaptiveCache()
        val rawVersion = entity?.data?.let { raw ->
            runCatching {
                Json.parseToJsonElement(raw).jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
            }.getOrDefault(1)
        } ?: 2
        if (rawVersion < 2 && !muscularResetDiagnosticEmitted) {
            muscularResetDiagnosticEmitted = true
            KpknDiagnosticLogger.event(
                namespace = "auge",
                name = "muscular_calibration_reset_v2",
                fields = mapOf("previousSchemaVersion" to rawVersion, "preservedChannels" to listOf("cns", "spinal")),
                priority = com.example.kpkn.data.diagnostics.TelemetryPriority.CRITICAL,
            )
        }
        entity?.toAdaptiveCache() ?: AugeAdaptiveCache()
    }

    suspend fun saveAdaptiveCache(cache: AugeAdaptiveCache) = writeOwner.write {
        dao.upsertAdaptiveCache(cache.toEntity())
    }

    suspend fun resetAdaptiveCache() = writeOwner.write {
        dao.upsertAdaptiveCache(AugeAdaptiveCache().toEntity())
    }

    // ─── Bulk import (backup) ─────────────────────────────────────────────────

    suspend fun importBackupSlice(
        wellbeingLogs: List<DailyWellbeingLog>,
        sleepLogs: List<SleepLog>,
        sleepLogsExtended: List<SleepLogExtended>,
        postSessionFeedback: List<PostSessionFeedback>,
        adaptiveCache: AugeAdaptiveCache?,
    ) = writeOwner.write {
        wellbeingLogs.forEach { saveWellbeingLog(it) }
        sleepLogsExtended.forEach { saveSleepLogExtended(it) }
        // Basic sleep only for entries without an extended counterpart
        val extendedIds = sleepLogsExtended.map { it.id }.toSet()
        sleepLogs.filter { it.id !in extendedIds }.forEach { dao.upsertSleepLog(it.toEntity()) }
        postSessionFeedback.forEach { dao.upsertFeedback(it.toEntity()) }
        if (adaptiveCache != null) {
            dao.upsertAdaptiveCache(adaptiveCache.toEntity())
        }
    }

    // ─── Singleton ───────────────────────────────────────────────────────────

    companion object {
        @Volatile private var INSTANCE: AugeRepository? = null
        internal fun closeForDataSession() { synchronized(this) { INSTANCE?.writeOwner?.closeAdmission(); INSTANCE = null } }

        fun getInstance(context: Context): AugeRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AugeRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
