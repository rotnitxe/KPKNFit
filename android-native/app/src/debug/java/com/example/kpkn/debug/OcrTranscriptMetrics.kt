package com.example.kpkn.debug

import com.example.kpkn.domain.exercises.ExerciseMatchLexicon
import java.io.File

/** Métricas del OCR contra una transcripción de referencia; solo las usa el arnés de depuración. */
internal data class OcrTranscriptMetrics(
    val expectedRows: Int,
    val recognizedRows: Int,
    val exactRows: Int,
    val exactCells: Int,
    val expectedCells: Int,
    val recognizedCells: Int,
    val wordErrors: Int,
    val expectedWords: Int,
    val characterErrors: Int,
    val expectedCharacters: Int,
) {
    val exactRowsPct: Double? get() = percentage(exactRows, maxOf(expectedRows, recognizedRows))
    val exactCellsPct: Double? get() = percentage(exactCells, maxOf(expectedCells, recognizedCells))
    val wordErrorRatePct: Double? get() = percentage(wordErrors, expectedWords)
    val characterErrorRatePct: Double? get() = percentage(characterErrors, expectedCharacters)

    private fun percentage(value: Int, total: Int): Double? = if (total == 0) null else value * 100.0 / total
}

/** Sidecar UTF-8 reference for `routine.jpg`: `routine.jpg.expected.txt`. */
internal fun expectedOcrTranscriptFile(corpusDirectory: File, sourceFile: File): File =
    File(corpusDirectory, "${sourceFile.name}.expected.txt")

/**
 * Compares reconstructed rows with a reference that uses the same ` | ` cell delimiter. Optional `--- página N ---`
 * markers are ignored for row counts and used to keep WER/CER alignment page-local when both files have them.
 */
internal fun compareOcrTranscripts(expected: String, recognized: String): OcrTranscriptMetrics {
    val expectedPages = pages(expected)
    val recognizedPages = pages(recognized)
    val expectedRows = expectedPages.flatten()
    val recognizedRows = recognizedPages.flatten()

    val expectedCellRows = expectedRows.map(::cells)
    val recognizedCellRows = recognizedRows.map(::cells)
    val rowPairs = alignNearby(expectedCellRows, recognizedCellRows)
    val exactRows = rowPairs.count { (expectedIndex, recognizedIndex) ->
        expectedCellRows[expectedIndex].isNotEmpty() &&
            expectedCellRows[expectedIndex] == recognizedCellRows[recognizedIndex]
    }
    val expectedCells = expectedCellRows.sumOf { it.size }
    val recognizedCells = recognizedCellRows.sumOf { it.size }
    var exactCells = 0
    for ((expectedIndex, recognizedIndex) in rowPairs) {
        val expectedRow = expectedCellRows[expectedIndex]
        val recognizedRow = recognizedCellRows[recognizedIndex]
        exactCells += alignNearby(expectedRow, recognizedRow).count { (expectedCell, recognizedCell) ->
            expectedRow[expectedCell] == recognizedRow[recognizedCell]
        }
    }

    val pagePairs = if (expectedPages.size == recognizedPages.size) {
        expectedPages.zip(recognizedPages)
    } else {
        listOf(expectedRows to recognizedRows)
    }
    var wordErrors = 0
    var expectedWords = 0
    var characterErrors = 0
    var expectedCharacters = 0
    for ((expectedPage, recognizedPage) in pagePairs) {
        val expectedText = normalize(expectedPage.joinToString(" ") { it.replace('|', ' ') })
        val recognizedText = normalize(recognizedPage.joinToString(" ") { it.replace('|', ' ') })
        val expectedTokens = expectedText.split(' ').filter(String::isNotEmpty)
        val recognizedTokens = recognizedText.split(' ').filter(String::isNotEmpty)
        wordErrors += editDistance(expectedTokens, recognizedTokens)
        expectedWords += expectedTokens.size
        characterErrors += editDistance(expectedText.toList(), recognizedText.toList())
        expectedCharacters += expectedText.length
    }

    return OcrTranscriptMetrics(
        expectedRows = expectedRows.size,
        recognizedRows = recognizedRows.size,
        exactRows = exactRows,
        exactCells = exactCells,
        expectedCells = expectedCells,
        recognizedCells = recognizedCells,
        wordErrors = wordErrors,
        expectedWords = expectedWords,
        characterErrors = characterErrors,
        expectedCharacters = expectedCharacters,
    )
}

/**
 * Aligns short ordered sequences while allowing nearby OCR-only or omitted items. Equal items are matched first; when the
 * current items differ, a bounded look-ahead skips the nearer unmatched item. If neither side has a nearby match, the pair
 * is treated as a substitution so later cells in a changed row can still be compared. The fixed window keeps this report
 * linear in the number of OCR rows, even for multi-page PDFs.
 */
private fun <T> alignNearby(expected: List<T>, recognized: List<T>): List<Pair<Int, Int>> {
    val pairs = ArrayList<Pair<Int, Int>>(minOf(expected.size, recognized.size))
    var expectedIndex = 0
    var recognizedIndex = 0
    while (expectedIndex < expected.size && recognizedIndex < recognized.size) {
        if (expected[expectedIndex] == recognized[recognizedIndex]) {
            pairs += expectedIndex to recognizedIndex
            expectedIndex++
            recognizedIndex++
            continue
        }

        val recognizedOffset = (1..ALIGNMENT_LOOKAHEAD).firstOrNull { offset ->
            recognizedIndex + offset < recognized.size && expected[expectedIndex] == recognized[recognizedIndex + offset]
        }
        val expectedOffset = (1..ALIGNMENT_LOOKAHEAD).firstOrNull { offset ->
            expectedIndex + offset < expected.size && expected[expectedIndex + offset] == recognized[recognizedIndex]
        }
        when {
            recognizedOffset != null && (expectedOffset == null || recognizedOffset <= expectedOffset) -> recognizedIndex++
            expectedOffset != null -> expectedIndex++
            else -> {
                pairs += expectedIndex to recognizedIndex
                expectedIndex++
                recognizedIndex++
            }
        }
    }
    return pairs
}

private const val ALIGNMENT_LOOKAHEAD = 32

private val PAGE_MARKER = Regex("---\\s*p[aá]gina\\s+\\d+\\s*---", RegexOption.IGNORE_CASE)

private fun pages(text: String): List<List<String>> {
    val result = mutableListOf<MutableList<String>>(mutableListOf())
    for (line in text.lineSequence().map(String::trim)) {
        if (PAGE_MARKER.matches(line)) {
            if (result.last().isNotEmpty()) result.add(mutableListOf<String>())
        } else if (line.isNotEmpty()) {
            result.last() += line
        }
    }
    return result.filter { it.isNotEmpty() }.map { it.toList() }
}

private fun cells(row: String): List<String> = row.split('|').map(::normalize)

private fun normalize(text: String): String = ExerciseMatchLexicon.normalize(text)

private fun <T> editDistance(reference: List<T>, recognized: List<T>): Int {
    if (reference.isEmpty()) return recognized.size
    if (recognized.isEmpty()) return reference.size
    var previous = IntArray(recognized.size + 1) { it }
    var current = IntArray(recognized.size + 1)
    for (referenceIndex in reference.indices) {
        current[0] = referenceIndex + 1
        for (recognizedIndex in recognized.indices) {
            val substitution = previous[recognizedIndex] + if (reference[referenceIndex] == recognized[recognizedIndex]) 0 else 1
            current[recognizedIndex + 1] = minOf(
                current[recognizedIndex] + 1,
                previous[recognizedIndex + 1] + 1,
                substitution,
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[recognized.size]
}
