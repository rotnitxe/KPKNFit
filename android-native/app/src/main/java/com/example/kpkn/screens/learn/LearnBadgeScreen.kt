package com.example.kpkn.screens.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.learn.BadgeTier
import com.example.kpkn.data.learn.LearnBadge
import com.example.kpkn.data.repository.LearnRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnBadgeScreen(
    courseId: String,
    onContinue: () -> Unit,
) {
    val module = LearnRepository.getModule(courseId)
    val progress = LearnRepository.getProgress(courseId)
    val badge = progress.badge

    if (module == null || badge == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontró insignia")
        }
        return
    }

    val tierColor = when (badge.tier) {
        BadgeTier.ORO -> Color(0xFFFFD700)
        BadgeTier.PLATA -> Color(0xFFC0C0C0)
        BadgeTier.BRONCE -> Color(0xFFCD7F32)
        null -> Color.Gray
    }

    val tierLabel = when (badge.tier) {
        BadgeTier.ORO -> "ORO"
        BadgeTier.PLATA -> "PLATA"
        BadgeTier.BRONCE -> "BRONCE"
        null -> "COMPLETADO"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("¡Felicidades!") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Badge visual
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = tierColor.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(3.dp, tierColor),
                modifier = Modifier.size(160.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        null,
                        tint = tierColor,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tierLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = tierColor,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                module.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Curso completado",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${badge.score}%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = tierColor,
                    )
                    Text(
                        "de acierto en el quiz final",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (badge.isSpecial) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE53935).copy(alpha = 0.1f),
                ) {
                    Text(
                        "\uD83C\uDF89 ¡Eres un Graduado KPKN!",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE53935),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continuar", fontWeight = FontWeight.Bold)
            }
        }
    }
}
