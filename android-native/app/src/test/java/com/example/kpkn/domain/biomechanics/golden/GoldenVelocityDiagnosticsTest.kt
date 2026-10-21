package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.bar.BarTrackCodec
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.AnalysisTuning
import com.example.kpkn.domain.biomechanics.metrics.MetricContext
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.RelativeVelocity
import com.example.kpkn.domain.biomechanics.metrics.VelocitySummary
import com.example.kpkn.domain.biomechanics.pose.PoseTrackCodec
import com.example.kpkn.domain.biomechanics.reps.CountSeries
import com.example.kpkn.domain.biomechanics.reps.CountSeriesBuilder
import com.example.kpkn.domain.biomechanics.reps.CountSeriesResult
import com.example.kpkn.domain.biomechanics.reps.RepPhases
import com.example.kpkn.domain.biomechanics.reps.RepSegmenter
import com.example.kpkn.domain.biomechanics.reps.TrackPreparation
import com.example.kpkn.domain.biomechanics.vbt.CalibratedVelocity
import com.example.kpkn.domain.biomechanics.vbt.PlateScale
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cobertura de velocidad SOLO DEL AJUSTE: recupera el diagnóstico no persistido en SetTechniqueReport. No calibra umbrales. */
class GoldenVelocityDiagnosticsTest {
    @Test
    fun tuningVelocityCoverageAndDurationErrorDiagnostic() {
        val corpus = GoldenReport.load()
        val fixtures = GoldenReport.tuningFixtures(corpus)
        assertEquals("El reparto de ajuste congelado tiene 59 clips", 59, fixtures.size)
        assertTrue("Faltan trazas de ajuste", fixtures.all { GoldenReport.isExtracted(corpus, it) })
        val results = GoldenReport.evaluateAvailable(corpus, fixtures)
        assertEquals(59, results.size)
        assertTrue("No ejecutar el motor sobre reserva", results.all { it.fixture.subset == "tuning" && it.fixture.source in GoldenReport.TUNING_SOURCES })
        val diagnostics = results.map { VelocityBenchDiagnostic.reconstruct(corpus, it) }
        GoldenReport.publish("GOLDEN_VELOCITY_TUNING.md", VelocityBenchDiagnostic.render(diagnostics))
    }
}

private object VelocityBenchDiagnostic {
    private val tuning = AnalysisTuning.DEFAULT

    fun reconstruct(corpus: GoldenCorpus, result: GoldenResult): VelocityDiagnostic {
        require(result.fixture.subset == "tuning" && result.fixture.source in GoldenReport.TUNING_SOURCES) { "Velocidad diagnóstica: solo ajuste." }
        val fixture = result.fixture
        val poseFile = path(corpus, fixture.posePath, "${fixture.id}.pose2.json.gz")
        checkHash(poseFile, requireNotNull(fixture.poseSha256))
        val pose = requireNotNull(PoseTrackCodec.decode(poseFile.readBytes())) { "Pose inválida: ${fixture.id}" }
        val bar = fixture.barSha256?.let { hash ->
            val file = path(corpus, fixture.barPath, "${fixture.id}.bar.json.gz")
            checkHash(file, hash)
            requireNotNull(BarTrackCodec.decode(file.readBytes())) { "Barra inválida: ${fixture.id}" }
        }
        val from = result.label.activeWindowUs?.first ?: pose.frames.first().tUs
        val to = result.label.activeWindowUs?.second ?: pose.frames.last().tUs
        val croppedPose = pose.copy(frames = pose.frames.filter { it.tUs in from..to })
        val croppedBar = bar?.copy(samples = bar.samples.filter { it.tUs in from..to })
        val profile = result.profile

        // Misma secuencia de TechniqueAnalysisEngine.analyze: preparación, señal, segmentación y polaridad ANTES de fases/métricas.
        val prepared = TrackPreparation.prepare(croppedPose, tuning)
        val built = CountSeriesBuilder.build(prepared, croppedBar, profile, tuning)
        if (built is CountSeriesResult.NoSignal) {
            require(MetricKey.REPS !in result.report.available) { "Divergencia de señal con el informe: ${fixture.id}" }
            return VelocityDiagnostic(result, null, null, null, null, false, built.reason)
        }
        require(built is CountSeriesResult.Ok)
        val series = built.series
        // Como el motor desde M2: la segmentación revisa la captura (encuadre y desplazamiento) con la traza preparada.
        val segmented = RepSegmenter.segment(series, profile, emptySet(), tuning, prepared)
        require(segmented.fullCount == result.report.repCount && segmented.partialCount == result.report.partialReps && segmented.failedCount == result.report.failedReps) { "Reconstrucción distinta del motor: ${fixture.id}" }
        val oriented = if (segmented.polarityFlipped) negated(series) else series
        val phases = RepPhases.compute(oriented, segmented, profile, tuning)
        val ctx = MetricContext(prepared, oriented, segmented, phases, profile, tuning)

        // La escala puede ser matemáticamente válida con el diámetro supuesto del banco; no es una validación física de ese diámetro.
        val plate = if (croppedBar != null && croppedBar.plateDiameterMm != null) {
            PlateScale.estimate(croppedBar, prepared.timebase.fs, croppedPose.focalLengthPx, tuning)
        } else null
        val scale = plate?.scale
        val calibratedBar = if (scale != null && series.source == CountSeriesBuilder.SOURCE_PLATE) croppedBar else null
        val relative = RelativeVelocity.compute(ctx)
        val calibrated = if (calibratedBar != null && scale != null) CalibratedVelocity.compute(ctx, calibratedBar, scale) else null
        val selected = if (calibrated != null && calibrated.available) calibrated else relative
        val selectedLoss = selected.lossPct?.takeIf { it.isFinite() }
        val publishedLoss = result.report.velocityLossPct
        require((selectedLoss == null) == (publishedLoss == null)) { "Disponibilidad de VL distinta del motor: ${fixture.id}" }
        require(selectedLoss == null || publishedLoss == null || abs(selectedLoss - publishedLoss) <= 0.051) { "VL reconstruida distinta del redondeo del motor: ${fixture.id}" }
        require((if (selected.available) selected.unit else null) == result.report.velocityUnit) { "Unidad de velocidad distinta del motor: ${fixture.id}" }
        return VelocityDiagnostic(result, selected, relative, calibrated, series.source, scale != null, null)
    }

    /** Copia exacta de la inversión de señal del motor; no invierte el punto de barra ni altera ciclos/tiempos. */
    private fun negated(series: CountSeries): CountSeries = CountSeries(
        t0Us = series.t0Us,
        fs = series.fs,
        r = DoubleArray(series.r.size) { -series.r[it] },
        filled = series.filled,
        weak = series.weak,
        walk = series.walk,
        barX = series.barX,
        barY = series.barY,
        perSide = series.perSide?.mapValues { (_, values) -> DoubleArray(values.size) { -values[it] } },
        source = series.source,
        unit = series.unit,
        validFraction = series.validFraction,
        workSide = series.workSide,
    )

    fun render(rows: List<VelocityDiagnostic>): String = buildString {
        require(rows.size == 59 && rows.all { it.result.fixture.subset == "tuning" }) { "El informe de velocidad debe conservar las 59 series de ajuste." }
        appendLine("# VT10 · cobertura y error de duración de velocidad en ajuste")
        appendLine()
        appendLine("DIAGNÓSTICO SIN AJUSTE DE UMBRALES. Se evaluaron únicamente las 59 fixtures de Pexels/Mixkit/wger del reparto de ajuste. No se ejecutó el motor sobre reserva. Se usaron las trazas completas y activeWindowS de GoldenReport; el contexto de métricas reproduce el del motor y comprueba coincidencia de conteos, disponibilidad de VL y su valor redondeado.")
        appendLine("durationError es el error relativo estimado de duración por serie (mediana de sus FULL), conservado por VelocitySummary. Las tablas expresan ese error en %. Se publican n medibles y faltantes: null no equivale a 0 %. El porcentaje sigue siendo una estimación del modelo de ruido, sin validación independiente con estos vídeos.")
        appendLine()
        appendLine("Umbrales leídos de AnalysisTuning.DEFAULT: κ=${fmt(tuning.vbt.vlDurationErrorFactor)}, guarda de VL=${percent(tuning.vbt.vlMaxDurationError)}, guarda de barras=${percent(tuning.vbt.vlMaxBarDurationError)}, CV inicial máximo=${percent(tuning.vbt.vlMaxFirstCv)}. Ninguno se modificó.")
        appendLine()
        appendLine("| Perfil | n | REPS | 3+ FULL motor | 3+ FULL etiqueta | VL publicada/n | n durationError / mediana % | Mediana error relativo % | Mediana error disco % |")
        appendLine("|---|---:|---:|---:|---:|---|---|---|---|")
        for ((profile, group) in rows.groupBy { it.result.profile.id }.toSortedMap(compareBy { it.name })) {
            val errors = group.mapNotNull { it.selected?.durationError?.takeIf(Double::isFinite) }
            val relativeErrors = group.mapNotNull { it.relative?.durationError?.takeIf(Double::isFinite) }
            val calibratedErrors = group.mapNotNull { it.calibrated?.durationError?.takeIf(Double::isFinite) }
            appendLine("| $profile | ${group.size} | ${group.count { MetricKey.REPS in it.result.report.available }} | ${group.count { it.result.report.repCount >= 3 }} | ${group.count { it.result.label.full >= 3 }} | ${group.count { it.result.report.velocityLossPct != null }}/${group.size} | ${errors.size}/${percent(median(errors))} | ${percent(median(relativeErrors))} (n=${relativeErrors.size}) | ${percent(median(calibratedErrors))} (n=${calibratedErrors.size}) |")
        }
        val allErrors = rows.mapNotNull { it.selected?.durationError?.takeIf(Double::isFinite) }
        appendLine()
        appendLine("Global descriptivo: ${rows.count { it.result.report.velocityLossPct != null }}/59 con VL publicada; ${allErrors.size}/59 con durationError estimable; mediana ${percent(median(allErrors))}. No es una aceptación ni una medida de error contra verdad física.")
        appendLine()
        appendLine("| Perfil · clips con 3–6 FULL en la etiqueta | n del subconjunto | n con VL | Mediana abs(VL) % | Máxima abs(VL) % |")
        appendLine("|---|---:|---:|---|---|")
        for ((profile, group) in rows.filter { it.result.label.full in 3..6 }.groupBy { it.result.profile.id }.toSortedMap(compareBy { it.name })) {
            val losses = group.mapNotNull { it.result.report.velocityLossPct?.takeIf(Double::isFinite)?.let(::abs) }
            appendLine("| $profile | ${group.size} | ${losses.size} | ${fmt(median(losses))} | ${fmt(losses.maxOrNull())} |")
        }
        appendLine()
        appendLine("El subconjunto anterior se selecciona por las FULL de la etiqueta, para no depender del conteo del motor. NO lleva etiquetas de ausencia de fatiga: una magnitud VL pequeña o grande no demuestra fatiga real, falta de fatiga ni error del estimador. Las series con conteo incierto permanecen en este diagnóstico y se identifican abajo; no se convierten en aceptación.")
        appendLine()
        appendLine("| Clip | Perfil | FULL etiqueta/motor | REPS | Fuente | Escala calculable | Velocidad elegida | VL publicada % | durationError % | lossReliable | Etiqueta incierta | Aviso de escala | Motivo sin señal |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        for (row in rows.sortedBy { it.result.fixture.id }) {
            val r = row.result
            appendLine("| ${r.fixture.id} | ${r.profile.id} | ${r.label.full}/${r.report.repCount} | ${MetricKey.REPS in r.report.available} | ${row.source ?: "sin señal"} | ${row.scaleComputable} | ${row.selected?.unit ?: "no disponible"} | ${fmt(r.report.velocityLossPct)} | ${percent(row.selected?.durationError)} | ${row.selected?.lossReliable ?: "sin medida"} | ${r.label.uncertain} | ${AnalysisFlag.PLATE_SCALE_MISMATCH in r.report.flags} | ${row.noSignal ?: "—"} |")
        }
        appendLine()
        appendLine("El disco del banco usa un diámetro supuesto, sin medida de referencia. Por eso no se calcula una tasa de falsos positivos de PLATE_SCALE_MISMATCH ni se valida m/s. La frecuencia cruda de ese aviso no certifica que el diámetro sea correcto o que el aviso se equivoque. No se recalibró κ ni se propusieron umbrales a partir de la reserva.")
    }

    private fun path(corpus: GoldenCorpus, explicit: String?, fallback: String): File {
        val file = File(explicit ?: fallback)
        val resolved = (if (file.isAbsolute) file else File(corpus.root, file.path)).canonicalFile
        require(resolved.toPath().startsWith(corpus.root.canonicalFile.toPath())) { "El diagnóstico de ajuste solo lee fixtures compatibles: $resolved" }
        return resolved
    }

    private fun checkHash(file: File, expected: String) {
        require(file.isFile) { "Falta fixture de ajuste: $file" }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        require(digest == expected) { "Hash de ajuste cambiado: $file" }
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted(); val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
    private fun fmt(value: Double?): String = value?.takeIf(Double::isFinite)?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—"
    private fun percent(value: Double?): String = value?.takeIf(Double::isFinite)?.let { "${fmt(it * 100)} %" } ?: "—"
}

private data class VelocityDiagnostic(
    val result: GoldenResult,
    val selected: VelocitySummary?,
    val relative: VelocitySummary?,
    val calibrated: VelocitySummary?,
    val source: String?,
    val scaleComputable: Boolean,
    val noSignal: String?,
)
