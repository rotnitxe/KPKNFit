package com.example.kpkn.domain.multisport.tracking

import com.example.kpkn.domain.multisport.strength.ArmwrestlingEngine
import com.example.kpkn.domain.multisport.strength.CalisthenicsEngine
import com.example.kpkn.domain.multisport.strength.StrongmanEngine
import com.example.kpkn.domain.multisport.strength.WeightliftingEngine
import com.example.kpkn.domain.performance.lab.PerformanceLabEngine
import com.example.kpkn.domain.multisport.combat.CombatSportEngine
import com.example.kpkn.domain.multisport.functional.FunctionalSportEngine
import com.example.kpkn.domain.multisport.endurance.EnduranceSportEngine
import com.example.kpkn.domain.multisport.field.FieldSportEngine
import com.example.kpkn.domain.multisport.games.GameSportEngine

/** Only implemented engines are registered here. SportId by itself is never a support claim. */
object NativeSportTrackingEngines {
    val registry = SportTrackingRegistry(listOf(
        PerformanceLabEngine, WeightliftingEngine, CalisthenicsEngine, StrongmanEngine, ArmwrestlingEngine,
    ) + CombatSportEngine.all + FunctionalSportEngine.registeredEngines + EnduranceSportEngine.registeredEngines +
        FieldSportEngine.all + GameSportEngine.registeredEngines)
}
