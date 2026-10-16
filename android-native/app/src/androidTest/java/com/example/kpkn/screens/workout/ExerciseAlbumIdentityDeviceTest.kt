package com.example.kpkn.screens.workout

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.media.ExerciseAlbumModel
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Identidad real tras finalizar/serializar: UUID, ordinal no compacto y lado. */
@RunWith(AndroidJUnit4::class)
class ExerciseAlbumIdentityDeviceTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun media(index: Int, side: String? = null) = WorkoutMedia(
        id = "photo", kind = WorkoutMediaKind.PHOTO, filePath = "/unused", createdAtMs = 1L,
        workoutLogId = "log", exerciseId = "curl", setIndex = index, side = side,
    )
    private fun effort(value: Double, side: String? = null, skipped: Boolean = false) = CompletedSet(
        id = UUID.randomUUID().toString(), side = side, skipped = skipped,
        actualIntensityMode = IntensityMode.RIR, actualIntensityValue = value,
    )

    @Test fun uuidConHuecoOmitidoYSaltadaConservaOrdinalYLadosAlGuardarJson() {
        val exercise = Exercise("curl", "Curl", isUnilateral = true,
            sets = List(4) { ExerciseSet(id = "plan-$it", targetReps = 6) })
        val completed = mapOf(
            // No entry for ordinal 0: list order must never stand in for the plan ordinal.
            "curl_1_L" to effort(2.0, "left"),
            "curl_1_R" to effort(4.0, "right"),
            "curl_2_L" to effort(8.0, "left", skipped = true),
            "curl_3_L" to effort(1.0), // Map suffix also preserves legacy ongoing side.
        )
        val log = WorkoutLog("log", "p", "s", "A", "2026-10-16", 20,
            completedExercises = toCompletedExercises(Session("s", "A", exercises = listOf(exercise)), completed))
        val restored = json.decodeFromString<WorkoutLog>(json.encodeToString(log))
        assertEquals(listOf(1, 1, 2, 3), restored.completedExercises.single().sets.map { it.setIndex })
        assertEquals(completed.values.map { it.id }, restored.completedExercises.single().sets.map { it.id })
        assertEquals("RIR 2", ExerciseAlbumModel.effortForLog(media(1, "L"), restored))
        assertEquals("RIR 4", ExerciseAlbumModel.effortForLog(media(1, "right"), restored))
        assertEquals("RIR 1", ExerciseAlbumModel.effortForLog(media(3, "left"), restored))
        assertNull(ExerciseAlbumModel.effortForLog(media(0, "left"), restored))
        assertNull(ExerciseAlbumModel.effortForLog(media(2, "left"), restored))
        assertNull(ExerciseAlbumModel.effortForLog(media(1), restored))
        assertNull(ExerciseAlbumModel.effortForLog(media(3, "right"), restored))
    }

    @Test fun jsonAntiguoSinOrdinalNoAtribuyeEsfuerzoPorPosicionNiUuid() {
        val old = json.decodeFromString<CompletedSet>("""{"id":"legacy-uuid","side":"left","rir":6}""")
        assertNull(old.setIndex)
        val exercise = com.example.kpkn.data.models.CompletedExercise("curl", "Curl", sets = listOf(old))
        val log = WorkoutLog("log", "p", "s", "A", "2026-10-16", 20, completedExercises = listOf(exercise))
        assertNull(ExerciseAlbumModel.effortForLog(media(0, "left"), log))
        org.junit.Assert.assertTrue(ExerciseAlbumModel.entryOf(media(0, "left")).detail.startsWith("Esfuerzo no disponible"))
        val exactLegacy = log.copy(completedExercises = listOf(exercise.copy(sets = listOf(old.copy(id = "curl_3_L")))))
        assertEquals("RIR 6", ExerciseAlbumModel.effortForLog(media(3, "left"), exactLegacy))
        assertNull(ExerciseAlbumModel.effortForLog(media(3, "right"), exactLegacy))
        val current = old.copy(setIndex = 3)
        assertEquals(3, json.decodeFromString<CompletedSet>(json.encodeToString(current)).setIndex)
    }
}
