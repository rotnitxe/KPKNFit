package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.JointRegion
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PostExerciseFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El borrador del feedback tras el ejercicio y lo que guarda «Guardar» (LV6, B1/B3): un solo borrador para la pantalla completa y la
 * tarjeta, la técnica guardada, ya sin la pregunta de intensidad y con las molestias previas que siguen o se resolvieron.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PostExerciseFeedbackDraftTest {

    private val squat = Exercise(id = "squat", name = "Sentadilla trasera", exerciseDbId = "db-squat")
    private val bench = Exercise(id = "bench", name = "Press banca")
    private val curl = Exercise(id = "curl", name = "Curl")

    private fun payloads(
        exercises: List<Exercise>,
        draft: PostExerciseFeedbackDraft,
        previous: Map<String, PostExerciseFeedback> = emptyMap(),
        linked: List<LinkedDiscomfort> = emptyList(),
    ) = buildPostExerciseFeedbackPayloads(
        feedbackExercises = exercises,
        draft = draft,
        previousFeedback = previous,
        linked = linked,
        canonicalKeyOf = { "canon-${it.id}" },
        nameOf = { it.name },
    )

    @Test
    fun aFreshDraftStartsAtEightWithNoDiscomfort() {
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), emptyMap())
        assertEquals(8, draft.technical("squat"))
        assertTrue(draft.discomfortIds.isEmpty())
        assertEquals("", draft.query)
    }

    @Test
    fun theDraftStartsFromWhatWasAlreadySaved() {
        val saved = mapOf(
            "squat" to PostExerciseFeedback(
                exerciseId = "squat",
                exerciseName = "Sentadilla",
                technicalQuality = 6,
                discomfortIds = listOf("knee_patellar", "none"),
            ),
        )
        val draft = PostExerciseFeedbackDraft.from(listOf(squat, bench), saved)
        assertEquals(6, draft.technical("squat"))
        assertEquals(8, draft.technical("bench"))
        assertEquals(listOf("knee_patellar"), draft.discomfortIds.toList())
    }

    @Test
    fun theTechniqueStaysInsideTheScale() {
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), emptyMap())
        draft.setTechnical("squat", 0)
        assertEquals(1, draft.technical("squat"))
        draft.setTechnical("squat", 99)
        assertEquals(10, draft.technical("squat"))
    }

    @Test
    fun discomfortsAreChosenOnceAndNeverNone() {
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), emptyMap())
        draft.select("lumbar")
        draft.select("lumbar")
        draft.select("none")
        draft.select("")
        assertEquals(listOf("lumbar"), draft.discomfortIds.toList())
        draft.toggle("lumbar")
        assertTrue(draft.discomfortIds.isEmpty())
        draft.toggle("lumbar")
        assertTrue(draft.isSelected("lumbar"))
        draft.deselect("lumbar")
        assertFalse(draft.isSelected("lumbar"))
    }

    @Test
    fun saveStoresTheTechniqueTheDiscomfortsAndNoIntensityQuestion() {
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), emptyMap())
        draft.setTechnical("squat", 6)
        draft.select("knee_patellar")
        val saved = payloads(listOf(squat), draft).single()
        assertEquals("squat", saved.exerciseId)
        assertEquals("db-squat", saved.exerciseDbId)
        assertEquals("canon-squat", saved.canonicalExerciseId)
        assertEquals("Sentadilla trasera", saved.exerciseName)
        assertEquals(6, saved.technicalQuality)
        assertEquals(listOf("knee_patellar"), saved.discomfortIds)
        assertEquals(listOf("knee_patellar"), saved.stillPresentDiscomfortIds)
        // Ya no se pregunta «Qué tan intenso fue»: sin dato previo no se inventa uno (antes salía 8,0 aunque no se mostrara el deslizador).
        assertNull(saved.perceivedIntensityRpe)
        assertFalse(saved.perceivedFailure)
    }

    @Test
    fun withoutDiscomfortSaveWritesNone() {
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), emptyMap())
        val saved = payloads(listOf(squat), draft).single()
        assertEquals(8, saved.technicalQuality)
        assertEquals(listOf("none"), saved.discomfortIds)
        assertEquals(listOf("none"), saved.stillPresentDiscomfortIds)
    }

    @Test
    fun theIntensityAndTheFailureThatTheVoiceWroteAreKept() {
        val before = PostExerciseFeedback(
            exerciseId = "squat",
            exerciseName = "Sentadilla trasera",
            technicalQuality = 7,
            notes = "bajé más lento",
            perceivedIntensityRpe = 9.0,
            perceivedFailure = true,
        )
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), mapOf("squat" to before))
        val saved = payloads(listOf(squat), draft, previous = mapOf("squat" to before)).single()
        assertEquals(9.0, saved.perceivedIntensityRpe!!, 1e-9)
        assertTrue(saved.perceivedFailure)
        assertEquals("bajé más lento", saved.notes)
        assertEquals(7, saved.technicalQuality)
    }

    @Test
    fun aSupersetSavesOneFeedbackPerExerciseWithTheSharedDiscomforts() {
        val draft = PostExerciseFeedbackDraft.from(listOf(squat, bench), emptyMap())
        draft.setTechnical("squat", 9)
        draft.setTechnical("bench", 5)
        draft.select("shoulder_anterior")
        val saved = payloads(listOf(squat, bench), draft)
        assertEquals(listOf("squat", "bench"), saved.map { it.exerciseId })
        assertEquals(listOf(9, 5), saved.map { it.technicalQuality })
        assertTrue(saved.all { it.discomfortIds == listOf("shoulder_anterior") })
    }

    @Test
    fun aPreviousDiscomfortThatStillHurtsIsKeptAndOneMarkedResolvedIsDropped() {
        val knee = PostExerciseFeedback(
            exerciseId = "curl",
            exerciseName = "Curl",
            technicalQuality = 8,
            discomfortIds = listOf("elbow_medial", "wrist_hand"),
        )
        val linked = listOf(
            LinkedDiscomfort("elbow_medial", "Codo (cara interna)", "Curl"),
            LinkedDiscomfort("wrist_hand", "Muñeca / mano", "Curl"),
        )
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), emptyMap())
        draft.setStillPresent("wrist_hand", false)
        val saved = payloads(listOf(squat), draft, previous = mapOf("curl" to knee), linked = linked)
        // El ejercicio actual hereda la que sigue (sin que la haya elegido) y la resuelta no.
        assertEquals(listOf("elbow_medial"), saved.first { it.exerciseId == "squat" }.stillPresentDiscomfortIds)
        // El ejercicio anterior se actualiza: solo le queda la que sigue.
        assertEquals(listOf("elbow_medial"), saved.first { it.exerciseId == "curl" }.stillPresentDiscomfortIds)
    }

    @Test
    fun resolvingEverythingLeavesNoneOnThePreviousExercise() {
        val before = PostExerciseFeedback(
            exerciseId = "curl",
            exerciseName = "Curl",
            technicalQuality = 8,
            discomfortIds = listOf("elbow_medial"),
        )
        val linked = listOf(LinkedDiscomfort("elbow_medial", "Codo (cara interna)", "Curl"))
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), emptyMap())
        draft.setStillPresent("elbow_medial", false)
        val saved = payloads(listOf(squat), draft, previous = mapOf("curl" to before), linked = linked)
        assertEquals(listOf("none"), saved.first { it.exerciseId == "curl" }.stillPresentDiscomfortIds)
        assertEquals(listOf("none"), saved.first { it.exerciseId == "squat" }.stillPresentDiscomfortIds)
    }

    @Test
    fun theHolderGivesTheSameDraftToTheFullScreenAndTheCard() {
        val holder = PostExerciseFeedbackDraftHolder()
        val fullScreen = holder.draftFor(listOf(squat), emptyMap())
        fullScreen.setTechnical("squat", 4)
        fullScreen.select("lumbar")
        val card = holder.draftFor(listOf(squat), emptyMap())
        assertSame(fullScreen, card)
        assertEquals(4, card.technical("squat"))
        assertEquals(listOf("lumbar"), card.discomfortIds.toList())
    }

    @Test
    fun anotherExerciseOrASavedFeedbackStartsAFreshDraft() {
        val holder = PostExerciseFeedbackDraftHolder()
        val first = holder.draftFor(listOf(squat), emptyMap())
        first.setTechnical("squat", 3)
        val other = holder.draftFor(listOf(bench), emptyMap())
        assertNotSame(first, other)
        assertEquals(8, other.technical("bench"))
        holder.clear()
        val afterSaving = holder.draftFor(listOf(bench), emptyMap())
        assertNotSame(other, afterSaving)
    }

    @Test
    fun linkedDiscomfortsAreTheUnresolvedOnesOfOtherExercisesThatShareAJoint() {
        val before = mapOf(
            "curl" to PostExerciseFeedback(
                exerciseId = "curl",
                exerciseName = "Curl",
                technicalQuality = 8,
                discomfortIds = listOf("elbow_medial", "knee_patellar"),
            ),
            "squat" to PostExerciseFeedback(
                exerciseId = "squat",
                exerciseName = "Sentadilla trasera",
                technicalQuality = 8,
                discomfortIds = listOf("lumbar"),
            ),
        )
        // El ejercicio actual es un press: carga el codo. La del curl en el codo se relaciona; la de rodilla no; la del propio ejercicio tampoco.
        val linked = linkedDiscomfortsFor(
            feedbackExercises = listOf(bench),
            feedbackByExerciseId = before,
            articulationsOf = { listOf(JointRegion.ELBOW, JointRegion.SHOULDER) },
        )
        assertEquals(listOf("elbow_medial"), linked.map { it.id })
        assertEquals("Curl", linked.single().reportedIn)

        val ownOnly = linkedDiscomfortsFor(
            feedbackExercises = listOf(squat),
            feedbackByExerciseId = before,
            articulationsOf = { listOf(JointRegion.LUMBAR) },
        )
        assertTrue("la molestia del propio ejercicio no se pregunta", ownOnly.isEmpty())
    }
}
