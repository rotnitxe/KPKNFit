package com.example.kpkn.screens.programs.importer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las formas de traer un programa: tres símbolos sin tarjeta. «Foto de tu rutina» despliega debajo «Hacer una foto» y «Elegir de
 * la galería»; elegir otra forma pliega esas acciones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImportSourceChoicesTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setChoices(onPaste: () -> Unit = {}) {
        rule.setContent {
            ImportSourceChoices(onChooseFile = {}, onPaste = onPaste, onPhoto = { _, _ -> })
        }
    }

    @Test
    fun theThreeSymbolsAreOfferedAndTheFileOneSaysItReadsPdfToo() {
        setChoices()

        rule.onNodeWithTag("import-choose-file").assertExists()
        rule.onNodeWithTag("import-paste-text").assertExists()
        rule.onNodeWithTag("import-source-photo").assertExists()
        rule.onNodeWithContentDescription("Elegir un archivo de Excel, CSV, Word, texto o PDF").assertExists()
    }

    @Test
    fun tappingThePhotoSymbolOffersTakingAPhotoOrChoosingOneFromTheGallery() {
        setChoices()
        rule.onNodeWithTag("import-photo-camera").assertDoesNotExist()

        rule.onNodeWithTag("import-source-photo").performClick()

        rule.onNodeWithText("Hacer una foto").assertExists()
        rule.onNodeWithText("Elegir de la galería").assertExists()
    }

    @Test
    fun choosingAnotherWayFoldsThePhotoActionsAgain() {
        var pasted = 0
        setChoices(onPaste = { pasted++ })
        rule.onNodeWithTag("import-source-photo").performClick()

        rule.onNodeWithTag("import-paste-text").performClick()

        assertEquals(1, pasted)
        rule.onNodeWithTag("import-photo-camera").assertDoesNotExist()
        rule.onNodeWithTag("import-photo-gallery").assertDoesNotExist()
    }
}
