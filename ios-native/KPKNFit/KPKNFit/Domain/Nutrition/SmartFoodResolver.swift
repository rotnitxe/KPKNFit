import Foundation
import os.log

enum SmartFoodResolver {

    // MARK: - Types

    struct ResolutionCandidate {
        let foodId: String
        let name: String
        let brand: String?
        let score: Double
        let confidence: Confidence
        let source: String
        let calories: Double
        let protein: Double
        let carbs: Double
        let fats: Double
        let fiber: Double
        let trace: [String]
    }

    enum Confidence { case high, medium, low, unresolved }

    struct ResolutionResult {
        let query: String
        let candidates: [ResolutionCandidate]
        let decision: Decision
        let resolvedFoodId: String?
    }

    enum Decision { case autoSelect, needsReview, unresolved }

    struct LearnedEntry {
        let foodId: String
        let portionGrams: Double?
        let cookingMethod: String?
        let count: Int
    }

    // MARK: - Dependencies (must be set before use)

    static var foodIndex: FoodIndex?
    static var learnedDao: LearnedResolutionDao?

    // MARK: - Cache

    private static var learnedCache: [String: LearnedEntry] = [:]

    // MARK: - Constants

    private static let highThreshold = 0.86
    private static let fuzzyHighThreshold = 0.90
    private static let mediumThreshold = 0.6
    private static let minThreshold = 0.18
    private static let safeGap = 0.16
    private static let learnedAutoThreshold = 0.74

    // MARK: - Public Methods

    static func preloadLearned() async {
        guard let learnedDao else { return }
        do {
            let learned = try await learnedDao.topFor(prefix: "", n: 500)
            for entity in learned {
                let key = entity.queryKey
                learnedCache[key] = LearnedEntry(
                    foodId: entity.foodId,
                    portionGrams: entity.portionGrams,
                    cookingMethod: entity.cookingMethod,
                    count: entity.count
                )
            }
        } catch {
            os_log("preloadLearned failed: %@", log: .default, type: .error, error.localizedDescription)
        }
    }

    static func resolve(query: String, brandHint: String? = nil) async -> ResolutionResult {
        guard let foodIndex else {
            return ResolutionResult(query: query, candidates: [], decision: .unresolved, resolvedFoodId: nil)
        }

        let normalizedQuery = FoodIndex.normalizeSearch(query)
        let queryTokens = FoodIndex.tokenize(normalizedQuery)
        if queryTokens.isEmpty {
            return ResolutionResult(query: query, candidates: [], decision: .unresolved, resolvedFoodId: nil)
        }

        let learnedKey = buildLearnedKey(normalizedQuery: normalizedQuery, brandHint: brandHint)
        let learned = learnedCache[learnedKey]

        let candidateIds = foodIndex.search(query: normalizedQuery)
        if candidateIds.isEmpty {
            var expandedIds = Set<String>()
            for token in queryTokens {
                expandedIds.formUnion(foodIndex.search(query: token))
            }
            if expandedIds.isEmpty {
                return ResolutionResult(query: query, candidates: [], decision: .unresolved, resolvedFoodId: nil)
            }
            return scoreAndRank(
                originalQuery: query, normalizedQuery: normalizedQuery,
                queryTokens: queryTokens, candidateIds: expandedIds,
                brandHint: brandHint, learned: learned
            )
        }

        return scoreAndRank(
            originalQuery: query, normalizedQuery: normalizedQuery,
            queryTokens: queryTokens, candidateIds: candidateIds,
            brandHint: brandHint, learned: learned
        )
    }

    static func resolveBatch(queries: [String]) async -> [ResolutionResult] {
        var results: [ResolutionResult] = []
        for q in queries {
            results.append(await resolve(query: q))
        }
        return results
    }

    static func recordLearned(
        query: String, brandHint: String?, foodId: String,
        portionGrams: Double?, cookingMethod: String?
    ) {
        let key = buildLearnedKey(
            normalizedQuery: FoodIndex.normalizeSearch(query),
            brandHint: brandHint
        )
        let existing = learnedCache[key]
        let newCount = (existing?.count ?? 0) + 1
        learnedCache[key] = LearnedEntry(
            foodId: foodId,
            portionGrams: portionGrams ?? existing?.portionGrams,
            cookingMethod: cookingMethod ?? existing?.cookingMethod,
            count: newCount
        )
        Task.detached(priority: .background) {
            guard let learnedDao else { return }
            do {
                try await learnedDao.upsert(
                    entity: LearnedResolutionEntity(
                        id: key,
                        queryKey: key,
                        foodId: foodId,
                        portionGrams: portionGrams,
                        cookingMethod: cookingMethod,
                        count: newCount,
                        lastUsedAt: Int64(Date().timeIntervalSince1970 * 1000),
                        createdAt: Int64(Date().timeIntervalSince1970 * 1000),
                        syncedAt: nil
                    )
                )
            } catch {
                os_log("recordLearned persist failed: %@", log: .default, type: .error, error.localizedDescription)
            }
        }
    }

    // MARK: - Scoring

    private static func scoreAndRank(
        originalQuery: String,
        normalizedQuery: String,
        queryTokens: [String],
        candidateIds: Set<String>,
        brandHint: String?,
        learned: LearnedEntry?
    ) -> ResolutionResult {
        guard let foodIndex else {
            return ResolutionResult(query: originalQuery, candidates: [], decision: .unresolved, resolvedFoodId: nil)
        }

        let candidates = candidateIds.compactMap { foodId -> ResolutionCandidate? in
            guard let food = foodIndex.getFood(foodId: foodId) else { return nil }
            let score = computeScore(
                food: food, normalizedQuery: normalizedQuery,
                queryTokens: queryTokens, brandHint: brandHint, learned: learned
            )
            let trace = buildTrace(
                food: food, normalizedQuery: normalizedQuery,
                queryTokens: queryTokens, brandHint: brandHint
            )
            return ResolutionCandidate(
                foodId: food.foodId, name: food.name, brand: food.brand,
                score: score,
                confidence: scoreToConfidence(score: score, queryTokens: queryTokens, food: food),
                source: food.source, calories: food.calories, protein: food.protein,
                carbs: food.carbs, fats: food.fats, fiber: food.fiber, trace: trace
            )
        }.sorted { $0.score > $1.score }

        let top = Array(candidates.prefix(4))

        let decision: Decision
        if top.isEmpty {
            decision = .unresolved
        } else if learned != nil && top[0].score >= learnedAutoThreshold {
            decision = .autoSelect
        } else if top[0].score >= highThreshold && (top.count == 1 || top[0].score - top[1].score >= safeGap) {
            decision = .autoSelect
        } else if top[0].score >= mediumThreshold {
            decision = .needsReview
        } else {
            decision = .unresolved
        }

        let resolvedId = decision == .autoSelect ? top.first?.foodId : nil

        return ResolutionResult(
            query: originalQuery, candidates: top,
            decision: decision, resolvedFoodId: resolvedId
        )
    }

    private static func computeScore(
        food: FoodIndex.IndexedFood,
        normalizedQuery: String,
        queryTokens: [String],
        brandHint: String?,
        learned: LearnedEntry?
    ) -> Double {
        var score = 0.0
        let foodTokens = food.tokens

        if food.normalizedName == normalizedQuery || food.name.lowercased() == normalizedQuery {
            score += 0.54
        }

        if food.normalizedName.contains(normalizedQuery) || normalizedQuery.contains(food.normalizedName) {
            score += 0.34
        } else {
            let substringHits = queryTokens.filter { qt in
                foodTokens.contains { ft in ft.contains(qt) || qt.contains(ft) }
            }.count
            if substringHits > 0 {
                score += 0.28 * (Double(substringHits) / Double(queryTokens.count))
            }
        }

        if !queryTokens.isEmpty && !foodTokens.isEmpty {
            let intersection = Set(queryTokens).intersection(foodTokens).count
            let union = Set(queryTokens + Array(foodTokens)).count
            let jaccard = union > 0 ? Double(intersection) / Double(union) : 0.0
            score += jaccard * 0.50
        }

        if !queryTokens.isEmpty {
            let covered = queryTokens.filter { qt in
                foodTokens.contains { ft in ft == qt || ft.contains(qt) || qt.contains(ft) }
            }.count
            score += (Double(covered) / Double(queryTokens.count)) * 0.50
        }

        let uncovered = queryTokens.filter { qt in
            !foodTokens.contains { ft in ft == qt || ft.contains(qt) || qt.contains(ft) }
        }.count
        score -= Double(uncovered) * 0.14

        let excess = foodTokens.filter { ft in
            !queryTokens.contains { qt in ft == qt || ft.contains(qt) || qt.contains(ft) }
        }.count
        score -= Double(excess) * 0.15

        if let brandHint, !brandHint.isEmpty, let brand = food.brand {
            let normalizedBrand = FoodIndex.normalizeSearch(brandHint)
            if brand.lowercased().contains(normalizedBrand) || normalizedBrand.contains(brand.lowercased()) {
                score += 0.18
            }
        }

        score += (Double(food.sourcePriority) - 50) / 100.0 * 0.12

        if let learned, learned.foodId == food.foodId {
            let queryIsCombo = normalizedQuery.contains(" con ")
                || normalizedQuery.contains(" y ")
                || normalizedQuery.contains(" e ")
            let foodIsCombo = food.normalizedName.contains(" con ")
                || food.normalizedName.contains(" y ")
                || food.normalizedName.contains(" e ")
            if queryIsCombo || !foodIsCombo {
                score += 0.32 + Double(min(learned.count, 3)) * 0.02
            }
        }

        if food.fiber > 0.0 || food.protein > 0.0 {
            score += 0.01
        }

        score += computeFuzzyBonus(food: food, queryTokens: queryTokens)

        return max(0.0, min(score, 1.0))
    }

    private static func computeFuzzyBonus(
        food: FoodIndex.IndexedFood,
        queryTokens: [String]
    ) -> Double {
        var bonus = 0.0

        for qt in queryTokens {
            if food.tokens.contains(qt) { continue }

            let qtPhonetic = PhoneticEs.encode(qt)
            let qtTrigrams = FoodIndex.generateTrigrams(qt)

            let phoneticMatch = food.phoneticTokens.values.contains(qtPhonetic) && !qtPhonetic.isEmpty

            var bestTrigramSim = 0.0
            for ft in food.tokens {
                let ftTrigrams = FoodIndex.generateTrigrams(ft)
                let intersection = qtTrigrams.intersection(ftTrigrams).count
                let union = qtTrigrams.union(ftTrigrams).count
                let sim = union > 0 ? Double(intersection) / Double(union) : 0.0
                if sim > bestTrigramSim { bestTrigramSim = sim }
            }

            var bestLevenshtein = 0.0
            if qt.count >= 5 {
                for ft in food.tokens where ft.count >= 4 {
                    let dist = levenshteinDistance(s1: qt, s2: ft, maxDistance: 3)
                    let maxLen = max(qt.count, ft.count)
                    let sim = 1.0 - Double(dist) / Double(maxLen)
                    if sim > bestLevenshtein { bestLevenshtein = sim }
                }
            }

            if phoneticMatch {
                bonus += 0.18
            } else if bestLevenshtein >= 0.8 {
                bonus += 0.22
            } else if bestTrigramSim >= 0.6 {
                bonus += 0.10 + (bestTrigramSim - 0.6) * 0.30
            }
        }

        return bonus
    }

    private static func levenshteinDistance(s1: String, s2: String, maxDistance: Int = 3) -> Int {
        let m = s1.count
        let n = s2.count
        if abs(m - n) > maxDistance { return maxDistance + 1 }

        var prev = [Int](0...n)
        var curr = [Int](repeating: 0, count: n + 1)

        for i in 1...m {
            curr[0] = i
            var minInRow = curr[0]
            let s1Char = s1[s1.index(s1.startIndex, offsetBy: i - 1)]
            for j in 1...n {
                let s2Char = s2[s2.index(s2.startIndex, offsetBy: j - 1)]
                let cost = s1Char == s2Char ? 0 : 1
                curr[j] = min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if curr[j] < minInRow { minInRow = curr[j] }
            }
            if minInRow > maxDistance { return maxDistance + 1 }
            swap(&prev, &curr)
        }

        return prev[n]
    }

    private static func scoreToConfidence(
        score: Double,
        queryTokens: [String],
        food: FoodIndex.IndexedFood
    ) -> Confidence {
        let hasFuzzyToken = queryTokens.contains { qt in
            guard !food.tokens.contains(qt) else { return false }
            let ph = PhoneticEs.encode(qt)
            if !ph.isEmpty && food.phoneticTokens.values.contains(ph) { return true }
            let qtTri = FoodIndex.generateTrigrams(qt)
            return food.tokens.contains { ft in
                let ftTri = FoodIndex.generateTrigrams(ft)
                let inter = qtTri.intersection(ftTri).count
                let uni = qtTri.union(ftTri).count
                return (uni > 0 ? Double(inter) / Double(uni) : 0.0) >= 0.6
            }
        }

        let threshold = hasFuzzyToken ? fuzzyHighThreshold : highThreshold

        if score >= threshold { return .high }
        if score >= mediumThreshold { return .medium }
        if score >= minThreshold { return .low }
        return .unresolved
    }

    private static func buildTrace(
        food: FoodIndex.IndexedFood,
        normalizedQuery: String,
        queryTokens: [String],
        brandHint: String?
    ) -> [String] {
        var trace: [String] = []

        if food.normalizedName == normalizedQuery {
            trace.append("exact-name")
        } else if food.normalizedName.contains(normalizedQuery) {
            trace.append("substring-name")
        }

        let matchedTokens = queryTokens.filter { qt in food.tokens.contains(qt) }
        if !matchedTokens.isEmpty {
            trace.append("tokens:\(matchedTokens.joined(separator: ","))")
        }

        if let brandHint, !brandHint.isEmpty, let brand = food.brand,
           brand.lowercased().contains(brandHint.lowercased()) {
            trace.append("brand-match")
        }

        let fuzzyTokens = queryTokens.filter { qt in
            guard !food.tokens.contains(qt) else { return false }
            let ph = PhoneticEs.encode(qt)
            return food.phoneticTokens.values.contains(ph)
        }
        if !fuzzyTokens.isEmpty {
            trace.append("fuzzy:\(fuzzyTokens.joined(separator: ","))")
        }

        return trace
    }

    private static func buildLearnedKey(normalizedQuery: String, brandHint: String?) -> String {
        if let brandHint, !brandHint.isEmpty {
            return "\(normalizedQuery)|\(brandHint)"
        }
        return normalizedQuery
    }
}


