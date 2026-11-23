package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType

/**
 * Banner flotante KPKN de aviso o bloqueo de guardado automático en el editor de sesiones.
 *
 * - Validación de nombre o reglas: superficie cálida `#161412`, borde ámbar sutil al 28 % y acción rápida `"Nombrar"`.
 * - Bloqueos críticos o de catálogo: superficie cálida `#181112`, borde rojo sutil al 28 % y acción `"Ver el ejercicio"`.
 */
@Composable
internal fun SessionEditorFrameBlockNotice(
    message: String,
    onOpenExercise: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onFocusSessionName: (() -> Unit)? = null,
    visible: Boolean = true,
) {
    val isMissingName = message.contains("falta el nombre", ignoreCase = true) ||
        message.contains("nombre de la sesión", ignoreCase = true)
    val isWarningTone = isMissingName || onOpenExercise == null
    val surfaceColor = if (isWarningTone) Color(0xFF161412) else Color(0xFF181112)
    val accentTone = if (isWarningTone) Color(0xFFE8A94B) else WizardColors.danger
    val borderColor = accentTone.copy(alpha = 0.28f)
    val shape = RoundedCornerShape(20.dp)

    val titleText = when {
        isMissingName -> "Falta el nombre de la sesión"
        onOpenExercise != null -> "Ejercicio pendiente de revisión"
        else -> "Guardado en pausa"
    }
    val subtitleText = if (isMissingName && onFocusSessionName != null) {
        "Escríbelo arriba para guardar los cambios."
    } else {
        message
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200, easing = EdMotion.Standard)) +
            slideInVertically(tween(220, easing = EdMotion.Standard)) { -it / 4 } +
            expandVertically(tween(220, easing = EdMotion.Standard)),
        exit = fadeOut(tween(160, easing = EdMotion.Ease)) +
            slideOutVertically(tween(180, easing = EdMotion.Ease)) { -it / 4 } +
            shrinkVertically(tween(180, easing = EdMotion.Ease)),
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .shadow(12.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(surfaceColor, shape)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentTone.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Alert,
                    contentDescription = null,
                    tint = accentTone,
                    modifier = Modifier.size(17.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = titleText,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                )
                Text(
                    text = subtitleText,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }

            val actionLabel: String?
            val actionClick: (() -> Unit)?
            when {
                isMissingName && onFocusSessionName != null -> {
                    actionLabel = "Nombrar"
                    actionClick = onFocusSessionName
                }
                onOpenExercise != null -> {
                    actionLabel = "Ver el ejercicio"
                    actionClick = onOpenExercise
                }
                else -> {
                    actionLabel = null
                    actionClick = null
                }
            }

            if (actionLabel != null && actionClick != null) {
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(accentTone.copy(alpha = 0.16f))
                        .border(1.dp, accentTone.copy(alpha = 0.30f), RoundedCornerShape(17.dp))
                        .clickable(role = Role.Button, onClick = actionClick)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = actionLabel,
                        style = KpknType.chip,
                        color = WizardColors.text,
                    )
                }
            }
        }
    }
}
