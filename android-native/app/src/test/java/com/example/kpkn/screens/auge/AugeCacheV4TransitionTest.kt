package com.example.kpkn.screens.auge

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.FoodCatalogImporter
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Transición v4: anclas explícitas; no se invoca el EMA ni se sella rendimiento suspendido. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AugeCacheV4TransitionTest {
    /** Sin importar el catálogo de alimentos: AUGE solo lee `nutritionLogs`, que arranca vacío. */
    private object NoCatalogImport : FoodCatalogImporter {
        override suspend fun importIfNeeded(
            db: KpknDatabase,
            context: Context,
            alreadyImported: Boolean,
            existingMeta: FoodImporter.ImportMetadata?,
            onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
        ): Boolean = false
    }

    private lateinit var programRepository: ProgramRepository
    private lateinit var augeDatabase: KpknDatabase
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var augeRepo: AugeRepository
    private lateinit var vm: AugeViewModel
    private lateinit var previousZone: java.util.TimeZone

    @Before
    fun setUp() {
        previousZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        NutritionRepository.initForTests(context, NoCatalogImport)
        programRepository = ProgramRepository.initForTests(context)
        assertTrue(
            "ProgramRepository no terminó de cargar",
            awaitUntil(SETTLE_MS) { programRepository.isReady.value },
        )
        // Base propia (en memoria) para AugeRepository: se publica como singleton de KpknDatabase y se reinicia el
        // singleton de AugeRepository. Aquí el repositorio se crea ANTES que el ViewModel (que reutiliza el singleton)
        // para poder sembrar filas que el primer recálculo ya vea.
        augeDatabase = KpknDatabase.createInMemory(context)
        setSingleton(KpknDatabase::class.java, augeDatabase)
        setSingleton(AugeRepository::class.java, null)
        augeRepo = AugeRepository.getInstance(context)
        viewModelStore = ViewModelStore()
    }

    @After
    fun tearDown() {
        try {
            // Cancela el viewModelScope (incluidas las corrutinas que un defecto deje colgadas).
            if (this::viewModelStore.isInitialized) viewModelStore.clear()
            setSingleton(AugeRepository::class.java, null)
            setSingleton(KpknDatabase::class.java, null)
            if (this::augeDatabase.isInitialized) augeDatabase.close()
            ProgramRepository.closeInstance()
            NutritionRepository.closeInstance()
        } finally {
            java.util.TimeZone.setDefault(previousZone)
            Dispatchers.resetMain()
        }
    }


    @Test fun tresGestosPersistenAnclasSinRepoblarEmaYReabrirConservaAvisos() {
        val anchorMs = 1_760_000_000_000L
        db { augeRepo.saveAdaptiveCache(AugeAdaptiveCache(modelUpdateNoticeShown = true, dismissedAdvisoryKeys = setOf("conservar"))) }
        programRepository.addWorkoutLog(WorkoutLog(id = "suspendido", programId = "p", sessionId = "s", sessionName = "prueba",
            date = Instant.ofEpochMilli(anchorMs - 48 * 3_600_000L).toString(), durationMinutes = 60,
            completedExercises = listOf(CompletedExercise("e", "Press banca", sets = listOf(CompletedSet("s", weight = 80.0, reps = 5, rpe = 8.0))))))
        startViewModel()
        listOf(40, 50, 60).forEachIndexed { index, value ->
            vm.applyManualBatteries(neural = value, perMuscle = mapOf("Pectorales" to value),
                manualBatteryAnchorMs = anchorMs + index, predictedNeuralBattery = 80,
                predictedMuscleBatteries = mapOf("Pectorales" to 80), sessionCnsDrain = 40.0)
            awaitWrites()
            val saved = db { augeRepo.getWellbeingLogs() }.single()
            assertEquals(anchorMs + index, saved.manualBatteryAnchorMs)
            assertEquals(80, saved.manualMuscleOverridesV2.getValue("Pectorales").automaticBatteryAtAnchor)
            assertEquals(value, saved.manualNeuralBattery)
            assertPrior()
        }
        viewModelStore.clear()
        viewModelStore = ViewModelStore()
        setSingleton(AugeRepository::class.java, null)
        augeRepo = AugeRepository.getInstance(ApplicationProvider.getApplicationContext<Context>())
        startViewModel()
        awaitWrites()
        assertPrior()
        assertTrue(db { augeRepo.getAdaptiveCache() }.modelUpdateNoticeShown)
        assertTrue("conservar" in db { augeRepo.getAdaptiveCache() }.dismissedAdvisoryKeys)
    }

    @Test fun cuatroVersionesPasanPorAugeAdaptiveCacheEntity() {
        listOf(null, 1, 2, 3).forEach { version ->
            val prefix = version?.let { "\"schemaVersion\":$it," } ?: ""
            val raw = """{$prefix"personalizedRecoveryHours":{"pectorales":55.0},"cnsRecoveryHours":90.0,"spinalRecoveryHours":110.0,
                "muscleDeltas":{"pectorales":12.0},"cnsLearningDelta":8.0,"spinalLearningDelta":-6.0,
                "cnsDrainMultiplier":1.2,"spinalDrainMultiplier":0.8,"muscleDrainMultipliers":{"pectorales":1.3},
                "totalObservations":77,"lastUpdatedMs":123,"lastPerformanceLearnLogId":"log",
                "dismissedAdvisoryKeys":["a|week"],"lastAdvisoryLevelByChannel":{"cns":"WATCH"},
                "lastAdvisoryDay":"2026-10-20","modelUpdateNoticeShown":true}"""
            val cache = com.example.kpkn.data.db.AugeAdaptiveCacheEntity(data = raw).toAdaptiveCache()
            assertEquals(4, cache.schemaVersion)
            assertTrue(cache.personalizedRecoveryHours.isEmpty())
            assertNull(cache.cnsRecoveryHours)
            assertNull(cache.spinalRecoveryHours)
            assertTrue(cache.parameterStates.isEmpty())
            assertEquals(77, cache.totalObservations)
            val encoded = cache.toEntity()
            listOf("muscleDeltas", "cnsLearningDelta", "spinalLearningDelta", "cnsDrainMultiplier", "spinalDrainMultiplier", "muscleDrainMultipliers").forEach {
                assertTrue(!encoded.data.contains(it))
            }
            assertEquals(cache, encoded.toAdaptiveCache())
        }
    }

    @Test fun guardarAvisoNoSobrescribeJsonFuturo() {
        val raw = """{"schemaVersion":5,"otraForma":"intacta"}"""
        db { augeDatabase.augeDao().upsertAdaptiveCache(com.example.kpkn.data.db.AugeAdaptiveCacheEntity(data = raw)) }
        assertEquals(AugeAdaptiveCache(), db { augeRepo.getAdaptiveCache() })
        db { augeRepo.saveAdaptiveCache(AugeAdaptiveCache(modelUpdateNoticeShown = true)) }
        assertEquals(raw, db { augeDatabase.augeDao().getAdaptiveCache() }?.data)
    }

    private fun assertPrior() {
        val cache = db { augeRepo.getAdaptiveCache() }
        assertTrue(cache.personalizedRecoveryHours.isEmpty())
        assertEquals(null, cache.cnsRecoveryHours)
        assertEquals(null, cache.spinalRecoveryHours)
        assertEquals(0, cache.totalObservations)
        assertEquals(null, cache.lastPerformanceLearnLogId)
    }

    private fun startViewModel() {
        vm = AugeViewModel(ApplicationProvider.getApplicationContext<Application>()).also { viewModelStore.put("auge-vm", it) }
        assertTrue(awaitUntil(SETTLE_MS) { !vm.snapshot.value.isLoading })
        awaitWrites()
    }

    private fun awaitWrites() {
        val mutex = AugeViewModel::class.java.getDeclaredField("augeWriteMutex").apply { isAccessible = true }.get(vm) as Mutex
        runBlocking { withTimeout(AWAIT_MS) { mutex.lock() } }
        mutex.unlock()
    }

    private fun <T> db(block: suspend () -> T): T = runBlocking { withTimeout(AWAIT_MS) { block() } }
    private fun awaitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (!condition()) {
            if (System.nanoTime() >= deadline) return false
            Thread.sleep(25)
        }
        return true
    }
    private fun setSingleton(owner: Class<*>, value: Any?) {
        owner.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, value)
    }
    private companion object {
        const val AWAIT_MS = 15_000L
        const val SETTLE_MS = 45_000L
    }
}
