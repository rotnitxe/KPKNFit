package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseDiscomfortReport
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.discomfort.DiscomfortHistory
import com.example.kpkn.domain.discomfort.DiscomfortSession
import com.example.kpkn.domain.discomfort.DiscomfortSessionExercise
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que el motor de molestias aporta al feedback (LV6, B1–B3): los chips destacados (primero lo reportado antes, luego lo frecuente en el
 * ejercicio), el aviso de una línea solo con evidencia, la fecha de «la reportaste el lunes», las entradas del motor armadas desde la
 * sesión en vivo y la caché que calcula una sola vez.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackInsightsTest {

    private val now: Instant = Instant.parse("2026-10-16T12:00:00Z")
    private val nbsp = "\u00A0"

    // ── Datos ──
    private val squatMuscles = listOf(
        InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY),
        InvolvedMuscle("Glúteos", MuscleRole.SECONDARY),
        InvolvedMuscle("Erectores Espinales", MuscleRole.STABILIZER),
    )
    private val curlMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY))

    private fun set(weight: Double = 100.0, reps: Int = 8, rir: Int? = 3) =
        CompletedSet(id = "s-$weight-$reps-$rir", weight = weight, reps = reps, rir = rir)

    private fun sets(count: Int, rir: Int? = 3) = List(count) { set(rir = rir) }

    private fun squat(sets: List<CompletedSet> = sets(3), technical: Int? = null) =
        DiscomfortSessionExercise(
            exerciseId = "squat",
            name = "Sentadilla",
            muscles = squatMuscles,
            sets = sets,
            technicalQuality = technical,
            
        )

    private fun session(vararg exercises: DiscomfortSessionExercise, focus: String? = "squat") =
        DiscomfortSession(exercises.toList(), focus)

    private fun daysAgo(days: Long): String = now.minus(days, ChronoUnit.DAYS).toString()

    private fun completed(sets: List<CompletedSet> = sets(4)) =
        CompletedExercise(exerciseId = "squat", exerciseName = "Sentadilla", effectiveMuscles = squatMuscles, sets = sets)

    private var logCounter = 0
    private fun log(days: Long, exercise: CompletedExercise, reports: List<ExerciseDiscomfortReport> = emptyList()) = WorkoutLog(
        id = "log-${logCounter++}",
        programId = "p",
        sessionId = "s",
        sessionName = "Pierna",
        date = daysAgo(days),
        durationMinutes = 60,
        completedExercises = listOf(exercise),
        postExerciseReports = reports,
    )

    private fun report(vararg ids: String) = ExerciseDiscomfortReport(
        exerciseId = "squat",
        exerciseName = "Sentadilla",
        technicalQuality = 8,
        discomfortIds = ids.toList(),
    )

    private fun history(vararg logs: WorkoutLog) = DiscomfortHistory(logs.toList(), now)

    // ── Chips destacados ──

    @Test
    fun reportedBeforeComesFirstAndTheFrequentOnesNeverRepeatIt() {
        val hist = history(log(3, completed(), reports = listOf(report("knee_patellar"))))
        val insights = FeedbackInsightsBuilder.build(session(squat()), hist)
        assertEquals(listOf("knee_patellar"), insights.reported.map { it.id })
        assertNotNull("lleva la fecha de la última vez", insights.reported.single().lastReportedAtMs)
    }

    @Test
    fun withoutHistoryTheKneeIsTheFirstFrequentForASquatAndNothingIsReported() {
        val insights = FeedbackInsightsBuilder.build(session(squat()), history())
        assertTrue(insights.reported.isEmpty())
    }

    @Test
    fun theDiscomfortsOfTheLastSessionJoinTheReportedOnesWithoutADate() {
        val insights = FeedbackInsightsBuilder.build(
            session(squat()),
            history(),
            lastSessionDiscomfortIds = listOf("lumbar", "none", "no_existe", "lumbar"),
        )
        assertEquals(listOf("lumbar"), insights.reported.map { it.id })
        assertNull("sin fecha: «de tu última sesión»", insights.reported.single().lastReportedAtMs)
    }

    @Test
    fun aDiscomfortReportedWithTheExerciseAndInTheLastSessionAppearsOnce() {
        val hist = history(log(3, completed(), reports = listOf(report("lumbar"))))
        val insights = FeedbackInsightsBuilder.build(session(squat()), hist, lastSessionDiscomfortIds = listOf("lumbar"))
        assertEquals(listOf("lumbar"), insights.reported.map { it.id })
        assertNotNull(insights.reported.single().lastReportedAtMs)
    }

    // ── El aviso del motor ──

    @Test
    fun withoutEvidenceThereIsNoNoticeAndNothingIsInvented() {
        val insights = FeedbackInsightsBuilder.build(session(squat()), history())
        assertTrue(insights.reported.isEmpty())
        assertTrue(insights.isEmpty)
    }

    // ── «la reportaste el lunes» ──

    private val utc = ZoneOffset.UTC
    private val nowMs = now.toEpochMilli()

    private fun ago(days: Long): Long = now.minus(days, ChronoUnit.DAYS).toEpochMilli()

    @Test
    fun theDateOfAReportReadsLikeAPerson() {
        assertEquals("la reportaste hoy", reportedAgoLabel(ago(0), nowMs, utc))
        assertEquals("la reportaste ayer", reportedAgoLabel(ago(1), nowMs, utc))
        // 16-oct-2026 es viernes: 3 días antes, martes; 6 días antes, sábado.
        assertEquals("la reportaste el martes", reportedAgoLabel(ago(3), nowMs, utc))
        assertEquals("la reportaste el sábado", reportedAgoLabel(ago(6), nowMs, utc))
        assertEquals("la reportaste el 9 de octubre", reportedAgoLabel(ago(7), nowMs, utc))
        assertEquals("la reportaste el 18 de septiembre", reportedAgoLabel(ago(28), nowMs, utc))
        assertEquals("de tu última sesión", reportedAgoLabel(null, nowMs, utc))
    }

    @Test
    fun theShortDateFitsInASmallChip() {
        assertEquals("hoy", reportedAgoShort(ago(0), nowMs, utc))
        assertEquals("ayer", reportedAgoShort(ago(1), nowMs, utc))
        assertEquals("martes", reportedAgoShort(ago(3), nowMs, utc))
        assertEquals("9 oct", reportedAgoShort(ago(7), nowMs, utc))
        assertEquals("18 sep", reportedAgoShort(ago(28), nowMs, utc))
        assertEquals("última sesión", reportedAgoShort(null, nowMs, utc))
    }

    // ── Las entradas del motor desde la sesión en vivo ──

    private val bench = Exercise(
        id = "bench",
        name = "Press banca",
        effectiveMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY)),
    )
    private val squatExercise = Exercise(id = "squat", name = "Sentadilla trasera", exerciseDbId = "db-squat")
    private val curlExercise = Exercise(id = "curl", name = "Curl")

    private fun inputs(
        exercises: List<Exercise>,
        completed: Map<String, CompletedSet>,
        technical: Map<String, Int> = emptyMap(),
        focus: List<String>,
        infoOf: (Exercise) -> ExerciseMuscleInfo? = { null },
    ) = FeedbackDiscomfortInputs.session(
        exercises = exercises,
        completedSets = completed,
        technicalByExerciseId = technical,
        focusExerciseIds = focus,
        infoOf = infoOf,
        nameOf = { it.name },
    )

    @Test
    fun theSessionHasTheExercisesWithSetsDoneAndTheFocusedOneEvenWithoutSets() {
        val done = mapOf("squat_0" to set(), "squat_1" to set(), "bench_0" to set())
        val onlySets = inputs(listOf(squatExercise, bench, curlExercise), done, focus = listOf("squat"))
        assertEquals(listOf("squat", "bench"), onlySets.exercises.map { it.exerciseId })
        assertEquals("squat", onlySets.focusExerciseId)
        assertEquals(2, onlySets.exercises.first().sets.size)

        val focusWithoutSets = inputs(listOf(squatExercise, curlExercise), done, focus = listOf("curl"))
        assertEquals(listOf("squat", "curl"), focusWithoutSets.exercises.map { it.exerciseId })
        assertTrue(focusWithoutSets.exercises.last().sets.isEmpty())
    }

    @Test
    fun aSupersetGroupLooksAtTheWholeSession() {
        val done = mapOf("squat_0" to set(), "bench_0" to set())
        val group = inputs(listOf(squatExercise, bench), done, focus = listOf("squat", "bench"))
        assertNull(group.focusExerciseId)
    }

    @Test
    fun unilateralSetsOfBothSidesBelongToTheirExercise() {
        val done = mapOf("squat_0_L" to set(), "squat_0_R" to set(), "squat_1_L" to set())
        val s = inputs(listOf(squatExercise), done, focus = listOf("squat"))
        assertEquals(3, s.exercises.single().sets.size)
    }

    @Test
    fun theTechniqueTheMusclesAndTheFlagsComeFromTheExercise() {
        val jump = Exercise(id = "jump", name = "Saltos al cajón", isUnilateral = true)
        val info = ExerciseMuscleInfo(
            id = "db-squat",
            name = "Sentadilla trasera",
            involvedMuscles = squatMuscles,
            
        )
        val done = mapOf("squat_0" to set(), "jump_0" to set(), "bench_0" to set())
        val s = inputs(
            exercises = listOf(squatExercise, jump, bench),
            completed = done,
            technical = mapOf("squat" to 5),
            focus = listOf("squat"),
            infoOf = { if (it.id == "squat") info else null },
        )
        val byId = s.exercises.associateBy { it.exerciseId }
        assertEquals(5, byId.getValue("squat").technicalQuality)
        assertNull(byId.getValue("bench").technicalQuality)
        assertEquals(squatMuscles, byId.getValue("squat").muscles)
        assertEquals("sus músculos efectivos mandan sobre los del catálogo", bench.effectiveMuscles, byId.getValue("bench").muscles)
        assertTrue("el modo unilateral se marca", byId.getValue("jump").isUnilateral)
        assertEquals("db-squat", byId.getValue("squat").exerciseDbId)
    }

    // ── La petición y la caché ──

    @Test
    fun theTechniqueOnlyChangesTheRequestWhereTheEngineNoticesIt() {
        fun request(note: Int) = FeedbackInsightsRequest.of(listOf("squat"), mapOf("squat" to note), completedSetsCount = 3)
        // 7 a 10 dicen lo mismo al motor (no es técnica baja): la misma petición, la misma caché.
        assertNotEquals(request(7), request(10))
        assertNotEquals(request(8), request(9))
        // 6 sí es técnica baja y 5 también, cada una con su texto.
        assertNotEquals(request(6), request(7))
        assertNotEquals(request(5), request(6))
        // Otra cantidad de series ya no es lo mismo.
        assertNotEquals(request(8), FeedbackInsightsRequest.of(listOf("squat"), mapOf("squat" to 8), completedSetsCount = 4))
    }

    @Test
    fun theEngineRunsOnceForTheSameRequestAndTheFullScreenAndTheCardShareIt() = runTest {
        var runs = 0
        val source = FeedbackInsightSource(
            inputs = {
                runs++
                session(squat()) to history()
            },
            lastSessionDiscomfortIds = { emptyList() },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val request = FeedbackInsightsRequest.of(listOf("squat"), mapOf("squat" to 8), completedSetsCount = 3)
        assertNull(source.peek(request))
        val fullScreen = source.load(request)
        val card = source.load(request)
        assertEquals(fullScreen, card)
        assertEquals(1, runs)
        assertEquals(fullScreen, source.peek(request))
        assertTrue(fullScreen.reported.isEmpty())
    }

    @Test
    fun aLowTechniqueRequestAsksTheEngineAgainAndShowsTheNotice() = runTest {
        val source = FeedbackInsightSource(
            inputs = { request ->
                val technical = request.technicalBuckets.getValue("squat")
                session(squat(technical = technical)) to history()
            },
            lastSessionDiscomfortIds = { emptyList() },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val high = source.load(FeedbackInsightsRequest.of(listOf("squat"), mapOf("squat" to 9), 3))
        val low = source.load(FeedbackInsightsRequest.of(listOf("squat"), mapOf("squat" to 4), 3))
        assertTrue(high.isEmpty)
        assertTrue(low.isEmpty)
    }

    @Test
    fun ifTheEngineFailsTheFeedbackKeepsWorkingWithoutChipsOrNotice() = runTest {
        val source = FeedbackInsightSource(
            inputs = { throw IllegalStateException("el motor falló") },
            lastSessionDiscomfortIds = { emptyList() },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val request = FeedbackInsightsRequest.of(listOf("squat"), mapOf("squat" to 8), 3)
        assertEquals(FeedbackInsights.Empty, source.load(request))
        assertNull(source.explain(request, "knee_patellar"))
        assertNull("lo que falló no se guarda como bueno", source.peek(request))
    }

    @Test
    fun theSheetExplainsADiscomfortOnlyWithEvidenceAndOnlyOnce() = runTest {
        var runs = 0
        val baseline = listOf(10L, 17L, 24L, 31L).map { log(it, completed(sets(20))) }
        val hist = history(*(baseline + log(3, completed(sets(20)))).toTypedArray())
        val source = FeedbackInsightSource(
            inputs = {
                runs++
                session(squat(sets(7))) to hist
            },
            lastSessionDiscomfortIds = { emptyList() },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val request = FeedbackInsightsRequest.of(listOf("squat"), mapOf("squat" to 8), 3)
        val knee = source.explain(request, "knee_patellar")
        assertNotNull(knee)
        assertEquals(1, knee!!.week.size)
        assertEquals(1, runs)
        source.explain(request, "knee_patellar")
        assertEquals("la segunda vez sale de la caché", 1, runs)
        // El codo no tiene nada que decir con una sentadilla: sin evidencia, nada.
        assertNull(source.explain(request, "elbow_medial"))
    }
}
