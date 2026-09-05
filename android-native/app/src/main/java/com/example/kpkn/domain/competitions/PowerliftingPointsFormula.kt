package com.example.kpkn.domain.competitions

enum class PowerliftingPointsFormula(val id: String, val label: String) {
    IPF_GL("IPF_GL", "IPF GL"),
    DOTS("DOTS", "DOTS"),
    WILKS("WILKS", "Wilks"),
    ;

    companion object {
        fun fromId(raw: String?): PowerliftingPointsFormula? =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }
    }
}
