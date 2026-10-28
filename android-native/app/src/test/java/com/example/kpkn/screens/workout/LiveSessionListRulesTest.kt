package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveSessionListRulesTest {

    @Test
    fun supersetKeepsRealGroupIdentitySessionOrderAndMemberIndices() {
        val first = Exercise(id = "a", name = "Sentadilla", supersetGroupRef = "group-real")
        val second = Exercise(id = "b", name = "Remo", supersetId = "group-real")
        val solo = Exercise(id = "c", name = "Plancha")

        val sections = buildSections(listOf(first, second, solo))

        assertEquals(2, sections.size)
        val group = sections[0] as ListSection.Superset
        assertEquals("group-real", group.groupId)
        assertEquals(listOf("a", "b"), group.members.map { it.first.id })
        assertEquals(listOf(0, 1), group.members.map { it.second })
        assertEquals("c", (sections[1] as ListSection.Single).exercise.id)
    }

    @Test
    fun unilateralRowsKeepTheirOwnExecutionKeysAndLabels() {
        val exercise = Exercise(
            id = "a",
            name = "Sentadilla búlgara",
            isUnilateral = true,
            sets = listOf(ExerciseSet(id = "s0")),
        )

        val rows = rowsForSet(exercise, 0, 0, exercise.sets.single(), emptyMap(), "S1")

        assertEquals(listOf("a_0_L", "a_0_R"), rows.map { it.key })
        assertEquals(listOf("S1 I", "S1 D"), rows.map { it.label })
        assertEquals(listOf("left", "right"), rows.map { it.side })
    }

    @Test
    fun performedEffortNeverStartsFromThePlannedPrescription() {
        val exercise = Exercise(
            id = "a",
            name = "Press banca",
            sets = listOf(ExerciseSet(id = "s0", targetRIR = 2, targetRPE = 8.0, intensityMode = IntensityMode.RIR)),
        )
        val pending = rowsForSet(exercise, 0, 0, exercise.sets.single(), emptyMap(), "S1").single()

        assertNull(initialEffortValue(pending, IntensityMode.RIR))
        assertEquals("—", performedEffort(null))

        val recorded = CompletedSet(id = "c0", weight = 100.0, reps = 5, rir = 3)
        val done = rowsForSet(exercise, 0, 0, exercise.sets.single(), mapOf("a_0" to recorded), "S1").single()
        assertEquals(3.0, initialEffortValue(done, IntensityMode.RIR))
        assertEquals("RIR 3", performedEffort(done.completed))
    }

    @Test
    fun effortChoicesMatchThePrototypeScales() {
        assertEquals(listOf(4.0, 3.0, 2.0, 1.0, 0.0, null), effortOptions(IntensityMode.RIR))
        assertEquals(listOf(6.0, 7.0, 8.0, 9.0, 10.0, null), effortOptions(IntensityMode.RPE))
        assertEquals(0.0, initialEffortValue(
            ListSetRow(
                exercise = Exercise(id = "a", name = "A"),
                exerciseOrder = 0,
                setIndex = 0,
                side = null,
                key = "a_0",
                label = "S1",
                planned = ExerciseSet(id = "s0"),
                completed = CompletedSet(id = "c0", isFailure = true),
            ),
            IntensityMode.RIR,
        ))
    }

    @Test
    fun emptySupersetSlotsDoNotProduceRows() {
        val exercise = Exercise(
            id = "a",
            name = "Superserie",
            sets = listOf(ExerciseSet(id = "s0"), ExerciseSet(id = "slot", isEmptySlot = true)),
        )

        val rows = rowsFor(exercise, 0, emptyMap())

        assertEquals(listOf("a_0"), rows.map { it.key })
    }
}
