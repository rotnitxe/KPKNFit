package com.example.kpkn.screens.body

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.design.KpknSurface
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.screens.body.design.BODY_RECORD_DELETE_TAG
import com.example.kpkn.screens.body.design.BODY_RECORD_SAVE_TAG
import com.example.kpkn.screens.body.design.BodyRecordDraft
import com.example.kpkn.screens.body.design.BodyRecordSheet
import com.example.kpkn.screens.body.design.bodyRecordFrom
import com.example.kpkn.screens.body.design.bodyRecordNew
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// NATIVE: con gráficos LEGACY, Robolectric no entrega los toques sobre el panel recortado de la hoja (Guardar y Eliminar no respondían).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class BodyRecordSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val now = LocalDateTime.of(2026, 10, 13, 10, 0)

    /** La hoja con su borrador en estado propio, como la monta la pantalla. */
    private fun montar(
        draft: BodyRecordDraft,
        source: BodyHistoryRow? = null,
        onSave: () -> Unit = {},
        onDelete: () -> Unit = {},
    ) {
        rule.setContent {
            var current by remember { mutableStateOf(draft) }
            // Como en la app: la hoja va sobre el negro de la pantalla y con la fuente de desenfoque del vidrio.
            val haze = remember { HazeState() }
            CompositionLocalProvider(LocalHazeState provides haze) {
                Box(modifier = Modifier.fillMaxSize().background(KpknSurface.canvas).hazeSource(state = haze)) {
                    BodyRecordSheet(
                        draft = current,
                        onDraftChange = { current = it },
                        source = source,
                        unit = WizardMassUnit.KG,
                        anchorKg = 64.5,
                        now = now,
                        onSave = onSave,
                        onDelete = onDelete,
                        onDismiss = {},
                    )
                }
            }
        }
        // Termina la entrada de la hoja antes de interactuar: las animaciones cambian dónde cae cada botón.
        rule.mainClock.advanceTimeBy(1_000L)
    }

    @Test
    fun sinPesoNoSeGuarda() {
        var guardado = false
        montar(bodyRecordNew(now), onSave = { guardado = true })
        rule.onNodeWithTag(BODY_RECORD_SAVE_TAG).performClick()
        assertFalse(guardado)
    }

    @Test
    fun conPesoSeGuarda() {
        var guardado = false
        montar(bodyRecordNew(now).copy(weightKg = 64.5), onSave = { guardado = true })
        rule.onNodeWithTag(BODY_RECORD_SAVE_TAG).assertIsEnabled()
        rule.onNodeWithTag(BODY_RECORD_SAVE_TAG).performClick()
        assertTrue(guardado)
    }

    @Test
    fun laEdicionSinCambiosNoSeGuarda() {
        val row = bodySampleHistoryRows().first()
        var guardado = false
        montar(bodyRecordFrom(row), source = row, onSave = { guardado = true })
        rule.onNodeWithTag(BODY_RECORD_SAVE_TAG).performClick()
        assertFalse(guardado)
    }

    @Test
    fun laMedicionDeHealthConnectEsDeSoloLectura() {
        val row = bodySampleHistoryRows().first { it.fromHealthConnect }
        montar(bodyRecordFrom(row), source = row)
        rule.onNodeWithText("Sincronizada con Health Connect. Solo lectura.").assertIsDisplayed()
        rule.onNodeWithText("Cerrar").assertIsDisplayed()
        rule.onNodeWithTag(BODY_RECORD_SAVE_TAG).assertDoesNotExist()
        rule.onNodeWithTag(BODY_RECORD_DELETE_TAG).assertDoesNotExist()
    }

    @Test
    fun eliminarPideConfirmacionYSoloLuegoBorra() {
        val row = bodySampleHistoryRows().first()
        var borrado = false
        montar(bodyRecordFrom(row), source = row, onDelete = { borrado = true })
        rule.onNodeWithTag(BODY_RECORD_DELETE_TAG).performClick()
        rule.mainClock.advanceTimeBy(1_000L)
        // El diálogo se ve en la hoja de render; la comprobación de visibilidad no lo encuentra en el árbol fusionado.
        rule.onNodeWithText("¿Eliminar esta medición?", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Cancelar").performClick()
        assertFalse(borrado)
        rule.onNodeWithTag(BODY_RECORD_DELETE_TAG).performClick()
        rule.onNodeWithText("Eliminar").performClick()
        assertTrue(borrado)
    }

    @Test
    fun unaHoraFuturaNoSeGuarda() {
        var guardado = false
        montar(bodyRecordNew(now).copy(weightKg = 64.5, dateTime = now.plusHours(3)), onSave = { guardado = true })
        rule.onNodeWithText("La hora no puede ser futura.").assertIsDisplayed()
        rule.onNodeWithTag(BODY_RECORD_SAVE_TAG).performClick()
        assertFalse(guardado)
    }
}
