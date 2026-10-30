package com.example.kpkn.screens.workout.components

import android.graphics.BlurMaskFilter
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.example.kpkn.domain.workout.EffortHeat
import com.example.kpkn.screens.workout.effortParticleCount
import com.example.kpkn.screens.workout.effortParticlePeriodSeconds
import com.example.kpkn.ui.design.depth.KpknDepthCalibration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pulido v1 · 13 · el canvas `.lv-fx` del slider de esfuerzo del lienzo (`lvEffort` en `scenes-lv.js`), portado tal cual: resplandor a lo
 * largo del relleno y halo del pulgar (sube con el nivel de calor), un reflejo que viaja por el relleno (nivel 3 o más), brasas que suben
 * (nivel 2 o más), la lengua de luz sobre el pulgar (nivel 4 o más) y el destello de llegada (anillo, resplandor y chispas, 460 ms).
 * Todo cae dentro de la banda táctil de 44 dp. Con [still] (movimiento reducido) no hay brasas, reflejo, lengua ni destello: queda el halo y
 * el resplandor fijos a la intensidad del nivel.
 *
 * pulido:lv-J12 lv-J13 (bucle de cuadros y precalentado de 110 cuadros al empezar) lv-X05
 */
@Stable
internal class LiveEffortFx(private val still: Boolean, private val random: Random = Random.Default) {
    /** Cambia en cada cuadro: el dibujo lo lee y se invalida solo. */
    var frame by mutableLongStateOf(0L)
        private set

    private class Ember(var x: Float, val life: Float, var t: Float, val rise: Float, val size: Float, val drift: Float, val color: Int)
    private class Spark(val angle: Float, val length: Float)

    private val embers = ArrayList<Ember>()
    private var sparks: List<Spark> = emptyList()
    private var flash = 0f
    private var flashK = 1f
    private var clock = 0f
    private var shown = 0f // nivel suavizado (`Ld`)
    private var target = 0f // nivel pedido (`Lt`)

    /** El nivel que se está viendo, para quien necesite el mismo número que el dibujo (relleno y pulgar). */
    val level: Float get() = shown

    /** Fija el nivel de calor (0 a 5); en reposo (`still`) o la primera vez salta sin suavizar. */
    fun setTarget(level: Float, immediate: Boolean = false) {
        target = level
        if (still || immediate) shown = level
        frame++
    }

    /** Dispara el destello de llegada: [full] = al llegar al fallo (9 chispas); si no, el aviso corto al pasar de RPE 9,5 (5 chispas, 55 %). */
    fun arrive(full: Boolean) {
        if (still) return
        flash = if (full) 1f else .8f
        flashK = if (full) 1f else .55f
        sparks = if (full) List(9) { k -> Spark((-PI * .95 + PI * .9 * k / 8 + (random.nextFloat() - .5f) * .2).toFloat(), 9f + random.nextFloat() * 7f) }
        else List(5) { k -> Spark((-PI * .9 + PI * .8 * k / 4).toFloat(), 6f + random.nextFloat() * 4f) }
    }

    /** ¿Hay algo que mover? (el bucle de cuadros solo corre entonces.) */
    val animating: Boolean get() = !still && (target > 0f || shown > 0f || flash > 0f || embers.isNotEmpty())

    /** Un paso de [dt] segundos (el `frame` del lienzo): suaviza el nivel y mueve brasas y destello. [x] = centro del pulgar, en dp. */
    fun step(dt: Float, x: Float) {
        clock += dt
        shown += (target - shown) * min(1f, dt * 7f)
        if (kotlin.math.abs(target - shown) < .005f) shown = target
        spawn(dt, x)
        val it = embers.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.t += dt
            if (e.t / e.life >= 1f) it.remove()
        }
        if (flash > 0f) flash = max(0f, flash - dt / .46f)
        frame++
    }

    /** El precalentado del lienzo (`for k<110: clock += 1/60; draw(1/60)`): las brasas ya existen en el primer cuadro. */
    fun prewarm(x: Float) {
        if (still) return
        repeat(110) { step(1f / 60f, x) }
    }

    private fun spawn(dt: Float, x: Float) {
        val l = shown
        if (still || l < 1.9f || x <= 0f) return
        val count = effortParticleCount(l)
        val period = effortParticlePeriodSeconds(l)
        val rate = count / period
        if (random.nextFloat() < rate * dt * 1.5f && embers.size < count + 4) {
            val span = min(x - 4f, 150f)
            val colors = intArrayOf(0xF7CF73, 0xF49A6E, 0xFFE2B0, 0xF06A3E)
            embers += Ember(
                x = x - random.nextFloat().toDouble().pow(1.7).toFloat() * span,
                life = period * (.55f + random.nextFloat() * .4f),
                t = 0f,
                rise = 7f + random.nextFloat() * (4.5f + l * 1.25f),
                size = 1.1f + random.nextFloat() * (1.2f + l * .4f),
                drift = (random.nextFloat() - .5f) * 9f,
                color = colors[random.nextInt(if (l >= 4f) 4 else 3)],
            )
        }
    }

    /**
     * Dibuja un cuadro. El origen es la esquina superior izquierda del lienzo de 44 dp (ya con el margen de 14 dp a la izquierda);
     * [x] es el centro del pulgar y [barTop]/[barBottom] no hacen falta: la pista va a y = 22 dp.
     */
    fun draw(scope: DrawScope, x: Float, height: Float = 44f) = with(scope) {
        val dp = density
        val y = height / 2f
        val l = shown
        val fire = EffortHeat.fireAt(l)
        if (fire == null && flash <= 0f && embers.isEmpty()) return@with
        val breathe = 1f + .08f * sin(clock * (if (l >= 5f) 5.2f else 2.6f))
        if (fire != null) {
            val a = fire.alpha
            // Resplandor a lo largo del relleno y halo del pulgar (siempre dentro de la banda).
            blurRect(6f, Color(244, 154, 110, (min(.8f, a * .95f) * 255).toInt()), max(4f, x - 120f) * dp, (y - 2.5f) * dp,
                max(0f, min(116f, x - 4f)) * dp, 5f * dp)
            blurCircle(6f, Color(255, 226, 176, (min(.9f, a * 1.15f * breathe) * 255).toInt()), x * dp, y * dp, (8f + l * 1.5f) * breathe * dp)
            // Reflejo que viaja por el relleno hacia el pulgar (calor en movimiento).
            if (!still && l >= 3f && x > 40f) {
                val speed = .5f + (l - 3f) * .28f
                val phase = (clock * speed) % 1f
                val hx = 8f + (x - 8f - 8f) * phase
                val ha = sin(PI.toFloat() * phase) * (.1f + .06f * l)
                blurRect(3.5f, Color(255, 236, 206, (ha.coerceIn(0f, 1f) * 255).toInt()), (hx - 14f) * dp, (y - 3f) * dp, 28f * dp, 6f * dp)
            }
        }
        // Brasas: suben desde el relleno, siempre dentro de la banda.
        for (e in embers) {
            val k = e.t / e.life
            val ease = 1f - (1f - k) * (1f - k)
            val px = e.x + e.drift * ease
            val py = y - 4f - e.rise * ease
            val rgb = e.color
            drawCircle(Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, (sin(PI.toFloat() * k) * .95f * 255).toInt().coerceIn(0, 255)),
                e.size * (1f - .35f * k) * dp, Offset(px * dp, py * dp))
        }
        // Lengua de luz sobre el pulgar (cerca del fallo).
        if (!still && l >= 4f) {
            val fl = (sin(clock * 13f) + sin(clock * 7.3f + 1f) + 2f) / 4f
            val h = (l - 3.2f) * 5.2f * (.65f + .35f * fl)
            val path = Path().apply {
                moveTo((x - 2.6f) * dp, (y - 13f) * dp)
                quadraticTo(x * dp, (y - 13f - h * 1.15f) * dp, (x + 2.6f) * dp, (y - 13f) * dp)
                close()
            }
            drawPath(path, Brush.verticalGradient(
                listOf(Color(255, 226, 176, ((.5f + .3f * fl) * 255).toInt()), Color(244, 154, 110, 0)),
                startY = (y - 13f) * dp, endY = (y - 13f - h) * dp,
            ))
        }
        // Destello de llegada: anillo que se expande, resplandor y chispas cortas.
        if (flash > 0f) {
            val k = 1f - flash
            val r = 8f + 13f * (1f - (1f - k).pow(2.2f))
            drawCircle(Color(255, 235, 200, (flash * .85f * flashK * 255).toInt().coerceIn(0, 255)), r * dp, Offset(x * dp, y * dp), style = Stroke(1.6f * dp))
            drawCircle(
                Brush.radialGradient(listOf(Color(255, 241, 200, (flash * .7f * flashK * 255).toInt().coerceIn(0, 255)), Color(255, 241, 200, 0)),
                    center = Offset(x * dp, y * dp), radius = 21f * dp),
                21f * dp, Offset(x * dp, y * dp),
            )
            for (s in sparks) {
                val r0 = 10f + k * 14f
                val r1 = r0 + s.length * (1f - k)
                drawLine(Color(255, 226, 176, (flash * flashK * 255).toInt().coerceIn(0, 255)),
                    Offset((x + cos(s.angle) * r0) * dp, (y + sin(s.angle) * r0 * .8f) * dp),
                    Offset((x + cos(s.angle) * r1) * dp, (y + sin(s.angle) * r1 * .8f) * dp), 1.3f * dp)
            }
        }
    }

    /** `g.filter = 'blur(Npx)'` sobre un rectángulo: desenfoque gaussiano de CSS (σ = N dp) con la calibración de la profundidad. */
    private fun DrawScope.blurRect(sigmaDp: Float, color: Color, left: Float, top: Float, w: Float, h: Float) = drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRect(left, top, left + w, top + h, blurPaint(sigmaDp, color))
    }

    /** Lo mismo sobre un círculo. */
    private fun DrawScope.blurCircle(sigmaDp: Float, color: Color, cx: Float, cy: Float, radius: Float) = drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawCircle(cx, cy, radius, blurPaint(sigmaDp, color))
    }

    private fun DrawScope.blurPaint(sigmaDp: Float, color: Color) = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        // Antes de la API 28 el desenfoque de máscara no es fiable en capas de hardware: se dibuja nítido (como el velo de respaldo de la profundidad).
        if (android.os.Build.VERSION.SDK_INT >= 28) maskFilter = BlurMaskFilter(KpknDepthCalibration.skiaRadius(sigmaDp * density), BlurMaskFilter.Blur.NORMAL)
    }
}
