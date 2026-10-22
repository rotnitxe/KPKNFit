package android.content

import android.content.res.AssetManager
import r4.jvm.unavailable

/** No Android behavior. Every entry records an error and throws. */
abstract class Context {
    init { unavailable("Context constructor") }
    val assets: AssetManager get() = unavailable("Context.assets")
    val applicationContext: Context get() = unavailable("Context.applicationContext")
}
