package com.example.kpkn.screens.body

import androidx.compose.ui.test.assertIsDisplayed
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.body.design.BodyCompositionModule
import com.example.kpkn.screens.body.design.BodyInfoTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class BodyCompositionModuleTest {

    @get:Rule
    val rule = createComposeRule()

    /** Pinta el módulo con los datos de ejemplo; [conAltura] y [conComposicion] quitan esas partes, como en la app. */
    private fun montar(
        conPesaje: Boolean = true,
        conAltura: Boolean = true,
        conComposicion: Boolean = true,
        onAltura: () -> Unit = {},
        onInfo: (BodyInfoTopic) -> Unit = {},
    ) {
        rule.setContent {
            BodyCompositionModule(
                weightKg = if (conPesaje) 64.5 else null,
                weightUnit = WizardMassUnit.KG,
                leanKg = if (conComposicion) bodySampleLeanKg() else null,
                fatKg = if (conComposicion) bodySampleFatKg() else null,
                bodyFatPercent = if (conComposicion) BODY_SAMPLE_BODY_FAT else null,
                musclePercent = if (conComposicion) BODY_SAMPLE_MUSCLE else null,
                ffmi = if (conAltura && conComposicion) bodySampleFfmi() else null,
                bmi = if (conAltura) bodySampleBmi() else null,
                hasHeight = conAltura,
                onAddHeight = onAltura,
                onInfo = onInfo,
            )
        }
    }

    @Test
    fun sinAlturaSeVeElAvisoYAnadirAlturaLlevaAlPerfil() {
        var navegado = false
        montar(conAltura = false, onAltura = { navegado = true })
        rule.onNodeWithText("Añade tu altura para ver tu IMC y FFMI").assertIsDisplayed()
        rule.onNodeWithText("Añadir altura").performClick()
        assertTrue(navegado)
    }

    @Test
    fun sinAlturaNoHayNiImcNiFfmi() {
        montar(conAltura = false)
        rule.onNodeWithContentDescription("Cómo se calcula el IMC").assertDoesNotExist()
        rule.onNodeWithContentDescription("Cómo se calcula el FFMI").assertDoesNotExist()
    }

    @Test
    fun conAlturaSeVenFfmiEImcConSuCategoriaYUnidad() {
        montar()
        // FFMI de ejemplo 19,1 (promedio), estimado a partir del % de grasa: lleva «≈».
        rule.onNodeWithContentDescription("≈ 19,1 kg/m²").assertIsDisplayed()
        rule.onNodeWithText("Promedio").assertIsDisplayed()
        // IMC de ejemplo 22,3 (normal), sobre el valor redondeado.
        rule.onNodeWithContentDescription("22,3 kg/m²").assertIsDisplayed()
        rule.onNodeWithText("Normal").assertIsDisplayed()
    }

    @Test
    fun laMasaMagraYLaGrasaLlevanSuAproximacion() {
        montar()
        // Cada tramo tiene sus kilos y su porcentaje en líneas separadas, para que nada se parta al crecer la letra.
        rule.onNodeWithText("≈ 53,4 kg").assertIsDisplayed()
        rule.onNodeWithText("82,8 %").assertIsDisplayed()
        rule.onNodeWithText("≈ 11,1 kg").assertIsDisplayed()
        rule.onNodeWithText("17,2 %").assertIsDisplayed()
    }

    @Test
    fun sinComposicionDeLaMismaMedicionNoHayFfmiYSeDicePorQue() {
        montar(conComposicion = false)
        rule.onNodeWithText("Registra peso y % de grasa en la misma medición para ver tu masa magra y tu FFMI.").assertIsDisplayed()
        rule.onNodeWithContentDescription("≈ 19,1 kg/m²").assertDoesNotExist()
        // El IMC sale del último pesaje y de la altura: no depende de la composición.
        rule.onNodeWithContentDescription("22,3 kg/m²").assertIsDisplayed()
    }

    @Test
    fun sinPesajeNoSePintaNada() {
        // La cabecera pinta la etiqueta en mayúsculas: «COMPOSICIÓN» es el módulo.
        montar(conPesaje = false)
        rule.onNodeWithText("COMPOSICIÓN").assertDoesNotExist()
        rule.onNodeWithText("Añadir altura").assertDoesNotExist()
    }

    @Test
    fun conPesajeElModuloTieneSuCabecera() {
        montar()
        rule.onNodeWithText("COMPOSICIÓN").assertIsDisplayed()
    }

    @Test
    fun cadaCifraAbreSuExplicacion() {
        val abiertas = mutableListOf<BodyInfoTopic>()
        montar(onInfo = { abiertas += it })
        rule.onNodeWithContentDescription("Cómo se calcula el FFMI").performClick()
        rule.onNodeWithContentDescription("Cómo se calcula el IMC").performClick()
        rule.onNodeWithContentDescription("Cómo se calcula la composición").performClick()
        assertEquals(listOf(BodyInfoTopic.FFMI, BodyInfoTopic.IMC, BodyInfoTopic.COMPOSICION), abiertas)
    }
}
