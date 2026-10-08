package com.example.kpkn.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SetupEntryViewModel(private val repository: ProgramRepository) : ViewModel() {
    data class State(val busy: Boolean = false, val failed: Boolean = false)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    fun startFree() {
        if (_state.value.busy) return
        _state.value = State(busy = true)
        viewModelScope.launch {
            try {
                repository.finishFreeIntro()
                _state.value = State()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.value = State(failed = true) }
        }
    }
}
