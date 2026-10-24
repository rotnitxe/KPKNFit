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

import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme

/** Vista previa aislada, sin repositorios ni escrituras de datos. */
class TrainingSettingsHarnessActivity : ComponentActivity() {
    @Suppress("DEPRECATION") private fun extra(key: String): String? = intent.extras?.get(key)?.toString()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        val state = extra("state") ?: "defaults"
        val scale = extra("fontScale")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.8f, 2f) ?: 1f
        val width = extra("widthDp")?.toIntOrNull()?.coerceIn(280, 1200) ?: 360
        val reduced = extra("reducedMotion")?.toBooleanStrictOrNull() ?: false
        setContent { KPKNTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalWizardReducedMotion provides reduced) {
                Box(Modifier.fillMaxSize().background(KpknSurface.canvas).statusBarsPadding().navigationBarsPadding()) {
                    Box(Modifier.widthIn(max=width.dp).fillMaxSize()) { MfpTrainingPreview.Settings(state) }
                }
            }
        } }
    }
}
