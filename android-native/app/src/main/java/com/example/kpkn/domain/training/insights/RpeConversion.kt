package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet

/**
 * El atleta registra RIR; la app habla en RPE por dentro.
 *
 * La conversión se calcula al leer y **nunca** reescribe el RIR guardado ni lo que el atleta ve
 * en su historial. Existe una única fórmula: `rpe = 10 - rir`.
 */
object RpeConversion {
    fun fromRir(rir: Int): Double = (10 - rir).coerceIn(0, 10).toDouble()

    fun fromRirOrNull(rir: Int?): Double? = rir?.let(::fromRir)

    fun fromRir(rir: Double): Double = (10.0 - rir).coerceIn(0.0, 10.0)

    /** RPE declarado de la serie; si no existe, se deriva del RIR registrado. */
    fun setRpe(rpe: Double?, rir: Int?): Double? = rpe?.takeIf { it in 0.0..10.0 } ?: fromRirOrNull(rir)

    /** RPE de la sesión: el declarado o, si falta, la media de las series. */
    fun sessionRpe(declared: Double?, exercises: List<CompletedExercise>): Double? {
        declared?.takeIf { it in 0.0..10.0 }?.let { return it }
        val values = exercises.flatMap { it.sets }
            .filter { !it.skipped && !it.isWarmup }
            .mapNotNull { setRpe(it.rpe, it.rir) }
        return values.takeIf { it.isNotEmpty() }?.average()
    }

    /** Igual que [sessionRpe] pero a partir de las series ya filtradas por la pantalla. */
    fun averageSetRpe(sets: List<CompletedSet>): Double? = sets
        .filter { !it.skipped && !it.isWarmup }
        .mapNotNull { setRpe(it.rpe, it.rir) }
        .takeIf { it.isNotEmpty() }
        ?.average()
}
