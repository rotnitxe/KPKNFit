package com.example.kpkn.screens.nutrition

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.MealReuse
import com.example.kpkn.domain.nutrition.MealReuse.CopyPreset
import com.example.kpkn.testsupport.RoomTestRule
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** N4 · Reutilización del diario desde el ViewModel: copiar un día, repetir ayer, plantillas y comidas planificadas. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NutritionMealReuseViewModelTest {

    @get:Rule
    val roomRule = RoomTestRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var nutritionRepo: NutritionRepository
    private lateinit var programRepo: ProgramRepository
    private lateinit var vm: NutritionViewModel
    private val collectors = mutableListOf<Job>()

    private val today: LocalDate = LocalDate.now()
    private val yesterday: LocalDate = today.minusDays(1)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        NutritionRepository.initForTests(context)
        ProgramRepository.initForTests(context)
        nutritionRepo = NutritionRepository.getInstance()
        programRepo = ProgramRepository.getInstance()
        nutritionRepo.clearNutritionLogs()
        nutritionRepo.setActiveNutritionPlanId(null)
        programRepo.clearPrograms()
        programRepo.clearActiveProgram()
        programRepo.clearOngoingWorkout()

        vm = NutritionViewModel()
        collectors += testScope.launch { vm.todayLogs.collect { } }
        collectors += testScope.launch { vm.dailyTotals.collect { } }
        collectors += testScope.launch { vm.plannedLogs.collect { } }
        collectors += testScope.launch { vm.reuseMessage.collect { } }
    }

    @After
    fun tearDown() {
        collectors.forEach { it.cancel() }
        collectors.clear()
        NutritionRepository.closeInstance()
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    private companion object {
        /** Las escrituras de Room terminan en Dispatchers.IO; con la máquina ocupada 5 s no siempre bastan. */
        const val SLOW_MS = 30_000L
    }

    private fun log(id: String, date: LocalDate, meal: MealType, kcal: Double) = NutritionLog(
        id = id,
        date = "${date}T12:00:00.000Z",
        mealType = meal,
        foods = listOf(LoggedFood(id = "food-$id", foodName = "Alimento $id", amount = 100.0, calories = kcal, protein = 5.0, carbs = 40.0, fats = 1.0)),
    )

    private fun saveNow(vararg logs: NutritionLog) = runBlocking { logs.forEach { nutritionRepo.saveNutritionLog(it) } }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + SLOW_MS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("no se cumplió a tiempo: $what; registros=${nutritionRepo.nutritionLogs.value.size}", condition())
    }

    private fun logsOn(date: LocalDate) = nutritionRepo.nutritionLogs.value.filter { it.date.startsWith(date.toString()) }

    @Test
    fun `copiar un dia a tres fechas lleva tres interacciones y todo queda planificado`() {
        saveNow(log("A", today, MealType.BREAKFAST, 300.0), log("B", today, MealType.LUNCH, 700.0))
        await("los dos registros de hoy") { vm.todayLogs.value.size == 2 }
        val before = vm.dailyTotals.value.calories

        // Interacción 1: abrir «Copiar día a…» (mañana ya viene elegido). 2: «Próximos 3 días». 3: «Copiar».
        var selection = MealReuse.initialSelection(today, setOf(today))
        selection = MealReuse.togglePreset(selection, CopyPreset.NEXT_3_DAYS, today, setOf(today))
        vm.copyDayTo(selection.sorted)

        await("las seis copias") { nutritionRepo.nutritionLogs.value.size == 8 }
        val copies = nutritionRepo.nutritionLogs.value.filter { it.id !in setOf("A", "B") }
        assertEquals(6, copies.size)
        assertTrue(copies.all { it.status == NutritionStatus.PLANNED })
        assertEquals(selection.sorted.map { it.toString() }.toSet(), copies.map { it.date.take(10) }.toSet())
        // Lo futuro no cambia lo comido hoy.
        assertEquals(2, vm.todayLogs.value.size)
        assertEquals(before, vm.dailyTotals.value.calories, 0.0)
        await("el aviso") { vm.reuseMessage.value != null }
        assertFalse(vm.reuseMessage.value!!.isError)
    }

    @Test
    fun `un doble toque en copiar no duplica`() {
        saveNow(log("A", today, MealType.LUNCH, 500.0))
        await("el registro de hoy") { vm.todayLogs.value.size == 1 }
        val tomorrow = today.plusDays(1)
        vm.copyLogsTo(listOf(log("A", today, MealType.LUNCH, 500.0)), listOf(tomorrow))
        vm.copyLogsTo(listOf(log("A", today, MealType.LUNCH, 500.0)), listOf(tomorrow))
        await("la copia") { logsOn(tomorrow).isNotEmpty() }
        vm.copyLogsTo(listOf(log("A", today, MealType.LUNCH, 500.0)), listOf(tomorrow))
        Thread.sleep(300)
        assertEquals(1, logsOn(tomorrow).size)
        val stored = runBlocking { nutritionRepo.databaseForTests().nutritionDao().getAllLogs() }
        assertEquals(2, stored.size)
    }

    @Test
    fun `una copia a una fecha pasada cuenta como consumida y no es futura`() {
        saveNow(log("A", today, MealType.LUNCH, 500.0))
        val past = today.minusDays(3)
        vm.copyLogsTo(listOf(log("A", today, MealType.LUNCH, 500.0)), listOf(past))
        await("la copia") { logsOn(past).isNotEmpty() }
        assertEquals(NutritionStatus.CONSUMED, logsOn(past).single().status)
    }

    @Test
    fun `repetir ayer con el dia anterior vacio avisa y no escribe`() {
        vm.repeatYesterday()
        await("el aviso") { vm.reuseMessage.value != null }
        assertEquals(0, nutritionRepo.nutritionLogs.value.size)
        assertNull(vm.pendingRepeat.value)
    }

    @Test
    fun `repetir ayer con hoy vacio copia sin preguntar`() {
        saveNow(log("Y1", yesterday, MealType.BREAKFAST, 300.0), log("Y2", yesterday, MealType.LUNCH, 600.0))
        vm.repeatYesterday()
        await("las dos copias de hoy") { logsOn(today).size == 2 }
        assertNull(vm.pendingRepeat.value)
        assertTrue(logsOn(today).all { it.status == NutritionStatus.CONSUMED })
        assertEquals(4, nutritionRepo.nutritionLogs.value.size)
    }

    @Test
    fun `repetir ayer con comidas hoy pregunta y al anadir no reemplaza nada`() {
        saveNow(log("Y1", yesterday, MealType.LUNCH, 600.0), log("T1", today, MealType.SNACK, 150.0))
        vm.repeatYesterday()
        await("la pregunta") { vm.pendingRepeat.value != null }
        assertEquals(1, vm.pendingRepeat.value!!.existingMeals)
        // Preguntar no escribe nada.
        assertEquals(2, nutritionRepo.nutritionLogs.value.size)

        vm.cancelRepeat()
        assertNull(vm.pendingRepeat.value)
        assertEquals(2, nutritionRepo.nutritionLogs.value.size)

        vm.repeatYesterday()
        await("la pregunta otra vez") { vm.pendingRepeat.value != null }
        vm.confirmRepeat()
        await("la copia") { logsOn(today).size == 2 }
        assertTrue(logsOn(today).any { it.id == "T1" })
        assertNull(vm.pendingRepeat.value)
        // Lo que ya había sigue igual.
        assertEquals(150.0, nutritionRepo.nutritionLogs.value.first { it.id == "T1" }.foods.single().calories, 0.0)
    }

    @Test
    fun `guardar una comida como plantilla es durable y deja la plantilla publicada`() {
        val source = log("A", today, MealType.LUNCH, 500.0)
        vm.saveLogAsTemplate(source, "  Mi almuerzo ", "tpl-fija")
        await("la plantilla") { vm.mealTemplates.value.isNotEmpty() }
        assertEquals("Mi almuerzo", vm.mealTemplates.value.single().name)
        val stored = runBlocking { nutritionRepo.databaseForTests().nutritionDao().getAllTemplates() }
        assertEquals(1, stored.size)
        vm.saveLogAsTemplate(source, "Mi almuerzo", "tpl-fija")
        Thread.sleep(300)
        assertEquals(1, vm.mealTemplates.value.size)
        // Guardar la plantilla no registra ninguna comida.
        assertTrue(nutritionRepo.nutritionLogs.value.none { it.id == "A" })
    }

    @Test
    fun `un nombre vacio no guarda la plantilla y avisa`() {
        vm.saveLogAsTemplate(log("A", today, MealType.LUNCH, 500.0), "   ", "tpl-x")
        await("el aviso") { vm.reuseMessage.value != null }
        assertTrue(vm.reuseMessage.value!!.isError)
        assertTrue(vm.mealTemplates.value.isEmpty())
    }

    @Test
    fun `pasar una comida planificada de hoy a consumida la cuenta y una futura no se deja`() {
        val planned = log("P1", today, MealType.DINNER, 800.0).copy(status = NutritionStatus.PLANNED)
        val future = log("P2", today.plusDays(2), MealType.DINNER, 800.0).copy(status = NutritionStatus.PLANNED)
        saveNow(planned, future)
        assertEquals(0.0, vm.dailyTotals.value.calories, 0.0)
        vm.markPlannedConsumed("P1")
        await("hoy consumida") { vm.todayLogs.value.any { it.id == "P1" } }
        assertEquals(800.0, vm.dailyTotals.value.calories, 0.0)

        vm.markPlannedConsumed("P2")
        await("el aviso") { vm.reuseMessage.value != null }
        assertTrue(vm.reuseMessage.value!!.isError)
        assertEquals(NutritionStatus.PLANNED, nutritionRepo.nutritionLogs.value.first { it.id == "P2" }.status)
    }

    @Test
    fun `las comidas planificadas del dia elegido salen aparte y no entran en lo comido`() {
        val tomorrow = today.plusDays(1)
        saveNow(log("P1", tomorrow, MealType.LUNCH, 500.0).copy(status = NutritionStatus.PLANNED))
        vm.setSelectedDate(tomorrow.toString())
        await("el plan") { vm.plannedLogs.value.size == 1 }
        assertTrue(vm.todayLogs.value.isEmpty())
        assertEquals(0.0, vm.dailyTotals.value.calories, 0.0)
    }
}
