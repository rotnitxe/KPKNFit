package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.screens.sessioneditor.design.SessionModuleEdge
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cada módulo de la lista (grupo, sueltos y cardio) reparte su borde de arriba y de abajo entre sus ítems (ER1b). */
class SessionListEdgesTest {

    private fun strength(id: String) = Exercise(id = id, name = id, exerciseDbId = id)

    private fun cardio(id: String) = Exercise(id = id, name = id, cardioDetails = CardioDetails(type = CardioType.WALK))

    @Test
    fun gruposYSueltosTienenTapaYFondo() {
        val session = Session(
            id = "s1",
            name = "Día",
            exercises = listOf(strength("l1"), strength("l2")),
            parts = listOf(
                SessionPart(id = "p1", name = "Empuje", exercises = listOf(strength("e1"), strength("e2"))),
                SessionPart(id = "p2", name = "Pierna", exercises = listOf(strength("e3"))),
            ),
        )
        val items = buildSessionListItems(session, collapsedPartIds = setOf("p2"))

        assertEquals(SessionModuleEdge.TOP, looseEdge(items, "l1"))
        assertEquals(SessionModuleEdge.BOTTOM, looseEdge(items, "l2"))
        assertEquals(SessionModuleEdge.TOP, headerEdge(items, "p1"))
        assertEquals(SessionModuleEdge.MIDDLE, partExerciseEdge(items, "e1"))
        assertEquals(SessionModuleEdge.MIDDLE, partExerciseEdge(items, "e2"))
        assertEquals(SessionModuleEdge.BOTTOM, footerEdge(items, "p1"))
        // Un grupo plegado es un módulo de una sola pieza.
        assertEquals(SessionModuleEdge.ONLY, headerEdge(items, "p2"))
    }

    @Test
    fun elEspacioDeCardioEsUnSoloModuloConSusCardios() {
        val session = Session(
            id = "s2",
            name = "Día",
            exercises = listOf(strength("l1")),
            parts = listOf(
                SessionPart(id = "cardio", name = "Espacio de cardio", exercises = listOf(cardio("c1"), cardio("c2"))),
            ),
        )
        val items = buildSessionListItems(session)

        assertEquals(SessionModuleEdge.TOP, cardioEdge(items, "c1"))
        assertEquals(SessionModuleEdge.MIDDLE, cardioEdge(items, "c2"))
        // Los divisores y las acciones de fuerza van sobre el negro: no llevan borde.
        assertEquals(
            null,
            items.firstOrNull { it is SessionListItem.StrengthAddActions }?.let { edgeOrNull(it) },
        )
    }

    private fun edgeOrNull(item: SessionListItem): SessionModuleEdge? = when (item) {
        is SessionListItem.LooseExercise -> item.edge
        is SessionListItem.PartHeader -> item.edge
        is SessionListItem.PartExercise -> item.edge
        is SessionListItem.PartAddExercise -> item.edge
        is SessionListItem.LooseSuperset -> item.edge
        is SessionListItem.PartSuperset -> item.edge
        else -> null
    }

    private fun looseEdge(items: List<SessionListItem>, id: String) =
        items.filterIsInstance<SessionListItem.LooseExercise>().first { it.exerciseId == id }.edge

    private fun headerEdge(items: List<SessionListItem>, partId: String) =
        items.filterIsInstance<SessionListItem.PartHeader>().first { it.partId == partId }.edge

    private fun footerEdge(items: List<SessionListItem>, partId: String) =
        items.filterIsInstance<SessionListItem.PartAddExercise>().first { it.partId == partId }.edge

    private fun partExerciseEdge(items: List<SessionListItem>, id: String) =
        items.filterIsInstance<SessionListItem.PartExercise>().first { it.exerciseId == id }.edge

    private fun cardioEdge(items: List<SessionListItem>, id: String) =
        items.filterIsInstance<SessionListItem.PartExercise>().first { it.exerciseId == id }.edge
}
