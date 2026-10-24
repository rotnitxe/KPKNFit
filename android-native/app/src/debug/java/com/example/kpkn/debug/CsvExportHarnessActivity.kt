package com.example.kpkn.debug

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.export.CsvExportDomain
import com.example.kpkn.screens.settings.export.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme

/** Selección CSV en memoria: data, empty, multiple, error o busy. No escribe archivos. */
class CsvExportHarnessActivity : ComponentActivity() {
    @Suppress("DEPRECATION") private fun extra(key: String): String? = intent.extras?.get(key)?.toString()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        val state = extra("state") ?: "data"
        val scale = extra("fontScale")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.8f, 2f) ?: 1f
        val width = extra("widthDp")?.toIntOrNull()?.coerceIn(280,1200) ?: 360
        val reduced = extra("reducedMotion")?.toBooleanStrictOrNull() ?: false
        setContent { KPKNTheme {
            val density = LocalDensity.current
            var selected by remember { mutableStateOf(if (state == "multiple") CsvExportDomain.entries.toSet() else if (state == "empty") emptySet() else setOf(CsvExportDomain.MEALS)) }
            var exports by remember { mutableIntStateOf(0) }
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides reduced) {
                Box(Modifier.fillMaxSize().background(KpknSurface.canvas).statusBarsPadding().navigationBarsPadding()) {
                    CsvExportContent(selected, { key -> selected = if (key in selected) selected - key else selected + key }, state == "busy",
                        if (state == "error") "No se pudo preparar la exportación." else if (exports > 0) "Vista previa: destino confirmado en memoria ($exports)." else null,
                        state != "empty", { exports++ }, { finish() }, Modifier.widthIn(max = width.dp).fillMaxSize())
                }
            }
        } }
    }
}
