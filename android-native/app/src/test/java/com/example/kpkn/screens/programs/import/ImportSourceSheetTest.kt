package com.example.kpkn.screens.programs.importer

import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La hoja «Importar un programa» (escena pr-import del lienzo v6): tres filas con su línea explicativa. «Foto de tu rutina» cambia la
 * hoja a «Hacer una foto» y «Elegir de la galería». Los controles se activan con la acción de clic semántica, la de TalkBack.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImportSourceSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setSheet(onPaste: () -> Unit = {}) {
        rule.setContent {
            ImportSourceSheet(onDismiss = {}, onFile = {}, onPhoto = { _, _ -> }, onPaste = onPaste)
        }
    }

    @Test
    fun theSheetOffersTheThreeSourcesEachWithItsLine() {
        setSheet()

        rule.onNodeWithText("Importar un programa").assertExists()
        rule.onNodeWithText("KPKN lo lee y te deja revisarlo antes de guardarlo.").assertExists()
        rule.onNodeWithText("Elegir archivo").assertExists()
        rule.onNodeWithText("Excel, CSV, Word, texto o PDF").assertExists()
        rule.onNodeWithText("Pegar texto").assertExists()
        rule.onNodeWithText("Ej.: Día 1 — Pierna / Sentadilla 4x6 @120kg").assertExists()
        rule.onNodeWithText("Foto de tu rutina").assertExists()
        rule.onNodeWithText("Hacer una foto o elegirla de la galería").assertExists()
        // La rejilla ilustrada y las acciones desplegables bajo un símbolo ya no existen.
        rule.onNodeWithTag("import-photo-camera").assertDoesNotExist()
    }

    @Test
    fun tappingThePhotoRowSwitchesTheSheetToTakingAPhotoOrChoosingOne() {
        setSheet()

        rule.onNodeWithTag("import-source-photo").performSemanticsAction(SemanticsActions.OnClick)

        rule.onNodeWithText("Hacer una foto").assertExists()
        rule.onNodeWithText("Elegir de la galería").assertExists()
        rule.onNodeWithTag("import-paste-text").assertDoesNotExist()
    }

    @Test
    fun tappingPasteTextCallsOnPaste() {
        var pasted = 0
        setSheet(onPaste = { pasted++ })

        rule.onNodeWithTag("import-paste-text").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, pasted)
    }

    @Test
    fun theHandoffIsTakenOnlyOnce() {
        val request = ImportRequest.Photo(Uri.parse("content://kpkn.test/rutina.jpg"), takenFile = null)
        ImportHandoff.pending = request

        assertSame(request, ImportHandoff.take())
        assertNull(ImportHandoff.take())
    }
}
