package com.example.kpkn.screens.training

import com.example.kpkn.domain.multisport.SportId

fun recordedSportLabel(sport: SportId): String = when (sport) {
    SportId.STRENGTH -> "Fuerza"; SportId.HYPERTROPHY -> "Hipertrofia"
    SportId.POWERLIFTING -> "Powerlifting"; SportId.POWERBUILDING -> "Powerbuilding"
    SportId.WEIGHTLIFTING -> "Halterofilia"; SportId.CALISTHENICS -> "Calistenia"
    SportId.STRONGMAN -> "Strongman"; SportId.ARMWRESTLING -> "Lucha de brazos"
    SportId.RUNNING -> "Carrera"; SportId.CYCLING -> "Ciclismo"; SportId.TRAIL -> "Trail"
    SportId.ROWING -> "Remo"; SportId.SWIMMING -> "Natación"; SportId.TRIATHLON -> "Triatlón"
    SportId.CROSSFIT -> "CrossFit"; SportId.HYROX -> "HYROX"; SportId.BOXING -> "Boxeo"
    SportId.KICKBOXING -> "Kickboxing"; SportId.MUAY_THAI -> "Muay thai"; SportId.BJJ -> "Jiu-jitsu brasileño"
    SportId.WRESTLING -> "Lucha"; SportId.JUDO -> "Judo"; SportId.MMA -> "Artes marciales mixtas"
    SportId.CLIMBING -> "Escalada"; SportId.SPRINT -> "Sprint"; SportId.JUMP -> "Salto"
    SportId.TENNIS -> "Tenis"; SportId.PADEL -> "Pádel"; SportId.TEAM_SPORT -> "Deporte de equipo"
}
