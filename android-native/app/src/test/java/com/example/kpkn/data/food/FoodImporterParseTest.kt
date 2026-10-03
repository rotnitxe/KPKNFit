package com.example.kpkn.data.food

import com.example.kpkn.domain.nutrition.OffSearchFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * WP-S8: el análisis de los CSV embebidos es puro (sin base de datos, sin Android) y tiene UNA vía por línea OFF
 * ([FoodImporter.parseOffLine]). Las líneas OFF se arman con las columnas reales del TSV; USDA se lee de un extracto
 * mínimo de filas REALES (`src/test/resources/food_data/usda_extract`) a través de la misma función que usa la
 * importación ([FoodImporter.parseUsda]), de modo que también se prueba el orden "food.csv primero, filtro de ids
 * Foundation, y solo entonces los nutrientes".
 *
 * Ids del extracto (copiados tal cual de los CSV de `assets/food_data`; sin la fila de alias de la frutilla a propósito):
 * 746782 leche entera (energía 2048 > 2047 > 1008, azúcar solo en 1063), 748967 huevo, 331960 pechuga cocida, 330458 aceite
 * de coco ("1 tablespoon, liquid oil"), 321358 hummus ("2 tablespoon = 33,9 g"), 2346409 frutilla (sin alias ni porción);
 * 321505 sal (Foundation sin energía) y 2727569 pollo con piel (carbohidrato por diferencia negativo) quedan fuera; 319877 y
 * 335241 no son Foundation pero tienen nutrientes que el filtro debe saltar; 319874 y 319875 tampoco lo son.
 */
class FoodImporterParseTest {

    // ─── Líneas OFF ──────────────────────────────────────────────────────────────────────────────────

    /** Una línea del TSV de OFF Chile (160 columnas, sin cabecera) con las columnas que lee el importador. */
    private fun offLine(
        code: String = "7802920007595",
        name: String = "Leche Descremada",
        brand: String = "COLUN",
        kcal: String = "34",
        fat: String = "0.1",
        carbs: String = "4.7",
        sugar: String = "4.7",
        fiber: String = "",
        protein: String = "3.3",
        sodium: String = "0.04",
        columns: Int = 160,
    ): String {
        val cells = MutableList(columns) { "" }
        // Una línea más corta que el TSV real simplemente no llega a las últimas columnas.
        listOf(0 to code, 10 to name, 18 to brand, 89 to kcal, 92 to fat, 129 to carbs, 130 to sugar, 146 to fiber, 150 to protein, 156 to sodium)
            .forEach { (index, value) -> if (index < columns) cells[index] = value }
        return cells.joinToString("\t")
    }

    /** La leche entera de Colun tal como está en el corpus de búsqueda (código 0842626777542). */
    private fun colunMilk() = offLine(
        code = "0842626777542", name = "mi Leche Entera", brand = "Colun",
        kcal = "59", fat = "3.1", carbs = "4.6", sugar = "4.6", fiber = "0", protein = "3.1", sodium = "0.04",
    )

    private fun aliasesOf(json: String): List<String> = Json.decodeFromString(json)

    @Test
    fun `a Colun milk line becomes a catalog row with its brand and normalized fields filled`() {
        val milk = requireNotNull(FoodImporter.parseOffLine(colunMilk()))
        assertEquals("off_0842626777542", milk.foodId)
        assertEquals("0842626777542", milk.sourceRecordId)
        assertTrue("name: '${milk.name}'", milk.name.contains("leche", ignoreCase = true))
        assertEquals("Colun", milk.brand)
        assertEquals("colun", milk.normalizedBrand)
        assertEquals(FoodImporter.normalizeSearch(milk.name), milk.normalizedName)
        assertTrue(milk.normalizedName.isNotBlank())
        val aliases = aliasesOf(milk.aliasesJson)
        assertTrue("aliases: $aliases", aliases.containsAll(listOf(milk.normalizedName, "colun")))
        assertEquals(aliases.distinct(), aliases)
        assertEquals("OFF Chile", milk.source)
        assertEquals(80, milk.sourcePriority)
        assertEquals("PER_100G_AS_SOLD", milk.nutritionBasis)
        assertEquals("UNKNOWN", milk.foodState)
        assertEquals(FoodImporter.DATA_VERSION.toString(), milk.datasetVersion)
        assertEquals("[]", milk.qualityFlagsJson)
    }

    @Test
    fun `a Colun milk line of the committed search fixture is parsed by the same seam the corpus uses`() {
        val line = OffSearchFixture.lines().first { raw ->
            val cells = raw.split('\t')
            cells.getOrNull(18).orEmpty().equals("colun", ignoreCase = true) && cells.getOrNull(10).orEmpty().contains("leche", ignoreCase = true)
        }
        val milk = requireNotNull(FoodImporter.parseOffLine(line))
        assertEquals("colun", milk.normalizedBrand)
        assertTrue(milk.name, milk.name.contains("leche", ignoreCase = true))
        assertEquals(FoodImporter.normalizeSearch(milk.name), milk.normalizedName)
        assertTrue(milk.foodId, milk.foodId.startsWith("off_"))
        // The corpus rows ARE importer rows: OffSearchFixture delegates here instead of keeping its own copy of the rules.
        assertEquals(milk, OffSearchFixture.entityFromLine(line))
    }

    @Test
    fun `the declared macros and sodium are the ones stored, in grams and milligrams per 100 g`() {
        val milk = requireNotNull(FoodImporter.parseOffLine(colunMilk()))
        assertEquals(59.0, milk.calories, 1e-9)
        assertEquals(3.1, milk.protein, 1e-9)
        assertEquals(3.1, milk.fats, 1e-9)
        assertEquals(4.6, milk.carbs, 1e-9)
        assertEquals(4.6, milk.sugar, 1e-9)
        assertEquals(0.0, milk.fiber, 1e-9)
        // OFF declara el sodio en gramos: 0,04 g son 40 mg.
        assertEquals(40.0, milk.sodiumMg, 1e-6)
    }

    @Test
    fun `a line whose declared energy disagrees with its macros is rejected`() {
        // 500 kcal declaradas contra 4*3,3 + 9*0,1 + 4*4,7 = 32,9 kcal de macros.
        assertNull(FoodImporter.parseOffLine(offLine(kcal = "500")))
        assertNotNull(FoodImporter.parseOffLine(offLine()))
    }

    @Test
    fun `the energy tolerance is 50 percent of the macro energy`() {
        // Macros: 4*10 + 9*0 + 4*15 = 100 kcal.
        fun line(kcal: String) = offLine(kcal = kcal, protein = "10", fat = "0", carbs = "15", sugar = "0", sodium = "")
        assertNotNull("150 kcal is exactly 50 %", FoodImporter.parseOffLine(line("150")))
        assertNotNull("50 kcal is exactly 50 %", FoodImporter.parseOffLine(line("50")))
        assertNull("151 kcal is beyond 50 %", FoodImporter.parseOffLine(line("151")))
        assertNull("49 kcal is beyond 50 %", FoodImporter.parseOffLine(line("49")))
    }

    @Test
    fun `between 35 and 50 percent the row is kept but flagged instead of rejected`() {
        val flagged = requireNotNull(FoodImporter.parseOffLine(offLine(kcal = "140", protein = "10", fat = "0", carbs = "15", sugar = "0")))
        assertTrue(flagged.qualityFlagsJson, flagged.qualityFlagsJson.contains("ENERGY_MISMATCH"))
        val clean = requireNotNull(FoodImporter.parseOffLine(offLine(kcal = "110", protein = "10", fat = "0", carbs = "15", sugar = "0")))
        assertFalse(clean.qualityFlagsJson, clean.qualityFlagsJson.contains("ENERGY_MISMATCH"))
    }

    @Test
    fun `lines that cannot be a product are rejected`() {
        assertNull("too few columns", FoodImporter.parseOffLine(offLine(columns = 156)))
        assertNull("empty line", FoodImporter.parseOffLine(""))
        assertNull("blank code", FoodImporter.parseOffLine(offLine(code = "  ")))
        assertNull("blank name", FoodImporter.parseOffLine(offLine(name = " ")))
        assertNull("no energy", FoodImporter.parseOffLine(offLine(kcal = "")))
        assertNull("zero energy", FoodImporter.parseOffLine(offLine(kcal = "0")))
        assertNull("no macros", FoodImporter.parseOffLine(offLine(protein = "", fat = "", carbs = "")))
        assertNull("energy above the 1000 kcal bound reads as no energy", FoodImporter.parseOffLine(offLine(kcal = "1200")))
        assertNull("unreadable sodium", FoodImporter.parseOffLine(offLine(sodium = "n/a")))
        assertNull("sodium above 5 g", FoodImporter.parseOffLine(offLine(sodium = "6")))
    }

    @Test
    fun `a blank sodium is accepted and stored as zero`() {
        val row = requireNotNull(FoodImporter.parseOffLine(offLine(sodium = "")))
        assertEquals(0.0, row.sodiumMg, 1e-9)
    }

    @Test
    fun `parseOff keeps what parseOffLine accepts, in file order, and skips the rest`() = runBlocking {
        val first = colunMilk()
        val second = offLine(code = "7802920009070", name = "yogurt natural colun", brand = "colun", kcal = "49", fat = "0.3", carbs = "4.9", protein = "3.7")
        val text = listOf(first, "too\tshort", offLine(kcal = "500"), second, offLine(code = "")).joinToString("\n")
        val rows = FoodImporter.parseOff(textAssets("food_data/off_chile.csv" to text))
        assertEquals(listOf(FoodImporter.parseOffLine(first), FoodImporter.parseOffLine(second)), rows)
        assertEquals(listOf("off_0842626777542", "off_7802920009070"), rows.map { it.foodId })
    }

    @Test
    fun `parseOff reads a file whose lines end in CRLF like the shipped one may`() = runBlocking {
        val text = listOf(colunMilk(), offLine()).joinToString("\r\n") + "\r\n"
        val rows = FoodImporter.parseOff(textAssets("food_data/off_chile.csv" to text))
        assertEquals(2, rows.size)
        assertEquals(requireNotNull(FoodImporter.parseOffLine(colunMilk())), rows.first())
    }

    @Test
    fun `parseOff reports progress that never goes back and ends at one`() = runBlocking {
        val text = List(2_000) { offLine(code = "78029${10_000 + it}") }.joinToString("\n")
        val seen = mutableListOf<Float>()
        val rows = FoodImporter.parseOff(textAssets("food_data/off_chile.csv" to text)) { seen.add(it) }
        assertEquals(2_000, rows.size)
        assertTrue("progress: $seen", seen.size >= 5)
        assertTrue(seen.all { it in 0f..1f })
        assertEquals(seen.sorted(), seen)
        assertTrue("it starts well before the end: ${seen.first()}", seen.first() < 0.5f)
        assertEquals(1f, seen.last(), 0f)
    }

    @Test
    fun `parseOff stops at the next check after its coroutine is cancelled`() = runBlocking {
        val text = List(5_000) { offLine() }.joinToString("\n")
        val calls = AtomicInteger()
        val job = launch(Dispatchers.Default) {
            FoodImporter.parseOff(textAssets("food_data/off_chile.csv" to text)) {
                calls.incrementAndGet()
                cancel() // el análisis lo comprueba cada pocas líneas: el siguiente chequeo corta
            }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals("the parse must not reach its final report", 1, calls.get())
    }

    // ─── USDA: extracto real mínimo ──────────────────────────────────────────────────────────────────

    private val extractFoundationIds = listOf("usda_746782", "usda_748967", "usda_331960", "usda_330458", "usda_321358", "usda_2346409")

    /** Abre los assets de USDA desde el extracto (misma ruta lógica que `context.assets.open`, otro origen). */
    private fun extract(without: Set<String> = emptySet()): (String) -> InputStream = { path ->
        val name = path.substringAfterLast('/')
        if (name in without) throw FileNotFoundException(path)
        checkNotNull(FoodImporterParseTest::class.java.classLoader?.getResourceAsStream("food_data/usda_extract/$name")) {
            "falta el recurso del extracto: $name"
        }
    }

    private fun extractLines(name: String): List<String> =
        checkNotNull(FoodImporterParseTest::class.java.classLoader?.getResourceAsStream("food_data/usda_extract/$name"))
            .bufferedReader(Charsets.UTF_8).use { it.readLines() }.drop(1)

    /** Assets de texto en memoria por su ruta lógica; una ruta que no está falla como un asset ausente. */
    private fun textAssets(vararg assets: Pair<String, String>): (String) -> InputStream {
        val byPath = assets.toMap()
        return { path -> ByteArrayInputStream(byPath.getValue(path).toByteArray(Charsets.UTF_8)) }
    }

    @Test
    fun `the extract keeps only foundation foods with energy and plausible macros`() = runBlocking {
        val rows = FoodImporter.parseUsda(extract())
        assertEquals(extractFoundationIds, rows.map { it.foodId })
        rows.forEach { row ->
            assertEquals("USDA", row.source)
            assertEquals(FoodImporter.DATA_VERSION.toString(), row.datasetVersion)
            assertEquals(row.foodId.removePrefix("usda_"), row.sourceRecordId)
        }
        // Salt is Foundation but has no energy row; chicken with skin has energy but a negative carbohydrate by difference;
        // 319877 / 335241 are not Foundation although food_nutrient.csv holds mapped nutrients for them.
        listOf("usda_321505", "usda_2727569", "usda_319877", "usda_335241", "usda_319874", "usda_319875").forEach { id ->
            assertFalse("$id must not be imported", rows.any { it.foodId == id })
        }
    }

    @Test
    fun `only foundation_food rows of food_csv are read`() {
        val foods = FoodImporter.parseUsdaFoundationFoods(extractLines("food.csv").asSequence())
        assertEquals(listOf(746782, 748967, 331960, 321505, 330458, 321358, 2346409, 2727569), foods.map { it.fdcId })
        val milk = foods.first()
        assertEquals("Milk, whole, 3.25% milkfat, with added vitamin D", milk.description)
        assertEquals("1", milk.categoryId)
        assertTrue(FoodImporter.parseUsdaFoundationFoods(sequenceOf("", "1,foundation_food", "x,foundation_food,No id,1")).isEmpty())
    }

    @Test
    fun `the nutrient filter skips every id that is not in the set`() {
        val nutrientLines = extractLines("food_nutrient.csv")
        val everything = FoodImporter.parseUsdaNutrients(nutrientLines.asSequence(), 16)
        // The extract has mapped nutrient rows for non-foundation foods too: without a filter they would be kept.
        assertTrue(everything.keys.containsAll(listOf(319877, 335241)))
        assertEquals(10, everything.size)

        val foundationIds = FoodImporter.parseUsdaFoundationFoods(extractLines("food.csv").asSequence()).map { it.fdcId }.toSet()
        val filtered = FoodImporter.parseUsdaNutrients(nutrientLines.asSequence(), foundationIds.size, foundationIds)
        assertEquals(foundationIds, filtered.keys)
        assertFalse(filtered.containsKey(319877))
        assertFalse(filtered.containsKey(335241))
        // The ids that stay are parsed exactly as without the filter.
        foundationIds.forEach { id ->
            assertEquals(everything.getValue(id).energy, filtered.getValue(id).energy, 0f)
            assertEquals(everything.getValue(id).sugar, filtered.getValue(id).sugar, 0f)
        }
        assertEquals(setOf(746782), FoodImporter.parseUsdaNutrients(nutrientLines.asSequence(), 4, setOf(746782)).keys)
        assertTrue(FoodImporter.parseUsdaNutrients(nutrientLines.asSequence(), 4, emptySet()).isEmpty())
    }

    @Test
    fun `the milk takes its energy by priority and its sugar from the 1063 id`() = runBlocking {
        val milk = FoodImporter.parseUsda(extract()).first { it.foodId == "usda_746782" }
        // 2048 = 60 wins over 2047 = 61 and 1008 = 60.0, whatever the row order.
        assertEquals(60.0, milk.calories, 1e-6)
        assertEquals(4.81f.toDouble(), milk.sugar, 1e-9)
        assertEquals(3.2f.toDouble(), milk.fats, 1e-9)
    }

    @Test
    fun `rows carry the curated Spanish name, the category and the one unit portion`() = runBlocking {
        val rows = FoodImporter.parseUsda(extract()).associateBy { it.foodId }
        assertEquals("Leche entera", rows.getValue("usda_746782").name)
        assertEquals("Dairy and Egg Products", rows.getValue("usda_746782").category)
        assertEquals(249.0, rows.getValue("usda_746782").portionGrams ?: 0.0, 1e-9)
        assertEquals("cup", rows.getValue("usda_746782").portionUnit)
        assertEquals("Fats and Oils", rows.getValue("usda_330458").category)
        assertEquals("tablespoon, liquid oil", rows.getValue("usda_330458").portionUnit)
        // "2 tablespoon = 33,9 g" is 16,95 g for ONE tablespoon.
        assertEquals(16.95, rows.getValue("usda_321358").portionGrams ?: 0.0, 1e-9)
        assertEquals("egg, whole without shell", rows.getValue("usda_748967").portionUnit)
        assertEquals("Poultry Products", rows.getValue("usda_331960").category)
        val aliases = aliasesOf(rows.getValue("usda_746782").aliasesJson)
        assertTrue(aliases.toString(), aliases.containsAll(listOf("leche entera", "milk whole 3 25 milkfat with added vitamin d", "leche completa")))
    }

    @Test
    fun `a foundation food without a curated alias or a portion keeps its English description`() = runBlocking {
        val strawberries = FoodImporter.parseUsda(extract()).first { it.foodId == "usda_2346409" }
        assertEquals("Strawberries, raw", strawberries.name)
        assertEquals("strawberries raw", strawberries.normalizedName)
        assertEquals("Fruits and Fruit Juices", strawberries.category)
        assertNull(strawberries.portionGrams)
        assertNull(strawberries.portionUnit)
        assertEquals(4.862f.toDouble(), strawberries.sugar, 1e-9)
    }

    @Test
    fun `a missing auxiliary table degrades the rows without stopping the import`() = runBlocking {
        val full = FoodImporter.parseUsda(extract()).associateBy { it.foodId }
        val noAliases = FoodImporter.parseUsda(extract(without = setOf("usda_es_aliases.csv"))).associateBy { it.foodId }
        assertEquals(extractFoundationIds.toSet(), noAliases.keys)
        assertEquals("Milk, whole, 3.25% milkfat, with added vitamin D", noAliases.getValue("usda_746782").name)

        val noPortions = FoodImporter.parseUsda(extract(without = setOf("food_portion.csv"))).associateBy { it.foodId }
        assertTrue(noPortions.values.all { it.portionGrams == null })
        assertEquals(full.getValue("usda_746782").name, noPortions.getValue("usda_746782").name)

        val noUnits = FoodImporter.parseUsda(extract(without = setOf("measure_unit.csv"))).associateBy { it.foodId }
        assertEquals("the rows stay even when the units cannot be named", extractFoundationIds.toSet(), noUnits.keys)
        assertNull(noUnits.getValue("usda_746782").portionUnit)

        val noCategories = FoodImporter.parseUsda(extract(without = setOf("food_category.csv"))).associateBy { it.foodId }
        assertTrue(noCategories.values.all { it.category == null })
        assertEquals(full.getValue("usda_746782").calories, noCategories.getValue("usda_746782").calories, 0.0)
    }

    @Test
    fun `a missing required table fails the parse so the previous catalog is kept`() = runBlocking {
        listOf("food.csv", "food_nutrient.csv").forEach { name ->
            try {
                FoodImporter.parseUsda(extract(without = setOf(name)))
                fail("$name is required")
            } catch (expected: FileNotFoundException) {
                assertTrue(expected.message.orEmpty().endsWith(name))
            }
        }
    }

    /** El mismo asset con todos los fin de línea en CRLF, como lo deja un checkout de Windows del repositorio. */
    private fun withCrlf(assets: (String) -> InputStream): (String) -> InputStream = { path ->
        val text = assets(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        ByteArrayInputStream(text.replace("\r\n", "\n").replace("\n", "\r\n").toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `assets checked out with CRLF line endings import the same rows as with LF`() = runBlocking {
        val lf = FoodImporter.parseUsda(extract())
        val crlf = FoodImporter.parseUsda(withCrlf(extract()))
        assertEquals(extractFoundationIds, lf.map { it.foodId })
        assertEquals(lf, crlf)
    }

    @Test
    fun `food_csv is read before food_nutrient_csv so the foundation ids can filter the nutrients`() = runBlocking {
        val opened = mutableListOf<String>()
        val assets = extract()
        FoodImporter.parseUsda({ path -> opened.add(path); assets(path) })
        val food = opened.indexOf("food_data/food.csv")
        val nutrients = opened.indexOf("food_data/food_nutrient.csv")
        assertTrue("opened: $opened", food >= 0 && food < nutrients)
        assertEquals("each asset is opened once", opened.size, opened.toSet().size)
        assertEquals(FoodImporter.IMPORT_ASSETS.filterNot { it.endsWith("off_chile.csv") }.toSet(), opened.toSet())
    }

    @Test
    fun `parseOff opens only the OFF asset the import declares`() = runBlocking {
        val opened = mutableListOf<String>()
        FoodImporter.parseOff({ path -> opened.add(path); ByteArrayInputStream(offLine().toByteArray()) })
        assertEquals(FoodImporter.IMPORT_ASSETS.filter { it.endsWith("off_chile.csv") }, opened)
    }

    @Test
    fun `parseUsda reports progress that never goes back and ends at one`() = runBlocking {
        val seen = mutableListOf<Float>()
        FoodImporter.parseUsda(extract()) { seen.add(it) }
        assertTrue("progress: $seen", seen.size >= 3)
        assertTrue(seen.all { it in 0f..1f })
        assertEquals(seen.sorted(), seen)
        assertEquals(1f, seen.last(), 0f)
    }

    // ─── Assets reales: la misma ruta que usa la app ──────────────────────────────────────────────────

    /** Abre los CSV embebidos desde `src/main/assets` (el directorio de trabajo de las pruebas es el módulo `app`). */
    private fun realAssets(): (String) -> InputStream = { path -> java.io.File(UsdaTestAssets.dir.parentFile, path).inputStream() }

    @Test
    fun `the foundation filter changes the memory the import needs, never the rows it produces`() = runBlocking {
        val viaImporter = FoodImporter.parseUsda(realAssets())

        // The assembly the importer did before WP-S8: every food that has a mapped nutrient goes into the map first.
        val categories = FoodImporter.parseFoodCategories(UsdaTestAssets.dataLines("food_category.csv").asSequence())
        val units = FoodImporter.parseMeasureUnits(UsdaTestAssets.dataLines("measure_unit.csv").asSequence())
        val portions = FoodImporter.parseUsdaPortions(UsdaTestAssets.dataLines("food_portion.csv").asSequence(), units)
        val aliases = FoodImporter.parseUsdaAliases(UsdaTestAssets.dataLines("usda_es_aliases.csv").asSequence())
        val nutrients = FoodImporter.parseUsdaNutrients(UsdaTestAssets.dataLines("food_nutrient.csv").asSequence())
        val unfiltered = UsdaTestAssets.foundation.mapNotNull { row ->
            val food = nutrients[row.fdcId] ?: return@mapNotNull null
            FoodImporter.usdaEntityOrNull(
                fdcId = row.fdcId,
                description = row.description,
                category = FoodImporter.usdaCategory(row.categoryId, categories),
                nutrients = food,
                portion = portions[row.fdcId],
                alias = aliases[row.fdcId],
            )
        }

        assertEquals(unfiltered, viaImporter)
        assertTrue("USDA rows: ${viaImporter.size}", viaImporter.size >= 350)
        assertTrue("the unfiltered map held far more foods than the filtered one needs: ${nutrients.size}", nutrients.size > 10 * viaImporter.size)
        assertEquals(viaImporter.size, viaImporter.map { it.foodId }.toSet().size)
    }

    @Test
    fun `the shipped OFF file imports thousands of rows through parseOff and every one comes from parseOffLine`() = runBlocking {
        val rows = FoodImporter.parseOff(realAssets())

        assertTrue("OFF rows: ${rows.size}", rows.size >= 4_000)
        assertTrue(rows.all { it.foodId.startsWith("off_") && it.source == "OFF Chile" && it.sourceRecordId == it.foodId.removePrefix("off_") })
        // The first rows of the file are rebuilt line by line through the single seam.
        val firstLines = java.io.File(UsdaTestAssets.dir, "off_chile.csv").useLines { lines -> lines.take(300).toList() }
        val expectedFirst = firstLines.mapNotNull(FoodImporter::parseOffLine)
        assertTrue(expectedFirst.isNotEmpty())
        assertEquals(expectedFirst, rows.take(expectedFirst.size))
    }
}
