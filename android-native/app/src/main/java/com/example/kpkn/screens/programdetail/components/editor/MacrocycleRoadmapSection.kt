package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import com.example.kpkn.data.models.Program
import com.example.kpkn.screens.programdetail.components.AdvancedRoadmap
import com.example.kpkn.screens.programdetail.components.LegacyAdvancedRoadmapSection

/** Roadmap-only surface for the advanced macrocycle editor. */
@Composable
internal fun MacrocycleRoadmapSection(
    program: Program,
    roadmap: AdvancedRoadmap,
    onFocusWeek: (blockId: String, weekId: String) -> Unit,
    onCreateSessionForWeek: (weekId: String, preferredDayOfWeek: Int, keyDateId: String?) -> Unit,
    onAdjustBlockWeeks: (blockId: String, deltaWeeks: Int) -> Unit,
) {
    LegacyAdvancedRoadmapSection(
        program = program,
        roadmap = roadmap,
        onFocusWeek = onFocusWeek,
        onCreateSessionForWeek = onCreateSessionForWeek,
        onAdjustBlockWeeks = onAdjustBlockWeeks,
    )
}
