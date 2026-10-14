package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CLOCK_TAG = "rest-clock"
private const val MIN_SP = 20
private const val MAX_SP = 46

/**
 * El reloj de descanso cabe SIEMPRE en una línea. Se mide con la letra al 100 % y al 130 %, en una caja de 156 dp (anillo de 204 dp
 * con 24 dp de relleno) y en otra de 124 dp. Al 100 % la letra es exactamente la mayor de 20 a 46 sp que cabe. Al 130 % autoSize
 * busca con paso de 1 sp y puede quedarse a un paso de esa mayor letra; se comprueba que no baja de ahí.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class RestClockTextTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun clock0257At100PercentIn156Dp() = checkClock("02:57", 156.dp, fontScale = 1f)

    @Test
    fun clock1000At100PercentIn156Dp() = checkClock("10:00", 156.dp, fontScale = 1f)

    @Test
    fun clock5959At100PercentIn156Dp() = checkClock("59:59", 156.dp, fontScale = 1f)

    @Test
    fun clock0257At130PercentIn156Dp() = checkClock("02:57", 156.dp, fontScale = 1.3f)

    @Test
    fun clock1000At130PercentIn156Dp() = checkClock("10:00", 156.dp, fontScale = 1.3f)

    @Test
    fun clock5959At130PercentIn156Dp() = checkClock("59:59", 156.dp, fontScale = 1.3f)

    @Test
    fun clock0257At100PercentIn124Dp() = checkClock("02:57", 124.dp, fontScale = 1f)

    @Test
    fun clock0257At130PercentIn124Dp() = checkClock("02:57", 124.dp, fontScale = 1.3f)

    private fun checkClock(text: String, box: Dp, fontScale: Float) {
        // 3 px por dp: una pantalla de 360 dp son 1080 px, como en el teléfono de la prueba de arranque.
        val density = Density(density = 3f, fontScale = fontScale)
        // Anchura de la línea en px de cada tamaño de letra, sin límite de ancho: sirve para saber cuál es el mayor que cabe.
        val naturalPx = mutableMapOf<Int, Float>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                KPKNTheme {
                    Column {
                        Box(Modifier.width(box), contentAlignment = Alignment.Center) {
                            RestClockText(
                                text = text,
                                maxFontSize = MAX_SP.sp,
                                modifier = Modifier.testTag(CLOCK_TAG),
                            )
                        }
                        val style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                        )
                        Column(Modifier.wrapContentWidth(unbounded = true)) {
                            for (sp in MIN_SP..MAX_SP) {
                                BasicText(
                                    text = text,
                                    style = style.copy(fontSize = sp.sp),
                                    maxLines = 1,
                                    softWrap = false,
                                    onTextLayout = { naturalPx[sp] = it.lineWidthPx() },
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        val layout = clockLayout()
        val boxPx = with(density) { box.toPx() }
        val clockPx = layout.lineWidthPx()
        val expectedSp = (MAX_SP downTo MIN_SP).firstOrNull { (naturalPx[it] ?: Float.MAX_VALUE) <= boxPx } ?: MIN_SP
        val fitPx = naturalPx[expectedSp] ?: Float.MAX_VALUE
        val stepBelowPx = naturalPx[expectedSp - 1] ?: 0f
        val label = "«$text» con letra al ${(fontScale * 100).roundToInt()} % en $box"

        assertEquals("$label: debe ocupar una sola línea", 1, layout.lineCount)
        assertFalse("$label: el texto desborda la caja", layout.didOverflowWidth)
        assertTrue("$label: la línea mide $clockPx px y la caja $boxPx px", clockPx <= boxPx + 0.5f)
        if (fontScale == 1f) {
            assertTrue(
                "$label: mide $clockPx px y la mayor letra que cabe ($expectedSp sp) mide $fitPx px",
                abs(clockPx - fitPx) <= 2f,
            )
        } else {
            assertTrue(
                "$label: mide $clockPx px; la mayor letra que cabe ($expectedSp sp) mide $fitPx px y un paso menos $stepBelowPx px",
                clockPx >= stepBelowPx - 2f,
            )
        }
    }

    /** Anchura de la primera línea (px), del borde izquierdo al derecho del texto, sea cual sea el ancho de la caja. */
    private fun TextLayoutResult.lineWidthPx(): Float = getLineRight(0) - getLineLeft(0)

    /** Capa de texto del reloj, la que Compose expone en la semántica del nodo con [CLOCK_TAG]. */
    private fun clockLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(CLOCK_TAG)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        return layouts.single()
    }
}
