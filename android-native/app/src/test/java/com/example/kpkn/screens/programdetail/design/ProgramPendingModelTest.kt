package com.example.kpkn.screens.programdetail.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La hoja «Pendiente» (lienzo v6, p-pending): bloqueantes, propuestas, preguntas y «Más adelante», en ese orden (a igual prioridad, en
 * orden de entrada); la primera propuesta va destacada y «Más adelante» no cuenta en la campana.
 */
class ProgramPendingModelTest {

    private fun item(kind: ProgramPendingKind, title: String, resolvable: Boolean = false) =
        ProgramPendingItem(
            kind = kind,
            title = title,
            detail = "",
            onOpen = {},
            primaryLabel = if (resolvable) "Aplicar" else null,
            onPrimary = if (resolvable) ({}) else null,
            secondaryLabel = if (resolvable) "Mantener carga" else null,
            onSecondary = if (resolvable) ({}) else null,
        )

    @Test
    fun orderedPendingSortsByPriorityAndKeepsTheEntryOrderWithinAPriority() {
        val items = listOf(
            item(ProgramPendingKind.CONTINUATION, "continuación"),
            item(ProgramPendingKind.PROGRESSION, "progresión"),
            item(ProgramPendingKind.ONE_RM_TEST, "test 1RM"),
            item(ProgramPendingKind.PROGRAM_END_SOON, "fin pronto"),
            item(ProgramPendingKind.AUTOREGULATION, "ajuste"),
            item(ProgramPendingKind.WEEKLY_CHECKIN, "check-in"),
            item(ProgramPendingKind.MISSING_TM, "TM que falta"),
        )

        assertEquals(
            listOf("test 1RM", "TM que falta", "progresión", "ajuste", "continuación", "check-in", "fin pronto"),
            orderedPending(items).map { it.title },
        )
    }

    @Test
    fun theFirstResolvableProposalIsFeaturedAndLeavesTheRows() {
        val items = listOf(
            item(ProgramPendingKind.WEEKLY_CHECKIN, "check-in"),
            item(ProgramPendingKind.PROGRESSION, "Sentadilla: +2,5 kg", resolvable = true),
            item(ProgramPendingKind.MISSING_TM, "Falta el TM de press militar"),
            item(ProgramPendingKind.PROGRAM_END_SOON, "Fin del programa en 2 semanas"),
        )

        assertEquals("Sentadilla: +2,5 kg", featuredPending(items)?.title)
        assertEquals(listOf("Falta el TM de press militar", "check-in"), pendingNowRows(items).map { it.title })
        assertEquals(listOf("Fin del programa en 2 semanas"), pendingLaterRows(items).map { it.title })
    }

    @Test
    fun aProposalWithoutActionsIsNotFeatured() {
        assertNull(featuredPending(listOf(item(ProgramPendingKind.AUTOREGULATION, "Propuesta de ajuste"))))
    }

    @Test
    fun laterRowsDoNotCountAsDecisions() {
        val items = listOf(
            item(ProgramPendingKind.PROGRESSION, "a", resolvable = true),
            item(ProgramPendingKind.WEEKLY_CHECKIN, "b"),
            item(ProgramPendingKind.PROGRAM_END_SOON, "c"),
        )

        assertEquals(2, pendingDecisionCount(items))
        assertEquals("2 cosas esperan tu decisión.", pendingSubtitle(2))
        assertEquals("1 cosa espera tu decisión.", pendingSubtitle(1))
    }

    @Test
    fun theRowOfSeveralOfTheSameKindSaysHowManyMore() {
        assertEquals("Cerraste 3 × 5 con RIR 3", pendingDetailWithMore("Cerraste 3 × 5 con RIR 3", 0))
        assertEquals("Cerraste 3 × 5 con RIR 3 · y 2 más", pendingDetailWithMore("Cerraste 3 × 5 con RIR 3", 2))
        assertEquals("Y 1 más", pendingDetailWithMore("", 1))
    }

    @Test
    fun theTextsOfTheRowsAreTheOnesOfTheCanvas() {
        assertEquals("Falta el TM de press militar", missingTmTitle(listOf("Press militar")))
        assertEquals("Faltan los TM de press militar y remo", missingTmTitle(listOf("Press militar", "Remo")))
        assertEquals("Faltan 3 TM", missingTmTitle(listOf("a", "b", "c")))
        assertEquals("Sin él, sus cargas no se calculan", missingTmDetail(1))
        assertEquals("Fin del programa en 2 semanas", programEndSoonTitle(weekNumber = 4, weekCount = 6))
        assertEquals("Fin del programa en 1 semana", programEndSoonTitle(weekNumber = 5, weekCount = 6))
        assertNull(programEndSoonTitle(weekNumber = 2, weekCount = 6))
        assertNull(programEndSoonTitle(weekNumber = 6, weekCount = 6))
        assertEquals("2 sesiones sin registrar", missedSessionsTitle(2))
        assertEquals("1 sesión sin registrar", missedSessionsTitle(1))
    }
}
