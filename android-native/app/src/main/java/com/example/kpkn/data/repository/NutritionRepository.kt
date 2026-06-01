package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.FoodIndex
import com.example.kpkn.domain.nutrition.SmartFoodResolver
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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
    private val foodPrefs by lazy { appContext.getSharedPreferences("nutrition_food_catalog", Context.MODE_PRIVATE) }

    @Serializable
    private data class FoodQueryLearningEntry(
        val query: String,
        val foodId: String,
        val score: Double = 1.0,
        val updatedAt: String,
    )

    @Serializable
    data class FoodCatalogMeta(
        val version: Int,
        val checksum: String,
        val importedAt: String,
    )

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

    // Phase B: SmartFoodResolver lazy-init
    private var _foodIndex: FoodIndex? = null
    private val foodIndex: FoodIndex
        get() = _foodIndex ?: FoodIndex().also { idx ->
            _foodIndex = idx
        }

    private var _smartResolver: SmartFoodResolver? = null
    private val smartResolver: SmartFoodResolver
        get() = _smartResolver ?: SmartFoodResolver(db.nutritionDao(), foodIndex, db.learnedResolutionDao()).also { resolver ->
            _smartResolver = resolver
            // Preload learned resolutions from DB
            kotlinx.coroutines.GlobalScope.launch {
                resolver.preloadLearned()
            }
        }

    private val _foodQueryLearning = MutableStateFlow<Map<String, FoodQueryLearningEntry>>(emptyMap())

    fun addCustomFood(food: FoodItem) {
        val normalized = normalizeFoodItem(food)
        _foodDatabase.update { current ->
            val filtered = current.filterNot { it.id == normalized.id }
            filtered + normalized
        }
        scope.launch { db.nutritionDao().upsertCustomFood(normalized.toEntity()) }
    }

    /**
     * Persists a food inferred by the local AI so future parses find it in the database
     * and skip the model inference entirely.
     *
     * Call this after receiving ParsedMealDescription.aiInferredFoods from parseFreeFormNutrition().
     * Only saves foods not already present (by normalized name) to avoid duplicates.
     */
    fun saveAiInferredFood(food: FoodItem) {
        val normalizedFood = normalizeFoodItem(food)
        // If the inferred name maps to an existing static food, prefer the curated DB entry.
        // This avoids storing noisy duplicates (e.g., "arroz") that can carry unstable macros.
        if (findFoodByNormalized(normalizedFood.name) != null) return
        val alreadyKnown = _foodDatabase.value.any {
            val knownName = it.normalizedName ?: normalizeSearchText(it.name)
            knownName == (normalizedFood.normalizedName ?: "") || it.id == normalizedFood.id
        }
        if (alreadyKnown) return
        _foodDatabase.update { it + normalizedFood }
        scope.launch { db.nutritionDao().upsertCustomFood(normalizedFood.toEntity()) }
    }

    /** Convenience: saves all AI-inferred foods from a parse result. */
    fun saveAiInferredFoods(foods: List<FoodItem>) = foods.forEach { saveAiInferredFood(it) }

    /**
     * Search foods across all sources: static, custom, and global (USDA).
     */
    suspend fun searchFood(query: String): List<FoodItem> = withContext(Dispatchers.IO) {
        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.isBlank()) return@withContext emptyList()

        val queryTokens = tokenize(normalizedQuery)
        if (queryTokens.isEmpty()) return@withContext emptyList()

        val localFoods = _foodDatabase.value.map(::normalizeFoodItem)

        val customMatches = db.nutritionDao()
            .searchCustomFoods(normalizedQuery, 120)
            .map { normalizeFoodItem(it.toFoodItem()) }

        val normalizedGlobal = db.nutritionDao()
            .searchGlobalFoodsNormalized(normalizedQuery, 150)
            .map { normalizeFoodItem(it.toFoodItem()) }

        val ftsQuery = buildFtsQuery(queryTokens)
        val ftsGlobal = if (ftsQuery.isNotBlank()) {
            runCatching { db.nutritionDao().searchGlobalFoodsWithFts(ftsQuery) }
                .getOrDefault(emptyList())
                .map { normalizeFoodItem(it.toFoodItem()) }
        } else {
            emptyList()
        }

        val fallbackGlobal = runCatching {
            db.nutritionDao().searchGlobalFoods(query).map { normalizeFoodItem(it.toFoodItem()) }
        }.getOrDefault(emptyList())

        val merged = (localFoods + customMatches + normalizedGlobal + ftsGlobal + fallbackGlobal)
            .associateBy { it.id.ifBlank { "${it.normalizedName}|${it.normalizedBrand}|${it.sourcePriority}" } }
            .values
            .toList()

        val learned = _foodQueryLearning.value

        merged
            .mapNotNull { food ->
                buildFoodCandidate(
                    food = food,
                    normalizedQuery = normalizedQuery,
                    queryTokens = queryTokens,
                    learnedEntry = learned[normalizedQuery],
                )
            }
            .sortedByDescending { it.score }
            .take(50)
            .map { it.food }
    }

    suspend fun searchFoodCandidates(query: String, limit: Int = 50): List<FoodCandidate> = withContext(Dispatchers.IO) {
        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.isBlank()) return@withContext emptyList()
        val queryTokens = tokenize(normalizedQuery)
        if (queryTokens.isEmpty()) return@withContext emptyList()

        val foods = searchFood(query)
        val learned = _foodQueryLearning.value[normalizedQuery]
        foods
            .mapNotNull { food ->
                buildFoodCandidate(
                    food = normalizeFoodItem(food),
                    normalizedQuery = normalizedQuery,
                    queryTokens = queryTokens,
                    learnedEntry = learned,
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    fun recordFoodSelection(query: String, food: FoodItem) {
        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.isBlank()) return

        val normalizedFood = normalizeFoodItem(food)
        val now = Instant.now().toString()

        _foodQueryLearning.update { current ->
            val prev = current[normalizedQuery]
            val updated = FoodQueryLearningEntry(
                query = normalizedQuery,
                foodId = normalizedFood.id,
                score = ((prev?.score ?: 0.0) + 1.0).coerceAtMost(8.0),
                updatedAt = now,
            )
            current + (normalizedQuery to updated)
        }
        persistFoodLearning()

        scope.launch {
            runCatching {
                db.nutritionDao().incrementCustomFoodUsage(normalizedFood.id, now)
            }
            runCatching {
                db.nutritionDao().incrementGlobalFoodUsage(normalizedFood.id, now)
            }
        }
    }

    fun setActiveNutritionPlanId(planId: String?) {
        _activeNutritionPlanId.value = planId
        scope.launch {
            if (planId == null) db.nutritionDao().clearActiveState()
            else db.nutritionDao().upsertActiveState(NutritionActiveStateEntity(activePlanId = planId))
        }
    }

    // ─── Nutrition Plans ─────────────────────────────────────────────────────

    private val _nutritionPlans = MutableStateFlow<List<NutritionPlan>>(emptyList())
    val nutritionPlans: StateFlow<List<NutritionPlan>> = _nutritionPlans.asStateFlow()

    private val _activeNutritionPlanId = MutableStateFlow<String?>(null)
    val activeNutritionPlanId: StateFlow<String?> = _activeNutritionPlanId.asStateFlow()

    val activeNutritionPlan: NutritionPlan?
        get() = _nutritionPlans.value.find { it.id == _activeNutritionPlanId.value }

    fun addNutritionPlan(plan: NutritionPlan) {
        _nutritionPlans.update { plans ->
            val existingIndex = plans.indexOfFirst { it.id == plan.id }
            if (existingIndex >= 0) {
                plans.toMutableList().apply { this[existingIndex] = plan }
            } else {
                plans + plan
            }
        }
        scope.launch { db.nutritionDao().upsertPlan(plan.toEntity()) }
    }

    fun deleteNutritionPlan(planId: String) {
        _nutritionPlans.update { list -> list.filter { it.id != planId } }
        if (_activeNutritionPlanId.value == planId) {
            _activeNutritionPlanId.value = null
            scope.launch { db.nutritionDao().clearActiveState() }
        }
        scope.launch { db.nutritionDao().deletePlan(planId) }
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

    // ─── SmartFoodResolver Integration (Phase B) ────────────────────────────────

    private val foodIndexLock = Any()

    suspend fun initFoodIndex() = withContext(Dispatchers.Default) {
        synchronized(foodIndexLock) {
            if (_foodIndex?.isBuilt() == true) return@withContext
        }
        try {
            val globalFoods = withContext(Dispatchers.IO) {
                db.nutritionDao().getAllGlobalFoods()
            }
            android.util.Log.i("NutritionRepository", "Building FoodIndex with ${globalFoods.size} global + ${_foodDatabase.value.size} static foods")
            synchronized(foodIndexLock) {
                foodIndex.build(globalFoods, _foodDatabase.value)
            }
            android.util.Log.i("NutritionRepository", "FoodIndex built: ${foodIndex.size()} foods indexed")
        } catch (e: Exception) {
            android.util.Log.w("NutritionRepository", "initFoodIndex failed", e)
        }
    }

    suspend fun resolveFoodWithSmartResolver(
        query: String,
        brandHint: String? = null,
    ): SmartFoodResolver.ResolutionResult {
        // Ensure index is built synchronously before resolving
        initFoodIndex()
        return smartResolver.resolve(query, brandHint)
    }

    fun recordLearnedResolution(
        query: String,
        brandHint: String?,
        foodId: String,
        portionGrams: Double?,
        cookingMethod: String?,
    ) {
        smartResolver.recordLearned(query, brandHint, foodId, portionGrams, cookingMethod)
    }

    /**
     * Look up a food by ID across all sources: static, custom, and global.
     */
    suspend fun getFoodById(foodId: String): FoodItem? = withContext(Dispatchers.IO) {
        val cached = _foodDatabase.value.find { it.id == foodId }
        if (cached != null) return@withContext cached

        // Check global foods (USDA/OFF)
        val global = runCatching {
            db.nutritionDao().searchGlobalFoods(foodId).firstOrNull()?.toFoodItem()
        }.getOrNull()
        global
    }

    // ─── Bootstrap ──────────────────────────────────────────────────────────

    private fun loadFromDb(context: Context) {
        scope.launch {
            try {
                // Ensure Massive Food DB is ready
                val globalCount = db.nutritionDao().getGlobalFoodCount()
                val imported = com.example.kpkn.data.food.FoodImporter.importIfNeeded(
                    db = db,
                    context = context,
                    alreadyImported = globalCount > 0,
                    existingMeta = loadFoodCatalogMeta()?.let {
                        com.example.kpkn.data.food.FoodImporter.ImportMetadata(
                            version = it.version,
                            checksum = it.checksum,
                            importedAt = it.importedAt,
                        )
                    },
                    onMetaUpdated = { meta ->
                        saveFoodCatalogMeta(
                            FoodCatalogMeta(
                                version = meta.version,
                                checksum = meta.checksum,
                                importedAt = meta.importedAt,
                            )
                        )
                    },
                )
                if (imported) {
                    android.util.Log.i("NutritionRepository", "Food catalog importado/actualizado")
                }

                val logs = db.nutritionDao().getAllLogs().map { it.toNutritionLog() }
                val plans = db.nutritionDao().getAllPlans().map { it.toNutritionPlan() }
                val activeId = db.nutritionDao().getActiveState()?.activePlanId
                val customFoods = db.nutritionDao().getAllCustomFoods()
                    .map { it.toFoodItem() }
                    .filterNot { custom ->
                        // Drop stale AI-inferred duplicates when a curated static entry exists.
                        custom.isAiInferred && findFoodByNormalized(custom.name) != null
                    }
                    .map(::normalizeFoodItem)
                val templates = db.nutritionDao().getAllTemplates().map { it.toMealTemplate() }
                val learning = loadFoodLearning()

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
                _foodDatabase.value = (buildFoodDatabase() + customFoods)
                    .map(::normalizeFoodItem)
                    .distinctBy { it.id.ifBlank { it.normalizedName ?: it.name.lowercase() } }
                _mealTemplates.value = templates
                _foodQueryLearning.value = learning
                _bodyMeasurements.value = measurements
                _measurementSchedule.value = normalizeMeasurementSchedule(schedule)

                // Initialize Phase B FoodIndex proactively in the background
                launch(Dispatchers.Default) {
                    initFoodIndex()
                }

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
                if (t is CancellationException) throw t
                android.util.Log.e("NutritionRepository", "loadFromDb failed (OOM?): ${t.javaClass.simpleName}", t)
                _foodDatabase.value = buildFoodDatabase()
                _mealTemplates.value = emptyList()
                _foodQueryLearning.value = emptyMap()
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

    private fun tokenize(normalizedText: String): List<String> = normalizedText
        .split(" ")
        .map { it.trim() }
        .filter { it.length >= 2 }
        .distinct()

    private fun normalizeFoodItem(food: FoodItem): FoodItem {
        val normalizedName = food.normalizedName ?: normalizeSearchText(food.name)
        val normalizedBrand = food.normalizedBrand ?: food.brand?.let(::normalizeSearchText)
        val normalizedAliases = food.searchAliases
            .map(::normalizeSearchText)
            .filter { it.isNotBlank() }
            .distinct()

        val withDefaults = food.copy(
            normalizedName = normalizedName,
            normalizedBrand = normalizedBrand,
            searchAliases = normalizedAliases,
        )

        val sourcePriority = if (withDefaults.sourcePriority != 50) {
            withDefaults.sourcePriority
        } else {
            when {
                withDefaults.isCustom -> 95
                withDefaults.tags.any { it.contains("OFF", ignoreCase = true) } -> 80
                withDefaults.tags.any { it.contains("USDA", ignoreCase = true) } -> 70
                else -> 60
            }
        }

        val verifiedScore = if (withDefaults.verifiedScore != 0.5) {
            withDefaults.verifiedScore
        } else {
            when {
                withDefaults.isCustom -> 0.9
                withDefaults.tags.any { it.contains("USDA", ignoreCase = true) } -> 0.85
                withDefaults.tags.any { it.contains("OFF", ignoreCase = true) } -> 0.72
                else -> 0.6
            }
        }

        return withDefaults.copy(
            sourcePriority = sourcePriority,
            verifiedScore = verifiedScore,
        )
    }

    private fun buildFtsQuery(tokens: List<String>): String {
        if (tokens.isEmpty()) return ""
        return tokens
            .joinToString(separator = " ") { "$it*" }
            .trim()
    }

    private fun buildFoodCandidate(
        food: FoodItem,
        normalizedQuery: String,
        queryTokens: List<String>,
        learnedEntry: FoodQueryLearningEntry?,
    ): FoodCandidate? {
        val normalizedName = food.normalizedName ?: normalizeSearchText(food.name)
        val normalizedBrand = food.normalizedBrand ?: food.brand?.let(::normalizeSearchText)
        val aliases = food.searchAliases.map(::normalizeSearchText).filter { it.isNotBlank() }
        val fields = buildList {
            add(normalizedName)
            if (!normalizedBrand.isNullOrBlank()) add(normalizedBrand)
            addAll(aliases)
        }

        val nameExact = normalizedName == normalizedQuery
        val aliasExact = aliases.any { it == normalizedQuery }
        val nameContains = normalizedName.contains(normalizedQuery)
        val aliasContains = aliases.any { it.contains(normalizedQuery) }
        val brandContains = !normalizedBrand.isNullOrBlank() && normalizedBrand.contains(normalizedQuery)

        val tokenHits = queryTokens.count { token ->
            fields.any { field -> field.split(" ").contains(token) || field.contains(token) }
        }
        if (!(nameExact || aliasExact || nameContains || aliasContains || tokenHits > 0 || brandContains)) {
            return null
        }

        val coverage = if (queryTokens.isEmpty()) 0.0 else tokenHits.toDouble() / queryTokens.size.toDouble()
        val precisionDenom = normalizedName.split(" ").filter { it.isNotBlank() }.size.coerceAtLeast(1)
        val precision = (tokenHits.toDouble() / precisionDenom.toDouble()).coerceIn(0.0, 1.0)

        val exactBoost = when {
            nameExact || aliasExact -> 0.45
            nameContains || aliasContains -> 0.25
            else -> 0.0
        }

        val sourceScore = (food.sourcePriority.coerceIn(0, 100) / 100.0) * 0.2
        val verifiedScore = food.verifiedScore.coerceIn(0.0, 1.0) * 0.2
        val usageScore = (kotlin.math.ln((food.usageCount + 1).toDouble()) / kotlin.math.ln(10.0)).coerceIn(0.0, 1.0) * 0.08
        val learnedScore = when {
            learnedEntry != null && learnedEntry.foodId == food.id -> 0.22
            else -> 0.0
        }

        val score = (coverage * 0.32) + (precision * 0.14) + exactBoost + sourceScore + verifiedScore + usageScore + learnedScore

        val confidence = when {
            score >= 0.82 -> SearchConfidence.HIGH
            score >= 0.58 -> SearchConfidence.MEDIUM
            else -> SearchConfidence.LOW
        }

        val source = when {
            food.tags.any { it.contains("OFF", ignoreCase = true) } -> SearchSource.OFF
            food.tags.any { it.contains("USDA", ignoreCase = true) } -> SearchSource.USDA
            else -> SearchSource.LOCAL
        }

        return FoodCandidate(
            foodId = food.id,
            displayName = food.name,
            score = score,
            confidence = confidence,
            source = source,
            food = food,
            trace = buildList {
                if (nameExact || aliasExact) add("exact")
                if (brandContains) add("brand")
                if (learnedEntry?.foodId == food.id) add("learned")
            },
            queryCoverage = coverage,
            tokenPrecision = precision,
            brandMatched = brandContains,
            learned = learnedEntry?.foodId == food.id,
        )
    }

    private fun persistFoodLearning() {
        val payload = runCatching {
            Json.encodeToString(_foodQueryLearning.value.values.toList())
        }.getOrDefault("[]")
        foodPrefs.edit().putString("food_query_learning", payload).apply()
    }

    private fun loadFoodLearning(): Map<String, FoodQueryLearningEntry> {
        val payload = foodPrefs.getString("food_query_learning", "[]") ?: "[]"
        val list = runCatching {
            Json.decodeFromString<List<FoodQueryLearningEntry>>(payload)
        }.getOrDefault(emptyList())
        return list.associateBy { it.query }
    }

    private fun saveFoodCatalogMeta(meta: FoodCatalogMeta) {
        val encoded = runCatching { Json.encodeToString(meta) }.getOrDefault("")
        foodPrefs.edit().putString("food_catalog_meta", encoded).apply()
    }

    private fun loadFoodCatalogMeta(): FoodCatalogMeta? {
        val encoded = foodPrefs.getString("food_catalog_meta", null) ?: return null
        return runCatching { Json.decodeFromString<FoodCatalogMeta>(encoded) }.getOrNull()
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
