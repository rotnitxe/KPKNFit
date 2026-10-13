package com.example.kpkn.screens.sessioneditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.kpkn.screens.sessioneditor.design.SessionEditorHeader
import com.example.kpkn.screens.sessioneditor.design.sessionEditorDataLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Cabecera de la sesión (SE2): edición en sitio del nombre, descripción por defecto, línea de datos y el chip del navegador. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SessionEditorHeaderTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setHeader(
        name: String = "Torso A",
        description: String? = null,
        minutes: Int? = 58,
        onName: (String) -> Unit = {},
        onNavigator: () -> Unit = {},
    ) {
        rule.setContent {
            SessionEditorHeader(
                sessionId = "s1",
                name = name,
                description = description,
                navigatorLabel = "Lun · $name",
                exerciseCount = 6,
                setCount = 22,
                durationMinutes = minutes,
                onNameCommit = onName,
                onDescriptionCommit = {},
                onOpenNavigator = onNavigator,
            )
        }
    }

    @Test
    fun tocarElNombreLoEditaYHechoGuardaElTexto() {
        val committed = mutableListOf<String>()
        setHeader(onName = { committed += it })

        rule.onNodeWithText("Torso A").performClick()
        rule.onNode(hasSetTextAction()).performTextClearance()
        rule.onNode(hasSetTextAction()).performTextInput("Torso B")
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.waitForIdle()

        assertTrue("debe guardar al terminar", committed.isNotEmpty())
        assertEquals("Torso B", committed.last())
    }

    @Test
    fun descripcionVaciaMuestraAnadirUnaDescripcion() {
        setHeader(description = null)
        rule.onNodeWithText("Añadir una descripción").assertIsDisplayed()
    }

    @Test
    fun lineaDeDatosConDuracionLaNombra() {
        setHeader(minutes = 58)
        rule.onNodeWithText("6 ejercicios · 22 series · ≈ 58 min").assertIsDisplayed()
    }

    @Test
    fun lineaDeDatosSinDuracionNoNombraElTiempo() {
        setHeader(minutes = null)
        rule.onNodeWithText("6 ejercicios · 22 series").assertIsDisplayed()
        rule.onNodeWithText("≈", substring = true).assertDoesNotExist()
    }

    @Test
    fun elChipDelNavegadorAbreSuHoja() {
        var opened = 0
        setHeader(onNavigator = { opened++ })
        rule.onNodeWithText("Lun · Torso A").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun lineaDeDatosEnSingularYSinEjercicios() {
        assertEquals("1 ejercicio · 1 serie · ≈ 3 min", sessionEditorDataLine(exercises = 1, sets = 1, minutes = 3))
        assertEquals("Sin ejercicios", sessionEditorDataLine(exercises = 0, sets = 0, minutes = 0))
    }
}
