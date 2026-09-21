package com.example.kpkn.data.settings

import android.content.Context
import com.example.kpkn.data.db.BackupTableManifest
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.LearnedResolutionEntity
import com.example.kpkn.data.db.SetupCommitReceiptEntity
import com.example.kpkn.data.db.SetupDraftEntity
import com.example.kpkn.data.db.NutritionActiveStateEntity
import com.example.kpkn.data.db.NutritionCalibrationProfileEntity
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.db.toCompetitionRecord
import com.example.kpkn.data.db.toContextPerformanceStateV2
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toExerciseReplacementDecisionV2
import com.example.kpkn.data.db.toExtendedEntity
import com.example.kpkn.data.db.toGlobalPerformanceStateV3
import com.example.kpkn.data.db.toMealTemplate
import com.example.kpkn.data.db.toPantryItem
import com.example.kpkn.data.db.toPerformanceRangeData
import com.example.kpkn.data.db.toPerformanceSnapshotData
import com.example.kpkn.data.db.toWorkoutContextProfile
import com.example.kpkn.data.db.toWorkoutMedia
import com.example.kpkn.data.db.toWorkoutTag
import com.example.kpkn.data.models.ApiKeys
import com.example.kpkn.data.models.ApiProvider
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.ContextPerformanceStateV2
import com.example.kpkn.data.models.ExerciseReplacementDecisionV2
import com.example.kpkn.data.models.GlobalPerformanceStateV3
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.data.models.NutritionCalibrationProfile
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.data.db.PerformanceRangeData
import com.example.kpkn.data.db.PerformanceSnapshotData
import com.example.kpkn.data.prepopulateWikiLabAssets
import com.example.kpkn.data.profile.ProfilePhotoStore
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.domain.body.validateBodyValue
import com.example.kpkn.data.food.FoodImporter
import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object SettingsJsonBackup {

    const val EXPORT_SCHEMA_VERSION = BackupTableManifest.JSON_BACKUP_SCHEMA_VERSION

    suspend fun buildPayload(
        context: Context,
        settings: Settings,
        profilePhotoJpegBase64: String?,
        programs: List<com.example.kpkn.data.models.Program>,
        workoutLogs: List<com.example.kpkn.data.models.WorkoutLog>,
        activeProgramState: com.example.kpkn.data.models.ActiveProgramState?,
        ongoingWorkout: com.example.kpkn.data.models.OngoingWorkoutState?,
        nutritionLogs: List<com.example.kpkn.data.models.NutritionLog>,
        nutritionPlans: List<com.example.kpkn.data.models.NutritionPlan>,
        activeNutritionPlanId: String?,
        pantryItems: List<com.example.kpkn.data.models.PantryItem>,
        mealTemplates: List<com.example.kpkn.data.models.MealTemplate>,
        customFoods: List<com.example.kpkn.data.models.FoodItem>,
        learnedResolutions: List<LearnedResolutionBackup>,
        foodCatalogMeta: NutritionRepository.FoodCatalogMeta?,
        bodyObservations: List<com.example.kpkn.data.models.BodyObservation>,
        bodyGoals: List<com.example.kpkn.data.models.BodyGoal>,
        measurementSchedule: MeasurementSchedule?,
        calibrationProfile: NutritionCalibrationProfile?,
        dailyGoalSnapshots: List<com.example.kpkn.data.models.DailyGoalSnapshot>,
        wellbeingLogs: List<com.example.kpkn.data.models.DailyWellbeingLog>,
        sleepLogs: List<com.example.kpkn.data.models.SleepLog>,
        sleepLogsExtended: List<com.example.kpkn.data.models.SleepLogExtended>,
        postSessionFeedback: List<com.example.kpkn.data.models.PostSessionFeedback>,
        adaptiveCache: com.example.kpkn.data.models.AugeAdaptiveCache?,
        sessionTemplates: List<SessionTemplate>,
        customExercises: List<com.example.kpkn.data.models.ExerciseMuscleInfo>,
        db: KpknDatabase,
    ): SettingsExportPayload {
        val v2 = db.workoutV2Dao()
        val competition = db.competitionRecordDao().getAll().map { it.toCompetitionRecord() }
        val performanceRanges = db.performanceRangeDao().getAll().map { it.toPerformanceRangeData() }
        val performanceSnapshots = db.performanceSnapshotDao().getRecent(10_000).map { it.toPerformanceSnapshotData() }
        val workoutMedia = db.workoutMediaDao().getAll().map { it.toWorkoutMedia() }
        val setupDrafts = db.setupDraftDao().getAllDrafts().map {
            SetupDraftBackup(
                draftId = it.draftId,
                payloadJson = it.payloadJson,
                revision = it.revision,
                catalogRevision = it.catalogRevision,
                updatedAtEpochMs = it.updatedAtEpochMs,
            )
        }
        val setupCommitReceipts = db.setupCommitReceiptDao().getAll().map {
            SetupCommitReceiptBackup(
                commitId = it.commitId,
                draftId = it.draftId,
                programId = it.programId,
                nutritionPlanId = it.nutritionPlanId,
                bodyGoalIdsJson = it.bodyGoalIdsJson,
                committedAtEpochMs = it.committedAtEpochMs,
            )
        }
        return SettingsExportPayload(
            schemaVersion = EXPORT_SCHEMA_VERSION,
            exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            settings = settings.copy(
                apiKeys = ApiKeys(),
                profilePicture = ProfilePhotoStore.STORAGE_TOKEN.takeIf { profilePhotoJpegBase64 != null },
            ),
            profilePhotoJpegBase64 = profilePhotoJpegBase64,
            programs = programs,
            workoutLogs = workoutLogs,
            activeProgramState = activeProgramState,
            ongoingWorkout = ongoingWorkout,
            nutritionLogs = nutritionLogs,
            nutritionPlans = nutritionPlans,
            activeNutritionPlanId = activeNutritionPlanId,
            pantryItems = pantryItems,
            mealTemplates = mealTemplates,
            customFoods = customFoods,
            learnedResolutions = learnedResolutions,
            foodCatalogMeta = foodCatalogMeta,
            bodyObservations = bodyObservations,
            bodyGoals = bodyGoals,
            measurementSchedule = measurementSchedule,
            calibrationProfile = calibrationProfile,
            dailyGoalSnapshots = dailyGoalSnapshots,
            wellbeingLogs = wellbeingLogs,
            sleepLogs = sleepLogs,
            sleepLogsExtended = sleepLogsExtended,
            postSessionFeedback = postSessionFeedback,
            adaptiveCache = adaptiveCache,
            sessionTemplates = sessionTemplates,
            customExercises = customExercises,
            competitionRecords = competition,
            contextPerformance = v2.getAllContextPerformance().map { it.toContextPerformanceStateV2() },
            globalPerformance = v2.getAllGlobalPerformance().map { it.toGlobalPerformanceStateV3() },
            contextProfiles = v2.getAllContextProfiles().map { it.toWorkoutContextProfile() },
            workoutTags = v2.getAllWorkoutTags().map { it.toWorkoutTag() },
            replacementDecisions = v2.getAllReplacementDecisions().map { it.toExerciseReplacementDecisionV2() },
            performanceRanges = performanceRanges,
            performanceSnapshots = performanceSnapshots,
            workoutMedia = workoutMedia,
            includesCompetitionSection = true,
            includesWorkoutV2Section = true,
            includesPerformanceSection = true,
            includesWorkoutMediaSection = true,
            includesCalibrationSection = true,
            includesFoodCatalogMetaSection = true,
            includesSetupSection = true,
            setupDrafts = setupDrafts,
            setupCommitReceipts = setupCommitReceipts,
        )
    }

    suspend fun importPayload(
        context: Context,
        payload: SettingsExportPayload,
        db: KpknDatabase,
        nutritionRepository: NutritionRepository,
        onMeasurementSchedule: suspend (MeasurementSchedule) -> Unit,
    ): String? {
        require(payload.schemaVersion in 1..EXPORT_SCHEMA_VERSION) {
            "Formato de exportación no compatible: ${payload.schemaVersion}"
        }
        val v5 = payload.schemaVersion >= 5
        val hasSetup = payload.schemaVersion >= 6 && payload.includesSetupSection
        val tablesToClear = BackupTableManifest.tablesToClearForImport(
            schemaVersion = payload.schemaVersion,
            hasCompetitionSection = v5 && payload.includesCompetitionSection,
            hasWorkoutV2Section = v5 && payload.includesWorkoutV2Section,
            hasPerformanceSection = v5 && payload.includesPerformanceSection,
            hasWorkoutMediaSection = v5 && payload.includesWorkoutMediaSection,
            hasCalibrationSection = v5 && payload.includesCalibrationSection,
            hasSetupSection = hasSetup,
        )
        val settingsProfileToken = when {
            payload.profilePhotoJpegBase64 != null -> ProfilePhotoStore.STORAGE_TOKEN
            payload.settings.profilePicture.isNullOrBlank() -> null
            else -> payload.settings.profilePicture
        }
        db.withTransaction {
            val sqlite = db.openHelper.writableDatabase
            BackupTableManifest.deleteRows(sqlite, tablesToClear)
            db.settingsDao().upsert(
                payload.settings.copy(
                    apiProvider = ApiProvider.LOCAL,
                    apiKeys = ApiKeys(),
                    profilePicture = settingsProfileToken,
                ).toEntity(),
            )
            payload.programs.forEach { db.programDao().upsert(it.toEntity()) }
            payload.workoutLogs.forEach { db.workoutLogDao().insert(it.toEntity()) }
            payload.activeProgramState?.let { db.stateDao().upsertActiveProgram(it.toEntity()) }
            payload.ongoingWorkout?.let { db.stateDao().upsertOngoingWorkout(it.toEntity()) }
            payload.nutritionLogs.forEach { db.nutritionDao().upsertLog(it.toEntity()) }
            payload.nutritionPlans.forEach { db.nutritionDao().upsertPlan(it.toEntity()) }
            payload.activeNutritionPlanId?.let {
                db.nutritionDao().upsertActiveState(NutritionActiveStateEntity(activePlanId = it))
            }
            payload.pantryItems.forEach { db.nutritionDao().upsertPantryItem(it.toEntity()) }
            payload.mealTemplates.forEach { db.nutritionDao().upsertTemplate(it.toEntity()) }
            payload.customFoods.forEach { db.nutritionDao().upsertCustomFood(it.toEntity()) }
            payload.learnedResolutions.forEach { db.learnedResolutionDao().upsert(it.toEntity()) }
            payload.bodyObservations
                .filter { validateBodyValue(it.metric, it.valueSi).valid }
                .forEach { db.bodyProgressDao().upsertObservation(it.toEntity()) }
            payload.bodyGoals
                .filter { it.targetValueSi.isFinite() }
                .forEach { db.bodyProgressDao().upsertGoal(it.toEntity()) }
            payload.dailyGoalSnapshots.forEach {
                db.nutritionDao().insertDailyGoalSnapshot(it.toEntity())
            }
            payload.sessionTemplates.forEach { db.sessionTemplateDao().upsert(it.toEntity()) }
            payload.customExercises.forEach { db.customExerciseDao().upsert(it.toEntity()) }

            if (hasSetup) {
                payload.setupDrafts.orEmpty().forEach { db.setupDraftDao().upsertDraft(it.toEntity()) }
                payload.setupCommitReceipts.orEmpty().forEach { db.setupCommitReceiptDao().insert(it.toEntity()) }
            }

            payload.wellbeingLogs.forEach { db.augeDao().upsertWellbeing(it.toEntity()) }
            payload.sleepLogsExtended.forEach { db.augeDao().upsertSleepLogExtended(it.toExtendedEntity()) }
            val extendedIds = payload.sleepLogsExtended.map { it.id }.toSet()
            payload.sleepLogs.filter { it.id !in extendedIds }.forEach { db.augeDao().upsertSleepLog(it.toEntity()) }
            payload.postSessionFeedback.forEach { db.augeDao().upsertFeedback(it.toEntity()) }
            payload.adaptiveCache?.let { db.augeDao().upsertAdaptiveCache(it.toEntity()) }

            if (v5 && payload.includesCalibrationSection) {
                if (payload.calibrationProfile != null) {
                    db.nutritionDao().upsertCalibrationProfile(
                        NutritionCalibrationProfileEntity(data = dbJson.encodeToString(payload.calibrationProfile)),
                    )
                }
            } else if (!v5 && payload.calibrationProfile != null) {
                db.nutritionDao().upsertCalibrationProfile(
                    NutritionCalibrationProfileEntity(data = dbJson.encodeToString(payload.calibrationProfile)),
                )
            }

            if (v5 && payload.includesCompetitionSection) {
                payload.competitionRecords.orEmpty().forEach { db.competitionRecordDao().upsert(it.toEntity()) }
            }
            if (v5 && payload.includesWorkoutV2Section) {
                val v2 = db.workoutV2Dao()
                payload.contextPerformance.orEmpty().forEach { v2.upsertContextPerformance(it.toEntity()) }
                payload.globalPerformance.orEmpty().forEach { v2.upsertGlobalPerformance(it.toEntity()) }
                payload.contextProfiles.orEmpty().forEach { v2.upsertContextProfile(it.toEntity()) }
                payload.workoutTags.orEmpty().forEach { v2.upsertWorkoutTag(it.toEntity()) }
                payload.replacementDecisions.orEmpty().forEach { v2.upsertReplacementDecision(it.toEntity()) }
            }
            if (v5 && payload.includesPerformanceSection) {
                payload.performanceRanges.orEmpty().forEach { db.performanceRangeDao().upsert(it.toEntity()) }
                payload.performanceSnapshots.orEmpty().forEach { db.performanceSnapshotDao().upsert(it.toEntity()) }
            }
            if (v5 && payload.includesWorkoutMediaSection) {
                payload.workoutMedia.orEmpty().forEach { db.workoutMediaDao().upsert(it.toEntity()) }
            }
        }

        when {
            payload.profilePhotoJpegBase64 != null -> {
                ProfilePhotoStore.saveBase64(context.applicationContext, payload.profilePhotoJpegBase64)
            }
            payload.profilePhotoJpegBase64 == null && payload.settings.profilePicture == null -> {
                ProfilePhotoStore.delete(context.applicationContext)
            }
        }

        val schedule = payload.measurementSchedule ?: MeasurementSchedule()
        onMeasurementSchedule(schedule)

        if (v5 && payload.includesFoodCatalogMetaSection) {
            if (payload.foodCatalogMeta != null) {
                nutritionRepository.restoreFoodCatalogMeta(payload.foodCatalogMeta)
            }
        } else if (!v5) {
            if (payload.foodCatalogMeta != null) {
                nutritionRepository.restoreFoodCatalogMeta(payload.foodCatalogMeta)
            }
        }

        ensureRegenerableAssetsIfEmpty(context, db)
        return settingsProfileToken
    }

    suspend fun ensureRegenerableAssetsIfEmpty(context: Context, db: KpknDatabase) {
        val wikiCount = db.wikiLabDao().getMuscleCount()
        if (wikiCount == 0) {
            prepopulateWikiLabAssets(context, db)
        }
        val globalCount = db.nutritionDao().getGlobalFoodCount()
        if (globalCount == 0) {
            FoodImporter.importIfNeeded(
                db = db,
                context = context,
                alreadyImported = false,
                existingMeta = null,
                onMetaUpdated = {},
            )
        }
    }
}

@Serializable
data class SettingsExportPayload(
    val schemaVersion: Int = 1,
    val exportedAt: String,
    val settings: Settings,
    val profilePhotoJpegBase64: String? = null,
    val programs: List<com.example.kpkn.data.models.Program>,
    val workoutLogs: List<com.example.kpkn.data.models.WorkoutLog>,
    val activeProgramState: com.example.kpkn.data.models.ActiveProgramState?,
    val ongoingWorkout: com.example.kpkn.data.models.OngoingWorkoutState?,
    val nutritionLogs: List<com.example.kpkn.data.models.NutritionLog>,
    val nutritionPlans: List<com.example.kpkn.data.models.NutritionPlan>,
    val activeNutritionPlanId: String?,
    val pantryItems: List<com.example.kpkn.data.models.PantryItem>,
    val mealTemplates: List<com.example.kpkn.data.models.MealTemplate>,
    val customFoods: List<com.example.kpkn.data.models.FoodItem> = emptyList(),
    val learnedResolutions: List<LearnedResolutionBackup> = emptyList(),
    val foodCatalogMeta: NutritionRepository.FoodCatalogMeta? = null,
    val bodyObservations: List<com.example.kpkn.data.models.BodyObservation> = emptyList(),
    val bodyGoals: List<com.example.kpkn.data.models.BodyGoal> = emptyList(),
    val measurementSchedule: MeasurementSchedule? = null,
    val calibrationProfile: NutritionCalibrationProfile? = null,
    val dailyGoalSnapshots: List<com.example.kpkn.data.models.DailyGoalSnapshot> = emptyList(),
    val wellbeingLogs: List<com.example.kpkn.data.models.DailyWellbeingLog>,
    val sleepLogs: List<com.example.kpkn.data.models.SleepLog>,
    val sleepLogsExtended: List<com.example.kpkn.data.models.SleepLogExtended> = emptyList(),
    val postSessionFeedback: List<com.example.kpkn.data.models.PostSessionFeedback>,
    val adaptiveCache: com.example.kpkn.data.models.AugeAdaptiveCache? = null,
    val sessionTemplates: List<SessionTemplate> = emptyList(),
    val customExercises: List<com.example.kpkn.data.models.ExerciseMuscleInfo> = emptyList(),
    val competitionRecords: List<CompetitionRecord>? = null,
    val contextPerformance: List<ContextPerformanceStateV2>? = null,
    val globalPerformance: List<GlobalPerformanceStateV3>? = null,
    val contextProfiles: List<WorkoutContextProfile>? = null,
    val workoutTags: List<WorkoutTag>? = null,
    val replacementDecisions: List<ExerciseReplacementDecisionV2>? = null,
    val performanceRanges: List<PerformanceRangeData>? = null,
    val performanceSnapshots: List<PerformanceSnapshotData>? = null,
    val workoutMedia: List<WorkoutMedia>? = null,
    val includesCompetitionSection: Boolean = false,
    val includesWorkoutV2Section: Boolean = false,
    val includesPerformanceSection: Boolean = false,
    val includesWorkoutMediaSection: Boolean = false,
    val includesCalibrationSection: Boolean = false,
    val includesFoodCatalogMetaSection: Boolean = false,
    val includesSetupSection: Boolean = false,
    val setupDrafts: List<SetupDraftBackup> = emptyList(),
    val setupCommitReceipts: List<SetupCommitReceiptBackup> = emptyList(),
)

@Serializable
data class SetupDraftBackup(
    val draftId: String,
    val payloadJson: String,
    val revision: Long,
    val catalogRevision: String? = null,
    val updatedAtEpochMs: Long,
) {
    fun toEntity() = SetupDraftEntity(draftId, payloadJson, revision, catalogRevision, updatedAtEpochMs)
}

@Serializable
data class SetupCommitReceiptBackup(
    val commitId: String,
    val draftId: String? = null,
    val programId: String? = null,
    val nutritionPlanId: String? = null,
    val bodyGoalIdsJson: String = "[]",
    val committedAtEpochMs: Long,
) {
    fun toEntity() = SetupCommitReceiptEntity(commitId, draftId, programId, nutritionPlanId, bodyGoalIdsJson, committedAtEpochMs)
}

@Serializable
data class LearnedResolutionBackup(
    val id: String,
    val queryKey: String,
    val foodId: String,
    val portionGrams: Double? = null,
    val cookingMethod: String? = null,
    val count: Int = 1,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = 0L,
    val syncedAt: Long? = null,
    val weightBasis: String? = null,
    val portionMinGrams: Double? = null,
    val portionMaxGrams: Double? = null,
    val preparation: String? = null,
    val oilProfile: String? = null,
    val confidence: Double = 1.0,
    val lastConfirmedAt: Long = 0L,
) {
    fun toEntity() = LearnedResolutionEntity(
        id = id,
        queryKey = queryKey,
        foodId = foodId,
        portionGrams = portionGrams,
        cookingMethod = cookingMethod,
        count = count,
        lastUsedAt = lastUsedAt,
        createdAt = createdAt,
        syncedAt = syncedAt,
        weightBasis = weightBasis,
        portionMinGrams = portionMinGrams,
        portionMaxGrams = portionMaxGrams,
        preparation = preparation,
        oilProfile = oilProfile,
        confidence = confidence,
        lastConfirmedAt = lastConfirmedAt,
    )
}
