package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.exercises.ExerciseTechniqueImageVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LV5, bug 2: la foto de la ficha no elige un implemento por su cuenta. Al abrir, la página con la que arranca no se avisa como elección;
 * solo lo que la persona desliza llega a quien la usa.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1000dp-xxhdpi", application = android.app.Application::class)
class CatalogCarouselSettleTest {

    @get:Rule
    val rule = createComposeRule()

    private val variants: List<ExerciseTechniqueImageVariant> =
        ExerciseTechniqueImageLookup.variants("hammer_curl").take(3)

    private val options = variants.map { variant ->
        CatalogDetailOption(value = variant.implementation, label = variant.label, selected = false, enabled = true)
    }

    @Test
    fun abrirLaFichaSinImplementoElegidoNoEligeNingunoSola() {
        val avisos = mutableListOf<String>()
        rule.setContent {
            CatalogExerciseImageCarousel(
                variants = variants,
                selectedImplementation = null,
                onImplementationSettled = { avisos += it },
                implementOptions = options,
                onSelectImplementation = {},
            )
        }
        rule.waitForIdle()

        assertTrue("abrir la ficha no es una elección: $avisos", avisos.isEmpty())
    }

    @Test
    fun abrirLaFichaConUnImplementoElegidoTampocoAvisa() {
        val avisos = mutableListOf<String>()
        val elegido = variants[1].implementation
        rule.setContent {
            CatalogExerciseImageCarousel(
                variants = variants,
                selectedImplementation = elegido,
                onImplementationSettled = { avisos += it },
                implementOptions = options,
                onSelectImplementation = {},
            )
        }
        rule.waitForIdle()

        assertTrue("la foto arranca en la página elegida y no avisa: $avisos", avisos.isEmpty())
    }

    @Test
    fun deslizarLaFotoSiAvisaDelImplementoDeLaNuevaPagina() {
        val avisos = mutableListOf<String>()
        rule.setContent {
            CatalogExerciseImageCarousel(
                variants = variants,
                selectedImplementation = variants[0].implementation,
                onImplementationSettled = { avisos += it },
                implementOptions = options,
                onSelectImplementation = {},
            )
        }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Ilustración del ejercicio con ${variants[0].label}")
            .performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(listOf(variants[1].implementation), avisos)
    }
}
