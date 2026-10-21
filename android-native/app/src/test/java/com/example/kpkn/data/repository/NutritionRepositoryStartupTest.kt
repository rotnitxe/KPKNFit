package com.example.kpkn.data.repository

import com.example.kpkn.testsupport.SyncRoomExecutor
import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.MealTemplateEntity
import com.example.kpkn.data.db.NutritionActiveStateEntity
import com.example.kpkn.data.db.NutritionLogEntity
import com.example.kpkn.data.db.NutritionPlanEntity
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MeasurementSchedule
import com.example.kpkn.data.models.MealTemplate
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.services.nutrition.NutritionNotificationManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowLog
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
@OptIn(ExperimentalCoroutinesApi::class)
class NutritionRepositoryStartupTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val repositories = mutableListOf<NutritionRepository>()
    private lateinit var db: KpknDatabase

    private val mealId = "meal-startup"
    private val planId = "plan-startup"

    @Before
    fun setUp() {
        SyncRoomExecutor.install()
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
        Dispatchers.resetMain()
        SyncRoomExecutor.uninstall()
    }

    // ─── Utilidades ───────────────────────────────────────────────────────

    private fun meal() = NutritionLog(
        id = mealId,
        date = LocalDate.now().toString() + "T12:00:00.000Z",
        foods = listOf(LoggedFood(foodName = "Arroz cocido", amount = 200.0, calories = 260.0, protein = 5.0, carbs = 56.0, fats = 1.0)),
    )

    private fun template() = MealTemplate(id = "template-startup", name = "Comida guardada", foods = meal().foods)

    private fun snapshot() = DailyGoalSnapshot(
        date = LocalDate.now().minusDays(1).toString(),
        planId = planId,
        calorieTargetKcal = 2100,
        capturedAtEpochMs = 1L,
    )

    /** Filas sanas que deben publicarse aunque el catálogo o alguna otra fila fallen. */
    private suspend fun seedUserRows() {
        val dao = db.nutritionDao()
        dao.upsertLog(meal().toEntity())
        dao.upsertPlan(NutritionPlan(id = planId, name = "Plan startup", calorieTarget = 2200, isActive = true).toEntity())
        dao.upsertActiveState(NutritionActiveStateEntity(activePlanId = planId))
        dao.upsertTemplate(template().toEntity())
        dao.insertDailyGoalSnapshot(snapshot().toEntity())
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
    fun `una comida ilegible queda en cuarentena y las filas sanas y el catalogo se cargan`() = runBlocking {
        seedUserRows()
        val corrupt = NutritionLogEntity(
            id = "corrupt", date = "2026-10-01T12:00:00.000Z", mealType = "LUNCH", data = "{contenido privado ilegible",
        )
        db.nutritionDao().upsertLog(corrupt)
        ShadowLog.clear()
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

        assertEquals(listOf(meal()), repo.nutritionLogs.value)
        assertEquals(listOf(template()), repo.mealTemplates.value)
        assertEquals(planId, repo.activeNutritionPlan?.id)
        assertEquals(listOf(snapshot()), repo.dailyGoalSnapshots.value)
        assertEquals(listOf(UnreadableNutritionRow(corrupt.id, "nutrition_logs")), repo.unreadableRows.value)
        assertEquals(corrupt, db.nutritionDao().getAllLogs().single { it.id == corrupt.id })
        assertTrue("el catálogo estático se publica con normalidad", repo.foodDatabase.value.size >= 215)

        val warning = ShadowLog.getLogsForTag("NutritionRepository").single { it.msg.startsWith("Fila ilegible") }
        assertEquals(Log.WARN, warning.type)
        assertTrue(warning.msg.contains(corrupt.id) && warning.msg.contains("nutrition_logs"))
        assertFalse(warning.msg.contains(corrupt.data))
        assertNull("la excepción de JSON puede contener datos privados", warning.throwable)

        repo.saveNutritionLog(meal().copy(id = "new-meal"))
        assertEquals(corrupt, db.nutritionDao().getAllLogs().single { it.id == corrupt.id })
    }

    @Test
    fun `una plantilla ilegible no oculta las sanas ni se borra al olvidar el aprendizaje`() = runBlocking {
        seedUserRows()
        val corrupt = MealTemplateEntity(id = "corrupt", name = "Plantilla ilegible", data = "{no es json")
        db.nutritionDao().upsertTemplate(corrupt)
        val repo = repository(importer { _, _, _ -> false })

        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }

        assertEquals(listOf(template()), repo.mealTemplates.value)
        assertEquals(listOf(meal()), repo.nutritionLogs.value)
        assertEquals(planId, repo.activeNutritionPlan?.id)
        assertEquals(listOf(snapshot()), repo.dailyGoalSnapshots.value)
        assertEquals(listOf(UnreadableNutritionRow(corrupt.id, "nutrition_templates")), repo.unreadableRows.value)
        assertEquals(corrupt, db.nutritionDao().getAllTemplates().single { it.id == corrupt.id })

        repo.clearLearnedResolutions()

        assertTrue(repo.mealTemplates.value.isEmpty())
        assertEquals(listOf(corrupt), db.nutritionDao().getAllTemplates())
        assertEquals(listOf(UnreadableNutritionRow(corrupt.id, "nutrition_templates")), repo.unreadableRows.value)
        assertEquals(listOf(mealId), db.nutritionDao().getAllLogs().map { it.id })
    }

    @Test
    fun `un plan ilegible se conserva al activar y republicar un plan sano`() = runBlocking {
        seedUserRows()
        val corrupt = NutritionPlanEntity(id = "corrupt", name = "Plan ilegible", isActive = false, data = "{no es json")
        db.nutritionDao().upsertPlan(corrupt)
        db.nutritionDao().upsertPlan(NutritionPlan(id = "other-plan", name = "Otro plan").toEntity())
        val repo = repository(importer { _, _, _ -> false })

        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }

        assertEquals(setOf(planId, "other-plan"), repo.nutritionPlans.value.map { it.id }.toSet())
        assertEquals(planId, repo.activeNutritionPlan?.id)
        assertEquals(listOf(meal()), repo.nutritionLogs.value)
        assertEquals(listOf(template()), repo.mealTemplates.value)
        assertEquals(listOf(snapshot()), repo.dailyGoalSnapshots.value)
        assertEquals(listOf(UnreadableNutritionRow(corrupt.id, "nutrition_plans")), repo.unreadableRows.value)

        repo.activatePlan("other-plan")
        withTimeout(TIMEOUT_MS) {
            while (db.nutritionDao().getActiveState()?.activePlanId != "other-plan") delay(10)
        }
        repo.publishNutritionPlanCommit()

        assertEquals("other-plan", repo.activeNutritionPlan?.id)
        assertEquals(setOf(planId, "other-plan"), repo.nutritionPlans.value.map { it.id }.toSet())
        assertTrue(repo.dailyGoalSnapshots.value.contains(snapshot()))
        assertEquals(corrupt, db.nutritionDao().getAllPlans().single { it.id == corrupt.id })
        assertEquals(listOf(UnreadableNutritionRow(corrupt.id, "nutrition_plans")), repo.unreadableRows.value)
    }

    @Test
    fun `la cuarentena distingue tablas con el mismo id y se actualiza tras reparar una fila`() = runBlocking {
        seedUserRows()
        val corruptLog = NutritionLogEntity("corrupt", "2026-10-01", "LUNCH", "{log ilegible")
        val corruptTemplate = MealTemplateEntity("corrupt", "Plantilla", "{plantilla ilegible")
        val corruptPlan = NutritionPlanEntity("corrupt", "Plan", false, "{plan ilegible")
        val dao = db.nutritionDao()
        dao.upsertLog(corruptLog)
        dao.upsertTemplate(corruptTemplate)
        dao.upsertPlan(corruptPlan)
        val repo = repository(importer { _, _, _ -> false })

        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }

        assertEquals(
            setOf("nutrition_logs", "nutrition_templates", "nutrition_plans").map { UnreadableNutritionRow("corrupt", it) }.toSet(),
            repo.unreadableRows.value.toSet(),
        )
        repo.publishNutritionPlanCommit()
        assertEquals("releer solo planes no borra el diagnóstico de las otras tablas", 3, repo.unreadableRows.value.size)

        // Simula una reparación externa explícita; el repositorio nunca reescribe la fila en cuarentena.
        dao.upsertLog(meal().copy(id = corruptLog.id).toEntity())
        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }

        assertEquals(setOf(mealId, corruptLog.id), repo.nutritionLogs.value.map { it.id }.toSet())
        assertEquals(
            setOf(UnreadableNutritionRow("corrupt", "nutrition_templates"), UnreadableNutritionRow("corrupt", "nutrition_plans")),
            repo.unreadableRows.value.toSet(),
        )
        assertEquals(corruptTemplate, dao.getAllTemplates().single { it.id == "corrupt" })
        assertEquals(corruptPlan, dao.getAllPlans().single { it.id == "corrupt" })
    }

    // ─── Cableado hacia el importador ─────────────────────────────────────

    @Test
    fun `the importer gets the stored meta and what it reports is kept for the next start`() = runBlocking {
        val seen = CopyOnWriteArrayList<Pair<Boolean, FoodImporter.ImportMetadata?>>()
        val reported = FoodImporter.ImportMetadata(
            version = FoodImporter.DATA_VERSION,
            checksum = FoodImporter.versionFingerprint(),
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

    /** Cuenta cuántas veces se piden los assets: la compuerta de versión solo puede pedir el manifiesto, nunca un CSV. */
    private open class AssetCountingContext(base: Context) : ContextWrapper(base) {
        val assetAccesses = AtomicInteger(0)

        override fun getAssets(): AssetManager {
            assetAccesses.incrementAndGet()
            return super.getAssets()
        }
    }

    /** Un APK sin manifiesto (o con uno ilegible): sus assets no responden. */
    private class NoAssetsContext(base: Context) : AssetCountingContext(base) {
        override fun getAssets(): AssetManager {
            assetAccesses.incrementAndGet()
            throw IllegalStateException("the assets are not available")
        }
    }

    @Test
    fun `a start whose stored meta is current reads the manifest and never a catalog CSV`() = runBlocking {
        // Catálogo ya importado (una fila) y el importador REAL: antes se hasheaban ~72 MB de CSV en cada arranque en
        // frío, incluso cuando nada había cambiado, y solo después se publicaban las comidas del usuario.
        db.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity(foodId = "off_1", name = "Yogur natural")))
        val repo = repository(FoodCatalogImporter.Default)
        val spy = AssetCountingContext(context)
        spy.assets
        assertEquals("sanity: the counter sees asset requests", 1, spy.assetAccesses.get())
        spy.assetAccesses.set(0)

        // WP-S10: la huella de ESTE build es "v<DATA_VERSION>+<sha256>" si los assets fusionados traen el manifiesto que genera Gradle y "v<DATA_VERSION>" si no.
        val current = NutritionRepository.FoodCatalogMeta(
            FoodImporter.DATA_VERSION,
            FoodImporter.expectedFingerprint(context),
            "2026-10-02T10:00:00Z",
        )
        repo.restoreFoodCatalogMeta(current)
        repo.refreshData(spy)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        // UNA petición: el manifiesto (< 1 KB). Un CSV sería la importación, y la meta dice que no hay nada que importar.
        assertEquals("the version gate asks for the manifest only, never for a CSV", 1, spy.assetAccesses.get())
        assertEquals(current, repo.getFoodCatalogMetaForBackup())
    }

    @Test
    fun `without a manifest the gate falls back to the data version and adopts a legacy checksum instead of importing`() = runBlocking {
        db.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity(foodId = "off_1", name = "Yogur natural")))
        val repo = repository(FoodCatalogImporter.Default)
        val spy = NoAssetsContext(context)

        // Instalación anterior a WP-S3: la meta guardada trae el SHA-256 de los CSV con la MISMA versión de datos. Sin manifiesto la
        // huella es solo la versión: se adopta en memoria, ni se reimporta ni se leen los CSV, y la meta guardada no se toca.
        val legacy = NutritionRepository.FoodCatalogMeta(FoodImporter.DATA_VERSION, "ab12".repeat(16), "2026-07-25T10:00:00Z")
        repo.restoreFoodCatalogMeta(legacy)
        repo.refreshData(spy)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        assertEquals("only the manifest was asked for", 1, spy.assetAccesses.get())
        assertEquals("a legacy checksum at the current version must not trigger a re-import", legacy, repo.getFoodCatalogMetaForBackup())

        // Y una meta con la huella de versión (la de WP-S3 a WP-S9) tampoco reimporta: la compuerta es estable, no hay bucle.
        val current = NutritionRepository.FoodCatalogMeta(FoodImporter.DATA_VERSION, FoodImporter.versionFingerprint(), "2026-10-02T10:00:00Z")
        repo.restoreFoodCatalogMeta(current)
        spy.assetAccesses.set(0)
        repo.refreshData(spy)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        assertEquals(1, spy.assetAccesses.get())
        assertEquals(current, repo.getFoodCatalogMetaForBackup())
    }

    @Test
    fun `the expected fingerprint is the data version plus the manifest of the assets when the build has one`() {
        val fingerprint = FoodImporter.expectedFingerprint(context)

        assertTrue("fingerprint '$fingerprint'", fingerprint.isNotBlank() && fingerprint.startsWith("v${FoodImporter.DATA_VERSION}"))
        // Esté o no el manifiesto generado en los assets fusionados, es la composición pura de lo que traen, y no cambia entre llamadas.
        val manifest = runCatching { context.assets.open(FoodImporter.MANIFEST_ASSET).use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        assertEquals(FoodImporter.composeFingerprint(manifest?.let(FoodImporter::parseManifestFingerprint)), fingerprint)
        assertEquals(fingerprint, FoodImporter.expectedFingerprint(context))
    }

    @Test
    fun `an unreadable manifest never crashes the gate and leaves the data version as the fingerprint`() {
        assertEquals(FoodImporter.versionFingerprint(), FoodImporter.expectedFingerprint(NoAssetsContext(context)))
    }

    // ─── WP-S4: el índice del resolvedor sigue al catálogo ────────────────

    @Test
    fun `initFoodIndex before phase 1 is a no-op and the publish rebuilds the index with the static catalog`() = runBlocking {
        // B5: filas globales ya en la base (instalación previa) pero el catálogo estático aún sin publicar. Antes, un
        // initFoodIndex() temprano (prewarm del drawer, share intent) construía y CONGELABA un índice con solo esta fila.
        db.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity(foodId = "off_1", name = "Yogur natural")))
        val repo = repository(importer { _, _, _ -> false })
        val index = repo.foodIndexForTests()

        repo.initFoodIndex()
        assertFalse("nothing reliable to index before the static catalog is published", index.isBuilt())
        assertEquals(0, index.size())
        assertEquals(-1, index.generation)

        repo.refreshData(context)
        // La fase 3 llama a initFoodIndex() con el catálogo ya publicado.
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }

        assertTrue(index.isBuilt())
        assertEquals("the index carries the published generation", repo.catalogGenerationForTests(), index.generation)
        assertEquals("static catalog + the global row", repo.foodDatabase.value.size + 1, index.size())
        assertNotNull("static rows are in the index", index.getFood("gen005"))
        assertNotNull(index.getFood("off_1"))
        // Misma instancia que lee el resolvedor: "arroz" solo existe en el catálogo estático.
        val resolved = repo.resolveFoodWithSmartResolver("arroz")
        assertTrue("the resolver sees the rebuilt index", resolved.candidates.isNotEmpty())
    }

    @Test
    fun `after the importer completes the index generation advances and contains the imported rows`() = runBlocking {
        val importerEntered = CompletableDeferred<Unit>()
        val releaseImport = CompletableDeferred<Unit>()
        val repo = repository(
            importer { _, _, _ ->
                importerEntered.complete(Unit)
                releaseImport.await()
                // El importador real confirma sus filas en la base y responde true.
                db.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity(foodId = "off_imported", name = "Yogur importado")))
                true
            },
        )
        val index = repo.foodIndexForTests()

        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { importerEntered.await() }

        // Un análisis durante la importación construye el índice con el catálogo estático y sin las filas por llegar.
        repo.initFoodIndex()
        val duringImport = index.generation
        assertEquals(repo.catalogGenerationForTests(), duringImport)
        assertNull(index.getFood("off_imported"))
        assertNotNull(index.getFood("gen005"))

        releaseImport.complete(Unit)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }

        assertTrue("an effective import advances the generation", index.generation > duringImport)
        assertEquals(repo.catalogGenerationForTests(), index.generation)
        assertNotNull("the imported row is in the resolver index", index.getFood("off_imported"))
        assertTrue(index.search("importado").contains("off_imported"))
    }

    @Test
    fun `a refresh after a restore rebuilds the index from the restored rows`() = runBlocking {
        val repo = repository(importer { _, _, _ -> false })
        val index = repo.foodIndexForTests()
        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        val before = index.generation
        assertNull(index.getFood("off_restored"))

        // Una restauración reemplaza las filas bajo el repositorio y termina siempre con refreshData (SettingsViewModel).
        db.nutritionDao().insertGlobalFoods(listOf(GlobalFoodEntity(foodId = "off_restored", name = "Queso restaurado")))
        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }

        assertTrue("the refresh advances the generation", index.generation > before)
        assertEquals(repo.catalogGenerationForTests(), index.generation)
        assertNotNull(index.getFood("off_restored"))
    }

    @Test
    fun `initFoodIndex does not rebuild while the catalog generation is unchanged`() = runBlocking {
        val repo = repository(importer { _, _, _ -> false })
        val index = repo.foodIndexForTests()
        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        val built = index.getFood("gen005")
        assertNotNull(built)

        repo.initFoodIndex()
        repo.initFoodIndex()
        // Cada build crea fichas nuevas: la misma instancia prueba que no hubo reconstrucción.
        assertSame("same generation: no rebuild", built, index.getFood("gen005"))

        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        assertNotSame("a refresh bumps the generation and the next init rebuilds", built, index.getFood("gen005"))
    }

    @Test
    fun `refreshData stales the index at once and the publish bumps the generation again`() = runBlocking {
        val repo = repository(importer { _, _, _ -> false })
        repo.refreshData(context)
        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        val before = repo.catalogGenerationForTests()
        assertEquals(before, repo.foodIndexForTests().generation)

        // Con el candado de guardado tomado, la fase 1 de la recarga no puede publicar: solo cuenta el aviso síncrono.
        val saveMutex = NutritionRepository::class.java.getDeclaredField("foodSaveMutex").apply { isAccessible = true }.get(repo) as Mutex
        val whileHeld = saveMutex.withLock {
            repo.refreshData(context)
            val bumped = repo.catalogGenerationForTests()
            assertTrue("refreshData bumps before reloading anything", bumped > before)
            assertTrue("the index is stale until it is rebuilt", repo.foodIndexForTests().generation < bumped)
            bumped
        }

        withTimeout(TIMEOUT_MS) { repo.awaitStartupLoadForTests() }
        assertTrue("publishing the reloaded state bumps again", repo.catalogGenerationForTests() > whileHeld)
        assertEquals("the index catches up with the last generation", repo.catalogGenerationForTests(), repo.foodIndexForTests().generation)
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
