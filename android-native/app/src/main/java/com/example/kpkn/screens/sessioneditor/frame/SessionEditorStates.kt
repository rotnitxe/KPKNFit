package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSkeleton
import com.example.kpkn.ui.design.KpknType

/**
 * Cargando (ED-A · A6): una sola franja, «Preparando la sesión», con el esqueleto de un módulo debajo para que la pantalla no salte al
 * llegar los datos. Es el único aviso de ese estado: no hay barra de progreso ni otro texto encima.
 */
@Composable
internal fun SessionEditorLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("session_editor_state_loading"),
    ) {
        Text(
            text = "CARGANDO",
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(
                text = "Preparando la sesión",
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Cargando el programa…",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        Spacer(Modifier.height(20.dp))
        KpknSkeleton(modifier = Modifier.fillMaxWidth().height(96.dp))
    }
}

/**
 * Error al cargar (ED-A · A6): una sola franja con el título, la explicación y las dos salidas, «Volver» y «Reintentar». [message] es el
 * motivo concreto que dio la carga; si viene vacío se usa el título de siempre. No hay snackbar ni otro aviso a la vez.
 */
@Composable
internal fun SessionEditorLoadErrorState(
    message: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("session_editor_state_error"),
    ) {
        Text(
            text = "ERROR AL CARGAR",
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(18.dp))
        Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) {
            Text(
                text = message?.trim()?.takeIf { it.isNotEmpty() } ?: "No pudimos recuperar este programa.",
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Puedes reintentar la carga o volver al programa.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpknSecondaryPill(text = "Volver", onClick = onBack, modifier = Modifier.width(120.dp))
            KpknPrimaryPill(text = "Reintentar", onClick = onRetry, modifier = Modifier.width(150.dp))
        }
    }
}

/**
 * Un mensaje de la snackbar que cuenta lo mismo que el aviso fijo de «guardado bloqueado» (ED-A · A6): mientras ese aviso está puesto
 * ([blockMessage]), no se enseña además la snackbar con la misma causa, para que no haya dos avisos de lo mismo. Los mensajes de otra
 * causa (por ejemplo, el borrador local que no se pudo escribir) siguen saliendo.
 */
internal fun isRedundantWhileSaveBlocked(snackbarMessage: String, blockMessage: String?): Boolean {
    if (blockMessage == null) return false
    val text = snackbarMessage.trim()
    return text == blockMessage.trim() || text.startsWith("No se pudo guardar:")
}
