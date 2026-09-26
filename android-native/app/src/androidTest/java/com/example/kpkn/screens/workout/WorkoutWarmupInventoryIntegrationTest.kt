package com.example.kpkn.screens.workout

import android.content.Context
import android.os.Looper
import android.os.Process
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.DumbbellPairStock
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.canonicalEquipmentKind
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue
import org.junit.Assume

/**
 * Contrato REAL del hook `WorkoutViewModel.getWarmupSuggestedWeight` en
 * Android, con el constructor real de la VM (Context + RestAlertManager),
 * repositorio/Room reales y catálogo real desde assets. Sin reflexión ni
 * asignación de campos privados: solo APIs productivas
 * (`ProgramRepository.init/updateSettings`, `initializeExerciseDatabase`,
 * parámetro público `workingWeightAnchor`).
 *
 * Cubre el wiring acreditado:
 * - Settings de inventario se consume de verdad (barra 20 + discos → 40/60/80).
 * - El id de configuración del catálogo resuelve y su kind es `barbell`
 *   (nunca `general_gym` por defecto).
 * - Inventario explícito SIN barra → null (pendiente), nunca 20 kg fantasma.
 * - Mancuerna declarada → el kind del catálogo dirige el resolvedor de pareja
 *   (12.5 kg reales, no una barra).
 *
 * El piso por etiqueta (55 kg sobre stock 40 kg → pendiente) queda acreditado
 * por el test de dominio `WarmupProductionLoadRouteTest.floor_above_the_declared_stock_is_pending_never_ready`;
 * a nivel VM exigiría crear un `WorkoutContextProfile` activo, que no tiene API
 * de test pública.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutWarmupInventoryIntegrationTest {

    private lateinit var context: Context
    private lateinit var repository: ProgramRepository
    private lateinit var db: KpknDatabase
    private var settingsSnapshot: Settings? = null
    private val viewModelStore = ViewModelStore()
    private var workoutViewModel: WorkoutViewModel? = null
    private var equipmentInventoryTouched = false

    /** IDs propios: nunca tocan programas o sesiones existentes. */
    private val programId = "androidtest-warmup-inventory-${System.nanoTime()}"
    private val sessionId = "androidtest-warmup-inventory-session-${System.nanoTime()}"

    private val barInventory = EquipmentInventory(
        barbellWeightKg = 20.0,
        plates = listOf(
            PlateStock(weightKg = 20.0, countPerSide = 1),
            PlateStock(weightKg = 10.0, countPerSide = 1),
            PlateStock(weightKg = 5.0, countPerSide = 1),
            PlateStock(weightKg = 2.5, countPerSide = 2),
        ),
    )

    private fun exerciseWithWarmups(configurationId: String): Exercise = Exercise(
        id = "androidtest-warmup-$configurationId",
        name = "Press banca",
        catalogConfigurationId = configurationId,
        canonicalExerciseId = configurationId,
        exerciseId = configurationId,
        exerciseDbId = configurationId,
        sets = listOf(
            ExerciseSet(
                id = "set-0",
                targetReps = 5,
                weight = 100.0,
                loadModeV2 = LoadModeV2.LOAD,
            ),
        ),
        warmupSets = listOf(
            WarmupSetDefinition(id = "w0", percentageOfWorkingWeight = 40.0, targetReps = 8),
            WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 60.0, targetReps = 5),
            WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = 80.0, targetReps = 3),
        ),
    )

    /**
     * ViewModel real con el constructor público en Main. El test no habilita
     * voz ni inicia entrada de micrófono; la API actual de WorkoutViewModel sí
     * inicializa su TTS compartido al construirlo (sin solicitar habla), y
     * ViewModelStore.clear() lo apaga durante cleanup.
     */
    private fun viewModelOnMain(): WorkoutViewModel {
        if (Looper.myLooper() == Looper.getMainLooper()) return createViewModelOnMain()
        lateinit var result: WorkoutViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = createViewModelOnMain()
        }
        return result
    }

    private fun createViewModelOnMain(): WorkoutViewModel = workoutViewModel ?: WorkoutViewModel(
        appContext = context,
        programId = programId,
        sessionId = sessionId,
        restAlertManager = WorkoutRestAlertManager(context),
    ).also { vm ->
        workoutViewModel = vm
        viewModelStore.put("workout-warmup-inventory-vm", vm)
    }

    @Before
    fun setUp() {
        // Guardia ANTES de cualquier mutación: solo app normal del QA (uid 10xxxx).
        val uid = Process.myUid()
        Assume.assumeTrue(
            "La prueba solo puede correr en usuario QA 10; uid=$uid",
            uid / 100000 == 10,
        )

        context = ApplicationProvider.getApplicationContext()
        db = KpknDatabase.getInstance(context)
        // Índice real de catálogo (solo lectura de assets, idempotente).
        initializeExerciseDatabase(context)
        // La VM accede por `ProgramRepository.getInstance()`; en instrumentación
        // sin Activity nadie lo inicializa todavía (init() es idempotente).
        repository = ProgramRepository.init(context)
        runBlocking {
            withTimeout(60_000L) { repository.isReady.first { it } }
        }
        val ongoingWorkout = runBlocking { db.stateDao().getOngoingWorkout() }
        Assume.assumeTrue(
            "No se construye ni limpia la VM mientras QA tenga un entrenamiento en curso.",
            ongoingWorkout == null,
        )
        runBlocking {
            // Captura solo tras completar la hidratación desde Room: settings.value
            // antes de isReady puede ser el Settings() inicial y no el del QA.
            settingsSnapshot = db.settingsDao().get()?.toSettings() ?: repository.settings.value
        }
    }

    @After
    fun tearDown() {
        if (settingsSnapshot == null) return

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModelStore.clear()
            workoutViewModel = null
        }

        if (equipmentInventoryTouched) settingsSnapshot?.let { snapshot ->
            repository.updateSettings { current ->
                current.copy(equipmentInventory = snapshot.equipmentInventory)
            }
            awaitEquipmentInventory(snapshot.equipmentInventory)
        }
    }

    /** updateSettings is fire-and-forget; cleanup and setup both wait for Room. */
    private fun awaitEquipmentInventory(expected: EquipmentInventory?) {
        runBlocking {
            withTimeout(30_000L) {
                while (db.settingsDao().get()?.toSettings()?.equipmentInventory != expected) {
                    delay(25L)
                }
            }
        }
    }

    private fun updateEquipmentInventory(expected: EquipmentInventory) {
        equipmentInventoryTouched = true
        repository.updateSettings { current -> current.copy(equipmentInventory = expected) }
        awaitEquipmentInventory(expected)
        assertEquals(expected, repository.settings.value.equipmentInventory)
    }

    @Test
    fun declared_bar_inventory_is_consumed_and_resolves_real_40_60_80() {
        updateEquipmentInventory(barInventory)

        // El id real del catálogo resuelve y su kind es `barbell`, no general_gym.
        val info = resolveCatalogExerciseInfo(
            catalogConfigurationId = "bench_press__barbell",
            exerciseDbId = null,
            exerciseId = null,
            exerciseName = null,
        )
        assertNotNull("El id del catálogo debe resolver en el dispositivo", info)
        assertEquals("barbell", canonicalEquipmentKind(info!!.equipment))
        assertTrue(canonicalEquipmentKind(info.equipment) != "general_gym")

        val exercise = exerciseWithWarmups("bench_press__barbell")
        listOf(40.0, 60.0, 80.0).forEachIndexed { index, expected ->
            val suggested = viewModelOnMain().getWarmupSuggestedWeight(
                exercise = exercise,
                warmupIndex = index,
                activeTag = null,
                workingWeightAnchor = 100.0,
            )
            assertEquals("warmup $index alcanzable con el material declarado", expected, suggested!!, 0.001)
        }
    }

    @Test
    fun explicit_inventory_without_bar_returns_pending_not_a_phantom_20() {
        // La ausencia explícita de barra prevalece sobre la barra legacy de 20 kg.
        val withoutBar = EquipmentInventory(plates = listOf(PlateStock(weightKg = 10.0, countPerSide = 2)))
        updateEquipmentInventory(withoutBar)

        val exercise = exerciseWithWarmups("bench_press__barbell")
        listOf(0, 1, 2).forEach { index ->
            val suggested = viewModelOnMain().getWarmupSuggestedWeight(
                exercise = exercise,
                warmupIndex = index,
                activeTag = null,
                workingWeightAnchor = 100.0,
            )
            assertNull("Sin barra declarada el paso queda pendiente (nunca 20 kg)", suggested)
        }
    }

    @Test
    fun dumbbell_kind_from_catalog_drives_the_declared_pair_stock() {
        val dumbbellInventory = EquipmentInventory(
            dumbbells = listOf(
                DumbbellPairStock(weightPerUnitKg = 12.5, pairAvailable = true),
                DumbbellPairStock(weightPerUnitKg = 7.5, pairAvailable = true),
            )
        )
        updateEquipmentInventory(dumbbellInventory)

        // El kind del catálogo (Mancuerna → dumbbells) dirige el resolvedor.
        val info = resolveCatalogExerciseInfo(
            catalogConfigurationId = "bench_press__dumbbells",
            exerciseDbId = null,
            exerciseId = null,
            exerciseName = null,
        )
        assertNotNull(info)
        assertEquals("dumbbells", canonicalEquipmentKind(info!!.equipment))

        val exercise = exerciseWithWarmups("bench_press__dumbbells")
        listOf(0, 1, 2).forEach { index ->
            val suggested = viewModelOnMain().getWarmupSuggestedWeight(
                exercise = exercise,
                warmupIndex = index,
                activeTag = null,
                workingWeightAnchor = 100.0,
            )
            // Objetivos 40/60/80 kg → la pareja declarada más alta (12.5), no 20 de barra.
            assertEquals("pareja de mancuernas declarada", 12.5, suggested!!, 0.001)
        }
    }
}
