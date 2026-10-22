package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.isSimpleLinearProgram
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.data.models.simpleCycleWeeks
import com.example.kpkn.data.models.totalBlockCount
import com.example.kpkn.data.models.totalProgramWeeks

/** Alcance visible de un programa: lo único que distingue a unos programas de otros de cara a la persona. */
sealed interface ProgramScope {
    /** Una o varias semanas que se repiten en ciclos (antes «programa simple»). */
    data class RepeatingWeeks(val weeks: Int) : ProgramScope
    /** N semanas que se recorren una sola vez, sin bloques. */
    data class FixedWeeks(val weeks: Int) : ProgramScope
    /** Varios bloques (mesociclos) recorridos una vez. */
    data class Blocks(val blocks: Int, val weeks: Int) : ProgramScope
}

object ProgramScopes {
    fun of(program: Program): ProgramScope {
        val weeks = program.totalProgramWeeks.coerceAtLeast(1)
        val blocks = program.totalBlockCount.coerceAtLeast(1)
        return when {
            program.isSimpleProgram && !program.isSimpleLinearProgram ->
                ProgramScope.RepeatingWeeks(program.simpleCycleWeeks ?: weeks)
            blocks >= 2 -> ProgramScope.Blocks(blocks, weeks)
            else -> ProgramScope.FixedWeeks(weeks)
        }
    }

    /** Etiqueta corta en español: «Una semana que se repite», «2 semanas que se repiten», «8 semanas», «Por bloques · 3 bloques · 12 semanas». */
    fun label(scope: ProgramScope): String = when (scope) {
        is ProgramScope.RepeatingWeeks ->
            if (scope.weeks <= 1) "Una semana que se repite" else "${scope.weeks} semanas que se repiten"
        is ProgramScope.FixedWeeks ->
            if (scope.weeks == 1) "1 semana" else "${scope.weeks} semanas"
        is ProgramScope.Blocks ->
            "Por bloques · ${scope.blocks} bloques · ${scope.weeks} semanas"
    }
}
