package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.AnalysisSource
import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-U11 (C9) · Reabrir una comida registrada para editarla. Contrato v2, «Saving and learning»: aceptar una estimación
 * no confirma identidad; una fila reabierta conserva su indicador de estimación y su rango; editar no enseña hábitos.
 */
class LoggedFoodEditingTest {

    /** Un alimento de catálogo tal como lo guarda el logger: nutrientes exactos y procedencia verificada. */
    private val rice = LoggedFood(
        id = "food-rice", foodName = "Arroz blanco cocido", amount = 150.0,
        calories = 195.0, protein = 4.1, carbs = 42.0, fats = 0.4,
        analysisSource = AnalysisSource.DATABASE, isUncertain = false,
    )

    /** Una estimación: el centro, el rango y la nota de referencia que se guardaron. */
    private val stew = LoggedFood(
        id = "food-stew", foodName = "Cazuela casera (estimado)", amount = 300.0,
        calories = 420.0, protein = 22.0, carbs = 38.0, fats = 18.0,
        caloriesMin = 300.0, caloriesMax = 560.0, proteinMin = 16.0, proteinMax = 28.0,
        carbsMin = 28.0, carbsMax = 48.0, fatsMin = 12.0, fatsMax = 24.0,
        analysisSource = AnalysisSource.LOCAL_HEURISTIC, isUncertain = true,
        nutritionReferenceNote = "Asumí una cazuela casera.",
        evidenceJson = """{"source":"HEURISTIC","sourceQuality":"estimated"}""",
    )

    private val fries = LoggedFood(
        id = "food-fries", foodName = "Papas fritas", amount = 120.0,
        calories = 380.0, protein = 4.0, carbs = 48.0, fats = 19.0,
        cookingMethod = CookingMethod.FRITO, analysisSource = AnalysisSource.DATABASE,
    )

    private fun interpretationOf(tag: ResolvedTag): FoodInterpretationV2 =
        tag.interpretationV2 ?: throw AssertionError("The reopened tag has no interpretation")

    private fun loggedOf(tag: ResolvedTag): LoggedFood =
        tag.loggedFood ?: throw AssertionError("The reopened tag has no logged food")

    /** Lo que hace `updateTagGrams` del logger para una tarjeta sin ficha (los gramos son una masa explícita). */
    private fun withGrams(tag: ResolvedTag, grams: Double): ResolvedTag {
        val rescaled = rescaleEstimatedFood(tag.baseLoggedFood ?: loggedOf(tag), grams)
        return tag.copy(
            amountGrams = grams, baseAmountGrams = grams, portionMinGrams = grams, portionMaxGrams = grams,
            amountIntent = AmountIntent.EXPLICIT_MASS, loggedFood = rescaled, baseLoggedFood = rescaled,
            hasManualEdits = true,
        )
    }

    // ─── La tarjeta reabierta ───────────────────────────────────────────────

    @Test
    fun `a reopened verified food keeps its amount and nutrients, asks nothing and is not an estimate`() {
        val tag = loggedFoodToEditableTag(rice)

        assertEquals(TagOrigin.EDIT, tag.origin)
        assertEquals(AmountIntent.EXPLICIT_MASS, tag.amountIntent)
        assertEquals(150.0, tag.amountGrams ?: Double.NaN, 0.0)
        assertEquals(150.0, tag.baseAmountGrams ?: Double.NaN, 0.0)
        assertTrue(tag.isResolved)
        assertEquals(FoodResolutionStatus.CONFIRMED_ESTIMATE, tag.resolutionStatus)
        assertEquals(NutritionSourceKind.CURATED_LOCAL, tag.nutritionSource)

        val interpretation = interpretationOf(tag)
        assertTrue("a saved food raises no question", interpretation.pendingQuestions.isEmpty())
        assertFalse(interpretation.isUncertain)
        assertFalse(tag.isUncertain)
        assertFalse(loggedOf(tag).isUncertain)
        assertTrue(interpretation.canFinalize())
        assertFalse(tag.hasMaterialQuestion())
        assertEquals(195.0, interpretation.calories, 0.0)
        assertEquals(195.0, loggedOf(tag).calories, 0.0)
        assertEquals("Arroz blanco cocido", loggedOf(tag).foodName)
    }

    @Test
    fun `a reopened estimate keeps its range and its estimate indicator`() {
        val tag = loggedFoodToEditableTag(stew)

        val interpretation = interpretationOf(tag)
        assertTrue(tag.isUncertain)
        assertTrue(interpretation.isUncertain)
        assertTrue(loggedOf(tag).isUncertain)
        assertEquals(300.0, interpretation.caloriesMin, 1e-6)
        assertEquals(560.0, interpretation.caloriesMax, 1e-6)
        assertEquals(300.0, loggedOf(tag).caloriesMin ?: Double.NaN, 1e-6)
        assertEquals(560.0, loggedOf(tag).caloriesMax ?: Double.NaN, 1e-6)
        assertEquals(16.0, loggedOf(tag).proteinMin ?: Double.NaN, 1e-6)
        assertEquals(24.0, loggedOf(tag).fatsMax ?: Double.NaN, 1e-6)
        assertEquals("the central value is not touched", 420.0, loggedOf(tag).calories, 0.0)
        assertTrue("an estimate is still finalizable: acceptance is the user's decision", interpretation.canFinalize())
        assertTrue(interpretation.pendingQuestions.isEmpty())
        assertEquals(NutritionSourceKind.HEURISTIC_ESTIMATE, tag.nutritionSource)
    }

    @Test
    fun `reopening a food never confirms a dimension and never teaches a habit`() {
        listOf(rice, stew, fries).forEach { food ->
            val tag = loggedFoodToEditableTag(food)
            assertTrue(food.foodName, tag.confirmedDimensions.isEmpty())
            assertTrue(food.foodName, tag.explicitDecision)
            assertNull(food.foodName, tag.confirmedLearning())
        }
    }

    @Test
    fun `a reopened food raises no question where the same food, undecided, would ask`() {
        val tag = loggedFoodToEditableTag(fries)

        assertTrue(interpretationOf(tag).pendingQuestions.isEmpty())
        assertFalse(tag.needsOilClarification)
        assertFalse(tag.needsCookingClarification)

        // Without the accepted estimate a fried food asks about the oil: that question is not asked again on a reopened meal.
        val undecided = NutritionInterpretationBridge.refresh(tag.copy(explicitDecision = false))
        assertTrue(undecided.interpretationV2?.pendingQuestions?.any { it is ClarificationRequest.Oil } == true)
    }

    @Test
    fun `odd saved foods reopen without throwing and one without a usable amount stays unsaved until it is fixed`() {
        val usable = loggedFoodToEditableTag(rice.copy(foodName = ""))
        assertTrue("a blank name is still a savable food", interpretationOf(usable).canFinalize())

        listOf(0.0, -5.0, Double.NaN).forEach { amount ->
            val tag = loggedFoodToEditableTag(rice.copy(amount = amount))
            assertFalse("amount $amount", interpretationOf(tag).canFinalize())
            assertNull(tag.confirmedLearning())
        }
    }

    @Test
    fun `reopened foods get distinct card ids even when their saved ids are blank`() {
        val tags = listOf(rice.copy(id = ""), rice.copy(id = "")).map(::loggedFoodToEditableTag)

        assertEquals(2, tags.map { it.id }.toSet().size)
    }

    // ─── Procedencia y nota ─────────────────────────────────────────────────

    @Test
    fun `a food picked by search keeps its verified source even though it has no analysis source`() {
        val picked = rice.copy(
            analysisSource = null,
            evidenceJson = """{"source":"OPEN_FOOD_FACTS","sourceQuality":"catalog"}""",
        )

        val tag = loggedFoodToEditableTag(picked)

        assertEquals(NutritionSourceKind.VERIFIED_GLOBAL, tag.nutritionSource)
        assertFalse(tag.isUncertain)
        assertFalse(interpretationOf(tag).isUncertain)
    }

    @Test
    fun `the saved evidence decides the source kind and unknown provenance is an estimate`() {
        fun kind(source: String?, analysis: AnalysisSource? = null, json: String? = source?.let { """{"source":"$it"}""" }) =
            editableNutritionSource(rice.copy(analysisSource = analysis, evidenceJson = json))

        assertEquals(NutritionSourceKind.CURATED_LOCAL, kind("CURATED_LOCAL"))
        assertEquals(NutritionSourceKind.VERIFIED_GLOBAL, kind("USDA_FOUNDATION"))
        assertEquals(NutritionSourceKind.VERIFIED_GLOBAL, kind("USDA_BRANDED"))
        assertEquals(NutritionSourceKind.VERIFIED_GLOBAL, kind("OPEN_FOOD_FACTS"))
        assertEquals(NutritionSourceKind.USER_PROVIDED, kind("MANUAL"))
        assertEquals(NutritionSourceKind.USER_PROVIDED, kind("CUSTOM_CONFIRMED"))
        assertEquals(NutritionSourceKind.DATASET_ESTIMATE, kind("DATASET_SEMANTIC"))
        assertEquals(NutritionSourceKind.HEURISTIC_ESTIMATE, kind("HEURISTIC", AnalysisSource.DATABASE))
        // Without readable evidence only DATABASE counts as verified (legacy rows).
        assertEquals(NutritionSourceKind.CURATED_LOCAL, kind(null, AnalysisSource.DATABASE))
        assertEquals(NutritionSourceKind.HEURISTIC_ESTIMATE, kind(null, AnalysisSource.RULES))
        assertEquals(NutritionSourceKind.HEURISTIC_ESTIMATE, kind(null, null))
        assertEquals(NutritionSourceKind.HEURISTIC_ESTIMATE, kind(null, AnalysisSource.LOCAL_HEURISTIC))
        assertEquals(NutritionSourceKind.CURATED_LOCAL, kind(null, AnalysisSource.DATABASE, json = "{not json"))
        assertEquals(NutritionSourceKind.CURATED_LOCAL, kind(null, AnalysisSource.DATABASE, json = """{"source":5}"""))
    }

    @Test
    fun `the saved reference note comes back once, with and without the unmatched suffix`() {
        val suffix = " Rango orientativo; composición sin confirmar."
        listOf("Asumí una cazuela casera.", "Tortilla sin ficha; composición no confirmada.$suffix").forEach { note ->
            val tag = loggedFoodToEditableTag(stew.copy(nutritionReferenceNote = note))

            assertEquals(note, loggedOf(tag).nutritionReferenceNote)
            assertEquals(note, interpretationOf(tag).nutritionReferenceNote)
            // Reinterpreting it again (what every edit does) keeps it as well.
            val again = refreshAfterEdit(tag, "portion")
            assertEquals(note, loggedOf(again).nutritionReferenceNote)
        }
    }

    @Test
    fun `a food without a reference note gets no estimate evidence and no invented note`() {
        val tag = loggedFoodToEditableTag(rice)

        assertNull(tag.nutritionEstimate)
        assertNull(loggedOf(tag).nutritionReferenceNote)
        assertNull(interpretationOf(tag).nutritionReferenceNote)
    }

    // ─── Editar una tarjeta reabierta ───────────────────────────────────────

    @Test
    fun `editing the amount of a reopened food leaves it decided with no question, no dimension and no learning`() {
        listOf(rice, stew, fries).forEach { food ->
            val tag = loggedFoodToEditableTag(food)

            val edited = refreshAfterEdit(withGrams(tag, 200.0), "portion")

            val interpretation = interpretationOf(edited)
            assertTrue(food.foodName, interpretation.pendingQuestions.isEmpty())
            assertTrue(food.foodName, edited.confirmedDimensions.isEmpty())
            assertTrue(food.foodName, edited.explicitDecision)
            assertNull(food.foodName, edited.confirmedLearning())
            assertTrue(food.foodName, interpretation.canFinalize())
            assertEquals(food.foodName, 200.0, interpretation.observedGrams ?: Double.NaN, 1e-9)
            assertEquals(TagOrigin.EDIT, edited.origin)
        }
    }

    @Test
    fun `every edited dimension of a reopened food is ignored as a confirmation`() {
        val tag = loggedFoodToEditableTag(fries)

        listOf("portion", "oil", "state", "identity").forEach { dimension ->
            val edited = refreshAfterEdit(tag, dimension)
            assertTrue(dimension, edited.confirmedDimensions.isEmpty())
            assertTrue(dimension, edited.explicitDecision)
            assertTrue(dimension, interpretationOf(edited).pendingQuestions.isEmpty())
        }
    }

    @Test
    fun `editing a verified food does not turn it into an estimate`() {
        val edited = refreshAfterEdit(withGrams(loggedFoodToEditableTag(rice), 225.0), "portion")

        assertFalse(edited.isUncertain)
        assertFalse(interpretationOf(edited).isUncertain)
        assertEquals(195.0 * 1.5, loggedOf(edited).calories, 1e-6)
    }

    @Test
    fun `editing the amount of a reopened estimate keeps a range instead of an exact value`() {
        val edited = refreshAfterEdit(withGrams(loggedFoodToEditableTag(stew), 450.0), "portion")

        val interpretation = interpretationOf(edited)
        assertTrue(interpretation.isUncertain)
        // 1.5x the portion: the central value scales, and the saved density range scales with it.
        assertEquals(630.0, interpretation.calories, 1e-6)
        assertEquals(450.0, interpretation.caloriesMin, 1e-6)
        assertEquals(840.0, interpretation.caloriesMax, 1e-6)
        assertEquals(840.0, loggedOf(edited).caloriesMax ?: Double.NaN, 1e-6)
        assertTrue(loggedOf(edited).isUncertain)
        assertEquals("Asumí una cazuela casera.", loggedOf(edited).nutritionReferenceNote)
    }

    @Test
    fun `a manual macro edit of a reopened food stays decided and is a manual value`() {
        val tag = loggedFoodToEditableTag(rice)
        val typed = tag.copy(loggedFood = loggedOf(tag).copy(calories = 230.0), hasManualEdits = true)

        val edited = refreshAfterEdit(typed)

        assertEquals(230.0, loggedOf(edited).calories, 0.0)
        assertTrue(edited.nutrientsManuallyEdited)
        assertEquals(NutritionSourceKind.USER_PROVIDED, edited.nutritionSource)
        assertTrue(edited.confirmedDimensions.isEmpty())
        assertTrue(interpretationOf(edited).pendingQuestions.isEmpty())
        assertNull(edited.confirmedLearning())
    }

    @Test
    fun `a tag that came from the text or from the search is refreshed as before`() {
        listOf(TagOrigin.DESCRIPTION, TagOrigin.SEARCH, TagOrigin.LAST_RESORT).forEach { origin ->
            val tag = ResolvedTag(
                tag = "arroz", amountGrams = 150.0, amountIntent = AmountIntent.EXPLICIT_MASS,
                loggedFood = rice, explicitDecision = true, origin = origin,
            )

            val edited = refreshAfterEdit(tag, "portion")

            assertEquals(origin.name, setOf("portion"), edited.confirmedDimensions)
            assertFalse(origin.name, edited.explicitDecision)
            assertEquals(origin, edited.origin)
        }
    }

    // ─── Guardar la edición ─────────────────────────────────────────────────

    /** Lo que guarda el logger por una tarjeta: la interpretación vigente sobre su alimento. */
    private fun savedAs(tag: ResolvedTag): LoggedFood = interpretationOf(tag).toLoggedFood(loggedOf(tag))

    @Test
    fun `a food the user did not touch is saved exactly as it was, with its evidence and unit`() {
        val legacy = LoggedFood(
            id = "food-banana", foodName = "Banana", amount = 2.0, unit = "u",
            calories = 210.0, protein = 2.6, carbs = 54.0, fats = 0.8,
            analysisSource = AnalysisSource.DATABASE,
            evidenceJson = """{"source":"USDA_FOUNDATION","sourceRecordId":"173944"}""",
        )
        val regenerated = savedAs(loggedFoodToEditableTag(legacy))
        // The logger would rewrite both: the unit is always grams and the evidence is rebuilt without the source row.
        assertEquals("g", regenerated.unit)
        assertNotEquals(legacy.evidenceJson, regenerated.evidenceJson)

        val merged = mergeEditedFoods(listOf(regenerated), listOf(legacy))

        assertSame(legacy, merged.single())
        assertEquals("u", merged.single().unit)
    }

    @Test
    fun `an untouched estimate keeps its saved range and note when the meal is saved again`() {
        val merged = mergeEditedFoods(listOf(savedAs(loggedFoodToEditableTag(stew))), listOf(stew))

        assertSame(stew, merged.single())
    }

    @Test
    fun `an amount changed and changed back is the same food again`() {
        val tag = loggedFoodToEditableTag(rice)
        val roundTrip = refreshAfterEdit(withGrams(refreshAfterEdit(withGrams(tag, 300.0), "portion"), 150.0), "portion")

        val merged = mergeEditedFoods(listOf(savedAs(roundTrip)), listOf(rice))

        assertSame(rice, merged.single())
    }

    @Test
    fun `a food whose amount changed is saved as the logger calculated it`() {
        val edited = savedAs(refreshAfterEdit(withGrams(loggedFoodToEditableTag(rice), 300.0), "portion"))

        val merged = mergeEditedFoods(listOf(edited), listOf(rice))

        assertSame(edited, merged.single())
        assertEquals(300.0, merged.single().amount, 1e-9)
        assertEquals("the saved id is kept so the row is replaced, never duplicated", "food-rice", merged.single().id)
        assertEquals(390.0, merged.single().calories, 1e-6)
    }

    @Test
    fun `a food changed by hand or by another card is saved as calculated`() {
        val tag = loggedFoodToEditableTag(rice)
        val typed = savedAs(refreshAfterEdit(tag.copy(loggedFood = loggedOf(tag).copy(carbs = 50.0), hasManualEdits = true)))
        val renamed = loggedOf(tag).copy(foodName = "Arroz integral cocido")

        assertSame(typed, mergeEditedFoods(listOf(typed), listOf(rice)).single())
        assertSame(renamed, mergeEditedFoods(listOf(renamed), listOf(rice)).single())
    }

    @Test
    fun `new foods, a removed food and the order of the edit are respected`() {
        val added = rice.copy(id = "food-new", foodName = "Palta", calories = 160.0)
        val untouched = savedAs(loggedFoodToEditableTag(stew))

        val merged = mergeEditedFoods(listOf(added, untouched), listOf(rice, stew))

        assertEquals(listOf("food-new", "food-stew"), merged.map { it.id })
        assertSame("the new food is passed through", added, merged[0])
        assertSame("rice was removed: nothing of it comes back", stew, merged[1])
    }

    @Test
    fun `blank or repeated saved ids never pair a food with the wrong one`() {
        val blank = rice.copy(id = "")
        val twin = rice.copy(id = "twin")
        val repeated = savedAs(loggedFoodToEditableTag(twin))

        assertSame(blank, mergeEditedFoods(listOf(blank), listOf(blank)).single())
        assertSame(repeated, mergeEditedFoods(listOf(repeated), listOf(twin, twin.copy(foodName = "Otro"))).single())
    }

    // ─── La comida editada ──────────────────────────────────────────────────

    private val meal = NutritionLog(
        id = "log-1", date = "2026-10-03T12:00:00.000Z", mealType = MealType.LUNCH,
        foods = listOf(rice, stew), notes = "Almuerzo en casa", status = NutritionStatus.CONSUMED,
    )

    @Test
    fun `the edited meal keeps its id, notes and status and the day it was saved on`() {
        val edited = applyEdit(meal, listOf(savedAs(loggedFoodToEditableTag(rice))), MealType.DINNER, "2026-10-03")

        assertEquals("log-1", edited.id)
        assertEquals("Almuerzo en casa", edited.notes)
        assertEquals(NutritionStatus.CONSUMED, edited.status)
        assertEquals(MealType.DINNER, edited.mealType)
        assertEquals("the saved timestamp is not rewritten while the day stays", "2026-10-03T12:00:00.000Z", edited.date)
        assertSame(rice, edited.foods.single())
    }

    @Test
    fun `the date keeps its saved format while the day stays and uses the logger format when the day changes`() {
        val moved = applyEdit(meal, meal.foods, MealType.LUNCH, "2026-10-02")
        assertEquals("2026-10-02T12:00:00.000Z", moved.date)

        val plain = meal.copy(date = "2026-10-03")
        assertEquals("2026-10-03", applyEdit(plain, plain.foods, MealType.LUNCH, "2026-10-03").date)
    }

    @Test
    fun `an unchanged edit gives back the saved meal`() {
        val unchanged = applyEdit(meal, meal.foods.map { savedAs(loggedFoodToEditableTag(it)) }, meal.mealType, meal.date.take(10))

        assertEquals(meal, unchanged)
        assertNotNull(unchanged.foods.firstOrNull())
    }
}
