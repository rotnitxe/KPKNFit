package com.example.kpkn.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.plan.CoverLayout
import com.example.kpkn.screens.onboarding.design.entreno.plan.DetailSheet
import com.example.kpkn.screens.onboarding.design.entreno.plan.GoalProfileList
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanBlockModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCardModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCarousel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCover
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDayModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDetailModel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDetailOverlay
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanPreparingOverlay
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanPreparingVariant
import com.example.kpkn.screens.onboarding.design.entreno.plan.PreparingContent
import com.example.kpkn.screens.onboarding.design.entreno.plan.rememberPreparingClock
import com.example.kpkn.screens.onboarding.design.entreno.plan.goalArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.goalCoverArt
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * SOLO DEPURACIÓN (no se integra): vista previa de los componentes del objetivo y del programa sobre la página negra.
 *
 * Extras (`adb shell am start … --es scenario goals`), siempre a través de `emu_run.py`:
 *  - `scenario`:
 *      `goals` (lista de objetivos; `--es blocked all|POWERLIFTING,…` para los bloqueados y `--es profile X` para el elegido),
 *      `cover` (la portada de `--es profile X` en tarjeta 3:4 y en banda; `--ei seed N`, `--ez animate true`),
 *      `cover_all` (las 10 portadas en dos columnas), `sheet` (los símbolos en seis instantes de su bucle; `--ez cover true`
 *      con la línea de portada), `big` (un símbolo ampliado; `--ef t 1.2` lo para en ese instante),
 *      `preparing_general` y `preparing_discipline` (el overlay «preparando»; `--ei readyAfterMs 0` lo deja listo desde el
 *      principio, sin extra nunca está listo y se queda esperando; `--ef freeze 1.6` lo para en ese instante y
 *      `--ef freezeFinish 3.0` lo para además en pleno destello final),
 *      `carousel` (`--ei count 1..5`, `--es selected texas`), `detail` (la hoja de detalle; `--ez chosen true` si ya es el elegido).
 *      `--ez inline true` en `preparing_*` y `detail` pinta el contenido dentro de la página en vez de en su ventana: así
 *      respeta `widthDp` y `fontScale`, que un `Dialog` ignora.
 *  - `profile` (nombre de `TrainingGoalProfile`): la disciplina de la vista previa.
 *  - `widthDp` (decimal): ancho del contenido (p. ej. 360). `fontScale` (decimal): escala de fuente (p. ej. 1.3).
 *  - `reducedMotion` (booleano): fuerza «reducir movimiento» (cuadro estático, sin bucles ni resorte).
 *  Todo es interactivo de verdad: tocar un perfil lo elige, «Elegir» elige la tarjeta, «Ver detalles» abre la hoja.
 */
class EntrenoPlanPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = PreviewArgs(
            scenario = intent.getStringExtra("scenario") ?: "goals",
            profile = intent.getStringExtra("profile")?.let { name -> TrainingGoalProfile.entries.firstOrNull { it.name == name } },
            blocked = intent.getStringExtra("blocked").orEmpty(),
            widthDp = intent.getFloatExtra("widthDp", 0f),
            fontScale = intent.getFloatExtra("fontScale", 0f),
            reduced = intent.getBooleanExtra("reducedMotion", false),
            cover = intent.getBooleanExtra("cover", false),
            animate = intent.getBooleanExtra("animate", false),
            chosen = intent.getBooleanExtra("chosen", false),
            t = intent.getFloatExtra("t", -1f),
            seed = intent.getIntExtra("seed", 0),
            count = intent.getIntExtra("count", 4),
            selected = intent.getStringExtra("selected"),
            readyAfterMs = intent.getIntExtra("readyAfterMs", -1),
            freeze = intent.getFloatExtra("freeze", -1f),
            freezeFinish = intent.getFloatExtra("freezeFinish", -1f),
            inlined = intent.getBooleanExtra("inline", false),
        )
        setContent { PlanPreview(args) }
    }
}

private class PreviewArgs(
    val scenario: String,
    val profile: TrainingGoalProfile?,
    val blocked: String,
    val widthDp: Float,
    val fontScale: Float,
    val reduced: Boolean,
    val cover: Boolean,
    val animate: Boolean,
    val chosen: Boolean,
    val t: Float,
    val seed: Int,
    val count: Int,
    val selected: String?,
    val readyAfterMs: Int,
    val freeze: Float,
    val freezeFinish: Float,
    val inlined: Boolean,
)

// ---------------------------------------------------------------- datos de ejemplo

/** Razones de bloqueo de ejemplo (las de `docs/entreno-v2/COPY.md`). */
private val SAMPLE_REASONS = mapOf(
    TrainingGoalProfile.POWERLIFTING to "Necesita barra, rack y banco.",
    TrainingGoalProfile.POWERBUILDING to "Necesita barra, rack y banco, o mancuernas.",
    TrainingGoalProfile.BODYBUILDING to "Necesita mancuernas, barra, poleas o máquinas.",
    TrainingGoalProfile.CALISTHENICS to "Necesita barra de dominadas o anillas.",
    TrainingGoalProfile.WEIGHTLIFTING to "Necesita barra y rack.",
    TrainingGoalProfile.STRONGMAN to "Necesita barra y mancuernas o kettlebell.",
    TrainingGoalProfile.ARMWRESTLING to "Necesita mancuernas, poleas, bandas, barra o kettlebell.",
)

private fun blockedReasonsOf(spec: String): Map<TrainingGoalProfile, String> = when {
    spec.isBlank() -> emptyMap()
    spec == "all" -> SAMPLE_REASONS
    else -> {
        val names = spec.split(',').map { it.trim() }
        SAMPLE_REASONS.filterKeys { it.name in names }
    }
}

/** Los programas de ejemplo de una disciplina: uno «a medida», tres de autor y, al final, una «versión inicial». */
private fun sampleCards(profile: TrainingGoalProfile): List<PlanCardModel> = listOf(
    PlanCardModel(
        id = "custom", title = "Programa a tu medida", kicker = "Hecho a tu medida",
        blurb = "Armado con tu material, tus días y tu tiempo. Se ajusta si cambias algo.",
        profile = profile, daysLabel = "4 días", minutesLabel = "60 min", levelLabel = "Intermedio",
        badge = "Se adapta a ti", coverSeed = 3,
    ),
    PlanCardModel(
        id = "texas", title = "Texas Method", kicker = "Mark Rippetoe",
        blurb = "Un día de volumen, uno ligero y uno de intensidad: el clásico de fuerza cuando el progreso lineal se frena.",
        profile = profile, daysLabel = "3 días", minutesLabel = "75 min", levelLabel = "Intermedio", coverSeed = 11,
    ),
    PlanCardModel(
        id = "bbb", title = "5/3/1 Boring But Big", kicker = "Jim Wendler",
        blurb = "Cuatro semanas de ondas 5/3/1 y volumen extra con el 50 % de tu marca.",
        profile = profile, daysLabel = "4 días", minutesLabel = "70 min", levelLabel = "Intermedio", coverSeed = 27,
    ),
    PlanCardModel(
        id = "phul", title = "PHUL", kicker = "Brandon Campbell",
        blurb = "Fuerza y masa en la misma semana: dos días de potencia y dos de hipertrofia.",
        profile = profile, daysLabel = "4 días", minutesLabel = "60 min", levelLabel = "Principiante", badge = "Recomendado", coverSeed = 42,
    ),
    PlanCardModel(
        id = "initial", title = "Calistenia inicial", kicker = "Versión inicial",
        blurb = "Domina tu peso corporal con progresiones sencillas. Se irá ampliando.",
        profile = TrainingGoalProfile.CALISTHENICS, daysLabel = "3 días", minutesLabel = "45 min", levelLabel = "Todos", coverSeed = 5,
    ),
)

private fun sampleDetail(card: PlanCardModel): PlanDetailModel = PlanDetailModel(
    card = card,
    description = "Un programa que reparte la semana en sesiones de volumen, intensidad y recuperación. " +
        "Funciona muy bien cuando el progreso lineal se estanca y ya dominas la técnica de los tres básicos: " +
        "cada día tiene un objetivo claro y la carga sube en ondas para que llegues fresco a tus mejores series.",
    mainExercises = listOf("Sentadilla", "Press banca", "Peso muerto", "Press militar", "Dominadas", "Remo con barra", "Curl de bíceps"),
    blocks = listOf(
        PlanBlockModel("Volumen", "Semanas 1–4", "Más series y repeticiones con el 80 % de tu marca."),
        PlanBlockModel("Intensidad", "Semanas 5–8", "Menos volumen y cargas más cercanas a tu máximo."),
        PlanBlockModel("Descarga", "Semana 9", "Bajamos la carga para llegar fresco al siguiente ciclo."),
    ),
    week = listOf(
        PlanDayModel(1, "Volumen", 75, 6, true),
        PlanDayModel(3, "Ligero", 50, 5, false),
        PlanDayModel(5, "Fuerza", 70, 5, false),
    ),
    reasons = listOf(
        "Entrenas 3 días: es la frecuencia para la que fue pensado.",
        "Tus 60 min por sesión alcanzan para los básicos y lo justo de accesorios.",
        "Tienes barra, rack y banco: usa los tres levantamientos.",
    ),
    notes = listOf("Versión inicial: aún no incluye variantes de peso corporal para la tracción."),
    attribution = "Basado en el método de Mark Rippetoe.",
)

// ---------------------------------------------------------------- vista previa

@Composable
private fun PlanPreview(args: PreviewArgs) {
    val base = LocalDensity.current
    val density = if (args.fontScale > 0f) Density(base.density, args.fontScale) else base
    CompositionLocalProvider(LocalDensity provides density) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(
                modifier = (if (args.widthDp > 0f) Modifier.width(args.widthDp.dp) else Modifier.fillMaxWidth())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 40.dp, bottom = 32.dp),
            ) {
                Text(
                    text = "${args.scenario}${if (args.widthDp > 0f) " · ${args.widthDp.toInt()} dp" else ""}" +
                        if (args.fontScale > 0f) " · fuente ${args.fontScale}" else "",
                    color = Color(0xFF8A8A8A),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(20.dp))
                when (args.scenario) {
                    "sheet" -> ArtSheet(args.cover)
                    "big" -> BigArt(args)
                    "cover" -> CoverPreview(args)
                    "cover_all" -> CoverGrid(args)
                    "preparing_general" ->
                        if (args.inlined) InlinePreparing(args, PlanPreparingVariant.GENERAL) else PreparingPreview(args, PlanPreparingVariant.GENERAL)
                    "preparing_discipline" ->
                        if (args.inlined) InlinePreparing(args, PlanPreparingVariant.DISCIPLINE) else PreparingPreview(args, PlanPreparingVariant.DISCIPLINE)
                    "carousel" -> CarouselPreview(args)
                    "detail" -> if (args.inlined) InlineDetail(args) else DetailPreview(args)
                    else -> GoalsPreview(args)
                }
            }
        }
    }
}

@Composable
private fun GoalsPreview(args: PreviewArgs) {
    var selected by remember { mutableStateOf(args.profile) }
    GoalProfileList(
        selected = selected,
        blockedReasons = blockedReasonsOf(args.blocked),
        onSelect = { selected = it },
        onBlockedTap = { },
        reduced = args.reduced,
    )
}

// ---------------------------------------------------------------- portadas

@Composable
private fun CoverPreview(args: PreviewArgs) {
    val profile = args.profile ?: TrainingGoalProfile.POWERLIFTING
    val card = sampleCards(profile)[1].copy(coverSeed = args.seed, badge = "Recomendado")
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PlanCover(card, Modifier.width(260.dp).aspectRatio(3f / 4f), animate = args.animate && !args.reduced, reduced = args.reduced)
        PlanCover(
            model = card.copy(title = "Programa a tu medida", kicker = "Hecho a tu medida", badge = null),
            modifier = Modifier.fillMaxWidth().height(236.dp),
            animate = args.animate && !args.reduced,
            reduced = args.reduced,
            parallax = { 0f },
            cornerRadius = 0.dp,
            topInset = 0.dp,
            layout = CoverLayout.BANNER,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (s in listOf(3, 11, 27)) {
                PlanCover(card.copy(coverSeed = s, badge = null), Modifier.weight(1f).aspectRatio(3f / 4f), reduced = args.reduced)
            }
        }
    }
}

@Composable
private fun CoverGrid(args: PreviewArgs) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (pair in TrainingGoalProfile.entries.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (profile in pair) {
                    val card = PlanCardModel(
                        id = profile.name, title = profile.label,
                        kicker = if (profile.isSpecific) "Versión inicial" else "Hecho a tu medida",
                        blurb = profile.tagline, profile = profile, daysLabel = "4 días", minutesLabel = "60 min",
                        levelLabel = "Intermedio", coverSeed = profile.ordinal * 7 + 1,
                    )
                    PlanCover(card, Modifier.weight(1f).aspectRatio(3f / 4f), reduced = args.reduced)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- overlay «preparando»

@Composable
private fun PreparingPreview(args: PreviewArgs, variant: PlanPreparingVariant) {
    val profile = args.profile ?: TrainingGoalProfile.POWERLIFTING
    var ready by remember { mutableStateOf(args.readyAfterMs == 0) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(args.readyAfterMs) {
        if (args.readyAfterMs > 0) {
            delay(args.readyAfterMs.toLong())
            ready = true
        }
    }
    GoalProfileList(selected = profile, blockedReasons = emptyMap(), onSelect = { }, onBlockedTap = { }, reduced = true)
    if (done) {
        Text("onAnimationDone", color = Color(0xFF43D18C), fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
    } else {
        PlanPreparingOverlay(
            variant = variant,
            profile = if (variant == PlanPreparingVariant.DISCIPLINE) profile else null,
            ready = ready,
            onAnimationDone = { done = true },
            reduced = args.reduced,
            frozenAt = if (args.freeze >= 0f) args.freeze else null,
            frozenFinishAt = if (args.freezeFinish >= 0f) args.freezeFinish else Float.NaN,
        )
    }
}

/**
 * El contenido del overlay «preparando» dentro de la página (sin la ventana): así toma el ancho y la escala de fuente que se
 * piden con `widthDp` y `fontScale`, que un `Dialog` ignora. `--ez inline true`.
 */
@Composable
private fun InlinePreparing(args: PreviewArgs, variant: PlanPreparingVariant) {
    val profile = args.profile ?: TrainingGoalProfile.POWERLIFTING
    val clock = rememberPreparingClock(args.reduced, if (args.freeze >= 0f) args.freeze else null)
    val finish = remember { mutableFloatStateOf(if (args.freezeFinish >= 0f) args.freezeFinish else Float.NaN) }
    Box(Modifier.fillMaxWidth().height(720.dp).clipToBounds()) {
        PreparingContent(
            variant = variant,
            profile = if (variant == PlanPreparingVariant.DISCIPLINE) profile else null,
            clock = clock,
            finishAt = finish,
            shown = { 1f },
            reduced = args.reduced,
            blur = false,
        )
    }
}

/** La hoja de detalle dentro de la página (sin la ventana), a 360 dp y con la letra al 130 % si se piden. `--ez inline true`. */
@Composable
private fun InlineDetail(args: PreviewArgs) {
    val profile = args.profile ?: TrainingGoalProfile.POWERLIFTING
    var chosen by remember { mutableStateOf(args.chosen) }
    Box(Modifier.fillMaxWidth().height(780.dp).clipToBounds()) {
        DetailSheet(
            model = sampleDetail(sampleCards(profile)[1]),
            selected = chosen,
            reduced = args.reduced,
            blur = false,
            shown = { 1f },
            onChoose = { chosen = true },
            onClose = { },
        )
    }
}

// ---------------------------------------------------------------- carrusel y detalle

@Composable
private fun CarouselPreview(args: PreviewArgs) {
    val profile = args.profile ?: TrainingGoalProfile.POWERLIFTING
    val cards = remember(profile, args.count) { sampleCards(profile).take(args.count.coerceIn(1, 5)) }
    var selected by remember { mutableStateOf(args.selected) }
    var open by remember { mutableStateOf<String?>(null) }
    PlanCarousel(cards, selected, onSelect = { selected = it }, onOpen = { open = it }, reduced = args.reduced)
    val card = cards.firstOrNull { it.id == open }
    if (card != null) {
        PlanDetailOverlay(
            model = sampleDetail(card),
            selected = card.id == selected,
            onChoose = {
                selected = card.id
                open = null
            },
            onClose = { open = null },
            reduced = args.reduced,
        )
    }
}

@Composable
private fun DetailPreview(args: PreviewArgs) {
    val profile = args.profile ?: TrainingGoalProfile.POWERLIFTING
    val card = sampleCards(profile)[1]
    var open by remember { mutableStateOf(true) }
    var chosen by remember { mutableStateOf(args.chosen) }
    GoalProfileList(selected = profile, blockedReasons = emptyMap(), onSelect = { }, onBlockedTap = { }, reduced = true)
    if (open) {
        PlanDetailOverlay(
            model = sampleDetail(card),
            selected = chosen,
            onChoose = { chosen = true },
            onClose = { open = false },
            reduced = args.reduced,
        )
    } else {
        Text("cerrado", color = Color(0xFF43D18C), fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
    }
}

// ---------------------------------------------------------------- hoja de contactos de los símbolos

private const val SHEET_FRAMES = 6

/** Cada símbolo en seis instantes de su bucle, seleccionado: para criticar el dibujo y su movimiento de un vistazo. */
@Composable
private fun ArtSheet(cover: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (profile in TrainingGoalProfile.entries) {
            Text(profile.name, color = Color(0xFF8A8A8A), fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val art = if (cover) goalCoverArt(profile) else goalArt(profile)
                for (i in 0 until SHEET_FRAMES) {
                    val t = if (i == SHEET_FRAMES - 1) art.restT else art.period * i / (SHEET_FRAMES - 1)
                    val pen = remember { SymbolPen() }
                    Canvas(Modifier.size(58.dp)) {
                        val k = min(size.width / art.width, size.height / art.height)
                        pen.begin(this, 1f, art.accent)
                        withTransform({ scale(k, k, Offset.Zero) }) {
                            art.drawStatic(pen)
                            art.drawDynamic(pen, t)
                        }
                    }
                }
            }
        }
    }
}

/** Un símbolo ampliado a ~320 dp (de línea de lista o de portada), parado en `t` o en movimiento si no se da. */
@Composable
private fun BigArt(args: PreviewArgs) {
    val profile = args.profile ?: TrainingGoalProfile.POWERLIFTING
    val art = if (args.cover) goalCoverArt(profile) else goalArt(profile)
    val pen = remember { SymbolPen() }
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        if (args.t >= 0f) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> clock.floatValue = (now - start) / 1_000_000_000f }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(320.dp)) {
            val k = min(size.width / art.width, size.height / art.height)
            val t = if (args.t >= 0f) args.t else art.restT + clock.floatValue
            pen.begin(this, 1f, art.accent)
            withTransform({ scale(k, k, Offset.Zero) }) {
                art.drawStatic(pen)
                art.drawDynamic(pen, ((t % art.period) + art.period) % art.period)
            }
        }
    }
}
