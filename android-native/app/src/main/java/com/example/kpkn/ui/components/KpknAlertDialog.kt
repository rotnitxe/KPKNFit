package com.example.kpkn.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknDangerPill
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.Typography as KpknAppTypography
import dev.chrisbanes.haze.HazeState

/**
 * Diálogo KPKN de vidrio v2 (KPKN_UI_V2 §2 y §6). Se monta sobre [KpknPortal], nunca en una ventana de Material, para que Haze
 * muestre lo que hay detrás. Todo va alineado a la izquierda (icono, título y texto); las acciones, a la derecha.
 *
 * Contrato de acciones: confirmar en píldora KPKN ([KpknPrimaryPill]) o, si es destructivo, en [KpknDangerPill]; cancelar como
 * acción de texto ([KpknAlertDismissButton]). El título va en `KpknType.dialogTitle` y en tinta; el texto, en `KpknType.body`.
 */
@Composable
fun KpknAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(KpknGlass.DialogCornerRadius),
    @Suppress("UNUSED_PARAMETER") properties: DialogProperties = DialogProperties(),
    hazeState: HazeState? = null,
) {
    val resolvedHaze = hazeState ?: LocalHazeState.current
    // Mientras el diálogo está en composición, la isla superior se oculta (I12). La versión de texto llega aquí, así que cuenta una vez.
    DisposableEffect(Unit) {
        KpknModalTracker.onOpened()
        onDispose { KpknModalTracker.onClosed() }
    }
    KpknPortal {
        KpknAlertDialogBody(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            hazeState = resolvedHaze,
        )
    }
}

/**
 * Tema del panel de los diálogos KPKN: tipografía de la app (Inter y Syne) y tokens de [kpknDialogColorScheme]. Envuelve el tema de
 * hoja y vuelve a poner los tokens después, porque el tema de hoja pisa fondo, superficies y contornos.
 */
@Composable
internal fun KpknDialogTheme(content: @Composable () -> Unit) {
    val colors = remember { kpknDialogColorScheme() }
    MaterialTheme(colorScheme = colors, typography = KpknAppTypography) {
        KpknSheetContentTheme {
            MaterialTheme(colorScheme = colors) {
                content()
            }
        }
    }
}

/**
 * Tokens de color del panel de los diálogos. Un `Button` o un `TextButton` de Material dentro de un diálogo sale en tinta (píldora
 * crema con letra negra, o texto crema), no en el primario del tema de la app (amarillo en el tema de alto contraste) ni en el morado
 * por defecto de Material. Vive solo dentro del panel: el tema global no cambia. Un color explícito de la llamada (un texto en rojo)
 * sigue mandando sobre estos.
 */
private fun kpknDialogColorScheme(): ColorScheme = darkColorScheme(
    primary = WizardColors.text,
    onPrimary = WizardColors.background,
    primaryContainer = WizardColors.text,
    onPrimaryContainer = WizardColors.background,
    inversePrimary = WizardColors.background,
    secondary = WizardColors.textMuted,
    onSecondary = WizardColors.background,
    secondaryContainer = KpknSurface.inset,
    onSecondaryContainer = WizardColors.text,
    tertiary = WizardColors.textMuted,
    onTertiary = WizardColors.background,
    tertiaryContainer = KpknSurface.inset,
    onTertiaryContainer = WizardColors.text,
    background = KpknSurface.module,
    onBackground = WizardColors.text,
    surface = KpknSurface.module,
    onSurface = WizardColors.text,
    surfaceVariant = KpknSurface.inset,
    onSurfaceVariant = WizardColors.textMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = WizardColors.text,
    inverseOnSurface = WizardColors.background,
    error = WizardColors.danger,
    onError = WizardColors.background,
    errorContainer = WizardColors.danger,
    onErrorContainer = WizardColors.background,
    outline = WizardColors.glassBorder,
    outlineVariant = KpknSurface.divider,
    scrim = Color.Black,
    surfaceBright = KpknSurface.modulePressed,
    surfaceDim = KpknSurface.canvas,
    surfaceContainerLowest = KpknSurface.canvas,
    surfaceContainerLow = KpknSurface.module,
    surfaceContainer = KpknSurface.module,
    surfaceContainerHigh = KpknSurface.inset,
    surfaceContainerHighest = KpknSurface.inset,
)

@Composable
private fun KpknAlertDialogBody(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier,
    dismissButton: @Composable (() -> Unit)?,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    shape: Shape,
    hazeState: HazeState?,
) {
    val panelInteraction = remember { MutableInteractionSource() }
    BackHandler(onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(360f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )

        KpknDialogTheme {
            Column(
                modifier = modifier
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    // Gris oscuro plano con sombra real de elevación (KpknFloating); sin vidrio ni desenfoque.
                    .kpknFloatingSurface(shape, KpknFloating.DialogElevation)
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                if (icon != null) {
                    Box(modifier = Modifier.padding(bottom = 12.dp)) { icon() }
                }
                if (title != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides WizardColors.text,
                            LocalTextStyle provides KpknType.dialogTitle,
                        ) {
                            title()
                        }
                    }
                }
                if (text != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides WizardColors.textMuted,
                            LocalTextStyle provides KpknType.body,
                        ) {
                            text()
                        }
                    }
                }
                // Fila de acciones: estilo de acción de KPKN, sin color de texto, para que cada botón pinte su propia etiqueta.
                CompositionLocalProvider(
                    LocalContentColor provides WizardColors.text,
                    LocalTextStyle provides KpknType.action,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        if (dismissButton != null) {
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * Sobrecarga de textos para las llamadas más comunes. Con [destructive], la confirmación va en peligro: se usa para borrar o quitar
 * algo, nunca para confirmar lo normal.
 */
@Composable
fun KpknAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    hazeState: HazeState? = LocalHazeState.current,
    destructive: Boolean = false,
) {
    KpknAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            if (destructive) {
                KpknDangerPill(text = confirmLabel, onClick = onConfirm)
            } else {
                KpknPrimaryPill(text = confirmLabel, onClick = onConfirm)
            }
        },
        dismissButton = dismissLabel?.let { label ->
            {
                KpknAlertDismissButton(text = label, onClick = { (onDismiss ?: onDismissRequest)() })
            }
        },
        title = { Text(title) },
        text = { Text(text) },
        hazeState = hazeState,
    )
}

/** Confirmar de los diálogos de vidrio: píldora principal de KPKN (antes, un botón blanco de Material), con la misma API. */
@Composable
fun KpknAlertConfirmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    KpknPrimaryPill(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
}

/** Cancelar de los diálogos de vidrio: acción de texto en `textMuted`, sin relleno, con 48 dp de zona táctil como mínimo. */
@Composable
fun KpknAlertDismissButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknTextAction(
        text = text,
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 48.dp),
        color = WizardColors.textMuted,
    )
}
