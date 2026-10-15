package com.example.kpkn.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.icons.KpknLineIcons

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

/** Prefijo de cada aviso. El éxito no lleva emoji: la marca KPKN lo pinta delante (SE9). */
private fun SnackbarType.emoji(): String = when (this) {
    SnackbarType.SUCCESS -> ""
    SnackbarType.DANGER -> "❌"
    SnackbarType.ACHIEVEMENT -> "🏆"
    SnackbarType.SUGGESTION -> "💡"
}

/** Tipo del aviso por el emoji de su texto; sin emoji, el aviso es de éxito. */
internal fun inferSnackbarType(message: String): SnackbarType = when {
    message.startsWith(SnackbarType.DANGER.emoji()) -> SnackbarType.DANGER
    message.startsWith(SnackbarType.ACHIEVEMENT.emoji()) -> SnackbarType.ACHIEVEMENT
    message.startsWith(SnackbarType.SUGGESTION.emoji()) -> SnackbarType.SUGGESTION
    else -> SnackbarType.SUCCESS
}

/** Texto que se muestra: el éxito va sin prefijo; los demás tipos, con su emoji delante. */
internal fun snackbarText(type: SnackbarType, message: String): String =
    if (type == SnackbarType.SUCCESS) message else "${type.emoji()} $message"

suspend fun SnackbarHostState.showKpknSnackbar(
    message: String,
    type: SnackbarType = SnackbarType.SUCCESS,
    duration: SnackbarDuration = SnackbarDuration.Short,
    actionLabel: String? = null,
): SnackbarResult {
    return showSnackbar(
        message = snackbarText(type, message),
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
        KpknSnackbarMessage(type = type, text = data.visuals.message)
    }
}

/** Texto del aviso: el éxito lleva delante la marca KPKN (SE9); los demás tipos, solo el texto. */
@Composable
private fun KpknSnackbarMessage(type: SnackbarType, text: String) {
    if (type == SnackbarType.SUCCESS) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = KpknLineIcons.Tower,
                contentDescription = null,
                tint = type.contentColor(),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = text, fontWeight = FontWeight.Bold)
        }
    } else {
        Text(text = text, fontWeight = FontWeight.Bold)
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
        KpknSnackbarMessage(type = type, text = snackbarText(type, message))
    }
}
