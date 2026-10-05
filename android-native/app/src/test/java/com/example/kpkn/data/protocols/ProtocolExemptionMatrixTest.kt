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
 * ya no declaraban ninguna.
 *
 * B.S6 parte 2b: se declaran las exenciones del contrato de receta que quedan por diseño del método, todas por slot (C1 y C8) o sobre
 * la receta entera (C5) o el bloque (BLOCK), con su fuente: C1 (series de más o de menos por slot: Texas de 3 días, Sheiko, Smolov, Smolov Jr y PHAT
 * heredado), C5 (los métodos que terminan en el test o la competición: Sheiko, Jacked & Tan, Rippler, UHF 9, Coan, Korte, Cube y Calgary),
 * BLOCK (la ola de 3s de Juggernaut, tras medir su realización en %1RM) y C8 (el levantamiento de competición que cuelga del otro del mismo día: Texas de 3 y 4 días, nSuns y
 * Westside). Los planes propios de KPKN (KPKN_NATIVE) siguen sin poder declarar ninguna: corrigen el dato (RTS y SBS ganan una descarga).
 */
class ProtocolExemptionMatrixTest {
    private val allowedByProtocol = mapOf(
        "texas-method-3d" to setOf("C1_SET_RANGE", "C8_SUPPLEMENTAL_LINK"),
        "texas-method-4d" to setOf("C8_SUPPLEMENTAL_LINK"),
        "nsuns-531-lp-4d" to setOf("C1_SET_RANGE", "C8_SUPPLEMENTAL_LINK"),
        "westside-conjugate" to setOf("C8_SUPPLEMENTAL_LINK"),
        "juggernaut-2" to setOf("BLOCK"),
        "sheiko-29-32" to setOf("C1_SET_RANGE", "C5_DELOAD_REQUIRED"),
        "smolov" to setOf("H6", "C4_CLAIMED_DAYS", "C1_SET_RANGE"),
        "smolov-jr" to setOf("C1_SET_RANGE"),
        "gzcl-jt-2" to setOf("C5_DELOAD_REQUIRED"),
        "gzcl-rippler" to setOf("C5_DELOAD_REQUIRED"),
        "gzcl-uhf-9" to setOf("C5_DELOAD_REQUIRED"),
        "cube-method" to setOf("C5_DELOAD_REQUIRED"),
        "calgary-16" to setOf("C5_DELOAD_REQUIRED"),
        "coan-phillipi-dl" to setOf("H2", "C5_DELOAD_REQUIRED"),
        "korte-3x3" to setOf("H5b", "C5_DELOAD_REQUIRED"),
        "phat-verified" to setOf("H3", "C1_SET_RANGE"),
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
