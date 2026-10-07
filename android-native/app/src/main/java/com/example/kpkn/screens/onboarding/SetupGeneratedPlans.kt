package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.programModeFor
import com.example.kpkn.data.programs.programNameFor
import com.example.kpkn.domain.training.generator.GeneratedRoutine

/*
 * Entreno v2 · piezas puras del paso PLAN que el ViewModel usa para los programas «a medida» y los planes de autor.
 */

/** Tolerancia de tiempo con la que un plan de autor sigue siendo viable (en porcentaje del tiempo pedido). */
internal const val TIME_BUDGET_TOLERANCE_PERCENT = 15

/**
 * Minutos por sesión que se admiten para un plan del catálogo cuando la persona pidió [minutes]: un 15 % más,
 * redondeado hacia arriba (60 → 69, 100 → 115). Un plan que cabe así es viable con la nota «~N min por sesión».
 */
internal fun timeBudgetWithTolerance(minutes: Int): Int = (minutes * (100 + TIME_BUDGET_TOLERANCE_PERCENT) + 99) / 100

/**
 * El programa que se previsualiza y se activa a partir de la rutina del generador: lo que el generador no sabe del alta
 * se completa aquí, igual que hacen los demás caminos del asistente.
 *
 * - Nombre y modo de la ficha del catálogo (`programNameFor`/`programModeFor`): el mismo plan se llama igual lo cree
 *   quien lo cree; `structureTemplateId` = id de la entrada, para que Home, el detalle del programa y la biblioteca lo
 *   resuelvan con `findForProgram`.
 * - Autorregulación de las opciones del alta («sugerir y confirmar» por defecto), marcas de powerlifting
 *   (`powerliftingProfile`, las mismas que el generador usó para las cargas) y la bolsa de prioridades que el
 *   generador aplicó (`planOrderPriorities`).
 * - El lugar de cada sesión (`Session.placeId`) según el resumen del generador: cada sesión se armó solo con el
 *   material de ese lugar.
 */
internal fun generatedProgramOf(routine: GeneratedRoutine, entry: CatalogEntry, draft: SetupWizardDraft): Program {
    val placeBySession = routine.summary.days.mapNotNull { day -> day.place?.let { place -> day.sessionId to place.name } }.toMap()
    val base = routine.program
    val placed = if (placeBySession.isEmpty()) base else base.copy(
        macrocycles = base.macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            meso.copy(
                                weeks = meso.weeks.map { week ->
                                    week.copy(
                                        sessions = week.sessions.map { session ->
                                            placeBySession[session.id]?.let { session.copy(placeId = it) } ?: session
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        },
    )
    val appliedBag = draft.trainingOptions.orderPriorities.filterValues { points -> points > 0 }
    return draft.trainingOptions.applyTo(placed).copy(
        id = draft.commitId.ifBlank { base.id },
        name = programNameFor(entry),
        mode = programModeFor(entry),
        structureTemplateId = entry.id,
        powerliftingProfile = draft.powerliftingProfile,
        planOrderPriorities = appliedBag.takeIf { it.isNotEmpty() },
    )
}

/**
 * El borrador sin semana armada (sin sesiones movidas ni reparto adaptado): la semana vuelve a ser la del programa.
 * Sirve cuando el programa cambia (otro plan, otra versión, otras respuestas) o al «Restablecer».
 */
internal fun SetupWizardDraft.withoutWeekLayout(): SetupWizardDraft =
    if (weekLayoutOverrides.isEmpty() && adaptedSplitId == null) this
    else copy(weekLayoutOverrides = emptyMap(), adaptedSplitId = null)

/** Memoria pequeña (la menos usada sale primero) de rutinas generadas, segura entre hilos. */
internal class GeneratedRoutineMemo(private val maxSize: Int) {
    private val map = object : LinkedHashMap<String, GeneratedRoutine>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GeneratedRoutine>?): Boolean = size > maxSize
    }

    @Synchronized
    fun get(key: String): GeneratedRoutine? = map[key]

    @Synchronized
    fun put(key: String, routine: GeneratedRoutine) {
        map[key] = routine
    }
}
