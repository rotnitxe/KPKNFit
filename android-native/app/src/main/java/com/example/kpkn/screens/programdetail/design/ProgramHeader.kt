package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverIdentity
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/** Lo que pinta la cabecera. Lo calcula la pantalla; la cabecera no decide nada. */
internal data class ProgramHeaderState(
    val name: String,
    val status: ProgramHeaderStatus,
    /** «12 semanas · 4 días por semana · Fuerza»; sin dato, se omite (ver [programMetaLine]). */
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
)

/** Las acciones de la cabecera. Cada una la ejecuta la pantalla. */
internal data class ProgramHeaderActions(
    val onPrimary: () -> Unit,
    val onStartPause: () -> Unit,
    val onEdit: () -> Unit,
    /** Abre el día de la siguiente sesión en la pestaña Semana. */
    val onOpenNextSession: () -> Unit = {},
)

/**
 * Cabecera del programa (KPKN_UI_V2 §5, P1): póster a la izquierda; a su derecha, el estado, el nombre y la meta. Debajo, la regla
 * de semanas a todo el ancho, el progreso y la fila de acciones: la principal contextual, pausar o reanudar y editar.
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
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
        Spacer(Modifier.height(18.dp))
        ProgramActionRow(state = state, actions = actions)
    }
}

/**
 * Fila de acciones: la principal con pausar o reanudar y editar. En un día sin sesión, la píldora secundaria dice cuál es la siguiente
 * sesión («Siguiente: jueves · Pierna A») y ocupa el mismo ancho que «Empezar»; si no hay siguiente, ⏸ y ✎ van a la izquierda. Si la
 * etiqueta no cabe en la fila a la letra actual, la píldora ocupa la primera línea y los iconos van debajo, a la derecha: así nunca se
 * corta una palabra (KPKN_UI_V2 §5).
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
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // En la fila, la píldora cabe en lo que dejan dos iconos de 48 dp y 8 dp de separación.
        val pillRowPx = with(density) { (maxWidth - 8.dp - 48.dp - 8.dp - 48.dp).toPx() }
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
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProgramControlButtons(state = state, actions = actions)
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
                    Spacer(Modifier.width(8.dp))
                }
                // Sin píldora (día sin sesión y sin siguiente), ⏸ y ✎ quedan a la izquierda.
                ProgramControlButtons(state = state, actions = actions)
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

/** Pausar o reanudar (48 dp) y editar (48 dp), con 8 dp entre los dos. */
@Composable
private fun ProgramControlButtons(state: ProgramHeaderState, actions: ProgramHeaderActions) {
    KpknIconButton(
        icon = if (state.status == ProgramHeaderStatus.ACTIVE) KpknLineIcons.Pause else KpknLineIcons.Play,
        contentDescription = when (state.status) {
            ProgramHeaderStatus.ACTIVE -> "Pausar programa"
            ProgramHeaderStatus.PAUSED -> "Reanudar programa"
            ProgramHeaderStatus.DRAFT -> "Activar programa"
        },
        onClick = actions.onStartPause,
    )
    Spacer(Modifier.width(8.dp))
    KpknIconButton(
        icon = KpknLineIcons.Edit,
        contentDescription = "Editar programa",
        onClick = actions.onEdit,
    )
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
