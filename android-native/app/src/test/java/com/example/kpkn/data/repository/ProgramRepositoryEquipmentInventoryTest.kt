package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.equipment.EquipmentInventoryEditor
import com.example.kpkn.domain.equipment.EquipmentInventoryTemplate
import com.example.kpkn.domain.training.*
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramRepositoryEquipmentInventoryTest {
    @get:Rule val room = RoomTestRule()
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()); CatalogCompositionTestSupport.install() }
    @After fun teardown() { ProgramRepository.closeInstance(); KpknDatabase.closeInstance(); Dispatchers.resetMain() }

    private suspend fun repository(settings: Settings): ProgramRepository {
        val repo = ProgramRepository.initForTests(ApplicationProvider.getApplicationContext<Context>())
        withTimeout(10_000) { repo.isReady.first { it } }
        repo.resetAllStateSync()
        repo.updateSettings { settings }
        return repo
    }

    private fun program(native: Boolean, id: String): Program {
        val recipe = TrainingPlanRecipe(
            id = "equipment-$id", repeats = true,
            liftSlots = mapOf(LiftSlot.BENCH to "bench_press__barbell"),
            progression = ProgressionRule.CycleIncrement(upperKg = 4.0, lowerKg = 4.0),
            nativeProgression = if (native) NativeProgressionSpec(NativeProgressionStrategy.REP_RANGE_THEN_LOAD, exposuresBeforeProposal = 2) else null,
            weeks = (1..if (native) 3 else 1).map { week ->
                WeekRecipe(weekNumber = week, blockIndex = 0, days = listOf(DayRecipe(id = "day-1", label = "Banca", slots = listOf(
                    SlotRecipe(id = "slot-1", role = SlotRole.T1_MAIN, lift = LiftRef("bench_press__barbell", LiftSlot.BENCH),
                        sets = List(2) { SetRecipe(repsMin = 8, repsMax = 12, rir = 2, loadBasis = LoadBasis.RPE) },
                        restSeconds = 90, intent = SlotIntent.H),
                ))))
            },
        )
        return PlanMaterializer.materialize(Program(id, "Material $id"), recipe, CatalogCompositionTestSupport.metadata,
            profile = PowerliftingProfile(bench1RM = 80.0, benchTM = 60.0), strict = false)
    }

    private suspend fun finishNative(repo: ProgramRepository): NativeProgressionProposal {
        val program = program(true, "native-equipment")
        repo.addProgramNow(program).getOrThrow()
        (1..2).forEach { week ->
            val session = NativeProgressionTestSupport.sessionOf(program, week)
            repo.finalizeWorkout(NativeProgressionTestSupport.completedLog(program, session.id, "material-$week", week, loadKg = { 60.0 }))
        }
        val persisted = repo.databaseForTests().programDao().getById(program.id)!!.toProgram()
        return persisted.nativeProgressionProposals.single { it.kind == NativeProgressionProposalKind.INCREASE_LOAD }
    }

    @Test fun conLosMismosAjustesAutorYNativoUsanElInventarioResueltoYProponenKg() = runBlocking {
        val settings = Settings(barbellWeight = 20.0, availablePlates = listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0))
        assertNull(settings.equipmentInventory)
        val repo = repository(settings)
        val native = finishNative(repo)
        assertEquals(62.5, native.targetLoadKg!!, 0.0)
        assertTrue(native.explanation.contains("62,5 kg"))
        assertFalse(native.explanation.contains("elige una carga"))

        val authored = program(false, "authored-equipment")
        repo.addProgramNow(authored).getOrThrow()
        val week = NativeProgressionTestSupport.weeksOf(authored).single()
        val session = week.sessions.single()
        repo.finalizeWorkout(WorkoutLog(id = "autor-1", programId = authored.id, sessionId = session.id, sessionName = session.name,
            date = "2026-01-10T10:00:00Z", durationMinutes = 30, weekId = week.id,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(1, week.id), cycleNumber = 1))
        val persisted = repo.databaseForTests().programDao().getById(authored.id)!!.toProgram()
        assertEquals("60 + 4 redondeado al paso del mismo inventario", 65.0, persisted.powerliftingProfile!!.benchTM!!, 0.0)
        assertEquals(settings.resolvedEquipmentInventory(), repo.settings.value.resolvedEquipmentInventory())
    }

    @Test fun elStockExplicitoMandaSobreLosDiscosLegacyEnLaFinalizacionReal() = runBlocking {
        val inventory = EquipmentInventoryEditor.validate(EquipmentInventoryEditor.template(EquipmentInventoryTemplate.COMMERCIAL)).inventory!!
        val repo = repository(Settings(availablePlates = listOf(10.0), equipmentInventory = inventory))
        assertEquals(62.5, finishNative(repo).targetLoadKg!!, 0.0)
    }

    @Test fun disponibilidadCategoricaSinStockSigueSinInventarKg() = runBlocking {
        val repo = repository(Settings(equipmentAvailability = EquipmentAvailability()))
        assertNull(finishNative(repo).targetLoadKg)
    }

    @Test fun guardarMaterialConfirmaRoomYPreservaLosDemasAjustesYCamposLegacy() = runBlocking {
        val original = Settings(username = "Atleta", barbellWeight = 15.0, availablePlates = listOf(5.0), equipmentAvailability = EquipmentAvailability())
        val repo = repository(original)
        val inventory = EquipmentInventoryEditor.validate(EquipmentInventoryEditor.template(EquipmentInventoryTemplate.HOME)).inventory!!
        repo.saveEquipmentInventory(inventory)
        assertEquals(original.copy(equipmentInventory = inventory), repo.settings.value)
        assertEquals(original.copy(equipmentInventory = inventory), repo.databaseForTests().settingsDao().get()!!.toSettings())
    }
}
