package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.sessioneditor.components.exerciseCatalogVariantTagLabel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fila, variantes e isla de selección del catálogo (SE6a): lo que se lee, el círculo, el despliegue y el botón de añadir. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CatalogExerciseRowTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laFilaSeLeeConNombreMusculoYMaterial() {
        rule.setContent {
            CatalogExerciseRow(
                name = "Press banca",
                subtitle = "Pectorales · Barra",
                matchLabel = null,
                thumbnailResId = null,
                pattern = exercisePatternOf("Press banca"),
                selected = false,
                onClick = {},
                onToggleSelection = {},
            )
        }

        rule.onNodeWithText("Press banca").assertExists()
        rule.onNodeWithText("Pectorales · Barra").assertExists()
    }

    @Test
    fun tocarElCirculoLlamaASeleccionar() {
        var toggled = 0
        var opened = 0
        rule.setContent {
            CatalogExerciseRow(
                name = "Press banca",
                subtitle = "Pectorales · Barra",
                matchLabel = null,
                thumbnailResId = null,
                pattern = exercisePatternOf("Press banca"),
                selected = false,
                onClick = { opened++ },
                onToggleSelection = { toggled++ },
            )
        }

        rule.onNodeWithTag("catalog_row_select").performClick()
        rule.waitForIdle()

        assertEquals(1, toggled)
        assertEquals(0, opened)
    }

    @Test
    fun unaDefinicionConVariantesLasDespliega() {
        rule.setContent {
            var open by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize()) {
                CatalogExerciseRow(
                    name = "Press banca",
                    subtitle = "Pectorales · Barra",
                    matchLabel = null,
                    thumbnailResId = null,
                    pattern = exercisePatternOf("Press banca"),
                    selected = false,
                    onClick = { open = !open },
                    onToggleSelection = {},
                    expandable = true,
                    expanded = open,
                )
                if (open) {
                    CatalogVariantChips(
                        values = listOf("barbell", "dumbbells"),
                        labelOf = { exerciseCatalogVariantTagLabel(it, "bench_press") },
                        selectedValue = "barbell",
                        onSelect = {},
                        onOpenFicha = {},
                    )
                }
            }
        }

        rule.onNodeWithText("Con Mancuernas").assertDoesNotExist()
        rule.onNodeWithText("Press banca").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Con Mancuernas").assertExists()
        rule.onNodeWithText("Ver ficha").assertExists()
    }

    @Test
    fun laIslaDiceAnadirDosYLlamaAAnadir() {
        var added = 0
        rule.setContent {
            CatalogSelectionIsland(
                count = 2,
                summary = catalogSelectionSummary(listOf("Press banca", "Remo con barra")),
                listExpanded = false,
                onToggleList = {},
                onAdd = { added++ },
                onSuperset = null,
                hazeState = null,
            )
        }

        rule.onNodeWithText("Press banca y Remo con barra").assertExists()
        rule.onNodeWithText("Añadir 2").assertExists().performClick()
        rule.waitForIdle()

        assertEquals(1, added)
        rule.onNodeWithText("Superserie").assertDoesNotExist()
    }

    @Test
    fun laIslaOfreceSuperserieCuandoSePuede() {
        var superset = 0
        rule.setContent {
            CatalogSelectionIsland(
                count = 2,
                summary = catalogSelectionSummary(listOf("Press banca", "Remo con barra")),
                listExpanded = false,
                onToggleList = {},
                onAdd = {},
                onSuperset = { superset++ },
                hazeState = null,
            )
        }

        rule.onNodeWithText("Superserie").assertExists().performClick()
        rule.waitForIdle()

        assertEquals(1, superset)
    }

    @Test
    fun elResumenDeLaSeleccionSeAbrevia() {
        assertEquals("", catalogSelectionSummary(emptyList()))
        assertEquals("Press banca", catalogSelectionSummary(listOf("Press banca")))
        assertEquals("Press banca y Remo con barra", catalogSelectionSummary(listOf("Press banca", "Remo con barra")))
        assertEquals(
            "Press banca, Remo con barra y 1 más",
            catalogSelectionSummary(listOf("Press banca", "Remo con barra", "Dominadas")),
        )
    }
}
