package com.example.kpkn.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.entreno.layout.SplitOption
import com.example.kpkn.screens.onboarding.design.entreno.layout.WeekLayoutBoardContent
import com.example.kpkn.screens.onboarding.design.entreno.layout.WeekLayoutDragState
import com.example.kpkn.screens.onboarding.design.entreno.layout.WeekLayoutSession
import com.example.kpkn.screens.onboarding.design.entreno.layout.slotOrder
import com.example.kpkn.screens.onboarding.design.entreno.layout.splitPatternDays
import com.example.kpkn.screens.onboarding.design.entreno.layout.swapAssignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SOLO DEPURACIÓN (no se integra): vista previa del tablero de la semana («Así queda tu semana») sobre la página negra.
 *
 * Extras (`adb shell am start … --es scenario board`), siempre a través de `emu_run.py`:
 *  - `scenario`: `board` (por defecto), `board_moving` (una ficha levantada a mitad de arrastre, dirigida por el mismo
 *    estado que usa el gesto), `board_selected` (la primera sesión elegida con un toque), `splits` (un reparto tocado, con
 *    el botón de adaptar, el aviso y «Restablecer») o `adapting` (el estado de carga).
 *  - `days` (entero, 1..7, por defecto 4): cuántos días de entreno. `start` (entero, 1..7, por defecto 1): primer día de la semana.
 *  - `offset` (entero, por defecto 0): desplaza las sesiones esa cantidad de ranuras (para ver cómo arranca la tira cuando
 *    la primera sesión queda lejos del inicio de la semana).
 *  - `to` (con `board_moving`): `next` (por defecto: el día de la segunda sesión, para ver el intercambio) o un día 1..7.
 *  - `widthDp` (decimal): ancho de la pantalla simulada (p. ej. 360). `fontScale` (decimal): escala de fuente (p. ej. 1.3).
 *  - `reducedMotion` (booleano): fuerza «reducir movimiento» (sin resortes ni bucles).
 *  Con un solo día no hay repartos (como en el catálogo real). Tocar, arrastrar y adaptar funcionan de verdad: la pantalla
 *  lleva su propia colocación, y «adaptar» tarda un instante en responder para ver el estado de carga.
 */
class EntrenoLayoutPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "board"
        val days = intent.getIntExtra("days", 4).coerceIn(1, 7)
        val start = intent.getIntExtra("start", 1)
        val moveTo = intent.getStringExtra("to") ?: "next"
        val offset = intent.getIntExtra("offset", 0)
        val widthDp = intent.getFloatExtra("widthDp", 0f)
        val fontScale = intent.getFloatExtra("fontScale", 0f)
        val reduced = intent.getBooleanExtra("reducedMotion", false)
        setContent { Preview(scenario, days, start, moveTo, offset, widthDp, fontScale, reduced) }
    }
}

private val SESSIONS_BY_COUNT: Map<Int, List<Triple<String, String, Pair<Int, Int>>>> = mapOf(
    1 to listOf(Triple("Cuerpo completo", "Todo el cuerpo", 60 to 8)),
    2 to listOf(
        Triple("Torso", "Pecho, espalda y hombros", 65 to 7),
        Triple("Pierna", "Cuádriceps, glúteos y femoral", 60 to 6),
    ),
    3 to listOf(
        Triple("Cuerpo A", "Sentadilla y press", 60 to 6),
        Triple("Cuerpo B", "Peso muerto y remo", 60 to 6),
        Triple("Cuerpo C", "Variantes y accesorios", 55 to 6),
    ),
    4 to listOf(
        Triple("Torso A", "Pecho, espalda y hombros", 60 to 6),
        Triple("Pierna A", "Cuádriceps y glúteos", 55 to 5),
        Triple("Torso B", "Hombros y brazos", 50 to 6),
        Triple("Pierna B", "Femoral y gemelos", 55 to 5),
    ),
    5 to listOf(
        Triple("Empuje", "Pecho, hombros y tríceps", 55 to 6),
        Triple("Tirón", "Espalda y bíceps", 55 to 6),
        Triple("Pierna", "Cuádriceps, glúteos y femoral", 70 to 7),
        Triple("Torso", "Pecho y espalda", 50 to 5),
        Triple("Brazos y core", "Brazos y abdomen", 40 to 5),
    ),
    6 to listOf(
        Triple("Empuje A", "Pecho y hombros", 55 to 6),
        Triple("Tirón A", "Espalda y bíceps", 55 to 6),
        Triple("Pierna A", "Cuádriceps y glúteos", 60 to 6),
        Triple("Empuje B", "Hombros y tríceps", 50 to 5),
        Triple("Tirón B", "Espalda y antebrazo", 50 to 5),
        Triple("Pierna B", "Femoral y gemelos", 55 to 5),
    ),
    7 to listOf(
        Triple("Pecho", "Pecho y tríceps", 45 to 5),
        Triple("Espalda", "Espalda y bíceps", 45 to 5),
        Triple("Pierna", "Cuádriceps y glúteos", 60 to 6),
        Triple("Hombros", "Hombros y trapecio", 40 to 5),
        Triple("Brazos", "Bíceps y tríceps", 35 to 5),
        Triple("Glúteos", "Glúteos y femoral", 50 to 5),
        Triple("Core y movilidad", "Abdomen y movilidad", 30 to 6),
    ),
)

private fun sampleSessions(days: Int): List<WeekLayoutSession> =
    SESSIONS_BY_COUNT.getValue(days).mapIndexed { index, (title, focus, amounts) ->
        WeekLayoutSession(
            id = "s${index + 1}",
            title = title,
            focus = focus,
            minutes = amounts.first,
            exerciseCount = amounts.second,
            isMain = index == 0,
        )
    }

private fun sampleSplits(days: Int): List<SplitOption> {
    if (days < 2) return emptyList()
    fun titles(cycle: List<String>) = List(days) { cycle[it % cycle.size] }
    return listOf(
        SplitOption("tp", "Torso y pierna", "Alterna torso y pierna", titles(listOf("Torso", "Pierna"))),
        SplitOption("ppl", "Empuje, tirón, pierna", "Tres sesiones que rotan", titles(listOf("Empuje", "Tirón", "Pierna"))),
        SplitOption("fb", "Cuerpo completo", "Todo el cuerpo cada día", titles(listOf("Cuerpo A", "Cuerpo B"))),
        SplitOption("bro", "Por grupos", "Un grupo grande por día", titles(listOf("Pecho", "Espalda", "Pierna", "Hombros", "Brazos"))),
    )
}

@Composable
private fun Preview(scenario: String, days: Int, start: Int, moveTo: String, offset: Int, widthDp: Float, fontScale: Float, reduced: Boolean) {
    val base = LocalDensity.current
    val density = if (fontScale > 0f) Density(base.density, fontScale) else base
    CompositionLocalProvider(LocalDensity provides density) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(
                modifier = (if (widthDp > 0f) Modifier.width(widthDp.dp) else Modifier.fillMaxWidth())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 40.dp, bottom = 32.dp),
            ) {
                Text(
                    text = "$scenario · $days días · inicio ${start}${if (widthDp > 0f) " · ${widthDp.toInt()} dp" else ""}" +
                        if (fontScale > 0f) " · fuente $fontScale" else "",
                    color = Color(0xFF8A8A8A),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(20.dp))
                Board(scenario, days, start, moveTo, offset, reduced)
            }
        }
    }
}

@Composable
private fun Board(scenario: String, days: Int, start: Int, moveTo: String, offset: Int, reduced: Boolean) {
    val sessions = remember(days) { sampleSessions(days) }
    val splits = remember(days) { sampleSplits(days) }
    val initial = remember(days, start, offset) {
        val order = slotOrder(start)
        val slots = splitPatternDays(days, start, emptyList()).map { order[(order.indexOf(it) + offset).mod(order.size)] }
        sessions.mapIndexed { index, s -> slots[index] to s.id }.toMap()
    }
    var assignment by remember { mutableStateOf(initial) }
    var currentSplit by remember { mutableStateOf<String?>(splits.firstOrNull()?.id) }
    var canReset by remember { mutableStateOf(scenario == "splits") }
    var adapting by remember { mutableStateOf(scenario == "adapting") }
    val scope = rememberCoroutineScope()
    val state = remember { WeekLayoutDragState() }
    val density = LocalDensity.current

    // Escenarios dirigidos con el mismo estado que usa el gesto (el arrastre sintético de adb no sirve: una pulsación larga y
    // un movimiento no caben en un solo `input swipe`).
    LaunchedEffect(scenario) {
        when (scenario) {
            "board_selected" -> {
                delay(700)
                state.selectedId = sessions.first().id
            }
            "board_moving" -> {
                delay(900)
                val g = state.geometry
                val firstId = sessions.first().id
                val fromDay = assignment.entries.first { it.value == firstId }.key
                val order = slotOrder(start)
                val targetDay = moveTo.toIntOrNull()
                    ?: assignment.entries.firstOrNull { it.value == sessions.getOrNull(1)?.id }?.key
                    ?: order[(order.indexOf(fromDay) + 1) % order.size]
                val glyphCenter = with(density) { 36.dp.toPx() }
                val from = g.home(fromDay) + Offset(g.colWidth / 2f, glyphCenter)
                val dest = g.home(targetDay) + Offset(g.colWidth / 2f, glyphCenter - with(density) { 10.dp.toPx() })
                state.lift(firstId, from)
                for (i in 1..14) {
                    val t = i / 14f
                    state.dragTo(Offset(from.x + (dest.x - from.x) * t, from.y + (dest.y - from.y) * t))
                    delay(16)
                }
            }
        }
    }

    WeekLayoutBoardContent(
        weekStartDay = start,
        sessions = sessions,
        assignment = assignment,
        onMove = { id, day -> assignment = swapAssignment(assignment, id, day); canReset = true },
        splitOptions = splits,
        selectedSplitId = currentSplit,
        onAdaptSplit = { id ->
            adapting = true
            scope.launch {
                delay(1600)
                currentSplit = id
                canReset = true
                adapting = false
            }
        },
        canReset = canReset,
        onReset = {
            assignment = initial
            currentSplit = splits.firstOrNull()?.id
            canReset = false
        },
        adapting = adapting,
        splitNotice = if (scenario == "splits") "Este programa trae su reparto de autor. Si lo adaptas, cambia su estructura original." else null,
        reduced = reduced,
        state = state,
        initialPendingSplitId = if (scenario == "splits") splits.getOrNull(1)?.id else null,
    )
}
