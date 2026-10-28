package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/** De dónde sale el rango con el que el volumen del programa colorea cada músculo. */
internal enum class RangesOrigin { OWN_RANGES, WIZARD_PROFILE, GENERAL_REFERENCE }

/**
 * El origen de los rangos: los que elegiste a mano mandan; sin ninguno, el perfil del asistente si lo hay y, si no, la referencia general.
 */
internal fun rangesOriginOf(ownRangesCount: Int, hasProfile: Boolean): RangesOrigin = when {
    ownRangesCount > 0 -> RangesOrigin.OWN_RANGES
    hasProfile -> RangesOrigin.WIZARD_PROFILE
    else -> RangesOrigin.GENERAL_REFERENCE
}

/** El rótulo del origen: va en la fila «Rangos: …». */
internal fun rangesOriginLabel(origin: RangesOrigin): String = when (origin) {
    RangesOrigin.OWN_RANGES -> "Tus rangos"
    RangesOrigin.WIZARD_PROFILE -> "Perfil del asistente"
    RangesOrigin.GENERAL_REFERENCE -> "Referencia general"
}

/** La explicación del origen, en la hoja. Con un solo músculo elegido a mano, va en singular. */
internal fun rangesOriginDetail(origin: RangesOrigin, ownRangesCount: Int): String = when (origin) {
    RangesOrigin.OWN_RANGES -> {
        val muscles = if (ownRangesCount == 1) "1 músculo" else "$ownRangesCount músculos"
        "$muscles con rango elegido por ti; el resto sale del perfil"
    }
    RangesOrigin.WIZARD_PROFILE -> "Lo que respondiste al empezar: estilo, técnica, constancia, fuerza y movilidad"
    RangesOrigin.GENERAL_REFERENCE -> "Series de referencia por músculo. Responde las preguntas para ajustarlas a ti"
}

/**
 * La fila «Rangos: …» bajo la cabecera del volumen: el origen, con un icono y un chevron. Tocarla abre la hoja «Rangos de volumen».
 * 44 dp como mínimo.
 */
@Composable
internal fun ProgramRangesRow(origin: RangesOrigin, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .testTag("progreso_rangos")
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = KpknLineIcons.Sliders, contentDescription = null, modifier = Modifier.size(16.dp), tint = WizardColors.textMuted)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Rangos: ${rangesOriginLabel(origin)}",
            style = KpknType.body,
            color = WizardColors.textMuted,
            modifier = Modifier.weight(1f),
        )
        Icon(imageVector = KpknLineIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = WizardColors.textMuted)
    }
}

/**
 * La hoja «Rangos de volumen»: de dónde salen los rangos y las formas de cambiarlos. Elegir los propios, revisar las respuestas y revisar
 * las propuestas son las funciones de siempre, ahora desde aquí; la hoja no cambia nada por sí sola.
 */
@Composable
internal fun ProgramRangesSheet(
    origin: RangesOrigin,
    ownRangesCount: Int,
    proposalsCount: Int,
    proposalsCycle: Int?,
    onChooseOwn: () -> Unit,
    onReviewAnswers: () -> Unit,
    onReviewProposals: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss, stableHeightFraction = 0.85f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Rangos de volumen", style = KpknType.dialogTitle, color = WizardColors.text)
            Text(
                "Las series semanales por músculo que KPKN usa para colorear el volumen y preparar propuestas.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            RangesGroup("De dónde salen") {
                RangesRow(
                    icon = KpknLineIcons.Info,
                    title = rangesOriginLabel(origin),
                    detail = rangesOriginDetail(origin, ownRangesCount),
                )
            }
            RangesGroup("Cambiarlos") {
                RangesRow(
                    icon = KpknLineIcons.Sliders,
                    title = "Elegir mis propios rangos",
                    detail = "Mínimo y máximo por músculo, con la referencia general al lado",
                    onClick = onChooseOwn,
                    tag = "rangos_elegir",
                )
                RangesRow(
                    icon = KpknLineIcons.Edit,
                    title = "Revisar mis respuestas",
                    detail = "Técnica, constancia, fuerza y movilidad: recalculan los rangos de este programa",
                    onClick = onReviewAnswers,
                    tag = "rangos_respuestas",
                    divider = true,
                )
            }
            RangesGroup("Ajustar con tus datos") {
                if (proposalsCount > 0) {
                    val muscles = if (proposalsCount == 1) "1 músculo" else "$proposalsCount músculos"
                    RangesRow(
                        icon = KpknLineIcons.History,
                        title = "Propuestas del ciclo ${proposalsCycle ?: 1}",
                        detail = "$muscles con cambios propuestos · Revisar",
                        onClick = onReviewProposals,
                        tag = "rangos_propuestas",
                    )
                } else {
                    Text(
                        "Al cerrar un ciclo con check-ins semanales, KPKN propondrá ajustes con tus datos.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }
        }
    }
}

/** Un grupo de la hoja: el rótulo en mayúsculas, en `textFaint`, y sus filas. */
@Composable
private fun RangesGroup(eyebrow: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = eyebrow.uppercase(Locale("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textFaint,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 4.dp),
        )
        content()
    }
}

/**
 * Una fila de la hoja, como las de «Pendiente»: icono, título y detalle, 54 dp como mínimo. Con [onClick] es un botón; sin él solo informa.
 * Un filete la separa de la anterior cuando [divider] es verdadero.
 */
@Composable
private fun RangesRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: (() -> Unit)? = null,
    tag: String? = null,
    divider: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 54.dp)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .then(
            if (onClick != null) {
                Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        )
        .padding(vertical = 4.dp, horizontal = 2.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        if (divider) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        }
        Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
            RangesIcon(icon = icon)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = RangesTitleStyle, color = WizardColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(text = detail, style = KpknType.caption, color = WizardColors.textFaint)
            }
        }
    }
}

/** Icono de una fila: círculo de 38 dp con fondo blanco al 6 % e icono en `textMuted`, como en «Pendiente». */
@Composable
private fun RangesIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = WizardColors.textMuted)
    }
}

/** Título de una fila: Inter SemiBold 14,5 sp, igual que en «Pendiente». */
private val RangesTitleStyle = KpknType.bodyStrong.copy(fontSize = 14.5f.sp, lineHeight = 20.sp)
