package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.PortionPreset
import org.junit.Assert.*
import org.junit.Test

/** Human-language contracts: expectations do not come from the parser's dataset. */
class NaturalLanguageReliabilityTest {
    private fun grams(text: String): Double = parseMealDescription(text).items.single().amountGrams!!

    @Test(timeout = 5000) fun longNoisyInputDoesNotLoseItsFinalFoodOrHang() {
        val text = "x".repeat(580) + ", arroz"
        assertEquals("arroz", parseMealDescription(text).items.last().tag)
        val numberNoise = "ciento ".repeat(80) + "gramos de arroz"
        assertTrue(TextNormalizer.normalize(numberNoise).isNotBlank())
    }

    @Test fun decimalCommaAndPointExpressTheSameMass() {
        for ((comma, point, expected) in listOf(
            Triple("0,5 kg arroz", "0.5 kg arroz", 500.0),
            Triple("12,5 g queso", "12.5 g queso", 12.5),
        )) {
            assertEquals(expected, grams(comma), 0.001)
            assertEquals(grams(point), grams(comma), 0.001)
        }
        assertEquals(grams("1.5 tazas de arroz"), grams("1,5 tazas de arroz"), 0.001)
        assertEquals(2, parseMealDescription("arroz, pollo").items.size)
    }

    @Test fun measurePositionSurvivesUntilBrandAndPackageResolution() {
        val prefix = parseMealDescription("100 g Hallulla Ideal").items.single()
        val suffix = parseMealDescription("Hallulla Ideal 1kg").items.single()
        assertFalse(prefix.amountIsTrailing)
        assertTrue(suffix.amountIsTrailing)
        assertEquals(100.0, prefix.amountGrams!!, 0.1)
        assertEquals(1000.0, suffix.amountGrams!!, 0.1)
        // Position alone does not assert package intent or erase an unbranded
        // explicit amount. The resolver needs the product's actual brand.
        val rice = parseMealDescription("arroz 200g").items.single()
        assertTrue(rice.amountIsTrailing)
        assertEquals(200.0, rice.amountGrams!!, 0.1)
        val mixed = parseMealDescription("100g Hallulla Ideal y Hallulla Ideal 1kg").items
        assertEquals(2, mixed.size)
        assertEquals(listOf(false, true), mixed.map { it.amountIsTrailing })
        assertEquals(listOf(100.0, 1000.0), mixed.map { it.amountGrams })
    }

    @Test fun writtenCompoundNumbersAreOneQuantity() {
        for ((text, expected) in listOf(
            "ciento cincuenta gramos de arroz" to 150.0,
            "treinta y dos gramos de queso" to 32.0,
            "mil doscientos cincuenta gramos de arroz" to 1250.0,
        )) {
            val item = parseMealDescription(text).items.single()
            assertEquals(expected, item.amountGrams!!, 0.001)
            assertEquals(1.0, item.quantity, 0.001)
            assertEquals(AmountIntent.EXPLICIT_MASS, item.amountIntent)
        }
    }

    @Test fun lexicalizedNumberWordsRemainFoodNamesAcrossNormalizationAndParsing() {
        val dessert = parseMealDescription("tres leches").items.single()
        assertEquals("tres leches", dessert.tag)
        assertEquals(1.0, dessert.quantity, 0.01)
        assertEquals("tres leches", TextNormalizer.normalize("tres leches"))
        val measured = parseMealDescription("100g tres leches").items.single()
        assertEquals("tres leches", measured.tag)
        assertEquals(100.0, measured.amountGrams!!, 0.01)
        val actualCount = parseMealDescription("2 tres leches").items.single()
        assertEquals("tres leches", actualCount.tag)
        assertEquals(2.0, actualCount.quantity, 0.01)
        assertEquals("mil hojas", parseMealDescription("mil hojas").items.single().tag)
        for ((words, number) in listOf("dos" to 2.0, "cuatro" to 4.0)) {
            val count = parseMealDescription("$words quesos").items.single()
            val digits = parseMealDescription("${number.toInt()} quesos").items.single()
            assertEquals(number, count.quantity, 0.01)
            assertEquals(digits.tag, count.tag)
            assertEquals("queso", count.tag)
        }
        val recipe = parseMealDescription("pizza cuatro quesos").items.single()
        assertEquals("pizza cuatro quesos", recipe.tag)
        assertEquals(1.0, recipe.quantity, 0.01)
        assertEquals("pizza cuatro quesos", TextNormalizer.normalize("pizza cuatro quesos"))
        val twoRecipes = parseMealDescription("dos pizzas cuatro quesos").items.single()
        assertEquals(2.0, twoRecipes.quantity, 0.01)
        assertEquals("pizzas cuatro quesos", twoRecipes.tag)
        // Genuine counts remain quantities when the words are not a dish name.
        assertEquals(3.0, parseMealDescription("tres vasos de leche").items.single().quantity, 0.01)
    }

    @Test fun cupFractionsAndCountsScaleOneUnitExactlyOnce() {
        // A cup is the declared 240 ml measure, not the 180 g dairy bowl prior.
        // FDA household metric equivalents; milk's current density estimate is 1.03 g/ml.
        assertEquals(240.0 * 1.03, grams("una taza de leche"), 0.1)
        assertEquals(81.0, grams("una taza de avena"), 0.1)
        for (food in listOf("leche", "arroz", "avena")) {
            val one = grams("una taza de $food")
            assertEquals(one * 0.5, grams("media taza de $food"), 0.1)
            assertEquals(one * 0.25, grams("1/4 de taza de $food"), 0.1)
            assertEquals(one * 0.25, grams("un cuarto de taza de $food"), 0.1)
            assertEquals(one * 1.5, grams("1 1/2 tazas de $food"), 0.1)
            assertEquals(one * 2.0, grams("dos tazas de $food"), 0.1)
        }
    }

    @Test fun volumeAndItsEquivalentMassHaveOneCanonicalUnit() {
        for ((food, expectedMass) in listOf("leche" to 247.2, "agua" to 240.0, "avena" to 81.0)) {
            val cup = grams("una taza de $food")
            val volume = parseMealDescription("240 ml de $food").items.single()
            assertEquals(expectedMass, cup, 0.1)
            assertEquals(cup, volume.amountGrams!!, 0.1)
            assertEquals(cup, grams("${expectedMass} g de $food"), 0.1)
            assertEquals("ml", volume.unitId)
        }
        assertEquals(123.6, grams("0,12 l de leche"), 0.1)
        assertEquals(247.2, grams("una taza de leche sin azúcar"), 0.1)
        assertEquals(grams("una taza de leche de avena"), grams("240 ml de leche de avena"), 0.1)
        assertTrue(grams("240 ml de leche de avena") > 200.0)
    }

    @Test fun householdCountsAreNotMultipliedTwice() {
        for (food in listOf("marraquetas", "hallullas", "huevos")) {
            val two = parseMealDescription("2 $food").items.single()
            val four = parseMealDescription("4 $food").items.single()
            assertEquals(2.0, two.quantity, 0.001)
            assertEquals(4.0, four.quantity, 0.001)
            assertEquals(2 * two.amountGrams!!, four.amountGrams!!, 0.1)
        }
        assertEquals(2 * grams("una cucharada colmada de azúcar"), grams("2 cucharadas colmadas de azúcar"), 0.1)
        assertEquals(0.5 * grams("una cucharadita de sal"), grams("media cucharadita de sal"), 0.1)
    }

    @Test fun portionsOfCountableFoodsStayPortions() {
        val breadSlice = grams("una rebanada de pan")
        assertTrue("bread slice $breadSlice", breadSlice in 20.0..45.0)
        assertEquals(2 * breadSlice, grams("dos rebanadas de pan"), 0.1)
        val appleSlice = grams("una rodaja de manzana")
        assertTrue("apple slice $appleSlice", appleSlice in 10.0..40.0)
        assertEquals(2 * appleSlice, grams("dos rodajas de manzana"), 0.1)
        assertTrue(appleSlice < grams("una manzana"))
    }

    @Test fun scoopSurvivesNoiseNormalizationAndBindsItsFoodAndFraction() {
        assertEquals("scoop", TextNormalizer.normalize("scoop"))
        assertEquals("scoops", TextNormalizer.normalize("scoops"))
        for ((text, quantity) in listOf(
            "un scoop whey" to 1.0,
            "dos scoops whey" to 2.0,
            "medio scoop whey" to 0.5,
            "0,5 scoops de whey" to 0.5,
        )) {
            val item = parseMealDescription(text).items.single()
            assertEquals(text, "whey", item.tag)
            assertEquals(quantity, item.quantity, 0.01)
            assertEquals(30.0 * quantity, item.amountGrams!!, 0.1)
            assertEquals("scoop", item.unitId)
            assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, item.amountIntent)
        }
    }

    @Test fun cookedOatsDoNotUseDryCerealMass() {
        val dry = grams("una taza de avena seca")
        val cooked = parseMealDescription("una taza de avena cocida").items.single()
        assertEquals(CookingMethod.COCIDO, cooked.cookingMethod)
        assertTrue("cooked ${cooked.amountGrams} vs dry $dry", cooked.amountGrams!! > dry * 2)
    }

    @Test fun differentlyCookedMentionsAreNeverCollapsed() {
        val items = parseMealDescription("100g pollo frito y 100g pollo cocido").items
        assertEquals(2, items.size)
        assertEquals(setOf(CookingMethod.FRITO, CookingMethod.COCIDO), items.map { it.cookingMethod }.toSet())
        assertTrue(items.all { it.amountGrams == 100.0 })
    }

    @Test fun omittedAndExplicitAmountsSurviveInEitherOrder() {
        for (text in listOf("arroz y 100g arroz", "100g arroz y arroz")) {
            val items = parseMealDescription(text).items
            assertEquals(2, items.size)
            assertEquals(100.0, items.single { it.amountIntent == AmountIntent.EXPLICIT_MASS }.amountGrams!!, 0.01)
            assertNull(items.single { it.amountIntent == AmountIntent.UNSPECIFIED }.amountGrams)
        }
        assertEquals(2, parseMealDescription("arroz y arroz").items.size)
        assertEquals(150.0, grams("100g arroz y 50g arroz"), 0.01)
    }

    @Test fun negationDoesNotDependOnMentionOrder() {
        for (text in listOf("arroz y sin arroz", "sin arroz y arroz")) {
            val items = parseMealDescription(text).items
            assertEquals(2, items.size)
            assertEquals(1, items.count { it.isExcluded })
            assertEquals(1, items.count { !it.isExcluded })
        }
        val excluded = parseMealDescription("pollo sin arroz ni pan").items.filter { it.isExcluded }
        assertEquals(setOf("arroz", "pan"), excluded.map { it.tag }.toSet())
    }

    @Test fun ingredientExclusionOnlyModifiesItsAdjacentMention() {
        val items = parseMealDescription("completo italiano sin mayonesa y arroz").items
        assertEquals(setOf("mayonesa"), items.single { it.tag.contains("completo") }.excludedIngredients)
        assertTrue(items.single { it.tag == "arroz" }.excludedIngredients.isEmpty())
        assertTrue(items.single { it.tag == "mayonesa" }.isExcluded)
        val repeated = parseMealDescription("100g pollo sin aceite y 100g pollo").items.filterNot { it.isExcluded }
        assertEquals(2, repeated.size)
        assertEquals(1, repeated.count { "aceite" in it.excludedIngredients })
        val comma = parseMealDescription("completo italiano, sin mayonesa y arroz").items
        assertEquals(items.map { it.tag to it.excludedIngredients }, comma.map { it.tag to it.excludedIngredients })
        val sandwich = parseMealDescription("sándwich de jamón y queso, sin queso").items
        assertEquals(2, sandwich.size)
        assertEquals(setOf("queso"), sandwich.first().excludedIngredients)
        assertTrue(sandwich.last().isExcluded)
    }

    @Test fun productAttributesRemainAttachedToFoodIdentity() {
        for (text in listOf("leche sin lactosa", "pan sin gluten", "yogurt sin azúcar")) {
            val item = parseMealDescription(text).items.single()
            assertFalse(item.isExcluded)
            assertEquals(FoodIdentity.normalize(text), FoodIdentity.normalize(item.effectiveFoodQuery()))
        }
        assertTrue(parseMealDescription("sin azúcar").items.single().isExcluded)
    }

    @Test fun anUnmeasuredTailIsNotSilentlyDropped() {
        val items = parseMealDescription("arroz 100g pollo 50g ensalada").items
        assertEquals(listOf("arroz", "pollo", "ensalada"), items.map { it.tag })
        assertEquals(100.0, items[0].amountGrams!!, 0.01)
        assertEquals(50.0, items[1].amountGrams!!, 0.01)
        assertNull(items[2].amountGrams)
        val trailingPreparation = parseMealDescription("arroz 100g pollo 50g cocido").items
        assertEquals(listOf("arroz", "pollo"), trailingPreparation.map { it.tag })
        assertNull(trailingPreparation[0].cookingMethod)
        assertEquals(CookingMethod.COCIDO, trailingPreparation[1].cookingMethod)
        assertEquals(50.0, trailingPreparation[1].amountGrams!!, 0.01)
    }

    @Test fun narrationAndExplicitRepairsDoNotCreateFoods() {
        assertEquals(listOf("arroz", "pollo"), parseMealDescription("hoy almorcé arroz con pollo").items.map { it.tag })
        assertEquals(listOf("pollo", "fideos"), parseMealDescription("pollo con arroz, perdón, fideos").items.map { it.tag })
        val denied = parseMealDescription("no comí arroz").items.single()
        assertEquals("arroz", denied.tag)
        assertTrue(denied.isExcluded)
        val correction = parseMealDescription("no arroz sino fideos").items
        assertEquals(listOf("arroz", "fideos"), correction.map { it.tag })
        assertTrue(correction[0].isExcluded)
        assertFalse(correction[1].isExcluded)
        val countRepair = parseMealDescription("Dos huevos, perdón, uno, con pan").items
        assertEquals(listOf("huevo", "pan"), countRepair.map { it.tag })
        assertEquals(1.0, countRepair[0].quantity, 0.01)
        assertEquals(50.0, countRepair[0].amountGrams!!, 0.1)
        val elliptical = parseMealDescription("Medio vaso de jugo y medio de leche").items
        assertEquals(listOf("jugo", "leche"), elliptical.map { it.tag })
        assertEquals(grams("medio vaso de leche"), elliptical[1].amountGrams!!, 0.1)
        assertEquals("vaso", elliptical[1].unitId)
    }

    @Test fun aLocalSizeDoesNotResizeTheOtherFood() {
        val items = parseMealDescription("ensalada grande y arroz").items
        assertEquals(PortionPreset.EXTRA, items[0].portion)
        assertEquals(PortionPreset.MEDIUM, items[1].portion)
        val largePlate = parseMealDescription("un plato grande de arroz").items.single()
        assertEquals(PortionPreset.LARGE, largePlate.portion)
        assertEquals(AmountIntent.RESOLVED_SUBJECTIVE, largePlate.amountIntent)
        assertTrue("utensil mass must not be scaled a second time", largePlate.amountGrams!! in 200.0..300.0)
    }
}
