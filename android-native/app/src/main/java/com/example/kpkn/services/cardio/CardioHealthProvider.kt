package com.example.kpkn.services.cardio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CardioHealthState(
    val exerciseId: String? = null,
    val sourceAvailable: Boolean = false,
    val heartRateBpm: Int? = null,
    val activeCalories: Double? = null,
)

interface CardioHealthProvider {
    val state: StateFlow<CardioHealthState>
    fun start(exerciseId: String)
    fun stop()
}

private class ManualCardioHealthProvider : CardioHealthProvider {
    private val _state = MutableStateFlow(CardioHealthState())
    override val state: StateFlow<CardioHealthState> = _state.asStateFlow()
    override fun start(exerciseId: String) { _state.value = CardioHealthState(exerciseId = exerciseId) }
    override fun stop() { _state.value = CardioHealthState() }
}

/** The launch version uses KPKN's local records; no external health provider is loaded. */
object CardioHealthProviderFactory {
    fun create(context: Context): CardioHealthProvider = ManualCardioHealthProvider()
}
