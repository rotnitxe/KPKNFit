package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.ui.graphics.Color
import com.example.kpkn.screens.onboarding.design.clamp01
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.mix
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.cos
import kotlin.math.sin

/*
 * Sesión en vivo: cabecera con el cronómetro, progreso de la sesión, la tarjeta del ejercicio con sus series
 * (se rellenan, se marcan, se ponen verdes), el aviso del siguiente ejercicio y el dock de descanso.
 *
 * Reparto de color con un solo significado cada uno: crema = lo activo, verde = hecho, dorado = récord, azul =
 * descanso.
 */

private val SetNumbers = arrayOf("1", "2", "3", "4")
private val Percents = Array(101) { "$it %" }
private val NextSet = arrayOf("", "Siguiente: serie 2 de 4", "", "Siguiente: serie 4 de 4")

/** Ángulos (en radianes) de las seis chispas de la confirmación. */
private val SparkCos = FloatArray(6) { cos(Math.toRadians(15.0 + 60.0 * it)).toFloat() }
private val SparkSin = FloatArray(6) { sin(Math.toRadians(15.0 + 60.0 * it)).toFloat() }

private fun enterK(s: EntrenoSesion, k: Int): Float = eOutCubic(seg(s.enter, 0.06f * k, 0.06f * k + 0.5f))

internal fun entrenoDrawLive(p: EntrenoPen, f: EntrenoFrame) {
    val s = f.sesion
    if (s.baseAlpha <= 0.002f) return
    p.group(s.baseAlpha) {
        drawLiveHeader(p, f)
        drawLiveProgress(p, f)
        drawLiveChips(p, f)
        drawLiveCard(p, f)
        drawNextOrCoach(p, f)
        drawRestDock(p, f)
    }
}

private fun drawLiveHeader(p: EntrenoPen, f: EntrenoFrame) {
    val s = f.sesion
    val e = enterK(s, 0)
    p.group(e) {
        p.shifted(0f, (1f - e) * 10f) {
            p.text("Pecho y espalda", EFont.Title14, 20f, EntrenoGeo.liveTitleBase, ECol.ink)
            val tp = EntrenoGeo.timerPill
            p.fill(tp, 14f, ECol.white, 0.06f)
            p.border(tp, 14f, 1f, ECol.white, 0.1f)
            // punto de «grabando» que respira
            p.circle(tp.l + 14f, tp.cy, 3f + 3.6f * f.latido, ECol.ok, 0.16f * (1f - f.latido))
            p.circle(tp.l + 14f, tp.cy, 3f, ECol.ok, 0.65f + 0.35f * f.latido)
            p.roll(s.crono, EFont.B13, tp.r - 12f, tp.cy + 4.6f, ECol.ink, EAlign.END, tabular = true)
        }
    }
}

private fun drawLiveProgress(p: EntrenoPen, f: EntrenoFrame) {
    val s = f.sesion
    val e = enterK(s, 1)
    p.group(e) {
        p.shifted(0f, (1f - e) * 8f) {
            val base = EntrenoGeo.progLabelBase
            p.roll(s.hechas, EFont.B115, 20f, base, ECol.ink, EAlign.START, tabular = true)
            val wn = p.width("0", EFont.B115)
            p.text(" de 8 series", EFont.M115, 20f + wn, base, ECol.muted)
            val pct = (s.progreso / 8f * 100f + 0.5f).toInt().coerceIn(0, 100)
            p.text(Percents[pct], EFont.M115, EntrenoGeo.segRight, base, ECol.muted, EAlign.END)
            val gap = 3f
            val segW = (EntrenoGeo.segRight - EntrenoGeo.segLeft - gap * 7f) / 8f
            for (i in 0..7) {
                val x = EntrenoGeo.segLeft + i * (segW + gap)
                p.fill(EntrenoRect(x, EntrenoGeo.segTop, x + segW, EntrenoGeo.segTop + EntrenoGeo.segH), 2.5f, ECol.white, 0.1f)
                val fp = clamp01(s.progreso - i)
                if (fp > 0.005f) p.fill(EntrenoRect(x, EntrenoGeo.segTop, x + segW * fp, EntrenoGeo.segTop + EntrenoGeo.segH), 2.5f, ECol.ok)
            }
        }
    }
}

private fun drawLiveChips(p: EntrenoPen, f: EntrenoFrame) {
    val e = enterK(f.sesion, 1)
    p.group(e) {
        p.shifted(0f, (1f - e) * 8f) {
            val top = EntrenoGeo.chipsTop
            val w1 = p.width("Press banca", EFont.B115) + 30f
            val c1 = EntrenoRect(20f, top, 20f + w1, top + EntrenoGeo.chipsH)
            p.fill(c1, 13f, ECol.white, 0.09f)
            p.border(c1, 13f, 1f, ECol.ink, 0.55f)
            p.circle(c1.l + 12f, c1.cy, 3f, ECol.ink)
            p.text("Press banca", EFont.B115, c1.l + 21f, c1.cy + 4.2f, ECol.ink)
            val w2 = p.width("Remo con barra", EFont.M115) + 24f
            val c2 = EntrenoRect(c1.r + 8f, top, c1.r + 8f + w2, top + EntrenoGeo.chipsH)
            p.border(c2, 13f, 1f, ECol.line)
            p.text("Remo con barra", EFont.M115, c2.l + 12f, c2.cy + 4.2f, ECol.muted)
        }
    }
}

private fun drawLiveCard(p: EntrenoPen, f: EntrenoFrame) {
    val s = f.sesion
    val e = enterK(s, 2)
    val icons = p.kit.icons
    p.group(e) {
        p.shifted(0f, (1f - e) * 12f) {
            val r = EntrenoGeo.liveCard
            p.shadow(r, 26f, 12f, 1f)
            p.fill(r, 26f, ECol.sheet)
            p.border(r, 26f, 1f, ECol.white, 0.07f)
            val th = EntrenoRect(28f, 166f, 72f, 210f)
            p.fill(th, 14f, ECol.white, 0.06f)
            p.icon(icons.barbell, th.cx, th.cy, 26f, ECol.ink, 1.7f, 0.92f)
            p.text("Press banca", EFont.Title17, 84f, 187f, ECol.ink)
            p.text("Pecho · Barra · objetivo 4 × 8", EFont.M105, 84f, 203f, ECol.muted)
            p.text("SERIE", EFont.Cap9, EntrenoGeo.badgeX, 237f, ECol.muted, EAlign.CENTER, 0.85f)
            p.text("KG", EFont.Cap9, EntrenoGeo.weightX, 237f, ECol.muted, EAlign.CENTER, 0.85f)
            p.text("REPS", EFont.Cap9, EntrenoGeo.repsX, 237f, ECol.muted, EAlign.CENTER, 0.85f)
            for (i in 0..3) drawSetRow(p, f, i)
        }
    }
}

private fun drawSetRow(p: EntrenoPen, f: EntrenoFrame, i: Int) {
    val sr = f.sesion.series[i]
    val r = EntrenoGeo.liveRow(i)
    val icons = p.kit.icons
    val en = sr.enter
    val doneT = sr.hecha
    val cy = r.cy
    p.group(en) {
        p.shifted(0f, (1f - en) * 8f) {
            // fondo de la fila: neutro → verde al registrarse
            p.fill(r, 14f, mix(Color(0x0AFFFFFF), ECol.ok.copy(alpha = 0.12f), doneT))
            if (doneT > 0.01f) p.border(r, 14f, 1f, ECol.ok, 0.32f * doneT)
            if (sr.onda in 0f..1f) p.fill(r, 14f, ECol.ok, 0.18f * (1f - eOutCubic(clamp01(sr.onda * 1.6f))))
            if (sr.touch > 0.01f) p.fill(r, 14f, ECol.white, 0.07f * sr.touch)
            val pulse = if (i == 3) 0.65f + 0.35f * f.latido else 1f
            if (sr.activa > 0.01f) p.border(r, 14f, 1.4f, ECol.ink, 0.6f * sr.activa * pulse)

            // número de serie
            val act = sr.activa
            val badgeFill = mix(mix(Color(0x12FFFFFF), ECol.ink, act), ECol.ok, doneT)
            p.circle(EntrenoGeo.badgeX, cy, 12f, badgeFill)
            p.text(SetNumbers[i], EFont.B115, EntrenoGeo.badgeX, cy + 4.2f, mix(mix(ECol.muted, ECol.onInk, act), ECol.onOk, doneT), EAlign.CENTER)

            // peso y repeticiones: guion mientras están vacíos, número que rueda al rellenarse
            drawValue(p, sr.peso, EntrenoGeo.weightX, cy)
            drawValue(p, sr.reps, EntrenoGeo.repsX, cy)

            // objetivo (se apaga al registrar) e insignia de récord
            p.text("obj. 60 × 8", EFont.M10, EntrenoGeo.zoneL + 4f, cy + 3.6f, ECol.muted, EAlign.START, 0.55f * (1f - doneT))
            if (sr.record > 0.01f) drawRecordBadge(p, sr.record, (EntrenoGeo.zoneL + EntrenoGeo.zoneR) / 2f, cy)

            // botón «visto»
            val cx = EntrenoGeo.checkX
            p.scaled(sr.checkScale, cx, cy) {
                p.circle(cx, cy, EntrenoGeo.checkR, ECol.ok, doneT)
                p.ring(cx, cy, EntrenoGeo.checkR - 0.7f, 1.4f, mix(ECol.white, ECol.ink, act), (1f - doneT) * (0.2f + 0.42f * act * pulse))
                p.icon(icons.check, cx, cy, 16f, mix(Color(0x38FFFFFF), ECol.onOk, doneT), 2.7f)
            }
            if (sr.onda in 0f..1f) drawConfirmation(p, cx, cy, sr.onda)
        }
    }
}

private fun drawValue(p: EntrenoPen, v: EntrenoRoll, cx: Float, cy: Float) {
    val dashA = if (v.to.isEmpty()) 0.5f else 0.5f * (1f - seg(v.p, 0f, 0.3f))
    if (dashA > 0.01f) p.text("–", EFont.M15, cx, cy + 5f, ECol.muted, EAlign.CENTER, dashA)
    if (v.to.isNotEmpty() && v.p > 0f) p.roll(v, EFont.Num17, cx, cy + 6.2f, ECol.ink, EAlign.CENTER)
}

/** Anillo que se expande y seis chispas: la confirmación «se oye» sin sonar. */
private fun drawConfirmation(p: EntrenoPen, cx: Float, cy: Float, k: Float) {
    val e = eOutCubic(k)
    p.ring(cx, cy, EntrenoGeo.checkR + 22f * e, 3f * (1f - e) + 0.4f, ECol.ok, 0.75f * (1f - k))
    val k2 = seg(k, 0.14f, 1f)
    if (k2 > 0f && k2 < 1f) p.ring(cx, cy, EntrenoGeo.checkR + 13f * eOutCubic(k2), 1.5f * (1f - k2) + 0.3f, ECol.ok, 0.4f * (1f - k2))
    val dist = 21f + 15f * e
    for (j in 0..5) p.circle(cx + SparkCos[j] * dist, cy + SparkSin[j] * dist, 1.9f * (1f - k), ECol.ok, 0.9f * (1f - k))
}

/** «¡Récord!»: una píldora dorada que entra con un pequeño rebote y un destello. */
private fun drawRecordBadge(p: EntrenoPen, rec: Float, cx: Float, cy: Float) {
    val icons = p.kit.icons
    val k = rec.coerceAtLeast(0f)
    p.group(clamp01(k * 2f)) {
        p.scaled(k, cx, cy) {
            p.rotated((1f - clamp01(k)) * -12f, cx, cy) {
                val pill = EntrenoRect(cx - 33f, cy - 10.5f, cx + 33f, cy + 10.5f)
                p.fill(pill, 10.5f, ECol.gold, 0.16f)
                p.border(pill, 10.5f, 1f, ECol.gold, 0.85f)
                p.iconFill(icons.star, cx - 21.5f, cy - 0.3f, 10.5f, ECol.gold)
                p.text("¡Récord!", EFont.B10, cx - 14f, cy + 3.6f, ECol.gold)
            }
        }
        // destello al entrar
        val sp = seg(rec, 0.1f, 1f)
        if (sp > 0f && sp < 1f) {
            val a = 1f - sp
            p.iconFill(icons.sparkle, cx + 30f, cy - 12f, 8f * (0.5f + sp * 0.8f), ECol.gold, 0.9f * a)
            p.iconFill(icons.sparkle, cx - 31f, cy + 11f, 6f * (0.5f + sp * 0.8f), ECol.gold, 0.7f * a)
        }
    }
}

private fun drawNextOrCoach(p: EntrenoPen, f: EntrenoFrame) {
    val s = f.sesion
    val e = enterK(s, 4)
    val icons = p.kit.icons
    val r = EntrenoGeo.preview
    p.group(e) {
        p.shifted(0f, (1f - e) * 10f) {
            // siguiente ejercicio
            p.group(1f - s.coach) {
                p.fill(r, 14f, ECol.white, 0.03f)
                p.border(r, 14f, 1f, ECol.line)
                p.text("SIGUIENTE", EFont.Cap9, r.l + 14f, r.t + 16f, ECol.muted, alpha = 0.9f)
                p.text("Remo con barra · 4 × 8 · 50 kg", EFont.M12, r.l + 14f, r.t + 32f, ECol.ink, alpha = 0.85f)
                p.icon(icons.chevR, r.r - 18f, r.cy, 14f, ECol.muted, 2.2f, 0.7f)
            }
            // aviso del asistente (respiro final)
            if (s.coach > 0.01f) {
                p.group(s.coach) {
                    p.shifted(0f, (1f - s.coach) * 6f) {
                        p.fill(r, 14f, ECol.gold, 0.07f)
                        p.border(r, 14f, 1f, ECol.gold, 0.32f)
                        p.iconFill(icons.sparkle, r.l + 22f, r.cy, 15f, ECol.gold)
                        p.text("Nuevo récord a 8 reps", EFont.B12, r.l + 40f, r.t + 18f, ECol.ink)
                        p.text("62,5 kg es tu mejor marca en Press banca", EFont.M105, r.l + 40f, r.t + 33f, ECol.muted)
                    }
                }
            }
        }
    }
}

private fun drawRestDock(p: EntrenoPen, f: EntrenoFrame) {
    val d = f.sesion.descanso
    if (d.visible <= 0.004f) return
    val icons = p.kit.icons
    val r = EntrenoGeo.dock
    p.group(clamp01(d.visible * 1.6f)) {
        p.shifted(0f, (1f - d.visible) * 70f) {
            p.shadow(r, 20f, 10f, 1f)
            p.fill(r, 20f, ECol.dock)
            p.border(r, 20f, 1f, ECol.rest, 0.35f)
            // anillo con la cuenta atrás
            val cx = r.l + 31f
            val cy = r.cy
            p.ring(cx, cy, 18f, 3.2f, ECol.rest, 0.18f)
            p.arc(cx, cy, 18f, 3.2f, -90f, 360f * d.fraccion, ECol.rest)
            p.text(entrenoRestClock(d.restante), EFont.B105, cx, cy + 3.9f, ECol.ink, EAlign.CENTER)
            // etiquetas
            val tx = r.l + 62f
            p.text("Descansa 1:30", EFont.B13, tx, cy - 3f, ECol.ink)
            p.text(NextSet[d.serie], EFont.M105, tx, cy + 13f, ECol.muted)
            // time-lapse: dos flechitas que parpadean mientras corre
            if (d.lapso > 0.02f) {
                val w = p.width("Descansa 1:30", EFont.B13)
                val flick = 0.6f + 0.4f * sin(f.t * 30f)
                p.iconSolid(icons.ffwd, tx + w + 11f, cy - 7.2f, 12f, ECol.rest, 1f, d.lapso * flick)
            }
            // «Saltar»
            val b = EntrenoGeo.saltar
            p.scaled(1f - 0.05f * d.saltarPress, b.cx, b.cy) {
                p.fill(b, 16f, ECol.white, 0.10f + 0.08f * d.saltarPress)
                p.border(b, 16f, 1f, ECol.white, 0.22f)
                p.iconSolid(icons.skipTri, b.l + 15f, b.cy, 12f, ECol.ink, 1.2f, 0.95f)
                p.icon(icons.skipBar, b.l + 15f, b.cy, 12f, ECol.ink, 2.2f, 0.95f)
                p.text("Saltar", EFont.B12, b.l + 26f, b.cy + 4.3f, ECol.ink)
            }
        }
    }
}
