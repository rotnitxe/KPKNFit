package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class ConfirmedProgramProposalsTest {
    @Test fun legacyAutomaticModeStillRequiresConfirmationOfEachNewProposal() {
        val original=Program("p","Programa",autoregulationMode=AutoregulationMode.AUTO,
            runState=ProgramRunState("run",weekId="w"),powerliftingProfile=PowerliftingProfile(squatTM=100.0))
        val proposal=AutoregulationProposal(AutoregulationProposalKind.ADJUST_TM,liftSlot="SQUAT",percentDelta=2.5,explanation="Regla explícita del programa")
        val result=ProgramAutoregulationEngine.apply(original,"next",listOf(proposal),null,emptySet(),null)
        assertFalse(result.applied)
        assertEquals(original.macrocycles,result.program.macrocycles)
        assertEquals(original.powerliftingProfile,result.program.powerliftingProfile)
        assertEquals("w",result.program.runState?.weekId)
        assertEquals(listOf(proposal),result.program.runState?.pendingAction?.proposals)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION,result.program.runState?.pendingAction?.type)
    }
    @Test fun retiredModelProposalsCannotCreateAnotherPendingAction() {
        val original=Program("p","Programa",autoregulationMode=AutoregulationMode.PROPOSE,runState=ProgramRunState("run"))
        val proposals=listOf(AutoregulationProposalKind.SCALE_WEEK_VOLUME,AutoregulationProposalKind.SCALE_WEEK_INTENSITY,
            AutoregulationProposalKind.INSERT_DELOAD,AutoregulationProposalKind.DELAY_PEAK,AutoregulationProposalKind.PROMOTE_TM).map {
            AutoregulationProposal(it,percentDelta=-10.0,volumeFactor=0.7,explanation="Antigua") }
        val result=ProgramAutoregulationEngine.apply(original,null,proposals,null,emptySet(),null)
        assertEquals(original,result.program)
        assertTrue(result.proposals.isEmpty())
        assertFalse(result.applied)
    }
}
