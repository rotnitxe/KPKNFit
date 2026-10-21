package com.example.kpkn.screens.nutrition.design.editor

import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.NutritionEditorBase
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Textos y cifras puras del editor de Nutrición: el cambio que se guarda, la meta en kg o lb, las líneas de macros, el aviso de desvío,
 * los anillos y las barras del reparto. Las cifras y sus unidades van unidas con espacios que no se parten (U+00A0).
 */
class NutritionPlanEditorFormatTest {

    private val base = NutritionEditorBase(caloriesKcal = 2150, proteinG = 160, carbsG = 210, fatG = 70)

    @Test
    fun elCambioDeKcalSeLeeComoFlecha() {
        assertEquals("2.300 → 2.150 kcal", editorChangeText(originalKcal = 2300, currentKcal = 2150, dirty = true, isNew = false))
    }

    @Test
    fun sinCambiosPlanNuevoYCambioSinKcal() {
        assertEquals("Sin cambios", editorChangeText(originalKcal = 2300, currentKcal = 2300, dirty = false, isNew = false))
        assertEquals("Plan nuevo · 2.150 kcal", editorChangeText(originalKcal = null, currentKcal = 2150, dirty = true, isNew = true))
        assertEquals("Cambios · 2.300 kcal", editorChangeText(originalKcal = 2300, currentKcal = 2300, dirty = true, isNew = false))
        assertEquals("Plan nuevo · —", editorChangeText(originalKcal = null, currentKcal = null, dirty = true, isNew = true))
    }

    @Test
    fun laMetaDePesoIdaYVueltaEnLibras() {
        val kg = editorWeightTargetKg("160,0", "lb")!!
        assertEquals(72.5747792, kg, 1e-6)
        assertEquals("160,0", editorWeightTargetText(kg, WizardMassUnit.LB))
        assertEquals("72,6", editorWeightTargetText(72.5747792, WizardMassUnit.KG))
        assertEquals(72.5, editorWeightTargetKg("72,5", "kg")!!, 1e-9)
        assertNull(editorWeightTargetKg("", "kg"))
        assertNull(editorWeightTargetKg("0", "kg"))
        assertEquals(WizardMassUnit.LB, editorWeightUnit("lb"))
        assertEquals(WizardMassUnit.KG, editorWeightUnit("kg"))
    }

    @Test
    fun laLineaDeMacrosVaConSusUnidadesPegadas() {
        assertEquals("P 160 · H 210 · G 70 g", editorMacrosLine(base))
        assertEquals("P — · H — · G — g", editorMacrosLine(null))
    }

    @Test
    fun elAvisoDeDesvioSoloSaleSiPasaDelDosPorCiento() {
        // 2.110 frente a 2.150: 40 kcal, por debajo del 2 % (43): no hay aviso.
        assertNull(editorMacroGapLine(base))
        // 2.110 frente a 2.300: 190 kcal menos, por encima del 2 %.
        assertEquals(
            "Tus macros suman 2.110 kcal, 190 menos que tus calorías.",
            editorMacroGapLine(NutritionEditorBase(2300, 160, 210, 70)),
        )
        // 2.110 frente a 2.000: 110 kcal más, por encima del 2 %.
        assertEquals(
            "Tus macros suman 2.110 kcal, 110 más que tus calorías.",
            editorMacroGapLine(NutritionEditorBase(2000, 160, 210, 70)),
        )
        // Sin calorías, o sin macros, no hay nada que comparar.
        assertNull(editorMacroGapLine(NutritionEditorBase(0, 0, 0, 0)))
        assertNull(editorMacroGapLine(NutritionEditorBase(2000, 0, 0, 0)))
    }

    @Test
    fun elRepartoDeEnergiaSumaCienYSeLee() {
        val percents = editorEnergyPercents(base)!!
        assertEquals(Triple(30, 40, 30), percents)
        assertEquals("30 % · 40 % · 30 %", editorEnergyLegend(percents))
        assertNull(editorEnergyPercents(null))
        assertNull(editorEnergyPercents(NutritionEditorBase(0, 0, 0, 0)))
    }

    @Test
    fun sinBaseLosAnillosEstanVaciosYConMarca() {
        val rings = editorRingFractions(null, eerKcal = 2000.0)
        assertEquals(0f, rings.calories, 0f)
        assertEquals(0f, rings.protein, 0f)
        assertEquals(0.8f, rings.referenceMark!!, 1e-6f)
    }

    @Test
    fun elAnilloExteriorSeMideContraElMantenimiento() {
        val rings = editorRingFractions(base, eerKcal = 2000.0)
        // 2.150 kcal frente a 2.000 de mantenimiento: 1,075 sobre la escala de 1,25 del wizard.
        assertEquals(0.86f, rings.calories, 1e-4f)
        assertEquals(0.30f, rings.protein, 1e-6f)
        assertEquals(0.40f, rings.carbs, 1e-6f)
        assertEquals(0.30f, rings.fat, 1e-6f)
    }

    @Test
    fun laDireccionDiceQuienLaDefine() {
        assertEquals("La define tu profesional", editorDirectionLine(PlanDirection.PROFESSIONAL))
        assertEquals("Elige una dirección para calcular la base.", editorDirectionLine(null))
    }

    @Test
    fun lasBarrasSonProporcionalesConMinimoDelTreintaPorCiento() {
        val week = listOf(2150, 2080, 2400, 1950)
        val fractions = editorBarFractions(week)
        assertEquals(2150f / 2400f, fractions[0], 1e-4f)
        assertEquals(2080f / 2400f, fractions[1], 1e-4f)
        assertEquals(1f, fractions[2], 1e-6f)
        assertEquals(1950f / 2400f, fractions[3], 1e-4f)
        // Un día con muy pocas kcal no desaparece: se queda en el 30 %.
        assertEquals(listOf(0.30f, 1f), editorBarFractions(listOf(500, 2000)))
        // Sin kcal, todas al mínimo.
        assertEquals(listOf(0.30f, 0.30f), editorBarFractions(listOf(0, 0)))
    }

    @Test
    fun elDiaElegidoSeLeeEnUnaLineaConSusKcal() {
        val monday = NutritionEditorDayTarget(LocalDate.of(2026, 10, 12), 2150, 160, 210, 70)
        assertEquals(
            "Lunes · 2.150 kcal · P 160 · H 210 · G 70 g",
            editorDayLine(monday),
        )
    }

    @Test
    fun laMetaDeHoyVaSinSeparadorDeMiles() {
        assertEquals(
            "La meta de hoy ya quedó fijada en 1800 kcal. Lo que cambies en este plan vale desde mañana.",
            editorTodayGoalLine(1800),
        )
    }
}
