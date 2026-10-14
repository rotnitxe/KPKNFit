package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import com.example.kpkn.screens.programdetail.design.split.SplitApplySheet
import com.example.kpkn.screens.programdetail.design.split.SplitCatalogMode
import com.example.kpkn.screens.programdetail.design.split.SplitCatalogScreen
import com.example.kpkn.screens.programdetail.design.split.SplitCompareSheet
import com.example.kpkn.screens.programdetail.design.split.SplitCustomEditorSheet
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * HOJA DE DIBUJO de «Cambiar reparto» (P5): la página con el reparto actual y el catálogo; la página con dos repartos elegidos para
 * comparar; un reparto sin plantilla para algún día; la hoja «Comparar»; la hoja de aplicar con impacto destructivo y con la elección
 * de semanas; y el editor del reparto propio, cerrado y con un día desplegado. Escribe PNG a 360 dp, con la letra al 100 % y al 130 %.
 * No comprueba nada: solo corre con `KPKN_RENDER_DIR` y sin ella se salta.
 *
 * Los datos son de ejemplo: el catálogo es el real; el motivo de «no disponible» es un texto de muestra, no un cálculo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2600dp-xxhdpi", application = android.app.Application::class)
class SplitViewRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Los casos de la hoja, en orden. */
    private val cases = listOf(
        "pagina",
        "paginaComparar",
        "noDisponible",
        "comparar",
        "aplicarImpacto",
        "aplicarSemanas",
        "editor",
        "editorDesplegado",
    )

    /** El caso que se dibuja ahora (-1 = nada) y la escala de letra. */
    private var current by mutableStateOf(-1)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private val ulX4: SplitTemplate = SPLIT_TEMPLATES.first { it.id == "ul_x4" }
    private val pplUl: SplitTemplate = SPLIT_TEMPLATES.first { it.id == "ppl_ul" }
    private val fullBody: SplitTemplate = SPLIT_TEMPLATES.first { it.id == "fullbody_x3" }
    private val pplX6: SplitTemplate = SPLIT_TEMPLATES.first { it.id == "ppl_x6" }
    private val broSplit: SplitTemplate = SPLIT_TEMPLATES.first { it.id == "bro_split" }

    /** El catálogo de la página: cuatro repartos de los que se publican. */
    private val catalog: List<SplitTemplate> = listOf(ulX4, pplUl, fullBody, pplX6)

    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        // Sin el árbol sin fusionar, las etiquetas dentro de una hoja (que es un clicable) no se encuentran.
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Alto del lienzo de cada caso, en dp. */
    private fun heightOf(name: String): Int = when (name) {
        "pagina", "paginaComparar" -> 2600
        "noDisponible" -> 1100
        "comparar" -> 1500
        "aplicarImpacto", "aplicarSemanas" -> 2100
        "editor", "editorDesplegado" -> 2100
        else -> 1800
    }

    /** La etiqueta de prueba que recorta cada caso. */
    private fun tagOf(name: String): String = when (name) {
        "comparar" -> "split_comparar"
        "aplicarImpacto", "aplicarSemanas" -> "split_aplicar"
        "editor", "editorDesplegado" -> "split_editor"
        else -> "split_raiz"
    }

    /** La interacción que se hace antes de capturar, si la hay (el paso que mostraría el caso). */
    private fun actionOf(name: String): (() -> Unit)? = when (name) {
        "aplicarImpacto" -> { { rule.onNodeWithText("Sesiones vacías").performClick() } }
        "aplicarSemanas" -> { { rule.onNodeWithText("Varias").performClick() } }
        "editorDesplegado" -> { { rule.onAllNodesWithText("Descanso").onFirst().performClick() } }
        else -> null
    }

    /** Página del catálogo con los datos de muestra; los parámetros cambian solo lo que cada caso muestra. */
    @Composable
    private fun Page(
        splits: List<SplitTemplate>,
        mode: SplitCatalogMode,
        selectedIds: List<String>,
        unavailableReason: (SplitTemplate) -> String?,
    ) {
        SplitCatalogScreen(
            current = ulX4,
            startDay = 1,
            query = "",
            onQueryChange = {},
            selectedTag = null,
            onSelectTag = {},
            splits = splits,
            customSplit = null,
            unavailableReason = unavailableReason,
            isCurrent = { split -> split.id == ulX4.id },
            mode = mode,
            selectedIds = selectedIds,
            onBack = {},
            onToggleCompareMode = {},
            onToggleMultiMode = {},
            onApply = {},
            onToggleSelect = {},
            onEditCustom = {},
            onApplyCustom = {},
            onCompare = {},
            onApplySelected = {},
        )
    }

    /** Pinta el caso [name]. */
    @Composable
    private fun Case(name: String) {
        val program = ProgramDetailSamples.splitProgram(weeks = 1)
        when (name) {
            "pagina" -> Page(
                splits = catalog,
                mode = SplitCatalogMode.BROWSE,
                selectedIds = emptyList(),
                unavailableReason = { null },
            )
            "paginaComparar" -> Page(
                splits = catalog,
                mode = SplitCatalogMode.COMPARE,
                selectedIds = listOf(ulX4.id, pplUl.id),
                unavailableReason = { null },
            )
            "noDisponible" -> Page(
                splits = listOf(broSplit),
                mode = SplitCatalogMode.BROWSE,
                selectedIds = emptyList(),
                unavailableReason = { "Piernas: sin plantilla compatible" },
            )
            "comparar" -> SplitCompareSheet(
                first = ulX4,
                second = pplUl,
                startDay = 1,
                onDismiss = {},
            )
            "aplicarImpacto" -> SplitApplySheet(
                program = program,
                selectedSplit = pplUl,
                blocks = SplitApplicationEngine.buildBlockOptions(program),
                weeks = SplitApplicationEngine.buildWeekOptions(program),
                selectedBlockId = "split-block",
                selectedWeekId = "split-w1",
                defaultStartDay = 1,
                isAdvancedProgram = false,
                totalWeeks = 1,
                generationTemplates = SESSION_TEMPLATES_SYSTEM,
                onDismiss = {},
                onApply = {},
            )
            "aplicarSemanas" -> {
                val threeWeeks = ProgramDetailSamples.splitProgram(weeks = 3)
                SplitApplySheet(
                    program = threeWeeks,
                    selectedSplit = pplUl,
                    blocks = SplitApplicationEngine.buildBlockOptions(threeWeeks),
                    weeks = SplitApplicationEngine.buildWeekOptions(threeWeeks),
                    selectedBlockId = "split-block",
                    selectedWeekId = "split-w1",
                    defaultStartDay = 1,
                    isAdvancedProgram = false,
                    totalWeeks = 3,
                    generationTemplates = SESSION_TEMPLATES_SYSTEM,
                    onDismiss = {},
                    onApply = {},
                )
            }
            "editor", "editorDesplegado" -> SplitCustomEditorSheet(
                program = program.copy(
                    customSplitName = "Mi reparto de prueba",
                    customSplitPattern = ProgramDetailSamples.splitCustomPattern,
                ),
                defaultStartDay = 1,
                onDismiss = {},
                onApply = { _, _ -> },
            )
        }
    }

    @Test
    fun renderSplitViewSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                Box(
                    Modifier
                        .testTag("split_host")
                        .width(360.dp)
                        .height(if (current >= 0) heightOf(cases[current]).dp else 200.dp)
                        .background(Color.Black),
                ) {
                    if (current >= 0) Case(cases[current])
                }
            }
        }
        for ((index, name) in cases.withIndex()) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                // Primero se vacía el lienzo: así cada captura parte de una composición nueva y no hereda los toques anteriores.
                rule.runOnUiThread {
                    current = -1
                    fontScale = scale
                }
                rule.waitForIdle()
                rule.runOnUiThread { current = index }
                rule.waitForIdle()
                actionOf(name)?.invoke()
                rule.waitForIdle()
                save(tagOf(name), "${name}_$suffix")
            }
        }
    }
}
