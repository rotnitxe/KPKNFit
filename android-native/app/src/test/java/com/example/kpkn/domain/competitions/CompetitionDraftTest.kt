package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionJournal
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionDraftTest {

    @Test
    fun empty_powerlifting_has_sbd_rows_without_invented_weights_and_cnext_reminders() {
        val draft = CompetitionDraft.createEmpty(CompetitionDiscipline.POWERLIFTING, id = "c1")
        assertEquals(listOf("Sentadilla", "Press banca", "Peso muerto"), draft.technicalBlocks.map { it.title })
        assertEquals(
            listOf(CompetitionMovementType.SQUAT, CompetitionMovementType.BENCH, CompetitionMovementType.DEADLIFT),
            draft.technicalBlocks.map { it.movementType },
        )
        assertTrue(draft.technicalBlocks.all { it.attempts.size == 3 })
        assertTrue(draft.technicalBlocks.all { b -> b.attempts.all { it.weightKg == null && it.resultType == CompetitionAttemptResult.PENDING } })
        assertTrue(draft.reminderOneWeekEnabled)
        assertTrue(draft.reminder48hEnabled)
        assertFalse(draft.reminderStartEnabled)
        assertEquals("powerlifting", draft.discipline)
    }

    @Test
    fun weightlifting_and_hyrox_seed_their_own_rows() {
        val wl = CompetitionDraft.createEmpty(CompetitionDiscipline.WEIGHTLIFTING)
        assertEquals(listOf("Arrancada", "Dos tiempos"), wl.technicalBlocks.map { it.title })
        val hyrox = CompetitionDraft.createEmpty(CompetitionDiscipline.HYROX)
        assertEquals(9, hyrox.customMetrics.size)
        assertEquals("8 × 1 km", hyrox.customMetrics.first().label)
        assertTrue(hyrox.technicalBlocks.isEmpty())
    }

    @Test
    fun bodyweight_is_kept_only_for_weigh_in_disciplines() {
        assertEquals(82.5, CompetitionDraft.createEmpty(CompetitionDiscipline.POWERLIFTING, bodyweightKg = 82.5).bodyweightKg!!, 0.0)
        assertNull(CompetitionDraft.createEmpty(CompetitionDiscipline.RUNNING, bodyweightKg = 82.5).bodyweightKg)
    }

    @Test
    fun ensure_blocks_keeps_journal_photos_reminders_and_written_attempts() {
        val photo = CompetitionPhoto(id = "p1", uri = "file://p1.jpg")
        val base = CompetitionDraft.createEmpty(CompetitionDiscipline.POWERLIFTING).copy(
            journal = CompetitionJournal(overallFeeling = "bien"),
            photos = listOf(photo),
            reminderStartEnabled = true,
        )
        val squat = base.technicalBlocks.first()
        val written = CompetitionAttempts.setWeight(base, squat.id, 1, 140.0)
        val ensured = CompetitionDraft.ensureBlocks(written, CompetitionDiscipline.POWERLIFTING)
        assertEquals("bien", ensured.journal?.overallFeeling)
        assertEquals(listOf(photo), ensured.photos)
        assertTrue(ensured.reminderStartEnabled)
        assertEquals(140.0, ensured.technicalBlocks.first().attempts.first { it.attemptNumber == 1 }.weightKg!!, 0.0)
        assertEquals(3, ensured.technicalBlocks.size)
    }

    @Test
    fun derived_title_uses_typed_name_or_discipline_and_year() {
        val record = CompetitionDraft.createEmpty(CompetitionDiscipline.RUNNING).copy(eventDate = "2026-11-08")
        assertEquals("Carrera 2026", CompetitionDraft.derivedTitle(record, CompetitionDiscipline.RUNNING))
        assertEquals("Maratón de Valencia", CompetitionDraft.derivedTitle(record.copy(title = "  Maratón de Valencia "), CompetitionDiscipline.RUNNING))
        assertEquals("Carrera", CompetitionDraft.derivedTitle(record.copy(eventDate = null), CompetitionDiscipline.RUNNING))
    }

    @Test
    fun add_media_caps_at_eight_and_remove_media_drops_only_that_one() {
        val record = CompetitionDraft.createEmpty(CompetitionDiscipline.POWERLIFTING)
        val media = (1..10).map { CompetitionPhoto(id = "p$it", uri = "file://p$it.jpg") }
        val full = CompetitionDraft.addMedia(record, media)
        assertEquals(CompetitionDraft.MAX_MEDIA, full.photos.size)
        val removed = CompetitionDraft.removeMedia(full, "p2")
        assertEquals(7, removed.photos.size)
        assertFalse(removed.photos.any { it.id == "p2" })
    }

    @Test
    fun with_journal_keeps_the_rest_of_the_diary() {
        val record = CompetitionDraft.createEmpty(CompetitionDiscipline.POWERLIFTING)
            .copy(journal = CompetitionJournal(judgesFeedback = "Profundidad justa", learnings = "Abrir más bajo"))
        val updated = CompetitionDraft.withJournal(record, feeling = "genial", best = "El tercer muerto")
        assertEquals("genial", updated.journal?.overallFeeling)
        assertEquals("El tercer muerto", updated.journal?.whatWentWell)
        assertEquals("Abrir más bajo", updated.journal?.learnings)
        assertEquals("Profundidad justa", updated.journal?.judgesFeedback)
    }
}
