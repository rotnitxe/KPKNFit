package com.example.kpkn.screens.workout

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.SharedExerciseLoadReferenceEntity
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.performance.StrengthModel
import com.example.kpkn.domain.training.ExerciseProgress
import com.example.kpkn.domain.training.ExerciseProgressMetric
import com.example.kpkn.domain.training.insights.MuscleVolumeAccounting
import com.example.kpkn.domain.training.insights.RpeConversion
import com.example.kpkn.domain.workout.CompletedSetLoadSemantics
import com.example.kpkn.screens.home.HomeTrainingMetricsBuilder
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.stories.workoutStoryFrom
import com.example.kpkn.screens.stories.workoutStoryPagesFrom
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.services.workout.WorkoutVoiceRuntime
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Aceptación AVD real en device/Compose/Room para:
 * - Req 2: Cierre de sesión, comparación semanal (lunes-domingo, cardio tiempo/distancia, < 60 s, BW, lastre, asistencia,
 *          unilateral, >5 músculos, esfuerzo derivado 8,5/10 vs valoración manual 4, vuelta de historia, share diferido
 *          cancelable y dialog multi-página sin descarte silencioso, medianoche, invalidación de récord).
 * - Req 13: Fixture común de 106 kg / 40 kg lastre / 20 kg asistencia / dropset / rest-pause / tiempo / irrecuperables
 *           entre ficha/progreso/Inicio/cierre/récords/historia y SharedExerciseLoadReferenceDao en Room 38.
 */
@RunWith(AndroidJUnit4::class)
class FinishStoryAndLoadSemanticsDeviceTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val suffix = System.nanoTime().toString().takeLast(8)
    private val dbName = "qa-finish-load-$suffix.db"
    private lateinit var fixtureContext: FixtureContext
    private var repository: ProgramRepository? = null

    @Before
    fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("La prueba solo puede usar KPKN Dev", "com.example.kpkn.dbg", base.packageName)
        fixtureContext = FixtureContext(base, suffix)
        NutritionRepository.closeInstance()
        WorkoutMediaRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
    }

    @After
    fun tearDown() {
        runBlocking { runCatching { WorkoutVoiceRuntime.stopEngineAndAwaitWithoutUiCallback() } }
        NutritionRepository.closeInstance()
        WorkoutMediaRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, null)
        if (::fixtureContext.isInitialized) {
            fixtureContext.deleteDatabase(dbName)
            fixtureContext.clearOwnPreferences()
        }
    }

    @Test
    fun req2_finishSheetMixedCardioEffortWeeklyChartAndStoryStatePreservation() {
        val today = LocalDate.of(2026, 11, 11) // miércoles; semana previa = 2–8 nov (lun-dom)
        val muscles6 = listOf(
            "Pectorales" to MuscleRole.PRIMARY,
            "Tríceps" to MuscleRole.SECONDARY,
            "Dorsales" to MuscleRole.PRIMARY,
            "Cuádriceps" to MuscleRole.PRIMARY,
            "Isquiosurales" to MuscleRole.SECONDARY,
            "Glúteos" to MuscleRole.PRIMARY,
        )
        fun completedLift(id: String, name: String, muscles: List<Pair<String, MuscleRole>>, sets: List<CompletedSet>) =
            CompletedExercise(
                exerciseId = id,
                exerciseName = name,
                catalogConfigurationId = "cfg-$id",
                effectiveMuscles = muscles.map { InvolvedMuscle(it.first, it.second) },
                sets = sets,
            )

        // Esfuerzo 8,5/10 · 2 de 3: RPE 8, RIR 1 (=9), una serie sin dato; valoración manual será 4
        val effortSets = listOf(
            CompletedSet("e1", weight = 100.0, reps = 5, actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 8.0),
            CompletedSet("e2", weight = 100.0, reps = 5, actualIntensityMode = IntensityMode.RIR, actualIntensityValue = 1.0),
            CompletedSet("e3", weight = 100.0, reps = 5),
        )
        // Lado unilateral = 1 serie lógica aunque registre ambos lados (sin RPE propio para conservar promedio 8,5)
        val unilateralSets = listOf(
            CompletedSet("uL", weight = 22.0, reps = 8, setIndex = 0, side = "left"),
            CompletedSet("uR", weight = 22.0, reps = 8, setIndex = 0, side = "right"),
        )
        // Cardio simultáneo distancia + tiempo, eligiendo TIEMPO (< 60 s)
        val cardioTimeSet = CompletedSet(
            id = "c1",
            reps = 0,
            timeSeconds = 45,
            distanceKm = 0.25,
            recordedPayloadV3 = RecordedSetPayload(
                actualValue = 45.0,
                exerciseId = "cinta",
                unitMode = UnitModeV2.TIME,
                durationSeconds = 45,
            ),
        )
        val currentExercises = listOf(
            completedLift("bench", "Press banca con nombre muy largo para probar recorte limpio en pantalla", muscles6.take(2), effortSets),
            completedLift("row", "Remo unilateral", muscles6.drop(2).take(1), unilateralSets),
            completedLift("squat", "Sentadilla", muscles6.drop(3), listOf(CompletedSet("s1", weight = 120.0, reps = 5, loadMode = LoadModeV2.LOAD))),
            completedLift("bw", "Rueda abdominal", listOf("Abdominales" to MuscleRole.PRIMARY), listOf(CompletedSet("bw1", weight = 106.0, reps = 12, loadMode = LoadModeV2.BODYWEIGHT))),
            CompletedExercise("cinta", "Cinta sprint", cardioDetails = CardioDetails(type = CardioType.TREADMILL), sets = listOf(cardioTimeSet)),
        )
        // Antecedente mejor en lunes 2 nov que en domingo 8 nov (control: mejor marca semanal, no más reciente)
        val priorHistory = listOf(
            WorkoutLog(
                id = "prev-best",
                programId = "p1",
                sessionId = "s1",
                sessionName = "Mixta QA",
                date = "2026-11-02T10:00:00Z",
                durationMinutes = 45,
                completedExercises = listOf(
                    completedLift("bench", "Press banca", muscles6.take(2), listOf(CompletedSet("pb", weight = 95.0, reps = 5))),
                    CompletedExercise("cinta", "Cinta sprint", cardioDetails = CardioDetails(type = CardioType.TREADMILL), sets = listOf(cardioTimeSet.copy(id = "cp1", timeSeconds = 38, recordedPayloadV3 = cardioTimeSet.recordedPayloadV3?.copy(actualValue = 38.0, durationSeconds = 38)))),
                ),
            ),
            WorkoutLog(
                id = "prev-recent-lower",
                programId = "p1",
                sessionId = "s1",
                sessionName = "Mixta QA",
                date = "2026-11-08T10:00:00Z",
                durationMinutes = 40,
                completedExercises = listOf(
                    completedLift("bench", "Press banca", muscles6.take(2), listOf(CompletedSet("pr", weight = 85.0, reps = 5))),
                ),
            ),
        )

        var errorMsg by mutableStateOf<String?>(null)
        var shareTriggeredCount by mutableIntStateOf(0)
        var shareCancelledCount by mutableIntStateOf(0)
        var confirmedWithShare by mutableStateOf<Boolean?>(null)
        var savedSharePayload: Pair<WorkoutStoryData, WorkoutStoryOptions>? = null

        rule.setContent {
            val baseConfig = LocalConfiguration.current
            val largeFontConfig = Configuration(baseConfig).apply {
                screenHeightDp = 620
                fontScale = 1.4f
            }
            CompositionLocalProvider(
                LocalWizardReducedMotion provides true,
                LocalConfiguration provides largeFontConfig,
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.4f),
            ) {
                KPKNTheme {
                    Box(Modifier.width(360.dp).height(620.dp)) {
                        RecordedSessionFinishSheet(
                            name = "Sesión mixta con título extenso en fuente grande",
                            completedExercises = currentExercises,
                            durationMinutes = 0,
                            durationSeconds = 45,
                            initialNotes = "",
                            saving = false,
                            errorMessage = errorMsg,
                            presentation = FinishPresentationContext(history = priorHistory, today = today),
                            onShareAfterSave = { data, opts -> savedSharePayload = data to opts },
                            onCancelShareAfterSave = {
                                savedSharePayload = null
                                shareCancelledCount++
                            },
                            onNotes = {},
                            onAlbum = {},
                            onConfirm = { _, _ ->
                                val pending = savedSharePayload
                                confirmedWithShare = pending != null
                                if (errorMsg == null && pending != null) {
                                    shareTriggeredCount++
                                    savedSharePayload = null
                                }
                            },
                            onDismiss = {},
                        )
                    }
                }
            }
        }

        // Esfuerzo derivado 8,5 y >5 músculos, gráfico semanal visible (incluye cardio <60 s "45 s" y "Sin dato comparable la semana anterior")
        rule.onNodeWithTag("finish-sheet").assertIsDisplayed()
        rule.onNodeWithTag("finish-effort-value").performScrollTo().assertIsDisplayed().assertTextEquals("8,5")
        rule.onNodeWithText("ESFUERZO (sRPE)").assertIsDisplayed()
        rule.onNodeWithTag("finish-deck-tab-comparativa").performScrollTo().performClick()
        rule.onNodeWithTag("finish-week-comparison").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Sin dato comparable la semana anterior", substring = true).onFirst().assertExists()
        rule.onNodeWithTag("finish-deck-tab-musculos").performScrollTo().performClick()
        rule.onNodeWithTag("finish-muscles").performScrollTo().assertIsDisplayed()

        // Notas + Valoración manual 4 + scroll + toggle Compartir al guardar
        rule.onNodeWithContentDescription("Esfuerzo 4").performScrollTo().performClick()
        rule.onNodeWithTag("finish-effort-value").performScrollTo().assertTextEquals("4")
        rule.onNodeWithTag("finish-notes-open").performScrollTo().performClick()
        rule.onNodeWithTag("finish-notes").performTextInput("Nota conservada")
        rule.onNodeWithTag("finish-notes-back").performClick()

        rule.onNodeWithTag("finish-share-switch").performScrollTo().performClick()

        // Ir a Preparar historia, volver atrás desde preview sin perder notas ni estado del switch
        rule.onNodeWithTag("finish-prepare-story").performScrollTo().performClick()
        rule.onNodeWithTag("story-sheet").assertIsDisplayed()
        rule.onNodeWithContentDescription("Volver al resumen").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("finish-sheet").assertIsDisplayed()
        rule.onNodeWithTag("finish-effort-value").performScrollTo().assertTextEquals("4")
        rule.onNodeWithTag("finish-notes-open").performScrollTo().performClick()
        rule.onNodeWithTag("finish-notes").assertTextContains("Nota conservada")
        rule.onNodeWithTag("finish-notes-back").performClick()

        // Simular error de guardado y luego apagar "Compartir al guardar" -> debe cancelar la petición diferida
        rule.runOnUiThread { errorMsg = "No se pudo guardar" }
        rule.waitForIdle()
        rule.onNodeWithTag("finish-save").performScrollTo().performClick()
        assertEquals(0, shareTriggeredCount)
        rule.onNodeWithTag("finish-share-switch").performScrollTo().performClick()
        assertTrue(shareCancelledCount >= 1)

        // Reactivar "Compartir al guardar" con guardado exitoso -> dispara 1 vez tras confirmar commit durable
        rule.runOnUiThread { errorMsg = null }
        rule.onNodeWithTag("finish-share-switch").performScrollTo().performClick()
        rule.onNodeWithTag("finish-save").performScrollTo().performClick()
        assertEquals(1, shareTriggeredCount)
        assertEquals(true, confirmedWithShare)

        // Verificación directa de invalidación de récord tras corregir serie por debajo del histórico
        val validPr = buildFinishRecords(listOf(currentExercises.first()), priorHistory)
        assertTrue("100 kg supera el récord previo de 95 kg", validPr.any { !it.isInitialReference })
        val downgraded = currentExercises.first().copy(
            sets = listOf(CompletedSet("e1", weight = 80.0, reps = 5)),
        )
        val afterCorrection = buildFinishRecords(listOf(downgraded), priorHistory)
        assertTrue("Al corregir a 80 kg debe desaparecer el récord frente a 95 kg", afterCorrection.none { !it.isInitialReference })
    }

    @Test
    fun req2_multiPageStoryOffersAllPagesCurrentPageAndSaveWithoutSilentDrop() {
        val fifteen = (1..15).map { idx ->
            val set = if (idx == 1) {
                CompletedSet("c$idx", reps = 0, timeSeconds = 900, recordedPayloadV3 = RecordedSetPayload(exerciseId = "c", unitMode = UnitModeV2.TIME, durationSeconds = 900, actualValue = 900.0))
            } else {
                CompletedSet("s$idx", weight = 60.0 + idx, reps = 6)
            }
            FinishLiftProgress("ex-$idx", if (idx == 1) "Bici estática" else "Ejercicio largo número $idx para probar paginación", set, if (idx == 1) 1 else 3, null, emptyList())
        }
        val data = WorkoutStoryData(
            sessionName = "Rutina extensa de fuerza y cardio con título de múltiples líneas",
            presentation = FinishPresentationContext(today = LocalDate.of(2026, 11, 11)),
            durationMinutes = 70,
            volume = 12400.0,
            sets = 42,
            averageRir = 2.0,
            intensity = "Alta",
            lifts = fifteen,
            sessionRpe = 8.0,
        )
        var options by mutableStateOf(WorkoutStoryOptions(exerciseList = true))
        val pagesModel = workoutStoryPagesFrom(data, options)
        assertTrue(pagesModel.size >= 3)
        assertEquals("15 min", pagesModel.first().exerciseLines.first().detail)

        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    WorkoutStorySheet(
                        data = data,
                        hazeState = HazeState(),
                        options = options,
                        onOptionsChange = { options = it },
                        shareAfterSave = false,
                        onShareAfterSaveChange = {},
                        onDismiss = {},
                    )
                }
            }
        }

        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("story-pagination", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("story-share", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("story-share-all").assertIsDisplayed()
        rule.onNodeWithTag("story-share-current").assertIsDisplayed()
        rule.onNodeWithTag("story-share-save-all").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").performClick()
    }

    @Test
    fun req2_midnightFinishControllerStoresLocalStartDateAcrossRoomAndVolume() = runBlocking {
        initializeExerciseDatabase(fixtureContext)
        val repo = ProgramRepository.initForTestsWithDatabaseFile(fixtureContext, dbName).also { repository = it }
        withTimeout(30_000) { repo.isReady.first { it } }

        val zone = ZoneId.of("America/Santiago")
        val startedAtTue2345 = ZonedDateTime.of(2026, 11, 10, 23, 45, 0, 0, zone).toInstant().toEpochMilli()
        val session = Session(
            id = "midnight-sess-$suffix",
            name = "Sesión nocturna",
            exercises = listOf(
                Exercise(id = "sq-m", name = "Sentadilla trasera", sets = listOf(ExerciseSet(id = "set-m1", targetReps = 5, weight = 100.0))),
            ),
        )
        val program = Program(
            id = "midnight-prog-$suffix",
            name = "Nocturno QA",
            macrocycles = listOf(
                Macrocycle(
                    id = "m-midnight", name = "M",
                    blocks = listOf(
                        Block(
                            id = "b-midnight", name = "B",
                            mesocycles = listOf(
                                Mesocycle(id = "me-midnight", name = "Me", weeks = listOf(ProgramWeek(id = "w-midnight", name = "W", sessions = listOf(session)))),
                            ),
                        ),
                    ),
                ),
            ),
        )
        repo.addProgramNow(program)

        // Sembrar estado en ejecución iniciado antes de medianoche antes de abrir WorkoutViewModel
        val ongoing = OngoingWorkoutState(
            programId = program.id,
            session = session,
            startTime = startedAtTue2345,
            completedSets = mapOf("sq-m_0" to CompletedSet("cs-m1", weight = 100.0, reps = 5, rir = 2)),
        )
        repo.startWorkout(ongoing)
        val vm = withContext(Dispatchers.Main) {
            WorkoutViewModel(fixtureContext, program.id, session.id, WorkoutRestAlertManager(fixtureContext))
        }
        withContext(Dispatchers.Main) {
            withTimeout(15_000) { vm.uiState.first { !it.isStartingWorkout && it.session != null } }
            vm.finishWorkout(
                notes = "Medianoche",
                fatigueLevel = 0,
                closingFeedback = SessionClosingFeedback(
                    discomforts = emptyList(),
                    sessionEffortRpe = 8.0,
                    completionInstantIso = "2026-11-11T03:10:00Z", // 00:10 AM en America/Santiago (ya día 11)
                    finishOperationId = "op-midnight-$suffix",
                ),
            )
        }
        withTimeout(15_000) {
            repo.history.first { logs -> logs.any { it.id.contains("midnight-sess-$suffix") } }
        }
        val log = repo.history.value.first { it.id.contains("midnight-sess-$suffix") }
        val expectedLocalStart = finishSessionLocalDate(startedAtTue2345, fallback = LocalDate.of(2026, 11, 11))
        assertEquals(expectedLocalStart.toString(), log.actualDate)
        assertEquals(expectedLocalStart, MuscleVolumeAccounting.recordedDate(log))
    }

    @Test
    fun req13_commonFixture106KgLastreAssistanceDropsetRestPauseAcrossAllConsumersAndRoom38() = runBlocking {
        initializeExerciseDatabase(fixtureContext)
        val repo = ProgramRepository.initForTestsWithDatabaseFile(fixtureContext, dbName).also { repository = it }
        withTimeout(30_000) { repo.isReady.first { it } }

        val today = LocalDate.of(2026, 11, 12)
        // Fixture común §4 / §13:
        // Atleta 106 kg: rueda abdominal ×5 BW (weight legado=106.0 -> 0 tonelaje, sin e1RM)
        val wheel = CompletedExercise(
            exerciseId = "wheel", exerciseName = "Rueda abdominal", catalogConfigurationId = "cfg-wheel",
            effectiveMuscles = listOf(InvolvedMuscle("Abdominales", MuscleRole.PRIMARY)),
            sets = listOf(CompletedSet("w1", weight = 106.0, reps = 5, loadMode = LoadModeV2.BODYWEIGHT)),
        )
        // Lastre 40 kg ×5 = 200 kg tonelaje
        val dipsLastre = CompletedExercise(
            exerciseId = "dips", exerciseName = "Fondos lastrados", catalogConfigurationId = "cfg-dips",
            effectiveMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY)),
            sets = listOf(CompletedSet("d1", weight = 146.0, reps = 5, loadMode = LoadModeV2.LASTRE, originalLoad = 40.0)),
        )
        // Asistencia 20 kg ×5 = 0 tonelaje externo, aparece como asistencia
        val assistPullup = CompletedExercise(
            exerciseId = "assist", exerciseName = "Dominadas asistidas", catalogConfigurationId = "cfg-assist",
            effectiveMuscles = listOf(InvolvedMuscle("Dorsales", MuscleRole.PRIMARY)),
            sets = listOf(CompletedSet("a1", weight = 86.0, reps = 5, loadMode = LoadModeV2.ASSISTED, originalLoad = 20.0)),
        )
        // Convencional 100 kg ×5 + descendente 80×4 + rest-pause ×3 = 500 + 320 + 300 = 1120 kg
        val conventionalWithTechniques = CompletedExercise(
            exerciseId = "bench", exerciseName = "Press banca", catalogConfigurationId = "cfg-bench",
            effectiveMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY)),
            sets = listOf(
                CompletedSet(
                    id = "b1", weight = 100.0, reps = 5, loadMode = LoadModeV2.LOAD, originalLoad = 100.0,
                    dropSets = listOf(DropSetData(weight = 80.0, reps = 4)),
                    restPauses = listOf(RestPauseData(restTime = 15, reps = 3)),
                ),
            ),
        )
        // Lastre irrecuperable y tiempo sin reps
        val unrecoverableLastre = CompletedExercise(
            exerciseId = "chin-old", exerciseName = "Dominadas legado", catalogConfigurationId = "cfg-chin-old",
            sets = listOf(CompletedSet("uo1", weight = 106.0, reps = 6, loadMode = LoadModeV2.LASTRE)),
        )
        val timedHold = CompletedExercise(
            exerciseId = "plank", exerciseName = "Plancha isométrica", catalogConfigurationId = "cfg-plank",
            sets = listOf(CompletedSet("pl1", weight = 0.0, reps = 0, timeSeconds = 50, recordedPayloadV3 = RecordedSetPayload(exerciseId = "plank", loadInputMode = LoadModeV2.BODYWEIGHT, unitMode = UnitModeV2.TIME, durationSeconds = 50, actualValue = 50.0))),
        )

        val log = WorkoutLog(
            id = "common-13-$suffix", programId = "p", sessionId = "s", sessionName = "Fixture 13",
            date = "${today}T12:00:00Z", actualDate = today.toString(), durationMinutes = 50,
            completedExercises = listOf(wheel, dipsLastre, assistPullup, conventionalWithTechniques, unrecoverableLastre, timedHold),
        )

        // Tonelaje: 0 (wheel) + 200 (lastre) + 0 (assist) + 1120 (conv+drop+rp) + 0 (unrecoverable) + 0 (timed) = 1320.0
        assertEquals(1320.0, CompletedSetLoadSemantics.historicalLogTonnageKg(log), 0.01)
        assertTrue(CompletedSetLoadSemantics.displaySet(wheel.sets.single()).contains("Peso corporal"))
        assertFalse(CompletedSetLoadSemantics.displaySet(wheel.sets.single()).contains("106"))
        assertTrue(CompletedSetLoadSemantics.displaySet(unrecoverableLastre.sets.single()).contains("Carga sin detalle"))

        // ExerciseProgress + Home + Finish + Records + Story
        val wheelReport = ExerciseProgress.build(listOf(log), "configuration:cfg-wheel", today = today)
        assertFalse(ExerciseProgressMetric.E1RM in wheelReport.available)
        assertFalse(ExerciseProgressMetric.VOLUME in wheelReport.available)

        val assistReport = ExerciseProgress.build(listOf(log), "configuration:cfg-assist", metric = ExerciseProgressMetric.BEST_SET, today = today)
        assertTrue(assistReport.points.single().explanation.contains("asistencia"))
        assertFalse(assistReport.points.single().higherIsBetter)

        val homeMetrics = HomeTrainingMetricsBuilder.build(listOf(log), emptyList(), emptyList(), today, today.atTime(15, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertEquals(1320.0, homeMetrics.weeks.last().tonnage, 0.01)

        val lifts = buildFinishLiftProgress(log.completedExercises, emptyList(), today)
        val finishModel = buildFinishModel("Fixture 13", log.completedExercises, 50, null, FinishPresentationContext(today = today), lifts, null, null)
        assertEquals(1320.0, finishModel.volume, 0.01)
        val story = workoutStoryFrom(
            WorkoutStoryData(
                sessionName = "Fixture 13",
                presentation = FinishPresentationContext(today = today),
                durationMinutes = 50,
                volume = finishModel.volume,
                sets = finishModel.sets,
                averageRir = null,
                intensity = "Alta",
                lifts = lifts,
                finishRecords = finishModel.recordList,
            ),
            WorkoutStoryOptions(exerciseId = "wheel"),
        )
        assertEquals("PESO CORPORAL", story.exerciseEyebrow)
        assertEquals("5", story.exerciseE1rm)
        assertEquals("reps", story.exerciseUnit)

        // Room 38 SharedExerciseLoadReferenceDao preserva referencias por contexto/modalidad tras reabrir Room
        val dbField = ProgramRepository::class.java.getDeclaredField("db").apply { isAccessible = true }
        val dao = (dbField.get(repo) as KpknDatabase).sharedExerciseLoadReferenceDao()
        dao.upsert(
            SharedExerciseLoadReferenceEntity(
                id = "ref-13-$suffix",
                exerciseKey = "bench",
                configurationId = "cfg-bench",
                tagId = null,
                unitMode = "REPS",
                loadMode = "LOAD",
                quantityConvention = "EXTERNAL_LOAD",
                kind = "ONE_RM",
                valueKg = 100.0,
                sourceAtMs = 5_000L,
                sourceId = log.id,
                payloadJson = """{"source":"qa-13"}""",
            ),
        )
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        val reopenedRepo = ProgramRepository.initForTestsWithDatabaseFile(fixtureContext, dbName).also { repository = it }
        withTimeout(30_000) { reopenedRepo.isReady.first { it } }
        val loadedRef = (dbField.get(reopenedRepo) as KpknDatabase).sharedExerciseLoadReferenceDao().getAll().firstOrNull { it.id == "ref-13-$suffix" }
        assertNotNull(loadedRef)
        assertEquals(100.0, loadedRef!!.valueKg, 0.01)
    }

    private class FixtureContext(base: Context, private val suffix: String) : ContextWrapper(base) {
        private val names = linkedSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = "qa-finish-load-$suffix-$name"
            synchronized(names) { names += isolated }
            return baseContext.getSharedPreferences(isolated, mode)
        }
        fun clearOwnPreferences() {
            synchronized(names) { names.toList().also { names.clear() } }.forEach(baseContext::deleteSharedPreferences)
        }
    }
}
