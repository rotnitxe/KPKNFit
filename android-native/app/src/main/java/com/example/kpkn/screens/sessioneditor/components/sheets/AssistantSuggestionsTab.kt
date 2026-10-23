package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.sessionassistant.AssistantNoticeKind
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknType

/**
 * Pestaña Sugerencias del asistente (ER1e): un módulo por sugerencia, con su porqué en una línea, una casilla por cada cambio y su
 * «Aplicar selección» al pie. Mientras el análisis se calcula, el esqueleto; sin sugerencias, un estado vacío que lo dice.
 */
@Composable
internal fun AssistantSuggestionsTab(
    suggestions: List<AssistantSuggestion>,
    analysisReady: Boolean,
    onApply: (suggestionId: String, acceptedDetailIds: List<String>) -> Unit,
    /** Notas informativas sin acción (MOT · S3): un dato verificable y una posibilidad, nunca una orden. */
    notes: List<String> = emptyList(),
) {
    if (!analysisReady) {
        SheetLoadingSkeleton()
        return
    }
    if (suggestions.isEmpty() && notes.isEmpty()) {
        KpknEmptyState(
            icon = KpknLineIcons.Sliders,
            title = "Sin sugerencias para esta sesión",
            body = "Si algo choca con el tiempo o con los límites que elegiste, aparecerá aquí.",
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        suggestions.take(3).forEach { suggestion ->
            AssistantSuggestionCard(suggestion = suggestion, onApplySuggestion = onApply)
        }
        notes.take((3 - suggestions.size).coerceAtLeast(0)).forEach { note ->
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                SheetCaption(text = AssistantNoticeKind.INFO.label, color = WizardColors.textFaint)
                Spacer(Modifier.height(4.dp))
                SheetCaption(text = note)
            }
        }
    }
}

/**
 * Una sugerencia en su módulo: qué cambia (título), por qué (una línea), las casillas de sus detalles y, al pie, «Aplicar selección».
 * Cada sugerencia aplica solo lo marcado de sí misma: el motor y el VM aplican una a una (cada aplicación cambia la sesión).
 */
@Composable
internal fun AssistantSuggestionCard(
    suggestion: AssistantSuggestion,
    onApplySuggestion: (suggestionId: String, acceptedDetailIds: List<String>) -> Unit,
) {
    val accepted = remember(suggestion.id, suggestion.details) {
        mutableStateMapOf<String, Boolean>().apply {
            suggestion.details.forEach { detail -> put(detail.id, detail.defaultAccepted) }
        }
    }
    val acceptedIds = suggestion.details
        .filter { detail -> accepted[detail.id] ?: detail.defaultAccepted }
        .map { it.id }

    KpknModule(modifier = Modifier.fillMaxWidth()) {
        SheetCaption(text = suggestion.kind.label, color = WizardColors.textFaint)
        Spacer(Modifier.height(2.dp))
        Text(
            text = suggestion.title,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
        )
        Spacer(Modifier.height(4.dp))
        SheetCaption(text = suggestion.message)
        if (suggestion.details.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            KpknPrimaryPill(
                text = "Aplicar",
                onClick = { onApplySuggestion(suggestion.id, emptyList()) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Spacer(Modifier.height(8.dp))
            suggestion.details.forEach { detail ->
                KpknCheckRow(
                    checked = accepted[detail.id] ?: detail.defaultAccepted,
                    title = detail.label,
                    onClick = { accepted[detail.id] = !(accepted[detail.id] ?: detail.defaultAccepted) },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            KpknPrimaryPill(
                text = if (acceptedIds.isEmpty()) "Nada seleccionado" else "Aplicar selección",
                onClick = { onApplySuggestion(suggestion.id, acceptedIds) },
                modifier = Modifier.fillMaxWidth(),
                enabled = acceptedIds.isNotEmpty(),
            )
        }
    }
}
