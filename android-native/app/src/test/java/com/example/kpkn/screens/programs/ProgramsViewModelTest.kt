package com.example.kpkn.screens.programs

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.programModeFor
import com.example.kpkn.data.programs.programNameFor
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProgramsViewModelTest {

    @get:Rule
    val roomRule = RoomTestRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private var nextViewModelKey = 0

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        // El repositorio de plantillas que crea el VM es un singleton: que no llegue vivo desde otra prueba.
        SessionTemplateRepository.resetForTests()
        val repository = ProgramRepository.initForTests(context)
        // Y que lea la base en memoria de ESTA prueba, no el kpkn.db del disco.
        setDatabaseSingletonForTest(repository.databaseForTests())
        withTimeout(10_000) {
            while (!ProgramRepository.getInstance().isReady.value) delay(25)
        }
        ProgramRepository.getInstance().resetAllStateSync()
    }

    @After
    fun tearDown() {
        try {
            // Primero los VM (cancela su viewModelScope), luego el repositorio de plantillas y solo al final la base.
            viewModelStore.clear()
            SessionTemplateRepository.resetForTests()
            ProgramRepository.closeInstance()
        } finally {
            setDatabaseSingletonForTest(null)
            Dispatchers.resetMain()
        }
    }

    /** Cada VM va al store de la prueba: [tearDown] lo limpia antes de cerrar la base. */
    private fun createViewModel(): ProgramsViewModel =
        ProgramsViewModel(ApplicationProvider.getApplicationContext()).also { viewModel ->
            viewModelStore.put("programs-${nextViewModelKey++}", viewModel)
        }

    private fun setDatabaseSingletonForTest(database: KpknDatabase?) {
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
        }.set(null, database)
    }

    @Test
    fun getProgramStats_excludes_loop_weeks() {
        val vm = createViewModel()
        val stats = vm.getProgramStats(
            Program(
                id = "p",
                name = "P",
                structure = ProgramStructure.SIMPLE,
                macrocycles = listOf(
                    Macrocycle(
                        id = "mc",
                        name = "M",
                        blocks = listOf(
                            Block(
                                id = "b",
                                name = "B",
                                mesocycles = listOf(
                                    Mesocycle(
                                        id = "m",
                                        name = "Meso",
                                        weeks = listOf(
                                            ProgramWeek(
                                                id = "w1",
                                                name = "S1",
                                                sessions = listOf(Session(id = "s1", name = "S")),
                                            ),
                                            ProgramWeek(
                                                id = "loop",
                                                name = "Loop",
                                                isLoopWeek = true,
                                                loopId = "l1",
                                                sessions = listOf(Session(id = "s2", name = "L")),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(1, stats.weeks)
        assertEquals(2, stats.sessions)
    }

    @Test
    fun createBlankProgram_uses_unified_block_names() {
        val vm = createViewModel()
        val id = vm.createBlankProgram()
        val created = ProgramRepository.getInstance().getProgramById(id)!!

        assertEquals(ProgramStructure.SIMPLE, created.structure)
        assertEquals("Macrociclo 1", created.macrocycles.first().name)
        assertEquals("Bloque 1", created.macrocycles.first().blocks.first().name)
    }

    @Test
    fun createProgramFromProtocol_keeps_editorial_dose_without_requiring_trait_answers() {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val vm = createViewModel()
        val profile = com.example.kpkn.data.models.PowerliftingProfile(
            squat1RM = 200.0,
            bench1RM = 140.0,
            deadlift1RM = 240.0,
        )
        val id = vm.createProgramFromProtocol("kpkn-native-sbd-4", profile)
        assertNotNull(id)
        val created = ProgramRepository.getInstance().getProgramById(id!!)!!
        assertNotNull(created.sourceRecipe)
        assertNull(created.athleteProfileScore)
        assertTrue(created.volumeRecommendations.isEmpty())
    }

    @Test
    fun createProgramFromProtocol_materializes_recipe_and_training_max() {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val vm = createViewModel()
        val profile = com.example.kpkn.data.models.PowerliftingProfile(
            squat1RM = 200.0,
            bench1RM = 140.0,
            deadlift1RM = 240.0,
        )
        val calibration = com.example.kpkn.screens.programdetail.components.buildVolumeCalibration(
            style = com.example.kpkn.data.models.TrainingStyle.POWERLIFTER,
            technique = 2,
            consistency = 2,
            strength = 2,
            mobility = 2,
        )
        val id = vm.createProgramFromProtocol("kpkn-native-sbd-4", profile, calibration = calibration)!!
        val created = ProgramRepository.getInstance().getProgramById(id)!!
        assertEquals(ProgramStructure.COMPLEX, created.structure)
        assertNotNull(created.sourceRecipe)
        assertTrue(created.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }.isNotEmpty())
        assertEquals(180.0, created.powerliftingProfile?.squatTM ?: -1.0, 0.01)
    }

    @Test
    fun createProgramFromProtocol_is_named_after_its_library_card_and_takes_its_discipline_mode() {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val vm = createViewModel()
        val repository = ProgramRepository.getInstance()
        val profile = com.example.kpkn.data.models.PowerliftingProfile(
            squat1RM = 200.0,
            bench1RM = 140.0,
            deadlift1RM = 240.0,
        )
        val calibration = com.example.kpkn.screens.programdetail.components.buildVolumeCalibration(
            style = com.example.kpkn.data.models.TrainingStyle.POWERLIFTER,
            technique = 2,
            consistency = 2,
            strength = 2,
            mobility = 2,
        )
        val entry = requireNotNull(PersonalizedPlanCatalog.find("protocol:kpkn-native-sbd-4"))

        // C.P6: el nombre de la ficha de la biblioteca (no el nombre crudo del protocolo) y el modo de su disciplina.
        val id = vm.createProgramFromProtocol("kpkn-native-sbd-4", profile, calibration = calibration)!!
        val created = repository.getProgramById(id)!!
        assertEquals(programNameFor(entry), created.name)
        assertEquals(programModeFor(entry), created.mode)

        // El nombre que la persona escribió sigue mandando sobre el de la ficha.
        val named = vm.createProgramFromProtocol(
            "kpkn-native-sbd-4",
            profile,
            preferredName = "  Mi bloque  ",
            calibration = calibration,
        )!!
        assertEquals("Mi bloque", repository.getProgramById(named)!!.name)
    }

    @Test
    fun createProgramFromTemplate_is_named_after_its_library_card() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val vm = createViewModel()
        val repository = ProgramRepository.getInstance()
        val calibration = com.example.kpkn.screens.programdetail.components.buildVolumeCalibration(
            style = com.example.kpkn.data.models.TrainingStyle.BODYBUILDER,
            technique = 2,
            consistency = 2,
            strength = 2,
            mobility = 2,
        )

        listOf("simple-1", "power-12-3").forEach { templateId ->
            val entry = requireNotNull(PersonalizedPlanCatalog.find("template:$templateId"))
            val id = vm.createProgramFromTemplate(templateId, calibration = calibration).getOrThrow()
            assertEquals(templateId, programNameFor(entry), repository.getProgramById(id)!!.name)
        }
    }

    @Test
    fun createProgramFromTemplate_keeps_editorial_dose_without_requiring_trait_answers() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val vm = createViewModel()
        val outcome = vm.createProgramFromTemplateGated("simple-1")
        assertTrue(outcome is ProgramsViewModel.TemplateApplyOutcome.Created)
        val created = ProgramRepository.getInstance().getProgramById((outcome as ProgramsViewModel.TemplateApplyOutcome.Created).programId)!!
        // simple-1 es la estructura en blanco de una semana; crear no inventa una rutina.
        assertEquals(1, created.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.size)
        assertNull(created.athleteProfileScore)
        assertTrue(created.volumeRecommendations.isEmpty())
    }

    @Test
    fun createProgramFromTemplate_activates_program_and_materializes_executable_sessions() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val vm = createViewModel()
        val repository = ProgramRepository.getInstance()
        val calibration = com.example.kpkn.screens.programdetail.components.buildVolumeCalibration(
            style = com.example.kpkn.data.models.TrainingStyle.BODYBUILDER,
            technique = 2,
            consistency = 2,
            strength = 2,
            mobility = 2,
        )

        listOf("simple-1", "power-12-3").forEach { templateId ->
            val result = vm.createProgramFromTemplate(templateId, calibration = calibration)
            assertTrue("create $templateId debe devolver Result.success", result.isSuccess)
            val id = result.getOrThrow()
            val created = repository.getProgramById(id)
            assertNotNull(created)
            assertEquals(id, repository.activeProgramState.value?.programId)
            com.example.kpkn.domain.training.ProgramExecutionContract.requireExecutable(created!!)
            val weeks = created.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
            assertTrue(weeks.isNotEmpty())
            assertTrue(
                "plantilla $templateId debe dejar sesiones ejecutables",
                weeks.any { week ->
                    week.sessions.any(com.example.kpkn.domain.templates.SessionTemplateEngine::sessionHasExecutableContent)
                },
            )
        }

        // También verifica que skipCalibration = true materializa sin calibración
        val uncalibratedResult = vm.createProgramFromTemplate("simple-1", skipCalibration = true)
        assertTrue("skipCalibration debe materializar", uncalibratedResult.isSuccess)
    }

    @Test
    fun createBlankProgram_numbers_after_the_highest_existing_name() {
        val repository = ProgramRepository.getInstance()
        repository.addProgram(buildNewProgram(id = "nuevo-1", name = "Nuevo programa 1"))
        repository.addProgram(buildNewProgram(id = "nuevo-3", name = "Nuevo programa 3"))
        val vm = createViewModel()

        val id = vm.createBlankProgram()

        // Contar nombres daba «Nuevo programa 3» otra vez; el mayor más uno da 4.
        assertEquals("Nuevo programa 4", repository.getProgramById(id)!!.name)
    }

    @Test
    fun inactivePrograms_leave_out_the_queued_program() = runBlocking {
        val repository = ProgramRepository.getInstance()
        repository.addProgram(buildNewProgram(id = "en-cola", name = "En cola"))
        repository.addProgram(buildNewProgram(id = "suelto", name = "Suelto"))
        val vm = createViewModel()
        vm.addToQueue("en-cola")

        val inactive = withTimeout(10_000) {
            vm.inactivePrograms.first { list -> list.any { it.id == "suelto" } }
        }

        assertEquals(listOf("suelto"), inactive.map { it.id })
        assertEquals(listOf("en-cola"), vm.programQueue.first { it.isNotEmpty() }.map { it.id })
    }
}
