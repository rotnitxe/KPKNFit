import Foundation
import os.log

// MARK: - JSON Helpers

private let dbJson: JSONEncoder = {
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.sortedKeys]
    encoder.dateEncodingStrategy = .iso8601
    return encoder
}()

private let dbJsonDecoder: JSONDecoder = {
    let decoder = JSONDecoder()
    decoder.dateDecodingStrategy = .iso8601
    decoder.keyDecodingStrategy = .useDefaultKeys
    return decoder
}()

private func encodeModel<T: Encodable>(_ value: T) -> String {
    guard let data = try? dbJson.encode(value) else { return "{}" }
    return String(decoding: data, as: UTF8.self)
}

private func decodeModel<T: Decodable>(_ type: T.Type, from data: String) -> T? {
    try? dbJsonDecoder.decode(type, from: Data(data.utf8))
}

private func isoTimestamp() -> String {
    let formatter = ISO8601DateFormatter()
    return formatter.string(from: Date())
}

// MARK: - Internal Types

internal struct FoodQueryLearningEntry: Codable {
    let query: String
    let foodId: String
    let score: Double
    let updatedAt: String
}

internal struct FoodCatalogMeta: Codable {
    let version: Int
    let checksum: String
    let importedAt: String
}

// MARK: - Stubs (to be ported from Android)

private func buildFoodDatabase() -> [FoodItem] {
    return []
}



internal struct FoodImporter {
    struct ImportMetadata {
        let version: Int
        let checksum: String
        let importedAt: String
    }

    static func importIfNeeded(
        db: KpknDatabase,
        alreadyImported: Bool,
        existingMeta: ImportMetadata?,
        onMetaUpdated: (ImportMetadata) -> Void
    ) async -> Bool {
        return false
    }
}

// MARK: - NutritionRepository

internal final class NutritionRepository {

    static let shared = NutritionRepository()

    // MARK: - State

    private(set) var nutritionLogs: [NutritionLog] = []
    private(set) var nutritionPlans: [NutritionPlan] = []
    private(set) var activeNutritionPlanId: String? = nil
    private(set) var foodDatabase: [FoodItem] = []
    private(set) var mealTemplates: [MealTemplate] = []
    private(set) var bodyMeasurements: [BodyMeasurementEntry] = []
    private(set) var measurementSchedule: MeasurementSchedule = MeasurementSchedule()

    var activeNutritionPlan: NutritionPlan? {
        nutritionPlans.first { $0.id == activeNutritionPlanId }
    }

    // MARK: - Food Index

    private var _foodIndex: FoodIndex? = nil
    private var foodIndex: FoodIndex {
        if let idx = _foodIndex { return idx }
        let newIdx = FoodIndex()
        _foodIndex = newIdx
        return newIdx
    }

    private let foodIndexLock = NSLock()

    // MARK: - Smart Resolver

    private var smartResolverReady = false

    private func ensureSmartResolver() {
        guard !smartResolverReady else { return }
        SmartFoodResolver.foodIndex = foodIndex
        SmartFoodResolver.learnedDao = db.learnedResolutionDao
        smartResolverReady = true
        Task {
            await SmartFoodResolver.preloadLearned()
        }
    }

    // MARK: - Query Learning

    private var foodQueryLearning: [String: FoodQueryLearningEntry] = [:]
    private let foodPrefs = UserDefaults.standard
    private let measurePrefs = UserDefaults.standard

    // MARK: - Database

    private let db: KpknDatabase

    private init() {
        db = KpknDatabase.instance()
    }

    // MARK: - Nutrition Logs

    func addNutritionLog(_ log: NutritionLog) {
        nutritionLogs.append(log)
        Task {
            try? await db.nutritionDao.upsertLog(entity: log.toEntity())
        }
        if !log.foods.isEmpty {
            _ = rememberMealTemplateFromLog(log)
        }
    }

    func updateNutritionLog(_ log: NutritionLog) {
        nutritionLogs = nutritionLogs.map { $0.id == log.id ? log : $0 }
        Task {
            try? await db.nutritionDao.upsertLog(entity: log.toEntity())
        }
    }

    func deleteNutritionLog(_ logId: String) {
        nutritionLogs = nutritionLogs.filter { $0.id != logId }
        Task {
            try? await db.nutritionDao.deleteLog(id: logId)
        }
    }

    func clearNutritionLogs() {
        nutritionLogs = []
        Task {
            try? await db.nutritionDao.clearAllLogs()
        }
    }

    // MARK: - Food Database

    func addCustomFood(_ food: FoodItem) {
        let normalized = normalizeFoodItem(food)
        foodDatabase = foodDatabase.filter { $0.id != normalized.id } + [normalized]
        Task {
            try? await db.nutritionDao.upsertCustomFood(entity: normalized.toEntity())
        }
    }

    func saveAiInferredFood(_ food: FoodItem) {
        let normalizedFood = normalizeFoodItem(food)
        if findFoodByNormalized(normalizedFood.name) != nil { return }
        let alreadyKnown = foodDatabase.contains { existing in
            let knownName = existing.normalizedName ?? FoodIndex.normalizeSearch(existing.name)
            return knownName == (normalizedFood.normalizedName ?? "") || existing.id == normalizedFood.id
        }
        if alreadyKnown { return }
        foodDatabase.append(normalizedFood)
        Task {
            try? await db.nutritionDao.upsertCustomFood(entity: normalizedFood.toEntity())
        }
    }

    func saveAiInferredFoods(_ foods: [FoodItem]) {
        for food in foods {
            saveAiInferredFood(food)
        }
    }

    func searchFood(_ query: String) async -> [FoodItem] {
        let normalizedQuery = FoodIndex.normalizeSearch(query)
        guard !normalizedQuery.isEmpty else { return [] }

        let queryTokens = FoodIndex.tokenize(normalizedQuery)
        guard !queryTokens.isEmpty else { return [] }

        let localFoods = foodDatabase.map { normalizeFoodItem($0) }

        let customMatches = await db.nutritionDao.searchCustomFoods(normalizedQuery: normalizedQuery, limit: 120)
            .map { normalizeFoodItem($0.toFoodItem()) }

        let normalizedGlobal = await db.nutritionDao.searchGlobalFoodsNormalized(normalizedQuery: normalizedQuery, limit: 150)
            .map { normalizeFoodItem($0.toFoodItem()) }

        let ftsQuery = buildFtsQuery(queryTokens)
        let ftsGlobal: [FoodItem]
        if !ftsQuery.isEmpty {
            do {
                ftsGlobal = try await db.nutritionDao.searchGlobalFoodsWithFts(query: ftsQuery)
                    .map { normalizeFoodItem($0.toFoodItem()) }
            } catch {
                ftsGlobal = []
            }
        } else {
            ftsGlobal = []
        }

        let fallbackGlobal: [FoodItem]
        do {
            fallbackGlobal = try await db.nutritionDao.searchGlobalFoods(query: query)
                .map { normalizeFoodItem($0.toFoodItem()) }
        } catch {
            fallbackGlobal = []
        }

        let merged = (localFoods + customMatches + normalizedGlobal + ftsGlobal + fallbackGlobal)
            .reduce(into: [String: FoodItem]()) { result, food in
                let key = food.id.isEmpty
                    ? "\(food.normalizedName ?? "")|\(food.normalizedBrand ?? "")|\(food.sourcePriority)"
                    : food.id
                if result[key] == nil {
                    result[key] = food
                }
            }
            .values
            .map { $0 }

        let learned = foodQueryLearning

        return merged
            .compactMap { food in
                buildFoodCandidate(
                    food: food,
                    normalizedQuery: normalizedQuery,
                    queryTokens: queryTokens,
                    learnedEntry: learned[normalizedQuery]
                )
            }
            .sorted { $0.score > $1.score }
            .prefix(50)
            .map { $0.food }
    }

    func searchFoodCandidates(_ query: String, limit: Int = 50) async -> [FoodCandidate] {
        let normalizedQuery = FoodIndex.normalizeSearch(query)
        guard !normalizedQuery.isEmpty else { return [] }
        let queryTokens = FoodIndex.tokenize(normalizedQuery)
        guard !queryTokens.isEmpty else { return [] }

        let foods = await searchFood(query)
        let learned = foodQueryLearning[normalizedQuery]

        return foods
            .compactMap { food in
                buildFoodCandidate(
                    food: normalizeFoodItem(food),
                    normalizedQuery: normalizedQuery,
                    queryTokens: queryTokens,
                    learnedEntry: learned
                )
            }
            .sorted { $0.score > $1.score }
            .prefix(limit)
            .map { $0 }
    }

    func recordFoodSelection(_ query: String, _ food: FoodItem) {
        let normalizedQuery = FoodIndex.normalizeSearch(query)
        guard !normalizedQuery.isEmpty else { return }

        let normalizedFood = normalizeFoodItem(food)
        let now = isoTimestamp()

        let prev = foodQueryLearning[normalizedQuery]
        let updated = FoodQueryLearningEntry(
            query: normalizedQuery,
            foodId: normalizedFood.id,
            score: min((prev?.score ?? 0.0) + 1.0, 8.0),
            updatedAt: now
        )
        foodQueryLearning[normalizedQuery] = updated
        persistFoodLearning()

        Task {
            do {
                try await db.nutritionDao.incrementCustomFoodUsage(foodId: normalizedFood.id, lastUsedAt: now)
            } catch {
                // ignore
            }
            do {
                try await db.nutritionDao.incrementGlobalFoodUsage(foodId: normalizedFood.id, lastUsedAt: now)
            } catch {
                // ignore
            }
        }
    }

    func setActiveNutritionPlanId(_ planId: String?) {
        activeNutritionPlanId = planId
        Task {
            if planId == nil {
                try? await db.nutritionDao.clearActiveState()
            } else {
                try? await db.nutritionDao.upsertActiveState(entity: NutritionActiveStateEntity(rowId: 1, activePlanId: planId))
            }
        }
    }

    // MARK: - Nutrition Plans

    func addNutritionPlan(_ plan: NutritionPlan) {
        if let existingIndex = nutritionPlans.firstIndex(where: { $0.id == plan.id }) {
            nutritionPlans[existingIndex] = plan
        } else {
            nutritionPlans.append(plan)
        }
        Task {
            try? await db.nutritionDao.upsertPlan(entity: plan.toEntity())
        }
    }

    func deleteNutritionPlan(_ planId: String) {
        nutritionPlans = nutritionPlans.filter { $0.id != planId }
        if activeNutritionPlanId == planId {
            activeNutritionPlanId = nil
            Task {
                try? await db.nutritionDao.clearActiveState()
            }
        }
        Task {
            try? await db.nutritionDao.deletePlan(id: planId)
        }
    }

    func activatePlan(_ planId: String) {
        nutritionPlans = nutritionPlans.map { $0.id == planId ? NutritionPlan(
            id: $0.id,
            name: $0.name,
            goalType: $0.goalType,
            goalValue: $0.goalValue,
            calorieTarget: $0.calorieTarget,
            proteinGoal: $0.proteinGoal,
            carbGoal: $0.carbGoal,
            fatGoal: $0.fatGoal,
            isActive: true,
            createdAt: $0.createdAt,
            primaryGoal: $0.primaryGoal,
            estimatedEndDate: $0.estimatedEndDate,
            weeklyChangeKg: $0.weeklyChangeKg,
            startValue: $0.startValue,
            targetBodyFat: $0.targetBodyFat,
            targetMuscle: $0.targetMuscle
        ) : $0 }
        activeNutritionPlanId = planId
        Task {
            try? await db.nutritionDao.activatePlanAtomic(
                planId: planId,
                plans: nutritionPlans.map { $0.toEntity() }
            )
        }
    }

    // MARK: - Meal Templates

    func upsertMealTemplate(_ template: MealTemplate) {
        mealTemplates = mealTemplates.filter { $0.id != template.id } + [template]
        Task {
            try? await db.nutritionDao.upsertTemplate(entity: template.toEntity())
        }
    }

    func rememberMealTemplateFromLog(_ log: NutritionLog) -> MealTemplate {
        if log.foods.isEmpty {
            return MealTemplate(
                id: UUID().uuidString,
                name: "Comida guardada",
                description: "Registro vacío",
                createdAt: isoTimestamp()
            )
        }
        let template = buildMealTemplateFromLog(log)
        upsertMealTemplate(template)
        return template
    }

    func findMealTemplateMatch(_ query: String) -> MealTemplate? {
        let normalizedQuery = FoodIndex.normalizeSearch(query)
        guard !normalizedQuery.isEmpty else { return nil }

        return mealTemplates
            .compactMap { template -> (MealTemplate, Double)? in
                let score = scoreMealTemplate(template, normalizedQuery)
                return score >= 0.68 ? (template, score) : nil
            }
            .max { $0.1 < $1.1 }?.0
    }

    // MARK: - Body Measurements

    func addBodyMeasurement(_ entry: BodyMeasurementEntry) {
        bodyMeasurements.append(entry)
        let encoded = encodeModel(bodyMeasurements)
        measurePrefs.set(encoded, forKey: "measurements")
    }

    func updateMeasurementSchedule(_ schedule: MeasurementSchedule) {
        let normalized = normalizeMeasurementSchedule(schedule)
        measurementSchedule = normalized
        let encoded = encodeModel(normalized)
        measurePrefs.set(encoded, forKey: "schedule")

        let notifier = NutritionNotificationManager()
        if normalized.enabled, let nextDate = normalized.nextDate {
            notifier.scheduleMeasurementReminder(nextDate, hour: normalized.reminderHour, minute: normalized.reminderMinute)
        } else {
            notifier.cancelMeasurementReminder()
        }
    }

    func deleteBodyMeasurement(_ id: String) {
        bodyMeasurements = bodyMeasurements.filter { $0.id != id }
        let encoded = encodeModel(bodyMeasurements)
        measurePrefs.set(encoded, forKey: "measurements")
    }

    // MARK: - SmartFoodResolver Integration

    func initFoodIndex() async {
        foodIndexLock.lock()
        if foodIndex.isBuilt() {
            foodIndexLock.unlock()
            return
        }
        foodIndexLock.unlock()

        do {
            let globalFoods = await db.nutritionDao.getAllGlobalFoods()
            os_log("Building FoodIndex with %d global + %d static foods", globalFoods.count, foodDatabase.count)
            foodIndexLock.lock()
            foodIndex.build(globalFoods: globalFoods, staticFoods: foodDatabase)
            foodIndexLock.unlock()
            os_log("FoodIndex built: %d foods indexed", foodIndex.size())
        } catch {
            os_log("initFoodIndex failed: %@", error.localizedDescription)
        }
    }

    func resolveFoodWithSmartResolver(_ query: String, brandHint: String? = nil) async -> SmartFoodResolver.ResolutionResult {
        await initFoodIndex()
        ensureSmartResolver()
        return await SmartFoodResolver.resolve(query: query, brandHint: brandHint)
    }

    func recordLearnedResolution(_ query: String, brandHint: String?, _ foodId: String, portionGrams: Double?, cookingMethod: String?) {
        SmartFoodResolver.recordLearned(
            query: query,
            brandHint: brandHint,
            foodId: foodId,
            portionGrams: portionGrams,
            cookingMethod: cookingMethod
        )
    }

    func getFoodById(_ foodId: String) async -> FoodItem? {
        if let cached = foodDatabase.first(where: { $0.id == foodId }) {
            return cached
        }
        do {
            return try await db.nutritionDao.searchGlobalFoods(query: foodId).first?.toFoodItem()
        } catch {
            return nil
        }
    }

    // MARK: - Bootstrap

    func loadFromDb() {
        Task { @MainActor in
            do {
                let globalCount = await db.nutritionDao.getGlobalFoodCount()
                let imported = await FoodImporter.importIfNeeded(
                    db: db,
                    alreadyImported: globalCount > 0,
                    existingMeta: loadFoodCatalogMeta().flatMap { meta in
                        FoodImporter.ImportMetadata(
                            version: meta.version,
                            checksum: meta.checksum,
                            importedAt: meta.importedAt
                        )
                    },
                    onMetaUpdated: { meta in
                        saveFoodCatalogMeta(FoodCatalogMeta(
                            version: meta.version,
                            checksum: meta.checksum,
                            importedAt: meta.importedAt
                        ))
                    }
                )
                if imported {
                    os_log("Food catalog importado/actualizado", log: .default, type: .info)
                }

                let logs = await db.nutritionDao.getAllLogs().map { $0.toNutritionLog() }
                let plans = await db.nutritionDao.getAllPlans().map { $0.toNutritionPlan() }
                let activeId = await db.nutritionDao.getActiveState()?.activePlanId
                let customFoods = await db.nutritionDao.getAllCustomFoods()
                    .map { $0.toFoodItem() }
                    .filter { custom in
                        !custom.isAiInferred || findFoodByNormalized(custom.name) == nil
                    }
                    .map { normalizeFoodItem($0) }
                let templates = await db.nutritionDao.getAllTemplates().map { $0.toMealTemplate() }
                let learning = loadFoodLearning()

                let measurementsJson = measurePrefs.string(forKey: "measurements") ?? "[]"
                let scheduleJson = measurePrefs.string(forKey: "schedule")
                let measurements = (try? dbJsonDecoder.decode([BodyMeasurementEntry].self, from: Data(measurementsJson.utf8))) ?? []
                let schedule: MeasurementSchedule
                if let scheduleJson = scheduleJson,
                   let decoded = try? dbJsonDecoder.decode(MeasurementSchedule.self, from: Data(scheduleJson.utf8)) {
                    schedule = decoded
                } else {
                    schedule = MeasurementSchedule()
                }

                nutritionLogs = logs
                nutritionPlans = plans
                activeNutritionPlanId = activeId
                foodDatabase = (buildFoodDatabase() + customFoods)
                    .map { normalizeFoodItem($0) }
                    .reduce(into: [String: FoodItem]()) { result, food in
                        let key = food.id.isEmpty
                            ? "\(food.normalizedName ?? "")|\(food.normalizedBrand ?? "")|\(food.sourcePriority)"
                            : food.id
                        if result[key] == nil {
                            result[key] = food
                        }
                    }
                    .values
                    .map { $0 }
                mealTemplates = templates
                foodQueryLearning = learning
                bodyMeasurements = measurements
                measurementSchedule = normalizeMeasurementSchedule(schedule)

                Task(priority: .utility) {
                    await self.initFoodIndex()
                }

                let notifier = NutritionNotificationManager()
                let currentSchedule = measurementSchedule
                if currentSchedule.enabled, let nextDate = currentSchedule.nextDate {
                    notifier.scheduleMeasurementReminder(nextDate, hour: currentSchedule.reminderHour, minute: currentSchedule.reminderMinute)
                } else {
                    notifier.cancelMeasurementReminder()
                }
            } catch {
                os_log("loadFromDb failed: %@", error.localizedDescription)
                foodDatabase = buildFoodDatabase()
                mealTemplates = []
                foodQueryLearning = [:]
                bodyMeasurements = []
                measurementSchedule = MeasurementSchedule()
                NutritionNotificationManager().cancelMeasurementReminder()
            }
        }
    }

    // MARK: - Bootstrap Helpers

    private func buildMealTemplateFromLog(_ log: NutritionLog) -> MealTemplate {
        let foods = log.foods
        let totals = foods.reduce(DailyMacroTotals()) { acc, food in
            DailyMacroTotals(
                calories: acc.calories + food.calories,
                protein: acc.protein + food.protein,
                carbs: acc.carbs + food.carbs,
                fats: acc.fats + food.fats,
                fiber: acc.fiber,
                sugar: acc.sugar,
                sodiumMg: acc.sodiumMg,
                potassiumMg: acc.potassiumMg,
                waterMl: acc.waterMl
            )
        }
        let signature = buildMealTemplateSignature(log)
        let id = deterministicUUID(from: signature)
        let now = isoTimestamp()
        let foodNames = foods.map { $0.foodName }.joined(separator: " + ")
        let mealLabel = log.mealType.rawValue.lowercased().capitalized

        return MealTemplate(
            id: id,
            name: "\(mealLabel) · \(foodNames)",
            description: buildMealTemplateDescription(log),
            foods: foods,
            totalCalories: totals.calories,
            totalProtein: totals.protein,
            totalCarbs: totals.carbs,
            totalFats: totals.fats,
            createdAt: now
        )
    }

    private func buildMealTemplateDescription(_ log: NutritionLog) -> String {
        let foods = log.foods.map { food -> String in
            let grams: String?
            if food.amount > 0 {
                grams = "\(formatNumber(food.amount))\(food.unit)"
            } else {
                grams = nil
            }
            if let grams = grams, !grams.isEmpty {
                return "\(food.foodName) \(grams)"
            } else {
                return food.foodName
            }
        }.joined(separator: ", ")

        let calories = log.foods.reduce(0.0) { $0 + $1.calories }
        let protein = log.foods.reduce(0.0) { $0 + $1.protein }
        let carbs = log.foods.reduce(0.0) { $0 + $1.carbs }
        let fats = log.foods.reduce(0.0) { $0 + $1.fats }

        let foodsStr = foods.isEmpty ? "Registro de comida" : foods
        return "\(foodsStr) · \(formatNumber(calories)) kcal · P\(formatNumber(protein)) C\(formatNumber(carbs)) G\(formatNumber(fats))"
    }

    private func buildMealTemplateSignature(_ log: NutritionLog) -> String {
        let sortedFoods = log.foods.sorted {
            FoodIndex.normalizeSearch($0.foodName) < FoodIndex.normalizeSearch($1.foodName)
        }
        var signature = log.mealType.rawValue + "|"
        for food in sortedFoods {
            signature += "\(FoodIndex.normalizeSearch(food.foodName)):\(formatNumber(food.amount)):\(food.unit.lowercased());"
        }
        return signature
    }

    private func scoreMealTemplate(_ template: MealTemplate, _ normalizedQuery: String) -> Double {
        var text = template.name
        if !template.description.isEmpty {
            text += " " + template.description
        }
        if !template.foods.isEmpty {
            text += " " + template.foods.map { $0.foodName }.joined(separator: " ")
        }
        let templateText = FoodIndex.normalizeSearch(text)

        if templateText.isEmpty { return 0.0 }
        if normalizedQuery == templateText { return 1.0 }
        if templateText.contains(normalizedQuery) || normalizedQuery.contains(templateText) { return 0.92 }

        let queryTokens = normalizedQuery.split(separator: " ").filter { $0.count >= 3 }.map(String.init)
        if queryTokens.isEmpty { return 0.0 }

        let templateTokens = Set(templateText.split(separator: " ").map(String.init))
        let tokenOverlap = Double(queryTokens.filter { templateTokens.contains($0) }.count) / Double(queryTokens.count)

        let foodOverlap: Double
        if !template.foods.isEmpty {
            let matchedFoods = template.foods.filter { food in
                let foodName = FoodIndex.normalizeSearch(food.foodName)
                return queryTokens.contains { token in
                    foodName.contains(token) || token.contains(foodName.prefix(6))
                }
            }.count
            foodOverlap = Double(matchedFoods) / Double(template.foods.count)
        } else {
            foodOverlap = 0.0
        }

        return (tokenOverlap * 0.65) + (foodOverlap * 0.35)
    }

    private func normalizeMeasurementSchedule(_ schedule: MeasurementSchedule) -> MeasurementSchedule {
        let today = Calendar.current.startOfDay(for: Date())
        let dateFormatter = ISO8601DateFormatter()

        let nextDate: String?
        if !schedule.enabled {
            nextDate = nil
        } else if schedule.nextDate == nil || schedule.nextDate!.isEmpty {
            let newDate = Calendar.current.date(byAdding: .day, value: schedule.intervalDays, to: today)!
            nextDate = dateFormatter.string(from: newDate)
        } else {
            if let parsed = dateFormatter.date(from: schedule.nextDate!),
               parsed > today {
                nextDate = schedule.nextDate
            } else {
                let newDate = Calendar.current.date(byAdding: .day, value: schedule.intervalDays, to: today)!
                nextDate = dateFormatter.string(from: newDate)
            }
        }

        return MeasurementSchedule(
            enabled: schedule.enabled,
            intervalDays: schedule.intervalDays,
            nextDate: nextDate,
            reminderHour: schedule.reminderHour,
            reminderMinute: schedule.reminderMinute
        )
    }

    // MARK: - Food Search Helpers

    private func buildFtsQuery(_ tokens: [String]) -> String {
        guard !tokens.isEmpty else { return "" }
        return tokens.map { "\($0)*" }.joined(separator: " ").trimmingCharacters(in: .whitespaces)
    }

    private func buildFoodCandidate(
        food: FoodItem,
        normalizedQuery: String,
        queryTokens: [String],
        learnedEntry: FoodQueryLearningEntry?
    ) -> FoodCandidate? {
        let normalizedName = food.normalizedName ?? FoodIndex.normalizeSearch(food.name)
        let normalizedBrand = food.normalizedBrand ?? food.brand.flatMap { FoodIndex.normalizeSearch($0) }
        let aliases = food.searchAliases.map { FoodIndex.normalizeSearch($0) }.filter { !$0.isEmpty }

        var fields: [String] = [normalizedName]
        if let normalizedBrand = normalizedBrand, !normalizedBrand.isEmpty {
            fields.append(normalizedBrand)
        }
        fields.append(contentsOf: aliases)

        let nameExact = normalizedName == normalizedQuery
        let aliasExact = aliases.contains(normalizedQuery)
        let nameContains = normalizedName.contains(normalizedQuery)
        let aliasContains = aliases.contains { $0.contains(normalizedQuery) }
        let brandContains = !(normalizedBrand ?? "").isEmpty && normalizedBrand!.contains(normalizedQuery)

        let tokenHits = queryTokens.filter { token in
            fields.contains { field in
                let fieldTokens = field.split(separator: " ").map(String.init)
                return fieldTokens.contains(token) || field.contains(token)
            }
        }.count

        if !(nameExact || aliasExact || nameContains || aliasContains || tokenHits > 0 || brandContains) {
            return nil
        }

        let coverage = queryTokens.isEmpty ? 0.0 : Double(tokenHits) / Double(queryTokens.count)
        let precisionDenom = max(normalizedName.split(separator: " ").filter { !$0.isEmpty }.count, 1)
        let precision = (Double(tokenHits) / Double(precisionDenom)).clamped(to: 0.0...1.0)

        let exactBoost: Double
        switch true {
        case nameExact || aliasExact: exactBoost = 0.45
        case nameContains || aliasContains: exactBoost = 0.25
        default: exactBoost = 0.0
        }

        let sourceScore = (Double(min(max(food.sourcePriority, 0), 100)) / 100.0) * 0.2
        let verifiedScore = min(max(food.verifiedScore, 0.0), 1.0) * 0.2
        let usageScore = min(max(log(Double(food.usageCount + 1)) / log(10.0), 0.0), 1.0) * 0.08
        let learnedScore: Double
        if let learnedEntry = learnedEntry, learnedEntry.foodId == food.id {
            learnedScore = 0.22
        } else {
            learnedScore = 0.0
        }

        let score = (coverage * 0.32) + (precision * 0.14) + exactBoost + sourceScore + verifiedScore + usageScore + learnedScore

        let confidence: SearchConfidence
        switch true {
        case score >= 0.82: confidence = .HIGH
        case score >= 0.58: confidence = .MEDIUM
        default: confidence = .LOW
        }

        let source: SearchSource
        switch true {
        case food.tags.contains(where: { $0.range(of: "OFF", options: .caseInsensitive) != nil }): source = .OFF
        case food.tags.contains(where: { $0.range(of: "USDA", options: .caseInsensitive) != nil }): source = .USDA
        default: source = .LOCAL
        }

        var trace: [String] = []
        if nameExact || aliasExact { trace.append("exact") }
        if brandContains { trace.append("brand") }
        if let learnedEntry = learnedEntry, learnedEntry.foodId == food.id { trace.append("learned") }

        return FoodCandidate(
            foodId: food.id,
            displayName: food.name,
            score: score,
            confidence: confidence,
            source: source,
            food: food,
            trace: trace,
            queryCoverage: coverage,
            tokenPrecision: precision,
            brandMatched: brandContains,
            learned: learnedEntry?.foodId == food.id
        )
    }

    // MARK: - Normalization

    private func normalizeFoodItem(_ food: FoodItem) -> FoodItem {
        let normalizedName = food.normalizedName ?? FoodIndex.normalizeSearch(food.name)
        let normalizedBrand = food.normalizedBrand ?? food.brand.flatMap { FoodIndex.normalizeSearch($0) }
        let normalizedAliases = food.searchAliases
            .map { FoodIndex.normalizeSearch($0) }
            .filter { !$0.isEmpty }
            .uniqued()

        var withDefaults = food.copy(
            normalizedName: normalizedName,
            normalizedBrand: normalizedBrand,
            searchAliases: normalizedAliases
        )

        let sourcePriority: Int
        if withDefaults.sourcePriority != 50 {
            sourcePriority = withDefaults.sourcePriority
        } else {
            switch true {
            case withDefaults.isCustom: sourcePriority = 95
            case withDefaults.tags.contains(where: { $0.range(of: "OFF", options: .caseInsensitive) != nil }): sourcePriority = 80
            case withDefaults.tags.contains(where: { $0.range(of: "USDA", options: .caseInsensitive) != nil }): sourcePriority = 70
            default: sourcePriority = 60
            }
        }

        let verifiedScore: Double
        if withDefaults.verifiedScore != 0.5 {
            verifiedScore = withDefaults.verifiedScore
        } else {
            switch true {
            case withDefaults.isCustom: verifiedScore = 0.9
            case withDefaults.tags.contains(where: { $0.range(of: "USDA", options: .caseInsensitive) != nil }): verifiedScore = 0.85
            case withDefaults.tags.contains(where: { $0.range(of: "OFF", options: .caseInsensitive) != nil }): verifiedScore = 0.72
            default: verifiedScore = 0.6
            }
        }

        return withDefaults.copy(
            sourcePriority: sourcePriority,
            verifiedScore: verifiedScore
        )
    }

    // MARK: - Persistence

    private func persistFoodLearning() {
        let values = Array(foodQueryLearning.values)
        let payload: String
        if let data = try? dbJson.encode(values) {
            payload = String(decoding: data, as: UTF8.self)
        } else {
            payload = "[]"
        }
        foodPrefs.set(payload, forKey: "food_query_learning")
    }

    private func loadFoodLearning() -> [String: FoodQueryLearningEntry] {
        guard let payload = foodPrefs.string(forKey: "food_query_learning") else { return [:] }
        guard let list = try? dbJsonDecoder.decode([FoodQueryLearningEntry].self, from: Data(payload.utf8)) else { return [:] }
        return Dictionary(uniqueKeysWithValues: list.map { ($0.query, $0) })
    }

    private func saveFoodCatalogMeta(_ meta: FoodCatalogMeta) {
        let encoded: String
        if let data = try? dbJson.encode(meta) {
            encoded = String(decoding: data, as: UTF8.self)
        } else {
            encoded = ""
        }
        foodPrefs.set(encoded, forKey: "food_catalog_meta")
    }

    private func loadFoodCatalogMeta() -> FoodCatalogMeta? {
        guard let encoded = foodPrefs.string(forKey: "food_catalog_meta") else { return nil }
        return try? dbJsonDecoder.decode(FoodCatalogMeta.self, from: Data(encoded.utf8))
    }

    // MARK: - Formatting

    private func formatNumber(_ value: Double) -> String {
        if value.truncatingRemainder(dividingBy: 1.0) == 0.0 {
            return String(Int(value))
        } else {
            var str = String(format: "%.1f", value)
            while str.hasSuffix("0") { str.removeLast() }
            while str.hasSuffix(".") { str.removeLast() }
            return str
        }
    }

    // MARK: - Public Refresh

    func refreshData() {
        loadFromDb()
    }

    // MARK: - Static API

    static func initialize() {
        let repo = shared
        repo.loadFromDb()
    }

    static func getInstance() -> NutritionRepository {
        return shared
    }
}

// MARK: - Private Helpers

private func deterministicUUID(from string: String) -> String {
    let data = string.data(using: .utf8)!
    var digest = [UInt8](repeating: 0, count: 16)
    for (i, byte) in data.enumerated() {
        digest[i % 16] &+= byte
    }
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    let uuid = UUID(uuid: (
        digest[0], digest[1], digest[2], digest[3],
        digest[4], digest[5], digest[6], digest[7],
        digest[8], digest[9], digest[10], digest[11],
        digest[12], digest[13], digest[14], digest[15]
    ))
    return uuid.uuidString
}

private extension Array where Element: Hashable {
    func uniqued() -> [Element] {
        var seen: Set<Element> = []
        return filter { seen.insert($0).inserted }
    }
}

