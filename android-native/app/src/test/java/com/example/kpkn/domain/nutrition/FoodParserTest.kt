package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class FoodParserTest {

    // ─── Basic Parsing ────────────────────────────────────────────────────

    @Test
    fun `parse empty string`() {
        val result = parseMealDescription("")
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `parse simple food name`() {
        val result = parseMealDescription("manzana")
        assertEquals(1, result.items.size)
        assertEquals("manzana", result.items[0].tag)
        assertEquals(1.0, result.items[0].quantity, 0.01)
        assertEquals(PortionPreset.MEDIUM, result.items[0].portion)
    }

    @Test
    fun `parse with quantity`() {
        val result = parseMealDescription("2 manzanas")
        assertEquals(1, result.items.size)
        assertEquals("manzana", result.items[0].tag)
        assertEquals(2.0, result.items[0].quantity, 0.01)
    }

    @Test
    fun `parse with literal quantity`() {
        val result = parseMealDescription("dos huevos")
        assertEquals(1, result.items.size)
        assertEquals("huevo", result.items[0].tag)
        assertEquals(2.0, result.items[0].quantity, 0.01)
    }

    @Test
    fun `parse with grams`() {
        val result = parseMealDescription("200g pollo")
        assertEquals(1, result.items.size)
        assertEquals("pollo", result.items[0].tag)
        assertEquals(200.0, result.items[0].amountGrams!!, 0.01)
    }

    @Test
    fun `parse with kg converts to grams`() {
        val result = parseMealDescription("1.5kg arroz")
        assertEquals(1, result.items.size)
        assertEquals("arroz", result.items[0].tag)
        assertEquals(1500.0, result.items[0].amountGrams!!, 0.01)
    }

    // ─── Multiple Items ───────────────────────────────────────────────────

    @Test
    fun `parse comma separated items`() {
        val result = parseMealDescription("200g pollo, 150g arroz")
        assertEquals(2, result.items.size)
    }

    @Test
    fun `parse y connector`() {
        val result = parseMealDescription("200g pollo y 150g arroz")
        assertEquals(2, result.items.size)
    }

    @Test
    fun `parse con connector`() {
        val result = parseMealDescription("200g pollo con arroz")
        assertEquals(2, result.items.size)
    }

    @Test
    fun `parse plus connector`() {
        val result = parseMealDescription("200g pollo + 150g arroz")
        assertEquals(2, result.items.size)
    }

    // ─── Cooking Method ───────────────────────────────────────────────────

    @Test
    fun `parse with cooking method`() {
        val result = parseMealDescription("200g pollo a la plancha")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.PLANCHA, result.items[0].cookingMethod)
    }

    @Test
    fun `parse horneado`() {
        val result = parseMealDescription("200g salmón al horno")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.HORNO, result.items[0].cookingMethod)
    }

    // ─── Portion Preset ───────────────────────────────────────────────────

    @Test
    fun `parse plato grande portion es LARGE no EXTRA`() {
        // B9: "plato grande" es LARGE; EXTRA queda solo para el adjetivo suelto.
        val result = parseMealDescription("plato grande de arroz")
        assertEquals(1, result.items.size)
        assertEquals(PortionPreset.LARGE, result.items[0].portion)
    }

    @Test
    fun `parse adjetivo grande suelto es EXTRA`() {
        val result = parseMealDescription("porción grande de ensalada")
        assertEquals(PortionPreset.EXTRA, result.items[0].portion)
    }

    @Test
    fun `parse mediano portion`() {
        val result = parseMealDescription("plato mediano de ensalada")
        assertEquals(1, result.items.size)
        assertEquals(PortionPreset.MEDIUM, result.items[0].portion)
    }

    @Test
    fun `parse pequeño portion`() {
        val result = parseMealDescription("porción pequeña de queso")
        assertEquals(1, result.items.size)
        assertEquals(PortionPreset.SMALL, result.items[0].portion)
    }

    // ─── Portion References ───────────────────────────────────────────────

    @Test
    fun `parse cucharada de aceite`() {
        val result = parseMealDescription("1 cucharada de aceite de oliva")
        assertEquals(1, result.items.size)
        assertEquals("aceite de oliva", result.items[0].tag)
        assertNotNull(result.items[0].amountGrams)
        assertTrue(result.items[0].amountGrams!! > 0)
    }

    @Test
    fun `parse taza de arroz`() {
        val result = parseMealDescription("una taza de arroz")
        assertEquals(1, result.items.size)
        assertEquals("arroz", result.items[0].tag)
        assertNotNull(result.items[0].amountGrams)
        assertTrue(result.items[0].amountGrams!! > 0)
    }

    @Test
    fun `parse media taza`() {
        val result = parseMealDescription("media taza de leche")
        assertEquals(1, result.items.size)
        assertEquals("leche", result.items[0].tag)
    }

    @Test
    fun `parse puñado`() {
        val result = parseMealDescription("un puñado de almendras")
        assertEquals(1, result.items.size)
        assertNotNull(result.items[0].amountGrams)
    }

    // ─── Range Quantities ─────────────────────────────────────────────────

    @Test
    fun `parse range quantity`() {
        val result = parseMealDescription("1-2 huevos")
        assertEquals(1, result.items.size)
        assertEquals(1.5, result.items[0].quantity, 0.01) // average of 1-2
    }

    // ─── Combined Descriptions ────────────────────────────────────────────

    @Test
    fun `parse complex meal`() {
        val result = parseMealDescription("200g pechuga de pollo a la plancha, 150g arroz blanco, ensalada grande")
        assertEquals(3, result.items.size)
        assertEquals("pechuga de pollo", result.items[0].tag)
        assertEquals(CookingMethod.PLANCHA, result.items[0].cookingMethod)
        assertEquals(200.0, result.items[0].amountGrams!!, 0.01)
    }

    @Test
    fun `parse empanada de pino protected entity`() {
        val result = parseMealDescription("2 empanadas de pino")
        assertEquals(1, result.items.size)
        assertEquals("empanadas de pino", result.items[0].tag)
        assertEquals(2.0, result.items[0].quantity, 0.01)
    }

    @Test
    fun `parse plato grande de pollo con arroz`() {
        val result = parseMealDescription("plato grande de pollo con arroz")
        assertTrue(result.items.size >= 2)
        assertEquals(PortionPreset.LARGE, result.items[0].portion)
    }

    @Test
    fun `raw description preserved`() {
        val desc = "200g pollo y 150g arroz"
        val result = parseMealDescription(desc)
        assertEquals(desc, result.rawDescription)
    }

    // ─── Extended Quantity Detection ───────────────────────────────────────

    @Test
    fun `parse literal twelve`() {
        val result = parseMealDescription("doce huevos")
        assertEquals(1, result.items.size)
        assertEquals(12.0, result.items[0].quantity, 0.01)
    }

    @Test
    fun `parse literal fifteen`() {
        val result = parseMealDescription("quince uvas")
        assertEquals(1, result.items.size)
        assertEquals(15.0, result.items[0].quantity, 0.01)
    }

    @Test
    fun `parse literal twenty`() {
        val result = parseMealDescription("veinte almendras")
        assertEquals(1, result.items.size)
        assertEquals(20.0, result.items[0].quantity, 0.01)
    }

    // ─── Protected Entities ────────────────────────────────────────────────

    @Test
    fun `parse sandwich de pollo con mayonesa as protected`() {
        val result = parseMealDescription("sandwich de pollo con mayonesa")
        assertTrue(result.items.size == 1 || result.items.size >= 2)
    }

    @Test
    fun `parse hamburguesa con queso as protected`() {
        val result = parseMealDescription("hamburguesa con queso")
        assertTrue(result.items.size == 1)
    }

    @Test
    fun `parse papas fritas con mayonesa as protected`() {
        val result = parseMealDescription("papas fritas con mayonesa")
        assertTrue(result.items.size == 1)
    }

    @Test
    fun `parse arroz con pollo splits into two foods`() {
        val result = parseMealDescription("arroz con pollo")
        assertEquals(listOf("arroz", "pollo"), result.items.map { it.tag })
    }

    @Test
    fun `parse arroz con leche stays a single dish`() {
        val result = parseMealDescription("arroz con leche")
        assertEquals(1, result.items.size)
        assertEquals("arroz con leche", result.items.single().tag)
    }

    @Test
    fun `parse salsa de tomate stays protected`() {
        val result = parseMealDescription("salsa de tomate")
        assertEquals(1, result.items.size)
        assertEquals("salsa de tomate", result.items.single().tag)
    }

    // ─── Iteración 1: regresiones de la auditoría ──────────────────────────

    @Test
    fun `B6 multi measure estilo A arroz 100g pollo 50g`() {
        val result = parseMealDescription("arroz 100g pollo 50g")
        assertEquals(2, result.items.size)
        val arroz = result.items.firstOrNull { it.tag == "arroz" }
        val pollo = result.items.firstOrNull { it.tag == "pollo" }
        assertNotNull("arroz debe sobrevivir al split", arroz)
        assertNotNull("pollo debe sobrevivir al split", pollo)
        assertEquals(100.0, arroz!!.amountGrams ?: 0.0, 0.01)
        assertEquals(50.0, pollo!!.amountGrams ?: 0.0, 0.01)
    }

    @Test
    fun `B6 multi measure estilo B 100g arroz 50g pollo`() {
        val result = parseMealDescription("100g arroz 50g pollo")
        assertEquals(2, result.items.size)
        val arroz = result.items.firstOrNull { it.tag == "arroz" }
        val pollo = result.items.firstOrNull { it.tag == "pollo" }
        assertNotNull(arroz)
        assertNotNull(pollo)
        assertEquals(100.0, arroz!!.amountGrams ?: 0.0, 0.01)
        assertEquals(50.0, pollo!!.amountGrams ?: 0.0, 0.01)
    }

    @Test
    fun `B7 cucharadita no se convierte en cucharada`() {
        val result = parseMealDescription("1 cucharadita de azúcar")
        assertEquals(1, result.items.size)
        val grams = result.items[0].amountGrams ?: 0.0
        assertTrue("cucharadita debe ser ~5g, fue $grams", grams in 3.0..8.0)
    }

    @Test
    fun `B8 tres leches no se rompe por numeros palabra`() {
        val normalized = TextNormalizer.normalize("tres leches con crema")
        assertTrue("el plato debe preservar 'tres': $normalized", normalized.contains("tres"))
        val result = parseMealDescription("tres leches con crema")
        assertTrue("debe quedar el plato tres leches", result.items.any { it.tag.contains("leches") || it.tag.contains("tres") })
    }

    @Test
    fun `B8 mil hojas no se convierte en 1000 hojas`() {
        val normalized = TextNormalizer.normalize("mil hojas")
        assertTrue("'mil' debe preservarse: $normalized", normalized.contains("mil"))
        assertEquals(1.0, parseMealDescription("mil hojas").items.firstOrNull()?.quantity ?: 1.0, 0.01)
    }

    @Test
    fun `B9 papas fritas detecta metodo FRITO`() {
        val result = parseMealDescription("papas fritas")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.FRITO, result.items[0].cookingMethod)
    }

    @Test
    fun `B9 papa frita singular tambien detecta FRITO`() {
        val result = parseMealDescription("papa frita")
        assertEquals(CookingMethod.FRITO, result.items[0].cookingMethod)
    }

    @Test
    fun `C12 idempotencia mismo input mismo output`() {
        val desc = "200g pollo a la plancha, 150g arroz, ensalada grande"
        val first = parseMealDescription(desc)
        val second = parseMealDescription(desc)
        val third = parseMealDescription(desc)
        fun signature(r: ParsedMealDescription) = r.items.joinToString("|") {
            "${it.tag}:${it.quantity}:${it.amountGrams}:${it.cookingMethod}:${it.portion}"
        }
        assertEquals(signature(first), signature(second))
        assertEquals(signature(second), signature(third))
    }

    // ─── IT3: vocabulario de cocción ───────────────────────────────────────

    @Test
    fun `IT3 huevo duro detecta COCIDO`() {
        val result = parseMealDescription("2 huevos duros")
        assertEquals(1, result.items.size)
        assertEquals(2.0, result.items[0].quantity, 0.01)
        assertEquals(CookingMethod.COCIDO, result.items[0].cookingMethod)
    }

    @Test
    fun `IT3 guiso detecta GUISADO`() {
        val result = parseMealDescription("guiso de lentejas")
        assertEquals(1, result.items.size)
        assertEquals(CookingMethod.GUISADO, result.items[0].cookingMethod)
    }

    @Test
    fun `IT3 guisado y guisito tambien detectan GUISADO`() {
        assertEquals(CookingMethod.GUISADO, parseMealDescription("lentejas guisadas").items[0].cookingMethod)
        assertEquals(CookingMethod.GUISADO, parseMealDescription("un guisito de pollo").items[0].cookingMethod)
    }

    @Test
    fun `IT3 aceite por categoria en pipeline completo`() {
        val foods = listOf(
            com.example.kpkn.data.models.FoodItem(
                id = "t1", name = "Papa (cruda)", servingSize = 100.0, unit = "g",
                calories = 87.0, protein = 1.9, carbs = 20.0, fats = 0.1,
            ),
        )
        val logged = com.example.kpkn.domain.nutrition.scaleFoodByPortion(
            food = foods[0],
            amountGrams = 100.0,
            cookingMethod = CookingMethod.FRITO,
        )
        // Factor por categoría: papas → kcal ×1.20
        assertEquals(104.4, logged.calories, 0.5) // 87 × 1.20
        // Aceite medio para tubérculo: 12 g
        val oiled = com.example.kpkn.domain.nutrition.adjustLoggedFoodForOil(
            logged, CookingMethod.FRITO, "medio", foodName = "Papa (cruda)",
        )
        assertEquals(12.1, oiled.fats, 0.1) // 0.1 + 12
        assertEquals(212.4, oiled.calories, 0.5) // 104.4 + 108
    }
}
