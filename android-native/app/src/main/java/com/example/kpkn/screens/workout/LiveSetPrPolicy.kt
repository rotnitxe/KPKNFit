package com.example.kpkn.screens.workout

import com.example.kpkn.data.media.WorkoutMediaPrFlags
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.HomologatedPerformanceResult
import com.example.kpkn.data.models.SessionMilestone

internal object LiveSetPrPolicy {
    const val SHORTCUT_LABEL = "PR: foto/video"

    fun isPrSeries(
        lastHomologatedResultV3: HomologatedPerformanceResult?,
        sessionCompletedSet: CompletedSet?,
        exerciseId: String,
        milestones: List<SessionMilestone>,
    ): Boolean {
        if (lastHomologatedResultV3?.isGlobalPr == true || lastHomologatedResultV3?.isContextPr == true) {
            return true
        }
        val set = sessionCompletedSet ?: return false
        return WorkoutMediaPrFlags.isPrSet(set, exerciseId, milestones)
    }

    fun shouldShowMediaShortcut(isPrSeries: Boolean): Boolean = isPrSeries
}
