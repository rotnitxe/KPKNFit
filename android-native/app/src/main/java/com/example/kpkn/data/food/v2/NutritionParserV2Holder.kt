package com.example.kpkn.data.food.v2

import android.content.Context
import android.util.Log
import com.example.kpkn.domain.nutrition.v2.ConceptCatalogLoaderV2
import com.example.kpkn.domain.nutrition.v2.NutritionParserV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Carga una sola vez el vocabulario v2 (`food_data/concepts_app.json.gz`) y comparte el [NutritionParserV2].
 * Si la carga falla, lo registra y devuelve null en las llamadas siguientes sin reintentar.
 */
object NutritionParserV2Holder {
    private const val TAG = "NutritionParserV2Holder"
    private const val ASSET = "food_data/concepts_app.json.gz"

    /** El empaquetado de Android puede entregar el asset ya descomprimido y sin la extensión `.gz`. */
    private const val ASSET_UNPACKED = "food_data/concepts_app.json"

    private val mutex = Mutex()
    // Volátiles: la vía rápida los lee fuera del candado.
    @Volatile private var parser: NutritionParserV2? = null
    @Volatile private var failed = false

    /** Parser v2 compartido, o null si el vocabulario no se pudo cargar. */
    suspend fun parser(context: Context): NutritionParserV2? {
        parser?.let { return it }
        if (failed) return null
        return mutex.withLock {
            parser?.let { return@withLock it }
            if (failed) return@withLock null
            val appContext = context.applicationContext
            try {
                val loaded = withContext(Dispatchers.IO) {
                    openVocabulary(appContext).use { input ->
                        val buffered = input.buffered()
                        NutritionParserV2(ConceptCatalogLoaderV2.read(buffered, gzip = isGzip(buffered)))
                    }
                }
                parser = loaded
                loaded
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo cargar el vocabulario v2 ($ASSET)", error)
                failed = true
                null
            }
        }
    }

    private fun openVocabulary(context: Context): InputStream =
        try {
            context.assets.open(ASSET)
        } catch (_: FileNotFoundException) {
            context.assets.open(ASSET_UNPACKED)
        }

    /** Distingue gzip por su cabecera (1f 8b) sin consumir el flujo. */
    private fun isGzip(input: BufferedInputStream): Boolean {
        input.mark(2)
        val first = input.read()
        val second = input.read()
        input.reset()
        return first == 0x1f && second == 0x8b
    }
}
