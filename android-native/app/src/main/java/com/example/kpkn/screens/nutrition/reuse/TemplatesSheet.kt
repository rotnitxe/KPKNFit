package com.example.kpkn.screens.nutrition.reuse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MealTemplate
import com.example.kpkn.domain.nutrition.MealReuse
import com.example.kpkn.domain.nutrition.MealReuse.TemplateSummary
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknDangerPill
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Lo que muestra una fila de la lista de plantillas: el resumen y en qué modo está (normal, renombrando o confirmando el borrado). */
data class TemplateRowState(
    val summary: TemplateSummary,
    val renaming: Boolean = false,
    val renameText: String = "",
    val renameError: String? = null,
    val confirmingDelete: Boolean = false,
)

/** «3 alimentos · P 28 g · H 50 g · G 12 g». */
internal fun templateCaption(summary: TemplateSummary): String {
    val foods = if (summary.foodCount == 1) "1 alimento" else "${summary.foodCount} alimentos"
    return "$foods · P ${summary.protein} g · H ${summary.carbs} g · G ${summary.fats} g"
}

/**
 * La hoja «Plantillas» del registro de comidas, con su estado: lista las guardadas, aplica una ([onApply]; la carga como borrador y no
 * guarda nada), la renombra o la borra. [rename] y [delete] esperan a Room: si fallan, la hoja lo dice y no cambia la lista.
 */
@Composable
fun TemplatesSheet(
    templates: List<MealTemplate>,
    onApply: (MealTemplate) -> Unit,
    rename: suspend (MealTemplate, String) -> Unit,
    delete: suspend (MealTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    val usable = remember(templates) { MealReuse.usableTemplates(templates) }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val rows = usable.map { template ->
        TemplateRowState(
            summary = MealReuse.summarize(template),
            renaming = renamingId == template.id,
            renameText = if (renamingId == template.id) renameText else "",
            renameError = if (renamingId == template.id) renameError else null,
            confirmingDelete = deletingId == template.id,
        )
    }
    fun byId(id: String) = usable.firstOrNull { it.id == id }
    KpknSheet(onDismissRequest = onDismiss) {
        TemplatesContent(
            rows = rows,
            failure = failure,
            busy = busy,
            onApply = { id -> byId(id)?.let(onApply) },
            onStartRename = { id ->
                byId(id)?.let {
                    renamingId = id
                    renameText = it.name
                    renameError = null
                    deletingId = null
                    failure = null
                }
            },
            onRenameTextChange = {
                renameText = it
                renameError = null
            },
            onConfirmRename = { id ->
                val template = byId(id) ?: return@TemplatesContent
                val renamed = MealReuse.renameTemplate(template, renameText)
                if (renamed == null) {
                    renameError = (MealReuse.validateTemplateName(renameText) as? MealReuse.TemplateNameCheck.Invalid)?.message ?: "Nombre no válido."
                } else if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            rename(template, renamed.name)
                            renamingId = null
                            failure = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failure = "No se pudo renombrar la plantilla. Inténtalo de nuevo."
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            onCancelRename = { renamingId = null },
            onAskDelete = { id ->
                deletingId = id
                renamingId = null
                failure = null
            },
            onConfirmDelete = { id ->
                val template = byId(id)
                if (template != null && !busy) {
                    busy = true
                    scope.launch {
                        try {
                            delete(template)
                            deletingId = null
                            failure = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failure = "No se pudo eliminar la plantilla. Inténtalo de nuevo."
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            onCancelDelete = { deletingId = null },
        )
    }
}

/** El contenido de la hoja, sin estado: la hoja de render lo pinta vacío y con datos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplatesContent(
    rows: List<TemplateRowState>,
    failure: String?,
    busy: Boolean,
    onApply: (String) -> Unit,
    onStartRename: (String) -> Unit,
    onRenameTextChange: (String) -> Unit,
    onConfirmRename: (String) -> Unit,
    onCancelRename: () -> Unit,
    onAskDelete: (String) -> Unit,
    onConfirmDelete: (String) -> Unit,
    onCancelDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("templates-root"),
    ) {
        Text("Plantillas", style = KpknType.title, color = WizardColors.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(
            "Elige una para cargarla como borrador: podrás cambiar las cantidades antes de guardar.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        if (rows.isEmpty()) {
            KpknEmptyState(
                icon = KpknLineIcons.Layers,
                title = "Aún no tienes plantillas",
                body = "Mantén pulsada una comida del diario y elige «Guardar como plantilla» para reutilizarla aquí.",
            )
        } else {
            rows.forEachIndexed { index, row ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
                TemplateRow(
                    row = row,
                    busy = busy,
                    onApply = onApply,
                    onStartRename = onStartRename,
                    onRenameTextChange = onRenameTextChange,
                    onConfirmRename = onConfirmRename,
                    onCancelRename = onCancelRename,
                    onAskDelete = onAskDelete,
                    onConfirmDelete = onConfirmDelete,
                    onCancelDelete = onCancelDelete,
                )
            }
        }
        failure?.let {
            Spacer(Modifier.height(12.dp))
            KpknModule(Modifier.fillMaxWidth().testTag("templates-failure"), accent = WizardColors.danger) {
                Text(it, style = KpknType.bodyStrong, color = WizardColors.text)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateRow(
    row: TemplateRowState,
    busy: Boolean,
    onApply: (String) -> Unit,
    onStartRename: (String) -> Unit,
    onRenameTextChange: (String) -> Unit,
    onConfirmRename: (String) -> Unit,
    onCancelRename: () -> Unit,
    onAskDelete: (String) -> Unit,
    onConfirmDelete: (String) -> Unit,
    onCancelDelete: () -> Unit,
) {
    val summary = row.summary
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("template-${summary.id}")) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable(onClickLabel = "Usar ${summary.name}", role = Role.Button) { onApply(summary.id) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(summary.name, style = KpknType.bodyStrong, color = WizardColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(templateCaption(summary), style = KpknType.caption, color = WizardColors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            KpknMetric(value = formatKcalEs(summary.kcal), unit = "kcal", style = KpknType.metricM)
        }
        when {
            row.renaming -> {
                KpknTextField(
                    value = row.renameText,
                    onValueChange = onRenameTextChange,
                    modifier = Modifier.testTag("template-rename-field"),
                    label = "Nombre de la plantilla",
                    error = row.renameError,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    KpknTextAction("Guardar nombre", { onConfirmRename(summary.id) }, leadingIcon = KpknLineIcons.Check)
                    KpknTextAction("Cancelar", onCancelRename, color = WizardColors.textMuted)
                }
            }
            row.confirmingDelete -> {
                Text(
                    "¿Eliminar esta plantilla? Las comidas que ya registraste con ella no cambian.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(8.dp))
                KpknDangerPill("Sí, eliminar", { onConfirmDelete(summary.id) }, Modifier.fillMaxWidth(), enabled = !busy)
                Spacer(Modifier.height(8.dp))
                KpknSecondaryPill("Cancelar", onCancelDelete, Modifier.fillMaxWidth())
            }
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                KpknTextAction("Usar", { onApply(summary.id) }, leadingIcon = KpknLineIcons.Plus)
                KpknTextAction("Renombrar", { onStartRename(summary.id) }, color = WizardColors.textMuted, leadingIcon = KpknLineIcons.Edit)
                KpknTextAction("Eliminar", { onAskDelete(summary.id) }, color = WizardColors.textMuted, leadingIcon = KpknLineIcons.Trash)
            }
        }
    }
}
