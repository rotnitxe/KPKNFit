package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MeetResults
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.UnilateralTarget
import com.example.kpkn.data.models.VolumeAdvance
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.SupersetVisualPlacement
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import com.example.kpkn.data.sessions.SessionTemplateSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTemplateEngineTest {

    private fun makeSet(id: String = "set-${java.util.UUID.randomUUID()}") = ExerciseSet(
        id = id,
        targetReps = 10,
        targetRPE = 8.0,
        intensityMode = IntensityMode.RPE,
    )

    private fun makeExercise(
        id: String = "ex-${java.util.UUID.randomUUID()}",
        name: String = "Exercise",
        sets: List<ExerciseSet> = listOf(makeSet()),
    ) = Exercise(id = id, name = name, sets = sets)

    private fun makePart(
        id: String = "part-${java.util.UUID.randomUUID()}",
        name: String = "Part",
        exercises: List<Exercise> = listOf(makeExercise()),
    ) = SessionPart(id = id, name = name, exercises = exercises)

    private fun makeTemplate(
        id: String = "template-${java.util.UUID.randomUUID()}",
        name: String = "Template",
        exercises: List<Exercise> = listOf(makeExercise()),
        parts: List<SessionPart> = emptyList(),
    ) = SessionTemplate(
        id = id,
        sourceType = SessionTemplateSourceType.SYSTEM,
        name = name,
        description = "Test template",
        session = Session(
            id = "session-$id",
            name = name,
            exercises = exercises,
            parts = parts,
        ),
    )

    private fun makeTargetSession(
        exercises: List<Exercise> = emptyList(),
        parts: List<SessionPart> = emptyList(),
    ) = Session(
        id = "target-session",
        name = "Target Session",
        exercises = exercises,
        parts = parts,
    )

    @Test
    fun `applyReplace_clearsExistingExercises`() {
        val template = makeTemplate(
            exercises = listOf(makeExercise(id = "tpl-ex1", name = "Template Exercise")),
        )
        val target = makeTargetSession(
            exercises = listOf(makeExercise(id = "existing-ex1", name = "Existing Exercise")),
        )

        val result = SessionTemplateEngine.applyTemplate(
            template = template,
            targetSession = target,
            mode = SessionTemplateApplyMode.REPLACE,
        )

        assertTrue("Must have template exercises", result.exercises.isNotEmpty())
        assertTrue("Must not have existing exercises", result.exercises.none { it.id == "existing-ex1" })
        assertEquals("Template Exercise", result.exercises.first().name)
    }

    @Test
    fun `applyAppend_keepsExistingAndAddsNew`() {
        val template = makeTemplate(
            exercises = listOf(makeExercise(id = "tpl-ex1", name = "Template Exercise")),
        )
        val target = makeTargetSession(
            exercises = listOf(makeExercise(id = "existing-ex1", name = "Existing Exercise")),
        )

        val result = SessionTemplateEngine.applyTemplate(
            template = template,
            targetSession = target,
            mode = SessionTemplateApplyMode.APPEND,
        )

        assertTrue("Must have both existing and template exercises", result.exercises.size >= 2)
        assertTrue("Must keep existing", result.exercises.any { it.name == "Existing Exercise" })
        assertTrue("Must add template", result.exercises.any { it.name == "Template Exercise" })
    }

    @Test
    fun `templateApplyRegeneratesIds`() {
        val template = makeTemplate(
            exercises = listOf(
                makeExercise(id = "tpl-ex1", name = "Exercise 1", sets = listOf(makeSet("tpl-set1"), makeSet("tpl-set2"))),
            ),
        )
        val target = makeTargetSession()

        val result = SessionTemplateEngine.applyTemplate(
            template = template,
            targetSession = target,
            mode = SessionTemplateApplyMode.REPLACE,
        )

        val originalIds = setOf("tpl-ex1", "tpl-set1", "tpl-set2")
        val resultExerciseIds = result.exercises.map { it.id }.toSet()
        val resultSetIds = result.exercises.flatMap { it.sets.map { s -> s.id } }.toSet()

        assertTrue("Exercise IDs must be regenerated", originalIds.intersect(resultExerciseIds).isEmpty())
        assertTrue("Set IDs must be regenerated", originalIds.intersect(resultSetIds).isEmpty())
    }

    @Test
    fun `supersetLinksPreservedWithinTemplate`() {
        val supersetId = "superset-1"
        val ex1 = makeExercise(id = "tpl-ex1", name = "Exercise 1").copy(supersetId = supersetId)
        val ex2 = makeExercise(id = "tpl-ex2", name = "Exercise 2").copy(supersetId = supersetId)
        val template = makeTemplate(exercises = listOf(ex1, ex2))
        val target = makeTargetSession()

        val result = SessionTemplateEngine.applyTemplate(
            template = template,
            targetSession = target,
            mode = SessionTemplateApplyMode.REPLACE,
        )

        val supersetIds = result.exercises.mapNotNull { it.supersetId }.distinct()
        assertEquals("Must have one superset ID", 1, supersetIds.size)
        assertFalse("Superset ID must be different from original", supersetIds.contains(supersetId))
        assertEquals("Both exercises must share the same superset ID", 2, result.exercises.count { it.supersetId == supersetIds.first() })
    }

    @Test
    fun `sessionHasContent_returnsTrueForNonEmpty`() {
        val session = makeTargetSession(exercises = listOf(makeExercise()))
        assertTrue(SessionTemplateEngine.sessionHasContent(session))
    }

    @Test
    fun `sessionHasContent_returnsFalseForEmpty`() {
        val session = makeTargetSession()
        assertFalse(SessionTemplateEngine.sessionHasContent(session))
    }

    @Test
    fun `mode-only sets are placeholders while explicit failure and unilateral targets execute`() {
        val modeOnly = makeTargetSession(
            exercises = listOf(
                makeExercise(sets = listOf(makeSet().copy(intensityMode = IntensityMode.RPE, targetRPE = null))),
            ),
        )
        assertFalse(SessionTemplateEngine.sessionHasExecutableContent(modeOnly))

        val failure = makeTargetSession(
            exercises = listOf(
                makeExercise(sets = listOf(makeSet().copy(
                    targetReps = null,
                    targetRPE = null,
                    intensityMode = IntensityMode.FAILURE,
                    isFailure = true,
                ))),
            ),
        )
        assertTrue(SessionTemplateEngine.sessionHasExecutableContent(failure))

        val unilateral = makeTargetSession(
            exercises = listOf(
                makeExercise(sets = listOf(ExerciseSet(
                    id = "unilateral-set",
                    leftTarget = UnilateralTarget(weight = 12.5, targetReps = 10),
                    rightTarget = UnilateralTarget(weight = 12.5, targetReps = 10),
                ))),
            ),
        )
        assertTrue(SessionTemplateEngine.sessionHasExecutableContent(unilateral))
    }

    @Test
    fun `complete executable gate rejects mixed valid and placeholder strength cards`() {
        val mixed = makeTargetSession(
            exercises = listOf(
                makeExercise(id = "valid-strength"),
                makeExercise(
                    id = "placeholder-strength",
                    sets = listOf(ExerciseSet(id = "mode-only", intensityMode = IntensityMode.LOAD)),
                ),
            ),
        )
        assertTrue(SessionTemplateEngine.sessionHasExecutableContent(mixed))
        assertFalse(SessionTemplateEngine.sessionHasCompleteExecutableContent(mixed))
    }

    @Test
    fun `complete executable gate rejects invalid visible modality alongside valid strength`() {
        val mixedCardio = makeTargetSession(
            exercises = listOf(makeExercise()),
            parts = listOf(
                SessionPart(
                    id = "empty-cardio-part",
                    name = "Cardio",
                    isCardioGroup = true,
                    targetDurationMinutes = 20,
                ),
            ),
        )
        assertTrue(SessionTemplateEngine.sessionHasExecutableContent(mixedCardio))
        assertFalse(SessionTemplateEngine.sessionHasCompleteExecutableContent(mixedCardio))

        val mixedMobility = makeTargetSession(
            exercises = listOf(makeExercise()),
            parts = listOf(
                SessionPart(
                    id = "empty-mobility-part",
                    name = "Movilidad",
                    isMobilityGroup = true,
                    targetDurationMinutes = 20,
                ),
            ),
        )
        assertTrue(SessionTemplateEngine.sessionHasExecutableContent(mixedMobility))
        assertFalse(SessionTemplateEngine.sessionHasCompleteExecutableContent(mixedMobility))
    }

    @Test
    fun `cloneSessionContent_generatesNewIds`() {
        val original = makeTargetSession(
            exercises = listOf(
                makeExercise(id = "orig-ex1", name = "Exercise 1", sets = listOf(makeSet("orig-set1"))),
            ),
        )

        val cloned = SessionTemplateEngine.cloneSessionContent(original)

        assertNotEquals("Cloned session ID must differ", original.id, cloned.id)
        assertTrue("Cloned exercises must have new IDs", cloned.exercises.none { it.id == "orig-ex1" })
        assertTrue("Cloned sets must have new IDs", cloned.exercises.flatMap { it.sets }.none { it.id == "orig-set1" })
    }

    @Test
    fun `canonical clone remaps multimodal references and clears execution state`() {
        val first = makeExercise(
            id = "first",
            sets = listOf(
                makeSet("set-first").copy(
                    weight = 120.0,
                    completedReps = 10,
                    completedRPE = 9.5,
                    isFailure = true,
                ),
            ),
        ).copy(supersetGroupRef = "group", supersetId = "group")
        val second = makeExercise(id = "second").copy(supersetGroupRef = "group", supersetId = "group")
        val source = Session(
            id = "source",
            name = "Mixta",
            parts = listOf(
                SessionPart(
                    id = "part",
                    name = "Fuerza",
                    exercises = listOf(first, second),
                    mobilitySeries = listOf(MobilitySeries(id = "mob", name = "90/90", sets = 2)),
                ),
            ),
            exercises = listOf(
                Exercise(
                    id = "cardio",
                    name = "Air Bike",
                    cardioDetails = CardioDetails(
                        type = CardioType.AIR_BIKE,
                        intervalBlocks = listOf(CardioIntervalBlock(id = "interval", type = CardioBlockType.WORK)),
                    ),
                ),
            ),
            supersetGroups = listOf(
                SupersetGroup(
                    id = "group",
                    exerciseOrder = listOf("first", "second"),
                    visualPlacement = SupersetVisualPlacement(partId = "part", anchorExerciseId = "first"),
                ),
            ),
            sessionB = Session(id = "variant-b", name = "B", exercises = listOf(makeExercise(id = "b-ex"))),
        )

        val clone = SessionTemplateEngine.cloneSessionContent(source)
        val clonedPart = clone.parts.single()
        val clonedFirst = clonedPart.exercises.first()
        val group = clone.supersetGroups.single()

        assertEquals(listOf(clonedPart.exercises[0].id, clonedPart.exercises[1].id), group.exerciseOrder)
        assertEquals(clonedPart.id, group.visualPlacement?.partId)
        assertEquals(clonedFirst.id, group.visualPlacement?.anchorExerciseId)
        assertTrue(clonedPart.mobilitySeries.single().id != "mob")
        assertTrue(clone.exercises.single().cardioDetails!!.intervalBlocks.single().id != "interval")
        assertTrue(clone.sessionB?.id != "variant-b")
        val clonedSet = clonedFirst.sets.single()
        assertEquals(null, clonedSet.weight)
        assertEquals(null, clonedSet.completedReps)
        assertEquals(null, clonedSet.completedRPE)
        assertFalse(clonedSet.isFailure)
    }

    @Test
    fun `week duplicate and progression seed clear meet links and volume advances recursively`() {
        fun runtimeSession(id: String) = Session(
            id = id,
            name = id,
            exercises = listOf(makeExercise(id = "$id-exercise")),
            meetBodyweight = 82.5,
            meetResults = MeetResults(placement = "1", total = 600.0),
            competitionRecordId = "$id-record",
            competitionKeyDateId = "$id-key-date",
            volumeAdvances = listOf(VolumeAdvance(id = "$id-volume")),
        )
        val source = runtimeSession("source").copy(
            sessionB = runtimeSession("nested-b"),
            sessionC = runtimeSession("nested-c"),
            sessionD = runtimeSession("nested-d"),
        )

        listOf(SessionClonePurpose.WEEK_DUPLICATE, SessionClonePurpose.PROGRESSION_SEED).forEach { purpose ->
            val cloned = SessionTemplateEngine.cloneSessionContent(source, purpose)
            listOfNotNull(cloned, cloned.sessionB, cloned.sessionC, cloned.sessionD).forEach { session ->
                assertEquals(null, session.meetBodyweight)
                assertEquals(null, session.meetResults)
                assertEquals(null, session.competitionRecordId)
                assertEquals(null, session.competitionKeyDateId)
                assertTrue(session.volumeAdvances.isEmpty())
                assertTrue(session.exercises.isNotEmpty())
            }
        }
    }

    @Test
    fun `template storage clone drops pending volume advances`() {
        val source = makeTargetSession(
            exercises = listOf(makeExercise()),
        ).copy(
            volumeAdvances = listOf(VolumeAdvance(id = "pending-volume")),
        )

        val stored = SessionTemplateEngine.cloneForTemplateStorage(source)

        assertTrue(stored.volumeAdvances.isEmpty())
    }

    @Test
    fun `cloned unilateral targets do not retain working weights`() {
        val source = makeTargetSession(
            exercises = listOf(
                makeExercise(
                    sets = listOf(ExerciseSet(
                        id = "weighted-unilateral",
                        targetReps = 8,
                        weight = 40.0,
                        leftTarget = UnilateralTarget(weight = 20.0, targetReps = 8),
                        rightTarget = UnilateralTarget(weight = 22.0, targetReps = 8),
                    )),
                ),
            ),
        )
        val clonedSet = SessionTemplateEngine.cloneForTemplateStorage(source)
            .exercises.single().sets.single()

        assertEquals(null, clonedSet.weight)
        assertEquals(null, clonedSet.leftTarget?.weight)
        assertEquals(null, clonedSet.rightTarget?.weight)
    }

    @Test
    fun `applyAppend_withParts_addsPartsCorrectly`() {
        val template = makeTemplate(
            parts = listOf(makePart(id = "tpl-part1", name = "Template Part")),
        )
        val target = makeTargetSession(
            parts = listOf(makePart(id = "existing-part1", name = "Existing Part")),
        )

        val result = SessionTemplateEngine.applyTemplate(
            template = template,
            targetSession = target,
            mode = SessionTemplateApplyMode.APPEND,
        )

        assertTrue("Must have both existing and template parts", result.parts.size >= 2)
        assertTrue("Must keep existing part", result.parts.any { it.name == "Existing Part" })
        assertTrue("Must add template part", result.parts.any { it.name == "Template Part" })
    }
}
