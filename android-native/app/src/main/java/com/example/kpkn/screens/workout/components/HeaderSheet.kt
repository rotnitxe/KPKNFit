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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay

/** KpknSheet owns entry/drag/Back. Explicit close actions also leave with an animation. */
@Composable
internal fun HeaderSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, dismissible: Boolean = true, content: @Composable (close: () -> Unit) -> Unit) {
    var closing by remember { mutableStateOf(false) }
    val reduced = wizardReducedMotion()
    val exit by animateFloatAsState(if (closing) 1f else 0f, tween(if (reduced) 0 else 180), label = "header-sheet-exit")
    val travel = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    LaunchedEffect(closing) {
        if (closing) { if (!reduced) delay(180); onDismiss() }
    }
    KpknSheet(onDismissRequest = onDismiss, dismissible = dismissible, modifier = modifier.graphicsLayer { translationY = exit * travel; alpha = 1f - exit }) {
        content { if (dismissible) closing = true }
    }
}

/** The footer stays available when the body needs to scroll, including at 130% font size. */
@Composable
internal fun HeaderSheetFrame(title: String, footer: @Composable ColumnScope.() -> Unit, body: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = KpknType.title, color = WizardColors.text)
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().fadeBottomWhenScrollable(scroll).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = body)
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = footer)
    }
}

/** 36dp visual control within a 48dp touch target. */
@Composable
internal fun HeaderSheetAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, multiline: Boolean = false) {
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(if (multiline) 40.dp else 36.dp).clip(shape)
            .background(if (primary) WizardColors.text.copy(alpha = if (enabled) 1f else .3f) else KpknSurface.inset)
            .border(1.dp, WizardColors.textFaint.copy(alpha = .25f), shape).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, style = KpknType.action.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = if (primary) KpknSurface.canvas else if (enabled) WizardColors.text else WizardColors.textFaint,
                maxLines = if (multiline) 2 else 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        }
    }
}
