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

// ─── Cocción: una sola transformación por ficha (WP-N10) ──────────────────────

/**
 * REGLA DE COCCIÓN (contrato v2: "Apply raw/cooked conversions exactly once and record them").
 *
 * Los nutrientes por 100 g de una ficha se transforman por EXACTAMENTE UNA de estas tres vías. [cookingTransformFor] decide
 * cuál y es la única fuente de esa decisión: la comparten [scaleFoodByPortion], el resolvedor de tags y las pruebas.
 *
 * a) [CookingTransform.StateConversion]: conversión de base por rendimiento cuando el estado de la ficha difiere del pedido.
 *    Ficha cruda y pedido cocido: los gramos que come la persona se dividen por el rendimiento ([cookingWeightYield]); ficha
 *    cocida y pedido crudo: se multiplican. El resolvedor la registra una sola vez en `ResolvedTag.stateConversion`. El
 *    rendimiento ya contiene el agua que se pierde o se gana, así que no se le suma ningún factor por gramo.
 * b) [CookingTransform.ConcentrationFactor]: factor por gramo de [COOKING_FACTORS], solo si el estado de la ficha es
 *    desconocido (ni cruda, ni cocida, ni ya preparada para ese método) y el método concentra ([CONCENTRATING_METHODS]:
 *    horno, plancha, parrilla y ahumado).
 * c) [CookingTransform.None]: nada. La ficha ya es la variante preparada, ya está en la base pedida o no hay método.
 *
 * Frito y empanizado nunca multiplican kcal ni macros: su grasa entra en gramos por [adjustLoggedFoodForOil], con la
 * categoría de absorción de la ficha (masas y tubérculos 12 g por 100 g). "Sin aceite" es una sartén seca (regla plancha): no
 * se añade aceite, no hay factor de fritura y, si el alimento pierde agua al cocerse (aves, carnes, pescados), se parte de la
 * ficha cruda convertida por rendimiento (vía a) en vez de la fila frita, que ya lleva su aceite.
 */
sealed interface CookingTransform {
    /** c) La ficha se escala tal cual. */
    data object None : CookingTransform

    /** a) Los gramos pasan de la base [from] a la base [to] con el rendimiento [weightYield] (cocido por gramo crudo). */
    data class StateConversion(val from: FoodState, val to: FoodState, val weightYield: Double) : CookingTransform

    /** b) Factor por gramo de [method] sobre una ficha de estado desconocido. */
    data class ConcentrationFactor(val method: CookingMethod, val factor: CookingFactor) : CookingTransform
}

/**
 * La única transformación de cocción de [food] cuando se come preparada con [method] (ver [CookingTransform]). Una ficha que
 * por su nombre y alias es a la vez cruda y cocida cuenta como cruda ante un pedido cocido.
 */
fun cookingTransformFor(food: FoodItem, method: CookingMethod?): CookingTransform {
    if (method == null) return CookingTransform.None
    val isRaw = CookingStateResolver.isDbFoodRaw(food)
    val isCooked = CookingStateResolver.isDbFoodCooked(food)
    return when {
        method != CookingMethod.CRUDO && isRaw ->
            CookingTransform.StateConversion(FoodState.RAW, FoodState.COOKED, cookingWeightYield(food))
        method == CookingMethod.CRUDO && isCooked ->
            CookingTransform.StateConversion(FoodState.COOKED, FoodState.RAW, cookingWeightYield(food))
        method in CONCENTRATING_METHODS && !isRaw && !isCooked &&
            !CookingStateResolver.isAlreadyPreparedForMethod(food, method) ->
            CookingTransform.ConcentrationFactor(method, cookingFactorFor(method))
        else -> CookingTransform.None
    }
}

/**
 * Palabras del nombre (singular, sin acentos) que fijan el rendimiento de cocción. Gana el primer grupo que tenga una:
 * proteína texturizada, granos y legumbres secos (se hidratan), carnes, pescados, hojas y hongos (se encogen) y verduras
 * firmes (pierden algo de agua).
 */
private val YIELD_BY_FOOD_WORD: List<Pair<Set<String>, Double>> = listOf(
    setOf("soya", "soja", "pvt") to 3.5,
    setOf("arroz", "pasta", "fideo", "lenteja", "garbanzo", "poroto", "avena", "quinoa") to 2.2,
    setOf(
        "pollo", "carne", "pavo", "cerdo", "pescado", "salmon", "vacuno", "bife",
        "espinaca", "acelga", "champinon", "hongo",
    ) to 0.75,
    setOf("zapallo", "zanahoria", "brocoli") to 0.9,
)

/**
 * Rendimiento de cocción: gramos cocidos por gramo crudo (0,75: la carne pierde un cuarto; 2,2: el arroz y las legumbres
 * secas más que se duplican). Una ficha con [FoodItem.cookingWeightFactor] manda; si no, se buscan las palabras del nombre
 * normalizado, no trozos de texto: "champiñones" es "champiñón" y "repollo" no es "pollo". "Poroto verde" es una verdura, no
 * una legumbre seca, y "pasta de maní" o "pasta de tomate" no son fideos.
 */
fun cookingWeightYield(food: FoodItem): Double {
    food.cookingWeightFactor?.takeIf { it > 0.0 }?.let { return it }
    val words = TextKeys.normalize(food.name).split(' ')
        .flatMap { word -> listOf(word, word.removeSuffix("s"), word.removeSuffix("es")) }
        .toSet()
    val notThisFood = buildSet {
        if ("poroto" in words && "verde" in words) add("poroto")
        val family = FoodIdentity.familyFor(food.name)
        if (family?.startsWith("untable_") == true || family == "pasta_concentrada") add("pasta")
    }
    return YIELD_BY_FOOD_WORD.firstOrNull { (group, _) -> group.any { it in words && it !in notThisFood } }?.second ?: 1.0
}

/**
 * Escala una ficha a la porción pedida.
 *
 * Reglas de base (plan 2026-08-16_nutrition_precision_v2, Fase 1; una sola transformación: ver [CookingTransform]):
 * - ficha y peso comparten base → escala directo, sin rendimiento ni factores;
 * - ficha cruda + pedido cocido → conversión real de base (rendimiento) y nada más: ningún factor por gramo encima;
 * - una ficha ya cocida/preparada jamás recibe rendimiento ni factor de concentración adicional (era la doble conversión
 *   que llevaba 200 g cocidos a 78–91 g);
 * - el factor por gramo de horno, plancha, parrilla y ahumado solo existe para una ficha de estado desconocido;
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

    // --- UNA SOLA TRANSFORMACIÓN DE COCCIÓN (ver CookingTransform) ---
    // La conversión de base cambia los gramos de la ficha (rendimiento); el factor por gramo, su densidad. Nunca las dos.
    val transform = cookingTransformFor(food, cookingMethod)
    val finalGrams = when (transform) {
        is CookingTransform.StateConversion ->
            if (transform.to == FoodState.COOKED) grams / transform.weightYield else grams * transform.weightYield
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

    // Factor por gramo: solo la vía b) de la regla (ficha de estado desconocido y método que concentra).
    val factor = (transform as? CookingTransform.ConcentrationFactor)?.factor ?: CookingFactor()
    val calPerGram = food.calories * factor.kcal
    val protPerGram = food.protein * factor.protein
    val carbPerGram = food.carbs * factor.carbs
    val fatPerGram = food.fats * factor.fats

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

/**
 * Alimento manual o estimado: escala los valores por 100 g dados por [amount]. Nunca aplica un factor de cocción: quien
 * arma los valores es dueño de la única transformación (un perfil estimado ya lleva la fritura o el factor del método
 * seco, ver [NutritionHeuristicEstimator.withCookingMethod]). [cookingMethod] solo se registra.
 */
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

    return LoggedFood(
        id = java.util.UUID.randomUUID().toString(),
        foodName = foodName,
        amount = amount,
        unit = unit,
        calories = calories * ratio,
        protein = protein * ratio,
        carbs = carbs * ratio,
        fats = fats * ratio,
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

fun computeDailyTotals(logs: List<NutritionLog>): DailyMacroTotals =
    computeFoodTotals(logs.filter { it.status != NutritionStatus.PLANNED }.flatMap { it.foods })

/**
 * Totales de [foods] sin mirar el estado del registro: [computeDailyTotals] descarta antes los planificados y la fila
 * de un registro (`NutritionDisplayFormat.logKcalSummary`) describe sus alimentos tal cual.
 *
 * Además del centro, la banda de kcal suma el rango guardado de cada alimento (`caloriesMin`/`caloriesMax`; sin rango
 * aporta su centro) y `isEstimate` se activa si alguno es incierto o trae un rango de ancho > 0, para que la UI no
 * presente ese centro como exacto. Cada campo conserva su redondeo de siempre y la banda se redondea como las kcal.
 */
fun computeFoodTotals(foods: List<LoggedFood>): DailyMacroTotals {
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
    var caloriesMin = 0.0
    var caloriesMax = 0.0
    var isEstimate = false

    for (food in foods) {
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
        val foodMin = food.caloriesMin ?: food.calories
        val foodMax = food.caloriesMax ?: food.calories
        caloriesMin += foodMin
        caloriesMax += foodMax
        if (food.isUncertain || foodMin != foodMax) isEstimate = true
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
        caloriesMin = kotlin.math.round(caloriesMin),
        caloriesMax = kotlin.math.round(caloriesMax),
        isEstimate = isEstimate,
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
