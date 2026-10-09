package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.RingsLiftPattern
import com.example.kpkn.data.models.RingsMuscleGroup
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AugePerMuscleInitialEvidenceTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val capture = LocalDate.of(2026, 10, 8).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun perMuscle(
        evidence: InitialRecoveryEvidence?,
        history: List<WorkoutLog> = emptyList(),
    ): Map<String, MuscleRecoveryStatus> = AugeRecoveryEngine.getPerMuscleBatteries(
        history = history,
        wellbeing = null,
        settings = Settings(initialRecoveryEvidence = evidence),
        nowOverrideMs = capture,
        zone = zone,
    )

    /** Ayer, sentadilla y 9 series de piernas: cuádriceps a 88 con la ley v2 (ver InitialRecoveryEvidenceV2Test). */
    private fun legsEvidenceV2(): InitialRecoveryEvidence = InitialRecoveryEvidenceFactory.fromInputsV2(
        capturedAtMs = capture,
        zone = zone,
        trainedDays = setOf(1),
        intensity = InitialRecoveryIntensity.HARD,
        lifts = setOf(RingsLiftPattern.SQUAT),
        groupSets = mapOf(RingsMuscleGroup.LEGS to 9),
    )

    @Test
    fun withoutV2EvidenceMusclesDoNotChange() {
        val v1 = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = capture,
            recencyDays = 1,
            sessions = 3,
            type = InitialRecoveryActivityType.STRENGTH,
            intensity = InitialRecoveryIntensity.HARD,
        )
        assertEquals(perMuscle(null), perMuscle(v1))
    }

    @Test
    fun v2EvidenceLowersTheLoadedPillarAndKeepsTheRest() {
        val base = perMuscle(null)
        val withEvidence = perMuscle(legsEvidenceV2())
        assertEquals(88, withEvidence.getValue("Cuádriceps").recoveryScore)
        assertEquals(base.getValue("Pectorales"), withEvidence.getValue("Pectorales"))
    }

    @Test
    fun sameDayRealLogDiscardsTheDayLoad() {
        val log = WorkoutLog(
            id = "real-log",
            programId = "program",
            sessionId = "session",
            sessionName = "Pierna",
            date = LocalDate.of(2026, 10, 7).atTime(18, 0).atZone(zone).toOffsetDateTime().toString(),
            durationMinutes = 60,
        )
        assertEquals(100, perMuscle(legsEvidenceV2(), history = listOf(log)).getValue("Cuádriceps").recoveryScore)
        assertEquals(88, perMuscle(legsEvidenceV2()).getValue("Cuádriceps").recoveryScore)
    }
}
