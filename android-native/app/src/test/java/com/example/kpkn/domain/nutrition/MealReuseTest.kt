package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealTemplate
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.Micronutrient
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.domain.nutrition.MealReuse.CopyPlanError
import com.example.kpkn.domain.nutrition.MealReuse.CopyPreset
import com.example.kpkn.domain.nutrition.MealReuse.CopySkipReason
import com.example.kpkn.domain.nutrition.MealReuse.RepeatDecision
import com.example.kpkn.domain.nutrition.expenditure.DayMarkEntry
import com.example.kpkn.domain.nutrition.expenditure.buildDayObservations
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N4 · Reutilización del diario: reglas de copia (ids, estado, hora, nota), plan e idempotencia, repetir el día anterior, selección de
 * fechas, plantillas y entrada rápida. Kotlin puro: corre en el arnés JVM.
 */
class MealReuseTest {

    private val today = LocalDate.of(2026, 10, 21)
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)

    /** Un alimento con cuanto puede traer una instantánea: rango, fuente, nota, micronutrientes y un id que NO existe en ningún catálogo. */
    private fun food(id: String, name: String, kcal: Double, grams: Double = 150.0) = LoggedFood(
        id = id, foodName = name, amount = grams, unit = "g", calories = kcal, protein = kcal / 20.0, carbs = kcal / 10.0, fats = kcal / 40.0,
        fiber = 3.5, sugar = 1.2, sodiumMg = 220.0, potassiumMg = 410.0,
        micronutrients = listOf(Micronutrient("Hierro", 2.5, "mg")),
        caloriesMin = kcal * 0.9, caloriesMax = kcal * 1.1, isUncertain = true,
        evidenceJson = """{"source":"CURATED_LOCAL"}""", nutritionReferenceNote = "Porción habitual",
        interpretationId = "interp-$id", quantity = 2.0,
    )

    private fun log(id: String, date: LocalDate, meal: MealType, vararg foods: LoggedFood, notes: String? = null, time: String = "T12:00:00.000Z", status: NutritionStatus = NutritionStatus.CONSUMED) =
        NutritionLog(id = id, date = "$date$time", mealType = meal, foods = foods.toList(), notes = notes, status = status)

    private val lunch = log("L1", yesterday, MealType.LUNCH, food("f-arroz", "Arroz de un catálogo que ya no existe", 260.0), food("f-pollo", "Pollo", 300.0))
    private val breakfast = log("B1", yesterday, MealType.BREAKFAST, food("f-avena", "Avena", 300.0))

    // ─── Reglas de copia ─────────────────────────────────────────────────────

    @Test
    fun `la copia conserva cantidades y nutrientes tal cual y no mira el catalogo`() {
        val copy = MealReuse.copyLog(lunch, today, today)
        assertEquals(lunch.foods.size, copy.foods.size)
        lunch.foods.zip(copy.foods).forEach { (original, copied) ->
            // Todo igual salvo el id: el alimento viaja entero, aunque su id no exista en ningún catálogo.
            assertEquals(original.copy(id = copied.id), copied)
            assertEquals(original.micronutrients, copied.micronutrients)
            assertEquals(original.caloriesMin, copied.caloriesMin)
            assertEquals(original.evidenceJson, copied.evidenceJson)
        }
        assertEquals(MealType.LUNCH, copy.mealType)
    }

    @Test
    fun `la copia lleva ids nuevos para la comida y para cada alimento`() {
        val copy = MealReuse.copyLog(lunch, today, today)
        assertNotEquals(lunch.id, copy.id)
        assertTrue(copy.foods.map { it.id }.toSet().size == copy.foods.size)
        assertTrue(copy.foods.none { copied -> lunch.foods.any { it.id == copied.id } })
        assertTrue(copy.foods.all { it.id.isNotBlank() })
    }

    @Test
    fun `los ids de la copia son deterministas y cambian con la fecha destino`() {
        val first = MealReuse.copyLog(lunch, today, today)
        val again = MealReuse.copyLog(lunch, today, today)
        assertEquals(first, again)
        val other = MealReuse.copyLog(lunch, tomorrow, today)
        assertNotEquals(first.id, other.id)
        assertTrue(first.foods.map { it.id }.intersect(other.foods.map { it.id }.toSet()).isEmpty())
        assertEquals("copia|L1|$today", MealReuse.copyKey("L1", today))
    }

    @Test
    fun `una copia a hoy o a una fecha pasada es CONSUMED y a una futura es PLANNED`() {
        assertEquals(NutritionStatus.CONSUMED, MealReuse.copyLog(lunch, today, today).status)
        assertEquals(NutritionStatus.CONSUMED, MealReuse.copyLog(lunch, today.minusDays(40), today).status)
        assertEquals(NutritionStatus.PLANNED, MealReuse.copyLog(lunch, tomorrow, today).status)
        assertEquals(NutritionStatus.PLANNED, MealReuse.copyLog(lunch, today.plusDays(30), today).status)
    }

    @Test
    fun `la copia usa la hora del original si es valida y el mediodia si no`() {
        val early = log("E1", yesterday, MealType.BREAKFAST, food("a", "Avena", 300.0), time = "T08:30:00.000Z")
        assertEquals("${today}T08:30:00.000Z", MealReuse.copyLog(early, today, today).date)
        val noSeconds = log("E2", yesterday, MealType.BREAKFAST, food("a", "Avena", 300.0), time = "T07:15")
        assertEquals("${today}T07:15", MealReuse.copyLog(noSeconds, today, today).date)
        val broken = early.copy(id = "E3", date = "${yesterday}T27:99:00.000Z")
        assertEquals("$today${MealReuse.DEFAULT_TIME_SUFFIX}", MealReuse.copyLog(broken, today, today).date)
        val dateOnly = early.copy(id = "E4", date = yesterday.toString())
        assertEquals("$today${MealReuse.DEFAULT_TIME_SUFFIX}", MealReuse.copyLog(dateOnly, today, today).date)
    }

    @Test
    fun `la nota no acumula la marca de duplicado`() {
        assertNull(MealReuse.copyNote(null))
        assertNull(MealReuse.copyNote("   "))
        assertEquals("Cena con amigos (duplicado)", MealReuse.copyNote("Cena con amigos"))
        assertEquals("Cena con amigos (duplicado)", MealReuse.copyNote("Cena con amigos (duplicado)"))
        assertEquals("Cena con amigos (duplicado)", MealReuse.copyNote("Cena con amigos (duplicado) (duplicado)"))
        assertEquals("(duplicado)", MealReuse.copyNote("(duplicado)"))
        val once = MealReuse.copyNote("Nota")
        assertEquals(once, MealReuse.copyNote(once))
    }

    @Test
    fun `copiar una copia no apila la marca en el registro`() {
        val noted = lunch.copy(notes = "Almuerzo del lunes")
        val first = MealReuse.copyLog(noted, today, today)
        val second = MealReuse.copyLog(first, tomorrow, today)
        assertEquals("Almuerzo del lunes (duplicado)", first.notes)
        assertEquals("Almuerzo del lunes (duplicado)", second.notes)
    }

    @Test
    fun `duplicar conserva su firma y no apila la marca en la nota`() {
        val once = duplicateLog(lunch.copy(notes = "Nota original"), "2026-10-25")
        assertEquals("Nota original (duplicado)", once.notes)
        assertEquals("2026-10-25", once.date.take(10))
        assertEquals(NutritionStatus.CONSUMED, once.status)
        assertNotEquals(lunch.id, once.id)
        assertEquals("Nota original (duplicado)", duplicateLog(once, "2026-10-26").notes)
        assertNull(duplicateLog(lunch, "2026-10-25").notes)
    }

    // ─── Plan de copia ───────────────────────────────────────────────────────

    @Test
    fun `un dia a tres fechas da seis copias ordenadas por fecha y por comida`() {
        val plan = MealReuse.planCopy(listOf(lunch, breakfast), listOf(today.plusDays(3), today, tomorrow), today)
        assertTrue(plan.isRunnable)
        assertEquals(6, plan.copies.size)
        assertEquals(listOf(today, today, tomorrow, tomorrow, today.plusDays(3), today.plusDays(3)), plan.copies.map { it.targetDate })
        // Dentro de cada fecha, primero el desayuno.
        assertEquals(MealType.BREAKFAST, plan.copies[0].log.mealType)
        assertEquals(MealType.LUNCH, plan.copies[1].log.mealType)
        assertEquals(plan.copies.size, plan.copies.map { it.log.id }.toSet().size)
        assertEquals(plan.copies.size, plan.copies.map { it.key }.toSet().size)
    }

    @Test
    fun `una comida no se copia al dia en el que ya esta`() {
        val plan = MealReuse.planCopy(listOf(lunch), listOf(yesterday, today), today)
        assertEquals(1, plan.copies.size)
        assertEquals(today, plan.copies.single().targetDate)
        assertEquals(listOf(CopySkipReason.SAME_DAY), plan.skipped.map { it.reason })
    }

    @Test
    fun `una comida que no se podria guardar se descarta y se dice`() {
        val broken = log("X1", yesterday, MealType.SNACK, food("z", "Roto", 100.0).copy(amount = 0.0))
        val empty = log("X2", yesterday, MealType.SNACK)
        val plan = MealReuse.planCopy(listOf(broken, empty, lunch), listOf(today), today)
        assertEquals(1, plan.copies.size)
        assertEquals(listOf(CopySkipReason.INVALID_FOODS, CopySkipReason.INVALID_FOODS), plan.skipped.map { it.reason })
    }

    @Test
    fun `el plan rechaza sin comidas, sin fechas, demasiadas fechas o fechas lejanas`() {
        assertEquals(CopyPlanError.NO_SOURCES, MealReuse.planCopy(emptyList(), listOf(today), today).error)
        assertEquals(CopyPlanError.NO_DATES, MealReuse.planCopy(listOf(lunch), emptyList(), today).error)
        val many = (1L..32L).map { today.plusDays(it) }
        assertEquals(CopyPlanError.TOO_MANY_DATES, MealReuse.planCopy(listOf(lunch), many, today).error)
        assertEquals(CopyPlanError.DATE_OUT_OF_RANGE, MealReuse.planCopy(listOf(lunch), listOf(today.plusDays(400)), today).error)
        val ok = MealReuse.planCopy(listOf(lunch), many.take(31), today)
        assertEquals(31, ok.copies.size)
        assertTrue(MealReuse.planCopy(listOf(lunch), many, today).copies.isEmpty())
    }

    @Test
    fun `una fecha repetida en la peticion se copia una sola vez`() {
        val plan = MealReuse.planCopy(listOf(lunch), listOf(today, today, today), today)
        assertEquals(1, plan.copies.size)
    }

    // ─── Ejecución: idempotencia y fallos ────────────────────────────────────

    @Test
    fun `ejecutar dos veces el mismo plan no duplica nada`() = runBlocking {
        val plan = MealReuse.planCopy(listOf(lunch, breakfast), listOf(today, tomorrow), today)
        val store = linkedMapOf<String, NutritionLog>()
        val first = MealReuse.runPlan(plan, store.keys.toSet()) { store[it.id] = it }
        assertEquals(4, first.saved.size)
        assertEquals(4, store.size)
        // Doble toque o reintento: el estado publicado ya trae esos ids y no se escribe nada.
        val second = MealReuse.runPlan(plan, store.keys.toSet()) { store[it.id] = it }
        assertTrue(second.saved.isEmpty())
        assertEquals(4, second.alreadyThere.size)
        assertEquals(4, store.size)
        // Y aunque el estado aún no se hubiera publicado, el mismo id reemplaza (upsert) en vez de duplicar.
        MealReuse.runPlan(plan, emptySet()) { store[it.id] = it }
        assertEquals(4, store.size)
    }

    @Test
    fun `una copia ya editada no se pisa al repetir la operacion`() = runBlocking {
        val plan = MealReuse.planCopy(listOf(lunch), listOf(today), today)
        val copy = plan.copies.single().log
        val edited = copy.copy(foods = copy.foods.map { it.copy(amount = 999.0) })
        val store = mutableMapOf(edited.id to edited)
        MealReuse.runPlan(plan, store.keys) { store[it.id] = it }
        assertEquals(999.0, store.getValue(edited.id).foods.first().amount, 0.0)
    }

    @Test
    fun `un fallo a medias se informa y un reintento solo escribe lo que falto`() = runBlocking {
        val plan = MealReuse.planCopy(listOf(lunch), listOf(today, tomorrow, today.plusDays(2)), today)
        val store = linkedMapOf<String, NutritionLog>()
        val failing = plan.copies[1].log.id
        val first = MealReuse.runPlan(plan, store.keys.toSet()) { if (it.id == failing) error("Room cerrada") else store[it.id] = it }
        assertEquals(2, first.saved.size)
        assertEquals(1, first.failed.size)
        assertEquals(tomorrow, first.failed.single().targetDate)
        val message = MealReuse.reportMessage(first)
        assertTrue(message.isError)
        assertTrue(message.text, message.text.contains("2 de 3"))
        assertTrue(message.text, message.text.contains("22 oct"))
        val retry = MealReuse.runPlan(plan, store.keys.toSet()) { store[it.id] = it }
        assertEquals(1, retry.saved.size)
        assertEquals(2, retry.alreadyThere.size)
        assertEquals(3, store.size)
        assertFalse(MealReuse.reportMessage(retry).isError)
    }

    @Test
    fun `el mensaje cuenta las copias y avisa de las planificadas`() {
        val plan = MealReuse.planCopy(listOf(lunch), listOf(today, tomorrow), today)
        val report = MealReuse.CopyReport(saved = plan.copies)
        val text = MealReuse.reportMessage(report).text
        assertTrue(text, text.startsWith("2 copias guardadas"))
        assertTrue(text, text.contains("planificadas"))
        val onlyToday = MealReuse.reportMessage(MealReuse.CopyReport(saved = plan.copies.take(1)))
        assertEquals("1 copia guardada.", onlyToday.text)
        assertEquals("Ya estaba copiado: no se duplicó nada.", MealReuse.reportMessage(MealReuse.CopyReport(alreadyThere = plan.copies)).text)
    }

    @Test
    fun `la vista previa dice cuantas copias de que`() {
        assertEquals("3 copias de Almuerzo", MealReuse.copyPreview(listOf(lunch), 3))
        assertEquals("1 copia de Almuerzo", MealReuse.copyPreview(listOf(lunch), 1))
        assertEquals("6 copias (2 comidas × 3 fechas)", MealReuse.copyPreview(listOf(lunch, breakfast), 3))
        assertEquals("Elige al menos una fecha.", MealReuse.copyPreview(listOf(lunch), 0))
    }

    // ─── Selección de fechas ─────────────────────────────────────────────────

    @Test
    fun `copiar un dia a tres fechas lleva tres interacciones`() {
        // 1) abrir «Copiar día a…» (mañana ya viene elegido); 2) «Próximos 3 días»; 3) Confirmar.
        val blocked = setOf(yesterday)
        var selection = MealReuse.initialSelection(today, blocked)
        assertEquals(setOf(tomorrow), selection.dates)
        selection = MealReuse.togglePreset(selection, CopyPreset.NEXT_3_DAYS, today, blocked)
        assertEquals(listOf(today.plusDays(1), today.plusDays(2), today.plusDays(3)), selection.sorted)
        val plan = MealReuse.planCopy(listOf(lunch, breakfast), selection.sorted, today)
        assertEquals(6, plan.copies.size)
        assertTrue(plan.copies.all { it.log.status == NutritionStatus.PLANNED })
    }

    @Test
    fun `un atajo ya activo se desactiva y hoy y manana se combinan`() {
        var selection = MealReuse.initialSelection(today)
        selection = MealReuse.togglePreset(selection, CopyPreset.TODAY, today)
        assertEquals(setOf(today, tomorrow), selection.dates)
        assertTrue(MealReuse.isPresetActive(selection, CopyPreset.TODAY, today))
        selection = MealReuse.togglePreset(selection, CopyPreset.TODAY, today)
        assertEquals(setOf(tomorrow), selection.dates)
        assertFalse(MealReuse.isPresetActive(selection, CopyPreset.TODAY, today))
    }

    @Test
    fun `tocar una fecha la marca y la quita y el dia de origen no se puede elegir`() {
        val blocked = setOf(yesterday)
        var selection = MealReuse.DateSelection()
        selection = MealReuse.toggleDate(selection, today.plusDays(5), today, blocked)
        assertEquals(setOf(today.plusDays(5)), selection.dates)
        selection = MealReuse.toggleDate(selection, today.plusDays(5), today, blocked)
        assertTrue(selection.dates.isEmpty())
        val refused = MealReuse.toggleDate(selection, yesterday, today, blocked)
        assertTrue(refused.dates.isEmpty())
        assertNotNull(refused.notice)
        val far = MealReuse.toggleDate(selection, today.plusDays(900), today, blocked)
        assertTrue(far.dates.isEmpty())
        assertNotNull(far.notice)
    }

    @Test
    fun `no se pasa del limite de fechas y se avisa`() {
        var selection = MealReuse.DateSelection((1L..31L).map { today.plusDays(it) }.toSet())
        selection = MealReuse.toggleDate(selection, today.plusDays(32), today)
        assertEquals(31, selection.dates.size)
        assertEquals("Máximo 31 fechas por vez.", selection.notice)
        val viaPreset = MealReuse.togglePreset(MealReuse.DateSelection((10L..40L).map { today.plusDays(it) }.toSet()), CopyPreset.NEXT_3_DAYS, today)
        assertEquals(31, viaPreset.dates.size)
        assertNotNull(viaPreset.notice)
    }

    // ─── Estados futuros y estimador ─────────────────────────────────────────

    @Test
    fun `un dia futuro copiado no cambia los totales de hoy ni la entrada del estimador`() {
        val consumedToday = log("T1", today, MealType.LUNCH, food("a", "Arroz", 600.0))
        val consumedYesterday = log("T0", yesterday, MealType.LUNCH, food("b", "Pollo", 700.0))
        val before = listOf(consumedYesterday, consumedToday)
        val plan = MealReuse.planCopy(listOf(consumedToday), listOf(tomorrow, today.plusDays(2)), today)
        val withPlanned = before + plan.copies.map { it.log }
        assertTrue(plan.copies.all { it.log.status == NutritionStatus.PLANNED })

        assertEquals(computeDailyTotals(before.filter { it.date.startsWith(today.toString()) }).calories,
            computeDailyTotals(withPlanned.filter { it.date.startsWith(today.toString()) }).calories, 0.0)
        assertEquals(0.0, computeDailyTotals(plan.copies.map { it.log }).calories, 0.0)

        val marks = emptyMap<LocalDate, DayMarkEntry>()
        assertEquals(buildDayObservations(before, marks, today), buildDayObservations(withPlanned, marks, today))
        // Aunque el estimador mirara hasta el último día planificado, lo planificado no entra como ingesta.
        val farThrough = today.plusDays(5)
        assertEquals(buildDayObservations(before, marks, farThrough), buildDayObservations(withPlanned, marks, farThrough))
        val plannedDay = buildDayObservations(withPlanned, marks, farThrough).first { it.date == tomorrow }
        assertNull(plannedDay.intakeKcal)
    }

    @Test
    fun `una copia a una fecha pasada sí entra como ingesta consumida`() {
        val source = log("T1", today, MealType.LUNCH, food("a", "Arroz", 600.0))
        val plan = MealReuse.planCopy(listOf(source), listOf(today.minusDays(3)), today)
        val observations = buildDayObservations(plan.copies.map { it.log }, emptyMap(), today)
        assertEquals(600.0, observations.first { it.date == today.minusDays(3) }.intakeKcal!!, 0.0)
    }

    // ─── Repetir ayer ────────────────────────────────────────────────────────

    @Test
    fun `repetir ayer con el dia anterior vacio no hay nada que repetir`() {
        val decision = MealReuse.decideRepeat(emptyList(), today, today)
        assertEquals(RepeatDecision.NothingToRepeat(yesterday), decision)
        val onlyPlanned = listOf(log("P", yesterday, MealType.LUNCH, food("a", "Arroz", 100.0), status = NutritionStatus.PLANNED))
        assertTrue(MealReuse.decideRepeat(onlyPlanned, today, today) is RepeatDecision.NothingToRepeat)
    }

    @Test
    fun `repetir ayer con el dia de hoy vacio copia sin preguntar`() {
        val decision = MealReuse.decideRepeat(listOf(lunch, breakfast), today, today)
        assertTrue(decision is RepeatDecision.Ready)
        val plan = (decision as RepeatDecision.Ready).plan
        assertEquals(2, plan.copies.size)
        assertTrue(plan.copies.all { it.targetDate == today && it.log.status == NutritionStatus.CONSUMED })
    }

    @Test
    fun `repetir ayer con comidas en el dia pregunta y nunca reemplaza`() {
        val existing = log("HOY", today, MealType.SNACK, food("m", "Manzana", 80.0))
        val decision = MealReuse.decideRepeat(listOf(lunch, breakfast, existing), today, today)
        assertTrue(decision is RepeatDecision.NeedsConfirmation)
        val ask = decision as RepeatDecision.NeedsConfirmation
        assertEquals(1, ask.existingMeals)
        // El plan solo añade: ninguna copia lleva el id de lo que ya hay y no hay ninguna operación de borrado en el plan.
        assertTrue(ask.plan.copies.none { it.log.id == existing.id })
        assertEquals(2, ask.plan.copies.size)
    }

    @Test
    fun `un dia con solo comidas planificadas tambien pregunta`() {
        val planned = log("PL", today, MealType.DINNER, food("c", "Cena", 500.0), status = NutritionStatus.PLANNED)
        val decision = MealReuse.decideRepeat(listOf(lunch, planned), today, today)
        assertTrue(decision is RepeatDecision.NeedsConfirmation)
    }

    @Test
    fun `la etiqueta de repetir cambia si no es hoy`() {
        assertEquals("Repetir ayer", MealReuse.repeatLabel(today, today))
        assertEquals("Repetir el día anterior", MealReuse.repeatLabel(yesterday, today))
        assertEquals(yesterday, MealReuse.repeatSourceDate(today))
    }

    // ─── Plantillas ──────────────────────────────────────────────────────────

    @Test
    fun `el nombre de una plantilla se valida y se limpia`() {
        assertEquals(MealReuse.TemplateNameCheck.Valid("Almuerzo base"), MealReuse.validateTemplateName("  Almuerzo   base "))
        assertTrue(MealReuse.validateTemplateName("   ") is MealReuse.TemplateNameCheck.Invalid)
        assertTrue(MealReuse.validateTemplateName("x".repeat(61)) is MealReuse.TemplateNameCheck.Invalid)
        assertTrue(MealReuse.validateTemplateName("x".repeat(60)) is MealReuse.TemplateNameCheck.Valid)
        assertEquals("Almuerzo · 20 oct", MealReuse.defaultTemplateName(lunch))
    }

    @Test
    fun `guardar una plantilla conserva la instantanea de cada alimento`() {
        val template = MealReuse.templateFromLog(lunch, "Mi almuerzo", "tpl-1", "2026-10-21T10:00:00Z")
        assertNotNull(template)
        template!!
        assertEquals("tpl-1", template.id)
        assertEquals(2, template.foods.size)
        lunch.foods.zip(template.foods).forEach { (original, saved) -> assertEquals(original.copy(id = saved.id), saved) }
        assertEquals(560.0, template.totalCalories, 0.0)
        // Mismo id, mismos alimentos: guardar otra vez es el mismo registro, no uno nuevo.
        assertEquals(template, MealReuse.templateFromLog(lunch, "Mi almuerzo", "tpl-1", "2026-10-21T10:00:00Z"))
        assertNull(MealReuse.templateFromLog(log("V", yesterday, MealType.LUNCH), "Vacía", "tpl-2", "2026-10-21T10:00:00Z"))
    }

    @Test
    fun `aplicar una plantilla da alimentos editables con ids nuevos aunque falten del catalogo`() {
        val template = MealReuse.templateFromLog(lunch, "Mi almuerzo", "tpl-1", "2026-10-21T10:00:00Z")!!
        var counter = 0
        val drafted = MealReuse.templateDraftFoods(template) { "draft-${counter++}" }
        assertEquals(listOf("draft-0", "draft-1"), drafted.map { it.id })
        assertEquals(template.foods.map { it.copy(id = "") }, drafted.map { it.copy(id = "") })
        // Cada alimento se puede abrir como tarjeta editable y cambiar de cantidad, sin catálogo.
        val tags = drafted.map(::loggedFoodToEditableTag)
        assertTrue(tags.all { it.interpretationV2?.canFinalize() == true })
        val edited = rescaleEstimatedFood(tags.first().loggedFood!!, 300.0)
        assertEquals(300.0, edited.amount, 0.0)
        assertEquals(drafted.first().calories * 2.0, edited.calories, 0.001)
    }

    @Test
    fun `una plantilla con un alimento roto lo deja fuera del borrador`() {
        val template = MealTemplate(id = "t", name = "Mixta", foods = listOf(food("a", "Bien", 100.0), food("b", "Mal", 100.0).copy(amount = Double.NaN)))
        assertEquals(1, MealReuse.templateDraftFoods(template).size)
    }

    @Test
    fun `renombrar valida el nombre y no toca los alimentos`() {
        val template = MealReuse.templateFromLog(lunch, "Mi almuerzo", "tpl-1", "2026-10-21T10:00:00Z")!!
        val renamed = MealReuse.renameTemplate(template, "  Comida del lunes ")!!
        assertEquals("Comida del lunes", renamed.name)
        assertEquals(template.foods, renamed.foods)
        assertEquals(template.id, renamed.id)
        assertNull(MealReuse.renameTemplate(template, "   "))
    }

    @Test
    fun `la lista de plantillas resume kcal macros y alimentos y oculta las vacias`() {
        val full = MealReuse.templateFromLog(lunch, "Mi almuerzo", "tpl-1", "2026-10-21T10:00:00Z")!!
        val older = MealReuse.templateFromLog(breakfast, "Desayuno", "tpl-0", "2026-10-01T10:00:00Z")!!
        val empty = MealTemplate(id = "e", name = "Registro vacío", createdAt = "2026-10-22T00:00:00Z")
        val usable = MealReuse.usableTemplates(listOf(older, empty, full))
        assertEquals(listOf("tpl-1", "tpl-0"), usable.map { it.id })
        val summary = MealReuse.summarize(full)
        assertEquals("Mi almuerzo", summary.name)
        assertEquals(560, summary.kcal)
        assertEquals(2, summary.foodCount)
        assertEquals(28, summary.protein)
    }

    // ─── Entrada rápida ──────────────────────────────────────────────────────

    @Test
    fun `una entrada rapida valida lleva solo kcal y macros y queda marcada`() {
        val result = QuickEntry.validate(QuickEntryForm(name = "  Café   con leche ", kcal = "120,5", protein = "4", carbs = "10", fats = "6"), "q-1")
        assertTrue(result.isValid)
        val food = result.food!!
        assertEquals("Café con leche", food.foodName)
        assertEquals(120.5, food.calories, 0.0)
        assertEquals(4.0, food.protein, 0.0)
        assertTrue(food.isQuickEntry)
        assertEquals("q-1", food.id)
        assertTrue(food.micronutrients.isEmpty())
        assertNull(food.caloriesMin)
        assertFalse(food.isUncertain)
    }

    @Test
    fun `el nombre de la entrada rapida es opcional`() {
        val food = QuickEntry.validate(QuickEntryForm(kcal = "500"), "q").food!!
        assertEquals("Entrada rápida", food.foodName)
        assertEquals(0.0, food.protein, 0.0)
    }

    @Test
    fun `las validaciones de la entrada rapida bloquean lo que no vale`() {
        fun errors(form: QuickEntryForm) = QuickEntry.validate(form, "q").errors
        assertEquals(setOf(QuickEntryField.KCAL), errors(QuickEntryForm()).keys)
        assertEquals(setOf(QuickEntryField.KCAL), errors(QuickEntryForm(kcal = "0")).keys)
        assertEquals(setOf(QuickEntryField.KCAL), errors(QuickEntryForm(kcal = "-5")).keys)
        assertEquals(setOf(QuickEntryField.KCAL), errors(QuickEntryForm(kcal = "abc")).keys)
        assertEquals(setOf(QuickEntryField.KCAL), errors(QuickEntryForm(kcal = "1e3")).keys)
        assertEquals(setOf(QuickEntryField.KCAL), errors(QuickEntryForm(kcal = "10001")).keys)
        assertEquals(setOf(QuickEntryField.PROTEIN), errors(QuickEntryForm(kcal = "500", protein = "-1")).keys)
        assertEquals(setOf(QuickEntryField.CARBS), errors(QuickEntryForm(kcal = "500", carbs = "x")).keys)
        assertEquals(setOf(QuickEntryField.FATS), errors(QuickEntryForm(kcal = "500", fats = "1001")).keys)
        assertEquals(setOf(QuickEntryField.NAME), errors(QuickEntryForm(kcal = "500", name = "n".repeat(61))).keys)
        assertNull(QuickEntry.validate(QuickEntryForm(kcal = "0"), "q").food)
        assertNull(QuickEntry.parseNumber("Infinity"))
        assertNull(QuickEntry.parseNumber("9".repeat(400)))
    }

    @Test
    fun `kcal que no cuadran con los macros avisan pero no bloquean`() {
        val off = QuickEntry.validate(QuickEntryForm(kcal = "900", protein = "10", carbs = "10", fats = "5"), "q")
        assertTrue(off.isValid)
        assertNotNull(off.warning)
        assertTrue(off.warning!!, off.warning!!.contains("125"))
        val fine = QuickEntry.validate(QuickEntryForm(kcal = "500", protein = "30", carbs = "50", fats = "20"), "q")
        assertNull(fine.warning)
        // Sin macros no hay con qué comparar.
        assertNull(QuickEntry.validate(QuickEntryForm(kcal = "900"), "q").warning)
    }

    @Test
    fun `un registro de 500 kcal sin alimento suma 500 en el dia`() {
        val food = QuickEntry.validate(QuickEntryForm(kcal = "500"), "q").food!!
        val entry = log("Q", today, MealType.LUNCH, food)
        val totals = computeDailyTotals(listOf(entry))
        assertEquals(500.0, totals.calories, 0.0)
        assertFalse(totals.isEstimate)
        assertEquals(500.0, computeFoodTotals(entry.foods).calories, 0.0)
        assertEquals(500.0, buildDayObservations(listOf(entry), emptyMap(), today).last().intakeKcal!!, 0.0)
    }

    @Test
    fun `los micronutrientes de una entrada rapida son desconocidos y no ceros`() {
        val quick = QuickEntry.validate(QuickEntryForm(kcal = "500"), "q").food!!
        assertFalse(MealReuse.micronutrientsKnown(quick))
        assertTrue(MealReuse.micronutrientsKnown(food("a", "Arroz", 100.0)))
        assertTrue(MealReuse.micronutrientsKnown(LoggedFood(id = "plain", foodName = "Agua", amount = 250.0)))
    }

    @Test
    fun `el modelo viejo se lee sin el campo nuevo y la marca viaja en el JSON`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
        val old = json.decodeFromString<LoggedFood>("""{"id":"a","foodName":"Arroz","amount":100.0,"calories":130.0}""")
        assertFalse(old.isQuickEntry)
        val quick = QuickEntry.validate(QuickEntryForm(kcal = "500"), "q").food!!
        val roundTrip = json.decodeFromString<LoggedFood>(json.encodeToString(LoggedFood.serializer(), quick))
        assertEquals(quick, roundTrip)
        assertTrue(roundTrip.isQuickEntry)
    }

    @Test
    fun `una entrada rapida entra al registro como tarjeta editable y guarda las mismas kcal`() {
        val quick = QuickEntry.validate(QuickEntryForm(name = "Pizza", kcal = "500"), "q").food!!
        val tag = loggedFoodToEditableTag(quick)
        val interpretation = tag.interpretationV2
        assertNotNull(interpretation)
        assertTrue(interpretation!!.canFinalize())
        assertTrue(interpretation.pendingQuestions.isEmpty())
        val saved = interpretation.toLoggedFood(tag.loggedFood!!)
        assertEquals(500.0, saved.calories, 0.0)
        assertTrue(saved.isQuickEntry)
        assertEquals("Pizza", saved.foodName)
        assertEquals(NutritionSourceKind.USER_PROVIDED, tag.nutritionSource)
    }

    @Test
    fun `una entrada rapida con macros que no cuadran conserva sus kcal al guardarse como tarjeta`() {
        val quick = QuickEntry.validate(QuickEntryForm(kcal = "900", protein = "10", carbs = "10", fats = "5"), "q").food!!
        val tag = loggedFoodToEditableTag(quick)
        val saved = tag.interpretationV2!!.toLoggedFood(tag.loggedFood!!)
        assertEquals(900.0, saved.calories, 0.0)
        assertEquals(10.0, saved.protein, 0.0)
    }
}
