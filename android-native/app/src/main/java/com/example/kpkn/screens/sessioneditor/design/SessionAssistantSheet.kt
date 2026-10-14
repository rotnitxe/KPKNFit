package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateDurationClass
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknType

/** Pestañas del asistente de la sesión, en el orden del control segmentado: Impacto · Sugerencias · Plantillas (SE4). */
enum class SessionAssistantTab(val label: String) {
    IMPACT("Impacto"),
    SUGGESTIONS("Sugerencias"),
    TEMPLATES("Plantillas"),
}

/**
 * Hoja del asistente de la sesión (SE4): una `KpknSheet` casi a pantalla completa que sustituye a la hoja anterior del editor. Todos los
 * datos llegan de la pantalla; la lógica de AUGE, de las sugerencias y de las plantillas no cambia aquí. Abre en la pestaña que pida
 * quien la abre (el módulo, en «Impacto»; la isla, la última usada).
 */
@Composable
fun SessionAssistantOverlay(
    uiState: SessionEditorUiState,
    templates: List<SessionTemplate>,
    impact: SessionImpact?,
    tab: SessionAssistantTab,
    onTabSelect: (SessionAssistantTab) -> Unit,
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
    // KpknSheet monta el contenido sobre el origen de desenfoque de la pantalla. Altura fija: al cambiar de pestaña, la hoja no salta.
    KpknSheet(onDismissRequest = onDismiss, stableHeightFraction = 0.92f) {
        SessionAssistantSheetContent(
            tab = tab,
            onTabSelect = onTabSelect,
            impact = impact,
            analysisReady = uiState.assistantReport != null,
            suggestions = uiState.assistantReport?.ajustes.orEmpty(),
            onApplySuggestion = onApplyAssistantSuggestion,
            templatesTab = {
                SessionAssistantTemplatesTab(
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
                    sessionSummary = sessionCountsLine(uiState.session),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Cuerpo del asistente (SE4): el título «Asistente de la sesión», el control «Impacto · Sugerencias · Plantillas» y la pestaña elegida. El
 * título y el control quedan fijos; la pestaña se desplaza. Sin estado: la pestaña y los datos llegan de fuera. Se monta dentro de una hoja
 * (con `Modifier.weight(1f)`) o suelto en las pruebas.
 */
@Composable
fun SessionAssistantSheetContent(
    tab: SessionAssistantTab,
    onTabSelect: (SessionAssistantTab) -> Unit,
    impact: SessionImpact?,
    analysisReady: Boolean,
    suggestions: List<AssistantSuggestion>,
    onApplySuggestion: (suggestionId: String, acceptedDetailIds: List<String>) -> Unit,
    templatesTab: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "Asistente de la sesión",
            style = KpknType.title,
            color = WizardColors.text,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
        )
        KpknSegmentedControl(
            options = SessionAssistantTab.entries.map { it.label },
            selectedIndex = tab.ordinal,
            onSelect = { index -> onTabSelect(SessionAssistantTab.entries[index]) },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (tab) {
                SessionAssistantTab.IMPACT -> SessionAssistantImpactTab(
                    impact = impact,
                    analysisReady = analysisReady,
                )
                SessionAssistantTab.SUGGESTIONS -> SessionAssistantSuggestionsTab(
                    suggestions = suggestions,
                    analysisReady = analysisReady,
                    onApply = onApplySuggestion,
                )
                SessionAssistantTab.TEMPLATES -> templatesTab()
            }
        }
    }
}
