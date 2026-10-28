package com.example.kpkn.screens.programs

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lo que «Crear y abrir» de la hoja «Desde cero» entrega a la pantalla. */
private data class BlankCreation(val name: String, val mode: ProgramMode, val cover: String)

/**
 * Las hojas de «Nuevo programa» (los cuatro caminos y «Desde cero») y la página «Catálogo».
 *
 * Los controles de las hojas se activan con la acción de clic semántica (`OnClick`), la misma que usa TalkBack: en este entorno de
 * prueba el toque táctil no llega a los controles de la hoja, aunque el nodo esté dentro de la ventana (medido con un diagnóstico).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class NewProgramSheetsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun conAsistenteLaHojaOfreceLosCuatroCaminos() {
        rule.setContent {
            NewProgramSheet(onDismiss = {}, onTailored = {}, onCatalog = {}, onImport = {}, onBlank = {})
        }

        rule.onNodeWithText("A tu medida").assertExists()
        rule.onNodeWithText("Del catálogo").assertExists()
        rule.onNodeWithText("Importar").assertExists()
        rule.onNodeWithText("Desde cero").assertExists()
    }

    @Test
    fun sinAsistenteNoSaleLaOpcionAMiMedida() {
        rule.setContent {
            NewProgramSheet(onDismiss = {}, onTailored = null, onCatalog = {}, onImport = {}, onBlank = {})
        }

        rule.onNodeWithText("A tu medida").assertDoesNotExist()
        rule.onNodeWithText("Del catálogo").assertExists()
    }

    @Test
    fun tocarDelCatalogoLlamaAOnCatalog() {
        var abiertos = 0
        rule.setContent {
            NewProgramSheet(onDismiss = {}, onTailored = null, onCatalog = { abiertos++ }, onImport = {}, onBlank = {})
        }

        rule.onNodeWithText("Del catálogo").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, abiertos)
    }

    @Test
    fun desdeCeroSinEscribirCreaConElNombreSugeridoYLosValoresDePorDefecto() {
        var creado: BlankCreation? = null
        rule.setContent {
            BlankProgramSheet(
                suggestedName = "Nuevo programa 3",
                onDismiss = {},
                onCreate = { name, mode, cover -> creado = BlankCreation(name, mode, cover) },
            )
        }

        rule.onNodeWithText("Crear y abrir").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(
            BlankCreation("Nuevo programa 3", ProgramMode.HYPERTROPHY, "kpkn-lines://PERFIL/CARBON"),
            creado,
        )
    }

    @Test
    fun desdeCeroEscribirUnNombreYElegirPowerliftingLoLleva() {
        var creado: BlankCreation? = null
        rule.setContent {
            BlankProgramSheet(
                suggestedName = "Nuevo programa 3",
                onDismiss = {},
                onCreate = { name, mode, cover -> creado = BlankCreation(name, mode, cover) },
            )
        }

        rule.onNode(hasSetTextAction()).performTextReplacement("Mi fuerza")
        rule.onNodeWithText("Powerlifting").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Crear y abrir").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(
            BlankCreation("Mi fuerza", ProgramMode.POWERLIFTING, "kpkn-lines://PERFIL/CARBON"),
            creado,
        )
    }

    /** La página «Catálogo» enseña su título y la primera tarjeta de la biblioteca, con su etiqueta. */
    @Test
    fun elCatalogoMuestraSuTituloYSuPrimerPlan() {
        val primero = libraryOrder(
            PersonalizedPlanCatalog.listedEntries()
                .filter { it.publication == PublicationState.PUBLISHED && isLibraryFamilyEntry(it) },
        ).first()
        rule.setContent {
            ProgramCatalogPage(
                onBack = {},
                hasPlanAction = true,
                onCreateFromTemplate = {},
                onSelectProtocol = {},
                onSelectPlan = {},
                onOpenConcept = null,
            )
        }

        rule.onNodeWithText("Catálogo").assertExists()
        rule.onNodeWithTag("catalog-plan-${primero.id}").assertExists()
    }
}
