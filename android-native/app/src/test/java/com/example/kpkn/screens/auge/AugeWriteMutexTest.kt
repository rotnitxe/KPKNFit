package com.example.kpkn.screens.auge

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.DailyWellbeingLog
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
 * Candado de escritura de AUGE (`AugeViewModel.augeWriteMutex`, un `Mutex` NO reentrante).
 *
 * Defecto P0 (F-01): `recompute` pide el candado al final para guardar el caché (primer recálculo del
 * día UTC o cambio de nivel de aviso, `cacheToPersist != null`) y cinco acciones públicas ya lo tenían
 * tomado al llamarlo (`saveWellbeing`, `applyManualBatteries`, `dismissAdvisory`,
 * `clearManualBatteryOverrides`, `resetAdaptiveCache`): esperaba para siempre y TODA escritura posterior
 * de AUGE quedaba colgada en silencio hasta reiniciar la app.
 *
 * Esas acciones son `fun` + `viewModelScope.launch` (no `suspend`) y devuelven al instante, así que no
 * sirve un `withTimeout` sobre la llamada: cada caso se afirma POR EFECTO. Tras invocar la acción se
 * sondea de verdad (el cálculo salta a `Dispatchers.Default`, un hilo real, y Room usa los suyos) hasta
 * que una SEGUNDA operación que toma el candado (`markModelUpdateNoticeShown`) ha persistido, y se
 * comprueba además que el recálculo llegó a guardar el aviso del día (así el caso ejercita de verdad el
 * camino con `cacheToPersist != null`).
 *
 * Entorno: las dos bases son en memoria. `ProgramRepository` y `NutritionRepository` se siembran con sus
 * `initForTests`; `AugeRepository` se apunta, por reflexión sobre los `INSTANCE` (mismo patrón que
 * `WorkoutFinishCancellationTest`), a una base propia de la prueba. Main es un `UnconfinedTestDispatcher`
 * y el temporizador de 5 min del ViewModel (`delay` virtual) nunca se dispara.
 *
 * Reloj: `AugeViewModel` y `AugeRepository` leen el reloj real por dentro (`System.currentTimeMillis()`,
 * `LocalDate.now()`) y no admiten reloj inyectado, así que estos casos dependen implícitamente de «hoy»
 * (el bienestar de hoy se fecha con `LocalDate.now()`, igual que el repositorio). Ninguna aserción
 * compara contra un instante concreto: solo miran que `lastAdvisoryDay` pase de `null` a un valor.
 * Candidato al paquete 1.7.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AugeWriteMutexTest {

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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        NutritionRepository.initForTests(context, NoCatalogImport)
        programRepository = ProgramRepository.initForTests(context)
        assertTrue(
            "ProgramRepository no terminó de cargar",
            awaitUntil(SETTLE_MS) { programRepository.isReady.value },
        )
        // Base propia (en memoria) para AugeRepository: se publica como singleton de KpknDatabase y se
        // reinicia el singleton de AugeRepository para que el ViewModel construya el suyo sobre ella.
        augeDatabase = KpknDatabase.createInMemory(context)
        setSingleton(KpknDatabase::class.java, augeDatabase)
        setSingleton(AugeRepository::class.java, null)
        viewModelStore = ViewModelStore()
    }

    @After
    fun tearDown() {
        try {
            // Cancela el viewModelScope (incluidas las corrutinas que el defecto deja colgadas).
            if (this::viewModelStore.isInitialized) viewModelStore.clear()
            setSingleton(AugeRepository::class.java, null)
            setSingleton(KpknDatabase::class.java, null)
            if (this::augeDatabase.isInitialized) augeDatabase.close()
            ProgramRepository.closeInstance()
            NutritionRepository.closeInstance()
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ─── Control: los llamadores SIN candado previo nunca estuvieron afectados ─────────────────────

    @Test
    fun control_unlockedCallers_persistTheCacheAndReleaseTheWriteLock() {
        startViewModel()
        // 1. El recálculo inicial del colector de init (sin candado previo) publica y persiste.
        settle()

        // 2. `refresh()` tampoco tiene candado previo: debe pedirlo él mismo y soltarlo.
        armFirstRecomputeOfTheDay()
        vm.refresh()
        assertTrue(
            "refresh() no llegó a persistir el aviso del día",
            awaitUntil { cache().lastAdvisoryDay != null },
        )
        assertWriteLockStaysUsable("refresh")
    }

    // ─── Los cinco llamadores que ya tienen el candado cuando llaman a recompute ───────────────────

    @Test
    fun saveWellbeing_whoseRecomputePersistsTheCache_releasesTheWriteLock() {
        startViewModel()
        settle()
        armFirstRecomputeOfTheDay()

        vm.saveWellbeing(wellbeing(notes = "candado-saveWellbeing"))

        assertWriteLockStaysUsable("saveWellbeing")
        assertEquals("candado-saveWellbeing", todayWellbeing()?.notes)
    }

    @Test
    fun applyManualBatteries_whoseRecomputePersistsTheCache_releasesTheWriteLock() {
        startViewModel()
        settle()
        armFirstRecomputeOfTheDay()

        vm.applyManualBatteries(neural = 55)

        assertWriteLockStaysUsable("applyManualBatteries")
        assertEquals(55, todayWellbeing()?.manualNeuralBattery)
    }

    @Test
    fun dismissAdvisory_whoseRecomputePersistsTheCache_releasesTheWriteLock() {
        startViewModel()
        settle()
        armFirstRecomputeOfTheDay()

        vm.dismissAdvisory(ADVISORY_ID)

        assertWriteLockStaysUsable("dismissAdvisory")
        assertTrue(ADVISORY_ID in cache().dismissedAdvisoryKeys)
    }

    @Test
    fun clearManualBatteryOverrides_whoseRecomputePersistsTheCache_releasesTheWriteLock() {
        startViewModel()
        settle()
        db {
            augeRepo.saveWellbeingLog(
                wellbeing(manualNeuralBattery = 40, manualBatteryAnchorMs = FIXED_ANCHOR_MS),
            )
        }
        assertEquals("precondición: hay un ajuste manual", 40, todayWellbeing()?.manualNeuralBattery)
        armFirstRecomputeOfTheDay()

        vm.clearManualBatteryOverrides()

        assertWriteLockStaysUsable("clearManualBatteryOverrides")
        assertNull(todayWellbeing()?.manualNeuralBattery)
    }

    @Test
    fun resetAdaptiveCache_whoseRecomputePersistsTheCache_releasesTheWriteLock() {
        startViewModel()
        settle()
        // El caché por defecto tiene `lastAdvisoryDay = null`: el reinicio dispara por sí solo el camino.
        db { augeRepo.saveAdaptiveCache(augeRepo.getAdaptiveCache().copy(cnsRecoveryHours = 77.0)) }
        assertEquals("precondición: τ aprendido", 77.0, cache().cnsRecoveryHours ?: Double.NaN, 0.0)

        vm.resetAdaptiveCache()

        assertWriteLockStaysUsable("resetAdaptiveCache")
        assertNull("el reinicio devolvió el caché a sus valores por defecto", cache().cnsRecoveryHours)
    }

    @Test
    fun threeConsecutiveSaveWellbeing_eachPersistingTheCache_allFinishAndAreSaved() {
        startViewModel()
        settle()
        val today = LocalDate.now()
        val days = listOf(today, today.minusDays(1), today.minusDays(2))

        days.forEachIndexed { index, day ->
            armFirstRecomputeOfTheDay()
            vm.saveWellbeing(wellbeing(date = day, notes = "seguido-$index"))
            assertTrue(
                "el recálculo del guardado ${index + 1} de 3 no llegó a persistir el aviso del día",
                awaitUntil { cache().lastAdvisoryDay != null },
            )
        }

        assertWriteLockStaysUsable("tres saveWellbeing seguidos")
        val saved = db { augeRepo.getWellbeingLogs() }.associateBy { it.date }
        days.forEachIndexed { index, day ->
            assertEquals("el guardado ${index + 1} de 3 se perdió", "seguido-$index", saved[day.toString()]?.notes)
        }
    }

    @Test
    fun lockedCaller_whoseRecomputeSealsTheNewestLog_doesNotRelockInsideTheLearner() {
        // Con un log en el historial, `learnFromLatestFinishedLog` también guarda (sella el log): dentro de
        // un llamador con candado no puede volver a pedirlo. Se «olvida» el sello para que el recálculo del
        // llamador vuelva a pasar por ahí (el log es antiguo: camino de sellado sin aprender).
        programRepository.addWorkoutLog(pastLog())
        startViewModel()
        settle()
        val logId = programRepository.history.value.first().id
        assertEquals("precondición: el recálculo inicial selló el log", logId, cache().lastPerformanceLearnLogId)
        forgetLearnedLog()
        armFirstRecomputeOfTheDay()

        vm.saveWellbeing(wellbeing(notes = "candado-aprendizaje"))

        assertWriteLockStaysUsable("saveWellbeing que sella el log")
        assertEquals(logId, cache().lastPerformanceLearnLogId)
    }

    // ─── Actualización perdida: guardar el aviso no pisa lo aprendido entre la lectura y el guardado ──

    @Test
    fun persistingTheAdvisoryDay_keepsWhatWasWrittenBetweenTheReadAndTheSave() {
        startViewModel()
        settle()
        armFirstRecomputeOfTheDay()
        val mutex = writeMutexOf(vm)

        // Este caso hace de «otra escritura» (p. ej. learnFromManualAdjustment) que tiene el candado: el
        // recálculo (sin candado) lee el caché, calcula, publica y espera el candado para guardar. Esa
        // espera es EXACTAMENTE el hueco entre su lectura y su guardado.
        runBlocking { withTimeout(AWAIT_MS) { mutex.lock() } }
        try {
            vm.refresh()
            assertTrue(
                "el recálculo no publicó (no llegó a su guardado)",
                awaitUntil { !vm.snapshot.value.isLoading },
            )
            db {
                val current = augeRepo.getAdaptiveCache()
                augeRepo.saveAdaptiveCache(
                    current.copy(
                        cnsRecoveryHours = LEARNED_TAU,
                        totalObservations = current.totalObservations + 1,
                        modelUpdateNoticeShown = true,
                    ),
                )
            }
        } finally {
            mutex.unlock()
        }

        assertTrue(
            "el recálculo no llegó a guardar el aviso del día",
            awaitUntil { cache().lastAdvisoryDay != null },
        )
        val after = cache()
        assertEquals("τ aprendido se perdió al guardar el aviso", LEARNED_TAU, after.cnsRecoveryHours ?: Double.NaN, 0.0)
        assertEquals(1, after.totalObservations)
        assertTrue("el aviso de modelo ya mostrado se revirtió", after.modelUpdateNoticeShown)
    }

    // ─── Excepciones: recompute que lanza no tumba al colector ─────────────────────────────────────

    @Test
    fun recomputeThatThrows_keepsTheCollectorAliveAndTheSnapshotPublishable() {
        startViewModel()
        settle()
        val before = vm.snapshot.value
        assertFalse(before.isLoading)

        // Falla una dependencia: sin su tabla, AugeRepository.getTodayWellbeing() lanza SQLiteException en
        // el primer paso de recompute (antes de publicar nada).
        sql("ALTER TABLE auge_wellbeing RENAME TO auge_wellbeing_apagada")
        try {
            // El cambio de ajustes lo recoge el COLECTOR de init, que lanza el recálculo que falla.
            programRepository.updateSettings { it.copy(username = "falla-1") }
            assertTrue(
                "tras la excepción el snapshot se quedó «cargando» para siempre",
                awaitUntil { !vm.snapshot.value.isLoading },
            )
            assertEquals("no pisa las baterías anteriores", before.batteries, vm.snapshot.value.batteries)
            assertEquals("no pisa el dashboard anterior", before.dashboard, vm.snapshot.value.dashboard)
        } finally {
            sql("ALTER TABLE auge_wellbeing_apagada RENAME TO auge_wellbeing")
        }

        // La dependencia vuelve: el colector sigue vivo, recalcula y el snapshot vuelve a ser publicable.
        armFirstRecomputeOfTheDay()
        programRepository.updateSettings { it.copy(username = "falla-2") }
        assertTrue(
            "el colector de init murió con la excepción: no volvió a recalcular",
            awaitUntil { cache().lastAdvisoryDay != null },
        )
        assertTrue(awaitUntil { !vm.snapshot.value.isLoading })
        assertWriteLockStaysUsable("el recálculo posterior a la excepción")
    }

    // ─── Ayudantes ────────────────────────────────────────────────────────────────────────────────

    private fun startViewModel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = AugeViewModel(app).also { viewModelStore.put("auge-vm", it) }
        augeRepo = AugeRepository.getInstance(app)
    }

    /** Espera al recálculo inicial del colector de init: publica el snapshot y persiste el aviso del día. */
    private fun settle() {
        assertTrue(
            "el recálculo inicial no publicó el snapshot (¿falla el entorno?)",
            awaitUntil(SETTLE_MS) { !vm.snapshot.value.isLoading },
        )
        assertTrue(
            "el recálculo inicial no persistió el aviso del día",
            awaitUntil(SETTLE_MS) { cache().lastAdvisoryDay != null },
        )
    }

    /** Deja el caché como el de un día nuevo: el siguiente recálculo tiene `cacheToPersist != null`. */
    private fun armFirstRecomputeOfTheDay() {
        db {
            augeRepo.saveAdaptiveCache(
                augeRepo.getAdaptiveCache().copy(lastAdvisoryDay = null, lastAdvisoryLevelByChannel = emptyMap()),
            )
        }
        assertNull("precondición: sin aviso del día", cache().lastAdvisoryDay)
    }

    /** Olvida que el recálculo ya selló el último log (marca persistida y marca en memoria del ViewModel). */
    private fun forgetLearnedLog() {
        db { augeRepo.saveAdaptiveCache(augeRepo.getAdaptiveCache().copy(lastPerformanceLearnLogId = null)) }
        AugeViewModel::class.java.getDeclaredField("inMemoryLastPerformanceLearnLogId")
            .apply { isAccessible = true }
            .set(vm, null)
    }

    /**
     * Una SEGUNDA operación que toma el candado de escritura debe poder persistir. Si la primera lo dejó
     * tomado (recompute pidiéndolo dos veces), esta se queda esperando para siempre y el caso falla.
     * Después se comprueba que el recálculo del llamador sí llegó a guardar el aviso del día.
     */
    private fun assertWriteLockStaysUsable(caller: String) {
        vm.markModelUpdateNoticeShown()
        assertTrue(
            "$caller dejó colgado el candado de escritura: markModelUpdateNoticeShown nunca persistió",
            awaitUntil { cache().modelUpdateNoticeShown },
        )
        assertNotNull(
            "el recálculo de $caller no llegó a guardar el aviso del día",
            cache().lastAdvisoryDay,
        )
    }

    private fun writeMutexOf(target: AugeViewModel): Mutex =
        AugeViewModel::class.java.getDeclaredField("augeWriteMutex")
            .apply { isAccessible = true }
            .get(target) as Mutex

    private fun cache(): AugeAdaptiveCache = db { augeRepo.getAdaptiveCache() }

    private fun todayWellbeing(): DailyWellbeingLog? = db { augeRepo.getTodayWellbeing() }

    private fun <T> db(block: suspend () -> T): T = runBlocking { withTimeout(AWAIT_MS) { block() } }

    private fun sql(statement: String) {
        augeDatabase.openHelper.writableDatabase.execSQL(statement)
    }

    private fun wellbeing(
        date: LocalDate = LocalDate.now(),
        notes: String? = null,
        manualNeuralBattery: Int? = null,
        manualBatteryAnchorMs: Long? = null,
    ) = DailyWellbeingLog(
        id = "bienestar-$date",
        date = date.toString(),
        notes = notes,
        manualNeuralBattery = manualNeuralBattery,
        manualBatteryAnchorMs = manualBatteryAnchorMs,
    )

    /** Log antiguo (fecha fija en el pasado): `learnFromLatestFinishedLog` solo lo sella, no aprende de él. */
    private fun pastLog() = WorkoutLog(
        id = "log-antiguo",
        programId = "programa",
        sessionId = "sesion",
        sessionName = "Sesión antigua",
        date = "2026-01-05T10:00:00.000Z",
        durationMinutes = 45,
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
        const val LEARNED_TAU = 41.5
        const val FIXED_ANCHOR_MS = 1_760_000_000_000L
        const val ADVISORY_ID = "STRUCTURE|2026-W41|ADJUST"
    }
}
