package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.text.Normalizer

/** Lo que la hoja necesita del ejercicio que se reemplaza. */
internal data class LiveReplaceTarget(
    val name: String,
    val catalogId: String?,
    val doneSets: Int,
    val pendingSets: Int,
    val planSummary: String?,
    val restSeconds: Int?,
)

private val ReplaceBody = TextStyle(fontFamily = WizardFonts.body)
private val ReplaceSyne = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold)

private fun String.searchKey(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/**
 * Candidatos de `replace`: con búsqueda, los del catálogo cuyo nombre o alias la contienen; sin ella, los que comparten más músculos
 * principales con el ejercicio (mismo tipo de fuerza primero). Nunca el propio ejercicio.
 */
internal fun liveReplaceCandidates(catalog: List<ExerciseMuscleInfo>, targetId: String?, query: String, limit: Int = 12): List<ExerciseMuscleInfo> {
    val key = query.trim().searchKey()
    val pool = catalog.filter { it.id != targetId }
    if (key.isNotEmpty()) {
        return pool.filter { info -> info.name.searchKey().contains(key) || info.alias?.searchKey()?.contains(key) == true }
            .sortedBy { if (it.name.searchKey().startsWith(key)) 0 else 1 }
            .take(limit)
    }
    val target = catalog.firstOrNull { it.id == targetId } ?: return emptyList()
    val muscles = target.involvedMuscles.filter { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY }.map { it.muscle }.toSet()
    if (muscles.isEmpty()) return emptyList()
    return pool.map { info -> info to info.involvedMuscles.count { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY && it.muscle in muscles } }
        .filter { it.second > 0 }
        .sortedWith(compareByDescending<Pair<ExerciseMuscleInfo, Int>> { it.second }.thenBy { if (it.first.force == target.force) 0 else 1 })
        .take(limit)
        .map { it.first }
}

/**
 * «Reemplazar ejercicio» del prototipo 02 (`replace` → `replace-preview`): una hoja, no la página del catálogo. Buscar, elegir un
 * candidato («revisar antes de aplicar»), «YA REALIZADO · N series se conservan» y, al elegir, «Revisar reemplazo» con lo que se
 * conserva del plan, lo que es propio del reemplazo, «Aplicar reemplazo» y «Elegir otro ejercicio». El catálogo completo queda a un toque.
 */
@Composable
internal fun LiveReplaceSheet(
    target: LiveReplaceTarget,
    catalog: List<ExerciseMuscleInfo>,
    onApply: (ExerciseMuscleInfo) -> Unit,
    onOpenFullCatalog: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf<ExerciseMuscleInfo?>(null) }
    val candidates = remember(catalog, target.catalogId, query) { liveReplaceCandidates(catalog, target.catalogId, query) }
    val picked = choice
    LiveSheet(
        title = if (picked == null) "Reemplazar ejercicio" else "Revisar reemplazo",
        onDismiss = onDismiss,
        tag = if (picked == null) "live-replace-sheet" else "live-replace-preview",
        footer = null,
    ) { close ->
        if (picked == null) {
            LiveSheetSubtitle("Reemplazar el trabajo pendiente de ${target.name}.")
            LiveSheetLabeledField("Buscar ejercicio", query, { query = it }, placeholder = "Nombre del ejercicio", tag = "live-replace-search")
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                candidates.forEach { info -> ReplacementRow(info) { choice = info } }
                if (candidates.isEmpty()) {
                    LiveSheetShortCopy(if (query.isBlank()) "Escribe el nombre del ejercicio que quieres usar." else "Sin resultados para «$query».")
                }
            }
            ReplaceHistoryNote("YA REALIZADO", "${target.doneSets} ${if (target.doneSets == 1) "serie se conserva" else "series se conservan"}")
            LiveSheetTextAction("Abrir el catálogo completo", onOpenFullCatalog, Modifier.testTag("live-replace-full-catalog"))
        } else {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp)) {
                Text("REEMPLAZO PARA LO PENDIENTE", style = ReplaceBody.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = LiveSheetTokens.Faint)
                Text(picked.name, Modifier.padding(vertical = 12.dp), style = ReplaceSyne.copy(fontSize = 30.sp, lineHeight = 39.sp),
                    color = WizardColors.text)
                Text("Desde ${target.name}", style = ReplaceBody.copy(fontSize = 12.sp, lineHeight = 17.sp), color = LiveSheetTokens.Faint)
            }
            InheritanceBlock(
                "Se conserva del plan",
                buildString {
                    append("${target.pendingSets} ${if (target.pendingSets == 1) "serie" else "series"}")
                    target.planSummary?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    target.restSeconds?.takeIf { it > 0 }?.let { append("\nDescanso · ${it / 60}:${(it % 60).toString().padStart(2, '0')}") }
                },
            )
            InheritanceBlock("Contexto propio del reemplazo", "Carga e historial · etiquetas · setup · técnicas · lados y ROM")
            LiveSheetShortCopy("La carga se elegirá con la referencia del nuevo ejercicio. Las series ya realizadas quedan asociadas al ejercicio original.")
            LiveSheetPrimary("Aplicar reemplazo", { onApply(picked); close() }, Modifier.padding(top = 12.dp), tag = "live-replace-apply")
            LiveSheetTextAction("Elegir otro ejercicio", { choice = null })
        }
    }
}

/** `.v2-replacements .menu-row`: icono en caja de 40, nombre 13, «Máquina · revisar antes de aplicar» y ›. */
@Composable
private fun ReplacementRow(info: ExerciseMuscleInfo, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).drawBehind {
            drawLine(LiveSheetTokens.Line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }.clickable(role = Role.Button, onClick = onClick).testTag("live-replace-option-${info.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x07FFFFFF)), contentAlignment = Alignment.Center) {
            Icon(KpknLineIcons.Swap, null, Modifier.size(23.dp), tint = WizardColors.text)
        }
        Column(Modifier.weight(1f)) {
            Text(info.name, style = ReplaceBody.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold), color = WizardColors.text)
            Text(listOfNotNull(info.equipment?.takeIf { it.isNotBlank() }, "revisar antes de aplicar").joinToString(" · "), Modifier.padding(top = 7.dp),
                style = ReplaceBody.copy(fontSize = 10.sp, lineHeight = 14.sp), color = LiveSheetTokens.Faint)
        }
        Icon(KpknLineIcons.ChevronRight, null, Modifier.size(15.dp), tint = LiveSheetTokens.Faint)
    }
}

/** `.v2-history-note`: filete de 2 a la izquierda, rótulo de 9 y la cifra en Syne 17. */
@Composable
private fun ReplaceHistoryNote(label: String, value: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 20.dp).drawBehind {
            drawLine(Color(0x2DFFFFFF), Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 2.dp.toPx())
        }.padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Text(label, style = ReplaceBody.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = LiveSheetTokens.Faint)
        Text(value, Modifier.padding(top = 8.dp), style = ReplaceSyne.copy(fontSize = 17.sp, lineHeight = 23.sp), color = WizardColors.text)
    }
}

/** `.v2-inheritance > div`: filete superior, título 13/600 y el texto de 12 con interlineado 1,8. */
@Composable
private fun InheritanceBlock(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(Color(0x16FFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }.padding(vertical = 17.dp),
    ) {
        Text(title, style = ReplaceBody.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold), color = WizardColors.text)
        Text(body, Modifier.padding(top = 12.dp), style = ReplaceBody.copy(fontSize = 12.sp, lineHeight = 22.sp), color = LiveSheetTokens.Muted)
    }
}
