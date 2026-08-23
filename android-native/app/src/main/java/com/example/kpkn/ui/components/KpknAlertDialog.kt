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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState

/**
 * Canonical KPKN DarkMica alert dialog.
 *
 * In-composition centered glass card (via [KpknPortal]) — never a Material AlertDialog window,
 * so Haze can sample the activity hazeSource for live blur.
 *
 * Button contrast contract (same as sheets):
 * - Filled / tonal confirm → white well + **black** label
 * - Text / outlined dismiss → transparent + **white** label
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

        KpknSheetContentTheme {
            Column(
                modifier = modifier
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .kpknGlassOrFallback(hazeState, shape)
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (icon != null) {
                    Box(modifier = Modifier.padding(bottom = 12.dp)) { icon() }
                }
                if (title != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides Color.White,
                            LocalTextStyle provides MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        ) {
                            title()
                        }
                    }
                }
                if (text != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides Color.White.copy(alpha = 0.85f),
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                        ) {
                            text()
                        }
                    }
                }
                // Action row: reset text style color so filled white buttons can paint black labels.
                CompositionLocalProvider(
                    LocalContentColor provides KpknSheetTokens.Body,
                    LocalTextStyle provides MaterialTheme.typography.labelLarge,
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
            FilledTonalButton(
                onClick = onConfirm,
                colors = kpknSheetWhiteTonalButtonColors(),
            ) {
                Text(confirmLabel, color = KpknSheetTokens.ControlLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = dismissLabel?.let { label ->
            {
                TextButton(
                    onClick = { (onDismiss ?: onDismissRequest)() },
                    colors = kpknSheetDialogTextButtonColors(),
                ) {
                    Text(label, color = KpknSheetTokens.Body)
                }
            }
        },
        title = { Text(title) },
        text = { Text(text) },
        hazeState = hazeState,
    )
}

/** Confirm action for glass alerts: white fill + black label. */
@Composable
fun KpknAlertConfirmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = kpknSheetWhiteTonalButtonColors(),
    ) {
        Text(text, color = KpknSheetTokens.ControlLabel, fontWeight = FontWeight.Bold)
    }
}

/** Dismiss / secondary action for glass alerts: transparent + white label. */
@Composable
fun KpknAlertDismissButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = kpknSheetDialogTextButtonColors(),
    ) {
        Text(text, color = KpknSheetTokens.Body)
    }
}
