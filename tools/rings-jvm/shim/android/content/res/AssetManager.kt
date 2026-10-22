package android.content.res

import java.io.InputStream
import r4.jvm.unavailable

class AssetManager {
    init { unavailable("AssetManager constructor") }
    fun open(path: String): InputStream = unavailable("AssetManager.open")
    fun list(path: String): Array<String>? = unavailable("AssetManager.list")
}
