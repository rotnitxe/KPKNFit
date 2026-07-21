import Foundation

struct CookingFactor {
    let kcal: Double
    let fats: Double
    let carbs: Double
    let protein: Double
    let waterChange: Double

    init(kcal: Double = 1.0, fats: Double = 1.0, carbs: Double = 1.0, protein: Double = 1.0, waterChange: Double = 0.0) {
        self.kcal = kcal
        self.fats = fats
        self.carbs = carbs
        self.protein = protein
        self.waterChange = waterChange
    }
}

let cookingFactors: [CookingMethod: CookingFactor] = [
    .CRUDO: CookingFactor(kcal: 1.00, fats: 1.00, carbs: 1.00, protein: 1.00, waterChange: 0.00),
    .COCIDO: CookingFactor(kcal: 0.90, fats: 1.00, carbs: 1.00, protein: 0.95, waterChange: 0.15),
    .VAPOR: CookingFactor(kcal: 0.95, fats: 1.00, carbs: 1.00, protein: 1.00, waterChange: 0.08),
    .OLLA: CookingFactor(kcal: 0.90, fats: 1.00, carbs: 1.00, protein: 0.95, waterChange: 0.12),
    .PLANCHA: CookingFactor(kcal: 1.00, fats: 0.95, carbs: 1.00, protein: 1.05, waterChange: -0.15),
    .ASADO_PARRILLA: CookingFactor(kcal: 1.05, fats: 0.90, carbs: 1.00, protein: 1.10, waterChange: -0.20),
    .HORNO: CookingFactor(kcal: 1.15, fats: 0.95, carbs: 1.05, protein: 1.10, waterChange: -0.25),
    .GUISADO: CookingFactor(kcal: 1.30, fats: 1.20, carbs: 1.00, protein: 1.00, waterChange: 0.05),
    .AHUMADO: CookingFactor(kcal: 1.10, fats: 0.95, carbs: 1.00, protein: 1.05, waterChange: -0.15),
    .FRITO: CookingFactor(kcal: 1.10, fats: 1.00, carbs: 1.00, protein: 1.10, waterChange: -0.20),
    .EMPANIZADO_FRITO: CookingFactor(kcal: 1.20, fats: 1.00, carbs: 1.20, protein: 1.10, waterChange: -0.15),
]

/// Apply cooking method factors to a FoodItem, returning adjusted per-100g macros.
func applyCooking(_ item: FoodItem, method: CookingMethod?) -> FoodItem {
    guard let method = method else { return item }
    guard let factor = cookingFactors[method] else { return item }

    return item.copy(
        calories: round1(item.calories * factor.kcal),
        protein: round1(item.protein * factor.protein),
        carbs: round1(item.carbs * factor.carbs),
        fats: round1(item.fats * factor.fats)
    )
}

/// Apply cooking factors to raw macro values, returning per-gram adjusted macros.
func applyCookingToMacros(
    calories: Double,
    protein: Double,
    carbs: Double,
    fats: Double,
    method: CookingMethod?
) -> Quadruple {
    guard let method = method else { return Quadruple(calories: calories, protein: protein, carbs: carbs, fats: fats) }
    guard let factor = cookingFactors[method] else { return Quadruple(calories: calories, protein: protein, carbs: carbs, fats: fats) }
    return Quadruple(
        calories: round1(calories * factor.kcal),
        protein: round1(protein * factor.protein),
        carbs: round1(carbs * factor.carbs),
        fats: round1(fats * factor.fats)
    )
}

/// Detect if a food name is a liquid (for unit display as ml instead of g).
func isLikelyLiquid(foodName: String, category: String? = nil) -> Bool {
    let lower = foodName.lowercased().trimmingCharacters(in: .whitespaces)
    let liquidKeywords = [
        "agua", "jugo", "zumo", "leche", "bebida", "refresco", "gaseosa",
        "café", "cafe", "té", "te", "cerveza", "vino", "licor", "ron", "whisky",
        "aceite", "vinagre", "salsa de soya", "salsa de soja", "caldo", "sopa",
        "batido", "smoothie", "malteada", "horchata", "ponche", "néctar", "nectar",
        "energética", "energetica", "isotónica", "isotonica", "cóctel", "coctel",
        "champán", "champagne", "sidra", "cava", "prosecco",
        "yogurt", "yogur", "kéfir", "kefir",
    ]
    if liquidKeywords.contains(where: { lower.contains($0) }) { return true }
    if let category = category {
        let liquidCategories = ["beverage", "bebida", "drink", "dairy drink", "juice"]
        if liquidCategories.contains(where: { category.lowercased().contains($0) }) { return true }
    }
    return false
}

struct Quadruple {
    let calories: Double
    let protein: Double
    let carbs: Double
    let fats: Double
}

func round1(_ v: Double) -> Double {
    (v * 10.0).rounded() / 10.0
}
