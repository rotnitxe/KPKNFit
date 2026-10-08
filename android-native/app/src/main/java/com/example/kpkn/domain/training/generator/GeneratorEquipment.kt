package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.training.ConfigurationEquipmentFilter
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.resolveEffectiveEquipment

/**
 * Material de UN lugar (una sesión se arma solo con el material de su lugar).
 *
 * No tiene lógica de material propia: los tokens salen del MISMO resolutor de equipo efectivo que el resto de la app
 * (`TrainingOptions.resolveEffectiveEquipment`, que ya acredita anillas, cajón, cuerda de saltar, barra baja de un parque
 * y los extras de gimnasio) y cada configuración se decide con el MISMO filtro que el planificador
 * ([ConfigurationEquipmentFilter]: implemento, máquina concreta, soportes de `supportRequirementsFor`). Solo se añaden
 * los requisitos propios de la reserva (el parámetro `requires` de [allows]).
 */
internal class DayEquipment(val availability: EquipmentAvailability) {

    /** Símbolos reconocibles (anillas, cajón y cuerda incluidos). */
    val symbols: Set<EquipmentSymbolId> = EquipmentSymbols.selectedFrom(availability)

    val bodyweightOnly: Boolean = EquipmentSymbols.isBodyweightOnly(availability)

    private val options = TrainingOptions(availability = availability)

    val tokens: Set<String> = options.resolveEffectiveEquipment(emptySet()).tokens

    private val exactMachines: Boolean = ConfigurationEquipmentFilter.requiresExactMachineConfiguration(options)

    /** ¿Se cumple un requisito? «a|b» = cualquiera de las llaves. */
    fun satisfied(requirement: String): Boolean = requirement.split('|').any { it in tokens }

    fun has(token: String): Boolean = token in tokens

    /**
     * ¿Se puede ejecutar la configuración con este material? El filtro compartido sobre el equipo efectivo (con las piezas
     * que la entrada del catálogo ya trae calculadas) y, además, los requisitos propios de la reserva ([requires]).
     */
    fun allows(entry: CatalogEntry, requires: List<String>): Boolean =
        ConfigurationEquipmentFilter.allowsPrecomputed(
            equipmentId = entry.equipmentId,
            machineToken = entry.machineToken,
            supportRequirements = entry.sharedRequirements,
            tokens = tokens,
            requireExactMachineConfiguration = exactMachines,
        ) && requires.all { satisfied(it) }

    /** Aparatos de cardio que este lugar permite (siempre caminar y correr al aire libre). */
    val cardioTypes: List<CardioType> = buildList {
        add(CardioType.WALK)
        add(CardioType.RUN_OUTDOOR)
        if ("cardio" in tokens) {
            add(CardioType.TREADMILL)
            add(CardioType.BIKE_STATIONARY)
            add(CardioType.ELLIPTICAL)
            add(CardioType.ROW_MACHINE)
        }
        if (availability.presenceOf(SetupApparatusPanel.OUTDOOR_BIKE_KEY) == ApparatusPresence.PRESENT) add(CardioType.BIKE_OUTDOOR)
    }

    val hasCardioMachines: Boolean get() = "cardio" in tokens

    /**
     * Sin símbolo ni ejercicio que la use (comba: pendiente de alta en el catálogo): lo único que acredita la llave
     * `jump_rope` es un borrador o unos ajustes antiguos. Se deja para el lote de catálogo que dé de alta la comba.
     */
    val hasJumpRope: Boolean get() = "jump_rope" in tokens
}
