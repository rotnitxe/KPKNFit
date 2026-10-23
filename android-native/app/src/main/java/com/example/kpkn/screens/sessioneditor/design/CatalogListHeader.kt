package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.exerciseCatalogMuscleLabel
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType

/** Etiqueta de prueba de la línea de contexto bajo el título del catálogo (LV5). */
internal const val CatalogHeaderContextTag = "catalog_header_context"

/** El título describe la acción del origen: añadir, reemplazar, elegir para Técnica o ampliar una superserie. */
internal fun catalogHeaderTitle(
    isReplacement: Boolean,
    isSingleSelection: Boolean = false,
    isSupersetAddMode: Boolean = false,
): String = when {
    isReplacement -> "Reemplazar"
    isSingleSelection -> "Elegir ejercicio"
    isSupersetAddMode -> "Añadir a la superserie"
    else -> "Añadir ejercicios"
}

/**
 * Al reemplazar, la tarjeta del ejercicio ocupa el contexto; al añadir se nombra la parte de la sesión.
 */
internal fun catalogHeaderContextLine(
    isReplacement: Boolean,
    targetGroupName: String?,
): String? = when {
    isReplacement -> null
    !targetGroupName.isNullOrBlank() -> "Añade ejercicios para $targetGroupName"
    else -> null
}

/**
 * Cabecera del catálogo (SE6a, SESSION_EDITOR_V1 §4). En la página completa ([onBack] no es nulo): «Atrás» con `KpknTopRow` y el título
 * «Añadir ejercicios» en `display` a 24 sp, que es lo que se encoge para caber en una línea (KPKN_UI_V2 §5). En la hoja: solo el título, en `title`. Debajo, el contexto de la parte si lo hay, el buscador
 * (`KpknTextField` con la lupa) y una fila desplazable de filtros: Todos, Tren superior, Tren inferior y Músculo.
 *
 * Con un músculo elegido, su filtro se ve activo con su valor («Pectorales»). Al tocar Músculo se despliega la fila de [muscleIds].
 * Al reemplazar un ejercicio ([isReplacement], H1) el título pasa a «Reemplazar», con la flecha de volver a su izquierda en la misma
 * fila (en la página completa); bajo él va la tarjeta fija «Reemplazando»
 * ([replacingCard]), el buscador «Buscar otro ejercicio» y solo dos filtros: «Mismo músculo» ([onToggleSameMuscle], que usa el filtro de
 * músculo de siempre con el músculo principal del ejercicio actual; sin él, no sale) y «Todos». Los filtros de región y de músculo
 * sueltos no salen al reemplazar.
 * Sin estado: quien la usa guarda los filtros y la búsqueda.
 */
@Composable
internal fun CatalogListHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: (() -> Unit)?,
    targetGroupName: String?,
    filterRegion: String?,
    filterMuscle: String?,
    muscleFilterExpanded: Boolean,
    muscleIds: List<String>,
    onClearFilters: () -> Unit,
    onToggleUpper: () -> Unit,
    onToggleLower: () -> Unit,
    onToggleMuscle: () -> Unit,
    onToggleMuscleId: (String) -> Unit,
    modifier: Modifier = Modifier,
    isReplacement: Boolean = false,
    replacingCard: CatalogReplacingCardModel? = null,
    sameMuscleSelected: Boolean = false,
    onToggleSameMuscle: (() -> Unit)? = null,
    isSingleSelection: Boolean = false,
    isSupersetAddMode: Boolean = false,
) {
    val title = catalogHeaderTitle(isReplacement, isSingleSelection, isSupersetAddMode)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isReplacement && onBack != null) {
            // Al reemplazar en la página completa (H1) la flecha de volver y el título van en la misma fila.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = onBack)
                Text(
                    text = title,
                    style = KpknType.display.copy(fontSize = 24.sp, lineHeight = 28.sp),
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        } else {
            if (onBack != null) {
                KpknTopRow(onBack = onBack)
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = if (onBack != null) KpknType.display.copy(fontSize = 24.sp, lineHeight = 28.sp) else KpknType.title,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val contextLine = catalogHeaderContextLine(isReplacement || isSingleSelection, targetGroupName)
                if (contextLine != null) {
                    Text(
                        text = contextLine,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(CatalogHeaderContextTag),
                    )
                }
            }
        }
        if (isReplacement && replacingCard != null) {
            CatalogReplacingCard(model = replacingCard, modifier = Modifier.padding(horizontal = 16.dp))
        }
        KpknTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = if (isReplacement) "Buscar otro ejercicio" else "Buscar ejercicio…",
            leadingIcon = KpknLineIcons.Search,
            trailing = if (query.isNotEmpty()) {
                {
                    KpknIconButton(
                        icon = KpknLineIcons.Close,
                        contentDescription = "Borrar búsqueda",
                        onClick = { onQueryChange("") },
                    )
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        // El chip de músculo es el último de la fila: con un músculo elegido (o con su selector abierto), la fila se desplaza hasta el
        // final para que «Pectorales» se lea entero y no quede cortado en el borde (SE6b).
        val chipRowScroll = rememberScrollState()
        LaunchedEffect(filterMuscle, muscleFilterExpanded) {
            if (filterMuscle != null || muscleFilterExpanded) {
                withFrameNanos { }
                chipRowScroll.animateScrollTo(chipRowScroll.maxValue)
            }
        }
        if (isReplacement) {
            // Al reemplazar (H1): «Mismo músculo» (el filtro de músculo de siempre, ya puesto) y «Todos». El filtro «Mismo patrón» del
            // diseño no existe en el catálogo y no sale; el orden de la lista ya empieza por los del mismo patrón.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onToggleSameMuscle != null) {
                    KpknChip(
                        text = "Mismo músculo",
                        selected = sameMuscleSelected,
                        onClick = onToggleSameMuscle,
                    )
                }
                KpknChip(
                    text = "Todos",
                    selected = filterRegion == null && filterMuscle == null,
                    onClick = onClearFilters,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(chipRowScroll)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KpknChip(
                    text = "Todos",
                    selected = filterRegion == null && filterMuscle == null,
                    onClick = onClearFilters,
                )
                KpknChip(
                    text = "Tren superior",
                    selected = filterRegion == "UPPER",
                    onClick = onToggleUpper,
                )
                KpknChip(
                    text = "Tren inferior",
                    selected = filterRegion == "LOWER",
                    onClick = onToggleLower,
                )
                KpknChip(
                    text = filterMuscle?.let(::exerciseCatalogMuscleLabel) ?: "Músculo",
                    selected = muscleFilterExpanded || filterMuscle != null,
                    onClick = onToggleMuscle,
                )
            }
            if (muscleFilterExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    muscleIds.forEach { muscleId ->
                        KpknChip(
                            text = exerciseCatalogMuscleLabel(muscleId),
                            selected = filterMuscle == muscleId,
                            onClick = { onToggleMuscleId(muscleId) },
                        )
                    }
                }
            }
        }
    }
}
