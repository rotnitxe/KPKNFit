package com.example.kpkn.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "calibration_decisions", indices = [
    Index(value = ["sessionLogId", "sourceSetId"]),
    Index(value = ["exerciseConfigId", "contextKey", "status", "resolvedAt"]),
])
data class CalibrationDecisionEntity(
    @PrimaryKey val id: String,
    val sourceSetId: String,
    val sessionLogId: String,
    val programId: String?,
    val exerciseConfigId: String,
    val contextKey: String,
    val capacityKg: Double?,
    val referenceKg: Double?,
    val referenceSource: String,
    val ratio: Double?,
    val factor: Double,
    val scope: String,
    val eligibility: String,
    val affected: String,
    /** Kept for the contract; calibration never propagates to another exercise. */
    val propagated: String,
    val status: String,
    val createdAt: Long,
    val resolvedAt: Long?,
    val estimatorVersion: Int,
    val data: String,
)

@Dao
interface CalibrationDecisionDao {
    @Query("SELECT * FROM calibration_decisions ORDER BY createdAt, id")
    suspend fun getAll(): List<CalibrationDecisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(decisions: List<CalibrationDecisionEntity>)

    @Query("SELECT * FROM calibration_decisions WHERE sessionLogId = :sessionLogId ORDER BY createdAt, id")
    suspend fun forSession(sessionLogId: String): List<CalibrationDecisionEntity>

    /** Una sesión descartada no deja decisiones: la serie que las originó tampoco llegó al historial. */
    @Query("DELETE FROM calibration_decisions WHERE sessionLogId = :sessionLogId")
    suspend fun deleteForSession(sessionLogId: String)

    @Query("SELECT * FROM calibration_decisions WHERE status = 'APPLIED' AND capacityKg > 0 AND resolvedAt >= :since AND resolvedAt <= :now ORDER BY resolvedAt DESC")
    suspend fun accepted(since: Long, now: Long): List<CalibrationDecisionEntity>
}
