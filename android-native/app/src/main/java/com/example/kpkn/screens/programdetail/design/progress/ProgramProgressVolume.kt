package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.screens.programdetail.design.ProgramVolumeBreakdown
import com.example.kpkn.screens.programdetail.design.ProgramVolumeMuscle
import com.example.kpkn.screens.programdetail.design.programVolumeSeries
import com.example.kpkn.screens.programdetail.design.structure.StructureAction
import com.example.kpkn.screens.programdetail.design.structure.StructureActionStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureBodyStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureCaptionStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureFaint
import com.example.kpkn.screens.programdetail.design.structure.StructureInk
import com.example.kpkn.screens.programdetail.design.structure.StructureModule
import com.example.kpkn.screens.programdetail.design.structure.StructureModuleHeader
import com.example.kpkn.screens.programdetail.design.structure.StructureMuted
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlin.math.max

/*
 * «Series por músculo» de la escena g-volume del lienzo v6: «Hecho / Plan», la fila «Rangos», una fila por músculo con la barra de lo hecho,
 * la marca de lo planificado y la franja del rango útil, la leyenda y, aparte, la tarjeta «Tus últimas 4 semanas» (Arreglos v1). Los estados vacíos
 * (programa sin activar, sin sesiones) no tienen escena: se rehacen con el mismo módulo y su acción, sin empujar nada hacia abajo.
 */

/** Franja del rango útil (`#43d18c26` con filete `#43d18c55`). */
private val UsefulZoneFill = Color(0x2643D18C)
private val UsefulZoneEdge = Color(0x5543D18C)

/** Fondo de la barra (`#ffffff08`). */
private val VolumeTrack = Color(0x08FFFFFF)

/**
 * El módulo. [muscles] llegan ya ordenadas; cada una lleva lo planificado ([ProgramVolumeMuscle.sets]), lo hecho
 * ([ProgramVolumeMuscle.real]) y su rango útil. Con [doneView] la barra es lo hecho y la marca lo planificado; si no, la barra es el plan.
 * [onDoneViewChange] null = sin registros, sin conmutador. Tocar un músculo despliega su [breakdown].
 */
@Composable
internal fun ProgressVolumeModule(
    muscles: List<ProgramVolumeMuscle>,
    doneView: Boolean,
    onDoneViewChange: ((Boolean) -> Unit)?,
    rangesLabel: String,
    onOpenRanges: () -> Unit,
    expandedMuscle: String?,
    onToggleMuscle: (String) -> Unit,
    breakdown: ProgramVolumeBreakdown?,
    modifier: Modifier = Modifier,
) {
    // Una escala para todas las filas (el lienzo usa 26 con un máximo de 24): la cifra más alta más 2 series de aire.
    val scale = (muscles.maxOfOrNull { m -> maxOf(m.sets, m.real ?: 0.0, m.zones?.maxRecoverable?.toDouble() ?: 0.0) } ?: 0.0) + 2.0
    StructureModule(modifier = modifier.testTag("progreso_volumen")) {
        StructureModuleHeader(
            label = "Series por músculo",
            action = if (onDoneViewChange != null) {
                {
                    ProgressSmallSegment(
                        options = listOf("Hecho", "Plan"),
                        selected = if (doneView) 0 else 1,
                        onSelect = { onDoneViewChange(it == 0) },
                        modifier = Modifier.testTag("volumen_hecho_plan"),
                    )
                }
            } else {
                null
            },
        )
        // `.act.faint` con «sliders» de 16 dp delante y «fwd» de 14 dp detrás, 2 dp bajo la cabecera.
        Row(
            modifier = Modifier
                .padding(top = 2.dp)
                .defaultMinSize(minHeight = 36.dp)
                .testTag("progreso_rangos")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onOpenRanges),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(imageVector = KpknLineIcons.Sliders, contentDescription = null, modifier = Modifier.size(16.dp), tint = StructureMuted)
            Text(text = "Rangos: $rangesLabel", style = StructureActionStyle, color = StructureMuted, maxLines = 1)
            Icon(imageVector = KpknLineIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = StructureMuted)
        }
        muscles.forEach { muscle ->
            val expanded = expandedMuscle == muscle.name
            VolumeRow(
                muscle = muscle,
                scale = scale,
                doneView = doneView,
                expanded = expanded,
                onToggle = { onToggleMuscle(muscle.name) },
            )
            if (expanded && breakdown != null) VolumeBreakdownList(breakdown)
        }
        ProgressLegend {
            ProgressLegendItem("Rango útil") { ProgressDot(UsefulZoneEdge) }
            ProgressLegendItem("Plan") { Box(Modifier.width(2.dp).height(12.dp).background(Color(0xAAF2EEE6))) }
            ProgressLegendItem("Indirectas: ½")
        }
    }
}

/**
 * `.vrow`: filete arriba y 10 dp de relleno; el músculo y «14 / 16» (sin unidad); la barra de 10 dp con la franja del rango útil (2 dp más
 * alta por arriba y por abajo), el relleno al 85 % (en `--danger` fuera del rango) y la marca de 18 dp de lo planificado; si falta para el
 * mínimo útil, la nota roja.
 */
@Composable
private fun VolumeRow(
    muscle: ProgramVolumeMuscle,
    scale: Double,
    doneView: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val plan = muscle.sets
    val value = if (doneView) muscle.real ?: 0.0 else plan
    val zones = muscle.zones
    val outside = zones != null && (value < zones.minEffective || value > zones.maxRecoverable)
    val below = zones != null && value < zones.minEffective
    val caption = when {
        !below -> null
        plan <= 0.0 -> "No hay trabajo directo en el plan"
        else -> "Por debajo del mínimo útil (${zones!!.minEffective})"
    }
    Column(Modifier.fillMaxWidth()) {
        ProgressDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onToggle,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = if (doneView) {
                        "${muscle.name}: ${programVolumeSeries(value)} hechas de ${programVolumeSeries(plan)} planificadas"
                    } else {
                        "${muscle.name}: ${programVolumeSeries(plan)} planificadas"
                    }
                    stateDescription = if (expanded) "Desplegado" else "Plegado"
                }
                .padding(vertical = 10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(text = muscle.name, style = ProgressLiftNameStyle, color = StructureInk, modifier = Modifier.weight(1f, fill = false))
                Text(
                    text = buildAnnotatedString {
                        append(programVolumeSeries(value))
                        if (doneView) {
                            withStyle(StructureCaptionStyle.toSpanStyle().copy(color = StructureFaint)) { append(" / ${programVolumeSeries(plan)}") }
                        }
                    },
                    style = ProgressVolumeValueStyle,
                    color = StructureInk,
                    maxLines = 1,
                )
            }
            VolumeBar(
                value = value,
                plan = plan.takeIf { doneView },
                zones = zones?.let { it.minEffective.toDouble() to it.maxRecoverable.toDouble() },
                scale = scale,
                color = if (outside) ProgressDanger else StructureInk,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (caption != null) {
                Text(text = caption, style = StructureCaptionStyle, color = ProgressDanger, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

/** `.vbar`: la pista, la franja del rango útil (2 dp más alta por cada lado), el relleno (1 % como mínimo) y la marca de lo planificado (4 dp por cada lado). */
@Composable
private fun VolumeBar(
    value: Double,
    plan: Double?,
    zones: Pair<Double, Double>?,
    scale: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(10.dp)) {
        val width = maxWidth
        Box(Modifier.fillMaxWidth().height(10.dp).background(VolumeTrack, RoundedCornerShape(5.dp)))
        if (zones != null) {
            val start = width * (zones.first / scale).toFloat().coerceIn(0f, 1f)
            val span = width * ((zones.second - zones.first) / scale).toFloat().coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset(x = start)
                    .width(span)
                    .requiredHeight(14.dp)
                    .drawBehind {
                        val r = CornerRadius(6.dp.toPx())
                        drawRoundRect(UsefulZoneFill, cornerRadius = r)
                        val s = 1.dp.toPx()
                        drawRoundRect(UsefulZoneEdge, topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, size.height - s), cornerRadius = r, style = Stroke(s))
                    },
            )
        }
        Box(
            Modifier
                .width(width * max((value / scale).toFloat(), 0.01f).coerceAtMost(1f))
                .height(10.dp)
                .alpha(0.85f)
                .background(color, RoundedCornerShape(5.dp)),
        )
        if (plan != null) {
            val x = width * (plan / scale).toFloat().coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset(x = x)
                    .width(2.dp)
                    .requiredHeight(18.dp)
                    .alpha(0.55f)
                    .background(StructureInk, RoundedCornerShape(1.dp)),
            )
        }
    }
}

/**
 * El desglose de un músculo desplegado: los ejercicios que aportan series y, si el músculo tiene cabezas, cada cabeza con los suyos. Las
 * cifras van sin unidad, como en la fila.
 */
@Composable
private fun VolumeBreakdownList(breakdown: ProgramVolumeBreakdown) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (breakdown.exercises.isEmpty()) {
            Text(text = "Sin ejercicios en este alcance.", style = StructureCaptionStyle, color = StructureFaint)
        }
        breakdown.exercises.forEach { line -> BreakdownLine(line.name, line.sets, StructureInk) }
        breakdown.subMuscles.forEach { sub ->
            val role = when (sub.role) {
                MuscleRole.PRIMARY -> "directo"
                MuscleRole.SECONDARY -> "secundario"
                MuscleRole.STABILIZER -> "estabilizador"
                else -> null
            }
            Spacer(Modifier.height(2.dp))
            BreakdownLine(listOfNotNull(sub.name, role).joinToString(" · "), sub.sets, StructureMuted)
            sub.exercises.forEach { line -> BreakdownLine(line.name, line.sets, StructureFaint, indent = 12) }
        }
    }
}

/** Una línea del desglose: el nombre y la cifra a la derecha, en `.cap`. */
@Composable
private fun BreakdownLine(name: String, sets: Double, color: Color, indent: Int = 0) {
    Row(Modifier.fillMaxWidth().padding(start = indent.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = name, style = StructureCaptionStyle, color = color, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(text = programVolumeSeries(sets), style = StructureCaptionStyle, color = StructureFaint, maxLines = 1)
    }
}

/**
 * Arreglos v1 (escena p-progress): el enlace «Ver lo que has hecho en 4 semanas» pasa a ser la tarjeta «Tus últimas 4 semanas»
 * ([ProgramFourWeeksEntry]) con su línea y el mini calendario de los 28 días, calculados de [program] y sus [logs]. Sin [program], la
 * tarjeta sale con el título y el calendario vacío.
 */
@Composable
internal fun ProgressVolumeHistoryLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    program: Program? = null,
    logs: List<WorkoutLog> = emptyList(),
) {
    val currentWeekId = remember(program) { program?.let { fourWeeksCurrentWeekId(it) } }
    val model = rememberProgramFourWeeks(program, logs, currentWeekId, withMuscles = false)
    ProgramFourWeeksEntry(model = model, onClick = onClick, modifier = modifier)
}

/**
 * El volumen sin datos que contar (sin escena en el lienzo): el mismo módulo «Series por músculo» con una frase y su acción, en una sola
 * altura para no empujar nada. [inactive] = programa sin activar («Activar»); si no, la semana sin sesiones («Añadir sesión»).
 */
@Composable
internal fun ProgressVolumeEmpty(inactive: Boolean, onAction: () -> Unit, modifier: Modifier = Modifier) {
    StructureModule(modifier = modifier.testTag(if (inactive) "volumen_inactivo" else "volumen_sin_sesiones")) {
        StructureModuleHeader(
            label = "Series por músculo",
            action = {
                StructureAction(
                    text = if (inactive) "Activar" else "Añadir sesión",
                    onClick = onAction,
                    trailingIcon = KpknLineIcons.ChevronRight,
                )
            },
        )
        Text(
            text = if (inactive) {
                "Activa tu programa para ver el volumen."
            } else {
                "Aún no hay sesiones en esta semana."
            },
            style = StructureBodyStyle,
            color = StructureMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
