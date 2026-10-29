package com.example.kpkn.screens.programdetail.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface

/*
 * Píldoras y botones redondos de Program Detail con las medidas del lienzo v6 (`.pill`, `.pill.sm`, `.ib`), que no son las de las píldoras
 * generales de la app: 52 dp (15 sp) y 40 dp (13 sp) de alto, iconos de 18 y 16 dp.
 */

/** El texto de `.pill`: Inter SemiBold 15 sp. */
internal val ProgramPillStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)

/** El texto de `.pill.sm`: Inter SemiBold 13 sp. */
private val ProgramPillSmallStyle = ProgramPillStyle.copy(fontSize = 13.sp, lineHeight = 18.sp)

/** La tinta oscura del texto de la píldora principal (`#0c0c0c`). */
private val PillInk = Color(0xFF0C0C0C)

/**
 * Una píldora del lienzo: [filled] = `.pill.pri` (tinta con texto negro); si no, `.pill.sec` (borde de tinta al 24 %). [small] = `.pill.sm`.
 * [icon] va delante del texto, separado 8 dp.
 */
@Composable
internal fun ProgramLienzoPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    small: Boolean = false,
    icon: ImageVector? = null,
    tag: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "programLienzoPillScale",
    )
    val ink = if (filled) PillInk else WizardColors.text
    Box(
        modifier = modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .height(if (small) 40.dp else 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (enabled) 1f else 0.38f)
            .clip(KpknShape.pill)
            .background(if (filled) WizardColors.text else Color.Transparent, KpknShape.pill)
            .then(if (filled) Modifier else Modifier.border(1.dp, WizardColors.text.copy(alpha = 0.24f), KpknShape.pill))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 16.dp else 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(if (small) 16.dp else 18.dp), tint = ink)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = if (small) ProgramPillSmallStyle else ProgramPillStyle,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Un botón redondo del lienzo (`.ib`): [size] de lado (44 dp por defecto), icono de 20 dp en `textMuted`; con [filled], fondo `inset`
 * (`.ib.fill`). Con [enabled] = false se pinta al 38 % y no responde.
 */
@Composable
internal fun ProgramRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    tint: Color = WizardColors.textMuted,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(if (filled) KpknSurface.inset else Color.Transparent, CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = tint)
    }
}
