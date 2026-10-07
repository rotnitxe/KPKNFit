package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.training.SplitApplicationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lógica pura del bloque Entreno que sobrevive a los pasos retirados: qué splits
 * son reales y compatibles con la frecuencia y la conversión de filas crudas de
 * calentamiento a `SetRecipe` (la usarán el reparto de la semana y la aproximación).
 *
 * Sin ejecución en esta rama (el build central manda); contratos puros, sin
 * Compose ni ViewModel. La selección canónica de pasos la cubre M1 en sus
 * tests de `selectedValues`/`withStepChoice`.
 */
class SetupTrainingSelectionTest {

    @Test
    fun `compatibles sin frecuencia son exactamente los visibles del catalogo`() {
        assertEquals(
            SPLIT_TEMPLATES.filter { it.isVisibleForApplication },
            compatibleSplitTemplates(daysPerWeek = null, startDay = 1),
        )
    }

    @Test
    fun `cada compatible recuenta sus dias con el motor real`() {
        val three = compatibleSplitTemplates(daysPerWeek = 3, startDay = 1)
        three.forEach { split ->
            assertTrue(split.isVisibleForApplication)
            assertEquals(3, SplitApplicationEngine.patternToTrainingDays(split.pattern, startDay = 1).size)
        }
        val six = compatibleSplitTemplates(daysPerWeek = 6, startDay = 1)
        six.forEach { split ->
            assertEquals(6, SplitApplicationEngine.patternToTrainingDays(split.pattern, startDay = 1).size)
        }
    }

    @Test
    fun `filas de calentamiento conservan lo escrito y dejan huecos visibles`() {
        val recipes = warmupRecipesFromRaw(listOf("40" to "8", "60" to "", "" to "5"))
        assertEquals(3, recipes.size)
        assertEquals(40.0, recipes[0].percent!!, 0.0)
        assertEquals(8, recipes[0].reps)
        assertTrue(recipes[0].isWarmup)
        // Fila ("60" a secas, reps en blanco): el porcentaje escrito se
        // conserva tal cual; solo el hueco de repeticiones queda null.
        assertEquals(60.0, recipes[1].percent!!, 0.0)
        assertNull(recipes[1].reps)
        // Fila (porcentaje en blanco, "5"): al revés, hueco en percent.
        assertNull(recipes[2].percent)
        assertEquals(5, recipes[2].reps)
        assertEquals(emptyList<SetRecipe>(), warmupRecipesFromRaw(emptyList()))
    }
}
