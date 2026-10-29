package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.programdetail.design.LienzoCapStyle
import com.example.kpkn.screens.programdetail.design.LienzoRadio
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.kpknEntrance

/** Qué hace tocar un módulo del catálogo: abrir su hoja de aplicar, o elegirlo para comparar o para aplicar varios (P5). */
internal enum class SplitCatalogMode { BROWSE, COMPARE, MULTI }

/** El nombre de una tarjeta `.pcard`: Syne Bold 17 / 19,5, −0,3. */
private val SplitCardTitleStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 19.5.sp, letterSpacing = (-0.3).sp)

/**
 * El color de cada día en las barras de m-split (los `MUS` del lienzo v6): pierna `musculo`, empuje `energia`, tirón `columna`; torso
 * `mente`; cuerpo completo y el resto, tinta.
 */
internal fun splitBarColor(role: SplitDayRole): Color = when (role) {
    SplitDayRole.LEGS -> SymbolPalette.musculo
    SplitDayRole.PUSH -> SymbolPalette.energia
    SplitDayRole.PULL -> SymbolPalette.columna
    SplitDayRole.TORSO -> SymbolPalette.mente
    SplitDayRole.FULL_BODY, SplitDayRole.OTHER, SplitDayRole.REST -> WizardColors.text
}

/**
 * Un reparto del catálogo (lienzo v6, m-split: `.pcard`): el radio (marcado si el programa o la semana ya lo usan), el nombre en Syne,
 * «4 días» y su semana como barras de color por grupo (solo los días de entreno). Si el reparto no tiene plantilla para algún día, lo
 * dice en una línea. La tarjeta entera es el botón: abre la hoja de aplicar ([onApply]); en modo comparar o de selección múltiple, marca
 * el reparto ([onToggle]) con un filete en tinta y un check en la esquina.
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
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    entranceIndex: Int = 0,
) {
    val selecting = mode != SplitCatalogMode.BROWSE
    val trainingRoles = split.pattern.map { splitDayMark(it).role }.filter { it != SplitDayRole.REST }
    val shape = RoundedCornerShape(24.dp)
    val action = if (selecting) onToggle else onApply
    val clickLabel = when {
        !selecting -> "Abrir ${split.name}"
        selected -> "Quitar de la selección"
        else -> "Añadir a la selección"
    }
    Box(modifier = modifier.fillMaxWidth().padding(bottom = 10.dp).kpknEntrance("split-catalog", entranceIndex)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 78.dp)
                .clip(shape)
                .background(KpknSurface.module, shape)
                .border(1.dp, if (selected) WizardColors.text else Color.White.copy(alpha = 0.07f), shape)
                .testTag("split_aplicar_${split.id}")
                .semantics {
                    if (selecting) stateDescription = if (selected) "Elegido" else "No elegido"
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = clickLabel,
                    onClick = action,
                )
                .padding(12.dp)
                .padding(end = if (selected) 32.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LienzoRadio(selected = isCurrent)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = split.name, style = SplitCardTitleStyle, color = WizardColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = splitDaysLabel(trainingRoles.size),
                    style = LienzoCapStyle,
                    color = WizardColors.textFaint,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    trainingRoles.forEach { role ->
                        Box(Modifier.width(22.dp).heightIn(min = 6.dp, max = 6.dp).clip(RoundedCornerShape(3.dp)).background(splitBarColor(role)))
                    }
                }
                if (unavailableReason != null) {
                    Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                        Icon(imageVector = KpknLineIcons.Info, contentDescription = null, tint = SymbolPalette.energia, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = unavailableReason,
                            style = LienzoCapStyle,
                            color = WizardColors.textFaint,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(WizardColors.text, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = KpknLineIcons.Check, contentDescription = null, tint = WizardColors.ctaContent, modifier = Modifier.size(16.dp))
            }
        }
    }
}
