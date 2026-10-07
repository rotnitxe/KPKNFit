package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WarmupExercise
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.split.SplitTestSupport.session
import com.example.kpkn.domain.training.split.SplitTestSupport.week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** Cómo se arma un día a partir de las unidades que le tocan: orden, partes, superseries, calentamientos y bloques. */
class WeekBuilderTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val resolver get() = SplitTestSupport.resolver

    private fun oneDay(vararg originals: Session): Session {
        val sessions = originals.toList()
        val units = WeekGather.gather(week("w", sessions), resolver)
        val day = DayInfo(0, 1, "Torso", "Torso", DayFocusParser.parse("Torso", emptyList()))
        return WeekBuilder.build(sessions, "w", listOf(day), listOf(units), setOf(0)) { "descripción" }.single()
    }

    private fun configurationsOf(session: Session) = session.allExercises().map { it.catalogConfigurationId }

    @Test
    fun the_order_is_main_compounds_first_alternating_push_and_pull() {
        val scrambled = session(
            "s", "S", 1,
            listOf(CatalogIds.PUSHDOWN, CatalogIds.CURL, CatalogIds.LAT, CatalogIds.OHP, CatalogIds.ROW, CatalogIds.BP),
        )
        val built = oneDay(scrambled)
        assertEquals(
            listOf(CatalogIds.BP, CatalogIds.ROW, CatalogIds.OHP, CatalogIds.LAT, CatalogIds.PUSHDOWN, CatalogIds.CURL),
            configurationsOf(built),
        )
    }

    @Test
    fun legs_go_squat_then_hinge_and_finishers_go_last() {
        val scrambled = session(
            "s", "S", 1,
            listOf(CatalogIds.CALF, CatalogIds.LEG_EXT, CatalogIds.PRESS_LEG, CatalogIds.RDL, CatalogIds.SQ_HIGH, CatalogIds.CURL_L),
        )
        val built = oneDay(scrambled)
        assertEquals(
            listOf(CatalogIds.SQ_HIGH, CatalogIds.RDL, CatalogIds.PRESS_LEG, CatalogIds.LEG_EXT, CatalogIds.CURL_L, CatalogIds.CALF),
            configurationsOf(built),
        )
    }

    @Test
    fun parts_are_rebuilt_with_the_names_of_the_original_plan() {
        val built = oneDay(
            session("a", "A", 1, listOf(CatalogIds.BP, CatalogIds.OHP, CatalogIds.PUSHDOWN)),
            session("b", "B", 3, listOf(CatalogIds.ROW, CatalogIds.LAT, CatalogIds.CURL)),
        )
        assertTrue(built.exercises.isEmpty())
        assertEquals(listOf("Principal", "Accesorios"), built.parts.map { it.name })
        assertEquals(listOf("${built.id}#part:0", "${built.id}#part:1"), built.parts.map { it.id })
        assertEquals(listOf("a-e0", "b-e0"), built.parts[0].exercises.map { it.id })
        assertEquals(6, built.allExercises().size)
        assertEquals("descripción", built.description)
        assertEquals("Torso", built.name)
        assertEquals(1, built.dayOfWeek)
        assertTrue(built.isMainSession)
    }

    @Test
    fun exercises_with_the_same_id_in_one_day_get_unique_ids() {
        fun withDuplicateId(base: Session) = base.copy(
            parts = base.parts.map { part -> part.copy(exercises = part.exercises.map { it.copy(id = "dup") }) },
        )
        val built = oneDay(
            withDuplicateId(session("a", "A", 1, listOf(CatalogIds.BP), usesParts = true)),
            withDuplicateId(session("b", "B", 3, listOf(CatalogIds.ROW), usesParts = true)),
        )
        val ids = built.allExercises().map { it.id }
        assertEquals(listOf("dup", "dup~2"), ids)
    }

    @Test
    fun supersets_with_the_same_group_id_in_one_day_are_renamed_consistently() {
        val built = oneDay(
            session("a", "A", 1, listOf(CatalogIds.BP, CatalogIds.CURL, CatalogIds.PUSHDOWN), supersets = mapOf(1 to "ss1", 2 to "ss1")),
            session("b", "B", 3, listOf(CatalogIds.ROW, CatalogIds.HAMMER, CatalogIds.OH_TRI), supersets = mapOf(1 to "ss1", 2 to "ss1")),
        )
        assertEquals(setOf("ss1", "ss1~2"), built.supersetGroups.map { it.id }.toSet())
        built.supersetGroups.forEach { group ->
            val members = built.allExercises().filter { it.supersetGroupRef == group.id }
            assertEquals(2, members.size)
            assertEquals(group.exerciseOrder.toSet(), members.map { it.id }.toSet())
            assertTrue(members.all { it.supersetId == group.id })
        }
    }

    @Test
    fun equal_warmups_are_merged_once_and_different_ones_with_the_same_id_get_their_own() {
        val shoulder = WarmupExercise(id = "w1", name = "Movilidad de hombro", duration = 60)
        val hip = WarmupExercise(id = "w1", name = "Movilidad de cadera", duration = 60)
        val merged = oneDay(
            session("a", "A", 1, listOf(CatalogIds.BP, CatalogIds.OHP), warmup = listOf(shoulder)),
            session("b", "B", 3, listOf(CatalogIds.ROW, CatalogIds.LAT), warmup = listOf(shoulder.copy(id = "other"))),
            session("c", "C", 5, listOf(CatalogIds.SQ_HIGH, CatalogIds.RDL), warmup = listOf(hip)),
        )
        assertEquals(listOf("Movilidad de hombro", "Movilidad de cadera"), merged.warmup.map { it.name })
        assertEquals(merged.warmup.size, merged.warmup.map { it.id }.toSet().size)
    }

    @Test
    fun blocks_go_where_they_were_cardio_last_or_first() {
        val cardio = SessionPart(
            id = "c#part",
            name = "Cardio",
            exercises = listOf(Exercise(id = "cardio-1", name = "cinta", cardioDetails = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 600))),
            isCardioGroup = true,
        )
        val leading = SessionPart(
            id = "m#part",
            name = "Movilidad",
            isMobilityGroup = true,
            mobilitySeries = listOf(MobilitySeries(id = "ms1", name = "Apertura torácica")),
        )
        val base = session("a", "A", 1, listOf(CatalogIds.BP, CatalogIds.OHP))
        // Movilidad antes de la fuerza y cardio al final.
        val ordered = base.copy(parts = listOf(leading) + base.parts + listOf(cardio))
        val built = oneDay(ordered)
        assertEquals(listOf("Movilidad", "Principal", "Accesorios", "Cardio"), built.parts.map { it.name })
        assertTrue(built.parts.first().isMobilityGroup)
        assertEquals(false, built.cardioFirst)

        // Con el cardio antes de la fuerza, la sesión nueva también lo pone antes.
        val cardioFirst = base.copy(parts = listOf(cardio) + base.parts, cardioFirst = true)
        val builtFirst = oneDay(cardioFirst)
        assertEquals(listOf("Cardio", "Principal", "Accesorios"), builtFirst.parts.map { it.name })
        assertEquals(true, builtFirst.cardioFirst)
        assertEquals("cardio-1", builtFirst.allExercises().first().id)
    }

    @Test
    fun a_day_without_units_is_an_empty_session_that_still_has_its_day() {
        val day = DayInfo(0, 4, "Pierna", "Pierna A", DayFocusParser.parse("Pierna", emptyList()))
        val built = WeekBuilder.build(emptyList(), "w", listOf(day), listOf(emptyList()), emptySet()) { "d" }.single()
        assertTrue(built.allExercises().isEmpty())
        assertEquals(4, built.dayOfWeek)
        assertEquals(listOf(4), built.assignedDays)
        assertEquals("Pierna A", built.name)
        assertEquals("Pierna", built.scheduleLabel)
        assertEquals("w-d1", built.id)
    }

    @Test
    fun session_ids_reuse_the_original_ones_and_derive_new_ones_for_extra_days() {
        val originals = listOf(session("a", "A", 1, listOf(CatalogIds.BP)), session("b", "B", 3, listOf(CatalogIds.ROW)))
        assertEquals(listOf("a", "b", "w-d3", "w-d4"), WeekBuilder.sessionIds(originals, "w", 4))
        assertEquals(listOf("a"), WeekBuilder.sessionIds(originals, "w", 1))
        val repeated = listOf(session("a", "A", 1, listOf(CatalogIds.BP)), session("a", "B", 3, listOf(CatalogIds.ROW)))
        assertEquals(listOf("a", "a~2"), WeekBuilder.sessionIds(repeated, "w", 2))
    }

    @Test
    fun unique_ids_register_what_they_hand_out() {
        val used = hashSetOf<String>()
        assertEquals("x", WeekBuilder.uniqueId("x", used))
        assertEquals("x~2", WeekBuilder.uniqueId("x", used))
        assertEquals("x~3", WeekBuilder.uniqueId("x", used))
        assertEquals("y", WeekBuilder.uniqueId("y", used))
    }
}
