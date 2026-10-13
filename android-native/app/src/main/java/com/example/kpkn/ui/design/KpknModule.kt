package com.example.kpkn.ui.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import java.util.Locale

/**
 * Módulo de la app: un bloque con vida propia sobre el negro (KPKN_UI_V2 §1). Relleno `module`, filete `moduleBorder` de 1 dp y radio
 * 24 dp. Con [accent] es un aviso: relleno del acento al 8 % sobre `module` y filete al 24 %. Con [onClick] es un botón entero:
 * `Role.Button`, sin ripple de Material, relleno `modulePressed` al pulsar y escala 0,985 con resorte. Sin [onClick] no es tocable.
 */
@Composable
fun KpknModule(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressing = pressed && onClick != null
    val scale by animateFloatAsState(
        targetValue = if (pressing) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "kpknModuleScale",
    )
    val fill = if (pressing) KpknSurface.modulePressed else KpknSurface.module
    val edge = accent?.copy(alpha = 0.24f) ?: KpknSurface.moduleBorder
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(fill, KpknShape.module)
            .then(if (accent != null) Modifier.background(accent.copy(alpha = 0.08f), KpknShape.module) else Modifier)
            .border(1.dp, edge, KpknShape.module)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Cabecera de módulo: el eyebrow en mayúsculas y en `textMuted` a la izquierda, y la [action] a la derecha, centradas entre sí.
 * Alto mínimo de 40 dp; la acción de texto o el botón de icono tienen su zona táctil de 48 dp dentro.
 */
@Composable
fun KpknModuleHeader(
    label: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(Locale("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
            // Hasta dos líneas: la etiqueta se parte entre palabras y nunca se corta a media palabra (KPKN_UI_V2 §5).
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action != null) action()
    }
}
