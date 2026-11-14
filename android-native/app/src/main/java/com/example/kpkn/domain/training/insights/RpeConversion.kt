package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.IntensityMode
import java.util.Locale

/**
 * El atleta registra RIR; la app habla en RPE por dentro.
 *
 * La conversión se calcula al leer y **nunca** reescribe el RIR guardado ni lo que el atleta ve
 * en su historial. Existe una única fórmula: `rpe = 10 - rir`.
 */
object RpeConversion {
    data class RecordedSessionEffort(
        val value: Double?,
        val coveredSets: Int,
        val totalSets: Int,
    ) {
        val coverageLabel: String
            get() = when {
                totalSets == 0 -> "Sin series de trabajo"
                value != null -> "${formatRpe(value)}/10 · $coveredSets de $totalSets series"
                else -> "Sin intensidad registrada · 0 de $totalSets series"
            }
    }

    fun fromRir(rir: Int): Double = (10 - rir).coerceIn(0, 10).toDouble()

    fun fromRirOrNull(rir: Int?): Double? = rir?.let(::fromRir)

    fun fromRir(rir: Double): Double = (10.0 - rir).coerceIn(0.0, 10.0)

    /** RPE declarado de la serie; si no existe, se deriva del RIR registrado. */
    fun setRpe(rpe: Double?, rir: Int?): Double? = rpe?.takeIf { it in 0.0..10.0 } ?: fromRirOrNull(rir)

    /** RPE de la sesión: el declarado o, si falta, la media de las series. */
    fun sessionRpe(declared: Double?, exercises: List<CompletedExercise>): Double? {
        return declared?.takeIf { it in 0.0..10.0 }
    }

    /**
     * Esfuerzo medio derivado de la intensidad realmente registrada por serie.
     * Promedia primero los lados de una serie unilateral y después las series de trabajo.
     * Una prescripción, un AMRAP sin intensidad o un fallo de ejecución sin dato no aportan.
     */
    fun recordedEffort(exercises: List<CompletedExercise>): RecordedSessionEffort {
        val logicalSets = linkedMapOf<String, MutableList<Double?>>()
        exercises.filter { it.cardioDetails == null }.forEachIndexed { exerciseIndex, exercise ->
            exercise.sets.filterNot(::isNonWorkingExecution).forEachIndexed { index, set ->
                val occurrence = exercise.occurrenceId?.takeIf { it.isNotBlank() } ?: "${exercise.exerciseId}@$exerciseIndex"
                val key = "$occurrence:${set.setIndex ?: index}"
                logicalSets.getOrPut(key) { mutableListOf() }.add(recordedSetEffort(set))
            }
        }
        val perSet = logicalSets.values.mapNotNull { values ->
            values.filterNotNull().takeIf { it.isNotEmpty() }?.average()
        }
        val covered = logicalSets.values.count { values -> values.any { it != null } }
        return RecordedSessionEffort(
            value = perSet.takeIf { it.isNotEmpty() }?.average(),
            coveredSets = covered,
            totalSets = logicalSets.size,
        )
    }

    private fun recordedSetEffort(set: CompletedSet): Double? {
        val payload = set.recordedPayloadV3
        val mode = set.actualIntensityMode ?: payload?.actualIntensityMode
        val value = set.actualIntensityValue ?: payload?.actualIntensityValue
        if (mode == IntensityMode.FAILURE) return 10.0
        if (set.isFailure || payload?.reachedFailure == true || set.homologatedResultV3?.reachedFailure == true ||
            set.setOutcomeV2?.reachedFailure == true
        ) {
            return 10.0
        }
        if (mode == IntensityMode.RPE) return value?.takeIf { it in 0.0..10.0 }
        if (mode == IntensityMode.RIR) return value?.let { (10.0 - it).coerceIn(0.0, 10.0) }
        return setRpe(set.rpe, set.rir)
    }

    private fun isNonWorkingExecution(set: CompletedSet): Boolean {
        val payload = set.recordedPayloadV3
        return set.skipped || set.isWarmup || set.isFailedSet || set.failureReason == "execution_error" ||
            payload?.skipped == true || payload?.failedSet == true || payload?.executionError == true ||
            set.homologatedResultV3?.failedSet == true || set.setOutcomeV2?.failedSet == true
    }

    private fun formatRpe(value: Double): String =
        String.format(Locale.US, "%.1f", value).removeSuffix(".0").replace('.', ',')

    /** Igual que [sessionRpe] pero a partir de las series ya filtradas por la pantalla. */
    fun averageSetRpe(sets: List<CompletedSet>): Double? = sets
        .filter { !it.skipped && !it.isWarmup }
        .mapNotNull { setRpe(it.rpe, it.rir) }
        .takeIf { it.isNotEmpty() }
        ?.average()
}
