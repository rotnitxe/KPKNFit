package com.example.kpkn.screens.home.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.home.HomeSamples
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El héroe de la sesión del día (I2): qué botones salen en cada modo, y que un músculo sin dato nunca sale como 100 %. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class HomeSessionHeroTest {

    @get:Rule
    val rule = createComposeRule()

    /** Pinta el héroe con las animaciones apagadas: el cuadro final, sin bucles. */
    private fun setHero(state: HomeHeroState, actions: HomeHeroActions = HomeHeroActions()) {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                HomeSessionHero(state = state, actions = actions)
            }
        }
    }

    @Test
    fun hoy_muestra_empezar_sesion_y_llama_a_onStart() {
        var started = 0
        setHero(HomeSamples.heroToday(), HomeHeroActions(onStart = { started++ }))

        rule.onNodeWithTag("home-hero").assertExists()
        rule.onNodeWithText("Empezar sesión").performClick()
        assertEquals(1, started)
    }

    @Test
    fun hecha_muestra_repetir_sesion_y_no_empezar() {
        setHero(HomeSamples.heroDone())

        rule.onNodeWithText("Repetir sesión").assertExists()
        rule.onNodeWithTag("home-hero-start").assertDoesNotExist()
    }

    @Test
    fun otro_dia_muestra_ver_en_el_programa_y_lo_abre() {
        var opened = 0
        setHero(HomeSamples.heroOtherDay(), HomeHeroActions(onOpenProgram = { opened++ }))

        rule.onNodeWithText("Ver en el programa").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun descanso_sin_botones_y_con_el_siguiente() {
        setHero(HomeSamples.heroRest())

        rule.onNodeWithText("Siguiente: jueves · Torso B").assertExists()
        rule.onNodeWithTag("home-hero-start").assertDoesNotExist()
        rule.onNodeWithTag("home-hero-edit").assertDoesNotExist()
        rule.onNodeWithTag("home-hero-secondary").assertDoesNotExist()
    }

    @Test
    fun sin_programa_muestra_crear_programa_y_lo_abre() {
        var created = 0
        setHero(HomeSamples.heroNoProgram(), HomeHeroActions(onCreateProgram = { created++ }))

        rule.onNodeWithText("Crear programa").performClick()
        assertEquals(1, created)
    }

    @Test
    fun musculo_sin_dato_sale_con_guion_y_nunca_con_100() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                HomeMuscleGauges(
                    muscles = listOf(HomeMuscleGauge("Pantorrilla", null), HomeMuscleGauge("Isquios", 78)),
                    onOpen = {},
                )
            }
        }

        rule.onNodeWithText("—", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("78%", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("100%", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Músculos de esta sesión: Isquios 78 %, Pantorrilla sin dato").assertExists()
    }
}
