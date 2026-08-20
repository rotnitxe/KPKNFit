package com.example.kpkn.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fase 2/6 — Migración Room v20→v23 (plan 2026-08-16_nutrition_precision_v2).
 *
 * Sembramos una base v20 realista con planes, logs, estado activo, alimentos
 * personalizados, catálogo global y resoluciones aprendidas, ejecutamos la
 * cadena completa de migraciones y verificamos que:
 * - ningún dato nutricional se pierde (identificadores estables);
 * - las columnas nuevas de procedencia existen con sus defaults;
 * - la tabla de calibración está disponible como singleton;
 * - los snapshots diarios inmutables están disponibles sin tocar datos previos.
 *
 * La v21 (body_observations/body_goals) pertenece a trabajo concurrente y se
 * valida aquí solo como paso intermedio de la cadena.
 */
@RunWith(AndroidJUnit4::class)
class NutritionMigrationTest {

    private val dbName = "migration-test-nutrition.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KpknDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private fun seedV20() {
        helper.createDatabase(dbName, 20).use { db ->
            db.execSQL(
                """INSERT INTO nutrition_plans (id, name, isActive, data) VALUES
                ('plan1', 'Cut', 1, '{"id":"plan1","calorieTarget":2000}'),
                ('plan2', 'Bulk', 0, '{"id":"plan2","calorieTarget":3000}')"""
            )
            db.execSQL(
                """INSERT INTO nutrition_logs (id, date, mealType, data) VALUES
                ('log1', '2026-08-15', 'LUNCH', '{"id":"log1","foods":[]}')"""
            )
            db.execSQL("INSERT INTO nutrition_active_state (rowId, activePlanId) VALUES (1, 'plan1')")
            db.execSQL(
                """INSERT INTO nutrition_custom_foods
                (id, name, normalizedName, aliasesJson, sourcePriority, verifiedScore, usageCount, data)
                VALUES ('custom1', 'Mi Batido', 'mi batido', '[]', 90, 1.0, 3, '{"id":"custom1"}')"""
            )
            db.execSQL(
                """INSERT INTO global_foods
                (foodId, name, normalizedName, aliasesJson, calories, protein, carbs, fats,
                 fiber, sugar, sodiumMg, potassiumMg, waterMl, source, sourcePriority, verifiedScore, usageCount)
                VALUES ('usda_331960', 'Chicken cooked', 'chicken cooked', '[]', 166.0, 32.1, 0.0, 3.24,
                        0.0, 0.0, 0.0, 0.0, 0.0, 'USDA', 70, 0.85, 0)"""
            )
            db.execSQL(
                """INSERT INTO learned_resolutions
                (id, queryKey, foodId, portionGrams, cookingMethod, count, lastUsedAt, createdAt)
                VALUES ('pechuga de pollo', 'pechuga de pollo', 'gen003', 200.0, 'COCIDO', 4, 1000, 500)"""
            )
        }
    }

    @Test
    fun migracionV20aV23_preservaDatosNutricionalesYAgregaHistorial() {
        seedV20()

        val db = helper.runMigrationsAndValidate(
            dbName, 23, true,
            KpknDatabase.MIGRATION_20_21,
            KpknDatabase.MIGRATION_21_22,
            KpknDatabase.MIGRATION_22_23,
        )

        // Planes, estado activo y logs intactos
        db.query("SELECT COUNT(*) FROM nutrition_plans").use { c ->
            c.moveToFirst(); assertEquals(2, c.getInt(0))
        }
        db.query("SELECT activePlanId FROM nutrition_active_state WHERE rowId = 1").use { c ->
            c.moveToFirst(); assertEquals("plan1", c.getString(0))
        }
        db.query("SELECT COUNT(*) FROM nutrition_logs").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }

        // Alimentos personalizados y aprendizaje intactos (identificadores estables)
        db.query("SELECT name FROM nutrition_custom_foods WHERE id = 'custom1'").use { c ->
            c.moveToFirst(); assertEquals("Mi Batido", c.getString(0))
        }
        db.query(
            "SELECT foodId, portionGrams, cookingMethod, confidence FROM learned_resolutions WHERE queryKey = 'pechuga de pollo'"
        ).use { c ->
            c.moveToFirst()
            assertEquals("gen003", c.getString(0))
            assertEquals(200.0, c.getDouble(1), 0.01)
            assertEquals("COCIDO", c.getString(2))
            assertEquals(1.0, c.getDouble(3), 0.01)
        }

        // Catálogo global: la fila v20 sobrevive con los defaults de procedencia
        db.query(
            """SELECT foodState, nutritionBasis, datasetVersion, qualityFlagsJson, sourceRecordId
               FROM global_foods WHERE foodId = 'usda_331960'"""
        ).use { c ->
            c.moveToFirst()
            assertEquals("UNKNOWN", c.getString(0))
            assertEquals("PER_100G", c.getString(1))
            assertEquals("", c.getString(2))
            assertEquals("[]", c.getString(3))
            assertTrue(c.isNull(4))
        }

        // Tabla de calibración disponible (singleton)
        db.execSQL(
            """INSERT INTO nutrition_calibration_profile (rowId, schemaVersion, data, updatedAt)
               VALUES (1, 1, '{"weighingConvention":"COOKED"}', 123)"""
        )
        db.query("SELECT schemaVersion FROM nutrition_calibration_profile WHERE rowId = 1").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }

        db.execSQL(
            """INSERT INTO daily_goal_snapshots
               (date, planId, calorieTargetKcal, proteinGoalG, carbGoalG, fatGoalG,
                direction, calculationOrigin, capturedAtEpochMs)
               VALUES ('2026-08-15', 'plan1', 2000, 160, 200, 60, 'DEFICIT', 'PLAN', 123)"""
        )
        db.query("SELECT planId, calorieTargetKcal FROM daily_goal_snapshots WHERE date = '2026-08-15'").use { c ->
            c.moveToFirst()
            assertEquals("plan1", c.getString(0))
            assertEquals(2000, c.getInt(1))
        }
    }
}
