package com.example.kpkn.domain.training.importer

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic labeled corpus: field accuracy is gated and every extra or missing exercise row fails the test. */
class GuidedImportBaselineTest {
    @Test
    fun `structured digital extraction meets the labeled corpus gate`() {
        val corpus = GuidedImportCorpus.serializer().let { serializer ->
            val bytes = requireNotNull(javaClass.getResourceAsStream("/import/guided_structured_corpus.json")) {
                "Missing guided import corpus"
            }.use { it.readBytes() }
            Json { ignoreUnknownKeys = true }.decodeFromString(serializer, String(bytes, Charsets.UTF_8))
        }
        var correctFields = 0
        var expectedFields = 0
        var extraRows = 0
        var missingRows = 0

        for (case in corpus.cases) {
            val textSheet = com.example.kpkn.data.importer.TextProgramReader.read(case.input, case.id)
            val draft = ProgramImportParser.parsePreparedText(textSheet, case.id)
            val actual = draft.weeks.flatMap { week ->
                week.days.flatMap { day -> day.exercises.map { Actual(week.index, day.dayOfWeek, it) } }
            }
            extraRows += (actual.size - case.expected.size).coerceAtLeast(0)
            missingRows += (case.expected.size - actual.size).coerceAtLeast(0)
            assertEquals("${case.id}: no invented or omitted exercise rows", case.expected.size, actual.size)

            case.expected.forEachIndexed { index, expected ->
                val row = actual.getOrNull(index)
                val fields = expected.fields()
                expectedFields += fields.size
                for ((field, value) in fields) {
                    if (row?.field(field) == value) correctFields++
                }
            }

            val actualWeekKinds = draft.weeks.associate { it.index.toString() to it.executionKind.name }
            for ((week, kind) in case.weekKinds) {
                assertEquals("${case.id}: week $week kind", kind, actualWeekKinds[week])
            }
            val actualRestDays = draft.weeks.flatMap { week ->
                week.days.filter { it.isRestDay }.map { ExpectedRestDay(week.index, it.dayOfWeek) }
            }.toSet()
            assertEquals("${case.id}: explicit rest days", case.restDays.toSet(), actualRestDays)

            val sourceLines = case.input.lines()
            for (lineNumber in case.noiseLines) {
                val noise = sourceLines.getOrNull(lineNumber - 1)
                assertNotNull("${case.id}: noise line $lineNumber exists", noise)
                assertFalse(
                    "${case.id}: navigation or advertising line became an exercise",
                    actual.any { it.exercise.rawName.equals(noise?.trim(), ignoreCase = true) },
                )
            }
        }

        val accuracy = correctFields.toDouble() / expectedFields
        println("Guided extraction corpus: field accuracy=$correctFields/$expectedFields; extra exercise rows=$extraRows; missing exercise rows=$missingRows")
        assertTrue("Structured field accuracy $correctFields/$expectedFields must be at least 98%", accuracy >= 0.98)
        assertEquals("No row pollution", 0, extraRows)
        assertEquals("No omitted rows", 0, missingRows)
    }

    private data class Actual(val week: Int, val dayOfWeek: Int?, val exercise: ImportedExercise) {
        fun field(name: String): Any? = when (name) {
            "week" -> week
            "dayOfWeek" -> dayOfWeek
            "name" -> exercise.rawName
            "sets" -> exercise.sets
            "repsMin" -> exercise.repsMin
            "repsMax" -> exercise.repsMax
            "load" -> exercise.loadKg
            "percent1Rm" -> exercise.percent1Rm
            "rpe" -> exercise.rpe
            "rir" -> exercise.rir
            "unit" -> exercise.loadUnit?.name
            "restSeconds" -> exercise.restSeconds
            "superset" -> exercise.supersetGroup
            "notes" -> exercise.notes
            "purpose" -> exercise.purpose.name
            "amrapMinimumReps" -> exercise.amrapMinimumReps
            else -> error("Unknown corpus field: $name")
        }
    }

    @Serializable
    private data class GuidedImportCorpus(val cases: List<CorpusCase>)

    @Serializable
    private data class CorpusCase(
        val id: String,
        val input: String,
        val noiseLines: List<Int> = emptyList(),
        val weekKinds: Map<String, String> = emptyMap(),
        val restDays: List<ExpectedRestDay> = emptyList(),
        val expected: List<ExpectedExercise>,
    )

    @Serializable
    private data class ExpectedExercise(
        val week: Int,
        val dayOfWeek: Int? = null,
        val name: String,
        val sets: Int,
        val repsMin: Int,
        val repsMax: Int,
        val load: Double? = null,
        val percent1Rm: Double? = null,
        val rpe: Double? = null,
        val rir: Int? = null,
        val unit: String? = null,
        val restSeconds: Int? = null,
        val superset: String? = null,
        val notes: String? = null,
        val purpose: String? = null,
        val amrapMinimumReps: Int? = null,
    ) {
        fun fields(): Map<String, Any> = buildMap {
            put("week", week)
            dayOfWeek?.let { put("dayOfWeek", it) }
            put("name", name)
            put("sets", sets)
            put("repsMin", repsMin)
            put("repsMax", repsMax)
            load?.let { put("load", it) }
            percent1Rm?.let { put("percent1Rm", it) }
            rpe?.let { put("rpe", it) }
            rir?.let { put("rir", it) }
            unit?.let { put("unit", it) }
            restSeconds?.let { put("restSeconds", it) }
            superset?.let { put("superset", it) }
            notes?.let { put("notes", it) }
            purpose?.let { put("purpose", it) }
            amrapMinimumReps?.let { put("amrapMinimumReps", it) }
        }
    }

    @Serializable
    private data class ExpectedRestDay(val week: Int, val dayOfWeek: Int?)
}
