package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.kpkn.data.db.KineticChainEntity
import com.example.kpkn.data.repository.WikiLabRepository

// ─── MOVEMENT PATTERN DETAIL ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementPatternDetailScreen(
    patternId: String,
    onNavigateToMuscle: (String) -> Unit,
    onNavigateToJoint: (String) -> Unit,
    onNavigateToExercise: (String) -> Unit,
    onBack: () -> Unit,
) {
    val pattern = WikiLabRepository.getPatternById(patternId)

    if (pattern == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Patrón no encontrado")
        }
        return
    }

    val forceTypes = WikiLabRepository.parseStringList(pattern.forceTypes)
    val chainTypes = WikiLabRepository.parseStringList(pattern.chainTypes)
    val muscleIds = remember(pattern.primaryMuscles) {
        WikiLabRepository.parseStringList(pattern.primaryMuscles)
            .mapNotNull { canonicalWikiLabMuscleIdFromEntityId(it) }
            .distinct()
    }
    val jointIds = WikiLabRepository.parseStringList(pattern.primaryJoints)
    val exerciseIds = WikiLabRepository.parseStringList(pattern.exampleExercises)
    val exampleExercises = remember(exerciseIds) {
        resolveWikiLabExerciseLinks(exerciseIds)
    }
    val insight = remember(pattern.id) { patternInsightFor(pattern.id) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        pattern.name,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── Title & Description ──────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = pattern.name,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF43A047)
                        )
                    )
                    Text(
                        pattern.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White.copy(alpha = 0.9f),
                        ),
                        lineHeight = 22.sp,
                    )
                }
            }

            // ─── Infobox (Wikipedia Table) ────────────────────────────────
            item {
                WikiPatternInfobox(pattern, forceTypes, chainTypes)
            }

            // ─── Insights ─────────────────────────────────────────────────
            insight?.let {
                item {
                    WikiLabInsightCard(
                        title = "LECTURA BIOMECÁNICA",
                        accent = Color(0xFF7E57C2),
                        icon = Icons.Default.Insights,
                        summary = it.summary,
                        bullets = it.mobilityDemands,
                        footer = "Úsalo para entender qué región suele limitar antes de cambiar la técnica.",
                    )
                }

                item {
                    WikiLabInsightCard(
                        title = "CUES Y ERRORES COMUNES",
                        accent = Color(0xFFFB8C00),
                        icon = Icons.Default.Rule,
                        summary = "Piensa estas pistas como una lista corta de control para enseñar, depurar o revisar el patrón en video.",
                        bullets = it.setupCues + it.commonErrors.map { error -> "Error frecuente: $error" },
                    )
                }
            }

            // ─── Primary Muscles ─────────────────────────────────────────
            if (muscleIds.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Motores Principales")
                        Spacer(Modifier.height(8.dp))
                        muscleIds.mapNotNull { id -> WikiLabRepository.getMuscleById(id) }.forEach { muscle ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToMuscle(muscle.id) }
                                    .padding(start = 12.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(Modifier.size(6.dp), RoundedCornerShape(50), Color(0xFF9C27B0)) {}
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = muscle.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                        color = Color(0xFF9C27B0)
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "· ${WikiLabRepository.getBodyPartLabel(muscle.bodyPart)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            // ─── Primary Joints ──────────────────────────────────────────
            if (jointIds.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Ejes Articulares")
                        Spacer(Modifier.height(8.dp))
                        jointIds.mapNotNull { id -> WikiLabRepository.getJointById(id) }.forEach { joint ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToJoint(joint.id) }
                                    .padding(start = 12.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(Modifier.size(6.dp), RoundedCornerShape(50), Color(0xFF1E88E5)) {}
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = joint.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                        color = Color(0xFF1E88E5)
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "· ${WikiLabRepository.getJointTypeLabel(joint.type)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            // ─── Example Exercises ───────────────────────────────────────
            if (exampleExercises.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Ejercicios de Ejemplo")
                        Spacer(Modifier.height(8.dp))
                        exampleExercises.forEach { exercise ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToExercise(exercise.id) }
                                    .padding(start = 12.dp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(Modifier.size(6.dp), RoundedCornerShape(50), Color(0xFF66BB6A)) {}
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = exercise.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                        color = Color(0xFF66BB6A)
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── KINETIC CHAIN DETAIL ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KineticChainDetailScreen(
    chainId: String,
    onNavigateToMuscle: (String) -> Unit,
    onBack: () -> Unit,
) {
    val chain = WikiLabRepository.getChainById(chainId)

    if (chain == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Cadena no encontrada")
        }
        return
    }

    val muscles = WikiLabRepository.parseStringList(chain.muscles)
    val allMuscles = WikiLabRepository.muscles.collectAsState().value
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        chain.name,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── Title & Description ──────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = chain.name,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E88E5)
                        )
                    )
                    Text(
                        chain.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White.copy(alpha = 0.9f),
                        ),
                        lineHeight = 22.sp,
                    )
                }
            }

            // ─── Importance ───────────────────────────────────────────────
            item {
                Column {
                    WikiSectionHeader("Importancia")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        chain.importance,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Serif,
                            color = Color.White.copy(alpha = 0.8f),
                        ),
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            // ─── Muscles in Chain ─────────────────────────────────────────
            if (muscles.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Músculos en la Cadena")
                        Spacer(Modifier.height(8.dp))
                        muscles.forEach { muscleRef ->
                            val matchingMuscle = WikiLabRepository.getMuscleById(muscleRef) ?: allMuscles.find {
                                it.name.equals(muscleRef, ignoreCase = true) ||
                                    it.name.contains(muscleRef, ignoreCase = true)
                            }
                            val displayName = matchingMuscle?.name ?: muscleRef
                            val isClickable = matchingMuscle != null

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isClickable) {
                                            Modifier.clickable { onNavigateToMuscle(matchingMuscle!!.id) }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(start = 12.dp).padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(6.dp),
                                    shape = RoundedCornerShape(50),
                                    color = Color(0xFF9C27B0),
                                ) {}
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Serif,
                                            color = if (isClickable) Color(0xFF29B6F6) else Color.White
                                        ),
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (matchingMuscle != null) {
                                        Text(
                                            text = matchingMuscle.description.take(120) + "...",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                                            color = Color.White.copy(alpha = 0.5f),
                                            maxLines = 2,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── WIKIPEDIA UI COMPONENTS ──────────────────────────────────────────────

@Composable
private fun WikiSectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 1.dp)
    }
}

@Composable
private fun WikiPatternInfobox(
    pattern: com.example.kpkn.data.db.MovementPatternEntity,
    forceTypes: List<String>,
    chainTypes: List<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        border = BorderStroke(1.dp, Color(0xFF2C2C2C)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Ficha Técnica Biomecánica",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            HorizontalDivider(color = Color(0xFF2C2C2C))
            
            InfoboxRow("Patrón", pattern.name)
            InfoboxRow("Fuerzas", forceTypes.joinToString(", "))
            InfoboxRow("Cadenas Cinéticas", chainTypes.map {
                when (it) {
                    "anterior" -> "Cadena Anterior"
                    "posterior" -> "Cadena Posterior"
                    "full" -> "Cuerpo Completo"
                    else -> it
                }
            }.joinToString(", "))
        }
    }
}

@Composable
private fun InfoboxRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}
