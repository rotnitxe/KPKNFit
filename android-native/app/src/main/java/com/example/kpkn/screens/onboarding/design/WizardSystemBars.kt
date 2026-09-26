package com.example.kpkn.screens.onboarding.design

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Iconos claros de barra de sistema mientras dura esta composición.
 *
 * El wizard vive sobre antracita sólido (`WizardColors.background`); `enableEdgeToEdge`
 * deja los iconos oscuros y quedan ilegibles como se vio en `gate-before.png`. Este
 * efecto es **local y acotado**: guarda el aspecto previo y lo restaura al salir de la
 * composición, sin tocar `MainActivity` ni crear un estado global para la app.
 */
@Composable
fun WizardDarkSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val controller = view.context.findActivity()?.window?.let {
            WindowCompat.getInsetsController(it, view)
        }
        if (controller == null) {
            onDispose {}
        } else {
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNav = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNav
            }
        }
    }
}

/**
 * Recorre la cadena de `baseContext` (p. ej. el wrapper de `LocaleManager`) hasta
 * la instancia de `Activity`, que es la que expone la ventana del sistema.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}