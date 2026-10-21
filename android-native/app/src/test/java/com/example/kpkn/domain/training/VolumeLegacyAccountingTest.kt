package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Test

/** Los valores heredados protegen al generador y las plantillas; T2 mantiene su API paralela. */
class VolumeLegacyAccountingTest {
    private fun session(muscles: List<InvolvedMuscle>): Session = Session(
        id = "sesion",
        name = "Sesión de glúteos",
        exercises = listOf(Exercise(
            id = "gluteos",
            name = "Extensión de cadera",
            effectiveMuscles = muscles,
            sets = List(3) { ExerciseSet("serie-$it", targetReps = 8, weight = 40.0) },
        )),
    )

    @Test
    fun `las contabilidades son intencionalmente distintas entre roles heredados y maximo canonico`() {
        val sessions = listOf(session(listOf(
            InvolvedMuscle("Glúteo Mayor", MuscleRole.PRIMARY, 1.0),
            InvolvedMuscle("Glúteo Medio", MuscleRole.SECONDARY, 0.5),
        )))
        val legacy = VolumeCalculator.calculateRoleSeparatedMuscleVolume(sessions, emptyList()).getValue("Glúteos")
        assertEquals(3.0, legacy.directSets, 0.0)
        assertEquals(1.5, legacy.secondarySets, 0.0)
        assertEquals(4.5, legacy.totalSets, 0.0)

        val canonical = VolumeCalculator.calculateVolume(sessions, emptyMap()).muscles.getValue("Glúteos")
        assertEquals(3.0, canonical.sets, 0.0)
        assertEquals(3.0, canonical.roles.directSets, 0.0)
        assertEquals(0.0, canonical.roles.secondarySets, 0.0)
    }

    @Test
    fun `las contabilidades son intencionalmente distintas entre truncado heredado y precision canonica`() {
        val sessions = listOf(session(listOf(InvolvedMuscle("Glúteo Mayor", MuscleRole.PRIMARY, 0.75))))
        val unified = VolumeCalculator.calculateUnifiedMuscleVolume(sessions, emptyList()).single()
        assertEquals(2.2, unified.displayVolume, 0.0)
        assertEquals(3, unified.sets)
        val legacyWeekly = VolumeCalculator.calculateCanonicalWeeklyMuscleVolumeForSessions(
            sessions, emptyList(), divisor = 2.0,
        ).single()
        assertEquals(1.1, legacyWeekly.weeklySets, 0.0)

        val canonical = VolumeCalculator.calculateVolume(sessions, emptyMap()).muscles.getValue("Glúteos")
        assertEquals(2.25, canonical.sets, 0.0)
        val canonicalWeekly = VolumeCalculator.calculateVolume(sessions, emptyMap(), divisor = 2.0)
            .muscles.getValue("Glúteos")
        assertEquals(1.125, canonicalWeekly.sets, 0.0)
    }
}
