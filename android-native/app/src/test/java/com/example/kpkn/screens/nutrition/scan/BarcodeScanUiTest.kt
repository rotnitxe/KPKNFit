package com.example.kpkn.screens.nutrition.scan

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.BarcodeLookup
import com.example.kpkn.data.models.BarcodeOrigin
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Las pantallas del escáner por estado, sin cámara: lo que se ve y a qué llama cada botón. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h2400dp-mdpi")
class BarcodeScanUiTest {
    @get:Rule val rule = createComposeRule()

    private val leche = FoodItem(
        id = "off_7801234567894", name = "leche entera", brand = "Colún", calories = 60.0, protein = 3.2, carbs = 4.8, fats = 3.0,
        source = "OFF Chile", sourceRecordId = "7801234567894", nutritionBasis = "PER_100G_AS_SOLD",
    )

    private fun show(state: BarcodeScanUiState, actions: BarcodeScanActions = BarcodeScanActions(), torch: Boolean = true) {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                BarcodeScanContent(
                    state = state,
                    torchAvailable = torch,
                    actions = actions,
                    preview = { ScanPreviewPlaceholder(it) },
                    modifier = Modifier.width(360.dp).height(1800.dp),
                )
            }
        }
    }

    /**
     * Los botones de la hoja viven en una columna con desplazamiento: en Robolectric el toque simulado no llega al `clickable` dentro de
     * un contenedor desplazable, así que se invoca su acción de clic (se comprueba que existe y que su zona táctil mide lo que debe).
     */
    private fun SemanticsNodeInteraction.tap() = performSemanticsAction(SemanticsActions.OnClick)

    @Test fun `sin permiso decidido se explica la cámara y se ofrece pedirla o teclear`() {
        var requested = false
        var manual = false
        show(
            BarcodeScanUiState(access = CameraAccess.ASK),
            BarcodeScanActions(onRequestPermission = { requested = true }, onShowManual = { manual = true }),
        )
        rule.onNodeWithText("Usa la cámara para leer el código").assertExists()
        rule.onNodeWithText("La imagen no se guarda ni sale del aparato.", substring = true).assertExists()
        rule.onNodeWithTag("scan-allow-camera").assertHeightIsAtLeast(48.dp).tap()
        rule.onNodeWithTag("scan-type-code").assertHeightIsAtLeast(48.dp).tap()
        assertTrue(requested && manual)
    }

    @Test fun `con permiso se ve el visor, la linterna con su estado y la entrada manual`() {
        var torch = 0
        var manual = false
        show(
            BarcodeScanUiState(access = CameraAccess.GRANTED),
            BarcodeScanActions(onToggleTorch = { torch++ }, onShowManual = { manual = true }),
        )
        rule.onNodeWithTag("scan-viewfinder").assertExists()
        rule.onNodeWithText("Vista previa simulada").assertExists()
        rule.onNodeWithText("Centra el código en el recuadro").assertExists()
        rule.onNodeWithContentDescription("Encender la linterna").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("scan-type-code").performClick()
        assertEquals(1, torch)
        assertTrue(manual)
    }

    @Test fun `la linterna dice que se puede apagar cuando esta encendida y no sale sin linterna`() {
        show(BarcodeScanUiState(access = CameraAccess.GRANTED, torchOn = true))
        rule.onNodeWithContentDescription("Apagar la linterna").assertExists()
    }

    @Test fun `sin linterna disponible no se ofrece el boton`() {
        show(BarcodeScanUiState(access = CameraAccess.GRANTED), torch = false)
        rule.onNodeWithTag("scan-torch").assertDoesNotExist()
    }

    @Test fun `con el permiso denegado queda solo la entrada manual con la salida a ajustes`() {
        var settings = false
        var typed = ""
        var submitted = false
        show(
            BarcodeScanUiState(access = CameraAccess.DENIED, manualError = "El código no es válido: revisa los dígitos."),
            BarcodeScanActions(onOpenSettings = { settings = true }, onManualTextChange = { typed = it }, onSubmitManual = { submitted = true }),
        )
        rule.onNodeWithTag("scan-denied").assertExists()
        rule.onNodeWithTag("scan-use-camera").assertDoesNotExist()
        rule.onNodeWithText("El código no es válido: revisa los dígitos.").assertExists()
        rule.onNodeWithTag("scan-open-settings").assertHeightIsAtLeast(48.dp).tap()
        rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("scan-manual-field"))).performTextInput("4006381333931")
        rule.onNodeWithTag("scan-manual-submit").assertHeightIsAtLeast(48.dp).tap()
        assertTrue(settings && submitted)
        assertEquals("4006381333931", typed)
    }

    @Test fun `la entrada manual con camara disponible ofrece volver a ella`() {
        var camera = false
        show(
            BarcodeScanUiState(access = CameraAccess.GRANTED, manualRequested = true),
            BarcodeScanActions(onShowCamera = { camera = true }),
        )
        rule.onNodeWithTag("scan-use-camera").assertHeightIsAtLeast(48.dp).tap()
        assertTrue(camera)
    }

    @Test fun `un producto encontrado sube como hoja sobre la camara y se anade con los gramos por defecto`() {
        var confirmed: Pair<FoodItem, Double>? = null
        var another = false
        show(
            BarcodeScanUiState(access = CameraAccess.GRANTED, phase = ScanPhase.RESULT, code = "7801234567894",
                result = BarcodeLookup.Found(leche, BarcodeOrigin.CATALOGO)),
            BarcodeScanActions(onConfirm = { food, grams -> confirmed = food to grams }, onScanAnother = { another = true }),
        )
        // La cámara y su recuadro siguen detrás de la hoja (no se sale de la cámara).
        rule.onNodeWithTag("scan-viewfinder").assertExists()
        rule.onNodeWithText("Centra el código en el recuadro").assertExists()
        rule.onNodeWithText("ENCONTRADO · BASE GLOBAL").assertExists()
        rule.onNodeWithTag("scan-food-name").assertExists()
        rule.onNodeWithText("Leche entera").assertExists()
        rule.onNodeWithTag("scan-summary").assertTextContains("Colún", substring = true).assertTextContains("kcal", substring = true)
        // Lo que el lienzo no tiene ya no se pinta: base global/código/calidad en módulos y el selector de ración.
        rule.onNodeWithText("Código 7801234567894").assertDoesNotExist()
        rule.onNodeWithTag("scan-ration").assertDoesNotExist()
        rule.onNodeWithTag("scan-quality").assertDoesNotExist()
        rule.onNodeWithText("Agregar a la comida").assertDoesNotExist()
        rule.onNodeWithText("Probar otro").assertDoesNotExist()
        rule.onNodeWithTag("scan-confirm").assertIsEnabled().assertHeightIsAtLeast(48.dp).tap()
        rule.onNodeWithTag("scan-another").assertHeightIsAtLeast(48.dp).tap()
        assertNotNull(confirmed)
        assertEquals(leche, confirmed!!.first)
        assertTrue("los gramos propuestos son positivos", confirmed!!.second > 0.0)
        assertTrue(another)
    }

    @Test fun `un alimento propio encontrado lo dice`() {
        val propio = leche.copy(id = "custom_1", isCustom = true, source = "custom", brand = null)
        show(BarcodeScanUiState(access = CameraAccess.GRANTED, phase = ScanPhase.RESULT, result = BarcodeLookup.Found(propio, BarcodeOrigin.PROPIO)))
        rule.onNodeWithText("ENCONTRADO · TUS ALIMENTOS").assertExists()
    }

    @Test fun `un producto con datos incompletos no se puede anadir y ofrece crear el alimento`() {
        var created = false
        val incompleto = leche.copy(calories = 0.0, protein = 0.0, carbs = 0.0, fats = 0.0, qualityFlags = listOf("INCOMPLETE", "LOW_QUALITY"))
        show(
            BarcodeScanUiState(access = CameraAccess.GRANTED, phase = ScanPhase.RESULT, code = "7801234567894",
                result = BarcodeLookup.Found(incompleto, BarcodeOrigin.CATALOGO)),
            BarcodeScanActions(onCreateOwn = { created = true }),
        )
        rule.onNodeWithText("Sin datos fiables: créalo con su etiqueta").assertExists()
        rule.onNodeWithTag("scan-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("scan-quality").assertDoesNotExist()
        rule.onNodeWithTag("scan-ration").assertDoesNotExist()
        rule.onNodeWithTag("scan-create-own").assertHeightIsAtLeast(48.dp).tap()
        assertTrue(created)
    }

    @Test fun `un producto desconocido ofrece crearlo con su codigo o probar otro`() {
        var created = false
        var another = false
        show(
            BarcodeScanUiState(access = CameraAccess.GRANTED, phase = ScanPhase.RESULT, code = "7801234567894", result = BarcodeLookup.NotFound),
            BarcodeScanActions(onCreateOwn = { created = true }, onScanAnother = { another = true }),
        )
        rule.onNodeWithText("No tengo este producto").assertExists()
        rule.onNodeWithText("Código 7801234567894").assertExists()
        rule.onNodeWithText("Crear alimento").assertExists()
        rule.onNodeWithText("Escanear otro").assertExists()
        rule.onNodeWithTag("scan-create-own").assertHeightIsAtLeast(48.dp).tap()
        rule.onNodeWithTag("scan-another").assertHeightIsAtLeast(48.dp).tap()
        assertTrue(created && another)
    }

    @Test fun `mientras busca lo dice`() {
        show(BarcodeScanUiState(access = CameraAccess.GRANTED, phase = ScanPhase.SEARCHING, code = "7801234567894"))
        rule.onNodeWithText("Buscando en tu teléfono…").assertExists()
    }
}
