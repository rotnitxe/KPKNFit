package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import java.time.LocalDate

/** Procedencia del objetivo canónico resuelto para una fecha. */
enum class NutritionGoalSource {
    /** Evidencia histórica ya fijada para la fecha. */
    DAILY_SNAPSHOT,

    /** Previsión vigente usada como meta del día (hoy). */
    TODAY_FORECAST,

    /** Previsión del plan para una fecha futura. */
    PLAN_FORECAST,
}

/**
 * Objetivo canónico de un día. Un campo nulo es ausencia/desconocido; un 0
 * explícito es un valor legítimo y viaja como 0.
 */
sealed interface NutritionDayGoal {
    /** Objetivo numérico vigente para la fecha. */
    data class PlanDayTarget(
        val calorieTargetKcal: Int?,
        val proteinGoalG: Int?,
        val carbGoalG: Int?,
        val fatGoalG: Int?,
        val direction: PlanDirection?,
        val calculationOrigin: CalculationOrigin,
        val planId: String? = null,
        val source: NutritionGoalSource,
    ) : NutritionDayGoal {
        /** true si queda alguna evidencia numérica; los ceros cuentan como valor. */
        fun hasGoalValues(): Boolean =
            calorieTargetKcal != null || proteinGoalG != null || carbGoalG != null || fatGoalG != null
    }

    /** Día sin metas numéricas: solo seguimiento. */
    object TrackingOnly : NutritionDayGoal

    /** Ausencia explícita de objetivo (pasado sin evidencia). Nunca un default. */
    object NoGoal : NutritionDayGoal
}

data class NutritionGoalResolution(
    val goal: NutritionDayGoal,
    /**
     * Snapshot histórico que queda fijado para la fecha, o null cuando no hay
     * evidencia que fijar: la ausencia histórica se conserva tal cual.
     */
    val fixSnapshot: DailyGoalSnapshot? = null,
)

/**
 * Resolución del objetivo canónico por fecha. Orden exacto:
 *
 * 1. [DailyGoalSnapshot] de la fecha si existe (histórico inmutable).
 * 2. Para hoy: previsión vigente, que antes de usarse como meta del día queda
 *    fijada como snapshot.
 * 3. Para futuro: previsión del plan (aún sin fijar).
 * 4. Para pasado sin snapshot: [NutritionDayGoal.NoGoal], ausencia explícita.
 *    NUNCA el plan activo actual.
 *
 * Un snapshot sin ningún valor numérico denota un día de solo seguimiento
 * ([NutritionDayGoal.TrackingOnly]).
 */
object NutritionGoalResolver {
    fun resolve(
        date: LocalDate,
        today: LocalDate = LocalDate.now(),
        snapshot: DailyGoalSnapshot? = null,
        todayForecast: NutritionDayGoal.PlanDayTarget? = null,
        planForecast: NutritionDayGoal.PlanDayTarget? = null,
        trackingOnly: Boolean = false,
        capturedAtEpochMs: Long = 0L,
    ): NutritionGoalResolution {
        // 1. La evidencia fijada de la fecha manda siempre: es histórica e inmutable.
        if (snapshot != null) {
            val snapshotGoal = NutritionDayGoal.PlanDayTarget(
                calorieTargetKcal = snapshot.calorieTargetKcal,
                proteinGoalG = snapshot.proteinGoalG,
                carbGoalG = snapshot.carbGoalG,
                fatGoalG = snapshot.fatGoalG,
                direction = snapshot.direction,
                calculationOrigin = snapshot.calculationOrigin,
                planId = snapshot.planId,
                source = NutritionGoalSource.DAILY_SNAPSHOT,
            )
            val goal = if (snapshotGoal.hasGoalValues()) snapshotGoal else NutritionDayGoal.TrackingOnly
            return NutritionGoalResolution(goal = goal, fixSnapshot = null)
        }
        // 4. Pasado sin snapshot: ausencia explícita. NUNCA el plan activo actual.
        if (date.isBefore(today)) {
            return NutritionGoalResolution(goal = NutritionDayGoal.NoGoal, fixSnapshot = null)
        }
        // Modo de solo seguimiento: sin metas que fijar ni fabricar.
        if (trackingOnly) {
            return NutritionGoalResolution(goal = NutritionDayGoal.TrackingOnly, fixSnapshot = null)
        }
        return when {
            // 2. Hoy: la previsión vigente queda fijada antes de usarse como meta del día.
            date == today -> {
                if (todayForecast == null) {
                    NutritionGoalResolution(goal = NutritionDayGoal.NoGoal, fixSnapshot = null)
                } else {
                    val goal = todayForecast.copy(source = NutritionGoalSource.TODAY_FORECAST)
                    NutritionGoalResolution(
                        goal = goal,
                        fixSnapshot = dailyGoalSnapshotOf(goal, date, capturedAtEpochMs),
                    )
                }
            }
            // 3. Futuro: previsión del plan, aún sin fijar.
            else -> NutritionGoalResolution(
                goal = planForecast?.copy(source = NutritionGoalSource.PLAN_FORECAST) ?: NutritionDayGoal.NoGoal,
                fixSnapshot = null,
            )
        }
    }
}

/**
 * Objetivo proyectado por un plan, sin fabricar valores: los ceros explícitos
 * del plan viajan como 0 y solo la ausencia real se expresa como null.
 */
fun planDayTargetOf(plan: NutritionPlan, source: NutritionGoalSource): NutritionDayGoal.PlanDayTarget =
    NutritionDayGoal.PlanDayTarget(
        calorieTargetKcal = plan.calorieTarget,
        proteinGoalG = plan.proteinGoal,
        carbGoalG = plan.carbGoal,
        fatGoalG = plan.fatGoal,
        direction = plan.direction,
        calculationOrigin = plan.calculationOrigin,
        planId = plan.id,
        source = source,
    )

/**
 * Snapshot histórico derivado de un objetivo resuelto. Los ceros explícitos
 * persisten como ceros; null significa ausencia/desconocido.
 */
fun dailyGoalSnapshotOf(
    target: NutritionDayGoal.PlanDayTarget,
    date: LocalDate,
    capturedAtEpochMs: Long,
): DailyGoalSnapshot =
    DailyGoalSnapshot(
        date = date.toString(),
        planId = target.planId,
        calorieTargetKcal = target.calorieTargetKcal,
        proteinGoalG = target.proteinGoalG,
        carbGoalG = target.carbGoalG,
        fatGoalG = target.fatGoalG,
        direction = target.direction,
        calculationOrigin = target.calculationOrigin,
        capturedAtEpochMs = capturedAtEpochMs,
    )
