package com.example.kpkn.screens.workout

import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknChannel
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknFill
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.kpknKeyframes

/*
 * ROADMAP DE LA SESIÓN EN VIVO · movimiento (`lv.css`, familia `lv2-*`).
 * Las curvas y las duraciones salen de `KpknMotion`; los `@keyframes` llevan los valores EXACTOS del lienzo (desplazamientos de 14, 8 y 6 dp,
 * 60 % del alto…), que no son los de la demostración canónica de su familia (`KpknFamilies`). Cada animación nombra su fila de `paridad/lv.md`.
 */
internal object LiveRoadmapMotion {
    private val Estandar = KpknMotion.Estandar

    /** lv-A19 · `lv2-sl` 220 ms (.2,.8,.2,1): el texto de la banda 1 entra desde la izquierda al ir a la serie anterior. */
    val SlideLeft = KpknAnimation(
        kpknKeyframes("lv2-sl", "@keyframes lv2-sl { 0% { opacity: 0; transform: translateX(-14px); } 100% { opacity: 1; transform: none; } }") {
            at(0f) { alpha(0f); translateX(-14f) }
            at(1f) { alpha(1f); transformNone() }
        }, KpknDuration(220), Estandar, fill = KpknFill.Both,
    )

    /** lv-A20 · `lv2-sr` 220 ms: entra desde la derecha (serie siguiente, fin del descanso). */
    val SlideRight = KpknAnimation(
        kpknKeyframes("lv2-sr", "@keyframes lv2-sr { 0% { opacity: 0; transform: translateX(14px); } 100% { opacity: 1; transform: none; } }") {
            at(0f) { alpha(0f); translateX(14f) }
            at(1f) { alpha(1f); transformNone() }
        }, KpknDuration(220), Estandar, fill = KpknFill.Both,
    )

    /** lv-A21 · `lv2-su` 220 ms: entra desde abajo (otro ejercicio, empieza el descanso). */
    val SlideUp = KpknAnimation(
        kpknKeyframes("lv2-su", "@keyframes lv2-su { 0% { opacity: 0; transform: translateY(8px); } 100% { opacity: 1; transform: none; } }") {
            at(0f) { alpha(0f); translateY(8f) }
            at(1f) { alpha(1f); transformNone() }
        }, KpknDuration(220), Estandar, fill = KpknFill.Both,
    )

    /** lv-A13 · `lv2-roll` 260 ms: la cifra de series registradas rueda desde el 60 % de su alto. */
    val Roll = KpknAnimation(
        kpknKeyframes("lv2-roll", "@keyframes lv2-roll { 0% { opacity: 0; transform: translateY(60%); } 100% { opacity: 1; transform: none; } }") {
            at(0f) { alpha(0f); translateYFraction(0.6f) }
            at(1f) { alpha(1f); transformNone() }
        }, KpknDuration(260), Estandar,
    )

    /** lv-A14 · `lv2-ck` 360 ms con 60 ms de retardo, relleno `backwards`: el ✓ se dibuja (`stroke-dashoffset` 24 → 0 sobre un trazo de 24). */
    val Check = KpknAnimation(
        kpknKeyframes("lv2-ck", "@keyframes lv2-ck { from { stroke-dashoffset: 24; } to { stroke-dashoffset: 0; } }") {
            base(KpknChannel.DashOffset, 0f)
            at(0f) { dashOffset(1f) }
            at(1f) { dashOffset(0f) }
        }, KpknDuration(360), Estandar, delay = KpknMotion.R60, fill = KpknFill.Backwards,
    )

    /** lv-A15 · `lv2-wipe` 550 ms: barrido verde (#87C9A5) sobre el botón; el ancho llega al 100 % en el 70 % y se desvanece en el resto. */
    val Wipe = KpknAnimation(
        kpknKeyframes("lv2-wipe", "@keyframes lv2-wipe { 0% { width: 0px; opacity: 0.9; } 70% { width: 100%; opacity: 0.9; } 100% { width: 100%; opacity: 0; } }") {
            base(KpknChannel.WidthFraction, 0f)
            at(0f) { width(0f); alpha(0.9f) }
            at(0.7f) { width(1f); alpha(0.9f) }
            at(1f) { width(1f); alpha(0f) }
        }, KpknDuration(550), Estandar,
    )

    /** lv-A18 · `lv2-tick` 80 ms: pulso del segmento provisional de la tira (escala 1,12 × 1,30 → 1). */
    val Tick = KpknAnimation(
        kpknKeyframes("lv2-tick", "@keyframes lv2-tick { from { transform: scale(1.12, 1.3); } to { transform: none; } }") {
            at(0f) { scaleX(1.12f); scaleY(1.3f) }
            at(1f) { transformNone() }
        }, KpknDuration(80), Estandar,
    )

    /**
     * lv-A16 · `lv2-chip` 220 ms, relleno `forwards`, retardo 160 ms + 40 ms por chip: cada chip de serie de la «Ruta» pasa de
     * `opacity: 0; translateY(6px)` (el estilo del elemento) a su sitio.
     */
    fun chip(index: Int): KpknAnimation = KpknAnimation(
        kpknKeyframes("lv2-chip", "@keyframes lv2-chip { 100% { opacity: 1; transform: none; } }") {
            base(KpknChannel.Alpha, 0f)
            base(KpknChannel.TranslateYDp, 6f)
            at(1f) { alpha(1f); transformNone() }
        }, KpknDuration(220), Estandar, delay = KpknDuration(160 + 40 * index), fill = KpknFill.Forwards,
    )

    /** lv-A12 / lv-T06 · el anillo avanza del valor anterior al nuevo en 700 ms con (.2,.8,.2,1). */
    val RingMs = KpknMotion.D700

    /** lv-T13 · `.lv2-drain` se vacía con `width 250ms linear`. */
    val DrainMs = KpknDuration(250)

    /** lv-T20 / lv-A17 · el relleno de un segmento crece con `width 420ms (.2,.8,.2,1)`. */
    val FillMs = KpknDuration(420)

    /** lv-T19 · `height / background / box-shadow 280ms` de un segmento; mientras se recorre la tira, 120 ms. */
    val SegmentMs = KpknMotion.D280
    val SegmentScrubMs = KpknMotion.D120

    /** lv-T12 / lv-T14 · pulsación de la banda 1 y de ‹ ›: fondo en 90 ms. */
    val PressMs = KpknMotion.D90

    /** lv-T15 · el botón principal: escala y fondo en 160 ms al soltar (90 ms al apretar). */
    val RecordHoldMs = 550
}
