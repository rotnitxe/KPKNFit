package com.example.kpkn.data.export

import androidx.room.withTransaction
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import java.time.ZoneId

/** Solo lectura. La cuarentena registra identidades; las filas originales permanecen intactas en Room. */
class CsvSnapshotLoader(private val db: KpknDatabase, private val settings: Settings, private val zone: ZoneId = ZoneId.systemDefault()) {
    suspend fun load(domains: Set<CsvExportDomain>): CsvExportSnapshot = withContext(Dispatchers.IO) {
        db.withTransaction {
            val issues = mutableListOf<CsvRowIssue>()
            fun <E, T : Any> decode(rows: List<E>, table: String, id: (E) -> String, read: (E) -> T): List<T> = rows.mapNotNull { row ->
                try { read(row) } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { issues += CsvRowIssue(table, id(row)); null }
            }
            val meals = if (CsvExportDomain.MEALS in domains) decode(db.nutritionDao().getAllLogs(), "nutrition_logs", { it.id }) { it.toNutritionLog() } else emptyList()
            val marks = if (CsvExportDomain.MEALS in domains) decode(db.nutritionDao().getAllDayMarks(), "nutrition_day_marks", { it.date }) { it.toDayMarkEntry() }
                .associate { it.date to it.mark } else emptyMap()
            val observations = if (CsvExportDomain.BODY in domains) decode(db.bodyProgressDao().getAllObservations(), "body_observations", { it.id }) {
                requireNotNull(it.toBodyObservation())
            } else emptyList()
            val workouts = if (CsvExportDomain.WORKOUTS in domains) decode(db.workoutLogDao().getAll(), "workout_logs", { it.id }) { requireNotNull(it.toWorkoutLog()) } else emptyList()
            val foods = if (CsvExportDomain.LIBRARY in domains) decode(db.nutritionDao().getAllCustomFoods(), "custom_foods", { it.id }) { dbJson.decodeFromString<FoodItem>(it.data) } else emptyList()
            val exercises = if (CsvExportDomain.LIBRARY in domains) decode(db.customExerciseDao().getAll(), "custom_exercises", { it.id }) { it.toExerciseMuscleInfo() } else emptyList()
            issues.forEach { issue -> android.util.Log.w("CsvExport", "Fila ilegible en ${issue.table}; id=${issue.id.filter { it.isLetterOrDigit() || it in "-_:." }.take(80)}") }
            CsvExportSnapshot(meals, observations, workouts, foods, exercises, marks, settings.weightUnit, zone, issues)
        }
    }
}
