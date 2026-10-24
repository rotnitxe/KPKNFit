package android.content.res

import java.io.FileNotFoundException
import java.io.InputStream

/*
 * STUB DEL ARNÉS JVM: android.content.res.AssetManager. En el arnés no hay assets; `open` lanza como un asset ausente.
 */
class AssetManager {
    fun open(fileName: String): InputStream =
        throw FileNotFoundException("Asset no disponible en el arnés JVM: $fileName")
}
