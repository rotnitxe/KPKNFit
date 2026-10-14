package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance

/**
 * Módulo «Impacto de la sesión» (SE4, SESSION_EDITOR_V1 §2, punto 3): bajo la cabecera del editor, la torre de los RINGS de 72 dp con lo que
 * gastará la sesión y sus tres cifras a la derecha. Tocarlo abre el asistente en «Impacto». Sin datos del motor ([impact] nulo) no se pinta.
 */
@Composable
fun SessionImpactModule(
    impact: SessionImpact?,
    onOpenAssistant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (impact == null) return
    val channels = impact.channels
    KpknModule(
        onClick = onOpenAssistant,
        onClickLabel = "Abrir el asistente de la sesión",
        modifier = modifier
            .fillMaxWidth()
            .kpknEntrance("sesion-impacto", 0)
            .testTag("sesion_impacto")
            .semantics(mergeDescendants = true) {
                contentDescription = "Impacto de la sesión: músculos menos ${channels.musculos}, " +
                    "energía menos ${channels.energia}, columna menos ${channels.columna}. Abrir el asistente"
            },
    ) {
        KpknModuleHeader(label = "Impacto de la sesión")
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KpknTowerRings(
                values = TowerRingValues(
                    columna = channels.columna,
                    musculos = channels.musculos,
                    energia = channels.energia,
                ),
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.width(20.dp))
            SessionImpactFigures(channels = channels, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Estimado · toca para ver el detalle",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = KpknLineIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = WizardColors.textFaint,
            )
        }
    }
}
