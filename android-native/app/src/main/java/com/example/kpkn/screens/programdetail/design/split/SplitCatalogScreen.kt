package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.rememberIslandVisible

/**
 * Página «Cambiar reparto» (P5), sin estado de negocio: pinta lo que le dan. De arriba abajo: la fila superior con «Atrás» y
 * «Comparar», el título en `display`, «Tu reparto», el buscador, las etiquetas, el recuento y el catálogo entero (sin paginar). Al
 * hacer scroll aparece la isla con el título. En modo comparar o de selección múltiple flota la píldora de acción.
 *
 * El scroll es de la propia página: la hoja que la contiene no debe desplazarse.
 *
 * @param current el reparto actual de la semana, o null si no tiene ninguno.
 * @param splits los repartos ya filtrados por búsqueda y etiqueta.
 * @param customSplit el reparto propio guardado, o null si todavía no tiene días.
 * @param unavailableReason motivo de que un reparto no tenga plantilla para algún día, o null.
 * @param isCurrent true si el reparto ya lo usa el programa o alguna de sus semanas.
 * @param selectedIds los ids elegidos en modo comparar o de selección múltiple.
 */
@Composable
internal fun SplitCatalogScreen(
    current: SplitTemplate?,
    startDay: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedTag: SplitTag?,
    onSelectTag: (SplitTag?) -> Unit,
    splits: List<SplitTemplate>,
    customSplit: SplitTemplate?,
    unavailableReason: (SplitTemplate) -> String?,
    isCurrent: (SplitTemplate) -> Boolean,
    mode: SplitCatalogMode,
    selectedIds: List<String>,
    onBack: (() -> Unit)?,
    onToggleCompareMode: () -> Unit,
    onToggleMultiMode: () -> Unit,
    onApply: (SplitTemplate) -> Unit,
    onInfo: (SplitTemplate) -> Unit,
    onToggleSelect: (SplitTemplate) -> Unit,
    onEditCustom: () -> Unit,
    onApplyCustom: () -> Unit,
    onCompare: () -> Unit,
    onApplySelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    // Alto de la cabecera (fila superior y título): al salir por arriba aparece la isla, con histéresis.
    var headerPx by remember { mutableIntStateOf(0) }
    val scrolledPast = rememberIslandVisible(scroll, headerPx)
    val islandVisible = headerPx > 0 && scrolledPast

    val countLabel = when {
        selectedTag == SplitTag.PERSONALIZADO -> "Tu reparto propio"
        splits.isEmpty() -> "Sin resultados"
        splits.size == 1 -> "1 reparto"
        else -> "${splits.size} repartos"
    }
    val selecting = mode != SplitCatalogMode.BROWSE

    Box(modifier = modifier.fillMaxSize().background(KpknSurface.canvas).testTag("split_raiz")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .testTag("split_pagina"),
        ) {
            Column(modifier = Modifier.fillMaxWidth().onSizeChanged { headerPx = it.height }) {
                KpknTopRow(
                    onBack = onBack,
                    actions = {
                        KpknTextAction(
                            text = if (mode == SplitCatalogMode.COMPARE) "Cancelar" else "Comparar",
                            onClick = onToggleCompareMode,
                        )
                    },
                )
                Text(
                    text = "Cambiar reparto",
                    style = KpknType.display,
                    color = WizardColors.text,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SplitCurrentModule(split = current, startDay = startDay)
                SplitSearchField(query = query, onQueryChange = onQueryChange)
            }
            Spacer(Modifier.height(12.dp))
            SplitTagRow(selectedTag = selectedTag, onSelectTag = onSelectTag)
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = countLabel, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
                    KpknTextAction(
                        text = if (mode == SplitCatalogMode.MULTI) "Cancelar selección" else "Seleccionar varios",
                        onClick = onToggleMultiMode,
                    )
                }
                if (selectedTag == SplitTag.PERSONALIZADO && !selecting) {
                    SplitCustomCatalogModule(
                        custom = customSplit,
                        startDay = startDay,
                        onEdit = onEditCustom,
                        onApply = onApplyCustom,
                    )
                } else {
                    splits.forEachIndexed { index, split ->
                        SplitCatalogModule(
                            split = split,
                            startDay = startDay,
                            isCurrent = isCurrent(split),
                            unavailableReason = unavailableReason(split),
                            mode = mode,
                            selected = split.id in selectedIds,
                            onApply = { onApply(split) },
                            onInfo = { onInfo(split) },
                            onToggle = { onToggleSelect(split) },
                            entranceIndex = minOf(index, 4),
                        )
                    }
                }
            }
            // Hueco para que el último módulo no quede bajo la píldora de selección ni bajo la barra de gestos.
            Spacer(Modifier.height(if (selecting) 140.dp else 96.dp))
        }

        SplitTopIsland(visible = islandVisible, onBack = onBack)

        if (mode == SplitCatalogMode.COMPARE) {
            SplitSelectionBar(
                label = if (selectedIds.size == 2) "Comparar 2" else "Elige dos repartos",
                enabled = selectedIds.size == 2,
                onClick = onCompare,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (mode == SplitCatalogMode.MULTI && selectedIds.isNotEmpty()) {
            SplitSelectionBar(
                label = if (selectedIds.size == 1) "Aplicar 1 reparto" else "Aplicar ${selectedIds.size} repartos",
                enabled = true,
                onClick = onApplySelected,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
