package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
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
 * - [availability], cuando no es null, es la fuente categórica del equipo para
 *   readiness, candidatos y el filtro real del motor. No afirma stock ni
 *   configuraciones exactas. Cuando es null, [effectiveEquipment] conserva las
 *   reglas legacy/inventario anteriores.
 */
@Serializable
data class TrainingOptions(
    val inventory: EquipmentInventory? = null,
    val orderPriorities: Map<String, Int> = emptyMap(),
    val autoregulationMode: AutoregulationMode = AutoregulationMode.PROPOSE,
    val automaticConfirmed: Boolean = false,
    val warmup: List<SetRecipe>? = null,
    val availability: EquipmentAvailability? = null,
) {
    /**
     * Validación pura del contrato. No toca motor: devuelve el motivo exacto
     * cuando la configuración no se puede aplicar.
     */
    fun validate(): TrainingValidation = validate(includeInventoryHonesty = true)

    /** Selection validation ignores independent legacy stock errors only for a categorical declaration. */
    internal fun validateForSelection(): TrainingValidation =
        validate(includeInventoryHonesty = availability == null)

    private fun validate(includeInventoryHonesty: Boolean): TrainingValidation {
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
        if (includeInventoryHonesty) reasons += inventoryHonestyReasons(inventory)
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
 * Origen de la evidencia de UN token emitido por
 * [TrainingOptions.resolveEffectiveEquipment]: la UI lo muestra para que el
 * usuario pueda corregirlo (§13.1 regla 2 «su origen se muestra y puede
 * corregirse»).
 */
enum class EffectiveEquipmentOrigin {
    /** Peso corporal: siempre disponible, no exige material. */
    BODYWEIGHT,
    /** Categoría confirmada en el wizard (§13.1 regla 1: delimita). */
    CONFIRMED_CATEGORY,
    /** Clave de aparato confirmada `PRESENT` (§13.1 regla 2). */
    CONFIRMED_APPARATUS,
    /** Clave de soporte confirmada `PRESENT` (§13.1 regla 2). */
    CONFIRMED_SUPPORT,
    /** Inventario previo exacto, admitido dentro de categorías confirmadas (regla 3). */
    DECLARED_INVENTORY,
    /** Perfil legacy (chips o inventario) para LEER programas previos (regla 4). */
    LEGACY_PROFILE,
    /** Paraguas legacy del chip `support` sobre la clase de soportes (AC-C3). */
    LEGACY_SUPPORT_ATTESTATION,
}

/**
 * Evidencia de UN requisito de soporte (§13.2): `PRESENT` está acreditado,
 * `ABSENT` fue negado explícitamente o cae en una categoría sin ese ítem, y
 * `UNKNOWN` simplemente falta confirmar. La UI distingue así `APPARATUS_ABSENT`
 * de `APPARATUS_UNKNOWN` (§15.2).
 */
enum class RequirementEvidence { PRESENT, ABSENT, UNKNOWN }

/**
 * Resultado estructurado del resolver (§13.1): tokens, origen de cada evidencia
 * y estado de cada requisito del vocabulario. [tokens] es exactamente lo que
 * consumen readiness, candidatos, el filtro del motor y la guardia de recetas
 * fijas; el resto viaja para que ninguna ruta tenga que re-inferirlo.
 */
data class EffectiveEquipmentResult(
    /** Tokens de material acreditados, en orden de emisión. */
    val tokens: Set<String>,
    /** Origen de cada token emitido. */
    val origins: Map<String, EffectiveEquipmentOrigin>,
    /** Estado de cada requisito del vocabulario (`KNOWN_REQUIREMENTS`). */
    val requirements: Map<String, RequirementEvidence>,
) {
    /** Requisitos acreditados. */
    val presentRequirements: Set<String>
        get() = requirements.filterValues { it == RequirementEvidence.PRESENT }.keys

    /** Requisitos negados explícitamente (o sin su categoría). */
    val missingRequirements: Set<String>
        get() = requirements.filterValues { it == RequirementEvidence.ABSENT }.keys

    /** Requisitos sin confirmar: aún no se sabe si existen. */
    val unknownRequirements: Set<String>
        get() = requirements.filterValues { it == RequirementEvidence.UNKNOWN }.keys
}

/**
 * **Resolutor único de equipo efectivo** (§13.1): la única API de equipo para el
 * readiness y los candidatos del wizard (M2) y para el filtro real del motor
 * ([SimpleCyclePersonalizer]); una sola implementación para no poder divergir.
 * No es estado mutable: una función pura sobre [TrainingOptions] + el perfil
 * legacy que trae el draft.
 *
 * Reglas:
 * 1. Las categorías confirmadas delimitan el equipo disponible: una categoría
 *    desmarcada no se reintroduce desde el inventario antiguo. Una respuesta
 *    confirmada con categorías vacías es solo cuerpo.
 * 2. Dentro de las categorías permitidas, la presencia concreta reciente manda:
 *    `PRESENT` habilita SOLO el mapeo curado de esa clave y `ABSENT` elimina el
 *    aparato y sus configuraciones aunque exista inventario previo; `UNKNOWN`
 *    no niega la evidencia exacta del inventario, pero deja su origen visible.
 * 3. El inventario previo aporta presencia verificable (máquina con
 *    `configurationId`, estación por `equipmentKind`, implemento) solo dentro de
 *    categorías confirmadas y sin contradicción; nunca aporta kilos que no
 *    están escritos ni acredita nada genérico.
 * 4. Sin disponibilidad nueva se conserva la compatibilidad legacy para LEER
 *    programas previos (chips + inventario, con el paraguas de `support` de
 *    [LEGACY_SUPPORT_ATTESTED_REQUIREMENTS]); `general_gym` nunca sobrevive a
 *    una declaración de inventario.
 * 5. `bodyweight` se emite siempre en la ruta nueva (no necesita material) y
 *    `machine_config:<id>` SOLO desde el mapeo curado o desde el inventario
 *    exacto, jamás porque se marcó `MACHINES`. Que la categoría `MACHINES` abra
 *    o no el resto de las variantes de máquina no lo decide este resolutor sino
 *    el filtro ([ConfigurationEquipmentFilter]): lo abre cuando la declaración es
 *    una sala de máquinas ([EquipmentAvailability.machinesAsCategory]) y no cuando
 *    son llaves sueltas del subpanel.
 */
fun TrainingOptions.resolveEffectiveEquipment(legacyEquipment: Set<String>): EffectiveEquipmentResult =
    availability?.let { resolveWithAvailability(it) } ?: resolveWithLegacy(legacyEquipment)

/**
 * Equipo efectivo compartido: vista `[Set<String>]` de
 * [TrainingOptions.resolveEffectiveEquipment] (una sola implementación, sin
 * segunda salida) para readiness, candidatos, el filtro real del motor y la
 * guardia de recetas fijas.
 *
 * Contrato de los tokens:
 * - [TrainingOptions.availability] no nula → `bodyweight` + categorías
 *   confirmadas + mapeo curado de las claves `PRESENT` (las del subpanel y las
 *   de símbolo de [SYMBOL_EQUIPMENT_KEYS]: `trx`/`rings`, `plyo_box`,
 *   `jump_rope`, `plate`, `hex_bar`, `t_bar`, `ghd`, `ab_wheel` y la barra baja
 *   de un parque). No emite `general_gym` ni `free_weights`, y
 *   `machine_config:<id>` solo desde mapeo curado o inventario exacto.
 * - [TrainingOptions.inventory] **null** (y sin availability) → perfil legacy
 *   intacto, solo normalizado (`bands`→`band`, `smith`→`smith_machine`).
 * - [TrainingOptions.inventory] **declarado** → manda lo que el material real
 *   acredita: `bodyweight` siempre (la ruta 100 % peso corporal sigue viva),
 *   `barbell`/`dumbbells`/`kettlebell` si existen, `machine` como presencia
 *   informativa (sin aprobar ejercicios), estaciones por `equipmentKind` y
 *   `machine_config:<configurationId>` por la configuración real declarada.
 *   `general_gym` se excluye siempre.
 * - Con `support` acreditado (chip/inventario legacy), la clase de soportes
 *   legacy queda atestiguada (§13.1 regla 4); en la ruta de disponibilidad NO
 *   existe ese paraguas.
 * - Solo **acreditación de presencia**: aquí no se resuelven cargas (eso es
 *   `PlanMaterializer.realizeWarmupLoads` + `WarmupFeasibility`, con matching
 *   `configurationId` ↔ ejercicio).
 */
fun TrainingOptions.effectiveEquipment(legacyEquipment: Set<String>): Set<String> =
    resolveEffectiveEquipment(legacyEquipment).tokens

/** Ruta nueva: categorías confirmadas + presencia concreta + inventario acotado. */
private fun TrainingOptions.resolveWithAvailability(declared: EquipmentAvailability): EffectiveEquipmentResult {
    val origins = LinkedHashMap<String, EffectiveEquipmentOrigin>()
    fun put(token: String, origin: EffectiveEquipmentOrigin) {
        // La última escritura gana: la evidencia más concreta y más reciente
        // (clave > inventario > categoría) es la que se muestra.
        origins[token] = origin
    }

    put(KIND_BODYWEIGHT, EffectiveEquipmentOrigin.BODYWEIGHT)
    // Regla 1: las categorías delimitan. Una categoría con TODAS sus claves
    // concretas ausentes pierde su token: la ausencia explícita gana.
    EquipmentCategory.entries.forEach { category ->
        // Regla 1: las categorías delimitan; una desmarcada en una respuesta
        // confirmada no se reintroduce (ni siquiera su token).
        if (category !in declared.categories) return@forEach
        val owners = equipmentKeysOf(category)
        val denied = owners.isNotEmpty() &&
            owners.all { declared.presenceOf(it.key) == ApparatusPresence.ABSENT }
        if (!denied) put(category.canonicalToken(), EffectiveEquipmentOrigin.CONFIRMED_CATEGORY)
    }
    // Regla 3: inventario previo admitido solo dentro de categorías
    // confirmadas y sin una ausencia explícita que lo contradiga.
    inventory?.let { stock ->
        val hasBar = stock.barbellWeightKg?.let { it.isFinite() && it > 0.0 } == true || stock.plates.isNotEmpty()
        if (hasBar && EquipmentCategory.BARBELL in declared.categories) {
            put(KIND_BARBELL, EffectiveEquipmentOrigin.DECLARED_INVENTORY)
        }
        if (stock.dumbbells.isNotEmpty() && EquipmentCategory.DUMBBELLS in declared.categories) {
            put(KIND_DUMBBELLS, EffectiveEquipmentOrigin.DECLARED_INVENTORY)
        }
        if (stock.kettlebells.isNotEmpty() && EquipmentCategory.KETTLEBELL in declared.categories) {
            put(KIND_KETTLEBELL, EffectiveEquipmentOrigin.DECLARED_INVENTORY)
        }
        stock.machines.forEach { machine ->
            val station = machine.equipmentKind?.trim().orEmpty()
            val stationCategory = when (station) {
                KIND_CABLE -> EquipmentCategory.CABLE
                KIND_SMITH -> EquipmentCategory.SMITH_MACHINE
                else -> null
            }
            if (stationCategory != null && stationCategory in declared.categories) {
                put(station, EffectiveEquipmentOrigin.DECLARED_INVENTORY)
            }
            val configurationId = machine.configurationId?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
            val category = stationCategory ?: EquipmentCategory.MACHINES
            if (category !in declared.categories) return@forEach
            if (configurationDeniedByAbsentKey(configurationId, declared)) return@forEach
            put(machineConfigToken(configurationId), EffectiveEquipmentOrigin.DECLARED_INVENTORY)
        }
    }
    // Regla 2: presencia concreta reciente. `PRESENT` habilita únicamente el
    // mapeo curado de su clave; `UNKNOWN` no acredita nada nuevo (y las claves
    // sin id verificado no habilitan nada: regla STOP, nunca se inventa).
    EFFECTIVE_EQUIPMENT_KEYS.forEach { spec ->
        if (declared.presenceOf(spec.key) != ApparatusPresence.PRESENT) return@forEach
        val gate = if (spec.category == null) {
            // Sin categoría propia: exige una respuesta confirmada, no vacía.
            declared.categories.isNotEmpty()
        } else {
            spec.category in declared.categories
        }
        if (!gate) return@forEach
        val origin = if (spec.machineConfigurations.isNotEmpty()) {
            EffectiveEquipmentOrigin.CONFIRMED_APPARATUS
        } else {
            EffectiveEquipmentOrigin.CONFIRMED_SUPPORT
        }
        spec.machineConfigurations.forEach { put(machineConfigToken(it), origin) }
        spec.attestedTokens.forEach { put(it, origin) }
    }
    // Llaves de símbolo que el subpanel no pinta (anillas, cajón, cuerda de saltar, barra baja de un parque y extras de
    // gimnasio): misma regla (presencia `PRESENT` con su categoría confirmada), con su propia lista. Es lo único que
    // las acredita: el generador de rutinas ya no las añade por su cuenta.
    SYMBOL_EQUIPMENT_KEYS.forEach { spec ->
        if (declared.presenceOf(spec.key) != ApparatusPresence.PRESENT) return@forEach
        if (spec.category !in declared.categories) return@forEach
        spec.attestedTokens.forEach { put(it, EffectiveEquipmentOrigin.CONFIRMED_SUPPORT) }
    }
    return EffectiveEquipmentResult(
        tokens = origins.keys.toSet(),
        origins = origins.toMap(),
        requirements = requirementEvidence(origins.keys, declared),
    )
}

/** Ruta legacy: perfil de chips + inventario declarado, con paraguas de soportes. */
private fun TrainingOptions.resolveWithLegacy(legacyEquipment: Set<String>): EffectiveEquipmentResult {
    val legacy = legacyEquipment.mapTo(LinkedHashSet<String>()) { normalizeLegacyEquipmentKind(it) }
    val origins = LinkedHashMap<String, EffectiveEquipmentOrigin>()
    fun put(kind: String, origin: EffectiveEquipmentOrigin) {
        // Primera escritura gana: el origen declarado se conserva.
        if (kind !in origins) origins[kind] = origin
    }

    val declared = inventory
    if (declared == null) {
        legacy.forEach { put(it, EffectiveEquipmentOrigin.LEGACY_PROFILE) }
        applyLegacySupportUmbrella(origins)
        return EffectiveEquipmentResult(
            tokens = origins.keys.toSet(),
            origins = origins.toMap(),
            requirements = requirementEvidence(origins.keys, availability = null),
        )
    }
    val hasBar = declared.barbellWeightKg?.let { it.isFinite() && it > 0.0 } == true || declared.plates.isNotEmpty()
    val hasMachines = declared.machines.isNotEmpty()
    put(KIND_BODYWEIGHT, EffectiveEquipmentOrigin.BODYWEIGHT)
    if (hasBar) put(KIND_BARBELL, EffectiveEquipmentOrigin.LEGACY_PROFILE)
    if (declared.dumbbells.isNotEmpty()) put(KIND_DUMBBELLS, EffectiveEquipmentOrigin.LEGACY_PROFILE)
    if (declared.kettlebells.isNotEmpty()) put(KIND_KETTLEBELL, EffectiveEquipmentOrigin.LEGACY_PROFILE)
    // Presencia de maquinaria (informativa; NO aprueba ejercicios: eso lo
    // decide el token de configuración en el filtro del motor y en la guardia).
    if (hasMachines) put(KIND_MACHINE, EffectiveEquipmentOrigin.LEGACY_PROFILE)
    // Maquinaria desde SUS campos declarados (M4): estación multi-ejercicio por
    // `equipmentKind` (`cable`/`smith_machine` son válidos por ser estación) y
    // máquina concreta por `configurationId` → token interno. El nombre de la
    // fila nunca infiere nada; `equipmentKind = machine` sin `configurationId`
    // no aprueba ninguna máquina (leg curl ≠ chest press ≠ prensa).
    declared.machines.forEach { machine ->
        val station = machine.equipmentKind?.trim().orEmpty()
        if (station == KIND_CABLE || station == KIND_SMITH) put(station, EffectiveEquipmentOrigin.LEGACY_PROFILE)
        machine.configurationId?.trim()?.takeIf { it.isNotBlank() }
            ?.let { put(machineConfigToken(it), EffectiveEquipmentOrigin.DECLARED_INVENTORY) }
    }
    // Material auxiliar declarado a mano (ids canónicos: support, pull_up_bar,
    // band, ball, cardio): presencia acreditada, sin auto-relleno del default.
    declared.supportEquipment.forEach { id ->
        if (id.isNotBlank()) put(normalizeLegacyEquipmentKind(id), EffectiveEquipmentOrigin.LEGACY_PROFILE)
    }
    legacy.forEach { kind ->
        when {
            // Reclamo «todo el gimnasio»: nunca sobrevive a una declaración.
            kind == KIND_GENERAL_GYM -> Unit
            kind in origins -> Unit
            // Sin modelo de inventario: disponibilidad explícita del usuario.
            kind !in INVENTORY_ATTESTED_KINDS -> put(kind, EffectiveEquipmentOrigin.LEGACY_PROFILE)
            kind == KIND_BARBELL && hasBar -> put(kind, EffectiveEquipmentOrigin.LEGACY_PROFILE)
            kind == KIND_DUMBBELLS && declared.dumbbells.isNotEmpty() -> put(kind, EffectiveEquipmentOrigin.LEGACY_PROFILE)
            kind == KIND_KETTLEBELL && declared.kettlebells.isNotEmpty() -> put(kind, EffectiveEquipmentOrigin.LEGACY_PROFILE)
            // Maquinaria del perfil: solo con maquinaria real declarada (el
            // kind genérico sigue sin aprobar nada por sí mismo).
            kind in MACHINE_KINDS && hasMachines -> put(kind, EffectiveEquipmentOrigin.LEGACY_PROFILE)
            // Acreditable por el modelo y sin presencia real: no se concede.
            else -> Unit
        }
    }
    applyLegacySupportUmbrella(origins)
    return EffectiveEquipmentResult(
        tokens = origins.keys.toSet(),
        origins = origins.toMap(),
        requirements = requirementEvidence(origins.keys, availability = null),
    )
}

/**
 * Paraguas legacy (§13.1 regla 4, AC-C3): con el chip/inventario `support`
 * acreditado, la CLASE de soportes legacy queda atestiguada para poder LEER
 * programas previos sin rechazarlos por un requisito que el vocabulario
 * antiguo no podía expresar. Solo en las rutas legacy/inventario: la ruta de
 * disponibilidad atestigua por clave (AC-C2).
 */
private fun applyLegacySupportUmbrella(origins: LinkedHashMap<String, EffectiveEquipmentOrigin>) {
    if (KIND_SUPPORT !in origins) return
    LEGACY_SUPPORT_ATTESTED_REQUIREMENTS.forEach { requirement ->
        if (requirement !in origins) {
            origins[requirement] = EffectiveEquipmentOrigin.LEGACY_SUPPORT_ATTESTATION
        }
    }
}

/**
 * Estado de cada requisito del vocabulario para el resultado estructurado:
 * - `PRESENT`: el token está entre los acreditados (o el perfil legacy reclama
 *   `general_gym`, que en esa ruta solo sirve para leer programas previos).
 * - `ABSENT`: TODAS las claves que lo acreditan están `ABSENT` (y hay al menos una; paquete A · B7,
 *   antes bastaba una sola) o su categoría está confirmada y el token se cayó por ausencias. Caso
 *   típico: «banco plano = No» con «banco regulable» sin responder deja `bench` en `UNKNOWN`, porque
 *   el regulable también acredita `bench`; solo negar los dos lo deja `ABSENT`.
 * - `UNKNOWN`: falta confirmar (nunca se niega sin una respuesta explícita).
 */
private fun requirementEvidence(
    tokens: Set<String>,
    availability: EquipmentAvailability?,
): Map<String, RequirementEvidence> {
    val legacyBlanket = availability == null && KIND_GENERAL_GYM in tokens
    return KNOWN_REQUIREMENTS.associateWith { requirement ->
        val owners = EFFECTIVE_EQUIPMENT_KEYS.filter { requirement in it.attestedTokens }
        when {
            requirement in tokens || legacyBlanket -> RequirementEvidence.PRESENT
            availability == null -> RequirementEvidence.UNKNOWN
            owners.isNotEmpty() && owners.all { availability.presenceOf(it.key) == ApparatusPresence.ABSENT } ->
                RequirementEvidence.ABSENT
            else -> {
                val category = EquipmentCategory.entries.firstOrNull { it.canonicalToken() == requirement }
                if (category != null && category in availability.categories && requirement !in tokens) {
                    RequirementEvidence.ABSENT
                } else {
                    RequirementEvidence.UNKNOWN
                }
            }
        }
    }
}


private fun EquipmentCategory.canonicalToken(): String = when (this) {
    EquipmentCategory.BARBELL -> KIND_BARBELL
    EquipmentCategory.DUMBBELLS -> KIND_DUMBBELLS
    EquipmentCategory.KETTLEBELL -> KIND_KETTLEBELL
    EquipmentCategory.MACHINES -> KIND_MACHINE
    EquipmentCategory.CABLE -> KIND_CABLE
    EquipmentCategory.SMITH_MACHINE -> KIND_SMITH
    EquipmentCategory.BAND -> KIND_BAND
    EquipmentCategory.SUPPORT -> KIND_SUPPORT
    EquipmentCategory.PULL_UP_BAR -> "pull_up_bar"
    EquipmentCategory.BALL -> "ball"
    EquipmentCategory.CARDIO -> "cardio"
}

/** Kind de equipo: peso corporal, siempre disponible sin material. */
private const val KIND_BODYWEIGHT = "bodyweight"
/** Aparatos/soportes: el chip legacy que acredita la clase de soportes. */
private const val KIND_SUPPORT = "support"
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
