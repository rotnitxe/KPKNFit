package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.design.WorkoutRouteBody
import com.example.kpkn.screens.workout.design.workoutRouteGroups
import com.example.kpkn.screens.workout.design.workoutRouteSummary
import com.example.kpkn.data.models.SupersetGroup

import com.example.kpkn.data.models.SessionPart

@Composable
internal fun UnifiedExerciseCarousel(
    exercises: List<Exercise>,
    parts: List<SessionPart> = emptyList(),
    supersetGroups: List<SupersetGroup> = emptyList(),
    currentIdx: Int,
    currentSetIdx: Int = 0,
    completedSets: Map<String, CompletedSet>,
    onSelect: (Int) -> Unit,
    onSelectGroup: (String) -> Unit = {},
    onOpenContext: (String) -> Unit = {},
    enableLongPress: Boolean = true,
) {
    // Las pruebas de la ruta (LS4) montan el mismo cuerpo que la hoja de producción, sin el roadmap de antes.
    WorkoutRouteBody(
        summary = workoutRouteSummary(
            currentIdx = currentIdx,
            exerciseCount = exercises.size,
            setsDone = WorkoutStepRules.completedRoadmapSlotsForExercises(exercises, completedSets),
            setsTotal = WorkoutStepRules.totalRoadmapSlotsForExercises(exercises),
        ),
        groups = workoutRouteGroups(
            exercises = exercises,
            supersetGroups = supersetGroups,
            currentIdx = currentIdx,
            currentSetIdx = currentSetIdx,
            completedSets = completedSets,
            omittedSetKeys = emptySet(),
        ),
        accent = SymbolPalette.musculo,
        godModeUndoCount = 0,
        onClose = {},
        onSelectExercise = onSelect,
        onSelectStep = {},
        onOpenContext = { id -> if (enableLongPress) onOpenContext(id) },
        onOpenMemberContext = { id -> if (enableLongPress) onOpenContext(id) },
        onReorder = {},
        onAdd = {},
        onCreateSuperset = {},
        onSkip = null,
        onBatchCreateSuperset = {},
        onBatchSkip = {},
        onBatchDelete = {},
        onBatchUltraFast = { _, _ -> },
        onDissolveSuperset = {},
        onRevertGodModeAction = {},
        modifier = Modifier.fillMaxWidth(),
    )
}
