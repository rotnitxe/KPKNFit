package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionRoadmapOption
import com.example.kpkn.screens.sessioneditor.sessionEditorDayLabel
import com.example.kpkn.data.models.Session
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/** Sin día pendiente de crear: el diálogo de «Crear sesión» está cerrado. */
private const val NO_DAY = -1

/**
 * Hoja «Cambiar de sesión» (SESSION_EDITOR_V1 §2): abre desde el chip de la cabecera y lleva lo que antes enseñaba
 * `SessionContextNavigator`. Guarda solo el estado de los dos diálogos de creación (sesión en un día vacío y variante derivada); el
 * resto llega de la pantalla. Elegir una sesión o crear una cierra la hoja; crear una variante la deja abierta para verla.
 *
 * @param defaultVariantName nombre que se propone al crear una variante, p. ej. «Torso A – Rápida».
 * @param nextVariant variante libre a crear (B, C o D), o nulo si ya están todas.
 */
@Composable
fun SessionNavigatorSheet(
    orderedDays: List<Int>,
    sessions: List<Session>,
    currentSessionId: String,
    currentDayOfWeek: Int?,
    activeVariant: WeekVariant,
    availableVariants: List<WeekVariant>,
    variantName: (WeekVariant) -> String,
    nextVariant: WeekVariant?,
    defaultVariantName: String,
    roadmapOptions: List<SessionRoadmapOption>,
    currentWeekId: String,
    blockGrouping: Boolean,
    weekCaption: String?,
    onDismiss: () -> Unit,
    onSelectSession: (String) -> Unit,
    onMarkMain: (String) -> Unit,
    onCreateSessionForDay: (Int) -> Unit,
    onSelectVariant: (WeekVariant) -> Unit,
    onCreateVariant: (WeekVariant, String) -> Unit,
    onDeleteActiveVariant: () -> Unit,
    onSelectRoadmapOption: (SessionRoadmapOption) -> Unit,
) {
    var pendingCreateDay by rememberSaveable { mutableStateOf(NO_DAY) }
    var showCreateVariant by rememberSaveable { mutableStateOf(false) }
    var newVariantName by rememberSaveable { mutableStateOf("") }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Cambiar de sesión",
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.semantics { heading() },
            )
            SessionNavigatorSheetContent(
                orderedDays = orderedDays,
                sessions = sessions,
                currentSessionId = currentSessionId,
                currentDayOfWeek = currentDayOfWeek,
                activeVariant = activeVariant,
                availableVariants = availableVariants,
                variantName = variantName,
                canCreateVariant = nextVariant != null,
                roadmapOptions = roadmapOptions,
                currentWeekId = currentWeekId,
                blockGrouping = blockGrouping,
                weekCaption = weekCaption,
                onSelectSession = { id ->
                    onSelectSession(id)
                    onDismiss()
                },
                onMarkMain = onMarkMain,
                onCreateForDay = { day -> pendingCreateDay = day },
                onSelectVariant = onSelectVariant,
                onRequestCreateVariant = {
                    newVariantName = defaultVariantName
                    showCreateVariant = true
                },
                onDeleteActiveVariant = onDeleteActiveVariant,
                onSelectRoadmapOption = { option ->
                    onSelectRoadmapOption(option)
                    onDismiss()
                },
            )
        }
    }

    if (pendingCreateDay != NO_DAY) {
        val day = pendingCreateDay
        KpknAlertDialog(
            onDismissRequest = { pendingCreateDay = NO_DAY },
            title = {
                Text(
                    text = "¿Crear sesión para ${sessionEditorDayLabel(day)}?",
                    style = KpknType.dialogTitle,
                    color = WizardColors.text,
                )
            },
            text = {
                Text(
                    text = "Este día no tiene una sesión asignada. ¿Quieres crear una nueva sesión aquí?",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            },
            confirmButton = {
                KpknPrimaryPill(
                    text = "Crear sesión",
                    onClick = {
                        pendingCreateDay = NO_DAY
                        onCreateSessionForDay(day)
                        onDismiss()
                    },
                )
            },
            dismissButton = {
                KpknTextAction(text = "Cancelar", onClick = { pendingCreateDay = NO_DAY })
            },
        )
    }

    val variantTarget = nextVariant
    if (showCreateVariant && variantTarget != null) {
        KpknAlertDialog(
            onDismissRequest = { showCreateVariant = false },
            title = {
                Text(
                    text = "Nueva variante",
                    style = KpknType.dialogTitle,
                    color = WizardColors.text,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Crea una variante derivada de la sesión original. Tendrá sus propios ejercicios, series y descansos independientes.",
                        style = KpknType.body,
                        color = WizardColors.textMuted,
                    )
                    KpknTextField(
                        value = newVariantName,
                        onValueChange = { newVariantName = it },
                        label = "Nombre de la variante",
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                KpknPrimaryPill(
                    text = "Crear variante",
                    enabled = newVariantName.isNotBlank(),
                    onClick = {
                        onCreateVariant(variantTarget, newVariantName.trim())
                        showCreateVariant = false
                    },
                )
            },
            dismissButton = {
                KpknTextAction(text = "Cancelar", onClick = { showCreateVariant = false })
            },
        )
    }
}
