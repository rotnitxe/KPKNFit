package com.example.kpkn.screens.learn

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.learn.LearnModule
import com.example.kpkn.data.learn.QuizQuestion
import com.example.kpkn.data.repository.LearnRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnQuizScreen(
    courseId: String,
    submoduleIndex: Int = -1, // -1 = quiz final
    onComplete: (score: Int, total: Int) -> Unit,
    onBack: () -> Unit,
) {
    val module = LearnRepository.getModule(courseId)
    if (module == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Curso no encontrado") }
        return
    }

    val questions: List<QuizQuestion> = if (submoduleIndex >= 0) {
        module.submodules.getOrNull(submoduleIndex)?.quiz ?: emptyList()
    } else {
        module.finalQuiz
    }

    if (questions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay preguntas") }
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableIntStateOf(-1) }
    var showExplanation by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val question = questions[currentIndex]

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (submoduleIndex >= 0) "Quiz Módulo ${submoduleIndex + 1}" else "Quiz Final"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Salir")
                    }
                },
            )
        }
    ) { padding ->
        if (finished) {
            // Resultados
            val percentage = (score * 100) / questions.size
            val passed = percentage >= 80

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (passed) "¡Excelente!" else "Sigue practicando",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = if (passed) Color(0xFF43A047) else Color(0xFFFF8F00),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "$score / ${questions.size}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "$percentage% de acierto",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { score.toFloat() / questions.size },
                    modifier = Modifier
                        .width(200.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (passed) Color(0xFF43A047) else Color(0xFFFF8F00),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(24.dp))

                if (submoduleIndex >= 0) {
                    // Submodule quiz - guardar y volver
                    Button(
                        onClick = {
                            LearnRepository.updateSubmoduleCompletion(courseId, submoduleIndex, score)
                            onComplete(score, questions.size)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Continuar")
                    }
                } else {
                    // Quiz final - guardar resultado
                    Button(
                        onClick = {
                            LearnRepository.updateFinalQuizScore(courseId, score)
                            onComplete(score, questions.size)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (passed) "¡Obtener insignia!" else "Guardar resultado")
                    }
                }
            }
        } else {
            // Pregunta actual
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Progreso
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Pregunta ${currentIndex + 1} de ${questions.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Aciertos: $score",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF43A047),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (currentIndex + 1).toFloat() / questions.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = module.category.color,
                        trackColor = module.category.color.copy(alpha = 0.15f),
                    )
                }

                // Pregunta
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        question.question,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                }

                // Opciones
                question.options.forEachIndexed { idx, option ->
                    item {
                        val isSelected = selectedAnswer == idx
                        val isCorrect = idx == question.correctIndex
                        val bgColor by animateColorAsState(
                            when {
                                !showExplanation -> if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                                isCorrect -> Color(0xFF43A047).copy(alpha = 0.12f)
                                isSelected && !isCorrect -> Color(0xFFE53935).copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.surface
                            },
                            label = "bg",
                        )
                        val borderColor = when {
                            !showExplanation -> if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            isCorrect -> Color(0xFF43A047)
                            isSelected && !isCorrect -> Color(0xFFE53935)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        }

                        Surface(
                            onClick = {
                                if (!showExplanation) {
                                    selectedAnswer = idx
                                    showExplanation = true
                                    if (isCorrect) score++
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = bgColor,
                            border = BorderStroke(1.5.dp, borderColor),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${'A' + idx}.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        showExplanation && isCorrect -> Color(0xFF43A047)
                                        showExplanation && isSelected && !isCorrect -> Color(0xFFE53935)
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (showExplanation && isCorrect) {
                                    Icon(Icons.Default.Check, null, tint = Color(0xFF43A047), modifier = Modifier.size(22.dp))
                                } else if (showExplanation && isSelected && !isCorrect) {
                                    Icon(Icons.Default.Close, null, tint = Color(0xFFE53935), modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }

                // Explicación
                if (showExplanation) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Explicación",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    question.explanation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                if (currentIndex < questions.size - 1) {
                                    currentIndex++
                                    selectedAnswer = -1
                                    showExplanation = false
                                } else {
                                    finished = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (currentIndex < questions.size - 1) "Siguiente pregunta" else "Ver resultados"
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
