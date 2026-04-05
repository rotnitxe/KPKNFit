package com.example.kpkn.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.navigation.KpknRoute
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun HomeWikiLabSection(onNavigate: (String) -> Unit) {
    if (TRAINING_CONCEPTS_DATABASE.isEmpty()) return

    var today by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            val now = LocalDate.now()
            if (now != today) {
                today = now
            }
        }
    }

    val dailyConcept = remember(today) {
        val seed = today.toEpochDay() * 1_103_515_245L + 12_345L
        val index = abs((seed % TRAINING_CONCEPTS_DATABASE.size).toInt())
        TRAINING_CONCEPTS_DATABASE[index]
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "WIKILAB",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Mock Search Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(KpknRoute.WikiLab.route) },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Buscar en WikiLab...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Daily Concept Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(KpknRoute.WikiLabConceptDetail.create(dailyConcept.id)) },
            shape = RoundedCornerShape(12.dp),
            color = dailyConcept.category.color.copy(alpha = 0.1f),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Concepto del dia",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = dailyConcept.category.color
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = dailyConcept.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dailyConcept.shortDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onNavigate(KpknRoute.WikiLabConceptDetail.create(dailyConcept.id)) },
                    colors = ButtonDefaults.buttonColors(containerColor = dailyConcept.category.color),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Leer más", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
