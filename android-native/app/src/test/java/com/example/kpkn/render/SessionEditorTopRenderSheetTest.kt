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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.SessionEditorSamples
import com.example.kpkn.screens.sessioneditor.design.SessionEditorBottomIsland
import com.example.kpkn.screens.sessioneditor.design.SessionEditorCoverStrip
import com.example.kpkn.screens.sessioneditor.design.SessionEditorHeader
import com.example.kpkn.screens.sessioneditor.design.SessionEditorLoadError
import com.example.kpkn.screens.sessioneditor.design.SessionEditorLoadingSkeleton
import com.example.kpkn.screens.sessioneditor.design.SessionEditorMoreMenu
import com.example.kpkn.screens.sessioneditor.design.SessionEditorTopActions
import com.example.kpkn.screens.sessioneditor.design.SessionNavigatorSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknTopIsland
import com.example.kpkn.ui.design.KpknTopRow
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
 * HOJA DE DIBUJO de la parte de arriba del editor de sesiones (SE2): la cabecera con nombre de dos líneas, la cabecera sin descripción,
 * el menú «⋯», la hoja del navegador, las dos islas, la carga y el error. No comprueba nada: escribe PNG. Pinta a 360 dp, con la letra al
 * 100 % y al 130 %, sin movimiento. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class SessionEditorTopRenderSheetTest {

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
        "cabecera", "sinDescripcion", "menu", "navegador",
        "islaSuperior", "islaInferior", "carga", "error",
    )

    /** El caso que se dibuja ahora, y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Alto de la hoja de cada caso: la del navegador necesita el de la pantalla para que se vea entera. */
    private fun heightOf(name: String): Int = when (name) {
        "navegador" -> 1000
        "menu" -> 420
        "cabecera" -> 330
        "sinDescripcion" -> 300
        "islaSuperior", "islaInferior" -> 180
        "carga" -> 480
        "error" -> 380
        else -> 400
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

    /** El caso [name], con sus datos de ejemplo. Sin estado: todo llega por parámetros. */
    @Composable
    private fun Case(name: String) {
        val haze = remember { HazeState() }
        // El ancla del menú va en píxeles de ventana: el botón «⋯» ocupa 300–348 dp en X y 4–52 dp en Y.
        val density = LocalDensity.current
        val moreAnchor = with(density) { Rect(300.dp.toPx(), 4.dp.toPx(), 348.dp.toPx(), 52.dp.toPx()) }
        when (name) {
            "cabecera" -> Column(Modifier.fillMaxWidth()) {
                KpknTopRow(
                    onBack = {},
                    actions = { SessionEditorTopActions(saveStatus = "Sin guardar", onMore = {}, onMoreBoundsChanged = {}) },
                )
                SessionEditorCoverStrip(background = SessionEditorSamples.background)
                SessionEditorHeader(
                    sessionId = "long",
                    name = SessionEditorSamples.LONG_NAME,
                    description = "Empuje con dominadas y fondos, cuatro semanas",
                    navigatorLabel = "Lun · Torso A",
                    exerciseCount = 6,
                    setCount = 22,
                    durationMinutes = 58,
                    onNameCommit = {},
                    onDescriptionCommit = {},
                    onOpenNavigator = {},
                )
            }
            "sinDescripcion" -> Column(Modifier.fillMaxWidth()) {
                KpknTopRow(
                    onBack = {},
                    actions = { SessionEditorTopActions(saveStatus = "Guardado", onMore = {}, onMoreBoundsChanged = {}) },
                )
                SessionEditorCoverStrip(background = SessionEditorSamples.background)
                SessionEditorHeader(
                    sessionId = "plain",
                    name = "Torso A",
                    description = null,
                    navigatorLabel = "Lun · Torso A",
                    exerciseCount = 6,
                    setCount = 22,
                    durationMinutes = 58,
                    onNameCommit = {},
                    onDescriptionCommit = {},
                    onOpenNavigator = {},
                )
            }
            "menu" -> Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth()) {
                    KpknTopRow(
                        onBack = {},
                        actions = { SessionEditorTopActions(saveStatus = "Guardado", onMore = {}, onMoreBoundsChanged = {}) },
                    )
                    SessionEditorCoverStrip(background = SessionEditorSamples.background)
                }
                SessionEditorMoreMenu(
                    anchor = moreAnchor,
                    onDismiss = {},
                    onCover = {},
                    onHistory = {},
                    onTransfer = {},
                    onSaveTemplate = {},
                    onRules = {},
                )
            }
            "navegador" -> SessionNavigatorSheet(
                orderedDays = SessionEditorSamples.orderedDays,
                sessions = SessionEditorSamples.weekSessions,
                currentSessionId = "s-lun",
                currentDayOfWeek = 1,
                activeVariant = WeekVariant.A,
                availableVariants = listOf(WeekVariant.A, WeekVariant.B),
                variantName = { variant -> if (variant == WeekVariant.A) "Original" else "Pierna rápida" },
                nextVariant = WeekVariant.C,
                defaultVariantName = "Torso A – Rápida",
                roadmapOptions = SessionEditorSamples.roadmap,
                currentWeekId = "w1",
                blockGrouping = false,
                weekCaption = null,
                onDismiss = {},
                onSelectSession = {},
                onMarkMain = {},
                onCreateSessionForDay = {},
                onSelectVariant = {},
                onCreateVariant = { _, _ -> },
                onDeleteActiveVariant = {},
                onSelectRoadmapOption = {},
            )
            "islaSuperior" -> Box(Modifier.fillMaxSize()) {
                KpknTopIsland(
                    visible = true,
                    title = "Torso A",
                    subtitle = "≈ 58 min",
                    leading = {
                        KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = {})
                    },
                    actions = {
                        KpknIconButton(icon = KpknLineIcons.Plus, contentDescription = "Añadir ejercicio", onClick = {})
                    },
                )
            }
            "islaInferior" -> Box(Modifier.fillMaxSize()) {
                SessionEditorBottomIsland(
                    visible = true,
                    timeLabel = "≈ 58 min",
                    hazeState = haze,
                    onAddExercise = {},
                    onAssistant = {},
                    onTime = {},
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                )
            }
            "carga" -> Column(Modifier.fillMaxWidth()) {
                KpknTopRow(onBack = {})
                SessionEditorLoadingSkeleton()
            }
            else -> Column(Modifier.fillMaxWidth()) {
                KpknTopRow(onBack = {})
                SessionEditorLoadError(
                    message = "No encontramos esta sesión en el programa.",
                    onBack = {},
                    onRetry = {},
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }

    @Test
    fun renderSessionEditorTop() {
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
