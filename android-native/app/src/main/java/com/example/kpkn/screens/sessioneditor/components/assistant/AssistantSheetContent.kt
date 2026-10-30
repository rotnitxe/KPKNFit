package com.example.kpkn.screens.sessioneditor.components.assistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateDurationClass
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.domain.templates.SessionTemplateFacets
import com.example.kpkn.domain.templates.TemplateSaveValidationException
import com.example.kpkn.domain.templates.TemplateSaveValidationIssue
import com.example.kpkn.screens.sessioneditor.AssistantEntry
import com.example.kpkn.screens.sessioneditor.AssistantTab
import com.example.kpkn.screens.sessioneditor.RulesSection
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import kotlinx.coroutines.delay

/** Todo lo que el asistente puede pedirle al editor (una sola lista, para que la hoja y las pruebas hablen igual). */
internal class AssistantCallbacks(
    val onDismiss: () -> Unit = {},
    val onApplySuggestion: (AssistantSuggestion, List<String>, Int) -> Unit = { _, _, _ -> },
    val onDismissSuggestion: (String, Int) -> Unit = { _, _ -> },
    val onUndoOutcome: (String) -> Unit = {},
    val onTemplateSearchChange: (String) -> Unit = {},
    val onSelectTemplate: (SessionTemplate) -> Unit = {},
    val onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit = {},
    val onCancelTemplateApply: () -> Unit = {},
    val onUndoTemplateApply: () -> Unit = {},
    val onFinishTemplateApply: () -> Unit = {},
    val onArchiveUserTemplate: (String) -> Unit = {},
    val onRestoreUserTemplate: (String) -> Unit = {},
    val onDeleteUserTemplate: (String) -> Unit = {},
    val onEditUserTemplate: suspend (SessionTemplate, String, String, Difficulty, SessionTemplateFocusCategory?, SessionTemplateDurationClass, List<String>, List<String>, Boolean) -> Result<Unit> =
        { _, _, _, _, _, _, _, _, _ -> Result.success(Unit) },
    val onSaveCurrentTemplate: suspend (String, String) -> Result<SessionTemplate> = { _, _ ->
        Result.failure(IllegalStateException("Guardado de plantillas no disponible en esta superficie"))
    },
    val onRefreshTimeCoach: () -> Unit = {},
    val onConsumeEntry: () -> Unit = {},
    val rules: AsRulesActions = AsRulesActions.None,
)

/** Los avisos con que falla guardar una plantilla (los de siempre). */
internal fun templateSaveError(failure: Throwable): String = when ((failure as? TemplateSaveValidationException)?.issue) {
    TemplateSaveValidationIssue.NAME_REQUIRED -> "Escribe un nombre para la plantilla."
    TemplateSaveValidationIssue.SESSION_REQUIRED -> "Abre una sesión antes de guardar una plantilla."
    TemplateSaveValidationIssue.SESSION_INCOMPLETE -> "Completa la dosis de los ejercicios y las modalidades antes de guardar la plantilla."
    null -> "No se pudo guardar la plantilla. Inténtalo de nuevo."
}

/** Alturas de la hoja: media y completa (fracción de la pantalla). */
internal const val ASSISTANT_MEDIUM_HEIGHT = 0.62f
internal const val ASSISTANT_FULL_HEIGHT = 0.93f

/**
 * El asistente entero (título, pestañas y el cuerpo de la pestaña activa), sin la hoja que lo envuelve: la hoja de la app lo mete en
 * una `KpknSheet` y la hoja de dibujo de las pruebas, en un marco. [fullHeight] es la altura elegida y [onFullHeight] la cambia (el asa).
 */
@Composable
internal fun AssistantSheetContent(
    uiState: SessionEditorUiState,
    templates: List<SessionTemplate>,
    archivedUserTemplates: List<SessionTemplate>,
    templatesReady: Boolean,
    corruptTemplateIds: List<String>,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    facetsById: Map<String, SessionTemplateFacets>,
    callbacks: AssistantCallbacks,
    fullHeight: Boolean,
    onFullHeight: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: AssistantTab = uiState.assistantEntry?.tab ?: AssistantTab.METRICS,
    initialSection: RulesSection = uiState.assistantEntry?.rules ?: RulesSection.VALUES,
) {
    val session = uiState.activeVariantSession ?: uiState.session
    var tab by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
    var section by rememberSaveable { mutableIntStateOf(initialSection.ordinal) }
    val currentTab = AssistantTab.entries[tab]
    val currentSection = RulesSection.entries[section]
    LaunchedEffect(uiState.assistantEntry) { if (uiState.assistantEntry != null) callbacks.onConsumeEntry() }
    LaunchedEffect(currentTab, currentSection) { if (currentTab == AssistantTab.RULES && currentSection == RulesSection.TIME) callbacks.onRefreshTimeCoach() }

    // as-J02 `toast(t, sub, ms=2200)` del lienzo — el aviso se cierra solo a los 2,2 s (cada aviso nuevo reinicia la cuenta, como `clearTimeout`)
    var toast by remember { mutableStateOf<Triple<String, String?, Int>?>(null) }
    var toastMs by remember { mutableIntStateOf(2200) }
    LaunchedEffect(toast?.third) { if (toast != null) { delay(toastMs.toLong()); toast = null } }
    fun showToast(title: String, sub: String?) { toastMs = 2200; toast = Triple(title, sub, (toast?.third ?: 0) + 1) }
    // as-J04 `reg-apply` — el aviso de «Reglas aplicadas» dura 1,5 s y a los 1,7 s la hoja se cierra (`setTimeout(() => H.scrim(), 1700)`)
    var closeAt by remember { mutableIntStateOf(0) }
    LaunchedEffect(closeAt) { if (closeAt > 0) { delay(1700); callbacks.onDismiss() } }

    val limitStatuses = remember(uiState.activeVariant, session, uiState.weekSessions, uiState.ruleLimits, exerciseIndex) { buildLimitStatuses(uiState, exerciseIndex) }
    val clash = remember(session, uiState.ruleLimits, exerciseIndex) { buildClashBanner(uiState, exerciseIndex) }
    val metrics = remember(session, uiState.weekSessions, uiState.ruleLimits, uiState.sessionTimeBreakdown, uiState.estimatedDurationMinutes, exerciseIndex) {
        buildMetricsModel(uiState, exerciseIndex)
    }
    val suggestions = remember(uiState.assistantReport, uiState.assistantOutcomes, session?.lastModifiedAtMs, uiState.ruleLimits, uiState.estimatedDurationMinutes, uiState.activeVariant) {
        buildSuggestionsModel(uiState, limitStatuses)
    }
    val badges = buildMap<AssistantTab, AsTabBadge> {
        val live = liveSuggestionCount(suggestions)
        if (live > 0) put(AssistantTab.SUGGESTIONS, AsTabBadge.Count(live))
        if (limitStatuses?.anyClash == true && uiState.ruleLimits.rigidLimits) put(AssistantTab.RULES, AsTabBadge.Dot)
    }

    Box(modifier.fillMaxSize()) {
        AssistantSheetFrame(
            sessionName = session?.name.orEmpty(),
            tab = currentTab,
            badges = badges,
            onTab = { tab = it.ordinal },
            onGrab = { onFullHeight(!fullHeight) },
        ) {
            when (currentTab) {
                AssistantTab.METRICS -> AssistantMetricsPane(
                    model = metrics,
                    onGoTemplates = { tab = AssistantTab.TEMPLATES.ordinal },
                    onGoTimeRules = { tab = AssistantTab.RULES.ordinal; section = RulesSection.TIME.ordinal },
                )
                AssistantTab.SUGGESTIONS -> AssistantSuggestionsPane(
                    model = suggestions,
                    fxFor = { suggestion, accepted -> buildFx(uiState, suggestion, accepted, exerciseIndex) },
                    onApply = callbacks.onApplySuggestion,
                    onDismiss = callbacks.onDismissSuggestion,
                    onUndo = callbacks.onUndoOutcome,
                    onGoTimeRules = { tab = AssistantTab.RULES.ordinal; section = RulesSection.TIME.ordinal },
                    onGoLimitRules = { tab = AssistantTab.RULES.ordinal; section = RulesSection.LIMITS.ordinal },
                )
                AssistantTab.TEMPLATES -> TemplatesTab(
                    uiState = uiState,
                    templates = templates,
                    archived = archivedUserTemplates,
                    ready = templatesReady,
                    corruptCount = corruptTemplateIds.size,
                    exerciseIndex = exerciseIndex,
                    facetsById = facetsById,
                    callbacks = callbacks,
                    onToast = ::showToast,
                )
                AssistantTab.RULES -> AssistantRulesPane(
                    uiState = uiState,
                    actions = callbacks.rules,
                    limitStatuses = limitStatuses,
                    clashBanner = clash,
                    section = currentSection,
                    onSection = { section = it.ordinal },
                    onGoSuggestions = { tab = AssistantTab.SUGGESTIONS.ordinal },
                    onToast = ::showToast,
                    onApplied = { toastMs = 1500; closeAt += 1 },
                )
            }
        }
        toast?.let { (title, sub, _) -> AsToast(title, sub, Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp)) }
    }
}

@Composable
private fun TemplatesTab(
    uiState: SessionEditorUiState,
    templates: List<SessionTemplate>,
    archived: List<SessionTemplate>,
    ready: Boolean,
    corruptCount: Int,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    facetsById: Map<String, SessionTemplateFacets>,
    callbacks: AssistantCallbacks,
    onToast: (String, String?) -> Unit,
) {
    val session = uiState.activeVariantSession ?: uiState.session
    val cards = remember(templates, facetsById) { buildTemplateCards(templates, facetsById) }
    val mine = remember(templates, archived) { buildMineRows(templates, archived) }
    val all = remember(templates, archived) { (templates + archived).associateBy { it.id } }
    val totals = remember(session) { session?.let(com.example.kpkn.domain.sessionassistant.TemplatePreviewBuilder::totals) }
    val decision = uiState.templateApplyDecision?.let { buildDecisionModel(it, session, session?.targetDurationMinutes) }
    val applying = uiState.templateApplyProgress?.let(::buildApplyModel)
    AssistantTemplatesPane(
        ready = ready,
        cards = cards,
        mineRows = mine,
        corruptCount = corruptCount,
        query = uiState.templateSearchQuery,
        onQueryChange = callbacks.onTemplateSearchChange,
        detailFor = { id -> all[id]?.let { buildTemplateDetail(it, cards.firstOrNull { c -> c.id == id }, facetsById[id], exerciseIndex) } },
        sessionName = session?.name.orEmpty(),
        sessionSummary = totals?.let { "${it.exercises} ejercicios, ${it.sets} series, ≈${it.minutes} min" } ?: "sin ejercicios",
        decision = decision,
        applying = applying,
        onApplyTemplate = { id -> all[id]?.let(callbacks.onSelectTemplate) },
        onConfirmDecision = callbacks.onConfirmApplyTemplate,
        onCancelDecision = callbacks.onCancelTemplateApply,
        onFinishApply = callbacks.onFinishTemplateApply,
        onUndoApply = callbacks.onUndoTemplateApply,
        onArchive = callbacks.onArchiveUserTemplate,
        onRestore = callbacks.onRestoreUserTemplate,
        onDelete = callbacks.onDeleteUserTemplate,
        onEdit = { id, name, description, level ->
            val template = all[id]
            if (template == null) false else {
                val result = callbacks.onEditUserTemplate(
                    template, name, description, difficultyOf(level), template.focusCategory, template.durationClass,
                    template.splitIds, template.splitDayLabels, template.autoGenerationEligible,
                )
                if (result.isFailure) onToast("No se pudo guardar la plantilla", null)
                result.isSuccess
            }
        },
        onSave = { name, description ->
            val result = runCatching { callbacks.onSaveCurrentTemplate(name, description) }.getOrElse { Result.failure(it) }
            if (result.isFailure) onToast(templateSaveError(result.exceptionOrNull()!!), null) else onToast("Plantilla guardada", null)
            result.isSuccess
        },
    )
}
