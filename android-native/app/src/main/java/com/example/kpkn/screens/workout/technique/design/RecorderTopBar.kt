package com.example.kpkn.screens.workout.technique.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton

/**
 * Barra superior del grabador de técnica (VT8a).
 *
 * Muestra el botón de cierre, el nombre del ejercicio y la etiqueta de la serie,
 * más las insignias de «60 fps» (si se concedió) y «Disco 45 cm» con sus chips desplegables.
 */
@Composable
fun RecorderTopBar(
    exerciseName: String,
    setLabel: String,
    is60FpsGranted: Boolean,
    showsPlateBadge: Boolean,
    plateDiameterMm: Int?,
    plateBadgeExpanded: Boolean,
    onTogglePlateExpanded: () -> Unit,
    onSelectPlateDiameterMm: (Int?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onCustomPlateSelected: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpknIconButton(
                icon = KpknLineIcons.Close,
                contentDescription = "Cerrar grabador",
                onClick = onClose,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = exerciseName,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (setLabel.isNotBlank()) {
                    Text(
                        text = setLabel,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 1,
                    )
                }
            }

        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (is60FpsGranted) {
                FpsBadge(fps = 60)
            }
            if (showsPlateBadge) {
                PlateDiameterBadge(
                    diameterMm = plateDiameterMm,
                    expanded = plateBadgeExpanded,
                    onToggleExpanded = onTogglePlateExpanded,
                    onSelectDiameterMm = onSelectPlateDiameterMm,
                    onCustomSelected = onCustomPlateSelected,
                )
            }
        }
    }
}
