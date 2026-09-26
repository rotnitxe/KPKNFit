package com.example.kpkn.domain.training

import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.MachineLoadRange
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.domain.calculations.PlateCalculator
import com.example.kpkn.domain.workout.WarmupCalibrationEngine
import kotlin.math.abs

/** Resultado honesto de la viabilidad de los calentamientos frente al inventario finito. */
enum class WarmupFeasibilityStatus {
    /** Sin inventario o sin carga de trabajo: no se afirma nada (nunca ilimitado). */
    UNKNOWN,
    /** Todas las aproximaciones del plan son alcanzables con el inventario declarado. */
    REALIZABLE,
    /** Parte de las aproximaciones solo se aproximan con el inventario (redondeo/stock). */
    PARTIAL,
    /** Ninguna aproximación del plan es alcanzable con el inventario declarado. */
    UNREALIZABLE,
}

/** Paso concreto de calentamiento y su resolución contra el inventario real. */
data class WarmupFeasibilityStep(
    val percentage: Double,
    val requestedKg: Double,
    /**
     * Carga alcanzable con el material TÍPICO del ejercicio, o null cuando no
     * hay material declarado que la satisfaga (nunca 0 ni un valor inventado).
     */
    val realizedKg: Double?,
    val isExact: Boolean,
) {
    /** Dentro de la banda de redondeo (±5 %, tope del calibrador): cuenta como alcanzable. */
    val isRealizable: Boolean
        get() = isExact || (realizedKg != null && abs(realizedKg - requestedKg) <= 0.05 * requestedKg)
}

/**
 * Viabilidad honesta de los calentamientos del plan (preset 40 % × 8, 60 % × 5,
 * 80 % × 3 sobre la carga de trabajo, o pasos declarados en
 * [com.example.kpkn.domain.onboarding.SetupTrainingOptions.warmup]) contra el
 * inventario FINITO del usuario.
 *
 * Contrato:
 * - Inventario o carga de trabajo desconocidos → [WarmupFeasibilityStatus.UNKNOWN],
 *   nunca se presenta un inventario ilimitado inventado.
 * - Con inventario declarado se resuelve cada carga con [PlateCalculator]
 *   (nunca supera el stock); con [MachineLoadRange] declarada se resuelve
 *   contra su rango real (pasos de [MachineLoadRange.incrementKg]): todas
 *   exactas → REALIZABLE; algunas fuera de la banda de redondeo → PARTIAL;
 *   ninguna alcanzable → UNREALIZABLE.
 */
data class WarmupFeasibility(
    val status: WarmupFeasibilityStatus,
    val workingLoadKg: Double?,
    val inventory: EquipmentInventory?,
    val steps: List<WarmupFeasibilityStep> = emptyList(),
) {
    /** El resultado no afirma viabilidad sin datos (es decir, sin inventario inventado). */
    val isHonest: Boolean get() = status != WarmupFeasibilityStatus.UNKNOWN
}

/**
 * Matching **configurationId ↔ ejercicio** para la maquinaria (fine-machine):
 * una máquina declarada solo se aplica a un ejercicio si su configuración
 * declarada coincide con la del ejercicio — leg curl ≠ leg press ≠ chest press.
 *
 * Reglas:
 * - Sin máquina → null.
 * - Máquina **sin** `configurationId` (rango legacy o estación multi
 *   cable/Smith): no hay con qué verificar y quien la llamó ya la eligió → se
 *   respeta (compatibilidad legacy intacta).
 * - Ejercicio **sin** id verificable → no se afirma coincidencia: se respeta la
 *   elección del llamante (la disponibilidad la decide la guardia de equipo).
 * - Ambos ids presentes y distintos → null: la máquina NO se aplica y nada se
 *   afirma con ella (nunca kg ni viabilidad inventados).
 *
 * El id esperado es `ExerciseMuscleInfo.catalogConfigurationId`, que
 * `ExerciseCatalogV2LegacyAdapter` escribe como `configuration.id` (mismo id v2
 * que declara `MachineLoadRange.configurationId` en el inventario).
 */
internal fun machineRangeFor(machine: MachineLoadRange?, configurationId: String?): MachineLoadRange? {
    if (machine == null) return null
    val declared = machine.configurationId?.trim()?.takeIf { it.isNotBlank() } ?: return machine
    val exerciseConfigurationId = configurationId?.trim()?.takeIf { it.isNotBlank() } ?: return machine
    return machine.takeIf { declared == exerciseConfigurationId }
}

/**
 * Selecciona la máquina/rango REAL del inventario para un ejercicio:
 * 1. Fila cuya `configurationId` coincide exacta con la del ejercicio (una
 *    máquina concreta declarada).
 * 2. Estación multi-ejercicio declarada por su tipo (`cable` o `smith_machine`
 *    en `equipmentKind`) cuando el ejercicio es de ese tipo: válida por ser
 *    estación, sin inferir nada de otras máquinas.
 * 3. En cualquier otro caso → null: sin rango acreditado no se resuelve carga
 *    (porcentaje pendiente), nunca «cualquier máquina vale».
 */
fun EquipmentInventory.machineRangeForExercise(
    configurationId: String?,
    equipmentKind: String?,
): MachineLoadRange? {
    val id = configurationId?.trim().orEmpty()
    if (id.isNotEmpty()) {
        machines.firstOrNull { it.configurationId?.trim() == id }?.let { return it }
    }
    val kind = equipmentKind?.trim().orEmpty()
    if (kind == "cable" || kind == "smith_machine") {
        machines.firstOrNull { it.equipmentKind?.trim() == kind }?.let { return it }
    }
    return null
}

/**
 * Carga ALCANZABLE para una aproximación, según el material real del
 * ejercicio (un único punto de decisión para la ruta de calentamientos):
 * - [machine] (rango exacto o estación ya seleccionado) → pasos reales de la
 *   máquina.
 * - `dumbbells` → pareja declarada por unidad ([EquipmentInventory.resolveDumbbell]);
 *   sin pareja/stock alcanzable → null.
 * - `kettlebell` → la más pesada declarada que no supere el objetivo; si todas
 *   lo superan → null (nunca un kg por encima del material).
 * - `machine`/`cable`/`smith_machine` sin rango seleccionado → null (sin
 *   configuración exacta ni estación declarada no se afirma nada).
 * - `bodyweight` → null (no hay carga externa que resolver).
 * - `plate` → disco real declarado: el más pesado que no supere el objetivo
 *   (nunca se le añade una barra).
 * - `barra`/cualquier otro tipo (incluido rótulo desconocido) → discos, y
 *   SOLO con barra real declarada (peso finito > 0). Sin barra → null: el
 *   default de 20 kg de `resolvedBarbellWeightKg` es una ruta legacy que solo
 *   Settings suministra cuando hay barra real; nunca se inventa aquí.
 *
 * null = la ruta lo convierte en [WarmupLoadStatus.PENDING_PERCENT]: porcentaje
 * pendiente, jamás 0 ni kilogramos inventados.
 */
internal fun reachableWarmupLoad(
    requestedKg: Double,
    equipmentKind: String?,
    inventory: EquipmentInventory,
    machine: MachineLoadRange?,
): Double? {
    if (!requestedKg.isFinite() || requestedKg <= 0.0) return null
    if (machine != null) return machine.snapLoad(requestedKg).achievedKg
    return when (equipmentKind) {
        "dumbbells" -> inventory.resolveDumbbell(requestedKg).achievedPerUnitKg
        "kettlebell" -> inventory.kettlebells.asSequence()
            .map { it.weightKg }
            .filter { it.isFinite() && it <= requestedKg + 0.001 }
            .maxOrNull()
        "plate" -> reachablePlateOnly(requestedKg, inventory)
        "machine", "cable", "smith_machine", "bodyweight" -> null
        else -> {
            // Barra (o material desconocido tratado históricamente como barra):
            // exige barra REAL declarada antes de repartir discos.
            val bar = inventory.barbellWeightKg
            if (bar == null || !bar.isFinite() || bar <= 0.0) {
                null
            } else {
                PlateCalculator.calculatePlates(requestedKg, inventory).achievedWeight
            }
        }
    }
}

/**
 * Disco real sin barra: el disco más pesado declarado con existencias que no
 * supere el objetivo (`countPerSide = null` = legado ilimitado). Si no hay
 * ninguno alcanzable → null (pendiente), nunca una barra prestada.
 */
private fun reachablePlateOnly(requestedKg: Double, inventory: EquipmentInventory): Double? =
    inventory.plates.asSequence()
        .filter { stock ->
            stock.weightKg.isFinite() && stock.weightKg > 0.0 &&
                (stock.countPerSide == null || stock.countPerSide > 0) &&
                stock.weightKg <= requestedKg + 0.001
        }
        .map { it.weightKg }
        .maxOrNull()

object WarmupFeasibilityChecker {
    /** Banda de redondeo del calibrador (±5 %): cargas dentro se consideran alcanzables. */
    const val ROUNDING_TOLERANCE = 0.05

    fun of(
        workingLoadKg: Double?,
        inventory: EquipmentInventory?,
        warmups: List<WarmupSetDefinition>,
        machine: MachineLoadRange? = null,
        configurationId: String? = null,
        equipmentKind: String? = null,
    ): WarmupFeasibility {
        // Matching configurationId ↔ ejercicio: una máquina declarada que no
        // corresponde a esta configuración (leg curl ≠ leg press) no se aplica y
        // no se afirma nada con ella.
        val matchedMachine = machineRangeFor(machine, configurationId)
        if (machine != null && matchedMachine == null) {
            return WarmupFeasibility(WarmupFeasibilityStatus.UNKNOWN, workingLoadKg, inventory)
        }
        // Sin inventario y sin máquina: no se afirma nada (nunca ilimitado).
        if (inventory == null && matchedMachine == null) {
            return WarmupFeasibility(WarmupFeasibilityStatus.UNKNOWN, workingLoadKg, inventory)
        }
        // Sin carga de trabajo utilizable → etiqueta pendiente, nunca 0 kg.
        if (workingLoadKg == null || !workingLoadKg.isFinite() || workingLoadKg <= 0.0) {
            return WarmupFeasibility(WarmupFeasibilityStatus.UNKNOWN, workingLoadKg, inventory)
        }
        val material = inventory ?: EquipmentInventory()
        val steps = warmups.mapNotNull { warmup ->
            // Misma normalización de porcentaje que la calibración: 0.4 y 40
            // son el mismo 40 % (nunca 0.4 % ni 40×).
            val percentage = WarmupCalibrationEngine.normalizePercentage(warmup.percentageOfWorkingWeight)
            if (percentage <= 0.0 || !percentage.isFinite()) return@mapNotNull null
            val requested = workingLoadKg * percentage
            // Misma resolución TÍPICA que las entries del Realizer: mancuerna
            // con su pareja, kettlebell con sus campanas declaradas… sin aplicar
            // discos a todo, y sin decidir la viabilidad por una barra ajena.
            // null = no alcanzable con el material declarado.
            val realized = reachableWarmupLoad(
                requestedKg = requested,
                equipmentKind = equipmentKind,
                inventory = material,
                machine = matchedMachine,
            )
            WarmupFeasibilityStep(
                percentage = warmup.percentageOfWorkingWeight,
                requestedKg = requested,
                realizedKg = realized,
                isExact = realized != null && abs(realized - requested) < 0.01,
            )
        }
        if (steps.isEmpty()) {
            // Sin pasos que comprobar: no hay nada que proclamar como inviable.
            return WarmupFeasibility(WarmupFeasibilityStatus.REALIZABLE, workingLoadKg, inventory)
        }
        val realizable = steps.count { it.isRealizable }
        val status = when {
            realizable == steps.size -> WarmupFeasibilityStatus.REALIZABLE
            realizable == 0 -> WarmupFeasibilityStatus.UNREALIZABLE
            else -> WarmupFeasibilityStatus.PARTIAL
        }
        return WarmupFeasibility(status, workingLoadKg, inventory, steps)
    }
}