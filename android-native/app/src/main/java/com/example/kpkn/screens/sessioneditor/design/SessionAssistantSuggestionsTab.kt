package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Pestaña Sugerencias del asistente (SE4): una fila por sugerencia del motor, con su porqué en `caption` y «Aplicar». Si la sugerencia trae
 * detalles, cada uno es una casilla marcada por defecto; «Aplicar» aplica los que queden marcados. Mientras el análisis se calcula, muestra
 * el esqueleto; sin sugerencias, un estado vacío.
 */
@Composable
fun SessionAssistantSuggestionsTab(
    suggestions: List<AssistantSuggestion>,
    analysisReady: Boolean,
    onApply: (suggestionId: String, acceptedDetailIds: List<String>) -> Unit,
) {
    if (!analysisReady) {
        SessionAssistantLoading()
        return
    }
    if (suggestions.isEmpty()) {
        KpknEmptyState(
            icon = KpknLineIcons.Sliders,
            title = "Sin sugerencias para esta sesión",
            body = "Si la sesión se pone demasiado exigente, aparecerán ajustes aquí.",
        )
        return
    }
    // Los detalles que el usuario cambia viven aquí, por id; sin cambio manda el valor que marca el motor.
    val toggled = remember(suggestions) { mutableStateMapOf<String, Boolean>() }
    KpknModule(modifier = Modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Ajustes sugeridos")
        suggestions.forEachIndexed { index, suggestion ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            }
            SessionAssistantSuggestionRow(
                suggestion = suggestion,
                isAccepted = { detail -> toggled[detail.id] ?: detail.defaultAccepted },
                onToggle = { detail -> toggled[detail.id] = !(toggled[detail.id] ?: detail.defaultAccepted) },
                onApply = onApply,
            )
        }
    }
}

@Composable
private fun SessionAssistantSuggestionRow(
    suggestion: AssistantSuggestion,
    isAccepted: (AssistantSuggestionDetail) -> Boolean,
    onToggle: (AssistantSuggestionDetail) -> Unit,
    onApply: (suggestionId: String, acceptedDetailIds: List<String>) -> Unit,
) {
    val acceptedIds = suggestion.details.filter(isAccepted).map { it.id }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(text = suggestion.title, style = KpknType.bodyStrong, color = WizardColors.text)
        Spacer(Modifier.height(4.dp))
        Text(text = suggestion.message, style = KpknType.caption, color = WizardColors.textMuted)
        suggestion.details.forEach { detail ->
            KpknCheckRow(
                checked = isAccepted(detail),
                title = detail.label,
                onClick = { onToggle(detail) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                // Sin detalles, «Aplicar» llega con la lista vacía, como antes.
                suggestion.details.isEmpty() -> KpknTextAction(
                    text = "Aplicar",
                    onClick = { onApply(suggestion.id, emptyList()) },
                )
                // Con todos los detalles desmarcados, aplicar mandaría los de por defecto: mejor no ofrecerlo.
                acceptedIds.isEmpty() -> Text(
                    text = "Nada seleccionado",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
                else -> KpknTextAction(
                    text = "Aplicar",
                    onClick = { onApply(suggestion.id, acceptedIds) },
                )
            }
        }
    }
}
