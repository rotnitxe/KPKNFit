package com.example.kpkn.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback

/**
 * Isla superior contextual (KPKN_UI_V2 §3): vidrio v2 flotante, bajo la barra de estado, con 12 dp de margen y 56 dp de alto. Entra y
 * sale como la de Home: fundido, bajada con resorte y escala desde el centro superior. Su contenido entra escalonado (icono a 0 ms,
 * título a 40 ms y acciones a 80 ms). Al cambiar de [title] el contenido nuevo sube 8 dp con fundido cruzado de 220 ms.
 *
 * Se pinta con [KpknPortal], hermana de la fuente del desenfoque, y solo tiene capa en el anfitrión mientras entra, está o sale. Con
 * [visible] en falso no queda ningún nodo tocable. Con movimiento reducido, solo fundidos de 120 ms.
 *
 * @param leading icono o botón de la izquierda, p. ej. «Atrás».
 * @param actions acciones de la derecha.
 */
@Composable
fun KpknTopIsland(
    visible: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val appear = remember { MutableTransitionState(false) }
    SideEffect { appear.targetState = visible }
    // Mientras la salida no ha terminado (currentState sigue en true) la capa sigue montada; al acabar, se libera.
    if (visible || appear.currentState) {
        KpknPortal {
            IslandFrame(
                appear = appear,
                modifier = modifier,
                title = title,
                subtitle = subtitle,
                leading = leading,
                actions = actions,
            )
        }
    }
}

@Composable
private fun IslandFrame(
    appear: MutableTransitionState<Boolean>,
    modifier: Modifier,
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    val reduced = wizardReducedMotion()
    val density = LocalDensity.current
    val slidePx = with(density) { 24.dp.roundToPx() }
    val fallPx = with(density) { 16.dp.roundToPx() }
    val contextPx = with(density) { 8.dp.roundToPx() }
    val sixPx = with(density) { 6.dp.toPx() }
    val origin = TransformOrigin(0.5f, 0f)
    // Entrada y salida de la isla (§3). Con movimiento reducido, solo fundidos de 120 ms.
    val enter: EnterTransition = if (reduced) {
        fadeIn(animationSpec = tween(durationMillis = 120))
    } else {
        fadeIn(animationSpec = tween(durationMillis = 180)) +
            slideInVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)) { -slidePx } +
            scaleIn(animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f), initialScale = 0.92f, transformOrigin = origin)
    }
    val exit: ExitTransition = if (reduced) {
        fadeOut(animationSpec = tween(durationMillis = 120))
    } else {
        fadeOut(animationSpec = tween(durationMillis = 140)) +
            slideOutVertically(animationSpec = tween(durationMillis = 160)) { -fallPx } +
            scaleOut(animationSpec = tween(durationMillis = 160), targetScale = 0.96f, transformOrigin = origin)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp)
            .height(56.dp),
    ) {
        AnimatedVisibility(
            visibleState = appear,
            modifier = Modifier.fillMaxSize(),
            enter = enter,
            exit = exit,
        ) {
            IslandBody(
                title = title,
                subtitle = subtitle,
                leading = leading,
                actions = actions,
                reduced = reduced,
                contextPx = contextPx,
                sixPx = sixPx,
            )
        }
    }
}

/** Contenido de la isla con su vidrio v2 y la entrada escalonada de §3. Se compone cada vez que la isla entra. */
@Composable
private fun IslandBody(
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    reduced: Boolean,
    contextPx: Int,
    sixPx: Float,
) {
    val stagger = remember { MutableTransitionState(false).apply { targetState = true } }
    val transition = rememberTransition(transitionState = stagger, label = "kpknIslandStagger")
    val iconIn by transition.animateFloat(
        transitionSpec = { tween(durationMillis = if (reduced) 120 else 180) },
        label = "kpknIslandIcon",
    ) { if (it) 1f else 0f }
    val textIn by transition.animateFloat(
        transitionSpec = { tween(durationMillis = if (reduced) 120 else 180, delayMillis = if (reduced) 0 else 40) },
        label = "kpknIslandText",
    ) { if (it) 1f else 0f }
    val actionsIn by transition.animateFloat(
        transitionSpec = { tween(durationMillis = if (reduced) 120 else 180, delayMillis = if (reduced) 0 else 80) },
        label = "kpknIslandActions",
    ) { if (it) 1f else 0f }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .kpknGlassOrFallback(LocalHazeState.current, KpknShape.island, variant = KpknGlassVariant.ISLAND),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (leading != null) {
                Box(modifier = Modifier.graphicsLayer { alpha = iconIn }) {
                    AnimatedContent(
                        targetState = title,
                        transitionSpec = { islandContextTransform(reduced, contextPx) },
                        label = "kpknIslandLeading",
                    ) {
                        leading()
                    }
                }
            }
            AnimatedContent(
                targetState = title,
                transitionSpec = { islandContextTransform(reduced, contextPx) },
                label = "kpknIslandTitle",
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        alpha = textIn
                        translationX = if (reduced) 0f else (1f - textIn) * -sixPx
                    },
            ) { current ->
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = current,
                        style = KpknType.islandTitle,
                        color = WizardColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = KpknType.islandSubtitle,
                            color = WizardColors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.graphicsLayer {
                    alpha = actionsIn
                    translationX = if (reduced) 0f else (1f - actionsIn) * -sixPx
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

/** Cambio de contexto (§3): el contenido nuevo sube 8 dp con fundido cruzado de 220 ms. Con movimiento reducido, fundido de 120 ms. */
private fun islandContextTransform(reduced: Boolean, slidePx: Int): ContentTransform =
    if (reduced) {
        fadeIn(animationSpec = tween(durationMillis = 120)) togetherWith fadeOut(animationSpec = tween(durationMillis = 120))
    } else {
        (fadeIn(animationSpec = tween(durationMillis = 220)) + slideInVertically(animationSpec = tween(durationMillis = 220)) { slidePx })
            .togetherWith(fadeOut(animationSpec = tween(durationMillis = 220)))
    }
