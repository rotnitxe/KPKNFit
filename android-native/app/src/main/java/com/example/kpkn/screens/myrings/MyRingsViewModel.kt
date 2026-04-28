package com.example.kpkn.screens.myrings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.auge.AugeRecoveryEngine
import com.example.kpkn.domain.auge.InterferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

enum class DrainFilter { GENERAL, MUSCULAR, CNS, COLUMNA }
enum class RankingsTab { GENERAL, ENERGIA, COLUMNA }
enum class InterferenceTab { PLANIFICADA, HISTORIAL }

class MyRingsViewModel(application: Application) : AndroidViewModel(application) {

    private val augeRepo = AugeRepository.getInstance(application)
    private val programRepo = ProgramRepository.getInstance()
    private val exerciseDb = EXERCISE_DATABASE_BY_ID

    // ─── UI filter state ──────────────────────────────────────────────────────

    private val _drainFilter = MutableStateFlow(DrainFilter.GENERAL)
    val drainFilter: StateFlow<DrainFilter> = _drainFilter.asStateFlow()

    private val _rankingsTab = MutableStateFlow(RankingsTab.GENERAL)
    val rankingsTab: StateFlow<RankingsTab> = _rankingsTab.asStateFlow()

    private val _interferenceTab = MutableStateFlow(InterferenceTab.PLANIFICADA)
    val interferenceTab: StateFlow<InterferenceTab> = _interferenceTab.asStateFlow()

    // ─── Data state ───────────────────────────────────────────────────────────

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sessionRankings = MutableStateFlow<List<SessionDrainRanking>>(emptyList())
    val sessionRankings: StateFlow<List<SessionDrainRanking>> = _sessionRankings.asStateFlow()

    private val _recentSessions = MutableStateFlow<List<SessionDrainRanking>>(emptyList())
    val recentSessions: StateFlow<List<SessionDrainRanking>> = _recentSessions.asStateFlow()

    private val _sessionsThisWeekCount = MutableStateFlow(0)
    val sessionsThisWeekCount: StateFlow<Int> = _sessionsThisWeekCount.asStateFlow()

    private val _exerciseDrainRankings = MutableStateFlow<List<ExerciseDrainRanking>>(emptyList())
    val exerciseDrainRankings: StateFlow<List<ExerciseDrainRanking>> = _exerciseDrainRankings.asStateFlow()

    private val _recoveryStats = MutableStateFlow<PersonalRecoveryStats?>(null)
    val recoveryStats: StateFlow<PersonalRecoveryStats?> = _recoveryStats.asStateFlow()

    private val _plannedInterferences = MutableStateFlow<List<SessionInterference>>(emptyList())
    val plannedInterferences: StateFlow<List<SessionInterference>> = _plannedInterferences.asStateFlow()

    private val _historicalInterferences = MutableStateFlow<List<SessionInterference>>(emptyList())
    val historicalInterferences: StateFlow<List<SessionInterference>> = _historicalInterferences.asStateFlow()

    private val _sleepLogs = MutableStateFlow<List<SleepLogExtended>>(emptyList())
    val sleepLogs: StateFlow<List<SleepLogExtended>> = _sleepLogs.asStateFlow()

    // ─── Derived: filtered exercise rankings ─────────────────────────────────

    val filteredExerciseRankings: StateFlow<List<ExerciseDrainRanking>> =
        combine(_exerciseDrainRankings, _drainFilter) { rankings, filter ->
            when (filter) {
                DrainFilter.GENERAL   -> rankings.sortedByDescending { it.overallDrain }
                DrainFilter.MUSCULAR  -> rankings.sortedByDescending { it.muscularDrain }
                DrainFilter.CNS       -> rankings.sortedByDescending { it.cnsDrain }
                DrainFilter.COLUMNA   -> rankings.sortedByDescending { it.spinalDrain }
            }.take(15)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ─── Derived: sorted session rankings by tab ──────────────────────────────

    val filteredSessionRankings: StateFlow<List<SessionDrainRanking>> =
        combine(_sessionRankings, _rankingsTab) { rankings, tab ->
            when (tab) {
                RankingsTab.GENERAL  -> rankings.sortedByDescending { it.totalDrain }
                RankingsTab.ENERGIA  -> rankings.sortedByDescending { it.cnsDrain }
                RankingsTab.COLUMNA  -> rankings.sortedByDescending { it.spinalDrain }
            }.take(10)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            programRepo.history.combine(programRepo.settings) { h, s -> Pair(h, s) }
                .distinctUntilChanged()
                .collectLatest { (history, settings) ->
                    loadData(history, settings)
                }
        }
        viewModelScope.launch {
            _sleepLogs.value = augeRepo.getAllSleepLogsExtended()
        }
    }

    private suspend fun loadData(history: List<WorkoutLog>, settings: Settings) {
        _isLoading.value = true
        withContext(Dispatchers.Default) {
            val activeProgramId = programRepo.activeProgramState.value?.programId
            val program = if (activeProgramId != null)
                programRepo.programs.value.firstOrNull { p -> p.id == activeProgramId }
            else null

            val rankings = AugeRecoveryEngine.calculateSessionDrainRankings(history, settings, exerciseDb)
            val exerciseRankings = AugeRecoveryEngine.calculateExerciseDrainRankings(history, settings, exerciseDb)
            val stats = AugeRecoveryEngine.calculatePersonalRecoveryStats(history, settings, exerciseDb)
            val recent = rankings
                .sortedByDescending { parseRankingDate(it.date) ?: LocalDate.MIN }
                .take(7)
            val weekStart = LocalDate.now().minusDays(6)
            val weekCount = rankings.count { ranking ->
                parseRankingDate(ranking.date)?.let { !it.isBefore(weekStart) } == true
            }

            val planned = InterferenceEngine.calculatePlannedInterferences(program, exerciseDb)
            val historical = InterferenceEngine.calculateHistoricalInterferences(history, exerciseDb, settings)

            withContext(Dispatchers.Main) {
                _sessionRankings.value = rankings
                _recentSessions.value = recent
                _sessionsThisWeekCount.value = weekCount
                _exerciseDrainRankings.value = exerciseRankings
                _recoveryStats.value = stats
                _plannedInterferences.value = planned
                _historicalInterferences.value = historical
                _isLoading.value = false
            }
        }
    }

    // ─── Public actions ───────────────────────────────────────────────────────

    fun setDrainFilter(filter: DrainFilter) { _drainFilter.value = filter }
    fun setRankingsTab(tab: RankingsTab)    { _rankingsTab.value = tab }
    fun setInterferenceTab(tab: InterferenceTab) { _interferenceTab.value = tab }

    fun saveSleepLog(log: SleepLogExtended) {
        viewModelScope.launch {
            augeRepo.saveSleepLogExtended(log)
            _sleepLogs.value = augeRepo.getAllSleepLogsExtended()
        }
    }

    fun deleteSleepLog(id: String) {
        viewModelScope.launch {
            augeRepo.deleteSleepLogExtended(id)
            _sleepLogs.value = augeRepo.getAllSleepLogsExtended()
        }
    }

    fun buildNewSleepLog(
        bedTime: String,
        wakeTime: String,
        quality: Int,
        awakenings: Int,
        notes: String?,
    ): SleepLogExtended {
        // Calculate duration from bedTime and wakeTime (simple HH:mm parsing)
        val duration = calculateSleepDuration(bedTime, wakeTime)
        val today = java.time.LocalDate.now().toString()
        return SleepLogExtended(
            id = UUID.randomUUID().toString(),
            date = today,
            bedTime = bedTime,
            wakeTime = wakeTime,
            duration = duration,
            quality = quality.coerceIn(1, 5),
            awakenings = awakenings.coerceAtLeast(0),
            notes = notes?.takeIf { it.isNotBlank() },
        )
    }

    private fun calculateSleepDuration(bedTime: String, wakeTime: String): Double {
        return try {
            val bedParts = bedTime.split(":").map { it.trim().toInt() }
            val wakeParts = wakeTime.split(":").map { it.trim().toInt() }
            val bedMinutes = bedParts[0] * 60 + bedParts[1]
            val wakeMinutes = wakeParts[0] * 60 + wakeParts[1]
            val diff = if (wakeMinutes >= bedMinutes) wakeMinutes - bedMinutes
                       else 1440 - bedMinutes + wakeMinutes  // crossed midnight
            diff / 60.0
        } catch (e: Exception) {
            7.5  // fallback
        }
    }

    private fun parseRankingDate(raw: String): LocalDate? = runCatching {
        LocalDate.parse(raw.take(10))
    }.getOrNull()
}
