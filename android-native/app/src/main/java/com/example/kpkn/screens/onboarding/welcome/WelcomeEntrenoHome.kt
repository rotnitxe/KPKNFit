package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.kpkn.screens.onboarding.design.eOutCubic

/*
 * Pantalla de inicio de la pestaña Entreno (la que se ve al arrancar) y la barra de estado simulada del teléfono.
 * Se lee en un segundo: el título, la semana, UNA tarjeta clara que invita a tocar («Nueva sesión») y las
 * sesiones que ya existen, más tenues. Abajo, la barra de pestañas de la app con «Entreno» marcada.
 */

private val DayLetters = arrayOf("L", "M", "X", "J", "V", "S", "D")

private class EntrenoSessionCard(val name: String, val detail: String, val done: Boolean, val c1: Color, val c2: Color)

private val EntrenoSessionCards = listOf(
    EntrenoSessionCard("Pierna y glúteo", "Lun · 6 ejercicios · 58 min", true, Color(0xFFE08E45), Color(0xFF8D3D2E)),
    EntrenoSessionCard("Hombro y brazo", "Mar · 5 ejercicios · 46 min", true, Color(0xFF5FA8D3), Color(0xFF1B4965)),
    EntrenoSessionCard("Full body ligero", "Sáb · 5 ejercicios · 40 min", false, Color(0xFF4ADE80), Color(0xFF2D6A4F)),
)

private val CoverBrushes: List<Brush> by lazy {
    EntrenoSessionCards.mapIndexed { i, s ->
        val top = EntrenoGeo.sessionTop[i]
        Brush.linearGradient(
            colors = listOf(s.c1.copy(alpha = 0.78f), s.c2.copy(alpha = 0.9f)),
            start = Offset(26f, top + 10f),
            end = Offset(64f, top + 48f),
        )
    }
}

/** Hora, señal, wifi y batería. Siempre visible, también durante el fundido del bucle. */
internal fun entrenoDrawStatusBar(p: EntrenoPen) {
    p.text("9:41", EFont.B12, 24f, 19f, ECol.ink, alpha = 0.95f)
    for (i in 0..3) {
        val h = 3.2f + i * 2.3f
        val x = 218f + i * 3.9f
        p.fill(EntrenoRect(x, 19.5f - h, x + 2.4f, 19.5f), 0.8f, ECol.ink, if (i == 3) 0.4f else 0.92f)
    }
    for (k in 0..2) p.arc(238.5f, 20f, 3.2f + 2.9f * k, 1.4f, -135f, 90f, ECol.ink, 0.9f)
    p.circle(238.5f, 19.4f, 1.1f, ECol.ink, 0.9f)
    p.border(EntrenoRect(256f, 9.5f, 277f, 19.5f), 3f, 1f, ECol.ink, 0.5f)
    p.fill(EntrenoRect(258f, 11.5f, 271.5f, 17.5f), 1.6f, ECol.ink, 0.92f)
    p.fill(EntrenoRect(277.8f, 12.6f, 279.4f, 16.4f), 0.8f, ECol.ink, 0.5f)
}

/** Raya del gesto de inicio del sistema. */
internal fun entrenoDrawHomeBar(p: EntrenoPen) {
    p.fill(EntrenoRect(102f, 607f, 198f, 611f), 2f, ECol.ink, 0.5f)
}

internal fun entrenoDrawHome(p: EntrenoPen, f: EntrenoFrame) {
    val t = f.t
    p.group(f.homeAlpha) {
        // Título, semana y avatar
        val e0 = entrenoHomeEnter(t, 0)
        p.group(e0) {
            p.shifted(0f, (1f - e0) * 8f) {
                p.text("Entreno", EFont.Hero, 20f, 68f, ECol.ink)
                p.text("Semana 3 · jueves", EFont.M12, 20f, 88f, ECol.muted)
                p.circle(268f, 58f, 16f, ECol.panel)
                p.ring(268f, 58f, 16f, 1f, ECol.line)
                p.text("KP", EFont.B115, 268f, 62f, ECol.ink, EAlign.CENTER, 0.9f)
            }
        }
        drawWeek(p, t)
        drawHeroCard(p, f)
        // Sesiones existentes
        val eLabel = entrenoHomeEnter(t, 3)
        p.group(eLabel) { p.text("TUS SESIONES", EFont.Cap9, 20f, 252f, ECol.muted, alpha = 0.9f) }
        for (i in EntrenoSessionCards.indices) drawSessionCard(p, i, entrenoHomeEnter(t, 3 + i))
        drawNav(p, entrenoHomeEnter(t, 6))
    }
}

private fun drawWeek(p: EntrenoPen, t: Float) {
    val e = entrenoHomeEnter(t, 1)
    val icons = p.kit.icons
    p.group(e) {
        p.shifted(0f, (1f - e) * 8f) {
            for (i in 0..6) {
                val cx = 20f + 260f / 7f * (i + 0.5f)
                val today = i == 3
                p.text(DayLetters[i], EFont.M10, cx, 119f, if (today) ECol.ink else ECol.muted, EAlign.CENTER)
                val cy = 137f
                when {
                    i <= 1 -> {
                        p.circle(cx, cy, 11f, ECol.ok, 0.15f)
                        p.ring(cx, cy, 10.4f, 1.2f, ECol.ok, 0.7f)
                        p.icon(icons.check, cx, cy, 12f, ECol.ok, 2.6f)
                    }
                    today -> {
                        p.ring(cx, cy, 10.4f, 1.6f, ECol.ink)
                        p.circle(cx, cy, 3.2f, ECol.ink)
                    }
                    else -> p.ring(cx, cy, 10.4f, 1.2f, ECol.muted, 0.32f)
                }
            }
        }
    }
}

private fun drawHeroCard(p: EntrenoPen, f: EntrenoFrame) {
    val e = entrenoHomeEnter(f.t, 2)
    val icons = p.kit.icons
    p.group(e) {
        p.shifted(0f, (1f - e) * 10f) {
            val r = EntrenoGeo.homeCard
            p.scaled(1f - 0.03f * f.tarjetaPress, r.cx, r.cy) {
                // un halo que respira invita al toque
                p.fill(r.inflated(3f), 23f, ECol.ink, 0.04f + 0.04f * f.latido)
                p.shadow(r, 20f, 9f, 1f)
                p.fill(r, 20f, ECol.ink)
                if (f.tarjetaOnda >= 0f) {
                    p.clipRound(r, 20f) {
                        val k = eOutCubic(f.tarjetaOnda)
                        p.circle(EntrenoGeo.homeTapX, EntrenoGeo.homeTapY, 8f + 190f * k, ECol.onInk, 0.12f * (1f - f.tarjetaOnda))
                    }
                }
                p.circle(r.l + 34f, r.cy, 17f, ECol.onInk)
                p.icon(icons.plus, r.l + 34f, r.cy, 16f, ECol.ink, 2.6f)
                p.text("Nueva sesión", EFont.Title15, r.l + 62f, r.cy - 3f, ECol.onInk)
                p.text("Elige ejercicios y empieza", EFont.M11, r.l + 62f, r.cy + 14f, ECol.onInk, alpha = 0.62f)
                p.icon(icons.chevR, r.r - 26f, r.cy, 16f, ECol.onInk, 2.4f, 0.7f)
            }
        }
    }
}

private fun drawSessionCard(p: EntrenoPen, i: Int, e: Float) {
    val s = EntrenoSessionCards[i]
    val top = EntrenoGeo.sessionTop[i]
    val icons = p.kit.icons
    p.group(e) {
        p.shifted(0f, (1f - e) * 10f) {
            val r = EntrenoRect(16f, top, 284f, top + EntrenoGeo.sessionH)
            p.fill(r, 18f, ECol.panel)
            p.border(r, 18f, 1f, ECol.line)
            val tile = EntrenoRect(26f, top + 10f, 64f, top + 48f)
            p.fillBrush(tile, 12f, CoverBrushes[i])
            p.icon(icons.barbell, tile.cx, tile.cy, 18f, ECol.white, 1.9f, 0.85f)
            p.text(s.name, EFont.B13, 76f, top + 26f, ECol.ink)
            p.text(s.detail, EFont.M105, 76f, top + 43f, ECol.muted)
            if (s.done) {
                p.circle(262f, r.cy, 10f, ECol.ok, 0.15f)
                p.icon(icons.check, 262f, r.cy, 12f, ECol.ok, 2.6f)
            } else {
                p.icon(icons.chevR, 264f, r.cy, 14f, ECol.muted, 2.2f, 0.7f)
            }
        }
    }
}

private val NavLabels = arrayOf("Inicio", "Entreno", "Nutrición", "Cuerpo")

private fun drawNav(p: EntrenoPen, e: Float) {
    val r = EntrenoGeo.nav
    val icons = p.kit.icons
    p.group(e) {
        p.shifted(0f, (1f - e) * 14f) {
            p.shadow(r, 28f, 8f, 1f)
            p.fill(r, 28f, Color(0xFF16171A), 0.96f)
            p.border(r, 28f, 1f, ECol.white, 0.12f)
            for (i in 0..3) {
                val cx = r.l + 34f + 68f * i
                val sel = i == 1
                val col = if (sel) ECol.ink else ECol.muted
                val cy = r.t + 21f
                if (sel) p.fill(EntrenoRect(cx - 27f, r.t + 5f, cx + 27f, r.b - 5f), 18f, ECol.white, 0.07f)
                when (i) {
                    0 -> p.iconSolid(icons.home, cx, cy, 21f, col, 1.4f)
                    1 -> drawDumbbell(p, cx, cy, col)
                    2 -> {
                        p.iconSolid(icons.apple, cx, cy, 21f, col, 0.6f)
                        p.icon(icons.appleStem, cx, cy, 21f, col, 1.7f)
                        p.iconFill(icons.appleLeaf, cx, cy, 21f, col)
                    }
                    else -> {
                        p.iconSolid(icons.arm, cx, cy, 21f, col, 0.8f)
                        p.icon(icons.armLine1, cx, cy, 21f, Color(0xFF141414), 1.7f)
                        p.icon(icons.armLine2, cx, cy, 21f, Color(0xFF141414), 1.7f)
                    }
                }
                p.text(NavLabels[i], EFont.M10, cx, r.b - 9f, col, EAlign.CENTER)
            }
        }
    }
}

/** La mancuerna de la pestaña Entreno (misma geometría que `DumbbellIcon` de la app). */
private fun drawDumbbell(p: EntrenoPen, cx: Float, cy: Float, color: Color) {
    val k = 21f / 24f
    val ox = cx - 10.5f
    p.line(ox + 4f * k, cy, ox + 20f * k, cy, 2f * k, color)
    p.fill(EntrenoRect(ox, cy - 6f * k, ox + 4f * k, cy + 6f * k), 1f * k, color)
    p.fill(EntrenoRect(ox + 20f * k, cy - 6f * k, ox + 24f * k, cy + 6f * k), 1f * k, color)
}
