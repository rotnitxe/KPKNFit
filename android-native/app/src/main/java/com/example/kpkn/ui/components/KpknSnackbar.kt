package com.example.kpkn.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
    SnackbarType.ACHIEVEMENT -> Color(0xFF92400E)
    SnackbarType.SUGGESTION -> Color.White
}

private fun SnackbarType.emoji(): String = when (this) {
    SnackbarType.SUCCESS -> "\u2705"
    SnackbarType.DANGER -> "\u274C"
    SnackbarType.ACHIEVEMENT -> "\uD83C\uDFC6"
    SnackbarType.SUGGESTION -> "\uD83D\uDCA1"
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
    Snackbar(
        modifier = Modifier.padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        containerColor = Color(0xFF323232),
        contentColor = Color.White,
        actionContentColor = Color.White,
        dismissActionContentColor = Color.White.copy(alpha = 0.6f),
    ) {
        Text(
            text = data.visuals.message,
            fontWeight = FontWeight.Bold,
        )
    }
}
