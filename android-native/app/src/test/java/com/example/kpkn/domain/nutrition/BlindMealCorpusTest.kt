package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.data.db.toFoodItem
import com.example.kpkn.data.food.FOOD_ALIASES
import com.example.kpkn.data.food.buildFoodDatabase
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.NutritionCalibrationProfile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * WP-N12 - the blind meal corpus: the 60 audit descriptions of the WP-N0 probe promoted to a permanent gate.
 *
 * [BlindMealCorpusProbeTest] stays the diagnostic (a fixed-width table, no expectations). This class runs the same live chain
 *
 *     parseMealDescription(text) -> TagResolver(port, calibration).resolveAll(parsed)
 *
 * and compares it with references that do NOT come from the engine (`docs/contracts/nutrition_interpretation_v2.md`,
 * "Evaluation"): each [Mention] carries a reference mass, a reference energy and a `kcalSource`, written from
 *  - USDA FoodData Central entries (SR Legacy / Foundation, `data/usdaFoodsOffline.json`): kcal per 100 g and USDA household weights;
 *  - the Chilean INTA composition table (marraqueta, 272 kcal/100 g, unit 100 g) as reproduced by public nutrition tables, and
 *    published Chilean dish tables (the primary INTA book is not available offline: the copy is cited, not verified);
 *  - the sum of USDA components for composite dishes (the ingredient weights are written in the source text).
 * Never from `HouseholdPortions`, the `FoodDatabase` rows or the dataset priors. [Basis] tells how a mass was obtained
 * (declared in the text, a published household measure, or the author's typical serving for a vague portion).
 *
 * Five dimensions are counted separately (counts and percentages are printed for every mode):
 *  - identity: the resolved row is the expected catalog id (or one of `alsoAcceptable`); an `unresolvedQuery` mention must stay a
 *    named estimate (no catalog row) - a modified recipe never borrows the row of the whole dish. Per mention.
 *  - coverage (additions / omissions): every mention has a tag and no active tag is left over. Per description.
 *  - grams within 25 % of the reference. Per mention.
 *  - kcal within 20 % of the reference, +-0.5 kcal when `exactKcal` (mass and profile declared in the text), and never tighter
 *    than [KCAL_FLOOR] kcal for the others (a 1 kcal rounding of a zero-energy drink is not an error). Per mention.
 *  - question: `hasMaterialQuestion()` equals the reference yes/no (ask for unmatched dishes, a bare chicken without a cut or
 *    a mass, frying or sauteing raw food without a stated oil, a modified recipe, raw-or-cooked staples given by mass). Per mention.
 * Tags are paired with mentions by identity first, then by position, so a wrong row still gets its grams, kcal and question judged.
 *
 * Four modes run: the real `dataset_knowledge.bin` snapshot and no snapshot, each with an empty history and with a
 * [NutritionCalibrationProfile] holding earlier identity mappings (they agree with the references, so applying them must change
 * nothing; "completo" is a trap: the remembered whole dish must not replace the declared "sin mayo"). The profile has no templates and no
 * state preference: `NutritionCalibrationProfile` carries identity mappings, state preferences and portions, and a state preference or a
 * remembered portion legitimately changes the answer of the description it matches.
 *
 * Thresholds (see [thresholds]): the worst mode's measured percentage rounded DOWN to a multiple of 5. Identity, coverage and question are capped
 * at the plan minimum (90) so that they keep their headroom; grams and kcal are the ratchet of the corpus (WP-N8b): they measured below their plan
 * minimum (80) and keep following their measured floor now that they are above it, so the failing cases are printed with their references. Guards: no
 * description is copied from the semantic dataset, and the control cases (#13, #31, #51) are asserted one by one on identity and question.
 *
 * Run: `./gradlew :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.nutrition.BlindMealCorpusTest" -i`;
 * the report is `app/build/reports/nutrition-reliability/blind-corpus.json`.
 */
class BlindMealCorpusTest {

    // --- Model -------------------------------------------------------------------------------------------------

    /** How [Mention.gramsRef] was obtained. */
    private enum class Basis { MASS, MEASURE, TYPICAL }

    /** One food the description names, with its reference. `expectedId` XOR `unresolvedQuery`. */
    private data class Mention(
        val label: String,
        val expectedId: String? = null,
        val unresolvedQuery: String? = null,
        val alsoAcceptable: Set<String> = emptySet(),
        val gramsRef: Double,
        val gramsBasis: Basis,
        val kcalRef: Double,
        val kcalSource: String,
        val question: Boolean,
        val exactKcal: Boolean = false,
    )

    private data class BlindCase(
        val n: Int,
        val text: String,
        val mentions: List<Mention>,
        val author: String = AUTHOR,
        val control: Boolean = false,
    )

    private enum class Dimension(val label: String, val planMinimum: Int) {
        IDENTITY("identity", 90),
        COVERAGE("coverage", 90),
        GRAMS("grams", 80),
        KCAL("kcal", 80),
        QUESTION("question", 90),
    }

    private class Env(val port: FoodResolutionPort, val indexedFoods: Int)

    private class MentionResult(val case: BlindCase, val mention: Mention, val tag: ResolvedTag?, val identity: Boolean) {
        val gotId: String? = tag?.foodItem?.id
        val gotGrams: Double? = tag?.let { it.amountGrams ?: it.loggedFood?.amount }
        val gotKcal: Double? = tag?.loggedFood?.calories
        val gotQuestion: Boolean? = tag?.hasMaterialQuestion()
        val grams: Boolean = gotGrams != null && abs(gotGrams - mention.gramsRef) <= GRAMS_TOLERANCE * mention.gramsRef + EPS
        val kcal: Boolean = gotKcal != null && abs(gotKcal - mention.kcalRef) <= kcalTolerance(mention) + EPS
        val question: Boolean = gotQuestion != null && gotQuestion == mention.question

        /** Informational (not thresholded): kcal per gram within 20 % of the reference density, whatever the portion error is. */
        val density: Boolean = gotKcal != null && gotGrams != null && gotGrams > 0.0 && abs(gotKcal / gotGrams - mention.kcalRef / mention.gramsRef) <=
            max(KCAL_TOLERANCE * mention.kcalRef / mention.gramsRef, DENSITY_FLOOR) + EPS
    }

    private class CaseResult(val case: BlindCase, val results: List<MentionResult>, val additions: List<ResolvedTag>, val omissions: Int) {
        val coverage: Boolean = additions.isEmpty() && omissions == 0
    }

    private class Rate(val passed: Int, val total: Int) {
        val percent: Double get() = if (total == 0) 0.0 else 100.0 * passed / total
    }

    private class ModeRun(val label: String, val snapshot: Boolean, val profile: Boolean, val cases: List<CaseResult>) {
        val results: List<MentionResult> get() = cases.flatMap { it.results }

        fun rate(d: Dimension): Rate = when (d) {
            Dimension.IDENTITY -> Rate(results.count { it.identity }, results.size)
            Dimension.COVERAGE -> Rate(cases.count { it.coverage }, cases.size)
            Dimension.GRAMS -> Rate(results.count { it.grams }, results.size)
            Dimension.KCAL -> Rate(results.count { it.kcal }, results.size)
            Dimension.QUESTION -> Rate(results.count { it.question }, results.size)
        }

        fun densityRate(): Rate = Rate(results.count { it.density }, results.size)

        /** Grams or kcal pass rate of the mentions whose reference mass has this [basis]. */
        fun rateBy(d: Dimension, basis: Basis): Rate {
            val subset = results.filter { it.mention.gramsBasis == basis }
            return Rate(subset.count { if (d == Dimension.GRAMS) it.grams else it.kcal }, subset.size)
        }
    }

    private class Collected(val modes: List<ModeRun>, val modeB: String, val indexedFoods: Int)

    private companion object {
        const val AUTHOR = "WP-N0 audit inputs; WP-N12 references (USDA FDC, INTA, published Chilean tables)"
        const val GRAMS_TOLERANCE = 0.25
        const val KCAL_TOLERANCE = 0.20
        const val KCAL_EXACT_TOLERANCE = 0.5
        const val KCAL_FLOOR = 2.0
        const val DENSITY_FLOOR = 0.02
        const val EPS = 1e-9
        const val MIN_TOKENS_FOR_CONTAINMENT = 7
        const val REPORT_PATH = "build/reports/nutrition-reliability/blind-corpus.json"

        /** The four runs are computed once and shared by the tests of this class. */
        @Volatile var cached: Collected? = null

        fun kcalTolerance(m: Mention): Double =
            if (m.exactKcal) KCAL_EXACT_TOLERANCE else max(KCAL_TOLERANCE * m.kcalRef, KCAL_FLOOR)
    }

    // --- The 60 cases (references: see the KDoc and each kcalSource) ---------------------------------------------

    private val corpus: List<BlindCase> = listOf(
        BlindCase(n = 1, text = "almorcé un plato de porotos con riendas y un pan con palta, después un café con leche",
            mentions = listOf(
                Mention("plato de porotos con riendas", expectedId = "cl029", gramsRef = 350.0, gramsBasis = Basis.TYPICAL, kcalRef = 355.6, question = false,
                    kcalSource = "plate of 400 g: 150 g white beans cooked (FDC 175203 139 kcal/100 g) + 80 g pasta cooked (FDC 169737 158 kcal/100 g) + 50 g squash cooked (FDC 170490 37 kcal/100 g) + 20 g chard (FDC 169991 19 kcal/100 g) + 5 g olive oil sofrito (FDC 171413 884 kcal/100 g) + 100 g broth (5 kcal/100 g) = 101.6 kcal/100 g (recipe of the 400 g plate: beans, pasta strips, squash, greens, broth); plate = 350 g (1.5 cups); cross-check arise-app.com: 90 kcal/100 g and 360 kcal per 400 g portion"),
                Mention("un pan con palta", expectedId = "cl025", gramsRef = 140.0, gramsBasis = Basis.MEASURE, kcalRef = 336.0, question = false,
                    kcalSource = "one bread unit 100 g x 272 kcal/100 g (INTA 2018 marraqueta, secondary copy) + 40 g avocado (FDC 171705 160 kcal/100 g)"),
                Mention("un café con leche", expectedId = "gen145", gramsRef = 237.0, gramsBasis = Basis.MEASURE, kcalRef = 101.91, question = false,
                    kcalSource = "FDC 2710386 43 kcal/100 g; 1 cup = 237 g (USDA); latte as the nearest milk-coffee entry"),
            ),
        ),
        BlindCase(n = 2, text = "2 yogures y 3 tomates",
            mentions = listOf(
                Mention("2 yogures", expectedId = "gen087", gramsRef = 250.0, gramsBasis = Basis.MEASURE, kcalRef = 152.5, question = false,
                    kcalSource = "FDC 171284 61 kcal/100 g; 2 single-serve pots of 125 g (Chile)"),
                Mention("3 tomates", expectedId = "gen026", gramsRef = 369.0, gramsBasis = Basis.MEASURE, kcalRef = 66.42, question = false,
                    kcalSource = "FDC 170457 18 kcal/100 g; 3 medium tomatoes of 123 g (USDA SR Legacy household weight)"),
            ),
        ),
        BlindCase(n = 3, text = "un vaso de agua",
            mentions = listOf(
                Mention("un vaso de agua", expectedId = "gen143", gramsRef = 237.0, gramsBasis = Basis.MEASURE, kcalRef = 0.0, question = false,
                    kcalSource = "FDC 173647 0 kcal/100 g; 1 cup = 237 g (USDA household weight of water)"),
            ),
        ),
        BlindCase(n = 4, text = "150 g salmón a la parrilla",
            mentions = listOf(
                Mention("150 g salmón a la parrilla", expectedId = "gen009", alsoAcceptable = setOf("gen009p", "gen009h"), gramsRef = 150.0, gramsBasis = Basis.MASS, kcalRef = 309.0, question = false,
                    kcalSource = "FDC 175168 206 kcal/100 g; cooked weight as eaten (the raw entry FDC 175167 gives 208 kcal/100 g, the same energy per gram)"),
            ),
        ),
        BlindCase(n = 5, text = "una lata de coca cola",
            mentions = listOf(
                Mention("una lata de coca cola", expectedId = "off_coca_cola_original_350ml", gramsRef = 368.0, gramsBasis = Basis.MEASURE, kcalRef = 154.56, question = false,
                    kcalSource = "FDC 174852 42 kcal/100 g; 1 can (12 fl oz) = 368 g (USDA household weight)"),
            ),
        ),
        BlindCase(n = 6, text = "al menos 2 huevos",
            mentions = listOf(
                Mention("2 huevos", expectedId = "gen007", gramsRef = 100.0, gramsBasis = Basis.MEASURE, kcalRef = 155.0, question = false,
                    kcalSource = "FDC 173424 155 kcal/100 g; 2 large eggs of 50 g (USDA SR Legacy)"),
            ),
        ),
        BlindCase(n = 7, text = "Desayuno: 2 huevos. Almuerzo: arroz con pollo.",
            mentions = listOf(
                Mention("2 huevos", expectedId = "gen007", gramsRef = 100.0, gramsBasis = Basis.MEASURE, kcalRef = 155.0, question = false,
                    kcalSource = "FDC 173424 155 kcal/100 g; 2 large eggs of 50 g (USDA SR Legacy)"),
                Mention("arroz (del arroz con pollo)", expectedId = "gen005", gramsRef = 200.0, gramsBasis = Basis.TYPICAL, kcalRef = 260.0, question = false,
                    kcalSource = "FDC 168878 130 kcal/100 g; rice on a lunch plate = 200 g (1.27 USDA cups of 158 g)"),
                Mention("pollo (del arroz con pollo)", expectedId = "gen004", alsoAcceptable = setOf("gen003c", "gen003h", "gen003p", "gen003v"), gramsRef = 130.0, gramsBasis = Basis.TYPICAL, kcalRef = 215.8, question = true,
                    kcalSource = "FDC 331960 166 kcal/100 g; one cooked presa = 130 g (between USDA half breast 86 g and whole breast 172 g); no cut named, no mass given"),
            ),
        ),
        BlindCase(n = 8, text = "pollo a la plancha y papas a lo pobre",
            mentions = listOf(
                Mention("pollo a la plancha", expectedId = "gen003c", alsoAcceptable = setOf("gen004", "gen003h", "gen003p", "gen003v"), gramsRef = 130.0, gramsBasis = Basis.TYPICAL, kcalRef = 214.5, question = true,
                    kcalSource = "FDC 171477 165 kcal/100 g; one cooked presa = 130 g (between USDA half breast 86 g and whole breast 172 g); no cut named, no mass given"),
                Mention("papas a lo pobre", unresolvedQuery = "a lo pobre", gramsRef = 250.0, gramsBasis = Basis.TYPICAL, kcalRef = 630.2, question = true,
                    kcalSource = "plate: 150 g french fries (FDC 170698 312 kcal/100 g) + 50 g fried egg (FDC 173423 196 kcal/100 g) + 50 g onion (FDC 170000 40 kcal/100 g) + 5 g olive oil for the onion (FDC 171413 884 kcal/100 g) (fries topped with fried onion and a fried egg; no catalog dish)"),
            ),
        ),
        BlindCase(n = 9, text = "agua con gas",
            mentions = listOf(
                Mention("agua con gas", expectedId = "gen151", gramsRef = 237.0, gramsBasis = Basis.MEASURE, kcalRef = 0.0, question = false,
                    kcalSource = "FDC 174842 0 kcal/100 g; 1 cup = 237 g; club soda is the USDA entry for carbonated water"),
            ),
        ),
        BlindCase(n = 10, text = "helado de vainilla y chocolate",
            mentions = listOf(
                Mention("helado de vainilla y chocolate", expectedId = "gen193", gramsRef = 132.0, gramsBasis = Basis.MEASURE, kcalRef = 279.18, question = false,
                    kcalSource = "two flavours = two scoops of 66 g (USDA 1/2 cup): 66 g vanilla (FDC 167575 207 kcal/100 g) + 66 g chocolate (FDC 168809 216 kcal/100 g)"),
            ),
        ),
        BlindCase(n = 11, text = "200 g de arroz con pollo",
            mentions = listOf(
                Mention("arroz (mitad del plato)", expectedId = "gen005", gramsRef = 100.0, gramsBasis = Basis.MASS, kcalRef = 130.0, question = false,
                    kcalSource = "FDC 168878 130 kcal/100 g; half of the 200 g cooked dish"),
                Mention("pollo (mitad del plato)", expectedId = "gen004", alsoAcceptable = setOf("gen003c", "gen003h", "gen003p", "gen003v"), gramsRef = 100.0, gramsBasis = Basis.MASS, kcalRef = 166.0, question = false,
                    kcalSource = "FDC 331960 166 kcal/100 g; half of the 200 g cooked dish"),
            ),
        ),
        BlindCase(n = 12, text = "asado",
            mentions = listOf(
                Mention("asado", expectedId = "gen093c", gramsRef = 150.0, gramsBasis = Basis.TYPICAL, kcalRef = 528.0, question = false,
                    kcalSource = "FDC 168676 352 kcal/100 g; a grilled beef rib serving of 150 g cooked (broiled whole rib, choice)"),
            ),
        ),
        BlindCase(n = 13, text = "un guiso de lentejas", control = true,
            mentions = listOf(
                Mention("un guiso de lentejas", expectedId = "gen012", alsoAcceptable = setOf("gen012h"), gramsRef = 198.0, gramsBasis = Basis.MEASURE, kcalRef = 229.68, question = false,
                    kcalSource = "FDC 172421 116 kcal/100 g; 1 cup = 198 g (USDA household weight of cooked lentils)"),
            ),
        ),
        BlindCase(n = 14, text = "500 cc de leche",
            mentions = listOf(
                Mention("500 cc de leche", expectedId = "gen016", gramsRef = 515.0, gramsBasis = Basis.MASS, kcalRef = 314.15, question = false, exactKcal = true,
                    kcalSource = "FDC 171265 61 kcal/100 g; 500 mL x 1.03 g/mL (USDA 1 cup = 244 g); whole milk is the default leche"),
            ),
        ),
        BlindCase(n = 15, text = "medio litro de jugo de naranja",
            mentions = listOf(
                Mention("medio litro de jugo de naranja", expectedId = "gen103", gramsRef = 524.0, gramsBasis = Basis.MASS, kcalRef = 235.8, question = false,
                    kcalSource = "FDC 169098 45 kcal/100 g; 500 mL x 1.048 g/mL (USDA 1 cup = 248 g)"),
            ),
        ),
        BlindCase(n = 16, text = "cuarto de kilo de carne molida",
            mentions = listOf(
                Mention("250 g de carne molida", expectedId = "gen010", alsoAcceptable = setOf("gen010p"), gramsRef = 250.0, gramsBasis = Basis.MASS, kcalRef = 537.5, question = false,
                    kcalSource = "FDC 171796 215 kcal/100 g; sold by the kilo, so the raw ground beef 85/15 entry"),
            ),
        ),
        BlindCase(n = 17, text = "1 lt de agua",
            mentions = listOf(
                Mention("1 lt de agua", expectedId = "gen143", gramsRef = 1000.0, gramsBasis = Basis.MASS, kcalRef = 0.0, question = false, exactKcal = true,
                    kcalSource = "FDC 173647 0 kcal/100 g; 1 L of water = 1000 g"),
            ),
        ),
        BlindCase(n = 18, text = "una botella de agua",
            mentions = listOf(
                Mention("una botella de agua", expectedId = "gen143", gramsRef = 500.0, gramsBasis = Basis.MEASURE, kcalRef = 0.0, question = false,
                    kcalSource = "FDC 173647 0 kcal/100 g; single-serve bottle of 500 mL"),
            ),
        ),
        BlindCase(n = 19, text = "un jugo de naranja",
            mentions = listOf(
                Mention("un jugo de naranja", expectedId = "gen103", gramsRef = 248.0, gramsBasis = Basis.MEASURE, kcalRef = 111.6, question = false,
                    kcalSource = "FDC 169098 45 kcal/100 g; 1 cup = 248 g (USDA household weight of orange juice)"),
            ),
        ),
        BlindCase(n = 20, text = "plato grande de arroz",
            mentions = listOf(
                Mention("plato grande de arroz", expectedId = "gen005", gramsRef = 296.25, gramsBasis = Basis.TYPICAL, kcalRef = 385.12, question = false,
                    kcalSource = "FDC 168878 130 kcal/100 g; a plate is 1.5 cups of 158 g and \"grande\" is x1.25"),
            ),
        ),
        BlindCase(n = 21, text = "un plato de arroz",
            mentions = listOf(
                Mention("un plato de arroz", expectedId = "gen005", gramsRef = 237.0, gramsBasis = Basis.TYPICAL, kcalRef = 308.1, question = false,
                    kcalSource = "FDC 168878 130 kcal/100 g; a plate is 1.5 cups of 158 g (USDA household weight)"),
            ),
        ),
        BlindCase(n = 22, text = "2 manzanas grandes",
            mentions = listOf(
                Mention("2 manzanas grandes", expectedId = "gen001", gramsRef = 484.0, gramsBasis = Basis.MEASURE, kcalRef = 251.68, question = false,
                    kcalSource = "FDC 171688 52 kcal/100 g; 2 large apples of 242 g (USDA SR Legacy household weight)"),
            ),
        ),
        BlindCase(n = 23, text = "pollo",
            mentions = listOf(
                Mention("pollo", expectedId = "gen004", alsoAcceptable = setOf("gen003c", "gen003h", "gen003p", "gen003v"), gramsRef = 130.0, gramsBasis = Basis.TYPICAL, kcalRef = 215.8, question = true,
                    kcalSource = "FDC 331960 166 kcal/100 g; one cooked presa = 130 g (between USDA half breast 86 g and whole breast 172 g); no cut named, no mass given"),
            ),
        ),
        BlindCase(n = 24, text = "uva",
            mentions = listOf(
                Mention("uva", expectedId = "gen053", gramsRef = 140.0, gramsBasis = Basis.MEASURE, kcalRef = 96.6, question = false,
                    kcalSource = "FDC 174683 69 kcal/100 g; FDA reference amount for fruit (21 CFR 101.12) = 140 g (USDA 1 cup = 151 g)"),
            ),
        ),
        BlindCase(n = 25, text = "wevos",
            mentions = listOf(
                Mention("wevos (plural)", expectedId = "gen007", gramsRef = 100.0, gramsBasis = Basis.MEASURE, kcalRef = 155.0, question = false,
                    kcalSource = "FDC 173424 155 kcal/100 g; a bare plural is at least two eggs of 50 g (USDA SR Legacy)"),
            ),
        ),
        BlindCase(n = 26, text = "ensalada de repollo",
            mentions = listOf(
                Mention("ensalada de repollo", unresolvedQuery = "ensalada de repollo", alsoAcceptable = setOf("gen067"), gramsRef = 150.0, gramsBasis = Basis.TYPICAL, kcalRef = 77.28, question = true,
                    kcalSource = "salad: 150 g cabbage (FDC 169975 25 kcal/100 g) + 4.5 g olive oil (1 tsp) (FDC 171413 884 kcal/100 g); side salad of 150 g"),
            ),
        ),
        BlindCase(n = 27, text = "tres leches",
            mentions = listOf(
                Mention("tres leches", expectedId = "sn_cr_tresleches", gramsRef = 150.0, gramsBasis = Basis.TYPICAL, kcalRef = 445.5, question = false,
                    kcalSource = "FDC 172707 297 kcal/100 g; one slice of 150 g; sponge cake (prepared from recipe) as the nearest USDA entry, the milk soak is not counted"),
            ),
        ),
        BlindCase(n = 28, text = "un sandwich de jamón y queso y una coca cola",
            mentions = listOf(
                Mention("pan del sándwich", expectedId = "gen019", alsoAcceptable = setOf("gen089", "cl010", "cl013", "cl014"), gramsRef = 60.0, gramsBasis = Basis.TYPICAL, kcalRef = 159.6, question = false,
                    kcalSource = "FDC 174924 266 kcal/100 g; two slices of sandwich bread = 60 g"),
                Mention("jamón", expectedId = "gen094", gramsRef = 40.0, gramsBasis = Basis.TYPICAL, kcalRef = 42.8, question = false,
                    kcalSource = "FDC 173863 107 kcal/100 g; two slices = 40 g; deli ham 96% fat free"),
                Mention("queso", expectedId = "gen088", alsoAcceptable = setOf("gen157", "gen047"), gramsRef = 30.0, gramsBasis = Basis.TYPICAL, kcalRef = 106.8, question = false,
                    kcalSource = "FDC 171241 356 kcal/100 g; one slice = 30 g; gouda is the usual sandwich cheese in Chile"),
                Mention("una coca cola", expectedId = "off_coca_cola_original_350ml", gramsRef = 368.0, gramsBasis = Basis.MEASURE, kcalRef = 154.56, question = false,
                    kcalSource = "FDC 174852 42 kcal/100 g; 1 can (12 fl oz) = 368 g (USDA household weight)"),
            ),
        ),
        BlindCase(n = 29, text = "pechuga de pollo frita sin aceite 150 g",
            mentions = listOf(
                Mention("150 g de pechuga frita sin aceite", expectedId = "gen003c", alsoAcceptable = setOf("gen003", "gen004", "gen003p"), gramsRef = 150.0, gramsBasis = Basis.MASS, kcalRef = 247.5, question = false,
                    kcalSource = "FDC 171477 165 kcal/100 g; dry pan: no oil, so the plain cooked breast entry"),
            ),
        ),
        BlindCase(n = 30, text = "150 g de pechuga cruda frita",
            mentions = listOf(
                Mention("150 g de pechuga cruda frita", expectedId = "gen003", gramsRef = 150.0, gramsBasis = Basis.MASS, kcalRef = 259.56, question = true,
                    kcalSource = "150 g raw (FDC 171077 120 kcal/100 g) + about 9 g absorbed frying oil (FDC 171413 884 kcal/100 g)"),
            ),
        ),
        BlindCase(n = 31, text = "papas fritas 150 g", control = true,
            mentions = listOf(
                Mention("150 g de papas fritas", expectedId = "gen021f", gramsRef = 150.0, gramsBasis = Basis.MASS, kcalRef = 468.0, question = false, exactKcal = true,
                    kcalSource = "FDC 170698 312 kcal/100 g; deep-fried potatoes (fast-food fries) as served"),
            ),
        ),
        BlindCase(n = 32, text = "huevos revueltos",
            mentions = listOf(
                Mention("huevos revueltos (plural)", expectedId = "gen007r", gramsRef = 100.0, gramsBasis = Basis.MEASURE, kcalRef = 185.0, question = false,
                    kcalSource = "FDC 2707198 185 kcal/100 g; a bare plural is at least two eggs of 50 g; FNDDS scrambled egg, fat not specified"),
            ),
        ),
        BlindCase(n = 33, text = "100 g champiñones salteados",
            mentions = listOf(
                Mention("100 g champiñones salteados", expectedId = "gen038", gramsRef = 100.0, gramsBasis = Basis.MASS, kcalRef = 70.0, question = true,
                    kcalSource = "FDC 2709941 70 kcal/100 g; FNDDS: fresh mushrooms cooked with oil, as eaten; the amount of oil is not said"),
            ),
        ),
        BlindCase(n = 34, text = "huevo poché",
            mentions = listOf(
                Mention("huevo poché", expectedId = "gen007", gramsRef = 50.0, gramsBasis = Basis.MEASURE, kcalRef = 71.5, question = false,
                    kcalSource = "FDC 172186 143 kcal/100 g; 1 large egg of 50 g, poached"),
            ),
        ),
        BlindCase(n = 35, text = "ajá, un café",
            mentions = listOf(
                Mention("un café", expectedId = "gen059", gramsRef = 237.0, gramsBasis = Basis.MEASURE, kcalRef = 2.37, question = false,
                    kcalSource = "FDC 171890 1 kcal/100 g; 1 cup = 237 g (USDA household weight of brewed coffee)"),
            ),
        ),
        BlindCase(n = 36, text = "brocolí al vapor",
            mentions = listOf(
                Mention("brocolí al vapor", expectedId = "gen022", gramsRef = 85.0, gramsBasis = Basis.MEASURE, kcalRef = 29.75, question = false,
                    kcalSource = "FDC 169967 35 kcal/100 g; FDA reference amount for vegetables (21 CFR 101.12) = 85 g (USDA 1 cup chopped = 156 g)"),
            ),
        ),
        BlindCase(n = 37, text = "tres tomates",
            mentions = listOf(
                Mention("3 tomates", expectedId = "gen026", gramsRef = 369.0, gramsBasis = Basis.MEASURE, kcalRef = 66.42, question = false,
                    kcalSource = "FDC 170457 18 kcal/100 g; 3 medium tomatoes of 123 g (USDA SR Legacy household weight)"),
            ),
        ),
        BlindCase(n = 38, text = "huevos x2",
            mentions = listOf(
                Mention("2 huevos", expectedId = "gen007", gramsRef = 100.0, gramsBasis = Basis.MEASURE, kcalRef = 155.0, question = false,
                    kcalSource = "FDC 173424 155 kcal/100 g; 2 large eggs of 50 g (USDA SR Legacy)"),
            ),
        ),
        BlindCase(n = 39, text = "porotos granados",
            mentions = listOf(
                Mention("porotos granados", expectedId = "cl007", gramsRef = 350.0, gramsBasis = Basis.TYPICAL, kcalRef = 347.2, question = false,
                    kcalSource = "plate of 350 g: 150 g cranberry beans cooked (FDC 173736 136 kcal/100 g) + 80 g corn kernels cooked (FDC 169999 96 kcal/100 g) + 60 g squash cooked (FDC 170490 37 kcal/100 g) + 5 g olive oil sofrito (FDC 171413 884 kcal/100 g); cross-check 350 g / 340 kcal (nutrola.app)"),
            ),
        ),
        BlindCase(n = 40, text = "porotos con riendas",
            mentions = listOf(
                Mention("porotos con riendas", expectedId = "cl029", gramsRef = 350.0, gramsBasis = Basis.TYPICAL, kcalRef = 355.6, question = false,
                    kcalSource = "plate of 400 g: 150 g white beans cooked (FDC 175203 139 kcal/100 g) + 80 g pasta cooked (FDC 169737 158 kcal/100 g) + 50 g squash cooked (FDC 170490 37 kcal/100 g) + 20 g chard (FDC 169991 19 kcal/100 g) + 5 g olive oil sofrito (FDC 171413 884 kcal/100 g) + 100 g broth (5 kcal/100 g) = 101.6 kcal/100 g; plate = 350 g (1.5 cups)"),
            ),
        ),
        BlindCase(n = 41, text = "lentejas con arroz",
            mentions = listOf(
                Mention("lentejas", expectedId = "gen012", alsoAcceptable = setOf("gen012h"), gramsRef = 198.0, gramsBasis = Basis.MEASURE, kcalRef = 229.68, question = false,
                    kcalSource = "FDC 172421 116 kcal/100 g; 1 cup = 198 g (USDA household weight of cooked lentils)"),
                Mention("arroz", expectedId = "gen005", gramsRef = 200.0, gramsBasis = Basis.TYPICAL, kcalRef = 260.0, question = false,
                    kcalSource = "FDC 168878 130 kcal/100 g; rice on a lunch plate = 200 g (1.27 USDA cups of 158 g)"),
            ),
        ),
        BlindCase(n = 42, text = "una taza de té sin azúcar",
            mentions = listOf(
                Mention("una taza de té sin azúcar", expectedId = "gen144", alsoAcceptable = setOf("gen060"), gramsRef = 237.0, gramsBasis = Basis.MEASURE, kcalRef = 2.37, question = false,
                    kcalSource = "FDC 173227 1 kcal/100 g; 1 cup = 237 g (USDA household weight of brewed tea)"),
            ),
        ),
        BlindCase(n = 43, text = "café sin azúcar",
            mentions = listOf(
                Mention("café sin azúcar", expectedId = "gen059", gramsRef = 237.0, gramsBasis = Basis.MEASURE, kcalRef = 2.37, question = false,
                    kcalSource = "FDC 171890 1 kcal/100 g; 1 cup = 237 g (USDA household weight of brewed coffee)"),
            ),
        ),
        BlindCase(n = 44, text = "coca cola zero",
            mentions = listOf(
                Mention("coca cola zero", expectedId = "off_coca_cola_zero", gramsRef = 355.0, gramsBasis = Basis.MEASURE, kcalRef = 0.0, question = false,
                    kcalSource = "FDC 171876 0 kcal/100 g; 1 can (12 fl oz) = 355 g (USDA household weight of diet cola)"),
            ),
        ),
        BlindCase(n = 45, text = "2 litros de agua",
            mentions = listOf(
                Mention("2 litros de agua", expectedId = "gen143", gramsRef = 2000.0, gramsBasis = Basis.MASS, kcalRef = 0.0, question = false, exactKcal = true,
                    kcalSource = "FDC 173647 0 kcal/100 g; 2 L of water = 2000 g"),
            ),
        ),
        BlindCase(n = 46, text = "me comí dos marraquetas con mantequilla",
            mentions = listOf(
                Mention("dos marraquetas", expectedId = "cl010", gramsRef = 200.0, gramsBasis = Basis.MEASURE, kcalRef = 544.0, question = false,
                    kcalSource = "2 x 100 g x 272 kcal/100 g (INTA 2018 marraqueta, secondary copy; the same table lists 1/2 unit = 50 g)"),
                Mention("mantequilla", expectedId = "gen049", gramsRef = 10.0, gramsBasis = Basis.MEASURE, kcalRef = 71.7, question = false,
                    kcalSource = "FDC 173410 717 kcal/100 g; two pats of 5 g (USDA household weight), one per bread"),
            ),
        ),
        BlindCase(n = 47, text = "anoche cené una cazuela de vacuno",
            mentions = listOf(
                Mention("una cazuela de vacuno", expectedId = "cl030", gramsRef = 450.0, gramsBasis = Basis.TYPICAL, kcalRef = 409.2, question = false,
                    kcalSource = "bowl of 450 g: 80 g lean beef braised (FDC 169444 216 kcal/100 g) + 100 g potato boiled (FDC 170438 87 kcal/100 g) + 80 g corn cooked (FDC 169999 96 kcal/100 g) + 80 g squash cooked (FDC 170490 37 kcal/100 g) + 30 g rice cooked (FDC 168878 130 kcal/100 g) + 80 g broth (5 kcal/100 g); cross-check 450 g / 380 kcal (nutrola.app)"),
            ),
        ),
        BlindCase(n = 48, text = "comí arroz con pollo y ensalada chilena, de postre un arroz con leche",
            mentions = listOf(
                Mention("arroz", expectedId = "gen005", gramsRef = 200.0, gramsBasis = Basis.TYPICAL, kcalRef = 260.0, question = false,
                    kcalSource = "FDC 168878 130 kcal/100 g; rice on a lunch plate = 200 g (1.27 USDA cups of 158 g)"),
                Mention("pollo", expectedId = "gen004", alsoAcceptable = setOf("gen003c", "gen003h", "gen003p", "gen003v"), gramsRef = 130.0, gramsBasis = Basis.TYPICAL, kcalRef = 215.8, question = true,
                    kcalSource = "FDC 331960 166 kcal/100 g; one cooked presa = 130 g (between USDA half breast 86 g and whole breast 172 g); no cut named, no mass given"),
                Mention("ensalada chilena", expectedId = "cl028", gramsRef = 100.0, gramsBasis = Basis.TYPICAL, kcalRef = 48.22, question = false,
                    kcalSource = "side salad of 100 g: 65 g tomato (FDC 170457 18 kcal/100 g) + 25 g onion (FDC 170000 40 kcal/100 g) + 3 g olive oil (FDC 171413 884 kcal/100 g); tomato and onion salad"),
                Mention("un arroz con leche", expectedId = "cl023", gramsRef = 200.0, gramsBasis = Basis.TYPICAL, kcalRef = 292.0, question = false,
                    kcalSource = "FDC 168063 146 kcal/100 g; one dessert cup of 200 g; Latino arroz con leche entry"),
            ),
        ),
        BlindCase(n = 49, text = "una empanada de pino al horno y una de queso",
            mentions = listOf(
                Mention("empanada de pino al horno", expectedId = "cl001", gramsRef = 180.0, gramsBasis = Basis.MEASURE, kcalRef = 430.0, question = false,
                    kcalSource = "published: baked pino empanada 150-180 g and 400-520 kcal (vidarecetas.com table with sources), 220 g / 420 kcal (nutrola.app); 430 kcal is the middle of 344 (nutrola scaled to 180 g) and 520"),
                Mention("empanada de queso", unresolvedQuery = "empanada de queso", gramsRef = 180.0, gramsBasis = Basis.MEASURE, kcalRef = 380.0, question = true,
                    kcalSource = "published: cheese empanada 180 g / 380 kcal (nutrola.app); no Chilean catalog row"),
            ),
        ),
        BlindCase(n = 50, text = "pan con palta y un té",
            mentions = listOf(
                Mention("pan con palta", expectedId = "cl025", gramsRef = 140.0, gramsBasis = Basis.MEASURE, kcalRef = 336.0, question = false,
                    kcalSource = "one bread unit 100 g x 272 kcal/100 g (INTA 2018 marraqueta, secondary copy) + 40 g avocado (FDC 171705 160 kcal/100 g)"),
                Mention("un té", expectedId = "gen060", alsoAcceptable = setOf("gen144"), gramsRef = 237.0, gramsBasis = Basis.MEASURE, kcalRef = 2.37, question = false,
                    kcalSource = "FDC 173227 1 kcal/100 g; 1 cup = 237 g (USDA household weight of brewed tea)"),
            ),
        ),
        BlindCase(n = 51, text = "un completo italiano sin mayo", control = true,
            mentions = listOf(
                Mention("un completo italiano sin mayo", unresolvedQuery = "completo italiano", gramsRef = 260.0, gramsBasis = Basis.MEASURE, kcalRef = 384.0, question = true,
                    kcalSource = "published: completo italiano 280 g / 520 kcal with mayonnaise (nutrola.app) minus about 20 g mayonnaise (FDC 171009 680 kcal/100 g); a modified recipe is not a catalog dish"),
            ),
        ),
        BlindCase(n = 52, text = "150 gr de pollo al horno con 100 g de arroz",
            mentions = listOf(
                Mention("150 g de pollo al horno", expectedId = "gen003h", alsoAcceptable = setOf("gen004", "gen003c", "gen003p", "gen003v"), gramsRef = 150.0, gramsBasis = Basis.MASS, kcalRef = 247.5, question = false,
                    kcalSource = "FDC 171477 165 kcal/100 g; roasted breast as eaten"),
                Mention("100 g de arroz", expectedId = "gen005", gramsRef = 100.0, gramsBasis = Basis.MASS, kcalRef = 130.0, question = true,
                    kcalSource = "FDC 168878 130 kcal/100 g; cooked rice; raw or cooked is not said (raw is 365 kcal/100 g, FDC 169756)"),
            ),
        ),
        BlindCase(n = 53, text = "avena con leche y plátano",
            mentions = listOf(
                Mention("avena", expectedId = "gen011", gramsRef = 40.0, gramsBasis = Basis.MEASURE, kcalRef = 151.6, question = false,
                    kcalSource = "FDC 173904 379 kcal/100 g; 1/2 cup of dry rolled oats = 40 g (USDA 1 cup = 81 g)"),
                Mention("leche", expectedId = "gen016", gramsRef = 244.0, gramsBasis = Basis.MEASURE, kcalRef = 148.84, question = false,
                    kcalSource = "FDC 171265 61 kcal/100 g; 1 cup = 244 g (USDA household weight of whole milk)"),
                Mention("plátano", expectedId = "gen002", gramsRef = 118.0, gramsBasis = Basis.MEASURE, kcalRef = 105.02, question = false,
                    kcalSource = "FDC 173944 89 kcal/100 g; 1 medium banana of 118 g (USDA SR Legacy household weight)"),
            ),
        ),
        BlindCase(n = 54, text = "un pan con queso y tomate",
            mentions = listOf(
                Mention("un pan con queso", expectedId = "cl026", gramsRef = 130.0, gramsBasis = Basis.MEASURE, kcalRef = 378.8, question = false,
                    kcalSource = "one bread unit 100 g x 272 kcal/100 g (INTA 2018 marraqueta, secondary copy) + 30 g gouda (FDC 171241 356 kcal/100 g)"),
                Mention("tomate", expectedId = "gen026", gramsRef = 40.0, gramsBasis = Basis.MEASURE, kcalRef = 7.2, question = false,
                    kcalSource = "FDC 170457 18 kcal/100 g; 2 slices of 20 g (USDA SR Legacy household weight)"),
            ),
        ),
        BlindCase(n = 55, text = "jugo natural de naranja grande",
            mentions = listOf(
                Mention("jugo natural de naranja grande", expectedId = "gen103", gramsRef = 310.0, gramsBasis = Basis.MEASURE, kcalRef = 139.5, question = false,
                    kcalSource = "FDC 169098 45 kcal/100 g; \"grande\" = 1.25 x one cup of 248 g (USDA household weight)"),
            ),
        ),
        BlindCase(n = 56, text = "dos vasos de leche descremada",
            mentions = listOf(
                Mention("dos vasos de leche descremada", expectedId = "gen046", gramsRef = 490.0, gramsBasis = Basis.MEASURE, kcalRef = 166.6, question = false,
                    kcalSource = "FDC 171269 34 kcal/100 g; 2 cups of 245 g (USDA household weight of skim milk)"),
            ),
        ),
        BlindCase(n = 57, text = "pollo frito",
            mentions = listOf(
                Mention("pollo frito", expectedId = "gen003f", gramsRef = 150.0, gramsBasis = Basis.TYPICAL, kcalRef = 333.0, question = true,
                    kcalSource = "FDC 171476 222 kcal/100 g; one fried portion of 150 g, meat and skin, flour coated; no cut named, no mass given"),
            ),
        ),
        BlindCase(n = 58, text = "100 g de atún en aceite",
            mentions = listOf(
                Mention("100 g de atún en aceite", expectedId = "gen029e", gramsRef = 100.0, gramsBasis = Basis.MASS, kcalRef = 198.0, question = false,
                    kcalSource = "FDC 173708 198 kcal/100 g; light tuna canned in oil, drained solids"),
            ),
        ),
        BlindCase(n = 59, text = "un yogur griego con granola",
            mentions = listOf(
                Mention("yogur griego", expectedId = "gen017", gramsRef = 125.0, gramsBasis = Basis.MEASURE, kcalRef = 121.25, question = false,
                    kcalSource = "FDC 171304 97 kcal/100 g; 1 single-serve pot of 125 g (Chile)"),
                Mention("granola", expectedId = "gen091", gramsRef = 30.0, gramsBasis = Basis.TYPICAL, kcalRef = 146.7, question = false,
                    kcalSource = "FDC 171646 489 kcal/100 g; a 30 g topping; homemade granola entry"),
            ),
        ),
        BlindCase(n = 60, text = "polloooo con arros",
            mentions = listOf(
                Mention("pollo", expectedId = "gen004", alsoAcceptable = setOf("gen003c", "gen003h", "gen003p", "gen003v"), gramsRef = 130.0, gramsBasis = Basis.TYPICAL, kcalRef = 215.8, question = true,
                    kcalSource = "FDC 331960 166 kcal/100 g; one cooked presa = 130 g (between USDA half breast 86 g and whole breast 172 g); no cut named, no mass given"),
                Mention("arroz", expectedId = "gen005", gramsRef = 200.0, gramsBasis = Basis.TYPICAL, kcalRef = 260.0, question = false,
                    kcalSource = "FDC 168878 130 kcal/100 g; rice on a lunch plate = 200 g (1.27 USDA cups of 158 g)"),
            ),
        ),
    )

    // --- Deterministic OFF fixture: the 12 rows of the probe ------------------------------------------------------

    /**
     * The rows of [BlindMealCorpusProbeTest]'s fixture, kept as a copy so that the probe stays a frozen measuring instrument:
     * built like the importer's OFF Chile rows (`source = "OFF Chile"`, `sourcePriority = 80`, aliases = normalized name +
     * normalized brand), macros per 100 g / 100 ml as sold (`PER_100G_AS_SOLD`).
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

    /**
     * Leaves `SemanticPortionRetriever` with no snapshot and returns how it was done: `install` only accepts a non-null snapshot and
     * `DatasetTestHarness.restore(null)` re-installs the real one, so the private `knowledge` / `lastRetrieve` fields are reset by reflection.
     */
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


    // --- The history of the second mode ----------------------------------------------------------------------------

    /**
     * Earlier confirmed identities. The foods named in the corpus map to the id the references expect (applying them must change
     * nothing); "completo" is a trap: a remembered mapping must never replace a declared variant ("sin mayo"). No state preference and
     * no remembered portion: those legitimately change the answer of the description they match.
     */
    private val calibratedProfile = NutritionCalibrationProfile(
        wizardVersion = 2,
        wizardCompleted = true,
        identityMappings = mapOf(
            "huevo" to "gen007",
            "arroz" to "gen005",
            "palta" to "gen014",
            "tomate" to "gen026",
            "agua" to "gen143",
            "marraqueta" to "cl010",
            "manzana" to "gen001",
            "uva" to "gen053",
            "completo" to "cl002",
        ),
    )

    // --- Pairing tags with mentions ---------------------------------------------------------------------------------

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

    private fun identityMatches(m: Mention, tag: ResolvedTag): Boolean {
        val id = tag.foodItem?.id
        if (id != null) return id == m.expectedId || id in m.alsoAcceptable
        val query = m.unresolvedQuery ?: return false
        val named = fold(listOf(tag.foodQuery, tag.tag, tag.loggedFood?.foodName.orEmpty()).joinToString(" "))
        return named.contains(fold(query))
    }

    /** Identity-anchored pairing first, then by position among the leftovers; excluded tags are not part of the meal. */
    private fun evaluate(case: BlindCase, tags: List<ResolvedTag>): CaseResult {
        val active = tags.filterNot { it.isExcluded }
        val taken = BooleanArray(active.size)
        val paired = arrayOfNulls<Int>(case.mentions.size)
        val anchored = BooleanArray(case.mentions.size)
        case.mentions.forEachIndexed { i, mention ->
            val j = active.indices.firstOrNull { !taken[it] && identityMatches(mention, active[it]) }
            if (j != null) {
                taken[j] = true
                paired[i] = j
                anchored[i] = true
            }
        }
        val free = active.indices.filter { !taken[it] }.toMutableList()
        for (i in case.mentions.indices) {
            if (paired[i] == null && free.isNotEmpty()) paired[i] = free.removeAt(0)
        }
        val results = case.mentions.mapIndexed { i, mention -> MentionResult(case, mention, paired[i]?.let { active[it] }, anchored[i]) }
        return CaseResult(case, results, free.map { active[it] }, paired.count { it == null })
    }

    // --- Running the four modes -------------------------------------------------------------------------------------

    private fun runMode(env: Env, calibration: NutritionCalibrationProfile?, label: String, snapshot: Boolean): ModeRun {
        val cases = corpus.map { case ->
            try {
                val tags = runBlocking { TagResolver(env.port, calibration).resolveAll(parseMealDescription(case.text)).first }
                evaluate(case, tags)
            } catch (t: Throwable) {
                throw AssertionError("the chain threw on #${case.n} [${case.text}] in mode '$label': $t", t)
            }
        }
        return ModeRun(label, snapshot, calibration != null, cases)
    }

    private fun collect(): Collected {
        val original = SemanticPortionRetriever.currentSnapshot()
        try {
            DatasetTestHarness.restore(null)
            val envA = buildEnv()
            val a1 = runMode(envA, null, "snapshot, empty history", snapshot = true)
            val a2 = runMode(envA, calibratedProfile, "snapshot, calibrated history", snapshot = true)
            val modeB = uninstallDatasetSnapshot()
            val envB = buildEnv()
            val b1 = runMode(envB, null, "no snapshot, empty history", snapshot = false)
            val b2 = runMode(envB, calibratedProfile, "no snapshot, calibrated history", snapshot = false)
            return Collected(listOf(a1, a2, b1, b2), modeB, envA.indexedFoods)
        } finally {
            // Same convention as the other dataset tests: the previous snapshot, or the real dataset when there was none.
            DatasetTestHarness.restore(original)
        }
    }

    private fun runs(): Collected {
        cached?.let { return it }
        val data = collect()
        cached = data
        publish(data)
        return data
    }

    // --- Thresholds ---------------------------------------------------------------------------------------------------

    /**
     * Percent per dimension: the worst mode's measured value rounded down to a multiple of 5; identity, coverage and question capped at the plan minimum.
     * Measured with WP-N8b on HEAD 3234cb23a (WP-N7 committed) and its follow-up (the custard at the 145 kcal of the USDA flan, the apple piece at the 182 g of
     * the USDA medium apple), identical in the four modes: identity 100 (83/83), coverage 100 (60/60), grams 96.4 (80/83), kcal 90.4 (75/83), question 97.6
     * (81/83). Before WP-N8b they were 97.6 (81/83), 100, 83.1 (69/83), 68.7 (57/83) and 95.2 (79/83), and the thresholds of grams and kcal were 75 and 60;
     * with the apple at 150 g kcal was 89.2 (74/83), because the 2 apples of #22 were 375 g and 195 kcal against 484 g and 252 kcal (at 182 g they are 455 g
     * and 237 kcal). They are now 95 and 90, above the plan minimum of 80: the failing mentions that remain (the cl029 density of #1 and #40, the helado of
     * #10, the tres leches of #27, the champiñones of #33, the arroz con leche of #48, the plural of #25 and #32 and the question of #11) are printed with
     * their references.
     */
    private val thresholds: Map<Dimension, Int> = mapOf(
        Dimension.IDENTITY to 90,
        Dimension.COVERAGE to 90,
        Dimension.GRAMS to 95,
        Dimension.KCAL to 90,
        Dimension.QUESTION to 90,
    )

    /** The dimensions whose threshold follows the measured floor above the plan minimum (WP-N8b). */
    private val ratcheted = setOf(Dimension.GRAMS, Dimension.KCAL)

    private fun suggestedThreshold(d: Dimension, data: Collected): Int {
        val worst = data.modes.minOf { it.rate(d).percent }
        val floor = (Math.floor(worst / 5.0) * 5.0).toInt()
        return if (d in ratcheted) floor else minOf(floor, d.planMinimum)
    }

    // --- Formatting ----------------------------------------------------------------------------------------------------

    private fun fmt(v: Double?): String = when {
        v == null -> "-"
        !v.isFinite() -> v.toString()
        Math.rint(v) == v -> String.format(Locale.ROOT, "%.0f", v)
        else -> String.format(Locale.ROOT, "%.1f", v)
    }

    private fun r1(v: Double?): Double? = v?.takeIf { it.isFinite() }?.let { Math.round(it * 10.0) / 10.0 }

    private fun pct(rate: Rate): String = String.format(Locale.ROOT, "%.1f%%", rate.percent)

    private fun tagOf(m: ModeRun): String = (if (m.snapshot) "S" else "N") + (if (m.profile) "H" else "E")

    private fun clip(s: String, max: Int): String = if (s.length <= max) s else s.take(max - 1) + "~"

    private fun expectedText(m: Mention): String =
        (m.expectedId ?: ("~" + m.unresolvedQuery)) + (if (m.alsoAcceptable.isEmpty()) "" else " or " + m.alsoAcceptable.sorted().joinToString("/"))

    private fun gotText(r: MentionResult): String = r.gotId ?: r.tag?.loggedFood?.foodName?.let { "(" + it + ")" } ?: "-"

    /** One line per failing mention of `d`, with its reference and the modes (SE, SH, NE, NH) in which it fails. */
    private fun failureLines(d: Dimension, data: Collected): List<String> {
        val failing = linkedMapOf<Pair<Int, Int>, MutableList<String>>()
        val sample = linkedMapOf<Pair<Int, Int>, MentionResult>()
        for (mode in data.modes) {
            for (case in mode.cases) {
                if (d == Dimension.COVERAGE) {
                    if (!case.coverage) failing.getOrPut(case.case.n to -1) { mutableListOf() }.add(tagOf(mode))
                    continue
                }
                case.results.forEachIndexed { i, r ->
                    val ok = when (d) {
                        Dimension.IDENTITY -> r.identity
                        Dimension.GRAMS -> r.grams
                        Dimension.KCAL -> r.kcal
                        else -> r.question
                    }
                    if (!ok) {
                        failing.getOrPut(case.case.n to i) { mutableListOf() }.add(tagOf(mode))
                        sample.putIfAbsent(case.case.n to i, r)
                    }
                }
            }
        }
        val first = data.modes.first()
        return failing.map { (key, modes) ->
            val tags = modes.joinToString(" ")
            if (d == Dimension.COVERAGE) {
                val case = first.cases.first { it.case.n == key.first }
                "#${case.case.n} [${case.case.text}] omissions=${case.omissions} additions=${case.additions.map { it.tag }} [$tags]"
            } else {
                val r = sample.getValue(key)
                val m = r.mention
                val (ref, got) = when (d) {
                    Dimension.IDENTITY -> "expected " + expectedText(m) to "got " + gotText(r)
                    Dimension.GRAMS -> "ref ${fmt(m.gramsRef)} g (${m.gramsBasis}, +-25 %)" to "got ${fmt(r.gotGrams)} g"
                    Dimension.KCAL -> "ref ${fmt(m.kcalRef)} kcal (+-${fmt(kcalTolerance(m))})" to "got ${fmt(r.gotKcal)} kcal"
                    else -> "ref question=${m.question}" to "got question=${r.gotQuestion}"
                }
                "#${r.case.n} ${m.label}: $ref; $got [$tags]" + if (d == Dimension.KCAL) "  <= ${clip(m.kcalSource, 110)}" else ""
            }
        }
    }

    // --- Report: stdout and blind-corpus.json ---------------------------------------------------------------------------

    private fun summaryLines(data: Collected): List<String> = buildList {
        add("%-33s %-3s %s".format(Locale.ROOT, "mode", "tag", Dimension.entries.joinToString("  ") { "%-17s".format(Locale.ROOT, it.label + " (>=" + thresholds.getValue(it) + ")") }))
        for (mode in data.modes) {
            add("%-33s %-3s %s".format(Locale.ROOT, mode.label, tagOf(mode), Dimension.entries.joinToString("  ") { d ->
                val r = mode.rate(d)
                "%-17s".format(Locale.ROOT, "${r.passed}/${r.total} ${pct(r)}")
            }))
        }
        for (mode in data.modes) {
            add("%-33s %-3s grams by basis %s | kcal by basis %s | energy density (informational) %s".format(Locale.ROOT, mode.label, tagOf(mode),
                Basis.entries.joinToString(" ") { b -> b.name + " " + mode.rateBy(Dimension.GRAMS, b).let { "${it.passed}/${it.total}" } },
                Basis.entries.joinToString(" ") { b -> b.name + " " + mode.rateBy(Dimension.KCAL, b).let { "${it.passed}/${it.total}" } },
                mode.densityRate().let { "${it.passed}/${it.total} ${pct(it)}" }))
        }
        add("suggested thresholds (worst mode, down to 5; identity, coverage and question capped at the plan minimum): " + Dimension.entries.joinToString(", ") { it.label + " " + suggestedThreshold(it, data) })
        add("plan minimums: " + Dimension.entries.joinToString(", ") { it.label + " " + it.planMinimum })
    }

    private fun referenceLines(): List<String> = buildList {
        for (case in corpus) {
            add("#${case.n}${if (case.control) " [control]" else ""} ${case.text}")
            for (m in case.mentions) {
                add("    ${clip(m.label, 32)} | ${expectedText(m)} | ${fmt(m.gramsRef)} g ${m.gramsBasis} | ${fmt(m.kcalRef)} kcal${if (m.exactKcal) " (+-0.5)" else ""} | question=${m.question} | ${m.kcalSource}")
            }
        }
    }

    /** The references, once: they do not depend on the mode. */
    private fun referenceJson(case: BlindCase, m: Mention): JsonObject = buildJsonObject {
        put("case", case.n)
        put("text", case.text)
        put("control", case.control)
        put("label", m.label)
        put("expectedId", m.expectedId)
        put("unresolvedQuery", m.unresolvedQuery)
        put("alsoAcceptable", JsonArray(m.alsoAcceptable.sorted().map { JsonPrimitive(it) }))
        put("gramsRef", m.gramsRef)
        put("gramsBasis", m.gramsBasis.name)
        put("kcalRef", r1(m.kcalRef))
        put("exactKcal", m.exactKcal)
        put("questionRef", m.question)
        put("kcalSource", m.kcalSource)
    }

    private fun mentionJson(r: MentionResult): JsonObject = buildJsonObject {
        put("case", r.case.n)
        put("label", r.mention.label)
        put("gotId", r.gotId)
        put("gotName", r.tag?.foodItem?.name ?: r.tag?.loggedFood?.foodName)
        put("gotGrams", r1(r.gotGrams))
        put("gotKcal", r1(r.gotKcal))
        put("gotQuestion", r.gotQuestion)
        put("identity", r.identity)
        put("grams", r.grams)
        put("kcal", r.kcal)
        put("question", r.question)
        put("density", r.density)
    }

    private fun modeJson(mode: ModeRun): JsonObject = buildJsonObject {
        put("mode", mode.label)
        put("tag", tagOf(mode))
        put("snapshot", mode.snapshot)
        put("calibratedHistory", mode.profile)
        put("rates", buildJsonObject {
            for (d in Dimension.entries) {
                val r = mode.rate(d)
                put(d.label, buildJsonObject {
                    put("passed", r.passed)
                    put("total", r.total)
                    put("percent", r1(r.percent))
                })
            }
        })
        put("coverage", JsonArray(mode.cases.map { c ->
            buildJsonObject {
                put("case", c.case.n)
                put("ok", c.coverage)
                put("omissions", c.omissions)
                put("additions", JsonArray(c.additions.map { JsonPrimitive(it.tag) }))
            }
        }))
        put("mentions", JsonArray(mode.results.map { mentionJson(it) }))
    }

    private fun writeJson(data: Collected): File {
        val report = buildJsonObject {
            put("corpus", "blind-meal-corpus-v1")
            put("workPackage", "WP-N12")
            put("chain", "parseMealDescription(text) -> TagResolver(port, calibration).resolveAll(parsed)")
            put("cases", corpus.size)
            put("mentions", corpus.sumOf { it.mentions.size })
            put("author", AUTHOR)
            put("tolerances", buildJsonObject {
                put("grams", GRAMS_TOLERANCE)
                put("kcal", KCAL_TOLERANCE)
                put("kcalExact", KCAL_EXACT_TOLERANCE)
                put("kcalFloor", KCAL_FLOOR)
            })
            put("thresholds", buildJsonObject { Dimension.entries.forEach { put(it.label, thresholds.getValue(it)) } })
            put("planMinimums", buildJsonObject { Dimension.entries.forEach { put(it.label, it.planMinimum) } })
            put("suggestedThresholds", buildJsonObject { Dimension.entries.forEach { put(it.label, suggestedThreshold(it, data)) } })
            put("passBMode", data.modeB)
            put("indexedFoods", data.indexedFoods)
            put("offFixtureIds", JsonArray(offFixture.map { JsonPrimitive(it.foodId) }))
            put("references", JsonArray(corpus.flatMap { c -> c.mentions.map { referenceJson(c, it) } }))
            put("failures", buildJsonObject {
                Dimension.entries.forEach { d -> put(d.label, JsonArray(failureLines(d, data).map { JsonPrimitive(it) })) }
            })
            put("modes", JsonArray(data.modes.map { modeJson(it) }))
        }
        return File(REPORT_PATH).apply {
            parentFile?.mkdirs()
            writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report))
        }
    }

    private fun publish(data: Collected) {
        println()
        println("=== BlindMealCorpus (WP-N12): ${corpus.size} descriptions, ${corpus.sumOf { it.mentions.size }} mentions, 4 modes; indexed foods=${data.indexedFoods}; pass B: ${data.modeB} ===")
        summaryLines(data).forEach { println(it) }
        for (d in Dimension.entries) {
            val lines = failureLines(d, data)
            println()
            println("--- ${d.label}: ${lines.size} failing (S = snapshot, N = no snapshot, E = empty history, H = calibrated history) ---")
            lines.forEach { println("  $it") }
        }
        println()
        println("--- references (case | mention | expected | grams reference | kcal reference | question | kcalSource) ---")
        referenceLines().forEach { println(it) }
        println("report: " + writeJson(data).absolutePath)
    }

    // --- Tests ---------------------------------------------------------------------------------------------------------

    @Test
    fun `corpus is well formed`() {
        assertEquals("audit descriptions", 60, corpus.size)
        assertEquals("numbered 1..60 in order", (1..60).toList(), corpus.map { it.n })
        assertEquals("distinct descriptions", 60, corpus.map { it.text }.toSet().size)
        assertEquals("controls named by the plan", listOf(13, 31, 51), corpus.filter { it.control }.map { it.n })
        assertEquals("OFF fixture rows", 12, offFixture.size)
        assertEquals("OFF fixture ids are unique", offFixture.size, offFixture.map { it.foodId }.toSet().size)
        val env = buildEnv()
        runBlocking {
            for (case in corpus) {
                assertTrue("#${case.n} has no mention", case.mentions.isNotEmpty())
                assertTrue("#${case.n} has no author", case.author.isNotBlank())
                for (m in case.mentions) {
                    val where = "#${case.n} ${m.label}"
                    assertTrue("$where: exactly one of expectedId / unresolvedQuery", (m.expectedId == null) != (m.unresolvedQuery == null))
                    for (id in listOfNotNull(m.expectedId) + m.alsoAcceptable) {
                        assertTrue("$where: unknown catalog id $id", env.port.getFoodById(id) != null)
                    }
                    assertTrue("$where: reference mass", m.gramsRef.isFinite() && m.gramsRef > 0.0)
                    assertTrue("$where: reference kcal", m.kcalRef.isFinite() && m.kcalRef >= 0.0)
                    assertTrue("$where: kcalSource", m.kcalSource.isNotBlank())
                }
            }
        }
    }

    /**
     * The descriptions are authored independently of the runtime dataset (contract "Evaluation"): none equals a dataset instruction and
     * no description of [MIN_TOKENS_FOR_CONTAINMENT] words or more occurs inside one. Shorter ones are ordinary food phrases ("un vaso
     * de agua") that any 19 k-document corpus contains; they are listed, not asserted.
     */
    @Test
    fun `no description is copied from the semantic dataset`() {
        val original = SemanticPortionRetriever.currentSnapshot()
        try {
            DatasetTestHarness.restore(null)
            val snapshot = checkNotNull(SemanticPortionRetriever.currentSnapshot()) { "the real snapshot is not installed" }
            val instructions = snapshot.documents.map { fold(it.instruction) }
            val exact = instructions.toSet()
            val padded = instructions.map { " $it " }
            val copied = corpus.filter { fold(it.text) in exact }
            fun occursInside(case: BlindCase): Boolean = padded.any { it.contains(" " + fold(case.text) + " ") }
            val (long, short) = corpus.partition { fold(it.text).split(" ").size >= MIN_TOKENS_FOR_CONTAINMENT }
            val embedded = long.filter { occursInside(it) }
            println("descriptions found inside a dataset instruction, shorter than $MIN_TOKENS_FOR_CONTAINMENT words (not asserted): " + short.filter { occursInside(it) }.map { it.n })
            assertTrue("equal to a dataset instruction: ${copied.map { it.n }}", copied.isEmpty())
            assertTrue("inside a dataset instruction: ${embedded.map { it.n }}", embedded.isEmpty())
        } finally {
            DatasetTestHarness.restore(original)
        }
    }

    @Test
    fun `pass rates meet the thresholds in every mode`() {
        val data = runs()
        val misses = mutableListOf<String>()
        val missed = linkedSetOf<Dimension>()
        for (mode in data.modes) {
            for (d in Dimension.entries) {
                val rate = mode.rate(d)
                if (rate.percent + EPS < thresholds.getValue(d)) {
                    misses += "${mode.label}: ${d.label} ${rate.passed}/${rate.total} = ${pct(rate)} < ${thresholds.getValue(d)} %"
                    missed += d
                }
            }
        }
        val detail = missed.joinToString("\n") { d -> "--- ${d.label} ---\n" + failureLines(d, data).joinToString("\n") { "  $it" } }
        assertTrue("below the thresholds:\n" + misses.joinToString("\n") + "\n" + detail, misses.isEmpty())
    }

    @Test
    fun `control cases keep their identity and their question in every mode`() {
        val data = runs()
        val broken = data.modes.flatMap { mode ->
            mode.cases.filter { it.case.control }.flatMap { c ->
                c.results.filterNot { it.identity && it.question }.map {
                    "${tagOf(mode)} #${c.case.n} ${it.mention.label}: identity=${it.identity} (expected ${expectedText(it.mention)}, got ${gotText(it)}), " +
                        "question=${it.question} (reference ${it.mention.question}, got ${it.gotQuestion})"
                }
            }
        }
        assertTrue("control cases moved:\n" + broken.joinToString("\n"), broken.isEmpty())
    }

    private fun signature(r: MentionResult): List<Any?> = listOf(r.gotId, r1(r.gotGrams), r1(r.gotKcal), r.gotQuestion)

    @Test
    fun `an earlier history that agrees with the references changes no result`() {
        val data = runs()
        val moved = mutableListOf<String>()
        for ((empty, history) in listOf(data.modes[0] to data.modes[1], data.modes[2] to data.modes[3])) {
            for ((a, b) in empty.results.zip(history.results)) {
                if (signature(a) != signature(b)) moved += "${tagOf(empty)} -> ${tagOf(history)} #${a.case.n} ${a.mention.label}: ${signature(a)} -> ${signature(b)}"
            }
            for ((a, b) in empty.cases.zip(history.cases)) {
                if (a.coverage != b.coverage) moved += "${tagOf(empty)} -> ${tagOf(history)} #${a.case.n}: coverage ${a.coverage} -> ${b.coverage}"
            }
        }
        assertTrue("the history moved results:\n" + moved.joinToString("\n"), moved.isEmpty())
    }

    /** The history modes are only meaningful if the profile is consulted: a mapping that disagrees with the default must win. */
    @Test
    fun `the calibrated history channel is live`() = runBlocking {
        val env = buildEnv()
        val plain = TagResolver(env.port, null).resolveAll(parseMealDescription("arroz")).first.single()
        assertEquals("gen005", plain.foodItem?.id)
        val remembered = TagResolver(env.port, NutritionCalibrationProfile(identityMappings = mapOf("arroz" to "gen006"))).resolveAll(parseMealDescription("arroz")).first.single()
        assertEquals("gen006", remembered.foodItem?.id)
    }

    @Test
    fun `report is written with one entry per mode and per description`() {
        val data = runs()
        val file = File(REPORT_PATH)
        assertTrue("blind-corpus.json written at " + file.absolutePath, file.isFile && file.length() > 0L)
        val written = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals("four modes", 4, written.getValue("modes").jsonArray.size)
        assertEquals("descriptions", corpus.size, written.getValue("modes").jsonArray.first().jsonObject.getValue("coverage").jsonArray.size)
        assertEquals("mentions per mode", corpus.sumOf { it.mentions.size }, data.modes.first().results.size)
    }
}
