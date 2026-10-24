package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.kpkn.screens.home.HomeBarContext
import com.example.kpkn.screens.home.HomeSamples
import com.example.kpkn.screens.home.HomeTopBar
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.KpknBottomBar
import com.example.kpkn.ui.components.KpknBottomBarItem
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlass
import com.example.kpkn.ui.design.KpknShape
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
 * HOJA DE DIBUJO de la barra inferior de 64 dp y de la isla superior de Home (I11b). No comprueba nada: escribe PNG. Pinta la
 * barra con cada pestaña elegida y sin Nutrición, y la isla en sus cuatro contextos más una variante sin nombre (la silueta del
 * avatar). A 360 dp, al 100 % y al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h400dp-xxhdpi", application = android.app.Application::class)
class KpknBottomBarRenderSheetTest {

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

    /** Qué escena se dibuja ahora: «barra» o «isla». Fuera de la composición para poder cambiarla. */
    private var scene by mutableStateOf("barra")
    private var fontScale by mutableStateOf(1f)

    /** Estado de la barra: pestaña elegida y si Nutrición está oculta. */
    private var barSelected by mutableStateOf("home")
    private var barWithoutNutrition by mutableStateOf(false)

    /** Estado de la isla: contexto, nombre y si ya se ve. */
    private var islandContext by mutableStateOf(HomeBarContext.GREETING)
    private var islandName by mutableStateOf("Alex")
    private var islandShown by mutableStateOf(false)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private val glassHaze = HazeState()
    private val primarySession = HomeSamples.twelveWeekItems(HomeSamples.twelveWeekProgram())[1]

    /** Las cuatro pestañas de la app; sin Nutrición, las tres que quedan. */
    private val todas = listOf(
        KpknBottomBarItem(route = "home", label = "Inicio", icon = KpknLineIcons.Home),
        KpknBottomBarItem(route = "training", label = "Entreno", icon = KpknLineIcons.Training),
        KpknBottomBarItem(route = "nutrition", label = "Nutrición", icon = KpknLineIcons.Nutrition),
        KpknBottomBarItem(route = "body", label = "Cuerpo", icon = KpknLineIcons.Body),
    )

    /** La barra tal como va en MainActivity: isla de vidrio con 12 dp a los lados y 8 dp abajo, sobre negro. */
    @Composable
    private fun SceneBarra() {
        Box(
            modifier = Modifier.testTag("sheet").width(360.dp).height(80.dp).background(Color.Black),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                    .kpknGlass(glassHaze, KpknShape.island),
            ) {
                KpknBottomBar(
                    items = if (barWithoutNutrition) todas.filter { it.route != "nutrition" } else todas,
                    selectedRoute = barSelected,
                    onSelect = {},
                )
            }
        }
    }

    /** La isla superior de Home con el contexto y el nombre que estén fijados ahora. */
    @Composable
    private fun SceneIsla() {
        val context = islandContext
        Box(Modifier.testTag("sheet").width(360.dp).height(80.dp).background(Color.Black)) {
            HomeTopBar(
                modifier = Modifier.testTag("isla"),
                greeting = "Buenos días",
                userName = islandName,
                greetingProgress = 1f,
                
                sessionProgress = if (context == HomeBarContext.SESSION) 1f else 0f,
                nutritionProgress = if (context == HomeBarContext.NUTRITION) 1f else 0f,
                hasPrograms = true,
                
                
                
                // Con cobertura de datos la isla enseña las tres cifras, no «Sin calibrar».
                
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
                barAlpha = if (islandShown) 1f else 0f,
            )
        }
    }

    @Composable
    private fun Scene() {
        when (scene) {
            "barra" -> SceneBarra()
            else -> SceneIsla()
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

    private fun crop(full: Bitmap, bounds: Rect): Bitmap {
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        return Bitmap.createBitmap(full, left, top, width, height)
    }

    /** Apila los recuadros uno debajo de otro, sobre negro, en una sola hoja. */
    private fun stack(frames: List<Bitmap>): Bitmap {
        val width = frames.maxOf { it.width }
        val height = frames.sumOf { it.height }
        val sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(android.graphics.Color.BLACK)
        var y = 0f
        for (frame in frames) {
            canvas.drawBitmap(frame, 0f, y, null)
            y += frame.height
        }
        return sheet
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** La barra en las cuatro pestañas elegidas y una quinta sin Nutrición. Espera 300 ms para que el punto llegue a su sitio. */
    private fun barFrames(): List<Bitmap> {
        val estados = listOf(
            "home" to false,
            "training" to false,
            "nutrition" to false,
            "body" to false,
            "home" to true,
        )
        return estados.map { (elegida, sinNutricion) ->
            rule.runOnIdle {
                scene = "barra"
                barSelected = elegida
                barWithoutNutrition = sinNutricion
            }
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(300)
            rule.waitForIdle()
            crop(drawView(), rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot)
        }
    }

    /** La isla en sus cuatro contextos y una variante sin nombre. Se deja asentar 600 ms tras aparecer. */
    private fun islandFrames(): List<Bitmap> {
        val estados = listOf(
            HomeBarContext.GREETING to "Alex",
            HomeBarContext.SESSION to "Alex",
            HomeBarContext.RINGS to "Alex",
            HomeBarContext.NUTRITION to "Alex",
            HomeBarContext.GREETING to "",
        )
        return estados.map { (contexto, nombre) ->
            rule.runOnIdle {
                scene = "isla"
                islandShown = false
                islandContext = contexto
                islandName = nombre
            }
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            rule.runOnIdle { islandShown = true }
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(600)
            rule.waitForIdle()
            crop(drawView(), rule.onNodeWithTag("isla").fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun renderKpknBottomBarSheets() {
        onlyWhenAsked()
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
            saveBitmap("i11b_barra$suffix", stack(barFrames()))
            saveBitmap("i11b_isla$suffix", stack(islandFrames()))
        }
    }
}
