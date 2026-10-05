package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluation
import com.example.kpkn.domain.onboarding.PlanCandidateEvaluator
import com.example.kpkn.domain.onboarding.PlanCandidateRequest
import com.example.kpkn.domain.onboarding.PlanEvaluationStage
import com.example.kpkn.domain.onboarding.PlanRejectionReason
import com.example.kpkn.domain.onboarding.PlanRepair
import com.example.kpkn.domain.onboarding.PlanRepairAdvisor
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.training.CoverageFixtures.EquipmentFixture
import com.example.kpkn.domain.training.CoverageFixtures.Profile
import com.example.kpkn.screens.onboarding.SetupExperience
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.TreeMap
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

// ─── Rejilla, techos y tablas del contrato (todo de tipos simples; los tipos de la prueba viven dentro de la clase) ───

/**
 * Techos del ratchet de violaciones por tier. La suite SIEMPRE está en verde: solo falla si una corrida supera
 * su techo. Cada paso posterior de los paquetes A–E baja los techos; al llegar a 0 el contrato queda cerrado.
 * Cuando una corrida da menos violaciones que su techo, la prueba imprime «techo bajable a N» (no falla).
 *
 * `ci/k` = filas con `floorMod(key.hashCode(), 4) == k`; `smoke` = filas con `floorMod(key.hashCode(), 16) == 0`
 * (la rebanada smoke está contenida en `ci/0`); `full` = todas las filas (= suma de los cuatro shards).
 */
private val VIOLATION_CEILING: Map<String, Int> = mapOf(
    // baseline 2026-10-03 (antes de A.B1–E2): corrida `full` de 25 650 filas = 3 537 violaciones
    // (smoke 226, ci/0 887, ci/1 885, ci/2 886, ci/3 879).
    // tras A.B1–B3/B7 (2026-10-03): corrida `full` = 3 162 (NOT_HONEST 195 → 120, DISHONEST_ABSENT 270 → 0,
    // NO_REPAIR 30 → 0; TIME_BUDGET_INEXACT 1 578 y MATERIAL_UNUSED 1 464 no cambian: A.C2 y A.B5).
    // tras A.B4/B5 (2026-10-03): corrida `full` = 1 704 (MATERIAL_UNUSED 1 464 → 0: kettlebell y Smith+banco usan ya
    // su material; NOT_HONEST 120 sin cambio; TIME_BUDGET_INEXACT 1 578 → 1 584, +6: Atleta con solo kettlebell (E10) o solo
    // Smith (E11) pasa del calendario «sin tirón» al calendario con tirón, más largo, y cae en el mismo patrón de A.C2 que el
    // resto de fixtures con tirón, 81 → 84 filas cada uno). Ready 13 624 → 13 471 (−153: Atleta E10 y E11 −72 cada uno,
    // Músculo E10, E11 y E16 −3 cada uno), todas rechazos honestos TIME_BUDGET con reparación `SetMinutes`.
    // tras A.C1/C2 (2026-10-03): corrida `full` = 120 (TIME_BUDGET_INEXACT 1 584 → 0: `requiredMinutes` es ya el mínimo
    // exacto del generador, DEC-w2-03; quedan solo las 120 NOT_HONEST `COMPOSITION` de Atleta de 1 día, intermedio y
    // avanzado, con solo barra de dominadas (E13), entonces pendientes de candidatos o de un calendario con V).
    // Ready 13 471, rechazos honestos 12 059 y reparaciones por clase no cambian: el asesor del dominio reproduce la
    // semántica de las simulaciones que sustituye (smoke 110 → 8, ci/0–ci/3 → 30 cada uno).
    // cierre A3 (gate48, 2026-10-04): corrida `full` de 25 650 filas = 0 violaciones tras resolver E13;
    // el ratchet queda en 0 para smoke, los cuatro shards ci y full.
    "smoke" to 0,
    "ci/0" to 0,
    "ci/1" to 0,
    "ci/2" to 0,
    "ci/3" to 0,
    "full" to 0,
)

/** Filas de la rejilla C1: 3 objetivos x 3 niveles x 6 días x 5 duraciones x 19 fixtures + Atleta x 12 (cardio). */
private const val EXPECTED_GRID_ROWS = 25_650

/** Tope de minutos por sesión que el wizard admite (el fitter solo acepta 20..100). */
private const val MAX_SESSION_MINUTES = 100

private const val EXAMPLES_PER_KIND = 25

/** RETURNING ≡ NEW (mismo nivel de catálogo), así que no se duplica en la rejilla. */
private val GRID_EXPERIENCES = listOf(SetupExperience.NEW, SetupExperience.INTERMEDIATE, SetupExperience.ADVANCED)
private val GRID_MINUTES = listOf(20, 30, 45, 60, 90)
private val GRID_CARDIO_MINUTES = listOf(10, 15, 20, 30)

/** Fixtures con material «utilizable» y los `equipmentId` del catálogo que C5 exige usar (solo Músculo/Atleta). */
private val MATERIAL_EQUIPMENT_IDS: Map<String, Set<String>> = mapOf(
    "E9" to setOf("machine", "cable"),
    "E10" to setOf("kettlebell"),
    "E11" to setOf("smith_machine"),
    "E12" to setOf("band"),
    "E14" to setOf("machine", "cable"),
)

private fun shardOf(key: String): Int = Math.floorMod(key.hashCode(), 4)

private fun inSmokeSlice(key: String): Boolean = Math.floorMod(key.hashCode(), 16) == 0

/**
 * Paquete A · A2 (curaduría de programas, 2026-10-03) — contrato de cobertura del plan propio, JVM puro.
 *
 * Definición (docs/audits/2026-10-programs/00-PLAN-curaduria-programas-2026-10-03.md §6 «Paquete A»):
 *  - C1. Para todo input alcanzable, el plan PROPIO del objetivo es `Ready`, o es un rechazo honesto
 *    (APPARATUS_UNKNOWN con llave confirmable · APPARATUS_ABSENT con evidencia explícita · PROFILE_MISMATCH ·
 *    TIME_BUDGET con `requiredMinutes` en (minutos, 100]) Y existe una reparación de un toque que lo deja `Ready`.
 *    COMPOSITION, INTERNAL_MATERIALIZATION, CATALOG_NOT_READY, UNRESOLVED_CONFIGURATION… = violación siempre.
 *  - C5. Con material utilizable (kettlebell, Smith + banco, banda + barra, máquinas) todo `Ready` de Músculo y de
 *    Atleta usa al menos una configuración de ese material.
 *  - `time_budget_minimum_is_exact`: `requiredMinutes` deja el plan `Ready` y `requiredMinutes - 1` sigue siendo TIME_BUDGET.
 *
 * Tiers (`KPKN_COVERAGE_TIER` o `-Dkpkn.coverage.tier`): `smoke` (por defecto, 1/16 de las filas), `ci` (un shard de 4
 * por hash, `KPKN_COVERAGE_SHARD`, 0 por defecto) y `full` (todas). El informe queda en
 * `build/reports/coverage-contract/<tier>[-shard].txt`.
 *
 * Rendimiento: cada evaluación real cuesta 25–60 ms (el generador reconstruye su tabla de catálogo en cada llamada),
 * así que `full` son ≈ 45 min en un solo hilo. Las filas se reparten por familia (mismo objetivo, nivel, días,
 * material y cardio; solo cambian los minutos) entre N hilos (`KPKN_COVERAGE_WORKERS` o
 * `-Dkpkn.coverage.workers`; por defecto un cuarto de los núcleos, 1..4, limitado por la memoria del JVM). Cada hilo
 * tiene SU generador y SU memo; el resultado de una fila no depende de qué hilo la procese y el informe se ordena
 * por posición de fila, así que el número de violaciones no varía con el reparto.
 *
 * Las reparaciones de un toque las decide [PlanRepairAdvisor] (A.C1), no una simulación del test, y `requiredMinutes`
 * es el mínimo exacto del generador (A.C2, DEC-w2-03).
 *
 * // TODO A.C4 (C3): `PlanRejectionPresenter` ya existe (parte pura); falta cablearlo en la UI (C.P11) para que UI y
 * //   test compartan `primary`.
 *
 * La rejilla no fija reparto (`selectedSplitId` es null en todas las filas), así que el motivo `SPLIT` no puede salir
 * aquí. Desde A.E2 la cobertura del reparto de los planes propios —cada testigo de `NativeProfileSplitWitness` da
 * `Ready`, cualquier otro reparto se rechaza con `SPLIT` y `ClearSplit` lo repara— vive en
 * `OwnPlanPrioritiesAndSplitTest` (y la cláusula C4 del pase a peso corporal la cubre `SetupWizardCandidateGateTest`).
 */
class PlanCoverageContractTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    // ─── Tipos de la prueba ───────────────────────────────────────────────────────────────

    /** Tipo de cardio del Atleta; RUN y BIKE usan los tipos exteriores, que no exigen aparato. */
    private enum class CardioChoice(val type: CardioType) {
        WALK(CardioType.WALK),
        RUN(CardioType.RUN_OUTDOOR),
        BIKE(CardioType.BIKE_OUTDOOR),
    }

    private enum class ViolationKind {
        NOT_HONEST,
        DISHONEST_ABSENT,
        TIME_BUDGET_OUT_OF_RANGE,
        TIME_BUDGET_INEXACT,
        NO_REPAIR,
        MATERIAL_UNUSED,
        EXCEPTION,
    }

    private data class Tier(val name: String, val shard: Int?) {
        val key: String get() = if (name == "ci") "ci/$shard" else name
        val fileStem: String get() = if (name == "ci") "ci-$shard" else name
    }

    /** Una evaluación completa: objetivo, nivel, calendario, duración, material y (solo Atleta) cardio. */
    private data class Spec(
        val profile: Profile,
        val experience: SetupExperience,
        val days: Int,
        val minutes: Int,
        val fixture: EquipmentFixture,
        val cardio: CardioChoice?,
        val cardioMinutes: Int?,
    ) {
        /** Clave estable de la fila: define el shard y el slice smoke. */
        val key: String = listOf(
            profile.name,
            experience.name,
            days,
            minutes,
            fixture.id,
            cardio?.name ?: "-",
            cardioMinutes ?: "-",
        ).joinToString("|")

        /** Familia = la misma fila sin los minutos; sus reparaciones por minutos caen en el mismo hilo (memo local). */
        val familyKey: String
            get() = listOf(
                profile.name,
                experience.name,
                days,
                fixture.id,
                cardio?.name ?: "-",
                cardioMinutes ?: "-",
            ).joinToString("|")

        val group: String get() = "${profile.name}/${fixture.id}"
    }

    private sealed interface Outcome {
        data class Ready(val usedEquipmentIds: Set<String>) : Outcome

        data class Rejected(
            val reason: PlanRejectionReason,
            val stage: PlanEvaluationStage,
            val requiredMinutes: Int?,
            val details: String,
            /** Tokens de material que el motor negó o no pudo confirmar (A.B1); vacío si no es un rechazo de aparatos. */
            val missingRequirements: List<String> = emptyList(),
        ) : Outcome

        /** Excepción inesperada o catálogo no listo: nunca es un resultado de producto. */
        data class Failed(val message: String) : Outcome
    }

    private data class Violation(
        /** Posición de la fila en la rejilla del tier: ordena el informe sin depender del reparto entre hilos. */
        val order: Int,
        val kind: ViolationKind,
        val key: String,
        val group: String,
        val reason: String,
        val detail: String,
    )

    private class GroupStats {
        var rows = 0
        var ready = 0
        var honest = 0
        val violationsByKind = LinkedHashMap<ViolationKind, Int>()

        fun add(other: GroupStats) {
            rows += other.rows
            ready += other.ready
            honest += other.honest
            other.violationsByKind.forEach { (kind, count) -> violationsByKind.merge(kind, count, Int::plus) }
        }
    }

    // ─── Motor de una corrida (UN hilo): evalúa, clasifica y simula las reparaciones ─────────

    private class CoverageRun {
        // UN generador y UN snapshot por hilo (el catálogo no se reconstruye por fila).
        private val generator = CoverageFixtures.personalizer()
        private val snapshot = CoverageFixtures.snapshot(
            PersonalizedPlanCatalog.entries(),
            CatalogCompositionTestSupport.catalog,
        )
        private val equipmentIdByConfiguration: Map<String, String> = CatalogCompositionTestSupport.catalog.families
            .flatMap { it.definitions }
            .flatMap { it.configurations }
            .associate { it.id to it.profile.equipmentId }
        private val memo = HashMap<Spec, Outcome>()
        private var currentOrder = 0

        var realEvaluations = 0
            private set
        var memoHits = 0
            private set
        var rows = 0
            private set
        var ready = 0
            private set
        val honestByReason = TreeMap<String, Int>()
        val repairedBy = TreeMap<String, Int>()
        val violations = ArrayList<Violation>()
        val groups = LinkedHashMap<String, GroupStats>()

        /** Procesa UNA fila de la rejilla; [order] es su posición dentro del tier. */
        suspend fun process(order: Int, row: Spec) {
            currentOrder = order
            rows++
            val group = groups.getOrPut(row.group) { GroupStats() }
            group.rows++
            when (val outcome = evaluate(row)) {
                is Outcome.Failed -> violate(ViolationKind.EXCEPTION, row, "THROWN", outcome.message)
                is Outcome.Ready -> {
                    ready++
                    group.ready++
                    checkMaterialUsed(row, outcome)
                }
                is Outcome.Rejected -> classifyRejected(row, outcome, group)
            }
        }

        private suspend fun evaluate(spec: Spec): Outcome {
            val cached = memo[spec]
            if (cached != null) {
                memoHits++
                return cached
            }
            realEvaluations++
            val outcome = compute(spec)
            memo[spec] = outcome
            return outcome
        }

        private suspend fun compute(spec: Spec): Outcome {
            val cardioType = spec.cardio?.type ?: CardioType.WALK
            val request = requestOf(spec)
            return try {
                when (
                    val result = PlanCandidateEvaluator.evaluate(
                        request = request,
                        snapshot = snapshot,
                        entryId = spec.profile.nativeKind.entryId,
                        engine = CoverageFixtures.materializer(generator, spec.experience, spec.fixture, cardioType),
                    )
                ) {
                    PlanCandidateEvaluation.CatalogLoading -> Outcome.Failed("catálogo no listo (CatalogLoading)")
                    is PlanCandidateEvaluation.Ready -> Outcome.Ready(usedEquipmentIds(result.preparedPlan))
                    is PlanCandidateEvaluation.Rejected -> Outcome.Rejected(
                        reason = result.reasonCode,
                        stage = result.stage,
                        requiredMinutes = result.requiredMinutes,
                        details = result.details.orEmpty().take(300),
                        missingRequirements = result.missingRequirements,
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Outcome.Failed("${error::class.java.simpleName}: ${error.message.orEmpty().take(200)}")
            }
        }

        /** `equipmentId` de cada configuración de fuerza del programa (el cardio no cuenta). */
        private fun usedEquipmentIds(program: Program): Set<String> = program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .flatMap { it.sessions }
            .flatMap { it.allExercises() }
            .filter { it.cardioDetails == null }
            .mapNotNull { it.catalogConfigurationId }
            .mapNotNull { equipmentIdByConfiguration[it] }
            .toSet()

        private fun Outcome.describe(): String = when (this) {
            is Outcome.Ready -> "Ready"
            is Outcome.Rejected ->
                if (reason == PlanRejectionReason.TIME_BUDGET) "TIME_BUDGET(requiredMinutes=$requiredMinutes)" else reason.name
            is Outcome.Failed -> "FAILED(${message.take(80)})"
        }

        private fun violate(kind: ViolationKind, row: Spec, reason: String, detail: String) {
            violations += Violation(currentOrder, kind, row.key, row.group, reason, detail)
            groups.getValue(row.group).violationsByKind.merge(kind, 1, Int::plus)
        }

        // ── C5 ──

        private fun checkMaterialUsed(row: Spec, outcome: Outcome.Ready) {
            if (row.profile != Profile.MUSCLE && row.profile != Profile.COMPLETE_ATHLETE) return
            val expected = MATERIAL_EQUIPMENT_IDS[row.fixture.id] ?: return
            if (outcome.usedEquipmentIds.none { it in expected }) {
                violate(
                    ViolationKind.MATERIAL_UNUSED,
                    row,
                    "READY",
                    "el plan Ready no usa ninguna configuración de ${expected.sorted()}; usa ${outcome.usedEquipmentIds.sorted()}",
                )
            }
        }

        // ── C1: honestidad del rechazo ──

        /**
         * Evidencia explícita de ausencia (A.B1/B2): el rechazo trae `missingRequirements` y CADA token tiene una
         * ausencia declarada en el fixture. `barbell` es una categoría (ausente = sin marcar con la disponibilidad
         * confirmada); `rack` y `bench` son soportes, ausentes solo si se negaron TODAS las llaves que los acreditan
         * (B7). Una lista vacía no prueba nada: el rechazo sería «declaraste ausente» sin decir qué.
         */
        private fun hasExplicitAbsenceEvidence(row: Spec, missingRequirements: List<String>): Boolean {
            if (missingRequirements.isEmpty()) return false
            val availability = row.fixture.availability
            return missingRequirements.all { token ->
                if (token == "barbell") {
                    EquipmentCategory.BARBELL !in availability.categories
                } else {
                    val owners = EFFECTIVE_EQUIPMENT_KEYS.filter { token in it.attestedTokens }
                    owners.isNotEmpty() && owners.all { availability.presenceOf(it.key) == ApparatusPresence.ABSENT }
                }
            }
        }

        /** APPARATUS_UNKNOWN honesto (C2): trae la lista y cada token resuelve una llave confirmable del panel. */
        private fun unknownHasConfirmableKeys(missingRequirements: List<String>): Boolean =
            missingRequirements.isNotEmpty() && missingRequirements.all { SetupApparatusPanel.keyForToken(it) != null }

        private suspend fun classifyRejected(row: Spec, rejected: Outcome.Rejected, group: GroupStats) {
            val reason = rejected.reason
            val note = "etapa=${rejected.stage} ${rejected.details} requisitos=${rejected.missingRequirements}"
            when (reason) {
                PlanRejectionReason.APPARATUS_UNKNOWN -> {
                    if (unknownHasConfirmableKeys(rejected.missingRequirements)) {
                        handleHonest(row, rejected, group)
                    } else {
                        violate(
                            ViolationKind.NOT_HONEST,
                            row,
                            reason.name,
                            "APPARATUS_UNKNOWN sin llave confirmable (cada requisito debe resolver una llave del panel): $note",
                        )
                    }
                }
                PlanRejectionReason.APPARATUS_ABSENT -> {
                    if (hasExplicitAbsenceEvidence(row, rejected.missingRequirements)) {
                        handleHonest(row, rejected, group)
                    } else {
                        violate(
                            ViolationKind.DISHONEST_ABSENT,
                            row,
                            reason.name,
                            "APPARATUS_ABSENT sin evidencia explícita en ${row.fixture.id} (solo UNKNOWN o sin requisitos): $note",
                        )
                    }
                }
                PlanRejectionReason.PROFILE_MISMATCH -> handleHonest(row, rejected, group)
                PlanRejectionReason.TIME_BUDGET -> {
                    val required = rejected.requiredMinutes
                    if (required != null && required > row.minutes && required <= MAX_SESSION_MINUTES) {
                        handleHonest(row, rejected, group)
                    } else {
                        violate(
                            ViolationKind.TIME_BUDGET_OUT_OF_RANGE,
                            row,
                            reason.name,
                            "requiredMinutes=$required fuera de (${row.minutes}, $MAX_SESSION_MINUTES]: $note",
                        )
                    }
                }
                PlanRejectionReason.COMPOSITION,
                PlanRejectionReason.INTERNAL_MATERIALIZATION,
                PlanRejectionReason.CATALOG_NOT_READY,
                PlanRejectionReason.UNRESOLVED_CONFIGURATION,
                PlanRejectionReason.RECIPE_UNAVAILABLE,
                PlanRejectionReason.LOAD_BASIS_UNREPRESENTABLE,
                PlanRejectionReason.NO_VALID_SUBSTITUTION,
                PlanRejectionReason.FREQUENCY,
                PlanRejectionReason.SPLIT,
                PlanRejectionReason.LEVEL_UNSUITABLE ->
                    violate(ViolationKind.NOT_HONEST, row, reason.name, note)
            }
        }

        private suspend fun handleHonest(row: Spec, rejected: Outcome.Rejected, group: GroupStats) {
            honestByReason.merge(rejected.reason.name, 1, Int::plus)
            group.honest++
            val repair = findRepair(row, rejected)
            if (repair != null) {
                repairedBy.merge(repair, 1, Int::plus)
            } else {
                violate(
                    ViolationKind.NO_REPAIR,
                    row,
                    rejected.reason.name,
                    "ninguna reparación de un toque deja el plan Ready: etapa=${rejected.stage} ${rejected.details}",
                )
            }
            // A.C2: el mínimo que informa el generador es el exacto (viable con él y no viable con uno menos).
            if (rejected.reason == PlanRejectionReason.TIME_BUDGET) checkTimeBudgetIsExact(row, rejected)
        }

        /** `time_budget_minimum_is_exact`: con `requiredMinutes` hay plan y con `requiredMinutes - 1` sigue siendo TIME_BUDGET. */
        private suspend fun checkTimeBudgetIsExact(row: Spec, rejected: Outcome.Rejected) {
            val required = rejected.requiredMinutes ?: return
            val atRequired = evaluate(row.copy(minutes = required))
            val below = evaluate(row.copy(minutes = required - 1))
            val belowIsTimeBudget = below is Outcome.Rejected && below.reason == PlanRejectionReason.TIME_BUDGET
            if (atRequired !is Outcome.Ready || !belowIsTimeBudget) {
                violate(
                    ViolationKind.TIME_BUDGET_INEXACT,
                    row,
                    rejected.reason.name,
                    "requiredMinutes=$required: con $required min -> ${atRequired.describe()}; " +
                        "con ${required - 1} min -> ${below.describe()}",
                )
            }
        }

        // ── Reparaciones de un toque: el asesor del dominio (A.C1), ya no una simulación propia ──

        /**
         * Nombre de la primera reparación (o cadena de reparaciones) que deja el plan `Ready`, o null si ninguna lo
         * logra. La decide [PlanRepairAdvisor.suggest] —la misma función que usa el wizard— y cada candidata se prueba
         * con el `evaluate(spec)` de este hilo (con memo), igual que el resto del contrato. Los nombres son los de
         * siempre: `SetMinutes`, `SetCardioMinutes`, `ConfirmApparatus`, `ConfirmApparatus+SetMinutes`, `SwitchGoal`,
         * `SwitchGoal+SetMinutes` y `ClearSplit`.
         */
        private suspend fun findRepair(row: Spec, rejected: Outcome.Rejected): String? {
            val repairs = PlanRepairAdvisor.suggest(
                request = requestOf(row),
                rejected = rejected.asEvaluation(row),
                availability = row.fixture.availability,
            ) { probeRequest, probeAvailability -> probe(row, probeRequest, probeAvailability) }
            return nameOf(repairs)
        }

        private fun nameOf(repairs: List<PlanRepair>): String? = repairs.takeIf { it.isNotEmpty() }
            ?.joinToString("+") { repair ->
                when (repair) {
                    is PlanRepair.SetMinutes -> "SetMinutes"
                    is PlanRepair.SetCardioMinutes -> "SetCardioMinutes"
                    is PlanRepair.ConfirmApparatus -> "ConfirmApparatus"
                    is PlanRepair.SwitchGoal -> if (repair.alsoMinutes != null) "SwitchGoal+SetMinutes" else "SwitchGoal"
                    PlanRepair.ClearSplit -> "ClearSplit"
                }
            }

        /** El pedido normalizado de una fila (el mismo que evalúa `compute`). */
        private fun requestOf(spec: Spec): PlanCandidateRequest = CoverageFixtures.request(
            spec.profile,
            spec.experience,
            spec.days,
            spec.minutes,
            spec.fixture,
            cardioMinutes = spec.cardioMinutes ?: 10,
            cardioType = spec.cardio?.type ?: CardioType.WALK,
        )

        /**
         * Evaluación de prueba que el asesor pide: traduce (pedido, material) de vuelta a una fila de la rejilla y usa el
         * `evaluate(spec)` memoizado de este hilo. De paso comprueba que el asesor arma el pedido del destino como lo
         * arma el wizard (la referencia propia de cada objetivo y el cardio solo en Atleta completo), que es justo lo
         * que este contrato no vería porque reconstruye el pedido desde la fila.
         */
        private suspend fun probe(
            row: Spec,
            request: PlanCandidateRequest,
            availability: EquipmentAvailability,
        ): PlanCandidateEvaluation {
            val profile = Profile.entries.first { it.planGoal == request.goalProfile }
            val isAthlete = profile == Profile.COMPLETE_ATHLETE
            check(request.reference == profile.reference) {
                "el asesor arma ${profile.name} con la referencia ${request.reference} y el wizard con ${profile.reference}"
            }
            check(request.requiresCardio == isAthlete) {
                "el asesor arma ${profile.name} con requiresCardio=${request.requiresCardio}"
            }
            val fixture = if (availability == row.fixture.availability) {
                row.fixture
            } else {
                EquipmentFixture("${row.fixture.id}+repair", availability)
            }
            val spec = row.copy(
                profile = profile,
                minutes = request.minutesPerSession,
                fixture = fixture,
                cardio = if (isAthlete) row.cardio ?: CardioChoice.WALK else null,
                cardioMinutes = if (isAthlete) request.cardioMinutes else null,
            )
            return when (val outcome = evaluate(spec)) {
                is Outcome.Ready -> CoverageFixtures.stubReady(profile.nativeKind.entryId, request.inputKey)
                is Outcome.Rejected -> outcome.asEvaluation(spec)
                is Outcome.Failed -> PlanCandidateEvaluation.Rejected(
                    planId = profile.nativeKind.entryId,
                    stage = PlanEvaluationStage.MATERIALIZATION,
                    reasonCode = PlanRejectionReason.INTERNAL_MATERIALIZATION,
                    details = outcome.message,
                )
            }
        }

        private fun Outcome.Rejected.asEvaluation(spec: Spec) = PlanCandidateEvaluation.Rejected(
            planId = spec.profile.nativeKind.entryId,
            stage = stage,
            reasonCode = reason,
            requiredMinutes = requiredMinutes,
            details = details,
            missingRequirements = missingRequirements,
        )
    }

    /** Resultado agregado de todos los hilos, con las violaciones ordenadas por posición de fila. */
    private class Summary(runs: List<CoverageRun>, groupOrder: List<String>) {
        val rows: Int = runs.sumOf { it.rows }
        val ready: Int = runs.sumOf { it.ready }
        val realEvaluations: Int = runs.sumOf { it.realEvaluations }
        val memoHits: Int = runs.sumOf { it.memoHits }
        val honestByReason = TreeMap<String, Int>()
        val repairedBy = TreeMap<String, Int>()
        val violations: List<Violation> = runs.flatMap { it.violations }.sortedBy { it.order }
        val groups = LinkedHashMap<String, GroupStats>()

        init {
            runs.forEach { run ->
                run.honestByReason.forEach { (reason, count) -> honestByReason.merge(reason, count, Int::plus) }
                run.repairedBy.forEach { (repair, count) -> repairedBy.merge(repair, count, Int::plus) }
            }
            groupOrder.forEach { groups[it] = GroupStats() }
            runs.forEach { run -> run.groups.forEach { (group, stats) -> groups.getValue(group).add(stats) } }
        }
    }

    // ─── Rejilla, tiers, hilos e informe ──────────────────────────────────────────────────

    private fun buildGrid(fixtures: List<EquipmentFixture>): List<Spec> = buildList {
        for (profile in Profile.entries) {
            for (experience in GRID_EXPERIENCES) {
                for (days in 1..6) {
                    for (minutes in GRID_MINUTES) {
                        for (fixture in fixtures) {
                            if (profile == Profile.COMPLETE_ATHLETE) {
                                for (choice in CardioChoice.entries) {
                                    for (cardioMinutes in GRID_CARDIO_MINUTES) {
                                        add(Spec(profile, experience, days, minutes, fixture, choice, cardioMinutes))
                                    }
                                }
                            } else {
                                add(Spec(profile, experience, days, minutes, fixture, null, null))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resolveTier(): Tier {
        val raw = (System.getenv("KPKN_COVERAGE_TIER") ?: System.getProperty("kpkn.coverage.tier"))
            ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "smoke"
        return when (raw) {
            "smoke" -> Tier("smoke", null)
            "full" -> Tier("full", null)
            "ci" -> {
                val shard = (System.getenv("KPKN_COVERAGE_SHARD") ?: System.getProperty("kpkn.coverage.shard"))
                    ?.trim()?.toIntOrNull() ?: 0
                require(shard in 0..3) { "KPKN_COVERAGE_SHARD debe estar entre 0 y 3 (llegó $shard)" }
                Tier("ci", shard)
            }
            else -> error("KPKN_COVERAGE_TIER desconocido: $raw (valores: smoke, ci, full)")
        }
    }

    private fun inTier(tier: Tier, key: String): Boolean = when (tier.name) {
        "smoke" -> inSmokeSlice(key)
        "ci" -> shardOf(key) == tier.shard
        else -> true
    }

    /** Hilos de la corrida: lo pedido (o un cuarto de los núcleos, 1..4) sin pasar de lo que aguanta la memoria del JVM. */
    private fun resolveWorkers(): Int {
        val requested = (System.getenv("KPKN_COVERAGE_WORKERS") ?: System.getProperty("kpkn.coverage.workers"))
            ?.trim()?.toIntOrNull()
        val byCores = (Runtime.getRuntime().availableProcessors() / 4).coerceIn(1, 4)
        val maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)
        val byMemory = ((maxMemoryMb - 192L) / 64L).toInt().coerceAtLeast(1)
        return (requested ?: byCores).coerceIn(1, 8).coerceAtMost(byMemory)
    }

    private fun reportDirectory(): File {
        val base = File(System.getProperty("user.dir"))
        val buildDir = listOf(File(base, "build"), File(base, "app/build"), File(base, "android-native/app/build"))
            .firstOrNull { it.isDirectory } ?: File(base, "build")
        return File(buildDir, "reports/coverage-contract")
    }

    private fun writeText(tier: Tier, suffix: String, text: String): File {
        val directory = reportDirectory()
        directory.mkdirs()
        val file = File(directory, "${tier.fileStem}$suffix")
        file.writeText(text)
        return file
    }

    /** Hasta [max] ejemplos, empezando por el primero de cada (objetivo, fixture) para que haya variedad. */
    private fun pickExamples(list: List<Violation>, max: Int): List<Violation> {
        val seenGroups = HashSet<String>()
        val firstPerGroup = list.filter { seenGroups.add(it.group) }.take(max)
        if (firstPerGroup.size >= max) return firstPerGroup
        val chosen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Violation, Boolean>())
        chosen.addAll(firstPerGroup)
        return firstPerGroup + list.filter { it !in chosen }.take(max - firstPerGroup.size)
    }

    private fun buildReport(tier: Tier, gridRows: Int, summary: Summary, elapsedMs: Long, workers: Int): String = buildString {
        val violations = summary.violations
        appendLine("== PlanCoverageContractTest · tier=${tier.key} ==")
        appendLine("filas de la rejilla C1: $gridRows · filas evaluadas en este tier: ${summary.rows}")
        appendLine("Ready: ${summary.ready}")
        appendLine("rechazos honestos (antes de reparar): ${summary.honestByReason.values.sum()} por motivo ${summary.honestByReason}")
        appendLine("reparaciones que dejaron el plan Ready (primera que lo logra): ${summary.repairedBy}")
        appendLine("violaciones: ${violations.size}")
        appendLine()
        appendLine("-- Violaciones por clase --")
        ViolationKind.entries.forEach { kind ->
            appendLine("  ${kind.name}: ${violations.count { it.kind == kind }}")
        }
        appendLine()
        appendLine("-- Violaciones por clase y motivo --")
        ViolationKind.entries.forEach { kind ->
            val byReason = TreeMap<String, Int>()
            violations.filter { it.kind == kind }.forEach { byReason.merge(it.reason, 1, Int::plus) }
            if (byReason.isNotEmpty()) appendLine("  ${kind.name}: $byReason")
        }
        appendLine()
        appendLine("-- Desglose por (objetivo, fixture): filas / Ready / rechazos honestos / violaciones --")
        summary.groups.forEach { (group, stats) ->
            val kinds = ViolationKind.entries
                .filter { (stats.violationsByKind[it] ?: 0) > 0 }
                .joinToString(", ") { "${it.name}=${stats.violationsByKind.getValue(it)}" }
            appendLine(
                "  $group  filas=${stats.rows} ready=${stats.ready} honestos=${stats.honest} " +
                    "violaciones=${stats.violationsByKind.values.sum()}" + if (kinds.isNotEmpty()) " [$kinds]" else "",
            )
        }
        appendLine()
        appendLine("-- Baseline para el ratchet (calculado a partir de las claves de las violaciones) --")
        val perShard = (0..3).map { shard -> violations.count { shardOf(it.key) == shard } }
        val smokeSlice = violations.count { inSmokeSlice(it.key) }
        appendLine(
            "  smoke=$smokeSlice ci/0=${perShard[0]} ci/1=${perShard[1]} ci/2=${perShard[2]} ci/3=${perShard[3]} " +
                "full=${violations.size}  (solo es completo en tier=full; en smoke/ci vale su propia rebanada)",
        )
        appendLine()
        appendLine("-- Ejemplos (hasta $EXAMPLES_PER_KIND por clase) --")
        ViolationKind.entries.forEach { kind ->
            val ofKind = violations.filter { it.kind == kind }
            if (ofKind.isEmpty()) return@forEach
            appendLine("  [${kind.name}] (${ofKind.size})")
            pickExamples(ofKind, EXAMPLES_PER_KIND).forEach { appendLine("    ${it.key}  (${it.reason}) ${it.detail}") }
        }
        appendLine()
        appendLine(
            "tiempo total: ${elapsedMs / 1000.0} s con $workers hilo(s) (evaluaciones reales=${summary.realEvaluations}, " +
                "resueltas por memo=${summary.memoHits}; memoria máxima del JVM de pruebas = " +
                "${Runtime.getRuntime().maxMemory() / (1024L * 1024L)} MB)",
        )
    }

    // ─── Pruebas ────────────────────────────────────────────────────────────────────────────

    @Test
    fun C1_own_plan_is_ready_or_honestly_rejected_with_one_tap_repair() {
        // TODO A.C4 (C3): falta cablear PlanRejectionPresenter.primary en la UI para que UI y test lo compartan.
        // TODO A.D4 (C4): el pase "a peso corporal" solo si todos los rechazos son APPARATUS_* (hoy no existe).
        val tier = resolveTier()
        val allRows = buildGrid(CoverageFixtures.allFixtures())
        assertEquals("rejilla C1 completa", EXPECTED_GRID_ROWS, allRows.size)
        assertEquals("claves de fila únicas", allRows.size, allRows.map { it.key }.toSet().size)
        val rows = allRows.filter { inTier(tier, it.key) }
        assertTrue("el tier ${tier.key} no tiene filas", rows.isNotEmpty())

        // Un trozo = una familia (mismos minutos aparte): sus reparaciones por minutos reutilizan el memo del hilo.
        val chunks = rows.withIndex().groupBy { it.value.familyKey }.values.toList()
        val workers = resolveWorkers()
        // Los generadores y los snapshots se crean aquí, antes de lanzar los hilos.
        val runs = List(workers) { CoverageRun() }
        val nextChunk = AtomicInteger(0)
        val processedRows = AtomicInteger(0)
        val progressLock = Any()
        val started = System.nanoTime()

        val executor = Executors.newFixedThreadPool(workers)
        try {
            val futures = runs.map { run ->
                executor.submit(
                    Callable {
                        runBlocking {
                            while (true) {
                                val index = nextChunk.getAndIncrement()
                                if (index >= chunks.size) break
                                val chunk = chunks[index]
                                for ((order, row) in chunk) run.process(order, row)
                                val after = processedRows.addAndGet(chunk.size)
                                if (after / 1_000 != (after - chunk.size) / 1_000 || after == rows.size) {
                                    synchronized(progressLock) {
                                        val elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000L
                                        writeText(
                                            tier,
                                            ".progress.txt",
                                            "filas=$after/${rows.size} transcurridos=${elapsedSeconds}s hilos=$workers\n",
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
            futures.forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L

        val summary = Summary(runs, rows.map { it.group }.distinct())
        assertEquals("todas las filas del tier se procesaron", rows.size, summary.rows)
        val report = buildReport(tier, allRows.size, summary, elapsedMs, workers)
        println(report)
        val reportFile = writeText(tier, ".txt", report)
        println("[C1] informe escrito en ${reportFile.absolutePath}")

        // Ratchet: la suite siempre está en verde; solo falla si se supera el techo de este tier.
        val ceiling = VIOLATION_CEILING.getValue(tier.key)
        val found = summary.violations.size
        if (found < ceiling) println("[C1] techo bajable a $found (tier=${tier.key}, techo actual=$ceiling)")
        assertTrue(
            "C1: $found violaciones superan el techo $ceiling del tier ${tier.key}. Informe: ${reportFile.absolutePath}\n" +
                summary.violations.take(15).joinToString("\n") { "  [${it.kind}] ${it.key} (${it.reason}) ${it.detail}" },
            found <= ceiling,
        )
    }

    @Test
    fun fixtures_have_unique_ids_and_expected_evidence() {
        val fixtures = CoverageFixtures.allFixtures()
        assertEquals("19 fixtures", 19, fixtures.size)
        assertEquals("ids E0..E18 en orden", (0..18).map { "E$it" }, fixtures.map { it.id })
        assertEquals("ids únicos", fixtures.size, fixtures.map { it.id }.toSet().size)
        assertEquals("E0–E7 son los legacy", (0..7).map { "E$it" }, CoverageFixtures.legacyFixtures().map { it.id })
        assertEquals("E8–E18 son los extendidos", (8..18).map { "E$it" }, CoverageFixtures.extendedFixtures().map { it.id })

        val byId = fixtures.associateBy { it.id }
        fun fixture(id: String) = requireNotNull(byId[id]) { "fixture inexistente $id" }
        val present = ApparatusPresence.PRESENT
        val allCategories = EquipmentCategory.entries.toSet()

        // E8: gimnasio sin confirmar, todo UNKNOWN.
        fixture("E8").availability.let {
            assertEquals(allCategories, it.categories)
            assertTrue("E8 no declara ninguna llave", it.apparatus.isEmpty() && it.supports.isEmpty())
            assertFalse("E8 no declara presencia explícita", it.hasExplicitPresence)
        }

        // E9: máquinas y poleas llave por llave + banco plano.
        val machineAndCableKeys = EFFECTIVE_EQUIPMENT_KEYS
            .filter { it.category == EquipmentCategory.MACHINES || it.category == EquipmentCategory.CABLE }
            .map { it.key }
            .toSet()
        val e9 = fixture("E9").availability
        assertEquals(setOf(EquipmentCategory.MACHINES, EquipmentCategory.CABLE), e9.categories)
        assertEquals(machineAndCableKeys, e9.apparatus.keys)
        assertTrue("E9: todas las llaves de máquina y polea en PRESENT", e9.apparatus.values.all { it == present })
        assertEquals(mapOf(EquipmentKeys.BENCH_FLAT to present), e9.supports)

        // E10: solo kettlebell.
        fixture("E10").let {
            assertEquals(setOf(EquipmentCategory.KETTLEBELL), it.availability.categories)
            assertTrue(it.availability.apparatus.isEmpty() && it.availability.supports.isEmpty())
            assertTrue("kettlebell" in it.tokens)
        }

        // E11: Smith + banco (el panel no tiene llave de Smith).
        fixture("E11").let {
            assertEquals(setOf(EquipmentCategory.SMITH_MACHINE, EquipmentCategory.SUPPORT), it.availability.categories)
            assertEquals(mapOf(EquipmentKeys.BENCH_FLAT to present), it.availability.supports)
            assertTrue(it.availability.apparatus.isEmpty())
            assertTrue("smith_machine" in it.tokens && "bench" in it.tokens)
        }

        // E12: banda + barra de dominadas. E13: solo barra de dominadas.
        fixture("E12").let {
            assertEquals(setOf(EquipmentCategory.BAND, EquipmentCategory.PULL_UP_BAR), it.availability.categories)
            assertEquals(mapOf(EquipmentKeys.PULLUP_BAR to present), it.availability.supports)
            assertTrue("band" in it.tokens && "pull_up_bar" in it.tokens)
        }
        fixture("E13").let {
            assertEquals(setOf(EquipmentCategory.PULL_UP_BAR), it.availability.categories)
            assertEquals(mapOf(EquipmentKeys.PULLUP_BAR to present), it.availability.supports)
            assertTrue("pull_up_bar" in it.tokens)
        }

        // E14: E9 + cardio + bici exterior.
        val e14 = fixture("E14").availability
        assertEquals(e9.categories + EquipmentCategory.CARDIO, e14.categories)
        assertEquals(e9.apparatus + (SetupApparatusPanel.OUTDOOR_BIKE_KEY to present), e14.apparatus)
        assertEquals(e9.supports, e14.supports)

        // E15: como E5 pero con rack y bancos negados de forma explícita (evidencia ABSENT).
        val e5 = fixture("E5").availability
        val negated = setOf(EquipmentKeys.SQUAT_RACK, EquipmentKeys.BENCH_FLAT, EquipmentKeys.BENCH_ADJUSTABLE)
        fixture("E15").let {
            assertEquals(allCategories, it.availability.categories)
            assertEquals(e5.supports.keys, it.availability.supports.keys)
            e5.supports.keys.forEach { key ->
                val expected = if (key in negated) ApparatusPresence.ABSENT else present
                assertEquals("E15 $key", expected, it.availability.supports.getValue(key))
            }
            assertTrue(it.availability.supports.values.any { value -> value == ApparatusPresence.ABSENT })
            assertTrue("rack y banco negados", "rack" !in it.tokens && "bench" !in it.tokens)
        }

        // E16: barra sola. E17: barra + mancuernas + banco sin rack declarado.
        fixture("E16").let {
            assertEquals(setOf(EquipmentCategory.BARBELL), it.availability.categories)
            assertTrue(it.availability.supports.isEmpty() && it.availability.apparatus.isEmpty())
            assertTrue("barbell" in it.tokens && "rack" !in it.tokens && "bench" !in it.tokens)
        }
        fixture("E17").let {
            assertEquals(
                setOf(EquipmentCategory.BARBELL, EquipmentCategory.DUMBBELLS, EquipmentCategory.SUPPORT),
                it.availability.categories,
            )
            assertEquals(
                mapOf(EquipmentKeys.BENCH_FLAT to present, EquipmentKeys.BENCH_ADJUSTABLE to present),
                it.availability.supports,
            )
            assertTrue("barbell" in it.tokens && "dumbbells" in it.tokens && "bench" in it.tokens && "rack" !in it.tokens)
        }

        // E18: como E5 (soportes confirmados) y solo la prensa declarada.
        fixture("E18").availability.let {
            assertEquals(allCategories, it.categories)
            assertEquals(mapOf(EquipmentKeys.LEG_PRESS to present), it.apparatus)
            assertEquals(e5.supports, it.supports)
        }
    }
}
