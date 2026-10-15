package com.example.kpkn.render

import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.workout.RestTimerKind
import com.example.kpkn.screens.workout.WorkoutLiveRelatorLine
import com.example.kpkn.screens.workout.WorkoutRestModalState
import com.example.kpkn.screens.workout.components.LivePagerCardFrame
import com.example.kpkn.screens.workout.components.RestLiveCard
import com.example.kpkn.screens.workout.components.RestTimerOverlay
import com.example.kpkn.screens.workout.components.WorkoutRecordFab
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertTrue
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

private const val PHONE_TAG = "lr1g-telefono"
private const val CHIPS_TAG = "lr1g-chips"
private const val BAND_TAG = "lr1g-banda"
private const val CARD_TAG = "lr1g-tarjeta"
private const val DOCK_TAG = "lr1g-cajon"
private const val FAB_TAG = "lr1g-check"

/** Altura de la cabecera con las píldoras (la fila de chips termina aquí, en dp). */
private const val CHIPS_BOTTOM_DP = 128f

/** Cajón plegado: el borde superior del dock queda a 651 dp del borde superior (como en la captura i5_a). */
private const val DOCK_DP = 229f

private val ACCENT = Color(0xFFF49A6E)
private const val LINE_ONE = "Serie 2 de 4: 6 reps a RIR 2."
private const val LINE_TWO = "Hoy: pesos libres: la barra no te guía. Tú estabilizas la trayectoria."

/**
 * HOJA DE RENDER de la sesión en vivo con el relator (LR1g). Sin `KPKN_RENDER_DIR` se salta. Dibuja a 360 × 880 dp la
 * serie con el relator en dos líneas y en una, y el descanso en página con el relator en la banda; mide en dp la banda,
 * el texto, la tarjeta, el cajón y el ✓, y lo deja en `medidas_<nombre>.txt` junto a los PNG.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
class LiveSessionRelatorRenderSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null

    private enum class Variant { SERIE_DOS_LINEAS, SERIE_UNA_LINEA, DESCANSO_PAGINA }

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar el entorno con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    @Test
    fun serie_dos_lineas_100() {
        onlyWhenAsked()
        draw(1f, "serie_dos_lineas_100", Variant.SERIE_DOS_LINEAS)
    }

    @Test
    fun serie_dos_lineas_130() {
        onlyWhenAsked()
        draw(1.3f, "serie_dos_lineas_130", Variant.SERIE_DOS_LINEAS)
    }

    @Test
    fun serie_una_linea_100() {
        onlyWhenAsked()
        draw(1f, "serie_una_linea_100", Variant.SERIE_UNA_LINEA)
    }

    @Test
    fun descanso_pagina_100() {
        onlyWhenAsked()
        draw(1f, "descanso_pagina_100", Variant.DESCANSO_PAGINA)
    }

    @Test
    fun descanso_pagina_130() {
        onlyWhenAsked()
        draw(1.3f, "descanso_pagina_130", Variant.DESCANSO_PAGINA)
    }

    @Test
    fun descanso_overlay_100() {
        onlyWhenAsked()
        drawOverlay(1f, "descanso_overlay_100")
    }

    private fun draw(fontScale: Float, name: String, variant: Variant) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .size(360.dp, 880.dp)
                                .background(Color.Black)
                                .testTag(PHONE_TAG),
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                // Cabecera de maqueta: solo fija la altura de la fila de chips.
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(CHIPS_BOTTOM_DP.dp)
                                        .testTag(CHIPS_TAG),
                                )
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(bottom = DOCK_DP.dp),
                                ) {
                                    RelatorBandAndCard(variant, fontScale)
                                }
                            }
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(DOCK_DP.dp)
                                    .background(Color(0xFF111111))
                                    .testTag(DOCK_TAG),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = (DOCK_DP + 12f).dp)
                                    .testTag(FAB_TAG),
                            ) {
                                WorkoutRecordFab(
                                    sessionAccentColor = ACCENT,
                                    isUpdateMode = false,
                                    enabled = true,
                                    onClick = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val medidas = medir(fontScale, variant)
        File(System.getenv("KPKN_RENDER_DIR")!!).mkdirs()
        File(System.getenv("KPKN_RENDER_DIR")!!, "medidas_$name.txt").writeText(medidas.joinToString("\n"))
        save(PHONE_TAG, name)
        val gapAbove = medidas.value("hueco_chips_texto")
        val gapBelow = medidas.value("hueco_texto_tarjeta")
        assertTrue("huecos $gapAbove / $gapBelow", kotlin.math.abs(gapAbove - gapBelow) <= 2f)
        assertTrue("la tarjeta debe quedar 12 dp por encima del cajón", medidas.value("hueco_tarjeta_cajon") >= 12f)
    }

    @Composable
    private fun BoxScope.RelatorBandAndCard(variant: Variant, fontScale: Float) {
        val density = LocalDensity.current
        val bandHeight = WorkoutUiTokens.liveRelatorBandHeight(density)
        // Como en la pantalla (WorkoutV2Body): la línea recibe la altura de la banda por su propio modifier.
        WorkoutLiveRelatorLine(
            text = if (variant == Variant.SERIE_UNA_LINEA) LINE_ONE else LINE_TWO,
            phaseKey = "lr1g",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(bandHeight)
                .testTag(BAND_TAG),
            accentColor = ACCENT,
        )
        // Altura visual de la tarjeta de la serie medida en LR1a (388 dp al 100 %, 418 dp al 130 %).
        val visual = if (fontScale > 1f) 418f else 388f
        val factor = WorkoutUiTokens.effectiveLivePagerCardScale() * WorkoutUiTokens.LivePagerCardHeightGrowFactor
        val natural = (visual / factor).dp
        LivePagerCardFrame(
            allowContentExpansion = true,
            lockToWorkingSetHeight = false,
            publishWorkingSetHeight = variant != Variant.DESCANSO_PAGINA,
        ) {
            if (variant == Variant.DESCANSO_PAGINA) {
                Box(Modifier.fillMaxWidth().height(natural).testTag(CARD_TAG)) {
                    RestLiveCard(
                        remainingSeconds = 189,
                        totalSeconds = 195,
                        sessionAccentColor = ACCENT,
                        restState = WorkoutRestModalState(
                            exerciseId = "e1",
                            exerciseName = "Sentadilla trasera con barra alta",
                            kind = RestTimerKind.STANDARD,
                            plannedSeconds = 195,
                            endsAtMs = 0L,
                            activeSeconds = 195,
                        ),
                        pendingRestSuggestion = null,
                        lastSetOutcome = null,
                        lastCompletedSet = null,
                        onDecrease = {},
                        onIncrease = {},
                        onSkip = {},
                        onUseAdaptive = null,
                        onExpand = {},
                        skipExerciseLabel = "Saltar series restantes de Sentadilla trasera",
                        onSkipExercise = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(natural)
                        .testTag(CARD_TAG)
                        .background(Color(0xFF111111), RoundedCornerShape(24.dp)),
                )
            }
        }
    }

    /** Descanso a pantalla completa (LR1g): el asa de minimizar centrada, el relator a 12 dp y el aro a 16 dp del relator. */
    private fun drawOverlay(fontScale: Float, name: String) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(
                            Modifier
                                .size(360.dp, 880.dp)
                                .background(Color.Black)
                                .testTag(PHONE_TAG),
                        ) {
                            RestTimerOverlay(
                                state = WorkoutRestModalState(
                                    exerciseId = "e1",
                                    exerciseName = "Sentadilla trasera con barra alta",
                                    kind = RestTimerKind.STANDARD,
                                    plannedSeconds = 195,
                                    endsAtMs = 0L,
                                    activeSeconds = 195,
                                ),
                                remainingSeconds = 189,
                                hazeState = HazeState(),
                                sessionAccentColor = ACCENT,
                                onDecrease = {},
                                onIncrease = {},
                                onSkip = {},
                                onMinimize = {},
                                relatorText = LINE_TWO,
                                relatorPhaseKey = "lr1g",
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val density = 3f
        val asa = rule.onNodeWithContentDescription("Minimizar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val texto = rule.onNode(hasText(LINE_TWO), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val aro = rule.onNodeWithTag("lr1g-aro", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val huecoAsa = (texto.top - asa.bottom) / density
        val huecoAro = (aro.top - texto.bottom) / density
        val medidas = listOf(
            "escala_letra=$fontScale variante=descanso_overlay",
            "asa_inferior=${asa.bottom / density}",
            "relator_superior=${texto.top / density} relator_inferior=${texto.bottom / density}",
            "aro_superior=${aro.top / density}",
            "hueco_asa_relator=$huecoAsa",
            "hueco_relator_aro=$huecoAro",
        )
        File(System.getenv("KPKN_RENDER_DIR")!!).mkdirs()
        File(System.getenv("KPKN_RENDER_DIR")!!, "medidas_$name.txt").writeText(medidas.joinToString("\n"))
        save(PHONE_TAG, name)
        assertTrue("asa→relator $huecoAsa dp", kotlin.math.abs(huecoAsa - 12f) <= 2f)
        assertTrue("relator→aro $huecoAro dp", kotlin.math.abs(huecoAro - 16f) <= 2f)
    }

    /** Medidas en dp de la maqueta, con el texto medido en su caja de línea (sin contar el aire del Box). */
    private fun medir(fontScale: Float, variant: Variant): List<String> {
        val density = 3f
        fun dp(rect: Rect): Pair<Float, Float> = rect.top / density to rect.bottom / density
        val chips = dp(rule.onNodeWithTag(CHIPS_TAG).fetchSemanticsNode().boundsInRoot)
        val band = dp(rule.onNodeWithTag(BAND_TAG).fetchSemanticsNode().boundsInRoot)
        val texto = rule.onNode(hasText(if (variant == Variant.SERIE_UNA_LINEA) LINE_ONE else LINE_TWO), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val textoDp = dp(texto)
        val card = dp(rule.onNodeWithTag(CARD_TAG).fetchSemanticsNode().boundsInRoot)
        val dock = dp(rule.onNodeWithTag(DOCK_TAG).fetchSemanticsNode().boundsInRoot)
        val fab = rule.onNodeWithTag(FAB_TAG).fetchSemanticsNode().boundsInRoot
        return listOf(
            "escala_letra=$fontScale variante=$variant",
            "chips_inferior=${chips.second}",
            "banda_superior=${band.first} banda_inferior=${band.second}",
            "texto_superior=${textoDp.first} texto_inferior=${textoDp.second}",
            "tarjeta_superior=${card.first} tarjeta_inferior=${card.second}",
            "cajon_superior=${dock.first}",
            "check_izquierda=${fab.left / density} check_superior=${fab.top / density} check_inferior=${fab.bottom / density}",
            "hueco_chips_texto=${textoDp.first - chips.second}",
            "hueco_texto_tarjeta=${card.first - textoDp.second}",
            "hueco_tarjeta_cajon=${dock.first - card.second}",
        )
    }

    private fun List<String>.value(key: String): Float =
        first { it.startsWith("$key=") }.substringAfter("=").toFloat()

    /** Recorta la hoja a la caja con la etiqueta [tag] y la guarda como PNG en `KPKN_RENDER_DIR`. */
    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = android.graphics.Canvas(full)
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
}
