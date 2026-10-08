package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.wizardPageCopy
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import androidx.compose.material3.Text
import com.example.kpkn.screens.onboarding.design.WizardSummaryRow
import com.example.kpkn.screens.onboarding.design.entreno.FreshDayRow
import com.example.kpkn.screens.onboarding.design.entreno.WeekCalendar
import com.example.kpkn.screens.onboarding.design.entreno.layout.SplitOption
import com.example.kpkn.screens.onboarding.design.entreno.layout.WeekLayoutBoardContent
import com.example.kpkn.screens.onboarding.design.entreno.layout.WeekLayoutDragState
import com.example.kpkn.screens.onboarding.design.entreno.layout.WeekLayoutSession
import com.example.kpkn.screens.onboarding.design.entreno.layout.slotOrder
import com.example.kpkn.screens.onboarding.design.entreno.layout.splitPatternDays
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
 * HOJAS DE DIBUJO para revisar el diseño sin teléfono (no comprueba nada: escribe PNG). Solo corre con la variable de entorno
 * `KPKN_RENDER_DIR` apuntando a una carpeta; sin ella se salta. Usa los gráficos nativos de Robolectric: las tipografías reales de
 * la app (Syne e Inter), el ancho del teléfono de pruebas (1080 px a 480 dpi = 360 dp; la pantalla es más alta para que quepa la hoja entera) y la letra al 100 y al 130 %.
 * No dibuja el desenfoque de pantalla ni la GPU: es para ver composición, medidas y textos, no para medir fluidez.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class WizardRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase entera se salta ANTES de arrancar el entorno de Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private data class Case(
        val name: String,
        val days: Int = 4,
        val scale: Float = 1f,
        val width: Float = 360f,
        val places: Boolean = false,
        val mode: String = "idle",
        val start: Int = 1,
    )

    private val sessionTitles = listOf(
        "Torso A" to "Pecho, espalda y hombros",
        "Pierna A" to "Cuádriceps y glúteos",
        "Torso B" to "Hombros y brazos",
        "Pierna B" to "Femoral y gemelos",
        "Empuje" to "Pecho, hombros y tríceps",
        "Tirón" to "Espalda y bíceps",
        "Core y movilidad" to "Abdomen y movilidad",
    )

    private fun sessionsFor(case: Case): List<WeekLayoutSession> = List(case.days) { i ->
        val (title, focus) = sessionTitles[i % sessionTitles.size]
        val place = if (case.places) TrainingPlace.entries[i % 2] else null
        WeekLayoutSession(
            id = "s${i + 1}",
            title = title,
            focus = if (place != null) "$focus · ${place.label}" else focus,
            minutes = 55 + 5 * (i % 3),
            exerciseCount = 5 + (i % 3),
            isMain = i == 0,
        )
    }

    private fun assignmentFor(case: Case, sessions: List<WeekLayoutSession>): Map<Int, String> {
        val days = splitPatternDays(case.days, case.start, emptyList())
        return sessions.mapIndexed { index, s -> days[index] to s.id }.toMap()
    }

    private val splits = listOf(
        SplitOption("tp", "Torso y pierna", "Alterna torso y pierna", listOf("Torso", "Pierna", "Torso", "Pierna")),
        SplitOption("ppl", "Empuje, tirón, pierna", "Tres sesiones que rotan", listOf("Empuje", "Tirón", "Pierna", "Empuje")),
        SplitOption("fb", "Cuerpo completo", "Todo el cuerpo cada día", listOf("A", "B", "A", "B")),
    )

    private var current by mutableStateOf(Case("none"))

    /** El estado de arrastre de cada caso: fuera de la composición para poder dirigirlo desde la prueba, como hace el gesto. */
    private val states = HashMap<String, WeekLayoutDragState>()

    /** La vista de Compose de la actividad, para dibujarla a un bitmap (`captureToImage` espera un pase de dibujo que Robolectric no hace). */
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

    @Test
    fun renderTheBoardSheets() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        val cases = buildList {
            for (days in listOf(3, 4, 5, 7)) {
                add(Case("board_d${days}_s100", days = days))
                add(Case("board_d${days}_s130", days = days, scale = 1.3f))
            }
            add(Case("board_d7_w320_s100", days = 7, width = 320f))
            add(Case("board_d7_w320_s130", days = 7, width = 320f, scale = 1.3f))
            add(Case("board_d4_places_s100", days = 4, places = true))
            add(Case("board_d4_places_s130", days = 4, places = true, scale = 1.3f))
            add(Case("board_d4_lifted_s100", days = 4, mode = "lifted"))
            add(Case("board_d4_selected_s100", days = 4, mode = "selected"))
            add(Case("board_d4_start_thu_s100", days = 4, start = 4))
        }
        rule.setContent {
            val case = current
            val base = LocalDensity.current
            hostView = LocalView.current
            val state = states.getOrPut(case.name) { WeekLayoutDragState() }
            val sessions = remember(case) { sessionsFor(case) }
            val assignment = remember(case) { assignmentFor(case, sessions) }
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = case.scale)) {
                Box(Modifier.testTag("sheet").wrapContentSize().background(Color.Black)) {
                    Box(Modifier.width(case.width.dp).padding(horizontal = 24.dp, vertical = 16.dp)) {
                        WeekLayoutBoardContent(
                            weekStartDay = case.start,
                            sessions = sessions,
                            assignment = assignment,
                            onMove = { _, _ -> },
                            splitOptions = splits.filter { it.dayTitles.isNotEmpty() },
                            selectedSplitId = "tp",
                            onAdaptSplit = {},
                            canReset = true,
                            onReset = {},
                            adapting = false,
                            splitNotice = null,
                            reduced = true,
                            state = state,
                        )
                    }
                }
            }
        }
        for (case in cases) {
            rule.runOnUiThread { current = case }
            rule.waitForIdle()
            val state = states.getValue(case.name)
            if (case.mode == "lifted") {
                // Se levanta la primera sesión y se la pasa sobre la segunda (vista previa del intercambio), con el mismo estado que el gesto.
                rule.runOnUiThread {
                    val g = state.geometry
                    val glyph = Offset(24f * 3f, g.rowHeight / 2f)
                    state.lift("s1", g.home(1) + glyph)
                    state.dragTo(g.home(2) + glyph + Offset(0f, 6f))
                }
                rule.waitForIdle()
            }
            if (case.mode == "selected") {
                rule.runOnUiThread { state.selectedId = "s1" }
                rule.waitForIdle()
            }
            save("sheet", case.name)
        }
    }

    // ── Las filas-resumen con textos reales ──────────────────────────────────────

    private val summaryRows = listOf(
        "Dónde entrenas" to "Gimnasio, casa y espacios públicos",
        "Material" to "Barra y discos, Rack +13",
        "Objetivo" to "Fuerza y masa muscular",
        "Día con más energía" to "Miércoles",
        "Días de entreno" to "6 días · lun, mar, mié, jue, vie, sáb",
        "Tiempo por sesión" to "75 min",
        "Ejercicios que te salen" to "Dominadas, Flexiones +2",
        "Músculos a mejorar" to "Pecho, Espalda +1",
        "Marcas" to "Sentadilla 120 kg, Press banca 80 kg",
        "Marcas" to "Sentadilla, Press banca +1",
        "Programa" to "Fuerza y masa muscular a medida",
        "Programa" to "Powerlifting a medida con un nombre larguísimo que no cabe en dos líneas de ninguna manera",
        "Tu semana" to "4 sesiones por semana",
    )

    private var summaryScale by mutableStateOf(1f)

    @Test
    fun renderTheSummaryRowSheets() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = summaryScale)) {
                Column(Modifier.testTag("rows").width(360.dp).background(Color.Black)) {
                    for ((label, value) in summaryRows) {
                        WizardSummaryRow(label = label, value = value, onClick = {}, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        for ((name, scale) in listOf("summary_s100" to 1f, "summary_s130" to 1.3f)) {
            rule.runOnUiThread { summaryScale = scale }
            rule.waitForIdle()
            save("rows", name)
        }
    }

    // ── El calendario y el día con más energía («Mi» en lugar de la X) ─────────────

    private var calendarCase by mutableStateOf(Triple("none", 1f, 360f))

    @Test
    fun renderTheCalendarSheets() {
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            val (_, scale, width) = calendarCase
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = scale)) {
                Column(Modifier.testTag("calendar").width(width.dp).background(Color.Black).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    FreshDayRow(selectedDay = 3, onSelect = {})
                    WeekCalendar(
                        weekStartDay = 3,
                        selectedDays = setOf(1, 3, 5, 7),
                        freshestDay = 3,
                        onToggleDay = {},
                        onWeekStartChange = {},
                        places = setOf(TrainingPlace.GYM, TrainingPlace.HOME),
                        dayPlaces = mapOf(5 to TrainingPlace.HOME),
                        onDayPlace = { _, _ -> },
                    )
                }
            }
        }
        for ((name, scale, width) in listOf(
            Triple("calendar_s100", 1f, 360f), Triple("calendar_s130", 1.3f, 360f), Triple("calendar_w320_s130", 1.3f, 320f),
        )) {
            rule.runOnUiThread { calendarCase = Triple(name, scale, width) }
            rule.waitForIdle()
            save("calendar", name)
        }
    }

    // ── Los títulos de pregunta del bloque a 312 dp de página (360 dp de pantalla) ─────

    private var titleScale by mutableStateOf(1f)

    @Test
    fun renderTheTitleSheets() {
        val steps = listOf(
            SetupStepId.EQUIPMENT, SetupStepId.AVAILABILITY, SetupStepId.GOAL, SetupStepId.FRESH_DAY, SetupStepId.WEEKDAYS,
            SetupStepId.SESSION_TIME, SetupStepId.CAPABILITIES, SetupStepId.PRIORITIES, SetupStepId.TRAINING_MAX, SetupStepId.WEEK_LAYOUT,
        )
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = titleScale)) {
                Column(Modifier.testTag("titles").width(360.dp).background(Color.Black).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    for (step in steps) {
                        val copy = wizardPageCopy(step)
                        Text(copy.title, style = WizardTypography.stepTitle, color = WizardColors.text)
                        Text(copy.subtitle.orEmpty(), style = WizardTypography.stepSubtitle, color = WizardColors.textMuted, modifier = Modifier.padding(bottom = 20.dp))
                    }
                }
            }
        }
        for ((name, scale) in listOf("titles_s100" to 1f, "titles_s130" to 1.3f)) {
            rule.runOnUiThread { titleScale = scale }
            rule.waitForIdle()
            save("titles", name)
        }
    }
}
