package com.example.kpkn.data.media

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import kotlin.math.abs

/**
 * Reconciles `isPr` on captured media from homologatedResultV3 and `pr_e1rm` milestones.
 */
object WorkoutMediaPrFlags {
    fun completedSetKey(exerciseId: String, setIndex: Int, side: String?): String {
        val token = sideToken(side)
        return if (token == null) "${exerciseId}_$setIndex" else "${exerciseId}_${setIndex}_$token"
    }

    fun isPrSet(
        set: CompletedSet,
        exerciseId: String,
        milestones: List<SessionMilestone>,
    ): Boolean {
        val homologated = set.homologatedResultV3
        if (homologated?.isGlobalPr == true || homologated?.isContextPr == true) return true
        if (set.isWarmup || set.skipped) return false
        val milestone = milestones
            .filter { it.exerciseId == exerciseId && it.kind == "pr_e1rm" }
            .maxByOrNull { it.value }
            ?: return false
        if (set.weight <= 0.0 || set.reps <= 0) return false
        val e1rm = calculateHybrid1RM(set.weight, set.reps)
        return abs(e1rm - milestone.value) <= 0.05
    }

    fun idsToMark(
        media: List<WorkoutMedia>,
        completedSets: Map<String, CompletedSet>,
        milestones: List<SessionMilestone>,
    ): List<String> {
        return media.mapNotNull { item ->
            val exerciseId = item.exerciseId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val setIndex = item.setIndex ?: return@mapNotNull null
            val set = lookupSet(completedSets, exerciseId, setIndex, item.side) ?: return@mapNotNull null
            if (isPrSet(set, exerciseId, milestones)) item.id else null
        }
    }

    private fun lookupSet(
        completedSets: Map<String, CompletedSet>,
        exerciseId: String,
        setIndex: Int,
        side: String?,
    ): CompletedSet? {
        completedSets[completedSetKey(exerciseId, setIndex, side)]?.let { return it }
        if (!side.isNullOrBlank()) {
            completedSets[completedSetKey(exerciseId, setIndex, null)]?.let { return it }
        }
        return completedSets["${exerciseId}_$setIndex"]
    }

    private fun sideToken(side: String?): String? {
        val raw = side?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (raw.lowercase()) {
            "l", "left" -> "L"
            "r", "right" -> "R"
            else -> raw.take(1).uppercase()
        }
    }
}
