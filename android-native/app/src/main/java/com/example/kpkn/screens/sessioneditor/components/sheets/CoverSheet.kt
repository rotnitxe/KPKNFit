package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.LabelPosition
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionBackgroundType
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionCoverGradient
import com.example.kpkn.screens.sessioneditor.sessionCoverColors
import com.example.kpkn.screens.sessioneditor.sessionGradients
import com.example.kpkn.screens.sessioneditor.sessionSolidPresets
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknType

/** Muestras por fila de la rejilla: 5 de 48 dp con 12 dp de aire ocupan los 288 dp útiles de un móvil de 360 dp. */
private const val COVER_SAMPLES_PER_ROW = 5

/**
 * Hoja «Portada de la sesión» (SE7). Los parámetros de fondo, filtros y etiqueta que esta hoja ya no pinta se conservan: los pasa el
 * despachador y no cambian.
 */
@Suppress("UNUSED_PARAMETER")
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
    CoverSheetBody(session = session, onSelectGradient = onSelectGradient)
}

/**
 * El contenido de la portada sin la hoja: la vista previa (la franja de 4 dp y un bloque con la portada), las dos rejillas de muestras y,
 * si el fondo es una imagen local, la nota de que ya no se admiten. Elegir una muestra llama a [onSelectGradient] con su id.
 */
@Composable
internal fun CoverSheetBody(
    session: Session,
    onSelectGradient: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosenId = session.background?.value
    val previewColors = sessionCoverColors(session.background)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Portada de la sesión", style = KpknType.title, color = WizardColors.text)
        Text(
            text = "Un degradado o un color sólido para la sesión.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        CoverPreview(colors = previewColors, name = session.name.ifBlank { "Sesión" })
        KpknModule {
            KpknModuleHeader(label = "Degradados")
            CoverSampleGrid(samples = sessionGradients, kind = "Degradado", chosenId = chosenId, onSelect = onSelectGradient)
            KpknModuleHeader(label = "Sólidos")
            CoverSampleGrid(samples = sessionSolidPresets, kind = "Sólido", chosenId = chosenId, onSelect = onSelectGradient)
        }
        if (session.background?.type == SessionBackgroundType.IMAGE) {
            Text(
                text = "Los fondos con imagen local se desactivaron para esta versión. Usa color sólido o gradiente.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
    }
}

/** La franja de 4 dp que enseña la sesión y, debajo, un bloque con el mismo fondo y el nombre de la sesión. */
@Composable
private fun CoverPreview(colors: List<Color>, name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient(colors)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(KpknShape.module)
                .background(Brush.linearGradient(colors))
                .border(1.dp, WizardColors.glassBorder, KpknShape.module),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = name,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** Una rejilla de muestras redondas de 48 dp, de [COVER_SAMPLES_PER_ROW] por fila. */
@Composable
private fun CoverSampleGrid(
    samples: List<SessionCoverGradient>,
    kind: String,
    chosenId: String?,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        samples.chunked(COVER_SAMPLES_PER_ROW).forEach { rowSamples ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowSamples.forEach { sample ->
                    CoverSample(
                        sample = sample,
                        kind = kind,
                        selected = sample.id == chosenId,
                        onClick = { onSelect(sample.id) },
                    )
                }
            }
        }
    }
}

/**
 * Una muestra: círculo de 48 dp con el degradado o el sólido. La elegida lleva un anillo de tinta de 2 dp y un `Check`; las demás, un
 * filete neutro de 1 dp. Es un botón de selección (`Role.RadioButton`) con su nombre.
 */
@Composable
private fun CoverSample(
    sample: SessionCoverGradient,
    kind: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = "$kind ${sample.name}" }
            .clip(CircleShape)
            .background(Brush.linearGradient(sample.colors))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) WizardColors.text else WizardColors.glassBorder,
                shape = CircleShape,
            )
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = KpknLineIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = WizardColors.text,
            )
        }
    }
}
