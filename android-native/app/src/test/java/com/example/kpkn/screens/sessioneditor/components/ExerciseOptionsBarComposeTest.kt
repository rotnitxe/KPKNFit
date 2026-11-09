package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.domain.training.resolveLiveApreBlock
import com.example.kpkn.screens.sessioneditor.apreExitPreview
import com.example.kpkn.screens.sessioneditor.toggledBilateralUnilateral
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ER1c1, R1 §4.2 más los tableros B1/B5/B8: la barra de opciones, la lateralidad en ⋯ con su hoja, la hoja de los 7 modos y el módulo «Lados». */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h880dp-mdpi")
class ExerciseOptionsBarComposeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setEditorContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { EditorOverlayTestHost(content) }
    }

    private fun showOptionsBar(exercise: Exercise, onUpdate: ((Exercise) -> Exercise) -> Unit) {
        setEditorContent {
            val local = rememberExerciseEditorLocalState(exercise)
            ExerciseOptionsBar(
                exercise = exercise,
                local = local,
                accentColor = Color(0xFFF49A6E),
                isSupersetExercise = false,
                suppressIndividualRest = false,
                relationshipAnchorName = null,
                onUpdateExercise = onUpdate,
                onOpenSuperset = {},
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onOpenCustomUnit = {},
                onOpenSmartLoad = {},
                onOpenGoal = {},
                onOpenRest = {},
            )
        }
    }

    private val press = Exercise(
        id = "press",
        name = "Press",
        restTime = 90,
        sets = listOf(ExerciseSet(id = "s1", targetReps = 8)),
    )

    @Test
    fun laHojaDeModosTieneLosSieteModosYElegirUnoLoDevuelve() {
        var picked: TrainingMode? = null
        setEditorContent {
            ExerciseModeSheetBody(current = TrainingMode.REPS, onSelect = { picked = it })
        }
        listOf("Reps", "AMRAP", "RM", "Solo RPE", "Tiempo", "Distancia", "Personalizado").forEach { label ->
            rule.onNodeWithText(label).assertExists()
        }
        rule.onNodeWithText("Tiempo").performClick()
        assertEquals(TrainingMode.TIME, picked)
    }

    /** B1/B8: la barra lleva Modo y Descanso; la lateralidad ya no es un alternador fijo, vive en el menú ⋯ y abre su hoja. */
    @Test
    fun elSelectorOfreceApreComoModoVisibleYAbreLaHojaDeBloque() {
        var apreRequested = false
        setEditorContent {
            ExerciseModeSheetBody(
                current = TrainingMode.REPS,
                onSelect = {},
                onSelectApre = { apreRequested = true },
            )
        }
        rule.onNodeWithText("APRE").assertIsDisplayed().performClick()
        assertTrue(apreRequested)
    }

    @Test
    fun salirDeApreEsUnaAccionExplicitaQueConservaLasCuatroSeries() {
        var confirmed = false
        setEditorContent {
            ExerciseModeSheetBody(
                current = TrainingMode.REPS,
                onSelect = {},
                onConfirm = { confirmed = true },
                hasApreBlock = true,
                onSelectApre = {},
            )
        }
        rule.onNodeWithText("Vista previa: se conservan las cuatro series", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Salir de APRE y conservar las cuatro series").assertIsDisplayed().performClick()
        assertTrue(confirmed)
    }

    @Test
    fun convertirUnBloqueApreMuestraEfectosAntesDeConfirmarElNuevoModo() {
        setEditorContent {
            ExerciseModeSheetBody(
                current = TrainingMode.REPS,
                selected = TrainingMode.TIME,
                onSelect = {},
                hasApreBlock = true,
                onSelectApre = {},
            )
        }
        rule.onNodeWithText("Vista previa: se retiran el marcador y las reglas APRE", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Aplicar Tiempo").assertIsDisplayed()
    }

    @Test
    fun marcadorApreInvalidoPermiteSalidaRevisableQueNoConvierteAmrapAjena() {
        val invalid = press.copy(
            reference1RM = 200.0,
            referenceTrainingMax = 180.0,
            sets = listOf(
                ExerciseSet("s1", targetReps = 5, weight = 100.0),
                ExerciseSet("ambiguous", targetReps = 6, weight = 120.0, purpose = SetPurpose.AMRAP, amrapMinimumReps = 6),
                ExerciseSet("foreign", targetReps = 8, weight = 80.0, purpose = SetPurpose.AMRAP, amrapMinimumReps = 6),
                ExerciseSet("s4", targetReps = 6, weight = 140.0, purpose = SetPurpose.AMRAP, amrapMinimumReps = 6),
            ),
            apreBlock = ApreBlockReference(6, listOf("s1", "ambiguous", "ambiguous", "s4")),
        )
        var updated: Exercise? = null
        showOptionsBar(invalid) { transform -> updated = transform(invalid) }

        rule.onNodeWithText("Modo: APRE · revisar").assertIsDisplayed().performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        val preview = invalid.apreExitPreview().summary
        rule.onNodeWithText(preview, substring = true).assertIsDisplayed()
        rule.onNodeWithText("Salir de APRE con esta previsualización").performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()

        assertEquals(TrainingMode.REPS, updated!!.trainingMode)
        assertEquals(null, updated!!.apreBlock)
        assertEquals(SetPurpose.AMRAP, updated!!.sets[1].purpose)
        assertEquals(SetPurpose.AMRAP, updated!!.sets[2].purpose)
        assertEquals(SetPurpose.NONE, updated!!.sets[3].purpose)
        assertEquals(200.0, updated!!.reference1RM!!, 0.0)
        assertEquals(180.0, updated!!.referenceTrainingMax!!, 0.0)
    }

    @Test
    fun marcadorApreInvalidoEntraEnLaRutaDeReparacionConCuatroIdsDistintos() {
        val invalid = press.copy(
            reference1RM = 200.0,
            sets = listOf(ExerciseSet("s1", targetReps = 8, weight = 80.0, purpose = SetPurpose.AMRAP)),
            apreBlock = ApreBlockReference(10, listOf("missing")),
        )
        var updated: Exercise? = null
        showOptionsBar(invalid) { transform -> updated = transform(invalid) }

        rule.onNodeWithText("Modo: APRE · revisar").assertIsDisplayed().performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Revisar APRE").assertIsDisplayed().performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("El marcador guardado está incompleto.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Aplicar APRE 10").performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()

        assertEquals(TrainingMode.REPS, updated!!.trainingMode)
        assertEquals(4, updated!!.apreBlock!!.setIds.size)
        assertEquals(4, updated!!.apreBlock!!.setIds.distinct().size)
        assertEquals(updated!!.apreBlock!!.setIds, updated!!.sets.map { it.id })
        assertEquals(10, resolveLiveApreBlock(updated!!)!!.variant.xrmReps)
    }

    @Test
    fun laBarraMuestraModoYDescansoYLaLateralidadViveEnElMenu() {
        setEditorContent {
            val local = rememberExerciseEditorLocalState(press)
            ExerciseOptionsBar(
                exercise = press,
                local = local,
                accentColor = Color(0xFFF49A6E),
                isSupersetExercise = false,
                suppressIndividualRest = false,
                relationshipAnchorName = null,
                onUpdateExercise = {},
                onOpenSuperset = {},
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onOpenCustomUnit = {},
                onOpenSmartLoad = {},
                onOpenGoal = {},
                onOpenRest = {},
            )
        }
        rule.onNodeWithText("Modo: Reps").assertExists()
        rule.onNodeWithContentDescription("Descanso del ejercicio").assertExists()
        rule.onNodeWithText("1:30").assertExists()
        rule.onNodeWithContentDescription("Más opciones del ejercicio").performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Lateralidad").assertExists().performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Bilateral").assertExists()
        rule.onNodeWithText("Unilateral").assertExists()
    }

    /** B1/B8: cambiar a unilateral desde la hoja hace lo mismo que `toggledBilateralUnilateral`. */
    @Test
    fun laHojaDeLateralidadHaceLoMismoQueToggledBilateralUnilateral() {
        var result: Exercise? = null
        setEditorContent {
            val local = rememberExerciseEditorLocalState(press)
            ExerciseOptionsBar(
                exercise = press,
                local = local,
                accentColor = Color(0xFFF49A6E),
                isSupersetExercise = false,
                suppressIndividualRest = false,
                relationshipAnchorName = null,
                onUpdateExercise = { transform -> result = transform(press) },
                onOpenSuperset = {},
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onOpenCustomUnit = {},
                onOpenSmartLoad = {},
                onOpenGoal = {},
                onOpenRest = {},
            )
        }
        rule.onNodeWithContentDescription("Más opciones del ejercicio").performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Lateralidad").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Unilateral").assertIsDisplayed().performClick()
        rule.waitForIdle()
        val expected = press.toggledBilateralUnilateral()
        assertEquals(expected, result)
        assertTrue(result!!.isEffectivelyUnilateral())
    }

    @Test
    fun elMenuDeOpcionesReuneRomRelacionSuperserieUnidadCargaYMeta() {
        setEditorContent {
            val local = rememberExerciseEditorLocalState(press)
            ExerciseOptionsBar(
                exercise = press,
                local = local,
                accentColor = Color(0xFFF49A6E),
                isSupersetExercise = false,
                suppressIndividualRest = false,
                relationshipAnchorName = null,
                onUpdateExercise = {},
                onOpenSuperset = {},
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onOpenCustomUnit = {},
                onOpenSmartLoad = {},
                onOpenGoal = {},
                onOpenRest = {},
            )
        }
        rule.onNodeWithContentDescription("Más opciones del ejercicio").performClick()
        listOf("Registrar ROM", "Relacionar ejercicio", "Superserie", "Unidad personalizada", "Carga inteligente", "Meta").forEach { label ->
            rule.onNodeWithText(label).assertExists()
        }
    }

    /** Pulido v1 (15): «Registrar ROM» del menú ⋯ cambia `Exercise.trackRom`, el dato del plan que lee la tarjeta en vivo. */
    @Test
    fun medirRomDelMenuActivaElDatoDelPlan() {
        var result: Exercise? = null
        setEditorContent {
            val local = rememberExerciseEditorLocalState(press)
            ExerciseOptionsBar(
                exercise = press,
                local = local,
                accentColor = Color(0xFFF49A6E),
                isSupersetExercise = false,
                suppressIndividualRest = false,
                relationshipAnchorName = null,
                onUpdateExercise = { transform -> result = transform(press) },
                onOpenSuperset = {},
                onOpenRelationshipPicker = {},
                onClearRelationship = {},
                onOpenCustomUnit = {},
                onOpenSmartLoad = {},
                onOpenGoal = {},
                onOpenRest = {},
            )
        }
        rule.onNodeWithContentDescription("Más opciones del ejercicio").performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        rule.onNodeWithText("Registrar ROM").performClick()
        rule.waitForIdle()
        assertTrue(result!!.trackRom)
    }

    @Test
    fun elModuloLadosTieneOrdenLadosYDescansoEntreLados() {
        val unilateral = press.toggledBilateralUnilateral()
        var orderToggled: Exercise? = null
        setEditorContent {
            SideOptionsModule(
                exercise = unilateral,
                onUpdateExercise = { transform -> orderToggled = transform(unilateral) },
                onOpenSideRest = {},
            )
        }
        // B5: el orden de lados se dice en L y R (con flecha, como el lienzo), nunca I/D.
        rule.onNodeWithText("Orden L → R").assertExists()
        rule.onNodeWithText("Lados iguales").assertExists()
        rule.onNodeWithText("Entre lados 0:15").assertExists()
        rule.onNodeWithText("Orden L → R").performClick()
        assertEquals(
            com.example.kpkn.data.models.UnilateralSideOrder.RIGHT_LEFT,
            orderToggled!!.unilateralSideOrder,
        )
    }
}
