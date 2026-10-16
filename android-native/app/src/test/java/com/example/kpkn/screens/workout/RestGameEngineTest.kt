package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * El minijuego del descanso (LV6, F8): física simple y justa. Un salto sube 70 dp en 0,77 s, la caja de choque es más pequeña que el
 * dibujo, las mancuernas nunca llegan más juntas que un salto más un respiro, y un cuadro tardío no teletransporta el mundo.
 */
class RestGameEngineTest {

    private fun engine(best: Int = 0, seed: Int = 1, worldWidth: Float = 320f) =
        RestGameEngine(initialBest = best, random = Random(seed)).also { it.worldWidth = worldWidth }

    /** Un jugador que salta a tiempo: cuando la mancuerna más cercana está a [lead] s de llegar. */
    private fun RestGameEngine.botJumps(lead: Float = 0.34f) {
        val next = obstacles.filter { it.x + RestGameEngine.OBSTACLE_W > RestGameEngine.RUNNER_X }.minByOrNull { it.x } ?: return
        val ahead = next.x - (RestGameEngine.RUNNER_X + RestGameEngine.RUNNER_HIT_RIGHT)
        if (ahead in 0f..(speed * lead)) jump()
    }

    @Test
    fun aJumpRisesAboutSeventyDpAndLandsInAboutThreeQuartersOfASecond() {
        val game = engine()
        assertTrue(game.jump())
        var apex = 0f
        var airTime = 0f
        val dt = 1f / 120f
        // Sin mancuernas en medio: se aparta el mundo para medir el salto solo.
        repeat(400) {
            game.obstacles.clear()
            game.step(dt)
            apex = maxOf(apex, game.height)
            if (!game.onGround) airTime += dt
        }
        // El salto continuo sube 69,7 dp; con cuadros de 1/120 s la suma discreta se queda en unos 68.
        assertEquals(69f, apex, 2.5f)
        assertEquals(0.77f, airTime, 0.04f)
        assertTrue(game.onGround)
    }

    @Test
    fun theRunnerCanOnlyJumpFromTheGround() {
        val game = engine()
        assertTrue(game.onGround)
        assertTrue(game.jump())
        game.step(0.05f)
        assertFalse(game.onGround)
        assertFalse(game.jump())
    }

    @Test
    fun aRunnerThatNeverJumpsHitsAndKeepsTheRecord() {
        val game = engine(best = 5)
        var hit = false
        var seconds = 0f
        while (!hit && seconds < 12f) {
            hit = game.step(1f / 60f)
            seconds += 1f / 60f
        }
        assertTrue("debía chocar con la primera mancuerna", hit)
        assertEquals(1, game.hits)
        assertEquals(0, game.score)
        assertEquals("el récord no se pierde al chocar", 5, game.best)
        assertTrue(game.flash > 0f)
        assertEquals("la velocidad vuelve a la inicial", RestGameEngine.BASE_SPEED, game.speed, 1e-3f)
        // Las mancuernas cercanas se quitan: no se choca dos veces con la misma.
        assertTrue(game.obstacles.none { it.x < RestGameEngine.RUNNER_X + RestGameEngine.RESTART_CLEAR })
    }

    @Test
    fun aPlayerWhoJumpsInTimeNeverGetsHitAndBeatsTheRecord() {
        val game = engine(best = 2, seed = 7)
        repeat(60 * 40) {
            game.botJumps()
            game.step(1f / 60f)
        }
        assertEquals("a tiempo no se choca", 0, game.hits)
        assertTrue("en 40 s caben muchos saltos: ${game.score}", game.score >= 15)
        assertEquals("el récord sube con la racha", game.score, game.best)
        assertTrue(game.bestChanged)
        game.markBestSaved()
        assertFalse(game.bestChanged)
    }

    @Test
    fun theGameIsFairAtEveryPaceAndEverySeed() {
        for (seed in 1..8) {
            val game = engine(seed = seed)
            repeat(60 * 90) {
                game.botJumps()
                game.step(1f / 60f)
            }
            assertEquals("semilla $seed: no se debe chocar saltando a tiempo", 0, game.hits)
        }
    }

    @Test
    fun aLateFrameNeverMovesTheWorldMoreThanFiftyMilliseconds() {
        val late = engine()
        val normal = engine()
        late.step(0.016f)
        normal.step(0.016f)
        val before = late.obstacles.first().x
        late.step(5f)
        normal.step(RestGameEngine.MAX_STEP)
        assertEquals(before - late.speed * RestGameEngine.MAX_STEP, late.obstacles.first().x, 1e-3f)
        assertEquals(normal.obstacles.first().x, late.obstacles.first().x, 1e-3f)
    }

    @Test
    fun theDumbbellsNeverArriveCloserThanAJumpPlusABreather() {
        val game = engine(seed = 3)
        var minimumGap = Float.MAX_VALUE
        repeat(60 * 60) {
            game.botJumps()
            game.step(1f / 60f)
            val xs = game.obstacles.map { it.x }.sorted()
            for (i in 1 until xs.size) minimumGap = minOf(minimumGap, xs[i] - xs[i - 1])
        }
        val jumpPlusBreather = RestGameEngine.BASE_SPEED * (RestGameEngine.AIR_TIME + RestGameEngine.BREATHER)
        assertTrue("hueco mínimo $minimumGap < $jumpPlusBreather", minimumGap >= jumpPlusBreather - 0.5f)
    }

    @Test
    fun theSpeedRisesSlowlyAndStopsAtTheCap() {
        val game = engine()
        repeat(60 * 10) { game.step(1f / 60f) }
        // 10 s a +3,5 dp/s por segundo desde 150 (sin choques el ritmo sigue subiendo; un choque lo reinicia, por eso se mide pronto).
        assertTrue(game.speed in 150f..190f)
        val long = engine()
        repeat(60 * 4) { long.botJumps(); long.step(1f / 60f) }
        assertTrue(long.speed <= RestGameEngine.MAX_SPEED)
        val capped = RestGameEngine(random = Random(5)).also { it.worldWidth = 320f }
        repeat(60 * 120) { capped.botJumps(); capped.step(1f / 60f) }
        assertEquals(RestGameEngine.MAX_SPEED, capped.speed, 1e-3f)
    }

    @Test
    fun resettingKeepsTheRecordButClearsTheRun() {
        val game = engine(seed = 9)
        repeat(60 * 20) {
            game.botJumps()
            game.step(1f / 60f)
        }
        val best = game.best
        assertTrue(best > 0)
        game.reset()
        assertEquals(0, game.score)
        assertEquals(best, game.best)
        assertTrue(game.obstacles.isEmpty())
        assertEquals(RestGameEngine.BASE_SPEED, game.speed, 1e-3f)
        assertTrue(game.onGround)
    }

    @Test
    fun theHitBoxIsSmallerThanTheDrawing() {
        // La caja de choque del corredor (12 dp) es la mitad del dibujo (24 dp) y la de la mancuerna pierde 4 dp por lado y mide 14 de 20.
        assertTrue(RestGameEngine.RUNNER_HIT_RIGHT - RestGameEngine.RUNNER_HIT_LEFT < 24f)
        assertTrue(RestGameEngine.OBSTACLE_W - 2 * RestGameEngine.OBSTACLE_HIT_INSET < RestGameEngine.OBSTACLE_W)
        assertTrue(RestGameEngine.OBSTACLE_HIT_H < RestGameEngine.OBSTACLE_H)
    }
}
