package com.example.kpkn.features.healthconnect

import android.content.Context
import com.example.kpkn.data.models.BodyMeasurementEntry
import com.example.kpkn.data.repository.NutritionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class HealthConnectRepository private constructor(context: Context) {
    
    private val healthConnectManager = HealthConnectManager.getInstance(context)
    private val nutritionRepository = NutritionRepository.getInstance()
    private val _isAvailable = MutableStateFlow(false)
    private val _hasPermissions = MutableStateFlow(false)
    private val _lastSyncDate = MutableStateFlow<LocalDate?>(null)
    private val _currentMetrics = MutableStateFlow(HealthMetrics())
    private val _isSyncing = MutableStateFlow(false)
    
    val isAvailable: Flow<Boolean> = _isAvailable.asStateFlow()
    val hasPermissions: Flow<Boolean> = _hasPermissions.asStateFlow()
    val lastSyncDate: Flow<LocalDate?> = _lastSyncDate.asStateFlow()
    val currentMetrics: Flow<HealthMetrics> = _currentMetrics.asStateFlow()
    val isSyncing: Flow<Boolean> = _isSyncing.asStateFlow()
    
    companion object {
        @Volatile
        private var instance: HealthConnectRepository? = null
        
        fun getInstance(context: Context): HealthConnectRepository {
            return instance ?: synchronized(this) {
                instance ?: HealthConnectRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    suspend fun checkAvailability() {
        _isAvailable.value = healthConnectManager.isAvailable()
    }
    
    suspend fun checkPermissions() {
        _hasPermissions.value = healthConnectManager.hasAllPermissions()
    }
    
    suspend fun syncHealthData(daysBack: Int = 7): Boolean {
        if (_isSyncing.value) return false
        
        _isSyncing.value = true
        try {
            if (!healthConnectManager.isAvailable()) {
                return false
            }
            
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(daysBack.toLong())
            
            healthConnectManager.getHealthDataFlow(startDate, endDate).collect { result ->
                if (result.isSuccess) {
                    val metrics = HealthMetrics.fromHealthData(result)
                    _currentMetrics.value = metrics
                    _lastSyncDate.value = LocalDate.now()
                    
                    // Sync to body repository for AUGE integration
                    syncToBodyRepository(metrics)
                }
            }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            _isSyncing.value = false
        }
    }
    
    private suspend fun syncToBodyRepository(metrics: HealthMetrics) {
        metrics.latestWeightKg?.let { weight ->
            val entry = BodyMeasurementEntry(
                id = System.currentTimeMillis().toString(),
                date = LocalDate.now().toString(),
                weight = weight,
                bodyFat = metrics.latestBodyFatPercentage,
                notes = "Synced from Health Connect"
            )
            nutritionRepository.addBodyMeasurement(entry)
        }
    }
    
    suspend fun readBodyMassFromHealthConnect(startDate: LocalDate, endDate: LocalDate): List<BodyMassRecord> {
        return healthConnectManager.readBodyMass(startDate, endDate)
    }
    
    suspend fun readStepsFromHealthConnect(startDate: LocalDate, endDate: LocalDate): List<StepCountRecord> {
        return healthConnectManager.readSteps(startDate, endDate)
    }
    
    suspend fun readExerciseSessionsFromHealthConnect(startDate: LocalDate, endDate: LocalDate): List<ExerciseSessionRecord> {
        return healthConnectManager.readExerciseSessions(startDate, endDate)
    }

    suspend fun readHeartRateSeries(startDate: LocalDate, endDate: LocalDate): List<HeartRateRecord> {
        return healthConnectManager.readHeartRate(startDate, endDate)
    }
    
    suspend fun writeWeightToHealthConnect(weightKg: Double): Boolean {
        return healthConnectManager.writeBodyMass(weightKg, java.time.Instant.now())
    }
    
    suspend fun writeBodyFatToHealthConnect(percentage: Double): Boolean {
        return healthConnectManager.writeBodyFat(percentage, java.time.Instant.now())
    }
    
    fun createPermissionRequestIntent(): android.content.Intent {
        return healthConnectManager.createPermissionRequestIntent()
    }
}

typealias BodyMassRecord = androidx.health.connect.client.records.WeightRecord
typealias BodyFatRecord = androidx.health.connect.client.records.BodyFatRecord
typealias StepCountRecord = androidx.health.connect.client.records.StepsRecord
typealias ExerciseSessionRecord = androidx.health.connect.client.records.ExerciseSessionRecord
typealias HeartRateRecord = androidx.health.connect.client.records.HeartRateRecord
typealias ActiveCaloriesBurnedRecord = androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
