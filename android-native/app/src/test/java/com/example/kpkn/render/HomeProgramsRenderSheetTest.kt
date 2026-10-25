package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.example.kpkn.domain.training.HomeProgramLine
import com.example.kpkn.domain.training.HomeWeekResolver
import com.example.kpkn.screens.home.HomeProgramsSection
import com.example.kpkn.screens.home.HomeSamples
import com.example.kpkn.screens.home.HomeTopBar
import com.example.kpkn.screens.home.HomeWikiLabSection
import com.example.kpkn.screens.home.design.HomeNotice
import com.example.kpkn.screens.home.design.HomeNoticeTone
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
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
 * HOJA DE DIBUJO de «Tus programas», «Aprende», los avisos y la barra superior de Home (no comprueba nada: escribe PNG). Pinta a
 * 360 dp, con la letra al 100 % y al 130 %, y sin movimiento para ver el cuadro final. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class HomeProgramsRenderSheetTest {

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

    /** Los casos de la hoja, en orden: pósteres (con activo, vacío y nombre largo), «Aprende», avisos y la barra en sus cuatro estados. */
    private val cases = listOf(
        "posters3", "postersEmpty", "postersLong", "aprende", "notices",
        "barGreeting", "barSession", "barRings", "barNutrition",
    )

    private val active = HomeSamples.twelveWeekProgram().copy(weekDays = 4, structureTemplateId = "generated:powerlifting:periodized")
    private val shelf = HomeSamples.shelfPrograms(active)
    private val activeLine = HomeWeekResolver.programLine(active, HomeSamples.twelveWeekItems(active), HomeSamples.today)
    private val longActive = HomeSamples.longNameProgram()
    private val longLine = HomeProgramLine(
        name = longActive.name,
        weekLabel = "Semana 3 de 12",
        weekIndex = 3,
        weekCount = 12,
        phase = "Fase de acumulación",
    )
    private val primarySession = HomeSamples.twelveWeekItems(active)[1]
    private val glassHaze = HazeState()

    /** El caso que se dibuja ahora, y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap (como en la hoja de la cabecera). */
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

    /** Pinta el caso [name] de la lista de [cases]. */
    @Composable
    private fun Sheet(name: String) {
        when (name) {
            "posters3" -> HomeProgramsSection(
                programs = shelf,
                activeProgramId = active.id,
                onProgramClick = {},
                onCreateProgram = {},
                activeProgramLine = activeLine,
                onSeeAll = {},
            )
            "postersEmpty" -> HomeProgramsSection(
                programs = emptyList(),
                activeProgramId = null,
                onProgramClick = {},
                onCreateProgram = {},
            )
            "postersLong" -> HomeProgramsSection(
                programs = listOf(longActive, shelf.first()),
                activeProgramId = longActive.id,
                onProgramClick = {},
                onCreateProgram = {},
                activeProgramLine = longLine,
                onSeeAll = {},
            )
            "aprende" -> HomeWikiLabSection(onNavigate = {})
            "notices" -> Column {
                HomeNotice(
                    title = "Posible exceso de volumen",
                    body = "Hay señales de carga alta en: pecho y espalda. Considera reducir series semanales o un descanso activo.",
                    actionLabel = "Ver recomendaciones",
                    onAction = {},
                    tone = HomeNoticeTone.DANGER,
                )
                HomeNotice(
                    title = "Ajuste de carga sugerido",
                    body = "Tu fatiga ha subido esta semana. Baja algo el volumen de hoy.",
                    actionLabel = "Entendido",
                    onAction = {},
                    tone = HomeNoticeTone.WARN,
                )
            }
            else -> HomeTopBar(
                greeting = "Buenos días",
                userName = "Alex",
                greetingProgress = if (name == "barGreeting") 1f else 0f,
                
                sessionProgress = if (name == "barSession") 1f else 0f,
                nutritionProgress = if (name == "barNutrition") 1f else 0f,
                hasPrograms = true,
                
                
                
                primarySession = primarySession,
                homeCompetition = null,
                isRestDay = false,
                dailyCalorieGoal = 2300,
                consumedCalories = 1240,
                onStartWorkout = { _, _ -> },
                onRegisterCompetition = {},
                onCreateProgram = {},
                onAddMeal = {},
                onNavigateToProfile = {},
                hazeState = glassHaze,
                onBottomPositionChanged = {},
            )
        }
    }

    @Test
    fun renderHomeProgramsSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                Column(Modifier.testTag("sheet").width(360.dp).background(Color.Black)) {
                    Sheet(cases[current])
                    Spacer(Modifier.height(24.dp))
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
