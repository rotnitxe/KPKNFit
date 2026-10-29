package com.example.kpkn.screens.programdetail.design

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Program
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram

/**
 * «Editar programa» (lienzo v6, m-edit): nombre y descripción en campos `.field`, «Modalidad» en chips y «Portada»: la portada a la
 * izquierda; a la derecha, los cinco fondos en círculos y los ocho estilos de líneas en una rejilla de cuatro pintada con el fondo elegido.
 * Debajo, «Usar una foto» y «Guardar», que solo se activa con cambios y nombre. Nada se escribe hasta «Guardar».
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
    val context = LocalContext.current
    val baseSpec = remember(program) {
        coverIdentityForProgram(program).lines ?: LineCoverSpec(LineCoverStyle.PERFIL, LineCoverBackground.CARBON, seed = 3)
    }
    val preview = remember(program, draft.cover) { coverIdentityForProgram(program.copy(coverImage = draft.cover)) }
    // La foto propia queda guardada como `content://`: se pide el permiso persistente para que la portada siga ahí al reabrir la app.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onDraftChange(draft.withPhoto(uri.toString()))
        }
    }
    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(title = "Editar programa")
        LienzoTextField(
            label = "Nombre",
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Nombre del programa",
            error = draft.trimmedName.isEmpty(),
            tag = "editar_nombre",
        )
        LienzoTextField(
            label = "Descripción",
            value = draft.description,
            onValueChange = { onDraftChange(draft.copy(description = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Para qué es este programa",
            singleLine = false,
            tag = "editar_descripcion",
        )
        LienzoGroup("Modalidad")
        LienzoChipsWrap(
            options = editableProgramModes.map(::programModeLabel),
            selected = { draft.mode == editableProgramModes[it] },
            onSelect = { onDraftChange(draft.copy(mode = editableProgramModes[it])) },
            tagOf = { "editar_modalidad_${editableProgramModes[it].name}" },
        )
        LienzoGroup("Portada")
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgramPoster(
                identity = preview,
                photoUrl = draft.cover.takeIf { draft.hasPhoto },
                animate = false,
                width = 84.dp,
                height = 104.dp,
                radius = 18.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    LineCoverBackground.entries.forEach { background ->
                        BackgroundDot(
                            background = background,
                            selected = !draft.hasPhoto && draft.background == background,
                            onClick = { onDraftChange(draft.withBackground(background)) },
                        )
                    }
                }
                StyleGrid(
                    base = baseSpec,
                    background = draft.background,
                    selected = draft.lineStyle.takeUnless { draft.hasPhoto },
                    onSelect = { onDraftChange(draft.withStyle(it)) },
                )
            }
        }
        LienzoTextAction(
            text = "Usar una foto",
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            icon = LienzoEyeIcon,
            modifier = Modifier.padding(top = 8.dp),
        )
        ProgramLienzoPill(
            text = "Guardar",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            enabled = draft.canSave(program),
        )
    }
}

/** Un fondo (círculo de 26 dp partido en diagonal: el fondo y su acento). El elegido lleva un anillo de tinta de 2 dp a 2 dp. */
@Composable
private fun BackgroundDot(background: LineCoverBackground, selected: Boolean, onClick: () -> Unit) {
    val bg = Color(background.bg)
    val accent = Color(background.accent)
    val ring = WizardColors.text
    Box(
        modifier = Modifier
            .size(26.dp)
            .drawBehind {
                if (selected) {
                    drawCircle(color = ring, radius = size.minDimension / 2f + 3.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
                }
            }
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    0f to bg, 0.5f to bg, 0.5f to accent, 1f to accent,
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
                CircleShape,
            )
            .semantics {
                contentDescription = background.label
                this.selected = selected
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton, onClick = onClick),
    )
}

/** Los ocho estilos en cuatro columnas (6 dp entre ellas), 4:5 y radio de 8 dp; el elegido con un anillo de tinta de 2 dp a 1 dp. */
@Composable
private fun StyleGrid(base: LineCoverSpec, background: LineCoverBackground, selected: LineCoverStyle?, onSelect: (LineCoverStyle) -> Unit) {
    val ring = WizardColors.text
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        editableLineStyles.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { style ->
                    val on = style == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(4f / 5f)
                            .drawBehind {
                                if (on) {
                                    val gap = 2.dp.toPx()
                                    drawRoundRect(
                                        color = ring,
                                        topLeft = Offset(-gap, -gap),
                                        size = Size(size.width + gap * 2, size.height + gap * 2),
                                        cornerRadius = CornerRadius(10.dp.toPx()),
                                        style = Stroke(width = 2.dp.toPx()),
                                    )
                                }
                            }
                            .clip(RoundedCornerShape(8.dp))
                            .testTag("editar_estilo_${style.name}")
                            .semantics {
                                contentDescription = style.label
                                this.selected = on
                            }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) {
                                onSelect(style)
                            },
                    ) {
                        LineCoverArt(spec = base.copy(style = style, background = background, weeks = 6), modifier = Modifier.fillMaxSize())
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
