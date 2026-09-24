package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Idempotencia POR operación de edición del editor directo: cada operación
 * tiene su propio identificador (nunca se reutiliza para siempre el del alta)
 * y los reintentos de esa misma operación lo repiten.
 */
class NutritionEditCommitIdsTest {

    private fun sequentialIds(): NutritionEditCommitIds {
        var counter = 0
        return NutritionEditCommitIds(newId = { "edit-op-${counter++}" })
    }

    @Test
    fun retryOfTheSameEditOperationReusesItsOwnId() {
        val ids = sequentialIds()

        val first = ids.idFor(1)
        val retry = ids.idFor(1)
        val doubleTap = ids.idFor(1)

        assertEquals(first, retry)
        assertEquals(first, doubleTap)
    }

    @Test
    fun eachEditOperationGetsItsOwnIdAndTheCreationIdIsNotReusedForever() {
        val ids = sequentialIds()

        val creation = ids.idFor(0) // alta
        val firstEdit = ids.idFor(1)
        val secondEdit = ids.idFor(2)

        assertEquals("edit-op-0", creation)
        assertNotEquals(creation, firstEdit)
        assertNotEquals(firstEdit, secondEdit)
        assertNotEquals(creation, secondEdit)
        // Volver a la revisión del alta sigue siendo la MISMA operación:
        // mismo id, replay sin duplicar.
        assertEquals(creation, ids.idFor(0))
    }

    @Test
    fun freshEditorSessionNeverReusesAnotherSessionsId() {
        val firstSession = NutritionEditCommitIds().idFor(0)
        val secondSession = NutritionEditCommitIds().idFor(0)

        assertNotEquals(firstSession, secondSession)
    }
}
