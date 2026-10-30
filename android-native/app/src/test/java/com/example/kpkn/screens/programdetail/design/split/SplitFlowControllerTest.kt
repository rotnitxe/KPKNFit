package com.example.kpkn.screens.programdetail.design.split

import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.split.MigrationPlan
import com.example.kpkn.domain.training.split.MigrationTestSupport
import com.example.kpkn.domain.training.split.MigrationTestSupport.cardioPart
import com.example.kpkn.domain.training.split.MigrationTestSupport.ulWeek
import com.example.kpkn.domain.training.split.PlanDecision
import com.example.kpkn.domain.training.split.SplitMigrationPlanner
import com.example.kpkn.domain.training.split.SplitTestSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * El estado de la vista previa del reparto (punto 23): elegir un reparto propone sus días y pide el plan; arrastrar una sesión cambia el día y
 * activa «Restablecer»; una decisión del cardio y el recorte por tiempo vuelven a pedir el plan; «Antes» no borra el plan; una semana sin sesiones
 * enseña plantillas en vez de un plan.
 */
class SplitFlowControllerTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private fun controller(
        week: ProgramWeek = ulWeek("w1"),
        targetMinutes: Int? = null,
        completed: Set<String> = emptySet(),
    ): SplitFlowController {
        val program = MigrationTestSupport.program(week)
        return SplitFlowController(
            SplitFlowController.Config(
                program = program,
                weekId = week.id,
                resolver = SplitTestSupport.resolver,
                completedSessionIds = completed,
                targetMinutes = targetMinutes,
            ),
            scope = CoroutineScope(Dispatchers.Unconfined),
            computeDispatcher = Dispatchers.Unconfined,
        )
    }

    private val ppl3 get() = MigrationTestSupport.split("ppl_x3")
    private val fullBody3 get() = MigrationTestSupport.split("fullbody_x3")

    @Test
    fun choosing_a_split_proposes_its_days_and_computes_the_plan() {
        val controller = controller()
        controller.open(ppl3)
        val state = controller.state.value
        assertFalse(state.calculating)
        val plan = state.plan
        assertNotNull(plan)
        assertEquals(3, plan!!.weekdays.size)
        assertEquals(plan.weekdays, state.initialWeekdays)
        assertFalse(state.dirty)
        assertEquals(RpView.AFTER, state.view)
    }

    @Test
    fun dragging_a_session_changes_its_day_and_enables_reset() {
        val controller = controller()
        controller.open(ppl3)
        val before = controller.state.value.plan!!
        val from = before.base.days.first().weekday
        val free = (1..7).first { day -> before.base.days.none { it.weekday == day } }
        controller.move(from, free)
        val moved = controller.state.value
        assertTrue(moved.dirty)
        assertEquals(free, moved.selectedWeekday)
        assertTrue(moved.plan!!.base.days.any { it.weekday == free })
        assertTrue(moved.plan!!.base.days.none { it.weekday == from })
        controller.reset()
        val reset = controller.state.value
        assertFalse(reset.dirty)
        assertEquals(before.weekdays, reset.plan!!.weekdays)
    }

    @Test
    fun dropping_on_an_occupied_day_swaps_the_two_sessions() {
        val controller = controller()
        controller.open(ppl3)
        val days = controller.state.value.plan!!.base.days.sortedBy { it.weekday }
        val a = days[0]
        val b = days[1]
        controller.move(a.weekday, b.weekday)
        val after = controller.state.value.plan!!.base.days
        assertEquals(b.weekday, after.single { it.title == a.title }.weekday)
        assertEquals(a.weekday, after.single { it.title == b.title }.weekday)
    }

    @Test
    fun toggling_selection_and_the_before_view_keep_the_plan() {
        val controller = controller()
        controller.open(ppl3)
        val plan = controller.state.value.plan
        controller.toggleSelected(3)
        assertEquals(3, controller.state.value.selectedWeekday)
        controller.toggleSelected(3)
        assertNull(controller.state.value.selectedWeekday)
        controller.setView(RpView.BEFORE)
        assertEquals(RpView.BEFORE, controller.state.value.view)
        assertEquals(plan, controller.state.value.plan)
    }

    @Test
    fun a_decision_about_the_cardio_asks_the_engine_again() {
        val controller = controller(week = ulWeek("w1", extraParts = mapOf("u1" to cardioPart())))
        controller.open(ppl3)
        val decision = controller.state.value.plan!!.decisions.filterIsInstance<PlanDecision.PlaceUnit>().single()
        val other = decision.options.first { it != decision.proposedDay }
        controller.place(decision.unitKey, other)
        val updated = controller.state.value.plan!!.decisions.filterIsInstance<PlanDecision.PlaceUnit>().single()
        assertTrue(updated.chosen)
        assertEquals(other, updated.proposedDay)
        assertTrue(controller.state.value.dirty)
    }

    @Test
    fun fitting_the_time_is_an_explicit_choice() {
        val controller = controller(targetMinutes = 30)
        controller.open(fullBody3)
        assertFalse(controller.state.value.request!!.fitToTime)
        controller.setFit(true)
        val state = controller.state.value
        assertTrue(state.request!!.fitToTime)
        assertTrue(state.plan!!.decisions.filterIsInstance<PlanDecision.FitTime>().single().applied)
        controller.setFit(false)
        assertFalse(controller.state.value.request!!.fitToTime)
    }

    @Test
    fun a_request_with_the_wrong_number_of_days_is_refused_by_the_engine() {
        val controller = controller()
        controller.open(ppl3)
        val request = controller.state.value.request!!
        assertTrue(SplitMigrationPlanner.plan(request.copy(weekdays = listOf(1, 2))) is MigrationPlan.Refused)
    }

    @Test
    fun closing_goes_back_to_an_empty_state() {
        val controller = controller()
        controller.open(ppl3)
        controller.close()
        val state = controller.state.value
        assertNull(state.split)
        assertNull(state.plan)
    }

    @Test
    fun a_week_without_sessions_shows_templates_and_no_plan() {
        val empty = ulWeek("w1").copy(sessions = emptyList())
        val controller = controller(week = empty)
        controller.open(MigrationTestSupport.split("ul_x4"))
        val state = controller.state.value
        assertTrue(state.emptyWeek)
        assertNull(state.plan)
        assertEquals(4, state.templateDays.size)
        val first = state.templateDays.first().weekday
        val free = (1..7).first { day -> state.templateDays.none { it.weekday == day } }
        controller.move(first, free)
        assertTrue(controller.state.value.templateDays.any { it.weekday == free })
        assertTrue(controller.state.value.dirty)
        controller.reset()
        assertFalse(controller.state.value.dirty)
    }
}
