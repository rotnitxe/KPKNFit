package com.example.kpkn.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable

@Serializable
data class PerformanceSnapshotData(
    val contextKey: String,
    val sessionId: String,
    val erm: Double,
    val setCount: Int = 0,
    val avgRpe: Double? = null,
    val reachedFailure: Boolean = false,
    val recordedAtMs: Long = 0L,
    /** Invalidación técnica (technicalQuality ≤ 2): excluir del rango de rendimiento. */
    val isTechnicalInvalid: Boolean = false,
)

@Entity(tableName = "performance_snapshot")
data class PerformanceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contextKey: String,
    val data: String,
)

fun PerformanceSnapshotData.toEntity(): PerformanceSnapshotEntity {
    val now = System.currentTimeMillis()
    return PerformanceSnapshotEntity(
        contextKey = contextKey,
        data = dbJson.encodeToString(this.copy(recordedAtMs = if (recordedAtMs == 0L) now else recordedAtMs)),
    )
}

fun PerformanceSnapshotEntity.toPerformanceSnapshotData(): PerformanceSnapshotData =
    dbJson.decodeFromString(data)
