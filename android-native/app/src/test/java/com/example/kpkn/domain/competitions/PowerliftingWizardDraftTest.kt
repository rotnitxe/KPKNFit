package com.example.kpkn.domain.competitions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerliftingWizardDraftTest {

    @Test
    fun empty_draft_has_sbd_titles_not_junk_prefill() {
        val draft = PowerliftingWizardDraft.createEmpty()
        assertFalse(PowerliftingWizardDraft.containsJunkPrefill(draft))
        assertFalse(draft.technicalBlocks.any { it.title.equals(PowerliftingWizardDraft.JUNK_TECHNICAL_TITLE, ignoreCase = true) })
        assertTrue(draft.technicalBlocks.map { it.title }.containsAll(listOf("Sentadilla", "Press banca", "Peso muerto")))
        assertTrue(draft.technicalBlocks.all { it.attempts.size == 3 })
        assertTrue(draft.technicalBlocks.all { it.attempts.all { attempt -> attempt.weightKg == null } })
        assertFalse(draft.reminderOneWeekEnabled)
        assertFalse(draft.reminder48hEnabled)
        assertFalse(draft.reminderStartEnabled)
    }
}
