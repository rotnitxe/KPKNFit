package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.domain.exercises.exerciseDisplayParts
import com.example.kpkn.domain.templates.RingBudgetPolicy
import com.example.kpkn.domain.templates.SessionTemplateCatalogPolicy
import com.example.kpkn.domain.templates.SessionTemplateFacets
import com.example.kpkn.domain.templates.SessionTemplateFacetsBuilder
import com.example.kpkn.domain.templates.TemplateCatalogFilterLogic
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatEditorOneDecimal
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Texto de presentación: el «Full body» del dominio se ve como «Cuerpo completo». Los ids y las etiquetas del dominio no cambian. */
internal fun templateCatalogText(text: String): String = text.replace("Full body", "Cuerpo completo")

/**
 * «6 ejercicios · 22 series», en singular o plural. El número va pegado a su unidad con un espacio que no se parte: en una línea estrecha no
 * queda «10» al final de una línea y «series» al principio de la siguiente.
 */
internal fun templateCountsLine(exercises: Int, sets: Int): String =
    "$exercises ${if (exercises == 1) "ejercicio" else "ejercicios"} · $sets ${if (sets == 1) "serie" else "series"}"

/** «1 plantilla» o «N plantillas», para la cabecera de cada grupo del catálogo. */
internal fun templatesCountText(count: Int): String = "$count ${if (count == 1) "plantilla" else "plantillas"}"

/** Acciones de gestión de una plantilla propia. Sin ellas, la fila no lleva el menú «⋯». */
internal class UserTemplateActions(
    val archived: Boolean,
    val onEdit: () -> Unit,
    val onArchiveOrRestore: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * Facetas de una plantilla (fatiga y cifras), calculadas en segundo plano. Son nulas mientras se calculan o si el cálculo falla: la
 * primera pintura no espera a AUGE.
 */
@Composable
internal fun rememberTemplateFacets(
    template: SessionTemplate,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
): SessionTemplateFacets? {
    val facets by produceState<SessionTemplateFacets?>(initialValue = null, template.id, exerciseIndex.size) {
        value = try {
            withContext(Dispatchers.Default) { SessionTemplateFacetsBuilder.build(template, exerciseIndex) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }
    return facets
}

/**
 * Fila de una plantilla dentro de su módulo. Arriba, el nombre con «Aplicar» y el menú «⋯» (en las plantillas propias). Debajo, las cifras
 * con su flecha, y la fatiga en barras a todo el ancho. Tocar el nombre o las cifras despliega sus ejercicios, dentro del mismo módulo.
 */
@Composable
internal fun CatalogTemplateRow(
    template: SessionTemplate,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    onApply: (() -> Unit)?,
    manage: UserTemplateActions?,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(template.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
    val facets = rememberTemplateFacets(template, exerciseIndex)
    val name = templateCatalogText(template.name)
    val exercises = template.session.allExercises()
    val sets = facets?.totalSets ?: exercises.sumOf { it.sets.size }
    val minutes = facets?.realDurationMinutes ?: template.estimatedDurationMinutes
    val archived = manage?.archived == true
    val meta = listOfNotNull(
        if (archived) "Archivada" else null,
        templateCountsLine(exercises.size, sets),
        minutes?.let { "≈ $it min" },
    ).joinToString(" · ")
    val toggleLabel = if (expanded) "Ocultar ejercicios" else "Ver ejercicios"

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = toggleLabel,
                        onClick = { expanded = !expanded },
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = name,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onApply != null) {
                KpknTextAction(
                    text = "Aplicar",
                    onClick = onApply,
                    modifier = Modifier.semantics { contentDescription = "Aplicar plantilla $name" },
                )
            }
            if (manage != null) {
                KpknIconButton(
                    icon = KpknLineIcons.More,
                    contentDescription = "Más opciones de $name",
                    onClick = { menuOpen = true },
                    modifier = Modifier.onGloballyPositioned { menuAnchor = it.boundsInWindow() },
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = toggleLabel,
                    onClick = { expanded = !expanded },
                )
                .padding(bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = meta,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = KpknLineIcons.ChevronDown,
                    contentDescription = null,
                    tint = WizardColors.textFaint,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = if (expanded) 180f else 0f },
                )
            }
            Spacer(Modifier.height(10.dp))
            TemplateFatigueBars(drain = facets?.drain, showValues = false)
        }
        AnimatedVisibility(visible = expanded) {
            TemplateExpandedDetails(
                template = template,
                exerciseIndex = exerciseIndex,
                facets = facets,
            )
        }
    }

    val anchor = menuAnchor
    if (manage != null && menuOpen && anchor != null) {
        KpknContextMenu(anchor = anchor, rows = 3, onDismiss = { menuOpen = false }) {
            KpknContextMenuRow(
                label = "Editar datos",
                onClick = {
                    menuOpen = false
                    manage.onEdit()
                },
            )
            KpknContextMenuRow(
                label = if (manage.archived) "Restaurar" else "Archivar",
                onClick = {
                    menuOpen = false
                    manage.onArchiveOrRestore()
                },
            )
            KpknContextMenuRow(
                label = "Eliminar",
                destructive = true,
                onClick = {
                    menuOpen = false
                    manage.onDelete()
                },
            )
        }
    }
}

/**
 * Fatiga estimada en tres barras finas, una por canal (músculos, energía y columna) con los colores de los anillos. Con [showValues],
 * cada barra lleva su cifra «≈». Mientras no hay dato, las barras van vacías: nunca una cifra inventada.
 */
@Composable
internal fun TemplateFatigueBars(
    drain: PredictedDrain?,
    showValues: Boolean,
    modifier: Modifier = Modifier,
) {
    val channels = listOf(
        Triple("Músculos", drain?.muscular, KpknRingColors.musculos),
        Triple("Energía", drain?.cns, KpknRingColors.energia),
        Triple("Columna", drain?.spinal, KpknRingColors.columna),
    )
    val summary = if (drain == null) {
        "Calculando la fatiga estimada"
    } else {
        "Fatiga estimada: " + channels.joinToString(", ") { (label, value, _) -> "$label ${value ?: 0} por ciento" }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        channels.forEach { (label, value, color) ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (showValues && value != null) "$label ≈$value%" else label,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(KpknSurface.inset),
                ) {
                    if (value != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(value.coerceIn(0, 100) / 100f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lo que se ve al desplegar una plantilla, dentro de su módulo: nivel, descripción, ejercicios con sus series, volumen por músculo y la
 * fatiga estimada con cifras. Las facetas llegan de la fila; mientras no, la fatiga dice que se calcula.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TemplateExpandedDetails(
    template: SessionTemplate,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    facets: SessionTemplateFacets?,
) {
    val estimatedVolume by produceState(initialValue = emptyMap<String, Double>(), template.id, exerciseIndex.size) {
        value = try {
            withContext(Dispatchers.Default) {
                SessionTemplateCatalogPolicy.calculateSessionMuscleVolume(template.session, exerciseIndex)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyMap()
        }
    }
    val drain = facets?.drain
    val warnings = remember(template, drain) {
        if (drain == null) {
            emptyList()
        } else {
            val list = mutableListOf<String>()
            val isPl = SessionTemplateCatalogPolicy.isPowerliftingTemplate(template)
            val caps = RingBudgetPolicy.sessionWarningCaps(isPl)
            if (drain.cns > caps.cns) list += "SNC elevada (${drain.cns}% > ${caps.cns}%)"
            if (drain.muscular > caps.muscular) list += "Muscular elevada (${drain.muscular}% > ${caps.muscular}%)"
            if (drain.spinal > caps.spinal) list += "Axial/espinal elevada (${drain.spinal}% > ${caps.spinal}%)"
            list
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Nivel: ${TemplateCatalogFilterLogic.difficultyLabel(template.difficulty)}",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        if (template.description.isNotBlank()) {
            Text(
                text = templateCatalogText(template.description),
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Ejercicios incluidos", style = KpknType.caption, color = WizardColors.textMuted)
            template.session.allExercises().forEachIndexed { idx, ex ->
                val catalogInfo = ex.catalogConfigurationId
                    ?.trim()
                    ?.lowercase()
                    ?.let { exerciseIndex[it] }
                val nameParts = exerciseDisplayParts(ex, catalogInfo)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${idx + 1}. ${nameParts.text}",
                            style = KpknType.caption,
                            color = WizardColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Chips SOLO del catálogo (variantChips + técnica de receta vía exerciseDisplayParts). Sin parseo de id.
                        val chips = nameParts.chips.ifEmpty {
                            catalogInfo?.catalogVariantChips.orEmpty()
                        }
                        if (chips.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                chips.forEach { chip -> InfoPill(text = chip) }
                            }
                        }
                    }
                    val setsCount = ex.sets.size
                    Text(
                        text = "$setsCount ${if (setsCount == 1) "serie" else "series"}",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }
        }

        if (estimatedVolume.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Volumen estimado por músculo", style = KpknType.caption, color = WizardColors.textMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    estimatedVolume.entries.sortedByDescending { it.value }.forEach { (muscle, sets) ->
                        InfoPill(text = "$muscle: ${formatEditorOneDecimal(sets)} series")
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Fatiga estimada (AUGE)", style = KpknType.caption, color = WizardColors.textMuted)
            if (facets == null) {
                Text(text = "Calculando detalles…", style = KpknType.caption, color = WizardColors.textMuted)
            } else {
                TemplateFatigueBars(drain = facets.drain, showValues = true)
            }
            facets?.averageTargetRpe?.let { rpe ->
                Text(
                    text = "RPE medio objetivo: ${formatEditorOneDecimal(rpe)}",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            facets?.primaryMuscles?.takeIf { it.isNotEmpty() }?.let { muscles ->
                Text(
                    text = "Enfoque: ${muscles.sortedBy { it.lowercase() }.joinToString(" · ")}",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            warnings.forEach { warning ->
                Text(text = warning, style = KpknType.caption, color = WizardColors.danger)
            }
        }
    }
}

/** Cifra o etiqueta de solo lectura dentro de un módulo: una píldora `inset` con texto en `caption`. */
@Composable
private fun InfoPill(text: String) {
    Box(
        modifier = Modifier
            .clip(KpknShape.pill)
            .background(KpknSurface.inset)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = text, style = KpknType.caption, color = WizardColors.textMuted)
    }
}
