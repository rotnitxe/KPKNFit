package com.example.kpkn.data.programs

import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftRef
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.data.protocols.definitions.NativeWeekBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C.P1 + C.P2 · Contrato editorial del catálogo (JVM puro, sin Robolectric).
 *
 * Fija sobre `PersonalizedPlanCatalog.entries()` y `PlanEditorialTable.byId` las
 * reglas del diseño editorial: correspondencia exacta entre tabla y catálogo,
 * títulos y resúmenes en español llano, niveles coherentes con las recetas,
 * días citados que coinciden con los días reales, orden editorial, duraciones,
 * glosario y atribución. Un texto nuevo que rompa una regla falla aquí.
 */
class PlanCatalogEditorialContractTest {

    private val entries: List<CatalogEntry> get() = PersonalizedPlanCatalog.entries()

    // ─── 1 · Correspondencia exacta tabla ↔ catálogo ──────────────────────────

    @Test
    fun table_and_catalog_are_an_exact_bijection() {
        val catalogIds = entries.map { it.id }
        val tableIds = PlanEditorialTable.byId.keys
        assertEquals("ids repetidos en el catálogo", catalogIds.size, catalogIds.toSet().size)
        val withoutCard = catalogIds.toSet() - tableIds
        val withoutEntry = tableIds - catalogIds.toSet()
        assertTrue("entradas del catálogo sin ficha editorial: $withoutCard", withoutCard.isEmpty())
        assertTrue("fichas editoriales sin entrada en el catálogo: $withoutEntry", withoutEntry.isEmpty())
        assertEquals("fichas de la tabla", EXPECTED_ENTRIES, tableIds.size)
        assertEquals("entradas del catálogo", EXPECTED_ENTRIES, catalogIds.size)
    }

    @Test
    fun forId_fails_loudly_and_names_the_missing_id() {
        val error = runCatching { PlanEditorialTable.forId("native:no-existe") }.exceptionOrNull()
        assertTrue("debe lanzar IllegalStateException, lanzó $error", error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("native:no-existe"))
    }

    // ─── 2 · Títulos ──────────────────────────────────────────────────────────

    @Test
    fun display_names_are_plain_unique_and_short() {
        val seen = mutableSetOf<String>()
        entries.forEach { entry ->
            val name = entry.displayName
            assertFalse("${entry.id}: «$name» lleva ':' o '_'", name.contains(':') || name.contains('_'))
            assertFalse(
                "${entry.id}: «$name» empieza como un id interno",
                listOf("native", "template", "protocol", "original", "adapted").any { name.startsWith(it, ignoreCase = true) },
            )
            ENGLISH_WORDS.forEach { word ->
                val regex = Regex("""(?<![\p{L}\d])${Regex.escape(word)}(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
                assertFalse("${entry.id}: «$name» lleva la palabra en inglés «$word»", regex.containsMatchIn(name))
            }
            assertFalse("${entry.id}: «$name» empieza por «Progresa con»", name.startsWith("Progresa con"))
            when (entry.id) {
                "protocol:texas-method-3d" -> assertTrue("«$name» debe llevar «(3 días)»", name.endsWith("(3 días)"))
                "protocol:texas-method-4d" -> assertTrue("«$name» debe llevar «(4 días)»", name.endsWith("(4 días)"))
                else -> assertFalse("${entry.id}: «$name» cita los días", name.contains("días"))
            }
            assertTrue("${entry.id}: «$name» mide ${name.length} (máx. $MAX_NAME_LENGTH)", name.length <= MAX_NAME_LENGTH)
            assertTrue("${entry.id}: el título «$name» está repetido", seen.add(name))
        }
    }

    @Test
    fun short_name_drops_the_trailing_parenthesis() {
        assertEquals("Texas Method", entry("protocol:texas-method-3d").shortName)
        assertEquals("Smolov", entry("protocol:smolov").shortName)
        assertEquals("PHUL", entry("protocol:phul-verified").shortName)
        assertEquals("Fuerza KPKN", entry("native:strength-foundation-v2").shortName)
        entries.forEach { assertFalse("${it.id}: shortName con paréntesis", it.shortName.contains("(")) }
    }

    // ─── 3 · Resúmenes ────────────────────────────────────────────────────────

    @Test
    fun summaries_are_short_distinct_and_free_of_internal_jargon() {
        val seen = mutableSetOf<String>()
        entries.forEach { entry ->
            val text = entry.summary
            val sentences = sentencesOf(text)
            val words = wordsOf(text)
            assertTrue("${entry.id}: ${sentences.size} frases (deben ser 2 a 4): «$text»", sentences.size in 2..4)
            assertTrue("${entry.id}: $words palabras (deben ser 25 a 75): «$text»", words in 25..75)
            assertTrue("${entry.id}: el resumen está repetido", seen.add(text))
            FORBIDDEN_IN_SUMMARY.forEach { forbidden ->
                assertFalse("${entry.id}: el resumen contiene «$forbidden»", text.contains(forbidden))
            }
            BARE_ACRONYMS.forEach { acronym ->
                assertFalse(
                    "${entry.id}: el resumen usa la sigla suelta «$acronym»",
                    Regex("""\b$acronym\b""").containsMatchIn(text),
                )
            }
        }
    }

    @Test
    fun summaries_explain_every_technical_term_they_use() {
        entries.forEach { entry ->
            val text = entry.summary
            EXPLAINED_TERMS.forEach { (term, explanation) ->
                if (Regex("""\b$term\b""").containsMatchIn(text)) {
                    assertTrue("${entry.id}: usa «$term» sin explicar «$explanation»", text.contains(explanation))
                }
            }
        }
    }

    // ─── 4 · Niveles ──────────────────────────────────────────────────────────

    @Test
    fun levels_match_the_recipes_and_read_in_plain_spanish() {
        entries.forEach { entry ->
            val label = PlanLabels.levelLabel(entry.levels)
            assertTrue("${entry.id}: la etiqueta de nivel «$label» no es una de las permitidas", label in ALLOWED_LEVEL_LABELS)
            assertTrue("${entry.id}: el nivel base ${entry.level} no está en ${entry.levels}", entry.level in entry.levels)
            if (entry.id !in LEGACY_BASE_LEVEL) {
                assertEquals("${entry.id}: el nivel base debe ser el menor de los niveles", entry.levels.min(), entry.level)
            }
            val claimed = (entry.recipe ?: entry.template?.recipe)?.claimedLevel
            if (claimed != null) {
                assertEquals(
                    "${entry.id}: los niveles deben coincidir con el nivel que declara la receta («$claimed»)",
                    setOf(levelOfClaimed(claimed)),
                    entry.levels,
                )
            }
        }
    }

    @Test
    fun every_adaptive_plan_declares_all_the_levels() {
        // Los cuatro planes propios, las ocho familias históricas y las plantillas simples se adaptan al nivel.
        val adaptive = entries.filter { it.source == CatalogSource.NATIVE || it.template?.recipe == null && it.source == CatalogSource.TEMPLATE }
        assertEquals(12 + 3, adaptive.size)
        adaptive.forEach { assertEquals("${it.id}: debe valer para todos los niveles", CatalogLevel.entries.toSet(), it.levels) }
    }

    // ─── 5 · Días citados ─────────────────────────────────────────────────────

    @Test
    fun days_cited_in_the_summary_match_the_supported_frequency() {
        val citedDays = Regex("""(\d+)\s+días\b""")
        val citedSessions = Regex("""(\d+)\s+sesiones\s+por\s+semana""")
        entries
            .filter { it.supportedFrequencies.first == it.supportedFrequencies.last }
            .filter { it.id !in FREQUENCY_TEXT_EXCEPTIONS }
            .forEach { entry ->
                val cited = (citedDays.findAll(entry.summary) + citedSessions.findAll(entry.summary))
                    .map { it.groupValues[1].toInt() }
                    .toList()
                cited.forEach { days ->
                    assertEquals(
                        "${entry.id}: el resumen cita $days días y el plan admite ${entry.supportedFrequencies}",
                        entry.supportedFrequencies.first,
                        days,
                    )
                }
            }
    }

    // ─── 6 · Orden editorial ──────────────────────────────────────────────────

    @Test
    fun anything_that_is_not_a_plan_ranks_after_every_plan_and_ranks_are_unique() {
        entries.filter { it.kind != PlanKind.PLAN }.forEach { entry ->
            assertTrue("${entry.id}: ${entry.kind} con rank ${entry.rank} (debe ser >= 900)", entry.rank >= 900)
        }
        val ranks = entries.map { it.rank }
        assertEquals("ranks repetidos: ${ranks.groupBy { it }.filter { it.value.size > 1 }.keys}", ranks.size, ranks.toSet().size)
        assertEquals(
            setOf("protocol:smolov", "protocol:smolov-jr"),
            entries.filter { it.kind == PlanKind.ESPECIALIZACION }.map { it.id }.toSet(),
        )
        assertEquals(setOf("protocol:coan-phillipi-dl"), entries.filter { it.kind == PlanKind.COMPLEMENTO }.map { it.id }.toSet())
        assertEquals(
            setOf("template:simple-1", "template:simple-ab", "template:simple-4"),
            entries.filter { it.kind == PlanKind.ESTRUCTURA }.map { it.id }.toSet(),
        )
    }

    // ─── 7 · Duración ─────────────────────────────────────────────────────────

    @Test
    fun repeating_recipes_say_they_repeat_and_finite_ones_do_not() {
        entries.forEach { entry ->
            val recipe = entry.recipe
            val weeks = entry.durationWeeks
            assertNotNull("${entry.id}: sin durationWeeks", weeks)
            val label = PlanLabels.durationLabel(entry.duration, requireNotNull(weeks))
            if (recipe != null) {
                assertEquals("${entry.id}: durationWeeks debe ser el número de semanas de la receta", recipe.weeks.size, weeks)
                when {
                    recipe.repeats && recipe.weeks.size > 1 -> {
                        assertEquals("${entry.id}: ciclo que se repite", CatalogDuration.REPEATING_CYCLE, entry.duration)
                        assertTrue("${entry.id}: «$label» debe decir que se repite", label.contains("que se repite"))
                        assertTrue("${entry.id}: «$label» debe llevar el número de semanas", label.contains("$weeks semanas"))
                    }
                    recipe.repeats -> assertEquals("${entry.id}: semana que se repite", CatalogDuration.REPEATING_WEEK, entry.duration)
                    else -> {
                        assertEquals("${entry.id}: ciclo finito", CatalogDuration.FINITE_CYCLE, entry.duration)
                        assertFalse("${entry.id}: «$label» no debe decir que se repite", label.contains("que se repite"))
                    }
                }
            }
        }
        // Los planes propios de seis semanas son ciclos finitos; las familias históricas, una semana que se repite.
        listOf(
            "native:strength-foundation-v2",
            "native:muscle-foundation-v2",
            "native:powerbuilding-foundation-v2",
            "native:complete-athlete-v2",
        ).forEach { id ->
            assertEquals(id, CatalogDuration.FINITE_CYCLE, entry(id).duration)
            assertEquals(id, NativeWeekBuilder.WEEKS, entry(id).durationWeeks)
        }
        entries.filter { it.source == CatalogSource.NATIVE && it.id.endsWith("-v2").not() }.forEach { entry ->
            assertEquals(entry.id, CatalogDuration.REPEATING_WEEK, entry.duration)
            assertEquals(entry.id, 1, entry.durationWeeks)
        }
    }

    @Test
    fun templates_take_their_weeks_from_the_template() {
        entries.filter { it.source == CatalogSource.TEMPLATE }.forEach { entry ->
            assertEquals(entry.id, entry.template!!.weeks, entry.durationWeeks)
            assertEquals(
                entry.id,
                if (entry.template!!.weeks == 1) CatalogDuration.REPEATING_WEEK else CatalogDuration.FINITE_CYCLE,
                entry.duration,
            )
        }
    }

    // ─── 8 · Glosario (términos) ──────────────────────────────────────────────

    @Test
    fun terms_include_every_term_derived_from_the_recipe() {
        entries.forEach { entry ->
            val recipe = entry.recipe ?: entry.template?.recipe
            assertTrue(
                "${entry.id}: terms ${entry.terms} no incluye los derivados ${derivedTerms(recipe)}",
                entry.terms.containsAll(derivedTerms(recipe)),
            )
            assertTrue("${entry.id}: terms no incluye los editoriales", entry.terms.containsAll(entry.editorial.terms))
        }
    }

    @Test
    fun editorial_terms_cover_the_phrases_the_summary_uses() {
        val phraseToTerm = listOf(
            "máximo de entrenamiento" to PlanTerm.TM,
            "máximo de repeticiones" to PlanTerm.AMRAP,
            "en reserva" to PlanTerm.RIR,
            "descarga" to PlanTerm.DELOAD,
            "que se repite" to PlanTerm.CYCLE,
        )
        entries.forEach { entry ->
            phraseToTerm.forEach { (phrase, term) ->
                if (entry.summary.contains(phrase)) {
                    assertTrue("${entry.id}: el resumen dice «$phrase» y falta $term en sus términos", term in entry.terms)
                }
            }
        }
    }

    @Test
    fun derivedTerms_reads_the_working_sets_of_a_recipe_and_ignores_warmups() {
        assertTrue(derivedTerms(null).isEmpty())

        val everything = recipeOf(
            repeats = true,
            weeks = listOf(
                weekOf(
                    1,
                    slotOf(
                        "main",
                        SlotRole.T1_MAIN,
                        SetRecipe(reps = 5, percent = 80.0, loadBasis = LoadBasis.PERCENT_TM, amrap = true),
                        SetRecipe(reps = 3, rpe = 8.0, loadBasis = LoadBasis.RPE),
                        SetRecipe(reps = 8, rir = 2, loadBasis = LoadBasis.RPE),
                    ),
                    slotOf("fast", SlotRole.SPEED, SetRecipe(reps = 3, percent = 60.0, loadBasis = LoadBasis.PERCENT_1RM)),
                ),
                weekOf(
                    2,
                    slotOf("light", SlotRole.T3_ACCESSORY, SetRecipe(reps = 10, rir = 3, loadBasis = LoadBasis.RPE)),
                    kind = WeekExecutionKind.DELOAD,
                ),
            ),
        )
        assertEquals(
            setOf(PlanTerm.CYCLE, PlanTerm.DELOAD, PlanTerm.SPEED, PlanTerm.AMRAP, PlanTerm.TM, PlanTerm.RPE, PlanTerm.RIR, PlanTerm.ONE_RM),
            derivedTerms(everything),
        )

        val warmupsOnly = recipeOf(
            repeats = false,
            weeks = listOf(
                weekOf(
                    1,
                    slotOf(
                        "main",
                        SlotRole.T1_MAIN,
                        SetRecipe(reps = 5, percent = 40.0, loadBasis = LoadBasis.PERCENT_TM, amrap = true, isWarmup = true),
                        SetRecipe(reps = 5, rir = 3, loadBasis = LoadBasis.RPE, isWarmup = true),
                    ),
                ),
            ),
        )
        assertTrue("los calentamientos no definen el método: ${derivedTerms(warmupsOnly)}", derivedTerms(warmupsOnly).isEmpty())
    }

    @Test
    fun derivedTerms_recognises_top_sets_speed_techniques_and_percent_of_top_set() {
        val topSet = recipeOf(
            repeats = false,
            weeks = listOf(
                weekOf(1, slotOf("top", SlotRole.T1_MAIN, SetRecipe(reps = 3, percent = 90.0, isTopSet = true, loadBasis = LoadBasis.PERCENT_1RM))),
            ),
        )
        assertEquals(setOf(PlanTerm.TOP_SET, PlanTerm.ONE_RM), derivedTerms(topSet))

        val backOff = recipeOf(
            repeats = false,
            weeks = listOf(
                weekOf(1, slotOf("back", SlotRole.T2_SUPPLEMENTAL, SetRecipe(reps = 5, percent = 85.0, loadBasis = LoadBasis.PERCENT_OF_TOP_SET))),
            ),
        )
        assertEquals(setOf(PlanTerm.TOP_SET), derivedTerms(backOff))

        val speedTechnique = recipeOf(
            repeats = false,
            weeks = listOf(
                weekOf(1, slotOf("sq", SlotRole.T2_SUPPLEMENTAL, SetRecipe(reps = 3, percent = 60.0), technique = TechniqueModifier.SPEED)),
            ),
        )
        assertTrue(PlanTerm.SPEED in derivedTerms(speedTechnique))
        assertFalse(PlanTerm.CYCLE in derivedTerms(speedTechnique))
    }

    // ─── 9 · Atribución ───────────────────────────────────────────────────────

    @Test
    fun attribution_lines_are_present_name_the_author_and_never_affiliate_kpkn() {
        entries.forEach { entry ->
            val line = entry.attributionLine
            if (entry.source == CatalogSource.PROTOCOL) assertNotNull("${entry.id}: sin línea de atribución", line)
            if (line == null) return@forEach
            assertFalse("${entry.id}: «$line» afilia a KPKN", line.contains("No afiliado a KPKN", ignoreCase = true))
            when (entry.origin) {
                PlanOrigin.KPKN -> {
                    if (entry.source == CatalogSource.PROTOCOL) {
                        assertTrue("${entry.id}: «$line»", line.startsWith(OWN_PLAN))
                    } else {
                        assertEquals("${entry.id}: los nativos y las plantillas son planes propios", OWN_PLAN, line)
                    }
                }
                PlanOrigin.KPKN_VERSION -> {
                    assertTrue("${entry.id}: «$line»", line.startsWith("Versión KPKN basada en "))
                    assertNamesTheAuthor(entry, line)
                }
                PlanOrigin.LEGACY_VERSION -> {
                    assertTrue("${entry.id}: «$line»", line.startsWith("Versión anterior en KPKN de "))
                    assertNamesTheAuthor(entry, line)
                }
                PlanOrigin.ORIGINAL -> {
                    assertTrue("${entry.id}: «$line»", line.startsWith("Original fiel de "))
                    assertNamesTheAuthor(entry, line)
                    assertTrue("${entry.id}: la atribución debe citar la edición", line.contains(entry.authoredSource!!.edition))
                }
                PlanOrigin.ADAPTED -> {
                    assertTrue("${entry.id}: «$line»", line.startsWith("Adaptación KPKN del original de "))
                    assertNamesTheAuthor(entry, line)
                    assertTrue("${entry.id}: la atribución debe citar la edición", line.contains(entry.authoredSource!!.edition))
                }
            }
            if (entry.origin != PlanOrigin.KPKN) {
                assertTrue("${entry.id}: «$line» debe aclarar que no hay afiliación", line.contains("No afiliado a "))
            }
        }
    }

    @Test
    fun the_origin_of_authored_entries_comes_from_their_declared_provenance() {
        val authored = entries.filter { it.provenance != null }
        assertEquals(4, authored.size)
        authored.forEach { entry ->
            val expected = when (entry.provenance!!.category) {
                PlanProvenanceClass.ORIGINAL -> PlanOrigin.ORIGINAL
                PlanProvenanceClass.ADAPTED -> PlanOrigin.ADAPTED
                else -> error("${entry.id}: procedencia inesperada")
            }
            assertEquals(entry.id, expected, entry.origin)
        }
        assertEquals(
            "los originales van antes que las adaptaciones",
            listOf(PlanOrigin.ORIGINAL, PlanOrigin.ORIGINAL, PlanOrigin.ADAPTED, PlanOrigin.ADAPTED),
            authored.sortedBy { it.rank }.map { it.origin },
        )
    }

    @Test
    fun the_notes_of_the_method_are_plain_spanish_sentences_on_the_expected_plans() {
        val withNote = setOf(
            "protocol:texas-method-3d",
            "protocol:westside-conjugate",
            "protocol:cube-method",
            "protocol:smolov",
            "protocol:smolov-jr",
            "protocol:korte-3x3",
            "protocol:sheiko-29-32",
            "protocol:nsuns-531-lp-4d",
            "protocol:coan-phillipi-dl",
            "protocol:phul-verified",
            "protocol:phat-verified",
        )
        entries.forEach { entry ->
            assertEquals("${entry.id}: notas del método", if (entry.id in withNote) 1 else 0, entry.notes.size)
            entry.notes.forEach { note ->
                assertTrue("${entry.id}: la nota «$note» debe ser una frase completa", note.endsWith("."))
                FORBIDDEN_IN_SUMMARY.forEach { forbidden ->
                    assertFalse("${entry.id}: la nota «$note» contiene «$forbidden»", note.contains(forbidden))
                }
            }
        }
    }

    // ─── 10 · Alias deprecados ────────────────────────────────────────────────

    @Test
    @Suppress("DEPRECATION")
    fun deprecated_aliases_are_derived_from_the_editorial_fields() {
        entries.forEach { entry ->
            assertEquals("${entry.id}: title", entry.displayName, entry.title)
            assertEquals("${entry.id}: description", entry.summary, entry.description)
            assertEquals("${entry.id}: technicalSubtitle", PlanLabels.subtitle(entry), entry.technicalSubtitle)
        }
    }

    // ─── 11 · Lo que este paso deja inerte ────────────────────────────────────

    @Test
    fun listed_and_the_references_override_are_inert_in_this_step() {
        // C.P2b activa `listed = false` (ocultar históricos) y `references` (re-baseline de cobertura).
        PlanEditorialTable.byId.forEach { (id, editorial) ->
            assertTrue("$id: listed debe seguir en true", editorial.listed)
            assertNull("$id: references debe seguir en null", editorial.references)
        }
        entries.forEach { assertTrue("${it.id}: listed", it.listed) }
    }

    // ─── Ayudas ───────────────────────────────────────────────────────────────

    private fun entry(id: String): CatalogEntry =
        requireNotNull(PersonalizedPlanCatalog.find(id)) { "Falta la entrada «$id» en el catálogo de planes" }

    private fun sentencesOf(text: String): List<String> =
        Regex("""(?<=[.!?])\s+""").split(text.trim()).filter { it.isNotBlank() }

    private fun wordsOf(text: String): Int = text.trim().split(Regex("""\s+""")).size

    private fun levelOfClaimed(claimed: String): CatalogLevel = when (claimed.trim().lowercase()) {
        "principiante" -> CatalogLevel.BEGINNER
        "intermedio" -> CatalogLevel.INTERMEDIATE
        "avanzado" -> CatalogLevel.ADVANCED
        else -> error("Nivel declarado desconocido: «$claimed»")
    }

    private fun assertNamesTheAuthor(entry: CatalogEntry, line: String) {
        val author = entry.provenance?.sourceAuthor ?: entry.sourceAuthor
        assertNotNull("${entry.id}: sin autor", author)
        // Apellido = última palabra de cada autor: «Mark Rippetoe / Glenn Pendlay» da Rippetoe y Pendlay.
        val surnames = author!!.split("/", "(", ")", ",").map { it.trim() }.filter { it.isNotEmpty() }.map { it.substringAfterLast(" ") }
        assertTrue("${entry.id}: «$line» no nombra a ningún autor de «$author»", surnames.any { line.contains(it) })
    }

    private fun recipeOf(repeats: Boolean, weeks: List<WeekRecipe>) =
        TrainingPlanRecipe(id = "test-recipe", weeks = weeks, repeats = repeats)

    private fun weekOf(number: Int, vararg slots: SlotRecipe, kind: WeekExecutionKind = WeekExecutionKind.TRAINING) =
        WeekRecipe(weekNumber = number, blockIndex = 0, kind = kind, days = listOf(DayRecipe(label = "Día 1", slots = slots.toList())))

    private fun slotOf(
        id: String,
        role: SlotRole,
        vararg sets: SetRecipe,
        technique: TechniqueModifier? = null,
    ) = SlotRecipe(
        id = id,
        role = role,
        lift = LiftRef("test_configuration"),
        sets = sets.toList(),
        restSeconds = 90,
        technique = technique,
    )

    private companion object {
        const val EXPECTED_ENTRIES = 55
        const val MAX_NAME_LENGTH = 48
        const val OWN_PLAN = "Plan propio de KPKN."

        val ENGLISH_WORDS = listOf(
            "Beginner", "Intermediate", "Advanced", "Upper", "Lower", "Push", "Pull", "Legs",
            "Off-season", "Week", "Peak", "Taper", "Block",
        )

        val FORBIDDEN_IN_SUMMARY = listOf(
            "Conservamos el orden", "Una planificación de", "T1", "T2", "T3", "cero exenciones",
            "(repeats)", "CLOSE_GRIP", "PPST", "§", "retitul",
        )

        val BARE_ACRONYMS = listOf("MEV", "MRV", "SBD", "PPST")

        /** Sigla → frase que debe acompañarla en el mismo texto. */
        val EXPLAINED_TERMS = listOf(
            "TM" to "máximo de entrenamiento",
            "AMRAP" to "máximo de repeticiones",
            "RIR" to "en reserva",
            "DUP" to "ondulación",
        )

        val ALLOWED_LEVEL_LABELS = setOf(
            "Principiante", "Intermedio", "Avanzado", "Todos los niveles",
            "Principiante a intermedio", "Intermedio a avanzado",
        )

        /**
         * Nivel base heredado: `NativeSpec` conserva INTERMEDIATE para estas dos familias aunque
         * sus `levels` sean «todos». El planner de hoy ordena por `level`; el comparador por
         * `levels` llega en C.P3 y este caso desaparece.
         */
        val LEGACY_BASE_LEVEL = setOf("native:gym-muscle", "native:bodyweight")

        /**
         * Entradas cuyo resumen cita días que la receta de hoy no cumple (unión de días distinta
         * de la declarada). ⚠ receta: E-10, se alinea en B.S6.
         */
        val FREQUENCY_TEXT_EXCEPTIONS = setOf(
            "protocol:candito-6",
            "protocol:lilliebridge",
            "protocol:smolov",
            "protocol:smolov-jr",
        )
    }
}
