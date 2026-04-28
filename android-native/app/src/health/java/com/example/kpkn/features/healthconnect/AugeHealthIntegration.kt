package com.example.kpkn.features.healthconnect

import android.content.Context
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class AugeHealthIntegration private constructor(context: Context) {
    
    private val healthConnectRepo = HealthConnectRepository.getInstance(context)
    private val augeRepository = AugeRepository.getInstance(context)
    private val programRepository = ProgramRepository.getInstance()
    private val _healthImpact = MutableStateFlow<HealthImpactResult?>(null)
    val healthImpact: Flow<HealthImpactResult?> = _healthImpact.asStateFlow()
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: Flow<Boolean> = _isSyncing.asStateFlow()
    
    companion object {
        @Volatile
        private var instance: AugeHealthIntegration? = null
        
        fun getInstance(context: Context): AugeHealthIntegration {
            return instance ?: synchronized(this) {
                instance ?: AugeHealthIntegration(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    suspend fun checkAndSyncHealthData(): HealthSyncResult {
        _isSyncing.value = true
        try {
            healthConnectRepo.checkAvailability()
            healthConnectRepo.checkPermissions()
            
            if (!healthConnectRepo.hasPermissions.first()) {
                return HealthSyncResult(
                    success = false,
                    message = "Permissions not granted",
                    syncedAt = null
                )
            }
            
            val success = healthConnectRepo.syncHealthData(daysBack = 7)
            
            if (success) {
                val metrics = healthConnectRepo.currentMetrics.first()
                _healthImpact.value = calculateHealthImpact(metrics)
            }
            
            return HealthSyncResult(
                success = success,
                message = if (success) "Sync completed" else "Sync failed",
                syncedAt = if (success) LocalDate.now() else null
            )
        } finally {
            _isSyncing.value = false
        }
    }
    
    private fun calculateHealthImpact(metrics: HealthMetrics): HealthImpactResult {
        // Calculate AUGE-relevant impact from Health Connect data
        
        val activityImpact = calculateActivityImpact(
            totalSteps = metrics.totalSteps,
            exerciseMinutes = metrics.totalExerciseMinutes,
            activeCalories = metrics.totalActiveCalories
        )
        
        val bodyImpact = calculateBodyImpact(
            latestWeight = metrics.latestWeightKg,
            latestBodyFat = metrics.latestBodyFatPercentage
        )
        
        return HealthImpactResult(
            activityImpact = activityImpact,
            bodyImpact = bodyImpact,
            readinessModifier = calculateReadinessModifier(activityImpact, bodyImpact),
            insights = generateInsights(metrics, activityImpact, bodyImpact)
        )
    }
    
    private fun calculateActivityImpact(
        totalSteps: Long,
        exerciseMinutes: Long,
        activeCalories: Double
    ): ActivityImpact {
        // Steps: 10,000+ steps = positive activity impact
        val stepsScore = when {
            totalSteps >= 15000 -> 1.1f  // Excellent
            totalSteps >= 10000 -> 1.0f  // Good
            totalSteps >= 7500 -> 0.9f   // Moderate
            totalSteps >= 5000 -> 0.8f   // Low
            else -> 0.7f                 // Very low
        }
        
        // Exercise minutes: more is generally better for recovery, but with limits
        val exerciseScore = when {
            exerciseMinutes >= 60 -> 1.05f  // Good amount of exercise
            exerciseMinutes >= 30 -> 1.0f   // Adequate
            exerciseMinutes >= 15 -> 0.95f  // Light
            else -> 0.9f                    // Minimal
        }
        
        // Active calories as a secondary indicator
        val caloriesScore = when {
            activeCalories >= 500 -> 1.05f
            activeCalories >= 300 -> 1.0f
            activeCalories >= 150 -> 0.95f
            else -> 0.9f
        }
        
        return ActivityImpact(
            steps = totalSteps,
            exerciseMinutes = exerciseMinutes,
            activeCalories = activeCalories,
            score = (stepsScore * 0.4f + exerciseScore * 0.4f + caloriesScore * 0.2f).coerceIn(0.7f, 1.15f),
            recommendation = generateActivityRecommendation(totalSteps, exerciseMinutes)
        )
    }
    
    private fun calculateBodyImpact(
        latestWeight: Double?,
        latestBodyFat: Double?
    ): BodyImpact {
        if (latestWeight == null) {
            return BodyImpact(
                hasData = false,
                weightKg = null,
                bodyFatPercentage = null,
                score = 1.0f,
                recommendation = "Registra tu peso para mejorar el análisis AUGE"
            )
        }
        
        val weightScore = 1.0f // Weight by itself doesn't indicate good/bad without trend
        
        val fatScore = when {
            latestBodyFat == null -> 1.0f
            latestBodyFat <= 15 -> 1.1f  // Good for athletes
            latestBodyFat <= 20 -> 1.0f  // Good range
            latestBodyFat <= 25 -> 0.95f // Above ideal
            else -> 0.85f                // High
        }
        
        return BodyImpact(
            hasData = true,
            weightKg = latestWeight,
            bodyFatPercentage = latestBodyFat,
            score = (weightScore * 0.5f + fatScore * 0.5f).coerceIn(0.8f, 1.15f),
            recommendation = if (latestBodyFat != null) {
                "Porcentaje de grasa corporal: ${latestBodyFat.toInt()}%"
            } else {
                "Registra tu porcentaje de grasa para análisis completo"
            }
        )
    }
    
    private fun calculateReadinessModifier(
        activityImpact: ActivityImpact,
        bodyImpact: BodyImpact
    ): Float {
        // Combine activity and body impact for readiness modification
        // Typical range: 0.85 - 1.15
        
        val activityWeight = 0.6f  // Activity has more weight in daily readiness
        val bodyWeight = 0.4f       // Body composition is secondary
        
        return (activityImpact.score * activityWeight + bodyImpact.score * bodyWeight)
            .coerceIn(0.85f, 1.15f)
    }
    
    private fun generateInsights(
        metrics: HealthMetrics,
        activityImpact: ActivityImpact,
        bodyImpact: BodyImpact
    ): List<String> {
        val insights = mutableListOf<String>()
        
        // Steps insights
        if (metrics.totalSteps < 5000) {
            insights.add("Pasos bajos hoy. Considera caminar más para mejorar recuperación activa.")
        } else if (metrics.totalSteps >= 10000) {
            insights.add("Excelente actividad con ${metrics.totalSteps} pasos hoy!")
        }
        
        // Exercise insights
        if (metrics.totalExerciseMinutes > 0) {
            insights.add("${metrics.totalExerciseMinutes} minutos de ejercicio registrado hoy.")
        }
        
        // Calories insights
        if (metrics.totalActiveCalories > 0) {
            insights.add("${metrics.totalActiveCalories.toInt()} kcal activas quemadas.")
        }
        
        // Body composition insights
        if (bodyImpact.hasData && bodyImpact.bodyFatPercentage != null) {
            val bf = bodyImpact.bodyFatPercentage
            when {
                bf <= 15 -> insights.add("Excelente composición corporal para rendimiento.")
                bf <= 20 -> insights.add("Buena composición corporal. Mantén el ritmo.")
                bf <= 25 -> insights.add("Composición corporal moderadamente alta.")
                else -> insights.add("Considera ajustar nutrición y entrenamiento.")
            }
        }
        
        return insights
    }
    
    private fun generateActivityRecommendation(steps: Long, exerciseMinutes: Long): String {
        return when {
            steps >= 15000 && exerciseMinutes >= 60 -> 
                "Excelente nivel de actividad. Listo para entrenamiento intenso."
            steps >= 10000 && exerciseMinutes >= 30 ->
                "Buena actividad. Listo para entrenamiento regular."
            steps >= 7500 ->
                "Actividad moderada. Entrenamiento ligero recomendado."
            else ->
                "Actividad baja. Considera descanso o actividad suave hoy."
        }
    }
    
    fun getReadinessModifier(): Float {
        return _healthImpact.value?.readinessModifier ?: 1.0f
    }
    
    fun getHealthInsights(): List<String> {
        return _healthImpact.value?.insights ?: emptyList()
    }
    
    suspend fun getRecentHealthData(days: Int = 7): HealthMetrics {
        healthConnectRepo.syncHealthData(days)
        return healthConnectRepo.currentMetrics.first()
    }
}

data class HealthSyncResult(
    val success: Boolean,
    val message: String,
    val syncedAt: LocalDate?
)

data class HealthImpactResult(
    val activityImpact: ActivityImpact,
    val bodyImpact: BodyImpact,
    val readinessModifier: Float,
    val insights: List<String>
)

data class ActivityImpact(
    val steps: Long,
    val exerciseMinutes: Long,
    val activeCalories: Double,
    val score: Float,
    val recommendation: String
)

data class BodyImpact(
    val hasData: Boolean,
    val weightKg: Double?,
    val bodyFatPercentage: Double?,
    val score: Float,
    val recommendation: String
)
