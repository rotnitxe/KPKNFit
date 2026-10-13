package com.example.kpkn.screens.body

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.screens.body.design.bodyHistoryItems
import com.example.kpkn.screens.body.design.bodyHistoryMonths
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class BodyHistorySectionTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun elHistorialAgrupaPorMesDelMasRecienteAlMasAntiguo() {
        val months = bodyHistoryMonths(bodySampleHistoryRows())
        assertEquals(
            listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9), YearMonth.of(2026, 8)),
            months.map { it.month },
        )
        // Dentro de octubre, el 13 antes que el 6.
        assertEquals(listOf(13, 6), months.first().rows.map { it.dateTime.dayOfMonth })
    }

    @Test
    fun cadaMesTieneSuEtiquetaYLaFilaDeHealthConnectSeMarca() {
        rule.setContent {
            LazyColumn { bodyHistoryItems(rows = bodySampleHistoryRows(), onOpen = {}, onAdd = {}) }
        }
        rule.onNodeWithText("OCTUBRE DE 2026").assertIsDisplayed()
        rule.onNodeWithText("SEPTIEMBRE DE 2026").assertIsDisplayed()
        rule.onNodeWithText("Health Connect").assertIsDisplayed()
    }

    @Test
    fun tocarUnaFilaDevuelveLaMedicionDeEsaFila() {
        var tocada: BodyHistoryRow? = null
        rule.setContent {
            LazyColumn { bodyHistoryItems(rows = bodySampleHistoryRows(), onOpen = { tocada = it }, onAdd = {}) }
        }
        rule.onNodeWithText("07:40", substring = true).performClick()
        assertEquals(LocalDateTime.of(2026, 10, 13, 7, 40), tocada!!.dateTime)
    }

    @Test
    fun sinMedicionesSeVeElVacioConSuAccion() {
        var anadido = false
        rule.setContent {
            LazyColumn { bodyHistoryItems(rows = emptyList(), onOpen = {}, onAdd = { anadido = true }) }
        }
        rule.onNodeWithText("Aún no hay medidas").assertIsDisplayed()
        rule.onNodeWithText("Registrar medidas").performClick()
        assertTrue(anadido)
    }
}
