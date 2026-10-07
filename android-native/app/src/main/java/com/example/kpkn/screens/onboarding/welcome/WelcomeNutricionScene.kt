package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalFontFamilyResolver
import com.example.kpkn.screens.onboarding.design.clamp01
import com.example.kpkn.screens.onboarding.design.eInOut
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.lerpF

/** Duración del bucle de la escena de Nutrición, en segundos. */
internal const val WelcomeNutricionPeriod = 14f

/**
 * Nutrición: se describe una comida con palabras propias y KPKN la separa en alimentos, estima cantidades y calcula
 * calorías y macros; al guardarla, el resumen del día (anillos) se actualiza.
 *
 * Es una función pura de [t] (segundos desde el inicio del bucle, 0 ≤ t < [WelcomeNutricionPeriod]): la lógica de qué se
 * ve en cada instante está en `nutricionFrameAt` y aquí solo se dibuja. Se compone en el lienzo lógico de 300 × 620.
 */
@Composable
internal fun WelcomeNutricionScene(t: Float, modifier: Modifier = Modifier) {
    val resolver = LocalFontFamilyResolver.current
    val kit = remember(resolver) { NutricionKit(resolver) }
    val geo = remember(kit) { NutricionSheetGeometry(kit) }
    val frame = nutricionFrameAt(t)
    Canvas(modifier.fillMaxSize().clipToBounds()) { drawNutricionFrame(frame, kit, geo) }
}

internal fun DrawScope.drawNutricionFrame(f: NutricionFrame, kit: NutricionKit, geo: NutricionSheetGeometry) {
    val l = NutricionLayout
    val k = size.width / l.Width
    withTransform({ scale(k, k, Offset.Zero) }) {
        clipRect(0f, 0f, l.Width, l.Height) {
            val screen = Size(l.Width, l.Height)
            drawRect(NC.screen, Offset.Zero, screen)
            drawNutricionHome(f, kit)
            if (f.sheet.scrim > 0.002f) drawRect(Color.Black.a(0.58f * f.sheet.scrim), Offset.Zero, screen)
            drawNutricionSheet(f, kit, geo)
            drawCaption(f.caption, kit)
            drawFinger(f.touch)
            // Fundido del bucle: la pantalla se cubre con su propio fondo; la barra de estado se queda.
            if (f.dip > 0.002f) drawRect(NC.screen.a(f.dip), Offset.Zero, screen)
            drawSystemBars(kit)
        }
    }
}

// ── rótulo del paso (arriba, sobre la hoja)

private val CaptionTexts = arrayOf("", "Descríbela con tus palabras", "KPKN lo entiende", "Y tu día se actualiza")

private fun DrawScope.drawCaption(c: NutricionCaption, kit: NutricionKit) {
    if (c.step == 0) return
    val cur = CaptionTexts[c.step]
    val prev = CaptionTexts[c.previous]
    val t = c.mix
    fun pillWidth(text: String) = 42f + kit.width(NT.Caption, text) + 14f
    val wCur = pillWidth(cur)
    val wPrev = if (c.previous == 0) wCur else pillWidth(prev)
    val w = lerpF(wPrev, wCur, eInOut(t))
    val h = 24f
    val x = (NutricionLayout.Width - w) / 2f
    val y = 84f
    val appear = if (c.previous == 0) t else 1f
    rr(x, y, w, h, h / 2f, Color(0xFF1B1C1F).a(0.96f * appear))
    rrLine(x, y, w, h, h / 2f, Color.White.a(0.16f * appear), 1f)

    // Tres puntos: en cuál del recorrido va la escena.
    for (i in 0..2) {
        val weight = when {
            i == c.step - 1 -> t
            i == c.previous - 1 -> 1f - t
            else -> 0f
        }
        drawCircle(NC.ink.a((0.30f + 0.65f * weight) * appear), 2f + 0.9f * weight, Offset(x + 14f + i * 8f, y + h / 2f))
    }

    val tx = x + 42f
    val mid = y + h / 2f
    // El texto que sale se va primero y el que entra llega después: casi nunca se ven los dos a la vez.
    if (c.previous != 0) nTextMid(kit, NT.Caption, prev, tx, mid - 5f * eInOut(t), NC.ink.a(1f - clamp01(t / 0.45f)))
    nTextMid(kit, NT.Caption, cur, tx, mid + 5f * (1f - eOutCubic(t)), NC.ink.a(appear * (if (c.previous == 0) 1f else clamp01((t - 0.40f) / 0.60f))))
}

// ── dedo

private val FingerFill = Color(0xFF9AA0AA)

private fun DrawScope.drawFinger(tc: NutricionTouch) {
    if (tc.alpha <= 0.01f) return
    val c = Offset(tc.x, tc.y)
    if (tc.ripple > 0f && tc.ripple < 1f) {
        val r = lerpF(12f, 36f, eOutCubic(tc.ripple))
        val fade = (1f - tc.ripple) * tc.alpha
        drawCircle(Color.Black.a(0.20f * fade), r + 1.3f, c, style = Stroke(1.1f))
        drawCircle(Color.White.a(0.45f * fade), r, c, style = Stroke(1.6f))
    }
    val radius = 15f * (1f - 0.16f * tc.press)
    // Gris translúcido con doble filete: se ve igual sobre el fondo oscuro que sobre el botón claro.
    drawCircle(FingerFill.a((0.40f + 0.14f * tc.press) * tc.alpha), radius, c)
    drawCircle(Color.Black.a(0.30f * tc.alpha), radius + 0.8f, c, style = Stroke(1.2f))
    drawCircle(Color.White.a(0.62f * tc.alpha), radius - 0.4f, c, style = Stroke(1.2f))
}

// ── barra de estado, etiqueta «Ejemplo» y gesto de inicio (siempre a la vista)

private fun DrawScope.drawSystemBars(kit: NutricionKit) {
    val l = NutricionLayout
    nTextMid(kit, NT.Status, "9:41", 24f, 17f, NC.ink)
    for (i in 0..3) {
        val h = 3f + i * 2f
        rr(236f + i * 4.2f, 21.5f - h, 2.8f, h, 1f, NC.ink)
    }
    rrLine(260f, 12.5f, 20f, 9f, 2.6f, NC.ink.a(0.6f), 1.1f)
    rr(261.8f, 14.3f, 12.6f, 5.4f, 1.2f, NC.ink)
    rr(280.6f, 15.4f, 1.8f, 3.2f, 0.9f, NC.ink.a(0.6f))

    // Los datos son de ejemplo: se dice siempre, con discreción.
    val tagW = kit.width(NT.Tag, "EJEMPLO") + 14f
    rrLine(l.Width - l.Margin - tagW, 52f, tagW, 16f, 8f, Color.White.a(0.20f), 1f)
    nTextMid(kit, NT.Tag, "EJEMPLO", l.Width - l.Margin - tagW / 2f, 60f, NC.muted, 0.5f)

    rr(l.Width / 2f - 48f, 608f, 96f, 4f, 2f, NC.ink.a(0.55f))
}
