package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopEngineTest {

    private fun simpleProgram(loop: Loop? = null) = Program(
        id = "p1",
        name = "Programa",
        structure = ProgramStructure.SIMPLE,
        loops = listOfNotNull(loop),
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso",
                                name = "Base",
                                weeks = listOf(ProgramWeek(id = "w1", name = "Semana 1")),
                            )
                        ),
                    )
                ),
            )
        ),
    )

    @Test
    fun materializeLoopWeeks_adds_real_editable_week() {
        val loop = Loop(id = "loop1", title = "Descarga", type = LoopType.DELOAD, repeatEveryXLoops = 4)

        val updated = LoopEngine.materializeLoopWeeks(simpleProgram(loop))
        val weeks = updated.macrocycles[0].blocks[0].mesocycles[0].weeks

        assertEquals(2, weeks.size)
        assertTrue(weeks[1].isLoopWeek)
        assertEquals("loop1", weeks[1].loopId)
        assertEquals("Loop Descarga · cada 4 ciclos", weeks[1].description)
        assertTrue("No synthetic empty session is added", weeks[1].sessions.isEmpty())
    }

    @Test
    fun materializeLoopWeeks_preserves_existing_loop_week_sessions() {
        val loop = Loop(id = "loop1", title = "Descarga", type = LoopType.DELOAD, repeatEveryXLoops = 4)
        val materialized = LoopEngine.materializeLoopWeeks(simpleProgram(loop))
        val withSession = materialized.copy(
            macrocycles = materialized.macrocycles.map { macro ->
                macro.copy(blocks = macro.blocks.map { block ->
                    block.copy(mesocycles = block.mesocycles.map { meso ->
                        meso.copy(weeks = meso.weeks.map { week ->
                            if (week.loopId == "loop1") week.copy(sessions = listOf(Session(id = "s1", name = "Loop"))) else week
                        })
                    })
                })
            }
        )

        val rematerialized = LoopEngine.materializeLoopWeeks(withSession)
        val loopWeek = rematerialized.macrocycles[0].blocks[0].mesocycles[0].weeks.first { it.loopId == "loop1" }

        assertEquals(listOf("s1"), loopWeek.sessions.map { it.id })
    }

    @Test
    fun deleteLoop_removes_loop_week() {
        val loop = Loop(id = "loop1", title = "Descarga", type = LoopType.DELOAD, repeatEveryXLoops = 4)
        val materialized = LoopEngine.materializeLoopWeeks(simpleProgram(loop))

        val updated = LoopEngine.deleteLoop(materialized, "loop1")

        assertFalse(updated.loops.any { it.id == "loop1" })
        assertFalse(updated.macrocycles[0].blocks[0].mesocycles[0].weeks.any { it.loopId == "loop1" })
    }

    @Test
    fun projectLoops_counts_cycle_weeks_not_program_weekDays() {
        val loop = Loop(id = "loop1", title = "Comp", type = LoopType.COMPETITION, repeatEveryXLoops = 2)
        val program = simpleProgram(loop).copy(
            simpleProgramKind = SimpleProgramKind.CYCLIC,
            weekDays = 14,
        )

        val next = LoopEngine.projectLoops(program, fromCycle = 1).first { it.cycle == 2 }

        assertEquals(7, next.daysUntil)
        assertEquals(1, next.weekInCycle)
    }

    @Test
    fun canAddNewLoop_requires_one_trainable_simple_cycle_without_competition() {
        val trainingSession = Session(
            id = "base-session",
            name = "Base",
            exercises = listOf(
                Exercise("squat", "Sentadilla", sets = listOf(ExerciseSet("set", targetReps = 5))),
            ),
        )
        val eligible = simpleProgram().copy(
            macrocycles = simpleProgram().macrocycles.map { macro -> macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.map { meso -> meso.copy(weeks = listOf(
                    ProgramWeek("w1", "Semana 1", sessions = listOf(trainingSession)),
                )) })
            }) },
        )

        assertTrue(LoopEngine.canAddNewLoop(eligible))
        assertFalse(LoopEngine.canAddNewLoop(eligible.copy(simpleProgramKind = SimpleProgramKind.CALENDARIZED)))
        assertFalse(LoopEngine.canAddNewLoop(eligible.copy(keyDates = listOf(
            ProgramKeyDate(id = "meet", title = "Competencia", type = KeyDateType.COMPETITION, startDate = "2026-12-01"),
        ))))
        assertFalse(LoopEngine.canAddNewLoop(eligible.copy(loops = listOf(
            Loop("competition", "Competición", type = LoopType.COMPETITION),
        ))))
        assertFalse(LoopEngine.canAddNewLoop(simpleProgram()))

        val multiWeek = eligible.copy(
            macrocycles = eligible.macrocycles.map { macro ->
                macro.copy(blocks = macro.blocks.map { block ->
                    block.copy(mesocycles = block.mesocycles.map { meso ->
                        meso.copy(
                            weeks = meso.weeks + ProgramWeek("w2", "Semana 2", sessions = listOf(trainingSession.copy(id = "s2"))),
                        )
                    })
                })
            },
        )
        assertFalse("Loops are exclusive to single-week routines", LoopEngine.canAddNewLoop(multiWeek))
    }
}
