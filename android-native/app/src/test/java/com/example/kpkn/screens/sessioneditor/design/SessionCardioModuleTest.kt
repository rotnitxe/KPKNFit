package com.example.kpkn.screens.sessioneditor.design

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Módulo de cardio plegado y módulo de preparación (SE5): la dosis se lee, tocar despliega, una barra por tramo y «Editar» llama. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionCardioModuleTest {

    @get:Rule
    val rule = createComposeRule()

    private val steady = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 1800, targetHrPercent = 65)

    /** HIIT con calentamiento y vuelta a la calma: 17 tramos (1 + 8 esfuerzos con 7 pausas + 1). */
    private val hiit = CardioDetails(
        type = CardioType.BIKE_STATIONARY,
        hiit = CardioHiitConfig(warmupSeconds = 180, workSeconds = 30, restSeconds = 60, rounds = 8, cooldownSeconds = 120),
    )

    private fun cardio(details: CardioDetails, onToggle: () -> Unit = {}) {
        rule.setContent {
            SessionCardioModule(
                name = "Cinta",
                details = details,
                expanded = false,
                onToggle = onToggle,
                onLongPress = {},
                onDragStart = {},
                onDrag = {},
                onDragEnd = {},
                onDragCancel = {},
                onDelete = {},
            )
        }
    }

    @Test
    fun laDosisSeLee() {
        cardio(steady)
        rule.onNodeWithText("30 min · zona 2").assertExists()
    }

    @Test
    fun tocarElNombreDespliega() {
        var toggles = 0
        cardio(steady, onToggle = { toggles++ })
        rule.onNodeWithText("Cinta").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun laMiniLineaTieneUnaBarraPorTramo() {
        cardio(hiit)
        rule.onNodeWithText("8 × 0:30 / 1:00").assertExists()
        assertEquals(17, rule.onAllNodesWithTag(CardioTimelineTag, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun elContinuoNoTieneMiniLinea() {
        cardio(steady)
        assertEquals(0, rule.onAllNodesWithTag(CardioTimelineTag, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun laPreparacionLlamaAEditar() {
        var edits = 0
        rule.setContent {
            SessionPreparationModule(
                rows = listOf(
                    SessionPreparationRow(name = "Movilidad de cadera", dose = "2 min"),
                    SessionPreparationRow(name = "Calentamiento · Sentadilla", dose = "3 × 10"),
                ),
                onEdit = { edits++ },
            )
        }
        rule.onNodeWithText("Movilidad de cadera").assertExists()
        rule.onNodeWithText("2 min").assertExists()
        rule.onNodeWithText("Editar").performClick()
        assertEquals(1, edits)
    }

    @Test
    fun sinDestinoNoHayAccionEditar() {
        rule.setContent {
            SessionPreparationModule(rows = listOf(SessionPreparationRow(name = "Movilidad de cadera", dose = null)))
        }
        assertEquals(0, rule.onAllNodesWithText("Editar").fetchSemanticsNodes().size)
    }
}
