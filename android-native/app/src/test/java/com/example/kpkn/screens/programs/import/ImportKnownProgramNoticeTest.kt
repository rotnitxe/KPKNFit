package com.example.kpkn.screens.programs.importer

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.domain.training.importer.ImportSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * H3f · El aviso «Parece…» en Programas → Importar: sale si el título nombra un programa de KPKN, «Usar el de KPKN» entrega su id
 * y «Seguir con mi archivo» lo quita (y el archivo sigue).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImportKnownProgramNoticeTest {
    @get:Rule
    val rule = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    /** Una hoja con una sentadilla: lo que decide el aviso es el título del archivo. */
    private fun oneSheet(): List<ImportSheet> = listOf(
        ImportSheet("Semana 1", listOf(listOf("Ejercicio", "Series", "Reps"), listOf("Sentadilla", "4", "6"))),
    )

    /** Deja que la lectura termine: el ViewModel publica en el bucle principal de Robolectric. */
    private fun settle(vm: ImportProgramViewModel) {
        repeat(600) {
            ShadowLooper.idleMainLooper()
            val state = vm.state.value
            if (state.imported != null && !state.busy) return
            Thread.sleep(10)
        }
        error("la importación no terminó: ${vm.state.value}")
    }

    @Test
    fun aRecognizedProgramShowsItsNoticeAndUsingItGivesItsId() {
        val vm = ImportProgramViewModel(app)
        vm.importFromSheets(oneSheet(), "GZCLP")
        settle(vm)
        val used = mutableListOf<String>()
        rule.setContent {
            ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = { used += it }, viewModel = vm)
        }

        rule.onNodeWithText("Parece «GZCLP».").assertExists()
        rule.onNodeWithTag("import-known-use").performClick()

        assertEquals(listOf("protocol:gzclp"), used)
    }

    @Test
    fun seguirConMiArchivoRemovesTheNoticeAndKeepsTheFile() {
        val vm = ImportProgramViewModel(app)
        vm.importFromSheets(oneSheet(), "GZCLP")
        settle(vm)
        rule.setContent {
            ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm)
        }

        rule.onNodeWithTag("import-known-dismiss").performClick()

        rule.onNodeWithTag("import-known-notice").assertDoesNotExist()
        assertTrue(vm.state.value.importKnownDismissed)
        assertNotNull(vm.state.value.imported)
    }

    @Test
    fun aFileThatIsNotAKnownProgramShowsNoNotice() {
        val vm = ImportProgramViewModel(app)
        vm.importFromSheets(oneSheet(), "Mi rutina")
        settle(vm)
        rule.setContent {
            ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm)
        }

        rule.onNodeWithTag("import-known-notice").assertDoesNotExist()
    }
}
