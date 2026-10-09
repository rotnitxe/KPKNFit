package com.example.kpkn.domain.training.periodization

import com.example.kpkn.domain.training.periodization.PhaseKind.ACCUMULATION
import com.example.kpkn.domain.training.periodization.PhaseKind.DELOAD
import com.example.kpkn.domain.training.periodization.PhaseKind.INTENSIFICATION
import com.example.kpkn.domain.training.periodization.PhaseKind.PEAK
import com.example.kpkn.domain.training.periodization.PhaseKind.REALIZATION
import com.example.kpkn.domain.training.periodization.PhaseKind.TAPER
import com.example.kpkn.domain.training.periodization.PhaseKind.TEST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodLayoutTest {

    /** Lunes 5 de octubre de 2026: el inicio de todas las pruebas con fecha. */
    private val anchor = LocalDate.of(2026, 10, 5)

    private fun span(kind: PhaseKind, weeks: Int) = PhaseSpan(kind, weeks)

    /** Tramos con la competición en la semana [weeks] (contando desde el ancla, que es la semana 1). */
    private fun dated(weeks: Int, variant: ProgramVariant = ProgramVariant.PERIODIZED, heavy: Boolean = false): List<PhaseSpan> {
        val meet = anchor.plusWeeks((weeks - 1).toLong()).plusDays(2)
        return PeriodLayout.phases(variant, Horizon.ToMeet(anchor, meet, weekStartDay = 1), heavy)
    }

    private fun withoutDate(variant: ProgramVariant, weeks: Int): List<PhaseSpan> =
        PeriodLayout.phases(variant, Horizon.Weeks(weeks), heavy = false)

    @Test
    fun simple_has_no_phases_for_any_horizon() {
        assertTrue(PeriodLayout.phases(ProgramVariant.SIMPLE, Horizon.Simple, heavy = false).isEmpty())
        assertTrue(PeriodLayout.phases(ProgramVariant.SIMPLE, Horizon.Weeks(12), heavy = false).isEmpty())
    }

    @Test
    fun blocks_without_date_are_accumulation_intensification_and_deload() {
        assertEquals(listOf(span(ACCUMULATION, 3), span(INTENSIFICATION, 2), span(DELOAD, 1)), withoutDate(ProgramVariant.BLOCKS, 6))
    }

    @Test
    fun periodized_without_date_follows_the_brief_templates() {
        assertEquals(
            listOf(span(ACCUMULATION, 4), span(INTENSIFICATION, 2), span(REALIZATION, 2), span(DELOAD, 1), span(TEST, 1)),
            withoutDate(ProgramVariant.PERIODIZED, 10),
        )
        assertEquals(
            listOf(span(ACCUMULATION, 5), span(INTENSIFICATION, 3), span(REALIZATION, 2), span(DELOAD, 1), span(TEST, 1)),
            withoutDate(ProgramVariant.PERIODIZED, 12),
        )
        // Siete semanas de acumulación pasan de seis: una descarga la parte, y el total sigue siendo 16.
        assertEquals(
            listOf(
                span(ACCUMULATION, 3), span(DELOAD, 1), span(ACCUMULATION, 3), span(INTENSIFICATION, 4),
                span(REALIZATION, 3), span(DELOAD, 1), span(TEST, 1),
            ),
            withoutDate(ProgramVariant.PERIODIZED, 16),
        )
    }

    @Test
    fun periodized_without_date_only_accepts_10_12_or_16_weeks() {
        try {
            withoutDate(ProgramVariant.PERIODIZED, 8)
            throw AssertionError("8 semanas no es una plantilla periodizada")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().isNotBlank())
        }
    }

    @Test
    fun dated_layouts_end_with_the_competition_and_keep_the_week_count() {
        assertEquals(listOf(span(ACCUMULATION, 6), span(INTENSIFICATION, 3), span(PEAK, 2), span(TAPER, 1)), dated(12))
        assertEquals(listOf(span(ACCUMULATION, 5), span(INTENSIFICATION, 3), span(PEAK, 2), span(TAPER, 2)), dated(12, heavy = true))
        assertEquals(listOf(span(ACCUMULATION, 3), span(INTENSIFICATION, 2), span(PEAK, 2), span(TAPER, 1)), dated(8))
        assertEquals(listOf(span(ACCUMULATION, 2), span(INTENSIFICATION, 1), span(PEAK, 1), span(TAPER, 1)), dated(5, ProgramVariant.BLOCKS))
        assertEquals(listOf(span(INTENSIFICATION, 1), span(PEAK, 1), span(TAPER, 1)), dated(3))
        assertEquals(listOf(span(PEAK, 1), span(TAPER, 1)), dated(2))
        assertTrue(dated(1).isEmpty())
    }

    @Test
    fun dated_layouts_total_the_weeks_until_the_competition() {
        listOf(12, 8, 5, 3, 2).forEach { weeks ->
            assertEquals("$weeks semanas", weeks, dated(weeks).sumOf { it.weeks })
        }
    }

    @Test
    fun meet_before_the_anchor_leaves_no_phases() {
        val meet = anchor.minusWeeks(2)
        assertTrue(PeriodLayout.phases(ProgramVariant.PERIODIZED, Horizon.ToMeet(anchor, meet, weekStartDay = 1), heavy = false).isEmpty())
    }

    @Test
    fun phases_keep_the_order_and_deload_or_taper_are_exempt() {
        val layouts = listOf(
            withoutDate(ProgramVariant.BLOCKS, 6),
            withoutDate(ProgramVariant.PERIODIZED, 10),
            withoutDate(ProgramVariant.PERIODIZED, 12),
            withoutDate(ProgramVariant.PERIODIZED, 16),
            dated(12),
            dated(12, heavy = true),
            dated(8),
            dated(5, ProgramVariant.BLOCKS),
            dated(3),
            dated(2),
        )
        val rank = mapOf(ACCUMULATION to 0, INTENSIFICATION to 1, REALIZATION to 3, PEAK to 3, TEST to 3)
        layouts.forEach { layout ->
            val ranks = layout.filter { it.kind in rank }.map { rank.getValue(it.kind) }
            assertEquals(ranks.sorted(), ranks)
        }
    }

    @Test
    fun the_layout_does_not_depend_on_marks_or_clock() {
        // Mismo horizonte, mismas fases: la función no recibe nada más que variante, horizonte y si es pesada.
        assertEquals(dated(12), dated(12))
        assertEquals(withoutDate(ProgramVariant.PERIODIZED, 12), withoutDate(ProgramVariant.PERIODIZED, 12))
    }
}
