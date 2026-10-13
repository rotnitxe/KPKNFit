package com.example.kpkn.screens.nutrition.design.editor

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.NutritionEditorBase
import com.example.kpkn.domain.nutrition.NutritionEditorProvenance
import com.example.kpkn.domain.nutrition.NutritionPlanEditorDraft
import com.example.kpkn.screens.nutrition.NutritionPlanEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Comportamiento de la raíz del editor de Nutrición, sin estado: cada control llama a su acción, «Guardar» se enciende solo con
 * cambios, el cambio de kcal se ve y la procedencia profesional apaga la dirección. Las acciones son lambdas que anotan las llamadas;
 * el ViewModel no interviene.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp", application = Application::class)
class NutritionPlanEditorDesignTest {

    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 13)
    private val base = NutritionEditorBase(caloriesKcal = 2150, proteinG = 160, carbsG = 210, fatG = 70)
    private val originalBase = NutritionEditorBase(caloriesKcal = 2300, proteinG = 170, carbsG = 230, fatG = 70)

    private fun uiState(
        // Un plan existente (con planId): así el cambio se lee como «antes → después», no como «plan nuevo».
        draft: NutritionPlanEditorDraft = NutritionPlanEditorDraft(planId = "plan-1", direction = PlanDirection.DEFICIT),
        base: NutritionEditorBase? = this.base,
        originalBase: NutritionEditorBase? = null,
        dirty: Boolean = false,
        saving: Boolean = false,
        loading: Boolean = false,
        errors: Map<String, String> = emptyMap(),
    ) = NutritionPlanEditorUiState(
        isLoading = loading,
        draft = draft,
        base = base,
        originalBase = originalBase,
        isDirty = dirty,
        isSaving = saving,
        errors = errors,
    )

    @Composable
    private fun Editor(state: NutritionPlanEditorUiState, actions: NutritionPlanEditorActions) {
        NutritionPlanEditorContent(
            state = state,
            actions = actions,
            isNew = state.draft.planId == null,
            goalOpen = false,
            selectedDay = null,
            today = today,
        )
    }

    @Test
    fun elegirVolumenLlamaASetDirectionSurplus() {
        val chosen = mutableListOf<PlanDirection>()
        rule.setContent {
            Editor(uiState(), NutritionPlanEditorActions(onDirection = { chosen += it }))
        }
        rule.onNodeWithText("Volumen").performClick()
        assertEquals(listOf(PlanDirection.SURPLUS), chosen)
    }

    @Test
    fun conProcedenciaProfesionalLaDireccionEstaApagada() {
        val chosen = mutableListOf<PlanDirection>()
        val professional = NutritionPlanEditorDraft(
            direction = PlanDirection.PROFESSIONAL,
            provenance = NutritionEditorProvenance.PROFESSIONAL,
        )
        rule.setContent {
            Editor(uiState(draft = professional), NutritionPlanEditorActions(onDirection = { chosen += it }))
        }
        rule.onNodeWithText("La define tu profesional").assertIsDisplayed()
        rule.onNodeWithText("Volumen").assertIsNotEnabled()
        rule.onNodeWithText("Volumen").performClick()
        assertTrue("con procedencia profesional la dirección no responde", chosen.isEmpty())
    }

    @Test
    fun guardarApagadoSinCambios() {
        rule.setContent {
            Editor(uiState(dirty = false), NutritionPlanEditorActions())
        }
        rule.onNodeWithText("Guardar").assertIsNotEnabled()
    }

    @Test
    fun guardarEncendidoConCambiosYElCambioSeVe() {
        var saved = false
        rule.setContent {
            Editor(
                uiState(originalBase = originalBase, dirty = true),
                NutritionPlanEditorActions(onSave = { saved = true }),
            )
        }
        rule.onNodeWithText("2.300 → 2.150 kcal").assertIsDisplayed()
        rule.onNodeWithText("Guardar").assertIsEnabled().performClick()
        assertTrue("«Guardar» llama a save", saved)
    }

    @Test
    fun guardandoLaPildoraDiceGuardandoYNoResponde() {
        rule.setContent {
            Editor(uiState(originalBase = originalBase, dirty = true, saving = true), NutritionPlanEditorActions())
        }
        rule.onNodeWithText("Guardando…").assertIsNotEnabled()
    }

    @Test
    fun unInterruptorDeCondicionLlamaASuSetter() {
        val pregnant = mutableListOf<Boolean>()
        rule.setContent {
            Editor(uiState(), NutritionPlanEditorActions(onPregnant = { pregnant += it }))
        }
        rule.onNodeWithText("Embarazo").performClick()
        assertEquals(listOf(true), pregnant)
    }

    @Test
    fun sinMetaSeOfreceFijarUnaMetaYLlamaAlAbrirla() {
        var opened = false
        rule.setContent {
            Editor(uiState(), NutritionPlanEditorActions(onOpenGoal = { opened = true }))
        }
        rule.onNodeWithText("Fijar una meta").performClick()
        assertTrue("«Fijar una meta» abre el control", opened)
    }

    @Test
    fun conErrorDeGuardadoSeVeElAvisoEnLenguajeLlano() {
        val message = "No se pudo guardar el plan. Inténtalo de nuevo."
        rule.setContent {
            Editor(uiState(dirty = true, errors = mapOf("commit" to message)), NutritionPlanEditorActions())
        }
        rule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun cargandoNoMuestraLosModulosYGuardarNoResponde() {
        rule.setContent {
            Editor(uiState(loading = true, base = null), NutritionPlanEditorActions())
        }
        rule.onNodeWithText("Dirección").assertDoesNotExist()
        rule.onNodeWithText("Guardar").assertIsNotEnabled()
    }
}
