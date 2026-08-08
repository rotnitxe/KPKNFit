package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class ProgramCurrentWeekResolverTest {

    @Test
    fun `Home and cyclic detail projections resolve the same current week instance`() {
        val program = Program(
            id = "p",
            name = "P",
            structure = ProgramStructure.SIMPLE,
            runState = ProgramRunState(
                runId = "run_p",
                cycleNumber = 2,
                weekId = "w2",
                weekInstanceId = ProgramProgressEngine.instanceIdFor(2, "w2"),
            ),
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "M",
                    blocks = listOf(
                        Block(
                            id = "block",
                            name = "B",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso",
                                    name = "M",
                                    weeks = listOf(
                                        ProgramWeek(id = "w1", name = "W1", sessions = listOf(Session("s1", "S1"))),
                                        ProgramWeek(id = "w2", name = "W2", sessions = listOf(Session("s2", "S2"))),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val active = ActiveProgramState(
            programId = program.id,
            status = ProgramStatus.ACTIVE,
            currentWeekId = ProgramProgressEngine.instanceIdFor(2, "w2"),
            currentWeekInstanceId = ProgramProgressEngine.instanceIdFor(2, "w2"),
            currentCycleNumber = 2,
        )

        val detail = ProgramCurrentWeekResolver.todayItem(
            program = program,
            activeState = active,
            history = emptyList(),
            today = LocalDate.of(2026, 8, 5),
            ongoing = null,
        )
        val home = HomeSessionResolver.resolveWeekLocation(
            program = program,
            active = active,
            dayOfWeek = 3,
            today = LocalDate.of(2026, 8, 5),
        )

        assertNotNull(detail)
        assertNotNull(home)
        assertEquals(detail?.templateWeekId, ProgramProgressEngine.templateWeekIdFromInstance(home?.week?.id ?: ""))
        assertEquals(detail?.instanceId, ProgramProgressEngine.instanceIdFor(2, home?.week?.id?.substringAfterLast("_") ?: ""))
    }
}
