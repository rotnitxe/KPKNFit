package com.example.kpkn.screens.workout

import com.example.kpkn.domain.calculations.calculateHybrid1RM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tabla RM de la calculadora (LS7b): la fila «1 RM» es el 1RM que se muestra arriba y las demás son la inversa de la misma fórmula,
 * así que la tabla baja de forma monótona y cada fila vuelve a dar ese 1RM. Pura, sin Robolectric.
 */
class WorkoutRmTableTest {

    private val estimate = calculateHybrid1RM(80.0, 8) // 99,3 kg: lo que muestra el módulo «1RM estimado»

    @Test
    fun laFila1RMEsLaEstimacionDeArriba() {
        val rows = rmTableLoads(estimate)
        assertEquals(1, rows.first().first)
        assertEquals(estimate, rows.first().second, 1e-9)
    }

    @Test
    fun lasFilasBajanDeFormaMonotona() {
        val rows = rmTableLoads(estimate)
        assertEquals((1..10).toList(), rows.map { it.first })
        for (i in 1 until rows.size) {
            assertTrue("la fila ${rows[i].first} no baja", rows[i].second < rows[i - 1].second)
        }
    }

    @Test
    fun cadaFilaVuelveAlMismo1RMConLaFormulaDeArriba() {
        for ((reps, load) in rmTableLoads(estimate)) {
            // Redondeo a 0,1 kg en ambos sentidos: la tolerancia cubre un decimal.
            assertTrue("reps $reps", abs(calculateHybrid1RM(load, reps) - estimate) <= 0.15)
        }
    }
}
