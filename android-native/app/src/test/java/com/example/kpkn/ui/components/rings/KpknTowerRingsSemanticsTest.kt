package com.example.kpkn.ui.components.rings

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lo que oye TalkBack y lo que responde al toque: la torre en Compose, con Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
class KpknTowerRingsSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `la descripcion accesible es la de la torre completa`() {
        rule.setContent {
            KpknTowerRings(values = TowerRingValues(72, 64, 80), modifier = Modifier.size(220.dp))
        }
        rule.onNodeWithContentDescription("Columna 72 %, Músculos 64 %, Energía 80 %").assertExists()
    }

    @Test
    fun `un valor que falta se anuncia como sin calibrar`() {
        rule.setContent {
            KpknTowerRings(values = TowerRingValues(null, 45, 20), modifier = Modifier.size(220.dp))
        }
        rule.onNodeWithContentDescription("Columna sin calibrar, Músculos 45 %, Energía 20 %").assertExists()
    }

    @Test
    fun `tocar la banda de cada anillo devuelve ese anillo`() {
        val tocados = mutableListOf<RingChannel>()
        rule.setContent {
            KpknTowerRings(
                values = TowerRingValues(72, 64, 80),
                modifier = Modifier.size(220.dp),
                onRingTap = { tocados += it },
            )
        }
        rule.onNodeWithContentDescription("Columna 72 %").performClick()
        rule.onNodeWithContentDescription("Energía 80 %").performClick()
        assertEquals(listOf(RingChannel.COLUMNA, RingChannel.ENERGIA), tocados)
    }
}
