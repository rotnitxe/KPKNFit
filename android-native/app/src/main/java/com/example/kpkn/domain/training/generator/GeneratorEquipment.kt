package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.SetupApparatusPanel
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.hasExplicitMachinePresence
import com.example.kpkn.domain.training.resolveEffectiveEquipment

/**
 * Material de UN lugar (una sesión se arma solo con el material de su lugar).
 *
 * Usa el MISMO resolutor de equipo efectivo que el resto de la app (`TrainingOptions.resolveEffectiveEquipment`) y el
 * mismo filtro por configuración que el generador histórico (`equipmentAllows`: implemento, máquina concreta, soportes de
 * `supportRequirementsFor`), más lo que ese resolutor aún no acredita: anillas (`trx`), cajón y cuerda de saltar, que
 * viajan en `supports` con las llaves de [EquipmentSymbols].
 */
internal class DayEquipment(val availability: EquipmentAvailability) {

    /** Símbolos reconocibles (anillas, cajón y cuerda incluidos). */
    val symbols: Set<EquipmentSymbolId> = EquipmentSymbols.selectedFrom(availability)

    val bodyweightOnly: Boolean = EquipmentSymbols.isBodyweightOnly(availability)

    val tokens: Set<String> = buildSet {
        addAll(TrainingOptions(availability = availability).resolveEffectiveEquipment(emptySet()).tokens)
        if (availability.presenceOf(EquipmentSymbols.RINGS_KEY) == ApparatusPresence.PRESENT) {
            add("rings")
            // El catálogo llama `trx` al implemento de suspensión; el resolutor compartido no lo acredita desde ningún símbolo.
            add("trx")
        }
        if (availability.presenceOf(EquipmentSymbols.BOX_KEY) == ApparatusPresence.PRESENT) add("plyo_box")
        if (availability.presenceOf(EquipmentSymbols.JUMP_ROPE_KEY) == ApparatusPresence.PRESENT) add("jump_rope")
    }

    private val exactMachines: Boolean = availability.hasExplicitMachinePresence()

    /** ¿Se cumple un requisito? «a|b» = cualquiera de las llaves. */
    fun satisfied(requirement: String): Boolean = requirement.split('|').any { it in tokens }

    fun has(token: String): Boolean = token in tokens

    /**
     * ¿Se puede ejecutar la configuración con este material? Replica `SimpleCyclePersonalizer.equipmentAllows` sobre el
     * equipo efectivo compartido y añade los requisitos propios de la reserva ([requires]).
     */
    fun allows(entry: CatalogEntry, requires: List<String>): Boolean {
        val actual = entry.equipmentId
        val machineDeclared = entry.machineToken in tokens
        if (exactMachines && actual == "machine" && !machineDeclared) return false
        if (!machineDeclared && actual !in tokens) return false
        val shared = entry.sharedRequirements
        if (shared.isNotEmpty() && !shared.all { it in tokens }) return false
        return requires.all { satisfied(it) }
    }

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
    val hasJumpRope: Boolean get() = "jump_rope" in tokens
}
