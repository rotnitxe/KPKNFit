package com.example.kpkn.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.kpkn.data.db.*
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.effectiveManualAnchors
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Caché sin fila persistida: no hay una actualización pendiente que anunciar. */
internal fun adaptiveCacheWhenEntityIsMissing(): AugeAdaptiveCache =
    AugeAdaptiveCache(modelUpdateNoticeShown = true)

/**
 * AugeRepository — Persistent storage para datos del sistema AUGE.
 * Migrado de SharedPreferences a Room para consistencia con ProgramRepository.
 *
 * Date policy: calendar keys use LocalDate in the device zone; anchors/schedules use epoch ms.
 */
class AugeRepository private constructor(context: Context) {

    private val db = KpknDatabase.getInstance(context)
    private val dao = db.augeDao()

    // ─── DailyWellbeingLog ────────────────────────────────────────────────────

    suspend fun getWellbeingLogs(): List<DailyWellbeingLog> = withContext(Dispatchers.IO) {
        dao.getAllWellbeing().mapNotNull { it.toWellbeingLog() }
    }

    /**
     * Upserts wellbeing for a calendar date, reusing the existing row id when present
     * so the unique(date) constraint is respected.
     */
    suspend fun saveWellbeingLog(log: DailyWellbeingLog) = withContext(Dispatchers.IO) {
        val existing = dao.getWellbeingForDate(log.date)?.toWellbeingLog()
        val toSave = if (existing != null && existing.id != log.id) {
            log.copy(id = existing.id)
        } else {
            log
        }
        dao.upsertWellbeing(toSave.toEntity())
    }

    /**
     * Gesto manual de AUGE: el bienestar con las anclas nuevas y el caché con los posteriores y los ids procesados se
     * guardan en UNA transacción Room. Si falla el segundo upsert se revierte el primero y no queda sello de dedupe ni
     * ancla a medias; el reintento con la misma identidad vuelve a evaluar sobre el estado anterior. Reutiliza la
     * resolución por fecha/id de [saveWellbeingLog] y la protección de un esquema futuro de [saveAdaptiveCache]
     * (con caché no escribible solo se guarda el bienestar). La base se queda en su versión: sin migración.
     */
    suspend fun saveWellbeingAndCache(log: DailyWellbeingLog, cache: AugeAdaptiveCache): Unit = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = dao.getWellbeingForDate(log.date)?.toWellbeingLog()
            val toSave = if (existing != null && existing.id != log.id) log.copy(id = existing.id) else log
            val stored = dao.getAdaptiveCache()?.decodeAdaptiveCache()
            dao.upsertWellbeing(toSave.toEntity())
            if (stored?.writable != false) {
                dao.upsertAdaptiveCache(com.example.kpkn.domain.auge.AugeAdaptiveCacheMigration.normalize(cache).toEntity())
            }
            Unit
        }
    }

    suspend fun getTodayWellbeing(): DailyWellbeingLog? = withContext(Dispatchers.IO) {
        dao.getWellbeingForDate(LocalDate.now().toString())?.toWellbeingLog()
    }

    /** Punto único de lectura: bienestar de hoy con la última ancla vigente de cada canal. */
    suspend fun getEffectiveManualAnchors(nowMs: Long, zone: ZoneId): DailyWellbeingLog? = withContext(Dispatchers.IO) {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        effectiveManualAnchors(
            dao.getWellbeingInRange(today.minusDays(10).toString(), today.toString()).mapNotNull { it.toWellbeingLog() },
            nowMs, zone,
        )
    }

    /** Compatibilidad para consumidores antiguos; comparte la misma resolución sin corte de 18 h. */
    suspend fun getActiveWellbeingWithManualOverrides(): DailyWellbeingLog? =
        getEffectiveManualAnchors(System.currentTimeMillis(), ZoneId.systemDefault())

    /** Restablece un canal global, un músculo o todos; la marca neutraliza también las filas anteriores. */
    suspend fun clearManualBatteryOverrides(
        channel: RecoveryChannelId? = null,
        muscle: String? = null,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) = withContext(Dispatchers.IO) {
        require(channel == null || muscle == null)
        val date = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toString()
        val today = dao.getWellbeingForDate(date)?.toWellbeingLog()
            ?: DailyWellbeingLog(id = UUID.randomUUID().toString(), date = date)
        val all = channel == null && muscle == null
        val effective = getEffectiveManualAnchors(nowMs, zone)
        val localKeys = if (all) {
            effective?.manualMuscleBatteries.orEmpty().keys + effective?.manualMuscleOverridesV2.orEmpty().keys +
                today.manualMuscleBatteries.keys + today.manualMuscleOverridesV2.keys
        } else setOfNotNull(muscle)
        val resetMuscles = localKeys.map { getAugeMusclePillarId(it) }.toSet()
        fun keepLocal(key: String) = getAugeMusclePillarId(key) !in resetMuscles
        saveWellbeingLog(
            today.copy(
                manualNeuralBattery = if (all || channel == RecoveryChannelId.SYSTEM) null else today.manualNeuralBattery,
                manualSpinalBattery = if (all || channel == RecoveryChannelId.STRUCTURE) null else today.manualSpinalBattery,
                manualMuscularBattery = if (all || channel == RecoveryChannelId.MUSCULAR) null else today.manualMuscularBattery,
                manualMuscleBatteries = today.manualMuscleBatteries.filterKeys(::keepLocal),
                manualMuscleAnchorMs = today.manualMuscleAnchorMs.filterKeys(::keepLocal),
                manualMuscleOverridesV2 = today.manualMuscleOverridesV2.filterKeys(::keepLocal),
                manualBatteryResetMs = today.manualBatteryResetMs +
                    (if (all) RecoveryChannelId.entries else listOfNotNull(channel)).associateWith { nowMs },
                manualMuscleResetMs = today.manualMuscleResetMs + resetMuscles.associateWith { nowMs },
            ),
        )
    }

    // ─── SleepLog (engine projection — prefer saveSleepLogExtended) ───────────

    suspend fun getLastNSleepLogs(n: Int): List<SleepLog> = withContext(Dispatchers.IO) {
        val limit = n.coerceAtLeast(1)
        dao.getLastNSleepLogs(limit).mapNotNull { it.toSleepLog() }
    }

    // ─── SleepLogExtended (canonical rich sleep source) ───────────────────────

    suspend fun saveSleepLogExtended(log: SleepLogExtended) = withContext(Dispatchers.IO) {
        dao.upsertSleepLogExtendedAtomic(
            extended = log.toExtendedEntity(),
            basic = log.toSleepLog().toEntity(),
        )
    }

    suspend fun getLastNSleepLogsExtended(n: Int): List<SleepLogExtended> = withContext(Dispatchers.IO) {
        val limit = n.coerceAtLeast(1)
        dao.getLastNSleepLogsExtended(limit).mapNotNull { it.toSleepLogExtended() }
    }

    suspend fun getAllSleepLogsExtended(): List<SleepLogExtended> = withContext(Dispatchers.IO) {
        dao.getAllSleepLogsExtended().mapNotNull { it.toSleepLogExtended() }
    }

    suspend fun deleteSleepLogExtended(id: String) = withContext(Dispatchers.IO) {
        dao.deleteSleepLogBoth(id)
    }

    // ─── PostSessionFeedback ──────────────────────────────────────────────────

    suspend fun getPostSessionFeedbacks(): List<PostSessionFeedback> = withContext(Dispatchers.IO) {
        dao.getAllFeedback().mapNotNull { it.toFeedback() }
    }

    suspend fun savePostSessionFeedback(fb: PostSessionFeedback) = withContext(Dispatchers.IO) {
        dao.upsertFeedback(fb.toEntity())
    }

    suspend fun getFeedbackForLog(logId: String): PostSessionFeedback? = withContext(Dispatchers.IO) {
        dao.getFeedbackForLog(logId)?.toFeedback()
    }

    // ─── Adaptive Cache ─────────────────────────────────────────────────────

    suspend fun getAdaptiveCache(): AugeAdaptiveCache = withContext(Dispatchers.IO) {
        val result = dao.getAdaptiveCache()?.decodeAdaptiveCache()
        result?.diagnostics?.forEach { diagnostic ->
            KpknDiagnosticLogger.event(namespace = "auge", name = "adaptive_cache_v4", fields = mapOf("diagnostic" to diagnostic))
        }
        result?.cache ?: adaptiveCacheWhenEntityIsMissing()
    }

    suspend fun saveAdaptiveCache(cache: AugeAdaptiveCache) = withContext(Dispatchers.IO) {
        // Releer también al guardar: ni los avisos ni otro escritor deben tapar un esquema futuro.
        val stored = dao.getAdaptiveCache()?.decodeAdaptiveCache()
        if (stored?.writable != false) {
            dao.upsertAdaptiveCache(com.example.kpkn.domain.auge.AugeAdaptiveCacheMigration.normalize(cache).toEntity())
        }
    }

    suspend fun resetAdaptiveCache() = withContext(Dispatchers.IO) {
        dao.upsertAdaptiveCache(AugeAdaptiveCache().toEntity())
    }

    // ─── Bulk import (backup) ─────────────────────────────────────────────────

    suspend fun importBackupSlice(
        wellbeingLogs: List<DailyWellbeingLog>,
        sleepLogs: List<SleepLog>,
        sleepLogsExtended: List<SleepLogExtended>,
        postSessionFeedback: List<PostSessionFeedback>,
        adaptiveCache: AugeAdaptiveCache?,
    ) = withContext(Dispatchers.IO) {
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

        fun getInstance(context: Context): AugeRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AugeRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
