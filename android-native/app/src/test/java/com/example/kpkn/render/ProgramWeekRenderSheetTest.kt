package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.programdetail.NativeProgressionCardUi
import com.example.kpkn.screens.programdetail.NativeProgressionItemUi
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import com.example.kpkn.screens.programdetail.design.ProgramDayModule
import com.example.kpkn.screens.programdetail.design.ProgramDayUi
import com.example.kpkn.screens.programdetail.design.ProgramPhase
import com.example.kpkn.screens.programdetail.design.ProgramPhasesModule
import com.example.kpkn.screens.programdetail.design.ProgramWeekEditSheet
import com.example.kpkn.screens.programdetail.design.ProgramWeekHeader
import com.example.kpkn.screens.programdetail.design.ProgramWeekStats
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
 * HOJA DE DIBUJO de la pestaña Semana de Program Detail (P2): el módulo «Fases» con su competición, la cabecera de un programa simple, un
 * día desplegado con una sesión opcional, un día de descanso, dos sesiones el mismo día (una personalizada), los avisos y propuestas y la
 * hoja «Editar semana». No comprueba nada: escribe PNG a 360 dp, con la letra al 100 % y al 130 %. Solo corre con `KPKN_RENDER_DIR`;
 * sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class ProgramWeekRenderSheetTest {

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
    private val cases = listOf("fases", "simple", "dia", "descanso", "dosSesiones", "hojaSemana")

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

    /** Alto de la hoja de cada caso. */
    private fun heightOf(name: String): Int = when (name) {
        "fases" -> 470
        "simple" -> 560
        "dia" -> 470
        "descanso" -> 200
        "dosSesiones" -> 720
        "hojaSemana" -> 420
        else -> 600
    }

    private val torsoDay: ProgramDayUi = ProgramDetailSamples.day(
        id = 1,
        name = "Lunes",
        short = "Lun",
        sessions = listOf(ProgramDetailSamples.sessionUi(ProgramDetailSamples.torsoA, done = true)),
        number = "13",
        today = true,
        start = true,
    )

    private val optionalSession = ProgramDetailSamples.session(
        id = "torso-opt",
        name = "Torso opcional",
        day = 3,
        exercises = listOf(ProgramDetailSamples.exercise("row", "Remo con barra", 4)),
        optional = true,
    )

    private val optionalDay: ProgramDayUi = ProgramDetailSamples.day(
        id = 3,
        name = "Miércoles",
        short = "Mié",
        sessions = listOf(
            ProgramDetailSamples.sessionUi(optionalSession),
        ),
        number = "21",
    )

    private val restDay: ProgramDayUi = ProgramDetailSamples.day(
        id = 4,
        name = "Jueves",
        short = "Jue",
        sessions = emptyList(),
        number = "15",
    )

    private val twoSessionsDay: ProgramDayUi = ProgramDetailSamples.day(
        id = 2,
        name = "Martes",
        short = "Mar",
        sessions = listOf(
            ProgramDetailSamples.sessionUi(ProgramDetailSamples.torsoA, done = true),
            ProgramDetailSamples.sessionUi(ProgramDetailSamples.piernaA, customized = true, restorable = true),
        ),
        number = "14",
    )

    /** Un día desplegado con sus acciones de prueba (sin estado). */
    @Composable
    private fun DayCase(day: ProgramDayUi, expanded: Boolean) {
        ProgramDayModule(
            day = day,
            expandedSessionIds = if (expanded) day.sessions.map { it.session.id }.toSet() else emptySet(),
            onToggleSession = {},
            onLongPressDay = {},
            onEditDate = {},
            onAddSession = {},
            onStartSession = {},
            onEditSession = {},
            onMoreSession = { _, _ -> },
            onRestoreSession = {},
            draggingSessionId = null,
            insertIndex = null,
            anchorFirstSession = false,
            onDayBounds = {},
            onSessionBounds = { _, _ -> },
            onSessionDragStart = {},
            onSessionDrag = { _, _ -> },
            onSessionDragEnd = {},
            onSessionDragCancel = {},
        )
    }

    /** Pinta el caso [name]. */
    @Composable
    private fun Sheet(name: String) {
        when (name) {
            "fases" -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                ProgramPhasesModule(
                    blocks = ProgramDetailSamples.phaseBlocks(),
                    selectedWeekId = "w6",
                    onSelectWeek = {},
                    onLongPressWeek = { _, _ -> },
                    onAddWeek = {},
                )
            }
            "simple" -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                ProgramWeekHeader(
                    title = "Semana 4",
                    subtitle = "Intensificación · 29 oct – 2 nov",
                    phaseColor = ProgramPhase.INTENSIFICATION.color,
                    stats = ProgramWeekStats(sessionsDone = 2, sessionsTotal = 5, setsDone = 26, setsTotal = 60, minutes = 276),
                    canPrev = true,
                    canNext = true,
                    onPrev = {},
                    onNext = {},
                    onOpenWeeks = {},
                )
                Spacer(Modifier.height(12.dp))
                DayCase(day = torsoDay, expanded = true)
            }
            "dia" -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                DayCase(day = optionalDay, expanded = true)
            }
            "descanso" -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                DayCase(day = restDay, expanded = false)
            }
            "dosSesiones" -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(12.dp))
                DayCase(day = twoSessionsDay, expanded = true)
            }
            "hojaSemana" -> Box(Modifier.fillMaxWidth().height(420.dp)) {
                ProgramWeekEditSheet(
                    name = "Semana 3",
                    description = "Bloque de fuerza con dos sesiones.",
                    onNameChange = {},
                    onDescriptionChange = {},
                    onSave = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun renderProgramWeekSheets() {
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
