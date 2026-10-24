package com.example.kpkn.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.kpkn.data.nutrition.label.*
import com.example.kpkn.domain.nutrition.label.*
import kotlinx.coroutines.launch

/** Cinco imágenes en memoria pasan por ML Kit, OcrLayout y el parser real. Sin repositorios ni fotos en disco. */
class LabelOcrSelfTestActivity : ComponentActivity() {
    private data class Case(val name: String, val lines: List<List<String>>, val energy: Double, val protein: Double, val sodium: Double? = null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        val output = TextView(this).apply { textSize = 18f; setPadding(24, 48, 24, 24); text = "Autocomprobación OCR local…\n" }
        setContentView(output)
        val cases = listOf(
            Case("UE 100g", listOf(listOf("Por 100 g"), listOf("Energia", "200 kcal"), listOf("Proteinas", "10 g"), listOf("Carbohidratos", "20 g"), listOf("Grasas", "8 g")), 200.0, 10.0),
            Case("Dos columnas", listOf(listOf("", "Por 100 g", "Por racion"), listOf("Racion: 30 g"), listOf("Energia", "200 kcal", "60 kcal"), listOf("Proteinas", "10 g", "3 g"), listOf("Carbohidratos", "20 g", "6 g"), listOf("Grasas", "8 g", "2.4 g")), 200.0, 10.0),
            Case("Estados Unidos", listOf(listOf("Nutrition Facts"), listOf("Serving size 30 g"), listOf("Calories", "120"), listOf("Protein", "3 g"), listOf("Total Carbohydrate", "15 g"), listOf("Total Fat", "5 g")), 120.0, 3.0),
            Case("Sal", listOf(listOf("Por 100 g"), listOf("Energia", "200 kcal"), listOf("Proteinas", "10 g"), listOf("Carbohidratos", "20 g"), listOf("Grasas", "8 g"), listOf("Sal", "0.5 g")), 200.0, 10.0, 200.0),
            Case("Trazas", listOf(listOf("Por 100 g"), listOf("Energia", "200 kcal"), listOf("Proteinas", "10 g"), listOf("Carbohidratos", "20 g"), listOf("Grasas", "8 g"), listOf("Fibra", "trazas")), 200.0, 10.0),
        )
        lifecycleScope.launch {
            var passed = 0
            // Diagnóstico exclusivo de estas imágenes sintéticas: nunca intercepta fotos de la galería.
            val delegate = com.example.kpkn.data.importer.ocr.MlKitTextRecognizer()
            val reader = ReadNutritionLabel(contentResolver, object : com.example.kpkn.data.importer.ocr.TextRecognizerPort {
                override suspend fun recognize(bitmap: Bitmap): List<com.example.kpkn.domain.training.importer.OcrWord> {
                    val words = delegate.recognize(bitmap)
                    val rows = com.example.kpkn.domain.training.importer.OcrLayout.reconstruct(listOf(
                        com.example.kpkn.domain.training.importer.OcrPage(bitmap.width, bitmap.height, words)))
                    Log.i("MfpLabelSelfTest", "SYNTHETIC OCR ROWS: $rows")
                    return words
                }
            })
            cases.forEachIndexed { index, case ->
                val bitmap = Bitmap.createBitmap(1080, 760, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = (30 + index * 2).toFloat() }
                case.lines.forEachIndexed { row, cells -> cells.forEachIndexed { col, text ->
                    canvas.drawText(text, floatArrayOf(24f, 465f, 780f)[col], 65f + row * 84f, paint)
                } }
                val result = reader.readBitmapForDebug(bitmap)
                val label = (result as? LabelReadResult.Ready)?.label
                fun near(field: LabelField, expected: Double) = label?.fields?.get(field)?.value?.let { kotlin.math.abs(it - expected) < 0.01 } == true
                val ok = near(LabelField.ENERGY_KCAL, case.energy) && near(LabelField.PROTEIN_G, case.protein) &&
                    (case.sodium == null || near(LabelField.SODIUM_MG, case.sodium)) &&
                    (index != 4 || label?.fields?.get(LabelField.FIBER_G)?.confidence == LabelConfidence.UNSURE)
                if (ok) passed++
                val line = "${if (ok) "OK" else "FAIL"} ${case.name}: ${label?.fields ?: result}"
                Log.i("MfpLabelSelfTest", line); output.append(line + "\n\n")
            }
            val summary = "SELFTEST ${if (passed == cases.size) "OK" else "FAIL"} $passed/${cases.size}"
            Log.i("MfpLabelSelfTest", summary); output.append(summary)
        }
    }
}
