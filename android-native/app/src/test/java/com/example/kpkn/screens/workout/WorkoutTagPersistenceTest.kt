package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.WorkoutContextProfile
import com.example.kpkn.data.models.WorkoutTag
import com.example.kpkn.domain.workout.WorkoutTagResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTagPersistenceTest {

    private val smithId = "11111111-1111-4111-8111-111111111111"
    private val libreId = "22222222-2222-4222-8222-222222222222"

    @Test
    fun seededTagsSurviveWithoutOngoingState() {
        val profiles = mapOf(
            "bench|tag|$smithId" to WorkoutContextProfile(
                id = "bench|tag|$smithId",
                exerciseKey = "bench",
                tagId = smithId,
                setupLabel = "Smith",
            ),
            "bench|tag|$libreId" to WorkoutContextProfile(
                id = "bench|tag|$libreId",
                exerciseKey = "bench",
                tagId = libreId,
                setupLabel = "Libre",
            ),
        )
        val seeded = WorkoutTagResolver.seedFromProfiles(profiles.values, "bench")
        assertEquals(2, seeded.size)
        assertTrue(seeded.any { it.name == "Smith" && it.id == smithId })
        assertTrue(seeded.any { it.name == "Libre" && it.id == libreId })
    }

    @Test
    fun mergePrefersExistingIdsOverReseededNames() {
        val existing = listOf(WorkoutTag(id = smithId, name = "Smith", exerciseKey = "bench"))
        val seeded = WorkoutTagResolver.seedFromProfiles(
            listOf(
                WorkoutContextProfile(
                    id = "bench|tag|$smithId",
                    exerciseKey = "bench",
                    tagId = smithId,
                    setupLabel = "Smith",
                ),
            ),
            "bench",
        )
        val merged = buildList {
            addAll(existing)
            seeded.forEach { candidate ->
                if (none { it.id == candidate.id || WorkoutTagResolver.namesMatch(it.name, candidate.name) }) {
                    add(candidate)
                }
            }
        }
        assertEquals(1, merged.size)
        assertEquals(smithId, merged.single().id)
    }
}
