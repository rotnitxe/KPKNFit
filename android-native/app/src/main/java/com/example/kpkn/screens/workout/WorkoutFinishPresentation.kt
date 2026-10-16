package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.auge.AugeUtils
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** Datos ya cargados en memoria para el cierre; no consulta disco ni modifica registros. */
internal data class FinishPresentationContext(
    val program: Program? = null,
    val weekNumber: Int? = null,
    val history: List<WorkoutLog> = emptyList(),
    val photos: List<StoryPhoto> = emptyList(),
    val calories: Int? = null,
    val records: Int = 0,
    val today: LocalDate = LocalDate.now(),
)

internal data class StoryPhoto(val path: String, val exerciseName: String? = null, val detail: String? = null)
internal data class FinishProgressPoint(val date: String, val weight: Double, val reps: Int, val estimated1Rm: Double)
internal data class FinishLiftProgress(
    val exerciseId: String,
    val name: String,
    val best: CompletedSet?,
    val setCount: Int,
    val previous: FinishProgressPoint?,
    val history: List<FinishProgressPoint>,
    val totalHistorySessions: Int = history.size,
    val first: FinishProgressPoint? = history.firstOrNull(),
) {
    val deltaKg: Double? get() = if (best != null && previous != null) best.weight - previous.weight else null
    val isImproved: Boolean get() = deltaKg?.let { it > 0.01 } == true
}

/** Configuraciones distintas nunca comparten la gráfica; en históricos sin configuración se conserva la identidad/aspectos. */
private fun sameFinishExercise(a: CompletedExercise, b: CompletedExercise): Boolean {
    val aConfig = a.catalogConfigurationId?.takeIf { it.isNotBlank() }
    val bConfig = b.catalogConfigurationId?.takeIf { it.isNotBlank() }
    if (aConfig != null || bConfig != null) return aConfig != null && aConfig == bConfig
    return a.resolvedCanonicalExerciseId() == b.resolvedCanonicalExerciseId() && a.selectedAspects.orEmpty() == b.selectedAspects.orEmpty()
}

private fun bestFinishSet(exercise: CompletedExercise): CompletedSet? = exercise.takeIf { it.cardioDetails == null }?.sets.orEmpty()
    .filter { !it.skipped && !it.isWarmup && !it.isFailedSet && it.reps > 0 && it.weight.isFinite() && it.weight >= 0 }
    .maxWithOrNull(compareBy<CompletedSet> { calculateHybrid1RM(it.weight, it.reps) }.thenBy { it.weight }.thenBy { it.reps })

internal fun buildFinishLiftProgress(completed: List<CompletedExercise>, history: List<WorkoutLog>): List<FinishLiftProgress> =
    completed.map { exercise ->
        val points = history.sortedBy { AugeUtils.logDateMs(it) }.mapNotNull { log ->
            val best = log.completedExercises.filter { sameFinishExercise(exercise, it) }
                .mapNotNull(::bestFinishSet)
                .maxByOrNull { calculateHybrid1RM(it.weight, it.reps) } ?: return@mapNotNull null
            FinishProgressPoint(log.date, best.weight, best.reps, calculateHybrid1RM(best.weight, best.reps))
        }
        FinishLiftProgress(exercise.exerciseId, exercise.exerciseName, bestFinishSet(exercise),
            logicalSetCountFromCompleted(listOf(exercise)), points.lastOrNull(), points.takeLast(24), points.size, points.firstOrNull())
    }

internal fun finishAverageRir(completed: List<CompletedExercise>): Double? {
    val values = completed.flatMap { it.sets }.filter { !it.skipped && !it.isWarmup }.mapNotNull { set ->
        // `isFailure` viene de reachedFailure, no de la prescripción. El modo FAILURE
        // también puede heredarse del plan y no prueba por sí mismo que se alcanzara el fallo.
        val rir = when {
            set.isFailure -> 0.0
            set.actualIntensityMode == IntensityMode.RIR -> set.actualIntensityValue ?: set.rir?.toDouble()
            set.actualIntensityMode == IntensityMode.RPE -> (set.actualIntensityValue ?: set.rpe)
                ?.takeIf { it in 0.0..10.0 }?.let { 10.0 - it }
            set.actualIntensityMode != null -> null
            // Compatibilidad con registros anteriores que aún no tenían el modo explícito.
            set.rir != null -> set.rir.toDouble()
            else -> set.rpe?.takeIf { it in 0.0..10.0 }?.let { 10.0 - it }
        }
        // La rueda permite RIR >10 según el objetivo. No recortar ni descartar ese dato real.
        rir?.takeIf { it.isFinite() && it >= 0.0 }
    }
    return values.takeIf { it.isNotEmpty() }?.average()
}

internal fun finishNumber(value: Double, decimals: Int = 1): String = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL")).apply {
    maximumFractionDigits = decimals
    minimumFractionDigits = 0
}.format(value).replace('.', ' ')

internal fun finishSetLabel(set: CompletedSet?): String = when {
    set == null -> "Sin serie de fuerza comparable"
    set.weight == 0.0 -> "${set.reps} reps · peso corporal"
    else -> "${finishNumber(set.weight)} kg × ${set.reps}"
}

internal fun finishDeltaLabel(delta: Double?): String = when {
    delta == null -> "Sin registro previo"
    kotlin.math.abs(delta) < 0.01 -> "="
    delta > 0 -> "+${finishNumber(delta)} kg"
    else -> "${finishNumber(delta)} kg"
}
