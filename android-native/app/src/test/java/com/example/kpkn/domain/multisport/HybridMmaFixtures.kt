package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.multisport.combat.*
import com.example.kpkn.domain.multisport.tracking.*
import kotlinx.serialization.json.*

internal object HybridMmaFixtures {
    val plan = CombatSessionPlan(sportId = CombatDiscipline.MMA, blocks = listOf(CombatTrainingBlockPlan(
        id = "class", type = CombatBlockType.EXTERNAL_CLASS,
        rulesetId = "dojo.class.custom", rulesetVersion = 1, protocolId = "technical.class", contextId = "dojo.one",
        declaredContact = CombatDeclaredContact.TECHNICAL, declaredIntensity = CombatDeclaredIntensity.MODERATE,
        declaredExternalDurationSeconds = 3600,
    )))
    val planDocument = SportTrackingDocument("mma-plan", CombatSportEngine.forDiscipline(CombatDiscipline.MMA).id,
        1, SportId.MMA.name, "PLAN", Json.encodeToJsonElement(CombatSessionPlan.serializer(), plan).jsonObject,
        durationContribution = SportDurationContribution.SESSION_TOTAL.name)
    val session = Session("mma-class", "Clase técnica real", placeId = "DOJO", captureMode = SessionCaptureMode.EXTERNAL,
        sportDocuments = listOf(planDocument), targetDurationMinutes = 60)
    val result = CombatSessionResult(sportId = CombatDiscipline.MMA,
        blocks = listOf(CombatTrainingBlockResult("class", CombatBlockType.EXTERNAL_CLASS,
            rulesetId = "dojo.class.custom", rulesetVersion = 1, protocolId = "technical.class", contextId = "dojo.one",
            declaredContact = CombatDeclaredContact.TECHNICAL, declaredIntensity = CombatDeclaredIntensity.MODERATE)),
        actualDurationSeconds = 3500, actualDurationSource = CombatObservedDurationSource.ATHLETE_REPORTED)
    val resultDocument = SportTrackingDocument("mma-result", planDocument.engineId, 1, SportId.MMA.name, "RESULT",
        Json.encodeToJsonElement(CombatSessionResult.serializer(), result).jsonObject)

    fun source(): Program {
        val base = HybridCalendarFixtures.program()
        return base.copy(trainingIntent = TrainingIntent(goals = base.trainingIntent!!.goals + SportGoal(
            "mma", SportId.MMA, "Aprender en mi clase", GoalPriority.PROGRESS, SportExperience.BEGINNER,
        ), primaryGoalId = "strength"))
    }

    fun composition(program: Program = source()) = HybridProgramComposition(
        configuration = program.hybridPlanning!!.copy(availableWindows = program.hybridPlanning.availableWindows +
            AvailableTrainingWindow("dojo-fri", 5, 1080, 1200, "DOJO", emptySet())),
        weeks = listOf(HybridWeekComposition("week", listOf(
            HybridSessionGoalBinding("strength-session", "strength", requiredEquipment = setOf("barbell")),
            HybridSessionGoalBinding("run-session", "running"),
        ), contributions = listOf(HybridSessionContribution(session,
            HybridSessionGoalBinding(session.id, "mma", allowedWeekdays = setOf(5)), "dojo.manual.class", 1)))),
    )

    fun assembled(): Program = (HybridProgramAssembler.assemble(source(), composition()) as HybridCompositionResult.Ready).program
}
