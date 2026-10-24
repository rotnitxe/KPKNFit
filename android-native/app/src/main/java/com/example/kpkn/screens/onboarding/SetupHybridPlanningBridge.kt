package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.HybridProgramComposition
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.multisport.HybridCompositionFailure
import com.example.kpkn.domain.multisport.HybridCompositionResult
import com.example.kpkn.domain.multisport.HybridProgramAssembler
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InvalidSetupHybridCompositionException(val reason: HybridCompositionFailure) :
    IllegalArgumentException("No se pudo combinar el plan con las sesiones y restricciones declaradas: $reason")

/** Uses authored/native recipes and the current preview, without introducing a wizard screen. */
object SetupHybridPlanningBridge {
    private val json = Json { encodeDefaults = true }

    fun snapshot(composition: HybridProgramComposition?): HybridProgramComposition? =
        composition?.let { json.decodeFromString<HybridProgramComposition>(json.encodeToString(it)) }

    fun prepare(program: Program, draft: SetupWizardDraft): Program {
        val intent = SetupMultisportIntent.requireValid(draft.trainingIntent ?: program.trainingIntent)
        val source = if (intent == program.trainingIntent) program else program.copy(trainingIntent = intent)
        val composition = draft.hybridComposition ?: return source
        return when (val result = HybridProgramAssembler.assemble(source, composition)) {
            is HybridCompositionResult.Ready -> result.program
            is HybridCompositionResult.Rejected -> throw InvalidSetupHybridCompositionException(result.reason)
        }
    }
}
