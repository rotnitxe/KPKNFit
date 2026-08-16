package com.example.kpkn.data.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 2 — Validaciones y procedencia del catálogo global (plan
 * 2026-08-16_nutrition_precision_v2). Cada fila importada debe poder
 * declarar fuente, ID de registro, versión, base nutricional y estado, y las
 * validaciones físicas deben rechazar lo imposible sin castigar diferencias
 * energéticas explicables (fibra, polioles, alcohol).
 */
class FoodCatalogProvenanceTest {

    // ─── Validación física ────────────────────────────────────────────────

    @Test
    fun `pechuga cocida USDA pasa la validacion fisica`() {
        // FDC 331960: 166 kcal / 32,1 P / 0 C / 3,24 G por 100 g
        assertTrue(FoodImporter.hasPhysicallyPlausibleMacros(166.0, 32.1, 0.0, 3.24))
    }

    @Test
    fun `macros negativos o no finitos se rechazan`() {
        assertFalse(FoodImporter.hasPhysicallyPlausibleMacros(100.0, -1.0, 0.0, 0.0))
        assertFalse(FoodImporter.hasPhysicallyPlausibleMacros(100.0, Double.NaN, 0.0, 0.0))
        assertFalse(FoodImporter.hasPhysicallyPlausibleMacros(Double.POSITIVE_INFINITY, 1.0, 1.0, 1.0))
        assertFalse(FoodImporter.hasPhysicallyPlausibleMacros(0.0, 0.0, 0.0, 0.0))
    }

    @Test
    fun `macros individuales sobre 100 g por 100 g se rechazan`() {
        assertFalse(FoodImporter.hasPhysicallyPlausibleMacros(800.0, 120.0, 0.0, 0.0))
        assertFalse(FoodImporter.hasPhysicallyPlausibleMacros(900.0, 0.0, 0.0, 105.0))
        // Grasa pura (aceite, 100 g/100 g) es físicamente posible
        assertTrue(FoodImporter.hasPhysicallyPlausibleMacros(884.0, 0.0, 0.0, 100.0))
    }

    // ─── Flags de calidad ─────────────────────────────────────────────────

    @Test
    fun `energia coherente con atwater no genera flag`() {
        // 32,1×4 + 3,24×9 = 157,6 kcal vs 166 declaradas → 5,3% de desvío
        val flags = FoodImporter.usdaQualityFlags(166.0, 32.1, 0.0, 3.24)
        assertTrue(flags.isEmpty())
    }

    @Test
    fun `incoherencia energetica se marca no se rechaza`() {
        // 4P+4C+9G = 150 pero declara 300 kcal → desvío 100% > 35%
        val flags = FoodImporter.usdaQualityFlags(300.0, 10.0, 20.0, 4.4)
        assertEquals(listOf("ENERGY_MISMATCH"), flags)
    }

    @Test
    fun `fila sin macros se marca incompleta`() {
        assertEquals(listOf("INCOMPLETE"), FoodImporter.usdaQualityFlags(50.0, 0.0, 0.0, 0.0))
    }

    @Test
    fun `off suma flag de baja calidad del parser colaborativo`() {
        val highConfidence = FoodImporter.offQualityFlags(100.0, 10.0, 5.0, 3.0, 0.9f)
        assertTrue(highConfidence.isEmpty())
        val lowConfidence = FoodImporter.offQualityFlags(100.0, 10.0, 5.0, 3.0, 0.4f)
        assertEquals(listOf("LOW_QUALITY"), lowConfidence)
    }

    @Test
    fun `flags se serializan como json estable`() {
        assertEquals("[]", FoodImporter.encodeQualityFlags(emptyList()))
        assertEquals("""["ENERGY_MISMATCH","LOW_QUALITY"]""",
            FoodImporter.encodeQualityFlags(listOf("ENERGY_MISMATCH", "LOW_QUALITY")))
    }

    // ─── Estado desde la descripción de origen ────────────────────────────

    @Test
    fun `descripciones usda declaran estado raw o cooked`() {
        assertEquals(
            "COOKED",
            FoodImporter.stateForDescription(
                "Chicken, broiler or fryers, breast, skinless, boneless, meat only, cooked, braised"
            ),
        )
        assertEquals(
            "RAW",
            FoodImporter.stateForDescription("Chicken, breast, boneless, skinless, raw"),
        )
        assertEquals(
            "COOKED",
            FoodImporter.stateForDescription("Rice, white, long-grain, regular, enriched, cooked"),
        )
        assertEquals(
            "RAW",
            FoodImporter.stateForDescription("Rice, white, long-grain, regular, raw, enriched"),
        )
        assertEquals(
            "UNKNOWN",
            FoodImporter.stateForDescription("Bread, white, commercially prepared"),
        )
    }
}
