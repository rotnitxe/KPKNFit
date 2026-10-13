package com.example.kpkn.screens.nutrition

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** «Mover a…» del menú de una fila (N2): cambia la comida del registro y nada más, por su id. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NutritionMoveLogTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var nutritionRepo: NutritionRepository
    private lateinit var programRepo: ProgramRepository
    private lateinit var vm: NutritionViewModel
    private val collectors = mutableListOf<Job>()

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
        collectors += testScope.launch { vm.mealGroups.collect { } }
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

    private fun todayLog(id: String) = NutritionLog(
        id = id,
        date = LocalDate.now().toString() + "T12:00:00.000Z",
        mealType = MealType.LUNCH,
        foods = listOf(LoggedFood(id = "food-$id", foodName = "Arroz", amount = 100.0, calories = 200.0, protein = 5.0, carbs = 40.0, fats = 1.0)),
    )

    /** Espera a que el VM publique la condición sobre los registros de hoy (las escrituras van por IO). */
    private fun awaitTodayLogs(condition: (List<NutritionLog>) -> Boolean) {
        val deadline = System.currentTimeMillis() + SLOW_MS
        while (System.currentTimeMillis() < deadline) {
            if (condition(vm.todayLogs.value)) return
            Thread.sleep(10)
        }
        assertTrue("todayLogs did not satisfy condition; value=${vm.todayLogs.value}", condition(vm.todayLogs.value))
    }

    @Test
    fun `mover cambia solo la comida del registro y conserva su id y sus alimentos`() {
        val log = todayLog("move-1")
        kotlinx.coroutines.runBlocking { nutritionRepo.saveNutritionLog(log) }
        awaitTodayLogs { logs -> logs.any { it.id == log.id } }

        vm.moveLog("move-1", MealType.DINNER)

        awaitTodayLogs { logs -> logs.singleOrNull()?.mealType == MealType.DINNER }
        assertEquals(log.copy(mealType = MealType.DINNER), vm.todayLogs.value.single())
        val stored = kotlinx.coroutines.runBlocking { nutritionRepo.databaseForTests().nutritionDao().getAllLogs() }
        assertEquals("la fila se reemplaza, nunca se duplica", listOf("move-1"), stored.map { it.id })
    }

    @Test
    fun `mover un registro que no existe no hace nada y no falla`() {
        vm.moveLog("no-existe", MealType.SNACK)
        assertTrue(vm.todayLogs.value.isEmpty())
        assertNull(vm.uiMessage.value)
    }
}
