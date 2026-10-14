package com.example.kpkn.screens.workout.design

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pruebas del cuerpo de la ruta de la sesión (KPKN LS4), montado suelto: sin la hoja, que no entrega toques en Robolectric. Cubre el
 * tocar una fila, las acciones de pie, el modo dios (selección, lote e isla) y «Deshacer» con su índice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutRouteSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setBody(
        groups: List<WorkoutRouteGroup> = sampleRouteGroups(),
        undoCount: Int = 0,
        onSkip: (() -> Unit)? = {},
        onClose: () -> Unit = {},
        onSelectExercise: (Int) -> Unit = {},
        onAdd: () -> Unit = {},
        onBatchSkip: (List<String>) -> Unit = {},
        onRevert: (Int) -> Unit = {},
    ) {
        rule.setContent {
            WorkoutRouteBody(
                summary = workoutRouteSummary(currentIdx = 2, exerciseCount = 6, setsDone = 14, setsTotal = 22),
                groups = groups,
                accent = SymbolPalette.musculo,
                godModeUndoCount = undoCount,
                onClose = onClose,
                onSelectExercise = onSelectExercise,
                onSelectStep = {},
                onOpenContext = {},
                onOpenMemberContext = {},
                onReorder = {},
                onAdd = onAdd,
                onCreateSuperset = {},
                onSkip = onSkip,
                onBatchCreateSuperset = {},
                onBatchSkip = onBatchSkip,
                onBatchDelete = {},
                onBatchUltraFast = { _, _ -> },
                onDissolveSuperset = {},
                onRevertGodModeAction = onRevert,
                modifier = Modifier.height(700.dp),
            )
        }
    }

    @Test
    fun tocar_una_fila_lleva_a_ese_ejercicio_y_cierra_la_ruta() {
        val picked = mutableListOf<Int>()
        var closed = false
        setBody(
            onSelectExercise = { picked.add(it) },
            onClose = { closed = true },
        )

        rule.onNodeWithText("Press inclinado con mancuernas").performClick()

        assertEquals(listOf(2), picked)
        assertEquals(true, closed)
    }

    @Test
    fun anadir_y_saltar_ejercicio_llaman_a_lo_suyo() {
        var added = 0
        var skipped = 0
        setBody(
            onAdd = { added++ },
            onSkip = { skipped++ },
        )

        rule.onNodeWithText("Añadir ejercicio").performClick()
        rule.onNodeWithText("Saltar ejercicio").performClick()

        assertEquals(1, added)
        assertEquals(1, skipped)
    }

    @Test
    fun seleccionar_muestra_la_isla_y_el_lote_lleva_los_ids_elegidos() {
        val batches = mutableListOf<List<String>>()
        var closed = false
        setBody(
            onBatchSkip = { batches.add(it) },
            onClose = { closed = true },
        )

        rule.onNodeWithText("Seleccionar").performClick()
        rule.onNodeWithText("Elige ejercicios").assertExists()
        rule.onNodeWithText("Press inclinado con mancuernas").performClick()
        rule.onNodeWithText("Remo con mancuerna a una mano").performClick()
        rule.onNodeWithText("2 elegidos").assertExists()
        rule.onNodeWithText("Omitir").performClick()

        assertEquals(listOf(listOf("e3", "e2")), batches)
        assertEquals(false, closed)
    }

    @Test
    fun deshacer_llama_con_el_indice_de_la_ultima_accion() {
        val reverted = mutableListOf<Int>()
        setBody(
            undoCount = 2,
            onRevert = { reverted.add(it) },
        )

        rule.onNodeWithText("Seleccionar").performClick()
        rule.onNodeWithText("Deshacer").performClick()

        assertEquals(listOf(1), reverted)
    }
}

/** Ruta de muestra: 6 ejercicios con 2 hechos, el actual (e3) y una superserie de 2 (e4 y e5). Los mismos datos que la hoja de render. */
internal fun sampleRouteGroups(withCardio: Boolean = false): List<WorkoutRouteGroup> = buildList {
    add(routeSingle("e1", 0, "Peso muerto con barra", done = 4, total = 4))
    add(routeSingle("e2", 1, "Remo con mancuerna a una mano", done = 4, total = 4))
    add(routeSingle("e3", 2, "Press inclinado con mancuernas", done = 1, total = 4, current = true))
    add(
        WorkoutRouteGroup(
            groupId = "sa",
            caption = "Superserie A · ronda 2 de 3",
            isCardio = false,
            members = listOf(
                routeMember("e4", 3, "Aperturas en polea", done = 2, total = 3, stepKey = "e4_1_"),
                routeMember("e5", 4, "Curl martillo", done = 2, total = 3, stepKey = "e5_1_"),
            ),
        ),
    )
    add(routeSingle("e6", 5, "Sentadilla frontal", done = 0, total = 4))
    if (withCardio) {
        add(
            WorkoutRouteGroup(
                groupId = null,
                caption = null,
                isCardio = true,
                members = listOf(routeMember("e7", 6, "Bicicleta estática", done = 0, total = 1)),
            ),
        )
    }
}

internal fun routeMember(
    id: String,
    index: Int,
    name: String,
    done: Int,
    total: Int,
    current: Boolean = false,
    stepKey: String? = null,
) = WorkoutRouteMember(
    exerciseId = id,
    exerciseIndex = index,
    name = name,
    setsDone = done,
    setsTotal = total,
    isCurrent = current,
    stepKey = stepKey,
)

internal fun routeSingle(
    id: String,
    index: Int,
    name: String,
    done: Int,
    total: Int,
    current: Boolean = false,
) = WorkoutRouteGroup(
    groupId = null,
    caption = null,
    isCardio = false,
    members = listOf(routeMember(id, index, name, done, total, current)),
)
