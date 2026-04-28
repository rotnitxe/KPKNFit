package com.example.kpkn.screens.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.kpkn.data.repository.LearnRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnCourseScreen(
    courseId: String,
    onStartModule: (Int) -> Unit,
    onStartFinalQuiz: () -> Unit,
    onBack: () -> Unit,
) {
    val module = remember(courseId) { LearnRepository.getModule(courseId) }
    val progress by LearnRepository.progress.collectAsState()
    val moduleProgress = progress[courseId]
    var showDisclaimer by remember { mutableStateOf(false) }

    if (module == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Curso no encontrado")
        }
        return
    }

    // Mostrar disclaimer si existe y no se ha mostrado
    if (module.disclaimer != null && moduleProgress?.disclaimerShown != true) {
        showDisclaimer = true
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text("Aviso") },
            text = { Text(module.disclaimer ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    LearnRepository.markDisclaimerShown(courseId)
                    showDisclaimer = false
                }) {
                    Text("Entendido")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(module.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header del curso
            item {
                CourseHeader(module = module, moduleProgress = moduleProgress)
            }

            // Progreso general
            if (moduleProgress != null && moduleProgress.inProgress) {
                item {
                    val totalSubs = module.submodules.size
                    val completed = moduleProgress.totalSubmodulesCompleted
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Progreso", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("$completed/$totalSubs módulos", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { completed.toFloat() / totalSubs.coerceAtLeast(1) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = module.category.color,
                            trackColor = module.category.color.copy(alpha = 0.15f),
                        )
                    }
                }
            }

            // Submódulos
            item {
                Text(
                    "Contenido",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            module.submodules.forEachIndexed { index, submodule ->
                val isCompleted = moduleProgress?.submoduleQuizScores?.containsKey(submodule.id) == true
                val isUnlocked = index == 0 || moduleProgress?.submoduleQuizScores?.containsKey(module.submodules[index - 1].id) == true
                val quizScore = moduleProgress?.submoduleQuizScores?.get(submodule.id)

                item {
                    SubmoduleCard(
                        index = index + 1,
                        title = submodule.title,
                        questionsCount = submodule.quiz.size,
                        isCompleted = isCompleted,
                        isUnlocked = isUnlocked,
                        quizScore = quizScore,
                        onClick = { if (isUnlocked) onStartModule(index) },
                    )
                }
            }

            // Quiz final
            item {
                Spacer(Modifier.height(4.dp))
                val allSubsCompleted = moduleProgress?.totalSubmodulesCompleted == module.submodules.size
                val finalQuizDone = moduleProgress?.finalQuizScore?.let { it >= 0 } == true

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = allSubsCompleted && !finalQuizDone) { onStartFinalQuiz() },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                moduleProgress?.isCompleted == true -> Color(0xFFFFD700).copy(alpha = 0.15f)
                                allSubsCompleted -> Color(0xFF43A047).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                when {
                                    moduleProgress?.isCompleted == true -> Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFD700))
                                    allSubsCompleted -> Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFF43A047))
                                    else -> Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Quiz Final",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                when {
                                    moduleProgress?.isCompleted == true -> "Completado con ${moduleProgress.finalQuizScore} aciertos"
                                    allSubsCompleted -> "${module.finalQuiz.size} preguntas · Listo para rendir"
                                    else -> "Completa todos los módulos primero"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (allSubsCompleted && !finalQuizDone) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CourseHeader(module: LearnModule, moduleProgress: com.example.kpkn.data.learn.CourseProgress?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(module.icon, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                module.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                module.shortDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${module.estimatedMinutes}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("minutos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${module.submodules.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("módulos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${module.finalQuiz.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("quiz final", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SubmoduleCard(
    index: Int,
    title: String,
    questionsCount: Int,
    isCompleted: Boolean,
    isUnlocked: Boolean,
    quizScore: Int?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isUnlocked, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Número/estado
            Surface(
                shape = CircleShape,
                color = when {
                    isCompleted -> Color(0xFF43A047)
                    isUnlocked -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        isCompleted -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        isUnlocked -> Text("$index", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White)
                        else -> Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (isCompleted && quizScore != null) "Quiz: $quizScore/$questionsCount correctas"
                    else "$questionsCount preguntas en el quiz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isUnlocked && !isCompleted) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}
