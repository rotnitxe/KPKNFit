package com.example.kpkn.domain.nutrition.exam

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.ClarificationRequest
import com.example.kpkn.domain.nutrition.ContextDetector
import com.example.kpkn.domain.nutrition.CookingStateResolver
import com.example.kpkn.domain.nutrition.DatasetTestHarness
import com.example.kpkn.domain.nutrition.FoodIndex
import com.example.kpkn.domain.nutrition.FoodResolutionPort
import com.example.kpkn.domain.nutrition.FoodState
import com.example.kpkn.domain.nutrition.HouseholdPortions
import com.example.kpkn.domain.nutrition.NutritionSourceKind
import com.example.kpkn.domain.nutrition.ResolvedTag
import com.example.kpkn.domain.nutrition.SmartFoodResolver
import com.example.kpkn.domain.nutrition.TagResolver
import com.example.kpkn.domain.nutrition.hasMaterialQuestion
import com.example.kpkn.domain.nutrition.parseMealDescription
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.round

/**
 * Cadena REAL del sistema actual (master) para el examen de nutricion: el mismo camino que `FoodLoggerDrawer.analyze`
 *
 *     ContextDetector.detect -> parseMealDescription -> TagResolver(port, null).resolveAll
 *
 * con el catalogo real de assets (USDA Foundation + OFF Chile, importados con las mismas funciones que la app) y el
 * dataset semantico real. Sin Room (el DAO es un proxy inerte), sin historial ni calibracion del usuario.
 */
class MasterExamPipeline private constructor(
    private val resolver: TagResolver,
    val usdaRows: Int,
    val offRows: Int,
    val indexSize: Int,
    private val system: String,
) {

    /** Procesa un caso con el contrato de la seccion 6: nunca lanza; un fallo o un exceso de 5 s va en `error`. */
    suspend fun predict(case: ExamCase): ExamPrediction {
        val mealType = parseMealType(case.mealType)
        val start = System.nanoTime()
        fun elapsedMs(): Double = round((System.nanoTime() - start) / 1_000_000.0 * 10.0) / 10.0
        return try {
            val tags = withTimeout(TIMEOUT_MS) {
                val context = ContextDetector.detect(case.text, mealType)
                val parsed = parseMealDescription(case.text)
                resolver.resolveAll(parsed, context, mealType).first
            }
            val latency = elapsedMs()
            if (latency > TIMEOUT_MS) {
                ExamPrediction(case.id, system, latency, "TIMEOUT", emptyList())
            } else {
                ExamPrediction(case.id, system, latency, null, tags.map(::toMention), lastResort = tags.isEmpty())
            }
        } catch (t: TimeoutCancellationException) {
            ExamPrediction(case.id, system, elapsedMs(), "TIMEOUT", emptyList())
        } catch (t: Throwable) {
            ExamPrediction(case.id, system, elapsedMs(), "${t.javaClass.simpleName}: ${t.message}", emptyList())
        }
    }

    companion object {
        const val TIMEOUT_MS = 5000L
        private const val ASSETS_DIR = "src/main/assets"

        fun parseMealType(raw: String): MealType =
            MealType.values().firstOrNull { it.name == raw.trim().uppercase() } ?: MealType.LUNCH

        /** Carga el catalogo real, instala el dataset semantico real y arma el resolutor. Lento (decenas de segundos). */
        suspend fun create(system: String): MasterExamPipeline {
            DatasetTestHarness.restore(null)
            val openAsset: (String) -> InputStream = { path -> FileInputStream(File(ASSETS_DIR, path)) }
            val usda = FoodImporter.parseUsda(openAsset)
            val off = FoodImporter.parseOff(openAsset)
            val global: List<GlobalFoodEntity> = usda + off
            val staticFoods = buildFoodDatabase()
            val staticById = staticFoods.associateBy { it.id }
            val globalById = HashMap<String, GlobalFoodEntity>(global.size * 2).also { map ->
                for (row in global) map[row.foodId] = row
            }
            val index = FoodIndex().apply { build(global, staticFoods, FOOD_ALIASES) }
            val smart = SmartFoodResolver(noOpNutritionDao(), index, null)
            val port = object : FoodResolutionPort {
                override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                    smart.resolve(tag, brandHint, contextHint, stateHint)

                override suspend fun getFoodById(id: String): FoodItem? =
                    staticById[id] ?: globalById[id]?.toFoodItem()

                override suspend fun staticFood(tag: String): FoodItem? = HouseholdPortions.householdStaticFood(tag)

                override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null

                override fun recordLearned(
                    query: String,
                    brandHint: String?,
                    foodId: String,
                    portionGrams: Double?,
                    cookingMethod: String?,
                ) = Unit
            }
            return MasterExamPipeline(TagResolver(port, null), usda.size, off.size, index.size(), system)
        }

        private fun noOpNutritionDao(): NutritionDao =
            java.lang.reflect.Proxy.newProxyInstance(
                NutritionDao::class.java.classLoader,
                arrayOf(NutritionDao::class.java),
            ) { _, _, _ -> null } as NutritionDao

        // --- ResolvedTag -> mencion ---------------------------------------------------------------------------

        internal fun toMention(tag: ResolvedTag): ExamMention {
            val logged: LoggedFood? = tag.loggedFood
            val v2 = tag.interpretationV2
            val grams = (logged?.amount ?: tag.amountGrams)?.finiteOrNull()
            val eaten = when {
                logged != null -> ExamMacros(
                    kcal = logged.calories.finiteOrNull(),
                    protein = logged.protein.finiteOrNull(),
                    fat = logged.fats.finiteOrNull(),
                    carbs = logged.carbs.finiteOrNull(),
                )
                v2 != null -> ExamMacros(
                    kcal = v2.calories.finiteOrNull(),
                    protein = v2.proteinGrams.finiteOrNull(),
                    fat = v2.fatGrams.finiteOrNull(),
                    carbs = v2.carbsGrams.finiteOrNull(),
                )
                else -> ExamMacros()
            }
            val per100g = if (grams != null && grams > 0.0 && eaten != ExamMacros()) {
                fun per(v: Double?): Double? = v?.let { (it * 100.0 / grams).finiteOrNull() }
                ExamMacros(per(eaten.kcal), per(eaten.protein), per(eaten.fat), per(eaten.carbs))
            } else {
                null
            }
            return ExamMention(
                text = tag.foodQuery.ifBlank { tag.tag },
                foodName = logged?.foodName?.takeIf { it.isNotBlank() } ?: tag.foodItem?.name,
                foodId = tag.foodItem?.id,
                source = tag.foodItem?.source,
                heuristic = !tag.isResolved || tag.foodItem == null ||
                    tag.nutritionSource == NutritionSourceKind.HEURISTIC_ESTIMATE,
                excluded = tag.isExcluded,
                grams = grams,
                unit = logged?.unit,
                eaten = eaten,
                per100g = per100g,
                state = tag.foodState.name,
                autoAccepted = !tag.hasMaterialQuestion(),
                questions = questionsOf(tag),
                nutrientsPresent = nutrientsOf(logged),
            )
        }

        private fun questionsOf(tag: ResolvedTag): List<String> {
            val out = LinkedHashSet<String>()
            tag.interpretationV2?.pendingQuestions.orEmpty().filter { it.material }.forEach { q ->
                out += when (q) {
                    is ClarificationRequest.Portion -> "AMOUNT"
                    is ClarificationRequest.Identity -> "IDENTITY"
                    is ClarificationRequest.WeightState -> "STATE"
                    is ClarificationRequest.Oil -> "OIL"
                }
            }
            if (tag.needsCookingClarification) out += "STATE"
            if (tag.clarificationKind != CookingStateResolver.ClarificationKind.NONE) out += "STATE"
            if (tag.needsCutClarification) out += "CUT"
            if (tag.needsOilClarification) out += "OIL"
            return out.toList()
        }

        private fun nutrientsOf(logged: LoggedFood?): List<String> {
            if (logged == null) return emptyList()
            val out = LinkedHashSet<String>()
            if (logged.fiber > 0.0) out += "fiber"
            if (logged.sugar > 0.0) out += "sugar"
            if (logged.sodiumMg > 0.0) out += "sodium"
            if (logged.potassiumMg > 0.0) out += "potassium"
            if (logged.fatBreakdown != null) out += "fatBreakdown"
            logged.micronutrients.forEach { out += it.name.lowercase() }
            return out.toList()
        }

        private fun Double.finiteOrNull(): Double? =
            if (isNaN() || isInfinite()) null else round(this * 100.0) / 100.0
    }
}
