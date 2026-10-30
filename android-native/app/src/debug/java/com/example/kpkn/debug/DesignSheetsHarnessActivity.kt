package com.example.kpkn.debug

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.kpkn.ui.design.depth.KpknDepthSheet
import com.example.kpkn.ui.design.motion.KpknMotionSheet

/**
 * SOLO DEPURACIÓN (no se integra): dibuja en el teléfono una hoja de calibración de los cimientos de «Pulido v1» (`cm-som-*`, `cm-deg-*`, `cm-vid-1`,
 * `cm-vid-filtros-1`) a pantalla completa y sin barras del sistema, para compararla con la captura del lienzo (`node tools/compare.cjs`). Es la
 * única forma de medir el vidrio real (Haze con `RenderEffect`) y el `blur()`, que la hoja de Robolectric no pinta.
 *
 * Extras (`adb shell am start -n com.example.kpkn.dbg/com.example.kpkn.debug.DesignSheetsHarnessActivity --es sheet cm-vid-1`), siempre por `phone_run.py`:
 *  - `sheet`: id de la hoja (por defecto `cm-vid-1`); las `cm-mov-*` son de movimiento (`KpknMotionSheet`, con su propio reloj), el resto de profundidad.
 *  - `labels` (booleano, por defecto `false`): con rótulos y cabecera, como en la captura completa del lienzo.
 *  - `effects` (booleano, por defecto `true`): `false` dibuja el fondo sin el efecto (`*_raw`).
 * El teléfono de pruebas suele estar bloqueado: la actividad pide mostrarse sobre el bloqueo y encender la pantalla.
 */
class DesignSheetsHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.BLACK
        window.navigationBarColor = AndroidColor.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val sheet = intent.getStringExtra("sheet") ?: "cm-vid-1"
        val labels = intent.getBooleanExtra("labels", false)
        val effects = intent.getBooleanExtra("effects", true)
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (sheet.startsWith("cm-mov")) KpknMotionSheet(sheet = sheet)
                else KpknDepthSheet(sheet = sheet, showLabels = labels, effects = effects)
            }
        }
    }
}
