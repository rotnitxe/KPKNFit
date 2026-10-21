package com.example.kpkn.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Superficies de la app (KPKN_UI_V2 §1): negro puro de fondo y módulos en gris casi negro con filetes de 1 dp.
 * El wizard conserva las suyas (`WizardColors`).
 */
object KpknSurface {
    /** Fondo de toda pantalla. */
    val canvas = Color(0xFF000000)

    /** Relleno de un módulo. */
    val module = Color(0xFF111111)

    /** Módulo o fila pulsados. */
    val modulePressed = Color(0xFF161616)

    /** Filete de 1 dp del módulo: blanco al 7 %. */
    val moduleBorder = Color.White.copy(alpha = 0.07f)

    /** Elementos tonales dentro de un módulo (chips, círculo de icono). */
    val inset = Color(0xFF1A1A1A)

    /** Separador de 1 dp dentro de un módulo: blanco al 6 %. */
    val divider = Color.White.copy(alpha = 0.06f)
}

/** Formas de la app (KPKN_UI_V2 §1). */
object KpknShape {
    /** Módulos: radio 24 dp. */
    val module: Shape = RoundedCornerShape(24.dp)

    /** Islas de vidrio (barra superior, barra inferior, botones flotantes): radio 32 dp. */
    val island: Shape = RoundedCornerShape(32.dp)

    /** Hojas: radio 28 dp solo arriba. */
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Botones e insignias: redondeo completo. */
    val pill: Shape = RoundedCornerShape(percent = 50)
}
