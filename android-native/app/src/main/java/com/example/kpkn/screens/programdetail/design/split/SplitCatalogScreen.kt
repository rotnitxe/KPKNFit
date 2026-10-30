package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.split.CATALOG_TAG_LABELS
import com.example.kpkn.domain.training.split.CatalogGroup
import com.example.kpkn.domain.training.split.CatalogNow
import com.example.kpkn.domain.training.split.SplitCatalogEntry
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.programdetail.design.LienzoTextAction
import com.example.kpkn.screens.programdetail.design.ProgramLienzoPill
import com.example.kpkn.screens.programdetail.design.ProgramRoundButton
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface

/** Las casillas de «Días por semana» del filtro. */
internal val SplitDayFilters: List<Int?> = listOf(null, 2, 3, 4, 5, 6)

/**
 * Página «Reparto» (escena `rp-catalogo`): «Atrás» y la lupa, el título y su línea, «Ahora» (cómo está tu semana), el filtro por días y
 * las etiquetas, los repartos agrupados en «Encajan con tus N días» y «Cambian tus días» con su descripción, y «Comparar», «Personalizado»
 * y «Seleccionar varios». Tocar una tarjeta abre la vista previa de tu semana con ese reparto ([onOpen]); en modo comparar o de selección
 * múltiple la marca. Sin estado de negocio: pinta lo que le dan. El scroll es de la propia página.
 *
 * @param now cómo está la semana abierta; null si la semana no tiene sesiones.
 * @param nowWeekLabel «semana 5».
 * @param haveDays días de entreno de la semana abierta, para avisar a los repartos que piden otro número.
 * @param groups lo que sale de `SplitCatalog.view`; vacío = «Sin resultados».
 */
@Composable
internal fun SplitCatalogScreen(
    now: CatalogNow?,
    nowWeekLabel: String,
    haveDays: Int?,
    startDay: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedTag: SplitTag?,
    onSelectTag: (SplitTag?) -> Unit,
    daysFilter: Int?,
    onDaysFilter: (Int?) -> Unit,
    groups: List<CatalogGroup>,
    customSplit: SplitTemplate?,
    mode: SplitCatalogMode,
    selectedIds: List<String>,
    onBack: (() -> Unit)?,
    onToggleCompareMode: () -> Unit,
    onToggleMultiMode: () -> Unit,
    onOpen: (SplitCatalogEntry) -> Unit,
    onToggleSelect: (SplitCatalogEntry) -> Unit,
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
    val searching = searchOpen || query.isNotEmpty()

    Box(modifier = modifier.fillMaxSize().background(KpknSurface.canvas).testTag("split_raiz")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 14.dp)
                .testTag("split_pagina"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    ProgramRoundButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = onBack, filled = true)
                } else {
                    Spacer(Modifier.height(44.dp))
                }
                ProgramRoundButton(icon = KpknLineIcons.Search, contentDescription = "Buscar", onClick = { searchOpen = !searchOpen }, filled = true)
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "Reparto",
                    style = RpType.pageTitle,
                    color = WizardColors.text,
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                )
                Text(
                    text = "Elige cómo se reparten los grupos en la semana.",
                    style = RpType.lead,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                )
                if (searching) {
                    RpSearchField(query = query, onQueryChange = onQueryChange, modifier = Modifier.padding(bottom = 12.dp))
                } else if (now != null) {
                    RpModule(modifier = Modifier.padding(bottom = 14.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                        Text("AHORA · ${nowWeekLabel.uppercase(java.util.Locale("es"))}", style = RpType.ey, color = WizardColors.textMuted)
                        Text(
                            text = "${now.label} · ${SpanishPlurals.days(now.trainingDays)}",
                            style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp),
                            color = WizardColors.text,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Box(Modifier.padding(top = 12.dp)) { RpStrip(cells = now.strip) }
                    }
                }
                // Filtro por días: «Días por semana» y sus seis casillas.
                Text("DÍAS POR SEMANA", style = RpType.eye.copy(letterSpacing = 1.2.sp), color = WizardColors.textFaint, modifier = Modifier.padding(bottom = 6.dp))
                RpSegmented(
                    options = SplitDayFilters.map { it?.toString() ?: "Todos" },
                    selectedIndex = SplitDayFilters.indexOf(daysFilter).coerceAtLeast(0),
                    onSelect = { onDaysFilter(SplitDayFilters[it]) },
                    tag = "split_dias",
                )
                Spacer(Modifier.height(10.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .rpFadeEdgesX()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RpChip(text = "Todos", on = selectedTag == null, onClick = { onSelectTag(null) })
                CATALOG_TAG_LABELS.forEach { (tag, label) ->
                    RpChip(text = label, on = selectedTag == tag, onClick = { onSelectTag(if (selectedTag == tag) null else tag) })
                }
            }
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                if (selectedTag == SplitTag.PERSONALIZADO && !selecting) {
                    Spacer(Modifier.height(12.dp))
                    SplitCustomCatalogModule(custom = customSplit, startDay = startDay, onEdit = onEditCustom, onApply = onApplyCustom)
                } else if (groups.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    RpEmptyResults(query = query)
                } else {
                    groups.forEach { group ->
                        RpSectionHeader(label = group.title, trailing = group.entries.size.toString(), top = 14.dp)
                        group.entries.forEachIndexed { entryIndex, entry ->
                            SplitCatalogCard(
                                entry = entry,
                                startDay = startDay,
                                mode = mode,
                                selected = mode != SplitCatalogMode.BROWSE && entry.id in selectedIds,
                                onClick = { if (selecting) onToggleSelect(entry) else onOpen(entry) },
                                needsOtherDays = if (group.changesDays) haveDays else null,
                                last = entryIndex == group.entries.lastIndex,
                            )
                        }
                    }
                }
                // Tras la ultima tarjeta (margen 10) o el modulo propio (12), el margen de 8 dp de la fila se funde con el anterior.
                val rowTop = if (selectedTag == SplitTag.PERSONALIZADO && !selecting) 12.dp else if (groups.isEmpty()) 8.dp else 10.dp
                Row(modifier = Modifier.fillMaxWidth().padding(top = rowTop), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                LienzoTextAction(
                    text = if (mode == SplitCatalogMode.MULTI) "Cancelar selección" else "Seleccionar varios",
                    onClick = onToggleMultiMode,
                    centered = true,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(if (selecting) 140.dp else 40.dp))
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
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

/** `.rp-search`: 48 dp, `#1a1a1a`, radio 24, la lupa de 18 dp y el texto de 15. */
@Composable
internal fun RpSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(KpknSurface.inset, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(KpknLineIcons.Search, contentDescription = null, tint = WizardColors.textFaint, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            val style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, color = WizardColors.text)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(WizardColors.text),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Buscar" }.testTag("split_buscar_campo"),
            )
            if (query.isEmpty()) Text("Busca un reparto o un músculo", style = style, color = WizardColors.textFaint)
        }
    }
}

/** `.rp-empty`: «Sin resultados» y qué probar. */
@Composable
internal fun RpEmptyResults(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Sin resultados",
            style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp),
            color = WizardColors.text,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            "No hay repartos con «${query.trim().ifEmpty { "esos filtros" }}». Prueba con otra palabra o quita un filtro.",
            style = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.5.sp),
            color = WizardColors.textFaint,
            textAlign = TextAlign.Center,
        )
    }
}
