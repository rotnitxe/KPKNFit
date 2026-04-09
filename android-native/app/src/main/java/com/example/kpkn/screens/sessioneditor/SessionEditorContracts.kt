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
    SUPERSERIE_MANAGER,
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

enum class SessionSaveScope {
    SESSION_ONLY,
    MESOCYCLE,
}

sealed interface SessionEditorAction {
    data object CloseEditor : SessionEditorAction
    data object OpenHistory : SessionEditorAction
    data object OpenRules : SessionEditorAction
    data object OpenTransfer : SessionEditorAction
    data object OpenBackgroundEditor : SessionEditorAction
    data object OpenAuge : SessionEditorAction
    data class SwitchSiblingSession(val sessionId: String) : SessionEditorAction
    data class SaveDraft(val scope: SessionSaveScope) : SessionEditorAction
}
