package com.example.kpkn.screens.programdetail.components

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** La línea de dosis de la sesión del programa (SE12): la del editor, con los kilos en coma y sin palabras internas. */
class SessionPrescriptionSummaryTest {

    private val nbsp = " "

    private fun ejercicio(
        vararg sets: ExerciseSet,
        descanso: Int? = null,
        referencia: PlanLoadReference? = null,
        convencion: LoadQuantityConvention = LoadQuantityConvention.UNSPECIFIED,
    ) = Exercise(
        id = "ej",
        name = "Ejercicio",
        trainingMode = TrainingMode.REPS,
        sets = sets.toList(),
        restTime = descanso,
        loadReference = referencia,
        loadQuantityConvention = convencion,
    )

    @Test
    fun descansoDeMasDeUnMinutoVaEnMinSSSinLaPalabraDescanso() {
        val ej = ejercicio(ExerciseSet(id = "s1", targetReps = 8), descanso = 195)
        assertEquals("1 × 8 · 3:15", formatExercisePrescription(ej))
    }

    @Test
    fun descansoDeMenosDeUnMinutoVaConSuUnidadConEspacioInseparable() {
        val ej = ejercicio(ExerciseSet(id = "s1", targetReps = 8), descanso = 45)
        assertEquals("1 × 8 · 45${nbsp}s", formatExercisePrescription(ej))
    }

    @Test
    fun tiempoPorSerieVaConSuUnidadYSuDescanso() {
        val sets = (1..3).map { ExerciseSet(id = "s$it", targetDuration = 40) }
        assertEquals("3 × 40${nbsp}s · 45${nbsp}s", formatExercisePrescription(ejercicio(*sets.toTypedArray(), descanso = 45)))
    }

    @Test
    fun sinRepeticionesNiDuracionNoQuedaLaCruzColgando() {
        val sets = (1..3).map { ExerciseSet(id = "s$it") }
        assertEquals("3 series", formatExercisePrescription(ejercicio(*sets.toTypedArray())))
    }

    @Test
    fun elPorcentajeVaConSuBaseYElRirEnLaMismaLinea() {
        val sets = (1..4).map {
            ExerciseSet(
                id = "s$it",
                targetRepsRange = RepRange(4, 6),
                targetPercentageRM = 80.0,
                loadBasis = LoadBasis.PERCENT_1RM,
                targetRIR = 2,
            )
        }
        val linea = formatExercisePrescription(ejercicio(*sets.toTypedArray(), descanso = 195))

        assertEquals("4 × 4–6 · 80$nbsp% 1RM · RIR 2 · 3:15", linea)
    }

    @Test
    fun losKilosVanConComaYEspacioInseparableSinLaPalabraTotal() {
        val sets = (1..3).map { ExerciseSet(id = "s$it", targetReps = 10, weight = 70.8) }
        val linea = formatExercisePrescription(
            ejercicio(*sets.toTypedArray(), descanso = 120, convencion = LoadQuantityConvention.TOTAL_EXTERNAL),
        )

        assertEquals("3 × 10 · 70,8${nbsp}kg · 2:00", linea)
    }

    @Test
    fun unaCargaPendienteNoSeEscribeNiSuReferencia() {
        val sets = (1..3).map { ExerciseSet(id = "s$it", targetRepsRange = RepRange(6, 10), targetRIR = 2) }
        val referencia = PlanLoadReference(
            kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
            configurationId = "cfg",
            repMin = 6,
            repMax = 10,
            state = PlanLoadReferenceState.PENDING,
        )
        val linea = formatExercisePrescription(ejercicio(*sets.toTypedArray(), descanso = 120, referencia = referencia))

        assertEquals("3 × 6–10 · RIR 2 · 2:00", linea)
        assertFalse(linea!!.contains("pendiente"))
        assertFalse(linea.contains("referencia"))
    }
}
