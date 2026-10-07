package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import com.example.kpkn.screens.onboarding.design.eOutCubic
import kotlin.math.min

/** Duración del bucle de la escena de Entreno, en segundos. */
internal const val WelcomeEntrenoPeriod = 16f

/*
 * Escena de Entreno de la bienvenida: dentro de un teléfono, la app KPKN se maneja sola. Se crea una sesión
 * («Nueva sesión» → nombre tecleado → dos ejercicios del catálogo con la lista deslizándose) y se empieza: la
 * sesión en vivo registra tres series de Press banca (60 × 8, 62,5 × 8 con ¡Récord!, 62,5 × 7) con su descanso.
 *
 * Es una función PURA de `t` (ver [entrenoFrameAt]): todo el guion vive en `WelcomeEntrenoTimeline.kt` y aquí solo
 * se pinta, en un único Canvas, sobre el lienzo lógico de 300 × 620.
 */
@Composable
internal fun WelcomeEntrenoScene(t: Float, modifier: Modifier = Modifier) {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current.density
    val kit = remember(resolver, density) { EntrenoKit(resolver, density).also { it.warm() } }
    Canvas(modifier.fillMaxSize()) {
        drawEntrenoScene(t, kit)
    }
}

/** Pinta el cuadro de [t] en cualquier [DrawScope] (lo usa el composable y las pruebas que renderizan a imagen). */
internal fun DrawScope.drawEntrenoScene(t: Float, kit: EntrenoKit) = drawEntreno(entrenoFrameAt(t), kit)

private fun DrawScope.drawEntreno(f: EntrenoFrame, kit: EntrenoKit) {
    val s = min(size.width / EntrenoGeo.W, size.height / EntrenoGeo.H)
    if (s <= 0f) return
    val ox = (size.width - EntrenoGeo.W * s) / 2f
    val oy = (size.height - EntrenoGeo.H * s) / 2f
    withTransform({
        translate(ox, oy)
        scale(s, s, Offset.Zero)
        clipRect(0f, 0f, EntrenoGeo.W, EntrenoGeo.H)
    }) {
        val p = EntrenoPen(this, kit)
        p.fillScreen(ECol.screen)
        // pantalla de debajo: inicio o sesión en vivo
        entrenoDrawHome(p, f)
        entrenoDrawLive(p, f)
        // hojas
        entrenoDrawEditor(p, f)
        entrenoDrawPicker(p, f)
        // fundido del bucle (cubre la app, no la barra de estado del teléfono)
        p.fillScreen(ECol.screen, f.velo, EntrenoGeo.statusH)
        entrenoDrawStatusBar(p)
        entrenoDrawHomeBar(p)
        drawFinger(p, f.dedo)
    }
}

/**
 * El dedo: un disco translúcido con un filete claro por dentro y otro oscuro por fuera (se ve igual sobre la
 * tarjeta crema que sobre el fondo negro), un halo y una onda que se abre en cada toque.
 */
private fun drawFinger(p: EntrenoPen, d: EntrenoDedo) {
    if (d.alpha <= 0.003f) return
    p.group(d.alpha) {
        val r = 15f * (1f - 0.16f * d.press)
        p.circle(d.x, d.y, r + 9f, ECol.white, 0.06f + 0.05f * d.press)
        p.circle(d.x, d.y + 1.5f, r + 1.5f, ECol.black, 0.2f)
        p.circle(d.x, d.y, r, ECol.muted, 0.34f + 0.14f * d.press)
        p.ring(d.x, d.y, r + 0.9f, 1.6f, ECol.black, 0.38f)
        p.ring(d.x, d.y, r - 0.2f, 1.5f, ECol.white, 0.8f)
        p.circle(d.x, d.y, 2.3f, ECol.white, 0.85f)
        if (d.onda >= 0f && d.onda <= 1f) {
            val e = eOutCubic(d.onda)
            p.ring(d.x, d.y, 15f + 24f * e, 2.2f * (1f - e) + 0.5f, ECol.white, 0.55f * (1f - d.onda))
            p.ring(d.x, d.y, 15.8f + 24f * e, 1f, ECol.black, 0.18f * (1f - d.onda))
        }
    }
}
