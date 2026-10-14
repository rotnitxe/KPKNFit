package com.example.kpkn.ui.components.permissions

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El diálogo «Activa tus avisos»: sus dos acciones llaman a sus lambdas y la línea del micrófono sale solo cuando el
 * micrófono falta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RecommendedPermissionsDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private val notificaciones = "Notificaciones: recordatorios y avisos del descanso"
    private val microfono = "Micrófono (comandos por voz)"
    private val lineaMicrofono = "El micrófono activa los comandos por voz."

    private fun lanzar(
        missing: List<String>,
        onOpenSettings: () -> Unit = {},
        onSkip: () -> Unit = {},
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                RecommendedPermissionsDialog(
                    missing = missing,
                    onOpenSettings = onOpenSettings,
                    onSkip = onSkip,
                )
            }
        }
    }

    @Test
    fun ir_a_ajustes_llama_a_onOpenSettings() {
        var abrir = 0
        var omitir = 0
        lanzar(listOf(notificaciones), onOpenSettings = { abrir++ }, onSkip = { omitir++ })

        rule.onNodeWithTag("permissions-dialog").assertIsDisplayed()
        rule.onNodeWithTag("permissions-open-settings").performClick()

        assertEquals(1, abrir)
        assertEquals(0, omitir)
    }

    @Test
    fun ahora_no_llama_a_onSkip() {
        var abrir = 0
        var omitir = 0
        lanzar(listOf(notificaciones), onOpenSettings = { abrir++ }, onSkip = { omitir++ })

        rule.onNodeWithTag("permissions-skip").performClick()

        assertEquals(0, abrir)
        assertEquals(1, omitir)
    }

    @Test
    fun con_microfono_aparece_su_linea() {
        lanzar(listOf(notificaciones, microfono))

        rule.onNodeWithText(lineaMicrofono).assertExists()
    }

    @Test
    fun sin_microfono_no_aparece_su_linea() {
        lanzar(listOf(notificaciones))

        rule.onNodeWithText(lineaMicrofono).assertDoesNotExist()
    }
}
