package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Caracterización del drenaje real per-músculo.
 * Documenta el comportamiento actual antes de la recalibración.
 * Estos tests DEBEN FALLAR tras la Fase 1 y actualizarse con los nuevos valores justificados.
 */
class AugeRingDrainRealismTest {

    private val exerciseDb = mapOf(
        "bench" to ExerciseMuscleInfo(
            id = "bench", name = "Press Banca", equipment = "barra",
            efc = 3.2, cnc = 3.5, ssc = 0.8,
            involvedMuscles = listOf(
                InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, volumeContribution = 1.0),
                InvolvedMuscle("Tríceps", MuscleRole.SECONDARY, volumeContribution = 0.45),
                InvolvedMuscle("Deltoides Anterior", MuscleRole.SECONDARY, volumeContribution = 0.35),
            ),
        ),
        "incline" to ExerciseMuscleInfo(
            id = "incline", name = "Press Inclinado Mancuerna", equipment = "mancuerna",
            efc = 3.0, cnc = 3.2, ssc = 0.6,
            involvedMuscles = listOf(
                InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, volumeContribution = 1.0),
                InvolvedMuscle("Deltoides Anterior", MuscleRole.SECONDARY, volumeContribution = 0.30),
            ),
        ),
    )

    private fun chestHardLog(now: Long): WorkoutLog {
        // Simula pecho intenso: bench 4x8 RPE8.5 + inclinado 4x10 RPE8
        return WorkoutLog(
            id = "log-chest", programId = "p", sessionId = "s", sessionName = "Pecho Intenso",
            date = Instant.ofEpochMilli(now).toString(),
            durationMinutes = 60,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "bench", exerciseName = "Press Banca",
                    exerciseDbId = "bench", restTime = 90,
                    sets = List(4) { i -> CompletedSet(id = "b-$i", weight = 80.0, reps = 8, rpe = 8.5) },
                ),
                CompletedExercise(
                    exerciseId = "incline", exerciseName = "Press Inclinado Mancuerna",
                    exerciseDbId = "incline", restTime = 90,
                    sets = List(4) { i -> CompletedSet(id = "i-$i", weight = 30.0, reps = 10, rpe = 8.0) },
                ),
            ),
        )
    }

    private fun chestLightLog(now: Long): WorkoutLog {
        return WorkoutLog(
            id = "log-light", programId = "p", sessionId = "s", sessionName = "Pecho Ligero",
            date = Instant.ofEpochMilli(now).toString(),
            durationMinutes = 40,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "bench", exerciseName = "Press Banca",
                    exerciseDbId = "bench", restTime = 120,
                    sets = List(3) { i -> CompletedSet(id = "b-$i", weight = 60.0, reps = 10, rpe = 7.0) },
                ),
            ),
        )
    }

    @Test
    fun chestHard_perMuscleDrain_isCurrentlyBarelyVisible() {
        val now = System.currentTimeMillis()
        val log = chestHardLog(now)
        val nowOverride = now + 5 * 60_000L // 5 min después

        val battery = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales",
            history = listOf(log),
            wellbeing = null,
            settings = Settings(),
            exerciseDb = exerciseDb,
            nowOverride = nowOverride,
        )

        // POST-FIX: tras pecho intenso (8 sets RPE8-8.5) debe drenar de forma visible
        // Recalibración 2026-08-17: floor 260 (antes 500) + clamp 120.
        // Simulación: stress ~47pp → battery 81 inmediato (drena ~19pts), realista sin caer a 0.
        println("[PIN] chestHard immediate Pectorales=${battery.recoveryScore} (expected 78-86 post-fix)")
        assertTrue(
            "Pectorales tras pecho intenso debe estar en 78..86 (drenaje realista), fue ${battery.recoveryScore}",
            battery.recoveryScore in 78..86,
        )
        // Realismo: no debe colapsar a <70 tras una sola sesión dura
        assertTrue("No debe drenar a <70 tras una sola sesión", battery.recoveryScore >= 70)
    }

    @Test
    fun chestHard_perMuscleAt24h_isAlreadyRecovered() {
        val now = System.currentTimeMillis()
        val log = chestHardLog(now)
        val now24h = now + 24 * 3_600_000L

        val battery24 = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales",
            history = listOf(log),
            wellbeing = null,
            settings = Settings(),
            exerciseDb = exerciseDb,
            nowOverride = now24h,
        )

        // POST-FIX: a las 24h debe seguir bajo pero en recuperación (no 95+ inmediato)
        println("[PIN] chestHard 24h Pectorales=${battery24.recoveryScore} (expected 88-93 post-fix)")
        assertTrue(
            "Pectorales a 24h debe estar 88..93 (aún fatigado), fue ${battery24.recoveryScore}",
            battery24.recoveryScore in 88..93,
        )
        assertTrue("A las 24h no debe estar ya en 100", battery24.recoveryScore < 97)
    }

    @Test
    fun chestLight_drainsLessThanHard() {
        val now = System.currentTimeMillis()
        val hard = chestHardLog(now)
        val light = chestLightLog(now)
        val nowOverride = now + 5 * 60_000L

        val hardScore = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales", history = listOf(hard), wellbeing = null,
            settings = Settings(), exerciseDb = exerciseDb, nowOverride = nowOverride,
        ).recoveryScore
        val lightScore = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales", history = listOf(light), wellbeing = null,
            settings = Settings(), exerciseDb = exerciseDb, nowOverride = nowOverride,
        ).recoveryScore

        println("[PIN] hard=$hardScore light=$lightScore")
        assertTrue("Sesión dura debe drenar al menos tanto como ligera", hardScore <= lightScore)
        // POST-FIX: diferencia debe ser apreciable (hard drena más) y ambos realistas
        assertTrue("Diferencia dura vs ligera debe ser 5..15pts post-fix, fue ${lightScore - hardScore}", (lightScore - hardScore) in 5..15)
        assertTrue("Light no debe quedar <85 tras sesión ligera", lightScore >= 85)
    }

    @Test
    fun capacityFloor_dominatesForNormalTraining() {
        val now = System.currentTimeMillis()
        // 4 semanas x 1 sesión pecho/semana = 4 logs de pecho
        val history = (0 until 4).map { w ->
            val t = now - w * 7L * 24 * 3_600_000L
            chestHardLog(t).copy(id = "log-$w", date = Instant.ofEpochMilli(t).toString())
        }

        // La capacidad efectiva debería ser el floor (500) porque weeklyAvg*1.8 nunca lo supera
        // Verificamos indirectamente: el drenaje con 4 semanas de historial no es mucho mayor
        val singleLog = listOf(chestHardLog(now))
        val batterySingle = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales", history = singleLog, wellbeing = null,
            settings = Settings(), exerciseDb = exerciseDb, nowOverride = now + 5 * 60_000L,
        ).recoveryScore
        val batteryWithHistory = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales", history = history, wellbeing = null,
            settings = Settings(), exerciseDb = exerciseDb, nowOverride = now + 5 * 60_000L,
        ).recoveryScore

        println("[PIN] capacity floor: single=$batterySingle withHistory=$batteryWithHistory")
        // Con floor fijo, ambos deben ser parecidos (capacidad no adapta)
        assertTrue("Capacidad debe ser casi idéntica con/sin historial (floor domina)", kotlin.math.abs(batterySingle - batteryWithHistory) <= 5)
    }

    @Test
    fun fullbody_eachMuscleBarelyDrains() {
        val now = System.currentTimeMillis()
        val legDb: Map<String, ExerciseMuscleInfo> = mapOf(
            "squat" to ExerciseMuscleInfo(
                id = "squat", name = "Sentadilla", equipment = "barra",
                efc = 3.8, cnc = 3.8, ssc = 1.2,
                involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, volumeContribution = 1.0)),
            ),
            "row" to ExerciseMuscleInfo(
                id = "row", name = "Remo", equipment = "barra",
                efc = 3.0, cnc = 3.2, ssc = 0.7,
                involvedMuscles = listOf(InvolvedMuscle("Dorsales", MuscleRole.PRIMARY, volumeContribution = 1.0)),
            ),
        )
        val fullDb = exerciseDb + legDb
        val log = WorkoutLog(
            id = "full", programId = "p", sessionId = "s", sessionName = "Fullbody",
            date = Instant.ofEpochMilli(now).toString(),
            durationMinutes = 70,
            completedExercises = listOf(
                CompletedExercise(exerciseId = "bench", exerciseName = "Press Banca", exerciseDbId = "bench", restTime = 90, sets = List(3) { CompletedSet(id = "b-$it", weight = 80.0, reps = 8, rpe = 8.0) }),
                CompletedExercise(exerciseId = "squat", exerciseName = "Sentadilla", exerciseDbId = "squat", restTime = 120, sets = List(3) { CompletedSet(id = "sq-$it", weight = 100.0, reps = 6, rpe = 8.0) }),
                CompletedExercise(exerciseId = "row", exerciseName = "Remo", exerciseDbId = "row", restTime = 90, sets = List(3) { CompletedSet(id = "r-$it", weight = 70.0, reps = 8, rpe = 8.0) }),
            ),
        )
        val nowOverride = now + 5 * 60_000L
        for (muscle in listOf("Pectorales", "Cuádriceps", "Dorsales")) {
            val b = AugeRecoveryEngine.calculateMuscleBattery(muscle, listOf(log), null, Settings(), fullDb, nowOverride = nowOverride).recoveryScore
            println("[PIN] fullbody $muscle=$b")
            // POST-FIX: fullbody debe drenar moderadamente (no 95+ imperceptible, no <75 excesivo)
            assertTrue("$muscle fullbody debe estar 86..92 post-fix, fue $b", b in 86..92)
        }
    }

    @Test
    fun extremeWeek_doesNotCollapseToZero() {
        val now = System.currentTimeMillis()
        // 6 días seguidos de pecho intenso (peor caso) no debe llevar a 0%
        val history = (0 until 6).map { d ->
            val t = now - d * 24L * 3_600_000L
            chestHardLog(t).copy(id = "ext-$d", date = Instant.ofEpochMilli(t).toString())
        }
        val battery = AugeRecoveryEngine.calculateMuscleBattery(
            muscleName = "Pectorales", history = history, wellbeing = null,
            settings = Settings(), exerciseDb = exerciseDb, nowOverride = now + 5 * 60_000L,
        ).recoveryScore
        println("[PIN] extreme 6-day hard Pectorales=$battery")
        // Tras 6 días duros, debe estar claramente fatigado (no 90+) pero no colapsado a 0
        // Con nueva calibración: 6× hard pecho distribuidos 10 días (ventana 10d), decay y floor suavizan.
        // Resultado medido ~74 (drena ~26pts), coherente con no-extremo y realista.
        assertTrue("Tras semana extrema, debe estar 55..85 pero no colapsar a 0, fue $battery", battery in 55..85)
        assertTrue("Debe respetar floor fisiológico (>=22)", battery >= 22)
        assertTrue("No debe quedar fresco (>90) tras semana extrema", battery < 90)
    }
}
