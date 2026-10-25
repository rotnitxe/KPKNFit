package com.example.kpkn.domain.multisport

import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.models.Program
import com.example.kpkn.screens.onboarding.InvalidSetupTrainingIntentException
import com.example.kpkn.screens.onboarding.SetupActivationPayload
import com.example.kpkn.screens.onboarding.SetupMultisportIntent
import com.example.kpkn.screens.onboarding.SetupWizardDraft
import java.time.LocalDate
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class MultisportIntentPolicyTest {
    @Test
    fun preservesGoalOrderAndDefensivelyCopiesLists() {
        val source = mutableListOf(
            goal(id = "run", sport = SportId.RUNNING),
            goal(id = "strength", sport = SportId.STRENGTH),
        )
        val intent = TrainingIntent(goals = source, primaryGoalId = "strength")

        source.reverse()
        assertEquals(listOf("run", "strength"), intent.goals.map(SportGoal::id))

        @Suppress("UNCHECKED_CAST")
        (intent.goals as MutableList<SportGoal>).clear()
        assertEquals(listOf("run", "strength"), intent.goals.map(SportGoal::id))
        assertEquals(emptyList<TrainingIntentValidationError>(), TrainingIntentPolicy.validate(intent))
    }

    @Test
    fun validationIsDeterministicAndIdempotent() {
        val intent = TrainingIntent(
            goals = listOf(
                goal(id = "run", sport = SportId.RUNNING),
                goal(id = "strength", sport = SportId.STRENGTH),
            ),
            primaryGoalId = "strength",
        )

        val first = TrainingIntentPolicy.validate(intent)
        assertTrue(first.isEmpty())
        assertEquals(first, TrainingIntentPolicy.validate(intent))
    }

    @Test
    fun reportsAllInvalidFieldsInStableOrderWithoutThrowing() {
        val intent = TrainingIntent(
            goals = listOf(
                goal(
                    id = " ",
                    objective = "\t",
                    target = SportGoalTarget(unit = " ", value = Double.NaN),
                ),
                goal(id = "repeat"),
                goal(id = "repeat", target = SportGoalTarget(unit = "km", value = Double.POSITIVE_INFINITY)),
            ),
            primaryGoalId = "missing",
        )

        val expected = listOf(
            TrainingIntentValidationError.EmptyGoalId(0),
            TrainingIntentValidationError.EmptyObjective(0),
            TrainingIntentValidationError.EmptyTargetUnit(0),
            TrainingIntentValidationError.NonFiniteTargetValue(0),
            TrainingIntentValidationError.DuplicateGoalId("repeat"),
            TrainingIntentValidationError.NonFiniteTargetValue(2),
            TrainingIntentValidationError.PrimaryGoalNotFound("missing"),
        )
        assertEquals(expected, TrainingIntentPolicy.validate(intent))
        assertEquals(expected, TrainingIntentPolicy.validate(intent))
    }

    @Test
    fun blankPrimaryIdIsReportedAsEmptyAndMissing() {
        val intent = TrainingIntent(goals = listOf(goal(id = "run")), primaryGoalId = " ")

        assertEquals(
            listOf(
                TrainingIntentValidationError.EmptyPrimaryGoalId,
                TrainingIntentValidationError.PrimaryGoalNotFound(" "),
            ),
            TrainingIntentPolicy.validate(intent),
        )
    }

    @Test
    fun dbJsonRoundTripPreservesOrderAndKnownFields() {
        val intent = TrainingIntent(
            goals = listOf(
                goal(
                    id = "run",
                    sport = SportId.RUNNING,
                    target = SportGoalTarget(unit = "km_per_week", value = 18.5),
                ),
                goal(id = "strength", sport = SportId.POWERLIFTING, priority = GoalPriority.MAINTAIN),
            ),
            primaryGoalId = "strength",
        )

        val decoded = dbJson.decodeFromString<TrainingIntent>(dbJson.encodeToString(intent))

        assertEquals(intent, decoded)
        assertEquals(listOf("run", "strength"), decoded.goals.map(SportGoal::id))
        assertTrue(TrainingIntentPolicy.validate(decoded).isEmpty())
    }

    @Test
    fun futureVersionPreservesUnknownSportAndAllOpaqueJsonWithoutInterpretation() {
        val json = """
            {
              "schemaVersion": 2,
              "goals": [{
                "id": "run",
                "sport": "FUTURE_ORBITAL_SPORT",
                "objective": "Finish a local event",
                "priority": "PROGRESS",
                "experience": "BEGINNER",
                "futureGoalField": {"preserved": [1, null, "data"]}
              }],
              "primaryGoalId": "run",
              "futureIntentField": {"revision": 3}
            }
        """.trimIndent()

        val decoded = dbJson.decodeFromString<TrainingIntent>(json)
        assertEquals(2, decoded.schemaVersion)
        assertTrue(decoded.isOpaque)
        assertThrows(IllegalStateException::class.java) { decoded.goals }
        assertThrows(IllegalStateException::class.java) { decoded.primaryGoalId }
        assertEquals(
            listOf(TrainingIntentValidationError.UnsupportedSchemaVersion(2)),
            TrainingIntentPolicy.validate(decoded),
        )

        val reencoded = dbJson.encodeToString(decoded)
        assertEquals(dbJson.parseToJsonElement(json), dbJson.parseToJsonElement(reencoded))
        assertEquals(decoded, dbJson.decodeFromString<TrainingIntent>(reencoded))
    }

    @Test
    fun futureProgramLoadsAndRoundTripsButActivationAndProjectionRejectIt() {
        // Future versions need not even retain v1 goal shape or primary identity.
        val raw = """{"schemaVersion":9,"goals":[{"sport":"UNKNOWN_FUTURE_SPORT"}],"futureIntentField":{"nested":[true,2]}}"""
        val programJson = """{"id":"p","name":"Future plan","trainingIntent":$raw}"""
        val program = dbJson.decodeFromString<Program>(programJson)
        val intent = program.trainingIntent!!
        val expected = listOf(TrainingIntentValidationError.UnsupportedSchemaVersion(9))
        assertEquals(expected, TrainingIntentPolicy.validate(intent))
        val activatedError = assertThrows(InvalidSetupTrainingIntentException::class.java) {
            SetupActivationPayload.programForActivation(
                program, SetupWizardDraft(), LocalDate.of(2026, 10, 18),
            )
        }
        assertEquals(expected, activatedError.errors)
        assertThrows(InvalidSetupTrainingIntentException::class.java) {
            SetupMultisportIntent.primaryLegacyProfile(intent)
        }
        val reencoded = dbJson.parseToJsonElement(dbJson.encodeToString(program)).jsonObject
        assertEquals(dbJson.parseToJsonElement(raw), reencoded["trainingIntent"])
        assertEquals(program, dbJson.decodeFromString<Program>(dbJson.encodeToString(program)))
    }

    @Test
    fun v1KeepsRepositoryUnknownFieldPolicyAndMissingVersionDefault() {
        val raw = """{"goals":[{"id":"run","sport":"RUNNING","objective":"Finish","priority":"PROGRESS","experience":"BEGINNER","extra":true}],"primaryGoalId":"run","extra":{}}"""
        val intent = dbJson.decodeFromString<TrainingIntent>(raw)
        assertFalse(intent.isOpaque)
        assertEquals(1, intent.schemaVersion)
        assertEquals(SportId.RUNNING, intent.goals.single().sport)
        assertTrue(TrainingIntentPolicy.validate(intent).isEmpty())
        assertFalse(dbJson.encodeToString(intent).contains("extra"))
        assertEquals(intent, dbJson.decodeFromString<TrainingIntent>(dbJson.encodeToString(intent)))
    }

    @Test
    fun unsupportedVersionCannotBeConstructedAsEditableV1Goals() {
        assertThrows(IllegalArgumentException::class.java) {
            TrainingIntent(schemaVersion = 2, goals = listOf(goal("run")), primaryGoalId = "run")
        }
    }

    private fun goal(
        id: String,
        sport: SportId = SportId.STRENGTH,
        objective: String = "Build sustainable progress",
        priority: GoalPriority = GoalPriority.PROGRESS,
        experience: SportExperience = SportExperience.INTERMEDIATE,
        target: SportGoalTarget? = null,
    ) = SportGoal(
        id = id,
        sport = sport,
        objective = objective,
        priority = priority,
        experience = experience,
        target = target,
    )
}
