package com.example.kpkn.domain.nutrition.label

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class LabelBasis { PER_100G, PER_100ML, PER_SERVING, UNKNOWN }
enum class LabelConfidence { READ, DERIVED, UNSURE }
enum class LabelField(val label: String) { ENERGY_KCAL("Energía"), PROTEIN_G("Proteína"), CARBS_G("Carbohidratos"), FAT_G("Grasas"),
    SATURATED_G("Saturadas"), FIBER_G("Fibra"), SUGAR_G("Azúcar"), SODIUM_MG("Sodio") }
data class LabelValue(val value: Double, val confidence: LabelConfidence, val raw: String)
data class LabelParse(
    val basis: LabelBasis = LabelBasis.UNKNOWN, val servingSize: Double? = null, val servingUnit: String? = null,
    val fields: Map<LabelField, LabelValue> = emptyMap(), val warnings: List<String> = emptyList(),
    val columns: Map<LabelBasis, Map<LabelField, LabelValue>> = emptyMap(), val reason: String? = null,
) {
    /** Cambiar de columna conserva lo leído; convertir entre porción y 100 usa solo una porción explícita compatible. */
    fun forBasis(target: LabelBasis): LabelParse {
        columns[target]?.let { return copy(basis = target, fields = it, reason = null) }
        if (target == basis) return this
        val size = servingSize?.takeIf { it.isFinite() && it > 0.0 }
        val hundred = if (servingUnit == "ml") LabelBasis.PER_100ML else LabelBasis.PER_100G
        val factor = when {
            size != null && basis == hundred && target == LabelBasis.PER_SERVING -> size / 100.0
            size != null && basis == LabelBasis.PER_SERVING && target == hundred -> 100.0 / size
            else -> return copy(warnings = (warnings + "No se puede convertir esta base sin una porción compatible leída.").distinct())
        }
        if (!factor.isFinite() || factor <= 0.0 || fields.values.any { !(it.value * factor).isFinite() }) {
            return copy(warnings = (warnings + "La porción no permite una conversión numérica válida.").distinct())
        }
        return copy(basis = target, fields = fields.mapValues { (_, field) -> field.copy(value = field.value * factor,
            confidence = if (field.confidence == LabelConfidence.UNSURE) LabelConfidence.UNSURE else LabelConfidence.DERIVED) },
            warnings = (warnings + "Valores calculados desde la porción leída; revísalos.").distinct())
    }
}

/** Lee filas ya reconstruidas por OcrLayout; no agrupa posiciones ni inventa valores ausentes. */
object NutritionLabelParser {
    private data class Token(val number: Double, val unit: String, val unsure: Boolean, val bounded: Boolean, val raw: String)
    private val number = Regex("(?<![\\p{L}0-9])([<≤]?\\s*-?\\s*[0-9Oo]+(?:[.,][0-9Oo]+)?)\\s*(kcal|kj|mg|mcg|µg|g|ml|%|9)?(?![\\p{L}])", RegexOption.IGNORE_CASE)
    private val serving = Regex("(?:porcion|racion|serving size)[^\\r\\n]*?([0-9]+(?:[.,][0-9]+)?)\\s*(g|ml)\\b")
    private val basePatterns = listOf(
        Regex("(?:por|per)\\s*100\\s*ml\\b") to LabelBasis.PER_100ML,
        Regex("(?:por|per)\\s*100\\s*g\\b") to LabelBasis.PER_100G,
        Regex("(?:por|per)\\s*(?:porcion|racion|unidad|serving)\\b") to LabelBasis.PER_SERVING,
    )
    private fun normalize(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    fun parse(rows: List<List<String>>, preferred: LabelBasis? = null): LabelParse {
        val cleaned = rows.map { it.map(String::trim) }.filter { row -> row.any { it.isNotBlank() } }
        val warning = mutableListOf<String>()
        val detected = cleaned.flatMap { row -> row.flatMap { cell ->
            val text = normalize(cell)
            basePatterns.flatMap { (pattern, basis) -> pattern.findAll(text).map { it.range.first to basis }.toList() }.sortedBy { it.first }.map { it.second }
        } }.distinct().toMutableList()
        val sizeMatch = cleaned.firstNotNullOfOrNull { row -> serving.find(normalize(row.joinToString(" "))) }
        val size = sizeMatch?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        val servingUnit = sizeMatch?.groupValues?.get(2)
        val namedTable = cleaned.any { row -> normalize(row.joinToString(" ")).let { "nutrition facts" in it || "informacion nutricional" in it || "informacion nutrimental" in it } }
        if (detected.isEmpty() && size != null && namedTable) detected += LabelBasis.PER_SERVING
        if (detected.isEmpty()) detected += LabelBasis.UNKNOWN
        val columns = detected.associateWith { linkedMapOf<LabelField, LabelValue>() }
        cleaned.forEach { row ->
            val text = normalize(row.joinToString(" "))
            val kind = kind(text) ?: return@forEach
            val salt = text.trimStart().startsWith("sal ") || text.trim() == "sal" || text.startsWith("salt ")
            val values: List<LabelValue?> = if (detected.size > 1 && row.size == detected.size + 1 && tokens(row[0]).isEmpty()) {
                row.drop(1).map { cell -> read(kind, salt, cell, warning) }
            } else {
                val all = tokens(row.joinToString(" ")).filterNot { it.unit == "%" }
                val chosen = if (kind == LabelField.ENERGY_KCAL && all.any { it.unit == "kcal" }) all.filter { it.unit == "kcal" } else all
                if (chosen.size == detected.size) chosen.map { convert(kind, salt, it, warning, text.contains("calori")) }
                else if (detected.size == 1) listOf(read(kind, salt, row.joinToString(" "), warning))
                else { warning += "Una fila no pudo asignarse con seguridad a sus columnas: ${kind.label}."; emptyList() }
            }
            detected.forEachIndexed { index, basis ->
                val value = values.getOrNull(index) ?: return@forEachIndexed
                if (!value.value.isFinite() || value.value < 0.0) { warning += "Se descartó un valor negativo o ilegible."; return@forEachIndexed }
                val grams = kind !in setOf(LabelField.ENERGY_KCAL, LabelField.SODIUM_MG)
                if (basis == LabelBasis.PER_100G && ((grams && value.value > 100.0) || (kind == LabelField.SODIUM_MG && value.value > 100_000.0))) {
                    warning += "Se descartó un valor mayor de 100 g por 100 g."; return@forEachIndexed
                }
                val prior = columns.getValue(basis)[kind]
                if (!salt || prior == null) columns.getValue(basis)[kind] = value
            }
        }
        val fieldCount = columns.values.maxOfOrNull { it.size } ?: 0
        if (fieldCount == 0 || (!namedTable && detected == listOf(LabelBasis.UNKNOWN) && fieldCount < 3)) {
            return LabelParse(reason = "No he encontrado una tabla nutricional en esta foto.")
        }
        val selected = preferred?.takeIf { it in detected } ?: listOf(LabelBasis.PER_100G, LabelBasis.PER_100ML, LabelBasis.PER_SERVING, LabelBasis.UNKNOWN).first { it in detected }
        if (detected.size > 1) warning += "Se leyó más de una columna; revisa la base elegida."
        if (selected == LabelBasis.UNKNOWN) warning += "No se leyó la base de los valores; debes confirmarla antes de guardar."
        val chosen = columns.getValue(selected)
        val kcal = chosen[LabelField.ENERGY_KCAL]?.value
        val protein = chosen[LabelField.PROTEIN_G]?.value
        val carbs = chosen[LabelField.CARBS_G]?.value
        val fat = chosen[LabelField.FAT_G]?.value
        if (kcal != null && protein != null && carbs != null && fat != null) {
            val macroEnergy = 4.0 * protein + 4.0 * carbs + 9.0 * fat
            if (abs(kcal - macroEnergy) > max(kcal, macroEnergy) * 0.25) warning += "Las kcal leídas no cuadran con 4·P + 4·C + 9·G; revisa la etiqueta."
        }
        if (fat != null && (chosen[LabelField.SATURATED_G]?.value ?: 0.0) > fat) warning += "Las grasas saturadas superan las grasas totales."
        if (carbs != null && (chosen[LabelField.SUGAR_G]?.value ?: 0.0) > carbs) warning += "Los azúcares superan los carbohidratos."
        val result = LabelParse(selected, size, servingUnit, chosen, warning.distinct(), columns.mapValues { it.value.toMap() })
        return if (preferred != null && preferred != selected) result.forBasis(preferred) else result
    }
    private fun kind(text: String): LabelField? {
        val value = text.trimStart('-', '•', ' ')
        if ("added sugar" in value || "azucares anadidos" in value) return null
        return when {
            value.startsWith("de las cuales saturad") || value.startsWith("grasas saturad") || value.startsWith("saturated fat") -> LabelField.SATURATED_G
            value.startsWith("de los cuales azucar") || value.startsWith("azucar") || value.startsWith("total sugar") || value.startsWith("sugar") -> LabelField.SUGAR_G
            value.startsWith("valor energetico") || value.startsWith("contenido energetico") || value.startsWith("energia") || value.startsWith("energy") || value.startsWith("calorias") || value.startsWith("calories") -> LabelField.ENERGY_KCAL
            value.startsWith("proteina") || value.startsWith("protein") -> LabelField.PROTEIN_G
            value.startsWith("hidratos de carbono") || value.startsWith("carbohidrato") || value.startsWith("total carbohydrate") || value.startsWith("carbohydrate") -> LabelField.CARBS_G
            value.startsWith("grasas") || value.startsWith("total fat") || value.startsWith("fat ") -> LabelField.FAT_G
            value.startsWith("fibra") || value.startsWith("dietary fiber") || value.startsWith("fiber") -> LabelField.FIBER_G
            value.startsWith("sodio") || value.startsWith("sodium") || value == "sal" || value.startsWith("sal ") || value.startsWith("salt ") -> LabelField.SODIUM_MG
            else -> null
        }
    }
    private fun tokens(text: String): List<Token> {
        if (normalize(text).let { "trazas" in it || "traces" in it }) return listOf(Token(0.0, "g", true, true, text))
        return number.findAll(text.replace('−', '-').replace('\u00A0', ' ').replace('\u202F', ' ')).mapNotNull { found ->
            val raw = found.groupValues[1].trim()
            val bounded = raw.startsWith('<') || raw.startsWith('≤')
            val corrected = raw.trimStart('<', '≤', ' ').filterNot(Char::isWhitespace).replace('O', '0').replace('o', '0').replace(',', '.')
            val value = corrected.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            val unit = found.groupValues[2].lowercase(Locale.ROOT)
            Token(if (bounded && value >= 0.0) 0.0 else value, if (unit == "9") "g" else unit,
                bounded || raw.any { it == 'O' || it == 'o' } || unit == "9", bounded, found.value)
        }.toList()
    }
    private fun read(field: LabelField, salt: Boolean, cell: String, warning: MutableList<String>): LabelValue? {
        val all = tokens(cell).filterNot { it.unit == "%" }
        val value = if (field == LabelField.ENERGY_KCAL) all.firstOrNull { it.unit == "kcal" } ?: all.firstOrNull() else all.firstOrNull()
        return value?.let { convert(field, salt, it, warning, normalize(cell).contains("calori")) }
    }
    private fun convert(field: LabelField, salt: Boolean, token: Token, warning: MutableList<String>, caloriesNamed: Boolean = false): LabelValue? {
        if (token.number < 0.0) { warning += "Se descartó un valor negativo."; return null }
        var confidence = if (token.unsure) LabelConfidence.UNSURE else LabelConfidence.READ
        if (token.unsure) warning += "Hay un valor limitado, trazas o una corrección de OCR; revísalo."
        var value = token.number
        when (field) {
            LabelField.ENERGY_KCAL -> when (token.unit) {
                "kcal" -> Unit
                "kj" -> { value /= 4.184; if (!token.unsure) confidence = LabelConfidence.DERIVED; warning += "La energía solo estaba en kJ y se convirtió a kcal." }
                "" -> if (!caloriesNamed) { warning += "No se leyó la unidad de energía."; return null }
                else -> return null
            }
            LabelField.SODIUM_MG -> {
                value *= when (token.unit) { "g" -> if (salt) 400.0 else 1000.0; "mg" -> if (salt) 0.4 else 1.0; "" -> if (salt) 400.0 else 1.0; else -> return null }
                if (salt) { if (!token.unsure) confidence = LabelConfidence.DERIVED; warning += "Sodio calculado desde sal (sal en g × 400)." }
                if (token.unit.isBlank()) confidence = LabelConfidence.UNSURE
            }
            else -> when (token.unit) { "g" -> Unit; "mg" -> value /= 1000.0; "" -> confidence = LabelConfidence.UNSURE; else -> return null }
        }
        return value.takeIf { it.isFinite() }?.let { LabelValue(it, confidence, token.raw) }
    }
}
