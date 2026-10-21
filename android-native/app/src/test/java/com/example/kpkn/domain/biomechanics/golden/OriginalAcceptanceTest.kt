package com.example.kpkn.domain.biomechanics.golden

import org.junit.Assert.*
import org.junit.Test

class OriginalAcceptanceTest {
    private fun samples(vararg values: Double) = values.map { OriginalAcceptance.Sample("synthetic", "CON", "left", it) }
    @Test fun exactCountsUseEligibleDenominatorAndAbstentionsStayVisible() {
        val rows = List(19) { OriginalAcceptance.Count("A", 8, 8, true) } + OriginalAcceptance.Count("A", 8, null, false)
        val c = OriginalAcceptance.counts(rows)
        assertEquals(20, c.eligible); assertEquals(19, c.outputs); assertEquals(1, c.abstentions)
        assertEquals(1.0, c.conditionalExact!!, 0.0); assertEquals(.95, c.eligibleExact!!, 0.0)
        assertEquals(OriginalAcceptance.Status.PASS, OriginalAcceptance.tierStatus(rows, "A"))
        assertEquals(OriginalAcceptance.Status.FAIL, OriginalAcceptance.tierStatus(rows + OriginalAcceptance.Count("A", 8, null, false), "A"))
    }
    @Test fun pointFullAndAltaCannotUseAnAcceptableRange() {
        val row = OriginalAcceptance.Count("B", 8, 9, true) // within [7,9], still not exact
        assertEquals(0, OriginalAcceptance.counts(listOf(row)).exactFull)
        assertEquals(OriginalAcceptance.Status.FAIL, OriginalAcceptance.altaStatus(listOf(row)))
        assertEquals(OriginalAcceptance.Status.NOT_VALIDATED, OriginalAcceptance.altaStatus(emptyList()))
        assertEquals(OriginalAcceptance.Status.NOT_VALIDATED, OriginalAcceptance.tierStatus(emptyList(), "A"))
        val severe = List(99) { OriginalAcceptance.Count("A", 8, 8, true) } + OriginalAcceptance.Count("A", 8, 10, false)
        assertEquals(OriginalAcceptance.Status.FAIL, OriginalAcceptance.tierStatus(severe, "A"))
    }
    @Test fun thresholdsAreInclusiveAndBUsesNinetyPercent() {
        val b = List(9) { OriginalAcceptance.Count("B", 8, 8, true) } + OriginalAcceptance.Count("B", 8, 9, false)
        assertEquals(OriginalAcceptance.Status.PASS, OriginalAcceptance.tierStatus(b, "B"))
        val alta = List(49) { OriginalAcceptance.Count("A", 8, 8, true) } + OriginalAcceptance.Count("A", 8, 9, true)
        assertEquals(OriginalAcceptance.Status.PASS, OriginalAcceptance.altaStatus(alta))
        assertEquals(OriginalAcceptance.Status.PASS, OriginalAcceptance.temporal(samples(100.0), samples(150.0), false).status)
        assertEquals(OriginalAcceptance.Status.FAIL, OriginalAcceptance.temporal(samples(100.0), samples(150.1), false).status)
    }
    @Test fun absoluteAgreementPenalizesConstantBias() {
        val x = listOf(100.0, 200.0, 300.0)
        assertEquals(1.0, OriginalAcceptance.iccA1(x, x)!!, 1e-12)
        assertEquals(2.0 / 3, OriginalAcceptance.iccA1(x, x.map { it + 100 })!!, 1e-12)
        assertEquals(OriginalAcceptance.Status.FAIL, OriginalAcceptance.temporal(samples(100.0, 200.0, 300.0), samples(200.0, 300.0, 400.0), true).status)
        assertEquals(OriginalAcceptance.Status.PASS, OriginalAcceptance.temporal(samples(100.0, 200.0, 300.0), samples(100.0, 200.0, 300.0), true).status)
    }
    @Test fun nullShortConstantAndNonFiniteNeverValidateIcc() {
        assertNull(OriginalAcceptance.iccA1(listOf(1.0, 2.0), listOf(1.0, 2.0)))
        assertNull(OriginalAcceptance.iccA1(listOf(1.0, 1.0, 1.0), listOf(1.0, 2.0, 3.0)))
        assertNull(OriginalAcceptance.iccA1(listOf(1.0, 2.0, Double.NaN), listOf(1.0, 2.0, 3.0)))
        assertEquals(OriginalAcceptance.Status.NOT_VALIDATED, OriginalAcceptance.temporal(null, samples(100.0), true).status)
        assertEquals(OriginalAcceptance.Status.NOT_VALIDATED, OriginalAcceptance.temporal(emptyList(), emptyList(), false).status)
        assertEquals(OriginalAcceptance.Status.NOT_VALIDATED, OriginalAcceptance.temporal(samples(100.0), samples(Double.POSITIVE_INFINITY), true).status)
    }
    @Test fun missingAndExtraRemainInCountsAndBlockEvenPerfectMatchedPairs() {
        val r = OriginalAcceptance.temporal(samples(100.0, 200.0, 300.0), samples(100.0, 300.0), false)
        assertEquals(3, r.expected); assertEquals(2, r.paired); assertEquals(1, r.missing)
        assertEquals(0.0, r.maeMs!!, 0.0); assertEquals(OriginalAcceptance.Status.FAIL, r.status)
        val extra = OriginalAcceptance.temporal(samples(100.0), samples(100.0, 200.0), false)
        assertEquals(1, extra.extras); assertEquals(OriginalAcceptance.Status.FAIL, extra.status)
    }
    @Test fun matcherIsMonotonicSideAwareAndNeverReusesAnEvent() {
        val x = samples(100.0, 200.0)
        val wrongSide = x.map { it.copy(side = "right") }
        assertEquals(0, OriginalAcceptance.match(x, wrongSide).pairs.size)
        assertEquals(0, OriginalAcceptance.match(x, x.map { it.copy(side = null) }).pairs.size)
        assertEquals(0, OriginalAcceptance.match(x, x.map { it.copy(clip = "other") }).pairs.size)
        val a = OriginalAcceptance.match(x, samples(101.0))
        assertEquals(listOf(0 to 0), a.pairs)
        val crossed = listOf(x[0].copy(key = "EXC"), x[1])
        val pairs = OriginalAcceptance.match(crossed, crossed.reversed()).pairs
        assertEquals(1, pairs.size)
    }
    @Test fun clipsMayHaveDifferentGlobalOrderWithoutCrossingOrReusing() {
        val clipA = samples(100.0, 200.0).map { it.copy(clip = "a") }
        val clipB = samples(110.0, 210.0).map { it.copy(clip = "b") }
        val expected = clipA + clipB
        val detected = clipB + clipA
        val alignment = OriginalAcceptance.match(expected, detected)
        assertEquals(listOf(0 to 2, 1 to 3, 2 to 0, 3 to 1), alignment.pairs)
        assertEquals(0, alignment.missing); assertEquals(0, alignment.extras)
        assertEquals(4, alignment.pairs.map { it.first }.toSet().size)
        assertEquals(4, alignment.pairs.map { it.second }.toSet().size)
        assertTrue(alignment.pairs.all { expected[it.first].clip == detected[it.second].clip })
        for (clip in listOf("a", "b")) {
            val pairs = alignment.pairs.filter { expected[it.first].clip == clip }
            assertTrue(pairs.zipWithNext().all { (a, b) -> a.first < b.first && a.second < b.second })
        }
    }
    @Test fun fullAcceptanceRequiresEveryIndependentMeasurement() {
        assertEquals(OriginalAcceptance.Status.NOT_VALIDATED,
            OriginalAcceptance.complete(OriginalAcceptance.Status.PASS, OriginalAcceptance.Status.NOT_VALIDATED))
    }
}
