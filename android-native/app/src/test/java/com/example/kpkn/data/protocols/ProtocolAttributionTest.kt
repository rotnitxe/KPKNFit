package com.example.kpkn.data.protocols

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
}
