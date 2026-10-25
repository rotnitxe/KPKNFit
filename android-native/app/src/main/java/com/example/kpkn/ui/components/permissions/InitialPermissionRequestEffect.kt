package com.example.kpkn.ui.components.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** A recreated activity continues the same permission flow, including a declined request. */
@Composable
internal fun InitialPermissionRequestEffect(
    onboardingCompleted: Boolean,
    onSetupRoute: Boolean,
    onRequestPermissions: () -> Unit,
) {
    var requested by rememberSaveable { mutableStateOf(false) }
    val requestPermissions by rememberUpdatedState(onRequestPermissions)
    LaunchedEffect(onboardingCompleted, onSetupRoute) {
        if (onboardingCompleted && !onSetupRoute && !requested) {
            requested = true
            requestPermissions()
        }
    }
}
