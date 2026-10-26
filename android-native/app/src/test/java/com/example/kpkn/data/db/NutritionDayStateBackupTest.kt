package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.settings.SettingsExportPayload
import com.example.kpkn.data.settings.SettingsJsonBackup
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionDayStateBackupTest {
    @get:Rule val roomRule = RoomTestRule()
    // sqlite4java on Windows cannot open Robolectric's long test-name + snapshot paths.
    private val context: Context by lazy {
        val root = Files.createTempDirectory("nutrition-backup-").toFile()
        object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
            override fun getDatabasePath(name: String) = (if (File(name).isAbsolute) File(name) else File(root, "db/$name")).apply { parentFile!!.mkdirs() }
            override fun deleteDatabase(name: String) = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?) =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY, handler)
        }
    }
    private val mark = NutritionDayMarkEntity("2026-10-10", "PARTIAL", "REVIEW_ACCEPTED", "2026-10-11T12:00:00Z")
    private val checkin = NutritionCheckInEntity("2026-10-05", "APPLIED", mark.updatedAt,
        """{"modelVersion":"expenditure-v1","previous":2200,"proposed":2100}""", "plan-aplicado", mark.updatedAt)

    private fun repository(db: KpknDatabase): NutritionRepository {
        val ctor = NutritionRepository::class.java.getDeclaredConstructor(Context::class.java, KpknDatabase::class.java,
            Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        return ctor.newInstance(context, db, false)
    }

    private suspend fun export(db: KpknDatabase) = SettingsJsonBackup.buildPayload(
        context = context, settings = Settings(), profilePhotoJpegBase64 = null, programs = emptyList(),
        workoutLogs = emptyList(), activeProgramState = null, ongoingWorkout = null, nutritionLogs = emptyList(),
        nutritionPlans = emptyList(), activeNutritionPlanId = null, pantryItems = emptyList(), mealTemplates = emptyList(),
        customFoods = emptyList(), learnedResolutions = emptyList(), foodCatalogMeta = null, bodyObservations = emptyList(),
        bodyGoals = emptyList(), measurementSchedule = null, calibrationProfile = null, dailyGoalSnapshots = emptyList(),
        wellbeingLogs = emptyList(), sleepLogs = emptyList(), sleepLogsExtended = emptyList(), postSessionFeedback = emptyList(),
        sessionTemplates = emptyList(), customExercises = emptyList(), db = db,
    )

    @Test fun `copia JSON viaja y restaura marcas checkins y sus instantaneas sin duplicar`() = runBlocking {
        val db = KpknDatabase.createInMemory(context)
        val repo = repository(db)
        try {
            db.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity("catalogo-local", "Arroz")))
            db.openHelper.writableDatabase.execSQL("INSERT INTO muscle_groups (id, name, description) VALUES ('musculo', 'Músculo', '')")
            db.nutritionDao().upsertDayMark(mark)
            db.nutritionDao().upsertCheckIn(checkin)
            val unreadable = mark.copy(date = "fecha-ilegible", mark = "OTRA")
            db.nutritionDao().upsertDayMark(unreadable)
            val payload = dbJson.decodeFromString<SettingsExportPayload>(dbJson.encodeToString(export(db)))
            assertTrue(payload.includesNutritionDayStateSection)
            assertEquals(BackupTableManifest.Category.USER_DATA, BackupTableManifest.tableCategory["nutrition_day_marks"])
            assertEquals(BackupTableManifest.Category.USER_DATA, BackupTableManifest.tableCategory["nutrition_checkins"])
            db.nutritionDao().upsertDayMark(mark.copy(date = "2026-10-12"))
            db.nutritionDao().upsertCheckIn(checkin.copy(weekKey = "2026-10-12"))
            repeat(2) { SettingsJsonBackup.importPayload(context, payload, db, repo) {} }
            assertEquals(setOf(mark, unreadable), db.nutritionDao().getAllDayMarks().toSet())
            assertEquals(listOf(checkin), db.nutritionDao().getAllCheckIns())
            val again = export(db)
            assertEquals(payload.nutritionDayMarks, again.nutritionDayMarks)
            assertEquals(payload.nutritionCheckIns, again.nutritionCheckIns)

            // Una copia anterior, sin la sección, no borra ni importa declaraciones accidentales.
            for (version in listOf(4, 5, 6)) {
                SettingsJsonBackup.importPayload(context, payload.copy(schemaVersion = version, includesNutritionDayStateSection = false), db, repo) {}
                assertEquals(setOf(mark, unreadable), db.nutritionDao().getAllDayMarks().toSet())
                assertEquals(checkin, db.nutritionDao().getCheckIn(checkin.weekKey))
            }
            // La sección vacía y explícita sí restaura un estado vacío.
            SettingsJsonBackup.importPayload(context, payload.copy(nutritionDayMarks = emptyList(), nutritionCheckIns = emptyList()), db, repo) {}
            assertTrue(db.nutritionDao().getAllDayMarks().isEmpty())
            assertTrue(db.nutritionDao().getAllCheckIns().isEmpty())
        } finally {
            val field = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
            (field.get(repo) as Job).cancelAndJoin()
            db.close()
        }
    }

    @Test fun `copia fisica incluye ambas tablas sin un tratamiento especial`() = runBlocking(Dispatchers.IO) {
        KpknDatabase.closeInstance()
        context.deleteDatabase("kpkn.db")
        var snapshot: File? = null
        try {
            val db = KpknDatabase.getInstance(context)
            db.nutritionDao().upsertDayMark(mark)
            db.nutritionDao().upsertCheckIn(checkin)
            db.nutritionCatalogDao().recordUse("orphan-snapshot", mark.updatedAt)
            snapshot = File(File(context.filesDir, "snapshots"), DatabaseBackupHelper.createSnapshot(context))
            db.nutritionDao().deleteDayMark(mark.date)
            db.openHelper.writableDatabase.execSQL("DELETE FROM nutrition_checkins")
            db.openHelper.writableDatabase.execSQL("DELETE FROM nutrition_food_usage")
            DatabaseBackupHelper.restoreSnapshot(context, snapshot)
            val restored = KpknDatabase.getInstance(context)
            assertEquals(listOf(mark), restored.nutritionDao().getAllDayMarks())
            assertEquals(listOf(checkin), restored.nutritionDao().getAllCheckIns())
            assertEquals(NutritionFoodUsageEntity("orphan-snapshot", 1, mark.updatedAt), restored.nutritionCatalogDao().usage("orphan-snapshot"))
        } finally {
            KpknDatabase.closeInstance()
            snapshot?.delete()
            context.deleteDatabase("kpkn.db")
        }
    }
}
