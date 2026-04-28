package com.example.kpkn.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable

@Serializable
data class PerformanceRangeData(
    val contextKey: String,
    val ermMin: Double,
    val ermMax: Double,
    val ermRms: Double,
    val sampleCount: Int = 0,
    val lastUpdatedMs: Long = 0L,
    val consecutiveAbove: Int = 0,
    val consecutiveBelow: Int = 0,
)

@Entity(tableName = "performance_range")
data class PerformanceRangeEntity(
    @PrimaryKey val contextKey: String,
    val data: String,
)

fun PerformanceRangeData.toEntity() = PerformanceRangeEntity(
    contextKey = contextKey,
    data = dbJson.encodeToString(this),
)

fun PerformanceRangeEntity.toPerformanceRangeData(): PerformanceRangeData =
    dbJson.decodeFromString(data)
