package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.R
import com.example.kpkn.data.onboarding.persistenceFactory
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private data class WelcomeSlide(
    val title: String,
    val body: String,
    val accent: Color,
    val imageRes: Int,
)

@Composable
fun SetupEntryScreen(
    onStart: (resume: Boolean) -> Unit,
    onCompleted: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by ProgramRepository.getInstance().settings.collectAsStateWithLifecycle()
    var page by remember { mutableIntStateOf(0) }
    var hasDraft by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val slides = remember {
        listOf(
            WelcomeSlide("Tu entrenamiento, con contexto.", "Registra sesiones reales y entiende qué está pasando mientras entrenas.", Color(0xFFE1B85A), R.drawable.onboarding_p1_welcome),
            WelcomeSlide("Una semana que encaja contigo.", "Construye una semana que respete tus días, tu equipo y tu tiempo.", Color(0xFF5BC4BE), R.drawable.onboarding_p2_training),
            WelcomeSlide("Entiende cómo llegas a entrenar.", "Músculos, energía y columna se leen por separado para empezar con contexto.", Color(0xFF8E9CF4), R.drawable.onboarding_p4_recovery),
            WelcomeSlide("Registra lo que tú comes.", "La alimentación es opcional y solo se activa cuando tú la eliges.", Color(0xFFE58C74), R.drawable.onboarding_p3_food),
        )
    }

    LaunchedEffect(settings.onboardingCompleted) {
        if (settings.onboardingCompleted) {
            onCompleted()
            return@LaunchedEffect
        }
        loading = true
        hasDraft = runCatching {
            withContext(Dispatchers.IO) {
                ProgramRepository.getInstance().isReady.first { it }
                val drafts = persistenceFactory(context).drafts
                listOf("full", "resume", "training_only", "nutrition_only", "rings_only")
                    .any { drafts.load("setup-wizard:$it") != null }
            }
        }.getOrDefault(false)
        loading = false
    }

    if (settings.onboardingCompleted) return
    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.kpknicon), contentDescription = "KPKN", tint = Color.Unspecified, modifier = Modifier.size(40.dp))
                Column {
                    Text("KPKN", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text("Tu punto de partida", color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                val slide = slides[page]
                Box(
                    Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(28.dp)).background(slide.accent.copy(alpha = .16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(slide.imageRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(.92f),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = .10f), Color.Black.copy(alpha = .74f)),
                            ),
                        ),
                    )
                    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(color = slide.accent.copy(alpha = .22f), shape = CircleShape) {
                            Text("${page + 1}", color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
                        }
                        Text(slide.title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        Text(slide.body, color = Color.White.copy(alpha = .74f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Icon(Icons.Default.ArrowBack, "Anterior", tint = Color.White) }
                    repeat(slides.size) { index ->
                        Box(Modifier.padding(horizontal = 4.dp).size(if (index == page) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (index == page) slide.accent else Color.White.copy(alpha = .24f)))
                    }
                    IconButton(onClick = { page = (page + 1).coerceAtMost(slides.lastIndex) }, enabled = page < slides.lastIndex) { Icon(Icons.Default.ArrowForward, "Siguiente", tint = Color.White) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onStart(hasDraft) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF4D35E), contentColor = Color(0xFF171717)),
                ) { Text(if (hasDraft) "Continuar configuración" else "Comenzar", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { page = (page + 1).coerceAtMost(slides.lastIndex) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (page == slides.lastIndex) "Ver de nuevo" else "Conocer KPKN", color = Color.White.copy(alpha = .72f))
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
