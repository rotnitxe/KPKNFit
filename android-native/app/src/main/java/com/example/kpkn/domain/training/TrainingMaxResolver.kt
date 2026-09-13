package com.example.kpkn.domain.training

import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.protocols.LiftSlot

object TrainingMaxResolver {
    fun oneRm(profile: PowerliftingProfile?, slot: LiftSlot): Double? {
        if (profile == null) return null
        return when (slot) {
            LiftSlot.SQUAT -> profile.squat1RM ?: profile.squatE1RM
            LiftSlot.BENCH -> profile.bench1RM ?: profile.benchE1RM
            LiftSlot.DEADLIFT -> profile.deadlift1RM ?: profile.deadliftE1RM
            LiftSlot.OVERHEAD -> profile.overhead1RM ?: profile.overheadE1RM
        }?.takeIf { it > 0.0 }
    }

    fun trainingMax(profile: PowerliftingProfile?, slot: LiftSlot, trainingMaxPercent: Double): Double? {
        if (profile == null) return null
        val stored = when (slot) {
            LiftSlot.SQUAT -> profile.squatTM
            LiftSlot.BENCH -> profile.benchTM
            LiftSlot.DEADLIFT -> profile.deadliftTM
            LiftSlot.OVERHEAD -> profile.overheadTM
        }
        if (stored != null && stored > 0.0) return stored
        val raw = when (slot) {
            LiftSlot.SQUAT -> profile.squat1RM ?: profile.squatE1RM
            LiftSlot.BENCH -> profile.bench1RM ?: profile.benchE1RM
            LiftSlot.DEADLIFT -> profile.deadlift1RM ?: profile.deadliftE1RM
            LiftSlot.OVERHEAD -> profile.overhead1RM ?: profile.overheadE1RM
        } ?: return null
        return raw * trainingMaxPercent
    }

    fun loadKg(percent: Double, trainingMax: Double?): Double? {
        if (trainingMax == null || trainingMax <= 0.0) return null
        return (percent / 100.0) * trainingMax
    }

    fun hydrateProfile(profile: PowerliftingProfile, trainingMaxPercent: Double): PowerliftingProfile {
        fun tm(current: Double?, oneRm: Double?, e1rm: Double?): Double? =
            current ?: listOfNotNull(oneRm, e1rm).firstOrNull()?.times(trainingMaxPercent)
        return profile.copy(
            squatTM = tm(profile.squatTM, profile.squat1RM, profile.squatE1RM),
            benchTM = tm(profile.benchTM, profile.bench1RM, profile.benchE1RM),
            deadliftTM = tm(profile.deadliftTM, profile.deadlift1RM, profile.deadliftE1RM),
            overheadTM = tm(profile.overheadTM, profile.overhead1RM, profile.overheadE1RM),
        )
    }
}
