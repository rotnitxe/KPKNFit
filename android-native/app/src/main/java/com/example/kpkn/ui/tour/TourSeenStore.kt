package com.example.kpkn.ui.tour

import com.example.kpkn.data.repository.ProgramRepository

/** Recorridos ya vistos o saltados. Producción: `Settings.tourSeen`. */
interface TourSeenStore {
    fun isReady(): Boolean = true

    fun seenIds(): Set<String>

    fun markSeen(tourId: String)
}

/** Lee y escribe `Settings.tourSeen` a través del repositorio de programas (que persiste los ajustes). */
object SettingsTourSeenStore : TourSeenStore {
    override fun isReady(): Boolean = ProgramRepository.getInstance().isReady.value

    override fun seenIds(): Set<String> = ProgramRepository.getInstance().settings.value.tourSeen

    override fun markSeen(tourId: String) {
        ProgramRepository.getInstance().updateSettings { current -> current.copy(tourSeen = current.tourSeen + tourId) }
    }
}
