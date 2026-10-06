package com.example.kpkn.domain.nutrition

import kotlin.math.roundToInt

/**
 * Una banda de porcentaje de grasa con su frase: «Categoría · rasgo visual».
 *
 * [upToInclusive] es el último entero de la banda; la siguiente empieza en el entero siguiente.
 */
private class DescriptorBand(val upToInclusive: Int, val text: String)

/** Las bandas de una figura, de menos a más grasa, y la frase de todo lo que queda por encima de la última. */
private class FigureBands(val bands: List<DescriptorBand>, val openTop: String) {
    fun textFor(wholePercent: Int): String =
        bands.firstOrNull { wholePercent <= it.upToInclusive }?.text ?: openTop
}

/** Frase de la banda abierta por arriba (desde 41 % en la figura masculina y desde 45 % en la femenina). */
private const val VERY_HIGH_VOLUME = "Volumen muy alto · un buen punto de partida para un plan guiado"

/**
 * Figura masculina. Las categorías se alinean con las del ACE (esencial, deportista, en forma, promedio y por
 * encima) pero con nombres llanos y sin juicio: describen lo que se ve, no lo que «debería» ser.
 */
private val MALE_FIGURE = FigureBands(
    bands = listOf(
        DescriptorBand(9, "Competición · definición extrema, venas visibles y mínima reserva"),
        DescriptorBand(13, "Atlético · abdomen marcado y músculo bien separado"),
        DescriptorBand(17, "En forma · abdomen visible y contorno muscular claro"),
        DescriptorBand(21, "Saludable · cintura suave y músculo que se intuye"),
        DescriptorBand(24, "Promedio · forma pareja, el músculo se nota poco"),
        DescriptorBand(29, "Con reserva · el abdomen redondea y la cintura gana volumen"),
        DescriptorBand(34, "Volumen moderado · grasa concentrada en torso y caderas"),
        DescriptorBand(40, "Volumen alto · contorno amplio y uniforme"),
    ),
    openTop = VERY_HIGH_VOLUME,
)

/** Figura femenina: los mismos criterios con los umbrales más altos que el ACE da para mujeres. */
private val FEMALE_FIGURE = FigureBands(
    bands = listOf(
        DescriptorBand(13, "Competición · definición extrema, cerca del mínimo esencial"),
        DescriptorBand(17, "Atlética · abdomen definido y poca grasa en caderas"),
        DescriptorBand(20, "Atlética suave · contorno muscular claro y cintura marcada"),
        DescriptorBand(24, "En forma · curvas firmes y músculo visible en hombros y piernas"),
        DescriptorBand(28, "Saludable · forma redondeada, el músculo se intuye"),
        DescriptorBand(31, "Promedio · más volumen en cintura y caderas"),
        DescriptorBand(37, "Con reserva · volumen en caderas y muslos, estructura cubierta"),
        DescriptorBand(44, "Volumen alto · contorno amplio y uniforme"),
    ),
    openTop = VERY_HIGH_VOLUME,
)

/** Porcentaje que se describe cuando llega algo que no es un número: la mitad de la escala, sin extremos. */
private const val UNKNOWN_PERCENT = 25

/** Los porcentajes se acotan antes de redondear para que ±∞ no desborde el entero. */
private const val MAX_DESCRIBED_PERCENT = 100.0

/**
 * Frase corta («Categoría · rasgo visual») que acompaña al porcentaje de grasa corporal en la regla del alta.
 *
 * Es pura y sin Android. Cambia con el porcentaje y con la figura elegida: [model] `"female"` usa las bandas de la
 * figura femenina; cualquier otro valor (incluido `null`) usa las de la masculina, que es la de arranque del borrador.
 * El tono es descriptivo y respetuoso. Los porcentajes con decimales (un dato antiguo de 17,5 %) se redondean al
 * entero más cercano, el mismo con el que la regla coloca el cursor.
 */
fun bodyFatDescriptor(percent: Double, model: String?): String {
    val whole = if (percent.isNaN()) {
        UNKNOWN_PERCENT
    } else {
        percent.coerceIn(0.0, MAX_DESCRIBED_PERCENT).roundToInt()
    }
    val figure = if (model?.trim()?.equals("female", ignoreCase = true) == true) FEMALE_FIGURE else MALE_FIGURE
    return figure.textFor(whole)
}
