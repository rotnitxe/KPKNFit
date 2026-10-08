package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.domain.training.generator.DayEquipment

/**
 * Las respuestas del paso CARDIO_TYPE («¿Qué cardio quieres incluir?»). El valor estable de cada una es su nombre, que
 * coincide con el de su [CardioType]; [ANY] («Lo que haya») no es un tipo sino «sin preferencia»: el generador elige entre
 * los aparatos de cada día (`CardioBuilder.typeFor`).
 *
 * Qué respuestas se OFRECEN depende del material y de los lugares ([CardioChoices.optionsFor]); esta lista es el catálogo
 * completo (etiquetas del resumen, del catálogo de pasos y de la lectura de borradores).
 */
enum class CardioChoice(val label: String, val type: CardioType?, val hint: String? = null) {
    WALK("Caminar", CardioType.WALK),
    RUN_OUTDOOR("Correr al aire libre", CardioType.RUN_OUTDOOR),
    BIKE_OUTDOOR("Bicicleta al aire libre", CardioType.BIKE_OUTDOOR),
    TREADMILL("Cinta", CardioType.TREADMILL),
    BIKE_STATIONARY("Bicicleta estática", CardioType.BIKE_STATIONARY),
    ELLIPTICAL("Elíptica", CardioType.ELLIPTICAL),
    ROW_MACHINE("Remo en máquina", CardioType.ROW_MACHINE),
    ANY("Lo que haya", null, hint = "Elegimos según el material de cada día.");

    /** Es un aparato concreto de los que solo hay con las máquinas de cardio (el símbolo «Cardio» del material). */
    val isMachine: Boolean get() = type in MACHINE_TYPES

    /** Pide máquinas de cardio donde se hace: los cuatro aparatos y «Lo que haya» (que elige entre ellos). */
    val needsMachines: Boolean get() = isMachine || this == ANY

    companion object {
        private val MACHINE_TYPES = setOf(
            CardioType.TREADMILL, CardioType.BIKE_STATIONARY, CardioType.ELLIPTICAL, CardioType.ROW_MACHINE,
        )

        /** La respuesta de un tipo de cardio guardado; null si el paso no lo ofrece (otros tipos del modelo). */
        fun of(type: CardioType?): CardioChoice? = type?.let { value -> entries.firstOrNull { it.type == value } }

        /** La respuesta de un valor estable (`TREADMILL`, `ANY`…); null si no es ninguna. */
        fun fromValue(value: String?): CardioChoice? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Reglas del paso CARDIO_TYPE: qué respuestas se ofrecen con el material y los lugares elegidos.
 *
 * - **Caminar y correr al aire libre**, siempre.
 * - **Bicicleta al aire libre**, solo si la persona tiene bicicleta (la llave [SetupApparatusPanel.OUTDOOR_BIKE_KEY], que
 *   escribe la casilla «Tengo bicicleta» del propio paso): sin ella el generador la ignoraría.
 * - **Cinta, bicicleta estática, elíptica, remo en máquina y «Lo que haya»**, si el símbolo «Cardio» está entre el material
 *   de ALGÚN lugar elegido.
 *
 * No tiene lógica de material propia: lo que cada lugar permite lo dice [DayEquipment.cardioTypes], el MISMO aparato que
 * usa el generador al armar cada día (con el material por lugar de [PlaceMaterial]), así que lo que se ofrece es justo lo
 * que luego se puede prescribir. Con varios lugares el tipo declarado se usa en los días de los lugares que lo permiten
 * y el resto cae al criterio del generador ([placesWithoutMachines] alimenta la nota del paso).
 */
object CardioChoices {

    /** El equipo de cada lugar elegido; con un solo lugar (o ninguno) manda la disponibilidad declarada, sin lugar (`null`). */
    private fun equipmentOf(places: Set<TrainingPlace>, declared: EquipmentAvailability?): List<Pair<TrainingPlace?, DayEquipment>> {
        val availability = declared ?: EquipmentAvailability()
        val byPlace = PlaceMaterial.byPlace(availability, places)
        if (byPlace.isEmpty()) return listOf(null to DayEquipment(availability))
        return byPlace.map { (place, own) -> place to DayEquipment(own) }
    }

    /** Las respuestas que se ofrecen, en el orden del catálogo ([CardioChoice.entries]). */
    fun optionsFor(places: Set<TrainingPlace>, availability: EquipmentAvailability?): List<CardioChoice> {
        val equipment = equipmentOf(places, availability).map { it.second }
        val allowed = equipment.flatMapTo(HashSet<CardioType>()) { it.cardioTypes }
        val machines = equipment.any { it.hasCardioMachines }
        return CardioChoice.entries.filter { choice -> if (choice == CardioChoice.ANY) machines else choice.type in allowed }
    }

    /** ¿Se ofrece [choice] con este material y estos lugares? */
    fun isOffered(choice: CardioChoice, places: Set<TrainingPlace>, availability: EquipmentAvailability?): Boolean =
        choice in optionsFor(places, availability)

    /**
     * Los lugares elegidos donde NO hay máquinas de cardio, en el orden del contrato. Vacío con un solo lugar: ahí manda la
     * selección entera y, si no hay máquinas, la pregunta ni las ofrece.
     */
    fun placesWithoutMachines(places: Set<TrainingPlace>, availability: EquipmentAvailability?): List<TrainingPlace> {
        val equipment = equipmentOf(places, availability)
        if (equipment.size < 2) return emptyList()
        return equipment.filter { (_, day) -> !day.hasCardioMachines }.mapNotNull { (place, _) -> place }
    }

    /** Por qué una respuesta ya no está disponible con el material de ahora (COPY · Cardio); [choice] null = un tipo que el paso no ofrece. */
    fun unavailableReason(choice: CardioChoice?): String = when {
        choice == CardioChoice.BIKE_OUTDOOR -> "Marca «Tengo bicicleta» o elige otro cardio."
        choice?.needsMachines == true -> "Ese cardio necesita las máquinas de cardio de tu material: elige otro o añádelas."
        else -> "Elige uno de los cardios de la lista."
    }
}
