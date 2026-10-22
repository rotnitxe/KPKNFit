package com.example.kpkn.data.media.technique

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.RangeReference
import com.example.kpkn.domain.biomechanics.metrics.RangeReferenceSource
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import com.example.kpkn.domain.biomechanics.reps.SignalUnit

/**
 * La referencia PERSONAL de recorrido (M4b): la mediana de la amplitud completa (`SetTechniqueReport.fullAmplitude`) de los
 * análisis anteriores del mismo ejercicio, para que el motor vea una serie hecha solo de parciales. Función pura: quien llama
 * (el repositorio) busca los análisis anteriores y lee sus informes.
 *
 * Un análisis anterior sirve si su motor es ≥ [MIN_ENGINE_VERSION], su confianza es ALTA o MEDIA, tiene señal de conteo y una
 * amplitud completa válida. Con la señal de conteo (y, si no es un desplazamiento en alturas de cuerpo, con la vista) la
 * referencia solo vale entre informes iguales, así que los análisis se agrupan por ella y se usa el grupo con más series
 * (con empate, el del análisis más reciente). Hacen falta al menos [MIN_SERIES] series; si no, no hay referencia y el motor
 * trabaja como siempre (no se inventa ninguna del perfil). Se cuentan como mucho las [MAX_SERIES] más recientes.
 */
internal object PersonalRangeReference {
    const val MIN_SERIES = 2
    const val MAX_SERIES = 12
    const val MIN_ENGINE_VERSION = 9

    /** Un análisis anterior: su vídeo, cuándo se grabó y su informe. */
    data class Prior(val mediaId: String, val recordedAtMs: Long, val report: SetTechniqueReport)

    private data class Sample(
        val mediaId: String,
        val recordedAtMs: Long,
        val signal: CountSignal,
        val view: ViewKind?,
        val amplitude: Double,
    )

    /** La referencia para el vídeo [selfMediaId] (que nunca cuenta consigo mismo), o `null`. */
    fun select(priors: List<Prior>, selfMediaId: String): RangeReference? {
        val samples = priors.asSequence()
            .filter { it.mediaId != selfMediaId }
            .mapNotNull(::sampleOf)
            .sortedWith(compareByDescending<Sample> { it.recordedAtMs }.thenBy { it.mediaId })
            .take(MAX_SERIES)
            .toList()
        val groups = samples.groupBy { it.signal to it.view }
        // El primer grupo en aparecer es el del análisis más reciente: con empate de tamaño gana.
        val best = groups.values.maxWithOrNull(compareBy<List<Sample>> { it.size }.thenByDescending { group -> samples.indexOf(group.first()) })
            ?.takeIf { it.size >= MIN_SERIES } ?: return null
        return RangeReference(
            amplitude = median(best.map { it.amplitude }),
            signal = best.first().signal,
            source = RangeReferenceSource.PERSONAL,
            seriesCount = best.size,
            view = best.first().view,
        )
    }

    private fun sampleOf(prior: Prior): Sample? {
        val report = prior.report
        val signal = report.countSignal ?: return null
        val amplitude = report.fullAmplitude?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        if (report.engineVersion < MIN_ENGINE_VERSION) return null
        if (report.confidence != CountConfidence.ALTA && report.confidence != CountConfidence.MEDIA) return null
        // Un desplazamiento en alturas de cuerpo no depende de la vista; un ángulo o una razón en 2D, sí.
        val view = if (report.countUnit == SignalUnit.BODY_HEIGHTS) null else report.view
        return Sample(prior.mediaId, prior.recordedAtMs, signal, view, amplitude)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}
