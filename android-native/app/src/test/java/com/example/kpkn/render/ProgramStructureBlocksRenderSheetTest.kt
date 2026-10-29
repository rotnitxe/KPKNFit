package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.programdetail.ProgramStructureSamples
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import com.example.kpkn.screens.programdetail.components.MacrocycleEditor
import com.example.kpkn.screens.programdetail.design.ProgramAnchoredMenuItem
import com.example.kpkn.screens.programdetail.design.ProgramItemMenu
import com.example.kpkn.screens.programdetail.design.ProgramWeekEditSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramBlockList
import com.example.kpkn.screens.programdetail.design.structure.ProgramBlockSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramConvertBlocksDialog
import com.example.kpkn.screens.programdetail.design.structure.ProgramDeleteDialog
import com.example.kpkn.screens.programdetail.design.structure.ProgramMesoSheet
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
 * HOJA DE DIBUJO de los bloques de la pestaña Estructura (P3b): un programa de cuatro bloques (uno con dos mesociclos y una semana de
 * competición); un programa simple con ciclo; el menú de una semana con «Pegar aquí»; las hojas de bloque nuevo y de edición, de mesociclo
 * y de semana; los diálogos de conversión y de borrado; y la pestaña Estructura entera con sus módulos de P3a. No comprueba nada: escribe
 * PNG a 360 dp, al 100 % y al 130 % de letra, sin movimiento. Sin `KPKN_RENDER_DIR`, se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class ProgramStructureBlocksRenderSheetTest {

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
        "blocksFour", "simpleCycle", "weekMenu",
        "blockNew", "blockEdit", "mesoEdit", "weekEdit",
        "dialogConvert", "dialogDelete", "structure",
    )

    /** El caso que se dibuja ahora, y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Un programa por bloques de cuatro bloques, con las mismas semanas que los ejemplos de los módulos: el de la pestaña entera. */
    private val structureProgram: Program = ProgramDetailSamples.active.copy(
        structure = ProgramStructure.COMPLEX,
        macrocycles = listOf(
            Macrocycle(
                id = "macro-1",
                name = "Macrociclo 1",
                blocks = listOf(
                    Block(
                        id = "bloque-acumulacion",
                        name = "Acumulación 1",
                        goal = BlockGoal.ACCUMULATION,
                        progressionScheme = BlockProgressionScheme.LINEAR_LOAD,
                        mesocycles = listOf(
                            Mesocycle(id = "meso-1", name = "Mesociclo 1", goal = MesocycleGoal.ACCUMULATION, weeks = weeksOf(1..3)),
                            Mesocycle(id = "meso-2", name = "Mesociclo 2", goal = MesocycleGoal.REALIZATION, weeks = weeksOf(4..6)),
                        ),
                    ),
                    Block(
                        id = "bloque-intensificacion",
                        name = "Intensificación",
                        goal = BlockGoal.INTENSIFICATION,
                        mesocycles = listOf(
                            Mesocycle(id = "meso-3", name = "Mesociclo 1", goal = MesocycleGoal.INTENSIFICATION, weeks = weeksOf(7..10)),
                        ),
                    ),
                    Block(
                        id = "bloque-realizacion",
                        name = "Realización",
                        goal = BlockGoal.REALIZATION,
                        mesocycles = listOf(
                            Mesocycle(id = "meso-4", name = "Mesociclo 1", goal = MesocycleGoal.REALIZATION, weeks = weeksOf(11..13)),
                        ),
                    ),
                    Block(
                        id = "bloque-competicion",
                        name = "Puesta a punto",
                        goal = BlockGoal.TAPER,
                        mesocycles = listOf(
                            Mesocycle(id = "meso-5", name = "Mesociclo 1", goal = MesocycleGoal.DELOAD, weeks = weeksOf(14..15)),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun weeksOf(range: IntRange): List<ProgramWeek> = range.map { ProgramWeek(id = "s$it", name = "Semana $it") }

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

    /** Alto de la caja de cada caso: lo justo para que el contenido quepa. */
    private fun heightOf(name: String): Int = when (name) {
        "blocksFour" -> 1900
        "simpleCycle" -> 640
        "weekMenu" -> 980
        "blockNew" -> 1400
        "blockEdit" -> 1560
        "mesoEdit" -> 820
        "weekEdit" -> 780
        "dialogConvert" -> 600
        "dialogDelete" -> 420
        "structure" -> 3200
        else -> 400
    }

    /** Pinta el caso [name] con los datos de ejemplo. Las acciones no hacen nada: la hoja solo dibuja. */
    @Composable
    private fun Sheet(name: String) {
        val noop: () -> Unit = {}
        val noop1: (String) -> Unit = {}
        val noop2: (String, String) -> Unit = { _, _ -> }
        val noop3: (String, String, Rect) -> Unit = { _, _, _ -> }
        when (name) {
            "blocksFour" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                ProgramBlockList(
                    blocks = ProgramStructureSamples.programaCuatroBloques,
                    expandedBlockId = ProgramStructureSamples.intensificacion.id,
                    progressionOf = { "Top set a RPE 8 y dos series de back-off al 90 %. Sube 2,5 kg cuando el top set sale a RPE 7 o menos." },
                    onToggleBlock = noop1,
                    onFocusWeek = noop2,
                    onWeekLongPress = noop3,
                    onBlockMore = { _, _ -> },
                    onAddWeekToBlock = noop1,
                )
            }
            "simpleCycle" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                ProgramBlockList(
                    blocks = listOf(ProgramStructureSamples.simpleCiclo),
                    expandedBlockId = null,
                    progressionOf = { "Top set a RPE 8 y dos series de back-off al 90 %. Sube 2,5 kg cuando el top set sale a RPE 7 o menos." },
                    onToggleBlock = noop1,
                    onFocusWeek = noop2,
                    onWeekLongPress = noop3,
                    onBlockMore = { _, _ -> },
                    onAddWeekToBlock = noop1,
                )
            }
            "weekMenu" -> Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    ProgramBlockList(
                    blocks = listOf(ProgramStructureSamples.acumulacion),
                    expandedBlockId = null,
                    progressionOf = { "Top set a RPE 8 y dos series de back-off al 90 %. Sube 2,5 kg cuando el top set sale a RPE 7 o menos." },
                    onToggleBlock = noop1,
                    onFocusWeek = noop2,
                    onWeekLongPress = noop3,
                    onBlockMore = { _, _ -> },
                    onAddWeekToBlock = noop1,
                )
                }
                ProgramItemMenu(
                    items = listOf(
                        ProgramAnchoredMenuItem(label = "Nombre y nota de la semana", onClick = noop),
                        ProgramAnchoredMenuItem(label = "Copiar esta semana", onClick = noop),
                        ProgramAnchoredMenuItem(label = "Pegar la semana 2 aquí", onClick = noop),
                        ProgramAnchoredMenuItem(label = "Añadir una semana después", onClick = noop),
                        ProgramAnchoredMenuItem(label = "Eliminar esta semana", onClick = noop, destructive = true),
                    ),
                    anchor = Rect(left = 200f, top = 330f, right = 240f, bottom = 370f),
                    onDismiss = noop,
                )
            }
            "blockNew" -> ProgramBlockSheet(
                block = null,
                subtitle = null,
                suggestedName = "Bloque 5",
                preview = null,
                onSave = { _, _, _, _ -> },
                onDismiss = noop,
            )
            "blockEdit" -> ProgramBlockSheet(
                block = ProgramStructureSamples.bloqueEditable,
                subtitle = "Semanas 4 y 5 · 29 oct – 9 nov",
                suggestedName = "",
                preview = "Carga +2,5 kg cada semana hasta la cuarta.",
                onSave = { _, _, _, _ -> },
                onDismiss = noop,
                onDelete = noop,
            )
            "mesoEdit" -> ProgramMesoSheet(
                meso = ProgramStructureSamples.bloqueEditable.mesocycles[1],
                subtitle = "Acumulación 1",
                suggestedName = "Mesociclo 3",
                canDelete = true,
                onSave = { _, _ -> },
                onDelete = noop,
                onDismiss = noop,
            )
            "weekEdit" -> ProgramWeekEditSheet(
                name = ProgramStructureSamples.semanaEditable.name,
                description = ProgramStructureSamples.semanaEditable.description.orEmpty(),
                onNameChange = noop1,
                onDescriptionChange = noop1,
                onSave = noop,
                onDismiss = noop,
                onDelete = noop,
            )
            "dialogConvert" -> ProgramConvertBlocksDialog(onConfirm = noop, onDismiss = noop)
            "dialogDelete" -> ProgramDeleteDialog(
                text = "Eliminar esta semana quitará sus sesiones.",
                onConfirm = noop,
                onDismiss = noop,
            )
            "structure" -> Column(Modifier.fillMaxWidth()) {
                MacrocycleEditor(
                    program = structureProgram,
                    onUpdateProgram = {},
                    onFocusWeek = noop2,
                    onWeekLongPress = { _, _ -> },
                    onUpdateBlock = { _, _, _, _, _ -> },
                    blockProgressionPreview = { null },
                    onDeleteBlock = noop1,
                    onAddSimpleWeek = noop,
                )
            }
        }
    }

    @Test
    fun renderProgramStructureBlocks() {
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
                    Sheet(cases[current])
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
