package com.example.kpkn.domain.nutrition

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DatasetVocabularyTest {

    private var originalSnapshot: DatasetKnowledgeSnapshot? = null

    @Before
    fun saveOriginal() {
        originalSnapshot = SemanticPortionRetriever.currentSnapshot()
        SemanticPortionRetriever.install(buildSnapshot())
    }

    @After
    fun restoreOriginal() {
        val restored = originalSnapshot
        if (restored != null) {
            SemanticPortionRetriever.install(restored)
            return
        }
        val asset = java.io.File("src/main/assets/food_data/dataset_knowledge.bin")
        if (asset.isFile) {
            java.io.DataInputStream(java.util.zip.GZIPInputStream(java.io.FileInputStream(asset))).use { input ->
                SemanticPortionRetriever.install(
                    com.example.kpkn.data.food.DatasetKnowledgeStore.decode(input),
                )
            }
        }
    }

    @Test
    fun `pechga repairs to pechuga without becoming grams`() {
        assertEquals("pechuga", SemanticPortionRetriever.repairToken("pechga"))
        assertNull(SemanticPortionRetriever.getGramsForFood("pechuga", null))
        assertEquals("pechuga de pollo", SemanticPortionRetriever.repairQuery("pechga de pollo"))
    }

    @Test
    fun `bare pollo and carne are not rewritten to a cut`() {
        assertNull(SemanticPortionRetriever.repairToken("pollo"))
        assertEquals("pollo", SemanticPortionRetriever.repairQuery("pollo"))
        assertEquals("carne", SemanticPortionRetriever.repairQuery("carne"))
        assertTrue(SemanticPortionRetriever.rankingTokens("pollo").isEmpty())
        assertTrue(SemanticPortionRetriever.rankingTokens("carne").isEmpty())
    }

    @Test
    fun `neighbor tokens come from labeled foods never grams`() {
        val retrieval = SemanticPortionRetriever.retrieve("completo palta")
        val tokens = SemanticPortionRetriever.neighborFoodTokens(retrieval)
        assertTrue(tokens.contains("completo"))
        assertTrue(tokens.contains("palta"))
        assertFalse(tokens.any { it.toDoubleOrNull() != null })
        assertNull(SemanticPortionRetriever.getGramsForFood("completo", retrieval))
    }

    @Test
    fun `ranking tokens boost co-occurring foods for non-family queries`() {
        val tokens = SemanticPortionRetriever.rankingTokens("completo italiano")
        assertTrue(tokens.contains("palta") || tokens.contains("completo"))
    }

    private fun buildSnapshot(): DatasetKnowledgeSnapshot {
        val doc = DatasetDocument(
            id = 0,
            instruction = "completo italiano con palta",
            type = "DESCRIBE",
            contexts = emptySet(),
            cookingTerms = emptySet(),
            macroBasis = DatasetMacroBasis.PER_100_G,
            basisGrams = 100.0,
            macros = DatasetMacros(calories = 250.0, protein = 12.0, fats = 10.0, carbs = 25.0),
            portions = listOf(
                DatasetPortion("completo", 180.0),
                DatasetPortion("palta", 60.0),
            ),
            vectorNorm = 1.0,
            trigramCount = 4,
        )
        val tokenEntry = DatasetTokenEntry(
            idf = 1.0,
            postings = listOf(DatasetPosting(0, 1.0)),
        )
        return DatasetKnowledgeSnapshot(
            formatVersion = 2,
            checksum = "vocab-test",
            documents = listOf(doc),
            tokenIndex = mapOf(
                "completo" to tokenEntry,
                "italiano" to tokenEntry,
                "palta" to tokenEntry,
                "pechuga" to tokenEntry,
                "pollo" to tokenEntry,
            ),
            trigramIndex = emptyMap(),
            portionPriors = mapOf(
                "completo" to DatasetPortionPrior("completo", 180.0, frequency = 80),
                "palta" to DatasetPortionPrior("palta", 60.0, frequency = 40),
                "pechuga" to DatasetPortionPrior("pechuga", 150.0, frequency = 120),
                "pollo" to DatasetPortionPrior("pollo", 150.0, frequency = 200),
                "arroz" to DatasetPortionPrior("arroz", 120.0, frequency = 90),
            ),
            contextProfiles = emptyMap(),
        )
    }
}
