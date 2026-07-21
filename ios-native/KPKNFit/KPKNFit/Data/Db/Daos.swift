import Foundation

// ─── DateCount ────────────────────────────────────────────────────────────────

internal struct DateCount: Codable {
    let date: String
    let count: Int
}

// Redundant database entity definitions removed (defined in Entities.swift)

// ─── Programs ─────────────────────────────────────────────────────────────────

internal final class ProgramDaoImpl: ProgramDao {
    func getAll() async -> [ProgramEntity] {
        []
    }

    func getById(id: String) async -> ProgramEntity? {
        nil
    }

    func upsert(entity: ProgramEntity) async {}

    func delete(id: String) async {}

    func deleteAll() async {}
}

// ─── Workout Logs ─────────────────────────────────────────────────────────────

internal final class WorkoutLogDaoImpl: WorkoutLogDao {
    func getAll() async -> [WorkoutLogEntity] {
        []
    }

    func getAllSince(from: String) async -> [WorkoutLogEntity] {
        []
    }

    func getByProgram(programId: String) async -> [WorkoutLogEntity] {
        []
    }

    func getBySession(sessionId: String) async -> [WorkoutLogEntity] {
        []
    }

    func insert(entity: WorkoutLogEntity) async {}

    func delete(id: String) async {}
}

// ─── Competition Records ──────────────────────────────────────────────────────

internal final class CompetitionRecordDaoImpl: CompetitionRecordDao {
    func getAll() async -> [CompetitionRecordEntity] {
        []
    }

    func getById(id: String) async -> CompetitionRecordEntity? {
        nil
    }

    func getByPlannedSessionId(sessionId: String) async -> CompetitionRecordEntity? {
        nil
    }

    func upsert(entity: CompetitionRecordEntity) async {}

    func delete(id: String) async {}
}

// ─── Settings ─────────────────────────────────────────────────────────────────

internal final class SettingsDaoImpl: SettingsDao {
    func get() async -> SettingsEntity? {
        nil
    }

    func upsert(entity: SettingsEntity) async {}
}

// ─── State ────────────────────────────────────────────────────────────────────

internal final class StateDaoImpl: StateDao {
    func getActiveProgram() async -> ActiveProgramEntity? {
        nil
    }

    func upsertActiveProgram(entity: ActiveProgramEntity) async {}

    func clearActiveProgram() async {}

    func getOngoingWorkout() async -> OngoingWorkoutEntity? {
        nil
    }

    func upsertOngoingWorkout(entity: OngoingWorkoutEntity) async {}

    func clearOngoingWorkout() async {}
}

// ─── WorkoutV2 ────────────────────────────────────────────────────────────────

internal final class WorkoutV2DaoImpl: WorkoutV2Dao {
    func getContextPerformance(contextKey: String) async -> WorkoutContextPerformanceEntity? {
        nil
    }

    func getAllContextPerformance() async -> [WorkoutContextPerformanceEntity] {
        []
    }

    func upsertContextPerformance(entity: WorkoutContextPerformanceEntity) async {}

    func getGlobalPerformance(globalKey: String) async -> WorkoutGlobalPerformanceEntity? {
        nil
    }

    func getAllGlobalPerformance() async -> [WorkoutGlobalPerformanceEntity] {
        []
    }

    func upsertGlobalPerformance(entity: WorkoutGlobalPerformanceEntity) async {}

    func getContextProfilesForExercise(exerciseKey: String) async -> [WorkoutContextProfileEntity] {
        []
    }

    func getAllContextProfiles() async -> [WorkoutContextProfileEntity] {
        []
    }

    func upsertContextProfile(entity: WorkoutContextProfileEntity) async {}

    func getReplacementDecisions(programId: String) async -> [WorkoutReplacementDecisionEntity] {
        []
    }

    func getAllReplacementDecisions() async -> [WorkoutReplacementDecisionEntity] {
        []
    }

    func upsertReplacementDecision(entity: WorkoutReplacementDecisionEntity) async {}
}

// ─── Auge ─────────────────────────────────────────────────────────────────────

internal final class AugeDaoImpl: AugeDao {
    func getAllWellbeing() async -> [WellbeingEntity] {
        []
    }

    func getWellbeingForDate(date: String) async -> WellbeingEntity? {
        nil
    }

    func getWellbeingInRange(from: String, to: String) async -> [WellbeingEntity] {
        []
    }

    func upsertWellbeing(entity: WellbeingEntity) async {}

    func getLastNSleepLogs(n: Int) async -> [SleepLogEntity] {
        []
    }

    func upsertSleepLog(entity: SleepLogEntity) async {}

    func getLastNSleepLogsExtended(n: Int) async -> [SleepLogExtendedEntity] {
        []
    }

    func getAllSleepLogsExtended() async -> [SleepLogExtendedEntity] {
        []
    }

    func upsertSleepLogExtended(entity: SleepLogExtendedEntity) async {}

    func deleteSleepLogExtended(id: String) async {}

    func getAllFeedback() async -> [PostSessionFeedbackEntity] {
        []
    }

    func getFeedbackSince(from: String) async -> [PostSessionFeedbackEntity] {
        []
    }

    func getFeedbackForLog(logId: String) async -> PostSessionFeedbackEntity? {
        nil
    }

    func upsertFeedback(entity: PostSessionFeedbackEntity) async {}

    func getPendingQuestionnaire() async -> PendingQuestionnaireEntity? {
        nil
    }

    func upsertPendingQuestionnaire(entity: PendingQuestionnaireEntity) async {}

    func clearPendingQuestionnaire() async {}

    func getAdaptiveCache() async -> AugeAdaptiveCacheEntity? {
        nil
    }

    func upsertAdaptiveCache(entity: AugeAdaptiveCacheEntity) async {}
}

// ─── Nutrition ────────────────────────────────────────────────────────────────

internal final class NutritionDaoImpl: NutritionDao {
    func getAllLogs() async -> [NutritionLogEntity] {
        []
    }

    func getLogsSince(from: String) async -> [NutritionLogEntity] {
        []
    }

    func getLogsForDate(date: String) async -> [NutritionLogEntity] {
        []
    }

    func upsertLog(entity: NutritionLogEntity) async {}

    func deleteLog(id: String) async {}

    func clearAllLogs() async {}

    func getAllPlans() async -> [NutritionPlanEntity] {
        []
    }

    func upsertPlan(entity: NutritionPlanEntity) async {}

    func deletePlan(id: String) async {}

    func getActiveState() async -> NutritionActiveStateEntity? {
        nil
    }

    func upsertActiveState(entity: NutritionActiveStateEntity) async {}

    func clearActiveState() async {}

    func getAllPantryItems() async -> [PantryItemEntity] {
        []
    }

    func upsertPantryItem(entity: PantryItemEntity) async {}

    func deletePantryItem(id: String) async {}

    func getAllTemplates() async -> [MealTemplateEntity] {
        []
    }

    func upsertTemplate(entity: MealTemplateEntity) async {}

    func deleteTemplate(id: String) async {}

    func getAllCustomFoods() async -> [CustomFoodEntity] {
        []
    }

    func searchCustomFoods(normalizedQuery: String, limit: Int) async -> [CustomFoodEntity] {
        []
    }

    func incrementCustomFoodUsage(foodId: String, lastUsedAt: String) async {}

    func upsertCustomFood(entity: CustomFoodEntity) async {}

    func deleteCustomFood(id: String) async {}

    func searchGlobalFoods(query: String) async -> [GlobalFoodEntity] {
        []
    }

    func searchGlobalFoodsNormalized(normalizedQuery: String, limit: Int) async -> [GlobalFoodEntity] {
        []
    }

    func searchGlobalFoodsWithFts(query: String) async -> [GlobalFoodEntity] {
        []
    }

    func getGlobalFoodCount() async -> Int {
        0
    }

    func getAllGlobalFoods() async -> [GlobalFoodEntity] {
        []
    }

    func insertGlobalFoods(foods: [GlobalFoodEntity]) async {}

    func incrementGlobalFoodUsage(foodId: String, lastUsedAt: String) async {}

    func clearGlobalFoods() async {}

    func activatePlanAtomic(planId: String, plans: [NutritionPlanEntity]) async {}
}

// ─── Custom Exercises ─────────────────────────────────────────────────────────

internal final class CustomExerciseDaoImpl: CustomExerciseDao {
    func getAll() async -> [CustomExerciseEntity] {
        []
    }

    func getById(id: String) async -> CustomExerciseEntity? {
        nil
    }

    func upsert(entity: CustomExerciseEntity) async {}

    func delete(id: String) async {}
}

// ─── Session Templates ────────────────────────────────────────────────────────

internal final class SessionTemplateDaoImpl: SessionTemplateDao {
    func getAll() async -> [SessionTemplateEntity] {
        []
    }

    func getById(id: String) async -> SessionTemplateEntity? {
        nil
    }

    func upsert(entity: SessionTemplateEntity) async {}

    func delete(id: String) async {}
}

// ─── Learned Resolutions ──────────────────────────────────────────────────────

internal final class LearnedResolutionDaoImpl: LearnedResolutionDao {
    func getByQueryKey(queryKey: String) async -> LearnedResolutionEntity? {
        nil
    }

    func upsert(entity: LearnedResolutionEntity) async {}

    func increment(queryKey: String, lastUsedAt: Int64, portionGrams: Double?, cookingMethod: String?) async {}

    func topFor(prefix: String, n: Int) async -> [LearnedResolutionEntity] {
        []
    }

    func prune(keep: Int) async {}

    func count() async -> Int {
        0
    }
}

// ─── Performance Range ────────────────────────────────────────────────────────

internal final class PerformanceRangeDaoImpl: PerformanceRangeDao {
    func getByContextKey(contextKey: String) async -> PerformanceRangeEntity? {
        nil
    }

    func getAll() async -> [PerformanceRangeEntity] {
        []
    }

    func upsert(entity: PerformanceRangeEntity) async {}
}

// ─── Performance Snapshot ─────────────────────────────────────────────────────

internal final class PerformanceSnapshotDaoImpl: PerformanceSnapshotDao {
    func getByContextKey(contextKey: String) async -> [PerformanceSnapshotEntity] {
        []
    }

    func getRecent(limit: Int) async -> [PerformanceSnapshotEntity] {
        []
    }

    func upsert(entity: PerformanceSnapshotEntity) async {}

    func delete(id: Int64) async {}
}
