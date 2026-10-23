package com.example.kpkn.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val CALIBRATION_MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `calibration_decisions` (
                `id` TEXT NOT NULL, `sourceSetId` TEXT NOT NULL, `sessionLogId` TEXT NOT NULL,
                `programId` TEXT, `exerciseConfigId` TEXT NOT NULL, `contextKey` TEXT NOT NULL,
                `capacityKg` REAL, `referenceKg` REAL, `referenceSource` TEXT NOT NULL,
                `ratio` REAL, `factor` REAL NOT NULL, `scope` TEXT NOT NULL, `eligibility` TEXT NOT NULL,
                `affected` TEXT NOT NULL, `propagated` TEXT NOT NULL, `status` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL, `resolvedAt` INTEGER, `estimatorVersion` INTEGER NOT NULL,
                `data` TEXT NOT NULL, PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calibration_decisions_sessionLogId_sourceSetId` ON `calibration_decisions` (`sessionLogId`, `sourceSetId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calibration_decisions_exerciseConfigId_contextKey_status_resolvedAt` ON `calibration_decisions` (`exerciseConfigId`, `contextKey`, `status`, `resolvedAt`)")
    }
}
