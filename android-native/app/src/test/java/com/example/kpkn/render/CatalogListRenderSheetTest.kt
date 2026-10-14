package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.sessioneditor.components.exerciseCatalogVariantTagLabel
import com.example.kpkn.screens.sessioneditor.design.CatalogExerciseRow
import com.example.kpkn.screens.sessioneditor.design.CatalogListHeader
import com.example.kpkn.screens.sessioneditor.design.CatalogListSkeleton
import com.example.kpkn.screens.sessioneditor.design.CatalogNoResults
import com.example.kpkn.screens.sessioneditor.design.CatalogSelectionIsland
import com.example.kpkn.screens.sessioneditor.design.CatalogVariantChips
import com.example.kpkn.screens.sessioneditor.design.catalogSelectionSummary
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
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
 * HOJA DE DIBUJO de la lista del catálogo (SE6a): la cabecera con un filtro activo, seis filas (con imagen y con glifo, una con variantes
 * desplegadas y dos elegidas), la isla con dos elegidos, la carga y el «sin resultados». No comprueba nada: escribe PNG. Pinta a 360 dp,
 * con la letra al 100 % y al 130 %, sin movimiento. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class CatalogListRenderSheetTest {

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
    private val cases = listOf("cabecera", "filas", "isla", "cargando", "sinResultados")

    /** El caso que se dibuja ahora, y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Alto de cada caso: la lista de filas es la más alta. */
    private fun heightOf(name: String): Int = when (name) {
        "cabecera" -> 300
        "filas" -> 900
        "isla" -> 820
        "cargando" -> 640
        else -> 560
    }

    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
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

    /** Una fila de ejemplo. [image] es la miniatura real del catálogo o nulo para el glifo del patrón. */
    private data class SampleRow(
        val name: String,
        val subtitle: String,
        val match: String?,
        val image: Int?,
        val selected: Boolean,
        val variants: Boolean = false,
    )

    private val sampleRows: List<SampleRow> by lazy {
        val benchImage = ExerciseTechniqueImageLookup.variants(ExerciseTechniqueImageLookup.FLAT_BENCH_PRESS_DEFINITION_ID)
            .firstOrNull()?.imageResId
        val curlImage = ExerciseTechniqueImageLookup.variants(ExerciseTechniqueImageLookup.STANDING_BICEPS_CURL_DEFINITION_ID)
            .firstOrNull()?.imageResId
        listOf(
            SampleRow("Press banca", "Pectorales · Barra", null, benchImage, selected = true),
            SampleRow("Remo con barra", "Dorsales · Barra", null, null, selected = true),
            SampleRow("Sentadilla búlgara", "Cuádriceps · Mancuernas", null, null, selected = false),
            SampleRow("Curl de bíceps de pie", "Bíceps · Barra", null, curlImage, selected = false),
            SampleRow("Press militar", "Deltoides · Barra", null, null, selected = false, variants = true),
            SampleRow("Dominadas", "Dorsales · Peso corporal", "Peso corporal · Agarre prono", null, selected = false),
        )
    }

    /** La cabecera de la página completa, con el filtro de Pectorales activo. Sin estado: todo llega por parámetros. */
    @Composable
    private fun PageHeader(query: String) {
        CatalogListHeader(
            query = query,
            onQueryChange = {},
            onBack = {},
            targetGroupName = null,
            filterRegion = null,
            filterMuscle = "pectoralis",
            muscleFilterExpanded = false,
            muscleIds = listOf("pectoralis", "latissimus_dorsi", "deltoid", "biceps", "triceps"),
            onClearFilters = {},
            onToggleUpper = {},
            onToggleLower = {},
            onToggleMuscle = {},
            onToggleMuscleId = {},
        )
    }

    /** Las filas de ejemplo; la que tiene variantes las trae desplegadas. */
    @Composable
    private fun SampleList(rows: List<SampleRow>, bottomPadding: Dp = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = bottomPadding)) {
            Text("Todos los ejercicios", style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.padding(vertical = 8.dp))
            rows.forEach { row ->
                CatalogExerciseRow(
                    name = row.name,
                    subtitle = row.subtitle,
                    matchLabel = row.match,
                    thumbnailResId = row.image,
                    pattern = exercisePatternOf(row.name),
                    selected = row.selected,
                    onClick = {},
                    onToggleSelection = {},
                    expandable = row.variants,
                    expanded = row.variants,
                )
                if (row.variants) {
                    CatalogVariantChips(
                        values = listOf("barbell", "smith_machine", "dumbbells"),
                        labelOf = { exerciseCatalogVariantTagLabel(it, null) },
                        selectedValue = "barbell",
                        onSelect = {},
                        onOpenFicha = {},
                    )
                }
                // Separador de 1 dp al pie de cada fila, como en la lista real.
                Box(Modifier.fillMaxWidth().height(1.dp).background(WizardColors.divider))
            }
        }
    }

    /** El caso [name], con sus datos de ejemplo. */
    @Composable
    private fun Case(name: String) {
        val haze = remember { HazeState() }
        when (name) {
            "cabecera" -> Column(Modifier.fillMaxWidth()) {
                PageHeader(query = "")
            }
            "filas" -> Column(Modifier.fillMaxWidth()) {
                PageHeader(query = "")
                SampleList(sampleRows)
            }
            "isla" -> Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth()) {
                    PageHeader(query = "")
                    SampleList(sampleRows.filter { !it.variants }, bottomPadding = 120.dp)
                }
                CatalogSelectionIsland(
                    count = 2,
                    summary = catalogSelectionSummary(listOf("Press banca", "Remo con barra")),
                    listExpanded = false,
                    onToggleList = {},
                    onAdd = {},
                    onSuperset = {},
                    hazeState = haze,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("catalog_island"),
                )
            }
            "cargando" -> Column(Modifier.fillMaxWidth()) {
                PageHeader(query = "")
                CatalogListSkeleton(modifier = Modifier.padding(horizontal = 16.dp))
            }
            else -> Column(Modifier.fillMaxWidth()) {
                PageHeader(query = "Zancada de caballo")
                CatalogNoResults(
                    query = "Zancada de caballo",
                    creating = false,
                    onCreateClick = {},
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }

    @Test
    fun renderCatalogList() {
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
                        .testTag("sheet")
                        .width(360.dp)
                        .height(heightOf(cases[current]).dp)
                        .background(Color.Black),
                ) {
                    Case(cases[current])
                }
            }
        }
        for ((index, name) in cases.withIndex()) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    current = index
                    fontScale = scale
                }
                rule.waitForIdle()
                save("sheet", "${name}_$suffix")
            }
        }
    }
}
