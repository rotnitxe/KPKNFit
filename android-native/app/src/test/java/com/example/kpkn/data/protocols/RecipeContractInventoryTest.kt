package com.example.kpkn.data.protocols

import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.data.protocols.CompositionSeverity
import com.example.kpkn.domain.training.CompositionFinding
import com.example.kpkn.domain.training.RecipeContractPolicy
import com.example.kpkn.domain.training.SessionCompositionPolicy
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Inventario del contrato de receta válida (B.S6, contrato HARD; C10 SOFT).
 *
 * Recorre las recetas que la app publica (protocolos visibles, plantillas con receta y las cuatro
 * autoradas), evalúa [RecipeContractPolicy] sin filtrar por exenciones y escribe
 * `build/reports/recipe-contract.txt` con los totales por regla, la tabla receta x regla, los
 * hallazgos, las exenciones declaradas y las exenciones muertas. El inventario no falla nunca:
 * solo el ratchet de [inventory_size_never_grows_above_the_ceiling] puede romperse, y solo hacia
 * arriba. La validación de composición exige C1–C9 HARD y conserva C10 como aviso.
 */
class RecipeContractInventoryTest {
    companion object {
        /**
         * Techo del inventario (ratchet de solo descenso): hallazgos del contrato sobre las recetas
         * publicadas (29 protocolos, 7 plantillas y 4 autoradas). Medido el 2026-10-03 en la primera
         * corrida de B.S2: 526 = C1 340 + C4 6 + C5 15 + C6 16 + C7 12 + C8 37 + C9 80 + C10 20
         * (C2 y C3 en 0). 2026-10-03, tras la revisión: REALIZATION cuenta como pico en C1 y los 60
         * T3 de 1 serie de powerbuild-16-4 (semanas 13-16) dejan de salir: 466 = C1 280 + C4 6 + C5 15 +
         * C6 16 + C7 12 + C8 37 + C9 80 + C10 20. 2026-10-03, tras B.S3: C6 solo marca las reglas sin
         * consumidor registrado (`ProgressionConsumers.executable`) y baja de 16 a 7 (TopSetPr ×3,
         * WeeklyPercent, RepMaxAutoregulated ×2 y la exigencia de AMRAP de kpkn-rts-style): 457 = C1 280 +
         * C4 6 + C5 15 + C6 7 + C7 12 + C8 37 + C9 80 + C10 20. 2026-10-03, tras B.S4: TopSetPr y
         * RepMaxAutoregulated ya tienen consumidor (propuestas ADJUST_TM de ProgramAutoregulationEngine) y
         * C6 baja de 7 a 2 (WeeklyPercent de madcow-5x5 y la exigencia de AMRAP de kpkn-rts-style): 452 = C1 280 +
         * C4 6 + C5 15 + C6 2 + C7 12 + C8 37 + C9 80 + C10 20. 2026-10-04, tras B.S6 parte 1 (correcciones de
         * datos receta a receta, las configuraciones propias M1 a M5 en lugar de los parches de técnica y las
         * descargas con kind DELOAD): 199 = C1 94 + C4 5 + C5 15 + C6 0 + C7 13 + C8 19 + C9 34 + C10 19
         * (C2 y C3 en 0); el C7 de más frente a los 12 de antes es el taper de kpkn-native-sbd-4 al 85 % del TM de la
         * tabla (bajarlo al 80 % lo quita y el techo sería 198). 2026-10-04, tras B.S6 parte 2b (suelo de 2 series en los
         * accesorios fuera de pico, descargas reales en las plantillas largas, Juggernaut por olas, configuraciones propias en
         * power-16-4 y power-20-5, taper de SBD-4 al 80 %): 112 = C1 46 + C4 5 + C5 8 + C6 0 + C7 12 + C8 23 + C9 0 +
         * C10 18 (C2 y C3 en 0). El crudo cuenta los hallazgos que las exenciones declaradas silencian después (C1 46 = Texas de
         * 3 días 4 + nSuns 16 + Sheiko 13 + Smolov 6 + Smolov Jr 3 + PHAT heredado 4; C5 8 = los métodos que terminan en el test;
         * C8 23 = Texas de 4 días 8 + nSuns 8 + Westside 3 + Texas de 3 días 4); lo que no es exento (C7 y C10) sale en
         * CEILING_AFTER_EXEMPTIONS. Cada corrección de datos posterior los baja; si una corrida da menos, la prueba imprime
         * «techo bajable a N». B.S6 parte 2c retira el espejo C7 (12 hallazgos): la ruta real convierte a %1RM
         * efectivo mediante PercentBasis. Techo 100 = C1 46 + C4 5 + C5 8 + C8 23 + C10 18.
         */
        private const val CEILING = 100

        /**
         * Segundo techo (ratchet de solo descenso): hallazgos del contrato que siguen vivos TRAS las exenciones declaradas. 2026-10-04,
         * tras B.S6 parte 2b: 29 = C7 11 (de los 12 del inventario, la ola de 3s de Juggernaut queda exenta) + C10 18 (semanas
         * idénticas: aviso, nunca HARD). Sin C7 el contrato HARD de la parte 2c ya no tiene nada por corregir en los datos: lo único
         * que queda es convertir los chequeos de la política a %1RM efectivo (C7) y decidir qué hacer con las semanas idénticas.
         * B.S6 parte 2c: la conversión compartida elimina C7 del inventario; el segundo techo es 18 (C10, siempre SOFT).
         */
        private const val CEILING_AFTER_EXEMPTIONS = 18

        /** Una justificación de exención debe tener al menos este largo (B.S6). */
        private const val MIN_JUSTIFICATION_LENGTH = 25

        /** Reglas de seguridad de intensidad: ninguna exención las silencia. */
        private val NON_EXEMPTABLE_RULES = setOf("H11", "H11b")

        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val metadata get() = CatalogCompositionTestSupport.metadata

    private data class Published(
        val kind: String,
        val recipe: TrainingPlanRecipe,
        /** Exenciones que el protocolo declara además de las de la receta. */
        val protocolExemptions: List<RecipeCompositionExemption>,
    ) {
        val label: String get() = "$kind ${recipe.id}"

        val declared: List<RecipeCompositionExemption>
            get() = (recipe.exemptions + protocolExemptions).distinctBy { it.rule to it.scope }
    }

    private data class Row(
        val entry: Published,
        /** Hallazgos del contrato sin filtrar por exenciones. */
        val contract: List<CompositionFinding>,
        /** Hallazgos del contrato que siguen vivos tras las exenciones declaradas. */
        val remaining: List<CompositionFinding>,
        /** Exenciones declaradas que no silencian nada hoy (ni HARD ni SOFT). */
        val deadExemptions: List<RecipeCompositionExemption>,
    )

    /** Las recetas que la app publica: protocolos visibles, plantillas con receta y las autoradas. */
    private fun published(): List<Published> {
        val protocols = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }
            .map { Published("protocolo", requireNotNull(it.recipe) { "${it.id} sin receta" }, it.exemptions) }
        val templates = PROGRAM_TEMPLATES.mapNotNull { template ->
            template.recipe?.let { Published("plantilla", it, emptyList()) }
        }
        val authored = AuthoredPhulPhatRecipes.all.map { Published("autorada", it, emptyList()) }
        return protocols + templates + authored
    }

    private fun inventory(): List<Row> = published().map { entry ->
        val raw = SessionCompositionPolicy.evaluateRecipeRaw(entry.recipe, metadata)
        Row(
            entry = entry,
            contract = RecipeContractPolicy.evaluate(entry.recipe, metadata),
            remaining = SessionCompositionPolicy.evaluateRecipe(entry.recipe, metadata, entry.protocolExemptions)
                .filter { it.rule in RecipeContractPolicy.RULES },
            deadExemptions = entry.declared.filter { exemption ->
                exemption.rule in NON_EXEMPTABLE_RULES ||
                    raw.none { it.rule == exemption.rule && SessionCompositionPolicy.scopeMatches(exemption.scope, it.scope) }
            },
        )
    }

    private fun reportFile(): File {
        val base = File(System.getProperty("user.dir"))
        val buildDir = listOf(File(base, "build"), File(base, "app/build"), File(base, "android-native/app/build"))
            .firstOrNull { it.isDirectory } ?: File(base, "build")
        return File(buildDir, "reports/recipe-contract.txt")
    }

    private fun scopeRange(findings: List<CompositionFinding>): String {
        val first = findings.first().scope
        val last = findings.last().scope
        return if (first == last) first else "$first … $last"
    }

    private fun buildReport(rows: List<Row>): String {
        val rules = RecipeContractPolicy.RULES
        val total = rows.sumOf { it.contract.size }
        val remainingTotal = rows.sumOf { it.remaining.size }
        return buildString {
            appendLine("=== Inventario del contrato de receta válida (B.S6 HARD; C10 SOFT) ===")
            appendLine(
                "Recetas evaluadas: ${rows.size} (protocolos ${rows.count { it.entry.kind == "protocolo" }}, " +
                    "plantillas ${rows.count { it.entry.kind == "plantilla" }}, " +
                    "autoradas ${rows.count { it.entry.kind == "autorada" }})",
            )
            appendLine("Hallazgos del contrato: $total · tras las exenciones declaradas: $remainingTotal")
            appendLine()

            appendLine("-- Totales por regla --")
            rules.forEach { rule ->
                val count = rows.sumOf { row -> row.contract.count { it.rule == rule } }
                val recipes = rows.count { row -> row.contract.any { it.rule == rule } }
                appendLine("${rule.padEnd(26)} ${count.toString().padStart(6)} hallazgos · $recipes recetas")
            }
            appendLine()

            appendLine("-- Tabla receta x regla (hallazgos por regla, total y tras exenciones) --")
            appendLine("receta".padEnd(46) + rules.joinToString("") { it.substringBefore('_').padStart(6) } + "  total  tras-ex")
            rows.forEach { row ->
                val counts = RecipeContractPolicy.summarize(row.contract)
                appendLine(
                    row.entry.label.padEnd(46) +
                        rules.joinToString("") { counts.getValue(it).toString().padStart(6) } +
                        row.contract.size.toString().padStart(7) +
                        row.remaining.size.toString().padStart(9),
                )
            }
            appendLine()

            appendLine("-- Hallazgos agrupados (regla | receta | veces | primer y último ámbito | mensaje) --")
            rows.forEach { row ->
                row.contract.groupBy { it.rule to it.message }.forEach { (key, group) ->
                    appendLine("${key.first} | ${row.entry.label} | x${group.size} | ${scopeRange(group)} | ${key.second}")
                }
            }
            appendLine()

            appendLine("-- C7: receta · ámbito · chequeo, % crudo frente a %1RM efectivo --")
            rows.forEach { row ->
                row.contract.filter { it.rule == RecipeContractPolicy.C7_PERCENT_BASIS }
                    .groupBy { it.message }
                    .forEach { (message, group) ->
                        appendLine("${row.entry.label} · ${scopeRange(group)} (x${group.size}) · $message")
                    }
            }
            appendLine()

            appendLine("-- Exenciones declaradas (receta · regla · ámbito · largo de la justificación) --")
            var declaredCount = 0
            rows.forEach { row ->
                row.entry.declared.forEach { exemption ->
                    declaredCount += 1
                    val length = exemption.justification.trim().length
                    val short = if (length < MIN_JUSTIFICATION_LENGTH) " · CORTA (< $MIN_JUSTIFICATION_LENGTH)" else ""
                    appendLine("${row.entry.label} · ${exemption.rule} · '${exemption.scope}' · $length caracteres$short · \"${exemption.justification}\"")
                }
            }
            appendLine("Total de exenciones declaradas: $declaredCount")
            appendLine()

            appendLine("-- Exenciones muertas (no silencian ningún hallazgo HARD ni SOFT hoy) --")
            var deadCount = 0
            rows.forEach { row ->
                row.deadExemptions.forEach { exemption ->
                    deadCount += 1
                    appendLine("${row.entry.label} · ${exemption.rule} · '${exemption.scope}'")
                }
            }
            appendLine("Total de exenciones muertas: $deadCount")
            appendLine()

            appendLine("-- Hallazgos completos (receta · regla · ámbito · mensaje) --")
            rows.forEach { row ->
                row.contract.forEach { finding ->
                    appendLine("${row.entry.label} · ${finding.rule} · ${finding.scope} · ${finding.message}")
                }
            }
        }
    }

    @Test
    fun inventory_is_written_to_the_build_reports_directory() {
        val rows = inventory()
        val report = buildReport(rows)
        val file = reportFile()
        file.parentFile?.mkdirs()
        file.writeText(report)

        val total = rows.sumOf { it.contract.size }
        println("INVENTARIO C1-C10: ${rows.size} recetas, $total hallazgos, ${rows.sumOf { it.remaining.size }} tras exenciones")
        RecipeContractPolicy.RULES.forEach { rule ->
            println("INVENTARIO ${rule.padEnd(26)} ${rows.sumOf { row -> row.contract.count { it.rule == rule } }}")
        }
        println("INVENTARIO informe: ${file.absolutePath}")
        assertTrue("el informe debe quedar escrito en ${file.absolutePath}", file.isFile && file.length() > 0)
    }

    /** «C1 46 · C4 5 · …» con las reglas del contrato que tienen algún hallazgo. */
    private fun breakdown(findings: List<CompositionFinding>): String =
        RecipeContractPolicy.RULES.mapNotNull { rule ->
            val count = findings.count { it.rule == rule }
            if (count == 0) null else "${rule.substringBefore('_')} $count"
        }.joinToString(" · ").ifEmpty { "sin hallazgos" }

    @Test
    fun inventory_size_never_grows_above_the_ceiling() {
        val rows = inventory()
        val total = rows.sumOf { it.contract.size }
        println("[B.S6 2b] crudo: $total (${breakdown(rows.flatMap { it.contract })})")
        if (total < CEILING) println("[B.S2] techo bajable a $total (techo actual=$CEILING)")
        assertTrue(
            "El inventario del contrato creció: $total hallazgos superan el techo $CEILING. " +
                "Corrige el dato o, si es deliberado, declara una exención justificada (B.S6).",
            total <= CEILING,
        )
    }

    @Test
    fun inventory_after_the_declared_exemptions_never_grows_above_the_second_ceiling() {
        val rows = inventory()
        val remaining = rows.flatMap { it.remaining }
        println("[B.S6 2b] tras exenciones: ${remaining.size} (${breakdown(remaining)})")
        rows.filter { it.remaining.isNotEmpty() }.forEach { row ->
            println("[B.S6 2b]   ${row.entry.label}: ${breakdown(row.remaining)}")
        }
        if (remaining.size < CEILING_AFTER_EXEMPTIONS) {
            println("[B.S6 2b] segundo techo bajable a ${remaining.size} (techo actual=$CEILING_AFTER_EXEMPTIONS)")
        }
        assertTrue(
            "Los hallazgos del contrato tras las exenciones crecieron: ${remaining.size} superan el segundo techo $CEILING_AFTER_EXEMPTIONS " +
                "(${breakdown(remaining)}). Corrige el dato o declara una exención justificada y con fuente.",
            remaining.size <= CEILING_AFTER_EXEMPTIONS,
        )
    }

    @Test
    fun after_the_exemptions_only_the_advisory_c10_remains_in_the_published_recipes() {
        // B.S6 parte 2c: C1–C9 están corregidas o tienen una exención declarada; C7 se cumple mediante la conversión compartida.
        // Solo C10 (semanas idénticas, aviso permanente) puede seguir vivo, siempre con severidad SOFT.
        val pending = setOf(RecipeContractPolicy.C10_IDENTICAL_WEEKS)
        val offenders = inventory().flatMap { row ->
            row.remaining.filter { it.rule !in pending || it.severity != CompositionSeverity.SOFT }.map { "${row.entry.label} · ${it.rule} · ${it.scope} · ${it.message}" }
        }
        assertTrue("Hallazgos del contrato sin cubrir (solo C10 puede quedar):\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun all_published_recipe_contracts_are_hard_except_the_advisory_c10() {
        val offenders = published().flatMap { entry ->
            SessionCompositionPolicy.evaluateRecipe(entry.recipe, metadata, entry.protocolExemptions)
                .filter { it.rule in RecipeContractPolicy.RULES && it.severity == CompositionSeverity.HARD }
                .map { "${entry.label} · ${it.rule} · ${it.scope} · ${it.message}" }
        }
        assertTrue("C1–C9 HARD sin cubrir:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun no_declared_exemption_of_the_contract_is_dead() {
        // Una exención que no silencia ningún hallazgo (un ámbito mal escrito, un dato ya corregido) se retira: B.S6 parte 1 quitó 26.
        val dead = inventory().flatMap { row ->
            row.deadExemptions.map { "${row.entry.label} · ${it.rule} · '${it.scope}'" }
        }
        assertTrue("Exenciones muertas (no silencian ningún hallazgo):\n" + dead.joinToString("\n"), dead.isEmpty())
    }
}
