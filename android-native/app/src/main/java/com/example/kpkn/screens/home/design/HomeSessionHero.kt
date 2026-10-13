package com.example.kpkn.screens.home.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.domain.training.HomeNextSession
import com.example.kpkn.domain.training.HomeWeekDay
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverIdentity
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverPaletteKind
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioStyle
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.sessioneditor.components.SessionBackgroundLayer
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * La sesión del día como héroe a sangre (ola Home v1, I2). Sin estado: recibe el estado ya resuelto y las acciones. Al final, las
 * funciones puras (la dosis, el kicker y el «Siguiente»): no usan Android, así que se prueban sin Robolectric.
 */

/** Qué le toca a la sesión del día elegido: decide el kicker, el título, los botones y el fondo. */
internal enum class HomeHeroMode { TODAY, ONGOING, DONE, OTHER_DAY, REST, NO_PROGRAM, REGISTER }

/** Un ejercicio del héroe: nombre y dosis ya formateada («4 × 5–7 · 100 kg»). */
internal data class HomeHeroLine(val name: String, val dose: String)

/** El fondo del héroe. */
internal sealed interface HomeHeroArt {
    /** El fondo propio de la sesión, con su desenfoque (`SessionBackgroundLayer`). */
    data class Own(val background: SessionBackground) : HomeHeroArt

    /** El arte del motor de portadas (K3a), a la opacidad [opacity]. */
    data class Cover(val identity: CoverIdentity, val opacity: Float = 1f) : HomeHeroArt

    /** Sin arte: queda el negro de la marca. */
    data object None : HomeHeroArt
}

/** Todo lo que pinta el héroe. [key] identifica el contenido: al cambiar de día hay un fundido entre uno y otro. */
internal data class HomeHeroState(
    val key: String,
    val mode: HomeHeroMode,
    val kicker: String,
    val title: String,
    val lines: List<HomeHeroLine> = emptyList(),
    val moreCount: Int = 0,
    val meta: String? = null,
    val body: String? = null,
    val advisoryBadge: String? = null,
    val countdown: String? = null,
    val art: HomeHeroArt = HomeHeroArt.None,
)

/** Las acciones del héroe. Quien llama las cablea con la sesión de su página. */
internal data class HomeHeroActions(
    val onStart: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onOpenProgram: () -> Unit = {},
    val onCreateProgram: () -> Unit = {},
    val onRegister: () -> Unit = {},
)

/** Tamaños del título, de mayor a menor. 30 sp es el mínimo: si una palabra no cabe ni así, el título va en una línea con elipsis. */
private val HeroTitleSizesSp = listOf(44, 42, 40, 38, 36, 34, 32, 30)

/** Días de la semana en español, de lunes a domingo, en minúscula (para «Siguiente: jueves»). */
private val weekdaysEs = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/** El arte vive en la mitad de arriba: ocupa el 55 % superior del héroe, con su foco ahí. */
private const val HERO_ART_SHARE = 0.55f

/** El texto va en la mitad de abajo, sobre el velo: nunca ocupa más del 45 % de la altura del héroe. */
private const val HERO_TEXT_SHARE = 0.45f

/**
 * La sesión del día a sangre, en dos zonas: arriba el arte (el 55 % superior); abajo el texto, sobre un velo fuerte que lo deja sobre
 * negro. Si el texto no cabe en su zona, el héroe crece (nunca baja de 440 dp). Etiqueta `home-hero`, con el ancla del recorrido
 * [TourAnchors.HOME_SESION]. [parallax] (−1 a 1) desplaza el arte: las páginas de sesiones del mismo día lo usan al deslizar.
 */
@Composable
internal fun HomeSessionHero(
    state: HomeHeroState,
    actions: HomeHeroActions,
    modifier: Modifier = Modifier,
    parallax: () -> Float = { 0f },
) {
    val reduced = wizardReducedMotion()
    val minHeight = with(LocalDensity.current) { 440.dp.roundToPx() }
    Layout(
        content = {
            // Tres hijos, en este orden: el arte, el velo y el texto. La medida de abajo los coloca.
            HeroArt(art = state.art, reduced = reduced, parallax = parallax)
            Box(Modifier.fillMaxSize().background(heroVeil()))
            HeroText(state = state, actions = actions)
        },
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .background(WizardColors.background)
            .testTag("home-hero")
            .tourAnchor(TourAnchors.HOME_SESION),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val text = measurables[2].measure(Constraints(maxWidth = width))
        // El texto ocupa como mucho el 45 % de abajo: el héroe crece si hace falta, y el arte queda en el 55 % de arriba.
        val height = maxOf(minHeight, ceil(text.height / HERO_TEXT_SHARE).toInt())
        val art = measurables[0].measure(Constraints.fixed(width, (height * HERO_ART_SHARE).roundToInt()))
        val veil = measurables[1].measure(Constraints.fixed(width, height))
        layout(width, height) {
            art.place(0, 0)
            veil.place(0, 0)
            text.place(0, height - text.height)
        }
    }
}

/** El arte del fondo, en la zona de arriba (la medida le da su altura). Sin arte queda una caja vacía sobre el negro. */
@Composable
private fun HeroArt(art: HomeHeroArt, reduced: Boolean, parallax: () -> Float) {
    when (art) {
        is HomeHeroArt.Own -> Box(Modifier.fillMaxSize()) {
            SessionBackgroundLayer(background = art.background, blurDp = (art.background.style?.blur ?: 0f).dp)
        }
        is HomeHeroArt.Cover -> CoverStudioArt(
            identity = art.identity,
            modifier = Modifier.fillMaxSize().alpha(art.opacity),
            animate = true,
            reduced = reduced,
            parallax = parallax,
        )
        HomeHeroArt.None -> Box(Modifier.fillMaxSize())
    }
}

/** El texto de la zona de abajo: avisos, kicker, título, ejercicios, meta y botones. Va sobre el velo, nunca sobre el arte. */
@Composable
private fun HeroText(state: HomeHeroState, actions: HomeHeroActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WizardSpacing.gutter, end = WizardSpacing.gutter, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Uno debajo de otro: dos avisos largos no caben en una fila de 312 dp (la cuenta atrás se quedaba en un punto).
        if (state.advisoryBadge != null || state.countdown != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.advisoryBadge?.let { HeroLabel(text = it, color = WizardColors.danger) }
                state.countdown?.let { HeroLabel(text = it, color = SymbolPalette.energia) }
            }
        }
        Text(
            text = state.kicker,
            style = HomeTypography.eyebrow,
            color = WizardColors.text.copy(alpha = 0.78f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        HeroTitle(text = state.title)
        if (state.lines.isNotEmpty()) HeroLines(lines = state.lines, moreCount = state.moreCount)
        state.body?.let { Text(text = it, style = WizardTypography.bodySmall, color = WizardColors.textMuted) }
        state.meta?.let { HeroMeta(text = it) }
        HeroButtons(mode = state.mode, actions = actions)
    }
}

/**
 * El velo de la zona de abajo: transparente hasta el 38 %; negro al 85 % en el 55 %, donde empieza el texto; negro pleno desde el
 * 70 %. Así ninguna letra del kicker ni del título queda sobre un color vivo.
 */
private fun heroVeil(): Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.38f to Color.Transparent,
    0.55f to WizardColors.background.copy(alpha = 0.85f),
    0.70f to WizardColors.background,
    1f to WizardColors.background,
)

/** Una etiqueta de aviso: píldora de 13 sp con el color al 16 % de fondo y el texto en el mismo color. */
@Composable
private fun HeroLabel(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), WizardShapes.pill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = WizardTypography.note.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Lo que resuelve el título en una columna: el tamaño elegido y si va en una sola línea (solo cuando ninguna forma de partirlo sirve). */
private class HeroTitleFit(val sizeSp: Int, val oneLine: Boolean)

/**
 * El título del héroe: usa todo el ancho y va en el mayor tamaño (de 44 a 30 sp) que quepa en dos líneas sin partir ninguna palabra.
 * Si una palabra no cabe ni a 30 sp, va en una sola línea con elipsis: nunca se parte dentro de una palabra.
 */
@Composable
private fun HeroTitle(text: String) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val fit = remember(text, widthPx, density.density, density.fontScale) {
            val sizeSp = HeroTitleSizesSp.firstOrNull { heroTitleFits(measurer, text, widthPx, it) }
            HeroTitleFit(sizeSp = sizeSp ?: HeroTitleSizesSp.last(), oneLine = sizeSp == null)
        }
        Text(
            text = text,
            style = HomeTypography.heroTitle.copy(fontSize = fit.sizeSp.sp, lineHeight = (fit.sizeSp * 1.05f).sp),
            color = WizardColors.text,
            maxLines = if (fit.oneLine) 1 else 2,
            softWrap = !fit.oneLine,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Cabe el título a [sp]: en dos líneas como mucho, y cada palabra entera dentro de [widthPx] (así nunca se parte una palabra). */
private fun heroTitleFits(measurer: TextMeasurer, text: String, widthPx: Int, sp: Int): Boolean {
    val style = HomeTypography.heroTitle.copy(fontSize = sp.sp, lineHeight = (sp * 1.05f).sp)
    val inTwoLines = !measurer.measure(
        text = text,
        style = style,
        constraints = Constraints(maxWidth = widthPx),
        maxLines = 2,
    ).hasVisualOverflow
    val longestWord = text.split(' ').filter { it.isNotBlank() }.maxOfOrNull { word ->
        measurer.measure(text = word, style = style, maxLines = 1, softWrap = false).size.width
    } ?: 0
    return inTwoLines && longestWord <= widthPx
}

/** Hasta tres ejercicios: nombre a la izquierda y dosis a la derecha (sin partir); luego «+N ejercicios más». */
@Composable
private fun HeroLines(lines: List<HomeHeroLine>, moreCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = line.name,
                    modifier = Modifier.weight(1f),
                    style = HomeTypography.heroLine,
                    color = WizardColors.text,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line.dose.isNotEmpty()) {
                    Text(
                        text = line.dose,
                        style = HomeTypography.heroDoseText,
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        if (moreCount > 0) {
            Text(
                text = if (moreCount == 1) "+1 ejercicio más" else "+$moreCount ejercicios más",
                style = WizardTypography.caption,
                color = WizardColors.textFaint,
            )
        }
    }
}

/** La meta con un reloj de 14 dp dibujado en línea fina, como el resto de símbolos de la marca. */
@Composable
private fun HeroMeta(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = 1.2.dp.toPx()
            val radius = size.minDimension / 2f - stroke
            drawCircle(color = WizardColors.textMuted, radius = radius, center = center, style = Stroke(width = stroke))
            drawLine(
                color = WizardColors.textMuted,
                start = center,
                end = Offset(center.x, center.y - radius * 0.55f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = WizardColors.textMuted,
                start = center,
                end = Offset(center.x + radius * 0.45f, center.y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        Text(
            text = text,
            style = WizardTypography.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Un botón del héroe: la píldora principal (de tinta), una secundaria (con borde) o una acción de texto. */
private enum class HeroButtonKind { PRIMARY, SECONDARY, TEXT }

private class HeroButton(val text: String, val tag: String, val kind: HeroButtonKind, val onClick: () -> Unit)

/**
 * Los botones de cada modo. «Editar» va como texto, como en el diseño de Home: así la píldora principal (o la de «Repetir
 * sesión») tiene el ancho que su texto necesita, también con la letra al 130 %. El descanso no tiene ninguno.
 */
private fun heroButtons(mode: HomeHeroMode, actions: HomeHeroActions): List<HeroButton> = when (mode) {
    HomeHeroMode.TODAY -> listOf(
        HeroButton("Empezar sesión", "home-hero-start", HeroButtonKind.PRIMARY, actions.onStart),
        HeroButton("Editar", "home-hero-edit", HeroButtonKind.TEXT, actions.onEdit),
    )
    HomeHeroMode.ONGOING -> listOf(
        HeroButton("Reanudar", "home-hero-start", HeroButtonKind.PRIMARY, actions.onResume),
        HeroButton("Editar", "home-hero-edit", HeroButtonKind.TEXT, actions.onEdit),
    )
    HomeHeroMode.DONE -> listOf(
        HeroButton("Repetir sesión", "home-hero-secondary", HeroButtonKind.SECONDARY, actions.onStart),
        HeroButton("Editar", "home-hero-edit", HeroButtonKind.TEXT, actions.onEdit),
    )
    HomeHeroMode.OTHER_DAY -> listOf(
        HeroButton("Ver en el programa", "home-hero-secondary", HeroButtonKind.SECONDARY, actions.onOpenProgram),
    )
    HomeHeroMode.REST -> emptyList()
    HomeHeroMode.NO_PROGRAM -> listOf(
        HeroButton("Crear programa", "home-hero-start", HeroButtonKind.PRIMARY, actions.onCreateProgram),
    )
    HomeHeroMode.REGISTER -> listOf(
        HeroButton("Registrar resultados", "home-hero-start", HeroButtonKind.PRIMARY, actions.onRegister),
    )
}

/** Una fila de botones con 10 dp entre ellos. Las píldoras ocupan el ancho que sobra; «Editar» mide lo que dice. */
@Composable
private fun HeroButtons(mode: HomeHeroMode, actions: HomeHeroActions) {
    val buttons = heroButtons(mode, actions)
    if (buttons.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        buttons.forEach { button ->
            when (button.kind) {
                HeroButtonKind.PRIMARY -> HomePrimaryPill(
                    text = button.text,
                    onClick = button.onClick,
                    modifier = Modifier.weight(1f),
                    tag = button.tag,
                )
                HeroButtonKind.SECONDARY -> HomeSecondaryPill(
                    text = button.text,
                    onClick = button.onClick,
                    modifier = Modifier.weight(1f),
                    tag = button.tag,
                )
                HeroButtonKind.TEXT -> HeroTextAction(text = button.text, onClick = button.onClick, tag = button.tag)
            }
        }
    }
}

/** Una acción de texto («Editar»): 56 dp de alto, toque de 48 dp como mínimo y sin píldora. */
@Composable
private fun HeroTextAction(text: String, onClick: () -> Unit, tag: String) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
            .testTag(tag)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = WizardTypography.cta, color = WizardColors.text, maxLines = 1)
    }
}

/** La identidad fija y tenue de las pantallas sin programa (TOPO sobre noche). Va al 35 %. */
internal fun homeHeroFallbackIdentity(): CoverIdentity = CoverIdentity(
    style = CoverStudioStyle.TOPO,
    palette = CoverPaletteKind.NOCHE,
    primary = SymbolPalette.columna,
    secondary = SymbolPalette.musculo,
    motif = 14,
    seed = 4242,
)

// ---------------------------------------------------------------- funciones puras

/**
 * La dosis de un ejercicio: «4 × 5–7 · 100 kg», «3 × 8–10 · RIR 2», «3 × AMRAP». Usa las series de trabajo (sin las vacías), las
 * reps (rango o fijas), y la carga: kg, % (con kg si hay 1RM), fallo, RIR o RPE, lo que haya. Null si no hay series de trabajo.
 */
internal fun heroDose(exercise: Exercise): String? {
    val working = exercise.sets.filter { !it.isEmptySlot }
    if (working.isEmpty()) return null
    val amrap = working.any { it.isAmrap || it.intensityMode == IntensityMode.AMRAP }
    val reps = if (amrap) "AMRAP" else working.first().effectiveRepRange()?.format()
    val head = if (reps != null) {
        "${working.size} × $reps"
    } else {
        "${working.size} ${if (working.size == 1) "serie" else "series"}"
    }
    return listOfNotNull(head, heroLoad(exercise, working)).joinToString(" · ")
}

/** La carga de la dosis: kg si los hay (o el % de 1RM en kg), si no el %, el fallo, el RIR o el RPE. */
private fun heroLoad(exercise: Exercise, working: List<ExerciseSet>): String? {
    val first = working.first()
    val percent = first.targetPercentageRM
    val kg = first.weight?.takeIf { it > 0.0 }
        ?: percent?.let { p -> exercise.reference1RM?.let { rm -> rm * p / 100.0 } }
    val rir = first.targetRIR
    val rpe = first.targetRPE
    return when {
        kg != null && kg > 0.0 -> "${formatHeroNumber(kg)} kg"
        percent != null -> "${formatHeroNumber(percent)} %"
        working.any { it.isFailure || it.intensityMode == IntensityMode.FAILURE } -> "al fallo"
        rir != null -> "RIR $rir"
        rpe != null -> "RPE ${formatHeroNumber(rpe)}"
        else -> null
    }
}

/** Un número para la dosis: sin decimales si es entero; si no, con una cifra decimal y coma (español). */
private fun formatHeroNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(Locale("es"), value)

/**
 * El kicker de la sesión del día: «HOY · DÍA 2 DE 4», «EN CURSO», «HOY · HECHA», «JUEVES 15 · DÍA 3 DE 4», «MIÉRCOLES 14».
 * [position] y [count] son la posición de ese día entre los que tienen sesión en «Tu semana»; null = no se sabe.
 */
internal fun homeHeroKicker(mode: HomeHeroMode, date: LocalDate, position: Int?, count: Int?): String {
    val dayPart = if (position != null && count != null && count > 0) "DÍA $position DE $count" else null
    return when (mode) {
        HomeHeroMode.TODAY -> listOfNotNull("HOY", dayPart).joinToString(" · ")
        HomeHeroMode.ONGOING -> "EN CURSO"
        HomeHeroMode.DONE -> "HOY · HECHA"
        HomeHeroMode.OTHER_DAY -> listOfNotNull(heroDayLabel(date), dayPart).joinToString(" · ")
        HomeHeroMode.REST -> heroDayLabel(date)
        HomeHeroMode.NO_PROGRAM -> "HOY"
        HomeHeroMode.REGISTER -> "DÍA DE COMPETICIÓN"
    }
}

/** El día en mayúsculas: «JUEVES 15». */
private fun heroDayLabel(date: LocalDate): String =
    "${weekdaysEs[date.dayOfWeek.value - 1]} ${date.dayOfMonth}".uppercase(Locale("es"))

/**
 * La línea «Siguiente: …» del descanso: «Siguiente: jueves 15 · Pierna A», con la siguiente sesión del programa después del día
 * ([next], la que busca `HomeWeekResolver.nextSession`). Si no se puede resolver, «Siguiente: la próxima semana». Null si no hay
 * «Tu semana» (no se sabe).
 */
internal fun homeHeroNextLine(weekDays: List<HomeWeekDay>, next: HomeNextSession?): String? {
    if (weekDays.isEmpty()) return null
    if (next == null) return "Siguiente: la próxima semana"
    return "Siguiente: ${weekdaysEs[next.date.dayOfWeek.value - 1]} ${next.date.dayOfMonth} · ${next.sessionName}"
}
