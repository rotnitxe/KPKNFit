package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.KpknGradient
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.KpknShadowSpec
import com.example.kpkn.ui.design.depth.kpknInset
import com.example.kpkn.ui.design.depth.kpknGlassPreset
import com.example.kpkn.ui.design.depth.kpknGradient
import com.example.kpkn.ui.design.depth.kpknShadow
import dev.chrisbanes.haze.HazeState

/*
 * REPARTO · controles del lienzo (`rp.css`): segmentados, chips, la barra flotante de vidrio, el botón de atrás de vidrio y la píldora.
 * Las sombras y el vidrio nombran el preset y dibujan el valor EXACTO de su fila de `paridad/rp.md`.
 */

/** `.rp-seg`: pista `#0d0d0d` con filete, 3 dp de margen, botones de 36 dp (radio 19); el elegido, `#272727` y tinta. */
@Composable
internal fun RpSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: List<Boolean>? = null,
    tag: String? = null,
) {
    val track = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(track)
            .background(RpColors.well, track)
            .border(1.dp, Color(0x10FFFFFF), track)
            .padding(1.dp)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selectedIndex
            val shape = RoundedCornerShape(19.dp)
            val isEnabled = enabled?.getOrNull(index) ?: true
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(shape)
                    .background(if (on) RpColors.segOn else Color.Transparent, shape)
                    .then(if (isEnabled) Modifier else Modifier.graphicsLayer { alpha = 0.35f })
                    .then(if (tag != null) Modifier.testTag("${tag}_$index") else Modifier)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = isEnabled, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = RpType.seg, color = if (on) WizardColors.text else WizardColors.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

/** `.rp-seg2`: el segmentado pequeño de «Antes / Después» (alto de 30 dp, 12 dp a los lados, pista de radio 18). */
@Composable
internal fun RpSegmentedSmall(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val track = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(track)
            .background(RpColors.well, track)
            .border(1.dp, Color(0x10FFFFFF), track)
            .padding(1.dp)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selectedIndex
            val shape = RoundedCornerShape(15.dp)
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(shape)
                    .background(if (on) RpColors.segOn else Color.Transparent, shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = RpType.seg2, color = if (on) WizardColors.text else WizardColors.textFaint, maxLines = 1)
            }
        }
    }
}

/** Un chip del lienzo con el alto y el relleno de la fila donde está (`34 / 13` en los filtros, `36 / 13` en las decisiones). */
@Composable
internal fun RpChip(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
    horizontalPadding: Dp = 13.dp,
    tag: String? = null,
) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (on) WizardColors.text else com.example.kpkn.ui.design.KpknSurface.inset, shape)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = RpType.meta, color = if (on) RpColors.onInk else WizardColors.textMuted, maxLines = 1)
    }
}

/**
 * `mask-image: linear-gradient(90deg, #0000, #000 16px, #000 calc(100% - 28px), #0000)` (fila rp-X01): la fila de chips se desvanece a los lados.
 * Se dibuja con una capa fuera de pantalla y la máscara con `DstIn`.
 */
internal fun Modifier.rpFadeEdgesX(startFade: Dp = 16.dp, endFade: Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val width = size.width
        if (width > 0f) {
            val start = (startFade.toPx() / width).coerceIn(0f, 1f)
            val end = (1f - endFade.toPx() / width).coerceIn(start, 1f)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    start to Color.Black,
                    end to Color.Black,
                    1f to Color.Transparent,
                    startX = 0f,
                    endX = width,
                ),
                topLeft = Offset.Zero,
                size = size,
                blendMode = BlendMode.DstIn,
            )
        }
    }

/** El botón de atrás de vidrio de la barra superior (`.topbar .glass`: 48 dp, vidrio de barra con su relleno y su sombra). */
@Composable
internal fun RpBackGlassButton(hazeState: HazeState?, onClick: () -> Unit, modifier: Modifier = Modifier, description: String = "Atrás") {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            // pulido:profundidad rp-S14 (barra-vidrio: 0 8 24 #0007 + inset 0 1 #ffffff1c) · rp-V03 (vidrio-barra) · rp-G07 (vidrio-relleno-barra)
            .kpknShadow(KpknShadowSpec(KpknShadow.BarraVidrio.spec.outerLayers), CircleShape)
            .kpknGlassPreset(hazeState, KpknGlassPreset.VidrioBarra, CircleShape)
            .kpknGradient(KpknGradient.VidrioRellenoBarra, CircleShape)
            .kpknInset(KpknShadow.BarraVidrio, CircleShape)
            .border(1.dp, Color(0x26FFFFFF), CircleShape)
            .semantics { contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(KpknLineIcons.ChevronLeft, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(20.dp))
    }
}

/**
 * La barra flotante de la vista previa y de la revisión (`.rp-cta.pl-glass`): una isla de vidrio de 64 dp con un botón tonal redondo a la
 * izquierda ([leading]) y la píldora principal, que ocupa el resto. Con [enabled] en falso la píldora se apaga (`#f2eee62e`).
 */
@Composable
internal fun RpCtaBar(
    hazeState: HazeState?,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingText: String? = null,
    leadingDescription: String = "",
    onLeading: () -> Unit = {},
    tag: String? = null,
) {
    val shape = RoundedCornerShape(34.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            // pulido:profundidad rp-S02 (isla-vidrio: 0 8 28 #000a + inset 0 1 #ffffff14) · rp-V02 (vidrio-isla)
            .kpknShadow(KpknShadowSpec(KpknShadow.IslaVidrio.spec.outerLayers), shape)
            .kpknGlassPreset(hazeState, KpknGlassPreset.VidrioIsla, shape)
            .background(Color(0xC21B1B1D), shape)
            .kpknInset(KpknShadow.IslaVidrio, shape)
            .border(RpGlassStroke, shape)
            .padding(1.dp)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null || leadingText != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x0FFFFFFF), RoundedCornerShape(24.dp))
                    .semantics { if (leadingDescription.isNotEmpty()) contentDescription = leadingDescription }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onLeading)
                    .size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, tint = WizardColors.text, modifier = Modifier.size(22.dp))
                if (leadingText != null) Text(leadingText, style = RpType.ghost, color = WizardColors.text, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true))
            }
        }
        val pillShape = RoundedCornerShape(24.dp)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(pillShape)
                .background(if (enabled) WizardColors.text else Color(0x2EF2EEE6), pillShape)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = RpType.pill, color = if (enabled) Color(0xFF0C0C0C) else Color(0x80F2EEE6), maxLines = 1)
        }
    }
}

/** La píldora principal o secundaria de una hoja (`.pill`, 52 dp); se apaga como la de la barra. */
@Composable
internal fun RpSheetPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, tag: String? = null, leadingIcon: ImageVector? = null) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(if (primary) (if (enabled) WizardColors.text else Color(0x2EF2EEE6)) else Color.Transparent, shape)
            .then(if (primary) Modifier else Modifier.border(1.dp, Color(0x3DF2EEE6), shape))
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (primary) (if (enabled) Color(0xFF0C0C0C) else Color(0x80F2EEE6)) else WizardColors.text
        if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, tint = ink, modifier = Modifier.size(17.dp))
        Text(label, style = RpType.pill, color = ink, maxLines = 1)
    }
}
