package com.example.kpkn.data.models

import com.example.kpkn.data.db.OngoingWorkoutEntity
import com.example.kpkn.data.db.toOngoingWorkoutState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SetPurposeContractTest {
    private val json = Json { encodeDefaults = true }

    @Test fun explicitFalseInOldCompletedSetJsonIsEvidenceButAnAbsentFlagIsNot() {
        val disabled = json.decodeFromString<CompletedSet>("""{"id":"old","reps":5,"amrapPerformed":false}""")
        assertEquals(SetPurpose.NONE, disabled.performedPurpose())
        val unknown = json.decodeFromString<CompletedSet>("""{"id":"old","reps":5}""")
        assertNull(unknown.performedPurpose())
        val active = json.decodeFromString<CompletedSet>("""{"id":"old","reps":5,"amrapPerformed":true}""")
        assertEquals(SetPurpose.AMRAP, active.performedPurpose())
        for (codec in listOf(Json, json)) {
            assertEquals(unknown, codec.decodeFromString<CompletedSet>(codec.encodeToString(unknown)))
        }
    }

    @Test fun recordedContextDistinguishesConfigurationSetupSideAndLoadConvention() {
        fun key(config: String = "bench", setup: String = "rack", side: String? = "left",
                convention: LoadQuantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL) =
            buildRecordedSetContextKey(config, "bench|brand|tag|LOAD|REPS", setup, side, convention)
        assertEquals(key(), key())
        assertNotEquals(key(), key(config = "incline"))
        assertNotEquals(key(), key(setup = "other-rack"))
        assertNotEquals(key(), key(side = "right"))
        assertNotEquals(key(), key(side = null))
        assertNotEquals(key(), key(convention = LoadQuantityConvention.PER_IMPLEMENT))
    }

    @Test fun nextSessionsScopeBelongsOnlyToCalibrators() {
        val set = ExerciseSet("s", purpose = SetPurpose.CALIBRATOR, calibrationScope = CalibrationScope.NEXT_SESSIONS)
        assertEquals(set, json.decodeFromString<ExerciseSet>(json.encodeToString(set)))
        val normal = json.decodeFromString<ExerciseSet>("""{"id":"s","purpose":"AMRAP","calibrationScope":"NEXT_SESSIONS"}""")
        assertNull(normal.calibrationScope)
    }

    @Test fun legacyFlagsAndIntensityBecomePurposeWithCalibratorPriority() {
        val cases = listOf(
            "\"isAmrap\":true" to SetPurpose.AMRAP,
            "\"intensityMode\":\"AMRAP\"" to SetPurpose.AMRAP,
            "\"isAmrap\":true,\"isCalibrator\":true" to SetPurpose.CALIBRATOR,
            "\"isCalibrator\":true" to SetPurpose.CALIBRATOR,
        )
        for ((legacy, expected) in cases) {
            val set = json.decodeFromString<ExerciseSet>("""{"id":"s","targetReps":6,"targetRepsRange":{"min":4,"max":6},$legacy}""")
            assertEquals(expected, set.purpose)
            assertEquals(4, set.amrapMinimumReps)
            assertEquals(if (expected == SetPurpose.CALIBRATOR) CalibrationScope.SESSION else null, set.calibrationScope)
        }
    }

    @Test fun newPurposeWinsAndNoMinimumSurvivesEveryJsonConfiguration() {
        val raw = """{"id":"s","purpose":"NONE","isAmrap":true,"isCalibrator":true,"intensityMode":"AMRAP"}"""
        assertEquals(SetPurpose.NONE, json.decodeFromString<ExerciseSet>(raw).purpose)
        for (codec in listOf(Json, json, Json { explicitNulls = false })) {
            val set = ExerciseSet("s", targetReps = 6, purpose = SetPurpose.AMRAP)
            val encoded = codec.encodeToString(set)
            assertTrue(encoded.contains("\"amrapMinimumReps\":null"))
            assertFalse(encoded.contains("isAmrap"))
            assertFalse(encoded.contains("isCalibrator"))
            assertEquals(set, codec.decodeFromString<ExerciseSet>(encoded))
        }
    }

    @Test fun deadExerciseFlagIsIgnoredEvenByStrictJsonAndCannotCreateACalibrator() {
        val exercise = json.decodeFromString<Exercise>("""{"id":"e","name":"Press","isCalibratorAmrap":true,"sets":[{"id":"s"}]}""")
        assertEquals(SetPurpose.NONE, exercise.sets.single().purpose)
        assertFalse(json.encodeToString(exercise).contains("isCalibratorAmrap"))
    }

    @Test fun legacyExerciseModeMigratesOnceAndCannotReviveExplicitNone() {
        val exercise = json.decodeFromString<Exercise>("""{"id":"e","name":"Press","trainingMode":"AMRAP","sets":[{"id":"old","targetReps":5},{"id":"new","purpose":"NONE"}]}""")
        assertEquals(SetPurpose.AMRAP, exercise.sets[0].purpose)
        assertEquals(5, exercise.sets[0].amrapMinimumReps)
        assertEquals(SetPurpose.NONE, exercise.sets[1].purpose)
        assertEquals(exercise, json.decodeFromString<Exercise>(json.encodeToString(exercise)))
    }

    @Test fun oldTrainingMaxMovesOutOfOneRmWithoutRepeatingOnNewJson() {
        val legacy = """{"id":"e","name":"Press","reference1RM":90.0,"sets":[{"id":"s","loadBasis":"PERCENT_TM","targetPercentageRM":80.0}]}"""
        val migrated = json.decodeFromString<Exercise>(legacy)
        assertNull(migrated.reference1RM)
        assertEquals(90.0, migrated.referenceTrainingMax!!, 0.0)
        val both = migrated.copy(reference1RM = 100.0)
        for (codec in listOf(Json, json)) {
            assertEquals(both, codec.decodeFromString<Exercise>(codec.encodeToString(both)))
            val pending = both.copy(referenceTrainingMax = null)
            assertEquals(pending, codec.decodeFromString<Exercise>(codec.encodeToString(pending)))
        }
        assertEquals(100.0, json.decodeFromString<Exercise>(legacy.replace("PERCENT_TM", "PERCENT_1RM").replace("90.0", "100.0")).reference1RM!!, 0.0)
    }

    @Test fun roomJsonReadsOldContractWithoutSqlMigration() {
        val entity = OngoingWorkoutEntity(data = """{"programId":"p","startTime":100,"session":{"id":"s","name":"Sesión","exercises":[{"id":"e","name":"Press","reference1RM":90.0,"sets":[{"id":"a","isCalibrator":true,"targetReps":5,"loadBasis":"PERCENT_TM"}]}]}}""")
        val restored = entity.toOngoingWorkoutState()!!
        val exercise = restored.session.exercises.single()
        assertEquals(90.0, exercise.referenceTrainingMax!!, 0.0)
        assertNull(exercise.reference1RM)
        assertEquals(SetPurpose.CALIBRATOR, exercise.sets.single().purpose)
        assertEquals(5, exercise.sets.single().amrapMinimumReps)
    }
}
