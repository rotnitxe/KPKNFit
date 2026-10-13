package com.example.kpkn.screens.home.design

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Ritmo y márgenes de los módulos de Home (KPKN_UI_V2 §1 y Home v1.1 §8): el margen de la pantalla, el aire entre módulos seguidos y
 * el aire antes de una etiqueta de grupo.
 */

/** Margen lateral de un módulo respecto a la pantalla: 16 dp. Su relleno interior (20 dp) va dentro del módulo. */
internal val HomeModuleMargin: Dp = 16.dp

/** Aire entre dos módulos seguidos: 12 dp. */
internal val HomeModuleGap: Dp = 12.dp

/** Aire entre el módulo anterior y la etiqueta de grupo «Tus programas»: 28 dp. */
internal val HomeGroupGap: Dp = 28.dp

/**
 * Escala de letra a partir de la cual las filas de cifras (comidas, datos de Cuerpo) pasan de una fila a dos de dos. A 130 % de letra,
 * cuatro celdas de 72 dp no caben sin cortar ni solapar.
 */
internal const val HomeLargeTextScale = 1.15f

/**
 * La `LazyColumn` de Home separa sus ítems con [sectionGap] (`HomeSectionGap`, 40 dp) y ese `verticalArrangement` es común a todas las
 * secciones, así que no se toca. Para que el aire entre este ítem y el anterior sea [target], el ítem sube la diferencia. Se pone en el
 * ítem que sigue a un módulo.
 */
internal fun Modifier.homeGapAbove(sectionGap: Dp, target: Dp = HomeModuleGap): Modifier =
    offset(y = target - sectionGap)
