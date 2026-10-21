package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.PredictedDrain
import kotlin.math.roundToInt

/**
 * Coste final de una sesión con decimales: lo que el motor general calcula antes de truncarlo a enteros. Es la única fuente del
 * coste que enseñan el editor (Impacto), el asistente y las plantillas: nadie debe volver a redondear antes de formatear.
 */
data class PreciseDrain(
    val cns: Double,
    val muscular: Double,
    val spinal: Double,
) {
    /** El contrato entero de siempre (trunca, no redondea): lo que devuelve `calculateAdjustedPredictedDrain`. */
    fun truncated(): PredictedDrain = PredictedDrain(
        cns = cns.toInt().coerceIn(0, 100),
        muscular = muscular.toInt().coerceIn(0, 100),
        spinal = spinal.toInt().coerceIn(0, 100),
    )

    val isAllZero: Boolean get() = cns <= 0.0 && muscular <= 0.0 && spinal <= 0.0

    companion object {
        val ZERO = PreciseDrain(0.0, 0.0, 0.0)
    }
}

/**
 * Cómo se lee un valor de coste: cero exacto, positivo menor de uno, valor normal o desconocido. «Cero» y «desconocido» no se
 * confunden (una banca declara carga axial 0; un ejercicio propio sin dato no declara nada) y un coste positivo pequeño no se
 * presenta como «sin carga».
 */
sealed interface DrainReading {
    data object Unknown : DrainReading
    data object Zero : DrainReading
    data class UnderOne(val value: Double) : DrainReading
    data class Value(val value: Double) : DrainReading

    /** El valor numérico conservado, o null si se desconoce. */
    val number: Double?
        get() = when (this) {
            Unknown -> null
            Zero -> 0.0
            is UnderOne -> value
            is Value -> value
        }

    /** Entero para los anillos: null = sin dato; un coste positivo menor de uno pinta 1 para que no parezca vacío. */
    val ringValue: Int?
        get() = when (this) {
            Unknown -> null
            Zero -> 0
            is UnderOne -> 1
            is Value -> value.roundToInt().coerceIn(0, 100)
        }

    /** Texto de la cifra: «Sin estimación», «0», «<1» o el entero redondeado. */
    val label: String
        get() = when (this) {
            Unknown -> "Sin estimación"
            Zero -> "0"
            is UnderOne -> "<1"
            is Value -> value.roundToInt().coerceIn(0, 100).toString()
        }

    companion object {
        /** null = desconocido; 0 exacto = cero; (0, 1) = «<1»; resto = valor. */
        fun of(value: Double?): DrainReading = when {
            value == null || value.isNaN() -> Unknown
            value <= 0.0 -> Zero
            value < 1.0 -> UnderOne(value)
            else -> Value(value)
        }
    }
}

/** Evidencia de la carga axial en que se apoya la Columna de una sesión. */
enum class SpinalEvidence {
    /** Todos los ejercicios de fuerza declaran su carga axial (también 0) o la sesión es solo cardio. */
    DECLARED,

    /** Algunos la declaran y otros no (ejercicio propio o sin ficha): la cifra se apoya en la heurística para esos. */
    PARTIAL,

    /** Hay fuerza y ningún ejercicio declara carga axial: no hay base para una estimación de Columna. */
    NONE,
}

/** Aporte de un ejercicio al coste: lo bruto de sus series y lo que de verdad entró en el total de la sesión. */
data class ExerciseDrainContribution(
    val exerciseId: String,
    val isCardio: Boolean,
    val setCount: Int,
    /** Suma de las series sin conservación ni tope (Columna ya con la compuerta axial). */
    val rawMuscular: Double,
    val rawCns: Double,
    val rawSpinal: Double,
    /** Lo que entró en los acumuladores de la sesión tras la conservación, el decaimiento y el tope blando. */
    val cappedMuscular: Double,
    val cappedCns: Double,
    val cappedSpinal: Double,
    /** Carga axial declarada por el catálogo para la configuración exacta; null = no declarada (propio o sin ficha). */
    val axialLoadFactor: Double?,
    /** Solo cardio: coste muscular por pilar. */
    val cardioMuscleDrains: Map<String, Double> = emptyMap(),
)

/**
 * Proyección de coste de una sesión PREVISTA, compartida por el editor y el asistente: el mismo cálculo que el motor general,
 * con decimales, el cardio dentro y el reparto por ejercicio.
 */
data class SessionDrainProjection(
    /** Coste final: finalización general (Columna escalada, mínimo de sesión dura) y ajuste por historial. */
    val precise: PreciseDrain,
    /** Acumulado antes del mínimo de sesión dura y del historial: base para repartir por músculo. */
    val accumulated: PreciseDrain,
    /** El mismo coste en el contrato entero de siempre. */
    val global: PredictedDrain,
    val perExercise: List<ExerciseDrainContribution>,
    val effectiveSetCount: Int,
    val hardEffectiveSets: Int,
    val hasWork: Boolean,
    val spinalEvidence: SpinalEvidence,
    /** Ejercicios de fuerza con series cuyo catálogo no declara carga axial. */
    val axialUnknownExerciseIds: List<String>,
) {
    val muscularReading: DrainReading get() = readingFor(precise.muscular)
    val cnsReading: DrainReading get() = readingFor(precise.cns)
    val spinalReading: DrainReading
        get() = if (hasWork && spinalEvidence == SpinalEvidence.NONE) DrainReading.Unknown else readingFor(precise.spinal)

    private fun readingFor(value: Double): DrainReading = when {
        !hasWork -> DrainReading.Zero
        // Hay trabajo y los tres canales dan exactamente cero: el motor no tiene base; no se inventa un respaldo fijo.
        precise.isAllZero -> DrainReading.Unknown
        else -> DrainReading.of(value)
    }

    /** Pico de los tres canales con decimales, para las reglas del asistente. */
    val peak: Double get() = maxOf(precise.muscular, precise.cns, precise.spinal)

    companion object {
        val EMPTY = SessionDrainProjection(
            precise = PreciseDrain.ZERO,
            accumulated = PreciseDrain.ZERO,
            global = PredictedDrain(0, 0, 0),
            perExercise = emptyList(),
            effectiveSetCount = 0,
            hardEffectiveSets = 0,
            hasWork = false,
            spinalEvidence = SpinalEvidence.DECLARED,
            axialUnknownExerciseIds = emptyList(),
        )
    }
}
