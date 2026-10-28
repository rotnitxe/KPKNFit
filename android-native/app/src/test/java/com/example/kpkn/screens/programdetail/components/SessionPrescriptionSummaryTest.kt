package com.example.kpkn.screens.programdetail.components

import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
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
        modo: TrainingMode = TrainingMode.REPS,
        customUnit: String? = null,
    ) = Exercise(
        id = "ej",
        name = "Ejercicio",
        trainingMode = modo,
        customUnit = customUnit,
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
    fun unaCargaEstimadaTerminaConElRotuloCargaEstimada() {
        val estimada = ejercicio(ExerciseSet(id = "s1", targetReps = 8), descanso = 195).copy(
            loadEstimate = com.example.kpkn.domain.training.VariantLoadEstimate("base", 200.0, 0.85, 170.0),
        )
        assertEquals("1 × 8 · 3:15 · carga estimada", formatExercisePrescription(estimada))
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
    fun laUnidadActivaEvitaDosisResidualPeroConservaCargaExplicita() {
        val timeSets = (1..3).map {
            ExerciseSet(
                id = "time-$it",
                targetRepsRange = RepRange(6, 10),
                targetDuration = 45,
                plannedTargetV2 = 400.0,
                weight = 84.0,
                targetRIR = 2,
                targetRPE = 9.0,
                targetPercentageRM = 80.0,
                intensityMode = IntensityMode.RPE,
                loadBasis = LoadBasis.PERCENT_1RM,
                unitModeV2 = UnitModeV2.TIME,
            )
        }
        val distanceSets = timeSets.map { it.copy(id = "distance-${it.id}", unitModeV2 = UnitModeV2.DISTANCE, intensityMode = IntensityMode.RIR) }
        val ambiguousTimeSets = timeSets.map { it.copy(id = "ambiguous-${it.id}", intensityMode = null) }
        val customSets = timeSets.map {
            it.copy(id = "custom-${it.id}", plannedTargetV2 = 12.0, weight = 5.0, unitModeV2 = UnitModeV2.CUSTOM, intensityMode = IntensityMode.FAILURE)
        }
        val repsSets = timeSets.map {
            it.copy(
                id = "reps-${it.id}",
                plannedTargetV2 = null,
                weight = null,
                targetPercentageRM = null,
                loadBasis = null,
                intensityMode = IntensityMode.RIR,
                unitModeV2 = UnitModeV2.REPS,
            )
        }

        assertEquals(
            "3 × 45${nbsp}s · 84${nbsp}kg · RPE 9 · 2:00",
            formatExercisePrescription(ejercicio(*timeSets.toTypedArray(), descanso = 120, modo = TrainingMode.TIME)),
        )
        assertEquals(
            "3 × 45${nbsp}s · 84${nbsp}kg · 2:00",
            formatExercisePrescription(ejercicio(*ambiguousTimeSets.toTypedArray(), descanso = 120, modo = TrainingMode.TIME)),
        )
        assertEquals(
            "3 × 400${nbsp}m · 84${nbsp}kg · RIR 2 · 2:00",
            formatExercisePrescription(ejercicio(*distanceSets.toTypedArray(), descanso = 120, modo = TrainingMode.DISTANCE)),
        )
        assertEquals(
            "3 × 6–10 · RIR 2 · 2:00",
            formatExercisePrescription(ejercicio(*repsSets.toTypedArray(), descanso = 120, modo = TrainingMode.REPS)),
        )
        assertEquals(
            "3 × 12${nbsp}pasos · 5${nbsp}kg · hasta el fallo · 2:00",
            formatExercisePrescription(ejercicio(*customSets.toTypedArray(), descanso = 120, modo = TrainingMode.CUSTOM, customUnit = "pasos")),
        )

        val capturedRm = PlanLoadReference(
            kind = PlanLoadReferenceKind.EXERCISE_1RM,
            configurationId = "press",
            state = PlanLoadReferenceState.CAPTURED,
            capturedLoadKg = 120.0,
        )
        val distanceWithoutExplicitWeight = distanceSets.map { it.copy(weight = null) }
        val customWithoutExplicitWeight = customSets.map { it.copy(weight = null) }
        val timeWithoutExplicitWeight = timeSets.map { it.copy(weight = null) }
        assertEquals(
            "3 × 45${nbsp}s · RPE 9 · 2:00",
            formatExercisePrescription(ejercicio(*timeWithoutExplicitWeight.toTypedArray(), descanso = 120, referencia = capturedRm, modo = TrainingMode.TIME)),
        )
        assertEquals(
            "3 × 400${nbsp}m · RIR 2 · 2:00",
            formatExercisePrescription(ejercicio(*distanceWithoutExplicitWeight.toTypedArray(), descanso = 120, referencia = capturedRm, modo = TrainingMode.DISTANCE)),
        )
        assertEquals(
            "3 × 12${nbsp}pasos · hasta el fallo · 2:00",
            formatExercisePrescription(ejercicio(*customWithoutExplicitWeight.toTypedArray(), descanso = 120, referencia = capturedRm, modo = TrainingMode.CUSTOM, customUnit = "pasos")),
        )
    }

    @Test
    fun modosRpeYRmConservanLaIntensidadDeFuerza() {
        val soloRpeSets = (1..3).map {
            ExerciseSet(
                id = "rpe-$it",
                targetRepsRange = RepRange(6, 10),
                targetRPE = 9.0,
                targetRIR = 2,
                targetPercentageRM = 80.0,
                intensityMode = IntensityMode.RPE,
                weight = 84.0,
                unitModeV2 = UnitModeV2.REPS,
            )
        }
        val rmSets = (1..3).map {
            ExerciseSet(
                id = "rm-$it",
                targetRepsRange = RepRange(6, 10),
                targetPercentageRM = 80.0,
                loadBasis = LoadBasis.PERCENT_1RM,
                weight = 84.0,
                targetRIR = 2,
                unitModeV2 = UnitModeV2.REPS,
            )
        }

        assertEquals(
            "3 series · RPE 9 · 2:00",
            formatExercisePrescription(ejercicio(*soloRpeSets.toTypedArray(), descanso = 120, modo = TrainingMode.SOLO_RPE)),
        )
        val rmTmSets = rmSets.map { it.copy(loadBasis = LoadBasis.PERCENT_TM) }
        assertEquals(
            "3 × 6–10 · 84${nbsp}kg · 80${nbsp}% TM · RIR 2 · 2:00",
            formatExercisePrescription(ejercicio(*rmTmSets.toTypedArray(), descanso = 120, modo = TrainingMode.RM)),
        )
        assertEquals(
            "3 × 6–10 · 84${nbsp}kg · 80${nbsp}% 1RM · RIR 2 · 2:00",
            formatExercisePrescription(ejercicio(*rmSets.toTypedArray(), descanso = 120, modo = TrainingMode.RM)),
        )
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
