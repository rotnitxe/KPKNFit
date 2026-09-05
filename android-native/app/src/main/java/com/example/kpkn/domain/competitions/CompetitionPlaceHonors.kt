package com.example.kpkn.domain.competitions

enum class CompetitionMedalHonor(val id: String, val place: Int, val label: String) {
    GOLD("gold", 1, "Oro"),
    SILVER("silver", 2, "Plata"),
    BRONZE("bronze", 3, "Bronce"),
}

enum class CompetitionTrophyHonor(val id: String, val label: String) {
    FIRST_MEET("first_meet", "Primera competición"),
    CONSISTENT("consistent", "Levantador constante"),
    NINE_FOR_NINE("nine_for_nine", "9/9"),
    BEAT_TOTAL("beat_total", "Superó su total"),
    BEST_SQUAT("best_squat", "Mejor sentadilla del día"),
    BEST_BENCH("best_bench", "Mejor banca del día"),
    BEST_DEADLIFT("best_deadlift", "Mejor peso muerto del día"),
}

data class CompetitionPlaceHonor(
    val place: Int,
    val medal: CompetitionMedalHonor?,
    val trophy: CompetitionTrophyHonor?,
)

object CompetitionPlaceHonors {
    val trophies: List<CompetitionTrophyHonor> = CompetitionTrophyHonor.entries

    fun medalForPlace(place: Int): CompetitionMedalHonor? =
        CompetitionMedalHonor.entries.firstOrNull { it.place == place }

    fun trophyById(id: String?): CompetitionTrophyHonor? =
        trophies.firstOrNull { it.id == id }

    fun fromPlacement(place: Int?, trophyId: String?): CompetitionPlaceHonor? {
        if (place == null || place < 1) return null
        val medal = medalForPlace(place)
        val trophy = if (medal == null) trophyById(trophyId) else null
        if (place >= 4 && trophy == null) return null
        return CompetitionPlaceHonor(place = place, medal = medal, trophy = trophy)
    }

    fun isValid(place: Int?, trophyId: String?): Boolean = fromPlacement(place, trophyId) != null

    fun placementString(place: Int): String = if (place <= 3) place.toString() else "4+"

    fun parsePlace(placement: String?): Int? {
        val raw = placement?.trim().orEmpty()
        if (raw.isEmpty()) return null
        raw.toIntOrNull()?.let { return it.takeIf { n -> n >= 1 } }
        if (raw.startsWith("4")) return 4
        return null
    }
}
