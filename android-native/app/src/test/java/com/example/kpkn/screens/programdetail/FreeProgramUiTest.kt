package com.example.kpkn.screens.programdetail

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.entitlements.PlanLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class FreeProgramUiTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { ProgramRepository.closeInstance(); KpknDatabase.closeInstance(); Dispatchers.resetMain() }
    @Test fun rejectedWeekDoesNotMoveSelectionOrCreateGhostContent() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        ProfileStorage.initialize(context)
        val repo = ProgramRepository.initForTests(context) { PlanLevel.FREE }
        withTimeout(10_000) { repo.isReady.first { it } }
        val week = ProgramWeek("week", "Week")
        val meso = Mesocycle("meso", "Meso", weeks = listOf(week))
        val block = Block("block", "Block", mesocycles = listOf(meso))
        val original = Program("free-ui", "Free", macrocycles = listOf(Macrocycle("macro", "Macro", listOf(block))))
        assertTrue(repo.addProgramNow(original).isSuccess)
        val vm = ProgramDetailViewModel(original.id)
        try {
            vm.selectBlock(block.id); vm.selectWeek(week.id)
            val selected = vm.uiState.value.selectedWeekId
            vm.addWeekToSimpleProgram()
            assertEquals(selected, vm.uiState.value.selectedWeekId)
            assertEquals(block.id, vm.uiState.value.selectedBlockId)
            assertEquals(original, repo.getProgramById(original.id))
            assertNotNull(vm.uiState.value.snackbarMessage)
            val twoWeeks = meso.copy(weeks = listOf(week, week.copy(id = "week2")))
            val advancedBlock = block.copy(mesocycles = listOf(twoWeeks))
            val advancedMacro = original.macrocycles.single().copy(blocks = listOf(advancedBlock))
            assertFalse(vm.addProgramCopy(original.copy(id = "blocked", macrocycles = listOf(advancedMacro))))
            assertNull(repo.getProgramById("blocked"))
        } finally { vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
    }
}
