package com.example.kpkn.domain.onboarding

import com.example.kpkn.domain.training.PersonalizationReport

/**
 * F-A2 (consolidación 2026-10-01): traduce el informe del fitter nativo
 * ([PersonalizationReport.reasonCode] + [PersonalizationReport.maxSessionMinutes])
 * al fallo TIPADO del evaluador (§15.2). Antes el ViewModel descartaba ambos
 * campos y reconstruía la etapa leyendo el texto del mensaje; el aviso «Este
 * plan necesita N min» no podía salir porque el fitter no escribe «estima N min».
 *
 * Mapeo cerrado (el mismo que ya aplicaba `PlanGenerationCoverageT006Test`):
 *
 * | `reasonCode`        | etapa              | motivo             |
 * |---------------------|--------------------|--------------------|
 * | `TIME_BUDGET`       | `SESSION_DURATION` | `TIME_BUDGET` + `requiredMinutes = maxSessionMinutes` |
 * | `APPARATUS_ABSENT`  | `MATERIAL`         | `APPARATUS_ABSENT` + `missingRequirements` (paquete A · B1) |
 * | `APPARATUS_UNKNOWN` | `MATERIAL`         | `APPARATUS_UNKNOWN` + `missingRequirements` (paquete A · B1) |
 * | `PROFILE_MISMATCH`  | `PROFILE`          | `PROFILE_MISMATCH` |
 * | `COMPOSITION`       | `COMPOSITION`      | `COMPOSITION`      |
 * | `SPLIT`             | `FREQUENCY_SPLIT`  | `SPLIT` (paquete A · E2) |
 *
 * `missingRequirements` son los tokens de material (`rack`, `bench`, `barbell`…) que el fitter
 * negó (`APPARATUS_ABSENT`) o no pudo confirmar (`APPARATUS_UNKNOWN`); la UI los traduce a la llave
 * del panel con `SetupApparatusPanel.keyForToken` y ya no necesita leer el texto del mensaje.
 *
 * Devuelve `null` cuando el informe no trae un motivo cerrado conocido: el
 * llamador conserva entonces su `SetupCandidateFailureException` heredada
 * (clasificación por texto), nunca un motivo inventado.
 */
object NativePlanFailureMapper {

    fun typedFailure(report: PersonalizationReport): PlanMaterializationException? {
        val message = report.limitations.joinToString(" ").ifBlank { DEFAULT_MESSAGE }
        return when (report.reasonCode) {
            "TIME_BUDGET" -> PlanMaterializationException(
                PlanEvaluationStage.SESSION_DURATION,
                PlanRejectionReason.TIME_BUDGET,
                message,
                requiredMinutes = report.maxSessionMinutes,
            )
            "APPARATUS_ABSENT" -> PlanMaterializationException(
                PlanEvaluationStage.MATERIAL,
                PlanRejectionReason.APPARATUS_ABSENT,
                message,
                missingRequirements = report.missingRequirements,
            )
            "APPARATUS_UNKNOWN" -> PlanMaterializationException(
                PlanEvaluationStage.MATERIAL,
                PlanRejectionReason.APPARATUS_UNKNOWN,
                message,
                missingRequirements = report.missingRequirements,
            )
            "PROFILE_MISMATCH" -> PlanMaterializationException(
                PlanEvaluationStage.PROFILE,
                PlanRejectionReason.PROFILE_MISMATCH,
                message,
            )
            "COMPOSITION" -> PlanMaterializationException(
                PlanEvaluationStage.COMPOSITION,
                PlanRejectionReason.COMPOSITION,
                message,
            )
            // Paquete A · E2: el reparto elegido no es el equivalente del calendario del plan (o el generador
            // histórico no lo puede cumplir). Se repara quitando el reparto (`PlanRepair.ClearSplit`).
            "SPLIT" -> PlanMaterializationException(
                PlanEvaluationStage.FREQUENCY_SPLIT,
                PlanRejectionReason.SPLIT,
                message,
            )
            else -> null
        }
    }

    /** Mensaje cuando el informe no trae ninguna limitación escrita. */
    const val DEFAULT_MESSAGE = "El plan nativo no produjo programa"
}
