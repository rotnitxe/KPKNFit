package com.example.kpkn.data.backup

import android.content.Context
import com.example.kpkn.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Legacy repository readiness for the independently implemented debug data seeder. */
object AppBootstrap {
    sealed interface State { data object Preparing : State; data object Ready : State; data object Recovery : State }
    private val _state = MutableStateFlow<State>(State.Preparing)
    val state = _state.asStateFlow()
    private val mutex = Mutex()

    suspend fun prepare(context: Context) = withContext(Dispatchers.IO) { mutex.withLock {
        _state.value = State.Preparing
        try {
            val programs = ProgramRepository.init(context)
            CompetitionRepository.init(context)
            AugeRepository.getInstance(context)
            WorkoutMediaRepository.init(context)
            CustomExerciseRepository.initialize(context)
            SessionTemplateRepository.getInstance(context).refreshFromStorage()
            NutritionRepository.init(context)
            withTimeout(60_000) { programs.isReady.first { it } }
            _state.value = State.Ready
        } catch (error: Throwable) {
            _state.value = State.Recovery
            throw error
        }
    } }
}
