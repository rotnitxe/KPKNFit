package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.exerciseCatalogMuscleLabel
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType

/**
 * Cabecera del catálogo (SE6a, SESSION_EDITOR_V1 §4). En la página completa ([onBack] no es nulo): «Atrás» con `KpknTopRow` y el título
 * «Añadir ejercicios» en `display` a 24 sp, que es lo que se encoge para caber en una línea (KPKN_UI_V2 §5). En la hoja: solo el título, en `title`. Debajo, el contexto de la parte si lo hay, el buscador
 * (`KpknTextField` con la lupa) y una fila desplazable de filtros: Todos, Tren superior, Tren inferior y Músculo.
 *
 * Con un músculo elegido, su filtro se ve activo con su valor («Pectorales»). Al tocar Músculo se despliega la fila de [muscleIds].
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
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            KpknTopRow(onBack = onBack)
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Añadir ejercicios",
                style = if (onBack != null) KpknType.display.copy(fontSize = 24.sp, lineHeight = 28.sp) else KpknType.title,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!targetGroupName.isNullOrBlank()) {
                Text(
                    text = "Agrega ejercicios para $targetGroupName",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        KpknTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Buscar ejercicio…",
            leadingIcon = KpknLineIcons.Search,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
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
