package com.example.kpkn.screens.onboarding.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La escena de Entreno es una función pura de `t`: aquí se fija QUÉ se ve en los instantes clave (el guion), sin
 * dibujar nada. Cubre el texto tecleado, las filas que existen, el desplazamiento de la lista, a dónde apunta el
 * dedo en cada toque, los valores de cada serie, el descanso, el cuadro congelado del movimiento reducido y la
 * continuidad del bucle.
 */
class WelcomeEntrenoSceneTest {

    private val period = WelcomeEntrenoPeriod
    private val tp = EntrenoT

    private fun at(t: Float) = entrenoFrameAt(t)

    private fun times(step: Float = 0.01f): List<Float> {
        val out = ArrayList<Float>()
        var t = 0f
        while (t < period) { out += t; t += step }
        return out
    }

    // ------------------------------------------------------------------------------------ guion

    @Test
    fun periodIsInsideTheRangeOfTheBrief() {
        assertTrue("el periodo debe estar entre 12 y 16 s", period in 12f..16f)
    }

    @Test
    fun milestonesAreStrictlyOrderedAndFitBeforeTheFreezeFrame() {
        val ordered = listOf(
            "tapHome" to tp.tapHome, "sheetRise" to tp.sheetRise, "focusIn" to tp.focusIn, "typeStart" to tp.typeStart,
            "typeEnd" to tp.typeEnd, "tapAdd" to tp.tapAdd, "pickerRise" to tp.pickerRise, "flingDown" to tp.flingDown,
            "flingUp" to tp.flingUp, "tapPress" to tp.tapPress, "tapRemo" to tp.tapRemo, "tapAnadir" to tp.tapAnadir,
            "pickerClose" to tp.pickerClose, "row1" to tp.row1, "row2" to tp.row2, "ctaOn" to tp.ctaOn,
            "tapEmpezar" to tp.tapEmpezar, "exitStart" to tp.exitStart, "liveStart" to tp.liveStart,
            "tapRow1" to tp.tapRow1, "tapCheck1" to tp.tapCheck1, "dock1In" to tp.dock1In, "lapseStart" to tp.lapseStart,
            "lapseEnd" to tp.lapseEnd, "tapSaltar" to tp.tapSaltar, "act2" to tp.act2, "tapCheck2" to tp.tapCheck2,
            "act3" to tp.act3, "tapCheck3" to tp.tapCheck3, "dock3In" to tp.dock3In,
        )
        for (i in 1 until ordered.size) {
            assertTrue(
                "${ordered[i - 1].first} (${ordered[i - 1].second}) debe ir antes que ${ordered[i].first} (${ordered[i].second})",
                ordered[i - 1].second < ordered[i].second,
            )
        }
        // La anilla de confirmación de la tercera serie (0,6 s) ya terminó en el cuadro que se congela.
        assertTrue("la tercera serie debe estar asentada antes de 0,7 × periodo", tp.tapCheck3 + 0.6f < period * 0.7f)
        assertTrue("el final debe dejar tiempo al fundido", tp.coachIn + 0.5f < period - tp.veilOut)
    }

    @Test
    fun tapsAreOrderedAndSeparated() {
        for (i in 1 until EntrenoTaps.size) {
            assertTrue(
                "${EntrenoTaps[i].id} debe ocurrir ≥ 0,3 s después de ${EntrenoTaps[i - 1].id}",
                EntrenoTaps[i].t - EntrenoTaps[i - 1].t >= 0.3f,
            )
        }
        assertEquals(11, EntrenoTaps.size)
    }

    @Test
    fun fingerWaypointTimesAreStrictlyIncreasing() {
        val ts = entrenoFingerWaypointTimes()
        for (i in 1 until ts.size) assertTrue("punto $i: ${ts[i - 1]} → ${ts[i]}", ts[i] > ts[i - 1])
    }

    @Test
    fun fingerPointsAtTheRightControlOnEveryTap() {
        for (tap in EntrenoTaps) {
            val d = at(tap.t).dedo
            assertTrue("${tap.id}: el dedo debe verse (alpha ${d.alpha})", d.alpha > 0.97f)
            assertTrue("${tap.id}: el dedo (${d.x}, ${d.y}) debe estar sobre su control ${tap.target}", tap.target.contains(d.x, d.y))
            assertEquals("${tap.id}: x", tap.x, d.x, 2.5f)
            assertEquals("${tap.id}: y", tap.y, d.y, 2.5f)
            assertTrue("${tap.id}: el dedo debe estar hundido al tocar (${d.press})", d.press > 0.9f)
            val after = at(tap.t + 0.05f).dedo
            assertTrue("${tap.id}: la onda del toque debe empezar", after.onda in 0f..0.2f)
        }
    }

    @Test
    fun fingerStaysInsideTheCanvasAndIsHiddenWhenNotNeeded() {
        for (t in times()) {
            val d = at(t).dedo
            assertTrue("x fuera del lienzo en $t", d.x in 0f..EntrenoGeo.W)
            assertTrue("y fuera del lienzo en $t", d.y in 0f..EntrenoGeo.H)
            assertTrue(d.alpha in 0f..1f)
            assertTrue(d.press in 0f..1f)
        }
        assertEquals("sin dedo al arrancar", 0f, at(0f).dedo.alpha, 0f)
        assertEquals("sin dedo al terminar", 0f, at(period - 0.01f).dedo.alpha, 0f)
        assertEquals("sin dedo mientras teclea", 0f, at((tp.typeStart + tp.typeEnd) / 2f).dedo.alpha, 0f)
    }

    // ------------------------------------------------------------------------------------ etapas

    @Test
    fun stagesFollowTheStoryboard() {
        assertEquals(EntrenoEtapa.INICIO, at(0.2f).etapa)
        assertEquals(EntrenoEtapa.INICIO, at(tp.tapHome).etapa)
        assertEquals(EntrenoEtapa.EDITOR, at(2.5f).etapa)
        assertEquals(EntrenoEtapa.SELECTOR, at((tp.flingDown + tp.flingUp) / 2f).etapa)
        assertEquals(EntrenoEtapa.SELECTOR, at(tp.tapRemo).etapa)
        assertEquals(EntrenoEtapa.EDITOR, at(tp.row2 + 0.3f).etapa)
        assertEquals(EntrenoEtapa.SESION, at(tp.tapCheck1).etapa)
        assertEquals(EntrenoEtapa.SESION, at(period * 0.7f).etapa)
    }

    @Test
    fun sheetRisesSettlesAndLeavesDownwards() {
        assertEquals(EntrenoGeo.sheetHidden, at(tp.sheetRise - 0.01f).editor.hojaY, 0.01f)
        assertEquals(EntrenoGeo.sheetRest, at(tp.focusIn + 0.3f).editor.hojaY, 1.5f)
        // rebasa un poco, pero nunca llega a tapar la barra de estado
        val minY = times().minOf { at(it).editor.hojaY }
        assertTrue("la hoja no debe subir más de 20 dp de su reposo ($minY)", minY > EntrenoGeo.sheetRest - 20f)
        assertTrue(minY > EntrenoGeo.statusH)
        assertEquals(EntrenoGeo.sheetHidden, at(tp.exitStart + tp.exitDur + 0.01f).editor.hojaY, 0.5f)
        assertTrue("el telón oscurece con la hoja", at(tp.focusIn).editor.telon > 0.55f)
        assertEquals(0f, at(tp.liveStart + 0.6f).editor.telon, 0.001f)
    }

    // ------------------------------------------------------------------------------------ nombre

    @Test
    fun typedNameIsAMonotonePrefixOfTheFinalName() {
        var last = ""
        for (t in times(0.005f)) {
            val typed = at(t).editor.nombre
            assertTrue("«$typed» debe ser prefijo de «$EntrenoNombre»", EntrenoNombre.startsWith(typed))
            assertTrue("el texto no puede acortarse ($last → $typed en $t)", typed.length >= last.length)
            last = typed
        }
        assertEquals("", at(tp.typeStart - 0.05f).editor.nombre)
        assertEquals(EntrenoNombre, at(tp.typeEnd + 0.02f).editor.nombre)
        assertEquals(EntrenoNombre, at(tp.tapAdd).editor.nombre)
        assertEquals("Pecho y espalda", EntrenoNombre)
    }

    @Test
    fun typedNameAdvancesAboutOneCharacterEveryFewHundredthsOfASecond() {
        // Ni se vuelca de golpe ni se arrastra: en mitad del tecleo hay entre 5 y 10 caracteres.
        val mid = at((tp.typeStart + tp.typeEnd) / 2f).editor.nombre.length
        assertTrue("a mitad del tecleo hay $mid caracteres", mid in 5..10)
        for (i in 1..EntrenoNombre.length) {
            val tt = tp.typeStart + tp.typeDur * i / EntrenoNombre.length
            assertTrue(entrenoTyped(tt).length >= i - 2)
        }
    }

    @Test
    fun caretShowsWhileTypingAndDisappearsWhenFocusIsLost() {
        assertFalse(at(0.5f).editor.cursor)
        assertTrue(at((tp.typeStart + tp.typeEnd) / 2f).editor.cursor)
        assertFalse("sin foco no hay cursor tras tocar «Añadir ejercicio»", at(tp.tapAdd + 0.3f).editor.cursor)
        // en cuanto el campo recibe el foco aparece el cursor, antes de la primera letra
        assertTrue(at(tp.focusIn + 0.1f).editor.cursor)
        assertEquals("", at(tp.focusIn + 0.1f).editor.nombre)
    }

    // ------------------------------------------------------------------------------------ selector

    @Test
    fun pickerScrollIsMonotoneWithInertiaAndLandsOnBothExercises() {
        var last = -1f
        for (t in times(0.005f)) {
            val s = at(t).selector.scroll
            assertTrue("el desplazamiento no retrocede ($last → $s en $t)", s >= last - 1e-3f)
            last = s
        }
        assertEquals(0f, at(tp.flingDown - 0.01f).selector.scroll, 0f)
        // durante el arrastre sigue al dedo
        val mid = (tp.flingDown + tp.flingUp) / 2f
        val dedo = at(mid).dedo
        val travelled = 562f - dedo.y
        assertEquals("la lista acompaña al dedo", travelled, at(mid).selector.scroll, 1.5f)
        // al soltar sigue por inercia: se sigue moviendo después de levantar el dedo
        assertTrue(at(tp.flingUp + 0.2f).selector.scroll > at(tp.flingUp).selector.scroll + 40f)
        // y se asienta en el valor final
        assertEquals(EntrenoGeo.pickerScrollEnd, at(tp.flingUp + tp.inertia + 0.05f).selector.scroll, 0.01f)
        assertEquals(EntrenoGeo.pickerScrollEnd, at(tp.tapPress).selector.scroll, 4f)
        // las dos filas tocadas están a la vista y por encima del botón «Añadir»
        for (idx in listOf(EntrenoGeo.idxPressBanca, EntrenoGeo.idxRemoConBarra)) {
            val row = EntrenoGeo.catRowRect(idx, EntrenoGeo.pickerScrollEnd)
            assertTrue("«${EntrenoGeo.catalog[idx].name}» debe estar bajo la zona de filtros", row.t >= EntrenoGeo.listViewTop)
            assertTrue("«${EntrenoGeo.catalog[idx].name}» no debe quedar bajo el botón", row.b <= EntrenoGeo.listTouchBottom)
        }
        assertEquals("Press banca", EntrenoGeo.catalog[EntrenoGeo.idxPressBanca].name)
        assertEquals("Remo con barra", EntrenoGeo.catalog[EntrenoGeo.idxRemoConBarra].name)
        // al empezar «Press banca» NO se ve: hay que desplazar para encontrarlo
        assertTrue(EntrenoGeo.catRowTop(EntrenoGeo.idxPressBanca, 0f) > EntrenoGeo.H)
        // la lista llega hasta el fondo en el reposo final
        val lastRowBottom = EntrenoGeo.catRowTop(EntrenoGeo.catalog.size - 1, EntrenoGeo.pickerScrollEnd) + EntrenoGeo.catRowH
        assertTrue("la lista no debe quedarse corta al final del desplazamiento", lastRowBottom >= EntrenoGeo.H)
    }

    @Test
    fun pickerSelectionAndButtonCountFollowTheTaps() {
        assertEquals(0f, at(tp.tapPress - 0.05f).selector.sel1, 1e-5f)
        assertTrue(at(tp.tapPress + 0.4f).selector.sel1 > 0.99f)
        assertEquals(0f, at(tp.tapRemo - 0.05f).selector.sel2, 1e-5f)
        assertTrue(at(tp.tapRemo + 0.4f).selector.sel2 > 0.99f)
        assertEquals("1", at(tp.tapRemo - 0.1f).selector.ctaCount.shown)
        assertEquals("2", at(tp.tapRemo + 0.5f).selector.ctaCount.shown)
        assertEquals(0f, at(tp.tapPress - 0.05f).selector.cta, 1e-5f)
        assertTrue(at(tp.tapRemo).selector.cta > 0.95f)
        assertEquals(EntrenoGeo.sheetHidden, at(tp.pickerClose + tp.pickerCloseDur + 0.02f).selector.y, 0.5f)
        assertEquals(EntrenoGeo.pickerRest, at(tp.tapPress).selector.y, 1.5f)
    }

    // ------------------------------------------------------------------------------------ filas y contadores

    @Test
    fun editorRowsEnterOneAfterTheOtherAndTheCountersRoll() {
        val before = at(tp.row1 - 0.05f).editor
        assertEquals(0f, before.fila1.enter, 0f)
        assertEquals(0f, before.filas, 0f)
        assertEquals("0", before.ejercicios.shown)
        assertTrue(before.vacio > 0.99f)

        val one = at(tp.row2 - 0.02f).editor
        assertTrue("fila 1 ya casi asentada", one.fila1.enter > 0.8f)
        assertEquals(0f, one.fila2.enter, 0f)
        // los contadores ruedan UNA vez, de 0 a 2 (las dos filas entran casi a la vez)
        val rolling = at(tp.row1 + 0.12f + 0.2f).editor
        assertTrue("los contadores están rodando", rolling.ejercicios.p in 0.1f..0.9f && !rolling.ejercicios.done)
        assertEquals("0", rolling.ejercicios.from)
        assertEquals("2", rolling.ejercicios.to)
        assertEquals("8", rolling.series.to)
        assertEquals("50", rolling.minutos.to)

        val two = at(tp.ctaOn + 0.5f).editor
        assertEquals(1f, two.fila1.enter, 0.02f)
        assertEquals(1f, two.fila2.enter, 0.02f)
        assertEquals(2f, two.filas, 0.04f)
        assertEquals("2", two.ejercicios.shown)
        assertEquals("8", two.series.shown)
        assertEquals("50", two.minutos.shown)
        assertEquals(0f, two.vacio, 0f)
        assertTrue(two.fila1.chip1 > 0.99f && two.fila2.chip3 > 0.99f)
        // los chips de cada fila entran uno tras otro
        val mid = at(tp.row1 + 0.30f).editor.fila1
        assertTrue(mid.chip1 > mid.chip2 && mid.chip2 >= mid.chip3)
    }

    @Test
    fun addButtonNeverOverlapsAVisibleRowWhileRowsEnter() {
        for (t in times(0.01f).filter { it in tp.row1..(tp.row2 + 0.8f) }) {
            val e = at(t).editor
            val addTop = EntrenoGeo.addTopL(e.filas)
            listOf(e.fila1 to 0, e.fila2 to 1).forEach { (fila, i) ->
                if (fila.enter > 0.3f) {
                    val rowBottom = EntrenoGeo.rowTopL(i) + EntrenoGeo.rowH
                    assertTrue("en t=$t el botón (y=$addTop) pisa la fila ${i + 1} (hasta y=$rowBottom)", addTop >= rowBottom - 2f)
                }
            }
        }
    }

    @Test
    fun startButtonEnablesOnlyOnceThereIsAnExercise() {
        assertEquals(0f, at(tp.row1).editor.ctaOn, 0f)
        assertEquals(0f, at(tp.ctaOn - 0.02f).editor.ctaOn, 0f)
        assertEquals(1f, at(tp.ctaOn + 0.4f).editor.ctaOn, 0.001f)
        assertTrue(at(tp.ctaOn + 0.1f).editor.ctaPulse in 0f..1f)
        assertEquals(-1f, at(tp.ctaOn + 0.9f).editor.ctaPulse, 0f)
        assertTrue("el botón se pulsa al tocarlo", at(tp.tapEmpezar).editor.ctaPress > 0.9f)
    }

    // ------------------------------------------------------------------------------------ sesión

    @Test
    fun setsAreLoggedInOrderWithTheExpectedNumbers() {
        val expected = listOf(Triple("60", "8", tp.tapCheck1), Triple("62,5", "8", tp.tapCheck2), Triple("62,5", "7", tp.tapCheck3))
        for ((i, e) in expected.withIndex()) {
            val before = at(e.third - 0.02f).sesion.series[i]
            assertNotEquals("serie ${i + 1} no puede estar hecha antes de su toque", EntrenoEstadoSerie.HECHA, before.estado)
            assertEquals("serie ${i + 1}: el peso ya está relleno al tocar el visto", e.first, before.peso.to)
            assertTrue("serie ${i + 1}: peso rodado antes del visto", before.peso.p > 0.99f)
            assertTrue("serie ${i + 1}: reps rodadas antes del visto", before.reps.p > 0.99f)
            assertEquals(e.second, before.reps.to)
            val after = at(e.third + 0.7f).sesion.series[i]
            assertEquals(EntrenoEstadoSerie.HECHA, after.estado)
            assertEquals(1f, after.hecha, 0.001f)
            assertEquals(e.first, after.peso.shown)
            assertEquals(e.second, after.reps.shown)
            assertEquals(-1f, after.onda, 0f)
            assertEquals(1f, after.checkScale, 0.01f)
        }
        // la cuarta serie queda pendiente y señalada como la siguiente
        val fourth = at(period * 0.7f).sesion.series[3]
        assertEquals(EntrenoEstadoSerie.ACTIVA, fourth.estado)
        assertEquals("", fourth.peso.shown)
    }

    @Test
    fun valuesFillInBeforeTheCheckAndTheFirstSetShowsTheRollIn() {
        val mid = (tp.roll1 + tp.tapCheck1) / 2f
        assertTrue("en mitad del relleno de la serie 1 el número está rodando", at(tp.roll1 + 0.12f).sesion.series[0].peso.p in 0.05f..0.95f)
        assertTrue(at(mid).sesion.series[0].activa > 0.9f)
        assertEquals("el pulso del toque en la fila", 1f, at(tp.tapRow1).sesion.series[0].touch, 0.05f)
        assertEquals(EntrenoEstadoSerie.PENDIENTE, at(tp.tapRow1 - 0.3f).sesion.series[0].estado)
    }

    @Test
    fun checkButtonPopsAndRingExpandsThenSettles() {
        val tc = tp.tapCheck1
        assertTrue("el botón se hunde al tocar", at(tc).sesion.series[0].checkScale < 0.95f)
        val peak = (1..40).maxOf { at(tc + it * 0.005f).sesion.series[0].checkScale }
        assertTrue("pop por encima de 1 (${peak})", peak > 1.1f)
        assertTrue(at(tc + 0.2f).sesion.series[0].onda in 0.01f..0.99f)
        assertEquals(-1f, at(tc - 0.05f).sesion.series[0].onda, 0f)
        assertEquals(1f, at(tc + 0.9f).sesion.series[0].checkScale, 0.005f)
    }

    @Test
    fun restDockRunsInTimeLapseAndIsSkipped() {
        assertEquals(0f, at(tp.tapCheck1).sesion.descanso.visible, 0f)
        val shown = at(tp.dock1In + 0.4f).sesion.descanso
        assertTrue(shown.visible > 0.9f)
        assertEquals(1, shown.serie)
        assertTrue("al aparecer el anillo está casi lleno", at(tp.dock1In + 0.05f).sesion.descanso.fraccion > 0.97f)
        assertEquals(90, at(tp.dock1In).sesion.descanso.restante)
        // el time-lapse vacía casi todo el descanso y nunca vuelve atrás
        var last = Int.MAX_VALUE
        var t = tp.dock1In
        while (t < tp.dock1Out) {
            val r = at(t).sesion.descanso.restante
            assertTrue("el descanso no puede subir ($last → $r en $t)", r <= last)
            last = r
            t += 0.01f
        }
        val skipped = at(tp.tapSaltar).sesion.descanso
        assertTrue("se salta con unos pocos segundos por delante (${skipped.restante})", skipped.restante in 5..25)
        assertTrue(at((tp.lapseStart + tp.lapseEnd) / 2f).sesion.descanso.lapso > 0.9f)
        assertTrue("al tocar «Saltar» se hunde", skipped.saltarPress > 0.9f)
        assertEquals(0f, at(tp.dock1Out + tp.dock1OutDur + 0.02f).sesion.descanso.visible, 0.001f)
        // tras la tercera serie vuelve el descanso, entero y corriendo a velocidad real
        val third = at(tp.dock3In + 0.5f).sesion.descanso
        assertEquals(3, third.serie)
        assertTrue(third.visible > 0.95f)
        assertTrue(third.restante in 85..89)
        assertTrue(third.fraccion in 0.9f..1f)
    }

    @Test
    fun sessionClockOnlyGoesForwardAndJumpsDuringTheTimeLapse() {
        var last = 0f
        var t = tp.liveStart
        while (t < period) {
            val s = entrenoSessionSeconds(t)
            assertTrue("el cronómetro no retrocede ($last → $s en $t)", s >= last - 1e-3f)
            last = s
            t += 0.01f
        }
        assertEquals(14.5f, entrenoSessionSeconds(tp.liveStart), 0.5f)
        // el cuadro congelado cae a mitad de un segundo: ningún dígito del cronómetro está rodando
        val frozen = entrenoSessionSeconds(period * 0.7f)
        assertEquals(0.55f, frozen - kotlin.math.floor(frozen), 0.02f)
        val jump = entrenoSessionSeconds(tp.lapseEnd) - entrenoSessionSeconds(tp.lapseStart)
        assertTrue("el time-lapse salta ≈ ${tp.lapseSeconds} s ($jump)", jump > tp.lapseSeconds)
        assertEquals("00:14", at(tp.liveStart).sesion.crono.to)
        assertEquals("00:00", entrenoClock(0))
        assertEquals("01:32", entrenoClock(92))
        assertEquals("12:05", entrenoClock(725))
        assertEquals("1:30", entrenoRestClock(90))
        assertEquals("0:07", entrenoRestClock(7))
    }

    @Test
    fun progressCounterAndBarAdvanceWithEachSet() {
        assertEquals("0", at(tp.tapCheck1 - 0.05f).sesion.hechas.shown)
        assertEquals(0f, at(tp.tapCheck1 - 0.05f).sesion.progreso, 0.001f)
        assertEquals("1", at(tp.tapCheck1 + 0.6f).sesion.hechas.shown)
        assertEquals(1f, at(tp.tapCheck1 + 0.6f).sesion.progreso, 0.01f)
        assertEquals("2", at(tp.tapCheck2 + 0.6f).sesion.hechas.shown)
        assertEquals(2f, at(tp.tapCheck2 + 0.6f).sesion.progreso, 0.01f)
        assertEquals("3", at(tp.tapCheck3 + 0.6f).sesion.hechas.shown)
        assertEquals(3f, at(tp.tapCheck3 + 0.6f).sesion.progreso, 0.01f)
        var last = 0f
        for (t in times()) {
            val p = at(t).sesion.progreso
            assertTrue(p >= last - 1e-4f)
            last = p
        }
    }

    @Test
    fun recordBadgeBelongsToTheFirstSetAt62Point5() {
        // 62,5 kg × 8 supera los 60 kg × 8 de la primera serie: la insignia va en la serie 2, nunca en las otras.
        for (t in times(0.02f)) {
            val s = at(t).sesion.series
            assertEquals(0f, s[0].record, 0f)
            assertEquals(0f, s[2].record, 0f)
            assertEquals(0f, s[3].record, 0f)
        }
        assertEquals(0f, at(tp.tapCheck2 + 0.05f).sesion.series[1].record, 0.001f)
        assertEquals(1f, at(tp.tapCheck2 + 0.9f).sesion.series[1].record, 0.001f)
        val peak = (0..60).maxOf { at(tp.tapCheck2 + 0.16f + it * 0.01f).sesion.series[1].record }
        assertTrue("la insignia entra con un pequeño rebote", peak > 1.01f)
    }

    // ------------------------------------------------------------------------------------ cuadro congelado y bucle

    @Test
    fun freezeFrameIsAPrettyCompleteStill() {
        val f = at(period * 0.7f)
        assertEquals(EntrenoEtapa.SESION, f.etapa)
        assertEquals("sin velo", 0f, f.velo, 0f)
        assertEquals("sin dedo en el cuadro quieto", 0f, f.dedo.alpha, 0f)
        assertEquals("sin onda del dedo", -1f, f.dedo.onda, 0f)
        assertEquals(1f, f.sesion.baseAlpha, 0f)
        assertEquals(0f, f.homeAlpha, 0f)
        assertEquals(EntrenoGeo.sheetHidden, f.editor.hojaY, 0.5f)
        assertEquals(EntrenoGeo.sheetHidden, f.selector.y, 0.5f)
        assertEquals(0f, f.editor.telon, 0f)
        assertEquals(0f, f.selector.telon, 0f)
        assertEquals("tres series registradas", "3", f.sesion.hechas.shown)
        assertTrue(f.sesion.hechas.done)
        val done = f.sesion.series.count { it.estado == EntrenoEstadoSerie.HECHA }
        assertEquals(3, done)
        for (i in 0..2) {
            val s = f.sesion.series[i]
            assertEquals(1f, s.enter, 0.001f)
            assertEquals(1f, s.hecha, 0.001f)
            assertEquals("serie ${i + 1} sin anillo a medias", -1f, s.onda, 0f)
            assertEquals("serie ${i + 1} sin pop a medias", 1f, s.checkScale, 0.01f)
            assertTrue(s.peso.done && s.reps.done)
        }
        assertEquals("el récord ya está a la vista", 1f, f.sesion.series[1].record, 0.001f)
        assertTrue("descanso visible", f.sesion.descanso.visible > 0.99f)
        assertTrue("el anillo del descanso casi lleno", f.sesion.descanso.fraccion > 0.9f)
        assertTrue("el dato de progreso rodó del todo", f.sesion.hechas.p >= 0.999f)
        assertTrue("el cronómetro ya no está rodando", f.sesion.crono.done)
        assertEquals("el cuadro congelado no es el del arranque", false, f == at(0f))
    }

    @Test
    fun loopStartsAndEndsUnderTheSameVeil() {
        assertEquals(1f, at(0f).velo, 0f)
        assertEquals(1f, at(period - 0.0005f).velo, 0.001f)
        assertEquals(1f, at(period).velo, 0f)
        assertEquals(0f, at(tp.veilIn + 0.01f).velo, 0.01f)
        assertEquals(0f, at(period - tp.veilOut - 0.01f).velo, 0.01f)
        assertEquals(0f, at(0f).dedo.alpha, 0f)
        assertEquals(0f, at(period).dedo.alpha, 0f)
        // el velo solo se mueve en los extremos
        for (t in times()) if (t in 0.6f..(period - 0.6f)) assertEquals(0f, at(t).velo, 0f)
        // t fuera de rango no rompe nada (el que muestra la escena repite con módulo, pero por si acaso)
        assertEquals(at(period), at(period + 3f))
        assertEquals(at(0f), at(-1f))
    }

    @Test
    fun theStillTailHasSomethingAliveHappening() {
        val a = at(period * 0.7f).sesion
        val b = at(period - 0.9f).sesion
        assertNotEquals("el descanso sigue corriendo", a.descanso.restante, b.descanso.restante)
        assertNotEquals("el cronómetro sigue corriendo", a.crono.to, b.crono.to)
        assertTrue("el aviso del asistente aparece en el respiro final", b.coach > 0.99f)
        assertEquals(0f, a.coach, 0f)
    }

    @Test
    fun everyNumberInEveryFrameIsFiniteAndInRange() {
        for (t in times(0.02f)) {
            val f = at(t)
            val numbers = mutableListOf(
                f.t, f.velo, f.latido, f.tarjetaPress, f.tarjetaOnda, f.homeAlpha,
                f.dedo.x, f.dedo.y, f.dedo.alpha, f.dedo.press, f.dedo.onda,
                f.editor.hojaY, f.editor.telon, f.editor.foco, f.editor.filas, f.editor.vacio, f.editor.addPress, f.editor.ctaOn,
                f.editor.ctaPulse, f.editor.ctaPress,
                f.editor.fila1.enter, f.editor.fila1.chip1, f.editor.fila1.chip2, f.editor.fila1.chip3,
                f.editor.fila2.enter, f.editor.fila2.chip1, f.editor.fila2.chip2, f.editor.fila2.chip3,
                f.editor.ejercicios.p, f.editor.series.p, f.editor.minutos.p,
                f.selector.y, f.selector.telon, f.selector.scroll, f.selector.sel1, f.selector.sel2, f.selector.cta, f.selector.ctaPress,
                f.selector.ctaCount.p,
                f.sesion.baseAlpha, f.sesion.enter, f.sesion.progreso, f.sesion.coach, f.sesion.hechas.p, f.sesion.crono.p,
                f.sesion.descanso.visible, f.sesion.descanso.fraccion, f.sesion.descanso.lapso, f.sesion.descanso.saltarPress,
            )
            for (s in f.sesion.series) numbers += listOf(s.enter, s.activa, s.touch, s.hecha, s.checkScale, s.onda, s.record, s.peso.p, s.reps.p)
            numbers.forEachIndexed { i, v -> assertTrue("número $i no finito ($v) en t=$t", v.isFinite()) }
            assertTrue(f.velo in 0f..1f && f.sesion.enter in 0f..1f && f.editor.telon in 0f..1f)
            assertTrue(f.sesion.descanso.fraccion in 0f..1f && f.sesion.descanso.restante in 0..90)
        }
    }

    @Test
    fun framesAreDeterministic() {
        for (t in listOf(0.8f, 2.5f, 4f, 5.5f, 7.5f, 9f, 11f, 13.5f)) assertEquals(at(t), at(t))
    }

    // ------------------------------------------------------------------------------------ números que ruedan

    @Test
    fun rollStepsFollowTheirSchedule() {
        val starts = floatArrayOf(1f, 3f)
        val values = intArrayOf(0, 4, 8)
        assertEquals(EntrenoRoll("0", "0", 1f), entrenoRollSteps(0.5f, starts, values, 0.4f))
        val rolling = entrenoRollSteps(1.2f, starts, values, 0.4f)
        assertEquals("0", rolling.from)
        assertEquals("4", rolling.to)
        assertEquals(0.5f, rolling.p, 0.001f)
        assertFalse(rolling.done)
        val settled = entrenoRollSteps(2f, starts, values, 0.4f)
        assertEquals("4", settled.shown)
        assertTrue(settled.done || settled.p >= 0.999f)
        val second = entrenoRollSteps(3.1f, starts, values, 0.4f)
        assertEquals("4", second.from)
        assertEquals("8", second.to)
        assertEquals("8", entrenoRollSteps(9f, starts, values, 0.4f).shown)
    }

    @Test
    fun settleSpringStartsAtZeroOvershootsALittleAndEndsAtOne() {
        assertEquals(0f, entrenoSettle(-1f), 0f)
        assertEquals(0f, entrenoSettle(0f), 0f)
        assertEquals(1f, entrenoSettle(3f), 0.001f)
        val peak = (1..300).maxOf { entrenoSettle(it * 0.005f) }
        assertTrue("rebasa algo, pero poco ($peak)", peak in 1.005f..1.12f)
    }

    @Test
    fun homeElementsEnterOneAfterAnotherBeforeTheFingerArrives() {
        assertEquals(0f, entrenoHomeEnter(0f, 0), 0f)
        for (i in 0..7) assertEquals(1f, entrenoHomeEnter(tp.fingerIn + 0.7f, i), 0.001f)
        assertTrue(entrenoHomeEnter(0.3f, 0) > entrenoHomeEnter(0.3f, 7))
    }
}
