package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/*
 * El teléfono de la bienvenida: chasis sobrio (borde metálico muy sutil, bisel fino, punch-hole) con la escena
 * dentro. La escena se compone SIEMPRE en el lienzo lógico [WelcomeSceneSize] y aquí solo se escala de forma
 * uniforme al interior de la pantalla: nunca se vuelve a maquetar.
 */

/** Medidas de un teléfono en dp: todo sale de la altura del cuerpo, y la pantalla conserva la proporción del lienzo de las escenas. */
internal data class WelcomePhoneGeometry(
    val bodyWidth: Float,
    val bodyHeight: Float,
    val bezel: Float,
    val displayWidth: Float,
    val displayHeight: Float,
    val displayRadius: Float,
    val bodyRadius: Float,
) {
    /** Cuánto se reduce (o amplía) el lienzo de 300 dp para llenar la pantalla del teléfono. */
    val sceneScale: Float get() = displayWidth / WelcomeSceneSize.width.value
}

internal fun welcomePhoneGeometry(bodyHeight: Float): WelcomePhoneGeometry {
    val bezel = (bodyHeight * 0.0125f).coerceIn(4f, 8f)
    val displayHeight = bodyHeight - 2f * bezel
    val displayWidth = displayHeight * WelcomeSceneSize.width.value / WelcomeSceneSize.height.value
    val displayRadius = displayWidth * 0.125f
    return WelcomePhoneGeometry(
        bodyWidth = displayWidth + 2f * bezel,
        bodyHeight = bodyHeight,
        bezel = bezel,
        displayWidth = displayWidth,
        displayHeight = displayHeight,
        displayRadius = displayRadius,
        bodyRadius = displayRadius + bezel,
    )
}

private val MetalTop = Color(0xFF3A3B3F)
private val MetalMid = Color(0xFF17181A)
private val MetalBottom = Color(0xFF2A2B2F)
private val BezelBlack = Color(0xFF050506)
private val KeyMetal = Color(0xFF2E2F33)

/**
 * Un teléfono con [screen] dentro. Si [description] no es null, TODO el teléfono es una sola entidad semántica
 * con esa descripción (TalkBack no entra en la escena); si es null es decorativo y no se anuncia.
 */
@Composable
internal fun WelcomePhone(
    bodyHeight: Dp,
    description: String?,
    modifier: Modifier = Modifier,
    screen: @Composable () -> Unit,
) {
    val g = remember(bodyHeight) { welcomePhoneGeometry(bodyHeight.value) }
    val bodyShape = RoundedCornerShape(g.bodyRadius.dp)
    val displayShape = RoundedCornerShape(g.displayRadius.dp)
    Box(
        modifier
            .size(g.bodyWidth.dp, g.bodyHeight.dp)
            .clearAndSetSemantics {
                if (description != null) {
                    contentDescription = description
                    role = Role.Image
                }
            },
    ) {
        // Cuerpo: metal oscuro con un filete de luz que cae de la esquina superior izquierda.
        Box(
            Modifier
                .matchParentSize()
                .shadow(22.dp, bodyShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(bodyShape)
                .background(Brush.verticalGradient(listOf(MetalTop, MetalMid, MetalBottom)))
                .border(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.26f)),
                        start = Offset.Zero,
                        end = Offset(g.bodyWidth * 2f, g.bodyHeight),
                    ),
                    bodyShape,
                ),
        )
        // Teclas laterales: dos filetes que asoman por el borde derecho.
        Box(Modifier.align(Alignment.TopEnd).offset(x = 1.5.dp, y = (g.bodyHeight * 0.20f).dp).size(2.5.dp, (g.bodyHeight * 0.075f).dp).clip(RoundedCornerShape(1.dp)).background(KeyMetal))
        Box(Modifier.align(Alignment.TopEnd).offset(x = 1.5.dp, y = (g.bodyHeight * 0.30f).dp).size(2.5.dp, (g.bodyHeight * 0.045f).dp).clip(RoundedCornerShape(1.dp)).background(KeyMetal))
        // Bisel negro y, dentro, la pantalla.
        Box(
            Modifier
                .matchParentSize()
                .padding(1.5.dp)
                .clip(RoundedCornerShape((g.bodyRadius - 1.5f).dp))
                .background(BezelBlack),
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(g.bezel.dp)
                .clip(displayShape)
                .background(WelcomeScenePalette.screen),
        ) {
            screen()
            // Un destello casi imperceptible en la esquina superior izquierda: el cristal, sin adornos.
            Box(
                Modifier.matchParentSize().background(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.045f),
                        0.42f to Color.Transparent,
                        start = Offset.Zero,
                        end = Offset(g.displayWidth * 2.2f, g.displayHeight * 0.9f),
                    ),
                ),
            )
            // Cámara frontal (punch-hole).
            val hole = (g.displayWidth * 0.034f).dp
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (g.displayWidth * 0.046f).dp)
                    .size(hole)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(0.8.dp, Color(0xFF1B2230), CircleShape),
            )
        }
    }
}

/**
 * Compone [content] en el lienzo lógico [WelcomeSceneSize] y lo escala de forma uniforme a [modifier]. El tamaño de
 * letra del sistema no toca la escena (es una ilustración con su propia maquetación): su `fontScale` vale 1.
 */
@Composable
internal fun WelcomeScaledScene(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val sceneW = WelcomeSceneSize.width.roundToPx()
            val sceneH = WelcomeSceneSize.height.roundToPx()
            val scale = minOf(w / sceneW.toFloat(), h / sceneH.toFloat())
            val placeables = measurables.map { it.measure(Constraints.fixed(sceneW, sceneH)) }
            layout(w, h) {
                val x = ((w - sceneW * scale) / 2f).toInt()
                val y = ((h - sceneH * scale) / 2f).toInt()
                placeables.forEach { p ->
                    p.placeWithLayer(IntOffset(x, y)) {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            }
        }
    }
}
