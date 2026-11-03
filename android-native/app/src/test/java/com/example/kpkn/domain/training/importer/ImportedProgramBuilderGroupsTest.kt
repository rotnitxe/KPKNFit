package com.example.kpkn.domain.training.importer

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.exercises.catalogv2.catalogV2SelectionIssues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Constructor de H3e: grupos seguidos del mismo ejercicio se funden, y las libras pasan a kilos. Casos escritos a mano. */
class ImportedProgramBuilderGroupsTest {

    @Test
    fun `la misma identidad se agrupa aunque una decision sea por nombre y otra por aparicion`() {
        val draft = draftOf(
            ImportedExercise(id = "a", rawName = "Remo", sets = 3, repsMin = 8, repsMax = 8),
            ImportedExercise(id = "b", rawName = "Remo", sets = 2, repsMin = 10, repsMax = 10, catalogSelectionId = "custom:barra"),
        )
        val custom = ExerciseMuscleInfo(id = "custom:barra", name = "Remo con barra", isCustom = true)
        val program = ImportedProgramBuilder.build(draft, mapOf("Remo" to custom.id), listOf(1), "same", customExercises = listOf(custom))
        val exercise = sessionsOf(program).single().exercises.single()
        assertEquals(custom.id, exercise.exerciseDbId)
        assertEquals(5, exercise.sets.size)
    }

    @Test
    fun `apariciones contiguas con distinta identidad conservan sus variantes`() {
        val draft = draftOf(
            ImportedExercise(id = "a", rawName = "Remo", sets = 3, repsMin = 8, repsMax = 8, catalogSelectionId = "custom:barra"),
            ImportedExercise(id = "b", rawName = "Remo", sets = 2, repsMin = 10, repsMax = 10, catalogSelectionId = "custom:mancuerna"),
        )
        val customs = listOf(
            ExerciseMuscleInfo(id = "custom:barra", name = "Remo con barra", isCustom = true),
            ExerciseMuscleInfo(id = "custom:mancuerna", name = "Remo con mancuerna", isCustom = true),
        )
        val program = ImportedProgramBuilder.build(draft, emptyMap(), listOf(1), "variants", customExercises = customs)
        val exercises = sessionsOf(program).single().exercises
        assertEquals(listOf("custom:barra", "custom:mancuerna"), exercises.map { it.exerciseDbId })
        assertEquals(listOf(3, 2), exercises.map { it.sets.size })
    }

    @Test
    fun `un ejercicio personal elegido conserva su identidad y prescripcion`() {
        val draft = draftOf(ImportedExercise(id = "e1", rawName = "Mi remo", sets = 3, repsMin = 8, repsMax = 10, notes = "Pausa"))
        val custom = ExerciseMuscleInfo(id = "custom:remo-1", name = "Remo personal", isCustom = true, movementPattern = "Tirón")
        val program = ImportedProgramBuilder.build(
            draft, resolved = mapOf("Mi remo" to custom.id), weekdays = listOf(1), programId = "custom-import",
            customExercises = listOf(custom),
        )
        val session = sessionsOf(program).single()
        val exercise = session.exercises.single()
        assertEquals("Remo personal", exercise.name)
        assertEquals(custom.id, exercise.exerciseDbId)
        assertEquals(3, exercise.sets.size)
        assertEquals(listOf("Pausa"), exercise.executionCues)
        assertTrue(session.catalogV2SelectionIssues().isEmpty())
    }

    @Test
    fun `ejercicios seguidos con el mismo nombre son un ejercicio con todas sus series`() {
        val draft = ProgramImportParser.parseText(
            "Día 1\nSentadilla 50%x5, 60%x4, 70%x3x2\nPrensa 3x10\nSentadilla 4x6",
            title = "Fusión",
        )

        val exercises = sessionsOf(build(draft)).single().exercises

        // El grupo de Sentadilla de arriba se funde; la Sentadilla de abajo queda aparte porque hay una Prensa entre medias.
        assertEquals(listOf("Sentadilla", "Prensa", "Sentadilla"), exercises.map { it.name })
        val fused = exercises[0]
        assertEquals(listOf(50.0, 60.0, 70.0, 70.0), fused.sets.map { it.targetPercentageRM })
        assertEquals(listOf(5, 4, 3, 3), fused.sets.map { it.targetReps })
        assertEquals(4, fused.sets.map { it.id }.distinct().size)
        assertEquals(4, exercises[2].sets.size)
    }

    @Test
    fun `al fundir, el descanso es el primero que no es nulo y las notas no se repiten`() {
        val draft = draftOf(
            ImportedExercise(id = "w1-d1-e1", rawName = "Remo", sets = 3, repsMin = 8, repsMax = 8, loadKg = 60.0, notes = "Codos pegados"),
            ImportedExercise(
                id = "w1-d1-e2",
                rawName = "Remo",
                sets = 2,
                repsMin = 10,
                repsMax = 10,
                loadKg = 50.0,
                restSeconds = 90,
                notes = "Codos pegados",
            ),
            ImportedExercise(id = "w1-d1-e3", rawName = "Remo", sets = 1, repsMin = 12, repsMax = 12, restSeconds = 120, notes = "Pausa arriba"),
        )

        val fused = sessionsOf(build(draft)).single().exercises.single()

        assertEquals(6, fused.sets.size)
        assertEquals(listOf(60.0, 60.0, 60.0, 50.0, 50.0, null), fused.sets.map { it.weight })
        assertEquals(90, fused.restTime)
        assertEquals(listOf("Codos pegados", "Pausa arriba"), fused.executionCues)
    }

    @Test
    fun `el mismo nombre separado por otro ejercicio no se funde`() {
        val draft = draftOf(
            ImportedExercise(id = "w1-d1-e1", rawName = "Remo", sets = 3, repsMin = 8, repsMax = 8),
            ImportedExercise(id = "w1-d1-e2", rawName = "Prensa", sets = 3, repsMin = 10, repsMax = 10),
            ImportedExercise(id = "w1-d1-e3", rawName = "Remo", sets = 3, repsMin = 8, repsMax = 8),
        )

        val exercises = sessionsOf(build(draft)).single().exercises

        assertEquals(listOf("Remo", "Prensa", "Remo"), exercises.map { it.name })
    }

    @Test
    fun `con libras el constructor pasa la carga a kilos al medio kilo mas cercano`() {
        val sheet = ImportSheet(
            name = "Week 1",
            rows = listOf(
                listOf("Exercise", "Sets", "Reps", "Weight (lb)"),
                listOf("Bench", "3", "5", "135"),
                listOf("Squat", "2", "5", "225"),
            ),
        )
        val draft = ProgramImportParser.parseSheets(listOf(sheet), title = "Libras")

        assertEquals(ImportLoadUnit.LB, draft.loadUnit)
        val exercises = sessionsOf(build(draft)).single().exercises
        assertEquals(listOf(61.0, 102.0), exercises.map { it.sets.first().weight })
        assertTrue(exercises.all { exercise -> exercise.sets.all { it.weight != null } })
    }

    @Test
    fun `con kilos la carga se queda como esta`() {
        val draft = ProgramImportParser.parseText("Día 1\nSentadilla 4x6 @120kg", title = "Kilos")

        val squat = sessionsOf(build(draft)).single().exercises.single()
        assertEquals(listOf(120.0, 120.0, 120.0, 120.0), squat.sets.map { it.weight })
    }

    private fun build(draft: ImportedProgramDraft): Program =
        ImportedProgramBuilder.build(draft, resolved = emptyMap(), weekdays = listOf(1, 3, 5), programId = "fus-1")

    private fun sessionsOf(program: Program): List<Session> =
        program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions

    private fun draftOf(vararg exercises: ImportedExercise): ImportedProgramDraft = ImportedProgramDraft(
        title = "Fusión",
        weeks = listOf(
            ImportedWeek(index = 1, days = listOf(ImportedDay(id = "w1-d1", name = "Día 1", exercises = exercises.toList()))),
        ),
    )
}
