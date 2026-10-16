package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.domain.workout.SupersetRules
import com.example.kpkn.screens.sessioneditor.components.sheets.toggleSupersetMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ER1c2: «Duplicar ejercicio» y la selección de la superserie por casillas (funciones puras, sin VM). */
class SessionEditorQuickActionsTest {

    private fun reps(id: String) = ExerciseSet(id = id, targetRepsRange = RepRange(8, 10))

    /** Un ejercicio de catálogo: identidad sin «custom:» y su `occurrenceId`, como lo deja la sesión. */
    private fun catalogExercise(id: String, name: String) = Exercise(
        id = id,
        name = name,
        exerciseDbId = "bench_press",
        catalogDefinitionId = "bench_press",
        catalogConfigurationId = "bench_press__barbell",
        catalogRevision = "rev-1",
        performanceProfileId = "perfil-1",
        occurrenceId = "occ-$id",
        sets = listOf(reps("$id-s1"), reps("$id-s2")),
    )

    @Test
    fun duplicarPoneLaCopiaJustoDebajoConIdsNuevosYOccurrenceNuevo() {
        val press = catalogExercise("press", "Press banca")
        val remo = catalogExercise("remo", "Remo")
        val session = Session(
            id = "sesion",
            name = "Empuje",
            parts = listOf(SessionPart(id = "parte", name = "Empuje", exercises = listOf(press, remo))),
        )
        var next = 0
        val updated = duplicateExerciseInSession(session, "parte", "press", newId = { "nuevo-${++next}" })

        val exercises = updated.parts.single().exercises
        assertEquals(listOf("press", exercises[1].id, "remo"), exercises.map { it.id })
        val copy = exercises[1]
        assertEquals("Press banca", copy.name)
        assertEquals(2, copy.sets.size)
        assertEquals(setOf("press-s1", "press-s2"), press.sets.map { it.id }.toSet())
        assertEquals(true, copy.sets.none { it.id == "press-s1" || it.id == "press-s2" })
        assertNotEquals("occ-press", copy.occurrenceId)
        assertNull(copy.supersetGroupRef)
    }

    @Test
    fun duplicarUnEjercicioManualConservaSuOccurrence() {
        val manual = Exercise(
            id = "manual",
            name = "Ejercicio propio",
            exerciseDbId = "custom:mi-ejercicio",
            occurrenceId = null,
            sets = listOf(reps("manual-s1")),
        )
        val session = Session(id = "sesion", name = "Libre", exercises = listOf(manual))
        var next = 0
        val updated = duplicateExerciseInSession(session, null, "manual", newId = { "nuevo-${++next}" })

        assertEquals(2, updated.exercises.size)
        assertNull(updated.exercises[1].occurrenceId)
    }

    @Test
    fun laCopiaDeUnMiembroSaleDeLaSuperserie() {
        val press = catalogExercise("press", "Press banca").copy(
            supersetGroupRef = "grupo",
            supersetId = "grupo",
            supersetRestBetween = 60,
            supersetRestAfter = 120,
        )
        val session = Session(id = "sesion", name = "Empuje", exercises = listOf(press))
        var next = 0
        val updated = duplicateExerciseInSession(session, null, "press", newId = { "nuevo-${++next}" })

        val copy = updated.exercises[1]
        assertNull(copy.supersetGroupRef)
        assertNull(copy.supersetId)
        assertNull(copy.supersetRestBetween)
        assertNull(copy.supersetRestAfter)
    }

    @Test
    fun marcarDeDosACuatroYNoEntraElQuinto() {
        var selected = listOf("a")
        selected = toggleSupersetMember(selected, "b")
        assertEquals(listOf("a", "b"), selected)
        selected = toggleSupersetMember(selected, "c")
        selected = toggleSupersetMember(selected, "d")
        assertEquals(listOf("a", "b", "c", "d"), selected)
        assertEquals(listOf("a", "b", "c", "d"), toggleSupersetMember(selected, "e"))
        assertEquals(listOf("a", "c", "d"), toggleSupersetMember(selected, "b"))
    }

    @Test
    fun crearSuperseriePorTresMarcadosCreaUnaDeTres() {
        val a = Exercise(id = "a", name = "A", sets = listOf(reps("a-s1")))
        val b = Exercise(id = "b", name = "B", sets = listOf(reps("b-s1")))
        val c = Exercise(id = "c", name = "C", sets = listOf(reps("c-s1")))
        val session = Session(
            id = "sesion",
            name = "Sesión",
            parts = listOf(SessionPart(id = "parte", name = "Parte", exercises = listOf(a, b, c))),
        )
        val created = SupersetRules.createSuperset(
            session = session,
            groupId = "grupo",
            exerciseIds = listOf("a", "b", "c"),
            restBetweenExercises = 60,
            restAfterSuperset = 120,
            anchorPartId = "parte",
            anchorExerciseId = "a",
        )

        val group = created.allSupersetGroups().single()
        assertEquals(listOf("a", "b", "c"), group.exerciseOrder)
        assertEquals(3, created.allExercises().count { it.supersetGroupRef == "grupo" })
    }
}
