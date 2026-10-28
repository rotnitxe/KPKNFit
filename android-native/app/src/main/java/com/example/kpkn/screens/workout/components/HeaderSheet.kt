package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.kpkn.screens.onboarding.design.WizardFonts
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay

/** KpknSheet owns entry/drag/Back. Explicit close actions also leave with an animation. */
@Composable
internal fun HeaderSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, dismissible: Boolean = true, stableHeightFraction: Float? = null,
    showDragHandle: Boolean = false, scrim: Color = LiveSheetTokens.Veil, panelTint: Color = LiveSheetTokens.Panel,
    content: @Composable (close: () -> Unit) -> Unit) {
    var closing by remember { mutableStateOf(false) }
    val reduced = wizardReducedMotion()
    val exit by animateFloatAsState(if (closing) 1f else 0f, tween(if (reduced) 0 else 180), label = "header-sheet-exit")
    val travel = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    LaunchedEffect(closing) {
        if (closing) { if (!reduced) delay(180); onDismiss() }
    }
    KpknSheet(onDismissRequest = onDismiss, dismissible = dismissible, stableHeightFraction = stableHeightFraction, showDragHandle = showDragHandle,
        backdropScrim = scrim, additionalGlassScrim = panelTint,
        modifier = modifier.imePadding().graphicsLayer { translationY = exit * travel; alpha = 1f - exit }) {
        content { if (dismissible) closing = true }
    }
}

/**
 * Marco de las hojas de cabecera con el aspecto del prototipo 02 (como [LiveSheet]): asa, título en Syne 21, cuerpo desplazable y el
 * pie fijo con su filete. El pie sigue a la vista aunque el cuerpo se desplace, también con letra al 130 %.
 */
@Composable
internal fun HeaderSheetFrame(title: String, footer: @Composable ColumnScope.() -> Unit, body: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 33.dp, height = 3.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x30FFFFFF)))
        }
        Text(title, Modifier.padding(start = 19.dp, end = 19.dp, top = 10.dp, bottom = 8.dp),
            style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 24.sp,
                letterSpacing = (-0.4).sp), color = WizardColors.text)
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().testTag("live-header-sheet-body").fadeBottomWhenScrollable(scroll).verticalScroll(scroll)
            .padding(start = 19.dp, end = 19.dp, top = 6.dp, bottom = 15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = body)
        Column(Modifier.fillMaxWidth().liveSheetTopLine().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp), content = footer)
    }
}

/** Acción de las hojas como los botones del prototipo: 46 dp de alto, píldora en tinta (principal) o con borde (secundaria). */
@Composable
internal fun HeaderSheetAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, multiline: Boolean = false) {
    val shape = RoundedCornerShape(23.dp)
    val compactAutoSize = remember { TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 12.sp, stepSize = .5.sp) }
    Box(modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(shape)
            .then(if (primary) Modifier.background(WizardColors.text.copy(alpha = if (enabled) 1f else .35f))
                else Modifier.border(1.dp, Color(0x24FFFFFF), shape))
            .padding(horizontal = if (multiline) 8.dp else 14.dp), contentAlignment = Alignment.Center) {
            val ink = if (primary) Color(0xFF111111) else if (enabled) WizardColors.text else WizardColors.textFaint
            if (multiline) BasicText(text,
                style = KpknType.action.copy(color = ink, fontWeight = FontWeight.SemiBold, lineHeight = TextUnit.Unspecified, textAlign = TextAlign.Center),
                maxLines = 2, autoSize = compactAutoSize)
            else Text(text, style = KpknType.action.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                color = ink, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Proposal tabs: 30dp buttons inside the 38dp inset track, scoped to live-session sheets. */
@Composable
internal fun LiveCompactSegments(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    // `.segmented` del prototipo 02: pestañas subrayadas a todo el ancho (antes, una pista negra con la elegida en tinta).
    Row(modifier.fillMaxWidth().drawBehind {
        drawLine(LiveSheetTokens.Line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { index, label ->
            val active = selectedIndex == index
            Box(Modifier.weight(1f).heightIn(min = 40.dp)
                .semantics { selected = active }
                .clickable(enabled = enabled, role = Role.Tab) { onSelect(index) }
                .drawBehind { if (active) drawLine(WizardColors.text, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) },
                contentAlignment = Alignment.Center) {
                Text(label, style = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = if (active) WizardColors.text else LiveSheetTokens.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
