package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.ParsedMealItem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * WP-N0 - diagnostic harness ("probe") for the natural-language meal pipeline.
 *
 * Runs the 60 audit inputs through the live chain TWICE and prints a fixed-width table plus a JSON report:
 *
 *     parseMealDescription(text) -> TagResolver(port).resolveAll(parsed)
 *
 *  - Pass A: real dataset snapshot installed (`DatasetTestHarness.restore(null)` decodes
 *    `src/main/assets/food_data/dataset_knowledge.bin`).
 *  - Pass B: no snapshot. The harness has no "uninstall" API (`restore(null)` re-installs the real dataset and
 *    `SemanticPortionRetriever.install` only accepts a non-null snapshot), so the private `knowledge`/`lastRetrieve` fields are reset by
 *    reflection; if that ever fails the probe falls back to an inert one-document snapshot and says so in the report.
 *
 * Chain note: `TagResolver.resolveAll` already ends with `NutritionInterpretationBridge.enrich` on every tag, so the
 * probe does NOT enrich again. The drawer currently enriches twice (`FoodLoggerDrawer.resolveTags` runs `enrich` on the
 * `resolveAll` result); WP-N1 removes that second pass. The drawer also feeds `SemanticPortionRetriever.retrieve(text)`
 * to the parser and passes `detectedContext`/`mealType` to `resolveAll`; the probe keeps the plain two-call chain so it
 * keeps compiling after the dead-path removal of WP-N7 (the retrieval parameter is inert per the audit).
 *
 * Fixture: 12 deterministic OFF rows on top of the curated catalog (static foods + FOOD_ALIASES), see [offFixture].
 *
 * Assertions are diagnostic only (harness integrity, GREEN today): every input produces a row in both passes, each row
 * has at least one tag or an explicit error / empty-result note, every input stays under [MAX_MS_PER_INPUT] ms and the
 * JSON report is written. Nothing is compared with `expect`: that text is the human-readable post-remediation target,
 * to be reviewed in the table / JSON before and after each work package.
 *
 * JVM caveat (finding `\b`, input #34): on JDK >= 19 `\b` is ASCII-only while Android/ICU treats accented letters as
 * word characters, so the regex-boundary divergence cannot be measured here (WP-N4 adds the instrumented twin).
 *
 * Run only this test (flavor Base):
 *   cd android-native && ./gradlew :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.nutrition.BlindMealCorpusProbeTest" -i
 * Outputs: stdout table (visible with `-i`, or in `app/build/test-results/testBaseDebugUnitTest/TEST-com.example.kpkn.domain.nutrition.BlindMealCorpusProbeTest.xml`)
 * and `app/build/reports/nutrition-reliability/blind-probe.json`.
 */
class BlindMealCorpusProbeTest {

    // --- Model ---------------------------------------------------------------------------------------------------

    private data class Probe(val n: Int, val finding: String, val text: String, val expect: String)

    private data class ParsedCell(
        val tag: String,
        val qty: Double,
        val grams: Double?,
        val intent: String,
        val cooking: String?,
        val excluded: Boolean,
        val portion: String,
        val foodQuery: String,
        val unitId: String?,
        val brandHint: String?,
        val excludedIngredients: List<String>,
        val trailing: Boolean,
    )

    private data class ResolvedCell(
        val tag: String,
        val foodQuery: String,
        val foodId: String?,
        val name: String?,
        val loggedName: String?,
        val grams: Double?,
        val kcal: Double?,
        val status: String,
        val questions: List<String>,
        val materialQuestion: Boolean,
        val oilLevel: String,
        val oilApplied: Boolean,
        val appliedOilGrams: Double?,
        val stateConversion: String?,
        val cooking: String?,
        val foodState: String,
        val stateAssumed: Boolean,
        val intent: String,
        val portion: String,
        val qty: Double,
        val source: String,
        val excluded: Boolean,
        val candidateIds: List<String>,
        val statusText: String,
    )

    private data class CaseRow(
        val probe: Probe,
        val pass: String,
        val ms: Long,
        val parseMs: Long,
        val resolveMs: Long,
        val normalized: String?,
        val parsed: List<ParsedCell>,
        val resolved: List<ResolvedCell>,
        val error: String?,
        val errorTrace: List<String>,
        val emptyReason: String?,
        val deltaKinds: List<String> = emptyList(),
    ) {
        val delta: Boolean get() = deltaKinds.isNotEmpty()
    }

    private class Env(val port: FoodResolutionPort, val indexedFoods: Int)

    private class Collected(
        val rowsA: List<CaseRow>,
        val rowsB: List<CaseRow>,
        val statusA: SemanticPortionRetriever.DatasetStatus,
        val statusB: SemanticPortionRetriever.DatasetStatus,
        val modeB: String,
        val indexedFoods: Int,
    )

    private companion object {
        /** Maximum description length fed to the chain (600 chars). */
        const val MAX_INPUT_CHARS = 600
        const val MAX_MS_PER_INPUT = 2000L
        const val REPORT_PATH = "build/reports/nutrition-reliability/blind-probe.json"

        // Table layout (fixed widths; long cells wrap onto continuation lines).
        const val W_N = 3
        const val W_FIND = 13
        const val W_INPUT = 34
        const val W_PARSED = 58
        const val W_RESOLVED = 98
        const val W_MS = 5
    }

    // --- The 60 audit inputs, verbatim from the plan (WP-N0), in order -------------------------------------------

    private val probes: List<Probe> = listOf(
            Probe(1, "P3,P4,P5,Q10", "almorcé un plato de porotos con riendas y un pan con palta, después un café con leche",
                "cl029 ~350 g/450 kcal + cl025 120 g/280 + café con leche ~240 ml/~90 kcal, 0 preguntas"),
            Probe(2, "Q1,Q2", "2 yogures y 3 tomates",
                "yogurt 2x125 g; tomate 3x120 g gen026"),
            Probe(3, "Q10,Q5", "un vaso de agua",
                "250 g, 0 kcal, AUTO"),
            Probe(4, "C1", "150 g salmón a la parrilla",
                "gen009, una sola conversión 150/0,78 ≈ 400 kcal, sin x1,05 ni aceite"),
            Probe(5, "Q4,Q10", "una lata de coca cola",
                "350 g ≈ 147 kcal"),
            Probe(6, "P2", "al menos 2 huevos",
                "huevo qty 2, 100 g, no excluido"),
            Probe(7, "P1", "Desayuno: 2 huevos. Almuerzo: arroz con pollo.",
                "3 tags"),
            Probe(8, "P4", "pollo a la plancha y papas a lo pobre",
                "2 tags, sin \"un la plancha\""),
            Probe(9, "P5,Q10", "agua con gas",
                "1 tag 0 kcal"),
            Probe(10, "P5", "helado de vainilla y chocolate",
                "1 tag"),
            Probe(11, "P5", "200 g de arroz con pollo",
                "arroz 100 g + pollo 100 g por cuotas del plato"),
            Probe(12, "P6", "asado",
                "1 tag"),
            Probe(13, "control", "un guiso de lentejas",
                "lentejas GUISADO"),
            Probe(14, "Q3", "500 cc de leche",
                "515 g EXPLICIT_MASS"),
            Probe(15, "Q3,Q5", "medio litro de jugo de naranja",
                "gen103 500 g ≈ 225 kcal"),
            Probe(16, "Q3", "cuarto de kilo de carne molida",
                "gen010 250 g"),
            Probe(17, "-", "1 lt de agua",
                "1000 g, 0 kcal, AUTO"),
            Probe(18, "Q4", "una botella de agua",
                "500 g"),
            Probe(19, "Q5", "un jugo de naranja",
                "250 g"),
            Probe(20, "Q6", "plato grande de arroz",
                "1,25 x #21"),
            Probe(21, "-", "un plato de arroz",
                "(sin esperado en el plan; referencia base de #20: plato grande = 1,25 x este)"),
            Probe(22, "Q1,Q6", "2 manzanas grandes",
                "2 x unidad x 1,25"),
            Probe(23, "Q7", "pollo",
                "pregunta de corte con opciones COCIDAS"),
            Probe(24, "Q8", "uva",
                "gen053, nunca huevo"),
            Probe(25, "-", "wevos",
                "gen007"),
            Probe(26, "Q9", "ensalada de repollo",
                "perfil VEGETAL ~28 kcal"),
            Probe(27, "Q9", "tres leches",
                "postre, no FATTY_PROTEIN"),
            Probe(28, "Q11", "un sandwich de jamón y queso y una coca cola",
                "componentes + coca cola aparte"),
            Probe(29, "C1", "pechuga de pollo frita sin aceite 150 g",
                "sin aceite ni factor FRITO"),
            Probe(30, "C1", "150 g de pechuga cruda frita",
                "rendimiento una vez + aceite una vez ≈ 293 kcal"),
            Probe(31, "control", "papas fritas 150 g",
                "gen021f 468 kcal sin aceite extra"),
            Probe(32, "C2", "huevos revueltos",
                "gen007r"),
            Probe(33, "C3", "100 g champiñones salteados",
                "gen038 una conversión"),
            Probe(34, "\\b", "huevo poché",
                "huevo + COCIDO en ambas plataformas"),
            Probe(35, "-", "ajá, un café",
                "solo café"),
            Probe(36, "-", "brocolí al vapor",
                "brocoli VAPOR"),
            Probe(37, "Q2", "tres tomates",
                "`tomate`"),
            Probe(38, "-", "huevos x2",
                "`huevo` qty 2"),
            Probe(39, "-", "porotos granados",
                "cl007"),
            Probe(40, "P3", "porotos con riendas",
                "cl029"),
            Probe(41, "-", "lentejas con arroz",
                "2 tags"),
            Probe(42, "-", "una taza de té sin azúcar",
                "1 tag ≈ 2 kcal, sin \"azúcar\" excluido"),
            Probe(43, "-", "café sin azúcar",
                "gen059"),
            Probe(44, "-", "coca cola zero",
                "0 kcal AUTO"),
            Probe(45, "-", "2 litros de agua",
                "2000 g, 0 kcal, AUTO"),
            Probe(46, "-", "me comí dos marraquetas con mantequilla",
                "cl010 200 g + mantequilla ~10 g"),
            Probe(47, "-", "anoche cené una cazuela de vacuno",
                "cl030"),
            Probe(48, "-", "comí arroz con pollo y ensalada chilena, de postre un arroz con leche",
                "4 tags"),
            Probe(49, "-", "una empanada de pino al horno y una de queso",
                "2 empanadas"),
            Probe(50, "-", "pan con palta y un té",
                "2 tags"),
            Probe(51, "control", "un completo italiano sin mayo",
                "no resuelto + pregunta"),
            Probe(52, "-", "150 gr de pollo al horno con 100 g de arroz",
                "gen003h 150 g; gen005 100 g"),
            Probe(53, "-", "avena con leche y plátano",
                "3 tags"),
            Probe(54, "-", "un pan con queso y tomate",
                "cl026 + gen026"),
            Probe(55, "-", "jugo natural de naranja grande",
                "250 x 1,25 g"),
            Probe(56, "-", "dos vasos de leche descremada",
                "gen046 2x250 ml x1,03"),
            Probe(57, "-", "pollo frito",
                "FRITO + pregunta de aceite"),
            Probe(58, "-", "100 g de atún en aceite",
                "gen029e AUTO 200 kcal"),
            Probe(59, "-", "un yogur griego con granola",
                "gen017 125 g + granola 30 g"),
            Probe(60, "-", "polloooo con arros",
                "pollo + arroz"),
    )

    // --- Deterministic OFF fixture (12 rows) ---------------------------------------------------------------------

    /**
     * Local copy of `FluencyGoldenCorpusTest.offFood()`, extended with brand / pack mass and built like the importer's
     * OFF Chile rows (`source = "OFF Chile"`, `sourcePriority = 80`, aliases = normalized name + normalized brand).
     * Macros are per 100 g / 100 ml as sold (`PER_100G_AS_SOLD`).
     */
    private fun offFood(
        id: String,
        name: String,
        brand: String?,
        calories: Double,
        protein: Double,
        carbs: Double,
        fats: Double,
        sugar: Double = 0.0,
        fiber: Double = 0.0,
        portionGrams: Double? = null,
        aliases: List<String> = emptyList(),
    ): GlobalFoodEntity {
        val normalizedName = FoodIndex.normalizeSearch(name)
        val normalizedBrand = brand?.let { FoodIndex.normalizeSearch(it) }
        val allAliases = (listOfNotNull(normalizedName, normalizedBrand) + aliases).distinct()
        return GlobalFoodEntity(
            foodId = id,
            name = name,
            brand = brand,
            normalizedName = normalizedName,
            normalizedBrand = normalizedBrand,
            aliasesJson = JsonArray(allAliases.map { JsonPrimitive(it) }).toString(),
            calories = calories,
            protein = protein,
            carbs = carbs,
            fats = fats,
            fiber = fiber,
            sugar = sugar,
            source = "OFF Chile",
            sourcePriority = 80,
            verifiedScore = 0.85,
            sourceRecordId = id.removePrefix("off_"),
            nutritionBasis = "PER_100G_AS_SOLD",
            portionGrams = portionGrams,
        )
    }

    private val offFixture: List<GlobalFoodEntity> = listOf(
        // Beverages and dairy/bakery rows named in the plan.
        offFood("off_coca_cola_original_350ml", "Coca-Cola Original 350 ml", "Coca-Cola", 42.0, 0.0, 10.6, 0.0, sugar = 10.6),
        offFood("off_coca_cola_zero", "Coca-Cola Zero", "Coca-Cola", 0.2, 0.0, 0.0, 0.0),
        offFood("off_agua_mineral_gas_cachantun", "Agua Mineral con Gas Cachantun", "Cachantun", 0.0, 0.0, 0.0, 0.0),
        offFood("off_leche_descremada_colun", "Leche Descremada Colun", "Colun", 34.0, 3.4, 4.9, 0.1, sugar = 4.9),
        offFood("off_yogurt_natural_colun", "Yogurt Natural Colun", "Colun", 59.0, 3.5, 4.7, 2.9, sugar = 4.7),
        offFood("off_pan_integral_bauducco", "Pan Integral Bauducco", "Bauducco", 250.0, 9.5, 45.0, 3.6, sugar = 4.0, fiber = 6.0),
        offFood("off_galletas_costa_vino", "Galletas Costa Vino", "Costa", 430.0, 6.5, 71.5, 13.0, sugar = 20.0),
        // Adversarial rows already used by FluencyGoldenCorpusTest / NutritionQaRegressionTest / IndependentNutritionCorpusTest.
        offFood("off_pizza_tomate", "Pizza de Tomate", null, 266.0, 11.0, 33.0, 10.0, aliases = listOf("tomato pizza")),
        offFood("off_caldo_pollo", "Caldo de Pollo", null, 12.0, 1.0, 1.0, 0.4, aliases = listOf("chicken broth")),
        // Shipped OFF row whose malformed brand column says "Avena" (plain oats must never resolve to it).
        offFood("off_7800120162489", "Vivo PRO BIÓTICOS", "Avena", 355.0, 6.9, 78.0, 3.0, fiber = 1.4),
        // Pack mass in the name and in portionGrams (the trailing "1kg" is the pack, never the eaten mass).
        offFood("off_hallulla_ideal_1kg", "Hallulla Ideal 1kg", "Ideal", 260.0, 8.0, 50.0, 3.0, portionGrams = 1000.0),
        // The plan names 11 rows for a fixture of 12: Banana Bread (FluencyGoldenCorpusTest) is the 12th (platano/pan collision).
        offFood("off_banana_bread", "Banana Bread", null, 326.0, 4.0, 54.0, 10.0, aliases = listOf("pan de platano")),
    )

    // --- Environment (same construction as IndependentNutritionCorpusTest / NutritionQaRegressionTest) -----------

    private fun noOpNutritionDao(): NutritionDao =
        java.lang.reflect.Proxy.newProxyInstance(
            NutritionDao::class.java.classLoader,
            arrayOf(NutritionDao::class.java),
        ) { _, _, _ -> null } as NutritionDao

    private fun buildEnv(): Env {
        val staticFoods = buildFoodDatabase()
        val foods = (staticFoods + offFixture.map { it.toFoodItem() }).associateBy { it.id }
        val index = FoodIndex().apply { build(offFixture, staticFoods, FOOD_ALIASES) }
        val resolver = SmartFoodResolver(noOpNutritionDao(), index, null)
        val port = object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?, stateHint: FoodState?) =
                resolver.resolve(tag, brandHint, contextHint, stateHint)
            override suspend fun getFoodById(id: String): FoodItem? = foods[id]
            override suspend fun staticFood(tag: String): FoodItem? = HouseholdPortions.householdStaticFood(tag)
            override fun staticIsExact(tag: String): Boolean = findFoodExactByNormalized(tag) != null
            override fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?) = Unit
        }
        return Env(port, index.size())
    }

    // --- Dataset snapshot control ---------------------------------------------------------------------------------

    /** Leaves `SemanticPortionRetriever` with no snapshot (see class KDoc) and returns how it was done. */
    private fun uninstallDatasetSnapshot(): String = try {
        val owner = SemanticPortionRetriever::class.java
        for (name in listOf("knowledge", "lastRetrieve")) {
            val field = owner.getDeclaredField(name)
            field.isAccessible = true
            // Kotlin object properties compile to static backing fields; keep the instance path as a safety net.
            field.set(if (java.lang.reflect.Modifier.isStatic(field.modifiers)) null else SemanticPortionRetriever, null)
        }
        check(SemanticPortionRetriever.currentSnapshot() == null) { "snapshot still installed after the reflective reset" }
        "no snapshot (SemanticPortionRetriever.knowledge reset to null by reflection)"
    } catch (t: Throwable) {
        SemanticPortionRetriever.install(DatasetTestHarness.snapshotFor("zzzz", 1.0, "zzzz", queryToken = "zzzz"))
        "inert one-document snapshot (reflective reset failed: ${t.javaClass.simpleName})"
    }

    // --- Chain ---------------------------------------------------------------------------------------------------

    private suspend fun warmUp(env: Env) {
        // Not recorded: one full untimed pass over the corpus (class loading, lazy tables, JIT), so that pass A and
        // pass B are both timed on a warm JVM and their ms are comparable (and comparable across work packages).
        for (probe in probes) {
            runCatching { TagResolver(env.port).resolveAll(parseMealDescription(probe.text.take(MAX_INPUT_CHARS))) }
        }
    }

    private suspend fun runProbe(env: Env, probe: Probe, pass: String): CaseRow {
        val text = probe.text.take(MAX_INPUT_CHARS)
        var normalized: String? = null
        var parsedCells: List<ParsedCell> = emptyList()
        val started = System.nanoTime()
        var parsedAt = started
        return try {
            val parsed = parseMealDescription(text)
            parsedAt = System.nanoTime()
            normalized = parsed.rawDescription
            parsedCells = parsed.items.map { parsedCell(it) }
            // resolveAll already enriches every tag (NutritionInterpretationBridge.enrich); the drawer enriches a
            // second time today. The probe deliberately stops here.
            val (tags, _) = TagResolver(env.port).resolveAll(parsed)
            val finished = System.nanoTime()
            val resolvedCells = tags.map { resolvedCell(it) }
            CaseRow(
                probe = probe,
                pass = pass,
                ms = (finished - started) / 1_000_000L,
                parseMs = (parsedAt - started) / 1_000_000L,
                resolveMs = (finished - parsedAt) / 1_000_000L,
                normalized = normalized,
                parsed = parsedCells,
                resolved = resolvedCells,
                error = null,
                errorTrace = emptyList(),
                emptyReason = if (resolvedCells.isEmpty()) {
                    "chain returned 0 tags for ${parsed.items.size} parsed item(s); the drawer would fall back to createLastResortManualTags"
                } else {
                    null
                },
            )
        } catch (t: Throwable) {
            val finished = System.nanoTime()
            val message = t.message.orEmpty().map { c -> if (c.isWhitespace()) ' ' else c }.joinToString("").take(300)
            CaseRow(
                probe = probe,
                pass = pass,
                ms = (finished - started) / 1_000_000L,
                parseMs = (parsedAt - started) / 1_000_000L,
                resolveMs = (finished - parsedAt) / 1_000_000L,
                normalized = normalized,
                parsed = parsedCells,
                resolved = emptyList(),
                error = "ERROR: ${t.javaClass.simpleName}: $message",
                errorTrace = t.stackTrace.take(8).map { it.toString() },
                emptyReason = null,
            )
        }
    }

    private fun parsedCell(item: ParsedMealItem) = ParsedCell(
        tag = item.tag,
        qty = item.quantity,
        grams = item.amountGrams,
        intent = item.amountIntent.name,
        cooking = item.cookingMethod?.name,
        excluded = item.isExcluded,
        portion = item.portion.name,
        foodQuery = item.foodQuery.ifBlank { item.tag },
        unitId = item.unitId,
        brandHint = item.brandHint,
        excludedIngredients = item.excludedIngredients.sorted(),
        trailing = item.amountIsTrailing,
    )

    private fun resolvedCell(t: ResolvedTag) = ResolvedCell(
        tag = t.tag,
        foodQuery = t.foodQuery,
        foodId = t.foodItem?.id,
        name = t.foodItem?.name,
        loggedName = t.loggedFood?.foodName,
        grams = t.amountGrams ?: t.loggedFood?.amount,
        kcal = t.loggedFood?.calories,
        status = t.resolutionStatus.name,
        questions = t.interpretationV2?.pendingQuestions.orEmpty().map { it.requestId },
        materialQuestion = t.hasMaterialQuestion(),
        oilLevel = t.oilLevel,
        oilApplied = t.oilApplied,
        appliedOilGrams = t.appliedOilGrams,
        stateConversion = t.stateConversion,
        cooking = t.cookingMethod?.name,
        foodState = t.foodState.name,
        stateAssumed = t.stateAssumed,
        intent = t.amountIntent.name,
        portion = t.portion.name,
        qty = t.quantity,
        source = t.nutritionSource.name,
        excluded = t.isExcluded,
        candidateIds = t.reviewCandidates.map { it.id },
        statusText = t.statusText,
    )

    private fun collect(): Collected {
        val original = SemanticPortionRetriever.currentSnapshot()
        try {
            // Pass A: the real dataset_knowledge.bin snapshot.
            DatasetTestHarness.restore(null)
            val statusA = SemanticPortionRetriever.status()
            val envA = buildEnv()
            val rowsA = runBlocking {
                warmUp(envA)
                probes.map { runProbe(envA, it, "A") }
            }
            // Pass B: no snapshot at all.
            val modeB = uninstallDatasetSnapshot()
            val statusB = SemanticPortionRetriever.status()
            val envB = buildEnv()
            val rowsB = runBlocking {
                probes.map { runProbe(envB, it, "B") }
            }
            return Collected(rowsA, rowsB, statusA, statusB, modeB, envA.indexedFoods)
        } finally {
            // Same convention as the other dataset tests: previous snapshot, or the real dataset when there was none.
            DatasetTestHarness.restore(original)
        }
    }

    private fun diffKinds(a: CaseRow, b: CaseRow): List<String> = buildList {
        if (a.error != b.error) add("error")
        if (a.resolved.map { it.foodId } != b.resolved.map { it.foodId }) add("ids")
        if (a.resolved.map { r1(it.grams) } != b.resolved.map { r1(it.grams) }) add("grams")
        if (a.resolved.map { r1(it.kcal) } != b.resolved.map { r1(it.kcal) }) add("kcal")
    }

    // --- Formatting ----------------------------------------------------------------------------------------------

    private fun r1(v: Double?): Double? = v?.takeIf { it.isFinite() }?.let { Math.round(it * 10.0) / 10.0 }

    private fun fmt(v: Double?): String {
        val rounded = r1(v) ?: return if (v == null) "-" else v.toString()
        return if (rounded == Math.rint(rounded)) {
            String.format(Locale.ROOT, "%.0f", rounded)
        } else {
            String.format(Locale.ROOT, "%.1f", rounded)
        }
    }

    private fun clean(s: String): String = s.replace("|", "/")

    private fun clip(s: String, max: Int): String = if (s.length <= max) s else s.take(max - 1) + "~"

    /** `tag|qty|grams|intent|cooking|excl` (+ `|p:<portion>` when not MEDIUM, `|u:<unitId>` when a unit bound). */
    private fun parsedLine(p: ParsedCell): String {
        val excl = (if (p.excluded) "EXCL" else "-") +
            (if (p.excludedIngredients.isEmpty()) "" else "!" + p.excludedIngredients.joinToString(","))
        val extra = buildString {
            if (p.portion != "MEDIUM") append("|p:").append(p.portion)
            if (p.unitId != null) append("|u:").append(p.unitId)
        }
        return clean(p.tag) + "|" + fmt(p.qty) + "|" + fmt(p.grams) + "|" + p.intent + "|" + (p.cooking ?: "-") + "|" + excl + extra
    }

    /** `foodId|name|grams|kcal|status|questions|oil|stateConversion`, prefixed `[x]` for an excluded tag. */
    private fun resolvedLine(c: ResolvedCell): String {
        val showOil = c.oilApplied || (c.appliedOilGrams ?: 0.0) > 0.0 || c.oilLevel != "medio"
        val oil = if (showOil) c.oilLevel + "/" + fmt(c.appliedOilGrams) + "g" else "-"
        val questions = if (c.questions.isEmpty()) "-" else c.questions.joinToString("+")
        val conversion = c.stateConversion?.removePrefix("weight_basis:") ?: "-"
        val name = clip(clean(c.name ?: c.loggedName ?: "-"), 26)
        return (if (c.excluded) "[x]" else "") +
            listOf(c.foodId ?: "-", name, fmt(c.grams), fmt(c.kcal), c.status, questions, oil, conversion).joinToString("|")
    }

    /** Greedy word wrap that hard-splits overlong tokens; never returns an empty list. */
    private fun wrapWords(text: String, width: Int): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(" ")) {
            var w = word
            while (w.length > width) {
                if (current.isNotEmpty()) {
                    lines += current.toString()
                    current = StringBuilder()
                }
                lines += w.take(width)
                w = w.drop(width)
            }
            when {
                current.isEmpty() -> current.append(w)
                current.length + 1 + w.length <= width -> current.append(" ").append(w)
                else -> {
                    lines += current.toString()
                    current = StringBuilder(w)
                }
            }
        }
        if (current.isNotEmpty() || lines.isEmpty()) lines += current.toString()
        return lines
    }

    /** One logical item (a parsed item / resolved tag) wrapped to [width]; continuation lines are indented. */
    private fun wrapItem(text: String, width: Int): List<String> =
        wrapWords(text, width - 2).mapIndexed { i, s -> if (i == 0) s else "  " + s }

    private fun joinCells(vararg cells: String): String = cells.joinToString(" | ")

    private fun rowLines(row: CaseRow): List<String> {
        val err = row.error
        val findingLines = wrapWords(row.probe.finding, W_FIND)
        val inputLines = wrapWords(row.probe.text, W_INPUT)
        val parsedLines = buildList<String> {
            row.normalized?.let { addAll(wrapItem("~ " + it, W_PARSED)) }
            if (row.parsed.isEmpty()) add("(no items)")
            row.parsed.forEach { addAll(wrapItem(parsedLine(it), W_PARSED)) }
        }
        val resolvedLines = when {
            err != null -> wrapItem(err, W_RESOLVED)
            row.resolved.isEmpty() -> listOf("NO_TAGS")
            else -> row.resolved.flatMap { wrapItem(resolvedLine(it), W_RESOLVED) }
        }
        val height = maxOf(findingLines.size, inputLines.size, parsedLines.size, resolvedLines.size)
        return (0 until height).map { i ->
            joinCells(
                (if (i == 0) row.probe.n.toString() else "").padStart(W_N),
                findingLines.getOrElse(i) { "" }.padEnd(W_FIND),
                inputLines.getOrElse(i) { "" }.padEnd(W_INPUT),
                if (i == 0) row.pass else " ",
                if (i == 0 && row.delta) "Δ" else " ",
                parsedLines.getOrElse(i) { "" }.padEnd(W_PARSED),
                resolvedLines.getOrElse(i) { "" }.padEnd(W_RESOLVED),
                (if (i == 0) row.ms.toString() else "").padStart(W_MS),
            ).trimEnd()
        }
    }

    private fun renderTable(rows: List<CaseRow>): String {
        val header = joinCells(
            "#".padStart(W_N),
            "finding".padEnd(W_FIND),
            "input".padEnd(W_INPUT),
            "P",
            "Δ",
            "parsed: tag|qty|g|intent|cooking|excl".padEnd(W_PARSED),
            "resolved: foodId|name|g|kcal|status|questions|oil|stateConv".padEnd(W_RESOLVED),
            "ms".padStart(W_MS),
        )
        val rule = "-".repeat(header.length)
        val sb = StringBuilder()
        sb.appendLine(rule)
        sb.appendLine(header)
        sb.appendLine(rule)
        for (row in rows) {
            rowLines(row).forEach { sb.appendLine(it) }
            if (row.pass == "B") sb.appendLine(rule)
        }
        return sb.toString()
    }

    private fun percentile(sorted: List<Long>, p: Double): Long =
        if (sorted.isEmpty()) 0L else sorted[((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)]

    private fun summaryLines(rows: List<CaseRow>): List<String> {
        fun stats(label: String, list: List<CaseRow>): String {
            val ms = list.map { it.ms }.sorted()
            return label + ": rows=" + list.size + " errors=" + list.count { it.error != null } +
                " noTags=" + list.count { it.emptyReason != null } +
                " ms p50=" + percentile(ms, 0.50) + " p95=" + percentile(ms, 0.95) + " max=" + (ms.lastOrNull() ?: 0L)
        }
        val deltaRows = rows.filter { it.pass == "A" && it.delta }
        val slow = rows.filter { it.ms >= MAX_MS_PER_INPUT }
        val deltaText = if (deltaRows.isEmpty()) {
            "none"
        } else {
            deltaRows.joinToString("; ") { "#" + it.probe.n + " (" + it.deltaKinds.joinToString(",") + ")" }
        }
        return listOf(
            stats("pass A", rows.filter { it.pass == "A" }),
            stats("pass B", rows.filter { it.pass == "B" }),
            "Δ inputs (" + deltaRows.size + "): " + deltaText,
            "error rows: " + rows.filter { it.error != null }.joinToString("; ") { "#" + it.probe.n + it.pass }.ifEmpty { "none" },
            "no-tag rows: " + rows.filter { it.emptyReason != null }.joinToString("; ") { "#" + it.probe.n + it.pass }.ifEmpty { "none" },
            "rows at or above " + MAX_MS_PER_INPUT + " ms: " +
                slow.joinToString("; ") { "#" + it.probe.n + it.pass + "=" + it.ms + "ms" }.ifEmpty { "none" },
        )
    }

    private fun renderReport(data: Collected, rows: List<CaseRow>): String {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("=== BlindMealCorpusProbe (WP-N0): " + probes.size + " inputs x 2 passes; OFF fixture rows=" + offFixture.size + "; indexed foods=" + data.indexedFoods + " ===")
        sb.appendLine("chain: parseMealDescription(text) -> TagResolver(port).resolveAll(parsed)  [resolveAll already enriches; the drawer currently enriches a second time]")
        sb.appendLine("pass A: real snapshot (checksum=" + data.statusA.checksum + ", documents=" + data.statusA.documentCount + ", tokens=" + data.statusA.tokenCount + ", priors=" + data.statusA.portionPriorCount + ")")
        sb.appendLine("pass B: " + data.modeB + " (ready=" + data.statusB.ready + ", documents=" + data.statusB.documentCount + ")")
        sb.appendLine("JVM: " + System.getProperty("java.vendor") + " " + System.getProperty("java.version") + "  (regex word boundary, backslash-b, is ASCII-only on JDK >= 19; Android/ICU treats accented letters as word chars - see #34)")
        sb.appendLine("legend: \u0394 = pass A and pass B differ in resolved foodIds/grams/kcal; ms = parse + resolve of one input on a warm JVM (an untimed full warm-up pass precedes pass A); [x] = excluded tag; ~ = normalized text; p:/u: = size preset / bound unit")
        sb.append(renderTable(rows))
        sb.appendLine()
        summaryLines(rows).forEach { sb.appendLine(it) }
        sb.appendLine()
        sb.appendLine("EXPECT (post-remediation targets from the plan; informational, never asserted):")
        probes.forEach { p ->
            sb.appendLine("#" + String.format(Locale.ROOT, "%02d", p.n) + " [" + p.finding + "] " + p.text + "  =>  " + p.expect)
        }
        return sb.toString()
    }

    // --- JSON ----------------------------------------------------------------------------------------------------

    private fun parsedJson(p: ParsedCell): JsonObject = buildJsonObject {
        put("tag", p.tag)
        put("qty", p.qty)
        put("grams", r1(p.grams))
        put("intent", p.intent)
        put("cooking", p.cooking)
        put("excl", p.excluded)
        put("portion", p.portion)
        put("foodQuery", p.foodQuery)
        put("unitId", p.unitId)
        put("brandHint", p.brandHint)
        put("excludedIngredients", JsonArray(p.excludedIngredients.map { JsonPrimitive(it) }))
        put("trailingAmount", p.trailing)
    }

    private fun resolvedJson(c: ResolvedCell): JsonObject = buildJsonObject {
        put("tag", c.tag)
        put("foodQuery", c.foodQuery)
        put("foodId", c.foodId)
        put("name", c.name)
        put("loggedName", c.loggedName)
        put("grams", r1(c.grams))
        put("kcal", r1(c.kcal))
        put("status", c.status)
        put("questions", JsonArray(c.questions.map { JsonPrimitive(it) }))
        put("materialQuestion", c.materialQuestion)
        put("oilLevel", c.oilLevel)
        put("oilApplied", c.oilApplied)
        put("appliedOilGrams", r1(c.appliedOilGrams))
        put("stateConversion", c.stateConversion)
        put("cooking", c.cooking)
        put("foodState", c.foodState)
        put("stateAssumed", c.stateAssumed)
        put("intent", c.intent)
        put("portion", c.portion)
        put("qty", c.qty)
        put("source", c.source)
        put("excluded", c.excluded)
        put("candidateIds", JsonArray(c.candidateIds.map { JsonPrimitive(it) }))
        put("statusText", c.statusText)
    }

    private fun caseJson(row: CaseRow): JsonObject = buildJsonObject {
        put("n", row.probe.n)
        put("finding", row.probe.finding)
        put("input", row.probe.text)
        put("expect", row.probe.expect)
        put("pass", row.pass)
        put("delta", row.delta)
        put("deltaKinds", JsonArray(row.deltaKinds.map { JsonPrimitive(it) }))
        put("ms", row.ms)
        put("parseMs", row.parseMs)
        put("resolveMs", row.resolveMs)
        put("normalized", row.normalized)
        put("error", row.error)
        put("errorTrace", JsonArray(row.errorTrace.map { JsonPrimitive(it) }))
        put("emptyReason", row.emptyReason)
        put("parsedLine", row.parsed.joinToString(" ; ") { parsedLine(it) })
        put("resolvedLine", row.resolved.joinToString(" ; ") { resolvedLine(it) })
        put("parsed", JsonArray(row.parsed.map { parsedJson(it) }))
        put("resolved", JsonArray(row.resolved.map { resolvedJson(it) }))
    }

    private fun statusJson(status: SemanticPortionRetriever.DatasetStatus): JsonObject = buildJsonObject {
        put("ready", status.ready)
        put("formatVersion", status.formatVersion)
        put("checksum", status.checksum)
        put("documentCount", status.documentCount)
        put("tokenCount", status.tokenCount)
        put("portionPriorCount", status.portionPriorCount)
    }

    private fun writeJson(data: Collected, rows: List<CaseRow>): File {
        val notes = listOf(
            "ms is parse + resolve for one input on a warm JVM; expect is the post-remediation target from the plan and is never asserted",
            "regex word boundary (backslash-b) is ASCII-only on JDK >= 19 while Android/ICU treats accented letters as word chars: input 34 cannot be measured on the JVM",
            "the plan lists 11 OFF rows for a fixture of 12; Banana Bread (FluencyGoldenCorpusTest) is the 12th",
        )
        val report = buildJsonObject {
            put("probe", "blind-meal-corpus-probe-v1")
            put("workPackage", "WP-N0")
            put("chain", "parseMealDescription(text) -> TagResolver(port).resolveAll(parsed); resolveAll already enriches (no second enrich)")
            put("inputs", probes.size)
            put("jvm", System.getProperty("java.vendor").orEmpty() + " " + System.getProperty("java.version").orEmpty())
            put("passA", buildJsonObject {
                put("description", "real dataset_knowledge.bin snapshot installed via DatasetTestHarness.restore(null)")
                put("dataset", statusJson(data.statusA))
            })
            put("passB", buildJsonObject {
                put("description", data.modeB)
                put("dataset", statusJson(data.statusB))
            })
            put("offFixtureIds", JsonArray(offFixture.map { JsonPrimitive(it.foodId) }))
            put("indexedFoods", data.indexedFoods)
            put("notes", JsonArray(notes.map { JsonPrimitive(it) }))
            put("summary", JsonArray(summaryLines(rows).map { JsonPrimitive(it) }))
            put("cases", JsonArray(rows.map { caseJson(it) }))
        }
        return File(REPORT_PATH).apply {
            parentFile?.mkdirs()
            writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report))
        }
    }

    // --- Tests ---------------------------------------------------------------------------------------------------

    @Test
    fun `fixture builds`() {
        assertEquals("audit inputs", 60, probes.size)
        assertEquals("inputs are numbered 1..60 in order", (1..60).toList(), probes.map { it.n })
        assertTrue(
            "every input is non-blank and within the 600-char bound",
            probes.all { it.text.isNotBlank() && it.text.length <= MAX_INPUT_CHARS },
        )
        assertEquals("OFF fixture rows", 12, offFixture.size)
        assertEquals("OFF fixture ids are unique", offFixture.size, offFixture.map { it.foodId }.toSet().size)
        val env = buildEnv()
        assertTrue("the index holds the curated catalog plus the OFF rows: " + env.indexedFoods, env.indexedFoods > offFixture.size)
        runBlocking {
            val zero = env.port.getFoodById("off_coca_cola_zero")
            assertEquals("Coca-Cola", zero?.brand)
            assertEquals(0.2, zero!!.calories, 0.0)
            assertEquals(1000.0, env.port.getFoodById("off_hallulla_ideal_1kg")!!.portionGrams!!, 0.0)
            assertEquals("Avena", env.port.getFoodById("off_7800120162489")?.brand)
        }
    }

    @Test
    fun `probe prints table for all 60 inputs`() {
        val data = collect()
        val kinds = data.rowsA.indices.map { diffKinds(data.rowsA[it], data.rowsB[it]) }
        val rows = data.rowsA.indices.flatMap { i ->
            listOf(data.rowsA[i].copy(deltaKinds = kinds[i]), data.rowsB[i].copy(deltaKinds = kinds[i]))
        }

        println(renderReport(data, rows))
        val file = writeJson(data, rows)

        // Diagnostic (harness-integrity) assertions only; expect is never compared.
        assertEquals("audit inputs", 60, probes.size)
        assertEquals("one row per input per pass", probes.size * 2, rows.size)
        val unexplained = rows.filter { it.error == null && it.resolved.isEmpty() && it.emptyReason == null }
        assertTrue(
            "rows with neither a tag nor an error note: " + unexplained.map { "#" + it.probe.n + it.pass },
            unexplained.isEmpty(),
        )
        val slow = rows.filter { it.ms >= MAX_MS_PER_INPUT }
        assertTrue(
            "inputs at or above " + MAX_MS_PER_INPUT + " ms: " + slow.map { "#" + it.probe.n + it.pass + "=" + it.ms + "ms" },
            slow.isEmpty(),
        )
        assertTrue("blind-probe.json written at " + file.absolutePath, file.isFile && file.length() > 0L)
        val written = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals("JSON carries one object per input per pass", rows.size, written["cases"]!!.jsonArray.size)
    }
}
