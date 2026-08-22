package com.example.kpkn.screens.sessioneditor

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.calculations.resolveReferenceCapacity

internal fun String.safeIntOrNull(): Int? = toIntOrNull()

internal fun String.safeDoubleOrNull(): Double? = replace(",", ".").toDoubleOrNull()

internal fun formatEditableNumber(value: Double?): String {
    if (value == null) return ""
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
}

internal fun String.toEditorColor(default: Color = Color(0xFF6E8A95)): Color =
    resolvePartAccent(this).primary

internal fun formatEditorOneDecimal(value: Double): String = "%.1f".format(value)

internal fun dayInitial(dayOfWeek: Int?): String = when (dayOfWeek) {
    1 -> "L"
    2 -> "M"
    3 -> "X"
    4 -> "J"
    5 -> "V"
    6 -> "S"
    7 -> "D"
    else -> "?"
}

internal fun formatRestSummary(restTime: Int?): String {
    val total = restTime ?: 90
    val minutes = total / 60
    val seconds = total % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

internal fun formatExerciseCollapsedSummary(exercise: Exercise): String? {
    if (exercise.sets.isEmpty()) return null
    val reps = exercise.sets.mapNotNull { it.effectiveRepRange()?.format() }.distinct()
    val loads = exercise.sets.mapNotNull { it.weight }.distinct()
    val repsPart = when {
        reps.size == 1 -> "${exercise.sets.size}×${reps.first()}"
        else -> "${exercise.sets.size} series"
    }
    val loadPart = when {
        loads.size == 1 -> " @ ${formatEditableNumber(loads.first())}kg"
        else -> ""
    }
    return repsPart + loadPart
}

/** Resumen corto de la config del ejercicio (chips / header expandido). */
internal fun formatExerciseConfigSummary(exercise: Exercise): String {
    val parts = buildList {
        add(trainingModeLabel(exercise.trainingMode))
        if (exercise.isEffectivelyUnilateral()) {
            add("Unilat.")
            add(
                if (exercise.unilateralIntensityMode == UnilateralIntensityMode.SHARED) {
                    "Lados iguales"
                } else {
                    "Lados aparte"
                },
            )
            exercise.restBetweenSidesSeconds?.takeIf { it > 0 }?.let {
                add("Entre lados ${formatRestSummary(it)}")
            }
        }
        if (!exercise.isInSuperset()) {
            exercise.restTime?.let { add("Rest ${formatRestSummary(it)}") }
        }
        if (exercise.isStarTarget) add("Meta")
        exercise.goal1RM?.takeIf { it > 0 }?.let { add("Meta ${formatEditableNumber(it)}kg") }
        resolveReferenceCapacity(exercise)?.takeIf { it > 0 }?.let {
            add("Ref ${formatEditableNumber(it)}kg")
        }
        if (exercise.warmupSets.isNotEmpty()) add("${exercise.warmupSets.size} aprox")
        if (exercise.mobilitySeries.isNotEmpty()) add("Movilidad")
        if (exercise.trackRom) add("ROM")
        exercise.relativeToCanonicalExerciseId?.let { add("Relacionado") }
    }
    return parts.joinToString(" · ")
}

internal fun trainingModeLabel(mode: TrainingMode): String = when (mode) {
    TrainingMode.REPS -> "Reps"
    TrainingMode.TIME -> "Tiempo"
    TrainingMode.RM -> "RM"
    TrainingMode.CUSTOM -> "Personalizado"
    TrainingMode.DISTANCE -> "Distancia"
    TrainingMode.SOLO_RPE -> "Solo RPE"
    TrainingMode.AMRAP -> "AMRAP"
}

internal data class SessionCoverGradient(
    val id: String,
    val name: String,
    val colors: List<Color>,
)

internal val sessionGradients = listOf(
    SessionCoverGradient("gradient://ember", "Ember", listOf(Color(0xFF20110F), Color(0xFF8D3D2E), Color(0xFFE08E45))),
    SessionCoverGradient("gradient://lagoon", "Lagoon", listOf(Color(0xFF0D1B2A), Color(0xFF1B4965), Color(0xFF5FA8D3))),
    SessionCoverGradient("gradient://velvet", "Velvet", listOf(Color(0xFF1C1024), Color(0xFF5B2A86), Color(0xFFE26D5A))),
    SessionCoverGradient("gradient://forest", "Forest", listOf(Color(0xFF102A1F), Color(0xFF2D6A4F), Color(0xFF95D5B2))),
    SessionCoverGradient("gradient://graphite", "Graphite", listOf(Color(0xFF09090B), Color(0xFF27272A), Color(0xFF52525B))),
    SessionCoverGradient("gradient://steel-blue", "Steel Blue", listOf(Color(0xFF0F172A), Color(0xFF1E3A5F), Color(0xFF38BDF8))),
    SessionCoverGradient("gradient://deep-red", "Deep Red", listOf(Color(0xFF120607), Color(0xFF7F1D1D), Color(0xFFEF4444))),
    SessionCoverGradient("gradient://mint-night", "Mint Night", listOf(Color(0xFF07130F), Color(0xFF14532D), Color(0xFF34D399))),
    SessionCoverGradient("gradient://indigo", "Indigo", listOf(Color(0xFF111827), Color(0xFF3730A3), Color(0xFF818CF8))),
    SessionCoverGradient("gradient://bronze", "Bronze", listOf(Color(0xFF15100A), Color(0xFF92400E), Color(0xFFF59E0B))),
)

internal val sessionSolidPresets = listOf(
    SessionCoverGradient("solid://obsidian", "Obsidian", listOf(Color(0xFF111318), Color(0xFF111318), Color(0xFF111318))),
    SessionCoverGradient("solid://steel", "Steel", listOf(Color(0xFF334155), Color(0xFF334155), Color(0xFF334155))),
    SessionCoverGradient("solid://ember-red", "Ember Red", listOf(Color(0xFF7F1D1D), Color(0xFF7F1D1D), Color(0xFF7F1D1D))),
    SessionCoverGradient("solid://ocean", "Ocean", listOf(Color(0xFF0F3D5E), Color(0xFF0F3D5E), Color(0xFF0F3D5E))),
    SessionCoverGradient("solid://moss", "Moss", listOf(Color(0xFF244B3C), Color(0xFF244B3C), Color(0xFF244B3C))),
    SessionCoverGradient("solid://charcoal", "Charcoal", listOf(Color(0xFF1F2329), Color(0xFF1F2329), Color(0xFF1F2329))),
    SessionCoverGradient("solid://slate", "Slate", listOf(Color(0xFF283241), Color(0xFF283241), Color(0xFF283241))),
    SessionCoverGradient("solid://wine", "Wine", listOf(Color(0xFF581C27), Color(0xFF581C27), Color(0xFF581C27))),
    SessionCoverGradient("solid://pine", "Pine", listOf(Color(0xFF12352A), Color(0xFF12352A), Color(0xFF12352A))),
    SessionCoverGradient("solid://navy", "Navy", listOf(Color(0xFF10233F), Color(0xFF10233F), Color(0xFF10233F))),
    SessionCoverGradient("solid://aubergine", "Aubergine", listOf(Color(0xFF2A1835), Color(0xFF2A1835), Color(0xFF2A1835))),
)

internal val sessionBackgroundPresets = sessionGradients + sessionSolidPresets

internal fun SessionPart.isEditorUncategorized(): Boolean =
    name.trim().lowercase() in setOf("sin categoría", "sin categoria", "sin grupo")

internal fun resolveRelationshipAnchorName(
    session: Session,
    exercise: Exercise,
): String? {
    val anchorId = exercise.relativeToCanonicalExerciseId ?: return null
    return session.allExercises()
        .firstOrNull { candidate ->
            candidate.id != exercise.id && candidate.resolvedCanonicalExerciseId() == anchorId
        }
        ?.name
        ?: anchorId
}

internal fun formatHistoryTimestamp(timestampMs: Long): String {
    return runCatching {
        SimpleDateFormat("dd MMM · HH:mm", Locale.forLanguageTag("es-ES")).format(Date(timestampMs))
    }.getOrDefault("Momento desconocido")
}

internal fun sessionEditorDayLabel(dayOfWeek: Int?): String = when (dayOfWeek) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "Sin día"
}

internal fun sessionEditorDayLabelShort(dayOfWeek: Int?): String = when (dayOfWeek) {
    1 -> "Lun"
    2 -> "Mar"
    3 -> "Mié"
    4 -> "Jue"
    5 -> "Vie"
    6 -> "Sáb"
    7 -> "Dom"
     else -> "?"
 }

internal fun smartReferenceMetricLabel(mode: TrainingMode, customUnit: String?): String = when (mode) {
    TrainingMode.REPS,
    TrainingMode.RM,
    -> "Reps base"
    TrainingMode.TIME -> "Tiempo base"
    TrainingMode.DISTANCE -> "Dist. base"
    TrainingMode.CUSTOM -> "${customUnit?.ifBlank { "Unidad" } ?: "Unidad"} base"
    TrainingMode.SOLO_RPE -> "Base"
    TrainingMode.AMRAP -> "AMRAP"
}

internal fun estimatedMetricLabel(mode: TrainingMode, customUnit: String?): String = when (mode) {
    TrainingMode.REPS,
    TrainingMode.RM,
    -> "Reps est."
    TrainingMode.TIME -> "Tiempo est."
    TrainingMode.DISTANCE -> "Dist. est."
    TrainingMode.CUSTOM -> "${customUnit?.ifBlank { "Unidad" } ?: "Unidad"} est."
    TrainingMode.SOLO_RPE -> "RPE"
    TrainingMode.AMRAP -> "AMRAP"
}

internal fun formatEstimatedMetric(value: Double?, mode: TrainingMode, customUnit: String?): String {
    if (value == null) return "-"
    return when (mode) {
        TrainingMode.TIME -> "${value.toInt()}s"
        TrainingMode.CUSTOM -> formatEditableNumber(value)
        else -> value.toInt().toString()
    }
}

internal data class SessionMuscleGroup(val label: String, val muscles: List<String>)

internal enum class SessionAnalyticsScope(val label: String) {
    CURRENT("Sesión actual"),
    WEEK("Semana"),
}

internal val SESSION_MUSCLE_GROUPS = listOf(
    SessionMuscleGroup("Pecho", listOf("Pectorales")),
    SessionMuscleGroup("Espalda", listOf("Dorsales", "Trapecio", "Erectores Espinales")),
    SessionMuscleGroup("Hombros", listOf("Deltoides")),
    SessionMuscleGroup("Brazos", listOf("Bíceps", "Tríceps", "Antebrazo")),
    SessionMuscleGroup("Core", listOf("Abdomen", "Core")),
    SessionMuscleGroup("Piernas", listOf("Cuádriceps", "Isquiosurales", "Glúteos", "Aductores", "Pantorrillas")),
)
