package com.example.kpkn.ui.components.rings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos
import kotlin.math.sin

/**
 * El ajuste oficial de los anillos en Compose (LV10b): el anillo de Energía y Columna (G5), el cuerpo de Músculos (G6) y la hoja con
 * sus pestañas. Lo que oye TalkBack, lo que responde al toque y lo que devuelve cada control; nunca guardan nada por sí solos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1400dp-xxhdpi")
class RingAdjustTest {

    @get:Rule
    val rule = createComposeRule()

    // ── Energía y Columna ────────────────────────────────────────────────────────────────────────────────────

    /** Muestra el dial con el valor como estado propio y apunta cada valor que entrega. */
    private fun showDial(
        channel: RingChannel = RingChannel.ENERGIA,
        initial: Int = 72,
        before: Int = 88,
        withReset: Boolean = false,
        emitted: MutableList<Int> = mutableListOf(),
        resets: MutableList<Unit> = mutableListOf(),
    ) {
        rule.setContent {
            var value by remember { mutableIntStateOf(initial) }
            Box(Modifier.fillMaxSize()) {
                EnergyColumnAdjust(
                    channel = channel,
                    value = value,
                    before = before,
                    onValueChange = {
                        value = it
                        emitted += it
                    },
                    onReset = if (withReset) ({ resets += Unit; value = before }) else null,
                )
            }
        }
    }

    @Test
    fun `el dial de Energia enseña su nombre, el valor, el antes y la palabra`() {
        showDial(RingChannel.ENERGIA, initial = 72, before = 88)
        rule.onNodeWithText("ENERGÍA").assertExists()
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("72\u00A0%")
        rule.onNodeWithTag("ring-adjust-before").assertTextEquals("antes 88\u00A0%")
        rule.onNodeWithTag("ring-adjust-word").assertTextEquals("Bien, sin más")
    }

    @Test
    fun `el dial de Columna usa sus propias palabras`() {
        showDial(RingChannel.COLUMNA, initial = 82, before = 82)
        rule.onNodeWithText("COLUMNA").assertExists()
        rule.onNodeWithTag("ring-adjust-word").assertTextEquals("Espalda descansada")
    }

    @Test
    fun `mas y menos mueven de 5 en 5`() {
        val emitted = mutableListOf<Int>()
        showDial(initial = 72, emitted = emitted)
        rule.onNodeWithTag("ring-adjust-plus").performClick()
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("77\u00A0%")
        rule.onNodeWithTag("ring-adjust-minus").performClick()
        rule.onNodeWithTag("ring-adjust-minus").performClick()
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("67\u00A0%")
        assertEquals(listOf(77, 72, 67), emitted)
    }

    @Test
    fun `los botones se paran en 0 y en 100 sin entregar valores repetidos`() {
        val emitted = mutableListOf<Int>()
        showDial(initial = 98, emitted = emitted)
        rule.onNodeWithTag("ring-adjust-plus").performClick()
        rule.onNodeWithTag("ring-adjust-plus").performClick()
        assertEquals("100 una sola vez", listOf(100), emitted)
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("100\u00A0%")
    }

    @Test
    fun `el anillo es un deslizador para accesibilidad`() {
        val emitted = mutableListOf<Int>()
        showDial(initial = 72, emitted = emitted)
        val range = rule.onNodeWithTag("ring-adjust-ring").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(72f, range.current, 0.001f)
        assertEquals(0f, range.range.start, 0.001f)
        assertEquals(100f, range.range.endInclusive, 0.001f)
        rule.onNodeWithTag("ring-adjust-ring").performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("40\u00A0%")
        assertEquals(listOf(40), emitted)
        rule.onNodeWithTag("ring-adjust-ring").performSemanticsAction(SemanticsActions.SetProgress) { it(150f) }
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("100\u00A0%")
    }

    /** Toca el anillo a [value] % de la vuelta (sentido horario desde arriba), sobre la banda del anillo. */
    private fun tapRingAt(value: Int) {
        rule.onNodeWithTag("ring-adjust-ring").performTouchInput {
            val half = width / 2f
            val angle = Math.toRadians(value * 3.6)
            click(Offset(half + 0.84f * half * sin(angle).toFloat(), half - 0.84f * half * cos(angle).toFloat()))
        }
    }

    @Test
    fun `tocar el anillo fija el valor de su angulo`() {
        val emitted = mutableListOf<Int>()
        showDial(initial = 72, emitted = emitted)
        tapRingAt(25)
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("25\u00A0%")
        tapRingAt(50)
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("50\u00A0%")
        tapRingAt(75)
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("75\u00A0%")
        assertEquals(listOf(25, 50, 75), emitted)
    }

    @Test
    fun `tocar el centro, donde van los textos, no cambia nada`() {
        val emitted = mutableListOf<Int>()
        showDial(initial = 72, emitted = emitted)
        rule.onNodeWithTag("ring-adjust-ring").performTouchInput { click(center) }
        assertTrue(emitted.isEmpty())
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("72\u00A0%")
    }

    /** Un arrastre empieza en el valor [from], pasa por los de [via] y termina en [to] (todo en % de la vuelta). */
    private fun dragRing(from: Int, via: List<Int>, to: Int) {
        fun point(value: Int, half: Float): Offset {
            val angle = Math.toRadians(value * 3.6)
            return Offset(half + 0.84f * half * sin(angle).toFloat(), half - 0.84f * half * cos(angle).toFloat())
        }
        rule.onNodeWithTag("ring-adjust-ring").performTouchInput {
            val half = width / 2f
            down(point(from, half))
            via.forEach { moveTo(point(it, half)) }
            moveTo(point(to, half))
            up()
        }
    }

    @Test
    fun `arrastrar por el anillo sigue al dedo`() {
        val emitted = mutableListOf<Int>()
        showDial(initial = 72, emitted = emitted)
        dragRing(from = 10, via = listOf(30, 60), to = 50)
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("50\u00A0%")
        assertEquals("pasa por cada tramo", 10, emitted.first())
        assertEquals(50, emitted.last())
    }

    @Test
    fun `dar la vuelta por arriba se queda en 100 en vez de saltar a 0`() {
        val emitted = mutableListOf<Int>()
        showDial(initial = 72, emitted = emitted)
        // Empieza justo a la izquierda de arriba (94) y el dedo cruza la parte de arriba hasta el 6.
        dragRing(from = 94, via = emptyList(), to = 6)
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("100\u00A0%")
        assertTrue("nunca baja de 90 tras empezar en 94: $emitted", emitted.all { it >= 90 })
    }

    @Test
    fun `Restablecer solo sale con onReset y se apaga al volver al antes`() {
        val resets = mutableListOf<Unit>()
        showDial(initial = 88, before = 88, withReset = true, resets = resets)
        rule.onNodeWithTag("ring-adjust-reset").assertIsNotEnabled()
        rule.onNodeWithTag("ring-adjust-minus").performClick()
        rule.onNodeWithTag("ring-adjust-reset").assertIsEnabled()
        rule.onNodeWithTag("ring-adjust-reset").performClick()
        assertEquals(1, resets.size)
        rule.onNodeWithTag("ring-adjust-value").assertTextEquals("88\u00A0%")
        rule.onNodeWithTag("ring-adjust-reset").assertIsNotEnabled()
    }

    @Test
    fun `sin onReset no hay boton de Restablecer`() {
        showDial(withReset = false)
        rule.onNodeWithTag("ring-adjust-reset").assertDoesNotExist()
    }

    // ── Músculos ─────────────────────────────────────────────────────────────────────────────────────────────

    private val allMuscles = mapOf(
        "Pectorales" to 92, "Deltoides" to 88, "Bíceps" to 95, "Abdomen" to 90, "Core" to 72, "Cuádriceps" to 54,
        "Pantorrillas" to 76, "Trapecio" to 90, "Dorsales" to 85, "Tríceps" to 93, "Erectores Espinales" to 68,
        "Glúteos" to 60, "Isquiosurales" to 58,
    )

    private fun showBody(
        values: Map<String, Int> = allMuscles,
        highlighted: Set<String> = emptySet(),
        initialSelected: String? = null,
        withReset: Boolean = false,
        emitted: MutableList<Pair<String, Int>> = mutableListOf(),
        resets: MutableList<Unit> = mutableListOf(),
        fontScale: Float = 1f,
    ) {
        rule.setContent {
            var current by remember { mutableStateOf(values) }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)) {
                Box(Modifier.fillMaxSize()) {
                    MuscleBodyAdjust(
                        values = current,
                        before = values,
                        onValueChange = { id, value ->
                            current = current + (id to value)
                            emitted += id to value
                        },
                        highlighted = highlighted,
                        initialSelected = initialSelected,
                        onReset = if (withReset) ({ resets += Unit; current = values }) else null,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun selectZone(pillar: String) {
        rule.onNodeWithTag("ring-adjust-zone-$pillar").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    @Test
    fun `cada zona de frente es un boton con su nombre y su valor`() {
        showBody()
        rule.onNodeWithTag("ring-adjust-zone-Cuádriceps").assertContentDescriptionEquals("Cuádriceps 54\u00A0%")
        rule.onNodeWithTag("ring-adjust-zone-Pectorales").assertContentDescriptionEquals("Pectorales 92\u00A0%")
        rule.onNodeWithTag("ring-adjust-zone-Deltoides").assertContentDescriptionEquals("Deltoides 88\u00A0%")
        rule.onNodeWithTag("ring-adjust-zone-Bíceps").assertContentDescriptionEquals("Bíceps 95\u00A0%")
        rule.onNodeWithTag("ring-adjust-zone-Abdomen").assertContentDescriptionEquals("Abdomen 90\u00A0%")
        rule.onNodeWithTag("ring-adjust-zone-Core").assertContentDescriptionEquals("Core 72\u00A0%")
        rule.onNodeWithTag("ring-adjust-zone-Pantorrillas").assertContentDescriptionEquals("Pantorrillas 76\u00A0%")
        // Lo de la espalda no está de frente.
        rule.onNodeWithTag("ring-adjust-zone-Trapecio").assertDoesNotExist()
        rule.onNodeWithTag("ring-adjust-zone-Glúteos").assertDoesNotExist()
    }

    @Test
    fun `tocar una zona la elige y la fila de abajo cuenta como esta`() {
        showBody(initialSelected = "Pectorales")
        rule.onNodeWithTag("ring-adjust-selection-name").assertTextEquals("Pectorales")
        rule.onNodeWithTag("ring-adjust-selection-detail").assertTextEquals("92\u00A0% · recuperado")
        selectZone("Cuádriceps")
        rule.onNodeWithTag("ring-adjust-selection-name").assertTextEquals("Cuádriceps")
        rule.onNodeWithTag("ring-adjust-selection-detail").assertTextEquals("54\u00A0% · cargado")
        selectZone("Core")
        rule.onNodeWithTag("ring-adjust-selection-detail").assertTextEquals("72\u00A0% · algo cargado")
    }

    @Test
    fun `los musculos de hoy se marcan y el primero es la zona elegida`() {
        showBody(highlighted = setOf("Cuádriceps", "Glúteos"))
        rule.onNodeWithTag("ring-adjust-selection-name").assertTextEquals("Cuádriceps")
        rule.onNodeWithText("Con borde: los músculos de hoy").assertExists()
    }

    @Test
    fun `sin musculos de hoy no sale la leyenda del borde`() {
        showBody(highlighted = emptySet())
        rule.onNodeWithText("Con borde: los músculos de hoy").assertDoesNotExist()
    }

    @Test
    fun `una zona de espalda como inicial abre el cuerpo por detras`() {
        showBody(initialSelected = "Isquiosurales")
        rule.onNodeWithTag("ring-adjust-zone-Isquiosurales").assertContentDescriptionEquals("Isquiosurales 58\u00A0%")
        rule.onNodeWithTag("ring-adjust-zone-Pectorales").assertDoesNotExist()
        rule.onNodeWithTag("ring-adjust-selection-name").assertTextEquals("Isquiosurales")
    }

    @Test
    fun `mas y menos mueven 10 y devuelven la clave del musculo`() {
        val emitted = mutableListOf<Pair<String, Int>>()
        showBody(highlighted = setOf("Cuádriceps"), emitted = emitted)
        rule.onNodeWithTag("ring-adjust-selection-before").assertDoesNotExist()
        rule.onNodeWithTag("ring-adjust-muscle-plus").performClick()
        rule.onNodeWithTag("ring-adjust-selection-detail").assertTextEquals("64\u00A0% · algo cargado")
        rule.onNodeWithTag("ring-adjust-selection-before").assertTextEquals("antes 54\u00A0%")
        rule.onNodeWithTag("ring-adjust-muscle-minus").performClick()
        rule.onNodeWithTag("ring-adjust-muscle-minus").performClick()
        assertEquals(listOf("Cuádriceps" to 64, "Cuádriceps" to 54, "Cuádriceps" to 44), emitted)
    }

    /** Cuánto bajan los botones − y + respecto al final del texto de la fila: negativo si van a su lado, cero o más si van debajo. */
    private fun buttonsBelowText(): Float {
        val plus = rule.onNodeWithTag("ring-adjust-muscle-plus").fetchSemanticsNode().boundsInRoot
        val detail = rule.onNodeWithTag("ring-adjust-selection-detail").fetchSemanticsNode().boundsInRoot
        return plus.top - detail.bottom
    }

    @Test
    fun `con la letra normal los botones van al lado del texto de la zona elegida`() {
        showBody(highlighted = setOf("Cuádriceps"), fontScale = 1f)
        assertTrue("los botones comparten línea con el texto: ${buttonsBelowText()}", buttonsBelowText() < 0f)
    }

    @Test
    fun `con la letra grande los botones bajan a su linea para que el texto no se parta`() {
        showBody(highlighted = setOf("Cuádriceps"), fontScale = 1.3f)
        assertTrue("los botones van debajo del texto: ${buttonsBelowText()}", buttonsBelowText() >= 0f)
        // Siguen siendo botones de 48 dp (144 px a 3 px/dp) y siguen funcionando.
        val plus = rule.onNodeWithTag("ring-adjust-muscle-plus").fetchSemanticsNode().boundsInRoot
        assertTrue("zona táctil de 48 dp: ${plus.width} x ${plus.height}", plus.width >= 143f && plus.height >= 143f)
    }

    @Test
    fun `los botones de la fila de la zona elegida miden 48 dp aunque se vean de 40`() {
        showBody(highlighted = setOf("Cuádriceps"))
        val minus = rule.onNodeWithTag("ring-adjust-muscle-minus").fetchSemanticsNode().boundsInRoot
        assertTrue("48 dp = 144 px: ${minus.width} x ${minus.height}", minus.width >= 143f && minus.height >= 143f)
    }

    @Test
    fun `Frente y Espalda giran el cuerpo y cada lado enseña sus zonas`() {
        showBody(initialSelected = "Pectorales")
        rule.onNodeWithText("Espalda").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ring-adjust-zone-Trapecio").assertContentDescriptionEquals("Trapecio 90\u00A0%")
        rule.onNodeWithTag("ring-adjust-zone-Pectorales").assertDoesNotExist()
        // El pecho ya no se ve: la zona elegida pasa a una de la espalda.
        rule.onNodeWithTag("ring-adjust-selection-name").assertTextEquals("Trapecio")
        rule.onNodeWithText("Frente").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ring-adjust-zone-Pectorales").assertExists()
        rule.onNodeWithTag("ring-adjust-zone-Trapecio").assertDoesNotExist()
    }

    @Test
    fun `una zona que se ve por los dos lados conserva la eleccion al girar`() {
        showBody(initialSelected = "Pantorrillas")
        rule.onNodeWithText("Espalda").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("ring-adjust-selection-name").assertTextEquals("Pantorrillas")
        rule.onNodeWithTag("ring-adjust-zone-Deltoides").assertExists()
    }

    @Test
    fun `una zona sin dato dice sin datos y se ajusta partiendo de 100`() {
        val emitted = mutableListOf<Pair<String, Int>>()
        showBody(values = allMuscles - "Pectorales", initialSelected = "Pectorales", emitted = emitted)
        rule.onNodeWithTag("ring-adjust-zone-Pectorales").assertContentDescriptionEquals("Pectorales, sin datos")
        rule.onNodeWithTag("ring-adjust-selection-detail").assertTextEquals("Sin datos")
        rule.onNodeWithTag("ring-adjust-muscle-minus").performClick()
        assertEquals(listOf("Pectorales" to 90), emitted)
    }

    @Test
    fun `un musculo con otro nombre se dibuja en su zona y devuelve su propia clave`() {
        val emitted = mutableListOf<Pair<String, Int>>()
        showBody(values = mapOf("Pecho" to 92, "Cuádriceps" to 80), initialSelected = "Pecho", emitted = emitted)
        rule.onNodeWithTag("ring-adjust-zone-Pectorales").assertContentDescriptionEquals("Pectorales 92\u00A0%")
        rule.onNodeWithTag("ring-adjust-muscle-minus").performClick()
        assertEquals(listOf("Pecho" to 82), emitted)
    }

    @Test
    fun `los musculos sin zona salen como chips y se ajustan igual`() {
        val emitted = mutableListOf<Pair<String, Int>>()
        showBody(values = allMuscles + ("Antebrazo" to 65), emitted = emitted)
        rule.onNodeWithTag("ring-adjust-extra-Antebrazo").assertExists()
        rule.onNodeWithText("Antebrazo · 65\u00A0%").assertExists()
        rule.onNodeWithTag("ring-adjust-extra-Antebrazo").performClick()
        rule.onNodeWithTag("ring-adjust-selection-name").assertTextEquals("Antebrazo")
        rule.onNodeWithTag("ring-adjust-muscle-plus").performClick()
        assertEquals(listOf("Antebrazo" to 75), emitted)
    }

    @Test
    fun `sin musculos sin zona no sale la fila de otros`() {
        showBody()
        rule.onNodeWithText("OTROS MÚSCULOS").assertDoesNotExist()
    }

    @Test
    fun `tocar el dibujo elige la zona de debajo del dedo`() {
        showBody(initialSelected = "Pectorales")
        // El muslo derecho (138, 232) y el izquierdo, que es su reflejo (82, 232): los dos son Cuádriceps.
        rule.onNodeWithTag("ring-adjust-body").performTouchInput {
            val scale = height / 360f
            click(Offset(138f * scale, 232f * scale))
        }
        rule.waitForIdle()
        rule.onNodeWithTag("ring-adjust-selection-name").assertTextEquals("Cuádriceps")
        selectZone("Pectorales")
        rule.onNodeWithTag("ring-adjust-body").performTouchInput {
            val scale = height / 360f
            click(Offset(82f * scale, 232f * scale))
        }
        rule.waitForIdle()
        rule.onNodeWithTag("ring-adjust-selection-name").assertTextEquals("Cuádriceps")
    }

    @Test
    fun `tocar fuera de todas las zonas no cambia la eleccion`() {
        showBody(initialSelected = "Pectorales")
        rule.onNodeWithTag("ring-adjust-body").performTouchInput {
            val scale = height / 360f
            click(Offset(10f * scale, 10f * scale))
        }
        rule.waitForIdle()
        rule.onNodeWithTag("ring-adjust-selection-name").assertTextEquals("Pectorales")
    }

    @Test
    fun `arrastrar en horizontal sobre la fila afina de uno en uno`() {
        val emitted = mutableListOf<Pair<String, Int>>()
        showBody(highlighted = setOf("Cuádriceps"), emitted = emitted)
        rule.onNodeWithTag("ring-adjust-selection").performTouchInput {
            down(Offset(width * 0.4f, height / 2f))
            moveBy(Offset(150f, 0f))
            up()
        }
        rule.waitForIdle()
        assertTrue("avanzó: $emitted", emitted.isNotEmpty())
        val last = emitted.last().second
        assertTrue("sube pero no tanto como un paso de 10: $last", last in 55..64)
        assertTrue("de uno en uno", emitted.zipWithNext().all { (a, b) -> b.second - a.second in 0..2 })
    }

    @Test
    fun `Restablecer del cuerpo solo sale con onReset y avisa`() {
        val resets = mutableListOf<Unit>()
        showBody(withReset = true, resets = resets)
        rule.onNodeWithTag("ring-adjust-reset").performClick()
        assertEquals(1, resets.size)
    }

    // ── La hoja ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun showSheet(
        initial: RingChannel,
        channels: List<RingChannel> = listOf(RingChannel.ENERGIA, RingChannel.COLUMNA, RingChannel.MUSCULOS),
        energy: RingAdjustLevel = RingAdjustLevel(value = 72, before = 88),
        spine: RingAdjustLevel = RingAdjustLevel(value = 64, before = 64),
        onDismiss: () -> Unit = {},
        onEnergyReset: () -> Unit = {},
        onSpineReset: () -> Unit = {},
        onMusclesReset: () -> Unit = {},
        onSpineChange: (Int) -> Unit = {},
    ) {
        rule.setContent {
            RingAdjustSheet(
                initialChannel = initial,
                onDismiss = onDismiss,
                energy = energy,
                spine = spine,
                muscles = RingAdjustMuscles(values = allMuscles, before = allMuscles, highlighted = setOf("Cuádriceps")),
                onEnergyReset = onEnergyReset,
                onSpineReset = onSpineReset,
                onMusclesReset = onMusclesReset,
                onSpineChange = onSpineChange,
                channels = channels,
            )
        }
        rule.waitForIdle()
    }

    // El panel recortado de la hoja no recibe toques táctiles en Robolectric y fusiona la semántica de lo que lleva dentro: se busca en
    // el árbol sin fusionar y se invoca la acción de clic de cada control (lo que haría TalkBack).
    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun tap(tag: String) {
        node(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    private fun tapTab(label: String) {
        rule.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitForIdle()
    }

    @Test
    fun `la hoja se abre en el canal pedido`() {
        showSheet(RingChannel.COLUMNA)
        rule.onNodeWithText("Ajusta cómo llegas", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("COLUMNA", useUnmergedTree = true).assertExists()
        node("ring-adjust-tabs").assertExists()
        node("ring-adjust-value").assertTextEquals("64\u00A0%")
    }

    @Test
    fun `las pestañas cambian de canal`() {
        showSheet(RingChannel.ENERGIA)
        node("ring-adjust-value").assertTextEquals("72\u00A0%")
        tapTab("Columna")
        node("ring-adjust-value").assertTextEquals("64\u00A0%")
        tapTab("Músculos")
        node("ring-adjust-body").assertExists()
        node("ring-adjust-selection-name").assertTextEquals("Cuádriceps")
    }

    @Test
    fun `con un solo canal no hay pestañas`() {
        showSheet(RingChannel.ENERGIA, channels = listOf(RingChannel.ENERGIA))
        node("ring-adjust-tabs").assertDoesNotExist()
        node("ring-adjust-value").assertTextEquals("72\u00A0%")
    }

    @Test
    fun `Listo cierra la hoja`() {
        var dismissed = 0
        showSheet(RingChannel.ENERGIA, onDismiss = { dismissed++ })
        tap("ring-adjust-done")
        assertEquals(1, dismissed)
    }

    @Test
    fun `Restablecer solo se activa si el canal abierto esta editado y llama a su restablecer`() {
        var energyResets = 0
        var spineResets = 0
        showSheet(
            RingChannel.ENERGIA,
            energy = RingAdjustLevel(value = 72, before = 88),
            spine = RingAdjustLevel(value = 64, before = 64),
            onEnergyReset = { energyResets++ },
            onSpineReset = { spineResets++ },
        )
        node("ring-adjust-reset").assertIsEnabled()
        tap("ring-adjust-reset")
        assertEquals(1, energyResets)
        assertEquals(0, spineResets)
        // Columna no está editada: su restablecer está apagado.
        tapTab("Columna")
        node("ring-adjust-reset").assertIsNotEnabled()
    }

    @Test
    fun `una marca de editado activa Restablecer aunque el valor haya vuelto al antes`() {
        showSheet(RingChannel.ENERGIA, energy = RingAdjustLevel(value = 88, before = 88, edited = true))
        node("ring-adjust-reset").assertIsEnabled()
    }

    @Test
    fun `los cambios de la hoja salen por el callback de su canal con el valor visible`() {
        val spine = mutableListOf<Int>()
        showSheet(RingChannel.COLUMNA, onSpineChange = { spine += it })
        tap("ring-adjust-plus")
        // Parte de 64 (el valor que ve la persona) y entrega 69; el padre decide qué guarda y cuándo.
        assertEquals(listOf(69), spine)
    }
}
