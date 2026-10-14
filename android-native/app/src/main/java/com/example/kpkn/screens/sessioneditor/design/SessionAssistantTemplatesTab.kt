package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyDecision
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateDurationClass
import com.example.kpkn.data.sessions.SessionTemplateFocusCategory
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.TemplateCatalogBrowser
import com.example.kpkn.screens.sessioneditor.components.templateCountsLine
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Pestaña Plantillas del asistente (SE4): el buscador `KpknTextField` con la lupa y, debajo, el navegador de plantillas, que conserva su
 * lógica entera (fatiga AUGE al desplegar cada una, «Aplicar», archivar, restaurar, editar y guardar). Si aplicar una plantilla vaciaría o
 * cambiaría una sesión con contenido, [applyDecision] pide la confirmación en un `KpknAlertDialog`: Reemplazar o Añadir al final.
 */
@Composable
fun SessionAssistantTemplatesTab(
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
    /** Resumen de la sesión que se guardaría («6 ejercicios · 22 series»); el diálogo de guardar lo muestra al pie. */
    sessionSummary: String? = null,
) {
    KpknTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = "Buscar plantilla…",
        leadingIcon = KpknLineIcons.Search,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
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
        sessionSummary = sessionSummary,
    )
    if (applyDecision != null) {
        KpknAlertDialog(
            onDismissRequest = onCancelApply,
            title = {
                Text(text = "Aplicar plantilla", style = KpknType.title, color = WizardColors.text)
            },
            text = {
                Text(
                    text = "La sesión ya tiene ejercicios. ¿Qué deseas hacer con la plantilla «${applyDecision.template.name}»?",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            },
            confirmButton = {
                KpknPrimaryPill(
                    text = "Reemplazar",
                    onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.REPLACE) },
                )
            },
            dismissButton = {
                KpknSecondaryPill(
                    text = "Añadir al final",
                    onClick = { onConfirmApplyTemplate(SessionTemplateApplyMode.APPEND) },
                )
            },
        )
    }
}

/** Resumen de una sesión para el pie del diálogo de guardar, «6 ejercicios · 22 series». Nulo si no hay sesión. */
internal fun sessionCountsLine(session: Session?): String? {
    val exercises = session?.allExercises() ?: return null
    return templateCountsLine(exercises.size, exercises.sumOf { it.sets.size })
}
