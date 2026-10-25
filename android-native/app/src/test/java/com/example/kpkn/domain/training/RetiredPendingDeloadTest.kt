package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class RetiredPendingDeloadTest {
    @Test fun oldAcceptAndRejectCannotRewriteCursorBlocksOrTrainingMax() {
        val action = PendingProgramAction(PendingProgramActionType.CONFIRM_DELOAD,"Antigua",nextBlockId="derived")
        val run = ProgramRunState("run",weekId="week",blockId="block",completedSessionIds=setOf("session"),pendingAction=action)
        val program = Program("p","Original",runState=run,powerliftingProfile=PowerliftingProfile(squatTM=123.5))
        for (accept in listOf(false,true)) {
            val result = ProgramProgressEngine.resolvePendingDeload(program,null,accept)
            assertEquals(program.copy(runState=run.copy(pendingAction=null)),result.program)
            assertFalse(result.advancedWeek)
            assertFalse(result.advancedCycle)
            assertEquals(result,ProgramProgressEngine.resolvePendingDeload(result.program,null,accept))
        }
    }
}
