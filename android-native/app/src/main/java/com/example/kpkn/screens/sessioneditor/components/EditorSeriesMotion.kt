package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknEasing
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.kpknAnimate
import com.example.kpkn.ui.design.motion.kpknKeyframes

/*
 * Movimiento de las series y de las hojas comunes del editor (Pulido v1 · EDITOR · pasada B/C de las series).
 * Todo sale de `ed.css`: cada `animation` y cada `transition` con su duración, curva, retardo y relleno EXACTOS. Las familias que el catálogo
 * (`ui/design/motion`) agrupa con otra pero cuyo valor exacto difiere (`ed-in` 6 dp en 260 ms, `ed-flash`, `ed-popin`) se escriben aquí con su
 * `@keyframes` literal; el resto se toma del catálogo por nombre.
 */

/** `@keyframes` y `animation` propios del editor. Un solo sitio: las pantallas no escriben duraciones ni curvas sueltas. */
internal object EdAnimations {
    /** `ed-in` (ed-A06): `.ed-focus.anim`, `.ed-presets.anim`, `.ed-tcfg.anim` · 0,26 s `--ed-ease`: 6 dp hacia arriba con fundido. */
    val In = KpknAnimation(
        kpknKeyframes("ed-in", "@keyframes ed-in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }") {
            at(0f) { alpha(0f); translateY(6f) }
            at(1f) { alpha(1f); transformNone() }
        },
        KpknDuration(260), KpknMotion.Estandar,
    )

    /**
     * `ed-flash` (ed-A10): `.ed-sc.flash` · 0,6 s `--ed-ease`. Parte con el fondo `#f2eee633` y escala 1,04 y vuelve a su estado: el canal de
     * mezcla de color va de 0 (el color de partida) a 1 (el fondo propio de la ficha).
     */
    val Flash = KpknAnimation(
        kpknKeyframes("ed-flash", "@keyframes ed-flash { 0% { background: #f2eee633; transform: scale(1.04); } 100% { transform: none; } }") {
            at(0f) { colorMix(0f); scale(1.04f) }
            at(1f) { colorMix(1f); transformNone() }
        },
        KpknDuration(600), KpknMotion.Estandar,
    )

    /** `ed-bump` (ed-A15): `.ed-stv.bump b` · 0,24 s `--ed-spring`; es la familia `aparece-escala` del catálogo, tal cual. */
    val Bump: KpknAnimation = KpknFamilies.ApareceEscala.demo

    /** `ed-popin` con `--dy: 6px` (ed-A08): el menú que se abre ENCIMA del ancla; crece desde abajo. */
    val PopInUp = popIn(6f, "6px")

    /** `ed-popin` con `--dy: -6px` (ed-A08): el menú que se abre DEBAJO del ancla. */
    val PopInDown = popIn(-6f, "-6px")

    private fun popIn(dy: Float, css: String) = KpknAnimation(
        kpknKeyframes(
            "ed-popin",
            "@keyframes ed-popin { from { opacity: 0; transform: scale(.92) translateY(var(--dy,-6px)); } to { opacity: 1; transform: none; } } /* --dy: $css */",
        ) {
            at(0f) { alpha(0f); scale(0.92f); translateY(dy) }
            at(1f) { alpha(1f); transformNone() }
        },
        KpknDuration(200), KpknMotion.Estandar,
    )

    /** Fotogramas de referencia de la hoja (los tiempos reales van en `EdSheetSpec`, que los pasa a `KpknSheet`). `.ed-scrim` · `ed-fadein` 0,22 s `--ed-ease` (ed-A11). */
    val ScrimIn = KpknAnimation(KpknFamilies.FundidoEntrada.keyframes, KpknDuration(220), KpknMotion.Estandar)

    /** `.ed-scrim.out` · `ed-fadeout` 0,2 s `ease` con `forwards` (ed-A12): es `fundido-salida`, tal cual. */
    val ScrimOut: KpknAnimation = KpknFamilies.FundidoSalida.demo

    /** `.ed-sheet` · `ed-up` 0,34 s `--ed-ease` (ed-A13): la hoja sube su propio alto. */
    val SheetUp = KpknAnimation(KpknFamilies.HojaSube.keyframes, KpknDuration(340), KpknMotion.Estandar)

    /** `.ed-sheet.out` · `ed-down` 0,24 s `cubic-bezier(.5,0,.9,.5)` con `forwards` (ed-A14): es `hoja-baja`, tal cual. */
    val SheetDown: KpknAnimation = KpknFamilies.HojaBaja.demo
}

/** Cuenta cuántas veces se ha tocado un valor: la tecla de reinicio de [edBump] (`stepField` → `.bump` 260 ms). */
internal val LocalEdBump = compositionLocalOf { 0 }

/** Da a los textos de dentro de un campo el valor actual de [LocalEdBump]. */
@Composable
internal fun EdBumpProvider(count: Int, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEdBump provides count, content = content)
}

/**
 * `.ed-stv.bump b` (ed-A15, ed-J06): la cifra rebota (escala .86 → 1, 0,24 s `--ed-spring`) cada vez que el campo cambia por un paso o por
 * arrastrar el valor. No rebota al aparecer (cuenta 0) ni cuando el dato cambia por otra vía.
 */
@Composable
internal fun Modifier.edBump(): Modifier {
    val count = LocalEdBump.current
    return this.kpknAnimate(EdAnimations.Bump, enabled = count > 0, restartKey = count)
}

/** `ed-in` (ed-A06): entrada de 6 dp con fundido al aparecer, o cada vez que cambia [restartKey]. Con [enabled] falso no hace nada. */
internal fun Modifier.edEnter(restartKey: Any? = null, enabled: Boolean = true): Modifier =
    this.kpknAnimate(EdAnimations.In, enabled = enabled, restartKey = restartKey)

/**
 * `ed-in` solo cuando cambia [key] (no al aparecer): el foco de la serie entra cada vez que se elige otra ficha (`drawCard(ex, true)`), pero no
 * se repite si el desplazamiento de la lista vuelve a componer la tarjeta abierta. [generation] guarda cuántos cambios se han visto.
 */
@Composable
internal fun Modifier.edEnterOnChange(key: Any?): Modifier {
    val seen = remember { arrayOf(key, 0) }
    if (seen[0] != key) {
        seen[0] = key
        seen[1] = (seen[1] as Int) + 1
    }
    val generation = seen[1] as Int
    return this.edEnter(restartKey = generation, enabled = generation > 0)
}

/** `transition: <color> <ms> ease`: el color que se ve ahora, camino del [target] (se mezcla como CSS). Con movimiento reducido salta. */
@Composable
internal fun edTransitionColor(
    target: Color,
    millis: Int = 160,
    easing: KpknEasing = KpknMotion.Suave,
): Color {
    val state: State<Color> = animateKpknColorAsState(target, KpknDuration(millis), easing)
    return state.value
}

/** `transition: transform .2s --ed-ease` del chevron de un menú (ed-T31, ed-T34): 180° con el menú abierto, 0° cerrado. */
@Composable
internal fun edChevronRotation(open: Boolean): Float =
    animateKpknFloatAsState(if (open) 180f else 0f, KpknMotion.D200, KpknMotion.Estandar, label = "edChevron").value

/** El estado «pulsado» (`:active`) de un [MutableInteractionSource] que se pasa al `clickable` con `indication = null`. */
@Composable
internal fun rememberEdPressed(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val pressed by (source as InteractionSource).collectIsPressedAsState()
    return source to pressed
}
