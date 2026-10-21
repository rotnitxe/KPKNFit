package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.RecoveryChannelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * D8 (2.6): los cortes de aviso se alinean con las zonas del ACWR.
 *
 * Antes WATCH 1,2 / ADJUST 1,4 / UNLOAD 1,6; después 1,3 / 1,5 / 1,7. Los comparadores no cambian
 * (WATCH ≥, ADJUST >, UNLOAD > con tendencia), la histéresis sigue en 0,10 y las zonas y la caída
 * máxima de un nivel por día conservan su contrato.
 */
class AcwrAdvisoryAlignmentTest {

    private val now = Instant.parse("2025-03-03T10:00:00Z").toEpochMilli()

    private fun level(
        acwr: Double?,
        previous: LoadAdvisoryLevel = LoadAdvisoryLevel.NONE,
        allowDrop: Boolean = true,
        extraWatch: Boolean = false,
        extraAdjust: Boolean = false,
        extraUnload: Boolean = false,
    ): LoadAdvisoryLevel = LoadAdvisoryEngine.levelFor(
        acwr = acwr,
        insufficient = false,
        extraWatch = extraWatch,
        extraAdjust = extraAdjust,
        extraUnload = extraUnload,
        previous = previous,
        allowDrop = allowDrop,
    )

    // ─── Valores de corte ────────────────────────────────────────────────────

    @Test
    fun thresholdsAlignWithTheZoneCutsWithoutTouchingZonesOrHysteresis() {
        assertEquals(1.3, AcwrThresholds.WATCH, 0.0)
        assertEquals(1.5, AcwrThresholds.ADJUST, 0.0)
        assertEquals(1.7, AcwrThresholds.UNLOAD, 0.0)
        assertEquals(0.8, AcwrThresholds.UNDER, 0.0)
        assertEquals(1.3, AcwrThresholds.CAUTION, 0.0)
        assertEquals(1.5, AcwrThresholds.DANGER, 0.0)
        assertEquals(0.10, AcwrThresholds.HYSTERESIS, 0.0)
    }

    @Test
    fun tableWithoutExtrasOrPreviousState() {
        // Tabla mínima del brief: 1,25 NONE (antes WATCH), 1,45 WATCH (antes ADJUST),
        // 1,65 con tendencia suficiente ADJUST (antes UNLOAD).
        assertEquals(LoadAdvisoryLevel.NONE, level(1.25))
        assertEquals(LoadAdvisoryLevel.WATCH, level(1.45))
        assertEquals(LoadAdvisoryLevel.ADJUST, level(1.65))

        // La misma fila por la ruta pública: una tendencia suficiente ya no convierte 1,65 en UNLOAD.
        assertEquals(LoadAdvisoryLevel.ADJUST, structureLevel(1.65, listOf(70, 68, 64)))
        assertEquals(LoadAdvisoryLevel.UNLOAD, structureLevel(1.71, listOf(70, 68, 64)))
    }

    @Test
    fun watchBorderIsInclusive() {
        assertEquals(LoadAdvisoryLevel.NONE, level(1.299))
        assertEquals(LoadAdvisoryLevel.WATCH, level(1.300))
        assertEquals(LoadAdvisoryLevel.WATCH, level(1.301))
    }

    @Test
    fun adjustBorderIsExclusive() {
        assertEquals(LoadAdvisoryLevel.WATCH, level(1.499))
        assertEquals(LoadAdvisoryLevel.WATCH, level(1.500))
        assertEquals(LoadAdvisoryLevel.ADJUST, level(1.501))
    }

    @Test
    fun unloadBorderNeedsTrendEvenAboveTheCut() {
        assertEquals(LoadAdvisoryLevel.ADJUST, level(1.699))
        assertEquals(LoadAdvisoryLevel.ADJUST, level(1.700))
        assertEquals(LoadAdvisoryLevel.ADJUST, level(1.701))
        // Sin tendencia suficiente (menos de 3 lecturas o subida), el corte no basta.
        assertEquals(LoadAdvisoryLevel.ADJUST, structureLevel(1.90, listOf(70, 71)))
        assertEquals(LoadAdvisoryLevel.ADJUST, structureLevel(1.90, listOf(70, 70, 70)))
        assertEquals(LoadAdvisoryLevel.ADJUST, structureLevel(1.90, listOf(70, 69, 68)))
    }

    @Test
    fun sameBordersWithPreviousStateApplyOneLevelAtMost() {
        val rows = listOf(
            // acwr, raw, con previo WATCH, con previo ADJUST, con previo UNLOAD
            listOf(1.299, LoadAdvisoryLevel.NONE, LoadAdvisoryLevel.WATCH, LoadAdvisoryLevel.WATCH, LoadAdvisoryLevel.ADJUST),
            listOf(1.300, LoadAdvisoryLevel.WATCH, LoadAdvisoryLevel.WATCH, LoadAdvisoryLevel.WATCH, LoadAdvisoryLevel.ADJUST),
            listOf(1.301, LoadAdvisoryLevel.WATCH, LoadAdvisoryLevel.WATCH, LoadAdvisoryLevel.WATCH, LoadAdvisoryLevel.ADJUST),
            listOf(1.499, LoadAdvisoryLevel.WATCH, LoadAdvisoryLevel.WATCH, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.ADJUST),
            listOf(1.500, LoadAdvisoryLevel.WATCH, LoadAdvisoryLevel.WATCH, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.ADJUST),
            listOf(1.501, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.ADJUST),
            listOf(1.699, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.UNLOAD),
            listOf(1.700, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.UNLOAD),
            listOf(1.701, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.ADJUST, LoadAdvisoryLevel.UNLOAD),
        )
        rows.forEach { row ->
            val acwr = row[0] as Double
            assertEquals("raw $acwr", row[1], level(acwr))
            assertEquals("previo WATCH $acwr", row[2], level(acwr, previous = LoadAdvisoryLevel.WATCH))
            assertEquals("previo ADJUST $acwr", row[3], level(acwr, previous = LoadAdvisoryLevel.ADJUST))
            assertEquals("previo UNLOAD $acwr", row[4], level(acwr, previous = LoadAdvisoryLevel.UNLOAD))
        }
    }

    // ─── Histéresis ──────────────────────────────────────────────────────────

    @Test
    fun hysteresisExitBandsMoveWithTheNewCuts() {
        // WATCH 1,3·0,90 = 1,17; ADJUST 1,5·0,90 = 1,35; UNLOAD 1,7·0,90 = 1,53.
        // Se usan valores interiores porque el producto en coma flotante puede quedar un ulp por encima.
        assertEquals(LoadAdvisoryLevel.WATCH, keep(LoadAdvisoryLevel.WATCH, 1.175))
        assertEquals(LoadAdvisoryLevel.NONE, keep(LoadAdvisoryLevel.WATCH, 1.169))
        assertEquals(LoadAdvisoryLevel.ADJUST, keep(LoadAdvisoryLevel.ADJUST, 1.355))
        assertEquals(LoadAdvisoryLevel.WATCH, keep(LoadAdvisoryLevel.ADJUST, 1.349))
        assertEquals(LoadAdvisoryLevel.UNLOAD, keep(LoadAdvisoryLevel.UNLOAD, 1.535))
        assertEquals(LoadAdvisoryLevel.ADJUST, keep(LoadAdvisoryLevel.UNLOAD, 1.529))
    }

    @Test
    fun aDayDropsAtMostOneLevel() {
        val jump = LoadAdvisoryEngine.applyHysteresis(
            raw = LoadAdvisoryLevel.WATCH,
            previous = LoadAdvisoryLevel.UNLOAD,
            allowDrop = true,
            acwr = 1.0,
        )
        assertEquals(LoadAdvisoryLevel.ADJUST, jump)
        val sameDay = LoadAdvisoryEngine.applyHysteresis(
            raw = LoadAdvisoryLevel.NONE,
            previous = LoadAdvisoryLevel.UNLOAD,
            allowDrop = false,
            acwr = 1.0,
        )
        assertEquals(LoadAdvisoryLevel.UNLOAD, sameDay)
    }

    @Test
    fun extrasStillPromoteWithoutAcwr() {
        assertEquals(LoadAdvisoryLevel.WATCH, level(null, extraWatch = true))
        assertEquals(LoadAdvisoryLevel.ADJUST, level(null, extraAdjust = true))
        assertEquals(LoadAdvisoryLevel.UNLOAD, level(null, extraUnload = true))
    }

    // ─── Recuento sobre el corpus del máster ─────────────────────────────────

    /**
     * D8 solo mueve avisos. Se recorre el corpus dorado con sus instantes explícitos y se comparan
     * los niveles que darían los cortes viejos y los nuevos. El máster no serializa avisos: esta es
     * la medición directa, no una fila del `expected.txt`.
     */
    @Test
    fun goldenCorpusAdvisoryShiftIsMeasuredWithoutClaimingTheMasterSerializesIt() {
        var instants = 0
        var changedInstants = 0
        var changedStructure = 0
        var changedSystem = 0
        val transitions = mutableMapOf<String, Int>()

        AugeGoldenCorpus.build(AugeGoldenCorpus.BASE_MS).forEach { caso ->
            caso.instants.forEach { instant ->
                instants++
                val axial = AxialLoadMonitor.evaluate(caso.history, AugeGoldenCorpus.EXERCISE_DB, instant)
                val systemic = SystemicLoadMonitor.evaluate(
                    caso.history,
                    AugeGoldenCorpus.EXERCISE_DB,
                    caso.settings,
                    nowMs = instant,
                )
                val structureOld = legacyLevel(
                    acwr = axial.acwr,
                    insufficient = axial.insufficientData,
                    extraWatch = axial.axialDaysIn4 >= 3,
                    extraAdjust = axial.axialDaysIn6 >= 4 || lowStarts(axial.startTrend, 60, 2),
                    extraUnload = legacyUnloadFromTrend(axial.acwr, axial.startTrend, 1.6),
                )
                val structureNew = LoadAdvisoryEngine.levelFor(
                    acwr = axial.acwr,
                    insufficient = axial.insufficientData,
                    extraWatch = axial.axialDaysIn4 >= 3,
                    extraAdjust = axial.axialDaysIn6 >= 4 || lowStarts(axial.startTrend, 60, 2),
                    extraUnload = unloadFromTrend(axial.acwr, axial.startTrend),
                    previous = LoadAdvisoryLevel.NONE,
                    allowDrop = true,
                )
                val systemOld = legacyLevel(
                    acwr = systemic.acwr,
                    insufficient = systemic.insufficientData,
                    extraWatch = systemic.sessionsIn7 >= 6,
                    extraAdjust = lowStarts(systemic.startTrend, 60, 2),
                    extraUnload = legacyUnloadFromTrend(systemic.acwr, systemic.startTrend, 1.6),
                )
                val systemNew = LoadAdvisoryEngine.levelFor(
                    acwr = systemic.acwr,
                    insufficient = systemic.insufficientData,
                    extraWatch = systemic.sessionsIn7 >= 6,
                    extraAdjust = lowStarts(systemic.startTrend, 60, 2),
                    extraUnload = unloadFromTrend(systemic.acwr, systemic.startTrend),
                    previous = LoadAdvisoryLevel.NONE,
                    allowDrop = true,
                )
                val structureChanged = structureOld != structureNew
                val systemChanged = systemOld != systemNew
                if (structureChanged) {
                    changedStructure++
                    transitions["STRUCTURE ${structureOld}->${structureNew}"] =
                        (transitions["STRUCTURE ${structureOld}->${structureNew}"] ?: 0) + 1
                }
                if (systemChanged) {
                    changedSystem++
                    transitions["SYSTEM ${systemOld}->${systemNew}"] =
                        (transitions["SYSTEM ${systemOld}->${systemNew}"] ?: 0) + 1
                }
                if (structureChanged || systemChanged) changedInstants++
            }
        }

        println(
            "[D8] instantes=$instants con_cambio=$changedInstants estructura=$changedStructure " +
                "sistema=$changedSystem transiciones=${transitions.toSortedMap()}",
        )
        assertTrue("El corpus debe tener instantes evaluables", instants > 0)
    }

    // ─── Utilidades de la medición ───────────────────────────────────────────

    private fun keep(previous: LoadAdvisoryLevel, acwr: Double): LoadAdvisoryLevel =
        LoadAdvisoryEngine.applyHysteresis(LoadAdvisoryLevel.NONE, previous, allowDrop = true, acwr = acwr)

    private fun structureLevel(acwr: Double, trend: List<Int>): LoadAdvisoryLevel {
        val report = AxialLoadReport(
            insufficientData = false,
            acwr = acwr,
            axialDaysIn7 = trend.size,
            axialDaysIn4 = 0,
            axialDaysIn6 = 0,
            consecutiveAxialDays = 0,
            heavySessions7 = 0,
            startTrend = trend,
        )
        val systemic = SystemicLoadReport(insufficientData = true, acwr = null, sessionsIn7 = 0, startTrend = emptyList())
        val result = LoadAdvisoryEngine.evaluate(report, systemic, AugeAdaptiveCache(), nowMs = now)
        return result.advisories.firstOrNull { it.channel == RecoveryChannelId.STRUCTURE }?.level
            ?: LoadAdvisoryLevel.NONE
    }

    private fun lowStarts(trend: List<Int>, threshold: Int, needed: Int): Boolean {
        if (trend.size < needed) return false
        return trend.takeLast(needed).all { it < threshold }
    }

    /** Copia literal de `LoadAdvisoryEngine.unloadFromTrend` con el corte de UNLOAD como parámetro. */
    private fun legacyUnloadFromTrend(acwr: Double?, trend: List<Int>, unload: Double): Boolean {
        if (lowStarts(trend, 50, 3)) return true
        if (acwr == null || acwr <= unload || trend.size < 3) return false
        val last = trend.takeLast(3)
        return last[0] >= last[1] && last[1] >= last[2] && last[0] - last[2] >= 5
    }

    private fun unloadFromTrend(acwr: Double?, trend: List<Int>): Boolean =
        legacyUnloadFromTrend(acwr, trend, AcwrThresholds.UNLOAD)

    /** Nivel crudo (sin histéresis) con los cortes viejos 1,2/1,4/1,6. */
    private fun legacyLevel(
        acwr: Double?,
        insufficient: Boolean,
        extraWatch: Boolean,
        extraAdjust: Boolean,
        extraUnload: Boolean,
    ): LoadAdvisoryLevel {
        if (insufficient && acwr == null && !extraWatch && !extraAdjust && !extraUnload) {
            return LoadAdvisoryLevel.NONE
        }
        return when {
            extraUnload -> LoadAdvisoryLevel.UNLOAD
            extraAdjust || (acwr != null && acwr > 1.4) -> LoadAdvisoryLevel.ADJUST
            extraWatch || (acwr != null && acwr >= 1.2) -> LoadAdvisoryLevel.WATCH
            else -> LoadAdvisoryLevel.NONE
        }
    }
}
