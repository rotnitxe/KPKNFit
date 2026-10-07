package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.LiftMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Las figuras de palitos son geometría pura: estas pruebas son los «ojos» de la cinemática inversa. Comprueban que en
 * TODA la fase los huesos mantienen su largo, que las manos agarran la barra, que los pies pisan el suelo, que nada
 * se sale del lienzo y que no hay saltos (un codo o una rodilla que cambian de lado de golpe).
 */
class FigurePosesTest {

    private fun dist(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)

    private fun joints(p: FigPose): List<Offset> =
        listOf(p.hip, p.kneeN, p.footN, p.kneeF, p.footF, p.shoulder, p.head, p.elbowN, p.handN, p.elbowF, p.handF)

    private val steps = (0..50).map { it / 50f }

    // ─── Levantamientos ──────────────────────────────────────────────────────

    @Test
    fun `en los seis levantamientos los huesos conservan su largo en toda la fase`() {
        val p = FigPose()
        for (lift in LiftMark.entries) for (u in steps) {
            LiftPoses.solve(lift, u, p)
            val tag = "${lift.name} u=$u"
            assertEquals("$tag muslo cercano", FigGeo.THIGH, dist(p.hip, p.kneeN), 0.01f)
            assertEquals("$tag muslo lejano", FigGeo.THIGH, dist(p.hip, p.kneeF), 0.01f)
            // La tibia solo mide su largo si el pie está al alcance (si no, la cadena se estira hacia él).
            assertTrue("$tag pie cercano fuera de alcance", dist(p.hip, p.footN) <= FigGeo.LEG + 0.02f)
            assertTrue("$tag pie lejano fuera de alcance", dist(p.hip, p.footF) <= FigGeo.LEG + 0.02f)
            assertEquals("$tag tibia cercana", FigGeo.SHIN, dist(p.kneeN, p.footN), 0.12f)
            assertEquals("$tag tronco", FigGeo.TORSO, dist(p.hip, p.shoulder), 0.01f)
            assertEquals("$tag brazo cercano (hombro-codo)", if (lift == LiftMark.SQUAT) 8.41f else FigGeo.UARM, dist(p.shoulder, p.elbowN), 0.02f)
        }
    }

    @Test
    fun `las manos agarran la barra y la barra queda a su alcance`() {
        val p = FigPose()
        for (lift in LiftMark.entries) for (u in steps) {
            LiftPoses.solve(lift, u, p)
            val tag = "${lift.name} u=$u"
            assertTrue("$tag sin barra", p.bar.isSpecifiedForTest())
            assertEquals("$tag mano cercana en la barra", 0f, dist(p.handN, p.bar), 0.01f)
            assertTrue("$tag brazo estirado de más (${dist(p.shoulder, p.handN)})", dist(p.shoulder, p.handN) <= FigGeo.ARM + 0.05f)
            if (lift != LiftMark.SQUAT) {
                assertEquals("$tag antebrazo cercano", FigGeo.FARM, dist(p.elbowN, p.handN), 0.15f)
            }
        }
    }

    @Test
    fun `los pies pisan el suelo y la figura cabe en el lienzo`() {
        val p = FigPose()
        for (lift in LiftMark.entries) for (u in steps) {
            LiftPoses.solve(lift, u, p)
            val tag = "${lift.name} u=$u"
            assertEquals("$tag pie cercano", LiftPoses.GROUND, p.footN.y, 0.001f)
            assertEquals("$tag pie lejano", LiftPoses.GROUND, p.footF.y, 0.001f)
            assertTrue("$tag cadera bajo tierra", p.hip.y < LiftPoses.GROUND - 8f)
            for (j in joints(p)) {
                assertTrue("$tag articulación fuera del lienzo $j", j.x in 0f..LiftPoses.CANVAS_W && j.y in 0f..LiftPoses.GROUND + 0.01f)
            }
            assertTrue("$tag cabeza se sale por arriba", p.head.y - FigGeo.HEAD_R >= 0f)
            assertTrue("$tag disco se sale", p.bar.x - LiftPoses.PLATE_R >= 0f && p.bar.x + LiftPoses.PLATE_R <= LiftPoses.CANVAS_W)
            assertTrue("$tag disco se sale por arriba", p.bar.y - LiftPoses.PLATE_R >= 0f)
        }
    }

    @Test
    fun `no hay saltos entre cuadros consecutivos`() {
        val a = FigPose()
        val b = FigPose()
        val fine = (0..200).map { it / 200f }
        for (lift in LiftMark.entries) {
            LiftPoses.solve(lift, fine.first(), a)
            for (u in fine.drop(1)) {
                LiftPoses.solve(lift, u, b)
                val ja = joints(a)
                val jb = joints(b)
                for (i in ja.indices) {
                    assertTrue("${lift.name} u=$u articulación $i salta ${dist(ja[i], jb[i])}", dist(ja[i], jb[i]) <= 2.5f)
                }
                assertTrue("${lift.name} u=$u la barra salta", dist(a.bar, b.bar) <= 2.5f)
                // se copia para el siguiente cuadro
                LiftPoses.solve(lift, u, a)
            }
        }
    }

    @Test
    fun `el press de banca es el unico con banco y esta a la altura de la cadera`() {
        val p = FigPose()
        for (lift in LiftMark.entries) {
            LiftPoses.solve(lift, 0.5f, p)
            if (lift == LiftMark.BENCH) {
                assertFalse(p.bench.isNaN())
                assertTrue(p.hip.y < p.bench && p.bench - p.hip.y < 4f)
            } else {
                assertTrue("${lift.name} no lleva banco", p.bench.isNaN())
            }
        }
    }

    @Test
    fun `el cuadro de reposo y el bucle empiezan en el mismo sitio`() {
        for (lift in LiftMark.entries) {
            val rest = LiftPoses.restU(lift)
            assertTrue("${lift.name} reposo fuera de 0..1", rest in 0f..1f)
            assertEquals("${lift.name} el bucle arranca en el reposo", rest, LiftPoses.uAt(lift, 0f), 1e-4f)
        }
    }

    @Test
    fun `la repeticion es periodica sube y baja y no se sale de 0 a 1`() {
        var t = 0f
        while (t < LiftPoses.PERIOD * 2) {
            for (lift in LiftMark.entries) {
                val u = LiftPoses.uAt(lift, t)
                assertTrue("${lift.name} t=$t u=$u", u in -1e-4f..1f + 1e-4f)
                assertEquals("${lift.name} periodo", u, LiftPoses.uAt(lift, t + LiftPoses.PERIOD), 1e-3f)
            }
            t += 0.05f
        }
        assertEquals(0f, LiftPoses.repEnvelope(0f), 1e-6f)
        assertEquals(1f, LiftPoses.repEnvelope(0.70f), 1e-6f)
        assertEquals(0f, LiftPoses.repEnvelope(1f), 1e-6f)
        assertTrue(FIG_CLOCK_WRAP % LiftPoses.PERIOD == 0.0)
    }

    // ─── Ejercicios de peso corporal ─────────────────────────────────────────

    @Test
    fun `flexion fondo y sentadilla a una pierna conservan los huesos y no dan saltos`() {
        val a = FigPose()
        val b = FigPose()
        val fine = (0..200).map { it / 200f }
        for (skill in listOf(CapabilitySkill.PUSH_UP, CapabilitySkill.DIP, CapabilitySkill.PISTOL_SQUAT)) {
            CapabilityPoses.solveSide(skill, 0f, a)
            for (d in fine) {
                CapabilityPoses.solveSide(skill, d, b)
                val tag = "${skill.name} d=$d"
                assertEquals("$tag muslo", FigGeo.THIGH, dist(b.hip, b.kneeN), 0.01f)
                assertEquals("$tag muslo lejano", FigGeo.THIGH, dist(b.hip, b.kneeF), 0.01f)
                assertTrue("$tag pie fuera de alcance", dist(b.hip, b.footN) <= FigGeo.LEG + 0.02f)
                assertTrue("$tag pie lejano fuera de alcance", dist(b.hip, b.footF) <= FigGeo.LEG + 0.02f)
                assertEquals("$tag tronco", FigGeo.TORSO, dist(b.hip, b.shoulder), 0.8f)
                assertEquals("$tag brazo", FigGeo.UARM, dist(b.shoulder, b.elbowN), 0.01f)
                val ja = joints(a)
                val jb = joints(b)
                for (i in ja.indices) {
                    assertTrue("$tag articulación $i salta ${dist(ja[i], jb[i])}", dist(ja[i], jb[i]) <= 2.5f)
                }
                CapabilityPoses.solveSide(skill, d, a)
            }
        }
    }

    @Test
    fun `la flexion apoya manos y pies y el cuerpo baja con la profundidad`() {
        val p = FigPose()
        var lastShoulderY = -1f
        for (d in steps) {
            CapabilityPoses.pushUp(d, p)
            assertEquals(CapabilityPoses.PUSH_GROUND, p.footN.y, 1e-3f)
            assertEquals(CapabilityPoses.PUSH_GROUND, p.handN.y, 1e-3f)
            assertTrue("d=$d el brazo no llega al suelo (${dist(p.shoulder, p.handN)})", dist(p.shoulder, p.handN) <= FigGeo.ARM + 0.5f)
            assertTrue("d=$d el hombro baja", p.shoulder.y >= lastShoulderY - 1e-3f)
            lastShoulderY = p.shoulder.y
            assertTrue("d=$d el hombro se hunde en el suelo", p.shoulder.y < CapabilityPoses.PUSH_GROUND - 3f)
        }
    }

    @Test
    fun `el fondo agarra la barra y los pies cuelgan sin tocar el suelo`() {
        val p = FigPose()
        var lastShoulderY = -1f
        for (d in steps) {
            CapabilityPoses.dip(d, p)
            assertEquals(CapabilityPoses.DIP_BAR_Y, p.handN.y, 1e-3f)
            assertTrue("d=$d brazo estirado de más", dist(p.shoulder, p.handN) <= FigGeo.ARM + 0.05f)
            assertTrue("d=$d pies al suelo", p.footN.y < CapabilityPoses.DIP_GROUND - 6f && p.footF.y < CapabilityPoses.DIP_GROUND - 6f)
            assertTrue("d=$d el hombro baja", p.shoulder.y >= lastShoulderY - 1e-3f)
            lastShoulderY = p.shoulder.y
            assertEquals("d=$d antebrazo", FigGeo.FARM, dist(p.elbowN, p.handN), 0.1f)
        }
    }

    @Test
    fun `la sentadilla a una pierna apoya una sola y estira la otra al frente`() {
        val p = FigPose()
        var lastHipY = -1f
        for (d in steps) {
            CapabilityPoses.pistol(d, p)
            assertEquals(CapabilityPoses.PISTOL_GROUND, p.footN.y, 1e-3f)
            assertTrue("d=$d el pie libre toca el suelo", p.footF.y < CapabilityPoses.PISTOL_GROUND - 6f)
            assertTrue("d=$d el pie libre no va al frente", p.footF.x > p.hip.x + 10f)
            assertTrue("d=$d la cadera sube", p.hip.y >= lastHipY - 1e-3f)
            lastHipY = p.hip.y
        }
    }

    @Test
    fun `la dominada cuelga de la barra y los hombros suben con la profundidad`() {
        val f = FigFrontPose()
        var lastY = Float.MAX_VALUE
        for (d in steps) {
            CapabilityPoses.pullUp(d, f)
            assertEquals(CapabilityPoses.PULL_BAR_Y, f.handL.y, 1e-4f)
            assertEquals(CapabilityPoses.PULL_BAR_Y, f.handR.y, 1e-4f)
            assertTrue("d=$d brazo izquierdo estirado de más", dist(f.shoulderL, f.handL) <= FigGeo.ARM + 0.05f)
            assertTrue("d=$d brazo derecho estirado de más", dist(f.shoulderR, f.handR) <= FigGeo.ARM + 0.05f)
            assertTrue("d=$d los hombros no suben", f.shoulderL.y <= lastY + 1e-4f)
            lastY = f.shoulderL.y
            assertTrue("d=$d los hombros pasan la barra", f.shoulderL.y > CapabilityPoses.PULL_BAR_Y)
            // Los codos van hacia fuera: el izquierdo a la izquierda del hombro, el derecho a la derecha.
            assertTrue("d=$d codo izquierdo", f.elbowL.x <= f.shoulderL.x + 1e-3f)
            assertTrue("d=$d codo derecho", f.elbowR.x >= f.shoulderR.x - 1e-3f)
            assertEquals("d=$d simetría", f.shoulderL.y, f.shoulderR.y, 1e-4f)
            assertEquals("d=$d codos simétricos", f.elbowL.y, f.elbowR.y, 1e-3f)
        }
        // Colgado y arriba son distintos y arriba el mentón llega a la barra.
        CapabilityPoses.pullUp(0f, f)
        val hangY = f.shoulderL.y
        CapabilityPoses.pullUp(1f, f)
        assertTrue(hangY - f.shoulderL.y > 3f)
        assertTrue("mentón sobre la barra", f.head.y + FigGeo.HEAD_R <= CapabilityPoses.PULL_BAR_Y + 1.5f)
    }

    @Test
    fun `cada ejercicio cabe en su escenario`() {
        val p = FigPose()
        val f = FigFrontPose()
        for (skill in CapabilitySkill.entries) {
            val stage = CapabilityPoses.stageOf(skill)
            for (d in steps) {
                val pts: List<Offset> = if (skill == CapabilitySkill.PULL_UP) {
                    CapabilityPoses.pullUp(d, f)
                    listOf(f.head, f.shoulderL, f.shoulderR, f.elbowL, f.elbowR, f.handL, f.handR, f.hip, f.kneeL, f.kneeR, f.footL, f.footR)
                } else {
                    CapabilityPoses.solveSide(skill, d, p)
                    joints(p)
                }
                for (j in pts) {
                    val tag = "${skill.name} d=$d $j"
                    assertTrue("$tag x", j.x >= stage.left && j.x <= stage.left + stage.width)
                    assertTrue("$tag y", j.y >= stage.top && j.y <= stage.top + stage.height)
                }
            }
        }
    }

    private fun Offset.isSpecifiedForTest(): Boolean = !x.isNaN() && !y.isNaN()
}
