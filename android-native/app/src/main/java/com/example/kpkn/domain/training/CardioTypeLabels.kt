package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CardioType

/** Rótulos en español de cada tipo de cardio: el nombre que ve la persona (tarjetas de sesión y ejercicio materializado). */
object CardioTypeLabels {
    fun label(type: CardioType): String = when (type) {
        CardioType.TREADMILL -> "Cinta"
        CardioType.ELLIPTICAL -> "Elíptica"
        CardioType.ROW_MACHINE -> "Remo"
        CardioType.BIKE_STATIONARY -> "Bicicleta estática"
        CardioType.RUN_OUTDOOR -> "Carrera exterior"
        CardioType.BIKE_OUTDOOR -> "Ciclismo exterior"
        CardioType.WALK -> "Caminata"
        CardioType.STAIR_CLIMBER -> "Escaladora"
        CardioType.AIR_BIKE -> "Air bike"
        CardioType.SKI_ERG -> "SkiErg"
        CardioType.CURVED_TREADMILL -> "Cinta curva"
        CardioType.SLED -> "Trineo"
    }
}
