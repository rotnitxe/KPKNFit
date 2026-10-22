package com.example.kpkn.screens.auge

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugePerformanceContext
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import java.time.Instant
import java.time.LocalDate

/**
 * Pruebas de integración del aprendizaje de rendimiento v4 (paquete 3.4):
 * ViewModel + repositorio + Room en memoria.
 *
 * Cubre:
 * - Sesión con contexto congelado [AugePerformanceContext] aprende rendimiento, persiste posteriores en Room y sella el log.
 * - Reprocesar el mismo log no duplica el aprendizaje ni incrementa n (idempotencia persistida).
 * - Exclusiones declaradas: falta de contexto («no_context»), descarga («deload») y molestias («discomfort») sellan el log sin aprender.
 * - Serialización ida y vuelta JSON de [WorkoutLog] con y sin [AugePerformanceContext].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AugePerformanceLearningIntegrationTest {

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

    private val observedAt: Long = System.currentTimeMillis()

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
        augeDatabase = KpknDatabase.createInMemory(context)
        setSingleton(KpknDatabase::class.java, augeDatabase)
        setSingleton(AugeRepository::class.java, null)
        augeRepo = AugeRepository.getInstance(context)
        viewModelStore = ViewModelStore()
    }

    @After
    fun tearDown() {
        try {
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

    @Test
    fun performanceLearning_withContext_updatesCacheAndPersistsPosteriors() {
        // Historial de 3 sesiones comparables a 72h, 48h y 24h
        val h1 = benchLog("h1", observedAt - 72L * HOUR_MS, weight = 80.0)
        val h2 = benchLog("h2", observedAt - 48L * HOUR_MS, weight = 80.0)
        val h3 = benchLog("h3", observedAt - 24L * HOUR_MS, weight = 80.0)
        programRepository.addWorkoutLog(h1)
        programRepository.addWorkoutLog(h2)
        programRepository.addWorkoutLog(h3)

        // Sesión medida reciente (hace 1h) con contexto de rendimiento válido
        val today = benchLog(
            "today",
            observedAt - HOUR_MS,
            weight = 72.0, // menor que 80 => ν < 0, peso 1
            context = AugePerformanceContext(
                sessionStartTimeMs = observedAt - HOUR_MS - 3600_000L,
                isDeload = false,
                deloadOrigin = "hierarchy_normal",
            ),
        )
        programRepository.addWorkoutLog(today)

        startViewModel()
        awaitWrites()

        val cache = cache()
        assertEquals("today", cache.lastPerformanceLearnLogId)
        assertTrue("Debe registrar al menos una observación de rendimiento", cache.observations.isNotEmpty())
        val pecsObs = cache.observations.firstOrNull { it.parameterKey == "pectorales" }
        assertNotNull("Observación de Pectorales presente", pecsObs)
        assertEquals("e1rm", pecsObs?.source)
        assertEquals(today.id, cache.lastPerformanceLearnLogId)
    }

    @Test
    fun reprocessingSameLogThreeTimes_leavesOneRecordAndAtMostOneUpdate() {
        val h1 = benchLog("h1", observedAt - 72L * HOUR_MS, weight = 80.0)
        val h2 = benchLog("h2", observedAt - 48L * HOUR_MS, weight = 80.0)
        val h3 = benchLog("h3", observedAt - 24L * HOUR_MS, weight = 80.0)
        val today = benchLog(
            "today",
            observedAt - HOUR_MS,
            weight = 72.0,
            context = AugePerformanceContext(
                sessionStartTimeMs = observedAt - HOUR_MS - 3600_000L,
                isDeload = false,
                deloadOrigin = "hierarchy_normal",
            ),
        )
        programRepository.addWorkoutLog(h1)
        programRepository.addWorkoutLog(h2)
        programRepository.addWorkoutLog(h3)
        programRepository.addWorkoutLog(today)

        startViewModel()
        awaitWrites()

        val firstCache = cache()
        val firstObsCount = firstCache.observations.size
        val firstPecsN = firstCache.parameterStates["pectorales"]?.n ?: 0

        // Reintentos de recálculo (ej. guardar bienestar o refrescar)
        repeat(2) {
            vm.saveWellbeing(DailyWellbeingLog(id = "wb-$it", date = LocalDate.now().toString()))
            awaitWrites()
        }

        val finalCache = cache()
        assertEquals("No se duplican registros", firstObsCount, finalCache.observations.size)
        assertEquals("n no sube en reprocesos", firstPecsN, finalCache.parameterStates["pectorales"]?.n ?: 0)
        assertEquals("today", finalCache.lastPerformanceLearnLogId)
    }

    @Test
    fun excludedSession_noContext_sealsLogWithoutLearning() {
        val todayNoContext = benchLog(
            "today-legacy",
            observedAt - HOUR_MS,
            weight = 72.0,
            context = null, // Sin contexto congelado
        )
        programRepository.addWorkoutLog(todayNoContext)

        startViewModel()
        awaitWrites()

        val cache = cache()
        assertEquals("today-legacy", cache.lastPerformanceLearnLogId)
        assertTrue("Sin contexto no se genera observación", cache.observations.none { it.parameterKey == "pectorales" })
    }

    @Test
    fun excludedSession_deload_sealsLogWithoutLearning() {
        val todayDeload = benchLog(
            "today-deload",
            observedAt - HOUR_MS,
            weight = 72.0,
            context = AugePerformanceContext(
                sessionStartTimeMs = observedAt - HOUR_MS - 3600_000L,
                isDeload = true,
                deloadOrigin = "week_execution_kind",
            ),
        )
        programRepository.addWorkoutLog(todayDeload)

        startViewModel()
        awaitWrites()

        val cache = cache()
        assertEquals("today-deload", cache.lastPerformanceLearnLogId)
        assertTrue("Sesión en deload no genera observación", cache.observations.none { it.parameterKey == "pectorales" })
    }

    @Test
    fun excludedSession_discomfort_sealsLogWithoutLearning() {
        val todayDiscomfort = benchLog(
            "today-discomfort",
            observedAt - HOUR_MS,
            weight = 72.0,
            discomforts = listOf("hombro"),
            context = AugePerformanceContext(
                sessionStartTimeMs = observedAt - HOUR_MS - 3600_000L,
                isDeload = false,
                deloadOrigin = "hierarchy_normal",
            ),
        )
        programRepository.addWorkoutLog(todayDiscomfort)

        startViewModel()
        awaitWrites()

        val cache = cache()
        assertEquals("today-discomfort", cache.lastPerformanceLearnLogId)
        assertTrue("Sesión con molestia no genera observación", cache.observations.none { it.parameterKey == "pectorales" })
    }

    @Test
    fun jsonRoundTrip_performanceContext_serializesAndDeserializesCorrectly() {
        val json = Json { ignoreUnknownKeys = true }

        val context = AugePerformanceContext(
            sessionStartTimeMs = 1760000000000L,
            isDeload = true,
            deloadOrigin = "week_execution_kind",
        )
        val encodedContext = json.encodeToString(context)
        val decodedContext = json.decodeFromString<AugePerformanceContext>(encodedContext)
        assertEquals(context, decodedContext)

        val logConContexto = benchLog("l1", observedAt, 80.0, context = context)
        val encodedLog = json.encodeToString(logConContexto)
        val decodedLog = json.decodeFromString<WorkoutLog>(encodedLog)
        assertEquals(context, decodedLog.performanceContext)

        val logSinContexto = benchLog("l2", observedAt, 80.0, context = null)
        val encodedSin = json.encodeToString(logSinContexto)
        val decodedSin = json.decodeFromString<WorkoutLog>(encodedSin)
        assertNull(decodedSin.performanceContext)
    }

    private fun startViewModel() {
        vm = AugeViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
            viewModelStore.put("auge-vm", it)
        }
        assertTrue(
            "el recalculo inicial no publico el snapshot",
            awaitUntil(SETTLE_MS) { !vm.snapshot.value.isLoading },
        )
    }

    private fun awaitWrites() {
        val mutex = AugeViewModel::class.java.getDeclaredField("augeWriteMutex")
            .apply { isAccessible = true }.get(vm) as Mutex
        runBlocking { withTimeout(AWAIT_MS) { mutex.lock() } }
        mutex.unlock()
    }

    private fun cache(): AugeAdaptiveCache = db { augeRepo.getAdaptiveCache() }

    private fun <T> db(block: suspend () -> T): T = runBlocking { withTimeout(AWAIT_MS) { block() } }

    private fun benchLog(
        id: String,
        ms: Long,
        weight: Double,
        discomforts: List<String> = emptyList(),
        context: AugePerformanceContext? = null,
    ) = WorkoutLog(
        id = id,
        programId = "prog",
        sessionId = id,
        sessionName = "Bench $id",
        date = Instant.ofEpochMilli(ms).toString(),
        durationMinutes = 60,
        discomforts = discomforts,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "bench",
                exerciseName = "Press banca",
                canonicalExerciseId = "bench",
                sets = listOf(
                    CompletedSet(id = "$id-s1", weight = weight, reps = 5, rpe = 8.0),
                    CompletedSet(id = "$id-s2", weight = weight, reps = 5, rpe = 8.0),
                ),
            ),
        ),
        performanceContext = context,
    )

    private fun awaitUntil(timeoutMs: Long = AWAIT_MS, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (true) {
            if (condition()) return true
            if (System.nanoTime() >= deadline) return false
            Thread.sleep(POLL_MS)
        }
    }

    private fun setSingleton(owner: Class<*>, value: Any?) {
        owner.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, value)
    }

    private companion object {
        const val AWAIT_MS = 15_000L
        const val SETTLE_MS = 45_000L
        const val POLL_MS = 25L
        const val HOUR_MS = 3_600_000L
    }
}
