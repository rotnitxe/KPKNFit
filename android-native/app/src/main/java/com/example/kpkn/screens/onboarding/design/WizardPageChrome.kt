package com.example.kpkn.screens.onboarding.design

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Cromo fijo de la página larga: cabecera de cristal arriba, velo bajo la barra de estado y
 * botón de confirmar abajo con su propio velo. Es lo único que no se desliza; todo lo demás
 * pasa por detrás y lo que pasa se desenfoca solo bajo estas piezas (`haze`). Sin brillos, sin
 * sombras de colores: un filete uniforme y desenfoque real.
 */

/** Un tramo de la barra de progreso de la cabecera: un bloque del recorrido. */
data class WizardProgressSegment(
    /** 0..1: parte de los pasos del bloque que ya están confirmados. */
    val fill: Float,
)

/** Alto de la cabecera por debajo de la barra de estado: píldora + aire arriba y abajo. */
val WizardHeaderBlockHeight: Dp = WizardSpacing.headerPill + 16.dp

/**
 * Cabecera: atrás, progreso por bloques y salir. El centro NO repite la pregunta del paso
 * (la pregunta vive en su sección): dice en qué bloque estás y cuánto llevas.
 */
@Composable
fun WizardPageHeader(
    haze: HazeState,
    label: String,
    segments: List<WizardProgressSegment>,
    onBack: (() -> Unit)?,
    onExit: (() -> Unit)?,
    exitLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            WizardGlassPill(
                haze = haze,
                modifier = Modifier.size(WizardSpacing.headerPill),
                onClick = onBack,
                description = "Volver al paso anterior",
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = WizardColors.text)
            }
        }
        WizardGlassPill(
            haze = haze,
            modifier = Modifier.weight(1f).height(WizardSpacing.headerPill),
            onClick = null,
            description = "Progreso: $label",
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    // La Torre anidada, firma de la marca en cada paso.
                    Icon(
                        painter = painterResource(R.drawable.kpkn_simbolo),
                        contentDescription = null,
                        tint = WizardColors.text.copy(alpha = 0.92f),
                        modifier = Modifier.size(width = 17.dp, height = 11.dp),
                    )
                    Text(
                        text = label,
                        style = WizardTypography.eyebrow,
                        color = WizardColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    segments.forEach { segment -> ProgressSegment(segment) }
                }
            }
        }
        if (onExit != null) {
            WizardGlassPill(
                haze = haze,
                modifier = Modifier.size(WizardSpacing.headerPill),
                onClick = onExit,
                description = exitLabel,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color(0xFFFF7A7A))
            }
        }
    }
}

@Composable
private fun ProgressSegment(segment: WizardProgressSegment) {
    Box(
        modifier = Modifier
            .width(22.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Color.White.copy(alpha = 0.18f)),
    ) {
        // Bloque completo en verde de marca; el que se está recorriendo, en tinta.
        val fill = segment.fill.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth(fill)
                .height(3.dp)
                .background(if (fill >= 0.999f) WizardColors.done else WizardColors.progressFill),
        )
    }
}

@Composable
private fun WizardGlassPill(
    haze: HazeState,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    description: String,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .hazeEffect(state = haze, style = wizardHazeStyle())
            .border(1.dp, WizardColors.glassBorder, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/**
 * Velo bajo la barra de estado y la cabecera: negro casi opaco arriba que se disuelve hacia
 * abajo, para que el contenido no choque con el reloj ni con los iconos del sistema.
 */
@Composable
fun WizardTopScrim(height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to Color.Black,
                    0.45f to Color.Black,
                    0.72f to Color.Black.copy(alpha = 0.82f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

/**
 * Botón de confirmar anclado abajo con un velo propio que sube desde el borde de la pantalla:
 * el contenido se desvanece hacia el negro bajo el botón y nunca queda tapado a medias.
 *
 * Sigue al teclado (`imePadding`) y a la barra de navegación. `setup-continue` identifica el
 * único CTA y el nodo publica `Role.Button` con el estado real (deshabilitado de verdad, no
 * solo atenuado) para TalkBack y para las pruebas.
 */
@Composable
fun WizardDock(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    Box(modifier = modifier.fillMaxWidth().imePadding()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(WizardDockScrimHeight + navBottom)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.6f to Color.Black.copy(alpha = 0.78f),
                        1f to Color.Black.copy(alpha = 0.96f),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            WizardDockButton(enabled = enabled, label = label, onClick = onClick)
        }
    }
}

/** Alto del velo inferior sobre la barra de navegación. */
val WizardDockScrimHeight: Dp = 130.dp

/** Espacio que el contenido debe respetar por encima del borde inferior: botón, aire y velo. */
val WizardDockClearance: Dp = WizardSpacing.dockButton + 16.dp + 20.dp

@Composable
private fun WizardDockButton(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "wizard-dock-press",
    )
    Box(
        modifier = Modifier
            .size(WizardSpacing.dockButton)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.background(WizardColors.cta)
                } else {
                    // Deshabilitado: sólido, no translúcido. Si no, el texto que pasa por debajo se lee a través
                    // del botón y ensucia justo donde el usuario busca el estado.
                    Modifier
                        .background(WizardColors.ctaDisabled)
                        .border(1.dp, WizardColors.glassBorder, CircleShape)
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .testTag("setup-continue")
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = if (enabled) WizardColors.ctaContent else WizardColors.ctaDisabledContent,
            modifier = Modifier.size(30.dp),
        )
    }
}
