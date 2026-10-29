package com.example.kpkn.screens.nutrition.nutrients

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.Micronutrient
import com.example.kpkn.domain.nutrition.DriProfile
import com.example.kpkn.domain.nutrition.DriSex
import com.example.kpkn.domain.nutrition.FootprintBand
import com.example.kpkn.domain.nutrition.NutrientCoverage
import com.example.kpkn.domain.nutrition.NutrientKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Geometría y selección de la huella radial (Arreglos v1): puras, sin Compose. */
class NutrientRadialTest {
    private val male30 = DriProfile(30, DriSex.MALE)
    private fun food(vararg amounts: Pair<NutrientKey, Double>) =
        LoggedFood(foodName = "Prueba", calories = 500.0, micronutrients = amounts.map { (key, value) -> Micronutrient(key.label, value, key.unit) })
    private fun meal(vararg amounts: Pair<NutrientKey, Double>) = NutrientFootprints.fromFoods(listOf(food(*amounts)), male30, 2000.0)
    private fun day(vararg amounts: Pair<NutrientKey, Double>) =
        NutrientFootprints.fromTotals(NutrientCoverage.summarize(listOf(food(*amounts))), male30, 2000.0, highlights = 0, day = true)

    @Test fun elAnguloSigueLaFormulaDelLienzo() {
        assertEquals(-77.25, radialAngleDegrees(0, 0), 1e-9)
        assertEquals(96.75, radialAngleDegrees(12, 1), 1e-9)
        assertEquals(257.25, radialAngleDegrees(23, 2), 1e-9)
    }

    @Test fun haySeisYVeinteRadiosEnTresGrupos() {
        val spokes = radialSpokes(meal(NutrientKey.IRON to 8.0))
        assertEquals(24, spokes.size)
        assertEquals(listOf(0, 1, 2), FootprintBand.entries.map { it.ordinal })
        assertEquals(radialAngleDegrees(11, 0), spokes[11].angleDegrees, 1e-9)
        assertEquals(radialAngleDegrees(12, 1), spokes[12].angleDegrees, 1e-9)
        assertEquals(radialAngleDegrees(21, 2), spokes[21].angleDegrees, 1e-9)
    }

    @Test fun losTresQueMasAportanLlevanCodigoYNingunTope() {
        val model = meal(NutrientKey.IRON to 16.0, NutrientKey.ZINC to 11.0, NutrientKey.CALCIUM to 500.0, NutrientKey.MAGNESIUM to 40.0, NutrientKey.SODIUM to 4600.0)
        val spokes = radialSpokes(model)
        assertEquals(setOf("Fe", "Zn", "Ca"), spokes.filter { it.labeled }.map { it.sigla }.toSet())
        assertEquals(3, spokes.count { it.labeled })
        assertTrue(spokes.filter { it.labeled }.none { it.ceiling })
        assertEquals(listOf(NutrientKey.IRON.label, NutrientKey.ZINC.label, NutrientKey.CALCIUM.label), radialTopRows(model).map { it.name })
        assertEquals(listOf("200 %", "100 %", "50 %"), radialTopRows(model).map { it.percentText })
    }

    @Test fun enUnaComidaElRestoVaAlCuarentaYCinco() {
        val spokes = radialSpokes(meal(NutrientKey.IRON to 16.0, NutrientKey.ZINC to 11.0, NutrientKey.CALCIUM to 500.0, NutrientKey.MAGNESIUM to 40.0))
        val fe = spokes.first { it.sigla == "Fe" }
        val mg = spokes.first { it.sigla == "Mg" }
        assertEquals(1f, fe.opacity, 0f)
        assertEquals(0.45f, mg.opacity, 0f)
        assertEquals(1f, fe.fraction!!, 0f)
        assertEquals(0.5f, spokes.first { it.sigla == "Ca" }.fraction!!, 1e-6f)
    }

    @Test fun sinDatoNoEsUnRadioDeCero() {
        val spokes = radialSpokes(meal(NutrientKey.IRON to 8.0))
        assertNull(spokes.first { it.sigla == "A" }.fraction)
        assertEquals(1f, spokes.first { it.sigla == "Fe" }.fraction!!, 0f)
    }

    @Test fun unTopeEsHuecoYSoloPasadoEsRojoYPleno() {
        val low = radialSpokes(meal(NutrientKey.SODIUM to 1150.0)).first { it.sigla == "Na" }
        assertTrue(low.ceiling)
        assertFalse(low.over)
        assertEquals(0.5f, low.fraction!!, 1e-6f)
        val high = radialSpokes(meal(NutrientKey.SODIUM to 4600.0)).first { it.sigla == "Na" }
        assertTrue(high.ceiling && high.over)
        assertEquals(1f, high.opacity, 0f)
    }

    @Test fun enElDiaLaOpacidadDependeDelNivel() {
        assertEquals(1f, radialDayOpacity(100.0), 0f)
        assertEquals(1f, radialDayOpacity(180.0), 0f)
        assertEquals(0.6f, radialDayOpacity(99.9), 0f)
        assertEquals(0.6f, radialDayOpacity(70.0), 0f)
        assertEquals(0.32f, radialDayOpacity(69.9), 0f)
        assertEquals(0.32f, radialDayOpacity(null), 0f)
        val spokes = radialSpokes(day(NutrientKey.IRON to 8.0, NutrientKey.ZINC to 8.0, NutrientKey.CALCIUM to 300.0))
        assertEquals(1f, spokes.first { it.sigla == "Fe" }.opacity, 0f)
        assertEquals(0.6f, spokes.first { it.sigla == "Zn" }.opacity, 0f)
        assertEquals(0.32f, spokes.first { it.sigla == "Ca" }.opacity, 0f)
        assertTrue(spokes.none { it.labeled })
    }

    @Test fun laLeyendaCuentaLosNivelesYNombraLosTopesPasados() {
        val legend = radialLegend(day(NutrientKey.IRON to 8.0, NutrientKey.ZINC to 8.0, NutrientKey.CALCIUM to 300.0, NutrientKey.SODIUM to 4600.0))
        assertEquals(RadialLegend(covered = 1, near = 1, below = 1, overMax = listOf(NutrientKey.SODIUM.label), goals = 22), legend)
        val none = radialLegend(day(NutrientKey.SODIUM to 1000.0))
        assertTrue(none.overMax.isEmpty())
        assertEquals(22, none.goals)
    }

    @Test fun sinReferenciaLosRadiosSonRelativosYUniformes() {
        val model = NutrientFootprints.fromFoods(listOf(food(NutrientKey.IRON to 8.0, NutrientKey.SODIUM to 4600.0)), null)
        val spokes = radialSpokes(model)
        assertTrue(spokes.none { it.labeled || it.ceiling || it.over })
        assertEquals(0.6f, spokes.first { it.sigla == "Fe" }.opacity, 0f)
    }

    @Test fun elCodigoSeAlineaSegunElCoseno() {
        assertEquals(RadialAlign.CENTER, radialLabelAlign(0.0))
        assertEquals(RadialAlign.CENTER, radialLabelAlign(0.34))
        assertEquals(RadialAlign.CENTER, radialLabelAlign(-0.34))
        assertEquals(RadialAlign.START, radialLabelAlign(0.35))
        assertEquals(RadialAlign.START, radialLabelAlign(0.9))
        assertEquals(RadialAlign.END, radialLabelAlign(-0.35))
        assertEquals(RadialAlign.END, radialLabelAlign(-0.9))
    }

    @Test fun elLargoTieneUnMinimoDeTresYUnTopeDeCien() {
        assertEquals(3f, radialLength(span = 27f, minLength = 3f, fraction = 0.01f), 0f)
        assertEquals(13.5f, radialLength(span = 27f, minLength = 3f, fraction = 0.5f), 1e-6f)
        assertEquals(27f, radialLength(span = 27f, minLength = 3f, fraction = 4f), 0f)
    }

    @Test fun laEntradaEscalonaVeintiDosMilisegundosPorRadio() {
        assertEquals(1406, radialTotalMs(24))
        assertEquals(0f, radialProgress(0f, 0), 0f)
        assertEquals(1f, radialProgress(900f, 0), 0f)
        assertEquals(0f, radialProgress(22f, 1), 0f)
        assertEquals(0.5f, radialProgress(22f + 450f, 1), 1e-6f)
        assertEquals(1f, radialProgress(1406f, 23), 0f)
        assertTrue(radialProgress(900f, 23) < 1f)
    }
}
