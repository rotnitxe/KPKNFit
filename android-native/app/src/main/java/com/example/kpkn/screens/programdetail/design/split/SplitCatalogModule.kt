package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance

/** Qué hace tocar un módulo del catálogo: abrir sus acciones, o elegirlo para comparar o para aplicar varios (P5). */
internal enum class SplitCatalogMode { BROWSE, COMPARE, MULTI }

/**
 * Módulo de un reparto del catálogo (P5, sustituye a la tarjeta de siempre). Nombre en `bodyStrong`; «4 días» en `caption`; las
 * etiquetas en `caption` `textMuted`, separadas por « · »; el diagrama de la semana; la disponibilidad en una línea si el reparto no
 * tiene plantilla para algún día; y «Aplicar» junto al icono de información.
 *
 * En modo comparar o de selección múltiple el módulo entero es el botón: lleva un filete en tinta y un check en la esquina cuando
 * está elegido, y oculta sus acciones. Sin estado: la selección y el modo los trae quien lo usa.
 *
 * @param unavailableReason motivo de que no esté disponible, o null si cada día tiene plantilla.
 * @param isCurrent el reparto ya lo usa el programa o la semana.
 */
@Composable
internal fun SplitCatalogModule(
    split: SplitTemplate,
    startDay: Int,
    isCurrent: Boolean,
    unavailableReason: String?,
    mode: SplitCatalogMode,
    selected: Boolean,
    onApply: () -> Unit,
    onInfo: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    entranceIndex: Int = 0,
) {
    val selecting = mode != SplitCatalogMode.BROWSE
    val tagsLine = split.tags.joinToString(separator = " · ") { splitTagLabel(it) }
    val trainingDays = splitTrainingDays(split.pattern)
    Box(modifier = modifier.fillMaxWidth().kpknEntrance("split-catalog", entranceIndex)) {
        KpknModule(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    if (selecting) stateDescription = if (selected) "Elegido" else "No elegido"
                },
            onClick = if (selecting) onToggle else null,
            onClickLabel = if (selected) "Quitar de la selección" else "Añadir a la selección",
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(end = if (selected) 32.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = split.name,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = splitDaysLabel(trainingDays),
                        style = KpknType.caption,
                        color = WizardColors.text,
                        maxLines = 1,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "En uso",
                            style = KpknType.caption,
                            color = WizardColors.done,
                            maxLines = 1,
                        )
                    }
                }
                if (tagsLine.isNotEmpty()) {
                    Text(
                        text = tagsLine,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SplitWeekDiagram(pattern = split.pattern, startDay = startDay)
            if (unavailableReason != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = KpknLineIcons.Info,
                        contentDescription = null,
                        tint = SymbolPalette.energia,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = unavailableReason,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!selecting) {
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    KpknSecondaryPill(
                        text = "Aplicar",
                        onClick = onApply,
                        modifier = Modifier.weight(1f),
                        tag = "split_aplicar_${split.id}",
                    )
                    Spacer(Modifier.width(8.dp))
                    KpknIconButton(
                        icon = KpknLineIcons.Info,
                        contentDescription = "Qué es ${split.name}",
                        onClick = onInfo,
                    )
                }
            }
        }
        if (selected) {
            Box(modifier = Modifier.matchParentSize().border(width = 1.5.dp, color = WizardColors.text, shape = KpknShape.module))
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(WizardColors.text, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Check,
                    contentDescription = null,
                    tint = WizardColors.ctaContent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
