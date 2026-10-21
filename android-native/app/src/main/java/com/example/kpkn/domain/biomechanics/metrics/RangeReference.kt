package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import kotlinx.serialization.Serializable

/** De dónde sale una referencia de recorrido: las series anteriores del usuario ([PERSONAL]) o el perfil ([PROFILE]). */
enum class RangeReferenceSource { PERSONAL, PROFILE }

/**
 * Referencia de recorrido (M4): la amplitud de una repetición COMPLETA medida fuera de esta serie, para que el motor vea una
 * serie hecha solo de parciales (sin ella, la amplitud completa sale de la propia serie y unas parciales se ven completas).
 *
 * @property amplitude amplitud de una repetición completa, en la unidad de [signal] (alturas corporales, grados o razón; con
 *   el disco, la del disco). Con PERSONAL: la mediana de `SetTechniqueReport.fullAmplitude` de las series anteriores.
 * @property signal la señal de conteo en la que se midió (`SetTechniqueReport.countSignal`). Si la serie cuenta con otra
 *   señal (por ejemplo, pose en vez de disco), la referencia no se usa.
 * @property source de dónde sale.
 * @property seriesCount en cuántas series se basa.
 * @property view la vista en la que se midió, si se sabe: un ángulo o una razón en 2D dependen de la vista, así que con otra
 *   vista la referencia no se usa. Un desplazamiento en alturas de cuerpo no depende de ella y no se comprueba. `null`: no se
 *   comprueba.
 */
@Serializable
data class RangeReference(
    val amplitude: Double,
    val signal: CountSignal,
    val source: RangeReferenceSource,
    val seriesCount: Int,
    val view: ViewKind? = null,
)

/**
 * Qué referencia de recorrido recibió el análisis y si cambió algo (va en el informe). [applied]: la referencia (con la
 * confianza de su origen) superó la amplitud completa de la propia serie, así que la clasificación se hizo contra ella.
 * [reason] dice por qué no se usó, si no se usó: `signal` (otra señal), `view` (otra vista) o `invalid` (amplitud no válida).
 */
@Serializable
data class RangeReferenceUse(
    val source: RangeReferenceSource,
    val amplitude: Double,
    val seriesCount: Int,
    val applied: Boolean,
    val reason: String? = null,
)
