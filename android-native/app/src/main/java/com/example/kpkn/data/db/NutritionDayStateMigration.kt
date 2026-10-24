package com.example.kpkn.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room v31, después de calibración v30: solo añade tablas e índice; es idempotente y conserva todas las filas anteriores. */
fun nutritionDayStateMigration(from: Int): Migration = object : Migration(from, from + 1) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(NUTRITION_DAY_MARKS_CREATE_SQL)
        db.execSQL(NUTRITION_CHECKINS_CREATE_SQL)
        db.execSQL(GLOBAL_FOOD_SOURCE_RECORD_INDEX_SQL)
    }
}

internal const val NUTRITION_DAY_MARKS_CREATE_SQL = """
CREATE TABLE IF NOT EXISTS `nutrition_day_marks` (`date` TEXT NOT NULL, `mark` TEXT NOT NULL,
`source` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`date`))
"""

internal const val NUTRITION_CHECKINS_CREATE_SQL = """
CREATE TABLE IF NOT EXISTS `nutrition_checkins` (`weekKey` TEXT NOT NULL, `status` TEXT NOT NULL,
`decidedAt` TEXT, `proposalJson` TEXT, `appliedPlanId` TEXT, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`weekKey`))
"""

internal const val GLOBAL_FOOD_SOURCE_RECORD_INDEX_SQL =
    "CREATE INDEX IF NOT EXISTS `index_global_foods_sourceRecordId` ON `global_foods` (`sourceRecordId`)"
