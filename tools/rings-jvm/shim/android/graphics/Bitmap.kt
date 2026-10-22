package android.graphics

import java.io.InputStream
import r4.jvm.unavailable

class Bitmap {
    init { unavailable("Bitmap constructor") }
    enum class Config { RGB_565 }
}

object BitmapFactory {
    class Options {
        init { unavailable("BitmapFactory.Options constructor") }
        var inJustDecodeBounds: Boolean = false
        var outWidth: Int = 0
        var inSampleSize: Int = 1
        var inPreferredConfig: Bitmap.Config? = null
    }
    fun decodeStream(stream: InputStream, padding: Any?, options: Options): Bitmap? =
        unavailable("BitmapFactory.decodeStream")
}
