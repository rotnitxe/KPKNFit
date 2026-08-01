package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.DatasetKnowledgeStore
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

/**
 * Harness para tests que instalan snapshots controlados en SemanticPortionRetriever
 * (singleton global). Restaura el snapshot previo, o el dataset real si no había
 * ninguno, para no contaminar los tests de integración.
 */
object DatasetTestHarness {

    fun snapshotFor(
        food: String,
        grams: Double,
        instruction: String = "2 $food",
        queryToken: String = food,
    ): DatasetKnowledgeSnapshot {
        val doc = DatasetDocument(
            id = 0,
            instruction = instruction,
            type = "portion_example",
            contexts = emptySet(),
            cookingTerms = emptySet(),
            macroBasis = DatasetMacroBasis.NONE,
            basisGrams = 0.0,
            macros = null,
            portions = listOf(DatasetPortion(food, grams)),
            vectorNorm = 1.0,
            trigramCount = 0,
        )
        val token = DatasetTokenEntry(idf = 1.0, postings = listOf(DatasetPosting(0, 1.0)))
        return DatasetKnowledgeSnapshot(
            formatVersion = 2,
            checksum = "test",
            documents = listOf(doc),
            tokenIndex = mapOf(queryToken to token),
            trigramIndex = emptyMap(),
            portionPriors = mapOf(food to DatasetPortionPrior(food, grams, frequency = 100)),
            contextProfiles = emptyMap(),
        )
    }

    fun restore(original: DatasetKnowledgeSnapshot?) {
        if (original != null) {
            SemanticPortionRetriever.install(original)
            return
        }
        // No había snapshot: reinstala el dataset real para los tests de integración.
        val asset = File("src/main/assets/food_data/dataset_knowledge.bin")
        DataInputStream(GZIPInputStream(FileInputStream(asset))).use { input ->
            SemanticPortionRetriever.install(DatasetKnowledgeStore.decode(input))
        }
    }
}
