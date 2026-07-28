package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.Session
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.SheetHeader
import com.example.kpkn.screens.sessioneditor.sessionGradients
import com.example.kpkn.screens.sessioneditor.sessionSolidPresets
import com.example.kpkn.ui.components.KpknSheetLightChip
import com.example.kpkn.ui.components.KpknSheetTokens
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
internal fun CoverSheet(
    session: Session,
    onPickImage: () -> Unit,
    onSelectGradient: (String) -> Unit,
    onBackgroundBlurChange: (Float) -> Unit,
    onBackgroundBrightnessChange: (Float) -> Unit,
    onCoverBrightnessChange: (Float) -> Unit,
    onCoverContrastChange: (Float) -> Unit,
    onCoverSaturationChange: (Float) -> Unit,
    onCoverGrayscaleChange: (Float) -> Unit,
    onCoverVignetteChange: (Float) -> Unit,
    onCoverMotionChange: (Boolean) -> Unit,
    onLabelPositionChange: (LabelPosition) -> Unit,
) {
    val blur = session.background?.style?.blur ?: 0f
    val brightness = session.background?.style?.brightness ?: 0.92f
    val coverContrast = session.coverStyle?.filters?.contrast ?: 1f
    val coverSaturation = session.coverStyle?.filters?.saturation ?: 1f
    val coverGrayscale = session.coverStyle?.filters?.grayscale ?: 0f
    val coverVignette = session.coverStyle?.filters?.vignette ?: 0f
    val coverMotion = session.coverStyle?.enableMotion ?: false
    val coverBrightness = session.coverStyle?.filters?.brightness ?: 1f
    val isImageBackground = session.background?.type == SessionBackgroundType.IMAGE
    var coverTab by rememberSaveable { mutableStateOf(if (session.background?.value?.startsWith("solid://") == true) "solid" else "gradient") }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetHeader("Portada de sesión", "Elige un fondo y ajusta solo lo que corresponde a ese tipo.")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpknSheetLightChip(
                label = "GRADIENTES",
                selected = coverTab == "gradient",
                modifier = Modifier.weight(1f),
                onClick = { coverTab = "gradient" },
            )
            KpknSheetLightChip(
                label = "SÓLIDOS",
                selected = coverTab == "solid",
                modifier = Modifier.weight(1f),
                onClick = { coverTab = "solid" },
            )
        }

        if (coverTab == "gradient") {
            Text("Gradientes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                sessionGradients.forEach { gradient ->
                Box(
                    modifier = Modifier
                        .size(width = 92.dp, height = 74.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(gradient.colors))
                        .border(
                            width = if (session.background?.value == gradient.id) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(20.dp),
                        )
                        .clickable { onSelectGradient(gradient.id) }
                )
            }
            }
        }
        if (coverTab == "solid") {
            Text("Colores sólidos", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                sessionSolidPresets.forEach { solid ->
                Box(
                    modifier = Modifier
                        .size(width = 92.dp, height = 52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(solid.colors.first())
                        .border(
                            width = if (session.background?.value == solid.id) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(14.dp),
                        )
                        .clickable { onSelectGradient(solid.id) }
                )
            }
            }
        }
        if (isImageBackground) {
            Text(
                "Los fondos con imagen local se desactivaron para esta versión. Usa color sólido o gradiente.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun CoverTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(999.dp),
        color = if (selected) KpknSheetTokens.ChipSelected else KpknSheetTokens.ChipIdle,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = KpknSheetTokens.ChipLabel,
            )
        }
    }
}
