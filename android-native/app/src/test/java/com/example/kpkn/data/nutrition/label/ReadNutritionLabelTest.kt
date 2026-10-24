package com.example.kpkn.data.nutrition.label

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.importer.ocr.TextRecognizerPort
import com.example.kpkn.domain.nutrition.label.*
import com.example.kpkn.domain.training.importer.OcrWord
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class ReadNutritionLabelTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun image(width: Int = 640, height: Int = 480, orientation: Int? = null): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output); bitmap.recycle(); output.toByteArray() }
        val exif = if (orientation == null) byteArrayOf() else byteArrayOf(69,120,105,102,0,0,73,73,42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,orientation.toByte(),0,0,0,0,0,0,0)
        val encoded = if (exif.isEmpty()) bytes else bytes.take(2).toByteArray() + byteArrayOf(0xff.toByte(),0xe1.toByte(),0, (exif.size + 2).toByte()) + exif + bytes.drop(2).toByteArray()
        val file = folder.newFile("photo-${System.nanoTime()}.jpg").apply { writeBytes(encoded) }
        return Uri.fromFile(file)
    }
    private fun words(): List<OcrWord> = listOf(
        OcrWord("Por 100 g", 10,10,120,28),
        OcrWord("Energía", 10,60,110,80), OcrWord("200",400,62,440,82), OcrWord("kcal",450,62,490,82),
        OcrWord("Proteínas",10,110,150,130), OcrWord("10",400,112,430,132), OcrWord("g",440,112,460,132),
        OcrWord("Carbohidratos",10,160,180,180), OcrWord("20",400,162,430,182), OcrWord("g",440,162,460,182),
        OcrWord("Grasas",10,210,110,230), OcrWord("8",400,212,420,232), OcrWord("g",430,212,450,232))
    @Test fun reconoceConCajasDesalineadasYNoCreaArchivos() = runBlocking {
        val uri = image(); var bitmap: Bitmap? = null
        val recognizer = object : TextRecognizerPort { override suspend fun recognize(image: Bitmap): List<OcrWord> { bitmap = image; return words() } }
        val before = folder.root.walkTopDown().filter(File::isFile).map { it.name }.toSet()
        val cacheBefore = context.cacheDir.walkTopDown().filter(File::isFile).map { it.relativeTo(context.cacheDir).path }.toSet()
        val result = ReadNutritionLabel(context.contentResolver, recognizer).read(uri) as LabelReadResult.Ready
        assertEquals(200.0, result.label.fields[LabelField.ENERGY_KCAL]!!.value, 0.0)
        assertEquals(10.0, result.label.fields[LabelField.PROTEIN_G]!!.value, 0.0)
        assertTrue(bitmap!!.isRecycled)
        assertEquals(before, folder.root.walkTopDown().filter(File::isFile).map { it.name }.toSet())
        assertEquals(cacheBefore, context.cacheDir.walkTopDown().filter(File::isFile).map { it.relativeTo(context.cacheDir).path }.toSet())
    }
    @Test fun reduceElLadoLargoYAplicaLaOrientacionExif() = runBlocking {
        val uri = image(4000,1000,6); var width = 0; var height = 0
        val recognizer = object : TextRecognizerPort { override suspend fun recognize(bitmap: Bitmap): List<OcrWord> { width = bitmap.width; height = bitmap.height; return words() } }
        assertTrue(ReadNutritionLabel(context.contentResolver, recognizer).read(uri) is LabelReadResult.Ready)
        assertTrue(maxOf(width, height) <= 2048); assertTrue(height > width)
    }
    @Test fun imagenIlegibleYSinTextoSonResultadosTipados() = runBlocking {
        val empty = object : TextRecognizerPort { override suspend fun recognize(bitmap: Bitmap) = emptyList<OcrWord>() }
        val invalid = folder.newFile("invalid.jpg").apply { writeText("no es imagen") }
        assertTrue(ReadNutritionLabel(context.contentResolver, empty).read(Uri.fromFile(invalid)) is LabelReadResult.Failed)
        assertTrue(ReadNutritionLabel(context.contentResolver, empty).read(image()) is LabelReadResult.NoTable)
    }
    @Test fun cancelarNoReciclaElBitmapMientrasLaTareaNativaLoUsa() = runBlocking {
        val started = CompletableDeferred<Bitmap>(); val release = CompletableDeferred<Unit>()
        val recognizer = object : TextRecognizerPort { override suspend fun recognize(bitmap: Bitmap): List<OcrWord> { started.complete(bitmap); release.await(); return words() } }
        val reader = ReadNutritionLabel(context.contentResolver, recognizer)
        val uri = image()
        val job = launch { reader.read(uri) }
        val bitmap = withTimeout(10_000) { started.await() }
        try { job.cancel(); assertFalse(bitmap.isRecycled) } finally { release.complete(Unit); job.join() }
        assertTrue(bitmap.isRecycled)
    }
}
