package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.isInSuperset
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SupersetDraft
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.KpknEmptyState

/** Miembros máximos de una superserie, como antes: el borrador se recorta a cuatro. */
private const val SUPERSET_MAX_MEMBERS = 4

/**
 * Hoja «Añadir a superserie» (SE7). Una fila de selección por ejercicio libre de la sesión: marcar y desmarcar cambia el borrador, y
 * «Crear superserie» lo confirma con [onConfirm] cuando hay dos o más. «Abrir catálogo» y «Cerrar» no cambian nada.
 */
@Composable
internal fun SupersetMemberPickerSheet(
    draft: SupersetDraft,
    sessionExercises: List<Exercise>,
    onUpdateDraft: (SupersetDraft) -> Unit,
    onConfirm: () -> Unit,
    onOpenCatalog: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedIds = draft.exerciseIds.toSet()
    val candidates = remember(sessionExercises, draft.exerciseIds) {
        sessionExercises.filter { it.id in selectedIds || !it.isInSuperset() }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Añadir a superserie", style = KpknType.title, color = WizardColors.text)
        Text(
            text = "Marca de 2 a 4 ejercicios de esta sesión. Si falta uno, ábrelo desde el catálogo.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        if (candidates.isEmpty()) {
            KpknEmptyState(
                icon = KpknLineIcons.Repeat,
                title = "No hay ejercicios libres en esta sesión.",
                body = null,
            )
        } else {
            KpknModule {
                candidates.forEachIndexed { index, exercise ->
                    if (index > 0) SheetDivider()
                    val checked = exercise.id in selectedIds
                    KpknCheckRow(
                        checked = checked,
                        title = exercise.name.ifBlank { "Ejercicio" },
                        enabled = checked || draft.exerciseIds.size < SUPERSET_MAX_MEMBERS,
                        onClick = {
                            val next = if (checked) {
                                draft.exerciseIds - exercise.id
                            } else {
                                (draft.exerciseIds + exercise.id).distinct().take(SUPERSET_MAX_MEMBERS)
                            }
                            onUpdateDraft(draft.copy(exerciseIds = next))
                        },
                    )
                }
            }
        }
        KpknPrimaryPill(
            text = "Crear superserie",
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = draft.exerciseIds.size >= 2,
        )
        KpknSecondaryPill(
            text = "Abrir catálogo",
            onClick = onOpenCatalog,
            modifier = Modifier.fillMaxWidth(),
        )
        KpknTextAction(text = "Cerrar", onClick = onDismiss)
    }
}

/**
 * Hoja «Gestionar superserie» (SE7). Los ejercicios del grupo con su número, sus series y un botón para quitar cada uno; los dos descansos
 * como celdas numéricas, en segundos; y «Deshacer superserie», en peligro y con confirmación. Deshacer quita a todos los miembros con
 * [onRemove], que ya disuelve el grupo cuando quedan dos.
 */
@Composable
internal fun SupersetManagerSheet(
    exercises: List<Exercise>,
    partId: String?,
    supersetId: String,
    restBetweenSeconds: Int?,
    restAfterSeconds: Int?,
    onUpdateRestBetween: (String?, String, Int) -> Unit,
    onUpdateRestAfter: (String?, String, Int) -> Unit,
    onRemove: (String?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    SupersetManagerBody(
        exercises = exercises,
        partId = partId,
        supersetId = supersetId,
        restBetweenSeconds = restBetweenSeconds,
        restAfterSeconds = restAfterSeconds,
        onUpdateRestBetween = onUpdateRestBetween,
        onUpdateRestAfter = onUpdateRestAfter,
        onRemove = onRemove,
        onDismiss = onDismiss,
    )
}

/** El contenido de «Gestionar superserie» sin el despachador. Guarda la confirmación de deshacer dentro del propio contenido. */
@Composable
internal fun SupersetManagerBody(
    exercises: List<Exercise>,
    partId: String?,
    supersetId: String,
    restBetweenSeconds: Int?,
    restAfterSeconds: Int?,
    onUpdateRestBetween: (String?, String, Int) -> Unit,
    onUpdateRestAfter: (String?, String, Int) -> Unit,
    onRemove: (String?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Los descansos viven en segundos; la celda los muestra en m:ss y se cambian con el selector de tiempo, como en reglas.
    var restBetween by rememberSaveable(supersetId, restBetweenSeconds) { mutableStateOf(restBetweenSeconds) }
    var restAfter by rememberSaveable(supersetId, restAfterSeconds) { mutableStateOf(restAfterSeconds) }
    var pendingUndo by remember { mutableStateOf(false) }
    var pickingRest by remember { mutableStateOf<SupersetRest?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Gestionar superserie", style = KpknType.title, color = WizardColors.text)
        Text(
            text = "${exercises.size} ejercicios vinculados",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )

        KpknModule {
            exercises.forEachIndexed { index, exercise ->
                if (index > 0) SheetDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = KpknType.bodyStrong,
                        color = WizardColors.textMuted,
                        modifier = Modifier.width(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exercise.name,
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${exercise.sets.size} series · usa el descanso de la superserie",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                    }
                    KpknIconButton(
                        icon = KpknLineIcons.Close,
                        contentDescription = "Quitar ${exercise.name} de la superserie",
                        tint = WizardColors.danger,
                        onClick = {
                            onRemove(partId, exercise.id)
                            onDismiss()
                        },
                    )
                }
            }
        }

        KpknModule {
            SupersetRestRow(
                label = "Entre ejercicios",
                seconds = restBetween,
                onOpen = { pickingRest = SupersetRest.BETWEEN },
            )
            SheetDivider()
            SupersetRestRow(
                label = "Tras la superserie",
                seconds = restAfter,
                onOpen = { pickingRest = SupersetRest.AFTER },
            )
        }

        KpknTextAction(
            text = "Deshacer superserie",
            onClick = { pendingUndo = true },
            color = WizardColors.danger,
        )
    }

    pickingRest?.let { rest ->
        val title = if (rest == SupersetRest.BETWEEN) "Descanso entre ejercicios" else "Descanso tras la superserie"
        val current = (if (rest == SupersetRest.BETWEEN) restBetween else restAfter) ?: 0
        KpknNativeTimePickerDialog(
            title = title,
            initialHour = (current / 60).coerceIn(0, 23),
            initialMinute = (current % 60).coerceIn(0, 59),
            hint = "Minutos : segundos",
            onConfirm = { hour, minute ->
                val seconds = hour * 60 + minute
                if (rest == SupersetRest.BETWEEN) {
                    restBetween = seconds
                    onUpdateRestBetween(partId, supersetId, seconds)
                } else {
                    restAfter = seconds
                    onUpdateRestAfter(partId, supersetId, seconds)
                }
                pickingRest = null
            },
            onDismiss = { pickingRest = null },
        )
    }

    if (pendingUndo) {
        KpknAlertDialog(
            onDismissRequest = { pendingUndo = false },
            title = "¿Deshacer la superserie?",
            text = "Los ejercicios vuelven a ser individuales y se pierden los descansos de la superserie.",
            confirmLabel = "Deshacer",
            onConfirm = {
                pendingUndo = false
                // El último ejercicio que se quita disuelve el grupo; el orden inverso lo deja vacío sin tocar el resto de la sesión.
                exercises.asReversed().forEach { exercise -> onRemove(partId, exercise.id) }
                onDismiss()
            },
            dismissLabel = "Cancelar",
            onDismiss = { pendingUndo = false },
            destructive = true,
        )
    }
}

/** Un descanso de la superserie en m:ss, como en reglas: la celda abre el selector de tiempo. Sin valor, muestra «–». */
@Composable
private fun SupersetRestRow(
    label: String,
    seconds: Int?,
    onOpen: () -> Unit,
) {
    val display = seconds?.let { KpknSetFormat.rest(it) }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            modifier = Modifier.weight(1f),
        )
        KpknNumberCell(
            value = display,
            input = KpknCellInput.Picker(onOpen = onOpen),
            contentDescription = "$label, ${display.ifEmpty { "sin tiempo" }}",
            modifier = Modifier.width(96.dp),
        )
    }
}

/** Los dos descansos de la superserie: cuál de ellos abre el selector de tiempo. */
private enum class SupersetRest { BETWEEN, AFTER }

/** Separador de 1 dp dentro de un módulo (`divider`, blanco al 6 %). */
@Composable
private fun SheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KpknSurface.divider),
    )
}
