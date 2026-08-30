package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.nutrition.deriveMacroGoals

enum class NutritionRecoveryStatus { DEFICIT, MAINTENANCE, SURPLUS }

data class NutritionRecoveryResult(
    val recoveryTimeMultiplier: Double,
    val status: NutritionRecoveryStatus,
    val factors: List<String>,
)

object NutritionRecoveryEngine {

    fun computeNutritionRecoveryMultiplier(
        nutritionLogs: List<NutritionLog>,
        settings: Settings,
        activePlan: NutritionPlan? = null,
        stressLevel: Int = 3,
        hoursWindow: Int = 48,
    ): NutritionRecoveryResult {
        val factors = mutableListOf<String>()
        var multiplier = 1.0

        val nowMs = System.currentTimeMillis()
        val windowStartMs = nowMs - hoursWindow * 3600_000L

        val recentLogs = nutritionLogs.filter { log ->
            try {
                val logMs = java.time.LocalDate.parse(log.date)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
                logMs > windowStartMs
            } catch (_: Exception) { false }
        }

        val goals = deriveMacroGoals(settings, activePlan)
        val calorieGoal = goals.calorieGoal
        val proteinGoal = goals.proteinGoal

        if (recentLogs.isEmpty()) {
            return NutritionRecoveryResult(
                1.0,
                NutritionRecoveryStatus.MAINTENANCE,
                listOf("Sin comidas en la ventana; no se asume déficit ni superávit."),
            )
        }

        var totalCal = 0.0
        var totalProtein = 0.0
        recentLogs.forEach { log ->
            log.foods.forEach { f: LoggedFood ->
                totalCal += f.calories
                totalProtein += f.protein
            }
        }
        val daysInWindow = maxOf(1.0, hoursWindow / 24.0)
        val avgCalories = totalCal / daysInWindow
        val avgProtein = totalProtein / daysInWindow

        val calRatio = if (calorieGoal > 0) avgCalories / calorieGoal else 1.0
        val proteinRatio = if (proteinGoal > 0) avgProtein / proteinGoal else 1.0

        val status: NutritionRecoveryStatus

        if (calRatio < 0.9) {
            status = NutritionRecoveryStatus.DEFICIT
            val deficitSeverity = 1.0 - calRatio
            multiplier = 1.0 + deficitSeverity * 1.2
            factors.add("Déficit calórico (~${((1 - calRatio) * 100).toInt()}%). Recursos limitados para reparación.")
            if (proteinRatio < 0.7) {
                multiplier *= 1.1
                factors.add("Proteína insuficiente agrava el déficit.")
            }
        } else if (calRatio <= 1.1) {
            status = NutritionRecoveryStatus.MAINTENANCE
            if (proteinRatio < 0.8) {
                multiplier = 1.05
                factors.add("Proteína por debajo del objetivo; ligera penalización.")
            } else {
                factors.add("Mantenimiento calórico. Recuperación estándar.")
            }
        } else {
            status = NutritionRecoveryStatus.SURPLUS
            val surplusPct = (calRatio - 1.0) * 100.0

            if (proteinRatio < 0.6) {
                multiplier = 1.05
                factors.add("Superávit sin suficiente proteína. La síntesis muscular está limitada.")
            } else if (proteinRatio < 0.8) {
                multiplier = when {
                    surplusPct < 15  -> 0.92
                    surplusPct < 25  -> 0.88
                    else             -> 0.92
                }
                factors.add("Superávit moderado (~${surplusPct.toInt()}%) con proteína subóptima. Beneficio limitado.")
            } else {
                multiplier = when {
                    surplusPct < 8   -> 0.96
                    surplusPct < 18  -> 0.86
                    surplusPct < 30  -> 0.90
                    else             -> 0.96
                }
                when {
                    surplusPct < 8   -> factors.add("Superávit ligero (~${surplusPct.toInt()}%). Pequeña mejora en recuperación.")
                    surplusPct < 18  -> factors.add("Superávit óptimo (~${surplusPct.toInt()}%). Recuperación acelerada.")
                    surplusPct < 30  -> factors.add("Superávit alto (~${surplusPct.toInt()}%). Beneficio decreciente.")
                    else             -> factors.add("Superávit muy alto (~${surplusPct.toInt()}%). Rendimientos decrecientes; no acelera más.")
                }
            }

            if (stressLevel >= 4 && multiplier < 1.0) {
                multiplier = minOf(1.0, multiplier + 0.06)
                factors.add("Estrés elevado reduce parte del beneficio nutricional.")
            }
        }

        return NutritionRecoveryResult(
            recoveryTimeMultiplier = multiplier.coerceIn(0.6, 1.6),
            status = status,
            factors = factors,
        )
    }
}
