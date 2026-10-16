package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.IntensityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La escala de esfuerzo del slider (LV8): los valores son los de siempre de la app, el plan solo se marca y nunca se preselecciona, y la
 * posición del pulgar, las etiquetas y la restauración de una serie salen de funciones puras.
 */
class EffortScaleTest {

    private fun rirScale(plan: Int? = 2) = buildEffortScale(
        plannedIntensityMode = IntensityMode.RIR,
        reportedIntensityMode = IntensityMode.RIR,
        targetRir = plan,
        targetRpe = null,
    )

    private fun rpeScale(plan: Double? = 8.0) = buildEffortScale(
        plannedIntensityMode = IntensityMode.RPE,
        reportedIntensityMode = IntensityMode.RPE,
        targetRir = null,
        targetRpe = plan,
    )

    @Test
    fun laEscalaTieneLosMismosValoresQueElCarruselDeSiempre() {
        val esperada = buildIntensityCarouselItems(IntensityMode.RIR, IntensityMode.RIR, targetRir = 2, targetRpe = null)
        assertEquals(esperada, rirScale().items)
        assertEquals("10", rirScale().items.first().display)
        assertTrue(rirScale().items.last().isFailure)
    }

    @Test
    fun elPlanSeMarcaEnSuValorYNoEnElExtremoIzquierdo() {
        val escala = rirScale(plan = 2)
        val marcada = escala.items[escala.planIndex!!]
        assertEquals(IntensityMode.RIR, marcada.mode)
        assertEquals(2.0, marcada.numericValue!!, 0.001)
        assertTrue("el plan no es el mínimo", escala.planIndex!! > 0)
        assertEquals("el mínimo es el extremo izquierdo (el menos intenso)", "10", escala.items.first().display)
    }

    @Test
    fun unPlanRpeSeMarcaEnLaEscalaRpe() {
        val escala = rpeScale(plan = 8.0)
        assertEquals(8.0, escala.items[escala.planIndex!!].numericValue!!, 0.001)
        assertEquals("0", escala.items.first().display)
    }

    @Test
    fun unPlanRirSeConvierteSiLaEscalaEstaEnRpe() {
        val escala = buildEffortScale(
            plannedIntensityMode = IntensityMode.AMRAP,
            reportedIntensityMode = IntensityMode.AMRAP,
            targetRir = 2,
            targetRpe = null,
        )
        val marcada = escala.items[escala.planIndex!!]
        assertEquals(IntensityMode.RPE, marcada.mode)
        assertEquals("RIR 2 es RPE 8", 8.0, marcada.numericValue!!, 0.001)
    }

    @Test
    fun unPlanAFalloMarcaElFallo() {
        val escala = buildEffortScale(IntensityMode.FAILURE, IntensityMode.FAILURE, targetRir = null, targetRpe = null)
        assertTrue(escala.items[escala.planIndex!!].isFailure)
    }

    @Test
    fun sinObjetivoDeEsfuerzoNoHayMarcaDePlan() {
        assertNull(buildEffortScale(null, null, targetRir = null, targetRpe = null).planIndex)
        val rm = buildEffortScale(IntensityMode.SOLO_RM, null, targetRir = null, targetRpe = null)
        assertNull(rm.planIndex)
        assertEquals("—", rm.items.first().display)
    }

    @Test
    fun laUnidadYLasPalabrasSalenDelValor() {
        val items = rirScale().items
        fun palabra(valor: Int) = effortWord(items.first { it.numericValue == valor.toDouble() })
        assertEquals("cómodo", palabra(4))
        assertEquals("firme", palabra(3))
        assertEquals("exigente", palabra(2))
        assertEquals("muy duro", palabra(1))
        assertEquals("al límite", palabra(0))
        assertEquals("todo", effortWord(items.last()))
        // RPE 8 es lo mismo que RIR 2.
        assertEquals("exigente", effortWord(rpeScale().items.first { it.numericValue == 8.0 }))
        assertNull(effortWord(buildEffortScale(IntensityMode.SOLO_RM, null, null, null).items.first()))
    }

    @Test
    fun elValorSeEscribeConComaDecimalYSuUnidad() {
        val rpe = rpeScale().items
        assertEquals("RPE 7,5", effortValueLabel(rpe.first { it.numericValue == 7.5 }))
        assertEquals("RPE 8", effortValueLabel(rpe.first { it.numericValue == 8.0 }))
        assertEquals("RIR 2", effortValueLabel(rirScale().items.first { it.numericValue == 2.0 }))
        assertEquals("Fallo", effortValueLabel(rpe.last()))
        assertEquals("Esfuerzo RIR 2, exigente", effortSpokenDescription(rirScale().items.first { it.numericValue == 2.0 }))
        assertEquals("Esfuerzo sin marcar", effortSpokenDescription(null))
    }

    @Test
    fun elFuegoSubeConElEsfuerzoYElFalloEsElMaximo() {
        val items = rirScale().items
        val niveles = items.map { effortFireLevel(it) }
        assertEquals("lo cómodo no arde", 0f, niveles.first(), 0f)
        assertEquals(5f, niveles.last(), 0f)
        assertTrue(effortFireLevel(items.first { it.numericValue == 2.0 }) < effortFireLevel(items.first { it.numericValue == 0.0 }))
        assertEquals(0, effortParticleCount(0f))
        assertEquals(16, effortParticleCount(5f))
        assertTrue(effortParticleCount(3f) < effortParticleCount(4f))
        assertTrue(effortParticlePeriodSeconds(5f) < effortParticlePeriodSeconds(2f))
    }

    @Test
    fun elPulgarEmpiezaAlaIzquierdaYLaUltimaMarcaEstaAlaDerecha() {
        assertEquals(0f, effortThumbFraction(0, 12), 0f)
        assertEquals(1f, effortThumbFraction(11, 12), 0f)
        assertEquals(0f, effortThumbFraction(0, 1), 0f)
        assertEquals(0.5f, effortThumbFraction(1, 3), 0.0001f)
    }

    @Test
    fun tocarUnPuntoDeLaPistaElige_LaMarcaMasCercana() {
        val ancho = 252f
        val inset = 23f
        assertEquals("el borde izquierdo es el mínimo", 0, effortIndexAtX(0f, ancho, inset, 12))
        assertEquals(0, effortIndexAtX(inset, ancho, inset, 12))
        assertEquals("el borde derecho es la última", 11, effortIndexAtX(ancho, ancho, inset, 12))
        assertEquals(11, effortIndexAtX(ancho - inset, ancho, inset, 12))
        val centro = effortCenterPx(5, 12, ancho, inset)
        assertEquals(5, effortIndexAtX(centro, ancho, inset, 12))
        assertEquals("un poco a un lado sigue siendo la misma marca", 5, effortIndexAtX(centro + 3f, ancho, inset, 12))
        assertEquals(0, effortIndexAtX(100f, ancho, inset, 1))
    }

    @Test
    fun arrastrarRecorreTodasLasMarcasEnOrden() {
        val ancho = 252f
        val inset = 23f
        val recorrido = (0..252 step 2).map { effortIndexAtX(it.toFloat(), ancho, inset, 12) }
        assertEquals(0, recorrido.first())
        assertEquals(11, recorrido.last())
        assertEquals((0..11).toList(), recorrido.distinct())
        assertTrue("el arrastre nunca retrocede", recorrido.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun alReabrirUnaSerieElSliderMuestraLoQueSeRegistro() {
        val items = rpeScale().items
        assertEquals("el valor con coma", 15, restoreEffortIndex(items, false, "7,5", IntensityMode.RPE))
        assertEquals("el valor con punto", 15, restoreEffortIndex(items, false, "7.5", IntensityMode.RPE))
        assertEquals(items.indexOfFirst { it.isFailure }, restoreEffortIndex(items, true, "", IntensityMode.RPE))
        assertNull("sin nada que mostrar sigue sin marcar", restoreEffortIndex(items, false, "", IntensityMode.RPE))
        assertNull(restoreEffortIndex(emptyList(), false, "8", null))
        // En una lista que mezcla RPE y RIR manda la unidad.
        val mezcla = buildEffortScale(IntensityMode.FAILURE, IntensityMode.FAILURE, null, null).items
        val rir2 = restoreEffortIndex(mezcla, false, "2", IntensityMode.RIR)!!
        assertEquals(IntensityMode.RIR, mezcla[rir2].mode)
    }

    @Test
    fun enUnaSerieAmrapElEsfuerzoSeGuardaComoFalloOReserva() {
        val items = rirScale().items
        val fallo = amrapEffortFor(items.last())
        assertTrue(fallo.reachedFailure)
        assertNull(fallo.reserveReps)
        val rir1 = amrapEffortFor(items.first { it.numericValue == 1.0 })
        assertFalse(rir1.reachedFailure)
        assertEquals(1, rir1.reserveReps)
        val rpe9 = amrapEffortFor(rpeScale().items.first { it.numericValue == 9.0 })
        assertEquals("RPE 9 es una reserva de 1", 1, rpe9.reserveReps)
    }

    @Test
    fun lasEtiquetasNoSePisanYElPlanSiempreSeConserva() {
        val escala = rirScale(plan = 2)
        val candidatas = effortLabelCandidates(escala)
        assertTrue(candidatas.any { it.text.endsWith("plan") })
        assertTrue(candidatas.any { it.text == "Fallo" })
        listOf(252f, 200f, 160f).forEach { ancho ->
            val widths = candidatas.associate { it.index to 40f }
            val centers = candidatas.associate { it.index to effortCenterPx(it.index, escala.items.size, ancho, 23f) }
            val colocadas = placeEffortLabels(candidatas, widths, centers, ancho, gapPx = 6f)
            assertTrue("el plan se conserva a $ancho", colocadas.any { it.text.endsWith("plan") })
            colocadas.zipWithNext().forEach { (a, b) ->
                assertTrue("se pisan a $ancho: ${a.text} y ${b.text}", a.startPx + a.widthPx + 6f <= b.startPx + 0.001f)
            }
            colocadas.forEach { assertTrue(it.startPx >= 0f && it.startPx + it.widthPx <= ancho + 0.001f) }
        }
    }

    @Test
    fun conEtiquetasCortasSiempreQuedanElMinimoElPlanYElFallo() {
        val escala = rirScale(plan = 2)
        val candidatas = effortLabelCandidates(escala)
        val widths = candidatas.associate { it.index to 24f }
        val centers = candidatas.associate { it.index to effortCenterPx(it.index, escala.items.size, 252f, 23f) }
        val indices = placeEffortLabels(candidatas, widths, centers, 252f, gapPx = 6f).map { it.index }
        assertTrue("el mínimo", 0 in indices)
        assertTrue("el plan", escala.planIndex in indices)
        assertTrue("el fallo", escala.items.lastIndex in indices)
    }

    @Test
    fun elPlanQueCoincideConElMinimoOElFalloSeFundeConEllos() {
        val aFallo = effortLabelCandidates(buildEffortScale(IntensityMode.FAILURE, IntensityMode.FAILURE, null, null))
        assertTrue(aFallo.any { it.text == "Fallo · plan" })
        val rirCero = effortLabelCandidates(rirScale(plan = 0))
        assertTrue(rirCero.any { it.text == "0 · plan" })
    }
}
