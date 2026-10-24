package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateDurationClass
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.components.TemplateCatalogBrowser
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState

/** Pestañas del asistente, en el orden del control segmentado: Impacto · Sugerencias · Plantillas. */
private val AssistantTabLabels = listOf("Contenido", "Sugerencias", "Plantillas")

@Composable
internal fun AssistantGlassOverlay(
    uiState: SessionEditorUiState,
    templates: List<SessionTemplate>,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState,
    onDismiss: () -> Unit,
    onApplyAssistantSuggestion: (suggestionId: String, acceptedDetailIds: List<String>) -> Unit,
    onTemplateSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelTemplateApply: () -> Unit,
    archivedUserTemplates: List<SessionTemplate> = emptyList(),
    templatesReady: Boolean = true,
    corruptTemplateIds: List<String> = emptyList(),
    onArchiveUserTemplate: (String) -> Unit = {},
    onRestoreUserTemplate: (String) -> Unit = {},
    onDeleteUserTemplate: (String) -> Unit = {},
    onEditUserTemplate: suspend (
        SessionTemplate,
        String,
        String,
        Difficulty,
        SessionTemplateFocusCategory?,
        SessionTemplateDurationClass,
        List<String>,
        List<String>,
        Boolean,
    ) -> Result<Unit> = { _, _, _, _, _, _, _, _, _ -> Result.success(Unit) },
    onSaveCurrentTemplate: suspend (String, String) -> Result<SessionTemplate> = { _, _ ->
        Result.failure(IllegalStateException("Guardado de plantillas no disponible en esta superficie"))
    },
) {
    // Uses KpknSheet (portal + LocalHazeState) so blur samples MainActivity's hazeSource.
    // Do NOT pass the session-local hazeState — the sheet is not a sibling of that source.
    // Hoja alta (82 %): altura fija, para que cambiar de pestaña no salte.
    KpknSheet(onDismissRequest = onDismiss, stableHeightFraction = 0.82f) {
        AssistantSheet(
            uiState = uiState,
            templates = templates,
            onApplyAssistantSuggestion = onApplyAssistantSuggestion,
            onTemplateSearchChange = onTemplateSearchChange,
            onSelectTemplate = onSelectTemplate,
            onConfirmApplyTemplate = onConfirmApplyTemplate,
            onCancelTemplateApply = onCancelTemplateApply,
            archivedUserTemplates = archivedUserTemplates,
            templatesReady = templatesReady,
            corruptTemplateIds = corruptTemplateIds,
            onArchiveUserTemplate = onArchiveUserTemplate,
            onRestoreUserTemplate = onRestoreUserTemplate,
            onDeleteUserTemplate = onDeleteUserTemplate,
            onEditUserTemplate = onEditUserTemplate,
            onSaveCurrentTemplate = onSaveCurrentTemplate,
        )
    }
}

@Composable
internal fun AssistantSheet(
    uiState: SessionEditorUiState,
    templates: List<SessionTemplate>,
    onApplyAssistantSuggestion: (suggestionId: String, acceptedDetailIds: List<String>) -> Unit,
    onTemplateSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelTemplateApply: () -> Unit,
    archivedUserTemplates: List<SessionTemplate> = emptyList(),
    templatesReady: Boolean = true,
    corruptTemplateIds: List<String> = emptyList(),
    onArchiveUserTemplate: (String) -> Unit = {},
    onRestoreUserTemplate: (String) -> Unit = {},
    onDeleteUserTemplate: (String) -> Unit = {},
    onEditUserTemplate: suspend (
        SessionTemplate,
        String,
        String,
        Difficulty,
        SessionTemplateFocusCategory?,
        SessionTemplateDurationClass,
        List<String>,
        List<String>,
        Boolean,
    ) -> Result<Unit> = { _, _, _, _, _, _, _, _, _ -> Result.success(Unit) },
    onSaveCurrentTemplate: suspend (String, String) -> Result<SessionTemplate> = { _, _ ->
        Result.failure(IllegalStateException("Guardado de plantillas no disponible en esta superficie"))
    },
) {
    val report = uiState.assistantReport
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Asistente de sesión",
            style = KpknType.title,
            color = WizardColors.text,
            modifier = Modifier.fillMaxWidth(),
        )
        KpknSegmentedControl(
            options = AssistantTabLabels,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.fillMaxWidth(),
        )

        when (selectedTab) {
            1 -> AssistantSuggestionsTab(
                suggestions = report?.ajustes.orEmpty(),
                analysisReady = report != null,
                onApply = onApplyAssistantSuggestion,
                notes = report?.notas.orEmpty(),
            )
            2 -> AssistantTemplatesTab(
                templates = templates,
                searchQuery = uiState.templateSearchQuery,
                applyDecision = uiState.templateApplyDecision,
                onSearchChange = onTemplateSearchChange,
                onSelectTemplate = onSelectTemplate,
                onConfirmApplyTemplate = onConfirmApplyTemplate,
                onCancelApply = onCancelTemplateApply,
                archivedUserTemplates = archivedUserTemplates,
                templatesReady = templatesReady,
                corruptTemplateIds = corruptTemplateIds,
                onArchiveUserTemplate = onArchiveUserTemplate,
                onRestoreUserTemplate = onRestoreUserTemplate,
                onDeleteUserTemplate = onDeleteUserTemplate,
                onEditUserTemplate = onEditUserTemplate,
                onSaveCurrentTemplate = onSaveCurrentTemplate,
            )
            else -> AssistantContentsTab(uiState = uiState)
        }
    }
}

@Composable
internal fun AssistantTemplatesTab(
    templates: List<SessionTemplate>,
    searchQuery: String,
    applyDecision: SessionTemplateApplyDecision?,
    onSearchChange: (String) -> Unit,
    onSelectTemplate: (SessionTemplate) -> Unit,
    onConfirmApplyTemplate: (SessionTemplateApplyMode) -> Unit,
    onCancelApply: () -> Unit,
    archivedUserTemplates: List<SessionTemplate> = emptyList(),
    templatesReady: Boolean = true,
    corruptTemplateIds: List<String> = emptyList(),
    onArchiveUserTemplate: (String) -> Unit = {},
    onRestoreUserTemplate: (String) -> Unit = {},
    onDeleteUserTemplate: (String) -> Unit = {},
    onEditUserTemplate: suspend (
        SessionTemplate,
        String,
        String,
        Difficulty,
        SessionTemplateFocusCategory?,
        SessionTemplateDurationClass,
        List<String>,
        List<String>,
        Boolean,
    ) -> Result<Unit> = { _, _, _, _, _, _, _, _, _ -> Result.success(Unit) },
    onSaveCurrentTemplate: suspend (String, String) -> Result<SessionTemplate> = { _, _ ->
        Result.failure(IllegalStateException("Guardado de plantillas no disponible en esta superficie"))
    },
) {
    // D4: el buscador va en primer plano; «Mis plantillas» es un botón que abre su lista y, mientras está abierta, el buscador se oculta.
    var mineOpen by rememberSaveable { mutableStateOf(false) }
    if (!mineOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Buscar plantillas", style = KpknType.bodyStrong, color = WizardColors.text)
            KpknTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = "Buscar plantilla…",
                leadingIcon = KpknLineIcons.Search,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
    TemplateCatalogBrowser(
        templates = templates,
        searchQuery = searchQuery,
        onSelectTemplate = onSelectTemplate,
        archivedUserTemplates = archivedUserTemplates,
        templatesReady = templatesReady,
        corruptTemplateIds = corruptTemplateIds,
        onArchiveUserTemplate = onArchiveUserTemplate,
        onRestoreUserTemplate = onRestoreUserTemplate,
        onDeleteUserTemplate = onDeleteUserTemplate,
        onEditUserTemplate = onEditUserTemplate,
        onSaveCurrentTemplate = onSaveCurrentTemplate,
        exerciseIndex = exerciseCatalogSnapshot().associateBy { it.id.lowercase() },
        glassDark = true,
        mineOpen = mineOpen,
        onMineOpenChange = { mineOpen = it },
    )
    if (applyDecision != null) {
        TemplateApplyDialog(
            decision = applyDecision,
            onDismiss = onCancelApply,
            onApply = onConfirmApplyTemplate,
        )
    }
}

internal fun resolveCatalogExerciseInfo(
    exercise: Exercise,
    catalogLookup: Map<String, ExerciseMuscleInfo>,
): ExerciseMuscleInfo? = resolveCatalogExerciseInfoInIndex(
    index = catalogLookup,
    catalogConfigurationId = exercise.catalogConfigurationId,
    exerciseDbId = exercise.exerciseDbId,
    exerciseId = exercise.exerciseId,
    exerciseName = exercise.name,
)
