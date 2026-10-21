package com.example.kpkn.screens.workout

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Una mancuerna que llega desde la derecha: su borde izquierdo, en dp del mundo del juego. */
internal enum class RestHazard { DUMBBELL, BENCH, GAP }
internal enum class RestSupplement { JUMP, SPEED, LIFE }
internal class RestGameObstacle(var x: Float, var counted: Boolean = false, val kind: RestHazard = RestHazard.DUMBBELL) {
    val width: Float get() = when (kind) { RestHazard.GAP -> 56f; RestHazard.BENCH -> 38f; else -> 30f }
    val hitHeight: Float get() = when (kind) { RestHazard.GAP -> 7f; RestHazard.BENCH -> 29f; else -> 14f }
}
internal class RestGameSupplement(var x: Float, val kind: RestSupplement)

/**
 * Motor del minijuego del descanso (LV6, F8): un corredor salta mancuernas que llegan desde la derecha. Puro y sin reloj propio: el
 * lienzo le da el tiempo de cada cuadro y él solo mueve el mundo. No toca el estado del descanso: el descanso manda y, cuando acaba,
 * quien lo muestra cierra el juego.
 *
 * Todo va en dp lógicos (1 dp ≈ 1 px del prototipo) y por segundo, así que juega igual a 60 que a 120 cuadros por segundo. La física
 * es sencilla y justa: el salto dura 0,77 s y sube 70 dp; la caja de choque del corredor y de la mancuerna es algo más pequeña que
 * el dibujo; y entre dos mancuernas siempre cabe un salto entero más un respiro, también cuando la velocidad sube poco a poco.
 */
internal class RestGameEngine(
    initialBest: Int = 0,
    private val random: Random = Random.Default,
) {
    companion object {
        /** Posición fija del corredor (su borde izquierdo) y su caja de choque: más estrecha que el dibujo de 24 dp. */
        const val RUNNER_X = 60f
        const val RUNNER_HIT_LEFT = 6f
        const val RUNNER_HIT_RIGHT = 18f

        /** La mancuerna dibuja 30 × 20 dp; su caja de choque pierde 4 dp por lado y mide 14 dp de alto. */
        const val OBSTACLE_W = 30f
        const val OBSTACLE_H = 20f
        const val OBSTACLE_HIT_INSET = 4f
        const val OBSTACLE_HIT_H = 14f

        /** Gravedad y velocidad de salto (dp/s² y dp/s): 0,77 s en el aire y 70 dp de altura. */
        const val GRAVITY = 940f
        const val JUMP_SPEED = 362f

        /** Velocidad del mundo (dp/s): empieza en 150 y sube 3,5 por segundo hasta 260. */
        const val BASE_SPEED = 150f
        const val MAX_SPEED = 260f
        const val SPEED_GAIN = 3.5f

        /** Un cuadro nunca cuenta más de 50 ms: al volver de segundo plano el mundo no da un salto. */
        const val MAX_STEP = 0.05f

        /** Duración del destello rojo tras un choque (s). */
        const val HIT_FLASH = 0.25f

        /** Tras un choque se quitan las mancuernas que quedan a menos de esto del corredor (dp). */
        const val RESTART_CLEAR = 140f

        /** Tiempo que el corredor pasa en el aire (s) y respiro extra entre dos mancuernas (s). */
        const val AIR_TIME = 2f * JUMP_SPEED / GRAVITY
        const val BREATHER = 0.38f

        /** Margen aleatorio que se suma al hueco mínimo entre mancuernas (dp). */
        const val GAP_JITTER = 90f
    }

    /** Altura del corredor sobre el suelo (dp) y su velocidad vertical (dp/s, positiva hacia arriba). */
    var height = 0f
        private set
    var velocity = 0f
        private set

    /** Saltos de esta racha y mejor racha (récord). */
    var score = 0
        private set
    var best = initialBest
        private set

    /** Verdadero si [best] subió y aún no se ha guardado; quien lo guarda lo apaga con [markBestSaved]. */
    var bestChanged = false
        private set

    /** Velocidad actual del mundo (dp/s) y tiempo de destello que queda tras un choque (s). */
    var speed = BASE_SPEED
        private set
    var flash = 0f
        private set

    /** Choques desde que empezó el juego (para quien quiera marcar el golpe). */
    var hits = 0
        private set

    /** Segundos de juego acumulados. */
    var elapsed = 0f
        private set

    /** Ancho visible del mundo (dp): el lienzo lo fija con el ancho que tenga. */
    var worldWidth = 320f

    val obstacles: MutableList<RestGameObstacle> = mutableListOf()

    var lives = 3
        private set
    val ended: Boolean get() = lives <= 0
    var power: RestSupplement? = null
        private set
    var powerSeconds = 0f
        private set
    private var invulnerable = 0f
    private var spawned = 0
    val supplements = mutableListOf<RestGameSupplement>()
    private var nextGap = gapFor(BASE_SPEED)

    private fun gapFor(speed: Float): Float = speed * (AIR_TIME + BREATHER) + random.nextFloat() * GAP_JITTER

    /** El corredor está en el suelo. */
    val onGround: Boolean get() = height <= 0f && velocity == 0f

    /** Salta si está en el suelo. Devuelve si saltó. */
    fun jump(): Boolean {
        if (!onGround || ended) return false
        velocity = JUMP_SPEED * if (power == RestSupplement.JUMP) 1.25f else 1f
        return true
    }

    /** El récord ya está guardado. */
    fun markBestSaved() {
        bestChanged = false
    }

    /** Empieza de cero (la racha, la velocidad y las mancuernas); el récord se queda. */
    fun reset() {
        height = 0f
        velocity = 0f
        score = 0
        speed = BASE_SPEED
        flash = 0f
        obstacles.clear()
        supplements.clear()
        lives = 3
        power = null
        powerSeconds = 0f
        invulnerable = 0f
        spawned = 0
        elapsed = 0f
        nextGap = gapFor(BASE_SPEED)
    }

    /**
     * Avanza el mundo [dtSeconds]. Devuelve `true` si en este cuadro el corredor chocó (la racha vuelve a cero y el récord se
     * conserva).
     */
    fun step(dtSeconds: Float): Boolean {
        val dt = dtSeconds.coerceIn(0f, MAX_STEP)
        if (dt <= 0f || ended) return false
        elapsed += dt
        speed = min(MAX_SPEED, speed + SPEED_GAIN * dt)
        if (flash > 0f) flash = max(0f, flash - dt)

        if (height > 0f || velocity > 0f) {
            velocity -= GRAVITY * dt
            height += velocity * dt
            if (height <= 0f) {
                height = 0f
                velocity = 0f
            }
        }

        invulnerable = max(0f, invulnerable - dt)
        powerSeconds = max(0f, powerSeconds - dt)
        if (powerSeconds == 0f) power = null
        val dx = speed * dt * if (power == RestSupplement.SPEED) 1.3f else 1f
        supplements.forEach { item ->
            item.x -= dx
            if (kotlin.math.abs(item.x - (RUNNER_X + 12f)) < 18f && height in 14f..78f) {
                if (item.kind == RestSupplement.LIFE) lives = min(5, lives + 1)
                else { power = item.kind; powerSeconds = 7f }
                item.x = -100f
            }
        }
        supplements.removeAll { it.x < -30f }
        var hit = false
        for (o in obstacles) {
            o.x -= dx
            val left = o.x + OBSTACLE_HIT_INSET
            val right = o.x + o.width - OBSTACLE_HIT_INSET
            val overlapsRunner = right > RUNNER_X + RUNNER_HIT_LEFT && left < RUNNER_X + RUNNER_HIT_RIGHT
            if (invulnerable == 0f && overlapsRunner && height < o.hitHeight) hit = true
        }

        if (hit) {
            hits++
            lives--
            invulnerable = 1.3f
            best = max(best, score)
            score = 0
            speed = BASE_SPEED
            flash = HIT_FLASH
            obstacles.removeAll { it.x < RUNNER_X + RESTART_CLEAR }
        } else {
            for (o in obstacles) {
                if (!o.counted && o.x + o.width < RUNNER_X) {
                    o.counted = true
                    score++
                    if (score > best) {
                        best = score
                        bestChanged = true
                    }
                }
            }
        }
        obstacles.removeAll { it.x < -OBSTACLE_W - 10f }

        val spawnX = worldWidth + 10f
        val last = obstacles.maxOfOrNull { it.x }
        if (last == null || spawnX - last >= nextGap) {
            val kind = if (spawned == 0) RestHazard.DUMBBELL else RestHazard.entries[random.nextInt(RestHazard.entries.size)]
            obstacles += RestGameObstacle(x = spawnX, kind = kind)
            spawned++
            if (spawned % 3 == 0) supplements += RestGameSupplement(spawnX + 105f, RestSupplement.entries[random.nextInt(RestSupplement.entries.size)])
            nextGap = gapFor(speed)
        }
        return hit
    }
}
