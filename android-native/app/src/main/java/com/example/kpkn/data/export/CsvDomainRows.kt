package com.example.kpkn.data.export

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.export.*
import com.example.kpkn.domain.nutrition.MealTime
import com.example.kpkn.domain.nutrition.expenditure.DayMark
import com.example.kpkn.domain.time.ActivityLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class CsvExportDomain(val label: String) { MEALS("Comidas"), BODY("Cuerpo"), WORKOUTS("Entrenos"), LIBRARY("Mi biblioteca") }
data class CsvRowIssue(val table: String, val id: String)
data class CsvExportSnapshot(
    val meals: List<NutritionLog> = emptyList(), val observations: List<BodyObservation> = emptyList(),
    val workouts: List<WorkoutLog> = emptyList(), val foods: List<FoodItem> = emptyList(),
    val exercises: List<ExerciseMuscleInfo> = emptyList(), val dayMarks: Map<LocalDate, DayMark> = emptyMap(),
    val weightUnit: WeightUnit = WeightUnit.KG, val zone: ZoneId = ZoneId.systemDefault(), val issues: List<CsvRowIssue> = emptyList(),
)
data class CsvExportFile(val name: String, val bytes: ByteArray)
data class CsvExportBundle(val files: List<CsvExportFile>, val issues: List<CsvRowIssue>, val date: LocalDate) {
    val isZip: Boolean get() = files.size > 1
    val suggestedName: String get() = if (isZip) "kpkn-export-$date.zip" else files.single().name
    fun writeTo(output: OutputStream) {
        if (!isZip) output.write(files.single().bytes) else ZipOutputStream(output).use { zip ->
            files.forEach { file -> zip.putNextEntry(ZipEntry(file.name).apply { time = 0L }); zip.write(file.bytes); zip.closeEntry() }
        }
    }
}

/** Adaptadores por dominio: exportan instantáneas existentes y aíslan cada fila ilegible sin modificarla. */
object CsvDomainRows {
    private fun t(value: String?) = CsvCell.Text(value.orEmpty())
    private fun n(value: Double?) = value?.let { require(it.isFinite()); CsvCell.Number(it) } ?: CsvCell.Empty
    private fun n(value: Int?) = n(value?.toDouble())
    private fun guard(issues: MutableList<CsvRowIssue>, table: String, id: String, row: () -> List<CsvCell>): List<CsvCell>? = try {
        row()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { issues += CsvRowIssue(table, id); null }
    fun build(snapshot: CsvExportSnapshot, domains: Set<CsvExportDomain>, date: LocalDate): CsvExportBundle {
        require(domains.isNotEmpty()) { "Selecciona al menos un dominio." }
        val issues = snapshot.issues.toMutableList()
        val files = mutableListOf<CsvExportFile>()
        fun file(name: String, headers: List<String>, rows: List<List<CsvCell>>) {
            files += CsvExportFile("kpkn-$name-$date.csv", CsvWriter.bytes(headers, rows))
        }
        if (CsvExportDomain.MEALS in domains) {
            val rows = snapshot.meals.filter { it.status != NutritionStatus.PLANNED }.flatMap { log ->
                log.foods.mapIndexedNotNull { index, food -> guard(issues, "nutrition_logs", "${log.id}:$index") {
                    val day = LocalDate.parse(log.date.take(10))
                    val mark = when (snapshot.dayMarks[day]) { DayMark.COMPLETE_CONFIRMED -> "completo"; DayMark.PARTIAL -> "parcial"; DayMark.FASTING -> "ayuno"; else -> "" }
                    listOf(t(day.toString()), t(MealTime.label(log.date)), t(log.mealType.name), t(food.foodName), n(food.amount), t(food.unit),
                        n(food.calories), n(food.protein), n(food.carbs), n(food.fats), if (food.isQuickEntry) CsvCell.Empty else n(food.fiber),
                        if (food.isQuickEntry) CsvCell.Empty else n(food.sugar), if (food.isQuickEntry) CsvCell.Empty else n(food.sodiumMg), t(mark), t(origin(food)))
                } }
            }
            val representedDays = rows.map { (it.first() as CsvCell.Text).value }.toSet()
            val marksOnly = snapshot.dayMarks.filter { (day, mark) -> day.toString() !in representedDays && mark != DayMark.NONE }.toSortedMap().map { (day, mark) ->
                val label = when (mark) { DayMark.FASTING -> "ayuno"; DayMark.COMPLETE_CONFIRMED -> "completo"; DayMark.PARTIAL -> "parcial"; else -> "" }
                listOf(t(day.toString()), t(""), t(""), t(""), CsvCell.Empty, t(""), if (mark == DayMark.FASTING) n(0.0) else CsvCell.Empty,
                    CsvCell.Empty, CsvCell.Empty, CsvCell.Empty, CsvCell.Empty, CsvCell.Empty, CsvCell.Empty, t(label), t("marca de día"))
            }
            file("comidas", listOf("fecha","hora","comida","alimento","cantidad","unidad","kcal","proteina_g","carbohidratos_g","grasa_g","fibra_g","azucar_g","sodio_mg","marca_dia","origen"), rows + marksOnly)
        }
        if (CsvExportDomain.BODY in domains) {
            val rows = snapshot.observations.mapNotNull { observation -> guard(issues, "body_observations", observation.id) {
                val at = Instant.ofEpochMilli(observation.timestampEpochMs).atZone(snapshot.zone)
                val pounds = observation.metric == BodyMetric.WEIGHT && snapshot.weightUnit == WeightUnit.LBS
                val value = if (pounds) observation.valueSi / 0.45359237 else observation.valueSi
                listOf(t(at.toLocalDate().toString()), t(at.format(DateTimeFormatter.ofPattern("HH:mm"))), t(observation.metric.name), n(value),
                    t(if (pounds) "lb" else observation.unitSi), n(observation.valueSi))
            } }
            file("cuerpo", listOf("fecha","hora","metrica","valor","unidad","valor_si"), rows)
        }
        if (CsvExportDomain.WORKOUTS in domains) {
            val rows = snapshot.workouts.flatMap { log -> log.completedExercises.flatMap { exercise -> exercise.sets.mapIndexedNotNull { index, set ->
                guard(issues, "workout_logs", "${log.id}:${exercise.exerciseId}:$index") {
                    val day = ActivityLocalDate.fromInstantIsoOrDatePrefix(log.date, snapshot.zone)
                    val technique = set.recordedPayloadV3?.techniques?.joinToString("+") { it.name }?.takeIf { it.isNotBlank() } ?: buildList {
                        if (set.dropSets.isNotEmpty()) add("DROP_SET")
                        if (set.restPauses.isNotEmpty()) add("REST_PAUSE")
                        if (set.amrapPerformed) add("AMRAP")
                    }.joinToString("+")
                    listOf(t(day.toString()), t(log.sessionName), t(exercise.exerciseName), n((set.setIndex ?: index) + 1), n(set.weight), n(set.reps), n(set.rir),
                        t(technique), t((!set.skipped).toString()), t(listOfNotNull(set.notes, log.exerciseNotes[exercise.exerciseId], log.notes).joinToString("\n")),
                        t(set.side), t(set.isFailedSet.toString()), t(set.isWarmup.toString()), n(set.timeSeconds), n(set.distanceKm),
                        t(if(set.restPauses.any { it.style == "myo" }) "myo" else null), t(Json.encodeToString(set.restPauses)), t(Json.encodeToString(set.dropSets)))
                }
            } } }
            file("entrenos", listOf("fecha","sesion","ejercicio","serie","kg","reps","rir","tecnica","completada","notas","lado","fallida","calentamiento","duracion_seg","distancia_km","variante_tecnica","rest_pause_json","drop_sets_json"), rows)
        }
        if (CsvExportDomain.LIBRARY in domains) {
            val foods = snapshot.foods.mapNotNull { food -> guard(issues, "custom_foods", food.id) {
                listOf(t(food.id), t(food.name), t(food.brand), t(food.source), t(food.nutritionBasis), n(food.servingSize), t(food.servingUnit ?: food.unit),
                    n(food.calories), n(food.protein), n(food.carbs), n(food.fats), n(food.recipe?.version), n(food.recipe?.servings), n(food.recipe?.finalWeightGrams),
                    t(food.recipe?.let { Json.encodeToString(it.ingredients) }), t(food.recipe?.notes))
            } }
            file("alimentos-propios", listOf("id","nombre","marca","origen","base","porcion","unidad","kcal","proteina_g","carbohidratos_g","grasa_g","version_receta","raciones","peso_final_g","ingredientes_json","notas"), foods)
            val exercises = snapshot.exercises.mapNotNull { exercise -> guard(issues, "custom_exercises", exercise.id) {
                listOf(t(exercise.id), t(exercise.name), t(exercise.alias), t(exercise.description), t(exercise.equipment), t(exercise.category),
                    t(exercise.type), t(exercise.force), t(Json.encodeToString(exercise.involvedMuscles)))
            } }
            file("ejercicios-propios", listOf("id","nombre","alias","descripcion","equipo","categoria","tipo","fuerza","musculos_json"), exercises)
        }
        return CsvExportBundle(files, issues, date)
    }
    private fun origin(food: LoggedFood): String = when {
        food.isQuickEntry -> "entrada rápida"
        food.recipeId != null -> "receta"
        food.evidenceJson.orEmpty().contains("CUSTOM_CONFIRMED") || food.evidenceJson.orEmpty().contains("MANUAL") -> "propio"
        food.isUncertain || food.analysisSource in setOf(AnalysisSource.LOCAL_AI_ESTIMATE, AnalysisSource.EXTERNAL_API_ESTIMATE, AnalysisSource.LOCAL_HEURISTIC) -> "estimación"
        food.analysisSource == AnalysisSource.DATABASE || listOf("CURATED_LOCAL", "USDA", "OPEN_FOOD_FACTS").any { food.evidenceJson.orEmpty().contains(it) } -> "catálogo"
        else -> "desconocido"
    }
}
