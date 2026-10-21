package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknPrimaryPill

/**
 * Vacío honesto de la pestaña Volumen (P4): el programa no está activo. La acción activa el programa; el texto dice qué pasará.
 */
@Composable
fun ProgramVolumeInactive(onActivate: () -> Unit) {
    KpknEmptyState(
        icon = KpknLineIcons.Training,
        title = "Activa tu programa para ver el volumen",
        body = "Cuando el programa esté activo, KPKN usará tu estructura para mostrar cómo se reparte el volumen por músculo.",
        action = {
            KpknPrimaryPill(
                text = "Activar",
                onClick = onActivate,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/**
 * Vacío honesto de la pestaña Volumen (P4): el programa activo todavía no tiene sesiones. La acción lleva a crearlas en Semana.
 */
@Composable
fun ProgramVolumeNoSessions(onAddSession: () -> Unit) {
    KpknEmptyState(
        icon = KpknLineIcons.Calendar,
        title = "Aún no hay sesiones en esta semana",
        body = "Primero crea al menos una sesión dentro de tu semana para que podamos representar tu volumen planificado por músculo.",
        action = {
            KpknPrimaryPill(
                text = "Añadir sesión",
                onClick = onAddSession,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
