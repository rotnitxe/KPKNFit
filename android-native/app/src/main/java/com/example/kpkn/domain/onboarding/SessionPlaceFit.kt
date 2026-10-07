package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.training.generator.DayEquipment
import com.example.kpkn.domain.training.generator.GeneratorCatalog

/**
 * Entreno v2 · ¿se pueden hacer los ejercicios de una sesión con el material de un lugar?
 *
 * Es la ÚNICA pregunta que el asistente se hace cuando una sesión cae en un día de otro lugar (la semana armada) y no tiene
 * lógica de material propia: el material de cada lugar sale de [PlaceMaterial] (la misma traducción de símbolos que usa el
 * generador al armar cada sesión), el equipo efectivo de [DayEquipment] y cada configuración se decide con el filtro único
 * `ConfigurationEquipmentFilter` (implemento, máquina concreta y soportes). Nunca otra comprobación.
 *
 * - Un ejercicio del catálogo (`Exercise.catalogConfigurationId`) cabe cuando el filtro lo admite con el material del lugar.
 * - El cardio cabe cuando su aparato lo permite ese lugar ([DayEquipment.cardioTypes]: caminar y correr siempre).
 * - Lo que no tiene identidad de catálogo (movilidad, ejercicios propios) no pide material y no se contrasta.
 */
internal class SessionPlaceFit internal constructor(
    private val catalog: GeneratorCatalog,
    private val availabilityOf: (TrainingPlace) -> EquipmentAvailability,
) {
    private val equipmentByPlace = HashMap<TrainingPlace, DayEquipment>()

    private fun equipmentOf(place: TrainingPlace): DayEquipment =
        equipmentByPlace.getOrPut(place) { DayEquipment(availabilityOf(place)) }

    /** ¿Caben TODOS los ejercicios de [session] con el material de [place]? */
    fun fits(session: Session, place: TrainingPlace): Boolean {
        val equipment = equipmentOf(place)
        return session.allExercises().all { exercise -> exerciseFits(exercise, equipment) }
    }

    private fun exerciseFits(exercise: Exercise, equipment: DayEquipment): Boolean {
        exercise.cardioDetails?.let { cardio -> return cardio.type in equipment.cardioTypes }
        val configurationId = exercise.catalogConfigurationId ?: return true
        val entry = catalog.entry(configurationId) ?: return true
        return equipment.allows(entry, requires = emptyList())
    }

    companion object {
        /**
         * El contraste de las sesiones con los lugares de un borrador: [availability] es la selección ÚNICA de material
         * (la unión de todos los lugares) y [places] los lugares elegidos. Con un solo lugar manda la disponibilidad
         * declarada; con varios, cada lugar tiene el suyo (el mismo reparto que usa el generador).
         */
        fun of(catalog: ExerciseCatalogV2, availability: EquipmentAvailability?, places: Set<TrainingPlace>): SessionPlaceFit {
            val declared = availability ?: EquipmentAvailability()
            val byPlace = PlaceMaterial.byPlace(EquipmentSymbols.selectedFrom(declared), places)
            return SessionPlaceFit(GeneratorCatalog.of(catalog)) { place ->
                byPlace[place]
                    ?: if (places.size <= 1) declared else EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(setOf(place)), setOf(place))
            }
        }
    }
}
