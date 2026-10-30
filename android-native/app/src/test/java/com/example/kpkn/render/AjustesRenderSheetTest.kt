package com.example.kpkn.render

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.export.CsvExportDomain
import com.example.kpkn.data.food.nutritionNotices
import com.example.kpkn.data.models.WeightUnit
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.settings.AjBackupKind
import com.example.kpkn.screens.settings.AjColors
import com.example.kpkn.screens.settings.AjCopyScreen
import com.example.kpkn.screens.settings.AjDeleteContent
import com.example.kpkn.screens.settings.AjDiagnosticsScreen
import com.example.kpkn.screens.settings.AjHelpScreen
import com.example.kpkn.screens.settings.AjMainScreen
import com.example.kpkn.screens.settings.AjPerm
import com.example.kpkn.screens.settings.AjPermState
import com.example.kpkn.screens.settings.AjPermissionsScreen
import com.example.kpkn.screens.settings.AjRemindersScreen
import com.example.kpkn.screens.settings.AjRestoreContent
import com.example.kpkn.screens.settings.AjSaveContent
import com.example.kpkn.screens.settings.AjSheetColumn
import com.example.kpkn.screens.settings.AjTimeContent
import com.example.kpkn.screens.settings.AjUnitsContent
import com.example.kpkn.screens.settings.LocalAjStatusTop
import com.example.kpkn.screens.settings.NutritionSourcesContent
import com.example.kpkn.screens.settings.export.CsvExportContent
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

/** Hoja de render del paquete AJUSTES (aj): cada escena del lienzo con sus datos de muestra, en PNG dentro de `KPKN_RENDER_DIR`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2600dp-mdpi", application = Application::class)
class AjustesRenderSheetTest {
    companion object {
        @JvmStatic @BeforeClass fun requested() { assumeTrue(System.getenv("KPKN_RENDER_DIR") != null) }
    }

    @get:Rule val rule = createComposeRule()
    private var host: View? = null

    private fun render(id: String, height: Int = 880, scale: Float = 1f, content: @Composable () -> Unit) {
        rule.setContent {
            KPKNTheme {
                host = LocalView.current
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, scale),
                    LocalWizardReducedMotion provides true,
                    LocalAjStatusTop provides 38.dp,
                ) {
                    Box(Modifier.width(360.dp).height(height.dp).background(KpknSurface.canvas).testTag("aj-render")) { content() }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("aj-render").fetchSemanticsNode().boundsInRoot
        val view = host!!
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val crop = Bitmap.createBitmap(bitmap, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(dir, "aj_${id}_360x${height}_${(scale * 100).toInt()}.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        crop.recycle(); bitmap.recycle()
    }

    private val none: () -> Unit = {}
    private fun perms(vararg pairs: Pair<AjPerm, AjPermState>) = mapOf(
        AjPerm.Notifications to AjPermState.GRANTED, AjPerm.Microphone to AjPermState.GRANTED, AjPerm.Location to AjPermState.DENIED,
        AjPerm.Bluetooth to AjPermState.GRANTED, AjPerm.Camera to AjPermState.GRANTED, AjPerm.Alarms to AjPermState.DENIED,
    ) + pairs

    private val allGranted = AjPerm.entries.associateWith { AjPermState.GRANTED }

    /** La hoja del lienzo dibujada sobre el velo, sin el motor de `KpknSheet` (que vive en un portal): mismo fondo, radio, asa y relleno. */
    @Composable
    private fun SheetOver(height: Int, tall: Boolean = false, content: @Composable ColumnScope.() -> Unit) = Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(AjColors.Scrim))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .then(if (tall) Modifier.height((height * 0.82f).dp) else Modifier)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(AjColors.Sheet),
        ) {
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.padding(top = 10.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.227f)))
            }
            AjSheetColumn(Modifier.padding(top = 28.dp).then(if (tall) Modifier.fillMaxSize() else Modifier), content = content)
        }
    }

    @Composable private fun Main(pending: Int = 2, active: Int = 0, unit: WeightUnit = WeightUnit.KG) =
        AjMainScreen(unit, active, pending, true, none, none, none, none, none, none, none, none)

    @Composable private fun Rem(states: Map<AjPerm, AjPermState>, workout: Boolean = false, meals: Boolean = false, sleep: Boolean = false) =
        AjRemindersScreen(
            states.getValue(AjPerm.Notifications), states[AjPerm.Alarms] == AjPermState.GRANTED, workout, "18:00", true, meals,
            "08:00", "13:00", "20:00", sleep, "22:00", none, { _, _ -> }, {}, none, none,
        )

    @Composable private fun Copy() = AjCopyScreen(none, none, none, none, none)
    @Composable private fun Help() = AjHelpScreen("KPKN Pre Release 21 · versión 55", false, none, none, none, none)

    @Test fun main() = render("main") { Main() }
    @Test fun main740() = render("main", 740) { Main() }
    @Test fun main130() = render("main", 740, 1.3f) { Main() }
    @Test fun unidades() = render("unidades") { Main(); SheetOver(880) { AjUnitsContent(WeightUnit.KG) {} } }
    @Test fun recordatorios() = render("recordatorios") { Rem(allGranted, workout = true, meals = true) }
    @Test fun recordatorios130() = render("recordatorios", 740, 1.3f) { Rem(allGranted, workout = true, meals = true) }
    @Test fun recordatoriosVacio() = render("recordatorios-vacio") { Rem(perms()) }
    @Test fun recordatoriosAlarmas() = render("recordatorios-alarmas") { Rem(perms(), workout = true) }
    @Test fun recordatoriosPermiso() = render("recordatorios-permiso") { Rem(perms(AjPerm.Notifications to AjPermState.DENIED, AjPerm.Alarms to AjPermState.GRANTED), workout = true) }
    @Test fun hora() = render("hora") {
        Rem(perms(), workout = true)
        SheetOver(880) { AjTimeContent("Entrenar", 18, 0) { _, _ -> } }
    }
    @Test fun permisos() = render("permisos") { AjPermissionsScreen(perms(), none, {}) }
    @Test fun permisos130() = render("permisos", 740, 1.3f) { AjPermissionsScreen(perms(), none, {}) }
    @Test fun permisoDialogo() = render("permiso-dialogo") {
        AjPermissionsScreen(perms(AjPerm.Microphone to AjPermState.DENIED, AjPerm.Camera to AjPermState.BLOCKED), none, {})
    }
    @Test fun copia() = render("copia") { Copy() }
    @Test fun guardar() = render("guardar") { Copy(); SheetOver(880) { AjSaveContent(AjBackupKind.Json, false, {}, none) } }
    @Test fun guardarError() = render("guardar-error") { Copy(); SheetOver(880) { AjSaveContent(AjBackupKind.Db, false, {}, none) } }
    @Test fun restaurar() = render("restaurar") { Copy(); SheetOver(880) { AjRestoreContent(false, AjBackupKind.Json, "", false, {}, none, none) } }
    @Test fun csv() = render("csv") {
        Copy()
        SheetOver(880, tall = true) { CsvExportContent(setOf(CsvExportDomain.MEALS), {}, false, null, null, none, none, Modifier.weight(1f)) }
    }
    @Test fun csv130() = render("csv", 740, 1.3f) {
        Copy()
        SheetOver(740, tall = true) { CsvExportContent(setOf(CsvExportDomain.MEALS), {}, false, null, null, none, none, Modifier.weight(1f)) }
    }
    @Test fun csvError() = render("csv-error") {
        Copy()
        SheetOver(880, tall = true) {
            CsvExportContent(setOf(CsvExportDomain.MEALS, CsvExportDomain.LIBRARY), {}, false, "No se pudo preparar la exportación: error desconocido", null, none, none, Modifier.weight(1f))
        }
    }
    @Test fun ayuda() = render("ayuda") { Help() }
    @Test fun fuentes() = render("fuentes") {
        Help()
        SheetOver(880, tall = true) { NutritionSourcesContent(null, null, {}, {}, Modifier.weight(1f)) }
    }
    @Test fun fuentesTexto() = render("fuentes-texto") {
        Help()
        SheetOver(880, tall = true) { NutritionSourcesContent(nutritionNotices.first(), "Fuentes de los datos de nutrición de KPKN Fit\n\nEl catálogo de alimentos combina datos abiertos y los adapta.", {}, {}, Modifier.weight(1f)) }
    }
    @Test fun diagnostico() = render("diagnostico") { AjDiagnosticsScreen(null, none, none, none, none, none) }
    @Test fun diagnosticoCarpeta() = render("diagnostico-carpeta") { AjDiagnosticsScreen("Documentos › KPKN", none, none, none, none, none) }
    @Test fun borrar() = render("borrar") { Main(); SheetOver(880) { AjDeleteContent(false, none, none) } }
    @Test fun borrar130() = render("borrar", 740, 1.3f) { Main(); SheetOver(740) { AjDeleteContent(false, none, none) } }
}
