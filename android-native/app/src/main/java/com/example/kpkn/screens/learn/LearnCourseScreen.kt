package com.example.kpkn.screens.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.learn.LearnModule
import com.example.kpkn.data.repository.LearnRepository
import com.example.kpkn.ui.components.KpknAlertDialog

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
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Curso no encontrado", color = Color.White)
        }
        return
    }

    // Mostrar disclaimer si existe y no se ha mostrado
    if (module.disclaimer != null && moduleProgress?.disclaimerShown != true) {
        showDisclaimer = true
    }

    if (showDisclaimer) {
        KpknAlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = {
                Text(
                    "Aviso importante",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    module.disclaimer ?: "",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        LearnRepository.markDisclaimerShown(courseId)
                        showDisclaimer = false
                    }
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
        )
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        module.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                            Text(
                                "Progreso del curso",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.6f)),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "$completed/$totalSubs módulos",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.6f))
                            )
                        }
                        Spacer(Modifier.height(6.dp))
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
                    "Contenido del programa",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        color = Color.White
                    ),
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
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF121212),
                    ),
                    border = BorderStroke(1.dp, Color(0xFF1E1E1E))
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
                                else -> Color(0xFF1A1A1A)
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                when {
                                    moduleProgress?.isCompleted == true -> Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFD700))
                                    allSubsCompleted -> Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFF43A047))
                                    else -> Icon(Icons.Default.Lock, null, tint = Color.White.copy(alpha = 0.3f))
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Evaluación Final",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = FontFamily.Serif,
                                    color = Color.White
                                ),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                when {
                                    moduleProgress?.isCompleted == true -> "Completado con ${moduleProgress.finalQuizScore} aciertos"
                                    allSubsCompleted -> "${module.finalQuiz.size} preguntas · Listo para rendir"
                                    else -> "Completa todos los módulos para desbloquear"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                        if (allSubsCompleted && !finalQuizDone) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White.copy(alpha = 0.5f))
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121212),
        ),
        border = BorderStroke(1.dp, Color(0xFF1E1E1E))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(module.icon, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                module.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                ),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                module.shortDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${module.estimatedMinutes}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    )
                    Text("minutos", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${module.submodules.size}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    )
                    Text("módulos", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${module.finalQuiz.size}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    )
                    Text("quiz final", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
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
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121212),
        ),
        border = BorderStroke(1.dp, Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Número/estado
            Surface(
                shape = CircleShape,
                color = when {
                    isCompleted -> Color(0xFF43A047).copy(alpha = 0.15f)
                    isUnlocked -> Color.White.copy(alpha = 0.1f)
                    else -> Color(0xFF1A1A1A)
                },
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        isCompleted -> Icon(Icons.Default.Check, null, tint = Color(0xFF43A047), modifier = Modifier.size(18.dp))
                        isUnlocked -> Text("$index", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White)
                        else -> Icon(Icons.Default.Lock, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.Serif,
                        color = if (isUnlocked) Color.White else Color.White.copy(alpha = 0.4f)
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (isCompleted && quizScore != null) "Completado · $quizScore/$questionsCount correctas"
                    else "$questionsCount preguntas en el quiz",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f),
                )
            }

            if (isUnlocked && !isCompleted) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
