package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectivePortionEngineTest {

    private fun resolveGrams(expr: String): Double? =
        SubjectivePortionEngine.resolve(expression = expr)?.grams

    @Test
    fun `IT3 override de utensilio cambia los gramos escalando el volumen`() {
        val original = SubjectivePortionEngine.currentUtensilOverrides()
        try {
            SubjectivePortionEngine.applyUtensilOverrides(emptyMap())
            val before = resolveGrams("una taza de avena")!!
            SubjectivePortionEngine.applyUtensilOverrides(mapOf("taza" to 300.0))
            val after = resolveGrams("una taza de avena")!!
            assertEquals(before * 300.0 / 250.0, after, 0.01)
        } finally {
            SubjectivePortionEngine.applyUtensilOverrides(original)
        }
    }

    @Test
    fun `IT3 override invalido o nulo se ignora y usa la base`() {
        val original = SubjectivePortionEngine.currentUtensilOverrides()
        try {
            SubjectivePortionEngine.applyUtensilOverrides(mapOf("taza" to -5.0, "vaso" to 0.0))
            // Sin categoría el engine usa MIXED (densidad 0.8).
            assertEquals(40.0, resolveGrams("una taza de avena")!!, 0.01)
            assertEquals(200.0, resolveGrams("un vaso de agua")!!, 0.01)
        } finally {
            SubjectivePortionEngine.applyUtensilOverrides(original)
        }
    }

    @Test
    fun `IT3 sin overrides los valores base se mantienen`() {
        val original = SubjectivePortionEngine.currentUtensilOverrides()
        try {
            SubjectivePortionEngine.applyUtensilOverrides(emptyMap())
            assertEquals(40.0, resolveGrams("una taza de avena")!!, 0.01)
            assertEquals(12.0, resolveGrams("una cucharada de aceite")!!, 0.01)
            assertEquals(4.0, resolveGrams("una cucharadita de azúcar")!!, 0.01)
        } finally {
            SubjectivePortionEngine.applyUtensilOverrides(original)
        }
    }
}
