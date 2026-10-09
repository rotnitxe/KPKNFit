package com.example.kpkn.ui.tour

import com.example.kpkn.data.repository.ProgramRepository

/** Recorridos ya vistos o saltados. Producción: `Settings.tourSeen`. */
interface TourSeenStore {
    fun seenIds(): Set<String>

    fun markSeen(tourId: String)
}

/** Lee y escribe `Settings.tourSeen` a través del repositorio de programas (que persiste los ajustes). */
object SettingsTourSeenStore : TourSeenStore {
    override fun seenIds(): Set<String> = ProgramRepository.getInstance().settings.value.tourSeen

    override fun markSeen(tourId: String) {
        ProgramRepository.getInstance().updateSettings { current -> current.copy(tourSeen = current.tourSeen + tourId) }
    }
}
