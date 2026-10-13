package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.ProgramStructureSamples
import com.example.kpkn.screens.programdetail.design.structure.CALENDAR_ROW_START
import com.example.kpkn.screens.programdetail.design.structure.ProgramCalendarModule
import com.example.kpkn.screens.programdetail.design.structure.ProgramCalendarSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramKeyDateSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramSimpleCalendarSheet
import com.example.kpkn.screens.programdetail.design.structure.ProgramStructureNotice
import com.example.kpkn.screens.programdetail.design.structure.ProgramToolsKind
import com.example.kpkn.screens.programdetail.design.structure.ProgramToolsModule
import com.example.kpkn.ui.components.icons.KpknLineIcons
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
import java.time.LocalDate

/**
 * HOJA DE DIBUJO de la pestaña Estructura (P3a): las herramientas de un programa por bloques, de uno simple cíclico y de uno calendarizado;
 * el calendario con competición a 41 días, sin inicio y con avisos del motor; la hoja del calendario con una fila desplegada y dos fechas
 * clave; la fecha clave nueva y al editar; la calendarización simple en sus dos estados; el aviso de estructura temporal; y los cuatro
 * iconos nuevos a 48 dp. No comprueba nada: escribe PNG a 360 dp, al 100 % y al 130 % de letra, sin movimiento. Sin `KPKN_RENDER_DIR`, se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class ProgramStructureTopRenderSheetTest {

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
        "toolsBlocks", "toolsCyclic", "toolsCalendarized",
        "calendarCompetition", "sheetCalendar",
        "keyDateNew", "keyDateEdit",
        "simpleCyclic", "simpleCalendarized",
        "notice", "icons",
    )

    /** El caso que se dibuja ahora, y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
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

    /** Alto de la caja de cada caso: lo justo para que el contenido de la hoja quepa. */
    private fun heightOf(name: String): Int = when (name) {
        "toolsBlocks" -> 520
        "toolsCyclic" -> 340
        "toolsCalendarized" -> 300
        "calendarCompetition" -> 620
        "sheetCalendar" -> 1600
        "keyDateNew", "keyDateEdit" -> 820
        "simpleCyclic" -> 900
        "simpleCalendarized" -> 520
        "notice" -> 200
        else -> 160
    }

    /** Pinta el caso [name] con los datos de ejemplo de `ProgramStructureSamples`, con hoy fijo. */
    @Composable
    private fun Sheet(name: String) {
        val today = ProgramStructureSamples.today
        val noop: () -> Unit = {}
        val competitionIn41Days = today.plusDays(41)
        when (name) {
            "toolsBlocks" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                ProgramToolsModule(
                    kind = ProgramToolsKind.BLOCKS, blockCount = 4, weekCount = 11, sessionCount = 44,
                    keyDateCount = 2, historyCount = 1,
                    onCalendar = noop, onKeyDates = noop, onRoute = noop, onCycles = noop,
                    onTemplates = noop, onHistory = noop, onCalendarize = noop,
                )
            }
            "toolsCyclic" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                ProgramToolsModule(
                    kind = ProgramToolsKind.CYCLIC, blockCount = 1, weekCount = 3, sessionCount = 9,
                    keyDateCount = 0, historyCount = 0,
                    onCalendar = noop, onKeyDates = noop, onRoute = noop, onCycles = noop,
                    onTemplates = noop, onHistory = noop, onCalendarize = noop,
                )
            }
            "toolsCalendarized" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                ProgramToolsModule(
                    kind = ProgramToolsKind.CALENDARIZED, blockCount = 1, weekCount = 12, sessionCount = 36,
                    keyDateCount = 0, historyCount = 2,
                    onCalendar = noop, onKeyDates = noop, onRoute = noop, onCycles = noop,
                    onTemplates = noop, onHistory = noop, onCalendarize = noop,
                )
            }
            "calendarCompetition" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                ProgramCalendarModule(
                    kind = ProgramToolsKind.BLOCKS,
                    today = today,
                    startDate = null,
                    endDate = null,
                    endIsEstimate = false,
                    competitionDate = competitionIn41Days,
                    trainingDays = setOf(1, 3, 5),
                    repeatingWeekLabel = "",
                    durationWeeks = 0,
                    messages = ProgramStructureSamples.engineMessages,
                    onEdit = noop,
                    onCalendarize = noop,
                )
            }
            "sheetCalendar" -> ProgramCalendarSheet(
                today = today,
                timelineStartDate = "2026-10-06",
                competitionDate = "2026-11-23",
                manualEndDate = "",
                projectedEnd = LocalDate.of(2027, 1, 25),
                blockRanges = ProgramStructureSamples.blockRanges,
                messages = emptyList(),
                otherKeyDates = ProgramStructureSamples.keyDates,
                focusKeyDates = false,
                canSave = true,
                hasRequiredStart = true,
                onTimelineStartDateChange = {},
                onCompetitionDateChange = {},
                onManualEndDateChange = {},
                onAddOtherKeyDate = noop,
                onEditOtherKeyDate = {},
                onDeleteOtherKeyDate = {},
                onSave = noop,
                onDismiss = noop,
                initialOpenRow = CALENDAR_ROW_START,
            )
            "keyDateNew" -> ProgramKeyDateSheet(
                keyDate = com.example.kpkn.data.models.ProgramKeyDate(
                    id = "key_new",
                    title = "",
                    type = KeyDateType.EXAMS,
                    startDate = today.toString(),
                ),
                isNew = true,
                today = today,
                onSave = {},
                onDelete = noop,
                onDismiss = noop,
            )
            "keyDateEdit" -> ProgramKeyDateSheet(
                keyDate = ProgramStructureSamples.keyDates.first().copy(notes = "Temas del 1 al 6: ácidos, bases y equilibrios."),
                isNew = false,
                today = today,
                onSave = {},
                onDelete = noop,
                onDismiss = noop,
            )
            "simpleCyclic" -> ProgramSimpleCalendarSheet(
                today = today,
                isCalendarized = false,
                startDate = "2026-10-19",
                endDate = "2026-12-13",
                startDayOfWeek = 1,
                trainingDays = setOf(1, 3, 5),
                hasSavedCycle = false,
                onStartDateChange = {},
                onEndDateChange = {},
                onStartDayOfWeekChange = {},
                onTrainingDaysChange = {},
                onStartBreak = noop,
                onCalendarizeCycle = noop,
                onRecoverCycle = noop,
                onStartFreshCycle = noop,
                onDismiss = noop,
                hazeState = remember { HazeState() },
            )
            "simpleCalendarized" -> ProgramSimpleCalendarSheet(
                today = today,
                isCalendarized = true,
                startDate = "2026-10-19",
                endDate = "2026-12-13",
                startDayOfWeek = 1,
                trainingDays = setOf(1, 3, 5),
                hasSavedCycle = true,
                onStartDateChange = {},
                onEndDateChange = {},
                onStartDayOfWeekChange = {},
                onTrainingDaysChange = {},
                onStartBreak = noop,
                onCalendarizeCycle = noop,
                onRecoverCycle = noop,
                onStartFreshCycle = noop,
                onDismiss = noop,
                hazeState = remember { HazeState() },
            )
            "notice" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                ProgramStructureNotice(messages = ProgramStructureSamples.engineMessages)
            }
            "icons" -> Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    NewIcon(KpknLineIcons.Route, "Ruta")
                    NewIcon(KpknLineIcons.Layers, "Plantillas")
                    NewIcon(KpknLineIcons.History, "Historial")
                    NewIcon(KpknLineIcons.Trash, "Eliminar")
                }
            }
        }
    }

    /** Un icono nuevo a 48 dp con su nombre debajo, para revisar la retícula. */
    @Composable
    private fun NewIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = WizardColors.text,
            )
            Spacer(Modifier.height(6.dp))
            Text(text = label, style = KpknType.caption, color = WizardColors.textMuted)
        }
    }

    @Test
    fun renderProgramStructureTopSheets() {
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
