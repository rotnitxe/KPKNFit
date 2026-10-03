package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.PORTION_MULTIPLIERS
import com.example.kpkn.data.models.PortionPreset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N9: protected phrases are read before typos and before connectors cut them, a compound name is one food, and a mass said
 * next to a named dish weighs the dish (audit findings A-P3 and A-P5, blind-probe inputs #1, #9, #10, #11 and #39-#41).
 *
 * The expectations come from how people write the phrases, not from the parser's own tables: "dos cafés con leche" is two coffees
 * with milk, "tortilla de maíz" is not a tortilla of choclo, and "200 g de arroz con pollo" is one plate of 200 g.
 */
class ProtectedPhrasesTest {

    // --- Environment ----------------------------------------------------------------------------------------------

    private fun noOpNutritionDao(): NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            NutritionDao::class.java.classLoader,
            arrayOf(NutritionDao::class.java),
        ) { _, _, _ -> null } as NutritionDao

    private val port: FoodResolutionPort by lazy {
        val staticFoods = buildFoodDatabase()
        val index = FoodIndex().apply { build(emptyList(), staticFoods, FOOD_ALIASES) }
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                resolver.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = staticFoods.firstOrNull { it.id == id }
            override suspend fun staticFood(tag: String): FoodItem? = HouseholdPortions.householdStaticFood(tag)
            override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
    }

    private fun resolve(text: String): List<ResolvedTag> = runBlocking {
        TagResolver(port).resolveAll(parseMealDescription(text)).first.filterNot { it.isExcluded }
    }

    private fun single(text: String): ResolvedTag {
        val tags = resolve(text)
        assertEquals("$text -> ${tags.map { it.tag }}", 1, tags.size)
        return tags.single()
    }

    private fun items(text: String) = parseMealDescription(text).items

    private fun tags(text: String) = items(text).map { it.tag }

    /** The serving a catalog dish declares, in grams: what a plate or a count of the dish is made of. */
    private fun serving(dish: String): Double = checkNotNull(findFoodExactByNormalized(dish)) { dish }.let {
        NutrientBasis.massForServingUnits(it, it.servingSize)
    }

    // --- The phrases and their spellings --------------------------------------------------------------------------

    @Test fun theProtectedDishesOfTheParserAreStillProtected() {
        for (name in listOf(
            "arroz con leche", "pastel de choclo", "pasteles de choclo", "empanadas de pino", "porotos con riendas", "café con leche",
            "té con leche", "leche con plátano", "sándwich de jamón y queso", "hamburguesa con queso", "papas con mayo", "tres leches",
        )) {
            assertTrue(name, name in ProtectedPhrases.PROTECTED_ENTITIES)
            assertTrue(name, ProtectedPhrases.isEntity(name))
        }
        assertFalse(ProtectedPhrases.isEntity("pollo con arroz"))
        assertFalse(ProtectedPhrases.isEntity("arroz con pollo"))
    }

    @Test fun aPhraseIsRecognisedWhateverItsCaseAndAccents() {
        assertTrue(ProtectedPhrases.isEntity("Café con Leche"))
        assertTrue(ProtectedPhrases.isEntity("cafe con leche"))
        assertTrue(ProtectedPhrases.isEntityKey("Cafe  con leche"))
        assertTrue(ProtectedPhrases.isPhrase("pan con palta"))
        assertTrue(ProtectedPhrases.isPhrase("Pan con Palta"))
        assertFalse(ProtectedPhrases.isPhrase("pan con tomate"))
    }

    @Test fun everyProtectedPhraseHasItsPluralAndItsAccentFreeSpelling() {
        val cases = mapOf(
            "café con leche" to listOf("cafés con leche", "cafe con leche", "cafes con leche"),
            "pan con palta" to listOf("panes con palta"),
            "arroz con leche" to listOf("arroces con leche"),
            "pastel de choclo" to listOf("pasteles de choclo"),
            "sándwich de jamón y queso" to listOf("sándwiches de jamón y queso", "sandwiches de jamon y queso"),
            "empanada de pino" to listOf("empanadas de pino"),
            "hamburguesa con queso" to listOf("hamburguesas con queso"),
            "té con leche" to listOf("tés con leche", "te con leche"),
            "porotos con riendas" to listOf("poroto con riendas"),
            "papas con mayo" to listOf("papa con mayo"),
        )
        for ((phrase, spellings) in cases) {
            val all = ProtectedPhrases.spellings(phrase)
            assertTrue("$phrase itself", phrase in all)
            for (spelling in spellings) assertTrue("$phrase -> $spelling in $all", spelling in all)
        }
    }

    @Test fun aPhraseThatIsNotHeadNounPlusConnectorGetsNoInventedPlural() {
        // "papas fritas" is a name of its own and "porotos granados" too: no "papa fritas", no "poroto granados".
        assertEquals(listOf("papas fritas"), ProtectedPhrases.spellings("Papas fritas"))
        assertEquals(listOf("porotos granados"), ProtectedPhrases.spellings("porotos granados"))
    }

    @Test fun compoundNamesAndFlavourPairsAreOnTheList() {
        for (name in listOf("agua con gas", "agua sin gas", "agua mineral con gas", "ave palta", "ave mayo", "tortilla de maíz", "aceite de maíz")) {
            assertTrue(name, name in ProtectedPhrases.COMPOUND_NAMES)
        }
        assertTrue("empanada de jamón y queso" in ProtectedPhrases.COMPOUND_NAMES)
        assertTrue("vainilla y chocolate" in ProtectedPhrases.FLAVOR_PAIRS)
        assertTrue("chocolate y vainilla" in ProtectedPhrases.FLAVOR_PAIRS)
        assertFalse("vainilla y vainilla" in ProtectedPhrases.FLAVOR_PAIRS)
    }

    @Test fun maskingHidesEverySpellingAndRestoreBringsTheOriginalBack() {
        val text = "2 cafés con leche y un plato de Porotos con Riendas con pan"
        val masked = ProtectedPhrases.mask(text) { "<$it>" }
        assertEquals("2 <0> y un plato de <1> con pan", masked.text)
        assertEquals(listOf("cafés con leche", "Porotos con Riendas"), masked.originals)
        assertEquals(text, masked.restore(masked.text))
    }

    @Test fun aFlavourPairIsMaskedOnlyAfterAFoodThatComesInFlavours() {
        assertEquals("<0>", ProtectedPhrases.mask("helado de vainilla y chocolate") { "<$it>" }.text)
        assertEquals("1 bola de <0>", ProtectedPhrases.mask("1 bola de helado de lúcuma y manjar") { "<$it>" }.text)
        assertEquals("<0> y una galleta", ProtectedPhrases.mask("torta de chocolate y vainilla y una galleta") { "<$it>" }.text)
        assertEquals("<0>", ProtectedPhrases.mask("Yogurt de frutilla y vainilla") { "<$it>" }.text)
        // Nothing before the pair, no "de", or a food that is not served in flavours: it is a list of two foods, as ever.
        assertEquals("vainilla y chocolate", ProtectedPhrases.mask("vainilla y chocolate") { "<$it>" }.text)
        assertEquals("helado con vainilla y chocolate", ProtectedPhrases.mask("helado con vainilla y chocolate") { "<$it>" }.text)
        assertEquals("helado vainilla y chocolate", ProtectedPhrases.mask("helado vainilla y chocolate") { "<$it>" }.text)
        assertEquals("jugo de limón y menta", ProtectedPhrases.mask("jugo de limón y menta") { "<$it>" }.text)
        assertEquals("leche de coco y chocolate", ProtectedPhrases.mask("leche de coco y chocolate") { "<$it>" }.text)
    }

    @Test fun aTextWithNoPhraseIsReturnedAsIs() {
        val masked = ProtectedPhrases.mask("200 g de pollo y arroz") { "<$it>" }
        assertEquals("200 g de pollo y arroz", masked.text)
        assertTrue(masked.originals.isEmpty())
        assertFalse(ProtectedPhrases.containsPhrase("200 g de pollo y arroz"))
        assertTrue(ProtectedPhrases.containsPhrase("un plato de porotos con riendas"))
    }

    // --- Typos run on what is not a protected phrase (A-P3) -------------------------------------------------------

    @Test fun aProtectedPhraseKeepsItsWordsThroughTheNormalizer() {
        assertEquals("porotos con riendas", TextNormalizer.normalize("porotos con riendas"))
        assertEquals("papas con mayo", TextNormalizer.normalize("papas con mayo"))
        assertEquals("2 cafés con leche", TextNormalizer.normalize("dos cafés con leche"))
        assertEquals("3 panes con palta", TextNormalizer.normalize("tres panes con palta"))
        assertEquals("leche con plátano", TextNormalizer.normalize("leche con plátano"))
        assertEquals("agua sin gas", TextNormalizer.normalize("agua sin gas"))
    }

    @Test fun aTypoNextToAProtectedPhraseIsStillCorrected() {
        assertEquals("papas con mayo y pollo", TextNormalizer.normalize("papas con mayo y poyo"))
        assertEquals("pollo con arroz", TextNormalizer.normalize("poyo con arros"))
        assertEquals("2 huevos", TextNormalizer.normalize("2 uebos"))
    }

    @Test fun pluralsAreNotCutToTheSingularByTheTypoPass() {
        for (plural in listOf("papas", "porotos", "lentejas", "garbanzos")) {
            assertEquals(plural, TextNormalizer.normalize(plural))
        }
        assertEquals("lentejas con arroz", TextNormalizer.normalize("lentejas con arroz"))
    }

    @Test fun aRegionalNameIsASynonymOnItsOwn() {
        assertEquals("palta", TextNormalizer.normalize("aguacate"))
        assertEquals("2 paltas", TextNormalizer.normalize("2 aguacates"))
        assertEquals("choclo", TextNormalizer.normalize("maíz"))
        assertEquals("choclo", TextNormalizer.normalize("maiz"))
        assertEquals("betarraga", TextNormalizer.normalize("remolacha"))
        assertEquals("zapallo", TextNormalizer.normalize("calabaza"))
        assertEquals("2 zapallos", TextNormalizer.normalize("2 calabazas"))
        assertEquals("pan con palta", TextNormalizer.normalize("pan con aguacate"))
        assertEquals("crema de zapallo", TextNormalizer.normalize("crema de calabaza"))
        assertEquals("jugo de betarraga", TextNormalizer.normalize("jugo de remolacha"))
    }

    @Test fun aSynonymNeverRenamesTheWordOfADish() {
        for (dish in listOf(
            "tortilla de maíz", "tortillas de maíz", "aceite de maíz", "harina de maíz", "palomitas de maíz",
            "pastel de choclo", "crema de zapallo", "maiz en grano",
        )) {
            assertEquals(dish, TextNormalizer.normalize(dish))
        }
        assertEquals(listOf("tortilla de maíz"), tags("tortilla de maíz"))
        assertEquals(listOf("pastel de choclo"), tags("pastel de choclo"))
        assertEquals(listOf("crema de zapallo"), tags("crema de zapallo"))
    }

    // --- Connectors do not cut a compound name (A-P5) -------------------------------------------------------------

    @Test fun carbonatedAndStillWaterAreOneMentionAndNothingIsExcluded() {
        assertEquals(listOf("agua con gas"), tags("agua con gas"))
        assertEquals(listOf("agua sin gas"), tags("agua sin gas"))
        assertEquals(listOf("agua mineral sin gas"), tags("agua mineral sin gas"))
        assertTrue(items("agua sin gas").none { it.isExcluded })
        assertEquals("gen151", single("agua con gas").foodItem?.id)
        assertEquals("gen143", single("agua sin gas").foodItem?.id)
    }

    @Test fun twoFlavoursOfOneFoodAreOneMention() {
        assertEquals(listOf("helado de vainilla y chocolate"), tags("helado de vainilla y chocolate"))
        assertEquals(listOf("yogurt de frutilla y vainilla"), tags("yogurt de frutilla y vainilla"))
        assertEquals(1, resolve("helado de vainilla y chocolate").size)
        // A second food still opens a second mention.
        assertEquals(listOf("helado de vainilla", "galleta"), tags("helado de vainilla y una galleta"))
        assertEquals(listOf("vainilla", "chocolate"), tags("vainilla y chocolate"))
        // A drink or a juice is not a food that comes in flavours: its pair of flavours still splits.
        assertEquals(listOf("jugo de limón", "menta"), tags("jugo de limón y menta"))
    }

    @Test fun aSandwichNamedAfterItsFillingIsOneMention() {
        assertEquals(listOf("empanada de jamón y queso"), tags("empanada de jamón y queso"))
        assertEquals(listOf("sándwich de jamón y queso"), tags("2 sándwiches de jamón y queso"))
        assertEquals(2.0, items("2 sándwiches de jamón y queso").single().quantity, 0.001)
        assertEquals(listOf("pan", "jamón", "queso"), tags("pan, jamón y queso"))
    }

    @Test fun pluralsOfAProtectedDishAreProtectedToo() {
        val coffees = items("dos cafés con leche").single()
        assertEquals("café con leche", coffees.tag)
        assertEquals(2.0, coffees.quantity, 0.001)
        val breads = items("tres panes con palta").single()
        assertEquals("pan con palta", breads.tag)
        assertEquals(3.0, breads.quantity, 0.001)
        assertEquals(listOf("arroz con leche"), tags("2 arroces con leche"))
        // A plural the parser lists as an entity keeps its plural, like a catalog name does.
        assertEquals(1, items("2 hamburguesas con queso").size)
        assertEquals(listOf("empanadas de pino"), tags("empanadas de pino"))
    }

    @Test fun twoCoffeesWithMilkAreTheCatalogCoffeeWithMilkTwice() {
        val tag = single("dos cafés con leche")
        assertEquals("gen145", tag.foodItem?.id)
        assertEquals(2.0, tag.quantity, 0.001)
        assertTrue("two cups: ${tag.amountGrams}", (tag.amountGrams ?: 0.0) in 400.0..560.0)
    }

    @Test fun aCountOfANamedDishIsThatManyServings() {
        val breads = single("3 panes con palta")
        assertEquals("cl025", breads.foodItem?.id)
        assertEquals(3 * serving("pan con palta"), breads.amountGrams ?: Double.NaN, 0.01)
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, breads.amountIntent)
        val one = single("un pan con palta")
        assertEquals(serving("pan con palta"), one.amountGrams ?: Double.NaN, 0.01)
    }

    @Test fun theDishesOfTheProbeAreOneTagEach() {
        // #40 and the singular spelling, #39, #41, and the ones whose words used to be cut by the typo pass.
        assertEquals("cl029", single("porotos con riendas").foodItem?.id)
        assertEquals("cl029", single("poroto con riendas").foodItem?.id)
        assertEquals(listOf("porotos con riendas"), tags("porotos con riendas"))
        assertEquals("cl007", single("porotos granados").foodItem?.id)
        assertEquals(listOf("papas con mayo"), tags("papas con mayo"))
        assertEquals(1, resolve("papas con mayo").size)
        val lentilsAndRice = resolve("lentejas con arroz")
        assertEquals(2, lentilsAndRice.size)
        assertEquals(listOf("gen012", "gen005"), lentilsAndRice.map { it.foodItem?.id })
    }

    @Test fun aPluralWordOnItsOwnStillResolves() {
        // A staple keeps the singular tag the typo pass used to give it: the plural is cut by the tag, not by the text.
        assertEquals(listOf("lenteja"), tags("lentejas"))
        assertEquals("gen012", single("lentejas").foodItem?.id)
        assertEquals("gen013", single("garbanzos").foodItem?.id)
        assertEquals("gen135", single("porotos").foodItem?.id)
        assertEquals("gen021", single("papas").foodItem?.id)
        // A count of them is a count of the singular.
        val two = items("2 garbanzos").single()
        assertEquals("garbanzo", two.tag)
        assertEquals(2.0, two.quantity, 0.001)
    }

    @Test fun friedPotatoesKeepTheSingularTagAndTheFriedRow() {
        // With the plural tag the prepared-row lookup of the resolver ("papas (fritas)") meets the bag of chips before the fried potato.
        val fries = items("papas fritas").single()
        assertEquals("papa", fries.tag)
        assertEquals(CookingMethod.FRITO, fries.cookingMethod)
        assertEquals("gen021f", single("papas fritas").foodItem?.id)
        assertEquals("gen021f", single("papas fritas 150 g").foodItem?.id)
        assertEquals("gen140", single("papas fritas snack").foodItem?.id)
        // A staple inside a protected dish keeps the words of the dish; next to another food it is its own mention.
        assertEquals(listOf("papas con mayo"), tags("papas con mayo"))
        assertEquals(listOf("papas con mayo"), tags("2 papas con mayo"))
        assertEquals(listOf("papa", "ketchup"), tags("papas fritas con ketchup"))
    }

    // --- The first probe input: a plate, a sandwich and a coffee with milk -------------------------------------------

    @Test fun aPlateOfBeansWithRiendasAndABreadWithAvocadoAndACoffeeWithMilkAreThreeTags() {
        val text = "almorcé un plato de porotos con riendas y un pan con palta, después un café con leche"
        assertEquals(listOf("porotos con riendas", "pan con palta", "café con leche"), tags(text))
        val tags = resolve(text)
        assertEquals(listOf("cl029", "cl025", "gen145"), tags.map { it.foodItem?.id })
        for (tag in tags) {
            assertEquals("${tag.tag} status", FoodResolutionStatus.AUTO, tag.resolutionStatus)
            assertFalse("${tag.tag} question", tag.hasMaterialQuestion())
            assertTrue("${tag.tag} pending", tag.interpretationV2?.pendingQuestions.orEmpty().isEmpty())
        }
        // The plate is one serving of the dish (350 g in the catalog), not the share of a topping.
        assertEquals(serving("porotos con riendas"), tags[0].amountGrams ?: Double.NaN, 0.01)
        assertEquals(350.0, serving("porotos con riendas"), 0.01)
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, tags[0].amountIntent)
    }

    @Test fun aPlateOfANamedDishIsOneServingOfIt() {
        val beans = items("un plato de porotos con riendas").single()
        assertEquals("porotos con riendas", beans.tag)
        assertEquals(serving("porotos con riendas"), beans.amountGrams ?: Double.NaN, 0.01)
        assertEquals("plato", beans.unitId)
        val two = items("2 platos de porotos con riendas").single()
        assertEquals(2 * serving("porotos con riendas"), two.amountGrams ?: Double.NaN, 0.01)
        assertEquals(2.0, two.quantity, 0.001)
        // The size of the plate scales the serving once: one scale for every size word.
        val large = PORTION_MULTIPLIERS.getValue(PortionPreset.LARGE)
        val small = PORTION_MULTIPLIERS.getValue(PortionPreset.SMALL)
        assertEquals(large * serving("porotos con riendas"), items("un plato grande de porotos con riendas").single().amountGrams ?: Double.NaN, 0.01)
        assertEquals(small * serving("porotos con riendas"), items("un plato chico de porotos con riendas").single().amountGrams ?: Double.NaN, 0.01)
        // A plate of a dish that is not a protected phrase keeps going through the portion engine.
        val rice = items("un plato de arroz").single()
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, rice.amountIntent)
        assertNotEquals(350.0, rice.amountGrams ?: Double.NaN, 0.01)
    }

    // --- A mass next to a named dish weighs the dish (A-P5, input #11) --------------------------------------------

    private fun massAndTag(text: String) = items(text).map { it.tag to it.amountGrams }

    @Test fun aMassInFrontOfADishIsSharedByItsParts() {
        val parsed = items("200 g de arroz con pollo")
        assertEquals(listOf("arroz", "pollo"), parsed.map { it.tag })
        assertEquals(listOf(100.0, 100.0), parsed.map { it.amountGrams })
        assertTrue(parsed.all { it.amountIntent == AmountIntent.EXPLICIT_MASS })
        assertTrue(parsed.all { it.containerScope == "arroz con pollo" })
        // The same plate however the mass is said.
        assertEquals(listOf("arroz" to 100.0, "pollo" to 100.0), massAndTag("200g de arroz con pollo"))
        assertEquals(listOf("arroz" to 100.0, "pollo" to 100.0), massAndTag("200 g arroz con pollo"))
        assertEquals(listOf("arroz" to 100.0, "pollo" to 100.0), massAndTag("arroz con pollo 200 g"))
        assertEquals(listOf("arroz" to 500.0, "pollo" to 500.0), massAndTag("1 kg de arroz con pollo"))
    }

    @Test fun thePartsTakeTheSharesOfTheDish() {
        assertEquals(listOf("arroz" to 120.0, "huevo" to 80.0), massAndTag("200 g de arroz con huevo"))
        val sandwich = items("150 g de pan con palta y huevo")
        assertEquals(listOf("pan", "palta", "huevo"), sandwich.map { it.tag })
        assertEquals(150.0, sandwich.sumOf { it.amountGrams ?: 0.0 }, 0.001)
        assertEquals(listOf(52.5, 52.5, 45.0), sandwich.map { it.amountGrams })
    }

    @Test fun thePartsKeepTheWordsOfThePersonWhenTheyNameAsManyPartsAsTheDishHas() {
        val parsed = items("200 g de arroz con huevo frito")
        assertEquals(listOf("arroz", "huevo"), parsed.map { it.tag })
        assertEquals(com.example.kpkn.data.models.CookingMethod.FRITO, parsed[1].cookingMethod)
    }

    @Test fun aSecondMentionAfterTheDishStaysASecondMention() {
        val parsed = items("200 g de arroz con pollo y ensalada")
        assertEquals(listOf("arroz", "pollo", "ensalada"), parsed.map { it.tag })
        assertEquals(listOf(100.0, 100.0, null), parsed.map { it.amountGrams })
        assertEquals(AmountIntent.UNSPECIFIED, parsed[2].amountIntent)
        val measured = items("200 g de arroz con pollo y 100 g de ensalada")
        assertEquals(listOf(100.0, 100.0, 100.0), measured.map { it.amountGrams })
        assertTrue(measured.all { it.amountIntent == AmountIntent.EXPLICIT_MASS })
        assertEquals(listOf("arroz", "pollo", "ensalada"), items("hoy comí 200 g de arroz con pollo, ensalada").map { it.tag })
    }

    @Test fun anExclusionBelongsToTheLastPartOfTheDish() {
        val parsed = items("200 g de arroz con pollo sin cebolla")
        assertEquals(listOf("arroz", "pollo", "cebolla"), parsed.map { it.tag })
        assertEquals(listOf(100.0, 100.0, null), parsed.map { it.amountGrams })
        assertTrue(parsed[2].isExcluded)
        assertTrue(parsed[0].excludedIngredients.isEmpty())
        assertEquals(setOf("cebolla"), parsed[1].excludedIngredients)
    }

    @Test fun withoutAMassTheDishStillSplitsIntoItsFoods() {
        val parsed = items("arroz con pollo")
        assertEquals(listOf("arroz", "pollo"), parsed.map { it.tag })
        assertTrue(parsed.all { it.amountIntent == AmountIntent.UNSPECIFIED && it.containerScope == null })
    }

    @Test fun aMassStaysWithTheFirstFoodWhenTheSecondOneIsAnAddOn() {
        // Oats are weighed dry, a soup base or a sauce is added to the food: the mass is the first food's, as it always was.
        assertEquals(listOf("avena" to 100.0, "leche" to null), massAndTag("100 g de avena con leche"))
        assertEquals(listOf("pasta" to 300.0, "salsa de tomate" to null), massAndTag("300 g de pasta con salsa de tomate"))
        // The chicken is what is weighed, the rice goes with it.
        assertEquals(listOf("pollo" to 200.0, "arroz" to null), massAndTag("200 g pollo con arroz"))
        assertEquals(listOf("pollo" to 150.0, "arroz" to null, "ensalada" to null), massAndTag("150 g de pollo con arroz y ensalada"))
    }

    @Test fun aDishThatTheCatalogNamesIsNotCutIntoParts() {
        val bread = items("250 g de pan con palta").single()
        assertEquals("pan con palta", bread.tag)
        assertEquals(250.0, bread.amountGrams ?: Double.NaN, 0.01)
        assertEquals("cl025", single("250 g de pan con palta").foodItem?.id)
    }

    @Test fun aMassBoundDishThatIsNotAMentionOfItsOwnIsLeftToTheOldReading() {
        // "2 porciones de 200 g de arroz con pollo" has words before the mass: the dish is cut at its "con", as it always was.
        val parsed = items("2 porciones de 200 g de arroz con pollo")
        assertEquals(listOf("arroz", "pollo"), parsed.map { it.tag })
        assertEquals(200.0, parsed[0].amountGrams ?: Double.NaN, 0.01)
        assertNull(parsed[1].amountGrams)
    }

    @Test fun aDishFollowedByACookingWordIsLeftToTheOldReading() {
        val parsed = items("200 g de arroz con pollo al horno")
        assertEquals(2, parsed.size)
        assertEquals(200.0, parsed[0].amountGrams ?: Double.NaN, 0.01)
    }

    @Test fun theSplitterHandsTheParserOneMeasuredMentionPerPart() {
        assertEquals(listOf("100 g de arroz", "100 g de pollo"), splitMealFragments("200 g de arroz con pollo"))
        assertEquals(listOf("arroz", "pollo"), splitMealFragments("arroz con pollo"))
    }

    @Test fun theMassOfADishIsResolvedPartByPart() {
        val parts = resolve("200 g de arroz con pollo")
        assertEquals(listOf("gen005", "gen004"), parts.map { it.foodItem?.id })
        assertEquals(listOf(100.0, 100.0), parts.map { it.amountGrams })
        assertEquals(listOf(AmountIntent.EXPLICIT_MASS, AmountIntent.EXPLICIT_MASS), parts.map { it.amountIntent })
    }

    @Test fun massBoundDishDoesNotScanAHugeText() {
        val huge = "200 g de arroz con pollo, ".repeat(400)
        assertTrue(huge.length > 2000)
        assertTrue(MassBoundDish.findAll(huge).isEmpty())
        assertNull(MassBoundDish.whole("arroz con pollo"))
        assertEquals(1, MassBoundDish.findAll("200 g de arroz con pollo").size)
    }
}
