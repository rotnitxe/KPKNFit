package com.example.kpkn.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.RecoveryChannelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Detalle de un canal de RINGS: el ajuste con deslizador, la rejilla de Músculos y el texto sin rango habitual.
 * El deslizador, la rejilla y los textos se buscan en el árbol sin fusionar: dentro de la hoja no aparecen en el fusionado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class RingDetailSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setDetail(
        channel: RecoveryChannelId,
        snapshot: AugeSnapshot = HomeSamples.ringsSample(),
        onSave: (RecoveryChannelId, Int) -> Unit = { _, _ -> },
        onDismiss: () -> Unit = {},
    ) {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                RingDetailSheet(
                    channel = channel,
                    snapshot = snapshot,
                    showModelUpdateNotice = false,
                    onDismiss = onDismiss,
                    onDismissAdvisory = {},
                    onModelNoticeShown = {},
                    perMuscle = snapshot.perMuscle,
                    onSaveAdjustment = onSave,
                )
            }
        }
    }

    @Test
    fun `mover el deslizador activa Guardar ajuste`() {
        // Músculos está en 64 %: el ajuste sin tocar no tiene nada que guardar.
        setDetail(RecoveryChannelId.MUSCULAR)

        rule.onNodeWithTag("home-ring-save").assertIsNotEnabled()
        rule.onNodeWithTag("home-ring-adjust", useUnmergedTree = true).performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        rule.onNodeWithTag("home-ring-save").assertIsEnabled()
    }

    @Test
    fun `guardar llama a la lambda con el canal y el valor`() {
        var saved: Pair<RecoveryChannelId, Int>? = null
        var dismissed = false
        setDetail(
            RecoveryChannelId.MUSCULAR,
            onSave = { channel, value -> saved = channel to value },
            onDismiss = { dismissed = true },
        )

        rule.onNodeWithTag("home-ring-adjust", useUnmergedTree = true).performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }
        // El botón queda bajo el pliegue de la hoja y el toque no llega: se invoca su acción de clic (lo que haría TalkBack).
        rule.onNodeWithTag("home-ring-save").performSemanticsAction(SemanticsActions.OnClick) { it() }

        assertEquals(RecoveryChannelId.MUSCULAR to 60, saved)
        assertTrue("guardar cierra la hoja", dismissed)
    }

    @Test
    fun `Músculos muestra la rejilla y Energía no`() {
        setDetail(RecoveryChannelId.MUSCULAR)
        rule.onNodeWithTag("home-ring-muscle-grid", useUnmergedTree = true).assertExists()
        // Un músculo sin sesiones no tiene dato: no sale en la rejilla.
        rule.onNodeWithText("Hombros", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `Energía no muestra la rejilla de músculos`() {
        setDetail(RecoveryChannelId.SYSTEM)
        rule.onNodeWithTag("home-ring-muscle-grid", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `sin personalBaseline sale el texto de 4 sesiones`() {
        setDetail(RecoveryChannelId.SYSTEM, snapshot = HomeSamples.ringsSample(baselines = false))
        rule.onNodeWithText("Tu normal aparecerá tras unas 4 sesiones con RINGS al inicio.").assertExists()
    }
}
