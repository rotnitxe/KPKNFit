package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.RecordSetResult
import com.example.kpkn.screens.workout.WeightSuggestion
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * El esfuerzo en un ejercicio unilateral (LV8, A2): cada lado se registra con SU esfuerzo. Al registrar el primer lado, el segundo empieza
 * sin marcar (una intensidad que la persona no eligió nunca se registra) y el ✓ no lo registra hasta que se marca.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class SetExecutionCardUnilateralEffortTest {

    @get:Rule
    val rule = createComposeRule()

    private data class Registro(val side: String?, val intensity: Double?)

    private val registros = mutableListOf<Registro>()
    private val holder = RecordActionHolder()

    private val serie = ExerciseSet(
        id = "uni-set",
        targetReps = 10,
        targetRPE = 8.0,
        weight = 20.0,
        loadModeV2 = LoadModeV2.LOAD,
        unitModeV2 = UnitModeV2.REPS,
        intensityMode = IntensityMode.RPE,
        leftTarget = UnilateralTarget(weight = 20.0, targetReps = 10, targetRPE = 8.0),
        rightTarget = UnilateralTarget(weight = 20.0, targetReps = 10, targetRPE = 8.0),
    )

    private val ejercicio = Exercise(
        id = "uni-ex",
        name = "Zancada búlgara",
        isUnilateral = true,
        trainingMode = TrainingMode.REPS,
        sets = listOf(serie),
    )

    private fun mostrar() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.width(360.dp).background(Color.Black), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.width(272.dp).testTag("lv8-uni")) {
                                SetInputCardV2(
                                    exercise = ejercicio,
                                    setIndex = 0,
                                    currentSet = serie,
                                    ghostSet = null,
                                    weightSuggestion = WeightSuggestion(suggestedWeight = 20.0, reason = "Ajuste por RPE"),
                                    previousSessionFirstSetWeight = 20.0,
                                    sessionAccentColor = Color(0xFFF49A6E),
                                    initialBodyWeight = 80.0,
                                    recordActionHolder = holder,
                                    recordActionPageKey = PAGINA,
                                    onShowHistory = {},
                                    onSetBodyWeight = {},
                                    onRecordV2 = { _, _, _, _, intensity, _, _, _, side, onResult ->
                                        registros += Registro(side, intensity)
                                        onResult(RecordSetResult.Created("uni-ex_0_${side.orEmpty()}"))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun pulsarCheck() {
        rule.runOnIdle { holder.actionForPage(PAGINA)?.invoke() }
        rule.waitForIdle()
    }

    private fun marcar(indice: Int) {
        rule.onNodeWithTag(EffortSliderTag).performSemanticsAction(SemanticsActions.SetProgress) { it(indice.toFloat()) }
        rule.waitForIdle()
    }

    private fun descripcionDelSlider(): String =
        rule.onNodeWithTag(EffortSliderTag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()

    @Test
    fun alRegistrarUnLadoElOtroEmpiezaSinMarcarYNoSeRegistraSinEsfuerzo() {
        mostrar()
        marcar(16)
        val primero = descripcionDelSlider()
        assertTrue("el primer lado queda marcado: $primero", primero.startsWith("Esfuerzo RPE"))

        pulsarCheck()

        assertEquals("se registró el primer lado", 1, registros.size)
        assertEquals("el otro lado empieza sin marcar", "Esfuerzo sin marcar", descripcionDelSlider())

        pulsarCheck()

        assertEquals("el segundo lado no se registra sin esfuerzo", 1, registros.size)
        // Refinamiento 02: el aviso sale en la fila de estado de la tarjeta (live-card-inline-error) con su texto nuevo.
        rule.onNodeWithText("Marca tu esfuerzo antes de registrar.").assertExists()

        marcar(14)
        pulsarCheck()

        assertEquals("con su esfuerzo, el segundo lado sí", 2, registros.size)
        assertTrue("cada lado con su esfuerzo: ${registros.map { it.intensity }}", registros[0].intensity != registros[1].intensity)
        assertEquals(setOf("left", "right"), registros.mapNotNull { it.side }.toSet())
    }

    private companion object {
        const val PAGINA = "lv8-unilateral"
    }
}
