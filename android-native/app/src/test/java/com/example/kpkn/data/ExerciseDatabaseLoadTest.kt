package com.example.kpkn.data

import com.example.kpkn.data.models.ExerciseMuscleInfo
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseDatabaseLoadTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testParseExerciseDatabase() {
        val file = listOf(
            File("../../catalog/exercises/v2/curation/evidence/legacy/exercise_database.json"),
            File("../catalog/exercises/v2/curation/evidence/legacy/exercise_database.json"),
            File("catalog/exercises/v2/curation/evidence/legacy/exercise_database.json"),
        ).first { it.exists() }
        assertTrue("exercise_database.json must exist", file.exists())
        val jsonText = file.readText()
        println("File size: ${jsonText.length} chars")
        try {
            val exercises = json.decodeFromString<List<ExerciseMuscleInfo>>(jsonText)
            println("Successfully parsed ${exercises.size} exercises!")
            assertTrue("Should parse all unique exercises", exercises.size == 257)
            assertTrue("Exercise IDs must be unique", exercises.map { it.id }.distinct().size == exercises.size)
            assertTrue(
                "subMuscleGroup must survive deserialization",
                exercises.any { !it.subMuscleGroup.isNullOrBlank() },
            )
        } catch (e: Throwable) {
            println("FAILED TO PARSE exercise_database.json!")
            println("Exception message: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
