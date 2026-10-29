package com.example.kpkn.screens.nutrition.nutrients

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.Micronutrient
import com.example.kpkn.domain.nutrition.DriProfile
import com.example.kpkn.domain.nutrition.DriSex
import com.example.kpkn.domain.nutrition.NutrientCoverage
import com.example.kpkn.domain.nutrition.NutrientGroup
import com.example.kpkn.domain.nutrition.NutrientKey
import com.example.kpkn.domain.nutrition.footprintSlots
import com.example.kpkn.domain.nutrition.groupedNutrientKeys
import com.example.kpkn.domain.nutrition.nutrients.NutrientId
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NutrientFootprintModelTest {
    private val male30 = DriProfile(30, DriSex.MALE)
    private fun iron(name: String, mg: Double, kcal: Double = 100.0) = LoggedFood(foodName = name, calories = kcal, micronutrients = listOf(Micronutrient("Hierro", mg, "mg")))

    @Test fun lasCifrasSonLasDelLienzo() {
        assertEquals("11", nutrientNumber(11.4))
        assertEquals("11,4", nutrientNumber(11.4, bigFigure = true))
        assertEquals("2.300", nutrientNumber(2300.0))
        assertEquals("0,15", nutrientNumber(0.15))
        assertEquals("26", nutrientNumber(25.555))
        assertEquals("1", nutrientNumber(1.0))
        assertEquals("0", nutrientNumber(0.0))
        assertEquals("10", nutrientNumber(9.96))
        assertEquals("1.234.567", nutrientNumber(1234567.0))
    }

    @Test fun laHuellaDibujaLas24EnOrdenYSinDatoNoEsCero() {
        val model = NutrientFootprints.fromFoods(listOf(iron("Lentejas", 4.0)), male30)
        assertEquals(footprintSlots.map { it.key }, model.bars.map { it.key })
        val fe = model.bars.first { it.key == NutrientKey.IRON }
        assertEquals(50.0, fe.percent!!, 1e-9)
        assertEquals(0.5f, fe.height!!, 1e-6f)
        model.bars.filter { it.key != NutrientKey.IRON }.forEach { assertNull(it.height); assertNull(it.percent) }
    }

    @Test fun lasTresQueMasAportanVanDestacadasSinContarTopes() {
        val model = NutrientsSamples.food().footprint
        assertEquals(3, model.bars.count { it.highlighted })
        assertTrue(model.bars.filter { it.highlighted }.none { it.ceiling })
        assertEquals(listOf("Vitamina A", "Tiamina", "Riboflavina"), model.highlights.filterNot { it.warn }.map { it.name })
        assertEquals(NutrientHighlight("Sodio", "33 % del máx.", true), model.highlights.single { it.warn })
    }

    @Test fun sinReferenciaLaHuellaEsRelativaYNoInventaUn100() {
        val model = NutrientFootprints.fromFoods(listOf(iron("Lentejas", 4.0), LoggedFood(foodName = "Sal", calories = 0.0, sodiumMg = 500.0)), profile = null)
        assertTrue(model.relative)
        assertTrue(model.highlights.isEmpty())
        assertTrue(model.bars.all { it.percent == null })
        val heights = model.bars.mapNotNull { it.height }
        assertEquals(2, heights.size)
        assertEquals(1f, heights.max(), 1e-6f)
        assertTrue(heights.all { it in 0f..1f })
    }

    @Test fun laFichaPor100gSeMueveConLaCantidad() {
        val model = NutrientFootprints.fromPer100g("Lentejas", mapOf(NutrientId.IRON to 2.0, NutrientId.ENERGY to 100.0), 150.0, male30)
        val fe = model.bars.first { it.key == NutrientKey.IRON }
        assertEquals(3.0, fe.amount!!, 1e-9)
        assertEquals(37.5, fe.percent!!, 1e-9)
    }

    @Test fun laListaDelDiaDevuelveLoVigiladoASuGrupoEnOrden() {
        val food = LoggedFood(foodName = "Mucha grasa", calories = 2000.0, fatBreakdown = com.example.kpkn.data.models.FatBreakdown(saturated = 30.0))
        val list = NutrientFootprints.list(NutrientCoverage.summarize(listOf(food)), NutrientsSamples.profile, 2000.0, NutrientScope.DAY)
        assertEquals(listOf(NutrientKey.SAT_FAT), list.watch.map { it.key })
        assertEquals(groupedNutrientKeys().first { it.first == NutrientGroup.FATS }.second, list.orderedRowsOf(NutrientGroup.FATS).map { it.key })
        assertEquals(NutrientKey.entries.size, NutrientGroup.entries.sumOf { list.orderedRowsOf(it).size })
    }

    @Test fun unDatoIncompletoSeMarcaParcial() {
        val state = NutrientsSamples.state("partial")
        assertTrue(state.list.rowsOf(NutrientGroup.MINERALS).first { it.key == NutrientKey.IRON }.partial)
        assertTrue(NutrientsSamples.state("complete").list.groups.flatMap { it.second }.none { it.partial })
    }

    @Test fun lasCuatroCifrasDelDia() {
        val state = NutrientsSamples.state("complete")
        assertEquals(NutrientDayCounts(onTrack = 0, near = 0, below = 22, overMax = 0), NutrientFootprints.dayCounts(state.footprint))
        assertTrue(state.footprint.day)
        assertTrue(state.footprint.bars.none { it.highlighted })
    }

    @Test fun laCoberturaSeDiceEnUnaLinea() {
        val state = NutrientsSamples.state("partial")
        assertEquals("Cobertura: 50 % de las kcal con dato · 1 alimento sin ficha", NutrientFootprints.dayModuleCoverageLine(state.coverage))
        assertNull(NutrientFootprints.dayModuleCoverageLine(NutrientsSamples.state("no_data").coverage))
        assertEquals("1 con ficha completa · 1 estimado", NutrientFootprints.mealCoverageLine(NutrientsSamples.meal().coverage))
    }

    @Test fun loParcialLlevaMayorOIgualYCuantosFaltan() {
        val row = NutrientsSamples.state("partial").list.rowsOf(NutrientGroup.MINERALS).first { it.key == NutrientKey.IRON }
        assertEquals("≥ 9 mg", row.valueText)
        assertEquals("50 %", row.percentText)
        assertEquals("1 alimento sin dato: puede ser más", row.note)
    }

    @Test fun sinDatoSeEscribeAsiYNuncaComoCero() {
        val rows = NutrientsSamples.state("no_data").list.groups.flatMap { it.second }
        assertEquals(NutrientKey.entries.size, rows.size)
        assertTrue(rows.all { it.missing && it.valueText == "sin dato" && it.percentText == null })
    }

    @Test fun loQuePasaUnTopeSubeAVigilar() {
        val food = LoggedFood(foodName = "Mucha grasa", calories = 2000.0, fatBreakdown = com.example.kpkn.data.models.FatBreakdown(saturated = 30.0))
        val totals = NutrientCoverage.summarize(listOf(food))
        val list = NutrientFootprints.list(totals, NutrientsSamples.profile, 2000.0, NutrientScope.DAY)
        val sat = list.watch.single()
        assertEquals(NutrientKey.SAT_FAT, sat.key)
        assertTrue(sat.ceiling && sat.over)
        assertEquals("135 %", sat.percentText)
        assertEquals("Tope de la guía: 10 % de la energía (22 g con 2.000 kcal) · OMS", sat.note)
        assertTrue(list.rowsOf(NutrientGroup.FATS).none { it.key == NutrientKey.SAT_FAT })
    }

    @Test fun elSodioDiceSuMaximoSegunElContexto() {
        val sodium = LoggedFood(foodName = "Caldo", calories = 50.0, sodiumMg = 800.0)
        val totals = NutrientCoverage.summarize(listOf(sodium))
        val day = NutrientFootprints.list(totals, male30, null, NutrientScope.DAY).rowsOf(NutrientGroup.MINERALS).first { it.key == NutrientKey.SODIUM }
        assertEquals("Máximo recomendado: 2.300 mg (CDRR) · AI: 1.500 mg", day.note)
        val food = NutrientFootprints.list(totals, male30, null, NutrientScope.FOOD).rowsOf(NutrientGroup.MINERALS).first { it.key == NutrientKey.SODIUM }
        assertEquals("Tope diario: 2.300 mg (CDRR). Se lee como parte del máximo, no como meta.", food.note)
        assertEquals("35 %", food.percentText)
    }

    @Test fun laFichaCasiCompletaExplicaSuHueco() {
        val all = NutrientKey.entries.filter { it != NutrientKey.IODINE && it != NutrientKey.ALCOHOL }
            .map { Micronutrient(it.label, 1.0, it.unit) }
        val food = LoggedFood(foodName = "Salmón", amount = 150.0, calories = 309.0, micronutrients = all)
        val model = NutrientFootprints.food(food, "1 filete · 150 g", male30)
        val iodine = model.list.rowsOf(NutrientGroup.MINERALS).first { it.key == NutrientKey.IODINE }
        assertEquals("La ficha no trae yodo. No cuenta como 0: tus totales lo dirán.", iodine.note)
        assertEquals("1 filete · 150 g · % de tu día", model.listSubtitle)
        assertNull(model.labelOnlyText)
    }

    @Test fun sinReferenciaDeIngestaSoloElTotal() {
        val food = LoggedFood(foodName = "Salmón", calories = 300.0, micronutrients = listOf(Micronutrient("EPA + DHA", 3.0, "g"), Micronutrient("Colesterol", 95.0, "mg")))
        val fats = NutrientFootprints.list(NutrientCoverage.summarize(listOf(food)), male30, 2300.0, NutrientScope.FOOD).rowsOf(NutrientGroup.FATS)
        val epa = fats.first { it.key == NutrientKey.EPA_DHA }
        assertTrue(epa.noReference)
        assertEquals("—", epa.percentText)
        assertEquals("Sin referencia de ingesta: se muestra el total.", epa.note)
        assertNull(fats.first { it.key == NutrientKey.CHOLESTEROL }.note)
    }

    @Test fun laNiacinaConservaSuNota() {
        val row = NutrientsSamples.state("complete").list.rowsOf(NutrientGroup.VITAMINS).first { it.key == NutrientKey.NIACIN }
        assertEquals("Preformada: con la que forma tu cuerpo, el total real suele ser mayor.", row.note)
    }

    @Test fun laEtiquetaCortaLoDice() {
        val bar = LoggedFood(foodName = "Barrita", calories = 191.0, fiber = 2.1, sodiumMg = 160.0, sugar = 12.0)
        assertEquals(7, NutrientFootprints.nutrientsWithData(bar))
        assertEquals("La etiqueta declara 7 de 38 nutrientes: energía, macros, azúcares, fibra y sal. El resto queda sin dato y no cuenta como 0 en tu día.",
            NutrientFootprints.labelOnlyText(bar))
        val model = NutrientFootprints.food(bar, "1 barrita · 42 g", male30)
        assertTrue(model.footprint.labelOnly)
        assertNotNull(model.labelOnlyText)
    }

    @Test fun laHojaDeUnNutrienteDiceDeDondeSale() {
        val foods = (1..7).map { iron("Alimento $it", it.toDouble()) } + LoggedFood(foodName = "Batido de proteína", calories = 150.0)
        val total = NutrientCoverage.summarize(foods, NutrientKey.IRON)
        val detail = NutrientFootprints.detail(total, male30, null, "hoy")
        assertEquals("Mineral · hoy", detail.eyebrow)
        assertEquals("≥ 28", detail.bigValue)
        assertEquals("mg de 8 mg", detail.ofText)
        assertEquals("350 %", detail.percentText)
        assertEquals("Otros alimentos (3)", detail.contributors.last().name)
        assertEquals(1f, detail.contributors.first().fraction, 1e-6f)
        assertEquals("Batido de proteína no trae dato de hierro: el total puede ser mayor.", detail.missingNote)
        assertEquals(listOf("RDA" to "8 mg", "Límite superior total" to "45 mg"), detail.references)
        assertEquals("Academias Nacionales de EE. UU. No sustituye el consejo de un profesional.", detail.sourceLine)
    }

    @Test fun laFechaYLaReferenciaVanEnElSubtitulo() {
        val today = LocalDate.of(2026, 10, 28)
        assertEquals("Hoy · % de la referencia de tu perfil", NutrientFootprints.daySheetSubtitle("2026-10-28", male30, today))
        assertEquals("Sábado 24 · % de la referencia de tu perfil", NutrientFootprints.daySheetSubtitle("2026-10-24", male30, today))
        assertEquals("Hoy · cantidades relativas", NutrientFootprints.daySheetSubtitle("2026-10-28", null, today))
        assertEquals("hoy", NutrientFootprints.dayContext("2026-10-28", LocalDate.of(2026, 10, 28)))
        assertEquals("sábado 24", NutrientFootprints.dayContext("2026-10-24", LocalDate.of(2026, 10, 28)))
    }

    @Test fun loQueMasSumoSinPorcentajes() {
        assertEquals("Lo que más sumó: vitamina A, tiamina y riboflavina", NutrientFootprints.savedLine(NutrientsSamples.food().footprint))
    }
}
