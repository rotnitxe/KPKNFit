package com.example.kpkn.screens.home.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
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

/** El héroe compacto de la sesión del día (I10a): qué botones salen en cada modo y que cada uno llama a su acción. */
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
    fun hoy_muestra_editar_como_secundaria_y_la_abre() {
        var edited = 0
        setHero(HomeSamples.heroToday(), HomeHeroActions(onEdit = { edited++ }))

        rule.onNodeWithTag("home-hero-edit").performClick()
        assertEquals(1, edited)
    }

    @Test
    fun en_curso_muestra_continuar_sesion_y_la_reanuda() {
        var resumed = 0
        setHero(HomeSamples.heroOngoing(), HomeHeroActions(onResume = { resumed++ }))

        rule.onNodeWithText("Continuar sesión").performClick()
        assertEquals(1, resumed)
        rule.onNodeWithTag("home-hero-edit").assertDoesNotExist()
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
    fun descanso_muestra_el_siguiente_y_ver_programa() {
        var opened = 0
        setHero(HomeSamples.heroRest(), HomeHeroActions(onOpenProgram = { opened++ }))

        rule.onNodeWithText("Siguiente: jueves 15 · Torso B").assertExists()
        rule.onNodeWithText("Ver programa").performClick()
        assertEquals(1, opened)
        rule.onNodeWithTag("home-hero-start").assertDoesNotExist()
        rule.onNodeWithTag("home-hero-edit").assertDoesNotExist()
    }

    @Test
    fun sin_programa_muestra_elige_programa_y_crear_o_importar() {
        var chosen = 0
        var created = 0
        setHero(
            HomeSamples.heroNoProgram(),
            HomeHeroActions(onChooseProgram = { chosen++ }, onCreateProgram = { created++ }),
        )

        rule.onNodeWithText("Elige tu programa").performClick()
        rule.onNodeWithText("Crear o importar").performClick()
        assertEquals(1, chosen)
        assertEquals(1, created)
    }
}
