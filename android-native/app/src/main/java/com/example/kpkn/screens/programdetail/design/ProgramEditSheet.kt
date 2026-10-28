package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kpkn.data.models.Program
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverIdentity
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverPaletteKind
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.restyled
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * «Editar programa» (KPKN_UI_V2, P1): nombre, descripción y modalidad con «Guardar», y el estudio de portada. Nada se escribe
 * hasta «Guardar», que queda deshabilitado sin cambios. El estudio muestra los diez estilos en miniatura y la paleta, con una
 * vista previa en vivo. Con foto propia, la foto manda hasta «Usar portada KPKN».
 *
 * Sin estado: el borrador ([draft]) y sus cambios viven en la pantalla.
 */
@Composable
internal fun ProgramEditSheet(
    program: Program,
    draft: ProgramEditDraft,
    onDraftChange: (ProgramEditDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    // La identidad de siempre: estilo y paleta de estudio se aplican sobre sus acentos y su semilla.
    val base = remember(program.id, program.structureTemplateId) { coverIdentityFor(program.id, program.structureTemplateId) }
    val preview = remember(base, draft.style, draft.palette) { base.restyled(draft.style, draft.palette) }
    val photoUrl = draft.cover.takeIf { draft.hasPhoto }
    KpknSheet(onDismissRequest = onDismiss, stableHeightFraction = 0.9f) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(text = "Editar programa", style = KpknType.title, color = WizardColors.text)
                Spacer(Modifier.height(16.dp))
                KpknTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    label = "Nombre",
                    placeholder = "Nombre del programa",
                    singleLine = true,
                    error = if (draft.trimmedName.isEmpty()) "Pon un nombre al programa" else null,
                )
                Spacer(Modifier.height(14.dp))
                KpknTextField(
                    value = draft.description,
                    onValueChange = { onDraftChange(draft.copy(description = it)) },
                    label = "Descripción",
                    placeholder = "Para qué es este programa",
                    singleLine = false,
                    minLines = 2,
                )
                Spacer(Modifier.height(24.dp))
                Text(text = "MODALIDAD", style = KpknType.eyebrow, color = WizardColors.textMuted)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Orienta el volumen y las cargas que KPKN propone para este programa.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    editableProgramModes.forEach { modo ->
                        KpknChip(
                            text = programModeLabel(modo),
                            selected = draft.mode == modo,
                            onClick = { onDraftChange(draft.copy(mode = modo)) },
                            modifier = Modifier.testTag("editar_modalidad_${modo.name}"),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(text = "PORTADA", style = KpknType.eyebrow, color = WizardColors.textMuted)
                Spacer(Modifier.height(12.dp))
                // La vista previa y la paleta comparten fila: así la rejilla de estilos cabe antes del corte del scroll.
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CoverPreview(identity = preview, photoUrl = photoUrl)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Paleta", style = KpknType.caption, color = WizardColors.textMuted)
                        Spacer(Modifier.height(8.dp))
                        KpknSegmentedControl(
                            options = CoverPaletteKind.entries.map(::coverPaletteLabel),
                            selectedIndex = draft.palette.ordinal,
                            onSelect = { index -> onDraftChange(draft.withPalette(CoverPaletteKind.entries[index])) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (draft.hasPhoto) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Tu foto manda. «Usar portada KPKN» la cambia por la portada de estudio elegida.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    KpknSecondaryPill(
                        text = "Usar portada KPKN",
                        onClick = { onDraftChange(draft.usingStudio()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(18.dp))
                StudioGrid(
                    base = base,
                    palette = draft.palette,
                    selected = draft.style,
                    onSelect = { style -> onDraftChange(draft.withStyle(style)) },
                )
                Spacer(Modifier.height(16.dp))
            }
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                KpknPrimaryPill(
                    text = "Guardar",
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = draft.canSave(program),
                )
            }
        }
    }
}

/** Vista previa de la portada: 96 × 128 dp, con la foto propia si la hay. */
@Composable
private fun CoverPreview(identity: CoverIdentity, photoUrl: String?) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 128.dp)
            .clip(shape)
            .background(KpknSurface.inset, shape)
            .border(1.dp, KpknSurface.moduleBorder, shape),
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Portada del programa",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            CoverStudioArt(identity = identity, modifier = Modifier.fillMaxSize(), animate = false, reduced = true)
        }
    }
}

/** Los diez estilos del estudio en miniaturas de 64 × 84 dp, cuatro por fila, con su nombre debajo. */
@Composable
private fun StudioGrid(
    base: CoverIdentity,
    palette: CoverPaletteKind,
    selected: CoverStudioStyle,
    onSelect: (CoverStudioStyle) -> Unit,
) {
    CoverStudioStyle.entries.chunked(4).forEachIndexed { row, styles ->
        if (row > 0) Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            styles.forEach { style ->
                StudioThumb(
                    label = coverStyleLabel(style),
                    identity = thumbnailIdentity(base.restyled(style, palette)),
                    selected = style == selected,
                    onClick = { onSelect(style) },
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(4 - styles.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * En la miniatura de 64 dp, el rótulo «SEMANA» del numeral no cabe: se enseña solo la cifra. La portada real sí lo lleva.
 */
private fun thumbnailIdentity(identity: CoverIdentity): CoverIdentity =
    if (identity.style == CoverStudioStyle.NUMERAL) identity.copy(numeralIsWeeks = false) else identity

/** Una miniatura del estudio: arte estático, anillo de tinta si está elegida y el nombre del estilo. */
@Composable
private fun StudioThumb(
    label: String,
    identity: CoverIdentity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier.selectable(
            selected = selected,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.RadioButton,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // La elección lleva un anillo de tinta a 3 dp de la miniatura: el crema de la portada no se confunde con él.
        Box(
            modifier = Modifier
                .size(width = 70.dp, height = 90.dp)
                .then(
                    if (selected) Modifier.border(2.dp, WizardColors.text, RoundedCornerShape(20.dp)) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 84.dp)
                    .clip(shape)
                    .border(width = 1.dp, color = KpknSurface.moduleBorder, shape = shape),
            ) {
                CoverStudioArt(identity = identity, modifier = Modifier.fillMaxSize(), animate = false, reduced = true)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = KpknType.caption,
            color = if (selected) WizardColors.text else WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
