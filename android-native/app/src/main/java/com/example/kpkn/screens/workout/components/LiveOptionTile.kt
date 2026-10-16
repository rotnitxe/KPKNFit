package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Losa de opción de las hojas de la sesión en vivo (LV11, prototipos C1 y C2): un icono de línea sobre su rótulo, en un recuadro de
 * [corner] de radio que se pinta con [background]. «Borrar» y «Quitar» van en `danger` (icono, rótulo y un velo del 10 %). Una opción
 * que no se puede usar se queda apagada y no responde. Con la letra grande el rótulo pasa a dos líneas y la losa crece: las hermanas de
 * una fila miden lo mismo si quien la monta usa `IntrinsicSize.Min` + `fillMaxHeight()`. [contentDescription] es lo que lee el lector de
 * pantalla cuando el rótulo corto («Añadir») necesita más contexto («Añadir serie»).
 */
@Composable
internal fun LiveOptionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    minHeight: Dp = 64.dp,
    corner: Dp = 16.dp,
    background: Color = KpknSurface.module,
    iconSize: Dp = 18.dp,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(corner)
    val ink = if (danger) WizardColors.danger else WizardColors.text
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "liveOptionTileScale",
    )
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(shape)
            .background(if (danger) WizardColors.danger.copy(alpha = 0.10f) else background, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = ink)
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = KpknType.caption,
            color = ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Píldora de contorno de las hojas de la sesión en vivo (LV11, prototipo C2): 44 dp de alto, texto `chip` y un filete de 1 dp en tinta
 * (o en `danger` si [danger]), sin relleno. Su zona táctil llega a 48 dp. «Saltar ejercicio» y «Quitar de la sesión» son dos hermanas: en
 * una fila miden lo mismo.
 */
@Composable
internal fun LiveOutlinePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val ink = if (danger) WizardColors.danger else WizardColors.text
    val edge = if (danger) WizardColors.danger.copy(alpha = 0.35f) else WizardColors.text.copy(alpha = 0.24f)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(KpknShape.pill)
                .border(1.dp, edge, KpknShape.pill)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = KpknType.chip,
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Con la letra por encima del 115 % lo que iba en fila pasa a columna: las rejillas de losas a dos columnas y las píldoras de pie
 * apiladas. Así ningún rótulo se parte a mitad de palabra ni se corta con elipsis al 130 % (LV_COMUN).
 */
@Composable
internal fun largeLiveFont(): Boolean = LocalDensity.current.fontScale > 1.15f
