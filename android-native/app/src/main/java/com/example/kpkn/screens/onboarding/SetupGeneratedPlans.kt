package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.programModeFor
import com.example.kpkn.data.programs.programNameFor
import com.example.kpkn.domain.onboarding.GeneratedPlans
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluator
import com.example.kpkn.domain.onboarding.PlanCandidateSources
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
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

// ─── Tiempo de la sesión: una sola medida y una sola regla de «¿cabe?» ──────────────────────────────────────────

/**
 * Tolerancia, en minutos, de un programa «a medida»: el generador ajusta cada sesión al tiempo pedido con el mismo
 * estimador con el que se mide después, así que lo que pasa de ahí ya no es el programa que se pidió.
 */
internal const val GENERATED_TIME_TOLERANCE_MINUTES = 1

/**
 * Minutos de la sesión MÁS LARGA del programa ya armado (semana, aproximación y movilidad incluidas), medidos con el
 * estimador común ([com.example.kpkn.domain.training.SessionDurationEstimator], el mismo que usa el barrido de programas
 * y el revelado); null sin sesiones con contenido. Es LA medida de la revisión: nadie más estima minutos.
 */
internal fun longestSessionMinutes(program: Program?): Int? =
    program?.let(PlanCandidateEvaluator::sessionMinutesOf)?.maxOrNull()

/**
 * La regla única de «¿cabe?» del asistente para los minutos por sesión. La usan la puerta de activación, la
 * confirmación de la receta fija y las notas de tiempo de la revisión: ninguna compara minutos por su cuenta.
 *
 * - Un programa «a medida» (generado) puede pasarse [GENERATED_TIME_TOLERANCE_MINUTES] min del tiempo pedido.
 * - Un plan del catálogo (propio o de autor) cabe con la tolerancia del barrido ([timeBudgetWithTolerance], 15 %); sin
 *   pasarse del tiempo pedido sale lo que se pidió y entre el pedido y la tolerancia cabe con la nota «~N min por sesión».
 */
internal object SessionTimeFit {

    /** Lo más largo que puede ser la sesión más larga para que el programa quepa. */
    fun limit(requested: Int, generated: Boolean): Int =
        if (generated) requested + GENERATED_TIME_TOLERANCE_MINUTES else timeBudgetWithTolerance(requested)

    /** ¿Cabe la sesión más larga ([longest]) en lo pedido, con la tolerancia que le toca? */
    fun fits(requested: Int, longest: Int, generated: Boolean): Boolean = longest <= limit(requested, generated)

    /** ¿Sale lo que se pidió, sin pasarse? (Un programa «a medida» admite su minuto de margen; el catálogo, ninguno.) */
    fun matches(requested: Int, longest: Int, generated: Boolean): Boolean =
        longest <= if (generated) requested + GENERATED_TIME_TOLERANCE_MINUTES else requested
}

/** Tiempo pedido que mide la revisión: el declarado o, sin declarar, el tope de los planes propios. */
internal fun SetupWizardDraft.requestedSessionMinutes(): Int = minutesPerSession ?: DEFAULT_REQUESTED_MINUTES

private const val DEFAULT_REQUESTED_MINUTES = 100

/** ¿El programa elegido es una receta fija (un plan de autor o una plantilla)? Los propios y los «a medida» son NATIVE. */
internal fun isFixedRecipe(planId: String?): Boolean =
    planId?.let(PersonalizedPlanCatalog::find)?.source?.let { it != CatalogSource.NATIVE } == true

/**
 * La receta fija elegida se aparta de lo declarado: trae otros días o su sesión más larga pasa de lo pedido. Es la
 * condición de la confirmación «Confirmo la rotación y la duración reales» y la misma que exige la puerta de activación.
 */
internal fun SetupWizardState.fixedRecipeDiffers(): Boolean {
    if (!isFixedRecipe(draft.selectedCatalogId)) return false
    val daysDiffer = fixedTrainingDays != null && fixedTrainingDays != draft.selectedWeekdays
    val longest = programSessionMinutes
    val timeDiffers = longest != null && !SessionTimeFit.matches(draft.requestedSessionMinutes(), longest, generated = false)
    return daysDiffer || timeDiffers
}

/**
 * La nota de tiempo de la revisión final: el programa armado pasa de lo pedido («~70 min por sesión: un poco más de los 60
 * que pediste.»). Null si sale lo que se pidió (con el margen que le toca) o aún no hay minutos medidos.
 */
internal fun timeReviewNote(draft: SetupWizardDraft, longestMinutes: Int?): String? {
    val longest = longestMinutes ?: return null
    val requested = draft.minutesPerSession ?: return null
    if (SessionTimeFit.matches(requested, longest, generated = GeneratedPlans.isGenerated(draft.selectedCatalogId))) return null
    return "~$longest min por sesión: un poco más de los $requested que pediste."
}

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
 * El programa «a medida» que ocupa el lugar de un plan propio de la biblioteca cuando el perfil de objetivo es GENERAL
 * ([PlanCandidateSources.GENERATED]: Fuerza y masa muscular, Fuerza y cardio, Funcional y saludable, que solo ofrecen su
 * «a medida»). El caso real: «Configurar este plan» con `native:complete-athlete-v2` prefija «Fuerza y cardio», un perfil
 * general que ya no ofrece ese plan sino su programa a medida; el plan elegido «cae» sin que nada haya cambiado. Con este
 * programa el aviso es honesto y no una alarma («Este programa de la biblioteca ahora se arma a medida en el asistente»)
 * y deja el candidato a un toque.
 *
 * Solo para los planes propios (NATIVE): el «a medida» es su continuación. Un plan de autor que no se ofrece desde un perfil
 * general sigue con el aviso de siempre (no lo sustituye ningún programa «a medida»), y los perfiles de disciplina sin
 * autores ofrecen una «versión inicial» que tampoco es el plan de la biblioteca. Null si no hay sustituto.
 */
internal fun tailoredReplacementOf(entry: CatalogEntry?, profile: TrainingGoalProfile?): String? {
    if (entry == null || profile == null) return null
    if (entry.source != CatalogSource.NATIVE || GeneratedPlans.isGenerated(entry.id)) return null
    if (GeneratedPlans.sourcesFor(profile) != PlanCandidateSources.GENERATED) return null
    return GeneratedPlans.entryIdFor(profile)
}

/**
 * El borrador sin semana armada (sin sesiones movidas ni reparto adaptado): la semana vuelve a ser la del programa.
 * Sirve cuando el programa cambia (otro plan, otra versión, otras respuestas) o al «Restablecer».
 */
internal fun SetupWizardDraft.withoutWeekLayout(): SetupWizardDraft =
    if (weekLayoutOverrides.isEmpty() && adaptedSplitId == null) this
    else copy(weekLayoutOverrides = emptyMap(), adaptedSplitId = null)

/**
 * El borrador con la semana armada INVALIDADA por un cambio anterior (días, material, minutos, plan, prioridades, otra
 * versión…): la semana vuelve a ser la del programa nuevo ([withoutWeekLayout]) y, si la persona ya la había confirmado o
 * llevaba decisiones suyas (sesiones movidas, reparto adaptado), WEEK_LAYOUT queda PENDIENTE de revisar (no «hecho»): nada
 * se limpia en silencio. «Restablecer» no pasa por aquí: es una decisión de la propia persona sobre su semana.
 */
internal fun SetupWizardDraft.withInvalidatedWeekLayout(): SetupWizardDraft {
    val lostDecisions = hasWeekLayout
    val cleared = withoutWeekLayout()
    return if (lostDecisions || SetupStepId.WEEK_LAYOUT in cleared.stepProgress.answers) {
        cleared.copy(stepProgress = cleared.stepProgress.withPendingReview(setOf(SetupStepId.WEEK_LAYOUT)))
    } else {
        cleared
    }
}

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
