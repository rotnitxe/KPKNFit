package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.WorkoutTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTagResolverTest {

    private val smith = WorkoutTag(id = "uuid-smith", name = "Smith", exerciseKey = "bench")
    private val libre = WorkoutTag(id = "uuid-libre", name = "Libre", exerciseKey = "bench")
    private val default = WorkoutTag(
        id = "uuid-default",
        name = "Default",
        exerciseKey = "bench",
        isDefault = true,
        ownsUntaggedHistory = true,
    )

    @Test
    fun normalizeNameCollapsesCaseAndAccents() {
        assertEquals("maquina a", WorkoutTagResolver.normalizeName("  Máquina   A "))
        assertTrue(WorkoutTagResolver.namesMatch("Smith", "smith"))
        assertTrue(WorkoutTagResolver.isDefaultName("default"))
    }

    @Test
    fun anteriorChipOmitsDefaultAndBlank() {
        assertEquals("Anterior", WorkoutTagResolver.anteriorChipLabel(null))
        assertEquals("Anterior", WorkoutTagResolver.anteriorChipLabel("Default"))
        assertEquals("Anterior (Smith)", WorkoutTagResolver.anteriorChipLabel("Smith"))
    }

    @Test
    fun resolvesLegacyNameOnLogAndUuidOnSet() {
        val byName = log(
            exerciseTags = mapOf("ex-1" to "Smith"),
            sets = listOf(CompletedSet(id = "s1", weight = 80.0, reps = 5)),
        )
        assertEquals(
            "uuid-smith",
            WorkoutTagResolver.resolvedTagIdFromLog(
                byName,
                byName.completedExercises.first(),
                listOf(smith, libre),
            ),
        )

        val byUuid = log(
            exerciseTagIds = mapOf("ex-1" to "uuid-libre"),
            sets = listOf(CompletedSet(id = "s1", weight = 70.0, reps = 6, tagId = "uuid-libre", tagName = "Libre")),
        )
        assertEquals(
            "uuid-libre",
            WorkoutTagResolver.resolvedTagIdFromLog(
                byUuid,
                byUuid.completedExercises.first(),
                listOf(smith, libre),
            ),
        )
    }

    @Test
    fun untaggedLogsBelongToOwner() {
        val untagged = log(sets = listOf(CompletedSet(id = "s1", weight = 60.0, reps = 8)))
        val tags = listOf(smith.copy(ownsUntaggedHistory = false), default)
        assertTrue(
            WorkoutTagResolver.logMatchesTag(
                untagged,
                untagged.completedExercises.first(),
                default,
                tags,
            ),
        )
        assertFalse(
            WorkoutTagResolver.logMatchesTag(
                untagged,
                untagged.completedExercises.first(),
                smith,
                tags,
            ),
        )
    }

    @Test
    fun resetCutoffIgnoresOlderLogs() {
        val old = log(
            date = "2026-08-01T10:00:00.000Z",
            exerciseTags = mapOf("ex-1" to "Smith"),
            sets = listOf(CompletedSet(id = "s1", weight = 80.0, reps = 5, tagId = "uuid-smith")),
        )
        val reset = smith.copy(historyResetAtIso = "2026-09-01T00:00:00.000Z")
        assertFalse(
            WorkoutTagResolver.logMatchesTag(
                old,
                old.completedExercises.first(),
                reset,
                listOf(reset),
            ),
        )
    }

    @Test
    fun strictFilterDoesNotFallBackToOtherTags() {
        val smithLog = log(
            id = "l1",
            exerciseTags = mapOf("ex-1" to "Smith"),
            sets = listOf(CompletedSet(id = "s1", weight = 100.0, reps = 3, tagId = "uuid-smith")),
        )
        val libreLog = log(
            id = "l2",
            date = "2026-09-02T10:00:00.000Z",
            exerciseTags = mapOf("ex-1" to "Libre"),
            sets = listOf(CompletedSet(id = "s2", weight = 40.0, reps = 10, tagId = "uuid-libre")),
        )
        val filtered = WorkoutTagResolver.filterLogs(
            logs = listOf(libreLog, smithLog),
            matchingExercise = { it.completedExercises.first() },
            tag = libre,
            allTags = listOf(smith, libre),
            strict = true,
        )
        assertEquals(listOf("l2"), filtered.map { it.id })
        assertNull(
            WorkoutTagResolver.filterLogs(
                logs = listOf(smithLog),
                matchingExercise = { it.completedExercises.first() },
                tag = libre,
                allTags = listOf(smith, libre),
                strict = true,
            ).firstOrNull(),
        )
    }

    @Test
    fun seedFromProfilesKeepsUuidTagId() {
        val uuid = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        val profile = com.example.kpkn.data.models.WorkoutContextProfile(
            id = "bench|tag|$uuid",
            exerciseKey = "bench",
            tagId = uuid,
            setupLabel = "Smith",
        )
        val seeded = WorkoutTagResolver.seedFromProfiles(listOf(profile), "bench")
        assertEquals(1, seeded.size)
        assertEquals(uuid, seeded.single().id)
        assertEquals("Smith", seeded.single().name)
    }

    @Test
    fun seedFromProfilesUsesProfileIdWhenTagIdIsNotUuid() {
        val profile = com.example.kpkn.data.models.WorkoutContextProfile(
            id = "bench|tag|smith",
            exerciseKey = "bench",
            tagId = "smith",
            setupLabel = "Smith",
        )
        val seeded = WorkoutTagResolver.seedFromProfiles(listOf(profile), "bench")
        assertEquals("bench|tag|smith", seeded.single().id)
        assertEquals("Smith", seeded.single().name)
    }

    private fun log(
        id: String = "log-1",
        date: String = "2026-09-01T10:00:00.000Z",
        exerciseTags: Map<String, String> = emptyMap(),
        exerciseTagIds: Map<String, String> = emptyMap(),
        sets: List<CompletedSet>,
    ): WorkoutLog = WorkoutLog(
        id = id,
        programId = "p",
        sessionId = "s",
        sessionName = "sesión",
        date = date,
        durationMinutes = 40,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "ex-1",
                exerciseName = "Press",
                canonicalExerciseId = "bench",
                sets = sets,
            ),
        ),
        exerciseTags = exerciseTags,
        exerciseTagIds = exerciseTagIds,
    )
}
