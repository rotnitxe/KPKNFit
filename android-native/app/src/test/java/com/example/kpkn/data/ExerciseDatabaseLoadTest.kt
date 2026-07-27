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
        val file = File("src/main/assets/exercise_database.json")
        assertTrue("exercise_database.json must exist", file.exists())
        val jsonText = file.readText()
        println("File size: ${jsonText.length} chars")
        try {
            val exercises = json.decodeFromString<List<ExerciseMuscleInfo>>(jsonText)
            println("Successfully parsed ${exercises.size} exercises!")
            assertTrue("Should parse all 969 unique exercises", exercises.size == 969)
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
