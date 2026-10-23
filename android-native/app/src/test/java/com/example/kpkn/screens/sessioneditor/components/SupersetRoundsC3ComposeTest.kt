package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.domain.workout.SupersetRules
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ED-D · C3: rondas y descansos de la superserie. El panel muestra el «descanso base» de las rondas sin el suyo, cada ronda avisa
 * cuando tiene descanso propio y puede volver al base sin reescribir los valores.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h900dp-xxhdpi")
class SupersetRoundsC3ComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private val zancada = Exercise(
        id = "zan",
        name = "Zancada con mancuernas",
        trainingMode = TrainingMode.REPS,
        supersetGroupRef = "ss",
        sets = (1..3).map { ExerciseSet(id = "zan-$it", targetReps = 10, targetRepsRange = RepRange(8, 10), targetRIR = 2) },
    )
    private val curl = Exercise(
        id = "curl",
        name = "Curl femoral tumbado",
        trainingMode = TrainingMode.REPS,
        supersetGroupRef = "ss",
        sets = (1..3).map { ExerciseSet(id = "curl-$it", targetReps = 12, targetRepsRange = RepRange(10, 12), targetRIR = 1) },
    )
    private val members = listOf(zancada, curl)

    @Composable
    private fun Card(
        group: SupersetGroup,
        onClearRoundRest: (String, Int) -> Unit = { _, _ -> },
        onUpdateSupersetRest: (String, Int?, Int?, Int?) -> Unit = { _, _, _, _ -> },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            SupersetGroupEditorCard(
                group = group,
                exercises = members,
                accentHex = null,
                partId = "acc",
                onOpenSupersetCreator = { _, _ -> },
                onUpdateSupersetRest = onUpdateSupersetRest,
                onRemoveFromSuperset = { _, _ -> },
                onDissolve = {},
                onAddRound = {},
                onClearRoundRest = onClearRoundRest,
            ) {
                members.forEach { member -> Text(member.name) }
            }
        }
    }

    private fun baseGroup() = SupersetGroup(
        id = "ss",
        exerciseOrder = listOf("zan", "curl"),
        rounds = 3,
        restBetweenExercises = 60,
        restAfterSuperset = 120,
    )

    private fun groupWithOwnRestInRoundTwo() = baseGroup().copy(
        roundRestBetweenExercises = mapOf(1 to 45),
        roundRestAfterSuperset = mapOf(1 to 150),
    )

    private fun sessionWith(group: SupersetGroup) = Session(
        id = "s1",
        name = "Pierna A",
        exercises = members,
        supersetGroups = listOf(group),
    )

    // ───────────────────────────── la vista ─────────────────────────────

    @Test
    fun elPanelDeRondasEnseñaElDescansoBase() {
        rule.setContent { Card(baseGroup()) }

        rule.onNodeWithText("3 rondas").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("DESCANSO BASE").assertIsDisplayed()
        rule.onNodeWithText("Para las rondas sin el suyo").assertIsDisplayed()
        rule.onNodeWithText("Rondas").assertIsDisplayed()
        rule.onNodeWithText("Entre ejercicios").assertIsDisplayed()
        rule.onNodeWithText("Tras la ronda").assertIsDisplayed()
        rule.onNodeWithText("POR RONDA · TOCA UNA PARA ABRIRLA").assertExists()
    }

    @Test
    fun losPasosDelDescansoBaseEscribenLosValoresReales() {
        val calls = mutableListOf<Triple<Int?, Int?, Int?>>()
        rule.setContent {
            Card(
                baseGroup(),
                onUpdateSupersetRest = { _, between, after, rounds ->
                    calls += Triple(between, after, rounds)
                },
            )
        }

        rule.onNodeWithText("3 rondas").performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Añadir 15 segundos a Entre ejercicios").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Quitar 15 segundos a Tras la ronda").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Añadir una ronda").performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(Triple(75, null, null), calls[0])
        assertEquals(Triple(null, 105, null), calls[1])
        assertEquals(Triple(null, null, 4), calls[2])
    }

    @Test
    fun unaRondaConDescansoPropioLoDiceYPuedeVolverAlBase() {
        var cleared by mutableStateOf<List<Int>>(emptyList())
        rule.setContent {
            Card(groupWithOwnRestInRoundTwo(), onClearRoundRest = { _, index -> cleared = cleared + index })
        }

        rule.onNodeWithText("3 rondas").performClick()
        rule.waitForIdle()

        // Solo la segunda ronda se aparta del base.
        rule.onNodeWithText("Descanso propio").assertExists()
        rule.onNodeWithText("Ronda 2").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Usar el base").performScrollTo()
        rule.waitForIdle()
        rule.onNodeWithText("Usar el base").performClick()
        rule.waitForIdle()

        assertEquals(listOf(1), cleared)
    }

    @Test
    fun unDescansoExplicitoIgualAlBaseTambienPuedeVolverAHerencia() {
        var cleared: Int? = null
        val group = baseGroup().copy(roundRestBetweenExercises = mapOf(1 to 60), roundRestAfterSuperset = mapOf(1 to 120))
        rule.setContent { Card(group, onClearRoundRest = { _, index -> cleared = index }) }
        rule.onNodeWithText("3 rondas").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Descanso propio").assertExists()
        rule.onNodeWithText("Ronda 2").performScrollTo().performClick()
        rule.onNodeWithText("Usar el base").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1, cleared)
    }

    // ───────────────────────────── el motor ─────────────────────────────

    @Test
    fun usarElBaseQuitaElDescansoPropioDeEsaRonda() {
        val cleared = SupersetRules.clearRoundRest(sessionWith(groupWithOwnRestInRoundTwo()), "ss", 1)
        val group = cleared.allSupersetGroups().first { it.id == "ss" }

        assertEquals(emptyMap<Int, Int>(), group.roundRestBetweenExercises)
        assertEquals(emptyMap<Int, Int>(), group.roundRestAfterSuperset)
    }

    @Test
    fun trasUsarElBaseUnCambioDelBaseLlegaALaRonda() {
        val cleared = SupersetRules.clearRoundRest(sessionWith(groupWithOwnRestInRoundTwo()), "ss", 1)
        val updated = SupersetRules.updateRest(cleared, "ss", restBetweenExercises = 90, restAfterSuperset = 150)
        val group = updated.allSupersetGroups().first { it.id == "ss" }

        assertEquals(90, group.roundRestBetweenExercises[1] ?: group.restBetweenExercises)
        assertEquals(150, group.roundRestAfterSuperset[1] ?: group.restAfterSuperset)
        assertEquals(emptyMap<Int, Int>(), group.roundRestBetweenExercises)
        assertEquals(emptyMap<Int, Int>(), group.roundRestAfterSuperset)
    }

    @Test
    fun lasRondasQueNoTocanElBaseNoSeMueven() {
        val cleared = SupersetRules.clearRoundRest(sessionWith(groupWithOwnRestInRoundTwo()), "ss", 1)
        val group = cleared.allSupersetGroups().first { it.id == "ss" }

        assertEquals(60, group.restBetweenExercises)
        assertEquals(120, group.restAfterSuperset)
    }
}
