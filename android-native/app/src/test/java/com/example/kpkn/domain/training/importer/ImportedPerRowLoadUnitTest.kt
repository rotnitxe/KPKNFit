package com.example.kpkn.domain.training.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedPerRowLoadUnitTest {
    @Test
    fun `explicit kilograms and pounds remain attached to each exercise`() {
        val draft = ProgramImportParser.parseText(
            """
                Día 1
                Peso muerto 3x5 @1100 lb
                Sentadilla 3x5 @180 kg
                Press banca 3x5 @225 lb
            """.trimIndent(),
            title = "Unidades mezcladas",
        )

        val exercises = draft.weeks.single().days.single().exercises.associateBy { it.rawName }
        assertEquals(ImportLoadUnit.KG, draft.loadUnit)
        assertEquals(1100.0, exercises.getValue("Peso muerto").loadKg)
        assertEquals(ImportLoadUnit.LB, exercises.getValue("Peso muerto").loadUnit)
        assertEquals(ImportLoadUnit.KG, exercises.getValue("Sentadilla").loadUnit)
        assertEquals(225.0, exercises.getValue("Press banca").loadKg)
        assertEquals(ImportLoadUnit.LB, exercises.getValue("Press banca").loadUnit)

        val program = ImportedProgramBuilder.build(draft, emptyMap(), weekdays = listOf(1), programId = "mixed-units")
        val setsByName = program.macrocycles.single().blocks.single().mesocycles.single().weeks.single()
            .sessions.single().exercises.associate { exercise -> exercise.name to exercise.sets.first().weight }
        assertEquals(499.0, setsByName.getValue("Peso muerto"))
        assertEquals(180.0, setsByName.getValue("Sentadilla"))
        assertEquals(102.0, setsByName.getValue("Press banca"))
    }

    @Test
    fun `deload and empty rest weeks survive parsing and build`() {
        val draft = ProgramImportParser.parseText(
            """
                Week 1
                Day 1
                Squat 3x5
                Week 2 Deload
                Day 1
                Squat 2x5
                Week 3 Rest
            """.trimIndent(),
            title = "Semanas",
        )

        assertEquals(
            listOf(
                com.example.kpkn.data.models.WeekExecutionKind.TRAINING,
                com.example.kpkn.data.models.WeekExecutionKind.DELOAD,
                com.example.kpkn.data.models.WeekExecutionKind.REST,
            ),
            draft.weeks.map { it.executionKind },
        )
        assertTrue(ImportDraftValidation.check(draft) { true }.isEmpty())

        val program = ImportedProgramBuilder.build(draft, emptyMap(), weekdays = listOf(1), programId = "week-kinds")
        val weeks = program.macrocycles.single().blocks.single().mesocycles.single().weeks
        assertEquals(draft.weeks.map { it.executionKind }, weeks.map { it.executionKind })
        assertTrue(weeks.last().sessions.isEmpty())
    }

    @Test
    fun `A1 and A2 rows become one superset in the built session`() {
        val draft = ProgramImportParser.parseText(
            """
                Día 1
                A1. Press banca 3x8
                A2. Remo con barra 3x10
            """.trimIndent(),
            title = "Superserie",
        )
        val exercises = draft.weeks.single().days.single().exercises
        assertEquals(listOf("A", "A"), exercises.map { it.supersetGroup })

        val session = ImportedProgramBuilder.build(
            draft,
            emptyMap(),
            weekdays = listOf(1),
            programId = "superset",
        ).macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.single()
        val group = session.allSupersetGroups().single()
        assertEquals(session.exercises.map { it.id }, group.exerciseOrder)
        assertTrue(session.exercises.all { it.supersetGroupRef == group.id })
    }

    @Test
    fun `explicit rest day survives as an optional empty session and is excluded from training weekdays`() {
        val draft = ProgramImportParser.parseText(
            """
                Lunes — Descanso
                Martes — Pierna
                Sentadilla 3x5
            """.trimIndent(),
            title = "Descanso explícito",
        )

        val days = draft.weeks.single().days
        assertEquals(2, days.size)
        assertTrue(days.first().isRestDay)
        assertEquals(1, days.first().dayOfWeek)
        assertEquals(1, days.count { !it.isRestDay })
        assertTrue(ImportDraftValidation.check(draft) { true }.isEmpty())

        val week = ImportedProgramBuilder.build(draft, emptyMap(), weekdays = listOf(2), programId = "rest-day")
            .macrocycles.single().blocks.single().mesocycles.single().weeks.single()
        assertEquals(2, week.sessions.size)
        assertEquals(com.example.kpkn.data.models.SessionRequirement.OPTIONAL, week.sessions.first().requirement)
        assertTrue(week.sessions.first().exercises.isEmpty())
        assertEquals(1, week.sessions.first().dayOfWeek)
        assertEquals(com.example.kpkn.data.models.SessionRequirement.REQUIRED, week.sessions.last().requirement)
    }
}
