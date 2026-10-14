package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.max

/**
 * Pestaña Impacto del asistente (SE4): la torre grande de 120 dp con el gasto por canal y, debajo, una fila por músculo con la barra de P4
 * (zonas y marca), de más a menos carga. Sin datos del motor muestra el esqueleto mientras se calcula, o un estado vacío.
 */
@Composable
fun SessionAssistantImpactTab(
    impact: SessionImpact?,
    analysisReady: Boolean,
) {
    if (impact == null) {
        if (analysisReady) {
            KpknEmptyState(
                icon = KpknLineIcons.Tower,
                title = "Sin impacto que mostrar",
                body = "Añade ejercicios con series para ver lo que gasta la sesión.",
            )
        } else {
            SessionAssistantLoading()
        }
        return
    }
    val channels = impact.channels
    KpknModule(modifier = Modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "RINGS de la sesión")
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KpknTowerRings(
                values = TowerRingValues(
                    columna = channels.columna,
                    musculos = channels.musculos,
                    energia = channels.energia,
                ),
                modifier = Modifier.size(120.dp),
            )
            Spacer(Modifier.width(20.dp))
            SessionImpactFigures(channels = channels, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Estimado por el motor para esta sesión.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
    }
    if (impact.muscles.isEmpty()) return
    // Una escala común para todas las barras: la mayor entre las series de un músculo y su máximo recuperable.
    val scaleMax = impact.muscles
        .maxOf { muscle -> max(muscle.sets, muscle.zones?.maxRecoverable?.toDouble() ?: 0.0) }
        .coerceAtLeast(1.0)
    KpknModule(modifier = Modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Músculos de la sesión")
        impact.muscles.forEachIndexed { index, muscle ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            }
            SessionImpactMuscleRow(muscle = muscle, scaleMax = scaleMax)
        }
    }
}
