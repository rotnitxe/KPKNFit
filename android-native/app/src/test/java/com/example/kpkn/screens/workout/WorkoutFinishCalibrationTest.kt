package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.PostSessionPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutFinishCalibrationTest {
    private val preview = PostSessionPreview(
        neural = 90,
        spinal = 88,
        muscular = 80,
        perMuscle = mapOf(
            "Pectorales" to muscle("Pectorales", 78),
            "Tríceps" to muscle("Tríceps", 86),
        ),
        globalCnsDrain = 10,
        globalMuscularDrain = 20,
        globalSpinalDrain = 12,
        automaticImpact = null,
        involvedVolumeMuscles = setOf("Pectorales", "Tríceps"),
    )

    @Test
    fun noEdit_hasEmptyDeltaAndZeroAdjustment() {
        val seed = mapOf("Pectorales" to 78, "Tríceps" to 86)
        assertTrue(seed.filter { it.value != seed[it.key] }.isEmpty())
        assertEquals(80, recalibratedCanonicalMuscularBattery(preview, seed, seed, emptySet()))
    }

    @Test
    fun editingOnlyChest_changesOnlyChestAndRestoringRemovesKey() {
        val seed = mapOf("Pectorales" to 78, "Tríceps" to 86)
        val edited = seed + ("Pectorales" to 70)
        val keys = edited.keys.filterTo(mutableSetOf()) { edited[it] != seed[it] }
        assertEquals(setOf("Pectorales"), keys)
        assertTrue("Tríceps nunca entra en el delta", "Tríceps" !in keys)
        val restored = seed + ("Pectorales" to 78)
        assertTrue(restored.keys.filterTo(mutableSetOf()) { restored[it] != seed[it] }.isEmpty())
    }

    private fun muscle(name: String, score: Int) = com.example.kpkn.data.models.MuscleRecoveryStatus(
        muscleName = name,
        recoveryScore = score,
        hoursToRecovery = 24,
        hoursSinceLastSession = 0,
        effectiveSets = 3,
        status = com.example.kpkn.data.models.RecoveryStatus.RECOVERING,
    )
}
