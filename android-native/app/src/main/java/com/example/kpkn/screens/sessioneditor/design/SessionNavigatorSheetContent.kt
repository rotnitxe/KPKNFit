package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionRoadmapOption
import com.example.kpkn.screens.sessioneditor.sessionEditorDayLabel
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Contenido del navegador de sesiones (SESSION_EDITOR_V1 §2): las sesiones de la semana por día, «Nueva sesión» en los días sin sesión,
 * las variantes de la sesión actual y, si el programa tiene varias semanas, la lista de semanas. Sin estado: la hoja (o la hoja de
 * render) pone los datos y las acciones. Cada elección es un `KpknRadioRow`; las acciones, un `KpknTextAction` de 48 dp.
 *
 * @param orderedDays días de la semana en el orden del programa (empieza por [com.example.kpkn.data.models.Program]`.weekStartDay`).
 * @param blockGrouping true si el programa tiene varios bloques: las semanas se agrupan con su nombre de bloque.
 * @param weekCaption «Semana 3 · Nombre», solo en programas simples con bucles activos.
 * @param canCreateVariant true si queda una variante libre (B, C o D) por crear.
 */
@Composable
fun SessionNavigatorSheetContent(
    orderedDays: List<Int>,
    sessions: List<Session>,
    currentSessionId: String,
    currentDayOfWeek: Int?,
    activeVariant: WeekVariant,
    availableVariants: List<WeekVariant>,
    variantName: (WeekVariant) -> String,
    canCreateVariant: Boolean,
    roadmapOptions: List<SessionRoadmapOption>,
    currentWeekId: String,
    blockGrouping: Boolean,
    weekCaption: String?,
    onSelectSession: (String) -> Unit,
    onMarkMain: (String) -> Unit,
    onCreateForDay: (Int) -> Unit,
    onSelectVariant: (WeekVariant) -> Unit,
    onRequestCreateVariant: () -> Unit,
    onDeleteActiveVariant: () -> Unit,
    onSelectRoadmapOption: (SessionRoadmapOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KpknModule(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
            KpknModuleHeader(label = "Sesiones de la semana")
            Spacer(Modifier.height(4.dp))
            orderedDays.forEach { day ->
                val daySessions = sessions.filter { it.dayOfWeek == day }
                if (daySessions.isEmpty()) {
                    NavigatorEmptyDayRow(day = day, onCreate = { onCreateForDay(day) })
                } else {
                    daySessions.forEach { session ->
                        NavigatorSessionRow(
                            day = day,
                            name = session.name,
                            isMain = session.isMainSession,
                            selected = session.id == currentSessionId,
                            canMarkMain = daySessions.size > 1 && !session.isMainSession && day == currentDayOfWeek,
                            onSelect = { onSelectSession(session.id) },
                            onMarkMain = { onMarkMain(session.id) },
                        )
                    }
                }
            }
        }

        KpknModule(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
            KpknModuleHeader(label = "Variantes de la sesión")
            Spacer(Modifier.height(4.dp))
            availableVariants.forEach { variant ->
                KpknRadioRow(
                    selected = variant == activeVariant,
                    title = variantName(variant),
                    onClick = { if (variant != activeVariant) onSelectVariant(variant) },
                )
            }
            if (canCreateVariant) {
                KpknTextAction(
                    text = "Crear sesión derivada",
                    onClick = onRequestCreateVariant,
                    icon = KpknLineIcons.Plus,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                )
            }
            if (activeVariant != WeekVariant.A) {
                KpknTextAction(
                    text = "Eliminar variante activa",
                    onClick = onDeleteActiveVariant,
                    color = WizardColors.danger,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                )
            }
        }

        if (roadmapOptions.size > 1) {
            KpknModule(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                KpknModuleHeader(label = "Semanas del programa")
                if (weekCaption != null) {
                    Text(
                        text = weekCaption,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                var lastBlock = -1
                roadmapOptions.forEach { option ->
                    if (blockGrouping && option.blockIndex != lastBlock) {
                        lastBlock = option.blockIndex
                        Text(
                            text = "Bloque ${option.blockIndex + 1} · ${option.blockName}",
                            style = KpknType.caption,
                            color = WizardColors.textFaint,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    KpknRadioRow(
                        selected = option.weekId == currentWeekId,
                        title = "B${option.blockIndex + 1} · S${option.weekIndex + 1}",
                        subtitle = option.weekName,
                        onClick = { onSelectRoadmapOption(option) },
                    )
                }
            }
        }
    }
}

/** Fila de un día sin sesión: el día en `bodyStrong` y «Sin sesión», con la acción «Nueva sesión» a la derecha. */
@Composable
private fun NavigatorEmptyDayRow(day: Int, onCreate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                text = sessionEditorDayLabel(day),
                style = KpknType.bodyStrong,
                color = WizardColors.textMuted,
            )
            Text(
                text = "Sin sesión",
                style = KpknType.caption,
                color = WizardColors.textFaint,
            )
        }
        KpknTextAction(text = "Nueva sesión", onClick = onCreate)
    }
}

/** Fila de una sesión: «Lun · Torso A», con «Principal» en `caption` si lo es y, si procede, la acción de marcarla. */
@Composable
private fun NavigatorSessionRow(
    day: Int,
    name: String,
    isMain: Boolean,
    selected: Boolean,
    canMarkMain: Boolean,
    onSelect: () -> Unit,
    onMarkMain: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KpknRadioRow(
            selected = selected,
            title = "${sessionEditorDayLabel(day)} · ${name.ifBlank { "Sesión" }}",
            subtitle = if (isMain) "Principal" else null,
            onClick = onSelect,
            modifier = Modifier.weight(1f),
        )
        if (canMarkMain) {
            KpknTextAction(text = "Marcar como principal", onClick = onMarkMain)
        }
    }
}
