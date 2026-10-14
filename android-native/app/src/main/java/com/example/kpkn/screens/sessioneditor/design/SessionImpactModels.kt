package com.example.kpkn.screens.sessioneditor.design

import com.example.kpkn.screens.programdetail.design.ProgramVolumeZones
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeSummary
import kotlin.math.roundToInt

/**
 * Impacto de la sesión (SE4): lo que gasta, en la forma que pintan la torre y sus cifras. Sale del resumen del motor AUGE; aquí solo se
 * reordenan sus datos, sin tocar sus cálculos.
 *
 * [energia], [musculos] y [columna] son el gasto estimado de cada canal, de 0 a 100 (los anillos de la torre).
 */
data class SessionImpactChannels(val energia: Int, val musculos: Int, val columna: Int)

/**
 * Volumen de un músculo en la sesión, en series. [zones] son los umbrales de la sesión del motor (mínimo efectivo, máximo adaptativo y
 * máximo recuperable). Sin ellos, la barra solo lleva la marca.
 */
data class SessionImpactMuscle(val name: String, val sets: Double, val zones: ProgramVolumeZones?)

/** El impacto de la sesión. Nulo cuando el motor no tiene datos: el módulo no se pinta y nada aparece como cero. */
data class SessionImpact(val channels: SessionImpactChannels, val muscles: List<SessionImpactMuscle>)

/**
 * Convierte el resumen del motor en el impacto de la pantalla. Sin canales (los tres a cero, como antes de calcular) devuelve nulo.
 * Los músculos salen de más a menos carga, y a igualdad, por nombre.
 */
fun sessionImpactOf(summary: SessionEditorAugeSummary): SessionImpact? {
    val drain = summary.sessionDrain
    if (drain.cns <= 0 && drain.muscular <= 0 && drain.spinal <= 0) return null
    val channels = SessionImpactChannels(
        energia = drain.cns.coerceIn(0, 100),
        musculos = drain.muscular.coerceIn(0, 100),
        columna = drain.spinal.coerceIn(0, 100),
    )
    val muscles = summary.sessionVolumeByMuscle
        .filterValues { it > 0.0 }
        .map { (name, sets) ->
            val threshold = summary.volumeThresholdsByMuscle[name]
            SessionImpactMuscle(
                name = name,
                sets = sets,
                zones = threshold?.let {
                    ProgramVolumeZones(
                        minEffective = it.sessionMev.roundToInt(),
                        maxAdaptive = it.sessionMav.roundToInt(),
                        maxRecoverable = it.sessionMrv.roundToInt(),
                    )
                },
            )
        }
        .sortedWith(compareByDescending<SessionImpactMuscle> { it.sets }.thenBy { it.name })
    return SessionImpact(channels = channels, muscles = muscles)
}
