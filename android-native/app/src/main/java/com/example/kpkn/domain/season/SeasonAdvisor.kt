package com.example.kpkn.domain.season

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.competitions.WeighInPlanner

enum class SeasonAdviceCode { PEAK_NEEDS_MAINTENANCE, MEET_NEEDS_MAINTENANCE, DELOAD_IS_NOT_A_PAUSE, DEFICIT_CAPS_VOLUME, SURPLUS_ALLOWS_MORE, WEIGHT_CLASS_RISK, NO_NUTRITION_PLAN }
data class SeasonAdvice(val code: SeasonAdviceCode,val title: String,val detail: String,val severity: Int)
data class SeasonInput(val blockGoal: BlockGoal? = null,val nextBlockGoal: BlockGoal? = null,val currentWeekIsDeload: Boolean = false,
    val direction: PlanDirection? = null,val calorieTarget: Int? = null,val maintenanceKcal: Int? = null,
    val weeklyWeightRateKg: Double? = null,val daysToCompetition: Int? = null,val targetBodyweightKg: Double? = null,
    val currentBodyweightKg: Double? = null)

/** Contexto de planes independientes. Las notas no cambian calorías, gasto, registros ni sesiones. */
object SeasonAdvisor {
    const val DEEP_DEFICIT_FRACTION = 0.15
    fun deficitMode(input: SeasonInput): Boolean = input.direction == PlanDirection.DEFICIT &&
        input.calorieTarget?.let { it > 0 } == true && input.maintenanceKcal?.let { it > 0 } == true &&
        input.calorieTarget <= input.maintenanceKcal * (1 - DEEP_DEFICIT_FRACTION)

    fun advise(input: SeasonInput): List<SeasonAdvice> = buildList {
        fun note(code: SeasonAdviceCode,title: String,detail: String,severity: Int = 1) { add(SeasonAdvice(code,title,detail,severity)) }
        if (input.blockGoal in setOf(BlockGoal.PEAK,BlockGoal.TAPER,BlockGoal.REALIZATION) && input.direction == PlanDirection.DEFICIT)
            note(SeasonAdviceCode.PEAK_NEEDS_MAINTENANCE,"Pico y déficit declarados","Tu programa está en pico/taper y tu plan nutricional indica déficit. Puedes revisar si prefieres mantenimiento durante esta fase; ningún objetivo se cambia aquí.",2)
        if (input.daysToCompetition in 0..14 && input.direction == PlanDirection.DEFICIT &&
            (input.targetBodyweightKg == null || input.currentBodyweightKg?.let { it <= input.targetBodyweightKg } == true))
            note(SeasonAdviceCode.MEET_NEEDS_MAINTENANCE,"Competición próxima","Tu plan indica déficit a dos semanas o menos de competir. Revisa la dirección nutricional frente a tu objetivo; la elección sigue siendo tuya.",2)
        if (input.currentWeekIsDeload) note(SeasonAdviceCode.DELOAD_IS_NOT_A_PAUSE,"Semana de descarga","La descarga es parte del programa. La contabilidad y los check-ins nutricionales conservan sus propios registros; esta tarjeta no modifica calorías ni gasto.")
        if (deficitMode(input)) note(SeasonAdviceCode.DEFICIT_CAPS_VOLUME,"Contexto de déficit del plan","La diferencia entre el objetivo y el mantenimiento declarado es de al menos 15 %. Las propuestas semanales pueden usar el límite intermedio de planificación y RIR mínimo 2; siempre debes revisarlas y confirmarlas.")
        if (input.direction == PlanDirection.SURPLUS && input.blockGoal == BlockGoal.ACCUMULATION)
            note(SeasonAdviceCode.SURPLUS_ALLOWS_MORE,"Acumulación y superávit declarados","Puedes revisar las propuestas de volumen frente a tus objetivos de planificación. Un superávit no mide tu capacidad de recuperación ni autoriza más series por sí solo.")
        val current = input.currentBodyweightKg?.takeIf { it.isFinite() && it > 0 }
        val target = input.targetBodyweightKg?.takeIf { it.isFinite() && it > 0 }
        val days = input.daysToCompetition?.takeIf { it >= 0 }
        if (current != null && target != null && days != null) {
            val forecast = WeighInPlanner.forecast(current,target,input.weeklyWeightRateKg,days)
            if (!forecast.makesWeight) note(SeasonAdviceCode.WEIGHT_CLASS_RISK,"Comparación con el objetivo de peso",forecast.message,2)
        }
        if (input.direction == null) note(SeasonAdviceCode.NO_NUTRITION_PLAN,"Sin dirección nutricional disponible","Puedes consultar tu plan de nutrición para añadir contexto a esta temporada. El entrenamiento mantiene su planificación independiente.")
    }
}
