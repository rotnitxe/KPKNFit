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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
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
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.home.HomeBarContext
import com.example.kpkn.screens.home.HomeSamples
import com.example.kpkn.screens.home.HomeTopBar
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
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
 * HOJA DE DIBUJO del lenguaje visual v2 (no comprueba nada: escribe PNG). Pinta módulos, botones, tipos, la isla superior en sus
 * cuatro contextos a 0, 60, 120, 240 y 400 ms de la entrada, y el respaldo sin desenfoque. A 360 dp; módulos, botones, tipos y
 * respaldo, al 100 % y al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class KpknDesignRenderSheetTest {

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

    /** Escena que se dibuja ahora: «modulos», «botones», «tipos», «vidrio» o «isla». Fuera de la composición para poder cambiarla. */
    private var scene by mutableStateOf("modulos")
    private var fontScale by mutableStateOf(1f)
    private var islandShown by mutableStateOf(false)
    private var islandContext by mutableStateOf(HomeBarContext.GREETING)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private val glassHaze = HazeState()
    private val primarySession = HomeSamples.twelveWeekItems(HomeSamples.twelveWeekProgram())[1]

    @Composable
    private fun SceneModulos() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpknModule {
                KpknModuleHeader(
                    label = "Volumen de entrenamiento",
                    action = { KpknTextAction(text = "Ver todo", onClick = {}) },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Tres sesiones esta semana, dos completadas.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            }
            KpknModule(modifier = Modifier.testTag("pulsado"), onClick = {}, onClickLabel = "Abrir historial") {
                KpknModuleHeader(label = "Tu historial")
                Spacer(Modifier.height(12.dp))
                KpknMetric(value = "92", unit = "%")
            }
            KpknModule(accent = SymbolPalette.energia) {
                Text(
                    text = "Te quedan 1.302 kcal para hoy.",
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                )
            }
        }
    }

    @Composable
    private fun SceneBotones() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            KpknPrimaryPill(
                text = "Empezar sesión",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.PlayArrow,
            )
            KpknSecondaryPill(text = "Editar", onClick = {}, modifier = Modifier.fillMaxWidth())
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KpknIconButton(icon = Icons.Filled.Add, contentDescription = "Agregar", onClick = {})
                KpknIconButton(icon = Icons.Filled.Add, contentDescription = "Agregar relleno", onClick = {}, filled = true)
                KpknTextAction(text = "Ver todos", onClick = {}, icon = Icons.Filled.Info)
            }
        }
    }

    @Composable
    private fun SceneTipos() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "Hoy, martes", style = KpknType.display, color = WizardColors.text)
            Text(text = "Volumen de entrenamiento", style = KpknType.title, color = WizardColors.text)
            Text(text = "CABECERA DE MÓDULO", style = KpknType.eyebrow, color = WizardColors.textMuted)
            KpknMetric(value = "64,0", unit = "kg", style = KpknType.metricXL)
            KpknMetric(value = "92", unit = "%", style = KpknType.metricL)
            KpknMetric(value = "2.300", unit = "kcal", style = KpknType.metricM)
            Text(
                text = "Texto de lectura para una frase de dos líneas.",
                style = KpknType.body,
                color = WizardColors.text,
            )
            Text(text = "Nombre de un ejercicio", style = KpknType.bodyStrong, color = WizardColors.text)
            Text(text = "Nota al pie de un control", style = KpknType.caption, color = WizardColors.textFaint)
            Text(text = "Acción de texto", style = KpknType.action, color = WizardColors.text)
        }
    }

    @Composable
    private fun SceneVidrio() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(Color(0xFF8FB2FF)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .kpknGlassOrFallback(null, KpknShape.island, variant = KpknGlassVariant.ISLAND),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Isla sin desenfoque", style = KpknType.bodyStrong, color = WizardColors.text)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .kpknGlassOrFallback(null, KpknShape.sheet, variant = KpknGlassVariant.SHEET),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Hoja sin desenfoque", style = KpknType.bodyStrong, color = WizardColors.text)
            }
        }
    }

    @Composable
    private fun SceneIsla() {
        val context = islandContext
        Box(Modifier.testTag("sheet").width(360.dp).height(120.dp).background(Color.Black)) {
            HomeTopBar(
                modifier = Modifier.testTag("isla"),
                greeting = "Buenos días",
                userName = "Alex",
                greetingProgress = 1f,
                
                sessionProgress = if (context == HomeBarContext.SESSION) 1f else 0f,
                nutritionProgress = if (context == HomeBarContext.NUTRITION) 1f else 0f,
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
                barAlpha = if (islandShown) 1f else 0f,
            )
        }
    }

    @Composable
    private fun Scene() {
        when (scene) {
            "modulos" -> SceneModulos()
            "botones" -> SceneBotones()
            "tipos" -> SceneTipos()
            "vidrio" -> SceneVidrio()
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

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Guarda el recuadro [tag] tal como se ve ahora. */
    private fun save(tag: String, name: String) {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        saveBitmap(name, crop(drawView(), bounds))
    }

    /** Pinta una escena estática a 100 % y 130 % de letra. Con [pressTag], ese módulo queda pulsado en la captura. */
    private fun renderStatic(sceneName: String, file: String, pressTag: String? = null) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
            }
            rule.waitForIdle()
            // Con el reloj parado no hay cuadros: uno para que la escena nueva llegue a la vista antes de capturar.
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            if (pressTag != null) {
                rule.onNodeWithTag(pressTag).performTouchInput { down(center) }
                rule.mainClock.advanceTimeBy(600)
                rule.waitForIdle()
            }
            save("sheet", "$file$suffix")
            if (pressTag != null) {
                rule.onNodeWithTag(pressTag).performTouchInput { up() }
                rule.waitForIdle()
            }
        }
    }

    /** Pinta la isla en cada contexto a 0, 60, 120, 240 y 400 ms de su entrada, y las apila en una sola hoja. */
    private fun renderIsland() {
        rule.runOnIdle { scene = "isla" }
        rule.waitForIdle()
        val times = listOf(0L, 60L, 120L, 240L, 400L)
        val frames = mutableListOf<Bitmap>()
        for (context in listOf(HomeBarContext.GREETING, HomeBarContext.SESSION, HomeBarContext.NUTRITION)) {
            rule.runOnIdle {
                islandShown = false
                islandContext = context
            }
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            rule.runOnIdle { islandShown = true }
            rule.waitForIdle()
            // El primer cuadro coloca la isla y arranca su entrada: ese instante es el de 0 ms.
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            var elapsed = 0L
            for (t in times) {
                rule.mainClock.advanceTimeBy(t - elapsed)
                elapsed = t
                rule.waitForIdle()
                val bounds = rule.onNodeWithTag("isla").fetchSemanticsNode().boundsInRoot
                frames += crop(drawView(), bounds)
            }
        }
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
        saveBitmap("diseño_isla", sheet)
    }

    @Test
    fun renderKpknDesignSheets() {
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
        renderStatic(sceneName = "modulos", file = "diseño_modulos", pressTag = "pulsado")
        renderStatic(sceneName = "botones", file = "diseño_botones")
        renderStatic(sceneName = "tipos", file = "diseño_tipos")
        renderStatic(sceneName = "vidrio", file = "diseño_vidrio_respaldo")
        renderIsland()
    }
}
