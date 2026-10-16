package com.example.kpkn.screens.workout

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Lo que se dice cuando Atrás no cierra una hoja que pide una decisión. */
internal const val BLOCKED_BACK_HINT = "Elige una opción para continuar"

/**
 * Atrás en una hoja que exige una decisión (adelanto de volumen, molestias tras un error de ejecución): no se cierra, pero avisa de por
 * qué en vez de quedarse muda y parecer rota (LV4, 15). Se llama desde DENTRO del contenido de la hoja para que su manejador tenga
 * prioridad sobre el de la propia hoja, que consume Atrás en silencio.
 */
@Composable
internal fun BlockedBackHint(enabled: Boolean = true) {
    val context = LocalContext.current
    BackHandler(enabled = enabled) {
        Toast.makeText(context, BLOCKED_BACK_HINT, Toast.LENGTH_SHORT).show()
    }
}
