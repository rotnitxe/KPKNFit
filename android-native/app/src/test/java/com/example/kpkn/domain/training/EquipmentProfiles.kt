package com.example.kpkn.domain.training

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.TrainingPlace

/** Un material declarado como lo declara el wizard: símbolos elegidos en unos lugares. */
internal data class EquipmentProfile(
    val name: String,
    val places: Set<TrainingPlace>,
    val symbols: Set<EquipmentSymbolId>,
) {
    val availability: EquipmentAvailability get() = EquipmentSymbols.availabilityOf(symbols, places)
}

/**
 * Materiales de las pruebas del resolutor único: los seis del brief del paquete E (el diferencial y la alcanzabilidad los
 * recorren todos), más combinaciones que ejercitan los extras de gimnasio, la barra baja del parque y el modo categórico.
 */
internal object EquipmentProfiles {

    private val gym = setOf(TrainingPlace.GYM)
    private val home = setOf(TrainingPlace.HOME)
    private val park = setOf(TrainingPlace.PUBLIC)

    private fun profile(name: String, places: Set<TrainingPlace>, vararg symbols: EquipmentSymbolId) =
        EquipmentProfile(name, places, symbols.toSet())

    val bodyOnly = profile("solo cuerpo", home, EquipmentSymbolId.BODYWEIGHT_ONLY)

    /** La semilla de un parque: barra de dominadas y paralelas. */
    val parkSeed = EquipmentProfile("parque", park, EquipmentSymbols.seedFor(park))

    val homeDumbbellsBench = profile("casa con mancuernas y banco", home, EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BENCH)

    val homeRingsBox = profile("casa con anillas y cajón", home, EquipmentSymbolId.RINGS, EquipmentSymbolId.BOX)

    val gymFull = EquipmentProfile("gimnasio completo", gym, EquipmentSymbols.seedFor(gym))

    val gymWithoutRack = EquipmentProfile("gimnasio sin rack", gym, EquipmentSymbols.seedFor(gym) - EquipmentSymbolId.RACK)

    /** Gimnasio sin la sala de máquinas pero con poleas: sigue siendo una declaración por llave. */
    val gymWithoutMachines = EquipmentProfile("gimnasio sin máquinas", gym, EquipmentSymbols.seedFor(gym) - EquipmentSymbolId.MACHINES)

    /** Gimnasio sin banco: no hay banco declinado ni de hiperextensión. */
    val gymWithoutBench = EquipmentProfile("gimnasio sin banco", gym, EquipmentSymbols.seedFor(gym) - EquipmentSymbolId.BENCH)

    /** Los seis materiales que pide el brief. */
    val required: List<EquipmentProfile> = listOf(bodyOnly, parkSeed, homeDumbbellsBench, homeRingsBox, gymFull, gymWithoutRack)

    /** Combinaciones que ejercitan lo que cada símbolo y cada lugar añaden. */
    val extra: List<EquipmentProfile> = listOf(
        profile("casa con barra, rack y banco", home, EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH),
        profile("casa con cuerda de saltar", home, EquipmentSymbolId.JUMP_ROPE),
        profile(
            "parque con banco y anillas", park,
            EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS, EquipmentSymbolId.BENCH, EquipmentSymbolId.RINGS,
        ),
        profile("parque sin barra de dominadas", park, EquipmentSymbolId.PARALLEL_BARS, EquipmentSymbolId.BENCH),
        profile("parque solo con barra de dominadas", park, EquipmentSymbolId.PULL_UP_BAR),
        EquipmentProfile("gimnasio con anillas", gym, EquipmentSymbols.seedFor(gym) + EquipmentSymbolId.RINGS),
        EquipmentProfile(
            "gimnasio sin máquinas ni poleas", gym,
            EquipmentSymbols.seedFor(gym) - EquipmentSymbolId.MACHINES - EquipmentSymbolId.CABLE,
        ),
        EquipmentProfile("gimnasio sin barra", gym, EquipmentSymbols.seedFor(gym) - EquipmentSymbolId.BARBELL),
        gymWithoutMachines,
        gymWithoutBench,
        profile("casa con barra y banco", home, EquipmentSymbolId.BARBELL, EquipmentSymbolId.BENCH),
        profile("casa con máquinas", home, EquipmentSymbolId.MACHINES),
        profile("gimnasio solo con máquinas", gym, EquipmentSymbolId.MACHINES),
        profile("gimnasio solo con poleas", gym, EquipmentSymbolId.CABLE),
        EquipmentProfile("gimnasio y parque", gym + park, EquipmentSymbols.seedFor(gym + park)),
        EquipmentProfile(
            "casa y parque con barra de dominadas", home + park,
            setOf(EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BARBELL),
        ),
    )

    /** Cada símbolo elegido solo, en cada lugar donde se ofrece. */
    val everySymbolAlone: List<EquipmentProfile> = TrainingPlace.entries.flatMap { place ->
        EquipmentSymbols.symbolsFor(setOf(place))
            .filter { it != EquipmentSymbolId.BODYWEIGHT_ONLY }
            .map { symbol -> EquipmentProfile("${symbol.name} solo, ${place.name}", setOf(place), setOf(symbol)) }
    }

    val all: List<EquipmentProfile> = required + extra + everySymbolAlone

    /**
     * Una disponibilidad SOLO categórica (sin llaves): la que dejan los borradores anteriores a los símbolos. No activa el
     * modo «configuración exacta» y acredita las categorías, no las llaves.
     */
    val categoricalOnly: EquipmentAvailability = EquipmentAvailability(
        categories = setOf(EquipmentCategory.BARBELL, EquipmentCategory.MACHINES, EquipmentCategory.SUPPORT),
    )

    /**
     * El gimnasio completo tal como lo dejaba el subpanel antiguo: las nueve llaves de máquina `PRESENT` SIN la bandera de
     * la sala de máquinas, o sea, con el modo «configuración exacta» (solo las máquinas curadas por su token).
     */
    val gymWithPanelAnswers: EquipmentAvailability = gymFull.availability.copy(machinesAsCategory = false)

    /** Disponibilidades que no salen de los símbolos de hoy (borradores y datos guardados anteriores). */
    val legacyAvailabilities: List<Pair<String, EquipmentAvailability>> = listOf(
        "solo categorías" to categoricalOnly,
        "gimnasio con el subpanel antiguo" to gymWithPanelAnswers,
    )

    /** Todo lo que recorren las pruebas diferenciales: los materiales de los símbolos y los de las disponibilidades antiguas. */
    val everyAvailability: List<Pair<String, EquipmentAvailability>> = all.map { it.name to it.availability } + legacyAvailabilities
}
