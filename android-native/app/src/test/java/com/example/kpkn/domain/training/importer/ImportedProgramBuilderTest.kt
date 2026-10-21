package com.example.kpkn.domain.training.importer

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionOrigin
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedProgramBuilderTest {

    private val draft = ImportedProgramDraft(
        title = "Mi bloque",
        weeks = listOf(
            ImportedWeek(
                index = 1,
                days = listOf(
                    ImportedDay(
                        id = "w1-d1",
                        name = "Día 1",
                        exercises = listOf(
                            ImportedExercise(
                                id = "w1-d1-e1",
                                rawName = "Sentadilla",
                                sets = 4,
                                repsMin = 6,
                                repsMax = 8,
                                loadKg = 120.0,
                                rpe = 8.0,
                                restSeconds = 180,
                                notes = "Profundidad",
                            ),
                            ImportedExercise(
                                id = "w1-d1-e2",
                                rawName = "Ejercicio libre",
                                sets = 3,
                                repsMin = 10,
                                repsMax = 10,
                                percent1Rm = 75.0,
                            ),
                        ),
                    ),
                    ImportedDay(
                        id = "w1-d2",
                        name = "Día 2",
                        exercises = listOf(
                            ImportedExercise(id = "w1-d2-e1", rawName = "Press banca", sets = 3, repsMin = 5, repsMax = 5, rir = 2),
                        ),
                    ),
                ),
            ),
            ImportedWeek(
                index = 2,
                days = listOf(
                    ImportedDay(
                        id = "w2-d1",
                        name = "Día 1",
                        exercises = listOf(
                            ImportedExercise(id = "w2-d1-e1", rawName = "Sentadilla", sets = 2, repsMin = 5, repsMax = 5),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `dos semanas dan un programa en bloque con sesiones en los dias de entreno`() {
        val program = ImportedProgramBuilder.build(draft, resolved = emptyMap(), weekdays = listOf(1, 3, 5), programId = "imp-1")

        assertEquals("Mi bloque", program.name)
        assertEquals(ProgramStructure.COMPLEX, program.structure)
        val weeks = program.macrocycles.single().blocks.single().mesocycles.single().weeks
        assertEquals(listOf("Semana 1", "Semana 2"), weeks.map { it.name })
        assertEquals(listOf("Día 1", "Día 2"), weeks[0].sessions.map { it.name })
        assertEquals(listOf(1, 5), weeks[0].sessions.map { it.dayOfWeek })
        assertEquals(listOf(1), weeks[1].sessions.map { it.dayOfWeek })
        assertEquals(SessionOrigin.USER_DRAFT, weeks[0].sessions[0].origin)
        assertEquals(1, program.startDay)
        assertEquals(setOf(1, 3, 5), program.schedulePlan?.trainingDays)
    }

    @Test
    fun `una sola semana es un programa SIMPLE`() {
        val program = ImportedProgramBuilder.build(
            draft.copy(weeks = draft.weeks.take(1)),
            resolved = emptyMap(),
            weekdays = listOf(1, 3, 5),
            programId = "imp-2",
        )

        assertEquals(ProgramStructure.SIMPLE, program.structure)
    }

    @Test
    fun `la procedencia queda marcada como importada y sin autoria`() {
        val program = ImportedProgramBuilder.build(draft, resolved = emptyMap(), weekdays = listOf(1, 3, 5), programId = "imp-3")

        assertEquals("imported:imp-3", program.planProvenance?.planId)
        assertEquals(PlanProvenanceClass.IMPORTED, program.planProvenance?.category)
        assertNull(program.author)
    }

    @Test
    fun `ejercicio sin resolver conserva su nombre libre y series con reps y carga`() {
        val program = ImportedProgramBuilder.build(draft, resolved = emptyMap(), weekdays = listOf(1, 3, 5), programId = "imp-4")

        val squat = program.firstWeekSessions()[0].exercises[0]
        assertEquals("Sentadilla", squat.name)
        assertNull(squat.catalogConfigurationId)
        assertNull(squat.catalogDefinitionId)
        assertEquals(4, squat.sets.size)
        assertEquals(RepRange(6, 8), squat.sets.first().targetRepsRange)
        assertEquals(8, squat.sets.first().targetReps)
        assertEquals(120.0, squat.sets.first().weight!!, 1e-6)
        assertEquals(8.0, squat.sets.first().targetRPE!!, 1e-6)
        assertEquals(180, squat.restTime)
        assertEquals(listOf("Profundidad"), squat.executionCues)

        val free = program.firstWeekSessions()[0].exercises[1]
        assertEquals("Ejercicio libre", free.name)
        assertNull(free.catalogConfigurationId)
        assertEquals(3, free.sets.size)
        assertEquals(RepRange(10, 10), free.sets.first().targetRepsRange)
        assertEquals(75.0, free.sets.first().targetPercentageRM!!, 1e-6)
    }

    @Test
    fun `ejercicio resuelto con catalogo toma el nombre y la identidad v2`() {
        val catalog = loadCatalog()

        val program = ImportedProgramBuilder.build(
            draft,
            resolved = mapOf("Sentadilla" to "high_bar_back_squat__barbell"),
            weekdays = listOf(1, 3, 5),
            programId = "imp-5",
            catalog = catalog,
        )

        val squat = program.firstWeekSessions()[0].exercises[0]
        assertEquals("Sentadilla Trasera con Barra Alta", squat.name)
        assertEquals("high_bar_back_squat__barbell", squat.catalogConfigurationId)
        assertEquals("high_bar_back_squat", squat.catalogDefinitionId)
        assertEquals("high_bar_back_squat__barbell", squat.exerciseId)
        assertEquals(catalog.catalogRevision, squat.catalogRevision)
    }

    @Test
    fun `sin catalogo una configuracion resuelta no cambia el ejercicio`() {
        val program = ImportedProgramBuilder.build(
            draft,
            resolved = mapOf("Sentadilla" to "high_bar_back_squat__barbell"),
            weekdays = listOf(1, 3, 5),
            programId = "imp-6",
        )

        val squat = program.firstWeekSessions()[0].exercises[0]
        assertEquals("Sentadilla", squat.name)
        assertNull(squat.catalogConfigurationId)
    }

    @Test
    fun `reparte las sesiones por igual o en circulo y sin dias no inventa fechas`() {
        assertEquals(listOf(1, 4, 6), ImportedProgramBuilder.assignWeekdays(3, listOf(1, 3, 4, 5, 6)))
        assertEquals(listOf(1, 3, 1, 3), ImportedProgramBuilder.assignWeekdays(4, listOf(1, 3)))
        assertEquals(listOf(null, null), ImportedProgramBuilder.assignWeekdays(2, emptyList()))

        val program = ImportedProgramBuilder.build(draft, resolved = emptyMap(), weekdays = emptyList(), programId = "imp-7")

        assertNull(program.startDay)
        assertNull(program.schedulePlan)
        assertTrue(program.firstWeekSessions().all { it.dayOfWeek == null })
    }

    @Test
    fun `sin titulo usa el nombre por defecto`() {
        val program = ImportedProgramBuilder.build(
            draft.copy(title = "   "),
            resolved = emptyMap(),
            weekdays = listOf(1, 3, 5),
            programId = "imp-8",
        )

        assertEquals(ImportedProgramBuilder.DEFAULT_TITLE, program.name)
    }

    @Test
    fun `un borrador editado (renombrado, parcheado, movido) genera el programa esperado`() {
        val renamed = ImportDraftEditor.rename(draft, "w1-d1-e2", "Remo con barra")
        val patched = ImportDraftEditor.update(
            renamed,
            "w1-d1-e1",
            ImportExercisePatch(sets = Change.Set(5), loadKg = Change.Set(125.0)),
        )
        val moved = ImportDraftEditor.move(patched, "w1-d2-e1", toDayId = "w1-d1", toIndex = 1)

        val program = ImportedProgramBuilder.build(moved, resolved = emptyMap(), weekdays = listOf(1, 3, 5), programId = "imp-9")

        val sessions = program.firstWeekSessions()
        assertEquals(listOf("Día 1", "Día 2"), sessions.map { it.name })
        assertEquals(listOf("Sentadilla", "Press banca", "Remo con barra"), sessions[0].exercises.map { it.name })
        assertTrue(sessions[1].exercises.isEmpty())
        val squat = sessions[0].exercises[0]
        assertEquals(5, squat.sets.size)
        assertEquals(125.0, squat.sets.first().weight!!, 1e-6)
        assertEquals(180, squat.restTime)
        assertEquals(listOf("Profundidad"), squat.executionCues)
    }

    @Test
    fun `avisos, texto de origen e ids no cambian el programa generado`() {
        val noisy = draft.copy(
            weeks = draft.weeks.map { week ->
                week.copy(
                    days = week.days.map { day ->
                        day.copy(
                            id = "x${day.id}",
                            exercises = day.exercises.map { it.copy(id = "x${it.id}", sourceText = "fila original") },
                        )
                    },
                )
            },
            warnings = listOf(
                ImportWarning(
                    id = "a1",
                    kind = ImportWarningKind.SKIPPED_ROW,
                    location = "Línea 9",
                    sourceText = "Hacer 3 vueltas",
                    message = "Línea 9: no se entiende «Hacer 3 vueltas»",
                    weekIndex = 1,
                    dayId = "w1-d1",
                    exerciseId = null,
                ),
            ),
            reviewedWarnings = setOf("a1"),
        )

        val expected = ImportedProgramBuilder.build(draft, resolved = emptyMap(), weekdays = listOf(1, 3, 5), programId = "imp-10")
        val actual = ImportedProgramBuilder.build(noisy, resolved = emptyMap(), weekdays = listOf(1, 3, 5), programId = "imp-10")

        assertEquals(expected, actual)
    }

    @Test
    fun `un nombre con dos opciones resuelto por la primera lleva la nota de la alternativa`() {
        val name = "Dominadas o jalón agarre cerrado"

        val program = ImportedProgramBuilder.build(
            oneExercise(name),
            resolved = mapOf(name to "pull_up__pronated__medium"),
            weekdays = emptyList(),
            programId = "imp-alt",
            catalog = loadCatalog(),
        )

        val exercise = program.firstWeekSessions()[0].exercises[0]
        assertEquals("Dominadas", exercise.name)
        assertEquals(listOf("Alternativa: jalón agarre cerrado."), exercise.executionCues)
    }

    @Test
    fun `sin resolver o con otra configuracion elegida no hay nota de alternativa`() {
        val name = "Dominadas o jalón agarre cerrado"

        val unresolved = ImportedProgramBuilder.build(
            oneExercise(name), resolved = emptyMap(), weekdays = emptyList(), programId = "imp-alt-2", catalog = loadCatalog(),
        )
        val chosen = ImportedProgramBuilder.build(
            oneExercise(name),
            resolved = mapOf(name to "lat_pulldown__bilateral__cable"),
            weekdays = emptyList(),
            programId = "imp-alt-3",
            catalog = loadCatalog(),
        )

        assertEquals(emptyList<String>(), unresolved.firstWeekSessions()[0].exercises[0].executionCues)
        assertEquals(emptyList<String>(), chosen.firstWeekSessions()[0].exercises[0].executionCues)
    }

    private fun oneExercise(name: String) = ImportedProgramDraft(
        title = "Alternativa",
        weeks = listOf(
            ImportedWeek(
                index = 1,
                days = listOf(
                    ImportedDay(
                        id = "w1-d1",
                        name = "Día 1",
                        exercises = listOf(
                            ImportedExercise(id = "w1-d1-e1", rawName = name, sets = 3, repsMin = 6, repsMax = 10),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun Program.firstWeekSessions(): List<Session> =
        macrocycles.single().blocks.single().mesocycles.single().weeks.first().sessions

    private fun loadCatalog(): ExerciseCatalogV2 {
        val file = listOf(
            File("src/main/assets/exercise_catalog_v2.json"),
            File("app/src/main/assets/exercise_catalog_v2.json"),
        ).first { it.exists() }
        return ExerciseCatalogV2Loader.decodeApproved(file.readText())
    }
}
