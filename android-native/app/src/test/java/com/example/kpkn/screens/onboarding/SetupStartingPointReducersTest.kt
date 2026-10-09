package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.onboarding.ArmStyle
import com.example.kpkn.domain.onboarding.AvoidGroup
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.CardioBase
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.SetupStepId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reductores puros del punto de partida de Entreno: marcas con repeticiones, variantes, evitar, cardio, estilo y repeticiones. */
class SetupStartingPointReducersTest {

    @Test
    fun aPressOnlyKeepsTheProfileAndItsOwnOneRepetitionMax() {
        val draft = SetupWizardDraft().withLiftMark(LiftMark.OVERHEAD_PRESS, 60.0)
        assertNotNull(draft.powerliftingProfile)
        val profile = draft.powerliftingProfile!!
        assertEquals(60.0, profile.overhead1RM!!, 1e-9)
        assertNull(profile.squat1RM)
        assertEquals(60.0, draft.liftMarks.getValue(LiftMark.OVERHEAD_PRESS), 1e-9)
        assertTrue(draft.knowsTrainingMarks)
        // Quitarla deja el perfil vacío: no quedan marcas de perfil.
        assertNull(draft.withLiftMark(LiftMark.OVERHEAD_PRESS, null).powerliftingProfile)
    }

    @Test
    fun aPersonalRecordGoesToTheEstimateAndLeavesTheOneRepetitionMaxEmpty() {
        val draft = SetupWizardDraft().withLiftMark(LiftMark.SQUAT, 100.0).withLiftMarkReps(LiftMark.SQUAT, 5)
        assertNotNull(draft.powerliftingProfile)
        val profile = draft.powerliftingProfile!!
        assertNull("una serie de trabajo nunca se guarda como 1RM", profile.squat1RM)
        assertEquals(calculateHybrid1RM(100.0, 5), profile.squatE1RM!!, 1e-9)
        // El dato que la persona escribió sigue siendo la serie.
        assertEquals(100.0, draft.liftMarks.getValue(LiftMark.SQUAT), 1e-9)
        assertEquals(5, draft.liftMarkReps[LiftMark.SQUAT])
        // Con una sola repetición vuelve a ser un 1RM y la estimación desaparece.
        val single = draft.withLiftMarkReps(LiftMark.SQUAT, 1)
        assertEquals(100.0, single.powerliftingProfile!!.squat1RM!!, 1e-9)
        assertNull(single.powerliftingProfile!!.squatE1RM)
        assertNull(single.liftMarkReps[LiftMark.SQUAT])
    }

    @Test
    fun theRepetitionsAreClampedToOneAndTen() {
        val draft = SetupWizardDraft().withLiftMark(LiftMark.BENCH, 80.0).withLiftMarkReps(LiftMark.BENCH, 15)
        assertEquals(10, draft.liftMarkReps[LiftMark.BENCH])
        assertEquals(calculateHybrid1RM(80.0, 10), draft.powerliftingProfile!!.benchE1RM!!, 1e-9)
        // Un valor por debajo de 1 se recorta a 1, que es un 1RM y no se guarda.
        assertNull(draft.withLiftMarkReps(LiftMark.BENCH, 0).liftMarkReps[LiftMark.BENCH])
        assertNull(draft.withLiftMarkReps(LiftMark.BENCH, null).liftMarkReps[LiftMark.BENCH])
    }

    @Test
    fun withMaxRepsDerivesTheLevelTheEngineStillReads() {
        val draft = SetupWizardDraft()
        assertEquals(CapabilityLevel.NONE, draft.withMaxReps(CapabilitySkill.PULL_UP, 0).capabilities[CapabilitySkill.PULL_UP])
        assertEquals(CapabilityLevel.SOME, draft.withMaxReps(CapabilitySkill.PULL_UP, 4).capabilities[CapabilitySkill.PULL_UP])
        assertEquals(CapabilityLevel.MANY, draft.withMaxReps(CapabilitySkill.PULL_UP, 5).capabilities[CapabilitySkill.PULL_UP])
        // Las flexiones tienen su propio umbral: algunas hasta 14, varias desde 15.
        assertEquals(CapabilityLevel.SOME, draft.withMaxReps(CapabilitySkill.PUSH_UP, 14).capabilities[CapabilitySkill.PUSH_UP])
        assertEquals(CapabilityLevel.MANY, draft.withMaxReps(CapabilitySkill.PUSH_UP, 15).capabilities[CapabilitySkill.PUSH_UP])
        assertEquals(8, draft.withMaxReps(CapabilitySkill.PULL_UP, 8).maxReps[CapabilitySkill.PULL_UP])
    }

    @Test
    fun theRepetitionsRangeIsZeroToThirtyAndRetiringThemRetiresTheLevel() {
        val draft = SetupWizardDraft()
        assertEquals(30, draft.withMaxReps(CapabilitySkill.DIP, 99).maxReps[CapabilitySkill.DIP])
        assertEquals(0, draft.withMaxReps(CapabilitySkill.DIP, -4).maxReps[CapabilitySkill.DIP])
        val cleared = draft.withMaxReps(CapabilitySkill.PULL_UP, 8).withMaxReps(CapabilitySkill.PULL_UP, null)
        assertFalse(CapabilitySkill.PULL_UP in cleared.maxReps)
        assertFalse(CapabilitySkill.PULL_UP in cleared.capabilities)
        // La sentadilla a una pierna no tiene regla de repeticiones: sigue con su nivel.
        assertTrue(draft.withMaxReps(CapabilitySkill.PISTOL_SQUAT, 3).maxReps.isEmpty())
    }

    @Test
    fun aVariantIsStoredOnlyForTheLiftItBelongsTo() {
        val draft = SetupWizardDraft()
        val lowBar = "low_bar_back_squat__barbell"
        assertEquals(lowBar, draft.withLiftVariant(LiftMark.SQUAT, lowBar).liftVariants[LiftMark.SQUAT])
        // Una variante de peso muerto no es de la sentadilla: no se guarda.
        assertTrue(draft.withLiftVariant(LiftMark.SQUAT, "conventional_deadlift__bilateral__barbell").liftVariants.isEmpty())
        // Retirar la variante vuelve a la de siempre.
        assertTrue(draft.withLiftVariant(LiftMark.SQUAT, lowBar).withLiftVariant(LiftMark.SQUAT, null).liftVariants.isEmpty())
    }

    @Test
    fun theAvoidedGroupsToggleAndNoneIsTheEmptySet() {
        val draft = SetupWizardDraft().withAvoidedToggled(AvoidGroup.DIPS).withAvoidedToggled(AvoidGroup.HIP_THRUST)
        assertEquals(setOf(AvoidGroup.DIPS, AvoidGroup.HIP_THRUST), draft.avoidedExercises)
        assertEquals(setOf(AvoidGroup.HIP_THRUST), draft.withAvoidedToggled(AvoidGroup.DIPS).avoidedExercises)
        assertTrue(draft.withAvoided(emptySet()).avoidedExercises.isEmpty())
    }

    @Test
    fun theCardioBaseAndTheArmStyleAreSingleAnswersThatCanBeRetired() {
        assertEquals(CardioBase.HIGH, SetupWizardDraft().withCardioBase(CardioBase.HIGH).cardioBase)
        assertNull(SetupWizardDraft().withCardioBase(CardioBase.HIGH).withCardioBase(null).cardioBase)
        assertEquals(ArmStyle.TOP_ROLL, SetupWizardDraft().withArmStyle(ArmStyle.TOP_ROLL).armStyle)
        assertNull(SetupWizardDraft().withArmStyle(ArmStyle.HOOK).withArmStyle(null).armStyle)
    }

    @Test
    fun theGenericChoiceWritesTheNewStepsThroughTheSameReducers() {
        // El control genérico (`setStepChoice`) escribe los pasos de valor único y de varios sin inventar nada.
        assertEquals(CardioBase.LOW, SetupWizardDraft().withStepChoice(SetupStepId.CARDIO_BASE, "LOW").cardioBase)
        assertNull(SetupWizardDraft().withStepChoice(SetupStepId.ARM_STYLE, "NO_EXISTE").armStyle)
        assertEquals(
            setOf(AvoidGroup.DEADLIFT, AvoidGroup.PULL_UPS),
            SetupWizardDraft().withStepChoices(SetupStepId.AVOIDED_EXERCISES, setOf("DEADLIFT", "PULL_UPS")).avoidedExercises,
        )
        assertTrue(SetupWizardDraft().withStepChoices(SetupStepId.AVOIDED_EXERCISES, emptySet()).avoidedExercises.isEmpty())
    }

    @Test
    fun theDerivedMarkProfileIsTheSameForAPlainOneRepetitionMax() {
        // Un 1RM sin repeticiones escribe el mismo campo que antes del punto de partida.
        val profile = SetupWizardDraft().withLiftMark(LiftMark.DEADLIFT, 180.0).powerliftingProfile!!
        assertEquals(180.0, profile.deadlift1RM!!, 1e-9)
        assertNull(profile.deadliftE1RM)
        assertNull(profile.squat1RM)
    }
}
