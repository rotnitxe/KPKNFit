package com.example.kpkn.screens.nutrition.customfood

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El formulario de alimento propio: errores tras pulsar Guardar, aviso de kcal, guardado, edición y borrado en dos pasos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h2400dp-mdpi")
class CustomFoodFormUiTest {
    @get:Rule val rule = createComposeRule()

    private var form by mutableStateOf(CustomFoodForm())
    private var showErrors by mutableStateOf(false)
    private var saving by mutableStateOf(false)
    private var confirmingDelete by mutableStateOf(false)
    private var saveError by mutableStateOf<String?>(null)
    private var saves = 0
    private var closes = 0
    private var deletes = 0

    /** El campo de texto de un `KpknTextField` etiquetado: la marca está en su contenedor y la acción de escribir en el campo de dentro. */
    private fun field(tag: String) = rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    private fun show(editing: FoodItem? = null) {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                val validation = CustomFoodValidator.validate(form, "custom_1", editing)
                CustomFoodFormContent(
                    form = form,
                    validation = validation,
                    showErrors = showErrors,
                    isEditing = editing != null,
                    saving = saving,
                    saveError = saveError,
                    confirmingDelete = confirmingDelete,
                    onChange = { form = it },
                    onSave = {
                        showErrors = true
                        if (validation.isValid) saves++
                    },
                    onAskDelete = { confirmingDelete = true },
                    onConfirmDelete = { deletes++ },
                    onCancelDelete = { confirmingDelete = false },
                    onClose = { closes++ },
                    modifier = Modifier.width(360.dp).height(2200.dp),
                )
            }
        }
    }

    @Test fun `vacio no enseña errores hasta pulsar guardar y entonces los dice campo a campo`() {
        show()
        rule.onNodeWithText("Escribe un nombre.").assertDoesNotExist()
        rule.onNodeWithTag("custom-food-save").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Escribe un nombre.").assertExists()
        rule.onNodeWithText("Indica la cantidad de la porción.").assertExists()
        assertTrue(rule.onAllNodesWithText("Obligatorio. Si no tiene, escribe 0.").fetchSemanticsNodes().size >= 4)
        assertEquals("sin datos válidos no guarda", 0, saves)
    }

    @Test fun `un formulario valido guarda y las kcal que no cuadran avisan sin bloquear`() {
        show()
        field("custom-food-name").performTextInput("Galletas de avena")
        field("custom-food-serving").performTextInput("30")
        field("custom-food-kcal").performTextInput("300")
        field("custom-food-protein").performTextInput("2")
        field("custom-food-carbs").performTextInput("21")
        field("custom-food-fats").performTextInput("4,5")
        rule.onNodeWithTag("custom-food-warning").assertExists()
        rule.onNodeWithTag("custom-food-save").performClick()
        assertEquals(1, saves)
        assertEquals("Galletas de avena", form.name)
        assertEquals("4,5", form.fats)
    }

    @Test fun `un codigo de barras erroneo se marca en su campo`() {
        show()
        field("custom-food-barcode").performTextInput("4006381333932")
        rule.onNodeWithTag("custom-food-save").performClick()
        rule.onNodeWithText("Código no válido: revisa los dígitos (8, 12 o 13).").assertExists()
    }

    @Test fun `guardando el boton se apaga y un error de guardado se ve`() {
        saving = true
        saveError = "No se pudo guardar el alimento. Inténtalo de nuevo."
        show()
        rule.onNodeWithTag("custom-food-save").assertIsNotEnabled()
        rule.onNodeWithText("Guardando…").assertExists()
        rule.onNodeWithTag("custom-food-save-error").assertExists()
    }

    @Test fun `en alta no hay borrar y en edicion borra en dos pasos`() {
        show()
        rule.onNodeWithTag("custom-food-delete").assertDoesNotExist()
    }

    @Test fun `editar muestra los datos guardados y el borrado pide confirmacion`() {
        val food = requireNotNull(
            CustomFoodValidator.validate(
                CustomFoodForm(name = "Galletas de avena", servingAmount = "30", calories = "135", protein = "2", carbs = "21", fats = "4,5"),
                "custom_1",
            ).food,
        )
        form = CustomFoodValidator.fromFood(food)
        show(editing = food)
        rule.onNodeWithText("Editar alimento propio").assertExists()
        rule.onNodeWithTag("custom-food-delete").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("¿Eliminar este alimento?").assertExists()
        rule.onNodeWithTag("custom-food-delete-cancel").performClick()
        rule.onNodeWithText("¿Eliminar este alimento?").assertDoesNotExist()
        rule.onNodeWithTag("custom-food-delete").performClick()
        rule.onNodeWithTag("custom-food-delete-confirm").performClick()
        assertEquals(1, deletes)
        rule.onNodeWithTag("custom-food-close").performClick()
        assertTrue(closes == 1)
    }
}
