package com.example.kpkn.screens.sessioneditor.frame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionRoadmapOption
import com.example.kpkn.screens.sessioneditor.components.EditorOverlayTestHost
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Geometría y clicks nativos del marco real, sin invocar acciones por SemanticsActions.OnClick. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-mdpi", application = android.app.Application::class)
class SessionEditorFrameIntegrationGeometryTest {
    @get:Rule val rule = createComposeRule()
    private var hostView: View? = null
    private var density = 1f
    private val calls = mutableListOf<String>()

    private fun mount(scale: Float, showWeekOnTap: Boolean = false,
        active: WeekVariant = WeekVariant.B, available: List<WeekVariant> = WeekVariant.entries,
        dayLabel: String = "Jue · Semana 3") {
        rule.setContent {
            val base = LocalDensity.current
            density = base.density
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale), LocalWizardReducedMotion provides true) {
                EditorOverlayTestHost {
                    hostView = LocalView.current
                    var week by remember { mutableStateOf(false) }
                    Box(Modifier.fillMaxSize().background(Color.Black).testTag("frame_geometry_screen")) {
                        SessionEditorFrameTopBar(
                            dayLabel = dayLabel, forward = true, canGoPrevious = true, canGoNext = true,
                            onBack = { calls += "back" }, onPrevious = { calls += "previous" }, onNext = { calls += "next" },
                            onOpenWeek = { calls += "week"; if (showWeekOnTap) week = true },
                            onTransfer = { calls += "transfer" }, onHistory = { calls += "history" },
                        )
                        SessionEditorFrameBottomBar(HazeState(), 3, 58, 60,
                            onAdd = { calls += "add" }, onAssistant = { calls += "assistant" },
                            onRules = { calls += "rules" }, onTime = { calls += "time" },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 12.dp))
                    }
                    if (week) SessionEditorFrameWeekSheet(
                        orderedDays = frameWeekOrder(1),
                        sessions = listOf(
                            Session(id = "main", name = "Pierna A", dayOfWeek = 4, isMainSession = true),
                            Session(id = "second", name = "Pierna B", dayOfWeek = 4),
                        ),
                        currentSessionId = "main", currentDayOfWeek = 4, competitionKeyDaysInWeek = setOf(7),
                        roadmapOptions = listOf(
                            SessionRoadmapOption(0, 0, 0, 1, "w2", "Macro", "Base", "Acumulación", 3),
                            SessionRoadmapOption(0, 0, 0, 2, "w3", "Macro", "Base", "Acumulación", 3)),
                        currentWeekId = "w3", isSimpleProgram = true, hasActiveLoops = true,
                        activeVariant = active, availableVariants = available,
                        variantName = { when (it) {
                            WeekVariant.A -> "Original"
                            WeekVariant.B -> "Pierna A – Rápida"
                            WeekVariant.C -> "Pierna A – Pesada"
                            WeekVariant.D -> "Pierna A – Técnica"
                        } },
                        nextVariant = nextFreeVariant(available), defaultVariantName = "Pierna A – Derivada",
                        onSelectSession = { calls += "session:$it" }, onSetMainSession = { calls += "main:$it" },
                        onCreateSessionForDay = { calls += "create-day:$it" },
                        onSelectRoadmapOption = { calls += "roadmap:${it.weekId}" },
                        onSelectVariant = { calls += "variant:$it" },
                        onCreateVariant = { variant, name -> calls += "create:$variant:$name" },
                        onDeleteActiveVariant = { calls += "delete" },
                        onDismiss = { week = false },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun fullText(label: String) {
        val layout = textLayout(rule.onNodeWithText(label, useUnmergedTree = true))
        val diagnostic = "$label: size=${layout.size}, lines=${(0 until layout.lineCount).map { listOf(layout.getLineLeft(it), layout.getLineRight(it), layout.getLineEnd(it, visibleEnd = true)) }}, overflowHeight=${layout.didOverflowHeight}"
        println(diagnostic)
        save("texto_${label.hashCode()}")
        assertFalse("$label desborda en vertical", layout.didOverflowHeight)
        for (line in 0 until layout.lineCount) assertFalse("$label elidido en línea $line", layout.isLineEllipsized(line))
        assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        for (line in 0 until layout.lineCount) {
            assertTrue("$label recortado a la izquierda", layout.getLineLeft(line) >= -1f)
            assertTrue("$diagnostic recortado a la derecha", layout.getLineRight(line) <= layout.size.width + 1f)
        }
    }

    private fun target(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue("Objetivo estrecho: $bounds", bounds.width / density >= 48f - 0.01f)
        assertTrue("Objetivo bajo: $bounds", bounds.height / density >= 48f - 0.01f)
        val screen = rule.onNodeWithTag("frame_geometry_screen").fetchSemanticsNode().boundsInRoot
        assertTrue("Objetivo fuera: $bounds / $screen", bounds.left >= screen.left - 1f && bounds.right <= screen.right + 1f &&
            bounds.top >= screen.top - 1f && bounds.bottom <= screen.bottom + 1f)
    }

    private fun frameGeometry(scale: Float, name: String) {
        mount(scale)
        listOf("Atrás", "Día anterior", "Día siguiente", "Más opciones").forEach {
            target(rule.onNodeWithContentDescription(it))
        }
        target(rule.onNodeWithTag("session_editor_open_week"))
        fullText("Jue · Semana 3")
        val tags = listOf("time", "add", "assistant", "rules").map { "session_editor_bar_$it" }
        val bounds = tags.map { tag -> target(rule.onNodeWithTag(tag)); rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (left, right) -> assertTrue("Acciones solapadas", left.right <= right.left + 1f) }
        listOf("Añadir", "Asistente", "Reglas").forEach(::fullText)
        save("a1_$name")
        tags.forEach { rule.onNodeWithTag(it).performClick() }
        rule.onNodeWithContentDescription("Atrás").performClick()
        rule.onNodeWithContentDescription("Día anterior").performClick()
        rule.onNodeWithContentDescription("Día siguiente").performClick()
        rule.onNodeWithTag("session_editor_open_week").performClick()
        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("Transferir").performClick()
        rule.onNodeWithContentDescription("Más opciones").performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("Versiones").performClick()
        assertEquals(listOf("time", "add", "assistant", "rules", "back", "previous", "next", "week", "transfer", "history"), calls)
    }

    private fun openWeek() {
        rule.onNodeWithTag("session_editor_open_week").performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
    }

    private fun weekGeometry(scale: Float, name: String) {
        mount(scale, showWeekOnTap = true)
        openWeek()
        // Los siete días deben verse simultáneamente a360: no hacer scroll horizontal para alcanzarlos.
        val days = (1..7).map { day ->
            val node = rule.onNodeWithTag("session_week_day_$day")
            target(node)
            node.fetchSemanticsNode().boundsInRoot
        }
        days.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left + 1f) }
        save("a5_dias_$name")
        // Domingo se pulsa realmente desde esa posición visible; cancelar no crea ninguna sesión.
        rule.onNodeWithTag("session_week_day_7").performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("¿Crear sesión para Domingo?").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertFalse(calls.any { it.startsWith("create-day:") })
        WeekVariant.entries.forEach { variant ->
            val row = rule.onNodeWithTag("session_variant_${variant.name}")
            row.performScrollTo()
            val badge = rule.onNodeWithTag("session_variant_badge_${variant.name}")
            println("Variante $variant: radio=${row.fetchSemanticsNode().boundsInRoot}, badge=${badge.fetchSemanticsNode().boundsInRoot}")
            save("variante_${variant.name}_$name")
            badge.assertIsDisplayed()
            val size = badge.fetchSemanticsNode().size
            assertEquals(22f, size.width / density, 0.01f)
            assertEquals(22f, size.height / density, 0.01f)
            val letter = rule.onNode(hasText(variant.name) and hasAnyAncestor(hasTestTag("session_variant_badge_${variant.name}")), useUnmergedTree = true)
            val style = textLayout(letter).layoutInput.style
            assertEquals(if (variant == WeekVariant.B) WizardColors.text else WizardColors.text.copy(alpha = 0.62f), style.color)
            val bounds = badge.fetchSemanticsNode().boundsInRoot
            val bitmap = bitmap()
            assertEquals(Color(0xFF1A1A1A).toArgb(), bitmap.getPixel((bounds.left + 2 * density).toInt(), bounds.center.y.toInt()))
            bitmap.recycle()
            target(row)
            fullText(when (variant) {
                WeekVariant.A -> "Original"
                WeekVariant.B -> "Pierna A – Rápida"
                WeekVariant.C -> "Pierna A – Pesada"
                WeekVariant.D -> "Pierna A – Técnica"
            })
        }
        rule.onNodeWithTag("session_variant_B").assertIsSelected()
        rule.onNodeWithText("Eliminar variante activa").performScrollTo()
        fullText("Eliminar variante activa")
        save("a5_variantes_$name")
        rule.onNodeWithTag("session_variant_C").performScrollTo().performClick()
        assertEquals(listOf("week", "variant:C"), calls)
    }

    @Test fun marco100En880() = frameGeometry(1f, "880_100")
    @Test fun marco130En880() = frameGeometry(1.3f, "880_130")
    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun marco100En740() = frameGeometry(1f, "740_100")
    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun marco130En740() = frameGeometry(1.3f, "740_130")
    @Test fun semana100En880() = weekGeometry(1f, "880_100")
    @Test fun semana130En880() = weekGeometry(1.3f, "880_130")
    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun semana100En740() = weekGeometry(1f, "740_100")
    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun semana130En740() = weekGeometry(1.3f, "740_130")

    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun originalProtegidaYCrearCancelarNoEjecutaElCallback() {
        mount(1.3f, showWeekOnTap = true, active = WeekVariant.A, available = listOf(WeekVariant.A, WeekVariant.B))
        openWeek()
        rule.onNodeWithText("Eliminar variante activa").assertDoesNotExist()
        rule.onNodeWithTag("session_variant_A").performScrollTo().performClick()
        assertEquals(listOf("week"), calls)
        rule.onNodeWithText("Crear sesión derivada").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("Nueva variante").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").performClick()
        assertEquals(listOf("week"), calls)
        rule.onNodeWithText("Crear sesión derivada").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("Crear variante").performClick()
        assertEquals(listOf("week", "create:C:Pierna A – Derivada"), calls)
    }

    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun principalRoadmapYEliminarMantienenCallbacksReales() {
        mount(1.3f, showWeekOnTap = true)
        openWeek()
        rule.onNodeWithText("Marcar como principal").performScrollTo().performClick()
        openWeek()
        rule.onNodeWithText("B1 · S2").performScrollTo().performClick()
        openWeek()
        rule.onNodeWithText("Eliminar variante activa").performScrollTo().performClick()
        assertEquals(listOf("week", "main:second", "week", "roadmap:w2", "week", "delete"), calls)
    }

    @Test @Config(qualifiers = "w360dp-h740dp-mdpi") fun unNombreLargoConservaLaDecisionDeAbrirSemanaYLaEtiquetaAccesibleCompleta() {
        val longName = "Sesión de accesorios con una descripción extraordinariamente larga"
        mount(1.3f, dayLabel = longName)
        val open = rule.onNodeWithContentDescription("Abrir la semana: $longName")
        target(open)
        open.performClick()
        assertEquals(listOf("week"), calls)
    }

    private fun bitmap(): Bitmap {
        val view = hostView!!
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(image)) }
        return image
    }

    private fun save(name: String) {
        val output = System.getenv("KPKN_RENDER_DIR") ?: return
        val image = bitmap()
        val file = File(output, "int_eda_$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
