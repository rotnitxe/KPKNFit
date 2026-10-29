package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.programdetail.design.LienzoBodyStyle
import com.example.kpkn.screens.programdetail.design.LienzoCapStyle
import com.example.kpkn.screens.programdetail.design.LienzoChip
import com.example.kpkn.screens.programdetail.design.LienzoTextAction
import com.example.kpkn.screens.programdetail.design.ProgramLienzoPill
import com.example.kpkn.screens.programdetail.design.ProgramRoundButton
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface

/** El título de la página (`.ptitle` a 26 sp): Syne ExtraBold, 1,05 de interlineado y −0,8 de espaciado. */
private val SplitPageTitleStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 27.sp, letterSpacing = (-0.8).sp)

/** Las etiquetas de m-split: todas menos «Personalizado», que tiene su píldora abajo. */
internal val splitFilterTags: List<SplitTag> = SplitTag.entries.filter { it != SplitTag.PERSONALIZADO }

/**
 * Página «Reparto» (lienzo v6, m-split), sin estado de negocio: pinta lo que le dan. De arriba abajo: «Atrás» y la lupa (que abre el
 * buscador), el título y su línea, las etiquetas en chips, los repartos como tarjetas con radio y su semana en barras de color, y
 * «Comparar» y «Personalizado». En modo comparar o de selección múltiple flota la píldora de acción. Búsqueda, etiquetas, comparar,
 * aplicar a varias semanas y el editor propio se mantienen; solo cambia la piel.
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
@OptIn(ExperimentalLayoutApi::class)
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
    onToggleSelect: (SplitTemplate) -> Unit,
    onEditCustom: () -> Unit,
    onApplyCustom: () -> Unit,
    onCompare: () -> Unit,
    onApplySelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    // La lupa abre y cierra el buscador; con texto escrito, el buscador se queda.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val selecting = mode != SplitCatalogMode.BROWSE

    Box(modifier = modifier.fillMaxSize().background(KpknSurface.canvas).testTag("split_raiz")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
                .testTag("split_pagina"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    ProgramRoundButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = onBack, filled = true)
                } else {
                    Spacer(Modifier.height(44.dp))
                }
                ProgramRoundButton(
                    icon = KpknLineIcons.Search,
                    contentDescription = "Buscar",
                    onClick = { searchOpen = !searchOpen },
                    filled = true,
                )
            }
            Text(
                text = "Reparto",
                style = SplitPageTitleStyle,
                color = WizardColors.text,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp).semantics { heading() },
            )
            Text(
                text = "Elige cómo se reparten los grupos en la semana.",
                style = LienzoBodyStyle,
                color = WizardColors.textMuted,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            if (searchOpen || query.isNotEmpty()) {
                SplitSearchField(query = query, onQueryChange = onQueryChange, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LienzoChip(text = "Todos", selected = selectedTag == null, onClick = { onSelectTag(null) })
                splitFilterTags.forEach { tag ->
                    LienzoChip(
                        text = splitTagLabel(tag),
                        selected = selectedTag == tag,
                        onClick = { onSelectTag(if (selectedTag == tag) null else tag) },
                    )
                }
            }
            if (selectedTag == SplitTag.PERSONALIZADO && !selecting) {
                SplitCustomCatalogModule(
                    custom = customSplit,
                    startDay = startDay,
                    onEdit = onEditCustom,
                    onApply = onApplyCustom,
                )
            } else {
                if (splits.isEmpty()) {
                    Text("Sin resultados", style = LienzoCapStyle, color = WizardColors.textFaint, modifier = Modifier.padding(bottom = 10.dp))
                }
                splits.forEachIndexed { index, split ->
                    SplitCatalogModule(
                        split = split,
                        startDay = startDay,
                        isCurrent = isCurrent(split),
                        unavailableReason = unavailableReason(split),
                        mode = mode,
                        selected = split.id in selectedIds,
                        onApply = { onApply(split) },
                        onToggle = { onToggleSelect(split) },
                        entranceIndex = minOf(index, 4),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgramLienzoPill(
                    text = if (mode == SplitCatalogMode.COMPARE) "Cancelar" else "Comparar",
                    onClick = onToggleCompareMode,
                    modifier = Modifier.weight(1f),
                    filled = false,
                    small = true,
                    tag = "split_comparar_modo",
                )
                ProgramLienzoPill(
                    text = "Personalizado",
                    onClick = { onSelectTag(if (selectedTag == SplitTag.PERSONALIZADO) null else SplitTag.PERSONALIZADO) },
                    modifier = Modifier.weight(1f),
                    filled = false,
                    small = true,
                    tag = "split_personalizado",
                )
            }
            // «Aplicar a varias semanas» se mantiene (m-split, notas): la selección múltiple, como acción de texto bajo las píldoras.
            LienzoTextAction(
                text = if (mode == SplitCatalogMode.MULTI) "Cancelar selección" else "Seleccionar varios",
                onClick = onToggleMultiMode,
                centered = true,
                modifier = Modifier.padding(top = 4.dp),
            )
            // Hueco para que el último módulo no quede bajo la píldora de selección ni bajo la barra de gestos.
            Spacer(Modifier.height(if (selecting) 140.dp else 96.dp))
        }

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
