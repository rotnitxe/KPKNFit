package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.multisport.endurance.*
import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportTrackingDocument
import com.example.kpkn.domain.performance.lab.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TrainingHistoryProjectionTest {
    private val now = Instant.parse("2026-10-24T12:00:00Z").toEpochMilli()
    private fun log(id: String = "activity", documents: List<SportTrackingDocument> = emptyList()) = WorkoutLog(
        id=id, programId="program", sessionId="session", sessionName="Registro", date="2026-10-23T12:00:00Z",
        durationMinutes=17, sportDocuments=documents,
        completedExercises=if(documents.isEmpty()) listOf(CompletedExercise("exercise", "Remo", sets=listOf(CompletedSet("set",weight=30.0,reps=8)))) else emptyList())
    private fun mark(captured: Long? = null, protocol: String = "jump-v1") = PerformanceLabMark(
        id="mark", protocol=PerformanceLabProtocol(protocol,1,"Salto"), metric=PerformanceLabMetric.JUMP_HEIGHT,
        dimension="hands_on_hips",unit=PerformanceLabUnit.CENTIMETER,value=31.25,
        provenance=PerformanceLabProvenance.INSTRUMENT_MEASURED,
        context=PerformanceLabContext("platform-a","rubber","flight-time"),status=PerformanceLabTrialStatus.VALID,
        capturedAtEpochMillis=captured)
    private fun doc(mark: PerformanceLabMark = mark()) = SportTrackingDocument("document",PerformanceLabEngine.id,1,"JUMP","RESULT",
        Json.encodeToJsonElement(PerformanceLabPayload(marks=listOf(mark))).jsonObject)

    @Test fun roundedMinutesAreNotExpandedIntoSecondsAndSetEffortIsNotSessionEffort() {
        val source=log().copy(completedExercises=listOf(CompletedExercise("exercise","Remo",sets=listOf(CompletedSet("set",weight=30.0,reps=8,rpe=9.0)))))
        val facts=TrainingHistoryProjection.facts(listOf(source),now)
        assertEquals(17.0,facts.single { it.unit=="min" }.value,0.0)
        assertFalse(facts.any { it.metric=="Duración de sesión" || it.metric=="Esfuerzo global declarado" || it.metric=="Esfuerzo × duración" })
        assertFalse(facts.any { it.metric.contains("RPE") }) // Legacy defaults cannot certify an explicit response.
    }
    @Test fun explicitSessionZeroIsRetainedAndDurationProductUsesExactSeconds() {
        val facts=TrainingHistoryProjection.facts(listOf(log().copy(actualDurationSeconds=61,actualDurationSource="stopwatch",
            sessionEffortRpe=0.0,sessionEffortProtocol="session_rpe_0_10_v1")),now)
        assertEquals(61.0,facts.single { it.metric=="Duración de sesión" }.value,0.0)
        assertEquals(0.0,facts.single { it.metric=="Esfuerzo global declarado" }.value,0.0)
        assertEquals(0.0,facts.single { it.metric=="Esfuerzo × duración" }.value,0.0)
        assertFalse(facts.any { it.unit=="min" })
    }
    @Test fun performanceMarksRetainPrecisionProtocolSourceAndContext() {
        val facts=TrainingHistoryProjection.facts(listOf(log(documents=listOf(doc()))),now)
        val point=facts.single { it.metric=="JUMP_HEIGHT" }
        assertEquals(31.25,point.value,0.0);assertEquals("CENTIMETER",point.unit)
        assertEquals("jump-v1@1",point.protocol);assertEquals("platform-a",point.context["instrument"])
        assertEquals(FactOrigin.OBSERVED,point.origin)
    }
    @Test fun futureMarksUnsupportedEnginesAndConflictingOwnersAreNotSummarized() {
        assertTrue(TrainingHistoryProjection.facts(listOf(log(documents=listOf(doc(mark(now+1))))),now).none { it.metric=="JUMP_HEIGHT" })
        val unsupported=doc().copy(engineId="unregistered")
        assertTrue(TrainingHistoryProjection.facts(listOf(log(documents=listOf(unsupported))),now).none { it.sourceId.startsWith("document:") })
        val first=log(documents=listOf(doc()));val second=first.copy(id="other-owner")
        val visible=TrainingInsightsEngine.visible(TrainingHistoryProjection.facts(listOf(first,second),now),now)
        assertTrue(visible.none { it.metric=="JUMP_HEIGHT" })
    }
    @Test fun mixedActivityDoesNotAcquireAnInventedStrengthDiscipline() {
        val documents=listOf(doc(), doc().copy(id="other",sportId="RUNNING",engineId="unregistered"))
        assertTrue(TrainingHistoryProjection.facts(listOf(log(documents=documents)),now).none { it.sport==SportId.STRENGTH })
    }
    @Test fun skippedAndWarmupSetsDoNotAppearAsPerformedWorkingLoad() {
        val source=log().copy(completedExercises=listOf(CompletedExercise("exercise","Remo",sets=listOf(
            CompletedSet("skip",weight=500.0,reps=20,skipped=true),CompletedSet("warm",weight=1.0,reps=3,isWarmup=true)))))
        assertFalse(TrainingHistoryProjection.facts(listOf(source),now).any { it.sourceId.contains(":set:") })
    }
    @Test fun enduranceSamplesKeepExplicitParentUnitsAndDoNotBecomeAggregatedPower() {
        val series = EnduranceMeasurementSeries("meter",EnduranceSeriesKind.POWER,EnduranceUnit.WATTS,
            EnduranceDataSource.IMPORTED_DEVICE,instrumentId="meter-a",
            coverage=EnduranceSampleCoverage(0,1000,1000,expectedSampleIntervalMillis=1000),
            samples=listOf(EnduranceSample(0,0.0),EnduranceSample(1000,153.125)))
        fun source(quality: EnduranceRecordQuality): WorkoutLog {
            val payload=EnduranceSportPayload(SportId.CYCLING,observation=EnduranceSportObservation(
                segmentKind=EnduranceSegmentKind.CYCLING,series=series,quality=quality))
            return log(documents=listOf(SportTrackingDocument("power-document",EnduranceSportEngine.cycling.id,1,
                "CYCLING","OBSERVATION",payload.toJsonObject())))
        }
        val points=TrainingHistoryProjection.facts(listOf(source(EnduranceRecordQuality.COMPLETE)),now).filter { it.unit=="WATTS" }
        assertEquals(listOf(0.0,153.125),points.map { it.value })
        assertTrue(points.all { it.origin==FactOrigin.HISTORICAL_AMBIGUOUS && it.method==null })
        assertFalse(TrainingHistoryProjection.facts(listOf(source(EnduranceRecordQuality.INVALIDATED)),now).any { it.unit=="WATTS" })
    }

    @Test fun allDeclaredDisciplinesUseTheSameNativeActivityWithoutInventingMeasurements() {
        val sources = SportId.entries.map { sport -> log(id=sport.name).copy(recordedSportId=sport.name,completedExercises=emptyList()) }
        val facts = TrainingHistoryProjection.facts(sources,now)
        assertEquals(SportId.entries.toSet(),facts.map { it.sport }.toSet())
        assertEquals(sources.size,facts.size)
        assertTrue(facts.all { it.unit=="min" && it.value==17.0 && it.origin==FactOrigin.HISTORICAL_AMBIGUOUS })
        assertEquals(sources.size,TrainingInsightsEngine.coverage(facts,now).activities)
    }

}
