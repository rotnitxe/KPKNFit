package com.example.kpkn.screens.nutrition.design.logger

import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.HouseholdPortions
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** En qué se cuenta la cantidad de la hoja de ajuste (f-edit) y de la ficha (s-detail). */
enum class LoggerUnitKind { NATURAL, GRAMS, SERVINGS, SPOONS }

/**
 * Una unidad de cantidad: «huevo / huevos» (la natural del alimento), «gramo / gramos», «ración / raciones» o «cucharada / cucharadas»,
 * con cuántos gramos vale una.
 */
data class LoggerUnit(val kind: LoggerUnitKind, val singular: String, val plural: String, val gramsPerUnit: Double) {
    /** La chip de la unidad («Huevos», «Gramos», «Raciones»). */
    val chipLabel: String get() = plural.replaceFirstChar { it.titlecase(Locale.ROOT) }
}

private data class LoggerNoun(val keys: List<String>, val singular: String, val plural: String, val volume: Boolean = false)

/** El nombre de la unidad natural según el alimento: el primero que encaja gana. */
private val NOUNS = listOf(
    LoggerNoun(listOf("huevo"), "huevo", "huevos"),
    LoggerNoun(listOf("biscote"), "biscote", "biscotes"),
    LoggerNoun(listOf("galleta"), "galleta", "galletas"),
    LoggerNoun(listOf("tostad", "rebanada", "pan de molde", "pan integral", "pan blanco", "marraqueta", "hallulla"), "rebanada", "rebanadas"),
    LoggerNoun(listOf("yogur", "envase"), "envase", "envases"),
    LoggerNoun(listOf("cafe", "leche", "taza", "infusion", " te ", "cacao"), "taza", "tazas", volume = true),
    LoggerNoun(listOf("jugo", "zumo", "refresco", "cerveza", "vaso", "batido"), "vaso", "vasos", volume = true),
    LoggerNoun(listOf("barrita"), "barrita", "barritas"),
    LoggerNoun(
        listOf("aguacate", "palta", "platano", "banana", "manzana", "naranja", "pera", "mandarina", "melocoton", "durazno", "kiwi"),
        "pieza",
        "piezas",
    ),
)

/** Lo que se suele medir a cucharadas: la tercera chip es «Cucharadas» y no «Raciones». */
private val SPOONABLE = listOf("yogur", "miel", "mantequilla", "aceite", "crema", "mermelada", "azucar", "mayonesa", "nata", "queso crema")

private const val SPOON_GRAMS = 15.0

/**
 * Las unidades en que se puede contar [name] (lienzo f-edit «Huevos / Gramos / Raciones», s-detail «Envases / Gramos / Cucharadas»): la
 * natural si el alimento se cuenta por piezas o tiene un recipiente propio, los gramos y las raciones (o las cucharadas si es untable).
 */
fun loggerUnits(name: String, food: FoodItem?, query: String? = null): List<LoggerUnit> {
    val text = unitNormalize(listOfNotNull(name, query, food?.name).joinToString(" "))
    val noun = NOUNS.firstOrNull { noun -> noun.keys.any { text.contains(it) } }
    val countable = food != null && HouseholdPortions.isCountable(food, query ?: name)
    val serving = food?.let { HouseholdPortions.defaultGrams(it, query ?: name) }?.takeIf { it > 0.0 } ?: 100.0
    val natural = when {
        countable -> {
            val grams = HouseholdPortions.unitGrams(food, query ?: name).takeIf { it > 0.0 } ?: serving
            LoggerUnit(LoggerUnitKind.NATURAL, noun?.singular ?: "unidad", noun?.plural ?: "unidades", grams)
        }
        noun != null && food != null -> LoggerUnit(LoggerUnitKind.NATURAL, noun.singular, noun.plural, serving)
        else -> null
    }
    val grams = LoggerUnit(LoggerUnitKind.GRAMS, "gramo", "gramos", 1.0)
    val third = if (SPOONABLE.any { text.contains(it) }) {
        LoggerUnit(LoggerUnitKind.SPOONS, "cucharada", "cucharadas", SPOON_GRAMS)
    } else {
        LoggerUnit(LoggerUnitKind.SERVINGS, "ración", "raciones", serving)
    }
    return listOfNotNull(natural, grams, third)
}

/** Cuántas [unit] son [grams] (sin redondear). */
fun loggerUnitValue(grams: Double, unit: LoggerUnit): Double = if (unit.gramsPerUnit <= 0.0) grams else grams / unit.gramsPerUnit

/**
 * La cifra tal como se lee: los gramos enteros («110»); el resto, a medias («2», «½», «1½»), o con un decimal si no cae en media
 * («1,3»).
 */
fun loggerFormatValue(value: Double, unit: LoggerUnit): String {
    if (unit.kind == LoggerUnitKind.GRAMS) return value.roundToInt().toString()
    val halves = (value * 2).roundToInt()
    return if (abs(value * 2 - halves) < 0.01) {
        val whole = halves / 2
        when {
            halves % 2 == 0 -> whole.toString()
            whole == 0 -> "½"
            else -> "$whole½"
        }
    } else {
        String.format(Locale.forLanguageTag("es"), "%.1f", value)
    }
}

/** «huevo» con una (o media), «huevos» con más. */
fun loggerUnitNoun(value: Double, unit: LoggerUnit): String = if (value <= 1.0 + 1e-9) unit.singular else unit.plural

/**
 * Los gramos tras un toque de −/+ ([direction] −1 o +1): 10 g en gramos (5 g por debajo de 10), una unidad en lo demás (media por
 * debajo de una). Nunca baja de [loggerMinGrams].
 */
fun loggerStepGrams(grams: Double, unit: LoggerUnit, direction: Int): Double {
    val value = loggerUnitValue(grams, unit)
    val next = when (unit.kind) {
        LoggerUnitKind.GRAMS -> {
            val step = if (value < 10.0 || (direction < 0 && value <= 10.0)) 5.0 else 10.0
            val snapped = if (direction > 0) floor(value / step) * step + step else kotlin.math.ceil(value / step) * step - step
            snapped
        }
        else -> {
            val step = if (value < 1.0 || (direction < 0 && value <= 1.0)) 0.5 else 1.0
            val snapped = if (direction > 0) floor(value / step + 1e-9) * step + step else kotlin.math.ceil(value / step - 1e-9) * step - step
            snapped
        }
    }
    return max(loggerMinGrams(unit), next * unit.gramsPerUnit)
}

/** Lo mínimo que deja el −: 5 g, o media unidad. */
fun loggerMinGrams(unit: LoggerUnit): Double = if (unit.kind == LoggerUnitKind.GRAMS) 5.0 else unit.gramsPerUnit * 0.5

/**
 * La cantidad de la tarjeta (lienzo `foodCard`): «2 huevos · 110 g», «½ pieza · 70 g», «1 taza · 240 ml» si el alimento tiene unidad
 * natural; «150 g» si no. [volumeUnit] es «ml» para lo que se mide en volumen.
 */
fun loggerAmountLine(grams: Double, units: List<LoggerUnit>, volumeUnit: Boolean = false): String {
    val mass = "${grams.roundToInt()} ${if (volumeUnit) "ml" else "g"}"
    val natural = units.firstOrNull { it.kind == LoggerUnitKind.NATURAL } ?: return mass
    val value = loggerUnitValue(grams, natural)
    return "${loggerFormatValue(value, natural)} ${loggerUnitNoun(value, natural)} · $mass"
}

private fun unitNormalize(value: String): String =
    " " + Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "") + " "
