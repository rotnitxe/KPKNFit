package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.CompetitionAttempt
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CompetitionTemplateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CompetitionScoringTest {

    @Test
    fun ipf_meet_uses_gl_and_not_dots() {
        val scored = CompetitionScoring.recalculate(meet("ipf"))
        val points = CompetitionScoring.displayedPoints(scored)
        assertEquals(PowerliftingPointsFormula.IPF_GL, points?.formula)
        assertEquals("IPF GL", points?.label)
        assertNotNull(scored.powerliftingDetails?.ipfGlPoints)
        assertNull(scored.powerliftingDetails?.dotsPoints)
        assertNull(scored.powerliftingDetails?.wilksPoints)
    }

    @Test
    fun wrpf_meet_uses_dots_not_ipf_gl() {
        val scored = CompetitionScoring.recalculate(meet("wrpf"))
        val points = CompetitionScoring.displayedPoints(scored)
        assertEquals(PowerliftingPointsFormula.DOTS, points?.formula)
        assertEquals("DOTS", points?.label)
        assertNotNull(scored.powerliftingDetails?.dotsPoints)
        assertNull(scored.powerliftingDetails?.ipfGlPoints)
    }

    @Test
    fun wpc_meet_uses_wilks_not_ipf_gl() {
        val scored = CompetitionScoring.recalculate(meet("wpc"))
        val points = CompetitionScoring.displayedPoints(scored)
        assertEquals(PowerliftingPointsFormula.WILKS, points?.formula)
        assertEquals("Wilks", points?.label)
        assertNull(scored.powerliftingDetails?.ipfGlPoints)
    }

    @Test
    fun same_total_different_formulas_are_not_forced_equal() {
        val ipf = CompetitionScoring.displayedPoints(CompetitionScoring.recalculate(meet("ipf")))
        val wrpf = CompetitionScoring.displayedPoints(CompetitionScoring.recalculate(meet("wrpf")))
        assertNotNull(ipf)
        assertNotNull(wrpf)
        assertNotEquals(ipf!!.formula, wrpf!!.formula)
        assertNotEquals(ipf.value, wrpf.value, 0.001)
    }

    private fun meet(federationId: String) = CompetitionRecord(
        id = "m-$federationId",
        title = "Open",
        sportType = CompetitionTemplateType.POWERLIFTING,
        federationId = federationId,
        bodyweightKg = 90.0,
        technicalBlocks = listOf(
            lift(CompetitionMovementType.SQUAT, 200.0),
            lift(CompetitionMovementType.BENCH, 120.0),
            lift(CompetitionMovementType.DEADLIFT, 230.0),
        ),
    )

    private fun lift(type: CompetitionMovementType, kg: Double) = CompetitionTechnicalBlock(
        id = type.name,
        title = type.name,
        movementType = type,
        attempts = listOf(
            CompetitionAttempt(
                id = "${type.name}-1",
                attemptNumber = 1,
                weightKg = kg,
                resultType = CompetitionAttemptResult.GOOD_LIFT,
            ),
        ),
    )
}
