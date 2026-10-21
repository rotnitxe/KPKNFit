package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.kpkn.data.workout.RestGameRecordStore
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.RestGameEngine
import com.example.kpkn.screens.workout.RestNextSetSummary
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

internal enum class RestArcadeMode(val label: String, val hint: String) {
    RUNNER("Salta las mancuernas", "Obstáculos, hoyos y suplementos"),
    FLAPPY("Entre las pesas", "Encuentra el hueco y mantén el vuelo"),
    SHOOTER("Escuadrón retro", "Esquiva las ráfagas y abre paso"),
}

/** Rest is supplied by the controller; selecting/restarting a game cannot mutate that clock. */
@Composable
internal fun RestArcade(
    remainingSeconds: Int, totalSeconds: Int, nextSet: RestNextSetSummary?,
    onBack: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier,
    recordStore: RestGameRecordStore? = null, runner: RestGameEngine? = null,
    running: Boolean = true, compact: Boolean = false,
) {
    var mode by remember { mutableStateOf<RestArcadeMode?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val reduced = wizardReducedMotion()
    var started by remember(mode, round) { mutableStateOf(!reduced) }
    val game = runner ?: remember(round) { RestGameEngine() }
    Column(
        modifier.fillMaxSize().then(if (compact) Modifier else Modifier.statusBarsPadding().navigationBarsPadding())
            .padding(horizontal = if (compact) 14.dp else 20.dp)
            .padding(top = if (compact) 10.dp else 20.dp, bottom = if (compact) 10.dp else 22.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
    ) {
        RestArcadeHeader(mode, remainingSeconds, totalSeconds, compact) { if (mode == null) onBack() else mode = null }
        // Same controller values as the rest surface; this rail has no clock/deadline owner.
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF1B1B1B))) {
            val progress = ((totalSeconds - remainingSeconds.coerceAtLeast(0)).toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Brush.horizontalGradient(listOf(ArcadeBlue, ArcadePeach))))
        }
        if (mode == null) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val gap = if (compact) 9.dp else 12.dp
                // CSS flex:1/min-height/overflow:auto from the approved picker, including card-sized hosts.
                val tileHeight = ((maxHeight - gap * 2) / 3).coerceAtLeast(if (compact) 110.dp else 128.dp)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
                    RestArcadeMode.entries.forEach { choice ->
                        val shape = RoundedCornerShape(24.dp)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = tileHeight).clip(shape).background(Color(0xFF111111))
                                .border(1.dp, ArcadeInk.copy(alpha = .09f), shape)
                                .clickable(role = Role.Button) { mode = choice }
                                .padding(if (compact) 14.dp else 18.dp).testTag("rest-game-${choice.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(choice.label, style = KpknType.title.copy(fontSize = if (compact) 18.sp else 20.sp,
                                    lineHeight = if (compact) 22.sp else 24.sp), color = ArcadeInk)
                                Text(choice.hint, style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 18.sp), color = ArcadeInk.copy(alpha = .55f))
                            }
                            RestArcadePickerArt(choice)
                        }
                    }
                }
            }
            RestArcadeReferenceButton("Volver al descanso", onBack, Modifier.fillMaxWidth().testTag("arcade-return-rest"))
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(mode, round) {
                    if (mode == RestArcadeMode.RUNNER) RestGameArena(game, recordStore, Modifier.fillMaxSize(), running && started)
                    else RestFlightArena(mode!!, Modifier.fillMaxSize(), running && started)
                }
                if (!started) KpknCompactPill("Empezar", onClick = { started = true }, filled = true, modifier = Modifier.align(Alignment.Center))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RestArcadeReferenceButton("Reiniciar juego", { game.reset(); round++ }, Modifier.weight(1f).testTag("rest-game-restart"))
                RestArcadeReferenceButton("Otro juego", { mode = null }, Modifier.weight(1f))
            }
            if (!compact && nextSet != null) Text("Después · ${nextSet.headline()}", style = KpknType.caption, color = ArcadeInk.copy(alpha = .55f))
            // The picker has one return action, as in the reference. In a running game retain the
            // existing skip callback alongside a short return label so neither action is truncated.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RestArcadeReferenceButton("Saltar descanso", onSkip, Modifier.weight(1f))
                RestArcadeReferenceButton("Volver", onBack, Modifier.weight(1f).testTag("arcade-return-rest"))
            }
        }
    }
}

@Composable
private fun RestArcadeHeader(mode: RestArcadeMode?, remainingSeconds: Int, totalSeconds: Int, compact: Boolean, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF111111))
            .border(1.dp, ArcadeInk.copy(alpha = .1f), CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Volver", onClick = onBack)
            .semantics { contentDescription = "Volver" }, contentAlignment = Alignment.Center) {
            Icon(KpknLineIcons.ChevronLeft, null, Modifier.size(18.dp), tint = ArcadeInk)
        }
        Column(Modifier.weight(1f)) {
            Text(mode?.label ?: "Jugar mientras descansas", style = KpknType.bodyStrong.copy(fontSize = 15.sp),
                color = ArcadeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (mode != null && !compact) Text("El descanso sigue corriendo", style = KpknType.caption.copy(fontSize = 12.sp), color = ArcadeInk.copy(alpha = .58f))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (remainingSeconds < 0) "+" else "") + KpknSetFormat.rest(abs(remainingSeconds)),
                style = KpknType.metricM.copy(fontSize = 25.sp, lineHeight = 29.sp, letterSpacing = (-1).sp),
                color = ArcadeInk, modifier = Modifier.testTag("arcade-rest-clock"))
            Text((if (remainingSeconds < 0) "sobre " else "de ") + KpknSetFormat.rest(totalSeconds.coerceAtLeast(1)) +
                if (remainingSeconds < 0) "" else " · sugerido",
                style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 12.sp), color = ArcadeInk.copy(alpha = .5f))
        }
    }
}

/** Neutral 44 dp action from recovery.css; wrapping is allowed at larger system font sizes. */
@Composable
private fun RestArcadeReferenceButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.heightIn(min = 44.dp).clip(shape).background(Color(0xFF111111))
        .border(1.dp, ArcadeInk.copy(alpha = .12f), shape).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, style = KpknType.action.copy(fontSize = 13.sp, lineHeight = 19.sp), color = ArcadeInk,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

private val ArcadeInk = Color(0xFFF2EEE6)
private val ArcadeBlue = Color(0xFF8FB2FF)
private val ArcadePeach = Color(0xFFF49A6E)

/** Pure frame-stepped game: normalized coordinates, no timer/service/persistence ownership. */
internal class RestFlightEngine(val mode: RestArcadeMode, private val random: Random = Random.Default) {
    internal data class Gate(var x: Float, val y: Float, var scored: Boolean = false)
    internal data class Ship(var x: Float, var y: Float, var hp: Int = 2)
    internal data class Shot(var x: Float, var y: Float, val hostile: Boolean)
    var playerX = 74f; private set
    var playerY = 210f; private set
    var velocity = 0f; private set
    var lives = 3; private set
    var score = 0; private set
    var elapsed = 0f; private set
    private var immune = 0f
    private var spawn = 0f
    private var fire = 0f
    private var hostileFire = 0f
    val gates = mutableListOf<Gate>()
    val ships = mutableListOf<Ship>()
    val shots = mutableListOf<Shot>()
    val ended get() = lives <= 0
    fun flap() { if (!ended) velocity = -190f }
    fun steer(x: Float, y: Float) { playerX = x.coerceIn(18f, 342f); playerY = y.coerceIn(225f, 395f) }
    private fun hit() {
        if (immune > 0f || ended) return
        lives--; immune = 1.3f
        if (mode == RestArcadeMode.FLAPPY) { playerY = 210f; velocity = -90f }
    }
    fun step(seconds: Float) {
        if (ended) return
        val dt = seconds.coerceIn(0f, .04f)
        if (dt == 0f) return
        elapsed += dt; immune = (immune - dt).coerceAtLeast(0f); spawn -= dt
        if (mode == RestArcadeMode.FLAPPY) {
            velocity += 460f * dt; playerY += velocity * dt
            if (playerY < 14f || playerY > 404f) hit()
            val speed = (100f + elapsed * 1.5f).coerceAtMost(180f)
            val halfGap = (68f - elapsed * .16f).coerceAtLeast(48f)
            if (spawn <= 0f) { gates += Gate(390f, 115f + random.nextFloat() * 190f); spawn = 1.85f }
            gates.forEach {
                it.x -= speed * dt
                if (it.x < playerX + 11f && it.x + 36f > playerX - 11f && abs(playerY - it.y) + 10f > halfGap) hit()
                if (!it.scored && it.x + 36f < playerX) { it.scored = true; score++ }
            }
            gates.removeAll { it.x < -40f }
        } else {
            if (elapsed <= dt) { playerX = 180f; playerY = 362f }
            fire -= dt; hostileFire -= dt
            if (fire <= 0f) { shots += Shot(playerX, playerY - 18f, false); fire = .23f }
            if (spawn <= 0f) { ships += Ship(25f + random.nextFloat() * 310f, -15f); spawn = (1.05f - elapsed * .008f).coerceAtLeast(.35f) }
            if (hostileFire <= 0f) { ships.forEach { shots += Shot(it.x, it.y + 12f, true) }; hostileFire = 1.5f }
            ships.forEach {
                it.y += (46f + elapsed * .6f).coerceAtMost(105f) * dt
                it.x = (it.x + sin(elapsed * 1.6f + it.y * .02f) * 18f * dt).coerceIn(16f, 344f)
                if (abs(it.x - playerX) < 21f && abs(it.y - playerY) < 23f) { hit(); it.hp = 0 }
                if (it.y > 430f) { hit(); it.hp = 0 }
            }
            shots.forEach { shot ->
                shot.y += (if (shot.hostile) 155f else -280f) * dt
                if (shot.hostile) {
                    if (abs(shot.x - playerX) < 13f && abs(shot.y - playerY) < 18f) { hit(); shot.y = 500f }
                } else {
                    val target = ships.firstOrNull { it.hp > 0 && abs(it.x - shot.x) < 17f && abs(it.y - shot.y) < 18f }
                    if (target != null) { target.hp--; shot.y = -100f; if (target.hp == 0) score += 10 }
                }
            }
            ships.removeAll { it.hp <= 0 }
            shots.removeAll { it.y < -20f || it.y > 440f }
        }
    }
}

@Composable
private fun RestFlightArena(mode: RestArcadeMode, modifier: Modifier, running: Boolean) {
    val engine = remember(mode) { RestFlightEngine(mode) }
    var frame by remember { mutableLongStateOf(0L) }
    var hudScore by remember { mutableIntStateOf(0) }
    var hudLives by remember { mutableIntStateOf(3) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(engine, owner, running) {
        if (!running) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (!engine.ended) withInfiniteAnimationFrameNanos { now ->
                engine.step(if (last == 0L) 0f else (now - last) / 1_000_000_000f)
                last = now; frame = now
                if (hudScore != engine.score) hudScore = engine.score
                if (hudLives != engine.lives) hudLives = engine.lives
            }
        }
    }
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Color(0xFF0E0E0E)).border(1.dp, ArcadeInk.copy(alpha = .1f), RoundedCornerShape(26.dp))) {
        Canvas(Modifier.fillMaxSize().testTag("flight-arena").semantics {
            contentDescription = if (mode == RestArcadeMode.FLAPPY) "Tocar para volar" else "Arrastrar avión para esquivar y disparar"
            if (mode == RestArcadeMode.FLAPPY) { role = Role.Button; onClick { engine.flap(); true } }
        }.pointerInput(mode) {
            if (mode == RestArcadeMode.FLAPPY) detectTapGestures { engine.flap() }
            else detectDragGestures(onDragStart = { engine.steer(it.x / size.width * 360f, it.y / size.height * 420f) }) { change, _ ->
                change.consume(); engine.steer(change.position.x / size.width * 360f, change.position.y / size.height * 420f)
            }
        }) {
            @Suppress("UNUSED_VARIABLE") val tick = frame
            val sx = size.width / 360f; val sy = size.height / 420f
            for (n in 0..22) {
                val x = ((n * 73) % 360).toFloat(); val y = (n * 37f + engine.elapsed * 12f) % 420f
                drawCircle(ArcadeInk.copy(alpha = .16f), 1f, Offset(x * sx, y * sy))
            }
            if (mode == RestArcadeMode.FLAPPY) {
                val gap = (68f - engine.elapsed * .16f).coerceAtLeast(48f)
                engine.gates.forEach {
                    drawRect(ArcadeBlue.copy(alpha = .65f), Offset(it.x * sx, 0f), Size(36f * sx, (it.y - gap) * sy))
                    drawRect(ArcadeBlue.copy(alpha = .65f), Offset(it.x * sx, (it.y + gap) * sy), Size(36f * sx, (420f - it.y - gap) * sy))
                }
                drawOval(ArcadePeach, Offset((engine.playerX - 13f) * sx, (engine.playerY - 9f) * sy), Size(26f * sx, 18f * sy))
                drawCircle(ArcadeInk, 2f * sx, Offset((engine.playerX + 6f) * sx, (engine.playerY - 3f) * sy))
                drawLine(ArcadeInk, Offset((engine.playerX - 8f) * sx, engine.playerY * sy), Offset((engine.playerX - 16f) * sx, (engine.playerY - 7f) * sy), 3f * sx)
            } else {
                fun plane(x: Float, y: Float, enemy: Boolean) {
                    val direction = if (enemy) -1f else 1f
                    val path = Path().apply {
                        moveTo(x * sx, (y - 17f * direction) * sy)
                        lineTo((x + 15f) * sx, (y + 10f * direction) * sy)
                        lineTo(x * sx, (y + 5f * direction) * sy)
                        lineTo((x - 15f) * sx, (y + 10f * direction) * sy); close()
                    }
                    drawPath(path, if (enemy) ArcadePeach else ArcadeBlue)
                    drawPath(path, ArcadeInk.copy(alpha = .6f), style = Stroke(1f * sx))
                }
                engine.ships.forEach { plane(it.x, it.y, true) }
                engine.shots.forEach { drawLine(if (it.hostile) ArcadePeach else ArcadeInk, Offset(it.x * sx, it.y * sy), Offset(it.x * sx, (it.y + 7f) * sy), 2f * sx) }
                plane(engine.playerX, engine.playerY, false)
            }
        }
        Text(if (mode == RestArcadeMode.FLAPPY) "Toca para volar" else "Arrastra tu avión · disparo automático",
            style = KpknType.caption, color = ArcadeInk.copy(alpha = .55f), modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
        // Read only primitive HUD values outside the draw phase.
        Text("$hudScore   ·   ${"♥".repeat(hudLives.coerceAtLeast(0))}", style = KpknType.bodyStrong, color = ArcadeInk, modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
        if (hudLives <= 0) Text("Fin de partida", style = KpknType.title, color = ArcadeInk, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .8f)).padding(18.dp))
    }
}
