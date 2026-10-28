package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.programdetail.design.structure.StructureAction
import com.example.kpkn.screens.programdetail.design.structure.StructureMenuRow
import com.example.kpkn.screens.programdetail.design.structure.StructureSheetGroup
import com.example.kpkn.screens.programdetail.design.structure.StructureSheetHeader
import com.example.kpkn.screens.programdetail.design.structure.StructureSheetPadding
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons

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

/** El rótulo del origen: título de la fila de la hoja y, en minúscula, «Rangos: perfil del asistente» del volumen. */
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

/** El icono de la fila del origen: «spark» para el perfil del asistente (g-ranges), los deslizadores para los tuyos y el libro para la referencia. */
private fun rangesOriginIcon(origin: RangesOrigin): ImageVector = when (origin) {
    RangesOrigin.OWN_RANGES -> KpknLineIcons.Sliders
    RangesOrigin.WIZARD_PROFILE -> ProgressIcons.Spark
    RangesOrigin.GENERAL_REFERENCE -> KpknLineIcons.Book
}

/**
 * La línea de detalle de las propuestas: «2 músculos con cambios propuestos tras 5 semanas respondidas · Revisar». Sin semanas
 * respondidas conocidas, sin esa parte.
 */
internal fun rangesProposalsDetail(proposalsCount: Int, answeredWeeks: Int): String {
    val muscles = if (proposalsCount == 1) "1 músculo" else "$proposalsCount músculos"
    val weeks = when {
        answeredWeeks <= 0 -> ""
        answeredWeeks == 1 -> " tras 1 semana respondida"
        else -> " tras $answeredWeeks semanas respondidas"
    }
    return "$muscles con cambios propuestos$weeks · Revisar"
}

/**
 * La hoja «Rangos de volumen» de la escena g-ranges: de dónde salen, cómo cambiarlos (elegir los propios, revisar las respuestas), las
 * propuestas con tus datos (solo si las hay) y «Usar la referencia general» (solo si no se usa ya). La hoja no cambia nada por sí sola.
 */
@Composable
internal fun ProgramRangesSheet(
    origin: RangesOrigin,
    ownRangesCount: Int,
    proposalsCount: Int,
    proposalsCycle: Int?,
    proposalsAnsweredWeeks: Int,
    onChooseOwn: () -> Unit,
    onReviewAnswers: () -> Unit,
    onReviewProposals: () -> Unit,
    onUseGeneral: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(StructureSheetPadding),
        ) {
            StructureSheetHeader(
                title = "Rangos de volumen",
                subtitle = "Las series semanales por músculo que KPKN usa para colorear el volumen y preparar propuestas.",
            )
            StructureSheetGroup(text = "De dónde salen")
            StructureMenuRow(
                icon = rangesOriginIcon(origin),
                title = rangesOriginLabel(origin),
                detail = rangesOriginDetail(origin, ownRangesCount),
                onClick = {},
                enabled = false,
                divider = false,
            )
            StructureSheetGroup(text = "Cambiarlos")
            StructureMenuRow(
                icon = KpknLineIcons.Sliders,
                title = "Elegir mis propios rangos",
                detail = "Mínimo y máximo por músculo, con la referencia general al lado",
                onClick = onChooseOwn,
                modifier = Modifier.testTag("rangos_elegir"),
            )
            StructureMenuRow(
                icon = KpknLineIcons.Edit,
                title = "Revisar mis respuestas",
                detail = "Abre las mismas preguntas del asistente; cambia el perfil de todos tus programas",
                onClick = onReviewAnswers,
                modifier = Modifier.testTag("rangos_respuestas"),
            )
            if (proposalsCount > 0) {
                StructureSheetGroup(text = "Ajustar con tus datos")
                StructureMenuRow(
                    icon = KpknLineIcons.History,
                    title = "Propuestas del ciclo ${proposalsCycle ?: 1}",
                    detail = rangesProposalsDetail(proposalsCount, proposalsAnsweredWeeks),
                    onClick = onReviewProposals,
                    modifier = Modifier.testTag("rangos_propuestas"),
                )
            }
            if (onUseGeneral != null) {
                StructureAction(
                    text = "Usar la referencia general",
                    onClick = onUseGeneral,
                    faint = true,
                    modifier = Modifier.padding(top = 8.dp).testTag("rangos_referencia_general"),
                )
            }
        }
    }
}
