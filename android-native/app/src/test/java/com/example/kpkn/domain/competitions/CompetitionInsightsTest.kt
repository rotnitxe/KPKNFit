package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CompetitionInsightsTest {

    private val today = LocalDate.of(2026, 10, 28)
    private val resolve: (CompetitionRecord) -> CompetitionDiscipline = { CompetitionDiscipline.of(it) }

    @Test
    fun hub_splits_the_next_one_from_the_history_and_counts_disciplines() {
        val records = listOf(
            CompetitionRecord(id = "next", title = "Nacional", eventDate = "2026-11-16", discipline = "powerlifting"),
            CompetitionRecord(id = "pl", title = "Regional", eventDate = "2026-03-01", discipline = "powerlifting", status = CompetitionRecordStatus.COMPLETED),
            CompetitionRecord(id = "run", title = "Media", eventDate = "2026-05-01", discipline = "carrera", status = CompetitionRecordStatus.COMPLETED),
            CompetitionRecord(id = "old", title = "Archivada", eventDate = "2025-05-01", discipline = "carrera", status = CompetitionRecordStatus.ARCHIVED),
        )
        val model = CompetitionInsights.hub(records, today, resolve)
        assertEquals("next", model.next?.id)
        assertEquals(19L, model.next?.daysUntil)
        assertEquals(listOf("run", "pl"), model.history.map { it.id })
        assertEquals("2 · 2 disciplinas", model.historyCount)
        assertTrue(model.chart.isEmpty())
    }

    @Test
    fun empty_hub_is_flagged() {
        val model = CompetitionInsights.hub(emptyList(), today, resolve)
        assertTrue(model.isEmpty)
        assertNull(model.next)
        assertEquals("", model.historyCount)
    }

    @Test
    fun pace_plan_has_three_segments_like_the_canvas() {
        val marathon = CompetitionRecord(id = "m", title = "", distanceKm = 42.195, targetTimeSeconds = 12_600.0)
        val segments = CompetitionInsights.paceSegments(marathon)
        assertEquals(listOf("0–10 km", "10–30 km", "30–42,2 km"), segments.map { it.label })
        assertEquals(listOf("5:04 /km", "4:58 /km", "4:54 /km"), segments.map { it.pace })
        assertTrue(CompetitionInsights.paceSegments(marathon.copy(distanceKm = 3.0)).isEmpty())
    }

    @Test
    fun riegel_range_grows_with_distance() {
        val (lo, hi) = CompetitionInsights.predictRange(21.0975, 5_892.0, 42.195)!!
        assertTrue(lo > 2 * 5_892.0)
        assertTrue(hi > lo)
        assertNull(CompetitionInsights.predictRange(0.0, 5_892.0, 42.195))
    }

    @Test
    fun fastest_split_caption_names_the_last_km_or_its_number() {
        assertEquals("Último km el más rápido: 4:21", CompetitionInsights.fastestSplitCaption(listOf(280.0, 275.0, 261.0)))
        assertEquals("Km más rápido: el 2, 4:21", CompetitionInsights.fastestSplitCaption(listOf(280.0, 261.0, 275.0)))
        assertNull(CompetitionInsights.fastestSplitCaption(emptyList()))
    }

    @Test
    fun stage_rate_and_weeks_until() {
        val record = CompetitionRecord(id = "bb", title = "", eventDate = "2026-11-25")
        assertEquals(4L, CompetitionInsights.weeksUntil(record, today))
        assertEquals(-0.5, CompetitionInsights.stageRate(80.0, 78.0, 4L)!!, 1e-9)
        assertNull(CompetitionInsights.stageRate(80.0, 78.0, 0L))
    }

    @Test
    fun format_reads_like_the_canvas() {
        assertEquals("192,5", CompetitionFormat.kg(192.5))
        assertEquals("130", CompetitionFormat.kg(130.0))
        assertEquals("−2,5", CompetitionFormat.signedKg(-2.5))
        assertEquals("1:38:12", CompetitionFormat.duration(5_892.0))
        assertEquals("36:10", CompetitionFormat.duration(2_170.0))
        assertEquals("3.º", CompetitionFormat.ordinal(3))
        assertEquals("21,1 km", CompetitionFormat.km(21.0975))
    }
}
