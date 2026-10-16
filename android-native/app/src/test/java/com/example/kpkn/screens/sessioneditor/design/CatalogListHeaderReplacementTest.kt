package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.onboarding.design.entreno.plan.ExercisePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LV5, bug 1 (H1): la cabecera del catálogo de reemplazo dice «Reemplazar», lleva la tarjeta fija «Reemplazando» y solo dos filtros
 * («Mismo músculo» y «Todos»); al añadir ejercicios sigue siendo la de siempre.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CatalogListHeaderReplacementTest {

    @get:Rule
    val rule = createComposeRule()

    private val card = CatalogReplacingCardModel(
        name = "Sentadilla trasera con barra alta",
        subtitle = "Sentadilla · Cuádriceps · 4 × 4–6 · RIR 2",
        thumbnailResId = null,
        pattern = ExercisePattern.SQUAT,
        mainMuscleId = "quadriceps",
    )

    private fun header(
        isReplacement: Boolean,
        replacingCard: CatalogReplacingCardModel? = null,
        targetGroupName: String? = null,
        onToggleSameMuscle: (() -> Unit)? = null,
    ): @androidx.compose.runtime.Composable () -> Unit = {
        CatalogListHeader(
            query = "",
            onQueryChange = {},
            onBack = {},
            targetGroupName = targetGroupName,
            filterRegion = null,
            filterMuscle = null,
            muscleFilterExpanded = false,
            muscleIds = emptyList(),
            onClearFilters = {},
            onToggleUpper = {},
            onToggleLower = {},
            onToggleMuscle = {},
            onToggleMuscleId = {},
            isReplacement = isReplacement,
            replacingCard = replacingCard,
            sameMuscleSelected = false,
            onToggleSameMuscle = onToggleSameMuscle,
        )
    }

    @Test
    fun alReemplazarElTituloEsReemplazarYLaTarjetaDelEjercicioActualVaArriba() {
        rule.setContent(header(isReplacement = true, replacingCard = card))

        rule.onNodeWithText("Reemplazar").assertExists()
        rule.onNodeWithText("Añadir ejercicios").assertDoesNotExist()
        rule.onNodeWithTag(CatalogReplacingCardTag).assertExists()
        rule.onNodeWithText("REEMPLAZANDO").assertExists()
        rule.onNodeWithText("Sentadilla trasera con barra alta").assertExists()
        rule.onNodeWithText("Sentadilla · Cuádriceps · 4 × 4–6 · RIR 2").assertExists()
    }

    @Test
    fun alReemplazarLosFiltrosSonMismoMusculoYTodosYNoLosDeRegion() {
        var sameMuscleTaps = 0
        rule.setContent(header(isReplacement = true, replacingCard = card, onToggleSameMuscle = { sameMuscleTaps++ }))

        rule.onNodeWithText("Mismo músculo").assertExists()
        rule.onNodeWithText("Todos").assertExists()
        rule.onNodeWithText("Tren superior").assertDoesNotExist()
        rule.onNodeWithText("Tren inferior").assertDoesNotExist()

        rule.onNodeWithText("Mismo músculo").performClick()
        assertEquals(1, sameMuscleTaps)
    }

    @Test
    fun sinMusculoPrincipalNoSaleMismoMusculo() {
        rule.setContent(header(isReplacement = true, replacingCard = card, onToggleSameMuscle = null))

        rule.onNodeWithText("Mismo músculo").assertDoesNotExist()
        rule.onNodeWithText("Todos").assertExists()
    }

    @Test
    fun alAnadirSigueDiciendoAnadirYLaParteDeLaSesion() {
        rule.setContent(header(isReplacement = false, targetGroupName = "Bloque principal"))

        rule.onNodeWithText("Añadir ejercicios").assertExists()
        rule.onNodeWithText("Reemplazar").assertDoesNotExist()
        rule.onNodeWithTag(CatalogReplacingCardTag).assertDoesNotExist()
        rule.onNodeWithText("Agrega ejercicios para Bloque principal").assertExists()
        rule.onNodeWithText("Tren superior").assertExists()
        rule.onNodeWithText("Mismo músculo").assertDoesNotExist()
    }

    @Test
    fun alReemplazarSinTarjetaSoloCambiaElTituloYNoDiceAgregar() {
        rule.setContent(header(isReplacement = true, replacingCard = null, targetGroupName = "Bloque principal"))

        rule.onNodeWithText("Reemplazar").assertExists()
        rule.onNodeWithTag(CatalogReplacingCardTag).assertDoesNotExist()
        rule.onNodeWithTag(CatalogHeaderContextTag).assertDoesNotExist()
        rule.onNodeWithText("Agrega ejercicios para Bloque principal").assertDoesNotExist()
    }

    @Test
    fun laLineaDeContextoEsPuraYNoInventaTexto() {
        assertNull(catalogHeaderContextLine(isReplacement = true, targetGroupName = "Parte"))
        assertNull(catalogHeaderContextLine(isReplacement = false, targetGroupName = null))
        assertNull(catalogHeaderContextLine(isReplacement = false, targetGroupName = "  "))
        assertEquals("Agrega ejercicios para Parte", catalogHeaderContextLine(isReplacement = false, targetGroupName = "Parte"))
        assertEquals("Reemplazar", catalogHeaderTitle(true))
        assertEquals("Añadir ejercicios", catalogHeaderTitle(false))
    }
}
