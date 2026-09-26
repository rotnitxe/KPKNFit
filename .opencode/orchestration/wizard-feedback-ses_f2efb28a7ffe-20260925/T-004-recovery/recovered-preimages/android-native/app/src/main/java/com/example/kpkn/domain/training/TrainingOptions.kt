package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.firstCompoundWarmupPercentSets
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Opciones de entrenamiento reales que el contrato del onboarding incorpora al
 * motor (sin sustituirlo): bolsa de prioridades de orden, modo de
 * autorregulación (PROPOSE por defecto, AUTO exige confirmación), inventario
 * honesto y plan de calentamiento del primer compuesto.
 *
 * Vive en `domain/training` como modelo común puro: `domain/onboarding` lo
 * re-exporta como `SetupTrainingOptions` (typealias) para que el dueño del
 * draft conserve su API sin acoplar `domain/training` a `domain/onboarding`.
 *
 * Contrato:
 * - [inventory] es nullable HASTA que el usuario declara su material. Declarar
 *   un inventario NUEVO exige cantidades explícitas y finitas (el lector legacy
 *   de `Settings.availablePlates` conserva cantidades nulas al leer backups
 *   antiguos, pero una declaración nueva se rechaza si no es explícita, si hay
 *   discos sin peso de barra —nunca se asume 20 kg— o si una máquina trae pasos
 *   NaN/Infinity o en cero). Con inventario null la viabilidad de cargas se
 *   resuelve en el motor (`PlanMaterializer.realizeWarmupLoads` contra el
 *   inventario guardado en Settings, con [WarmupFeasibility] viajando en el
 *   plan), nunca como material ilimitado inventado.
 * - [orderPriorities] es la bolsa de orden (máximo 2 puntos por músculo, 5 en
 *   total, sin negativos): solo orden de ejercicios; jamás ejercicios, series,
 *   repeticiones, intensidades, frecuencia ni volumen.
 * - [autoregulationMode] nace en PROPOSE. AUTO solo es válido tras una
 *   confirmación explícita ([automaticConfirmed]) que la UI debe exponer.
 * - [warmup], cuando se declara, sustituye al preset del plan
 *   (40 % × 8, 60 % × 5, 80 % × 3 sobre la carga de trabajo) para el primer
 *   compuesto de cada patrón. Vacío = sin calentamientos automáticos. Los pasos
 *   efectivos quedan normalizados (orden ascendente y sin duplicados dentro de
 *   ±5 puntos porcentuales, nunca por encima del 100 %); las recetas de autor
 *   no se tocan.
 */
@Serializable
data class TrainingOptions(
    val inventory: EquipmentInventory? = null,
    val orderPriorities: Map<String, Int> = emptyMap(),
    val autoregulationMode: AutoregulationMode = AutoregulationMode.PROPOSE,
    val automaticConfirmed: Boolean = false,
    val warmup: List<SetRecipe>? = null,
) {
    /**
     * Validación pura del contrato. No toca motor: devuelve el motivo exacto
     * cuando la configuración no se puede aplicar.
     */
    fun validate(): TrainingValidation {

        val reasons = mutableListOf<String>()
        if (autoregulationMode == AutoregulationMode.AUTO && !automaticConfirmed) {
            reasons += "La autorregulación AUTO requiere confirmación explícita del usuario antes de activarse."
        }
        if (orderPriorities.any { it.key.isBlank() }) {
            reasons += "La bolsa de prioridades no puede contener músculos vacíos."
        }
        if (orderPointsFromBag(orderPriorities) == null) {
            reasons += "La bolsa de prioridades de orden no es válida: máximo 2 puntos por músculo, 5 en total y ningún punto negativo."
        }
        warmup.orEmpty().forEachIndexed { index, step ->
            val percent = step.percent
            if (percent == null || !percent.isFinite() || percent <= 0.0 || percent > 100.0) {
                reasons += "El paso de calentamiento $index debe tener un porcentaje válido entre 0 y 100 (un calentamiento nunca supera la carga de trabajo)."
            }
            val reps = step.reps
            if (reps == null || reps !in 1..60) {
                reasons += "El paso de calentamiento $index debe indicar entre 1 y 60 repeticiones."
            }
        }
        return if (reasons.isEmpty()) TrainingValidation.Valid else TrainingValidation.Invalid(reasons.distinct())
    }

    /**
     * Aplica las opciones a un [PersonalizerInput] del motor: la bolsa de
     * prioridades pasa como [PersonalizerInput.exerciseOrderPriorities] (el
     * motor sigue siendo quien resuelve catálogo, volúmenes y proveniencia).
     * Solo sobreescribe la bolsa cuando viene con puntos.
     */
    fun applyTo(input: PersonalizerInput): PersonalizerInput =
        if (orderPriorities.isNotEmpty()) input.copy(exerciseOrderPriorities = orderPriorities) else input

    /**
     * Aplica las opciones a un [Program] ya existente sin tocar su estructura
     * ni su receta: solo el modo de autorregulación elegido.
     */
    fun applyTo(program: Program): Program = program.copy(autoregulationMode = autoregulationMode)

    /**
     * Pasos de calentamiento efectivos: los declarados en [warmup] (vacío =
     * sin calentamientos) o, si no se declaró nada, el preset del plan
     * 40 % × 8, 60 % × 5, 80 % × 3 sobre la carga de trabajo. Siempre
     * normalizados: orden ascendente por porcentaje y duplicados equivalentes
     * (±5 puntos porcentuales) colapsados. La normalización solo aplica a los
     * pasos del plan (preset/declarados): los calentamientos que traiga una
     * receta de autor se conservan intactos en `PlanMaterializer.assignWarmups`.
     */
    fun resolvedWarmupSteps(): List<SetRecipe> = normalizedWarmupSteps(warmup ?: firstCompoundWarmupPercentSets())

    /**
     * Inventario nuevo solo se acepta con cantidades explícitas y finitas: una
     * declaración no puede dejar `countPerSide` nulo (eso significaría discos
     * ilimitados), ni valores NaN/Infinity, ni barra fantasma de 20 kg. El
     * lector legacy de backups antiguos (`Settings.availablePlates` →
     * cantidades nulas) sigue funcionando porque la compatibilidad se resuelve
     * al leer; aquí solo se valida lo que el usuario DECLARA como nuevo.
     * Con inventario null no se afirma nada: material desconocido hasta declarar.
     */
    private fun inventoryHonestyReasons(inventory: EquipmentInventory?): List<String> {
        if (inventory == null) return emptyList()
        val reasons = mutableListOf<String>()
        val bar = inventory.barbellWeightKg
        if (bar == null) {
            // Sin barra declarada solo es honesto si tampoco hay discos: con
            // discos y sin barra el sistema acabaría asumiendo 20 kg.
            if (inventory.plates.isNotEmpty()) {
                reasons += "Una declaración nueva con discos debe indicar el peso real de la barra (nunca se asume 20 kg)."
            }
        } else if (!bar.isFinite() || bar <= 0.0) {
            reasons += "El peso de la barra debe ser positivo y finito."
        }
        inventory.plates.forEach { stock ->
            if (!stock.weightKg.isFinite() || stock.weightKg <= 0.0) reasons += "Un disco del inventario tiene un peso inválido."
            if (stock.countPerSide == null) {
                reasons += "El inventario nuevo debe declarar la cantidad de discos por lado (countPerSide explícito)."
            }
            if (stock.countPerSide != null && stock.countPerSide < 0) reasons += "Las cantidades de disco por lado no pueden ser negativas."
        }
        inventory.dumbbells.forEach { pair ->
            if (!pair.weightPerUnitKg.isFinite() || pair.weightPerUnitKg <= 0.0) reasons += "Una mancuerna del inventario tiene un peso inválido."
        }
        inventory.kettlebells.forEach { kb ->
            if (!kb.weightKg.isFinite() || kb.weightKg <= 0.0) reasons += "Una kettlebell del inventario tiene un peso inválido."
        }
        inventory.machines.forEach { machine ->
            if (machine.name.isBlank()) reasons += "Una máquina del inventario nuevo debe identificarse por su nombre."
            if (!machine.incrementKg.isFinite() || machine.incrementKg <= 0.0) reasons += "El paso de la máquina '${machine.name}' debe ser positivo y finito."
            if (!machine.minLoadKg.isFinite() || machine.minLoadKg < 0.0) reasons += "El mínimo de la máquina '${machine.name}' no puede ser negativo ni NaN/Infinity."
            if (machine.maxLoadKg != null && (!machine.maxLoadKg.isFinite() || machine.maxLoadKg < machine.minLoadKg)) {
                reasons += "El máximo de la máquina '${machine.name}' debe ser finito y no menor que su mínimo."
            }
            if (!machine.baseLoadKg.isFinite() || machine.baseLoadKg < 0.0) reasons += "La carga base de la máquina '${machine.name}' debe ser finita y no negativa."
            if (machine.maxLoadKg != null && machine.baseLoadKg.isFinite() && machine.baseLoadKg > machine.maxLoadKg) {
                reasons += "La carga base de la máquina '${machine.name}' no puede superar su máximo."
            }
        }
        return reasons
    }
}

sealed interface TrainingValidation {
    data object Valid : TrainingValidation
    data class Invalid(val reasons: List<String>) : TrainingValidation
}

/**
 * **Equipo efectivo compartido**: la única API de equipo para el readiness y
 * los candidatos del wizard (M2) y para el filtro real del motor
 * ([SimpleCyclePersonalizer]); una sola salida para no poder divergir.
 *
 * Contrato:
 * - [TrainingOptions.inventory] **null** → compatibilidad legacy: se devuelve el
 *   perfil tal cual (solo normalización de vocabulario: `bands`→`band`,
 *   `smith`→`smith_machine`). No se añade ni se quita nada.
 * - [TrainingOptions.inventory] **declarado** → manda lo que el material real
 *   acredita. Siempre `bodyweight` (no necesita material: la ruta 100 % peso
 *   corporal sigue viva y el readiness nunca queda vacío), más `barbell`,
 *   `dumbbells` y `kettlebell` cuando existen; `supportEquipment` aporta sus
 *   ids canónicos (`support`, `pull_up_bar`, `band`, `ball`, `cardio`).
 *   Maquinaria: la **presencia** `machine` sigue atestiguada por
 *   `machines.isNotEmpty()` (informativa, sin aprobar ejercicios) y, además,
 *   `machines[].equipmentKind` acredita la estación declarada (`cable` o
 *   `smith_machine`, multi-ejercicio) y `machines[].configurationId` emite el
 *   token [machineConfigToken] con la configuración real del catálogo.
 *   `general_gym` (reclamo «todo el gimnasio») se excluye siempre.
 * - Disponibilidad legacy explícita **sin modelo de inventario** (`band`,
 *   `pull_up_bar`, `support`, `ball`, `cardio`…) se conserva; con
 *   `supportEquipment` declarado se acredita además la presencia real.
 * - Solo **acreditación de presencia**: aquí no se resuelven cargas (eso es
 *   `PlanMaterializer.realizeWarmupLoads` + `WarmupFeasibility`, con matching
 *   `configurationId` ↔ ejercicio).
 */
fun TrainingOptions.effectiveEquipment(legacyEquipment: Set<String>): Set<String> {
    val legacy = legacyEquipment.mapTo(LinkedHashSet<String>()) { normalizeLegacyEquipmentKind(it) }
    val declared = inventory ?: return legacy
    val hasBar = declared.barbellWeightKg?.let { it.isFinite() && it > 0.0 } == true || declared.plates.isNotEmpty()
    val hasMachines = declared.machines.isNotEmpty()
    val kinds = linkedSetOf(KIND_BODYWEIGHT)
    if (hasBar) kinds += KIND_BARBELL
    if (declared.dumbbells.isNotEmpty()) kinds += KIND_DUMBBELLS
    if (declared.kettlebells.isNotEmpty()) kinds += KIND_KETTLEBELL
    // Presencia de maquinaria (informativa; NO aprueba ejercicios: eso lo
    // decide el token de configuración en el filtro del motor y en la guardia).
    if (hasMachines) kinds += KIND_MACHINE
    // Maquinaria desde SUS campos declarados (M4): estación multi-ejercicio por
    // `equipmentKind` (`cable`/`smith_machine` son válidos por ser estación) y
    // máquina concreta por `configurationId` → token interno. El nombre de la
    // fila nunca infiere nada; `equipmentKind = machine` sin `configurationId`
    // no aprueba ninguna máquina (leg curl ≠ chest press ≠ prensa).
    declared.machines.forEach { machine ->
        val station = machine.equipmentKind?.trim().orEmpty()
        if (station == KIND_CABLE || station == KIND_SMITH) kinds += station
        machine.configurationId?.trim()?.takeIf { it.isNotBlank() }?.let { kinds += machineConfigToken(it) }
    }
    // Material auxiliar declarado a mano (ids canónicos: support, pull_up_bar,
    // band, ball, cardio): presencia acreditada, sin auto-relleno del default.
    declared.supportEquipment.forEach { id ->
        if (id.isNotBlank()) kinds += normalizeLegacyEquipmentKind(id)
    }
    legacy.forEach { kind ->
        when {
            // Reclamo «todo el gimnasio»: nunca sobrevive a una declaración.
            kind == KIND_GENERAL_GYM -> Unit
            kind in kinds -> Unit
            // Sin modelo de inventario: disponibilidad explícita del usuario.
            kind !in INVENTORY_ATTESTED_KINDS -> kinds += kind
            kind == KIND_BARBELL && hasBar -> kinds += kind
            kind == KIND_DUMBBELLS && declared.dumbbells.isNotEmpty() -> kinds += kind
            kind == KIND_KETTLEBELL && declared.kettlebells.isNotEmpty() -> kinds += kind
            // Maquinaria del perfil: solo con maquinaria real declarada (el
            // kind genérico sigue sin aprobar nada por sí mismo).
            kind in MACHINE_KINDS && hasMachines -> kinds += kind
            // Acreditable por el modelo y sin presencia real: no se concede.
            else -> Unit
        }
    }
    return kinds
}

/** Kind de equipo: peso corporal, siempre disponible sin material. */
private const val KIND_BODYWEIGHT = "bodyweight"
/** Reclamo «todo el gimnasio»; nunca se asume con inventario declarado. */
private const val KIND_GENERAL_GYM = "general_gym"
private const val KIND_BARBELL = "barbell"
private const val KIND_DUMBBELLS = "dumbbells"
private const val KIND_KETTLEBELL = "kettlebell"
private const val KIND_MACHINE = "machine"
/** Estación multi-ejercicio: solo con `equipmentKind` explícito o chip legacy. */
private const val KIND_CABLE = "cable"
private const val KIND_SMITH = "smith_machine"
private const val KIND_PLATE = "plate"
private const val KIND_BAND = "band"
private const val KIND_EZ_BAR = "ez_bar"
private const val KIND_TRX = "trx"
private const val KIND_SAFETY_BAR = "safety_bar"

/**
 * Token interno (puro, sin estado) que acredita UNA máquina concreta del
 * catálogo real: `machine_config:<configurationId>`.
 *
 * Solo lo emite `effectiveEquipment` cuando el inventario declara la
 * configuración de la máquina (campo `MachineLoadRange.configurationId`, M4);
 * lo consumen el filtro real del motor y la guardia de recetas fijas. Nunca se
 * sustituye por el kind genérico `machine`: una máquina declarada no acredita
 * otras máquinas (leg curl ≠ chest press ≠ prensa).
 */
internal fun machineConfigToken(configurationId: String): String = "machine_config:$configurationId"

/**
 * Kind canónico de material a partir del rótulo del catálogo
 * (`ExerciseMuscleInfo.equipment`, que `ExerciseCatalogV2LegacyAdapter` genera
 * con `equipmentLabel(...)`; los ids desconocidos pasan tal cual, porque ese
 * rótulo hace `?: id`). Es el inverso de ese rótulo + los plurales legacy: NO
 * inventa equivalencias (un rótulo no reconocido → null y la ruta de cargas
 * cae al comportamiento histórico de discos, sin asumir material).
 *
 * Consumido por la ruta de calentamientos del workout para decidir qué motor
 * resuelve la carga: discos (por defecto), mancuerna por pareja, kettlebell,
 * máquina por configuración exacta o estación cable/Smith declarada.
 */
fun canonicalEquipmentKind(label: String?): String? {
    val raw = label?.trim()?.lowercase().orEmpty()
    if (raw.isEmpty()) return null
    return when (raw) {
        "barra" -> KIND_BARBELL
        "mancuerna", "mancuernas" -> KIND_DUMBBELLS
        "máquina", "maquina" -> KIND_MACHINE
        "polea" -> KIND_CABLE
        "peso corporal" -> KIND_BODYWEIGHT
        "disco", "discos" -> KIND_PLATE
        "banda", "bandas" -> KIND_BAND
        "kettlebell", "kettlebells" -> KIND_KETTLEBELL
        "barra ez" -> KIND_EZ_BAR
        "trx" -> KIND_TRX
        "máquina smith", "maquina smith" -> KIND_SMITH
        "barra de seguridad" -> KIND_SAFETY_BAR
        // Ya es un id canónico (el rótulo de catálogo cae al propio id).
        else -> raw.takeIf { it in CANONICAL_EQUIPMENT_KINDS }
    }
}

/** Ids canónicos de material que el catálogo puede traer como rótulo-id. */
private val CANONICAL_EQUIPMENT_KINDS = setOf(
    KIND_BARBELL, KIND_DUMBBELLS, KIND_MACHINE, KIND_CABLE, KIND_BODYWEIGHT, KIND_PLATE,
    KIND_BAND, KIND_KETTLEBELL, KIND_EZ_BAR, KIND_TRX, KIND_SMITH, KIND_SAFETY_BAR,
    "hex_bar", "t_bar", "h_bar", "ghd", "sliders", "wrist_roller", "ab_wheel",
)

/**
 * Kinds que el inventario declarado PUEDE acreditar con presencia real: para
 * ellos la declaración manda y no se concede nada sin material.
 */
private val INVENTORY_ATTESTED_KINDS = setOf(KIND_BARBELL, KIND_DUMBBELLS, KIND_KETTLEBELL, KIND_MACHINE, "cable", "smith_machine")

/** Kinds de maquinaria distintos de la genérica: solo si hay maquinaria real Y el usuario los declaró. */
private val MACHINE_KINDS = setOf(KIND_MACHINE, "cable", "smith_machine")

/**
 * Normalización compartida del vocabulario legacy de equipo (mismo criterio que
 * usaba el motor en privado): un único punto de verdad para el wizard y para
 * el filtro real de `SimpleCyclePersonalizer`.
 */
internal fun normalizeLegacyEquipmentKind(kind: String): String = when (kind) {
    "bands" -> "band"
    "smith" -> "smith_machine"
    else -> kind
}

/**
 * Normalización de los pasos de calentamiento del plan (preset o declarados):
 * solo pasos válidos (0 < % ≤ 100, 1..60 reps), orden ascendente por porcentaje
 * y colapso de pasos equivalentes dentro de ±5 puntos porcentuales (gana el más
 * liviano, igual que la política anti-redundancia del materializador). NO toca
 * calentamientos de recetas de autor: en `PlanMaterializer.assignWarmups` los
 * `recipeWarmups` se conservan íntegros y esta normalización solo aplica a los
 * pasos del plan que se añaden.
 */
internal fun normalizedWarmupSteps(steps: List<SetRecipe>): List<SetRecipe> {
    val valid = steps.filter { step ->
        val percent = step.percent
        val reps = step.reps
        percent != null && percent.isFinite() && percent > 0.0 && percent <= 100.0 && reps != null && reps in 1..60
    }.sortedBy { it.percent!! }
    val out = ArrayList<SetRecipe>(valid.size)
    for (step in valid) {
        val percent = step.percent!!
        if (out.isEmpty() || abs(out.last().percent!! - percent) > 5.0) out += step
    }
    return out
}
