package com.example.kpkn.screens.workout.design

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.example.kpkn.screens.workout.components.SetAdjustmentBody
import com.example.kpkn.screens.workout.components.SetTechniqueBody
import com.example.kpkn.screens.workout.components.TechniqueCheckRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las piezas de «Opciones de la serie» (LS7b) montadas sueltas, sin la hoja: Robolectric no entrega toques dentro de `KpknSheet`.
 * Cada prueba comprueba que el control llama con su valor o con su acción.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class WorkoutSetOptionsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laHojaDeOpcionesCambiaElRomYLlamaConElValor() {
        var rom: Int? = null
        rule.setContent {
            WorkoutSetOptionsBody(
                showRom = true,
                romPercent = 100,
                onRomChange = { rom = it },
                assistedReps = 0,
                maxAssistedReps = 8,
                onAssistedStep = {},
                partialReps = 0,
                onPartialStep = {},
                onOpenTechnique = {},
                accentColor = Color.White,
            )
        }

        rule.onNodeWithContentDescription("Rango de movimiento")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(60f) }

        assertEquals(60, rom)
    }

    @Test
    fun anadirUnDropSetLlamaAAnadir() {
        var added = 0
        rule.setContent {
            SetTechniqueBody(
                dropEnabled = true,
                restPauseEnabled = false,
                onToggleDrop = {},
                onToggleRestPause = {},
                dropRows = listOf(TechniqueCheckRow(weightText = "60", repsText = "8")),
                onDropRowsChange = {},
                onAddDrop = { added++ },
                restPauseRows = emptyList(),
                onRestPauseRowsChange = {},
                onAddRestPause = {},
                noteText = "",
                onNoteChange = {},
                isFailedSet = false,
                onFailedSetChange = {},
                showPrMediaShortcut = false,
                onOpenPrMedia = null,
                onFlipBack = {},
                onSave = {},
            )
        }

        rule.onNodeWithText("Añadir drop-set").performClick()
        rule.waitForIdle()

        assertEquals(1, added)
    }

    @Test
    fun elAjusteDeSerieLlamaConSuValor() {
        var severity: Float? = null
        var applied = 0
        rule.setContent {
            SetAdjustmentBody(
                subtitle = "Remo sentado · Serie 2/4",
                readinessScore = 72,
                readinessLabel = "Buena",
                readinessDetail = "Músculos 70 % · Energía 75 %",
                readinessTone = Color.White,
                plannedCaption = null,
                plannedValue = "80",
                plannedUnit = "kg",
                suggestedCaption = null,
                suggestedValue = "76",
                suggestedUnit = "kg",
                suggestedDelta = "−5 %",
                erm = null,
                severity = 0.5f,
                onSeverityChange = { severity = it },
                applyLabel = "Aplicar 76 kg",
                onApply = { applied++ },
                onDismiss = {},
            )
        }

        rule.onNodeWithContentDescription("Intensidad del ajuste")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
        assertEquals(0.8f, severity ?: Float.NaN, 0.001f)

        rule.onNodeWithText("Aplicar 76 kg").performClick()
        rule.waitForIdle()
        assertEquals(1, applied)
    }
}
