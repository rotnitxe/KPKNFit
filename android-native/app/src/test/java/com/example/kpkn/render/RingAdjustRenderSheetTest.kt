package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.RecoveryBand
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.RecoveryChannelSnapshot
import com.example.kpkn.data.models.RecoveryDashboard
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.screens.home.HomeSamples
import com.example.kpkn.screens.home.LocalRingMuscleAdjustSave
import com.example.kpkn.screens.home.RingDetailSheet
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.ReadinessReportedDiscomfort
import com.example.kpkn.screens.workout.components.WorkoutReadinessSheet
import com.example.kpkn.ui.components.rings.EnergyColumnAdjust
import com.example.kpkn.ui.components.rings.MuscleBodyAdjust
import com.example.kpkn.ui.components.rings.RingAdjustLevel
import com.example.kpkn.ui.components.rings.RingAdjustMuscles
import com.example.kpkn.ui.components.rings.RingAdjustSheet
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.theme.KPKNTheme
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
 * HOJA DE RENDER del ajuste oficial de los anillos y del estado previo (LV10b): el anillo de Energía y de Columna con «antes» (G5), el
 * cuerpo de frente y de espalda con una zona elegida (G6), el estado previo entero (G1) a 360 dp con la letra al 100 % y al 130 %, y el
 * detalle de Inicio con el ajuste. No comprueba nada: escribe PNG. Solo corre con la variable de entorno `KPKN_RENDER_DIR`; sin ella se
 * salta. Dibuja las piezas REALES con datos de ejemplo y sin movimiento (el cuadro final).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class RingAdjustRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private var current by mutableStateOf<Scene?>(null)
    private var fontScale by mutableStateOf(1f)
    private var hostView: View? = null

    /** Una escena: lo que se dibuja, el nodo que se recorta y, si hace falta, qué tocar antes de fotografiarla. */
    private class Scene(
        val name: String,
        val crop: String,
        val height: Int = 880,
        val touch: (ComposeContentTestRule.() -> Unit)? = null,
        val content: @Composable () -> Unit,
    )

    private fun draw(scene: Scene, scales: List<Pair<Float, String>> = listOf(1f to "100", 1.3f to "130")) {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                KPKNTheme {
                    current?.let { active ->
                        Box(
                            Modifier
                                .width(360.dp)
                                .height(active.height.dp)
                                .background(Color.Black)
                                .testTag("scene-frame"),
                        ) { active.content() }
                    }
                }
            }
        }
        for ((scale, suffix) in scales) {
            rule.runOnUiThread {
                current = scene
                fontScale = scale
            }
            rule.waitForIdle()
            scene.touch?.invoke(rule)
            rule.waitForIdle()
            save(scene.crop, "${scene.name}_$suffix")
        }
    }

    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
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

    private fun ComposeContentTestRule.click(tag: String) {
        onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    // ── datos de ejemplo ─────────────────────────────────────────────────────────────────────────────────

    private val bodyValues = linkedMapOf(
        "Pectorales" to 92, "Deltoides" to 88, "Bíceps" to 95, "Abdomen" to 90, "Core" to 72, "Cuádriceps" to 54,
        "Pantorrillas" to 76, "Trapecio" to 90, "Dorsales" to 85, "Tríceps" to 93, "Erectores Espinales" to 68,
        "Glúteos" to 60, "Isquiosurales" to 58,
    )
    private val bodyBefore = bodyValues + ("Cuádriceps" to 66)

    private val sampleAuge = AugeSnapshot(
        dashboard = RecoveryDashboard(
            overallScore = 89,
            headline = "Listo para entrenar",
            summary = "Recuperación buena en todos los canales.",
            recommendation = "Entrena con normalidad.",
            confidenceLabel = "Alta",
            channels = listOf(
                RecoveryChannelSnapshot(
                    id = RecoveryChannelId.MUSCULAR, title = "Músculos", shortTitle = "Músculos", score = 90,
                    band = RecoveryBand.NORMAL, description = "Recuperación muscular.", action = "Volumen normal.",
                ),
                RecoveryChannelSnapshot(
                    id = RecoveryChannelId.SYSTEM, title = "Energía", shortTitle = "Energía", score = 89,
                    band = RecoveryBand.HIGH, description = "Sistema nervioso.", action = "Buen día.",
                ),
                RecoveryChannelSnapshot(
                    id = RecoveryChannelId.STRUCTURE, title = "Columna", shortTitle = "Columna", score = 83,
                    band = RecoveryBand.NORMAL, description = "Carga estructural.", action = "Carga normal.",
                ),
            ),
        ),
    )

    @Composable
    private fun AdjustSheet(channel: RingChannel, highlighted: Set<String> = emptySet()) {
        RingAdjustSheet(
            initialChannel = channel,
            onDismiss = {},
            energy = RingAdjustLevel(value = 72, before = 88),
            spine = RingAdjustLevel(value = 64, before = 82),
            muscles = RingAdjustMuscles(values = bodyValues, before = bodyBefore, highlighted = highlighted),
            hazeState = HazeState(),
        )
    }

    // ── G5: Energía y Columna ────────────────────────────────────────────────────────────────────────────

    @Test
    fun g5Energia() = draw(Scene(name = "g5_energia", crop = "scene-frame") { AdjustSheet(RingChannel.ENERGIA) })

    @Test
    fun g5Columna() = draw(Scene(name = "g5_columna", crop = "scene-frame") { AdjustSheet(RingChannel.COLUMNA) })

    @Test
    fun g5EnergiaAlta() = draw(
        Scene(name = "g5_energia_alta", crop = "scene-frame") {
            RingAdjustSheet(
                initialChannel = RingChannel.ENERGIA,
                onDismiss = {},
                energy = RingAdjustLevel(value = 96, before = 88),
                hazeState = HazeState(),
            )
        },
        scales = listOf(1f to "100"),
    )

    @Test
    fun g5EnergiaBaja() = draw(
        Scene(name = "g5_energia_baja", crop = "scene-frame") {
            RingAdjustSheet(
                initialChannel = RingChannel.ENERGIA,
                onDismiss = {},
                energy = RingAdjustLevel(value = 12, before = 88),
                hazeState = HazeState(),
            )
        },
        scales = listOf(1f to "100"),
    )

    // ── G6: Músculos ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun g6Frente() = draw(
        Scene(name = "g6_frente", crop = "scene-frame") { AdjustSheet(RingChannel.MUSCULOS, highlighted = setOf("Cuádriceps", "Glúteos", "Pantorrillas")) },
    )

    @Test
    fun g6Espalda() = draw(
        Scene(name = "g6_espalda", crop = "scene-frame") { AdjustSheet(RingChannel.MUSCULOS, highlighted = setOf("Isquiosurales", "Glúteos", "Pantorrillas")) },
    )

    @Test
    fun g6ConMusculosSinZona() = draw(
        Scene(name = "g6_otros", crop = "scene-frame") {
            RingAdjustSheet(
                initialChannel = RingChannel.MUSCULOS,
                onDismiss = {},
                muscles = RingAdjustMuscles(
                    values = bodyValues + mapOf("Antebrazo" to 65, "Aductores" to 80) - "Pectorales",
                    before = bodyBefore,
                    highlighted = setOf("Cuádriceps", "Antebrazo"),
                ),
                hazeState = HazeState(),
            )
        },
        scales = listOf(1f to "100"),
    )

    @Test
    fun enLineaSinTarjeta() = draw(
        Scene(name = "en_linea", crop = "scene-frame", height = 1500) {
            Column(
                Modifier.width(360.dp).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                EnergyColumnAdjust(channel = RingChannel.COLUMNA, value = 64, before = 82, onValueChange = {}, onReset = {})
                MuscleBodyAdjust(
                    values = bodyValues,
                    before = bodyBefore,
                    onValueChange = { _, _ -> },
                    initialSelected = "Cuádriceps",
                    boxedSelection = false,
                    onReset = {},
                )
            }
        },
        scales = listOf(1f to "100"),
    )

    // ── G1: el estado previo ─────────────────────────────────────────────────────────────────────────────

    @Composable
    private fun Estado(voice: Boolean, gleam: Float? = 0.35f) {
        WorkoutReadinessSheet(
            showReadinessSheet = true,
            gender = null,
            sessionMuscleStartingBatteries = linkedMapOf("Cuádriceps" to 54, "Glúteos" to 60, "Isquiosurales" to 58, "Pantorrillas" to 76),
            readinessNeuralStart = 88,
            readinessMuscularStart = 90,
            readinessSpinalStart = 82,
            hazeState = HazeState(),
            onSave = { _, _, _, _, _, _, _, _, _ -> },
            augeSnapshot = sampleAuge,
            voiceSessionEnabled = voice,
            voiceCaptureMode = VoiceCaptureMode.HANDS_FREE,
            forcedGleamProgress = gleam,
            reportedDiscomforts = listOf(
                ReadinessReportedDiscomfort("knee_patellar", "lunes"),
                ReadinessReportedDiscomfort("lumbar", "viernes"),
            ),
            suggestionPillars = listOf("Cuádriceps", "Glúteos", "Isquiosurales", "Pantorrillas", "Erectores Espinales"),
            initialDiscomforts = listOf("lumbar"),
        )
    }

    @Test
    fun estadoConVozApagada() = draw(Scene(name = "estado_voz_apagada", crop = "scene-frame") { Estado(voice = false) })

    @Test
    fun estadoConVozEncendida() = draw(Scene(name = "estado_voz_encendida", crop = "scene-frame") { Estado(voice = true) })

    @Test
    fun estadoAlto() = draw(
        Scene(name = "estado_alto", crop = "scene-frame", height = 1400) { Estado(voice = true) },
        scales = listOf(1.3f to "130"),
    )

    @Test
    fun estadoAbreElAjusteDeEnergia() = draw(
        Scene(name = "estado_ajuste_energia", crop = "scene-frame", touch = { click("readiness-legend-ENERGIA") }) { Estado(voice = true) },
        scales = listOf(1f to "100"),
    )

    @Test
    fun estadoAbreElAjusteDeMusculos() = draw(
        Scene(name = "estado_ajuste_musculos", crop = "scene-frame", touch = { click("readiness-legend-MUSCULOS") }) { Estado(voice = true) },
        scales = listOf(1f to "100"),
    )

    // ── Inicio: el detalle de un canal con el ajuste oficial ─────────────────────────────────────────────

    @Composable
    private fun HomeDetail(channel: RecoveryChannelId) {
        val snapshot = HomeSamples.ringsSample()
        CompositionLocalProvider(LocalRingMuscleAdjustSave provides { _ -> }) {
            RingDetailSheet(
                channel = channel,
                snapshot = snapshot,
                showModelUpdateNotice = false,
                onDismiss = {},
                onDismissAdvisory = {},
                onModelNoticeShown = {},
                perMuscle = snapshot.perMuscle,
            )
        }
    }

    @Test
    fun inicioDetalleEnergia() = draw(Scene(name = "home_detalle_energia", crop = "scene-frame", height = 1800) { HomeDetail(RecoveryChannelId.SYSTEM) })

    @Test
    fun inicioDetalleMusculos() = draw(
        Scene(name = "home_detalle_musculos", crop = "scene-frame", height = 1800) { HomeDetail(RecoveryChannelId.MUSCULAR) },
        scales = listOf(1f to "100"),
    )

    @Test
    fun inicioAjusteDeEnergia() = draw(
        Scene(name = "home_ajuste_energia", crop = "scene-frame", height = 880, touch = { click("home-ring-adjust") }) { HomeDetail(RecoveryChannelId.SYSTEM) },
        scales = listOf(1f to "100"),
    )

    @Test
    fun inicioAjusteDeMusculos() = draw(
        Scene(name = "home_ajuste_musculos", crop = "scene-frame", height = 880, touch = { click("home-ring-adjust") }) { HomeDetail(RecoveryChannelId.MUSCULAR) },
        scales = listOf(1f to "100"),
    )
}
