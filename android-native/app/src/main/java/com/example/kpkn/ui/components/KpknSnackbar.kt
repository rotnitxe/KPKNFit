package com.example.kpkn.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

enum class SnackbarType { SUCCESS, DANGER, ACHIEVEMENT, SUGGESTION }

/** Icono de cada tipo de aviso, del set KPKN Line: la torre de la marca para el éxito (SE9). */
internal fun SnackbarType.icon(): ImageVector = when (this) {
    SnackbarType.SUCCESS -> KpknLineIcons.Tower
    SnackbarType.DANGER -> KpknLineIcons.Alert
    SnackbarType.ACHIEVEMENT -> KpknLineIcons.Trophy
    SnackbarType.SUGGESTION -> KpknLineIcons.Bulb
}

/** Lo que viaja con cada aviso: el texto tal cual, sin emoji, y su tipo, que pinta el icono (SE12). */
internal class KpknSnackbarVisuals(
    override val message: String,
    val type: SnackbarType,
    override val actionLabel: String?,
    override val withDismissAction: Boolean,
    override val duration: SnackbarDuration,
) : SnackbarVisuals

/** Tipo de un aviso: el de [KpknSnackbarVisuals]. Cualquier otro aviso se lee como de éxito. */
internal fun SnackbarVisuals.snackbarType(): SnackbarType = (this as? KpknSnackbarVisuals)?.type ?: SnackbarType.SUCCESS

suspend fun SnackbarHostState.showKpknSnackbar(
    message: String,
    type: SnackbarType = SnackbarType.SUCCESS,
    duration: SnackbarDuration = SnackbarDuration.Short,
    actionLabel: String? = null,
): SnackbarResult = showSnackbar(
    KpknSnackbarVisuals(
        message = message,
        type = type,
        actionLabel = actionLabel,
        withDismissAction = true,
        duration = duration,
    ),
)

@Composable
fun KpknSnackbar(data: SnackbarData) {
    KpknSnackbarPiece(
        type = data.visuals.snackbarType(),
        message = data.visuals.message,
        actionLabel = data.visuals.actionLabel,
        withDismissAction = data.visuals.withDismissAction,
        onAction = data::performAction,
        onDismiss = data::dismiss,
    )
}

/**
 * La pieza del aviso (SE12): módulo `#111111` con filete blanco al 7 % y radio 24; el icono del tipo y el texto en Inter, en tinta; la
 * acción en tinta, como [KpknTextAction]; el cerrar, con [KpknIconButton]. Ningún emoji: el icono dice el tipo. Va `internal` para la
 * hoja de render.
 */
@Composable
internal fun KpknSnackbarPiece(
    type: SnackbarType,
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // A 130 % de letra, la acción en la misma fila deja el texto en una columna estrecha: entonces baja a su propia línea.
    val stackAction = LocalDensity.current.fontScale > 1.15f
    Snackbar(
        modifier = modifier
            .padding(12.dp)
            .border(width = 1.dp, color = KpknSurface.moduleBorder, shape = KpknShape.module),
        shape = KpknShape.module,
        containerColor = KpknSurface.module,
        contentColor = WizardColors.text,
        actionOnNewLine = stackAction,
        action = if (actionLabel != null) {
            { KpknTextAction(text = actionLabel, onClick = onAction) }
        } else {
            null
        },
        dismissAction = if (withDismissAction) {
            { KpknIconButton(icon = KpknLineIcons.Close, contentDescription = "Cerrar", onClick = onDismiss) }
        } else {
            null
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = type.icon(),
                contentDescription = null,
                tint = WizardColors.text,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(text = message, style = KpknType.bodyStrong, color = WizardColors.text)
        }
    }
}

@Composable
fun KpknSnackbarBanner(
    message: String,
    type: SnackbarType = SnackbarType.SUCCESS,
    modifier: Modifier = Modifier,
) {
    KpknSnackbarPiece(type = type, message = message, modifier = modifier)
}
