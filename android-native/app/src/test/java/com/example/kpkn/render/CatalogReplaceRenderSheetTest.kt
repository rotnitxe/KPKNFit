package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.sessioneditor.components.CatalogCurrentExerciseLabel
import com.example.kpkn.screens.sessioneditor.components.CatalogReplacementContext
import com.example.kpkn.screens.sessioneditor.components.CatalogTestBuilders
import com.example.kpkn.screens.sessioneditor.components.catalogReplacementAnchorFor
import com.example.kpkn.screens.sessioneditor.components.catalogReplacementCandidateLine
import com.example.kpkn.screens.sessioneditor.components.catalogReplacingCardModel
import com.example.kpkn.screens.sessioneditor.design.CatalogExerciseRow
import com.example.kpkn.screens.sessioneditor.design.CatalogListHeader
import com.example.kpkn.screens.sessioneditor.design.CatalogReplaceBar
import com.example.kpkn.ui.design.KpknType
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
 * HOJA DE DIBUJO del catálogo de reemplazo (H1, LV5): la cabecera con la tarjeta «Reemplazando» y los dos filtros, la lista de candidatos
 * y la barra de abajo con la nota y el botón, con el catálogo REAL (la sentadilla trasera con barra alta y cinco candidatos) y sin elegir
 * nada o con la sentadilla frontal elegida. No comprueba nada: escribe PNG. Pinta a 360 dp, con la letra al 100 % y al 130 %, sin
 * movimiento. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class CatalogReplaceRenderSheetTest {

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

    /** Si la sentadilla frontal está elegida, y la escala de letra. Fuera de la composición para cambiarlos desde la prueba. */
    private var chosen by mutableStateOf(false)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

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

    private val anchorId = "high_bar_back_squat"
    private val chosenId = "front_squat"
    private val candidateIds = listOf(chosenId, "quads_prensa_piernas", "quads_sentadilla_hack", "belt_squat", "bulgarian_split_squat")

    /** La pantalla de reemplazo tal como la pinta el selector, con datos del catálogo real. */
    @Composable
    private fun Scene(isChosen: Boolean) {
        val catalog = CatalogTestBuilders.real
        val definitions = catalog.families.flatMap { it.definitions }.associateBy { it.id }
        val anchorDefinition = definitions.getValue(anchorId)
        val card = catalogReplacingCardModel(
            catalog = catalog,
            context = CatalogReplacementContext(exerciseName = anchorDefinition.canonicalName, planSummary = "4 × 4–6 · RIR 2"),
            definitionId = anchorId,
            configurationId = anchorDefinition.defaultConfigurationId,
        )
        val anchor = catalogReplacementAnchorFor(definitions, anchorId, anchorDefinition.defaultConfigurationId)
        val chosenName = definitions.getValue(chosenId).canonicalName
        Box(Modifier.fillMaxWidth().height(1100.dp)) {
            Column(Modifier.fillMaxWidth()) {
                CatalogListHeader(
                    query = "",
                    onQueryChange = {},
                    onBack = {},
                    targetGroupName = null,
                    filterRegion = null,
                    filterMuscle = null,
                    muscleFilterExpanded = false,
                    muscleIds = emptyList(),
                    onClearFilters = {},
                    onToggleUpper = {},
                    onToggleLower = {},
                    onToggleMuscle = {},
                    onToggleMuscleId = {},
                    isReplacement = true,
                    replacingCard = card,
                    sameMuscleSelected = false,
                    onToggleSameMuscle = {},
                )
                Text(
                    text = "Primero los más parecidos al ejercicio actual",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    (listOf(anchorId) + candidateIds).forEach { id ->
                        val definition = definitions.getValue(id)
                        val default = definition.configurations.first { it.id == definition.defaultConfigurationId }
                        CatalogExerciseRow(
                            name = definition.canonicalName,
                            subtitle = anchor?.let { catalogReplacementCandidateLine(definition, default, it) },
                            matchLabel = if (id == anchorId) CatalogCurrentExerciseLabel else null,
                            thumbnailResId = ExerciseTechniqueImageLookup.resolveImageResId(
                                catalogDefinitionId = id,
                                exerciseDbId = null,
                                exerciseId = id,
                                catalogConfigurationId = default.id,
                                selectedImplementation = null,
                            ),
                            pattern = exercisePatternOf(definition.canonicalName),
                            selected = isChosen && id == chosenId,
                            onClick = {},
                            onToggleSelection = {},
                        )
                        Box(Modifier.fillMaxWidth().height(1.dp).background(WizardColors.divider))
                    }
                }
            }
            CatalogReplaceBar(
                note = "Se mantienen las series del plan: 4 × 4–6 · RIR 2",
                buttonText = if (isChosen) "Reemplazar por $chosenName" else "Reemplazar",
                enabled = isChosen,
                onConfirm = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    @Test
    fun renderCatalogoDeReemplazo() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
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
                        .height(1100.dp)
                        .background(Color.Black),
                ) {
                    Scene(isChosen = chosen)
                }
            }
        }
        for ((name, isChosen) in listOf("sinElegir" to false, "elegido" to true)) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    chosen = isChosen
                    fontScale = scale
                }
                rule.waitForIdle()
                save("sheet", "reemplazar_${name}_$suffix")
            }
        }
    }
}
