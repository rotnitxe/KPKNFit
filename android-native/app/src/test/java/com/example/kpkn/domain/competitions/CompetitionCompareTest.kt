package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.CompetitionAttempt
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CompetitionTemplateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CompetitionCompareTest {

    @Test
    fun series_orders_by_date_and_keeps_formula_label_per_meet() {
        val later = meet("b", "2026-06-01", "wrpf", squat = 210.0)
        val earlier = meet("a", "2026-01-01", "ipf", squat = 180.0)
        val series = CompetitionCompare.series(listOf(later, earlier))
        assertEquals(listOf("a", "b"), series.map { it.recordId })
        assertEquals("IPF GL", series[0].pointsLabel)
        assertEquals("DOTS", series[1].pointsLabel)
        assertEquals(180.0, series[0].squatKg)
        assertEquals(210.0, series[1].squatKg)
        assertNotEquals(series[0].points, series[1].points)
    }

    @Test
    fun best_valid_ignores_failed_attempts() {
        val record = meet("x", "2026-02-02", "ipf", squat = 200.0).copy(
            technicalBlocks = listOf(
                CompetitionTechnicalBlock(
                    id = "sq",
                    title = "Sentadilla",
                    movementType = CompetitionMovementType.SQUAT,
                    attempts = listOf(
                        CompetitionAttempt("1", 1, 180.0, resultType = CompetitionAttemptResult.GOOD_LIFT),
                        CompetitionAttempt("2", 2, 200.0, resultType = CompetitionAttemptResult.NO_LIFT),
                        CompetitionAttempt("3", 3, 190.0, resultType = CompetitionAttemptResult.GOOD_LIFT),
                    ),
                ),
                lift(CompetitionMovementType.BENCH, 100.0),
                lift(CompetitionMovementType.DEADLIFT, 200.0),
            ),
        )
        assertEquals(190.0, CompetitionCompare.toPoint(record).squatKg)
    }

    private fun meet(
        id: String,
        date: String,
        federationId: String,
        squat: Double,
    ) = CompetitionRecord(
        id = id,
        title = id,
        eventDate = date,
        sportType = CompetitionTemplateType.POWERLIFTING,
        status = CompetitionRecordStatus.COMPLETED,
        federationId = federationId,
        bodyweightKg = 90.0,
        technicalBlocks = listOf(
            lift(CompetitionMovementType.SQUAT, squat),
            lift(CompetitionMovementType.BENCH, 100.0),
            lift(CompetitionMovementType.DEADLIFT, 200.0),
        ),
    )

    private fun lift(type: CompetitionMovementType, kg: Double) = CompetitionTechnicalBlock(
        id = type.name,
        title = type.name,
        movementType = type,
        attempts = listOf(
            CompetitionAttempt("${type.name}-1", 1, kg, resultType = CompetitionAttemptResult.GOOD_LIFT),
        ),
    )
}
