package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.split.SplitTestSupport.session
import com.example.kpkn.domain.training.split.SplitTestSupport.sessionsOf
import com.example.kpkn.domain.training.split.SplitTestSupport.week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** El efecto real de arrastrar y soltar: mover a un día libre, intercambiar y escribir el resultado en el programa. */
class WeekAssignmentTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val ppl get() = SplitTestSupport.ppl()

    private fun Program.dayOf(id: String): Int? = sessionsOf(this).first { it.id == id }.dayOfWeek

    // ─── move ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_assignment_of_a_program_maps_each_day_to_its_session() {
        assertEquals(mapOf(1 to "push", 3 to "pull", 5 to "legs"), WeekAssignment.of(ppl))
        assertEquals(emptyMap<Int, String>(), WeekAssignment.of(ppl, weekIndex = 4))
    }

    @Test
    fun moving_to_a_free_day_moves_the_session_and_frees_its_old_day() {
        val moved = WeekAssignment.move(WeekAssignment.of(ppl), "push", 2)
        assertEquals(mapOf(2 to "push", 3 to "pull", 5 to "legs"), moved)
    }

    @Test
    fun moving_onto_an_occupied_day_swaps_the_two_sessions() {
        val swapped = WeekAssignment.move(WeekAssignment.of(ppl), "push", 3)
        assertEquals(mapOf(1 to "pull", 3 to "push", 5 to "legs"), swapped)
        // El intercambio es simétrico: soltar la otra sobre la primera da lo mismo.
        assertEquals(swapped, WeekAssignment.move(WeekAssignment.of(ppl), "pull", 1))
    }

    @Test
    fun dropping_on_the_same_day_or_outside_the_week_changes_nothing() {
        val assignment = WeekAssignment.of(ppl)
        assertEquals(assignment, WeekAssignment.move(assignment, "pull", 3))
        assertEquals(assignment, WeekAssignment.move(assignment, "pull", 0))
        assertEquals(assignment, WeekAssignment.move(assignment, "pull", 8))
        assertEquals(assignment, WeekAssignment.move(assignment, "no-existe", 2))
    }

    @Test
    fun a_rest_day_outside_the_training_days_is_a_valid_destination() {
        val moved = WeekAssignment.move(WeekAssignment.of(ppl), "legs", 7)
        assertEquals(mapOf(1 to "push", 3 to "pull", 7 to "legs"), moved)
        // Y se puede volver: mover otra vez libera el 7.
        assertEquals(mapOf(1 to "push", 3 to "pull", 6 to "legs"), WeekAssignment.move(moved, "legs", 6))
    }

    @Test
    fun the_result_of_a_move_is_sorted_by_day() {
        val moved = WeekAssignment.move(WeekAssignment.of(ppl), "push", 6)
        assertEquals(listOf(3, 5, 6), moved.keys.toList())
    }

    // ─── applyAssignment ──────────────────────────────────────────────────────────────────────────

    @Test
    fun applying_a_swap_rewrites_the_days_and_the_schedule_without_touching_exercises() {
        val original = ppl
        val assignment = WeekAssignment.move(WeekAssignment.of(original), "push", 3)
        val applied = WeekAssignment.applyAssignment(original, assignment)

        assertEquals(3, applied.dayOf("push"))
        assertEquals(1, applied.dayOf("pull"))
        assertEquals(5, applied.dayOf("legs"))
        sessionsOf(applied).forEach { assertEquals(listOf(it.dayOfWeek), it.assignedDays) }
        assertEquals(setOf(1, 3, 5), applied.schedulePlan?.trainingDays)
        // Ningún ejercicio ni parte cambia: solo el día de cada sesión.
        val before = sessionsOf(original).associateBy { it.id }
        sessionsOf(applied).forEach { after ->
            assertEquals(before.getValue(after.id).copy(dayOfWeek = after.dayOfWeek, assignedDays = after.assignedDays), after)
        }
    }

    @Test
    fun moving_to_a_rest_day_updates_the_training_days_of_the_schedule() {
        val original = ppl
        val assignment = WeekAssignment.move(WeekAssignment.of(original), "legs", 6)
        val applied = WeekAssignment.applyAssignment(original, assignment)
        assertEquals(6, applied.dayOf("legs"))
        assertEquals(setOf(1, 3, 6), applied.schedulePlan?.trainingDays)
        assertEquals(1, applied.schedulePlan?.weekStartDay)
    }

    @Test
    fun an_unchanged_assignment_leaves_the_program_as_it_was() {
        val original = ppl
        val applied = WeekAssignment.applyAssignment(original, WeekAssignment.of(original))
        assertEquals(sessionsOf(original), sessionsOf(applied))
        assertEquals(setOf(1, 3, 5), applied.schedulePlan?.trainingDays)
    }

    @Test
    fun sessions_that_the_assignment_does_not_name_keep_their_day() {
        val original = ppl
        val applied = WeekAssignment.applyAssignment(original, mapOf(2 to "push"))
        assertEquals(2, applied.dayOf("push"))
        assertEquals(3, applied.dayOf("pull"))
        assertEquals(5, applied.dayOf("legs"))
        assertEquals(setOf(2, 3, 5), applied.schedulePlan?.trainingDays)
    }

    @Test
    fun a_session_with_several_assigned_days_only_changes_the_one_it_occupied() {
        val twoDays = SplitTestSupport.program(
            listOf(
                week(
                    "w1",
                    listOf(
                        session("a", "A", 1, SplitTestSupport.PUSH).copy(assignedDays = listOf(1, 4)),
                        session("b", "B", 2, SplitTestSupport.PULL),
                    ),
                ),
            ),
        )
        val applied = WeekAssignment.applyAssignment(twoDays, mapOf(6 to "a", 2 to "b"))
        val a = sessionsOf(applied).first { it.id == "a" }
        assertEquals(6, a.dayOfWeek)
        assertEquals(listOf(6, 4), a.assignedDays)
        assertEquals(setOf(6, 4, 2), applied.schedulePlan?.trainingDays)
    }

    @Test
    fun the_same_move_is_repeated_in_the_weeks_with_the_same_days() {
        fun copyOfWeek(prefix: String) = week(
            prefix,
            sessionsOf(ppl).map { it.copy(id = "$prefix-${it.id}") },
        )
        val program = SplitTestSupport.program(listOf(copyOfWeek("w1"), copyOfWeek("w2"), copyOfWeek("w3")))
        val assignment = WeekAssignment.move(WeekAssignment.of(program), "w1-push", 2)

        val applied = WeekAssignment.applyAssignment(program, assignment)
        val days = SplitTestSupport.weeksOf(applied).map { week -> week.sessions.first { it.id.endsWith("push") }.dayOfWeek }
        assertEquals(listOf(2, 2, 2), days)
        assertEquals(setOf(2, 3, 5), applied.schedulePlan?.trainingDays)

        val onlyFirst = WeekAssignment.applyAssignment(program, assignment, propagateToOtherWeeks = false)
        val daysFirst = SplitTestSupport.weeksOf(onlyFirst).map { week -> week.sessions.first { it.id.endsWith("push") }.dayOfWeek }
        assertEquals(listOf(2, 1, 1), daysFirst)
        // El calendario del plan sigue diciendo todos los días que alguna semana usa.
        assertEquals(setOf(1, 2, 3, 5), onlyFirst.schedulePlan?.trainingDays)
    }

    @Test
    fun a_week_with_other_days_is_not_touched_by_the_propagation() {
        val different = SplitTestSupport.program(
            listOf(
                week("w1", sessionsOf(ppl)),
                week("w2", sessionsOf(ppl).map { it.copy(id = "x-${it.id}", dayOfWeek = (it.dayOfWeek ?: 1) + 1, assignedDays = listOf((it.dayOfWeek ?: 1) + 1)) }),
            ),
        )
        val applied = WeekAssignment.applyAssignment(different, WeekAssignment.move(WeekAssignment.of(different), "push", 2))
        val second = SplitTestSupport.weeksOf(applied)[1].sessions
        assertEquals(listOf(2, 4, 6), second.map { it.dayOfWeek })
    }

    @Test
    fun issues_describe_what_an_assignment_gets_wrong() {
        val program = ppl
        assertEquals(emptyList<String>(), WeekAssignment.issues(program, WeekAssignment.of(program)))
        val problems = WeekAssignment.issues(program, mapOf(9 to "push", 3 to "ghost", 4 to "pull", 5 to "pull"))
        assertTrue(problems.any { it.contains("día 9") })
        assertTrue(problems.any { it.contains("ghost") })
        assertTrue(problems.any { it.contains("pull") && it.contains("más de un día") })
        assertEquals(listOf("Tu programa no tiene esa semana."), WeekAssignment.issues(program, emptyMap(), weekIndex = 5))
    }

    @Test
    fun sessions_are_described_for_the_board() {
        val infos = WeekAssignment.sessionsOf(ppl, SplitTestSupport.resolver)
        assertEquals(listOf("push", "pull", "legs"), infos.map { it.id })
        assertEquals(listOf(1, 3, 5), infos.map { it.day })
        assertTrue(infos.all { it.minutes > 0 && it.exerciseCount == 6 && it.focus.isNotBlank() && it.isMain })
        assertTrue(infos.first { it.id == "legs" }.focus.contains("Cuádriceps"))
    }

    @Test
    fun applying_to_a_week_that_does_not_exist_returns_the_same_program() {
        val program = ppl
        assertEquals(program, WeekAssignment.applyAssignment(program, mapOf(2 to "push"), weekIndex = 3))
    }
}
