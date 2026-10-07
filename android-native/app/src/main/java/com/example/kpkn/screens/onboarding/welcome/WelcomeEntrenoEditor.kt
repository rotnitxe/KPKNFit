package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.kpkn.screens.onboarding.design.clamp01
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.mix
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Hoja del editor de sesión («Nueva sesión») y el selector de ejercicios que sube encima.
 *
 * Editor: asa, título, campo del nombre (se teclea solo), tres contadores (ejercicios · series · min) que ruedan,
 * las filas de ejercicio con sus chips, «Añadir ejercicio» y el botón «Empezar sesión».
 * Selector: búsqueda, filtros y una lista con inercia; los ejercicios elegidos se marcan en verde y el botón de
 * abajo cuenta cuántos se añaden.
 */

private class EntrenoRowSpec(val name: String, val chips: Array<String>)

private val EditorRows = arrayOf(
    EntrenoRowSpec("Press banca", arrayOf("4 × 8", "60 kg", "90 s")),
    EntrenoRowSpec("Remo con barra", arrayOf("4 × 8", "50 kg", "90 s")),
)

private val FilterChips = arrayOf("Todos", "Pecho", "Espalda", "Hombro")

internal fun entrenoDrawEditor(p: EntrenoPen, f: EntrenoFrame) {
    val e = f.editor
    if (e.hojaY >= EntrenoGeo.H) return
    p.fillScreen(ECol.black, e.telon, EntrenoGeo.statusH)
    p.sheet(e.hojaY, 26f, ECol.sheet)
    p.shifted(0f, e.hojaY) {
        val icons = p.kit.icons
        // asa y título
        p.fill(EntrenoRect(133f, 8f, 167f, 12f), 2f, ECol.white, 0.22f)
        p.text("Nueva sesión", EFont.B13, 150f, 36f, ECol.ink, EAlign.CENTER, 0.92f)

        // campo del nombre
        val nf = EntrenoGeo.nameFieldL
        p.fill(nf, 16f, ECol.white, 0.05f)
        p.border(nf, 16f, 1f, ECol.line)
        if (e.foco > 0.01f) p.border(nf, 16f, 1.5f, ECol.ink, 0.78f * e.foco)
        p.text("NOMBRE", EFont.Cap9, nf.l + 16f, nf.t + 20f, ECol.muted, alpha = 0.9f)
        if (e.nombre.isEmpty()) {
            p.text("Ponle un nombre", EFont.M15, nf.l + 16f, nf.t + 43f, ECol.muted, alpha = 0.55f)
        } else {
            p.text(e.nombre, EFont.Name, nf.l + 16f, nf.t + 44f, ECol.ink)
        }
        if (e.cursor) {
            val cx = nf.l + 16f + p.width(e.nombre, EFont.Name) + 2f
            p.fill(EntrenoRect(cx, nf.t + 28f, cx + 2f, nf.t + 48f), 1f, ECol.ink)
        }

        // contadores
        val sc = EntrenoGeo.statsCardL
        p.fill(sc, 18f, ECol.white, 0.035f)
        p.border(sc, 18f, 1f, ECol.line)
        val w3 = sc.w / 3f
        val labels = arrayOf("ejercicios", "series", "min")
        for (i in 0..2) {
            val cx = sc.l + w3 * (i + 0.5f)
            val roll = when (i) {
                0 -> e.ejercicios
                1 -> e.series
                else -> e.minutos
            }
            if (i == 2) {
                val a = if (roll.from == "0" && roll.to != "0") seg(roll.p, 0f, 0.5f) else if (roll.to == "0") 0f else 1f
                p.text("≈", EFont.Title16, cx - p.width(roll.to, EFont.Big24) / 2f - 3f, sc.t + 36f, ECol.muted, EAlign.END, a)
            }
            p.roll(roll, EFont.Big24, cx, sc.t + 37f, ECol.ink, EAlign.CENTER)
            p.text(labels[i], EFont.M105, cx, sc.t + 53f, ECol.muted, EAlign.CENTER)
            if (i > 0) p.line(sc.l + w3 * i, sc.t + 12f, sc.l + w3 * i, sc.b - 12f, 1f, ECol.white, 0.08f)
        }

        // ejercicios
        if (e.vacio > 0.01f) {
            val r = EntrenoRect(16f, EntrenoGeo.rowTopL(0), 284f, EntrenoGeo.rowTopL(0) + EntrenoGeo.rowH)
            p.dashed(r, 18f, 1.2f, ECol.ink, 0.22f * e.vacio)
            p.icon(icons.barbell, r.cx, r.cy - 9f, 22f, ECol.ink, 1.7f, 0.35f * e.vacio)
            p.text("Aún no hay ejercicios", EFont.M12, r.cx, r.cy + 17f, ECol.muted, EAlign.CENTER, 0.75f * e.vacio)
        }
        drawEditorRow(p, e.fila1, 0)
        drawEditorRow(p, e.fila2, 1)

        // «Añadir ejercicio»
        val addTop = EntrenoGeo.addTopL(e.filas)
        val add = EntrenoRect(16f, addTop, 284f, addTop + EntrenoGeo.addH)
        p.scaled(1f - 0.025f * e.addPress, add.cx, add.cy) {
            p.fill(add, 14f, ECol.white, 0.02f + 0.08f * e.addPress)
            p.dashed(add, 14f, 1.3f, ECol.ink, 0.38f)
            val tw = p.width("Añadir ejercicio", EFont.B13)
            val x0 = add.cx - (18f + tw) / 2f
            p.icon(icons.plus, x0 + 7f, add.cy, 15f, ECol.ink, 2.3f)
            p.text("Añadir ejercicio", EFont.B13, x0 + 22f, add.cy + 4.6f, ECol.ink, alpha = 0.94f)
        }

        // «Empezar sesión»
        val cta = EntrenoRect(EntrenoGeo.cta.l, EntrenoGeo.cta.t - EntrenoGeo.sheetRest, EntrenoGeo.cta.r, EntrenoGeo.cta.b - EntrenoGeo.sheetRest)
        drawStartButton(p, cta, e.ctaOn, e.ctaPress, e.ctaPulse)
    }
}

private fun drawEditorRow(p: EntrenoPen, e: EntrenoFilaEditor, index: Int) {
    if (e.enter <= 0.004f) return
    val spec = EditorRows[index]
    val icons = p.kit.icons
    val top = EntrenoGeo.rowTopL(index)
    val en = e.enter
    p.group(clamp01(en * 1.7f)) {
        p.shifted(0f, (1f - en) * 10f) {
            p.scaled(0.96f + 0.04f * en, 150f, top + 32f) {
                val r = EntrenoRect(16f, top, 284f, top + EntrenoGeo.rowH)
                p.shadow(r, 18f, 6f, 0.7f)
                p.fill(r, 18f, ECol.panel)
                p.border(r, 18f, 1f, ECol.line)
                val th = EntrenoRect(26f, top + 12f, 66f, top + 52f)
                p.fill(th, 12f, ECol.white, 0.06f)
                p.icon(icons.barbell, th.cx, th.cy, 24f, ECol.ink, 1.8f, 0.9f)
                p.text(spec.name, EFont.B14, 78f, top + 25f, ECol.ink)
                var x = 78f
                for (k in 0..2) {
                    val cp = when (k) {
                        0 -> e.chip1
                        1 -> e.chip2
                        else -> e.chip3
                    }
                    val w = p.width(spec.chips[k], EFont.M105) + 14f
                    if (cp > 0.01f) {
                        p.scaled(cp, x + w / 2f, top + 43f) {
                            p.group(clamp01(cp * 1.6f)) {
                                val chip = EntrenoRect(x, top + 34f, x + w, top + 52f)
                                p.fill(chip, 9f, ECol.white, 0.075f)
                                p.text(spec.chips[k], EFont.M105, x + 7f, top + 46.6f, ECol.ink, alpha = 0.85f)
                            }
                        }
                    }
                    x += w + 6f
                }
                for (j in 0..2) p.line(256f, top + 25f + j * 6f, 270f, top + 25f + j * 6f, 1.6f, ECol.muted, 0.5f)
            }
        }
    }
}

/** El botón grande de abajo: apagado hasta que hay un ejercicio; al activarse cambia de color y lanza un aviso. */
private fun drawStartButton(p: EntrenoPen, r: EntrenoRect, on: Float, press: Float, pulse: Float) {
    val icons = p.kit.icons
    p.scaled(1f - 0.03f * press, r.cx, r.cy) {
        if (pulse in 0f..1f) {
            val k = eOutCubic(pulse)
            p.border(r.inflated(2f + 9f * k), 16f + 9f * k, 1.6f, ECol.ink, 0.5f * (1f - pulse))
        }
        p.fill(r, 16f, mix(Color(0x14FFFFFF), ECol.ink, on))
        val label = "Empezar sesión"
        val tw = p.width(label, EFont.B15)
        val x0 = r.cx - (22f + tw) / 2f
        p.iconSolid(icons.play, x0 + 8f, r.cy, 16f, ECol.onInk, 1.4f, on)
        p.text(label, EFont.B15, x0 + 22f, r.cy + 5.4f, mix(Color(0x99C9C5BD), ECol.onInk, on))
    }
}

// ============================================================================================ selector

internal fun entrenoDrawPicker(p: EntrenoPen, f: EntrenoFrame) {
    val s = f.selector
    if (s.y >= EntrenoGeo.H) return
    val icons = p.kit.icons
    p.fillScreen(ECol.black, s.telon, EntrenoGeo.statusH)
    p.sheet(s.y, 26f, ECol.sheetHi)
    p.shifted(0f, s.y) {
        p.fill(EntrenoRect(133f, 8f, 167f, 12f), 2f, ECol.white, 0.22f)
        p.text("Elegir ejercicios", EFont.Title17, 20f, 44f, ECol.ink)

        // búsqueda
        val search = EntrenoRect(16f, 58f, 284f, 94f)
        p.fill(search, 12f, ECol.white, 0.06f)
        p.icon(icons.search, 32f, search.cy, 15f, ECol.muted, 2.2f, 0.9f)
        p.text("Buscar ejercicio", EFont.M13, 46f, search.cy + 4.6f, ECol.muted, alpha = 0.7f)

        // filtros
        var x = 16f
        for (i in FilterChips.indices) {
            val w = p.width(FilterChips[i], EFont.B115) + 22f
            val chip = EntrenoRect(x, 104f, x + w, 128f)
            if (i == 0) {
                p.fill(chip, 12f, ECol.ink)
                p.text(FilterChips[i], EFont.B115, chip.cx, chip.cy + 4.2f, ECol.onInk, EAlign.CENTER)
            } else {
                p.border(chip, 12f, 1f, ECol.line)
                p.text(FilterChips[i], EFont.B115, chip.cx, chip.cy + 4.2f, ECol.muted, EAlign.CENTER, 0.95f)
            }
            x += w + 6f
        }

        // lista (recortada por arriba y por abajo)
        val viewTop = EntrenoGeo.pickerListTopL
        val viewBottom = EntrenoGeo.H - s.y + 4f
        p.clipRect(EntrenoRect(0f, viewTop, EntrenoGeo.W, viewBottom)) {
            val first = floor(s.scroll / EntrenoGeo.catRowH).toInt().coerceAtLeast(0)
            val last = ceil((s.scroll + (viewBottom - viewTop)) / EntrenoGeo.catRowH).toInt().coerceAtMost(EntrenoGeo.catalog.size - 1)
            for (i in first..last) drawCatalogRow(p, i, viewTop + EntrenoGeo.catRowH * i - s.scroll, s)
        }
        // difuminado arriba (bajo los filtros) y abajo (sobre el botón)
        p.ds.drawRect(
            Brush.verticalGradient(listOf(ECol.sheetHi.copy(alpha = ECol.sheetHi.alpha * p.ga), Color.Transparent), startY = viewTop, endY = viewTop + 14f),
            Offset(0f, viewTop),
            Size(EntrenoGeo.W, 14f),
        )
        val cta = EntrenoRect(EntrenoGeo.cta.l, EntrenoGeo.cta.t - EntrenoGeo.pickerRest, EntrenoGeo.cta.r, EntrenoGeo.cta.b - EntrenoGeo.pickerRest)
        if (s.cta > 0.01f) {
            val fade = clamp01(s.cta)
            p.ds.drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, ECol.sheetHi.copy(alpha = fade * p.ga)),
                    startY = cta.t - 44f,
                    endY = cta.t - 4f,
                ),
                Offset(0f, cta.t - 44f),
                Size(EntrenoGeo.W, EntrenoGeo.H - s.y - cta.t + 44f + 90f),
            )
            drawAddButton(p, cta, s)
        }
    }
}

private fun drawCatalogRow(p: EntrenoPen, i: Int, top: Float, s: EntrenoSelector) {
    val item = EntrenoGeo.catalog[i]
    val icons = p.kit.icons
    val sel = when (i) {
        EntrenoGeo.idxPressBanca -> s.sel1
        EntrenoGeo.idxRemoConBarra -> s.sel2
        else -> 0f
    }.coerceAtLeast(0f)
    val r = EntrenoRect(10f, top + 2f, 290f, top + EntrenoGeo.catRowH - 2f)
    if (sel > 0.01f) p.fill(r, 14f, ECol.ok, 0.09f * clamp01(sel))
    val th = EntrenoRect(24f, top + 10f, 60f, top + 46f)
    p.fill(th, 11f, ECol.white, 0.06f)
    p.icon(icons.barbell, th.cx, th.cy, 20f, ECol.ink, 1.8f, 0.75f)
    p.text(item.name, EFont.B13, 72f, top + 25f, ECol.ink)
    p.text(item.detail, EFont.M105, 72f, top + 41f, ECol.muted)
    val cx = 262f
    val cy = top + 28f
    if (sel > 0.01f) {
        p.scaled(0.55f + 0.45f * sel, cx, cy) {
            p.circle(cx, cy, 12f, ECol.ok)
            p.icon(icons.check, cx, cy, 14f, ECol.onOk, 2.8f)
        }
    } else {
        p.ring(cx, cy, 11.4f, 1.3f, ECol.white, 0.26f)
        p.icon(icons.plus, cx, cy, 12f, ECol.ink, 2.4f, 0.8f)
    }
    p.line(72f, top + EntrenoGeo.catRowH - 0.5f, 284f, top + EntrenoGeo.catRowH - 0.5f, 0.8f, ECol.white, 0.06f)
}

/** «Añadir 2 ejercicios»: entra al elegir el primero y su número rueda al elegir el segundo. */
private fun drawAddButton(p: EntrenoPen, r: EntrenoRect, s: EntrenoSelector) {
    val en = s.cta
    p.group(clamp01(en * 1.5f)) {
        p.shifted(0f, (1f - en) * 50f) {
            p.scaled(1f - 0.03f * s.ctaPress, r.cx, r.cy) {
                p.shadow(r, 16f, 8f, 1f)
                p.fill(r, 16f, ECol.ink)
                val a = "Añadir "
                val b = " ejercicio"
                val wa = p.width(a, EFont.B15)
                val wn = p.width("2", EFont.B15)
                val wb = p.width(b, EFont.B15)
                val ws = p.width("s", EFont.B15)
                val x0 = r.cx - (wa + wn + wb + ws) / 2f
                val base = r.cy + 5.4f
                p.text(a, EFont.B15, x0, base, ECol.onInk)
                p.roll(s.ctaCount, EFont.B15, x0 + wa + wn / 2f, base, ECol.onInk, EAlign.CENTER, tabular = true)
                p.text(b, EFont.B15, x0 + wa + wn, base, ECol.onInk)
                val plural = if (s.ctaCount.to == "2") seg(s.ctaCount.p, 0.2f, 0.9f) else 0f
                p.text("s", EFont.B15, x0 + wa + wn + wb, base, ECol.onInk, alpha = plural)
            }
        }
    }
}
