package com.example.kpkn.domain.training

import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import org.junit.Test

/** Sonda de diagnóstico T-004: tabla real de entradas publicadas (referencias × frecuencias). */
class T004EntryCoverageProbeTest {

    @Test
    fun `tabla de entradas publicadas por referencia y frecuencia`() {
        val entries = PersonalizedPlanCatalog.entries().filter { it.publication.name == "PUBLISHED" }
        println("[T-004 PROBE] entradas publicadas=${entries.size}")
        entries.forEach { entry ->
            println(
                "[T-004 PROBE] id=${entry.id} source=${entry.source} freq=${entry.supportedFrequencies} " +
                    "refs=${entry.references} level=${entry.level} adap=${entry.adaptation} focuses=${entry.supportedFocuses.size}",
            )
        }
        val byRef = entries.groupBy { it.references }
        println("[T-004 PROBE] cobertura por referencia:")
        listOf("POWERLIFTING", "HYPERTROPHY", "POWERBUILDING").forEach { ref ->
            val supporting = entries.filter { ref in it.references.map { r -> r.name } }
            val freqs = supporting.flatMap { it.supportedFrequencies.toList() }.toSortedSet()
            println("[T-004 PROBE] ref=$ref entries=${supporting.size} frecuencias=$freqs")
        }
    }
}
