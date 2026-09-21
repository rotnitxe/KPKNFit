package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryAxialExposure
import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoveryMuscleScope
import com.example.kpkn.data.models.InitialRecoveryResponseState
import com.example.kpkn.data.models.InitialRecoverySensations

enum class RingsCompletion { INCOMPLETE, UNKNOWN, PRESERVE, OMITTED, VALID }

data class SetupRingsInput(
    val startAction: String? = null,
    val recentTraining: Boolean? = null,
    val recentTrainingUnknown: Boolean = false,
    val sessions: Int? = null,
    val recencyDays: Int? = null,
    val activityType: InitialRecoveryActivityType? = null,
    val intensity: InitialRecoveryIntensity? = null,
    val muscleFeeling: Int? = null,
    val energy: Int? = null,
    val structureFeeling: Int? = null,
    val axialState: InitialRecoveryResponseState = InitialRecoveryResponseState.UNKNOWN,
    val axialSessions: Int? = null,
    val axialIntensity: InitialRecoveryIntensity? = null,
    val muscleScope: InitialRecoveryMuscleScope = InitialRecoveryMuscleScope.UNKNOWN,
    val selectedMuscles: List<String> = emptyList(),
    val discomfortIds: List<String> = emptyList(),
    val capturedAtMs: Long? = null,
)

data class SetupRingsMapping(
    val completion: RingsCompletion,
    val evidence: InitialRecoveryEvidence? = null,
    val discomfortIds: List<String> = emptyList(),
)

object SetupRingsMapper {
    fun map(input: SetupRingsInput, nowMs: Long): SetupRingsMapping {
        val action = input.startAction?.trim()?.lowercase()
        if (action == "omit" || action == "remove" || action?.contains("quitar") == true) {
            return SetupRingsMapping(RingsCompletion.OMITTED, discomfortIds = input.discomfortIds.distinct())
        }
        if (action == "keep" || action == "preserve" || action?.contains("conservar") == true || action?.contains("dejar") == true || action == "leave_uncalibrated" || action == "leave_un_calibrated") {
            return SetupRingsMapping(RingsCompletion.PRESERVE, discomfortIds = input.discomfortIds.distinct())
        }
        if (input.recentTrainingUnknown || input.recentTraining == null) return SetupRingsMapping(RingsCompletion.UNKNOWN, discomfortIds = input.discomfortIds.distinct())
        if (input.muscleFeeling == null || input.energy == null || input.structureFeeling == null) {
            return SetupRingsMapping(RingsCompletion.INCOMPLETE, discomfortIds = input.discomfortIds.distinct())
        }
        val recent = input.recentTraining
        val sessions = if (recent == false) 0 else input.sessions?.takeIf { it in 1..7 } ?: return SetupRingsMapping(RingsCompletion.INCOMPLETE, discomfortIds = input.discomfortIds.distinct())
        val recency = if (recent == false) 0 else input.recencyDays?.takeIf { it in 0..6 } ?: return SetupRingsMapping(RingsCompletion.INCOMPLETE, discomfortIds = input.discomfortIds.distinct())
        val type = if (recent == false) InitialRecoveryActivityType.MIXED else input.activityType ?: return SetupRingsMapping(RingsCompletion.INCOMPLETE, discomfortIds = input.discomfortIds.distinct())
        val intensity = if (recent == false) InitialRecoveryIntensity.MODERATE else input.intensity ?: return SetupRingsMapping(RingsCompletion.INCOMPLETE, discomfortIds = input.discomfortIds.distinct())
        val capturedAt = input.capturedAtMs ?: nowMs
        val axial = InitialRecoveryAxialExposure(
            state = input.axialState,
            sessions = if (recent == false) 0 else input.axialSessions,
            intensity = input.axialIntensity,
            recencyDays = if (recent == false) 0 else recency,
        )
        val evidence = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = capturedAt,
            recencyDays = recency,
            sessions = sessions,
            type = type,
            intensity = intensity,
            sensations = InitialRecoverySensations(input.muscleFeeling, input.energy, input.structureFeeling),
            muscleScope = input.muscleScope,
            selectedMuscles = input.selectedMuscles,
            axialExposure = axial,
            activityTypeState = if (recent == false) InitialRecoveryResponseState.DERIVED else InitialRecoveryResponseState.DECLARED,
        )
        return SetupRingsMapping(RingsCompletion.VALID, evidence, input.discomfortIds.distinct())
    }
}
