package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.domain.training.ApreVariant
import com.example.kpkn.screens.sessioneditor.RestSheet
import com.example.kpkn.screens.sessioneditor.withTrainingMode
import com.example.kpkn.screens.sessioneditor.normalizeExercise
import com.example.kpkn.screens.sessioneditor.normalizedForEditorSave
import com.example.kpkn.screens.sessioneditor.withExplicitRmLoads
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-mdpi")
class EdbFocusControlsTest {
    @get:Rule val rule = createComposeRule()
    private val state = mutableStateOf(Exercise(id = "press", name = "Press"))
    private val fontScale = mutableStateOf(1f)

    private fun mount(exercise: Exercise, side: String? = null) {
        state.value = exercise
        rule.setContent { EditorOverlayTestHost {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                val current = state.value
                ExerciseSetList(exercise = current, reference1RM = current.reference1RM, trainingMode = current.trainingMode,
                    customUnit = current.customUnit, predictedMetrics = emptyMap(), accentColor = Color(0xFFF49A6E),
                    backgroundAccentColor = Color(0xFFF49A6E), onAddSet = {}, onRemoveSet = {}, onMoveSet = { _, _ -> },
                    onUpdateSet = { id, transform ->
                        val before = state.value
                        state.value = before.copy(sets = before.sets.map { if (it.id == id) transform(it) else it })
                            .withExplicitRmLoads(before).normalizeExercise()
                    }, onTransformExercise = { transform ->
                        val before = state.value
                        state.value = transform(before).withExplicitRmLoads(before).normalizeExercise()
                    })
            }
        } }
        rule.onNodeWithTag(if (side == null) "set-row-s1" else "set-row-s1:$side").performClick()
    }

    private fun edit(label: String, value: String) {
        rule.onNodeWithContentDescription(label).performClick()
        rule.onNode(hasSetTextAction() and isFocused()).performTextReplacement(value)
        rule.onNode(hasSetTextAction() and isFocused()).performImeAction()
        rule.waitForIdle()
    }

    private fun assertRmLabelIsComplete(label: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag("set-focus-s1")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val lines = 0 until layout.lineCount
        val detail = "${layout.size}, líneas=${lines.map { layout.getLineLeft(it) to layout.getLineRight(it) }}, ${layout.layoutInput.constraints}"
        assertEquals("Texto completo del rótulo", label, layout.layoutInput.text.text)
        assertFalse("$label se desborda en alto: $detail", layout.didOverflowHeight)
        assertTrue("$label supera dos líneas: $detail", layout.lineCount <= 2)
        assertTrue("$label sigue elidido: $detail", lines.all { !layout.isLineEllipsized(it) })
        assertEquals("Todos los caracteres quedan visibles: $detail", label.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        assertTrue("$label se recorta en horizontal: $detail", lines.all {
            layout.getLineLeft(it) >= -1f && layout.getLineRight(it) <= layout.size.width + 1f
        })
        rule.onNodeWithContentDescription(label).assertHeightIsEqualTo(48.dp)
    }

    private fun rm(basis: LoadBasis = LoadBasis.PERCENT_1RM, oneRm: Double? = 120.0, tm: Double? = null) = Exercise(
        id = "press", name = "Press", trainingMode = TrainingMode.RM, reference1RM = oneRm, referenceTrainingMax = tm,
        sets = listOf(ExerciseSet(id = "s1", targetPercentageRM = 80.0, targetRepsRange = RepRange(4, 6), targetRIR = 2,
            weight = 95.0, loadBasis = basis)))

    @Test fun rmMuestraElLadoElegidoYConservaElPorcentajeComunAlEditar() {
        val base = rm()
        val source = base.copy(isUnilateral = true, unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
            unilateralIntensityMode = UnilateralIntensityMode.INDEPENDENT, sets = listOf(base.sets.single().copy(
                leftTarget = UnilateralTarget(weight = 70.0, targetReps = 6, targetRIR = 2),
                rightTarget = UnilateralTarget(weight = 80.0, targetReps = 5, targetRIR = 3))))
        mount(source, side = "R")
        rule.onNodeWithContentDescription("Carga del lado R").assertTextContains("R · 80\u00a0kg")
        edit("%RM", "75")
        val saved = state.value.sets.single()
        assertEquals(90.0, saved.leftTarget!!.weight!!, 0.001)
        assertEquals(90.0, saved.rightTarget!!.weight!!, 0.001)
        assertEquals(2, saved.leftTarget!!.targetRIR)
        assertEquals(3, saved.rightTarget!!.targetRIR)
    }

    @Test fun rmCaptionHeredaCargaGlobalYRespetaElPesoExplicitoDeCadaLado() {
        val base = rm(oneRm = 100.0)
        val source = base.copy(isUnilateral = true, unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
            unilateralIntensityMode = UnilateralIntensityMode.INDEPENDENT, sets = listOf(base.sets.single().copy(
                weight = 80.0,
                leftTarget = UnilateralTarget(targetReps = 10),
                rightTarget = UnilateralTarget(targetReps = 12),
            )))

        mount(source, side = "L")
        rule.onNodeWithContentDescription("Carga del lado L").assertTextContains("L · 80\u00a0kg")
        rule.onNodeWithContentDescription("Peso (kg)").assertTextContains("80")
        rule.onNodeWithContentDescription("Reps estimadas").assertTextContains("7")

        rule.onNodeWithTag("set-row-s1:R").performClick()
        rule.onNodeWithContentDescription("Carga del lado R").assertTextContains("R · 80\u00a0kg")
        rule.onNodeWithContentDescription("Peso (kg)").assertTextContains("80")

        state.value = state.value.copy(sets = state.value.sets.map { set ->
            set.copy(rightTarget = set.rightTarget!!.copy(weight = 90.0))
        })
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Carga del lado R").assertTextContains("R · 90\u00a0kg")
        rule.onNodeWithContentDescription("Peso (kg)").assertTextContains("80")

        state.value = state.value.copy(sets = state.value.sets.map { set ->
            set.copy(rightTarget = set.rightTarget!!.copy(weight = 0.0))
        })
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Carga del lado R").assertTextContains("R · 0\u00a0kg")
        assertEquals(80.0, state.value.sets.single().weight!!, 0.0)
        assertEquals(0.0, state.value.sets.single().rightTarget!!.weight!!, 0.0)
    }

    @Test fun amrapMuestraCargaYVistaPreviaDelMismoLado() {
        val source = Exercise(id = "press", name = "Press", trainingMode = TrainingMode.AMRAP,
            isUnilateral = true, unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
            unilateralIntensityMode = UnilateralIntensityMode.INDEPENDENT, sets = listOf(
                ExerciseSet(id = "s1", purpose = SetPurpose.CALIBRATOR, calibrationScope = CalibrationScope.SESSION,
                    amrapMinimumReps = 8, weight = 60.0, leftTarget = UnilateralTarget(weight = 30.0), rightTarget = UnilateralTarget(weight = 40.0)),
                ExerciseSet(id = "s2", targetReps = 8, weight = 60.0,
                    leftTarget = UnilateralTarget(weight = 35.0), rightTarget = UnilateralTarget(weight = 45.0))))
        mount(source, side = "R")
        rule.onNode(hasText("40\u00a0kg") and hasAnyAncestor(hasTestTag("set-focus-s1:R"))).assertExists()
        rule.onNodeWithText("Ajustará S2 · hoy 45\u00a0kg").assertExists()
        rule.onNodeWithContentDescription("Aumentar mínimo AMRAP").performClick()
        val saved = state.value.sets.first()
        assertEquals(9, saved.amrapMinimumReps)
        assertEquals(source.sets.first().leftTarget, saved.leftTarget)
        assertEquals(source.sets.first().rightTarget, saved.rightTarget)
    }

    @Test fun elBloqueApreUnilateralConservaCuatroParesYElMarcador() {
        val source = rm(oneRm = 200.0).copy(isUnilateral = true, unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
            unilateralIntensityMode = UnilateralIntensityMode.INDEPENDENT)
        var sequence = 0
        val prescribed = source.withApreBlock(ApreVariant.APRE_6) { "apre-${++sequence}" }.normalizeExercise()
        val restored = Json.decodeFromString<Exercise>(Json.encodeToString(prescribed))
        assertEquals(4, restored.sets.size)
        assertEquals(UnilateralIntensityMode.INDEPENDENT, restored.unilateralIntensityMode)
        assertEquals(restored.sets.map { it.id }, restored.apreBlock!!.setIds)
        restored.sets.forEach { set ->
            assertNotNull(set.leftTarget)
            assertNotNull(set.rightTarget)
            assertEquals(set.weight, set.leftTarget!!.weight)
            assertEquals(set.leftTarget, set.rightTarget)
        }
        assertEquals(SetPurpose.AMRAP, restored.sets[2].purpose)
        assertEquals(6, restored.sets[2].amrapMinimumReps)
    }

    @Test fun unaCargaProgramadaNoInventaUnRpeEnElFocoDeTiempo() {
        mount(Exercise(id = "press", name = "Plancha", trainingMode = TrainingMode.TIME,
            sets = listOf(ExerciseSet(id = "s1", targetDuration = 60, weight = 10.0))))
        rule.onNodeWithText("+ Intensidad").assertExists()
        rule.onNodeWithContentDescription("RPE").assertDoesNotExist()
        rule.onNodeWithText("+ Intensidad").performClick()
        rule.waitForIdle()
        assertEquals(8.0, state.value.sets.single().targetRPE!!, 0.001)
    }

    @Test fun laHojaDeDescansoConfirmaLasSeriesYLosLadosJuntos() {
        var confirmed: Pair<Int, Int?>? = null
        rule.setContent { EditorOverlayTestHost {
            RestSheet("Entre series", 120, 15, Color(0xFFF49A6E), {}, { primary, side -> confirmed = primary to side })
        } }
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Sumar 15 segundos a Entre series").performClick()
        rule.onNodeWithContentDescription("Sumar 15 segundos a Entre lados").performClick()
        assertNull(confirmed)
        rule.onNodeWithText("Listo").performClick()
        rule.waitForIdle()
        assertEquals(135 to 30, confirmed)
    }

    @Test fun cambiarDeFalloARmNoDejaTecnicasOcultasActivas() {
        val exercise = Exercise(id = "press", name = "Press", sets = listOf(ExerciseSet(id = "s1", isFailure = true,
            intensityMode = IntensityMode.FAILURE, isDropSet = true, restAfterSeconds = 75)))
        val changed = exercise.withTrainingMode(TrainingMode.RM).sets.single()
        assertFalse(changed.isFailure)
        assertFalse(changed.isDropSet)
        assertEquals(75, changed.restAfterSeconds)
    }

    @Test fun laFichaDeLaSerieRealMide85Por76Dp() {
        mount(rm())
        rule.onNodeWithTag("set-row-s1").assertWidthIsEqualTo(85.dp).assertHeightIsEqualTo(76.dp)
    }

    @Test fun salirDelFalloQuitaLasTecnicasYConservaElDescanso() {
        mount(Exercise(id = "press", name = "Press", sets = listOf(ExerciseSet(id = "s1", targetReps = 8,
            isFailure = true, intensityMode = IntensityMode.FAILURE, restAfterSeconds = 60))))
        rule.onNodeWithText("Drop-set").performClick()
        rule.waitForIdle()
        assertEquals(1, state.value.sets.single().plannedIntensityTechniques.size)
        assertEquals(60, state.value.sets.single().restAfterSeconds)
        rule.onNodeWithText("RIR").performClick()
        rule.waitForIdle()
        assertFalse(state.value.sets.single().isFailure)
        assertTrue(state.value.sets.single().plannedIntensityTechniques.isEmpty())
        assertEquals(60, state.value.sets.single().restAfterSeconds)
        rule.onNodeWithText("Drop-set").assertDoesNotExist()
    }

    @Test fun minutosYSegundosGuardanUnaDuracionUnica() {
        mount(Exercise(id = "press", name = "Plancha", trainingMode = TrainingMode.TIME,
            sets = listOf(ExerciseSet(id = "s1", targetDuration = 75))))
        edit("Minutos", "2")
        assertEquals(135, state.value.sets.single().targetDuration)
        edit("Segundos", "45")
        assertEquals(165, state.value.sets.single().targetDuration)
    }

    @Test fun distanciaIncrementaCincoMetrosSinCambiarLaUnidad() {
        mount(Exercise(id = "press", name = "Paseo", trainingMode = TrainingMode.DISTANCE,
            sets = listOf(ExerciseSet(id = "s1", plannedTargetV2 = 100.0))))
        rule.onNodeWithContentDescription("Aumentar Distancia (m)").performClick()
        rule.waitForIdle()
        assertEquals(105.0, state.value.sets.single().plannedTargetV2!!, 0.001)
    }

    @Test fun rmEnlazaPorcentajeRepsYPesoSinExponerRir() {
        mount(rm())
        rule.onNodeWithText("RIR").assertDoesNotExist()
        edit("%RM", "85")
        assertEquals(102.5, state.value.sets.single().weight!!, 0.001)
        rule.onNodeWithContentDescription("Reps estimadas").assertTextContains("5")
        edit("Reps estimadas", "6")
        assertEquals(83.0, state.value.sets.single().targetPercentageRM!!, 0.001)
        edit("Peso (kg)", "90")
        assertEquals(75.0, state.value.sets.single().targetPercentageRM!!, 0.001)
        rule.onNodeWithContentDescription("Reps estimadas").assertTextContains("10")
        // La edición de porcentaje no borra la prescripción híbrida que venía en la receta.
        assertEquals(RepRange(4, 6), state.value.sets.single().targetRepsRange)
        assertEquals(2, state.value.sets.single().targetRIR)
    }

    @Test fun porcentajeTmUsaTmYLasRepsEstimadasUsanElUnoRm() {
        mount(rm(LoadBasis.PERCENT_TM, oneRm = 125.0, tm = 100.0))
        edit("Peso (kg)", "90")
        assertEquals(90.0, state.value.sets.single().targetPercentageRM!!, 0.001)
        assertEquals(90.0, state.value.sets.single().weight!!, 0.001)
        rule.onNodeWithContentDescription("Reps estimadas").assertTextContains("12")
        edit("% del TM", "80")
        assertEquals(80.0, state.value.sets.single().weight!!, 0.001)
        edit("% del TM", "40")
        assertEquals(40.0, state.value.sets.single().weight!!, 0.001)
    }

    @Test fun etiquetasRmSeVenCompletasEnFocoNormalAl100Y130() {
        mount(rm())
        for (scale in listOf(1f, 1.3f)) {
            rule.runOnIdle { fontScale.value = scale }
            rule.waitForIdle()
            listOf("Reps estimadas", "%RM", "Peso (kg)").forEach(::assertRmLabelIsComplete)
        }
    }

    @Test fun noSustituyeUnTmAusentePorElUnoRm() {
        mount(rm(LoadBasis.PERCENT_TM, oneRm = 125.0, tm = null))
        rule.onNodeWithText("Falta el TM").assertExists()
    }

    @Test fun noSustituyeElUnoRmAusentePorElTm() {
        mount(rm(LoadBasis.PERCENT_1RM, oneRm = null, tm = 100.0))
        rule.onNodeWithText("Falta el 1RM").assertExists()
    }

    @Test fun amrapConservaMinimoNuloYCalibradorAlGuardarUnaRecetaRm() {
        val source = Exercise(id = "press", name = "Press", trainingMode = TrainingMode.RM, reference1RM = 80.0, sets = listOf(
            ExerciseSet(id = "s1", weight = 60.0, targetPercentageRM = 75.0, purpose = SetPurpose.AMRAP, amrapMinimumReps = 8),
            ExerciseSet(id = "s2", weight = 60.0, targetPercentageRM = 75.0), ExerciseSet(id = "s3", weight = 60.0, targetPercentageRM = 75.0)))
        mount(source)
        rule.onNodeWithText("Calibrador").performClick()
        rule.onNodeWithText("Ajustará S2–S3 · hoy 60\u00a0kg").assertExists()
        rule.onNodeWithText("Sin mínimo").performClick()
        rule.onNodeWithText("Próximas sesiones").performClick()
        rule.waitForIdle()
        val saved = Session(id = "session", name = "Sesión", exercises = listOf(state.value)).normalizedForEditorSave()
        val set = saved.exercises.single().sets.first()
        assertEquals(SetPurpose.CALIBRATOR, set.purpose)
        assertNull(set.amrapMinimumReps)
        assertEquals(CalibrationScope.NEXT_SESSIONS, set.calibrationScope)
    }

    @Test fun elModoSeAplicaSoloAlConfirmar() {
        var picked: TrainingMode? = null
        rule.setContent { EditorOverlayTestHost {
            ExerciseModeSheet(current = TrainingMode.REPS, onSelect = { picked = it }, onDismiss = {})
        } }
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Tiempo").performClick()
        rule.waitForIdle()
        assertNull(picked)
        rule.onNodeWithText("Aplicar Tiempo").performClick()
        rule.waitForIdle()
        assertEquals(TrainingMode.TIME, picked)
    }

    @Test fun bloqueApreConservaTablaPropositoYMarcadorTrasSerializar() {
        val expected = mapOf(ApreVariant.APRE_3 to listOf(92.5, 140.0, 185.0, 185.0),
            ApreVariant.APRE_6 to listOf(82.5, 125.0, 165.0, 165.0), ApreVariant.APRE_10 to listOf(75.0, 112.5, 150.0, 150.0))
        expected.forEach { (variant, loads) ->
            var sequence = 0
            val exercise = Exercise(id = "press", name = "Press", reference1RM = 200.0)
                .withApreBlock(variant) { "apre-${sequence++}" }.normalizeExercise()
            val restored = Json.decodeFromString<Exercise>(Json.encodeToString(exercise))
            assertEquals(loads, restored.sets.map { it.weight })
            assertEquals(SetPurpose.AMRAP, restored.sets[2].purpose)
            assertEquals(variant.xrmReps, restored.sets[2].amrapMinimumReps)
            assertNull(restored.sets[2].calibrationScope)
            assertEquals(ApreBlockReference(variant.xrmReps, restored.sets.map { it.id }), restored.apreBlock)
        }
    }
}
