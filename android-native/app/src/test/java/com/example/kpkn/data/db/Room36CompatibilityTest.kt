package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class Room36CompatibilityTest {
    private fun context(): Context {
        val dir = Files.createTempDirectory("catalog-room36-").toFile()
        return object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(dir, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?) =
                SQLiteDatabase.openDatabase(getDatabasePath(name).absolutePath, factory, mode or SQLiteDatabase.CREATE_IF_NECESSARY, handler)
        }
    }

    @Test fun room35MigrationPreservesOriginalRowsAndSeparatesUsageAcrossCatalogReplacementAndReopen() = runBlocking {
        val context = context()
        val path = listOf(File("."), File(".."), File("../..")).map {
            File(it, "android-native/app/schemas/com.example.kpkn.data.db.KpknDatabase/35.json")
        }.first { it.isFile }
        val schema = JSONObject(path.readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("test.db"), null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("""INSERT INTO global_foods(foodId,name,normalizedName,aliasesJson,calories,protein,carbs,fats,
                fiber,sugar,sodiumMg,potassiumMg,waterMl,caffeineMg,creatineG,source,sourcePriority,verifiedScore,
                usageCount,lastUsedAt,foodState,nutritionBasis,datasetVersion,qualityFlagsJson)
                VALUES('used','Original','original','[]',0,0,0,0,0,0,0,0,0,0,0,'OFF',50,0.5,7,'2026-10-23','UNKNOWN','PER_100G_AS_SOLD','v12','[]')""")
            old.execSQL("INSERT INTO nutrition_logs(id,date,mealType,data) VALUES('history','2026-10-23','LUNCH',?)", arrayOf("{\"original\":\"histórico\"}"))
            old.version = 35
        }
        fun open() = Room.databaseBuilder(context, KpknDatabase::class.java, "test.db")
            .addMigrations(NUTRITION_CATALOG_35_36).allowMainThreadQueries().build()
        var db = open()
        try {
            assertEquals(36, db.openHelper.writableDatabase.version) // Full generated Room schema validation.
            assertEquals(NutritionFoodUsageEntity("used", 7, "2026-10-23"), db.nutritionCatalogDao().usage("used"))
            db.openHelper.writableDatabase.query("SELECT data FROM nutrition_logs WHERE id='history'").use {
                assertTrue(it.moveToFirst()); assertEquals("{\"original\":\"histórico\"}", it.getString(0))
            }
            db.nutritionDao().clearGlobalFoods()
            // Removed products retain personal usage; publication does not need a product row.
            val snapshot = NutritionCatalogPublicationEntity(1, "a".repeat(64), "b".repeat(64), 548_229_120L, 2, 287718, null)
            db.nutritionCatalogDao().publish(snapshot)
            (1..12).map { async { db.nutritionDao().incrementGlobalFoodUsage("used", "2026-10-24") } }.awaitAll()
            assertEquals(19, db.nutritionCatalogDao().usage("used")!!.usageCount)
            assertEquals(19, db.nutritionDao().getGlobalFoodUsage().single().usageCount)
            db.close()
            db = open()
            assertEquals(snapshot, db.nutritionCatalogDao().active())
            assertEquals(19, db.nutritionCatalogDao().usageForBackup().single().usageCount)
            db.nutritionDao().restoreGlobalFoodUsage("not-in-current-generation", 4, "2020-01-01")
            assertEquals(4, db.nutritionCatalogDao().usage("not-in-current-generation")!!.usageCount)
        } finally { db.close() }
    }

    @Test fun stalePublicationRollsBackWithoutChangingActivePointerOrUsage() = runBlocking {
        val db = KpknDatabase.createInMemory(context())
        try {
            val dao = db.nutritionCatalogDao()
            val port = dao
            val first = NutritionCatalogPublicationEntity(1, "a".repeat(64), "d".repeat(64), 548_229_120L, 2, 60000, null)
            port.publish(first)
            dao.recordUse("personal", "2026-10-24")
            try {
                port.publish(first.copy(generation = "b".repeat(64), previousGeneration = "c".repeat(64)))
                fail("Stale pointer accepted")
            } catch (_: IllegalStateException) { }
            assertEquals(first, port.active())
            assertEquals(1, dao.usage("personal")!!.usageCount)
        } finally { db.close() }
    }

    @Test fun incrementLazilyPreservesUsageOnLegacyRowsInsertedAfterMigration() = runBlocking {
        val db = KpknDatabase.createInMemory(context())
        try {
            val dao = db.nutritionDao()
            dao.insertGlobalFoods(listOf(GlobalFoodEntity("legacy-late", "Original", usageCount = 4, lastUsedAt = "2026-01-01")))
            assertEquals(4, dao.getGlobalFoodUsage().single().usageCount)
            dao.incrementGlobalFoodUsage("legacy-late", "2026-10-24")
            assertEquals(5, dao.getGlobalFoodById("legacy-late")!!.usageCount)
            assertEquals(5, db.nutritionCatalogDao().usage("legacy-late")!!.usageCount)
            dao.clearGlobalFoods()
            assertEquals(5, dao.getGlobalFoodUsage().single().usageCount)
        } finally { db.close() }
    }
}
