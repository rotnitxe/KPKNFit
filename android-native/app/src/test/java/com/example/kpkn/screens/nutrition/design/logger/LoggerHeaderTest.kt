package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cabecera, pestañas y «sin resultados» del registro: sus textos y sus acciones. No mide el dibujo (eso lo hace la hoja de render).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class LoggerHeaderTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun laCabeceraMuestraTituloFechaYComidas() {
        rule.setContent {
            LoggerHeader(
                title = "Editar comida",
                dateLabel = "lun 13 oct",
                mealType = MealType.SNACK,
                onMealSelect = {},
                onSettings = {},
                onClose = {},
            )
        }
        rule.onNodeWithText("Editar comida").assertIsDisplayed()
        rule.onNodeWithText("lun 13 oct").assertIsDisplayed()
        rule.onNodeWithText("Desayuno").assertIsDisplayed()
        rule.onNodeWithText("Merienda").assertIsDisplayed()
        rule.onNodeWithText("Cena").assertIsDisplayed()
    }

    @Test
    fun elegirComidaLlamaAlLambdaConLaComidaElegida() {
        var elegida: MealType? = null
        rule.setContent {
            LoggerHeader(
                title = "Registrar comida",
                dateLabel = "Hoy · lun 13 oct",
                mealType = MealType.LUNCH,
                onMealSelect = { elegida = it },
                onSettings = {},
                onClose = {},
            )
        }
        rule.onNodeWithText("Cena").performClick()
        assertEquals(MealType.DINNER, elegida)
    }

    @Test
    fun cerrarYConfiguracionLlamanASusLambdas() {
        var cerrado = false
        var configurado = false
        rule.setContent {
            LoggerHeader(
                title = "Registrar comida",
                dateLabel = "Hoy · lun 13 oct",
                mealType = MealType.LUNCH,
                onMealSelect = {},
                onSettings = { configurado = true },
                onClose = { cerrado = true },
            )
        }
        rule.onNodeWithContentDescription("Ajustes del registro").performClick()
        rule.onNodeWithContentDescription("Cerrar").performClick()
        assertTrue(configurado)
        assertTrue(cerrado)
    }

    @Test
    fun elegirPestanaLlamaAlLambdaConSuIndice() {
        var pestana = -1
        rule.setContent { LoggerTabs(activeTab = 0, onTabSelect = { pestana = it }) }
        rule.onNodeWithText("Buscar").performClick()
        assertEquals(1, pestana)
    }

    @Test
    fun sinResultadosMuestraElMensajeYDescribirloCambiaALaPestanaDescribir() {
        rule.setContent {
            var pestana by remember { mutableIntStateOf(1) }
            var texto by remember { mutableStateOf("") }
            Column {
                LoggerTabs(activeTab = pestana, onTabSelect = { pestana = it })
                if (pestana == 1) {
                    LoggerSearchEmpty(
                        query = "pan",
                        onDescribe = {
                            texto = "pan"
                            pestana = 0
                        },
                        onCreate = {},
                    )
                } else {
                    Text(text = "descripcion:$texto")
                }
            }
        }
        rule.onNodeWithText("No encontré «pan»").assertIsDisplayed()
        rule.onNodeWithText("Describirla").performClick()
        rule.onNodeWithText("descripcion:pan").assertIsDisplayed()
    }

    @Test
    fun sinResultadosLlamaAlLambdaDeDescribir() {
        var descrito = false
        rule.setContent {
            LoggerSearchEmpty(query = "xyz", onDescribe = { descrito = true }, onCreate = {})
        }
        rule.onNodeWithText("Describirla").performClick()
        assertTrue(descrito)
    }

    @Test
    fun sinResultadosOfreceCrearElAlimento() {
        var creado = false
        rule.setContent {
            LoggerSearchEmpty(query = "arepa de choclo", onDescribe = {}, onCreate = { creado = true })
        }
        rule.onNodeWithText("Descríbela y la estimamos, o créala con sus datos.").assertIsDisplayed()
        rule.onNodeWithText("Crear alimento").performClick()
        assertTrue(creado)
    }
}
