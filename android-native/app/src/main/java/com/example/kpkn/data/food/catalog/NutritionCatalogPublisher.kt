package com.example.kpkn.data.food.catalog

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.kpkn.data.db.NutritionCatalogDao
import com.example.kpkn.data.db.NutritionCatalogPublicationEntity
import com.example.kpkn.domain.nutrition.nutrients.NutritionCatalogPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Origen de los archivos empaquetados (assets); null si el archivo no existe. */
fun interface CatalogAssetSource {
    fun open(name: String): InputStream?
}

@Serializable
private data class CatalogManifest(
    val generation: String,
    val sha256: String,
    val bytes: Long,
    val formatVersion: Int,
    val calculableIdentities: Int,
)

/**
 * Publica el paquete de catálogo desde assets a `filesDir/nutrition_catalog/<generation>.sqlite` con verificación
 * (sha256, tamaño, `metadata.generation`) y mueve el puntero de Room. No lanza (salvo cancelación):
 * ante cualquier fallo devuelve null y la app sigue como si no hubiera paquete.
 */
class NutritionCatalogPublisher(
    private val filesDir: File,
    private val assets: CatalogAssetSource,
    private val dao: NutritionCatalogDao,
) {
    private val folder get() = File(filesDir, FOLDER)

    suspend fun publish(): NutritionCatalogPort? = withContext(Dispatchers.IO) {
        var tmp: File? = null
        try {
            val manifest = readManifest() ?: return@withContext null
            val finalFile = File(folder, "${manifest.generation}.sqlite")
            val active = dao.active()
            if (active?.generation == manifest.generation && finalFile.isFile && finalFile.length() == manifest.bytes) {
                val reader = runCatching { SqliteNutritionCatalogReader.open(finalFile) }.getOrNull()
                if (reader != null) {
                    if (reader.generation == manifest.generation) {
                        cleanFolder(finalFile)
                        return@withContext reader
                    }
                    reader.close()
                }
            }
            check(folder.isDirectory || folder.mkdirs()) { "No se pudo crear ${folder.path}" }
            tmp = File(folder, "${manifest.generation}.sqlite.tmp")
            if (tmp.exists()) tmp.delete()
            val sha = decompress(tmp, manifest)
            check(sha.equals(manifest.sha256, ignoreCase = true)) { "sha256 del paquete no coincide con el manifiesto" }
            check(tmp.length() == manifest.bytes) { "Tamaño del paquete no coincide con el manifiesto" }
            verify(tmp, manifest.generation)
            if (finalFile.exists()) check(finalFile.delete()) { "No se pudo reemplazar ${finalFile.name}" }
            check(tmp.renameTo(finalFile)) { "No se pudo renombrar el paquete verificado" }
            tmp = null
            val previous = active?.generation?.takeIf { it != manifest.generation } ?: active?.previousGeneration
            dao.publish(
                NutritionCatalogPublicationEntity(
                    rowId = 1,
                    generation = manifest.generation,
                    sha256 = sha,
                    bytes = manifest.bytes,
                    formatVersion = manifest.formatVersion,
                    calculableIdentities = manifest.calculableIdentities,
                    previousGeneration = previous,
                ),
            )
            cleanFolder(finalFile)
            SqliteNutritionCatalogReader.open(finalFile)
        } catch (cancelled: CancellationException) {
            tmp?.delete()
            throw cancelled
        } catch (error: Throwable) {
            tmp?.delete()
            Log.w(TAG, "No se pudo publicar el paquete de catálogo; la app sigue sin él", error)
            null
        }
    }

    private fun readManifest(): CatalogManifest? {
        val stream = assets.open(MANIFEST_ASSET) ?: return null
        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        return json.decodeFromString<CatalogManifest>(text).also {
            require(it.generation.isNotBlank() && it.generation.all { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '_' }) {
                "Generación inválida en el manifiesto"
            }
            require(it.bytes > 0) { "Tamaño inválido en el manifiesto" }
        }
    }

    /**
     * Copia el paquete a [target] (descomprimiéndolo si es gzip) y devuelve el sha256 (hex minúsculas) de lo escrito; corta
     * si excede el tamaño declarado. El empaquetado de Android descomprime los `.gz` de assets y les quita la extensión, así
     * que en el APK el paquete llega como [PACK_ASSET_PLAIN]; gzip se reconoce por la cabecera (1f 8b), no por el nombre.
     */
    private fun decompress(target: File, manifest: CatalogManifest): String {
        val raw = (assets.open(PACK_ASSET) ?: assets.open(PACK_ASSET_PLAIN) ?: error("Falta el asset $PACK_ASSET")).buffered()
        val source = if (isGzip(raw)) GZIPInputStream(raw) else raw
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        source.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    check(total <= manifest.bytes) { "El paquete descomprimido excede el tamaño del manifiesto" }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isGzip(input: BufferedInputStream): Boolean {
        input.mark(2)
        val gzip = input.read() == 0x1f && input.read() == 0x8b
        input.reset()
        return gzip
    }

    /**
     * Sin `PRAGMA quick_check`: desde SQLite 3.44 también revisa las tablas FTS4, y en Android esa revisión intenta escribir
     * y falla con la base abierta de solo lectura («attempt to write a readonly database»). El sha256 del archivo completo ya
     * garantiza que es idéntico al construido, y `build_pack.py` corre `integrity_check` al construirlo.
     */
    private fun verify(file: File, generation: String) {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val declared = db.rawQuery("SELECT value FROM metadata WHERE key = 'generation'", null)
                .use { c -> if (c.moveToFirst()) c.getString(0) else null }
            check(declared == generation) { "metadata.generation ($declared) no coincide con el manifiesto" }
        } finally {
            db.close()
        }
    }

    private fun cleanFolder(keep: File) {
        folder.listFiles()?.forEach { if (it.name != keep.name) it.deleteRecursively() }
    }

    companion object {
        private const val TAG = "NutritionCatalog"
        private const val FOLDER = "nutrition_catalog"
        const val MANIFEST_ASSET = "food_data/nutrition_catalog.json"
        const val PACK_ASSET = "food_data/nutrition_catalog.sqlite.gz"
        const val PACK_ASSET_PLAIN = "food_data/nutrition_catalog.sqlite"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
