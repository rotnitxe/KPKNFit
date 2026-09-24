package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
import java.time.LocalDate

/**
 * MacroCalculator — Pure Kotlin utility for macro scaling, daily stats, and food resolution.
 * Mirrors logic from NutritionView.tsx, RegisterFoodDrawer.tsx, and nutritionStore.ts.
 */

// ─── Portion Scaling ─────────────────────────────────────────────────────────

private val CHEESE_NAME = listOf("queso", "gouda", "gauda", "cheddar", "mantecoso", "cottage")

fun getContextualDefaultServingSize(food: FoodItem): Double {
    val lowerName = food.name.lowercase()
    val densityCategory = SubjectivePortionEngine.detectDensityCategory(food.name)
    
    return when (densityCategory) {
        SubjectivePortionEngine.FoodDensityCategory.FAT -> {
            if (lowerName.contains("aceite") || lowerName.contains("oil")) {
                if (food.servingSize >= 100.0) 10.0 else food.servingSize
            } else {
                if (food.servingSize >= 100.0) 15.0 else food.servingSize
            }
        }
        SubjectivePortionEngine.FoodDensityCategory.PROTEIN -> {
            if (lowerName.contains("huevo") || lowerName.contains("egg") || lowerName.contains("clara")) {
                food.servingSize
            } else {
                val isCooked = lowerName.contains("cocid") || lowerName.contains("plancha") || lowerName.contains("horno") || lowerName.contains("asad") || lowerName.contains("frit")
                if (food.servingSize >= 100.0) {
                    if (isCooked) 120.0 else 150.0
                } else food.servingSize
            }
        }
        SubjectivePortionEngine.FoodDensityCategory.GRAIN -> {
            if (lowerName.contains("avena") || lowerName.contains("oat")) {
                if (food.servingSize >= 100.0) 40.0 else food.servingSize
            } else {
                val isRaw = lowerName.contains("crud") || lowerName.contains("sec")
                if (food.servingSize >= 100.0) {
                    if (isRaw) 45.0 else 120.0
                } else food.servingSize
            }
        }
        SubjectivePortionEngine.FoodDensityCategory.NUTS -> {
            if (food.servingSize >= 100.0) 30.0 else food.servingSize
        }
        SubjectivePortionEngine.FoodDensityCategory.POWDER -> {
            if (food.servingSize >= 100.0) 30.0 else food.servingSize
        }
        SubjectivePortionEngine.FoodDensityCategory.FRUIT -> {
            if (food.servingSize >= 100.0) 120.0 else food.servingSize
        }
        SubjectivePortionEngine.FoodDensityCategory.VEGETABLE -> {
            if (food.servingSize >= 100.0) 100.0 else food.servingSize
        }
        SubjectivePortionEngine.FoodDensityCategory.DAIRY -> {
            if (CHEESE_NAME.any { lowerName.contains(it) }) {
                if (food.servingSize >= 100.0) 30.0 else food.servingSize
            } else {
                if (food.servingSize >= 100.0) 200.0 else food.servingSize
            }
        }
        else -> food.servingSize
    }
}

fun cookingWeightYield(food: FoodItem): Double = when {
        food.cookingWeightFactor != null && food.cookingWeightFactor > 0.0 -> food.cookingWeightFactor
        food.name.lowercase().contains("soya") || food.name.lowercase().contains("soja") || food.name.lowercase().contains("pvt") -> 3.5
        food.name.lowercase().contains("arroz") || food.name.lowercase().contains("pasta") || food.name.lowercase().contains("fideo") || food.name.lowercase().contains("lenteja") || food.name.lowercase().contains("garbanzo") || food.name.lowercase().contains("poroto") || food.name.lowercase().contains("avena") || food.name.lowercase().contains("quinoa") -> 2.2
        food.name.lowercase().contains("pollo") || food.name.lowercase().contains("carne") || food.name.lowercase().contains("pavo") || food.name.lowercase().contains("cerdo") || food.name.lowercase().contains("pescado") || food.name.lowercase().contains("salmón") || food.name.lowercase().contains("vacuno") || food.name.lowercase().contains("bife") || food.name.lowercase().contains("espinaca") || food.name.lowercase().contains("acelga") || food.name.lowercase().contains("champiñón") -> 0.75
        else -> 1.0
    }

/**
 * Escala una ficha a la porción pedida.
 *
 * Reglas de base (plan 2026-08-16_nutrition_precision_v2, Fase 1):
 * - ficha y peso comparten base → escala directo, sin rendimiento ni factores;
 * - ficha cruda + pedido cocido → conversión real de base (yield/retención);
 * - una ficha ya cocida/preparada jamás recibe yield ni factor de concentración
 *   adicional (era la doble conversión que llevaba 200 g cocidos a 78–91 g);
 * - el contexto (post-entreno, etc.) no muta la densidad por 100 g.
 */
fun scaleFoodByPortion(
    food: FoodItem,
    quantity: Double = 1.0,
    portion: PortionPreset = PortionPreset.MEDIUM,
    amountGrams: Double? = null,
    cookingMethod: CookingMethod? = null,
    portionAdjustment: Double = 1.0,
): LoggedFood {
    val multiplier = PORTION_MULTIPLIERS[portion] ?: 1.0
    val baseServing = if (amountGrams != null) food.servingSize else getContextualDefaultServingSize(food)
    val baseGrams = amountGrams ?: NutrientBasis.massForServingUnits(food, baseServing * quantity * multiplier)
    val grams = if (amountGrams != null) baseGrams else baseGrams * portionAdjustment

    // --- AJUSTE DE COCCIÓN / HIDRATACIÓN CULINARIA ---
    val dbFoodIsRaw = CookingStateResolver.isDbFoodRaw(food)
    val dbFoodIsCooked = CookingStateResolver.isDbFoodCooked(food)
    val userRequestIsCooked = cookingMethod != null && cookingMethod != CookingMethod.CRUDO
    val userRequestIsRaw = cookingMethod == CookingMethod.CRUDO

    val rawToCookedFactor = cookingWeightYield(food)

    // El rendimiento solo convierte entre bases distintas: crudo→cocido (o el
    // retorno cocido→crudo). Si la ficha ya está en la base pedida, los gramos
    // del usuario escalan la ficha tal cual.
    val finalGrams = when {
        dbFoodIsRaw && userRequestIsCooked -> {
            grams / rawToCookedFactor
        }
        dbFoodIsCooked && userRequestIsRaw -> {
            grams * rawToCookedFactor
        }
        else -> grams
    }

    val ratio = finalGrams / NutrientBasis.grams(food)

    fun extractMicronutrientAmount(vararg names: String): Double {
        val lowered = names.map { it.lowercase() }
        return food.micronutrients
            .filter { micro -> lowered.any { key -> micro.name.lowercase().contains(key) } }
            .sumOf { it.amount }
    }

    val fiberBase = food.carbBreakdown?.fiber ?: 0.0
    val sugarBase = food.carbBreakdown?.sugar ?: 0.0
    val sodiumBase = extractMicronutrientAmount("sodio")
    val potassiumBase = extractMicronutrientAmount("potasio")
    val waterBase = extractMicronutrientAmount("agua", "water")
    val caffeineBase = food.caffeineMg.takeIf { it > 0.0 }
        ?: extractMicronutrientAmount("cafeina", "caffeine")
    val creatineBase = food.creatineG

    var calPerGram = food.calories
    var protPerGram = food.protein
    var carbPerGram = food.carbs
    var fatPerGram = food.fats

    // Los factores de cocción (rendimiento/retención aproximados) solo aplican
    // cuando la ficha NO codifica ya la preparación: sobre una ficha cocida es
    // una segunda concentración. Tampoco cuando el usuario pidió crudo.
    if (cookingMethod != null && cookingMethod != CookingMethod.CRUDO && !dbFoodIsCooked) {
        // IT3: factor por categoría de alimento (fritura de masas/tubérculos concentra más).
        val cf = cookingFactorFor(food.name, cookingMethod)
        if (cf != CookingFactor()) {
            calPerGram = food.calories * cf.kcal
            protPerGram = food.protein * cf.protein
            carbPerGram = food.carbs * cf.carbs
            fatPerGram = food.fats * cf.fats
        }
    }

    val totalCalories = kotlin.math.round(calPerGram * ratio)
    val totalProtein = kotlin.math.round(protPerGram * ratio * 10) / 10.0
    val totalCarbs = kotlin.math.round(carbPerGram * ratio * 10) / 10.0
    val totalFats = kotlin.math.round(fatPerGram * ratio * 10) / 10.0

    val effectivePortion = if (amountGrams != null) null else portion

    // New calculated entries carry canonical mass. Historical logs are never rescaled.
    val resolvedUnit = "g"

    return LoggedFood(
        id = java.util.UUID.randomUUID().toString(),
        foodName = food.name,
        amount = grams,
        unit = resolvedUnit,
        calories = totalCalories,
        protein = totalProtein,
        carbs = totalCarbs,
        fats = totalFats,
        fiber = kotlin.math.round(fiberBase * ratio * 10) / 10.0,
        sugar = kotlin.math.round(sugarBase * ratio * 10) / 10.0,
        sodiumMg = kotlin.math.round(sodiumBase * ratio * 10) / 10.0,
        potassiumMg = kotlin.math.round(potassiumBase * ratio * 10) / 10.0,
        waterMl = kotlin.math.round(waterBase * ratio * 10) / 10.0,
        fatBreakdown = food.fatBreakdown,
        micronutrients = food.micronutrients.map {
            it.copy(amount = kotlin.math.round(it.amount * ratio * 10) / 10.0)
        },
        caffeineMg = kotlin.math.round(caffeineBase * ratio * 10) / 10.0,
        creatineG = kotlin.math.round(creatineBase * ratio * 10) / 10.0,
        portionPreset = effectivePortion,
        cookingMethod = cookingMethod,
        quantity = quantity,
    )
}

// ─── Manual Override Food ─────────────────────────────────────────────────────

fun createLoggedFood(
    foodName: String,
    amount: Double,
    unit: String = "g",
    calories: Double = 0.0,
    protein: Double = 0.0,
    carbs: Double = 0.0,
    fats: Double = 0.0,
    fiber: Double = 0.0,
    sugar: Double = 0.0,
    sodiumMg: Double = 0.0,
    potassiumMg: Double = 0.0,
    waterMl: Double = 0.0,
    caffeineMg: Double = 0.0,
    creatineG: Double = 0.0,
    portion: PortionPreset? = null,
    cookingMethod: CookingMethod? = null,
): LoggedFood {
    val ratio = if (amount > 0) amount / 100.0 else 1.0
    val (adjCal, adjProt, adjCarb, adjFat) = if (cookingMethod != null && cookingMethod != CookingMethod.CRUDO) {
        // IT3: factor por categoría de alimento.
        val cf = cookingFactorFor(foodName, cookingMethod)
        if (cf != CookingFactor()) {
            Quadruple(
                round1(calories * cf.kcal * ratio),
                round1(protein * cf.protein * ratio),
                round1(carbs * cf.carbs * ratio),
                round1(fats * cf.fats * ratio),
            )
        } else Quadruple(calories * ratio, protein * ratio, carbs * ratio, fats * ratio)
    } else Quadruple(calories * ratio, protein * ratio, carbs * ratio, fats * ratio)

    return LoggedFood(
        id = java.util.UUID.randomUUID().toString(),
        foodName = foodName,
        amount = amount,
        unit = unit,
        calories = adjCal,
        protein = adjProt,
        carbs = adjCarb,
        fats = adjFat,
        fiber = fiber,
        sugar = sugar,
        sodiumMg = sodiumMg,
        potassiumMg = potassiumMg,
        waterMl = waterMl,
        caffeineMg = caffeineMg,
        creatineG = creatineG,
        portionPreset = portion,
        cookingMethod = cookingMethod,
    )
}

// ─── Daily Stats ─────────────────────────────────────────────────────────────

fun computeDailyTotals(logs: List<NutritionLog>): DailyMacroTotals {
    var calories = 0.0
    var protein = 0.0
    var carbs = 0.0
    var fats = 0.0
    var fiber = 0.0
    var sugar = 0.0
    var sodiumMg = 0.0
    var potassiumMg = 0.0
    var waterMl = 0.0
    var caffeineMg = 0.0
    var creatineG = 0.0

    for (log in logs) {
        if (log.status == NutritionStatus.PLANNED) continue
        for (food in log.foods) {
            calories += food.calories
            protein += food.protein
            carbs += food.carbs
            fats += food.fats
            fiber += food.fiber
            sugar += food.sugar
            sodiumMg += food.sodiumMg
            potassiumMg += food.potassiumMg
            waterMl += food.waterMl
            caffeineMg += food.caffeineMg
            creatineG += food.creatineG
        }
    }

    return DailyMacroTotals(
        calories = kotlin.math.round(calories),
        protein = kotlin.math.round(protein),
        carbs = kotlin.math.round(carbs),
        fats = kotlin.math.round(fats),
        fiber = kotlin.math.round(fiber * 10) / 10.0,
        sugar = kotlin.math.round(sugar * 10) / 10.0,
        sodiumMg = kotlin.math.round(sodiumMg),
        potassiumMg = kotlin.math.round(potassiumMg),
        waterMl = kotlin.math.round(waterMl),
        caffeineMg = kotlin.math.round(caffeineMg),
        creatineG = kotlin.math.round(creatineG * 10) / 10.0,
    )
}

fun computeMealGroups(logs: List<NutritionLog>): List<MealGroup> {
    return MealType.entries.map { mealType ->
        val mealLogs = logs.filter { it.mealType == mealType }
        val totals = computeDailyTotals(mealLogs)
        MealGroup(mealType = mealType, logs = mealLogs, totals = totals)
    }
}

/**
 * Serie de calorías con la meta de CADA día resuelta por fecha: un día sin
 * meta queda con `goal = null` y no se le inventa ninguno. La fecha se indexa
 * como `LocalDate.toString()` (ISO).
 */
fun computeTrendData(
    logs: List<NutritionLog>,
    goalKcalByDate: Map<String, Int?>,
    days: Int = 7,
): List<TrendPoint> {
    val now = java.time.LocalDate.now()
    val cutoff = now.minusDays(days.toLong() - 1)

    val byDay = mutableMapOf<String, Double>()
    for (log in logs) {
        if (log.status == NutritionStatus.PLANNED) continue
        val dayPart = log.date.take(10)
        val dayDate = try { java.time.LocalDate.parse(dayPart) } catch (e: Exception) { continue }
        if (dayDate.isBefore(cutoff)) continue

        val dayCal = log.foods.sumOf { it.calories }
        byDay[dayPart] = (byDay[dayPart] ?: 0.0) + dayCal
    }

    return (0 until days.coerceAtLeast(1)).map { offset ->
            val date = cutoff.plusDays(offset.toLong())
            val dateKey = date.toString()
            val calories = byDay[dateKey]
            TrendPoint(
                date = dateKey,
                calories = kotlin.math.round(calories ?: 0.0).toDouble(),
                goal = goalKcalByDate[dateKey]?.toDouble(),
                hasData = calories != null,
            )
        }
}

fun computeMacroRingPct(
    totals: DailyMacroTotals,
    calorieGoal: Int?,
    proteinGoal: Int?,
    carbGoal: Int?,
    fatGoal: Int?,
): MacroRingPct {
    // Un campo sin meta (null) o un 0 explícito no produce anillo: 0.0.
    return MacroRingPct(
        calories = if (calorieGoal != null && calorieGoal > 0) totals.calories / calorieGoal else 0.0,
        protein = if (proteinGoal != null && proteinGoal > 0) totals.protein / proteinGoal else 0.0,
        carbs = if (carbGoal != null && carbGoal > 0) totals.carbs / carbGoal else 0.0,
        fats = if (fatGoal != null && fatGoal > 0) totals.fats / fatGoal else 0.0,
    )
}

data class MacroRingPct(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
)

// ─── Metas del día ───────────────────────────────────────────────────────────

/**
 * Metas de un día con ausencia explícita por campo: un campo nulo es ausencia
 * y un 0 explícito viaja como 0. Los defaults 2500/150/250/70 ya no existen:
 * cuando no hay metas, el resultado es [DayGoalsResult.Absent] y ningún
 * consumidor debe mostrar, alertar o medir contra valores fabricados.
 */
data class MacroGoals(
    val calorieGoal: Int? = null,
    val proteinGoal: Int? = null,
    val carbGoal: Int? = null,
    val fatGoal: Int? = null,
    val fiberGoal: Int? = null,
    val sugarLimit: Int? = null,
    val sodiumLimitMg: Int? = null,
    val potassiumGoalMg: Int? = null,
    val hydrationGoalMl: Int? = null,
    val showOverages: Boolean = true,
) {
    /** true si queda alguna meta numérica de calorías/macros (los ceros cuentan). */
    val hasGoals: Boolean
        get() = calorieGoal != null || proteinGoal != null || carbGoal != null || fatGoal != null
}

/** Motivo explícito de ausencia de metas. Nunca un default. */
enum class GoalsAbsence {
    /** Solo seguimiento: el día no tiene metas numéricas. */
    TRACKING_ONLY,

    /** Ausencia explícita de objetivo (p. ej. día pasado sin evidencia). */
    NO_GOAL,
}

/**
 * Resultado sellado de derivar las metas de un día. La ausencia se modela de
 * forma explícita ([Absent]) y cada consumidor la maneja a la vista: ocultar
 * anillos de objetivos, mostrar «sin objetivos», no enviar la alerta y no
 * calcular déficit. No basta con guardar ceros.
 */
sealed interface DayGoalsResult {
    data class Present(val goals: MacroGoals) : DayGoalsResult
    data class Absent(val reason: GoalsAbsence) : DayGoalsResult
}

/**
 * Previsión de las metas vigentes a partir del plan activo y los ajustes, sin
 * fabricar valores: null es ausencia real. Con plan, sus campos mandan (un 0
 * del plan es un 0 legítimo, no ausencia); sin plan, los objetivos explícitos
 * de los ajustes. Devuelve null cuando no hay ninguna evidencia de metas.
 */
fun dayGoalForecastOf(settings: Settings, activePlan: NutritionPlan?): NutritionDayGoal.PlanDayTarget? {
    // Modo durable de solo registro: no existe ninguna previsión de metas.
    // Ni del plan activo (que no lo hay) ni de los objetivos de ajustes: un
    // valor residual en ajustes no es una meta vigente.
    if (settings.nutritionTrackingOnly) return null
    return activePlan?.let { planDayTargetOf(it, NutritionGoalSource.PLAN_FORECAST) }
        ?: settingsDayTargetOf(settings, NutritionGoalSource.PLAN_FORECAST)
}

/** Previsión desde los objetivos explícitos de ajustes; null sin evidencia. */
fun settingsDayTargetOf(settings: Settings, source: NutritionGoalSource): NutritionDayGoal.PlanDayTarget? {
    val target = NutritionDayGoal.PlanDayTarget(
        calorieTargetKcal = settings.dailyCalorieGoal,
        proteinGoalG = settings.dailyProteinGoal,
        carbGoalG = settings.dailyCarbGoal,
        fatGoalG = settings.dailyFatGoal,
        direction = null,
        calculationOrigin = CalculationOrigin.SETTINGS_MIGRATION,
        planId = null,
        source = source,
    )
    return target.takeIf { it.hasGoalValues() }
}

/**
 * Proyección de un objetivo ya resuelto por fecha. Los campos sin meta quedan
 * null (nunca un default inventado); un 0 explícito se conserva como 0. Los
 * límites/guías de micronutrientes solo salen de los ajustes del usuario, sin
 * literales por defecto.
 */
fun dayGoalsOf(goal: NutritionDayGoal, settings: Settings): DayGoalsResult = when (goal) {
    is NutritionDayGoal.PlanDayTarget ->
        if (goal.hasGoalValues()) DayGoalsResult.Present(macroGoalsOf(goal, settings))
        else DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY)
    NutritionDayGoal.TrackingOnly -> DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY)
    NutritionDayGoal.NoGoal -> DayGoalsResult.Absent(GoalsAbsence.NO_GOAL)
}

private fun macroGoalsOf(target: NutritionDayGoal.PlanDayTarget, settings: Settings): MacroGoals =
    MacroGoals(
        // Sin fallback a los ajustes: la evidencia resuelta (snapshot o
        // previsión) es la única fuente; un null es ausencia real del día.
        calorieGoal = target.calorieTargetKcal,
        proteinGoal = target.proteinGoalG,
        carbGoal = target.carbGoalG,
        fatGoal = target.fatGoalG,
        fiberGoal = settings.dailyFiberGoal,
        sugarLimit = settings.dailySugarLimit,
        sodiumLimitMg = settings.dailySodiumLimitMg,
        potassiumGoalMg = settings.dailyPotassiumGoalMg,
        hydrationGoalMl = settings.dailyHydrationGoalMl,
        showOverages = settings.nutritionShowOverages,
    )

/**
 * Resolución por fecha para los consumidores. Orden exacto del resolvedor:
 * para cada fecha manda su [DailyGoalSnapshot] histórico (inmutable); sin
 * snapshot, la previsión vigente solo se usa para hoy y el futuro (el pasado
 * queda [NutritionDayGoal.NoGoal]); sin ninguna evidencia, solo seguimiento.
 * Nunca fabrica metas ni aplica el plan actual a días pasados.
 */
fun resolveDayGoals(
    date: LocalDate,
    settings: Settings,
    activePlan: NutritionPlan?,
    snapshot: DailyGoalSnapshot? = null,
    today: LocalDate = LocalDate.now(),
): DayGoalsResult {
    // Modo durable de solo registro: mientras esté elegido, HOY y el futuro no
    // tienen metas (ni siquiera un snapshot del mismo día: el modo está activo
    // ahora). Los snapshots históricos de días PASADOS siguen explicando la
    // ingesta de esas fechas con el objetivo que estuvo vigente.
    if (settings.nutritionTrackingOnly && !date.isBefore(today)) {
        return DayGoalsResult.Absent(GoalsAbsence.TRACKING_ONLY)
    }
    val forecast = dayGoalForecastOf(settings, activePlan)
    val resolution = NutritionGoalResolver.resolve(
        date = date,
        today = today,
        snapshot = snapshot,
        todayForecast = forecast,
        planForecast = forecast,
        trackingOnly = forecast == null,
    )
    return dayGoalsOf(resolution.goal, settings)
}

/** [resolveDayGoals] para un conjunto de fechas, con snapshots por fecha. */
fun resolveDayGoalsByDate(
    dates: Collection<LocalDate>,
    settings: Settings,
    activePlan: NutritionPlan?,
    snapshots: List<DailyGoalSnapshot> = emptyList(),
    today: LocalDate = LocalDate.now(),
): Map<LocalDate, DayGoalsResult> {
    val snapshotsByDate = snapshots.associateBy { it.date.trim().take(10) }
    return dates.distinct().associateWith { date ->
        resolveDayGoals(
            date = date,
            settings = settings,
            activePlan = activePlan,
            snapshot = snapshotsByDate[date.toString()],
            today = today,
        )
    }
}

/**
 * Metas vigentes (plan + ajustes) sin fabricar defaults: un campo sin
 * evidencia queda null. No distingue [GoalsAbsence]; para metas por fecha usa
 * [resolveDayGoals], que es lo que deben consumir las pantallas.
 */
fun deriveMacroGoals(settings: Settings, activePlan: NutritionPlan? = null): MacroGoals =
    dayGoalForecastOf(settings, activePlan)?.let { macroGoalsOf(it, settings) } ?: MacroGoals()

// ─── Alertas de macros ───────────────────────────────────────────────────────

enum class MacroAlertKind { PROTEIN_DEFICIT, CARB_DEFICIT, FAT_DEFICIT, CALORIE_EXCESS }

data class MacroAlertItem(
    val kind: MacroAlertKind,
    val consumed: Int,
    val goal: Int,
    val percent: Int,
)

data class MacroDeficitAlerts(
    val calorieExcess: Boolean,
    val items: List<MacroAlertItem>,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Alertas justificadas solo por metas reales. Sin metas ([DayGoalsResult.Absent])
 * no se avisa de «te faltan calorías/proteína»: no hay meta contra la que
 * medir. Un campo sin meta no genera su alerta (los ceros explícitos tampoco,
 * porque no son metas > 0 contra las que medir). Mismos umbrales de siempre:
 * proteína < 70%, carbohidratos < 60%, grasas < 60%, calorías > 110%.
 */
fun macroDeficitAlerts(totals: DailyMacroTotals, goals: DayGoalsResult): MacroDeficitAlerts {
    val present = (goals as? DayGoalsResult.Present)?.goals ?: return MacroDeficitAlerts(false, emptyList())
    var calorieExcess = false
    val items = buildList {
        present.proteinGoal?.takeIf { it > 0 }?.let { goal ->
            val pct = totals.protein / goal
            if (pct < 0.70) add(MacroAlertItem(MacroAlertKind.PROTEIN_DEFICIT, totals.protein.toInt(), goal, (pct * 100).toInt()))
        }
        present.carbGoal?.takeIf { it > 0 }?.let { goal ->
            val pct = totals.carbs / goal
            if (pct < 0.60) add(MacroAlertItem(MacroAlertKind.CARB_DEFICIT, totals.carbs.toInt(), goal, (pct * 100).toInt()))
        }
        present.fatGoal?.takeIf { it > 0 }?.let { goal ->
            val pct = totals.fats / goal
            if (pct < 0.60) add(MacroAlertItem(MacroAlertKind.FAT_DEFICIT, totals.fats.toInt(), goal, (pct * 100).toInt()))
        }
        present.calorieGoal?.takeIf { it > 0 }?.let { goal ->
            val pct = totals.calories / goal
            if (pct > 1.10) {
                calorieExcess = true
                add(MacroAlertItem(MacroAlertKind.CALORIE_EXCESS, totals.calories.toInt(), goal, (pct * 100).toInt()))
            }
        }
    }
    return MacroDeficitAlerts(calorieExcess = calorieExcess, items = items)
}

// ─── Duplicate Nutrition Log ─────────────────────────────────────────────────

fun duplicateLog(log: NutritionLog, targetDate: String): NutritionLog {
    return NutritionLog(
        id = java.util.UUID.randomUUID().toString(),
        date = "${targetDate}T12:00:00.000Z",
        mealType = log.mealType,
        foods = log.foods.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
        notes = log.notes?.let { "$it (duplicado)" },
        status = NutritionStatus.CONSUMED,
    )
}
