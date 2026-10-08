package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La cuadrícula de material con Compose sobre Robolectric: cada implemento tiene su marca de prueba y su
 * semántica de casilla, tocarlo avisa con el id correcto, el estado se anuncia, solo se pintan los que se piden
 * y, con 360 dp de ancho y la fuente al 130 %, nada se sale de su sitio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EquipmentSymbolGridTest {

    @get:Rule
    val rule = createComposeRule()

    private val toggled = mutableListOf<EquipmentSymbolId>()
    private var selected by mutableStateOf(emptySet<EquipmentSymbolId>())

    private fun show(
        symbols: List<EquipmentSymbolId> = EquipmentSymbolId.entries.toList(),
        initial: Set<EquipmentSymbolId> = emptySet(),
        flip: Boolean = true,
    ) {
        selected = initial
        // Como en el wizard, la cuadrícula vive dentro de una página que se desplaza: seis filas no caben en la ventana.
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                EquipmentSymbolGrid(
                    symbols = symbols,
                    selected = selected,
                    onToggle = { id ->
                        toggled += id
                        if (flip) selected = if (id in selected) selected - id else selected + id
                    },
                )
            }
        }
    }

    @Test
    fun allSixteenSymbolsHaveTheirTestTag() {
        show()
        // 15 implementos y «Solo peso corporal»: «Cuerda de saltar» salió de la cuadrícula (ningún ejercicio la usa).
        assertEquals(16, EquipmentSymbolId.entries.size)
        for (id in EquipmentSymbolId.entries) {
            rule.onNodeWithTag("setup-equipment-${id.name}").assertExists()
            assertEquals("setup-equipment-${id.name}", equipmentSymbolTag(id))
        }
    }

    @Test
    fun tappingEachSymbolReportsItsId() {
        show(flip = false)
        for (id in EquipmentSymbolId.entries) {
            rule.onNodeWithTag("setup-equipment-${id.name}").performScrollTo().performClick()
        }
        assertEquals(EquipmentSymbolId.entries.toList(), toggled)
    }

    @Test
    fun announcesTheLabelAndTheState() {
        show(initial = setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.BODYWEIGHT_ONLY))
        rule.onNodeWithTag("setup-equipment-BARBELL").assertContentDescriptionEquals("Barra y discos, seleccionado")
        rule.onNodeWithTag("setup-equipment-BARBELL").assertIsOn()
        rule.onNodeWithTag("setup-equipment-RACK").assertContentDescriptionEquals("Rack, sin seleccionar")
        rule.onNodeWithTag("setup-equipment-RACK").assertIsOff()
        rule.onNodeWithTag("setup-equipment-SMITH").assertContentDescriptionEquals("Smith/Multipower, sin seleccionar")
        rule.onNodeWithTag("setup-equipment-BODYWEIGHT_ONLY").assertContentDescriptionEquals("Solo peso corporal, seleccionado")
    }

    @Test
    fun theStateFollowsTheTaps() {
        show()
        rule.onNodeWithTag("setup-equipment-KETTLEBELL").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-equipment-KETTLEBELL").assertContentDescriptionEquals("Kettlebell, seleccionado")
        rule.onNodeWithTag("setup-equipment-KETTLEBELL").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-equipment-KETTLEBELL").assertContentDescriptionEquals("Kettlebell, sin seleccionar")
    }

    @Test
    fun onlyTheRequestedSymbolsAreShown() {
        show(symbols = listOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS, EquipmentSymbolId.BODYWEIGHT_ONLY))
        rule.onNodeWithTag("setup-equipment-DUMBBELLS").assertExists()
        rule.onNodeWithTag("setup-equipment-BANDS").assertExists()
        rule.onNodeWithTag("setup-equipment-BODYWEIGHT_ONLY").assertExists()
        rule.onNodeWithTag("setup-equipment-BARBELL").assertDoesNotExist()
        rule.onNodeWithTag("setup-equipment-RACK").assertDoesNotExist()
    }

    @Test
    fun withReducedMotionTheSemanticsAreTheSame() {
        selected = setOf(EquipmentSymbolId.CABLE)
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                EquipmentSymbolGrid(
                    symbols = EquipmentSymbolId.entries.toList(),
                    selected = selected,
                    onToggle = { toggled += it },
                    reduced = true,
                )
            }
        }
        rule.onNodeWithTag("setup-equipment-CABLE").assertContentDescriptionEquals("Poleas, seleccionado")
        rule.onNodeWithTag("setup-equipment-MACHINES").assertContentDescriptionEquals("Máquinas, sin seleccionar")
        rule.onNodeWithTag("setup-equipment-MACHINES").performScrollTo().performClick()
        assertEquals(listOf(EquipmentSymbolId.MACHINES), toggled)
    }

    @Test
    fun theGridDoesNotDecideExclusivity() {
        // «Solo peso corporal» es exclusivo en la lógica del paso, no aquí: la cuadrícula pinta lo que le dan.
        show(initial = setOf(EquipmentSymbolId.BODYWEIGHT_ONLY, EquipmentSymbolId.BARBELL))
        rule.onNodeWithTag("setup-equipment-BODYWEIGHT_ONLY").assertIsOn()
        rule.onNodeWithTag("setup-equipment-BARBELL").assertIsOn()
        rule.onNodeWithTag("setup-equipment-RACK").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(
            setOf(EquipmentSymbolId.BODYWEIGHT_ONLY, EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK),
            selected,
        )
    }

    @Test
    fun eachSymbolIsACheckboxWithAnAdequateTouchTarget() {
        show()
        for (id in EquipmentSymbolId.entries) {
            val node = rule.onNodeWithTag("setup-equipment-${id.name}")
            assertEquals(Role.Checkbox, node.fetchSemanticsNode().config[SemanticsProperties.Role])
            node.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun nothingSpillsOutAt360dpWithLargeFonts() {
        val container = 312.dp // 360 dp de pantalla menos el margen de 24 dp a cada lado
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                Box(Modifier.width(container).verticalScroll(rememberScrollState())) {
                    EquipmentSymbolGrid(
                        symbols = EquipmentSymbolId.entries.toList(),
                        selected = setOf(EquipmentSymbolId.SMITH, EquipmentSymbolId.PULL_UP_BAR),
                        onToggle = {},
                    )
                }
            }
        }
        val bounds = EquipmentSymbolId.entries.associateWith {
            rule.onNodeWithTag("setup-equipment-${it.name}").getUnclippedBoundsInRoot()
        }
        for ((id, b) in bounds) {
            assertTrue("$id empieza fuera por la izquierda", b.left.value >= -0.01f)
            assertTrue("$id acaba fuera por la derecha: ${b.right}", b.right.value <= container.value + 0.01f)
        }
        // Tres por fila: los símbolos de una misma fila no se pisan.
        val rows = bounds.entries.groupBy { it.value.top.value.toInt() }
        for ((_, row) in rows) {
            val sorted = row.sortedBy { it.value.left.value }
            for (i in 1 until sorted.size) {
                assertTrue(
                    "${sorted[i - 1].key} se pisa con ${sorted[i].key}",
                    sorted[i - 1].value.right.value <= sorted[i].value.left.value + 0.01f,
                )
            }
            assertTrue("más de tres por fila", row.size <= 3)
        }
    }
}
