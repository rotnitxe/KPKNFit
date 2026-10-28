package com.example.kpkn.data.food.catalog

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.testsupport.SyncRoomExecutor
import kotlinx.coroutines.runBlocking
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
import java.io.Closeable
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NutritionCatalogPublisherTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: KpknDatabase
    private lateinit var filesDir: File
    private val open = mutableListOf<Closeable>()

    @Before
    fun setUp() {
        SyncRoomExecutor.install()
        db = KpknDatabase.createInMemory(context)
        filesDir = CatalogFixture.tempDir()
    }

    @After
    fun tearDown() {
        open.forEach { runCatching { it.close() } }
        db.close()
        filesDir.deleteRecursively()
        SyncRoomExecutor.uninstall()
    }

    private fun publisher(assets: CatalogAssetSource) = NutritionCatalogPublisher(filesDir, assets, db.nutritionCatalogDao())

    private fun publish(assets: CatalogAssetSource) = runBlocking { publisher(assets).publish() }.also { (it as? Closeable)?.let(open::add) }

    private val folder get() = File(filesDir, "nutrition_catalog")
    private fun folderNames() = folder.list().orEmpty().sorted()

    @Test fun `without the asset it returns null and touches nothing`() {
        assertNull(publish(CatalogAssetSource { null }))
        assertFalse(folder.exists())
        assertNull(runBlocking { db.nutritionCatalogDao().active() })
    }

    @Test fun `publishes a verified pack and moves the pointer`() {
        val port = publish(CatalogFixture.fixtureAssets())
        assertNotNull(port)
        assertEquals(CatalogFixture.generation, port!!.generation)
        assertEquals("usda_171265", port.record("usda_171265")?.id)
        val active = runBlocking { db.nutritionCatalogDao().active() }!!
        assertEquals(CatalogFixture.generation, active.generation)
        assertEquals(CatalogFixture.sha256(CatalogFixture.sqlite), active.sha256)
        assertEquals(CatalogFixture.sqlite.size.toLong(), active.bytes)
        assertEquals(1, active.formatVersion)
        assertEquals(12, active.calculableIdentities)
        assertNull(active.previousGeneration)
        assertEquals(listOf("${CatalogFixture.generation}.sqlite"), folderNames())
        assertEquals(CatalogFixture.sqlite.size.toLong(), File(folder, "${CatalogFixture.generation}.sqlite").length())
    }

    @Test fun `second call with the same generation does not copy again`() {
        val assets = CatalogFixture.fixtureAssets()
        val first = publish(assets)
        assertNotNull(first)
        (first as Closeable).close()
        val modified = File(folder, "${CatalogFixture.generation}.sqlite").lastModified()
        val second = publish(assets)
        assertNotNull(second)
        assertEquals(CatalogFixture.generation, second!!.generation)
        assertEquals("el .gz solo se abre una vez", 1, assets.opens[NutritionCatalogPublisher.PACK_ASSET])
        assertEquals(modified, File(folder, "${CatalogFixture.generation}.sqlite").lastModified())
        assertEquals(listOf("${CatalogFixture.generation}.sqlite"), folderNames())
    }

    @Test fun `tampered sha in the manifest returns null and leaves the pointer intact`() {
        val assets = CatalogFixture.fixtureAssets()
        assets.put(
            NutritionCatalogPublisher.MANIFEST_ASSET,
            CatalogFixture.manifest(CatalogFixture.generation, "0".repeat(64), CatalogFixture.sqlite.size.toLong()).toByteArray(),
        )
        assertNull(publish(assets))
        assertTrue(folderNames().none { it.endsWith(".tmp") })
        assertFalse(File(folder, "${CatalogFixture.generation}.sqlite").exists())
        assertNull(runBlocking { db.nutritionCatalogDao().active() })
    }

    @Test fun `a failed update keeps the previous generation published`() {
        assertNotNull(publish(CatalogFixture.fixtureAssets()))
        open.forEach { it.close() }
        val (raw, gz, _) = CatalogFixture.variant(filesDir, "fixture-new-1")
        val bad = CatalogFixture.FakeAssets(
            mapOf(
                NutritionCatalogPublisher.MANIFEST_ASSET to CatalogFixture.manifest("fixture-new-1", "f".repeat(64), raw.size.toLong()).toByteArray(),
                NutritionCatalogPublisher.PACK_ASSET to gz,
            ),
        )
        assertNull(publish(bad))
        assertEquals(CatalogFixture.generation, runBlocking { db.nutritionCatalogDao().active() }!!.generation)
        assertEquals(listOf("${CatalogFixture.generation}.sqlite"), folderNames())
    }

    @Test fun `size mismatch or metadata generation mismatch is rejected`() {
        val sha = CatalogFixture.sha256(CatalogFixture.sqlite)
        val wrongSize = CatalogFixture.fixtureAssets().apply {
            put(NutritionCatalogPublisher.MANIFEST_ASSET, CatalogFixture.manifest(CatalogFixture.generation, sha, CatalogFixture.sqlite.size - 1L).toByteArray())
        }
        assertNull(publish(wrongSize))
        val wrongGeneration = CatalogFixture.fixtureAssets().apply {
            put(NutritionCatalogPublisher.MANIFEST_ASSET, CatalogFixture.manifest("fixture-other", sha, CatalogFixture.sqlite.size.toLong()).toByteArray())
        }
        assertNull(publish(wrongGeneration))
        assertTrue(folderNames().isEmpty())
        assertNull(runBlocking { db.nutritionCatalogDao().active() })
    }

    @Test fun `corrupt manifest or missing pack returns null`() {
        val badJson = CatalogFixture.FakeAssets(mapOf(NutritionCatalogPublisher.MANIFEST_ASSET to "{no json".toByteArray()))
        assertNull(publish(badJson))
        val noPack = CatalogFixture.FakeAssets(mapOf(NutritionCatalogPublisher.MANIFEST_ASSET to CatalogFixture.manifestJson.toByteArray()))
        assertNull(publish(noPack))
        assertTrue(folderNames().none { it.endsWith(".tmp") })
        assertNull(runBlocking { db.nutritionCatalogDao().active() })
    }

    @Test fun `new generation removes the old file and records the previous generation`() {
        val first = publish(CatalogFixture.fixtureAssets())
        assertNotNull(first)
        (first as Closeable).close()
        val (_, gz, manifest) = CatalogFixture.variant(filesDir, "fixture-new-2")
        val next = publish(
            CatalogFixture.FakeAssets(
                mapOf(
                    NutritionCatalogPublisher.MANIFEST_ASSET to manifest.toByteArray(),
                    NutritionCatalogPublisher.PACK_ASSET to gz,
                ),
            ),
        )
        assertNotNull(next)
        assertEquals("fixture-new-2", next!!.generation)
        val active = runBlocking { db.nutritionCatalogDao().active() }!!
        assertEquals("fixture-new-2", active.generation)
        assertEquals(CatalogFixture.generation, active.previousGeneration)
        assertEquals(listOf("fixture-new-2.sqlite"), folderNames())
    }

    @Test fun `leftover tmp files are cleaned on a normal publish`() {
        folder.mkdirs()
        File(folder, "old.sqlite.tmp").writeText("basura")
        assertNotNull(publish(CatalogFixture.fixtureAssets()))
        assertEquals(listOf("${CatalogFixture.generation}.sqlite"), folderNames())
    }
}
