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
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.WellbeingSource
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.FoodCatalogImporter
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.declaredCheckInChannels
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Procedencia, copias y filas del día en `AugeViewModel` (paquete 1.2a, hallazgo F-04).
 *
 * - a. `applyManualBatteries` reconstruía la fila entera con el constructor y no copiaba `source` ni
 *   `capturedFields`: una fila del alta (`ONBOARDING_INITIAL`) volvía a `DAILY_CHECK_IN` y vacía, y lo que el alta
 *   declaró pasaba a «sin datos» tras el primer ajuste del cierre.
 * - b. `saveWellbeing` aprendía de las COPIAS. El chequeo previo al entreno reenvía los ajustes de hoy sin que la
 *   persona los toque; como la predicción ya se movió con el tiempo, el valor copiado «difería» y se reaprendía τ de
 *   un dato que nadie tocó (y el guardado contaba la copia como ajuste para estampar el ancla).
 * - c. Control: una edición real (Energía distinta de lo guardado) sí aprende y reancla; la copia que viaja con ella no.
 * - d. `saveWellbeing` con el `id` de la fila de AYER y la fecha de hoy (la pantalla se compuso antes de medianoche y
 *   el anfitrión reutiliza el `id` que leyó una sola vez) reescribía la fila de ayer: la clave primaria es el `id`.
 * - e. Control: un `id` libre se conserva y sigue habiendo una sola fila por fecha.
 *
 * Entorno: el de `AugeWriteMutexTest` (dos bases en memoria; `ProgramRepository` y `NutritionRepository` con
 * `initForTests`, `AugeRepository` apuntado por reflexión a una base propia; Main es un `UnconfinedTestDispatcher`,
 * así que las acciones públicas del ViewModel toman el candado de escritura antes de devolver). Esas acciones son
 * `fun` + `viewModelScope.launch` y no devuelven nada: cada caso espera con [awaitWrites], que toma el mismo candado
 * (un `Mutex` es justo: entra después de lo que ya lo pidió) y por tanto solo pasa cuando la acción terminó entera,
 * recálculo incluido.
 *
 * Reloj: `AugeViewModel` y `AugeRepository` leen el reloj real por dentro (`System.currentTimeMillis()`,
 * `LocalDate.now()`) y no admiten reloj inyectado, así que estos casos dependen implícitamente de «hoy» y de «ahora».
 * Todo lo que la prueba fecha sale de [realNowMs] y de `LocalDate.now()` con desplazamientos relativos (ayer, hace 10 h,
 * hace 48 h); ninguna aserción compara contra un instante concreto. Candidato al paquete 1.7.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AugeWellbeingProvenanceTest {

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
            Dispatchers.resetMain()
        }
    }

    // ─── a. applyManualBatteries conserva la procedencia de la fila ───────────────────────────────

    @Test
    fun applyManualBatteries_keepsTheProvenanceOfTheOnboardingRow() {
        val onboarding = onboardingRow(LocalDate.now())
        db { augeRepo.saveWellbeingLog(onboarding) }
        startViewModel()
        settle()
        assertEquals(
            "precondición: el alta declara Energía y Músculos",
            setOf(RecoveryChannelId.SYSTEM, RecoveryChannelId.MUSCULAR),
            declaredCheckInChannels(onboarding),
        )

        vm.applyManualBatteries(neural = 60)
        awaitWrites()

        val saved = requireNotNull(todayWellbeing())
        assertEquals("el ajuste del cierre no se aplicó", 60, saved.manualNeuralBattery)
        assertEquals("la fila perdió su procedencia", WellbeingSource.ONBOARDING_INITIAL, saved.source)
        assertEquals("la fila perdió lo que el alta declaró", onboarding.capturedFields, saved.capturedFields)
        assertEquals(
            "lo declarado en el alta pasó a «sin datos»",
            setOf(RecoveryChannelId.SYSTEM, RecoveryChannelId.MUSCULAR),
            declaredCheckInChannels(saved),
        )
        // Solo cambian los campos del gesto (Energía y su ancla): todo lo demás de la fila queda como estaba.
        assertEquals(
            onboarding.copy(
                manualNeuralBattery = 60, manualBatteryAnchorMs = saved.manualBatteryAnchorMs,
                manualNeuralAnchorMs = saved.manualBatteryAnchorMs,
                manualMuscleAnchorMs = mapOf("Pectorales" to FIXED_ANCHOR_MS),
            ),
            saved,
        )
    }

    @Test
    fun applyManualBatteries_withAMuscleDelta_keepsTheProvenanceAndWritesTheV2Override() {
        val onboarding = onboardingRow(LocalDate.now())
        db { augeRepo.saveWellbeingLog(onboarding) }
        startViewModel()
        settle()

        vm.applyManualBatteries(
            perMuscleDelta = mapOf("Pectorales" to 40),
            manualBatteryAnchorMs = FIXED_ANCHOR_MS,
            predictedMuscleBatteries = mapOf("Pectorales" to 90),
        )
        awaitWrites()

        val saved = requireNotNull(todayWellbeing())
        assertEquals(WellbeingSource.ONBOARDING_INITIAL, saved.source)
        assertEquals(onboarding.capturedFields, saved.capturedFields)
        assertEquals(
            ManualMuscleBatteryOverride(
                battery = 40,
                anchorEpochMs = FIXED_ANCHOR_MS,
                sourceSessionId = null,
                automaticBatteryAtAnchor = 90,
            ),
            saved.manualMuscleOverridesV2.values.single(),
        )
        // Al tocar un músculo las escrituras nuevas usan V2 y se vacía V1; lo demás no cambia.
        assertEquals(
            onboarding.copy(
                manualMuscleBatteries = emptyMap(),
                manualBatteryAnchorMs = FIXED_ANCHOR_MS,
                manualNeuralAnchorMs = FIXED_ANCHOR_MS,
                manualMuscleOverridesV2 = saved.manualMuscleOverridesV2,
            ),
            saved,
        )
    }

    // ─── b. Una copia sin cambios no reaprende ni reancla ─────────────────────────────────────────

    @Test
    fun saveWellbeing_withAnUnchangedCopyOfTheStoredAdjustments_neitherRelearnsNorReanchors() {
        val stored = seedLearningScenario()
        startViewModel()
        settle()
        assertNotEquals(
            "precondición: la predicción de Energía ya se movió del valor guardado",
            STORED_NEURAL,
            vm.snapshot.value.batteries.cnc,
        )
        val before = cache()

        vm.saveWellbeing(hostCopyOf(stored))
        awaitWrites()

        val after = cache()
        assertEquals("personalizedRecoveryHours", before.personalizedRecoveryHours, after.personalizedRecoveryHours)
        assertEquals("cnsRecoveryHours", before.cnsRecoveryHours, after.cnsRecoveryHours)
        assertEquals("spinalRecoveryHours", before.spinalRecoveryHours, after.spinalRecoveryHours)
        assertEquals("totalObservations", before.totalObservations, after.totalObservations)
        val saved = requireNotNull(todayWellbeing())
        assertEquals("la copia reinició el ancla", stored.manualBatteryAnchorMs, saved.manualBatteryAnchorMs)
        assertEquals("la copia cambió la fila guardada", stored, saved)
    }

    // ─── c. Una edición real sí aprende y reancla (y la copia que viaja con ella, no) ──────────────

    @Test
    fun saveWellbeing_withARealEdit_learnsAndReanchorsOnlyThen() {
        val stored = seedLearningScenario()
        startViewModel()
        settle()
        assertNotEquals(
            "precondición: la predicción de Energía ya se movió del valor que se va a editar",
            EDITED_NEURAL,
            vm.snapshot.value.batteries.cnc,
        )
        val before = cache()

        // La persona mueve Energía de 60 a 45; el chequeo previo copia a la vez los Pectorales de hoy sin tocarlos.
        vm.saveWellbeing(hostCopyOf(stored).copy(manualNeuralBattery = EDITED_NEURAL))
        awaitWrites()

        val after = cache()
        assertEquals(
            "una edición real cuenta como una observación (antes=$before, después=$after)",
            before.totalObservations + 1,
            after.totalObservations,
        )
        assertNotNull("Energía debía enseñar su τ (antes=$before, después=$after)", after.cnsRecoveryHours)
        assertEquals(
            "los Pectorales copiados que viajan con la edición no enseñan",
            before.personalizedRecoveryHours,
            after.personalizedRecoveryHours,
        )
        val saved = requireNotNull(todayWellbeing())
        assertEquals(EDITED_NEURAL, saved.manualNeuralBattery)
        assertTrue(
            "la edición debía reanclar (ancla ${saved.manualBatteryAnchorMs}, la guardada era ${stored.manualBatteryAnchorMs})",
            (saved.manualBatteryAnchorMs ?: 0L) > (stored.manualBatteryAnchorMs ?: 0L),
        )
        assertEquals(
            stored.copy(
                manualNeuralBattery = EDITED_NEURAL, manualBatteryAnchorMs = saved.manualBatteryAnchorMs,
                manualNeuralAnchorMs = saved.manualBatteryAnchorMs,
                manualMuscleAnchorMs = mapOf("Pectorales" to requireNotNull(stored.manualBatteryAnchorMs)),
            ),
            saved,
        )
    }

    // ─── d. Nunca se reescribe la fila de otro día ────────────────────────────────────────────────

    @Test
    fun saveWellbeing_withYesterdaysIdAndTodaysDate_leavesYesterdaysRowIntact() {
        val today = LocalDate.now()
        val yesterday = yesterdayRow(today.minusDays(1))
        db { augeRepo.saveWellbeingLog(yesterday) }
        startViewModel()
        settle()

        // El anfitrión leyó la fila de ayer al componerse la pantalla y, pasada la medianoche, guarda con su `id` y la fecha de hoy.
        vm.saveWellbeing(DailyWellbeingLog(id = yesterday.id, date = today.toString(), notes = "hoy"))
        awaitWrites()

        val rows = allRows()
        assertEquals("la fila de ayer quedó intacta", yesterday, rows.firstOrNull { it.date == yesterday.date })
        val todays = rows.filter { it.date == today.toString() }
        assertEquals("debe existir una única fila nueva con la fecha de hoy", 1, todays.size)
        assertEquals("hoy", todays.single().notes)
        assertNotEquals("la fila de hoy reutilizó el id de ayer", yesterday.id, todays.single().id)
        assertEquals("ayer y hoy, nada más", 2, rows.size)
    }

    // ─── e. Un id libre se conserva y hay una sola fila por fecha ─────────────────────────────────

    @Test
    fun saveWellbeing_keepsAFreeIdAndWritesOneRowPerDate() {
        val today = LocalDate.now().toString()
        startViewModel()
        settle()

        vm.saveWellbeing(DailyWellbeingLog(id = "id-libre", date = today, notes = "primero"))
        awaitWrites()
        assertEquals("un id que ninguna otra fecha usa se conserva", "id-libre", todayWellbeing()?.id)

        // El anfitrión trae otro id para una fila de hoy que ya existe: manda el id guardado.
        vm.saveWellbeing(DailyWellbeingLog(id = "id-viejo-del-anfitrion", date = today, notes = "segundo"))
        awaitWrites()

        val rows = allRows()
        assertEquals("una sola fila para hoy, con el id guardado", listOf("id-libre"), rows.map { it.id })
        assertEquals("segundo", rows.single().notes)
    }

    // ─── Ayudantes ────────────────────────────────────────────────────────────────────────────────

    private fun startViewModel() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = AugeViewModel(app).also { viewModelStore.put("auge-vm", it) }
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

    /**
     * Espera a que termine lo que el ViewModel ya pidió con el candado de escritura (guardado, aprendizaje y
     * recálculo incluidos): el `Mutex` es justo, así que este `lock` solo entra cuando salió todo lo que estaba antes.
     */
    private fun awaitWrites() {
        val mutex = writeMutexOf(vm)
        runBlocking { withTimeout(AWAIT_MS) { mutex.lock() } }
        mutex.unlock()
    }

    /**
     * Escenario de aprendizaje: una sesión dura de hace 48 h en el historial (de ella sale el estrés de Energía con el
     * que AUGE puede reaprender τ) y los ajustes de hoy, Energía 60 y Pectorales 50, fijados hace 10 h. Se siembra
     * ANTES de crear el ViewModel para que su primer recálculo ya los use.
     */
    private fun seedLearningScenario(): DailyWellbeingLog {
        programRepository.addWorkoutLog(heavyLog(hoursAgo = 48L))
        val stored = DailyWellbeingLog(
            id = "ajustes-de-hoy",
            date = LocalDate.now().toString(),
            sleepQuality = 4,
            stressLevel = 2,
            doms = 2,
            motivation = 5,
            manualNeuralBattery = STORED_NEURAL,
            manualMuscleBatteries = mapOf("Pectorales" to 50),
            manualBatteryAnchorMs = realNowMs() - 10L * HOUR_MS,
            notes = "ajustes de hoy",
        )
        db { augeRepo.saveWellbeingLog(stored) }
        return stored
    }

    /** Fila del alta tal como la deja el commit del alta: Energía y Músculos declarados, con su ancla fija. */
    private fun onboardingRow(date: LocalDate) = DailyWellbeingLog(
        id = "alta",
        date = date.toString(),
        sleepQuality = 4,
        stressLevel = 2,
        doms = 2,
        motivation = 5,
        sleepHours = 7.5,
        manualNeuralBattery = 70,
        manualMuscleBatteries = mapOf("Pectorales" to 80),
        manualBatteryAnchorMs = FIXED_ANCHOR_MS,
        notes = "nota del alta",
        preWorkoutDiscomforts = listOf("rodilla"),
        source = WellbeingSource.ONBOARDING_INITIAL,
        capturedFields = setOf("energy", "muscle_batteries"),
    )

    private fun yesterdayRow(date: LocalDate) = DailyWellbeingLog(
        id = "fila-de-ayer",
        date = date.toString(),
        sleepQuality = 2,
        manualNeuralBattery = 55,
        manualBatteryAnchorMs = FIXED_ANCHOR_MS,
        notes = "de ayer",
    )

    /** Lo que envía el chequeo previo: la fila de hoy copiada tal cual (globales y V1), sin ancla ni procedencia. */
    private fun hostCopyOf(row: DailyWellbeingLog) = DailyWellbeingLog(
        id = row.id,
        date = row.date,
        sleepQuality = row.sleepQuality,
        stressLevel = row.stressLevel,
        doms = row.doms,
        motivation = row.motivation,
        sleepHours = row.sleepHours,
        moodState = row.moodState,
        workIntensity = row.workIntensity,
        studyIntensity = row.studyIntensity,
        manualMuscularBattery = row.manualMuscularBattery,
        manualNeuralBattery = row.manualNeuralBattery,
        manualSpinalBattery = row.manualSpinalBattery,
        manualMuscleBatteries = row.manualMuscleBatteries,
        notes = row.notes,
        preWorkoutDiscomforts = row.preWorkoutDiscomforts,
    )

    /**
     * Sesión de hace [hoursAgo] h con seis series duras (RPE 9): el motor garantiza un drenaje mínimo de Energía a partir
     * de seis series duras, así el estrés que usa el aprendizaje es > 0 sin depender del catálogo de ejercicios.
     */
    private fun heavyLog(hoursAgo: Long) = WorkoutLog(
        id = "sesion-dura",
        programId = "programa",
        sessionId = "sesion",
        sessionName = "Sesión dura",
        date = Instant.ofEpochMilli(realNowMs() - hoursAgo * HOUR_MS).toString(),
        durationMinutes = 60,
        completedExercises = listOf(
            CompletedExercise(
                exerciseId = "ejercicio-de-prueba",
                exerciseName = "Ejercicio de prueba",
                effectiveMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0)),
                sets = List(6) { index ->
                    CompletedSet(id = "serie-$index", weight = 100.0, reps = 5, rpe = 9.0)
                },
            ),
        ),
    )

    /** Único punto de este archivo que lee el reloj real: el ViewModel tampoco admite reloj inyectado. */
    private fun realNowMs(): Long = System.currentTimeMillis()

    private fun writeMutexOf(target: AugeViewModel): Mutex =
        AugeViewModel::class.java.getDeclaredField("augeWriteMutex")
            .apply { isAccessible = true }
            .get(target) as Mutex

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
        const val HOUR_MS = 3_600_000L
        const val FIXED_ANCHOR_MS = 1_760_000_000_000L
        const val STORED_NEURAL = 60
        const val EDITED_NEURAL = 45
    }
}
