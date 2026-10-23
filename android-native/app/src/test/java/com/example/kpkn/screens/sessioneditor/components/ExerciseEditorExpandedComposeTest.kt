package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseRelationshipType
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.WarmupSetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ER1c1, R1 §4.1: la preparación del ejercicio desplegado a la vista (píldoras punteadas y bloques con su contador). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h2000dp-xxhdpi")
class ExerciseEditorExpandedComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val bare = Exercise(
        id = "press",
        name = "Press",
        sets = listOf(ExerciseSet(id = "s1", targetReps = 8)),
    )

    private fun render(
        exercise: Exercise,
        onUpdate: ((Exercise) -> Exercise) -> Unit = {},
        onOpenMobility: () -> Unit = {},
        relationshipAnchorName: String? = null,
    ) {
        val current = mutableStateOf(exercise)
        rule.setContent {
            EditorOverlayTestHost {
                val local = rememberExerciseEditorLocalState(current.value)
                ExerciseEditorExpanded(
                    exercise = current.value,
                    local = local,
                    accentColor = Color(0xFFF49A6E),
                    coverColor = Color(0xFFF49A6E),
                    isSupersetExercise = false,
                    suppressIndividualRest = false,
                    resolved1RM = 100.0,
                    predictedMetrics = emptyMap(),
                    localPrEstimatedRm = null,
                    relationshipAnchorName = relationshipAnchorName,
                    cardioFirst = false,
                    showCardioPlacementChip = false,
                    onToggleCardioPlacement = null,
                    cardioHistory = null,
                    onUpdateExercise = { transform ->
                        val updated = transform(current.value)
                        current.value = updated
                        onUpdate { updated }
                    },
                    onAddSet = {},
                    onUpdateSet = { _, _ -> },
                    onRemoveSet = {},
                    onMoveSet = { _, _ -> },
                    onRemoveMobility = {},
                    onOpenMobility = onOpenMobility,
                    onOpenSuperset = {},
                    onOpenRelationshipPicker = {},
                    onClearRelationship = {},
                    onUpdateRelationshipType = {},
                    onUpdateRelationshipNotes = {},
                )
            }
        }
    }

    @Test
    fun sinMovilidadNiAproximacionSalenLasDosPildorasPunteadas() {
        render(bare)
        rule.onNodeWithText("+ Movilidad").assertExists()
        rule.onNodeWithText("+ Aproximación").assertExists()
    }

    @Test
    fun laPildoraDeAproximacionCreaLaPrimeraConSuValorPorDefecto() {
        var result: Exercise? = null
        render(bare, onUpdate = { transform -> result = transform(bare) })
        rule.onNodeWithText("+ Aproximación").performClick()
        val created = result!!.warmupSets
        assertEquals(1, created.size)
        assertEquals(0.5, created[0].percentageOfWorkingWeight, 0.0001)
        assertEquals(8, created[0].targetReps)
        assertEquals(60, created[0].restBetween)
    }

    @Test
    fun laPildoraDeMovilidadAbreElCatalogo() {
        var opened = false
        render(bare, onOpenMobility = { opened = true })
        rule.onNodeWithText("+ Movilidad").performClick()
        assertTrue(opened)
    }

    @Test
    fun laRelacionMuestraElNombreDelAnclaYNoElIdDeConfiguracion() {
        val configurationId = "reverse_pec_fly__bilateral__dumbbells"
        val variation = bare.copy(
            relativeToCanonicalExerciseId = configurationId,
            relationshipType = ExerciseRelationshipType.VARIATION,
        )

        render(variation, relationshipAnchorName = "Aperturas Inversas")

        rule.onNodeWithText("Variacion de Aperturas Inversas").assertExists()
        rule.onNodeWithText(configurationId).assertDoesNotExist()
    }

    @Test
    fun unaRelacionSinNombreResueltoNoExponeElIdDeConfiguracion() {
        val configurationId = "reverse_pec_fly__bilateral__dumbbells"
        val variation = bare.copy(
            relativeToCanonicalExerciseId = configurationId,
            relationshipType = ExerciseRelationshipType.VARIATION,
        )

        render(variation)

        rule.onNodeWithText("Variacion de un ejercicio del programa").assertExists()
        rule.onNodeWithText(configurationId).assertDoesNotExist()
    }

    @Test
    fun conUnaAproximacionLaMovilidadQuedaComoPildoraDebajo() {
        val withWarmup = bare.copy(
            warmupSets = listOf(WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 0.5, targetReps = 8, restBetween = 60)),
        )
        render(withWarmup)
        rule.onNodeWithText("APROXIMACIÓN · 1").assertExists()
        rule.onNodeWithText("+ Movilidad").assertExists()
        rule.onAllNodesWithText("+ Aproximación").fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aproximacionesMuestranCadaSerieConSuDescansoEnSuFila() {
        val three = bare.copy(
            warmupSets = (1..3).map { index ->
                WarmupSetDefinition(id = "w$index", percentageOfWorkingWeight = 0.5, targetReps = 8, restBetween = 60)
            },
        )
        val updates = mutableListOf<Exercise>()
        render(three, onUpdate = { transform -> updates += transform(three) })
        rule.onNodeWithText("APROXIMACIÓN · 3").assertExists()
        rule.onNodeWithTag("warmup-common-rest").assertTextContains("1:00")
        // H1: tres fichas; cada selección expone el descanso real de esa aproximación en su foco.
        (1..3).forEach { index ->
            rule.onNodeWithTag("warmup-chip-w$index").performClick().assertIsSelected()
            rule.onNode(hasText("Aproximación $index") and hasAnyAncestor(hasTestTag("warmup-focus"))).assertExists()
            rule.onNode(hasText("Descanso · 1:00") and hasAnyAncestor(hasTestTag("warmup-focus"))).assertExists()
        }
        // El descanso común conserva el selector y aplica sus 60 segundos a las tres aproximaciones.
        rule.onNodeWithTag("warmup-common-rest").performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("Descanso común de aproximaciones").assertIsDisplayed()
        rule.onNodeWithText("Aceptar").performClick()
        assertEquals(3, updates.size)
        assertEquals(listOf(60, 60, 60), updates.last().warmupSets.map { it.restBetween })
        updates.last().warmupSets.forEach { warmup ->
            assertEquals(0.5, warmup.percentageOfWorkingWeight, 0.0001)
            assertEquals(8, warmup.targetReps)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun movilidadMuestraSuBloqueConSuTiempoGlobalYSuAgregarOtra() {
        val mobility = bare.copy(
            mobilitySeries = listOf(MobilitySeries(id = "m1", name = "Gato-vaca")),
            mobilityConfig = MobilityConfig(totalMinutes = 8),
        )
        var saved: Exercise? = null
        var opened = false
        render(mobility, onUpdate = { transform -> saved = transform(mobility) }, onOpenMobility = { opened = true })
        rule.onNodeWithText("MOVILIDAD · 1").assertExists()
        rule.onNodeWithTag("mobility-chip-m1").assertTextContains("Gato-vaca")
        rule.onNodeWithText("Total · 8 min").assertExists()
        rule.onNodeWithTag("mobility-global-time").performClick()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("Tiempo de movilidad").assertIsDisplayed()
        rule.onNodeWithContentDescription("Usar teclado").performClick()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        rule.onAllNodes(hasSetTextAction())[1].performTextReplacement("9")
        rule.onNodeWithText("Aceptar").performClick()
        assertEquals(9, saved!!.mobilityConfig!!.totalMinutes)
        assertEquals(mobility.mobilityConfig!!.mode, saved!!.mobilityConfig!!.mode)
        assertEquals(mobility.mobilitySeries, saved!!.mobilitySeries)
        rule.onNodeWithText("Total · 9 min").assertExists()
        rule.onNodeWithTag("mobility-chip-add").performClick()
        assertTrue(opened)
    }
}
