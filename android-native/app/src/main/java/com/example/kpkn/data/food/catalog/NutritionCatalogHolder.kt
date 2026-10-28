package com.example.kpkn.data.food.catalog

import com.example.kpkn.domain.nutrition.nutrients.NutritionCatalogPort
import java.io.Closeable

/**
 * Puerto del paquete de catálogo verificado y publicado, compartido por la app.
 * `current == null` significa que no hay paquete verificado y la app funciona como antes (sin instantánea de nutrientes).
 */
object NutritionCatalogHolder {
    @Volatile
    var current: NutritionCatalogPort? = null
        private set

    /** Reemplaza el puerto vigente (null = ninguno) y cierra el anterior si es [Closeable] y distinto. */
    @Synchronized
    fun install(port: NutritionCatalogPort?) {
        val previous = current
        current = port
        if (previous != null && previous !== port) {
            runCatching { (previous as? Closeable)?.close() }
        }
    }
}
