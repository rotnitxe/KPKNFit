package com.example.kpkn.screens.programdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanLabels
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.programs.PlanInfoModelBuilder

/**
 * Lo que la tarjeta «Plan y procedencia» del detalle del programa enseña, ya redactado en español llano.
 * Se calcula con [buildProgramPlanDisplaySummary], una función pura (sin Compose) que se prueba en JVM.
 *
 * @property catalogEntry entrada del catálogo a la que pertenece el programa; null si lo creó el usuario a mano
 *   o si viene de un método que ya no está en el catálogo. Con ella la tarjeta ofrece «Ver cómo funciona».
 * @property planName el título editorial del plan; sin entrada, el nombre del propio programa.
 * @property summary «Qué haces»: el resumen editorial de la entrada; null sin entrada.
 * @property planNotes notas del plan en lenguaje llano (las líneas que siguen a la descripción del programa).
 */
internal data class ProgramPlanDisplaySummary(
    val catalogEntry: CatalogEntry?,
    val planName: String,
    val provenanceLabel: String,
    val sourceLine: String?,
    val summary: String?,
    val planNotes: List<String>,
    val requiredMaterial: String,
    val changes: List<String>,
    val daysPerWeek: String,
    val maxSessionMinutes: Int?,
    val weeklyVolume: String,
    val pendingLoadCount: Int,
)

/** Texto que ocupa el lugar de un dato que falta («—»). */
private const val MISSING_VALUE = "—"

/** Las notas del plan que se enseñan antes de resumir el resto en «+N notas más». */
private const val MAX_PLAN_NOTES = 6

/** Los cambios registrados que se enseñan antes de resumir el resto en «+N cambios más». */
private const val MAX_CHANGES = 4

internal fun buildProgramPlanDisplaySummary(program: Program): ProgramPlanDisplaySummary {
    val entry = PersonalizedPlanCatalog.findForProgram(program)
    val provenance = program.planProvenance ?: program.sourceRecipe?.provenance
    val weeks = program.macrocycles.flatMap { macro ->
        macro.blocks.flatMap { block -> block.mesocycles.flatMap { it.weeks } }
    }
    val sessions = weeks.flatMap { it.sessions }
    val material = requiredMaterial(program, sessions.flatMap { it.allExercises().mapNotNull { exercise -> exercise.catalogConfigurationId } }.toSet())
    val dayCounts = weeks.mapNotNull { week ->
        week.sessions.mapNotNull { session -> session.dayOfWeek }.toSet().takeIf { it.isNotEmpty() }?.size
    }
    val weekVolumes = weeks.mapNotNull(::materializedWorkingSetCount)
    val durations = sessions.map(::estimatedSessionDurationMinutes).filter { it > 0 }
    val pendingLoads = program.exerciseLoadReferences.sumOf { loadRef ->
        loadRef.references.count { it.state == com.example.kpkn.data.protocols.PlanLoadReferenceState.PENDING }
    }
    return ProgramPlanDisplaySummary(
        catalogEntry = entry,
        planName = entry?.displayName ?: program.name,
        provenanceLabel = entry?.let(PlanLabels::provenanceLabel) ?: provenanceLabelWithoutEntry(program, provenance),
        sourceLine = if (entry != null) {
            entry.attributionLine?.takeIf { it.isNotBlank() } ?: provenance?.let(::sourceLine)
        } else {
            provenance?.let(::sourceLine) ?: authorLine(program)
        },
        summary = entry?.summary?.takeIf { it.isNotBlank() },
        planNotes = planNotesOf(program.description, entry),
        requiredMaterial = material,
        changes = provenance?.slotChanges.orEmpty().mapNotNull { change ->
            val from = change.fromConfigurationId?.let(::shortConfigurationName)
            val to = change.toConfigurationId?.let(::shortConfigurationName)
            val transition = when {
                from != null && to != null -> "$from → $to"
                to != null -> "Configuración: $to"
                else -> null
            }
            transition?.let { "$it · ${change.reason.ifBlank { "cambio registrado" }}" }
        },
        daysPerWeek = dayCounts.rangeLabel("días/semana"),
        maxSessionMinutes = durations.maxOrNull(),
        weeklyVolume = weekVolumes.rangeLabel("series materializadas/semana"),
        pendingLoadCount = pendingLoads,
    )
}

/**
 * Texto del chip ámbar del encabezado del detalle: el nombre corto del plan del catálogo (sin los días y sin
 * emoji) o, si el programa viene de un protocolo que el catálogo ya no ofrece, el nombre de ese protocolo.
 * Null cuando el programa no viene de ningún método.
 */
internal fun planChipLabel(program: Program): String? =
    PersonalizedPlanCatalog.findForProgram(program)?.shortName
        ?: program.sourceProtocolId?.let { id -> PROTOCOL_LIBRARY.firstOrNull { it.id == id }?.name }

/**
 * Las notas de un plan: lo que el programa guarda en su descripción después de la descripción misma. Los
 * personalizadores escriben `{resumen}` + una línea por nota; el resumen ya lo cuenta la sección «Qué haces», y
 * el banner del encabezado solo deja ver dos líneas, así que las notas no se leen en ningún otro sitio.
 *
 * - Se descartan el resumen de la entrada y la descripción heredada de ella (aunque ocupen varias líneas), las
 *   líneas vacías y las repetidas, y se conserva el orden.
 * - Si la descripción no contiene ninguno de esos textos (el programa se creó con una versión anterior de la
 *   entrada, o el usuario reescribió la descripción), su primera línea sigue siendo la descripción y tampoco
 *   es una nota. Sin entrada ocurre siempre así.
 */
@Suppress("DEPRECATION")
internal fun planNotesOf(description: String?, entry: CatalogEntry?): List<String> {
    val text = description?.replace("\r\n", "\n")?.takeIf { it.isNotBlank() } ?: return emptyList()
    val inherited = listOfNotNull(entry?.summary, entry?.description)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedByDescending { it.length }
    var rest = text
    var inheritedFound = false
    inherited.forEach { known ->
        if (rest.contains(known)) {
            rest = rest.replace(known, "\n")
            inheritedFound = true
        }
    }
    val lines = rest.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return (if (inheritedFound) lines else lines.drop(1)).distinct()
}

/**
 * Etiqueta de procedencia de un programa que no resuelve a ninguna entrada del catálogo. La clase declarada por
 * la procedencia manda; sin ella se dice de dónde viene lo poco que se sabe, sin mostrar nunca un identificador.
 */
private fun provenanceLabelWithoutEntry(program: Program, provenance: PlanProvenance?): String =
    when (provenance?.category) {
        PlanProvenanceClass.ORIGINAL -> "Original fiel"
        PlanProvenanceClass.ADAPTED -> "Adaptación KPKN"
        PlanProvenanceClass.KPKN -> "Plan KPKN"
        PlanProvenanceClass.LEGACY, null -> when {
            // Viene de un método (protocolo o plantilla) que el catálogo ya no ofrece.
            !program.sourceProtocolId.isNullOrBlank() || !program.structureTemplateId.isNullOrBlank() ->
                "Versión anterior de un método"
            !program.author.isNullOrBlank() -> "Programa guardado"
            else -> "Programa creado a mano"
        }
    }

/** «Autor · {nombre}» cuando el programa guarda un autor; los ids de método no se enseñan nunca. */
private fun authorLine(program: Program): String? =
    program.author?.trim()?.takeIf { it.isNotEmpty() }?.let { "Autor · $it" }

@Composable
internal fun PlanDetailsSummary(
    program: Program,
    onOpenPlanInfo: ((CatalogEntry) -> Unit)? = null,
) {
    val summary = remember(program) { buildProgramPlanDisplaySummary(program) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Plan y procedencia", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            PlanNameRow(planName = summary.planName, entry = summary.catalogEntry, onOpenPlanInfo = onOpenPlanInfo)
            Text(summary.provenanceLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            summary.sourceLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            summary.summary?.let { text ->
                Text("Qué haces", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (summary.planNotes.isNotEmpty()) {
                Text("Notas del plan", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                summary.planNotes.take(MAX_PLAN_NOTES).forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (summary.planNotes.size > MAX_PLAN_NOTES) {
                    Text(
                        moreLabel(summary.planNotes.size - MAX_PLAN_NOTES, "nota", "notas"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SummaryLine("Material requerido", summary.requiredMaterial)
            SummaryLine("Calendario", summary.daysPerWeek)
            SummaryLine("Sesión más larga", summary.maxSessionMinutes?.let { "~$it min (estimación)" } ?: "Sin sesiones materializadas")
            SummaryLine("Volumen real", summary.weeklyVolume)
            if (summary.pendingLoadCount > 0) {
                SummaryLine("Cargas de referencia", "${summary.pendingLoadCount} pendientes de resolver")
            }
            if (summary.changes.isNotEmpty()) {
                Text("Cambios registrados", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                summary.changes.take(MAX_CHANGES).forEach { change ->
                    Text("• $change", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (summary.changes.size > MAX_CHANGES) {
                    Text(
                        moreLabel(summary.changes.size - MAX_CHANGES, "cambio", "cambios"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Fila «Plan: {nombre}». Con una entrada del catálogo y un destino para [onOpenPlanInfo], lleva el botón de
 * texto «Ver cómo funciona», que abre la hoja del plan en modo solo lectura.
 */
@Composable
private fun PlanNameRow(
    planName: String,
    entry: CatalogEntry?,
    onOpenPlanInfo: ((CatalogEntry) -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Plan: $planName",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        if (entry != null && onOpenPlanInfo != null) {
            TextButton(
                onClick = { onOpenPlanInfo(entry) },
                modifier = Modifier.testTag("plan_details_info_button"),
            ) {
                Text("Ver cómo funciona", maxLines = 1)
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.weight(0.38f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

/** «+1 nota más», «+3 notas más»: concuerda con la cantidad que se resume. */
private fun moreLabel(remaining: Int, singular: String, plural: String): String =
    "+${SpanishPlurals.withNoun(remaining, singular, plural)} más"

/**
 * Línea de máximos de entrenamiento del detalle: «TM: sentadilla 180 · banca 120 · peso muerto 220 kg».
 * Los enteros van sin decimales, los demás con coma («110,5»), y un máximo que falta se escribe «—». La unidad
 * solo acompaña a la línea si hay al menos un valor.
 */
internal fun trainingMaxLine(profile: PowerliftingProfile?): String {
    val squat = trainingMaxValue(profile?.squatTM)
    val bench = trainingMaxValue(profile?.benchTM)
    val deadlift = trainingMaxValue(profile?.deadliftTM)
    val line = "TM: sentadilla $squat · banca $bench · peso muerto $deadlift"
    return if (listOf(squat, bench, deadlift).any { it != MISSING_VALUE }) "$line kg" else line
}

private fun trainingMaxValue(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let(PlanInfoModelBuilder::formatNumber) ?: MISSING_VALUE

private fun sourceLine(provenance: PlanProvenance): String? = listOfNotNull(
    provenance.sourceTitle,
    provenance.sourceAuthor,
    provenance.sourceEdition,
).filter { it.isNotBlank() }.distinct().joinToString(" · ").ifBlank { null }

private fun requiredMaterial(program: Program, configurationIds: Set<String>): String {
    if (configurationIds.isEmpty()) return "Sin configuraciones catalogadas"
    val catalog = approvedExerciseCatalogV2() ?: return "No disponible: catálogo de configuraciones pendiente"
    val configurations = catalog.families.flatMap { it.definitions }.flatMap { it.configurations }
        .filter { it.id in configurationIds }
    if (configurations.isEmpty()) return "No disponible para las configuraciones materializadas"
    val terms = configurations.flatMap { configuration ->
        configuration.profile.richMetadata?.programming?.requiredEquipment.orEmpty() + configuration.profile.equipmentId
    }.map(::materialLabel).filter { it.isNotBlank() }.distinct().sorted()
    val adaptations = program.planProvenance?.slotChanges.orEmpty()
    val opaqueChangeCount = adaptations.count { it.toConfigurationId != null && it.toConfigurationId !in configurationIds }
    return buildList {
        addAll(terms)
        if (opaqueChangeCount > 0) add("Revisar cambios de configuración")
    }.distinct().joinToString(", ").ifBlank { "Sin material específico declarado" }
}

private fun materialLabel(raw: String): String {
    val token = raw.trim().lowercase().substringAfterLast(':').replace('-', '_').replace(' ', '_')
    return when (token) {
        "barbell", "bar", "barbell_and_plates" -> "barra y discos"
        "dumbbells", "dumbbell" -> "mancuernas"
        "bench", "flat_bench", "incline_bench" -> "banco"
        "rack", "squat_rack", "power_rack" -> "rack"
        "pull_up_bar", "chin_up_bar" -> "barra de dominadas"
        "cable", "pulley" -> "polea"
        "band", "bands", "resistance_band" -> "bandas"
        "bodyweight", "none" -> "peso corporal"
        "machine" -> "máquina específica"
        "smith_machine", "smith" -> "máquina Smith"
        "kettlebell" -> "kettlebell"
        "cardio_machine", "treadmill", "bike" -> "aparato de cardio"
        else -> raw.trim().replace('_', ' ').takeIf { it.isNotEmpty() } ?: ""
    }
}

private fun shortConfigurationName(id: String): String =
    com.example.kpkn.data.exercises.catalogConfigurationDisplayName(id) ?: id.replace('_', ' ')

private fun materializedWorkingSetCount(week: ProgramWeek): Int? = week.sessions
    .takeIf { it.isNotEmpty() }
    ?.sumOf { session ->
        session.allExercises().sumOf { exercise ->
            exercise.sets.count { !it.isEmptySlot && !it.isCalibrator && !it.isIneffective }
        }
    }

private fun estimatedSessionDurationMinutes(session: com.example.kpkn.data.models.Session): Int =
    com.example.kpkn.domain.training.SessionDurationEstimator.estimate(session).totalMinutes

private fun List<Int>.rangeLabel(suffix: String): String = when {
    isEmpty() -> "Sin datos"
    minOrNull() == maxOrNull() -> "${maxOrNull()} $suffix"
    else -> "${minOrNull()}–${maxOrNull()} $suffix"
}
