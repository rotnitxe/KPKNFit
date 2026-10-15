package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.protocols.LoadBasis
import org.junit.Assert.assertEquals
import org.junit.Test

/** ER2a: la fila cerrada de una serie enseña la carga guardada; la calculada solo aparece si la serie no tiene ninguna. */
class ExerciseSetRowLoadTest {

    @Test
    fun laFilaRmEnseñaLaCargaGuardadaYNoLaCalculada() {
        val set = ExerciseSet(id = "s1", weight = 95.0, targetPercentageRM = 80.0, loadBasis = LoadBasis.PERCENT_1RM)
        val summary = setRowSummary(
            set = set,
            side = null,
            mode = TrainingMode.RM,
            customUnit = null,
            predictedWeight = 96.0,
            estimatedMetric = 7.0,
        )
        assertEquals("95 kg × 7", summary.dose)
    }

    @Test
    fun enRepsLaFilaUsaElPesoGuardadoAunqueLaCalculadaSeaOtra() {
        val set = ExerciseSet(id = "r1", weight = 80.0, targetReps = 8, intensityMode = IntensityMode.RIR, targetRIR = 2)
        val summary = setRowSummary(
            set = set,
            side = null,
            mode = TrainingMode.REPS,
            customUnit = null,
            predictedWeight = 75.0,
            estimatedMetric = null,
        )
        assertEquals("80 kg × 8", summary.dose)
    }

    @Test
    fun sinCargaGuardadaLaFilaMuestraLaCalculada() {
        val set = ExerciseSet(id = "r2", targetReps = 8)
        val summary = setRowSummary(
            set = set,
            side = null,
            mode = TrainingMode.REPS,
            customUnit = null,
            predictedWeight = 75.0,
            estimatedMetric = null,
        )
        assertEquals("75 kg × 8", summary.dose)
    }
}
