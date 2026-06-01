package com.example.kpkn.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.navigation.KpknRoute
import com.example.kpkn.ui.components.icons.WikiIcon
import kotlinx.coroutines.delay
import java.time.LocalDate

private val LearnCardDark = Color(0xFF1C1C1E)

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
        val index = Math.floorMod(today.toEpochDay().toInt(), TRAINING_CONCEPTS_DATABASE.size)
        TRAINING_CONCEPTS_DATABASE[index]
    }

    val conceptColor = dailyConcept.category.color

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = LearnCardDark),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(conceptColor.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) { WikiIcon(tint = conceptColor) }
                    Column {
                        Text(
                            text = "ENCICLOPEDIA",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.4.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "Enciclopedia del entrenamiento",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.42f),
                        )
                    }
                }
                Surface(
                    onClick = { onNavigate(KpknRoute.WikiLab.route) },
                    shape = RoundedCornerShape(10.dp),
                    color = conceptColor.copy(alpha = 0.18f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        modifier = Modifier.padding(7.dp).size(16.dp),
                        tint = conceptColor,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(KpknRoute.WikiLabConceptDetail.create(dailyConcept.id)) },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF242426),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = conceptColor, modifier = Modifier.size(12.dp))
                        Text(
                            text = "CONCEPTO DEL DÍA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = conceptColor,
                        )
                    }
                    Text(
                        text = dailyConcept.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Text(
                        text = dailyConcept.shortDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.56f),
                        maxLines = 2,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = conceptColor.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = dailyConcept.category.label,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = conceptColor,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.clickable { onNavigate(KpknRoute.WikiLabConceptDetail.create(dailyConcept.id)) }
                        ) {
                            Text(
                                text = "Leer más",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = conceptColor,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = conceptColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
