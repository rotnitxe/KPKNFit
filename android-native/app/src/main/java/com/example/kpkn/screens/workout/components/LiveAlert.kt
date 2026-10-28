package com.example.kpkn.screens.workout.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.KpknModalTracker
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.icons.KpknLineIcons

private val AlertShape = RoundedCornerShape(28.dp)

/**
 * `.v2-alert` del prototipo 02: tarjeta centrada (radio 28, degradado gris, borde blanco al 15 %) sobre un velo, con el icono en un
 * círculo, el título en Syne 26, el texto en gris, la acción principal ancha y la secundaria en texto. Sustituye, dentro de la sesión
 * en vivo, a los diálogos de Material con dos `TextButton`.
 */
@Composable
internal fun LiveAlert(
    title: String,
    text: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    icon: ImageVector = KpknLineIcons.Info,
    dangerSecondary: Boolean = false,
    dismissible: Boolean = true,
    primaryEnabled: Boolean = true,
    tag: String = "live-alert",
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    DisposableEffect(Unit) {
        KpknModalTracker.onOpened()
        onDispose { KpknModalTracker.onClosed() }
    }
    val body: @Composable () -> Unit = {
        BackHandler { if (dismissible) onDismiss() }
        // Por encima de las hojas del anfitrión (KpknSheet se dibuja con zIndex 350): una alerta abierta sobre una hoja recibe los toques.
        Box(
            Modifier.fillMaxSize().zIndex(450f).background(Color(0x88000000))
                .clickable(remember { MutableInteractionSource() }, indication = null) { if (dismissible) onDismiss() }
                .padding(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 420.dp)
                    .shadow(28.dp, AlertShape, ambientColor = Color(0xAA000000), spotColor = Color(0xAA000000))
                    .clip(AlertShape)
                    .background(Brush.linearGradient(listOf(Color(0xED242424), Color(0xF5121212))))
                    .border(1.dp, Color(0x26FFFFFF), AlertShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .semantics { paneTitle = title }
                    .testTag(tag),
            ) {
                Box(
                    Modifier.size(43.dp).clip(CircleShape).background(Color(0x08FFFFFF)).border(1.dp, Color(0x19FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(22.dp), tint = WizardColors.text) }
                Text(
                    title,
                    Modifier.padding(top = 18.dp, bottom = 16.dp),
                    style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 29.sp),
                    color = WizardColors.text,
                )
                if (text.isNotBlank()) {
                    Text(text, Modifier.padding(bottom = 23.dp),
                        style = TextStyle(fontFamily = WizardFonts.body, fontSize = 13.sp, lineHeight = 22.sp), color = LiveSheetTokens.Muted)
                }
                extra?.invoke(this)
                LiveSheetPrimary(primaryLabel, onPrimary, enabled = primaryEnabled, tag = "$tag-primary")
                if (secondaryLabel != null && onSecondary != null) {
                    LiveSheetTextAction(secondaryLabel, onSecondary, Modifier.padding(top = 4.dp).testTag("$tag-secondary"), danger = dangerSecondary)
                }
            }
        }
    }
    if (LocalKpknOverlayHost.current != null) {
        KpknPortal { body() }
    } else {
        Popup(properties = PopupProperties(focusable = true, dismissOnBackPress = false, dismissOnClickOutside = false,
            usePlatformDefaultWidth = false)) { body() }
    }
}
