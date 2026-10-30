package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.domain.training.insights.MuscleVolumeAccounting
import java.time.LocalDate
import kotlin.math.abs

/*
 * Datos de la hoja «Sesión completada» (Pulido v1 · punto 18, escenas `lx-f-*`). Todo se calcula con lo que el cierre ya tenía
 * (`CompletedExercise`, `FinishPresentationContext`); aquí solo se ordena para dibujarlo SIN scroll: una línea de volumen con su
 * tendencia, cuatro anillos, músculos con barras, mejoras como etiquetas, récord y cardio si los hubo.
 */

internal data class FinishMuscle(val name: String, val total: Double, val direct: Double)

internal data class FinishLiftChip(val name: String, val deltaKg: Double?)

internal data class FinishRecord(val exerciseName: String, val e1rm: Double, val previousE1rm: Double?)

internal data class FinishCardio(val type: String, val minutes: Int?, val km: Double?, val heartRate: Int?, val kcal: Int?)

internal data class FinishModel(
    val name: String,
    val dateLine: String,
    val minutes: Int,
    val sets: Int,
    val plannedSets: Int,
    val volume: Double,
    val volumeDelta: Double?,
    /** Volumen de las últimas sesiones iguales (hasta 7) y, al final, el de hoy. Sin comparación (< 2 puntos) no se dibuja la línea. */
    val trend: List<Double>,
    val effort: Double?,
    val records: Int,
    val record: FinishRecord?,
    val cardio: FinishCardio?,
    val muscles: List<FinishMuscle>,
    val lifts: List<FinishLiftChip>,
    val targetMinutes: Int?,
    val empty: Boolean,
) {
    /** Vuelta de reloj de 60 min, o el avance hacia el objetivo de tiempo si lo había (decisión aprobada «En vivo · más»). */
    val durationFraction: Float
        get() = if (empty) 0f else (minutes.toFloat() / (targetMinutes?.takeIf { it > 0 } ?: 60)).coerceIn(0f, 1f)
    val setsFraction: Float get() = if (plannedSets > 0) (sets.toFloat() / plannedSets).coerceIn(0f, 1f) else 0f
    val effortFraction: Float get() = ((effort ?: 0.0) / 10.0).toFloat().coerceIn(0f, 1f)
}

/** Los tres niveles de degradación sin scroll (alto útil ÷ escala de texto): A ≥ 760, B 600–759, C < 600. */
internal enum class FinishTier { A, B, C }

/** `lxFinTier`: alto de la hoja (94 % de la pantalla) entre la escala del texto; récord, cardio o error bajan un nivel desde B. */
internal fun finishTierFor(screenHeightDp: Int, fontScale: Float, heavy: Boolean): FinishTier {
    val effective = screenHeightDp * 0.94f / fontScale.coerceAtLeast(0.5f)
    val tier = when { effective >= 760f -> FinishTier.A; effective >= 600f -> FinishTier.B; else -> FinishTier.C }
    return if (tier == FinishTier.B && heavy) FinishTier.C else tier
}

private val Days = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
private val Months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** «Mié 29 oct». */
internal fun finishDateLabel(date: LocalDate): String = "${Days[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${Months[date.monthValue - 1]}"

/** Un número con punto de millar y coma decimal («8.420», «7,5»); los decimales sobrantes en cero no se escriben. Independiente del idioma del teléfono. */
internal fun finishGrouped(value: Double, decimals: Int = 0): String {
    val raw = String.format(java.util.Locale.US, "%,." + decimals + "f", value)
    val trimmed = if (decimals > 0) raw.trimEnd('0').trimEnd('.') else raw
    return trimmed.replace(',', '#').replace('.', ',').replace('#', '.')
}

internal fun buildFinishModel(
    name: String,
    completedExercises: List<CompletedExercise>,
    durationMinutes: Int,
    effort: Double?,
    presentation: FinishPresentationContext,
    lifts: List<FinishLiftProgress>,
    plannedSets: Int?,
    targetMinutes: Int?,
): FinishModel {
    val strength = completedExercises.filter { it.cardioDetails == null }
    val sets = logicalSetCountFromCompleted(strength)
    val volume = sessionTonnage(strength)
    val previousSessions = presentation.history.filter { it.sessionName == name }.sortedBy { it.date }
    val previous = previousSessions.lastOrNull()
    val trend = (previousSessions.takeLast(7).map { it.totalVolume } + volume).takeIf { sets > 0 && it.size >= 2 } ?: emptyList()
    val direct = MuscleVolumeAccounting.sessionDirectSets(strength)
    val muscles = MuscleVolumeAccounting.sessionSets(strength).entries.sortedByDescending { it.value }.take(5)
        .map { FinishMuscle(it.key, it.value, direct[it.key] ?: 0.0) }
    val chips = lifts.filter { it.best != null && it.previous != null }
        .sortedByDescending { it.best?.let { s -> StrengthModel.estimate1RM(s) } ?: 0.0 }
        .map { FinishLiftChip(it.name, it.deltaKg?.let { d -> if (abs(d) < 0.01) 0.0 else d }) }
        .filter { it.deltaKg != null }
        // Primero las que mejoran (de mayor a menor), después las que se mantienen.
        .sortedByDescending { it.deltaKg }
        .take(2)
    val milestone = presentation.milestones.maxByOrNull { it.value }
    val record = milestone?.let { m ->
        FinishRecord(m.exerciseName, m.value, lifts.firstOrNull { it.exerciseId == m.exerciseId }?.previous?.estimated1Rm)
    }
    val cardio = completedExercises.firstOrNull { it.cardioDetails != null }?.let { ex ->
        val sets = ex.sets.filter { !it.skipped }
        val seconds = sets.sumOf { it.timeSeconds ?: 0 }
        val km = sets.sumOf { it.distanceKm ?: 0.0 }
        val hr = sets.mapNotNull { it.avgHeartRate }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val kcal = sets.sumOf { it.calories ?: 0.0 }
        FinishCardio(
            type = ex.exerciseName,
            minutes = (seconds / 60).takeIf { seconds > 0 },
            km = km.takeIf { it > 0.0 },
            heartRate = hr,
            kcal = kcal.toInt().takeIf { kcal > 0.0 },
        )
    }
    return FinishModel(
        name = name,
        dateLine = finishDateLabel(presentation.today),
        minutes = durationMinutes,
        sets = sets,
        plannedSets = (plannedSets ?: sets).coerceAtLeast(sets),
        volume = volume,
        volumeDelta = previous?.let { volume - it.totalVolume }.takeIf { sets > 0 },
        trend = trend,
        effort = effort,
        records = presentation.records,
        record = record,
        cardio = cardio,
        muscles = if (strength.isEmpty()) emptyList() else muscles,
        lifts = chips,
        targetMinutes = targetMinutes,
        empty = isFinishBlockedForEmptySession(completedExercises),
    )
}
