package com.example.kpkn.screens.home.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.domain.training.HomeNextSession
import com.example.kpkn.domain.training.HomeWeekDay
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.programdetail.design.ProgramNextSession
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import java.time.LocalDate
import java.util.Locale

/*
 * La sesión del día dentro del módulo «Esta semana» (Home v1.1, I10a): compacta y sin portada. Sin estado: recibe el estado ya
 * resuelto y las acciones. Al final, las funciones puras (la dosis, el kicker y el «Siguiente»): no usan Android, así que se prueban
 * sin Robolectric.
 */

/** Qué le toca a la sesión del día elegido: decide el kicker, el título, los botones y si salen músculos. */
internal enum class HomeHeroMode { TODAY, ONGOING, DONE, OTHER_DAY, REST, NO_PROGRAM, REGISTER }

/** Un ejercicio del héroe: nombre y dosis ya formateada («4 × 5–7 · 100 kg»). */
internal data class HomeHeroLine(val name: String, val dose: String)

/** La cuenta atrás de la competición: la cifra ([value], con su [unit] si la tiene) y su etiqueta («para tu competición»). */
internal data class HomeHeroCountdown(val value: String, val unit: String?, val label: String)

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
    val countdown: HomeHeroCountdown? = null,
)

/**
 * Las acciones del héroe. Quien llama las cablea con la sesión de su página. [onOpenProgram] es null cuando no hay programa que abrir:
 * entonces no sale el botón «Ver en el programa» ni «Ver programa».
 */
internal data class HomeHeroActions(
    val onStart: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onOpenProgram: (() -> Unit)? = null,
    val onChooseProgram: () -> Unit = {},
    val onCreateProgram: () -> Unit = {},
    /** «Registrar resultados»: el registro de la competencia (Registros v4, c-log). */
    val onRegister: () -> Unit = {},
    /** El día de la competencia (MEET_DAY): «Modo competición» abre el modo competición de la propia competencia (c-today, C-D2). */
    val onOpenCompetition: (() -> Unit)? = null,
    /** Tocar la cuenta atrás abre la página «Competencias» (c-hub). */
    val onOpenCountdown: (() -> Unit)? = null,
)

/** Días de la semana en español, de lunes a domingo, en minúscula (para «Siguiente: jueves»). */
private val weekdaysEs = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/**
 * La sesión del día, compacta: el kicker (con la insignia de aviso a la derecha), el título en dos líneas como mucho, hasta tres
 * ejercicios, la meta, la cuenta atrás, la fila de músculos (solo si [muscles] tiene algo) y los botones del modo. No pinta fondo:
 * lo pone el módulo. Etiqueta `home-hero`, con el ancla del recorrido [TourAnchors.HOME_SESION].
 */
@Composable
internal fun HomeSessionHero(
    state: HomeHeroState,
    actions: HomeHeroActions,
    modifier: Modifier = Modifier,
    muscles: List<HomeSessionMuscle> = emptyList(),
    onOpenMuscles: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home-hero")
            .tourAnchor(TourAnchors.HOME_SESION),
    ) {
        HeroKicker(kicker = state.kicker, done = state.mode == HomeHeroMode.DONE, badge = state.advisoryBadge)
        Spacer(Modifier.height(8.dp))
        HeroTitle(text = state.title)
        if (state.lines.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HeroLines(lines = state.lines, moreCount = state.moreCount)
        }
        state.body?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = KpknType.body,
                color = WizardColors.textMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.meta?.let {
            Spacer(Modifier.height(12.dp))
            HeroMeta(text = it)
        }
        state.countdown?.let {
            Spacer(Modifier.height(12.dp))
            HeroCountdown(countdown = it, onClick = actions.onOpenCountdown)
        }
        if (muscles.isNotEmpty()) HomeMuscleChips(muscles = muscles, onOpen = onOpenMuscles)
        val buttons = heroButtons(mode = state.mode, actions = actions)
        if (buttons.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            HeroButtonRow(buttons = buttons)
        }
    }
}

/**
 * El kicker («HOY · DÍA 2 DE 4») en el eyebrow; en una sesión hecha, va en `done` con el check. [badge], si lo hay, va a su derecha
 * como insignia. Si no cabe a su lado, baja a una línea propia, a la izquierda, y así el aviso no se corta.
 */
@Composable
private fun HeroKicker(kicker: String, done: Boolean, badge: String?) {
    if (badge == null) {
        HeroKickerText(kicker = kicker, done = done)
        return
    }
    Layout(
        content = {
            HeroKickerText(kicker = kicker, done = done)
            HeroBadge(text = badge)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val maxWidth = constraints.maxWidth
        val kickerPlaceable = measurables[0].measure(Constraints(maxWidth = maxWidth))
        val badgeWidth = measurables[1].maxIntrinsicWidth(24.dp.roundToPx())
        if (kickerPlaceable.width + gap + badgeWidth <= maxWidth) {
            val badgePlaceable = measurables[1].measure(Constraints(maxWidth = maxWidth - kickerPlaceable.width - gap))
            val height = maxOf(kickerPlaceable.height, badgePlaceable.height)
            layout(maxWidth, height) {
                kickerPlaceable.placeRelative(0, (height - kickerPlaceable.height) / 2)
                badgePlaceable.placeRelative(kickerPlaceable.width + gap, (height - badgePlaceable.height) / 2)
            }
        } else {
            val badgePlaceable = measurables[1].measure(Constraints(maxWidth = maxWidth))
            layout(maxWidth, kickerPlaceable.height + gap + badgePlaceable.height) {
                kickerPlaceable.placeRelative(0, 0)
                badgePlaceable.placeRelative(0, kickerPlaceable.height + gap)
            }
        }
    }
}

/** El texto del kicker, con el check delante si la sesión está hecha. */
@Composable
private fun HeroKickerText(kicker: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (done) {
            Icon(
                imageVector = KpknLineIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = WizardColors.done,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = kicker,
            style = KpknType.eyebrow,
            color = if (done) WizardColors.done else WizardColors.textMuted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** La insignia de aviso: píldora `inset` de 24 dp, con el texto en `caption` y en la energía. Se corta con elipsis si no cabe. */
@Composable
private fun HeroBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 24.dp)
            .clip(KpknShape.pill)
            .background(KpknSurface.inset)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KpknType.caption,
            color = SymbolPalette.energia,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * El título de la sesión: en dos líneas como mucho. Si no cabe entero, se quedan las palabras enteras que quepan y «…»: nunca se
 * corta una palabra por la mitad.
 */
@Composable
private fun HeroTitle(text: String) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val shown = remember(text, widthPx, density.density, density.fontScale) {
            fitWordsToLines(measurer, text, KpknType.title, widthPx, maxLines = 2)
        }
        Text(
            text = shown,
            style = KpknType.title,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Hasta tres ejercicios: el nombre a la izquierda, en una línea y sin cortar palabras, y la dosis a la derecha, entera; luego «+N».
 */
@Composable
private fun HeroLines(lines: List<HomeHeroLine>, moreCount: Int) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column {
        lines.forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val widthPx = constraints.maxWidth
                    val shown = remember(line.name, widthPx, density.fontScale) {
                        fitWordsToLines(measurer, line.name, KpknType.bodyStrong, widthPx, maxLines = 1)
                    }
                    Text(
                        text = shown,
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (line.dose.isNotEmpty()) {
                    Text(
                        text = line.dose,
                        style = HomeTypography.dose,
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
                modifier = Modifier.padding(top = 2.dp),
                style = KpknType.caption,
                color = WizardColors.textFaint,
            )
        }
    }
}

/**
 * El texto cabe en [maxLines] líneas de [style] y [widthPx] sin cortar ninguna palabra: si no cabe entero, se quedan las palabras
 * enteras que quepan y «…». El elipsis de Compose corta por letras y deja «movilida…»; esta función no. Si ni una palabra cabe, se
 * devuelve la primera con «…».
 */
private fun fitWordsToLines(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    widthPx: Int,
    maxLines: Int,
): String {
    fun fits(candidate: String): Boolean = !measurer.measure(
        text = candidate,
        style = style,
        constraints = Constraints(maxWidth = widthPx),
        maxLines = maxLines,
    ).hasVisualOverflow
    if (fits(text)) return text
    val words = text.split(' ').filter { it.isNotBlank() }
    for (keep in words.size - 1 downTo 1) {
        val candidate = words.take(keep).joinToString(" ") + "…"
        if (fits(candidate)) return candidate
    }
    return words.firstOrNull()?.plus("…") ?: text
}

/** La meta de la sesión, con el reloj de línea de 16 dp, como en los demás símbolos de Home. */
@Composable
private fun HeroMeta(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = KpknLineIcons.Clock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = WizardColors.textMuted,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** La cuenta atrás de la competición: la cifra en `metricM` con su unidad, y la etiqueta en `caption` a su lado. */
@Composable
private fun HeroCountdown(countdown: HomeHeroCountdown, onClick: (() -> Unit)? = null) {
    Row(
        modifier = if (onClick != null) Modifier.heightIn(min = 48.dp).clickable(onClickLabel = "Abrir Competencias", onClick = onClick) else Modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KpknMetric(
            value = countdown.value,
            unit = countdown.unit,
            style = KpknType.metricM,
            color = SymbolPalette.energia,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = countdown.label,
            modifier = Modifier.weight(1f, fill = false),
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Un botón del héroe: la píldora principal (de tinta) o la secundaria (con borde). */
private enum class HeroButtonKind { PRIMARY, SECONDARY }

private class HeroButton(val text: String, val tag: String, val kind: HeroButtonKind, val onClick: () -> Unit)

/**
 * Los botones de cada modo. Hoy: «Empezar sesión» y «Editar»; en curso: «Continuar sesión»; hecha: «Repetir sesión» y «Editar»; otro
 * día y descanso: una secundaria que abre el programa; sin programa: «Elige tu programa» y «Crear o importar»; competición:
 * «Registrar resultados». Las etiquetas de prueba se conservan.
 */
private fun heroButtons(mode: HomeHeroMode, actions: HomeHeroActions): List<HeroButton> = when (mode) {
    HomeHeroMode.TODAY -> listOf(
        HeroButton("Empezar sesión", "home-hero-start", HeroButtonKind.PRIMARY, actions.onStart),
        HeroButton("Editar", "home-hero-edit", HeroButtonKind.SECONDARY, actions.onEdit),
    )
    HomeHeroMode.ONGOING -> listOf(
        HeroButton("Continuar sesión", "home-hero-start", HeroButtonKind.PRIMARY, actions.onResume),
    )
    HomeHeroMode.DONE -> listOf(
        HeroButton("Repetir sesión", "home-hero-secondary", HeroButtonKind.SECONDARY, actions.onStart),
        HeroButton("Editar", "home-hero-edit", HeroButtonKind.SECONDARY, actions.onEdit),
    )
    HomeHeroMode.OTHER_DAY -> listOfNotNull(
        actions.onOpenProgram?.let { HeroButton("Ver en el programa", "home-hero-secondary", HeroButtonKind.SECONDARY, it) },
    )
    HomeHeroMode.REST -> listOfNotNull(
        actions.onOpenProgram?.let { HeroButton("Ver programa", "home-hero-secondary", HeroButtonKind.SECONDARY, it) },
    )
    HomeHeroMode.NO_PROGRAM -> listOf(
        HeroButton("Elige tu programa", "home-hero-start", HeroButtonKind.PRIMARY, actions.onChooseProgram),
        HeroButton("Crear o importar", "home-hero-secondary", HeroButtonKind.SECONDARY, actions.onCreateProgram),
    )
    HomeHeroMode.REGISTER -> actions.onOpenCompetition?.let { openCompetition ->
        // Día de la competencia (C-D2): el botón abre el modo competición, que guarda cada intento.
        listOf(HeroButton("Modo competición", "home-hero-start", HeroButtonKind.PRIMARY, openCompetition))
    } ?: listOf(
        HeroButton("Registrar resultados", "home-hero-start", HeroButtonKind.PRIMARY, actions.onRegister),
    )
}

/** Una píldora del héroe (KPKN_UI_V2 §6), con su etiqueta de prueba. */
@Composable
private fun HeroButtonView(button: HeroButton) {
    when (button.kind) {
        HeroButtonKind.PRIMARY -> KpknPrimaryPill(text = button.text, onClick = button.onClick, tag = button.tag)
        HeroButtonKind.SECONDARY -> KpknSecondaryPill(text = button.text, onClick = button.onClick, tag = button.tag)
    }
}

/**
 * La fila de botones, con 10 dp entre ellos. Si caben a su ancho natural, van en una fila y la primera ocupa lo que sobra. Si no
 * (la letra al 130 %), se apilan a ancho completo, para que ninguna etiqueta se corte.
 */
@Composable
private fun HeroButtonRow(buttons: List<HeroButton>) {
    Layout(
        content = { buttons.forEach { HeroButtonView(button = it) } },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val gap = 10.dp.roundToPx()
        val pillHeight = 56.dp.roundToPx()
        val maxWidth = constraints.maxWidth
        val gaps = gap * (measurables.size - 1)
        val naturalWidths = measurables.map { it.maxIntrinsicWidth(pillHeight) }
        val inRow = measurables.size == 1 || naturalWidths.sum() + gaps <= maxWidth
        if (inRow) {
            val firstWidth = maxWidth - naturalWidths.drop(1).sum() - gaps
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints.fixed(if (index == 0) firstWidth else naturalWidths[index], pillHeight))
            }
            layout(maxWidth, pillHeight) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, 0)
                    x += placeable.width + gap
                }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints.fixed(maxWidth, pillHeight)) }
            layout(maxWidth, placeables.sumOf { it.height } + gaps) {
                var y = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y += placeable.height + gap
                }
            }
        }
    }
}

// ---------------------------------------------------------------- funciones puras

/**
 * La dosis de un ejercicio: «4 × 5–7 · 100 kg», «3 × 8–10 · RIR 2», «3 × AMRAP». Usa las series de trabajo (sin las vacías), las
 * reps (rango o fijas), y la carga: kg, % (con kg si hay 1RM), fallo, RIR o RPE, lo que haya. Null si no hay series de trabajo.
 */
internal fun heroDose(exercise: Exercise): String? {
    val working = exercise.sets.filter { !it.isEmptySlot }
    if (working.isEmpty()) return null
    val amrap = working.any { it.purpose.isAmrap }
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
        ?: com.example.kpkn.domain.calculations.percentLoadKg(exercise, first)
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
 * La línea «Siguiente: …» del descanso: «Siguiente: jueves 15 · Pierna A», con la siguiente sesión datada del programa después del día
 * ([next], la que busca `HomeWeekResolver.nextSession`). Sin ella, la del programa en su semana ([programNext], la misma función que
 * Program Detail): «Siguiente: jueves · Pierna A». Da la vuelta a la semana, así que cae siempre en los 7 días que siguen al descanso.
 * Si no hay ninguna, «Siguiente: la próxima semana». Null si no hay «Tu semana» (no se sabe).
 */
internal fun homeHeroNextLine(
    weekDays: List<HomeWeekDay>,
    next: HomeNextSession?,
    programNext: ProgramNextSession? = null,
): String? {
    if (weekDays.isEmpty()) return null
    if (next != null) return "Siguiente: ${weekdaysEs[next.date.dayOfWeek.value - 1]} ${next.date.dayOfMonth} · ${next.sessionName}"
    if (programNext != null) return programNext.label
    return "Siguiente: la próxima semana"
}
