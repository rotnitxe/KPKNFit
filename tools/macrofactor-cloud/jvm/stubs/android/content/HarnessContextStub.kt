package android.content

import android.content.res.AssetManager

/*
 * STUB DEL ARNÉS JVM (no es código de la app). Imita solo lo que el código puro usa de android.content.Context:
 * el tipo y la propiedad `assets`. Sin Android no hay assets reales: las lecturas fallan y su `runCatching` cae al respaldo.
 */
open class Context {
    open val assets: AssetManager
        get() = AssetManager()

    open val applicationContext: Context
        get() = this
}
