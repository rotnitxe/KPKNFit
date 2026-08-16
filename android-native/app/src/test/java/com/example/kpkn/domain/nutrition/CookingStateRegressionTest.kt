package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 0 — Regresiones del defecto crítico de estado crudo/cocido (plan
 * 2026-08-16_nutrition_precision_v2).
 *
 * "200 g de pechuga cocida" terminaba en 78–91 g de proteína por tres defectos
 * encadenados: (1) el parser arranca "cocida" del tag antes de resolver y la
 * penalización de estado nunca separa la fila cruda de la cocida; (2) el
 * desempate por foodId prefiere gen003 (cruda); (3) findPreparedVariant solo
 * probaba el sufijo masculino "cocido" y nunca encontraba "(cocida)", por lo
 * que se escalaba la ficha cruda con yield ÷0,75 y encima un factor de cocción.
 *
 * Referencia del asset USDA vigente (food_data/food_nutrient.csv):
 * - FDC 331960 "Chicken… cooked, braised": 166 kcal, 32,1 g proteína, 3,24 g
 *   grasa por 100 g → 200 g cocidos ≈ 64,2 g de proteína.
 * - FDC 2646170 "Chicken, breast, boneless, skinless, raw": ~106 kcal, 22,5 g
 *   proteína, 1,93 g grasa por 100 g.
 *
 * Cada caso ejecuta el pipeline completo parser → TagResolver con el catálogo
 * estático (sin Room) y comprueba identidad, fila fuente, base de peso, gramos
 * visibles y macros finales — no solo el ID seleccionado.
 */
class CookingStateRegressionTest {

    private class RealPort(
        private val resolver: SmartFoodResolver,
        private val foods: List<FoodItem>,
    ) : FoodResolutionPort {
        override suspend fun resolveSmart(
            tag: String,
            brandHint: String?,
            contextHint: String?,
            stateHint: FoodState?,
        ) = resolver.resolve(tag, brandHint, contextHint, stateHint)

        override suspend fun getFoodById(id: String): FoodItem? =
            foods.firstOrNull { it.id == id }

        override suspend fun staticFood(tag: String): FoodItem? =
            findFoodByNormalized(tag)

        override fun staticIsExact(tag: String): Boolean =
            findFoodExactByNormalized(tag) != null

        override fun recordLearned(
            query: String,
            brandHint: String?,
            foodId: String,
            portionGrams: Double?,
            cookingMethod: String?,
        ) = Unit
    }

    private suspend fun resolve(description: String): List<ResolvedTag> {
        val foods = buildFoodDatabase()
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = foods, staticAliases = FOOD_ALIASES)
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        val parsed = parseMealDescription(description)
        val (tags, _) = TagResolver(RealPort(resolver, foods)).resolveAll(parsed)
        return tags
    }

    private fun pechuga(tags: List<ResolvedTag>): ResolvedTag =
        tags.first { it.tag.contains("pechuga") }

    // ─── 200 g cocida: ficha cocida directa, sin doble conversión ───────────

    @Test
    fun `200 g pechuga cocida usa la ficha cocida sin doble conversion`() = runBlocking {
        val tag = pechuga(resolve("200 g de pechuga de pollo cocida"))

        // Identidad y fila fuente: la fila cocida, no la cruda con yield.
        assertEquals("gen004", tag.foodItem?.id)
        assertEquals("Pechuga de Pollo (cocida)", tag.foodItem?.name)
        // Base de peso: la ficha y el peso declarado comparten base cocida.
        assertEquals(FoodState.COOKED, tag.foodState)
        // Gramos visibles: los que introdujo el usuario.
        assertEquals(200.0, tag.amountGrams ?: 0.0, 0.01)

        val logged = tag.loggedFood
        assertNotNull("con estado explícito no debe haber bloqueo por aclaración", logged)
        logged!!
        assertEquals(200.0, logged.amount, 0.01)
        // Macros finales: FDC 331960 → 32,1 g/100 g → 64,2 g para 200 g.
        // El rango 78–91 g (ficha cruda + 200/0,75 + factor de cocción) es el
        // defecto que esta regresión prohíbe.
        assertEquals(64.2, logged.protein, 0.3)
        assertEquals(332.0, logged.calories, 1.0)
        assertEquals(CookingMethod.COCIDO, logged.cookingMethod)
    }

    @Test
    fun `200 g pechuga cocinada equivale a cocida`() = runBlocking {
        val tag = pechuga(resolve("200 g de pechuga de pollo cocinada"))

        assertEquals("gen004", tag.foodItem?.id)
        assertEquals(FoodState.COOKED, tag.foodState)
        val logged = tag.loggedFood
        assertNotNull(logged)
        logged!!
        assertEquals(200.0, logged.amount, 0.01)
        assertEquals(64.2, logged.protein, 0.3)
    }

    @Test
    fun `200 g pechuga a la plancha escala la variante preparada`() = runBlocking {
        val tag = pechuga(resolve("200 g de pechuga de pollo a la plancha"))

        assertEquals("gen003c", tag.foodItem?.id)
        assertEquals(200.0, tag.amountGrams ?: 0.0, 0.01)
        val logged = tag.loggedFood
        assertNotNull(logged)
        logged!!
        // La fila (plancha) ya codifica la preparación: 31 g/100 g directos,
        // sin yield ni factor adicional.
        assertEquals(62.0, logged.protein, 0.3)
    }

    @Test
    fun `200 g pechuga al horno escala la variante preparada`() = runBlocking {
        val tag = pechuga(resolve("200 g de pechuga de pollo al horno"))

        assertEquals("gen003h", tag.foodItem?.id)
        val logged = tag.loggedFood
        assertNotNull(logged)
        logged!!
        assertEquals(200.0, logged.amount, 0.01)
        assertEquals(62.0, logged.protein, 0.3)
    }

    @Test
    fun `200 g pechuga cruda escala la ficha cruda directamente`() = runBlocking {
        val tag = pechuga(resolve("200 g de pechuga de pollo cruda"))

        assertEquals("gen003", tag.foodItem?.id)
        assertEquals(FoodState.RAW, tag.foodState)
        val logged = tag.loggedFood
        assertNotNull(logged)
        logged!!
        assertEquals(200.0, logged.amount, 0.01)
        // FDC 2646170: 22,5 g/100 g crudos → 45 g para 200 g. Hoy la ficha
        // "cruda" tiene densidad cocida (31 g/100 g) y devuelve 62 g.
        assertEquals(45.0, logged.protein, 0.5)
    }

    // ─── Ficha base: la fila "cruda" no puede tener densidad cocida ────────

    @Test
    fun `la ficha cruda de pechuga tiene densidad proteica cruda`() {
        val raw = findFoodByNormalized("pechuga de pollo cruda")
        assertNotNull(raw)
        val cooked = findFoodByNormalized("pechuga de pollo cocida")
        assertNotNull(cooked)
        // Pechuga cruda ~22,5 g/100 g; una "cruda" con ≥26 g/100 g es una
        // ficha cocida mal rotulada y reintroduce la doble conversión.
        assertTrue(
            "ficha cruda con densidad cocida: ${raw!!.protein} g/100 g",
            raw.protein < 26.0,
        )
        assertTrue(raw.protein < cooked!!.protein)
    }

    // ─── Contexto: jamás muta la densidad proteica ──────────────────────────

    @Test
    fun `contexto post-entreno no cambia la proteina por 100 g`() = runBlocking {
        val plain = pechuga(resolve("200 g de pechuga de pollo cocida")).loggedFood!!
        val postWorkout = pechuga(
            resolve("200 g de pechuga de pollo cocida, post entrenamiento"),
        ).loggedFood!!

        assertEquals(plain.protein, postWorkout.protein, 0.01)
        assertEquals(plain.calories, postWorkout.calories, 0.01)
    }

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao {
        return java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao
    }
}
