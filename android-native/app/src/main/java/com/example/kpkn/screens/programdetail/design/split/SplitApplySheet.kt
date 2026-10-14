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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.kpkn.domain.templates.SuggestionPrefs
import com.example.kpkn.domain.training.AdvancedSplitMode
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.SessionMigrationMode
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.domain.training.SplitApplicationRequest
import com.example.kpkn.domain.training.SplitBlockOption
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

/** Alcances de un reparto de una semana o un bloque, con su etiqueta corta. */
private val SingleScopes: List<Pair<SplitTemporalScope, String>> = listOf(
    SplitTemporalScope.CURRENT_WEEK to "Semana",
    SplitTemporalScope.SELECTED_WEEKS to "Varias",
    SplitTemporalScope.CURRENT_BLOCK to "Bloque",
    SplitTemporalScope.WHOLE_PROGRAM to "Programa",
)

/**
 * Hoja «Aplicar reparto» (P5): aplica un reparto a la semana, a varias, al bloque o al programa. Sustituye a `SplitApplySheet` con piezas
 * KPKN; la lógica es la de siempre: el motor calcula el impacto, la disponibilidad de las plantillas y la aplicación, y el botón solo se
 * activa cuando todo cuadra y, si reemplaza sesiones, cuando se acepta el impacto. No tiene «Cancelar»: se cierra con el asa o atrás.
 */
@Composable
internal fun SplitApplySheet(
    program: Program,
    selectedSplit: SplitTemplate,
    blocks: List<SplitBlockOption>,
    weeks: List<SplitWeekOption>,
    selectedBlockId: String?,
    selectedWeekId: String?,
    defaultStartDay: Int,
    isAdvancedProgram: Boolean,
    totalWeeks: Int,
    generationTemplates: List<SessionTemplate>,
    onDismiss: () -> Unit,
    onApply: (Program) -> Unit,
) {
    val isCalendarized = ProgramCalendarEngine.isCalendarized(program)
    var startDay by rememberSaveable { mutableStateOf(defaultStartDay) }
    var temporalScope by rememberSaveable {
        mutableStateOf(
            if (!selectedWeekId.isNullOrBlank()) SplitTemporalScope.CURRENT_WEEK
            else SplitTemporalScope.CURRENT_BLOCK,
        )
    }
    var advancedMode by rememberSaveable { mutableStateOf(AdvancedSplitMode.GLOBAL) }
    var migrationMode by rememberSaveable { mutableStateOf<SessionMigrationMode?>(null) }
    var destructiveAccepted by rememberSaveable { mutableStateOf(false) }
    var showFinalConfirm by rememberSaveable { mutableStateOf(false) }
    var applyError by remember { mutableStateOf<String?>(null) }
    val blockSelections = remember {
        mutableStateMapOf<String, String>().apply { putAll(blocks.associate { it.id to selectedSplit.id }) }
    }
    val resolvedWeekId = selectedWeekId ?: weeks.firstOrNull()?.id
    val selectedWeekIds = remember(resolvedWeekId) {
        mutableStateListOf<String>().apply { resolvedWeekId?.let(::add) }
    }
    var focusOverrides by remember(selectedSplit.id) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val weekPreview = remember(selectedSplit, focusOverrides, generationTemplates) {
        SplitApplicationEngine.prebuiltWeekPreview(
            split = selectedSplit,
            templates = generationTemplates,
            prefs = SuggestionPrefs(
                preferredDifficulty = selectedSplit.difficulty,
                forcedTemplateByDayIndex = focusOverrides,
            ),
        )
    }
    val templatePreview = weekPreview.days

    LaunchedEffect(temporalScope, migrationMode, advancedMode, selectedWeekIds.toList(), blockSelections.toMap()) {
        destructiveAccepted = false
    }

    val resolvedMode = migrationMode ?: SessionMigrationMode.MIGRATE
    val request = SplitApplicationRequest(
        program = program,
        selectedSplit = selectedSplit,
        selectedBlockId = selectedBlockId,
        selectedWeekId = resolvedWeekId,
        startDay = startDay,
        temporalScope = temporalScope,
        selectedWeekIds = selectedWeekIds.toSet(),
        advancedMode = advancedMode,
        migrationMode = resolvedMode,
        perBlockSelections = blockSelections.toMap(),
        prebuiltPrefs = SuggestionPrefs(
            preferredDifficulty = selectedSplit.difficulty,
            forcedTemplateByDayIndex = focusOverrides,
        ),
        generationTemplates = generationTemplates,
    )
    val impact = SplitApplicationEngine.impactSummary(request)
    val targetHasSessions = impact.affectedSessions > 0
    // PER_BLOCK valida cada reparto elegido para su bloque, no solo el que se muestra: es el espejo de la puerta del motor.
    val prebuiltUnavailable = remember(
        selectedSplit.id,
        migrationMode,
        advancedMode,
        temporalScope,
        selectedWeekIds.toList(),
        blockSelections.toMap(),
        startDay,
        generationTemplates,
    ) { SplitApplicationEngine.prebuiltUnavailabilityReasons(request) }
    val selectedModeAvailable = migrationMode != SessionMigrationMode.PREBUILT || prebuiltUnavailable.isEmpty()
    val canApply = impact.affectedWeeks > 0 && migrationMode != null && selectedModeAvailable &&
        (!impact.willReplaceSessions || destructiveAccepted)
    val showScope = totalWeeks > 1 && !(isAdvancedProgram && advancedMode == AdvancedSplitMode.PER_BLOCK)
    val scopeIndex = SingleScopes.indexOfFirst { it.first == temporalScope }.coerceAtLeast(0)

    val modeOptions = buildList {
        if (targetHasSessions) {
            add(
                SplitModeOption(
                    mode = SessionMigrationMode.MIGRATE,
                    title = "Conservar y reubicar",
                    description = "Mantiene los ejercicios existentes e intenta alinearlos con los días del nuevo reparto.",
                ),
            )
        }
        add(
            SplitModeOption(
                mode = SessionMigrationMode.CLEAN,
                title = "Sesiones vacías",
                description = "Crea la estructura de días del reparto sin ejercicios.",
            ),
        )
        add(
            SplitModeOption(
                mode = SessionMigrationMode.PREBUILT,
                title = "Con ejercicios de plantilla",
                description = "Carga ejercicios desde la plantilla más compatible para cada día.",
            ),
        )
    }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            // Sin tope propio: la hoja ya se limita a su altura máxima y este contenido se desplaza dentro de ella.
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("split_aplicar"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Aplicar reparto", style = KpknType.title, color = WizardColors.text)
            Text(
                text = "Elige el alcance y cómo se crean las sesiones. Ninguna opción se aplica hasta que confirmes.",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )

            KpknModule {
                Text(text = selectedSplit.name, style = KpknType.bodyStrong, color = WizardColors.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${splitDaysLabel(splitTrainingDays(selectedSplit.pattern))} a la semana",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(14.dp))
                SplitWeekDiagram(pattern = selectedSplit.pattern, startDay = startDay)
                Spacer(Modifier.height(8.dp))
                SplitRoleLegend(pattern = selectedSplit.pattern)
                // Lo que antes abría el icono de información del catálogo: la descripción, y sus beneficios y precauciones.
                if (selectedSplit.description.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(text = selectedSplit.description, style = KpknType.body, color = WizardColors.textMuted)
                }
                SplitExplainer(title = "Beneficios", items = selectedSplit.pros.take(4))
                SplitExplainer(title = "Precauciones", items = selectedSplit.cons.take(4))
            }

            if (isCalendarized) {
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

            if (showScope) {
                KpknModule {
                    KpknModuleHeader(label = "Alcance")
                    Spacer(Modifier.height(10.dp))
                    KpknSegmentedControl(
                        options = SingleScopes.map { it.second },
                        selectedIndex = scopeIndex,
                        onSelect = { temporalScope = SingleScopes[it].first },
                        size = KpknSegmentedSize.Compact,
                    )
                    if (temporalScope == SplitTemporalScope.SELECTED_WEEKS && advancedMode != AdvancedSplitMode.PER_BLOCK) {
                        Spacer(Modifier.height(14.dp))
                        SplitWeekCircles(
                            weeks = weeks,
                            selectedWeekIds = selectedWeekIds.toList(),
                            currentWeekId = resolvedWeekId,
                            onToggle = { weekId ->
                                if (weekId in selectedWeekIds) selectedWeekIds.remove(weekId) else selectedWeekIds.add(weekId)
                            },
                        )
                    }
                }
            }

            if (isAdvancedProgram) {
                KpknModule {
                    KpknModuleHeader(label = "Programa avanzado")
                    Spacer(Modifier.height(10.dp))
                    KpknSegmentedControl(
                        options = listOf("Un reparto", "Por bloque"),
                        selectedIndex = if (advancedMode == AdvancedSplitMode.PER_BLOCK) 1 else 0,
                        onSelect = { advancedMode = if (it == 1) AdvancedSplitMode.PER_BLOCK else AdvancedSplitMode.GLOBAL },
                        size = KpknSegmentedSize.Compact,
                    )
                    if (advancedMode == AdvancedSplitMode.PER_BLOCK) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Se aplicará el reparto elegido a todas las semanas de cada bloque.",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                        )
                        blocks.forEach { block ->
                            Spacer(Modifier.height(12.dp))
                            SplitBlockRow(
                                block = block,
                                selectedSplitId = blockSelections[block.id] ?: selectedSplit.id,
                                onSelect = { blockSelections[block.id] = it },
                            )
                        }
                    }
                }
            }

            KpknModule {
                KpknModuleHeader(label = "Contenido de las sesiones")
                Spacer(Modifier.height(6.dp))
                SplitModeRows(
                    options = modeOptions,
                    selected = migrationMode,
                    onSelect = { migrationMode = it },
                    note = impact.migrationNote,
                )
            }

            if (migrationMode == SessionMigrationMode.PREBUILT) {
                if (prebuiltUnavailable.isNotEmpty()) {
                    Text(
                        text = "No se puede generar este alcance hasta publicar recetas exactas:\n${prebuiltUnavailable.take(3).joinToString("\n")}",
                        style = KpknType.caption,
                        color = WizardColors.danger,
                    )
                }
                SplitTemplatePreview(
                    days = templatePreview,
                    warnings = weekPreview.warnings,
                    exceedsWeeklyBudget = weekPreview.exceedsWeeklyBudget,
                    onPickTemplate = { dayIndex, templateId ->
                        focusOverrides = focusOverrides + (dayIndex to templateId)
                    },
                )
            }

            if (impact.willReplaceSessions) {
                SplitImpactModule(
                    affectedSessions = impact.affectedSessions,
                    affectedWeeks = impact.affectedWeeks,
                    accepted = destructiveAccepted,
                    onAcceptedChange = { destructiveAccepted = it },
                )
            }

            KpknPrimaryPill(
                text = when {
                    migrationMode == null -> "Elige el contenido"
                    impact.willReplaceSessions -> "Reemplazar ${sessionsLabel(impact.affectedSessions)}"
                    else -> "Aplicar reparto"
                },
                onClick = {
                    if (impact.isLargeDestructiveChange) showFinalConfirm = true
                    else applySplitSafely(request, onApply) { applyError = it }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canApply,
                tag = "split_aplicar_confirmar",
            )
            Spacer(Modifier.height(18.dp))
        }
    }

    if (showFinalConfirm) {
        SplitConfirmDialog(
            title = "Confirmar reemplazo",
            text = "Se reemplazarán ${sessionsLabel(impact.affectedSessions)} en ${weeksLabel(impact.affectedWeeks)}. " +
                "Esta acción no se puede deshacer automáticamente.",
            confirmLabel = "Sí, reemplazar",
            onConfirm = {
                showFinalConfirm = false
                applySplitSafely(request, onApply) { applyError = it }
            },
            onDismiss = { showFinalConfirm = false },
        )
    }
    applyError?.let { message ->
        SplitErrorDialog(message = message, onDismiss = { applyError = null })
    }
}

/**
 * Aplica el reparto con el motor. Si falla, no toca el programa: entrega el mensaje del motor a [onError].
 */
private fun applySplitSafely(
    request: SplitApplicationRequest,
    onApply: (Program) -> Unit,
    onError: (String) -> Unit,
) {
    try {
        // La escala al MAV calibrado corre dentro de apply() vía request.program.volumeRecommendations; sin calibrar no se toca nada
        // (la invitación a calibrar vive en la pestaña Volumen).
        onApply(
            SplitApplicationEngine.apply(
                request,
                exerciseList = com.example.kpkn.data.exercises.exerciseCatalogSnapshot(),
            ),
        )
    } catch (error: Exception) {
        onError(
            error.message?.takeIf { it.isNotBlank() }
                ?: "No se pudo aplicar el reparto.",
        )
    }
}

/** Un bloque de la explicación del reparto: su título en `bodyStrong` y una línea por punto en `caption`. Sin puntos, no pinta nada. */
@Composable
private fun SplitExplainer(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
    items.forEach { item ->
        Text(text = "• $item", style = KpknType.caption, color = WizardColors.textMuted)
    }
}
