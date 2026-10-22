package com.example.kpkn.data.media.technique

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.PartialZone
import com.example.kpkn.domain.biomechanics.metrics.RangeReferenceSource
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import com.example.kpkn.domain.biomechanics.reps.SignalUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** La referencia PERSONAL de recorrido (M4b), pura: qué análisis anteriores valen y cómo se resume su amplitud. */
class PersonalRangeReferenceTest {
    private fun report(
        amplitude: Double?,
        signal: CountSignal? = CountSignal.DROP_HIP_MID,
        unit: SignalUnit? = SignalUnit.BODY_HEIGHTS,
        view: ViewKind = ViewKind.SIDE,
        confidence: CountConfidence = CountConfidence.ALTA,
        engine: Int = PersonalRangeReference.MIN_ENGINE_VERSION,
    ): SetTechniqueReport = TechniqueReportSamples.fullReport().copy(
        engineVersion = engine, confidence = confidence, view = view,
        countSignal = signal, countUnit = unit, fullAmplitude = amplitude,
    )

    private fun prior(
        id: String,
        at: Long,
        amplitude: Double?,
        signal: CountSignal? = CountSignal.DROP_HIP_MID,
        unit: SignalUnit? = SignalUnit.BODY_HEIGHTS,
        view: ViewKind = ViewKind.SIDE,
        confidence: CountConfidence = CountConfidence.ALTA,
        engine: Int = PersonalRangeReference.MIN_ENGINE_VERSION,
    ) = PersonalRangeReference.Prior(id, at, report(amplitude, signal, unit, view, confidence, engine))

    private fun select(vararg priors: PersonalRangeReference.Prior, self: String = "self") =
        PersonalRangeReference.select(priors.toList(), self)

    @Test
    fun zero_or_one_previous_series_give_no_reference_and_two_or_three_give_their_median() {
        assertNull(select())
        assertNull(select(prior("a", 1, 0.40)))

        val two = select(prior("a", 1, 0.40), prior("b", 2, 0.50))!!
        assertEquals(0.45, two.amplitude, 1e-9)
        assertEquals(2, two.seriesCount)
        assertEquals(RangeReferenceSource.PERSONAL, two.source)
        assertEquals(CountSignal.DROP_HIP_MID, two.signal)

        val three = select(prior("a", 1, 0.40), prior("b", 2, 0.90), prior("c", 3, 0.50))!!
        assertEquals("la mediana, no la media: un atípico no la arrastra", 0.50, three.amplitude, 1e-9)
        assertEquals(3, three.seriesCount)
    }

    @Test
    fun the_video_itself_never_counts() {
        assertNull(select(prior("self", 9, 0.60), prior("a", 1, 0.40)))
        val withSelf = select(prior("self", 9, 5.00), prior("a", 1, 0.40), prior("b", 2, 0.50))!!
        assertEquals(2, withSelf.seriesCount)
        assertEquals(0.45, withSelf.amplitude, 1e-9)
    }

    @Test
    fun only_series_measured_with_the_same_count_signal_are_pooled() {
        assertNull("una de cada señal no suma dos", select(prior("a", 1, 0.40), prior("b", 2, 0.50, signal = CountSignal.RISE_HAND)))
        val pooled = select(
            prior("a", 1, 0.40), prior("b", 2, 0.50), prior("c", 3, 3.00, signal = CountSignal.RISE_HAND),
        )!!
        assertEquals(CountSignal.DROP_HIP_MID, pooled.signal)
        assertEquals(2, pooled.seriesCount)
        assertEquals(0.45, pooled.amplitude, 1e-9)
    }

    @Test
    fun an_angle_is_pooled_per_view_but_a_body_height_displacement_is_not() {
        val angles = select(
            prior("a", 1, 100.0, unit = SignalUnit.DEGREES, view = ViewKind.SIDE),
            prior("b", 2, 90.0, unit = SignalUnit.DEGREES, view = ViewKind.FRONT),
            prior("c", 3, 94.0, unit = SignalUnit.DEGREES, view = ViewKind.FRONT),
        )!!
        assertEquals("gana el grupo con más series", ViewKind.FRONT, angles.view)
        assertEquals(2, angles.seriesCount)
        assertEquals(92.0, angles.amplitude, 1e-9)

        val displacement = select(
            prior("a", 1, 0.40, view = ViewKind.SIDE), prior("b", 2, 0.50, view = ViewKind.FRONT),
        )!!
        assertEquals("el desplazamiento no depende de la vista", 2, displacement.seriesCount)
        assertNull(displacement.view)
    }

    @Test
    fun a_tie_between_groups_goes_to_the_most_recent_series() {
        val reference = select(
            prior("a", 1, 100.0, unit = SignalUnit.DEGREES, view = ViewKind.SIDE),
            prior("b", 2, 102.0, unit = SignalUnit.DEGREES, view = ViewKind.SIDE),
            prior("c", 3, 80.0, unit = SignalUnit.DEGREES, view = ViewKind.FRONT),
            prior("d", 4, 84.0, unit = SignalUnit.DEGREES, view = ViewKind.FRONT),
        )!!
        assertEquals(ViewKind.FRONT, reference.view)
        assertEquals(82.0, reference.amplitude, 1e-9)
    }

    @Test
    fun low_confidence_old_engine_and_unmeasured_series_do_not_count() {
        assertNull(select(prior("a", 1, 0.40), prior("b", 2, 0.50, confidence = CountConfidence.BAJA)))
        assertNull(select(prior("a", 1, 0.40), prior("b", 2, 0.50, engine = 8)))
        assertNull(select(prior("a", 1, 0.40), prior("b", 2, null)))
        assertNull(select(prior("a", 1, 0.40), prior("b", 2, 0.0)))
        assertNull(select(prior("a", 1, 0.40), prior("b", 2, Double.NaN)))
        assertNull(select(prior("a", 1, 0.40), prior("b", 2, 0.50, signal = null)))
        // MEDIA sí vale.
        assertNotNull(select(prior("a", 1, 0.40), prior("b", 2, 0.50, confidence = CountConfidence.MEDIA)))
    }

    @Test
    fun only_the_twelve_most_recent_series_are_used() {
        val priors = (1..14).map { i -> prior("p$i", i.toLong(), if (i <= 2) 9.0 else 0.50) }
        val reference = PersonalRangeReference.select(priors, "self")!!
        assertEquals(PersonalRangeReference.MAX_SERIES, reference.seriesCount)
        assertEquals("las dos más antiguas, con 9,0, quedan fuera", 0.50, reference.amplitude, 1e-9)
    }

    @Test
    fun the_partial_summary_keeps_the_own_confidence_and_a_zone_only_when_it_is_shared() {
        val base = TechniqueReportSamples.fullReport()
        val partial = base.reps.first { it.kind == RepKind.PARTIAL }
        fun withZones(vararg zones: PartialZone?, confidence: CountConfidence? = CountConfidence.MEDIA) = base.copy(
            reps = base.reps.filter { it.kind != RepKind.PARTIAL } + zones.mapIndexed { i, zone -> partial.copy(index = 10 + i, zone = zone) },
            partialConfidence = confidence,
        )

        assertEquals(PartialSummary(CountConfidence.MEDIA, PartialZone.STRETCHED), PartialSummary.of(withZones(PartialZone.STRETCHED)))
        assertEquals(PartialZone.SHORTENED, PartialSummary.of(withZones(PartialZone.SHORTENED, PartialZone.SHORTENED)).zone)
        assertNull("zonas distintas", PartialSummary.of(withZones(PartialZone.STRETCHED, PartialZone.SHORTENED)).zone)
        assertNull("una parcial sin zona", PartialSummary.of(withZones(PartialZone.STRETCHED, null)).zone)
        assertNull("sin parciales", PartialSummary.of(withZones()).zone)
        assertNull("un informe de antes del motor 9", PartialSummary.of(withZones(PartialZone.STRETCHED, confidence = null)).confidence)
    }
}
