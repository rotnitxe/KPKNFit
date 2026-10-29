package com.example.kpkn.screens.nutrition.design.logger

import java.text.Normalizer
import java.util.Locale

/** Cómo se pinta un trozo del texto de la comida (lienzo `.tok`): sin marca, leyéndose (acento) o ya convertido en alimento (verde). */
enum class LoggerTokenState { PLAIN, READING, DONE }

/** Un trozo del texto («dos tostadas») con su posición y su estado. */
data class LoggerToken(val start: Int, val end: Int, val state: LoggerTokenState)

private val SEPARATORS = Regex("(,|;|\\+|\\n|\\s+con\\s+|\\s+y\\s+|\\s+más\\s+|\\s+mas\\s+)", RegexOption.IGNORE_CASE)

/**
 * Las menciones de [text] en orden: lo que queda entre comas, «con», «y», «más», «+» o saltos de línea, sin espacios en los bordes.
 * «Dos huevos revueltos con dos tostadas, medio aguacate y un café con leche» da «Dos huevos revueltos», «dos tostadas», «medio aguacate»,
 * «un café» y «leche».
 */
fun loggerMentionRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var cursor = 0
    fun add(from: Int, to: Int) {
        var s = from
        var e = to
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && (text[e - 1].isWhitespace() || text[e - 1] == '.')) e--
        if (e > s) ranges += s until e
    }
    SEPARATORS.findAll(text).forEach { match ->
        add(cursor, match.range.first)
        cursor = match.range.last + 1
    }
    add(cursor, text.length)
    return ranges
}

/**
 * Los trozos mientras se analiza (f-ana): los anteriores a [readingIndex] ya leídos, el de [readingIndex] leyéndose y el resto sin marca.
 * Con [readingIndex] negativo (movimiento reducido), todos se marcan como leyéndose a la vez.
 */
fun loggerReadingTokens(text: String, readingIndex: Int): List<LoggerToken> =
    loggerMentionRanges(text).mapIndexed { index, range ->
        val state = when {
            readingIndex < 0 -> LoggerTokenState.READING
            index < readingIndex -> LoggerTokenState.DONE
            index == readingIndex -> LoggerTokenState.READING
            else -> LoggerTokenState.PLAIN
        }
        LoggerToken(range.first, range.last + 1, state)
    }

/**
 * Los trozos después de analizar (f-result): en verde los que se convirtieron en un alimento de [foodMentions] (el texto de cada
 * alimento), sin marca los demás. Coinciden si uno contiene al otro o si comparten una palabra de cuatro letras o más.
 */
fun loggerResultTokens(text: String, foodMentions: List<String>): List<LoggerToken> {
    val mentions = foodMentions.map(::tokenNormalize).filter { it.isNotBlank() }
    return loggerMentionRanges(text).mapNotNull { range ->
        val piece = tokenNormalize(text.substring(range.first, range.last + 1))
        val matched = mentions.any { mention ->
            piece.contains(mention) || mention.contains(piece) ||
                piece.split(' ').any { word -> word.length >= 4 && mention.split(' ').any { it == word || (it.length >= 4 && (it.startsWith(word) || word.startsWith(it))) } }
        }
        if (matched) LoggerToken(range.first, range.last + 1, LoggerTokenState.DONE) else null
    }
}

private fun tokenNormalize(value: String): String =
    Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9ñ ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
