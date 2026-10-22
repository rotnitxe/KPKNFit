package com.example.kpkn.screens.auge

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.AugeObservationStatus
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
import com.example.kpkn.domain.auge.ManualGestureMeta
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
import kotlin.math.roundToInt

/**
 * Aprendizaje manual v4 de extremo a extremo (paquete 3.3): ViewModel + repositorio + Room en memoria.
 *
 * Un gesto = una observación por objetivo realmente cambiado, con id `(canal/músculo, ancla, valor, logId)`; las copias,
 * las reentregas (3 veces, recreación del ViewModel) y los gestos viejos que llegan tarde no crean registro ni reanclan;
 * el cierre a t≈0 solo ancla; la Columna aprende con la `y` mostrada; `automaticBatteryAtAnchor` conserva la predicción
 * calculada (nunca un 100 inventado).
 *
 * Reloj y zona: el ViewModel y el repositorio leen «hoy» por dentro (`LocalDate.now()`, `System.currentTimeMillis()`), así
 * que el único instante «ahora» de cada caso se toma UNA vez en [observedAt] y el gesto lo congela con
 * `manualBatteryAnchorMs`/`ManualGestureMeta.observedAtMs` (ninguna reentrega fabrica otro). La zona se fija en UTC.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AugeManualLearningIntegrationTest {

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

    /** Único «ahora» de la prueba: el instante congelado de los gestos. */
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

    // ─── Home: una observación por gesto, sin importar cuántas veces se entregue ───────────────────

    @Test
    fun homeGesture_deliveredThreeTimes_leavesOneRecordAndAtMostOneUpdate() {
        programRepository.addWorkoutLog(heavyLog(hoursAgo = 48L))
        startViewModel()

        repeat(3) {
            vm.applyManualBatteries(neural = 45, manualBatteryAnchorMs = observedAt)
            awaitWrites()
        }

        val cache = cache()
        assertEquals("1 registro de Energía", 1, cache.observations.count { it.parameterKey == "cns" })
        assertTrue("≤ 1 actualización admitida", (cache.parameterStates["cns"]?.n ?: 0) <= 1)
        assertEquals("el contador histórico no se toca", 0, cache.totalObservations)
        val saved = requireNotNull(todayWellbeing())
        assertEquals(45, saved.manualNeuralBattery)
        assertEquals("el ancla es el instante congelado", observedAt, saved.manualNeuralAnchorMs)
    }

    @Test
    fun homeGesture_replayedAfterRecreatingTheViewModel_changesNothing() {
        programRepository.addWorkoutLog(heavyLog(hoursAgo = 48L))
        startViewModel()
        vm.applyManualBatteries(neural = 45, manualBatteryAnchorMs = observedAt)
        awaitWrites()
        val first = cache()
        val firstRow = requireNotNull(todayWellbeing())

        recreateViewModel()
        vm.applyManualBatteries(neural = 45, manualBatteryAnchorMs = observedAt)
        awaitWrites()

        val replay = cache()
        assertEquals("mismos registros", first.observations, replay.observations)
        assertEquals("mismos posteriores (n y metadatos)", first.parameterStates, replay.parameterStates)
        assertEquals(first.cnsRecoveryHours, replay.cnsRecoveryHours)
        assertEquals("sin reanclar", firstRow, todayWellbeing())
    }

    @Test
    fun aStaleReplayDoesNotOverwriteTheNewerValueNorTeach() {
        programRepository.addWorkoutLog(heavyLog(hoursAgo = 48L))
        startViewModel()
        vm.applyManualBatteries(neural = 45, manualBatteryAnchorMs = observedAt)
        awaitWrites()
        // La persona vuelve a mover Energía un segundo después (otro gesto, otra ancla).
        vm.applyManualBatteries(neural = 70, manualBatteryAnchorMs = observedAt + 1_000L)
        awaitWrites()
        val before = cache()
        assertEquals(70, todayWellbeing()?.manualNeuralBattery)

        // Reentrega tardía del primer gesto: el dedupe persistido (marca de agua) lo detecta ANTES de reanclar.
        vm.applyManualBatteries(neural = 45, manualBatteryAnchorMs = observedAt)
        awaitWrites()

        assertEquals("el valor nuevo sigue siendo el vigente", 70, todayWellbeing()?.manualNeuralBattery)
        assertEquals("el ancla nueva no retrocede", observedAt + 1_000L, todayWellbeing()?.manualNeuralAnchorMs)
        assertEquals(before.observations, cache().observations)
        assertEquals(before.parameterStates, cache().parameterStates)
    }

    @Test
    fun aGestureWithFourTargets_writesFourUniqueRecords_andTheSecondDeliveryAddsNone() {
        programRepository.addWorkoutLog(heavyLog(hoursAgo = 48L))
        startViewModel()

        repeat(2) {
            vm.applyManualBatteries(
                neural = 45,
                spinal = 55,
                perMuscleDelta = mapOf("Pectorales" to 40, "Dorsales" to 50),
                manualBatteryAnchorMs = observedAt,
            )
            awaitWrites()
        }

        val cache = cache()
        assertEquals(setOf("cns", "spinal", "pectorales", "dorsales"), cache.observations.map { it.parameterKey }.toSet())
        assertEquals("4 ids únicos", 4, cache.observations.map { it.id }.toSet().size)
        assertEquals("1 registro por objetivo", 4, cache.observations.size)
        assertTrue(
            "cada n sube como máximo 1",
            cache.parameterStates.values.all { it.n <= 1 },
        )
        assertTrue("todos comparten el ancla del gesto", cache.observations.all { it.anchorMs == observedAt })
        val saved = requireNotNull(todayWellbeing())
        assertEquals(setOf("Pectorales", "Dorsales"), saved.manualMuscleOverridesV2.keys)
        assertEquals(45, saved.manualNeuralBattery)
        assertNotNull("la Columna se guarda invertida, no como la y mostrada", saved.manualSpinalBattery)
    }

    // ─── Copias: no crean observación ni reancla ──────────────────────────────────────────────────

    @Test
    fun aCopyOfTheStoredValue_createsNoRecordAndKeepsTheRowUntouched() {
        val stored = DailyWellbeingLog(
            id = "ajustes-de-hoy",
            date = LocalDate.now().toString(),
            manualNeuralBattery = 60,
            manualBatteryAnchorMs = observedAt - 10L * HOUR_MS,
            notes = "ajustes de hoy",
        )
        db { augeRepo.saveWellbeingLog(stored) }
        programRepository.addWorkoutLog(heavyLog(hoursAgo = 48L))
        startViewModel()
        val before = cache()

        vm.applyManualBatteries(neural = 60, manualBatteryAnchorMs = observedAt)
        awaitWrites()

        assertEquals(before.observations, cache().observations)
        assertEquals(before.parameterStates, cache().parameterStates)
        assertEquals("la copia no reancla ni cambia la fila", stored, todayWellbeing())
    }

    // ─── Cierre: a t≈0 solo ancla ─────────────────────────────────────────────────────────────────

    @Test
    fun finishGestureAtZeroHours_anchorsTheStateWithoutChangingTau_andKeepsThePersistedLogId() {
        val closing = closingLog()
        programRepository.addWorkoutLog(closing)
        startViewModel()

        vm.applyManualBatteries(
            neural = 40,
            perMuscleDelta = mapOf("Pectorales" to 30),
            manualBatteryAnchorMs = observedAt,
            logId = closing.id,
        )
        awaitWrites()

        val cache = cache()
        assertEquals(setOf("cns", "pectorales"), cache.observations.map { it.parameterKey }.toSet())
        assertTrue(
            "a t≈0 todas quedan censuradas por la ventana de 8 h",
            cache.observations.all {
                it.outcome.status == AugeObservationStatus.CENSORED && it.outcome.reason == "ventana" && it.relevantLogId == closing.id
            },
        )
        assertNull("0 cambios de τ", cache.cnsRecoveryHours)
        assertTrue(cache.personalizedRecoveryHours.isEmpty())
        val saved = requireNotNull(todayWellbeing())
        assertEquals(40, saved.manualNeuralBattery)
        assertEquals("sourceSessionId del cierre = el log persistido", closing.id, saved.manualMuscleOverridesV2.getValue("Pectorales").sourceSessionId)
    }

    // ─── Readiness: la Columna aprende con la y mostrada ──────────────────────────────────────────

    @Test
    fun readinessSpine_learnsWithTheDisplayedValue_andStoresTheRawOne() {
        programRepository.addWorkoutLog(heavyLog(hoursAgo = 48L))
        startViewModel()
        val raw = 61
        val displayed = 55

        vm.saveWellbeing(
            DailyWellbeingLog(id = "chequeo", date = LocalDate.now().toString(), manualSpinalBattery = raw),
            ManualGestureMeta(observedAtMs = observedAt, displayedSpinal = displayed),
        )
        awaitWrites()

        val record = cache().observations.single { it.parameterKey == "spinal" }
        assertEquals("el aprendiz recibe la y mostrada", displayed.toDouble(), record.y, 0.0)
        val saved = requireNotNull(todayWellbeing())
        assertEquals("el estado guarda la cruda", raw, saved.manualSpinalBattery)
        assertEquals(observedAt, saved.manualSpinalAnchorMs)
    }

    // ─── automaticBatteryAtAnchor ─────────────────────────────────────────────────────────────────

    @Test
    fun automaticBatteryAtAnchor_keepsTheComputedPrediction_neverAnInventedHundred() {
        programRepository.addWorkoutLog(heavyLog(hoursAgo = 48L))
        startViewModel()

        vm.applyManualBatteries(perMuscleDelta = mapOf("Pectorales" to 40), manualBatteryAnchorMs = observedAt)
        awaitWrites()

        val record = cache().observations.single { it.parameterKey == "pectorales" }
        val v2 = requireNotNull(todayWellbeing()).manualMuscleOverridesV2.getValue("Pectorales")
        assertEquals(record.predicted.roundToInt().coerceIn(0, 100), v2.automaticBatteryAtAnchor)
        assertEquals(40, v2.battery)
        assertNull("fuera del cierre no hay log de origen", v2.sourceSessionId)
    }

    @Test
    fun theCallerPredictionWinsOverTheComputedOne() {
        startViewModel()

        vm.applyManualBatteries(
            perMuscleDelta = mapOf("Pectorales" to 40),
            manualBatteryAnchorMs = observedAt,
            predictedMuscleBatteries = mapOf("Pectorales" to 83),
        )
        awaitWrites()

        assertEquals(83, requireNotNull(todayWellbeing()).manualMuscleOverridesV2.getValue("Pectorales").automaticBatteryAtAnchor)
    }

    // ─── El aplicador del cierre confirma el éxito de la transacción ──────────────────────────────

    @Test
    fun onPersisted_reportsTrueOnlyAfterTheTransactionCommitted() {
        startViewModel()
        var result: Boolean? = null

        vm.applyManualBatteries(neural = 45, manualBatteryAnchorMs = observedAt, onPersisted = { result = it })

        assertTrue("el callback no llegó", awaitUntil { result != null })
        assertEquals(true, result)
        assertEquals(45, todayWellbeing()?.manualNeuralBattery)
    }

    // ─── Ayudantes ────────────────────────────────────────────────────────────────────────────────

    private fun startViewModel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = AugeViewModel(app).also { viewModelStore.put("auge-vm", it) }
        assertTrue(
            "el recálculo inicial no publicó el snapshot (¿falla el entorno?)",
            awaitUntil(SETTLE_MS) { !vm.snapshot.value.isLoading },
        )
        awaitWrites()
    }

    /** Recrea el ViewModel (y el repositorio) sobre la MISMA base: lo persistido sobrevive, la memoria no. */
    private fun recreateViewModel() {
        viewModelStore.clear()
        viewModelStore = ViewModelStore()
        setSingleton(AugeRepository::class.java, null)
        augeRepo = AugeRepository.getInstance(ApplicationProvider.getApplicationContext<Context>())
        startViewModel()
    }

    /** Espera a lo que el ViewModel ya pidió con el candado de escritura (el `Mutex` es justo). */
    private fun awaitWrites() {
        val mutex = AugeViewModel::class.java.getDeclaredField("augeWriteMutex")
            .apply { isAccessible = true }.get(vm) as Mutex
        runBlocking { withTimeout(AWAIT_MS) { mutex.lock() } }
        mutex.unlock()
    }

    private fun cache(): AugeAdaptiveCache = db { augeRepo.getAdaptiveCache() }

    private fun todayWellbeing(): DailyWellbeingLog? = db { augeRepo.getTodayWellbeing() }

    private fun <T> db(block: suspend () -> T): T = runBlocking { withTimeout(AWAIT_MS) { block() } }

    /** Sesión de [hoursAgo] h con seis series duras (RPE 9): la misma que usa `AugeWellbeingProvenanceTest`. */
    private fun heavyLog(hoursAgo: Long) = WorkoutLog(
        id = "sesion-dura",
        programId = "programa",
        sessionId = "sesion",
        sessionName = "Sesión dura",
        date = Instant.ofEpochMilli(observedAt - hoursAgo * HOUR_MS).toString(),
        durationMinutes = 60,
        completedExercises = listOf(workExercise()),
    )

    /** El log del cierre: fechado en el instante de finalización congelado del gesto. */
    private fun closingLog() = WorkoutLog(
        id = "log-del-cierre",
        programId = "programa",
        sessionId = "sesion",
        sessionName = "Sesión recién cerrada",
        date = Instant.ofEpochMilli(observedAt).toString(),
        durationMinutes = 60,
        completedExercises = listOf(workExercise()),
    )

    private fun workExercise() = CompletedExercise(
        exerciseId = "ejercicio-de-prueba",
        exerciseName = "Ejercicio de prueba",
        effectiveMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0)),
        sets = List(6) { index -> CompletedSet(id = "serie-$index", weight = 100.0, reps = 5, rpe = 9.0) },
    )

    /** Espera REAL (hilos de Room y de `Dispatchers.Default`): solo el tope usa el reloj monotónico. */
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
