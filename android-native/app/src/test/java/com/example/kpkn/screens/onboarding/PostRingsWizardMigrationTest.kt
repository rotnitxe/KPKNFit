package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.*
import com.example.kpkn.data.compatibility.LegacyTrainingImport
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.onboarding.SetupDraftScope
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PostRingsWizardMigrationTest {
    @Test fun `no scope or old include switch restores a retired stage`() {
        for(training in listOf(false,true)) for(nutrition in listOf(false,true)) {
            val route=SetupStepGraph.nodes(SetupStepContext(includeTraining=training,includeNutrition=nutrition,includeRings=true))
            assertTrue(route.none { it.id.name.startsWith("RINGS_") || it.id.name=="MILESTONE_RINGS" })
            assertEquals(SetupStepId.REVIEW_ACTIVATE,route.last().id)
        }
    }
    @Test fun `all retired cursors and scopes preserve original draft identity and useful answers`() {
        val retired=listOf("RINGS_INTRO","RINGS_SESSION_DAYS","RINGS_MAIN_LIFTS","RINGS_GROUP_SETS","RINGS_FEELINGS",
            "RINGS_RECENT","RINGS_SESSIONS","RINGS_RECENCY","RINGS_ACTIVITY","RINGS_INTENSITY","RINGS_AXIAL",
            "RINGS_MUSCLE_FEELING","RINGS_ENERGY_FEELING","RINGS_STRUCTURE_FEELING","RINGS_DISCOMFORT","RINGS_RESULT","MILESTONE_RINGS","RINGS_START")
        val json=Json { ignoreUnknownKeys=true;encodeDefaults=true }
        for(step in retired) for(scope in listOf("RINGS_ONLY","full","resume","training_only","nutrition_only")) {
            val original=SetupWizardDraft(draftId="keep",commitId="receipt-keep",name="Ana",ageYears=31,weightKg=70.25,
                includeTraining=false,includeNutrition=false,draftScope=scope)
            val encoded=json.encodeToJsonElement(original).jsonObject
            val raw=JsonObject(encoded + ("stepProgress" to JsonObject(encoded.getValue("stepProgress").jsonObject + mapOf(
                "currentStepId" to JsonPrimitive(step),"block" to JsonPrimitive("RINGS"),
                "visited" to JsonArray(listOf(JsonPrimitive(step))),
                "stalePreviews" to JsonArray(listOf(JsonPrimitive("RINGS_BATTERIES")))))) +
                ("ringsAnswers" to buildJsonObject { put("energyFeeling",3) })).toString()
            val normalized=LegacyTrainingImport.draft(raw)
            assertEquals("normalization must be idempotent",Json.parseToJsonElement(normalized),Json.parseToJsonElement(LegacyTrainingImport.draft(normalized)))
            assertTrue(LegacyTrainingImport.draftOriginals(raw).containsKey("ringsAnswers"))
            val restored=json.decodeFromString<SetupWizardDraft>(normalized)
            val repaired=SetupDraftCompatibility.repair(restored)
            assertEquals("keep",repaired.draftId);assertEquals("receipt-keep",repaired.commitId)
            assertEquals("Ana",repaired.name);assertEquals(70.25,repaired.weightKg!!,0.0)
            assertTrue("$step / $scope: ${repaired.stepProgress.currentStepId}",repaired.stepProgress.currentStepId in SetupStepGraph.stepIds(repaired.stepContext()))
            assertEquals(repaired,SetupDraftCompatibility.repair(repaired))
        }
        assertEquals(SetupDraftScope.LEGACY_RESUME,SetupDraftResolver.scopeOf("RINGS_ONLY"))
    }
}
