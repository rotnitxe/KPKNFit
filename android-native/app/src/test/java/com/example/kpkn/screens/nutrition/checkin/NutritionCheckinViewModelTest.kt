package com.example.kpkn.screens.nutrition.checkin

import androidx.lifecycle.viewModelScope
import com.example.kpkn.domain.nutrition.checkin.*
import com.example.kpkn.domain.nutrition.expenditure.*
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionCheckinViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private var today = NutritionCheckinSamples.today
    private val context = MutableStateFlow(NutritionCheckinSamples.context())
    private val decisions = MutableStateFlow<List<CheckinRecord>>(emptyList())
    private var fail = false
    private var applications = 0
    private lateinit var vm: NutritionCheckinViewModel
    private val store = object : CheckinStore {
        override val records = decisions.asStateFlow()
        override suspend fun apply(plan: CheckinPlan) {
            decisions.value = listOf(CheckinRecord(plan.weekKey, CheckinDecision.PENDING))
            check(!fail) { "Fallo de prueba." }
            applications++
            decisions.value = listOf(CheckinRecord(plan.weekKey, CheckinDecision.APPLIED))
        }
        override suspend fun decline(weekKey: LocalDate, proposal: CheckinProposal?) { decisions.value = listOf(CheckinRecord(weekKey, CheckinDecision.DECLINED)) }
        override suspend fun postpone(weekKey: LocalDate, today: LocalDate, proposal: CheckinProposal?) { decisions.value = listOf(CheckinRecord(weekKey, CheckinDecision.POSTPONED, today.plusDays(1))) }
    }
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = NutritionCheckinViewModel(CheckinDependencies(context, store,
            savePreferences = { change -> context.value = context.value.copy(preferences = change(context.value.preferences)) },
            markDay = { date, mark, _ -> context.value = context.value.copy(days = context.value.days.map { if (it.date == date) it.copy(mark = mark) else it }) },
            today = { today }), dispatcher)
    }
    @After fun close() { vm.viewModelScope.cancel(); Dispatchers.resetMain() }

    @Test fun `el modelo expone NOW FUTURE NONE segun fecha dia elegido y plan`() {
        assertEquals(CheckinAvailability.NOW, vm.state.value.availability)
        today = today.plusDays(4)
        vm.controller.refresh()
        assertEquals(CheckinAvailability.FUTURE, vm.state.value.availability)
        context.value = context.value.copy(plan = null)
        assertEquals(CheckinAvailability.NONE, vm.state.value.availability)
    }
    @Test fun `posponer oculta hoy y ofrece manana`() {
        vm.controller.postpone()
        assertEquals(CheckinAvailability.FUTURE, vm.state.value.availability)
        today = today.plusDays(1)
        vm.controller.refresh()
        assertEquals(CheckinAvailability.NOW, vm.state.value.availability)
    }
    @Test fun `descartar un modulo persiste su preferencia sin descartar el checkin`() {
        vm.controller.dismissModule(CheckinModule.WEIGH_IN)
        assertTrue(context.value.preferences.dismissedModules.contains(CheckinModule.WEIGH_IN))
        assertFalse(vm.state.value.steps.contains(CheckinModule.WEIGH_IN))
        assertEquals(CheckinAvailability.NOW, vm.state.value.availability)
        vm.controller.setCheckinDay(DayOfWeek.TUESDAY)
        assertEquals(2, context.value.preferences.dayOfWeek)
    }
    @Test fun `la introduccion solo aparece la primera vez y conservar la propuesta sigue siendo explicito`() {
        vm.controller.next()
        assertTrue(context.value.preferences.introductionSeen)
        assertFalse(vm.state.value.steps.contains(CheckinModule.INTRODUCTION))
        assertEquals(0, applications)
    }
    @Test fun `un fallo deja PENDING visible y reintentar aplica una vez`() {
        fail = true
        vm.controller.accept()
        assertEquals(CheckinDecision.PENDING, decisions.value.single().status)
        assertNotNull(vm.state.value.error)
        assertEquals(0, applications)
        fail = false
        vm.controller.accept()
        assertEquals(1, applications)
        assertEquals(CheckinAvailability.DISMISSED, vm.state.value.availability)
        vm.controller.accept()
        assertEquals(1, applications)
    }
    @Test fun `revisar parciales y confirmar ayuno usa las mismas marcas durables`() {
        context.value = NutritionCheckinSamples.context("partial_days")
        val partialDate = today.minusDays(2)
        vm.controller.markPartial(partialDate)
        assertEquals(DayMark.PARTIAL, context.value.days.single { it.date == partialDate }.mark)
        assertEquals(ExclusionReason.PARTIAL, vm.state.value.plan!!.estimate!!.quality.excludedDates[partialDate])
        vm.controller.keepComplete(partialDate)
        assertFalse(vm.state.value.plan!!.estimate!!.quality.excludedDates.containsKey(partialDate))
        val fastingDate = today.minusDays(4)
        vm.controller.confirmFasting(fastingDate)
        assertFalse(vm.state.value.plan!!.estimate!!.quality.excludedDates.containsKey(fastingDate))
        assertEquals(56, context.value.days.size)
    }
    @Test fun `la proyeccion ignora comida futura y una edicion conserva la serie anterior a ese dia`() {
        val before = vm.state.value.plan!!.series
        context.value = context.value.copy(days = context.value.days + DayObservation(today.plusDays(1), 9000.0))
        assertEquals(before, vm.state.value.plan!!.series)
        val editedDate = today.minusDays(8)
        context.value = context.value.copy(days = context.value.days.map { if (it.date == editedDate) it.copy(intakeKcal = 3300.0) else it })
        val after = vm.state.value.plan!!.series
        assertEquals(before.filter { it.date < editedDate }, after.filter { it.date < editedDate })
        assertNotEquals(before.last().kcal, after.last().kcal)
    }
    @Test fun `no acepta una propuesta anterior mientras recalcula los datos editados`() {
        val delayed = NutritionCheckinViewModel(CheckinDependencies(context, store, {}, { _, _, _ -> }, { today }), StandardTestDispatcher(dispatcher.scheduler))
        try {
            dispatcher.scheduler.runCurrent()
            assertFalse(delayed.state.value.loading)
            val date = today.minusDays(2)
            context.value = context.value.copy(days = context.value.days.map { if (it.date == date) it.copy(intakeKcal = 3300.0) else it })
            assertTrue(delayed.state.value.loading)
            delayed.controller.accept()
            assertEquals(0, applications)
            assertTrue(decisions.value.isEmpty())
            dispatcher.scheduler.runCurrent()
            assertFalse(delayed.state.value.loading)
        } finally { delayed.viewModelScope.cancel() }
    }
}
