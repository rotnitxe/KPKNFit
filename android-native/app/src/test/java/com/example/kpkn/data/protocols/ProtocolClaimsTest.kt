package com.example.kpkn.data.protocols

import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolClaimsTest {
    @Test
    fun description_and_tags_match_recipe() {
        PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.forEach { protocol ->
            val recipe = protocol.recipe!!
            val blob = (protocol.description + " " + protocol.tags.joinToString(" ")).lowercase()
            if (blob.contains("amrap")) {
                assertTrue("${protocol.id} menciona AMRAP sin series AMRAP", recipe.weeks.any { week ->
                    week.days.any { day -> day.slots.any { slot -> slot.sets.any { it.amrap } } }
                })
            }
            if (Regex("""\brpe\b""").containsMatchIn(blob)) {
                assertTrue("${protocol.id} menciona RPE sin RPE en receta", recipe.weeks.any { week ->
                    week.days.any { day -> day.slots.any { slot -> slot.sets.any { it.rpe != null } } }
                })
            }
            if (blob.contains("%")) {
                assertTrue("${protocol.id} menciona % sin porcentajes", recipe.weeks.any { week ->
                    week.days.any { day -> day.slots.any { slot -> slot.sets.any { it.percent != null } } }
                })
            }
            val dayClaim = Regex("""(\d+)\s*días""").find(blob)
            if (dayClaim != null) {
                assertTrue(
                    "${protocol.id} promete ${dayClaim.groupValues[1]} días vs ${recipe.daysPerWeek}",
                    dayClaim.groupValues[1].toInt() == recipe.daysPerWeek,
                )
            }
            val weekClaim = Regex("""(\d+)\s*semanas""").find(blob)
            if (weekClaim != null) {
                assertTrue(
                    "${protocol.id} promete ${weekClaim.groupValues[1]} semanas vs ${recipe.weeks.size}",
                    weekClaim.groupValues[1].toInt() == recipe.weeks.size,
                )
            }
            recipe.claimedLevel?.let { level ->
                assertTrue("${protocol.id} nivel $level no está en tags/descripcion", blob.contains(level.lowercase()))
            }
        }
    }
}
