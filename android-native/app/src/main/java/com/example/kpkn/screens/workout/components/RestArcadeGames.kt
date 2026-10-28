package com.example.kpkn.screens.workout.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Minijuegos del descanso (iteración 03, aprobada): «Salta las mancuernas», «Entre las pesas» y «Escuadrón retro». Dibujo crema sobre
 * negro; el melocotón solo para el jugador, los golpes y los potenciadores. Los motores avanzan por fotogramas en unidades de dp y no
 * conocen el reloj del descanso: el reloj manda desde fuera (congela el juego en pausa y al llegar a cero).
 */

internal val GameInk = Color(0xFFF2EEE6)
internal val GameMuted = Color(0xFFB9B5AE)
internal val GameFaint = Color(0xFF5C5A57)
internal val GamePeach = Color(0xFFF49A6E)
private val GameLine = Color(0x14FFFFFF)

internal class GameParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Color)
internal class GamePopup(val text: String, val x: Float, val y: Float, var t: Float = 0f, val color: Color = GameInk, val big: Boolean = false)

/** Lo común: tamaño, vidas, puntos, récord, partículas y avisos flotantes. */
internal abstract class ArcadeWorld(val w: Float, val h: Float, var best: Int, protected val random: Random, protected val reduced: Boolean) {
    var lives = 3; protected set
    var shake = 0f; protected set
    var t = 0f; protected set
    val parts = mutableListOf<GameParticle>()
    val pops = mutableListOf<GamePopup>()
    abstract val score: Int
    abstract val hint: String
    /** Avanza [dt] segundos; true cuando se acaban las vidas. */
    abstract fun update(dt: Float): Boolean
    abstract fun press(x: Float, y: Float)
    open fun move(x: Float, y: Float) {}
    open fun release() {}
    abstract fun extra(): String
    abstract fun draw(scope: DrawScope, text: TextMeasurer)

    protected fun rnd(a: Float, b: Float) = a + random.nextFloat() * (b - a)

    protected fun burst(x: Float, y: Float, n: Int, color: Color, speed: Float = 160f) {
        repeat(n) {
            val a = rnd(0f, 6.28f)
            val v = rnd(speed * .3f, speed)
            parts += GameParticle(x, y, cos(a) * v, sin(a) * v - 40f, rnd(.35f, .7f), color)
        }
    }

    protected fun stepEffects(dt: Float) {
        parts.removeAll { p ->
            p.life -= dt
            if (p.life <= 0f) true else { p.vy += 600f * dt; p.x += p.vx * dt; p.y += p.vy * dt; false }
        }
        pops.removeAll { p -> p.t += dt; p.t > .9f }
        if (score > best) best = score
    }

    protected fun DrawScope.drawEffects(text: TextMeasurer) {
        parts.forEach { p ->
            drawRect(p.color.copy(alpha = (p.life * 2f).coerceIn(0f, 1f)), Offset(p.x - 1.5f, p.y - 1.5f), Size(3f, 3f))
        }
        pops.forEach { p ->
            label(text, p.text, p.x, p.y - p.t * 34f, p.color.copy(alpha = 1f - p.t / .9f), if (p.big) 18f else 13f, FontWeight.ExtraBold, display = true)
        }
    }

    protected fun DrawScope.label(text: TextMeasurer, s: String, x: Float, y: Float, color: Color, size: Float = 9f, weight: FontWeight = FontWeight.SemiBold,
        display: Boolean = false) {
        // El lienzo ya va escalado a dp: el texto se mide a [size] dp fijos (no crece con la letra del sistema, como el resto del juego).
        val layout = text.measure(s, TextStyle(fontFamily = if (display) WizardFonts.display else WizardFonts.body, fontWeight = weight,
            fontSize = (size / (density * fontScale)).sp, color = color))
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height))
    }
}

private fun DrawScope.roundRect(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r, r))

private fun DrawScope.plate(x: Float, y: Float, r: Float, color: Color = GameInk) {
    drawCircle(color, r, Offset(x, y))
    drawCircle(Color.Black, r * .28f, Offset(x, y))
    drawCircle(Color(0x55000000), r * .68f, Offset(x, y), style = Stroke(1.5f))
}

private fun DrawScope.dumbbell(x: Float, y: Float, w: Float, h: Float) {
    roundRect(GameInk, x, y, w * .24f, h, 3f)
    roundRect(GameInk, x + w * .76f, y, w * .24f, h, 3f)
    drawRect(GameMuted, Offset(x + w * .24f, y + h * .4f), Size(w * .52f, h * .2f))
}

/** Vista previa animada del selector (84 × 84): la escena de cada juego en bucle. */
internal fun DrawScope.drawGamePreview(mode: RestArcadeMode, t: Float) {
    val w = size.width / density
    val h = size.height / density
    scaleToDp {
        drawRect(Color(0xFF0A0A0A), Offset.Zero, Size(w, h))
        when (mode) {
            RestArcadeMode.RUNNER -> {
                drawRect(Color(0x20FFFFFF), Offset(0f, 62f), Size(w, 2f))
                val x = (w + 30f) - (t * 60f) % (w + 60f)
                dumbbell(x, 48f, 26f, 14f)
                val j = max(0f, sin(t * 3f)) * 26f
                drawCircle(GameInk, 5f, Offset(24f, 40f - j))
                roundRect(GameInk, 19f, 45f - j, 10f, 12f, 3f)
            }
            RestArcadeMode.FLAPPY -> {
                val x = (w + 20f) - (t * 40f) % (w + 50f)
                roundRect(GameMuted, x, 0f, 22f, 24f, 4f)
                roundRect(GameMuted, x, 58f, 22f, 30f, 4f)
                plate(28f, 42f + sin(t * 4f) * 8f, 8f)
            }
            RestArcadeMode.SHOOTER -> {
                for (i in 0 until 4) {
                    val ex = 16f + i * 18f
                    val ey = 20f + sin(t * 2f + i) * 4f
                    drawPath(Path().apply { moveTo(ex, ey + 6f); lineTo(ex - 7f, ey - 4f); lineTo(ex + 7f, ey - 4f); close() }, GameInk)
                }
                val sx = 42f + sin(t * 1.5f) * 22f
                drawPath(Path().apply { moveTo(sx, 58f); lineTo(sx - 8f, 72f); lineTo(sx + 8f, 72f); close() }, GameInk)
                drawRect(GameInk, Offset(sx - 1f, 40f - (t * 80f) % 20f), Size(2f, 6f))
            }
        }
    }
}

internal inline fun DrawScope.scaleToDp(block: DrawScope.() -> Unit) {
    drawContext.transform.scale(density, density, Offset.Zero)
    try { block() } finally { drawContext.transform.scale(1f / density, 1f / density, Offset.Zero) }
}

internal fun newArcadeWorld(mode: RestArcadeMode, w: Float, h: Float, best: Int, reduced: Boolean, random: Random = Random.Default): ArcadeWorld =
    when (mode) {
        RestArcadeMode.RUNNER -> RunnerWorld(w, h, best, random, reduced)
        RestArcadeMode.FLAPPY -> FlappyWorld(w, h, best, random, reduced)
        RestArcadeMode.SHOOTER -> ShooterWorld(w, h, best, random, reduced)
    }

/* ---------- 1 · Salta las mancuernas ---------- */

internal class RunnerWorld(w: Float, h: Float, best: Int, random: Random, reduced: Boolean) : ArcadeWorld(w, h, best, random, reduced) {
    private class Obstacle(val kind: Int, val w: Float, val h: Float, var x: Float, var cleared: Boolean = false, var hit: Boolean = false)
    private class Pick(val chalk: Boolean, var x: Float, var y: Float, var got: Boolean = false)

    private val ground = h * .74f
    private val x = w * .22f
    private var y = ground
    private var vy = 0f
    private var onGround = true
    private var coyote = 0f
    private var buffer = 0f
    private var holding = false
    private var jumps = 0
    private var chalk = 0f
    private var speed = 250f
    private var dist = 0f
    private var next = 0f
    private var inv = 0f
    private var combo = 0
    private var points = 0f
    private val obs = mutableListOf<Obstacle>()
    private val picks = mutableListOf<Pick>()
    override val score get() = points.toInt()
    override val hint = "Toca para saltar · mantén para saltar más alto"

    override fun press(x: Float, y: Float) { buffer = .12f; holding = true }
    override fun release() { holding = false }
    override fun extra() = when {
        combo > 3 -> "Racha ×${min(5, 1 + combo / 4)}"
        chalk > 0f -> "Doble salto"
        else -> ""
    }

    private fun spawn() {
        val kinds = listOf(Triple(0, 34f, 18f), Triple(1, 24f, 28f), Triple(2, 30f, 42f))
        val lvl = min(2, floor(dist / 1400f).toInt())
        val pick = kinds[floor(rnd(0f, lvl + 1.2f)).toInt() % 3]
        obs += Obstacle(pick.first, pick.second, pick.third, w + 20f)
        if (random.nextFloat() < .3f && dist > 500f) obs += Obstacle(0, 34f, 18f, w + 20f + pick.second + rnd(58f, 78f))
        if (random.nextFloat() < .45f) picks += Pick(random.nextFloat() < .18f && chalk <= 0f, w + rnd(90f, 160f), ground - rnd(70f, 110f))
        next = rnd(.95f, 1.6f) * max(.62f, 1f - speed / 1400f) * (260f / speed) * 1.55f
    }

    override fun update(dt: Float): Boolean {
        t += dt
        speed = min(470f, 250f + t * 2.4f)
        val dx = speed * dt
        dist += dx; points += dx * .05f
        inv = max(0f, inv - dt); chalk = max(0f, chalk - dt); shake = max(0f, shake - dt * 30f)
        buffer = max(0f, buffer - dt); coyote = if (onGround) .09f else max(0f, coyote - dt)
        val maxJumps = if (chalk > 0f) 2 else 1
        if (buffer > 0f && (coyote > 0f || jumps < maxJumps)) {
            vy = -560f; onGround = false; coyote = 0f; buffer = 0f; jumps++
            burst(x, y, 5, GameMuted, 90f)
        }
        val gravity = if (vy < 0f && holding) 1500f else 2900f
        vy += gravity * dt; y += vy * dt
        if (y >= ground) {
            if (!onGround) burst(x, ground, 4, GameFaint, 70f)
            y = ground; vy = 0f; onGround = true; jumps = 0
        }
        next -= dt
        if (next <= 0f) spawn()
        for (o in obs) {
            o.x -= dx
            val px = x - 11f; val pw = 22f; val ph = 34f; val py = y - ph
            val hitNow = px < o.x + o.w - 5f && px + pw > o.x + 5f && py + ph > ground - o.h + 4f
            if (hitNow && inv <= 0f && !o.hit) {
                o.hit = true; lives--; inv = 1.3f; combo = 0; shake = if (reduced) 0f else 7f
                burst(x, y - 17f, 14, GamePeach, 200f); pops += GamePopup("−1", x, y - 48f, color = GamePeach)
                if (lives <= 0) return true
            }
            if (!o.cleared && o.x + o.w < px && !o.hit) {
                o.cleared = true; combo++
                val gap = (ground - o.h) - y
                val near = gap < 14f && !onGround
                val m = min(5, 1 + combo / 4)
                val pts = (if (near) 25 else 10) * m
                points += pts
                pops += GamePopup(if (near) "¡Justo! +$pts" else "+$pts", o.x + o.w / 2f, ground - o.h - 14f, big = near)
            }
        }
        obs.removeAll { it.x <= -60f }
        for (p in picks) {
            p.x -= dx; p.y += sin(t * 5f + p.x * .02f) * .3f
            if (!p.got && abs(p.x - x) < 20f && abs(p.y - (y - 17f)) < 26f) {
                p.got = true
                if (p.chalk) { chalk = 8f; pops += GamePopup("Doble salto · 8 s", p.x, p.y - 14f, big = true) }
                else { points += 30f; pops += GamePopup("+30", p.x, p.y - 10f) }
                burst(p.x, p.y, 10, GameInk, 140f)
            }
        }
        picks.removeAll { it.x <= -30f || it.got }
        stepEffects(dt)
        return false
    }

    override fun draw(scope: DrawScope, text: TextMeasurer) = with(scope) {
        drawRect(Color.Black, Offset.Zero, Size(w, h))
        for (i in 0 until 7) {
            val rx = ((i * 90f - dist * .15f) % (w + 90f) + w + 90f) % (w + 90f) - 45f
            drawRect(Color(0x09FFFFFF), Offset(rx, ground - 150f), Size(40f, 150f), style = Stroke(1f))
            drawLine(Color(0x09FFFFFF), Offset(rx - 6f, ground - 118f), Offset(rx + 46f, ground - 118f), 1f)
        }
        for (i in 0 until 14) {
            val fx = ((i * 42f - dist * .55f) % (w + 42f) + w + 42f) % (w + 42f) - 21f
            drawRect(Color(0x08FFFFFF), Offset(fx, ground + 12f), Size(24f, 2f))
        }
        drawRect(GameLine, Offset(0f, ground), Size(w, 2f))
        val bestX = x + (best / .05f - dist)
        if (best > 0 && bestX > 0f && bestX < w) {
            drawLine(Color(0x40FFFFFF), Offset(bestX, ground - 60f), Offset(bestX, ground), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)))
            label(text, "RÉCORD", bestX, ground - 66f, GameFaint)
        }
        for (o in obs) {
            val alpha = if (o.hit) .35f else 1f
            when (o.kind) {
                0 -> {
                    roundRect(GameInk.copy(alpha = alpha), o.x, ground - o.h, o.w * .24f, o.h, 3f)
                    roundRect(GameInk.copy(alpha = alpha), o.x + o.w * .76f, ground - o.h, o.w * .24f, o.h, 3f)
                    drawRect(GameMuted.copy(alpha = alpha), Offset(o.x + o.w * .24f, ground - o.h + o.h * .4f), Size(o.w * .52f, o.h * .2f))
                }
                1 -> {
                    drawCircle(GameInk.copy(alpha = alpha), o.w * .5f, Offset(o.x + o.w / 2f, ground - o.h * .38f))
                    drawArc(GameInk.copy(alpha = alpha), 180f, 180f, false, Offset(o.x + o.w / 2f - o.w * .3f, ground - o.h * .78f - o.w * .3f),
                        Size(o.w * .6f, o.w * .6f), style = Stroke(4f))
                }
                else -> for (i in 0 until 4) {
                    val inset = if (i % 2 == 1) 2f else 0f
                    roundRect((if (i % 2 == 1) GameMuted else GameInk).copy(alpha = alpha), o.x + inset, ground - (i + 1) * o.h / 4f,
                        o.w - inset * 2f, o.h / 4f - 2f, 3f)
                }
            }
        }
        for (p in picks) {
            if (p.chalk) {
                roundRect(GameInk, p.x - 8f, p.y - 10f, 16f, 20f, 4f)
                label(text, "2×", p.x, p.y + 6f, Color.Black, 9f, FontWeight.ExtraBold, display = true)
            } else {
                drawRoundRect(GameInk, Offset(p.x - 6f, p.y - 9f), Size(12f, 18f), CornerRadius(3f, 3f), style = Stroke(2f))
                drawRect(GameMuted, Offset(p.x - 4f, p.y - 2f), Size(8f, 8f))
            }
        }
        if (!(inv > 0f && floor(inv * 12f).toInt() % 2 == 1)) {
            val run = if (onGround) sin(t * 22f) else .6f
            val body = if (chalk > 0f) GamePeach else GameInk
            drawCircle(body, 6f, Offset(x, y - 30f))
            roundRect(body, x - 6f, y - 24f, 12f, 14f, 4f)
            fun limb(ax: Float, ay: Float, bx: Float, by: Float) = drawLine(body, Offset(ax, ay), Offset(bx, by), 4f, StrokeCap.Round)
            limb(x - 2f, y - 11f, x - 2f - run * 7f, y - 1f)
            limb(x + 2f, y - 11f, x + 2f + run * 7f, y - 1f)
            limb(x - 5f, y - 20f, x - 10f + run * 4f, y - 13f)
            limb(x + 5f, y - 20f, x + 10f - run * 4f, y - 13f)
        }
        if (chalk > 0f) drawRect(GamePeach, Offset(x - 14f, y - 44f), Size(28f * (chalk / 8f), 2f))
        drawEffects(text)
    }
}

/* ---------- 2 · Entre las pesas ---------- */

internal class FlappyWorld(w: Float, h: Float, best: Int, random: Random, reduced: Boolean) : ArcadeWorld(w, h, best, random, reduced) {
    private class Column(var x: Float, val mid: Float, val gapH: Float, val move: Float, val phase: Float, var passed: Boolean = false, var cur: Float = mid)

    private val x = w * .3f
    private var y = h * .42f
    private var vy = 0f
    private val cols = mutableListOf<Column>()
    private var next = .4f
    private var speed = 150f
    private var inv = 0f
    private var points = 0
    private var chain = 0
    private var started = false
    private var lastGap = h * .42f
    override val score get() = points
    override val hint = "Toca para impulsar · pasa por el centro para encadenar «Perfecto»"

    override fun press(x: Float, y: Float) {
        started = true; vy = -360f
        burst(this.x - 10f, this.y + 6f, 3, GameFaint, 60f)
    }
    override fun extra() = if (chain > 1) "Perfecto ×${min(4, 1 + chain)}" else ""

    override fun update(dt: Float): Boolean {
        t += dt; inv = max(0f, inv - dt)
        if (!started) { y = h * .42f + sin(t * 3f) * 6f; stepEffects(dt); return false }
        vy = min(520f, vy + 1250f * dt); y += vy * dt
        val lvl = min(1f, points / 40f)
        speed = 150f + lvl * 60f
        next -= dt
        if (next <= 0f) {
            val gapH = 168f - lvl * 42f
            val mid = (lastGap + rnd(-110f, 110f) * (.5f + lvl * .5f)).coerceIn(gapH / 2f + 40f, h - gapH / 2f - 60f)
            lastGap = mid
            cols += Column(w + 30f, mid, gapH, if (lvl > .5f && random.nextFloat() < .4f) rnd(18f, 34f) else 0f, rnd(0f, 6f))
            next = 1.55f - lvl * .25f
        }
        for (c in cols) {
            c.x -= speed * dt
            val mid = c.mid + if (c.move != 0f) sin(t * 1.4f + c.phase) * c.move else 0f
            c.cur = mid
            val inX = x + 11f > c.x && x - 11f < c.x + 34f
            val out = y - 11f < mid - c.gapH / 2f || y + 11f > mid + c.gapH / 2f
            if (inX && out && inv <= 0f) {
                lives--; inv = 1.6f; chain = 0; vy = -120f
                y = y.coerceIn(mid - c.gapH / 2f + 14f, mid + c.gapH / 2f - 14f)
                burst(x, y, 14, GamePeach, 180f); pops += GamePopup("−1", x, y - 20f, color = GamePeach)
                if (lives <= 0) return true
            }
            if (!c.passed && c.x + 34f < x) {
                c.passed = true
                val perfect = abs(y - mid) < 14f
                chain = if (perfect) chain + 1 else 0
                val m = if (perfect) min(4, 1 + chain) else 1
                points += m
                pops += GamePopup(if (perfect) "Perfecto ×$m" else "+1", x, y - 22f, big = perfect)
                if (perfect) burst(x, y, 8, GameInk, 120f)
            }
        }
        cols.removeAll { it.x <= -50f }
        if (y > h - 20f || y < 10f) {
            if (inv <= 0f) {
                lives--; inv = 1.6f; chain = 0
                burst(x, y, 12, GamePeach, 160f)
                if (lives <= 0) return true
            }
            y = y.coerceIn(20f, h - 30f); vy = -260f
        }
        stepEffects(dt)
        return false
    }

    override fun draw(scope: DrawScope, text: TextMeasurer) = with(scope) {
        drawRect(Color.Black, Offset.Zero, Size(w, h))
        // El fondo respira en ciclos de 4 s: inhala / exhala.
        val breath = (sin(t * PI.toFloat() / 4f) + 1f) / 2f
        drawCircle(GameInk.copy(alpha = .025f + breath * .03f), 60f + breath * 70f, Offset(w / 2f, h * .45f))
        label(text, if (breath > .5f) "INHALA" else "EXHALA", w / 2f, h * .45f + 10f, GameFaint)
        for (k in cols) {
            val m = k.cur
            val top = m - k.gapH / 2f
            val bot = m + k.gapH / 2f
            drawRect(Color(0x18FFFFFF), Offset(k.x + 14f, 0f), Size(6f, top))
            drawRect(Color(0x18FFFFFF), Offset(k.x + 14f, bot), Size(6f, h - bot))
            for (i in 0 until 3) {
                val color = if (i == 0) GameInk else GameMuted
                roundRect(color, k.x + i * 3f, top - 14f - i * 12f, 34f - i * 6f, 10f, 3f)
                roundRect(color, k.x + i * 3f, bot + 4f + i * 12f, 34f - i * 6f, 10f, 3f)
            }
            if (!k.passed) drawLine(Color(0x22FFFFFF), Offset(k.x, m), Offset(k.x + 34f, m), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f)))
        }
        if (!(inv > 0f && floor(inv * 12f).toInt() % 2 == 1)) {
            rotate((vy / 900f).coerceIn(-.5f, .8f) * 180f / PI.toFloat(), Offset(x, y)) {
                plate(x, y, 12f, if (chain > 1) GamePeach else GameInk)
            }
        }
        drawEffects(text)
        if (!started) label(text, "Toca para impulsar el disco", w / 2f, h * .62f, GameMuted, 12f)
    }
}

/* ---------- 3 · Escuadrón retro ---------- */

internal class ShooterWorld(w: Float, h: Float, best: Int, random: Random, reduced: Boolean) : ArcadeWorld(w, h, best, random, reduced) {
    private class Shot(var x: Float, var y: Float)
    private class Enemy(var hp: Int, var t: Float, val pattern: Int, val i: Int, val count: Int, var x: Float = 0f, var y: Float = -30f, var fire: Float, var dead: Boolean = false)
    private class Bullet(var x: Float, var y: Float, val vx: Float, val vy: Float, var grazed: Boolean = false)
    private class Power(val x: Float, var y: Float, val double: Boolean, var got: Boolean = false)
    private class Boss(var x: Float, var y: Float, var hp: Int, val max: Int, var t: Float = 0f)

    private var x = w / 2f
    private var y = h - 70f
    private var tx = x
    private var ty = y
    private val shots = mutableListOf<Shot>()
    private val enemies = mutableListOf<Enemy>()
    private val bullets = mutableListOf<Bullet>()
    private val powers = mutableListOf<Power>()
    private var fire = 0f
    private var wave = 0
    private var waveT = 1f
    private var inv = 0f
    private var points = 0
    private var double = 0f
    private var shield = false
    private var boss: Boss? = null
    private var drag: FloatArray? = null
    override val score get() = points
    override val hint = "Arrastra en cualquier punto para mover la nave · dispara sola"

    // Control relativo: se arrastra en cualquier punto y la nave sigue al dedo sin quedar debajo.
    override fun press(x: Float, y: Float) { drag = floatArrayOf(x, y, tx, ty) }
    override fun move(x: Float, y: Float) {
        val d = drag ?: return
        tx = (d[2] + (x - d[0]) * 1.15f).coerceIn(16f, w - 16f)
        ty = (d[3] + (y - d[1]) * 1.15f).coerceIn(h * .45f, h - 30f)
    }
    override fun release() { drag = null }
    override fun extra() = when {
        boss != null -> "Jefe"
        double > 0f -> "Disparo doble"
        wave > 0 -> "Oleada $wave"
        else -> ""
    }

    private fun spawnWave() {
        wave++
        if (wave % 4 == 0) {
            boss = Boss(w / 2f, -60f, 40 + wave * 4, 40 + wave * 4)
            pops += GamePopup("Jefe", w / 2f, h * .3f, big = true)
            return
        }
        val pattern = wave % 3
        val count = 6 + min(6, wave)
        repeat(count) { i -> enemies += Enemy(1 + wave / 5, -i * .22f, pattern, i, count, fire = rnd(1.2f, 3f)) }
        pops += GamePopup("Oleada $wave", w / 2f, h * .3f, big = true)
    }

    override fun update(dt: Float): Boolean {
        t += dt; inv = max(0f, inv - dt); double = max(0f, double - dt); shake = max(0f, shake - dt * 30f)
        x += (tx - x) * min(1f, dt * 14f); y += (ty - y) * min(1f, dt * 14f)
        fire -= dt
        if (fire <= 0f) {
            fire = .17f
            if (double > 0f) { shots += Shot(x - 7f, y - 14f); shots += Shot(x + 7f, y - 14f) } else shots += Shot(x, y - 14f)
        }
        shots.forEach { it.y -= 560f * dt }
        shots.removeAll { it.y <= -10f }
        if (enemies.isEmpty() && boss == null) { waveT -= dt; if (waveT <= 0f) { spawnWave(); waveT = 1.2f } }
        for (e in enemies) {
            e.t += dt
            if (e.t < 0f) continue
            val p = e.t
            // Patrones del prototipo por oleada: línea, V y serpiente.
            when (e.pattern) {
                0 -> { e.x = ((e.i + .5f) / e.count) * w + sin(p * 1.2f) * 24f; e.y = min(70f + (e.i % 2) * 28f, p * 180f - 30f) }
                1 -> { val k = e.i - (e.count - 1) / 2f; e.x = w / 2f + k * 30f + sin(p) * 30f; e.y = min(60f + abs(k) * 20f, p * 170f - 30f) }
                else -> { e.x = w / 2f + sin(p * 2f + e.i * .5f) * (w * .36f); e.y = p * 60f - 20f + e.i * 2f }
            }
            e.fire -= dt
            if (e.fire <= 0f && wave > 1 && e.y > 0f) {
                e.fire = rnd(2.2f, 4f) - min(1.2f, wave * .08f)
                val a = atan2(y - e.y, x - e.x)
                bullets += Bullet(e.x, e.y, cos(a) * 150f, sin(a) * 150f)
            }
        }
        boss?.let { b ->
            b.t += dt; b.y = min(90f, b.y + 60f * dt); b.x = w / 2f + sin(b.t * .8f) * (w * .3f)
            if (b.y >= 90f && floor(b.t * 10f).toInt() % 9 == 0 && floor((b.t - dt) * 10f).toInt() % 9 != 0) {
                for (i in 0 until 8) {
                    val a = PI.toFloat() / 2f + (i - 3.5f) * .22f + sin(b.t) * .2f
                    bullets += Bullet(b.x, b.y + 20f, cos(a) * 140f, sin(a) * 140f)
                }
            }
        }
        for (s in shots) {
            for (e in enemies) {
                if (e.t >= 0f && !e.dead && abs(s.x - e.x) < 13f && abs(s.y - e.y) < 12f) {
                    s.y = -99f; e.hp--
                    if (e.hp <= 0) {
                        e.dead = true; points += 10; burst(e.x, e.y, 9, GameInk, 150f)
                        if (random.nextFloat() < .08f) powers += Power(e.x, e.y, random.nextFloat() < .5f)
                    }
                }
            }
            val b = boss
            if (b != null && abs(s.x - b.x) < 38f && abs(s.y - b.y) < 22f) {
                s.y = -99f; b.hp--; burst(s.x, s.y, 2, GameMuted, 80f)
                if (b.hp <= 0) {
                    points += 200; burst(b.x, b.y, 40, GamePeach, 260f); pops += GamePopup("+200", b.x, b.y, big = true)
                    powers += Power(b.x, b.y, true); boss = null; shake = if (reduced) 0f else 8f
                }
            }
        }
        enemies.removeAll { it.dead || it.y >= h + 30f }
        for (b in bullets) {
            b.x += b.vx * dt; b.y += b.vy * dt
            val d = hypot(b.x - x, b.y - y)
            if (d < 9f && inv <= 0f) {
                b.y = h + 99f
                if (shield) { shield = false; inv = .8f; pops += GamePopup("Escudo", x, y - 24f) }
                else {
                    lives--; inv = 1.5f; shake = if (reduced) 0f else 7f; burst(x, y, 16, GamePeach, 200f)
                    if (lives <= 0) return true
                }
            } else if (d < 22f && !b.grazed) {
                b.grazed = true; points += 5; pops += GamePopup("Roce +5", x, y - 26f)
            }
        }
        bullets.removeAll { it.y >= h + 20f || it.y <= -20f || it.x <= -20f || it.x >= w + 20f }
        for (p in powers) {
            p.y += 90f * dt
            if (hypot(p.x - x, p.y - y) < 20f) {
                p.got = true
                if (p.double) { double = 10f; pops += GamePopup("Disparo doble", x, y - 30f, big = true) }
                else { shield = true; pops += GamePopup("Escudo", x, y - 30f, big = true) }
            }
        }
        powers.removeAll { it.got || it.y >= h + 20f }
        stepEffects(dt)
        return false
    }

    override fun draw(scope: DrawScope, text: TextMeasurer) = with(scope) {
        drawRect(Color.Black, Offset.Zero, Size(w, h))
        for (i in 0 until 40) {
            val sy = (i * 53f + t * (20f + (i % 3) * 25f)) % h
            drawRect(if (i % 3 != 0) Color(0x12FFFFFF) else Color(0x26FFFFFF), Offset((i * 97f) % w, sy), Size(1.5f, 1.5f))
        }
        shots.forEach { drawRect(GameInk, Offset(it.x - 1.5f, it.y - 6f), Size(3f, 9f)) }
        for (e in enemies) {
            if (e.t < 0f) continue
            drawPath(Path().apply {
                moveTo(e.x, e.y + 9f); lineTo(e.x - 11f, e.y - 6f); lineTo(e.x - 4f, e.y - 2f); lineTo(e.x, e.y - 8f)
                lineTo(e.x + 4f, e.y - 2f); lineTo(e.x + 11f, e.y - 6f); close()
            }, if (e.hp > 1) GameMuted else GameInk)
        }
        boss?.let { b ->
            roundRect(GameInk, b.x - 38f, b.y - 16f, 76f, 32f, 10f)
            drawRect(Color.Black, Offset(b.x - 26f, b.y - 3f), Size(52f, 6f))
            drawRect(GameMuted, Offset(b.x - 34f, b.y + 14f), Size(8f, 10f))
            drawRect(GameMuted, Offset(b.x + 26f, b.y + 14f), Size(8f, 10f))
            drawRect(Color(0x18FFFFFF), Offset(20f, 8f), Size(w - 40f, 4f))
            drawRect(GamePeach, Offset(20f, 8f), Size((w - 40f) * b.hp / b.max, 4f))
        }
        bullets.forEach { drawCircle(GamePeach, 3.5f, Offset(it.x, it.y)) }
        powers.forEach { p ->
            drawCircle(GameInk, 9f, Offset(p.x, p.y), style = Stroke(2f))
            label(text, if (p.double) "2×" else "E", p.x, p.y + 6f, GameInk, 9f, FontWeight.ExtraBold, display = true)
        }
        if (!(inv > 0f && floor(inv * 12f).toInt() % 2 == 1)) {
            drawPath(Path().apply { moveTo(x, y - 14f); lineTo(x - 12f, y + 10f); lineTo(x, y + 5f); lineTo(x + 12f, y + 10f); close() }, GameInk)
            drawRect(GamePeach, Offset(x - 2f, y + 6f), Size(4f, 4f + random.nextFloat() * 4f))
            if (shield) drawCircle(Color(0x80F2EEE6), 20f, Offset(x, y), style = Stroke(1.5f))
        }
        drawEffects(text)
    }
}
