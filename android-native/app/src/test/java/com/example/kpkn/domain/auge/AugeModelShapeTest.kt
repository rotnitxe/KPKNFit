package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.data.models.ArticularBatteryState
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.channelScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Forma del modelo de 2.6: D1 (el global de Músculos agrega los 13 pilares sin el coeficiente articular) y D2
 * (0,85·media + 0,15·cuartil inferior, sin cambios). Sin reloj real: instante absoluto y zona UTC.
 *
 * D1 solo retira el coeficiente del global: los chips musculares, la Energía y la Columna cruda siguen siendo los
 * mismos, y la Columna mostrada y el componente articular de readiness conservan su protección.
 */
class AugeModelShapeTest {

    private companion object {
        const val NOW = 1_767_225_600_000L // 2026-01-01T10:00:00Z, fijo
        const val HOUR = 3_600_000L
        val ZONE: ZoneId = ZoneId.of("UTC")
        val ARTICULAR_BAJA: Double = 50.0
    }

    private val db = mapOf(
        "bench" to ExerciseMuscleInfo(
            id = "bench", name = "Press Banca", equipment = "barra",
            efc = 3.2, cnc = 3.5, ssc = 0.35, axialLoadFactor = 0.0,
            involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, volumeContribution = 1.0)),
        ),
        "squat" to ExerciseMuscleInfo(
            id = "squat", name = "Sentadilla", equipment = "barra",
            efc = 4.2, cnc = 4.5, ssc = 1.0, axialLoadFactor = 1.0,
            involvedMuscles = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, volumeContribution = 1.0)),
        ),
    )

    // ─── Tabla explicativa del coeficiente (aritmética de D1) ────────────────

    @Test
    fun coefficientTableShowsWhatD1Removes() {
        // Pilar 90 con articular relacionada 50: el coeficiente viejo 0,6 + 0,4·50/100 = 0,8 deja 72.
        assertEquals(72, pilarConCoeficiente(90, 50))
        // Articular 100: coeficiente 1,0, el pilar entra igual (90 → 90).
        assertEquals(90, pilarConCoeficiente(90, 100))
        // El global nuevo no aplica coeficiente: el pilar de 90 entra como 90.
        assertEquals(90, pilarSinCoeficiente(90))
        assertFalse(AugeParameters.MUSCULAR_GLOBAL_APPLIES_ARTICULAR_COEF)
    }

    // ─── D2 conservada: 13 pilares, 3 en el cuartil, 0,85/0,15 ───────────────

    @Test
    fun d2WeightsAndBottomQuartileAreUnchanged() {
        // Trece pilares distintos, todos por encima de 30 (la desaceleración no actúa).
        val puntuaciones = listOf(100, 96, 93, 90, 88, 86, 84, 82, 80, 78, 76, 74, 72)
        val pilares = AugeParameters.PILLARS.mapIndexed { i, nombre -> nombre to puntuaciones[i] }.toMap()
        val detalle = redGlobal(pilares, ARTICULAR_BAJA)

        val ordenadas = puntuaciones.sorted()
        val cuartil = ordenadas.take(3)
        assertEquals(3, maxOf(1, (ordenadas.size * AugeParameters.MUSCULAR_GLOBAL_BOTTOM_FRACTION).toInt()))
        assertEquals(listOf(72, 74, 76), cuartil)

        val media = puntuaciones.average()
        val inferior = cuartil.average()
        val esperado = AugeUtils.batteryPercent(
            media * AugeParameters.MUSCULAR_GLOBAL_MEAN_WEIGHT +
                inferior * AugeParameters.MUSCULAR_GLOBAL_BOTTOM_WEIGHT,
        )
        assertEquals(83, esperado)
        // Antes = después: D2 no cambia los pesos; el código usa los mismos 0,85/0,15.
        assertEquals(esperado, detalle.batteries.muscular)
        assertEquals(13, AugeParameters.PILLARS.size)
        assertEquals(AugeParameters.PILLARS, pilares.keys.toList())
    }

    // ─── Caso del motor con articular baja ───────────────────────────────────

    @Test
    fun lowArticularOnlyRaisesTheMuscularGlobal() {
        val settings = Settings(athleteType = AthleteType.ENTHUSIAST)
        val historial = listOf(sesionLigera())
        val detalles = AugeRecoveryEngine.getPerMuscleBatteryDetails(
            history = historial, wellbeing = null, settings = settings, exerciseDb = db,
            nowOverrideMs = NOW, zone = ZONE,
        )
        val chips = detalles.mapValues { it.value.status }
        val articular = ArticularBattery.entries.associateWith {
            ArticularBatteryState(recoveryScore = 50, status = com.example.kpkn.data.models.ArticularStatus.EXHAUSTED)
        }

        val global = AugeRecoveryEngine.calculateGlobalBatteriesDetail(
            history = historial, wellbeing = null, settings = settings, exerciseDb = db,
            precomputedMuscles = chips, articularBatteries = articular,
            precomputedMuscleDetails = detalles, nowOverrideMs = NOW, zone = ZONE,
        )

        // El global es exactamente la agregación D2 de los 13 chips, sin coeficiente articular.
        val chipsInt = chips.mapValues { it.value.recoveryScore }
        assertEquals(agregar(chipsInt), global.batteries.muscular)
        // Y el coeficiente viejo habría dejado un global menor.
        val antes = agregar(chipsInt.mapValues { (_, v) -> pilarConCoeficiente(v, 50) })
        assertTrue("el global debe subir al retirar el coeficiente: antes=$antes", global.batteries.muscular > antes)
        assertEquals(global.batteries.muscular, agregar(chipsInt))

        // Chips, Energía y Columna cruda no cambian: son el mismo cálculo de siempre.
        assertEquals(chips, detalles.mapValues { it.value.status })
        assertEquals(
            AugeRecoveryEngine.calculateSystemicFatigue(
                historial, null, settings, db, nowOverride = NOW, zone = ZONE,
            ).first,
            global.batteries.cnc,
        )
        assertEquals(
            AugeRecoveryEngine.calculateSpinalBattery(
                historial, null, settings, db, nowOverride = NOW, zone = ZONE,
            ),
            global.batteries.spinal,
        )

        // La Columna mostrada conserva su blend (axial + articular + guardia): la articular baja sigue pesando.
        val panel = AugeRecoveryEngine.calculateRecoveryDashboardDetail(
            batteries = global.batteries, perMuscle = chips, articularBatteries = articular, wellbeing = null,
        ).dashboard
        val columna = panel.channelScore(RecoveryChannelId.STRUCTURE)
        assertEquals(
            AugeRecoveryEngine.structureRingScore(
                spinal = global.batteries.spinal,
                articularFloor = 50,
                guardMuscleScores = AugeParameters.SPINE_GUARD_MUSCLES.map { chips.getValue(it).recoveryScore },
            ),
            columna,
        )
        assertTrue("la articular baja debe seguir limitando la Columna", columna < 100)

        // Readiness: el canal Músculos y el global se mueven; Energía y Columna quedan igual que antes.
        val panelAntes = AugeRecoveryEngine.calculateRecoveryDashboardDetail(
            batteries = GlobalBatteries(muscular = antes, cnc = global.batteries.cnc, spinal = global.batteries.spinal),
            perMuscle = chips, articularBatteries = articular, wellbeing = null,
        ).dashboard
        assertEquals(panelAntes.channelScore(RecoveryChannelId.SYSTEM), panel.channelScore(RecoveryChannelId.SYSTEM))
        assertEquals(panelAntes.channelScore(RecoveryChannelId.STRUCTURE), columna)
        assertEquals(antes, panelAntes.channelScore(RecoveryChannelId.MUSCULAR))
        assertTrue("el readiness debe moverse con el global muscular", panelAntes.overallScore != panel.overallScore)
        assertEquals(
            AugeUtils.batteryPercent(
                panel.channelScore(RecoveryChannelId.SYSTEM) * 0.40 +
                    panel.channelScore(RecoveryChannelId.MUSCULAR) * 0.35 +
                    panel.channelScore(RecoveryChannelId.STRUCTURE) * 0.25,
            ),
            panel.overallScore,
        )
        assertEquals(panel.overallScore, AugeRecoveryEngine.calculateDailyReadiness(panel, null).score)
    }

    // ─── Utilidades de la prueba ─────────────────────────────────────────────

    private fun redGlobal(pilares: Map<String, Int>, articular: Double) = run {
        val chips = pilares.mapValues { (nombre, score) ->
            MuscleRecoveryStatus(nombre, score, 0, 0, 0, com.example.kpkn.data.models.RecoveryStatus.OPTIMAL)
        }
        AugeRecoveryEngine.calculateGlobalBatteriesDetail(
            history = emptyList(), wellbeing = null, settings = Settings(), exerciseDb = emptyMap(),
            precomputedMuscles = chips,
            articularBatteries = ArticularBattery.entries.associateWith {
                ArticularBatteryState(recoveryScore = articular.toInt())
            },
            nowOverrideMs = NOW, zone = ZONE,
        )
    }

    private fun pilarConCoeficiente(pilar: Int, articular: Int): Int =
        (pilar * (0.6 + 0.4 * (articular / 100.0))).roundToInt()

    private fun pilarSinCoeficiente(pilar: Int): Int = pilar

    /** D2 sobre los 13 pilares: 0,85·media + 0,15·los 3 peores. */
    private fun agregar(pilares: Map<String, Int>): Int {
        val ordenadas = AugeParameters.PILLARS.map { pilares.getValue(it) }.sorted()
        val cuartil = ordenadas.take(maxOf(1, (ordenadas.size * 0.25).toInt()))
        return AugeUtils.batteryPercent(ordenadas.average() * 0.85 + cuartil.average() * 0.15)
    }

    private fun sesionLigera(): WorkoutLog = WorkoutLog(
        id = "d1-ligera", programId = "p", sessionId = "s", sessionName = "Ligera",
        date = Instant.ofEpochMilli(NOW - 12 * HOUR).toString(), durationMinutes = 45,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "bench", exerciseName = "Press Banca", exerciseDbId = "bench", restTime = 120,
                sets = List(4) { i -> CompletedSet(id = "b-$i", weight = 60.0, reps = 10, rpe = 7.0) },
            ),
            CompletedExercise(
                exerciseId = "squat", exerciseName = "Sentadilla", exerciseDbId = "squat", restTime = 180,
                sets = List(4) { i -> CompletedSet(id = "sq-$i", weight = 80.0, reps = 8, rpe = 7.0) },
            ),
        ),
    )
}
