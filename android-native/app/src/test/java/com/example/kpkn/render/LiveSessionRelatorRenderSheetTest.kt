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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.workout.RelatorAssistAction
import com.example.kpkn.screens.workout.RelatorAssistActionKind
import com.example.kpkn.screens.workout.RelatorSilentAnimation
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
import org.junit.Assert.assertEquals
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
/** Serie registrada por encima de lo previsto (LR1h): la frase más larga de la serie registrada. */
private const val LINE_REGISTRADA = "Te sobró margen: la próxima puede subir 2,5 kg."
/** Mitad del descanso sin historial (LR1h): cuánto queda y qué sigue. */
private const val LINE_MITAD = "Quedan 2:00. Después, serie 2 de 4: 95 kg × 6."

/** LV2: frases y acciones para probar brillo continuo, tramos de acción y personajes */
private const val LINE_CON_ACCION = "Próxima serie: 95 kg × 6. Si quieres, sube a 97,5 kg."
private val ACTION_SUBE = RelatorAssistAction(
    kind = RelatorAssistActionKind.ADJUST_LOAD,
    label = "sube a 97,5 kg",
    span = "sube a 97,5 kg",
    weightKg = 97.5,
)

private const val LINE_DOS_ACCIONES = "Hoy toca 95 kg × 6: pon 95 kg o cambia por prensa."
private val ACTION_DOS_1 = RelatorAssistAction(
    kind = RelatorAssistActionKind.APPLY_SUGGESTED_LOAD,
    label = "pon 95 kg",
    span = "pon 95 kg",
    weightKg = 95.0,
)
private val ACTION_DOS_2 = RelatorAssistAction(
    kind = RelatorAssistActionKind.OPEN_REPLACE,
    label = "cambia por prensa",
    span = "cambia por prensa",
)

/**
 * HOJA DE RENDER de la sesión en vivo con el relator (LR1g / LV2). Sin `KPKN_RENDER_DIR` se salta. Dibuja a 360 × 880 dp la
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
    fun serie_una_linea_130() {
        onlyWhenAsked()
        draw(1.3f, "serie_una_linea_130", Variant.SERIE_UNA_LINEA)
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

    /** LR1h: la frase de la serie registrada y la de la mitad del descanso, en la página de descanso, a 100 % y 130 %. */
    @Test
    fun descanso_registrada_100() {
        onlyWhenAsked()
        draw(1f, "descanso_registrada_100", Variant.DESCANSO_PAGINA, LINE_REGISTRADA)
    }

    @Test
    fun descanso_registrada_130() {
        onlyWhenAsked()
        draw(1.3f, "descanso_registrada_130", Variant.DESCANSO_PAGINA, LINE_REGISTRADA)
    }

    @Test
    fun descanso_mitad_100() {
        onlyWhenAsked()
        draw(1f, "descanso_mitad_100", Variant.DESCANSO_PAGINA, LINE_MITAD)
    }

    @Test
    fun descanso_mitad_130() {
        onlyWhenAsked()
        draw(1.3f, "descanso_mitad_130", Variant.DESCANSO_PAGINA, LINE_MITAD)
    }

    @Test
    fun descanso_overlay_100() {
        onlyWhenAsked()
        drawOverlay(1f, "descanso_overlay_100")
    }

    // --- Fotogramas LV2: brillo continuo, dos acciones, personajes trabajando, destello y momentos silenciosos ---

    @Test
    fun relator_shimmer_frame1_100() {
        onlyWhenAsked()
        draw(1f, "relator_shimmer_frame1_100", Variant.SERIE_DOS_LINEAS, LINE_CON_ACCION, actions = listOf(ACTION_SUBE), forceShimmerSweepProgress = 0.25f)
    }

    @Test
    fun relator_shimmer_frame1_130() {
        onlyWhenAsked()
        draw(1.3f, "relator_shimmer_frame1_130", Variant.SERIE_DOS_LINEAS, LINE_CON_ACCION, actions = listOf(ACTION_SUBE), forceShimmerSweepProgress = 0.25f)
    }

    @Test
    fun relator_shimmer_frame2_100() {
        onlyWhenAsked()
        draw(1f, "relator_shimmer_frame2_100", Variant.SERIE_DOS_LINEAS, LINE_CON_ACCION, actions = listOf(ACTION_SUBE), forceShimmerSweepProgress = 0.50f)
    }

    @Test
    fun relator_shimmer_frame2_130() {
        onlyWhenAsked()
        draw(1.3f, "relator_shimmer_frame2_130", Variant.SERIE_DOS_LINEAS, LINE_CON_ACCION, actions = listOf(ACTION_SUBE), forceShimmerSweepProgress = 0.50f)
    }

    @Test
    fun relator_dos_lineas_dos_acciones_100() {
        onlyWhenAsked()
        draw(1f, "relator_dos_lineas_dos_acciones_100", Variant.SERIE_DOS_LINEAS, LINE_DOS_ACCIONES, actions = listOf(ACTION_DOS_1, ACTION_DOS_2))
    }

    @Test
    fun relator_dos_lineas_dos_acciones_130() {
        onlyWhenAsked()
        draw(1.3f, "relator_dos_lineas_dos_acciones_130", Variant.SERIE_DOS_LINEAS, LINE_DOS_ACCIONES, actions = listOf(ACTION_DOS_1, ACTION_DOS_2))
    }

    @Test
    fun relator_trabajando_frame1_100() {
        onlyWhenAsked()
        draw(1f, "relator_trabajando_frame1_100", Variant.SERIE_DOS_LINEAS, LINE_CON_ACCION, actions = listOf(ACTION_SUBE), forceWorkingProgress = 0.25f)
    }

    @Test
    fun relator_trabajando_frame1_130() {
        onlyWhenAsked()
        draw(1.3f, "relator_trabajando_frame1_130", Variant.SERIE_DOS_LINEAS, LINE_CON_ACCION, actions = listOf(ACTION_SUBE), forceWorkingProgress = 0.25f)
    }

    @Test
    fun relator_trabajando_frame2_100() {
        onlyWhenAsked()
        draw(1f, "relator_trabajando_frame2_100", Variant.SERIE_DOS_LINEAS, LINE_CON_ACCION, actions = listOf(ACTION_SUBE), forceWorkingProgress = 0.65f)
    }

    @Test
    fun relator_trabajando_frame2_130() {
        onlyWhenAsked()
        draw(1.3f, "relator_trabajando_frame2_130", Variant.SERIE_DOS_LINEAS, LINE_CON_ACCION, actions = listOf(ACTION_SUBE), forceWorkingProgress = 0.65f)
    }

    @Test
    fun relator_hecho_destello_100() {
        onlyWhenAsked()
        draw(1f, "relator_hecho_destello_100", Variant.SERIE_DOS_LINEAS, LINE_CON_ACCION, actions = listOf(ACTION_SUBE), forceSparkProgress = 0.40f)
    }

    @Test
    fun relator_hecho_destello_130() {
        onlyWhenAsked()
        draw(1.3f, "relator_hecho_destello_130", Variant.SERIE_DOS_LINEAS, LINE_CON_ACCION, actions = listOf(ACTION_SUBE), forceSparkProgress = 0.40f)
    }

    @Test
    fun relator_anim_descanso_100() {
        onlyWhenAsked()
        draw(1f, "relator_anim_descanso_100", Variant.DESCANSO_PAGINA, "", silentAnimation = RelatorSilentAnimation.REST_BREATHING)
    }

    @Test
    fun relator_anim_descanso_130() {
        onlyWhenAsked()
        draw(1.3f, "relator_anim_descanso_130", Variant.DESCANSO_PAGINA, "", silentAnimation = RelatorSilentAnimation.REST_BREATHING)
    }

    @Test
    fun relator_anim_record_100() {
        onlyWhenAsked()
        draw(1f, "relator_anim_record_100", Variant.SERIE_DOS_LINEAS, "", silentAnimation = RelatorSilentAnimation.RECORD_CELEBRATION)
    }

    @Test
    fun relator_anim_record_130() {
        onlyWhenAsked()
        draw(1.3f, "relator_anim_record_130", Variant.SERIE_DOS_LINEAS, "", silentAnimation = RelatorSilentAnimation.RECORD_CELEBRATION)
    }

    @Test
    fun relator_anim_preparacion_100() {
        onlyWhenAsked()
        draw(1f, "relator_anim_preparacion_100", Variant.SERIE_DOS_LINEAS, "", silentAnimation = RelatorSilentAnimation.PREPARATION_SQUAT)
    }

    @Test
    fun relator_anim_preparacion_130() {
        onlyWhenAsked()
        draw(1.3f, "relator_anim_preparacion_130", Variant.SERIE_DOS_LINEAS, "", silentAnimation = RelatorSilentAnimation.PREPARATION_SQUAT)
    }

    /** Texto del relator de cada variante: la serie de una línea lleva la frase corta; el resto, la de dos líneas. */
    private fun textoPara(variant: Variant): String = if (variant == Variant.SERIE_UNA_LINEA) LINE_ONE else LINE_TWO

    private fun draw(
        fontScale: Float,
        name: String,
        variant: Variant,
        text: String = textoPara(variant),
        actions: List<RelatorAssistAction> = emptyList(),
        silentAnimation: RelatorSilentAnimation = RelatorSilentAnimation.NONE,
        forceShimmerSweepProgress: Float? = null,
        forceWorkingProgress: Float? = null,
        forceSparkProgress: Float? = null,
    ) {
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
                                // Cabecera de maqueta: fija la altura de la cabecera (CHIPS_BOTTOM_DP) y el límite inferior real de los chips (9 dp arriba del borde inferior, como WorkoutHeaderBar).
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(CHIPS_BOTTOM_DP.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height((CHIPS_BOTTOM_DP - 9f).dp)
                                            .testTag(CHIPS_TAG),
                                    )
                                }
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(bottom = DOCK_DP.dp),
                                ) {
                                    RelatorBandAndCard(
                                        variant = variant,
                                        fontScale = fontScale,
                                        text = text,
                                        actions = actions,
                                        silentAnimation = silentAnimation,
                                        forceShimmerSweepProgress = forceShimmerSweepProgress,
                                        forceWorkingProgress = forceWorkingProgress,
                                        forceSparkProgress = forceSparkProgress,
                                    )
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
        val isSilent = silentAnimation != RelatorSilentAnimation.NONE
        val expectedText = when {
            isSilent -> ""
            forceWorkingProgress != null -> "El equipo está cargando los discos de 97,5\u00A0kg…"
            // Desde c97af14a3 la confirmación con kilos exige el acuse real del registro; el destello forzado no lo tiene.
            forceSparkProgress != null -> "Carga ajustada."
            else -> text
        }
        val lineas = if (isSilent) 0 else lineasDeTexto(expectedText)
        val medidas = medir(fontScale, variant, expectedText, silentAnimation) + (if (!isSilent) listOf("lineas_texto=$lineas") else emptyList())
        File(System.getenv("KPKN_RENDER_DIR")!!).mkdirs()
        File(System.getenv("KPKN_RENDER_DIR")!!, "medidas_$name.txt").writeText(medidas.joinToString("\n"))
        save(PHONE_TAG, name)
        val isWorkingOrDone = forceWorkingProgress != null || forceSparkProgress != null
        if (!isSilent && !isWorkingOrDone) {
            val gapAbove = medidas.value("hueco_chips_texto")
            val gapBelow = medidas.value("hueco_texto_tarjeta")
            assertTrue("huecos $gapAbove / $gapBelow", kotlin.math.abs(gapAbove - gapBelow) <= 2f)
            assertTrue("la tarjeta debe quedar 12 dp por encima del cajón", medidas.value("hueco_tarjeta_cajon") >= 12f)
            assertTrue("el relator debe caber en dos líneas: $lineas", lineas in 1..2)
        } else if (isWorkingOrDone) {
            val bandaTop = medidas.value("banda_superior")
            val bandaBottom = medidas.value("banda_inferior")
            val chipsBottom = medidas.value("chips_inferior")
            val tarjetaTop = medidas.value("tarjeta_superior")
            val gapBandAbove = bandaTop - chipsBottom
            val gapBandBelow = tarjetaTop - bandaBottom
            assertTrue("huecos banda $gapBandAbove / $gapBandBelow", kotlin.math.abs(gapBandAbove - gapBandBelow) <= 2f)
            assertTrue("la tarjeta debe quedar 12 dp por encima del cajón", medidas.value("hueco_tarjeta_cajon") >= 12f)
            assertTrue("el relator debe caber en dos líneas: $lineas", lineas in 1..2)
        } else {
            val h = medidas.value("altura_banda")
            val expectedHeight = WorkoutUiTokens.liveRelatorBandHeight(Density(3f, fontScale)).value
            assertEquals(expectedHeight, h, 0.5f)
            assertTrue("la tarjeta debe quedar 12 dp por encima del cajón", medidas.value("hueco_tarjeta_cajon") >= 12f)
        }
    }

    /** Líneas que ocupa el texto del relator en pantalla (LR1h): la banda admite dos. Sin texto encontrado, -1. */
    private fun lineasDeTexto(text: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        val config = rule.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode().config
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.firstOrNull()?.lineCount ?: -1
    }

    @Composable
    private fun BoxScope.RelatorBandAndCard(
        variant: Variant,
        fontScale: Float,
        text: String,
        actions: List<RelatorAssistAction> = emptyList(),
        silentAnimation: RelatorSilentAnimation = RelatorSilentAnimation.NONE,
        forceShimmerSweepProgress: Float? = null,
        forceWorkingProgress: Float? = null,
        forceSparkProgress: Float? = null,
    ) {
        val density = LocalDensity.current
        val bandHeight = WorkoutUiTokens.liveRelatorBandHeight(density)
        // Como en la pantalla (WorkoutV2Body): la línea recibe la altura de la banda por su propio modifier y el offset de centrado (-4.5 dp).
        WorkoutLiveRelatorLine(
            text = text,
            phaseKey = "lv2",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(bandHeight)
                .offset(y = (-4.5).dp)
                .testTag(BAND_TAG),
            actions = actions,
            accentColor = ACCENT,
            silentAnimation = silentAnimation,
            forceShimmerSweepProgress = forceShimmerSweepProgress,
            forceWorkingProgress = forceWorkingProgress,
            forceSparkProgress = forceSparkProgress,
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
        // El descanso a pantalla completa ya no lleva la frase del relator (franja retirada, D3): el aro va bajo el asa.
        rule.onAllNodes(hasText(LINE_TWO), useUnmergedTree = true).assertCountEquals(0)
        val aro = rule.onNodeWithTag("lr1g-aro", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val huecoAsaAro = (aro.top - asa.bottom) / density
        val medidas = listOf(
            "escala_letra=$fontScale variante=descanso_overlay",
            "asa_inferior=${asa.bottom / density}",
            "aro_superior=${aro.top / density}",
            "hueco_asa_aro=$huecoAsaAro",
        )
        File(System.getenv("KPKN_RENDER_DIR")!!).mkdirs()
        File(System.getenv("KPKN_RENDER_DIR")!!, "medidas_$name.txt").writeText(medidas.joinToString("\n"))
        save(PHONE_TAG, name)
        assertTrue("el aro empieza bajo el asa ($huecoAsaAro dp)", huecoAsaAro >= 0f)
    }

    /** Medidas en dp de la maqueta, con el texto medido en su caja de línea (sin contar el aire del Box). */
    private fun medir(
        fontScale: Float,
        variant: Variant,
        text: String,
        silentAnimation: RelatorSilentAnimation = RelatorSilentAnimation.NONE,
    ): List<String> {
        val density = 3f
        fun dp(rect: Rect): Pair<Float, Float> = rect.top / density to rect.bottom / density
        val chips = dp(rule.onNodeWithTag(CHIPS_TAG).fetchSemanticsNode().boundsInRoot)
        val band = dp(rule.onNodeWithTag(BAND_TAG).fetchSemanticsNode().boundsInRoot)
        val card = dp(rule.onNodeWithTag(CARD_TAG).fetchSemanticsNode().boundsInRoot)
        val dock = dp(rule.onNodeWithTag(DOCK_TAG).fetchSemanticsNode().boundsInRoot)
        val fab = rule.onNodeWithTag(FAB_TAG).fetchSemanticsNode().boundsInRoot

        if (silentAnimation != RelatorSilentAnimation.NONE) {
            val bandHeight = band.second - band.first
            return listOf(
                "escala_letra=$fontScale variante=$variant",
                "chips_inferior=${chips.second}",
                "banda_superior=${band.first} banda_inferior=${band.second}",
                "altura_banda=$bandHeight",
                "tarjeta_superior=${card.first} tarjeta_inferior=${card.second}",
                "cajon_superior=${dock.first}",
                "hueco_tarjeta_cajon=${dock.first - card.second}",
            )
        }

        val texto = rule.onNode(hasText(text), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val textoDp = dp(texto)
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

    private fun List<String>.value(key: String): Float {
        val entry = first { it.startsWith("$key=") || it.contains(" $key=") }
        val token = if (entry.startsWith("$key=")) entry else entry.substring(entry.indexOf(" $key=") + 1)
        return token.substringAfter("$key=").substringBefore(" ").toFloat()
    }

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
