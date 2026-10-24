package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.compatibility.LegacyTrainingImport
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class WizChatRetirementTest {
    @Test fun retiredChatCursorAndCompletedStageMigrateWithoutLosingOtherAnswers() {
        val raw = """{"draftScope":"full","wizChat":{"stage":"RINGS","currentQuestionId":"R_RECENT","completedStages":["PROFILE","TRAINING","RINGS"],"acceptedAnswers":[{"questionId":"P_NAME","textValue":"Nombre sintético"},{"questionId":"R_RECENT","textValue":"No lo sé"}]}}"""
        val migrated = LegacyTrainingImport.draft(raw)
        assertEquals(migrated, LegacyTrainingImport.draft(migrated))
        val chat = Json.parseToJsonElement(migrated).jsonObject.getValue("wizChat").jsonObject
        assertEquals("REVIEW", chat.getValue("stage").jsonPrimitive.content)
        assertEquals("REVIEW", chat.getValue("currentQuestionId").jsonPrimitive.content)
        assertEquals(listOf("PROFILE", "TRAINING"), chat.getValue("completedStages").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("Nombre sintético", chat.getValue("acceptedAnswers").jsonArray.single().jsonObject.getValue("textValue").jsonPrimitive.content)
    }
    @Test fun everyActiveConversationScopeHasNoCalibrationStage() {
        for (training in listOf(false, true)) for (nutrition in listOf(false, true)) {
            val context = WizChatGraphContext(includeTraining = training, includeNutrition = nutrition)
            var current: WizChatQuestionId? = WizChatGraph.firstFor(context)
            val visited = mutableSetOf<WizChatQuestionId>()
            while (current != null) {
                assertTrue("No loop: $current", visited.add(current))
                assertFalse(current.name.startsWith("R_"))
                assertNotNull(WizChatGraph.question(current))
                current = WizChatGraph.next(current, context)
            }
            assertTrue(WizChatQuestionId.REVIEW in visited)
        }
    }
}
