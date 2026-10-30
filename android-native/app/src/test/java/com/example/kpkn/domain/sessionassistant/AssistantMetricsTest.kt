package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.VolumeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Métricas del asistente (pulido `as-met-completa`): el detalle de un músculo suma exactamente su cifra. */
class AssistantMetricsTest {

    private val index: Map<String, ExerciseMuscleInfo> = listOf(
        ExerciseMuscleInfo(
            id = "bulgarian",
            name = "Sentadilla búlgara",
            involvedMuscles = listOf(
                InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, 1.0),
                InvolvedMuscle("Glúteos", MuscleRole.SECONDARY, 0.5),
                InvolvedMuscle("Aductores", MuscleRole.SECONDARY, 0.5),
            ),
        ),
        ExerciseMuscleInfo(
            id = "thrust",
            name = "Hip thrust",
            involvedMuscles = listOf(
                InvolvedMuscle("Glúteos", MuscleRole.PRIMARY, 1.0),
                InvolvedMuscle("Isquiosurales", MuscleRole.SECONDARY, 0.25),
            ),
        ),
        ExerciseMuscleInfo(
            id = "legext",
            name = "Extensión de cuádriceps",
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, 1.0)),
        ),
    ).associateBy { it.id }

    private fun exercise(dbId: String, name: String, sets: Int) = Exercise(
        id = "ex-$dbId",
        name = name,
        exerciseDbId = dbId,
        sets = List(sets) { ExerciseSet(id = "$dbId-$it", targetReps = 10, weight = 50.0) },
    )

    private val session = Session(
        id = "s",
        name = "Legs II",
        exercises = listOf(
            exercise("bulgarian", "Sentadilla búlgara", 4),
            exercise("thrust", "Hip thrust", 3),
            exercise("legext", "Extensión de cuádriceps", 2),
        ),
    )

    @Test
    fun breakdown_matchesTheUsualVolumeFigure() {
        val usual = VolumeCalculator.calculateUnifiedMuscleVolume(listOf(session), index.values.toList())
            .associate { it.muscleName to it.displayVolume }
        val rows = AssistantMuscleBreakdown.rows(session, index)
        assertEquals(usual.keys, rows.map { it.muscle }.toSet())
        rows.forEach { row -> assertEquals(row.muscle, usual.getValue(row.muscle), row.volume, 1e-9) }
        assertEquals("de más a menos series", rows.map { it.volume }.sortedDescending(), rows.map { it.volume })
    }

    @Test
    fun everyLineBelongsToAnExerciseOfTheSession_andSumsToTheFigure() {
        val rows = AssistantMuscleBreakdown.rows(session, index)
        val gluteos = rows.first { it.muscle == "Glúteos" }
        assertEquals(listOf("Sentadilla búlgara", "Hip thrust"), gluteos.lines.map { it.exerciseName })
        assertEquals(4 * 0.5 + 3 * 1.0, gluteos.lines.sumOf { it.contribution }, 1e-9)
        assertEquals(5.0, gluteos.volume, 1e-9)
        val quad = rows.first { it.muscle == "Cuádriceps" }
        assertEquals(6.0, quad.volume, 1e-9)
        assertEquals(listOf(4, 2), quad.lines.map { it.sets })
    }

    @Test
    fun emptySession_hasNoRows() {
        assertTrue(AssistantMuscleBreakdown.rows(Session(id = "e", name = "Vacía"), index).isEmpty())
    }

    @Test
    fun durationPreview_followsTheCheckedBoxes() {
        val detail = { seconds: Int ->
            AssistantSuggestionDetail(
                id = "d$seconds",
                label = "x",
                action = AssistantDetailAction.RemoveFailure,
                effect = AssistantActionEffect(deltaSeconds = -seconds, deltaSets = 0),
            )
        }
        val none = AssistantSuggestionPreview.duration(59, 50, emptyList())
        assertEquals("Elige qué opciones quieres aplicar.", none.text)
        assertEquals(59, none.afterMinutes)

        val some = AssistantSuggestionPreview.duration(59, 50, listOf(detail(120)))
        assertEquals(2, some.savedMinutes)
        assertEquals(57, some.afterMinutes)
        assertFalse(some.fits)
        assertEquals("Con las opciones marcadas ahorras 2 min; la sesión seguiría 7 min por encima del objetivo.", some.text)

        val enough = AssistantSuggestionPreview.duration(59, 50, listOf(detail(120), detail(180), detail(300)))
        assertTrue(enough.fits)
        assertEquals("Con las opciones marcadas la sesión cabría en tu tiempo.", enough.text)
    }

    @Test
    fun muscleLimitPreview_comparesBeforeAndAfter() {
        val over = Session(
            id = "o",
            name = "Glúteos",
            exercises = listOf(exercise("thrust", "Hip thrust", 5), exercise("bulgarian", "Sentadilla búlgara", 3)),
        )
        val params = AssistantActionParameters(exerciseIndex = index)
        val rows = AssistantMuscleBreakdown.rows(over, index)
        val remove = AssistantSuggestionDetail(
            id = "limit_detail_volume_ex-thrust",
            label = "Quitar 1 serie de Hip thrust",
            action = AssistantDetailAction.ReduceSet(exerciseId = "ex-thrust"),
        )
        // Glúteos: 5 × 1 + 3 × 0,5 = 6,5. Con límite 6 pasa; quitando una serie queda en 5,5.
        val with = AssistantSuggestionPreview.muscleLimit(over, rows, 6.0, listOf(remove), params, index)!!
        assertEquals("Glúteos", with.muscle)
        assertEquals(6.5, with.before, 1e-9)
        assertEquals(5.5, with.after, 1e-9)
        assertTrue(with.fits)
        assertEquals("Quitando 1 serie, Glúteos queda en 5,5: dentro de tu límite.", with.text)

        val without = AssistantSuggestionPreview.muscleLimit(over, rows, 6.0, emptyList(), params, index)!!
        assertFalse(without.fits)
        assertEquals("Sin cambios, Glúteos sigue en 6,5: por encima de tu límite.", without.text)

        assertNull(AssistantSuggestionPreview.muscleLimit(over, rows, 20.0, listOf(remove), params, index))
    }

    @Test
    fun numberFormat_usesDecimalCommaWithoutTrailingZero() {
        assertEquals("10", AssistantSuggestionPreview.number(10.0))
        assertEquals("10,5", AssistantSuggestionPreview.number(10.5))
    }

    @Test
    fun limitStatus_saysWithinOrWhatPassesEachLimit() {
        val other = Session(
            id = "otra",
            name = "Glúteo",
            exercises = listOf(exercise("thrust", "Hip thrust", 20)),
        )
        val statuses = AssistantLimitStatus.compute(
            session = session,
            weekSessions = listOf(session, other),
            maxRpe = 9.0,
            maxExercisesPerMuscle = 6,
            maxVolumePerMuscleSession = 5.0,
            maxVolumePerMuscleWeekly = 24.0,
            maxSamePattern = null,
            exerciseIndex = index,
        )
        assertEquals("Cuádriceps tiene 6", statuses.volumePerMuscleSession!!.text)
        assertFalse(statuses.volumePerMuscleSession!!.within)
        assertEquals("Glúteos lleva 25", statuses.volumePerMuscleWeekly!!.text)
        assertFalse(statuses.volumePerMuscleWeekly!!.within)
        assertTrue(statuses.exercisesPerMuscle!!.within)
        assertTrue(statuses.exercisesPerMuscle!!.text.startsWith("El máximo es 2"))
        assertNull(statuses.samePattern)
        assertTrue(statuses.anyClash)
    }

    @Test
    fun limitStatus_noClashWhenEverythingIsWithin() {
        val statuses = AssistantLimitStatus.compute(
            session = session, weekSessions = listOf(session), maxRpe = null, maxExercisesPerMuscle = null,
            maxVolumePerMuscleSession = 12.0, maxVolumePerMuscleWeekly = 24.0, maxSamePattern = null, exerciseIndex = index,
        )
        assertEquals("El máximo: Cuádriceps 6 · dentro", statuses.volumePerMuscleSession!!.text)
        assertFalse(statuses.anyClash)
    }
}
