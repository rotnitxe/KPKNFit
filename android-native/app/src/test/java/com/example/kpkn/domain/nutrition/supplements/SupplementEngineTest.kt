package com.example.kpkn.domain.nutrition.supplements

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.Micronutrient
import com.example.kpkn.domain.nutrition.NutrientCoverage
import com.example.kpkn.domain.nutrition.NutrientKey
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
    fun unSuplementoPropioConservaIdNombreUnidadYNotas() {
        val item = SupplementPilaItem(
            id = "propio-abc12345",
            dose = 2.5,
            customName = "Mi mezcla",
            customUnit = "g",
            notes = "Con el desayuno",
        )
        val def = SupplementCatalog.resolve(item)!!
        assertEquals("propio-abc12345", def.id)
        assertEquals("Mi mezcla", def.name)
        assertEquals("g", def.unit)
        assertEquals("Con el desayuno", item.notes)
        assertNull(SupplementCatalog.byId(item.id))
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
        val take = takes["proteina"]!!.single()
        assertEquals("12:55", take.time)
        assertEquals(25.0, take.dose, 1e-9)
        assertEquals("l1", take.logId)
        assertEquals("sup:proteina", take.foodId)
        assertEquals(0, take.foodIndex)
        assertEquals("2026-10-29", take.date)
        assertTrue(SupplementEngine.takesFromLogs(listOf(log), "2026-10-28").isEmpty())
        assertNotNull(SupplementEngine.buildTakeLog(def("cafeina"), 100.0, "08:00", "2026-10-29", "l2").foods.single().caffeineMg.takeIf { it == 100.0 })
    }

    @Test
    fun losMicronutrientesDeclaradosSumanEnSusUnidades() {
        val cases = listOf(Triple("vitd", NutrientKey.VITAMIN_D, 10.0), Triple("magnesio", NutrientKey.MAGNESIUM, 300.0),
            Triple("zinc", NutrientKey.ZINC, 10.0), Triple("omega3", NutrientKey.EPA_DHA, 1.0))
        cases.forEach { (id, key, dose) ->
            val food = SupplementEngine.buildTakeLog(def(id), dose, "08:00", "2026-10-29", id).foods.single()
            assertEquals(id, dose, NutrientCoverage.summarize(listOf(food), key).totalKnown!!, 1e-9)
        }
    }

    @Test
    fun techoDeZincIncluyeComidasYTomasAntiguasPeroNoPlanes() {
        val date = "2026-10-29"
        val food = NutritionLog(id = "food", date = date, foods = listOf(LoggedFood(foodName = "Comida", micronutrients = listOf(Micronutrient("Zinc", 25.0, "mg")))))
        val legacy = NutritionLog(id = "old", date = date, foods = listOf(LoggedFood(id = "sup:zinc", amount = 10.0, unit = "mg")))
        val planned = food.copy(id = "planned", status = NutritionStatus.PLANNED)
        val known = SupplementEngine.knownCeilingTotal(def("zinc"), listOf(food, legacy, planned), date)
        assertEquals(35.0, known, 1e-9)
        assertTrue(SupplementEngine.ceiling(def("zinc"), known, 10.0)!!.exceeded)
    }

    @Test
    fun techoDeMagnesioNoSumaElMagnesioDeComida() {
        val date = "2026-10-29"
        val food = NutritionLog(id = "food", date = date, foods = listOf(LoggedFood(foodName = "Comida", micronutrients = listOf(Micronutrient("Magnesio", 200.0, "mg")))))
        val take = SupplementEngine.buildTakeLog(def("magnesio"), 300.0, "08:00", date, "take")
        assertEquals(300.0, SupplementEngine.knownCeilingTotal(def("magnesio"), listOf(food, take), date), 1e-9)
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
    fun opcionesDeCuandoConservanLaFechaRealAlCruzarMedianoche() {
        val noon = SupplementEngine.whenOptions(java.time.LocalDateTime.of(2026, 10, 29, 12, 55))
        assertEquals(listOf("12:55", "12:25", "11:55", "08:30"), noon.map { it.time })
        assertTrue(noon.all { it.date == "2026-10-29" })

        // A las 00:10, «Hace 30 min» es AYER a las 23:40; no se recoloca al día de hoy.
        val midnight = SupplementEngine.whenOptions(java.time.LocalDateTime.of(2026, 10, 30, 0, 10))
        assertEquals("23:40", midnight[1].time)
        assertEquals("2026-10-29", midnight[1].date)
        assertEquals("23:10", midnight[2].time)
        assertEquals("2026-10-29", midnight[2].date)
        assertEquals("Ayer por la mañana", midnight[3].label)
        assertEquals("2026-10-29", midnight[3].date)

        // A las 00:40 «Hace 30 min» sigue siendo del mismo día.
        val afterMidnight = SupplementEngine.whenOptions(java.time.LocalDateTime.of(2026, 10, 30, 0, 40))
        assertEquals("00:10", afterMidnight[1].time)
        assertEquals("2026-10-30", afterMidnight[1].date)
    }

    @Test
    fun elBorradoUsaLaIdentidadExactaYConservaOtrosAlimentos() {
        val date = "2026-10-29"
        val first = SupplementEngine.buildTakeLog(def("creatina"), 5.0, "08:00", date, "same")
        val other = LoggedFood(id = "food", foodName = "Avena", amount = 80.0, calories = 300.0)
        val shared = first.copy(foods = first.foods + other)
        val takes = SupplementEngine.takesFromLogs(listOf(shared), date)["creatina"]!!
        val removed = SupplementEngine.removeTake(shared, takes.single())!!
        assertEquals(listOf("food"), removed.foods.map { it.id })
        assertEquals(1, removed.foods.size)
    }

    @Test
    fun dosTomasIgualesSeDistinguenPorSuPosicion() {
        val date = "2026-10-29"
        val log = NutritionLog(
            id = "two", date = date,
            foods = listOf(
                LoggedFood(id = "sup:creatina", foodName = "Creatina", amount = 5.0, creatineG = 5.0),
                LoggedFood(id = "sup:creatina", foodName = "Creatina", amount = 5.0, creatineG = 5.0),
            ),
        )
        val takes = SupplementEngine.takesFromLogs(listOf(log), date)["creatina"]!!
        assertEquals(2, takes.size)
        assertEquals(listOf(0, 1), takes.map { it.foodIndex })
        assertEquals("two#1", takes[1].key)
        val after = SupplementEngine.removeTake(log, takes[1])!!
        assertEquals(1, after.foods.size)
        assertEquals("two#0", SupplementEngine.takesFromLogs(listOf(after), date)["creatina"]!!.single().key)
    }

    @Test
    fun laCreatinaAntiguaSePuedeQuitarDeSuRegistro() {
        val date = "2026-10-29"
        val legacy = NutritionLog(
            id = "old", date = date,
            foods = listOf(LoggedFood(id = "gen106", foodName = "Creatina", amount = 1.0, creatineG = 5.0)),
        )
        val take = SupplementEngine.takesFromLogs(listOf(legacy), date)["creatina"]!!.single()
        assertEquals("gen106", take.foodId)
        assertNull(SupplementEngine.removeTake(legacy, take))
    }

    @Test
    fun unaTomaDeCadaDiaNoCuentaComoHechaConDosDosis() {
        val item = SupplementPilaItem("creatina", 5.0, doses = 3)
        val takes = listOf(SupplementTake("08:00", 5.0, "a", "sup:creatina", 0, "2026-10-29"))
        assertFalse(SupplementEngine.isDone(item, takes))
        assertTrue(SupplementEngine.isDone(item, takes + takeAt("13:00") + takeAt("20:00")))
        assertTrue(SupplementEngine.isDone(item.copy(doses = 1), takes))
    }

    private fun takeAt(time: String) = SupplementTake(time, 5.0, "l$time", "sup:creatina", 0, "2026-10-29")

    @Test
    fun tocaAhoraSoloDespuesDelRecordatorioYDentroDeLaVentana() {
        assertTrue(SupplementEngine.isDueNow("12:30", "12:55"))
        assertFalse(SupplementEngine.isDueNow("13:00", "12:55"))
        assertFalse(SupplementEngine.isDueNow("10:00", "12:55"))
        assertFalse(SupplementEngine.isDueNow(null, "12:55"))
        assertFalse(SupplementEngine.isDueNow("xx", "12:55"))
    }
}
