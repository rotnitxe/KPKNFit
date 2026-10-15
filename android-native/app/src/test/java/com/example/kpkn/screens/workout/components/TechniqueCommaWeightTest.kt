package com.example.kpkn.screens.workout.components

import com.example.kpkn.screens.sessioneditor.components.DropSetPlanDefaults
import com.example.kpkn.screens.workout.WorkoutTechniqueDraftKind
import com.example.kpkn.screens.workout.WorkoutTechniqueProgressDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Coma decimal en los pesos de la técnica (LR1c2). Un drop escrito como «62,5» hereda peso al añadir el siguiente (4.º o 5.º, sin
 * sugerencia del plan) y el borrador guiado lo conserva. Sin el arreglo, el drop nuevo sale sin peso y el borrador vuelve a cero.
 */
class TechniqueCommaWeightTest {

    @Test
    fun dropAnadidoTrasUnoConComaHeredaSuPesoMenosUnPaso() {
        val nuevo = nextDropWeightKg(
            mainWeight = 100.0,
            mainReps = 8,
            nextIndex = DropSetPlanDefaults.MaxDrops,
            lastWeightText = "62,5",
        )

        assertNotNull("el drop nuevo debe heredar el peso de «62,5»", nuevo)
        assertEquals(62.5 - DropSetPlanDefaults.DropKg, nuevo!!, 0.001)
    }

    @Test
    fun borradorGuiadoConComaConservaElPesoDelDrop() {
        val fase = WorkoutTechniqueProgressDraft(
            kind = WorkoutTechniqueDraftKind.GUIDED_DROP,
            phaseIndex = 0,
            phaseCount = 2,
            dropWeightText = "62,5",
        ).toGuidedPhaseOrNull() as GuidedTechniquePhase.DropSet

        assertEquals(62.5, fase.suggestedWeight, 0.001)
    }

    @Test
    fun dropGuiadoEscritoConComaGuardaSuPeso() {
        assertEquals(62.5, parseGuidedDropWeightKg("62,5")!!, 0.001)
    }
}
