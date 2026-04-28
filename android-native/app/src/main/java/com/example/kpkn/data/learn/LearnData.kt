package com.example.kpkn.data.learn

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

// ─── Categorías de Learn ─────────────────────────────────────────────────────

enum class LearnCategory(
    val label: String,
    val color: Color,
    val icon: String,
) {
    BEGINNER("Novato", Color(0xFF00BCD4), "rocket"),
    TRAINING("Entrenamiento", Color(0xFF1E88E5), "dumbbell"),
    NUTRITION("Nutrición", Color(0xFF43A047), "apple"),
    RINGS("RINGS", Color(0xFF448AFF), "ring"),
    MENTAL_HEALTH("Salud Mental", Color(0xFF7E57C2), "mind"),
    TOOLS("Herramientas", Color(0xFFFF8F00), "wrench"),
}

// ─── Tipos de contenido ──────────────────────────────────────────────────────

enum class ContentType {
    PARAGRAPH,
    HEADING,
    BULLET,
    TIP,
    WARNING,
    CALLOUT,
}

data class ContentBlock(
    val type: ContentType,
    val text: String = "",
    val items: List<String> = emptyList(),
    val accentColor: Long? = null, // ARGB para CALLOUT
)

// ─── Quiz ────────────────────────────────────────────────────────────────────

data class QuizQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
)

// ─── Curso ───────────────────────────────────────────────────────────────────

data class LearnModule(
    val id: String,
    val title: String,
    val category: LearnCategory,
    val shortDescription: String,
    val icon: String,
    val estimatedMinutes: Int,
    val disclaimer: String? = null,
    val submodules: List<LearnSubmodule>,
    val finalQuiz: List<QuizQuestion>,
    val isSpecial: Boolean = false, // true solo para "Tu primera rutina en KPKN"
)

data class LearnSubmodule(
    val id: String,
    val title: String,
    val content: List<ContentBlock>,
    val quiz: List<QuizQuestion>,
)

// ─── Insignias ───────────────────────────────────────────────────────────────

@Serializable
enum class BadgeTier { BRONCE, PLATA, ORO }

@Serializable
data class LearnBadge(
    val courseId: String,
    val courseName: String,
    val tier: BadgeTier?,
    val earnedAt: String,
    val score: Int,
    val isSpecial: Boolean = false, // "Graduado KPKN"
)

// ─── Progreso ────────────────────────────────────────────────────────────────

@Serializable
data class CourseProgress(
    val moduleId: String,
    val submoduleIndex: Int = -1,               // último submódulo completado
    val submoduleQuizScores: Map<String, Int> = emptyMap(), // submoduleId -> score/10
    val finalQuizScore: Int = -1,               // -1 = no rendido
    val badge: LearnBadge? = null,
    val completedAt: String? = null,
    val disclaimerShown: Boolean = false,
) {
    val isCompleted: Boolean get() = finalQuizScore >= 8
    val inProgress: Boolean get() = submoduleIndex >= 0 || submoduleQuizScores.isNotEmpty()
    val totalSubmodulesCompleted: Int get() = submoduleQuizScores.size
}
