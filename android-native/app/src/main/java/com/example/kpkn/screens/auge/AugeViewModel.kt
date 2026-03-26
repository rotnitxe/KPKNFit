package com.example.kpkn.screens.auge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import com.example.kpkn.domain.auge.AugeTtcEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * AugeViewModel — Central state for the AUGE battery/recovery system.
 * Shared across Home and Workout screens via AndroidViewModel.
 */
class AugeViewModel(application: Application) : AndroidViewModel(application) {

    private val augeRepo = AugeRepository.getInstance(application)
    private val programRepo = ProgramRepository.getInstance()

    // ─── Public state ─────────────────────────────────────────────────────────

    private val _batteries = MutableStateFlow(GlobalBatteries(muscular = 100, cnc = 100, spinal = 100))
    val batteries: StateFlow<GlobalBatteries> = _batteries.asStateFlow()

    private val _perMuscle = MutableStateFlow<Map<String, MuscleRecoveryStatus>>(emptyMap())
    val perMuscle: StateFlow<Map<String, MuscleRecoveryStatus>> = _perMuscle.asStateFlow()

    private val _readiness = MutableStateFlow<AugeReadinessVerdict?>(null)
    val readiness: StateFlow<AugeReadinessVerdict?> = _readiness.asStateFlow()

    private val _pendingQuestionnaire = MutableStateFlow<PendingQuestionnaire?>(null)
    val pendingQuestionnaire: StateFlow<PendingQuestionnaire?> = _pendingQuestionnaire.asStateFlow()

    private val _articular = MutableStateFlow<Map<ArticularBattery, ArticularBatteryState>>(emptyMap())
    val articular: StateFlow<Map<ArticularBattery, ArticularBatteryState>> = _articular.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        // Recompute whenever history or settings change
        viewModelScope.launch {
            programRepo.history.combine(programRepo.settings) { h, s -> Pair(h, s) }
                .collectLatest { (history, settings) ->
                    recompute(history, settings)
                }
        }
    }

    // ─── Core recompute ───────────────────────────────────────────────────────

    private suspend fun recompute(history: List<WorkoutLog>, settings: Settings) {
        val wellbeing = augeRepo.getTodayWellbeing()
        val feedbacks = augeRepo.getPostSessionFeedbacks()
        val sleepLogs = augeRepo.getLastNSleepLogs(7)
        val exerciseDb = emptyMap<String, ExerciseMuscleInfo>() // Phase 5: wire ExerciseDatabase

        val (batteries, perMuscle, readiness, pending, articular) = withContext(Dispatchers.Default) {
            val bat = AugeRecoveryEngine.calculateGlobalBatteries(history, wellbeing, settings, exerciseDb, sleepLogs)
            val muscles = AugeRecoveryEngine.getPerMuscleBatteries(history, wellbeing, settings, exerciseDb, sleepLogs)
            val (cns, _, _) = AugeRecoveryEngine.calculateSystemicFatigue(history, wellbeing, settings, exerciseDb, sleepLogs)
            val verdict = AugeRecoveryEngine.calculateDailyReadiness(cns, wellbeing)
            val pending = AugeRecoveryEngine.checkPendingSurveys(history, feedbacks)
            val articular = AugeTtcEngine.calculateArticularBatteries(history, exerciseDb)
            Quintuple(bat, muscles, verdict, pending, articular)
        }

        _batteries.value = batteries
        _perMuscle.value = perMuscle
        _readiness.value = readiness
        _pendingQuestionnaire.value = pending ?: augeRepo.getPendingQuestionnaire()
        _articular.value = articular
        _isLoading.value = false
    }

    // ─── Public actions ───────────────────────────────────────────────────────

    /** Call when user submits the daily wellbeing log (from ReadinessSheet). */
    fun saveWellbeing(log: DailyWellbeingLog) {
        viewModelScope.launch {
            augeRepo.saveWellbeingLog(log)
            recompute(programRepo.history.value, programRepo.settings.value)
        }
    }

    /** Call when user submits post-session feedback (from PostSessionSheet). */
    fun savePostSessionFeedback(fb: PostSessionFeedback) {
        viewModelScope.launch {
            augeRepo.savePostSessionFeedback(fb)
            augeRepo.clearPendingQuestionnaire()
            _pendingQuestionnaire.value = null
            recompute(programRepo.history.value, programRepo.settings.value)
        }
    }

    /** Schedule a post-session questionnaire to appear 2h after workout. */
    fun schedulePendingQuestionnaire(q: PendingQuestionnaire) {
        viewModelScope.launch {
            augeRepo.setPendingQuestionnaire(q)
            _pendingQuestionnaire.value = q
        }
    }

    /** Dismiss the pending questionnaire without saving. */
    fun dismissPendingQuestionnaire() {
        viewModelScope.launch {
            augeRepo.clearPendingQuestionnaire()
            _pendingQuestionnaire.value = null
        }
    }

    /** Force recompute (e.g. after a new workout log is added). */
    fun refresh() {
        viewModelScope.launch {
            recompute(programRepo.history.value, programRepo.settings.value)
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
