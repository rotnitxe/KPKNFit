package com.example.kpkn.screens.home.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.HomeProgramLine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Datos de la cabecera de Home. [dateLine] va en mayúsculas («MARTES 13 DE OCTUBRE»); [greeting] va sin signos («Buenos días»);
 * [name] es el nombre, que sirve para «Hola, …» si el saludo completo no cabe; [initial] es la letra del botón de perfil;
 * [program] es el programa activo, o null si no hay.
 */
data class HomeMastheadState(
    val dateLine: String,
    val greeting: String,
    val name: String,
    val initial: String,
    val program: HomeProgramLine?,
)

/** Fecha de la cabecera en español y en mayúsculas: «MARTES 13 DE OCTUBRE». */
fun homeDateLine(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es"))).uppercase(Locale("es"))

/**
 * Cabecera de la página de Home, sin estado. Primera fila: fecha y botón de perfil. Debajo, el saludo en Syne (se encoge hasta
 * 22 sp). Si hay programa: su nombre, «Semana N de M», la regla de semanas y la fase. Etiqueta de prueba: `home-masthead`.
 */
@Composable
fun HomeMasthead(
    state: HomeMastheadState,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = WizardSpacing.gutter, top = 20.dp, end = WizardSpacing.gutter)
            .testTag("home-masthead"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.dateLine,
                style = HomeTypography.eyebrow,
                color = WizardColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            HomeProfileButton(initial = state.initial, onClick = onOpenProfile)
        }
        Spacer(Modifier.height(6.dp))
        MastheadGreeting(greeting = state.greeting, name = state.name)
        state.program?.let { program ->
            Spacer(Modifier.height(18.dp))
            MastheadProgram(program = program)
        }
    }
}

/** Tamaños del saludo de la cabecera, de mayor a menor: desde 30 sp (el rol `question`) hasta 22 sp. */
private val mastheadGreetingSizesSp: List<Float> = (30 downTo 22).map { it.toFloat() }

/** Texto y tamaño en sp del saludo elegido para la cabecera. */
internal data class MastheadGreetingFit(val text: String, val sp: Float)

/**
 * Elige el saludo para un ancho de [widthPx] píxeles: el [full] en el mayor tamaño de 30 a 22 sp que quepa en una línea; si no
 * cabe, el [short] («Hola, …») con la misma bajada; y si tampoco, el [tiny] («Hola») a 22 sp, que la pantalla corta con «…» si
 * ni así cabe. [measureWidthPx] da el ancho en píxeles de una línea de un texto a un tamaño en sp.
 */
internal fun fitMastheadGreeting(
    full: String,
    short: String,
    tiny: String,
    widthPx: Float,
    measureWidthPx: (text: String, sp: Float) -> Float,
): MastheadGreetingFit {
    fun sizeFor(text: String): Float? = mastheadGreetingSizesSp.firstOrNull { measureWidthPx(text, it) <= widthPx }
    sizeFor(full)?.let { return MastheadGreetingFit(full, it) }
    sizeFor(short)?.let { return MastheadGreetingFit(short, it) }
    return MastheadGreetingFit(tiny, mastheadGreetingSizesSp.last())
}

@Composable
private fun MastheadGreeting(greeting: String, name: String) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        val fit = remember(greeting, name, widthPx, density.density, density.fontScale) {
            fitMastheadGreeting(
                full = "$greeting, $name",
                short = "Hola, $name",
                tiny = "Hola",
                widthPx = widthPx,
            ) { text, sp ->
                measurer.measure(
                    text = text,
                    style = WizardTypography.question.copy(fontSize = sp.sp),
                    maxLines = 1,
                    softWrap = false,
                ).size.width.toFloat()
            }
        }
        Text(
            text = fit.text,
            style = WizardTypography.question.copy(fontSize = fit.sp.sp, lineHeight = (fit.sp * 1.2f).sp),
            color = WizardColors.text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Botón de perfil: círculo de 48 dp con borde de 1 dp y la inicial en Syne. Se anuncia como «Abrir tu perfil». */
@Composable
private fun HomeProfileButton(initial: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag("home-profile")
            .size(48.dp)
            .clip(CircleShape)
            .border(1.dp, WizardColors.glassBorder, CircleShape)
            .semantics { contentDescription = "Abrir tu perfil" }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initial, style = HomeTypography.avatarInitial, color = WizardColors.text)
    }
}

/** Programa activo: nombre y «Semana N de M» en una fila; debajo la regla de semanas y la fase, si las hay. */
@Composable
private fun MastheadProgram(program: HomeProgramLine) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = program.name,
                style = WizardTypography.header,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            program.weekLabel?.let { label ->
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        val index = program.weekIndex
        val count = program.weekCount
        if (index != null && count != null && count >= 2) {
            Spacer(Modifier.height(8.dp))
            if (count <= 16) WeekRule(index = index, count = count) else WeekLine(index = index, count = count)
        }
        program.phase?.let { phase ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = phase,
                style = WizardTypography.note,
                color = WizardColors.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Tinta al 70 % para las semanas hechas. */
private val mastheadDoneWeek: Color = WizardColors.text.copy(alpha = 0.70f)

/** Blanco al 12 % para las semanas que faltan. */
private val mastheadFutureWeek: Color = Color.White.copy(alpha = 0.12f)

/** Color de la semana [i] (0 = primera) con la semana actual [index] (1 = primera): hecha, actual o futura. */
private fun weekColor(i: Int, index: Int): Color = when {
    i < index - 1 -> mastheadDoneWeek
    i == index - 1 -> SymbolPalette.musculo
    else -> mastheadFutureWeek
}

/**
 * Regla de semanas: [count] segmentos de 4 dp de alto, separados 3 dp, con esquinas de 2 dp. Hechas en tinta al 70 %, la actual
 * en músculo y las futuras al 12 %.
 */
@Composable
private fun WeekRule(index: Int, count: Int) {
    Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
        val gap = 3.dp.toPx()
        val segment = (size.width - gap * (count - 1)) / count
        val corner = CornerRadius(2.dp.toPx())
        for (i in 0 until count) {
            drawRoundRect(
                color = weekColor(i, index),
                topLeft = Offset(i * (segment + gap), 0f),
                size = Size(segment, size.height),
                cornerRadius = corner,
            )
        }
    }
}

/**
 * Con más de 16 semanas, una sola línea de 3 dp con el mismo código de color: lo hecho, la semana actual y lo que falta.
 */
@Composable
private fun WeekLine(index: Int, count: Int) {
    Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
        val width = size.width
        val doneEnd = width * (index - 1) / count
        val currentEnd = width * index / count
        drawRoundRect(
            color = mastheadFutureWeek,
            size = size,
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
        drawRect(color = mastheadDoneWeek, topLeft = Offset.Zero, size = Size(doneEnd, size.height))
        drawRect(
            color = SymbolPalette.musculo,
            topLeft = Offset(doneEnd, 0f),
            size = Size(currentEnd - doneEnd, size.height),
        )
    }
}
