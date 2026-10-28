package com.example.kpkn.screens.programdetail.design

import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/*
 * Fases de los bloques para el módulo «Fases» de la pestaña Semana (P2). Sin Compose salvo el color: la prueba decide qué fase le toca a
 * cada objetivo.
 */

/** Fase de un bloque: su nombre en el eyebrow y su color (los `--ph-*` del lienzo v6). Competición lleva bandera. */
internal enum class ProgramPhase(val label: String, val color: Color) {
    ACCUMULATION("Acumulación", SymbolPalette.musculo),
    INTENSIFICATION("Intensificación", SymbolPalette.energia),
    REALIZATION("Realización", SymbolPalette.columna),
    DELOAD("Descarga", Color(0xFF7FCFA4)),
    COMPETITION("Competición", WizardColors.danger),
}

/**
 * Fase de un bloque. Un bloque con una semana de competición es «Competición»; si no, la fase sale de su objetivo: la especificidad
 * cuenta como intensificación, el pico como realización y la puesta a punto («taper») como competición. Null si no se sabe (el eyebrow
 * va en `textMuted`): densidad, personalizado o sin objetivo.
 */
internal fun programPhaseOf(goal: BlockGoal?, hasCompetitionWeek: Boolean): ProgramPhase? {
    if (hasCompetitionWeek) return ProgramPhase.COMPETITION
    return when (goal) {
        BlockGoal.ACCUMULATION -> ProgramPhase.ACCUMULATION
        BlockGoal.INTENSIFICATION, BlockGoal.SPECIFICITY -> ProgramPhase.INTENSIFICATION
        BlockGoal.REALIZATION, BlockGoal.PEAK -> ProgramPhase.REALIZATION
        BlockGoal.DELOAD -> ProgramPhase.DELOAD
        BlockGoal.TAPER -> ProgramPhase.COMPETITION
        BlockGoal.DENSITY, BlockGoal.CUSTOM, null -> null
    }
}

/**
 * Eyebrow de un bloque: su nombre y, si el nombre no lo dice ya, la fase entre puntos («Bloque 2 · Intensificación»). Sin fase conocida
 * se añade el objetivo del bloque, salvo «Personalizado», que no aporta nada.
 */
internal fun programPhaseEyebrow(blockName: String, phase: ProgramPhase?, goal: BlockGoal?): String {
    val name = blockName.trim()
    val tag = phase?.label ?: goal?.takeIf { it != BlockGoal.CUSTOM }?.label
    return if (tag == null || name.contains(tag, ignoreCase = true)) name else "$name · $tag"
}
