package com.example.kpkn.data

import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.multisport.GoalPriority
import com.example.kpkn.domain.multisport.SportExperience
import com.example.kpkn.domain.multisport.SportGoal
import com.example.kpkn.domain.multisport.SportGoalTarget
import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.TrainingIntent
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ProgramTrainingIntentSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun legacyProgramAndExplicitNullKeepUndeclaredIntent() {
        for (raw in listOf(
            """{"id":"old","name":"Plan antiguo"}""",
            """{"id":"old","name":"Plan antiguo","trainingIntent":null}""",
        )) {
            val program = json.decodeFromString<Program>(raw)
            assertNull(program.trainingIntent)
            assertNull(json.decodeFromString<Program>(json.encodeToString(program)).trainingIntent)
        }
    }

    @Test fun orderedTwoSportIntentSurvivesProgramJsonRoundTrip() {
        val intent = TrainingIntent(
            goals = listOf(
                SportGoal("mma", SportId.MMA, "Entrenar técnica", GoalPriority.RECREATION,
                    SportExperience.BEGINNER),
                SportGoal("run", SportId.RUNNING, "Completar una carrera", GoalPriority.PROGRESS,
                    SportExperience.INTERMEDIATE, SportGoalTarget("km", 21.1)),
            ),
            primaryGoalId = "run",
        )
        val program = Program("two-sports", "Dos deportes", author = "Usuario", trainingIntent = intent)
        val restored = json.decodeFromString<Program>(json.encodeToString(program))
        assertEquals(program, restored)
        assertEquals(listOf("mma", "run"), restored.trainingIntent!!.goals.map { it.id })
        assertEquals(GoalPriority.RECREATION, restored.trainingIntent!!.goals.first().priority)
        assertEquals("run", restored.trainingIntent!!.primaryGoalId)
    }
}
