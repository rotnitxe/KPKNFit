import Foundation
import SQLite3

// MARK: - DAO Protocols

internal protocol ProgramDao {
    func getAll() async -> [ProgramEntity]
    func getById(id: String) async -> ProgramEntity?
    func upsert(entity: ProgramEntity) async
    func delete(id: String) async
    func deleteAll() async
}

internal protocol WorkoutLogDao {
    func getAll() async -> [WorkoutLogEntity]
    func getAllSince(from: String) async -> [WorkoutLogEntity]
    func getByProgram(programId: String) async -> [WorkoutLogEntity]
    func getBySession(sessionId: String) async -> [WorkoutLogEntity]
    func insert(entity: WorkoutLogEntity) async
    func delete(id: String) async
}

internal protocol CompetitionRecordDao {
    func getAll() async -> [CompetitionRecordEntity]
    func getById(id: String) async -> CompetitionRecordEntity?
    func getByPlannedSessionId(sessionId: String) async -> CompetitionRecordEntity?
    func upsert(entity: CompetitionRecordEntity) async
    func delete(id: String) async
}

internal protocol SettingsDao {
    func get() async -> SettingsEntity?
    func upsert(entity: SettingsEntity) async
}

internal protocol StateDao {
    func getActiveProgram() async -> ActiveProgramEntity?
    func upsertActiveProgram(entity: ActiveProgramEntity) async
    func clearActiveProgram() async
    func getOngoingWorkout() async -> OngoingWorkoutEntity?
    func upsertOngoingWorkout(entity: OngoingWorkoutEntity) async
    func clearOngoingWorkout() async
}

internal protocol WorkoutV2Dao {
    func getContextPerformance(contextKey: String) async -> WorkoutContextPerformanceEntity?
    func getAllContextPerformance() async -> [WorkoutContextPerformanceEntity]
    func upsertContextPerformance(entity: WorkoutContextPerformanceEntity) async
    func getGlobalPerformance(globalKey: String) async -> WorkoutGlobalPerformanceEntity?
    func getAllGlobalPerformance() async -> [WorkoutGlobalPerformanceEntity]
    func upsertGlobalPerformance(entity: WorkoutGlobalPerformanceEntity) async
    func getContextProfilesForExercise(exerciseKey: String) async -> [WorkoutContextProfileEntity]
    func getAllContextProfiles() async -> [WorkoutContextProfileEntity]
    func upsertContextProfile(entity: WorkoutContextProfileEntity) async
    func getReplacementDecisions(programId: String) async -> [WorkoutReplacementDecisionEntity]
    func getAllReplacementDecisions() async -> [WorkoutReplacementDecisionEntity]
    func upsertReplacementDecision(entity: WorkoutReplacementDecisionEntity) async
}



internal protocol NutritionDao {
    func getAllLogs() async -> [NutritionLogEntity]
    func getLogsSince(from: String) async -> [NutritionLogEntity]
    func getLogsForDate(date: String) async -> [NutritionLogEntity]
    func upsertLog(entity: NutritionLogEntity) async
    func deleteLog(id: String) async
    func clearAllLogs() async
    func getAllPlans() async -> [NutritionPlanEntity]
    func upsertPlan(entity: NutritionPlanEntity) async
    func deletePlan(id: String) async
    func getActiveState() async -> NutritionActiveStateEntity?
    func upsertActiveState(entity: NutritionActiveStateEntity) async
    func clearActiveState() async
    func getAllPantryItems() async -> [PantryItemEntity]
    func upsertPantryItem(entity: PantryItemEntity) async
    func deletePantryItem(id: String) async
    func getAllTemplates() async -> [MealTemplateEntity]
    func upsertTemplate(entity: MealTemplateEntity) async
    func deleteTemplate(id: String) async
    func getAllCustomFoods() async -> [CustomFoodEntity]
    func searchCustomFoods(normalizedQuery: String, limit: Int) async -> [CustomFoodEntity]
    func incrementCustomFoodUsage(foodId: String, lastUsedAt: String) async
    func upsertCustomFood(entity: CustomFoodEntity) async
    func deleteCustomFood(id: String) async
    func searchGlobalFoods(query: String) async -> [GlobalFoodEntity]
    func searchGlobalFoodsNormalized(normalizedQuery: String, limit: Int) async -> [GlobalFoodEntity]
    func searchGlobalFoodsWithFts(query: String) async -> [GlobalFoodEntity]
    func getGlobalFoodCount() async -> Int
    func getAllGlobalFoods() async -> [GlobalFoodEntity]
    func insertGlobalFoods(foods: [GlobalFoodEntity]) async
    func incrementGlobalFoodUsage(foodId: String, lastUsedAt: String) async
    func clearGlobalFoods() async
    func activatePlanAtomic(planId: String, plans: [NutritionPlanEntity]) async
}

internal protocol CustomExerciseDao {
    func getAll() async -> [CustomExerciseEntity]
    func getById(id: String) async -> CustomExerciseEntity?
    func upsert(entity: CustomExerciseEntity) async
    func delete(id: String) async
}

internal protocol WikiLabDao {
    func getAllMuscleGroups() async -> [MuscleGroupEntity]
    func getAllJoints() async -> [JointEntity]
    func getAllTendons() async -> [TendonEntity]
    func getAllMovementPatterns() async -> [MovementPatternEntity]
    func getAllKineticChains() async -> [KineticChainEntity]
    func insertMuscleGroup(_ entity: MuscleGroupEntity) async
    func insertJoint(_ entity: JointEntity) async
    func insertTendon(_ entity: TendonEntity) async
    func insertMovementPattern(_ entity: MovementPatternEntity) async
    func insertKineticChain(_ entity: KineticChainEntity) async
    func clearAll() async
    func count() async -> Int
}

internal protocol SessionTemplateDao {
    func getAll() async -> [SessionTemplateEntity]
    func getById(id: String) async -> SessionTemplateEntity?
    func upsert(entity: SessionTemplateEntity) async
    func delete(id: String) async
}

internal protocol LearnedResolutionDao {
    func getByQueryKey(queryKey: String) async -> LearnedResolutionEntity?
    func upsert(entity: LearnedResolutionEntity) async
    func increment(queryKey: String, lastUsedAt: Int64, portionGrams: Double?, cookingMethod: String?) async
    func topFor(prefix: String, n: Int) async -> [LearnedResolutionEntity]
    func prune(keep: Int) async
    func count() async -> Int
}

internal protocol PerformanceRangeDao {
    func getByContextKey(contextKey: String) async -> PerformanceRangeEntity?
    func getAll() async -> [PerformanceRangeEntity]
    func upsert(entity: PerformanceRangeEntity) async
}

internal protocol PerformanceSnapshotDao {
    func getByContextKey(contextKey: String) async -> [PerformanceSnapshotEntity]
    func getRecent(limit: Int) async -> [PerformanceSnapshotEntity]
    func upsert(entity: PerformanceSnapshotEntity) async
    func delete(id: Int64) async
}

// MARK: - Internal Database Implementation

internal final class KpknDatabaseInternal {
    private var db: OpaquePointer?
    private let dbPath: String

    init(filePath: String) throws {
        self.dbPath = filePath
        try open()
    }

    deinit {
        close()
    }

    func close() {
        if let handle = db {
            sqlite3_close(handle)
            db = nil
        }
    }

    var handle: OpaquePointer? { db }

    private func open() throws {
        guard sqlite3_open(dbPath, &db) == SQLITE_OK else {
            let msg = String(cString: sqlite3_errmsg(db))
            sqlite3_close(db)
            db = nil
            throw NSError(domain: "KpknDatabase", code: 1, userInfo: [NSLocalizedDescriptionKey: msg])
        }

        sqlite3_exec(db, "PRAGMA journal_mode=WAL", nil, nil, nil)
        sqlite3_exec(db, "PRAGMA journal_mode=WAL", nil, nil, nil)
        sqlite3_exec(db, "PRAGMA cache_size=-64000", nil, nil, nil)
        sqlite3_exec(db, "PRAGMA temp_store=MEMORY", nil, nil, nil)
        sqlite3_exec(db, "PRAGMA synchronous=NORMAL", nil, nil, nil)
    }

    func execute(_ sql: String) throws {
        var errMsg: UnsafeMutablePointer<Int8>?
        guard sqlite3_exec(db, sql, nil, nil, &errMsg) == SQLITE_OK else {
            let msg = errMsg.map { String(cString: $0) } ?? "unknown error"
            if let ptr = errMsg { sqlite3_free(ptr) }
            throw NSError(domain: "KpknDatabase", code: 2, userInfo: [NSLocalizedDescriptionKey: msg])
        }
    }

    func runMigrations(currentVersion: Int32) throws -> Int32 {
        var version = currentVersion
        try execute("BEGIN IMMEDIATE")
        do {
            for step in KpknDatabase.migrationSteps where version == step.fromVersion {
                try step.migrate(db: self)
                version = step.toVersion
            }
            if version != currentVersion { try execute("PRAGMA user_version=\(version)") }
            try execute("COMMIT")
        } catch {
            try? execute("ROLLBACK")
            throw error
        }

        return version
    }

    func getUserVersion() throws -> Int32 {
        var stmt: OpaquePointer?
        var version: Int32 = 0
        if sqlite3_prepare_v2(db, "PRAGMA user_version", -1, &stmt, nil) == SQLITE_OK,
           sqlite3_step(stmt) == SQLITE_ROW {
            version = Int32(sqlite3_column_int64(stmt, 0))
        }
        if stmt != nil { sqlite3_finalize(stmt) }
        return version
    }
}

// MARK: - Public Database

public final class KpknDatabase {

    // MARK: DAO Properties

    let programDao: ProgramDao
    let workoutLogDao: WorkoutLogDao
    let competitionRecordDao: CompetitionRecordDao
    let settingsDao: SettingsDao
    let stateDao: StateDao
    let workoutV2Dao: WorkoutV2Dao
    let nutritionDao: NutritionDao
    let customExerciseDao: CustomExerciseDao
    let wikiLabDao: WikiLabDao
    let sessionTemplateDao: SessionTemplateDao
    let learnedResolutionDao: LearnedResolutionDao
    let performanceRangeDao: PerformanceRangeDao
    let performanceSnapshotDao: PerformanceSnapshotDao

    // MARK: Singleton

    private static let lock = DispatchQueue(label: "kpkn.database.singleton")
    private static var _instance: KpknDatabase?

    public class func instance() -> KpknDatabase {
        lock.sync {
            if _instance == nil {
                do {
                    _instance = try KpknDatabase()
                } catch {
                    fatalError("Failed to open KpknDatabase: \(error)")
                }
            }
            return _instance!
        }
    }

    public class func closeInstance() {
        lock.sync {
            _instance = nil
        }
    }

    // MARK: Internal

    let internalDb: KpknDatabaseInternal

    private init() throws {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dbURL = docs.appendingPathComponent("kpkn.db")
        let isNew = !FileManager.default.fileExists(atPath: dbURL.path)
        let internalDb = try KpknDatabaseInternal(filePath: dbURL.path)
        self.internalDb = internalDb


        if isNew {
            try createAllTables(db: internalDb)
            try internalDb.execute("PRAGMA user_version=19")
            _ = try internalDb.runMigrations(currentVersion: 19)
        } else {
            let currentVersion = try internalDb.getUserVersion()

            if #available(iOS 16.0, *) {
                try? internalDb.execute("PRAGMA journal_mode=WAL")
            }

            let newVersion = try internalDb.runMigrations(currentVersion: currentVersion)

            if #available(iOS 16.0, *) {
                try? internalDb.execute("VACUUM")
            }

            try? FileManager.default.removeItem(atPath: docs.appendingPathComponent("kpkn.db-journal").path)
        }

        let pm = ProgramDaoImpl(db: internalDb)
        let wl = WorkoutLogDaoImpl(db: internalDb)
        let cr = CompetitionRecordDaoImpl(db: internalDb)
        let sd = SettingsDaoImpl(db: internalDb)
        let st = StateDaoImpl(db: internalDb)
        let wv = WorkoutV2DaoImpl(db: internalDb)
        let nd = NutritionDaoImpl(db: internalDb)
        let ce = CustomExerciseDaoImpl(db: internalDb)
        let wd = WikiLabDaoImpl(db: internalDb)
        let tp = SessionTemplateDaoImpl(db: internalDb)
        let lr = LearnedResolutionDaoImpl(db: internalDb)
        let pr = PerformanceRangeDaoImpl(db: internalDb)
        let ps = PerformanceSnapshotDaoImpl(db: internalDb)

        programDao = pm
        workoutLogDao = wl
        competitionRecordDao = cr
        settingsDao = sd
        stateDao = st
        workoutV2Dao = wv
        nutritionDao = nd
        customExerciseDao = ce
        wikiLabDao = wd
        sessionTemplateDao = tp
        learnedResolutionDao = lr
        performanceRangeDao = pr
        performanceSnapshotDao = ps
    }

    private func createAllTables(db: KpknDatabaseInternal) throws {
        for sql in Self.initialCreateStatements {
            try db.execute(sql)
        }
    }

    private static let initialCreateStatements: [String] = [
        "CREATE TABLE IF NOT EXISTS `programs` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",

        "CREATE TABLE IF NOT EXISTS `workout_logs` (`id` TEXT NOT NULL, `programId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `date` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_workout_logs_programId_date` ON `workout_logs` (`programId`, `date`)",
        "CREATE INDEX IF NOT EXISTS `index_workout_logs_sessionId_date` ON `workout_logs` (`sessionId`, `date`)",

        "CREATE TABLE IF NOT EXISTS `settings` (`rowId` INTEGER NOT NULL DEFAULT 0, `data` TEXT NOT NULL, PRIMARY KEY(`rowId`))",

        "CREATE TABLE IF NOT EXISTS `wellbeing` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `sleep_logs` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `post_session_feedback` (`logId` TEXT NOT NULL, `date` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`logId`))",
        "CREATE TABLE IF NOT EXISTS `pending_questionnaire` (`rowId` INTEGER NOT NULL DEFAULT 0, `data` TEXT, PRIMARY KEY(`rowId`))",

        "CREATE TABLE IF NOT EXISTS `active_program` (`rowId` INTEGER NOT NULL DEFAULT 0, `data` TEXT, PRIMARY KEY(`rowId`))",
        "CREATE TABLE IF NOT EXISTS `ongoing_workout` (`rowId` INTEGER NOT NULL DEFAULT 0, `data` TEXT, PRIMARY KEY(`rowId`))",

        "CREATE TABLE IF NOT EXISTS `nutrition_logs` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `mealType` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_nutrition_logs_date` ON `nutrition_logs` (`date`)",

        "CREATE TABLE IF NOT EXISTS `nutrition_plans` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `nutrition_active_state` (`rowId` INTEGER NOT NULL DEFAULT 1, `activePlanId` TEXT, PRIMARY KEY(`rowId`))",
        "CREATE TABLE IF NOT EXISTS `nutrition_pantry` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `nutrition_templates` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `nutrition_custom_foods` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",

        "CREATE TABLE IF NOT EXISTS `muscle_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `bodyPart` TEXT, `coverImage` TEXT, `origin` TEXT, `insertion` TEXT, `mechanicalFunctions` TEXT, `mev` TEXT, `mav` TEXT, `mrv` TEXT, `recommendedExercises` TEXT, `relatedJoints` TEXT, `relatedTendons` TEXT, `importanceMovement` TEXT, `importanceHealth` TEXT, `aestheticImportance` TEXT, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `joints` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `description` TEXT NOT NULL, `bodyPart` TEXT, `musclesCrossing` TEXT, `tendonsRelated` TEXT, `movementPatterns` TEXT, `commonInjuries` TEXT, `protectiveExercises` TEXT, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `tendons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `muscleId` TEXT, `jointId` TEXT, `commonInjuries` TEXT, `protectiveExercises` TEXT, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `movement_patterns` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `forceTypes` TEXT, `chainTypes` TEXT, `primaryMuscles` TEXT, `primaryJoints` TEXT, `exampleExercises` TEXT, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `kinetic_chains` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `importance` TEXT NOT NULL, `muscles` TEXT, PRIMARY KEY(`id`))",

        "CREATE TABLE IF NOT EXISTS `workout_context_performance` (`contextKey` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`contextKey`))",

        "CREATE TABLE IF NOT EXISTS `workout_replacement_decisions` (`id` TEXT NOT NULL, `programId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",

        "CREATE TABLE IF NOT EXISTS `global_foods` (`foodId` TEXT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT, `calories` REAL NOT NULL DEFAULT 0.0, `protein` REAL NOT NULL DEFAULT 0.0, `carbs` REAL NOT NULL DEFAULT 0.0, `fats` REAL NOT NULL DEFAULT 0.0, `fiber` REAL NOT NULL DEFAULT 0.0, `sugar` REAL NOT NULL DEFAULT 0.0, `source` TEXT NOT NULL DEFAULT 'unknown', `normalizedName` TEXT NOT NULL DEFAULT '', `normalizedBrand` TEXT, `aliasesJson` TEXT NOT NULL DEFAULT '[]', `sodiumMg` REAL NOT NULL DEFAULT 0.0, `potassiumMg` REAL NOT NULL DEFAULT 0.0, `waterMl` REAL NOT NULL DEFAULT 0.0, `sourcePriority` INTEGER NOT NULL DEFAULT 50, `verifiedScore` REAL NOT NULL DEFAULT 0.5, `usageCount` INTEGER NOT NULL DEFAULT 0, `lastUsedAt` TEXT, PRIMARY KEY(`foodId`))",
        "CREATE INDEX IF NOT EXISTS `index_global_foods_name` ON `global_foods` (`name`)",
        "CREATE INDEX IF NOT EXISTS `index_global_foods_normalizedName` ON `global_foods` (`normalizedName`)",
        "CREATE INDEX IF NOT EXISTS `index_global_foods_normalizedBrand` ON `global_foods` (`normalizedBrand`)",

        "CREATE VIRTUAL TABLE IF NOT EXISTS `global_foods_fts` USING fts4(content=`global_foods`, `name`, `brand`)",
        "CREATE TRIGGER IF NOT EXISTS `global_foods_ai` AFTER INSERT ON `global_foods` BEGIN INSERT INTO `global_foods_fts`(`rowid`, `name`, `brand`) VALUES (new.`rowid`, new.`name`, new.`brand`); END",
        "CREATE TRIGGER IF NOT EXISTS `global_foods_ad` AFTER DELETE ON `global_foods` BEGIN INSERT INTO `global_foods_fts`(`global_foods_fts`, `rowid`, `name`, `brand`) VALUES ('delete', old.`rowid`, old.`name`, old.`brand`); END",
        "CREATE TRIGGER IF NOT EXISTS `global_foods_au` AFTER UPDATE ON `global_foods` BEGIN INSERT INTO `global_foods_fts`(`global_foods_fts`, `rowid`, `name`, `brand`) VALUES ('delete', old.`rowid`, old.`name`, old.`brand`); INSERT INTO `global_foods_fts`(`rowid`, `name`, `brand`) VALUES (new.`rowid`, new.`name`, new.`brand`); END",

        "CREATE TABLE IF NOT EXISTS `custom_exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_custom_exercises_name` ON `custom_exercises` (`name`)",

        "CREATE TABLE IF NOT EXISTS `workout_global_performance` (`globalKey` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`globalKey`))",
        "CREATE TABLE IF NOT EXISTS `workout_context_profiles` (`id` TEXT NOT NULL, `exerciseKey` TEXT NOT NULL, `lastUsedAt` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_workout_context_profiles_exerciseKey` ON `workout_context_profiles` (`exerciseKey`)",
        "CREATE INDEX IF NOT EXISTS `index_workout_context_profiles_lastUsedAt` ON `workout_context_profiles` (`lastUsedAt`)",

        "CREATE TABLE IF NOT EXISTS `session_templates` (`id` TEXT NOT NULL, `sourceType` TEXT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL DEFAULT 0, `isArchived` INTEGER NOT NULL DEFAULT 0, `createdAt` TEXT NOT NULL DEFAULT '', `data` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_session_templates_sourceType` ON `session_templates` (`sourceType`)",
        "CREATE INDEX IF NOT EXISTS `index_session_templates_sortOrder` ON `session_templates` (`sortOrder`)",
        "CREATE INDEX IF NOT EXISTS `index_session_templates_createdAt` ON `session_templates` (`createdAt`)",

        "CREATE TABLE IF NOT EXISTS `learned_resolutions` (`id` TEXT NOT NULL, `queryKey` TEXT NOT NULL, `foodId` TEXT NOT NULL, `portionGrams` REAL, `cookingMethod` TEXT, `count` INTEGER NOT NULL DEFAULT 1, `lastUsedAt` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL DEFAULT 0, `syncedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_learned_resolutions_queryKey` ON `learned_resolutions` (`queryKey`)",

        "CREATE TABLE IF NOT EXISTS `performance_range` (`contextKey` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`contextKey`))",
        "CREATE TABLE IF NOT EXISTS `performance_snapshot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `contextKey` TEXT NOT NULL, `data` TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_performance_snapshot_contextKey` ON `performance_snapshot` (`contextKey`)",

        "CREATE TABLE IF NOT EXISTS `auge_sleep_extended` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_auge_sleep_extended_date` ON `auge_sleep_extended` (`date`)",

        "CREATE TABLE IF NOT EXISTS `competition_records` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `eventDate` TEXT NOT NULL, `status` TEXT NOT NULL, `sportType` TEXT NOT NULL, `plannedSessionId` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_competition_records_eventDate` ON `competition_records` (`eventDate`)",
        "CREATE INDEX IF NOT EXISTS `index_competition_records_status` ON `competition_records` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_competition_records_sportType` ON `competition_records` (`sportType`)",
        "CREATE INDEX IF NOT EXISTS `index_competition_records_plannedSessionId` ON `competition_records` (`plannedSessionId`)",

        "CREATE TABLE IF NOT EXISTS `auge_adaptive_cache` (`rowId` INTEGER NOT NULL DEFAULT 1, `data` TEXT, PRIMARY KEY(`rowId`))",
    ]

    // MARK: Migrations

    fileprivate struct MigrationStep {
        let fromVersion: Int32
        let toVersion: Int32
        let migrate: (KpknDatabaseInternal) throws -> Void
    }

    fileprivate static let migrationSteps: [MigrationStep] = [
        MigrationStep(fromVersion: 1, toVersion: 2) { db in
            try db.execute("CREATE TABLE IF NOT EXISTS `nutrition_logs` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `mealType` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_nutrition_logs_date` ON `nutrition_logs` (`date`)")
            try db.execute("CREATE TABLE IF NOT EXISTS `nutrition_plans` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
            try db.execute("CREATE TABLE IF NOT EXISTS `nutrition_active_state` (`rowId` INTEGER NOT NULL DEFAULT 1, `activePlanId` TEXT, PRIMARY KEY(`rowId`))")
            try db.execute("CREATE TABLE IF NOT EXISTS `nutrition_pantry` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
            try db.execute("CREATE TABLE IF NOT EXISTS `nutrition_templates` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
            try db.execute("CREATE TABLE IF NOT EXISTS `nutrition_custom_foods` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
        },

        MigrationStep(fromVersion: 2, toVersion: 3) { db in
            try db.execute("CREATE TABLE IF NOT EXISTS `muscle_groups` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `bodyPart` TEXT, `coverImage` TEXT, `origin` TEXT, `insertion` TEXT, `mechanicalFunctions` TEXT, `mev` TEXT, `mav` TEXT, `mrv` TEXT, `recommendedExercises` TEXT, `relatedJoints` TEXT, `relatedTendons` TEXT, `importanceMovement` TEXT, `importanceHealth` TEXT, `aestheticImportance` TEXT, PRIMARY KEY(`id`))")
            try db.execute("CREATE TABLE IF NOT EXISTS `joints` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `description` TEXT NOT NULL, `bodyPart` TEXT, `musclesCrossing` TEXT, `tendonsRelated` TEXT, `movementPatterns` TEXT, `commonInjuries` TEXT, `protectiveExercises` TEXT, PRIMARY KEY(`id`))")
            try db.execute("CREATE TABLE IF NOT EXISTS `tendons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `muscleId` TEXT, `jointId` TEXT, `commonInjuries` TEXT, `protectiveExercises` TEXT, PRIMARY KEY(`id`))")
            try db.execute("CREATE TABLE IF NOT EXISTS `movement_patterns` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `forceTypes` TEXT, `chainTypes` TEXT, `primaryMuscles` TEXT, `primaryJoints` TEXT, `exampleExercises` TEXT, PRIMARY KEY(`id`))")
            try db.execute("CREATE TABLE IF NOT EXISTS `kinetic_chains` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `importance` TEXT NOT NULL, `muscles` TEXT, PRIMARY KEY(`id`))")
        },

        MigrationStep(fromVersion: 3, toVersion: 4) { db in
            try db.execute("CREATE TABLE IF NOT EXISTS `workout_context_performance` (`contextKey` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`contextKey`))")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_workout_context_performance_contextKey` ON `workout_context_performance` (`contextKey`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_workout_context_performance_updatedAt` ON `workout_context_performance` (`updatedAt`)")
            try db.execute("CREATE TABLE IF NOT EXISTS `workout_replacement_decisions` (`id` TEXT NOT NULL, `programId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_workout_replacement_decisions_programId` ON `workout_replacement_decisions` (`programId`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_workout_replacement_decisions_sessionId` ON `workout_replacement_decisions` (`sessionId`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_workout_replacement_decisions_createdAt` ON `workout_replacement_decisions` (`createdAt`)")
        },

        MigrationStep(fromVersion: 4, toVersion: 5) { db in
            try db.execute("CREATE TABLE IF NOT EXISTS `global_foods` (`fdcId` INTEGER NOT NULL, `name` TEXT NOT NULL, `brand` TEXT, `calories` REAL NOT NULL, `protein` REAL NOT NULL, `carbs` REAL NOT NULL, `fats` REAL NOT NULL, `fiber` REAL NOT NULL, `sugar` REAL NOT NULL, PRIMARY KEY(`fdcId`))")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_global_foods_name` ON `global_foods` (`name`)")
        },

        MigrationStep(fromVersion: 5, toVersion: 6) { db in
            try db.execute("DROP INDEX IF EXISTS `index_workout_logs_programId`")
            try db.execute("DROP INDEX IF EXISTS `index_workout_logs_sessionId`")
            try db.execute("DROP INDEX IF EXISTS `index_workout_logs_date`")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_workout_logs_programId_date` ON `workout_logs` (`programId`, `date`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_workout_logs_sessionId_date` ON `workout_logs` (`sessionId`, `date`)")

            try db.execute("DROP INDEX IF EXISTS `index_global_foods_name`")
            try db.execute("DROP TABLE IF EXISTS `global_foods`")
            try db.execute("CREATE TABLE `global_foods` (`foodId` TEXT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT, `calories` REAL NOT NULL DEFAULT 0.0, `protein` REAL NOT NULL DEFAULT 0.0, `carbs` REAL NOT NULL DEFAULT 0.0, `fats` REAL NOT NULL DEFAULT 0.0, `fiber` REAL NOT NULL DEFAULT 0.0, `sugar` REAL NOT NULL DEFAULT 0.0, `source` TEXT NOT NULL DEFAULT 'unknown', PRIMARY KEY(`foodId`))")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_global_foods_name` ON `global_foods` (`name`)")

            try db.execute("CREATE VIRTUAL TABLE IF NOT EXISTS `global_foods_fts` USING fts4(content=`global_foods`, `name`, `brand`)")
            try db.execute("CREATE TRIGGER IF NOT EXISTS `global_foods_ai` AFTER INSERT ON `global_foods` BEGIN INSERT INTO `global_foods_fts`(`rowid`, `name`, `brand`) VALUES (new.`rowid`, new.`name`, new.`brand`); END")
            try db.execute("CREATE TRIGGER IF NOT EXISTS `global_foods_ad` AFTER DELETE ON `global_foods` BEGIN INSERT INTO `global_foods_fts`(`global_foods_fts`, `rowid`, `name`, `brand`) VALUES ('delete', old.`rowid`, old.`name`, old.`brand`); END")
            try db.execute("CREATE TRIGGER IF NOT EXISTS `global_foods_au` AFTER UPDATE ON `global_foods` BEGIN INSERT INTO `global_foods_fts`(`global_foods_fts`, `rowid`, `name`, `brand`) VALUES ('delete', old.`rowid`, old.`name`, old.`brand`); INSERT INTO `global_foods_fts`(`rowid`, `name`, `brand`) VALUES (new.`rowid`, new.`name`, new.`brand`); END")
        },

        MigrationStep(fromVersion: 6, toVersion: 7) { db in
            try db.execute("CREATE TABLE IF NOT EXISTS `workout_global_performance` (`globalKey` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`globalKey`))")
            try db.execute("CREATE TABLE IF NOT EXISTS `workout_context_profiles` (`id` TEXT NOT NULL, `exerciseKey` TEXT NOT NULL, `lastUsedAt` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_workout_context_profiles_exerciseKey` ON `workout_context_profiles` (`exerciseKey`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_workout_context_profiles_lastUsedAt` ON `workout_context_profiles` (`lastUsedAt`)")
        },

        MigrationStep(fromVersion: 7, toVersion: 8) { db in
            try db.execute("CREATE TABLE IF NOT EXISTS `custom_exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `data` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_custom_exercises_name` ON `custom_exercises` (`name`)")
        },

        MigrationStep(fromVersion: 8, toVersion: 9) { db in
            try db.execute("DROP INDEX IF EXISTS `index_workout_context_performance_contextKey`")
            try db.execute("DROP INDEX IF EXISTS `index_workout_context_performance_updatedAt`")
        },

        MigrationStep(fromVersion: 9, toVersion: 10) { db in
            try db.execute("DROP INDEX IF EXISTS `index_workout_replacement_decisions_programId`")
            try db.execute("DROP INDEX IF EXISTS `index_workout_replacement_decisions_sessionId`")
            try db.execute("DROP INDEX IF EXISTS `index_workout_replacement_decisions_createdAt`")
        },

        MigrationStep(fromVersion: 10, toVersion: 11) { db in
            try db.execute("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `normalizedName` TEXT NOT NULL DEFAULT ''")
            try db.execute("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `normalizedBrand` TEXT")
            try db.execute("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `aliasesJson` TEXT NOT NULL DEFAULT '[]'")
            try db.execute("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `sourcePriority` INTEGER NOT NULL DEFAULT 50")
            try db.execute("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `verifiedScore` REAL NOT NULL DEFAULT 0.5")
            try db.execute("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `usageCount` INTEGER NOT NULL DEFAULT 0")
            try db.execute("ALTER TABLE `nutrition_custom_foods` ADD COLUMN `lastUsedAt` TEXT")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_nutrition_custom_foods_name` ON `nutrition_custom_foods` (`name`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_nutrition_custom_foods_normalizedName` ON `nutrition_custom_foods` (`normalizedName`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_nutrition_custom_foods_normalizedBrand` ON `nutrition_custom_foods` (`normalizedBrand`)")
            try db.execute("UPDATE `nutrition_custom_foods` SET `normalizedName` = lower(`name`) WHERE `normalizedName` = ''")
            try db.execute("UPDATE `nutrition_custom_foods` SET `normalizedBrand` = lower(`name`) WHERE `normalizedBrand` IS NULL")

            try db.execute("ALTER TABLE `global_foods` ADD COLUMN `normalizedName` TEXT NOT NULL DEFAULT ''")
            try db.execute("ALTER TABLE `global_foods` ADD COLUMN `normalizedBrand` TEXT")
            try db.execute("ALTER TABLE `global_foods` ADD COLUMN `aliasesJson` TEXT NOT NULL DEFAULT '[]'")
            try db.execute("ALTER TABLE `global_foods` ADD COLUMN `sodiumMg` REAL NOT NULL DEFAULT 0.0")
            try db.execute("ALTER TABLE `global_foods` ADD COLUMN `potassiumMg` REAL NOT NULL DEFAULT 0.0")
            try db.execute("ALTER TABLE `global_foods` ADD COLUMN `waterMl` REAL NOT NULL DEFAULT 0.0")
            try db.execute("ALTER TABLE `global_foods` ADD COLUMN `sourcePriority` INTEGER NOT NULL DEFAULT 50")
            try db.execute("ALTER TABLE `global_foods` ADD COLUMN `verifiedScore` REAL NOT NULL DEFAULT 0.5")
            try db.execute("ALTER TABLE `global_foods` ADD COLUMN `usageCount` INTEGER NOT NULL DEFAULT 0")
            try db.execute("ALTER TABLE `global_foods` ADD COLUMN `lastUsedAt` TEXT")

            try db.execute("CREATE INDEX IF NOT EXISTS `index_global_foods_normalizedName` ON `global_foods` (`normalizedName`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_global_foods_normalizedBrand` ON `global_foods` (`normalizedBrand`)")

            try db.execute("UPDATE `global_foods` SET `normalizedName` = lower(`name`) WHERE `normalizedName` = ''")
            try db.execute("UPDATE `global_foods` SET `normalizedBrand` = lower(`brand`) WHERE `normalizedBrand` IS NULL AND `brand` IS NOT NULL")
            try db.execute("UPDATE `global_foods` SET `sourcePriority` = CASE WHEN lower(`source`) LIKE '%off%' THEN 80 WHEN lower(`source`) LIKE '%usda%' THEN 70 ELSE 60 END")
            try db.execute("UPDATE `global_foods` SET `verifiedScore` = CASE WHEN lower(`source`) LIKE '%usda%' THEN 0.85 WHEN lower(`source`) LIKE '%off%' THEN 0.72 ELSE 0.60 END")
        },

        MigrationStep(fromVersion: 11, toVersion: 12) { db in
            try db.execute("CREATE TABLE IF NOT EXISTS `auge_sleep_extended` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_auge_sleep_extended_date` ON `auge_sleep_extended` (`date`)")
        },

        MigrationStep(fromVersion: 12, toVersion: 13) { db in
            try db.execute("CREATE TABLE IF NOT EXISTS `session_templates` (`id` TEXT NOT NULL, `sourceType` TEXT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL DEFAULT 0, `isArchived` INTEGER NOT NULL DEFAULT 0, `createdAt` TEXT NOT NULL DEFAULT '', `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_session_templates_sourceType` ON `session_templates` (`sourceType`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_session_templates_sortOrder` ON `session_templates` (`sortOrder`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_session_templates_createdAt` ON `session_templates` (`createdAt`)")
        },

        MigrationStep(fromVersion: 13, toVersion: 14) { db in
            try db.execute("CREATE TABLE IF NOT EXISTS `learned_resolutions` (`id` TEXT NOT NULL, `queryKey` TEXT NOT NULL, `foodId` TEXT NOT NULL, `portionGrams` REAL, `cookingMethod` TEXT, `count` INTEGER NOT NULL DEFAULT 1, `lastUsedAt` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL DEFAULT 0, `syncedAt` INTEGER, PRIMARY KEY(`id`))")
            try db.execute("CREATE UNIQUE INDEX IF NOT EXISTS `index_learned_resolutions_queryKey` ON `learned_resolutions` (`queryKey`)")
        },

        MigrationStep(fromVersion: 14, toVersion: 15) { db in
            try db.execute("CREATE TABLE IF NOT EXISTS `performance_range` (`contextKey` TEXT NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`contextKey`))")
            try db.execute("CREATE TABLE IF NOT EXISTS `performance_snapshot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `contextKey` TEXT NOT NULL, `data` TEXT NOT NULL)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_performance_snapshot_contextKey` ON `performance_snapshot` (`contextKey`)")
        },

        MigrationStep(fromVersion: 15, toVersion: 16) { _ in
        },

        MigrationStep(fromVersion: 16, toVersion: 17) { _ in
        },

        MigrationStep(fromVersion: 17, toVersion: 18) { db in
            try db.execute("CREATE TABLE IF NOT EXISTS `competition_records` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `eventDate` TEXT NOT NULL, `status` TEXT NOT NULL, `sportType` TEXT NOT NULL, `plannedSessionId` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, `data` TEXT NOT NULL, PRIMARY KEY(`id`))")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_competition_records_eventDate` ON `competition_records` (`eventDate`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_competition_records_status` ON `competition_records` (`status`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_competition_records_sportType` ON `competition_records` (`sportType`)")
            try db.execute("CREATE INDEX IF NOT EXISTS `index_competition_records_plannedSessionId` ON `competition_records` (`plannedSessionId`)")
        },

        MigrationStep(fromVersion: 18, toVersion: 19) { db in
            try db.execute("CREATE TABLE IF NOT EXISTS `auge_adaptive_cache` (`rowId` INTEGER NOT NULL DEFAULT 1, `data` TEXT, PRIMARY KEY(`rowId`))")
        },
        MigrationStep(fromVersion: 19, toVersion: 20) { db in
            try db.preserveTrainingOriginals()
        },
        MigrationStep(fromVersion: 20, toVersion: 21) { db in
            try db.retireDerivedTraining()
        },
    ]
}


// Isolated legacy preservation boundary. No model result is interpreted as a measurement.
private extension KpknDatabaseInternal {
    func preserveTrainingOriginals() throws {
        try execute("CREATE TABLE IF NOT EXISTS training_observations (sourceIdentity TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, date TEXT NOT NULL, payload TEXT NOT NULL, provenance TEXT NOT NULL)")
        let wellbeingFields: Set<String> = ["id","date","sleepQuality","stressLevel","doms","motivation","sleepHours","moodState","workIntensity","studyIntensity","notes","preWorkoutDiscomforts","source","capturedFields"]
        for (table, canonical) in [("wellbeing", "auge_wellbeing"), ("sleep_logs", "auge_sleep"), ("auge_sleep_extended", "auge_sleep_extended"), ("post_session_feedback", "auge_feedback")] {
            let id = canonical == "auge_feedback" ? "logId" : "id"
            var statement: OpaquePointer?
            guard sqlite3_prepare_v2(handle, "SELECT `\(id)`, date, data FROM `\(table)`", -1, &statement, nil) == SQLITE_OK else {
                throw NSError(domain: "TrainingOriginals", code: 1, userInfo: [NSLocalizedDescriptionKey: "No se puede leer la fuente original; se conserva la base sin purgar."])
            }
            defer { sqlite3_finalize(statement) }
            var status = sqlite3_step(statement)
            while status == SQLITE_ROW {
                guard let keyText = sqlite3_column_text(statement, 0), let dateText = sqlite3_column_text(statement, 1), let rawText = sqlite3_column_text(statement, 2) else {
                    throw NSError(domain: "TrainingOriginals", code: 2)
                }
                let key = String(cString: keyText), date = String(cString: dateText), raw = String(cString: rawText)
                guard let data = raw.data(using: .utf8), let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    throw NSError(domain: "TrainingOriginals", code: 3)
                }
                let original = canonical == "auge_wellbeing" ? object.filter { wellbeingFields.contains($0.key) } : object
                let encoded = try JSONSerialization.data(withJSONObject: original, options: [.sortedKeys])
                guard let payload = String(data: encoded, encoding: .utf8) else { throw NSError(domain: "TrainingOriginals", code: 4) }
                func quote(_ value: String) -> String { "'" + value.replacingOccurrences(of: "'", with: "''") + "'" }
                try execute("INSERT OR IGNORE INTO training_observations VALUES (\(quote(canonical + ":" + key)),\(quote(canonical.replacingOccurrences(of: "auge_", with: ""))),\(quote(date)),\(quote(payload)),'historical_original;original_scale;defaults_not_confirmed')")
                if canonical == "auge_wellbeing" {
                    let manualKeys: Set<String> = ["manualNeuralBattery","manualMuscularBattery","manualSpinalBattery","manualMuscleBatteries","manualMuscleOverridesV2","manualBatteryAnchorMs"]
                    var manual = object.filter { manualKeys.contains($0.key) && !($0.value is NSNull) }
                    if let overrides = manual["manualMuscleOverridesV2"] as? [String: [String: Any]] {
                        manual["manualMuscleOverridesV2"] = overrides.mapValues { $0.filter { $0.key != "automaticBatteryAtAnchor" } }
                    }
                    if !manual.isEmpty {
                        let bytes = try JSONSerialization.data(withJSONObject: manual, options: [.sortedKeys])
                        guard let declaration = String(data: bytes, encoding: .utf8) else { throw NSError(domain: "TrainingOriginals", code: 6) }
                        try execute("INSERT OR IGNORE INTO training_observations VALUES (\(quote(canonical + ":" + key + ":manual")),'historical_self_report',\(quote(date)),\(quote(declaration)),'legacy_0_100_self_report;not_neurological_measurement;not_comparable')")
                    }
                }
                status = sqlite3_step(statement)
            }
            guard status == SQLITE_DONE else { throw NSError(domain: "TrainingOriginals", code: 5) }
        }
    }
}

// Final purge follows original preservation in the same SQLite transaction.
private extension KpknDatabaseInternal {
    func retireDerivedTraining() throws {
        func quote(_ value: String) -> String { "'" + value.replacingOccurrences(of: "'", with: "''") + "'" }
        func preserve(_ identity: String, _ kind: String, _ original: [String: Any]) throws {
            guard !original.isEmpty else { return }
            let bytes = try JSONSerialization.data(withJSONObject: original, options: [.sortedKeys])
            guard let payload = String(data: bytes, encoding: .utf8) else { throw NSError(domain: "TrainingOriginals", code: 10) }
            try execute("INSERT INTO training_observations VALUES (\(quote(identity)),\(quote(kind)),'',\(quote(payload)),'historical_original;original_scale;not_comparable')")
        }
        let settingsDerived: Set<String> = ["augePredictionBias","initialRecoveryEvidence","augeEnableNutritionTracking","augeEnableSleepTracking","augeRecoverySensitivity","augeFatigueSensitivity","augeReadinessThreshold","augeAutoDeload","augeShowAlertsInSession"]
        let logDerived: Set<String> = ["sessionStressScore","muscularImpactV2","ringStartSnapshot","postSessionPreview"]
        let setDerived: Set<String> = ["batteryDrain","cnsDrain","spinalDrain","muscularDrain","readinessScore","spinalScore"]
        let overrides: Set<String> = ["readinessNeuralOverride","readinessMuscularOverride","readinessSpinalOverride","readinessMuscleOverrides"]
        let ongoingDerived = overrides.union(["lastDrainOverlay","sessionStressScore","postSessionPreview","exerciseReadinessMap","patternReadiness","currentAutoRegulation"])
        func sets(_ map: [String: [String: Any]]) -> [String: [String: Any]] { map.mapValues { $0.filter { !setDerived.contains($0.key) } } }
        for (table, key) in [("settings","rowId"),("workout_logs","id"),("ongoing_workout","rowId")] {
            var statement: OpaquePointer?
            guard sqlite3_prepare_v2(handle,"SELECT \(key),data FROM \(table) WHERE data IS NOT NULL",-1,&statement,nil) == SQLITE_OK else { throw NSError(domain:"TrainingOriginals",code:11) }
            defer { sqlite3_finalize(statement) }
            var status = sqlite3_step(statement)
            while status == SQLITE_ROW {
                guard let idText = sqlite3_column_text(statement,0), let raw = sqlite3_column_text(statement,1),
                    let bytes = String(cString:raw).data(using:.utf8),
                    var object = try JSONSerialization.jsonObject(with:bytes) as? [String:Any] else { throw NSError(domain:"TrainingOriginals",code:12) }
                let identity = String(cString:idText)
                if table == "settings" {
                    if let evidence = object["initialRecoveryEvidence"] as? [String:Any] {
                        let keys: Set<String> = ["capturedAtMs","coveredFromMs","coveredToMs","sessions","activityType","intensity","zones","sensations","sourceId","muscleScope","axialExposure","activityTypeState"]
                        try preserve("settings:\(identity):initial_declarations","historical_onboarding",evidence.filter { keys.contains($0.key) })
                    }
                    object = object.filter { !settingsDerived.contains($0.key) }
                    if let algorithm = object["algorithmSettings"] as? [String:Any] { object["algorithmSettings"] = algorithm.filter { !settingsDerived.contains($0.key) && $0.key != "failureFatigueFactor" } }
                } else if table == "workout_logs" {
                    object = object.filter { !logDerived.contains($0.key) }
                    if let energy=object["energySummary"] as? [String:Any], (energy["methodVersion"] as? String ?? "auge-energy-v1").hasPrefix("auge-energy") { object.removeValue(forKey:"energySummary") }
                    if let exercises = object["completedExercises"] as? [[String:Any]] {
                        object["completedExercises"] = exercises.map { exercise -> [String:Any] in
                            var e = exercise
                            e.removeValue(forKey:"totalSpinalScore")
                            if let values = e["sets"] as? [[String:Any]] { e["sets"] = values.map { $0.filter { !setDerived.contains($0.key) } } }
                            return e
                        }
                    }
                } else {
                    let declarations = object.filter { overrides.contains($0.key) && !($0.value is NSNull) }
                    if !declarations.isEmpty { try preserve("ongoing:\(identity):\(object["startTime"] ?? "unknown")","historical_session_context",declarations.merging(object.filter { ["programId","startTime"].contains($0.key) }) { a,_ in a }) }
                    object = object.filter { !ongoingDerived.contains($0.key) }
                    if let completed = object["completedSets"] as? [String:[String:Any]] { object["completedSets"] = sets(completed) }
                    if let undo = object["godModeUndoStack"] as? [[String:Any]] { object["godModeUndoStack"] = undo.map { snapshot -> [String:Any] in
                        var value=snapshot
                        if let completed=value["completedSets"] as? [String:[String:Any]] { value["completedSets"]=sets(completed) }
                        return value
                    } }
                }
                let encoded = try JSONSerialization.data(withJSONObject:object,options:[.sortedKeys])
                guard let text=String(data:encoded,encoding:.utf8) else { throw NSError(domain:"TrainingOriginals",code:13) }
                try execute("UPDATE \(table) SET data=\(quote(text)) WHERE \(key)=\(quote(identity))")
                status=sqlite3_step(statement)
            }
            guard status == SQLITE_DONE else { throw NSError(domain:"TrainingOriginals",code:14) }
        }
        for table in ["wellbeing","sleep_logs","post_session_feedback","auge_sleep_extended","auge_adaptive_cache","pending_questionnaire"] { try execute("DROP TABLE IF EXISTS \(table)") }
    }
}
