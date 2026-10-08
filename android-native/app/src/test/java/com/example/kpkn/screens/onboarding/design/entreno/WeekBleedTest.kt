package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.kpkn.domain.onboarding.TrainingPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Siete celdas de 48 dp: los días de la energía, del calendario y del inicio de semana. A 360 dp la página deja 312 dp entre sus
 * márgenes (siete celdas de 44,6 dp); la fila «sangra» hacia los márgenes lo justo (12 dp por lado) y cada día mide 48 dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class WeekBleedTest {

    @get:Rule
    val rule = createComposeRule()

    /** La fila dentro de una página de 360 dp con sus márgenes de 24 dp, con la escala de letra [fontScale]. */
    private fun page(fontScale: Float = 1f, content: @Composable () -> Unit) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { content() }
            }
        }
        rule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    // ------------------------------------------------------------ la cuenta

    @Test
    fun theBleedIsJustWhatSevenCellsNeed() {
        // 312 dp (936 px a 3x): faltan 24 dp para 336 → 12 dp por lado.
        assertEquals(36, weekBleedPerSide(available = 936, cells = 7, minCell = 144, maxBleed = 72))
        // Si caben de sobra no sangra.
        assertEquals(0, weekBleedPerSide(available = 1089, cells = 7, minCell = 144, maxBleed = 72))
        assertEquals(0, weekBleedPerSide(available = 1008, cells = 7, minCell = 144, maxBleed = 72))
        // Estrecho: nunca más que el margen.
        assertEquals(72, weekBleedPerSide(available = 816, cells = 7, minCell = 144, maxBleed = 72))
        // Entradas rotas no rompen nada.
        assertEquals(0, weekBleedPerSide(available = 0, cells = 7, minCell = 144, maxBleed = 72))
        assertEquals(0, weekBleedPerSide(available = 936, cells = 0, minCell = 144, maxBleed = 72))
        assertEquals(0, weekBleedPerSide(available = 936, cells = 7, minCell = 144, maxBleed = 0))
    }

    // ------------------------------------------------------------ el día con más energía

    @Composable
    private fun FreshRow(onSelect: (Int) -> Unit = {}) = FreshDayRow(selectedDay = 4, onSelect = onSelect)

    private fun assertSevenCells(prefix: String, minWidth: Float, screenDp: Float) {
        val cells = (1..7).map { bounds("$prefix$it") }
        for ((index, b) in cells.withIndex()) {
            assertTrue("día ${index + 1} mide ${b.width.value} dp de ancho", b.width.value >= minWidth - 0.01f)
            assertTrue("día ${index + 1} mide ${b.height.value} dp de alto", b.height.value >= 48f - 0.01f)
        }
        // Dentro de la pantalla y sin pisarse.
        assertTrue("el primero empieza fuera: ${cells.first().left}", cells.first().left.value >= -0.01f)
        assertTrue("el último acaba fuera: ${cells.last().right}", cells.last().right.value <= screenDp + 0.01f)
        for (i in 1 until cells.size) {
            assertTrue("el día $i se pisa con el siguiente", cells[i - 1].right.value <= cells[i].left.value + 0.01f)
        }
    }

    @Test
    fun theFreshDayCellsAreFortyEightDpWide() {
        page { FreshRow() }
        assertSevenCells("setup-freshday-", 48f, 360f)
    }

    @Test
    fun theFreshDayCellsAreStillFortyEightDpWideWithLargeLetters() {
        page(fontScale = 1.3f) { FreshRow() }
        assertSevenCells("setup-freshday-", 48f, 360f)
    }

    @Test
    fun aTouchInTheBleedZoneChoosesTheFirstDay() {
        var chosen = 0
        page { FreshRow { chosen = it } }
        // El primer día empieza a 12 dp del borde de la pantalla, 12 dp antes del margen de la página (24 dp): se toca a 3 px de su borde.
        val first = bounds("setup-freshday-1")
        assertEquals(12f, first.left.value, 0.6f)
        rule.onNodeWithTag("setup-freshday-1").performTouchInput { click(Offset(3f, height / 2f)) }
        assertEquals(1, chosen)
        val last = bounds("setup-freshday-7")
        rule.onNodeWithTag("setup-freshday-7").performTouchInput { click(Offset(width - 3f, height / 2f)) }
        assertEquals(7, chosen)
        assertEquals(348f, last.right.value, 0.6f)
    }

    // ------------------------------------------------------------ el calendario

    @Composable
    private fun Calendar(places: Set<TrainingPlace> = setOf(TrainingPlace.GYM), onToggle: (Int) -> Unit = {}) = WeekCalendar(
        weekStartDay = 1,
        selectedDays = setOf(1, 3, 5),
        freshestDay = 3,
        onToggleDay = onToggle,
        onWeekStartChange = {},
        places = places,
        dayPlaces = emptyMap(),
        onDayPlace = { _, _ -> },
    )

    @Test
    fun theCalendarDaysAreFortyEightDpTargets() {
        page { Calendar() }
        assertSevenCells("setup-weekday-", 48f, 360f)
    }

    @Test
    fun theCalendarDaysAreStillFortyEightDpWithLargeLetters() {
        page(fontScale = 1.3f) { Calendar() }
        assertSevenCells("setup-weekday-", 48f, 360f)
    }

    @Test
    fun thePlaceOfEachDayIsAFortyEightDpTarget() {
        page { Calendar(places = setOf(TrainingPlace.GYM, TrainingPlace.HOME)) }
        for (day in listOf(1, 3, 5)) {
            val b = bounds("setup-weekplace-$day")
            assertTrue("lugar del día $day: ${b.width} × ${b.height}", b.height.value >= 47.99f && b.width.value >= 47.99f)
        }
    }

    @Test
    fun theWeekStartOptionsAreFortyEightDpWide() {
        page { Calendar() }
        rule.onNodeWithTag("setup-weekstart").performClick()
        rule.waitForIdle()
        assertSevenCells("setup-weekstart-", 48f, 360f)
    }

    @Test
    fun touchingADayInTheBleedZoneTogglesIt() {
        var toggled = 0
        page { Calendar(onToggle = { toggled = it }) }
        rule.onNodeWithTag("setup-weekday-1").performTouchInput { click(Offset(3f, 40f)) }
        assertEquals(1, toggled)
    }
}

/** 320 dp: ni a todo el ancho de la pantalla caben siete celdas de 48 dp (336 dp), así que cada día mide lo máximo posible (45,7 dp). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w320dp-h800dp-xxhdpi")
class WeekBleedNarrowScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theDaysUseTheWholeScreenWidthAndNeverLeaveIt() {
        rule.setContent { Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { FreshDayRow(selectedDay = 2, onSelect = {}) } }
        rule.waitForIdle()
        val cells = (1..7).map { rule.onNodeWithTag("setup-freshday-$it").getUnclippedBoundsInRoot() }
        assertEquals(0f, cells.first().left.value, 0.6f)
        assertEquals(320f, cells.last().right.value, 0.6f)
        for (b in cells) assertTrue("cada día mide ${b.width.value} dp", b.width.value >= 45.5f)
    }
}

/** 411 dp (un teléfono ancho): ya caben sin salirse de los márgenes, y cada día mide más de 48 dp. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WeekBleedWideScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theRowStaysInsideTheMargins() {
        rule.setContent { Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { FreshDayRow(selectedDay = 2, onSelect = {}) } }
        rule.waitForIdle()
        val cells = (1..7).map { rule.onNodeWithTag("setup-freshday-$it").getUnclippedBoundsInRoot() }
        assertEquals(24f, cells.first().left.value, 0.6f)
        assertEquals(387f, cells.last().right.value, 0.6f)
        for (b in cells) assertTrue("cada día mide ${b.width.value} dp", b.width.value >= 48f)
    }
}
