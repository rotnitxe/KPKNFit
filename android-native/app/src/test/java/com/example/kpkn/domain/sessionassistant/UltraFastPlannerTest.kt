package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.domain.workout.SetTechniqueScope
import com.example.kpkn.domain.workout.techniqueScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Acortar sesión»: objetivo, tácticas por tipo, suelos de descanso, cardio, exclusiones y lo registrado intacto. */
class UltraFastPlannerTest {

    private fun strength(id: String, name: String, sets: Int, rest: Int, weight: Double = 60.0) = Exercise(
        id = id, name = name, restTime = rest,
        sets = (0 until sets).map { ExerciseSet(id = "${id}_s$it", targetReps = 10, weight = weight) },
    )

    private fun cardio(id: String, name: String = "Elíptica", minutes: Int = 20) = Exercise(
        id = id, name = name, sets = listOf(ExerciseSet(id = "${id}_s0")),
        cardioDetails = CardioDetails(type = CardioType.ELLIPTICAL, targetDurationSeconds = minutes * 60),
    )

    private fun info(name: String, equipment: String, type: String, muscle: String, force: String? = null) = ExerciseMuscleInfo(
        id = name.lowercase(), name = name, equipment = equipment, type = type, force = force,
        involvedMuscles = listOf(InvolvedMuscle(muscle = muscle, role = MuscleRole.PRIMARY)),
    )

    private val squat = strength("squat", "Sentadilla barra alta", 4, 180, weight = 100.0)
    private val press = strength("press", "Press inclinado mancuernas", 4, 120, weight = 26.0)
    private val row = strength("row", "Remo en máquina", 4, 120)
    private val curl = strength("curl", "Curl polea baja", 4, 90, weight = 20.0)
    private val laterals = strength("lat", "Elevaciones laterales máquina", 4, 90, weight = 15.0)
    private val elliptical = cardio("cardio")

    private val index = mapOf(
        "sentadilla barra alta" to info("Sentadilla barra alta", "barra", "Básico", "Cuádriceps"),
        "press inclinado mancuernas" to info("Press inclinado mancuernas", "mancuerna", "Básico", "Pectorales"),
        "remo en máquina" to info("Remo en máquina", "máquina", "Básico", "Dorsales"),
        "curl polea baja" to info("Curl polea baja", "polea", "Aislamiento", "Bíceps"),
        "elevaciones laterales máquina" to info("Elevaciones laterales máquina", "máquina", "Aislamiento", "Deltoides"),
    )

    private fun fullSession() = Session(id = "s", name = "Full", exercises = listOf(squat, press, row, curl, laterals, elliptical))

    private fun UltraFastApplyResult.exercise(id: String) = transformedExercises.first { it.id == id }

    // ── Objetivos ────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun goalOf10MinutesSavesAtLeastTenAndLeavesTheMainLiftAndCardioAlone() {
        val result = UltraFastPlanner.plan(fullSession(), index, goal = ShortenGoal(10))
        val preview = result.preview
        assertTrue("ahorra al menos 10 min: ${preview.savedSeconds}", preview.savedSeconds >= 600)
        assertTrue(preview.targetReached)
        assertEquals(ShortenGoal(10), preview.goal)
        assertEquals("la principal conserva sus series", 4, result.exercise("squat").sets.size)
        assertEquals("el cardio no se toca para −10", elliptical, result.exercise("cardio"))
        assertTrue(result.removedExerciseIds.isEmpty())
        assertEquals(preview.beforeSeconds - preview.afterSeconds, preview.savedSeconds)
    }

    @Test
    fun goalOf20MinutesSavesMoreThan10AndStillKeepsTheCardio() {
        val ten = UltraFastPlanner.plan(fullSession(), index, goal = ShortenGoal(10)).preview
        val twenty = UltraFastPlanner.plan(fullSession(), index, goal = ShortenGoal(20))
        assertTrue("ahorra al menos 20 min: ${twenty.preview.savedSeconds}", twenty.preview.savedSeconds >= 1200)
        assertTrue(twenty.preview.savedSeconds > ten.savedSeconds)
        assertTrue(twenty.preview.targetReached)
        assertTrue("antes de quitar el cardio se acortan otras cosas", twenty.removedExerciseIds.isEmpty())
    }

    @Test
    fun minimumAppliesEveryTacticAndRemovesTheAccessoryCardio() {
        val result = UltraFastPlanner.plan(fullSession(), index, goal = ShortenGoal.MINIMUM)
        assertEquals(setOf("cardio"), result.removedExerciseIds)
        assertTrue(result.transformedExercises.none { it.id == "cardio" })
        val item = result.preview.items.first { it.itemId == "cardio" }
        assertEquals("Quitar de hoy", item.detail)
        assertTrue(item.removesExercise)
        assertEquals(result.preview.maxSavedSeconds, result.preview.savedSeconds)
        assertEquals(2, result.exercise("squat").sets.size)
    }

    @Test
    fun theEngineProposesAGoalFromTheSessionLimitOrFromWhatIsLeft() {
        assertEquals(ShortenGoal(30), UltraFastPlanner.defaultGoal(3600, 3000, elapsedSeconds = 1800, limitMinutes = 60))
        assertEquals(ShortenGoal(15), UltraFastPlanner.defaultGoal(3600, 3000, elapsedSeconds = 0, limitMinutes = null))
        assertEquals("si no se alcanza, lo mínimo eficaz", ShortenGoal.MINIMUM,
            UltraFastPlanner.defaultGoal(3600, 600, elapsedSeconds = 0, limitMinutes = null))
        assertEquals(listOf(ShortenGoal(10), ShortenGoal(15), ShortenGoal(20), ShortenGoal.MINIMUM),
            UltraFastPlanner.goalOptions(ShortenGoal(15), 3000))
        assertEquals("solo objetivos alcanzables", listOf(ShortenGoal(5), ShortenGoal.MINIMUM),
            UltraFastPlanner.goalOptions(ShortenGoal.MINIMUM, 7 * 60))

        val proposed = UltraFastPlanner.plan(fullSession(), index).preview
        assertNotNull(proposed.goal)
        assertTrue("el objetivo propuesto está entre las píldoras", proposed.goal in proposed.goalOptions)
        assertEquals(ShortenGoal.MINIMUM, proposed.goalOptions.last())
    }

    // ── Tácticas por tipo ────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun heavyCompoundsLoseLowValueSetsWhileIsolationAndMachinesGetDropSets() {
        val bench = strength("bench", "Press de Banca Plano", 4, 180, weight = 80.0)
            .copy(exerciseDbId = "bench_press__barbell", catalogConfigurationId = "bench_press__barbell")
        val session = Session(id = "s", name = "A", exercises = listOf(squat, bench, press, curl))
        val result = UltraFastPlanner.plan(session, index, goal = ShortenGoal.MINIMUM)

        assertEquals(ShortenRole.HEAVY_COMPOUND, UltraFastPlanner.roleOf(squat, index["sentadilla barra alta"]))
        assertEquals(ShortenRole.COMPOUND, UltraFastPlanner.roleOf(press, index["press inclinado mancuernas"]))
        assertEquals(ShortenRole.ISOLATION, UltraFastPlanner.roleOf(curl, index["curl polea baja"]))

        val squatAfter = result.exercise("squat")
        assertEquals(squat.sets.take(2).map { it.id }, squatAfter.sets.map { it.id })
        assertTrue("la sentadilla no recibe técnicas", squatAfter.sets.none { it.isDropSet || it.isRestPause })

        val benchAfter = result.exercise("bench")
        assertEquals(2, benchAfter.sets.size)
        assertTrue("banca: rest-pause en la última", benchAfter.sets.last().isRestPause)
        assertFalse(benchAfter.sets.first().isRestPause)

        val curlAfter = result.exercise("curl")
        assertEquals(curl.sets.take(2).map { it.id }, curlAfter.sets.map { it.id })
        val drop = curlAfter.sets.last()
        assertTrue("polea: drop-set en la última", drop.isDropSet)
        assertEquals(SetTechniqueScope.STACKED_ON_SET, drop.techniqueScope())
        assertEquals("2", drop.plannedIntensityTechniques.first { it.type == TechniqueType.DROP_SET }.params["count"])
        assertFalse(curlAfter.sets.first().isDropSet)

        val pressAfter = result.exercise("press")
        assertTrue("mancuernas: drop-set en la última", pressAfter.sets.last().isDropSet)
        val curlLine = result.preview.items.first { it.itemId == "curl" }
        assertTrue(curlLine.detail, curlLine.detail.contains("4 → 2 series"))
        assertTrue(curlLine.detail, curlLine.detail.contains("última en drop-set"))
        assertTrue(curlLine.detail, curlLine.detail.contains("descanso 1:30 → 0:45"))
    }

    @Test
    fun aSmallGoalTouchesRestsAndIsolationBeforeTheMainLift() {
        val result = UltraFastPlanner.plan(Session(id = "s", name = "A", exercises = listOf(squat, curl, laterals)), index,
            goal = ShortenGoal(3))
        assertTrue(result.preview.savedSeconds >= 180)
        assertEquals("la sentadilla queda igual", squat, result.exercise("squat"))
    }

    @Test
    fun restsAreShortenedWithAFloorPerType() {
        assertEquals(120, UltraFastPlanner.shortenedRestSeconds(120, ShortenRole.HEAVY_COMPOUND))
        assertEquals(135, UltraFastPlanner.shortenedRestSeconds(180, ShortenRole.HEAVY_COMPOUND))
        assertEquals(75, UltraFastPlanner.shortenedRestSeconds(90, ShortenRole.COMPOUND))
        assertEquals(150, UltraFastPlanner.shortenedRestSeconds(240, ShortenRole.COMPOUND))
        assertEquals(45, UltraFastPlanner.shortenedRestSeconds(90, ShortenRole.ISOLATION))
        assertEquals("nunca sube un descanso ya corto", 30, UltraFastPlanner.shortenedRestSeconds(30, ShortenRole.ISOLATION))

        val shortRest = strength("shortRest", "Curl polea baja", 4, 45, weight = 20.0)
        val result = UltraFastPlanner.plan(Session(id = "s", name = "A", exercises = listOf(shortRest)), index, goal = ShortenGoal.MINIMUM)
        assertEquals(45, result.exercise("shortRest").restTime)
        val heavy = UltraFastPlanner.plan(Session(id = "s", name = "A", exercises = listOf(squat)), index, goal = ShortenGoal.MINIMUM)
        assertEquals(135, heavy.exercise("squat").restTime)
    }

    // ── Cardio ───────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun cardioIsShortenedWithAFloorAndHiitLosesRounds() {
        val steady = elliptical.cardioDetails!!
        assertEquals(15 * 60, UltraFastPlanner.shortenCardio(steady, 0.75)!!.targetDurationSeconds)
        assertEquals(10 * 60, UltraFastPlanner.shortenCardio(steady, 0.5)!!.targetDurationSeconds)
        assertNull("5 min es el suelo", UltraFastPlanner.shortenCardio(steady.copy(targetDurationSeconds = 300), 0.5))

        val hiit = CardioHiitConfig(workSeconds = 30, restSeconds = 60, rounds = 8)
        val shorter = UltraFastPlanner.shortenCardio(CardioDetails(type = CardioType.AIR_BIKE, hiit = hiit), 0.75)!!
        assertEquals(6, shorter.hiit!!.rounds)
        assertTrue(shorter.hasIntervals())

        // Solo cardio: es lo principal, se acorta pero no se quita.
        val onlyCardio = UltraFastPlanner.plan(Session(id = "s", name = "C", exercises = listOf(elliptical)), emptyMap(),
            goal = ShortenGoal.MINIMUM)
        assertTrue(onlyCardio.removedExerciseIds.isEmpty())
        assertEquals(10 * 60, onlyCardio.exercise("cardio").cardioDetails!!.targetDurationSeconds)
        assertEquals("20 → 10 min", onlyCardio.preview.items.single().detail)
    }

    @Test
    fun theCurrentCardioIsShortenedButNeverRemoved() {
        val result = UltraFastPlanner.plan(fullSession(), index, goal = ShortenGoal.MINIMUM, currentExerciseId = "cardio")
        assertTrue(result.removedExerciseIds.isEmpty())
        assertEquals(10 * 60, result.exercise("cardio").cardioDetails!!.targetDurationSeconds)
    }

    // ── Exclusiones ──────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun anExcludedExerciseStaysAsPlannedAndOthersCompensate() {
        val minimum = UltraFastPlanner.plan(fullSession(), index, goal = ShortenGoal.MINIMUM, excludedItemIds = setOf("curl", "cardio"))
        assertEquals(curl, minimum.exercise("curl"))
        assertEquals(elliptical, minimum.exercise("cardio"))
        assertTrue(minimum.preview.items.first { it.itemId == "curl" }.excluded)
        assertEquals(setOf("curl", "cardio"), minimum.preview.excludedItemIds)

        val ten = UltraFastPlanner.plan(fullSession(), index, goal = ShortenGoal(10), excludedItemIds = setOf("curl"))
        assertEquals(curl, ten.exercise("curl"))
        assertTrue("las demás compensan: ${ten.preview.savedSeconds}", ten.preview.savedSeconds >= 600)
    }

    // ── Lo registrado ────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun recordedSetsNeverChangeAndDoNotCountAsPending() {
        val logged = mapOf(
            "squat" to setOf(squat.sets[0].id, squat.sets[1].id),
            "curl" to setOf(curl.sets[0].id, curl.sets[3].id),
            "press" to press.sets.map { it.id }.toSet(),
        )
        val session = fullSession()
        val untouched = UltraFastPlanner.plan(session, index, goal = ShortenGoal.MINIMUM)
        val result = UltraFastPlanner.plan(session, index, goal = ShortenGoal.MINIMUM, protectedSetIdsByExercise = logged)

        assertTrue("lo hecho no cuenta como pendiente", result.preview.beforeSeconds < untouched.preview.beforeSeconds)
        logged.forEach { (exerciseId, ids) ->
            val original = session.allExercises().first { it.id == exerciseId }
            val after = result.exercise(exerciseId)
            ids.forEach { id -> assertEquals("$exerciseId/$id intacta", original.sets.first { it.id == id }, after.sets.first { it.id == id }) }
        }
        assertEquals("un ejercicio terminado no tiene propuesta", press, result.exercise("press"))
        assertTrue(result.preview.items.none { it.itemId == "press" })
        // Curl con dos registradas de cuatro (una fuera de orden): la mitad son dos, ya hechas; solo se quitan las pendientes.
        assertEquals(setOf(curl.sets[0].id, curl.sets[3].id), result.exercise("curl").sets.map { it.id }.toSet())
    }

    @Test
    fun anExerciseWithItsOwnTechniqueKeepsEverySeries() {
        val withDrop = curl.copy(sets = curl.sets.mapIndexed { i, set -> if (i == 3) set.withTechnique(SeriesTechnique.DROPSET) else set })
        val result = UltraFastPlanner.plan(Session(id = "s", name = "A", exercises = listOf(withDrop)), index, goal = ShortenGoal.MINIMUM)
        val after = result.exercise("curl")
        assertEquals(withDrop.sets.map { it.id }, after.sets.map { it.id })
        assertEquals(45, after.restTime)
    }

    @Test
    fun sameCableAntagonistsBecomeASuperset() {
        val extension = strength("ext", "Extension triceps polea", 3, 90, weight = 20.0)
        val curl3 = strength("curl3", "Curl polea", 3, 90, weight = 20.0)
        val cableIndex = mapOf(
            "curl polea" to info("Curl polea", "polea", "Aislamiento", "Bíceps", force = "Tirón"),
            "extension triceps polea" to info("Extension triceps polea", "polea", "Aislamiento", "Tríceps", force = "Empuje"),
        )
        val result = UltraFastPlanner.plan(Session(id = "s", name = "A", exercises = listOf(curl3, extension)), cableIndex,
            goal = ShortenGoal.MINIMUM)
        assertEquals(1, result.preview.supersets.size)
        assertTrue(result.preview.items.any { it.kind == ShortenItemKind.NEW_SUPERSET })
        val groupId = result.exercise("curl3").supersetGroupRef
        assertNotNull(groupId)
        assertEquals(groupId, result.exercise("ext").supersetGroupRef)
        assertTrue(result.supersetGroups.any { it.id == groupId })
    }
}
