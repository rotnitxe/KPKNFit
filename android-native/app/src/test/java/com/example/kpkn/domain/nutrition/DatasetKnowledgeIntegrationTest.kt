package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.DatasetKnowledgeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.ceil

class DatasetKnowledgeIntegrationTest {
    @Before
    fun loadDataset() {
        if (!SemanticPortionRetriever.status().ready) {
            val asset = File("src/main/assets/food_data/dataset_knowledge.bin")
            DataInputStream(GZIPInputStream(FileInputStream(asset))).use { input ->
                SemanticPortionRetriever.install(DatasetKnowledgeStore.decode(input))
            }
        }
    }

    @Test
    fun `compiled asset contains every source document and valid postings`() {
        val status = SemanticPortionRetriever.status()
        assertTrue(status.ready)
        assertEquals(2, status.formatVersion)
        assertEquals(19_405, status.documentCount)
        assertTrue(status.tokenCount > 5_000)
        assertTrue(status.trigramCount > 3_000)
        assertTrue(status.portionPriorCount > 2_000)
    }

    @Test
    fun `retrieval returns absolute scored examples and structured macros`() {
        val result = SemanticPortionRetriever.retrieve(
            "valores nutricionales oficiales para 100 gramos de turkey young hen skin roasted",
        )

        assertTrue(result.matches.isNotEmpty())
        assertTrue(result.matches.first().score in 0.04..1.0)
        assertTrue(result.confidence in 0.0..1.0)
        assertNotNull(result.macroRange)
        assertTrue(result.macroRange!!.sampleCount > 0)
        assertTrue(result.macroRange!!.kcalMedian > 0.0)
        assertTrue(result.datasetChecksum?.length == 64)
    }

    @Test
    fun `context matching uses real documents without synthetic IDs`() {
        val result = SemanticPortionRetriever.retrieve(
            "post entreno pollo a la plancha con arroz",
        )

        assertTrue("POST_ENTRENO" in result.contextDetected)
        assertTrue(result.matches.all { it.docId in 0 until 19_405 })
    }

    @Test
    fun `golden Chilean and typo queries retrieve useful examples`() {
        val queries = listOf(
            "completo con palta tomate y mayo",
            "cazuela de vacuno con papa y zapallo",
            "un chorrito de aceite de oliva",
            "pechga de poyo ala plancha",
            "colacion de casino yogurt y galletas",
        )

        queries.forEach { query ->
            val result = SemanticPortionRetriever.retrieve(query)
            assertTrue("No matches for '$query'", result.matches.isNotEmpty())
            assertTrue("Low confidence for '$query'", result.confidence >= 0.10)
            assertTrue(result.matches.all { it.docId in 0 until 19_405 })
        }
    }

    @Test
    fun `warm retrieval p95 remains below ten milliseconds`() {
        val queries = listOf(
            "pollo a la plancha 200 g",
            "arroz cocido",
            "un puñado de almendras",
            "completo italiano",
            "post entreno pavo con papas",
        )
        repeat(5) { queries.forEach { SemanticPortionRetriever.retrieve(it) } }

        val elapsed = buildList {
            repeat(20) {
                queries.forEach { query ->
                    val startedAt = System.nanoTime()
                    SemanticPortionRetriever.retrieve(query)
                    add((System.nanoTime() - startedAt) / 1_000_000.0)
                }
            }
        }.sorted()
        val percentileIndex = (ceil(elapsed.size * 0.95).toInt() - 1).coerceIn(elapsed.indices)

        assertTrue("p95=${elapsed[percentileIndex]}ms", elapsed[percentileIndex] < 10.0)
    }

    @Test
    fun `parser can consume dataset priors for subjective portions`() {
        val query = "un puñado de almendras"
        val retrieval = SemanticPortionRetriever.retrieve(query)
        val parsed = parseMealDescription(query, retrieval)

        assertTrue(parsed.items.isNotEmpty())
        assertNotNull(parsed.items.first().amountGrams)
        assertTrue(parsed.items.first().amountGrams!! > 0.0)
    }

    @Test
    fun `dataset warnings never overwrite verified macros`() {
        val retrieval = SemanticPortionRetriever.RetrievalResult(
            query = "alimento",
            matches = emptyList(),
            contextDetected = emptyList(),
            portionPriors = emptyMap(),
            macroRange = SemanticPortionRetriever.MacroRangeEstimate(
                kcalMin = 10.0,
                kcalMax = 20.0,
                kcalMedian = 15.0,
                proteinMin = 1.0,
                proteinMax = 2.0,
                proteinMedian = 1.5,
                fatsMin = 1.0,
                fatsMax = 2.0,
                fatsMedian = 1.5,
                carbsMin = 1.0,
                carbsMax = 2.0,
                carbsMedian = 1.5,
            ),
            confidence = 0.8,
            elapsedMs = 1,
        )
        val input = MacroValidator.MacroInput(
            calories = 400.0,
            protein = 30.0,
            carbs = 40.0,
            fats = 13.3,
        )

        val result = MacroValidator.validate(input, retrieval, portionGrams = 100.0)

        assertFalse(result.wasAdjusted)
        assertEquals(input.calories, result.adjustedCalories, 0.001)
        assertEquals(input.protein, result.adjustedProtein, 0.001)
        assertEquals(input.carbs, result.adjustedCarbs, 0.001)
        assertEquals(input.fats, result.adjustedFats, 0.001)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `context detector uses dataset medians with bounded adjustments`() {
        val casino = ContextDetector.detect("colación del casino yogurt y galletas")
        val post = ContextDetector.detect("batido post entreno con proteína")

        assertEquals(ContextDetector.MealContext.CASINO, casino.primaryContext)
        assertEquals(ContextDetector.MealContext.POST_ENTRENO, post.primaryContext)
        assertTrue(casino.portionAdjustment in 0.65..1.50)
        assertTrue(post.proteinAdjustment in -0.10..0.30)
        assertNotNull(ContextDetector.getContextProfile(ContextDetector.MealContext.CASINO))
    }

    @Test
    fun `portion priors prefer retrieval matches over global averages`() {
        val query = "200g de pechuga de pollo a la plancha"
        val retrieval = SemanticPortionRetriever.retrieve(query)
        val grams = SemanticPortionRetriever.getGramsForFood("pechuga de pollo", retrieval)

        assertNotNull(grams)
        assertTrue(grams!! in 50.0..400.0)
    }

}
