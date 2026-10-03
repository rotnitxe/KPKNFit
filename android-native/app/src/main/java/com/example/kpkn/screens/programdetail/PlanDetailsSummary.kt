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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2

internal data class ProgramPlanDisplaySummary(
    val provenanceLabel: String,
    val sourceLine: String?,
    val requiredMaterial: String,
    val changes: List<String>,
    val daysPerWeek: String,
    val maxSessionMinutes: Int?,
    val weeklyVolume: String,
    val pendingLoadCount: Int,
)

internal fun buildProgramPlanDisplaySummary(program: Program): ProgramPlanDisplaySummary {
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
        provenanceLabel = provenanceLabel(provenance),
        sourceLine = provenance?.let(::sourceLine)
            ?: program.sourceProtocolId?.let { "Método anterior · $it" }
            ?: program.author?.let { "Autor guardado · $it" },
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

@Composable
internal fun PlanDetailsSummary(program: Program) {
    val summary = remember(program) { buildProgramPlanDisplaySummary(program) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Plan y procedencia", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Text(summary.provenanceLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            summary.sourceLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            SummaryLine("Material requerido", summary.requiredMaterial)
            SummaryLine("Calendario", summary.daysPerWeek)
            SummaryLine("Sesión más larga", summary.maxSessionMinutes?.let { "~$it min (estimación)" } ?: "Sin sesiones materializadas")
            SummaryLine("Volumen real", summary.weeklyVolume)
            if (summary.pendingLoadCount > 0) {
                SummaryLine("Cargas de referencia", "${summary.pendingLoadCount} pendientes de resolver")
            }
            if (summary.changes.isNotEmpty()) {
                Text("Cambios registrados", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                summary.changes.take(4).forEach { change ->
                    Text("• $change", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (summary.changes.size > 4) {
                    Text("+${summary.changes.size - 4} cambios más", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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

private fun provenanceLabel(provenance: PlanProvenance?): String = when (provenance?.category) {
    PlanProvenanceClass.ORIGINAL -> "Original fiel"
    PlanProvenanceClass.ADAPTED -> "Adaptación KPKN"
    PlanProvenanceClass.KPKN -> "Plan KPKN"
    PlanProvenanceClass.LEGACY, null -> "Procedencia no declarada"
}

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
