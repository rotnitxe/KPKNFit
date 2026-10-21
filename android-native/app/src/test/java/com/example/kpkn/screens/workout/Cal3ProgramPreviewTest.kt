package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.training.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class Cal3ProgramPreviewTest {
    companion object { @BeforeClass @JvmStatic fun metadata() { CatalogCompositionTestSupport.install() } }
    private val f = cal3Fixture()
    private val ex = f.exercise.copy(catalogConfigurationId = "barbell_squat", referenceTrainingMax = 100.0,
        sets = f.exercise.sets.map { it.copy(loadBasis = LoadBasis.PERCENT_TM, targetPercentageRM = 90.0) })
    private val recipe = TrainingPlanRecipe("cal3-rule", emptyList(), progression = ProgressionRule.RepTargetDrivenTm())
    private fun program(mode: AutoregulationMode) = Program("cal3-program", "Programa", sourceRecipe = recipe,
        powerliftingProfile = PowerliftingProfile(squatTM = 100.0, benchTM = 80.0), autoregulationMode = mode)
    private val completed = f.completed.copy(reps = 11, weight = 100.0, purpose = SetPurpose.AMRAP, amrapMinimumReps = 6)
    private fun preview(p: Program, c: CompletedSet = completed) = liveAmrapProgramPreview(p, ex, ex.sets[0], c, "week")

    @Test fun offDoesNotProposeOrMutateTm() {
        val p = program(AutoregulationMode.OFF)
        val result = preview(p)
        assertTrue(result.proposals.isEmpty())
        assertTrue(result.message.contains("OFF"))
        assertEquals(100.0, p.powerliftingProfile!!.squatTM!!, 0.0)
    }
    @Test fun proposeShowsTheSameChangeAsTheProgramEngineAndRemainsPendingAtWeeklyClose() {
        val p = program(AutoregulationMode.PROPOSE)
        val result = preview(p)
        assertEquals(102.5, result.afterTm!!, .001)
        assertEquals(100.0, p.powerliftingProfile!!.squatTM!!, 0.0)
        val channel = ProgramAutoregulationEngine.apply(p, null, result.proposals, recipe, emptySet(), CatalogCompositionTestSupport.metadata)
        assertFalse(channel.applied)
        assertEquals(result.proposals, channel.program.runState!!.pendingAction!!.proposals)
        assertEquals(100.0, channel.program.powerliftingProfile!!.squatTM!!, 0.0)
    }
    @Test fun autoUsesTheExistingChannelAtWeeklyCloseWithoutTouchingOtherLiftTms() {
        val p = program(AutoregulationMode.AUTO)
        val result = preview(p)
        assertTrue(result.message.contains("al cerrar la semana"))
        val channel = ProgramAutoregulationEngine.apply(p, null, result.proposals, recipe, emptySet(), CatalogCompositionTestSupport.metadata)
        assertTrue(channel.applied)
        assertEquals(result.afterTm!!, channel.program.powerliftingProfile!!.squatTM!!, .001)
        assertEquals(80.0, channel.program.powerliftingProfile!!.benchTM!!, 0.0)
        assertEquals(100.0, p.powerliftingProfile!!.squatTM!!, 0.0)
    }
    @Test fun noRuleMeansHistoryOnlyAndShortSetsUseTheActualMinimum() {
        val p = program(AutoregulationMode.AUTO)
        assertFalse(preview(p.copy(sourceRecipe = recipe.copy(progression = ProgressionRule.None))).hasRule)
        assertEquals(98.0, preview(p, completed.copy(reps = 4)).afterTm!!, .001)
        assertTrue(preview(p, completed.copy(reps = 5)).proposals.isEmpty())
        // Characterize the existing rule's zero target; CAL3 does not rewrite program rules.
        assertEquals(105.5, preview(p, completed.copy(amrapMinimumReps = null)).afterTm!!, .001)
    }
    @Test fun previewIsDeterministicAndUsesRecipeBindingsForTheSameExerciseOnly() {
        val p = program(AutoregulationMode.PROPOSE)
        assertEquals(preview(p), preview(p))
        val result = liveAmrapProgramPreview(p, ex.copy(catalogConfigurationId = "dumbbell_row"), ex.sets[0], completed, "week")
        assertTrue(result.proposals.isEmpty())
    }
}
