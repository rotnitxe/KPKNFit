package com.example.kpkn.screens.programdetail.design.split

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.split.DayKind
import com.example.kpkn.domain.training.split.MigrationPlan
import com.example.kpkn.domain.training.split.MigrationTestSupport
import com.example.kpkn.domain.training.split.MigrationTestSupport.cardioPart
import com.example.kpkn.domain.training.split.MigrationTestSupport.ok
import com.example.kpkn.domain.training.split.MigrationTestSupport.request
import com.example.kpkn.domain.training.split.MigrationTestSupport.ulWeek
import com.example.kpkn.domain.training.split.PlanDecision
import com.example.kpkn.domain.training.split.PlanWarning
import com.example.kpkn.domain.training.split.SplitMigrationPlanner
import com.example.kpkn.domain.training.split.SplitTestSupport
import com.example.kpkn.domain.training.split.WarningKind
import com.example.kpkn.domain.training.split.WarningSeverity
import com.example.kpkn.domain.training.split.PlanWarnings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Los modelos de pantalla del reparto (punto 23) con el motor de verdad: la agenda antes y después, los avisos con su negrita (unidos son la frase
 * del motor), el volumen y la frecuencia, el veredicto de la revisión, el mapa de dónde sale cada sesión, las decisiones y el resumen tras aplicar.
 */
class SplitFlowModelsTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val resolver get() = SplitTestSupport.resolver

    private fun planOf(splitId: String, weekdays: List<Int>, week: ProgramWeek = ulWeek("w1"), targetMinutes: Int? = null): Pair<com.example.kpkn.data.models.Program, MigrationPlan.Ok> {
        val program = MigrationTestSupport.program(week)
        return program to ok(SplitMigrationPlanner.plan(request(program, splitId, weekdays, targetMinutes = targetMinutes)))
    }

    // ─── Texto de un ejercicio ──────────────────────────────────────────────────────────────────

    @Test
    fun prescription_reads_sets_reps_rir_and_rest() {
        val exercise = Exercise(
            id = "e1",
            name = "Press banca",
            sets = List(4) { ExerciseSet(id = "s$it", targetRepsRange = RepRange(5, 6), targetRIR = 2) },
            restTime = 180,
        )
        assertEquals("4 × 5–6 · RIR 2 · 3 min", SplitFlowModels.prescription(exercise))
        assertEquals("3 × 10 · 2,5 min", SplitFlowModels.prescription(exercise.copy(sets = List(3) { ExerciseSet(id = "t$it", targetReps = 10) }, restTime = 150)))
        assertEquals("3 × 8 · 45 s", SplitFlowModels.prescription(exercise.copy(sets = List(3) { ExerciseSet(id = "u$it", targetReps = 8) }, restTime = 45)))
    }

    @Test
    fun rest_text_uses_comma_decimals() {
        assertEquals("1 min", SplitFlowModels.restText(60))
        assertEquals("1,5 min", SplitFlowModels.restText(90))
        assertEquals("30 s", SplitFlowModels.restText(30))
    }

    // ─── Avisos ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun every_warning_kind_reads_like_the_engine_sentence() {
        val warnings = listOf(
            PlanWarning(WarningKind.DAYS_CHANGED, WarningSeverity.ATTENTION, mapOf("added" to listOf(6), "removed" to listOf(3), "from" to 4, "to" to 5)),
            PlanWarning(WarningKind.FREQ_DOWN, WarningSeverity.ATTENTION, mapOf("muscle" to "Pecho", "from" to 3, "to" to 2)),
            PlanWarning(WarningKind.FREQ_UP, WarningSeverity.INFO, mapOf("muscles" to listOf("Pecho", "Espalda", "Bíceps"))),
            PlanWarning(WarningKind.ADJACENT_HEAVY, WarningSeverity.ATTENTION, mapOf("a" to "Torso A", "b" to "Torso B", "muscle" to "Espalda")),
            PlanWarning(WarningKind.SHORT_DAY, WarningSeverity.ATTENTION, mapOf("title" to "Pierna B", "count" to 2)),
            PlanWarning(WarningKind.TIME_OVER, WarningSeverity.ATTENTION, mapOf("title" to "Torso A", "minutes" to 80, "target" to 75)),
            PlanWarning(WarningKind.MISFIT, WarningSeverity.INFO, mapOf("names" to listOf("Rueda abdominal"))),
            PlanWarning(WarningKind.DELOAD_KEPT, WarningSeverity.INFO, mapOf("weekId" to "w6")),
        )
        warnings.forEach { warning ->
            assertEquals(PlanWarnings.sentence(warning), SplitFlowModels.richText(warning).plain())
        }
        val days = SplitFlowModels.richText(warnings.first())
        assertEquals(RpSpan("5 días", bold = true), days[1])
    }

    // ─── Agenda ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_before_agenda_has_seven_days_and_the_weeks_sessions() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val days = SplitFlowModels.beforeAgenda(program, "w1", resolver)
        assertEquals(7, days.size)
        assertEquals(listOf(1, 2, 4, 5), days.filter { !it.rest }.map { it.weekday })
        assertTrue(days.flatMap { it.sessions }.all { it.readOnly })
        assertEquals("Torso A", days.first().sessions.single().title)
    }

    @Test
    fun the_after_agenda_places_each_new_session_on_its_day_with_exercises_and_move_targets() {
        val (_, plan) = planOf("ppl_x3", listOf(1, 3, 5))
        val days = SplitFlowModels.afterAgenda(plan)
        assertEquals(listOf(1, 3, 5), days.filter { !it.rest }.map { it.weekday })
        val first = days.first().sessions.single()
        assertEquals(DayKind.OPEN, first.kind)
        assertTrue(first.draggable)
        assertEquals(7, first.moveTargets.size)
        assertTrue(first.moveTargets.single { it.weekday == 1 }.here)
        assertTrue(first.exercises.isNotEmpty())
        assertTrue(first.detail.endsWith(" min"))
        // Lo que cae en el día de otra sesión nueva se intercambia: lo dice la descripción del destino.
        assertTrue(first.moveTargets.single { it.weekday == 3 }.description.contains("intercambia con"))
    }

    @Test
    fun exercises_that_change_session_carry_the_origin_and_the_rest_do_not() {
        val (_, plan) = planOf("ppl_x3", listOf(1, 3, 5))
        val lines = SplitFlowModels.afterAgenda(plan).flatMap { it.sessions }.flatMap { it.exercises }
        assertTrue(lines.any { it.movedFrom != null })
        assertEquals(plan.base.moves.count { it.kind == com.example.kpkn.domain.training.split.MoveKind.MOVED }, lines.count { it.movedFrom != null })
    }

    @Test
    fun week_dates_follow_the_start_date_by_weekday() {
        val week = ulWeek("w1").copy(startDate = "2026-11-03")
        val dates = SplitFlowModels.weekDates(week)
        // El 3 de nov de 2026 es martes: el lunes de esa semana es el 9.
        assertEquals(9, dates[0])
        assertEquals(3, dates[1])
        assertEquals(4, dates[2])
        assertNull(SplitFlowModels.weekDates(ulWeek("w2")).first())
    }

    // ─── Volumen ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun volume_rows_list_the_eleven_groups_and_keep_the_series_when_exercises_move_whole() {
        val (_, plan) = planOf("ppl_x3", listOf(1, 3, 5))
        val rows = SplitFlowModels.volumeRows(plan.base)
        assertTrue(rows.size >= 11)
        assertEquals("Pecho", rows.first().label)
        assertEquals("Core", rows[10].label)
        rows.forEach { assertEquals(it.before, it.after, 0.01) }
        assertEquals("2→3×", RpVolumeRow("Pecho", 9.0, 9.0, 2, 3).freqText)
        assertEquals("2×", RpVolumeRow("Pecho", 9.0, 9.0, 2, 2).freqText)
        assertEquals("18,5", SplitFlowModels.number(18.5))
        assertEquals("9", SplitFlowModels.number(9.0))
    }

    // ─── Revisión ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_review_says_nothing_is_lost_and_counts_what_moves() {
        val (program, plan) = planOf("ppl_x3", listOf(1, 3, 5))
        val summary = SplitFlowModels.reviewSummary(program, plan, targetMinutes = null)
        assertTrue(summary.verdict.startsWith("No pierdes nada: "))
        assertEquals(summary.sets, summary.setsBefore)
        assertTrue(summary.moved > 0)
        assertEquals("series, las mismas", summary.setsLabel)
    }

    @Test
    fun the_map_accounts_for_every_exercise_of_every_original_session() {
        val (program, plan) = planOf("ppl_x3", listOf(1, 3, 5))
        val map = SplitFlowModels.reviewMap(program, plan)
        assertEquals(4, map.size)
        map.forEach { row -> assertEquals(row.total, row.parts.sumOf { it.count }) }
        assertTrue(map.all { it.caption.startsWith("→ ") || it.caption == "Se queda como estaba" })
    }

    @Test
    fun the_review_sessions_follow_the_week_order_and_mark_superset_members() {
        val (_, plan) = planOf("ppl_x3", listOf(1, 3, 5))
        val sessions = SplitFlowModels.reviewSessions(plan)
        assertEquals(3, sessions.size)
        assertTrue(sessions.first().meta.startsWith("Lun"))
        assertTrue(sessions.flatMap { it.rows }.filter { it.moved }.all { it.tag!!.startsWith("de ") })
    }

    // ─── Decisiones ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun cardio_gets_a_proposal_with_the_days_it_can_go_to() {
        val (_, plan) = planOf("ppl_x3", listOf(1, 3, 5), week = ulWeek("w1", extraParts = mapOf("u1" to cardioPart())))
        val decisions = SplitFlowModels.decisions(plan)
        val cardio = decisions.places.single { it.title.contains("Cinta") }
        assertTrue(cardio.explanation.startsWith("El cardio no depende del músculo"))
        assertEquals(3, cardio.options.size)
        assertEquals(1, cardio.options.count { it.on })
    }

    @Test
    fun the_time_decision_is_off_by_default_and_names_the_minutes() {
        val (_, plan) = planOf("fullbody_x3", listOf(1, 3, 5), targetMinutes = 30)
        val time = SplitFlowModels.decisions(plan).time
        assertNotNull(time)
        assertEquals(30, time!!.targetMinutes)
        assertFalse(time.fit)
        assertTrue(plan.decisions.any { it is PlanDecision.FitTime })
    }

    // ─── Resumen ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_applied_summary_and_its_undone_version_read_like_the_canvas() {
        val (program, plan) = planOf("ppl_x3", listOf(1, 3, 5))
        val summary = SplitFlowModels.appliedSummary(program, plan, "Semana 5", null)
        assertTrue(summary.beforeText.contains("4 días (lunes, martes, jueves y viernes)"))
        val applied = SplitFlowModels.appliedModel(summary, undone = false)
        assertEquals("Reparto aplicado", applied.title)
        assertEquals(4, applied.stats.size)
        assertEquals("Días de entreno" to "lun, mié y vie", applied.stats.last())
        val undone = SplitFlowModels.appliedModel(summary, undone = true)
        assertEquals("Reparto deshecho", undone.title)
        assertTrue(undone.sub.startsWith("Tu semana 5 volvió a como estaba: "))
        assertTrue(undone.sub.endsWith("con las mismas series."))
    }

    @Test
    fun a_locked_week_says_which_days_are_already_done() {
        val program = MigrationTestSupport.program(ulWeek("w1"))
        val plan = ok(SplitMigrationPlanner.plan(request(program, "ppl_x3", listOf(1, 3, 5), completed = setOf("w1-u1"))))
        val notice = SplitFlowModels.notices(plan).first()
        assertEquals(RpSeverity.OK, notice.severity)
        assertTrue(notice.text.plain().contains("ya está hecho"))
        val days = SplitFlowModels.afterAgenda(plan)
        val locked = days.flatMap { it.sessions }.single { it.kind == DayKind.LOCKED }
        assertEquals("Hecha · se queda como está", locked.detail)
        assertFalse(locked.draggable)
        assertTrue(locked.moveTargets.isEmpty())
    }
}
