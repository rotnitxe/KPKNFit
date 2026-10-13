package com.example.kpkn.screens.home.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.HomeDayKind
import com.example.kpkn.domain.training.HomeWeekDay
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.onSymbolsVisible
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos

/** Ancho del subrayado que marca el día elegido. */
private val UnderlineWidth: Dp = 18.dp

/** Nombres de los días en español, de lunes a domingo (para la descripción de accesibilidad). */
private val dayNamesEs = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/**
 * «Tu semana»: una columna por día, sin estado. Tocar un día llama a [onSelect] con su fecha. El día elegido es un `Role.Tab`
 * seleccionado y lleva un subrayado de músculo que se desliza hasta su columna. Etiquetas: `home-week` y `home-day-<fecha ISO>`.
 * Sin [days] no pinta nada.
 */
@Composable
fun HomeWeekStrip(
    days: List<HomeWeekDay>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /** Relleno lateral de la tira: 16 dp por defecto; el módulo «Esta semana» lo pone a cero, porque ya tiene su propio aire. */
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    if (days.isEmpty()) return
    val reduced = wizardReducedMotion()
    // Solo se anima mientras la tira está a la vista (el reloj de «en curso» también depende de esto).
    var visible by remember { mutableStateOf(false) }
    val selectedIndex = days.indexOfFirst { it.date == selected }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .testTag("home-week")
            .onSymbolsVisible { visible = it },
    ) {
        val columnWidth = maxWidth / days.size
        val spec: AnimationSpec<Dp> = if (reduced) snap() else tween(420, easing = FastOutSlowInEasing)
        val underlineX by animateDpAsState(
            targetValue = columnWidth * selectedIndex.coerceAtLeast(0) + (columnWidth - UnderlineWidth) / 2,
            animationSpec = spec,
            label = "homeWeekUnderline",
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                days.forEach { day ->
                    HomeDayColumn(
                        day = day,
                        selected = day.date == selected,
                        animate = visible && !reduced,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                if (selectedIndex >= 0) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(underlineX.roundToPx(), 0) }
                            .size(width = UnderlineWidth, height = 2.dp)
                            .background(SymbolPalette.musculo, RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
    }
}

/**
 * Una columna de «Tu semana», de arriba abajo: letra, número en su círculo (relleno si es hoy, borde si es el elegido) y la marca
 * del estado. Es un `Role.Tab` con su descripción en español.
 */
@Composable
private fun HomeDayColumn(
    day: HomeWeekDay,
    selected: Boolean,
    animate: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = homeDayDescription(day)
    Column(
        modifier = modifier
            .testTag("home-day-${day.date}")
            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(day.date) })
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.letter,
            style = HomeTypography.eyebrow,
            color = if (day.isToday) WizardColors.text else WizardColors.textFaint,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(
                    when {
                        day.isToday -> Modifier.clip(CircleShape).background(WizardColors.text)
                        selected -> Modifier.border(1.5.dp, WizardColors.text, CircleShape)
                        else -> Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = HomeTypography.dayNumber,
                color = when {
                    day.isToday -> WizardColors.background
                    selected -> WizardColors.text
                    else -> WizardColors.textMuted
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.height(14.dp), contentAlignment = Alignment.Center) {
            DayMark(kind = day.kind, animate = animate)
        }
    }
}

/**
 * La marca de cada día, en 14 dp: check verde si está hecho; punto de músculo si toca o está en curso (este último respira);
 * punto hueco si era un día de entreno y no se hizo; guion tenue si es descanso.
 */
@Composable
private fun DayMark(kind: HomeDayKind, animate: Boolean) {
    when (kind) {
        HomeDayKind.DONE -> Canvas(Modifier.size(14.dp)) {
            drawCircle(color = SymbolPalette.ok)
            val check = Path().apply {
                moveTo(size.width * 0.27f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.68f)
                lineTo(size.width * 0.73f, size.height * 0.36f)
            }
            drawPath(
                path = check,
                color = SymbolPalette.onOk,
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        HomeDayKind.PLANNED -> Canvas(Modifier.size(6.dp)) {
            drawCircle(color = SymbolPalette.musculo)
        }
        HomeDayKind.ONGOING -> OngoingDot(animate = animate)
        HomeDayKind.MISSED -> Canvas(Modifier.size(6.dp)) {
            drawCircle(color = WizardColors.textFaint, style = Stroke(width = 1.dp.toPx()))
        }
        HomeDayKind.REST -> Box(Modifier.size(width = 8.dp, height = 2.dp).background(WizardColors.textFaint))
    }
}

/** Punto de «en curso»: respira de 1 a 1,4 en 1,6 s, solo si [animate] (la fila se ve y no hay movimiento reducido). */
@Composable
private fun OngoingDot(animate: Boolean) {
    val clock = rememberSymbolClock(active = animate)
    Canvas(
        Modifier
            .size(6.dp)
            .graphicsLayer {
                val s = 1f + 0.4f * (0.5f - 0.5f * cos(2f * PI.toFloat() * clock.seconds / 1.6f))
                scaleX = s
                scaleY = s
            },
    ) {
        drawCircle(color = SymbolPalette.musculo)
    }
}

/** Descripción de accesibilidad: «martes 13, hoy, Pierna A, pendiente» o «miércoles 14, descanso». */
private fun homeDayDescription(day: HomeWeekDay): String {
    val weekday = dayNamesEs[day.date.dayOfWeek.value - 1]
    val state = when (day.kind) {
        HomeDayKind.DONE -> "hecha"
        HomeDayKind.PLANNED -> "pendiente"
        HomeDayKind.ONGOING -> "en curso"
        HomeDayKind.MISSED -> "sin hacer"
        HomeDayKind.REST -> null
    }
    return buildList {
        add("$weekday ${day.date.dayOfMonth}")
        if (day.isToday) add("hoy")
        add(day.sessionName ?: "descanso")
        if (state != null) add(state)
    }.joinToString(", ")
}
