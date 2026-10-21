package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/**
 * Molestias del programa (P4): filas con el icono de información en `danger`, sin contenedor. Primero las molestias por nombre con sus
 * veces; después, las que se repiten con un ejercicio concreto. Cada bloque lleva su etiqueta de sección en mayúsculas.
 */
@Composable
fun ProgramVolumeDiscomfortRows(
    discomforts: List<ProgramVolumeDiscomfort>,
    associations: List<ProgramVolumeAssociation>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (discomforts.isNotEmpty()) {
            DiscomfortLabel("Molestias recientes")
            discomforts.forEach { item ->
                DiscomfortRow(title = item.name, detail = null, count = item.count)
            }
        }
        if (associations.isNotEmpty()) {
            DiscomfortLabel("Molestias por ejercicio")
            associations.forEach { item ->
                DiscomfortRow(title = item.exercise, detail = item.discomfort, count = item.count)
            }
        }
    }
}

@Composable
private fun DiscomfortLabel(text: String) {
    Text(
        text = text.uppercase(Locale("es")),
        style = KpknType.eyebrow,
        color = WizardColors.textMuted,
    )
}

@Composable
private fun DiscomfortRow(title: String, detail: String?, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = KpknLineIcons.Info,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = WizardColors.danger,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "$count ${if (count == 1) "vez" else "veces"}",
            style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.textMuted,
            maxLines = 1,
        )
    }
}
