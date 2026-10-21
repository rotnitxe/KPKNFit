package com.example.kpkn.screens.workout.components

import com.example.kpkn.data.models.DropSetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registro de la técnica con coma decimal (LR0): un drop escrito como «62,5» llega a la serie con 62,5 kg y no se descarta.
 */
class SetCardTechniqueCommitTest {

    @Test
    fun unDropConComaDecimalSeRegistraConSuPeso() {
        var registrados: List<DropSetData>? = null

        val guardado = commit(
            dropRows = listOf(TechniqueCheckRow(weightText = "62,5", repsText = "3", done = true)),
            restPauseRows = emptyList(),
            dropEnabled = true,
            restPauseEnabled = false,
            onCompleteTechniques = { drops, _ -> registrados = drops },
        )

        assertTrue(guardado)
        assertEquals(listOf(DropSetData(62.5, 3)), registrados)
    }
}
