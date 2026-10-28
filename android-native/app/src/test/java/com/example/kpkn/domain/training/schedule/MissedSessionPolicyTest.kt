package com.example.kpkn.domain.training.schedule

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.training.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class MissedSessionPolicyTest {
    companion object { @JvmStatic @BeforeClass fun install() = CatalogCompositionTestSupport.install() }
    private val metadata get() = CatalogCompositionTestSupport.metadata
    private fun fixture(): Program {
        fun week(id: String) = ProgramWeek(id,id,sessions = listOf(Session("s-$id",id,exercises = listOf(
            Exercise("e-$id","Banca",sets = listOf(ExerciseSet("set-$id",targetReps = 5)))))))
        return Program("p","Plan",structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(Macrocycle("macro","Macro",listOf(
                Block("b1","Bloque 1",goal = BlockGoal.ACCUMULATION,mesocycles = listOf(Mesocycle("m1","Meso",weeks = listOf(week("w1"),week("w2"))))),
                Block("b2","Bloque 2",goal = BlockGoal.INTENSIFICATION,mesocycles = listOf(Mesocycle("m2","Meso",weeks = listOf(week("w3")))))
            ))),runState = ProgramRunState("run",weekId = "w2",weekInstanceId = "w2",blockId = "b1"))
    }
    private fun log(week: String) = WorkoutLog("log-$week","p","s-$week",week,"2026-10-01T10:00:00Z",30,
        weekId = week,weekInstanceId = week,cycleNumber = 1,programRunId = "run")
    @Test fun incompleteBlockRequestsActualMissingWeekAndExplicitSkipReleasesIt() {
        val p = fixture()
        val logs = listOf(log("w2"))
        val last = ProgramHierarchyIndex(p).locateWeek("w2")!!.week.sessions.single()
        val held = ProgramProgressEngine.advanceAfterSessionComplete(p,null,last,"w2",logs,compositionMetadata = metadata,nowMs = 10)
        assertFalse(held.advancedWeek)
        assertEquals("w1",held.program.pendingMissedSessions?.weekId)
        assertEquals(listOf("s-w1"),held.program.pendingMissedSessions?.sessionIds)
        val skipped = ProgramProgressEngine.resolveMissedSessions(held.program,MissedSessionChoice.SKIP,null,logs,nowMs = 20)
        assertEquals(1,skipped.skippedSessionKeys.size)
        assertNull(skipped.pendingMissedSessions)
        assertEquals("MISSED_SESSION",skipped.programDecisions.last().source)
        val advanced = ProgramProgressEngine.advanceAfterSessionComplete(skipped,null,last,"w2",logs,compositionMetadata = metadata)
        assertTrue(advanced.advancedWeek)
        assertEquals("w3",advanced.program.runState?.weekId)
        assertEquals(1,logs.size)
    }
    @Test fun keepingWeekCreatesNoCompletionOrFakeLogAndStaleRunCannotResolve() {
        val p = fixture().copy(pendingMissedSessions = MissedSessionsRequest("w1","w1",listOf("s-w1"),programRunId = "run"))
        val kept = ProgramProgressEngine.resolveMissedSessions(p,MissedSessionChoice.DO_THIS_WEEK,metadata,listOf(log("w2")))
        assertTrue(kept.skippedSessionKeys.isEmpty())
        assertNull(kept.pendingMissedSessions)
        assertFalse(BlockTransitionEngine.areBlockSessionsComplete(kept,kept.macrocycles[0].blocks[0],listOf(log("w2")),"run"))
        val stale = p.copy(runState = p.runState!!.copy(runId = "another"))
        assertEquals(stale,ProgramProgressEngine.resolveMissedSessions(stale,MissedSessionChoice.SKIP,metadata,emptyList()))
        assertEquals(MissedSessionChoice.DO_THIS_WEEK,MissedSessionPolicy.defaultChoice(p.copy(sourceProtocolId = "author")))
    }
    @Test fun staleIdsAreClearedButReportedAsAnUnappliedResponse() {
        val p = fixture().copy(pendingMissedSessions = MissedSessionsRequest("w1","w1",listOf("old-session"),programRunId = "run"))
        val result = ProgramProgressEngine.resolveMissedSessions(p,MissedSessionChoice.SKIP,metadata,emptyList(),nowMs = 7)
        assertNull(result.pendingMissedSessions)
        assertTrue(result.skippedSessionKeys.isEmpty())
        val decision = result.programDecisions.single()
        assertEquals(UnappliedResponse.TITLE,decision.title)
        assertEquals("MISSED_SESSION",decision.source)
        assertEquals("w1",decision.weekId)
        assertTrue(decision.detail.contains("cambiaron"))
        assertEquals(decision.detail,UnappliedResponse.newReason(p,result))
        // Cuando ya no falta nada, el motivo es otro.
        val done = ProgramProgressEngine.resolveMissedSessions(p.copy(pendingMissedSessions = MissedSessionsRequest("w1","w1",listOf("s-w1"),programRunId = "run")),
            MissedSessionChoice.SKIP,metadata,listOf(log("w1")),nowMs = 8)
        assertNull(done.pendingMissedSessions)
        assertTrue(done.programDecisions.single().detail.contains("Ya no faltan"))
    }
    @Test fun softReturnWithoutCatalogKeepsTheRequestAndSaysWhy() {
        val p = ownRecipe().let { it.copy(pendingMissedSessions = MissedSessionsRequest(
            ProgramHierarchyIndex(it).orderedWeeks().single().week.id,it.runState!!.weekInstanceId!!,
            ProgramHierarchyIndex(it).orderedWeeks().single().week.sessions.map { s -> s.id },programRunId = "run")) }
        val result = ProgramProgressEngine.resolveMissedSessions(p,MissedSessionChoice.SOFT_RETURN,null,emptyList(),nowMs = 9)
        assertEquals(p.pendingMissedSessions,result.pendingMissedSessions)
        assertEquals(p.macrocycles,result.macrocycles)
        assertEquals(UnappliedResponse.TITLE,result.programDecisions.last().title)
        assertTrue(result.programDecisions.last().detail.contains("catálogo"))
    }
    @Test fun doingTheMissingEarlierWeekAfterAnExplicitChoiceReconcilesWithRealCanonicalLog() {
        val p = fixture()
        val oldLogs = listOf(log("w2"))
        val last = ProgramHierarchyIndex(p).locateWeek("w2")!!.week.sessions.single()
        val held = ProgramProgressEngine.advanceAfterSessionComplete(p,null,last,"w2",oldLogs,compositionMetadata = metadata).program
        val kept = ProgramProgressEngine.resolveMissedSessions(held,MissedSessionChoice.DO_THIS_WEEK,metadata,oldLogs)
        val missing = ProgramHierarchyIndex(p).locateWeek("w1")!!.week.sessions.single()
        val realLogs = oldLogs + log("w1")
        val advanced = ProgramProgressEngine.advanceAfterSessionComplete(kept,null,missing,"w1",realLogs,compositionMetadata = metadata)
        assertTrue(advanced.advancedWeek)
        assertEquals("w3",advanced.program.runState?.weekId)
        assertEquals(2,realLogs.size)
        assertTrue(advanced.program.skippedSessionKeys.isEmpty())
        val withoutChoice = ProgramProgressEngine.advanceAfterSessionComplete(p,null,missing,"w1",realLogs,compositionMetadata = metadata)
        assertFalse(withoutChoice.advancedWeek)
        assertEquals("w2",withoutChoice.program.runState?.weekId)
    }
    @Test fun skipIdentityIsUnambiguousAndCannotCompleteAnotherRunOrCycle() {
        val key = MissedSessionPolicy.skippedKey("r:1",1,"inst_c1_w1","s:w")
        assertEquals(key,MissedSessionPolicy.skippedKey("r:1",1,"w1","s:w"))
        assertNotEquals(key,MissedSessionPolicy.skippedKey("r",1,"1:w1","s:w"))
        val week = ProgramWeek("w1","W",sessions = listOf(Session("s:w","S")))
        assertTrue(ProgramProgressEngine.isWeekInstanceComplete(week,emptyList(),"p","w1",1,"r:1",listOf(key)))
        assertFalse(ProgramProgressEngine.isWeekInstanceComplete(week,emptyList(),"p","w1",2,"r:1",listOf(key)))
        assertFalse(ProgramProgressEngine.isWeekInstanceComplete(week,emptyList(),"p","w1",1,"r:2",listOf(key)))
    }
    private fun ownRecipe(): Program {
        val recipe = TrainingPlanRecipe("return",listOf(WeekRecipe(1,0,days = listOf(1,3).map { day -> DayRecipe("Día $day",weekday = day,
            slots = listOf(SlotRecipe("bench",SlotRole.T3_ACCESSORY,LiftRef(CatalogIds.BP),List(4) { SetRecipe(reps = 8,rir = 2,percent = 70.0) },90))) })))
        var n = 0
        val p = PlanMaterializer.materialize(Program("p","Propio"),recipe,metadata,object : IdProvider { override fun newId() = "id-${++n}" },strict = false)
        val week = ProgramHierarchyIndex(p).orderedWeeks().single().week
        return p.copy(runState = ProgramRunState("run",weekId = week.id,weekInstanceId = ProgramProgressEngine.instanceIdFor(1,week.id)))
    }
    @Test fun softReturnNeedsExplicitOwnRecipeChoicePreservesStartedSessionAndIsIdempotent() {
        val p = ownRecipe()
        val week = ProgramHierarchyIndex(p).orderedWeeks().single().week
        val protected = week.sessions.first().id
        val changed = ProgramProgressEngine.applySoftReturn(p,metadata,setOf(protected),99)
        val resultWeek = ProgramHierarchyIndex(changed).orderedWeeks().single().week
        assertEquals(week.sessions.first(),resultWeek.sessions.first())
        assertEquals(2,resultWeek.sessions.last().allExercises().single().sets.size)
        assertTrue(resultWeek.sessions.last().allExercises().single().sets.all { it.targetRIR == 4 })
        assertEquals(p.sourceRecipe,changed.sourceRecipe)
        assertEquals(changed,ProgramProgressEngine.applySoftReturn(changed,metadata,emptySet(),100))
        val author = p.copy(sourceProtocolId = "original")
        assertEquals(author,ProgramProgressEngine.applySoftReturn(author,metadata,emptySet(),100))
    }
    @Test fun firstOrdinarySessionDoesNotCallFutureDaysMissing() {
        val p = ownRecipe()
        val week = ProgramHierarchyIndex(p).orderedWeeks().single().week
        val s = week.sessions.first()
        val actual = log("w1").copy(sessionId = s.id,weekId = week.id,weekInstanceId = p.runState!!.weekInstanceId)
        val result = ProgramProgressEngine.advanceAfterSessionComplete(p,null,s,p.runState.weekInstanceId!!,listOf(actual),compositionMetadata = metadata)
        assertNull(result.program.pendingMissedSessions)
    }
}
