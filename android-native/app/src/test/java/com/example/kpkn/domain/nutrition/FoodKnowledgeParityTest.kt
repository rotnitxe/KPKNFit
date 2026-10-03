package com.example.kpkn.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * WP-N13, the parity rule: while the Kotlin default ([FoodKnowledgeDefaults]) and the asset both exist they are the same knowledge,
 * entry by entry and in the same order, so that nothing changes whichever one is in force and the asset cannot drift. The test walks
 * every field of every section by reflection: a field added to a section is compared at once, and a new section must be added to
 * [sections] on purpose. When a later increment deletes a Kotlin copy, this test loses that section and the asset is the only copy.
 *
 * The working directory of the unit tests is the `app` module, like the other tests that read `src/main/assets`.
 */
class FoodKnowledgeParityTest {

    private val default = FoodKnowledge.defaultSnapshot()
    private val parsed = parseFoodKnowledge(File("src/main/assets/food_data/food_knowledge_v1.json").readText(Charsets.UTF_8))

    /** The properties of [FoodKnowledgeSnapshot]: the version and one per section of the asset. */
    private val sections =
        listOf("version", "protectedPhrases", "typos", "synonyms", "householdUnits", "containers", "utensils", "densities")

    @Test
    fun `the asset parses to exactly the Kotlin default`() {
        assertEquals(default, parsed)
    }

    @Test
    fun `every section of the snapshot has a parity check here`() {
        // The order of reflected fields is not specified: compare as sets.
        assertEquals(sections.sorted(), instanceFields(FoodKnowledgeSnapshot::class.java).map { it.name }.sorted())
    }

    @Test
    fun `the version is the one this build reads`() = assertSameField("version", default.version, parsed.version)

    @Test
    fun `protected phrases are the same lists in the same order`() =
        assertSameField("protectedPhrases", default.protectedPhrases, parsed.protectedPhrases)

    @Test
    fun `typos are the same pairs in the same order`() = assertSameField("typos", default.typos, parsed.typos)

    @Test
    fun `synonyms are the same pairs in the same order`() = assertSameField("synonyms", default.synonyms, parsed.synonyms)

    @Test
    fun `household units are the same weights and lists`() =
        assertSameField("householdUnits", default.householdUnits, parsed.householdUnits)

    @Test
    fun `containers are the same content rows, liquid containers and food classes`() =
        assertSameField("containers", default.containers, parsed.containers)

    @Test
    fun `utensils are the same default volumes`() = assertSameField("utensils", default.utensils, parsed.utensils)

    @Test
    fun `densities are the same grams per ml, rules and fallback`() = assertSameField("densities", default.densities, parsed.densities)

    @Test
    fun `the density categories of the default are the constants of the enum, in order`() {
        assertEquals(SubjectivePortionEngine.FoodDensityCategory.values().map { it.name }, FoodKnowledgeDefaults.DENSITY_CATEGORIES)
        assertEquals(FoodKnowledgeDefaults.DENSITY_CATEGORIES, default.densities.gramsPerMl.keys.toList())
    }

    // ─── Field-by-field comparison ──────────────────────────────────────────────────────────────────────────────────

    private fun instanceFields(type: Class<*>) =
        type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }.onEach { it.isAccessible = true }

    /** Maps, sets and lists are compared in order (a map by its keys first), numbers exactly, and every other object field by field. */
    private fun assertSameField(path: String, expected: Any?, actual: Any?) {
        when {
            expected == null || actual == null -> assertEquals(path, expected, actual)
            expected is Map<*, *> -> {
                val got = actual as Map<*, *>
                assertEquals("$path: keys, in order", expected.keys.toList(), got.keys.toList())
                expected.forEach { (key, value) -> assertSameField("$path[$key]", value, got[key]) }
            }
            expected is Set<*> -> assertEquals("$path: entries, in order", expected.toList(), (actual as Set<*>).toList())
            expected is List<*> -> {
                val got = actual as List<*>
                assertEquals("$path: size", expected.size, got.size)
                expected.indices.forEach { assertSameField("$path[$it]", expected[it], got[it]) }
            }
            expected is Double -> assertEquals(path, expected, actual as Double, 0.0)
            expected.javaClass.name.startsWith("com.example.kpkn.") -> {
                assertEquals("$path: type", expected.javaClass, actual.javaClass)
                val fields = instanceFields(expected.javaClass)
                assertTrue("$path: ${expected.javaClass.simpleName} has no fields to compare", fields.isNotEmpty())
                fields.forEach { assertSameField("$path.${it.name}", it.get(expected), it.get(actual)) }
            }
            else -> assertEquals(path, expected, actual)
        }
    }
}