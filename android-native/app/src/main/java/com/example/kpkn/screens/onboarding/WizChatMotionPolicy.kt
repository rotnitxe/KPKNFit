package com.example.kpkn.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

@Composable
fun wizChatReducedMotion(): Boolean = LocalView.current.context.contentResolver.let { resolver ->
    runCatching { android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
}
