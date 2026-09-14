package com.example.kpkn.domain.nutrition

/**
 * Último nivel de seguridad del pipeline de análisis por descripción (CRI-ANALYSIS).
 *
 * Cuando el análisis automático completo (parser + dataset + resolver) falla, se usa este
 * splitter puro para producir fragmentos de comida que el usuario pueda revisar y guardar.
 * No depende de Room, dataset ni de Android: es puro dominio y por eso es testeable en JVM.
 */
object LastResortSplitter {
    private val SPLIT = Regex("""[;\n]|(?<!\d),|,(?!\d)|\s\+|\s+(?:y|e|con|sino)\s+""", RegexOption.IGNORE_CASE)
    private val NEGATION = Regex("""\b(?:sin|no|ni)\s+""", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE = Regex("""^(?:az[uú]car(?:es)?|lactosa|gluten|piel(?:es)?|grasa(?:s)?|miga)\b""", RegexOption.IGNORE_CASE)

    /**
     * Separa la descripción por conectores de lista (",", ";", "+", " con ", " y ", " e ").
     * Si no hay separadores, devuelve el texto completo como un único fragmento (seguro).
     * Fragmentos de menos de 2 caracteres se descartan. Nunca lanza.
     */
    fun split(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val parts = raw.trim().replace(
            Regex(""",[ \t]*(?=(?:sin|ni)[ \t]+)""", RegexOption.IGNORE_CASE), " ",
        ).split(SPLIT)
            .map { it.trim().removePrefix("e ").trim() }
            .filter { it.length >= 2 }
            .flatMap { fragment ->
                // This safety fallback does not guess food identities or weights.
                // Preserve product attributes, but never turn a plainly excluded
                // ingredient into a positive food when the full parser failed.
                val negation = NEGATION.findAll(fragment).firstOrNull { match ->
                    match.range.first == 0 || !match.value.trim().equals("sin", true) ||
                        !ATTRIBUTE.containsMatchIn(fragment.substring(match.range.last + 1).trim())
                }
                if (negation == null) listOf(fragment) else {
                    val before = fragment.substring(0, negation.range.first).trim()
                    val after = fragment.substring(negation.range.last + 1).trim()
                    listOfNotNull(before.takeIf { it.isNotBlank() }) +
                        after.split(Regex("""\s+ni\s+""", RegexOption.IGNORE_CASE))
                            .filter { it.isNotBlank() }.map { "sin $it" }
                }
            }
        return parts.ifEmpty { listOf(raw.trim()) }
    }
}
