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
            assertTrue("Should parse all 1030 exercises", exercises.size == 1030)
        } catch (e: Throwable) {
            println("FAILED TO PARSE exercise_database.json!")
            println("Exception message: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
