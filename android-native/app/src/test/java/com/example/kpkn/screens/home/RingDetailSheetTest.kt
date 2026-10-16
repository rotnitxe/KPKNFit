package com.example.kpkn.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.RecoveryChannelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Detalle de un canal de RINGS: el ajuste oficial (LV10b: «Ajustar» abre la hoja de ajuste del canal y «Guardar ajuste» guarda solo si
 * algo cambió), la rejilla de Músculos y el texto sin rango habitual. Los botones quedan bajo el pliegue de la hoja y el toque no llega:
 * se invoca su acción de clic (lo que haría TalkBack).
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
        onSaveMuscles: ((Map<String, Int>) -> Unit)? = null,
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalRingMuscleAdjustSave provides onSaveMuscles) {
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
    }

    private fun click(tag: String) {
        rule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    private fun setDialTo(value: Float) {
        rule.onNodeWithTag("ring-adjust-ring", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        rule.waitForIdle()
    }

    // ── Energía y Columna ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `sin tocar nada Guardar ajuste esta apagado`() {
        setDetail(RecoveryChannelId.SYSTEM)
        rule.onNodeWithTag("home-ring-adjust", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("home-ring-save", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun `Ajustar abre la hoja de ajuste en ese canal y sin pestañas`() {
        setDetail(RecoveryChannelId.SYSTEM)
        click("home-ring-adjust")
        rule.onNodeWithTag("ring-adjust-sheet", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Ajusta Energía").assertExists()
        // La energía está en 72 %: el anillo parte de ahí y «antes» es el mismo valor.
        rule.onNodeWithTag("ring-adjust-value", useUnmergedTree = true).assertTextEquals("72\u00A0%")
        rule.onNodeWithTag("ring-adjust-tabs", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `mover el anillo activa Guardar ajuste`() {
        setDetail(RecoveryChannelId.SYSTEM)
        click("home-ring-adjust")
        setDialTo(60f)
        click("ring-adjust-done")
        rule.onNodeWithTag("ring-adjust-sheet", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("home-ring-save", useUnmergedTree = true).assertIsEnabled()
        rule.onNodeWithTag("home-ring-adjust-summary", useUnmergedTree = true).assertTextEquals("Tu ajuste: 60\u00A0%")
    }

    @Test
    fun `guardar llama a la lambda con el canal y el valor y cierra el detalle`() {
        var saved: Pair<RecoveryChannelId, Int>? = null
        var dismissed = false
        setDetail(
            RecoveryChannelId.SYSTEM,
            onSave = { channel, value -> saved = channel to value },
            onDismiss = { dismissed = true },
        )
        click("home-ring-adjust")
        setDialTo(60f)
        click("ring-adjust-done")
        click("home-ring-save")

        assertEquals(RecoveryChannelId.SYSTEM to 60, saved)
        assertTrue("guardar cierra la hoja", dismissed)
    }

    @Test
    fun `Columna se guarda con su canal y en la escala que se ve`() {
        var saved: Pair<RecoveryChannelId, Int>? = null
        setDetail(RecoveryChannelId.STRUCTURE, onSave = { channel, value -> saved = channel to value })
        click("home-ring-adjust")
        rule.onNodeWithTag("ring-adjust-value", useUnmergedTree = true).assertTextEquals("80\u00A0%")
        setDialTo(55f)
        click("ring-adjust-done")
        click("home-ring-save")
        // El valor sale tal como se ve: la Columna se invierte una vez, al guardar en AugeViewModel (applyManualBatteries).
        assertEquals(RecoveryChannelId.STRUCTURE to 55, saved)
    }

    @Test
    fun `volver al valor de partida no deja nada que guardar`() {
        setDetail(RecoveryChannelId.SYSTEM)
        click("home-ring-adjust")
        setDialTo(60f)
        setDialTo(72f)
        click("ring-adjust-done")
        rule.onNodeWithTag("home-ring-save", useUnmergedTree = true).assertIsNotEnabled()
        rule.onNodeWithTag("home-ring-adjust-summary", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `Restablecer en la hoja quita el ajuste`() {
        setDetail(RecoveryChannelId.SYSTEM)
        click("home-ring-adjust")
        setDialTo(40f)
        click("ring-adjust-reset")
        rule.onNodeWithTag("ring-adjust-value", useUnmergedTree = true).assertTextEquals("72\u00A0%")
        click("ring-adjust-done")
        rule.onNodeWithTag("home-ring-save", useUnmergedTree = true).assertIsNotEnabled()
    }

    // ── Músculos ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Musculos abre el cuerpo y guarda cada musculo por su pilar`() {
        var savedMuscles: Map<String, Int>? = null
        var savedGlobal: Pair<RecoveryChannelId, Int>? = null
        setDetail(
            RecoveryChannelId.MUSCULAR,
            onSave = { channel, value -> savedGlobal = channel to value },
            onSaveMuscles = { savedMuscles = it },
        )
        click("home-ring-adjust")
        rule.onNodeWithTag("ring-adjust-body", useUnmergedTree = true).assertExists()
        // «Pectorales» está en 58 %: un paso de +10 lo deja en 68.
        click("ring-adjust-zone-Pectorales")
        rule.onNodeWithTag("ring-adjust-selection-detail", useUnmergedTree = true).assertTextEquals("58\u00A0% · cargado")
        click("ring-adjust-muscle-plus")
        click("ring-adjust-done")
        rule.onNodeWithTag("home-ring-adjust-summary", useUnmergedTree = true).assertTextEquals("1 músculo ajustado")
        click("home-ring-save")

        assertEquals(mapOf("Pectorales" to 68), savedMuscles)
        assertNull("no se escribe un global de Músculos inventado", savedGlobal)
    }

    @Test
    fun `un musculo con otro nombre se guarda con la clave de su pilar`() {
        var savedMuscles: Map<String, Int>? = null
        setDetail(RecoveryChannelId.MUSCULAR, onSaveMuscles = { savedMuscles = it })
        click("home-ring-adjust")
        // «Isquiotibiales» (74 %) es el pilar «Isquiosurales»: se ve por detrás.
        rule.onNodeWithText("Espalda").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
        click("ring-adjust-zone-Isquiosurales")
        click("ring-adjust-muscle-minus")
        click("ring-adjust-done")
        click("home-ring-save")
        assertEquals(mapOf("Isquiosurales" to 64), savedMuscles)
    }

    @Test
    fun `sin cambios en el cuerpo Guardar ajuste sigue apagado`() {
        setDetail(RecoveryChannelId.MUSCULAR, onSaveMuscles = {})
        rule.onNodeWithTag("home-ring-save", useUnmergedTree = true).assertIsNotEnabled()
        click("home-ring-adjust")
        click("ring-adjust-done")
        rule.onNodeWithTag("home-ring-save", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun `Musculos sin quien guarde no ofrece el ajuste pero conserva su rejilla`() {
        setDetail(RecoveryChannelId.MUSCULAR, onSaveMuscles = null)
        rule.onNodeWithTag("home-ring-adjust", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("home-ring-save", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("home-ring-muscle-grid", useUnmergedTree = true).assertExists()
    }

    // ── lo de siempre ────────────────────────────────────────────────────────────────────────────────────────

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

    @Test
    fun `sin dato en el canal no hay ajuste`() {
        setDetail(RecoveryChannelId.STRUCTURE, snapshot = HomeSamples.ringsSample(structure = null))
        rule.onNodeWithTag("home-ring-adjust", useUnmergedTree = true).assertDoesNotExist()
    }
}
