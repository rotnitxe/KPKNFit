package com.example.kpkn.screens.onboarding

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.kpkn.screens.onboarding.design.WEIGHIN_ADD_TAG
import com.example.kpkn.screens.onboarding.design.WEIGHIN_CANCEL_TAG
import com.example.kpkn.screens.onboarding.design.WEIGHIN_EDITOR_TAG
import com.example.kpkn.screens.onboarding.design.WEIGHIN_FAT_RULER_TAG
import com.example.kpkn.screens.onboarding.design.WEIGHIN_FAT_TOGGLE_TAG
import com.example.kpkn.screens.onboarding.design.WEIGHIN_SAVE_TAG
import com.example.kpkn.screens.onboarding.design.WizardDock
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeighInsBody
import com.example.kpkn.screens.onboarding.design.weighInDayTag
import com.example.kpkn.screens.onboarding.design.weighInDeleteTag
import com.example.kpkn.screens.onboarding.design.weighInRowTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Paso de pesajes en la página: añadir con fecha, peso y grasa; los días usados salen apagados; tocar una fila la edita y
 * su «×» la borra; el editor usa la unidad del alta; y «Omitir» del dock solo se pinta cuando el anfitrión lo pasa.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class NutritionWeighInsComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val today: LocalDate = LocalDate.of(2026, 10, 12)
    private val yesterday: LocalDate = today.minusDays(1)

    @Test
    fun `añadir un pesaje con fecha peso y grasa lo entrega al guardar`() {
        val saved = mutableListOf<SetupWeighIn>()
        rule.setContent {
            WizardWeighInsBody(
                rows = emptyList(),
                unit = WizardMassUnit.KG,
                currentKg = 80.0,
                today = today,
                onSave = { saved += it },
                onDelete = {},
            )
        }
        rule.onNodeWithTag(WEIGHIN_ADD_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(WEIGHIN_EDITOR_TAG).assertExists()
        // Sin pesaje todavía: la regla sola no deja guardar; hace falta moverla o tocarla.
        rule.onNodeWithTag(WEIGHIN_SAVE_TAG).assertIsNotEnabled()
        rule.onNodeWithContentDescription("Regla de peso en kilogramos").performTouchInput {
            swipe(Offset(centerX - 200f, bottom - 30f), Offset(centerX + 200f, bottom - 30f))
        }
        rule.onNodeWithTag(WEIGHIN_FAT_TOGGLE_TAG).performClick()
        rule.onNodeWithTag(WEIGHIN_FAT_RULER_TAG).performTouchInput { click() }
        rule.onNodeWithTag(WEIGHIN_SAVE_TAG).performClick()
        rule.waitForIdle()
        val row = saved.single()
        assertEquals(today.toString(), row.dateIso)
        assertTrue(row.weightKg in 20.0..500.0)
        assertNotNull(row.bodyFatPercent)
        assertNull(row.muscleMassPercent)
    }

    @Test
    fun `un dia con pesaje sale apagado y no se puede elegir`() {
        val rows = listOf(SetupWeighIn(id = "ayer", dateIso = yesterday.toString(), weightKg = 79.0))
        rule.setContent {
            WizardWeighInsBody(rows = rows, unit = WizardMassUnit.KG, currentKg = 80.0, today = today, onSave = {}, onDelete = {})
        }
        rule.onNodeWithTag(WEIGHIN_ADD_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(weighInDayTag(yesterday)).assertIsNotEnabled()
        rule.onNodeWithTag(weighInDayTag(today)).assertIsEnabled()
    }

    @Test
    fun `tocar una fila la edita y su boton de eliminar la borra`() {
        val row = SetupWeighIn(id = "ayer", dateIso = yesterday.toString(), weightKg = 79.0, bodyFatPercent = 18.0)
        val saved = mutableListOf<SetupWeighIn>()
        val deleted = mutableListOf<String>()
        rule.setContent {
            WizardWeighInsBody(
                rows = listOf(row),
                unit = WizardMassUnit.KG,
                currentKg = 80.0,
                today = today,
                onSave = { saved += it },
                onDelete = { deleted += it },
            )
        }
        rule.onNodeWithTag(weighInRowTag(yesterday.toString())).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(WEIGHIN_EDITOR_TAG).assertExists()
        rule.onNodeWithTag(WEIGHIN_CANCEL_TAG).assertExists()
        rule.onNodeWithTag(WEIGHIN_SAVE_TAG).performClick()
        assertEquals(listOf("ayer"), saved.map { it.id })
        assertEquals(18.0, saved.single().bodyFatPercent ?: -1.0, 1e-9)
        rule.onNodeWithTag(weighInDeleteTag(yesterday.toString())).performClick()
        assertEquals(listOf("ayer"), deleted)
    }

    @Test
    fun `el editor usa la unidad del alta`() {
        rule.setContent {
            WizardWeighInsBody(
                rows = emptyList(),
                unit = WizardMassUnit.LB,
                currentKg = 80.0,
                today = today,
                onSave = {},
                onDelete = {},
            )
        }
        rule.onNodeWithTag(WEIGHIN_ADD_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Regla de peso en libras").assertExists()
        rule.onNodeWithTag("setup-weight-value").assertTextContains(" lb", substring = true)
    }

    @Test
    fun `omitir del dock aparece solo cuando se pasa y llama a su accion`() {
        var skipped = 0
        rule.setContent {
            WizardDock(
                enabled = true,
                label = "Continuar",
                onClick = {},
                secondaryLabel = "Omitir",
                onSecondary = { skipped++ },
            )
        }
        rule.onNodeWithTag("setup-dock-secondary").performClick()
        assertEquals(1, skipped)
    }

    @Test
    fun `sin accion secundaria el dock no muestra omitir`() {
        rule.setContent {
            WizardDock(enabled = true, label = "Continuar", onClick = {})
        }
        rule.onNodeWithTag("setup-dock-secondary").assertDoesNotExist()
    }
}
