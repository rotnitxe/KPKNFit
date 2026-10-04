package com.example.kpkn.data.protocols

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matriz 2.2: las únicas exenciones permitidas en protocolos visibles.
 * Cualquier regla extra (p. ej. W2 de hipertrofia sobre un ciclo PL) es un desvío.
 *
 * B.S6 parte 1: se retiraron las 26 exenciones muertas (las que no silenciaban ningún hallazgo) y la matriz queda ajustada a lo
 * vivo: nSuns declara el rango C1 por slot (T1 de 9 series), Smolov la C4 de las semanas 9 a 13 (la fase intensa son 3 sesiones por
 * semana) además de sus H6, Coan solo la H2, Korte solo la H5b y PHAT heredado la H3. Sheiko, Smolov Jr, Westside y PHUL heredado
 * ya no declaran ninguna.
 */
class ProtocolExemptionMatrixTest {
    private val allowedByProtocol = mapOf(
        "nsuns-531-lp-4d" to setOf("C1_SET_RANGE"),
        "smolov" to setOf("H6", "C4_CLAIMED_DAYS"),
        "coan-phillipi-dl" to setOf("H2"),
        "korte-3x3" to setOf("H5b"),
        "phat-verified" to setOf("H3"),
    )

    @Test
    fun visible_exemptions_match_matrix_2_2() {
        val failures = mutableListOf<String>()
        PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.forEach { protocol ->
            val declared = (protocol.exemptions + (protocol.recipe?.exemptions ?: emptyList()))
                .map { it.rule }
                .toSet()
            val allowed = allowedByProtocol[protocol.id].orEmpty()
            if (protocol.publicationStatus == ProtocolPublicationStatus.KPKN_NATIVE && declared.isNotEmpty()) {
                failures += "${protocol.id}: KPKN_NATIVE no puede declarar $declared"
            }
            val extra = declared - allowed
            if (extra.isNotEmpty()) {
                failures += "${protocol.id}: exenciones fuera de matriz 2.2 $extra (permitidas $allowed)"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun peak_protocols_do_not_use_wildcard_scope() {
        val peakGoals = setOf(
            com.example.kpkn.data.models.BlockGoal.PEAK,
            com.example.kpkn.data.models.BlockGoal.REALIZATION,
        )
        val failures = mutableListOf<String>()
        PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.forEach { protocol ->
            val hasPeak = protocol.recipe?.weeks?.any { it.blockGoal in peakGoals } == true
            if (!hasPeak) return@forEach
            val wild = (protocol.exemptions + (protocol.recipe?.exemptions ?: emptyList()))
                .filter { it.scope == "*" }
            if (wild.isNotEmpty()) {
                failures += "${protocol.id}: scope '*' en protocolo con pico: ${wild.map { it.rule }}"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
