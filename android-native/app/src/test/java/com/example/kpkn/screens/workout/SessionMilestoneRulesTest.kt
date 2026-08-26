package com.example.kpkn.screens.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMilestoneRulesTest {

    @Test
    fun first_set_without_history_is_not_a_pr() {
        assertFalse(shouldRecordPrE1rmMilestone(e1rm = 100.0, historyBest = 0.0, sessionBestPrevious = 0.0))
    }

    @Test
    fun improvement_over_history_records_pr() {
        assertTrue(shouldRecordPrE1rmMilestone(e1rm = 110.0, historyBest = 100.0, sessionBestPrevious = 0.0))
    }

    @Test
    fun tiny_epsilon_over_history_is_not_enough() {
        assertFalse(shouldRecordPrE1rmMilestone(e1rm = 100.2, historyBest = 100.0, sessionBestPrevious = 0.0))
    }

    @Test
    fun session_prior_best_can_gate_without_history() {
        assertFalse(shouldRecordPrE1rmMilestone(e1rm = 100.0, historyBest = 0.0, sessionBestPrevious = 100.0))
        assertTrue(shouldRecordPrE1rmMilestone(e1rm = 106.0, historyBest = 0.0, sessionBestPrevious = 100.0))
    }
}
