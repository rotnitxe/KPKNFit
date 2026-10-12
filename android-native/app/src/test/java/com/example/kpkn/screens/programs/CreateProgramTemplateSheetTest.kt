package com.example.kpkn.screens.programs

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La entrada «Importar» de la hoja de crear programa: su título y su subtítulo nuevos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class CreateProgramTemplateSheetTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laEntradaDeImportarTieneSuTituloYSubtituloNuevos() {
        rule.setContent {
            CreateProgramTemplateSheet(
                onDismiss = {},
                onCreateBlank = {},
                onImport = {},
                onCreateFromTemplate = {},
            )
        }

        rule.onNodeWithText("Importar un programa").assertExists()
        rule.onNodeWithText("Desde Excel, Word, PDF, texto o una foto de tu rutina.").assertExists()
        rule.onNodeWithText("Importar (Excel, CSV o texto)").assertDoesNotExist()
    }
}
