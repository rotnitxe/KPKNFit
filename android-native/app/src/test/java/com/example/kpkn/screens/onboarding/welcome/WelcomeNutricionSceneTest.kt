package com.example.kpkn.screens.onboarding.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * La lógica de la escena de Nutrición: qué se ve en cada instante. Son funciones puras de `t`: aquí no hay pantalla.
 */
class WelcomeNutricionSceneTest {
    private val period = WelcomeNutricionPeriod
    private val still = period * 0.7f
    private val b = NutricionBeats

    /** Instante en que la última fila ya rodó hasta su cifra y terminó de llenar sus barras. */
    private val lastRowSettled = b.Rows0 + b.RowStagger * 3 + 0.78f + 0.01f

    /** Todos los instantes del bucle a 60 cuadros por segundo. */
    private fun sweep(step: Float = 1f / 60f): List<Float> {
        val out = ArrayList<Float>()
        var t = 0f
        while (t < period) {
            out += t
            t += step
        }
        return out
    }

    // ── contrato

    @Test
    fun theLoopHasTheLengthTheContractAllows() {
        assertTrue("periodo $period", period in 11f..15f)
    }

    @Test
    fun theBeatsComeInOrder() {
        val order = listOf(
            b.RevealEnd, b.Touch1, b.SheetUp0, b.CaretOn, b.TypeStart, b.TypeEnd, b.EnterPress, b.Expand0, b.Chips0,
            b.Rows0, b.Cta0, b.Touch2, b.Close0, b.Rings0, b.Badge1, b.Tour0, b.DipStart, b.DipEnd,
        )
        for (i in 1 until order.size) assertTrue("beat $i: ${order[i - 1]} < ${order[i]}", order[i - 1] < order[i])
        assertTrue(b.DipEnd < period)
    }

    @Test
    fun theStillFrameFallsAfterTheLastThingSettlesAndBeforeTheFadeBack() {
        assertTrue("el cuadro fijo ($still) llega tras el último sello (${b.Badge1})", still >= b.Badge1)
        assertTrue(still < b.Tour0)
        assertEquals(period * 0.7f, WelcomeNutricionStillT, 1e-4f)
    }

    // ── la comida de ejemplo

    @Test
    fun everyRowAddsUpFromItsMacros() {
        for (food in NutricionMeal.foods) {
            val err = abs(food.kcalFromMacros - food.kcal) / food.kcal.toFloat()
            assertTrue("${food.name}: ${food.kcal} kcal vs ${food.kcalFromMacros} por macros", err <= 0.05f)
        }
    }

    @Test
    fun theDayTotalIsTheSumOfTheRows() {
        val m = NutricionMeal
        assertEquals(m.foods.sumOf { it.kcal }, m.totalKcal)
        assertEquals(m.foods.sumOf { it.protein }, m.totalProtein)
        assertEquals(m.foods.sumOf { it.carbs }, m.totalCarbs)
        assertEquals(m.foods.sumOf { it.fat }, m.totalFat)
        // Las cifras que cuenta la escena.
        assertEquals(504, m.totalKcal)
        assertEquals(27, m.totalProtein)
        assertEquals(40, m.totalCarbs)
        assertEquals(26, m.totalFat)
        val fromMacros = 4 * m.totalProtein + 4 * m.totalCarbs + 9 * m.totalFat
        assertTrue("total ${m.totalKcal} vs $fromMacros por macros", abs(fromMacros - m.totalKcal) / m.totalKcal.toFloat() <= 0.05f)
    }

    @Test
    fun theFirstMealTakesAboutAFifthOfTheDay() {
        val share = NutricionMeal.totalKcal / NutricionMeal.GoalKcal.toFloat()
        assertTrue("fracción $share", share in 0.20f..0.24f)
    }

    @Test
    fun everyFoodComesFromAPhraseOfTheSentenceInOrderWithoutOverlap() {
        val spans = NutricionMeal.spans
        assertEquals(4, spans.size)
        for (i in spans.indices) {
            assertEquals(NutricionMeal.foods[i].phrase, NutricionMeal.Sentence.substring(spans[i].first, spans[i].last + 1))
            if (i > 0) assertTrue(spans[i - 1].last < spans[i].first)
        }
    }

    @Test
    fun thousandsUseThePointOfSpanish() {
        assertEquals("1.796", nutricionMiles(1796))
        assertEquals("2.300", nutricionMiles(2300))
        assertEquals("504", nutricionMiles(504))
        assertEquals("60", nutricionMiles(60))
        assertEquals("0", nutricionMiles(0))
    }

    // ── el tecleo

    @Test
    fun theTypedTextIsAlwaysAPrefixAndOnlyGrows() {
        var last = 0
        for (t in sweep(1f / 120f)) {
            val f = nutricionFrameAt(t)
            val typed = f.textoTecleado
            assertTrue("«$typed» no es prefijo en t=$t", NutricionMeal.Sentence.startsWith(typed))
            assertTrue("el texto encogió en t=$t", typed.length >= last)
            last = typed.length
        }
        assertEquals(NutricionMeal.Sentence.length, last)
    }

    @Test
    fun nothingIsTypedBeforeTheBeatAndEverythingAfter() {
        assertEquals("", nutricionFrameAt(b.TypeStart - 0.01f).textoTecleado)
        assertEquals("", nutricionFrameAt(0f).textoTecleado)
        assertEquals(NutricionMeal.Sentence, nutricionFrameAt(b.TypeEnd + 0.01f).textoTecleado)
        assertEquals(NutricionMeal.Sentence, nutricionFrameAt(still).textoTecleado)
        // A mitad del tecleo hay un trozo, ni vacío ni entero.
        val mid = nutricionFrameAt((b.TypeStart + b.TypeEnd) / 2f).textoTecleado
        assertTrue(mid.isNotEmpty() && mid.length < NutricionMeal.Sentence.length)
    }

    @Test
    fun theTypingRhythmIsHuman() {
        val times = NutricionTyping.times
        assertEquals(NutricionMeal.Sentence.length, times.size)
        assertTrue(times.first() > b.TypeStart)
        assertEquals(b.TypeEnd, times.last(), 1e-3f)
        val gaps = FloatArray(times.size - 1) { times[it + 1] - times[it] }
        assertTrue("hay una tecla demasiado seguida: ${gaps.min()}", gaps.min() > 0.012f)
        assertTrue("hay una pausa demasiado larga: ${gaps.max()}", gaps.max() < 0.30f)
        val average = gaps.average().toFloat()
        // Tras la coma se respira: la tecla del espacio tarda más que la media.
        val comma = NutricionMeal.Sentence.indexOf(',')
        assertTrue("pausa tras la coma ${gaps[comma]} vs media $average", gaps[comma] > average * 1.5f)
        // No es una máquina de escribir uniforme.
        assertTrue(gaps.max() > average * 1.5f)
    }

    @Test
    fun theKeyThatJustWasTypedGlowsAndFades() {
        val i = 20
        val at = NutricionTyping.times[i]
        val f0 = nutricionFrameAt(at + 0.001f).sheet
        assertEquals(NutricionMeal.Sentence[i], f0.keyChars[0])
        assertTrue(f0.keyGlow[0] > 0.9f)
        val later = nutricionFrameAt(at + NutricionTyping.KeyGlowSecs + 0.05f).sheet
        assertFalse(later.keyChars.indices.any { later.keyChars[it] == NutricionMeal.Sentence[i] && later.keyGlow[it] > 0f })
    }

    @Test
    fun theKeyboardIsUpWhileTypingAndGoneWhenTheFoodsAppear() {
        assertTrue(nutricionFrameAt((b.TypeStart + b.TypeEnd) / 2f).sheet.keyboard > 0.99f)
        assertEquals(0f, nutricionFrameAt(b.Rows0).sheet.keyboard, 1e-4f)
    }

    // ── KPKN lo entiende

    @Test
    fun noChipExistsUntilTheSentenceIsComplete() {
        assertEquals(0, nutricionFrameAt(b.TypeEnd).chipsVisibles)
        assertEquals(0, nutricionFrameAt(b.Chips0 - 0.01f).chipsVisibles)
        assertEquals(4, nutricionFrameAt(b.Chips0 + b.ChipStagger * 3 + b.ChipFlight + 0.01f).chipsVisibles)
        assertEquals(4, nutricionFrameAt(still).chipsVisibles)
    }

    @Test
    fun chipsLeaveOneByOneInOrder() {
        var last = 0
        for (t in sweep()) {
            val n = nutricionFrameAt(t).chipsVisibles
            if (t < b.DipStart) assertTrue("los chips no pueden volver en t=$t", n >= last)
            last = n
            val chips = nutricionFrameAt(t).sheet.chips
            for (k in 1..3) assertTrue("el chip $k no sale antes que el ${k - 1}", chips[k] <= chips[k - 1] + 1e-6f)
        }
    }

    @Test
    fun theSentenceFadesAsItsWordsBecomeChips() {
        assertEquals(1f, nutricionFrameAt(b.Chips0 - 0.06f).sheet.textAlpha, 1e-4f)
        assertEquals(0f, nutricionFrameAt(b.Chips0 + 0.41f).sheet.textAlpha, 1e-4f)
        var last = 1f
        for (t in sweep()) {
            if (t < b.TypeStart) continue
            val a = nutricionFrameAt(t).sheet.textAlpha
            if (t < b.DipStart) assertTrue("la frase reapareció en t=$t", a <= last + 1e-5f)
            last = a
        }
        // Al terminar de teclear aún se ve entera: los chips salen de ella, no de la nada.
        assertEquals(1f, nutricionFrameAt(b.TypeEnd + 0.2f).sheet.textAlpha, 1e-4f)
    }

    @Test
    fun theChipsFitInsideTheFieldThatHeldTheSentence() {
        val l = NutricionLayout
        val needed = l.ChipsInset * 2f + l.ChipH * 2f + l.ChipRowGap
        assertTrue("los dos renglones de chips ($needed) caben en el campo (${l.FieldH})", needed <= l.FieldH + 0.01f)
    }

    @Test
    fun rowsAreResolvedOneAtATime() {
        assertEquals(0, nutricionFrameAt(b.Rows0 - 0.01f).filasResueltas)
        assertEquals(0, nutricionFrameAt(b.Rows0 + 0.2f).filasResueltas)
        var last = 0
        val seen = HashSet<Int>()
        for (t in sweep(1f / 120f)) {
            val n = nutricionFrameAt(t).filasResueltas
            if (t < b.DipStart) assertTrue("filas resueltas bajó en t=$t", n >= last)
            assertTrue("saltó de $last a $n filas en t=$t", n - last <= 1 || t < 0.01f)
            last = n
            seen += n
        }
        assertEquals(setOf(0, 1, 2, 3, 4), seen)
        assertEquals(4, nutricionFrameAt(still).filasResueltas)
    }

    @Test
    fun rowNumbersRollUpToTheirValueAndTheTotalIsTheirSum() {
        for (t in sweep()) {
            val s = nutricionFrameAt(t).sheet
            assertEquals("total de kcal en t=$t", s.rowKcal.sum(), s.totalKcal)
            for (i in 0..3) assertTrue(s.rowKcal[i] in 0..NutricionMeal.foods[i].kcal)
        }
        val done = nutricionFrameAt(lastRowSettled).sheet
        assertEquals(listOf(182, 112, 150, 60), done.rowKcal.toList())
        assertEquals(504, done.totalKcal)
        assertEquals(27, done.totalProtein)
        assertEquals(40, done.totalCarbs)
        assertEquals(26, done.totalFat)
    }

    @Test
    fun theFirstRowsStartRollingBeforeTheLastEnters() {
        val mid = nutricionFrameAt(b.Rows0 + b.RowStagger * 2 + 0.05f).sheet
        assertTrue(mid.rowKcal[0] > 0)
        assertTrue(mid.rowIn[3] == 0f)
        assertTrue(mid.rowKcal[0] >= mid.rowKcal[1])
    }

    @Test
    fun theMacroBarsAreProportionalToTheGramsOfEachRow() {
        val s = nutricionFrameAt(lastRowSettled).sheet
        for (i in 0..3) for (j in 0..2) assertTrue(s.rowBars[i * 3 + j] in 0f..1f)
        // El pan lleva muchos más hidratos que proteína y grasa.
        assertTrue(s.rowBars[2 * 3 + 1] > s.rowBars[2 * 3] * 2f)
        assertTrue(s.rowBars[2 * 3 + 1] > s.rowBars[2 * 3 + 2] * 5f)
        // La palta es sobre todo grasa.
        assertTrue(s.rowBars[1 * 3 + 2] > s.rowBars[1 * 3])
    }

    // ── el día se actualiza

    @Test
    fun theDayStartsEmpty() {
        val f = nutricionFrameAt(0.8f)
        assertEquals(0, f.kcalMostradas)
        assertTrue(f.anillos.all { it == 0f })
        assertEquals(NutricionMeal.GoalKcal, f.home.kcalLeft)
        assertEquals(0f, f.home.cardGrow, 0f)
    }

    @Test
    fun theRingsSweepToDifferentPercentages() {
        val f = nutricionFrameAt(still)
        assertEquals(504f / 2300f, f.anillos[0], 1e-3f)
        assertEquals(27f / 150f, f.anillos[1], 1e-3f)
        assertEquals(40f / 260f, f.anillos[2], 1e-3f)
        assertEquals(26f / 70f, f.anillos[3], 1e-3f)
        assertEquals("los cuatro porcentajes son distintos", 4, f.anillos.map { (it * 1000).toInt() }.toSet().size)
        assertTrue(f.anillos.all { it > 0f && it < 1f })
        assertEquals(0.22f, f.anillos[0], 0.015f)
    }

    @Test
    fun theCenterNumberCountsUpAndNeverBackUntilTheFade() {
        var last = 0
        for (t in sweep()) {
            if (t > b.DipStart) break
            val k = nutricionFrameAt(t).kcalMostradas
            assertTrue("el número del centro bajó en t=$t ($k < $last)", k >= last)
            last = k
        }
        assertEquals(504, last)
        assertEquals(504, nutricionFrameAt(b.Num1).kcalMostradas)
        assertTrue(nutricionFrameAt((b.Num0 + b.Num1) / 2f).kcalMostradas in 100..450)
    }

    @Test
    fun theRingsOnlyGrowWhileTheDayUpdates() {
        val prev = FloatArray(4)
        for (t in sweep()) {
            if (t > b.DipStart) break
            val r = nutricionFrameAt(t).anillos
            for (i in 0..3) {
                assertTrue("anillo $i bajó en t=$t", r[i] >= prev[i] - 1e-6f)
                prev[i] = r[i]
            }
        }
    }

    @Test
    fun theLegendAndTheRemainingFollowTheRings() {
        val f = nutricionFrameAt(still).home
        assertEquals(27, f.protein)
        assertEquals(40, f.carbs)
        assertEquals(26, f.fat)
        assertEquals(2300 - 504, f.kcalLeft)
        assertEquals(1796, f.kcalLeft)
        assertEquals(1f, f.summaryAlpha, 1e-4f)
    }

    @Test
    fun theSheetIsDownBeforeTheRingsSweepSoTheyAreNotHidden() {
        val sweeping = nutricionFrameAt(b.Rings0 + 0.3f)
        // A esa altura la hoja ya dejó libre la zona de los anillos (su borde superior está por debajo de ellos).
        val ringBottom = NutricionLayout.RingCy + NutricionLayout.RingOuter
        assertTrue("la hoja (${sweeping.sheet.top}) aún tapa los anillos", sweeping.sheet.top > ringBottom - 40f)
        assertTrue(nutricionFrameAt(b.Close1).sheet.top >= NutricionLayout.SheetHidden - 1f)
    }

    // ── la hoja

    @Test
    fun theSheetRisesWithASoftSpringAndSettlesAtTheTypingHeight() {
        assertEquals(NutricionLayout.SheetHidden, nutricionFrameAt(b.SheetUp0 - 0.1f).sheet.top, 1e-3f)
        assertEquals(NutricionLayout.SheetTyping, nutricionFrameAt(b.KbUp1 + 0.5f).sheet.top, 0.5f)
        var minTop = Float.MAX_VALUE
        var t = b.SheetUp0
        var prev = Float.MAX_VALUE
        var risingMonotonic = true
        while (t < b.SheetUp0 + 0.38f) {
            val top = nutricionSheetTop(t)
            if (top > prev + 1e-3f) risingMonotonic = false
            prev = top
            minTop = minOf(minTop, top)
            t += 0.005f
        }
        assertTrue("sube sin retroceder hasta el primer pico", risingMonotonic)
        // Un sobreimpulso pequeño (< 3 % del recorrido), nunca un rebote.
        val travel = NutricionLayout.SheetHidden - NutricionLayout.SheetTyping
        var overshoot = 0f
        t = b.SheetUp0
        while (t < b.SheetUp0 + 1.0f) {
            overshoot = maxOf(overshoot, NutricionLayout.SheetTyping - nutricionSheetTop(t))
            t += 0.005f
        }
        assertTrue("sobreimpulso ${overshoot / travel}", overshoot / travel < 0.03f)
    }

    @Test
    fun theSpringStartsAtRestEndsAtOneAndOvershootsLittle() {
        assertEquals(0f, nutricionSpring(0f), 0f)
        assertEquals(0f, nutricionSpring(-1f), 0f)
        assertEquals(1f, nutricionSpring(3f), 1e-3f)
        var peak = 0f
        var t = 0f
        while (t < 2f) { peak = maxOf(peak, nutricionSpring(t)); t += 0.002f }
        assertTrue("pico $peak", peak in 1f..1.04f)
    }

    @Test
    fun theSheetIsFullHeightWhileTheFoodsAppearAndTheButtonIsReachable() {
        val s = nutricionFrameAt(b.Cta1).sheet
        assertEquals(NutricionLayout.SheetFull, s.top, 0.5f)
        val l = NutricionLayout
        val ctaBottom = l.CtaCy + l.CtaH / 2f
        assertTrue("el botón queda a $ctaBottom", ctaBottom <= 596f)
        assertTrue(l.CtaCy - l.CtaH / 2f > l.SheetFull + l.FieldTop + l.FieldH)
    }

    @Test
    fun theLayoutLeavesRoomForEverything() {
        val l = NutricionLayout
        // Teclado: la última fila termina antes del gesto de inicio.
        val keyboardBottom = l.KeyboardTop + 10f + 4 * 40f + 34f
        assertTrue("teclado hasta $keyboardBottom", keyboardBottom <= 606f)
        // Con el campo lleno, el teclado no pisa el campo.
        val fieldBottom = l.SheetTyping + l.FieldTop + l.FieldH
        assertTrue(fieldBottom < l.KeyboardTop)
        // Pantalla de inicio: las dos tarjetas (con la primera ya abierta) caben sobre el botón.
        val cardsBottom = 398f + 100f + 8f + 44f
        assertTrue("tarjetas hasta $cardsBottom", cardsBottom <= l.FabTop - 4f)
        assertTrue(l.FabTop + l.FabH < 606f)
        // Anillos bajo el rótulo de paso y sobre la meta.
        assertTrue(l.RingCy - l.RingOuter > 84f + 24f)
        assertTrue(l.RingCy + l.RingOuter < 296f)
    }

    // ── el dedo

    @Test
    fun theFingerTouchesTheRightControlAtTheRightMoment() {
        val toFab = nutricionFrameAt(b.Touch1 + 0.001f).touch
        assertEquals(NutricionLayout.FabCx, toFab.x, 1.5f)
        assertEquals(NutricionLayout.FabCy, toFab.y, 1.5f)
        assertTrue(toFab.alpha > 0.95f)
        val pressed = nutricionFrameAt((b.Touch1 + b.Release1) / 2f + 0.03f).touch
        assertTrue(pressed.press > 0.95f)
        assertEquals(1f, nutricionFrameAt((b.Touch1 + b.Release1) / 2f + 0.03f).home.fabPress, 0.06f)

        val toCta = nutricionFrameAt(b.Touch2 + 0.001f).touch
        assertEquals(150f, toCta.x, 1.5f)
        assertEquals(NutricionLayout.CtaCy, toCta.y, 1.5f)
        assertTrue(toCta.alpha > 0.95f)
        assertTrue(nutricionFrameAt((b.Touch2 + b.Release2) / 2f + 0.03f).sheet.ctaPress > 0.95f)
    }

    @Test
    fun theFingerIsNotThereWhenNothingIsBeingTouched() {
        assertEquals(0f, nutricionFrameAt(0.1f).touch.alpha, 1e-3f)
        assertEquals(0f, nutricionFrameAt((b.Release1 + b.Touch2) / 2f).touch.alpha, 1e-3f)
        assertEquals(0f, nutricionFrameAt(still).touch.alpha, 1e-3f)
        assertEquals(0f, nutricionFrameAt(b.DipStart).touch.alpha, 1e-3f)
    }

    @Test
    fun theSheetOnlyRisesAfterTheFingerLetsGo() {
        assertEquals(NutricionLayout.SheetHidden, nutricionFrameAt(b.Release1).sheet.top, 1e-3f)
        assertEquals(NutricionLayout.SheetHidden, nutricionFrameAt(b.Touch1).sheet.top, 1e-3f)
        assertTrue(nutricionFrameAt(b.Release1 + 0.2f).sheet.top < NutricionLayout.SheetHidden - 100f)
        // y baja cuando el dedo suelta «Guardar comida»
        assertTrue(nutricionFrameAt(b.Touch2).sheet.top <= NutricionLayout.SheetFull + 0.5f)
        assertTrue(nutricionFrameAt(b.Close0 + 0.2f).sheet.top > NutricionLayout.SheetFull + 20f)
    }

    // ── rótulo del paso

    @Test
    fun theCaptionStepsGoOneTwoThree() {
        assertEquals(0, nutricionFrameAt(0.1f).caption.step)
        assertEquals(1, nutricionFrameAt(b.Caption2 - 0.1f).caption.step)
        assertEquals(2, nutricionFrameAt(b.Caption2 + 0.5f).caption.step)
        assertEquals(1, nutricionFrameAt(b.Caption2 + 0.1f).caption.previous)
        assertEquals(3, nutricionFrameAt(still).caption.step)
        assertEquals(1f, nutricionFrameAt(still).caption.mix, 1e-4f)
        var last = 0
        for (t in sweep()) {
            val s = nutricionFrameAt(t).caption.step
            assertTrue("el paso retrocedió en t=$t", s >= last)
            last = s
        }
    }

    // ── cuadro representativo y bucle

    @Test
    fun theStillFrameIsComplete() {
        val f = nutricionFrameAt(still)
        assertEquals("pantalla a la vista", 0f, f.dip, 1e-4f)
        assertFalse("la hoja ya bajó", f.hojaVisible)
        assertEquals(504, f.kcalMostradas)
        assertEquals(504f / 2300f, f.anillos[0], 1e-3f)
        assertEquals(1f, f.home.cardGrow, 1e-4f)
        assertTrue("comida marcada como registrada", f.home.badge >= 0.99f)
        assertTrue(f.home.cardFoods.all { it >= 0.99f })
        assertEquals(3, f.caption.step)
        assertEquals(4, f.filasResueltas)
        assertEquals(4, f.chipsVisibles)
        assertEquals(0f, f.touch.alpha, 1e-4f)
        assertTrue("sin respiro de anillos todavía", f.home.tour.all { it == 0f })
    }

    @Test
    fun theStillFrameIsSettledNotMidAnimation() {
        val a = nutricionFrameAt(still)
        val c = nutricionFrameAt(still + 0.1f)
        assertTrue(a.anillos.contentEquals(c.anillos))
        assertEquals(a.kcalMostradas, c.kcalMostradas)
        assertEquals(a.home.cardGrow, c.home.cardGrow, 1e-6f)
        assertEquals(a.home.badge, c.home.badge, 1e-3f)
        assertEquals(a.sheet.top, c.sheet.top, 1e-6f)
    }

    @Test
    fun theLoopIsCoveredAtItsSeam() {
        val first = nutricionFrameAt(0f)
        val last = nutricionFrameAt(period - 1e-3f)
        assertEquals(1f, first.dip, 1e-3f)
        assertEquals(1f, last.dip, 1e-3f)
        // El fundido a cubierto es continuo: ningún salto de más de un 9 % por cuadro (a 60 fps).
        var prev = nutricionFrameAt(0f).dip
        for (t in sweep()) {
            val d = nutricionFrameAt(t).dip
            assertTrue("salto de fundido en t=$t", abs(d - prev) < 0.09f)
            prev = d
        }
        // Mientras está cubierta, lo que hay debajo da igual; al descubrirse, el inicio está vacío.
        assertEquals(0, nutricionFrameAt(b.RevealEnd).kcalMostradas)
        assertTrue(nutricionFrameAt(b.RevealEnd).anillos.all { it == 0f })
        assertEquals(0f, nutricionFrameAt(0.2f).touch.alpha, 1e-3f)
        assertEquals(NutricionLayout.SheetHidden, nutricionFrameAt(b.RevealEnd).sheet.top, 1e-3f)
    }

    @Test
    fun timeWrapsAroundThePeriod() {
        val a = nutricionFrameAt(3.2f)
        val c = nutricionFrameAt(3.2f + period)
        assertEquals(a.textoTecleado, c.textoTecleado)
        assertEquals(a.sheet.top, c.sheet.top, 1e-2f)
        assertEquals(0, nutricionFrameAt(Float.NaN).kcalMostradas)
    }

    @Test
    fun ringsAreIntroducedOneAtATimeDuringTheRest() {
        for (t in sweep()) {
            val active = nutricionFrameAt(t).home.tour.count { it > 0.01f }
            assertTrue("dos anillos a la vez en t=$t", active <= 1)
        }
        assertTrue(nutricionFrameAt(b.Tour0 + b.TourStep * 0.5f).home.tour[0] > 0.9f)
        assertTrue(nutricionFrameAt(b.Tour0 + b.TourStep * 3.5f).home.tour[3] > 0.9f)
        assertTrue(b.Tour0 + b.TourStep * 4 < b.DipStart)
    }

    @Test
    fun everyValueOfEveryFrameIsInRange() {
        for (t in sweep()) {
            val f = nutricionFrameAt(t)
            val s = f.sheet
            val h = f.home
            val unit = listOf(
                f.dip, f.touch.alpha, f.touch.press, f.touch.ripple, f.caption.mix, s.scrim, s.keyboard, s.caret, s.focus,
                s.enterGlow, s.shimmer, s.textAlpha, s.ctaIn, s.ctaPress, h.fabPress, h.summaryAlpha, h.cardGrow,
            ) + s.underline.toList() + s.ignite.toList() + s.chips.toList() + s.rowIn.toList() + s.rowRoll.toList() +
                s.rowBars.toList() + s.keyGlow.toList() + h.rings.toList() + h.tour.toList()
            for (v in unit) assertTrue("fuera de [0,1] en t=$t: $v", v in -1e-4f..1.0001f)
            for (v in h.cardFoods.toList() + h.badge) assertTrue("rebote fuera de rango en t=$t: $v", v in -1e-4f..1.2f)
            for (v in listOf(f.touch.x, f.touch.y, s.top)) assertTrue("no finito en t=$t", v.isFinite())
            assertTrue(f.textoTecleado.length <= NutricionMeal.Sentence.length)
        }
    }
}
