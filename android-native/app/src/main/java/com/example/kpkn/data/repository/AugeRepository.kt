package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * AugeRepository — Persistent storage para datos del sistema AUGE.
 * Migrado de SharedPreferences a Room para consistencia con ProgramRepository.
 */
class AugeRepository private constructor(context: Context) {

    private val dao = KpknDatabase.getInstance(context).augeDao()

    // ─── DailyWellbeingLog ────────────────────────────────────────────────────

    suspend fun getWellbeingLogs(): List<DailyWellbeingLog> = withContext(Dispatchers.IO) {
        dao.getAllWellbeing().map { it.toWellbeingLog() }
    }

    suspend fun saveWellbeingLog(log: DailyWellbeingLog) = withContext(Dispatchers.IO) {
        dao.upsertWellbeing(log.toEntity())
    }

    suspend fun getTodayWellbeing(): DailyWellbeingLog? = withContext(Dispatchers.IO) {
        dao.getWellbeingForDate(LocalDate.now().toString())?.toWellbeingLog()
    }

    // ─── SleepLog ─────────────────────────────────────────────────────────────

    suspend fun saveSleepLog(log: SleepLog) = withContext(Dispatchers.IO) {
        dao.upsertSleepLog(log.toEntity())
    }

    suspend fun getLastNSleepLogs(n: Int): List<SleepLog> = withContext(Dispatchers.IO) {
        dao.getLastNSleepLogs(n).map { it.toSleepLog() }
    }

    // ─── PostSessionFeedback ──────────────────────────────────────────────────

    suspend fun getPostSessionFeedbacks(): List<PostSessionFeedback> = withContext(Dispatchers.IO) {
        dao.getAllFeedback().map { it.toFeedback() }
    }

    suspend fun savePostSessionFeedback(fb: PostSessionFeedback) = withContext(Dispatchers.IO) {
        dao.upsertFeedback(fb.toEntity())
    }

    suspend fun getFeedbackForLog(logId: String): PostSessionFeedback? = withContext(Dispatchers.IO) {
        dao.getFeedbackForLog(logId)?.toFeedback()
    }

    // ─── PendingQuestionnaire ─────────────────────────────────────────────────

    suspend fun getPendingQuestionnaire(): PendingQuestionnaire? = withContext(Dispatchers.IO) {
        dao.getPendingQuestionnaire()?.toPendingQuestionnaire()
    }

    suspend fun setPendingQuestionnaire(q: PendingQuestionnaire) = withContext(Dispatchers.IO) {
        dao.upsertPendingQuestionnaire(q.toEntity())
    }

    suspend fun clearPendingQuestionnaire() = withContext(Dispatchers.IO) {
        dao.clearPendingQuestionnaire()
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
