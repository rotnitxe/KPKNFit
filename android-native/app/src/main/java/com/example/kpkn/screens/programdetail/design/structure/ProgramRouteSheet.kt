package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Hoja «Ruta del programa» (P3c, sustituye a la sección que se desplegaba en la página): casi a pantalla completa, con la línea de tiempo,
 * el ajuste a la competición y los bloques. Cada bloque tiene su «−» y su «+» (llaman a [onAdjustBlockWeeks] con −1 o +1). Tocar una semana
 * o un bloque cierra la hoja y lleva a esa semana con [onFocusWeek].
 */
@Composable
internal fun ProgramRouteSheet(
    model: ProgramRouteModel,
    onFocusWeek: (blockId: String, weekId: String) -> Unit,
    onAdjustBlockWeeks: (blockId: String, deltaWeeks: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss, maxHeightFraction = 0.96f) {
        ProgramRouteContent(
            model = model,
            onFocusWeek = onFocusWeek,
            onAdjustBlockWeeks = onAdjustBlockWeeks,
        )
    }
}

/** El contenido de la ruta, sin la hoja: así se puede montar suelto en las pruebas y en la hoja de render. */
@Composable
internal fun ProgramRouteContent(
    model: ProgramRouteModel,
    onFocusWeek: (blockId: String, weekId: String) -> Unit,
    onAdjustBlockWeeks: (blockId: String, deltaWeeks: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = model.weeks.firstOrNull()
    val last = model.weeks.lastOrNull()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = "Ruta del programa", style = KpknType.title, color = WizardColors.text)
        if (first != null && last != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = routeSummaryLine(model.blocks.size, model.weeks.size, first.start, last.end),
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (first == null || last == null) {
            KpknEmptyState(
                icon = KpknLineIcons.Route,
                title = "Falta la fecha de inicio",
                body = "Guárdala en el calendario del programa para ver la ruta.",
            )
        } else {
            KpknModule {
                KpknModuleHeader(label = "Línea de tiempo")
                Spacer(Modifier.height(8.dp))
                ProgramRouteTimeline(model = model)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Los bloques son el plan completo; los puntos, semanas reales. La competición reserva su semana entera.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            if (model.competitionDate != null) {
                Spacer(Modifier.height(12.dp))
                ProgramRouteCompetitionModule(
                    model = model,
                    onFocusWeek = onFocusWeek,
                    onAdjustBlockWeeks = onAdjustBlockWeeks,
                )
            }
            Spacer(Modifier.height(12.dp))
            KpknModule {
                KpknModuleHeader(label = "Bloques")
                model.blocks.forEachIndexed { index, block ->
                    if (index > 0) ProgramRowDivider()
                    ProgramRouteBlockRow(
                        block = block,
                        onFocus = { onFocusWeek(block.id, block.firstWeekId) },
                        onAdjust = { delta -> onAdjustBlockWeeks(block.id, delta) },
                    )
                }
            }
        }
    }
}

/**
 * El módulo «Competición»: la frase de encaje («Tu competición cae 2 semanas después del final del programa») o la confirmación, la
 * semana de competición, y la acción que lo cuadra en el último bloque.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramRouteCompetitionModule(
    model: ProgramRouteModel,
    onFocusWeek: (blockId: String, weekId: String) -> Unit,
    onAdjustBlockWeeks: (blockId: String, deltaWeeks: Int) -> Unit,
) {
    val line = competitionFitLine(model.fitStatus, model.fitWeeks)
    val competitionWeek = model.competitionWeekIndex?.let { index -> model.weeks.getOrNull(index) }
    val fixLabel = competitionFixLabel(model.fitStatus, model.fitWeeks)
    val fixNote = competitionFixNote(model.fitStatus, model.fitWeeks)
    val lastBlock = model.blocks.lastOrNull()
    KpknModule {
        KpknModuleHeader(label = "Competición")
        Spacer(Modifier.height(8.dp))
        Text(
            text = line ?: "Falta un dato para calcular cómo encaja la competición.",
            style = KpknType.bodyStrong,
            color = WizardColors.text,
        )
        if (fixNote != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = fixNote, style = KpknType.caption, color = WizardColors.textMuted)
        }
        if (competitionWeek != null || (fixLabel != null && lastBlock != null)) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (competitionWeek != null) {
                    KpknTextAction(
                        text = "Ver semana de la competición",
                        onClick = { onFocusWeek(competitionWeek.blockId, competitionWeek.weekId) },
                    )
                }
                if (fixLabel != null && lastBlock != null) {
                    KpknSecondaryPill(
                        text = fixLabel,
                        onClick = {
                            onAdjustBlockWeeks(lastBlock.id, competitionFixDelta(model.fitStatus, model.fitWeeks))
                        },
                    )
                }
            }
        }
    }
}
