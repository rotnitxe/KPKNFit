package com.example.kpkn.screens.programdetail

import com.example.kpkn.data.models.PowerliftingProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/** La fila de máximos de Propuestas: «Añadir» cuando el programa usa TM y no hay ninguno; nada cuando no lo usa. */
class ProgramTrainingMaxRowTest {

    @Test fun overheadTmIsVisibleEvenWhenItIsTheOnlyStoredLift() {
        assertEquals(TrainingMaxRow.Value("TM: press militar 72 kg"),
            trainingMaxRowOf(usesTm = true, profile = PowerliftingProfile(overheadTM = 72.0)))
    }

    @Test
    fun `a program that uses TM with no max set offers adding them`() {
        assertEquals(TrainingMaxRow.Unset, trainingMaxRowOf(usesTm = true, profile = null))
        assertEquals(TrainingMaxRow.Unset, trainingMaxRowOf(usesTm = true, profile = PowerliftingProfile()))
    }

    @Test
    fun `a program that uses TM shows the maxes that exist`() {
        assertEquals(
            TrainingMaxRow.Value("TM: sentadilla 108 kg"),
            trainingMaxRowOf(usesTm = true, profile = PowerliftingProfile(squatTM = 108.0)),
        )
    }

    @Test
    fun `a program that does not use TM shows no row, even with maxes saved`() {
        assertEquals(TrainingMaxRow.None, trainingMaxRowOf(usesTm = false, profile = null))
        assertEquals(TrainingMaxRow.None, trainingMaxRowOf(usesTm = false, profile = PowerliftingProfile(squatTM = 108.0)))
    }
}
