package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.PlannedAttempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompetitionAttemptsTest {

    private val pl = CompetitionDiscipline.POWERLIFTING

    private fun withWeights(vararg rows: List<Double>): CompetitionRecord {
        var record = CompetitionDraft.createEmpty(pl, id = "c1")
        record.technicalBlocks.forEachIndexed { i, block ->
            rows.getOrNull(i)?.forEachIndexed { j, kg -> record = CompetitionAttempts.setWeight(record, block.id, j + 1, kg) }
        }
        return record
    }

    @Test
    fun a_tap_toggles_between_good_and_no_lift() {
        assertEquals(CompetitionAttemptResult.GOOD_LIFT, CompetitionAttempts.toggled(CompetitionAttemptResult.PENDING))
        assertEquals(CompetitionAttemptResult.NO_LIFT, CompetitionAttempts.toggled(CompetitionAttemptResult.GOOD_LIFT))
        assertEquals(CompetitionAttemptResult.GOOD_LIFT, CompetitionAttempts.toggled(CompetitionAttemptResult.NO_LIFT))
        assertEquals(CompetitionAttemptResult.GOOD_LIFT, CompetitionAttempts.toggled(CompetitionAttemptResult.SKIPPED))
    }

    @Test
    fun no_lift_repeats_the_weight_and_good_lift_takes_the_planned_one() {
        val base = withWeights(listOf(125.0)).copy(
            plannedAttempts = listOf(PlannedAttempt(CompetitionMovementType.SQUAT, 2, 132.5), PlannedAttempt(CompetitionMovementType.SQUAT, 3, 137.5)),
        )
        val missed = CompetitionAttempts.decide(base, pl, CompetitionAttemptResult.NO_LIFT)
        val squatMissed = missed.technicalBlocks.first()
        assertEquals(CompetitionAttemptResult.NO_LIFT, squatMissed.attempts.first { it.attemptNumber == 1 }.resultType)
        assertEquals(125.0, squatMissed.attempts.first { it.attemptNumber == 2 }.weightKg!!, 0.0)

        val made = CompetitionAttempts.decide(base, pl, CompetitionAttemptResult.GOOD_LIFT)
        assertEquals(132.5, made.technicalBlocks.first().attempts.first { it.attemptNumber == 2 }.weightKg!!, 0.0)
    }

    @Test
    fun total_drops_when_a_valid_attempt_becomes_a_no_lift() {
        var record = withWeights(listOf(120.0, 130.0, 140.0), listOf(80.0), listOf(180.0))
        val (squat, bench, dead) = record.technicalBlocks
        record = CompetitionAttempts.toggle(record, squat.id, 1)
        record = CompetitionAttempts.toggle(record, squat.id, 2)
        record = CompetitionAttempts.toggle(record, bench.id, 1)
        record = CompetitionAttempts.toggle(record, dead.id, 1)
        assertEquals(390.0, CompetitionAttempts.total(record, pl)!!, 0.0)
        record = CompetitionAttempts.toggle(record, squat.id, 2)
        assertEquals(380.0, CompetitionAttempts.total(record, pl)!!, 0.0)
    }

    @Test
    fun total_is_null_until_something_is_valid() {
        assertNull(CompetitionAttempts.total(withWeights(listOf(120.0)), pl))
    }

    @Test
    fun next_caption_reads_like_the_canvas() {
        val benchRecord = withWeights(emptyList(), listOf(125.0, 132.5)).copy(
            plannedAttempts = listOf(PlannedAttempt(CompetitionMovementType.BENCH, 3, 137.5)),
        )
        val benchRow = benchRecord.technicalBlocks[1]
        val cell = AttemptCell(benchRow, benchRow.attempts.first { it.attemptNumber == 2 })
        assertEquals("Si entra: 3.º a 137,5 · si no: repites 132,5", CompetitionAttempts.nextCaption(benchRecord, cell))
        val last = AttemptCell(benchRow, benchRow.attempts.first { it.attemptNumber == 3 })
        assertEquals("Último intento de press banca", CompetitionAttempts.nextCaption(benchRecord, last))
    }

    @Test
    fun total_if_all_make_sums_the_third_attempts() {
        val planned = listOf(
            PlannedAttempt(CompetitionMovementType.SQUAT, 3, 200.0),
            PlannedAttempt(CompetitionMovementType.BENCH, 3, 130.0),
            PlannedAttempt(CompetitionMovementType.DEADLIFT, 3, 240.0),
            PlannedAttempt(CompetitionMovementType.DEADLIFT, 2, 230.0),
        )
        assertEquals(570.0, CompetitionAttempts.totalIfAllMake(planned)!!, 0.0)
    }
}
