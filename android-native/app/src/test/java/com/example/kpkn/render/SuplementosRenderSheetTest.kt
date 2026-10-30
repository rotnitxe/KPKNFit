package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.SupplementPilaItem
import com.example.kpkn.domain.nutrition.supplements.SupplementCatalog
import com.example.kpkn.domain.nutrition.supplements.SupplementTake
import com.example.kpkn.screens.nutrition.design.NutritionSupplementsModule
import com.example.kpkn.screens.nutrition.design.SupplementAddContent
import com.example.kpkn.screens.nutrition.design.SupplementAdjustContent
import com.example.kpkn.screens.nutrition.design.SupplementCeilingContent
import com.example.kpkn.screens.nutrition.design.SupplementRowState
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.time.LocalDate
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Datos de muestra del lienzo de Suplementos (`pilaBase()` de `scenes-sp.js`) para las hojas de render. */
internal object SupplementSamples {
    private val pila = listOf(
        SupplementPilaItem("creatina", 5.0, moment = 1, reminder = "08:00"),
        SupplementPilaItem("vitd", 10.0, moment = 1),
        SupplementPilaItem("omega3", 1.0, moment = 2, reminder = "13:00"),
        SupplementPilaItem("cafeina", 100.0, moment = 3),
        SupplementPilaItem("proteina", 25.0, moment = 4),
        SupplementPilaItem("magnesio", 300.0, moment = 6, reminder = "22:30"),
    )
    private val baseTakes = mapOf(
        "creatina" to listOf(SupplementTake("08:40", 5.0)),
        "vitd" to listOf(SupplementTake("08:40", 10.0)),
    )
    private val allTakes = baseTakes + mapOf(
        "omega3" to listOf(SupplementTake("13:00", 1.0)),
        "cafeina" to listOf(SupplementTake("12:55", 100.0)),
        "proteina" to listOf(SupplementTake("12:55", 25.0)),
        "magnesio" to listOf(SupplementTake("12:55", 300.0)),
    )

    fun rows(all: Boolean = false, techo: Boolean = false): List<SupplementRowState> =
        pila.mapNotNull { item ->
            val takes = (if (all || techo) allTakes else baseTakes)[item.id].orEmpty()
            SupplementCatalog.byId(item.id)?.let { SupplementRowState(it, item, takes) }
        }.sortedBy { it.item.moment }

    /** Seis días anteriores y hoy: la fracción de la pila tomada. */
    val week: List<Float> = listOf(1f, 1f, 0.83f, 1f, 0.83f, 1f, 0.33f)
}

/**
 * HOJA DE RENDER de Suplementos (Pulido v1; no comprueba nada: escribe PNG con el prefijo `sp_` en `KPKN_RENDER_DIR`). El módulo en sus
 * estados (con pila, vacío, «toca ahora», todo tomado y techo superado) dentro de una página de Nutrición recortada, y el contenido de las
 * hojas de ajustar, aviso de techo y añadir sobre su fondo plano. 360 dp de ancho, sin movimiento; la letra al 100 % y, en las densas, al 130 %.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp-mdpi", application = android.app.Application::class)
class SuplementosRenderSheetTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun requested() {
            assumeTrue("Se necesita KPKN_RENDER_DIR para guardar los PNG.", System.getenv("KPKN_RENDER_DIR") != null)
        }

        private val today: LocalDate = LocalDate.of(2026, 10, 28)
        private val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    }

    @get:Rule
    val rule = createComposeRule()

    private var hostView: View? = null

    private fun capture(name: String, scale: Float, fixedHeight: Boolean, content: @Composable () -> Unit) {
        assumeTrue("Sin KPKN_RENDER_DIR no se dibuja nada.", System.getenv("KPKN_RENDER_DIR") != null)
        rule.setContent {
            KPKNTheme {
                hostView = LocalView.current
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, scale), LocalWizardReducedMotion provides true) {
                    Box(
                        Modifier.testTag("sp-root").width(360.dp).then(if (fixedHeight) Modifier.height(880.dp) else Modifier).background(KpknSurface.canvas),
                    ) { content() }
                }
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("sp-root").fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(full)) }
        val cropped = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!).apply { mkdirs() }
        File(dir, "sp_${name}_${(scale * 100).toInt()}.png").outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cropped.recycle()
        full.recycle()
    }

    /** La página de Nutrición del lienzo: fecha, título, una tarjeta de relleno, el módulo y otra tarjeta. */
    @Composable
    private fun Page(
        rows: List<SupplementRowState>,
        caffeineTotal: Double,
        foodCaffeine: Double,
        nowId: String? = null,
        suggestions: List<String> = emptyList(),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 44.dp)) {
            Text("MIÉRCOLES 29", style = KpknType.caption.copy(fontSize = 11.sp, letterSpacing = 1.4.sp), color = Color(0xFF8E8980))
            Text("Nutrición", style = KpknType.title, color = Color(0xFFF2EEE6), modifier = Modifier.padding(top = 4.dp))
            Stub("Hoy · 3 comidas", "1.240 kcal", Modifier.padding(top = 14.dp))
            NutritionSupplementsModule(
                loaded = true,
                rows = rows,
                caffeineTotalMg = caffeineTotal,
                foodCaffeineMg = foodCaffeine,
                creatineStreakDays = 6,
                week = SupplementSamples.week,
                today = today,
                nowId = nowId,
                showHint = rows.none { it.takes.isNotEmpty() && it.def.id == "proteina" },
                suggestionIds = suggestions,
                onTake = {},
                onHold = {},
                onAdd = {},
                onToggleSuggestion = {},
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Stub("Semana", "promedio 2.180 kcal", Modifier)
        }
    }

    @Composable
    private fun Stub(left: String, right: String, modifier: Modifier) {
        Row(
            modifier.fillMaxWidth().padding(vertical = 6.dp).background(KpknSurface.module, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(left, style = KpknType.caption, color = Color(0xFFC4BFB6))
            Text(right, style = KpknType.bodyStrong.copy(fontSize = 14.sp), color = Color(0xFFF2EEE6))
        }
    }

    @Composable
    private fun SheetFrame(height: Boolean = false, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
        Box(Modifier.fillMaxWidth().height(880.dp), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().then(if (height) Modifier.height(760.dp) else Modifier).background(Color(0xFF1B1B1D), sheetShape)
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 34.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }

    private val caffeine get() = SupplementCatalog.byId("cafeina")!!

    @Test fun modulo100() = capture("modulo", 1f, true) { Page(SupplementSamples.rows(), 95.0, 95.0) }
    @Test fun modulo130() = capture("modulo", 1.3f, false) { Page(SupplementSamples.rows(), 95.0, 95.0) }
    @Test fun vacio100() = capture("vacio", 1f, true) { Page(emptyList(), 95.0, 95.0, suggestions = listOf("creatina", "cafeina", "vitd", "omega3", "magnesio")) }
    @Test fun hora100() = capture("hora", 1f, true) { Page(SupplementSamples.rows(), 95.0, 95.0, nowId = "omega3") }
    @Test fun completo100() = capture("completo", 1f, true) { Page(SupplementSamples.rows(all = true), 195.0, 95.0) }
    @Test fun techo100() = capture("techo", 1f, true) { Page(SupplementSamples.rows(techo = true), 495.0, 395.0) }

    @Test fun ajustar100() = capture("ajustar", 1f, true) {
        SheetFrame { SupplementAdjustContent(caffeine, SupplementPilaItem("cafeina", 100.0, moment = 3), 195.0, "12:55", { _, _ -> }, {}) }
    }
    @Test fun ajustar130() = capture("ajustar", 1.3f, false) {
        SheetFrame { SupplementAdjustContent(caffeine, SupplementPilaItem("cafeina", 100.0, moment = 3), 195.0, "12:55", { _, _ -> }, {}) }
    }
    @Test fun aviso100() = capture("aviso", 1f, true) {
        SheetFrame { SupplementCeilingContent(caffeine, 100.0, 495.0, {}, {}) }
    }
    @Test fun anadir100() = capture("anadir", 1f, true) {
        SheetFrame(height = true) { SupplementAddContent(setOf("creatina", "cafeina", "vitd", "omega3", "proteina", "magnesio"), {}, {}) }
    }
}
