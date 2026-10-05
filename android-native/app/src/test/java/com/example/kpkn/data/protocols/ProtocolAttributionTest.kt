package com.example.kpkn.data.protocols

import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.definitions.AuthoredSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolAttributionTest {
    @Test
    fun verified_archetype_support_slots_are_kpkn_default() {
        val failures = mutableListOf<String>()
        PROTOCOL_LIBRARY.filter {
            it.isVisibleForApplication && it.publicationStatus == ProtocolPublicationStatus.VERIFIED
        }.forEach { protocol ->
            val recipe = protocol.recipe ?: return@forEach
            recipe.weeks.forEach { week ->
                week.days.forEach { day ->
                    if (day.archetype == null) return@forEach
                    day.slots.filter { it.role != SlotRole.T1_MAIN }.forEach { slot ->
                        if (slot.source != SlotSource.KPKN_DEFAULT) {
                            failures += "${protocol.id} w${week.weekNumber}/${day.label} ${slot.id} (${slot.role}) es ${slot.source}"
                        }
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun kpkn_assist_slots_are_never_author() {
        val failures = mutableListOf<String>()
        PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.forEach { protocol ->
            protocol.recipe?.weeks?.forEach { week ->
                week.days.forEach { day ->
                    day.slots.filter { it.role == SlotRole.T3_ACCESSORY && it.source == SlotSource.AUTHOR && day.archetype != null }
                        .forEach { slot ->
                            failures += "${protocol.id} ${day.label} T3 ${slot.id} AUTHOR en arquetipo"
                        }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ─── §10.1: procedencia de las recetas autoradas (paquete E) ─────────────

    private val authoredOriginals
        get() = listOf(
            AuthoredPhulPhatRecipes.phulOriginal to AuthoredSources.phul,
            AuthoredPhulPhatRecipes.phatOriginal to AuthoredSources.phat,
        )

    @Test
    fun authored_originals_register_source_edition_and_consult_date_separately_from_kpkn_defaults() {
        authoredOriginals.forEach { (recipe, source) ->
            val provenance = requireNotNull(recipe.provenance) { "${recipe.id} sin procedencia" }
            assertEquals("${recipe.id} clase", PlanProvenanceClass.ORIGINAL, provenance.category)
            assertEquals(source.sourceTitle, provenance.sourceTitle)
            assertEquals(source.sourceUrl, provenance.sourceUrl)
            assertEquals(source.author, provenance.sourceAuthor)
            // §10.1 regla 4: edición/fecha de la fuente y fecha de consulta.
            val edition = requireNotNull(provenance.sourceEdition)
            assertTrue("edición de la fuente en '$edition'", edition.contains(source.edition))
            assertTrue("consulta en '$edition'", edition.contains(source.consultedOn))
            assertEquals(source.consultedOn, "2026-09-28")
            // Reglas efectivas de la fuente (parámetros publicados).
            assertTrue(source.effectiveRules.isNotEmpty())
            // «Configuración inicial KPKN» separada de la prescripción del autor.
            assertTrue("defaults KPKN requeridos", source.kpknDefaults.isNotEmpty())
            assertTrue(provenance.operationalDefaults.isNotEmpty())
            assertTrue(provenance.operationalDefaults.any { it.scope == KpknOperationalDefaultScope.REST })
            assertTrue(provenance.operationalDefaults.any { it.scope == KpknOperationalDefaultScope.INITIAL_CHOICE })
            assertTrue(provenance.operationalDefaults.any { it.scope == KpknOperationalDefaultScope.WARMUP })
            // Textos disjuntos: una regla del autor nunca aparece como default KPKN.
            assertTrue(
                "reglas de fuente y defaults KPKN no se mezclan",
                source.effectiveRules.none { rule -> source.kpknDefaults.any { it.contains(rule) } },
            )
            // Sin cambios de slot en el original: se publica tal cual la tabla.
            assertTrue(provenance.slotChanges.isEmpty())
        }
    }

    @Test
    fun authored_adaptations_reference_their_parent_without_mutating_it() {
        listOf(
            AuthoredPhulPhatRecipes.phulAdapted to AuthoredPhulPhatRecipes.phulOriginal,
            AuthoredPhulPhatRecipes.phatAdapted to AuthoredPhulPhatRecipes.phatOriginal,
        ).forEach { (adapted, original) ->
            val provenance = requireNotNull(adapted.provenance)
            assertEquals(PlanProvenanceClass.ADAPTED, provenance.category)
            assertEquals(original.id, provenance.parentId)
            assertEquals(1, provenance.parentRevision)
            assertEquals(adapted.id, provenance.planId)
            // La tabla original no se muta: mismo día/orden/dosis en la adaptación.
            assertEquals(
                original.weeks.first().days.map { day -> day.id to day.slots.map { slot -> slot.id to slot.sets } },
                adapted.weeks.first().days.map { day -> day.id to day.slots.map { slot -> slot.id to slot.sets } },
            )
            assertEquals(
                original.weeks.first().days.map { it.label },
                adapted.weeks.first().days.map { it.label },
            )
            // §10.2 acota «sin sustitución automática» al Original: la adaptación
            // nunca marca principales como de competición, así el resolver puede
            // sustituirlos con material declarado.
            assertTrue(
                "La adaptación permite sustitución en los principales",
                adapted.weeks.flatMap { it.days }.flatMap { it.slots }.none { it.isCompetitionLift },
            )
            // Y el original conserva su clase ORIGINAL.
            assertEquals(PlanProvenanceClass.ORIGINAL, original.provenance?.category)
        }
    }

    @Test
    fun authored_slots_are_attributed_to_the_author_and_keep_lift_slots_empty() {
        AuthoredPhulPhatRecipes.all.forEach { recipe ->
            assertTrue("${recipe.id} sin exenciones de origen", recipe.exemptions.all { it.scope != "*" })
            recipe.weeks.forEach { week ->
                week.days.forEach { day ->
                    day.slots.forEach { slot ->
                        assertEquals("${recipe.id}/${day.id}/${slot.id}", SlotSource.AUTHOR, slot.source)
                        assertEquals("§14.3: sin mapa SBD genérico", null, slot.lift.liftSlot)
                    }
                }
            }
            assertTrue("§14.3 liftSlots vacío", recipe.liftSlots.isEmpty())
        }
    }

    // ─── E-11 / E-32: autores completos y planes propios sin URL ──────────────

    private val visibleProtocols get() = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }

    private val kpknOwnIds = listOf(
        "kpkn-native-sbd-4",
        "kpkn-ppl-6",
        "kpkn-rp-style",
        "kpkn-rts-style",
        "kpkn-sbs-rtf",
    )

    @Test
    fun no_visible_author_has_a_slash_or_a_section_mark() {
        val authors = visibleProtocols.map { it.id to it.author } +
            PersonalizedPlanCatalog.entries().flatMap { entry ->
                listOfNotNull(entry.sourceAuthor, entry.provenance?.sourceAuthor, entry.authoredSource?.author)
                    .map { entry.id to it }
            }
        assertTrue("sin autores que revisar", authors.isNotEmpty())
        authors.forEach { (id, author) ->
            assertTrue("$id: autor vacío", author.isNotBlank())
            assertFalse("$id: el autor «$author» usa barra; los autores se unen con «y»", author.contains('/'))
            assertFalse("$id: el autor «$author» lleva un símbolo de sección", author.contains('§'))
        }
    }

    @Test
    fun third_party_methods_name_every_author_in_full_and_the_disclaimer_repeats_them() {
        val expected = mapOf(
            "texas-method-3d" to "Mark Rippetoe y Glenn Pendlay",
            "texas-method-4d" to "Andy Baker y Mark Rippetoe",
            "madcow-5x5" to "Madcow (a partir de Bill Starr)",
            // El id conserva la grafía «phillipi» porque está persistido; solo cambia el texto.
            "coan-phillipi-dl" to "Ed Coan y Mark Philippi",
            // H-06 (B.S6 parte 2b): el método es de Ernie Lilliebridge y su familia (no «Matt»); sin barra ni símbolo de sección.
            "lilliebridge" to "Ernie Lilliebridge (familia Lilliebridge)",
        )
        expected.forEach { (id, authors) ->
            val protocol = visibleProtocols.single { it.id == id }
            assertEquals("$id: autores", authors, protocol.author)
            assertEquals("$id: disclaimer", "No afiliado a $authors", protocol.source.disclaimer)
        }
        // E-32: el autor declarado y el disclaimer citan siempre a las mismas personas.
        visibleProtocols.filter { it.publicationStatus == ProtocolPublicationStatus.VERIFIED }.forEach { protocol ->
            assertTrue(
                "${protocol.id}: el disclaimer «${protocol.source.disclaimer}» no repite el autor «${protocol.author}»",
                protocol.source.disclaimer.orEmpty().contains(protocol.author),
            )
        }
    }

    @Test
    fun kpkn_own_plans_have_no_source_url_and_never_say_not_affiliated_to_kpkn() {
        kpknOwnIds.forEach { id ->
            val protocol = visibleProtocols.single { it.id == id }
            assertEquals("$id: estado", ProtocolPublicationStatus.KPKN_NATIVE, protocol.publicationStatus)
            assertNull("$id: un plan propio no tiene página de fuente", protocol.source.primaryUrl)
            assertNull("$id: ni una evidencia con URL", protocol.source.evidenceUrl)
            val disclaimer = protocol.source.disclaimer.orEmpty()
            assertTrue("$id: «$disclaimer» no empieza por «$KPKN_OWN_PLAN_DISCLAIMER»", disclaimer.startsWith(KPKN_OWN_PLAN_DISCLAIMER))
            assertFalse("$id: «$disclaimer» afilia a KPKN", disclaimer.contains("No afiliado a KPKN", ignoreCase = true))
            // El catálogo copia la fuente de la definición: tampoco ahí queda una URL ni la leyenda.
            val entry = requireNotNull(PersonalizedPlanCatalog.find("protocol:$id")) { "Falta la entrada de $id" }
            assertNull("$id: sourceUrl del catálogo", entry.sourceUrl)
            assertFalse(
                "$id: el disclaimer del catálogo afilia a KPKN",
                entry.disclaimer.orEmpty().contains("No afiliado a KPKN", ignoreCase = true),
            )
        }
    }

    @Test
    fun no_visible_protocol_links_to_a_kpkn_fit_source_page() {
        visibleProtocols.forEach { protocol ->
            listOfNotNull(protocol.source.primaryUrl, protocol.source.evidenceUrl).forEach { url ->
                assertFalse("${protocol.id}: la URL «$url» apunta a kpkn.fit", url.contains("kpkn.fit", ignoreCase = true))
            }
        }
    }

    @Test
    fun authored_source_texts_shown_to_the_user_carry_no_section_marks() {
        listOf(AuthoredSources.phul, AuthoredSources.phat).forEach { source ->
            (source.effectiveRules + source.kpknDefaults + listOf(source.sourceTitle, source.edition)).forEach { text ->
                assertFalse("${source.planId}: «$text» lleva un símbolo de sección", text.contains('§'))
            }
        }
    }
}
