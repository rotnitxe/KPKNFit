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

internal data class FinishRecord(
    val exerciseName: String,
    val e1rm: Double,
    val previousE1rm: Double?,
    val kind: String = "e1RM",
)

internal data class FinishCardio(
    val type: String,
    val minutes: Int?,
    val km: Double?,
    val heartRate: Int?,
    val kcal: Int?,
    val blockCount: Int = 1,
    val seconds: Int? = null,
) {
    val durationFormatted: Pair<String, String>?
        get() = when {
            seconds != null && seconds in 1..59 -> "$seconds" to "s"
            minutes != null && minutes > 0 -> "$minutes" to "min"
            seconds != null && seconds >= 60 -> "${seconds / 60}" to "min"
            else -> null
        }
}

internal data class FinishModel(
    val name: String,
    val dateLine: String,
    val minutes: Int,
    val durationSeconds: Int = minutes * 60,
    val sets: Int,
    val plannedSets: Int,
    val volume: Double,
    val volumeDelta: Double?,
    /** Volumen de las últimas sesiones iguales (hasta 7) y, al final, el de hoy. Sin comparación (< 2 puntos) no se dibuja la línea. */
    val trend: List<Double>,
    val effort: Double?,
    /** Esfuerzo medio derivado de la intensidad realmente registrada; la valoración manual vive aparte. */
    val recordedEffort: com.example.kpkn.domain.training.insights.RpeConversion.RecordedSessionEffort =
        com.example.kpkn.domain.training.insights.RpeConversion.RecordedSessionEffort(null, 0, 0),
    val records: Int,
    val record: FinishRecord?,
    val recordList: List<FinishRecord> = emptyList(),
    val cardio: FinishCardio?,
    val muscles: List<FinishMuscle>,
    val lifts: List<FinishLiftChip>,
    val liftProgress: List<FinishLiftProgress> = emptyList(),
    val targetMinutes: Int?,
    val empty: Boolean,
) {
    /** Vuelta de reloj de 60 min, o el avance hacia el objetivo de tiempo si lo había (decisión aprobada «En vivo · más»). */
    val durationFraction: Float
        get() = if (empty) 0f else ((durationSeconds / 60f) / (targetMinutes?.takeIf { it > 0 } ?: 60)).coerceIn(0f, 1f)
    val setsFraction: Float get() = if (plannedSets > 0) (sets.toFloat() / plannedSets).coerceIn(0f, 1f) else 0f
    val effortFraction: Float get() = ((recordedEffort.value ?: 0.0) / 10.0).toFloat().coerceIn(0f, 1f)
}

internal fun finishDurationLabel(durationSeconds: Int, durationMinutes: Int): String =
    if (durationSeconds in 1..59) "${durationSeconds} s" else "${durationMinutes} min"

internal fun finishActivityLabel(model: FinishModel): String = when {
    model.empty -> "sin actividad"
    model.sets > 0 && model.cardio != null -> "${model.sets} series · ${model.cardio.blockCount} bloques de cardio"
    model.sets > 0 -> "${model.sets} series"
    model.cardio != null -> if (model.cardio.blockCount == 1) "cardio" else "${model.cardio.blockCount} bloques de cardio"
    else -> "sin actividad"
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
    durationSeconds: Int = durationMinutes * 60,
): FinishModel {
    val strength = completedExercises.filter { it.cardioDetails == null }
    val sets = logicalSetCountFromCompleted(strength)
    val recordedEffort = com.example.kpkn.domain.training.insights.RpeConversion.recordedEffort(completedExercises)
    val volume = sessionTonnage(strength)
    val previousSessions = presentation.history.filter { it.sessionName == name }.sortedBy { it.date }
    val previous = previousSessions.lastOrNull()
    val trend = (previousSessions.takeLast(7).map { it.totalVolume } + volume).takeIf { sets > 0 && it.size >= 2 } ?: emptyList()
    val direct = MuscleVolumeAccounting.sessionDirectSets(strength)
    val muscles = MuscleVolumeAccounting.sessionSets(strength).entries.sortedByDescending { it.value }
        .map { FinishMuscle(it.key, it.value, direct[it.key] ?: 0.0) }
    val chips = lifts.filter { it.best != null && it.previous != null }
        .sortedByDescending { it.best?.let { s -> StrengthModel.estimate1RM(s) } ?: 0.0 }
        .map { FinishLiftChip(it.name, it.deltaKg?.let { d -> if (abs(d) < 0.01) 0.0 else d }) }
        .filter { it.deltaKg != null }
        // Primero las que mejoran (de mayor a menor), después las que se mantienen.
        .sortedByDescending { it.deltaKg }
        .take(2)
    val milestone = presentation.milestones.maxByOrNull { it.value }
    val recordList = presentation.milestones
        .groupBy { it.exerciseId to it.kind }
        .values.mapNotNull { group -> group.maxByOrNull { it.value } }
        .sortedByDescending { it.value }
        .map { m ->
            FinishRecord(
                exerciseName = m.exerciseName,
                e1rm = m.value,
                previousE1rm = lifts.firstOrNull { it.exerciseId == m.exerciseId }?.previous?.estimated1Rm,
                kind = m.kind,
            )
        }
    val record = recordList.firstOrNull() ?: milestone?.let { m ->
        FinishRecord(m.exerciseName, m.value, lifts.firstOrNull { it.exerciseId == m.exerciseId }?.previous?.estimated1Rm)
    }
    val cardioBlocks = completedExercises.mapNotNull { exercise ->
        val sets = exercise.sets.filter { !it.skipped }
        if (exercise.cardioDetails == null || sets.isEmpty()) null else exercise to sets
    }
    val cardio = cardioBlocks.takeIf { it.isNotEmpty() }?.let { blocks ->
        val sets = blocks.flatMap { it.second }
        val seconds = sets.sumOf { it.timeSeconds ?: 0 }
        val km = sets.sumOf { it.distanceKm ?: 0.0 }
        val hrSamples = sets.filter { it.avgHeartRate != null }
        val hrCoverage = hrSamples.filter { (it.timeSeconds ?: 0) > 0 }
        val hr = when {
            hrCoverage.isNotEmpty() -> {
                val coveredSeconds = hrCoverage.sumOf { it.timeSeconds!!.toLong() }
                (hrCoverage.sumOf { it.avgHeartRate!!.toLong() * it.timeSeconds!!.toLong() } / coveredSeconds).toInt()
            }
            hrSamples.isNotEmpty() -> hrSamples.mapNotNull { it.avgHeartRate }.average().toInt()
            else -> null
        }
        val kcal = sets.sumOf { it.calories ?: 0.0 }
        FinishCardio(
            type = blocks.map { it.first.exerciseName }.distinct().joinToString(" + "),
            minutes = (seconds / 60).takeIf { it > 0 },
            km = km.takeIf { it > 0.0 },
            heartRate = hr,
            kcal = kcal.toInt().takeIf { kcal > 0.0 },
            blockCount = blocks.size,
            seconds = seconds.takeIf { it > 0 },
        )
    }
    return FinishModel(
        name = name,
        dateLine = finishDateLabel(presentation.today),
        minutes = durationMinutes,
        durationSeconds = durationSeconds.coerceAtLeast(0),
        sets = sets,
        plannedSets = (plannedSets ?: sets).coerceAtLeast(sets),
        volume = volume,
        volumeDelta = previous?.let { volume - it.totalVolume }.takeIf { sets > 0 },
        trend = trend,
        effort = effort,
        recordedEffort = recordedEffort,
        records = presentation.records,
        record = record,
        recordList = recordList,
        cardio = cardio,
        muscles = if (strength.isEmpty()) emptyList() else muscles,
        lifts = chips,
        liftProgress = lifts,
        targetMinutes = targetMinutes,
        empty = isFinishBlockedForEmptySession(completedExercises),
    )
}
