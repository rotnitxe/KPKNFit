package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.CompetitionRecord

enum class CompetitionMedalHonor(val id: String, val place: Int, val label: String) {
    GOLD("gold", 1, "Oro"),
    SILVER("silver", 2, "Plata"),
    BRONZE("bronze", 3, "Bronce"),
}

/** «Algo más (opcional)» de c-place: admite varios. */
enum class CompetitionExtra(val id: String, val label: String) {
    NATIONAL_RECORD("national_record", "Récord nacional"),
    BEST_LIFTER("best_lifter", "Mejor levantador"),
    QUALIFIED("qualified", "Clasificado"),
}

/**
 * Puesto de c-place (Registros v4): opcional y exacto. Las medallas son los tres primeros puestos; fuera del podio se guarda el
 * número real (ya no «4+», que se mostraba «4.º» aunque quedaras décimo).
 */
object CompetitionPlaceHonors {
    fun medalForPlace(place: Int?): CompetitionMedalHonor? = CompetitionMedalHonor.entries.firstOrNull { it.place == place }

    fun medalById(id: String?): CompetitionMedalHonor? = CompetitionMedalHonor.entries.firstOrNull { it.id == id }

    /** El puesto exacto como se guarda («5»); null o < 1 = sin puesto. */
    fun placementString(place: Int?): String? = place?.takeIf { it >= 1 }?.toString()

    /** Lee el puesto guardado. Un «4+» antiguo no dice el puesto real: se lee como sin puesto. */
    fun parsePlace(placement: String?): Int? = placement?.trim()?.toIntOrNull()?.takeIf { it >= 1 }

    /** Aplica el puesto: 1–3 llevan su medalla; null quita puesto y medalla («Sin puesto»). */
    fun applyPlace(record: CompetitionRecord, place: Int?): CompetitionRecord {
        val exact = place?.takeIf { it >= 1 }
        return record.copy(placement = placementString(exact), medal = medalForPlace(exact)?.id, trophyId = null)
    }

    /** Alterna un extra de «Algo más». */
    fun toggleExtra(record: CompetitionRecord, extra: CompetitionExtra): CompetitionRecord =
        record.copy(extras = if (extra.id in record.extras) record.extras - extra.id else record.extras + extra.id)

    /** La medalla de [record]: la guardada o la de su puesto. */
    fun medalOf(record: CompetitionRecord): CompetitionMedalHonor? = medalById(record.medal) ?: medalForPlace(parsePlace(record.placement))
}
