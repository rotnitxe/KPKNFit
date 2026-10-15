package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.WarmupSetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
                resolved1RM = 100.0,
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
                onOpenMobility = onOpenMobility,
                onOpenSuperset = {},
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onUpdateRelationshipType = {},
                onUpdateRelationshipNotes = {},
            )
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
    fun aproximacionesMuestranCadaSerieConSuDescansoEnSuFila() {
        val three = bare.copy(
            warmupSets = (1..3).map { index ->
                WarmupSetDefinition(id = "w$index", percentageOfWorkingWeight = 0.5, targetReps = 8, restBetween = 60)
            },
        )
        render(three)
        rule.onNodeWithText("APROXIMACIÓN · 3").assertExists()
        assertEquals(3, rule.onAllNodesWithText("Descanso entre series 1:00").fetchSemanticsNodes().size)
    }

    @Test
    fun movilidadMuestraSuBloqueConSuTiempoGlobalYSuAgregarOtra() {
        val mobility = bare.copy(mobilitySeries = listOf(MobilitySeries(id = "m1", name = "Gato-vaca")))
        render(mobility)
        rule.onNodeWithText("MOVILIDAD · 1").assertExists()
        rule.onNodeWithText("Tiempo global de movilidad").assertExists()
        rule.onNodeWithText("+ Agregar otra").assertExists()
        rule.onNodeWithText("Gato-vaca").assertExists()
    }
}
