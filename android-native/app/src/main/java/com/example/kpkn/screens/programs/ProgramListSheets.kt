package com.example.kpkn.screens.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Program
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.programdetail.design.ProgramShareIcon
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Qué borrar y cómo: «Eliminar» del ⋯ (deleteProgram) o la papelera de Archivados (permanentlyDeleteProgram). */
internal data class ProgramDeleteRequest(val program: Program, val permanent: Boolean)

/** Una fila de la hoja ⋯: icono en círculo, título y, si hay, la línea que explica la acción. */
private data class ProgramMenuEntry(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val danger: Boolean,
    val onClick: () -> Unit,
)

private val SheetTitleStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 21.sp,
    lineHeight = 25.sp,
    letterSpacing = (-0.4).sp,
)

/**
 * La hoja ⋯ de un programa: cabecera con miniatura, nombre y estado, y una fila por acción con la línea que la explica. «Activar»
 * y «Añadir a la cola» salen solo si tienen sentido ([canActivate], [canQueue]). «Eliminar» sale en rojo y no borra sin confirmar.
 */
@Composable
internal fun ProgramMoreSheet(
    program: Program,
    subtitle: String,
    canActivate: Boolean,
    canQueue: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onQueue: () -> Unit,
    onShare: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = buildList<ProgramMenuEntry> {
        if (canActivate) add(ProgramMenuEntry(KpknLineIcons.Play, "Activar programa", "Pasa a ser el programa en curso", false, onActivate))
        add(ProgramMenuEntry(KpknLineIcons.Edit, "Editar nombre y portada", null, false, onEdit))
        if (canQueue) add(ProgramMenuEntry(KpknLineIcons.Repeat, "Añadir a la cola", "Empieza cuando termine el actual", false, onQueue))
        add(ProgramMenuEntry(ProgramShareIcon, "Compartir en historias", null, false, onShare))
        add(ProgramMenuEntry(KpknLineIcons.Layers, "Archivar", "Lo quitas de la lista sin borrarlo", false, onArchive))
        add(
            ProgramMenuEntry(
                icon = KpknLineIcons.Trash,
                title = "Eliminar",
                subtitle = "Borra el programa y su plan. Tus registros se conservan.",
                danger = true,
                onClick = onDelete,
            ),
        )
    }
    KpknSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgramCoverThumb(program = program, width = 56.dp, height = 70.dp, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = program.name,
                        style = SheetTitleStyle,
                        color = WizardColors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text = subtitle, style = KpknType.caption, color = WizardColors.textFaint)
                }
            }
            Spacer(Modifier.height(14.dp))
            entries.forEachIndexed { index, entry ->
                if (index > 0) MenuDivider()
                ProgramMenuRow(entry)
            }
        }
    }
}

/**
 * La hoja de Archivados: cada programa con «Restaurar» y una papelera roja. Borrar de aquí es definitivo: la pantalla pide
 * confirmación antes de llamar a permanentlyDeleteProgram.
 */
@Composable
internal fun ArchivedProgramsSheet(
    programs: List<Program>,
    onRestore: (Program) -> Unit,
    onDelete: (Program) -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            Text(text = "Archivados", style = SheetTitleStyle, color = WizardColors.text)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "No aparecen en la lista ni en Inicio.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(12.dp))
            if (programs.isEmpty()) {
                Text(text = "No tienes programas archivados.", style = KpknType.body, color = WizardColors.textFaint)
            }
            programs.forEachIndexed { index, program ->
                if (index > 0) MenuDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = program.name,
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    KpknTextAction(text = "Restaurar", onClick = { onRestore(program) })
                    KpknIconButton(
                        icon = KpknLineIcons.Trash,
                        contentDescription = "Borrar ${program.name}",
                        onClick = { onDelete(program) },
                        tint = WizardColors.danger,
                    )
                }
            }
        }
    }
}

/** Confirmación de borrado, la de «Eliminar» del ⋯ y la de la papelera de Archivados: el botón de borrar va en rojo. */
@Composable
internal fun ProgramDeleteDialog(
    program: Program,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = "¿Eliminar ${program.name}?",
        text = "Borra el programa y su plan. Tus registros se conservan.",
        confirmLabel = "Eliminar",
        onConfirm = onConfirm,
        dismissLabel = "Cancelar",
        destructive = true,
    )
}

@Composable
private fun ProgramMenuRow(entry: ProgramMenuEntry) {
    val interaction = remember { MutableInteractionSource() }
    val ink = if (entry.danger) WizardColors.danger else WizardColors.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = entry.onClick,
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(KpknSurface.inset),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = if (entry.danger) WizardColors.danger else WizardColors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.title, style = KpknType.bodyStrong, color = ink)
            entry.subtitle?.let { subtitle ->
                Text(text = subtitle, style = KpknType.caption, color = WizardColors.textFaint)
            }
        }
    }
}

@Composable
private fun MenuDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}
