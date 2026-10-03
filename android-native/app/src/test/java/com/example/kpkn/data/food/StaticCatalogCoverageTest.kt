package com.example.kpkn.data.food

import com.example.kpkn.domain.nutrition.TextKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-D1: the coverage probe of the food-system audit (docs/audits/2026-10-food-system/coverage-probe.json) as a test. The 174
 * everyday Chilean terms came from a list of the foods people actually write; the audit found 56 of them without a card. A term HAS
 * a card when the app can answer it with a food: `staticFoodForAlias(term) ?: findFoodByNormalized(term)` (the static catalog, by
 * declared alias, name or search alias) or a row of the two curated branded catalogs that carries the term as a whole phrase.
 *
 * Fails when a term that has a card today loses it, or when coverage drops below 150/174. The terms that stay without a card are
 * printed: brand names with no row to quote ("negrita", the "pre entreno" supplements), words that are no food of their own
 * ("pap") or that the app refuses to answer on purpose ("tallarines" is dry or cooked pasta: AMBIGUOUS_STATE_ALIASES asks first)
 * and "mate" (the infusion; its phonetic neighbour "mote" is a different food, so it waits for PhoneticEs to stop folding vowels).
 */
class StaticCatalogCoverageTest {

    /** The 174 unique terms of coverage-probe.json, in the order of the file. */
    private val terms: List<String> = listOf(
        "quesillo", "jurel", "reineta", "kuchen", "alfajor", "galleta", "cerveza", "vino", "pisco", "margarina", "gauda", "gouda",
        "salame", "ceviche", "arepa", "pan pita", "tostada", "cereal", "granola", "nuez", "chocolate", "helado", "azúcar",
        "leche condensada", "queso crema", "chuleta", "porotos verdes", "habas", "champiñón", "mandarina", "limón", "frambuesa",
        "cereza", "chirimoya", "membrillo", "cuscús", "coca", "bebida", "jugo", "agua", "té", "café", "mate", "sushi", "pizza",
        "hamburguesa", "tallarines", "fideos", "lasaña", "ravioles", "ñoquis", "sopa", "crema de", "queso fresco", "ricotta",
        "mantequilla", "aceite de oliva", "mayonesa", "ketchup", "mostaza", "salsa de tomate", "ají", "merkén", "palta", "plátano",
        "manzana", "pera", "uva", "sandía", "melón", "durazno", "ciruela", "kiwi", "frutilla", "arándano", "naranja", "piña",
        "mango", "papaya", "yogur", "leche descremada", "leche entera", "leche sin lactosa", "queso gauda", "queso mantecoso",
        "jamón", "pavo", "atún", "salmón", "merluza", "pollo", "pechuga", "trutro", "carne molida", "lomo", "posta", "costillar",
        "cerdo", "vienesa", "huevo", "arroz", "quinoa", "lentejas", "garbanzos", "porotos", "papa", "camote", "choclo", "zapallo",
        "zanahoria", "tomate", "cebolla", "lechuga", "repollo", "brócoli", "espinaca", "acelga", "pepino", "pimentón", "betarraga",
        "apio", "avena", "pan integral", "pan de molde", "pan blanco", "tortilla", "wrap", "sopaipilla", "empanada", "completo",
        "churrasco", "chacarero", "barros luna", "ave mayo", "pastel de papa", "porotos con riendas", "carbonada", "ajiaco",
        "humitas", "pastel de choclo", "cazuela", "charquicán", "chorrillana", "mote con huesillo", "manjar", "berlín",
        "chilenito", "cuchuflí", "super 8", "negrita", "sahne nuss", "ramitas", "papas fritas", "nuggets", "proteína", "whey",
        "creatina", "barra de proteína", "pre entreno", "red bull", "monster", "score", "gatorade", "powerade", "cachantún", "pap",
        "fanta", "sprite", "vino tinto", "piscola", "ron", "vodka", "whisky", "espumante",
    )

    /** Terms without a card today. The set may only shrink: a term that gains one needs no edit, one that loses it fails. */
    private val withoutCard: Set<String> = setOf("mate", "tallarines", "negrita", "pre entreno", "pap")

    private val branded by lazy {
        (BrandedSnackCatalog.buildProgrammaticCatalog() + BrandedEnergyKcalCatalog.buildProgrammaticCatalog()).map { food ->
            (listOf(food.name, food.brand.orEmpty()) + food.searchAliases).map { " " + TextKeys.normalize(it) + " " }
        }
    }

    private fun hasStaticCard(term: String): Boolean = (staticFoodForAlias(term) ?: findFoodByNormalized(term)) != null

    private fun hasBrandedCard(term: String): Boolean {
        val phrase = " " + TextKeys.normalize(term) + " "
        return branded.any { keys -> keys.any { phrase in it } }
    }

    private fun hasCard(term: String): Boolean = hasStaticCard(term) || hasBrandedCard(term)

    @Test
    fun `the probe holds 174 distinct terms`() {
        assertEquals(174, terms.size)
        assertEquals(174, terms.map { TextKeys.normalize(it) }.toSet().size)
    }

    @Test
    fun `at least 150 of the 174 terms have a card and none that had one loses it`() {
        val uncovered = terms.filterNot(::hasCard)
        println("STATIC CATALOG COVERAGE: ${terms.size - uncovered.size}/${terms.size}; terms without a card: $uncovered")
        val lost = uncovered.filter { it !in withoutCard }
        assertTrue("terms that lost their card: $lost", lost.isEmpty())
        assertTrue("coverage ${terms.size - uncovered.size}/${terms.size} is below 150", terms.size - uncovered.size >= 150)
    }

    @Test
    fun `the terms of the withoutCard list are real probe terms`() {
        withoutCard.forEach { assertTrue("$it is no term of the probe", it in terms) }
    }

    @Test
    fun `terms that only matched inside another word now have a card of their own`() {
        // The 2026-10 grep counted these as covered by "Aguacate", "Tomate", "Cocada", a category and a Nicaraguan cheese.
        mapOf(
            "agua" to "gen143", "coca" to "gen146", "bebida" to "gen146", "ron" to "gen197", "cereal" to "gen179",
            "helado" to "gen193", "tostada" to "gen178", "quesillo" to "gen158",
        ).forEach { (term, id) -> assertEquals(term, id, staticFoodForAlias(term)?.id) }
    }

    @Test
    fun `every other term of the list that the audit found without a card resolves to a static row`() {
        // Not brand names: these are foods, and a food of the everyday list must not depend on a branded catalog.
        listOf(
            "jurel", "reineta", "kuchen", "cerveza", "vino", "margarina", "gauda", "gouda", "salame", "ceviche", "arepa", "pan pita",
            "nuez", "azúcar", "leche condensada", "queso crema", "chuleta", "porotos verdes", "habas", "champiñón", "mandarina", "limón",
            "frambuesa", "cereza", "chirimoya", "membrillo", "cuscús", "lasaña", "ravioles", "ñoquis", "ají", "merkén", "papaya",
            "leche sin lactosa", "queso gauda", "chacarero", "barros luna", "ave mayo", "carbonada", "ajiaco", "chilenito", "cuchuflí",
            "nuggets", "barra de proteína", "gatorade", "powerade", "cachantún", "fanta", "sprite", "vino tinto", "piscola", "vodka",
            "whisky", "espumante",
        ).forEach { term -> assertTrue("$term has no static card", hasStaticCard(term)) }
    }
}
