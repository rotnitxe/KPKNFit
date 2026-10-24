package com.example.kpkn.screens.progress.design

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknType

/** Ventana sin ningún registro asignable: se dice «sin datos», nunca una tabla de ceros. */
@Composable
fun MuscleVolumeEmpty(days: Int) {
    KpknEmptyState(
        icon = KpknLineIcons.Layers,
        title = "Sin registros en esta ventana",
        body = "No hay entrenamientos registrados en los últimos $days días. Cuando registres sesiones verás aquí las series por músculo.",
    )
}

/**
 * Línea discreta con los registros que no se pudieron situar en el calendario (sin fecha legible). No suman a ninguna cifra; solo se
 * avisa de que existen. Con cero registros así no se pinta nada.
 */
@Composable
fun MuscleVolumeUnassignedNote(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Text(
        text = if (count == 1) {
            "1 registro sin fecha asignable no se cuenta."
        } else {
            "$count registros sin fecha asignable no se cuentan."
        },
        style = KpknType.caption,
        color = WizardColors.textMuted,
        modifier = modifier.testTag("muscle-volume-unassigned"),
    )
}
