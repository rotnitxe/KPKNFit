package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * WP-U9 (C7): volver a analizar la descripción no puede borrar los alimentos que el usuario sumó por la búsqueda.
 * [mergeReanalyzedTags] es pura: aquí se fija qué sobrevive, qué se reemplaza y qué se fusiona.
 */
class TagMergeOnReanalyzeTest {

    private fun fromText(name: String, id: String, edited: Boolean = false, grams: Double? = null) =
        ResolvedTag(id = id, tag = name, amountGrams = grams, hasManualEdits = edited)

    /** Lo que deja la búsqueda: el drawer lo crea con ediciones manuales y origen SEARCH. */
    private fun picked(name: String, id: String) =
        ResolvedTag(id = id, tag = name, hasManualEdits = true, origin = TagOrigin.SEARCH)

    private fun ids(tags: List<ResolvedTag>) = tags.map { it.id }

    /** Puerto del resolvedor sobre el catálogo estático (mismo patrón que TagResolverEnrichmentTest). */
    private val resolverPort: FoodResolutionPort by lazy {
        val staticFoods = buildFoodDatabase()
        val index = FoodIndex().apply { build(emptyList(), staticFoods, FOOD_ALIASES) }
        val dao = java.lang.reflect.Proxy.newProxyInstance(
            NutritionDao::class.java.classLoader, arrayOf(NutritionDao::class.java),
        ) { _, _, _ -> null } as NutritionDao
        val resolver = SmartFoodResolver(dao, index)
        object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                resolver.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = staticFoods.firstOrNull { it.id == id }
            override suspend fun staticFood(tag: String): FoodItem? = HouseholdPortions.householdStaticFood(tag)
            override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
    }

    @Test
    fun `a food added by search survives a changed description`() {
        val first = picked("palta", "search-palta")
        val second = picked("queso", "search-queso")
        val previous = listOf(first, fromText("arroz", "old-arroz"), second)
        val parsed = listOf(fromText("pollo", "new-pollo"))

        val merged = mergeReanalyzedTags(previous, parsed, sameRequest = false)

        // El texto nuevo reemplaza al viejo; lo buscado queda al final, en su orden, sin tocar.
        assertEquals(listOf("new-pollo", "search-palta", "search-queso"), ids(merged))
        assertSame(first, merged[1])
        assertSame(second, merged[2])
    }

    @Test
    fun `the same description keeps the manual edits of its tags and the searched food`() {
        val edited = fromText("pollo", "old-pollo", edited = true, grams = 250.0)
        val previous = listOf(edited, picked("palta", "search-palta"))
        val parsed = listOf(fromText("pollo", "new-pollo", grams = 120.0))

        val merged = mergeReanalyzedTags(previous, parsed, sameRequest = true)

        assertEquals(listOf("old-pollo", "search-palta"), ids(merged))
        assertSame(edited, merged[0])
        assertEquals(250.0, merged[0].amountGrams ?: Double.NaN, 0.0)
    }

    @Test
    fun `the same description still drops an edited tag whose mention is gone from the text`() {
        val edited = fromText("arroz", "old-arroz", edited = true)

        val merged = mergeReanalyzedTags(listOf(edited), listOf(fromText("pollo", "new-pollo")), sameRequest = true)

        assertEquals(listOf("new-pollo"), ids(merged))
    }

    @Test
    fun `a changed description supersedes the tags of the old one even when they were edited`() {
        val edited = fromText("pollo", "old-pollo", edited = true, grams = 250.0)
        val parsed = listOf(fromText("pollo", "new-pollo", grams = 120.0), fromText("arroz", "new-arroz"))

        val merged = mergeReanalyzedTags(listOf(edited), parsed, sameRequest = false)

        assertEquals(listOf("new-pollo", "new-arroz"), ids(merged))
        assertEquals(120.0, merged[0].amountGrams ?: Double.NaN, 0.0)
    }

    @Test
    fun `a pinned tag is never duplicated`() {
        val search = picked("palta", "search-palta")

        // El resultado del análisis ya trae ese id (o la lista previa lo repite): una sola copia.
        val sameIdInParse = mergeReanalyzedTags(listOf(search), listOf(search.copy(tag = "otra")), sameRequest = false)
        assertEquals(listOf("search-palta"), ids(sameIdInParse))
        val repeatedInPrevious = mergeReanalyzedTags(listOf(search, search), emptyList(), sameRequest = true)
        assertEquals(listOf("search-palta"), ids(repeatedInPrevious))

        // Y analizar otra vez sobre su propio resultado es estable.
        val first = mergeReanalyzedTags(listOf(search), listOf(fromText("pollo", "new-pollo")), sameRequest = false)
        val second = mergeReanalyzedTags(first, listOf(fromText("pollo", "newer-pollo")), sameRequest = true)
        assertEquals(listOf("newer-pollo", "search-palta"), ids(second))
    }

    @Test
    fun `an analysis that finds nothing leaves only the pinned foods`() {
        val search = picked("palta", "search-palta")

        val merged = mergeReanalyzedTags(listOf(fromText("arroz", "old-arroz"), search), emptyList(), sameRequest = false)

        assertEquals(listOf("search-palta"), ids(merged))
    }

    @Test
    fun `foods loaded for editing are pinned like searched ones`() {
        val loaded = ResolvedTag(id = "loaded", tag = "arroz", origin = TagOrigin.EDIT)

        val merged = mergeReanalyzedTags(listOf(loaded), listOf(fromText("pollo", "new-pollo")), sameRequest = false)

        assertEquals(listOf("new-pollo", "loaded"), ids(merged))
    }

    @Test
    fun `last resort placeholders are replaced by a retry instead of stacking on top of it`() {
        val placeholder = ResolvedTag(id = "placeholder", tag = "pollo con arroz", origin = TagOrigin.LAST_RESORT)
        val search = picked("palta", "search-palta")
        val parsed = listOf(fromText("pollo", "new-pollo"), fromText("arroz", "new-arroz"))

        listOf(true, false).forEach { sameRequest ->
            val merged = mergeReanalyzedTags(listOf(placeholder, search), parsed, sameRequest)
            assertEquals("sameRequest=$sameRequest", listOf("new-pollo", "new-arroz", "search-palta"), ids(merged))
        }
    }

    @Test
    fun `refreshing a tag keeps where it came from`() {
        TagOrigin.entries.forEach { origin ->
            val refreshed = NutritionInterpretationBridge.refresh(ResolvedTag(tag = "arroz", amountGrams = 150.0, origin = origin))
            assertEquals(origin, refreshed.origin)
        }
    }

    @Test
    fun `answering the tortilla question keeps where the tag came from`() = runBlocking {
        val resolver = TagResolver(resolverPort)
        val original = resolver.resolveAll(parseMealDescription("40 g tortilla")).first.single()

        TagOrigin.entries.forEach { origin ->
            val corrected = resolver.resolveDeclaredComposition(original.copy(origin = origin), "tortilla de huevo")
            assertEquals(origin, corrected.origin)
        }
    }

    @Test
    fun `tags come from the description unless said otherwise and only search and edit are pinned`() {
        assertEquals(TagOrigin.DESCRIPTION, ResolvedTag(tag = "arroz").origin)
        assertEquals(
            mapOf(
                TagOrigin.DESCRIPTION to false,
                TagOrigin.SEARCH to true,
                TagOrigin.LAST_RESORT to false,
                TagOrigin.EDIT to true,
            ),
            TagOrigin.entries.associateWith { it.isPinned },
        )
    }
}
