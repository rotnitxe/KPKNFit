package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.CalibrationStatus
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.training.AmrapProgramPreview
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.relator.RelatorProgressionContext
import com.example.kpkn.data.protocols.ProgressionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorPlanProgressionContextTest {
    private val recipe = TrainingPlanRecipe(
        id = "apre-mann",
        weeks = emptyList(),
        progression = ProgressionRule.ApreXrm(),
    )

    private val apreExercise = Exercise(
        id = "lift",
        name = "Sentadilla",
        catalogConfigurationId = "squat_low_bar",
        slotRole = SlotRole.T1_MAIN,
        sets = listOf(
            ExerciseSet(id = "s1", targetReps = 12, weight = 75.0),
            ExerciseSet(id = "s2", targetReps = 10, weight = 112.5),
            ExerciseSet(
                id = "s3", targetReps = 10, weight = 150.0, targetPercentageRM = 81.52, targetRPE = 8.0,
                purpose = SetPurpose.AMRAP, amrapMinimumReps = 10,
            ),
            ExerciseSet(
                id = "s4", targetReps = 10, weight = 150.0, targetPercentageRM = 81.52,
                purpose = SetPurpose.AMRAP, amrapMinimumReps = 10,
            ),
        ),
        apreBlock = ApreBlockReference(xrmReps = 10, setIds = listOf("s1", "s2", "s3", "s4")),
    )

    @Test
    fun uses_the_just_logged_set_after_the_cursor_has_advanced_and_keeps_real_hit_fields() {
        val state = WorkoutUiState(
            currentExerciseIdx = 1,
            currentSetIdx = 0,
            setJustLoggedKey = "lift_2",
            completedSets = mapOf(
                "lift_2" to CompletedSet(
                    id = "s3",
                    weight = 150.0,
                    reps = 12,
                    rir = 1,
                    isFailure = true,
                    partialReps = 2,
                    amrapPerformed = true,
                    purpose = SetPurpose.AMRAP,
                    amrapMinimumReps = 10,
                    setIndex = 2,
                ),
            ),
        )
        val nextExercise = Exercise(
            id = "next",
            name = "Press banca",
            sets = listOf(ExerciseSet(id = "next-s1", targetReps = 8)),
        )
        val progression = relatorProgressionContextFor(
            state = state,
            visibleExercises = listOf(apreExercise, nextExercise),
            currentExercise = nextExercise,
            currentSet = nextExercise.sets.first(),
            base = RelatorProgressionContext(recipe = recipe, sourceProtocolId = "apre-mann"),
        )

        val hit = progression.hit
        assertNotNull(hit)
        assertEquals(LiftSlot.SQUAT, hit!!.liftSlot)
        assertEquals(150.0, hit.weightKg!!, 1e-9)
        assertEquals(12, hit.actualReps)
        assertEquals(10, hit.prescribedReps)
        assertEquals(81.52, hit.percent!!, 1e-9)
        assertEquals(1.0, hit.weightRatio!!, 1e-9)
        assertNull(hit.meanRpe)
        assertEquals(1, hit.declaredRir)
        assertTrue(hit.reachedFailure)
        assertEquals(2, hit.partialReps)
        assertFalse(hit.isLastAmrapOfExercise)
        assertEquals(SlotRole.T1_MAIN, progression.slotRole)
        assertEquals(42, progression.plannedVolumeReps)
        assertEquals(10, progression.plannedMinimumReps)
        assertTrue(progression.isApreBlock)
        assertNull(progression.tierCheck)
        assertNull(progression.frozenCyclesBefore)
    }

    @Test
    fun ordinary_amrap_and_explicit_opt_out_do_not_become_apre() {
        val ordinaryExercise = apreExercise.copy(apreBlock = null)
        val ordinaryState = WorkoutUiState(
            setJustLoggedKey = "lift_2",
            completedSets = mapOf(
                "lift_2" to CompletedSet(
                    id = "s3", weight = 150.0, reps = 12, amrapPerformed = true,
                    purpose = SetPurpose.AMRAP, amrapMinimumReps = 10, setIndex = 2,
                ),
            ),
        )
        val ordinary = relatorProgressionContextFor(
            ordinaryState, listOf(ordinaryExercise), ordinaryExercise, ordinaryExercise.sets[2],
            RelatorProgressionContext(recipe = recipe),
        )
        assertNotNull(ordinary.hit)
        assertFalse(ordinary.isApreBlock)

        val legacyApre = relatorProgressionContextFor(
            ordinaryState, listOf(ordinaryExercise), ordinaryExercise, ordinaryExercise.sets[2],
            RelatorProgressionContext(recipe = recipe, sourceProtocolId = "apre-mann"),
        )
        assertTrue(legacyApre.isApreBlock)

        val invalidMarker = ordinaryExercise.copy(
            apreBlock = ApreBlockReference(xrmReps = 10, setIds = listOf("s3", "s4")),
        )
        val invalidExplicitMarker = relatorProgressionContextFor(
            ordinaryState, listOf(invalidMarker), invalidMarker, invalidMarker.sets[2],
            RelatorProgressionContext(recipe = recipe, sourceProtocolId = "apre-mann"),
        )
        assertFalse(invalidExplicitMarker.isApreBlock)

        val optedOut = ordinaryState.copy(
            completedSets = mapOf(
                "lift_2" to CompletedSet(
                    id = "s3", weight = 150.0, reps = 12, amrapPerformed = false,
                    purpose = SetPurpose.NONE, amrapMinimumReps = 10, setIndex = 2,
                ),
            ),
        )
        val noHit = relatorProgressionContextFor(
            optedOut, listOf(apreExercise), apreExercise, apreExercise.sets[2],
            RelatorProgressionContext(recipe = recipe, sourceProtocolId = "apre-mann"),
        )
        assertNull(noHit.hit)
        assertFalse(noHit.isApreBlock)
    }
    @Test
    fun s4_uses_only_a_current_material_capture_for_the_same_logged_set() {
        val stateBase = WorkoutUiState(programId = "program")
        val done = CompletedSet(
            id = "s4-event", weight = 155.0, originalLoad = 155.0, reps = 17,
            purpose = SetPurpose.AMRAP, amrapPerformed = true, amrapMinimumReps = 10,
            setIndex = 3, loadMode = LoadModeV2.LOAD,
        )
        val context = calibrationContextForSet(stateBase, apreExercise, 3, null, done)
        val capture = com.example.kpkn.domain.training.captureApreXrm(
            apreExercise, 3, done, sourceProtocolId = "apre-mann", hasApreProgression = true,
            incrementKg = 5.0, capturedAtMs = 123L, sourceProgramId = "program", contextOverride = context,
        )!!
        val state = stateBase.copy(
            setJustLoggedKey = "lift_3",
            completedSets = mapOf("lift_3" to done.copy(apreXrm = capture)),
        )
        val progression = relatorProgressionContextFor(
            state, listOf(apreExercise), apreExercise, apreExercise.sets[0],
            RelatorProgressionContext(recipe = recipe, sourceProtocolId = "apre-mann"),
        )
        val effect = AmrapProgramPreview.preview(recipe, AutoregulationMode.PROPOSE, null, progression.hit!!)

        assertEquals(5.0, progression.hit!!.apreXrmCapture!!.incrementKg, 0.0)
        assertEquals(160.0, effect.nextWeekXrmKg!!, 1e-9)

        val staleState = state.copy(completedSets = mapOf("lift_3" to done.copy(reps = 18, apreXrm = capture)))
        val stale = relatorProgressionContextFor(
            staleState, listOf(apreExercise), apreExercise, apreExercise.sets[0],
            RelatorProgressionContext(recipe = recipe, sourceProtocolId = "apre-mann"),
        )
        assertNull(stale.hit!!.apreXrmCapture)
        val withoutCapture = AmrapProgramPreview.preview(
            recipe, AutoregulationMode.PROPOSE, null, stale.hit!!.copy(apreXrmCapture = null),
        )
        val stalePreview = AmrapProgramPreview.preview(recipe, AutoregulationMode.PROPOSE, null, stale.hit!!)
        assertEquals(withoutCapture.nextWeekXrmKg, stalePreview.nextWeekXrmKg)
    }

    @Test
    fun s3_uses_its_current_calibration_result_and_labels_proposal_vs_applied_load() {
        val materialExercise = apreExercise.copy(
            sets = apreExercise.sets.mapIndexed { index, set ->
                if (index in 2..3) set.copy(machineBrand = "Máquina", loadModeV2 = LoadModeV2.LOAD) else set
            },
        )
        val session = Session(id = "session", name = "APRE", exercises = listOf(materialExercise))
        val source = CompletedSet(
            id = "s3-event", weight = 155.0, originalLoad = 155.0, reps = 17,
            purpose = SetPurpose.AMRAP, amrapPerformed = true, amrapMinimumReps = 10,
            setIndex = 2, loadMode = LoadModeV2.LOAD,
        )
        val stateBase = WorkoutUiState(
            programId = "program", session = session, startTimeMs = 123L,
            completedSets = mapOf("lift_2" to source),
        )
        val record = buildRecordedApreCalibration(stateBase, materialExercise, 2, source, 456L)!!
        val state = stateBase.copy(
            setJustLoggedKey = "lift_2",
            calibrationDecisions = listOf(record),
        )
        val progression = relatorProgressionContextFor(
            state, listOf(materialExercise), materialExercise, materialExercise.sets[3],
            RelatorProgressionContext(recipe = recipe, sourceProtocolId = "apre-mann"),
        )
        val hit = progression.hit!!
        val proposal = AmrapProgramPreview.preview(recipe, AutoregulationMode.AUTO, null, hit)

        assertEquals(5.0, record.loadIncrementKg, 0.0)
        assertEquals(160.0, record.apre!!.set4LoadKg, 1e-9)
        assertEquals(record.apre!!.set4LoadKg, hit.apreSet4LoadKg!!, 0.0)
        assertFalse(hit.apreSet4Applied)
        assertEquals(record.apre!!.set4LoadKg, proposal.nextSetLoadKg!!, 0.0)
        assertEquals(162.5, AmrapProgramPreview.preview(
            recipe, AutoregulationMode.AUTO, null, hit.copy(apreSet4LoadKg = null),
        ).nextSetLoadKg!!, 1e-9)
        assertTrue(proposal.observerNote!!.text, proposal.observerNote!!.text.contains("prevista"))

        val appliedExercise = materialExercise.copy(sets = materialExercise.sets.mapIndexed { index, set ->
            if (index == 3) set.copy(weight = record.apre!!.set4LoadKg) else set
        })
        val appliedRecord = record.copy(
            status = CalibrationStatus.APPLIED,
            appliedSetIds = setOf(calibrationSetId(appliedExercise.sets[3], null)),
        )
        val appliedState = state.copy(
            session = session.copy(exercises = listOf(appliedExercise)),
            calibrationDecisions = listOf(appliedRecord),
        )
        val appliedHit = relatorProgressionContextFor(
            appliedState, listOf(appliedExercise), appliedExercise, appliedExercise.sets[3],
            RelatorProgressionContext(recipe = recipe, sourceProtocolId = "apre-mann"),
        ).hit!!
        val appliedNote = AmrapProgramPreview.preview(recipe, AutoregulationMode.AUTO, null, appliedHit).observerNote!!.text
        assertTrue(appliedNote, appliedNote.contains("ajustada a"))
    }

}
