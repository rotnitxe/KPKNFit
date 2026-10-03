package com.example.kpkn.domain.nutrition

import com.example.kpkn.domain.nutrition.FoodCombinationParser.Accompaniment
import com.example.kpkn.domain.nutrition.FoodCombinationParser.Role
import com.example.kpkn.domain.nutrition.SubjectivePortionEngine.FoodDensityCategory.GRAIN
import com.example.kpkn.domain.nutrition.SubjectivePortionEngine.FoodDensityCategory.NUTS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * WP-N13: [FoodKnowledge] serves the Kotlin default until a snapshot is installed, and every consumer reads what is in force at the
 * moment it uses a table. Each section is checked the same way: use the pipeline first (so that whatever it derived is built), install a
 * snapshot that differs in one entry, see the new entry at work, reset, see the old behavior again. The singleton is global: every test
 * starts and ends on the default.
 */
class FoodKnowledgeInstallTest {

    private val default = FoodKnowledge.defaultSnapshot()

    @Before
    fun startFromTheDefault() = FoodKnowledge.reset()

    @After
    fun restoreTheDefault() = FoodKnowledge.reset()

    @Test
    fun `nothing installed serves the Kotlin default`() {
        assertSame(default, FoodKnowledge.current())
        assertSame(default, FoodKnowledge.defaultSnapshot())
    }

    @Test
    fun `install makes the snapshot current and reset brings the default back`() {
        val changed = default.copy(typos = default.typos + ("pyollo" to "pollo"))
        FoodKnowledge.install(changed)
        assertSame(changed, FoodKnowledge.current())
        assertSame("the default is still reachable", default, FoodKnowledge.defaultSnapshot())
        FoodKnowledge.reset()
        assertSame(default, FoodKnowledge.current())
    }

    @Test
    fun `a version this build does not read is not installed`() {
        assertThrows(IllegalArgumentException::class.java) { FoodKnowledge.install(default.copy(version = FOOD_KNOWLEDGE_VERSION + 1)) }
        assertSame(default, FoodKnowledge.current())
    }

    @Test
    fun `installing the asset the loader reads changes nothing, because it equals the default`() {
        val asset = parseFoodKnowledge(File("src/main/assets/food_data/food_knowledge_v1.json").readText(Charsets.UTF_8))
        val entities = ProtectedPhrases.PROTECTED_ENTITIES
        FoodKnowledge.install(asset)
        assertSame("an equal snapshot leaves the one in force in place", default, FoodKnowledge.current())
        assertSame("and everything derived from it", entities, ProtectedPhrases.PROTECTED_ENTITIES)
    }

    @Test
    fun `a derived value is rebuilt only when an install changes the snapshot`() {
        val builds = AtomicInteger()
        val cache = KnowledgeCache { builds.incrementAndGet() }
        assertEquals(1, cache.get())
        assertEquals("a hit builds nothing", 1, cache.get())
        FoodKnowledge.install(default.copy())
        assertEquals("an equal snapshot is not a change", 1, cache.get())
        FoodKnowledge.install(default.copy(typos = default.typos + ("pyollo" to "pollo")))
        assertEquals(2, cache.get())
        assertEquals(2, cache.get())
        FoodKnowledge.reset()
        assertEquals("the default is another snapshot again", 3, cache.get())
    }

    // ─── Section by section ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an installed typo is applied by the normalizer and gone after reset`() {
        assertEquals("2 pyollo", TextNormalizer.normalize("2 pyollo"))
        FoodKnowledge.install(default.copy(typos = default.typos + ("pyollo" to "pollo")))
        assertEquals("2 pollo", TextNormalizer.normalize("2 pyollo"))
        FoodKnowledge.reset()
        assertEquals("2 pyollo", TextNormalizer.normalize("2 pyollo"))
    }

    @Test
    fun `an installed synonym is applied by the normalizer`() {
        assertEquals("2 calabazon", TextNormalizer.normalize("2 calabazon"))
        FoodKnowledge.install(default.copy(synonyms = default.synonyms + ("calabazon" to "zapallote")))
        assertEquals("2 zapallote", TextNormalizer.normalize("2 calabazon"))
    }

    @Test
    fun `an installed protected phrase is kept whole by every pass that reads the lists`() {
        assertFalse(ProtectedPhrases.isEntity("pan con chancho"))
        val lists = default.protectedPhrases
        FoodKnowledge.install(default.copy(protectedPhrases = lists.copy(entityLiterals = lists.entityLiterals + "pan con chancho")))
        assertTrue(ProtectedPhrases.isEntity("Pan con chancho"))
        assertTrue("PROTECTED_ENTITIES", "pan con chancho" in ProtectedPhrases.PROTECTED_ENTITIES)
        assertTrue("lexiconEntries", "pan con chancho" in ProtectedPhrases.lexiconEntries)
        assertEquals("1 <0> y un te", ProtectedPhrases.mask("1 pan con chancho y un te") { "<$it>" }.text)
        // The splitter of the parser reads the same list: the dish stays one mention.
        assertEquals(listOf("pan con chancho"), parseMealDescription("pan con chancho").items.map { it.tag })
        FoodKnowledge.reset()
        assertFalse(ProtectedPhrases.isEntity("pan con chancho"))
        assertEquals("1 pan con chancho y un te", ProtectedPhrases.mask("1 pan con chancho y un te") { "<$it>" }.text)
    }

    @Test
    fun `an installed flavour and an installed compound name reach the derived lists`() {
        val lists = default.protectedPhrases
        val extended = lists.copy(flavors = lists.flavors + "zapallo", maizeProducts = lists.maizeProducts + "empanada")
        FoodKnowledge.install(default.copy(protectedPhrases = extended))
        assertTrue("vainilla y zapallo" in ProtectedPhrases.FLAVOR_PAIRS)
        assertTrue("zapallo y vainilla" in ProtectedPhrases.FLAVOR_PAIRS)
        assertTrue("empanada de maíz" in ProtectedPhrases.COMPOUND_NAMES)
        assertEquals("<0>", ProtectedPhrases.mask("helado de vainilla y zapallo") { "<$it>" }.text)
        FoodKnowledge.reset()
        assertFalse("vainilla y zapallo" in ProtectedPhrases.FLAVOR_PAIRS)
        assertFalse("empanada de maíz" in ProtectedPhrases.COMPOUND_NAMES)
    }

    @Test
    fun `installed piece weights, countable markers and family defaults reach the household portions`() {
        assertNull(HouseholdPortions.unitWeightByToken("zapallito"))
        assertFalse(HouseholdPortions.isCountable(null, "chancho"))
        assertEquals(200.0, HouseholdPortions.defaultGrams(null, "leche"), 0.0)
        val units = default.householdUnits
        FoodKnowledge.install(
            default.copy(
                householdUnits = units.copy(
                    unitGramsByToken = units.unitGramsByToken + ("zapallito" to 90.0),
                    countableNameMarkers = units.countableNameMarkers + "chancho",
                    familyDefaultGrams = units.familyDefaultGrams + ("leche" to 123.0),
                    notAWholePiece = units.notAWholePiece + "relleno",
                ),
            ),
        )
        assertEquals(90.0, requireNotNull(HouseholdPortions.unitWeightByToken("zapallito")), 0.0)
        assertNull("a qualifier that is not a whole piece", HouseholdPortions.unitWeightByToken("zapallito relleno"))
        assertTrue(HouseholdPortions.isCountable(null, "chancho"))
        assertEquals(123.0, HouseholdPortions.defaultGrams(null, "leche"), 0.0)
        FoodKnowledge.reset()
        assertNull(HouseholdPortions.unitWeightByToken("zapallito"))
        assertEquals(200.0, HouseholdPortions.defaultGrams(null, "leche"), 0.0)
    }

    @Test
    fun `installed container content, food classes and liquid containers reach the portion engine`() {
        fun grams(text: String) = requireNotNull(SubjectivePortionEngine.resolve(text)).grams
        assertEquals(180.0, grams("una lata de sidra"), 0.0)
        assertEquals(180.0, grams("una lata de leche"), 0.0)
        val containers = default.containers
        val lata = containers.contentByContainer.getValue("lata")
        FoodKnowledge.install(
            default.copy(
                containers = containers.copy(
                    contentByContainer = containers.contentByContainer + ("lata" to (lata + ("sidra" to 275.0))),
                    foodClasses = containers.foodClasses + ContainerFoodClass("sidra", listOf("sidra", "sidras")),
                    liquidContainers = containers.liquidContainers + "lata",
                ),
            ),
        )
        assertEquals("a class and its row", 275.0, grams("una lata de sidra"), 0.0)
        assertEquals("a liquid container weighs its volume by the density of the food", 180.0 * 1.03, grams("una lata de leche"), 1e-9)
        FoodKnowledge.reset()
        assertEquals(180.0, grams("una lata de sidra"), 0.0)
        assertEquals(180.0, grams("una lata de leche"), 0.0)
    }

    @Test
    fun `an installed container word names a container the engine had no word for`() {
        assertTrue("no word for a tarro yet", SubjectivePortionEngine.resolve("un tarro de miel")?.source != "container:tarro")
        val containers = default.containers
        FoodKnowledge.install(
            default.copy(
                containers = containers.copy(
                    words = listOf(
                        ContainerWord("tarro", """(?:un|una)\s+tarros?""", 1.0),
                        ContainerWord("tarro", """medio\s+tarro""", 0.5),
                    ) + containers.words,
                    contentByContainer = containers.contentByContainer + ("tarro" to mapOf(ContainersKnowledge.DEFAULT to 400.0)),
                ),
            ),
        )
        val tarro = requireNotNull(SubjectivePortionEngine.resolve("un tarro de miel"))
        assertEquals("container:tarro", tarro.source)
        assertEquals(400.0, tarro.grams, 0.0)
        val half = requireNotNull(SubjectivePortionEngine.resolve("medio tarro de miel"))
        assertEquals("a fraction is a share of one container", 200.0, half.grams, 0.0)
        val lata = requireNotNull(SubjectivePortionEngine.resolve("una lata de arroz"))
        assertEquals("the words that were there still work", 180.0, lata.grams, 0.0)
        FoodKnowledge.reset()
        assertTrue(SubjectivePortionEngine.resolve("un tarro de miel")?.source != "container:tarro")
    }

    @Test
    fun `installed densities and detection rules reach the portion engine`() {
        val fat = SubjectivePortionEngine.FoodDensityCategory.FAT
        assertEquals(0.9, fat.densityGPerMl, 0.0)
        assertEquals(90.0, SubjectivePortionEngine.massFromVolumeMl(100.0, "aceite"), 1e-9)
        assertEquals(SubjectivePortionEngine.FoodDensityCategory.MIXED, SubjectivePortionEngine.detectDensityCategory("chancho asado"))
        val densities = default.densities
        FoodKnowledge.install(
            default.copy(
                densities = densities.copy(
                    gramsPerMl = densities.gramsPerMl + ("FAT" to 0.95),
                    rules = listOf(DensityRule("NUTS", listOf("chancho"), emptyList())) + densities.rules,
                    fallbackCategory = "GRAIN",
                ),
            ),
        )
        assertEquals(0.95, fat.densityGPerMl, 0.0)
        assertEquals(95.0, SubjectivePortionEngine.massFromVolumeMl(100.0, "aceite"), 1e-9)
        assertEquals("a rule at the front wins", NUTS, SubjectivePortionEngine.detectDensityCategory("chancho asado"))
        assertEquals("the fallback is data too", GRAIN, SubjectivePortionEngine.detectDensityCategory("algo desconocido"))
        FoodKnowledge.reset()
        assertEquals(0.9, fat.densityGPerMl, 0.0)
        assertEquals(SubjectivePortionEngine.FoodDensityCategory.MIXED, SubjectivePortionEngine.detectDensityCategory("chancho asado"))
    }

    @Test
    fun `installed word lists of a density rule match whole words only`() {
        val densities = default.densities
        FoodKnowledge.install(
            default.copy(densities = densities.copy(rules = listOf(DensityRule("NUTS", emptyList(), listOf("mani"))) + densities.rules)),
        )
        assertEquals(NUTS, SubjectivePortionEngine.detectDensityCategory("un mani"))
        assertNotEquals("'mani' inside 'manilla' is not the word", NUTS, SubjectivePortionEngine.detectDensityCategory("manilla"))
    }

    @Test
    fun `installed utensil defaults are what the logger shows`() {
        assertEquals(240.0, SubjectivePortionEngine.UTENSIL_DEFAULTS.getValue("taza"), 0.0)
        val edited = default.utensils.defaultMl + ("taza" to 200.0) + ("tarro" to 350.0)
        FoodKnowledge.install(default.copy(utensils = UtensilsKnowledge(edited)))
        assertEquals(200.0, SubjectivePortionEngine.UTENSIL_DEFAULTS.getValue("taza"), 0.0)
        assertNotNull(SubjectivePortionEngine.UTENSIL_DEFAULTS["tarro"])
        FoodKnowledge.reset()
        assertEquals(240.0, SubjectivePortionEngine.UTENSIL_DEFAULTS.getValue("taza"), 0.0)
        assertNull(SubjectivePortionEngine.UTENSIL_DEFAULTS["tarro"])
    }

    // ─── Dish compositions ──────────────────────────────────────────────────────────────────────────────────────────

    private val ostras = DishComposition(
        "arroz con ostras",
        listOf(DishComponent("arroz", 0.7, Role.STARCH), DishComponent("ostras", 0.3, Role.TOPPING)),
    )

    private fun withDishes(dishes: List<DishComposition>): FoodKnowledgeSnapshot =
        default.copy(dishCompositions = default.dishCompositions.copy(dishes = dishes))

    @Test
    fun `an installed dish is read by the combination parser and gone after reset`() {
        assertFalse("not a known dish yet", FoodCombinationParser.parse("arroz con ostras").isKnownDish)
        assertEquals(listOf("arroz", "ostras"), FoodCombinationParser.splitFoods("arroz con ostras"))
        FoodKnowledge.install(withDishes(default.dishCompositions.dishes + ostras))
        val known = FoodCombinationParser.parse("arroz con ostras")
        assertTrue(known.isKnownDish)
        assertEquals("arroz con ostras", known.dishName)
        assertEquals("arroz", known.baseFood)
        assertEquals(0.7, known.baseProportion, 0.0)
        assertEquals(listOf(Accompaniment("ostras", 0.3, Role.TOPPING)), known.accompaniments)
        assertEquals(0.95, known.confidence, 0.0)
        assertEquals("the splitter keeps the dish whole", listOf("arroz con ostras"), FoodCombinationParser.splitFoods("arroz con ostras"))
        assertEquals("the dishes that were there still work", "arroz con pollo", FoodCombinationParser.parse("arroz con pollo").dishName)
        FoodKnowledge.reset()
        assertFalse(FoodCombinationParser.parse("arroz con ostras").isKnownDish)
        assertEquals(listOf("arroz", "ostras"), FoodCombinationParser.splitFoods("arroz con ostras"))
    }

    @Test
    fun `an installed table that changes the proportions of a dish or drops it is what the parser follows`() {
        val pollo = default.dishCompositions.dishes.single { it.name == "arroz con pollo" }
        assertEquals(0.5, FoodCombinationParser.parse("arroz con pollo").baseProportion, 0.0)
        val reshaped = DishComposition(
            pollo.name,
            listOf(DishComponent("arroz", 0.65, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING)),
        )
        FoodKnowledge.install(withDishes(default.dishCompositions.dishes.map { if (it.name == pollo.name) reshaped else it }))
        val changed = FoodCombinationParser.parse("arroz con pollo")
        assertEquals(0.65, changed.baseProportion, 0.0)
        assertEquals(0.35, changed.accompaniments.single().proportion, 0.0)
        FoodKnowledge.install(withDishes(default.dishCompositions.dishes.filterNot { it.name == pollo.name }))
        val dropped = FoodCombinationParser.parse("arroz con pollo")
        assertFalse("a dish the installed knowledge does not list is not known", dropped.isKnownDish)
        assertNull(dropped.dishName)
        FoodKnowledge.reset()
        assertEquals(0.5, FoodCombinationParser.parse("arroz con pollo").baseProportion, 0.0)
        assertEquals("arroz con pollo", FoodCombinationParser.parse("arroz con pollo").dishName)
    }

    @Test
    fun `the order of the installed dishes decides a tie between two names of the same length`() {
        // "pan con palta" and "pan con jamon" are 13 letters each and the text holds both: the one listed first wins.
        val text = "pan con palta y pan con jamon"
        assertEquals("pan con palta", FoodCombinationParser.parse(text).dishName)
        FoodKnowledge.install(withDishes(default.dishCompositions.dishes.reversed()))
        assertEquals("pan con jamon", FoodCombinationParser.parse(text).dishName)
        FoodKnowledge.reset()
        assertEquals("pan con palta", FoodCombinationParser.parse(text).dishName)
    }

    @Test
    fun `an installed sandwich is a known sandwich mention and is gone after reset`() {
        val text = "un sandwich de queso y café"
        assertEquals("the clause stops before the drink", "sandwich de queso", FoodCombinationParser.sandwichMention(text))
        assertEquals(0.85, FoodCombinationParser.parse("sandwich de queso y café").confidence, 0.0)
        val withCoffee = DishComposition(
            "sandwich de queso y café",
            listOf(
                DishComponent("pan", 0.4, Role.STARCH),
                DishComponent("queso", 0.5, Role.TOPPING),
                DishComponent("café", 0.1, Role.SIDE),
            ),
        )
        FoodKnowledge.install(withDishes(default.dishCompositions.dishes + withCoffee))
        assertEquals("the known dish is the whole mention", "sandwich de queso y café", FoodCombinationParser.sandwichMention(text))
        assertEquals(0.95, FoodCombinationParser.parse("sandwich de queso y café").confidence, 0.0)
        FoodKnowledge.reset()
        assertEquals("sandwich de queso", FoodCombinationParser.sandwichMention(text))
    }

    @Test
    fun `an installed table that adds or drops a dish reaches MassBoundDish, which forgets what it remembered`() {
        assertNull("not a dish yet", MassBoundDish.whole("200 g de arroz con ostras"))
        assertNotNull("a dish of the table", MassBoundDish.whole("200 g de arroz con pollo"))
        FoodKnowledge.install(withDishes(default.dishCompositions.dishes.filterNot { it.name == "arroz con pollo" } + ostras))
        val match = requireNotNull(MassBoundDish.whole("200 g de arroz con ostras"))
        assertEquals("arroz con ostras", match.dish)
        assertEquals(listOf("arroz", "ostras"), match.parts)
        assertEquals(listOf("140 g de arroz", "60 g de ostras"), MassBoundDish.fragments(match))
        assertNull("a dish the installed table dropped", MassBoundDish.whole("200 g de arroz con pollo"))
        FoodKnowledge.reset()
        assertNull(MassBoundDish.whole("200 g de arroz con ostras"))
        assertNotNull(MassBoundDish.whole("200 g de arroz con pollo"))
    }

    @Test
    fun `an installed protected phrase that names a dish keeps MassBoundDish from binding a mass to it`() {
        assertNotNull(MassBoundDish.whole("200 g de arroz con pollo"))
        val lists = default.protectedPhrases
        FoodKnowledge.install(default.copy(protectedPhrases = lists.copy(entityLiterals = lists.entityLiterals + "arroz con pollo")))
        assertNull("a protected phrase names the dish already", MassBoundDish.whole("200 g de arroz con pollo"))
        FoodKnowledge.reset()
        assertNotNull(MassBoundDish.whole("200 g de arroz con pollo"))
    }

    // ─── Concurrency ────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Three readers call [read] in a loop while the writer installs [changed] and resets for a quarter of a second; [read] says whether it
     * saw something that is neither snapshot. Returns the number of surprises, of reads and of swaps.
     */
    private fun raceReadersAgainstSwaps(changed: FoodKnowledgeSnapshot, read: () -> Boolean): Triple<Int, Int, Int> {
        val stop = AtomicBoolean(false)
        val surprises = AtomicInteger()
        val reads = AtomicInteger()
        val readers = List(3) {
            thread(isDaemon = true) {
                while (!stop.get()) {
                    if (read()) surprises.incrementAndGet()
                    reads.incrementAndGet()
                }
            }
        }
        // Let every reader start before the first swap, then swap for a quarter of a second.
        val started = System.nanoTime()
        while (reads.get() < readers.size && System.nanoTime() - started < 10_000_000_000L) Thread.sleep(1)
        val end = System.nanoTime() + 250_000_000L
        var swaps = 0
        while (System.nanoTime() < end) {
            FoodKnowledge.install(changed)
            FoodKnowledge.reset()
            swaps++
        }
        stop.set(true)
        readers.forEach { it.join(10_000) }
        return Triple(surprises.get(), reads.get(), swaps)
    }

    @Test
    fun `readers racing installs and resets only ever see one snapshot or the other`() {
        val changed = default.copy(typos = default.typos + ("pyollo" to "pollo"))
        val (surprises, reads, swaps) = raceReadersAgainstSwaps(changed) {
            val result = TextNormalizer.normalize("2 pyollo")
            result != "2 pyollo" && result != "2 pollo"
        }
        assertEquals("a reader saw something that is neither snapshot", 0, surprises)
        assertTrue("the readers ran ($reads reads)", reads >= 3)
        assertTrue("the writer swapped ($swaps swaps)", swaps > 0)
        assertSame(default, FoodKnowledge.current())
    }

    @Test
    fun `parsers racing installs and resets only ever see the dishes of one snapshot`() {
        val changed = withDishes(default.dishCompositions.dishes + ostras)
        val (surprises, reads, swaps) = raceReadersAgainstSwaps(changed) {
            val parsed = FoodCombinationParser.parse("arroz con ostras")
            val reading = when {
                parsed.isKnownDish && parsed.dishName == "arroz con ostras" && parsed.baseProportion == 0.7 &&
                    parsed.accompaniments == listOf(Accompaniment("ostras", 0.3, Role.TOPPING)) -> "the installed dish"
                !parsed.isKnownDish && parsed.dishName == null -> "the generic reading"
                else -> "a mix of both"
            }
            reading == "a mix of both"
        }
        assertEquals("a reader saw something that is neither snapshot", 0, surprises)
        assertTrue("the readers ran ($reads reads)", reads >= 3)
        assertTrue("the writer swapped ($swaps swaps)", swaps > 0)
        assertSame(default, FoodKnowledge.current())
    }
}
