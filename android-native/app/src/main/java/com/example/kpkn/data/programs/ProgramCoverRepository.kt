package com.example.kpkn.data.programs

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.kpkn.data.models.Program
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/*
 * Portadas propias por programa: `assets/program_covers/cover_<id_saneado>.webp` (1600 × 2000, ≤ 200 KB). Sin archivo, la
 * app pinta su degradado de siempre. El alta guarda `coverImage = "kpkn-cover://<entryId>"` en el programa activado.
 */

/** Carpeta de las portadas dentro de `assets/`. */
const val PROGRAM_COVERS_DIR = "program_covers"

/** Esquema del `coverImage` que apunta a la portada de una entrada del catálogo. */
const val PROGRAM_COVER_SCHEME = "kpkn-cover://"

/** Reducción máxima al decodificar: más allá, la imagen ya no tiene sentido en pantalla. */
private const val MAX_SAMPLE_SIZE = 64

/** Lo que el repositorio necesita de los assets: la app usa [AssetManager]; las pruebas, un fake. */
interface CoverAssetSource {
    /** Nombres de los archivos de [dir] (vacío si la carpeta no existe). */
    fun listFiles(dir: String): Set<String>

    /** Flujo del asset [path]. */
    fun open(path: String): InputStream
}

/** Los assets de la app. */
class AssetManagerCoverSource(private val assets: AssetManager) : CoverAssetSource {
    override fun listFiles(dir: String): Set<String> = assets.list(dir)?.toSet().orEmpty()

    override fun open(path: String): InputStream = assets.open(path)
}

/**
 * El id como parte del nombre de archivo: minúsculas, y todo lo que no sea letra o dígito ASCII pasa a `_`
 * (`native:strength-foundation-v2` → `native_strength_foundation_v2`).
 */
fun sanitizeCoverId(id: String): String = buildString(id.length) {
    for (ch in id.lowercase()) append(if (ch in 'a'..'z' || ch in '0'..'9') ch else '_')
}

/** El `coverImage` que apunta a la portada de la entrada [entryId]. */
fun programCoverValueOf(entryId: String): String = PROGRAM_COVER_SCHEME + entryId

/**
 * El id de la entrada de un `coverImage` `kpkn-cover://…`. Null si el valor es de otro esquema, vacío o nulo: entonces la
 * app pinta su degradado.
 */
fun programCoverEntryIdOf(coverImage: String?): String? =
    coverImage?.takeIf { it.startsWith(PROGRAM_COVER_SCHEME) }
        ?.removePrefix(PROGRAM_COVER_SCHEME)
        ?.takeIf { it.isNotBlank() }

/** El programa con la portada de la entrada [entryId], si aún no tiene una. Lo usa el alta al activar un plan. */
fun Program.withCatalogCover(entryId: String): Program =
    if (coverImage != null) this else copy(coverImage = programCoverValueOf(entryId))

/** Reducción (potencia de 2) que deja la imagen de [sourceWidth] al menos tan ancha como [requestedWidth]. */
internal fun sampleSizeFor(sourceWidth: Int, requestedWidth: Int): Int {
    if (sourceWidth <= 0 || requestedWidth <= 0) return 1
    var sample = 1
    while (sample < MAX_SAMPLE_SIZE && sourceWidth / (sample * 2) >= requestedWidth) sample *= 2
    return sample
}

/**
 * Portadas de los programas: dice si hay archivo para una entrada y guarda en memoria las últimas [capacity] decodificadas
 * (LRU). La decodificación va en `Dispatchers.IO` y reduce la imagen hasta el ancho pedido.
 */
class ProgramCoverRepository internal constructor(
    private val source: CoverAssetSource,
    private val capacity: Int = CACHE_CAPACITY,
) {
    /** Archivos de portada, listados una sola vez. */
    private val files: Set<String> by lazy { source.listFiles(PROGRAM_COVERS_DIR) }

    /** Bitmaps decodificados por entrada y ancho; sale primero el que menos se ha usado. */
    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > capacity
    }

    /** Ruta del asset de portada de [programIdOrEntryId] (`program_covers/cover_<id>.webp`), o null si no hay archivo. */
    fun coverPathFor(programIdOrEntryId: String): String? {
        val file = "cover_${sanitizeCoverId(programIdOrEntryId)}.webp"
        return if (file in files) "$PROGRAM_COVERS_DIR/$file" else null
    }

    /** La portada de [entryId] a [widthPx] si ya está en memoria. No toca el disco. */
    fun peek(entryId: String, widthPx: Int): Bitmap? = synchronized(cache) { cache[keyOf(entryId, widthPx)] }

    /** La portada de [entryId] a [widthPx], o null si la entrada no tiene archivo. Decodifica en `Dispatchers.IO`. */
    suspend fun load(entryId: String, widthPx: Int): Bitmap? {
        peek(entryId, widthPx)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            coverPathFor(entryId)?.let { decode(it, widthPx) }
        } ?: return null
        synchronized(cache) { cache[keyOf(entryId, widthPx)] = bitmap }
        return bitmap
    }

    private fun decode(path: String, widthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, widthPx)
            // Son fotos sobre negro: sin canal alfa, la mitad de memoria.
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return source.open(path).use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun keyOf(entryId: String, widthPx: Int): String = "$entryId@$widthPx"

    companion object {
        /** Imágenes que se guardan decodificadas a la vez. */
        const val CACHE_CAPACITY = 8

        @Volatile
        private var instance: ProgramCoverRepository? = null

        /** El repositorio de la app: una sola caché para todas las pantallas. */
        fun getInstance(context: Context): ProgramCoverRepository =
            instance ?: synchronized(this) {
                instance ?: ProgramCoverRepository(AssetManagerCoverSource(context.applicationContext.assets))
                    .also { instance = it }
            }
    }
}
