package com.example.kpkn.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.BarcodeLookup
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.domain.nutrition.Gtin
import com.example.kpkn.screens.nutrition.scan.MlKitBarcodeDecoder
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SOLO DEPURACIÓN. Autocomprobación del lector SIN cámara: dibuja en memoria códigos EAN-13, EAN-8 y UPC-A válidos (varias escalas,
 * uno girado, uno desenfocado) y uno con el dígito de control mal, los pasa por el MISMO decodificador de ML Kit empaquetado que usa
 * la cámara y por la misma ruta de validación y búsqueda (`Gtin` + `NutritionRepository.findFoodByBarcode`) contra una base EN
 * MEMORIA con tres productos de ejemplo. Escribe una línea por caso y el resumen en logcat con la etiqueta `MfpBarcodeSelfTest`
 * (`SELFTEST OK n/n` o `SELFTEST FAIL …`) y lo muestra en pantalla. No toca la base real ni abre la cámara.
 * Lanzar: `--actions "launch:BarcodeSelfTestActivity"`.
 */
class BarcodeSelfTestActivity : ComponentActivity() {
    private val lines = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        setContent {
            KPKNTheme {
                Column(Modifier.fillMaxSize().background(KpknSurface.canvas).statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text("Autocomprobación del lector de códigos", style = KpknType.title, color = WizardColors.text)
                    lines.forEach { Text(it, style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }
        lifecycleScope.launch(Dispatchers.Default) { runSelfTest() }
    }

    private fun log(line: String) {
        Log.i(TAG, line)
        runOnUiThread { lines += line }
    }

    /** Qué debe pasar con un caso: el código canónico que sale del lector y la validación (null = se rechaza) y el resultado de la búsqueda. */
    private class Case(val label: String, val bitmap: Bitmap, val canonical: String?, val lookup: String?)

    private suspend fun runSelfTest() {
        val decoder = MlKitBarcodeDecoder()
        val db = KpknDatabase.createInMemory(applicationContext)
        val repo = newRepository(applicationContext, db)
        var passed = 0
        val failures = mutableListOf<String>()
        val cases = buildCases()
        try {
            seed(db)
            for (case in cases) {
                val outcome = try {
                    val raw = decoder.decode(case.bitmap)
                    val canonical = raw.firstNotNullOfOrNull { Gtin.normalize(it) }
                    val lookup = canonical?.let { describe(repo.findFoodByBarcode(it)) }
                    Triple(raw, canonical, lookup)
                } catch (error: Exception) {
                    log("CASE ${case.label}: EXCEPCIÓN ${error.javaClass.simpleName}: ${error.message}")
                    failures += case.label
                    continue
                }
                val (raw, canonical, lookup) = outcome
                val ok = canonical == case.canonical && lookup == case.lookup
                log("CASE ${case.label}: leído=$raw canónico=$canonical búsqueda=$lookup esperado=${case.canonical}/${case.lookup} -> ${if (ok) "OK" else "FAIL"}")
                if (ok) passed++ else failures += case.label
            }
        } finally {
            decoder.close()
            withContext(Dispatchers.IO) {
                runCatching {
                    val field = NutritionRepository::class.java.getDeclaredField("repositoryJob").apply { isAccessible = true }
                    (field.get(repo) as Job).cancel()
                }
                runCatching { db.close() }
            }
            cases.forEach { it.bitmap.recycle() }
        }
        log(if (failures.isEmpty()) "SELFTEST OK $passed/${cases.size}" else "SELFTEST FAIL $passed/${cases.size}: ${failures.joinToString("; ")}")
    }

    private fun describe(lookup: BarcodeLookup): String = when (lookup) {
        is BarcodeLookup.Found -> "Found:${lookup.food.id}"
        BarcodeLookup.NotFound -> "NotFound"
        BarcodeLookup.Invalid -> "Invalid"
    }

    /** El repositorio sobre la base en memoria, por el constructor privado que usan las pruebas (no arranca la carga del catálogo). */
    private fun newRepository(context: Context, db: KpknDatabase): NutritionRepository {
        val ctor = NutritionRepository::class.java.getDeclaredConstructor(Context::class.java, KpknDatabase::class.java, Boolean::class.javaPrimitiveType)
        ctor.isAccessible = true
        return ctor.newInstance(context, db, false)
    }

    private suspend fun seed(db: KpknDatabase) {
        fun row(id: String, name: String, code: String) = GlobalFoodEntity(
            foodId = id, name = name, brand = "Ejemplo", calories = 60.0, protein = 3.0, carbs = 5.0, fats = 3.0,
            source = "OFF Chile", sourcePriority = 80, verifiedScore = 0.72, sourceRecordId = code,
        )
        db.nutritionDao().insertGlobalFoods(
            listOf(
                row("off_4006381333931", "Leche de ejemplo", "4006381333931"),
                // Guardado en su forma corta de 8 dígitos y sin el cero inicial del UPC-A: la búsqueda prueba las variantes.
                row("off_96385074", "Chicle de ejemplo", "96385074"),
                row("off_36000291452", "Cereal de ejemplo", "36000291452"),
            ),
        )
    }

    private fun buildCases(): List<Case> = listOf(
        Case("EAN-13 4006381333931 (x3)", BarcodeBitmaps.render(BarcodeBitmaps.ean13("4006381333931"), 3), "4006381333931", "Found:off_4006381333931"),
        Case("EAN-13 7801234567894 (x5, sin producto)", BarcodeBitmaps.render(BarcodeBitmaps.ean13("7801234567894"), 5), "7801234567894", "NotFound"),
        Case("EAN-13 4006381333931 (x8, grande)", BarcodeBitmaps.render(BarcodeBitmaps.ean13("4006381333931"), 8), "4006381333931", "Found:off_4006381333931"),
        Case("EAN-8 96385074 (x4)", BarcodeBitmaps.render(BarcodeBitmaps.ean8("96385074"), 4), "0000096385074", "Found:off_96385074"),
        Case("UPC-A 036000291452 (x4)", BarcodeBitmaps.render(BarcodeBitmaps.ean13("0036000291452"), 4), "0036000291452", "Found:off_36000291452"),
        Case("EAN-13 girado 4 grados (x4)", BarcodeBitmaps.rotate(BarcodeBitmaps.render(BarcodeBitmaps.ean13("4006381333931"), 4), 4f), "4006381333931", "Found:off_4006381333931"),
        Case("EAN-13 desenfocado (x5)", BarcodeBitmaps.soften(BarcodeBitmaps.render(BarcodeBitmaps.ean13("7801234567894"), 5)), "7801234567894", "NotFound"),
        Case("EAN-13 con dígito de control mal 4006381333932 (se rechaza)", BarcodeBitmaps.render(BarcodeBitmaps.ean13("4006381333932"), 4), null, null),
    )

    companion object {
        const val TAG = "MfpBarcodeSelfTest"
    }
}

/** Códigos de barras dibujados en memoria: barras negras sobre blanco, con la zona en blanco a los lados. Solo para la autocomprobación. */
internal object BarcodeBitmaps {
    private val L = arrayOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
    private val G = arrayOf("0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111")
    private val R = arrayOf("1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100")
    private val PARITY = arrayOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")

    /** Las 95 columnas (1 = barra) de un EAN-13; un UPC-A es el EAN-13 con un cero delante. */
    fun ean13(digits: String): String {
        require(digits.length == 13)
        val parity = PARITY[digits[0] - '0']
        val out = StringBuilder("101")
        for (i in 1..6) out.append(if (parity[i - 1] == 'L') L[digits[i] - '0'] else G[digits[i] - '0'])
        out.append("01010")
        for (i in 7..12) out.append(R[digits[i] - '0'])
        out.append("101")
        return out.toString()
    }

    /** Las 67 columnas de un EAN-8. */
    fun ean8(digits: String): String {
        require(digits.length == 8)
        val out = StringBuilder("101")
        for (i in 0..3) out.append(L[digits[i] - '0'])
        out.append("01010")
        for (i in 4..7) out.append(R[digits[i] - '0'])
        out.append("101")
        return out.toString()
    }

    /** Dibuja [modules] con [moduleSize] píxeles por columna, 10 columnas en blanco a cada lado y un margen vertical. */
    fun render(modules: String, moduleSize: Int): Bitmap {
        val quiet = 10
        val barHeight = moduleSize * 40
        val margin = moduleSize * 8
        val bitmap = Bitmap.createBitmap((modules.length + 2 * quiet) * moduleSize, barHeight + 2 * margin, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL; isAntiAlias = false }
        modules.forEachIndexed { index, c ->
            if (c == '1') {
                val left = ((quiet + index) * moduleSize).toFloat()
                canvas.drawRect(left, margin.toFloat(), left + moduleSize, (margin + barHeight).toFloat(), paint)
            }
        }
        return bitmap
    }

    /** El mismo código girado [degrees] grados sobre un fondo blanco algo mayor. */
    fun rotate(source: Bitmap, degrees: Float): Bitmap {
        val pad = source.height / 2
        val out = Bitmap.createBitmap(source.width + 2 * pad, source.height + 2 * pad, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val matrix = Matrix().apply {
            postTranslate(pad.toFloat(), pad.toFloat())
            postRotate(degrees, out.width / 2f, out.height / 2f)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return out
    }

    /** Desenfoque suave: se reduce a la mitad y se vuelve a ampliar con filtro. */
    fun soften(source: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(source, source.width / 2, source.height / 2, true)
        val back = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        small.recycle()
        source.recycle()
        return back
    }
}
