package com.example.kpkn.domain.biomechanics.testing

import com.example.kpkn.domain.biomechanics.kinematics.Side
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Jerk mínimo: s(τ) = 10τ³ − 15τ⁴ + 6τ⁵ (0 → 1, con velocidad y aceleración nulas en los extremos). */
internal fun minJerk(tau: Double): Double {
    val t = tau.coerceIn(0.0, 1.0)
    val t3 = t * t * t
    return t3 * (10.0 - 15.0 * t + 6.0 * t * t)
}

private fun smoothStep(x: Double): Double {
    val t = x.coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}

/** Un tramo de la línea de tiempo: interpola con jerk mínimo cada canal de su valor inicial al final. */
internal class Piece(
    val t0: Double,
    val t1: Double,
    val bL0: Double,
    val bL1: Double,
    val bR0: Double,
    val bR1: Double,
    val x0: Double,
    val x1: Double,
    val u0: Double,
    val u1: Double,
    val gait: Boolean = false,
    val gaitFreq: Double = 0.0,
)

/** Línea de tiempo continua de una serie: canales b (izq./der.), desplazamiento de la raíz, soporte y paso. */
internal class SynthTimeline(
    val pieces: List<Piece>,
    val reps: List<SynthRepTruth>,
    val spans: List<SynthSpanTruth>,
    val frontSide: Side,
    val totalSeconds: Double,
    private val start: MotionState,
) {
    fun stateAt(t: Double): MotionState {
        if (pieces.isEmpty()) return start
        if (t <= pieces.first().t0) {
            val p = pieces.first()
            return MotionState(p.bL0, p.bR0, p.x0, p.u0)
        }
        val last = pieces.last()
        if (t >= last.t1) return MotionState(last.bL1, last.bR1, last.x1, last.u1)
        // Búsqueda binaria del tramo que contiene t.
        var lo = 0
        var hi = pieces.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (pieces[mid].t0 <= t) lo = mid else hi = mid - 1
        }
        val p = pieces[lo]
        val dur = p.t1 - p.t0
        val s = if (dur <= 1e-12) 1.0 else minJerk((t - p.t0) / dur)
        var amp = 0.0
        var phase = 0.0
        if (p.gait && dur > 1e-9) {
            val ramp = min(0.4, dur / 3.0)
            amp = smoothStep((t - p.t0) / ramp) * smoothStep((p.t1 - t) / ramp)
            phase = 2.0 * PI * p.gaitFreq * (t - p.t0)
        }
        return MotionState(
            bL = p.bL0 + (p.bL1 - p.bL0) * s,
            bR = p.bR0 + (p.bR1 - p.bR0) * s,
            rootX = p.x0 + (p.x1 - p.x0) * s,
            unrack = p.u0 + (p.u1 - p.u0) * s,
            gaitAmp = amp,
            gaitPhase = phase,
        )
    }

    companion object {
        private const val RECOVERY_S = 1.0
        private const val STALL_S = 0.8
        private const val UNRACK_SHIFT_M = 0.30
        private const val PARTIAL_FAR_FRACTION = 0.55
        private const val PARTIAL_REST_VALLEY = 0.45
        private const val FAILED_FRACTION = 0.5

        /** Compila el guion en tramos y calcula la verdad de cada repetición. */
        fun build(config: SynthConfig, anthro: Anthro): SynthTimeline {
            val def = SynthMovements.def(config.movement)
            val script = config.script

            // Lado de la pierna delantera de la zancada: el de la primera repetición con lado, o el izquierdo.
            var firstRepSide: Side? = null
            var altIndex = 0
            for (seg in script) {
                if (seg is SynthSegment.Rep && firstRepSide == null) {
                    firstRepSide = effectiveSide(seg, altIndex, def, config)
                }
                if (seg is SynthSegment.Rep) altIndex++
            }
            val frontSide = if (config.movement == SynthMovement.LUNGE) firstRepSide ?: Side.LEFT else Side.LEFT

            // Si el guion saca la barra antes de devolverla, empieza en el soporte.
            var startU = 0.0
            for (seg in script) {
                if (seg is SynthSegment.Unrack) {
                    startU = 1.0
                    break
                }
                if (seg is SynthSegment.Rerack) break
            }

            val pieces = ArrayList<Piece>()
            val reps = ArrayList<SynthRepTruth>()
            val spans = ArrayList<SynthSpanTruth>()
            var t = 0.0
            var bL = 0.0
            var bR = 0.0
            var x = 0.0
            var u = startU
            val start = MotionState(0.0, 0.0, 0.0, startU)

            fun move(duration: Double, toL: Double, toR: Double, toX: Double = x, toU: Double = u, gait: Boolean = false, freq: Double = 0.0) {
                if (duration <= 1e-9) {
                    bL = toL; bR = toR; x = toX; u = toU
                    return
                }
                pieces.add(Piece(t, t + duration, bL, toL, bR, toR, x, toX, u, toU, gait, freq))
                t += duration
                bL = toL; bR = toR; x = toX; u = toU
            }

            fun hold(duration: Double) = move(duration, bL, bR)

            fun ensureRest() {
                if (abs(bL) > 1e-9 || abs(bR) > 1e-9) move(RECOVERY_S, 0.0, 0.0)
            }

            fun gaitFreq(speed: Double) = 0.75 + 0.30 * min(abs(speed), 2.0)

            var repIndex = 0
            var altCounter = 0
            for (seg in script) {
                var spanStart = t
                when (seg) {
                    is SynthSegment.Idle -> {
                        ensureRest()
                        spanStart = t
                        hold(seg.seconds)
                    }
                    is SynthSegment.Rest -> {
                        ensureRest()
                        spanStart = t
                        hold(seg.seconds)
                    }
                    is SynthSegment.Walk -> {
                        ensureRest()
                        spanStart = t
                        val speed = if (seg.seconds > 1e-9) seg.dxM / seg.seconds else 0.0
                        move(seg.seconds, 0.0, 0.0, toX = x + seg.dxM, gait = def.standingRest && abs(seg.dxM) > 1e-9, freq = gaitFreq(speed))
                    }
                    is SynthSegment.Unrack -> {
                        ensureRest()
                        spanStart = t
                        if (config.movement == SynthMovement.BENCH) {
                            move(seg.seconds, 0.0, 0.0, toU = 0.0)
                        } else {
                            move(seg.seconds, 0.0, 0.0, toX = x - UNRACK_SHIFT_M, gait = def.standingRest, freq = gaitFreq(UNRACK_SHIFT_M / max(seg.seconds, 0.5)))
                        }
                    }
                    is SynthSegment.Rerack -> {
                        ensureRest()
                        spanStart = t
                        if (config.movement == SynthMovement.BENCH) {
                            move(seg.seconds, 0.0, 0.0, toU = 1.0)
                        } else {
                            move(seg.seconds, 0.0, 0.0, toX = x + UNRACK_SHIFT_M, gait = def.standingRest, freq = gaitFreq(UNRACK_SHIFT_M / max(seg.seconds, 0.5)))
                        }
                    }
                    is SynthSegment.Rep -> {
                        spanStart = t
                        val side = effectiveSide(seg, altCounter, def, config)
                        altCounter++
                        // Canales que trabajan: con lado (y movimiento de brazos o piernas independientes), solo ese; si no, los dos.
                        val bothSides = side == null || config.movement == SynthMovement.LUNGE
                        val leftWorks = bothSides || side == Side.LEFT
                        val rightWorks = bothSides || side == Side.RIGHT
                        val workingB = if (leftWorks) bL else bR

                        val a = seg.amplitude
                        val peak: Double
                        val valley: Double
                        when (seg.kind) {
                            SynthRepKind.FULL -> { peak = a; valley = 0.0 }
                            SynthRepKind.PARTIAL_FAR -> { peak = PARTIAL_FAR_FRACTION * a; valley = 0.0 }
                            SynthRepKind.PARTIAL_REST -> { peak = a; valley = PARTIAL_REST_VALLEY * a }
                            SynthRepKind.FAILED -> { peak = if (def.startsEccentric) a else FAILED_FRACTION * a; valley = 0.0 }
                        }

                        // Fases en el orden del arranque: E → EXC, P1, CON, P2; C → CON, P2, EXC, P1.
                        val leaveS = if (def.startsEccentric) seg.eccS else seg.conS
                        val holdPeakS = if (def.startsEccentric) seg.p1S else seg.p2S
                        val returnS = if (def.startsEccentric) seg.conS else seg.eccS
                        val holdValleyS = if (def.startsEccentric) seg.p2S else seg.p1S

                        fun go(duration: Double, to: Double) =
                            move(duration, if (leftWorks) to else bL, if (rightWorks) to else bR)

                        val departB = workingB
                        val tDepart = t
                        go(leaveS, peak)
                        val tTurnStart = t
                        val tTurnEnd: Double
                        val tArrive: Double
                        if (seg.kind == SynthRepKind.FAILED) {
                            if (def.startsEccentric) {
                                // Baja entera, y en la subida llega al 50 % de su recorrido, se queda 0,8 s y vuelve abajo.
                                hold(holdPeakS)
                                tTurnEnd = t
                                go(seg.conS, peak * (1.0 - FAILED_FRACTION))
                                hold(STALL_S)
                                go(seg.eccS, peak)
                                tArrive = t
                            } else {
                                // La concéntrica llega al 50 %, se queda 0,8 s y baja de nuevo al reposo.
                                hold(STALL_S)
                                tTurnEnd = t
                                go(seg.eccS, 0.0)
                                tArrive = t
                                hold(seg.p1S)
                            }
                        } else {
                            hold(holdPeakS)
                            tTurnEnd = t
                            go(returnS, valley)
                            tArrive = t
                            hold(holdValleyS)
                        }

                        val workSide = if (config.movement == SynthMovement.LUNGE) frontSide else side ?: Side.LEFT
                        val angleAtDepart = angleAt(config, anthro, frontSide, workSide, departB, u)
                        val angleAtTurn = angleAt(config, anthro, frontSide, workSide, peak, u)
                        reps.add(
                            SynthRepTruth(
                                index = repIndex++,
                                kind = seg.kind,
                                side = if (config.movement == SynthMovement.LUNGE) frontSide else side,
                                departUs = (tDepart * 1e6).roundToLong(),
                                turnStartUs = (tTurnStart * 1e6).roundToLong(),
                                turnEndUs = (tTurnEnd * 1e6).roundToLong(),
                                arriveUs = (tArrive * 1e6).roundToLong(),
                                romDeg = abs(angleAtTurn - angleAtDepart),
                            ),
                        )
                    }
                }
                spans.add(SynthSpanTruth(seg, (spanStart * 1e6).roundToLong(), (t * 1e6).roundToLong()))
            }
            return SynthTimeline(pieces, reps, spans, frontSide, t, start)
        }

        /** Lado que trabaja en una repetición: el pedido, el que toca si `alternating`, o null (ambos). */
        private fun effectiveSide(rep: SynthSegment.Rep, repOrdinal: Int, def: MovementDef, config: SynthConfig): Side? {
            if (!def.supportsSide) return null
            rep.side?.let { return it }
            if (config.alternating) return if (repOrdinal % 2 == 0) Side.LEFT else Side.RIGHT
            return null
        }

        /** Ángulo principal (flexión, °) con el lado de trabajo en [b] y el otro en reposo. */
        private fun angleAt(config: SynthConfig, anthro: Anthro, frontSide: Side, workSide: Side, b: Double, unrack: Double): Double {
            val bL = if (workSide == Side.LEFT || config.movement == SynthMovement.LUNGE) b else 0.0
            val bR = if (workSide == Side.RIGHT || config.movement == SynthMovement.LUNGE) b else 0.0
            // Los movimientos de torso (sentadilla, peso muerto…) usan max(bL, bR): con un solo canal basta.
            val st = MotionState(if (config.movement.sidesLinked()) b else bL, if (config.movement.sidesLinked()) b else bR, 0.0, unrack)
            val post = SynthMovements.posture(config.movement, st, anthro, frontSide)
            val joints = SynthBody.joints(post, anthro)
            return SynthMovements.primaryAngleDeg(config.movement, joints, workSide, anthro)
        }

        /** Los movimientos que no distinguen izquierda y derecha: se miden con los dos canales iguales. */
        private fun SynthMovement.sidesLinked(): Boolean = !SynthMovements.def(this).supportsSide
    }
}
