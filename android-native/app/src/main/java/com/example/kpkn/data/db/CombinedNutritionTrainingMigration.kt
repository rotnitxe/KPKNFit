package com.example.kpkn.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Joins the public MFP31 and private POST-RINGS31..34 histories without changing their published identities. */
val COMBINED_NUTRITION_TRAINING_35: Array<Migration> = (30..34).map { from ->
    object : Migration(from, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (from <= 31) {
                if (hasCompatibilityTable(db, "auge_wellbeing")) {
                    // The original responses must be copied before any retired table is removed.
                    TRAINING_OBSERVATIONS_30_31.migrate(db)
                    RETIRE_DERIVED_TRAINING_31_32.migrate(db)
                } else {
                    check(hasCompatibilityTable(db, "training_observations")) {
                        "No se reconoce este historial de entrenamiento; conserva y recupera la copia original."
                    }
                }
            }
            if (from <= 32) RETIRE_CATALOG_COEFFICIENTS_32_33.migrate(db)
            if (from <= 33) RETIRE_PENDING_TRAINING_PROPOSALS_33_34.migrate(db)
            // Idempotent: existing day marks/check-ins from MFP31 retain every row.
            nutritionDayStateMigration(34).migrate(db)
        }
    }
}.toTypedArray()

private fun hasCompatibilityTable(db: SupportSQLiteDatabase, table: String): Boolean =
    db.query("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }
