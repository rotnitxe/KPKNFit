package com.example.kpkn.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.navigation.KpknRoute
import com.example.kpkn.screens.wikilab.APRENDE_DIVIDER
import com.example.kpkn.screens.wikilab.APRENDE_LINK_COLOR
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun HomeWikiLabSection(onNavigate: (String) -> Unit) {
    if (TRAINING_CONCEPTS_DATABASE.isEmpty()) return

    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            today = LocalDate.now()
        }
    }
    val concept = remember(today) {
        TRAINING_CONCEPTS_DATABASE[Math.floorMod(today.toEpochDay().toInt(), TRAINING_CONCEPTS_DATABASE.size)]
    }
    // Category remains useful as editorial metadata, but it must not dictate
    // the visual palette of the home surface.
    val accent = APRENDE_LINK_COLOR

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clickable { onNavigate(KpknRoute.WikiLabConceptDetail.create(concept.id)) }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "APRENDE",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp,
                color = Color.White.copy(alpha = 0.76f),
            )
            IconButton(onClick = { onNavigate(KpknRoute.WikiLab.route) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Search, contentDescription = "Buscar en Aprende", tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(3.dp).heightIn(min = 94.dp).background(APRENDE_DIVIDER))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "CONCEPTO DEL DÍA · ${concept.category.label.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.9.sp,
                    color = Color.White.copy(alpha = 0.58f),
                )
                Text(
                    concept.name,
                    fontFamily = FontFamily.Serif,
                    fontSize = 23.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    concept.shortDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Leer concepto", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = accent)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
