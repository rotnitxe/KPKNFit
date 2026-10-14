package com.example.kpkn.screens.workout.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RestPauseData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reverso de técnica con Compose sobre Robolectric (LR0): al marcar como hechas todas las filas de drops, la técnica se registra
 * en la serie; sin marcar nada, no se registra nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w420dp-h1600dp")
class SetCardTechniqueBackTest {

    @get:Rule
    val rule = createComposeRule()

    private var saved: Pair<List<DropSetData>, List<RestPauseData>>? = null

    /** Muestra el reverso con un drop sugerido y lo activa con «Añadir drop-sets». */
    private fun mostrarReversoConDrops() {
        rule.setContent {
            MaterialTheme {
                SetCardTechniqueBack(
                    currentSet = ExerciseSet(id = "set-1", targetReps = 8, weight = 100.0),
                    coverWeightLabel = "100 kg",
                    coverValueLabel = "8",
                    coverIntensityLabel = null,
                    mainWeight = 100.0,
                    mainReps = 8,
                    sessionAccentColor = Color.White,
                    isFailedSet = false,
                    onFailedSetChange = {},
                    onFlipBack = {},
                    onCompleteTechniques = { drops, pauses -> saved = drops to pauses },
                )
            }
        }
        rule.onNodeWithText("Añadir drop-sets").performClick()
    }

    @Test
    fun marcarElDropComoHechoRegistraLaTecnicaEnLaSerie() {
        mostrarReversoConDrops()

        rule.onNodeWithTag(TechniqueDoneToggleTag).performClick()
        rule.waitForIdle()

        val (drops, pauses) = checkNotNull(saved) { "al marcar el drop como hecho no se registró la técnica" }
        assertEquals(1, drops.size)
        assertTrue(drops.first().weight > 0.0)
        assertEquals(3, drops.first().reps)
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun sinMarcarNingunDropNoSeRegistraNada() {
        mostrarReversoConDrops()

        rule.waitForIdle()

        assertNull(saved)
    }
}
