package com.example.kpkn.screens.programdetail.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknType

/** Una opción de «Más opciones». [onClick] se ejecuta después de cerrar el menú. */
internal data class ProgramMenuItem(val label: String, val onClick: () -> Unit)

/**
 * Menú de «Más opciones» (KPKN_UI_V2 §2, P1): un panel de vidrio bajo la fila superior, en la esquina derecha. Tocar fuera lo cierra.
 * Solo lleva las acciones que la pantalla ya tiene.
 */
@Composable
internal fun ProgramMoreMenu(items: List<ProgramMenuItem>, onDismiss: () -> Unit) {
    val hazeState = LocalHazeState.current
    val reduced = wizardReducedMotion()
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val interaction = remember { MutableInteractionSource() }
    KpknPortal {
        Box(modifier = Modifier.fillMaxSize()) {
            // Velo invisible: cualquier toque fuera del panel lo cierra.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
            )
            AnimatedVisibility(
                visibleState = shown,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 76.dp, end = 12.dp),
                enter = if (reduced) {
                    fadeIn(animationSpec = tween(durationMillis = 120))
                } else {
                    fadeIn(animationSpec = tween(durationMillis = 160)) +
                        scaleIn(
                            animationSpec = tween(durationMillis = 180),
                            initialScale = 0.96f,
                            transformOrigin = TransformOrigin(1f, 0f),
                        )
                },
            ) {
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .kpknGlassOrFallback(hazeState, KpknShape.module, variant = KpknGlassVariant.ISLAND)
                        .padding(vertical = 6.dp),
                ) {
                    items.forEach { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = {
                                        onDismiss()
                                        item.onClick()
                                    },
                                )
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(text = item.label, style = KpknType.bodyStrong, color = WizardColors.text)
                        }
                    }
                }
            }
        }
    }
}
