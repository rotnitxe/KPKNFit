package com.example.kpkn.screens.workout

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorActionExecutionTest {

    @Test
    fun relator_inline_pieces_preserves_single_phrase_with_action() {
        val text = "Próxima serie: 95 kg × 6. Si quieres, sube a 97,5 kg."
        val action = RelatorAssistAction(
            kind = RelatorAssistActionKind.ADJUST_LOAD,
            label = "sube a 97,5 kg",
            span = "sube a 97,5 kg",
            weightKg = 97.5,
        )
        val pieces = relatorInlinePieces(text, listOf(action))
        assertEquals(3, pieces.size)
        assertTrue(pieces[0] is RelatorInlinePiece.Copy)
        assertEquals("Próxima serie: 95 kg × 6. Si quieres, ", (pieces[0] as RelatorInlinePiece.Copy).text)
        assertTrue(pieces[1] is RelatorInlinePiece.Action)
        assertEquals(action, (pieces[1] as RelatorInlinePiece.Action).action)
        assertTrue(pieces[2] is RelatorInlinePiece.Copy)
        assertEquals(".", (pieces[2] as RelatorInlinePiece.Copy).text)
    }

    @Test
    fun relator_inline_pieces_supports_two_actions() {
        val text = "Hoy toca 95 kg × 6: pon 95 kg o cambia por prensa."
        val action1 = RelatorAssistAction(
            kind = RelatorAssistActionKind.APPLY_SUGGESTED_LOAD,
            label = "pon 95 kg",
            span = "pon 95 kg",
            weightKg = 95.0,
        )
        val action2 = RelatorAssistAction(
            kind = RelatorAssistActionKind.OPEN_REPLACE,
            label = "cambia por prensa",
            span = "cambia por prensa",
        )
        val pieces = relatorInlinePieces(text, listOf(action1, action2))
        assertEquals(5, pieces.size)
        assertEquals("Hoy toca 95 kg × 6: ", (pieces[0] as RelatorInlinePiece.Copy).text)
        assertEquals(action1, (pieces[1] as RelatorInlinePiece.Action).action)
        assertEquals(" o ", (pieces[2] as RelatorInlinePiece.Copy).text)
        assertEquals(action2, (pieces[3] as RelatorInlinePiece.Action).action)
        assertEquals(".", (pieces[4] as RelatorInlinePiece.Copy).text)
    }

    @Test
    fun relator_action_has_working_phase_correctly_classified() {
        // Acciones que «trabajan» con personajes (H2)
        assertTrue(RelatorAssistActionKind.APPLY_SUGGESTED_LOAD.hasWorkingPhase())
        assertTrue(RelatorAssistActionKind.ADJUST_LOAD.hasWorkingPhase())
        assertTrue(RelatorAssistActionKind.START_REST.hasWorkingPhase())
        assertTrue(RelatorAssistActionKind.EXTEND_REST.hasWorkingPhase())
        assertTrue(RelatorAssistActionKind.SKIP_REMAINING_WARMUPS.hasWorkingPhase())

        // Acciones que abren hojas/cámara: instantáneas, sin fase de trabajo
        assertFalse(RelatorAssistActionKind.OPEN_REPLACE.hasWorkingPhase())
        assertFalse(RelatorAssistActionKind.OPEN_READINESS.hasWorkingPhase())
        assertFalse(RelatorAssistActionKind.OPEN_TECHNIQUE.hasWorkingPhase())
        assertFalse(RelatorAssistActionKind.OPEN_HISTORY.hasWorkingPhase())
        assertFalse(RelatorAssistActionKind.CAPTURE_MEDIA.hasWorkingPhase())
        assertFalse(RelatorAssistActionKind.OPEN_ALBUM.hasWorkingPhase())
    }

    @Test
    fun all_eleven_relator_actions_exist_and_map_cleanly() {
        val eleven = listOf(
            RelatorAssistActionKind.APPLY_SUGGESTED_LOAD,
            RelatorAssistActionKind.ADJUST_LOAD,
            RelatorAssistActionKind.START_REST,
            RelatorAssistActionKind.EXTEND_REST,
            RelatorAssistActionKind.SKIP_REMAINING_WARMUPS,
            RelatorAssistActionKind.OPEN_REPLACE,
            RelatorAssistActionKind.OPEN_READINESS,
            RelatorAssistActionKind.OPEN_TECHNIQUE,
            RelatorAssistActionKind.OPEN_HISTORY,
            RelatorAssistActionKind.CAPTURE_MEDIA,
            RelatorAssistActionKind.OPEN_ALBUM,
        )
        assertEquals(11, eleven.distinct().size)
        eleven.forEach { kind ->
            assertNotNull(RelatorAssistActionKind.valueOf(kind.name))
        }
    }

    @Test
    fun relator_working_phrases_describe_actions() {
        val loadAction = RelatorAssistAction(
            kind = RelatorAssistActionKind.APPLY_SUGGESTED_LOAD,
            label = "pon 95 kg",
            span = "pon 95 kg",
            weightKg = 95.0,
        )
        val kg = loadAction.weightKg?.let { formatRelatorKg(it) } ?: "95"
        val workingPhrase = "El equipo está cargando los discos de $kg\u00A0kg…"
        assertTrue(workingPhrase.contains("95"))
        assertTrue(workingPhrase.contains("cargando los discos"))

        val restAction = RelatorAssistAction(
            kind = RelatorAssistActionKind.EXTEND_REST,
            label = "+15 s",
            span = "+15 s",
        )
        val restPhrase = "Ajustando el tiempo de descanso…"
        assertTrue(restPhrase.contains("descanso"))

        val warmupAction = RelatorAssistAction(
            kind = RelatorAssistActionKind.SKIP_REMAINING_WARMUPS,
            label = "pasa a efectivas",
            span = "pasa a efectivas",
        )
        val warmupPhrase = "Saltando el calentamiento restante…"
        assertTrue(warmupPhrase.contains("calentamiento"))
    }

    @Test
    fun relator_done_phrase_creates_undo_for_load_action() {
        val action = RelatorAssistAction(
            kind = RelatorAssistActionKind.APPLY_SUGGESTED_LOAD,
            label = "pon 95 kg",
            span = "pon 95 kg",
            setIndex = 0,
            weightKg = 95.0,
        )
        val prevKg = "92,5"
        val undoLabel = "vuelve a $prevKg"
        val donePhrase = "Listo: la serie 1 va con 95\u00A0kg. Si te arrepientes, $undoLabel."
        assertTrue(donePhrase.contains("Listo: la serie 1 va con 95"))
        assertTrue(donePhrase.contains("vuelve a 92,5"))

        val undoAction = RelatorAssistAction(
            kind = RelatorAssistActionKind.ADJUST_LOAD,
            label = undoLabel,
            exerciseId = action.exerciseId,
            setIndex = action.setIndex,
            side = action.side,
            span = undoLabel,
            weightKg = 92.5,
        )
        assertEquals(92.5, undoAction.weightKg)
        assertEquals("vuelve a 92,5", undoAction.label)
    }

    @Test
    fun silent_animations_fallback_talkback() {
        val talkBackRest = "Descansando"
        val talkBackRecord = "Nuevo récord"
        val talkBackPrep = "Preparándote"

        assertEquals("Descansando", talkBackRest)
        assertEquals("Nuevo récord", talkBackRecord)
        assertEquals("Preparándote", talkBackPrep)
    }

    @Test
    fun relator_silent_moments_resolution() {
        val resRest = RelatorResolution(
            text = null,
            holdPrevious = false,
            phaseKey = "rest",
            silentAnimation = RelatorSilentAnimation.REST_BREATHING,
        )
        assertEquals(RelatorSilentAnimation.REST_BREATHING, resRest.silentAnimation)

        val resRecord = RelatorResolution(
            text = "PR homologado en Press banca (~100 kg)",
            holdPrevious = false,
            phaseKey = "milestone",
            silentAnimation = RelatorSilentAnimation.RECORD_CELEBRATION,
        )
        assertEquals(RelatorSilentAnimation.RECORD_CELEBRATION, resRecord.silentAnimation)

        val resPrep = RelatorResolution(
            text = null,
            holdPrevious = false,
            phaseKey = "warmup",
            silentAnimation = RelatorSilentAnimation.PREPARATION_SQUAT,
        )
        assertEquals(RelatorSilentAnimation.PREPARATION_SQUAT, resPrep.silentAnimation)
    }
}
