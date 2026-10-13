package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.SessionMigrationMode
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknType

/** Una opción de «Contenido de las sesiones»: su modo, su título y su línea de ayuda. */
internal data class SplitModeOption(
    val mode: SessionMigrationMode,
    val title: String,
    val description: String,
)

/**
 * Elección de qué pasa con las sesiones (P5): filas de radio KPKN. La nota del motor («Se conservarán N sesiones…») va debajo de la
 * opción que la explica, en `caption`. Sustituye a las tarjetas `SplitModeCard`. Sin estado.
 */
@Composable
internal fun SplitModeRows(
    options: List<SplitModeOption>,
    selected: SessionMigrationMode?,
    onSelect: (SessionMigrationMode) -> Unit,
    note: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        options.forEach { option ->
            KpknRadioRow(
                selected = option.mode == selected,
                title = option.title,
                subtitle = option.description,
                onClick = { onSelect(option.mode) },
            )
            if (option.mode == selected && note != null) {
                Text(
                    text = note,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(start = 36.dp, bottom = 8.dp),
                )
            }
        }
    }
}
