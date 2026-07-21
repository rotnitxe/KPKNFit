import Foundation

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
    (try? dbJson.encode(value)) ?? "{}"
}

private func decodeModel<T: Decodable>(_ type: T.Type, from data: String) -> T? {
    try? dbJsonDecoder.decode(type, from: Data(data.utf8))
}

private func encodeAliases(_ aliases: [String]) -> String {
    let filtered = aliases.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.uniqued()
    return encodeModel(filtered)
}

private func decodeAliases(_ json: String) -> [String] {
    decodeModel([String].self, from: json) ?? []
}

private func normalizeSearch(_ value: String?) -> String {
    guard let value = value, !value.trimmingCharacters(in: .whitespaces).isEmpty else { return "" }
    var stripped = value.precomposedStringWithCompatibilityMapping
    stripped = stripped.replacingOccurrences(
        of: "[\\u{0300}-\\u{036F}]",
        with: "",
        options: .regularExpression
    )
    let lower = stripped.lowercased()
    let lettersDigits = lower.replacingOccurrences(
        of: "[^\\p{L}\\p{N}]+",
        with: " ",
        options: .regularExpression
    )
    let collapsed = lettersDigits.replacingOccurrences(
        of: "\\s+",
        with: " ",
        options: .regularExpression
    )
    return collapsed.trimmingCharacters(in: .whitespaces)
}

// MARK: - Entities

internal struct ProgramEntity: Codable {
    let id: String
    let name: String
    let data: String
}

internal struct WorkoutLogEntity: Codable {
    let id: String
    let programId: String
    let sessionId: String
    let date: String
    let data: String
}

internal struct CompetitionRecordEntity: Codable {
    let id: String
    let title: String
    let eventDate: String
    let status: String
    let sportType: String
    let plannedSessionId: String
    let updatedAtMs: Int64
    let data: String
}

internal struct SettingsEntity: Codable {
    let rowId: Int
    let data: String
}

internal struct ActiveProgramEntity: Codable {
    let rowId: Int
    let data: String?
}

internal struct OngoingWorkoutEntity: Codable {
    let rowId: Int
    let data: String?
}

internal struct WorkoutContextPerformanceEntity: Codable {
    let contextKey: String
    let updatedAt: String
    let data: String
}

internal struct WorkoutGlobalPerformanceEntity: Codable {
    let globalKey: String
    let updatedAt: String
    let data: String
}

internal struct WorkoutContextProfileEntity: Codable {
    let id: String
    let exerciseKey: String
    let lastUsedAt: String
    let data: String
}

internal struct WorkoutReplacementDecisionEntity: Codable {
    let id: String
    let programId: String
    let sessionId: String
    let createdAt: String
    let data: String
}

internal struct WellbeingEntity: Codable {
    let id: String
    let date: String
    let data: String
}

internal struct SleepLogEntity: Codable {
    let id: String
    let date: String
    let data: String
}

internal struct SleepLogExtendedEntity: Codable {
    let id: String
    let date: String
    let data: String
}

internal struct PostSessionFeedbackEntity: Codable {
    let logId: String
    let date: String
    let data: String
}

internal struct PendingQuestionnaireEntity: Codable {
    let rowId: Int
    let data: String?
}

internal struct AugeAdaptiveCacheEntity: Codable {
    let rowId: Int
    let data: String?
}

internal struct NutritionLogEntity: Codable {
    let id: String
    let date: String
    let mealType: String
    let data: String
}

internal struct NutritionPlanEntity: Codable {
    let id: String
    let name: String
    let isActive: Bool
    let data: String
}

internal struct NutritionActiveStateEntity: Codable {
    let rowId: Int
    let activePlanId: String?
}

internal struct PantryItemEntity: Codable {
    let id: String
    let name: String
    let data: String
}

internal struct MealTemplateEntity: Codable {
    let id: String
    let name: String
    let data: String
}

internal struct CustomFoodEntity: Codable {
    let id: String
    let name: String
    let normalizedName: String
    let normalizedBrand: String?
    let aliasesJson: String
    let sourcePriority: Int
    let verifiedScore: Double
    let usageCount: Int
    let lastUsedAt: String?
    let data: String
}

internal struct GlobalFoodEntity: Codable {
    let foodId: String
    let name: String
    let brand: String?
    let normalizedName: String
    let normalizedBrand: String?
    let aliasesJson: String
    let calories: Double
    let protein: Double
    let carbs: Double
    let fats: Double
    let fiber: Double
    let sugar: Double
    let sodiumMg: Double
    let potassiumMg: Double
    let waterMl: Double
    let source: String
    let sourcePriority: Int
    let verifiedScore: Double
    let usageCount: Int
    let lastUsedAt: String?
}

internal struct GlobalFoodFtsEntity: Codable {
    let name: String
    let brand: String?
}

internal struct SessionTemplateEntity: Codable {
    let id: String
    let sourceType: String
    let name: String
    let sortOrder: Int
    let isArchived: Int
    let createdAt: String
    let data: String
}

internal struct CustomExerciseEntity: Codable {
    let id: String
    let name: String
    let data: String
    let createdAt: String
    let updatedAt: String
}

internal struct LearnedResolutionEntity: Codable {
    let id: String
    let queryKey: String
    let foodId: String
    let portionGrams: Double?
    let cookingMethod: String?
    let count: Int
    let lastUsedAt: Int64
    let createdAt: Int64
    let syncedAt: Int64?
}

// MARK: - Program Conversions

extension Program {
    func toEntity() -> ProgramEntity {
        ProgramEntity(id: id, name: name, data: encodeModel(self))
    }
}

extension ProgramEntity {
    func toProgram() -> Program {
        decodeModel(Program.self, from: data)!
    }
}

// MARK: - WorkoutLog Conversions

extension WorkoutLog {
    func toEntity() -> WorkoutLogEntity {
        WorkoutLogEntity(id: id, programId: programId, sessionId: sessionId, date: date, data: encodeModel(self))
    }
}

extension WorkoutLogEntity {
    func toWorkoutLog() -> WorkoutLog {
        decodeModel(WorkoutLog.self, from: data)!
    }
}

// MARK: - CompetitionRecord Conversions

extension CompetitionRecord {
    func toEntity() -> CompetitionRecordEntity {
        CompetitionRecordEntity(
            id: id,
            title: title,
            eventDate: eventDate ?? "",
            status: status.rawValue,
            sportType: sportType.rawValue,
            plannedSessionId: plannedSessionId ?? "",
            updatedAtMs: updatedAtMs,
            data: encodeModel(self)
        )
    }
}

extension CompetitionRecordEntity {
    func toCompetitionRecord() -> CompetitionRecord {
        decodeModel(CompetitionRecord.self, from: data)!
    }
}

// MARK: - Settings Conversions

extension AppSettings {
    func toEntity() -> SettingsEntity {
        SettingsEntity(rowId: 1, data: encodeModel(self))
    }
}

extension SettingsEntity {
    func toSettings() -> AppSettings {
        decodeModel(AppSettings.self, from: data)!
    }
}

// MARK: - ActiveProgramState Conversions

extension ActiveProgramState {
    func toEntity() -> ActiveProgramEntity {
        ActiveProgramEntity(rowId: 1, data: encodeModel(self))
    }
}

extension ActiveProgramEntity {
    func toActiveProgramState() -> ActiveProgramState {
        decodeModel(ActiveProgramState.self, from: data ?? "{}")!
    }
}

// MARK: - OngoingWorkoutState Conversions

extension OngoingWorkoutState {
    func toEntity() -> OngoingWorkoutEntity {
        OngoingWorkoutEntity(rowId: 1, data: encodeModel(self))
    }
}

extension OngoingWorkoutEntity {
    func toOngoingWorkoutState() -> OngoingWorkoutState {
        decodeModel(OngoingWorkoutState.self, from: data ?? "{}")!
    }
}

// MARK: - WorkoutContextPerformance Conversions

extension ContextPerformanceStateV2 {
    func toEntity() -> WorkoutContextPerformanceEntity {
        WorkoutContextPerformanceEntity(contextKey: contextKey, updatedAt: lastUpdatedAtIso ?? "", data: encodeModel(self))
    }
}

extension WorkoutContextPerformanceEntity {
    func toContextPerformanceStateV2() -> ContextPerformanceStateV2 {
        decodeModel(ContextPerformanceStateV2.self, from: data)!
    }
}

// MARK: - WorkoutGlobalPerformance Conversions

extension GlobalPerformanceStateV3 {
    func toEntity() -> WorkoutGlobalPerformanceEntity {
        WorkoutGlobalPerformanceEntity(globalKey: globalKey, updatedAt: lastUpdatedAtIso ?? "", data: encodeModel(self))
    }
}

extension WorkoutGlobalPerformanceEntity {
    func toGlobalPerformanceStateV3() -> GlobalPerformanceStateV3 {
        decodeModel(GlobalPerformanceStateV3.self, from: data)!
    }
}

// MARK: - WorkoutContextProfile Conversions

extension WorkoutContextProfile {
    func toEntity() -> WorkoutContextProfileEntity {
        WorkoutContextProfileEntity(id: id, exerciseKey: exerciseKey, lastUsedAt: lastUsedAtIso ?? "", data: encodeModel(self))
    }
}

extension WorkoutContextProfileEntity {
    func toWorkoutContextProfile() -> WorkoutContextProfile {
        decodeModel(WorkoutContextProfile.self, from: data)!
    }
}

// MARK: - ExerciseReplacementDecisionV2 Conversions

extension ExerciseReplacementDecisionV2 {
    func toEntity() -> WorkoutReplacementDecisionEntity {
        WorkoutReplacementDecisionEntity(id: id, programId: programId, sessionId: sessionId, createdAt: createdAtIso, data: encodeModel(self))
    }
}

extension WorkoutReplacementDecisionEntity {
    func toExerciseReplacementDecisionV2() -> ExerciseReplacementDecisionV2 {
        decodeModel(ExerciseReplacementDecisionV2.self, from: data)!
    }
}

// MARK: - Wellbeing Conversions

extension DailyWellbeingLog {
    func toEntity() -> WellbeingEntity {
        WellbeingEntity(id: id, date: date, data: encodeModel(self))
    }
}

extension WellbeingEntity {
    func toWellbeingLog() -> DailyWellbeingLog {
        decodeModel(DailyWellbeingLog.self, from: data)!
    }
}

// MARK: - SleepLog Conversions

extension SleepLog {
    func toEntity() -> SleepLogEntity {
        SleepLogEntity(id: id, date: date, data: encodeModel(self))
    }
}

extension SleepLogEntity {
    func toSleepLog() -> SleepLog {
        decodeModel(SleepLog.self, from: data)!
    }
}

// MARK: - SleepLogExtended Conversions

extension SleepLogExtended {
    func toExtendedEntity() -> SleepLogExtendedEntity {
        SleepLogExtendedEntity(id: id, date: date, data: encodeModel(self))
    }
}

extension SleepLogExtendedEntity {
    func toSleepLogExtended() -> SleepLogExtended {
        decodeModel(SleepLogExtended.self, from: data)!
    }
}

// MARK: - PostSessionFeedback Conversions

extension PostSessionFeedback {
    func toEntity() -> PostSessionFeedbackEntity {
        PostSessionFeedbackEntity(logId: logId, date: date, data: encodeModel(self))
    }
}

extension PostSessionFeedbackEntity {
    func toFeedback() -> PostSessionFeedback {
        decodeModel(PostSessionFeedback.self, from: data)!
    }
}

// MARK: - PendingQuestionnaire Conversions

extension PendingQuestionnaire {
    func toEntity() -> PendingQuestionnaireEntity {
        PendingQuestionnaireEntity(rowId: 1, data: encodeModel(self))
    }
}

extension PendingQuestionnaireEntity {
    func toPendingQuestionnaire() -> PendingQuestionnaire {
        decodeModel(PendingQuestionnaire.self, from: data ?? "{}")!
    }
}

// MARK: - AugeAdaptiveCache Conversions

extension AugeAdaptiveCache {
    func toEntity() -> AugeAdaptiveCacheEntity {
        AugeAdaptiveCacheEntity(rowId: 1, data: encodeModel(self))
    }
}

extension AugeAdaptiveCacheEntity {
    func toAdaptiveCache() -> AugeAdaptiveCache {
        decodeModel(AugeAdaptiveCache.self, from: data ?? "{}")!
    }
}

// MARK: - NutritionLog Conversions

extension NutritionLog {
    func toEntity() -> NutritionLogEntity {
        NutritionLogEntity(id: id, date: date, mealType: mealType.rawValue, data: encodeModel(self))
    }
}

extension NutritionLogEntity {
    func toNutritionLog() -> NutritionLog {
        decodeModel(NutritionLog.self, from: data)!
    }
}

// MARK: - NutritionPlan Conversions

extension NutritionPlan {
    func toEntity() -> NutritionPlanEntity {
        NutritionPlanEntity(id: id, name: name, isActive: isActive, data: encodeModel(self))
    }
}

extension NutritionPlanEntity {
    func toNutritionPlan() -> NutritionPlan {
        decodeModel(NutritionPlan.self, from: data)!
    }
}

// MARK: - PantryItem Conversions

extension PantryItem {
    func toEntity() -> PantryItemEntity {
        PantryItemEntity(id: id, name: name, data: encodeModel(self))
    }
}

extension PantryItemEntity {
    func toPantryItem() -> PantryItem {
        decodeModel(PantryItem.self, from: data)!
    }
}

// MARK: - MealTemplate Conversions

extension MealTemplate {
    func toEntity() -> MealTemplateEntity {
        MealTemplateEntity(id: id, name: name, data: encodeModel(self))
    }
}

extension MealTemplateEntity {
    func toMealTemplate() -> MealTemplate {
        decodeModel(MealTemplate.self, from: data)!
    }
}

// MARK: - FoodItem Conversions

extension FoodItem {
    func toEntity() -> CustomFoodEntity {
        let normalizedNameValue = normalizedName ?? normalizeSearch(name)
        let normalizedBrandValue = normalizedBrand ?? brand.flatMap { normalizeSearch($0) }
        let aliases = (searchAliases + [name, brand].compactMap { $0 })
            .map { normalizeSearch($0) }
            .filter { !$0.isEmpty }

        var normalizedCopy = self
        normalizedCopy.normalizedName = normalizedNameValue
        normalizedCopy.normalizedBrand = normalizedBrandValue
        normalizedCopy.searchAliases = (searchAliases + aliases).uniqued()

        return CustomFoodEntity(
            id: id,
            name: name,
            normalizedName: normalizedNameValue,
            normalizedBrand: normalizedBrandValue,
            aliasesJson: encodeAliases(aliases),
            sourcePriority: sourcePriority,
            verifiedScore: verifiedScore,
            usageCount: usageCount,
            lastUsedAt: lastUsedAt,
            data: encodeModel(normalizedCopy)
        )
    }
}

extension CustomFoodEntity {
    func toFoodItem() -> FoodItem {
        let decoded = decodeModel(FoodItem.self, from: data)!
        let aliases = decodeAliases(aliasesJson)
        var result = decoded
        result.id = decoded.id.isEmpty ? id : decoded.id
        result.name = decoded.name.isEmpty ? name : decoded.name
        result.normalizedName = decoded.normalizedName ?? normalizedName
        result.normalizedBrand = decoded.normalizedBrand ?? normalizedBrand
        result.searchAliases = (decoded.searchAliases + aliases).uniqued()
        result.sourcePriority = decoded.sourcePriority != 50 ? decoded.sourcePriority : sourcePriority
        result.verifiedScore = decoded.verifiedScore != 0.5 ? decoded.verifiedScore : verifiedScore
        result.usageCount = max(decoded.usageCount, usageCount)
        result.lastUsedAt = decoded.lastUsedAt ?? lastUsedAt
        return result
    }
}

// MARK: - SessionTemplate Conversions

extension SessionTemplate {
    func toEntity() -> SessionTemplateEntity {
        SessionTemplateEntity(
            id: id,
            sourceType: sourceType.rawValue,
            name: name,
            sortOrder: sortOrder,
            isArchived: isArchived ? 1 : 0,
            createdAt: createdAt ?? "",
            data: encodeModel(self)
        )
    }
}

extension SessionTemplateEntity {
    func toSessionTemplate() -> SessionTemplate {
        decodeModel(SessionTemplate.self, from: data)!
    }
}

// MARK: - CustomExercise Conversions

extension ExerciseMuscleInfo {
    func toEntity(nowIso: String = isoTimestamp()) -> CustomExerciseEntity {
        var copy = self
        copy.isCustom = true
        return CustomExerciseEntity(id: id, name: name, data: encodeModel(copy), createdAt: nowIso, updatedAt: nowIso)
    }
}

extension CustomExerciseEntity {
    func toExerciseMuscleInfo() -> ExerciseMuscleInfo {
        decodeModel(ExerciseMuscleInfo.self, from: data)!
    }
}

// MARK: - GlobalFoodEntity Conversions

extension GlobalFoodEntity {
    func toFoodItem() -> FoodItem {
        let decodedNormalizedName = normalizedName.trimmingCharacters(in: .whitespaces).isEmpty
            ? normalizeSearch(name)
            : normalizedName
        let micronutrients: [Micronutrient] = [
            sodiumMg > 0 ? Micronutrient(name: "Sodio", amount: sodiumMg, unit: "mg") : nil,
            potassiumMg > 0 ? Micronutrient(name: "Potasio", amount: potassiumMg, unit: "mg") : nil,
            waterMl > 0 ? Micronutrient(name: "Agua", amount: waterMl, unit: "ml") : nil,
        ].compactMap { $0 }

        return FoodItem(
            id: foodId,
            name: name,
            brand: brand,
            normalizedName: decodedNormalizedName,
            normalizedBrand: normalizedBrand,
            calories: calories,
            protein: protein,
            carbs: carbs,
            fats: fats,
            carbBreakdown: CarbBreakdown(fiber: fiber, sugar: sugar),
            micronutrients: micronutrients,
            tags: [source],
            searchAliases: decodeAliases(aliasesJson),
            sourcePriority: sourcePriority,
            verifiedScore: verifiedScore,
            usageCount: usageCount,
            lastUsedAt: lastUsedAt
        )
    }
}

// MARK: - Private Helpers

private func isoTimestamp() -> String {
    let formatter = ISO8601DateFormatter()
    return formatter.string(from: Date())
}

private extension Array where Element: Hashable {
    func uniqued() -> [Element] {
        var seen: Set<Element> = []
        return filter { seen.insert($0).inserted }
    }
}
