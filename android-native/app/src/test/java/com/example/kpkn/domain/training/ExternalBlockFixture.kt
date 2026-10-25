package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*

/** An authored block outside the recipe; no fatigue model or prescription scaling. */
fun appendExternalBlockFixture(program: Program, afterBlockId: String): Pair<Program, String> {
    val id = "external-$afterBlockId"
    val source = program.macrocycles.flatMap { it.blocks }.first { it.id == afterBlockId }
    val external = source.copy(id = id, name = "Bloque editado", sourceDefinitionId = null,
        goal = BlockGoal.DELOAD, mesocycles = source.mesocycles.takeLast(1).map { meso ->
            meso.copy(id = "$id-meso", weeks = meso.weeks.takeLast(1).map { week ->
                week.copy(id = "$id-week", sessions = week.sessions.map { it.copy(id = "$id-${it.id}") })
            })
        })
    return program.copy(macrocycles = program.macrocycles.map { macro ->
        val index = macro.blocks.indexOfFirst { it.id == afterBlockId }
        if (index < 0) macro else macro.copy(blocks = macro.blocks.take(index + 1) + external + macro.blocks.drop(index + 1))
    }) to id
}
