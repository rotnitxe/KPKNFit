package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * NutritionRepository — Write-through cache para estado nutricional.
 */
class NutritionRepository private constructor(context: Context) {

    private val db = KpknDatabase.getInstance(context)
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── Nutrition Logs ──────────────────────────────────────────────────────

    private val _nutritionLogs = MutableStateFlow<List<NutritionLog>>(emptyList())
    val nutritionLogs: StateFlow<List<NutritionLog>> = _nutritionLogs.asStateFlow()

    fun addNutritionLog(log: NutritionLog) {
        _nutritionLogs.update { it + log }
        scope.launch { db.nutritionDao().upsertLog(log.toEntity()) }
        if (log.foods.isNotEmpty()) {
            rememberMealTemplateFromLog(log)
        }
    }

    fun updateNutritionLog(log: NutritionLog) {
        _nutritionLogs.update { list -> list.map { if (it.id == log.id) log else it } }
        scope.launch { db.nutritionDao().upsertLog(log.toEntity()) }
    }

    fun deleteNutritionLog(logId: String) {
        _nutritionLogs.update { list -> list.filter { it.id != logId } }
        scope.launch { db.nutritionDao().deleteLog(logId) }
    }

    fun clearNutritionLogs() {
        _nutritionLogs.value = emptyList()
        scope.launch { db.nutritionDao().clearAllLogs() }
    }

    // ─── Food Database (static + custom + global) ──────────────────────────

    private val _foodDatabase = MutableStateFlow<List<FoodItem>>(emptyList())
    val foodDatabase: StateFlow<List<FoodItem>> = _foodDatabase.asStateFlow()

    fun addCustomFood(food: FoodItem) {
        _foodDatabase.update { it + food }
        scope.launch { db.nutritionDao().upsertCustomFood(food.toEntity()) }
    }

    /**
     * Persists a food inferred by the local AI so future parses find it in the database
     * and skip the model inference entirely.
     *
     * Call this after receiving ParsedMealDescription.aiInferredFoods from parseFreeFormNutrition().
     * Only saves foods not already present (by normalized name) to avoid duplicates.
     */
    fun saveAiInferredFood(food: FoodItem) {
        val alreadyKnown = _foodDatabase.value.any {
            it.name.equals(food.name, ignoreCase = true) || it.id == food.id
        }
        if (alreadyKnown) return
        _foodDatabase.update { it + food }
        scope.launch { db.nutritionDao().upsertCustomFood(food.toEntity()) }
    }

    /** Convenience: saves all AI-inferred foods from a parse result. */
    fun saveAiInferredFoods(foods: List<FoodItem>) = foods.forEach { saveAiInferredFood(it) }

    /**
     * Search foods across all sources: static, custom, and global (USDA).
     */
    suspend fun searchFood(query: String): List<FoodItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        // 1. Search in local (static + custom)
        val localMatches = _foodDatabase.value.filter { 
            it.name.contains(query, ignoreCase = true) || 
            it.searchAliases.any { a -> a.contains(query, ignoreCase = true) }
        }.take(20)

        // 2. Search in massive global DB (USDA)
        val globalMatches = db.nutritionDao().searchGlobalFoods(query).map { it.toFoodItem() }

        // Combine (Local priority)
        (localMatches + globalMatches).distinctBy { it.name.lowercase() }.take(50)
    }

    // ─── Nutrition Plans ─────────────────────────────────────────────────────

    private val _nutritionPlans = MutableStateFlow<List<NutritionPlan>>(emptyList())
    val nutritionPlans: StateFlow<List<NutritionPlan>> = _nutritionPlans.asStateFlow()

    private val _activeNutritionPlanId = MutableStateFlow<String?>(null)
    val activeNutritionPlanId: StateFlow<String?> = _activeNutritionPlanId.asStateFlow()

    val activeNutritionPlan: NutritionPlan?
        get() = _nutritionPlans.value.find { it.id == _activeNutritionPlanId.value }

    fun addNutritionPlan(plan: NutritionPlan) {
        _nutritionPlans.update { it + plan }
        scope.launch { db.nutritionDao().upsertPlan(plan.toEntity()) }
    }

    fun activatePlan(planId: String) {
        _nutritionPlans.update { list -> list.map { it.copy(isActive = it.id == planId) } }
        _activeNutritionPlanId.value = planId
        scope.launch { db.nutritionDao().activatePlanAtomic(planId, _nutritionPlans.value.map { it.toEntity() }) }
    }

    // ─── User meal memory / templates ───────────────────────────────────────

    private val _mealTemplates = MutableStateFlow<List<MealTemplate>>(emptyList())
    val mealTemplates: StateFlow<List<MealTemplate>> = _mealTemplates.asStateFlow()

    // ─── Body Measurements ──────────────────────────────────────────────────
    private val measurePrefs by lazy { appContext.getSharedPreferences("body_measurements", Context.MODE_PRIVATE) }
    private val _bodyMeasurements = MutableStateFlow<List<BodyMeasurementEntry>>(emptyList())
    val bodyMeasurements: StateFlow<List<BodyMeasurementEntry>> = _bodyMeasurements.asStateFlow()

    private val _measurementSchedule = MutableStateFlow(MeasurementSchedule())
    val measurementSchedule: StateFlow<MeasurementSchedule> = _measurementSchedule.asStateFlow()

    fun addBodyMeasurement(entry: BodyMeasurementEntry) {
        _bodyMeasurements.update { it + entry }
        scope.launch {
            measurePrefs.edit()
                .putString("measurements", Json.encodeToString(_bodyMeasurements.value))
                .apply()
        }
    }

    fun updateMeasurementSchedule(schedule: MeasurementSchedule) {
        val normalized = normalizeMeasurementSchedule(schedule)
        _measurementSchedule.value = normalized
        scope.launch {
            measurePrefs.edit()
                .putString("schedule", Json.encodeToString(normalized))
                .apply()
        }

        val notifier = NutritionNotificationManager(appContext)
        if (normalized.enabled && normalized.nextDate != null) {
            notifier.scheduleMeasurementReminder(
                normalized.nextDate,
                normalized.reminderHour,
                normalized.reminderMinute,
            )
        } else {
            notifier.cancelMeasurementReminder()
        }
    }

    fun deleteBodyMeasurement(id: String) {
        _bodyMeasurements.update { it.filterNot { entry -> entry.id == id } }
        scope.launch {
            measurePrefs.edit()
                .putString("measurements", Json.encodeToString(_bodyMeasurements.value))
                .apply()
        }
    }

    fun upsertMealTemplate(template: MealTemplate) {
        _mealTemplates.update { current ->
            current.filterNot { it.id == template.id } + template
        }
        scope.launch { db.nutritionDao().upsertTemplate(template.toEntity()) }
    }

    fun rememberMealTemplateFromLog(log: NutritionLog): MealTemplate {
        if (log.foods.isEmpty()) {
            return MealTemplate(
                id = UUID.randomUUID().toString(),
                name = "Comida guardada",
                description = "Registro vacío",
                createdAt = Instant.now().toString(),
            )
        }
        val template = buildMealTemplateFromLog(log)
        upsertMealTemplate(template)
        return template
    }

    fun findMealTemplateMatch(query: String): MealTemplate? {
        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.isBlank()) return null

        return _mealTemplates.value
            .mapNotNull { template ->
                val score = scoreMealTemplate(template, normalizedQuery)
                if (score >= 0.68) template to score else null
            }
            .maxByOrNull { it.second }
            ?.first
    }

    // ─── Bootstrap ──────────────────────────────────────────────────────────

    private fun loadFromDb(context: Context) {
        scope.launch {
            try {
                // Ensure Massive Food DB is ready
                com.example.kpkn.data.food.FoodImporter.importIfEmpty(db.nutritionDao().getGlobalFoodCount() > 0, context)

                val logs = db.nutritionDao().getAllLogs().map { it.toNutritionLog() }
                val plans = db.nutritionDao().getAllPlans().map { it.toNutritionPlan() }
                val activeId = db.nutritionDao().getActiveState()?.activePlanId
                val customFoods = db.nutritionDao().getAllCustomFoods().map { it.toFoodItem() }
                val templates = db.nutritionDao().getAllTemplates().map { it.toMealTemplate() }

                val measurementsJson = measurePrefs.getString("measurements", "[]") ?: "[]"
                val scheduleJson = measurePrefs.getString("schedule", null)
                val measurements = runCatching {
                    Json.decodeFromString<List<BodyMeasurementEntry>>(measurementsJson)
                }.getOrElse { emptyList() }
                val schedule = runCatching {
                    scheduleJson?.let { Json.decodeFromString<MeasurementSchedule>(it) } ?: MeasurementSchedule()
                }.getOrElse { MeasurementSchedule() }

                _nutritionLogs.value = logs
                _nutritionPlans.value = plans
                _activeNutritionPlanId.value = activeId
                _foodDatabase.value = buildFoodDatabase() + customFoods
                _mealTemplates.value = templates
                _bodyMeasurements.value = measurements
                _measurementSchedule.value = normalizeMeasurementSchedule(schedule)

                val notifier = NutritionNotificationManager(context)
                val currentSchedule = _measurementSchedule.value
                if (currentSchedule.enabled && currentSchedule.nextDate != null) {
                    notifier.scheduleMeasurementReminder(
                        currentSchedule.nextDate,
                        currentSchedule.reminderHour,
                        currentSchedule.reminderMinute,
                    )
                } else {
                    notifier.cancelMeasurementReminder()
                }
            } catch (t: Throwable) {
                android.util.Log.e("NutritionRepository", "loadFromDb failed (OOM?): ${t.javaClass.simpleName}", t)
                _foodDatabase.value = buildFoodDatabase()
                _mealTemplates.value = emptyList()
                _bodyMeasurements.value = emptyList()
                _measurementSchedule.value = MeasurementSchedule()
                NutritionNotificationManager(appContext).cancelMeasurementReminder()
            }
        }
    }

    private fun buildMealTemplateFromLog(log: NutritionLog): MealTemplate {
        val foods = log.foods
        val totals = foods.fold(DailyMacroTotals()) { acc, food ->
            acc.copy(
                calories = acc.calories + food.calories,
                protein = acc.protein + food.protein,
                carbs = acc.carbs + food.carbs,
                fats = acc.fats + food.fats,
            )
        }
        val signature = buildMealTemplateSignature(log)
        val id = UUID.nameUUIDFromBytes(signature.toByteArray(Charsets.UTF_8)).toString()
        val now = Instant.now().toString()
        val foodNames = foods.joinToString(" + ") { it.foodName }.ifBlank { "Comida guardada" }
        val mealLabel = log.mealType.name.lowercase().replaceFirstChar { it.uppercase() }

        return MealTemplate(
            id = id,
            name = "$mealLabel · $foodNames",
            description = buildMealTemplateDescription(log),
            foods = foods,
            totalCalories = totals.calories,
            totalProtein = totals.protein,
            totalCarbs = totals.carbs,
            totalFats = totals.fats,
            createdAt = now,
        )
    }

    private fun buildMealTemplateDescription(log: NutritionLog): String {
        val foods = log.foods.joinToString(", ") { food ->
            val grams = food.amount.takeIf { it > 0 }?.let { "${formatNumber(it)}${food.unit}" }
            buildString {
                append(food.foodName)
                if (!grams.isNullOrBlank()) {
                    append(" ")
                    append(grams)
                }
            }
        }.ifBlank { "Registro de comida" }

        val calories = log.foods.sumOf { it.calories }
        val protein = log.foods.sumOf { it.protein }
        val carbs = log.foods.sumOf { it.carbs }
        val fats = log.foods.sumOf { it.fats }

        return "$foods · ${formatNumber(calories)} kcal · P${formatNumber(protein)} C${formatNumber(carbs)} G${formatNumber(fats)}"
    }

    private fun buildMealTemplateSignature(log: NutritionLog): String {
        return buildString {
            append(log.mealType.name)
            append("|")
            log.foods
                .sortedBy { normalizeSearchText(it.foodName) }
                .forEach { food ->
                    append(normalizeSearchText(food.foodName))
                    append(":")
                    append(formatNumber(food.amount))
                    append(":")
                    append(food.unit.lowercase())
                    append(";")
                }
        }
    }

    private fun scoreMealTemplate(template: MealTemplate, normalizedQuery: String): Double {
        val templateText = normalizeSearchText(
            buildString {
                append(template.name)
                if (template.description.isNotBlank()) {
                    append(" ")
                    append(template.description)
                }
                if (template.foods.isNotEmpty()) {
                    append(" ")
                    append(template.foods.joinToString(" ") { it.foodName })
                }
            }
        )

        if (templateText.isBlank()) return 0.0
        if (normalizedQuery == templateText) return 1.0
        if (templateText.contains(normalizedQuery) || normalizedQuery.contains(templateText)) return 0.92

        val queryTokens = normalizedQuery.split(" ").filter { it.length >= 3 }.distinct()
        if (queryTokens.isEmpty()) return 0.0

        val templateTokens = templateText.split(" ").toSet()
        val tokenOverlap = queryTokens.count { it in templateTokens }.toDouble() / queryTokens.size.toDouble()
        val foodOverlap = if (template.foods.isNotEmpty()) {
            template.foods.count { food ->
                val foodName = normalizeSearchText(food.foodName)
                queryTokens.any { token -> foodName.contains(token) || token.contains(foodName.take(6)) }
            }.toDouble() / template.foods.size.toDouble()
        } else 0.0

        return (tokenOverlap * 0.65) + (foodOverlap * 0.35)
    }

    private fun normalizeMeasurementSchedule(schedule: MeasurementSchedule): MeasurementSchedule {
        val today = LocalDate.now()
        val nextDate = when {
            !schedule.enabled -> null
            schedule.nextDate.isNullOrBlank() -> today.plusDays(schedule.intervalDays.toLong()).toString()
            else -> {
                val parsed = runCatching { LocalDate.parse(schedule.nextDate) }.getOrNull()
                if (parsed == null || !parsed.isAfter(today)) {
                    today.plusDays(schedule.intervalDays.toLong()).toString()
                } else schedule.nextDate
            }
        }

        return schedule.copy(nextDate = nextDate)
    }

    private fun normalizeSearchText(text: String): String {
        val stripped = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            ("%.1f".format(value)).trimEnd('0').trimEnd('.')
        }
    }

    companion object {
        @Volatile private var INSTANCE: NutritionRepository? = null
        fun init(context: Context): NutritionRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: NutritionRepository(context.applicationContext).also { INSTANCE = it; it.loadFromDb(context.applicationContext) }
        }
        fun getInstance(): NutritionRepository = INSTANCE ?: error("Not initialized")
    }
}
