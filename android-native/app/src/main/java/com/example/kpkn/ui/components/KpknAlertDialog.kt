package com.example.kpkn.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeState

/**
 * Canonical KPKN Liquid Glass [AlertDialog].
 * Transparent Material container + [kpknGlass] (or solid fallback) on the dialog surface.
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
    properties: DialogProperties = DialogProperties(),
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState? = LocalHazeState.current,
) {
    // AlertDialog lives in its own window; use the cross-window frosted fallback.
    val glassModifier = modifier.kpknWindowGlass(shape)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = glassModifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = Color.Transparent,
        iconContentColor = Color.White,
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.85f),
        tonalElevation = 0.dp,
        properties = properties,
    )
}

/** Convenience overload matching the most common string-based call sites. */
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
) {
    KpknAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = dismissLabel?.let { label ->
            {
                TextButton(onClick = { (onDismiss ?: onDismissRequest)() }) {
                    Text(label)
                }
            }
        },
        title = { Text(title) },
        text = { Text(text) },
        hazeState = hazeState,
    )
}
