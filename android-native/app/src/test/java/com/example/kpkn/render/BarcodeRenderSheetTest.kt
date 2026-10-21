package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.BarcodeLookup
import com.example.kpkn.data.models.BarcodeOrigin
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.screens.nutrition.customfood.CustomFoodForm
import com.example.kpkn.screens.nutrition.customfood.CustomFoodFormContent
import com.example.kpkn.screens.nutrition.customfood.CustomFoodValidator
import com.example.kpkn.screens.nutrition.scan.BarcodeScanActions
import com.example.kpkn.screens.nutrition.scan.BarcodeScanContent
import com.example.kpkn.screens.nutrition.scan.BarcodeScanUiState
import com.example.kpkn.screens.nutrition.scan.CameraAccess
import com.example.kpkn.screens.nutrition.scan.ScanPhase
import com.example.kpkn.screens.nutrition.scan.ScanPreviewPlaceholder
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Hoja de render de N3 (solo con `KPKN_RENDER_DIR`): el escáner con permiso (visor), el escáner sin permiso (entrada manual), la
 * explicación del permiso, producto encontrado (y con datos incompletos), no encontrado, y el formulario de alimento propio vacío,
 * con errores y en edición. A 360 dp, al 100 % y al 130 % de letra.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2600dp-mdpi", application = Application::class)
class BarcodeRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun requested() { assumeTrue(System.getenv("KPKN_RENDER_DIR") != null) }
    }

    @get:Rule val rule = createComposeRule()
    private var host: View? = null

    private val leche = FoodItem(
        id = "off_7801234567894", name = "leche entera", brand = "Colún", calories = 60.0, protein = 3.2, carbs = 4.8, fats = 3.0,
        source = "OFF Chile", sourceRecordId = "7801234567894", nutritionBasis = "PER_100G_AS_SOLD",
    )
    private val incompleta = leche.copy(calories = 0.0, protein = 0.0, carbs = 0.0, fats = 0.0, qualityFlags = listOf("INCOMPLETE", "LOW_QUALITY"))

    private fun scan(state: BarcodeScanUiState, torch: Boolean = true): @Composable (Modifier) -> Unit = { modifier ->
        BarcodeScanContent(state, torch, BarcodeScanActions(), { ScanPreviewPlaceholder(it) }, modifier)
    }

    private fun form(form: CustomFoodForm, showErrors: Boolean, editing: Boolean = false): @Composable (Modifier) -> Unit = { modifier ->
        val validation = CustomFoodValidator.validate(form, "custom_1")
        CustomFoodFormContent(
            form = form, validation = validation, showErrors = showErrors, isEditing = editing, saving = false, saveError = null,
            confirmingDelete = false, onChange = {}, onSave = {}, onAskDelete = {}, onConfirmDelete = {}, onCancelDelete = {}, onClose = {},
            modifier = modifier,
        )
    }

    private fun render(name: String, scale: Float, heightDp: Int, content: @Composable (Modifier) -> Unit) {
        // Segunda guarda: sin la variable, ninguna prueba de la clase llega a dibujar (ni revienta con el `!!` de más abajo).
        assumeTrue(System.getenv("KPKN_RENDER_DIR") != null)
        rule.setContent {
            KPKNTheme {
                host = LocalView.current
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, scale), LocalWizardReducedMotion provides true) {
                    Box(Modifier.testTag("barcode-render").width(360.dp).height(heightDp.dp).background(KpknSurface.canvas)) {
                        content(Modifier.width(360.dp).height(heightDp.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("barcode-render").fetchSemanticsNode().boundsInRoot
        val view = host!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val crop = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val directory = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(directory, "barcode_${name}_${(scale * 100).toInt()}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle(); bitmap.recycle()
    }

    private val camera = BarcodeScanUiState(access = CameraAccess.GRANTED)
    private val permission = BarcodeScanUiState(access = CameraAccess.ASK)
    private val manual = BarcodeScanUiState(access = CameraAccess.DENIED, manualText = "7801234567890", manualError = "El código no es válido: revisa los dígitos.")
    private val found = BarcodeScanUiState(access = CameraAccess.GRANTED, phase = ScanPhase.RESULT, code = "7801234567894", result = BarcodeLookup.Found(leche, BarcodeOrigin.CATALOGO))
    private val foundFlags = BarcodeScanUiState(access = CameraAccess.GRANTED, phase = ScanPhase.RESULT, code = "7801234567894", result = BarcodeLookup.Found(incompleta, BarcodeOrigin.CATALOGO))
    private val notFound = BarcodeScanUiState(access = CameraAccess.GRANTED, phase = ScanPhase.RESULT, code = "7801234567894", result = BarcodeLookup.NotFound)

    private val emptyForm = CustomFoodForm(barcode = "7801234567894")
    private val errorForm = CustomFoodForm(
        name = "Galletas de avena", barcode = "12345", servingAmount = "abc", calories = "300", protein = "2", carbs = "21", fats = "4,5",
    )
    private val savedFood: FoodItem = requireNotNull(
        CustomFoodValidator.validate(
            CustomFoodForm(name = "Galletas de avena", brand = "Marilan", barcode = "7801234567894", servingAmount = "30", calories = "135", protein = "2", carbs = "21", fats = "4,5", fiber = "1,5", sodiumMg = "90"),
            "custom_1",
        ).food,
    )

    @Test fun camera100() = render("camera", 1f, 780, scan(camera))
    @Test fun camera130() = render("camera", 1.3f, 900, scan(camera))
    @Test fun permission100() = render("permission", 1f, 780, scan(permission))
    @Test fun permission130() = render("permission", 1.3f, 900, scan(permission))
    @Test fun manual100() = render("manual", 1f, 780, scan(manual))
    @Test fun manual130() = render("manual", 1.3f, 900, scan(manual))
    @Test fun found100() = render("found", 1f, 1100, scan(found))
    @Test fun found130() = render("found", 1.3f, 1500, scan(found))
    @Test fun foundFlags100() = render("found_flags", 1f, 900, scan(foundFlags))
    @Test fun foundFlags130() = render("found_flags", 1.3f, 1300, scan(foundFlags))
    @Test fun notFound100() = render("not_found", 1f, 780, scan(notFound))
    @Test fun notFound130() = render("not_found", 1.3f, 900, scan(notFound))
    @Test fun formEmpty100() = render("form_empty", 1f, 1500, form(emptyForm, showErrors = false))
    @Test fun formEmpty130() = render("form_empty", 1.3f, 2100, form(emptyForm, showErrors = false))
    @Test fun formError100() = render("form_error", 1f, 1600, form(errorForm, showErrors = true))
    @Test fun formError130() = render("form_error", 1.3f, 2300, form(errorForm, showErrors = true))
    @Test fun formEdit100() = render("form_edit", 1f, 1500, form(CustomFoodValidator.fromFood(savedFood), showErrors = false, editing = true))
    @Test fun formEdit130() = render("form_edit", 1.3f, 2100, form(CustomFoodValidator.fromFood(savedFood), showErrors = false, editing = true))
}
