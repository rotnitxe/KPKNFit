package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.kpkn.screens.onboarding.design.clamp01
import com.example.kpkn.screens.onboarding.design.lerpF
import com.example.kpkn.screens.onboarding.design.mix

/*
 * Escena de Nutrición · la pantalla Nutrición de la app, simplificada: el resumen del día con sus cuatro anillos
 * (calorías, proteína, hidratos, grasas: trazo 10, hueco 3, arcos redondeados y pista al 10 %), las cifras, la
 * tarjeta de la comida y el botón «Registrar comida».
 */

/** 1796 → «1.796» (miles con punto, como en español). */
internal fun nutricionMiles(n: Int): String =
    if (n >= 1000) "${n / 1000}.${(n % 1000).toString().padStart(3, '0')}" else n.toString()

private val Teal = mix(NC.fat, NC.ink, 0.28f)

private const val CardTop = 398f
private const val CardPlaceholderH = 44f
private const val CardLoggedH = 100f

internal fun DrawScope.drawNutricionHome(f: NutricionFrame, kit: NutricionKit) {
    val h = f.home

    // Cabecera
    nText(kit, NT.Hoy, "Hoy", 20f, 38f, NC.muted)
    nText(kit, NT.Title, "Nutrición", 20f, 68f, NC.ink)

    drawRings(h)
    drawRingCenter(h, kit)

    // Meta del día y reparto de macros
    nTextMid(kit, NT.Goal, "de ${nutricionMiles(NutricionMeal.GoalKcal)} kcal", NutricionLayout.RingCx, 304f, NC.muted, 0.5f)
    drawLegend(h, kit)
    drawLeftPill(h, kit)

    drawMeals(h, kit)
    drawFab(h, kit)
}

// ── anillos

private fun DrawScope.drawRings(h: NutricionHomeFrame) {
    val l = NutricionLayout
    for (i in 0..3) {
        val radius = l.RingOuter - l.RingStroke / 2f - i * (l.RingStroke + l.RingGap)
        val topLeft = Offset(l.RingCx - radius, l.RingCy - radius)
        val size = Size(radius * 2f, radius * 2f)
        val color = NC.rings[i]
        // La pista, siempre: así los anillos vacíos se ven vacíos y no ausentes.
        drawArc(color.a(0.10f), 0f, 360f, false, topLeft, size, style = Stroke(l.RingStroke))
        val frac = h.rings[i]
        if (frac > 0.0015f) {
            val pulse = h.tour[i]
            if (pulse > 0.01f) {
                // «Este anillo es esta cifra»: un halo tenue que respira una vez.
                drawArc(color.a(0.24f * pulse), -90f, 360f * frac, false, topLeft, size,
                    style = Stroke(l.RingStroke + 6f * pulse, cap = StrokeCap.Round))
            }
            drawArc(color, -90f, 360f * frac, false, topLeft, size, style = Stroke(l.RingStroke, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawRingCenter(h: NutricionHomeFrame, kit: NutricionKit) {
    val cx = NutricionLayout.RingCx
    val cy = NutricionLayout.RingCy
    val pulse = h.tour[0]
    val k = 1f + 0.06f * pulse
    withTransform({ scale(k, k, Offset(cx, cy - 6f)) }) {
        nTextMid(kit, NT.RingNum, h.kcal.toString(), cx, cy - 6f, NC.ink, 0.5f)
    }
    nTextMid(kit, NT.RingUnit, "kcal", cx, cy + 14f, NC.muted, 0.5f)
}

// ── leyenda de macros

private val MacroLetters = arrayOf("P", "H", "G")
private const val LegendY = 328f

private fun DrawScope.drawLegend(h: NutricionHomeFrame, kit: NutricionKit) {
    val values = intArrayOf(h.protein, h.carbs, h.fat)
    val texts = Array(3) { "${values[it]} g" }
    // Cada elemento: punto + letra + valor. Se centran entre sí.
    val gap = 20f
    val widths = FloatArray(3) { 10f + kit.width(NT.Legend, MacroLetters[it]) + 4.5f + kit.width(NT.LegendVal, texts[it]) }
    var x = NutricionLayout.RingCx - (widths.sum() + gap * 2f) / 2f
    for (i in 0..2) {
        val pulse = h.tour[i + 1]
        val color = NC.rings[i + 1]
        val a = h.summaryAlpha
        val r = 3.5f + 1.6f * pulse
        drawCircle(color.a(a), r, Offset(x + 3.5f, LegendY))
        nTextMid(kit, NT.Legend, MacroLetters[i], x + 10f, LegendY, NC.ink.a(a))
        nTextMid(kit, NT.LegendVal, texts[i], x + 10f + kit.width(NT.Legend, MacroLetters[i]) + 4.5f, LegendY,
            mix(NC.ink.a(0.78f * a), NC.ink.a(a), pulse))
        x += widths[i] + gap
    }
}

// ── kcal restantes

private fun DrawScope.drawLeftPill(h: NutricionHomeFrame, kit: NutricionKit) {
    val label = "${nutricionMiles(h.kcalLeft)} kcal restantes"
    val w = 10f + 12f + 6f + kit.width(NT.Pill, label) + 12f
    val x = NutricionLayout.RingCx - w / 2f
    val y = 346f
    rr(x, y, w, 22f, 11f, Teal.a(0.13f))
    // Un círculo con su marca, como el icono de la app.
    drawCircle(Teal.a(0.9f), 5.2f, Offset(x + 10f + 6f, y + 11f), style = Stroke(1.2f))
    drawCheck(x + 10f + 6f, y + 11f, 6f, Teal, 1.2f)
    nTextMid(kit, NT.Pill, label, x + 10f + 12f + 6f, y + 11f, Teal)
}

// ── comidas del día

private fun DrawScope.drawMeals(h: NutricionHomeFrame, kit: NutricionKit) {
    nTextMid(kit, NT.Section, "COMIDAS DE HOY", 20f, 384f, NC.muted)
    val g = h.cardGrow
    val cardH = lerpF(CardPlaceholderH, CardLoggedH, g)
    val m = NutricionLayout.Margin
    val w = NutricionLayout.Width - 2f * m

    // Desayuno: de «sin registrar» a la comida con sus cuatro alimentos.
    rr(m, CardTop, w, cardH, 14f, Color.White.a(0.055f))
    rrLine(m, CardTop, w, cardH, 14f, mix(Color.White.a(0.09f), NC.ok.a(0.38f), g), 1f)

    val emptyA = 1f - clamp01(g * 2.2f)
    if (emptyA > 0f) drawEmptyMeal(kit, CardTop, NutricionMeal.Name, "Sin registrar", emptyA)
    val loggedA = clamp01((g - 0.30f) / 0.55f)
    if (loggedA > 0f) drawLoggedMeal(h, kit, CardTop, loggedA)

    // Almuerzo: aún por registrar, tenue.
    drawEmptyMeal(kit, CardTop + cardH + 8f, "Almuerzo", "Sin registrar", 0.5f, drawBox = true)
}

private fun DrawScope.drawEmptyMeal(kit: NutricionKit, top: Float, title: String, sub: String, alpha: Float, drawBox: Boolean = false) {
    val m = NutricionLayout.Margin
    if (drawBox) {
        rr(m, top, NutricionLayout.Width - 2f * m, CardPlaceholderH, 14f, Color.White.a(0.04f * alpha * 2f))
        rrLine(m, top, NutricionLayout.Width - 2f * m, CardPlaceholderH, 14f, Color.White.a(0.07f * alpha * 2f), 1f)
    }
    val cx = m + 22f
    val cy = top + CardPlaceholderH / 2f
    drawCircle(Color.White.a(0.07f * alpha), 11f, Offset(cx, cy))
    drawLine(NC.ink.a(0.55f * alpha), Offset(cx - 4f, cy), Offset(cx + 4f, cy), 1.4f, StrokeCap.Round)
    drawLine(NC.ink.a(0.55f * alpha), Offset(cx, cy - 4f), Offset(cx, cy + 4f), 1.4f, StrokeCap.Round)
    nTextMid(kit, NT.CardTitle, title, m + 42f, top + 16f, NC.ink.a(alpha))
    nTextMid(kit, NT.CardMeta, sub, m + 42f, top + 31f, NC.muted.a(alpha))
}

private fun DrawScope.drawLoggedMeal(h: NutricionHomeFrame, kit: NutricionKit, top: Float, alpha: Float) {
    val m = NutricionLayout.Margin
    val innerX = m + 12f
    // Encabezado: sello, nombre, cuántos alimentos y el total.
    val badge = h.badge
    val bx = innerX + 10f
    val by = top + 19f
    drawCircle(NC.ok.a(alpha), 9f * badge.coerceAtLeast(0f), Offset(bx, by))
    if (badge > 0.35f) drawCheck(bx, by, 9f * badge.coerceAtMost(1.1f), NC.onInk.a(alpha), 1.7f)
    nTextMid(kit, NT.CardTitle, NutricionMeal.Name, innerX + 28f, top + 15f, NC.ink.a(alpha))
    nTextMid(kit, NT.CardMeta, "Registrado · 4 alimentos", innerX + 28f, top + 29f, NC.ok.a(0.85f * alpha))
    val right = NutricionLayout.Width - m - 12f
    nTextMid(kit, NT.RowUnit, "kcal", right, top + 24f, NC.muted.a(alpha), 1f)
    nTextMid(kit, NT.CardKcal, NutricionMeal.totalKcal.toString(), right - kit.width(NT.RowUnit, "kcal") - 3f, top + 21f, NC.ink.a(alpha), 1f)

    // Los cuatro alimentos, cada uno con su dibujo y su cifra.
    val colW = (NutricionLayout.Width - 2f * m - 24f) / 4f
    for (i in 0..3) {
        val food = NutricionMeal.foods[i]
        val pop = h.cardFoods[i].coerceAtLeast(0f)
        val a = alpha * clamp01(pop * 1.6f)
        val cx = innerX + colW * (i + .5f)
        val cy = top + 58f
        rr(cx - 14f, cy - 14f, 28f, 28f, 9f, Color.White.a(0.07f * a))
        drawFoodIcon(food.id, cx, cy, 20f * pop.coerceAtMost(1.15f), a)
        nTextMid(kit, NT.MiniName, food.name.substringBefore(' '), cx, top + 82f, NC.muted.a(a), 0.5f)
        nTextMid(kit, NT.MiniKcal, "${food.kcal}", cx, top + 93f, NC.ink.a(a), 0.5f)
    }
}

// ── botón «Registrar comida»

private fun DrawScope.drawFab(h: NutricionHomeFrame, kit: NutricionKit) {
    val l = NutricionLayout
    val press = h.fabPress
    val k = 1f - 0.05f * press
    withTransform({ scale(k, k, Offset(l.FabCx, l.FabCy)) }) {
        val x = l.Width - l.FabRight - l.FabW
        // Un fondo oscuro debajo del cristal para que el botón no se confunda con la tarjeta.
        rr(x, l.FabTop, l.FabW, l.FabH, l.FabH / 2f, NC.screen)
        rr(x, l.FabTop, l.FabW, l.FabH, l.FabH / 2f, Color.White.a(0.12f + 0.12f * press))
        rrLine(x, l.FabTop, l.FabW, l.FabH, l.FabH / 2f, Color.White.a(0.22f), 1f)
        val cx = x + 10f + 12f
        drawCircle(Color.White.a(0.14f), 12f, Offset(cx, l.FabCy))
        drawLine(NC.ink, Offset(cx - 4.5f, l.FabCy), Offset(cx + 4.5f, l.FabCy), 1.8f, StrokeCap.Round)
        drawLine(NC.ink, Offset(cx, l.FabCy - 4.5f), Offset(cx, l.FabCy + 4.5f), 1.8f, StrokeCap.Round)
        nTextMid(kit, NT.Fab, "Registrar comida", x + 10f + 24f + 8f, l.FabCy, NC.ink)
    }
}
