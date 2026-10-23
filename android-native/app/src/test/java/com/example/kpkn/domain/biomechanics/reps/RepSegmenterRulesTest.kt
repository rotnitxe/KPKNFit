package com.example.kpkn.domain.biomechanics.reps

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.metrics.PartialKind
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

/**
 * Las reglas del segmentador una a una, sobre señales hechas a mano (tramos de jerk mínimo entre niveles): así se prueban
 * casos que el generador de poses no da limpios (la sentadilla no es lineal en el ángulo de rodilla) y se fija cada umbral.
 * Todas llevan reposo = 0, una repetición completa = 1,0 y 30 muestras por segundo.
 */
class RepSegmenterRulesTest {
    private val eccentric: AnalysisProfile = RepTestSupport.eccentricProfile()
    private val concentric: AnalysisProfile = RepTestSupport.concentricProfile()

    private var lastTrace = ""

    private fun segment(series: CountSeries, profile: AnalysisProfile, hints: Set<String> = emptySet()): SegmentationResult {
        val (seg, trace) = RepTestSupport.segmentTraced(series, profile, hints)
        lastTrace = trace
        return seg
    }

    /** El resumen del resultado y, tras él, los números con los que decidió el segmentador en la última llamada. */
    private fun describe(seg: SegmentationResult) = RepTestSupport.summary(seg) + "\n   " + lastTrace

    private fun fiveReps(): SignalBuilder {
        val b = SignalBuilder().hold(2.0)
        repeat(5) { b.rep() }
        return b.hold(1.5)
    }

    // ---- pasos 1 a 5: clases por recorrido ----

    @Test
    fun theAmplitudeThresholdsSplitFullPartialAndNoise() {
        val b = SignalBuilder().hold(2.0)
        for (a in listOf(1.0, 1.0, 1.0, 1.0, 0.8, 0.6, 0.45)) b.rep(a)
        b.rep(0.2).hold(1.5)
        val seg = segment(b.series(), eccentric)
        val partialKinds = seg.cycles.filter { it.kind == RepKind.PARTIAL }.map { it.partialKind }
        assertEquals(describe(seg), Triple(5, 2, 0), RepTestSupport.counts(seg))
        assertEquals(describe(seg), listOf(PartialKind.REST_END, PartialKind.REST_END), partialKinds)
    }

    @Test
    fun partialsThatReachTheFarEndAreFarEndAndTheShortOnesAreRestEnd() {
        val fails = RepTestSupport.Failures()
        // Rebotes entre el 45 % y el 100 % tras cinco completas.
        val bounce = SignalBuilder().hold(2.0)
        repeat(5) { bounce.rep() }
        bounce.go(1.5, 1.0).go(1.5, 0.45).go(1.5, 1.0).go(1.5, 0.45).go(1.5, 1.0).go(1.5, 0.0).hold(2.0)
        val a = segment(bounce.series(), eccentric)
        fails.check(RepTestSupport.counts(a) == Triple(5, 3, 0)) { "rebotes: ${describe(a)}" }
        fails.check(a.cycles.filter { it.kind == RepKind.PARTIAL }.all { it.partialKind == PartialKind.FAR_END }) { "rebotes: ${describe(a)}" }
        fails.check(a.cycles.filter { it.kind == RepKind.PARTIAL }.all { it.amplitudeFraction in 0.5..0.6 }) {
            "rebotes: a = ${a.cycles.map { it.amplitudeFraction }}"
        }
        // Medias repeticiones desde el reposo.
        val half = SignalBuilder().hold(2.0)
        repeat(5) { half.rep() }
        repeat(3) { half.go(1.0, 0.55).go(1.0, 0.0) }
        half.hold(2.0)
        val b = segment(half.series(), eccentric)
        fails.check(RepTestSupport.counts(b) == Triple(5, 3, 0)) { "medias: ${describe(b)}" }
        fails.check(b.cycles.filter { it.kind == RepKind.PARTIAL }.all { it.partialKind == PartialKind.REST_END }) { "medias: ${describe(b)}" }
        fails.assertNone()
    }

    @Test
    fun concentricStartFailureNeedsASlowRiseAndAStall() {
        val fails = RepTestSupport.Failures()
        fun series(tail: SignalBuilder.() -> Unit): CountSeries {
            val b = SignalBuilder().hold(2.0)
            repeat(4) { b.rep() }
            b.tail()
            return b.hold(2.0).series()
        }
        // Sube a la mitad despacio (3 s frente a 1,5 s), se queda 0,8 s y baja: FAILED.
        val failed = segment(series { go(3.0, 0.5).hold(0.8).go(1.5, 0.0).hold(0.5) }, concentric)
        fails.check(RepTestSupport.counts(failed) == Triple(4, 0, 1)) { "intento fallido: ${describe(failed)}" }
        // La misma mitad a velocidad normal y sin pararse: una parcial.
        val fast = segment(series { go(1.5, 0.5).go(1.5, 0.0).hold(0.5) }, concentric)
        fails.check(RepTestSupport.counts(fast) == Triple(4, 1, 0)) { "media repetición rápida: ${describe(fast)}" }
        // Despacio pero sin parar: tampoco es un fallo.
        val slow = segment(series { go(3.0, 0.5).go(1.5, 0.0).hold(0.5) }, concentric)
        fails.check(RepTestSupport.counts(slow) == Triple(4, 1, 0)) { "media repetición lenta: ${describe(slow)}" }
        // Parada larga pero a velocidad normal: tampoco.
        val paused = segment(series { go(1.5, 0.5).hold(0.8).go(1.5, 0.0).hold(0.5) }, concentric)
        fails.check(RepTestSupport.counts(paused) == Triple(4, 1, 0)) { "media repetición con pausa: ${describe(paused)}" }
        fails.assertNone()
    }

    @Test
    fun eccentricStartFailureIsOnlySeenAtTheEndAndTheSetDownIsIgnored() {
        val fails = RepTestSupport.Failures()
        fun series(tail: SignalBuilder.() -> Unit): CountSeries {
            val b = SignalBuilder().hold(2.0)
            repeat(4) { b.rep() }
            b.tail()
            return b.series()
        }
        // Baja entera, intenta subir a la mitad, se queda 0,8 s, vuelve a bajar y se recupera.
        val failed = segment(series { go(1.5, 1.0).hold(0.5).go(3.0, 0.5).hold(0.8).go(1.5, 1.0).go(1.0, 0.0).hold(2.0) }, eccentric)
        fails.check(RepTestSupport.counts(failed) == Triple(4, 0, 1)) { "fallo con dejada: ${describe(failed)}" }
        fails.check(failed.discarded.any { it.reason == "setdown" }) { "fallo con dejada: ${describe(failed)}" }
        // La serie acaba mientras el intento está parado.
        val stalled = segment(series { go(1.5, 1.0).hold(0.5).go(2.0, 0.6).hold(1.5) }, eccentric)
        fails.check(RepTestSupport.counts(stalled) == Triple(4, 0, 1)) { "fallo al final de la serie: ${describe(stalled)}" }
        // La serie acaba abajo sin intento de subir: se dejó la barra; no es repetición ni fallo. Acabe con un descenso mínimo
        // o en una meseta, el último ciclo se ve como pico gracias a la bajada virtual del final y se descarta como «setdown».
        val minimalDescent: SignalBuilder.() -> Unit = { go(1.5, 1.0).hold(1.0).go(0.5, 0.9) }
        val plateau: SignalBuilder.() -> Unit = { go(1.5, 1.0).hold(1.5) }
        for ((label, tail) in listOf("con un descenso mínimo" to minimalDescent, "en una meseta" to plateau)) {
            val bottom = segment(series(tail), eccentric)
            fails.check(RepTestSupport.counts(bottom) == Triple(4, 0, 0)) { "serie que acaba abajo $label: ${describe(bottom)}" }
            fails.check(bottom.discarded.any { it.reason == "setdown" }) { "serie que acaba abajo $label: ${describe(bottom)}" }
        }
        fails.assertNone()
    }

    @Test
    fun theLastConcentricRepCountsWhenTheSeriesEndsAtLockout() {
        val b = SignalBuilder().hold(2.0)
        repeat(4) { b.rep() }
        b.go(1.5, 1.0).hold(1.5).go(0.5, 0.95)
        val seg = segment(b.series(), concentric)
        assertEquals(describe(seg), Triple(5, 0, 0), RepTestSupport.counts(seg))
        // Lo mismo si la serie acaba en una meseta en el bloqueo.
        val plateau = SignalBuilder().hold(2.0)
        repeat(4) { plateau.rep() }
        plateau.go(1.5, 1.0).hold(1.5)
        val segPlateau = segment(plateau.series(), concentric)
        assertEquals(describe(segPlateau), Triple(5, 0, 0), RepTestSupport.counts(segPlateau))
        // Y una subida a medias que acaba la serie no es una repetición.
        val half = SignalBuilder().hold(2.0)
        repeat(4) { half.rep() }
        half.go(1.5, 0.5).hold(1.0).go(0.5, 0.45)
        val seg2 = segment(half.series(), concentric)
        assertEquals(describe(seg2), 4, seg2.fullCount)
        assertEquals(describe(seg2), 0, seg2.failedCount)
    }

    // ---- paso 5: no-repeticiones ----

    @Test
    fun walkingOverACycleDiscardsIt() {
        val series = fiveReps()
        val n = series.size
        val walk = BooleanArray(n)
        for (k in 300 until 420) walk[k] = true // la tercera repetición: de 10 s a 14 s
        val seg = segment(series.series(walk = walk), eccentric)
        assertEquals(describe(seg), 4, seg.fullCount)
        assertEquals(describe(seg), listOf("nonrep_walk"), seg.discarded.map { it.reason })
        // Un solape pequeño (< 20 % del ciclo) no descarta.
        val brief = BooleanArray(n)
        for (k in 300 until 320) brief[k] = true
        assertEquals(5, segment(series.series(walk = brief), eccentric).fullCount)
    }

    @Test
    fun aConfirmedPlateThatTravelsSidewaysIsNotARepInAVerticalProfile() {
        val fails = RepTestSupport.Failures()
        val b = SignalBuilder().hold(2.0)
        repeat(5) { b.rep(0.3) }
        b.hold(1.5)
        val r = b.values()
        // La barra sube y baja con la señal, y en la segunda repetición (de 6 s a 10 s) se desplaza 0,8 BH hacia un lado.
        val barX = DoubleArray(r.size)
        for (k in r.indices) {
            val t = k / 30.0
            barX[k] = if (t < 6.0) 0.0 else if (t < 10.0) 0.8 * ((t - 6.0) / 4.0) else 0.8
        }
        val squat = AnalysisProfiles.of(AnalysisProfileId.SQUAT).copy(minAmplitude = 0.1)
        val vertical = segment(b.series(raw = r, source = CountSeriesBuilder.SOURCE_PLATE, barX = barX, barY = r.copyOf()), squat)
        fails.check(vertical.fullCount == 4 && vertical.discarded.any { it.reason == "nonrep_horizontal" }) { "perfil vertical: ${describe(vertical)}" }
        val poseVertical = segment(b.series(raw = r, source = CountSeriesBuilder.SOURCE_POSE, barX = barX, barY = r.copyOf()), squat)
        fails.check(poseVertical.fullCount == 4 && poseVertical.discarded.any { it.reason == "nonrep_horizontal" }) {
            "sentadilla con proxy de pose: ${describe(poseVertical)}"
        }
        val bench = AnalysisProfiles.of(AnalysisProfileId.BENCH).copy(minAmplitude = 0.1)
        val poseHand = segment(b.series(raw = r, source = CountSeriesBuilder.SOURCE_POSE, barX = barX, barY = r.copyOf()), bench)
        fails.check(poseHand.fullCount == 4 && poseHand.discarded.any { it.reason == "nonrep_horizontal" }) {
            "press banca con proxy de mano: ${describe(poseHand)}"
        }
        // El remo no es un perfil vertical: la barra se mueve en diagonal y no se descarta.
        val row = AnalysisProfiles.of(AnalysisProfileId.ROW).copy(minAmplitude = 0.1, perSide = false)
        val rowResult = segment(b.series(raw = r, source = CountSeriesBuilder.SOURCE_PLATE, barX = barX, barY = r.copyOf()), row)
        fails.check(rowResult.fullCount == 5) { "remo: ${describe(rowResult)}" }
        // Sin punto de barra (sin escala o sin regla) no se descarta nada.
        fails.check(segment(b.series(raw = r, source = CountSeriesBuilder.SOURCE_PLATE), squat).fullCount == 5) { "sin punto de barra" }
        fails.assertNone()
    }

    @Test
    fun aLateralShoulderProxyInPoseLungeDoesNotVetoAFullRep() {
        val b = SignalBuilder().hold(2.0)
        repeat(5) { b.rep(0.3) }
        b.hold(1.5)
        val r = b.values()
        val shoulderX = DoubleArray(r.size) { k ->
            val t = k / 30.0
            if (t < 6.0) 0.0 else if (t < 10.0) 0.8 * ((t - 6.0) / 4.0) else 0.8
        }
        val lunge = AnalysisProfiles.of(AnalysisProfileId.LUNGE)
        val pose = segment(b.series(raw = r, source = CountSeriesBuilder.SOURCE_POSE,
            barX = shoulderX, barY = r.copyOf()), lunge)
        assertEquals(describe(pose), 5, pose.fullCount)
        assertTrue(describe(pose), pose.discarded.none { it.reason == "nonrep_horizontal" })

        // La misma geometría sí se descarta cuando procede del disco; una trayectoria vertical del disco se conserva.
        val plateSideways = segment(b.series(raw = r, source = CountSeriesBuilder.SOURCE_PLATE,
            barX = shoulderX, barY = r.copyOf()), lunge)
        assertEquals(describe(plateSideways), 4, plateSideways.fullCount)
        assertTrue(describe(plateSideways), plateSideways.discarded.any { it.reason == "nonrep_horizontal" })
        val plateVertical = segment(b.series(raw = r, source = CountSeriesBuilder.SOURCE_PLATE,
            barX = DoubleArray(r.size), barY = r.copyOf()), lunge)
        assertEquals(describe(plateVertical), 5, plateVertical.fullCount)
    }

    @Test
    fun anAlreadyAscendingInitialLungePeakBeforeALongPauseIsNotASecondFullRep() {
        val b = SignalBuilder(start = 0.075).go(1.4, 0.3).hold(0.5).go(1.5, 0.0).hold(8.0)
            .rep(0.3).hold(1.5)
        val r = b.values()
        val shoulderX = DoubleArray(r.size) { k -> 0.8 * (k / 30.0 / 1.4).coerceIn(0.0, 1.0) }
        val lunge = AnalysisProfiles.of(AnalysisProfileId.LUNGE)
        val seg = segment(b.series(raw = r, source = CountSeriesBuilder.SOURCE_POSE,
            barX = shoulderX, barY = r.copyOf()), lunge)
        assertEquals(describe(seg), 1, seg.fullCount)
        assertTrue(describe(seg), seg.discarded.any { it.reason == "nonrep_horizontal" })
    }

    @Test
    fun aTurnInsideAFilledGapIsMarked() {
        val series = fiveReps()
        val filled = BooleanArray(series.size)
        for (k in 226..240) filled[k] = true // el giro de la segunda repetición, a los 7,75 s
        val seg = segment(series.series(filled = filled), eccentric)
        assertEquals(describe(seg), 5, seg.fullCount)
        assertEquals(describe(seg), listOf(false, true, false, false, false), seg.cycles.map { it.turnInGap })
        assertTrue(describe(seg), seg.ambiguousCycles >= 1)
        assertEquals(0, segment(fiveReps().series(), eccentric).ambiguousCycles)
    }

    // ---- paso 6: ventana activa ----

    @Test
    fun partialsBeforeAndAfterTheWindowFollowTheHints() {
        val fails = RepTestSupport.Failures()
        fun half(b: SignalBuilder) = b.go(1.0, 0.55).go(1.0, 0.0)
        val withSetup = SignalBuilder().hold(1.0)
        half(withSetup).hold(8.0)
        repeat(5) { withSetup.rep() }
        withSetup.hold(10.0)
        half(withSetup).hold(1.5)
        val a = segment(withSetup.series(), eccentric)
        fails.check(RepTestSupport.counts(a) == Triple(5, 0, 0)) { "preparación y re-rack: ${describe(a)}" }
        fails.check(a.discarded.map { it.reason }.sorted() == listOf("rerack", "setup")) { "preparación y re-rack: ${describe(a)}" }

        val adjacentBefore = SignalBuilder().hold(2.0)
        half(adjacentBefore)
        repeat(5) { adjacentBefore.rep() }
        adjacentBefore.hold(1.5)
        fails.check(segment(adjacentBefore.series(), eccentric).partialCount == 0) { "parcial pegado antes, sin pista" }
        fails.check(segment(adjacentBefore.series(), eccentric, setOf("PARTIALS")).partialCount == 1) { "parcial pegado antes, con PARTIALS" }

        val adjacentAfter = SignalBuilder().hold(2.0)
        repeat(5) { adjacentAfter.rep() }
        half(adjacentAfter)
        half(adjacentAfter).hold(1.5)
        fails.check(RepTestSupport.counts(segment(adjacentAfter.series(), eccentric)) == Triple(5, 2, 0)) { "parciales pegados después" }

        val farAfter = SignalBuilder().hold(2.0)
        repeat(5) { farAfter.rep() }
        farAfter.hold(12.0)
        half(farAfter).hold(1.5)
        fails.check(RepTestSupport.counts(segment(farAfter.series(), eccentric)) == Triple(5, 0, 0)) { "parcial tras 12 s sin pista" }
        fails.check(RepTestSupport.counts(segment(farAfter.series(), eccentric, setOf("REST_PAUSE"))) == Triple(5, 1, 0)) {
            "parcial tras 12 s con REST_PAUSE"
        }
        fails.assertNone()
    }

    @Test
    fun theFirstAndTheLastRepMuchBiggerThanTheRestArePickupAndSetDown() {
        val fails = RepTestSupport.Failures()
        val b = SignalBuilder().hold(2.0)
        b.rep(1.6)
        repeat(5) { b.rep() }
        b.rep(1.6).hold(1.5)
        val seg = segment(b.series(), eccentric)
        fails.check(RepTestSupport.counts(seg) == Triple(5, 0, 0)) { "recogida y dejada: ${describe(seg)}" }
        fails.check(seg.discarded.map { it.reason }.sorted() == listOf("setdown", "setup")) { "recogida y dejada: ${describe(seg)}" }
        // Reps un 20 % más profundas al principio y al final son repeticiones.
        val mild = SignalBuilder().hold(2.0)
        mild.rep(1.2)
        repeat(5) { mild.rep() }
        mild.rep(1.2).hold(1.5)
        fails.check(segment(mild.series(), eccentric).fullCount == 7) { "reps un 20 % mayores" }
        fails.assertNone()
    }

    // ---- paso 7: polaridad ----

    @Test
    fun aSignalThatRestsAtTheTopIsFlippedOnce() {
        // Quieto arriba 3 s entre inmersiones breves: el reposo está arriba y la señal está al revés.
        val b = SignalBuilder(start = 1.0).hold(3.0)
        repeat(5) { b.go(1.0, 0.0).go(1.0, 1.0).hold(3.0) }
        val seg = segment(b.series(), eccentric)
        assertTrue(describe(seg), seg.polarityFlipped)
        assertEquals(describe(seg), 5, seg.fullCount)
        // Una serie normal, con el reposo abajo y pausas arriba, no se da la vuelta.
        val normal = SignalBuilder().hold(2.0)
        repeat(4) { normal.go(1.5, 1.0).hold(1.5).go(1.5, 0.0).hold(0.5) }
        normal.hold(2.0)
        val ok = segment(normal.series(), eccentric)
        assertTrue(describe(ok), !ok.polarityFlipped)
        assertEquals(describe(ok), 4, ok.fullCount)
        // Pausas de 4 s arriba (más quieto arriba que abajo): si la serie empieza y acaba abajo, la polaridad es la buena.
        val paused = SignalBuilder().hold(1.0)
        repeat(4) { paused.go(1.5, 1.0).hold(4.0).go(1.5, 0.0).hold(0.5) }
        paused.hold(1.0)
        val longPauses = segment(paused.series(), eccentric)
        assertTrue(describe(longPauses), !longPauses.polarityFlipped)
        assertEquals(describe(longPauses), 4, longPauses.fullCount)
    }

    // ---- paso 8: mini-series ----

    @Test
    fun longRestsSplitTheSetIntoMiniSets() {
        val fails = RepTestSupport.Failures()
        fun series(restS: Double): CountSeries {
            val b = SignalBuilder().hold(2.0)
            repeat(3) { b.rep() }
            b.hold(restS)
            repeat(2) { b.rep() }
            b.hold(restS)
            repeat(3) { b.rep() }
            return b.hold(1.5).series()
        }
        val long = segment(series(11.5), eccentric)
        fails.check(long.miniSets == 3 && long.fullCount == 8) { "descansos de 12 s: ${describe(long)}" }
        fails.check(long.cycles.map { it.miniSet } == listOf(0, 0, 0, 1, 1, 2, 2, 2)) { "mini-serie de cada repetición ${long.cycles.map { it.miniSet }}" }
        val short = segment(series(5.5), eccentric)
        fails.check(short.miniSets == 1 && short.fullCount == 8) { "descansos de 6 s: ${describe(short)}" }
        fails.assertNone()
    }

    // ---- paso 9: por lado ----

    private fun twoArms(slots: Int, alternate: Boolean, rightReps: Int = slots): Pair<DoubleArray, DoubleArray> {
        val left = SignalBuilder().hold(2.0)
        val right = SignalBuilder().hold(2.0)
        for (i in 0 until slots) {
            if (alternate) {
                if (i % 2 == 0) { left.rep(); right.hold(4.0) } else { left.hold(4.0); right.rep() }
            } else {
                left.rep()
                if (i < rightReps) right.rep() else right.hold(4.0)
            }
        }
        left.hold(1.5)
        right.hold(1.5)
        return left.values() to right.values()
    }

    private fun sideSeries(left: DoubleArray, right: DoubleArray): CountSeries {
        val r = DoubleArray(left.size) { max(left[it], right[it]) }
        return SignalBuilder.seriesOf(r, perSide = mapOf(Side.LEFT to left, Side.RIGHT to right))
    }

    private val curl: AnalysisProfile = AnalysisProfiles.of(AnalysisProfileId.CURL).copy(minAmplitude = 0.1)

    @Test
    fun theSidesAreCountedByTheirPattern() {
        val fails = RepTestSupport.Failures()
        val (la, ra) = twoArms(6, alternate = true)
        val alternating = segment(sideSeries(la, ra), curl)
        fails.check(alternating.sideMode == SideMode.ALTERNATING && alternating.fullCount == 6) { "alterno: ${describe(alternating)}" }
        fails.check(alternating.perSideCounts == mapOf(Side.LEFT to 3, Side.RIGHT to 3)) { "alterno: ${alternating.perSideCounts}" }
        fails.check(alternating.cycles.map { it.side } == listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT)) {
            "alterno: ${alternating.cycles.map { it.side }}"
        }

        val (ls, rs) = twoArms(5, alternate = false)
        val together = segment(sideSeries(ls, rs), curl)
        fails.check(together.sideMode == SideMode.BOTH_SYNC && together.fullCount == 5) { "a la vez: ${describe(together)}" }
        fails.check(together.perSideCounts == mapOf(Side.LEFT to 5, Side.RIGHT to 5)) { "a la vez: ${together.perSideCounts}" }
        fails.check(together.ambiguousCycles == 0) { "a la vez: dudosos ${together.ambiguousCycles}" }

        val flat = DoubleArray(ls.size)
        val single = segment(sideSeries(ls, flat), curl)
        fails.check(single.sideMode == SideMode.SINGLE && single.fullCount == 5) { "un brazo: ${describe(single)}" }
        fails.check(single.perSideCounts == mapOf(Side.LEFT to 5) && single.cycles.all { it.side == Side.LEFT }) { "un brazo: ${single.perSideCounts}" }
        val weakOther = DoubleArray(ls.size) { 0.2 * ls[it] }
        val weaker = segment(sideSeries(ls, weakOther), curl)
        fails.check(weaker.sideMode == SideMode.SINGLE && weaker.perSideCounts == mapOf(Side.LEFT to 5)) { "un brazo con eco del otro: ${describe(weaker)}" }

        val (lu, ru) = twoArms(5, alternate = false, rightReps = 4)
        val uneven = segment(sideSeries(lu, ru), curl)
        fails.check(uneven.sideMode == SideMode.BOTH_SYNC && uneven.perSideCounts == mapOf(Side.LEFT to 5, Side.RIGHT to 4)) {
            "lados que no coinciden: ${describe(uneven)} ${uneven.perSideCounts}"
        }
        fails.check(uneven.ambiguousCycles >= 1) { "lados que no coinciden: dudosos ${uneven.ambiguousCycles}" }

        // Sin los dos lados fiables (de lado a la cámara) manda el cercano y no se sabe qué lado trabaja.
        val nearOnly = segment(SignalBuilder.seriesOf(ls), curl)
        fails.check(nearOnly.sideMode == SideMode.NEAR_ONLY && nearOnly.fullCount == 5 && nearOnly.perSideCounts.isEmpty()) {
            "solo el lado cercano: ${describe(nearOnly)}"
        }
        // Y con el disco como señal (una sola barra) el perfil por lado no aplica.
        val plate = segment(SignalBuilder.seriesOf(ls, source = CountSeriesBuilder.SOURCE_PLATE), curl)
        fails.check(plate.sideMode == SideMode.NOT_APPLICABLE) { "disco: ${plate.sideMode}" }
        fails.assertNone()
    }

    // ---- paso 4 y salida ----

    @Test
    fun noRepsComeWithTheirReason() {
        val fails = RepTestSupport.Failures()
        val short = segment(SignalBuilder().hold(0.25).go(0.25, 1.0).go(0.25, 0.0).series(), eccentric)
        fails.check(short.cycles.isEmpty() && short.noRepsReason == "too_short") { "muy corta: ${describe(short)}" }
        val flat = segment(SignalBuilder().hold(20.0).series(sigma = 0.01), eccentric)
        fails.check(flat.cycles.isEmpty() && flat.noRepsReason in setOf("no_peaks", "low_amplitude", "low_snr")) { "plana: ${describe(flat)}" }
        val small = SignalBuilder().hold(2.0)
        repeat(5) { small.rep(0.06) }
        val tiny = segment(small.hold(1.5).series(), eccentric)
        fails.check(tiny.fullCount == 0 && tiny.noRepsReason == "low_amplitude") { "por debajo de la amplitud mínima: ${describe(tiny)}" }
        val noisy = SignalBuilder().hold(2.0)
        repeat(5) { noisy.rep(0.3) }
        val buried = segment(noisy.hold(1.5).series(sigma = 0.1), RepTestSupport.eccentricProfile(0.05))
        fails.check(buried.fullCount == 0 && buried.noRepsReason in setOf("low_snr", "no_peaks", "low_amplitude")) {
            "enterrada en ruido: ${describe(buried)}"
        }
        fails.assertNone()
    }

    @Test
    fun theSegmentationReportsTheActiveWindowPeriodAndNoise() {
        val series = SignalBuilder().hold(2.0).also { b -> repeat(5) { b.rep() } }.hold(1.5).series(sigma = 0.01)
        val seg = segment(series, eccentric)
        assertEquals(describe(seg), 5, seg.fullCount)
        val window = seg.activeWindow
        assertNotNull(window)
        assertTrue("ventana $window", window!!.first <= seg.cycles.first().peak && window.last >= seg.cycles.last().peak)
        assertEquals("T0", 4.0, seg.periodS ?: Double.NaN, 0.25)
        assertTrue("σn = ${seg.noiseSigma}", seg.noiseSigma in 0.005..0.012)
        assertEquals("Afull", 1.0, seg.aFull, 0.05)
        assertEquals("Lrest", 0.0, seg.lRest, 0.05)
        assertTrue("amplitud relativa ${seg.cycles.map { it.amplitudeFraction }}", seg.cycles.all { it.amplitudeFraction in 0.9..1.1 })
    }

    @Test
    fun cyclesNearTheThresholdsCountAsAmbiguous() {
        val b = SignalBuilder().hold(2.0)
        repeat(4) { b.rep() }
        b.rep(0.65).hold(1.5)
        val seg = segment(b.series(), eccentric)
        assertEquals(describe(seg), Triple(4, 1, 0), RepTestSupport.counts(seg))
        assertTrue("n_amb = ${seg.ambiguousCycles}", seg.ambiguousCycles >= 1)
    }
}
