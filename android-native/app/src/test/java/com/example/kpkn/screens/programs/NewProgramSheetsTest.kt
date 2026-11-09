package com.example.kpkn.screens.programs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.mutableStateOf
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Lo que «Crear y abrir» de la hoja «Desde cero» entrega a la pantalla. */
private data class BlankCreation(val name: String, val cover: String, val identityLabel: String)

/**
 * Las hojas de «Nuevo programa» (los cuatro caminos y «Desde cero») y la página «Catálogo».
 *
 * Los controles de las hojas se activan con la acción de clic semántica (`OnClick`), la misma que usa TalkBack: en este entorno de
 * prueba el toque táctil no llega a los controles de la hoja, aunque el nodo esté dentro de la ventana (medido con un diagnóstico).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class NewProgramSheetsTest {
    @get:Rule
    val rule = createComposeRule()
    private var hostView: View? = null

    private fun captureCoverBitmap(): Bitmap {
        val view = checkNotNull(hostView)
        val bounds = rule.onNodeWithTag("blank-cover-ONDAS").fetchSemanticsNode().boundsInRoot
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(AndroidColor.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        return Bitmap.createBitmap(full, left, top, width, height)
    }

    private fun saveCoverBitmapIfRequested(name: String, bitmap: Bitmap) {
        val outputDir = System.getenv("KPKN_RENDER_DIR")?.let(::File) ?: return
        outputDir.mkdirs()
        FileOutputStream(File(outputDir, "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun conAsistenteLaHojaOfreceLosCuatroCaminos() {
        rule.setContent {
            NewProgramSheet(onDismiss = {}, onTailored = {}, onCatalog = {}, onImport = {}, onBlank = {})
        }

        rule.onNodeWithText("A tu medida").assertExists()
        rule.onNodeWithText("Del catálogo").assertExists()
        rule.onNodeWithText("Importar").assertExists()
        rule.onNodeWithText("Desde cero").assertExists()
    }

    @Test
    fun sinAsistenteNoSaleLaOpcionAMiMedida() {
        rule.setContent {
            NewProgramSheet(onDismiss = {}, onTailored = null, onCatalog = {}, onImport = {}, onBlank = {})
        }

        rule.onNodeWithText("A tu medida").assertDoesNotExist()
        rule.onNodeWithText("Del catálogo").assertExists()
    }

    @Test
    fun tocarDelCatalogoLlamaAOnCatalog() {
        var abiertos = 0
        rule.setContent {
            NewProgramSheet(onDismiss = {}, onTailored = null, onCatalog = { abiertos++ }, onImport = {}, onBlank = {})
        }

        rule.onNodeWithText("Del catálogo").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, abiertos)
    }

    @Test
    fun desdeCeroRequiereElegirUnaEtiquetaAntesDeCrear() {
        var creado: BlankCreation? = null
        rule.setContent {
            BlankProgramSheet(
                suggestedName = "Nuevo programa 3",
                onDismiss = {},
                onCreate = { name, cover, label -> creado = BlankCreation(name, cover, label) },
            )
        }

        rule.onNodeWithTag("blank-program-create").assertIsNotEnabled()
        rule.onNodeWithText("Hipertrofia").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithTag("blank-program-create").assertIsEnabled()
        rule.onNodeWithTag("blank-program-create").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(
            BlankCreation("Nuevo programa 3", "kpkn-lines://PERFIL/CARBON", "Hipertrofia"),
            creado,
        )
    }

    @Test
    fun desdeCeroPermiteUnaEtiquetaPersonalizada() {
        var creado: BlankCreation? = null
        rule.setContent {
            BlankProgramSheet(
                suggestedName = "Nuevo programa 3",
                onDismiss = {},
                onCreate = { name, cover, label -> creado = BlankCreation(name, cover, label) },
            )
        }

        rule.onNode(hasSetTextAction()).performTextReplacement("Mi fuerza")
        rule.onNodeWithTag("blank-modality-personalizada").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag("blank-program-custom-modality")),
            useUnmergedTree = true,
        ).performTextReplacement("  Escalada  ")
        rule.onNodeWithTag("blank-program-create").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(
            BlankCreation("Mi fuerza", "kpkn-lines://PERFIL/CARBON", "Escalada"),
            creado,
        )
    }

    @Test
    fun laMiniaturaYElProgramaNuevoSiguenElFondoElegido() {
        var creado: BlankCreation? = null
        rule.setContent {
            BlankProgramSheet(
                suggestedName = "Nuevo programa 3",
                onDismiss = {},
                onCreate = { name, cover, label -> creado = BlankCreation(name, cover, label) },
            )
        }

        rule.onNodeWithTag("blank-cover-bg-noche").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithTag("blank-cover-ONDAS").assertContentDescriptionEquals("Portada Ondas, fondo Noche")
        rule.onNodeWithText("Hipertrofia").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithTag("blank-program-create").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(BlankCreation("Nuevo programa 3", "kpkn-lines://PERFIL/NOCHE", "Hipertrofia"), creado)
    }

    @Test
    fun laMiniaturaDePortadaDibujaElFondoElegido() {
        val background = mutableStateOf(LineCoverBackground.CARBON)
        rule.setContent {
            hostView = LocalView.current
            BlankCoverChoice(
                style = LineCoverStyle.ONDAS,
                background = background.value,
                selected = true,
                onSelect = {},
            )
        }

        rule.waitForIdle()
        val carbonPreview = captureCoverBitmap()
        saveCoverBitmapIfRequested("blank-cover-carbon", carbonPreview)
        rule.runOnIdle { background.value = LineCoverBackground.NOCHE }
        rule.waitForIdle()
        val nightPreview = captureCoverBitmap()
        saveCoverBitmapIfRequested("blank-cover-night", nightPreview)
        var changedPixels = 0
        var sampledPixels = 0
        for (y in 12 until carbonPreview.height - 12) {
            for (x in 12 until carbonPreview.width - 12) {
                val before = carbonPreview.getPixel(x, y)
                val after = nightPreview.getPixel(x, y)
                val colorDelta = abs(AndroidColor.red(before) - AndroidColor.red(after)) +
                    abs(AndroidColor.green(before) - AndroidColor.green(after)) +
                    abs(AndroidColor.blue(before) - AndroidColor.blue(after))
                if (colorDelta > 16) changedPixels++
                sampledPixels++
            }
        }
        assertTrue("la miniatura cambia de píxeles al elegir Noche", changedPixels > sampledPixels * 0.35f)
    }

    /** La página «Catálogo» enseña su título y la primera tarjeta de la biblioteca, con su etiqueta. */
    @Test
    fun elCatalogoMuestraSuTituloYSuPrimerPlan() {
        val primero = libraryOrder(
            PersonalizedPlanCatalog.listedEntries()
                .filter { it.publication == PublicationState.PUBLISHED && isLibraryFamilyEntry(it) },
        ).first()
        rule.setContent {
            ProgramCatalogPage(
                onBack = {},
                hasPlanAction = true,
                onCreateFromTemplate = {},
                onSelectProtocol = {},
                onSelectPlan = {},
                onOpenConcept = null,
            )
        }

        rule.onNodeWithText("Catálogo").assertExists()
        rule.onNodeWithTag("catalog-plan-${primero.id}").assertExists()
    }
}
