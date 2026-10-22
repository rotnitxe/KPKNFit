package com.example.kpkn.screens.sessioneditor.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.domain.exercises.catalogv2.*
import com.example.kpkn.screens.sessioneditor.CatalogSupersetConfig
import com.example.kpkn.screens.sessioneditor.design.*
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourSeenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** E1–E6, E8 y E9: recorridos del selector real con configuraciones del asset aprobado. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-mdpi", application = Application::class)
class CatalogScreensV2Test {
    @get:Rule val rule = createComposeRule()
    private var selection = emptyList<ExerciseMuscleInfo>()
    private var committed = emptyList<ExerciseMuscleInfo>()
    private var superset: CatalogSupersetConfig? = null
    private var latestQuery = ""
    private object NoTourSeen : TourSeenStore {
        override fun seenIds(): Set<String> = emptySet()
        override fun markSeen(tourId: String) = Unit
    }

    private fun definition(id: String) = CatalogTestBuilders.realDefinition(id)
    private fun name(id: String) = definition(id).canonicalName
    private fun repository(vararg ids: String): InMemoryExerciseCatalogRepositoryV2 {
        val real = CatalogTestBuilders.real
        return InMemoryExerciseCatalogRepositoryV2(real.copy(families = real.families.mapNotNull { family ->
            family.definitions.filter { it.id in ids }.takeIf { it.isNotEmpty() }?.let { family.copy(definitions = it) }
        })).also { runBlocking { it.load() } }
    }

    private fun mount(
        vararg ids: String,
        query: String = "",
        technique: Boolean = false,
        replacing: Boolean = false,
        addingToSuperset: Boolean = false,
        source: ExerciseCatalogRepositoryV2 = repository(*ids),
    ): StateRestorationTester {
        latestQuery = query
        val overlays = KpknOverlayHostController(tourSeen = NoTourSeen)
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            var text by rememberSaveable { mutableStateOf(query) }
            var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
            KPKNTheme {
                CompositionLocalProvider(LocalKpknOverlayHost provides overlays) {
                    Box(Modifier.fillMaxSize()) {
                        ExercisePickerV2Catalog(
                            repository = source, query = text, onSearch = { text = it; latestQuery = it },
                            editingExisting = technique || replacing,
                            singleSelectionLabel = if (technique) "Elegir" else null,
                            selectedExercisesIds = selected.toSet(), onSelect = { committed = listOf(it) },
                            onMultiSelect = { committed = it; it.map { info -> info.id } },
                            onSelectionChange = { selection = it; selected = it.map { info -> info.id } },
                            onOpenExerciseDetail = {}, onDismiss = {}, opaqueSurface = true,
                            onCreateSupersetConfigured = { infos, config -> committed = infos; superset = config },
                            dismissAfterMultiSelect = false, isSupersetAddMode = addingToSuperset,
                            replacement = if (replacing) CatalogReplacementContext("Original", "4 × 4–6 · RIR 2") else null,
                        )
                        KpknOverlayHostContent(overlays)
                    }
                }
            }
        }
        if (ids.isNotEmpty() && query.isBlank()) waitFor(name(ids.first()))
        return restoration
    }

    private fun waitFor(text: String) = try {
        rule.waitUntil(15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: ComposeTimeoutException) {
        // El árbol semántico en el mensaje evita una segunda corrida a ciegas.
        throw AssertionError("No apareció «$text». Árbol:\n" + rule.onRoot().printToString(maxDepth = 40), e)
    }

    /** Textos y descripciones visibles en el arbol sin fusionar: diagnostico compacto para los recorridos con hoja. */
    private fun visibleTexts(): String {
        val out = mutableListOf<String>()
        fun walk(node: androidx.compose.ui.semantics.SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.let { out += it.joinToString("|") { t -> t.text } }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.let { out += "cd:" + it.joinToString("|") }
            node.children.forEach(::walk)
        }
        walk(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return out.joinToString(" / ")
    }

    private fun checkpoint(label: String, text: String) {
        if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) {
            throw AssertionError("[" + label + "] falta " + text + ". Visibles: " + visibleTexts())
        }
    }

    /** La hoja entra con una animación de resorte: se deja terminar antes de tocar sus controles. */
    private fun settleSheet() {
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
    }

    private fun select(id: String) {
        val description = "Añadir ${name(id)} a la selección"
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(description))
        rule.onNodeWithContentDescription(description).assertHeightIsAtLeast(48.dp).performClick()
    }

    @Test fun e1AportesDelCatalogoRealSinPorcentajes() {
        val catalog = CatalogTestBuilders.real
        val configurationId = "chest_supported_row__cable__low__close"
        val info = requireNotNull(catalog.toLegacySelection(ExerciseSelectionV2("chest_supported_row", configurationId, catalog.catalogRevision)))
        val contributions = oneSeriesVolumeContributions(info)
        assertTrue(contributions.any { it.role == MuscleRole.PRIMARY && it.seriesEquivalent == 1.0 })
        assertTrue(contributions.any { it.role == MuscleRole.SECONDARY && it.seriesEquivalent == 0.5 })
        assertTrue(contributions.any { it.role == MuscleRole.STABILIZER && it.seriesEquivalent == 0.4 })
        rule.setContent { CatalogMuscleContribution(info) }
        rule.onNodeWithText("Primario · 1 serie").assertExists()
        rule.onAllNodesWithText("Secundario · 0,5 series").fetchSemanticsNodes().also { assertTrue(it.isNotEmpty()) }
        rule.onNodeWithText("Estabilizador · 0,4 series").assertExists()
        rule.onAllNodes(hasText("%", substring = true)).assertCountEquals(0)
    }

    @Test fun e1RespetaElAporteExplicitoYElPesoNeutralizadorDeEMI() {
        val info = ExerciseMuscleInfo("custom:emi", "EMI", involvedMuscles = listOf(
            InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, volumeContribution = 0.37),
            InvolvedMuscle("Bíceps", MuscleRole.NEUTRALIZER),
        ))
        rule.setContent { CatalogMuscleContribution(info) }
        rule.onNodeWithText("Primario · 0,37 series").assertExists()
        rule.onNodeWithText("Neutralizador · 0,1 series").assertExists()
    }

    @Test fun e1LaFilaRealMuestraElAporteAlDesplegarSinAbrirLaFicha() {
        mount("bench_press")
        rule.onNodeWithText(name("bench_press")).performClick()
        rule.onNodeWithTag("catalog_muscle_contribution").assertExists()
        rule.onNodeWithText("Primario · 1 serie").assertExists()
        rule.onNodeWithTag("exercise-ficha").assertDoesNotExist()
    }

    @Test fun e2AlturaYAgarreSeCambianEnElResultadoYSeEntreganExactos() {
        mount("chest_supported_row")
        rule.onNodeWithText(name("chest_supported_row")).performClick()
        rule.onNodeWithText("En polea").performClick()
        rule.onNodeWithTag("catalog_inline_pulley_height").assertIsDisplayed()
        rule.onNodeWithTag("catalog_inline_grip_width").assertIsDisplayed()
        rule.onNodeWithContentDescription("Polea Alta").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText("Amplio").performClick()
        rule.onNodeWithContentDescription("Polea Baja").performClick()
        select("chest_supported_row")
        assertEquals(1, selection.size)
        val chosen = definition("chest_supported_row").configurations.single { it.id == selection.single().catalogConfigurationId }
        assertEquals("cable", chosen.selectedOptions["implement"])
        assertEquals("low", chosen.selectedOptions["pulley_height"])
        assertEquals("wide", chosen.selectedOptions["grip_width"])
        rule.onNodeWithTag("exercise-ficha").assertDoesNotExist()
        rule.onNodeWithText("Añadir 1").performClick()
        assertEquals(chosen.id, committed.single().catalogConfigurationId)
    }

    @Test fun e2CambiarMaterialOcultaLaAlturaInaplicableYPermiteAnadirOtraVariante() {
        mount("bench_press", "chest_supported_row")
        select("bench_press"); select("chest_supported_row")
        rule.onNodeWithText(name("chest_supported_row")).performClick()
        rule.onNodeWithText("En polea").performClick()
        rule.onNodeWithContentDescription("Polea Alta").performClick()
        select("chest_supported_row")
        rule.onNodeWithText("Con mancuernas").performClick()
        rule.onNodeWithTag("catalog_inline_pulley_height").assertDoesNotExist()
        assertEquals(listOf("bench_press", "chest_supported_row", "chest_supported_row"), selection.map { it.catalogDefinitionId })
        assertEquals("cable", definition("chest_supported_row").configurations.single {
            it.id == selection.last().catalogConfigurationId
        }.selectedOptions["implement"])
        rule.onNodeWithContentDescription("Quitar ${name("chest_supported_row")} de la selección").performClick()
        assertEquals(listOf("bench_press", "chest_supported_row"), selection.map { it.catalogDefinitionId })
        assertTrue(selection.last().catalogConfigurationId!!.contains("cable"))
    }

    @Test fun e2ElBorradorDeAlturaYAgarreSeRestauraAntesDeSeleccionar() {
        val restoration = mount("chest_supported_row")
        rule.onNodeWithText(name("chest_supported_row")).performClick()
        rule.onNodeWithText("En polea").performClick()
        rule.onNodeWithContentDescription("Polea Alta").performClick()
        rule.onNodeWithText("Amplio").performClick()
        restoration.emulateSavedInstanceStateRestore()
        waitFor("Altura de polea")
        rule.onNodeWithContentDescription("Polea Alta").assertIsOn()
        rule.onNodeWithText("Amplio").assertIsOn()
        select("chest_supported_row")
        assertEquals("chest_supported_row__cable__high__wide", selection.single().catalogConfigurationId)
    }

    @Test fun e3ElOrdenSeEditaSeRestauraYSeEntregaCompleto() {
        val restoration = mount("bench_press", "chest_supported_row")
        select("bench_press"); select("chest_supported_row")
        rule.onNodeWithContentDescription("Ver los ejercicios elegidos").performClick()
        rule.onNodeWithText("ORDEN DE LA SELECCIÓN").assertExists()
        rule.onNodeWithContentDescription("Subir ${name("chest_supported_row")} en el orden").performClick()
        restoration.emulateSavedInstanceStateRestore()
        waitFor("ORDEN DE LA SELECCIÓN")
        rule.onNodeWithText("Añadir 2").performClick()
        assertEquals(listOf("chest_supported_row", "bench_press"), committed.map { it.catalogDefinitionId })
        rule.onNodeWithContentDescription("Quitar ${name("chest_supported_row")} de la selección")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Orden 1"))
    }

    @Test fun e3LaListaElegidaSeConservaAlRecrearYSoloSePliegaAlVaciar() {
        val restoration = mount("bench_press", "chest_supported_row")
        select("bench_press"); select("chest_supported_row")
        rule.onNodeWithContentDescription("Ver los ejercicios elegidos").performClick()
        rule.onNodeWithText("ORDEN DE LA SELECCIÓN").assertExists()
        rule.onNodeWithContentDescription("Quitar ${name("bench_press")}").performClick()
        rule.onNodeWithContentDescription("Quitar ${name("chest_supported_row")}").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("ORDEN DE LA SELECCIÓN").fetchSemanticsNodes().isEmpty()
        }
        select("bench_press")
        rule.onNodeWithContentDescription("Ver los ejercicios elegidos").performClick()
        rule.onNodeWithText("ORDEN DE LA SELECCIÓN").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        waitFor("ORDEN DE LA SELECCIÓN")
    }

    @Test fun e4CuatroPermitenSuperserieYCincoSoloAnadir() {
        val ids = arrayOf("bench_press", "chest_supported_row", "romanian_deadlift", "gironda_row", "push_up")
        mount(*ids)
        ids.take(4).forEach(::select)
        rule.onNodeWithText("Superserie").assertExists()
        select(ids.last())
        rule.onNodeWithText("Superserie").assertDoesNotExist()
        rule.onNodeWithText("Añadir 5").assertExists()
        assertNull(superset)
        assertTrue(committed.isEmpty())
    }

    @Test fun e4CreadorConservaRondasDescansosYOrdenAlRecrear() {
        val restoration = mount("bench_press", "chest_supported_row")
        select("bench_press"); select("chest_supported_row")
        rule.onNodeWithText("Superserie").performClick()
        checkpoint("tras Superserie", "Configurar superserie")
        settleSheet()
        rule.onNodeWithContentDescription("Añadir una ronda").performClick()
        checkpoint("tras ronda", "Configurar superserie")
        val sinDescanso = hasText("0\u00A0s") or hasText("0 s") // KpknSetFormat.doseTime usa espacio duro
        if (rule.onAllNodes(sinDescanso, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            throw AssertionError("Falta el atajo de 0 s. Arbol:\n" + visibleTexts())
        }
        rule.onAllNodes(sinDescanso, useUnmergedTree = true).onFirst().performClick()
        restoration.emulateSavedInstanceStateRestore()
        waitFor("Configurar superserie")
        settleSheet()
        rule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Crear superserie"))
        rule.onNodeWithText("Crear superserie").performClick()
        assertEquals(CatalogSupersetConfig(4, 0, 120), superset)
        assertEquals(listOf("bench_press", "chest_supported_row"), committed.map { it.catalogDefinitionId })
    }

    @Test fun e4AnadirAUnaSuperserieTieneSuTituloYSuAccionDirecta() {
        mount("bench_press", "chest_supported_row", addingToSuperset = true)
        rule.onNodeWithText("Añadir a la superserie").assertExists()
        select("bench_press"); select("chest_supported_row")
        rule.onNodeWithText("Superserie").assertDoesNotExist()
        rule.onNodeWithText("Añadir 2").performClick()
        assertEquals(2, committed.size)
        assertNull(superset)
    }

    @Test fun e4ElCreadorNoConfirmaCincoNiRecortaLaSeleccion() {
        var confirmations = 0
        rule.setContent {
            CatalogSupersetConfiguratorContent(
                selectedExercises = List(5) { ExerciseMuscleInfo("$it", "Ejercicio $it") },
                initialConfig = CatalogSupersetConfig(), onDismiss = {}, onConfirm = { confirmations++ },
            )
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Crear superserie"))
        rule.onNodeWithText("Crear superserie").assertIsNotEnabled()
        rule.onAllNodesWithText("Ejercicio ", substring = true).assertCountEquals(5)
        assertEquals(0, confirmations)
    }

    @Test fun e4LaHojaSePliegaSinCrearNiPerderLaSeleccion() {
        mount("bench_press", "chest_supported_row")
        select("bench_press"); select("chest_supported_row")
        rule.onNodeWithText("Superserie").performClick()
        waitFor("Configurar superserie")
        settleSheet()
        rule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Cancelar"))
        rule.onNodeWithText("Cancelar").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("Configurar superserie").fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithText("Añadir 2").assertExists()
        assertTrue(committed.isEmpty())
        assertNull(superset)
    }

    @Test fun e5ReemplazarEsUnaPropuestaHastaConfirmar() {
        mount("bench_press", replacing = true)
        rule.onAllNodesWithText("Reemplazar").assertCountEquals(2)
        rule.onNodeWithText("REEMPLAZANDO").assertExists()
        select("bench_press")
        assertTrue(committed.isEmpty())
        rule.onNodeWithText("Se mantienen las series del plan: 4 × 4–6 · RIR 2").assertExists()
        rule.onNodeWithTag(CatalogReplaceButtonTag).performClick()
        assertEquals("bench_press", committed.single().catalogDefinitionId)
    }

    @Test fun e6FiltroVacioPermiteVerEnTodosSinBorrarLaBusqueda() {
        mount("bench_press", query = "press")
        waitFor(name("bench_press"))
        rule.onNodeWithText("Tren inferior").performClick()
        waitFor("Ver en Todos")
        rule.onNodeWithText("Ninguna press en Tren inferior").assertExists()
        rule.onNodeWithText("Hay coincidencias en otros grupos. No hace falta crear nada.").assertExists()
        rule.onNodeWithText("Crear ejercicio").assertDoesNotExist()
        rule.onNodeWithText("Ver en Todos").performClick()
        waitFor(name("bench_press"))
        assertEquals("press", latestQuery)
    }

    @Test fun e6BusquedaSinCoincidenciasOfreceCrearYSePuedeLimpiar() {
        mount("bench_press", query = "zzzinexistentezzz")
        waitFor("Crear ejercicio")
        rule.onNodeWithText("No encontramos “zzzinexistentezzz”").assertExists()
        rule.onNodeWithText("Ver en Todos").assertDoesNotExist()
        rule.onNodeWithContentDescription("Borrar búsqueda").assertHeightIsAtLeast(48.dp).performClick()
        waitFor(name("bench_press"))
        assertEquals("", latestQuery)
    }

    @Test fun e8CargaYErrorConservanBusquedaYFiltrosYReintentan() {
        val delegate = repository("bench_press")
        val states = MutableStateFlow<ExerciseCatalogStateV2>(ExerciseCatalogStateV2.Loading)
        var retries = 0
        val source = object : ExerciseCatalogRepositoryV2 by delegate {
            override val state = states
            override suspend fun load() { retries++; states.value = ExerciseCatalogStateV2.Ready(CatalogTestBuilders.real) }
        }
        mount(query = "press", source = source)
        rule.onNodeWithTag("catalog_loading").assertExists()
        rule.onNodeWithText("Tren superior").assertExists()
        rule.runOnIdle { states.value = ExerciseCatalogStateV2.Error("prueba") }
        rule.onNodeWithText("No se pudo cargar el catálogo de ejercicios.").assertExists()
        rule.onNodeWithText("Reintentar").performClick()
        waitFor(name("bench_press"))
        assertEquals(1, retries)
        assertEquals("press", latestQuery)
    }

    @Test fun e9ElegirExigeConfirmacionConUnSoloCandidatoYSeRestaura() {
        val restoration = mount("bench_press", "chest_supported_row", technique = true)
        rule.onNodeWithText("Elegir ejercicio").assertExists()
        rule.onNodeWithTag("catalog_technique_confirm").assertIsNotEnabled()
        select("bench_press"); select("chest_supported_row")
        assertTrue(committed.isEmpty())
        assertEquals(listOf("chest_supported_row"), selection.map { it.catalogDefinitionId })
        rule.onNodeWithText("Añadir 1").assertDoesNotExist()
        rule.onNodeWithText("Superserie").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("catalog_technique_confirm").assertIsEnabled().performClick()
        assertEquals("chest_supported_row", committed.single().catalogDefinitionId)
    }

    @Test fun e9LaVarianteCambiaElCandidatoPendienteSinConfirmarlo() {
        mount("chest_supported_row", technique = true)
        select("chest_supported_row")
        rule.onAllNodesWithText(name("chest_supported_row")).onFirst().performClick()
        rule.onNodeWithText("En polea").performClick()
        rule.onNodeWithContentDescription("Polea Baja").performClick()
        rule.onNodeWithText("Cerrado").performClick()
        assertTrue(committed.isEmpty())
        assertEquals("chest_supported_row__cable__low__close", selection.single().catalogConfigurationId)
        rule.onNodeWithTag("catalog_technique_confirm").performClick()
        assertEquals(selection.single().catalogConfigurationId, committed.single().catalogConfigurationId)
    }
}
