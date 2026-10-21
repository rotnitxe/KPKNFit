package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.RecoveryStatus
import com.example.kpkn.data.models.VoiceCaptureMode
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El estado previo con el ajuste oficial (LV10b): lo que se ajusta va al mismo estado de la hoja y solo los canales editados se
 * entregan como ajustes manuales; las molestias y la voz salen a la vista, sin plegar.
 *
 * Todo vive dentro de [com.example.kpkn.ui.components.KpknSheet], cuyo panel recortado no recibe toques táctiles en Robolectric y fusiona
 * la semántica de lo que lleva dentro: por eso se busca en el árbol sin fusionar y se invoca la acción de clic de cada control (lo que
 * haría TalkBack), igual que en `RingDetailSheetTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1400dp-xxhdpi")
class WorkoutReadinessSheetAdjustTest {

    @get:Rule
    val rule = createComposeRule()

    private val restoration = StateRestorationTester(rule)

    /** Lo que la hoja entrega a `onSave`, con los nombres de sus parámetros. */
    private class Saved(
        val neural: Int,
        val muscular: Int?,
        val spinal: Int,
        val perMuscle: Map<String, Int>,
        val discomforts: List<String>,
        val manualNeural: Int?,
        val manualSpinal: Int?,
        val manualMuscular: Int?,
        val manualMuscleBatteries: Map<String, Int>,
    )

    private var saved: Saved? = null
    private var voiceToggles = 0
    private val modeChanges = mutableListOf<VoiceCaptureMode>()

    private val sessionMuscles = linkedMapOf("Cuádriceps" to 54, "Pectorales" to 92)

    private val enginePerMuscle = mapOf(
        "Cuádriceps" to MuscleRecoveryStatus("Cuádriceps", 54, 20, 12, 8, RecoveryStatus.RECOVERING),
        "Pectorales" to MuscleRecoveryStatus("Pectorales", 92, 0, 60, 4, RecoveryStatus.FRESH),
        "Dorsales" to MuscleRecoveryStatus("Dorsales", 70, 6, 30, 12, RecoveryStatus.RECOVERING),
        // Sin ninguna sesión: su 100 % es el valor por defecto del motor y no se enseña como dato.
        "Deltoides" to MuscleRecoveryStatus("Deltoides", 100, 0, -1, 0, RecoveryStatus.FRESH),
    )

    private fun show(
        voiceEnabled: Boolean = false,
        mode: VoiceCaptureMode = VoiceCaptureMode.HANDS_FREE,
        reported: List<ReadinessReportedDiscomfort> = emptyList(),
        pillars: List<String>? = null,
        initialDiscomforts: List<String> = emptyList(),
        onOpenVoice: (() -> Unit)? = null,
    ) {
        saved = null
        voiceToggles = 0
        modeChanges.clear()
        restoration.setContent {
            KPKNTheme {
                Box(Modifier.fillMaxSize()) {
                    WorkoutReadinessSheet(
                        showReadinessSheet = true,
                        gender = null,
                        sessionMuscleStartingBatteries = sessionMuscles,
                        readinessNeuralStart = 88,
                        readinessMuscularStart = 90,
                        readinessSpinalStart = 82,
                        hazeState = HazeState(),
                        onSave = { neural, muscular, spinal, perMuscle, discomforts, manualNeural, manualSpinal, manualMuscular, manualMuscles ->
                            saved = Saved(neural, muscular, spinal, perMuscle, discomforts, manualNeural, manualSpinal, manualMuscular, manualMuscles)
                        },
                        perMuscle = enginePerMuscle,
                        initialDiscomforts = initialDiscomforts,
                        voiceSessionEnabled = voiceEnabled,
                        voiceCaptureMode = mode,
                        onVoiceToggle = { voiceToggles++ },
                        onOpenVoice = onOpenVoice,
                        onVoiceCaptureModeChange = { modeChanges += it },
                        reportedDiscomforts = reported,
                        suggestionPillars = pillars,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun text(value: String) = rule.onNodeWithText(value, useUnmergedTree = true)

    /** Toca un control de la hoja invocando su acción de clic. */
    private fun tap(tag: String) {
        node(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    private fun confirm(): Saved {
        tap("readiness-confirm")
        return checkNotNull(saved) { "confirmar debía llamar a onSave" }
    }

    private fun openAdjust(channel: String) {
        tap("readiness-legend-$channel")
        node("readiness-adjust-panel").assertExists()
    }

    private fun setDialTo(value: Float) {
        node("ring-adjust-ring").performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        rule.waitForIdle()
    }

    private fun selectZone(pillar: String) {
        node("ring-adjust-zone-$pillar").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    private fun done() {
        tap("ring-adjust-done")
        node("readiness-adjust-panel").assertDoesNotExist()
    }

    // ── el contenido de hoy sigue a la vista ──────────────────────────────────────────────────────────────

    @Test
    fun `el estado previo conserva su titulo, la torre y las leyendas que abren el ajuste`() {
        show()
        text("¿Cómo llegas a entrenar hoy?").assertExists()
        node("readiness-tower").assertExists()
        listOf("MUSCULOS", "ENERGIA", "COLUMNA").forEach { node("readiness-legend-$it").assertHasClickAction() }
        text("MÚSCULOS").assertExists()
        text("ENERGÍA").assertExists()
        text("COLUMNA").assertExists()
    }

    @Test
    fun `la leyenda dice el valor de cada anillo con espacio inseparable`() {
        show()
        // Los músculos son el promedio de los de hoy: (54 + 92) / 2 = 73.
        node("readiness-legend-MUSCULOS").assertExists()
        text("73\u00A0%").assertExists()
        text("88\u00A0%").assertExists()
        text("82\u00A0%").assertExists()
    }

    // ── confirmar solo entrega lo que se editó ────────────────────────────────────────────────────────────

    @Test
    fun `confirmar sin tocar nada no convierte ningun canal en ajuste manual`() {
        show()
        val result = confirm()
        assertNull("Energía", result.manualNeural)
        assertNull("Columna", result.manualSpinal)
        assertNull("Músculos globales", result.manualMuscular)
        assertTrue("músculos", result.manualMuscleBatteries.isEmpty())
        assertEquals(88, result.neural)
        assertEquals(82, result.spinal)
        assertEquals(73, result.muscular)
        assertEquals(sessionMuscles.toMap(), result.perMuscle)
        assertTrue(result.discomforts.isEmpty())
    }

    @Test
    fun `tocar la leyenda de un anillo abre el ajuste en ese canal`() {
        show()
        openAdjust("ENERGIA")
        node("ring-adjust-value").assertTextEquals("88\u00A0%")
        done()
        openAdjust("COLUMNA")
        node("ring-adjust-value").assertTextEquals("82\u00A0%")
        done()
        openAdjust("MUSCULOS")
        node("ring-adjust-body").assertExists()
    }

    @Test
    fun `tocar un anillo de la torre tambien abre el ajuste`() {
        show()
        rule.onNodeWithContentDescription("Energía 88 %", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
        node("readiness-adjust-panel").assertExists()
        node("ring-adjust-value").assertTextEquals("88\u00A0%")
    }

    @Test
    fun `ajustar Energia y confirmar entrega solo Energia`() {
        show()
        openAdjust("ENERGIA")
        setDialTo(60f)
        node("ring-adjust-value").assertTextEquals("60\u00A0%")
        done()
        // La hoja de atrás ya enseña el valor nuevo.
        text("60\u00A0%").assertExists()
        val result = confirm()
        assertEquals(60, result.manualNeural)
        assertEquals(60, result.neural)
        assertNull("Columna sigue sin editar", result.manualSpinal)
        assertTrue(result.manualMuscleBatteries.isEmpty())
    }

    @Test
    fun `la Columna se entrega en la escala visible y solo si se edito`() {
        show()
        openAdjust("COLUMNA")
        setDialTo(70f)
        done()
        val result = confirm()
        // La hoja entrega el valor que se ve; invertirlo para guardarlo es asunto de quien recibe onSave (una sola vez).
        assertEquals(70, result.manualSpinal)
        assertEquals(70, result.spinal)
        assertNull(result.manualNeural)
    }

    @Test
    fun `restablecer devuelve el canal a su valor automatico y deja de ser ajuste manual`() {
        show()
        openAdjust("ENERGIA")
        setDialTo(50f)
        tap("ring-adjust-reset")
        node("ring-adjust-value").assertTextEquals("88\u00A0%")
        done()
        val result = confirm()
        assertNull("restablecer quita la marca de editado", result.manualNeural)
        assertEquals(88, result.neural)
    }

    @Test
    fun `un ajuste que vuelve al valor de partida con los botones sigue contando como editado hasta restablecer`() {
        show()
        openAdjust("ENERGIA")
        tap("ring-adjust-minus")
        tap("ring-adjust-plus")
        node("ring-adjust-value").assertTextEquals("88\u00A0%")
        done()
        val result = confirm()
        assertEquals("tocó el canal: es un ajuste de la persona", 88, result.manualNeural)
    }

    // ── músculos ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `un musculo de hoy se ajusta en el cuerpo y sale como editado`() {
        show()
        openAdjust("MUSCULOS")
        selectZone("Cuádriceps")
        node("ring-adjust-selection-before").assertDoesNotExist()
        tap("ring-adjust-muscle-plus")
        node("ring-adjust-selection-detail").assertTextEquals("64\u00A0% · algo cargado")
        node("ring-adjust-selection-before").assertTextEquals("antes 54\u00A0%")
        done()
        val result = confirm()
        assertEquals(mapOf("Cuádriceps" to 64), result.manualMuscleBatteries)
        assertNull("manualMuscular sigue en null", result.manualMuscular)
        assertEquals(64, result.perMuscle["Cuádriceps"])
        assertEquals(92, result.perMuscle["Pectorales"])
        // El promedio de los músculos de hoy sigue las ediciones: (64 + 92) / 2 = 78.
        assertEquals(78, result.muscular)
        assertNull(result.manualNeural)
        assertNull(result.manualSpinal)
    }

    @Test
    fun `los musculos de hoy salen marcados y los demas tambien se pueden tocar`() {
        show()
        openAdjust("MUSCULOS")
        text("Con borde: los músculos de hoy").assertExists()
        // De frente están los dos de hoy y también el Deltoides, que no tiene sesión: sale sin datos y no un 100 % inventado.
        node("ring-adjust-zone-Deltoides").assertContentDescriptionEquals("Deltoides, sin datos")
        node("ring-adjust-zone-Cuádriceps").assertContentDescriptionEquals("Cuádriceps 54\u00A0%")
    }

    @Test
    fun `un musculo que no es de hoy se entrega como editado sin mover el promedio de los de hoy`() {
        show()
        openAdjust("MUSCULOS")
        rule.onNodeWithText("Espalda").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
        selectZone("Dorsales")
        node("ring-adjust-selection-detail").assertTextEquals("70\u00A0% · algo cargado")
        tap("ring-adjust-muscle-minus")
        done()
        val result = confirm()
        assertEquals(mapOf("Dorsales" to 60), result.manualMuscleBatteries)
        assertEquals("el promedio sigue siendo el de los de hoy", 73, result.muscular)
        assertEquals(60, result.perMuscle["Dorsales"])
    }

    @Test
    fun `Restablecer en Musculos devuelve todos a su valor y quita las marcas`() {
        show()
        openAdjust("MUSCULOS")
        selectZone("Cuádriceps")
        tap("ring-adjust-muscle-minus")
        tap("ring-adjust-reset")
        node("ring-adjust-selection-detail").assertTextEquals("54\u00A0% · cargado")
        done()
        val result = confirm()
        assertTrue(result.manualMuscleBatteries.isEmpty())
        assertEquals(54, result.perMuscle["Cuádriceps"])
    }

    @Test
    fun `recrear conserva los valores y las marcas de los musculos ajustados`() {
        show()
        openAdjust("MUSCULOS")
        selectZone("Cuádriceps")
        tap("ring-adjust-muscle-plus")
        done()
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        val result = confirm()
        assertEquals(mapOf("Cuádriceps" to 64), result.manualMuscleBatteries)
        assertEquals(64, result.perMuscle["Cuádriceps"])
        assertNull(result.manualMuscular)
        assertNull(result.manualNeural)
        assertNull(result.manualSpinal)
    }

    // ── molestias en línea ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `las molestias salen en linea con su buscador y Ninguna elegida`() {
        show(pillars = listOf("Cuádriceps"))
        text("¿ALGUNA MOLESTIA HOY?").assertExists()
        text("Busca o elige abajo").assertExists()
        node("readiness-discomfort-none").assertIsOn()
        // Una sugerida para los cuádriceps de hoy.
        node("readiness-discomfort-hip_front").assertExists()
    }

    @Test
    fun `las molestias de la sesion anterior salen con su dia y elegirlas apaga Ninguna`() {
        show(reported = listOf(ReadinessReportedDiscomfort("knee_patellar", "lunes")))
        text("Rodilla anterior · lunes").assertExists()
        node("readiness-discomfort-knee_patellar").assertIsOff()
        tap("readiness-discomfort-knee_patellar")
        node("readiness-discomfort-knee_patellar").assertIsOn()
        node("readiness-discomfort-none").assertIsOff()
        val result = confirm()
        assertEquals(listOf("knee_patellar"), result.discomforts)
    }

    @Test
    fun `Ninguna limpia lo elegido`() {
        show(reported = listOf(ReadinessReportedDiscomfort("knee_patellar", null)), initialDiscomforts = listOf("knee_patellar"))
        node("readiness-discomfort-none").assertIsOff()
        tap("readiness-discomfort-none")
        node("readiness-discomfort-none").assertIsOn()
        assertTrue(confirm().discomforts.isEmpty())
    }

    @Test
    fun `el buscador perdona una falta y lo elegido sobrevive al limpiar el texto`() {
        show()
        rule.onNode(hasSetTextAction()).performTextReplacement("rodila")
        rule.waitForIdle()
        node("readiness-discomfort-knee_patellar").assertExists()
        tap("readiness-discomfort-knee_patellar")
        rule.onNodeWithContentDescription("Limpiar búsqueda", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
        // Sin texto vuelven los chips y la molestia elegida sigue a la vista, elegida.
        node("readiness-discomfort-knee_patellar").assertIsOn()
        assertEquals(listOf("knee_patellar"), confirm().discomforts)
    }

    @Test
    fun `una busqueda sin resultados lo dice`() {
        show()
        rule.onNode(hasSetTextAction()).performTextReplacement("zzzzzz")
        rule.waitForIdle()
        text("Sin resultados para «zzzzzz»").assertExists()
    }

    @Test
    fun `con pocas molestias no hay Mas ni Menos`() {
        show(pillars = listOf("Cuádriceps"))
        node("readiness-discomfort-more").assertDoesNotExist()
        node("readiness-discomfort-less").assertDoesNotExist()
    }

    // ── la voz ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `el icono delega en el flujo de voz de la sesion cuando esta disponible`() {
        var opened = 0
        show(onOpenVoice = { opened++ })
        tap("readiness-voice-fab")
        assertEquals(1, opened)
        assertEquals(0, voiceToggles)
        node("readiness-voice-toggle").assertDoesNotExist()
    }

    @Test
    fun `el icono de voz abre sus controles y sin modos mientras esta apagada`() {
        show(voiceEnabled = false)
        tap("readiness-voice-fab")
        text("VOZ").assertExists()
        text("Comandos de voz").assertExists()
        node("readiness-voice-toggle").assertIsOff()
        node("readiness-voice-mode-hands-free").assertDoesNotExist()
        node("readiness-voice-mode-music").assertDoesNotExist()
        tap("readiness-voice-toggle")
        assertEquals(1, voiceToggles)
    }

    @Test
    fun `con la voz encendida salen Manos libres y Musica y cambiar de modo avisa`() {
        show(voiceEnabled = true, mode = VoiceCaptureMode.HANDS_FREE)
        tap("readiness-voice-fab")
        node("readiness-voice-toggle").assertIsOn()
        node("readiness-voice-mode-hands-free").assertIsOn()
        node("readiness-voice-mode-music").assertIsOff()
        tap("readiness-voice-mode-music")
        assertEquals(listOf(VoiceCaptureMode.MUSIC), modeChanges)
    }

    @Test
    fun `pulsar el modo ya elegido no lo cambia`() {
        show(voiceEnabled = true, mode = VoiceCaptureMode.HANDS_FREE)
        tap("readiness-voice-fab")
        tap("readiness-voice-mode-hands-free")
        assertTrue(modeChanges.isEmpty())
    }

    @Test
    fun `el i abre la explicacion de la voz`() {
        show(voiceEnabled = true)
        tap("readiness-voice-fab")
        tap("readiness-voice-info")
        rule.onNodeWithText("¿Cómo funciona la voz?").assertExists()
        rule.onNodeWithText("Entendido").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
        rule.onNodeWithText("¿Cómo funciona la voz?").assertDoesNotExist()
    }
}
