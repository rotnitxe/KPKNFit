package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.approach.ApproachLevel
import com.example.kpkn.domain.training.approach.ApproachRamp

data class WarmupSet(val weightKg: Double,val reps: Int)
data class LiftMeetPlan(val movement: CompetitionMovementType,val opener: Double,
    val warmups: List<WarmupSet>,val second: Double,val third: Double)

object MeetDayPlan {
    fun build(planned: List<PlannedAttempt>): List<LiftMeetPlan> = planned.groupBy { it.movementType }.mapNotNull { (movement,attempts) ->
        if (movement !in AttemptPlanner.lifts || attempts.size != 3 || attempts.map { it.attemptNumber }.toSet() != setOf(1,2,3)) return@mapNotNull null
        val ordered = attempts.sortedBy { it.attemptNumber }
        if (ordered.any { !it.weightKg.isFinite() || it.weightKg <= 0 } || ordered.zipWithNext().any { it.first.weightKg > it.second.weightKg }) return@mapNotNull null
        val opener = ordered[0].weightKg
        val warmups = ApproachRamp.long(ApproachLevel.ADVANCED,true).map {
            WarmupSet(AttemptPlanner.roundKg(opener * it.percent / 100),it.reps)
        }.filter { it.weightKg > 0 && it.weightKg < opener && it.weightKg <= opener * 0.92 }.distinctBy { it.weightKg }
        LiftMeetPlan(movement,opener,warmups,ordered[1].weightKg,ordered[2].weightKg)
    }

    /** Estado orientativo local. Una ausencia de resultado nunca autoriza otro intento. */
    fun nextAttempt(plan: LiftMeetPlan,attemptNumber: Int,result: CompetitionAttemptResult): Double? = when {
        attemptNumber !in 1..2 -> null
        result == CompetitionAttemptResult.NO_LIFT -> if (attemptNumber == 1) plan.opener else plan.second
        result == CompetitionAttemptResult.GOOD_LIFT -> if (attemptNumber == 1) plan.second else plan.third
        else -> null
    }
}
