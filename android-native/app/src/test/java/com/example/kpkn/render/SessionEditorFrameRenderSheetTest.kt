package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.SessionRoadmapOption
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameBlockNotice
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameBottomBar
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameHeader
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameTopBar
import com.example.kpkn.screens.sessioneditor.frame.SessionEditorFrameWeekContent
import com.example.kpkn.screens.sessioneditor.frame.frameWeekOrder
import com.example.kpkn.screens.sessioneditor.frame.nextFreeVariant
import com.example.kpkn.screens.sessioneditor.frame.sessionFrameSummary
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
 * HOJA DE DIBUJO del marco del editor (ER1a): la fila superior, la cabecera con nombre largo y corto, la barra inferior en tres
 * estados, la hoja «Semana» con 4 días con sesión (uno de competición y uno con dos) y el aviso de bloqueo. No comprueba nada: escribe
 * PNG. A 360 dp, al 100 % y al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi", application = android.app.Application::class)
class SessionEditorFrameRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }

        /** Las escenas que se guardan, una por PNG. */
        private val SCENES = listOf(
            "fila",
            "cabecera_larga",
            "cabecera_corta",
            "barra_sin_sugerencias",
            "barra_con_3",
            "barra_pasada",
            "barra_60",
            "semana",
            "bloqueo",
        )
    }

    @get:Rule
    val rule = createComposeRule()

    /** Qué escena se dibuja ahora. Fuera de la composición para poder cambiarla desde la prueba. */
    private var scene by mutableStateOf(SCENES.first())
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private val glassHaze = HazeState()

    private val weekSessions = listOf(
        Session(id = "lun", name = "Pierna A", dayOfWeek = 1, isMainSession = true),
        Session(id = "mar", name = "Torso A", dayOfWeek = 2, isMainSession = true),
        Session(id = "mie", name = "Pierna B", dayOfWeek = 3, isMainSession = true),
        Session(id = "vie-1", name = "Empuje", dayOfWeek = 5, isMainSession = true),
        Session(id = "vie-2", name = "Tirón de apoyo", dayOfWeek = 5, isMainSession = false),
    )

    private val roadmap = listOf(
        SessionRoadmapOption(
            macroIndex = 0, blockIndex = 0, mesoIndex = 0, weekIndex = 0, weekId = "w1",
            macroName = "Macro", blockName = "Bloque 1", weekName = "Semana de carga", sessionCount = 5,
        ),
        SessionRoadmapOption(
            macroIndex = 0, blockIndex = 0, mesoIndex = 0, weekIndex = 1, weekId = "w2",
            macroName = "Macro", blockName = "Bloque 1", weekName = "Semana de descarga", sessionCount = 5,
        ),
    )

    @Composable
    private fun Scene() {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when (scene) {
                "fila" -> SessionEditorFrameTopBar(
                    dayLabel = "Jue · Pierna A",
                    forward = true,
                    canGoPrevious = true,
                    canGoNext = true,
                    onBack = {},
                    onPrevious = {},
                    onNext = {},
                    onOpenWeek = {},
                    onTransfer = {},
                    onHistory = {},
                )
                "cabecera_larga" -> Column(Modifier.fillMaxWidth()) {
                    SessionEditorFrameHeader(
                        sessionId = "larga",
                        name = "Pierna pesada de sentadilla y peso muerto",
                        description = "Fuerza con dos superseries; el cardio va al final",
                        protocolLabel = "Fuerza · 5/3/1",
                        summary = sessionFrameSummary(7, 22, 54),
                        onNameChange = {},
                        onDescriptionChange = {},
                        onDaySwipe = { false },
                    )
                }
                "cabecera_corta" -> Column(Modifier.fillMaxWidth()) {
                    SessionEditorFrameHeader(
                        sessionId = "corta",
                        name = "Pierna A",
                        description = "",
                        protocolLabel = null,
                        summary = sessionFrameSummary(4, 12, 38),
                        onNameChange = {},
                        onDescriptionChange = {},
                        onDaySwipe = { false },
                    )
                }
                "barra_sin_sugerencias" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    SessionEditorFrameBottomBar(
                        hazeState = glassHaze,
                        suggestionCount = 0,
                        estimatedMinutes = 54,
                        limitMinutes = 60,
                        onAdd = {},
                        onAssistant = {},
                        onRules = {},
                        modifier = Modifier.padding(12.dp),
                    )
                }
                "barra_con_3" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    SessionEditorFrameBottomBar(
                        hazeState = glassHaze,
                        suggestionCount = 3,
                        estimatedMinutes = 50,
                        limitMinutes = 60,
                        onAdd = {},
                        onAssistant = {},
                        onRules = {},
                        modifier = Modifier.padding(12.dp),
                    )
                }
                "barra_pasada" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    SessionEditorFrameBottomBar(
                        hazeState = glassHaze,
                        suggestionCount = 0,
                        estimatedMinutes = 70,
                        limitMinutes = 60,
                        onAdd = {},
                        onAssistant = {},
                        onRules = {},
                        modifier = Modifier.padding(12.dp),
                    )
                }
                "barra_60" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    SessionEditorFrameBottomBar(
                        hazeState = glassHaze,
                        suggestionCount = 0,
                        estimatedMinutes = 60,
                        limitMinutes = 60,
                        onAdd = {},
                        onAssistant = {},
                        onRules = {},
                        modifier = Modifier.padding(12.dp),
                    )
                }
                "semana" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    SessionEditorFrameWeekContent(
                        orderedDays = frameWeekOrder(1),
                        sessions = weekSessions,
                        currentSessionId = "lun",
                        currentDayOfWeek = 1,
                        competitionKeyDaysInWeek = setOf(3),
                        roadmapOptions = roadmap,
                        currentWeekId = "w1",
                        isSimpleProgram = true,
                        hasActiveLoops = true,
                        activeVariant = WeekVariant.A,
                        availableVariants = listOf(WeekVariant.A),
                        variantName = { if (it == WeekVariant.A) "Original" else "Derivada" },
                        nextVariant = nextFreeVariant(listOf(WeekVariant.A)),
                        defaultVariantName = "Pierna A – Rápida",
                        onSelectSession = {},
                        onSetMainSession = {},
                        onCreateSessionForDay = {},
                        onSelectRoadmapOption = {},
                        onSelectVariant = {},
                        onCreateVariant = { _, _ -> },
                        onDeleteActiveVariant = {},
                        onDone = {},
                    )
                }
                "bloqueo" -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    SessionEditorFrameBlockNotice(
                        message = "No se pudo guardar: «Sentadilla búlgara» no tiene su ficha del catálogo. Cámbialo por uno del catálogo o quítalo.",
                        onOpenExercise = {},
                    )
                }
            }
        }
    }

    /** Dibuja la vista entera sobre negro, tal como está ahora. */
    private fun drawView(): Bitmap {
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        return full
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderSessionEditorFrameSheets() {
        assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides false,
            ) {
                Scene()
            }
        }
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle { fontScale = scale }
            rule.waitForIdle()
            // Con el reloj parado no hay cuadros: uno para que el cambio de escala llegue a la vista antes de capturar.
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            for (name in SCENES) {
                rule.runOnIdle { scene = name }
                rule.waitForIdle()
                rule.mainClock.advanceTimeByFrame()
                rule.waitForIdle()
                saveBitmap("er1a_$name$suffix", drawView())
            }
        }
    }
}
