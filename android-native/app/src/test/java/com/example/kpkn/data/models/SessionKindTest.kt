package com.example.kpkn.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionKindTest {

    @Test
    fun isCompetitionMeet_false_for_plain_training_session() {
        val session = Session(id = "s1", name = "Push day")
        assertFalse(session.isCompetitionMeet)
        assertEquals(SessionKind.TRAINING, session.kind)
    }

    @Test
    fun isCompetitionMeet_true_when_isMeetDay_flag_set() {
        val session = Session(id = "s1", name = "Meet day", isMeetDay = true)
        assertTrue(session.isCompetitionMeet)
        assertEquals(SessionKind.COMPETITION, session.kind)
    }

    @Test
    fun isCompetitionMeet_true_when_isCompetitionSession_flag_set() {
        // Legacy data may only have one of the two flags set; the helper ORs both so callers
        // don't need to know which flag was historically used.
        val session = Session(id = "s1", name = "Meet day", isCompetitionSession = true)
        assertTrue(session.isCompetitionMeet)
        assertEquals(SessionKind.COMPETITION, session.kind)
    }

    @Test
    fun isCompetitionMeet_true_when_both_flags_set() {
        val session = Session(id = "s1", name = "Meet day", isMeetDay = true, isCompetitionSession = true)
        assertTrue(session.isCompetitionMeet)
    }
}
