package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class CompetitionRecordMode {
    TECHNICAL,
    JOURNAL,
    HYBRID,
}

@Serializable
enum class CompetitionTemplateType {
    POWERLIFTING,
    BODYBUILDING,
    WEIGHTLIFTING,
    RUNNING,
    STRONGMAN,
    CROSSFIT,
    MARTIAL_ARTS,
    CUSTOM,
}

@Serializable
enum class CompetitionRecordStatus {
    PLANNED,
    COMPLETED,
    ARCHIVED,
}

@Serializable
enum class CompetitionMovementType {
    SQUAT,
    BENCH,
    DEADLIFT,
    SNATCH,
    CLEAN_AND_JERK,
    PRESS,
    RUN,
    CUSTOM,
}

@Serializable
enum class CompetitionAttemptResult {
    GOOD_LIFT,
    NO_LIFT,
    SKIPPED,
    PENDING,
}

@Serializable
enum class CompetitionEquipment {
    RAW,
    SLEEVES,
    WRAPS,
    EQUIPPED,
    CLASSIC,
    CUSTOM,
}

@Serializable
data class PlannedAttempt(
    val movementType: CompetitionMovementType,
    val attemptNumber: Int,
    val weightKg: Double,
    val basis: String = "",
    /** Escrito a mano en el plan: cambiar de estrategia no lo pisa sin preguntar (Registros v4, c-plan). */
    val manual: Boolean = false,
)

@Serializable
data class CompetitionRecord(
    val id: String,
    val title: String,
    val eventDate: String? = null,
    val startTime: String? = null,
    val sportType: CompetitionTemplateType = CompetitionTemplateType.CUSTOM,
    val recordMode: CompetitionRecordMode = CompetitionRecordMode.HYBRID,
    val status: CompetitionRecordStatus = CompetitionRecordStatus.PLANNED,
    val location: String? = null,
    val federation: String? = null,
    val federationId: String? = null,
    val category: String? = null,
    val bodyweightKg: Double? = null,
    val resultSummary: String? = null,
    val placement: String? = null,
    val medal: String? = null,
    val trophyId: String? = null,
    val notes: String? = null,
    val plannedProgramId: String? = null,
    val plannedSessionId: String? = null,
    val plannedWeekId: String? = null,
    val keyDateId: String? = null,
    val reminderOneWeekEnabled: Boolean = true,
    val reminder48hEnabled: Boolean = true,
    val reminderStartEnabled: Boolean = false,
    val technicalBlocks: List<CompetitionTechnicalBlock> = emptyList(),
    val journal: CompetitionJournal? = null,
    val photos: List<CompetitionPhoto> = emptyList(),
    val customMetrics: List<CustomCompetitionMetric> = emptyList(),
    val powerliftingDetails: PowerliftingCompetitionDetails? = null,
    val bodybuildingDetails: BodybuildingCompetitionDetails? = null,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val plannedAttempts: List<PlannedAttempt> = emptyList(),
    val targetWeightClass: String? = null,
    val targetBodyweightKg: Double? = null,
    val meetResultAppliedAtMs: Long? = null,
    /** Disciplina de la competencia (id de `CompetitionDiscipline`); null en los registros anteriores: se deduce de [sportType]. */
    val discipline: String? = null,
    /** Estrategia del plan de intentos (`AttemptStrategy.name`). */
    val attemptStrategy: String? = null,
    /** «Algo más» del puesto: ids de `CompetitionExtra` (varios). */
    val extras: List<String> = emptyList(),
    /** Participantes de la categoría o del cuadro («2.º de 14»). */
    val fieldSize: Int? = null,
    /** Contra el reloj: distancia en km, tiempo final, objetivo, parciales por km y posiciones. */
    val distanceKm: Double? = null,
    val finishTimeSeconds: Double? = null,
    val targetTimeSeconds: Double? = null,
    val splitsSeconds: List<Double> = emptyList(),
    val overallPlace: Int? = null,
    val overallField: Int? = null,
    val categoryPlace: Int? = null,
    /** Marca en metros (saltos). */
    val markMeters: Double? = null,
    /** Combates y partidos: ronda, resultado y método o marcador. */
    val bouts: List<CompetitionBout> = emptyList(),
    /** Escalada: tops y zonas. */
    val climbing: CompetitionClimbingResult? = null,
    /** Pareja o equipo (pádel, tenis de dobles). */
    val partner: String? = null,
)

@Serializable
data class CompetitionBout(
    val id: String,
    val round: String,
    val won: Boolean,
    val method: String = "",
)

@Serializable
data class CompetitionClimbingResult(
    val topsDone: Int = 0,
    val topsTotal: Int = 0,
    val zonesDone: Int = 0,
    val zonesTotal: Int = 0,
)

@Serializable
data class CompetitionTechnicalBlock(
    val id: String,
    val title: String,
    val movementType: CompetitionMovementType = CompetitionMovementType.CUSTOM,
    val exerciseDbId: String? = null,
    val canonicalExerciseId: String? = null,
    val exerciseName: String? = null,
    val resultUnit: String? = null,
    val attempts: List<CompetitionAttempt> = emptyList(),
    val bestValidWeightKg: Double? = null,
    val bestValidMark: String? = null,
    val notes: String? = null,
)

@Serializable
data class CompetitionAttempt(
    val id: String,
    val attemptNumber: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val distance: Double? = null,
    val timeSeconds: Double? = null,
    val mark: String? = null,
    val resultType: CompetitionAttemptResult = CompetitionAttemptResult.PENDING,
    val invalidReason: String? = null,
    val rpe: Double? = null,
    val technicalNotes: String? = null,
)

@Serializable
data class CompetitionJournal(
    val overallFeeling: String? = null,
    val physicalState: String? = null,
    val mentalState: String? = null,
    val whatWentWell: String? = null,
    val whatWentWrong: String? = null,
    val learnings: String? = null,
    val preparationNotes: String? = null,
    val judgesFeedback: String? = null,
    val personalReflection: String? = null,
)

@Serializable
enum class CompetitionMediaKind {
    PHOTO,
    VIDEO,
}

@Serializable
data class CompetitionPhoto(
    val id: String,
    val uri: String,
    val caption: String? = null,
    val kind: CompetitionMediaKind = CompetitionMediaKind.PHOTO,
)

@Serializable
data class CustomCompetitionMetric(
    val id: String,
    val label: String,
    val value: String,
    val unit: String? = null,
)

@Serializable
data class PowerliftingCompetitionDetails(
    val weightClass: String? = null,
    val division: String? = null,
    val equipment: CompetitionEquipment = CompetitionEquipment.RAW,
    val sexCategory: String? = null,
    val totalKg: Double? = null,
    val ipfGlPoints: Double? = null,
    val dotsPoints: Double? = null,
    val wilksPoints: Double? = null,
    val pointsFormula: String? = null,
)

@Serializable
data class BodybuildingCompetitionDetails(
    val division: String? = null,
    val stageWeightKg: Double? = null,
    val conditionNotes: String? = null,
    val posingNotes: String? = null,
    /** Rondas de la tarima («Prejuicio y final»). */
    val rounds: String? = null,
    val judgesFeedback: String? = null,
    val personalReflection: String? = null,
)
