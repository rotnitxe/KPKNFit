package com.example.kpkn.screens.sessioneditor

enum class SessionEditorSheet {
    NONE,
    EXERCISE_PICKER,
    QUICK_ACTIONS,
    BACKGROUND,
    HISTORY,
    RULES,
    TRANSFER,
    SAVE,
    AUGE,
    WARMUP,
    MOBILITY_PICKER,
    CARDIO_PICKER,
    SUPERSERIE_MANAGER,
    SUPERSET_CREATOR,
    RELATIONSHIP_PICKER,
    /** Session template browser/picker. Opened from the Templates FAB. */
    TEMPLATES,
}

data class SupersetDraft(
    val partId: String? = null,
    val exerciseIds: List<String> = emptyList(),
    val restBetweenExercises: Int = 60,
    val restAfterSuperset: Int = 120,
    val rounds: Int? = null,
)

sealed interface SessionExerciseEditorBlock {
    data class Single(val exercise: com.example.kpkn.data.models.Exercise) : SessionExerciseEditorBlock
    data class Superset(
        val group: com.example.kpkn.data.models.SupersetGroup,
        val exercises: List<com.example.kpkn.data.models.Exercise>,
    ) : SessionExerciseEditorBlock
}

data class SessionDraftBundle(
    val sessionId: String,
    val weekId: String,
    val macroIndex: Int,
    val mesoIndex: Int,
    val dayOfWeek: Int?,
    val siblingSessionIds: List<String> = emptyList(),
    val weekSessionIds: List<String> = emptyList(),
)

data class SessionRoadmapOption(
    val macroIndex: Int,
    val blockIndex: Int,
    val mesoIndex: Int,
    val weekIndex: Int,
    val weekId: String,
    val macroName: String,
    val blockName: String,
    val weekName: String,
    val sessionCount: Int,
)

data class SessionCloneDayOption(
    val key: String,
    val macroIndex: Int,
    val mesoIndex: Int,
    val weekId: String,
    val dayOfWeek: Int,
    val macroName: String,
    val blockName: String,
    val mesoName: String,
    val weekName: String,
    val existingSessionId: String? = null,
    val existingSessionName: String? = null,
    val existingExerciseCount: Int = 0,
    val isCurrentSessionDay: Boolean = false,
)

data class SessionCloneSourceOption(
    val sessionId: String,
    val dayOfWeek: Int?,
    val macroIndex: Int,
    val mesoIndex: Int,
    val weekId: String,
    val macroName: String,
    val blockName: String,
    val mesoName: String,
    val weekName: String,
    val sessionName: String,
    val exerciseCount: Int,
    val exercises: List<SessionCloneExerciseOption> = emptyList(),
)

data class SessionCloneExerciseOption(
    val exerciseId: String,
    val name: String,
    val sourcePartName: String? = null,
)

data class ProgramExerciseCandidate(
    val exerciseId: String,
    val exerciseName: String,
    val exerciseDbId: String?,
    val sessionDayOfWeek: Int?,
    val sessionName: String,
    val partName: String?,
)

enum class SessionCloneApplyMode {
    APPEND,
    REPLACE,
}

/** Queued copy of [sourceSession] onto other days; flushed on save. */
data class PendingTransferToDays(
    val targetKeys: Set<String>,
    val selectedExerciseIds: Set<String>?,
    val applyMode: SessionCloneApplyMode,
    val sourceSession: com.example.kpkn.data.models.Session,
)

enum class SessionSaveScope {
    SESSION_ONLY,
    MESOCYCLE,
}

sealed interface SessionEditorAction {
    data object CloseEditor : SessionEditorAction
    data object OpenRules : SessionEditorAction
    data object OpenBackgroundEditor : SessionEditorAction
    data object OpenAuge : SessionEditorAction
    data class SwitchSiblingSession(val sessionId: String) : SessionEditorAction
    data class SaveDraft(val scope: SessionSaveScope) : SessionEditorAction
}
