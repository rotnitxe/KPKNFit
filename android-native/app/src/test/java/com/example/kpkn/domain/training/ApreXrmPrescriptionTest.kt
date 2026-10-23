package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import org.junit.Assert.*
import org.junit.Test

class ApreXrmPrescriptionTest {
    private val base = Exercise("press", "Press", canonicalExerciseId = "bench__bar", catalogConfigurationId = "bench__bar",
        recipeDayId = "d1", recipeSlotId = "t1", trainingMode = TrainingMode.RM,
        reference1RM = 200.0, referenceTrainingMax = 180.0,
        sets = Apre.block(200.0, ApreVariant.APRE_10).map { row ->
            ExerciseSet("s${row.ordinal}", weight = row.loadKg, targetReps = row.targetReps,
                targetPercentageRM = row.percentOfXrm, loadBasis = LoadBasis.PERCENT_TM,
                purpose = row.purpose, amrapMinimumReps = row.minimumReps, loadModeV2 = LoadModeV2.LOAD)
        }, apreBlock = ApreBlockReference(10, listOf("s1", "s2", "s3", "s4")))
    private val done = CompletedSet("actual-s4", weight = 155.0, originalLoad = 155.0, reps = 17,
        purpose = SetPurpose.AMRAP, setIndex = 3, loadMode = LoadModeV2.LOAD)
    private fun capture(exercise: Exercise = base, completed: CompletedSet = done, step: Double = 2.5) =
        captureApreXrm(exercise, 3, completed, incrementKg = step, capturedAtMs = 100L, sourceProgramId = "p")!!

    @Test fun aplicaElXrmDeTrabajoSinMoverNiRotularCapacidades() {
        val future = base.copy(id = "next-press")
        val result = applyApreXrmToExercise(future, capture())
        assertEquals(listOf(82.5, 122.5, 162.5, 162.5), result.sets.map { it.weight })
        assertEquals(200.0, result.reference1RM!!, 0.0)
        assertEquals(180.0, result.referenceTrainingMax!!, 0.0)
        assertEquals(TrainingMode.REPS, result.trainingMode)
        assertTrue(result.sets.all { it.targetPercentageRM == null && it.loadBasis == null })
        assertEquals(listOf(75.0, 112.5, 150.0, 150.0), base.sets.map { it.weight })
        assertEquals(base.sets.map { it.purpose }, result.sets.map { it.purpose })
        assertEquals(result, applyApreXrmToExercise(result, capture()))
    }

    @Test fun otraConfiguracionEquipoOSlotNoSeModifican() {
        val cap = capture()
        val otherConfig = base.copy(id = "next", catalogConfigurationId = "other")
        val otherSlot = base.copy(id = "next", recipeSlotId = "other-slot")
        val otherBrand = base.copy(sets = base.sets.map { it.copy(machineBrand = "Otra máquina") })
        assertEquals(otherConfig, applyApreXrmToExercise(otherConfig, cap))
        assertEquals(otherSlot, applyApreXrmToExercise(otherSlot, cap))
        assertEquals(otherBrand, applyApreXrmToExercise(otherBrand, cap))
    }

    @Test fun marcadorInvalidoReferenciaDeCapacidadYSeriesExtrasSeConservan() {
        val cap = capture()
        assertEquals(base, applyApreXrmToExercise(base, cap.copy(reference = cap.reference.copy(kind = PlanLoadReferenceKind.EXERCISE_1RM))))
        val broken = base.copy(apreBlock = null)
        assertEquals(broken, applyApreXrmToExercise(broken, cap))
        val extra = base.copy(sets = base.sets + ExerciseSet("extra", weight = 80.0))
        assertEquals(extra, applyApreXrmToExercise(extra, cap))
    }

    @Test fun rematerializarConsumeLaBolsaSinVolverAlTmYConSaltoReal() {
        val cap = capture(completed = done.copy(weight = 51.0, originalLoad = 51.0), step = 1.0)
        assertEquals(53.0, cap.reference.capturedLoadKg!!, 0.0)
        assertEquals(1.0, cap.reference.metadata!!.loadIncrementKg!!, 0.0)
        val result = consumeApreXrmReferences(base, listOf(cap.reference))
        assertEquals(listOf(27.0, 40.0, 53.0, 53.0), result.sets.map { it.weight })
        assertEquals(180.0, result.referenceTrainingMax!!, 0.0)
    }

    @Test fun historialAjenoDeOtroContextoORangoNoSeAplica() {
        val cap = capture()
        val other = cap.reference.copy(metadata = cap.reference.metadata!!.copy(contextKey = "otro"))
        assertEquals(base, consumeApreXrmReferences(base, listOf(other)))
        assertEquals(base, consumeApreXrmReferences(base, listOf(cap.reference.copy(repMin = 6, repMax = 6))))
    }

    @Test fun laCargaYLaMarcaManualDelOtroLadoSeConservan() {
        val paired = base.copy(unilateralMode = UnilateralMode.UNILATERAL_DIFFERENTIAL,
            sets = base.sets.map { it.copy(leftTarget = UnilateralTarget(weight = 150.0),
                rightTarget = UnilateralTarget(weight = 140.0), manualLoadRequiredSides = setOf("right")) })
        val cap = capture(paired, done.copy(side = "left"))
        val result = applyApreXrmToExercise(paired, cap)
        assertEquals(listOf(82.5, 122.5, 162.5, 162.5), result.sets.map { it.leftTarget!!.weight })
        assertTrue(result.sets.all { it.rightTarget!!.weight == 140.0 && it.manualLoadRequiredSides == setOf("right") })
        val right = capture(paired, done.copy(side = "right"))
        assertEquals(paired, applyApreXrmToExercise(paired, right))
    }
}
