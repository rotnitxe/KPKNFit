package com.example.kpkn.data.repository

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionActiveStateEntity
import com.example.kpkn.data.db.NutritionLogEntity
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * WP-S3 + WP-U2 (B3, C2): el arranque publica las filas del usuario ANTES de la importación del catálogo global y con
 * independencia de ella. Como en NutritionDurableSaveTest, el repositorio se construye por reflexión sobre una base en
 * memoria YA sembrada, esta vez con un importador inyectado (suspendido o que lanza), y se dispara UNA carga con
 * `refreshData`: así se observa el estado publicado exactamente mientras el importador sigue suspendido.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionRepositoryStartupTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val repositories = mutableListOf<NutritionRepository>()
    private lateinit var db: KpknDatabase

    private val mealId = "meal-startup"
    private val planId = "plan-startup"

    @Before
    fun setUp() {
        ShadowAlarmManager.reset()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        // Singletons que pueden venir de otras pruebas: sin repositorio de programas (no arranca la previsión) y con un
        // repositorio de cuerpo nuevo (el del repositorio de nutrición se crea sobre la base de archivo).
        ProgramRepository.closeInstance()
        BodyProgressRepository.closeInstance()
        KpknDatabase.closeInstance()
        db = KpknDatabase.createInMemory(context)
    }

    @After
    fun tearDown() = runBlocking {
        val repositoryJob = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
        repositories.forEach { (repositoryJob.get(it) as Job).cancelAndJoin() }
        repositories.clear()
        db.close()
        BodyProgressRepository.closeInstance()
        KpknDatabase.closeInstance()
        ShadowAlarmManager.reset()
    }

    // ─── Utilidades ───────────────────────────────────────────────────────

    private fun meal() = NutritionLog(
        id = mealId,
        date = LocalDate.now().toString() + "T12:00:00.000Z",
        foods = listOf(LoggedFood(foodName = "Arroz cocido", amount = 200.0, calories = 260.0, protein = 5.0, carbs = 56.0, fats = 1.0)),
    )

    /** Un log, un plan y el plan activo: las filas que NO deben depender de la importación del catálogo. */
    private suspend fun seedUserRows() {
        val dao = db.nutritionDao()
        dao.upsertLog(meal().toEntity())
        dao.upsertPlan(NutritionPlan(id = planId, name = "Plan startup", calorieTarget = 2200, isActive = true).toEntity())
        dao.upsertActiveState(NutritionActiveStateEntity(activePlanId = planId))
    }

    private fun importer(
        block: suspend (
            alreadyImported: Boolean,
            existingMeta: FoodImporter.ImportMetadata?,
            onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
        ) -> Boolean,
    ): FoodCatalogImporter = object : FoodCatalogImporter {
        override suspend fun importIfNeeded(
            db: KpknDatabase,
            context: Context,
            alreadyImported: Boolean,
            existingMeta: FoodImporter.ImportMetadata?,
            onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
        ): Boolean = block(alreadyImported, existingMeta, onMetaUpdated)
    }

    private fun repository(importer: FoodCatalogImporter): NutritionRepository {
        val ctor = NutritionRepository::class.java.getDeclaredConstructor(
            Context::class.java,
            KpknDatabase::class.java,
            Boolean::class.javaPrimitiveType,
            FoodCatalogImporter::class.java,
        )
        ctor.isAccessible = true
        return ctor.newInstance(context, db, false, importer).also { repositories += it }
    }

    private fun scheduledAlarms(): Int =
        shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size

    // ─── Fase 1 antes que la importación ──────────────────────────────────

    @Test
    fun `user rows and the static catalog are published while the catalog import is still suspended`() = runBlocking {
        seedUserRows()
        val importerEntered = CompletableDeferred<Unit>()
        val releaseImport = CompletableDeferred<Unit>()
        val repo = repository(
            importer { _, _, _ ->
                importerEntered.complete(Unit)
                releaseImport.await()
                false
            },
        )

        repo.refreshData(context)
        // La fase 2 solo empieza cuando la fase 1 terminó: el importador está dentro y suspendido.
        withTimeout(TIMEOUT_MS) { importerEntered.await() }

        assertFalse("the importer must still be suspended", releaseImport.isCompleted)
        assertEquals(listOf(mealId), repo.nutritionLogs.value.map { it.id })
        assertEquals(listOf(planId), repo.nutritionPlans.value.map { it.id })
        assertEquals(planId, repo.activeNutritionPlanId.value)
        assertTrue(
            "static catalog must be published before the import (was ${repo.foodDatabase.value.size})",
            repo.foodDatabase.value.size >= 215,
        )

        releaseImport.complete(Unit)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
    }

    @Test
    fun `a failing catalog import keeps the published user rows and the measurement reminder`() = runBlocking {
        seedUserRows()
        // El recordatorio vive en AlarmManager. Lo agenda el repositorio de cuerpo y se cancela: de aquí en adelante solo
        // el arranque del repositorio de nutrición puede volver a agendarlo, y solo un fallo de importación mal manejado
        // (como el catch de antes) podría volver a cancelarlo.
        val body = BodyProgressRepository.getInstance(context)
        body.awaitReady()
        body.updateMeasurementSchedule(
            MeasurementSchedule(
                enabled = true,
                intervalDays = 7,
                nextDate = LocalDate.now().plusDays(3).toString(),
                reminderHour = 9,
                reminderMinute = 0,
            ),
        )
        assertEquals(1, scheduledAlarms())
        NutritionNotificationManager(context).cancelMeasurementReminder()
        assertEquals(0, scheduledAlarms())

        val importerEntered = CompletableDeferred<Unit>()
        val repo = repository(
            importer { _, _, _ ->
                importerEntered.complete(Unit)
                throw IllegalStateException("catalog import boom")
            },
        )

        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { importerEntered.await() }
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }

        assertEquals(listOf(mealId), repo.nutritionLogs.value.map { it.id })
        assertEquals(listOf(planId), repo.nutritionPlans.value.map { it.id })
        assertEquals(planId, repo.activeNutritionPlanId.value)
        assertTrue(repo.foodDatabase.value.size >= 215)
        assertEquals("a failed import must not cancel the measurement reminder", 1, scheduledAlarms())
        assertNull("the import indicator must not stay stuck", FoodImporter.importProgress.value)
    }

    @Test
    fun `an unreadable user row falls back to the static catalog and still lets the import run`() = runBlocking {
        db.nutritionDao().upsertLog(
            NutritionLogEntity(id = "corrupt", date = "2026-10-01T12:00:00.000Z", mealType = "LUNCH", data = "{not json"),
        )
        val importerEntered = CompletableDeferred<Unit>()
        val repo = repository(
            importer { _, _, _ ->
                importerEntered.complete(Unit)
                false
            },
        )

        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { importerEntered.await() }
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }

        assertTrue(repo.nutritionLogs.value.isEmpty())
        assertTrue("minimal fallback keeps the static catalog", repo.foodDatabase.value.size >= 215)
    }

    // ─── Cableado hacia el importador ─────────────────────────────────────

    @Test
    fun `the importer gets the stored meta and what it reports is kept for the next start`() = runBlocking {
        val seen = CopyOnWriteArrayList<Pair<Boolean, FoodImporter.ImportMetadata?>>()
        val reported = FoodImporter.ImportMetadata(
            version = FoodImporter.DATA_VERSION,
            checksum = FoodImporter.datasetFingerprint(),
            importedAt = "2026-10-02T10:00:00Z",
        )
        val repo = repository(
            importer { alreadyImported, existingMeta, onMetaUpdated ->
                seen += alreadyImported to existingMeta
                onMetaUpdated(reported)
                true
            },
        )

        // Primer arranque: catálogo vacío y sin meta.
        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        assertEquals(listOf<Pair<Boolean, FoodImporter.ImportMetadata?>>(false to null), seen.toList())
        assertEquals(
            NutritionRepository.FoodCatalogMeta(reported.version, reported.checksum, reported.importedAt),
            repo.getFoodCatalogMetaForBackup(),
        )

        // La importación confirmó filas: el siguiente arranque ve catálogo presente y la meta guardada.
        db.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity(foodId = "off_1", name = "Yogur natural")))
        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        assertEquals(2, seen.size)
        assertEquals(true to reported, seen.last())
    }

    @Test
    fun `overlapping loads never run two catalog imports at the same time`() = runBlocking {
        val calls = AtomicInteger(0)
        val running = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val repo = repository(
            importer { _, _, _ ->
                peak.accumulateAndGet(running.incrementAndGet()) { a, b -> maxOf(a, b) }
                try {
                    if (calls.incrementAndGet() == 1) {
                        firstEntered.complete(Unit)
                        releaseFirst.await()
                    }
                } finally {
                    running.decrementAndGet()
                }
                false
            },
        )

        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { firstEntered.await() }
        // Segunda carga mientras la primera importación sigue suspendida: publica sus filas, pero su importación espera.
        repo.refreshData(context)
        repeat(60) { if (calls.get() < 2) delay(20) }
        assertEquals("the second import must wait for the first", 1, calls.get())

        releaseFirst.complete(Unit)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        assertEquals(2, calls.get())
        assertEquals(1, peak.get())
    }

    // ─── Compuerta de versión: arranque sin leer los CSV ──────────────────

    /** Cuenta cuántas veces se piden los assets: la compuerta de versión no debe abrir ninguno. */
    private class AssetCountingContext(base: Context) : ContextWrapper(base) {
        val assetAccesses = AtomicInteger(0)

        override fun getAssets(): AssetManager {
            assetAccesses.incrementAndGet()
            return super.getAssets()
        }
    }

    @Test
    fun `a start whose stored meta is current never opens the catalog assets`() = runBlocking {
        // Catálogo ya importado (una fila) y el importador REAL: antes se hasheaban ~72 MB de CSV en cada arranque en
        // frío, incluso cuando nada había cambiado, y solo después se publicaban las comidas del usuario.
        db.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity(foodId = "off_1", name = "Yogur natural")))
        val repo = repository(FoodCatalogImporter.Default)
        val spy = AssetCountingContext(context)
        spy.assets
        assertEquals("sanity: the counter sees asset requests", 1, spy.assetAccesses.get())
        spy.assetAccesses.set(0)

        val current = NutritionRepository.FoodCatalogMeta(
            FoodImporter.DATA_VERSION,
            FoodImporter.datasetFingerprint(),
            "2026-10-02T10:00:00Z",
        )
        repo.restoreFoodCatalogMeta(current)
        repo.refreshData(spy)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        assertEquals("the version gate must decide without opening any CSV", 0, spy.assetAccesses.get())
        assertEquals(current, repo.getFoodCatalogMetaForBackup())

        // Instalación anterior a WP-S3: la meta guardada trae el SHA-256 de los CSV con la MISMA versión de datos. Se
        // adopta la huella vigente en memoria: ni se reimporta ni se leen los assets, y la meta guardada no se toca.
        val legacy = NutritionRepository.FoodCatalogMeta(FoodImporter.DATA_VERSION, "ab12".repeat(16), "2026-07-25T10:00:00Z")
        repo.restoreFoodCatalogMeta(legacy)
        repo.refreshData(spy)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        assertEquals("a legacy checksum at the current version must not trigger a re-import", 0, spy.assetAccesses.get())
        assertEquals(legacy, repo.getFoodCatalogMetaForBackup())
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
