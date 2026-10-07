package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WarmupExercise
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.PatternFamily
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.split.SplitTestSupport.LEGS
import com.example.kpkn.domain.training.split.SplitTestSupport.PUSH
import com.example.kpkn.domain.training.split.SplitTestSupport.session
import com.example.kpkn.domain.training.split.SplitTestSupport.week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** Cómo se reúnen los ejercicios de una semana en unidades que se mueven enteras. */
class WeekUnitsTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val resolver get() = SplitTestSupport.resolver

    private fun gather(vararg sessions: com.example.kpkn.data.models.Session): List<MovableUnit> =
        WeekGather.gather(week("w", sessions.toList()), resolver)

    @Test
    fun each_exercise_is_a_unit_with_its_tier_group_and_pattern() {
        val push = gather(session("push", "Empuje", 1, PUSH))
        assertEquals(6, push.size)
        assertEquals(listOf(0, 1, 0, 2, 2, 2), push.map { it.tier })
        assertEquals(listOf(true, false, true, false, false, false), push.map { it.isHeavy })
        assertTrue(push.all { it.group == SplitGroup.PUSH && it.kind == UnitKind.STRENGTH })
        assertEquals(PatternFamily.HORIZONTAL_PUSH, push[0].pattern)
        assertEquals(PatternFamily.VERTICAL_PUSH, push[2].pattern)
        assertEquals(listOf(4, 3, 3, 3, 3, 3), push.map { it.sets })
        assertEquals(listOf("0:0:${CatalogIds.BP}", "0:1:${CatalogIds.BP_INC_DB}"), push.take(2).map { it.key })
        assertEquals("las claves son únicas", push.size, push.map { it.key }.toSet().size)
        // La identidad de un ejercicio es su definición de catálogo: «press de banca» con barra o con mancuernas es el mismo.
        fun definitionOf(configuration: String) = requireNotNull(resolver.traitsOf(SplitTestSupport.exercise("x", configuration))?.definitionId)
        assertEquals(setOf(definitionOf(CatalogIds.BP)), push[0].exerciseKeys)
        assertEquals(setOf(definitionOf(CatalogIds.OHP)), push[2].exerciseKeys)
        assertTrue(definitionOf(CatalogIds.BP) != definitionOf(CatalogIds.BP_INC_DB))

        val legs = gather(session("legs", "Pierna", 5, LEGS))
        assertEquals(listOf(0, 0, 1, 2, 2, 3), legs.map { it.tier })
        assertTrue(legs.all { it.group == SplitGroup.LEGS })
    }

    @Test
    fun the_seconds_of_the_units_add_up_to_the_estimate_of_the_whole_session() {
        listOf(
            session("push", "Empuje", 1, PUSH),
            session("legs", "Pierna", 5, LEGS),
            session("ss", "Con superserie", 2, PUSH, supersets = mapOf(4 to "ss1", 5 to "ss1")),
            session("wu", "Con calentamiento", 3, PUSH, warmup = listOf(WarmupExercise(id = "w1", name = "Movilidad", duration = 90))),
        ).forEach { session ->
            val units = gather(session)
            val estimate = SessionDurationEstimator.estimate(session).totalSeconds
            assertEquals(session.name, estimate, units.sumOf { it.seconds } + WeekGather.GENERAL_WARMUP_SECONDS)
        }
    }

    @Test
    fun a_superset_is_one_unit_and_keeps_its_group() {
        val units = gather(session("push", "Empuje", 1, PUSH, supersets = mapOf(4 to "ss1", 5 to "ss1")))
        assertEquals(5, units.size)
        val superset = units.single { it.exercises.size == 2 }
        assertEquals(listOf("push-e4", "push-e5"), superset.exercises.map { it.id })
        assertEquals("ss1", superset.superset?.id)
        assertEquals(2, superset.strengthExerciseCount)
        assertEquals(6, superset.sets)
        assertEquals(2, superset.exerciseKeys.size)
    }

    @Test
    fun the_general_warmup_goes_to_the_first_strength_exercise() {
        val warmup = listOf(WarmupExercise(id = "w1", name = "Movilidad", duration = 90))
        val units = gather(session("push", "Empuje", 1, PUSH, warmup = warmup))
        assertEquals(warmup, units.first().warmup)
        assertTrue(units.drop(1).all { it.warmup.isEmpty() })
    }

    @Test
    fun cardio_parts_and_part_owned_mobility_are_blocks() {
        val cardio = SessionPart(
            id = "c#part",
            name = "Cardio",
            exercises = listOf(Exercise(id = "cardio-1", name = "cinta", cardioDetails = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 1200))),
            isCardioGroup = true,
            targetDurationMinutes = 20,
        )
        val mobility = SessionPart(
            id = "m#part",
            name = "Movilidad",
            isMobilityGroup = true,
            mobilitySeries = listOf(MobilitySeries(id = "ms1", name = "Apertura torácica", sets = 2, reps = "8")),
        )
        val units = gather(session("push", "Empuje", 1, PUSH, extraParts = listOf(cardio, mobility)))
        assertEquals(8, units.size)
        val cardioUnit = units.single { it.kind == UnitKind.CARDIO }
        assertEquals(cardio, cardioUnit.block)
        assertEquals(5, cardioUnit.tier)
        assertEquals(1, cardioUnit.exerciseCount)
        assertEquals(0, cardioUnit.strengthExerciseCount)
        assertTrue(cardioUnit.seconds >= 20 * 60)

        val mobilityUnit = units.single { it.kind == UnitKind.MOBILITY }
        assertNotNull(mobilityUnit.block)
        assertEquals(mobility.mobilitySeries, mobilityUnit.block?.mobilitySeries)
        assertTrue(mobilityUnit.block?.exercises.orEmpty().isEmpty())
        assertFalse("la movilidad iba tras la fuerza", mobilityUnit.blockLeading)
        assertEquals(-1, mobilityUnit.tier)
    }

    @Test
    fun an_exercise_mirrored_in_the_session_list_and_a_part_is_counted_once() {
        val base = session("push", "Empuje", 1, PUSH)
        val mirrored = base.copy(exercises = base.parts.first().exercises)
        val units = gather(mirrored)
        assertEquals(6, units.size)
        assertEquals(units.map { it.exercises.single().id }.toSet().size, units.size)
    }

    @Test
    fun an_exercise_the_catalog_does_not_know_is_unclassified_but_still_a_unit() {
        val mystery = Exercise(
            id = "m1",
            name = "Ejercicio inventado xyz",
            sets = List(3) { index -> com.example.kpkn.data.models.ExerciseSet(id = "m1-s$index", targetReps = 10) },
        )
        val units = gather(session("a", "A", 1, PUSH).copy(exercises = listOf(mystery)))
        val unit = units.single { it.exercises.firstOrNull()?.id == "m1" }
        assertFalse(unit.isClassified)
        assertEquals(SplitGroup.OTHER, unit.group)
        assertEquals(2, unit.tier)
    }

    @Test
    fun a_session_with_only_a_warmup_keeps_it_in_a_unit_of_its_own() {
        val onlyWarmup = session("a", "A", 1, emptyList(), warmup = listOf(WarmupExercise(id = "w1", name = "Movilidad", duration = 60)))
        val units = gather(onlyWarmup)
        assertEquals(1, units.size)
        assertEquals(UnitKind.MOBILITY, units.single().kind)
        assertEquals(onlyWarmup.warmup, units.single().warmup)
    }
}
