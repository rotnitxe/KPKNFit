package com.example.kpkn.screens.settings.export

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.data.export.CsvExportDomain
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1000dp-mdpi", application = Application::class)
class CsvExportSheetTest {
    @get:Rule val rule = createComposeRule()
    @Test fun sinSeleccionNoSeExporta() {
        rule.setContent { KPKNTheme { CsvExportContent(emptySet(), {}, false, null, false, {}, {}) } }
        rule.onNodeWithTag("csv-export").assertIsNotEnabled()
        rule.onNodeWithText("No hay registros; se exportarán archivos con sus cabeceras.").assertExists()
    }
    @Test fun seleccionarUnDominioAvisaAlEstadoYExportarSoloLlamaLaAccion() {
        var domain: CsvExportDomain? = null; var exports = 0
        rule.setContent { KPKNTheme { CsvExportContent(setOf(CsvExportDomain.MEALS), { domain = it }, false, null, true, { exports++ }, {}) } }
        rule.onNodeWithTag("csv-domain-BODY").performClick(); assertEquals(CsvExportDomain.BODY, domain)
        rule.onNodeWithTag("csv-export").performClick(); assertEquals(1, exports)
    }
    @Test fun unFalloQuedaVisibleYLaPreparacionNoAdmiteDobleToque() {
        rule.setContent { KPKNTheme { CsvExportContent(setOf(CsvExportDomain.MEALS), {}, true, "No se pudo preparar la exportación", true, {}, {}) } }
        rule.onNodeWithTag("csv-export").assertIsNotEnabled()
        rule.onNodeWithText("No se pudo preparar la exportación").assertExists()
    }
}
