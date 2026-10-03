package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.food.DatasetKnowledgeStore
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.food.FoodKnowledgeStore
import com.example.kpkn.data.persistence.PersistenceWriteCoordinator
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.staticFoodForAlias
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.FoodIndex
import com.example.kpkn.domain.nutrition.FoodKnowledge
import com.example.kpkn.domain.nutrition.FoodState
import com.example.kpkn.domain.nutrition.FoodIdentity
import com.example.kpkn.domain.nutrition.FoodSearchRanker
import com.example.kpkn.domain.nutrition.FoodTemplateMatcher
import com.example.kpkn.domain.nutrition.HouseholdPortions
import com.example.kpkn.domain.nutrition.NutritionGoalResolver
import com.example.kpkn.domain.nutrition.NutritionGoalSource
import com.example.kpkn.domain.nutrition.SemanticPortionRetriever
import com.example.kpkn.domain.nutrition.SmartFoodResolver
import com.example.kpkn.domain.nutrition.SubjectivePortionEngine
import com.example.kpkn.domain.nutrition.TextKeys
import com.example.kpkn.domain.nutrition.FoodLearningConfirmation
import com.example.kpkn.domain.nutrition.NutritionCalibrationWizardEngine
import com.example.kpkn.domain.nutrition.dailyGoalSnapshotOf
import com.example.kpkn.domain.nutrition.planDayTargetForDate
import androidx.room.withTransaction
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import com.example.kpkn.telemetry.nutrition.NutritionTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Seam de arranque del catálogo global (WP-S3/U2): misma forma que [FoodImporter.importIfNeeded]. En producción es
 * [Default]; las pruebas inyectan uno suspendido o que lanza para demostrar que las filas del usuario se publican
 * antes de la importación y con independencia de ella.
 */
internal interface FoodCatalogImporter {
    suspend fun importIfNeeded(
        db: KpknDatabase,
        context: Context,
        alreadyImported: Boolean,
        existingMeta: FoodImporter.ImportMetadata?,
        onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
    ): Boolean

    companion object {
        /** Importador de producción: delega en [FoodImporter.importIfNeeded]. */
        val Default: FoodCatalogImporter = object : FoodCatalogImporter {
            override suspend fun importIfNeeded(
                db: KpknDatabase,
                context: Context,
                alreadyImported: Boolean,
                existingMeta: FoodImporter.ImportMetadata?,
                onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
            ): Boolean = FoodImporter.importIfNeeded(db, context, alreadyImported, existingMeta, onMetaUpdated)
        }
    }
}

/**
 * NutritionRepository — Write-through cache para estado nutricional.
 */
class NutritionRepository private constructor(
    context: Context,
    private val db: KpknDatabase,
    private val ownsDatabase: Boolean,
    /** Seam de arranque (WP-S3/U2): ver [FoodCatalogImporter]. */
    private val catalogImporter: FoodCatalogImporter,
) {
    /**
     * Forma `(Context, KpknDatabase, Boolean)` conservada: varias pruebas construyen el repositorio por reflexión con
     * esa firma exacta (NutritionDurableSaveTest, SettingsJsonBackupTest).
     */
    private constructor(
        context: Context,
        db: KpknDatabase = KpknDatabase.getInstance(context),
        ownsDatabase: Boolean = false,
    ) : this(context, db, ownsDatabase, FoodCatalogImporter.Default)

    /** Normalized body observations are shared with the body feature and never stored in Settings. */
    private val normalizedBodyRepository by lazy { BodyProgressRepository.getInstance(appContext) }
    val bodyProgressRepository: BodyProgressRepository
        get() = normalizedBodyRepository

    private val appContext = context.applicationContext
    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + repositoryJob)
    private val foodSaveMutex = Mutex()
    /** Serializa las importaciones del catálogo global (el arranque y un `refreshData` pueden solaparse). */
    private val catalogImportMutex = Mutex()
    private val foodCalibration by lazy { NutritionCalibrationRepository.forDatabase(appContext, db) }
    private val foodPrefs by lazy { appContext.getSharedPreferences("nutrition_food_catalog", Context.MODE_PRIVATE) }

    /** Solo para pruebas JVM: la base con la que trabaja este repositorio (mismo patrón que ProgramRepository). */
    internal fun databaseForTests(): KpknDatabase = db

    // ─── IT3: utensilios configurables (ml por utensilio) ────────────────────

    private val utensilPrefs by lazy { appContext.getSharedPreferences("nutrition_utensils", Context.MODE_PRIVATE) }

    /** Aplica los overrides persistidos al motor de porciones (una vez al iniciar). */
    fun loadUtensilOverrides() {
        val overrides = utensilPrefs.all
            .filterKeys { it.startsWith("ml_") }
            .mapNotNull { (key, value) ->
                val name = key.removePrefix("ml_")
                val ml = (value as? Number)?.toDouble()
                if (ml != null && ml > 0) name to ml else null
            }
            .toMap()
        SubjectivePortionEngine.applyUtensilOverrides(overrides)
    }

    /** Guarda el volumen de un utensilio y lo aplica de inmediato. */
    fun saveUtensilOverride(name: String, ml: Double) {
        utensilPrefs.edit().putFloat("ml_$name", ml.toFloat()).apply()
        SubjectivePortionEngine.applyUtensilOverrides(SubjectivePortionEngine.currentUtensilOverrides() + (name to ml))
    }

    /**
     * Devuelve un utensilio a su volumen base (WP-U12, C11): quita el override guardado y el del motor de porciones.
     * Un valor que el usuario no cambió no debe quedar fijado como si lo hubiera elegido (congelaría la base).
     */
    fun clearUtensilOverride(name: String) {
        utensilPrefs.edit().remove("ml_$name").apply()
        SubjectivePortionEngine.applyUtensilOverrides(SubjectivePortionEngine.currentUtensilOverrides() - name)
    }

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

    /** Backup hooks: custom foods and catalog provenance are user data, not UI-only cache. */
    suspend fun getCustomFoodsForBackup(): List<FoodItem> = withContext(Dispatchers.IO) {
        db.nutritionDao().getAllCustomFoods().mapNotNull { entity ->
            runCatching { entity.toFoodItem() }.getOrNull()
        }
    }

    fun getFoodCatalogMetaForBackup(): FoodCatalogMeta? = loadFoodCatalogMeta()

    fun restoreFoodCatalogMeta(meta: FoodCatalogMeta) {
        saveFoodCatalogMeta(meta)
    }

    // ─── Nutrition Logs ──────────────────────────────────────────────────────

    private val _nutritionLogs = MutableStateFlow<List<NutritionLog>>(emptyList())
    val nutritionLogs: StateFlow<List<NutritionLog>> = _nutritionLogs.asStateFlow()

    private val _dailyGoalSnapshots = MutableStateFlow<List<DailyGoalSnapshot>>(emptyList())
    /** Immutable per-day targets exposed for historical charts and audits. */
    val dailyGoalSnapshots: StateFlow<List<DailyGoalSnapshot>> = _dailyGoalSnapshots.asStateFlow()

    fun addNutritionLog(log: NutritionLog) {
        _nutritionLogs.update { it + log }
        scope.launch { db.nutritionDao().upsertLog(log.toEntity()) }
        captureDailyGoalSnapshot(log.date.take(10))
    }

    /** Await durable storage before publishing success or teaching a confirmed habit. */
    suspend fun saveNutritionLog(log: NutritionLog, confirmations: List<FoodLearningConfirmation> = emptyList()) = foodSaveMutex.withLock {
        require(log.foods.isNotEmpty()) { "A meal must contain every active food" }
        require(log.foods.all { food ->
            food.amount.isFinite() && food.amount > 0.0 &&
                listOf(food.calories, food.protein, food.carbs, food.fats).all { it.isFinite() && it >= 0.0 }
        }) { "Invalid food amount or nutrients" }
        withContext(Dispatchers.IO) {
            val alreadyStored = db.nutritionDao().getLogsForDate(log.date).any { it.id == log.id }
            // El objetivo histórico pasa por el resolvedor canónico: un registro
            // retroactivo (o su edición/borrado) jamás captura el plan activo
            // actual; sin evidencia del objetivo que estuvo vigente se conserva
            // la ausencia histórica. Los ceros explícitos persisten como ceros.
            val snapshot = resolvedDailyGoalSnapshot(log.date.take(10), activeNutritionPlan)
            val snapshotInserted = db.withTransaction {
                db.nutritionDao().upsertLog(log.toEntity())
                if (snapshot != null) db.nutritionDao().insertDailyGoalSnapshot(snapshot.toEntity()) != -1L else false
            }
            // Publish only committed rows, with no background writes left behind.
            _nutritionLogs.update { current -> current.filterNot { it.id == log.id } + log }
            if (snapshotInserted && snapshot != null) _dailyGoalSnapshots.update { current -> current.filterNot { it.date == snapshot.date } + snapshot }
            // A failed optional habit write must not report that the durable meal failed.
            try {
                for (confirmation in confirmations.takeUnless { alreadyStored }.orEmpty()) {
                    if (confirmation.dimensions.isEmpty()) continue
                    if ("identity" in confirmation.dimensions) {
                        smartResolver.recordLearned(confirmation.query, null, confirmation.foodId, null, null)
                        getFoodById(confirmation.foodId)?.let { recordFoodSelection(confirmation.query, it) }
                    }
                    foodCalibration.update { stored ->
                        var profile = stored
                        if ("identity" in confirmation.dimensions) profile = NutritionCalibrationWizardEngine.recordConfirmedIdentity(profile, confirmation.query, confirmation.foodId)
                        if ("portion" in confirmation.dimensions) confirmation.portionGrams?.let {
                            profile = NutritionCalibrationWizardEngine.recordConfirmedPortion(profile, confirmation.family, it)
                        }
                        if ("state" in confirmation.dimensions) {
                            profile = NutritionCalibrationWizardEngine.recordConfirmedState(profile, confirmation.family, confirmation.weightBasis)
                            profile = NutritionCalibrationWizardEngine.recordConfirmedState(profile, FoodIdentity.normalize(confirmation.query), confirmation.weightBasis)
                        }
                        if ("oil" in confirmation.dimensions) confirmation.oilGramsPer100?.let {
                            profile = NutritionCalibrationWizardEngine.recordConfirmedOil(profile, confirmation.family, it)
                            profile = NutritionCalibrationWizardEngine.recordConfirmedOil(profile, FoodIdentity.normalize(confirmation.query), it)
                        }
                        profile
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.w("NutritionRepository", "Meal saved; confirmed habit could not be updated", error)
            }
        }
    }

    /**
     * Guarda una comida ya registrada tal cual, esperando la escritura (WP-U11: editar una comida y deshacer un borrado).
     * Es el mismo upsert por id de [saveNutritionLog] y sin confirmaciones que aprender: editar o restaurar nunca entrena
     * un hábito. Un fallo se propaga al llamador, que se lo muestra al usuario.
     */
    suspend fun updateNutritionLog(log: NutritionLog) {
        saveNutritionLog(log, emptyList())
    }

    fun deleteNutritionLog(logId: String) {
        scope.launch {
            runCatching { deleteNutritionLogAndAwait(logId) }
        }
    }

    /**
     * Borra una comida y devuelve la que se borró (null si ya no existía) para poder deshacerlo (WP-U11). Un fallo de la
     * base se propaga: nunca se informa de un borrado que no ocurrió.
     */
    suspend fun deleteNutritionLogAndAwait(logId: String): NutritionLog? = foodSaveMutex.withLock {
        withContext(Dispatchers.IO) {
            // La fila que el usuario vio es la que vuelve al deshacer: primero la lista publicada, si no la guardada.
            val existing = _nutritionLogs.value.firstOrNull { it.id == logId }
                ?: db.nutritionDao().getAllLogs().firstOrNull { it.id == logId }
                    ?.let { entity -> runCatching { entity.toNutritionLog() }.getOrNull() }
            db.withTransaction { db.nutritionDao().deleteLog(logId) }
            _nutritionLogs.update { list -> list.filter { it.id != logId } }
            existing?.date?.take(10)?.let { captureDailyGoalSnapshot(it) }
            existing
        }
    }

    fun clearNutritionLogs() {
        _nutritionLogs.value = emptyList()
        scope.launch { db.nutritionDao().clearAllLogs() }
    }

    // ─── Food Database (static + custom + global) ──────────────────────────

    private val _foodDatabase = MutableStateFlow<List<FoodItem>>(emptyList())
    val foodDatabase: StateFlow<List<FoodItem>> = _foodDatabase.asStateFlow()

    /**
     * Progreso (0..1) de la importación del catálogo global; null si no hay ninguna en curso (WP-S8). El selector de
     * alimentos lo muestra en la pestaña Buscar mientras el catálogo se prepara.
     */
    val catalogImportProgress: StateFlow<Float?> = FoodImporter.importProgress

    // Phase B: SmartFoodResolver lazy-init. `by lazy` is synchronized: two threads can never end up with two FoodIndex
    // instances (the one the resolver reads and another one `initFoodIndex` fills). The instance never changes for the
    // life of the repository; what changes is its content, through FoodIndex.build (WP-S4).
    private val foodIndex: FoodIndex by lazy { FoodIndex() }

    private val smartResolver: SmartFoodResolver by lazy {
        SmartFoodResolver(db.nutritionDao(), foodIndex, db.learnedResolutionDao()).also { resolver ->
            // Preload learned resolutions from DB
            scope.launch {
                resolver.preloadLearned()
            }
        }
    }

    /**
     * Estado del catálogo del que se alimenta [foodIndex] (WP-S4): sube cada vez que cambia lo que el índice debe
     * contener (estado publicado, catálogo importado, recarga desde disco). [initFoodIndex] reconstruye mientras la
     * generación del índice difiera de esta.
     */
    private val catalogGeneration = AtomicInteger()

    private fun bumpCatalogGeneration() {
        catalogGeneration.incrementAndGet()
    }

    /** Solo para pruebas JVM: el índice del resolvedor y la generación del catálogo contra la que se compara. */
    internal fun foodIndexForTests(): FoodIndex = foodIndex

    internal fun catalogGenerationForTests(): Int = catalogGeneration.get()

    private val _foodQueryLearning = MutableStateFlow<Map<String, FoodQueryLearningEntry>>(emptyMap())

    fun addCustomFood(food: FoodItem) {
        val normalized = normalizeFoodItem(food)
        _foodDatabase.update { current ->
            val filtered = current.filterNot { it.id == normalized.id }
            filtered + normalized
        }
        // E16/IT2: el resolver debe ver los alimentos del usuario.
        foodIndex.addStaticFood(normalized)
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
            val knownName = it.normalizedName ?: TextKeys.normalize(it.name)
            knownName == (normalizedFood.normalizedName ?: "") || it.id == normalizedFood.id
        }
        if (alreadyKnown) return
        _foodDatabase.update { it + normalizedFood }
        // E16/IT2: los alimentos inferidos también entran al índice del resolver.
        foodIndex.addStaticFood(normalizedFood)
        scope.launch { db.nutritionDao().upsertCustomFood(normalizedFood.toEntity()) }
    }

    /** Convenience: saves all AI-inferred foods from a parse result. */
    fun saveAiInferredFoods(foods: List<FoodItem>) = foods.forEach { saveAiInferredFood(it) }

    /**
     * Search foods across all sources (static, custom and global USDA/OFF), best first. Same ranking as
     * [searchFoodCandidates]; kept for callers that only need the rows.
     */
    suspend fun searchFood(query: String): List<FoodItem> = searchFoodCandidates(query).map { it.food }

    /**
     * Ranked search of the catalog (WP-S2). ONE scoring pass over a pool that gathers the static catalog and the user's
     * foods (kept pre-normalized between searches), the rows the DAO retrieves for the query and for its expansion terms,
     * with duplicates collapsed; [FoodSearchRanker] then scores and orders it. With [loggerFilter] only rows the logger
     * may accept (verified nutrients and the identity the person declared) are returned, and the filter runs BEFORE
     * [limit], so the screen is never short of rows because rejects took their places.
     */
    suspend fun searchFoodCandidates(
        query: String,
        limit: Int = 50,
        loggerFilter: Boolean = false,
    ): List<FoodCandidate> = withContext(Dispatchers.IO) {
        val normalizedQuery = TextKeys.normalize(query)
        if (normalizedQuery.isBlank()) return@withContext emptyList()

        // The ranker's anchor: the alias table (by id, WP-S6) first, then the household default of the query.
        val anchor = staticFoodForAlias(query) ?: HouseholdPortions.householdStaticFood(query)
        val q = FoodSearchRanker.query(query, anchor?.id)
        if (q.tokens.isEmpty()) return@withContext emptyList()

        FoodSearchRanker.rank(
            q = q,
            pool = searchPool(q, normalizedQuery),
            learnedFoodId = _foodQueryLearning.value[normalizedQuery]?.foodId,
            limit = limit,
            loggerFilter = loggerFilter,
        )
    }

    /** The static catalog and user foods, normalized once per published catalog (see [localSearchPool]). */
    private class SearchPool(val source: List<FoodItem>, val foods: List<FoodItem>)

    @Volatile
    private var searchPoolCache: SearchPool? = null

    private fun localSearchPool(): SearchPool {
        val published = _foodDatabase.value
        searchPoolCache?.takeIf { it.source === published }?.let { return it }
        return SearchPool(published, published.map(::normalizeFoodItem)).also { searchPoolCache = it }
    }

    private suspend fun searchPool(q: FoodSearchRanker.Query, normalizedQuery: String): List<FoodItem> {
        val dao = db.nutritionDao()
        // The reads are independent LIKE scans of the same tables: they run side by side, and the rows are merged in the
        // order of the terms so the pool never depends on which read finished first.
        val (custom, retrievedBatches) = coroutineScope {
            val customRead = async {
                searchRead<List<CustomFoodEntity>>(emptyList()) { dao.searchCustomFoods(normalizedQuery, SEARCH_CUSTOM_LIMIT) }
            }
            val globalReads = searchTerms(q, normalizedQuery).map { (term, termLimit) ->
                async { searchRead<List<GlobalFoodEntity>>(emptyList()) { dao.searchGlobalFoodsNormalized(term, termLimit) } }
            }
            customRead.await() to globalReads.awaitAll()
        }
        val retrieved = LinkedHashMap<String, GlobalFoodEntity>()
        retrievedBatches.forEach { rows -> rows.forEach { retrieved.putIfAbsent(it.foodId, it) } }

        val retrievedFoods = custom.map { normalizeFoodItem(it.toFoodItem()) } + retrieved.values.map { normalizeFoodItem(it.toFoodItem()) }
        return FoodSearchRanker.collapseDuplicates(localSearchPool().foods + retrievedFoods, q.anchorId)
    }

    /**
     * What the DAO is asked for, as (term, row limit): the whole phrase first, then terms that widen retrieval to what
     * the ranker can still match. Rows are retrieved with `LIKE '%term%'` (a superset of the ranker's whole-word and
     * prefix hits, which then decide): the alias the catalog declares for the query ("banana" -> plátano), the family
     * aliases ("pan" -> hallulla, marraqueta...), and the stems / words of the query ("huevos" -> "huevo", "leche colun" ->
     * "leche", "colun": a phrase LIKE alone never finds a product whose brand sits in another column). At most
     * [MAX_SEARCH_TERMS] terms; words that only state a cooking state never widen the search.
     */
    private fun searchTerms(q: FoodSearchRanker.Query, normalizedQuery: String): List<Pair<String, Int>> {
        val identifyingStems = q.stems.filterIndexed { index, _ -> q.identifying[index] }
        val terms = buildList {
            FOOD_ALIASES[q.raw.trim().lowercase()]?.let { add(it) }
            FOOD_ALIASES[normalizedQuery]?.let { add(it) }
            addAll(FoodIdentity.queryAliases(q.raw))
            if (q.tokens.size > 1) addAll(identifyingStems) else addAll(identifyingStems.filter { it != q.tokens[0] })
        }.map(TextKeys::normalize).filter { it.isNotBlank() && it != normalizedQuery }.distinct().take(MAX_SEARCH_TERMS)
        return listOf(normalizedQuery to SEARCH_PHRASE_LIMIT) + terms.map { it to SEARCH_TERM_LIMIT }
    }

    /**
     * A DAO read of the search. A failure only means fewer candidates, so it yields [fallback]; the caller's own
     * cancellation always goes through (a closed database cancels Room's scope too, and that one is a failure).
     */
    private suspend fun <T> searchRead(fallback: T, read: suspend () -> T): T =
        try {
            read()
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            fallback
        } catch (error: Exception) {
            fallback
        }

    fun recordFoodSelection(query: String, food: FoodItem) {
        val normalizedQuery = TextKeys.normalize(query)
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

    /**
     * Publica en las cachés el resultado de un alta del asistente. Relee también
     * los snapshots: el alta puede haber reemplazado la meta de HOY (se activó un
     * plan distinto el mismo día) y el Home no debe seguir mostrando la meta del
     * plan anterior hasta reiniciar la app.
     */
    suspend fun publishSetupCommit(plan: NutritionPlan?, activateNutrition: Boolean) {
        if (plan == null) return
        publishNutritionPlanCommit()
    }

    /**
     * Publica en las cachés el resultado de un commit transaccional del editor
     * nutricional directo (plan, plan activo y snapshots históricos). Sirve
     * también para el modo de solo registro, en el que no hay plan que publicar.
     */
    suspend fun publishNutritionPlanCommit() {
        val committedPlans = withContext(Dispatchers.IO) {
            db.nutritionDao().getAllPlans().map { it.toNutritionPlan() }
        }
        val committedActiveId = withContext(Dispatchers.IO) {
            db.nutritionDao().getActiveState()?.activePlanId
        }
        val committedSnapshots = withContext(Dispatchers.IO) {
            db.nutritionDao().getAllDailyGoalSnapshots().mapNotNull { it.toDailyGoalSnapshot() }
        }
        withContext(Dispatchers.Main.immediate) {
            _nutritionPlans.value = committedPlans
            _activeNutritionPlanId.value = committedActiveId
            _dailyGoalSnapshots.value = committedSnapshots
        }
    }

    fun addNutritionPlan(plan: NutritionPlan) {
        _nutritionPlans.update { plans ->
            val existingIndex = plans.indexOfFirst { it.id == plan.id }
            if (existingIndex >= 0) {
                plans.toMutableList().apply { this[existingIndex] = plan }
            } else {
                plans + plan
            }
        }
        scope.launch {
            PersistenceWriteCoordinator.mutex.withLock {
                if (_nutritionPlans.value.firstOrNull { it.id == plan.id } == plan) {
                    db.nutritionDao().upsertPlan(plan.toEntity())
                }
            }
        }

        // Keep the typed body goal normalized and linked to the plan. The body
        // feature can render it without depending on an active nutrition plan;
        // deleting/replacing the plan removes only this derived row.
        val bodyRepository = BodyProgressRepository.getInstance(appContext)
        bodyRepository.deletePlanGoals(plan.id)
        val typedGoal = plan.typedBodyGoal
            ?: plan.primaryGoal?.let {
                TypedBodyGoal(
                    metric = it.metric,
                    targetValueSi = it.value.takeIf { value -> value.isFinite() && value > 0.0 },
                    unitSi = it.unit,
                    origin = CalculationOrigin.SETTINGS_MIGRATION,
                    linkedPlanId = plan.id,
                )
            }
        typedGoal?.targetValueSi?.takeIf { it.isFinite() && it > 0.0 }?.let { target ->
            val bodyMetric = when (typedGoal.metric) {
                GoalMetric.WEIGHT -> BodyMetric.WEIGHT
                GoalMetric.BODY_FAT -> BodyMetric.BODY_FAT_PERCENT
                GoalMetric.MUSCLE_MASS -> BodyMetric.MUSCLE_MASS_PERCENT
            }
            bodyRepository.upsertGoal(
                BodyGoal(
                    id = "plan:${plan.id}:${bodyMetric.name}",
                    metric = bodyMetric,
                    targetValueSi = target,
                    unitSi = typedGoal.unitSi,
                    origin = CalculationOrigin.PLAN,
                    linkedPlanId = plan.id,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun deleteNutritionPlan(planId: String) {
        _nutritionPlans.update { list -> list.filter { it.id != planId } }
        // Body goals linked to this plan are derived rows; manual/professional
        // goals remain independent and survive plan deletion.
        BodyProgressRepository.getInstance(appContext).deletePlanGoals(planId)
        if (_activeNutritionPlanId.value == planId) {
            _activeNutritionPlanId.value = null
            ProgramRepository.getInstance().updateSettings { current ->
                current.copy(
                    dailyCalorieGoal = null,
                    dailyProteinGoal = null,
                    dailyCarbGoal = null,
                    dailyFatGoal = null,
                    calorieGoalObjective = CalorieGoalObjective.MAINTENANCE,
                )
            }
            scope.launch { db.nutritionDao().clearActiveState() }
        }
        scope.launch { db.nutritionDao().deletePlan(planId) }
    }

    fun activatePlan(planId: String) {
        _nutritionPlans.update { list -> list.map { it.copy(isActive = it.id == planId) } }
        _activeNutritionPlanId.value = planId
        scope.launch { db.nutritionDao().activatePlanAtomic(planId, _nutritionPlans.value.map { it.toEntity() }) }
        pinTodayGoalOfActivatedPlan(planId)
    }

    /**
     * Meta de HOY al activar un plan: sin fila se inserta; con una fila del MISMO
     * plan no se toca; con una fila de OTRO plan se reemplaza (activar un plan
     * distinto el mismo día cambia la meta de hoy). Los días pasados no se tocan.
     */
    private fun pinTodayGoalOfActivatedPlan(planId: String) {
        val plan = _nutritionPlans.value.find { it.id == planId } ?: return
        scope.launch {
            val today = LocalDate.now()
            val target = planDayTargetForDate(plan, today, NutritionGoalSource.PLAN_FORECAST)
            val snapshot = dailyGoalSnapshotOf(target, today, System.currentTimeMillis())
            val written = db.nutritionDao().pinTodayGoalSnapshot(snapshot.toEntity(), today.toString())
            if (written) {
                _dailyGoalSnapshots.update { current -> current.filterNot { it.date == snapshot.date } + snapshot }
            }
        }
    }

    /**
     * Resuelve con el resolvedor canónico el objetivo que debe quedar fijado
     * para una fecha, o null cuando no hay evidencia del objetivo que estuvo
     * vigente (la ausencia histórica se conserva y no se escribe nada). Los
     * ceros explícitos del plan persisten como ceros; null es ausencia.
     */
    private suspend fun resolvedDailyGoalSnapshot(date: String, plan: NutritionPlan?): DailyGoalSnapshot? {
        val normalizedDate = date.trim().take(10)
        if (normalizedDate.isBlank()) return null
        val parsedDate = runCatching { LocalDate.parse(normalizedDate) }.getOrNull() ?: return null
        val existing = db.nutritionDao().getDailyGoalSnapshot(normalizedDate)?.toDailyGoalSnapshot()
        val forecast = plan?.let { planDayTargetForDate(it, parsedDate, NutritionGoalSource.PLAN_FORECAST) }
        return NutritionGoalResolver.resolve(
            date = parsedDate,
            today = LocalDate.now(),
            snapshot = existing,
            todayForecast = forecast,
            planForecast = forecast,
            capturedAtEpochMs = System.currentTimeMillis(),
        ).fixSnapshot
    }

    /**
     * Captures the goal in force for a date exactly once. This deliberately
     * uses an INSERT-IGNORE DAO operation: changing/deleting a plan must not
     * rewrite the target used to explain an historical intake day. The goal is
     * resolved through [NutritionGoalResolver]: retroactive records, edits and
     * deletions never capture the current active plan.
     */
    fun captureDailyGoalSnapshot(date: String) {
        val normalizedDate = date.trim().take(10)
        if (normalizedDate.isBlank()) return
        val plan = activeNutritionPlan
        scope.launch {
            val snapshot = resolvedDailyGoalSnapshot(normalizedDate, plan) ?: return@launch
            val inserted = db.nutritionDao().insertDailyGoalSnapshot(snapshot.toEntity())
            if (inserted != -1L) {
                _dailyGoalSnapshots.update { current ->
                    if (current.any { it.date == snapshot.date }) current else current + snapshot
                }
            }
        }
    }

    suspend fun getDailyGoalSnapshot(date: String): DailyGoalSnapshot? =
        db.nutritionDao().getDailyGoalSnapshot(date.trim().take(10))?.toDailyGoalSnapshot()

    suspend fun getDailyGoalSnapshots(): List<DailyGoalSnapshot> =
        db.nutritionDao().getAllDailyGoalSnapshots().mapNotNull { it.toDailyGoalSnapshot() }

    // ─── Previsión semanal desde el calendario de entrenamiento ─────────────

    /**
     * Fachada de persistencia de la previsión para
     * [NutritionCalendarForecastCoordinator]. Usa [NutritionForecastWriteLane]:
     * el MISMO mutex global que el editor cubre CAS de origen + commit + la
     * publicación en caché, de modo que un guardado del editor no puede quedar
     * ENTRE la escritura de Room y la publicación (y una caché más nueva jamás
     * se pisa: CAS en `publishForecastIfSourceUnchanged`).
     */
    private val forecastStore: NutritionForecastStore = object : NutritionForecastStore {
        override val activePlanId: StateFlow<String?> = _activeNutritionPlanId
        override val plans: StateFlow<List<NutritionPlan>> = _nutritionPlans
        override val snapshots: StateFlow<List<DailyGoalSnapshot>> = _dailyGoalSnapshots

        private val lane = NutritionForecastWriteLane(
            readCurrent = { planId ->
                withContext(Dispatchers.IO) {
                    db.nutritionDao().getAllPlans().firstOrNull { it.id == planId }?.toNutritionPlan()
                }
            },
            commitRevision = { revised ->
                withContext(Dispatchers.IO) {
                    db.withTransaction { db.nutritionDao().upsertPlan(revised.toEntity()) }
                }
            },
            publish = { source, revised ->
                // Publicación SIN esperas dentro del mutex global: `StateFlow.update`
                // es atómico y thread-safe (mismo trato que `addNutritionPlan`), así
                // la sección crítica no espera al hilo principal (no amplía la
                // ventana del mutex con un salto a Main) y al ser código no
                // suspendible no hay ventana de cancelación entre commit y caché.
                _nutritionPlans.publishForecastIfSourceUnchanged(source, revised)
            },
        )

        override suspend fun storeForecastRevision(source: NutritionPlan, revised: NutritionPlan): Boolean {
            val outcome = runCatching { lane.write(source, revised) }
            val failure = outcome.exceptionOrNull()
            if (failure != null) {
                // Convergencia SIEMPRE, también si el llamador se canceló: la BD
                // es la verdad durable y la caché se resincroniza desde ella
                // (NonCancellable: no se aborta a mitad de la relectura).
                withContext(NonCancellable) {
                    runCatching { publishNutritionPlanCommit() }
                        .exceptionOrNull()
                        ?.let { syncError ->
                            android.util.Log.e(
                                "NutritionRepository",
                                "La convergencia de caché también falló; se reintentará en la próxima publicación",
                                syncError,
                            )
                        }
                }
                if (failure is CancellationException) throw failure
                android.util.Log.e(
                    "NutritionRepository",
                    "No se pudo publicar la revisión de previsión; caché resincronizada desde BD",
                    failure,
                )
                return false
            }
            return outcome.getOrDefault(false)
        }
    }

    @Volatile
    private var calendarForecast: NutritionCalendarForecastCoordinator? = null
    private var forecastUpdatesStarted = false

    /**
     * Arranca (UNA sola vez) la revisión de la previsión del plan activo cuando
     * el repositorio de programas YA está listo. Observa programa/calendario/
     * prescripciones/vitales, el registro de entrenamiento (única fuente de
     * opcionales confirmadas) y el día; nunca la ingesta ni el gasto registrado.
     */
    private fun startCalendarForecastUpdates() {
        if (forecastUpdatesStarted) return
        val programRepository = runCatching { ProgramRepository.getInstance() }.getOrNull() ?: return
        forecastUpdatesStarted = true
        scope.launch {
            runCatching { programRepository.isReady.first { it } }.getOrNull() ?: return@launch
            val coordinator = NutritionCalendarForecastCoordinator(
                store = forecastStore,
                activeProgramId = programRepository.activeProgramState.map { it?.programId },
                programs = programRepository.programs,
                settings = programRepository.settings,
                workoutLogs = programRepository.history,
                scope = scope,
            )
            calendarForecast = coordinator
            coordinator.start()
        }
    }

    /** Detiene la colección de reforecast (solo para cierre/pruebas). */
    internal fun stopCalendarForecastUpdates() {
        calendarForecast?.stop()
        calendarForecast = null
        forecastUpdatesStarted = false
    }

    // ─── User meal memory / templates ───────────────────────────────────────

    private val _mealTemplates = MutableStateFlow<List<MealTemplate>>(emptyList())
    val mealTemplates: StateFlow<List<MealTemplate>> = _mealTemplates.asStateFlow()

    // ─── Body Measurements ──────────────────────────────────────────────────
    private val _bodyMeasurements = MutableStateFlow<List<BodyMeasurementEntry>>(emptyList())
    val bodyMeasurements: StateFlow<List<BodyMeasurementEntry>> = _bodyMeasurements.asStateFlow()

    private val _measurementSchedule = MutableStateFlow(MeasurementSchedule())
    val measurementSchedule: StateFlow<MeasurementSchedule> = _measurementSchedule.asStateFlow()

    init {
        // Legacy consumers (Home/session editor) receive a compatibility
        // projection, while Room observations remain the only source of truth.
        scope.launch {
            normalizedBodyRepository.observations.collect { observations ->
                _bodyMeasurements.value = observations.toLegacyMeasurementEntries()
                _measurementSchedule.value = normalizedBodyRepository.measurementSchedule.value
            }
        }
    }

    fun addBodyMeasurement(entry: BodyMeasurementEntry) {
        normalizedBodyRepository.addLegacyEntry(entry)
    }

    fun updateMeasurementSchedule(schedule: MeasurementSchedule) {
        // Room-backed body progress is the single write source. Keep the
        // legacy flow as a compatibility projection for older screens.
        normalizedBodyRepository.updateMeasurementSchedule(schedule)
        _measurementSchedule.value = normalizedBodyRepository.measurementSchedule.value
    }

    fun deleteBodyMeasurement(id: String) {
        normalizedBodyRepository.deleteObservation(id)
        normalizedBodyRepository.observations.value
            .filter { it.sessionId == "legacy:$id" }
            .forEach { normalizedBodyRepository.deleteObservation(it.id) }
    }

    private fun List<BodyObservation>.toLegacyMeasurementEntries(): List<BodyMeasurementEntry> =
        groupBy { it.sessionId ?: "instant:${it.timestampEpochMs}" }
            .mapNotNull { (groupId, rows) ->
                val first = rows.minByOrNull { it.timestampEpochMs } ?: return@mapNotNull null
                val date = runCatching {
                    Instant.ofEpochMilli(first.timestampEpochMs)
                        .atZone(ZoneId.of(first.zoneId))
                        .toLocalDate()
                        .toString()
                }.getOrElse { first.timestampEpochMs.toString() }
                BodyMeasurementEntry(
                    id = groupId.removePrefix("legacy:"),
                    date = date,
                    weight = rows.firstOrNull { it.metric == BodyMetric.WEIGHT }?.valueSi,
                    bodyFat = rows.firstOrNull { it.metric == BodyMetric.BODY_FAT_PERCENT }?.valueSi,
                    muscleMass = rows.firstOrNull { it.metric == BodyMetric.MUSCLE_MASS_PERCENT }?.valueSi,
                    waistCm = rows.firstOrNull { it.metric == BodyMetric.WAIST }?.valueSi,
                    hipCm = rows.firstOrNull { it.metric == BodyMetric.HIP }?.valueSi,
                    neckCm = rows.firstOrNull { it.metric == BodyMetric.NECK }?.valueSi,
                    chestCm = rows.firstOrNull { it.metric == BodyMetric.CHEST }?.valueSi,
                    armCm = rows.firstOrNull { it.metric == BodyMetric.ARM }?.valueSi,
                    thighCm = rows.firstOrNull { it.metric == BodyMetric.THIGH }?.valueSi,
                )
            }
            .sortedBy { it.date }

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
        val normalizedQuery = FoodTemplateMatcher.normalizeSearchText(query)
        if (normalizedQuery.isBlank()) return null

        // CRI-AUDIT: saltar templates sin alimentos — un match de template vacío hacía
        // que el pipeline hiciera short-circuit con tags=[] sin ningún aviso (dead-end).
        return _mealTemplates.value
            .filter { it.foods.isNotEmpty() }
            .mapNotNull { template ->
                val score = FoodTemplateMatcher.score(template, normalizedQuery)
                if (score >= FoodTemplateMatcher.THRESHOLD) template to score else null
            }
            .maxByOrNull { it.second }
            ?.first
    }

    // ─── SmartFoodResolver Integration (Phase B) ────────────────────────────────

    /** Serializa las reconstrucciones del índice: una llamada que espera encuentra la generación ya vigente. */
    private val foodIndexMutex = Mutex()
    private val datasetKnowledgeMutex = Mutex()
    @Volatile
    private var datasetKnowledgeReady = false
    // CRI-AUDIT: negative-cache. Si el dataset falla al cargar, no re-gunzipear 1.3MB en
    // cada análisis; se reintenta solo en la próxima ejecución del proceso.
    private var datasetKnowledgeFailed = false

    private suspend fun ensureDatasetKnowledge() {
        if (datasetKnowledgeReady || datasetKnowledgeFailed) return
        datasetKnowledgeMutex.withLock {
            if (datasetKnowledgeReady || datasetKnowledgeFailed) return@withLock
            installFoodKnowledge()
            // CRI-ANALYSIS: install() (con require lanzable) antes quedaba FUERA del
            // runCatching; un fallo ahí propagaba desde prepareSemanticDataset() y
            // resolveFoodWithSmartResolver() a la vez (pipeline + salvage).
            runCatching {
                val snapshot = DatasetKnowledgeStore.load(appContext)
                SemanticPortionRetriever.install(snapshot)
                datasetKnowledgeReady = true
            }.onSuccess {
                android.util.Log.i(
                    "NutritionRepository",
                    "Dataset knowledge ready: ${SemanticPortionRetriever.status().documentCount} docs, v${SemanticPortionRetriever.status().formatVersion}",
                )
            }.onFailure { error ->
                datasetKnowledgeFailed = true
                android.util.Log.w("NutritionRepository", "Dataset knowledge load failed", error)
            }
        }
    }

    /** WP-N13: installs the versioned food knowledge asset once; any failure is logged and the Kotlin default stays in force. */
    private suspend fun installFoodKnowledge() {
        try {
            FoodKnowledge.install(FoodKnowledgeStore.load(appContext))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("NutritionRepository", "Food knowledge asset not installed; the built-in default stays", error)
        }
    }

    suspend fun prepareSemanticDataset() {
        ensureDatasetKnowledge()
    }

    /** D7: estado del dataset de conocimiento (diagnóstico / aviso no-silencioso). */
    fun datasetStatus(): SemanticPortionRetriever.DatasetStatus = SemanticPortionRetriever.status()

    /**
     * Deja el índice del resolvedor al día con el catálogo (WP-S4). Sin el catálogo estático publicado (fase 1 del
     * arranque) no hay nada fiable que indexar: construir ahora dejaba un índice con solo las filas globales (B5), así
     * que no hace nada; la publicación sube [catalogGeneration] y el arranque vuelve a llamar aquí al terminar. Si no,
     * reconstruye solo cuando la generación del índice difiere de la vigente. La generación se lee ANTES que los
     * datos: un cambio durante la construcción deja el índice con una generación vieja y la próxima llamada lo
     * reconstruye.
     */
    suspend fun initFoodIndex() {
        withContext(Dispatchers.Default) {
            if (_foodDatabase.value.isEmpty()) return@withContext
            foodIndexMutex.withLock {
                val generation = catalogGeneration.get()
                if (foodIndex.generation == generation) return@withLock
                rebuildFoodIndex(generation)
            }
        }
    }

    private suspend fun rebuildFoodIndex(generation: Int) {
        try {
            val globalFoods = withContext(Dispatchers.IO) {
                db.nutritionDao().getAllGlobalFoods()
            }
            val staticFoods = _foodDatabase.value
            android.util.Log.i("NutritionRepository", "Building FoodIndex g$generation with ${globalFoods.size} global + ${staticFoods.size} static foods")
            foodIndex.build(globalFoods, staticFoods, FOOD_ALIASES, generation)
            android.util.Log.i("NutritionRepository", "FoodIndex built: ${foodIndex.size()} foods indexed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("NutritionRepository", "initFoodIndex failed", e)
        }
    }

    suspend fun resolveFoodWithSmartResolver(
        query: String,
        brandHint: String? = null,
        contextHint: String? = null,
        stateHint: FoodState? = null,
    ): SmartFoodResolver.ResolutionResult {
        // Ensure both verified foods and offline semantic knowledge are ready.
        ensureDatasetKnowledge()
        initFoodIndex()
        return smartResolver.resolve(query, brandHint, contextHint, stateHint)
    }

    suspend fun recordLearnedResolution(
        query: String,
        brandHint: String?,
        foodId: String,
        portionGrams: Double?,
        cookingMethod: String?,
    ) {
        smartResolver.recordLearned(query, brandHint, foodId, portionGrams, cookingMethod)
    }

    /** E16/IT2: invalidación del aprendizaje desde la UI. */
    suspend fun clearLearnedResolutions() = foodSaveMutex.withLock {
        withContext(Dispatchers.IO) {
            smartResolver.clearLearned()
            foodCalibration.update { profile -> profile.copy(
                habitualPortionsGrams = emptyMap(), maturePortionsGrams = emptyMap(), confirmedPortions = emptyMap(),
                identityMappings = emptyMap(), statePreferences = emptyMap(), preparationProfiles = emptyMap(), oilProfiles = emptyMap(),
            ) }
            db.withTransaction { db.nutritionDao().getAllTemplates().forEach { db.nutritionDao().deleteTemplate(it.id) } }
            _mealTemplates.value = emptyList()
            _foodQueryLearning.value = emptyMap()
            check(foodPrefs.edit().remove("food_query_learning_v2").commit()) { "Could not clear food selection memory" }
        }
    }

    /**
     * Look up a food by ID across all sources: static, custom, and global.
     */
    suspend fun getFoodById(foodId: String): FoodItem? = withContext(Dispatchers.IO) {
        val cached = _foodDatabase.value.find { it.id == foodId }
        val global = if (cached == null) {
            // Check global foods (USDA/OFF)
            runCatching {
                db.nutritionDao().getGlobalFoodById(foodId)?.toFoodItem()
            }.getOrNull()
        } else {
            null
        }
        cached ?: global
    }

    // ─── Bootstrap ──────────────────────────────────────────────────────────

    /** Última carga de arranque; las pruebas la esperan para observar el final de sus tres fases. */
    @Volatile
    private var startupLoadJob: Job? = null

    /** Solo para pruebas JVM: espera a que terminen las tres fases de la última carga de arranque. */
    internal suspend fun awaitStartupLoadForTests() {
        startupLoadJob?.join()
    }

    /**
     * Arranque en tres fases independientes (WP-S3 + WP-U2); ninguna arrastra a la anterior:
     *
     * 1. [publishUserState]: lee UNA vez las filas del usuario y las publica. El catálogo global no interviene, así
     *    una importación lenta o fallida jamás oculta logs, planes ni metas.
     * 2. [importCatalog]: importa el catálogo global. Un fallo solo se registra: nunca limpia estado ni cancela el
     *    recordatorio de medición.
     * 3. Calienta el conocimiento semántico y el índice de alimentos, ya con el catálogo importado. El índice recuerda
     *    con qué generación del catálogo se construyó (WP-S4): la fase 1, una importación efectiva y [refreshData] la
     *    suben, así que un `initFoodIndex()` previo, p. ej. un análisis durante la importación, queda obsoleto y esta
     *    fase lo reconstruye con las filas recién importadas.
     *
     * @return se completa al terminar la fase 1 (estado publicado), aunque la importación siga en curso.
     */
    private fun loadFromDb(context: Context): Deferred<Unit> {
        val userStatePublished = CompletableDeferred<Unit>()
        val load = scope.launch {
            try {
                publishUserState(context)
            } finally {
                userStatePublished.complete(Unit)
            }
            importCatalog(context)

            // Initialize verified and semantic indexes proactively in the background.
            launch(Dispatchers.Default) {
                ensureDatasetKnowledge()
                initFoodIndex()
            }
        }
        // Respaldo: si el scope ya estaba cancelado el cuerpo nunca corre y nadie debe esperar para siempre.
        load.invokeOnCompletion { userStatePublished.complete(Unit) }
        startupLoadJob = load
        return userStatePublished
    }

    /**
     * Fase 1. La subfase 1a lee y publica las filas del usuario; la 1b publica el cuerpo y programa el recordatorio
     * de medición. Cada una tiene su propio `catch` con el respaldo mínimo de siempre: un fallo del cuerpo no deshace
     * comidas ya publicadas.
     */
    private suspend fun publishUserState(context: Context) {
        try {
            // Lo costoso queda fuera del candado: alimentos propios + catálogo estático y de marcas.
            val customFoods = db.nutritionDao().getAllCustomFoods()
                .map { it.toFoodItem() }
                .filterNot { custom ->
                    // Drop stale AI-inferred duplicates when a curated static entry exists.
                    custom.isAiInferred && findFoodByNormalized(custom.name) != null
                }
                .map(::normalizeFoodItem)
            val foodCatalog = (buildFoodDatabase(appContext) + customFoods)
                .map(::normalizeFoodItem)
                .distinctBy { it.id.ifBlank { it.normalizedName ?: it.name.lowercase() } }

            // UNA sola lectura bajo el mismo candado que guardan/olvidan comidas: la carga puede solaparse con un
            // guardado y no debe publicar una foto anterior. Los alimentos se publican al final, de modo que quien
            // espera el catálogo sabe que el resto del estado ya está publicado.
            foodSaveMutex.withLock {
                val dao = db.nutritionDao()
                val logs = dao.getAllLogs().map { it.toNutritionLog() }
                val templates = dao.getAllTemplates().map { it.toMealTemplate() }
                val learning = loadFoodLearning()
                val snapshots = dao.getAllDailyGoalSnapshots().mapNotNull { it.toDailyGoalSnapshot() }
                val plans = dao.getAllPlans().map { it.toNutritionPlan() }
                val activeId = dao.getActiveState()?.activePlanId

                _nutritionLogs.value = logs
                _mealTemplates.value = templates
                _foodQueryLearning.value = learning
                _dailyGoalSnapshots.value = snapshots
                _nutritionPlans.value = plans
                _activeNutritionPlanId.value = activeId
                _foodDatabase.value = foodCatalog
                // El índice del resolvedor se arma desde `_foodDatabase`: lo construido antes de este punto ya no
                // es fiable (B5). Se sube después de publicar, así quien lea la generación nueva ve ya los datos.
                bumpCatalogGeneration()
            }

            // Los utensilios guardados vuelven al motor de porciones en cada arranque (WP-U2/U12): sin esto se
            // pierden al reiniciar el proceso. Es secundario: un fallo aquí no debe activar el respaldo de la fase.
            runCatching { loadUtensilOverrides() }
                .onFailure { android.util.Log.w("NutritionRepository", "Could not reapply saved utensil overrides", it) }

            // Con el estado nutricional publicado, la previsión semanal del
            // plan activo se revisa sola cuando cambia el calendario
            // (después de que el repositorio de programas esté listo).
            startCalendarForecastUpdates()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            android.util.Log.e("NutritionRepository", "loadFromDb failed (OOM?): ${t.javaClass.simpleName}", t)
            _foodDatabase.value = runCatching { buildFoodDatabase(appContext) }.getOrDefault(emptyList())
            bumpCatalogGeneration()
            _mealTemplates.value = emptyList()
            _foodQueryLearning.value = emptyMap()
        }

        try {
            // BodyProgressRepository performs the one-time legacy JSON
            // migration and verifies its Room row count before removing
            // the old preference. Do not read that preference here again.
            normalizedBodyRepository.awaitReady()
            _bodyMeasurements.value = normalizedBodyRepository.observations.value.toLegacyMeasurementEntries()
            _measurementSchedule.value = normalizedBodyRepository.measurementSchedule.value

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
            android.util.Log.e("NutritionRepository", "Body progress load failed: ${t.javaClass.simpleName}", t)
            _bodyMeasurements.value = emptyList()
            _measurementSchedule.value = MeasurementSchedule()
            NutritionNotificationManager(appContext).cancelMeasurementReminder()
        }
    }

    /**
     * Fase 2: importa el catálogo global. Corre DESPUÉS de publicar el estado del usuario y nunca lo toca: un fallo
     * solo se registra (log + telemetría); jamás limpia estado ni cancela el recordatorio de medición.
     */
    private suspend fun importCatalog(context: Context) {
        val outcome = runCatching {
            // Serializa importaciones solapadas: la segunda reevalúa la compuerta con la meta ya guardada.
            catalogImportMutex.withLock {
                val globalCount = db.nutritionDao().getGlobalFoodCount()
                catalogImporter.importIfNeeded(
                    db = db,
                    context = context,
                    alreadyImported = globalCount > 0,
                    existingMeta = loadFoodCatalogMeta()?.let {
                        FoodImporter.ImportMetadata(
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
            }
        }
        val failure = outcome.exceptionOrNull()
        if (failure is CancellationException) throw failure
        if (failure != null) {
            android.util.Log.e(
                "NutritionRepository",
                "Catalog import failed; user data stays published: ${failure.javaClass.simpleName}",
                failure,
            )
            runCatching { NutritionTelemetry.catalogImportFailed(failure.javaClass.simpleName) }
        } else if (outcome.getOrDefault(false)) {
            android.util.Log.i("NutritionRepository", "Food catalog importado/actualizado")
            // Filas nuevas en global_foods: el índice construido antes de esta importación no las tiene (B5).
            bumpCatalogGeneration()
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
                .sortedBy { TextKeys.normalize(it.foodName) }
                .forEach { food ->
                    append(TextKeys.normalize(food.foodName))
                    append(":")
                    append(formatNumber(food.amount))
                    append(":")
                    append(food.unit.lowercase())
                    append(";")
                }
        }
    }

    private fun scoreMealTemplate(template: MealTemplate, normalizedQuery: String): Double =
        FoodTemplateMatcher.score(template, normalizedQuery)

    private fun queryQuantitiesMismatch(template: MealTemplate, normalizedQuery: String): Boolean =
        FoodTemplateMatcher.quantitiesMismatch(template, normalizedQuery)

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

    private fun normalizeFoodItem(food: FoodItem): FoodItem {
        val normalizedName = food.normalizedName ?: TextKeys.normalize(food.name)
        val normalizedBrand = food.normalizedBrand ?: food.brand?.let(TextKeys::normalize)
        val normalizedAliases = food.searchAliases
            .map(TextKeys::normalize)
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

    private fun persistFoodLearning() {
        val payload = runCatching {
            Json.encodeToString(_foodQueryLearning.value.values.toList())
        }.getOrDefault("[]")
        foodPrefs.edit().putString("food_query_learning_v2", payload).apply()
    }

    private fun loadFoodLearning(): Map<String, FoodQueryLearningEntry> {
        val payload = foodPrefs.getString("food_query_learning_v2", "[]") ?: "[]"
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

    fun refreshData(context: Context) {
        // El estado en disco pudo cambiar por completo (restauración de respaldo o de snapshot, borrado de datos,
        // catálogo importado por fuera): el índice ya construido no es fiable. La fase 1 de la carga lo vuelve a
        // marcar al publicar el estado nuevo.
        bumpCatalogGeneration()
        loadFromDb(context)
    }

    companion object {
        /** Espera máxima de [initForTests] a que se publique el estado del usuario (fase 1 del arranque). */
        private const val TEST_STARTUP_TIMEOUT_MS = 30_000L

        // Retrieval of the search (WP-S2): rows asked of the DAO for the whole phrase, per expansion term, and for custom foods.
        private const val SEARCH_PHRASE_LIMIT = 150
        private const val SEARCH_TERM_LIMIT = 60
        private const val SEARCH_CUSTOM_LIMIT = 120
        private const val MAX_SEARCH_TERMS = 4

        @Volatile private var INSTANCE: NutritionRepository? = null
        fun init(context: Context): NutritionRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: NutritionRepository(context.applicationContext).also { INSTANCE = it; it.loadFromDb(context.applicationContext) }
        }
        fun initForTests(context: Context): NutritionRepository = initForTests(context, FoodCatalogImporter.Default)

        /**
         * Igual que [initForTests] con el importador del catálogo inyectado (suspendido o que lanza). Retorna con el
         * estado del usuario YA publicado (fase 1): ninguna publicación tardía del arranque pisa lo que la prueba
         * escribe a continuación. La importación (fase 2) sigue en segundo plano.
         */
        internal fun initForTests(context: Context, catalogImporter: FoodCatalogImporter): NutritionRepository {
            val appContext = context.applicationContext
            val (repository, userStatePublished) = synchronized(this) {
                closeInstance()
                val created = NutritionRepository(
                    context = appContext,
                    db = KpknDatabase.createInMemory(appContext),
                    ownsDatabase = true,
                    catalogImporter = catalogImporter,
                )
                INSTANCE = created
                created to created.loadFromDb(appContext)
            }
            runBlocking { withTimeoutOrNull(TEST_STARTUP_TIMEOUT_MS) { userStatePublished.await() } }
            return repository
        }
        fun getInstance(): NutritionRepository = INSTANCE ?: error("Not initialized")
        internal fun closeInstance() {
            INSTANCE?.let { runBlocking { it.repositoryJob.cancelAndJoin() } }
            INSTANCE?.takeIf { it.ownsDatabase }?.db?.close()
            INSTANCE = null
        }
    }
}
