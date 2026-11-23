package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.programdetail.design.structure.StructureAction
import com.example.kpkn.screens.programdetail.design.structure.StructureCaptionStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureEyebrowStyle
import com.example.kpkn.screens.programdetail.design.structure.StructureFaint
import com.example.kpkn.screens.programdetail.design.structure.StructureInk
import com.example.kpkn.screens.programdetail.design.structure.StructureModule
import com.example.kpkn.screens.programdetail.design.structure.StructureMuted
import com.example.kpkn.screens.programdetail.design.structure.tabular
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import java.util.Locale

private val CollapsibleLocale = Locale.forLanguageTag("es")

/**
 * Acordeón nativo KPKN para la pestaña Progreso de Program Detail.
 * Mantiene la estética de [StructureModule] con cabecera interactiva, resumen tabular a la derecha y chevron animado.
 */
@Composable
internal fun ProgramCollapsibleModule(
    title: String,
    summary: String? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "collapsibleChevronRotation",
    )
    StructureModule(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onToggle,
                )
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title.uppercase(CollapsibleLocale),
                style = StructureEyebrowStyle,
                color = StructureMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = StructureCaptionStyle.tabular(),
                        color = StructureFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = KpknLineIcons.ChevronDown,
                    contentDescription = if (expanded) "Contraer $title" else "Desplegar $title",
                    tint = StructureMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun ProgramFourWeeksQuickLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KpknSurface.module)
            .border(1.dp, KpknSurface.moduleBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Tus últimas 4 semanas",
            style = ProgressLiftNameStyle,
            color = StructureInk,
        )
        Text(
            text = "Ver balance muscular ›",
            style = StructureCaptionStyle,
            color = SymbolPalette.musculo,
        )
    }
}

/**
 * Contenido del módulo «Progreso por ejercicio» dentro de Program Detail.
 * Muestra los ejercicios con tendencia/registros (con sparkline y delta de e1RM), los ejercicios planificados en el programa pendientes de
 * registro y el acceso directo a la vista dedicada completa de Progreso por ejercicio.
 */
@Composable
internal fun ProgramExercisesProgressBody(
    loggedRows: List<ProgressLiftRow>,
    allExercises: List<ProgramExerciseProgressSummary>,
    early: Boolean,
    onOpenLiftSheet: (ProgressLiftRow) -> Unit,
    onSeeAllLiftsSheet: () -> Unit,
    onOpenDedicatedProgress: (String?) -> Unit,
) {
    val unloggedExercises = remember(allExercises, loggedRows) {
        val loggedKeys = loggedRows.map { it.key }.toSet()
        allExercises.filter { !it.isLogged && it.key !in loggedKeys }
    }

    if (loggedRows.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Con sesiones registradas",
                style = StructureCaptionStyle,
                color = StructureMuted,
            )
            if (!early) {
                StructureAction(
                    text = "Todos",
                    onClick = onSeeAllLiftsSheet,
                    trailingIcon = KpknLineIcons.ChevronRight,
                    faint = true,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        loggedRows.forEach { row ->
            ProgressLiftItem(
                row = row,
                onClick = { onOpenLiftSheet(row) },
            )
        }
    }

    if (unloggedExercises.isNotEmpty()) {
        if (loggedRows.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = if (loggedRows.isEmpty()) "Ejercicios planificados en el programa" else "Planificados pendientes de registro",
            style = StructureCaptionStyle,
            color = StructureMuted,
        )
        Spacer(Modifier.height(6.dp))
        unloggedExercises.take(6).forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDedicatedProgress(item.key) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = ProgressLiftNameStyle,
                        color = StructureInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.subtitle,
                        style = StructureCaptionStyle,
                        color = StructureFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "Ver ficha ›",
                    style = StructureCaptionStyle,
                    color = SymbolPalette.musculo,
                )
            }
        }
    }

    if (loggedRows.isEmpty() && unloggedExercises.isEmpty()) {
        Text(
            text = "Añade ejercicios a las sesiones del programa o registra un entrenamiento para ver aquí su evolución.",
            style = StructureCaptionStyle,
            color = StructureFaint,
        )
    }

    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KpknSurface.inset)
            .border(1.dp, KpknSurface.moduleBorder, RoundedCornerShape(12.dp))
            .clickable { onOpenDedicatedProgress(loggedRows.firstOrNull()?.key ?: allExercises.firstOrNull()?.key) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Progreso detallado por ejercicio",
                style = ProgressLiftNameStyle,
                color = StructureInk,
            )
            Text(
                text = "Curvas de e1RM, tonelaje, RIR/RPE, cumplimiento e historial de series",
                style = StructureCaptionStyle,
                color = StructureFaint,
            )
        }
        Icon(
            imageVector = KpknLineIcons.ChevronRight,
            contentDescription = null,
            tint = SymbolPalette.musculo,
            modifier = Modifier.size(18.dp),
        )
    }
}
