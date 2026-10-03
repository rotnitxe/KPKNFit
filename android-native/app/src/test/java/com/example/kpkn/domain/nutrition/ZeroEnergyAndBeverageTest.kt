package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.GENERIC_FOODS
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.food.isApproximationAlias
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.FoodItem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-N5: foods of zero energy, drinks, containers, units and the density of liquids (audit findings A-Q3, A-Q4,
 * A-Q5 and A-Q10, inputs #3, #5, #9, #14-19 and #42-45 of the blind probe).
 *
 * Pure JVM, same chain as the drawer: parseMealDescription -> TagResolver(port).resolveAll. Two ports: the static
 * catalog alone, and the catalog next to the supermarket rows an Open Food Facts import adds (Coca-Cola), so the
 * generic rows and the branded ones are shown to coexist.
 */
class ZeroEnergyAndBeverageTest {

    // --- Environment ----------------------------------------------------------------------------------------------

    private fun noOpNutritionDao(): NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            NutritionDao::class.java.classLoader,
            arrayOf(NutritionDao::class.java),
        ) { _, _, _ -> null } as NutritionDao

    private fun offRow(id: String, name: String, brand: String, kcal: Double, carbs: Double): GlobalFoodEntity {
        val normalizedName = FoodIndex.normalizeSearch(name)
        val normalizedBrand = FoodIndex.normalizeSearch(brand)
        return GlobalFoodEntity(
            foodId = id,
            name = name,
            brand = brand,
            normalizedName = normalizedName,
            normalizedBrand = normalizedBrand,
            aliasesJson = JsonArray(listOf(normalizedName, normalizedBrand).map { JsonPrimitive(it) }).toString(),
            calories = kcal,
            protein = 0.0,
            carbs = carbs,
            fats = 0.0,
            sugar = carbs,
            source = "OFF Chile",
            sourcePriority = 80,
            verifiedScore = 0.85,
            sourceRecordId = id.removePrefix("off_"),
            nutritionBasis = "PER_100G_AS_SOLD",
        )
    }

    private val cocaColaRows = listOf(
        offRow("off_coca_cola_original_350ml", "Coca-Cola Original 350 ml", "Coca-Cola", 42.0, 10.6),
        offRow("off_coca_cola_zero", "Coca-Cola Zero", "Coca-Cola", 0.2, 0.0),
    )

    private fun portFor(globals: List<GlobalFoodEntity>): FoodResolutionPort {
        val staticFoods = buildFoodDatabase()
        val foods = staticFoods + globals.map { it.toFoodItem() }
        val index = FoodIndex().apply { build(globals, staticFoods, FOOD_ALIASES) }
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        return object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                resolver.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = foods.firstOrNull { it.id == id }
            override suspend fun staticFood(tag: String): FoodItem? = HouseholdPortions.householdStaticFood(tag)
            override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
    }

    private val catalogPort: FoodResolutionPort by lazy { portFor(emptyList()) }
    private val brandedPort: FoodResolutionPort by lazy { portFor(cocaColaRows) }

    private fun resolve(text: String, branded: Boolean = false): List<ResolvedTag> = runBlocking {
        TagResolver(if (branded) brandedPort else catalogPort).resolveAll(parseMealDescription(text)).first
            .filterNot { it.isExcluded }
    }

    private fun single(text: String, branded: Boolean = false): ResolvedTag {
        val tags = resolve(text, branded)
        assertEquals("$text -> ${tags.map { it.tag }}", 1, tags.size)
        return tags.single()
    }

    private fun ResolvedTag.kcal(): Double = loggedFood?.calories ?: Double.NaN

    /** Saved as is: resolved to a row, no question to answer. */
    private fun assertSavedWithoutQuestion(tag: ResolvedTag, label: String) {
        assertEquals("$label status", FoodResolutionStatus.AUTO, tag.resolutionStatus)
        assertTrue("$label resolved", tag.isResolved)
        assertFalse("$label material question", tag.hasMaterialQuestion())
        assertTrue("$label pending questions", tag.interpretationV2?.pendingQuestions.orEmpty().isEmpty())
    }

    private fun assertWater(tag: ResolvedTag, id: String, grams: Double, label: String) {
        assertEquals("$label row", id, tag.foodItem?.id)
        assertEquals("$label grams", grams, tag.amountGrams ?: Double.NaN, 0.01)
        assertEquals("$label kcal", 0.0, tag.kcal(), 0.0)
        assertSavedWithoutQuestion(tag, label)
    }

    // --- Zero energy (A-Q10): inputs #3, #9, #17, #18, #45 ------------------------------------------------------------

    @Test
    fun `a glass of water is 250 g of Agua, zero kcal, saved without a question`() {
        val tag = single("un vaso de agua")
        assertWater(tag, "gen143", 250.0, "un vaso de agua")
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, tag.amountIntent)
    }

    @Test
    fun `carbonated and still water are one tag each with no kcal`() {
        assertWater(single("agua con gas"), "gen151", 250.0, "agua con gas")
        assertWater(single("agua sin gas"), "gen143", 250.0, "agua sin gas")
        assertWater(single("agua mineral con gas"), "gen151", 250.0, "agua mineral con gas")
        assertWater(single("un vaso de agua con gas"), "gen151", 250.0, "un vaso de agua con gas")
        // "con gas" and "sin gas" qualify the water: there is no "gas" food on the side.
        val tags = resolve("agua con gas") + resolve("agua sin gas")
        assertTrue("gas is not a food: ${tags.map { it.tag }}", tags.none { it.tag.trim() == "gas" })
    }

    @Test
    fun `litres of water keep their mass and cost nothing`() {
        assertWater(single("1 lt de agua"), "gen143", 1000.0, "1 lt de agua")
        assertWater(single("2 litros de agua"), "gen143", 2000.0, "2 litros de agua")
        assertWater(single("2 lts de agua"), "gen143", 2000.0, "2 lts de agua")
        assertWater(single("1.5 l de agua"), "gen143", 1500.0, "1.5 l de agua")
        assertWater(single("litro y medio de agua"), "gen143", 1500.0, "litro y medio de agua")
        assertWater(single("500 cc de agua"), "gen143", 500.0, "500 cc de agua")
        assertWater(single("medio litro de agua"), "gen143", 500.0, "medio litro de agua")
        assertEquals(AmountIntent.EXPLICIT_MASS, single("2 litros de agua").amountIntent)
    }

    @Test
    fun `a bottle of water is 500 g and a count of bottles multiplies it`() {
        assertWater(single("una botella de agua"), "gen143", 500.0, "una botella de agua")
        val two = single("2 botellas de agua")
        assertWater(two, "gen143", 1000.0, "2 botellas de agua")
        assertEquals(2.0, two.quantity, 0.0)
        assertWater(single("una botellita de agua"), "gen143", 330.0, "una botellita de agua")
        assertWater(single("un vasito de agua"), "gen143", 150.0, "un vasito de agua")
    }

    @Test
    fun `water by name resolves to its row, at the glass serving it declares`() {
        for (alias in listOf("agua", "agua mineral", "agua de la llave", "agüita")) {
            val tag = single(alias)
            assertWater(tag, "gen143", 250.0, alias)
        }
    }

    @Test
    fun `a food declared zero-energy is plausible with every macro at zero, nothing else is`() {
        val water = checkNotNull(findFoodExactByNormalized("agua"))
        assertTrue(FoodIdentity.hasPlausibleMacros(water))
        assertTrue(NutrientBasis.isVerified(water))
        assertTrue(NutrientBasis.isZeroEnergy(water))
        // The creatine row is all zero and NOT declared zero-energy: still a broken row for the nutrition pipeline.
        val creatine = GENERIC_FOODS.first { it.id == "gen106" }
        assertFalse(FoodIdentity.hasPlausibleMacros(creatine))
        // The declaration cannot hide calories: a mislabelled row is neither zero-energy nor excused.
        val liar = water.copy(id = "liar", calories = 100.0, tags = listOf(NutrientBasis.ZERO_ENERGY_TAG))
        assertFalse(NutrientBasis.isZeroEnergy(liar))
        // Without the tag the all-zero water row is the same broken row as before.
        assertFalse(FoodIdentity.hasPlausibleMacros(water.copy(tags = emptyList())))
        // The index carries the flag, so the resolver does not drop the row as an empty one.
        val index = FoodIndex().apply { build(emptyList(), buildFoodDatabase(), FOOD_ALIASES) }
        assertTrue(index.getFood("gen143")?.isZeroEnergy == true)
        assertTrue(index.getFood("gen151")?.isZeroEnergy == true)
        assertTrue(index.getFood("gen147")?.isZeroEnergy == true)
        assertFalse(index.getFood("gen106")?.isZeroEnergy == true)
        assertFalse(index.getFood("gen016")?.isZeroEnergy == true)
    }

    @Test
    fun `no mass cap applies to zero energy, and a liquid in a container gets a higher cap`() {
        val water = checkNotNull(findFoodExactByNormalized("agua"))
        assertTrue(HouseholdPortions.itemKcalIsPlausible(water, 5000.0, explicitKilogram = false))
        val rice = checkNotNull(findFoodExactByNormalized("arroz"))
        assertFalse("1 kg of rice still needs an explicit kilogram", HouseholdPortions.itemKcalIsPlausible(rice, 1000.0, explicitKilogram = false))
        assertTrue(HouseholdPortions.itemKcalIsPlausible(rice, 1000.0, explicitKilogram = true))
        val milk = checkNotNull(findFoodExactByNormalized("leche"))
        assertTrue("a 1 L carton of milk", HouseholdPortions.itemKcalIsPlausible(milk, 1000.0, explicitKilogram = false))
        assertFalse("but not 3 L of it", HouseholdPortions.itemKcalIsPlausible(milk, 3000.0, explicitKilogram = false))
    }

    @Test
    fun `an explicit bulk unit lifts the mass cap of an unresolved food`() {
        // No row for this juice: the estimate used to be cut at 1200 kcal even when the person said "3 litros".
        val tag = single("3 litros de jugo de manzana")
        assertEquals(3000.0, tag.amountGrams ?: Double.NaN, 0.01)
        assertTrue("not capped: ${tag.kcal()}", tag.kcal() > HouseholdPortions.MAX_ITEM_KCAL_WITHOUT_KG)
        assertTrue(HouseholdPortions.isExplicitKilogram("2 litros de agua"))
        assertTrue(HouseholdPortions.isExplicitKilogram("1 l de agua"))
        assertTrue(HouseholdPortions.isExplicitKilogram("1 lt de agua"))
        assertTrue(HouseholdPortions.isExplicitKilogram("1 kg de papas"))
        assertTrue(HouseholdPortions.isExplicitKilogram("2 kilogramos de papas"))
        assertTrue(HouseholdPortions.isExplicitKilogram("medio litro de leche"))
        assertFalse(HouseholdPortions.isExplicitKilogram("500 ml de leche"))
        assertFalse(HouseholdPortions.isExplicitKilogram("100 g de arroz"))
        assertFalse(HouseholdPortions.isExplicitKilogram("2 lechugas"))
    }

    // --- Drinks: inputs #5, #42-44 -----------------------------------------------------------------------------------

    @Test
    fun `a can of coca cola is 350 g and about 147 kcal, from the generic row or the branded one`() {
        val generic = single("una lata de coca cola")
        assertEquals("gen146", generic.foodItem?.id)
        assertEquals(350.0, generic.amountGrams ?: Double.NaN, 0.01)
        assertTrue("generic kcal ${generic.kcal()}", generic.kcal() in 140.0..160.0)
        assertSavedWithoutQuestion(generic, "una lata de coca cola")

        val branded = single("una lata de coca cola", branded = true)
        assertEquals("off_coca_cola_original_350ml", branded.foodItem?.id)
        assertEquals(350.0, branded.amountGrams ?: Double.NaN, 0.01)
        assertTrue("branded kcal ${branded.kcal()}", branded.kcal() in 140.0..160.0)
        assertSavedWithoutQuestion(branded, "una lata de coca cola (OFF)")
    }

    @Test
    fun `coca cola resolves to a row whose name or brand says coca, with the catalog alone and with the branded rows`() {
        val generic = single("coca cola")
        assertEquals("gen146", generic.foodItem?.id)
        val branded = single("coca cola", branded = true)
        val label = (branded.foodItem?.name.orEmpty() + " " + branded.foodItem?.brand.orEmpty()).lowercase()
        assertTrue("branded row: $label", "coca" in label)
    }

    @Test
    fun `coca cola zero costs no energy, with the catalog alone and with the branded rows`() {
        val generic = single("coca cola zero")
        assertEquals("gen147", generic.foodItem?.id)
        assertEquals(0.0, generic.kcal(), 0.0)
        assertSavedWithoutQuestion(generic, "coca cola zero")
        val branded = single("coca cola zero", branded = true)
        assertEquals("off_coca_cola_zero", branded.foodItem?.id)
        assertTrue("branded kcal ${branded.kcal()}", branded.kcal() <= 1.0)
        assertSavedWithoutQuestion(branded, "coca cola zero (OFF)")
        for (text in listOf("una bebida zero", "una bebida light", "una coca cola light", "una coca zero", "una gaseosa sin azúcar", "una cocacola light", "una pepsi light", "una fanta zero")) {
            val tag = single(text)
            assertEquals("$text row", "gen147", tag.foodItem?.id)
            assertEquals("$text kcal", 0.0, tag.kcal(), 0.0)
            assertEquals("$text grams", 350.0, tag.amountGrams ?: Double.NaN, 0.01)
        }
    }

    @Test
    fun `a cup of tea without sugar is one tag of about 2 kcal`() {
        val tag = single("una taza de té sin azúcar")
        assertEquals("gen144", tag.foodItem?.id)
        assertEquals(240.0, tag.amountGrams ?: Double.NaN, 0.01)
        assertTrue("kcal ${tag.kcal()}", tag.kcal() in 1.0..3.0)
        assertTrue("azúcar is not a food on the side", tag.excludedIngredients.isEmpty() && !tag.tag.startsWith("azúcar"))
        assertSavedWithoutQuestion(tag, "una taza de té sin azúcar")
        assertEquals("gen144", single("té negro").foodItem?.id)
        assertEquals("gen144", single("agua de hierbas").foodItem?.id)
    }

    @Test
    fun `coffee without sugar, solo, espresso and americano are the black coffee row`() {
        for (text in listOf("café sin azúcar", "un café solo", "un espresso", "un americano", "café negro", "café")) {
            val tag = single(text)
            assertEquals("$text row", "gen059", tag.foodItem?.id)
            assertTrue("$text kcal ${tag.kcal()}", tag.kcal() in 0.0..8.0)
        }
        assertSavedWithoutQuestion(single("café sin azúcar"), "café sin azúcar")
    }

    @Test
    fun `cafe con leche is a catalog row, 37 kcal per 100 ml, and no longer an approximation of milk`() {
        val row = checkNotNull(findFoodExactByNormalized("café con leche"))
        assertEquals("gen145", row.id)
        assertEquals(37.0, row.calories, 0.0)
        assertEquals("gen016 + FDC 171890", "171265+171890", row.sourceRecordId)
        assertFalse("approximation alias is gone", isApproximationAlias("café con leche"))
        assertFalse("alias to milk is gone", FOOD_ALIASES.containsKey("café con leche"))
        val tag = single("un café con leche")
        assertEquals("gen145", tag.foodItem?.id)
        assertSavedWithoutQuestion(tag, "un café con leche")
        assertEquals(247.2, tag.amountGrams ?: Double.NaN, 0.05)
        assertTrue("cup of café con leche ${tag.kcal()}", tag.kcal() in 85.0..95.0)
        val cup = single("una taza de café con leche")
        assertEquals("gen145", cup.foodItem?.id)
        assertEquals(247.2, cup.amountGrams ?: Double.NaN, 0.05)
    }

    @Test
    fun `beer, wine, soda and boxed juice declare their own serving`() {
        val expected = listOf(
            Triple("una cerveza", "gen149", 330.0),
            Triple("una chela", "gen149", 330.0),
            Triple("un vino", "gen150", 150.0),
            Triple("un vino tinto", "gen150", 150.0),
            Triple("una gaseosa", "gen146", 350.0),
            Triple("una bebida", "gen146", 350.0),
            Triple("una sprite", "gen146", 350.0),
        )
        for ((text, id, grams) in expected) {
            val tag = single(text)
            assertEquals("$text row", id, tag.foodItem?.id)
            assertEquals("$text grams", grams, tag.amountGrams ?: Double.NaN, 0.01)
            assertSavedWithoutQuestion(tag, text)
        }
        assertEquals("gen148", single("un jugo en caja").foodItem?.id)
        assertEquals(200.0, single("un jugo en caja").amountGrams ?: Double.NaN, 0.01)
    }

    @Test
    fun `a drink keeps its serving next to other foods`() {
        val tags = resolve("pollo con arroz y un vaso de agua")
        assertWater(tags.single { it.foodItem?.id == "gen143" }, "gen143", 250.0, "water next to a plate")
        val tea = resolve("pan con palta y un té")
        assertEquals(2, tea.size)
        val lunch = resolve("almorcé un plato de porotos con riendas y un pan con palta, después un café con leche")
        val coffee = lunch.single { it.foodItem?.id == "gen145" }
        assertTrue("café con leche inside a meal ${coffee.amountGrams}", (coffee.amountGrams ?: 0.0) in 200.0..280.0)
        assertSavedWithoutQuestion(coffee, "café con leche inside a meal")
    }

    // --- Units (A-Q3): inputs #14, #15, #16 -------------------------------------------------------------------------

    @Test
    fun `the normalizer reads lt, lts, cc, cm3, kilogramos and the fractions of the kilo and the litre`() {
        val cases = mapOf(
            "1 lt de agua" to "1 l de agua",
            "2 lts de agua" to "2 l de agua",
            "500 cc de leche" to "500 ml de leche",
            "200 cm3 de leche" to "200 ml de leche",
            "200 cm\u00B3 de leche" to "200 ml de leche",
            "1 kilogramo de arroz" to "1 kg de arroz",
            "2 kilogramos de arroz" to "2 kg de arroz",
            "medio kilo de arroz" to "500 g de arroz",
            "cuarto de kilo de carne molida" to "250 g de carne molida",
            "un cuarto de kilo de carne molida" to "250 g de carne molida",
            "tres cuartos de kilo de arroz" to "750 g de arroz",
            "kilo y medio de arroz" to "1500 g de arroz",
            "un kilo y medio de arroz" to "1500 g de arroz",
            "medio litro de leche" to "500 ml de leche",
            "un cuarto de litro de leche" to "250 ml de leche",
            "tres cuartos de litro de agua" to "750 ml de agua",
            "litro y medio de agua" to "1500 ml de agua",
        )
        for ((input, expected) in cases) {
            assertEquals(input, expected, TextNormalizer.normalize(input))
        }
    }

    @Test
    fun `the parser turns those units into the right mass`() {
        fun parsed(text: String) = parseMealDescription(text).items.single()
        val milk = parsed("500 cc de leche")
        assertEquals("leche", milk.tag)
        assertEquals(515.0, milk.amountGrams ?: Double.NaN, 0.01)
        assertEquals(AmountIntent.EXPLICIT_MASS, milk.amountIntent)
        val juice = parsed("medio litro de jugo de naranja")
        assertEquals("jugo de naranja", juice.tag)
        assertEquals(500.0, juice.amountGrams ?: Double.NaN, 0.01)
        val beef = parsed("cuarto de kilo de carne molida")
        assertEquals("carne molida", beef.tag)
        assertEquals(250.0, beef.amountGrams ?: Double.NaN, 0.01)
        assertEquals(1000.0, parsed("1 kilogramo de arroz").amountGrams ?: Double.NaN, 0.01)
        assertEquals(1500.0, parsed("kilo y medio de arroz").amountGrams ?: Double.NaN, 0.01)
    }

    @Test
    fun `half a litre of orange juice is 500 g and 225 kcal, a cuarto de kilo of mince is 250 g`() {
        val juice = single("medio litro de jugo de naranja")
        assertEquals("gen103", juice.foodItem?.id)
        assertEquals(500.0, juice.amountGrams ?: Double.NaN, 0.01)
        assertEquals(225.0, juice.kcal(), 1.0)
        val milk = single("500 cc de leche")
        assertEquals("gen016", milk.foodItem?.id)
        assertEquals(515.0, milk.amountGrams ?: Double.NaN, 0.01)
        assertEquals(AmountIntent.EXPLICIT_MASS, milk.amountIntent)
        val beef = single("cuarto de kilo de carne molida")
        assertEquals("gen010", beef.foodItem?.id)
        assertEquals(250.0, beef.amountGrams ?: Double.NaN, 0.01)
    }

    // --- Juice and density of liquids (A-Q5): inputs #15, #19 --------------------------------------------------------

    @Test
    fun `a juice is counted in glasses, never in fruits`() {
        for ((text, grams) in listOf("un jugo de naranja" to 250.0, "un vaso de jugo de naranja" to 250.0, "2 jugos de naranja" to 500.0)) {
            val tag = single(text)
            assertEquals("$text row", "gen103", tag.foodItem?.id)
            assertEquals("$text grams", grams, tag.amountGrams ?: Double.NaN, 0.01)
            assertSavedWithoutQuestion(tag, text)
        }
        assertEquals("45 kcal per 100 ml, not per 60 g", 112.5, single("un jugo de naranja").kcal(), 1.0)
        // The vessel measures the juice: its name is not reduced to the fruit ("jugo de manzana" is not an apple).
        val apple = single("un vaso de jugo de manzana")
        assertEquals("jugo de manzana", apple.tag)
        assertEquals(250.0, apple.amountGrams ?: Double.NaN, 0.01)
    }

    @Test
    fun `a drink is a liquid whatever it is made of, and only a whole word counts`() {
        val liquid = SubjectivePortionEngine.FoodDensityCategory.LIQUID
        val dairy = SubjectivePortionEngine.FoodDensityCategory.DAIRY
        for (food in listOf("jugo de naranja", "jugo de manzana", "agua", "agua con gas", "té", "te negro", "café", "cerveza", "vino tinto", "coca cola", "gaseosa", "refresco", "caldo de pollo", "mate", "té sin azúcar", "bebida zero")) {
            assertEquals(food, liquid, SubjectivePortionEngine.detectDensityCategory(food))
        }
        for (food in listOf("leche", "café con leche", "batido de plátano", "licuado de frutilla", "leche con plátano", "yogurt")) {
            assertEquals(food, dairy, SubjectivePortionEngine.detectDensityCategory(food))
        }
        // Whole words only: "aguacate" holds "agua" and "tomate" holds "mate", and neither is a drink.
        for (food in listOf("aguacate", "tomate", "tomates", "naranja", "manzana", "arroz", "pollo")) {
            assertNotEquals(food, liquid, SubjectivePortionEngine.detectDensityCategory(food))
        }
        assertEquals(500.0, SubjectivePortionEngine.massFromVolumeMl(500.0, "jugo de naranja"), 0.0)
        assertEquals(515.0, SubjectivePortionEngine.massFromVolumeMl(500.0, "leche"), 0.01)
    }

    // --- Containers (A-Q4): inputs #5, #18 ---------------------------------------------------------------------------

    private fun container(expression: String): SubjectivePortionEngine.PortionResult {
        val result = SubjectivePortionEngine.resolve(expression)
        assertNotNull("no portion for '$expression'", result)
        return checkNotNull(result)
    }

    @Test
    fun `a can holds what that food comes in`() {
        val grams = mapOf(
            "una lata de coca cola" to 350.0,
            "una lata de cerveza" to 350.0,
            "una lata de bebida" to 350.0,
            "una lata de atún" to 170.0,
            "una lata de atún al agua" to 170.0,
            "una lata de jurel" to 170.0,
            "una lata de porotos" to 400.0,
            "una lata de garbanzos" to 400.0,
            "una lata de duraznos" to 180.0,
            "una lata chica de coca cola" to 250.0,
            "una latita de bebida" to 250.0,
            "una lata chica de atún" to 120.0,
        )
        for ((expression, expected) in grams) {
            assertEquals(expression, expected, container(expression).grams, 0.01)
        }
    }

    @Test
    fun `a bottle, a carton and a box hold what that food comes in`() {
        val grams = mapOf(
            "una botella de agua" to 500.0,
            "una botella de coca cola" to 500.0,
            "una botella de cerveza" to 330.0,
            "una botella de vino" to 750.0,
            // Milk and oil weigh what their density says, like "1 litro de leche" (1030 g).
            "una botella de leche" to 772.5,
            "un cartón de leche" to 1030.0,
            "una botella de aceite" to 675.0,
            "una botellita de agua" to 330.0,
            "un cartón de jugo" to 1000.0,
            "una caja de jugo" to 200.0,
            "una cajita de jugo" to 200.0,
            "una caja de cereal" to 500.0,
            "un vasito de agua" to 150.0,
        )
        for ((expression, expected) in grams) {
            assertEquals(expression, expected, container(expression).grams, 0.01)
        }
    }

    @Test
    fun `a container takes its count like a utensil does, digits or words`() {
        val two = container("2 latas de atún")
        assertEquals(340.0, two.grams, 0.01)
        assertEquals(2.0, two.relativeFactor, 0.0)
        assertEquals("container:lata", two.source)
        assertEquals(170.0, container("1 lata de atún").grams, 0.01)
        assertEquals(1000.0, container("2 botellas de agua").grams, 0.01)
        assertEquals(375.0, container("media botella de vino").grams, 0.01)
        assertEquals(250.0, container("media botella de agua").grams, 0.01)
        assertEquals(187.5, container("un cuarto de botella de vino").grams, 0.01)
        assertEquals(1050.0, container("3 latas de cerveza").grams, 0.01)
    }

    @Test
    fun `can by parsed meal reaches the table with its count and its class`() {
        val atun = parseMealDescription("dos latas de atún").items.single()
        assertEquals(2.0, atun.quantity, 0.0)
        assertEquals(340.0, atun.amountGrams ?: Double.NaN, 0.01)
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, atun.amountIntent)
        val cola = parseMealDescription("una lata de coca cola").items.single()
        assertEquals("coca cola", cola.tag)
        assertEquals(350.0, cola.amountGrams ?: Double.NaN, 0.01)
        val small = parseMealDescription("una lata chica de coca cola").items.single()
        assertEquals("coca cola", small.tag)
        assertEquals(250.0, small.amountGrams ?: Double.NaN, 0.01)
        val beans = single("una lata de porotos")
        assertEquals(400.0, beans.amountGrams ?: Double.NaN, 0.01)
    }

    @Test
    fun `two cans of soda are plausible, a bottle of wine is a bottle`() {
        val cans = single("dos latas de coca cola", branded = true)
        assertEquals(700.0, cans.amountGrams ?: Double.NaN, 0.01)
        assertSavedWithoutQuestion(cans, "dos latas de coca cola")
        val wine = single("una botella de vino")
        assertEquals("gen150", wine.foodItem?.id)
        assertEquals(750.0, wine.amountGrams ?: Double.NaN, 0.01)
    }

    // --- Catalog rows ------------------------------------------------------------------------------------------------

    private val newRowIds = (143..151).map { "gen$it" }

    @Test
    fun `the new rows continue the generic ids, each one once, as per 100 g drinks with a cited source`() {
        val ids = GENERIC_FOODS.map { it.id }
        assertEquals("ids are unique", ids.size, ids.toSet().size)
        val rows = newRowIds.map { id -> checkNotNull(GENERIC_FOODS.firstOrNull { it.id == id }) { "missing $id" } }
        for (row in rows) {
            assertEquals(row.id, "bebida", row.category)
            assertEquals(row.id, "ml", row.unit)
            assertEquals(row.id, "PER_100G_AS_SOLD", row.nutritionBasis)
            assertTrue("${row.id} serving ${row.servingSize}", row.servingSize in 100.0..350.0)
            assertFalse("${row.id} source", row.source.isNullOrBlank())
            assertFalse("${row.id} source record", row.sourceRecordId.isNullOrBlank())
            assertTrue("${row.id} plausible", FoodIdentity.hasPlausibleMacros(row))
            assertTrue("${row.id} verified", NutrientBasis.isVerified(row))
            // A zero-energy declaration is for foods up to 5 kcal, and every such food makes it.
            assertEquals(row.id, row.calories <= NutrientBasis.ZERO_ENERGY_MAX_KCAL, NutrientBasis.isZeroEnergy(row))
        }
        assertEquals(listOf("Agua", "Té sin azúcar", "Café con leche", "Bebida gaseosa", "Bebida zero/light", "Jugo en caja (néctar)", "Cerveza", "Vino", "Agua con gas"), rows.map { it.name })
        assertEquals(listOf(0.0, 1.0, 37.0, 42.0, 0.0, 45.0, 43.0, 85.0, 0.0), rows.map { it.calories })
    }

    @Test
    fun `every alias of the new rows finds its row`() {
        val expected = mapOf(
            "gen143" to listOf("agua", "agua mineral", "agua sin gas", "agua mineral sin gas", "agua de la llave", "agüita"),
            "gen151" to listOf("agua con gas", "agua mineral con gas", "agua gasificada"),
            "gen144" to listOf("té sin azúcar", "té negro", "agua de hierbas"),
            "gen145" to listOf("café con leche"),
            "gen146" to listOf("gaseosa", "bebida", "coca cola", "coca-cola", "cocacola", "sprite", "fanta", "pepsi"),
            "gen147" to listOf("coca cola zero", "coca zero", "bebida zero", "bebida light"),
            "gen148" to listOf("jugo en caja", "néctar"),
            "gen149" to listOf("cerveza", "chela", "schop"),
            "gen150" to listOf("vino", "vino tinto", "vino blanco", "tinto"),
            "gen059" to listOf("café solo", "espresso", "americano", "café sin azúcar"),
        )
        for ((id, aliases) in expected) {
            for (alias in aliases) {
                assertEquals("alias '$alias'", id, findFoodExactByNormalized(alias)?.id)
            }
        }
    }

    @Test
    fun `a food named without sugar is not the sugar, and the tea keeps its own name`() {
        assertNotEquals("gen144", findFoodByNormalized("azúcar")?.id)
        assertNotEquals("gen144", findFoodByNormalized("azucar")?.id)
        assertEquals("gen144", findFoodByNormalized("té sin azúcar")?.id)
        // A sugar spoon keeps the 4.5 g it always had: the tea's 240 ml is not its "standard portion".
        val sugar = parseMealDescription("un poco de azúcar").items.single()
        assertEquals(4.5, sugar.amountGrams ?: Double.NaN, 0.01)
    }

    @Test
    fun `a vessel measures a drink even when the drink is a catalog phrase`() {
        // These names are catalog phrases, which the parser used to keep whole and drop the vessel from.
        val skim = single("un vaso de leche descremada")
        assertEquals("gen046", skim.foodItem?.id)
        assertEquals(257.5, skim.amountGrams ?: Double.NaN, 0.05)
        val banana = single("un vaso de leche con plátano")
        assertEquals("cl020", banana.foodItem?.id)
        assertEquals(257.5, banana.amountGrams ?: Double.NaN, 0.05)
        val wine = single("una copa de vino tinto")
        assertEquals("gen150", wine.foodItem?.id)
        assertEquals(150.0, wine.amountGrams ?: Double.NaN, 0.01)
        assertEquals(125.0, single("medio vaso de agua").amountGrams ?: Double.NaN, 0.01)
        // A dish named after a drink is still a dish: no vessel is read into it.
        val dish = parseMealDescription("un plato de arroz con leche").items.single()
        assertEquals("arroz con leche", dish.tag)
    }

    @Test
    fun `a counted catalog phrase keeps its modifier word`() {
        // "light" is a size/diet modifier of yogurt, but "bebida light" is the catalog's zero-energy drink.
        val drink = parseMealDescription("una bebida light").items.single()
        assertEquals("bebida light", drink.tag)
        assertEquals(null, drink.modifierScale)
    }
}
