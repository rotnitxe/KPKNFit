package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class CompetitionPlanningTest {
    private val squat = CompetitionMovementType.SQUAT
    @Test fun editableStrategiesUseExplicitTwoPointFiveKgRounding() {
        assertEquals(listOf(182.5,192.5,200.0),AttemptPlanner.plan(squat,200.0).map { it.weightKg })
        assertEquals(listOf(175.0,187.5,195.0),AttemptPlanner.plan(squat,200.0,AttemptStrategy.CONSERVATIVE).map { it.weightKg })
        assertEquals(listOf(92.5,97.5,102.5),AttemptPlanner.plan(squat,100.0,AttemptStrategy.AMBITIOUS).map { it.weightKg })
        assertTrue(AttemptPlanner.plan(squat,Double.NaN).isEmpty())
        assertTrue(AttemptPlanner.plan(squat,-2.0).isEmpty())
        assertTrue(AttemptPlanner.plan(CompetitionMovementType.RUN,200.0).isEmpty())
    }
    @Test fun expectedMaxHonoursValidReferencePriorityAndUnits() {
        val p = PowerliftingProfile(squat1RM = 200.0,squatE1RM = 195.0,squatTM = 180.0)
        assertEquals(200.0,AttemptPlanner.expectedMaxOf(p,squat,0.9)!!,0.0)
        assertEquals(195.0,AttemptPlanner.expectedMaxOf(p.copy(squat1RM = Double.NaN),squat,0.9)!!,0.0)
        assertEquals(200.0,AttemptPlanner.expectedMaxOf(p.copy(squat1RM = null,squatE1RM = null),squat,0.9)!!,1e-9)
        assertNull(AttemptPlanner.expectedMaxOf(p.copy(squat1RM = null,squatE1RM = null),squat,0.0))
        assertNull(AttemptPlanner.expectedMaxOf(p.copy(squatVariant = SquatVariant.FRONT_SQUAT),squat,0.9))
    }
    @Test fun forecastIsOnlyArithmeticAndMissingTrendIsExplicit() {
        val within = WeighInPlanner.forecast(84.0,83.0,-0.3,28)
        assertEquals(82.8,within.projectedKg,1e-9)
        assertTrue(within.makesWeight)
        val outside = WeighInPlanner.forecast(84.0,83.0,-0.1,28)
        assertEquals(83.6,outside.projectedKg,1e-9)
        assertFalse(outside.makesWeight)
        assertTrue(outside.message.contains("no una pauta"))
        val unknown = WeighInPlanner.forecast(84.0,83.0,null,28)
        assertEquals(84.0,unknown.projectedKg,0.0)
        assertTrue(unknown.message.contains("Sin tendencia"))
        assertTrue(WeighInPlanner.forecast(84.0,83.0,0.0,0).message.contains("Confirma el peso real"))
    }
    @Test fun meetDayRejectsAmbiguousAttemptsAndKeepsWarmupsBelowOpener() {
        val attempts = listOf(180.0,190.0,200.0).mapIndexed { i,w -> PlannedAttempt(squat,i + 1,w) }
        val plan = MeetDayPlan.build(attempts).single()
        assertTrue(plan.warmups.zipWithNext().all { it.first.weightKg < it.second.weightKg })
        assertTrue(plan.warmups.all { it.weightKg < 180 && it.weightKg <= 180 * 0.92 })
        assertTrue(plan.warmups.last().weightKg >= 150)
        assertTrue(MeetDayPlan.build(attempts + attempts[0]).isEmpty())
        assertTrue(MeetDayPlan.build(attempts.dropLast(1)).isEmpty())
        assertEquals(180.0,MeetDayPlan.nextAttempt(plan,1,CompetitionAttemptResult.NO_LIFT)!!,0.0)
        assertEquals(190.0,MeetDayPlan.nextAttempt(plan,1,CompetitionAttemptResult.GOOD_LIFT)!!,0.0)
        assertEquals(190.0,MeetDayPlan.nextAttempt(plan,2,CompetitionAttemptResult.NO_LIFT)!!,0.0)
        assertNull(MeetDayPlan.nextAttempt(plan,3,CompetitionAttemptResult.NO_LIFT))
        assertNull(MeetDayPlan.nextAttempt(plan,1,CompetitionAttemptResult.PENDING))
    }
    private fun result(vararg weights: Double): CompetitionRecord = CompetitionRecord("meet","Meet",
        status = CompetitionRecordStatus.COMPLETED,technicalBlocks = listOf(squat,CompetitionMovementType.BENCH,CompetitionMovementType.DEADLIFT).zip(weights.toList()).map { (m,w) ->
            CompetitionTechnicalBlock(m.name,m.name,movementType = m,attempts = listOf(
                CompetitionAttempt("${m.name}-1",1,weightKg = w,resultType = CompetitionAttemptResult.GOOD_LIFT),
                CompetitionAttempt("${m.name}-2",2,weightKg = w + 50,resultType = CompetitionAttemptResult.NO_LIFT)))
        })
    @Test fun onlyHigherValidResultsChangeProfileAndVariantsArePreserved() {
        val current = PowerliftingProfile(squat1RM = 190.0,bench1RM = 125.0,deadlift1RM = 220.0,benchTM = 100.0)
        val proposal = MeetResultToProfile.propose(result(200.0,120.0,230.0),current,0.9)!!
        assertEquals(200.0,proposal.profile.squat1RM!!,0.0)
        assertEquals(125.0,proposal.profile.bench1RM!!,0.0)
        assertEquals(230.0,proposal.profile.deadlift1RM!!,0.0)
        assertEquals(100.0,proposal.profile.benchTM!!,0.0)
        assertEquals(current.benchVariant,proposal.profile.benchVariant)
        assertNull(MeetResultToProfile.propose(result(180.0,110.0,200.0),current,0.9))
        assertNull(MeetResultToProfile.propose(result(200.0).copy(meetResultAppliedAtMs = 1),current,0.9))
        assertNull(MeetResultToProfile.propose(result(200.0).copy(status = CompetitionRecordStatus.PLANNED),current,0.9))
        val incompatible = current.copy(squatVariant = SquatVariant.FRONT_SQUAT,benchVariant = BenchVariant.DUMBBELL,deadliftVariant = DeadliftVariant.HEX_BAR)
        assertNull(MeetResultToProfile.propose(result(200.0,140.0,230.0),incompatible,0.9))
    }
}
