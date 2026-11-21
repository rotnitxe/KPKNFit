package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.compatibility.LegacyTrainingBackup
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.SharedExerciseLoadReference
import com.example.kpkn.data.models.SharedLoadReferenceKind
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.SharedExerciseLoadReferenceRepository
import com.example.kpkn.data.repository.TrainingObservationRepository
import com.example.kpkn.data.settings.SettingsExportPayload
import com.example.kpkn.data.settings.SettingsJsonBackup
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class Bloque2PersistenceHardeningTest {
    @get:Rule
    val roomRule = RoomTestRule()

    private fun shortPathContext(block: (Context, File) -> Unit) {
        KpknDatabase.closeInstance()
        BodyProgressRepository.closeInstance()
        SharedExerciseLoadReferenceRepository.closeInstance()
        val root = Files.createTempDirectory("b2-hardening-").toFile()
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
            override fun getDatabasePath(name: String): File =
                (if (File(name).isAbsolute) File(name) else File(root, "db/$name")).apply { parentFile!!.mkdirs() }

            override fun deleteDatabase(name: String): Boolean =
                SQLiteDatabase.deleteDatabase(getDatabasePath(name))

            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY)

            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
                handler: DatabaseErrorHandler?,
            ): SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY, handler)
        }
        try {
            block(context, root)
        } finally {
            SharedExerciseLoadReferenceRepository.closeInstance()
            BodyProgressRepository.closeInstance()
            KpknDatabase.closeInstance()
            root.deleteRecursively()
        }
    }

    private fun nutritionRepo(context: Context, db: KpknDatabase): NutritionRepository {
        val ctor = NutritionRepository::class.java.getDeclaredConstructor(
            Context::class.java,
            KpknDatabase::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        return ctor.newInstance(context, db, false)
    }

    private suspend fun cancelNutritionRepoJob(repo: NutritionRepository) {
        val field = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
        (field.get(repo) as Job).cancelAndJoin()
    }

    @Test
    fun `B2-01 full JSON v9 export and import works through LegacyTrainingBackup`() = shortPathContext { context, _ ->
        runBlocking {
            val exportDb = KpknDatabase.createInMemory(context)
            val importDb = KpknDatabase.createInMemory(context)
            val repo = nutritionRepo(context, importDb)
            try {
                importDb.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity(foodId = "local", name = "Avena")))
                importDb.openHelper.writableDatabase.execSQL("INSERT INTO muscle_groups (id, name, description) VALUES ('m1', 'Pecho', '')")
                val payload = SettingsJsonBackup.buildPayload(
                    context = context,
                    settings = Settings(),
                    profilePhotoJpegBase64 = null,
                    programs = listOf(Program(id = "prog-v9", name = "Fuerza")),
                    workoutLogs = emptyList(),
                    activeProgramState = null,
                    ongoingWorkout = null,
                    nutritionLogs = emptyList(),
                    nutritionPlans = emptyList(),
                    activeNutritionPlanId = null,
                    pantryItems = emptyList(),
                    mealTemplates = emptyList(),
                    customFoods = emptyList(),
                    learnedResolutions = emptyList(),
                    foodCatalogMeta = null,
                    bodyObservations = emptyList(),
                    bodyGoals = emptyList(),
                    measurementSchedule = null,
                    calibrationProfile = null,
                    dailyGoalSnapshots = emptyList(),
                    wellbeingLogs = emptyList(),
                    sleepLogs = emptyList(),
                    sleepLogsExtended = emptyList(),
                    postSessionFeedback = emptyList(),
                    sessionTemplates = emptyList(),
                    customExercises = emptyList(),
                    db = exportDb,
                )
                assertEquals(9, payload.schemaVersion)
                val exported = LegacyTrainingBackup.export(dbJson.encodeToString(payload))
                val normalized = LegacyTrainingBackup.normalize(exported)
                val decoded = dbJson.decodeFromString<SettingsExportPayload>(normalized)
                SettingsJsonBackup.importPayload(context, decoded, importDb, repo) {}
                assertEquals(listOf("prog-v9"), importDb.programDao().getAll().map { it.id })
            } finally {
                cancelNutritionRepoJob(repo)
                exportDb.close()
                importDb.close()
            }
        }
    }

    @Test
    fun `B2-03 MIGRATION_14_15 matches schema 38 performance_snapshot without orphan index`() = shortPathContext { context, _ ->
        val dbFile = context.getDatabasePath("mig14.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        KpknDatabase.MIGRATION_14_15.migrate(db)
                    }

                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            val cols = db.query("PRAGMA table_info(`performance_snapshot`)").use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(Triple(c.getString(1), c.getString(2), c.getInt(3)))
                    }
                }
            }
            val idCol = cols.single { it.first == "id" }
            assertEquals("INTEGER", idCol.second)
            assertEquals(1, idCol.third)
            val indices = db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='performance_snapshot'").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
            assertTrue("Expected no undeclared indices on performance_snapshot, found $indices", indices.isEmpty())
        } finally {
            helper.close()
        }
    }

    @Test
    fun `B2-03 RETIRE_DERIVED_TRAINING_31_32 handles null and empty ongoing_workout data`() = shortPathContext { context, _ ->
        val dbFile = context.getDatabasePath("retire31.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(31) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS settings (rowId INTEGER NOT NULL PRIMARY KEY, data TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS workout_logs (id TEXT NOT NULL PRIMARY KEY, programId TEXT NOT NULL, sessionId TEXT NOT NULL, date TEXT NOT NULL, data TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS ongoing_workout (rowId INTEGER NOT NULL PRIMARY KEY, data TEXT)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS setup_drafts (draftId TEXT NOT NULL PRIMARY KEY, payloadJson TEXT NOT NULL, revision INTEGER NOT NULL, catalogRevision TEXT, updatedAtEpochMs INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS training_observations (sourceIdentity TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, date TEXT NOT NULL, payload TEXT NOT NULL, provenance TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS auge_wellbeing (id TEXT PRIMARY KEY, date TEXT, data TEXT)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS auge_sleep (id TEXT PRIMARY KEY, date TEXT, data TEXT)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS auge_sleep_extended (id TEXT PRIMARY KEY, date TEXT, data TEXT)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS auge_feedback (logId TEXT PRIMARY KEY, date TEXT, data TEXT)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS auge_adaptive_cache (rowId INTEGER PRIMARY KEY, data TEXT)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS auge_pending (rowId INTEGER PRIMARY KEY, data TEXT)")
                        db.execSQL("INSERT INTO ongoing_workout VALUES (1, NULL)")
                        db.execSQL("INSERT INTO setup_drafts VALUES ('d1', '{\"stepProgress\":null,\"wizChat\":null}', 1, NULL, 100)")
                    }

                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            RETIRE_DERIVED_TRAINING_31_32.migrate(db)
            assertEquals(1, db.query("SELECT * FROM ongoing_workout").use { it.count })
        } finally {
            helper.close()
        }
    }

    @Test
    fun `B2-10 and B2-12 JSON import clears workout_voice_history and pre-validates invalid avatar before mutating Room`() = shortPathContext { context, _ ->
        runBlocking {
            val db = KpknDatabase.createInMemory(context)
            val repo = nutritionRepo(context, db)
            try {
                db.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity(foodId = "local", name = "Avena")))
                db.openHelper.writableDatabase.execSQL("INSERT INTO muscle_groups (id, name, description) VALUES ('m1', 'Pecho', '')")
                db.programDao().upsert(Program(id = "existing-prog", name = "Existente").toEntity())
                db.workoutVoiceHistoryDao().upsert(
                    WorkoutVoiceHistoryEntity(
                        id = "vh-1",
                        sessionKey = "s1",
                        exerciseId = "ex-1",
                        exerciseName = "Sentadilla",
                        setIdx = 0,
                        side = null,
                        transcript = "100 por 5",
                        status = "REGISTERED",
                        revision = 1,
                        payload = "{}",
                        audit = null,
                        createdAtEpochMs = 100L,
                        updatedAtEpochMs = 100L,
                    ),
                )
                val basePayload = SettingsExportPayload(
                    schemaVersion = BackupTableManifest.JSON_BACKUP_SCHEMA_VERSION,
                    exportedAt = "2026-11-19T12:00:00",
                    settings = Settings(),
                    programs = listOf(Program(id = "imported-prog", name = "Importado")),
                    workoutLogs = emptyList(),
                    activeProgramState = null,
                    ongoingWorkout = null,
                    nutritionLogs = emptyList(),
                    nutritionPlans = emptyList(),
                    activeNutritionPlanId = null,
                    pantryItems = emptyList(),
                    mealTemplates = emptyList(),
                )
                // Invalid Base64 avatar must fail BEFORE clearing existing Room rows.
                val badAvatarResult = runCatching {
                    SettingsJsonBackup.importPayload(
                        context = context,
                        payload = basePayload.copy(profilePhotoJpegBase64 = "not-valid-base64!!!"),
                        db = db,
                        nutritionRepository = repo,
                        onMeasurementSchedule = {},
                    )
                }
                assertTrue(badAvatarResult.isFailure)
                assertEquals(listOf("existing-prog"), db.programDao().getAll().map { it.id })

                // Valid import replaces programs and clears stale workout_voice_history rows.
                val validPngBytes = ByteArrayOutputStream().apply {
                    Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, this)
                }.toByteArray()
                SettingsJsonBackup.importPayload(
                    context = context,
                    payload = basePayload.copy(profilePhotoJpegBase64 = Base64.encodeToString(validPngBytes, Base64.NO_WRAP)),
                    db = db,
                    nutritionRepository = repo,
                    onMeasurementSchedule = {},
                )
                assertEquals(listOf("imported-prog"), db.programDao().getAll().map { it.id })
                assertEquals(0, db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM workout_voice_history").use {
                    it.moveToFirst(); it.getInt(0)
                })
            } finally {
                cancelNutritionRepoJob(repo)
                db.close()
            }
        }
    }

    @Test
    fun `B2-11 ProgramVersionDao prunes non-initial versions and deletes orphaned program versions`() = shortPathContext { context, _ ->
        runBlocking {
            val db = KpknDatabase.createInMemory(context)
            try {
                val dao = db.programVersionDao()
                dao.upsert(
                    ProgramVersionEntity("v0", "p1", "s1", "A", "initial:s1:A", 1L, "Edit", "Inicial", isInitial = true, snapshotJson = "{}"),
                )
                repeat(5) { idx ->
                    dao.upsert(
                        ProgramVersionEntity("v${idx + 1}", "p1", "s1", "A", "op-$idx", (idx + 2).toLong(), "Edit", "Paso $idx", isInitial = false, snapshotJson = "{}"),
                    )
                }
                dao.upsert(
                    ProgramVersionEntity("other", "p2", "s1", "A", "op-other", 10L, "Edit", "Otro", isInitial = false, snapshotJson = "{}"),
                )
                dao.pruneSessionVariant("p1", "s1", "A", keepNonInitial = 2)
                val remaining = dao.getForProgram("p1").map { it.id }.toSet()
                assertEquals(setOf("v0", "v4", "v5"), remaining)

                dao.deleteForProgram("p1")
                assertTrue(dao.getForProgram("p1").isEmpty())
                assertEquals(1, dao.getForProgram("p2").size)
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun `B2-08 DatabaseBackupHelper prunes snapshots and deletes wal and shm sidecars`() = shortPathContext { context, _ ->
        runBlocking(Dispatchers.IO) {
            KpknDatabase.getInstance(context)
            val createdNames = mutableListOf<String>()
            repeat(DatabaseBackupHelper.MAX_RETAINED_SNAPSHOTS + 2) { idx ->
                val name = DatabaseBackupHelper.createSnapshot(context)
                val file = File(File(context.filesDir, "snapshots"), name)
                file.setLastModified(1_700_000_000_000L + idx * 1_000L)
                createdNames += name
            }
            val snapshots = DatabaseBackupHelper.listSnapshots(context)
            assertEquals(DatabaseBackupHelper.MAX_RETAINED_SNAPSHOTS, snapshots.size)

            val victim = snapshots.first()
            File(victim.path + "-wal").writeText("wal")
            File(victim.path + "-shm").writeText("shm")
            assertTrue(DatabaseBackupHelper.deleteSnapshot(victim))
            assertFalse(victim.exists())
            assertFalse(File(victim.path + "-wal").exists())
            assertFalse(File(victim.path + "-shm").exists())
        }
    }

    @Test
    fun `B2-05 and B2-13 BodyProgressRepository clearAllDataAndAwait clears memory and ignores unknown schedule keys`() = shortPathContext { context, _ ->
        runBlocking {
            context.getSharedPreferences("body_measurements", Context.MODE_PRIVATE)
                .edit()
                .putString("schedule", """{"enabled":true,"intervalDays":7,"unknownFutureField":"kept"}""")
                .commit()
            val repo = BodyProgressRepository.getInstance(context)
            repo.awaitReady()
            assertTrue(repo.measurementSchedule.value.enabled)
            assertEquals(7, repo.measurementSchedule.value.intervalDays)

            assertTrue(
                repo.addObservation(
                    BodyObservation("obs-1", BodyMetric.WEIGHT, 75.0, "kg", timestampEpochMs = 1_000L),
                ),
            )
            assertTrue(
                repo.upsertGoal(
                    BodyGoal("goal-1", BodyMetric.WEIGHT, 72.0, "kg", createdAtEpochMs = 1_000L, updatedAtEpochMs = 1_000L),
                ),
            )
            repo.clearAllDataAndAwait()
            assertTrue(repo.observations.value.isEmpty())
            assertTrue(repo.goals.value.isEmpty())
            assertFalse(repo.measurementSchedule.value.enabled)
        }
    }

    @Test
    fun `B2-02 CalibrationDecisionRepository and TrainingObservationRepository skip corrupt rows safely`() = shortPathContext { context, _ ->
        runBlocking {
            val db = KpknDatabase.createInMemory(context)
            try {
                db.calibrationDecisionDao().upsert(
                    listOf(
                        CalibrationDecisionEntity(
                            "bad-cal", "s", "log", null, "cfg", "ctx",
                            null, null, "USER", null, 1.0, "EXERCISE", "OK",
                            "[]", "[]", "PROPOSED", 1L, null, 1, "{corrupt}",
                        ),
                    ),
                )
                val calRepo = com.example.kpkn.data.repository.CalibrationDecisionRepository(db.calibrationDecisionDao())
                assertTrue(calRepo.all().isEmpty())
                assertTrue(calRepo.forSession("log").isEmpty())

                db.trainingObservationDao().preserve(
                    TrainingObservationEntity("bad-fb", "feedback", "2026-11-19", "{corrupt", "test"),
                )
                db.trainingObservationDao().preserve(
                    TrainingObservationEntity(
                        "ok-fb",
                        "feedback",
                        "2026-11-19",
                        """{"logId":"log-1","date":"2026-11-19","cnsRecovery":8}""",
                        "test",
                    ),
                )
                val obsRepo = TrainingObservationRepository(context, databaseOverride = db)
                assertEquals(listOf("log-1"), obsRepo.getPostSessionFeedbacks().map { it.logId })
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun `B2-05 SharedExerciseLoadReferenceRepository resetFromStorage drops stale in-memory entries`() = shortPathContext { context, _ ->
        runBlocking {
            val db = KpknDatabase.getInstance(context)
            val repo = SharedExerciseLoadReferenceRepository.getInstance(context)
            val ref1 = SharedExerciseLoadReference(
                id = "ref-1",
                exerciseKey = "squat",
                configurationId = "barbell",
                tagId = null,
                unitMode = UnitModeV2.REPS,
                loadMode = LoadModeV2.LOAD,
                quantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL,
                kind = SharedLoadReferenceKind.ONE_RM,
                baseValueKg = 140.0,
                sourceAtMs = 1_000L,
            )
            repo.save(ref1)
            assertEquals(1, repo.references.value.size)
            repo.awaitPendingWrites()

            db.sharedExerciseLoadReferenceDao().deleteAll()
            repo.resetFromStorage()
            assertTrue(repo.references.value.isEmpty())
        }
    }

    @Test
    fun `B3-08 SettingsViewModel clearAllAppData removes importer draft and private media`() = shortPathContext { context, _ ->
        com.example.kpkn.data.repository.ProgramRepository.initForTests(context)
        NutritionRepository.initForTests(
            context,
            object : com.example.kpkn.data.repository.FoodCatalogImporter {
                override suspend fun importIfNeeded(
                    db: KpknDatabase,
                    context: Context,
                    alreadyImported: Boolean,
                    existingMeta: com.example.kpkn.data.food.FoodImporter.ImportMetadata?,
                    onMetaUpdated: (com.example.kpkn.data.food.FoodImporter.ImportMetadata) -> Unit,
                ): Boolean = false
            },
        )
        val viewModelStore = androidx.lifecycle.ViewModelStore()
        try {
            runBlocking {
                val db = KpknDatabase.getInstance(context)
                db.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity(foodId = "local", name = "Avena")))
                db.openHelper.writableDatabase.execSQL("INSERT INTO muscle_groups (id, name, description) VALUES ('m1', 'Pecho', '')")
            }
            val evidenceFile = File(File(context.filesDir, "import-source-evidence").apply { mkdirs() }, "ev.pdf").apply { writeText("pdf") }
            val photoFile = File(File(context.cacheDir, "import_photos").apply { mkdirs() }, "cam.jpg").apply { writeText("jpg") }
            val draftMain = File(context.filesDir, "imported-program-editor-v1.json").apply { writeText("{}") }
            val draftBak = File(context.filesDir, "imported-program-editor-v1.json.bak").apply { writeText("{}") }
            val draftUncommitted = File(context.filesDir, "imported-program-editor-v1.json.uncommitted-1").apply { writeText("{}") }

            val vm = com.example.kpkn.screens.settings.SettingsViewModel().also {
                viewModelStore.put("settings", it)
            }
            val done = java.util.concurrent.atomic.AtomicBoolean(false)
            val err = java.util.concurrent.atomic.AtomicReference<String?>(null)
            vm.clearAllAppData(context, onSuccess = { done.set(true) }, onError = { err.set(it); done.set(true) })
            repeat(600) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                if (done.get()) return@repeat
                Thread.sleep(10)
            }
            assertTrue("clearAllAppData did not complete (err=${err.get()})", done.get())
            assertEquals(null, err.get())
            assertFalse(evidenceFile.parentFile!!.exists())
            assertFalse(photoFile.parentFile!!.exists())
            assertFalse(draftMain.exists())
            assertFalse(draftBak.exists())
            assertFalse(draftUncommitted.exists())
        } finally {
            viewModelStore.clear()
            com.example.kpkn.data.repository.SessionTemplateRepository.resetForTests()
            NutritionRepository.closeInstance()
            com.example.kpkn.data.repository.ProgramRepository.closeInstance()
        }
    }
}
