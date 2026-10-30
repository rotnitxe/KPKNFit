package com.example.kpkn.domain.cardio

/**
 * Pulido v1 · CARDIO DEL EDITOR (punto 6): las cinco zonas del cardio en vivo como las usa el editor. Kotlin puro, sin colores (los de
 * `CardioZoneColors`) ni nombres (los de `CardioZoneNames`).
 *
 * El cardio guarda la zona de pulso como `targetHrPercent` (un porcentaje de la frecuencia máxima): la zona elegida escribe el punto
 * medio de su banda y [zoneOfPercent] la lee de vuelta, así el dato no cambia y las zonas antiguas por % siguen leyéndose bien.
 */
object CardioZones {
    /** Rango de % de la FC máxima de cada zona, para el selector (`< 60 %` … `> 90 %`). */
    val percentRangeLabels: List<String> = listOf("< 60 %", "60–70 %", "70–80 %", "80–90 %", "> 90 %")

    /** El punto medio de la banda de la zona [zone] (1..5): 55, 65, 75, 85 y 95 %. */
    fun midpointPercent(zone: Int): Int = when (zone.coerceIn(1, 5)) {
        1 -> 55
        2 -> 65
        3 -> 75
        4 -> 85
        else -> 95
    }

    /** La zona (1..5) de un % de FC: cortes en 60, 70, 80 y 90 %. */
    fun zoneOfPercent(hrPercent: Int): Int = when {
        hrPercent < 60 -> 1
        hrPercent < 70 -> 2
        hrPercent < 80 -> 3
        hrPercent < 90 -> 4
        else -> 5
    }

    /** La zona (1..5) de un RPE (1..10): `(RPE + 1) / 2`, como el cardio en vivo. */
    fun zoneOfRpe(rpe: Int): Int = ((rpe + 1) / 2).coerceIn(1, 5)

    /** El rango de RPE de la zona, como lo dice la ficha: «1–2», «3–4», «5–6», «7–8», «9–10». */
    fun rpeRangeLabel(zone: Int): String {
        val z = zone.coerceIn(1, 5)
        return if (z == 5) "9–10" else "${z * 2 - 1}–${z * 2}"
    }
}
