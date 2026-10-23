package com.example.kpkn.screens.sessioneditor.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.screens.sessioneditor.normalizeExercise
import com.example.kpkn.screens.sessioneditor.rmSetLoadKg
import com.example.kpkn.screens.sessioneditor.withExplicitRmLoads
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Integración real C3 -> focos ED-B, con clicks nativos y el recálculo usado por el ViewModel. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-mdpi")
class SupersetRoundsFocusIntegrationTest {
    @get:Rule val rule = createComposeRule()
    private val state = mutableStateOf(Exercise(id = "press", name = "Press C3"))
    private val fontScale = mutableStateOf(1f)
    private var hostView: View? = null
    private val moved = mutableListOf<Pair<String, Int>>()
    private val removed = mutableListOf<String>()

    private fun rm(tm: Double? = null, basis: LoadBasis = LoadBasis.PERCENT_1RM) = Exercise(
        id = "press", name = "Press C3", supersetGroupRef = "ss", trainingMode = TrainingMode.RM,
        reference1RM = 120.0, referenceTrainingMax = tm,
        sets = listOf(
            ExerciseSet(id = "s1", targetPercentageRM = 80.0, weight = 95.0,
                targetRepsRange = RepRange(4, 6), targetRIR = 2, loadBasis = basis),
            ExerciseSet(id = "s2", targetPercentageRM = 75.0, weight = 90.0,
                targetRepsRange = RepRange(6, 8), targetRIR = 3, loadBasis = basis),
        ),
    )

    private fun lateral(source: Exercise) = source.copy(
        isUnilateral = true, unilateralMode = UnilateralMode.UNILATERAL_PAIRED,
        unilateralIntensityMode = UnilateralIntensityMode.INDEPENDENT,
        unilateralSideOrder = UnilateralSideOrder.RIGHT_LEFT,
        sets = source.sets.mapIndexed { index, set -> set.copy(
            leftTarget = UnilateralTarget(weight = 30.0 + index * 5, targetRepsRange = RepRange(8, 10), targetRIR = 2),
            rightTarget = UnilateralTarget(weight = 40.0 + index * 5, targetRepsRange = RepRange(10, 12), targetRIR = 3),
        ) },
    )

    private fun amrap() = lateral(rm()).let { exercise -> exercise.copy(sets = exercise.sets.mapIndexed { index, set ->
        if (index == 0) set.copy(purpose = SetPurpose.CALIBRATOR, calibrationScope = CalibrationScope.SESSION,
            amrapMinimumReps = 5) else set
    }) }

    private fun update(transform: (Exercise) -> Exercise) {
        val previous = state.value
        state.value = transform(previous).withExplicitRmLoads(previous).normalizeExercise()
    }

    private fun mount(exercise: Exercise, round: Int = 1) {
        state.value = exercise
        rule.setContent { EditorOverlayTestHost {
            hostView = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                Column(Modifier.fillMaxWidth().background(Color.Black).verticalScroll(rememberScrollState())) {
                    val current = state.value
                    SupersetGroupEditorCard(
                        group = SupersetGroup(id = "ss", exerciseOrder = listOf(current.id), rounds = 2,
                            restBetweenExercises = 60, restAfterSuperset = 120),
                        exercises = listOf(current), accentHex = null, partId = "p",
                        onOpenSupersetCreator = { _, _ -> }, onUpdateSupersetRest = { _, _, _, _ -> },
                        onRemoveFromSuperset = { _, _ -> }, onDissolve = {}, onAddRound = {},
                        onUpdateExercise = { id, transform -> assertEquals(current.id, id); update(transform) },
                        onUpdateSet = { id, setId, transform ->
                            assertEquals(current.id, id)
                            update { e -> e.copy(sets = e.sets.map { if (it.id == setId) transform(it) else it }) }
                        },
                        onRemoveSet = { _, setId -> removed += setId },
                        onMoveSet = { _, setId, delta -> moved += setId to delta },
                    ) {}
                }
            }
        } }
        rule.onNodeWithText("2 rondas").performClick()
        rule.onNodeWithText("Ronda $round").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Ampliar serie").performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun within(side: String? = null, set: String = "s1") =
        hasAnyAncestor(hasTestTag("round-set-focus-$set${side?.let { ":$it" }.orEmpty()}"))

    private fun edit(label: String, value: String, side: String? = null) {
        rule.onNode(hasContentDescription(label) and within(side)).performScrollTo().performClick()
        rule.onNode(hasSetTextAction() and isFocused()).performTextReplacement(value)
        rule.onNode(hasSetTextAction() and isFocused()).performImeAction()
        rule.waitForIdle()
    }

    @Test fun rmTieneTresCifrasEnlazadasConservaRangosYRirYAcciones() {
        mount(rm())
        rule.onNodeWithContentDescription("Reps est.").assertExists()
        edit("%RM", "75")
        val saved = state.value.sets.first()
        assertEquals(90.0, saved.weight!!, 0.001)
        assertEquals(RepRange(4, 6), saved.targetRepsRange)
        assertEquals(2, saved.targetRIR)
        assertNull(saved.restAfterSeconds)
        rule.onNode(hasText("Bajar") and within()).performScrollTo().performClick()
        rule.onNode(hasText("Eliminar") and within()).performScrollTo().performClick()
        assertEquals(listOf("s1" to 1), moved)
        assertEquals(listOf("s1"), removed)
        rule.onNode(hasText("Subir") and within()).assertDoesNotExist()
    }

    @Test fun referenciaTmSePropagaDesdeElGrupoYRecalculaSoloSuBase() {
        val source = rm(tm = 100.0, basis = LoadBasis.PERCENT_TM)
        mount(source.copy(sets = source.sets.mapIndexed { index, set ->
            if (index == 1) set.copy(loadBasis = LoadBasis.PERCENT_1RM) else set
        }))
        rule.onNode(hasSetTextAction() and within()).performScrollTo().performTextReplacement("125")
        rule.onNode(hasSetTextAction() and within()).performImeAction()
        rule.waitForIdle()
        assertEquals(125.0, state.value.referenceTrainingMax!!, 0.001)
        assertEquals(100.0, state.value.sets.first().weight!!, 0.001)
        assertEquals(90.0, state.value.sets[1].weight!!, 0.001)
        assertEquals(120.0, state.value.reference1RM!!, 0.001)
    }

    @Test fun sinTmSeAvisaAunqueExistaUn1rm() {
        mount(rm(basis = LoadBasis.PERCENT_TM))
        rule.onNodeWithText("Falta el TM").assertExists()
        assertNull(state.value.referenceTrainingMax)
    }

    @Test fun rmLateralRespetaOrdenYCargasYRecalculaAmbosLadosConElPorcentajeComun() {
        mount(lateral(rm()))
        rule.onNodeWithContentDescription("Carga del lado R").assertTextContains("R · 40\u00a0kg")
        rule.onNodeWithContentDescription("Carga del lado L").assertTextContains("L · 30\u00a0kg")
        // Los focos pueden estar fuera del viewport: boundsInRoot recorta ambos al mismo borde.
        // La API ya usada por WeekBleedTest mide la posición real, sin ese recorte.
        val right = rule.onNodeWithTag("round-set-focus-s1:R").getUnclippedBoundsInRoot()
        val left = rule.onNodeWithTag("round-set-focus-s1:L").getUnclippedBoundsInRoot()
        assertTrue("R debe preceder a L: $right / $left", right.top < left.top)
        assertTrue("Los focos laterales no se solapan", right.bottom <= left.top)
        rule.onNodeWithContentDescription("Carga del lado R").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Carga del lado L").performScrollTo().assertIsDisplayed()
        val eitherSide = within("R") or within("L")
        rule.onAllNodes(hasText("Bajar") and eitherSide).assertCountEquals(1)
        rule.onAllNodes(hasText("Eliminar") and eitherSide).assertCountEquals(1)
        rule.onNode(hasText("Eliminar") and within("L")).assertDoesNotExist()
        edit("%RM", "75", side = "R")
        val saved = state.value.sets.first()
        assertEquals(90.0, saved.leftTarget!!.weight!!, 0.001)
        assertEquals(90.0, saved.rightTarget!!.weight!!, 0.001)
        assertEquals(RepRange(8, 10), saved.leftTarget!!.targetRepsRange)
        assertEquals(RepRange(10, 12), saved.rightTarget!!.targetRepsRange)
        assertEquals(2, saved.leftTarget!!.targetRIR)
        assertEquals(3, saved.rightTarget!!.targetRIR)
    }

    @Test fun amrapTienePrioridadSobreRmYPrevisualizaSoloLasCargasDelLado() {
        val source = amrap()
        mount(source)
        rule.onNode(hasText("Lado R") and within("R")).performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("Lado L") and within("L")).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("%RM").assertDoesNotExist()
        rule.onNode(hasText("Ajustará S2 · hoy 45\u00a0kg") and within("R")).assertExists()
        rule.onNode(hasText("Ajustará S2 · hoy 35\u00a0kg") and within("L")).assertExists()
        rule.onNode(hasContentDescription("Aumentar mínimo AMRAP") and within("R")).performScrollTo().performClick()
        assertEquals(6, state.value.sets.first().amrapMinimumReps)
        assertEquals(source.sets.first().leftTarget, state.value.sets.first().leftTarget)
        assertEquals(source.sets.first().rightTarget, state.value.sets.first().rightTarget)
        rule.onNode(hasText("Sin mínimo") and within("R")).performScrollTo().performClick()
        assertNull(state.value.sets.first().amrapMinimumReps)
        rule.onNode(hasText("AMRAP") and within("R")).performScrollTo().performClick()
        assertEquals(SetPurpose.AMRAP, state.value.sets.first().purpose)
        assertNull(state.value.sets.first().calibrationScope)
        assertNull(state.value.sets.first().restAfterSeconds)
    }

    @Test fun amrapBilateralConservaCargaYRangoAlCambiarElMinimo() {
        val source = rm().let { e -> e.copy(sets = e.sets.mapIndexed { index, set ->
            if (index == 0) set.copy(purpose = SetPurpose.AMRAP, amrapMinimumReps = 5) else set
        }) }
        mount(source)
        rule.onNodeWithContentDescription("%RM").assertDoesNotExist()
        rule.onNodeWithContentDescription("Aumentar mínimo AMRAP").performScrollTo().performClick()
        val saved = state.value.sets.first()
        assertEquals(6, saved.amrapMinimumReps)
        assertEquals(source.sets.first().weight, saved.weight)
        assertEquals(source.sets.first().targetRepsRange, saved.targetRepsRange)
        assertEquals(2, saved.targetRIR)
        rule.onAllNodes(hasText("Eliminar") and within()).assertCountEquals(1)
    }

    @Test fun segundaRondaConservaSubirEliminarYNoBajar() {
        mount(rm(), round = 2)
        rule.onNode(hasText("Subir") and within(set = "s2")).performScrollTo().performClick()
        rule.onNode(hasText("Eliminar") and within(set = "s2")).performScrollTo().performClick()
        assertEquals(listOf("s2" to -1), moved)
        assertEquals(listOf("s2"), removed)
        rule.onNode(hasText("Bajar") and within(set = "s2")).assertDoesNotExist()
    }

    @Test fun alcanceAmrapEnC3Al130MuestraAmbasDecisionesCompletasYConservaSusClicks() {
        fontScale.value = 1.3f
        val source = amrap()
        mount(source)
        fun completeScope(label: String) {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNode(hasText(label) and within("R"), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val lines = 0 until layout.lineCount
            val detail = "${layout.size}, párrafo=${layout.multiParagraph.width}, líneas=${lines.map { layout.getLineLeft(it) to layout.getLineRight(it) }}, ${layout.layoutInput.constraints}"
            println("C3 alcance «$label»: $detail")
            assertEquals("Etiqueta exacta del alcance", label, layout.layoutInput.text.text)
            assertFalse("$label desborda en alto: $detail", layout.didOverflowHeight)
            for (line in lines) assertFalse("$label elidido: $detail", layout.isLineEllipsized(line))
            assertEquals("Todos los caracteres visibles: $detail", label.length,
                layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            // Como H3: el párrafo puede ser mayor que la caja ajustada sin recortar ninguna línea.
            assertTrue("$label recortado: $detail", lines.all {
                layout.getLineLeft(it) >= -1f && layout.getLineRight(it) <= layout.size.width + 1f
            })
        }
        completeScope("Esta sesión")
        completeScope("Próximas sesiones")
        rule.onNode(hasText("Próximas sesiones") and within("R")).performScrollTo().performClick().assertIsSelected()
        assertEquals(CalibrationScope.NEXT_SESSIONS, state.value.sets.first().calibrationScope)
        rule.onNode(hasText("Esta sesión") and within("R")).performScrollTo().performClick().assertIsSelected()
        assertEquals(CalibrationScope.SESSION, state.value.sets.first().calibrationScope)
        assertEquals(source.sets.first().leftTarget, state.value.sets.first().leftTarget)
        assertEquals(source.sets.first().rightTarget, state.value.sets.first().rightTarget)
    }

    @Test fun renderRmC3En100Y130() {
        val source = rm(tm = 100.0, basis = LoadBasis.PERCENT_TM)
        render(source.copy(sets = source.sets.map { set -> set.copy(weight = rmSetLoadKg(source, set)) }), "rm")
    }
    @Test fun renderAmrapLateralC3En100Y130() = render(amrap(), "amrap_lateral")

    private fun render(exercise: Exercise, scene: String) {
        val output = System.getenv("KPKN_RENDER_DIR")
        assumeTrue("Los renders requieren KPKN_RENDER_DIR", output != null)
        mount(exercise)
        for (scale in listOf(1f, 1.3f)) {
            rule.runOnIdle { fontScale.value = scale }
            rule.onNodeWithTag(if (scene == "rm") "round-set-focus-s1" else "round-set-focus-s1:R").performScrollTo()
            rule.waitForIdle()
            val view = hostView!!
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { view.draw(Canvas(bitmap)) }
            val file = File(output!!, "int_c3_${scene}_${(scale * 100).toInt()}.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
