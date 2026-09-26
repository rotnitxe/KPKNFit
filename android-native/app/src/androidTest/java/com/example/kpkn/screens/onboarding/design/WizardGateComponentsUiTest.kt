package com.example.kpkn.screens.onboarding.design

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.nutrition.bodyFatForSliderPos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Invariantes de interacción del prototipo visual (Fase 1):
 *
 * - La posición inicial de regla y rueda no es una respuesta (nada emite al
 *   montar sin interacción).
 * - Tocar el valor central (el texto grande de la regla / el ítem central de
 *   la rueda) confirma exactamente ese valor una sola vez, sin haber movido el
 *   control y sin píldora ni CTA secundario.
 * - La rueda muestra cinco valores alrededor del central, como la referencia.
 * - Los hitos solo muestran párrafo de la etapa actual; pasadas y futuras
 *   quedan resumidas a número y título.
 * - El CTA del scaffold expone `setup-continue` con `Role.Button` y su estado
 *   deshabilitado real (ocupado/inválido) para TalkBack.
 * - El selector de figura acepta el controlado opcional (`model`,
 *   `sliderPosition`) sin reiniciar al reabrir y sin emitir al montar.
 *
 * Los textos con decimales se construyen con las mismas escalas que el
 * producción (`WizardWeightScale` / `WizardHeightScale` / `bodyFatForSliderPos`),
 * así que la aserción es canónica con independencia del idioma del dispositivo.
 */
class WizardGateComponentsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun weightRuleInitialPositionDoesNotEmit() {
        val received = mutableListOf<Double>()
        composeRule.setContent {
            WizardWeightRule(
                unit = WizardMassUnit.KG,
                valueKg = null,
                onValueChange = { received.add(it) },
            )
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<Double>(), received)
    }

    @Test
    fun heightWheelInitialPositionDoesNotEmit() {
        val received = mutableListOf<Int>()
        composeRule.setContent {
            WizardHeightWheel(
                unit = WizardHeightUnit.CM,
                cm = null,
                onValueChange = { received.add(it) },
            )
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<Int>(), received)
    }

    @Test
    fun weightRuleCenterValueTapConfirmsCentralValueExactlyOnce() {
        val received = mutableListOf<Double>()
        var confirms = 0
        composeRule.setContent {
            WizardWeightRule(
                unit = WizardMassUnit.KG,
                valueKg = null,
                onValueChange = { received.add(it) },
                onConfirm = { confirms++ },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(WizardWeightScale.formatWithUnit(70.0, WizardMassUnit.KG)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(70.0), received)
        assertEquals(1, confirms)
    }

    @Test
    fun heightWheelCenterValueTapConfirmsCentralValueExactlyOnce() {
        val received = mutableListOf<Int>()
        var confirms = 0
        composeRule.setContent {
            WizardHeightWheel(
                unit = WizardHeightUnit.CM,
                cm = null,
                onValueChange = { received.add(it) },
                onConfirm = { confirms++ },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(cmLabel(175)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(175), received)
        assertEquals(1, confirms)
    }

    @Test
    fun heightWheelShowsFiveVisibleRowsAroundTheCenter() {
        composeRule.setContent {
            WizardHeightWheel(
                unit = WizardHeightUnit.CM,
                cm = null,
                onValueChange = {},
            )
        }
        composeRule.waitForIdle()

        // Referencia W/p3: cinco valores visibles con el central entre guías.
        composeRule.onNodeWithText(cmLabel(173)).assertIsDisplayed()
        composeRule.onNodeWithText(cmLabel(174)).assertIsDisplayed()
        composeRule.onAllNodesWithText(cmLabel(175)).assertCountEquals(1)
        composeRule.onNodeWithText(cmLabel(175)).assertIsDisplayed()
        composeRule.onNodeWithText(cmLabel(176)).assertIsDisplayed()
        composeRule.onNodeWithText(cmLabel(177)).assertIsDisplayed()
    }

    @Test
    fun weightRuleCenterTapAfterUnitToggleKeepsTheSameCanonicalValue() {
        var unit by mutableStateOf(WizardMassUnit.KG)
        val received = mutableListOf<Double>()
        composeRule.setContent {
            Column {
                WizardMassUnitToggle(selected = unit, onSelected = { unit = it })
                WizardWeightRule(
                    unit = unit,
                    valueKg = null,
                    onValueChange = { received.add(it) },
                    onConfirm = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(WizardWeightScale.formatWithUnit(70.0, WizardMassUnit.KG)).performClick()
        composeRule.waitForIdle()
        assertEquals(70.0, received.first(), 0.0001)

        composeRule.onNodeWithText("LB").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(WizardWeightScale.formatWithUnit(70.0, WizardMassUnit.LB)).performClick()
        composeRule.waitForIdle()

        assertEquals(2, received.size)
        // Reexpresar 70 kg en libras muestra 154.3 lb; al volver a kg la cuantización
        // de 0,1 lb devuelve ≈69.99 kg (sin deriva acumulada).
        assertEquals(70.0, received.last(), 0.05)
    }

    @Test
    fun milestonesShowHeroNumberedStatesAndBodyOnlyForCurrent() {
        composeRule.setContent {
            WizardMilestones(
                heroTitle = "¡Empecemos!",
                heroSubtitle = "Tu programa personalizado te espera",
                items = listOf(
                    WizardMilestoneItem(WizardBlock.BASICS, "Datos básicos", "Cuerpo", WizardMilestoneState.DONE),
                    WizardMilestoneItem(WizardBlock.TRAINING, "Entreno", "Entrenar", WizardMilestoneState.CURRENT),
                    WizardMilestoneItem(WizardBlock.NUTRITION, "Nutrición", "Comer", WizardMilestoneState.PENDING),
                ),
            )
        }
        composeRule.onNodeWithText("¡Empecemos!").assertIsDisplayed()
        composeRule.onNodeWithText("Tu programa personalizado te espera").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1. Datos básicos: DONE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2. Entreno: CURRENT").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("3. Nutrición: PENDING").assertIsDisplayed()
        // Solo la etapa actual lleva párrafo; pasadas y futuras quedan resumidas.
        composeRule.onNodeWithText("Entrenar").assertIsDisplayed()
        composeRule.onNodeWithText("Cuerpo").assertDoesNotExist()
        composeRule.onNodeWithText("Comer").assertDoesNotExist()
    }

    // ─── Etiquetas estables de los controles (roundtrip / restauración) ───────

    @Test
    fun weightRuleCenterTagRestoresThePersistedValueAndConfirmsItOnce() {
        val received = mutableListOf<Double>()
        var confirms = 0
        composeRule.setContent {
            WizardWeightRule(
                unit = WizardMassUnit.KG,
                valueKg = 72.5,
                onValueChange = { received.add(it) },
                onConfirm = { confirms++ },
            )
        }
        composeRule.waitForIdle()

        // Restauración: el centro muestra exactamente el valor recuperado.
        composeRule.onNode(
            hasTestTag("setup-weight-value") and hasText(WizardWeightScale.formatWithUnit(72.5, WizardMassUnit.KG)),
        ).assertExists()

        composeRule.onNodeWithTag("setup-weight-value").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(72.5), received)
        assertEquals(1, confirms)
    }

    @Test
    fun weightRuleCenterTapKeepsWorkingWhenOptionalOnConfirmIsOmitted() {
        // API en bruto: `onConfirm` es opcional y sus llamantes antiguos siguen íntegros.
        val received = mutableListOf<Double>()
        composeRule.setContent {
            WizardWeightRule(
                unit = WizardMassUnit.KG,
                valueKg = null,
                onValueChange = { received.add(it) },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup-weight-value").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(70.0), received)
    }

    @Test
    fun heightWheelCenterTagRestoresThePersistedValueAndConfirmsItOnce() {
        val received = mutableListOf<Int>()
        var confirms = 0
        composeRule.setContent {
            WizardHeightWheel(
                unit = WizardHeightUnit.CM,
                cm = 172,
                onValueChange = { received.add(it) },
                onConfirm = { confirms++ },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNode(
            hasTestTag("setup-height-value") and hasText(cmLabel(172)),
        ).assertExists()

        composeRule.onNodeWithTag("setup-height-value").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(172), received)
        assertEquals(1, confirms)
    }

    // ─── CTA del scaffold: rol y estado ocupado/inválido ──────────────────────

    @Test
    fun scaffoldCtaReportsItselfAsADisabledButtonWhileBusyOrInvalid() {
        composeRule.setContent {
            WizardScaffold(
                block = WizardBlock.BASICS,
                title = "Datos básicos",
                progress = 0.2f,
                ctaLabel = "Continuar",
                ctaEnabled = false,
                onCta = {},
            ) {
                Text("¿Cuánto quieres entrenar?")
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup-continue").assertIsDisplayed()
        composeRule.onNodeWithTag("setup-continue").assertIsNotEnabled()
        composeRule.onNode(
            hasTestTag("setup-continue") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).assertExists()
        composeRule.onNodeWithText("Continuar").assertExists()
    }

    @Test
    fun scaffoldCtaFiresTheContinuarActionOnceWhenEnabled() {
        var clicks = 0
        composeRule.setContent {
            WizardScaffold(
                block = WizardBlock.BASICS,
                title = "Datos básicos",
                progress = 0.2f,
                ctaLabel = "Continuar",
                ctaEnabled = true,
                onCta = { clicks++ },
            ) {
                Text("¿Cuánto quieres entrenar?")
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup-continue").assertIsEnabled()
        composeRule.onNodeWithTag("setup-continue").performClick()
        composeRule.waitForIdle()

        assertEquals(1, clicks)
    }

    // ─── Tarjeta de selección: semántica de radio y estado de solo lectura ────

    @Test
    fun choiceCardKeepsRadioSemanticsSelectionAndDisabledState() {
        var clicks = 0
        composeRule.setContent {
            Column {
                WizardChoiceCard(title = "Lunes", selected = true, onClick = { clicks++ })
                WizardChoiceCard(title = "Domingo", selected = false, enabled = false, onClick = { clicks += 10 })
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Lunes").assertIsSelected()
        composeRule.onNode(
            hasText("Lunes") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
        ).assertExists()

        // Tarjeta de solo lectura (deshabilitada): conserva el rol y no está activa.
        composeRule.onNodeWithText("Domingo").assertIsNotSelected()
        composeRule.onNodeWithText("Domingo").assertIsNotEnabled()

        composeRule.onNodeWithText("Lunes").performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }

    // ─── Selector de figura: API controlada sin emisiones al montar ──────────

    @Test
    fun physiqueControlledModelRoundtripEmitsCanonicalValuesWithoutMountingDeclarations() {
        val modelChanges = mutableListOf<String>()
        val positionChanges = mutableListOf<Float>()
        val candidateChanges = mutableListOf<WizardBodyFatValue>()
        var model by mutableStateOf("female")
        var position by mutableStateOf(4f)

        composeRule.setContent {
            WizardPhysiqueSelector(
                candidate = null,
                onCandidateChange = { candidateChanges += it },
                model = model,
                sliderPosition = position,
                onModelChange = { modelChanges += it; model = it },
                onSliderPositionChange = { positionChanges += it; position = it },
            )
        }
        composeRule.waitForIdle()

        // Nada declarado al montar.
        assertTrue(modelChanges.isEmpty())
        assertTrue(positionChanges.isEmpty())
        assertTrue(candidateChanges.isEmpty())

        // Restauración exacta del modelo controlado.
        composeRule.onNodeWithText("Ejemplos ♀").assertIsSelected()

        // Roundtrip: cambiar de figura emite el valor canónico y no toca la
        // posición ni el candidato (el personaje nunca cambia `equationSex`).
        composeRule.onNodeWithText("Ejemplos ♂").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("male"), modelChanges)
        assertEquals("male", model)
        composeRule.onNodeWithText("Ejemplos ♂").assertIsSelected()
        composeRule.onNodeWithText("Ejemplos ♀").assertIsNotSelected()
        assertTrue(positionChanges.isEmpty())
        assertTrue(candidateChanges.isEmpty())

        composeRule.onNodeWithText("Ejemplos ♀").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("male", "female"), modelChanges)
    }

    @Test
    fun physiqueRestoresSliderPositionAndConfirmsCurrentPercentWithTruthfulSource() {
        val candidateChanges = mutableListOf<WizardBodyFatValue>()
        val positionChanges = mutableListOf<Float>()
        var position by mutableStateOf(6f)

        composeRule.setContent {
            WizardPhysiqueSelector(
                candidate = null,
                onCandidateChange = { candidateChanges += it },
                sliderPosition = position,
                onSliderPositionChange = { positionChanges += it; position = it },
            )
        }
        composeRule.waitForIdle()

        // Contrato por defecto (pantalla productiva): sin selector de fuente
        // duplicado ni segundo CTA dentro del control.
        composeRule.onNodeWithText("Estimación visual").assertDoesNotExist()

        val restored = bodyFatForSliderPos(6f)
        val percentLine = "≈ ${restored.roundToInt()} % de grasa corporal (estimación visual)"
        composeRule.onNodeWithText(percentLine).assertIsDisplayed()
        // Reabrir con la posición recuperada no emite nada.
        assertTrue(positionChanges.isEmpty())
        assertTrue(candidateChanges.isEmpty())

        // Confirmar sobre el texto central: reporta el % actual y su fuente real.
        composeRule.onNodeWithText(percentLine).performClick()
        composeRule.waitForIdle()

        assertEquals(1, candidateChanges.size)
        val reported = candidateChanges.single()
        assertEquals(restored, reported.percent, 0.0001)
        assertEquals(WizardBodyFatSource.VISUAL_ESTIMATE, reported.source)
        assertTrue(positionChanges.isEmpty())
    }

    @Test
    fun physiqueVisualOnlyDropsDuplicatedSourceAndSecondCtaButKeepsCenterConfirmation() {
        val candidateChanges = mutableListOf<WizardBodyFatValue>()

        composeRule.setContent {
            WizardPhysiqueSelector(
                candidate = null,
                onCandidateChange = { candidateChanges += it },
                mode = WizardPhysiqueMode.VISUAL_ONLY,
            )
        }
        composeRule.waitForIdle()

        // La pantalla productiva elige la fuente fuera: sin selector duplicado
        // ni segundo CTA (pill) dentro del control.
        composeRule.onNodeWithText("Estimación visual").assertDoesNotExist()
        composeRule.onNodeWithText("Conozco mi %").assertDoesNotExist()
        val secondCta = composeRule.onAllNodesWithText("como estado actual", true).fetchSemanticsNodes()
        assertTrue("no debe quedar un segundo CTA", secondCta.isEmpty())
        assertTrue(candidateChanges.isEmpty())

        val percentLine =
            "≈ ${bodyFatForSliderPos(4f).roundToInt()} % de grasa corporal (estimación visual)"
        composeRule.onNodeWithText(percentLine).performClick()
        composeRule.waitForIdle()

        assertEquals(1, candidateChanges.size)
        assertEquals(WizardBodyFatSource.VISUAL_ESTIMATE, candidateChanges.single().source)
        assertEquals(bodyFatForSliderPos(4f), candidateChanges.single().percent, 0.0001)
    }

    @Test
    fun physiqueCompleteModeKeepsSourceSelectorAndPillForTheVisualGate() {
        composeRule.setContent {
            WizardPhysiqueSelector(
                candidate = null,
                onCandidateChange = {},
                mode = WizardPhysiqueMode.COMPLETE,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Estimación visual").assertIsDisplayed()
        composeRule.onNodeWithText("Conozco mi %").assertIsDisplayed()
        val secondCta = composeRule.onAllNodesWithText("como estado actual", true).fetchSemanticsNodes()
        assertEquals(1, secondCta.size)
    }

    /**
     * Rótulo canónico de la rueda en centímetros: se lee de la misma fuente que
     * la producción (`WizardHeightScale.labels`), no de un literal.
     */
    private fun cmLabel(cm: Int): String =
        WizardHeightScale.labels(WizardHeightUnit.CM)[cm - WizardHeightScale.MIN_CM]
}
