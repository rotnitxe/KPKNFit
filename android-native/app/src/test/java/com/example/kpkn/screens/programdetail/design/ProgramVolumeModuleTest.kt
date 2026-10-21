package com.example.kpkn.screens.programdetail.design

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Módulo «Volumen de la semana» y sus vacíos (P4), en Robolectric: desplegar una fila, calibrar y los textos honestos.
 * Se pintan con movimiento reducido: la entrada animada del módulo no debe mover los nodos mientras se prueban.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramVolumeModuleTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Pinta [content] en reposo, sin la entrada animada de los módulos. */
    private fun setAtRest(content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                content()
            }
        }
    }

    @Test
    fun filaDesplegadaMuestraElDesglose() {
        setAtRest {
            ProgramVolumeModule(
                muscles = ProgramDetailSamples.volumeWeek,
                scope = ProgramDetailSamples.volumeScopeWeek,
                onSelectKind = {},
                onPreviousScope = {},
                onNextScope = {},
                expandedMuscle = "Pectorales",
                onToggleMuscle = {},
                breakdown = ProgramDetailSamples.volumeBreakdownPectorales,
                onCalibrate = {},
            )
        }
        composeRule.onNodeWithText("Press banca").assertExists()
    }

    @Test
    fun tocarUnaFilaPideDesplegarla() {
        var requested: String? = null
        setAtRest {
            ProgramVolumeModule(
                muscles = ProgramDetailSamples.volumeWeek,
                scope = ProgramDetailSamples.volumeScopeWeek,
                onSelectKind = {},
                onPreviousScope = {},
                onNextScope = {},
                expandedMuscle = null,
                onToggleMuscle = { requested = it },
                breakdown = null,
                onCalibrate = {},
            )
        }
        // La fila es un botón con acción de clic: se activa por su acción semántica, la misma que usa TalkBack.
        composeRule.onNodeWithTag("volumen_fila_Pectorales")
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Pectorales", requested)
    }

    @Test
    fun calibrarLlamaALaAccion() {
        var calibrated = false
        setAtRest {
            ProgramVolumeModule(
                muscles = ProgramDetailSamples.volumeWeek,
                scope = ProgramDetailSamples.volumeScopeWeek,
                onSelectKind = {},
                onPreviousScope = {},
                onNextScope = {},
                expandedMuscle = null,
                onToggleMuscle = {},
                breakdown = null,
                onCalibrate = { calibrated = true },
            )
        }
        composeRule.onNodeWithText("Calibrar").performClick()
        assertTrue(calibrated)
    }

    @Test
    fun sinActivarElProgramaElVacioOfreceActivar() {
        var activated = false
        setAtRest {
            ProgramVolumeInactive(onActivate = { activated = true })
        }
        composeRule.onNodeWithText("Activa tu programa para ver el volumen").assertExists()
        composeRule.onNodeWithText("Activar").performClick()
        assertTrue(activated)
    }

    @Test
    fun sinSesionesElVacioOfreceAnadirSesion() {
        var added = false
        setAtRest {
            ProgramVolumeNoSessions(onAddSession = { added = true })
        }
        composeRule.onNodeWithText("Aún no hay sesiones en esta semana").assertExists()
        composeRule.onNodeWithText("Añadir sesión").performClick()
        assertTrue(added)
    }

    @Test
    fun registrosSinFechaSeExplicanSinOcultarLasFilasConVolumenReal() {
        val muscle = ProgramDetailSamples.volumeWeek.first().copy(sets = 0.0, real = 2.0)
        setAtRest {
            ProgramVolumeModule(
                muscles = listOf(muscle),
                scope = ProgramDetailSamples.volumeScopeWeek,
                onSelectKind = {}, onPreviousScope = {}, onNextScope = {},
                expandedMuscle = null, onToggleMuscle = {}, breakdown = null, onCalibrate = {},
                unassignedCount = 2,
            )
        }
        composeRule.onNodeWithText("2 registros sin fecha no se cuentan").assertExists()
        composeRule.onNodeWithTag("volumen_fila_${muscle.name}").assertExists()
        composeRule.onNodeWithText("Real 2 series/sem", useUnmergedTree = true).assertExists()
    }

    private fun assertPlannedAndRealUnits(scope: ProgramVolumeScope, totalVolume: Boolean = false, fontScale: Float = 1f) {
        val muscle = ProgramDetailSamples.volumeWeek.first().copy(sets = 6.0, real = 4.0)
        setAtRest {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.width(328.dp)) {
                    ProgramVolumeModule(
                        muscles = listOf(muscle), scope = scope,
                        onSelectKind = {}, onPreviousScope = {}, onNextScope = {},
                        expandedMuscle = null, onToggleMuscle = {}, breakdown = null, onCalibrate = {},
                        totalVolume = totalVolume,
                    )
                }
            }
        }
        val unit = if (totalVolume) "series" else "series/sem"
        composeRule.onNodeWithContentDescription("6 $unit", useUnmergedTree = true).assertExists()
        val row = composeRule.onNodeWithTag("volumen_fila_${muscle.name}")
        for (text in listOf(unit, "Real 4 $unit")) {
            assertVolumeTextComplete(
                composeRule.onNodeWithText(text, useUnmergedTree = true), row, text,
                context = "unidad al $fontScale",
            )
        }
    }

    @Test
    fun laSemanaMuestraLaMismaUnidadEnPlanificadoYReal() {
        assertPlannedAndRealUnits(ProgramDetailSamples.volumeScopeWeek)
    }

    @Test
    fun elPromedioSemanalMuestraLaMismaUnidadSinRecortesAlCientoTreinta() {
        assertPlannedAndRealUnits(
            ProgramDetailSamples.volumeScopeWeek.copy(
                label = "Programa completo", detail = "Promedio semanal de 3 semanas.",
            ),
            fontScale = 1.3f,
        )
    }

    @Test
    fun soloElTotalMuestraSeriesSinUnidadSemanal() {
        assertPlannedAndRealUnits(
            ProgramDetailSamples.volumeScopeWeek.copy(label = "Programa completo", detail = "Total acumulado."),
            totalVolume = true,
        )
        composeRule.onNodeWithText("VOLUMEN TOTAL").assertExists()
        composeRule.onNodeWithText("series/sem", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun lasPorcionesConservanElRolYExplicanQueNoSeSuman() {
        setAtRest {
            ProgramVolumeBreakdownList(
                ProgramVolumeBreakdown(
                    exercises = listOf(ProgramVolumeLine("Press", 3.0)),
                    subMuscles = listOf(ProgramVolumeSubMuscle("Deltoides Lateral", 1.5, emptyList(), com.example.kpkn.data.models.MuscleRole.SECONDARY)),
                ),
            )
        }
        composeRule.onNodeWithText("Deltoides Lateral · Secundario").assertExists()
        composeRule.onNodeWithText("Las porciones comparten series; no se suman al músculo.").assertExists()
    }

    @Test
    fun losNombresYRolesDePorcionesNoSeCortanConLetraAlCientoTreinta() {
        val label = "Deltoides Anterior · Directo"
        setAtRest {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                Box(Modifier.width(288.dp)) {
                    ProgramVolumeBreakdownList(
                        ProgramVolumeBreakdown(
                            exercises = emptyList(),
                            subMuscles = listOf(ProgramVolumeSubMuscle("Deltoides Anterior", 4.0, emptyList(), com.example.kpkn.data.models.MuscleRole.PRIMARY)),
                        ),
                    )
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertFalse("el nombre y el rol deben quedar completos", layouts.single().hasVisualOverflow)
    }
}

/**
 * Comprueba el texto dispuesto y su caja visible; conserva las métricas de overflow como diagnóstico.
 * Con softWrap=false, el párrafo puede retener el ancho máximo aunque la línea quepa en su caja intrínseca.
 */
internal fun assertVolumeTextComplete(
    node: SemanticsNodeInteraction,
    container: SemanticsNodeInteraction,
    text: String,
    context: String,
    singleLine: Boolean = true,
) {
    val layouts = mutableListOf<TextLayoutResult>()
    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertTrue("$context: se esperaba un layout para «$text», recibidos ${layouts.size}", layouts.size == 1)
    val layout = layouts.single()
    val bounds = node.getBoundsInRoot()
    val containerBounds = container.getBoundsInRoot()
    val visibleWidthPx = node.fetchSemanticsNode().boundsInRoot.width
    val naturalWidth = layout.multiParagraph.intrinsics.maxIntrinsicWidth
    val lines = 0 until layout.lineCount
    val visibleEnd = if (layout.lineCount > 0) layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) else 0
    val ellipsized = lines.any { layout.isLineEllipsized(it) }
    val lineWidths = lines.map { layout.getLineRight(it) - layout.getLineLeft(it) }
    val complete = layout.lineCount > 0 && visibleEnd == text.length && !ellipsized
    val fitsLineCount = !singleLine || layout.lineCount == 1
    // Una línea debe caber tanto en la caja medida como en la parte visible tras el recorte de sus padres.
    val fitsWidth = if (singleLine) {
        naturalWidth <= layout.size.width && naturalWidth <= visibleWidthPx
    } else {
        lineWidths.all { it <= layout.size.width && it <= visibleWidthPx }
    }
    val inside = bounds.left >= containerBounds.left && bounds.right <= containerBounds.right &&
        bounds.top >= containerBounds.top && bounds.bottom <= containerBounds.bottom
    val diagnostic = "$context: «$text», lineCount=${layout.lineCount}, size=${layout.size}, " +
        "paragraphWidth=${layout.multiParagraph.width}, maxIntrinsicWidth=$naturalWidth, " +
        "didOverflowWidth=${layout.didOverflowWidth}, didOverflowHeight=${layout.didOverflowHeight}, " +
        "hasVisualOverflow=${layout.hasVisualOverflow}, visibleEnd=$visibleEnd/${text.length}, " +
        "ellipsized=$ellipsized, lineWidths=$lineWidths, visibleWidthPx=$visibleWidthPx, " +
        "nodeBounds=$bounds, containerBounds=$containerBounds, " +
        "complete=$complete, fitsLineCount=$fitsLineCount, fitsWidth=$fitsWidth, inside=$inside"
    println("[VolumenTexto] $diagnostic")
    assertTrue(diagnostic, complete && fitsLineCount && fitsWidth && inside)
}
