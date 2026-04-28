package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.background
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = pattern.name,
                            fontWeight = FontWeight.Black,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            forceTypes.forEach { ft ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF43A047).copy(alpha = 0.12f),
                                ) {
                                    Text(
                                        ft,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF43A047),
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Header Description ──────────────────────────────────────
            item {
                Text(
                    pattern.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(8.dp))
            }

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

            // ─── Chain Types ─────────────────────────────────────────────
            if (chainTypes.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(16.dp),
                    ) {
                        Text(
                            "CADENAS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (0.1f).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            chainTypes.forEach { chain ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1E88E5).copy(alpha = 0.12f),
                                ) {
                                    Text(
                                        when (chain) {
                                            "anterior" -> "Cadena Anterior"
                                            "posterior" -> "Cadena Posterior"
                                            "full" -> "Cuerpo Completo"
                                            else -> chain
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E88E5),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Primary Muscles ─────────────────────────────────────────
            if (muscleIds.isNotEmpty()) {
                item {
                    PatternEntitiesCard(
                        title = "MOTORES PRINCIPALES",
                        color = Color(0xFF9C27B0),
                        icon = Icons.Default.FitnessCenter,
                        entities = muscleIds.mapNotNull { id ->
                            WikiLabRepository.getMuscleById(id)?.let { it.id to it.name }
                        },
                        onClick = onNavigateToMuscle,
                    )
                }
            }

            // ─── Primary Joints ──────────────────────────────────────────
            if (jointIds.isNotEmpty()) {
                item {
                    PatternEntitiesCard(
                        title = "EJES ARTICULARES",
                        color = Color(0xFF1E88E5),
                        icon = Icons.Default.Hub,
                        entities = jointIds.mapNotNull { id ->
                            WikiLabRepository.getJointById(id)?.let {
                                it.id to WikiLabRepository.getJointTypeLabel(it.type) + " - " + it.name
                            }
                        },
                        onClick = onNavigateToJoint,
                    )
                }
            }

            // ─── Example Exercises ───────────────────────────────────────
            if (exampleExercises.isNotEmpty()) {
                item {
                    PatternEntitiesCard(
                        title = "EJERCICIOS DE EJEMPLO",
                        color = Color(0xFF43A047),
                        icon = Icons.Default.FitnessCenter,
                        entities = exampleExercises.map { exercise ->
                            exercise.id to exercise.name
                        },
                        onClick = onNavigateToExercise,
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun PatternEntitiesCard(
    title: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    entities: List<Pair<String, String>>,
    onClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, letterSpacing = (0.1f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        entities.forEach { (id, name) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onClick(id) }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(Modifier.size(8.dp), RoundedCornerShape(50), color) {}
                Spacer(Modifier.width(10.dp))
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = color.copy(alpha = 0.4f))
            }
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = chain.name,
                            fontWeight = FontWeight.Black,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E88E5).copy(alpha = 0.12f),
                        ) {
                            Text(
                                "Cadena Cinética",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E88E5),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    chain.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Importance
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(16.dp),
                ) {
                    Text(
                        "IMPORTANCIA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (0.1f).sp,
                        color = Color(0xFF2E7D32),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        chain.importance,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2E7D32),
                        lineHeight = 20.sp,
                    )
                }
            }

            // Muscles in this chain
            if (muscles.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FitnessCenter, null, Modifier.size(14.dp), tint = Color(0xFF9C27B0))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "MÚSCULOS DE LA CADENA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (0.1f).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        muscles.forEach { muscleRef ->
                            val matchingMuscle = WikiLabRepository.getMuscleById(muscleRef) ?: allMuscles.find {
                                it.name.equals(muscleRef, ignoreCase = true) ||
                                    it.name.contains(muscleRef, ignoreCase = true)
                            }
                            val displayName = matchingMuscle?.name ?: muscleRef

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (matchingMuscle != null) {
                                            Modifier.clickable { onNavigateToMuscle(matchingMuscle.id) }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = RoundedCornerShape(50),
                                    color = Color(0xFF9C27B0),
                                ) {}
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (matchingMuscle != null) {
                                        Text(
                                            matchingMuscle.description.take(80) + if (matchingMuscle.description.length > 80) "..." else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = Color(0xFF9C27B0).copy(alpha = 0.4f),
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
