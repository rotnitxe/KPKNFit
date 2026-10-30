package com.example.kpkn.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pulido v1 · tarjeta en vivo (`lv`): el carrusel de repeticiones y el ROM tal como los fija el lienzo aprobado. */
class LiveCardLogicTest {

    // ── Carrusel: tamaño y opacidad de las cifras ────────────────────────────────────────────────

    @Test fun elCentroEsTodoElTamanoYLosVecinosDecrecen() {
        assertEquals(1f, RepsCarouselMath.scale(0f), 1e-4f)
        assertEquals(.64f, RepsCarouselMath.scale(1f), 1e-4f)
        assertEquals(.48f, RepsCarouselMath.scale(2f), 1e-4f)
        assertEquals(.38f, RepsCarouselMath.scale(3f), 1e-4f)
        // Simétrico: a la izquierda y a la derecha igual.
        assertEquals(RepsCarouselMath.scale(1.4f), RepsCarouselMath.scale(-1.4f), 1e-6f)
    }

    @Test fun laOpacidadBajaDelCentroAlTercerVecino() {
        assertEquals(1f, RepsCarouselMath.alpha(0f), 1e-4f)
        assertEquals(.62f, RepsCarouselMath.alpha(1f), 1e-4f)
        assertEquals(.28f, RepsCarouselMath.alpha(2f), 1e-4f)
        assertEquals(0f, RepsCarouselMath.alpha(3f), 1e-4f)
        assertEquals(0f, RepsCarouselMath.alpha(5f), 1e-4f)
    }

    @Test fun fueraDelRangoLasCifrasSeAtenuanEnCuantoSeAlejanDelCentro() {
        assertEquals(.8f, RepsCarouselMath.outOfRangeAlpha(0f, .8f), 1e-4f)
        assertEquals(.8f * .62f, RepsCarouselMath.outOfRangeAlpha(.6f, .8f), 1e-4f)
        assertEquals(.8f * .62f, RepsCarouselMath.outOfRangeAlpha(2f, .8f), 1e-4f)
    }

    @Test fun laCifraDelCentroPasaDeOscuraACremaEntreCeroComaTresYCeroComaSesentaYDos() {
        assertEquals(0f, RepsCarouselMath.lensBlend(0f), 1e-4f)
        assertEquals(0f, RepsCarouselMath.lensBlend(.3f), 1e-4f)
        assertEquals(1f, RepsCarouselMath.lensBlend(.62f), 1e-4f)
        assertEquals(1f, RepsCarouselMath.lensBlend(2f), 1e-4f)
    }

    // ── Carrusel: el rango del plan ──────────────────────────────────────────────────────────────

    @Test fun dentroDelRangoIncluyeLosExtremosYUnAmrapNoTieneTecho() {
        assertTrue(RepsCarouselMath.isInRange(4, 4, 6))
        assertTrue(RepsCarouselMath.isInRange(6, 4, 6))
        assertFalse(RepsCarouselMath.isInRange(7, 4, 6))
        assertFalse(RepsCarouselMath.isInRange(3, 4, 6))
        assertTrue(RepsCarouselMath.isInRange(40, 8, null))
        assertFalse(RepsCarouselMath.isInRange(5, null, null))
    }

    @Test fun elRielDelRangoCubreDeLaCeldaMinimaALaMaximaConSieteDeMargen() {
        // Valor centrado en 5, plan 4–6: la celda 4 empieza a 1,5 celdas del centro (−81 dp) y el riel mide 3 celdas − 14.
        val span = RepsCarouselMath.railSpan(min = 4, max = 6, amrap = false, position = 5f, lastValue = 50)
        assertEquals((4 - .5f - 5f) * 54f + 7f, span.startDp, 1e-3f)
        assertEquals(3 * 54f - 14f, span.widthDp, 1e-3f)
        // Plan fijo: una marca corta bajo ese valor.
        val fixed = RepsCarouselMath.railSpan(min = 5, max = 5, amrap = false, position = 4f, lastValue = 50)
        assertEquals(54f - 14f, fixed.widthDp, 1e-3f)
        // AMRAP: desde el mínimo hasta el final de la lista.
        val open = RepsCarouselMath.railSpan(min = 8, max = null, amrap = true, position = 11f, lastValue = 50)
        assertEquals((50 - 8 + 1) * 54f, open.widthDp, 1e-3f)
    }

    @Test fun elRotuloDerechoDiceElPlanFrenteALoMarcado() {
        assertEquals(RepsCarouselMath.Label("Plan 4–6", true), RepsCarouselMath.label(5, 4, 6, amrap = false, failed = false))
        assertEquals(RepsCarouselMath.Label("Plan 4–6", true), RepsCarouselMath.label(4, 4, 6, amrap = false, failed = false))
        assertEquals(RepsCarouselMath.Label("Plan 4–6 · −1", false), RepsCarouselMath.label(3, 4, 6, amrap = false, failed = false))
        assertEquals(RepsCarouselMath.Label("Plan 4–6 · +2", false), RepsCarouselMath.label(8, 4, 6, amrap = false, failed = false))
        assertEquals(RepsCarouselMath.Label("Plan 5 · −1", false), RepsCarouselMath.label(4, 5, 5, amrap = false, failed = false))
        assertEquals(RepsCarouselMath.Label("Plan 5", true), RepsCarouselMath.label(5, 5, 5, amrap = false, failed = false))
    }

    @Test fun enUnAmrapElRotuloEsElMinimoYSeEnciendeAlLlegarAEl() {
        assertEquals(RepsCarouselMath.Label("Mínimo 8", true), RepsCarouselMath.label(11, 8, null, amrap = true, failed = false))
        assertEquals(RepsCarouselMath.Label("Mínimo 8", true), RepsCarouselMath.label(8, 8, null, amrap = true, failed = false))
        assertEquals(RepsCarouselMath.Label("Mínimo 8 · −2", false), RepsCarouselMath.label(6, 8, null, amrap = true, failed = false))
        // Sin mínimo: el texto de siempre de la tarjeta.
        assertEquals(RepsCarouselMath.Label("Sin mínimo", false), RepsCarouselMath.label(6, null, null, amrap = true, failed = false, noPlan = "Sin mínimo"))
    }

    @Test fun unaSerieFallidaDiceSerieFallidaYNoCompara() {
        assertEquals(RepsCarouselMath.Label("Serie fallida", false), RepsCarouselMath.label(0, 4, 6, amrap = false, failed = true))
        assertEquals(RepsCarouselMath.Label("Serie fallida", false), RepsCarouselMath.label(0, 8, null, amrap = true, failed = true))
        assertFalse(RepsCarouselMath.belowPlan(0, 4, amrap = false, failed = true))
    }

    @Test fun sinPlanNoHayRotuloNiTrazoMelocoton() {
        assertEquals(RepsCarouselMath.Label("", false), RepsCarouselMath.label(5, null, null, amrap = false, failed = false))
        assertFalse(RepsCarouselMath.belowPlan(5, null, amrap = false, failed = false))
    }

    @Test fun elTrazoMelocotonSoloAparecePorDebajoDelPlanYNoEnUnAmrap() {
        assertTrue(RepsCarouselMath.belowPlan(3, 4, amrap = false, failed = false))
        assertFalse(RepsCarouselMath.belowPlan(4, 4, amrap = false, failed = false))
        assertFalse(RepsCarouselMath.belowPlan(3, 8, amrap = true, failed = false))
    }

    @Test fun laListaLlegaSiempreAlMenosAlCincuentaYMasAllaDelValorYDelPlan() {
        assertEquals(50, RepsCarouselMath.lastValue(5, 6))
        assertEquals(62, RepsCarouselMath.lastValue(52, null))
        assertEquals(70, RepsCarouselMath.lastValue(10, 70))
    }

    // ── ROM ──────────────────────────────────────────────────────────────────────────────────────

    @Test fun lasZonasSonParcialMenosDeSesentaYCincoYCompletoDesdeNoventaYCinco() {
        assertEquals(RomZone.PARTIAL, LiveRom.zone(10))
        assertEquals(RomZone.PARTIAL, LiveRom.zone(64))
        assertEquals(RomZone.ALMOST_FULL, LiveRom.zone(65))
        assertEquals(RomZone.ALMOST_FULL, LiveRom.zone(94))
        assertEquals(RomZone.FULL, LiveRom.zone(95))
        assertEquals(RomZone.FULL, LiveRom.zone(100))
        assertEquals("80 % · Casi completo", LiveRom.tipText(80))
    }

    @Test fun elDeslizadorVaDeCincoEnCincoYLasMarcasCaenEnSusValores() {
        assertEquals(10, LiveRom.snap(0f))
        assertEquals(10, LiveRom.snap(12f))
        assertEquals(35, LiveRom.snap(34f))
        // Las marcas 50, 80 y 100 son múltiplos de cinco: el arrastre las encuentra exactas.
        assertEquals(50, LiveRom.snap(52f))
        assertEquals(45, LiveRom.snap(47f))
        assertEquals(80, LiveRom.snap(82f))
        assertEquals(80, LiveRom.snap(78f))
        assertEquals(95, LiveRom.snap(97f))
        assertEquals(100, LiveRom.snap(98f))
        assertEquals(100, LiveRom.snap(180f))
        assertEquals(60, LiveRom.snap(61f))
        assertEquals(90, LiveRom.snap(91f))
    }

    @Test fun elCampoExactoAdmiteCualquierEnteroDeDiezACien() {
        assertEquals(75, LiveRom.fromTyped(75))
        assertEquals(10, LiveRom.fromTyped(3))
        assertEquals(100, LiveRom.fromTyped(250))
    }

    @Test fun laFraccionDelPulgarVaDeCeroAUno() {
        assertEquals(0f, LiveRom.fraction(10), 1e-6f)
        assertEquals(1f, LiveRom.fraction(100), 1e-6f)
        assertEquals(.5f, LiveRom.fraction(55), 1e-6f)
    }

    @Test fun elRomSoloSeAnotaSiElEjercicioLoRegistra() {
        // Sin trackRom nada se anota…
        assertNull(LiveRom.recordable(trackRom = false, value = 80, alreadyRecorded = null))
        // …con trackRom se anota lo marcado (acotado).
        assertEquals(80, LiveRom.recordable(trackRom = true, value = 80, alreadyRecorded = null))
        assertEquals(100, LiveRom.recordable(trackRom = true, value = 140, alreadyRecorded = null))
        assertNull(LiveRom.recordable(trackRom = true, value = null, alreadyRecorded = null))
    }

    @Test fun unRomYaRegistradoNoSeBorraAunqueElEjercicioDejeDeMedirlo() {
        assertEquals(70, LiveRom.recordable(trackRom = false, value = 70, alreadyRecorded = 70))
        assertEquals(70, LiveRom.recordable(trackRom = false, value = null, alreadyRecorded = 70))
        assertEquals(60, LiveRom.recordable(trackRom = false, value = 60, alreadyRecorded = 70))
    }
}

class EffortHeatTest {
    @Test fun sinCalorPorDebajoDelNivelDos() {
        assertNull(EffortHeat.fireAt(0f))
        assertNull(EffortHeat.fireAt(1.99f))
    }

    @Test fun enElNivelDosYTresElCalorEsElDeLaTabla() {
        val two = EffortHeat.fireAt(2f)!!
        assertEquals(0xECCFB4, two.tipRgb); assertEquals(.26f, two.tail, 1e-4f); assertEquals(7f, two.blurDp, 1e-4f); assertEquals(.16f, two.alpha, 1e-4f)
        val three = EffortHeat.fireAt(3f)!!
        assertEquals(0xF4B88F, three.tipRgb); assertEquals(.40f, three.tail, 1e-4f); assertEquals(10f, three.blurDp, 1e-4f)
    }

    @Test fun entreNivelesInterpolaYElFalloEsElMaximo() {
        val mid = EffortHeat.fireAt(2.5f)!!
        assertEquals(.33f, mid.tail, 1e-4f); assertEquals(8.5f, mid.blurDp, 1e-4f)
        val fail = EffortHeat.fireAt(5f)!!
        assertEquals(0xFFF1C8, fail.tipRgb); assertEquals(.70f, fail.tail, 1e-4f); assertEquals(16f, fail.blurDp, 1e-4f); assertEquals(.60f, fail.alpha, 1e-4f)
    }

    @Test fun elPulgarSeCalientaDesdeElNivelUnoComaCinco() {
        assertEquals(0f, EffortHeat.thumbMix(1.5f), 1e-4f)
        assertEquals(1f, EffortHeat.thumbMix(3.7f), 1e-4f)
        assertEquals(0f, EffortHeat.hot(4f, hasFire = false), 1e-4f)
        assertEquals(1f, EffortHeat.hot(5f, hasFire = true), 1e-4f)
    }
}
