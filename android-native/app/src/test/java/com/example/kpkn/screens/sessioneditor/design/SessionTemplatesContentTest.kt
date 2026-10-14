package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateKind
import com.example.kpkn.data.sessions.SessionTemplatePublicationStatus
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.SessionEditorSamples
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourSeenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cuerpo de la pestaña Plantillas del asistente (SE4b) montado suelto: en Robolectric los toques dentro de una `KpknSheet` no llegan, así
 * que se prueban las filas, el menú «⋯», los filtros y el botón de guardar sin la hoja. Cubre aplicar (con su id), archivar y borrar con
 * confirmación, un filtro activo que filtra la lista y «Guardar esta sesión», que abre el guardado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SessionTemplatesContentTest {

    @get:Rule
    val rule = createComposeRule()

    private class NoSeen : TourSeenStore {
        override fun seenIds(): Set<String> = emptySet()

        override fun markSeen(tourId: String) = Unit
    }

    /**
     * Monta el cuerpo con el anfitrión de superposiciones de la app: los menús, los diálogos y las hojas salen en la raíz, no en línea
     * dentro de la fila de filtros (donde su geometría no es la de producción).
     */
    private fun setBody(content: @Composable () -> Unit) {
        rule.setContent {
            val host = remember { KpknOverlayHostController(tourSeen = NoSeen()) }
            CompositionLocalProvider(
                LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides host,
            ) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    KpknOverlayHostContent(host)
                }
            }
        }
    }

    private fun plantilla(
        id: String,
        nombre: String,
        origen: SessionTemplateSourceType,
        nivel: Difficulty,
    ) = SessionTemplate(
        id = id,
        sourceType = origen,
        name = nombre,
        description = "",
        kind = SessionTemplateKind.TRAINING,
        publicationStatus = SessionTemplatePublicationStatus.VERIFIED,
        difficulty = nivel,
        session = Session(
            id = "$id-sesion",
            name = nombre,
            exercises = listOf(
                Exercise(
                    id = "$id-ej",
                    name = "Press banca",
                    sets = listOf(SessionEditorSamples.workSet("$id-s1", 6, 8, 2)),
                ),
            ),
        ),
    )

    @Test
    fun aplicarLlamaConElIdDeLaPlantilla() {
        val propia = plantilla("u1", "Empuje propio", SessionTemplateSourceType.USER, Difficulty.INTERMEDIO)
        var aplicada: SessionTemplate? = null
        setBody {
            SessionAssistantTemplatesTab(
                templates = listOf(propia),
                searchQuery = "",
                applyDecision = null,
                onSearchChange = {},
                onSelectTemplate = { aplicada = it },
                onConfirmApplyTemplate = {},
                onCancelApply = {},
            )
        }
        rule.onNodeWithContentDescription("Aplicar plantilla Empuje propio").performClick()
        assertEquals("u1", aplicada?.id)
    }

    @Test
    fun menuDeUnaPlantillaPropiaArchivaYBorraConConfirmacion() {
        val propia = plantilla("u1", "Empuje propio", SessionTemplateSourceType.USER, Difficulty.INTERMEDIO)
        var archivada: String? = null
        var borrada: String? = null
        setBody {
            SessionAssistantTemplatesTab(
                templates = listOf(propia),
                searchQuery = "",
                applyDecision = null,
                onSearchChange = {},
                onSelectTemplate = {},
                onConfirmApplyTemplate = {},
                onCancelApply = {},
                onArchiveUserTemplate = { archivada = it },
                onDeleteUserTemplate = { borrada = it },
            )
        }
        rule.onNodeWithContentDescription("Más opciones de Empuje propio").performClick()
        rule.onNodeWithText("Archivar").performClick()
        assertEquals("u1", archivada)

        rule.onNodeWithContentDescription("Más opciones de Empuje propio").performClick()
        rule.onNodeWithText("Eliminar").performClick()
        // Borrar pide confirmación: hasta confirmar, no se llama al borrado.
        assertNull(borrada)
        rule.onNodeWithText("Eliminar plantilla").assertExists()
        rule.onNodeWithText("Eliminar").performClick()
        assertEquals("u1", borrada)
    }

    @Test
    fun unFiltroActivoFiltraLaLista() {
        val intermedia = plantilla("c1", "Sesión intermedia", SessionTemplateSourceType.SYSTEM, Difficulty.INTERMEDIO)
        val exigente = plantilla("c2", "Sesión exigente", SessionTemplateSourceType.SYSTEM, Difficulty.AVANZADO)
        setBody {
            SessionAssistantTemplatesTab(
                templates = listOf(intermedia, exigente),
                searchQuery = "",
                applyDecision = null,
                onSearchChange = {},
                onSelectTemplate = {},
                onConfirmApplyTemplate = {},
                onCancelApply = {},
            )
        }
        rule.onNodeWithText("Sesión intermedia").assertExists()
        rule.onNodeWithText("Sesión exigente").assertExists()

        rule.onNodeWithText("Nivel").performClick()
        rule.onNodeWithText("Exigente").performClick()

        rule.onNodeWithText("Sesión intermedia").assertDoesNotExist()
        rule.onNodeWithText("Sesión exigente").assertExists()
    }

    @Test
    fun guardarEstaSesionAbreElGuardadoConSuResumen() {
        setBody {
            SessionAssistantTemplatesTab(
                templates = emptyList(),
                searchQuery = "",
                applyDecision = null,
                onSearchChange = {},
                onSelectTemplate = {},
                onConfirmApplyTemplate = {},
                onCancelApply = {},
                sessionSummary = "6 ejercicios · 22 series",
            )
        }
        rule.onNodeWithText("Todavía no tienes plantillas guardadas.").assertExists()
        rule.onNodeWithText("Guardar esta sesión").performClick()
        rule.onNodeWithText("Guardar sesión como plantilla").assertExists()
        rule.onNodeWithText("6 ejercicios · 22 series").assertExists()
    }

    @Test
    fun resumenDeLaSesionEnSingularYPlural() {
        assertNull(sessionCountsLine(null))
        val una = Session(
            id = "s1",
            name = "Una",
            exercises = listOf(Exercise(id = "e", name = "Press", sets = listOf(SessionEditorSamples.workSet("s", 6, 8)))),
        )
        // El número va pegado a su unidad con un espacio que no se parte ( ).
        assertEquals("1 ejercicio · 1 serie", sessionCountsLine(una))
    }
}
