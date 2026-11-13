package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.workout.CompletedSetLoadSemantics
import com.example.kpkn.domain.training.ExerciseProgress
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
    val exerciseId: String,
    val exerciseName: String,
    val metric: String,
    val value: Double,
    val previousValue: Double?,
    /** Delta is positive for every improvement, including lower assistance. */
    val improvement: Double?,
    val unit: String,
    val currentLabel: String,
    val previousLabel: String?,
    val isInitialReference: Boolean,
) {
    val summaryLine: String
        get() = if (isInitialReference) {
            currentLabel
        } else {
            "$currentLabel · +${finishGrouped(improvement ?: 0.0, 1)} $unit · antes ${previousLabel.orEmpty()}"
        }
}

private enum class FinishRecordMetric(val label: String, val unit: String, val lowerIsBetter: Boolean = false) {
    E1RM("e1RM", "kg"),
    BODYWEIGHT_REPS("Repeticiones", "reps"),
    BODYWEIGHT_TIME("Duración", "s"),
    LASTRE("Lastre", "kg"),
    ASSISTANCE("Asistencia", "kg", lowerIsBetter = true),
    CARDIO_DISTANCE("Distancia", "km"),
    CARDIO_TIME("Tiempo", "s"),
}

private data class FinishRecordKey(
    val exercise: String,
    val metric: FinishRecordMetric,
    val mode: LoadModeV2?,
    val unitMode: UnitModeV2?,
    val context: List<String>,
    val reps: Int? = null,
)

private data class FinishRecordObservation(
    val key: FinishRecordKey,
    val exerciseId: String,
    val exerciseName: String,
    val value: Double,
    val reps: Int,
)

private fun finishRecordExerciseKey(exercise: CompletedExercise): String = listOf(
    ExerciseProgress.identity(exercise),
    exercise.variantName.orEmpty(),
    exercise.selectedAspects.orEmpty().toSortedMap().toString(),
    exercise.performanceProfileId.orEmpty(),
).joinToString("|")

private fun finishRecordContext(set: CompletedSet): List<String> = listOf(
    set.contextKey.orEmpty(),
    set.recordedPayloadV3?.contextProfileId.orEmpty(),
    set.homologatedResultV3?.contextKey.orEmpty(),
    set.setOutcomeV2?.contextKey.orEmpty(),
    set.setupProfileId.orEmpty(),
    set.machineBrand.orEmpty(),
    set.tagId.orEmpty(),
    set.subTagIds.sorted().joinToString(","),
    (set.side ?: set.recordedPayloadV3?.side).orEmpty(),
)

private fun finishRecordUnit(set: CompletedSet): UnitModeV2 =
    set.recordedPayloadV3?.unitMode
        ?: set.homologatedResultV3?.unitMode
        ?: set.setOutcomeV2?.unitMode
        ?: when {
            set.distanceKm != null -> UnitModeV2.DISTANCE
            set.timeSeconds != null && set.reps <= 0 -> UnitModeV2.TIME
            else -> UnitModeV2.REPS
        }

private fun recordObservations(exercise: CompletedExercise, set: CompletedSet): List<FinishRecordObservation> {
    if (set.skipped || set.isWarmup || set.isFailedSet || set.recordedPayloadV3?.skipped == true ||
        set.recordedPayloadV3?.failedSet == true || set.homologatedResultV3?.failedSet == true || set.setOutcomeV2?.failedSet == true
    ) return emptyList()
    val mode = CompletedSetLoadSemantics.mode(set)
    val unit = finishRecordUnit(set)
    val exerciseKey = finishRecordExerciseKey(exercise)
    val context = finishRecordContext(set)
    val base = { metric: FinishRecordMetric, reps: Int? ->
        FinishRecordKey(exerciseKey, metric, mode, unit, context, reps)
    }
    fun observation(metric: FinishRecordMetric, value: Double, reps: Int = set.reps, repsKey: Int? = null) =
        FinishRecordObservation(base(metric, repsKey), exercise.exerciseId, exercise.exerciseName, value, reps)

    if (exercise.cardioDetails != null) return buildList {
        set.distanceKm?.takeIf { it.isFinite() && it > 0.0 }?.let { add(observation(FinishRecordMetric.CARDIO_DISTANCE, it)) }
        set.timeSeconds?.takeIf { it > 0 }?.let { add(observation(FinishRecordMetric.CARDIO_TIME, it.toDouble())) }
    }

    return when (mode) {
        LoadModeV2.BODYWEIGHT -> when (unit) {
            UnitModeV2.TIME -> set.timeSeconds?.takeIf { it > 0 }
                ?.let { listOf(observation(FinishRecordMetric.BODYWEIGHT_TIME, it.toDouble())) }.orEmpty()
            UnitModeV2.REPS -> set.reps.takeIf { it > 0 }
                ?.let { listOf(observation(FinishRecordMetric.BODYWEIGHT_REPS, it.toDouble())) }.orEmpty()
            else -> emptyList()
        }
        LoadModeV2.LASTRE -> {
            val load = CompletedSetLoadSemantics.externalLoadKg(set)
            if (set.reps > 0 && load != null) listOf(observation(FinishRecordMetric.LASTRE, load, repsKey = set.reps)) else emptyList()
        }
        LoadModeV2.ASSISTED -> {
            val load = CompletedSetLoadSemantics.assistanceKg(set)
            if (set.reps > 0 && load != null) listOf(observation(FinishRecordMetric.ASSISTANCE, load, repsKey = set.reps)) else emptyList()
        }
        LoadModeV2.LOAD, null -> {
            val load = CompletedSetLoadSemantics.externalLoadKg(set)
            val e1rm = StrengthModel.estimate1RM(set)
            if (set.reps > 0 && load != null && load > 0.0 && e1rm.isFinite() && e1rm > 0.0)
                listOf(observation(FinishRecordMetric.E1RM, e1rm))
            else emptyList()
        }
    }
}

/** Builds current marks from the corrected sets and compares them with the best exact historical match. */
internal fun buildFinishRecords(
    completedExercises: List<CompletedExercise>,
    history: List<WorkoutLog>,
): List<FinishRecord> {
    val current = completedExercises.flatMap { exercise -> exercise.sets.flatMap { recordObservations(exercise, it) } }
        .groupBy { it.key }
        .mapNotNull { (_, rows) ->
            val first = rows.first()
            val metric = first.key.metric
            val best = if (metric.lowerIsBetter) rows.minByOrNull { it.value } else rows.maxByOrNull { it.value }
            best
        }
    if (current.isEmpty()) return emptyList()
    val previous = history.flatMap { log ->
        log.completedExercises.flatMap { exercise -> exercise.sets.flatMap { recordObservations(exercise, it) } }
    }.groupBy { it.key }

    return current.mapNotNull { now ->
        val metric = now.key.metric
        val beforeRows = previous[now.key].orEmpty()
        val before = if (beforeRows.isEmpty()) null else if (metric.lowerIsBetter) {
            beforeRows.minByOrNull { it.value }
        } else {
            beforeRows.maxByOrNull { it.value }
        }
        val improved = before == null || if (metric.lowerIsBetter) now.value < before.value - 1e-6 else now.value > before.value + 1e-6
        if (!improved) return@mapNotNull null
        fun label(value: Double, reps: Int): String = when (metric) {
            FinishRecordMetric.E1RM -> "${finishGrouped(value, 1)} kg e1RM"
            FinishRecordMetric.BODYWEIGHT_REPS -> "$reps reps · peso corporal"
            FinishRecordMetric.BODYWEIGHT_TIME -> "${finishGrouped(value, 0)} s · peso corporal"
            FinishRecordMetric.LASTRE -> "${finishGrouped(value, 1)} kg lastre × $reps reps"
            FinishRecordMetric.ASSISTANCE -> "${finishGrouped(value, 1)} kg asistencia × $reps reps"
            FinishRecordMetric.CARDIO_DISTANCE -> "${finishGrouped(value, 2)} km"
            FinishRecordMetric.CARDIO_TIME -> "${finishGrouped(value, 0)} s"
        }
        val difference = before?.let { kotlin.math.abs(now.value - it.value) }
        FinishRecord(
            exerciseId = now.exerciseId,
            exerciseName = now.exerciseName,
            metric = metric.label,
            value = now.value,
            previousValue = before?.value,
            improvement = difference,
            unit = metric.unit,
            currentLabel = label(now.value, now.reps),
            previousLabel = before?.let { label(it.value, it.reps) },
            isInitialReference = before == null,
        )
    }.sortedWith(compareByDescending<FinishRecord> { !it.isInitialReference }.thenByDescending { it.improvement ?: 0.0 }.thenBy { it.exerciseName })
}

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
    finishRecords: List<FinishRecord>? = null,
): FinishModel {
    val strength = completedExercises.filter { it.cardioDetails == null }
    val sets = logicalSetCountFromCompleted(strength)
    val recordedEffort = com.example.kpkn.domain.training.insights.RpeConversion.recordedEffort(completedExercises)
    val volume = sessionTonnage(strength)
    // Historical tonnage is recomputed from the recorded sets with the
    // modality-correct reading; legacy totalVolume may mix synthetic body
    // mass and must not seed the trend.
    val previousSessions = presentation.history.filter { it.sessionName == name }.sortedBy { it.date }
    val previousSessionVolumes = previousSessions.map { sessionTonnage(it.completedExercises.filter { ex -> ex.cardioDetails == null }) }
    val previous = previousSessions.lastOrNull()?.let { previousSessions.lastIndex to previousSessionVolumes.last() }
    val trend = (previousSessionVolumes.takeLast(7) + volume).takeIf { sets > 0 && it.size >= 2 } ?: emptyList()
    val direct = MuscleVolumeAccounting.sessionDirectSets(strength)
    val muscles = MuscleVolumeAccounting.sessionSets(strength).entries.sortedByDescending { it.value }
        .map { FinishMuscle(it.key, it.value, direct[it.key] ?: 0.0) }
    val chips = lifts.filter {
        val best = it.best
        best != null && it.previous != null &&
            com.example.kpkn.domain.workout.CompletedSetLoadSemantics.supportsEstimatedOneRm(best)
    }
        .sortedByDescending { it.best?.let { s -> StrengthModel.estimate1RM(s) } ?: 0.0 }
        .map { FinishLiftChip(it.name, it.deltaKg?.let { d -> if (abs(d) < 0.01) 0.0 else d }) }
        .filter { it.deltaKg != null }
        // Primero las que mejoran (de mayor a menor), después las que se mantienen.
        .sortedByDescending { it.deltaKg }
        .take(2)
    val recordList = finishRecords ?: buildFinishRecords(completedExercises, presentation.history)
    val record = recordList.firstOrNull()
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
        volumeDelta = previous?.let { volume - it.second }.takeIf { sets > 0 },
        trend = trend,
        effort = effort,
        recordedEffort = recordedEffort,
        records = recordList.count { !it.isInitialReference },
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
