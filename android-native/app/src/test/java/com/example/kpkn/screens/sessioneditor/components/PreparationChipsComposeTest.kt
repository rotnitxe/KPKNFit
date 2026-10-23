package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.ReviewedExerciseTechniqueImages
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityUnit
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.sessioneditor.components.sheets.MobilityPickerSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** H1, H2 y H4 del editor: fichas de 85 × 76, foco dentro del ejercicio y dosis de movilidad. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h2400dp-xxhdpi")
class PreparationChipsComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val baseExercise = Exercise(
        id = "press",
        name = "Press banca",
        sets = listOf(ExerciseSet(id = "s1", targetReps = 8, weight = 100.0)),
    )

    private fun warmup(id: String = "w1", percentage: Double = 0.5) = WarmupSetDefinition(
        id = id,
        percentageOfWorkingWeight = percentage,
        targetReps = 8,
        restBetween = 60,
    )

    private fun renderExercise(
        exercise: Exercise,
        onUpdate: ((Exercise) -> Exercise) -> Unit = {},
    ) {
        rule.setContent {
            val local = rememberExerciseEditorLocalState(exercise)
            ExerciseEditorExpanded(
                exercise = exercise,
                local = local,
                accentColor = Color(0xFFF49A6E),
                coverColor = Color(0xFFF49A6E),
                isSupersetExercise = false,
                suppressIndividualRest = false,
                resolved1RM = 130.0,
                predictedMetrics = emptyMap(),
                localPrEstimatedRm = null,
                relationshipAnchorName = null,
                cardioFirst = false,
                showCardioPlacementChip = false,
                onToggleCardioPlacement = null,
                cardioHistory = null,
                onUpdateExercise = onUpdate,
                onAddSet = {},
                onUpdateSet = { _, _ -> },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
                onRemoveMobility = {},
                onOpenMobility = {},
                onOpenSuperset = {},
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onUpdateRelationshipType = {},
                onUpdateRelationshipNotes = {},
            )
        }
    }

    /** Render real del ejercicio con estado: editar o quitar se refleja en la misma pantalla. */
    private fun renderStatefulExercise(initial: Exercise, onResult: (Exercise) -> Unit = {}) {
        rule.setContent {
            var current by remember { mutableStateOf(initial) }
            val local = rememberExerciseEditorLocalState(current)
            ExerciseEditorExpanded(
                exercise = current,
                local = local,
                accentColor = Color(0xFFF49A6E),
                coverColor = Color(0xFFF49A6E),
                isSupersetExercise = false,
                suppressIndividualRest = false,
                resolved1RM = 130.0,
                predictedMetrics = emptyMap(),
                localPrEstimatedRm = null,
                relationshipAnchorName = null,
                cardioFirst = false,
                showCardioPlacementChip = false,
                onToggleCardioPlacement = null,
                cardioHistory = null,
                onUpdateExercise = { transform ->
                    current = transform(current)
                    onResult(current)
                },
                onAddSet = {},
                onUpdateSet = { _, _ -> },
                onRemoveSet = {},
                onMoveSet = { _, _ -> },
                onRemoveMobility = {},
                onOpenMobility = {},
                onOpenSuperset = {},
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onUpdateRelationshipType = {},
                onUpdateRelationshipNotes = {},
            )
        }
    }

    @Test
    fun laFichaDeAproximacionMideIgualQueLaDeSerieEfectiva() = measurePreparationChipsAtScale(1f)

    @Test
    fun lasFichasConservanLasMedidasConFuenteAlCientoTreinta() = measurePreparationChipsAtScale(1.3f)

    private fun measurePreparationChipsAtScale(fontScale: Float) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Column {
                    EffectiveSeriesChip(label = "S1", load = "100 kg", dose = "8 reps", tag = "effective-series-chip-S1")
                    WarmupPreparationChips(
                        exerciseId = "press",
                        sets = listOf(warmup()),
                        workingWeightKg = 100.0,
                        capacityKg = 130.0,
                        effectiveSetCount = 4,
                        onUpdate = { _, _ -> },
                        onRemove = {},
                        onAdd = {},
                    )
                    MobilityPreparationChips(
                        exerciseId = "press",
                        series = listOf(MobilitySeries(id = "m1", name = "Gato-vaca", durationSeconds = 45)),
                        onUpdate = { _, _ -> },
                        onMove = { _, _ -> },
                        onRemove = {},
                        onAdd = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("warmup-chip-w1")
            .assertWidthIsEqualTo(85.dp)
            .assertHeightIsEqualTo(76.dp)
        rule.onNodeWithTag("effective-series-chip-S1")
            .assertWidthIsEqualTo(85.dp)
            .assertHeightIsEqualTo(76.dp)
        rule.onNodeWithTag("mobility-chip-m1")
            .assertWidthIsEqualTo(85.dp)
            .assertHeightIsEqualTo(76.dp)
        rule.onNodeWithTag("warmup-chip-add")
            .assertWidthIsEqualTo(85.dp)
            .assertHeightIsEqualTo(76.dp)
        rule.onNodeWithTag("mobility-chip-add")
            .assertWidthIsEqualTo(85.dp)
            .assertHeightIsEqualTo(76.dp)
    }

    @Test
    fun laMiniaturaUsaLaIdentidadGuardadaYElNombreCompletoSigueAccesible() {
        val movement = MobilityExerciseCatalog.getAllMobilityExercises().first {
            ReviewedExerciseTechniqueImages.mobilityImage(it.id) != null
        }
        rule.setContent {
            MobilityPreparationChips(exerciseId = "press", series = listOf(
                MobilitySeries(id = "recorded", name = movement.name, catalogConfigurationId = movement.id, durationSeconds = 45),
                MobilitySeries(id = "unknown", name = movement.name, durationSeconds = 45),
            ), onUpdate = { _, _ -> }, onMove = { _, _ -> }, onRemove = {}, onAdd = {})
        }
        rule.onNodeWithTag("mobility-chip-reviewed-art-recorded", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("mobility-chip-placeholder-unknown", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("mobility-chip-recorded", useUnmergedTree = true)
            .assertContentDescriptionEquals("${movement.name}, ${KpknSetFormat.doseTime(45)}")
    }

    @Test
    fun losContadoresDePreparacionSeLeenCompletosConFuenteAlCientoTreinta() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                Column(Modifier.width(300.dp)) {
                    ExercisePreparationSection(
                        exercise = baseExercise.copy(warmupSets = listOf(warmup()),
                            mobilitySeries = listOf(MobilitySeries(id = "m1", name = "Gato-vaca", durationSeconds = 45))),
                        mobilityBlockExpanded = true, warmupBlockExpanded = true,
                        onToggleMobilityBlock = {}, onToggleWarmupBlock = {}, onUpdateExercise = {},
                        onRemoveMobility = {}, onOpenMobility = {}, capacityKg = 130.0,
                    )
                }
            }
        }
        listOf("MOVILIDAD · 1", "APROXIMACIÓN · 1").forEach { header ->
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(header, useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Se debe medir el texto de $header", layouts.isNotEmpty())
            assertTrue("Debe leerse completo: $header", layouts.none { it.hasVisualOverflow })
        }
        rule.onNodeWithTag("mobility-global-time").assertIsDisplayed()
        rule.onNodeWithTag("warmup-common-rest").assertIsDisplayed()
    }

    @Test
    fun elFocoYLasAccionesVivenDentroDelEjercicioDesplegado() {
        renderExercise(
            baseExercise.copy(
                mobilitySeries = listOf(MobilitySeries(id = "m1", name = "Gato-vaca", durationSeconds = 45)),
                warmupSets = listOf(warmup()),
            ),
        )
        rule.onNodeWithTag("warmup-chip-w1").assert(hasAnyAncestor(hasTestTag("exercise-editor-expanded"))).performClick()
        rule.onNodeWithTag("warmup-chip-add").assert(hasAnyAncestor(hasTestTag("exercise-editor-expanded")))
        rule.onNodeWithTag("warmup-focus").assert(hasAnyAncestor(hasTestTag("exercise-editor-expanded")))
        rule.onNodeWithTag("warmup-focus-remove").assert(hasAnyAncestor(hasTestTag("exercise-editor-expanded")))
        rule.onNodeWithTag("warmup-focus-done").assert(hasAnyAncestor(hasTestTag("exercise-editor-expanded")))

        rule.onNodeWithTag("mobility-chip-m1").assert(hasAnyAncestor(hasTestTag("exercise-editor-expanded"))).performClick()
        rule.onNodeWithTag("mobility-chip-add").assert(hasAnyAncestor(hasTestTag("exercise-editor-expanded")))
        rule.onNodeWithTag("mobility-focus").assert(hasAnyAncestor(hasTestTag("exercise-editor-expanded")))
        rule.onNodeWithTag("mobility-dose-save").assert(hasAnyAncestor(hasTestTag("exercise-editor-expanded")))
        rule.onNodeWithTag("mobility-dose-time").assert(hasAnyAncestor(hasTestTag("exercise-editor-expanded")))
        rule.onNodeWithTag("mobility-dose-reps").assert(hasAnyAncestor(hasTestTag("exercise-editor-expanded")))
        rule.onNodeWithTag("mobility-dose-remove").assert(hasAnyAncestor(hasTestTag("exercise-editor-expanded")))
    }

    @Test
    fun laCargaSugeridaSaleDelPesoDeTrabajoYNoDelUnoRm() {
        renderExercise(baseExercise.copy(warmupSets = listOf(warmup())))
        rule.onNodeWithText("50 %").assertExists()
        rule.onNodeWithText("8 × 50 kg").assertExists()
        rule.onNodeWithTag("warmup-chip-w1").performClick()
        rule.onNodeWithText("Sugerida: 50 kg · entre 10 % y 95 % · al menos 1 repetición").assertExists()
    }

    @Test
    fun laCargaConDecimalesSeEscribeConComa() {
        val exercise = baseExercise.copy(
            sets = listOf(ExerciseSet(id = "s1", targetReps = 8, weight = 105.0)),
            warmupSets = listOf(warmup()),
        )
        renderExercise(exercise)
        rule.onNodeWithText("8 × 52,5 kg").assertExists()
    }

    @Test
    fun unaAproximacionAlOchentaYCincoAvisaEnLaFichaYEnElFoco() {
        renderExercise(baseExercise.copy(warmupSets = listOf(warmup(percentage = 0.85))))
        rule.onNodeWithText("85 %").assertExists()
        rule.onNodeWithTag("warmup-chip-w1").performClick()
        rule.onNodeWithText("Una aproximación al 85 % o más puede comportarse como una serie efectiva; revisa su carga y repeticiones.")
            .assertExists()
    }

    @Test
    fun editarLosKgDelFocoRecalculaElPorcentajeSobreLaSerieObjetivo() {
        var latest: Exercise? = null
        renderStatefulExercise(baseExercise.copy(warmupSets = listOf(warmup()))) { latest = it }
        rule.onNodeWithTag("warmup-chip-w1").performClick()
        rule.onNodeWithTag("warmup-focus-kg").performTextReplacement("60")
        rule.onNodeWithTag("warmup-focus-kg").performImeAction()
        rule.waitForIdle()
        assertEquals(0.6, latest!!.warmupSets.single().percentageOfWorkingWeight, 0.0001)
        rule.onNodeWithText("60 %").assertExists()
    }

    @Test
    fun unPorcentajeMenorQueElMinimoNoSeInterpretaComoFraccion() {
        var latest: Exercise? = null
        renderStatefulExercise(baseExercise.copy(warmupSets = listOf(warmup()))) { latest = it }
        rule.onNodeWithTag("warmup-chip-w1").performClick()
        rule.onNodeWithTag("warmup-focus-percent").performTextReplacement("1")
        rule.onNodeWithTag("warmup-focus-percent").performImeAction()
        rule.waitForIdle()
        assertEquals(0.1, latest!!.warmupSets.single().percentageOfWorkingWeight, 0.0001)
        rule.onNodeWithText("10 %").assertExists()
    }

    @Test
    fun unaCargaMayorQueElPesoDeTrabajoSeLimitaAlNoventaYCincoPorCiento() {
        var latest: Exercise? = null
        renderStatefulExercise(baseExercise.copy(warmupSets = listOf(warmup()))) { latest = it }
        rule.onNodeWithTag("warmup-chip-w1").performClick()
        rule.onNodeWithTag("warmup-focus-kg").performTextReplacement("120")
        rule.onNodeWithTag("warmup-focus-kg").performImeAction()
        rule.waitForIdle()
        assertEquals(0.95, latest!!.warmupSets.single().percentageOfWorkingWeight, 0.0001)
        rule.onNodeWithText("95 %").assertExists()
    }

    @Test
    fun elAltaYElQuitarDeUnaAproximacionQuedanDentroDelEjercicio() {
        var latest: Exercise? = null
        renderStatefulExercise(baseExercise.copy(warmupSets = listOf(warmup()))) { latest = it }
        rule.onNodeWithTag("warmup-chip-add").performClick()
        rule.waitForIdle()
        assertEquals(2, latest!!.warmupSets.size)
        assertEquals(0.6, latest!!.warmupSets.last().percentageOfWorkingWeight, 0.0001)
        assertEquals(6, latest!!.warmupSets.last().targetReps)

        val addedId = latest!!.warmupSets.last().id
        rule.onNodeWithTag("warmup-chip-$addedId").performClick()
        rule.onNodeWithTag("warmup-focus-remove").performClick()
        rule.waitForIdle()
        assertEquals(listOf("w1"), latest!!.warmupSets.map { it.id })
    }

    @Test
    fun elMaximoRecomendadoSeVeYSoloAvisaAlSuperarlo() {
        val threeSets = baseExercise.copy(
            sets = (1..3).map { ExerciseSet(id = "s$it", targetReps = 8, weight = 100.0) },
        )
        renderExercise(threeSets.copy(warmupSets = (1..6).map { warmup(id = "w$it") }))
        rule.onNodeWithText("Máximo recomendado: 6 aproximaciones para 3 series efectivas.").assertExists()
        assertEquals(0, rule.onAllNodesWithText("Demasiadas aproximaciones: máximo recomendado 6 para 3 series efectivas.").fetchSemanticsNodes().size)
    }

    @Test
    fun superarElMaximoRecomendadoMuestraElAviso() {
        val threeSets = baseExercise.copy(
            sets = (1..3).map { ExerciseSet(id = "s$it", targetReps = 8, weight = 100.0) },
        )
        renderExercise(threeSets.copy(warmupSets = (1..7).map { warmup(id = "w$it") }))
        rule.onNodeWithText("Máximo recomendado: 6 aproximaciones para 3 series efectivas.").assertExists()
        rule.onNodeWithText("Demasiadas aproximaciones: máximo recomendado 6 para 3 series efectivas.").assertExists()
    }

    @Test
    fun laDosisPorTiempoGuardaElSegundoYElModo() {
        var result: Exercise? = null
        val exercise = baseExercise.copy(
            mobilitySeries = listOf(MobilitySeries(id = "m1", name = "Gato-vaca", durationSeconds = 45)),
        )
        renderExercise(exercise, onUpdate = { transform -> result = transform(exercise) })
        rule.onNodeWithTag("mobility-chip-m1").performClick()
        rule.onNodeWithText("Tiempo (s)").assertExists()
        rule.onNodeWithTag("mobility-dose-save").performClick()
        val saved = result!!.mobilitySeries.single()
        assertEquals(45, saved.durationSeconds)
        assertEquals(MobilityUnit.SECONDS, saved.unit)
    }

    @Test
    fun laDosisPorRepeticionesCambiaElModoYDaLasReps() {
        var result: Exercise? = null
        val exercise = baseExercise.copy(
            mobilitySeries = listOf(
                MobilitySeries(id = "m1", name = "Gato-vaca", durationSeconds = 45),
            ),
        )
        renderExercise(exercise, onUpdate = { transform -> result = transform(exercise) })
        rule.onNodeWithTag("mobility-chip-m1").performClick()
        rule.onNodeWithTag("mobility-dose-reps").performClick()
        rule.onNodeWithText("Dosis por repeticiones: 10 por serie.").assertExists()
        rule.onNodeWithTag("mobility-dose-save").performClick()
        val saved = result!!.mobilitySeries.single()
        assertEquals(MobilityUnit.REPS, saved.unit)
        assertEquals("10", saved.reps)
        assertNull(saved.durationSeconds)
    }

    @Test
    fun reordenarUnMovimientoLoSubeEnLaListaDelEjercicio() {
        var result: Exercise? = null
        val exercise = baseExercise.copy(
            mobilitySeries = listOf(
                MobilitySeries(id = "m1", name = "Gato-vaca", durationSeconds = 45),
                MobilitySeries(id = "m2", name = "Sentadilla profunda", durationSeconds = 30),
            ),
        )
        renderExercise(exercise, onUpdate = { transform -> result = transform(exercise) })
        rule.onNodeWithTag("mobility-chip-m2").performClick()
        rule.onNodeWithTag("mobility-move-up").performClick()
        assertEquals(listOf("m2", "m1"), result!!.mobilitySeries.map { it.id })
    }

    @Test
    fun elCatalogoAnadeYQuitaConLosToquesDeLaHoja() {
        val catalog = MobilityExerciseCatalog.getAllMobilityExercises()
        val first = catalog.first()
        var added: String? = null
        var removed: String? = null
        rule.setContent {
            MobilityPickerSheet(
                selectedMobilityIds = setOf(first.id),
                onAdd = { added = it.id },
                onRemove = { removed = it.id },
                onDismiss = {},
            )
        }
        rule.onNodeWithText(first.name).assertExists()
        rule.onNodeWithText("Añadido · Quitar").performClick()
        assertEquals(first.id, removed)
        rule.onAllNodesWithText("Añadir")[0].performClick()
        assertTrue(added != null)
        assertTrue(added != first.id)
    }

    @Test
    fun elCatalogoUsaLosTextosYLosToquesDeLaHoja() {
        val catalog = MobilityExerciseCatalog.getAllMobilityExercises()
        val first = catalog.first()
        rule.setContent {
            MobilityPickerSheet(
                selectedMobilityIds = emptySet(),
                onAdd = {},
                onDismiss = {},
            )
        }
        rule.onNodeWithText(first.name).assertExists()
        assertTrue(rule.onAllNodesWithText("Añadir").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Ver ficha").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun elCatalogoMuestraLaAccionCompletaConFuenteAlCientoTreinta() {
        val first = MobilityExerciseCatalog.getAllMobilityExercises().first()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                Column(Modifier.width(360.dp)) {
                    MobilityPickerSheet(
                        selectedMobilityIds = setOf(first.id),
                        onAdd = {},
                        onRemove = {},
                        onDismiss = {},
                    )
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("Añadido · Quitar", useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
        assertTrue("La acción debe proporcionar su disposición de texto", layouts.isNotEmpty())
        layouts.forEach { layout ->
            val lines = (0 until layout.lineCount)
            val detail = "${layout.size}, párrafo=${layout.multiParagraph.width}, líneas=${lines.map { layout.getLineLeft(it) to layout.getLineRight(it) }}, ${layout.layoutInput.constraints}"
            println("EDC_H3_LAYOUT: $detail")
            assertEquals("Añadido · Quitar", layout.layoutInput.text.text)
            assertTrue("No debe cortarse en alto: $detail", !layout.didOverflowHeight)
            assertTrue("Todos los caracteres deben estar visibles: $detail",
                layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) == layout.layoutInput.text.length &&
                    lines.none { layout.isLineEllipsized(it) })
            // Los límites se redondean a píxeles enteros; se permite únicamente ese píxel de redondeo.
            assertTrue("La línea completa debe caber en su zona: $detail", lines.all {
                layout.getLineLeft(it) >= -1f && layout.getLineRight(it) <= layout.size.width + 1f
            })
        }
        rule.onAllNodesWithText("Ver ficha")[0].assertIsDisplayed()
    }

}
