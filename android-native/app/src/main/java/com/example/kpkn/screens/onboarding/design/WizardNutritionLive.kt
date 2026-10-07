package com.example.kpkn.screens.onboarding.design

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kpkn.domain.nutrition.PlanTuning
import com.example.kpkn.domain.nutrition.PlanValues

/**
 * Estado «en vivo» del plan mientras se toca: los anillos responden al instante con él y el borrador solo se
 * escribe al soltar. Esta pieza decide cuándo el plan confirmado (el que el borrador ya tiene) reemplaza al
 * que se ve, para que ni un arrastre en curso ni una escritura todavía sin confirmar se pisen.
 */
data class LiveSync(
    val live: PlanTuning,
    /** Valores enviados al borrador que todavía no han vuelto confirmados; null = nada en vuelo. */
    val pending: PlanValues?,
)

/**
 * Qué plan se ve cuando el borrador publica [committed]:
 * - Con un arrastre en curso no se toca nada.
 * - Si coincide con lo que se ve, se adopta (trae el contexto y la base más recientes) y se da por recibida
 *   la escritura.
 * - Con una escritura enviada y aún sin confirmar se espera: el confirmado de ANTES no deshace lo que se ve.
 * - Sin nada en vuelo, manda el confirmado.
 *
 * Puro y sin Compose: la clase [WizardNutritionLiveState] solo lo guarda en estados observables.
 */
fun reconcileLive(
    live: PlanTuning,
    pending: PlanValues?,
    dragging: Boolean,
    committed: PlanTuning,
): LiveSync = when {
    dragging -> LiveSync(live, pending)
    committed.values == live.values -> LiveSync(committed, null)
    pending != null -> LiveSync(live, pending)
    else -> LiveSync(committed, null)
}

/**
 * Soporte de composición de [reconcileLive]: guarda el plan que se ve ([live]), si hay un arrastre en curso
 * y la escritura pendiente. Las ediciones solo cambian [live]; quien lo usa escribe al borrador con lo que
 * devuelven [finishDrag] y [commitNow] (null = no hay nada nuevo que escribir).
 */
@Stable
class WizardNutritionLiveState(initial: PlanTuning) {
    /** El plan que se ve (arrastres incluidos). */
    var live: PlanTuning by mutableStateOf(initial)
        private set

    /** Hay un dedo sobre un deslizador. */
    var dragging: Boolean by mutableStateOf(false)
        private set

    /** Escritura enviada al borrador aún sin confirmar. */
    var pending: PlanValues? by mutableStateOf(null)
        private set

    /** Últimos valores que el borrador tiene o está a punto de tener: una escritura idéntica no se reenvía. */
    private var settled: PlanValues = initial.values

    /** Cambio continuo (arrastre): solo mueve lo que se ve. */
    fun edit(change: (PlanTuning) -> PlanTuning) {
        dragging = true
        live = change(live)
    }

    /** Soltó el dedo: devuelve el plan que hay que escribir, o null si nada cambió respecto a lo escrito. */
    fun finishDrag(): PlanTuning? {
        dragging = false
        return send()
    }

    /** Cambio puntual (muesca, restablecer): se aplica y se devuelve para escribirlo ya. */
    fun commitNow(change: (PlanTuning) -> PlanTuning): PlanTuning? {
        dragging = false
        live = change(live)
        return send()
    }

    /** El borrador publicó [committed]: se reconcilia con lo que se ve. */
    fun adopt(committed: PlanTuning) {
        val next = reconcileLive(live, pending, dragging, committed)
        live = next.live
        pending = next.pending
        if (!dragging && next.pending == null) settled = committed.values
    }

    /** La escritura pendiente no volvió a tiempo: manda lo confirmado. */
    fun expirePending(committed: PlanTuning) {
        if (dragging || pending == null) return
        pending = null
        adopt(committed)
    }

    private fun send(): PlanTuning? {
        val plan = live
        if (plan.values == settled) return null
        settled = plan.values
        pending = plan.values
        return plan
    }
}
