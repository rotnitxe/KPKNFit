package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.protocols.definitions.NativeCardioDefaults
import com.example.kpkn.domain.training.EFFECTIVE_EQUIPMENT_KEYS

/**
 * Evaluación de prueba que el asesor pide al llamador: «¿qué pasaría con este pedido y este material?».
 * En producción es una llamada a [PlanCandidateEvaluator.evaluate] con el plan propio del objetivo del
 * pedido (`request.goalProfile`); en las pruebas puede ser un doble programable. Es la ÚNICA fuente de
 * verdad del asesor: nunca adivina si una reparación funciona, la prueba.
 *
 * El material que manda es el segundo parámetro, no `request.effectiveEquipment` (que el asesor no
 * recalcula): quien evalúa debe derivar el equipo efectivo de la disponibilidad recibida. Una
 * `CancellationException` se propaga; cualquier otro resultado que no sea `Ready` cuenta como «no repara».
 */
typealias PlanRepairEvaluator = suspend (PlanCandidateRequest, EquipmentAvailability) -> PlanCandidateEvaluation

/**
 * Paquete A · A.C1 (curaduría de programas, 2026-10-03) — asesor de reparaciones de UN toque, puro.
 *
 * Dado un plan propio rechazado, propone la lista ORDENADA de cambios que lo dejan `Ready` (vacía = no hay
 * reparación de un toque). Antes estas reglas vivían simuladas dentro de `PlanCoverageContractTest`; ahora
 * el contrato de cobertura y el wizard (A.C3, `applyRepair`) usan la misma función.
 *
 * Reglas (cada una se PRUEBA con [PlanRepairEvaluator] antes de proponerse):
 *  - `TIME_BUDGET` → [PlanRepair.SetMinutes] con `requiredMinutes` (exacto desde A.C2) si con esos minutos queda
 *    `Ready`. Nunca propone menos del mínimo del reloj del asistente ([EntrenoStepValues.SESSION_MINUTES_MIN]): el
 *    borrador no puede guardar esos minutos tal cual, así que ni se prueban ni se ofrecen.
 *    Solo para Atleta completo, si no, [PlanRepair.SetCardioMinutes] con el mayor valor de los que el plan
 *    ofrece (10, 15, 20, 30) que sea menor que el actual y deje el plan `Ready`: el cardio solo baja por decisión
 *    de la persona, nunca dentro del generador (DEC-w1-01).
 *  - `APPARATUS_UNKNOWN` → [PlanRepair.ConfirmApparatus] con las llaves del panel que resuelven los
 *    `missingRequirements` del rechazo y sus categorías (`SetupApparatusPanel.categoriesFor`), si con el material
 *    confirmado queda `Ready`; si con él solo falla por tiempo, se encadena UN [PlanRepair.SetMinutes] con los minutos
 *    exactos. De entre las llaves que acreditan un requisito (el banco lo acreditan el plano y el regulable) se elige
 *    la que sigue SIN responder ([confirmableKeyFor]): nunca una que la persona negó («banco plano = No» con el
 *    regulable sin responder propone el regulable) y, si ninguna está sin responder, no se propone nada.
 *  - `APPARATUS_ABSENT` y `PROFILE_MISMATCH` → [PlanRepair.SwitchGoal] hacia un objetivo cuyo plan propio ya existe
 *    (DEC-w2-02, sin «fuerza relativa»): Fuerza → Fuerza y músculo si hay mancuernas (categoría confirmada) y, si no,
 *    Músculo; Fuerza y músculo → Músculo; Músculo y Atleta completo no tienen destino. Jamás hacia Atleta completo.
 *    Solo se propone si el destino queda `Ready` o falla únicamente por tiempo y un `SetMinutes` lo arregla
 *    (`alsoMinutes`).
 *  - `SPLIT` → [PlanRepair.ClearSplit] si, sin el reparto elegido, queda `Ready` (desde A.E2 los planes propios rechazan
 *    por reparto, y el wizard la aplica con las mismas escrituras que la tarjeta «Recomendado»).
 *  - El resto de motivos (catálogo, frecuencia, composición, internos…) no tiene reparación de un toque.
 *
 * El destino de [PlanRepair.SwitchGoal] se evalúa con la referencia propia de ese objetivo, sin cardio y sin
 * reparto elegido: el cardio es un compromiso de Atleta completo y el reparto era del objetivo anterior.
 */
object PlanRepairAdvisor {

    /**
     * Techo de minutos por sesión que el asesor propone (el fitter de los planes propios acepta de 20 a 100; el reloj del
     * asistente va de [EntrenoStepValues.SESSION_MINUTES_MIN] a 180, pero ninguna reparación pasa de aquí).
     */
    const val MAX_SESSION_MINUTES: Int = 100

    /**
     * Reparaciones de un toque para [rejected], el rechazo de [request] con el material [availability].
     * [evaluate] prueba cada candidata; el asesor no comprueba nada por su cuenta.
     */
    suspend fun suggest(
        request: PlanCandidateRequest,
        rejected: PlanCandidateEvaluation.Rejected,
        availability: EquipmentAvailability,
        evaluate: PlanRepairEvaluator,
    ): List<PlanRepair> = when (rejected.reasonCode) {
        PlanRejectionReason.TIME_BUDGET -> timeBudgetRepairs(request, rejected, availability, evaluate)
        PlanRejectionReason.APPARATUS_UNKNOWN -> confirmApparatusRepairs(request, rejected, availability, evaluate)
        PlanRejectionReason.APPARATUS_ABSENT,
        PlanRejectionReason.PROFILE_MISMATCH -> switchGoalRepairs(request, availability, evaluate)
        PlanRejectionReason.SPLIT -> clearSplitRepairs(request, availability, evaluate)
        PlanRejectionReason.CATALOG_NOT_READY,
        PlanRejectionReason.RECIPE_UNAVAILABLE,
        PlanRejectionReason.LEVEL_UNSUITABLE,
        PlanRejectionReason.FREQUENCY,
        PlanRejectionReason.UNRESOLVED_CONFIGURATION,
        PlanRejectionReason.NO_VALID_SUBSTITUTION,
        PlanRejectionReason.LOAD_BASIS_UNREPRESENTABLE,
        PlanRejectionReason.COMPOSITION,
        PlanRejectionReason.INTERNAL_MATERIALIZATION -> emptyList()
    }

    /**
     * Disciplina que el wizard asocia a cada objetivo (la misma de `SetupWizardDraft.trainingReference()`: Fuerza →
     * powerlifting, Músculo → hipertrofia, Fuerza y músculo → powerbuilding; Atleta completo y los legacy no la
     * filtran). El evaluador la compara con las referencias del plan propio.
     */
    internal fun referenceOf(goal: PlanGoalProfile): TrainingReference? = when (goal) {
        PlanGoalProfile.STRENGTH -> TrainingReference.POWERLIFTING
        PlanGoalProfile.MUSCLE -> TrainingReference.HYPERTROPHY
        PlanGoalProfile.STRENGTH_MUSCLE -> TrainingReference.POWERBUILDING
        PlanGoalProfile.COMPLETE_ATHLETE,
        PlanGoalProfile.LEGACY_MIXED,
        PlanGoalProfile.LEGACY_HEALTH -> null
    }

    /**
     * Destino honesto de un cambio de objetivo, o null si no lo hay. Nunca [PlanGoalProfile.COMPLETE_ATHLETE]:
     * exige cardio y la persona no ha elegido sus minutos.
     */
    internal fun switchDestination(goal: PlanGoalProfile, availability: EquipmentAvailability): PlanGoalProfile? =
        when (goal) {
            PlanGoalProfile.STRENGTH ->
                if (EquipmentCategory.DUMBBELLS in availability.categories) {
                    PlanGoalProfile.STRENGTH_MUSCLE
                } else {
                    PlanGoalProfile.MUSCLE
                }
            PlanGoalProfile.STRENGTH_MUSCLE -> PlanGoalProfile.MUSCLE
            PlanGoalProfile.MUSCLE,
            PlanGoalProfile.COMPLETE_ATHLETE,
            PlanGoalProfile.LEGACY_MIXED,
            PlanGoalProfile.LEGACY_HEALTH -> null
        }

    // ─── Llaves del panel que acreditan un requisito (paso H5) ─────────────────────────────────

    /**
     * TODAS las llaves del panel que acreditan [token], en el orden del catálogo de llaves. Es el criterio de
     * [SetupApparatusPanel.keyForToken] (token entre los `attestedTokens` de la llave, o id de configuración entre sus
     * `machineConfigurations`, con o sin el prefijo `machine_config:`), pero sin quedarse con la primera: el banco lo
     * acreditan el plano y el regulable, y `keyForToken` devuelve siempre el plano aunque la persona lo haya negado.
     */
    internal fun keysAttesting(token: String): List<String> {
        val configurationId = token.removePrefix("machine_config:")
        return EFFECTIVE_EQUIPMENT_KEYS
            .filter { key -> token in key.attestedTokens || configurationId in key.machineConfigurations }
            .map { key -> key.key }
    }

    /**
     * La llave que hay que CONFIRMAR para que [token] quede acreditado: de las que lo acreditan, la primera cuya
     * presencia en [availability] sigue sin responder (`UNKNOWN`). Nunca una `ABSENT` —confirmarla pisaría un «No» de
     * la persona— ni una `PRESENT` (el requisito ya estaría acreditado). Null si el token no tiene llave del panel
     * (`barbell`, `dumbbells`, `machine`…) o si ninguna de sus llaves está sin responder.
     */
    internal fun confirmableKeyFor(token: String, availability: EquipmentAvailability?): String? =
        keysAttesting(token).firstOrNull { key ->
            SetupApparatusPanel.presenceOf(availability, key) == ApparatusPresence.UNKNOWN
        }

    /** Las llaves a confirmar para [tokens], en el orden de los requisitos y sin repetir ([confirmableKeyFor]). */
    internal fun confirmableKeysFor(tokens: List<String>, availability: EquipmentAvailability?): List<String> =
        tokens.mapNotNull { token -> confirmableKeyFor(token, availability) }.distinct()

    // ─── TIME_BUDGET ───────────────────────────────────────────────────────────────────────────

    private suspend fun timeBudgetRepairs(
        request: PlanCandidateRequest,
        rejected: PlanCandidateEvaluation.Rejected,
        availability: EquipmentAvailability,
        evaluate: PlanRepairEvaluator,
    ): List<PlanRepair> {
        minutesThatFix(request, rejected, availability, evaluate)?.let { return listOf(PlanRepair.SetMinutes(it)) }
        val current = request.cardioMinutes
        if (request.goalProfile == PlanGoalProfile.COMPLETE_ATHLETE && request.requiresCardio && current != null) {
            for (candidate in NativeCardioDefaults.OFFERED_MINUTES.sortedDescending()) {
                if (candidate >= current) continue
                val lighter = request.copy(
                    inputKey = "${request.inputKey}|repair=cardio:$candidate",
                    cardioMinutes = candidate,
                )
                if (evaluate(lighter, availability) is PlanCandidateEvaluation.Ready) {
                    return listOf(PlanRepair.SetCardioMinutes(candidate))
                }
            }
        }
        return emptyList()
    }

    /**
     * Minutos con los que [first] —un rechazo de [probe]— deja de ser un `TIME_BUDGET`: los `requiredMinutes` del
     * rechazo, siempre que sean un presupuesto que el wizard admite (por encima del de [probe], desde el mínimo del
     * reloj [EntrenoStepValues.SESSION_MINUTES_MIN] y hasta [MAX_SESSION_MINUTES]) y que con ellos el plan quede
     * `Ready`. Null si [first] no es de tiempo o no se arregla con un solo `SetMinutes`.
     */
    private suspend fun minutesThatFix(
        probe: PlanCandidateRequest,
        first: PlanCandidateEvaluation.Rejected,
        availability: EquipmentAvailability,
        evaluate: PlanRepairEvaluator,
    ): Int? {
        if (first.reasonCode != PlanRejectionReason.TIME_BUDGET) return null
        val required = first.requiredMinutes ?: return null
        if (required < EntrenoStepValues.SESSION_MINUTES_MIN || required <= probe.minutesPerSession || required > MAX_SESSION_MINUTES) {
            return null
        }
        val roomier = probe.copy(
            inputKey = "${probe.inputKey}|repair=minutes:$required",
            minutesPerSession = required,
        )
        return required.takeIf { evaluate(roomier, availability) is PlanCandidateEvaluation.Ready }
    }

    // ─── APPARATUS_UNKNOWN ─────────────────────────────────────────────────────────────────────

    private suspend fun confirmApparatusRepairs(
        request: PlanCandidateRequest,
        rejected: PlanCandidateEvaluation.Rejected,
        availability: EquipmentAvailability,
        evaluate: PlanRepairEvaluator,
    ): List<PlanRepair> {
        val keys = confirmableKeysFor(rejected.missingRequirements, availability)
        if (keys.isEmpty()) return emptyList()
        val repair = PlanRepair.ConfirmApparatus(keys, SetupApparatusPanel.categoriesFor(keys))
        val confirmed = repair.applyTo(availability)
        val probe = request.copy(inputKey = "${request.inputKey}|repair=confirm:${keys.joinToString(",")}")
        return when (val first = evaluate(probe, confirmed)) {
            is PlanCandidateEvaluation.Ready -> listOf(repair)
            is PlanCandidateEvaluation.Rejected ->
                minutesThatFix(probe, first, confirmed, evaluate)
                    ?.let { listOf(repair, PlanRepair.SetMinutes(it)) }
                    .orEmpty()
            PlanCandidateEvaluation.CatalogLoading -> emptyList()
        }
    }

    // ─── APPARATUS_ABSENT / PROFILE_MISMATCH ───────────────────────────────────────────────────

    private suspend fun switchGoalRepairs(
        request: PlanCandidateRequest,
        availability: EquipmentAvailability,
        evaluate: PlanRepairEvaluator,
    ): List<PlanRepair> {
        val destination = switchDestination(request.goalProfile, availability) ?: return emptyList()
        val probe = request.copy(
            inputKey = "${request.inputKey}|repair=goal:${destination.name}",
            goalProfile = destination,
            reference = referenceOf(destination),
            cardioMinutes = null,
            requiresCardio = false,
            selectedSplitId = null,
        )
        return when (val first = evaluate(probe, availability)) {
            is PlanCandidateEvaluation.Ready -> listOf(PlanRepair.SwitchGoal(destination))
            is PlanCandidateEvaluation.Rejected ->
                minutesThatFix(probe, first, availability, evaluate)
                    ?.let { listOf(PlanRepair.SwitchGoal(destination, alsoMinutes = it)) }
                    .orEmpty()
            PlanCandidateEvaluation.CatalogLoading -> emptyList()
        }
    }

    // ─── SPLIT ─────────────────────────────────────────────────────────────────────────────────

    private suspend fun clearSplitRepairs(
        request: PlanCandidateRequest,
        availability: EquipmentAvailability,
        evaluate: PlanRepairEvaluator,
    ): List<PlanRepair> {
        if (request.selectedSplitId == null) return emptyList()
        val withoutSplit = request.copy(
            inputKey = "${request.inputKey}|repair=split:clear",
            selectedSplitId = null,
        )
        return if (evaluate(withoutSplit, availability) is PlanCandidateEvaluation.Ready) {
            listOf(PlanRepair.ClearSplit)
        } else {
            emptyList()
        }
    }
}
