package com.example.kpkn.data.protocols

import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.CompositionFinding
import com.example.kpkn.domain.training.SessionCompositionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Migración de los ámbitos de exención de `contains`/`startsWith` a glob anclado
 * (B.S1). Congela la tabla ANTIGUA y comprueba que cada exención migrada silencia
 * exactamente lo mismo que silenciaba: ni pierde un hallazgo ni amplía el alcance.
 *
 * Además deja como contrato permanente que ninguna receta publicada (protocolos
 * visibles, plantillas y las cuatro autoradas) incumple H11 ni H11b, que no se
 * pueden exentar.
 *
 * B.S6 parte 1: la tabla se ajusta a las exenciones que quedan. Se retiraron las 26
 * exenciones muertas (las que no silenciaban ningún hallazgo) y las dos exenciones nuevas
 * —el rango C1 por slot de nSuns y la C4 de las semanas 9 a 13 de Smolov— se anotan con su
 * equivalente en la semántica antigua (`contains` / `==`). `scopeUniverse` conoce ahora los
 * ámbitos de las reglas C (por slot, por semana, por día, `recipe`), para que la equivalencia
 * estructural de un ámbito de slot o de semana no sea vacía.
 */
class ExemptionScopeMigrationTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val metadata get() = CatalogCompositionTestSupport.metadata

    private data class Published(
        val label: String,
        val recipe: TrainingPlanRecipe,
        /** Exenciones que el protocolo declara además de las de la receta. */
        val protocolExemptions: List<RecipeCompositionExemption>,
    ) {
        val declared: List<RecipeCompositionExemption> get() = recipe.exemptions + protocolExemptions
    }

    /** Las recetas que la app publica: protocolos visibles, plantillas con receta y las autoradas. */
    private fun published(): List<Published> {
        val protocols = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }
            .map { Published("protocolo ${it.id}", requireNotNull(it.recipe) { "${it.id} sin receta" }, it.exemptions) }
        val templates = PROGRAM_TEMPLATES.mapNotNull { template ->
            template.recipe?.let { Published("plantilla ${template.id}", it, emptyList()) }
        }
        val authored = AuthoredPhulPhatRecipes.all.map { Published("autorada ${it.id}", it, emptyList()) }
        return protocols + templates + authored
    }

    // ─── Tabla ANTIGUA congelada: recetaId → (regla, ámbito viejo) ─────────────────

    /**
     * Smolov: las H6 vivas (S1, S2, S3 y Test), la C4 de las semanas 9 a 13 (`==` sobre el ámbito de semana, nueva en B.S6 parte 1) y, nuevas
     * en la parte 2b, la C1 por slot del día S4 de cualquier semana (`contains`) y del segundo día de la semana 9 (`==`).
     */
    private val smolovLegacy = listOf(
        "H6" to "S1", "H6" to "S2", "H6" to "S3", "H6" to "Test",
        "C4_CLAIMED_DAYS" to "w9", "C4_CLAIMED_DAYS" to "w10", "C4_CLAIMED_DAYS" to "w11",
        "C4_CLAIMED_DAYS" to "w12", "C4_CLAIMED_DAYS" to "w13",
        "C1_SET_RANGE" to "S4/sq", "C1_SET_RANGE" to "w9/S2/sq",
    )

    private val phulAuthoredLegacy = listOf(
        "H1" to "Superior hipertrofia",
        "H3" to "Inferior hipertrofia",
    )

    private val phatAuthoredLegacy = listOf(
        "H6" to "Pecho/brazos hipertrofia",
        "H1" to "Superior fuerza",
        "H1" to "Inferior fuerza",
        "H1" to "Inferior hipertrofia",
        "H3" to "Inferior fuerza",
        "H3" to "Espalda/hombros hipertrofia",
        "H3" to "Inferior hipertrofia",
        "H3" to "Pecho/brazos hipertrofia",
    )

    /**
     * Exenciones de B.S6 parte 2b cuyo ámbito antiguo (`contains`) casaría con MÁS ámbitos que el glob anclado nuevo: son `recipeId →
     * «regla|ámbito nuevo»`. Solo la C1 de la sentadilla de Sheiko: el id de slot `a` es prefijo de `a2` y el `contains` antiguo
     * silenciaría también el segundo bloque de sentadilla (ningún hallazgo C1 lo toca: tiene 2 a 5 series). Para ellas no se exige la
     * igualdad de conjuntos, sino que el ámbito nuevo sea un subconjunto no vacío del antiguo.
     */
    private val narrowerThanLegacy = setOf(
        "sheiko-29-32" to "C1_SET_RANGE|w*/Sentadilla/Banca/a",
    )

    private val legacyScopes: Map<String, List<Pair<String, String>>> = mapOf(
        // B.S6: el T1 de 9 series de cada día, por slot (`contains` sobre «día/slot»); H5b y H6 `*` estaban muertas. Parte 2b (H-09): el T2 de
        // peso muerto sumo (sentadilla) y el de sentadilla frontal (peso muerto) cuelgan de su T1 de otro patrón: C8 por slot.
        "nsuns-531-lp-4d" to listOf(
            "C1_SET_RANGE" to "Banca/OHP/t1",
            "C1_SET_RANGE" to "Sentadilla/Sumo/t1",
            "C1_SET_RANGE" to "Banca/Cerrado/t1",
            "C1_SET_RANGE" to "Peso muerto/Frontal/t1",
            "C8_SUPPLEMENTAL_LINK" to "Sentadilla/Sumo/t2",
            "C8_SUPPLEMENTAL_LINK" to "Peso muerto/Frontal/t2",
        ),
        "smolov" to smolovLegacy,
        "smolov-jr" to listOf("C1_SET_RANGE" to "S4/sq"),
        "coan-phillipi-dl" to listOf("H2" to "Peso muerto", "C5_DELOAD_REQUIRED" to "recipe"),
        "korte-3x3" to listOf("H5b" to "*", "C5_DELOAD_REQUIRED" to "recipe"),
        "phat-verified" to listOf("H3" to "*", "C1_SET_RANGE" to "Power Lower/ext"),
        // B.S6 parte 2b: Texas de 3 días (peso muerto de una serie que cuelga de la sentadilla), Texas de 4 días (los dos volúmenes que cuelgan
        // del top de otro patrón), Westside (los tirones rápidos), Juggernaut (la ola de 3s, `==` sobre el ámbito de bloque), Sheiko (series de
        // más por slot y C5) y los métodos que terminan en el test o la competición (C5, `==` sobre «recipe»).
        "texas-method-3d" to listOf("C1_SET_RANGE" to "Volumen 5x5/dl", "C8_SUPPLEMENTAL_LINK" to "Volumen 5x5/dl"),
        "texas-method-4d" to listOf("C8_SUPPLEMENTAL_LINK" to "Sentadilla/PM/t2", "C8_SUPPLEMENTAL_LINK" to "PM/Sentadilla/t2"),
        "westside-conjugate" to listOf("C8_SUPPLEMENTAL_LINK" to "DE Lower/sdl"),
        "juggernaut-2" to listOf("BLOCK" to "block3/3s"),
        "sheiko-29-32" to listOf(
            "C1_SET_RANGE" to "Sentadilla/Banca/b",
            "C1_SET_RANGE" to "Sentadilla/Banca/a",
            "C1_SET_RANGE" to "Peso muerto/Banca/a",
            "C5_DELOAD_REQUIRED" to "recipe",
        ),
        "gzcl-jt-2" to listOf("C5_DELOAD_REQUIRED" to "recipe"),
        "gzcl-rippler" to listOf("C5_DELOAD_REQUIRED" to "recipe"),
        "gzcl-uhf-9" to listOf("C5_DELOAD_REQUIRED" to "recipe"),
        "cube-method" to listOf("C5_DELOAD_REQUIRED" to "recipe"),
        "calgary-16" to listOf("C5_DELOAD_REQUIRED" to "recipe"),
        AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID to phulAuthoredLegacy,
        AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID to phulAuthoredLegacy,
        AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID to phatAuthoredLegacy,
        AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID to phatAuthoredLegacy,
    )

    /** Semántica ANTIGUA de un ámbito: `*`, `==`, `startsWith` o `contains`. */
    private fun legacyMatches(scope: String, findingScope: String): Boolean =
        scope == "*" || scope == findingScope || findingScope.startsWith(scope) || findingScope.contains(scope)

    /** `applyExemptions` ANTIGUO: H11 pasaba siempre y el resto se filtraba con [legacyMatches]. */
    private fun legacyFilter(
        findings: List<CompositionFinding>,
        exemptions: List<Pair<String, String>>,
    ): List<CompositionFinding> = findings.filter { finding ->
        if (finding.rule == "H11") return@filter true
        exemptions.none { (rule, scope) -> rule == finding.rule && legacyMatches(scope, finding.scope) }
    }

    /** Los ámbitos que la política puede emitir para esa regla, según la forma de la receta. */
    private fun scopeUniverse(recipe: TrainingPlanRecipe, rule: String): List<String> {
        val weekScopes = recipe.weeks.map { "w${it.weekNumber}" }
        val dayScopes = recipe.weeks.flatMap { week -> week.days.map { day -> "w${week.weekNumber}/${day.label}" } }
        val slotScopes = recipe.weeks.flatMap { week ->
            week.days.flatMap { day -> day.slots.map { slot -> "w${week.weekNumber}/${day.label}/${slot.id}" } }
        }
        val blockScopes = recipe.weeks.groupBy { it.blockIndex }.toSortedMap()
            .map { (index, weeks) -> "block$index/${weeks.first().blockName}" }
        return when {
            rule == "W5" -> blockScopes
            rule == "BLOCK" -> blockScopes + weekScopes + "native"
            // Contrato de receta válida (B.S2): C1, C3, C8 y C9 por slot; C2 por día; C4 y C10 por semana; C5 y C6 `recipe`;
            // C7 por día, semana o bloque (los ámbitos que emite `RecipeContractPolicy`).
            rule in setOf("C1_SET_RANGE", "C3_EMPTY_SET", "C8_SUPPLEMENTAL_LINK", "C9_REDUNDANT_TECHNIQUE") -> slotScopes
            rule in setOf("C4_CLAIMED_DAYS", "C10_IDENTICAL_WEEKS") -> weekScopes
            rule in setOf("C5_DELOAD_REQUIRED", "C6_PROGRESSION_CONSUMER") -> listOf("recipe")
            rule.startsWith("W") || rule in setOf("S4", "S5", "S6") -> weekScopes
            else -> dayScopes
        }.distinct()
    }

    private fun List<CompositionFinding>.asMultiset(): Map<CompositionFinding, Int> =
        groupingBy { it }.eachCount()

    @Test
    fun every_recipe_that_declares_exemptions_is_frozen_in_the_legacy_table() {
        val withExemptions = published().filter { it.declared.isNotEmpty() }.map { it.recipe.id }.toSet()
        assertEquals(
            "Recetas con exenciones que la tabla antigua no congela (o al revés)",
            legacyScopes.keys,
            withExemptions,
        )
    }

    @Test
    fun migrated_exemption_scopes_silence_exactly_what_the_legacy_scopes_silenced() {
        val failures = mutableListOf<String>()
        val report = StringBuilder()
        published().filter { it.declared.isNotEmpty() }.forEach { entry ->
            val recipe = entry.recipe
            val legacy = legacyScopes.getValue(recipe.id)
            // Las listas de receta y de protocolo repiten las mismas exenciones: se comparan sin duplicados.
            val current = entry.declared.distinctBy { it.rule to it.scope }
            if (current.map { it.rule } != legacy.map { it.first }) {
                failures += "${entry.label}: las reglas exentadas ${current.map { it.rule }} no coinciden con la tabla antigua ${legacy.map { it.first }}"
                return@forEach
            }
            val raw = SessionCompositionPolicy.evaluateRecipeRaw(recipe, metadata)
            val legacyKept = legacyFilter(raw, legacy)
            val currentKept = SessionCompositionPolicy.applyExemptions(raw, entry.declared)
            if (legacyKept.asMultiset() != currentKept.asMultiset()) {
                // «Quedan» = hallazgos que siguen visibles tras filtrar con cada semántica.
                val silencedOnlyNow = legacyKept.asMultiset().keys - currentKept.asMultiset().keys
                val silencedOnlyBefore = currentKept.asMultiset().keys - legacyKept.asMultiset().keys
                failures += "${entry.label}: el filtrado cambia. Dejan de silenciarse: ${silencedOnlyBefore.map { "${it.rule} ${it.scope}" }}. " +
                    "Se silencian de más: ${silencedOnlyNow.map { "${it.rule} ${it.scope}" }}"
            }
            // Equivalencia estructural: cada ámbito nuevo casa con los mismos ámbitos posibles que el viejo.
            legacy.forEachIndexed { index, (rule, oldScope) ->
                val newScope = current[index].scope
                val universe = scopeUniverse(recipe, rule)
                val oldMatches = universe.filter { legacyMatches(oldScope, it) }
                val newMatches = universe.filter { SessionCompositionPolicy.scopeMatches(newScope, it) }
                if ((recipe.id to "$rule|$newScope") in narrowerThanLegacy) {
                    // El ámbito antiguo era más ancho a propósito: el nuevo debe casar con algo y solo con ámbitos que el antiguo también casaba.
                    if (newMatches.isEmpty() || !oldMatches.containsAll(newMatches)) {
                        failures += "${entry.label}: $rule '$oldScope' → '$newScope' debe ser un subconjunto no vacío: casa con $newMatches y casaba con $oldMatches"
                    }
                } else if (oldMatches != newMatches) {
                    failures += "${entry.label}: $rule '$oldScope' → '$newScope' casa con $newMatches y casaba con $oldMatches"
                }
                val covered = raw.count { it.rule == rule && SessionCompositionPolicy.scopeMatches(newScope, it.scope) }
                report.appendLine("MIGRACION ${recipe.id} $rule '$oldScope' -> '$newScope' (cubre $covered hallazgos en bruto)")
            }
        }
        println(report)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun every_published_recipe_is_free_of_h11_and_h11b() {
        val inventory = mutableListOf<String>()
        published().forEach { entry ->
            SessionCompositionPolicy.evaluateRecipeRaw(entry.recipe, metadata)
                .filter { it.rule == "H11" || it.rule == "H11b" }
                .forEach { inventory += "${it.rule} | ${entry.label} | ${it.scope} | ${it.message}" }
        }
        // Inventario completo para el informe (también viaja en el mensaje del fallo).
        println("INVENTARIO H11/H11b (${inventory.size} hallazgos en ${published().size} recetas)")
        inventory.forEach { println("INVENTARIO $it") }
        assertTrue(
            "H11/H11b en recetas publicadas (no son exentables, se corrige el dato):\n" + inventory.joinToString("\n"),
            inventory.isEmpty(),
        )
    }
}
