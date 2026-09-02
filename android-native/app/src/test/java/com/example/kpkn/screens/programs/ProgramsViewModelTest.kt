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
}
