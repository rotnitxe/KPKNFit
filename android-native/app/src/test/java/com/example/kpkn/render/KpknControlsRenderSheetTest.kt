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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSkeleton
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknSwitch
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknTopIsland
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.components.icons.KpknLineIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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
 * HOJA DE DIBUJO de los controles compartidos KPKN v2 (no comprueba nada: escribe PNG). Pinta cada control en sus estados, la isla
 * superior en 5 fotogramas de su entrada sobre un fondo con color (para ver el vidrio), la fila superior, el esqueleto y el vacío. A 360 dp,
 * al 100 % y al 130 % de letra. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class KpknControlsRenderSheetTest {

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

    /** Escena que se dibuja ahora. Fuera de la composición para poder cambiarla desde la prueba. */
    private var scene by mutableStateOf("interruptores")
    private var fontScale by mutableStateOf(1f)
    private var islandShown by mutableStateOf(false)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    private val glassHaze = HazeState()
    private val noop: (Boolean) -> Unit = {}

    @Composable
    private fun SwitchLine(label: String, checked: Boolean, enabled: Boolean) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            KpknSwitch(checked = checked, onCheckedChange = if (enabled) noop else null, enabled = enabled)
        }
    }

    @Composable
    private fun SceneInterruptores() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpknModule {
                KpknModuleHeader(label = "Interruptores")
                Spacer(Modifier.height(4.dp))
                SwitchLine(label = "Apagado", checked = false, enabled = true)
                SwitchLine(label = "Encendido", checked = true, enabled = true)
                SwitchLine(label = "Deshabilitado, apagado", checked = false, enabled = false)
                SwitchLine(label = "Deshabilitado, encendido", checked = true, enabled = false)
            }
            KpknModule {
                KpknModuleHeader(label = "Opciones")
                Spacer(Modifier.height(4.dp))
                KpknRadioRow(selected = true, title = "Mañana", subtitle = "Antes de las 12:00", onClick = {})
                KpknRadioRow(selected = false, title = "Tarde", subtitle = "Después de las 17:00", onClick = {})
                KpknRadioRow(selected = false, title = "Noche", subtitle = "No disponible", onClick = {}, enabled = false)
                KpknCheckRow(checked = true, title = "Pesar cada lunes", onClick = {})
                KpknCheckRow(checked = false, title = "Medir cintura", subtitle = "Opcional", onClick = {})
                KpknCheckRow(checked = true, title = "Sesión cerrada", onClick = {}, enabled = false)
            }
        }
    }

    @Composable
    private fun SceneSeleccion() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpknModule {
                KpknModuleHeader(label = "Vista")
                Spacer(Modifier.height(10.dp))
                KpknSegmentedControl(options = listOf("Semana", "Mes", "Año"), selectedIndex = 1, onSelect = {})
                Spacer(Modifier.height(10.dp))
                KpknSegmentedControl(options = listOf("Volumen", "Intensidad", "Frecuencia"), selectedIndex = 0, onSelect = {})
            }
            KpknModule {
                KpknModuleHeader(label = "Compacto")
                Spacer(Modifier.height(10.dp))
                KpknSegmentedControl(
                    options = listOf("Hoy", "Semana", "Mes", "Año"),
                    selectedIndex = 2,
                    onSelect = {},
                    size = KpknSegmentedSize.Compact,
                )
            }
            KpknModule {
                KpknModuleHeader(label = "Filtros")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknChip(text = "Fuerza", selected = true, onClick = {})
                    KpknChip(text = "Cardio", selected = false, onClick = {})
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknChip(text = "Movilidad", selected = false, onClick = {}, leadingIcon = KpknLineIcons.Check)
                    KpknChip(text = "Dolor", selected = false, onClick = {}, enabled = false)
                }
            }
        }
    }

    @Composable
    private fun SceneCampos() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            KpknTextField(
                value = "",
                onValueChange = {},
                placeholder = "Nombre",
                label = "Alias",
                leadingIcon = KpknLineIcons.Edit,
            )
            // Este campo se enfoca tocándolo desde la prueba (índice 1 de los campos con texto).
            KpknTextField(value = "Juan", onValueChange = {}, label = "Alias")
            KpknTextField(value = "Jo", onValueChange = {}, label = "Alias", error = "Mínimo 3 letras")
            KpknTextField(
                value = "",
                onValueChange = {},
                placeholder = "Peso",
                label = "Peso",
                enabled = false,
                trailing = { Text(text = "kg", style = KpknType.unit, color = WizardColors.textMuted) },
            )
        }
    }

    @Composable
    private fun SceneProgreso() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpknModule {
                KpknModuleHeader(label = "Volumen semanal")
                Spacer(Modifier.height(8.dp))
                KpknMetric(value = "42", unit = "%", style = KpknType.metricL)
                Spacer(Modifier.height(12.dp))
                KpknProgressBar(progress = 0.42f)
            }
            KpknModule {
                KpknModuleHeader(label = "Proteína del día")
                Spacer(Modifier.height(8.dp))
                KpknMetric(value = "136", unit = "g de 150", style = KpknType.metricM)
                Spacer(Modifier.height(12.dp))
                KpknProgressBar(progress = 0.9f, marker = 0.75f, color = SymbolPalette.energia)
            }
            KpknModule {
                KpknModuleHeader(label = "Sin empezar")
                Spacer(Modifier.height(12.dp))
                KpknProgressBar(progress = 0f)
                Spacer(Modifier.height(12.dp))
                KpknProgressBar(progress = 1f, color = SymbolPalette.ok)
            }
        }
    }

    @Composable
    private fun SceneFila() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpknTopRow(
                onBack = {},
                actions = {
                    KpknIconButton(icon = KpknLineIcons.Edit, contentDescription = "Editar", onClick = {})
                    KpknIconButton(icon = KpknLineIcons.More, contentDescription = "Más", onClick = {})
                },
            )
            Text(
                text = "Plan de fuerza",
                style = KpknType.display,
                color = WizardColors.text,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            KpknTopRow(
                onBack = null,
                actions = {
                    KpknIconButton(icon = KpknLineIcons.Plus, contentDescription = "Añadir", onClick = {}, filled = true)
                },
            )
        }
    }

    @Composable
    private fun SceneIsla() {
        Box(
            modifier = Modifier.testTag("sheet").width(360.dp).height(124.dp).background(KpknSurface.canvas),
        ) {
            // Fondo con color bajo la isla: el vidrio tiene que verse como manchas suaves, sin letras legibles.
            Box(modifier = Modifier.matchParentSize().hazeSource(glassHaze)) {
                Box(
                    modifier = Modifier
                        .padding(start = 150.dp, top = 20.dp)
                        .size(width = 210.dp, height = 120.dp)
                        .clip(CircleShape)
                        .background(SymbolPalette.musculo),
                )
                Box(
                    modifier = Modifier
                        .padding(start = 20.dp, top = 70.dp)
                        .size(width = 150.dp, height = 90.dp)
                        .clip(CircleShape)
                        .background(SymbolPalette.columna),
                )
                Text(
                    text = "92 %",
                    style = KpknType.metricL,
                    color = WizardColors.text,
                    modifier = Modifier.padding(start = 200.dp, top = 84.dp),
                )
            }
            KpknTopIsland(
                visible = islandShown,
                title = "Hoy",
                subtitle = "Martes, 13 de octubre",
                leading = { KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = {}) },
                actions = {
                    KpknIconButton(icon = KpknLineIcons.Plus, contentDescription = "Añadir", onClick = {}, filled = true)
                },
            )
        }
    }

    @Composable
    private fun SceneEsqueleto() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpknSkeletonModule(lines = 3)
            KpknSkeletonModule(lines = 2)
            KpknSkeleton(modifier = Modifier.fillMaxWidth().height(120.dp), shape = KpknShape.module)
        }
    }

    @Composable
    private fun SceneVacio() {
        Column(
            modifier = Modifier.testTag("sheet").width(360.dp).background(KpknSurface.canvas).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpknEmptyState(
                icon = KpknLineIcons.Calendar,
                title = "Sin sesiones esta semana",
                body = "Programa una sesión y aparecerá aquí.",
                action = {
                    KpknSecondaryPill(text = "Programar sesión", onClick = {}, modifier = Modifier.fillMaxWidth())
                },
            )
            KpknEmptyState(icon = KpknLineIcons.Book, title = "Nada que mostrar", body = null)
        }
    }

    @Composable
    private fun Scene() {
        when (scene) {
            "interruptores" -> SceneInterruptores()
            "seleccion" -> SceneSeleccion()
            "campos" -> SceneCampos()
            "progreso" -> SceneProgreso()
            "fila" -> SceneFila()
            "esqueleto" -> SceneEsqueleto()
            "vacio" -> SceneVacio()
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

    /** Pinta una escena estática a 100 % y 130 % de letra. [afterShow] prepara el estado antes de capturar (foco, reloj…). */
    private fun renderStatic(sceneName: String, file: String, afterShow: () -> Unit = {}) {
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = sceneName
                fontScale = scale
            }
            rule.waitForIdle()
            // Con el reloj parado no hay cuadros: uno para que la escena nueva llegue a la vista antes de capturar.
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            afterShow()
            save("sheet", "$file$suffix")
        }
    }

    /** Pinta la isla a 0, 60, 120, 240 y 400 ms de su entrada, al 100 % y al 130 %, y apila los fotogramas en una sola hoja. */
    private fun renderIsland() {
        val times = listOf(0L, 60L, 120L, 240L, 400L)
        for ((scale, suffix) in listOf(1f to "", 1.3f to "_s130")) {
            rule.runOnIdle {
                scene = "isla"
                fontScale = scale
                islandShown = false
            }
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            rule.runOnIdle { islandShown = true }
            rule.waitForIdle()
            // El primer cuadro coloca la isla y arranca su entrada: ese instante es el de 0 ms.
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            val frames = mutableListOf<Bitmap>()
            var elapsed = 0L
            for (t in times) {
                rule.mainClock.advanceTimeBy(t - elapsed)
                elapsed = t
                rule.waitForIdle()
                val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
                frames += crop(drawView(), bounds)
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
            saveBitmap("controles_isla_entrada$suffix", sheet)
        }
    }

    @Test
    fun renderKpknControlsSheets() {
        onlyWhenAsked()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides false,
                LocalHazeState provides glassHaze,
            ) {
                Scene()
            }
        }
        renderStatic(sceneName = "interruptores", file = "controles_interruptores")
        renderStatic(sceneName = "seleccion", file = "controles_seleccion")
        renderStatic(sceneName = "campos", file = "controles_campos", afterShow = {
            // Foco en el segundo campo, con un toque real; luego se deja asentar el borde animado.
            rule.onAllNodes(hasSetTextAction())[1].performTouchInput { click(center) }
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
        })
        renderStatic(sceneName = "progreso", file = "controles_progreso")
        renderStatic(sceneName = "fila", file = "controles_fila")
        renderStatic(sceneName = "esqueleto", file = "controles_esqueleto", afterShow = {
            // Un fotograma a mitad del brillo (1,2 s por ciclo).
            rule.mainClock.advanceTimeBy(600)
            rule.waitForIdle()
        })
        renderStatic(sceneName = "vacio", file = "controles_vacio")
        renderIsland()
    }
}
