package com.example.kpkn.screens.nutrition

import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** El historial de Nutrición por meses (N5): agrupación, ventana de tres meses, `loadMore()`, metas y orden de las comidas. */
@OptIn(ExperimentalCoroutinesApi::class)
class MealHistoryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val hoy = LocalDate.of(2026, 10, 13)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun registro(
        id: String,
        fecha: String,
        tipo: MealType = MealType.LUNCH,
        status: NutritionStatus = NutritionStatus.CONSUMED,
    ) = NutritionLog(
        id = id,
        date = fecha,
        mealType = tipo,
        status = status,
        foods = listOf(
            LoggedFood(id = id, foodName = "Comida $id", amount = 100.0, calories = 500.0, protein = 30.0, carbs = 50.0, fats = 15.0),
        ),
    )

    private fun historial(
        logs: List<NutritionLog>,
        snapshots: List<DailyGoalSnapshot> = emptyList(),
    ): MealHistoryViewModel = MealHistoryViewModel(
        inputs = MealHistoryInputs(
            logs = MutableStateFlow(logs),
            settings = MutableStateFlow(Settings()),
            plans = MutableStateFlow<List<NutritionPlan>>(emptyList()),
            activePlanId = MutableStateFlow<String?>(null),
            goalSnapshots = MutableStateFlow(snapshots),
        ),
        locale = Locale.forLanguageTag("es"),
        today = { hoy },
        computeDispatcher = dispatcher,
    )

    @Test
    fun agrupaPorMesDelMasRecienteAlMasAntiguo() {
        val vm = historial(
            listOf(
                registro("a", "2026-08-30"),
                registro("b", "2026-10-12"),
                registro("c", "2026-09-28"),
                registro("d", "2026-10-13"),
                registro("e", "2026-09-15"),
            ),
        )
        val meses = vm.months.value
        assertEquals(listOf("octubre de 2026", "septiembre de 2026", "agosto de 2026"), meses.map { it.label })
        assertEquals(listOf("2026-10-13", "2026-10-12"), meses[0].days.map { it.dateKey })
        assertEquals(listOf("2026-09-28", "2026-09-15"), meses[1].days.map { it.dateKey })
    }

    @Test
    fun cargaTresMesesYLoadMoreSumaTresMas() {
        // Siete meses de registros, de enero a julio de 2026: la primera tanda enseña julio, junio y mayo.
        val logs = (1..7).map { mes -> registro("m$mes", "2026-%02d-10".format(mes)) }
        val vm = historial(logs)
        assertEquals(listOf(7, 6, 5), vm.months.value.map { it.month.monthValue })
        assertTrue(vm.hasMore.value)

        vm.loadMore()
        assertEquals(listOf(7, 6, 5, 4, 3, 2), vm.months.value.map { it.month.monthValue })
        assertTrue(vm.hasMore.value)

        vm.loadMore()
        assertEquals(7, vm.months.value.size)
        assertFalse(vm.hasMore.value)

        // Sin más meses, pedir más no cambia nada.
        vm.loadMore()
        assertEquals(7, vm.months.value.size)
    }

    @Test
    fun laCargaTerminaConLaPrimeraTanda() {
        val vm = historial(listOf(registro("a", "2026-10-13")))
        assertFalse(vm.isLoading.value)
        assertEquals(1, vm.months.value.size)
    }

    @Test
    fun sinEvidenciaNoHayMeta() {
        // Hoy tiene una meta guardada de 2.300 kcal; el 15 de septiembre, un día pasado sin meta guardada, no tiene ninguna.
        val vm = historial(
            logs = listOf(registro("a", "2026-10-13"), registro("b", "2026-09-15")),
            snapshots = listOf(DailyGoalSnapshot(date = "2026-10-13", calorieTargetKcal = 2300)),
        )
        val dias = vm.months.value.flatMap { it.days }.associateBy { it.dateKey }
        assertEquals(2300, dias.getValue("2026-10-13").kcalGoal)
        assertNull(dias.getValue("2026-09-15").kcalGoal)
    }

    @Test
    fun lasComidasVanEnElOrdenDeLaRaiz() {
        // Merienda antes que cena, como en la raíz (MealSlots), aunque el enum de comidas las ordene de otra forma.
        val vm = historial(
            listOf(registro("c", "2026-10-13", MealType.DINNER), registro("s", "2026-10-13", MealType.SNACK)),
        )
        assertEquals(
            listOf(MealType.SNACK, MealType.DINNER),
            vm.months.value.single().days.single().logs.map { it.mealType },
        )
    }

    @Test
    fun losPlanificadosNoEntranEnElHistorial() {
        val vm = historial(listOf(registro("p", "2026-10-01", status = NutritionStatus.PLANNED)))
        assertTrue(vm.months.value.isEmpty())
        assertFalse(vm.hasMore.value)
    }
}
