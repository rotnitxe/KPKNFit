package com.example.kpkn.data.db

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [26, 34])
class NormalizedFoodFtsTest {
    @Test fun migrateV29() = runBlocking(Dispatchers.IO) {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "restore_${UUID.randomUUID()}.db"
        val candidates = generateSequence(File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
            .flatMap { sequenceOf(File(it, "schemas/com.example.kpkn.data.db.KpknDatabase/29.json"), File(it, "android-native/app/schemas/com.example.kpkn.data.db.KpknDatabase/29.json")) }
        val fixture = candidates.first { it.isFile }
        val old = Json.parseToJsonElement(fixture.readText()).jsonObject.getValue("database").jsonObject
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(29) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    old.getValue("entities").jsonArray.forEach { element ->
                        val entity = element.jsonObject
                        val table = entity.getValue("tableName").jsonPrimitive.content
                        db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                        entity["indices"]?.jsonArray?.forEach { index -> db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
                    }
                    old.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture is v29")
            }).build())
        try {
            val sql = helper.writableDatabase
            val fields = old.getValue("entities").jsonArray.first { it.jsonObject.getValue("tableName").jsonPrimitive.content == "global_foods" }
                .jsonObject.getValue("fields").jsonArray.map { it.jsonObject }.filter { it["notNull"]?.jsonPrimitive?.boolean == true }
            val values = fields.associate { field -> field.getValue("columnName").jsonPrimitive.content to
                (if (field.getValue("affinity").jsonPrimitive.content == "TEXT") "" else 0) }.toMutableMap<String, Any>()
            values.putAll(mapOf("foodId" to "traceable", "name" to "Leche con cacao", "normalizedName" to "leche con cacao",
                "source" to "USDA", "usageCount" to 11, "aliasesJson" to "[\"chocolate milk\",\"café\"]",
                "nutritionBasis" to "PER_100G_AS_SOLD", "qualityFlagsJson" to "[]", "normalizedBrand" to "colun"))
            sql.execSQL("INSERT INTO global_foods (${values.keys.joinToString(",") { "`$it`" }}) VALUES (${values.keys.joinToString(",") { "?" }})", values.values.toTypedArray())
            helper.close()
            val migrated = KpknDatabase.openIsolated(context, name)
            try {
                val dao = migrated.nutritionDao()
                val byAlias = dao.searchGlobalFoodsWithFts("chocolate* milk*").single()
                assertEquals("traceable", byAlias.foodId); assertEquals(11, byAlias.usageCount)
                assertEquals("traceable", dao.searchGlobalFoodsWithFts("colun*").single().foodId)
                assertEquals("traceable", dao.searchGlobalFoodsWithFts("cafe*").single().foodId)
                migrated.openHelper.writableDatabase.execSQL("UPDATE global_foods SET normalizedName='leche descremada', aliasesJson='[\"skim milk\"]' WHERE foodId='traceable'")
                assertTrue(dao.searchGlobalFoodsWithFts("chocolate*").isEmpty())
                assertEquals("traceable", dao.searchGlobalFoodsWithFts("skim*").single().foodId)
                migrated.openHelper.writableDatabase.execSQL("DELETE FROM global_foods WHERE foodId='traceable'")
                assertTrue(dao.searchGlobalFoodsWithFts("skim*").isEmpty())
                assertEquals(0, migrated.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { it.count })
            } finally { migrated.close() }
        } finally { helper.close(); context.deleteDatabase(name) }
    }
}
