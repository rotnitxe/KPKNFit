package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vista previa de plantilla y resultado de Reemplazar / Añadir (pulido `as-pla-vista`, `as-pla-aplicar`). */
class TemplatePreviewBuilderTest {

    private val index = mapOf(
        "squat" to ExerciseMuscleInfo("squat", "Sentadilla", involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, 1.0))),
    )

    private fun exercise(id: String, sets: Int) = Exercise(
        id = id,
        name = "Sentadilla $id",
        exerciseDbId = "squat",
        restTime = 180,
        sets = List(sets) { ExerciseSet(id = "$id-$it", targetReps = 6, targetRPE = 8.0, weight = 80.0) },
    )

    private val template = SessionTemplate(
        id = "t",
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = "Leg Day",
        description = "",
        session = Session(
            id = "ts",
            name = "Leg Day",
            parts = listOf(SessionPart(id = "p1", name = "Compuestos", exercises = listOf(exercise("a", 3)))),
            exercises = listOf(exercise("b", 2)),
        ),
    )

    @Test
    fun parts_listLooseExercisesFirstThenEachGroup() {
        val parts = TemplatePreviewBuilder.parts(template)
        assertEquals(listOf("Ejercicios", "Compuestos"), parts.map { it.title })
        val line = parts.last().exercises.single()
        assertEquals(3, line.sets)
        assertEquals(6, line.reps)
        assertEquals(8.0, line.rpe!!, 1e-9)
        assertEquals(180, line.restSeconds)
    }

    @Test
    fun preview_carriesVolumeAndDuration() {
        val preview = TemplatePreviewBuilder.preview(template, current = null, targetMinutes = null, exerciseIndex = index)
        assertEquals(5.0, preview.volumenPorMusculo.getValue("Cuádriceps"), 1e-9)
        assertTrue(preview.duracionEstimada > 0)
        assertTrue(preview.advertencias.isEmpty())
        assertEquals(SessionTemplateApplyMode.REPLACE, preview.modoRecomendado)
    }

    @Test
    fun outcomes_areSimpleSums_andWarnWhenAppendPassesTheLimit() {
        val current = SessionTotals(exercises = 6, sets = 18, minutes = 59)
        val tpl = SessionTotals(exercises = 6, sets = 17, minutes = 35)
        val result = TemplatePreviewBuilder.outcomes(current, tpl, targetMinutes = 50)
        assertEquals(tpl, result.replace)
        assertEquals(SessionTotals(12, 35, 94), result.append)
        assertEquals(44, result.appendOverMinutes)
        assertNull(TemplatePreviewBuilder.outcomes(current, tpl, targetMinutes = null).appendOverMinutes)
        assertNull(TemplatePreviewBuilder.outcomes(current, tpl, targetMinutes = 200).appendOverMinutes)
    }
}
