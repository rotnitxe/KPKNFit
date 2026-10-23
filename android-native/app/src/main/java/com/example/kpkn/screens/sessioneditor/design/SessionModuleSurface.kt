package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSurface

/**
 * Lugar de un trozo dentro de su módulo (SESSION_EDITOR_V1 §2, punto 6). Una parte es un módulo, pero la lista del editor son ítems
 * perezosos: cada trozo (cabecera, ejercicio, superserie, pie) pinta su parte del relleno, y solo el primero y el último redondean.
 */
enum class SessionModuleEdge { TOP, MIDDLE, BOTTOM, ONLY }

/** Lugar de un trozo: [first] y [last] dicen si es el primero y el último de su módulo. Si es los dos, va como [SessionModuleEdge.ONLY]. */
internal fun sessionModuleEdge(first: Boolean, last: Boolean): SessionModuleEdge = when {
    first && last -> SessionModuleEdge.ONLY
    first -> SessionModuleEdge.TOP
    last -> SessionModuleEdge.BOTTOM
    else -> SessionModuleEdge.MIDDLE
}

/** Aire debajo de un módulo: solo lo pone el último trozo (12 dp, KPKN_UI_V2 §1). Va fuera del relleno, como padding previo. */
internal fun SessionModuleEdge.bottomGap(): Dp =
    if (this == SessionModuleEdge.BOTTOM || this == SessionModuleEdge.ONLY) 12.dp else 0.dp

/**
 * Relleno `module` de un trozo con su filete de 1 dp (`moduleBorder`) en los lados y en el borde que le toca. Va DESPUÉS del padding que da
 * el margen de la pantalla, así que el relleno ocupa solo la zona interior. Radio de 24 dp solo en las esquinas exteriores del módulo.
 */
internal fun Modifier.sessionModuleSurface(edge: SessionModuleEdge, highlighted: Boolean = false): Modifier = drawBehind {
    val radius = 24.dp.toPx()
    // Con [highlighted] (el módulo al que se va a soltar un ejercicio, ED-A · A3) el filete es de 2 dp y en tinta.
    val stroke = (if (highlighted) 2f else 1f).dp.toPx()
    val half = stroke / 2f
    val w = size.width
    val h = size.height
    val roundTop = edge == SessionModuleEdge.TOP || edge == SessionModuleEdge.ONLY
    val roundBottom = edge == SessionModuleEdge.BOTTOM || edge == SessionModuleEdge.ONLY
    val topR = if (roundTop) radius else 0f
    val bottomR = if (roundBottom) radius else 0f

    // Relleno: el contorno exacto del trozo.
    val fill = Path().apply {
        addRoundRect(
            RoundRect(
                0f, 0f, w, h,
                CornerRadius(topR), CornerRadius(topR), CornerRadius(bottomR), CornerRadius(bottomR),
            ),
        )
    }
    drawPath(fill, KpknSurface.module)

    // Filete: los lados y el borde que le toca. El borde que no le toca cae fuera del recorte y no se ve.
    val far = radius * 2f + stroke
    val border = Path().apply {
        addRoundRect(
            RoundRect(
                half,
                if (roundTop) half else -far,
                w - half,
                if (roundBottom) h - half else h + far,
                CornerRadius(topR), CornerRadius(topR), CornerRadius(bottomR), CornerRadius(bottomR),
            ),
        )
    }
    clipRect(0f, 0f, w, h) {
        drawPath(border, if (highlighted) WizardColors.text else KpknSurface.moduleBorder, style = Stroke(width = stroke))
    }
}
