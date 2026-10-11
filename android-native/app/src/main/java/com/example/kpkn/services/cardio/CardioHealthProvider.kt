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

/** Reflection keeps the base flavor free of Health Connect classes and dependencies. */
object CardioHealthProviderFactory {
    fun create(context: Context): CardioHealthProvider = runCatching {
        Class.forName("com.example.kpkn.features.healthconnect.HealthConnectCardioProvider")
            .getConstructor(Context::class.java)
            .newInstance(context.applicationContext) as CardioHealthProvider
    }.getOrElse { ManualCardioHealthProvider() }
}
