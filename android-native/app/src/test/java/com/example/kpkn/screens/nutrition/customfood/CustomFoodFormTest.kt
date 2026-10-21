package com.example.kpkn.screens.nutrition.customfood

import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.FoodIdentity
import com.example.kpkn.domain.nutrition.NutrientBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomFoodFormTest {
    private val ok = CustomFoodForm(
        name = "Galletas de avena", brand = "Marilan", servingAmount = "30", servingUnit = "g",
        calories = "135", protein = "2", carbs = "21", fats = "4,5",
    )

    private fun validate(form: CustomFoodForm, base: FoodItem? = null) = CustomFoodValidator.validate(form, "custom_1", base)

    @Test fun `un formulario completo por porcion da un alimento propio con su base y su procedencia`() {
        val result = validate(ok)
        assertTrue(result.errors.toString(), result.isValid)
        assertNull(result.warning)
        val food = result.food!!
        assertEquals("custom_1", food.id)
        assertEquals("Galletas de avena", food.name)
        assertEquals("Marilan", food.brand)
        assertTrue(food.isCustom)
        assertFalse(food.isAiInferred)
        assertEquals("custom", food.source)
        assertEquals("PER_SERVING", food.nutritionBasis)
        assertEquals(30.0, food.servingSize, 0.0)
        assertEquals("g", food.unit)
        assertEquals(30.0, NutrientBasis.grams(food), 0.0)
        assertEquals(4.5, food.fats, 0.0)
        assertNull(food.sourceRecordId)
        assertTrue("el registro de comidas lo acepta", NutrientBasis.isVerified(food))
    }

    @Test fun `por 100 g los nutrientes se refieren a 100 g y la porcion es la ración por defecto`() {
        val food = validate(ok.copy(basis = CustomFoodBasis.PER_100G, calories = "450", protein = "7", carbs = "70", fats = "15")).food!!
        assertEquals("PER_100G_AS_SOLD", food.nutritionBasis)
        assertEquals(100.0, NutrientBasis.grams(food), 0.0)
        assertEquals(30.0, food.servingSize, 0.0)
        assertEquals(30.0, food.portionGrams!!, 0.0)
    }

    @Test fun `la coma y el punto valen como decimales y se rechazan signos letras y notacion cientifica`() {
        assertEquals(4.5, CustomFoodValidator.parseNumber("4,5")!!, 0.0)
        assertEquals(4.5, CustomFoodValidator.parseNumber(" 4.5 ")!!, 0.0)
        assertEquals(0.5, CustomFoodValidator.parseNumber(",5")!!, 0.0)
        listOf("", "abc", "-1", "+2", "1e3", "NaN", "Infinity", "1,2,3", "4.5g").forEach {
            assertNull("«$it»", CustomFoodValidator.parseNumber(it))
        }
    }

    @Test fun `los obligatorios vacios dan error pero un cero explicito vale`() {
        val result = validate(CustomFoodForm(name = " ", servingAmount = ""))
        val fields = result.errors.keys
        assertTrue(fields.containsAll(listOf(
            CustomFoodField.NAME, CustomFoodField.SERVING, CustomFoodField.CALORIES,
            CustomFoodField.PROTEIN, CustomFoodField.CARBS, CustomFoodField.FATS,
        )))
        assertNull(result.food)
        assertTrue(validate(ok.copy(protein = "0", carbs = "0", fats = "0", calories = "0")).isValid)
    }

    @Test fun `no se aceptan negativos, letras ni valores fuera de rango`() {
        val result = validate(ok.copy(calories = "-5", protein = "abc", carbs = "1000000", servingAmount = "0"))
        assertEquals(
            setOf(CustomFoodField.CALORIES, CustomFoodField.PROTEIN, CustomFoodField.CARBS, CustomFoodField.SERVING),
            result.errors.keys,
        )
        assertFalse(result.isValid)
        assertNull(result.food)
        assertTrue(validate(ok.copy(servingAmount = "6000")).errors.containsKey(CustomFoodField.SERVING))
        assertTrue(validate(ok.copy(name = "x".repeat(81))).errors.containsKey(CustomFoodField.NAME))
    }

    @Test fun `un codigo de barras valido se guarda canonico y uno erroneo es un error del campo`() {
        assertEquals("0036000291452", validate(ok.copy(barcode = "036000291452")).food!!.sourceRecordId)
        assertEquals("4006381333931", validate(ok.copy(barcode = " 4006-3813 33931")).food!!.sourceRecordId)
        assertTrue(validate(ok.copy(barcode = "4006381333932")).errors.containsKey(CustomFoodField.BARCODE))
        assertTrue(validate(ok.copy(barcode = "123")).errors.containsKey(CustomFoodField.BARCODE))
        assertTrue("el código es opcional", validate(ok.copy(barcode = "")).isValid)
    }

    @Test fun `kcal que no cuadran con los macros avisan pero no bloquean`() {
        val result = validate(ok.copy(calories = "300"))
        assertTrue(result.isValid)
        assertNotNull(result.warning)
        assertTrue(result.warning!!.contains("no cuadran"))
        assertTrue("con tanta diferencia lo dice", validate(ok.copy(calories = "900")).warning!!.contains("podría no aceptar"))
        assertNull("dentro de la tolerancia no avisa", validate(ok.copy(calories = "150")).warning)
    }

    @Test fun `fibra o azucar mayores que los carbohidratos avisan sin bloquear`() {
        val result = validate(ok.copy(fiber = "30"))
        assertTrue(result.isValid)
        assertTrue(result.warning!!.contains("fibra"))
        assertTrue(validate(ok.copy(sugar = "30")).warning!!.contains("azúcar"))
    }

    @Test fun `por 100 g los macros no pueden sumar mas de 100 g ni usar mililitros`() {
        val per100 = ok.copy(basis = CustomFoodBasis.PER_100G, calories = "500", protein = "60", carbs = "50", fats = "5")
        assertTrue(validate(per100).errors.containsKey(CustomFoodField.PROTEIN))
        assertTrue(validate(per100.copy(calories = "1000", protein = "5", carbs = "5", fats = "5")).errors.containsKey(CustomFoodField.CALORIES))
        val ml = validate(ok.copy(basis = CustomFoodBasis.PER_100G, servingUnit = "ml"))
        assertTrue(ml.errors.containsKey(CustomFoodField.BASIS))
        assertTrue("por porción sí vale en ml", validate(ok.copy(servingUnit = "ml")).isValid)
    }

    @Test fun `fibra azucar y sodio opcionales se guardan y los vacios no inventan nada`() {
        val withExtras = validate(ok.copy(fiber = "2", sugar = "6,5", sodiumMg = "120")).food!!
        assertEquals(2.0, withExtras.carbBreakdown!!.fiber, 0.0)
        assertEquals(6.5, withExtras.carbBreakdown!!.sugar, 0.0)
        assertEquals(120.0, withExtras.micronutrients.single { it.name == "Sodio" }.amount, 0.0)
        val bare = validate(ok).food!!
        assertNull(bare.carbBreakdown)
        assertTrue(bare.micronutrients.isEmpty())
    }

    @Test fun `un alimento de energia nula como el agua se marca y el registro lo acepta`() {
        val water = validate(ok.copy(name = "Agua mineral", calories = "0", protein = "0", carbs = "0", fats = "0")).food!!
        assertTrue(water.tags.contains(NutrientBasis.ZERO_ENERGY_TAG))
        assertTrue(FoodIdentity.hasPlausibleMacros(water))
        assertTrue(NutrientBasis.isVerified(water))
    }

    @Test fun `editar conserva lo que el formulario no toca y recalcula el resto`() {
        val original = validate(ok).food!!.copy(usageCount = 7, lastUsedAt = "2026-10-20", normalizedName = "viejo")
        val form = CustomFoodValidator.fromFood(original)
        assertEquals("Galletas de avena", form.name)
        assertEquals("4,5", form.fats)
        assertEquals("135", form.calories)
        val edited = validate(form.copy(name = "Galletas de avena y miel"), base = original).food!!
        assertEquals(7, edited.usageCount)
        assertEquals("2026-10-20", edited.lastUsedAt)
        assertEquals("Galletas de avena y miel", edited.name)
        assertNull("el nombre normalizado se recalcula al guardar", edited.normalizedName)
        assertEquals("custom_1", edited.id)
    }

    @Test fun `el formulario de un alimento guardado reproduce sus datos`() {
        val food = validate(ok.copy(barcode = "4006381333931", fiber = "1,5", sodiumMg = "90", basis = CustomFoodBasis.PER_100G,
            calories = "450", protein = "7", carbs = "70", fats = "15")).food!!
        val form = CustomFoodValidator.fromFood(food)
        assertEquals("4006381333931", form.barcode)
        assertEquals(CustomFoodBasis.PER_100G, form.basis)
        assertEquals("1,5", form.fiber)
        assertEquals("90", form.sodiumMg)
        assertEquals("", form.sugar)
        assertEquals(food.copy(), validate(form, base = food).food)
    }
}
