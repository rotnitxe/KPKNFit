package com.example.kpkn.screens.programdetail.design

import org.junit.Assert.assertEquals
import org.junit.Test

/** La hoja «Pendiente»: lo más urgente primero (a igual prioridad, en orden de entrada) y los grupos en orden de aparición. */
class ProgramPendingModelTest {

    private fun item(kind: ProgramPendingKind, title: String) =
        ProgramPendingItem(kind = kind, title = title, detail = "", onOpen = {})

    @Test
    fun orderedPendingSortsByPriorityAndKeepsTheEntryOrderWithinAPriority() {
        val items = listOf(
            item(ProgramPendingKind.CONTINUATION, "continuación"),
            item(ProgramPendingKind.PROGRESSION, "progresión A"),
            item(ProgramPendingKind.ONE_RM_TEST, "test 1RM"),
            item(ProgramPendingKind.PROGRESSION, "progresión B"),
            item(ProgramPendingKind.WEEKLY_CHECKIN, "check-in"),
            item(ProgramPendingKind.MISSING_TM, "TM que falta"),
        )

        assertEquals(
            listOf("test 1RM", "TM que falta", "progresión A", "progresión B", "check-in", "continuación"),
            orderedPending(items).map { it.title },
        )
    }

    @Test
    fun groupedPendingListsTheWeekGroupsInOrderAndCarriesTheirItems() {
        val grouped = groupedPending(
            orderedPending(
                listOf(
                    item(ProgramPendingKind.CONTINUATION, "continuación"),
                    item(ProgramPendingKind.WEEKLY_CHECKIN, "check-in"),
                    item(ProgramPendingKind.PROGRESSION, "progresión"),
                    item(ProgramPendingKind.MISSED_SESSIONS, "sesión perdida"),
                ),
            ),
        )

        assertEquals(
            listOf(
                "Esta semana" to listOf("sesión perdida", "progresión"),
                "Semana pasada" to listOf("check-in"),
                "Más adelante" to listOf("continuación"),
            ),
            grouped.map { (group, members) -> group to members.map { it.title } },
        )
    }

    @Test
    fun groupedPendingOmitsGroupsWithoutItems() {
        val grouped = groupedPending(listOf(item(ProgramPendingKind.PROGRESSION, "a"), item(ProgramPendingKind.CONTINUATION, "b")))

        assertEquals(listOf("Esta semana", "Más adelante"), grouped.map { it.first })
        assertEquals(emptyList<Pair<String, List<ProgramPendingItem>>>(), groupedPending(emptyList()))
    }

    @Test
    fun groupedPendingKeepsTheOrderInWhichTheGroupsAppear() {
        val grouped = groupedPending(listOf(item(ProgramPendingKind.CONTINUATION, "b"), item(ProgramPendingKind.PROGRESSION, "a")))

        assertEquals(listOf("Más adelante", "Esta semana"), grouped.map { it.first })
    }
}
