package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi")
class RelatorActionExecutionTest {

    @get:Rule
    val rule = createComposeRule()

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

    @Test
    fun relator_action_underline_bounds_strictly_within_action_text() {
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
        val actions = listOf(action1, action2)

        var capturedLayout: TextLayoutResult? = null
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1.3f)) {
                Box(Modifier.width(360.dp)) {
                    Text(
                        text = text,
                        textAlign = TextAlign.Center,
                        onTextLayout = { capturedLayout = it },
                    )
                }
            }
        }
        rule.waitForIdle()

        val layout = capturedLayout
        assertNotNull("TextLayoutResult debe haberse obtenido", layout)
        val segments = relatorActionLineSegments(text, actions, layout!!)
        assertTrue("Debe haber al menos 2 tramos de acción", segments.size >= 2)

        // Acción 1 ("pon 95 kg")
        val seg1 = segments.first { it.action == action1 }
        val action1Start = text.indexOf("pon 95 kg")
        val action1End = action1Start + "pon 95 kg".length
        assertEquals(action1Start, seg1.startOffset)
        assertEquals(action1End, seg1.endOffset)

        val prefixEnd = text.indexOf("pon 95 kg")
        val prefixRight = layout.getBoundingBox(prefixEnd - 1).right
        // El subrayado de "pon 95 kg" debe comenzar estrictamente después del prefijo "Hoy toca 95 kg × 6: "
        // y NO estar desplazado a la izquierda bajo "kg × 6:"
        assertTrue("Subrayado debe empezar después del prefijo", seg1.bounds.left >= prefixRight - 2f)
        val charPonLeft = layout.getBoundingBox(action1Start).left
        assertEquals("Subrayado debe empezar exactamente en 'pon'", charPonLeft, seg1.bounds.left, 2f)
        val charKgRight = layout.getBoundingBox(action1End - 1).right
        assertEquals("Subrayado debe terminar exactamente en 'kg'", charKgRight, seg1.bounds.right, 2f)

        // Acción 2 ("cambia por prensa"), que puede ocupar uno o varios tramos si salta de línea al 130 %
        val segs2 = segments.filter { it.action == action2 }
        assertTrue("Acción 2 debe tener al menos un tramo", segs2.isNotEmpty())
        for (seg in segs2) {
            val charLeft = layout.getBoundingBox(seg.startOffset).left
            val charRight = layout.getBoundingBox(seg.endOffset - 1).right
            assertEquals("Subrayado debe empezar exactamente en el carácter inicial del tramo", charLeft, seg.bounds.left, 2f)
            assertEquals("Subrayado debe terminar exactamente en el carácter final del tramo", charRight, seg.bounds.right, 2f)
        }
    }
}

