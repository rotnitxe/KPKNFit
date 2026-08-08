package com.example.kpkn.data.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.kpkn.screens.workout.WorkoutRestModalState
import com.example.kpkn.screens.workout.WorkoutSetDraft

class SessionSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun decode_legacy_session_payload_keeps_safe_defaults() {
        val legacyPayload = """
            {
              "id": "session-1",
              "name": "Sesion legado",
              "description": "Editor antiguo",
              "dayOfWeek": 2,
              "isMainSession": true,
              "parts": [
                {
                  "id": "part-1",
                  "name": "Principal",
                  "exercises": [
                    {
                      "id": "exercise-1",
                      "name": "Back Squat",
                      "trainingMode": "REPS",
                      "sets": [
                        {
                          "id": "set-1",
                          "targetReps": 5,
                          "targetRPE": 8.0
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val session = json.decodeFromString<Session>(legacyPayload)

        assertEquals("session-1", session.id)
        assertEquals("Sesion legado", session.name)
        assertTrue(session.background == null)
        assertTrue(session.coverStyle == null)
        assertEquals(1, session.parts.size)
        assertEquals(1, session.parts.first().exercises.size)
        assertEquals(5, session.parts.first().exercises.first().sets.first().targetReps)
    }

    @Test
    fun mobility_series_without_rest_field_keeps_zero_default() {
        val mobility = json.decodeFromString<MobilitySeries>("""
            {
              "id": "mob-legacy",
              "name": "Movilidad legado",
              "sets": 2,
              "durationSeconds": 30
            }
        """.trimIndent())

        assertEquals(0, mobility.restBetweenSeconds)
    }

    @Test
    fun mobility_series_round_trip_preserves_rest_between_seconds() {
        val original = MobilitySeries(
            id = "mob-round-trip",
            name = "Movilidad con pausa",
            sets = 3,
            reps = "8",
            restBetweenSeconds = 25,
        )

        val decoded = json.decodeFromString<MobilitySeries>(
            json.encodeToString(MobilitySeries.serializer(), original),
        )

        assertEquals(25, decoded.restBetweenSeconds)
        assertEquals(3, decoded.sets)
        assertEquals("8", decoded.reps)
    }

    @Test
    fun encode_and_decode_rich_session_payload_preserves_new_editor_fields() {
        val session = Session(
            id = "session-rich",
            name = "Sesion completa",
            description = "Paridad Compose",
            dayOfWeek = 4,
            isMainSession = true,
            background = SessionBackground(
                type = SessionBackgroundType.COLOR,
                value = "gradient://ember",
                style = SessionBackgroundStyle(blur = 6f, brightness = 0.88f),
            ),
            coverStyle = CoverStyle(
                filters = CoverFilters(brightness = 1.1f, contrast = 1.05f, saturation = 0.92f),
                labelPosition = LabelPosition.BOTTOM_CENTER,
            ),
            warmup = listOf(
                WarmupExercise(
                    id = "warmup-1",
                    name = "Bike",
                    duration = 5,
                    sets = 1,
                    reps = "5 min",
                )
            ),
            microProgram = SessionMicroProgram(
                enabled = true,
                everyXCycles = 2,
                isMainInCycle = true,
                rules = listOf(MicroProgramRule("rule-1", "Regla 1", "Mantener RIR 2")),
            ),
            meetResults = MeetResults(placement = "1er lugar", total = 615.0, dots = 420.5, awards = listOf("Open")),
            competitionDetails = CompetitionDetails(
                competitionDate = "2026-05-10",
                startTime = "09:00",
                location = "Santiago",
                federation = "IPF",
                weighInDate = "2026-05-09",
                weighInTime = "08:00",
                reminderOneWeekEnabled = true,
                reminder48hEnabled = true,
                strategyNotes = "Abrir conservador y cerrar agresivo.",
            ),
            parts = listOf(
                SessionPart(
                    id = "part-1",
                    name = "Tier A",
                    color = "#00F0FF",
                    exercises = listOf(
                        Exercise(
                            id = "exercise-1",
                            name = "Back Squat",
                            exerciseDbId = "back-squat",
                            exerciseId = "back-squat",
                            trainingMode = TrainingMode.RM,
                            restTime = 180,
                            reference1RM = 220.0,
                            setupDetails = ExerciseSetupDetails(seatPosition = "Rack 12"),
                            prFor1RM = PrReference(210.0, 3),
                            consolidatedWeight = ConsolidatedWeight(180.0, 5),
                            brandEquivalencies = listOf(BrandEquivalency("Hammer", BrandPr(190.0, 5, 221.7))),
                            setupCues = listOf("Brace 360"),
                            executionCues = listOf("Drive traps up"),
                            isCompetitionLift = true,
                            isStarTarget = true,
                            goal1RM = 230.0,
                            goalPr = PrReference(200.0, 6),
                            unilateralMode = UnilateralMode.UNILATERAL_DIFFERENTIAL,
                            unilateralSideOrder = UnilateralSideOrder.RIGHT_LEFT,
                            restBetweenSidesSeconds = 25,
                            sets = listOf(
                                ExerciseSet(
                                    id = "set-1",
                                    targetReps = 5,
                                    targetRPE = 8.0,
                                    targetPercentageRM = 80.0,
                                    intensityMode = IntensityMode.LOAD,
                                    completedReps = 5,
                                    completedRPE = 8.5,
                                    isCalibrator = true,
                                    dropSets = listOf(DropSetData(120.0, 10)),
                                    restPauses = listOf(RestPauseData(15, 3)),
                                    attemptResult = AttemptResult.GOOD,
                                    judgingLights = listOf(true, true, true),
                                    technicalQuality = 9,
                                    discomfortIds = listOf("lumbar"),
                                    refereeNotes = "Buen control en bajada",
                                )
                            ),
                        )
                    ),
                )
            ),
        )

        val encoded = json.encodeToString(Session.serializer(), session)
        val decoded = json.decodeFromString<Session>(encoded)

        assertNotNull(decoded.background)
        assertNotNull(decoded.coverStyle)
        assertEquals(1, decoded.warmup.size)
        assertEquals(2, decoded.microProgram?.everyXCycles)
        assertEquals(615.0, decoded.meetResults?.total ?: 0.0, 0.001)
        assertEquals("2026-05-09", decoded.competitionDetails?.weighInDate)
        assertEquals(true, decoded.competitionDetails?.reminder48hEnabled)
        assertEquals("Rack 12", decoded.parts.first().exercises.first().setupDetails?.seatPosition)
        assertEquals(true, decoded.parts.first().exercises.first().isCompetitionLift)
        assertEquals(PrReference(200.0, 6), decoded.parts.first().exercises.first().goalPr)
        assertEquals(UnilateralSideOrder.RIGHT_LEFT, decoded.parts.first().exercises.first().unilateralSideOrder)
        assertEquals(25, decoded.parts.first().exercises.first().restBetweenSidesSeconds)
        assertEquals(true, decoded.parts.first().exercises.first().sets.first().isCalibrator)
        assertEquals(AttemptResult.GOOD, decoded.parts.first().exercises.first().sets.first().attemptResult)
        assertEquals(9, decoded.parts.first().exercises.first().sets.first().technicalQuality)
        assertEquals("lumbar", decoded.parts.first().exercises.first().sets.first().discomfortIds.first())
    }

    @Test
    fun decode_legacy_workout_log_payload_keeps_v2_defaults() {
        val legacyLog = """
            {
              "id": "log-1",
              "programId": "p-1",
              "sessionId": "s-1",
              "sessionName": "Sesion",
              "date": "2026-01-01T10:00:00Z",
              "durationMinutes": 55,
              "completedExercises": [
                {
                  "exerciseId": "ex-1",
                  "exerciseName": "Press",
                  "sets": [
                    {
                      "id": "set-1",
                      "weight": 80.0,
                      "reps": 8
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString<WorkoutLog>(legacyLog)
        assertTrue(decoded.contextualPerformanceStateV2.isEmpty())
        assertTrue(decoded.replacementDecisionsV2.isEmpty())
        assertNotNull(decoded.completedExercises.first().sets.first())
        assertTrue(decoded.completedExercises.first().sets.first().setOutcomeV2 == null)
    }

    @Test
    fun encode_and_decode_ongoing_workout_state_preserves_workout_session_contracts() {
        val ongoing = OngoingWorkoutState(
            programId = "program-1",
            session = Session(id = "session-1", name = "Sesion activa"),
            startTime = 123456789L,
            activeExerciseId = "bench",
            activeSetId = "set-1",
            activeSetIndex = 1,
            setDrafts = mapOf(
                "bench_1" to WorkoutSetDraft(
                    weightText = "102.5",
                    valueText = "5",
                    intensityText = "8.5",
                    isDirty = true,
                )
            ),
            manualLoadOverrides = mapOf("bench_1" to 102.5),
            loadSuggestionReasons = mapOf("bench_1" to "Base manual de la sesion"),
            dynamicWeights = mapOf("bench_1" to 102.5),
            editingSetKey = "bench_1",
            restModalState = WorkoutRestModalState(
                exerciseId = "bench",
                exerciseName = "Bench Press",
                plannedSeconds = 120,
                suggestedSeconds = 135,
                activeSeconds = 150,
                isManualOverride = true,
            ),
        )

        val encoded = json.encodeToString(OngoingWorkoutState.serializer(), ongoing)
        val decoded = json.decodeFromString<OngoingWorkoutState>(encoded)

        assertEquals("102.5", decoded.setDrafts["bench_1"]?.weightText)
        assertEquals(true, decoded.setDrafts["bench_1"]?.isDirty)
        assertEquals(102.5, decoded.manualLoadOverrides["bench_1"] ?: 0.0, 0.001)
        assertEquals("bench_1", decoded.editingSetKey)
        assertEquals(150, decoded.restModalState?.activeSeconds)
        assertEquals(true, decoded.restModalState?.isManualOverride)
    }
}
