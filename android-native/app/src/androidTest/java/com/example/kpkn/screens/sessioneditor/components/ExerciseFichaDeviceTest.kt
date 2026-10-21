package com.example.kpkn.screens.sessioneditor.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.exercises.exerciseFichaEffects
import com.example.kpkn.domain.exercises.exerciseHistorySummary
import com.example.kpkn.ui.theme.KPKNTheme
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Contratos de la ficha única comprobables en el Z Flip, sin datos ni permisos de cámara. */
@RunWith(AndroidJUnit4::class)
class ExerciseFichaDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun model(context: ExerciseFichaContext = ExerciseFichaContext.CATALOG) = CatalogDetailModel(
        name = "Press banca", caption = null, description = null, imageVariants = emptyList(), selectedImplementation = null,
        axes = emptyList(), configurationLine = null, configurationIncomplete = false, muscleInfo = null,
        joints = emptyList(), patternId = null, isSelected = false, canSelect = false, context = context,
    )
    private fun show(model: CatalogDetailModel) {
        rule.setContent {
            KPKNTheme {
                Box(Modifier.width(360.dp).height(620.dp)) {
                    ExerciseFichaContent(model, onDismiss = {}, onToggleSelection = {}, onSelectOption = { _, _ -> }, onImplementationSettled = {})
                }
            }
        }
    }
    @Test fun catalogoConservaElPieDeshabilitadoSiFaltaConfiguracion() {
        show(model())
        rule.onNodeWithText("Añadir").assertExists()
        rule.onNodeWithTag("exercise-ficha-select").assertIsNotEnabled()
    }
    @Test fun sesionNoMuestraPieNiBloquesSinDatos() {
        show(model(ExerciseFichaContext.SESSION))
        rule.onNodeWithTag("exercise-ficha-select").assertDoesNotExist()
        rule.onNodeWithText("Tu historial").assertDoesNotExist()
        rule.onNodeWithText("Cómo se hace").assertDoesNotExist()
        rule.onNodeWithText("Articulaciones").assertDoesNotExist()
    }
    @Test fun wizardConservaSuContextoYSinPie() {
        show(model(ExerciseFichaContext.WIZARD).copy(contextLine = "En tu programa: Banca A"))
        rule.onNodeWithTag("setup-exercise-info").assertExists()
        rule.onNodeWithText("En tu programa: Banca A").assertExists()
        rule.onNodeWithTag("exercise-ficha-select").assertDoesNotExist()
    }
    @Test fun reemplazoConservaLaAccionElegir() {
        assertEquals("Elegir", exerciseFichaFooter(ExerciseFichaContext.REPLACEMENT, false))
        assertEquals("Quitar", exerciseFichaFooter(ExerciseFichaContext.CATALOG, true))
        assertNull(exerciseFichaFooter(ExerciseFichaContext.SESSION, true))
        assertNull(exerciseFichaFooter(ExerciseFichaContext.WIZARD, false))
    }
    @Test fun losConsejosRealesConservanPreparacionAntesDeEjecucion() {
        show(model(ExerciseFichaContext.SESSION).copy(
            description = "Descripción editorial", setupCues = listOf("Ajusta el banco"), executionCues = listOf("Empuja de forma estable"),
        ))
        val how = rule.onNodeWithText("Cómo se hace").fetchSemanticsNode().boundsInRoot.top
        val setup = rule.onNodeWithText("Ajusta el banco").fetchSemanticsNode().boundsInRoot.top
        val execution = rule.onNodeWithText("Empuja de forma estable").fetchSemanticsNode().boundsInRoot.top
        assertTrue(how < setup && setup < execution)
    }

    @Test fun elDesplazamientoDifuminaOscureceYAgrandaSinBucle() {
        val top = exerciseFichaEffects(0f)
        val half = exerciseFichaEffects(150f)
        val end = exerciseFichaEffects(310f)
        assertEquals(0f, top.blurDp, 0f)
        assertEquals(8f, half.blurDp, 0f)
        assertEquals(0.34f, half.darkness, 0.001f)
        assertEquals(1.08f, end.scale, 0.001f)
        assertEquals(1f, end.headerAlpha, 0f)
        val reduced = exerciseFichaEffects(300f, reducedMotion = true, canBlur = false)
        assertEquals(1f, reduced.scale, 0f)
        assertEquals(0f, reduced.blurDp, 0f)
        assertTrue(reduced.darkness > 0f)
    }
    @Test fun historialRealPorIdentidadSinFiltrarEtiquetasNiContarCalentamientos() {
        fun log(id: String, date: String, sets: List<CompletedSet>, exerciseId: String = "banca") = WorkoutLog(
            id = id, programId = "p", sessionId = id, sessionName = id, date = date, durationMinutes = 20,
            completedExercises = listOf(CompletedExercise(exerciseId, "Press", canonicalExerciseId = exerciseId, sets = sets)),
        )
        val history = listOf(
            log("old", "2026-10-12", listOf(CompletedSet("old_0", weight = 95.0, reps = 6, tagId = "A"))),
            log("new", "2026-10-15T18:00:00Z", listOf(CompletedSet("new_0", weight = 70.0, reps = 4, tagId = "B"))),
            log("warmup", "2026-10-16", listOf(CompletedSet("warm_0", weight = 200.0, reps = 8, isWarmup = true))),
            log("other", "2026-10-16", listOf(CompletedSet("other_0", weight = 500.0, reps = 10)), "sentadilla"),
        )
        val summary = exerciseHistorySummary(history, setOf("banca"), ZoneOffset.UTC)!!
        assertEquals("old_0", summary.bestSet?.id)
        assertTrue(summary.estimatedRmKg!! > 95.0)
        assertEquals(LocalDate.of(2026, 10, 15), summary.lastDate)
        assertNull(exerciseHistorySummary(history, setOf("remo"), ZoneOffset.UTC))
        assertNull(exerciseHistorySummary(emptyList(), setOf("banca"), ZoneOffset.UTC))
    }
}
