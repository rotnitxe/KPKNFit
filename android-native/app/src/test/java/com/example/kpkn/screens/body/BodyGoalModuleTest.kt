package com.example.kpkn.screens.body

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.domain.body.ResolvedBodyGoal
import com.example.kpkn.screens.body.design.BodyGoalDraft
import com.example.kpkn.screens.body.design.BodyGoalModule
import com.example.kpkn.screens.body.design.BodyGoalRemoveDialog
import com.example.kpkn.screens.body.design.BodyGoalSheetContent
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class BodyGoalModuleTest {

    @get:Rule
    val rule = createComposeRule()

    private fun montar(
        goal: ResolvedBodyGoal? = bodySampleResolvedGoal(),
        conPlan: Boolean,
        onEditarEnPlan: () -> Unit = {},
        onEditarPropio: () -> Unit = {},
    ) {
        rule.setContent {
            BodyGoalModule(
                goal = goal,
                weightUnit = WizardMassUnit.KG,
                activePlan = conPlan,
                onEditInPlan = onEditarEnPlan,
                onEditOwn = onEditarPropio,
            )
        }
    }

    @Test
    fun conPlanActivoEditarVaAlEditorDelPlanYSeDice() {
        var enPlan = false
        var propio = false
        montar(conPlan = true, onEditarEnPlan = { enPlan = true }, onEditarPropio = { propio = true })
        rule.onNodeWithText("Tu objetivo vive en tu plan de nutrición").assertIsDisplayed()
        rule.onNodeWithText("Editar").performClick()
        assertTrue(enPlan)
        assertFalse(propio)
    }

    @Test
    fun sinPlanEditarAbreLaHojaDeObjetivoPropio() {
        var enPlan = false
        var propio = false
        montar(conPlan = false, onEditarEnPlan = { enPlan = true }, onEditarPropio = { propio = true })
        rule.onNodeWithText("Tu objetivo vive en tu plan de nutrición").assertDoesNotExist()
        rule.onNodeWithText("Editar").performClick()
        assertTrue(propio)
        assertFalse(enPlan)
    }

    @Test
    fun laMetaYLaFechaDeInicioSeVen() {
        montar(conPlan = false)
        rule.onNodeWithText("Meta de peso").assertIsDisplayed()
        rule.onNodeWithText("Desde el 14 de agosto").assertIsDisplayed()
    }

    @Test
    fun sinObjetivoConPlanFijarAbreElEditorDelPlan() {
        var enPlan = false
        montar(goal = null, conPlan = true, onEditarEnPlan = { enPlan = true })
        rule.onNodeWithText("Aún no tienes objetivo").assertIsDisplayed()
        rule.onNodeWithText("Fijar objetivo").performClick()
        assertTrue(enPlan)
    }

    @Test
    fun sinObjetivoYSinPlanFijarAbreLaHoja() {
        var propio = false
        montar(goal = null, conPlan = false, onEditarPropio = { propio = true })
        rule.onNodeWithText("Aún no tienes objetivo").assertIsDisplayed()
        rule.onNodeWithText("Fijar objetivo").performClick()
        assertTrue(propio)
    }

    @Test
    fun laHojaSoloGuardaSiHayMetaFijadaYEmiteUnObjetivoManualSinPlan() {
        var guardado: BodyGoal? = null
        rule.setContent {
            BodyGoalSheetContent(
                draft = BodyGoalDraft(metric = GoalMetric.WEIGHT, target = 60.0),
                weightUnit = WizardMassUnit.KG,
                canRemove = false,
                onDraftChange = {},
                onSave = { guardado = it },
                onRemove = {},
                onDismiss = {},
            )
        }
        rule.onNodeWithText("Guardar").performClick()
        val objetivo = guardado!!
        assertEquals(CalculationOrigin.MANUAL, objetivo.origin)
        assertNull(objetivo.linkedPlanId)
        assertEquals(BodyMetric.WEIGHT, objetivo.metric)
        assertEquals(60.0, objetivo.targetValueSi, 1e-9)
        assertEquals("kg", objetivo.unitSi)
        assertTrue(objetivo.createdAtEpochMs > 0L)
        assertEquals(objetivo.createdAtEpochMs, objetivo.updatedAtEpochMs)
    }

    @Test
    fun sinMetaFijadaGuardarNoEmiteNada() {
        var guardado: BodyGoal? = null
        rule.setContent {
            BodyGoalSheetContent(
                draft = BodyGoalDraft(metric = GoalMetric.BODY_FAT),
                weightUnit = WizardMassUnit.KG,
                canRemove = false,
                onDraftChange = {},
                onSave = { guardado = it },
                onRemove = {},
                onDismiss = {},
            )
        }
        rule.onNodeWithText("Guardar").performClick()
        assertNull(guardado)
        rule.onNodeWithText("Quitar objetivo").assertDoesNotExist()
    }

    @Test
    fun quitarObjetivoSoloSiHayUnObjetivoPropioQueQuitar() {
        var quitar = false
        rule.setContent {
            BodyGoalSheetContent(
                draft = bodySampleGoalDraft(),
                weightUnit = WizardMassUnit.KG,
                canRemove = true,
                onDraftChange = {},
                onSave = {},
                onRemove = { quitar = true },
                onDismiss = {},
            )
        }
        rule.onNodeWithText("Quitar objetivo").performClick()
        assertTrue(quitar)
    }

    @Test
    fun laConfirmacionDeQuitarTieneSuAccionYSuCancelar() {
        var confirmado = false
        var cancelado = false
        rule.setContent {
            BodyGoalRemoveDialog(onConfirm = { confirmado = true }, onDismiss = { cancelado = true })
        }
        rule.onNodeWithText("¿Quitar el objetivo?").assertIsDisplayed()
        rule.onNodeWithText("Quitar").performClick()
        assertTrue(confirmado)
        assertFalse(cancelado)
    }
}
