package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Hoja «Plantillas» (P3c, sustituye a la piel de la biblioteca del editor): dos grupos con su eyebrow, «PLANTILLAS» y «PROTOCOLOS», un
 * módulo por opción. Tocar una plantilla abre su confirmación ([onPickTemplate]); tocar un protocolo abre su ficha
 * ([onPickProtocol]). «Filtrar» despliega los cuatro filtros de siempre.
 */
@Composable
internal fun ProgramTemplatesSheet(
    templates: List<ProgramLibraryCard>,
    protocols: List<ProgramLibraryCard>,
    onPickTemplate: (key: String) -> Unit,
    onPickProtocol: (key: String) -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        ProgramTemplatesContent(
            templates = templates,
            protocols = protocols,
            onPickTemplate = onPickTemplate,
            onPickProtocol = onPickProtocol,
        )
    }
}

/** El contenido de la hoja sin la hoja: los filtros, los dos grupos y el estado vacío. */
@Composable
internal fun ProgramTemplatesContent(
    templates: List<ProgramLibraryCard>,
    protocols: List<ProgramLibraryCard>,
    onPickTemplate: (key: String) -> Unit,
    onPickProtocol: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filters by remember { mutableStateOf(ProgramLibraryFilters()) }
    var filtersOpen by remember { mutableStateOf(false) }
    val shownTemplates = templates.filter { card -> card.traits.matches(filters) }
    val shownProtocols = protocols.filter { card -> card.traits.matches(filters) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = "Plantillas", style = KpknType.title, color = WizardColors.text)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Aplican una estructura completa al programa.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        KpknTextAction(
            text = if (filters.activeCount > 0) "Filtrar · ${filters.activeCount}" else "Filtrar",
            onClick = { filtersOpen = !filtersOpen },
            icon = KpknLineIcons.Sliders,
        )
        AnimatedVisibility(visible = filtersOpen) {
            Column {
                Spacer(Modifier.height(12.dp))
                ProgramLibraryFilterPanel(filters = filters, onChange = { chosen -> filters = chosen })
            }
        }
        Spacer(Modifier.height(16.dp))
        if (shownTemplates.isNotEmpty()) {
            ProgramSectionLabel(text = "Plantillas")
            Spacer(Modifier.height(8.dp))
            shownTemplates.forEach { card ->
                ProgramLibraryRow(card = card, onClick = { onPickTemplate(card.key) })
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
        if (shownProtocols.isNotEmpty()) {
            ProgramSectionLabel(text = "Protocolos")
            Spacer(Modifier.height(8.dp))
            shownProtocols.forEach { card ->
                ProgramLibraryRow(card = card, onClick = { onPickProtocol(card.key) })
                Spacer(Modifier.height(8.dp))
            }
        }
        if (shownTemplates.isEmpty() && shownProtocols.isEmpty()) {
            KpknEmptyState(
                icon = KpknLineIcons.Layers,
                title = "No hay planes con esos filtros",
                body = "Quita algún filtro para ver más opciones.",
            )
        }
    }
}
