package com.example.kpkn.screens.home.design

import androidx.compose.foundation.layout.padding
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

/** Aire entre el módulo anterior y la etiqueta de grupo «Tus programas», y tras el carrusel: 28 dp. */
internal val HomeGroupGap: Dp = 28.dp

/** Aire entre la cabecera de la página y el primer módulo: 20 dp (I12). */
internal val HomePageGap: Dp = 20.dp

/**
 * Escala de letra a partir de la cual las filas de cifras (comidas, datos de Cuerpo) pasan de una fila a dos de dos. A 130 % de letra,
 * cuatro celdas de 72 dp no caben sin cortar ni solapar.
 */
internal const val HomeLargeTextScale = 1.15f

/**
 * Aire de arriba de un ítem de la página (I12): la única regla de separación. La `LazyColumn` de Home no separa sus ítems
 * (`Arrangement.Top`); cada ítem pone aquí su aire: [HomePageGap] bajo la cabecera, [HomeModuleGap] entre módulos seguidos y
 * [HomeGroupGap] antes de una etiqueta de grupo o tras un carrusel. Es relleno, no desplazamiento: el aire ocupa sitio y un ítem
 * ausente no deja hueco. Va en el modificador más externo del ítem, para que su ancla mida el borde del módulo.
 */
internal fun Modifier.homeGapAbove(gap: Dp): Modifier = padding(top = gap)
