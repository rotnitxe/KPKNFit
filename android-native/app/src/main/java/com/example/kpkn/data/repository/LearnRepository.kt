package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.learn.CourseProgress
import com.example.kpkn.data.learn.LEARN_MODULES
import com.example.kpkn.data.learn.LearnBadge
import com.example.kpkn.data.learn.LearnModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

object LearnRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _progress = MutableStateFlow<Map<String, CourseProgress>>(emptyMap())
    val progress: StateFlow<Map<String, CourseProgress>> = _progress.asStateFlow()

    private val _badges = MutableStateFlow<List<LearnBadge>>(emptyList())
    val badges: StateFlow<List<LearnBadge>> = _badges.asStateFlow()

    @Volatile
    private var initialized = false
    private lateinit var prefs: android.content.SharedPreferences

    fun initialize(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences("learn_prefs", Context.MODE_PRIVATE)
        initialized = true
        scope.launch {
            loadState()
        }
    }

    fun getModules(): List<LearnModule> = LEARN_MODULES

    fun getModule(id: String): LearnModule? = LEARN_MODULES.firstOrNull { it.id == id }

    fun getProgress(moduleId: String): CourseProgress =
        _progress.value[moduleId] ?: CourseProgress(moduleId = moduleId)

    fun updateSubmoduleCompletion(moduleId: String, submoduleIndex: Int, quizScore: Int) {
        _progress.update { map ->
            val current = map[moduleId] ?: CourseProgress(moduleId = moduleId)
            val updatedScores = current.submoduleQuizScores.toMutableMap()
            val module = getModule(moduleId)
            val submoduleId = module?.submodules?.getOrNull(submoduleIndex)?.id
            if (submoduleId != null) {
                updatedScores[submoduleId] = quizScore
            }
            val updated = current.copy(
                submoduleIndex = maxOf(current.submoduleIndex, submoduleIndex),
                submoduleQuizScores = updatedScores,
            )
            map + (moduleId to updated)
        }
        persistState()
    }

    fun updateFinalQuizScore(moduleId: String, score: Int) {
        _progress.update { map ->
            val current = map[moduleId] ?: CourseProgress(moduleId = moduleId)
            val module = getModule(moduleId)
            val totalQuestions = module?.finalQuiz?.size ?: 1
            val percentage = (score * 100) / totalQuestions

            val badge = if (percentage >= 80) {
                val tier = when {
                    percentage >= 95 -> com.example.kpkn.data.learn.BadgeTier.ORO
                    percentage >= 85 -> com.example.kpkn.data.learn.BadgeTier.PLATA
                    else -> com.example.kpkn.data.learn.BadgeTier.BRONCE
                }
                LearnBadge(
                    courseId = moduleId,
                    courseName = module?.title ?: "",
                    tier = tier,
                    earnedAt = Instant.now().toString(),
                    score = percentage,
                    isSpecial = module?.isSpecial == true,
                )
            } else null

            val updated = current.copy(
                finalQuizScore = score,
                badge = badge,
                completedAt = if (percentage >= 80) Instant.now().toString() else null,
            )

            if (badge != null) {
                _badges.update { list ->
                    val filtered = list.filterNot { it.courseId == moduleId }
                    (filtered + badge).sortedByDescending { it.earnedAt }
                }
            }

            map + (moduleId to updated)
        }
        persistState()
    }

    fun markDisclaimerShown(moduleId: String) {
        _progress.update { map ->
            val current = map[moduleId] ?: CourseProgress(moduleId = moduleId)
            map + (moduleId to current.copy(disclaimerShown = true))
        }
        persistState()
    }

    fun hasSpecialBadge(): Boolean = _badges.value.any { it.isSpecial }

    fun completedCoursesCount(): Int = _progress.value.count { it.value.isCompleted }

    private fun loadState() {
        val progressJson = prefs.getString("progress", null)
        if (progressJson != null) {
            runCatching {
                _progress.value = json.decodeFromString<Map<String, CourseProgress>>(progressJson)
            }
        }
        val badgesJson = prefs.getString("badges", null)
        if (badgesJson != null) {
            runCatching {
                _badges.value = json.decodeFromString<List<LearnBadge>>(badgesJson)
            }
        }
    }

    private fun persistState() {
        scope.launch {
            prefs.edit()
                .putString("progress", json.encodeToString(_progress.value))
                .putString("badges", json.encodeToString(_badges.value))
                .apply()
        }
    }
}
