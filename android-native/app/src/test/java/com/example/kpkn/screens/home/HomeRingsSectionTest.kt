package com.example.kpkn.screens.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.ringScore
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Tus RINGS» de Home vivo (I1): las tres columnas de la leyenda, abrir el detalle al tocar, la lectura sin datos y la introducción de (i). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class HomeRingsSectionTest {

    @get:Rule
    val rule = createComposeRule()

    /** Pinta la sección con [snapshot]; cada canal cuyo detalle se abre se anota en [opened]. */
    private fun setSection(snapshot: AugeSnapshot, opened: MutableList<RecoveryChannelId> = mutableListOf()) {
        rule.setContent {
            var selected by remember { mutableStateOf<RecoveryChannelId?>(null) }
            HomeRingsSection(
                muscularProgress = snapshot.ringScore(RecoveryChannelId.MUSCULAR) / 100f,
                sncProgress = snapshot.ringScore(RecoveryChannelId.SYSTEM) / 100f,
                columnaProgress = snapshot.ringScore(RecoveryChannelId.STRUCTURE) / 100f,
                dashboard = snapshot.dashboard,
                snapshot = snapshot,
                selectedChannel = selected,
                onSelectedChannelChange = { channel ->
                    selected = channel
                    if (channel != null) opened += channel
                },
            )
        }
    }

    @Test
    fun `las tres columnas van en orden Músculos, Energía y Columna de izquierda a derecha`() {
        setSection(HomeSamples.ringsSample())

        val muscle = rule.onNodeWithTag("home-rings-row-MUSCULAR").fetchSemanticsNode().boundsInRoot.left
        val energy = rule.onNodeWithTag("home-rings-row-SYSTEM").fetchSemanticsNode().boundsInRoot.left
        val column = rule.onNodeWithTag("home-rings-row-STRUCTURE").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Músculos, Energía y Columna de izquierda a derecha", muscle < energy && energy < column)
    }

    @Test
    fun `tocar una columna abre el detalle de su canal tras el salto`() {
        val opened = mutableListOf<RecoveryChannelId>()
        setSection(HomeSamples.ringsSample(), opened)

        rule.onNodeWithTag("home-rings-row-MUSCULAR").performClick()
        rule.mainClock.advanceTimeBy(600)

        assertEquals(listOf(RecoveryChannelId.MUSCULAR), opened)
        rule.onNodeWithTag("home-ring-sheet").assertExists()
        rule.onNodeWithTag("home-ring-close").assertExists()
    }

    @Test
    fun `sin datos se lee Sin calibrar`() {
        setSection(HomeSamples.ringsSample(structure = null))

        rule.onNodeWithTag("home-rings-row-STRUCTURE").assert(hasText("Sin calibrar", substring = true))
    }

    @Test
    fun `con reducir movimiento se abre el detalle de inmediato sin animacion`() {
        val opened = mutableListOf<RecoveryChannelId>()
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                var selected by remember { mutableStateOf<RecoveryChannelId?>(null) }
                val snapshot = HomeSamples.ringsSample()
                HomeRingsSection(
                    muscularProgress = snapshot.ringScore(RecoveryChannelId.MUSCULAR) / 100f,
                    sncProgress = snapshot.ringScore(RecoveryChannelId.SYSTEM) / 100f,
                    columnaProgress = snapshot.ringScore(RecoveryChannelId.STRUCTURE) / 100f,
                    dashboard = snapshot.dashboard,
                    snapshot = snapshot,
                    selectedChannel = selected,
                    onSelectedChannelChange = { channel ->
                        selected = channel
                        if (channel != null) opened += channel
                    },
                )
            }
        }

        rule.onNodeWithTag("home-rings-row-MUSCULAR").performClick()
        assertEquals(listOf(RecoveryChannelId.MUSCULAR), opened)
        rule.onNodeWithTag("home-ring-sheet").assertExists()
    }

    @Test
    fun `el (i) abre la introducción de RINGS en repaso`() {
        setSection(HomeSamples.ringsSample())

        rule.onNodeWithTag("home-rings-info").performClick()

        // Repaso: empieza en el acto de los anillos (sin el umbral del alta) y el botón final dice «Entendido».
        rule.onNodeWithText("TUS TRES ANILLOS").assertExists()
        rule.onNodeWithTag("rings-intro-skip").performClick()
        rule.onNodeWithTag("rings-intro-start").assert(hasText("Entendido"))
    }
}
