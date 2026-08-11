package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRI-ANALYSIS: el último nivel de seguridad del pipeline de análisis por descripción.
 * Debe separar por conectores de lista sin lanzar y nunca dejar un callejón sin salida.
 */
class LastResortSplitterTest {

    @Test
    fun `splits by connectors y and con`() {
        val result = LastResortSplitter.split("fideos con salsa de tomate y un poco de carne molida")
        assertEquals(listOf("fideos", "salsa de tomate", "un poco de carne molida"), result)
    }

    @Test
    fun `splits single foods by comma and con`() {
        assertEquals(listOf("pollo", "arroz", "ensalada"), LastResortSplitter.split("pollo, arroz y ensalada"))
        // "con" y "y" separan alimentos en el último nivel (para que el usuario los revisa por separado).
        assertEquals(listOf("arroz", "pollo", "ensalada"), LastResortSplitter.split("arroz con pollo y ensalada"))
    }

    @Test
    fun `no separators returns whole text as single fragment`() {
        assertEquals(listOf("salsa de tomate"), LastResortSplitter.split("salsa de tomate"))
    }

    @Test
    fun `blank input returns empty`() {
        assertTrue(LastResortSplitter.split("   ").isEmpty())
        assertTrue(LastResortSplitter.split("").isEmpty())
    }

    @Test
    fun `filters out tiny fragments`() {
        val result = LastResortSplitter.split("pollo y arroz")
        assertTrue(result.all { it.length >= 2 })
    }

    @Test
    fun `never throws on any input`() {
        listOf(
            "…".repeat(5000),
            "fideos con salsa de tomate y un poco de carne molida",
            "\n,;+con  y   e----",
            "x y z",
        ).forEach { input ->
            // no exception expected
            LastResortSplitter.split(input)
        }
    }
}
