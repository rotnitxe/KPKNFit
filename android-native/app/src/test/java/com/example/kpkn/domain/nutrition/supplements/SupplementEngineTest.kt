package com.example.kpkn.domain.nutrition.supplements

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.SupplementPilaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplementEngineTest {

    private fun def(id: String) = SupplementCatalog.byId(id)!!

    @Test
    fun catalogoTiene11SinLosTresExcluidos() {
        val ids = SupplementCatalog.entries.map { it.id }
        assertEquals(11, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it == "ashwagandha" || it == "hierro" || it == "melatonina" })
        assertTrue(SupplementCatalog.entries.all { it.min <= it.defaultDose && it.defaultDose <= it.max })
        assertTrue(SupplementCatalog.entries.all { it.defaultMoment in SUPPLEMENT_MOMENTS.indices })
    }

    @Test
    fun laPilaSeOrdenaPorMomentoYEsEstable() {
        val pila = listOf(
            SupplementPilaItem("magnesio", 300.0, moment = 6),
            SupplementPilaItem("creatina", 5.0, moment = 1),
            SupplementPilaItem("vitd", 10.0, moment = 1),
            SupplementPilaItem("cafeina", 100.0, moment = 3),
        )
        assertEquals(listOf("creatina", "vitd", "cafeina", "magnesio"), SupplementEngine.sorted(pila).map { it.id })
    }

    @Test
    fun hechoCuandoLasTomasLlegan() {
        val item = SupplementPilaItem("omega3", 1.0, doses = 2)
        assertFalse(SupplementEngine.isDone(item, listOf(SupplementTake("08:00", 1.0))))
        assertTrue(SupplementEngine.isDone(item, listOf(SupplementTake("08:00", 1.0), SupplementTake("13:00", 1.0))))
    }

    @Test
    fun elTechoAvisaSoloAlPasarseYNoBloquea() {
        val caf = def("cafeina")
        val ok = SupplementEngine.ceiling(caf, dayTotalNow = 300.0, dose = 100.0)!!
        assertFalse(ok.exceeded)
        val over = SupplementEngine.ceiling(caf, dayTotalNow = 395.0, dose = 100.0)!!
        assertTrue(over.exceeded)
        assertEquals(495.0, over.projected, 1e-9)
        assertEquals(400.0, over.cap, 1e-9)
        assertNull(SupplementEngine.ceiling(def("creatina"), 5.0, 5.0))
    }

    @Test
    fun elTomaSeRegistraComoComidaYSeLeeDeVuelta() {
        val log = SupplementEngine.buildTakeLog(def("proteina"), 25.0, "12:55", "2026-10-29", "l1")
        val food = log.foods.single()
        assertEquals(25.0, food.protein, 1e-9)
        assertEquals(110.0, food.calories, 1e-9)
        assertTrue(food.isUncertain)
        assertEquals(MealType.SNACK, log.mealType)
        val takes = SupplementEngine.takesFromLogs(listOf(log), "2026-10-29")
        assertEquals(listOf(SupplementTake("12:55", 25.0, "l1")), takes["proteina"])
        assertTrue(SupplementEngine.takesFromLogs(listOf(log), "2026-10-28").isEmpty())
        assertNotNull(SupplementEngine.buildTakeLog(def("cafeina"), 100.0, "08:00", "2026-10-29", "l2").foods.single().caffeineMg.takeIf { it == 100.0 })
    }

    @Test
    fun laCreatinaRapidaDeAntesCuentaComoToma() {
        val legacy = NutritionLog(
            id = "old", date = "2026-10-29", mealType = MealType.SNACK,
            foods = listOf(LoggedFood(id = "gen106", foodName = "Creatina Monohidrato", amount = 5.0, creatineG = 5.0)),
        )
        val planned = legacy.copy(id = "plan", status = NutritionStatus.PLANNED)
        val takes = SupplementEngine.takesFromLogs(listOf(legacy, planned), "2026-10-29")
        assertEquals(1, takes["creatina"]!!.size)
        assertEquals(5.0, takes["creatina"]!!.single().dose, 1e-9)
    }

    @Test
    fun cafeinaDeComidasEsElTotalMenosElSuplemento() {
        val takes = listOf(SupplementTake("08:00", 100.0))
        assertEquals(95.0, SupplementEngine.foodCaffeineMg(195.0, takes), 1e-9)
        assertEquals(0.0, SupplementEngine.foodCaffeineMg(50.0, takes), 1e-9)
    }

    @Test
    fun semanaEsLaFraccionDeLaPilaTomadaCadaDia() {
        val f = SupplementEngine.weekFractions(setOf("a", "b", "c"), listOf(setOf("a", "b", "c"), setOf("a"), emptySet(), setOf("z")))
        assertEquals(listOf(1f, 1f / 3f, 0f, 0f), f)
        assertEquals(listOf(0f), SupplementEngine.weekFractions(emptySet(), listOf(setOf("a"))))
    }

    @Test
    fun formatoDeDosisEnEspanol() {
        assertEquals("100 mg", SupplementEngine.doseLabel(def("cafeina"), 100.0))
        assertEquals("1,6 g", SupplementEngine.doseLabel(def("betaalanina"), 1.6))
        assertEquals("1 porción", SupplementEngine.doseLabel(def("electrolitos"), 1.0))
        assertEquals("2 porciones", SupplementEngine.doseLabel(def("electrolitos"), 2.0))
        assertEquals("1.240", SupplementEngine.formatNumberEs(1240.0))
        assertEquals("0,25", SupplementEngine.formatNumberEs(0.25))
    }

    @Test
    fun elPasoDeDosisRespetaMinimoYMaximo() {
        val caf = def("cafeina")
        assertEquals(125.0, SupplementEngine.stepDose(caf, 100.0, 1), 1e-9)
        assertEquals(25.0, SupplementEngine.stepDose(caf, 25.0, -1), 1e-9)
        assertEquals(400.0, SupplementEngine.stepDose(caf, 400.0, 1), 1e-9)
    }

    @Test
    fun opcionesDeCuando() {
        val opts = SupplementEngine.whenOptions("12:55")
        assertEquals(listOf("12:55", "12:25", "11:55", "08:30"), opts.map { it.second })
        assertEquals("00:10", SupplementEngine.whenOptions("00:40")[1].second)
        assertEquals("23:40", SupplementEngine.whenOptions("00:10")[1].second)
    }

    @Test
    fun tocaAhoraSoloDespuesDelRecordatorioYDentroDeLaVentana() {
        assertTrue(SupplementEngine.isDueNow("12:30", "12:55"))
        assertFalse(SupplementEngine.isDueNow("13:00", "12:55"))
        assertFalse(SupplementEngine.isDueNow("10:00", "12:55"))
        assertFalse(SupplementEngine.isDueNow(null, "12:55"))
        assertFalse(SupplementEngine.isDueNow("xx", "12:55"))
    }
}
