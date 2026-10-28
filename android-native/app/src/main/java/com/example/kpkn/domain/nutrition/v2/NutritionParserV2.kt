package com.example.kpkn.domain.nutrition.v2

/**
 * Parser v2 completo (F2): normaliza con desplazamientos, etiqueta con el léxico del catálogo, arma las menciones con la
 * gramática y las resuelve a ficha, gramos y preguntas. Determinista y sin estado; el léxico se compila una vez.
 * Contrato en `docs/nutrition-v2/F2_PARSER_V2.md`.
 */
class NutritionParserV2(val catalog: ConceptCatalogV2) {
    private val lexicon = buildLexiconV2(catalog)
    private val resolver = ResolverV2(catalog)

    fun parse(text: String): MealParseV2 {
        val tokens = NormalizerV2.tokenize(text)
        val drafts = GrammarV2.parse(tokens, lexicon.tag(tokens), catalog)
        return MealParseV2(text, resolver.resolve(text, drafts))
    }
}
