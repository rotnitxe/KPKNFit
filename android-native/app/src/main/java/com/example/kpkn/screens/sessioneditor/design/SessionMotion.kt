package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/*
 * Movimiento del editor de sesiones (SE8). Con `wizardReducedMotion()`, nada se despliega, se desliza ni rebota: solo fundidos de 120 ms y
 * valores finales. Sin el ajuste, son las transiciones de siempre del bloque.
 */

/** Entrada de un bloque del editor que aparece con su contenido: fundido y expansión, o solo fundido si el movimiento está reducido. */
@Composable
internal fun sessionEnter(): EnterTransition =
    if (wizardReducedMotion()) fadeIn(animationSpec = tween(durationMillis = 120)) else fadeIn() + expandIn()

/** Salida de un bloque del editor: el contrario de [sessionEnter]. */
@Composable
internal fun sessionExit(): ExitTransition =
    if (wizardReducedMotion()) fadeOut(animationSpec = tween(durationMillis = 120)) else fadeOut() + shrinkOut()

/** Entrada vertical de un bloque (superserie, descanso): se despliega en alto, o solo se funde si el movimiento está reducido. */
@Composable
internal fun sessionEnterVertical(): EnterTransition =
    if (wizardReducedMotion()) fadeIn(animationSpec = tween(durationMillis = 120)) else expandVertically() + fadeIn()

/** Salida vertical de un bloque: el contrario de [sessionEnterVertical]. */
@Composable
internal fun sessionExitVertical(): ExitTransition =
    if (wizardReducedMotion()) fadeOut(animationSpec = tween(durationMillis = 120)) else shrinkVertically() + fadeOut()

/** Muelle de un valor que se anima (escala de arrastre, giro de una flecha): de golpe si el movimiento está reducido. */
@Composable
internal fun sessionFloatSpec(): AnimationSpec<Float> =
    if (wizardReducedMotion()) snap() else spring()
