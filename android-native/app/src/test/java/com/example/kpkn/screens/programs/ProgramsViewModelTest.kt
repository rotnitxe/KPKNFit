package com.example.kpkn.screens.programs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProgramsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProgramRepository.initForTests(context)
        withTimeout(10_000) {
            while (!ProgramRepository.getInstance().isReady.value) delay(25)
        }
        ProgramRepository.getInstance().resetAllStateSync()
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    @Test
    fun getProgramStats_excludes_loop_weeks() {
        val vm = ProgramsViewModel(ApplicationProvider.getApplicationContext())
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
        val vm = ProgramsViewModel(ApplicationProvider.getApplicationContext())
        val id = vm.createBlankProgram()
        val created = ProgramRepository.getInstance().getProgramById(id)!!

        assertEquals(ProgramStructure.SIMPLE, created.structure)
        assertEquals("Macrociclo 1", created.macrocycles.first().name)
        assertEquals("Bloque 1", created.macrocycles.first().blocks.first().name)
    }

    @Test
    fun createProgramFromProtocol_requires_calibration_before_materializing() {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val vm = ProgramsViewModel(ApplicationProvider.getApplicationContext())
        val profile = com.example.kpkn.data.models.PowerliftingProfile(
            squat1RM = 200.0,
            bench1RM = 140.0,
            deadlift1RM = 240.0,
        )
        assertNull(vm.createProgramFromProtocol("kpkn-native-sbd-4", profile))
    }

    @Test
    fun createProgramFromProtocol_materializes_recipe_and_training_max() {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val vm = ProgramsViewModel(ApplicationProvider.getApplicationContext())
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
    fun createProgramFromTemplate_requires_calibration_before_materializing() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val vm = ProgramsViewModel(ApplicationProvider.getApplicationContext())
        val outcome = vm.createProgramFromTemplateGated("simple-1")
        assertEquals(ProgramsViewModel.TemplateApplyOutcome.RequiresCalibration, outcome)
    }

    @Test
    fun createProgramFromTemplate_activates_program_and_materializes_executable_sessions() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val vm = ProgramsViewModel(ApplicationProvider.getApplicationContext())
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
}
