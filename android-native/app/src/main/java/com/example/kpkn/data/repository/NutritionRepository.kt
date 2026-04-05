package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NutritionRepository — Write-through cache para estado nutricional.
 *
 * Patrón:
 * - StateFlow = cache en memoria (lecturas instantáneas, UI reactiva)
 * - Room = backing store persistente
 * - loadFromDb() carga Room → StateFlows al arrancar
 * - Mutaciones actualizan StateFlow inmediatamente + Room en background
 */
class NutritionRepository private constructor(context: Context) {

    private val db = KpknDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── Nutrition Logs ──────────────────────────────────────────────────────

    private val _nutritionLogs = MutableStateFlow<List<NutritionLog>>(emptyList())
    val nutritionLogs: StateFlow<List<NutritionLog>> = _nutritionLogs.asStateFlow()

    fun addNutritionLog(log: NutritionLog) {
        _nutritionLogs.update { it + log }
        scope.launch {
            try {
                db.nutritionDao().upsertLog(log.toEntity())
            } catch (e: Exception) {
                android.util.Log.e("NutritionRepo", "Room write failed for addLog", e)
                _nutritionLogs.update { current -> current.filter { it.id != log.id } }
            }
        }
    }

    fun updateNutritionLog(log: NutritionLog) {
        val previous = _nutritionLogs.value.find { it.id == log.id }
        _nutritionLogs.update { list -> list.map { if (it.id == log.id) log else it } }
        scope.launch {
            try {
                db.nutritionDao().upsertLog(log.toEntity())
            } catch (e: Exception) {
                android.util.Log.e("NutritionRepo", "Room write failed for updateLog", e)
                if (previous != null) {
                    _nutritionLogs.update { list -> list.map { if (it.id == log.id) previous else it } }
                }
            }
        }
    }

    fun deleteNutritionLog(logId: String) {
        val removed = _nutritionLogs.value.find { it.id == logId }
        _nutritionLogs.update { list -> list.filter { it.id != logId } }
        scope.launch {
            try {
                db.nutritionDao().deleteLog(logId)
            } catch (e: Exception) {
                android.util.Log.e("NutritionRepo", "Room delete failed for log $logId", e)
                if (removed != null) {
                    _nutritionLogs.update { it + removed }
                }
            }
        }
    }

    fun clearNutritionLogs() {
        _nutritionLogs.value = emptyList()
        scope.launch { db.nutritionDao().clearAllLogs() }
    }

    // ─── Food Database (static + custom) ─────────────────────────────────────

    private val _foodDatabase = MutableStateFlow(buildFoodDatabase())
    val foodDatabase: StateFlow<List<FoodItem>> = _foodDatabase.asStateFlow()

    fun addCustomFood(food: FoodItem) {
        _foodDatabase.update { it + food }
        scope.launch { db.nutritionDao().upsertCustomFood(food.toEntity()) }
    }

    // ─── Nutrition Plans ─────────────────────────────────────────────────────

    private val _nutritionPlans = MutableStateFlow<List<NutritionPlan>>(emptyList())
    val nutritionPlans: StateFlow<List<NutritionPlan>> = _nutritionPlans.asStateFlow()

    private val _activeNutritionPlanId = MutableStateFlow<String?>(null)
    val activeNutritionPlanId: StateFlow<String?> = _activeNutritionPlanId.asStateFlow()

    val activeNutritionPlan: NutritionPlan?
        get() {
            val plans = _nutritionPlans.value
            val activeId = _activeNutritionPlanId.value
            return plans.find { it.id == activeId } ?: plans.find { it.isActive } ?: plans.lastOrNull()
        }

    fun addNutritionPlan(plan: NutritionPlan) {
        _nutritionPlans.update { it + plan }
        scope.launch { db.nutritionDao().upsertPlan(plan.toEntity()) }
    }

    fun updateNutritionPlan(plan: NutritionPlan) {
        _nutritionPlans.update { list -> list.map { if (it.id == plan.id) plan else it } }
        scope.launch { db.nutritionDao().upsertPlan(plan.toEntity()) }
    }

    fun deleteNutritionPlan(planId: String) {
        _nutritionPlans.update { list -> list.filter { it.id != planId } }
        if (_activeNutritionPlanId.value == planId) {
            _activeNutritionPlanId.value = null
            scope.launch { db.nutritionDao().upsertActiveState(NutritionActiveStateEntity(activePlanId = null)) }
        }
        scope.launch { db.nutritionDao().deletePlan(planId) }
    }

    fun setActiveNutritionPlanId(id: String?) {
        _activeNutritionPlanId.value = id
        scope.launch { db.nutritionDao().upsertActiveState(NutritionActiveStateEntity(activePlanId = id)) }
    }

    fun activatePlan(planId: String) {
        val previousPlans = _nutritionPlans.value
        val previousActiveId = _activeNutritionPlanId.value
        _nutritionPlans.update { list ->
            list.map { it.copy(isActive = it.id == planId) }
        }
        _activeNutritionPlanId.value = planId
        scope.launch {
            try {
                db.nutritionDao().activatePlanAtomic(planId, _nutritionPlans.value.map { it.toEntity() })
            } catch (e: Exception) {
                android.util.Log.e("NutritionRepo", "Room write failed for activatePlan", e)
                _nutritionPlans.value = previousPlans
                _activeNutritionPlanId.value = previousActiveId
            }
        }
    }

    // ─── Pantry Items ───────────────────────────────────────────────────────

    private val _pantryItems = MutableStateFlow<List<PantryItem>>(emptyList())
    val pantryItems: StateFlow<List<PantryItem>> = _pantryItems.asStateFlow()

    fun addPantryItem(item: PantryItem) {
        _pantryItems.update { it + item }
        scope.launch { db.nutritionDao().upsertPantryItem(item.toEntity()) }
    }

    fun deletePantryItem(itemId: String) {
        _pantryItems.update { list -> list.filter { it.id != itemId } }
        scope.launch { db.nutritionDao().deletePantryItem(itemId) }
    }

    // ─── Meal Templates ─────────────────────────────────────────────────────

    private val _mealTemplates = MutableStateFlow<List<MealTemplate>>(emptyList())
    val mealTemplates: StateFlow<List<MealTemplate>> = _mealTemplates.asStateFlow()

    fun addMealTemplate(template: MealTemplate) {
        _mealTemplates.update { it + template }
        scope.launch { db.nutritionDao().upsertTemplate(template.toEntity()) }
    }

    fun deleteMealTemplate(templateId: String) {
        _mealTemplates.update { list -> list.filter { it.id != templateId } }
        scope.launch { db.nutritionDao().deleteTemplate(templateId) }
    }

    // ─── Bootstrap ──────────────────────────────────────────────────────────

    private fun loadFromDb() {
        scope.launch {
            val logs = db.nutritionDao().getAllLogs().map { it.toNutritionLog() }
            val plans = db.nutritionDao().getAllPlans().map { it.toNutritionPlan() }
            val activeId = db.nutritionDao().getActiveState()?.activePlanId
            val pantry = db.nutritionDao().getAllPantryItems().map { it.toPantryItem() }
            val templates = db.nutritionDao().getAllTemplates().map { it.toMealTemplate() }
            val customFoods = db.nutritionDao().getAllCustomFoods().map { it.toFoodItem() }

            withContext(Dispatchers.Main) {
                _nutritionLogs.value = logs
                _nutritionPlans.value = plans
                _activeNutritionPlanId.value = activeId
                _pantryItems.value = pantry
                _mealTemplates.value = templates
                // Merge static foods with custom foods
                _foodDatabase.value = buildFoodDatabase() + customFoods
            }
        }
    }

    // ─── Singleton ──────────────────────────────────────────────────────────

    companion object {
        @Volatile private var INSTANCE: NutritionRepository? = null

        fun init(context: Context): NutritionRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NutritionRepository(context.applicationContext)
                    .also { INSTANCE = it; it.loadFromDb() }
            }

        fun getInstance(): NutritionRepository =
            INSTANCE ?: error("NutritionRepository not initialized — call init(context) first.")
    }
}
