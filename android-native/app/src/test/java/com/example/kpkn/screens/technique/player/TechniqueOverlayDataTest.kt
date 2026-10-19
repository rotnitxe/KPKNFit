package com.example.kpkn.screens.technique.player

import com.example.kpkn.domain.biomechanics.bar.BarSampleSource
import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.screens.technique.TechniqueSamples
import com.example.kpkn.screens.technique.player.design.fitVideoRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los datos de la capa del reproductor (VT7a), con la sentadilla sintética de `TechniqueSamples`. */
class TechniqueOverlayDataTest {
    private val data = TechniqueSamples.squatOverlay
    private val report = TechniqueSamples.squatReport
    private val track = TechniqueSamples.squatResult.track

    @Test
    fun theAnalysisOfTheSampleFindsTheSixRepetitions() {
        // La base de todas las demás pruebas: si el motor no cuenta las seis, lo demás no dice nada.
        assertEquals("repeticiones del informe: ${report.reps.map { it.kind }}", 6, report.reps.size)
        assertTrue(MetricKey.PATH in report.available)
        assertEquals("left", report.nearSide)
    }

    @Test
    fun skeletonAndTrailCoordinatesFallInsideTheFrame() {
        assertEquals(track.frames.size * OverlayJoint.COUNT * 2, data.joints.size)
        var finite = 0
        for (v in data.joints) {
            if (v.isNaN()) continue
            finite++
            assertTrue("coordenada fuera de 0..1: $v", v in 0f..1f)
        }
        assertTrue("casi todos los puntos deberían tener dato: $finite", finite > data.joints.size / 2)
        val trail = data.trail
        assertNotNull(trail)
        var points = 0
        for (i in trail!!.timesUs.indices) {
            if (trail.x[i].isNaN()) continue
            points++
            assertTrue("x de la trayectoria fuera de 0..1: ${trail.x[i]}", trail.x[i] in 0f..1f)
            assertTrue("y de la trayectoria fuera de 0..1: ${trail.y[i]}", trail.y[i] in 0f..1f)
        }
        assertTrue(points > 100)
        assertEquals(TechniqueSamples.VIDEO_WIDTH.toFloat() / TechniqueSamples.VIDEO_HEIGHT, data.videoAspect, 1e-6f)
    }

    @Test
    fun relativeSpeedsRunFromZeroToOneInEveryRepetition() {
        val trail = data.trail!!
        assertEquals(report.reps.size, trail.repFrom.size)
        for (k in trail.repFrom.indices) {
            var min = Float.MAX_VALUE
            var max = -Float.MAX_VALUE
            var n = 0
            for (i in trail.repFrom[k] until trail.repTo[k]) {
                val v = trail.speed[i]
                if (v.isNaN()) continue
                assertTrue("velocidad relativa fuera de 0..1 en la repetición $k: $v", v in 0f..1f)
                min = minOf(min, v)
                max = maxOf(max, v)
                n++
            }
            assertTrue("la repetición $k tiene pocos puntos: $n", n >= 20)
            assertEquals("mínimo de la repetición $k", 0f, min, 1e-6f)
            assertEquals("máximo de la repetición $k", 1f, max, 1e-6f)
        }
    }

    @Test
    fun trailRangesStayInsideTheirRepetition() {
        val trail = data.trail!!
        for (k in data.reps.indices) {
            val rep = data.reps[k]
            assertTrue(trail.repFrom[k] < trail.repTo[k])
            for (i in trail.repFrom[k] until trail.repTo[k]) {
                assertTrue("el punto $i queda fuera de la repetición $k", trail.timesUs[i] in rep.startUs..rep.endUs)
            }
        }
    }

    @Test
    fun stepPtsAreSortedAndComeFromTheTrack() {
        assertEquals(track.frames.size, data.stepPtsUs.size)
        for (i in data.stepPtsUs.indices) {
            assertEquals(track.frames[i].tUs, data.stepPtsUs[i])
            if (i > 0) assertTrue("PTS desordenados en $i", data.stepPtsUs[i] > data.stepPtsUs[i - 1])
        }
    }

    @Test
    fun theStepSeekPositionIsTheFlooredMillisecond() {
        for (i in data.stepPtsUs.indices) {
            val ms = data.stepSeekMs(i)
            assertTrue("$ms ms pasa del PTS ${data.stepPtsUs[i]}", ms * 1000L <= data.stepPtsUs[i])
            assertTrue("$ms ms se queda a más de 1 ms del PTS ${data.stepPtsUs[i]}", data.stepPtsUs[i] < (ms + 1) * 1000L)
        }
    }

    @Test
    fun withoutABarTrackTheSubstituteOfThePoseIsUsed() {
        assertEquals(TechniqueTrailSource.POSE, data.trailSource)
        assertTrue(data.hasTrail)
    }

    @Test
    fun aBarTrackWithCoverageBecomesThePointOfTheBar() {
        val withPlate = TechniqueSamples.squatPlateOverlay
        assertEquals(TechniqueTrailSource.PLATE, withPlate.trailSource)
        // El disco del ejemplo sigue al hombro cercano, así que las dos trayectorias coinciden en el espacio de la imagen.
        val plate = withPlate.trail!!
        val pose = data.trail!!
        var compared = 0
        var sumGap = 0f
        for (i in plate.timesUs.indices) {
            val j = pose.timesUs.indexOf(plate.timesUs[i])
            if (j < 0 || plate.y[i].isNaN() || pose.y[j].isNaN()) continue
            compared++
            sumGap += kotlin.math.abs(plate.y[i] - pose.y[j])
        }
        assertTrue(compared > 100)
        // La traza de pose lleva el ruido del generador; el disco sale de la verdad limpia: coinciden en promedio.
        assertTrue("las trayectorias deberían coincidir: hueco medio ${sumGap / compared}", sumGap / compared < 0.005f)
    }

    @Test
    fun anAutomaticCircleOrLegacyTrackCannotPublishAPlateTrailEvenWithFullCoverage() {
        val selected = TechniqueSamples.squatPlate
        val seed = checkNotNull(selected.seed)
        for (bar in listOf(selected.copy(seed = seed.copy(userConfirmed = false)), selected.copy(seed = null))) {
            val provisional = TechniqueOverlayData.from(track, bar, report,
                TechniqueSamples.VIDEO_WIDTH, TechniqueSamples.VIDEO_HEIGHT)
            assertEquals(TechniqueTrailSource.POSE, provisional.trailSource)
            assertTrue(data.trail!!.timesUs.contentEquals(provisional.trail!!.timesUs))
            assertTrue(data.trail!!.x.contentEquals(provisional.trail!!.x))
            assertTrue(data.trail!!.y.contentEquals(provisional.trail!!.y))
        }
    }

    @Test
    fun aBarTrackWithoutCoverageFallsBackToThePose() {
        val weak = TechniqueOverlayData.from(
            track,
            TechniqueSamples.plateWithSource(BarSampleSource.PREDICTED),
            report,
            TechniqueSamples.VIDEO_WIDTH,
            TechniqueSamples.VIDEO_HEIGHT,
        )
        assertEquals(TechniqueTrailSource.POSE, weak.trailSource)
    }

    @Test
    fun aProfileWithoutBarPointHasNoTrailAndNoGuide() {
        // El mismo vídeo medido como extensión de cuádriceps: sin punto de barra ni verticalidad.
        val other = TechniqueOverlayData.from(
            track,
            TechniqueSamples.squatPlate,
            report.copy(profileId = AnalysisProfileId.LEG_EXTENSION),
            TechniqueSamples.VIDEO_WIDTH,
            TechniqueSamples.VIDEO_HEIGHT,
        )
        assertNull(other.trail)
        assertTrue(other.guideX.isNaN())
        assertTrue(other.hasSkeleton)
    }

    @Test
    fun theGuideIsTheMidfootOfTheNearSideOnlyWhenTheProfileMeasuresVerticality() {
        assertTrue(data.hasGuide)
        assertTrue("guía en ${data.guideX}", data.guideX in 0.05f..0.95f)
        // Sin la trayectoria en el informe (los pies se movían) tampoco hay guía.
        val noPath = TechniqueOverlayData.from(
            track, null, report.copy(available = report.available - MetricKey.PATH), TechniqueSamples.VIDEO_WIDTH, TechniqueSamples.VIDEO_HEIGHT,
        )
        assertTrue(noPath.guideX.isNaN())
        // Y la banca (consistencia, no verticalidad) no la pide.
        val bench = TechniqueOverlayData.from(
            track, null, report.copy(profileId = AnalysisProfileId.BENCH), TechniqueSamples.VIDEO_WIDTH, TechniqueSamples.VIDEO_HEIGHT,
        )
        assertTrue(bench.guideX.isNaN())
    }

    @Test
    fun theRepetitionMarksMirrorTheReport() {
        assertEquals(report.reps.size, data.reps.size)
        for ((i, mark) in data.reps.withIndex()) {
            val rep = report.reps[i]
            assertEquals(rep.index, mark.index)
            assertEquals(rep.kind, mark.kind)
            assertEquals(rep.phases.departUs, mark.startUs)
            assertEquals(rep.phases.arriveUs, mark.endUs)
            assertTrue(mark.startUs <= mark.turnUs && mark.turnUs <= mark.endUs)
            if (i > 0) assertTrue(mark.startUs >= data.reps[i - 1].startUs)
        }
        assertEquals(Side.LEFT, data.nearSide)
    }

    @Test
    fun theFrameLookupFollowsThePtsAndRefusesStaleData() {
        val first = data.frameTimesUs.first()
        val last = data.frameTimesUs.last()
        assertEquals(-1, data.frameIndexAt(first - 50_000L))
        assertEquals(0, data.frameIndexAt(first))
        // A mitad de camino entre dos fotogramas manda el anterior.
        val a = data.frameTimesUs[10]
        val b = data.frameTimesUs[11]
        assertEquals(10, data.frameIndexAt((a + b) / 2))
        assertEquals(11, data.frameIndexAt(b))
        // 1 µs de redondeo entre el reloj del reproductor y el de la traza no cambia de fotograma.
        assertEquals(11, data.frameIndexAt(b - 1L))
        // Mucho después del último fotograma ya no hay dato que pintar.
        assertEquals(data.frameTimesUs.size - 1, data.frameIndexAt(last + 20_000L))
        assertEquals(-1, data.frameIndexAt(last + 2_000_000L))
    }

    @Test
    fun theTrailSegmentIsTheLastRepetitionThatAlreadyLeft() {
        val reps = data.reps
        assertEquals(-1, data.trailSegmentAt(reps[0].startUs - 200_000L))
        assertEquals(0, data.trailSegmentAt(reps[0].startUs))
        assertEquals(0, data.trailSegmentAt(reps[0].endUs + 100_000L))
        assertEquals(1, data.trailSegmentAt(reps[1].startUs + 10_000L))
        assertEquals(reps.size - 1, data.trailSegmentAt(reps.last().endUs + 5_000_000L))
        assertEquals(-1, data.repIndexAt(reps[0].startUs - 1L))
        assertEquals(2, data.repIndexAt((reps[2].startUs + reps[2].endUs) / 2))
        assertEquals(1, data.repIndexNear(reps[0].endUs + 1L))
        assertEquals(-1, data.repIndexNear(reps.last().endUs + 1L))
    }

    @Test
    fun emptyDataIsHarmless() {
        val empty = TechniqueOverlayData.from(null, null, null, 0, 0)
        assertFalse(empty.hasSkeleton)
        assertFalse(empty.hasTrail)
        assertFalse(empty.hasGuide)
        assertTrue(empty.reps.isEmpty())
        assertEquals(0f, empty.videoAspect, 0f)
        assertEquals(-1, empty.frameIndexAt(0L))
        assertEquals(-1, empty.stepIndexAt(0L))
        assertEquals(-1, empty.repIndexAt(0L))
        assertEquals(-1, empty.trailSegmentAt(0L))
        assertEquals(-1, empty.trailIndexAt(0L))
        assertEquals(0L, empty.lastFrameUs)
        assertEquals(0, TechniqueOverlayData.EMPTY.stepPtsUs.size)
    }

    @Test
    fun withoutAReportTheSkeletonStillComesButNotTheMarks() {
        val bare = TechniqueOverlayData.from(track, null, null, 0, 0)
        assertTrue(bare.hasSkeleton)
        assertTrue(bare.reps.isEmpty())
        assertNull(bare.trail)
        assertTrue(bare.guideX.isNaN())
        // Sin tamaño de vídeo manda el de la traza.
        assertEquals(TechniqueSamples.VIDEO_WIDTH.toFloat() / TechniqueSamples.VIDEO_HEIGHT, bare.videoAspect, 1e-6f)
        // El disco solo, sin informe: trayectoria de un único tramo.
        val plateOnly = TechniqueOverlayData.from(track, TechniqueSamples.squatPlate, null, 0, 0)
        assertEquals(TechniqueTrailSource.PLATE, plateOnly.trailSource)
        assertEquals(1, plateOnly.trail!!.repFrom.size)
        assertEquals(0, plateOnly.trailSegmentAt(1_000_000L))
    }

    @Test
    fun theVideoRectFitsAndCentersLikeThePlayerView() {
        // Mismo aspecto (dentro del 1 %): llena el contenedor.
        val full = fitVideoRect(328f, 583f, 9f / 16f)
        assertEquals(0f, full.left, 0f)
        assertEquals(328f, full.width, 0f)
        assertEquals(583f, full.height, 0f)
        // Contenedor cuadrado: bandas a los lados.
        val pillar = fitVideoRect(400f, 400f, 0.5f)
        assertEquals(200f, pillar.width, 1e-3f)
        assertEquals(100f, pillar.left, 1e-3f)
        assertEquals(400f, pillar.height, 0f)
        // Vídeo apaisado en un contenedor más alto: bandas arriba y abajo.
        val letter = fitVideoRect(300f, 600f, 1.5f)
        assertEquals(300f, letter.width, 0f)
        assertEquals(200f, letter.height, 1e-3f)
        assertEquals(200f, letter.top, 1e-3f)
        // Sin proporción conocida, todo el lienzo; sin lienzo, nada.
        assertEquals(300f, fitVideoRect(300f, 600f, 0f).width, 0f)
        assertEquals(0f, fitVideoRect(0f, 600f, 1f).width, 0f)
    }
}
