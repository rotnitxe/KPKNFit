package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.data.models.ArticularBatteryState
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.RecoveryStatus
import com.example.kpkn.data.models.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Paquete 1.5b (F-25, resto): un NaN en el caché o en una entrada no debe lanzar al redondear una batería.
 * `roundToInt()` lanza con NaN (`toInt()` daba 0). Reloj explícito y zona fija: ninguna prueba toca el reloj real.
 */
class BatteryPercentNanSafeTest {
    private val origin = Instant.parse("2026-09-12T18:00:00Z").toEpochMilli()
    private val settings = Settings()
    private fun cacheHeredado(field: String) = AugeAdaptiveCacheMigration.migrate(kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<AugeAdaptiveCache>("""{"schemaVersion":3,"$field":"NaN"}"""), 3)

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private fun freshPillars(): Map<String, MuscleRecoveryStatus> =
        AugeRecoveryEngine.pillarMuscles.associateWith { muscle ->
            MuscleRecoveryStatus(
                muscleName = muscle, recoveryScore = 100, hoursToRecovery = 0, hoursSinceLastSession = -1,
                effectiveSets = 0, status = RecoveryStatus.FRESH,
            )
        }

    /** Anillos globales sin historial: solo cuentan el caché y, si lo hay, el ajuste manual. */
    private fun globales(cache: AugeAdaptiveCache, wellbeing: DailyWellbeingLog? = null) =
        AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(), wellbeing = wellbeing, settings = settings, adaptiveCache = cache,
            precomputedMuscles = freshPillars(), nowOverrideMs = origin,
        )

    /** Ajuste manual de músculos al 80 % con ancla explícita (sin zona del sistema). */
    private fun ajusteManual80() = DailyWellbeingLog(
        id = "manual", date = "2026-09-12", manualMuscularBattery = 80, manualBatteryAnchorMs = origin,
    )

    // ─── (a) NaN en el caché: no lanza y devuelve enteros en 0..100 ─────────

    @Test
    fun cnsLearningDeltaNaN_noLanzaYDaUnEnteroAcotado() {
        val g = globales(cacheHeredado("cnsLearningDelta"))
        assertTrue("Energía fuera de 0..100: ${g.cnc}", g.cnc in 0..100)
    }

    @Test
    fun spinalLearningDeltaNaN_noLanzaYDaUnEnteroAcotado() {
        val g = globales(cacheHeredado("spinalLearningDelta"))
        assertTrue("Columna fuera de 0..100: ${g.spinal}", g.spinal in 0..100)
    }

    @Test
    fun muscleDeltaNaN_noLanzaYDaUnEnteroAcotado() {
        val g = globales(cacheHeredado("muscleDeltas"))
        assertTrue("Músculos fuera de 0..100: ${g.muscular}", g.muscular in 0..100)
    }

    @Test
    fun tauNaNConAjusteManualMuscular_noLanzaYDaUnEnteroAcotado() {
        val g = globales(
            AugeAdaptiveCache(personalizedRecoveryHours = mapOf("pectorales" to Double.NaN)),
            ajusteManual80(),
        )
        assertTrue("Músculos fuera de 0..100: ${g.muscular}", g.muscular in 0..100)
    }

    @Test
    fun control_conTauFinitoElAjusteManualSeLee80() {
        val g = globales(
            AugeAdaptiveCache(personalizedRecoveryHours = mapOf("pectorales" to 48.0)),
            ajusteManual80(),
        )
        assertEquals("Control: con τ finito el ajuste manual del 80 % se lee 80", 80, g.muscular)
    }

    // ─── (b) batteryPercent: NaN ⇒ 0, acotado y mitad hacia arriba ─────────

    @Test
    fun batteryPercent_casosDeBorde() {
        assertEquals("NaN", 0, AugeUtils.batteryPercent(Double.NaN))
        assertEquals("+∞", 100, AugeUtils.batteryPercent(Double.POSITIVE_INFINITY))
        assertEquals("−∞", 0, AugeUtils.batteryPercent(Double.NEGATIVE_INFINITY))
        assertEquals("99,5 ⇒ 100", 100, AugeUtils.batteryPercent(99.5))
        assertEquals("99,49 ⇒ 99", 99, AugeUtils.batteryPercent(99.49))
        assertEquals("0,5 ⇒ 1", 1, AugeUtils.batteryPercent(0.5))
        assertEquals("0,49 ⇒ 0", 0, AugeUtils.batteryPercent(0.49))
        assertEquals("−3 ⇒ 0", 0, AugeUtils.batteryPercent(-3.0))
        assertEquals("140 ⇒ 100", 100, AugeUtils.batteryPercent(140.0))
    }

    @Test
    fun batteryPercent_esMonotonoEnLaMallaDeCeroACien() {
        var previo = -1
        for (n in 0..2000) { // 0,00 a 100,00 con paso 0,05
            val valor = n * 0.05
            val obtenido = AugeUtils.batteryPercent(valor)
            assertTrue("fuera de 0..100 en $valor: $obtenido", obtenido in 0..100)
            assertTrue("no monótono en $valor: $obtenido tras $previo", obtenido >= previo)
            previo = obtenido
        }
    }

    // ─── (d) TTC: promedio articular y combinada redondean la mitad hacia arriba ─

    @Test
    fun combinadaEstructuralDeMusculo99SinArticularDaCien() {
        // Pectorales solo tiene el hombro y, sin dato, cuenta 100: (99 + 100) / 2 = 99,5 ⇒ 100 (antes 99).
        val r = AugeTtcEngine.getStructuralReadinessForMuscle("Pectorales", 99, emptyMap())
        assertEquals("Combinada", 100, r.combinedBattery)
    }

    @Test
    fun promedioArticularDe53Y54DaCincuentaYCuatro() {
        // Bíceps: hombro 53 y codo 54 ⇒ promedio 53,5 ⇒ 54 (antes 53). Con músculo 53, la combinada también da 54.
        val r = AugeTtcEngine.getStructuralReadinessForMuscle(
            muscleName = "Bíceps",
            muscleBattery = 53,
            articularBatteries = mapOf(
                ArticularBattery.SHOULDER to ArticularBatteryState(recoveryScore = 53),
                ArticularBattery.ELBOW to ArticularBatteryState(recoveryScore = 54),
            ),
        )
        assertEquals("Promedio articular", 54, r.articularBattery)
        assertEquals("Combinada", 54, r.combinedBattery)
    }
}
