package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden set de RESOLUCIÓN (Iteración 2): seed de métricas de identidad.
 *
 * Cada caso ejecuta el pipeline real parser → TagResolver con el catálogo
 * estático completo (sin Room): texto de usuario → alimento resuelto.
 * Protege contra regresiones de identidad ("escribo X y registra Y") y
 * garantiza determinismo ante el mismo input.
 */
class ResolutionGoldenCorpusTest {

    private class RealPort(
        private val resolver: SmartFoodResolver,
        private val foods: List<FoodItem>,
    ) : FoodResolutionPort {
        override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
            resolver.resolve(tag, brandHint, contextHint, stateHint)

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

    // ─── Identidad: casos curados críticos ────────────────────────────────

    @Test
    fun `papas fritas resuelve a Papa frita`() = runBlocking {
        val tags = resolve("papas fritas")
        assertEquals(1, tags.size)
        assertEquals("gen021f", tags.single().foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
        assertTrue(tags.single().isResolved)
        // Nota: los gramos de un ítem sin cantidad vienen del dataset de porciones
        // (estado global del singleton), así que aquí solo se valida identidad.
        assertNotNull(tags.single().loggedFood)
    }

    @Test
    fun `manjar resuelve a Manjar y no a miel`() = runBlocking {
        val tags = resolve("manjar")
        assertEquals("gen109", tags.single().foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
    }

    @Test
    fun `torta nunca se autoconfirma como pan blanco`() = runBlocking {
        val tags = resolve("torta")
        assertEquals(FoodResolutionStatus.NEEDS_CONFIRMATION, tags.single().resolutionStatus)
        assertFalse(tags.single().isResolved)
        assertEquals("gen019", tags.single().foodItem?.id)
        assertTrue(tags.single().statusText.contains("parecido"))
    }

    @Test
    fun `cafe con leche requiere confirmacion`() = runBlocking {
        val tags = resolve("café con leche")
        assertEquals(FoodResolutionStatus.NEEDS_CONFIRMATION, tags.single().resolutionStatus)
        assertEquals("gen016", tags.single().foodItem?.id)
    }

    @Test
    fun `quesadilla requiere confirmacion`() = runBlocking {
        val tags = resolve("quesadilla")
        assertEquals(FoodResolutionStatus.NEEDS_CONFIRMATION, tags.single().resolutionStatus)
    }

    @Test
    fun `fideos pide estado y no expone macros provisionales`() = runBlocking {
        val tags = resolve("fideos")
        assertEquals(FoodResolutionStatus.NEEDS_STATE, tags.single().resolutionStatus)
        assertNull(tags.single().loggedFood)
    }

    @Test
    fun `pollo a la plancha usa la variante preparada`() = runBlocking {
        val tags = resolve("200g pollo a la plancha")
        assertEquals("gen003c", tags.single().foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
        assertEquals(200.0, tags.single().amountGrams ?: 0.0, 0.01)
    }

    @Test
    fun `salmon al horno usa la variante al horno`() = runBlocking {
        val tags = resolve("salmón al horno")
        assertEquals("gen009h", tags.single().foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
    }

    @Test
    fun `cucharadita de azucar son ~5 gramos`() = runBlocking {
        val tags = resolve("1 cucharadita de azúcar")
        val grams = tags.single().amountGrams ?: 0.0
        assertTrue("cucharadita debe ser ~5g, fue $grams", grams in 3.0..8.0)
    }

    // ─── Determinismo: mismo input → mismo alimento ───────────────────────

    @Test
    fun `atun es deterministico entre ejecuciones`() = runBlocking {
        val ids = (1..3).map { resolve("atún").single().foodItem?.id }
        assertTrue("atún debe resolver a algo", ids.all { it != null })
        assertEquals("todas las ejecuciones dan el mismo alimento", 1, ids.distinct().size)
        assertEquals("gen029", ids.first())
    }

    @Test
    fun `cazuela es deterministico entre ejecuciones`() = runBlocking {
        val ids = (1..3).map { resolve("cazuela").single().foodItem?.id }
        assertEquals(1, ids.distinct().size)
        assertEquals("cl004", ids.first())
    }

    @Test
    fun `idempotencia del pipeline completo`() = runBlocking {
        val desc = "200g pechuga de pollo a la plancha, 150g arroz blanco, ensalada grande"
        suspend fun signature() = resolve(desc).joinToString("|") {
            "${it.tag}:${it.foodItem?.id}:${it.resolutionStatus}:${it.amountGrams}"
        }
        assertEquals(signature(), signature())
        assertEquals(signature(), signature())
    }

    // ─── E17: filas reales conflictivas (atún agua/aceite, cazuela, pasta) ──

    @Test
    fun `atun a secas resuelve a la variante al agua y no a la del aceite`() = runBlocking {
        val tags = resolve("atún")
        assertEquals(FoodResolutionStatus.NEEDS_CONFIRMATION, tags.single().resolutionStatus)
        assertEquals("gen029", tags.single().foodItem?.id)
        assertTrue(tags.single().foodItem?.name?.contains("agua", ignoreCase = true) == true)
    }

    @Test
    fun `atun en aceite resuelve a su propia variante`() = runBlocking {
        val tags = resolve("atún en aceite")
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
        assertEquals("gen029e", tags.single().foodItem?.id)
    }

    @Test
    fun `atun al agua resuelve a la variante al agua`() = runBlocking {
        val tags = resolve("atún al agua")
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
        assertEquals("gen029", tags.single().foodItem?.id)
    }

    @Test
    fun `pasta a secas no se autoconfirma y pide estado`() = runBlocking {
        val tags = resolve("pasta")
        assertEquals(FoodResolutionStatus.NEEDS_STATE, tags.single().resolutionStatus)
        assertEquals("gen040", tags.single().foodItem?.id)
    }

    @Test
    fun `pasta cocida resuelve a la variante cocida`() = runBlocking {
        val tags = resolve("pasta cocida")
        assertEquals("gen040", tags.single().foodItem?.id)
    }

    @Test
    fun `cazuela resuelve al plato preparado y no a sus ingredientes sueltos`() = runBlocking {
        val tags = resolve("cazuela")
        assertEquals("cl004", tags.single().foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
    }

    @Test
    fun `cazuela de vacuno resuelve al plato preparado de vacuno`() = runBlocking {
        val tags = resolve("cazuela de vacuno")
        assertEquals("cl030", tags.single().foodItem?.id)
        assertEquals(FoodResolutionStatus.AUTO, tags.single().resolutionStatus)
    }

    // ─── Anti-auto-refuerzo del aprendizaje ───────────────────────────────

    @Test
    fun `IT2 el boost aprendido no auto-selecciona un match debil`() = runBlocking {
        val foods = listOf(
            FoodItem(
                id = "t1", name = "Pollo al Horno Especial", servingSize = 100.0, unit = "g",
                calories = 180.0, protein = 25.0, carbs = 2.0, fats = 8.0,
            ),
            FoodItem(
                id = "t2", name = "Pollo Sancochado", servingSize = 100.0, unit = "g",
                calories = 160.0, protein = 24.0, carbs = 1.0, fats = 6.0,
            ),
        )
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = foods, staticAliases = emptyMap())
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)

        // El usuario "corrigió" una vez hacia t1 para la query "pollo".
        resolver.recordLearned("pollo", null, "t1", null, null)

        val result = resolver.resolve("pollo")

        assertEquals("el boost re-rankea a t1 al top", "t1", result.resolvedFoodId)
        assertEquals(
            "el boost aprendido (0.34) NO debe cruzar el umbral de AUTO por sí solo",
            SmartFoodResolver.Decision.NEEDS_REVIEW,
            result.decision,
        )
    }

    @Test
    fun `IT2 aprendizaje con marca normalizada usa la misma clave`() = runBlocking {
        val foods = listOf(
            FoodItem(
                id = "b1", name = "Pan Integral", brand = "Watt's", servingSize = 100.0, unit = "g",
                calories = 250.0, protein = 10.0, carbs = 40.0, fats = 4.0,
            ),
        )
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = foods, staticAliases = emptyMap())
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)

        resolver.recordLearned("pan", "Watt's", "b1", null, null)
        val first = resolver.resolve("pan", "Watt's")
        val second = resolver.resolve("pan", "watts")

        assertEquals("la clave normalizada une Watt's y watts", first.resolvedFoodId, second.resolvedFoodId)
        assertEquals("b1", second.resolvedFoodId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun noOpNutritionDao(): com.example.kpkn.data.db.NutritionDao {
        return java.lang.reflect.Proxy.newProxyInstance(
            com.example.kpkn.data.db.NutritionDao::class.java.classLoader,
            arrayOf(com.example.kpkn.data.db.NutritionDao::class.java),
        ) { _, _, _ -> null } as com.example.kpkn.data.db.NutritionDao
    }
}
