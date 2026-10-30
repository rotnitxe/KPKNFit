package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.split.SplitCatalogEntry
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState

/** Qué hace tocar una tarjeta del catálogo: abrir la vista previa, o elegirla para comparar o para aplicar varios. */
internal enum class SplitCatalogMode { BROWSE, COMPARE, MULTI }

/**
 * Una tarjeta del catálogo (`.rp-card`, escena `rp-catalogo`): la etiqueta «Recomendado KPKN», el nombre llano, la descripción de una
 * frase, la tira de siete casillas con su letra, las etiquetas «N días · N× por músculo · nivel» y, si el reparto pide otro número de
 * días que los de la semana, su aviso. Ya no lleva radio: el reparto actual se ve en «Ahora» y se elige en la vista previa.
 * Tocar la tarjeta abre la vista previa ([onClick]); en modo comparar o de selección múltiple la marca con un filete en tinta y un check.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitCatalogCard(
    entry: SplitCatalogEntry,
    startDay: Int,
    mode: SplitCatalogMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Días de entreno de la semana abierta, si el reparto pide otro número (el aviso «Necesita…»). */
    needsOtherDays: Int? = null,
    /** La última de su grupo: su margen de 10 dp se funde con el del rotulo siguiente. */
    last: Boolean = false,
) {
    val selecting = mode != SplitCatalogMode.BROWSE
    val clickLabel = when {
        !selecting -> "Abrir ${entry.name}"
        selected -> "Quitar de la selección"
        else -> "Añadir a la selección"
    }
    Box(modifier = modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else 10.dp)) {
        // rp-T02: el borde y el fondo de la tarjeta cambian en 200 ms con la curva suave
        val fill by animateKpknColorAsState(if (selected) RpColors.selected else KpknSurface.module, KpknMotion.D200, KpknMotion.Suave)
        val line by animateKpknColorAsState(if (selected) WizardColors.text else RpColors.line, KpknMotion.D200, KpknMotion.Suave)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RpCardShape)
                .background(fill, RpCardShape)
                .border(1.dp, line, RpCardShape)
                .testTag("split_aplicar_${entry.id}")
                .semantics {
                    contentDescription = "${entry.name}, ${SpanishPlurals.days(entry.trainingDays)}"
                    if (selecting) stateDescription = if (selected) "Elegido" else "No elegido"
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = clickLabel,
                    onClick = onClick,
                )
                .padding(1.dp)
                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 12.dp),
        ) {
            if (entry.recommended) {
                Row(
                    modifier = Modifier.padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(RpSparkIcon, contentDescription = null, tint = WizardColors.textMuted, modifier = Modifier.size(12.dp))
                    Text("RECOMENDADO KPKN", style = RpType.recommended, color = WizardColors.textMuted, maxLines = 1)
                }
            }
            Text(
                text = entry.name,
                style = RpType.cardTitle,
                color = WizardColors.text,
                modifier = Modifier.padding(end = if (selected) 30.dp else 0.dp),
            )
            Text(
                text = entry.description,
                style = RpType.cardBody,
                color = WizardColors.textMuted,
                modifier = Modifier.padding(top = 7.dp, bottom = 12.dp),
            )
            RpStrip(cells = entry.strip(startDay))
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                RpMetaChip(bold = SpanishPlurals.days(entry.trainingDays), rest = null, small = true)
                entry.timesPerMuscle?.let { RpMetaChip(bold = "$it×", rest = "por músculo", small = true) }
                RpMetaChip(bold = null, rest = entry.levelLabel, small = true)
            }
            if (needsOtherDays != null) {
                Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(RpInfoIcon, contentDescription = null, tint = RpColors.warn, modifier = Modifier.padding(top = 1.dp).size(14.dp))
                    Text(
                        text = "Necesita ${SpanishPlurals.days(entry.trainingDays)} y tu semana tiene $needsOtherDays. Al elegirlo, el reparto propone los días.",
                        style = RpType.fit,
                        color = RpColors.warn,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(WizardColors.text, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(KpknLineIcons.Check, contentDescription = null, tint = RpColors.onInk, modifier = Modifier.size(15.dp))
            }
        }
    }
}
