import Foundation

/// FoodIndex — In-memory inverted index over all food sources.
/// Built once on first access, cached. Supports token, trigram, and phonetic lookups.
class FoodIndex {

    struct IndexedFood {
        let foodId: String
        let name: String
        let brand: String?
        let normalizedName: String
        let tokens: Set<String>
        let trigrams: Set<String>
        let phoneticTokens: [String: String]
        let calories: Double
        let protein: Double
        let carbs: Double
        let fats: Double
        let fiber: Double
        let sourcePriority: Int
        let source: String
    }

    private var foods: [String: IndexedFood] = [:]
    private var tokenIndex: [String: Set<String>] = [:]
    private var trigramIndex: [String: Set<String>] = [:]
    private var phoneticIndex: [String: Set<String>] = [:]
    private var built = false
    private let lock = NSLock()

    func isBuilt() -> Bool { lock.lock(); defer { lock.unlock() }; return built }
    func size() -> Int { lock.lock(); defer { lock.unlock() }; return foods.count }

    func build(globalFoods: [GlobalFoodEntity], staticFoods: [FoodItem]) {
        lock.lock(); defer { lock.unlock() }
        if built { return }
        for food in staticFoods { addFood(indexStaticFood(food)) }
        for food in globalFoods { addFood(indexGlobalFood(food)) }
        built = true
    }

    func search(query: String) -> Set<String> {
        lock.lock(); defer { lock.unlock() }
        let normalizedQuery = FoodIndex.normalizeSearch(query)
        let queryTokens = FoodIndex.tokenize(normalizedQuery)
        if queryTokens.isEmpty { return [] }

        var candidates = Set<String>()
        for token in queryTokens {
            if let ids = tokenIndex[token] { candidates.formUnion(ids) }
            for trigram in FoodIndex.generateTrigrams(token) {
                if let ids = trigramIndex[trigram] { candidates.formUnion(ids) }
            }
            let phonetic = PhoneticEs.encode(token)
            if !phonetic.isEmpty, let ids = phoneticIndex[phonetic] { candidates.formUnion(ids) }
        }
        return candidates
    }

    func getFood(foodId: String) -> IndexedFood? { lock.lock(); defer { lock.unlock() }; return foods[foodId] }
    func getAllFoods() -> [IndexedFood] { lock.lock(); defer { lock.unlock() }; return Array(foods.values) }

    private func addFood(_ food: IndexedFood) {
        foods[food.foodId] = food
        for token in food.tokens { tokenIndex[token, default: []].insert(food.foodId) }
        for trigram in food.trigrams { trigramIndex[trigram, default: []].insert(food.foodId) }
        for (_, code) in food.phoneticTokens where !code.isEmpty { phoneticIndex[code, default: []].insert(food.foodId) }
    }

    private func indexStaticFood(_ food: FoodItem) -> IndexedFood {
        let normalizedName = FoodIndex.normalizeSearch(food.name)
        let tokens = Set(FoodIndex.tokenize(normalizedName))
        let trigrams = Set(tokens.flatMap { FoodIndex.generateTrigrams($0) })
        let phoneticTokens = Dictionary(uniqueKeysWithValues: tokens.map { ($0, PhoneticEs.encode($0)) })
        return IndexedFood(
            foodId: food.id, name: food.name, brand: food.brand,
            normalizedName: normalizedName, tokens: tokens, trigrams: trigrams,
            phoneticTokens: phoneticTokens, calories: food.calories,
            protein: food.protein, carbs: food.carbs, fats: food.fats,
            fiber: food.carbBreakdown?.fiber ?? 0.0,
            sourcePriority: food.sourcePriority, source: "LOCAL"
        )
    }

    private func indexGlobalFood(_ food: GlobalFoodEntity) -> IndexedFood {
        let normalizedName = food.normalizedName.isEmpty ? FoodIndex.normalizeSearch(food.name) : food.normalizedName
        var allNames = [normalizedName]
        if let aliases = try? JSONSerialization.jsonObject(with: Data(food.aliasesJson.utf8)) as? [String] {
            allNames.append(contentsOf: aliases)
        }
        if let brand = food.normalizedBrand { allNames.append(brand) }

        let tokens = Set(allNames.flatMap { FoodIndex.tokenize($0) })
        let trigrams = Set(tokens.flatMap { FoodIndex.generateTrigrams($0) })
        let phoneticTokens = Dictionary(uniqueKeysWithValues: tokens.map { ($0, PhoneticEs.encode($0)) })
        return IndexedFood(
            foodId: food.foodId, name: food.name, brand: food.brand,
            normalizedName: normalizedName, tokens: tokens, trigrams: trigrams,
            phoneticTokens: phoneticTokens, calories: food.calories,
            protein: food.protein, carbs: food.carbs, fats: food.fats,
            fiber: food.fiber, sourcePriority: food.sourcePriority, source: food.source
        )
    }

    private static let SPANISH_STOPWORDS: Set<String> = [
        "de", "la", "el", "con", "sin", "a", "al", "en", "por", "y", "o",
        "un", "una", "unos", "unas", "del", "las", "los", "lo",
        "para", "que", "es", "su", "se", "no", "más", "como",
    ]

    static func normalizeSearch(_ value: String) -> String {
        let stripped = value.folding(options: .diacriticInsensitive, locale: .current)
        return stripped
            .lowercased()
            .replacingOccurrences(of: "[^\\p{L}\\p{Nd}]+", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
    }

    static func tokenize(_ normalized: String) -> [String] {
        normalized.components(separatedBy: .whitespaces)
            .filter { $0.count >= 2 && !SPANISH_STOPWORDS.contains($0) }
    }

    static func generateTrigrams(_ token: String) -> Set<String> {
        if token.count < 3 { return Set([token]) }
        let padded = "$\(token)$"
        var trigrams = Set<String>()
        let chars = Array(padded)
        for i in 0..<(chars.count - 2) {
            trigrams.insert(String(chars[i..<i+3]))
        }
        return trigrams
    }
}
