import Foundation

struct NutritionProfile {
    let calories: Double
    let protein: Double
    let carbs: Double
    let fats: Double
}

private let LEAN_PROTEIN = NutritionProfile(calories: 165, protein: 31, carbs: 0, fats: 3.6)
private let FATTY_PROTEIN = NutritionProfile(calories: 220, protein: 26, carbs: 0, fats: 12)
private let FISH_LEAN = NutritionProfile(calories: 120, protein: 22, carbs: 0, fats: 3)
private let FISH_FATTY = NutritionProfile(calories: 200, protein: 20, carbs: 0, fats: 13)
private let EGG_PROFILE = NutritionProfile(calories: 155, protein: 13, carbs: 1, fats: 11)
private let LEGUME = NutritionProfile(calories: 116, protein: 9, carbs: 20, fats: 0.4)
private let GRAIN_COOKED = NutritionProfile(calories: 130, protein: 3, carbs: 27, fats: 0.5)
private let GRAIN_DRY = NutritionProfile(calories: 360, protein: 13, carbs: 68, fats: 6)
private let PASTA_COOKED = NutritionProfile(calories: 131, protein: 5, carbs: 25, fats: 1.1)
private let BREAD = NutritionProfile(calories: 265, protein: 9, carbs: 49, fats: 3)
private let STARCHY_VEG = NutritionProfile(calories: 85, protein: 1.9, carbs: 20, fats: 0.1)
private let DAIRY_LIQUID = NutritionProfile(calories: 62, protein: 3.2, carbs: 4.8, fats: 3.4)
private let DAIRY_THICK = NutritionProfile(calories: 97, protein: 9, carbs: 3.9, fats: 5)
private let CHEESE = NutritionProfile(calories: 350, protein: 22, carbs: 2, fats: 28)
private let OIL_FAT = NutritionProfile(calories: 780, protein: 0, carbs: 0, fats: 87)
private let NUTS_SEEDS = NutritionProfile(calories: 580, protein: 20, carbs: 20, fats: 50)
private let FRUIT = NutritionProfile(calories: 60, protein: 0.8, carbs: 14, fats: 0.3)
private let VEGETABLE = NutritionProfile(calories: 28, protein: 2, carbs: 5, fats: 0.3)
private let PROTEIN_POWDER = NutritionProfile(calories: 380, protein: 80, carbs: 8, fats: 5)
private let SUGAR_SWEET = NutritionProfile(calories: 380, protein: 0.5, carbs: 90, fats: 0.2)
private let PROCESSED_MEAT = NutritionProfile(calories: 240, protein: 16, carbs: 2, fats: 19)
private let SAUCE_DRESSING = NutritionProfile(calories: 250, protein: 1.5, carbs: 15, fats: 20)
private let MIXED_DISH = NutritionProfile(calories: 160, protein: 10, carbs: 16, fats: 6)

private let KEYWORD_PROFILES: [([String], NutritionProfile)] = [
    (["whey", "proteína en polvo", "proteina en polvo", "caseína", "caseina", "suplemento proteico", "mass gainer", "gainers"], PROTEIN_POWDER),
    (["pechuga de pollo", "pechuga de pavo", "pollo a la plancha", "pechuga", "pechuga cocida", "pollo cocido", "pavo cocido", "clara de huevo", "claras de huevo", "claras", "atún", "atun", "merluza", "tilapia", "corvina", "reineta", "camarón", "camaron", "langostino", "pulpo", "calamar", "pollo", "pavo"], LEAN_PROTEIN),
    (["carne molida", "carne de vacuno", "carne de res", "carne de cerdo", "asado", "costilla", "lomo vetado", "punta de ganso", "tapapecho", "paleta", "pierna de cerdo", "chuleta", "filete", "lomo", "vacuno", "res", "cerdo", "carne"], FATTY_PROTEIN),
    (["salmón", "salmon", "sardina", "caballa", "atún en aceite", "jurel"], FISH_FATTY),
    (["pescado", "congrio", "lenguado", "bacalao"], FISH_LEAN),
    (["huevo duro", "huevo cocido", "huevo frito", "huevo revuelto", "huevos duros", "huevo", "huevos"], EGG_PROFILE),
    (["jamón", "jamon", "salchicha", "vienesa", "longaniza", "mortadela", "salame", "cecina", "tocino", "panceta"], PROCESSED_MEAT),
    (["lentejas", "lenteja", "garbanzos", "garbanzo", "porotos", "poroto", "frijoles", "frijol", "habas", "edamame", "soya"], LEGUME),
    (["pasta", "fideos", "spaghetti", "espagueti", "tallarín", "tallarines", "penne", "macarrón", "macarrones", "lasaña"], PASTA_COOKED),
    (["avena", "granola", "müesli", "muesli", "cereal"], GRAIN_DRY),
    (["arroz blanco", "arroz integral", "arroz", "quinoa", "cebada cocida", "mijo cocido", "trigo bulgur"], GRAIN_COOKED),
    (["marraqueta", "hallulla", "baguette", "pan de molde", "pan integral", "pan blanco", "pan", "tortilla", "arepa", "pita"], BREAD),
    (["papa", "papas", "batata", "camote", "yuca", "ñame", "taro"], STARCHY_VEG),
    (["queso cheddar", "queso gauda", "queso mantecoso", "queso fresco", "queso crema", "queso", "mozzarella", "parmesano"], CHEESE),
    (["yogurt griego", "yogur griego", "yogurt natural", "yogur natural", "yogurt", "yogur", "queso cottage"], DAIRY_THICK),
    (["leche entera", "leche descremada", "leche semi", "leche"], DAIRY_LIQUID),
    (["aceite de oliva", "aceite de coco", "aceite vegetal", "aceite", "mantequilla", "manteca", "ghee", "margarina"], OIL_FAT),
    (["almendra", "almendras", "nuez", "nueces", "maní", "mani", "pecan", "pistache", "semillas de chia", "semillas de linaza", "semillas de girasol", "semillas de zapallo", "semilla", "chía", "chia"], NUTS_SEEDS),
    (["manzana", "plátano", "platano", "banana", "naranja", "uva", "pera", "durazno", "mango", "papaya", "sandía", "sandia", "melón", "melon", "piña", "pina", "frutilla", "frambuesa", "arándano", "arandano", "kiwi", "ciruela", "damasco", "maracuyá", "lúcuma", "fruta"], FRUIT),
    (["lechuga", "espinaca", "brócoli", "brocoli", "zanahoria", "tomate", "cebolla", "pepino", "zapallo", "pimentón", "pimenton", "apio", "rúcula", "repollo", "coliflor", "poroto verde", "choclo", "betarraga", "champiñon", "verdura", "vegetal", "acelga", "alcachofa", "berenjena", "zucchini", "pepinillo"], VEGETABLE),
    (["azúcar", "azucar", "miel", "sirope", "jarabe", "mermelada", "dulce de leche", "manjar", "chocolate"], SUGAR_SWEET),
    (["mayonesa", "ketchup", "mostaza", "salsa de tomate", "salsa de soya", "salsa", "aderezo", "vinagreta"], SAUCE_DRESSING),
]

func estimateNutritionByKeyword(_ foodName: String) -> NutritionProfile? {
    let lower = foodName.trimmingCharacters(in: .whitespaces).lowercased()
    let cookingBoost = detectCookingBoost(lower)

    for (keywords, profile) in KEYWORD_PROFILES {
        if keywords.contains(where: { lower.contains($0) }) {
            if let boost = cookingBoost {
                return NutritionProfile(
                    calories: profile.calories * boost.kcal,
                    protein: profile.protein * boost.protein,
                    carbs: profile.carbs * boost.carbs + boost.additiveCarbs,
                    fats: profile.fats * boost.fats
                )
            }
            return profile
        }
    }

    guard lower.count >= 3 else { return nil }
    if let boost = cookingBoost {
        return NutritionProfile(
            calories: MIXED_DISH.calories * boost.kcal,
            protein: MIXED_DISH.protein * boost.protein,
            carbs: MIXED_DISH.carbs * boost.carbs + boost.additiveCarbs,
            fats: MIXED_DISH.fats * boost.fats
        )
    }
    return MIXED_DISH
}

private struct CookingEstimateBoost {
    let kcal: Double
    let protein: Double
    let carbs: Double
    let fats: Double
    let additiveCarbs: Double
    init(kcal: Double = 1.0, protein: Double = 1.0, carbs: Double = 1.0, fats: Double = 1.0, additiveCarbs: Double = 0.0) {
        self.kcal = kcal; self.protein = protein; self.carbs = carbs; self.fats = fats; self.additiveCarbs = additiveCarbs
    }
}

private let REGEX_FRITO = try! NSRegularExpression(pattern: "\\bfrit[oa]s?\\b|\\bfritura\\b|\\bfre[ií]do\\b")
private let REGEX_EMPANIZADO = try! NSRegularExpression(pattern: "\\bempanizad[oa]s?\\b|\\bapanad[oa]s?\\b|\\brebozad[oa]s?\\b|\\btempura\\b|\\bcapead[oa]s?\\b")
private let REGEX_SALTEADO = try! NSRegularExpression(pattern: "\\bsaltead[oa]s?\\b|\\bsofrit[oa]s?\\b|\\bwokead[oa]s?\\b")
private let REGEX_CONFITADO = try! NSRegularExpression(pattern: "\\bconfitad[oa]s?\\b")
private let REGEX_GRATINADO = try! NSRegularExpression(pattern: "\\bgratinad[oa]s?\\b|\\bcon\\s+queso\\s+gratinado\\b")
private let REGEX_PLANCHA = try! NSRegularExpression(pattern: "\\bplancha\\b|\\ba\\s+la\\s+plancha\\b")
private let REGEX_PARRILLA = try! NSRegularExpression(pattern: "\\bparrilla\\b|\\basad[oa]s?\\s+a\\s+la\\s+parrilla\\b")

private func detectCookingBoost(_ foodName: String) -> CookingEstimateBoost? {
    let range = NSRange(foodName.startIndex..., in: foodName)
    var boost: CookingEstimateBoost? = nil

    if REGEX_FRITO.firstMatch(in: foodName, range: range) != nil {
        boost = CookingEstimateBoost(kcal: 2.0, fats: 3.0)
    }
    if REGEX_EMPANIZADO.firstMatch(in: foodName, range: range) != nil {
        boost = CookingEstimateBoost(kcal: 3.0, fats: 3.0, additiveCarbs: 15.0)
    }
    if REGEX_SALTEADO.firstMatch(in: foodName, range: range) != nil {
        boost = CookingEstimateBoost(kcal: 1.5, fats: 1.8)
    }
    if REGEX_CONFITADO.firstMatch(in: foodName, range: range) != nil {
        boost = CookingEstimateBoost(kcal: 2.5, fats: 3.5)
    }
    if REGEX_GRATINADO.firstMatch(in: foodName, range: range) != nil {
        boost = CookingEstimateBoost(kcal: 1.8, protein: 1.15, fats: 2.0)
    }
    if REGEX_PLANCHA.firstMatch(in: foodName, range: range) != nil {
        boost = CookingEstimateBoost(kcal: 1.0, protein: 1.05, fats: 0.9)
    }
    if REGEX_PARRILLA.firstMatch(in: foodName, range: range) != nil {
        boost = CookingEstimateBoost(kcal: 1.05, protein: 1.10, fats: 0.9)
    }

    return boost
}
