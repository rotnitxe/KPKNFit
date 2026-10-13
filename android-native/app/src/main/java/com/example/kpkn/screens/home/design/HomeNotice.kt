package com.example.kpkn.screens.home.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Tono de un aviso de Home: [DANGER] para el exceso de carga y [WARN] para el resto de avisos. */
enum class HomeNoticeTone { DANGER, WARN }

/**
 * Aviso de Home: un módulo teñido en el tono ([KpknModule] con acento), con un icono de línea de 20 dp, el título, el cuerpo y una acción
 * de texto. Etiqueta de prueba: `home-notice-<tono>`.
 */
@Composable
fun HomeNotice(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    tone: HomeNoticeTone,
    modifier: Modifier = Modifier,
) {
    val accent = noticeAccent(tone)
    KpknModule(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HomeModuleMargin)
            .testTag("home-notice-${tone.name.lowercase()}"),
        accent = accent,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = noticeIcon(tone),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = accent,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
                Spacer(Modifier.height(4.dp))
                Text(text = body, style = KpknType.body, color = WizardColors.textMuted)
                KpknTextAction(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Color del tono: el rojo suave de la marca para el peligro y la energía (amarillo de los símbolos) para el aviso. */
private fun noticeAccent(tone: HomeNoticeTone): Color = when (tone) {
    HomeNoticeTone.DANGER -> WizardColors.danger
    HomeNoticeTone.WARN -> SymbolPalette.energia
}

/** Icono de línea del tono: la campana para el exceso de carga y la (i) para el aviso de ajuste. */
private fun noticeIcon(tone: HomeNoticeTone): ImageVector = when (tone) {
    HomeNoticeTone.DANGER -> KpknLineIcons.Bell
    HomeNoticeTone.WARN -> KpknLineIcons.Info
}
