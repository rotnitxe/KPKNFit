package com.example.kpkn.data.food

import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.Micronutrient

/**
 * FoodDatabase — Static food catalog for KPKN Fit.
 * Mirrors data/foodDatabase.ts + data/foodDatabaseExpansion.ts from PWA.
 * Contains generic foods (90 items), plus Chilean-specific foods.
 */

fun buildFoodDatabase(): List<FoodItem> = GENERIC_FOODS + CHILEAN_FOODS

// ─── Generic Foods (serving 100g unless noted) ───────────────────────────────

val GENERIC_FOODS: List<FoodItem> = listOf(
    FoodItem(id = "gen001", name = "Manzana", servingSize = 100.0, unit = "g", calories = 52.0, protein = 0.3, carbs = 14.0, fats = 0.2),
    FoodItem(id = "gen002", name = "Plátano", servingSize = 100.0, unit = "g", calories = 89.0, protein = 1.1, carbs = 23.0, fats = 0.3),
    FoodItem(id = "gen003", name = "Pechuga de Pollo (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 165.0, protein = 31.0, carbs = 0.0, fats = 3.6, cookingWeightFactor = 0.75),
    FoodItem(id = "gen004", name = "Pechuga de Pollo (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 195.0, protein = 30.0, carbs = 0.0, fats = 7.8),
    FoodItem(id = "gen005", name = "Arroz Blanco (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 130.0, protein = 2.7, carbs = 28.0, fats = 0.3),
    FoodItem(id = "gen006", name = "Arroz Integral (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 111.0, protein = 2.6, carbs = 23.0, fats = 0.9),
    FoodItem(id = "gen007", name = "Huevo Entero (cocido)", servingSize = 50.0, unit = "g", calories = 77.0, protein = 6.3, carbs = 0.6, fats = 5.3),
    FoodItem(id = "gen008", name = "Clara de Huevo", servingSize = 100.0, unit = "g", calories = 52.0, protein = 11.0, carbs = 0.7, fats = 0.2),
    FoodItem(id = "gen009", name = "Salmón (crudo)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 208.0, protein = 20.0, carbs = 0.0, fats = 13.0, cookingWeightFactor = 0.78),
    FoodItem(id = "gen010", name = "Carne Molida (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 217.0, protein = 26.0, carbs = 0.0, fats = 11.0),
    FoodItem(id = "gen011", name = "Avena en Hojuelas", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 389.0, protein = 16.9, carbs = 66.0, fats = 6.9),
    FoodItem(id = "gen012", name = "Lentejas (cocidas)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 116.0, protein = 9.0, carbs = 20.0, fats = 0.4),
    FoodItem(id = "gen013", name = "Garbanzos (cocidos)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 139.0, protein = 7.0, carbs = 26.0, fats = 2.4),
    FoodItem(id = "gen014", name = "Palta (Aguacate)", servingSize = 100.0, unit = "g", calories = 160.0, protein = 2.0, carbs = 9.0, fats = 15.0),
    FoodItem(id = "gen015", name = "Aceite de Oliva", servingSize = 100.0, unit = "ml", calories = 884.0, protein = 0.0, carbs = 0.0, fats = 100.0),
    FoodItem(id = "gen016", name = "Leche Entera", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 61.0, protein = 3.2, carbs = 4.8, fats = 3.3),
    FoodItem(id = "gen017", name = "Yogurt Griego Natural", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 97.0, protein = 9.0, carbs = 3.9, fats = 5.0),
    FoodItem(id = "gen018", name = "Queso Cottage", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 98.0, protein = 11.0, carbs = 3.4, fats = 4.3),
    FoodItem(id = "gen019", name = "Pan Blanco", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 265.0, protein = 9.0, carbs = 49.0, fats = 3.2),
    FoodItem(id = "gen020", name = "Pan Integral", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 247.0, protein = 13.0, carbs = 41.0, fats = 3.4),
    FoodItem(id = "gen021", name = "Papa (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 87.0, protein = 1.9, carbs = 20.0, fats = 0.1),
    FoodItem(id = "gen022", name = "Brócoli (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 35.0, protein = 2.4, carbs = 7.2, fats = 0.4),
    FoodItem(id = "gen023", name = "Espinaca (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 23.0, protein = 2.9, carbs = 3.6, fats = 0.4),
    FoodItem(id = "gen024", name = "Zanahoria (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 41.0, protein = 0.9, carbs = 10.0, fats = 0.2),
    FoodItem(id = "gen025", name = "Almendras", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 579.0, protein = 21.0, carbs = 22.0, fats = 49.0),
    FoodItem(id = "gen026", name = "Tomate", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 18.0, protein = 0.9, carbs = 3.9, fats = 0.2),
    FoodItem(id = "gen027", name = "Cebolla", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 40.0, protein = 1.1, carbs = 9.0, fats = 0.1),
    FoodItem(id = "gen028", name = "Lomo de Cerdo (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 242.0, protein = 27.0, carbs = 0.0, fats = 14.0),
    FoodItem(id = "gen029", name = "Atún en lata (agua)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 116.0, protein = 26.0, carbs = 0.0, fats = 1.0),
    FoodItem(id = "gen030", name = "Quinoa (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 120.0, protein = 4.4, carbs = 21.0, fats = 1.9),
    FoodItem(id = "gen031", name = "Porotos negros (cocidos)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 132.0, protein = 8.9, carbs = 24.0, fats = 0.5),
    FoodItem(id = "gen032", name = "Frutillas", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 32.0, protein = 0.7, carbs = 8.0, fats = 0.3),
    FoodItem(id = "gen033", name = "Naranja", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 47.0, protein = 0.9, carbs = 12.0, fats = 0.1),
    FoodItem(id = "gen034", name = "Mantequilla de maní", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 588.0, protein = 25.0, carbs = 20.0, fats = 50.0),
    FoodItem(id = "gen035", name = "Posta Rosada (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 205.0, protein = 36.0, carbs = 0.0, fats = 6.0),
    FoodItem(id = "gen036", name = "Pimentón Rojo", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 31.0, protein = 1.0, carbs = 6.0, fats = 0.3),
    FoodItem(id = "gen037", name = "Pepino", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 15.0, protein = 0.7, carbs = 3.6, fats = 0.1),
    FoodItem(id = "gen038", name = "Champiñones (crudos)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 22.0, protein = 3.1, carbs = 3.3, fats = 0.3),
    FoodItem(id = "gen039", name = "Nueces", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 654.0, protein = 15.0, carbs = 14.0, fats = 65.0),
    FoodItem(id = "gen040", name = "Pasta (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 131.0, protein = 5.0, carbs = 25.0, fats = 1.1),
    FoodItem(id = "gen041", name = "Tofu", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 76.0, protein = 8.1, carbs = 1.9, fats = 4.8),
    FoodItem(id = "gen042", name = "Hummus", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 166.0, protein = 7.9, carbs = 15.0, fats = 9.6),
    FoodItem(id = "gen043", name = "Hígado de Pollo (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 167.0, protein = 24.0, carbs = 1.0, fats = 6.5,
        micronutrients = listOf(Micronutrient("Hierro", 11.6, "mg"), Micronutrient("Vitamina A", 3296.0, "µg"), Micronutrient("Vitamina B12", 16.9, "µg"))),
    FoodItem(id = "gen044", name = "Merluza (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 94.0, protein = 19.0, carbs = 0.0, fats = 1.2,
        micronutrients = listOf(Micronutrient("Selenio", 36.5, "µg"), Micronutrient("Potasio", 256.0, "mg"))),
    FoodItem(id = "gen045", name = "Pavo (pechuga cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 135.0, protein = 30.0, carbs = 0.0, fats = 0.7),
    FoodItem(id = "gen046", name = "Leche Descremada", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 34.0, protein = 3.4, carbs = 5.0, fats = 0.1,
        micronutrients = listOf(Micronutrient("Calcio", 122.0, "mg"))),
    FoodItem(id = "gen047", name = "Queso Cheddar", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 403.0, protein = 25.0, carbs = 1.3, fats = 33.0),
    FoodItem(id = "gen048", name = "Miel", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 304.0, protein = 0.0, carbs = 82.0, fats = 0.0),
    FoodItem(id = "gen049", name = "Mantequilla", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 717.0, protein = 0.9, carbs = 0.1, fats = 81.0),
    FoodItem(id = "gen050", name = "Cacahuates", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 567.0, protein = 26.0, carbs = 16.0, fats = 49.0),
    FoodItem(id = "gen051", name = "Castañas de cajú", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 553.0, protein = 18.0, carbs = 30.0, fats = 44.0),
    FoodItem(id = "gen052", name = "Semillas de Chía", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 486.0, protein = 17.0, carbs = 42.0, fats = 31.0,
        micronutrients = listOf(Micronutrient("Calcio", 631.0, "mg"), Micronutrient("Hierro", 7.7, "mg"))),
    FoodItem(id = "gen053", name = "Uva", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 69.0, protein = 0.7, carbs = 18.0, fats = 0.2,
        micronutrients = listOf(Micronutrient("Potasio", 191.0, "mg"))),
    FoodItem(id = "gen054", name = "Batata (camote)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 86.0, protein = 1.6, carbs = 20.0, fats = 0.1,
        micronutrients = listOf(Micronutrient("Vitamina A", 709.0, "µg"))),
    FoodItem(id = "gen055", name = "Arvejas (cocidas)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 84.0, protein = 5.4, carbs = 15.0, fats = 0.2),
    FoodItem(id = "gen056", name = "Frijoles rojos (cocidos)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 127.0, protein = 8.7, carbs = 28.0, fats = 0.5),
    FoodItem(id = "gen057", name = "Soya texturizada (seca)", brand = "Genérico", servingSize = 30.0, unit = "g", calories = 340.0, protein = 52.0, carbs = 18.0, fats = 1.0, cookingWeightFactor = 0.25,
        micronutrients = listOf(Micronutrient("Hierro", 8.8, "mg"), Micronutrient("Calcio", 350.0, "mg"))),
    FoodItem(id = "gen058", name = "Avena Instantánea", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 389.0, protein = 16.9, carbs = 66.0, fats = 6.9,
        micronutrients = listOf(Micronutrient("Hierro", 4.7, "mg"), Micronutrient("Magnesio", 177.0, "mg"))),
    FoodItem(id = "gen059", name = "Café (negro)", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 2.0, protein = 0.1, carbs = 0.0, fats = 0.0),
    FoodItem(id = "gen060", name = "Té Verde", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 1.0, protein = 0.0, carbs = 0.0, fats = 0.0),
    FoodItem(id = "gen061", name = "Cacao en polvo", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 228.0, protein = 20.0, carbs = 58.0, fats = 14.0,
        micronutrients = listOf(Micronutrient("Hierro", 13.9, "mg"), Micronutrient("Magnesio", 499.0, "mg"))),
    FoodItem(id = "gen062", name = "Dátiles", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 282.0, protein = 2.5, carbs = 75.0, fats = 0.4),
    FoodItem(id = "gen063", name = "Pasas", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 299.0, protein = 3.1, carbs = 79.0, fats = 0.5),
    FoodItem(id = "gen064", name = "Salsa de Tomate", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 32.0, protein = 1.6, carbs = 7.2, fats = 0.4),
    FoodItem(id = "gen065", name = "Mayonesa", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 680.0, protein = 1.1, carbs = 0.6, fats = 75.0),
)

// ─── Chilean Foods ───────────────────────────────────────────────────────────

val CHILEAN_FOODS: List<FoodItem> = listOf(
    FoodItem(id = "cl001", name = "Empanada de Pino", servingSize = 180.0, unit = "u", calories = 450.0, protein = 18.0, carbs = 35.0, fats = 26.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl002", name = "Completo Italiano", servingSize = 200.0, unit = "u", calories = 380.0, protein = 12.0, carbs = 32.0, fats = 22.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl003", name = "Pastel de Choclo", servingSize = 250.0, unit = "g", calories = 420.0, protein = 22.0, carbs = 40.0, fats = 18.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl004", name = "Cazuela", servingSize = 400.0, unit = "ml", calories = 350.0, protein = 25.0, carbs = 30.0, fats = 12.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl005", name = "Mote con Huesillo", servingSize = 300.0, unit = "ml", calories = 220.0, protein = 2.0, carbs = 55.0, fats = 0.5, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl006", name = "Sopaipillas", servingSize = 60.0, unit = "u", calories = 150.0, protein = 3.0, carbs = 20.0, fats = 7.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl007", name = "Porotos Granados", servingSize = 300.0, unit = "ml", calories = 380.0, protein = 16.0, carbs = 50.0, fats = 12.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl008", name = "Curanto", servingSize = 400.0, unit = "g", calories = 550.0, protein = 35.0, carbs = 45.0, fats = 22.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl009", name = "Chorrillana", servingSize = 400.0, unit = "g", calories = 750.0, protein = 30.0, carbs = 55.0, fats = 42.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl010", name = "Marraqueta", servingSize = 100.0, unit = "g", calories = 260.0, protein = 9.0, carbs = 50.0, fats = 2.5, tags = listOf("chileno")),
    FoodItem(id = "cl011", name = "Pisco Sour", servingSize = 150.0, unit = "ml", calories = 220.0, protein = 0.5, carbs = 18.0, fats = 0.0, tags = listOf("chileno")),
    FoodItem(id = "cl012", name = "Terremoto", servingSize = 300.0, unit = "ml", calories = 350.0, protein = 1.0, carbs = 45.0, fats = 0.0, tags = listOf("chileno")),
)

// ─── Search Aliases ──────────────────────────────────────────────────────────

val FOOD_ALIASES: Map<String, String> = mapOf(
    // Sinónimos comunes
    "manzana" to "manzana",
    "plátano" to "plátano", "banana" to "plátano", "cambur" to "plátano",
    "pollo" to "pechuga de pollo", "pechuga" to "pechuga de pollo",
    "arroz" to "arroz blanco", "arroz blanco" to "arroz blanco",
    "huevo" to "huevo entero", "huevos" to "huevo entero", "huevo cocido" to "huevo entero",
    "atún" to "atún en lata",
    "leche" to "leche entera",
    "pan" to "pan blanco",
    "salmon" to "salmón", "salmón" to "salmón",
    "carne" to "carne molida",
    "papa" to "papa", "papas" to "papa",
    "palta" to "palta", "aguacate" to "palta",
    "almonds" to "almendras", "almendra" to "almendras",
    "nuez" to "nueces", "nueces" to "nueces",
    "queso" to "queso cheddar", "cheddar" to "queso cheddar",
    "tomate" to "tomate",
    "pasta" to "pasta",
    "yogurt" to "yogurt griego natural",
    "tofu" to "tofu",
    "tuna" to "atún en lata",
    "oatmeal" to "avena",
    "avena" to "avena",
    "maní" to "cacahuates", "cacahuate" to "cacahuates", "peanut" to "cacahuates",
    "porotos" to "porotos negros", "frijoles" to "porotos negros",
    "lentejas" to "lentejas",
    "garbanzos" to "garbanzos",
    "trigo" to "avena",
    "café" to "café",
    "té" to "té verde",
)

// ─── Portion References (grams per unit) ─────────────────────────────────────

data class PortionRef(
    val refType: String,
    val grams: Double,
)

val PORTION_REFERENCES: List<PortionRef> = listOf(
    PortionRef("tablespoon", 15.0),
    PortionRef("teaspoon", 5.0),
    PortionRef("cup", 240.0),
    PortionRef("handful", 30.0),
    PortionRef("pinch", 1.0),
    PortionRef("glass", 250.0),
    PortionRef("slice", 30.0),
    PortionRef("can", 200.0),
    PortionRef("portion", 150.0),
    PortionRef("scoop", 30.0),
    PortionRef("palm", 80.0),
    PortionRef("fist", 100.0),
)

// ─── Lookup Helpers ──────────────────────────────────────────────────────────

fun findFoodByNormalized(text: String): FoodItem? {
    val normalized = text.trim().lowercase()
    val alias = FOOD_ALIASES[normalized] ?: normalized
    val allFoods = GENERIC_FOODS + CHILEAN_FOODS

    // Exact match
    allFoods.find { it.name.lowercase() == alias }?.let { return it }

    // Contains match
    allFoods.find { it.name.lowercase().contains(alias) }?.let { return it }

    // Reverse contains
    allFoods.find { alias.contains(it.name.lowercase()) }?.let { return it }

    return null
}

fun getGramsForReference(refType: String, foodItem: FoodItem? = null): Double {
    val base = PORTION_REFERENCES.find { it.refType == refType }?.grams ?: 100.0
    return when (refType) {
        "palm" -> when {
            foodItem != null && foodItem.protein > 20 -> 80.0 // meat palm
            else -> 100.0
        }
        "handful" -> when {
            foodItem != null && foodItem.fats > 30 -> 30.0 // nuts
            else -> 30.0
        }
        else -> base
    }
}
