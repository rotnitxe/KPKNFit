package com.example.kpkn.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class SnackbarType { SUCCESS, DANGER, ACHIEVEMENT, SUGGESTION }

private fun SnackbarType.containerColor(): Color = when (this) {
    SnackbarType.SUCCESS -> Color(0xFF10B981)
    SnackbarType.DANGER -> Color(0xFFEF4444)
    SnackbarType.ACHIEVEMENT -> Color(0xFFFBBF24)
    SnackbarType.SUGGESTION -> Color(0xFF6366F1)
}

private fun SnackbarType.contentColor(): Color = when (this) {
    SnackbarType.SUCCESS -> Color.White
    SnackbarType.DANGER -> Color.White
    SnackbarType.ACHIEVEMENT -> Color.White
    SnackbarType.SUGGESTION -> Color.White
}

private fun SnackbarType.emoji(): String = when (this) {
    SnackbarType.SUCCESS -> "\u2705"
    SnackbarType.DANGER -> "\u274C"
    SnackbarType.ACHIEVEMENT -> "\uD83C\uDFC6"
    SnackbarType.SUGGESTION -> "\uD83D\uDCA1"
}

private fun inferSnackbarType(message: String): SnackbarType = when {
    message.startsWith(SnackbarType.SUCCESS.emoji()) -> SnackbarType.SUCCESS
    message.startsWith(SnackbarType.DANGER.emoji()) -> SnackbarType.DANGER
    message.startsWith(SnackbarType.ACHIEVEMENT.emoji()) -> SnackbarType.ACHIEVEMENT
    message.startsWith(SnackbarType.SUGGESTION.emoji()) -> SnackbarType.SUGGESTION
    else -> SnackbarType.SUCCESS
}

suspend fun SnackbarHostState.showKpknSnackbar(
    message: String,
    type: SnackbarType = SnackbarType.SUCCESS,
    duration: SnackbarDuration = SnackbarDuration.Short,
    actionLabel: String? = null,
): SnackbarResult {
    return showSnackbar(
        message = "${type.emoji()} $message",
        actionLabel = actionLabel,
        withDismissAction = true,
        duration = duration,
    )
}

@Composable
fun KpknSnackbar(data: SnackbarData) {
    val type = inferSnackbarType(data.visuals.message)
    val shape = RoundedCornerShape(12.dp)
    val accentColor = type.containerColor()

    Snackbar(
        modifier = Modifier
            .padding(12.dp)
            .kpknWindowGlass(shape, withBorder = false)
            .border(width = 1.dp, color = accentColor.copy(alpha = 0.55f), shape = shape),
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = type.contentColor(),
        actionContentColor = type.contentColor(),
        dismissActionContentColor = type.contentColor().copy(alpha = 0.6f),
        action = {
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) {
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissAction = {
            if (data.visuals.withDismissAction) {
                IconButton(onClick = { data.dismiss() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                    )
                }
            }
        },
    ) {
        Text(
            text = data.visuals.message,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun KpknSnackbarBanner(
    message: String,
    type: SnackbarType = SnackbarType.SUCCESS,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val accentColor = type.containerColor()

    Snackbar(
        modifier = modifier
            .padding(12.dp)
            .kpknWindowGlass(shape, withBorder = false)
            .border(width = 1.dp, color = accentColor.copy(alpha = 0.55f), shape = shape),
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = type.contentColor(),
    ) {
        Text(
            text = "${type.emoji()} $message",
            fontWeight = FontWeight.Bold,
        )
    }
}
