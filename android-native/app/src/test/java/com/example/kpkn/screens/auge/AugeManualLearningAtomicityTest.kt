package com.example.kpkn.screens.auge

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.DailyWellbeingLog
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Atomicidad del guardado manual (paquete 3.3): bienestar + caché en UNA transacción Room.
 *
 * Fallo inyectado entre los dos upserts con un disparador SQL sobre `auge_adaptive_cache` (el segundo upsert aborta):
 * ni el bienestar ni el caché cambian (0 filas parcialmente actualizadas), la corrutina libera el candado, el callback
 * recibe `false` y el reintento con la MISMA identidad (mismo instante) termina exactamente una vez. Después se
 * comprueba el intercalado de una edición manual con sello/avisos y recálculos (0 pérdidas) y la cancelación mientras se
 * espera el candado (el callback recibe `false` y el candado queda libre).
 *
 * Reloj y zona: el único «ahora» es [observedAt], congelado en el gesto; la zona es UTC.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AugeManualLearningAtomicityTest {

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

    /** Único «ahora» de la prueba: el instante congelado del gesto. */
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

    // ─── Fallo entre los dos upserts ──────────────────────────────────────────────────────────────

    @Test
    fun failureBetweenTheTwoUpserts_leavesNoPartialRows_andTheRetryCommitsExactlyOnce() {
        startViewModel()
        val cacheBefore = cache()
        val rowsBefore = allRows()
        assertTrue("precondición: sin bienestar guardado", rowsBefore.isEmpty())

        // Del segundo upsert (el caché) en adelante todo aborta: el bienestar ya se había escrito dentro de la transacción.
        failCacheWrites()
        var firstResult: Boolean? = null
        vm.applyManualBatteries(neural = 45, manualBatteryAnchorMs = observedAt, onPersisted = { firstResult = it })
        assertTrue("el callback del intento fallido no llegó", awaitUntil { firstResult != null })
        awaitWrites()

        assertEquals("el intento fallido avisa con false", false, firstResult)
        assertTrue("0 filas de bienestar parcialmente escritas", allRows().isEmpty())
        assertEquals("el caché queda igual que antes", cacheBefore.observations, cache().observations)
        assertEquals(cacheBefore.parameterStates, cache().parameterStates)
        assertTrue("el candado quedó libre tras el fallo", !writeMutex().isLocked)

        // El reintento, con la misma identidad (mismo instante), termina exactamente una vez.
        restoreCacheWrites()
        var secondResult: Boolean? = null
        vm.applyManualBatteries(neural = 45, manualBatteryAnchorMs = observedAt, onPersisted = { secondResult = it })
        assertTrue("el callback del reintento no llegó", awaitUntil { secondResult != null })
        awaitWrites()

        assertEquals(true, secondResult)
        val committed = cache()
        assertEquals("un único registro de Energía", 1, committed.observations.count { it.parameterKey == "cns" })
        val saved = requireNotNull(todayWellbeing())
        assertEquals(45, saved.manualNeuralBattery)
        assertEquals(observedAt, saved.manualNeuralAnchorMs)

        // Una tercera entrega ya no añade nada: ni registro, ni cambio de la fila.
        vm.applyManualBatteries(neural = 45, manualBatteryAnchorMs = observedAt)
        awaitWrites()
        assertEquals(committed.observations, cache().observations)
        assertEquals(saved, todayWellbeing())
    }

    @Test
    fun aSuccessfulGesture_changesBothTablesTogether() {
        startViewModel()
        assertTrue(allRows().isEmpty())

        vm.applyManualBatteries(neural = 45, perMuscleDelta = mapOf("Pectorales" to 40), manualBatteryAnchorMs = observedAt)
        awaitWrites()

        assertNotNull(todayWellbeing())
        assertEquals(setOf("cns", "pectorales"), cache().observations.map { it.parameterKey }.toSet())
    }

    // ─── Intercalado de edición, sello/aviso y recálculo ──────────────────────────────────────────

    @Test
    fun interleavedManualEditAdvisoryDismissNoticeAndRecompute_losesNothing() {
        startViewModel()

        vm.applyManualBatteries(neural = 45, manualBatteryAnchorMs = observedAt)
        vm.dismissAdvisory(ADVISORY_ID)
        vm.refresh()
        vm.markModelUpdateNoticeShown()
        vm.applyManualBatteries(neural = 52, manualBatteryAnchorMs = observedAt + 1_000L)
        vm.refresh()
        awaitWrites()
        assertTrue("el recálculo no llegó a persistir el aviso del día", awaitUntil { cache().lastAdvisoryDay != null })

        val cache = cache()
        assertEquals("los dos gestos dejaron su posterior/registro", 2, cache.observations.count { it.parameterKey == "cns" })
        assertTrue("el descarte sobrevive", ADVISORY_ID in cache.dismissedAdvisoryKeys)
        assertTrue("el aviso de modelo mostrado sobrevive", cache.modelUpdateNoticeShown)
        assertEquals(52, todayWellbeing()?.manualNeuralBattery)
        assertEquals(observedAt + 1_000L, todayWellbeing()?.manualNeuralAnchorMs)
    }

    @Test
    fun threeConsecutiveSaves_allComplete() {
        startViewModel()
        val today = LocalDate.now()
        val days = listOf(today, today.minusDays(1), today.minusDays(2))

        days.forEachIndexed { index, day ->
            vm.saveWellbeing(DailyWellbeingLog(id = "bienestar-$day", date = day.toString(), notes = "seguido-$index"))
            awaitWrites()
        }

        val saved = allRows().associateBy { it.date }
        days.forEachIndexed { index, day ->
            assertEquals("el guardado ${index + 1} de 3 se perdió", "seguido-$index", saved[day.toString()]?.notes)
        }
        assertTrue(!writeMutex().isLocked)
    }

    // ─── Cancelación ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun cancellationWhileWaitingForTheLock_reportsFalse_andLeavesTheLockToItsOwner() {
        startViewModel()
        val mutex = writeMutex()
        runBlocking { withTimeout(AWAIT_MS) { mutex.lock() } }
        var result: Boolean? = null
        try {
            vm.applyManualBatteries(neural = 45, manualBatteryAnchorMs = observedAt, onPersisted = { result = it })
            assertNull("sigue esperando el candado", result)

            viewModelStore.clear()

            assertTrue("la cancelación no avisó con false", awaitUntil { result != null })
            assertEquals(false, result)
            assertTrue("el candado sigue siendo de quien lo tomó", mutex.isLocked)
        } finally {
            mutex.unlock()
        }
        assertFalse("tras soltarlo, el candado queda libre", mutex.isLocked)
        assertTrue("no se escribió nada", allRows().isEmpty())
    }

    // ─── Ayudantes ────────────────────────────────────────────────────────────────────────────────

    private fun startViewModel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = AugeViewModel(app).also { viewModelStore.put("auge-vm", it) }
        assertTrue(
            "el recálculo inicial no publicó el snapshot (¿falla el entorno?)",
            awaitUntil(SETTLE_MS) { !vm.snapshot.value.isLoading },
        )
        assertTrue(
            "el recálculo inicial no persistió el aviso del día",
            awaitUntil(SETTLE_MS) { cache().lastAdvisoryDay != null },
        )
        awaitWrites()
    }

    /** El segundo upsert de la transacción (el caché) aborta; sirve para INSERT y para UPDATE. */
    private fun failCacheWrites() {
        sql("CREATE TRIGGER fallo_cache_insert BEFORE INSERT ON auge_adaptive_cache BEGIN SELECT RAISE(ABORT, 'fallo inyectado'); END")
        sql("CREATE TRIGGER fallo_cache_update BEFORE UPDATE ON auge_adaptive_cache BEGIN SELECT RAISE(ABORT, 'fallo inyectado'); END")
    }

    private fun restoreCacheWrites() {
        sql("DROP TRIGGER fallo_cache_insert")
        sql("DROP TRIGGER fallo_cache_update")
    }

    private fun sql(statement: String) {
        augeDatabase.openHelper.writableDatabase.execSQL(statement)
    }

    private fun writeMutex(): Mutex =
        AugeViewModel::class.java.getDeclaredField("augeWriteMutex").apply { isAccessible = true }.get(vm) as Mutex

    /** Espera a lo que el ViewModel ya pidió con el candado de escritura (el `Mutex` es justo). */
    private fun awaitWrites() {
        val mutex = writeMutex()
        runBlocking { withTimeout(AWAIT_MS) { mutex.lock() } }
        mutex.unlock()
    }

    private fun cache(): AugeAdaptiveCache = db { augeRepo.getAdaptiveCache() }

    private fun todayWellbeing(): DailyWellbeingLog? = db { augeRepo.getTodayWellbeing() }

    private fun allRows(): List<DailyWellbeingLog> = db { augeRepo.getWellbeingLogs() }

    private fun <T> db(block: suspend () -> T): T = runBlocking { withTimeout(AWAIT_MS) { block() } }

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
        const val ADVISORY_ID = "STRUCTURE|2026-W41|ADJUST"
    }
}
