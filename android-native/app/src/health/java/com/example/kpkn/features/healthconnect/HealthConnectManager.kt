package com.example.kpkn.features.healthconnect

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectManager private constructor(private val context: Context) {

    private var client: HealthConnectClient? = null

    companion object {
        @Volatile
        private var instance: HealthConnectManager? = null

        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getWritePermission(BodyFatRecord::class),
        )
        val READ_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )
        /** Minimum permission set required to import body progress. */
        val BODY_READ_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
        )
        val WRITE_PERMISSIONS = setOf(
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getWritePermission(BodyFatRecord::class),
        )

        fun getInstance(context: Context): HealthConnectManager {
            return instance ?: synchronized(this) {
                instance ?: HealthConnectManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    fun isAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun getClient(): HealthConnectClient? {
        if (!isAvailable()) return null
        if (client == null) {
            client = HealthConnectClient.getOrCreate(context)
        }
        return client
    }

    suspend fun hasAllPermissions(): Boolean {
        val healthClient = getClient() ?: return false
        return try {
            val granted = healthClient.permissionController.getGrantedPermissions()
            PERMISSIONS.all { it in granted }
        } catch (_: Exception) {
            false
        }
    }

    fun createPermissionRequestIntent(): Intent {
        return Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
    }

    suspend fun readBodyMass(startDate: LocalDate, endDate: LocalDate): List<WeightRecord> {
        val healthClient = getClient() ?: return emptyList()
        return try {
            healthClient.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                        endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    ),
                ),
            ).records
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun readBodyFat(startDate: LocalDate, endDate: LocalDate): List<BodyFatRecord> {
        val healthClient = getClient() ?: return emptyList()
        return try {
            healthClient.readRecords(
                ReadRecordsRequest(
                    recordType = BodyFatRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                        endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    ),
                ),
            ).records
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun readSteps(startDate: LocalDate, endDate: LocalDate): List<StepsRecord> {
        val healthClient = getClient() ?: return emptyList()
        return try {
            healthClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                        endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    ),
                ),
            ).records
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun readExerciseSessions(startDate: LocalDate, endDate: LocalDate): List<ExerciseSessionRecord> {
        val healthClient = getClient() ?: return emptyList()
        return try {
            healthClient.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                        endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    ),
                ),
            ).records
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun readHeartRate(startDate: LocalDate, endDate: LocalDate): List<HeartRateRecord> {
        val healthClient = getClient() ?: return emptyList()
        return try {
            healthClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                        endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    ),
                ),
            ).records
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun hasReadPermissions(): Boolean {
        val healthClient = getClient() ?: return false
        return runCatching {
            val granted = healthClient.permissionController.getGrantedPermissions()
            READ_PERMISSIONS.all { it in granted }
        }.getOrDefault(false)
    }

    suspend fun hasBodyReadPermissions(): Boolean {
        val healthClient = getClient() ?: return false
        return runCatching {
            val granted = healthClient.permissionController.getGrantedPermissions()
            BODY_READ_PERMISSIONS.all { it in granted }
        }.getOrDefault(false)
    }

    suspend fun hasWritePermissions(): Boolean {
        val healthClient = getClient() ?: return false
        return runCatching {
            val granted = healthClient.permissionController.getGrantedPermissions()
            WRITE_PERMISSIONS.all { it in granted }
        }.getOrDefault(false)
    }

    suspend fun readActiveCalories(startDate: LocalDate, endDate: LocalDate): List<ActiveCaloriesBurnedRecord> {
        val healthClient = getClient() ?: return emptyList()
        return try {
            healthClient.readRecords(
                ReadRecordsRequest(
                    recordType = ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                        endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    ),
                ),
            ).records
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun writeBodyMass(weightKg: Double, timestamp: Instant): Boolean {
        val healthClient = getClient() ?: return false
        return try {
            healthClient.insertRecords(
                listOf(
                    WeightRecord(
                        time = timestamp,
                        weight = Mass.kilograms(weightKg),
                        zoneOffset = ZoneId.systemDefault().rules.getOffset(timestamp),
                        metadata = Metadata.manualEntry(),
                    ),
                ),
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun writeBodyFat(percentage: Double, timestamp: Instant): Boolean {
        val healthClient = getClient() ?: return false
        return try {
            healthClient.insertRecords(
                listOf(
                    BodyFatRecord(
                        time = timestamp,
                        percentage = Percentage(percentage),
                        zoneOffset = ZoneId.systemDefault().rules.getOffset(timestamp),
                        metadata = Metadata.manualEntry(),
                    ),
                ),
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getHealthDataFlow(startDate: LocalDate, endDate: LocalDate): Flow<HealthDataResult> = flow {
        try {
            val steps = readSteps(startDate, endDate)
            val exercises = readExerciseSessions(startDate, endDate)
            val heartRate = readHeartRate(startDate, endDate)
            val calories = readActiveCalories(startDate, endDate)
            val bodyMass = readBodyMass(startDate, endDate)
            val bodyFat = readBodyFat(startDate, endDate)

            emit(
                HealthDataResult(
                    steps = steps,
                    exerciseSessions = exercises,
                    heartRate = heartRate,
                    activeCalories = calories,
                    bodyMass = bodyMass,
                    bodyFat = bodyFat,
                    isSuccess = true,
                ),
            )
        } catch (e: Exception) {
            emit(HealthDataResult(isSuccess = false, errorMessage = e.message))
        }
    }
}

data class HealthDataResult(
    val steps: List<StepsRecord> = emptyList(),
    val exerciseSessions: List<ExerciseSessionRecord> = emptyList(),
    val heartRate: List<HeartRateRecord> = emptyList(),
    val activeCalories: List<ActiveCaloriesBurnedRecord> = emptyList(),
    val bodyMass: List<WeightRecord> = emptyList(),
    val bodyFat: List<BodyFatRecord> = emptyList(),
    val isSuccess: Boolean = true,
    val errorMessage: String? = null,
)

data class HealthMetrics(
    val totalSteps: Long = 0,
    val totalExerciseMinutes: Long = 0,
    val totalActiveCalories: Double = 0.0,
    val latestWeightKg: Double? = null,
    val latestBodyFatPercentage: Double? = null,
    val exerciseCount: Int = 0,
    val averageHeartRateBpm: Double? = null,
) {
    companion object {
        fun fromHealthData(data: HealthDataResult): HealthMetrics {
            val totalSteps = data.steps.sumOf { it.count }
            val totalExerciseMinutes = data.exerciseSessions.sumOf {
                java.time.Duration.between(it.startTime, it.endTime).toMinutes()
            }
            val totalActiveCalories = data.activeCalories.sumOf { it.energy.inKilocalories }
            val latestWeight = data.bodyMass.lastOrNull()?.weight?.inKilograms
            val latestBodyFat = data.bodyFat.lastOrNull()?.percentage?.value
            val heartRateSamples = data.heartRate.flatMap { it.samples }
            val averageHeartRate = heartRateSamples
                .map { it.beatsPerMinute.toDouble() }
                .takeIf { it.isNotEmpty() }
                ?.average()

            return HealthMetrics(
                totalSteps = totalSteps,
                totalExerciseMinutes = totalExerciseMinutes,
                totalActiveCalories = totalActiveCalories,
                latestWeightKg = latestWeight,
                latestBodyFatPercentage = latestBodyFat,
                exerciseCount = data.exerciseSessions.size,
                averageHeartRateBpm = averageHeartRate,
            )
        }
    }
}
