package com.example.kpkn.data.food

import com.example.kpkn.data.models.CarbBreakdown
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.Micronutrient
import com.example.kpkn.domain.nutrition.FoodIdentity
import com.example.kpkn.domain.nutrition.FoodSearchRanker
import com.example.kpkn.domain.nutrition.FoodStapleOntology
import com.example.kpkn.domain.nutrition.FoodState
import com.example.kpkn.domain.nutrition.TextKeys

/**
 * FoodDatabase — Static food catalog for KPKN Fit.
 * Mirrors data/foodDatabase.ts + data/foodDatabaseExpansion.ts from PWA.
 * Contains generic foods (90 items), plus Chilean-specific foods.
 */

fun buildFoodDatabase(context: android.content.Context? = null): List<FoodItem> =
    ALL_FOODS + BrandedEnergyKcalCatalog.load(context) + BrandedSnackCatalog.load(context)

// ─── Provenance of the static catalog (WP-S10) ───────────────────────────────

/**
 * The `source` of a static row that declares none: written by the KPKN team, not imported from a dataset. The resolver's index reports such
 * a row as "LOCAL" (see FoodIndex), so the label never changes who is a catalog row.
 */
internal const val STATIC_CATALOG_SOURCE = "KPKN curated"

/** The `datasetVersion` of a static row that declares none: the review of the static catalog it belongs to (year-month). */
private const val STATIC_CATALOG_VERSION = "static-2026-10"

private const val BASIS_PER_SERVING = "PER_SERVING"
private const val BASIS_PER_100G = "PER_100G_AS_SOLD"

/**
 * Fills in what a row of the static catalog leaves unsaid, so that every row states where it comes from ([GENERIC_FOODS] and
 * [CHILEAN_FOODS] pass through here). A value a row declares itself is never touched: the USDA, recipe and per-100 g rows of
 * WP-N5, WP-S6 and WP-D1 keep their own `source`, `sourceRecordId` and `nutritionBasis`.
 *  - `source` and `datasetVersion`: [STATIC_CATALOG_SOURCE] and [STATIC_CATALOG_VERSION] when blank.
 *  - `nutritionBasis`: [FoodItem] defaults to PER_SERVING and no row of the catalog writes that default on purpose, so a row that
 *    still says it is a row that said nothing. When its serving is the 100 g (or 100 ml) its table was written for, its macros ARE
 *    per 100 g: PER_100G_AS_SOLD, and that 100 is a denominator, not a portion anybody eats. A serving of any other size or unit
 *    ("Empanada de Pino", 180 u) is a real serving and stays PER_SERVING.
 */
internal fun List<FoodItem>.withCuratedProvenance(): List<FoodItem> = map { it.curated() }

private fun FoodItem.curated(): FoodItem = copy(
    source = source?.takeIf { it.isNotBlank() } ?: STATIC_CATALOG_SOURCE,
    datasetVersion = datasetVersion?.takeIf { it.isNotBlank() } ?: STATIC_CATALOG_VERSION,
    nutritionBasis = if (nutritionBasis.isBlank() || nutritionBasis == BASIS_PER_SERVING) staticBasis() else nutritionBasis,
)

private fun FoodItem.staticBasis(): String {
    val per100 = servingSize == 100.0 && (unit.equals("g", ignoreCase = true) || unit.equals("ml", ignoreCase = true))
    return if (per100) BASIS_PER_100G else BASIS_PER_SERVING
}

// ─── Generic Foods (serving 100g unless noted) ───────────────────────────────

val GENERIC_FOODS: List<FoodItem> = listOf(
    FoodItem(id = "gen001", name = "Manzana", servingSize = 100.0, unit = "g", calories = 52.0, protein = 0.3, carbs = 14.0, fats = 0.2),
    FoodItem(id = "gen002", name = "Plátano", servingSize = 100.0, unit = "g", calories = 89.0, protein = 1.1, carbs = 23.0, fats = 0.3, searchAliases = listOf("banana", "cambur")),
    // Fichas de pechuga alineadas al asset USDA (food_nutrient.csv):
    // cruda = FDC 2646170 (22,5 P/1,9 G por 100 g crudos), cocida = FDC 331960
    // braised (166 kcal/32,1 P/3,2 G por 100 g cocidos). La gen003 anterior
    // tenía densidad cocida (31 g/100 g) rotulada como cruda y duplicaba la
    // concentración al dividir por el rendimiento.
    FoodItem(id = "gen003", name = "Pechuga de Pollo (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 106.0, protein = 22.5, carbs = 0.0, fats = 1.9, cookingWeightFactor = 0.75),
    FoodItem(id = "gen004", name = "Pechuga de Pollo (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 166.0, protein = 32.1, carbs = 0.0, fats = 3.2),
    FoodItem(id = "gen005", name = "Arroz Blanco (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 130.0, protein = 2.7, carbs = 28.0, fats = 0.3),
    // WP-S11: la única ficha de arroz integral cocido. FDC 169704 (Rice, brown, long-grain, cooked): 123 kcal, 2,7 P, 26 C, 0,9 G por 100 g,
    // redondeados como la antigua gen136, que se fusionó aquí (111 kcal de esta fila contradecían los 123 de la otra). El id viejo sigue
    // resolviendo por LEGACY_FOOD_ID_REDIRECTS: los aprendizajes y plantillas que lo guardaron no se pierden.
    FoodItem(
        id = "gen006",
        name = "Arroz Integral (cocido)",
        brand = "Genérico",
        category = "cereal",
        servingSize = 100.0,
        unit = "g",
        nutritionBasis = "PER_100G_COOKED",
        source = "KPKN Curated (FDC 169704, brown rice long-grain cooked)",
        sourceRecordId = "169704", // Rice, brown, long-grain, cooked
        calories = 123.0,
        protein = 2.7,
        carbs = 26.0,
        fats = 0.9,
        foodState = "COOKED",
        searchAliases = listOf("arroz integral", "arroz integral cocido"),
    ),
    FoodItem(id = "gen007", name = "Huevo Entero (cocido)", servingSize = 50.0, unit = "g", calories = 77.0, protein = 6.3, carbs = 0.6, fats = 5.3),
    FoodItem(id = "gen008", name = "Clara de Huevo", servingSize = 100.0, unit = "g", calories = 52.0, protein = 11.0, carbs = 0.7, fats = 0.2),
    FoodItem(id = "gen009", name = "Salmón (crudo)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 208.0, protein = 20.0, carbs = 0.0, fats = 13.0, cookingWeightFactor = 0.78),
    FoodItem(id = "gen010", name = "Carne Molida (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 217.0, protein = 26.0, carbs = 0.0, fats = 11.0),
    FoodItem(id = "gen011", name = "Avena en Hojuelas", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 389.0, protein = 16.9, carbs = 66.0, fats = 6.9),
    FoodItem(id = "gen012", name = "Lentejas (cocidas)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 116.0, protein = 9.0, carbs = 20.0, fats = 0.4),
    FoodItem(id = "gen013", name = "Garbanzos (cocidos)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 139.0, protein = 7.0, carbs = 26.0, fats = 2.4),
    FoodItem(id = "gen014", name = "Palta (Aguacate)", servingSize = 100.0, unit = "g", calories = 160.0, protein = 2.0, carbs = 9.0, fats = 15.0),
    // USDA SR Legacy records in data/usdaFoodsOffline.json: 171413 (oil),
    // 171265 (whole milk). Values per 100 g; milk macros rounded to one decimal.
    // unit="ml" is a display/household measure, never the nutrient denominator.
    FoodItem(id = "gen015", name = "Aceite de Oliva", servingSize = 100.0, unit = "ml", nutritionBasis = "PER_100G_AS_SOLD", source = "USDA SR Legacy (rounded)", sourceRecordId = "171413", calories = 884.0, protein = 0.0, carbs = 0.0, fats = 100.0),
    FoodItem(id = "gen016", name = "Leche Entera", brand = "Genérico", servingSize = 100.0, unit = "ml", nutritionBasis = "PER_100G_AS_SOLD", source = "USDA SR Legacy (rounded)", sourceRecordId = "171265", calories = 61.0, protein = 3.2, carbs = 4.8, fats = 3.3),
    FoodItem(id = "gen017", name = "Yogurt Griego Natural", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 97.0, protein = 9.0, carbs = 3.9, fats = 5.0),
    FoodItem(id = "gen018", name = "Queso Cottage", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 98.0, protein = 11.0, carbs = 3.4, fats = 4.3),
    // WP-S10: sus macros son por 100 g (PER_100G_AS_SOLD) y por eso esos 100 g ya no cuentan como porción; el pan que come la gente (un pan, "2 panes" =
    // 200 g en EverydayMealCorpusTest) sí pesa 100 g, y la fila lo declara en vez de heredarlo del denominador.
    FoodItem(id = "gen019", name = "Pan Blanco", brand = "Genérico", servingSize = 100.0, unit = "g", portionGrams = 100.0, portionUnit = "unidad", calories = 265.0, protein = 9.0, carbs = 49.0, fats = 3.2),
    FoodItem(id = "gen020", name = "Pan Integral", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 265.0, protein = 9.5, carbs = 45.0, fats = 4.2, nutritionBasis = "PER_100G_AS_SOLD"),
    FoodItem(id = "gen021", name = "Papa (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 87.0, protein = 1.9, carbs = 20.0, fats = 0.1),
    FoodItem(id = "gen022", name = "Brócoli (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 35.0, protein = 2.4, carbs = 7.2, fats = 0.4),
    FoodItem(id = "gen023", name = "Espinaca (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 23.0, protein = 2.9, carbs = 3.6, fats = 0.4),
    FoodItem(id = "gen024", name = "Zanahoria (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 41.0, protein = 0.9, carbs = 10.0, fats = 0.2),
    FoodItem(id = "gen025", name = "Almendras", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 579.0, protein = 21.0, carbs = 22.0, fats = 49.0),
    FoodItem(id = "gen026", name = "Tomate", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 18.0, protein = 0.9, carbs = 3.9, fats = 0.2, source = "USDA SR Legacy (rounded)", sourceRecordId = "170457", nutritionBasis = "PER_100G_AS_SOLD"),
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
    FoodItem(id = "gen038", name = "Champiñones (crudos)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 22.0, protein = 3.1, carbs = 3.3, fats = 0.3, searchAliases = listOf("champiñón", "champinon", "hongo", "hongos")),
    FoodItem(id = "gen039", name = "Nueces", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 654.0, protein = 15.0, carbs = 14.0, fats = 65.0, searchAliases = listOf("nuez")),
    FoodItem(id = "gen040", name = "Pasta (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 131.0, protein = 5.0, carbs = 25.0, fats = 1.1),
    FoodItem(id = "gen041", name = "Tofu", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 76.0, protein = 8.1, carbs = 1.9, fats = 4.8),
    FoodItem(id = "gen042", name = "Hummus", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 166.0, protein = 7.9, carbs = 15.0, fats = 9.6),
    FoodItem(id = "gen043", name = "Hígado de Pollo (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 167.0, protein = 24.0, carbs = 1.0, fats = 6.5,
        micronutrients = listOf(Micronutrient("Hierro", 11.6, "mg"), Micronutrient("Vitamina A", 3296.0, "µg"), Micronutrient("Vitamina B12", 16.9, "µg"))),
    FoodItem(id = "gen044", name = "Merluza (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 94.0, protein = 19.0, carbs = 0.0, fats = 1.2,
        micronutrients = listOf(Micronutrient("Selenio", 36.5, "µg"), Micronutrient("Potasio", 256.0, "mg"))),
    FoodItem(id = "gen045", name = "Pavo (pechuga cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 135.0, protein = 30.0, carbs = 0.0, fats = 0.7),
    FoodItem(id = "gen046", name = "Leche Descremada", brand = "Genérico", servingSize = 100.0, unit = "ml", nutritionBasis = "PER_100G_AS_SOLD", source = "USDA SR Legacy (rounded)", sourceRecordId = "171269", calories = 34.0, protein = 3.4, carbs = 5.0, fats = 0.1,
        micronutrients = listOf(Micronutrient("Calcio", 122.0, "mg"))),
    FoodItem(id = "gen047", name = "Queso Cheddar", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 403.0, protein = 25.0, carbs = 1.3, fats = 33.0),
    FoodItem(id = "gen048", name = "Miel", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 304.0, protein = 0.0, carbs = 82.0, fats = 0.0),
    FoodItem(id = "gen049", name = "Mantequilla", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 717.0, protein = 0.9, carbs = 0.1, fats = 81.0),
    FoodItem(id = "gen050", name = "Cacahuates", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 567.0, protein = 26.0, carbs = 16.0, fats = 49.0, searchAliases = listOf("maní")),
    FoodItem(id = "gen051", name = "Castañas de cajú", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 553.0, protein = 18.0, carbs = 30.0, fats = 44.0),
    FoodItem(id = "gen052", name = "Semillas de Chía", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 486.0, protein = 17.0, carbs = 42.0, fats = 31.0,
        micronutrients = listOf(Micronutrient("Calcio", 631.0, "mg"), Micronutrient("Hierro", 7.7, "mg"))),
    FoodItem(id = "gen053", name = "Uva", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 69.0, protein = 0.7, carbs = 18.0, fats = 0.2,
        micronutrients = listOf(Micronutrient("Potasio", 191.0, "mg"))),
    FoodItem(id = "gen054", name = "Batata (camote)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 86.0, protein = 1.6, carbs = 20.0, fats = 0.1,
        micronutrients = listOf(Micronutrient("Vitamina A", 709.0, "µg"))),
    FoodItem(id = "gen055", name = "Arvejas (cocidas)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 84.0, protein = 5.4, carbs = 15.0, fats = 0.2),
    FoodItem(id = "gen056", name = "Frijoles rojos (cocidos)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 127.0, protein = 8.7, carbs = 28.0, fats = 0.5),
    // servingSize=100g (valores por 100g seca).
    // cookingWeightFactor ≈ hidratación típica (100 g seca → ~350 g hidratada).
    FoodItem(id = "gen057", name = "Soya texturizada (seca)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 340.0, protein = 52.0, carbs = 33.0, fats = 1.2,
        cookingWeightFactor = 3.5,
        searchAliases = listOf("soya texturizada", "pvt", "proteina vegetal", "carne vegetal", "soja texturizada"),
        micronutrients = listOf(Micronutrient("Hierro", 8.8, "mg"), Micronutrient("Calcio", 350.0, "mg"))),
    FoodItem(id = "gen058", name = "Avena Instantánea", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 389.0, protein = 16.9, carbs = 66.0, fats = 6.9,
        micronutrients = listOf(Micronutrient("Hierro", 4.7, "mg"), Micronutrient("Magnesio", 177.0, "mg"))),
    FoodItem(id = "gen059", name = "Café (negro)", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 2.0, protein = 0.1, carbs = 0.0, fats = 0.0, caffeineMg = 40.0, tags = listOf("zero_energy"), searchAliases = listOf("café sin azúcar", "café solo", "espresso", "americano")),
    FoodItem(id = "gen060", name = "Té Verde", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 1.0, protein = 0.0, carbs = 0.0, fats = 0.0, caffeineMg = 15.0),
    FoodItem(id = "gen061", name = "Cacao en polvo", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 228.0, protein = 20.0, carbs = 58.0, fats = 14.0,
        micronutrients = listOf(Micronutrient("Hierro", 13.9, "mg"), Micronutrient("Magnesio", 499.0, "mg"))),
    FoodItem(id = "gen062", name = "Dátiles", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 282.0, protein = 2.5, carbs = 75.0, fats = 0.4),
    FoodItem(id = "gen063", name = "Pasas", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 299.0, protein = 3.1, carbs = 79.0, fats = 0.5),
    FoodItem(id = "gen064", name = "Salsa de Tomate", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 32.0, protein = 1.6, carbs = 7.2, fats = 0.4),
    FoodItem(id = "gen065", name = "Mayonesa", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 680.0, protein = 1.1, carbs = 0.6, fats = 75.0),
    // ─── Verduras ────────────────────────────────────────────────────────────
    FoodItem(id = "gen066", name = "Lechuga", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 15.0, protein = 1.4, carbs = 2.9, fats = 0.2, source = "USDA SR Legacy (rounded)", sourceRecordId = "169249", nutritionBasis = "PER_100G_AS_SOLD", searchAliases = listOf("lechuga", "ensalada verde")),
    FoodItem(id = "gen067", name = "Repollo", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 25.0, protein = 1.3, carbs = 5.8, fats = 0.1, searchAliases = listOf("repollo", "col", "cabbage")),
    FoodItem(id = "gen068", name = "Coliflor", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 25.0, protein = 1.9, carbs = 5.0, fats = 0.3),
    FoodItem(id = "gen069", name = "Apio", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 16.0, protein = 0.7, carbs = 3.0, fats = 0.2),
    FoodItem(id = "gen070", name = "Betarraga", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 43.0, protein = 1.6, carbs = 10.0, fats = 0.2, searchAliases = listOf("betarraga", "remolacha", "beet")),
    FoodItem(id = "gen071", name = "Choclo Desgranado", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 86.0, protein = 3.2, carbs = 19.0, fats = 1.2, searchAliases = listOf("choclo", "maíz", "maiz", "corn")),
    FoodItem(id = "gen072", name = "Zapallo", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 26.0, protein = 1.0, carbs = 6.5, fats = 0.1, searchAliases = listOf("zapallo", "calabaza", "pumpkin")),
    FoodItem(id = "gen073", name = "Acelga", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 19.0, protein = 1.8, carbs = 3.7, fats = 0.2),
    FoodItem(id = "gen074", name = "Puerro", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 61.0, protein = 1.5, carbs = 14.0, fats = 0.3, searchAliases = listOf("puerro", "ajo porro")),
    // ─── Frutas ───────────────────────────────────────────────────────────────
    FoodItem(id = "gen075", name = "Kiwi", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 61.0, protein = 1.1, carbs = 15.0, fats = 0.5),
    FoodItem(id = "gen076", name = "Melón", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 34.0, protein = 0.8, carbs = 8.2, fats = 0.2),
    FoodItem(id = "gen077", name = "Sandía", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 30.0, protein = 0.6, carbs = 7.6, fats = 0.2),
    FoodItem(id = "gen078", name = "Piña", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 50.0, protein = 0.5, carbs = 13.0, fats = 0.1, searchAliases = listOf("piña", "anana", "ananás")),
    FoodItem(id = "gen079", name = "Pera", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 57.0, protein = 0.4, carbs = 15.0, fats = 0.1),
    FoodItem(id = "gen080", name = "Durazno", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 39.0, protein = 0.9, carbs = 10.0, fats = 0.3, searchAliases = listOf("durazno", "melocotón", "melocoton")),
    FoodItem(id = "gen081", name = "Ciruela", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 46.0, protein = 0.7, carbs = 11.0, fats = 0.3),
    FoodItem(id = "gen082", name = "Arándanos", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 57.0, protein = 0.7, carbs = 14.0, fats = 0.3, searchAliases = listOf("arándanos", "arandanos", "blueberries")),
    FoodItem(id = "gen083", name = "Mango", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 60.0, protein = 0.8, carbs = 15.0, fats = 0.4),
    // ─── Lácteos ──────────────────────────────────────────────────────────────
    // WP-D1: 98 kcal por 100 g es el queso cottage (gen018), no un queso fresco. El queso fresco chileno de etiqueta ronda 130-177 kcal
    // (Colun 7802920000701: 177 kcal, 12,6 P, 4,5 C, 12,1 G; La Vaquita 7804613390533: 177 kcal; Tilos light: 172 kcal).
    FoodItem(
        id = "gen084",
        name = "Queso Fresco",
        brand = "Genérico",
        category = "lacteo",
        servingSize = 50.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (etiquetas de queso fresco chileno, Colun 177 kcal)",
        sourceRecordId = "7802920000701",
        calories = 177.0,
        protein = 12.6,
        carbs = 4.5,
        fats = 12.1,
        searchAliases = listOf("queso fresco", "queso blanco"),
    ),
    FoodItem(id = "gen085", name = "Leche Semidescremada", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 46.0, protein = 3.2, carbs = 4.8, fats = 1.5, micronutrients = listOf(Micronutrient("Calcio", 120.0, "mg"))),
    FoodItem(id = "gen086", name = "Crema de Leche", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 292.0, protein = 2.2, carbs = 2.8, fats = 30.0, searchAliases = listOf("crema", "nata", "crema de leche")),
    FoodItem(id = "gen087", name = "Yogurt Natural", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 59.0, protein = 3.5, carbs = 4.7, fats = 3.3, searchAliases = listOf("yogurt natural", "yogur")),
    FoodItem(id = "gen088", name = "Queso Mantecoso", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 350.0, protein = 22.0, carbs = 1.5, fats = 28.0, searchAliases = listOf("queso mantecoso", "queso amarillo")),
    // ─── Panes y Cereales ────────────────────────────────────────────────────
    FoodItem(id = "gen089", name = "Pan de Molde", brand = "Genérico", servingSize = 25.0, unit = "u", calories = 67.0, protein = 2.2, carbs = 12.0, fats = 0.9, searchAliases = listOf("pan de molde", "molde", "pan lactal", "toast")),
    FoodItem(id = "gen090", name = "Tortilla de Trigo", brand = "Genérico", servingSize = 40.0, unit = "u", calories = 120.0, protein = 3.2, carbs = 22.0, fats = 2.5, searchAliases = listOf("tortilla", "wrap")),
    FoodItem(id = "gen091", name = "Granola", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 471.0, protein = 10.0, carbs = 64.0, fats = 20.0),
    FoodItem(id = "gen092", name = "Arroz Parbolizado", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 129.0, protein = 2.8, carbs = 28.0, fats = 0.4, searchAliases = listOf("arroz parbolizado", "arroz vaporizado")),
    // ─── Carnes y Embutidos ───────────────────────────────────────────────────
    FoodItem(id = "gen093", name = "Filete de Vacuno", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 187.0, protein = 28.0, carbs = 0.0, fats = 8.0, cookingWeightFactor = 0.75, searchAliases = listOf("filete", "lomo", "vacuno", "bife")),
    FoodItem(id = "gen094", name = "Jamón Cocido", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 107.0, protein = 17.0, carbs = 2.0, fats = 3.7, searchAliases = listOf("jamón", "jamon", "jamón de pavo", "fiambre")),
    FoodItem(id = "gen095", name = "Salchicha Tipo Viena", brand = "Genérico", servingSize = 50.0, unit = "u", calories = 145.0, protein = 6.5, carbs = 2.1, fats = 12.5, searchAliases = listOf("salchicha", "vienesa", "hotdog")),
    FoodItem(id = "gen096", name = "Camarón (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 99.0, protein = 21.0, carbs = 0.9, fats = 1.1, searchAliases = listOf("camarón", "camaron", "shrimp")),
    FoodItem(id = "gen097", name = "Tilapia (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 128.0, protein = 26.0, carbs = 0.0, fats = 2.7),
    // ─── Aceites y Grasas ────────────────────────────────────────────────────
    FoodItem(id = "gen098", name = "Aceite de Coco", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 862.0, protein = 0.0, carbs = 0.0, fats = 100.0, searchAliases = listOf("aceite de coco", "coconut oil")),
    FoodItem(id = "gen099", name = "Aceite Vegetal", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 884.0, protein = 0.0, carbs = 0.0, fats = 100.0, searchAliases = listOf("aceite vegetal", "aceite de girasol", "aceite de maíz")),
    // ─── Condimentos ─────────────────────────────────────────────────────────
    FoodItem(id = "gen100", name = "Ketchup", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 100.0, protein = 1.0, carbs = 27.0, fats = 0.1, searchAliases = listOf("ketchup", "cátsup", "catsup", "salsa de tomate dulce")),
    FoodItem(id = "gen101", name = "Mostaza", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 60.0, protein = 3.7, carbs = 5.8, fats = 3.3),
    FoodItem(id = "gen102", name = "Salsa de Soya", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 53.0, protein = 8.1, carbs = 4.9, fats = 0.6, searchAliases = listOf("salsa de soya", "soya", "sillao")),
    // ─── Bebidas ─────────────────────────────────────────────────────────────
    FoodItem(id = "gen103", name = "Jugo de Naranja Natural", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 45.0, protein = 0.7, carbs = 10.0, fats = 0.2, searchAliases = listOf("jugo de naranja", "jugo naranja", "orange juice")),
    FoodItem(id = "gen104", name = "Leche de Almendras", brand = "Genérico", servingSize = 100.0, unit = "ml", calories = 17.0, protein = 0.6, carbs = 0.6, fats = 1.5, searchAliases = listOf("leche de almendras", "leche vegetal")),
    // ─── Suplementos ─────────────────────────────────────────────────────────
    FoodItem(id = "gen105", name = "Proteína en Polvo (Whey)", brand = "Genérico", servingSize = 30.0, unit = "g", calories = 120.0, protein = 24.0, carbs = 3.0, fats = 2.0, searchAliases = listOf("whey", "proteína en polvo", "suero de leche", "proteína whey", "whey protein")),
    FoodItem(id = "gen106", name = "Creatina Monohidrato", brand = "Genérico", servingSize = 5.0, unit = "g", calories = 0.0, protein = 0.0, carbs = 0.0, fats = 0.0, creatineG = 5.0, searchAliases = listOf("creatina", "creatine")),
    // ─── Variantes de cocción (top 30 alimentos) ─────────────────────────────
    // Pollo
    FoodItem(id = "gen003c", name = "Pechuga de Pollo (plancha)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 173.0, protein = 31.0, carbs = 0.0, fats = 3.4, searchAliases = listOf("pollo a la plancha", "pechuga plancha")),
    FoodItem(id = "gen003f", name = "Pechuga de Pollo (frita)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 223.0, protein = 30.0, carbs = 0.0, fats = 10.9, searchAliases = listOf("pollo frito", "pechuga frita")),
    FoodItem(id = "gen003h", name = "Pechuga de Pollo (horno)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 168.0, protein = 31.0, carbs = 0.0, fats = 3.5, searchAliases = listOf("pollo al horno", "pechuga al horno")),
    FoodItem(id = "gen003v", name = "Pechuga de Pollo (vapor)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 160.0, protein = 31.0, carbs = 0.0, fats = 3.2, searchAliases = listOf("pollo al vapor", "pechuga vapor")),
    FoodItem(id = "gen003p", name = "Pechuga de Pollo (parrilla)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 170.0, protein = 31.0, carbs = 0.0, fats = 3.3, searchAliases = listOf("pollo a la parrilla", "pechuga parrilla")),
    // Trutro/muslo — FDC 171077 (crudo) / 172385 (asado sin piel, cocido)
    FoodItem(id = "gen003t", name = "Trutro de Pollo (crudo)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 177.0, protein = 19.7, carbs = 0.0, fats = 10.9, cookingWeightFactor = 0.75, searchAliases = listOf("trutro de pollo", "trutro", "muslo de pollo", "muslo", "pierna de pollo")),
    FoodItem(id = "gen003tc", name = "Trutro de Pollo (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 195.0, protein = 24.0, carbs = 0.0, fats = 10.2, searchAliases = listOf("trutro cocido", "muslo cocido", "pierna cocida")),
    FoodItem(id = "gen003a", name = "Ala de Pollo (cocida)", brand = "Genérico", servingSize = 80.0, unit = "u", calories = 203.0, protein = 18.0, carbs = 0.0, fats = 14.0, searchAliases = listOf("ala de pollo", "alitas", "alita", "ala")),
    FoodItem(id = "gen003e", name = "Pollo Entero (asado)", brand = "Genérico", servingSize = 200.0, unit = "g", calories = 165.0, protein = 27.0, carbs = 0.0, fats = 6.0, nutritionBasis = "PER_100G_COOKED", searchAliases = listOf("pollo entero", "pollo asado entero")),
    // Vacuno
    FoodItem(id = "gen010p", name = "Carne Molida (plancha)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 228.0, protein = 26.0, carbs = 0.0, fats = 10.5, searchAliases = listOf("carne a la plancha")),
    FoodItem(id = "gen010f", name = "Carne Molida (frita)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 293.0, protein = 25.0, carbs = 0.0, fats = 15.4, searchAliases = listOf("carne frita")),
    // Arroz
    FoodItem(id = "gen005c", name = "Arroz Blanco (crudo)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 360.0, protein = 7.0, carbs = 80.0, fats = 0.6, cookingWeightFactor = 2.8, searchAliases = listOf("arroz crudo")),
    // Huevo
    FoodItem(id = "gen007f", name = "Huevo Entero (frito)", servingSize = 50.0, unit = "g", calories = 90.0, protein = 6.3, carbs = 0.6, fats = 7.0, searchAliases = listOf("huevo frito", "huevos fritos")),
    FoodItem(id = "gen007c", name = "Huevo Entero (crudo)", servingSize = 50.0, unit = "g", calories = 72.0, protein = 6.3, carbs = 0.4, fats = 5.0, searchAliases = listOf("huevo crudo")),
    FoodItem(id = "gen007r", name = "Huevo Entero (revuelto)", servingSize = 50.0, unit = "g", calories = 95.0, protein = 6.5, carbs = 0.8, fats = 7.5, searchAliases = listOf("huevos revueltos", "huevo revuelto")),
    // Salmón
    FoodItem(id = "gen009p", name = "Salmón (plancha)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 218.0, protein = 20.0, carbs = 0.0, fats = 12.4, searchAliases = listOf("salmon a la plancha")),
    FoodItem(id = "gen009h", name = "Salmón (horno)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 212.0, protein = 20.0, carbs = 0.0, fats = 12.7, searchAliases = listOf("salmon al horno")),
    // Papa
    FoodItem(id = "gen021f", name = "Papa (frita)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 312.0, protein = 3.4, carbs = 41.0, fats = 15.0, searchAliases = listOf("papa frita", "papas fritas", "patatas fritas", "papa frita de plato", "papas fritas de plato")),
    FoodItem(id = "gen021h", name = "Papa (horno)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 93.0, protein = 2.5, carbs = 21.0, fats = 0.1, searchAliases = listOf("papa al horno", "papas al horno")),
    FoodItem(id = "gen021p", name = "Papa (puré)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 110.0, protein = 2.0, carbs = 18.0, fats = 3.5, searchAliases = listOf("pure de papa", "pure")),
    // Pasta
    FoodItem(id = "gen040c", name = "Pasta (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 371.0, protein = 13.0, carbs = 75.0, fats = 1.5, cookingWeightFactor = 2.2, searchAliases = listOf("pasta cruda", "fideos crudos", "fideos secos", "tallarines secos")),
    // Lentejas
    FoodItem(id = "gen012c", name = "Lentejas (crudas)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 352.0, protein = 25.0, carbs = 60.0, fats = 1.1, cookingWeightFactor = 2.5, searchAliases = listOf("lentejas crudas")),
    // Cerdo
    FoodItem(id = "gen028p", name = "Lomo de Cerdo (plancha)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 254.0, protein = 27.0, carbs = 0.0, fats = 13.3, searchAliases = listOf("cerdo a la plancha")),
    // Atún
    FoodItem(id = "gen029e", name = "Atún en lata (aceite)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 200.0, protein = 26.0, carbs = 0.0, fats = 10.0, searchAliases = listOf("atun en aceite")),

    // ─── Estados de hidratación ──────────────────────────────────────────────
    FoodItem(id = "gen012h", name = "Lentejas (hidratadas)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 116.0, protein = 9.0, carbs = 20.0, fats = 0.4, searchAliases = listOf("lentejas remojadas", "lentejas hidratadas")),
    FoodItem(id = "gen013h", name = "Garbanzos (hidratados)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 139.0, protein = 7.0, carbs = 26.0, fats = 2.4, searchAliases = listOf("garbanzos remojados", "garbanzos hidratados")),
    FoodItem(id = "gen040h", name = "Pasta (hidratada/cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 131.0, protein = 5.0, carbs = 25.0, fats = 1.1, searchAliases = listOf("pasta cocida", "fideos cocidos")),
    FoodItem(id = "gen005h", name = "Arroz (hidratado/cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 130.0, protein = 2.7, carbs = 28.0, fats = 0.3, searchAliases = listOf("arroz cocido", "arroz graneado")),
    FoodItem(id = "gen057h", name = "Soya texturizada (hidratada)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 120.0, protein = 18.0, carbs = 12.0, fats = 0.4, searchAliases = listOf("soya hidratada", "pvt hidratada", "proteina vegetal hidratada")),

    // ─── Cortes de carne chilenos ────────────────────────────────────────────
    FoodItem(id = "gen093a", name = "Posta Rosada (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 140.0, protein = 22.0, carbs = 0.0, fats = 5.0, cookingWeightFactor = 0.75, searchAliases = listOf("posta rosada", "posta")),
    FoodItem(id = "gen093b", name = "Posta Negra (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 150.0, protein = 21.0, carbs = 0.0, fats = 6.5, cookingWeightFactor = 0.75, searchAliases = listOf("posta negra")),
    FoodItem(id = "gen093c", name = "Asado de Tira (crudo)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 250.0, protein = 18.0, carbs = 0.0, fats = 20.0, cookingWeightFactor = 0.7, searchAliases = listOf("asado de tira", "costillar")),
    FoodItem(id = "gen093d", name = "Lomo Vetado (crudo)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 220.0, protein = 20.0, carbs = 0.0, fats = 15.0, cookingWeightFactor = 0.75, searchAliases = listOf("lomo vetado", "vetado")),
    FoodItem(id = "gen093e", name = "Punta de Ganso (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 190.0, protein = 21.0, carbs = 0.0, fats = 11.0, cookingWeightFactor = 0.75, searchAliases = listOf("punta de ganso")),
    FoodItem(id = "gen093f", name = "Pulpa Negra (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 145.0, protein = 21.0, carbs = 0.0, fats = 6.0, cookingWeightFactor = 0.75, searchAliases = listOf("pulpa negra")),
    FoodItem(id = "gen093g", name = "Plateada (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 210.0, protein = 19.0, carbs = 0.0, fats = 14.0, cookingWeightFactor = 0.7, searchAliases = listOf("plateada")),
    FoodItem(id = "gen093h", name = "Churrasco (crudo)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 180.0, protein = 22.0, carbs = 0.0, fats = 9.0, cookingWeightFactor = 0.75, searchAliases = listOf("churrasco", "bistec")),
    FoodItem(id = "gen093i", name = "Malaya (cruda)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 280.0, protein = 17.0, carbs = 0.0, fats = 23.0, cookingWeightFactor = 0.65, searchAliases = listOf("malaya")),
    FoodItem(id = "gen093j", name = "Huachalomo (crudo)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 195.0, protein = 20.0, carbs = 0.0, fats = 12.0, cookingWeightFactor = 0.75, searchAliases = listOf("huachalomo")),

    // ─── Preparaciones chilenas adicionales ──────────────────────────────────
    FoodItem(id = "cl021", name = "Calzones Rotos", servingSize = 80.0, unit = "g", calories = 420.0, protein = 6.0, carbs = 50.0, fats = 22.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("calzones rotos")),
    FoodItem(id = "cl022", name = "Sopaipillas Pasadas", servingSize = 150.0, unit = "g", calories = 280.0, protein = 3.0, carbs = 45.0, fats = 10.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("sopaipillas pasadas")),
    FoodItem(id = "cl023", name = "Arroz con Leche", servingSize = 200.0, unit = "g", calories = 220.0, protein = 6.0, carbs = 38.0, fats = 5.0, tags = listOf("preparacion", "chileno", "postre"), searchAliases = listOf("arroz con leche")),
    FoodItem(id = "cl024", name = "Leche Asada", servingSize = 150.0, unit = "g", calories = 180.0, protein = 7.0, carbs = 28.0, fats = 4.0, tags = listOf("preparacion", "chileno", "postre"), searchAliases = listOf("leche asada")),
    FoodItem(id = "cl025", name = "Pan con Palta", servingSize = 120.0, unit = "g", calories = 280.0, protein = 6.0, carbs = 30.0, fats = 16.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("pan con palta", "palta pan")),
    FoodItem(id = "cl026", name = "Pan con Queso", servingSize = 100.0, unit = "g", calories = 320.0, protein = 14.0, carbs = 28.0, fats = 18.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("pan con queso")),
    FoodItem(id = "cl027", name = "Pan con Jamón", servingSize = 100.0, unit = "g", calories = 260.0, protein = 12.0, carbs = 30.0, fats = 10.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("pan con jamon", "pan con jamón")),
    FoodItem(id = "cl028", name = "Ensalada Chilena", servingSize = 150.0, unit = "g", calories = 60.0, protein = 1.5, carbs = 8.0, fats = 3.0, tags = listOf("preparacion", "chileno", "ensalada"), searchAliases = listOf("ensalada chilena", "tomate cebolla")),
    FoodItem(id = "cl029", name = "Porotos con Riendas", servingSize = 350.0, unit = "g", calories = 450.0, protein = 20.0, carbs = 65.0, fats = 12.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("porotos con riendas")),
    FoodItem(id = "cl030", name = "Cazuela de Vacuno", servingSize = 400.0, unit = "ml", calories = 380.0, protein = 28.0, carbs = 35.0, fats = 14.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("cazuela de vacuno", "cazuela")),
    FoodItem(id = "cl031", name = "Cazuela de Pollo", servingSize = 400.0, unit = "ml", calories = 340.0, protein = 26.0, carbs = 32.0, fats = 12.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("cazuela de pollo")),
    FoodItem(id = "cl032", name = "Empanada de Mariscos", servingSize = 150.0, unit = "g", calories = 320.0, protein = 16.0, carbs = 28.0, fats = 18.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("empanada de mariscos")),
    FoodItem(id = "cl033", name = "Humitas", servingSize = 200.0, unit = "g", calories = 280.0, protein = 8.0, carbs = 40.0, fats = 10.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("humitas")),
    FoodItem(id = "cl034", name = "Pastel de Papa", servingSize = 250.0, unit = "g", calories = 380.0, protein = 18.0, carbs = 35.0, fats = 20.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("pastel de papa")),
    FoodItem(id = "cl035", name = "Completo Americano", servingSize = 220.0, unit = "g", calories = 420.0, protein = 14.0, carbs = 35.0, fats = 26.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("completo americano")),
    FoodItem(id = "cl036", name = "Sándwich de Pavita", servingSize = 150.0, unit = "g", calories = 280.0, protein = 18.0, carbs = 25.0, fats = 12.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("sandwich de pavita", "pavita")),
    FoodItem(id = "cl037", name = "Ave Palta", servingSize = 180.0, unit = "g", calories = 350.0, protein = 12.0, carbs = 30.0, fats = 22.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("ave palta")),
    FoodItem(id = "cl038", name = "Bistec a lo Pobre", servingSize = 400.0, unit = "g", calories = 850.0, protein = 45.0, carbs = 60.0, fats = 48.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("bistec a lo pobre", "lomo a lo pobre")),
    // cl039 (segundo "Charquicán", idéntico a cl015) se fusionó en cl015 (WP-S10): ver LEGACY_FOOD_ID_REDIRECTS.
    FoodItem(id = "cl040", name = "Porotos Granados con Mazamorra", servingSize = 350.0, unit = "g", calories = 420.0, protein = 18.0, carbs = 60.0, fats = 14.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("porotos granados con mazamorra")),

    // ─── Alimentos adicionales del dataset ───────────────────────────────────
    FoodItem(id = "gen107", name = "Tocino", brand = "Genérico", servingSize = 15.0, unit = "g", calories = 54.0, protein = 3.0, carbs = 0.0, fats = 4.5, searchAliases = listOf("tocino", "bacon"), tags = listOf("USDA")),
    FoodItem(id = "gen108", name = "Mermelada", brand = "Genérico", servingSize = 20.0, unit = "g", calories = 50.0, protein = 0.0, carbs = 13.0, fats = 0.0, searchAliases = listOf("mermelada", "jam")),
    FoodItem(id = "gen109", name = "Manjar (Dulce de Leche)", brand = "Genérico", servingSize = 20.0, unit = "g", calories = 60.0, protein = 1.5, carbs = 12.0, fats = 1.0, searchAliases = listOf("manjar", "dulce de leche")),
    FoodItem(id = "gen110", name = "Choclo en Grano", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 95.0, protein = 3.5, carbs = 20.0, fats = 1.5, searchAliases = listOf("choclo en grano", "maiz en grano")),
    FoodItem(id = "gen111", name = "Palmitos", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 25.0, protein = 2.5, carbs = 3.0, fats = 0.5, searchAliases = listOf("palmitos", "chonta")),
    FoodItem(id = "gen112", name = "Aceitunas", brand = "Genérico", servingSize = 10.0, unit = "g", calories = 15.0, protein = 0.1, carbs = 0.5, fats = 1.5, searchAliases = listOf("aceitunas", "olivas")),
    FoodItem(id = "gen113", name = "Pepinillos", brand = "Genérico", servingSize = 10.0, unit = "g", calories = 3.0, protein = 0.1, carbs = 0.5, fats = 0.0, searchAliases = listOf("pepinillos", "pickles")),
    FoodItem(id = "gen114", name = "Salsa Golf", brand = "Genérico", servingSize = 20.0, unit = "g", calories = 70.0, protein = 0.5, carbs = 5.0, fats = 5.5, searchAliases = listOf("salsa golf", "rosada")),
    FoodItem(id = "gen115", name = "Vinagreta", brand = "Genérico", servingSize = 20.0, unit = "g", calories = 80.0, protein = 0.0, carbs = 2.0, fats = 8.5, searchAliases = listOf("vinagreta")),
    FoodItem(id = "gen116", name = "Maltodextrina", brand = "Genérico", servingSize = 30.0, unit = "g", calories = 110.0, protein = 0.0, carbs = 27.0, fats = 0.0, searchAliases = listOf("maltodextrina", "maltodextrine")),
    FoodItem(id = "gen117", name = "Caseína", brand = "Genérico", servingSize = 30.0, unit = "g", calories = 110.0, protein = 25.0, carbs = 2.0, fats = 1.0, searchAliases = listOf("caseina", "caseína")),
    FoodItem(id = "gen118", name = "Requesón", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 140.0, protein = 12.0, carbs = 4.0, fats = 9.0, searchAliases = listOf("requeson", "requesón", "ricotta")),
    FoodItem(id = "gen119", name = "Mantequilla de Almendras", brand = "Genérico", servingSize = 20.0, unit = "g", calories = 120.0, protein = 4.0, carbs = 4.0, fats = 10.0, searchAliases = listOf("mantequilla de almendras", "almond butter")),
    FoodItem(id = "gen120", name = "Couscous (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 112.0, protein = 3.8, carbs = 23.0, fats = 0.2, searchAliases = listOf("couscous", "cuscus")),
    FoodItem(id = "gen121", name = "Bulgur (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 83.0, protein = 3.1, carbs = 18.0, fats = 0.2, searchAliases = listOf("bulgur", "trigo bulgur")),
    FoodItem(id = "gen122", name = "Mijo (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 119.0, protein = 3.5, carbs = 23.0, fats = 1.0, searchAliases = listOf("mijo")),
    FoodItem(id = "gen123", name = "Amaranto (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 102.0, protein = 3.8, carbs = 19.0, fats = 1.6, searchAliases = listOf("amaranto")),
    FoodItem(id = "gen124", name = "Trigo Sarraceno (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 92.0, protein = 3.4, carbs = 20.0, fats = 0.6, searchAliases = listOf("trigo sarraceno", "alforfón", "alforfon")),
    FoodItem(id = "gen125", name = "Yuca (cocida)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 130.0, protein = 0.9, carbs = 31.0, fats = 0.3, searchAliases = listOf("yuca", "mandioca")),
    FoodItem(id = "gen126", name = "Ñame (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 118.0, protein = 1.5, carbs = 28.0, fats = 0.2, searchAliases = listOf("ñame", "name")),
    // F4.4: alimentos referenciados por emojis que antes caían a MIXED_DISH subestimado
    FoodItem(id = "gen127", name = "Pizza", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 266.0, protein = 11.0, carbs = 33.0, fats = 10.0, searchAliases = listOf("pizza")),
    FoodItem(id = "gen128", name = "Hamburguesa", brand = "Genérico", servingSize = 150.0, unit = "u", calories = 380.0, protein = 18.0, carbs = 30.0, fats = 19.0, searchAliases = listOf("hamburguesa", "burger", "burguer")),
    FoodItem(id = "gen129", name = "Taco", brand = "Genérico", servingSize = 100.0, unit = "u", calories = 220.0, protein = 8.0, carbs = 22.0, fats = 11.0, searchAliases = listOf("taco", "tacos")),
    FoodItem(id = "gen130", name = "Burrito", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 200.0, protein = 8.0, carbs = 26.0, fats = 7.0, searchAliases = listOf("burrito", "burritos")),
    FoodItem(id = "gen131", name = "Sushi", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 150.0, protein = 6.0, carbs = 25.0, fats = 3.0, searchAliases = listOf("sushi")),
    FoodItem(id = "gen132", name = "Donut", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 420.0, protein = 5.0, carbs = 51.0, fats = 22.0, searchAliases = listOf("donut", "dona", "donuts", "donas")),
    // E16: identidad faltante detectada por el contrato de métricas (baseline 87%)
    FoodItem(id = "gen133", name = "Pan Integral", brand = "Genérico", servingSize = 50.0, unit = "u", calories = 265.0, protein = 9.5, carbs = 45.0, fats = 4.2, nutritionBasis = "PER_100G_AS_SOLD", searchAliases = listOf("pan integral", "pan de molde integral", "pan centeno")),
    FoodItem(id = "gen134", name = "Sopa (casera)", brand = "Genérico", servingSize = 250.0, unit = "ml", calories = 42.0, protein = 2.0, carbs = 4.0, fats = 1.5, searchAliases = listOf("sopa", "sopa casera", "caldo", "sopa de verduras", "sopa de pollo")),
    FoodItem(id = "gen135", name = "Porotos (cocidos)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 140.0, protein = 8.5, carbs = 25.0, fats = 0.5, searchAliases = listOf("porotos", "poroto", "porotos cocidos", "porotos negros", "frijoles", "frijol")),
    // gen136 (segundo "Arroz Integral (cocido)") se fusionó en gen006 (WP-S11): ver LEGACY_FOOD_ID_REDIRECTS.
    FoodItem(
        id = "gen137",
        name = "Galletas de chocolate",
        brand = "Genérico",
        category = "galletas",
        servingSize = 100.0,
        unit = "g",
        calories = 502.0,
        protein = 6.2,
        carbs = 63.0,
        fats = 24.0,
        carbBreakdown = CarbBreakdown(sugar = 32.0),
        searchAliases = listOf("galleta de chocolate", "galletas de chocolate", "chocolate cookies", "galletas chocolate"),
    ),
    FoodItem(
        id = "gen138",
        name = "Galletas saladas",
        brand = "Genérico",
        category = "galletas",
        servingSize = 100.0,
        unit = "g",
        calories = 435.0,
        protein = 9.0,
        carbs = 70.0,
        fats = 13.0,
        searchAliases = listOf("galleta salada", "galletas saladas", "crackers", "galleta de agua", "galletas de agua", "galleta de soda", "galletas de soda"),
    ),
    FoodItem(
        id = "gen139",
        name = "Galletas de avena",
        brand = "Genérico",
        category = "galletas",
        servingSize = 100.0,
        unit = "g",
        calories = 450.0,
        protein = 7.0,
        carbs = 66.0,
        fats = 18.0,
        searchAliases = listOf("galleta de avena", "galletas de avena", "oatmeal cookies"),
    ),
    FoodItem(
        id = "gen140",
        name = "Papas fritas (snack)",
        brand = "Genérico",
        category = "chips",
        servingSize = 100.0,
        unit = "g",
        calories = 536.0,
        protein = 6.6,
        carbs = 53.0,
        fats = 35.0,
        searchAliases = listOf("papas fritas snack", "chips de bolsa", "papas chips", "patatas chips"),
    ),
    FoodItem(
        id = "gen141",
        name = "Chocolate de mesa",
        brand = "Genérico",
        category = "dulce",
        servingSize = 100.0,
        unit = "g",
        calories = 546.0,
        protein = 7.6,
        carbs = 46.0,
        fats = 31.0,
        carbBreakdown = CarbBreakdown(sugar = 40.0),
        searchAliases = listOf("chocolate", "chocolate de mesa", "tableta de chocolate"),
    ),
    FoodItem(
        id = "gen142",
        name = "Dulce genérico",
        brand = "Genérico",
        category = "dulce",
        servingSize = 100.0,
        unit = "g",
        calories = 394.0,
        protein = 0.0,
        carbs = 98.0,
        fats = 0.1,
        carbBreakdown = CarbBreakdown(sugar = 80.0),
        searchAliases = listOf("dulce", "golosina", "caramelo"),
    ),
    // ─── WP-N5: bebidas y alimentos de 0 kcal ────────────────────────────────────────────────────────────────────────
    // Valores por 100 g/ml como se bebe (PER_100G_AS_SOLD); `servingSize` es la porción individual típica (un vaso, una
    // lata, una copa), nunca el denominador nutricional. `sourceRecordId` es el id FDC de data/usdaFoodsOffline.json.
    // El tag "zero_energy" (<= 5 kcal) declara que sus macros en cero son reales: FoodIdentity.hasPlausibleMacros no
    // los rechaza y ningún tope de masa los frena (dos litros de agua no son un envase de comida).
    FoodItem(
        id = "gen143",
        name = "Agua",
        brand = "Genérico",
        category = "bebida",
        servingSize = 250.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "USDA SR Legacy (rounded)",
        sourceRecordId = "173647", // Beverages, water, tap, drinking
        calories = 0.0,
        protein = 0.0,
        carbs = 0.0,
        fats = 0.0,
        tags = listOf("zero_energy"),
        searchAliases = listOf("agua", "agua mineral", "agua sin gas", "agua mineral sin gas", "agua de la llave", "agua potable", "agua purificada", "agüita", "cachantún", "cachantun"),
    ),
    FoodItem(
        id = "gen144",
        name = "Té sin azúcar",
        brand = "Genérico",
        category = "bebida",
        servingSize = 240.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "USDA SR Legacy (rounded)",
        sourceRecordId = "173227", // Beverages, tea, black, brewed, prepared with tap water
        calories = 1.0,
        protein = 0.0,
        carbs = 0.3,
        fats = 0.0,
        caffeineMg = 20.0, // té negro de taza: ~47 mg por 240 ml, en línea con el té verde (gen060)
        tags = listOf("zero_energy"),
        searchAliases = listOf("té", "te", "té negro", "agua de hierbas", "té de hierbas", "infusión"),
    ),
    // Receta: 60 % leche entera (gen016, FDC 171265) + 40 % café (FDC 171890): 37 kcal, 1,9 P, 2,9 C, 2,0 G por 100 ml.
    FoodItem(
        id = "gen145",
        name = "Café con leche",
        brand = "Genérico",
        category = "bebida",
        servingSize = 240.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "USDA SR Legacy (receta: 60 % leche entera + 40 % café)",
        sourceRecordId = "171265+171890",
        calories = 37.0,
        protein = 1.9,
        carbs = 2.9,
        fats = 2.0,
        caffeineMg = 16.0, // 40 % de café (gen059: 40 mg por 100 ml)
    ),
    FoodItem(
        id = "gen146",
        name = "Bebida gaseosa",
        brand = "Genérico",
        category = "bebida",
        servingSize = 350.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "USDA SR Legacy (rounded)",
        sourceRecordId = "174852", // Beverages, carbonated, cola, regular
        calories = 42.0,
        protein = 0.0,
        carbs = 10.4,
        fats = 0.3,
        caffeineMg = 8.0, // cola; las limón-lima (sprite) no llevan: promedio de los alias
        searchAliases = listOf("bebida", "gaseosa", "coca cola", "coca-cola", "cocacola", "coca", "sprite", "fanta", "pepsi", "refresco", "bebida cola"),
    ),
    FoodItem(
        id = "gen147",
        name = "Bebida zero/light",
        brand = "Genérico",
        category = "bebida",
        servingSize = 350.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "USDA SR Legacy (rounded)",
        sourceRecordId = "171876", // Beverages, carbonated, low calorie, cola or pepper-types, with sodium saccharin
        calories = 0.0,
        protein = 0.0,
        carbs = 0.1,
        fats = 0.0,
        caffeineMg = 10.0, // cola dietética
        tags = listOf("zero_energy"),
        searchAliases = listOf(
            "bebida zero", "bebida light", "bebida diet", "bebida sin azúcar",
            "gaseosa zero", "gaseosa light", "gaseosa diet", "gaseosa sin azúcar",
            "coca cola zero", "coca zero", "coca cola light", "coca light", "coca cola diet", "coca cola sin azúcar",
            "cocacola zero", "cocacola light", "cocacola diet",
            "pepsi zero", "pepsi light", "pepsi diet", "pepsi max", "sprite zero", "sprite light", "fanta zero", "fanta light",
        ),
    ),
    // Néctar típico de caja (~45 kcal/100 ml, ~11 g de azúcares); referencia USDA 171947 (jugo de fruta > 3 %: 46 kcal).
    FoodItem(
        id = "gen148",
        name = "Jugo en caja (néctar)",
        brand = "Genérico",
        category = "bebida",
        servingSize = 200.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (néctar típico en caja)",
        sourceRecordId = "171947",
        calories = 45.0,
        protein = 0.1,
        carbs = 11.0,
        fats = 0.0,
        searchAliases = listOf("jugo", "néctar", "jugo en caja", "jugo de caja", "jugo envasado"),
    ),
    FoodItem(
        id = "gen149",
        name = "Cerveza",
        brand = "Genérico",
        category = "bebida",
        servingSize = 330.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "USDA SR Legacy (rounded)",
        sourceRecordId = "168746", // Alcoholic beverage, beer, regular, all
        calories = 43.0,
        protein = 0.5,
        carbs = 3.6,
        fats = 0.0,
        searchAliases = listOf("cerveza", "chela", "schop", "cerveza rubia"),
    ),
    FoodItem(
        id = "gen150",
        name = "Vino",
        brand = "Genérico",
        category = "bebida",
        servingSize = 150.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "USDA SR Legacy (rounded)",
        sourceRecordId = "173190", // Alcoholic beverage, wine, table, red
        calories = 85.0,
        protein = 0.1,
        carbs = 2.6,
        fats = 0.0,
        searchAliases = listOf("vino", "vino tinto", "vino blanco", "tinto", "blanco"),
    ),
    // El agua con gas va aparte porque su nombre es compuesto: "agua con gas" no puede ser un alias de "Agua", que
    // FoodIdentity.matchesDeclaredIdentity rechaza por el " con " de la consulta frente al nombre simple de la ficha.
    FoodItem(
        id = "gen151",
        name = "Agua con gas",
        brand = "Genérico",
        category = "bebida",
        servingSize = 250.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "USDA SR Legacy (rounded)",
        sourceRecordId = "174842", // Beverages, carbonated, club soda
        calories = 0.0,
        protein = 0.0,
        carbs = 0.0,
        fats = 0.0,
        tags = listOf("zero_energy"),
        searchAliases = listOf("agua mineral con gas", "agua gasificada", "agua carbonatada", "cachantún con gas", "cachantun con gas"),
    ),
    // ─── WP-S6: aliases that named nothing, now backed by a row of their own ─────────────────────────────────────────────
    // Valores por 100 g (PER_100G_AS_SOLD); `servingSize` es la porción típica (un trozo de queque, un tamal). La procedencia es el
    // registro FDC que sustenta los macros (data/usdaFoodsOffline.json); `source` no lleva el texto "USDA" para que
    // HouseholdPortions.isGlobalSku no trate una ficha curada como un SKU de supermercado.
    // Queque chileno ~ pound cake: FDC 172704 (353 kcal); los queques planos de OFF Chile van de 400 (Castaño) a 257-311 (con
    // frutos o rellenos).
    FoodItem(
        id = "gen152",
        name = "Queque",
        brand = "Genérico",
        servingSize = 70.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 172704, pound cake)",
        sourceRecordId = "172704", // Cake, pound, commercially prepared, butter
        calories = 353.0,
        protein = 5.0,
        carbs = 53.6,
        fats = 14.0,
        tags = listOf("preparacion", "chileno", "postre"),
        searchAliases = listOf("queque", "queques", "queque casero", "queque del casino", "queque de vainilla", "panqué"),
    ),
    // Tamal con relleno de carne: FNDDS 2708570 (174 kcal; "Tamale, NFS"); los tamales de OFF van de 156 a 203 (mediana 183).
    FoodItem(
        id = "gen153",
        name = "Tamal",
        brand = "Genérico",
        servingSize = 150.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 2708570, tamale)",
        sourceRecordId = "2708570", // Tamale, NFS
        calories = 174.0,
        protein = 7.4,
        carbs = 15.8,
        fats = 9.0,
        tags = listOf("preparacion"),
        searchAliases = listOf("tamal", "tamales"),
    ),
    // ─── WP-D1: Convenciones de las fichas nuevas ───────────────────────────────────────────────────────────────────────────────
    // Valores por 100 g/ml como se venden o se beben (PER_100G_AS_SOLD; PER_100G_COOKED si la ficha es un alimento cocido); `servingSize` es la
    // porción individual típica (un vaso, una lata, una lonja), nunca el denominador nutricional. `sourceRecordId` es el id FDC
    // (data/usdaFoodsOffline.json) que sustenta los macros, o el código de barras de la etiqueta chilena cuando el valor sale de ella; los
    // platos llevan su receta. `source` no lleva "USDA" ni "OFF" para que HouseholdPortions.isGlobalSku no trate una ficha curada como un
    // SKU de supermercado.
    // `unit = "u"` marca una pieza o una porción de uso (lata de jurel, filete, chuleta, porción de margarina o de leche condensada, trozo de
    // lasaña): HouseholdPortions.isCountable la trata como "con porción doméstica propia" y no la reemplaza por los 350 g de un plato inferido
    // ("jurel en lata"), por los 40 g de un relleno de sándwich ("pan con margarina") ni por los 200 g de la familia "leche" o los 160 g de la
    // familia "pasta" (FoodIdentity las asigna por palabra). El queso gauda declara 100 g (sin porción propia, como los demás quesos: HouseholdPortions da
    // 30 g a todo queso). El azúcar declara 30 g, la porción estándar de los polvos del motor de porciones: "un poco de azúcar" son 0,15 de
    // ella, 4,5 g.
    // ─── WP-D1: Lácteos y grasas ────────────────────────────────────────────────────────────────────────────────────────────────
    FoodItem(
        id = "gen154",
        name = "Leche sin lactosa",
        brand = "Genérico",
        category = "lacteo",
        servingSize = 200.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 2705391, lactose-free milk 2%)",
        sourceRecordId = "2705391", // Milk, lactose free, reduced fat (2%)
        calories = 50.0,
        protein = 3.4,
        carbs = 4.9,
        fats = 1.9,
        searchAliases = listOf(
            "leche sin lactosa", "leche deslactosada", "leche cero lactosa", "leche zero lactosa", "leche semidescremada sin lactosa",
            "leche sin lactosa semidescremada", "leche deslactosada semidescremada",
        ),
    ),
    FoodItem(
        id = "gen155",
        name = "Leche condensada",
        brand = "Genérico",
        category = "lacteo",
        servingSize = 20.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 171275, sweetened condensed milk)",
        sourceRecordId = "171275", // Milk, canned, condensed, sweetened
        calories = 321.0,
        protein = 7.9,
        carbs = 54.4,
        fats = 8.7,
        carbBreakdown = CarbBreakdown(sugar = 54.4),
        searchAliases = listOf("leche condensada", "leche condensada azucarada", "lechera"),
    ),
    FoodItem(
        id = "gen156",
        name = "Queso crema",
        brand = "Genérico",
        category = "lacteo",
        servingSize = 20.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 173418, cream cheese)",
        sourceRecordId = "173418", // Cheese, cream
        calories = 350.0,
        protein = 6.2,
        carbs = 5.5,
        fats = 34.4,
        searchAliases = listOf("queso crema", "queso untable", "queso crema untable", "cream cheese"),
    ),
    FoodItem(
        id = "gen157",
        name = "Queso gauda",
        brand = "Genérico",
        category = "lacteo",
        servingSize = 100.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 171241, gouda)",
        sourceRecordId = "171241", // Cheese, gouda; las etiquetas chilenas de gauda dan 341-355 kcal, 22-25 g P y 28 g G
        calories = 356.0,
        protein = 24.9,
        carbs = 2.2,
        fats = 27.4,
        searchAliases = listOf("queso gauda", "queso gouda", "gauda", "gouda", "queso gauda laminado", "queso gouda laminado", "gauda laminado"),
    ),
    FoodItem(
        id = "gen158",
        name = "Quesillo",
        brand = "Genérico",
        category = "lacteo",
        servingSize = 50.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 171248, ricotta part skim; etiquetas de quesillo chileno 138-148 kcal)",
        sourceRecordId = "171248", // Cheese, ricotta, part skim milk; etiquetas: Colun 7802920000749 (138 kcal), Quillayes 7802930004362 (148 kcal)
        calories = 138.0,
        protein = 11.4,
        carbs = 5.1,
        fats = 7.9,
        searchAliases = listOf("quesillo", "quesillo fresco", "quesillo natural"),
    ),
    FoodItem(
        id = "gen159",
        name = "Margarina",
        brand = "Genérico",
        category = "grasa",
        servingSize = 10.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 172347, margarine tub 80% fat)",
        sourceRecordId = "172347", // Margarine, regular, 80% fat, composite, tub, with salt
        calories = 713.0,
        protein = 0.2,
        carbs = 0.8,
        fats = 80.2,
        searchAliases = listOf("margarina", "margarina de mesa", "margarina con sal"),
    ),
    // ─── WP-D1: Pescados, carnes y proteínas ────────────────────────────────────────────────────────────────────────────────────
    // Jurel y reineta son pescados de consumo diario en Chile sin registro propio en USDA: se usa el pariente más cercano (FDC citado en
    // `source`) y el valor de las etiquetas chilenas cuando existe.
    FoodItem(
        id = "gen160",
        name = "Jurel en lata (al natural)",
        brand = "Genérico",
        category = "pescado",
        servingSize = 150.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 175121, canned jack mackerel; etiquetas chilenas 140-160 kcal)",
        sourceRecordId = "175121", // Fish, mackerel, jack, canned, drained solids; el jurel en aceite ronda 225 kcal
        calories = 156.0,
        protein = 23.2,
        carbs = 0.0,
        fats = 6.3,
        searchAliases = listOf("jurel", "jurel en lata", "jurel al natural", "jurel en conserva", "lata de jurel", "conserva de jurel"),
    ),
    FoodItem(
        id = "gen161",
        name = "Reineta (cocida)",
        brand = "Genérico",
        category = "pescado",
        servingSize = 150.0,
        unit = "u",
        nutritionBasis = "PER_100G_COOKED",
        source = "KPKN Curated (proxy FDC 173694, sea bass cooked)",
        sourceRecordId = "173694", // Fish, sea bass, mixed species, cooked, dry heat (la reineta, Brama australis, no tiene registro USDA)
        calories = 124.0,
        protein = 23.6,
        carbs = 0.0,
        fats = 2.6,
        foodState = "COOKED",
        searchAliases = listOf("reineta", "filete de reineta", "reineta cocida"),
    ),
    FoodItem(
        id = "gen162",
        name = "Salame",
        brand = "Genérico",
        category = "embutido",
        servingSize = 20.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 174603, Italian pork salami; etiquetas chilenas 407-421 kcal)",
        sourceRecordId = "174603", // Salami, Italian, pork
        calories = 425.0,
        protein = 21.7,
        carbs = 1.2,
        fats = 37.0,
        searchAliases = listOf("salame", "salame italiano", "salame ahumado", "salami"),
    ),
    FoodItem(
        id = "gen163",
        name = "Chuleta de cerdo (cocida)",
        brand = "Genérico",
        category = "carne",
        servingSize = 120.0,
        unit = "u",
        nutritionBasis = "PER_100G_COOKED",
        source = "KPKN Curated (FDC 167827, pork loin chop broiled)",
        sourceRecordId = "167827", // Pork, fresh, loin, center loin (chops), bone-in, separable lean and fat, cooked, broiled (porción comestible, sin hueso)
        calories = 209.0,
        protein = 25.6,
        carbs = 0.0,
        fats = 11.1,
        foodState = "COOKED",
        searchAliases = listOf("chuleta", "chuleta de cerdo", "chuleta de chancho", "chuleta cocida"),
    ),
    FoodItem(
        id = "gen164",
        name = "Nuggets de pollo (fritos)",
        brand = "Genérico",
        category = "carne",
        servingSize = 90.0,
        unit = "g",
        nutritionBasis = "PER_100G_COOKED",
        source = "KPKN Curated (FDC 2706096, chicken nuggets from frozen)",
        sourceRecordId = "2706096", // Chicken nuggets, from frozen
        calories = 298.0,
        protein = 13.4,
        carbs = 17.9,
        fats = 19.2,
        foodState = "COOKED",
        searchAliases = listOf(
            "nuggets", "nugget", "nuggets de pollo", "nugget de pollo", "chicken nuggets", "nuggets fritos", "nuggets de pollo fritos",
        ),
    ),
    FoodItem(
        id = "gen165",
        name = "Barra de proteína",
        brand = "Genérico",
        category = "snack",
        servingSize = 50.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (mediana de 52 barras de proteína del catálogo chileno)",
        sourceRecordId = "mediana n=52 (etiquetas chilenas)", // no hay una barra genérica en USDA (las formuladas van de 412 a 426 kcal); mediana de las barras con energía declarada
        calories = 332.0,
        protein = 31.3,
        carbs = 31.3,
        fats = 9.1,
        searchAliases = listOf(
            "barra de proteína", "barra de proteina", "barras de proteína", "barras de proteina", "barra proteica", "barras proteicas",
            "barrita de proteína", "protein bar",
        ),
    ),
    // ─── WP-D1: Frutas y verduras ───────────────────────────────────────────────────────────────────────────────────────────────
    // Frutas frescas: FDC SR Legacy "raw" por 100 g de porción comestible. `unit = "u"` con `servingSize` = peso de UNA pieza para las que
    // se cuentan (mandarina, limón, chirimoya, membrillo, ají): HouseholdPortions.unitGrams devuelve ese peso.
    FoodItem(
        id = "gen166",
        name = "Mandarina",
        brand = "Genérico",
        category = "fruta",
        servingSize = 90.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 169105, tangerines raw)",
        sourceRecordId = "169105", // Tangerines, (mandarin oranges), raw; una mandarina mediana pesa ~88 g
        calories = 53.0,
        protein = 0.8,
        carbs = 13.3,
        fats = 0.3,
        foodState = "RAW",
        searchAliases = listOf("mandarina", "mandarinas", "clementina", "clementinas"),
    ),
    FoodItem(
        id = "gen167",
        name = "Limón",
        brand = "Genérico",
        category = "fruta",
        servingSize = 60.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 167746, lemon raw without peel)",
        sourceRecordId = "167746", // Lemons, raw, without peel; un limón mediano pesa ~58 g
        calories = 29.0,
        protein = 1.1,
        carbs = 9.3,
        fats = 0.3,
        foodState = "RAW",
        searchAliases = listOf("limón", "limon", "limones", "limón de pica", "limon de pica"),
    ),
    FoodItem(
        id = "gen168",
        name = "Frambuesa",
        brand = "Genérico",
        category = "fruta",
        servingSize = 100.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 167755, raspberries raw)",
        sourceRecordId = "167755", // Raspberries, raw
        calories = 52.0,
        protein = 1.2,
        carbs = 11.9,
        fats = 0.7,
        foodState = "RAW",
        searchAliases = listOf("frambuesa", "frambuesas", "frambuesa fresca", "raspberry"),
    ),
    FoodItem(
        id = "gen169",
        name = "Cereza",
        brand = "Genérico",
        category = "fruta",
        servingSize = 80.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 171719, sweet cherries raw)",
        sourceRecordId = "171719", // Cherries, sweet, raw; un puñado de 10 cerezas sin cuesco pesa ~80 g
        calories = 63.0,
        protein = 1.1,
        carbs = 16.0,
        fats = 0.2,
        foodState = "RAW",
        searchAliases = listOf("cereza", "cerezas", "cereza fresca"),
    ),
    FoodItem(
        id = "gen170",
        name = "Chirimoya",
        brand = "Genérico",
        category = "fruta",
        servingSize = 235.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 173953, cherimoya raw)",
        sourceRecordId = "173953", // Cherimoya, raw; una fruta sin cáscara ni semillas pesa ~235 g
        calories = 75.0,
        protein = 1.6,
        carbs = 17.7,
        fats = 0.7,
        foodState = "RAW",
        searchAliases = listOf("chirimoya", "chirimoyas", "cherimoya"),
    ),
    FoodItem(
        id = "gen171",
        name = "Membrillo",
        brand = "Genérico",
        category = "fruta",
        servingSize = 92.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 168163, quince raw)",
        sourceRecordId = "168163", // Quinces, raw; una fruta pesa ~92 g. El dulce de membrillo (pasta) es otro alimento, ~270 kcal/100 g
        calories = 57.0,
        protein = 0.4,
        carbs = 15.3,
        fats = 0.1,
        foodState = "RAW",
        searchAliases = listOf("membrillo", "membrillos", "quince"),
    ),
    FoodItem(
        id = "gen172",
        name = "Papaya",
        brand = "Genérico",
        category = "fruta",
        servingSize = 150.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 169926, papaya raw)",
        sourceRecordId = "169926", // Papayas, raw; la papaya en conserva (almíbar) tiene ~2x las kcal
        calories = 43.0,
        protein = 0.5,
        carbs = 10.8,
        fats = 0.3,
        foodState = "RAW",
        searchAliases = listOf("papaya", "papayas", "papaya fresca"),
    ),
    FoodItem(
        id = "gen173",
        name = "Porotos verdes (cocidos)",
        brand = "Genérico",
        category = "verdura",
        servingSize = 100.0,
        unit = "g",
        nutritionBasis = "PER_100G_COOKED",
        source = "KPKN Curated (FDC 169141, green snap beans boiled)",
        sourceRecordId = "169141", // Beans, snap, green, cooked, boiled, drained, without salt
        calories = 35.0,
        protein = 1.9,
        carbs = 7.9,
        fats = 0.3,
        foodState = "COOKED",
        searchAliases = listOf(
            "porotos verdes", "poroto verde", "porotos verdes cocidos", "vainitas", "ejotes", "judías verdes", "judias verdes", "chauchas",
        ),
    ),
    FoodItem(
        id = "gen174",
        name = "Habas (cocidas)",
        brand = "Genérico",
        category = "verdura",
        servingSize = 100.0,
        unit = "g",
        nutritionBasis = "PER_100G_COOKED",
        source = "KPKN Curated (FDC 170378, broadbeans immature boiled)",
        sourceRecordId = "170378", // Broadbeans, immature seeds, cooked, boiled, drained, without salt
        calories = 62.0,
        protein = 4.8,
        carbs = 10.1,
        fats = 0.5,
        foodState = "COOKED",
        searchAliases = listOf("habas", "haba", "habas cocidas"),
    ),
    FoodItem(
        id = "gen175",
        name = "Ají verde",
        brand = "Genérico",
        category = "condimento",
        servingSize = 15.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 170497, hot chili pepper green raw)",
        sourceRecordId = "170497", // Peppers, hot chili, green, raw; un ají pesa ~15 g
        calories = 40.0,
        protein = 2.0,
        carbs = 9.5,
        fats = 0.2,
        foodState = "RAW",
        searchAliases = listOf("ají", "aji", "ajíes", "ajies", "ají verde", "aji verde", "ají cacho de cabra", "aji cacho de cabra"),
    ),
    FoodItem(
        id = "gen176",
        name = "Merkén",
        brand = "Genérico",
        category = "condimento",
        servingSize = 3.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (proxy FDC 171319, chili powder)",
        sourceRecordId = "171319", // Spices, chili powder; el merkén (ají cacho de cabra ahumado, sal y semilla de cilantro) no tiene registro USDA y el proxy no lleva la sal añadida
        calories = 282.0,
        protein = 13.5,
        carbs = 49.7,
        fats = 14.3,
        searchAliases = listOf("merkén", "merken", "merquén", "merquen", "ají merkén", "aji merken"),
    ),
    // ─── WP-D1: Cereales, panes y pastas ────────────────────────────────────────────────────────────────────────────────────────
    // Panes y cereales como se venden; pastas rellenas y ñoquis cocidos. Las etiquetas chilenas sustentan el cereal de desayuno y los ñoquis
    // (USDA no tiene un cereal genérico ni ñoquis simples).
    FoodItem(
        id = "gen177",
        name = "Pan pita",
        brand = "Genérico",
        category = "pan",
        servingSize = 60.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 174915, white pita bread)",
        sourceRecordId = "174915", // Bread, pita, white, enriched; un pan pita grande pesa ~60 g
        calories = 275.0,
        protein = 9.1,
        carbs = 55.7,
        fats = 1.2,
        searchAliases = listOf("pan pita", "panes pita", "pita", "pan de pita", "pan árabe", "pan arabe"),
    ),
    FoodItem(
        id = "gen178",
        name = "Tostada (pan tostado)",
        brand = "Genérico",
        category = "pan",
        servingSize = 25.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 174925, white bread toasted)",
        sourceRecordId = "174925", // Bread, white, commercially prepared, toasted; una rebanada tostada pesa ~25 g
        calories = 290.0,
        protein = 9.0,
        carbs = 54.5,
        fats = 4.0,
        searchAliases = listOf("tostada", "tostadas", "pan tostado", "tostada de pan", "tostadas de pan", "pan de molde tostado"),
    ),
    FoodItem(
        id = "gen179",
        name = "Cereal de desayuno",
        brand = "Genérico",
        category = "cereal",
        servingSize = 40.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (mediana de 44 cereales de desayuno del catálogo chileno; FDC 173884)",
        sourceRecordId = "173884", // mediana de los cereales de desayuno chilenos con energía declarada (370 kcal); Cereals ready-to-eat, GENERAL MILLS, CHEERIOS: 372 kcal
        calories = 370.0,
        protein = 7.6,
        carbs = 74.1,
        fats = 3.5,
        searchAliases = listOf(
            "cereal", "cereales", "cereal de desayuno", "cereales de desayuno", "corn flakes", "cornflakes", "copos de maíz",
            "copos de maiz",
        ),
    ),
    FoodItem(
        id = "gen180",
        name = "Lasaña",
        brand = "Genérico",
        category = "plato",
        servingSize = 250.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 2708750, lasagna with meat)",
        sourceRecordId = "2708750", // Lasagna with meat
        calories = 139.0,
        protein = 7.5,
        carbs = 16.2,
        fats = 5.0,
        foodState = "COOKED",
        tags = listOf("preparacion"),
        searchAliases = listOf("lasaña", "lasana", "lasañas", "lasaña de carne", "lasagna", "lasagna de carne"),
    ),
    FoodItem(
        id = "gen181",
        name = "Ravioles (cocidos)",
        brand = "Genérico",
        category = "pasta",
        servingSize = 200.0,
        unit = "g",
        nutritionBasis = "PER_100G_COOKED",
        source = "KPKN Curated (FDC 2708760, ravioli no sauce)",
        sourceRecordId = "2708760", // Ravioli, NS as to filling, no sauce
        calories = 185.0,
        protein = 10.3,
        carbs = 19.9,
        fats = 7.0,
        foodState = "COOKED",
        searchAliases = listOf("ravioles", "raviol", "ravioli", "raviolis", "ravioles cocidos"),
    ),
    FoodItem(
        id = "gen182",
        name = "Ñoquis (cocidos)",
        brand = "Genérico",
        category = "pasta",
        servingSize = 200.0,
        unit = "g",
        nutritionBasis = "PER_100G_COOKED",
        source = "KPKN Curated (etiquetas de ñoquis de papa chilenos 154-159 kcal)",
        sourceRecordId = "7802500000411+8003039013159", // Talliani 7802500000411 (154 kcal, 4,0 P, 33,3 C, 0,3 G) y Maffei 8003039013159 (159 kcal, 3,7 P, 34,0 C, 0,4 G); el FNDDS Gnocchi, potato (2708722) trae grasa añadida
        calories = 156.0,
        protein = 3.9,
        carbs = 33.7,
        fats = 0.4,
        foodState = "COOKED",
        searchAliases = listOf("ñoquis", "noquis", "ñoquis cocidos", "gnocchi", "gnocchis"),
    ),
    // ─── WP-D1: Platos (RECIPE_ESTIMATE) ────────────────────────────────────────────────────────────────────────────────────────
    // Estimación por receta (método FAO/INFOODS del contrato nutrition_interpretation_v2): macros por 100 g del plato armado = suma ponderada de
    // los perfiles NOMBRADOS de cada ficha (ids del propio catálogo o FDC) con las proporciones por peso que ahí se listan. `source` =
    // RECIPE_ESTIMATE y `sourceRecordId` repite la receta para que la suposición sea visible. `qualityFlags` queda VACÍO a propósito:
    // NutrientBasis.isVerified exige que lo esté, y una ficha con bandera deja de ser identidad válida para la búsqueda y el resolvedor.
    // Receta (Chacarero): Sándwich chileno de 270 g: marraqueta 100 g + churrasco cocido 85 g + tomate 50 g + porotos verdes 30 g + ají verde 5 g.
    // Por 100 g: 163 kcal, 12,6 P, 20,3 C, 3,5 G (receta: cl010 37 % + gen093 31,5 % + gen026 18,5 % + gen173 11 % + gen175 2 %).
    FoodItem(
        id = "gen183",
        name = "Chacarero",
        brand = "Genérico",
        category = "plato",
        servingSize = 270.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "RECIPE_ESTIMATE",
        sourceRecordId = "receta: cl010 37 % + gen093 31,5 % + gen026 18,5 % + gen173 11 % + gen175 2 %",
        calories = 163.0,
        protein = 12.6,
        carbs = 20.3,
        fats = 3.5,
        foodState = "COOKED",
        tags = listOf("preparacion", "chileno"),
        searchAliases = listOf("chacarero", "chacareros", "sándwich chacarero", "sandwich chacarero"),
    ),
    // Receta (Barros Luco): Sándwich chileno de 230 g: marraqueta 100 g + churrasco cocido 90 g + queso gauda 40 g derretido ("barros luna" es la forma que se escribe a menudo).
    // Por 100 g: 248 kcal, 19,2 P, 22,1 C, 9,0 G (receta: cl010 43,5 % + gen093 39,1 % + gen157 17,4 %).
    FoodItem(
        id = "gen184",
        name = "Barros Luco",
        brand = "Genérico",
        category = "plato",
        servingSize = 230.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "RECIPE_ESTIMATE",
        sourceRecordId = "receta: cl010 43,5 % + gen093 39,1 % + gen157 17,4 %",
        calories = 248.0,
        protein = 19.2,
        carbs = 22.1,
        fats = 9.0,
        foodState = "COOKED",
        tags = listOf("preparacion", "chileno"),
        searchAliases = listOf("barros luco", "barros luna", "barroluco", "sándwich barros luco", "sandwich barros luco"),
    ),
    // Receta (Ave mayo): Sándwich chileno de 180 g: marraqueta 80 g + pollo cocido deshilachado 70 g + mayonesa 30 g.
    // Por 100 g: 293 kcal, 16,7 P, 22,3 C, 14,9 G (receta: cl010 44,4 % + gen004 38,9 % + gen065 16,7 %).
    FoodItem(
        id = "gen185",
        name = "Ave mayo",
        brand = "Genérico",
        category = "plato",
        servingSize = 180.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "RECIPE_ESTIMATE",
        sourceRecordId = "receta: cl010 44,4 % + gen004 38,9 % + gen065 16,7 %",
        calories = 293.0,
        protein = 16.7,
        carbs = 22.3,
        fats = 14.9,
        foodState = "COOKED",
        tags = listOf("preparacion", "chileno"),
        searchAliases = listOf("ave mayo", "ave mayonesa", "ave con mayo", "sándwich ave mayo", "sandwich ave mayo"),
    ),
    // Receta (Carbonada): Plato hondo de 400 g: caldo 45 % + carne de vacuno 12 % + papa 20 % + zapallo 6 % + choclo 5 % + arroz 5 % + zanahoria 3 % + arvejas 3 % + cebolla 1 %.
    // Por 100 g: 56 kcal, 4,3 P, 7,6 C, 1,1 G (receta: agua 45 % + gen093 12 % + gen021 20 % + gen072 6 % + gen071 5 % + gen005 5 % + gen024 3 % + gen055 3 % + gen027 1 %).
    FoodItem(
        id = "gen186",
        name = "Carbonada",
        brand = "Genérico",
        category = "plato",
        servingSize = 400.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "RECIPE_ESTIMATE",
        sourceRecordId = "receta: agua 45 % + gen093 12 % + gen021 20 % + gen072 6 % + gen071 5 % + gen005 5 % + gen024 3 % + gen055 3 % + gen027 1 %",
        calories = 56.0,
        protein = 4.3,
        carbs = 7.6,
        fats = 1.1,
        foodState = "COOKED",
        tags = listOf("preparacion", "chileno"),
        searchAliases = listOf("carbonada", "carbonadas", "carbonada de vacuno"),
    ),
    // Receta (Ajiaco): Ajiaco chileno (sobras de carne asada con papas, cebolla y ají) de 350 g: carne 25 % + papa 40 % + cebolla 12 % + caldo 17 % + aceite 3 % + pimentón 3 %. No es el ajiaco colombiano.
    // Por 100 g: 114 kcal, 7,9 P, 9,3 C, 5,1 G (receta: gen093 25 % + gen021 40 % + gen027 12 % + agua 17 % + gen099 3 % + gen036 3 %).
    FoodItem(
        id = "gen187",
        name = "Ajiaco",
        brand = "Genérico",
        category = "plato",
        servingSize = 350.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "RECIPE_ESTIMATE",
        sourceRecordId = "receta: gen093 25 % + gen021 40 % + gen027 12 % + agua 17 % + gen099 3 % + gen036 3 %",
        calories = 114.0,
        protein = 7.9,
        carbs = 9.3,
        fats = 5.1,
        foodState = "COOKED",
        tags = listOf("preparacion", "chileno"),
        searchAliases = listOf("ajiaco", "ajiaco chileno", "ajiaco de carne"),
    ),
    // Receta (Ceviche): Plato de 250 g: pescado blanco crudo 68 % (FDC 173713, whiting raw) + cebolla 18 % + jugo de limón 12 % (FDC 167747) + cilantro y ají 2 %; sin acompañamientos.
    // Por 100 g: 71 kcal, 12,7 P, 2,4 C, 0,9 G (receta: FDC 173713 68 % + gen027 18 % + FDC 167747 12 % + agua 2 %).
    FoodItem(
        id = "gen188",
        name = "Ceviche",
        brand = "Genérico",
        category = "plato",
        servingSize = 250.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "RECIPE_ESTIMATE",
        sourceRecordId = "receta: FDC 173713 68 % + gen027 18 % + FDC 167747 12 % + agua 2 %",
        calories = 71.0,
        protein = 12.7,
        carbs = 2.4,
        fats = 0.9,
        tags = listOf("preparacion", "chileno"),
        searchAliases = listOf("ceviche", "cebiche", "ceviches", "ceviche de pescado", "cebiche de pescado"),
    ),
    FoodItem(
        id = "gen189",
        name = "Arepa",
        brand = "Genérico",
        category = "plato",
        servingSize = 120.0,
        unit = "u",
        nutritionBasis = "PER_100G_COOKED",
        source = "KPKN Curated (FDC 168070, arepa)",
        sourceRecordId = "168070", // Restaurant, Latino, arepa (unleavened cornmeal bread); arepa sola, sin relleno
        calories = 219.0,
        protein = 5.5,
        carbs = 37.1,
        fats = 5.4,
        foodState = "COOKED",
        tags = listOf("preparacion"),
        searchAliases = listOf("arepa", "arepas", "arepa de maíz", "arepa de maiz"),
    ),
    // ─── WP-D1: Dulces y postres ────────────────────────────────────────────────────────────────────────────────────────────────
    // Kuchen: pariente USDA del coffeecake de fruta (el kuchen de manzana de OFF Chile da 280 kcal, el de nuez 435). Chilenito y cuchuflí no
    // existen en USDA: valores de etiquetas chilenas (código de barras en `sourceRecordId`); el peso de la pieza es el típico del producto.
    FoodItem(
        id = "gen190",
        name = "Kuchen",
        brand = "Genérico",
        category = "postre",
        servingSize = 100.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 174937, fruit cake)",
        sourceRecordId = "174937", // Cake, coffeecake, fruit
        calories = 311.0,
        protein = 5.2,
        carbs = 51.5,
        fats = 10.2,
        tags = listOf("chileno", "postre"),
        searchAliases = listOf("kuchen", "kuchenes", "kuchen de fruta"),
    ),
    FoodItem(
        id = "gen191",
        name = "Chilenito",
        brand = "Genérico",
        category = "dulce",
        servingSize = 40.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (etiqueta de chilenito chileno 400 kcal)",
        sourceRecordId = "0742032346078", // Chilenito de papaya, Lili's: 400 kcal, 4,2 P, 60 C, 12 G
        calories = 400.0,
        protein = 4.2,
        carbs = 60.0,
        fats = 12.0,
        tags = listOf("chileno", "postre"),
        searchAliases = listOf("chilenito", "chilenitos"),
    ),
    FoodItem(
        id = "gen192",
        name = "Cuchuflí",
        brand = "Genérico",
        category = "dulce",
        servingSize = 25.0,
        unit = "u",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (mediana de etiquetas de cuchuflí chilenos 400-439 kcal)",
        sourceRecordId = "7802245000882+7804674350026+7804674380023", // Entrelagos 7802245000882 (400 kcal), Donenic 7804674350026 (400 kcal), Chocolada 7804674380023 (439 kcal); mediana de P, C y G
        calories = 400.0,
        protein = 5.3,
        carbs = 56.4,
        fats = 15.8,
        tags = listOf("chileno", "postre"),
        searchAliases = listOf("cuchuflí", "cuchufli", "cuchuflís", "cuchuflies", "cuchuflí de manjar", "cuchufli de manjar"),
    ),
    FoodItem(
        id = "gen193",
        name = "Helado",
        brand = "Genérico",
        category = "postre",
        servingSize = 100.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 167575, vanilla ice cream)",
        sourceRecordId = "167575", // Ice creams, vanilla
        calories = 207.0,
        protein = 3.5,
        carbs = 23.6,
        fats = 11.0,
        searchAliases = listOf("helado", "helados", "ice cream"),
    ),
    FoodItem(
        id = "gen194",
        name = "Azúcar",
        brand = "Genérico",
        category = "dulce",
        servingSize = 30.0,
        unit = "g",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 169655, granulated sugar)",
        sourceRecordId = "169655", // Sugars, granulated; una cucharadita pesa ~5 g y una cucharada ~12,5 g
        calories = 387.0,
        protein = 0.0,
        carbs = 100.0,
        fats = 0.0,
        carbBreakdown = CarbBreakdown(sugar = 100.0),
        searchAliases = listOf("azúcar", "azucar", "azúcar blanca", "azucar blanca", "azúcar granulada", "azucar granulada", "sugar"),
    ),
    // ─── WP-D1: Bebidas ─────────────────────────────────────────────────────────────────────────────────────────────────────────
    // Como las de WP-N5: `category = "bebida"` (HouseholdPortions.BEVERAGE_CATEGORY) y `servingSize` = el vaso, la botella o la copa. Los nombres
    // llevan "bebida" o "vino" para que SubjectivePortionEngine.detectDensityCategory los pese como líquido (1 g/ml).
    FoodItem(
        id = "gen195",
        name = "Bebida isotónica",
        brand = "Genérico",
        category = "bebida",
        servingSize = 500.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (etiqueta de isotónica chilena 24 kcal; FDC 2710771)",
        sourceRecordId = "7801620005191", // Gatorade Cool Blue: 24 kcal y 6 g de carbohidratos por 100 ml (Powerade: 21 kcal); FNDDS 2710771, Sports drink NFS: 26 kcal
        calories = 24.0,
        protein = 0.0,
        carbs = 6.0,
        fats = 0.0,
        searchAliases = listOf("bebida isotónica", "bebida isotonica", "isotónica", "isotonica", "gatorade", "powerade", "bebida deportiva"),
    ),
    // Receta (Piscola (pisco con bebida cola)): 300 ml: pisco 22 % (FDC 174815, destilado de 80 proof: 231 kcal por 100 g) + bebida cola 78 % (gen146, FDC 174852); el alcohol (7 kcal/g) no aparece en los macros.
    // Por 100 g: 84 kcal, 0,0 P, 8,1 C, 0,2 G (receta: FDC 174815 22 % + gen146 78 %).
    FoodItem(
        id = "gen196",
        name = "Piscola (pisco con bebida cola)",
        brand = "Genérico",
        category = "bebida",
        servingSize = 300.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "RECIPE_ESTIMATE",
        sourceRecordId = "receta: FDC 174815 22 % + gen146 78 %",
        calories = 84.0,
        protein = 0.0,
        carbs = 8.1,
        fats = 0.2,
        caffeineMg = 6.0,
        tags = listOf("chileno"),
        searchAliases = listOf("piscola", "piscolas", "pisco con coca", "pisco con coca cola", "pisco con cola", "pisco cola"),
    ),
    FoodItem(
        id = "gen197",
        name = "Bebida destilada (pisco, ron, vodka, whisky)",
        brand = "Genérico",
        category = "bebida",
        servingSize = 45.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 174815, distilled spirits 80 proof)",
        sourceRecordId = "174815", // Alcoholic beverage, distilled, all (gin, rum, vodka, whiskey) 80 proof: 231 kcal por 100 g, todas del alcohol (7 kcal/g); un trago son 45 ml
        calories = 231.0,
        protein = 0.0,
        carbs = 0.0,
        fats = 0.0,
        searchAliases = listOf("pisco", "ron", "vodka", "whisky", "whiskey", "güisqui", "ginebra", "gin", "tequila", "destilado"),
    ),
    FoodItem(
        id = "gen198",
        name = "Vino espumante",
        brand = "Genérico",
        category = "bebida",
        servingSize = 120.0,
        unit = "ml",
        nutritionBasis = "PER_100G_AS_SOLD",
        source = "KPKN Curated (FDC 2710687, sparkling wine)",
        sourceRecordId = "2710687", // Wine, sparkling; una copa de espumante son 120 ml
        calories = 75.0,
        protein = 0.1,
        carbs = 2.3,
        fats = 0.0,
        searchAliases = listOf("espumante", "espumantes", "vino espumante", "champaña", "champana", "champagne", "champán"),
    ),
).withCuratedProvenance()

// ─── Chilean Foods ───────────────────────────────────────────────────────────

val CHILEAN_FOODS: List<FoodItem> = listOf(
    FoodItem(id = "cl001", name = "Empanada de Pino", servingSize = 180.0, unit = "u", calories = 450.0, protein = 18.0, carbs = 35.0, fats = 26.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("empanada", "empanadas", "empanada de pino")),
    FoodItem(id = "cl002", name = "Completo Italiano", servingSize = 200.0, unit = "u", calories = 380.0, protein = 12.0, carbs = 32.0, fats = 22.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl003", name = "Pastel de Choclo", servingSize = 250.0, unit = "g", calories = 420.0, protein = 22.0, carbs = 40.0, fats = 18.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl004", name = "Cazuela", servingSize = 400.0, unit = "ml", calories = 350.0, protein = 25.0, carbs = 30.0, fats = 12.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl005", name = "Mote con Huesillo", servingSize = 300.0, unit = "ml", calories = 220.0, protein = 2.0, carbs = 55.0, fats = 0.5, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl006", name = "Sopaipillas", servingSize = 60.0, unit = "u", calories = 150.0, protein = 3.0, carbs = 20.0, fats = 7.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("sopaipilla", "sopaipillas")),
    FoodItem(id = "cl007", name = "Porotos Granados", servingSize = 300.0, unit = "ml", calories = 380.0, protein = 16.0, carbs = 50.0, fats = 12.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl008", name = "Curanto", servingSize = 400.0, unit = "g", calories = 550.0, protein = 35.0, carbs = 45.0, fats = 22.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl009", name = "Chorrillana", servingSize = 400.0, unit = "g", calories = 750.0, protein = 30.0, carbs = 55.0, fats = 42.0, tags = listOf("preparacion", "chileno")),
    FoodItem(id = "cl010", name = "Marraqueta", servingSize = 100.0, unit = "g", calories = 260.0, protein = 9.0, carbs = 50.0, fats = 2.5, tags = listOf("chileno"), searchAliases = listOf("marraqueta", "marraquetas", "pan francés", "pan frances")),
    FoodItem(id = "cl011", name = "Pisco Sour", servingSize = 150.0, unit = "ml", calories = 220.0, protein = 0.5, carbs = 18.0, fats = 0.0, tags = listOf("chileno")),
    FoodItem(id = "cl012", name = "Terremoto", servingSize = 300.0, unit = "ml", calories = 350.0, protein = 1.0, carbs = 45.0, fats = 0.0, tags = listOf("chileno")),
    FoodItem(id = "cl013", name = "Hallulla", servingSize = 80.0, unit = "u", calories = 210.0, protein = 7.0, carbs = 40.0, fats = 3.0, tags = listOf("chileno"), searchAliases = listOf("hallulla", "hallullas", "hallula", "hallulas", "pan de hallulla")),
    FoodItem(id = "cl014", name = "Pan Amasado", servingSize = 80.0, unit = "u", calories = 230.0, protein = 6.0, carbs = 45.0, fats = 4.0, tags = listOf("chileno"), searchAliases = listOf("pan amasado", "amaso")),
    FoodItem(id = "cl015", name = "Charquicán", servingSize = 350.0, unit = "g", calories = 380.0, protein = 20.0, carbs = 35.0, fats = 16.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("charquicán", "charquican")),
    FoodItem(id = "cl016", name = "Longaniza Asada", servingSize = 100.0, unit = "g", calories = 310.0, protein = 16.0, carbs = 2.0, fats = 27.0, tags = listOf("chileno"), searchAliases = listOf("longaniza", "longacha", "chorizo chileno")),
    FoodItem(id = "cl017", name = "Caldillo de Congrio", servingSize = 400.0, unit = "ml", calories = 310.0, protein = 28.0, carbs = 18.0, fats = 12.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("caldillo de congrio", "caldillo")),
    FoodItem(id = "cl018", name = "Pebre", servingSize = 50.0, unit = "g", calories = 18.0, protein = 0.7, carbs = 4.0, fats = 0.3, tags = listOf("condimento", "chileno"), searchAliases = listOf("pebre")),
    FoodItem(id = "cl019", name = "Merluza Frita", servingSize = 150.0, unit = "g", calories = 290.0, protein = 22.0, carbs = 18.0, fats = 14.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("merluza frita", "pescado frito")),
    FoodItem(id = "cl020", name = "Leche con Plátano", servingSize = 300.0, unit = "ml", calories = 195.0, protein = 5.5, carbs = 38.0, fats = 2.5, tags = listOf("preparacion", "chileno"), searchAliases = listOf("leche con plátano", "leche con platano", "leche platano")),
).withCuratedProvenance()

// ─── Search Aliases ──────────────────────────────────────────────────────────

/**
 * Declared aliases: what a person writes -> the text of the catalog food it means. The target is only text; [FOOD_ALIAS_IDS]
 * resolves it to the id of ONE food ([resolveAliasTarget]), and every lookup (exact lookups, the anchor of the search ranker,
 * FoodIndex) answers from that id. A target must name a row of a catalog, by its name, its search alias or its name without
 * the "(...)" state ("papa" is "Papa (cocida)"); an alias that names nothing is dead, and FoodAliasConsistencyTest fails.
 * An alias that is NOT the same food as the word ("torta" -> pan blanco) belongs to [FOOD_ALIASES_APPROXIMATION].
 */
val FOOD_ALIASES: Map<String, String> = mapOf(
    // Sinónimos comunes
    "manzana" to "manzana",
    "plátano" to "plátano", "banana" to "plátano", "cambur" to "plátano", "platano" to "plátano",
    "pechuga" to "pechuga de pollo", "pechuga de pollo" to "pechuga de pollo",
    "poyo" to "pechuga de pollo",
    "trutro" to "trutro de pollo", "muslo" to "trutro de pollo", "muslo de pollo" to "trutro de pollo",
    "ala de pollo" to "ala de pollo", "alitas" to "ala de pollo",
    "arroz" to "arroz blanco", "arroz blanco" to "arroz blanco",
    "huevo" to "huevo entero", "huevos" to "huevo entero", "huevo cocido" to "huevo entero",
    // Atún desnudo → variante al agua (default silencioso).
    "atún" to "atún en lata",
    "atún al agua" to "atún en lata",
    "atun al agua" to "atún en lata",
    "leche" to "leche entera",
    "pan" to "pan blanco",
    "salmon" to "salmón", "salmón" to "salmón",
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
    "oatmeal" to "avena en hojuelas",
    "avena" to "avena en hojuelas",
    "maní" to "cacahuates", "cacahuate" to "cacahuates", "peanut" to "cacahuates",
    "porotos" to "porotos negros", "frijoles" to "porotos negros",
    "lentejas" to "lentejas",
    "lenteja" to "lentejas",
    "garbanzos" to "garbanzos",
    "garbanzo" to "garbanzos",
    "trigo" to "pan integral",
    "café" to "café",
    "té" to "té verde",
    // Nuevos alias
    "choclo" to "choclo desgranado", "maíz" to "choclo desgranado", "maiz" to "choclo desgranado",
    "zapallo" to "zapallo", "calabaza" to "zapallo",
    "betarraga" to "betarraga", "remolacha" to "betarraga",
    "kiwi" to "kiwi", "melón" to "melón", "sandia" to "sandía", "piña" to "piña",
    "pera" to "pera", "durazno" to "durazno", "ciruela" to "ciruela",
    "jamón" to "jamón cocido", "jamon" to "jamón cocido",
    "salchicha" to "salchicha tipo viena", "vienesa" to "salchicha tipo viena",
    "whey" to "proteína en polvo (whey)", "proteina en polvo" to "proteína en polvo (whey)",
    "creatina" to "creatina monohidrato",
    "red bull" to "red bull original",
    "redbull" to "red bull original",
    "monster" to "monster energy",
    "score" to "score energy drink",
    "winkler" to "winkler energy drink",
    "energética" to "red bull original",
    "energetica" to "red bull original",
    "marraqueta" to "marraqueta", "marraquetas" to "marraqueta",
    "hallulla" to "hallulla", "hallullas" to "hallulla",
    "hallula" to "hallulla", "hallulas" to "hallulla",
    "gouda" to "queso gauda", "gauda" to "queso gauda", "queso gouda" to "queso gauda",
    "panes" to "pan blanco",
    "longaniza" to "longaniza asada",
    "filete" to "filete de vacuno", "lomo" to "filete de vacuno",
    "merluza" to "merluza (cocida)",
    "camarón" to "camarón (cocido)", "camaron" to "camarón (cocido)",
    // ── Jerga Latinoamericana ──
    // Chile
    "empanada" to "empanada de pino", "empanadas" to "empanada de pino",
    "choripán" to "empanada de pino", "choripan" to "empanada de pino",
    "chorrillana" to "chorrillana",
    "completo" to "completo italiano", "completo italiano" to "completo italiano",
    "completo americano" to "completo americano",
    "sanguche" to "pan blanco", "sánduche" to "pan blanco", "sandwich" to "pan blanco",
    "lomito" to "filete de vacuno",
    "terremoto" to "terremoto",
    "once" to "pan blanco",
    "pan con palta" to "pan con palta",
    "pan con queso" to "pan con queso",
    "pan con jamon" to "pan con jamón",
    "bistec a lo pobre" to "bistec a lo pobre",
    "lomo a lo pobre" to "bistec a lo pobre",
    "porotos con riendas" to "porotos con riendas",
    // México
    "taco" to "taco", "tacos" to "taco",
    "torta" to "pan blanco", "torta de jamón" to "pan blanco",
    "gordita" to "pan blanco",
    "quesadilla" to "queso cheddar",
    "chilaquiles" to "tortilla de trigo",
    "pozole" to "porotos granados",
    "tamal" to "tamal", "tamales" to "tamal",
    // Argentina / Uruguay
    "milanga" to "filete de vacuno", "milanesa" to "filete de vacuno",
    "milanesa napolitana" to "filete de vacuno",
    "facturas" to "pan blanco", "medialunas" to "pan blanco",
    "asado" to "filete de vacuno",
    "bife" to "filete de vacuno", "bife de chorizo" to "filete de vacuno",
    "provoleta" to "queso cheddar",
    // Perú
    "ceviche" to "ceviche", "cebiche" to "ceviche",
    "lomo saltado" to "filete de vacuno",
    "ají de gallina" to "pechuga de pollo",
    "causa" to "papa",
    "anticucho" to "filete de vacuno",
    // Colombia / Venezuela
    "cachapa" to "choclo desgranado",
    "pabellón" to "arroz blanco", "pabellon" to "arroz blanco",
    "tequeños" to "queso cheddar",
    "bandeja paisa" to "arroz blanco",
    "empanada colombiana" to "empanada de pino",
    // Internacionales
    "burguer" to "hamburguesa", "burger" to "hamburguesa",
    "hotdog" to "salchicha tipo viena", "hot dog" to "salchicha tipo viena",
    "galleta de chocolate" to "galletas de chocolate",
    "galletas de chocolate" to "galletas de chocolate",
    "galleta salada" to "galletas saladas",
    "galletas saladas" to "galletas saladas",
    "galleta de avena" to "galletas de avena",
    "galletas de avena" to "galletas de avena",
    // "galleta(s)" is a category, not a food: the catalog has one row per kind of cookie and none for the word alone, so it
    // stays an approximation (generic sweet, never bread) and the person is asked which cookie it was.
    "galleta" to "dulce genérico",
    "galletas" to "dulce genérico",
    "cereal" to "cereal de desayuno",
    "batido" to "leche entera",
    "smoothie" to "leche entera",
    "ensalada" to "lechuga",
    // Hidratación
    "fideos secos" to "pasta (cruda)",
    "tallarines secos" to "pasta (cruda)",
    "lentejas secas" to "lentejas (crudas)",
    "lentejas remojadas" to "lentejas (hidratadas)",
    "garbanzos secos" to "garbanzos (cocidos)",
    "garbanzos remojados" to "garbanzos (hidratados)",
    "pasta seca" to "pasta (cruda)",
    "arroz seco" to "arroz blanco (crudo)",
    "soya seca" to "soya texturizada (seca)",
    "soya hidratada" to "soya texturizada (hidratada)",
    "pvt seca" to "soya texturizada (seca)",
    "pvt hidratada" to "soya texturizada (hidratada)",
    // Cortes de carne
    "posta" to "posta rosada (cruda)",
    "posta rosada" to "posta rosada (cruda)",
    "posta negra" to "posta negra (cruda)",
    "asado de tira" to "asado de tira (crudo)",
    "costillar" to "asado de tira (crudo)",
    "lomo vetado" to "lomo vetado (crudo)",
    "vetado" to "lomo vetado (crudo)",
    "punta de ganso" to "punta de ganso (cruda)",
    "pulpa negra" to "pulpa negra (cruda)",
    "plateada" to "plateada (cruda)",
    "churrasco" to "churrasco (crudo)",
    "malaya" to "malaya (cruda)",
    "huachalomo" to "huachalomo (crudo)",
    // Casino / contexto
    "queque del casino" to "queque",
    "café de máquina" to "café (negro)",
    // Postres / dulces
    "arroz con leche" to "arroz con leche",
    "leche asada" to "leche asada",
    // Preparaciones
    "ensalada chilena" to "ensalada chilena",
    "porotos granados" to "porotos granados",
    "cazuela" to "cazuela",
    "humitas" to "humitas",
    "pastel de papa" to "pastel de papa",
    "sopaipillas" to "sopaipillas",
    "sopaipillas pasadas" to "sopaipillas pasadas",
    "calzones rotos" to "calzones rotos",
)

/**
 * Alias que NO son el mismo alimento que la consulta (aproximación): el destino
 * es lo "más parecido" del catálogo, no la identidad del plato escrito
 * ("torta" ≈ pan blanco, "ensalada" ≈ lechuga, "milanesa" ≈ filete de vacuno). Un alimento con ficha propia (ceviche, cereal) ya no es
 * aproximación: su alias apunta a la ficha.
 * Siguen siendo aproximaciones (el plato escrito no es la ficha), pero el
 * sistema elige el genérico y guarda sin preguntar.
 * Las claves se normalizan sin tildes y en minúsculas.
 */
val FOOD_ALIASES_APPROXIMATION: Set<String> = setOf(
    // Chile
    "choripan", "sanguche", "sanduche", "sandwich", "once", "lomito",
    // México
    "torta", "torta de jamon", "gordita", "quesadilla", "chilaquiles", "pozole",
    // Argentina / Uruguay
    "milanga", "milanesa", "milanesa napolitana", "facturas", "medialunas", "asado", "provoleta",
    // Perú
    "lomo saltado", "aji de gallina", "causa", "anticucho",
    // Colombia / Venezuela
    "cachapa", "pabellon", "tequeños", "bandeja paisa",
    "empanada colombiana",
    // Internacionales / conceptos generales
    "batido", "smoothie", "ensalada", "trigo",
    "galleta", "galletas",
)

/** Claves de aproximación una sola vez, con la misma clave que los alias ([foodAliasKey]: sin tildes, en singular). */
private val FOOD_ALIASES_APPROXIMATION_NORMALIZED: Set<String> by lazy {
    FOOD_ALIASES_APPROXIMATION.map(::foodAliasKey).toSet()
}

/** True si el texto es un alias de aproximación (identidad distinta a lo escrito): "torta" y "tortas". */
fun isApproximationAlias(text: String): Boolean = foodAliasKey(text) in FOOD_ALIASES_APPROXIMATION_NORMALIZED

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
    PortionRef("pinch", 2.0),
    PortionRef("little", 20.0),
    PortionRef("splash", 10.0),
    PortionRef("glass", 250.0),
    PortionRef("slice", 30.0),
    PortionRef("can", 200.0),
    PortionRef("portion", 150.0),
    PortionRef("scoop", 30.0),
    PortionRef("palm", 80.0),
    PortionRef("fist", 100.0),
    PortionRef("piece", 120.0),
)

// Cacheado estático para evitar la concatenación repetida de miles de elementos
private val ALL_FOODS: List<FoodItem> by lazy { GENERIC_FOODS + CHILEAN_FOODS }

private val foodById: Map<String, FoodItem> by lazy { ALL_FOODS.associateBy { it.id } }

/**
 * Ids that no longer name a row of the static catalog -> the row that replaced them. A learned resolution (the personal mapping of
 * "arroz integral" to a food id) keeps the id it was saved with, so a deleted row must keep answering (WP-S11: gen136 was a second
 * "Arroz Integral (cocido)" with other values and was merged into gen006; WP-S10: cl039 was the same "Charquicán" as cl015, which is the
 * row every lookup answered, and was merged into it).
 */
val LEGACY_FOOD_ID_REDIRECTS: Map<String, String> = mapOf("gen136" to "gen006", "cl039" to "cl015")

/** [id] as the catalog knows it today: the row that replaced a deleted one, or [id] itself. */
fun resolveLegacyFoodId(id: String): String = LEGACY_FOOD_ID_REDIRECTS[id] ?: id

fun findStaticFoodById(id: String): FoodItem? = foodById[resolveLegacyFoodId(id)]

/** Multi-word catalog phrases used by the deterministic parser before connectors split them. */
fun staticFoodPhrases(): List<String> = ALL_FOODS
    .flatMap { food -> listOf(food.name) + food.searchAliases }
    .map(String::trim)
    .filter { it.contains(' ') }
    .distinctBy { stripAccents(it.lowercase()) }

private val AMBIGUOUS_STATE_ALIASES = setOf(
    "pasta", "fideo", "fideos", "tallarin", "tallarines",
)

// Compiled/allocated once: findFoodByNormalized used to rebuild both for every catalog food on every call.
private val NAME_WORD_SPLIT = "[\\s(),/]+".toRegex()

// "Té sin azúcar" has no sugar: a declared absence is an attribute of the food, never a word of its identity, so a
// query for "azúcar" must not find the tea (and borrow its serving size).
private val DECLARED_ABSENCE_WORDS = Regex("""\bsin\s+\S+""")

private fun nameForMatching(name: String): String {
    val lower = name.lowercase()
    // The lookups below run over the whole catalog on every call: only a name with a "sin" pays for the regex.
    return if (" sin " in lower) lower.replace(DECLARED_ABSENCE_WORDS, " ") else lower
}
private val FILLER_WORDS = setOf("de", "con", "y", "e", "la", "el")

// ─── Alias ids (WP-S6) ───────────────────────────────────────────────────────

/**
 * Key of an alias or of a query: [TextKeys.normalize] (accent-free, lower case, split on whatever is not a letter or a
 * digit) with every word folded to its singular by [FoodSearchRanker.stem], the one plural rule of the search ranker and
 * of FoodIndex. "Papas", "papa" and "PAPAS" share a key, and "huevos" finds the alias declared as "huevo".
 */
fun foodAliasKey(text: String): String {
    val normalized = TextKeys.normalize(text)
    if (' ' !in normalized) return FoodSearchRanker.stem(normalized)
    return normalized.split(' ').joinToString(" ") { FoodSearchRanker.stem(it) }
}

/** The curated branded catalogs, programmatic part only: their names are known without a Context. */
private val BRANDED_FOODS: List<FoodItem> by lazy { BrandedEnergyKcalCatalog.load(null) + BrandedSnackCatalog.load(null) }

/** Trailing "(...)" qualifiers of a name: "Papa (cocida)" is the food "Papa" in one of its states. */
private val TRAILING_QUALIFIERS = Regex("""(?:\s*\([^()]*\))+\s*$""")

/** The words a "(...)" qualifier uses when it states nothing but the state of the food ("cocida", "hidratada/cocida"). */
private val PLAIN_STATE_WORDS = setOf(
    "cocido", "cocida", "cocidos", "cocidas", "crudo", "cruda", "crudos", "crudas",
    "hidratado", "hidratada", "hidratados", "hidratadas", "seco", "seca", "secos", "secas",
)

/**
 * The rows an alias target can name, indexed by the keys [resolveAliasTarget] compares: the static catalog (the only rows
 * the household logic may default to) and the curated branded catalogs.
 */
private class AliasUniverse(foods: List<FoodItem>) {
    val byId: Map<String, FoodItem> = foods.associateBy { it.id }

    /** [TextKeys.normalize] of every name and search alias -> the rows that carry it. */
    val exact: Map<String, List<FoodItem>>

    /** Names that carry a trailing qualifier, without it ("Papa (cocida)" -> "papa") -> the rows. */
    val bare: Map<String, List<FoodItem>>

    init {
        val exactKeys = HashMap<String, MutableList<FoodItem>>()
        val bareKeys = HashMap<String, MutableList<FoodItem>>()
        for (food in foods) {
            (listOf(food.name) + food.searchAliases).map(TextKeys::normalize).filter { it.isNotEmpty() }.distinct()
                .forEach { exactKeys.getOrPut(it) { ArrayList(2) }.add(food) }
            if (TRAILING_QUALIFIERS.containsMatchIn(food.name)) {
                val bareName = TextKeys.normalize(TRAILING_QUALIFIERS.replace(food.name, ""))
                if (bareName.isNotEmpty()) bareKeys.getOrPut(bareName) { ArrayList(2) }.add(food)
            }
        }
        exact = exactKeys
        bare = bareKeys
    }
}

private val ALIAS_UNIVERSE: AliasUniverse by lazy { AliasUniverse(ALL_FOODS + BRANDED_FOODS) }

/** COOKED before HYDRATED before RAW before UNKNOWN: the state a person eats when the food is named without one. */
private fun stateRank(food: FoodItem): Int = when (FoodIdentity.stateFor(food)) {
    FoodState.COOKED -> 0
    FoodState.HYDRATED -> 1
    FoodState.RAW -> 2
    FoodState.UNKNOWN -> 3
}

/** True when the "(...)" qualifier of the name only states the state of the food ("Papa (cocida)"), not a method ("Papa (frita)"). */
private fun hasPlainStateQualifier(food: FoodItem): Boolean {
    val qualifier = TRAILING_QUALIFIERS.find(food.name)?.value ?: return true
    return TextKeys.normalize(qualifier).split(' ').all { it in PLAIN_STATE_WORDS }
}

/**
 * Which of several rows an alias target names: the static catalog before the branded rows, then the state a person eats
 * ([stateRank]), then a plain state qualifier before a method ("Papa (cocida)" before "Papa (frita)"), then the lowest id
 * ("gen007" before "gen007f"). The choice never depends on the order the catalogs list their rows in.
 */
private val ALIAS_CANDIDATE_ORDER: Comparator<FoodItem> by lazy {
    compareBy<FoodItem>(
        { if (foodById.containsKey(it.id)) 0 else 1 },
        ::stateRank,
        { if (hasPlainStateQualifier(it)) 0 else 1 },
        { it.id.length },
        { it.id },
    )
}

/**
 * The id of the food a declared alias target names, or null when it names none. In order:
 *  1. the household staple graph (FoodStapleOntology): "pechuga de pollo" is the cooked breast a person means, not the first
 *     row whose name happens to contain those words. It is skipped when the target declares a state the graph's food does not
 *     have: "arroz blanco (crudo)" is not the cooked rice the "arroz blanco" node answers;
 *  2. a row with exactly that name or search alias, accent-insensitive ("plátano", "red bull original");
 *  3. a row whose name is the target once its trailing qualifiers are dropped ("huevo entero" is "Huevo Entero (cocido)"),
 *     the state a person eats first ([ALIAS_CANDIDATE_ORDER]).
 * Never by `contains`: that is how "pechuga" and "nuggets" ended up on raw chicken breast and a dozen aliases on nothing.
 */
fun resolveAliasTarget(value: String): String? {
    val key = TextKeys.normalize(value)
    if (key.isEmpty()) return null
    val universe = ALIAS_UNIVERSE
    FoodStapleOntology.resolveFoodId(value)?.let { id ->
        val declared = FoodIdentity.stateFor(value)
        val food = universe.byId[id]
        if (food != null && (declared == FoodState.UNKNOWN || FoodIdentity.stateFor(food) == declared)) return id
    }
    universe.exact[key]?.let { rows -> return rows.minWithOrNull(ALIAS_CANDIDATE_ORDER)?.id }
    universe.bare[key]?.let { rows -> return rows.minWithOrNull(ALIAS_CANDIDATE_ORDER)?.id }
    return null
}

/**
 * Every alias of [FOOD_ALIASES] resolved to the id of the food it names, keyed by [foodAliasKey] (accent-free, singular).
 * The target text is resolved by [resolveAliasTarget]; an alias whose target names nothing is absent, and
 * FoodAliasConsistencyTest keeps the table free of them. When two aliases share a key the first of the table wins.
 */
val FOOD_ALIAS_IDS: Map<String, String> by lazy {
    buildMap {
        FOOD_ALIASES.forEach { (alias, target) ->
            val key = foodAliasKey(alias)
            if (key.isEmpty()) return@forEach
            resolveAliasTarget(target)?.let { id -> putIfAbsent(key, id) }
        }
    }
}

/**
 * The row of the static catalog (GENERIC_FOODS + CHILEAN_FOODS) that [query] names, whatever its `source` text says: the
 * household staple graph first ("pollo", "pechuga cruda"), then a declared alias ("banana", "huevos"), then the name or
 * search alias of a row. It is the anchor of the search ranker and the household default of a query: unlike the catalog
 * lookup of HouseholdPortions it never drops a curated row because its source reads "USDA" (leche entera, tomate, agua).
 */
fun staticFoodForAlias(query: String): FoodItem? {
    if (query.isBlank()) return null
    FoodStapleOntology.resolveFoodId(query)?.let(::findStaticFoodById)?.let { return it }
    return findFoodExactByNormalized(query)
}

// ─── Lookup Helpers ──────────────────────────────────────────────────────────

// O(1) HashMap para búsqueda rápida por nombre exacto: nombre y alias de cada ficha. Los alias declarados en FOOD_ALIASES
// se resuelven por id (FOOD_ALIAS_IDS), nunca por coincidencia parcial de nombre.
private val foodByExactName: Map<String, FoodItem> by lazy {
    buildMap {
        ALL_FOODS.sortedBy { it.id }.forEach { food ->
            putIfAbsent(food.name.lowercase(), food)
            food.searchAliases.forEach { alias -> putIfAbsent(alias.lowercase(), food) }
        }
        // G7: claves sin tildes → "salmon" y "salmón" resuelven igual
        val existingKeys = keys.toList()
        existingKeys.forEach { key ->
            putIfAbsent(stripAccents(key), getValue(key))
        }
    }
}

/** The same keys with every word folded to its singular: "papa fritas" meets "Papas fritas" in one lookup, not a scan. */
private val foodByAgreementKey: Map<String, FoodItem> by lazy {
    buildMap { foodByExactName.forEach { (key, food) -> putIfAbsent(agreementKey(key), food) } }
}

private fun agreementKey(text: String): String =
    stripAccents(text).split(' ').joinToString(" ") { FoodSearchRanker.stem(it) }

private fun stripAccents(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(TextKeys.MARKS, "")

/** O(1) exact lookup: a declared alias (by id), then the name or search alias of a row, with and without accents. */
private fun exactLookup(normalized: String, stripped: String): FoodItem? {
    FOOD_ALIAS_IDS[foodAliasKey(normalized)]?.let(::findStaticFoodById)?.let { return it }
    foodByExactName[normalized]?.let { return it }
    if (stripped != normalized) foodByExactName[stripped]?.let { return it }
    return null
}

/**
 * Lookup SOLO por coincidencia exacta/alias O(1) (sin fallbacks difusos por palabras).
 * Permite distinguir un match estático de alta precisión de uno fuzzy.
 */
fun findFoodExactByNormalized(text: String): FoodItem? {
    val normalized = text.trim().lowercase()
    val stripped = stripAccents(normalized)
    if (stripped in AMBIGUOUS_STATE_ALIASES) return null
    exactLookup(normalized, stripped)?.let { return it }
    // Agreement errors ("papa fritas") retain the same exact lexical identity.
    return foodByAgreementKey[agreementKey(stripped)]
}

fun findFoodByNormalized(text: String): FoodItem? {
    val normalized = text.trim().lowercase()
    val stripped = stripAccents(normalized)
    if (stripped in AMBIGUOUS_STATE_ALIASES) return null
    val alias = FOOD_ALIASES[normalized] ?: normalized

    // O(1) exact lookup
    exactLookup(normalized, stripped)?.let { return it }

    val allFoods = ALL_FOODS
    val aliasWords = alias.split(NAME_WORD_SPLIT).filter { it.length > 1 }
    val contentWords = aliasWords.filter { it !in FILLER_WORDS }
    if (aliasWords.isNotEmpty()) {
        val matches = allFoods.filter { food ->
            val foodWords = nameForMatching(food.name).split(NAME_WORD_SPLIT).filter { it.length > 1 }
            aliasWords.all { aw -> foodWords.any { fw -> fw == aw } } &&
                !queryStealsChild(contentWords, food.name)
        }
        val chosen = if (contentWords.size >= 2) {
            val head = contentWords.first()
            matches.minByOrNull { food ->
                val foodWords = nameForMatching(food.name)
                    .split(NAME_WORD_SPLIT)
                    .filter { it.length > 1 && it !in FILLER_WORDS }
                val extra = (foodWords.size - contentWords.size).coerceAtLeast(0)
                val foodHead = foodWords.firstOrNull()
                val headPenalty = if (foodHead == head || foodHead?.startsWith(head) == true || head.startsWith(foodHead ?: "")) 0 else 80
                extra + headPenalty
            }
        } else {
            matches.firstOrNull()
        }
        chosen?.let { return it }
    }

    // Segundo fallback inverso: que todas las palabras de la comida estén en el alias.
    // Queries de 2+ tokens no pueden elegir un hijo (taco de pollo ≠ Pollo).
    allFoods.find { food ->
        val foodNameLower = nameForMatching(food.name)
        if (foodNameLower.length <= 3) return@find false
        val foodWords = foodNameLower.split(NAME_WORD_SPLIT).filter { it.length > 2 }
        if (foodWords.isEmpty()) return@find false
        val aliasWordsAll = alias.split(NAME_WORD_SPLIT)
        foodWords.all { fw -> aliasWordsAll.any { aw -> aw == fw || aw.startsWith(fw) } } &&
            !queryStealsChild(contentWords, food.name)
    }?.let { return it }

    return null
}

private fun queryStealsChild(queryContent: List<String>, foodName: String): Boolean {
    if (queryContent.size < 2) return false
    val head = queryContent.first()
    val foodTokens = nameForMatching(foodName).split(NAME_WORD_SPLIT).filter { it.length > 1 }
    val foodHasHead = foodTokens.any { it == head || it.startsWith(head) || head.startsWith(it) }
    if (foodHasHead) return false
    return foodTokens.isNotEmpty() && foodTokens.all { ft -> queryContent.any { it == ft || it.contains(ft) } }
}

fun getGramsForReference(refType: String, foodItem: FoodItem? = null): Double {
    val base = PORTION_REFERENCES.find { it.refType == refType }?.grams ?: 100.0
    return when (refType) {
        "palm" -> when {
            foodItem != null && foodItem.protein > 20 -> 80.0 // meat palm
            else -> 100.0
        }
        "handful" -> when {
            foodItem != null && foodItem.fats > 30 -> 30.0
            else -> 30.0
        }
        "bowl" -> when {
            foodItem != null && foodItem.name.lowercase().contains("avena") -> 40.0
            else -> 250.0
        }
        "little" -> when {
            foodItem != null && foodItem.calories > 500 -> 10.0 // alta densidad calórica (aceite, mantequilla)
            foodItem != null && foodItem.fats > 40 -> 10.0
            else -> 20.0
        }
        "pinch" -> 2.0
        "splash" -> when {
            foodItem != null && foodItem.calories > 400 -> 5.0
            else -> 10.0
        }
        else -> base
    }
}
