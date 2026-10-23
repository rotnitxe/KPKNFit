package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import org.junit.Assert.*
import org.junit.Test

class ApreXrmCaptureFactoryTest {
    private val exercise = Exercise("press", "Press", recipeDayId = "d1", recipeSlotId = "t1",
        sets = Apre.block(200.0, ApreVariant.APRE_10).map { row ->
            ExerciseSet("s${row.ordinal}", weight = row.loadKg, targetReps = row.targetReps,
                purpose = row.purpose, amrapMinimumReps = row.minimumReps, loadModeV2 = LoadModeV2.LOAD)
        }, apreBlock = ApreBlockReference(10, listOf("s1", "s2", "s3", "s4")))
    private val done = CompletedSet("actual-s4", weight = 155.0, originalLoad = 155.0, reps = 17,
        purpose = SetPurpose.AMRAP, setIndex = 3, loadMode = LoadModeV2.LOAD)

    @Test fun s4De155Por17Propone162YMedioComo10RmSinModificarCapacidades() {
        val capture = captureApreXrm(exercise, 3, done, capturedAtMs = 100L, sourceProgramId = "program")!!
        assertEquals(162.5, capture.reference.capturedLoadKg!!, 0.0)
        assertEquals(PlanLoadReferenceKind.OBSERVED_WORKING_SET, capture.reference.kind)
        assertEquals(10, capture.reference.repMin)
        assertEquals(10, capture.reference.repMax)
        assertEquals(ExerciseLoadReferenceStore.APRE_XRM, capture.reference.metadata!!.origin)
        assertNull(exercise.reference1RM)
        assertNull(exercise.referenceTrainingMax)
        assertEquals(150.0, exercise.sets[3].weight!!, 0.0)
    }

    @Test fun procedenciaDistingueElSlotDeLasIdentidadesDeSerie() {
        val capture = captureApreXrm(exercise, 3, done)!!
        assertEquals("t1", capture.reference.sourceSlotId)
        assertEquals("s4", capture.sourcePlannedSetId)
        assertEquals("s4", capture.reference.metadata!!.sourceSetId)
        assertEquals("actual-s4", capture.reference.metadata!!.sourceId)
        assertEquals("d1", capture.sourceRecipeDayId)
        assertNull(capture.reference.capturedAtMs) // Un registro legado no recibe una fecha inventada.
    }

    @Test fun repsCompletasNoDescuentanOtraVezLasParciales() {
        val full = Apre.validReps(13, 2)
        val capture = captureApreXrm(exercise, 3, done.copy(reps = full, partialReps = 2))!!
        assertEquals(155.0, capture.reference.capturedLoadKg!!, 0.0)
        assertTrue(capture.reference.metadata!!.reason!!.contains("11 reps válidas"))
    }

    @Test fun fuenteS3NormalCalibradorYBloqueEditadoNoGeneranXrm() {
        assertNull(captureApreXrm(exercise, 2, done))
        assertNull(captureApreXrm(exercise.copy(apreBlock = null), 3, done))
        assertNull(captureApreXrm(exercise, 3, done.copy(purpose = SetPurpose.CALIBRATOR)))
        assertNull(captureApreXrm(exercise, 3, done.copy(skipped = true)))
        val invalid = exercise.copy(sets = exercise.sets.dropLast(1))
        assertNull(captureApreXrm(invalid, 2, done))
    }

    @Test fun corporalAsistidaYContextoDeOtroLadoNoGeneranXrm() {
        assertNull(captureApreXrm(exercise, 3, done.copy(loadMode = LoadModeV2.BODYWEIGHT)))
        assertNull(captureApreXrm(exercise, 3, done.copy(loadMode = LoadModeV2.ASSISTED)))
        val left = done.copy(side = "left")
        val rightContext = plannedApreContext(exercise, exercise.sets[3], "right", done)
        assertNull(captureApreXrm(exercise, 3, left, contextOverride = rightContext))
        assertEquals("left", captureApreXrm(exercise, 3, left)!!.reference.side)
    }

    @Test fun lastreUsaSoloLaCargaExternaYRechazaUnaCargaDesconocida() {
        val lastre = done.copy(weight = 100.0, originalLoad = 20.0, loadMode = LoadModeV2.LASTRE)
        assertEquals(21.0, captureApreXrm(exercise, 3, lastre, incrementKg = 1.0)!!.reference.capturedLoadKg!!, 0.0)
        assertNull(captureApreXrm(exercise, 3, lastre.copy(originalLoad = null)))
        assertNull(captureApreXrm(exercise, 3, done.copy(originalLoad = Double.NaN)))
    }
}
