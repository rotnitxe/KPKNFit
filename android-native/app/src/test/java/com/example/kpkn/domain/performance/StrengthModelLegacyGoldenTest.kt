package com.example.kpkn.domain.performance

import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calculations.calculateWeightFrom1RM
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tabla congelada antes del cambio con la híbrida y su inversa de 5d0abc72f; nunca consulta el modelo para generar expectativas. */
@Suppress("DEPRECATION")
@RunWith(Parameterized::class)
class StrengthModelLegacyGoldenTest(
    private val weight: Double,
    private val estimates: List<Double>,
    private val loads: List<Double>,
) {
    @Test
    fun `sin RIR mantiene las cincuenta estimaciones historicas`() {
        assertEquals(50, estimates.size)
        estimates.forEachIndexed { index, expected ->
            assertEquals("$weight × ${index + 1}", expected, StrengthModel.estimate1RM(weight, index + 1), 0.0)
            assertEquals(expected, calculateHybrid1RM(weight, index + 1), 0.0)
        }
    }

    @Test
    fun `la inversa mantiene las cincuenta cargas historicas`() {
        assertEquals(50, loads.size)
        loads.forEachIndexed { index, expected ->
            assertEquals("$weight RM ${index + 1}", expected, StrengthModel.weightFor(weight, index + 1), 0.0)
            assertEquals(expected, calculateWeightFrom1RM(weight, index + 1), 0.0)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "carga={0}")
        fun data(): List<Array<Any>> = listOf(
            arrayOf(20.0, listOf(20.0, 20.6, 21.2, 21.8, 22.5, 23.2, 24.0, 24.8, 25.7, 26.7, 27.3, 28.0, 28.7, 29.3, 30.0, 30.7, 31.3, 32.0, 32.7, 33.3, 33.7, 34.1, 34.5, 34.8, 35.2, 35.6, 35.9, 36.3, 36.7, 37.1, 37.4, 37.8, 38.2, 38.5, 38.9, 39.3, 39.6, 40.0, 40.4, 40.7, 41.1, 41.5, 41.8, 42.2, 42.6, 42.9, 43.3, 43.7, 44.0, 44.4), listOf(20.0, 19.4, 18.9, 18.3, 17.8, 17.2, 16.7, 16.1, 15.6, 15.0, 14.6, 14.3, 14.0, 13.6, 13.3, 13.0, 12.8, 12.5, 12.2, 12.0, 11.9, 11.7, 11.6, 11.5, 11.4, 11.2, 11.1, 11.0, 10.9, 10.8, 10.7, 10.6, 10.5, 10.4, 10.3, 10.2, 10.1, 10.0, 9.9, 9.8, 9.7, 9.6, 9.6, 9.5, 9.4, 9.3, 9.2, 9.2, 9.1, 9.0)),
            arrayOf(62.5, listOf(62.5, 64.3, 66.2, 68.2, 70.3, 72.6, 75.0, 77.6, 80.4, 83.3, 85.4, 87.5, 89.6, 91.7, 93.8, 95.8, 97.9, 100.0, 102.1, 104.2, 105.3, 106.5, 107.7, 108.8, 110.0, 111.2, 112.3, 113.5, 114.7, 115.8, 117.0, 118.1, 119.3, 120.4, 121.6, 122.7, 123.9, 125.0, 126.2, 127.3, 128.5, 129.6, 130.8, 131.9, 133.1, 134.2, 135.3, 136.5, 137.6, 138.7), listOf(62.5, 60.8, 59.0, 57.3, 55.6, 53.8, 52.1, 50.3, 48.6, 46.9, 45.7, 44.6, 43.6, 42.6, 41.7, 40.8, 39.9, 39.1, 38.3, 37.5, 37.1, 36.7, 36.3, 35.9, 35.5, 35.1, 34.8, 34.4, 34.1, 33.7, 33.4, 33.1, 32.7, 32.4, 32.1, 31.8, 31.5, 31.2, 31.0, 30.7, 30.4, 30.1, 29.9, 29.6, 29.4, 29.1, 28.9, 28.6, 28.4, 28.2)),
            arrayOf(100.0, listOf(100.0, 102.9, 105.9, 109.1, 112.5, 116.1, 120.0, 124.1, 128.6, 133.3, 136.7, 140.0, 143.3, 146.7, 150.0, 153.3, 156.7, 160.0, 163.3, 166.7, 168.5, 170.4, 172.3, 174.1, 176.0, 177.9, 179.7, 181.6, 183.5, 185.3, 187.2, 189.0, 190.9, 192.7, 194.5, 196.4, 198.2, 200.1, 201.9, 203.7, 205.6, 207.4, 209.2, 211.1, 212.9, 214.7, 216.5, 218.3, 220.2, 222.0), listOf(100.0, 97.2, 94.4, 91.7, 88.9, 86.1, 83.3, 80.6, 77.8, 75.0, 73.2, 71.4, 69.8, 68.2, 66.7, 65.2, 63.8, 62.5, 61.2, 60.0, 59.3, 58.7, 58.0, 57.4, 56.8, 56.2, 55.6, 55.1, 54.5, 54.0, 53.4, 52.9, 52.4, 51.9, 51.4, 50.9, 50.4, 50.0, 49.5, 49.1, 48.6, 48.2, 47.8, 47.4, 47.0, 46.6, 46.2, 45.8, 45.4, 45.0)),
            arrayOf(142.25, listOf(142.25, 146.3, 150.6, 155.2, 160.0, 165.2, 170.7, 176.6, 182.9, 189.7, 194.4, 199.1, 203.9, 208.6, 213.4, 218.1, 222.9, 227.6, 232.3, 237.1, 239.7, 242.4, 245.1, 247.7, 250.4, 253.0, 255.7, 258.3, 261.0, 263.6, 266.2, 268.9, 271.5, 274.1, 276.7, 279.4, 282.0, 284.6, 287.2, 289.8, 292.4, 295.0, 297.6, 300.2, 302.8, 305.4, 308.0, 310.6, 313.2, 315.8), listOf(142.25, 138.3, 134.3, 130.4, 126.4, 122.5, 118.5, 114.6, 110.6, 106.7, 104.1, 101.6, 99.2, 97.0, 94.8, 92.8, 90.8, 88.9, 87.1, 85.4, 84.4, 83.5, 82.6, 81.7, 80.8, 80.0, 79.1, 78.3, 77.5, 76.8, 76.0, 75.3, 74.5, 73.8, 73.1, 72.4, 71.8, 71.1, 70.5, 69.8, 69.2, 68.6, 68.0, 67.4, 66.8, 66.3, 65.7, 65.1, 64.6, 64.1))
        )
    }
}
