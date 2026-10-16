package com.example.kpkn.screens.technique.trends.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.technique.trends.AnalyzedVideoItemUi
import com.example.kpkn.ui.components.LocalMediaImage
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rememberLocalMediaImageSource
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Fila de un vídeo analizado en la lista del módulo «Vídeos».
 * Muestra miniatura, fecha, carga/reps reales de la serie, reps detectadas por el analizador
 * y confianza del recuento.
 */
@Composable
fun AnalyzedVideoRow(
    item: AnalyzedVideoItemUi,
    isCompareMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = buildString {
        append("Vídeo del ${item.dateLabel}. ")
        item.loadAndReps?.let { append("$it. ") }
        append("Detectadas ${item.detectedReps}.")
        if (isCompareMode) {
            if (item.isSelectedForCompare) append(" Seleccionado para comparar.")
            else append(" No seleccionado.")
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Indicador de selección de comparación o miniatura
        if (isCompareMode) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (item.isSelectedForCompare) Color.White else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (item.isSelectedForCompare) Color.White else WizardColors.textMuted,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (item.isSelectedForCompare) {
                    Icon(
                        imageVector = KpknLineIcons.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Miniatura del vídeo
        val thumbSource = rememberLocalMediaImageSource(item.thumbFile)
        Box(
            modifier = Modifier
                .size(42.dp, 56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(KpknSurface.inset),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbSource != null) {
                LocalMediaImage(
                    source = thumbSource,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = KpknLineIcons.Camera,
                    contentDescription = null,
                    tint = WizardColors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Información de la serie y fecha
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.dateLabel,
                    style = KpknType.bodyStrong,
                    color = Color.White,
                )
                item.loadAndReps?.let { lr ->
                    Text(
                        text = "· $lr",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.detectedReps,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                // Insignia de confianza si no es alta
                when (item.confidence) {
                    CountConfidence.MEDIA -> {
                        Text(
                            text = "· Revisar",
                            style = KpknType.caption,
                            color = SymbolPalette.energia,
                        )
                    }
                    CountConfidence.BAJA -> {
                        Text(
                            text = "· No concluyente",
                            style = KpknType.caption,
                            color = WizardColors.danger,
                        )
                    }
                    else -> Unit
                }
            }
        }

        if (!isCompareMode) {
            Icon(
                imageVector = KpknLineIcons.ChevronRight,
                contentDescription = null,
                tint = WizardColors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
