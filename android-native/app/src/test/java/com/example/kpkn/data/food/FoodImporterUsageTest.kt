package com.example.kpkn.data.food

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * WP-S8 (B4): el catálogo global se reemplaza en UNA transacción corta que conserva el uso (`usageCount`,
 * `lastUsedAt`) de las filas que siguen en el dataset, deja el índice FTS sin entradas huérfanas y, si algo falla, hace
 * rollback. Room real en memoria (como NutritionDurableSaveTest); el análisis de los CSV se sustituye por filas
 * sintéticas porque aquí solo importa lo que ocurre en la base.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class FoodImporterUsageTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KpknDatabase

    @Before
    fun setUp() {
        KpknDatabase.closeInstance()
        db = KpknDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
        KpknDatabase.closeInstance()
    }

    // ─── Utilidades ───────────────────────────────────────────────────────

    private fun dao(): NutritionDao = db.nutritionDao()

    private fun food(id: String, name: String, usage: Int = 0, lastUsedAt: String? = null) =
        GlobalFoodEntity(foodId = id, name = name, normalizedName = name.lowercase(), usageCount = usage, lastUsedAt = lastUsedAt)

    /** Docids del índice FTS que casan con [term] (el índice, no la tabla de contenido). */
    private fun matchDocids(term: String): List<Long> =
        db.openHelper.writableDatabase
            .query("SELECT docid FROM global_foods_fts WHERE global_foods_fts MATCH ? ORDER BY docid", arrayOf(term))
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }

    private fun contentRowIds(): Set<Long> =
        db.openHelper.writableDatabase.query("SELECT rowid FROM global_foods")
            .use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) } }

    /** Entradas del índice que casan con [term] pero cuya fila de contenido ya no existe. */
    private fun orphanDocids(term: String): List<Long> = matchDocids(term).filter { it !in contentRowIds() }

    /** Un DAO que delega en [real] pero lanza en la [failAt]-ésima llamada a `insertGlobalFoods`. */
    private fun failingOnInsert(real: NutritionDao, failAt: Int): NutritionDao {
        val inserts = AtomicInteger()
        return Proxy.newProxyInstance(NutritionDao::class.java.classLoader, arrayOf(NutritionDao::class.java)) { _, method, args ->
            if (method.name == "insertGlobalFoods" && inserts.incrementAndGet() == failAt) {
                throw IllegalStateException("disk full (simulated)")
            }
            try {
                method.invoke(real, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        } as NutritionDao
    }

    private fun currentMeta() =
        FoodImporter.ImportMetadata(FoodImporter.DATA_VERSION, FoodImporter.datasetFingerprint(), "2026-10-03T00:00:00Z")

    private val usedOnce = "2026-10-01T10:00:00Z"
    private val usedLater = "2026-10-02T18:30:00Z"

    // ─── Uso conservado ───────────────────────────────────────────────────

    @Test
    fun `usage counters survive the replacement while the data becomes the new dataset's`() = runBlocking {
        val dao = dao()
        dao.insertGlobalFoods(
            listOf(
                food("off_1", "Yogur natural", usage = 5, lastUsedAt = usedOnce),
                food("off_2", "Leche entera"),
            ),
        )

        FoodImporter.commitRows(db, dao, listOf(food("off_1", "Yogur natural light"), food("off_2", "Leche entera"), food("off_3", "Pan integral")))

        val kept = requireNotNull(dao.getGlobalFoodById("off_1"))
        assertEquals("the row is the new dataset's", "Yogur natural light", kept.name)
        assertEquals(5, kept.usageCount)
        assertEquals(usedOnce, kept.lastUsedAt)
        val untouched = requireNotNull(dao.getGlobalFoodById("off_2"))
        assertEquals(0, untouched.usageCount)
        assertNull(untouched.lastUsedAt)
        assertEquals(0, requireNotNull(dao.getGlobalFoodById("off_3")).usageCount)
        assertEquals(3, dao.getGlobalFoodCount())
    }

    @Test
    fun `a row absent from the new dataset is gone together with its counters`() = runBlocking {
        val dao = dao()
        dao.insertGlobalFoods(listOf(food("off_1", "Yogur natural", usage = 5, lastUsedAt = usedOnce), food("off_9", "Fantasma", usage = 3, lastUsedAt = usedOnce)))

        FoodImporter.commitRows(db, dao, listOf(food("off_1", "Yogur natural")))

        assertNull(dao.getGlobalFoodById("off_9"))
        assertEquals(1, dao.getGlobalFoodCount())
        assertEquals(emptyList<Long>(), matchDocids("fantasma"))
        assertEquals(5, requireNotNull(dao.getGlobalFoodById("off_1")).usageCount)
    }

    @Test
    fun `an empty parse never wipes the catalog`() = runBlocking {
        val dao = dao()
        dao.insertGlobalFoods(listOf(food("off_1", "Yogur natural", usage = 5, lastUsedAt = usedOnce)))

        val error = runCatching { FoodImporter.commitRows(db, dao, emptyList()) }.exceptionOrNull()

        assertTrue("an empty dataset is refused: $error", error is IllegalStateException)
        assertEquals(1, dao.getGlobalFoodCount())
        assertEquals(5, requireNotNull(dao.getGlobalFoodById("off_1")).usageCount)
    }

    // ─── Índice FTS ───────────────────────────────────────────────────────

    @Test
    fun `the fts index has each docid once and no orphans after the commit even when REPLACE left some behind`() = runBlocking {
        val dao = dao()
        // Android's SQLite runs with recursive triggers off, so REPLACE deletes the old row WITHOUT firing the delete
        // trigger and its fts entry stays behind (Robolectric's SQLite may turn them on: pin the device behaviour).
        db.openHelper.writableDatabase.execSQL("PRAGMA recursive_triggers = OFF")
        dao.insertGlobalFoods(listOf(food("off_1", "Leche entera"), food("off_2", "Yogur natural")))
        dao.insertGlobalFoods(listOf(food("off_1", "Leche entera colun")))
        db.openHelper.writableDatabase.execSQL("INSERT INTO global_foods_fts(docid, name, brand) VALUES (424242, 'fantasma', 'huerfano')")
        assertTrue("premise: REPLACE leaves an orphan entry", orphanDocids("leche").isNotEmpty())
        assertEquals("premise: a planted orphan is found as one", listOf(424242L), orphanDocids("fantasma"))

        // The same foodId twice INSIDE the new dataset goes through REPLACE as well.
        FoodImporter.commitRows(db, dao, listOf(food("off_1", "Leche entera"), food("off_1", "Leche descremada"), food("off_3", "Pan integral")))

        assertEquals(2, dao.getGlobalFoodCount())
        listOf("leche", "descremada", "pan", "integral").forEach { term ->
            val docids = matchDocids(term)
            assertTrue("'$term' must be indexed", docids.isNotEmpty())
            assertEquals("'$term' returns each docid once: $docids", docids.distinct(), docids)
            assertEquals("'$term' has no orphan entry", emptyList<Long>(), orphanDocids(term))
        }
        assertEquals("the replaced first copy is not indexed any more", emptyList<Long>(), matchDocids("entera"))
        assertEquals("a row that left the dataset is not indexed any more", emptyList<Long>(), matchDocids("yogur"))
        assertEquals("a planted orphan is gone", emptyList<Long>(), matchDocids("fantasma"))
        assertEquals(listOf("off_1"), dao.searchGlobalFoodsWithFts("descremada").map { it.foodId })
    }

    @Test
    fun `the index of a clean database stays complete after the commit`() = runBlocking {
        val dao = dao()
        dao.insertGlobalFoods(listOf(food("off_1", "Yogur natural")))

        FoodImporter.commitRows(db, dao, listOf(food("off_1", "Yogur natural"), food("off_2", "Leche entera")))

        assertEquals(contentRowIds(), (matchDocids("yogur") + matchDocids("leche")).toSet())
        assertEquals(listOf("off_2"), dao.searchGlobalFoodsWithFts("leche").map { it.foodId })
    }

    // ─── Fallos: el catálogo anterior sigue entero ────────────────────────

    @Test
    fun `a failing commit rolls back and keeps the previous rows, their usage and their index entries`() = runBlocking {
        val dao = dao()
        dao.insertGlobalFoods(listOf(food("off_1", "Yogur natural", usage = 5, lastUsedAt = usedOnce), food("off_2", "Leche entera")))
        val newRows = List(2_100) { food("new_$it", "Producto nuevo $it") }

        // 2.100 rows are two insert batches: the first one is already in the table when the second one fails.
        val error = runCatching { FoodImporter.commitRows(db, failingOnInsert(dao, failAt = 2), newRows) }.exceptionOrNull()

        assertTrue("the commit must fail: $error", error is IllegalStateException)
        assertEquals(2, dao.getGlobalFoodCount())
        val kept = requireNotNull(dao.getGlobalFoodById("off_1"))
        assertEquals("Yogur natural", kept.name)
        assertEquals(5, kept.usageCount)
        assertEquals(usedOnce, kept.lastUsedAt)
        assertNotNull(dao.getGlobalFoodById("off_2"))
        assertNull("the batch inserted before the failure was rolled back", dao.getGlobalFoodById("new_0"))
        assertEquals(1, matchDocids("yogur").size)
        assertEquals(emptyList<Long>(), matchDocids("producto"))
    }

    @Test
    fun `a failing parse never touches the database and the import reports failure`() = runBlocking {
        val dao = dao()
        dao.insertGlobalFoods(listOf(food("off_1", "Yogur natural", usage = 5, lastUsedAt = usedOnce)))
        var meta: FoodImporter.ImportMetadata? = null

        val imported = FoodImporter.runImport(db, alreadyImported = true, existingMeta = null, onMetaUpdated = { meta = it }, dao = dao) {
            error("the CSV could not be read")
        }

        assertFalse(imported)
        assertNull("the meta is only saved after a commit", meta)
        assertNull("the indicator is reset", FoodImporter.importProgress.value)
        assertEquals(5, requireNotNull(dao.getGlobalFoodById("off_1")).usageCount)
        assertEquals(1, dao.getGlobalFoodCount())
    }

    @Test
    fun `a failing commit inside the import reports failure and keeps the catalog`() = runBlocking {
        val dao = dao()
        dao.insertGlobalFoods(listOf(food("off_1", "Yogur natural", usage = 5, lastUsedAt = usedOnce)))
        var meta: FoodImporter.ImportMetadata? = null

        val imported = FoodImporter.runImport(db, alreadyImported = true, existingMeta = null, onMetaUpdated = { meta = it }, dao = failingOnInsert(dao, failAt = 1)) {
            listOf(food("off_1", "Yogur natural light"), food("off_2", "Leche entera"))
        }

        assertFalse(imported)
        assertNull(meta)
        assertNull(FoodImporter.importProgress.value)
        val kept = requireNotNull(dao.getGlobalFoodById("off_1"))
        assertEquals("Yogur natural", kept.name)
        assertEquals(5, kept.usageCount)
        assertEquals(1, dao.getGlobalFoodCount())
    }

    // ─── La importación completa: progreso, meta y base libre durante el análisis ──────

    @Test
    fun `a successful import replaces the rows keeps the usage and saves the meta`() = runBlocking {
        val dao = dao()
        dao.insertGlobalFoods(listOf(food("off_1", "Yogur natural", usage = 5, lastUsedAt = usedOnce)))
        var meta: FoodImporter.ImportMetadata? = null

        val imported = FoodImporter.runImport(db, alreadyImported = true, existingMeta = null, onMetaUpdated = { meta = it }, dao = dao) {
            listOf(food("off_1", "Yogur natural light"), food("off_2", "Leche entera"))
        }

        assertTrue(imported)
        assertEquals(FoodImporter.DATA_VERSION, meta?.version)
        assertEquals(FoodImporter.datasetFingerprint(), meta?.checksum)
        assertEquals("Yogur natural light", requireNotNull(dao.getGlobalFoodById("off_1")).name)
        assertEquals(5, requireNotNull(dao.getGlobalFoodById("off_1")).usageCount)
        assertEquals(usedOnce, requireNotNull(dao.getGlobalFoodById("off_1")).lastUsedAt)
        assertEquals(2, dao.getGlobalFoodCount())
        assertNull(FoodImporter.importProgress.value)
    }

    @Test
    fun `the progress goes up between 0 and 1 and ends with null`() = runBlocking {
        val seen = CopyOnWriteArrayList<Float?>()
        val collector = launch(Dispatchers.Unconfined) { FoodImporter.importProgress.collect { seen.add(it) } }

        val imported = FoodImporter.runImport(db, alreadyImported = false, existingMeta = null, onMetaUpdated = {}, dao = dao()) { onProgress ->
            // The parse reports its own 0..1 fraction; a report that goes back must not move the indicator back.
            listOf(0f, 0.5f, 1f, 0.25f).forEach { fraction ->
                onProgress(fraction)
                delay(20)
            }
            listOf(food("off_1", "Yogur natural"))
        }
        collector.cancel()

        assertTrue(imported)
        assertNull("it starts idle", seen.first())
        assertNull("and ends idle", seen.last())
        val values = seen.filterNotNull()
        assertTrue("every value is a fraction: $values", values.all { it in 0.01f..1f })
        assertEquals("it never goes back: $values", values.sorted(), values)
        assertEquals("it starts at 1 percent", 0.01f, values.first(), 1e-6f)
        // The parse fraction 0..1 occupies the band 5..95 percent of the indicator.
        assertTrue("fraction 0 is 5 percent: $values", values.any { abs(it - 0.05f) < 1e-3f })
        assertTrue("fraction 0.5 is 50 percent: $values", values.any { abs(it - 0.5f) < 1e-3f })
        assertEquals("the parse ends at 95 percent, right before the close: $values", 0.95f, values[values.size - 2], 1e-3f)
        assertEquals("it closes at 100 percent", 1f, values.last(), 0f)
    }

    @Test
    fun `the base stays writable while the catalog is parsed and a use logged meanwhile survives the commit`() = runBlocking {
        val dao = dao()
        dao.insertGlobalFoods(listOf(food("off_1", "Yogur natural", usage = 5, lastUsedAt = usedOnce)))
        val parseEntered = CompletableDeferred<Unit>()
        val finishParse = CompletableDeferred<Unit>()
        val import = async(Dispatchers.IO) {
            FoodImporter.runImport(db, alreadyImported = true, existingMeta = null, onMetaUpdated = {}, dao = dao) {
                parseEntered.complete(Unit)
                finishParse.await()
                listOf(food("off_1", "Yogur natural light"), food("off_2", "Leche entera"))
            }
        }
        withTimeout(30_000) { parseEntered.await() }

        // The parse is "running": nothing holds a transaction, so this write completes right away. With the old single
        // long transaction it would queue behind the whole import (a stuck dispatcher cannot be cancelled, hence the
        // separate job and the parse released in `finally`: a regression fails instead of hanging).
        val write = async(Dispatchers.IO) { dao.incrementGlobalFoodUsage("off_1", usedLater) }
        val wroteDuringParse = try {
            withTimeoutOrNull(30_000) { write.await() } != null
        } finally {
            finishParse.complete(Unit)
        }
        assertTrue("a write must complete while the catalog is being parsed", wroteDuringParse)
        assertTrue(withTimeout(60_000) { import.await() })

        val kept = requireNotNull(dao.getGlobalFoodById("off_1"))
        assertEquals("Yogur natural light", kept.name)
        assertEquals("the use logged during the parse is kept", 6, kept.usageCount)
        assertEquals(usedLater, kept.lastUsedAt)
    }

    @Test
    fun `an up to date catalog is neither parsed nor touched`() = runBlocking {
        val dao = dao()
        dao.insertGlobalFoods(listOf(food("off_1", "Yogur natural", usage = 5, lastUsedAt = usedOnce)))
        val parsed = AtomicInteger()

        val imported = FoodImporter.runImport(db, alreadyImported = true, existingMeta = currentMeta(), onMetaUpdated = {}, dao = dao) {
            parsed.incrementAndGet()
            emptyList()
        }

        assertFalse(imported)
        assertEquals(0, parsed.get())
        assertNull(FoodImporter.importProgress.value)
        assertEquals(5, requireNotNull(dao.getGlobalFoodById("off_1")).usageCount)
    }
}
