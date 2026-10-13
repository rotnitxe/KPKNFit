package com.example.kpkn.screens.nutrition.design.editor

import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.NutritionEditorBase
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Textos y cifras puras del editor de Nutrición: el cambio que se guarda, la meta en kg o lb, las líneas de macros y los anillos. */
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
    fun laLineaDeMacrosYLaBaseRevisada() {
        assertEquals("P 160 · H 210 · G 70 g", editorMacrosLine(base))
        assertEquals("P — · H — · G — g", editorMacrosLine(null))
        assertEquals(
            "Base revisada que se guardará tal cual: 2150 kcal · P 160 g · H 210 g · G 70 g",
            editorBaseRevisedLine(base),
        )
        assertEquals("Total energético mostrado: 2110 kcal", editorAtwaterLine(base))
        assertEquals(
            "2150 kcal · P 160 g · H 210 g · G 70 g",
            editorBaseRevisedFigures(base),
        )
        assertNull(editorAtwaterLine(NutritionEditorBase(2110, 160, 210, 70)))
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
    fun elDiaElegidoSeLeeEnUnaLineaYHoySeMarca() {
        val monday = NutritionEditorDayTarget(LocalDate.of(2026, 10, 12), 2150, 160, 210, 70)
        val tuesday = NutritionEditorDayTarget(LocalDate.of(2026, 10, 13), 2100, 150, 200, 70)
        assertEquals("Lunes · P 160 · H 210 · G 70 g", editorDayLine(monday))
        val marked = editorWeekDays(listOf(monday, tuesday), today = LocalDate.of(2026, 10, 13))
        assertEquals(listOf(false, true), marked.map { it.isToday })
        assertEquals(2100, marked[1].kcal)
    }

    @Test
    fun laMetaDeHoyVaSinSeparadorDeMiles() {
        assertEquals(
            "La meta de hoy ya quedó fijada en 1800 kcal. Lo que cambies en este plan vale desde mañana.",
            editorTodayGoalLine(1800),
        )
    }
}
