package com.example.kpkn.data.food.catalog

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import com.example.kpkn.domain.nutrition.nutrients.NutrientId
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/** Paquete de muestra (`test/resources/nutrition_catalog/`, generado con `make_fixture_pack.py`) y utilidades compartidas por los tests de F4-B. */
internal object CatalogFixture {
    private fun bytes(name: String): ByteArray =
        checkNotNull(CatalogFixture::class.java.classLoader!!.getResourceAsStream("nutrition_catalog/$name")) { "falta $name" }.use { it.readBytes() }

    val sqlite: ByteArray by lazy { bytes("fixture.sqlite") }
    val gzip: ByteArray by lazy { bytes("fixture.sqlite.gz") }
    val manifestJson: String by lazy { bytes("fixture.json").toString(Charsets.UTF_8) }
    val generation: String by lazy { JSONObject(manifestJson).getString("generation") }

    fun tempDir(): File = Files.createTempDirectory("nutrition-catalog-test").toFile()

    /** Escribe una copia del `.sqlite` de la muestra en [dir] y la devuelve. */
    fun materialize(dir: File, name: String = "fixture.sqlite"): File = File(dir, name).also { it.writeBytes(sqlite) }

    fun sha256(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    fun gzipOf(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    /** Variante del paquete con otra `metadata.generation`: (sqlite, gz, manifiesto). */
    fun variant(workDir: File, generation: String): Triple<ByteArray, ByteArray, String> {
        val file = materialize(workDir, "variant-$generation.sqlite")
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        db.execSQL("UPDATE metadata SET value = ? WHERE key = 'generation'", arrayOf(generation))
        db.close()
        val raw = file.readBytes()
        file.delete()
        return Triple(raw, gzipOf(raw), manifest(generation, sha256(raw), raw.size.toLong()))
    }

    fun manifest(generation: String, sha: String, bytes: Long) =
        """{"generation":"$generation","sha256":"$sha","bytes":$bytes,"formatVersion":1,"calculableIdentities":12}"""

    /** Assets falsos que cuentan cuántas veces se abrió cada archivo. */
    class FakeAssets(files: Map<String, ByteArray>) : CatalogAssetSource {
        private val files = files.toMutableMap()
        val opens = mutableMapOf<String, Int>()

        fun put(name: String, data: ByteArray) { files[name] = data }

        override fun open(name: String): InputStream? {
            val data = files[name] ?: return null
            opens[name] = (opens[name] ?: 0) + 1
            return ByteArrayInputStream(data)
        }
    }

    fun fixtureAssets() = FakeAssets(
        mapOf(
            NutritionCatalogPublisher.MANIFEST_ASSET to manifestJson.toByteArray(Charsets.UTF_8),
            NutritionCatalogPublisher.PACK_ASSET to gzip,
        ),
    )
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class SqliteNutritionCatalogReaderTest {
    private val dirs = mutableListOf<File>()
    private val readers = mutableListOf<SqliteNutritionCatalogReader>()

    private fun reader(): SqliteNutritionCatalogReader {
        val dir = CatalogFixture.tempDir().also { dirs += it }
        return SqliteNutritionCatalogReader.open(CatalogFixture.materialize(dir)).also { readers += it }
    }

    @After
    fun tearDown() {
        readers.forEach { runCatching { it.close() } }
        dirs.forEach { it.deleteRecursively() }
    }

    @Test fun `opens the sample and exposes its generation`() {
        assertEquals(CatalogFixture.generation, reader().generation)
    }

    @Test fun `record maps every column`() {
        val milk = reader().record("usda_171265")!!
        assertEquals("USDA_SR_LEGACY", milk.source)
        assertEquals("171265", milk.sourceRecordId)
        assertTrue(milk.name.startsWith("Milk, whole"))
        assertEquals("g", milk.basisUnit)
        assertEquals(100.0, milk.basisAmount, 0.0)
        assertEquals(1.0313, milk.densityGPerMl!!, 1e-9)
        assertNull(reader().record("usda_0"))
    }

    @Test fun `ml products have no density and a gtin`() {
        val tabasco = reader().record("off_0011210115644")!!
        assertEquals("ml", tabasco.basisUnit)
        assertNull(tabasco.densityGPerMl)
    }

    @Test fun `recordFor resolves app sources to catalog sources`() {
        val r = reader()
        assertEquals("usda_171265", r.recordFor("USDA", "171265")?.id)
        assertEquals("usda_171265", r.recordFor("usda sr legacy", "171265")?.id)
        assertEquals("usda_2706489", r.recordFor("USDA", "2706489")?.id)
        assertEquals("off_7802080001129", r.recordFor("OFF Chile", "7802080001129")?.id)
        assertEquals("ciqual_12060", r.recordFor("CIQUAL", "12060")?.id)
        assertNull(r.recordFor("USDA", "999999999"))
        assertNull("OFF no debe devolver una ficha USDA", r.recordFor("OFF Chile", "171265"))
        assertNull(r.recordFor("OTRA", "171265"))
    }

    @Test fun `byGtin goes through product to record`() {
        val r = reader()
        assertEquals("off_7802080001129", r.byGtin("7802080001129")?.id)
        assertNull(r.byGtin("0000000000000"))
    }

    @Test fun `nutrients distinguish absent from declared zero`() {
        val n = reader().nutrientsPer100("usda_171265")
        assertEquals(35, n.size)
        assertEquals(113.0, n[NutrientId.CALCIUM]!!, 1e-9)
        assertTrue(n.containsKey(NutrientId.EPA_DHA))
        assertEquals(0.0, n[NutrientId.EPA_DHA]!!, 0.0)
        assertEquals(0.0, n[NutrientId.VITAMIN_C]!!, 0.0)
        assertFalse("sin fila = sin dato", n.containsKey(NutrientId.ADDED_SUGARS))
        assertTrue(reader().nutrientsPer100("usda_0").isEmpty())
    }

    @Test fun `portions come ordered by seq with nullable ml`() {
        val p = reader().portions("usda_171265")
        assertEquals(listOf(1, 2, 3, 4), p.map { it.seq })
        assertEquals("1 cup", p[0].label)
        assertNull(p[0].labelEs)
        assertEquals(244.0, p[0].grams, 1e-9)
        assertEquals(236.588, p[0].ml!!, 1e-9)
        assertNull(p[3].ml)
        assertTrue(reader().portions("usda_0").isEmpty())
    }

    @Test fun `search matches by prefix and by two tokens`() {
        val r = reader()
        assertEquals(setOf("usda_1999630", "usda_1999631"), r.search("leche", 10).map { it.id }.toSet())
        assertEquals(listOf("usda_1999630"), r.search("leche soya", 10).map { it.id })
        assertEquals(listOf("usda_1999630"), r.search("lec soy", 10).map { it.id })
        assertEquals(listOf("off_0011210115644"), r.search("tabas", 10).map { it.id })
        assertEquals(1, r.search("leche", 1).size)
        assertTrue(r.search("zzzzzz", 10).isEmpty())
    }

    @Test fun `empty or symbol only query returns nothing`() {
        val r = reader()
        assertTrue(r.search("", 10).isEmpty())
        assertTrue(r.search("   ", 10).isEmpty())
        assertTrue(r.search("!!! ()", 10).isEmpty())
        assertTrue(r.search("leche", 0).isEmpty())
    }

    @Test fun `hostile text never breaks the queries`() {
        val r = reader()
        assertNull(r.record("x' OR '1'='1"))
        assertNull(r.recordFor("USDA", "1' OR '1'='1"))
        assertTrue(r.search("leche\" OR *", 10).size <= 2)
    }

    @Test fun `open fails without generation or without file`() {
        val dir = CatalogFixture.tempDir().also { dirs += it }
        val empty = File(dir, "empty.sqlite")
        val db = SQLiteDatabase.openOrCreateDatabase(empty, null)
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.close()
        assertTrue(runCatching { SqliteNutritionCatalogReader.open(empty) }.isFailure)
        assertTrue(runCatching { SqliteNutritionCatalogReader.open(File(dir, "missing.sqlite")) }.isFailure)
    }

    @Test fun `close releases the database`() {
        val r = reader()
        r.close()
        assertTrue(runCatching { r.record("usda_171265") }.isFailure)
        assertNotNull(CatalogFixture.generation)
    }
}
