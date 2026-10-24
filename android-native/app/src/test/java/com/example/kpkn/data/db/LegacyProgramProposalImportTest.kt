package com.example.kpkn.data.db

import com.example.kpkn.data.compatibility.LegacyProgramProposalImport
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LegacyProgramProposalImportTest {
    @Test fun mixedPendingKeepsProtocolProposalAndAllAuthoredOriginals() {
        val raw = """{"id":"p","notes":"Nota AUGE original","macrocycles":[{"id":"b","weight":73.125}],"runState":{"runId":"run","weekId":"w","completedSessionIds":["s"],"pendingAction":{"type":"CONFIRM_AUTOREGULATION","message":"Antigua","proposals":[{"kind":"SCALE_WEEK_VOLUME","volumeFactor":0.7},{"kind":"ADJUST_XRM","explanation":"APRE original","apreXrm":{"reference":{"weightKg":51.25}}}]}}}"""
        val source = Json.parseToJsonElement(raw).jsonObject
        val result = LegacyProgramProposalImport.program(raw)
        assertEquals(result, LegacyProgramProposalImport.program(result))
        val migrated = Json.parseToJsonElement(result).jsonObject
        assertEquals(source["macrocycles"], migrated["macrocycles"])
        assertEquals(source["notes"], migrated["notes"])
        val run = migrated.getValue("runState").jsonObject
        assertEquals("run", run.getValue("runId").jsonPrimitive.content)
        assertEquals("w", run.getValue("weekId").jsonPrimitive.content)
        val proposal = run.getValue("pendingAction").jsonObject.getValue("proposals").jsonArray.single()
        assertEquals(source.getValue("runState").jsonObject.getValue("pendingAction").jsonObject.getValue("proposals").jsonArray[1], proposal)
    }
    @Test fun retiredDeloadClearsOnlyThePendingActionIncludingPausedRuns() {
        val state = """{"runId":"r","blockId":"b","pendingAction":{"type":"CONFIRM_DELOAD","nextBlockId":"d"}}"""
        val result = Json.parseToJsonElement(LegacyProgramProposalImport.program("""{"runState":$state,"calendarBreaks":[{"id":"break","pausedRunState":$state}],"sessions":[{"id":"s"}]}""")).jsonObject
        assertEquals(JsonNull, result.getValue("runState").jsonObject["pendingAction"])
        assertEquals("b", result.getValue("runState").jsonObject.getValue("blockId").jsonPrimitive.content)
        assertEquals(JsonNull, result.getValue("calendarBreaks").jsonArray.single().jsonObject.getValue("pausedRunState").jsonObject["pendingAction"])
        assertEquals("s", result.getValue("sessions").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
    }
}
