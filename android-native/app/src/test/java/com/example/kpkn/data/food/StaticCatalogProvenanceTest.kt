package com.example.kpkn.data.food

import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.FoodIdentity
import com.example.kpkn.domain.nutrition.FoodIndex
import com.example.kpkn.domain.nutrition.HouseholdPortions
import com.example.kpkn.domain.nutrition.TextKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-S10 (B9): every row of the static catalog says where it comes from. The audit's complaint was a catalog whose rows state no
 * source, no release and no basis; [withCuratedProvenance] fills in what a row leaves unsaid and leaves alone what it declares (the
 * USDA, recipe and per-100 g rows of WP-N5, WP-S6 and WP-D1). StaticCatalogContentTest covers the rows WP-D1 added.
 */
class StaticCatalogProvenanceTest {

    private val all: List<FoodItem> = GENERIC_FOODS + CHILEAN_FOODS

    private val knownBases = setOf("PER_SERVING", "PER_100G_AS_SOLD", "PER_100G_COOKED", "PER_100G_RAW", "PER_100G_PREPARED")

    private fun isPer100(food: FoodItem) = food.servingSize == 100.0 && food.unit.lowercase() in setOf("g", "ml")

    private fun basisOf(serving: Double, unit: String, declared: String? = null): String {
        val row = if (declared == null) {
            FoodItem(id = "x", servingSize = serving, unit = unit)
        } else {
            FoodItem(id = "x", servingSize = serving, unit = unit, nutritionBasis = declared)
        }
        return listOf(row).withCuratedProvenance().single().nutritionBasis
    }

    // 1. Every row says where it comes from

    @Test
    fun `every static food declares a source a dataset version and a nutrition basis`() {
        assertTrue(all.size >= 200)
        all.forEach { food ->
            val label = "${food.id} ${food.name}"
            assertFalse("$label: source", food.source.isNullOrBlank())
            assertFalse("$label: datasetVersion", food.datasetVersion.isNullOrBlank())
            assertTrue("$label: nutritionBasis '${food.nutritionBasis}'", food.nutritionBasis in knownBases)
        }
    }

    @Test
    fun `a row that says nothing is curated by KPKN and a row that says something keeps it`() {
        val plain = checkNotNull(findStaticFoodById("gen001"))
        assertEquals("KPKN curated", plain.source)
        assertEquals("static-2026-10", plain.datasetVersion)
        assertNull("the label is a source, not a record id", plain.sourceRecordId)

        // Declared provenance survives untouched: the rows of WP-N5, WP-S6 and WP-D1.
        val oil = checkNotNull(findStaticFoodById("gen015"))
        assertTrue(oil.source.orEmpty().startsWith("USDA SR Legacy"))
        assertEquals("171413", oil.sourceRecordId)
        assertEquals("PER_100G_AS_SOLD", oil.nutritionBasis)
        val rice = checkNotNull(findStaticFoodById("gen006"))
        assertTrue(rice.source.orEmpty().startsWith("KPKN Curated ("))
        assertEquals("169704", rice.sourceRecordId)
        assertEquals("PER_100G_COOKED", rice.nutritionBasis)
        assertEquals("RECIPE_ESTIMATE", checkNotNull(findStaticFoodById("gen183")).source)
        // The release is shared by every row of the catalog: none declares its own.
        assertEquals(1, all.map { it.datasetVersion }.distinct().size)
    }

    // 2. The helper

    @Test
    fun `the helper fills a blank source and release and never overrides a declared one`() {
        val rows = listOf(
            FoodItem(id = "t1", name = "Sin nada"),
            FoodItem(id = "t2", name = "En blanco", source = "  ", datasetVersion = ""),
            FoodItem(id = "t3", name = "Declarada", source = "USDA SR Legacy", sourceRecordId = "171413", datasetVersion = "2026-08"),
        ).withCuratedProvenance()

        assertEquals(listOf("KPKN curated", "KPKN curated", "USDA SR Legacy"), rows.map { it.source })
        assertEquals(listOf("static-2026-10", "static-2026-10", "2026-08"), rows.map { it.datasetVersion })
        assertEquals("171413", rows[2].sourceRecordId)
        assertEquals(STATIC_CATALOG_SOURCE, rows[0].source)
    }

    @Test
    fun `the helper writes the basis only on a row that said none`() {
        // FoodItem defaults to PER_SERVING and no row of the catalog writes that default on purpose: the helper reads it as "said nothing".
        assertEquals("PER_SERVING", FoodItem().nutritionBasis)
        // The 100 g or 100 ml its table was written for: the macros are per 100 g.
        assertEquals("PER_100G_AS_SOLD", basisOf(100.0, "g"))
        assertEquals("PER_100G_AS_SOLD", basisOf(100.0, "ml"))
        assertEquals("PER_100G_AS_SOLD", basisOf(100.0, "G"))
        // A serving of any other size or unit is a real serving.
        assertEquals("PER_SERVING", basisOf(180.0, "u"))
        assertEquals("PER_SERVING", basisOf(250.0, "g"))
        assertEquals("PER_SERVING", basisOf(100.0, "u"))
        assertEquals("PER_SERVING", basisOf(99.0, "g"))
        // A declaration is never overridden, whatever the serving.
        assertEquals("PER_100G_COOKED", basisOf(180.0, "u", "PER_100G_COOKED"))
        assertEquals("PER_100G_RAW", basisOf(100.0, "g", "PER_100G_RAW"))
        assertEquals("PER_100G_AS_SOLD", basisOf(350.0, "ml", "PER_100G_AS_SOLD"))
        // A blank basis (an old JSON) is not a declaration either.
        assertEquals("PER_100G_AS_SOLD", basisOf(100.0, "g", ""))
    }

    // 3. The basis of the real rows

    @Test
    fun `no row is a per serving row of exactly 100 g or 100 ml`() {
        val unfilled = all.filter { it.nutritionBasis == "PER_SERVING" && isPer100(it) }
        assertTrue("rows measured per 100 g that still say PER_SERVING: ${unfilled.map { it.id }}", unfilled.isEmpty())
    }

    @Test
    fun `a per 100 g basis is declared only on a row measured in g ml or units`() {
        // The "u" rows (Mandarina 90 u, Pan pita 60 u) give the weight in grams of ONE unit; their macros are still per 100 g.
        all.filter { it.nutritionBasis.startsWith("PER_100G") }.forEach { food ->
            assertTrue("${food.id} ${food.name}: unit '${food.unit}'", food.unit.lowercase() in setOf("g", "ml", "u"))
            assertTrue("${food.id}: serving ${food.servingSize}", food.servingSize.isFinite() && food.servingSize > 0.0)
        }
    }

    @Test
    fun `a per 100 g row that is eaten by the unit declares its unit`() {
        // Pan Blanco: its macros are per 100 g, so its 100 g is a denominator and no portion; a "pan" weighs 100 g and the row says so
        // ("2 panes" is 200 g in EverydayMealCorpusTest).
        val bread = checkNotNull(findStaticFoodById("gen019"))
        assertEquals("PER_100G_AS_SOLD", bread.nutritionBasis)
        assertEquals(100.0, bread.portionGrams ?: 0.0, 0.0)
        assertEquals(100.0, HouseholdPortions.unitGrams(bread, "pan"), 0.0)
    }

    // 4. Identity

    @Test
    fun `no id repeats`() {
        val repeated = all.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("ids that repeat: $repeated", repeated.isEmpty())
    }

    @Test
    fun `no name repeats with the same state except the known twin`() {
        val clash = all.groupBy { TextKeys.normalize(it.name) to FoodIdentity.stateFor(it) }
            .filterValues { it.size > 1 }
            .map { (key, rows) -> key.first to rows.map { it.id }.sorted() }
            .toMap()
        // Pan Integral: gen133 is the slice of the staple ontology (50 g, "pan integral") and gen020 the 100 g row of the first catalog.
        // Both stay on purpose: FoodSearchRankerTest.collapseDuplicates and SearchGoldenCorpusTest cover the pair.
        assertEquals(mapOf("pan integral" to listOf("gen020", "gen133")), clash)
    }

    @Test
    fun `Charquican is one row and the merged id still answers`() {
        assertEquals(1, all.count { TextKeys.normalize(it.name) == "charquican" })
        assertEquals("cl015", staticFoodForAlias("charquicán")?.id)
        assertEquals("cl015", findStaticFoodById("cl039")?.id)
        assertEquals("cl015", resolveLegacyFoodId("cl039"))
    }

    @Test
    fun `the redirects of deleted ids point at live rows and shadow no live id`() {
        val live = all.map { it.id }.toSet()
        assertTrue(LEGACY_FOOD_ID_REDIRECTS.isNotEmpty())
        LEGACY_FOOD_ID_REDIRECTS.forEach { (old, current) ->
            assertFalse("$old is still a live id", old in live)
            assertTrue("$old -> $current: the target is not a live id", current in live)
            assertFalse("$old -> $current: a redirect chain", current in LEGACY_FOOD_ID_REDIRECTS)
            assertEquals(current, resolveLegacyFoodId(old))
            assertEquals(current, findStaticFoodById(old)?.id)
        }
    }

    // 5. The resolver

    @Test
    fun `the resolver index keeps calling a row without provenance of its own LOCAL`() {
        val index = FoodIndex()
        index.build(globalFoods = emptyList(), staticFoods = all, staticAliases = emptyMap())

        // TagResolution trusts "LOCAL" as "a row of the static catalog": the label of WP-S10 must not change that.
        assertEquals("LOCAL", index.getFood("gen001")?.source)
        assertEquals("LOCAL", index.getFood("cl001")?.source)
        // A row that has a provenance of its own keeps showing it.
        assertEquals(findStaticFoodById("gen015")?.source, index.getFood("gen015")?.source)
        assertTrue(index.getFood("gen006")?.source.orEmpty().startsWith("KPKN Curated ("))
    }
}
