package com.example.kpkn.screens.programdetail.design

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La píldora «Siguiente» de la cabecera en un día sin sesión: TalkBack la describe completa y abre su día al tocarla. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramHeaderNextPillComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the next session pill is described for TalkBack and opens its day when pressed`() {
        var opened = false
        val state = ProgramHeaderState(
            name = "Base sólida",
            status = ProgramHeaderStatus.ACTIVE,
            meta = "Rutina",
            identity = coverIdentityFor("base-solida", null),
            photoUrl = null,
            bundled = null,
            progressLabel = null,
            primary = ProgramHeaderPrimary.None,
            nextSession = ProgramNextSession(
                dayOfWeek = 4,
                label = "Siguiente: jueves · Pierna A",
                description = "Siguiente sesión: jueves, Pierna A",
            ),
        )
        composeRule.setContent {
            ProgramHeader(
                state = state,
                actions = ProgramHeaderActions(
                    onPrimary = {},
                    onOpenNextSession = { opened = true },
                ),
            )
        }
        composeRule.onNodeWithContentDescription("Siguiente sesión: jueves, Pierna A").performClick()
        assertTrue(opened)
    }
}
