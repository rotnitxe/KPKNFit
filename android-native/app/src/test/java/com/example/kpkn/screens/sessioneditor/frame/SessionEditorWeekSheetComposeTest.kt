package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.SessionRoadmapOption
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ED-A · A5: la hoja de semana y variantes (días, sesiones del día, variantes A/B/C/D, semanas del programa y crear sesión en un día vacío).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi", application = android.app.Application::class)
class SessionEditorWeekSheetComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val nbsp = " "

    // ───────────────────────────── A5 · semana y variantes ─────────────────────────────

    private val week = listOf(
        Session(id = "lun", name = "Torso A", dayOfWeek = 1, isMainSession = true),
        Session(id = "mie", name = "Torso B", dayOfWeek = 3, isMainSession = true),
        Session(id = "jue-1", name = "Pierna A", dayOfWeek = 4, isMainSession = true),
        Session(id = "jue-2", name = "Pierna B", dayOfWeek = 4, isMainSession = false),
        Session(id = "vie", name = "Torso C", dayOfWeek = 5, isMainSession = true),
    )

    private val roadmap = listOf(
        SessionRoadmapOption(0, 0, 0, 1, "w2", "Macro", "Base", "Acumulación", 5),
        SessionRoadmapOption(0, 0, 0, 2, "w3", "Macro", "Base", "Acumulación", 5),
    )

    private class WeekCalls {
        val selected = mutableListOf<String>()
        val main = mutableListOf<String>()
        val created = mutableListOf<Int>()
        val weeks = mutableListOf<String>()
        val variants = mutableListOf<WeekVariant>()
        val createdVariants = mutableListOf<Pair<WeekVariant, String>>()
        var deleted = 0
        var done = 0
    }

    @Composable
    private fun WeekContent(
        calls: WeekCalls,
        activeVariant: WeekVariant = WeekVariant.A,
        available: List<WeekVariant> = listOf(WeekVariant.A, WeekVariant.B),
    ) {
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            SessionEditorFrameWeekContent(
                orderedDays = frameWeekOrder(1),
                sessions = week,
                currentSessionId = "jue-1",
                currentDayOfWeek = 4,
                competitionKeyDaysInWeek = setOf(7),
                roadmapOptions = roadmap,
                currentWeekId = "w3",
                isSimpleProgram = true,
                hasActiveLoops = true,
                activeVariant = activeVariant,
                availableVariants = available,
                variantName = { if (it == WeekVariant.A) "Original" else "Pierna A – Rápida" },
                nextVariant = nextFreeVariant(available),
                defaultVariantName = "Pierna A – Rápida",
                onSelectSession = { calls.selected += it },
                onSetMainSession = { calls.main += it },
                onCreateSessionForDay = { calls.created += it },
                onSelectRoadmapOption = { calls.weeks += it.weekId },
                onSelectVariant = { calls.variants += it },
                onCreateVariant = { variant, name -> calls.createdVariants += variant to name },
                onDeleteActiveVariant = { calls.deleted++ },
                onDone = { calls.done++ },
            )
        }
    }

    @Test
    fun laSemanaMuestraSusDiasSesionesDelDiaVariantesYSemanas() {
        rule.setContent { WeekContent(WeekCalls()) }

        rule.onNodeWithText("Semana 3 · Acumulación").assertExists()
        listOf("L", "M", "X", "J", "V", "S", "D").forEach { day -> rule.onNodeWithText(day).assertExists() }
        rule.onNodeWithText("SESIONES DE JUEVES", ignoreCase = true).assertExists()
        rule.onNodeWithText("Marcar como principal").assertExists()
        rule.onNodeWithText("VARIANTES DE LA SESIÓN", ignoreCase = true).assertExists()
        rule.onNodeWithText("Original").assertExists()
        rule.onNodeWithText("Pierna A – Rápida").assertExists()
        rule.onNodeWithText("SEMANAS DEL PROGRAMA", ignoreCase = true).assertExists()
        rule.onNodeWithText("B1 · S2").assertExists()
        rule.onNodeWithText("B1 · S3").assertExists()
    }

    @Test
    fun tocarUnDiaConSesionVaAEsaSesionYUnDiaVacioPideCrearla() {
        val calls = WeekCalls()
        rule.setContent { WeekContent(calls) }

        rule.onNodeWithText("L").performClick()
        assertEquals(listOf("lun"), calls.selected)

        rule.onNodeWithText("S").performClick()
        rule.onNodeWithText("¿Crear sesión para Sábado?").assertExists()
        rule.onNodeWithText("Cancelar").performClick()
        assertTrue(calls.created.isEmpty())

        rule.onNodeWithText("S").performClick()
        rule.onNodeWithText("Crear sesión").performClick()
        assertEquals(listOf(6), calls.created)
    }

    @Test
    fun laSesionPrincipalDelDiaSeCambiaDesdeLaHoja() {
        val calls = WeekCalls()
        rule.setContent { WeekContent(calls) }

        rule.onNodeWithText("Marcar como principal").performClick()

        assertEquals(listOf("jue-2"), calls.main)
    }

    @Test
    fun lasVariantesSeEligenSeCreanYSeBorranSoloFueraDeLaOriginal() {
        val calls = WeekCalls()
        rule.setContent { WeekContent(calls, activeVariant = WeekVariant.A) }

        rule.onAllNodesWithText("Eliminar variante activa").assertCountEquals(0)
        rule.onNodeWithText("Pierna A – Rápida").performClick()
        assertEquals(listOf(WeekVariant.B), calls.variants)

        rule.onNodeWithText("Crear sesión derivada").performClick()
        rule.onNodeWithText("Nueva variante").assertExists()
        rule.onNodeWithText("Crear variante").performClick()
        assertEquals(listOf(WeekVariant.C to "Pierna A – Rápida"), calls.createdVariants)
    }

    @Test
    fun conUnaVarianteActivaSePuedeEliminar() {
        val calls = WeekCalls()
        rule.setContent { WeekContent(calls, activeVariant = WeekVariant.B) }

        rule.onNodeWithText("Eliminar variante activa").performClick()

        assertEquals(1, calls.deleted)
        assertEquals(1, calls.done)
    }

    @Test
    fun tocarUnaSemanaDelProgramaLaAbre() {
        val calls = WeekCalls()
        rule.setContent { WeekContent(calls) }

        rule.onNodeWithText("B1 · S2").performClick()

        assertEquals(listOf("w2"), calls.weeks)
    }

    @Test
    fun conTodasLasVariantesYaNoSeOfreceCrearOtra() {
        val all = listOf(WeekVariant.A, WeekVariant.B, WeekVariant.C, WeekVariant.D)
        rule.setContent { WeekContent(WeekCalls(), available = all) }

        rule.onAllNodesWithText("Crear sesión derivada").assertCountEquals(0)
        assertNull(nextFreeVariant(all))
    }

    @Test
    fun laHojaDeSemanaNoDependeDeUnaEscalaDeLetraConcreta() {
        var scale by mutableStateOf(1f)
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides androidx.compose.ui.unit.Density(LocalDensity.current.density, fontScale = scale),
            ) {
                WeekContent(WeekCalls())
            }
        }
        rule.onNodeWithText("Original").assertExists()
        rule.runOnIdle { scale = 1.3f }
        rule.waitForIdle()
        rule.onNodeWithText("Original").assertExists()
        rule.onNodeWithText("B1 · S3").assertExists()
    }
}
