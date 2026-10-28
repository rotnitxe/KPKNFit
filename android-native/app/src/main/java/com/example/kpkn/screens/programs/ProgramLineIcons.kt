package com.example.kpkn.screens.programs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Los iconos del lienzo v6 de Programas que el set KPKN Line todavía no trae: subir, destello, filtro, pegar y ojo. Mismas reglas del
 * set (retícula de 24, trazo de 1,75 con extremos y uniones redondeados) y los mismos trazados que `app.js` del lienzo. Viven aquí y no
 * en `KpknLineIcons` para no pisar a otros paquetes que tocan ese archivo.
 */
internal object ProgramLineIcons {

    /** Subir: flecha hacia arriba sobre una bandeja. Archivar, Importar y «Elegir archivo». */
    val Upload: ImageVector by lazy {
        icono("ProgramLine.Upload", listOf("M12 15V4", "M7.5 8.5 12 4l4.5 4.5", "M5 15v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3"))
    }

    /** Destello de cuatro puntas. «A tu medida» y «Crear a mi medida». */
    val Spark: ImageVector by lazy {
        icono("ProgramLine.Spark", listOf("M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8z"))
    }

    /** Filtro: tres rayas que se acortan. El botón de filtros del catálogo. */
    val Filter: ImageVector by lazy {
        icono("ProgramLine.Filter", listOf("M4 6h16", "M7 12h10", "M10 18h4"))
    }

    /** Pegar: tablero con renglones. «Pegar texto». */
    val Paste: ImageVector by lazy {
        icono(
            "ProgramLine.Paste",
            listOf(
                "M8 4.5h8a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3v-10a3 3 0 0 1 3-3z",
                "M9 4.5h6v3H9z",
                "M9 12h6",
                "M9 16h4",
            ),
        )
    }

    /** Ojo con pupila. «Foto de tu rutina». */
    val Eye: ImageVector by lazy {
        icono(
            "ProgramLine.Eye",
            listOf(
                "M2.5 12C2.5 12 6 5.5 12 5.5C18 5.5 21.5 12 21.5 12C21.5 12 18 18.5 12 18.5C6 18.5 2.5 12 2.5 12z",
                "M12 9.2a2.8 2.8 0 1 0 0 5.6 2.8 2.8 0 0 0 0-5.6z",
            ),
        )
    }

    private fun icono(nombre: String, trazados: List<String>): ImageVector {
        val tinta = SolidColor(Color.Black)
        val builder = ImageVector.Builder(
            name = nombre,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (d in trazados) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = tinta,
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
