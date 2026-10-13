package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.training.AdvancedSplitMode
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.SessionMigrationMode
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.domain.training.SplitApplicationRequest
import com.example.kpkn.domain.training.SplitTemporalScope
import com.example.kpkn.domain.training.SplitWeekOption
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknType

/** Destinos de varios repartos: las semanas elegidas, el bloque actual o todo el programa. */
private val MultiScopes: List<Pair<SplitTemporalScope, String>> = listOf(
    SplitTemporalScope.SELECTED_WEEKS to "Varias",
    SplitTemporalScope.CURRENT_BLOCK to "Bloque",
    SplitTemporalScope.WHOLE_PROGRAM to "Programa",
)

/**
 * Hoja «Aplicar varios repartos» (P5): reparte los repartos elegidos por las semanas destino, en orden y en ciclo si hay más semanas que
 * repartos. Sustituye a `MultiSplitApplySheet` con piezas KPKN; la lógica y las condiciones son las de siempre. Sin «Cancelar».
 */
@Composable
internal fun SplitMultiApplySheet(
    program: Program,
    splits: List<SplitTemplate>,
    weeks: List<SplitWeekOption>,
    selectedBlockId: String?,
    defaultStartDay: Int,
    generationTemplates: List<SessionTemplate>,
    onDismiss: () -> Unit,
    onApply: (Program) -> Unit,
) {
    var startDay by rememberSaveable { mutableStateOf(defaultStartDay) }
    var temporalScope by rememberSaveable { mutableStateOf(SplitTemporalScope.CURRENT_BLOCK) }
    var migrationMode by rememberSaveable { mutableStateOf<SessionMigrationMode?>(null) }
    var destructiveAccepted by rememberSaveable { mutableStateOf(false) }
    var showFinalConfirm by rememberSaveable { mutableStateOf(false) }
    var applyError by remember { mutableStateOf<String?>(null) }
    val selectedWeekIds = remember(selectedBlockId) {
        mutableStateListOf<String>().apply {
            addAll(weeks.filter { selectedBlockId == null || it.blockId == selectedBlockId }.map { it.id })
        }
    }

    val targetWeeks = when (temporalScope) {
        SplitTemporalScope.SELECTED_WEEKS -> weeks.filter { it.id in selectedWeekIds }
        SplitTemporalScope.CURRENT_BLOCK -> weeks.filter { selectedBlockId == null || it.blockId == selectedBlockId }
        SplitTemporalScope.WHOLE_PROGRAM -> weeks
        SplitTemporalScope.CURRENT_WEEK -> weeks.filter { it.id in selectedWeekIds.take(1) }
    }
    val affectedSessions = targetWeeks.sumOf { it.sessions.size }
    val willReplace = migrationMode != null && migrationMode != SessionMigrationMode.MIGRATE && affectedSessions > 0
    val prebuiltAvailable = remember(splits, generationTemplates) {
        splits.all { split ->
            SplitApplicationEngine.prebuiltWeekPreview(split, templates = generationTemplates).days.all { it.isAvailable }
        }
    }
    val canApply = migrationMode != null && splits.isNotEmpty() && targetWeeks.isNotEmpty() &&
        (migrationMode != SessionMigrationMode.PREBUILT || prebuiltAvailable) && (!willReplace || destructiveAccepted)
    val scopeIndex = MultiScopes.indexOfFirst { it.first == temporalScope }.coerceAtLeast(0)

    // Aplica con el motor y, si falla, muestra su mensaje sin tocar el programa.
    fun applyNow(mode: SessionMigrationMode) {
        runCatching {
            applyMultipleSplits(program, splits, targetWeeks, selectedBlockId, startDay, mode, generationTemplates)
        }.fold(
            onSuccess = onApply,
            onFailure = { error ->
                applyError = error.message?.takeIf { it.isNotBlank() } ?: "No se pudo aplicar el reparto."
            },
        )
    }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            // Sin tope propio: la hoja ya se limita a su altura máxima y este contenido se desplaza dentro de ella.
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("split_aplicar_varios"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Aplicar varios repartos", style = KpknType.title, color = WizardColors.text)
            Text(
                text = "Los repartos elegidos se reparten por las semanas destino en el mismo orden. Si hay más semanas que repartos, se repiten en ciclo.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )

            KpknModule {
                KpknModuleHeader(label = "Repartos en orden")
                splits.forEachIndexed { index, split ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${index + 1} · ${split.name}",
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                    Text(
                        text = "${splitDaysLabel(splitTrainingDays(split.pattern))} a la semana",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }

            if (ProgramCalendarEngine.isCalendarized(program)) {
                Text(
                    text = "El inicio de la semana lo fija el calendario del programa.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            } else {
                KpknModule {
                    KpknModuleHeader(label = "Inicio de semana")
                    Spacer(Modifier.height(10.dp))
                    SplitStartDayControl(startDay = startDay, onSelect = { startDay = it })
                }
            }

            KpknModule {
                KpknModuleHeader(label = "Destino")
                Spacer(Modifier.height(10.dp))
                KpknSegmentedControl(
                    options = MultiScopes.map { it.second },
                    selectedIndex = scopeIndex,
                    onSelect = { temporalScope = MultiScopes[it].first },
                    size = KpknSegmentedSize.Compact,
                )
                if (temporalScope == SplitTemporalScope.SELECTED_WEEKS) {
                    Spacer(Modifier.height(14.dp))
                    SplitWeekCircles(
                        weeks = weeks,
                        selectedWeekIds = selectedWeekIds.toList(),
                        currentWeekId = null,
                        onToggle = { weekId ->
                            if (weekId in selectedWeekIds) selectedWeekIds.remove(weekId) else selectedWeekIds.add(weekId)
                        },
                    )
                }
            }

            KpknModule {
                KpknModuleHeader(label = "Contenido de las sesiones")
                Spacer(Modifier.height(6.dp))
                SplitModeRows(
                    options = listOf(
                        SplitModeOption(
                            mode = SessionMigrationMode.MIGRATE,
                            title = "Conservar y reubicar",
                            description = "Mantiene sesiones y las reubica según el reparto que toque en cada semana.",
                        ),
                        SplitModeOption(
                            mode = SessionMigrationMode.CLEAN,
                            title = "Sesiones vacías",
                            description = "Reemplaza las semanas destino por sesiones base vacías.",
                        ),
                        SplitModeOption(
                            mode = SessionMigrationMode.PREBUILT,
                            title = "Sesiones pre-creadas",
                            description = "Carga ejercicios desde la mejor plantilla compatible de cada reparto.",
                        ),
                    ),
                    selected = migrationMode,
                    onSelect = { migrationMode = it },
                    note = null,
                )
            }

            if (willReplace) {
                SplitImpactModule(
                    affectedSessions = affectedSessions,
                    affectedWeeks = targetWeeks.size,
                    accepted = destructiveAccepted,
                    onAcceptedChange = { destructiveAccepted = it },
                )
            }

            KpknPrimaryPill(
                text = when {
                    migrationMode == null -> "Elige el contenido"
                    willReplace -> "Reemplazar ${sessionsLabel(affectedSessions)}"
                    else -> "Aplicar repartos"
                },
                onClick = {
                    migrationMode?.let { mode ->
                        if (willReplace && (targetWeeks.size > 1 || affectedSessions > 4)) {
                            showFinalConfirm = true
                        } else {
                            applyNow(mode)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canApply,
                tag = "split_aplicar_varios_confirmar",
            )
            Spacer(Modifier.height(18.dp))
        }
    }

    if (showFinalConfirm) {
        SplitConfirmDialog(
            title = "Confirmar aplicación múltiple",
            text = "Se reemplazarán ${sessionsLabel(affectedSessions)} en ${weeksLabel(targetWeeks.size)} usando ${splits.size} " +
                if (splits.size == 1) "reparto." else "repartos.",
            confirmLabel = "Sí, aplicar",
            onConfirm = {
                showFinalConfirm = false
                migrationMode?.let { applyNow(it) }
            },
            onDismiss = { showFinalConfirm = false },
        )
    }
    applyError?.let { message ->
        SplitErrorDialog(message = message, onDismiss = { applyError = null })
    }
}

/**
 * Reparte [splits] por las semanas [targetWeeks]: el reparto i se aplica a las semanas que caen en su posición módulo el número de
 * repartos. Devuelve el programa sin tocar si no hay repartos o semanas.
 */
private fun applyMultipleSplits(
    program: Program,
    splits: List<SplitTemplate>,
    targetWeeks: List<SplitWeekOption>,
    selectedBlockId: String?,
    startDay: Int,
    migrationMode: SessionMigrationMode,
    generationTemplates: List<SessionTemplate>,
): Program {
    if (splits.isEmpty() || targetWeeks.isEmpty()) return program
    var updated = program
    splits.forEachIndexed { splitIndex, split ->
        val weekIds = targetWeeks.filterIndexed { index, _ -> index % splits.size == splitIndex }.map { it.id }.toSet()
        if (weekIds.isNotEmpty()) {
            updated = SplitApplicationEngine.apply(
                SplitApplicationRequest(
                    program = updated,
                    selectedSplit = split,
                    selectedBlockId = selectedBlockId,
                    selectedWeekId = weekIds.first(),
                    startDay = startDay,
                    temporalScope = SplitTemporalScope.SELECTED_WEEKS,
                    selectedWeekIds = weekIds,
                    advancedMode = AdvancedSplitMode.GLOBAL,
                    migrationMode = migrationMode,
                    generationTemplates = generationTemplates,
                ),
            )
        }
    }
    return updated
}
