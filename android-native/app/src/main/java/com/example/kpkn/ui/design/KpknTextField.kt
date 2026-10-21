package com.example.kpkn.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors

/**
 * Campo de texto de la app (KPKN_UI_V2): [BasicTextField] sobre `inset`, con radio de 16 dp, 56 dp de alto como mínimo y 16 dp de relleno.
 * El texto va en `body` y en tinta; el marcador, en `textFaint`, y el cursor, en tinta. El borde es de 1 dp: blanco al 6 %, en tinta al
 * 40 % con foco y en `danger` con [error]; el mensaje de error va debajo en `caption` y `danger`, y también se expone a accesibilidad.
 * [label] va encima, en `caption` y `textMuted`. Sin [enabled], todo va al 38 %.
 * Con `singleLine = false`, el marcador puede ocupar hasta 2 líneas; [maxLines] limita lo que crece el campo, que después se desplaza por dentro.
 */
@Composable
fun KpknTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    error: String? = null,
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val edge by animateColorAsState(
        targetValue = when {
            error != null -> WizardColors.danger
            focused -> WizardColors.text.copy(alpha = 0.40f)
            else -> Color.White.copy(alpha = 0.06f)
        },
        animationSpec = tween(durationMillis = 160),
        label = "kpknFieldEdge",
    )
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f },
    ) {
        if (label != null) {
            Text(
                text = label,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .background(KpknSurface.inset, shape)
                .border(width = 1.dp, color = edge, shape = shape)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = WizardColors.textFaint,
                )
                Spacer(Modifier.width(12.dp))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        val message = error
                        if (message != null) this.error(message)
                    },
                enabled = enabled,
                textStyle = KpknType.body.copy(color = WizardColors.text),
                cursorBrush = SolidColor(WizardColors.text),
                singleLine = singleLine,
                minLines = minLines.coerceAtMost(maxLines),
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interaction,
                decorationBox = { inner ->
                    // Arriba a la izquierda: un marcador de dos líneas no baja el cursor al centro.
                    Box(contentAlignment = Alignment.TopStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = KpknType.body,
                                color = WizardColors.textFaint,
                                maxLines = if (singleLine) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (error != null) {
            Text(
                text = error,
                style = KpknType.caption,
                color = WizardColors.danger,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}
