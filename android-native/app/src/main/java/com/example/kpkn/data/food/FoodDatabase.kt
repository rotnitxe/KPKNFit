package com.example.kpkn.data.food

import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.Micronutrient

/**
 * FoodDatabase — Static food catalog for KPKN Fit.
 * Mirrors data/foodDatabase.ts + data/foodDatabaseExpansion.ts from PWA.
 * Contains generic foods (90 items), plus Chilean-specific foods.
 */

fun buildFoodDatabase(): List<FoodItem> = ALL_FOODS

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
    // servingSize=100g (valores por 100g seca).
    // cookingWeightFactor ≈ hidratación típica (100 g seca → ~350 g hidratada).
    FoodItem(id = "gen057", name = "Soya texturizada (seca)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 340.0, protein = 52.0, carbs = 33.0, fats = 1.2,
        cookingWeightFactor = 3.5,
        searchAliases = listOf("soya texturizada", "pvt", "proteina vegetal", "carne vegetal", "soja texturizada"),
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
    // ─── Verduras ────────────────────────────────────────────────────────────
    FoodItem(id = "gen066", name = "Lechuga", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 15.0, protein = 1.4, carbs = 2.9, fats = 0.2, searchAliases = listOf("lechuga", "ensalada verde")),
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
    FoodItem(id = "gen084", name = "Queso Fresco", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 98.0, protein = 6.7, carbs = 2.7, fats = 7.1, searchAliases = listOf("queso fresco", "queso blanco")),
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
    FoodItem(id = "gen105", name = "Proteína en Polvo (Whey)", brand = "Genérico", servingSize = 30.0, unit = "g", calories = 120.0, protein = 24.0, carbs = 3.0, fats = 2.0, searchAliases = listOf("whey", "proteína en polvo", "proteina", "suero de leche", "proteína whey", "whey protein")),
    FoodItem(id = "gen106", name = "Creatina Monohidrato", brand = "Genérico", servingSize = 5.0, unit = "g", calories = 0.0, protein = 0.0, carbs = 0.0, fats = 0.0, searchAliases = listOf("creatina", "creatine")),
    // ─── Variantes de cocción (top 30 alimentos) ─────────────────────────────
    // Pollo
    FoodItem(id = "gen003c", name = "Pechuga de Pollo (plancha)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 173.0, protein = 31.0, carbs = 0.0, fats = 3.4, searchAliases = listOf("pollo a la plancha", "pechuga plancha")),
    FoodItem(id = "gen003f", name = "Pechuga de Pollo (frita)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 223.0, protein = 30.0, carbs = 0.0, fats = 10.9, searchAliases = listOf("pollo frito", "pechuga frita")),
    FoodItem(id = "gen003h", name = "Pechuga de Pollo (horno)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 168.0, protein = 31.0, carbs = 0.0, fats = 3.5, searchAliases = listOf("pollo al horno", "pechuga al horno")),
    FoodItem(id = "gen003v", name = "Pechuga de Pollo (vapor)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 160.0, protein = 31.0, carbs = 0.0, fats = 3.2, searchAliases = listOf("pollo al vapor", "pechuga vapor")),
    FoodItem(id = "gen003p", name = "Pechuga de Pollo (parrilla)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 170.0, protein = 31.0, carbs = 0.0, fats = 3.3, searchAliases = listOf("pollo a la parrilla", "pechuga parrilla")),
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
    FoodItem(id = "gen021f", name = "Papa (frita)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 312.0, protein = 3.4, carbs = 41.0, fats = 15.0, searchAliases = listOf("papa frita", "papas fritas", "papa fritas")),
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
    FoodItem(id = "cl039", name = "Charquicán", servingSize = 350.0, unit = "g", calories = 380.0, protein = 20.0, carbs = 35.0, fats = 16.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("charquican", "charquicán")),
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
    FoodItem(id = "gen129", name = "Taco", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 220.0, protein = 8.0, carbs = 22.0, fats = 11.0, searchAliases = listOf("taco", "tacos")),
    FoodItem(id = "gen130", name = "Burrito", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 200.0, protein = 8.0, carbs = 26.0, fats = 7.0, searchAliases = listOf("burrito", "burritos")),
    FoodItem(id = "gen131", name = "Sushi", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 150.0, protein = 6.0, carbs = 25.0, fats = 3.0, searchAliases = listOf("sushi")),
    FoodItem(id = "gen132", name = "Donut", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 420.0, protein = 5.0, carbs = 51.0, fats = 22.0, searchAliases = listOf("donut", "dona", "donuts", "donas")),
    // E16: identidad faltante detectada por el contrato de métricas (baseline 87%)
    FoodItem(id = "gen133", name = "Pan Integral", brand = "Genérico", servingSize = 50.0, unit = "u", calories = 265.0, protein = 9.5, carbs = 45.0, fats = 4.2, searchAliases = listOf("pan integral", "pan de molde integral", "pan centeno")),
    FoodItem(id = "gen134", name = "Sopa (casera)", brand = "Genérico", servingSize = 250.0, unit = "ml", calories = 42.0, protein = 2.0, carbs = 4.0, fats = 1.5, searchAliases = listOf("sopa", "sopa casera", "caldo", "sopa de verduras", "sopa de pollo")),
    FoodItem(id = "gen135", name = "Porotos (cocidos)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 140.0, protein = 8.5, carbs = 25.0, fats = 0.5, searchAliases = listOf("porotos", "poroto", "porotos cocidos", "porotos negros", "frijoles", "frijol")),
    FoodItem(id = "gen136", name = "Arroz Integral (cocido)", brand = "Genérico", servingSize = 100.0, unit = "g", calories = 123.0, protein = 2.7, carbs = 26.0, fats = 0.9, searchAliases = listOf("arroz integral", "arroz integral cocido")),
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
    FoodItem(id = "cl010", name = "Marraqueta", servingSize = 100.0, unit = "g", calories = 260.0, protein = 9.0, carbs = 50.0, fats = 2.5, tags = listOf("chileno"), searchAliases = listOf("marraqueta", "pan francés", "pan frances")),
    FoodItem(id = "cl011", name = "Pisco Sour", servingSize = 150.0, unit = "ml", calories = 220.0, protein = 0.5, carbs = 18.0, fats = 0.0, tags = listOf("chileno")),
    FoodItem(id = "cl012", name = "Terremoto", servingSize = 300.0, unit = "ml", calories = 350.0, protein = 1.0, carbs = 45.0, fats = 0.0, tags = listOf("chileno")),
    FoodItem(id = "cl013", name = "Hallulla", servingSize = 80.0, unit = "u", calories = 210.0, protein = 7.0, carbs = 40.0, fats = 3.0, tags = listOf("chileno"), searchAliases = listOf("hallulla", "pan de hallulla")),
    FoodItem(id = "cl014", name = "Pan Amasado", servingSize = 80.0, unit = "u", calories = 230.0, protein = 6.0, carbs = 45.0, fats = 4.0, tags = listOf("chileno"), searchAliases = listOf("pan amasado", "amaso")),
    FoodItem(id = "cl015", name = "Charquicán", servingSize = 350.0, unit = "g", calories = 380.0, protein = 20.0, carbs = 35.0, fats = 16.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("charquicán", "charquican")),
    FoodItem(id = "cl016", name = "Longaniza Asada", servingSize = 100.0, unit = "g", calories = 310.0, protein = 16.0, carbs = 2.0, fats = 27.0, tags = listOf("chileno"), searchAliases = listOf("longaniza", "longacha", "chorizo chileno")),
    FoodItem(id = "cl017", name = "Caldillo de Congrio", servingSize = 400.0, unit = "ml", calories = 310.0, protein = 28.0, carbs = 18.0, fats = 12.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("caldillo de congrio", "caldillo")),
    FoodItem(id = "cl018", name = "Pebre", servingSize = 50.0, unit = "g", calories = 18.0, protein = 0.7, carbs = 4.0, fats = 0.3, tags = listOf("condimento", "chileno"), searchAliases = listOf("pebre")),
    FoodItem(id = "cl019", name = "Merluza Frita", servingSize = 150.0, unit = "g", calories = 290.0, protein = 22.0, carbs = 18.0, fats = 14.0, tags = listOf("preparacion", "chileno"), searchAliases = listOf("merluza frita", "pescado frito")),
    FoodItem(id = "cl020", name = "Leche con Plátano", servingSize = 300.0, unit = "ml", calories = 195.0, protein = 5.5, carbs = 38.0, fats = 2.5, tags = listOf("preparacion", "chileno"), searchAliases = listOf("leche con plátano", "leche con platano", "leche platano")),
)

// ─── Search Aliases ──────────────────────────────────────────────────────────

val FOOD_ALIASES: Map<String, String> = mapOf(
    // Sinónimos comunes
    "manzana" to "manzana",
    "plátano" to "plátano", "banana" to "plátano", "cambur" to "plátano", "platano" to "plátano",
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
    "whey" to "proteína en polvo (whey)", "proteína" to "proteína en polvo (whey)",
    "creatina" to "creatina monohidrato",
    "marraqueta" to "marraqueta", "hallulla" to "hallulla",
    "longaniza" to "longaniza asada",
    "filete" to "filete de vacuno", "lomo" to "filete de vacuno",
    "merluza" to "merluza (cocida)",
    "camarón" to "camarón (cocido)", "camaron" to "camarón (cocido)",
    // ── Jerga Latinoamericana ──
    // Chile
    "choripán" to "empanada de pino", "choripan" to "empanada de pino",
    "chorrillana" to "chorrillana",
    "completo" to "completo italiano", "completo italiano" to "completo italiano",
    "completo americano" to "completo americano",
    "pichanga" to "pichanga",
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
    "ceviche" to "merluza (cocida)", "cebiche" to "merluza (cocida)",
    "lomo saltado" to "filete de vacuno",
    "ají de gallina" to "pechuga de pollo",
    "causa" to "papa",
    "anticucho" to "filete de vacuno",
    // Colombia / Venezuela
    "arepa" to "pan blanco", "arepa reina pepiada" to "pan blanco",
    "cachapa" to "choclo desgranado",
    "pabellón" to "arroz blanco", "pabellon" to "arroz blanco",
    "tequeños" to "queso cheddar",
    "bandeja paisa" to "arroz blanco",
    "empanada colombiana" to "empanada de pino",
    // Internacionales
    "burguer" to "hamburguesa", "burger" to "hamburguesa",
    "hotdog" to "salchicha tipo viena", "hot dog" to "salchicha tipo viena",
    "nuggets" to "pechuga de pollo",
    "galleta" to "pan blanco", "galletas" to "pan blanco",
    "cereal" to "avena",
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
    "galletas del casino" to "galleta",
    "café de máquina" to "café (negro)",
    "café con leche" to "leche entera",
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
 * ("torta" ≈ pan blanco, "ensalada" ≈ lechuga, "ceviche" ≈ merluza cocida).
 * Estos NUNCA se auto-confirman: pasan a revisión con aviso visible.
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
    "ceviche", "cebiche", "lomo saltado", "aji de gallina", "causa", "anticucho",
    // Colombia / Venezuela
    "arepa", "arepa reina pepiada", "cachapa", "pabellon", "tequeños", "bandeja paisa",
    "empanada colombiana",
    // Internacionales / conceptos generales
    "galleta", "galletas", "cereal", "batido", "smoothie", "ensalada", "trigo",
    "cafe con leche", "nuggets",
)

/** Claves de aproximación normalizadas una sola vez (sin tildes, minúsculas). */
private val FOOD_ALIASES_APPROXIMATION_NORMALIZED: Set<String> by lazy {
    FOOD_ALIASES_APPROXIMATION.map { stripAccents(it.trim().lowercase()) }.toSet()
}

/** True si el texto es un alias de aproximación (identidad distinta a lo escrito). */
fun isApproximationAlias(text: String): Boolean {
    val normalized = stripAccents(text.trim().lowercase())
    return normalized in FOOD_ALIASES_APPROXIMATION_NORMALIZED
}

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

/** Multi-word catalog phrases used by the deterministic parser before connectors split them. */
fun staticFoodPhrases(): List<String> = ALL_FOODS
    .flatMap { food -> listOf(food.name) + food.searchAliases }
    .map(String::trim)
    .filter { it.contains(' ') }
    .distinctBy { stripAccents(it.lowercase()) }

private val AMBIGUOUS_STATE_ALIASES = setOf(
    "pasta", "fideo", "fideos", "tallarin", "tallarines",
)

// ─── Lookup Helpers ──────────────────────────────────────────────────────────

// O(1) HashMap para búsqueda rápida por nombre exacto
private val foodByExactName: Map<String, FoodItem> by lazy {
    val allFoods = ALL_FOODS
    buildMap {
        allFoods.forEach { food ->
            put(food.name.lowercase(), food)
            food.searchAliases.forEach { alias -> putIfAbsent(alias.lowercase(), food) }
        }
        // Pre-resolver todos los aliases del mapa FOOD_ALIASES
        FOOD_ALIASES.forEach { (key, value) ->
            if (!containsKey(key.lowercase())) {
                val target = allFoods.find { it.name.lowercase() == value.lowercase() }
                    ?: allFoods.find { it.name.lowercase().contains(value.lowercase()) }
                if (target != null) put(key.lowercase(), target)
            }
        }
        // G7: claves sin tildes → "salmon" y "salmón" resuelven igual
        val existingKeys = keys.toList()
        existingKeys.forEach { key ->
            putIfAbsent(stripAccents(key), getValue(key))
        }
    }
}

private fun stripAccents(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")

/**
 * Lookup SOLO por coincidencia exacta/alias O(1) (sin fallbacks difusos por palabras).
 * Permite distinguir un match estático de alta precisión de uno fuzzy.
 */
fun findFoodExactByNormalized(text: String): FoodItem? {
    val normalized = text.trim().lowercase()
    if (stripAccents(normalized) in AMBIGUOUS_STATE_ALIASES) return null
    val alias = FOOD_ALIASES[normalized] ?: normalized
    foodByExactName[alias]?.let { return it }
    foodByExactName[normalized]?.let { return it }
    val stripped = stripAccents(normalized)
    if (stripped != normalized) foodByExactName[stripped]?.let { return it }
    val strippedAlias = stripAccents(alias)
    if (strippedAlias != stripped) foodByExactName[strippedAlias]?.let { return it }
    return null
}

fun findFoodByNormalized(text: String): FoodItem? {
    val normalized = text.trim().lowercase()
    if (stripAccents(normalized) in AMBIGUOUS_STATE_ALIASES) return null
    val alias = FOOD_ALIASES[normalized] ?: normalized

    // O(1) exact lookup
    foodByExactName[alias]?.let { return it }
    foodByExactName[normalized]?.let { return it }
    // Fallback con coincidencia de palabras completas (evita que "pan" coincida con "empanada" o "pollo" con "repollo")
    val allFoods = ALL_FOODS
    val aliasWords = alias.split("[\\s(),/]+".toRegex()).filter { it.length > 1 }
    if (aliasWords.isNotEmpty()) {
        allFoods.find { food ->
            val foodWords = food.name.lowercase().split("[\\s(),/]+".toRegex())
            aliasWords.all { aw -> foodWords.any { fw -> fw == aw } }
        }?.let { return it }
    }

    // Segundo fallback inverso: que todas las palabras de la comida estén en el alias
    allFoods.find { food ->
        val foodNameLower = food.name.lowercase()
        if (foodNameLower.length <= 3) return@find false
        val foodWords = foodNameLower.split("[\\s(),/]+".toRegex()).filter { it.length > 2 }
        if (foodWords.isEmpty()) return@find false
        val aliasWordsAll = alias.split("[\\s(),/]+".toRegex())
        foodWords.all { fw -> aliasWordsAll.any { aw -> aw == fw || aw.startsWith(fw) } }
    }?.let { return it }

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
