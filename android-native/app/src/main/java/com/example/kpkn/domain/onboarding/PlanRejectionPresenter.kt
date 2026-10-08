package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.domain.text.SpanishPlurals

/**
 * Paquete A · A.C4, parte pura (curaduría de programas, 2026-10-03) — presentador ÚNICO de rechazos de plan.
 *
 * El wizard y la prueba de cobertura comparten estas dos funciones:
 *  - [PlanRejectionPresenter.primary] elige QUÉ rechazo contar cuando hay varios (hoy la UI toma el primero de la
 *    lista, que suele ser un rechazo trivial de un plan que la persona ni pidió);
 *  - [PlanRejectionPresenter.present] lo convierte en un texto llano y como mucho dos botones (tabla de C.P11).
 *
 * Es dominio puro: no importa nada de `screens/` ni de Android. [RejectionView] es la proyección mínima de un
 * rechazo (el ViewModel convierte su `SetupCandidateRejection` en una; la etapa del wizard se traduce así:
 * CATALOG → CATALOG, PROFILE → PROFILE, FREQUENCY → FREQUENCY_SPLIT, MATERIAL → MATERIAL, MATERIALIZATION →
 * MATERIALIZATION, DURATION → SESSION_DURATION, COMPOSITION → COMPOSITION). El texto crudo del motor
 * (`reason`, ids de configuración, diagnósticos) NO entra en la proyección, así que no puede llegar a la
 * persona: solo va al registro.
 */
data class RejectionView(
    /** Plan rechazado; null en los rechazos globales (catálogo sin cargar). */
    val planId: String?,
    /** Motivo cerrado de §15.2; null solo en rechazos heredados sin código. */
    val reasonCode: PlanRejectionReason?,
    /** Minutos que el plan necesita cuando el motivo es de tiempo (exactos desde A.C2); null = no cabe en 100. */
    val requiredMinutes: Int? = null,
    /** Llave del panel de material que se puede confirmar con un toque, si la hay. */
    val apparatusKey: String? = null,
    val needsApparatusConfirmation: Boolean = false,
    /** Tokens de material que el motor negó o no pudo confirmar (`rack`, `bench`, `barbell`…). */
    val missingRequirements: List<String> = emptyList(),
    val stage: PlanEvaluationStage? = null,
)

/**
 * Lo que el presentador necesita saber de la pantalla para escribir el texto. Todo es opcional: sin un dato el
 * texto usa una forma más general, nunca imprime un valor vacío ni un id.
 */
data class PresentationContext(
    /** Minutos por sesión que la persona eligió («elegiste M»). */
    val userMinutes: Int? = null,
    /** Etiqueta del objetivo elegido («Músculo», «Fuerza y músculo»…). */
    val goalLabel: String? = null,
    /** Días por semana que la persona eligió. */
    val daysChosen: Int? = null,
    /** Disciplina del plan rechazado («powerlifting», «hipertrofia»…) para el motivo de perfil. */
    val disciplineLabelOf: (String?) -> String? = { null },
    /**
     * Nombre de una llave del panel de material; por defecto, el nombre corto del botón de un toque
     * ([PlanRejectionPresenter.shortLabelOf]: «rack», «banco», «banco regulable»…), de modo que el texto y el botón
     * dicen lo mismo con las mismas palabras.
     */
    val apparatusLabelOf: (String) -> String? = { key -> PlanRejectionPresenter.shortLabelOf(key) },
    /** Días por semana que el plan rechazado admite (un solo valor para los planes de autor). */
    val planDaysOf: (String?) -> IntRange? = { null },
    /**
     * Llave del panel que confirma un requisito de material (`bench`, `rack`…). Por defecto la primera que lo
     * acredita ([SetupApparatusPanel.keyForToken]); el asistente pasa la que sigue SIN responder
     * ([PlanRepairAdvisor.confirmableKeyFor]), la misma que confirma el botón de un toque, para que el texto y el
     * botón nombren siempre el mismo requisito («banco» o «banco regulable» si el plano ya se negó).
     */
    val apparatusKeyOf: (String) -> String? = { token -> SetupApparatusPanel.keyForToken(token) },
    /** Plan propio del objetivo elegido (el que la persona espera ver); null = no se conoce. */
    val ownPlanId: String? = null,
    /** Perfil del objetivo elegido; con [ownPlanId] decide el texto del rechazo de perfil del plan propio. */
    val goalProfile: PlanGoalProfile? = null,
)

/** Botón de un rechazo. La UI decide qué hace cada uno con la API del wizard; [label] es su texto. */
sealed interface RejectionAction {
    val label: String

    /** Reintenta el cálculo sin tocar las respuestas. */
    data object Retry : RejectionAction {
        override val label: String = "Reintentar"
    }

    data object ChangeGoal : RejectionAction {
        override val label: String = "Cambiar objetivo"
    }

    /** Muestra los otros planes que sí encajan con las respuestas. */
    data object SeeAlternatives : RejectionAction {
        override val label: String = "Ver alternativas"
    }

    data object ChangeDays : RejectionAction {
        override val label: String = "Cambiar días"
    }

    data object ChangeSplit : RejectionAction {
        override val label: String = "Cambiar reparto"
    }

    /** Abre el paso de material para confirmar o corregir lo que falta. */
    data object ConfirmApparatus : RejectionAction {
        override val label: String = "Confirmar material"
    }

    /** Sube los minutos por sesión a [minutes], el mínimo exacto con el que el plan cabe. */
    data class SetMinutes(val minutes: Int) : RejectionAction {
        override val label: String get() = "Ajustar a $minutes min"
    }
}

/** Texto para la persona y hasta dos botones (el principal primero). */
data class RejectionPresentation(
    val text: String,
    val primary: RejectionAction?,
    val secondary: RejectionAction? = null,
) {
    val actions: List<RejectionAction> get() = listOfNotNull(primary, secondary)
}

object PlanRejectionPresenter {

    /** Rechazo de catálogo: nada que ver con las respuestas de la persona. */
    const val CATALOG_TEXT: String =
        "No encontramos plan por un error de catálogo. Puedes reintentar sin cambiar tus respuestas."

    /** Fallo interno del motor al preparar el plan (composición, materialización, carga, configuración). */
    const val INTERNAL_TEXT: String = "Algo falló al preparar este plan. Tus respuestas no cambian."

    /**
     * Rechazo de perfil del plan PROPIO de Fuerza y músculo (r2 §11.1): sin barra con rack y banco ni mancuernas, la
     * frase genérica de disciplina («Este plan es de …; tu objetivo es …») no explica nada; se dice el requisito, con
     * «pesas» y no con el término técnico. Es la única fuente de este texto: el asistente no lo reescribe.
     */
    const val OWN_POWERBUILDING_TEXT: String =
        "Fuerza y músculo necesita pesas en los ejercicios principales: barra con rack y banco, o mancuernas."

    /**
     * El rechazo más accionable de [rejections], o null si la lista está vacía. Orden de preferencia, y a igual
     * preferencia gana el primero de la lista:
     *  1. el del plan propio del perfil ([ownPlanId]): es el que la persona espera;
     *  2. `APPARATUS_UNKNOWN` con una llave que se puede confirmar con un toque;
     *  3. `TIME_BUDGET`, el de menos minutos requeridos (el más fácil de arreglar; sin minutos, el último);
     *  4. `PROFILE_MISMATCH` o `APPARATUS_ABSENT`;
     *  5. cualquier otro.
     */
    fun primary(rejections: List<RejectionView>, ownPlanId: String?): RejectionView? {
        if (rejections.isEmpty()) return null
        if (ownPlanId != null) {
            rejections.firstOrNull { it.planId == ownPlanId }?.let { return it }
        }
        rejections.firstOrNull { it.reasonCode == PlanRejectionReason.APPARATUS_UNKNOWN && it.apparatusKey != null }
            ?.let { return it }
        rejections.filter { it.reasonCode == PlanRejectionReason.TIME_BUDGET }
            .minWithOrNull(compareBy<RejectionView> { it.requiredMinutes ?: Int.MAX_VALUE })
            ?.let { return it }
        rejections.firstOrNull {
            it.reasonCode == PlanRejectionReason.PROFILE_MISMATCH || it.reasonCode == PlanRejectionReason.APPARATUS_ABSENT
        }?.let { return it }
        return rejections.first()
    }

    /** Texto llano y botones de [rejection] (tabla de C.P11); nunca el texto crudo del motor ni ids. */
    fun present(rejection: RejectionView, context: PresentationContext = PresentationContext()): RejectionPresentation =
        when (rejection.reasonCode) {
            null -> presentWithoutCode(rejection, context)
            PlanRejectionReason.CATALOG_NOT_READY -> catalogError()
            PlanRejectionReason.RECIPE_UNAVAILABLE ->
                RejectionPresentation("Este plan no está disponible ahora.", RejectionAction.SeeAlternatives)
            PlanRejectionReason.PROFILE_MISMATCH ->
                RejectionPresentation(
                    profileMismatchText(rejection, context),
                    RejectionAction.ChangeGoal,
                    RejectionAction.SeeAlternatives,
                )
            PlanRejectionReason.LEVEL_UNSUITABLE ->
                RejectionPresentation("Este plan no es adecuado para tu nivel.", RejectionAction.SeeAlternatives)
            PlanRejectionReason.FREQUENCY ->
                RejectionPresentation(frequencyText(rejection, context), RejectionAction.ChangeDays)
            PlanRejectionReason.SPLIT ->
                RejectionPresentation("El reparto elegido no encaja con este plan.", RejectionAction.ChangeSplit)
            PlanRejectionReason.APPARATUS_UNKNOWN -> apparatusUnknown(rejection, context)
            PlanRejectionReason.APPARATUS_ABSENT -> apparatusAbsent(rejection, context)
            PlanRejectionReason.NO_VALID_SUBSTITUTION ->
                RejectionPresentation(
                    "No hay un cambio de ejercicio que respete este plan; no recortamos el plan en silencio.",
                    RejectionAction.SeeAlternatives,
                )
            PlanRejectionReason.TIME_BUDGET -> timeBudget(rejection, context)
            PlanRejectionReason.UNRESOLVED_CONFIGURATION,
            PlanRejectionReason.INTERNAL_MATERIALIZATION,
            PlanRejectionReason.COMPOSITION,
            PlanRejectionReason.LOAD_BASIS_UNREPRESENTABLE -> internalFailure()
        }

    /** Etiqueta curada del panel de material para [key] («Rack de sentadilla»), o null si no es una llave del panel. */
    fun panelLabelOf(key: String): String? = PANEL_LABELS[key]

    /**
     * Nombre corto de una llave del panel («rack», «banco», «banco regulable», «barra baja»): el del botón de un toque
     * («Sí, tengo rack y banco») y, con las mismas palabras, el del texto («Falta confirmar si tienes rack y banco»).
     * Las llaves sin nombre corto usan la etiqueta del panel en minúscula; null si [key] no es una llave del panel.
     */
    fun shortLabelOf(key: String): String? = SHORT_LABELS[key] ?: panelLabelOf(key)?.lowercaseFirst()

    // ─── Motivos ───────────────────────────────────────────────────────────────────────────────

    private fun catalogError() = RejectionPresentation(CATALOG_TEXT, RejectionAction.Retry)

    private fun internalFailure() = RejectionPresentation(INTERNAL_TEXT, RejectionAction.Retry)

    /** Rechazo heredado sin motivo cerrado: se explica por lo que sí se sabe. */
    private fun presentWithoutCode(rejection: RejectionView, context: PresentationContext): RejectionPresentation = when {
        rejection.stage == PlanEvaluationStage.CATALOG -> catalogError()
        rejection.needsApparatusConfirmation -> apparatusUnknown(rejection, context)
        else -> internalFailure()
    }

    private fun profileMismatchText(rejection: RejectionView, context: PresentationContext): String {
        // El plan propio de Fuerza y músculo se rechaza por perfil cuando no hay resistencia externa: ahí se dice el
        // requisito en lugar de la frase de disciplina.
        if (rejection.planId != null && rejection.planId == context.ownPlanId &&
            context.goalProfile == PlanGoalProfile.STRENGTH_MUSCLE
        ) {
            return OWN_POWERBUILDING_TEXT
        }
        val discipline = context.disciplineLabelOf(rejection.planId)?.lowercaseFirst()
        val goal = context.goalLabel?.lowercaseFirst()
        return when {
            discipline != null && goal != null -> "Este plan es de $discipline; tu objetivo es $goal."
            discipline != null -> "Este plan es de $discipline, que no es tu objetivo."
            goal != null -> "Este plan no corresponde a tu objetivo: $goal."
            else -> "Este plan no corresponde a tu objetivo."
        }
    }

    private fun frequencyText(rejection: RejectionView, context: PresentationContext): String {
        val planDays = context.planDaysOf(rejection.planId)?.let { planDaysText(it) }
        val chosen = context.daysChosen?.let { SpanishPlurals.days(it) }
        return when {
            planDays != null && chosen != null -> "Este plan usa $planDays; elegiste $chosen."
            planDays != null -> "Este plan usa $planDays."
            chosen != null -> "Este plan no se adapta a los días que elegiste ($chosen)."
            else -> "Este plan no se adapta a los días que elegiste."
        }
    }

    /** «4 días distintos» (un solo valor, como los planes de autor), «1 día distinto» o «de 3 a 5 días». */
    private fun planDaysText(days: IntRange): String =
        if (days.first == days.last) {
            SpanishPlurals.choose(days.first, "${days.first} día distinto", "${days.first} días distintos")
        } else {
            "de ${days.first} a ${days.last} días"
        }

    private fun apparatusUnknown(rejection: RejectionView, context: PresentationContext): RejectionPresentation {
        val labels = apparatusLabels(rejection, context)
        val text = if (labels.isEmpty()) {
            "Falta confirmar si tienes todo el material de este plan."
        } else {
            "Falta confirmar si tienes ${joinSpanish(labels)}."
        }
        val canConfirm = rejection.apparatusKey?.let(::panelLabelOf) != null
        return RejectionPresentation(
            text,
            if (canConfirm) RejectionAction.ConfirmApparatus else RejectionAction.SeeAlternatives,
        )
    }

    private fun apparatusAbsent(rejection: RejectionView, context: PresentationContext): RejectionPresentation {
        val labels = apparatusLabels(rejection, context)
        // «, que dijiste que no tienes» concuerda con cualquier género y número («rack», «mancuernas», «barra y carga»);
        // un pronombre («no lo tienes») fallaría con la mitad de las etiquetas.
        val text = if (labels.isEmpty()) {
            "Este plan necesita material que dijiste que no tienes."
        } else {
            "Este plan necesita ${joinSpanish(labels)}, que dijiste que no tienes."
        }
        val canReview = rejection.apparatusKey?.let(::panelLabelOf) != null ||
            rejection.missingRequirements.any { SetupApparatusPanel.keyForToken(it) != null }
        return if (canReview) {
            RejectionPresentation(text, RejectionAction.ConfirmApparatus, RejectionAction.SeeAlternatives)
        } else {
            RejectionPresentation(text, RejectionAction.SeeAlternatives)
        }
    }

    private fun timeBudget(rejection: RejectionView, context: PresentationContext): RejectionPresentation {
        // Solo se promete «Ajustar a N min» con un N que el reloj del asistente ofrece (de su mínimo al techo de las reparaciones).
        val required = rejection.requiredMinutes
            ?.takeIf { it in EntrenoStepValues.SESSION_MINUTES_MIN..PlanRepairAdvisor.MAX_SESSION_MINUTES }
        if (required == null) {
            val chosen = context.userMinutes?.let { "los $it min que elegiste" } ?: "el tiempo que elegiste"
            return RejectionPresentation(
                "Con las series mínimas este plan no cabe en $chosen.",
                RejectionAction.SeeAlternatives,
            )
        }
        val chosen = context.userMinutes?.let { " y elegiste $it" }.orEmpty()
        return RejectionPresentation(
            "Con las series mínimas este plan necesita $required min por sesión$chosen.",
            RejectionAction.SetMinutes(required),
        )
    }

    // ─── Ayudas de texto ───────────────────────────────────────────────────────────────────────

    /**
     * Nombres del material que falta, en el orden de los requisitos: la etiqueta del panel cuando el requisito
     * tiene una llave confirmable y, si no (una categoría como la barra), un nombre llano. Los requisitos sin
     * nombre se omiten: jamás se imprime un token.
     */
    private fun apparatusLabels(rejection: RejectionView, context: PresentationContext): List<String> {
        val fromRequirements = rejection.missingRequirements.mapNotNull { token ->
            context.apparatusKeyOf(token)?.let(context.apparatusLabelOf) ?: TOKEN_LABELS[token]
        }
        val labels = fromRequirements.ifEmpty { listOfNotNull(rejection.apparatusKey?.let(context.apparatusLabelOf)) }
        return labels.map { it.lowercaseFirst() }.distinct()
    }

    private fun joinSpanish(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items.first()
        else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
    }

    /** Minúscula inicial para insertar una etiqueta en una frase; deja intactas las siglas («EZ»). */
    private fun String.lowercaseFirst(): String =
        if (isEmpty() || (length > 1 && this[1].isUpperCase())) this else replaceFirstChar { it.lowercaseChar() }

    /** Nombres llanos de los requisitos que son una categoría y no una llave del panel. */
    private val TOKEN_LABELS: Map<String, String> = mapOf(
        "barbell" to "barra y carga",
        "dumbbells" to "mancuernas",
        "kettlebell" to "kettlebell",
        "band" to "bandas",
        "smith_machine" to "máquina Smith",
        "machine" to "máquinas",
        "cable" to "poleas",
        "ball" to "balón",
        "support" to "apoyo estable",
    )

    /**
     * Nombres cortos de las llaves del panel (decisión D5 del dueño: «Sí, tengo rack y banco»). Las demás llaves usan
     * la etiqueta del panel en minúscula ([shortLabelOf]).
     */
    private val SHORT_LABELS: Map<String, String> = mapOf(
        "squat_rack" to "rack",
        "bench_flat" to "banco",
        "bench_adjustable" to "banco regulable",
        "preacher_bench" to "banco predicador",
        "pullup_bar" to "barra de dominadas",
        "dip_bars" to "paralelas",
        "low_bar_support" to "barra baja",
        "ez_bar" to "barra EZ",
    )

    private val PANEL_LABELS: Map<String, String> by lazy {
        SetupApparatusPanel.itemsFor(EquipmentCategory.entries.toSet()).associate { it.key to it.label }
    }
}
