package com.example.kpkn.screens.programs

import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanKind
import com.example.kpkn.data.programs.PlanLabels
import com.example.kpkn.data.programs.PlanOrigin
import com.example.kpkn.data.programs.PublicationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * C.P5 · La biblioteca de planes (JVM puro, sin Robolectric ni Compose).
 *
 * Fija sobre las 50 entradas que la biblioteca ofrece: la tarjeta sale de la ficha editorial (nombre,
 * procedencia, resumen, metadatos y atribución) sin `level.name` en inglés ni ids; el orden es el editorial
 * de DEC-w2-06 (propios, originales, adaptaciones, plantillas, terceros, históricos…); el filtro de
 * procedencia coincide con la etiqueta de la tarjeta; y el tap de cada clase de tarjeta sigue al asistente cuando
 * lo hay (C.P6, decisión D8: la biblioteca no se salta el evaluador) y, sin asistente (la biblioteca embebida del
 * editor), hace lo de siempre (crear desde la plantilla, entregar el método al llamador) o abre la hoja de solo lectura.
 */
class LibraryCardModelTest {

    /** Lo que la biblioteca ofrece: las entradas listadas y publicadas. */
    private val listed: List<CatalogEntry>
        get() = PersonalizedPlanCatalog.listedEntries().filter { it.publication == PublicationState.PUBLISHED }

    private fun entry(id: String): CatalogEntry =
        PersonalizedPlanCatalog.find(id) ?: error("Falta la entrada del catálogo «$id»")

    // ─── 1 · La tarjeta sale de la ficha editorial ───────────────────────────

    @Test
    fun the_library_offers_the_fifty_listed_entries() {
        // 55 entradas, cinco nativos históricos ocultos (C.P2b, DEC-w2-07).
        assertEquals(EXPECTED_LISTED, listed.size)
        assertEquals(listed.size, listed.map { it.id }.toSet().size)
    }

    @Test
    fun every_card_reads_its_name_provenance_summary_and_meta_from_the_editorial_card() {
        listed.forEach { entry ->
            val card = LibraryCardModel(entry)
            assertEquals("${entry.id}: título", entry.displayName, card.title)
            assertEquals("${entry.id}: procedencia", PlanLabels.provenanceLabel(entry), card.provenance)
            assertEquals("${entry.id}: resumen", entry.summary, card.summary)
            assertEquals("${entry.id}: metadatos", PlanLabels.metaLine(entry), card.meta)
            assertEquals("${entry.id}: atribución", entry.attributionLine, card.attribution)
        }
    }

    @Test
    fun no_card_shows_an_id_an_english_level_or_a_legacy_label() {
        listed.forEach { entry ->
            val card = LibraryCardModel(entry)
            val texts = listOfNotNull(card.title, card.provenance, card.summary, card.meta, card.attribution)
            texts.forEach { text ->
                assertFalse("${entry.id}: «$text» está vacío", text.isBlank())
                assertFalse("${entry.id}: «$text» lleva un nivel en inglés", ENGLISH_LEVEL.containsMatchIn(text))
                assertFalse("${entry.id}: «$text» lleva un id con prefijo", ID_WITH_PREFIX.containsMatchIn(text))
                assertFalse("${entry.id}: «$text» lleva un guion bajo", text.contains('_'))
            }
            assertTrue("${entry.id}: «${card.title}» mide más de $MAX_TITLE", card.title.length <= MAX_TITLE)
            assertFalse("${entry.id}: «${card.provenance}» es la etiqueta antigua", card.provenance.startsWith("Método ·"))
            assertFalse("${entry.id}: «${card.provenance}» no declara procedencia", card.provenance.contains("procedencia heredada"))
            assertTrue(
                "${entry.id}: «${card.meta}» no dice los días por semana",
                card.meta.contains("por semana"),
            )
            assertTrue(
                "${entry.id}: «${card.meta}» no dice el nivel en español",
                LEVEL_LABELS.any { label -> card.meta.contains(label) },
            )
        }
    }

    @Test
    fun an_authored_card_replaces_the_old_author_and_edition_line_with_the_editorial_attribution() {
        listed.filter { it.authoredSource != null }.forEach { entry ->
            val source = requireNotNull(entry.authoredSource)
            val attribution = requireNotNull(LibraryCardModel(entry).attribution) { "${entry.id}: sin atribución" }
            assertTrue("${entry.id}: «$attribution» no cita al autor", attribution.contains(source.author))
            assertTrue("${entry.id}: «$attribution» no cita la edición", attribution.contains(source.edition))
            assertTrue("${entry.id}: «$attribution» no desafilia", attribution.contains("No afiliado a ${source.author}"))
        }
        assertEquals("PHUL y PHAT, originales y adaptados", 4, listed.count { it.authoredSource != null })
    }

    @Test
    fun a_third_party_card_names_the_method_author_in_its_provenance_and_never_affiliates_kpkn() {
        val texas = LibraryCardModel(entry("protocol:texas-method-3d"))
        assertTrue(texas.provenance, texas.provenance.startsWith("Versión KPKN del método de "))
        assertTrue(texas.meta, texas.meta.startsWith("3 días por semana · "))
        assertTrue(requireNotNull(texas.attribution).contains("No afiliado a"))
        listed.forEach { entry ->
            assertFalse(
                "${entry.id}: afilia a KPKN",
                LibraryCardModel(entry).attribution.orEmpty().contains("No afiliado a KPKN", ignoreCase = true),
            )
        }
    }

    // ─── 2 · Orden editorial ─────────────────────────────────────────────────

    @Test
    fun the_library_is_sorted_by_rank_and_then_by_name() {
        val ordered = libraryOrder(listed)
        assertEquals("no pierde ni repite entradas", listed.map { it.id }.toSet(), ordered.map { it.id }.toSet())
        assertEquals(listed.size, ordered.size)
        ordered.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.id} (${a.rank}) va antes que ${b.id} (${b.rank})", a.rank <= b.rank)
            if (a.rank == b.rank) {
                assertTrue(
                    "mismo rank ${a.rank}: «${a.displayName}» debe ir antes que «${b.displayName}»",
                    String.CASE_INSENSITIVE_ORDER.compare(a.displayName, b.displayName) <= 0,
                )
            }
        }
    }

    @Test
    fun the_order_does_not_depend_on_the_order_of_the_catalog() {
        val expected = libraryOrder(listed).map { it.id }
        repeat(5) { seed ->
            assertEquals("semilla $seed", expected, libraryOrder(listed.shuffled(Random(seed))).map { it.id })
        }
    }

    @Test
    fun equal_ranks_are_told_apart_by_name_ignoring_case_and_the_lowest_rank_goes_first() {
        val base = entry("protocol:texas-method-3d")
        fun card(name: String, rank: Int) = base.copy(
            id = "prueba:$name",
            editorial = base.editorial.copy(displayName = name, rank = rank),
        )
        val ordered = libraryOrder(listOf(card("b", 5), card("C", 5), card("a", 5), card("z", 1)))
        assertEquals(listOf("z", "a", "b", "C"), ordered.map { it.displayName })
    }

    @Test
    fun the_order_goes_from_own_plans_to_originals_adaptations_templates_third_parties_and_historical_ones() {
        val ordered = libraryOrder(listed)
        // Los ocho primeros son los cuatro planes propios y PHUL y PHAT (originales y adaptados).
        assertEquals(
            listOf(
                "native:strength-foundation-v2",
                "native:muscle-foundation-v2",
                "native:powerbuilding-foundation-v2",
                "native:complete-athlete-v2",
                "original:phul-ms-2021-r1",
                "original:phat-biolayne-2016-r1",
                "adapted:phul-kpkn-r1",
                "adapted:phat-kpkn-r1",
            ),
            ordered.take(8).map { it.id },
        )
        val groups = ordered.map { entry -> group(entry) }
        groups.zipWithNext().forEachIndexed { index, (a, b) ->
            assertTrue(
                "«${ordered[index].displayName}» (grupo $a) va antes que «${ordered[index + 1].displayName}» (grupo $b)",
                a <= b,
            )
        }
        assertEquals("están todos los grupos", GROUP_SIZES.keys, groups.toSet())
        GROUP_SIZES.forEach { (group, size) -> assertEquals("tamaño del grupo $group", size, groups.count { it == group }) }
        // Las especializaciones, los complementos, las estructuras y las versiones anteriores cierran la lista.
        assertEquals("protocol:phat-verified", ordered.last().id)
        assertTrue(ordered.takeLast(8).all { entry -> group(entry) >= 6 })
    }

    // ─── 3 · Procedencia: el filtro coincide con la etiqueta de la tarjeta ───

    @Test
    fun the_origin_filters_agree_with_the_label_of_the_card() {
        listed.forEach { entry ->
            val provenance = LibraryCardModel(entry).provenance
            val matching = LibraryOriginFilter.entries
                .filter { filter -> filter != LibraryOriginFilter.ALL && originMatches(entry, filter) }
            assertEquals("${entry.id}: cada tarjeta cae en un solo filtro de procedencia ($matching)", 1, matching.size)
            assertTrue("${entry.id}: «Todas» las incluye", originMatches(entry, LibraryOriginFilter.ALL))
            when (matching.single()) {
                LibraryOriginFilter.ORIGINAL -> assertTrue(provenance, provenance.startsWith("Original fiel"))
                LibraryOriginFilter.ADAPTED -> assertTrue(provenance, provenance.startsWith("Adaptación KPKN"))
                LibraryOriginFilter.KPKN -> assertEquals("Plan KPKN", provenance)
                LibraryOriginFilter.LEGACY -> assertTrue(
                    "${entry.id}: «$provenance»",
                    provenance.startsWith("Versión KPKN del método") || provenance == PersonalizedPlanCatalog.LEGACY_VERSION_LABEL,
                )
                LibraryOriginFilter.ALL -> error("«Todas» no puede ser el único filtro")
            }
        }
    }

    @Test
    fun the_five_kpkn_plans_with_a_fixed_recipe_are_plan_kpkn_and_not_version_kpkn() {
        // Su autor es «KPKN Fit» (no «KPKN»): antes caían en «Versión KPKN» aunque la tarjeta dijera «Plan KPKN».
        listOf(
            "protocol:kpkn-native-sbd-4",
            "protocol:kpkn-ppl-6",
            "protocol:kpkn-rp-style",
            "protocol:kpkn-rts-style",
            "protocol:kpkn-sbs-rtf",
        ).forEach { id ->
            val entry = entry(id)
            assertTrue("$id: filtro «Plan KPKN»", originMatches(entry, LibraryOriginFilter.KPKN))
            assertFalse("$id: filtro «Versión KPKN»", originMatches(entry, LibraryOriginFilter.LEGACY))
            assertEquals("Plan KPKN", LibraryCardModel(entry).provenance)
        }
    }

    @Test
    fun the_origin_filters_split_the_fifty_entries_as_the_editorial_table_does() {
        fun count(filter: LibraryOriginFilter) = listed.count { originMatches(it, filter) }
        assertEquals(EXPECTED_LISTED, count(LibraryOriginFilter.ALL))
        // Cuatro propios + tres nativos históricos + diez plantillas + cinco planes KPKN de receta fija.
        assertEquals(22, count(LibraryOriginFilter.KPKN))
        assertEquals(2, count(LibraryOriginFilter.ORIGINAL))
        assertEquals(2, count(LibraryOriginFilter.ADAPTED))
        // Veintidós métodos de terceros y las dos versiones anteriores de PHUL y PHAT.
        assertEquals(24, count(LibraryOriginFilter.LEGACY))
    }

    // ─── 4 · Qué hace el tap de cada tarjeta ─────────────────────────────────

    @Test
    fun a_template_with_a_recipe_follows_the_assistant_when_there_is_one_and_creates_from_the_template_when_there_is_not() {
        val templates = listed.filter { it.source == CatalogSource.TEMPLATE }
        assertEquals(10, templates.size)
        val withRecipe = templates.filterNot(::isBlankStructure)
        // Las siete plantillas con la semana escrita en una receta; las tres estructuras en blanco van aparte (H1).
        assertEquals(7, withRecipe.size)
        withRecipe.forEach { entry ->
            // C.P6 (D8, r2 §16.1): con asistente la plantilla NO crea el programa directo; su botón lleva al asistente,
            // que evalúa con el material, los días y el tiempo de la persona antes de proponerla.
            val withAssistant = libraryTapFor(entry, hasPlanAction = true)
            assertEquals("${entry.id}: sigue al asistente", LibraryTap.OpenSheet(LibraryPrimary.ConfigurePlan), withAssistant)
            assertEquals("Configurar este plan", (withAssistant as LibraryTap.OpenSheet).primary?.label)
            // Sin asistente (la biblioteca embebida del editor) rige el camino directo de siempre.
            val tap = libraryTapFor(entry, hasPlanAction = false)
            assertTrue("${entry.id}: $tap", tap is LibraryTap.OpenSheet)
            val primary = (tap as LibraryTap.OpenSheet).primary
            assertEquals("${entry.id}: usa su plantilla", LibraryPrimary.UseTemplate(requireNotNull(entry.template)), primary)
            assertEquals("Usar esta plantilla", primary?.label)
        }
    }

    @Test
    fun a_blank_structure_keeps_the_direct_path_even_when_there_is_an_assistant() {
        // H1 (excepción a r2 §16.1): el asistente no materializa plantillas vacías y nunca las ofrece como candidatas, así
        // que «Configurar este plan» las dejaría en un callejón sin salida. Conservan «Usar esta plantilla».
        val structures = listed.filter { it.kind == PlanKind.ESTRUCTURA }
        assertEquals(
            "las tres estructuras en blanco",
            setOf("template:simple-1", "template:simple-ab", "template:simple-4"),
            structures.map { it.id }.toSet(),
        )
        structures.forEach { entry ->
            assertTrue("${entry.id}: es una estructura en blanco", isBlankStructure(entry))
            val template = requireNotNull(entry.template)
            val withAssistant = libraryTapFor(entry, hasPlanAction = true)
            assertEquals("${entry.id}: camino directo", LibraryTap.OpenSheet(LibraryPrimary.UseTemplate(template)), withAssistant)
            assertEquals("Usar esta plantilla", (withAssistant as LibraryTap.OpenSheet).primary?.label)
            assertEquals(
                "${entry.id}: sin asistente es lo mismo",
                LibraryTap.OpenSheet(LibraryPrimary.UseTemplate(template)),
                libraryTapFor(entry, hasPlanAction = false),
            )
        }
        // Ninguna otra entrada se considera estructura en blanco.
        assertEquals(structures.map { it.id }.toSet(), listed.filter(::isBlankStructure).map { it.id }.toSet())
    }

    @Test
    fun the_previous_muscle_and_cardio_version_opens_a_read_only_sheet_because_no_goal_of_the_assistant_offers_it() {
        // H1: `native:strength-cardio` declara `references = ∅` (C.P2b): ningún objetivo del asistente lo sirve.
        val legacy = entry("native:strength-cardio")
        assertFalse(servesSomeWizardGoal(legacy))
        assertEquals(LibraryTap.OpenSheet(null), libraryTapFor(legacy, hasPlanAction = true))
        assertNull((libraryTapFor(legacy, hasPlanAction = true) as LibraryTap.OpenSheet).primary)
        // Los planes que sí sirven a un objetivo siguen configurándose.
        assertTrue(servesSomeWizardGoal(entry("native:muscle-foundation-v2")))
        assertEquals(
            LibraryTap.OpenSheet(LibraryPrimary.ConfigurePlan),
            libraryTapFor(entry("native:muscle-foundation-v2"), hasPlanAction = true),
        )
        // «Otros» del filtro de perfil es justo lo que ningún objetivo sirve: la versión anterior y las tres estructuras.
        assertEquals(
            setOf("native:strength-cardio", "template:simple-1", "template:simple-ab", "template:simple-4"),
            listed.filterNot(::servesSomeWizardGoal).map { it.id }.toSet(),
        )
    }

    @Test
    fun a_library_method_follows_the_assistant_when_there_is_one_and_is_handed_to_the_caller_when_there_is_not() {
        val protocols = listed.filter { visibleProtocolOf(it) != null }
        // 22 métodos de terceros, 5 planes KPKN de receta fija y las dos versiones anteriores.
        assertEquals(29, protocols.size)
        protocols.forEach { entry ->
            // C.P6 (D8): con asistente el método tampoco crea el programa directo (antes: TM wizard en dos toques).
            assertEquals(
                "${entry.id}: sigue al asistente",
                LibraryTap.OpenSheet(LibraryPrimary.ConfigurePlan),
                libraryTapFor(entry, hasPlanAction = true),
            )
            // Sin asistente se entrega al llamador, que ya enseña la misma hoja con `ProtocolDetailSheet`.
            val tap = libraryTapFor(entry, hasPlanAction = false)
            assertTrue("${entry.id}: $tap", tap is LibraryTap.HandOffProtocol)
            assertEquals(entry.sourceId, (tap as LibraryTap.HandOffProtocol).protocol.id)
        }
        // Sin ficha en la biblioteca, el método sigue al asistente si lo hay, o abre la hoja de solo lectura.
        val texas = entry("protocol:texas-method-3d")
        assertEquals(
            LibraryTap.OpenSheet(LibraryPrimary.ConfigurePlan),
            libraryTapFor(texas, hasPlanAction = true, protocolOf = { null }),
        )
        assertEquals(
            LibraryTap.OpenSheet(null),
            libraryTapFor(texas, hasPlanAction = false, protocolOf = { null }),
        )
    }

    @Test
    fun own_and_authored_plans_configure_the_plan_when_there_is_an_assistant_and_are_read_only_when_there_is_not() {
        val withoutProtocol = listed.filter { it.template == null && visibleProtocolOf(it) == null }
        // Los siete nativos y los cuatro de autor.
        assertEquals(11, withoutProtocol.size)
        // Con asistente, todos se configuran salvo la versión anterior «Músculo y cardio», que ningún objetivo sirve (H1).
        val configurable = withoutProtocol.filter(::servesSomeWizardGoal)
        assertEquals(10, configurable.size)
        configurable.forEach { entry ->
            val configure = libraryTapFor(entry, hasPlanAction = true)
            assertEquals("${entry.id}", LibraryTap.OpenSheet(LibraryPrimary.ConfigurePlan), configure)
            assertEquals("Configurar este plan", (configure as LibraryTap.OpenSheet).primary?.label)
        }
        assertEquals(
            listOf("native:strength-cardio"),
            withoutProtocol.filterNot(::servesSomeWizardGoal).map { it.id },
        )
        withoutProtocol.forEach { entry ->
            // Sin asistente no hay acción posible para ellos: la hoja es de solo lectura.
            val readOnly = libraryTapFor(entry, hasPlanAction = false)
            assertEquals("${entry.id}", LibraryTap.OpenSheet(null), readOnly)
            assertNull((readOnly as LibraryTap.OpenSheet).primary)
        }
    }

    @Test
    fun the_primary_labels_are_the_sheet_labels() {
        assertEquals(PlanInfoModelBuilder.PRIMARY_LIBRARY, LibraryPrimary.ConfigurePlan.label)
        assertEquals("Configurar este plan", LibraryPrimary.ConfigurePlan.label)
        assertEquals(
            "Usar esta plantilla",
            LibraryPrimary.UseTemplate(requireNotNull(entry("template:simple-1").template)).label,
        )
    }

    @Test
    fun every_listed_entry_resolves_to_an_action_or_to_the_read_only_sheet_in_every_context() {
        listed.forEach { entry ->
            // Con asistente toda tarjeta abre su hoja y nada se entrega al llamador: las que el asistente ofrece traen
            // «Configurar este plan»; las tres estructuras en blanco, «Usar esta plantilla»; la versión anterior que
            // ningún objetivo sirve, la hoja de solo lectura (H1).
            val expected = when {
                isBlankStructure(entry) -> LibraryTap.OpenSheet(LibraryPrimary.UseTemplate(requireNotNull(entry.template)))
                !servesSomeWizardGoal(entry) -> LibraryTap.OpenSheet(null)
                else -> LibraryTap.OpenSheet(LibraryPrimary.ConfigurePlan)
            }
            assertEquals("${entry.id}: con asistente", expected, libraryTapFor(entry, hasPlanAction = true))
            val tap = libraryTapFor(entry, hasPlanAction = false)
            when (tap) {
                is LibraryTap.HandOffProtocol -> assertEquals(entry.sourceId, tap.protocol.id)
                is LibraryTap.OpenSheet -> if (entry.template == null) assertNull("${entry.id}", tap.primary)
            }
        }
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    /** El grupo editorial de una entrada, para comprobar que el orden por `rank` respeta la secuencia pedida. */
    private fun group(entry: CatalogEntry): Int = when {
        entry.id in OWN_PROFILES -> 0
        entry.origin == PlanOrigin.ORIGINAL -> 1
        entry.origin == PlanOrigin.ADAPTED -> 2
        entry.kind == PlanKind.ESPECIALIZACION || entry.kind == PlanKind.COMPLEMENTO -> 6
        entry.kind == PlanKind.ESTRUCTURA -> 7
        entry.origin == PlanOrigin.LEGACY_VERSION -> 8
        entry.source == CatalogSource.TEMPLATE || (entry.origin == PlanOrigin.KPKN && entry.source == CatalogSource.PROTOCOL) -> 3
        entry.origin == PlanOrigin.KPKN_VERSION -> 4
        entry.source == CatalogSource.NATIVE -> 5
        else -> error("La entrada ${entry.id} no encaja en ningún grupo editorial")
    }

    private companion object {
        const val EXPECTED_LISTED = 50
        const val MAX_TITLE = 48

        val OWN_PROFILES = setOf(
            "native:strength-foundation-v2",
            "native:muscle-foundation-v2",
            "native:powerbuilding-foundation-v2",
            "native:complete-athlete-v2",
        )

        /**
         * 0 propios (4) · 1 originales (2) · 2 adaptados (2) · 3 plantillas con receta y planes KPKN de receta
         * fija (7 + 5) · 4 terceros (19) · 5 históricos (3) · 6 especializaciones y complemento (3) ·
         * 7 estructuras en blanco (3) · 8 versiones anteriores (2).
         */
        val GROUP_SIZES: Map<Int, Int> = mapOf(0 to 4, 1 to 2, 2 to 2, 3 to 12, 4 to 19, 5 to 3, 6 to 3, 7 to 3, 8 to 2)

        val ENGLISH_LEVEL = Regex("""\b(beginner|intermediate|advanced)\b""", RegexOption.IGNORE_CASE)
        val ID_WITH_PREFIX = Regex("""\b(native|template|protocol|original|adapted):[a-z0-9]""")
        val LEVEL_LABELS = listOf("Todos los niveles", "Principiante", "Intermedio", "Avanzado")
    }
}
