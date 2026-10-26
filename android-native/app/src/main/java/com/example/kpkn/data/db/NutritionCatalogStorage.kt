package com.example.kpkn.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Regenerable metadata only. Catalog contents live in a verified immutable SQLite file. */
@Entity(tableName = "nutrition_catalog_publication")
data class NutritionCatalogPublicationEntity(
    @PrimaryKey val rowId: Int = 1,
    val generation: String,
    val sha256: String,
    val bytes: Long,
    val formatVersion: Int,
    val calculableIdentities: Int,
    val previousGeneration: String?,
)

/** Personal data: no foreign key to a replaceable product generation. */
@Entity(tableName = "nutrition_food_usage")
data class NutritionFoodUsageEntity(
    @PrimaryKey val foodId: String,
    val usageCount: Int,
    val lastUsedAt: String?,
)

@Dao
interface NutritionCatalogDao {
    @Query("SELECT * FROM nutrition_catalog_publication WHERE rowId = 1")
    suspend fun active(): NutritionCatalogPublicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPublication(value: NutritionCatalogPublicationEntity)

    /** Detect a competing publisher before changing the pointer; no food rows in this transaction. */
    @Transaction
    suspend fun publish(value: NutritionCatalogPublicationEntity) {
        require(value.rowId == 1)
        val old = active()
        check(old?.generation == value.previousGeneration || old?.generation == value.generation) {
            "Catalog publication changed during preparation"
        }
        putPublication(value)
    }

    @Query("SELECT * FROM nutrition_food_usage ORDER BY foodId")
    suspend fun usageForBackup(): List<NutritionFoodUsageEntity>

    @Query("SELECT * FROM nutrition_food_usage WHERE foodId = :foodId")
    suspend fun usage(foodId: String): NutritionFoodUsageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedUsage(value: NutritionFoodUsageEntity)

    @Query("UPDATE nutrition_food_usage SET usageCount = usageCount + 1, lastUsedAt = :at WHERE foodId = :foodId")
    suspend fun incrementExistingUsage(foodId: String, at: String)

    @Transaction
    suspend fun recordUse(foodId: String, at: String) {
        require(foodId.isNotBlank() && at.isNotBlank())
        seedUsage(NutritionFoodUsageEntity(foodId, 0, null))
        incrementExistingUsage(foodId, at)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreUsage(values: List<NutritionFoodUsageEntity>)
}

val NUTRITION_CATALOG_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `nutrition_catalog_publication` (
            `rowId` INTEGER NOT NULL, `generation` TEXT NOT NULL, `sha256` TEXT NOT NULL,
            `bytes` INTEGER NOT NULL, `formatVersion` INTEGER NOT NULL,
            `calculableIdentities` INTEGER NOT NULL, `previousGeneration` TEXT, PRIMARY KEY(`rowId`))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `nutrition_food_usage` (
            `foodId` TEXT NOT NULL, `usageCount` INTEGER NOT NULL, `lastUsedAt` TEXT, PRIMARY KEY(`foodId`))""")
        // Preserve even orphaned product usage and timestamps. Never clear the legacy catalog or originals.
        db.execSQL("""INSERT OR IGNORE INTO nutrition_food_usage(foodId, usageCount, lastUsedAt)
            SELECT foodId, usageCount, lastUsedAt FROM global_foods WHERE usageCount > 0 OR lastUsedAt IS NOT NULL""")
    }
}
