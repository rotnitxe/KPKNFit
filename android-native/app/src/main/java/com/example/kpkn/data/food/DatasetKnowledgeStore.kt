package com.example.kpkn.data.food

import android.content.Context
import com.example.kpkn.domain.nutrition.DatasetContextProfile
import com.example.kpkn.domain.nutrition.DatasetDocument
import com.example.kpkn.domain.nutrition.DatasetKnowledgeSnapshot
import com.example.kpkn.domain.nutrition.DatasetMacroBasis
import com.example.kpkn.domain.nutrition.DatasetMacros
import com.example.kpkn.domain.nutrition.DatasetPortion
import com.example.kpkn.domain.nutrition.DatasetPortionPrior
import com.example.kpkn.domain.nutrition.DatasetPosting
import com.example.kpkn.domain.nutrition.DatasetTokenEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

object DatasetKnowledgeStore {
    private const val ASSET_PATH = "food_data/dataset_knowledge.bin"
    private const val EXPECTED_MAGIC = "KPKNDS02"
    private const val EXPECTED_FORMAT_VERSION = 2
    private const val MAX_COLLECTION_SIZE = 100_000
    private const val MAX_STRING_BYTES = 1_000_000

    suspend fun load(context: Context): DatasetKnowledgeSnapshot = withContext(Dispatchers.IO) {
        context.assets.open(ASSET_PATH).use { asset ->
            DataInputStream(GZIPInputStream(asset.buffered())).use(::decode)
        }
    }

    internal fun decode(input: DataInputStream): DatasetKnowledgeSnapshot {
        val magicBytes = ByteArray(EXPECTED_MAGIC.length)
        input.readFully(magicBytes)
        val magic = magicBytes.toString(Charsets.US_ASCII)
        require(magic == EXPECTED_MAGIC) { "Invalid dataset knowledge magic: $magic" }

        val formatVersion = input.readInt()
        require(formatVersion == EXPECTED_FORMAT_VERSION) {
            "Unsupported dataset knowledge version: $formatVersion"
        }
        val checksum = input.readSizedString()

        val documentCount = input.readSafeCount("documents")
        val documents = ArrayList<DatasetDocument>(documentCount)
        repeat(documentCount) { expectedId ->
            val id = input.readInt()
            require(id == expectedId) {
                "Dataset document IDs must be contiguous: expected $expectedId, got $id"
            }
            val instruction = input.readSizedString()
            val type = input.readSizedString()
            val contexts = buildSet {
                repeat(input.readUnsignedByte()) { add(input.readSizedString()) }
            }
            val cookingTerms = buildSet {
                repeat(input.readUnsignedByte()) { add(input.readSizedString()) }
            }
            val macroBasis = DatasetMacroBasis.fromCode(input.readUnsignedByte())
            val basisGrams = input.readDouble()
            val macros = if (input.readUnsignedByte() == 1) {
                DatasetMacros(
                    calories = input.readDouble(),
                    protein = input.readDouble(),
                    fats = input.readDouble(),
                    carbs = input.readDouble(),
                )
            } else {
                null
            }
            val portions = buildList {
                repeat(input.readUnsignedShort()) {
                    add(
                        DatasetPortion(
                            food = input.readSizedString(),
                            grams = input.readDouble(),
                        ),
                    )
                }
            }
            documents += DatasetDocument(
                id = id,
                instruction = instruction,
                type = type,
                contexts = contexts,
                cookingTerms = cookingTerms,
                macroBasis = macroBasis,
                basisGrams = basisGrams,
                macros = macros,
                portions = portions,
                vectorNorm = input.readFloat().toDouble(),
                trigramCount = input.readUnsignedShort(),
            )
        }

        val tokenCount = input.readSafeCount("tokens")
        val tokenIndex = HashMap<String, DatasetTokenEntry>(mapCapacity(tokenCount))
        repeat(tokenCount) {
            val token = input.readSizedString()
            val idf = input.readFloat().toDouble()
            val postings = buildList {
                repeat(input.readUnsignedShort()) {
                    val documentId = input.readInt()
                    require(documentId in documents.indices) {
                        "Token '$token' references invalid document $documentId"
                    }
                    add(
                        DatasetPosting(
                            documentId = documentId,
                            weight = input.readFloat().toDouble(),
                        ),
                    )
                }
            }
            tokenIndex[token] = DatasetTokenEntry(idf = idf, postings = postings)
        }

        val trigramCount = input.readSafeCount("trigrams")
        val trigramIndex = HashMap<String, IntArray>(mapCapacity(trigramCount))
        repeat(trigramCount) {
            val trigram = input.readSizedString()
            val ids = IntArray(input.readUnsignedShort()) {
                input.readInt().also { documentId ->
                    require(documentId in documents.indices) {
                        "Trigram '$trigram' references invalid document $documentId"
                    }
                }
            }
            trigramIndex[trigram] = ids
        }

        val portionPriorCount = input.readSafeCount("portion priors")
        val portionPriors = HashMap<String, DatasetPortionPrior>(mapCapacity(portionPriorCount))
        repeat(portionPriorCount) {
            val food = input.readSizedString()
            portionPriors[food] = DatasetPortionPrior(
                food = food,
                grams = input.readDouble(),
                frequency = input.readInt(),
            )
        }

        val contextCount = input.readSafeCount("context profiles")
        val contextProfiles = HashMap<String, DatasetContextProfile>(mapCapacity(contextCount))
        repeat(contextCount) {
            val context = input.readSizedString()
            contextProfiles[context] = DatasetContextProfile(
                context = context,
                sampleCount = input.readInt(),
                medianGrams = input.readDouble(),
                medianCalories = input.readDouble(),
                medianProtein = input.readDouble(),
                medianFats = input.readDouble(),
                medianCarbs = input.readDouble(),
            )
        }

        return DatasetKnowledgeSnapshot(
            formatVersion = formatVersion,
            checksum = checksum,
            documents = documents,
            tokenIndex = tokenIndex,
            trigramIndex = trigramIndex,
            portionPriors = portionPriors,
            contextProfiles = contextProfiles,
        )
    }

    private fun DataInputStream.readSafeCount(label: String): Int =
        readInt().also { count ->
            if (count !in 0..MAX_COLLECTION_SIZE) {
                throw IOException("Invalid $label count: $count")
            }
        }

    private fun DataInputStream.readSizedString(): String {
        val byteCount = readInt()
        if (byteCount !in 0..MAX_STRING_BYTES) {
            throw IOException("Invalid dataset string length: $byteCount")
        }
        val bytes = ByteArray(byteCount)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun mapCapacity(expectedSize: Int): Int =
        ((expectedSize / 0.75f) + 1).toInt()
}
