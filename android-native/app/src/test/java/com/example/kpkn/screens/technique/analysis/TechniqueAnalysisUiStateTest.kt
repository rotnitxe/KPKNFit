package com.example.kpkn.screens.technique.analysis

import com.example.kpkn.domain.biomechanics.bar.PlateTrackingContinuity

import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.CaptureOverrides
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.PartialZone
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.TechniqueObservation
import com.example.kpkn.domain.biomechanics.metrics.TempoSummary
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.screens.technique.TechniqueSamples
import com.example.kpkn.screens.technique.analysis.design.keepTogether
import com.example.kpkn.screens.technique.player.TechniqueOverlayData
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas de HONESTIDAD de la pantalla «Análisis de técnica» (VT7b), en la JVM y sobre los informes de ejemplo: lo que se pinta, lo
 * que no y con qué símbolo. Sin dato, la línea no existe; m/s solo con el disco calibrado y con «≈»; la pérdida de velocidad sin
 * dato fiable no es un 0 %; las observaciones salen tal cual.
 */
class TechniqueAnalysisUiStateTest {
    private val zone: ZoneId = ZoneId.of("Europe/Madrid")

    private fun tiles(report: SetTechniqueReport) = buildSummary(report).tiles.associateBy { it.id }

    private fun marksOf(report: SetTechniqueReport) =
        TechniqueOverlayData.from(null, null, report, 720, 1280).reps

    // ── Resumen ───────────────────────────────────────────────────────────────

    @Test
    fun a_calibrated_set_gives_every_tile_and_meters_per_second_with_an_approximate_sign() {
        val byId = tiles(TechniqueSamples.analysisReport)
        assertEquals(
            listOf(
                SummaryTileId.REPS, SummaryTileId.TUT, SummaryTileId.TEMPO, SummaryTileId.ROM,
                SummaryTileId.VELOCITY_LOSS, SummaryTileId.VELOCITY_MEAN, SummaryTileId.BAR_PATH,
            ),
            buildSummary(TechniqueSamples.analysisReport).tiles.map { it.id },
        )
        val reps = byId.getValue(SummaryTileId.REPS)
        assertEquals("8", reps.figure)
        assertEquals(CountConfidence.ALTA, reps.confidence)
        assertEquals(listOf("+1 parcial", "1 fallida", "Confianza alta"), reps.notes)

        assertEquals("41", byId.getValue(SummaryTileId.TUT).value)
        assertEquals("s", byId.getValue(SummaryTileId.TUT).unit)
        assertEquals("3-1-1-0", byId.getValue(SummaryTileId.TEMPO).value)

        val rom = byId.getValue(SummaryTileId.ROM)
        assertEquals("108°", rom.figure)
        assertEquals("ROM de la rodilla", rom.label)
        assertEquals(listOf("± 3° entre repeticiones"), rom.notes)

        val loss = byId.getValue(SummaryTileId.VELOCITY_LOSS)
        assertEquals("19", loss.figure)
        assertEquals("%", loss.unit)

        val mean = byId.getValue(SummaryTileId.VELOCITY_MEAN)
        assertEquals("≈ ", mean.prefix)
        assertEquals("m/s", mean.unit)
        assertTrue(mean.figure, mean.figure.startsWith("≈ 0,5"))

        val path = byId.getValue(SummaryTileId.BAR_PATH)
        assertEquals("3,2", path.figure)
        assertEquals("cm", path.unit)
    }

    @Test
    fun without_a_calibrated_disc_there_is_no_meters_per_second_anywhere() {
        val report = TechniqueSamples.analysisReportRelative
        val summary = buildSummary(report)
        // Sin escala física validada la velocidad media no se inventa: aparece como «—» y se explica por qué.
        val mean = summary.tiles.first { it.id == SummaryTileId.VELOCITY_MEAN }
        assertEquals("—", mean.value)
        assertEquals(listOf("Sin escala física validada"), mean.notes)
        assertTrue(summary.tiles.none { it.unit == "m/s" || it.figure.contains("m/s") || it.notes.any { n -> n.contains("m/s") } })

        // Ni en las barras ni en el detalle de cada repetición.
        val breakdown = buildBreakdown(report, marksOf(report))!!
        val velocity = breakdown.metrics.first { it.kind == RepMetricKind.VELOCITY }
        assertTrue(velocity.valueLabels.filterNotNull().none { it.contains("m/s") })
        assertTrue(breakdown.reps.none { it.detail.contains("m/s") })
        assertTrue(velocity.valueLabels.filterNotNull().any { it.endsWith("% de la mejor") })
        assertNotNull("explica por qué no hay m/s", velocity.note)

        // El desvío sin calibrar va en % del recorrido y con «≈».
        val path = summary.tiles.first { it.id == SummaryTileId.BAR_PATH }
        assertEquals("≈ 6", path.figure)
        assertEquals("%", path.unit)
    }

    @Test
    fun with_the_disc_calibrated_every_speed_carries_an_approximate_sign() {
        val report = TechniqueSamples.analysisReport
        val breakdown = buildBreakdown(report, marksOf(report))!!
        val velocity = breakdown.metrics.first { it.kind == RepMetricKind.VELOCITY }
        val labels = velocity.valueLabels.filterNotNull()
        assertTrue(labels.isNotEmpty())
        assertTrue(labels.all { it.startsWith("≈ ") && it.endsWith(" m/s") })
        assertTrue(breakdown.reps.first { it.number == 1 }.detail.contains("≈ 0,62 m/s"))
    }

    @Test
    fun a_loss_without_a_reliable_value_is_never_painted_as_zero() {
        val summary = buildSummary(TechniqueSamples.analysisReportLossUnknown)
        val loss = summary.tiles.first { it.id == SummaryTileId.VELOCITY_LOSS }
        assertEquals("Sin dato fiable", loss.note)
        assertEquals("no hay cifra", "", loss.figure)
        assertNull(loss.unit)
        assertNull(loss.countTo)
        assertEquals(listOf("El ruido del vídeo no deja medirla"), loss.notes)
    }

    @Test
    fun a_loss_that_needs_more_full_reps_says_so() {
        val few = TechniqueSamples.analysisReportLossUnknown.let { report ->
            report.copy(reps = report.reps.filter { it.index in setOf(0, 1, 2) }, repCount = 2, partialReps = 1, failedReps = 0)
        }
        val loss = buildSummary(few).tiles.first { it.id == SummaryTileId.VELOCITY_LOSS }
        assertEquals("Sin dato fiable", loss.note)
        assertEquals(listOf("Hacen falta 3 repeticiones completas"), loss.notes)
    }

    @Test
    fun no_loss_and_no_relative_velocity_means_no_loss_tile() {
        val report = TechniqueSamples.analysisReportRelative.copy(
            velocityLossPct = null,
            available = TechniqueSamples.analysisReportRelative.available - MetricKey.VELOCITY_RELATIVE,
        )
        assertTrue(buildSummary(report).tiles.none { it.id == SummaryTileId.VELOCITY_LOSS })
    }

    @Test
    fun a_missing_measure_removes_its_tile_instead_of_inventing_a_zero() {
        val report = TechniqueSamples.analysisReport.copy(
            tutMs = null,
            tempo = null,
            romMeanDeg = null,
            velocityUnit = null,
            velocityLossPct = null,
            available = setOf(MetricKey.REPS),
            barDeviation = null,
        )
        val ids = buildSummary(report).tiles.map { it.id }
        // La velocidad media ausente se pinta «—»: es una celda del panel de métricas, no un dato inventado.
        assertEquals(listOf(SummaryTileId.REPS, SummaryTileId.VELOCITY_MEAN), ids)
    }

    @Test
    fun a_tempo_with_nothing_measured_is_not_painted() {
        val report = TechniqueSamples.analysisReport.copy(tempo = TempoSummary(null, null, null, null))
        assertTrue(buildSummary(report).tiles.none { it.id == SummaryTileId.TEMPO })
    }

    @Test
    fun a_long_time_under_tension_reads_as_minutes() {
        val summary = buildSummary(TechniqueSamples.analysisReport.copy(tutMs = 112_400L))
        val tut = summary.tiles.first { it.id == SummaryTileId.TUT }
        assertEquals("1:52", tut.value)
        assertEquals("min", tut.unit)
        assertNull("las cifras con dos puntos no cuentan", tut.countTo)
    }

    @Test
    fun medium_confidence_estimates_the_count_and_asks_to_review_it() {
        val reps = buildSummary(TechniqueSamples.analysisReportMedium).tiles.first { it.id == SummaryTileId.REPS }
        assertEquals("≈ 8", reps.figure)
        assertEquals(CountConfidence.MEDIA, reps.confidence)
        assertTrue(reps.notes.contains("Confianza media"))
        assertTrue(reps.notes.contains("Revisa el conteo"))
    }

    @Test
    fun low_confidence_says_the_video_is_not_conclusive() {
        val reps = buildSummary(TechniqueSamples.analysisReport.copy(confidence = CountConfidence.BAJA)).tiles.first { it.id == SummaryTileId.REPS }
        assertEquals("≈ 8", reps.figure)
        assertTrue(reps.notes.contains("Confianza baja"))
        assertTrue(reps.notes.contains("Vídeo no concluyente"))
    }

    @Test
    fun the_rom_of_an_oblique_view_is_an_estimate() {
        val rom = buildSummary(TechniqueSamples.analysisReportMedium).tiles.first { it.id == SummaryTileId.ROM }
        assertEquals("≈ 108°", rom.figure)
        assertNull("una estimación no cuenta hacia arriba", rom.countTo)
    }

    @Test
    fun depth_is_counted_over_the_full_reps_and_is_an_estimate() {
        assertEquals("Bajo el paralelo en ≈ 7 de 8 · a la par en 1", buildSummary(TechniqueSamples.analysisReport).depthLine)
        val withoutDepth = TechniqueSamples.analysisReport.copy(available = TechniqueSamples.analysisReport.available - MetricKey.DEPTH)
        assertNull(buildSummary(withoutDepth).depthLine)
    }

    @Test
    fun partials_failures_and_sides_are_counted_apart_from_the_full_reps() {
        val both = buildSummary(TechniqueSamples.analysisReport.copy(partialReps = 2, failedReps = 2, perSideCounts = mapOf("left" to 4, "right" to 3)))
        val notes = both.tiles.first { it.id == SummaryTileId.REPS }.notes
        assertEquals(listOf("+2 parciales", "2 fallidas", "Izq. 4 · der. 3", "Confianza alta"), notes)
    }

    @Test
    fun a_video_without_reps_explains_why_and_has_no_figures() {
        val none = buildSummary(TechniqueReportSamples.minimalReport())
        assertEquals("No vimos a nadie en el vídeo", none.noRepsMessage)
        assertTrue(none.tiles.isEmpty())
        assertNull(none.depthLine)
        assertEquals("El vídeo es demasiado corto para contar repeticiones", noRepsMessage("too_short"))
        assertEquals("El movimiento fue demasiado pequeño para contarlo", noRepsMessage("low_amplitude"))
        assertEquals("No encontramos repeticiones en el vídeo", noRepsMessage("no_cycles"))
    }

    // ── Por repetición ────────────────────────────────────────────────────────

    @Test
    fun the_bars_follow_the_overlay_order_and_are_relative_to_the_best() {
        val report = TechniqueSamples.analysisReport
        val marks = marksOf(report)
        val breakdown = buildBreakdown(report, marks)!!
        assertEquals(RepMetricKind.entries.toList(), breakdown.metrics.map { it.kind })
        assertEquals(marks.size, breakdown.reps.size)
        assertEquals((1..marks.size).toList(), breakdown.reps.map { it.number })
        assertEquals(listOf(RepKind.FULL, RepKind.FULL, RepKind.PARTIAL), breakdown.reps.take(3).map { it.kind })
        assertTrue(breakdown.hasPartial)
        assertTrue(breakdown.hasFailed)

        val velocity = breakdown.metrics.first { it.kind == RepMetricKind.VELOCITY }
        assertEquals(1f, velocity.heights.filterNotNull().max(), 0f)
        assertEquals("la mejor es la primera", 0, velocity.bestIndex)
        assertNull("la parcial no tiene velocidad: sin dato, sin barra", velocity.heights[2])
        assertNull("la fallida tampoco", velocity.heights[9])
        assertEquals(
            "Velocidad: la más rápida, la 1; la más lenta, la 9, un 18 % menos",
            velocity.description,
        )
        // El tiempo sí lo tiene toda repetición.
        val time = breakdown.metrics.first { it.kind == RepMetricKind.TIME }
        assertTrue(time.heights.all { it != null })
    }

    @Test
    fun the_detail_line_of_a_rep_only_says_what_was_measured() {
        val report = TechniqueSamples.analysisReport
        val breakdown = buildBreakdown(report, marksOf(report))!!
        assertEquals("Rep 1 · bajada 2,8 s · subida 0,9 s · ROM 112° · ≈ 0,62 m/s · bajo el paralelo", breakdown.reps[0].detail)
        assertEquals("Rep 3 · parcial · ≈\u00A055\u00A0%\u00A0del\u00A0recorrido · bajada 3,0 s", breakdown.reps[2].detail)
        assertEquals("Rep 10 · fallida · bajada 3,5 s", breakdown.reps[9].detail)
        assertTrue(breakdown.reps[8].detail.endsWith("a la par"))
    }

    @Test
    fun the_oblique_rom_and_the_inverted_exercises_use_the_right_words() {
        val oblique = TechniqueSamples.analysisReportMedium
        val breakdown = buildBreakdown(oblique, marksOf(oblique))!!
        assertTrue(breakdown.reps[0].detail.contains("ROM ≈ 112°"))
        assertEquals("excéntrica" to "concéntrica", phaseWords(AnalysisProfileId.PULL_VERTICAL))
        assertEquals("bajada" to "subida", phaseWords(AnalysisProfileId.SQUAT))
        val row = TechniqueSamples.analysisReport.copy(profileId = AnalysisProfileId.ROW)
        assertTrue(buildBreakdown(row, marksOf(row))!!.reps[0].detail.contains("excéntrica 2,8 s"))
    }

    @Test
    fun a_single_rep_has_no_chart() {
        val report = TechniqueSamples.analysisReport.copy(reps = TechniqueSamples.analysisReport.reps.take(1))
        assertNull(buildBreakdown(report, marksOf(report)))
    }

    // ── Observaciones, consejos y disco ───────────────────────────────────────

    @Test
    fun observations_are_painted_as_the_engine_wrote_them() {
        val report = TechniqueSamples.analysisReport
        val marks = marksOf(report)
        val items = buildObservations(report, marks)
        assertEquals(report.observations.map { it.text }, items.map { it.text })
        assertEquals(3, items.size)
        assertEquals(8, items[0].repPosition)
        assertEquals(report.observations[0].atUs, items[0].seekUs)
        assertTrue(items.all { it.canSee })
        // Sin instante ni repetición no hay nada que ver.
        val lonely = report.copy(observations = listOf(TechniqueObservation("x", "Texto", emptyList(), null, 0.0, 0.0)))
        assertFalse(buildObservations(lonely, marks).single().canSee)
        // Como mucho tres.
        val many = report.copy(observations = List(5) { report.observations[0].copy(code = "c$it") })
        assertEquals(3, buildObservations(many, marks).size)
    }

    @Test
    fun a_rep_that_is_not_on_the_timeline_does_not_jump_to_the_wrong_one() {
        val report = TechniqueSamples.analysisReport
        val stray = report.observations[0].copy(repIndices = listOf(42))
        val item = buildObservations(report.copy(observations = listOf(stray)), marksOf(report)).single()
        assertEquals(-1, item.repPosition)
        assertTrue("sigue pudiendo saltar a su instante", item.canSee)
    }

    @Test
    fun the_tips_follow_the_flags_in_order_of_importance_with_at_most_three() {
        assertEquals(
            listOf(CameraTip.MULTI_PERSON, CameraTip.OBLIQUE_VIEW, CameraTip.FAR_SIDE_WORKING),
            buildTips(TechniqueSamples.analysisReportMedium),
        )
        assertEquals(emptyList<CameraTip>(), buildTips(TechniqueSamples.analysisReport))
        assertEquals(
            "con flags hay consejo aunque la confianza sea alta",
            listOf(CameraTip.PLATE_ELLIPSE),
            buildTips(TechniqueSamples.analysisReport.copy(flags = setOf(AnalysisFlag.PLATE_ELLIPSE, AnalysisFlag.ROLL_CORRECTED))),
        )
        assertEquals(
            "sin flags con consejo pero con confianza baja: el consejo general",
            listOf(CameraTip.GENERIC),
            buildTips(TechniqueSamples.analysisReport.copy(confidence = CountConfidence.MEDIA)),
        )
        assertEquals("Que solo salgas tú en el encuadre", CameraTip.MULTI_PERSON.text)
        assertEquals("Graba a 30 fps o más", CameraTip.LOW_FPS.text)
    }

    @Test
    fun the_scale_warnings_each_carry_their_cause() {
        val all = plateWarnings(
            setOf(
                AnalysisFlag.PLATE_ELLIPSE, AnalysisFlag.PLATE_REJECTED, AnalysisFlag.PARALLAX_SUPPRESSED,
                AnalysisFlag.PLATE_SCALE_MISMATCH, AnalysisFlag.MULTI_PERSON,
            ),
        )
        assertEquals(4, all.size)
        assertTrue(all[0].startsWith("El disco se ve ovalado"))
        assertTrue(all[2].contains("paralaje"))
        assertTrue(plateWarnings(setOf(AnalysisFlag.ROLL_CORRECTED)).isEmpty())
    }

    @Test
    fun the_plate_module_has_three_states_and_only_appears_when_it_makes_sense() {
        val report = TechniqueSamples.analysisReport
        val marks = marksOf(report)
        val seed = PlateSeed(tUs = 4_000_000L, cx = 300f, cy = 500f, r = 60f, userConfirmed = false)
        val found = TechniqueSamples.analysisPlate.copy(seed = seed)

        fun plate(bar: BarTrack?, overrides: CaptureOverrides = CaptureOverrides(), usesPlate: Boolean = true) =
            buildPlate(report, marks, bar, overrides, usesPlate, 450, 720, 1280)

        val suggested = plate(found)!!
        assertEquals(PlateModuleState.SUGGESTED, suggested.state)
        assertEquals(seed, suggested.circle)
        assertEquals(4_000_000L, suggested.frameUs)

        val confirmed = plate(found.copy(seed = seed.copy(userConfirmed = true)), CaptureOverrides(plateSeed = seed.copy(userConfirmed = true), plateDiameterMm = 400))!!
        assertEquals(PlateModuleState.CONFIRMED, confirmed.state)
        assertEquals(400, confirmed.diameterMm)
        val broken = plate(found.copy(seed = seed.copy(userConfirmed = true), trackingContinuity = PlateTrackingContinuity.INTERRUPTED))!!
        assertEquals(PlateModuleState.CONFIRMED, broken.state)
        assertFalse(broken.inMetersPerSecond)
        assertTrue(broken.warnings.any { it.contains("Se perdió el seguimiento") })
        val unknown = plate(found.copy(seed = seed.copy(userConfirmed = true), trackingContinuity = PlateTrackingContinuity.UNKNOWN))!!
        assertFalse(unknown.inMetersPerSecond)
        assertTrue(unknown.warnings.any { it.contains("No podemos comprobar") })
        assertTrue("con velocityUnit MPS se dice en m/s", confirmed.inMetersPerSecond)

        val notFound = plate(null)!!
        assertEquals(PlateModuleState.NOT_FOUND, notFound.state)
        assertNull(notFound.circle)
        assertEquals("sin disco, el fotograma es el del fondo de la 1.ª repetición", marks.first().turnUs, notFound.frameUs)

        assertNull(
            "un perfil que no usa el disco, sin escala por disco ni disco suyo, no lo ofrece",
            buildPlate(report.copy(scale = null), marks, null, CaptureOverrides(), false, 450, 720, 1280),
        )

        val relative = TechniqueSamples.analysisReportRelative
        val confirmedWithoutSpeed = buildPlate(relative, marks, found, CaptureOverrides(plateSeed = seed.copy(userConfirmed = true)), true, 450, 720, 1280)!!
        assertFalse("sin m/s no se promete la velocidad en m/s", confirmedWithoutSpeed.inMetersPerSecond)
    }

    // ── Cabecera, estados y perfiles ──────────────────────────────────────────

    private fun media(setIndex: Int? = 2, weightKg: Double? = 80.0, reps: Int? = 8, createdAtMs: Long = timeOf(2026, 10, 13)) = WorkoutMedia(
        id = "m1",
        kind = WorkoutMediaKind.VIDEO,
        filePath = "/x.mp4",
        createdAtMs = createdAtMs,
        setIndex = setIndex,
        weightKg = weightKg,
        reps = reps,
    )

    private fun timeOf(year: Int, month: Int, day: Int): Long =
        ZonedDateTime.of(year, month, day, 18, 30, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun the_set_line_says_series_load_reps_and_date() {
        val now = timeOf(2026, 10, 14)
        assertEquals("Serie 3 · 80 kg × 8 · 13 oct", setInfoOf(media(), now, zone))
        assertEquals("Serie 3 · 82,5 kg × 5 · 13 oct", setInfoOf(media(weightKg = 82.5, reps = 5), now, zone))
        assertEquals("Serie 1 · 80 kg · 13 oct", setInfoOf(media(setIndex = 0, reps = null), now, zone))
        assertEquals("Serie 3 · 12 repeticiones · 13 oct", setInfoOf(media(weightKg = null, reps = 12), now, zone))
        assertEquals("Serie 3 · 1 repetición · 13 oct", setInfoOf(media(weightKg = 0.0, reps = 1), now, zone))
    }

    @Test
    fun without_set_data_only_the_date_remains_and_an_old_year_is_named() {
        val now = timeOf(2026, 10, 14)
        assertEquals("13 oct", setInfoOf(media(setIndex = null, weightKg = null, reps = null), now, zone))
        assertEquals("2 ene 2025", setInfoOf(media(setIndex = null, weightKg = null, reps = null, createdAtMs = timeOf(2025, 1, 2)), now, zone))
    }

    @Test
    fun unsupported_and_failed_rows_become_sentences() {
        assertEquals("Los levantamientos olímpicos aún no se analizan", unsupportedText("olympic"))
        assertEquals("Este ejercicio aún no se analiza", unsupportedText(null))
        assertEquals("Este tipo de ejercicio aún no se analiza", unsupportedText("algo_nuevo"))
        assertEquals("El móvil se quedó sin memoria al analizarlo.", failureMessage("out_of_memory"))
        assertEquals("No encontramos el archivo del vídeo.", failureMessage("video_missing"))
        assertEquals("Algo falló durante el análisis.", failureMessage("analyzer_unavailable"))
        assertEquals("Algo falló durante el análisis.", failureMessage(null))
    }

    @Test
    fun the_profile_list_puts_the_guess_first_and_folds_to_five() {
        val folded = profileOptions(AnalysisProfileId.ROW, selected = AnalysisProfileId.ROW, expanded = false)
        assertEquals(COLLAPSED_PROFILE_COUNT, folded.size)
        assertEquals(AnalysisProfileId.ROW, folded.first().id)
        assertEquals(1, folded.count { it.id == AnalysisProfileId.ROW })
        assertEquals("Remo", folded.first().title)

        val withoutGuess = profileOptions(null, selected = null, expanded = false)
        assertEquals(AnalysisProfileId.SQUAT, withoutGuess.first().id)
        assertEquals("Sentadilla", withoutGuess.first().title)

        // Un movimiento elegido que no cabe en la lista plegada se añade para que no desaparezca.
        val picked = profileOptions(null, selected = AnalysisProfileId.FLY, expanded = false)
        assertEquals(COLLAPSED_PROFILE_COUNT + 1, picked.size)
        assertEquals(AnalysisProfileId.FLY, picked.last().id)

        val all = profileOptions(AnalysisProfileId.CURL, selected = null, expanded = true)
        assertEquals(AnalysisProfileId.entries.size, all.size)
        assertEquals(AnalysisProfileId.entries.toSet(), all.map { it.id }.toSet())
        assertEquals(AnalysisProfileId.entries.size, ALL_PROFILE_COUNT)
        assertTrue("todos tienen nombre y pista", all.all { it.title.isNotBlank() && !it.subtitle.isNullOrBlank() })
    }

    @Test
    fun the_state_only_opens_trends_with_results_and_a_key() {
        val ready = TechniqueSamples.analysisUiState()
        assertTrue(ready.canOpenTrends)
        assertFalse(ready.copy(exerciseKey = null).canOpenTrends)
        assertFalse(ready.copy(results = null).canOpenTrends)
        assertFalse(TechniqueSamples.statusUiState(AnalysisStatusUi.Analyzing(42, updating = false, queued = false)).canOpenTrends)
        assertTrue(TechniqueSamples.statusUiState(AnalysisStatusUi.Analyzing(42, updating = false, queued = false)).isAnalyzing)
    }

    @Test
    fun percent_is_kept_between_zero_and_one_hundred() {
        assertEquals(0, clampPercent(-5))
        assertEquals(42, clampPercent(42))
        assertEquals(100, clampPercent(250))
    }

    // ── Líneas sueltas ────────────────────────────────────────────────────────

    @Test
    fun short_lines_keep_the_approximate_sign_the_unit_and_the_last_words_together() {
        val nbsp = "\u00A0"
        // «≈» y la unidad pegados a la cifra: una línea nunca acaba en «≈» ni deja «m/s» solo.
        assertEquals("Mejor repetición ≈${nbsp}0,62${nbsp}m/s", "Mejor repetición ≈ 0,62 m/s".keepTogether())
        // Las dos últimas palabras juntas, si son cortas: no queda un «pie» ni un «1» huérfano.
        assertEquals("Respecto del medio${nbsp}pie", "Respecto del medio pie".keepTogether())
        assertEquals("Bajo el paralelo en ≈${nbsp}7 de${nbsp}8", "Bajo el paralelo en ≈ 7 de 8".keepTogether())
        assertEquals("a la par en${nbsp}1", "a la par en 1".keepTogether())
        // Dos palabras, o un final largo, se quedan como están: una columna estrecha lo partiría a media palabra.
        assertEquals("Confianza alta", "Confianza alta".keepTogether())
        assertEquals("± 3° entre repeticiones", "± 3° entre repeticiones".keepTogether())
        assertEquals("Hacen falta 3 repeticiones completas", "Hacen falta 3 repeticiones completas".keepTogether())
        // «3 segundos» no es «3 s»: la cifra solo se pega a una unidad entera.
        assertEquals("Dura 3 segundos${nbsp}más", "Dura 3 segundos más".keepTogether())
        assertEquals("Dura 3${nbsp}s más", "Dura 3 s más".keepTogether())
        // Solo cambia el tipo de espacio: sin los espacios duros, el texto es el mismo.
        val original = "Bajo el paralelo en ≈ 7 de 8"
        assertEquals(original, original.keepTogether().replace(nbsp, " "))
    }

    @Test
    fun reps_only_report_keeps_navigation_anchors_without_publishing_a_time_chart() {
        val source = TechniqueSamples.analysisReport
        val report = source.copy(
            available = setOf(MetricKey.REPS),
            tutMs = null, setDurationMs = null, restPositionMs = null, tempo = null,
            velocityUnit = null, velocityLossPct = null, mcvBest = null,
            reps = source.reps.map { it.copy(
                eccentricMs = null, turnPauseMs = null, concentricMs = null, restPauseMs = null,
                romDeg = null, depthClass = null, mcv = null, peakVelocity = null,
            ) },
        )
        val marks = marksOf(report)
        assertEquals(source.reps.size, marks.size)
        assertTrue(marks.all { it.endUs > it.startUs })
        assertTrue(marks.any { it.isLoopable })
        val originalAnchors = marksOf(source)
        assertEquals(originalAnchors, marks)
        assertNull("Coarse seek/loop intervals are not measured rep durations", buildBreakdown(report, marks))
        assertEquals(listOf(SummaryTileId.REPS, SummaryTileId.VELOCITY_MEAN), buildSummary(report).tiles.map { it.id })
        assertEquals(source.repCount, report.repCount)
        assertEquals(source.confidence, report.confidence)
        assertEquals(originalAnchors, marksOf(report)) // UI preparation must not rewrite navigation.
    }

    @Test
    fun unavailable_phases_do_not_leak_through_nonnull_rep_fields_while_rom_and_speed_remain() {
        val source = TechniqueSamples.analysisReport
        val report = source.copy(available = source.available - MetricKey.PHASES)
        // Deliberately retain non-null phase fields to require availability, not incidental nulls.
        assertTrue(report.reps.any { it.eccentricMs != null || it.concentricMs != null })
        val breakdown = buildBreakdown(report, marksOf(report))!!
        assertEquals(listOf(RepMetricKind.VELOCITY, RepMetricKind.ROM), breakdown.metrics.map { it.kind })
        assertTrue(breakdown.reps.none { it.detail.contains("bajada ") || it.detail.contains("subida ") })
        assertTrue(breakdown.reps.first().detail.contains("ROM 112°"))
        assertTrue(breakdown.reps.first().detail.contains("≈ 0,62 m/s"))
        assertEquals(source.reps.map { it.kind }, breakdown.reps.map { it.kind })
        assertEquals((1..source.reps.size).toList(), breakdown.reps.map { it.number })
        assertTrue(breakdown.metrics.none { it.description.startsWith("Tiempo:") })
    }

    // ── Parciales (M4) ────────────────────────────────────────────────────────

    private val pct = "≈\u00A055\u00A0%\u00A0del\u00A0recorrido"

    @Test
    fun the_summary_line_names_the_zone_and_the_range_of_the_partials() {
        assertEquals("2 parciales en estiramiento ($pct)", buildSummary(PartialsSamples.report()).partialsLine)
        assertEquals(
            "2 parciales acortadas ($pct)",
            buildSummary(PartialsSamples.report(zones = listOf(PartialZone.SHORTENED, PartialZone.SHORTENED))).partialsLine,
        )
        assertEquals(
            "1 parcial en estiramiento ($pct)",
            buildSummary(PartialsSamples.report(zones = listOf(PartialZone.STRETCHED))).partialsLine,
        )
        // Zonas distintas: cada una con su número.
        assertEquals(
            "2 parciales: 1 en estiramiento y 1 acortada ($pct)",
            buildSummary(PartialsSamples.report(zones = listOf(PartialZone.STRETCHED, PartialZone.SHORTENED))).partialsLine,
        )
    }

    @Test
    fun a_medium_partial_count_is_an_estimate_and_a_low_one_is_not_offered_at_all() {
        assertEquals(
            "≈\u00A02 parciales en estiramiento ($pct)",
            buildSummary(PartialsSamples.report(partialConfidence = CountConfidence.MEDIA)).partialsLine,
        )
        val low = PartialsSamples.report(partialConfidence = CountConfidence.BAJA)
        assertNull(buildSummary(low).partialsLine)
        assertFalse(buildSummary(low).tiles.first { it.id == SummaryTileId.REPS }.notes.any { "parcial" in it })
        val bar = buildBreakdown(low, marksOf(low))!!.reps[2]
        assertEquals(RepKind.PARTIAL, bar.kind)
        assertNull(bar.zone)
        assertNull(bar.percent)
        assertEquals("Rep 3 · parcial · bajada 3,0 s", bar.detail)
    }

    @Test
    fun the_count_note_uses_the_own_confidence_of_the_partials_and_falls_back_to_the_reps_one() {
        fun note(report: SetTechniqueReport) = buildSummary(report).tiles.first { it.id == SummaryTileId.REPS }.notes.first()
        assertEquals("+2 parciales", note(PartialsSamples.report(partialConfidence = CountConfidence.ALTA)))
        assertEquals("+≈\u00A02 parciales", note(PartialsSamples.report(partialConfidence = CountConfidence.MEDIA)))
        // Un informe de antes del motor 9 no trae la confianza de las parciales: vale la del conteo.
        assertEquals("+2 parciales", note(PartialsSamples.report(partialConfidence = null)))
        val oldMedium = PartialsSamples.report(partialConfidence = null, base = TechniqueSamples.analysisReportMedium)
        assertEquals("+≈\u00A02 parciales", note(oldMedium))
        // Sin zona (motor viejo) la línea sigue diciendo el recorrido.
        assertEquals(
            "2 parciales ($pct)",
            buildSummary(PartialsSamples.report(partialConfidence = null, zones = listOf(null, null))).partialsLine,
        )
    }

    @Test
    fun every_partial_bar_carries_its_zone_and_range_for_the_detail_and_for_talkback() {
        val report = PartialsSamples.report(zones = listOf(PartialZone.STRETCHED, PartialZone.SHORTENED))
        val breakdown = buildBreakdown(report, marksOf(report))!!
        val third = breakdown.reps[2]
        val sixth = breakdown.reps[5]
        assertEquals(PartialZone.STRETCHED, third.zone)
        assertEquals(55, third.percent)
        assertEquals(PartialZone.SHORTENED, sixth.zone)
        assertEquals("Rep 3 · parcial en estiramiento · $pct · bajada 3,0 s", third.detail)
        assertTrue(sixth.detail.startsWith("Rep 6 · parcial acortada · $pct"))
        assertEquals("parcial en estiramiento, $pct", third.partialSpoken)
        assertNull(breakdown.reps[0].partialSpoken)
        assertEquals(
            "Parciales: la 3, en estiramiento, $pct; la 6, acortada, $pct",
            breakdown.partialsDescription,
        )
        assertEquals(listOf<PartialZone?>(PartialZone.STRETCHED, PartialZone.SHORTENED), breakdown.partialZones)
        // Sin parciales, nada que decir.
        val withoutPartials = TechniqueSamples.analysisReport.copy(reps = TechniqueSamples.analysisReport.reps.filter { it.kind != RepKind.PARTIAL })
        val none = buildBreakdown(withoutPartials, marksOf(withoutPartials))!!
        assertNull(none.partialsDescription)
    }

    @Test
    fun measured_phases_keep_time_chart_and_details_without_requiring_tut_or_tempo_aggregates() {
        val source = TechniqueSamples.analysisReport
        val report = source.copy(
            available = source.available - MetricKey.TUT - MetricKey.TEMPO,
            tutMs = null, tempo = null,
        )
        assertTrue(MetricKey.PHASES in report.available)
        val expected = buildBreakdown(source, marksOf(source))!!
        val actual = buildBreakdown(report, marksOf(report))!!
        assertEquals(expected.metrics.first { it.kind == RepMetricKind.TIME }, actual.metrics.first { it.kind == RepMetricKind.TIME })
        assertEquals(expected.reps, actual.reps)
        assertTrue(actual.reps.first().detail.contains("bajada 2,8 s"))
        assertTrue(actual.reps.first().detail.contains("subida 0,9 s"))
    }

    // ── Revisión del conteo ───────────────────────────────────────────────────

    @Test
    fun a_correction_moves_the_proposal_without_losing_the_original_count() {
        val report = TechniqueSamples.analysisReport
        val breakdown = buildBreakdown(report, marksOf(report))!!
        val partialPosition = breakdown.reps.indexOfFirst { it.kind == RepKind.PARTIAL }
        val review = buildReview(breakdown.reps, mapOf(partialPosition to RepKind.FULL))
        assertEquals("9", review.proposal)
        assertEquals("8 + 1", review.originalProposal)
        assertTrue(review.changed)
        assertEquals(RepKind.FULL, review.kindAt(partialPosition))
        assertTrue(review.isChangedAt(partialPosition))
        assertEquals(breakdown.reps.count { it.kind == RepKind.FULL } + 1, review.complete)
        assertEquals(breakdown.reps.count { it.kind == RepKind.PARTIAL } - 1, review.partial)
        assertEquals(1, review.failed)
        assertEquals(1, review.originalFailed)
    }

    @Test
    fun failed_reps_are_not_reclassified_and_an_empty_review_is_the_original() {
        val report = TechniqueSamples.analysisReport
        val breakdown = buildBreakdown(report, marksOf(report))!!
        val failedPosition = breakdown.reps.indexOfFirst { it.kind == RepKind.FAILED }
        val review = buildReview(breakdown.reps, mapOf(failedPosition to RepKind.PARTIAL))
        assertEquals(RepKind.FAILED, review.kindAt(failedPosition))
        assertFalse(review.changed)
        assertEquals("8 + 1", review.proposal)
        assertEquals(review.originalProposal, review.proposal)
    }

    @Test
    fun corrections_travel_as_stable_sorted_text_and_broken_entries_are_dropped() {
        val encoded = encodeRepCorrections(mapOf(5 to RepKind.PARTIAL, 2 to RepKind.FULL))
        assertEquals("2:FULL,5:PARTIAL", encoded)
        assertEquals(mapOf(2 to RepKind.FULL, 5 to RepKind.PARTIAL), decodeRepCorrections(encoded))
        assertTrue(decodeRepCorrections("").isEmpty())
        assertEquals(mapOf(3 to RepKind.PARTIAL), decodeRepCorrections("x:PARCIAL,3:PARTIAL,9:NOPE"))
    }
}
