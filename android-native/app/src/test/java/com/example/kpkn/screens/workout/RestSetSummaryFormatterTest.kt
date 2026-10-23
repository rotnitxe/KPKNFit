package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import org.junit.Assert.assertEquals
import org.junit.Test

class RestSetSummaryFormatterTest {
    @Test
    fun customQuantityUsesRecordedUnitAndKeepsTheRealLoadInBothRestSurfaces() {
        val completed = CompletedSet(
            id = "press-custom",
            weight = 5.0,
            reps = 12,
            recordedPayloadV3 = RecordedSetPayload(
                exerciseId = "press",
                unitMode = UnitModeV2.CUSTOM,
                completedReps = 12,
                externalLoad = 5.0,
            ),
            homologatedResultV3 = homologated(UnitModeV2.CUSTOM, actualValue = 12.5),
        )

        assertEquals("5 kg × 12,5 pasos", restLastSetFigureText(completed, customUnit = "pasos"))
        assertEquals("5 kg × 12,5 u", restLastSetFigureText(completed, customUnit = null))
    }

    @Test
    fun legacyRepsAndTimeKeepTheirExistingLabelsAndTimeSpacingCanMatchEachSurface() {
        val reps = CompletedSet(id = "reps", weight = 5.0, reps = 12)
        val time = CompletedSet(id = "time", weight = 5.0, reps = 9, timeSeconds = 45)

        assertEquals("5 kg × 12 reps", restLastSetFigureText(reps, customUnit = null))
        assertEquals("5 kg × 45 s", restLastSetFigureText(time, customUnit = null))
        assertEquals("5 kg × 45s", restLastSetFigureText(time, customUnit = null, timeSuffix = "s"))
    }

    private fun homologated(unitMode: UnitModeV2, actualValue: Double) = HomologatedPerformanceResult(
        contextKey = "context",
        globalKey = "global",
        loadMode = LoadModeV2.LOAD,
        unitMode = unitMode,
        actualValue = actualValue,
        metricType = "CRM",
        metricValue = actualValue,
        localPerformanceIndex = 50.0,
        globalPerformanceIndex = 50.0,
        contextPercentile = 50.0,
        globalPercentile = 50.0,
        contextEwma = actualValue,
        contextStdDev = 0.0,
        globalEwma = actualValue,
        globalStdDev = 0.0,
        isContextPr = false,
        isGlobalPr = false,
        historyColor = HistoryColorV2.NEUTRAL,
        difficultySignal = DifficultySignalV2.MATCHED,
        augeEquivalentLoad = 5.0,
        augeEquivalentReps = 12,
    )
}
