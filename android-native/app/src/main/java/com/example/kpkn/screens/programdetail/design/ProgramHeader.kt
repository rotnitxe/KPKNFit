package com.example.kpkn.screens.programdetail.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverIdentity
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/** Lo que pinta la cabecera. Lo calcula la pantalla; la cabecera no decide nada. */
internal data class ProgramHeaderState(
    val name: String,
    val status: ProgramHeaderStatus,
    /** «12 semanas · 4 días por semana · Fuerza»; sin dato, se omite (ver [programMetaLine]). Va en una sola línea. */
    val meta: String,
    /** Arte de estudio o de siempre; null solo si hay foto o portada empaquetada. */
    val identity: CoverIdentity?,
    val photoUrl: String?,
    val bundled: ImageBitmap?,
    val weekCount: Int,
    val currentWeekIndex: Int?,
    /** «Semana 3 de 12 · Acumulación»; null si no se sabe en qué semana va. */
    val progressLabel: String?,
    val primary: ProgramHeaderPrimary,
    /** La siguiente sesión, para un día sin sesión: su píldora dice «Siguiente: jueves · Pierna A». Null si no hay ninguna. */
    val nextSession: ProgramNextSession? = null,
    /** Línea de alcance del programa (ver [programScopeLine]). La cabecera ya no la pinta; el dato se conserva. */
    val scopeLine: String? = null,
    val deloadNotice: String? = null,
    /** Una fase por semana, para la cinta de fases. Vacía: se pinta la regla de semanas de siempre. */
    val ribbon: List<ProgramRibbonWeek> = emptyList(),
    /** El bloque actual, a la derecha de la cinta («Bloque 2 de 3»). */
    val blockLabel: String? = null,
    /** Una línea bajo la fila de acciones, p. ej. «Hoy también: Cardio y potencia · ≈ 32 min». */
    val todayAlso: String? = null,
)

/** Las acciones de la cabecera. Cada una la ejecuta la pantalla. */
internal data class ProgramHeaderActions(
    val onPrimary: () -> Unit,
    /** Ya no se pintan aquí: van en ⋯. */
    val onStartPause: () -> Unit,
    /** Ya no se pintan aquí: van en ⋯. */
    val onEdit: () -> Unit,
    /** Abre el día de la siguiente sesión en la pestaña Semana. */
    val onOpenNextSession: () -> Unit = {},
    /** «Compartir en historias». Null = la cabecera no pinta el botón. */
    val onShare: (() -> Unit)? = null,
    /** Pendientes sin resolver: la campana enseña la cuenta y, con 0, no se pinta. */
    val pendingCount: Int = 0,
    /** Abre la hoja «Pendiente». */
    val onPending: () -> Unit = {},
)

/**
 * Cabecera del programa (KPKN_UI_V2 §5): póster a la izquierda; a su derecha, el estado, el nombre y la meta. Debajo, la cinta de fases
 * (o la regla de semanas), el progreso y la fila de acciones: la principal contextual, compartir y la campana de pendientes.
 */
@Composable
internal fun ProgramHeader(
    state: ProgramHeaderState,
    actions: ProgramHeaderActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            ProgramPoster(
                identity = state.identity,
                photoUrl = state.photoUrl,
                bundled = state.bundled,
                animate = true,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                StatusEyebrow(state.status)
                Spacer(Modifier.height(8.dp))
                ProgramTitle(state.name)
                Spacer(Modifier.height(8.dp))
                if (state.meta.isNotBlank()) {
                    Text(
                        text = state.meta,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                state.deloadNotice?.let { notice ->
                    Spacer(Modifier.height(4.dp))
                    Text(notice, style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
        }
        if (state.ribbon.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ProgramPhaseRibbon(weeks = state.ribbon)
            if (state.progressLabel != null || state.blockLabel != null) {
                Spacer(Modifier.height(8.dp))
                ProgramRibbonLabels(progress = state.progressLabel, block = state.blockLabel)
            }
        } else {
            Spacer(Modifier.height(18.dp))
            ProgramWeekRuler(weekCount = state.weekCount, currentIndex = state.currentWeekIndex)
            state.progressLabel?.let { label ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = label,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ProgramActionRow(state = state, actions = actions)
        state.todayAlso?.let { line ->
            Text(
                text = line,
                style = KpknType.caption,
                color = WizardColors.textFaint,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp),
            )
        }
    }
}

/** Ancho que ocupa cada botón de la derecha: 52 dp y 8 dp de separación. */
private val ProgramTrailingSlot = 60.dp

/**
 * Fila de acciones: la principal (ocupa lo que sobra), «Compartir en historias» si lo hay, y la campana con los pendientes. En un día sin
 * sesión, la píldora secundaria enseña la siguiente sesión («Siguiente: jueves · Pierna A»). Si la etiqueta no cabe a la letra actual, la
 * píldora ocupa la primera línea y los botones van debajo, a la derecha: así nunca se corta una palabra (KPKN_UI_V2 §5).
 */
@Composable
private fun ProgramActionRow(state: ProgramHeaderState, actions: ProgramHeaderActions) {
    val primaryLabel = programHeaderPrimaryLabel(state.primary)
    val primaryIcon = if (state.primary is ProgramHeaderPrimary.Start || state.primary == ProgramHeaderPrimary.Continue) {
        KpknLineIcons.Play
    } else {
        null
    }
    // Sin acción principal (día sin sesión), la píldora secundaria enseña la siguiente sesión.
    val isNext = primaryLabel == null && state.nextSession != null
    val pillLabel = primaryLabel ?: state.nextSession?.label
    val pillOnClick = if (isNext) actions.onOpenNextSession else actions.onPrimary
    // En la píldora secundaria: el icono de calendario y lo que lee TalkBack de la siguiente sesión.
    val pillIcon = if (isNext) KpknLineIcons.Calendar else primaryIcon
    val pillDescription = if (isNext) state.nextSession?.description else null
    val trailing = (if (actions.onShare != null) 1 else 0) + (if (actions.pendingCount > 0) 1 else 0)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Lo que dejan los botones de la derecha; lo demás es para la píldora.
        val pillRowPx = with(density) { (maxWidth - ProgramTrailingSlot * trailing).toPx() }
        // Lo que necesita la píldora: el texto, el icono (20 dp y 8 dp) si lo hay, y 24 dp de margen a cada lado.
        val neededPx = if (pillLabel == null) {
            0f
        } else {
            measurer.measure(text = pillLabel, style = WizardTypography.cta, maxLines = 1, softWrap = false).size.width.toFloat() +
                with(density) { (48.dp + (if (pillIcon != null) 28.dp else 0.dp)).toPx() }
        }
        if (pillLabel != null && neededPx > pillRowPx) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ProgramHeaderPill(
                    isNext = isNext,
                    text = pillLabel,
                    onClick = pillOnClick,
                    modifier = Modifier.fillMaxWidth(),
                    icon = pillIcon,
                    description = pillDescription,
                )
                if (trailing > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProgramTrailingButtons(actions = actions)
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (pillLabel != null) {
                    ProgramHeaderPill(
                        isNext = isNext,
                        text = pillLabel,
                        onClick = pillOnClick,
                        modifier = Modifier.weight(1f),
                        icon = pillIcon,
                        description = pillDescription,
                    )
                    if (trailing > 0) Spacer(Modifier.width(8.dp))
                }
                // Sin píldora (día sin sesión y sin siguiente), los botones quedan a la izquierda.
                ProgramTrailingButtons(actions = actions)
            }
        }
    }
}

/**
 * La píldora de la fila: la principal («Empezar…», «Activar programa») o, en un día sin sesión, la secundaria con la siguiente sesión.
 */
@Composable
private fun ProgramHeaderPill(
    isNext: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: ImageVector?,
    description: String?,
) {
    if (isNext) {
        // La secundaria de la siguiente sesión: TalkBack lee «Siguiente sesión: jueves, Pierna A» en vez de la etiqueta corta.
        val pillModifier = if (description != null) modifier.semantics { contentDescription = description } else modifier
        KpknSecondaryPill(text = text, onClick = onClick, modifier = pillModifier, tag = "program_header_next", icon = icon)
    } else {
        KpknPrimaryPill(text = text, onClick = onClick, modifier = modifier, tag = "program_header_primary", icon = icon)
    }
}

/** Los botones de la derecha: compartir (si lo hay) y la campana con la cuenta, separados por 8 dp. */
@Composable
private fun ProgramTrailingButtons(actions: ProgramHeaderActions) {
    val share = actions.onShare
    if (share != null) {
        ProgramShareButton(onClick = share)
        if (actions.pendingCount > 0) Spacer(Modifier.width(8.dp))
    }
    ProgramPendingBell(count = actions.pendingCount, onClick = actions.onPending)
}

/** «Compartir en historias»: círculo de 52 dp en `inset` con el icono de compartir ([ProgramShareIcon]). */
@Composable
private fun ProgramShareButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "programShareScale",
    )
    Box(
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(KpknSurface.inset, CircleShape)
            .semantics { contentDescription = "Compartir en historias" }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = ProgramShareIcon, contentDescription = null, modifier = Modifier.size(20.dp), tint = WizardColors.text)
    }
}

/** Bajo la cinta: el progreso a la izquierda (`textMuted`) y el bloque a la derecha (`textFaint`). */
@Composable
private fun ProgramRibbonLabels(progress: String?, block: String?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        progress?.let { label ->
            Text(
                text = label,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        block?.let { name ->
            Text(
                text = name,
                style = KpknType.caption,
                color = WizardColors.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = if (progress == null) Modifier.weight(1f) else Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Eyebrow de estado: un punto (`done` activo, `energia` pausado, `textFaint` borrador) y el texto en mayúsculas. */
@Composable
private fun StatusEyebrow(status: ProgramHeaderStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(programHeaderDotColor(status)))
        Spacer(Modifier.width(8.dp))
        Text(
            text = programHeaderStatusLabel(status).uppercase(Locale("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
            maxLines = 1,
        )
    }
}

/** Nombre del programa: 26 sp como máximo, tres líneas como máximo, y se encoge hasta 20 sp antes de cortar. */
@Composable
private fun ProgramTitle(name: String) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val style = remember(name, widthPx, measurer) { fittedTitleStyle(measurer, name, widthPx) }
        Text(
            text = name,
            style = style,
            color = WizardColors.text,
            maxLines = TitleMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** Tamaños del nombre, en sp. */
private const val TitleMaxSp = 26f
private const val TitleMinSp = 20f
private const val TitleMaxLines = 3

/**
 * El estilo del nombre con la mayor letra (de 26 a 20 sp, en pasos de 1 sp) con la que cabe en [TitleMaxLines] líneas de [widthPx].
 * Si ni con 20 sp cabe, se queda en 20 sp y la línea final se corta con elipsis.
 */
internal fun fittedTitleStyle(measurer: TextMeasurer, name: String, widthPx: Int): TextStyle {
    var sp = TitleMaxSp
    while (sp > TitleMinSp) {
        val style = titleStyleAt(sp)
        val overflows = measurer.measure(
            text = name,
            style = style,
            constraints = Constraints(maxWidth = widthPx),
            maxLines = TitleMaxLines,
        ).hasVisualOverflow
        if (!overflows) return style
        sp -= 1f
    }
    return titleStyleAt(TitleMinSp)
}

private fun titleStyleAt(sp: Float): TextStyle = KpknType.display.copy(
    fontSize = sp.sp,
    lineHeight = (sp * 1.2f).sp,
)

/** Color del punto de estado; expuesto para las pruebas de la cabecera. */
internal fun programHeaderDotColor(status: ProgramHeaderStatus): Color = when (status) {
    ProgramHeaderStatus.ACTIVE -> WizardColors.done
    ProgramHeaderStatus.PAUSED -> SymbolPalette.energia
    ProgramHeaderStatus.DRAFT -> WizardColors.textFaint
}
