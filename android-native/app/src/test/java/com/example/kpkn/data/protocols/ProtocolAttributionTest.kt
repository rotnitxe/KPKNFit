package com.example.kpkn.data.protocols

import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.definitions.AuthoredSources
import org.junit.Assert.assertEquals
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
}
