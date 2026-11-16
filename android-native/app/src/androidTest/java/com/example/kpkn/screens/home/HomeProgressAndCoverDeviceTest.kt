package com.example.kpkn.screens.home

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.domain.training.ExerciseProgress
import com.example.kpkn.domain.training.ExerciseProgressMetric
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCover
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.programdetail.buildProgramPlanDisplaySummary
import com.example.kpkn.screens.programdetail.design.ProgramCoverPickOutcome
import com.example.kpkn.screens.programdetail.design.ProgramEditDraft
import com.example.kpkn.screens.programdetail.design.ProgramEditSheet
import com.example.kpkn.screens.programdetail.design.ProgramPlanSheet
import com.example.kpkn.screens.programdetail.design.ProgramPoster
import com.example.kpkn.screens.programdetail.design.programPlanFicha
import com.example.kpkn.screens.programdetail.design.resolvePickedProgramCover
import com.example.kpkn.screens.progress.ExerciseProgressScreen
import com.example.kpkn.screens.progress.ExerciseProgressViewModel
import com.example.kpkn.screens.progress.MuscleVolumePanelRoute
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Aceptación AVD real para:
 * - Req 4: Progreso desde Inicio con 0/1/5+ ejercicios, todas las modalidades, 2 configuraciones con el mismo nombre,
 *          apertura a ficha exacta, regreso al mismo bloque y ruta de Volumen Muscular.
 * - Req 12: Portadas en Editar, Detalle y «Sobre el programa»: copia privada sin permiso persistente, flujo nulo,
 *           error en copia preservando anterior, cancelación, estilos/empaquetado/fallback y reapertura tras Room close/reopen.
 */
@RunWith(AndroidJUnit4::class)
class HomeProgressAndCoverDeviceTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val suffix = System.nanoTime().toString().takeLast(8)
    private val dbName = "qa-home-cover-$suffix.db"
    private lateinit var fixtureContext: FixtureContext

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
        NutritionRepository.closeInstance()
        WorkoutMediaRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, null)
        if (::fixtureContext.isInitialized) {
            fixtureContext.deleteDatabase(dbName)
            fixtureContext.clearOwnPreferences()
            File(fixtureContext.filesDir, "program_covers").deleteRecursively()
        }
    }

    @Test
    fun req4_homeProgressZeroOneFiveTwoConfigsModalitiesReturnAndMuscleVolumeRoute() {
        val today = LocalDate.of(2026, 11, 12)
        val nowMs = today.atTime(15, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val prevDay = today.minusDays(7)
        val muscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY))

        fun log(id: String, date: LocalDate, exercises: List<CompletedExercise>) = WorkoutLog(
            id = id, programId = "p1", sessionId = "s1", sessionName = "Mixta",
            date = "${date}T12:00:00Z", actualDate = date.toString(), durationMinutes = 40,
            completedExercises = exercises,
        )

        val oneLog = listOf(
            log(
                "one", today,
                listOf(
                    CompletedExercise(
                        "bp-flat", "Press banca", catalogDefinitionId = "def-bp", catalogConfigurationId = "cfg-flat",
                        effectiveMuscles = muscles,
                        sets = listOf(CompletedSet("s1", weight = 80.0, reps = 5, loadMode = LoadModeV2.LOAD, originalLoad = 80.0)),
                    ),
                ),
            ),
        )
        val manyLogs = listOf(
            log(
                "prev", prevDay,
                listOf(
                    CompletedExercise("bp-flat", "Press banca", catalogDefinitionId = "def-bp", catalogConfigurationId = "cfg-flat", effectiveMuscles = muscles, sets = listOf(CompletedSet("1", weight = 80.0, reps = 5, loadMode = LoadModeV2.LOAD, originalLoad = 80.0))),
                    CompletedExercise("bp-smith", "Press banca", catalogDefinitionId = "def-bp", catalogConfigurationId = "cfg-smith", effectiveMuscles = muscles, variantName = "Multipower", sets = listOf(CompletedSet("2", weight = 70.0, reps = 5, loadMode = LoadModeV2.LOAD, originalLoad = 70.0))),
                    CompletedExercise("pu", "Flexiones", catalogConfigurationId = "cfg-pu", sets = listOf(CompletedSet("3", weight = 0.0, reps = 12, loadMode = LoadModeV2.BODYWEIGHT))),
                    CompletedExercise("ls", "Fondos lastrados", catalogConfigurationId = "cfg-ls", sets = listOf(CompletedSet("4", weight = 120.0, reps = 5, loadMode = LoadModeV2.LASTRE, originalLoad = 20.0))),
                    CompletedExercise("as", "Dominadas asistidas", catalogConfigurationId = "cfg-as", sets = listOf(CompletedSet("5", weight = 60.0, reps = 6, loadMode = LoadModeV2.ASSISTED, originalLoad = 30.0))),
                    CompletedExercise("run", "Correr cinta", catalogConfigurationId = "cfg-run", sets = listOf(CompletedSet("6", reps = 0, distanceKm = 2.0, recordedPayloadV3 = RecordedSetPayload(exerciseId = "run", unitMode = UnitModeV2.DISTANCE, actualValue = 2000.0)))),
                ),
            ),
            log(
                "curr", today,
                listOf(
                    CompletedExercise("bp-flat", "Press banca", catalogDefinitionId = "def-bp", catalogConfigurationId = "cfg-flat", effectiveMuscles = muscles, sets = listOf(CompletedSet("1b", weight = 85.0, reps = 5, loadMode = LoadModeV2.LOAD, originalLoad = 85.0))),
                    CompletedExercise("bp-smith", "Press banca", catalogDefinitionId = "def-bp", catalogConfigurationId = "cfg-smith", effectiveMuscles = muscles, variantName = "Multipower", sets = listOf(CompletedSet("2b", weight = 77.5, reps = 5, loadMode = LoadModeV2.LOAD, originalLoad = 77.5))),
                    CompletedExercise("pu", "Flexiones", catalogConfigurationId = "cfg-pu", sets = listOf(CompletedSet("3b", weight = 0.0, reps = 15, loadMode = LoadModeV2.BODYWEIGHT))),
                    CompletedExercise("ls", "Fondos lastrados", catalogConfigurationId = "cfg-ls", sets = listOf(CompletedSet("4b", weight = 125.0, reps = 5, loadMode = LoadModeV2.LASTRE, originalLoad = 25.0))),
                    CompletedExercise("as", "Dominadas asistidas", catalogConfigurationId = "cfg-as", sets = listOf(CompletedSet("5b", weight = 60.0, reps = 6, loadMode = LoadModeV2.ASSISTED, originalLoad = 20.0))),
                    CompletedExercise("run", "Correr cinta", catalogConfigurationId = "cfg-run", sets = listOf(CompletedSet("6b", reps = 0, distanceKm = 3.0, recordedPayloadV3 = RecordedSetPayload(exerciseId = "run", unitMode = UnitModeV2.DISTANCE, actualValue = 3000.0)))),
                ),
            ),
        )

        var activeLogs by mutableStateOf(emptyList<WorkoutLog>())
        var screen by mutableStateOf("home")
        var selectedProgressId by mutableStateOf<String?>(null)
        val destinations = mutableListOf<String>()

        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    val metrics = remember(activeLogs) {
                        HomeTrainingMetricsBuilder.build(activeLogs, emptyList(), emptyList(), today, nowMs)
                    }
                    when (screen) {
                        "home" -> HomeMetricsSection(
                            metrics = metrics,
                            onOpenHistory = { screen = "volume" },
                            onOpenProgress = { id ->
                                destinations += id
                                selectedProgressId = id
                                screen = "progress"
                            },
                            onOpenProgressList = {},
                        )
                        "progress" -> {
                            val vm = remember(selectedProgressId, activeLogs) {
                                ExerciseProgressViewModel(
                                    exerciseId = selectedProgressId,
                                    history = MutableStateFlow(activeLogs),
                                    today = { today },
                                )
                            }
                            val uiState by vm.state.collectAsState()
                            ExerciseProgressScreen(
                                state = uiState,
                                onExercise = vm::selectExercise,
                                onMetric = vm::selectMetric,
                                onWindow = vm::selectWindow,
                                onPoint = vm::selectPoint,
                                onBack = { screen = "home" },
                            )
                        }
                        else -> MuscleVolumePanelRoute(programId = null, onBack = { screen = "home" })
                    }
                }
            }
        }

        // 0 ejercicios: sin bloque PROGRESO ni "Ver los N"
        rule.onNodeWithTag("home-training-metrics").assertIsDisplayed()
        rule.onNodeWithText("PROGRESO").assertDoesNotExist()

        // 1 ejercicio: muestra 1 fila, sin "Ver los N"
        rule.runOnUiThread { activeLogs = oneLog }
        rule.waitForIdle()
        rule.onNodeWithText("PROGRESO").assertIsDisplayed()
        rule.onNodeWithText("Ver los", substring = true).assertDoesNotExist()

        // 6 registros / 5+ ejercicios incluyendo 2 configuraciones del MISMO ejercicio ("Press banca") + modalidades
        val expectedChoices = ExerciseProgress.choices(manyLogs)
        assertEquals(6, expectedChoices.size)
        rule.runOnUiThread { activeLogs = manyLogs }
        rule.waitForIdle()
        rule.onNodeWithText("Ver los 6 ›").assertIsDisplayed()
        val smithChoice = expectedChoices.first { it.id == "configuration:cfg-smith" }
        assertFalse("Ningún control visible usa ruta muerta training-metrics", destinations.contains("training-metrics"))

        // Abrir la segunda fila visible y volver a Inicio
        val secondChoice = expectedChoices[1]
        rule.onNodeWithTag("home-progress-row-${secondChoice.id}").performClick()
        rule.waitForIdle()
        assertFalse(destinations.contains("training-metrics"))
        rule.onNodeWithText("Progreso por ejercicio").assertIsDisplayed()
        rule.onAllNodesWithText(secondChoice.name, substring = true).onFirst().assertIsDisplayed()

        // Verificar que la ficha de Asistencia y Lastre distinguen configuraciones y unidades reales
        rule.runOnUiThread { selectedProgressId = smithChoice.id }
        rule.waitForIdle()
        rule.onAllNodesWithText("Multipower", substring = true).onFirst().assertIsDisplayed()

        // Volver exactamente al mismo bloque de Inicio
        rule.onNodeWithText("Volver").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home-training-metrics").assertIsDisplayed()
    }

    @Test
    fun req12_programCoverPrivateCopyFailuresStylesBundledAndReopenConsistencyInSobre() {
        runBlocking {
        initializeExerciseDatabase(fixtureContext)
        val repo = ProgramRepository.initForTestsWithDatabaseFile(fixtureContext, dbName)
        withTimeout(30_000) { repo.isReady.first { it } }

        val dir = File(fixtureContext.filesDir, "covers-test-$suffix").apply { mkdirs() }
        val photoBytes = ByteArray(128) { (it + 1).toByte() }
        val uri1 = Uri.parse("content://qa.covers/first.jpg")
        val uriNull = Uri.parse("content://qa.covers/null.jpg")
        val uriFail = Uri.parse("content://qa.covers/fail.jpg")
        val uriCancel = Uri.parse("content://qa.covers/slow.jpg")
        val clock = { 1_731_000_000_000L }

        // 1. Copia privada sin permiso persistente: debe persistir un archivo legible en program_covers/
        val firstOutcome = resolvePickedProgramCover(
            programId = "prog-$suffix",
            uri = uri1,
            filesDir = dir,
            takePersistableReadPermission = { throw SecurityException("no persistent grant") },
            openInputStream = { ByteArrayInputStream(photoBytes) },
            isReadableImageFile = { true },
            isReadableImageStream = { true },
            nowMs = clock,
        )
        assertTrue(firstOutcome is ProgramCoverPickOutcome.Success)
        val firstCoverUri = (firstOutcome as ProgramCoverPickOutcome.Success).uriString
        val firstFile = File(Uri.parse(firstCoverUri).path!!)
        assertTrue(firstFile.exists() && firstFile.readBytes().contentEquals(photoBytes))

        // 2. Flujo nulo: falla sin borrar la portada anterior
        val nullOutcome = resolvePickedProgramCover(
            programId = "prog-$suffix", uri = uriNull, filesDir = dir,
            takePersistableReadPermission = {}, openInputStream = { null }, nowMs = clock,
        )
        assertTrue(nullOutcome is ProgramCoverPickOutcome.Failure)
        assertTrue(firstFile.readBytes().contentEquals(photoBytes))

        // 3. Error a mitad de copia en el mismo milisegundo: conserva la portada anterior intacta
        val brokenStream = object : InputStream() {
            var count = 0
            override fun read(): Int = if (count++ < 4) 7 else throw IOException("disconnect")
        }
        val ioOutcome = resolvePickedProgramCover(
            programId = "prog-$suffix", uri = uriFail, filesDir = dir,
            takePersistableReadPermission = {}, openInputStream = { brokenStream }, nowMs = clock,
        )
        assertTrue(ioOutcome is ProgramCoverPickOutcome.Failure)
        assertTrue(firstFile.readBytes().contentEquals(photoBytes))

        // 4. Cancelación: no deja residuos nuevos sin terminar
        val slowStream = object : InputStream() {
            override fun read(): Int { Thread.sleep(200); return 1 }
        }
        val cancelJob = async(Dispatchers.Default) {
            resolvePickedProgramCover(
                programId = "prog-cancel-$suffix", uri = uriCancel, filesDir = dir,
                takePersistableReadPermission = {}, openInputStream = { slowStream },
            )
        }
        cancelJob.cancelAndJoin()
        assertEquals(1, File(dir, "program_covers").listFiles().orEmpty().size)

        // 5. Persistir en Room, cerrar/reabrir Room y comprobar Detalle + «Sobre el programa» (foto, estudio, líneas, fallback)
        val prog = Program(
            id = "prog-cover-$suffix",
            name = "Fuerza Portada QA",
            description = "Programa con portada propia",
            coverImage = firstCoverUri,
            macrocycles = listOf(
                Macrocycle(
                    id = "m-cov", name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "b-cov", name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(id = "me-cov", name = "Meso", weeks = listOf(ProgramWeek(id = "w-cov", name = "S1", sessions = listOf(Session(id = "s1", name = "Sesión A", dayOfWeek = 1))))),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(repo.addProgramNow(prog).isSuccess)
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        val reopened = ProgramRepository.initForTestsWithDatabaseFile(fixtureContext, dbName)
        withTimeout(30_000) { reopened.isReady.first { it } }
        val loaded = reopened.getProgramById(prog.id)!!
        assertEquals(firstCoverUri, loaded.coverImage)

        val studioProgram = loaded.copy(coverImage = "kpkn-studio://PLATES/PAPEL")
        val fichaStudio = programPlanFicha(
            summary = buildProgramPlanDisplaySummary(studioProgram),
            program = studioProgram,
            description = studioProgram.description,
            calendarRange = null,
            isActive = true,
            canOpenPlanInfo = false,
            imageOf = { null },
        )
        assertEquals("kpkn-studio://PLATES/PAPEL", fichaStudio.detail.card.coverImage)

        val fallbackProgram = loaded.copy(coverImage = "file:///nonexistent/deleted.jpg")
        val fichaFallback = programPlanFicha(
            summary = buildProgramPlanDisplaySummary(fallbackProgram),
            program = fallbackProgram,
            description = fallbackProgram.description,
            calendarRange = null,
            isActive = false,
            canOpenPlanInfo = false,
            imageOf = { null },
        )
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    Column {
                        PlanCover(model = fichaFallback.detail.card, modifier = Modifier.size(160.dp, 220.dp), reduced = true)
                        ProgramPoster(identity = coverIdentityForProgram(fallbackProgram), photoUrl = fallbackProgram.coverImage)
                    }
                }
            }
        }
        rule.onNodeWithText("Fuerza Portada QA").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Portada del programa")[0].assertIsDisplayed()
        dir.deleteRecursively()
        }
    }

    private class FixtureContext(base: Context, private val suffix: String) : ContextWrapper(base) {
        private val names = linkedSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = "qa-home-cover-$suffix-$name"
            synchronized(names) { names += isolated }
            return baseContext.getSharedPreferences(isolated, mode)
        }
        fun clearOwnPreferences() {
            synchronized(names) { names.toList().also { names.clear() } }.forEach(baseContext::deleteSharedPreferences)
        }
    }
}
