package com.example.kpkn.domain.biomechanics.golden

import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit selection only. It must never run as part of historical tuning diagnostics. */
class OriginalContractAcceptanceTest {
    @Test fun reservedCorpusMustMeetOriginalContract() {
        // Place before load(): default suite never opens the reserve through this new test.
        assumeTrue("Select explicitly after engine frozen and independent labels ready",
            System.getProperty("kpkn.golden.originalAcceptance") == "true")
        val corpus = GoldenReport.load()
        val results = GoldenReport.finalHoldout(corpus)
        // Freeze eligibility from labels/protocol, NEVER from output, confidence or success.
        // Zero FULL is a valid target; do not inherit the historical zero_range exclusion.
        val eligible = results.filter { r ->
            r.label.usable && !r.label.uncertain && !r.fixture.preexistingSmoke &&
                r.profile.tier.name in setOf("A", "B") && "negative_back_view" !in r.exclusions
        }
        val rows = eligible.map { r -> OriginalAcceptance.Count(r.profile.tier.name, r.label.full,
            r.report.repCount.takeIf { r.hasOutput }, r.report.confidence == CountConfidence.ALTA) }
        val a = OriginalAcceptance.tierStatus(rows, "A")
        val b = OriginalAcceptance.tierStatus(rows, "B")
        val high = OriginalAcceptance.altaStatus(rows)
        // Current labels contain turns, NOT independently annotated precise phase boundaries/durations.
        // Deliberately unavailable; do not construct reference phases from the engine or turn midpoints.
        val events = OriginalAcceptance.temporal(null, emptyList(), phaseIcc = false)
        val con = OriginalAcceptance.temporal(null, emptyList(), phaseIcc = true)
        val exc = OriginalAcceptance.temporal(null, emptyList(), phaseIcc = true)
        // Other original contract axes also lack independent truth in this adapter.
        val depth = OriginalAcceptance.Status.NOT_VALIDATED
        val view = OriginalAcceptance.Status.NOT_VALIDATED
        val plate = OriginalAcceptance.Status.NOT_VALIDATED
        // This adapter uses the already exposed historical reserve. Independence cannot be certified here.
        // A new sealed manifest and reviewed adapter are required before that status can change.
        val independence = OriginalAcceptance.Status.NOT_VALIDATED
        val status = OriginalAcceptance.complete(a, b, high, events.status, con.status, exc.status, depth, view, plate, independence)
        val report = buildString {
            appendLine("REGRESION HISTORICA / NO BLINDA. Original contract metrics: $status.")
            appendLine("Independence=$independence; historical exposed reserve cannot certify blind acceptance.")
            appendLine("Exact FULL by tier, all eligible series denominator.")
            appendLine("Evaluated=${results.size}; eligible=${eligible.size}; excluded=${results.size - eligible.size}")
            for (tier in listOf("A", "B")) {
                val c = OriginalAcceptance.counts(rows.filter { it.tier == tier })
                appendLine("$tier: $c; conditional=${c.conditionalExact}; eligible=${c.eligibleExact}; coverage=${c.coverage}")
            }
            val c = OriginalAcceptance.counts(rows)
            appendLine("ALTA: exact=${c.altaExact}/${c.alta}; precision=${c.altaPrecision}; coverage=${c.alta}/${c.eligible} (${c.altaCoverage}); status=$high")
            for (r in results.filter { it !in eligible }) appendLine("Excluded ${r.fixture.id}: usable=${r.label.usable}, uncertain=${r.label.uncertain}, tier=${r.profile.tier}, historicalReasons=${r.exclusions}")
            appendLine("Events=$events; CON=$con; EXC=$exc")
            appendLine("Depth=$depth; view=$view; plate=$plate; physical m/s pending separate phone validation.")
            appendLine("Historical regression only. A new sealed manifest and independent evidence must be wired, and eligibility reviewed, before blind acceptance can be assessed.")
        }
        GoldenReport.publish("GOLDEN_ORIGINAL_CONTRACT.md", report)
        assertEquals(report, OriginalAcceptance.Status.PASS, status)
    }
}
