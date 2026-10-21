package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType

/**
 * Impacto de un reparto que reemplaza sesiones (P5): un módulo teñido de `danger` con cuántas sesiones y en cuántas semanas, y la
 * aceptación explícita antes de aplicar. Sustituye a `DestructiveImpactCard`. La aceptación sigue siendo la misma condición de siempre.
 */
@Composable
internal fun SplitImpactModule(
    affectedSessions: Int,
    affectedWeeks: Int,
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.fillMaxWidth(), accent = WizardColors.danger) {
        KpknModuleHeader(label = "Impacto")
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Se reemplazarán ${sessionsLabel(affectedSessions)}",
            style = KpknType.bodyStrong,
            color = WizardColors.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "En ${weeksLabel(affectedWeeks)}. Las sesiones actuales se sustituyen por las del reparto nuevo; revisa el alcance antes de aplicar.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        KpknCheckRow(
            checked = accepted,
            title = "Entiendo que se reemplazarán estas sesiones",
            onClick = { onAcceptedChange(!accepted) },
        )
    }
}

/** «1 sesión» o «3 sesiones». */
internal fun sessionsLabel(count: Int): String = if (count == 1) "1 sesión" else "$count sesiones"

/** «1 semana» o «3 semanas». */
internal fun weeksLabel(count: Int): String = if (count == 1) "1 semana" else "$count semanas"
