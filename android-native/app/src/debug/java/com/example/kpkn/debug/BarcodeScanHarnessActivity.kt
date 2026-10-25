package com.example.kpkn.debug

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.BarcodeLookup
import com.example.kpkn.data.models.BarcodeOrigin
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.Gtin
import com.example.kpkn.screens.nutrition.customfood.CustomFoodForm
import com.example.kpkn.screens.nutrition.customfood.CustomFoodFormOverlay
import com.example.kpkn.screens.nutrition.scan.BarcodeScanFlow
import com.example.kpkn.screens.nutrition.scan.CameraAccess
import com.example.kpkn.screens.nutrition.scan.ScanPreviewPlaceholder
import com.example.kpkn.screens.nutrition.scan.ScriptedBarcodeSource
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.delay

/**
 * SOLO DEPURACIÓN. Escáner de códigos de barras y formulario de alimento propio con DATOS EN MEMORIA y una fuente de códigos
 * falsa: no abre la cámara ni escribe en la base real. Extras: `state` = camera (por defecto) | permission | manual | found |
 * not_found | found_flags | custom_form | custom_form_error, más `fontScale`, `reducedMotion` y `widthDp`.
 *
 * - `camera`, `permission` y `manual` dejan los botones «Simular» (arriba a la derecha) para entregar lecturas a la fuente falsa.
 * - `found`, `not_found` y `found_flags` entregan solas dos lecturas iguales, como la cámara: recorren el modelo y la validación reales.
 * - El permiso de cámara de `permission` es el real del sistema; concederlo deja la vista previa simulada.
 * Lanzar: `--actions "launch:BarcodeScanHarnessActivity|--es|state|found"`.
 */
class BarcodeScanHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        val name = intent.getStringExtra("state") ?: "camera"
        val scale = extra("fontScale")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.8f, 2f) ?: 1f
        val width = extra("widthDp")?.toIntOrNull()?.coerceIn(280, 1200) ?: 360
        val reduced = extra("reducedMotion")?.toBooleanStrictOrNull() ?: false

        // «Base de datos» en memoria: un producto del catálogo, uno con datos incompletos y lo que se cree aquí.
        val leche = FoodItem(
            id = "off_7801234567894", name = "leche entera", brand = "Colún", calories = 60.0, protein = 3.2, carbs = 4.8, fats = 3.0,
            source = "OFF Chile", sourceRecordId = "7801234567894", nutritionBasis = "PER_100G_AS_SOLD", sourcePriority = 80, verifiedScore = 0.72,
        )
        val incompleto = FoodItem(
            id = "off_4006381333931", name = "barrita sin datos", brand = "Marca X", source = "OFF Chile", sourceRecordId = "4006381333931",
            nutritionBasis = "PER_100G_AS_SOLD", qualityFlags = listOf("INCOMPLETE", "LOW_QUALITY"),
        )
        val propios = mutableMapOf<String, FoodItem>()
        val lookup: suspend (String) -> BarcodeLookup = { code ->
            val candidates = Gtin.candidates(code)
            when {
                candidates.isEmpty() -> BarcodeLookup.Invalid
                else -> {
                    val own = propios.values.firstOrNull { it.sourceRecordId in candidates }
                    val global = listOf(leche, incompleto).firstOrNull { it.sourceRecordId in candidates }
                    when {
                        own != null -> BarcodeLookup.Found(own, BarcodeOrigin.PROPIO)
                        global != null -> BarcodeLookup.Found(global, BarcodeOrigin.CATALOGO)
                        else -> BarcodeLookup.NotFound
                    }
                }
            }
        }
        val saveCustom: suspend (FoodItem) -> Unit = { food -> propios[food.id] = food }
        val source = ScriptedBarcodeSource()

        setContent {
            KPKNTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides reduced) {
                    Box(Modifier.fillMaxSize().background(KpknSurface.canvas), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = width.dp).fillMaxSize()) {
                            when (name) {
                                "custom_form" -> CustomFoodFormOverlay(
                                    initial = null, save = saveCustom, onSaved = { finish() }, onDismiss = { finish() }, barcode = "7801234567894",
                                )
                                "custom_form_error" -> CustomFoodFormOverlay(
                                    initial = null, save = saveCustom, onSaved = { finish() }, onDismiss = { finish() },
                                    initialForm = CustomFoodForm(
                                        name = "Galletas de avena", barcode = "12345", servingAmount = "abc", calories = "300", protein = "2", carbs = "21", fats = "4,5",
                                    ),
                                    showErrorsInitially = true,
                                )
                                else -> {
                                    val access = when (name) {
                                        "permission" -> CameraAccess.ASK
                                        "manual" -> CameraAccess.DENIED
                                        else -> CameraAccess.GRANTED
                                    }
                                    BarcodeScanFlow(
                                        source = source,
                                        lookup = lookup,
                                        saveCustomFood = saveCustom,
                                        onConfirm = { _, _ -> finish() },
                                        onDismiss = { finish() },
                                        preview = { ScanPreviewPlaceholder(it) },
                                        initialAccess = access,
                                    )
                                    val auto = when (name) {
                                        "found" -> "7801234567894"
                                        "found_flags" -> "4006381333931"
                                        "not_found" -> "5901234123457"
                                        else -> null
                                    }
                                    if (auto != null) {
                                        LaunchedEffect(Unit) {
                                            delay(400)
                                            source.emit(auto)
                                            source.emit(auto)
                                        }
                                    } else {
                                        SimulateButtons(source)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION") private fun extra(key: String): String? = intent.extras?.get(key)?.toString()
}

/** Botones para entregar lecturas a la fuente falsa; cada uno la entrega dos veces, como dos fotogramas seguidos. */
@androidx.compose.runtime.Composable
private fun androidx.compose.foundation.layout.BoxScope.SimulateButtons(source: ScriptedBarcodeSource) {
    val cases = remember {
        listOf(
            "Simular leche (catálogo)" to "7801234567894",
            "Simular barrita incompleta" to "4006381333931",
            "Simular desconocido" to "5901234123457",
            "Simular ruido (inválido)" to "1234567890123",
        )
    }
    Column(
        Modifier.zIndex(500f).align(Alignment.TopEnd).statusBarsPadding().padding(top = 64.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        cases.forEach { (label, code) ->
            Text(
                label,
                style = KpknType.caption,
                color = WizardColors.text,
                modifier = Modifier
                    .background(KpknSurface.inset, RoundedCornerShape(12.dp))
                    .clickable { source.emit(code); source.emit(code) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
