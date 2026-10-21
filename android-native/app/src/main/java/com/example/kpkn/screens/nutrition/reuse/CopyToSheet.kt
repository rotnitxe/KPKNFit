package com.example.kpkn.screens.nutrition.reuse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.MealReuse
import com.example.kpkn.domain.nutrition.MealReuse.CopyPreset
import com.example.kpkn.domain.nutrition.MealReuse.DateSelection
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate

/** Los atajos de la hoja y su rótulo, en el orden en que se ofrecen. */
internal val CopyPresetLabels: List<Pair<CopyPreset, String>> = listOf(
    CopyPreset.TODAY to "Hoy",
    CopyPreset.TOMORROW to "Mañana",
    CopyPreset.NEXT_3_DAYS to "Próximos 3 días",
    CopyPreset.NEXT_7_DAYS to "Próximos 7 días",
)

/**
 * La hoja «Copiar a…» (una comida) o «Copiar día a…» (todas las del día): con su propio estado de fechas. Mañana viene elegido; los
 * atajos y el calendario cambian la selección; «Copiar» entrega las fechas ordenadas a [onConfirm]. Para que una copia a 3 fechas lleve
 * 3 interacciones: abrir la hoja, «Próximos 3 días» y «Copiar».
 */
@Composable
fun CopyToSheet(
    title: String,
    subtitle: String,
    sources: List<NutritionLog>,
    today: LocalDate,
    blocked: Set<LocalDate>,
    busy: Boolean,
    onConfirm: (List<LocalDate>) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
) {
    var selection by remember { mutableStateOf(MealReuse.initialSelection(today, blocked)) }
    KpknSheet(onDismissRequest = onDismiss, hazeState = hazeState) {
        CopyToContent(
            title = title,
            subtitle = subtitle,
            sources = sources,
            selection = selection,
            today = today,
            blocked = blocked,
            busy = busy,
            onToggleDate = { selection = MealReuse.toggleDate(selection, it, today, blocked) },
            onTogglePreset = { selection = MealReuse.togglePreset(selection, it, today, blocked) },
            onConfirm = { onConfirm(selection.sorted) },
        )
    }
}

/** El contenido de la hoja, sin estado: la hoja de render y la vista previa lo pintan con selecciones de ejemplo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CopyToContent(
    title: String,
    subtitle: String,
    sources: List<NutritionLog>,
    selection: DateSelection,
    today: LocalDate,
    blocked: Set<LocalDate>,
    busy: Boolean,
    onToggleDate: (LocalDate) -> Unit,
    onTogglePreset: (CopyPreset) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).testTag("copy-to-root")) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text(title, style = KpknType.title, color = WizardColors.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = KpknType.caption, color = WizardColors.textMuted)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CopyPresetLabels.forEach { (preset, label) ->
                    KpknChip(
                        text = label,
                        selected = MealReuse.isPresetActive(selection, preset, today, blocked),
                        onClick = { onTogglePreset(preset) },
                        modifier = Modifier.testTag("copy-preset-${preset.name}"),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ReuseDatesCalendar(
                selected = selection.dates,
                onToggle = onToggleDate,
                today = today,
                minDate = today.minusDays(MealReuse.DATE_WINDOW_DAYS),
                maxDate = today.plusDays(MealReuse.DATE_WINDOW_DAYS),
                blocked = blocked,
            )
            selection.notice?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = KpknType.caption, color = SymbolPalette.energia, modifier = Modifier.testTag("copy-notice"))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                MealReuse.copyPreview(sources, selection.dates.size),
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.testTag("copy-preview"),
            )
            if (selection.dates.any { it.isAfter(today) }) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Las fechas futuras quedan planificadas: no cuentan como lo que comiste.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        KpknPrimaryPill(
            text = if (busy) "Copiando…" else "Copiar",
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            tag = "copy-confirm",
            icon = KpknLineIcons.Check,
            enabled = !busy && selection.dates.isNotEmpty(),
        )
    }
}
